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

package stroom.widget.histogram.client;

import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.query.api.DestroyReason;
import stroom.query.api.GroupSelection;
import stroom.query.api.OffsetRange;
import stroom.query.api.Param;
import stroom.query.api.Result;
import stroom.query.api.TableResult;
import stroom.query.api.TimeRange;
import stroom.query.client.presenter.DateTimeSettingsFactory;
import stroom.query.client.presenter.QueryModel;
import stroom.query.client.presenter.ResultComponent;
import stroom.query.client.presenter.ResultStoreModel;
import stroom.query.shared.QueryTablePreferences;
import stroom.util.client.Console;
import stroom.util.shared.ErrorMessage;
import stroom.util.shared.Severity;

import com.google.web.bindery.event.shared.EventBus;

import java.util.List;
import java.util.function.Consumer;

/**
 * Encapsulates a {@link QueryModel} for histogram queries against temporal stores.
 *
 * <p>This helper creates a lightweight {@link ResultComponent} that receives {@link TableResult}
 * data and forwards it to the provided result handler.</p>
 *
 * <p><b>Failures are reported rather than drawn as an empty histogram.</b> Every way this query can
 * fail produces no bars, which is also what a store with nothing in it produces — so without the
 * error listener registered in the constructor the two are indistinguishable to the user, and the
 * more likely of the two (a query that will not resolve) looks like the harmless one. The result
 * handler cannot cover this, because a failure of that kind never reaches {@code setData}.</p>
 */
public class HistogramQueryHelper {

    private final QueryModel queryModel;

    /**
     * Whether a failure has already been reported for this helper.
     *
     * <p>The histogram re-runs on every timeline range change, so a persistent fault — a query that
     * cannot resolve, a store that is unreachable — would otherwise repeat its message for as long
     * as the user keeps moving the timeline.</p>
     */
    private boolean errorReported;

    /**
     * What this helper's query is for, as the user would recognise it — the name its failures are
     * reported under. One class serves more than one query, so a fixed name misattributed a failed
     * timeline-extent read to the density bars.
     */
    private final String name;

    /**
     * Called on every failure, reported or not; {@code null} when the owner does not need to know.
     */
    private Runnable failureHandler;

    /**
     * @param name          what the query is for, e.g. {@code "Histogram"}; names it in failure
     *                      reports and in the query info the server logs
     * @param resultHandler receives each table result the query produces
     */
    public HistogramQueryHelper(final EventBus eventBus,
                                final RestFactory restFactory,
                                final DateTimeSettingsFactory dateTimeSettingsFactory,
                                final ResultStoreModel resultStoreModel,
                                final String name,
                                final Consumer<TableResult> resultHandler) {
        this.name = name;
        this.queryModel = new QueryModel(
                eventBus,
                restFactory,
                dateTimeSettingsFactory,
                resultStoreModel,
                () -> QueryTablePreferences.builder().build());

        final ResultComponent resultComponent = new ResultComponent() {
            @Override
            public OffsetRange getRequestedRange() {
                return new OffsetRange(0, 10000);
            }

            @Override
            public GroupSelection getGroupSelection() {
                return null;
            }

            @Override
            public void reset() {
            }

            @Override
            public void startSearch() {
            }

            @Override
            public void endSearch() {
            }

            @Override
            public void setQueryModel(final QueryModel queryModel) {
            }

            @Override
            public void setData(final Result result) {
                if (result instanceof TableResult) {
                    final TableResult tableResult = (TableResult) result;
                    resultHandler.accept(tableResult);
                }
            }
        };

        queryModel.addResultComponent(QueryModel.TABLE_COMPONENT_ID, resultComponent);

        // Without this a failed histogram is indistinguishable from a store with nothing in it:
        // both draw no bars. The failures that actually happen here are quiet ones — a query whose
        // `from` clause did not resolve, or an unbound parameter, either of which produces zero
        // rows and an error message that nothing was listening for. Neither reaches setData, so the
        // result handler cannot report them.
        //
        // Only ERROR and above. A WARNING does not empty the bars, and the histogram has no state
        // to protect, so there is nothing to be gained by reporting one.
        queryModel.addSearchErrorListener(errors -> {
            if (errors == null) {
                return;
            }
            for (final ErrorMessage error : errors) {
                if (error != null
                    && error.getSeverity() != null
                    && error.getSeverity().greaterThanOrEqual(Severity.ERROR)) {
                    if (failureHandler != null) {
                        failureHandler.run();
                    }
                    if (errorReported) {
                        return;
                    }
                    errorReported = true;
                    Console.error(name + ": the query failed, so it has no result to show."
                                  + " This is not the same as there being no data."
                                  + " Cause: " + error.getMessage()
                                  + " Further failures are not reported.");
                    return;
                }
            }
        });
    }

    /**
     * Sets a handler called whenever a search fails, so an owner waiting on a result can tell the
     * result is not coming. A failure never reaches the result handler.
     *
     * <p>Called on every failure, unlike the console report, which is made once.</p>
     *
     * @param failureHandler the handler, or {@code null} to remove it
     */
    public void setFailureHandler(final Runnable failureHandler) {
        this.failureHandler = failureHandler;
    }

    /**
     * Initialises the underlying query model with the given data source.
     *
     * @param docRef the data source to query against
     */
    public void init(final DocRef docRef) {
        queryModel.init(docRef);
    }

    /**
     * Resets the underlying query model, destroying the current result store.
     *
     * <p>Also re-arms failure reporting. This is called when a document is (re-)read, so a fault
     * that was reported for the previous document is reported again for the next one rather than
     * being suppressed for the life of the session.</p>
     */
    public void reset() {
        errorReported = false;
        queryModel.reset(DestroyReason.NO_LONGER_NEEDED);
    }

    /**
     * Starts a histogram search with no time bound at all.
     *
     * <p>Reads whatever the query matches across the store's whole history. Prefer
     * {@link #run(String, List, Long, Long)}, which bounds the read to the visible range; this
     * overload exists for the one caller that genuinely wants everything — the timeline's "Show All"
     * extent, which cannot be answered by a bounded read.</p>
     *
     * @param query  the StroomQL query text to execute
     * @param params the query parameters, or {@code null} when the query uses none. The
     *               {@code from} clause resolves {@code param('key')} server-side, so a query
     *               naming its data source that way will not resolve without them
     */
    public void run(final String query, final List<Param> params) {
        run(query, params, null, null);
    }

    /**
     * Starts a histogram search bounded to {@code [fromMs, toMs]}.
     *
     * <p><b>Both bounds, which was not always safe.</b> An upper bound used to switch the temporal
     * store into a point-in-time mode that returned one row per key rather than the rows a histogram
     * counts — the store inferred its read from whether a time term was {@code <} or {@code >}. That
     * inference is gone: the mode is now named by the caller, so a range is just a range and the
     * bound costs nothing.</p>
     *
     * <p>It matters because without it the read returned every bucket from {@code fromMs} to the end
     * of the store. Zoomed to an hour a year into a store, that is tens of thousands of grouped rows
     * crossing the wire for the dozen bars actually drawn.</p>
     *
     * <p><b>Do not derive a data extent from a result of this call.</b> It is bounded, so the buckets
     * returned say nothing about data outside the range — useless for "Show All", which exists to
     * reach beyond it. Use the two-argument overload for a read whose range is the answer.</p>
     *
     * @param fromMs the start of the visible range, or null for no lower bound
     * @param toMs   the end of the visible range, or null for no upper bound
     */
    public void run(final String query,
                    final List<Param> params,
                    final Long fromMs,
                    final Long toMs) {
        if (query == null || query.trim().isEmpty()) {
            return;
        }
        queryModel.startNewSearch(
                QueryModel.TABLE_COMPONENT_ID,
                "histogramTable",
                query,
                params,
                fromMs == null && toMs == null
                        ? null
                        // The end is inclusive to the caller and exclusive in the generated term.
                        : new TimeRange("CUSTOM",
                                fromMs == null ? null : String.valueOf(fromMs),
                                toMs == null ? null : String.valueOf(toMs + 1)),
                false,  // incremental
                false,  // storeHistory
                name + " Query",  // queryInfo
                null);  // additionalQueryExpression
    }
}
