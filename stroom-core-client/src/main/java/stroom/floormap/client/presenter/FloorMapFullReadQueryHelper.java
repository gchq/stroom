/*
 * Copyright 2016-2026 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.floormap.client.presenter;

import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.query.api.DestroyReason;
import stroom.query.api.GroupSelection;
import stroom.query.api.OffsetRange;
import stroom.query.api.Param;
import stroom.query.api.Result;
import stroom.query.api.TableResult;
import stroom.query.client.presenter.DateTimeSettingsFactory;
import stroom.query.client.presenter.QueryModel;
import stroom.query.client.presenter.ResultComponent;
import stroom.query.client.presenter.ResultStoreModel;
import stroom.query.shared.QueryTablePreferences;
import stroom.util.shared.ErrorMessage;
import stroom.util.shared.Severity;

import com.google.web.bindery.event.shared.EventBus;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/// Runs a Map tab read that **replaces** what the tab knows rather than adding to it.
///
/// Two callers, for the same reason. The events **baseline** reads every entity active within
/// the horizon; the facts **history** reads every version of every fact. Both discard the
/// previous answer wholesale, so both need the same guarantee: never apply a result that might be
/// partial. That guarantee is the entire content of this class, and it is subtle enough that having
/// it in one place matters more than the two callers' differences do.
///
/// ### Why this needs its own [QueryModel]
///
/// `QueryModel`'s state is single-valued — one `currentSearch`, one
/// `currentQueryKey`, one searching flag — and `startNewSearch` destroys the previous
/// result store. A read sharing the playback model would be destroyed by the next 300 ms tick,
/// every time. Same reason `HistogramQueryHelper` owns one, and this follows its shape.
///
/// ### Why the result is delivered on the searching→idle edge, not from setData
///
/// A wholesale replacement applied from a *partial* result drops things that are still
/// there — entities in the events case, the whole floor plan in the facts case. Two things make
/// that reachable:
///
/// - `StateSearchProvider` catches a scan failure, calls `addError`, and then
///   **still** calls `signalComplete()` — so a broken or never-written Plan B store
///   presents as a legitimately empty horizon, and a mid-scan failure presents as a small
///   complete result.
/// - `QueryModel.update` calls `setData` *before* `setErrors`, so at
///   `setData` time the error state is not yet known. `setSearching(false)` fires
///   after both.
///
/// So rows are held as they arrive and handed over only on the running→idle edge, together
/// with whether the search reported an error. This is the pattern [FloorMapQueryPresenter]
/// already uses and documents, including its guard against acting on the "not searching" that the
/// *start* of the next run also reports.
class FloorMapFullReadQueryHelper {

    /// The row cap for a read, as an `OffsetRange` length.
    ///
    /// **This is a fetch cap, not a store cap** — how many rows come back in one response,
    /// not how many the server holds. It is nonetheless the binding limit here, because the
    /// server-side limits are far above it:
    ///
    /// Where each limit applies
    ///
    /// | Limit | Value | Governs |
    /// |---|---|---|
    /// | this constant | 20 000 | rows returned per fetch |
    /// | `LmdbPutFilterFactory` | 1 000 000 | rows admitted to the store — **only** if ungrouped and unsorted |
    /// | `DataStoreSettings.maxResults` | 1 000 000 / 100 / 10 / 1 | rows returned per depth at fetch time |
    /// | `ResultStoreLmdbConfig` | 10 GiB | the store's LMDB env |
    ///
    /// This query neither groups nor sorts, so the `LmdbPutFilterFactory` limit applies. It is a put filter:
    /// on reaching the cap it stops accepting and signals completion, ending the search early.
    ///
    /// **What hitting this cap means has changed.** The read is a snapshot at the selected
    /// time, so it returns *one row per entity*. Reaching 20 000 therefore says the store
    /// holds more distinct entities than the cap allows — not that it holds more history.
    /// Narrowing the time range would not help, and neither would `condense`: both reduce
    /// versions per entity, and there is already only one. Only a higher cap helps. An earlier
    /// version of this javadoc recommended `condense`, which was correct for the windowed
    /// read this replaced.
    ///
    /// **That is a statement about this cap only.** `condense` remains important for
    /// Plan B *store capacity*, where collapsing runs of identical positions keeps event
    /// values low-cardinality — which is worth roughly a factor of five in bytes per row, because
    /// the default `VARIABLE` value type deduplicates repeated values through a lookup table.
    /// See `docs/floormap-single-read-feasibility.md` §5. The two limits are unrelated and
    /// both statements hold.
    ///
    /// @see Outcome#truncated()
    static final int MAX_ROWS = 20_000;

    /// The read-mode contract, as `FloorMapEventStoreSearchProvider` defines it.
    ///
    /// Both are required together; either alone is rejected server side by name, deliberately, so
    /// that a caller who believes they asked for a snapshot cannot silently get every row.
    private static final String PARAM_READ_MODE = "readMode";
    private static final String PARAM_AS_AT = "asAt";
    private static final String READ_MODE_SNAPSHOT = "snapshot";

    /// How long the server may spend on a baseline before responding.
    ///
    /// `QuerySearchRequest` defaults to one second, and a whole-store Plan B scan routinely
    /// exceeds that. Overrunning is not data loss — polling continues and the rows arrive — but it
    /// costs a request per second per overrun and the timeout message is stripped before the client
    /// sees it, so the lateness is silent. Thirty seconds is long enough that an ordinary baseline
    /// answers in one round trip.
    private static final long TIMEOUT_MS = 30_000L;

    private final QueryModel queryModel;
    private final String componentName;
    private final String taskName;

    /// Whether a baseline is in flight.
    ///
    /// **Deliberately not `queryModel.isSearching()`.** That flag is cleared only by
    /// `stop`, `reset`, a null response and completion — *not* by the REST
    /// failure path, which sets errors and stops polling with the flag still set. Delegating to it
    /// would let one network blip suppress every future baseline, silently, until the document was
    /// re-read.
    private boolean running;

    /// Rows from the current search, replaced on each poll.
    private TableResult latestResult;

    /// Whether any result has arrived since [#runSnapshot] or [#runAll] — see [Outcome#failed()].
    private boolean resultSeen;

    /// Whether the current search has reported an error. Cleared when a search starts.
    private boolean errored;

    private boolean searching;

    FloorMapFullReadQueryHelper(final EventBus eventBus,
                                final RestFactory restFactory,
                                final DateTimeSettingsFactory dateTimeSettingsFactory,
                                final ResultStoreModel resultStoreModel,
                                final int maxRows,
                                final String componentName,
                                final String taskName,
                                final Consumer<Outcome> outcomeHandler) {
        this.componentName = componentName;
        this.taskName = taskName;
        this.queryModel = new QueryModel(
                eventBus,
                restFactory,
                dateTimeSettingsFactory,
                resultStoreModel,
                () -> QueryTablePreferences.builder().build());
        this.queryModel.setTimeout(TIMEOUT_MS);

        this.queryModel.addResultComponent(QueryModel.TABLE_COMPONENT_ID, new ResultComponent() {
            @Override
            public OffsetRange getRequestedRange() {
                return new OffsetRange(0, maxRows);
            }

            @Override
            public GroupSelection getGroupSelection() {
                return null;
            }

            @Override
            public void reset() {}

            @Override
            public void startSearch() {}

            @Override
            public void endSearch() {}

            @Override
            public void setData(final Result result) {
                if (result instanceof final TableResult tableResult) {
                    latestResult = tableResult;
                    resultSeen = true;
                }
            }

            @Override
            public void setQueryModel(final QueryModel model) {}
        });

        // Errors arrive after setData and before the idle edge, so recording them here is enough
        // to have the answer by the time the outcome is delivered.
        //
        // Only ERROR and above count. Refusing a baseline keeps stale positions on the map until
        // the next one, so a WARNING — a value that would not format, say — must not be able to
        // freeze the map for as long as it keeps recurring. The list is rebuilt on every poll and
        // may be null.
        this.queryModel.addSearchErrorListener(errors -> {
            if (errors != null) {
                for (final ErrorMessage error : errors) {
                    if (error != null
                        && error.getSeverity() != null
                        && error.getSeverity().greaterThanOrEqual(Severity.ERROR)) {
                        errored = true;
                        break;
                    }
                }
            }
        });

        this.queryModel.addSearchStateListener(isSearching -> {
            if (isSearching) {
                // Attribute errors to the search that is starting, not the one that finished.
                errored = false;
                resultSeen = false;
                latestResult = null;
                searching = true;
                return;
            }
            // Only the running-to-idle edge. The reset at the start of the next run reports "not
            // searching" too, and a deliberate reset() clears `running` first so its edge is not
            // mistaken for a completion.
            final boolean finished = searching && running;
            searching = false;
            if (finished) {
                running = false;
                // The null-response path fires this edge with no setErrors at all, so "idle having
                // never delivered a result" counts as a failure rather than an empty store.
                outcomeHandler.accept(new Outcome(latestResult, errored || !resultSeen));
            }
        });
    }

    /// Whether a baseline is in flight.
    ///
    /// For deciding whether abandoning it is *wanted*, not whether it is safe —
    /// [#runSnapshot] and [#runAll] handle the safety themselves. A routine read asks the same
    /// question the one in flight is already answering, so replacing it would be a self-destroying
    /// loop; one following a user's jump asks about a position the in-flight read has already left,
    /// so it should replace it.
    boolean isRunning() {
        return running;
    }

    void init(final DocRef docRef) {
        queryModel.init(docRef);
    }

    /// Abandons any baseline in flight and destroys its result store.
    ///
    /// Clears the in-flight state **before** resetting the model, so the
    /// searching→false the reset emits is not read as a completed baseline carrying whatever
    /// rows had arrived.
    void reset() {
        clearInFlight();
        queryModel.reset(DestroyReason.NO_LONGER_NEEDED);
    }

    /// Forgets the in-flight search without touching the model.
    ///
    /// Ordering matters more than the assignments do. `startNewSearch` destroys the
    /// previous search, which emits searching→false — and if [#running] and
    /// [#searching] were still set, that destruction would present as the completion edge and
    /// deliver the abandoned search's rows as if they answered the new one.
    private void clearInFlight() {
        running = false;
        searching = false;
        latestResult = null;
        resultSeen = false;
        errored = false;
    }

    /// Starts a point-in-time read: one row per entity, as the entity was at `asAt`.
    ///
    /// **Sends no `TimeRange`, and says what it wants instead.** A `TimeRange` is
    /// folded into `>= from AND < to` expression terms by `ResultStoreManager` before the
    /// store ever sees it, which is how the read mode used to be inferred — from whether a time term
    /// happened to be `<` rather than `>`. The store now takes the mode as a parameter,
    /// so the request carries `readMode=snapshot` and `asAt` and leaves the expression
    /// alone.
    ///
    /// **Expiry is not sent.** How long an entity stays on the map is a property of the store,
    /// not of this request, so the server derives the floor from the store document. That is what
    /// makes two floor maps reading one store agree with each other.
    ///
    /// `asAt` is epoch milliseconds, which is the only form the server accepts: a snapshot's
    /// instant must not depend on how a date is spelled or on the viewer's time zone.
    ///
    /// @param query  the query text; a blank one is a no-op
    /// @param params the store references
    /// @param asAt   the instant to read the store as at
    void runSnapshot(final String query, final List<Param> params, final long asAt) {
        final List<Param> withReadMode = new ArrayList<>();
        if (params != null) {
            withReadMode.addAll(params);
        }
        withReadMode.add(new Param(PARAM_READ_MODE, READ_MODE_SNAPSHOT));
        withReadMode.add(new Param(PARAM_AS_AT, String.valueOf(asAt)));
        start(query, withReadMode);
    }

    /// Starts a read of **everything** the query matches, with no time bound at all — the facts
    /// history.
    ///
    /// Sending no `TimeRange` is what makes it whole-history rather than a snapshot,
    /// and it is load-bearing rather than incidental. With a `TimeRange` present,
    /// `UpdatableTemporalStoreDaoImpl.getQueryTime` lifts a snapshot boundary out of it and
    /// the DAO returns one row per key at or before that boundary. With none, `getQueryTime`
    /// returns null and the standard path returns every historical version — which is precisely what
    /// a client-side snapshot needs, and what `HistogramQueryHelper` already relies on for the
    /// same reason.
    ///
    /// @param query  the resolved query text; a blank one is a no-op
    /// @param params the store references, matching the substitutions already made in `query`
    void runAll(final String query, final List<Param> params) {
        start(query, params);
    }

    private void start(final String query,
                       final List<Param> params) {
        if (query == null || query.trim().isEmpty()) {
            return;
        }
        // Abandon anything in flight here rather than obliging the caller to do it first. See
        // clearInFlight() for what goes wrong otherwise.
        clearInFlight();
        running = true;
        queryModel.startNewSearch(
                QueryModel.TABLE_COMPONENT_ID,
                componentName,
                query,
                params,
                // No TimeRange, ever: it would be folded into time terms on the query. A snapshot
                // carries its instant as parameters instead, and a whole-history read must stay
                // unbounded - see runSnapshot() and runAll().
                null,
                false,  // incremental
                false,  // storeHistory
                taskName,
                null);  // additionalQueryExpression
    }

    /// A finished read: the rows, and whether to trust them.
    static final class Outcome {

        private final TableResult result;
        private final boolean failed;

        private Outcome(final TableResult result, final boolean failed) {
            this.result = result;
            this.failed = failed;
        }

        /// Whether the search reported an error, or ended without ever delivering a result.
        ///
        /// A failed read must not be applied: it cannot be distinguished from an empty store by
        /// its rows, and applying it would drop every entity, or blank the whole floor plan.
        boolean failed() {
            return failed;
        }

        /// Whether the row cap bound.
        ///
        /// `TableResult.getTotalResults()` is populated independently of the rows returned,
        /// so a larger total means the result was cut. A truncated baseline is per-key complete in
        /// key order except possibly at the boundary key, so it is worth upserting — but it is no
        /// evidence that an absent entity has gone, so it must not prune.
        boolean truncated() {
            return result != null
                   && result.getTotalResults() != null
                   && result.getRows() != null
                   && result.getTotalResults() > result.getRows().size();
        }

        TableResult result() {
            return result;
        }
    }
}
