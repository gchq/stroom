/*
 * Copyright 2026 Crown Copyright
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

package stroom.floormap.impl;

import stroom.docref.DocRef;
import stroom.docstore.api.DocFinder;
import stroom.entity.shared.ExpressionCriteria;
import stroom.floormap.shared.FloorMapEventExpiry;
import stroom.floormap.shared.FloorMapEventStoreDoc;
import stroom.index.shared.IndexFieldImpl;
import stroom.planb.impl.PlanBDocCache;
import stroom.planb.impl.StateFieldUtil;
import stroom.planb.impl.dao.temporalstate.TemporalStateDb;
import stroom.planb.impl.data.shard.ShardManager;
import stroom.planb.shared.PlanBDocument;
import stroom.query.api.DateTimeSettings;
import stroom.query.api.ExpressionUtil;
import stroom.query.api.Param;
import stroom.query.api.Query;
import stroom.query.api.SearchRequest;
import stroom.query.api.SearchTaskProgress;
import stroom.query.api.datasource.FindFieldCriteria;
import stroom.query.api.datasource.IndexField;
import stroom.query.api.datasource.QueryField;
import stroom.query.common.v2.CoprocessorSettings;
import stroom.query.common.v2.CoprocessorsFactory;
import stroom.query.common.v2.CoprocessorsImpl;
import stroom.query.common.v2.DataStoreSettings;
import stroom.query.common.v2.ExpressionPredicateFactory;
import stroom.query.common.v2.FieldInfoResultPageFactory;
import stroom.query.common.v2.IndexFieldProvider;
import stroom.query.common.v2.ResultStore;
import stroom.query.common.v2.ResultStoreFactory;
import stroom.query.common.v2.SearchProcess;
import stroom.query.common.v2.SearchProvider;
import stroom.query.language.functions.FieldIndex;
import stroom.query.language.functions.ValuesConsumer;
import stroom.security.api.SecurityContext;
import stroom.task.api.TaskContextFactory;
import stroom.task.api.TaskManager;
import stroom.task.shared.TaskProgress;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;
import stroom.util.shared.ResultPage;

import jakarta.inject.Inject;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

/// Serves queries against a [FloorMapEventStoreDoc].
///
/// Plan B's own `StateSearchProvider` answers for `PlanBDoc.TYPE` only, and
/// `SearchProviderRegistryImpl` resolves a provider by the data source's document type, so a
/// type of ours needs a provider of ours. That is not merely a consequence of choosing a separate
/// document type — it is the reason to have one, because it is what lets the read mode be
/// **stated** rather than inferred.
///
/// ## The read mode is opt-in
///
/// The default is an ordinary range read, exactly as any other data source gives: a query with a
/// time range gets the rows in that range. A snapshot — one row per entity, as the map wants —
/// happens only when the caller says so, by sending **both** `readMode=snapshot` and
/// `asAt`. Either alone is rejected by name.
///
/// **Why not default to a snapshot**, given that is what this store mostly serves? Because the
/// store serves three reads and only one of them is a snapshot: the density histogram and the
/// timeline extent both need every row. And because defaulting would be inference from absence — the
/// Events Query tab and any dashboard send a time range and cannot set a param from query text, so
/// they would silently get a read nobody asked for. The map is presenter code and opts in with one
/// line; a human writing a query gets what they wrote.
///
/// This replaces a mechanism that inferred the mode from whether a time term happened to be
/// `<` rather than `>`, inside shared Plan B code, for every temporal state store in the
/// system. See `docs/temporal-store-parity-report.md`.
///
/// ## Expiry comes from the store, not from the caller
///
/// The caller says *when*; the store says *how long an entity lasts*. So the floor
/// passed to `searchSnapshot` is derived here from the document's own expiry rather than sent
/// with the request — which is what makes two floor maps reading one store agree, and what makes the
/// `expiry <= retention` check on the document meaningful.
public class FloorMapEventStoreSearchProvider implements SearchProvider, IndexFieldProvider {

    private static final LambdaLogger LOGGER =
            LambdaLoggerFactory.getLogger(FloorMapEventStoreSearchProvider.class);

    /// Names the read the caller wants. Absent means an ordinary range read.
    public static final String PARAM_READ_MODE = "readMode";
    /// The instant to take a snapshot at, as epoch milliseconds.
    public static final String PARAM_AS_AT = "asAt";
    /// The only value of [#PARAM_READ_MODE] that changes anything.
    public static final String READ_MODE_SNAPSHOT = "snapshot";

    /// The largest instant the key encoding can hold — six unsigned bytes of milliseconds.
    ///
    /// Beyond this the store's own encoding refuses the value from several frames deeper, with a
    /// message about negative unsigned bytes that says nothing about what the caller sent.
    private static final long MAX_AS_AT_MILLIS = (1L << 48) - 1;

    private final Executor executor;
    private final FloorMapEventStoreStore eventStoreStore;
    private final PlanBDocCache planBDocCache;
    private final CoprocessorsFactory coprocessorsFactory;
    private final ResultStoreFactory resultStoreFactory;
    private final TaskManager taskManager;
    private final TaskContextFactory taskContextFactory;
    private final ShardManager shardManager;
    private final ExpressionPredicateFactory expressionPredicateFactory;
    private final SecurityContext securityContext;
    private final FieldInfoResultPageFactory fieldInfoResultPageFactory;
    private final DocFinder docFinder;

    @Inject
    public FloorMapEventStoreSearchProvider(final Executor executor,
                                            final FloorMapEventStoreStore eventStoreStore,
                                            final PlanBDocCache planBDocCache,
                                            final CoprocessorsFactory coprocessorsFactory,
                                            final ResultStoreFactory resultStoreFactory,
                                            final TaskManager taskManager,
                                            final TaskContextFactory taskContextFactory,
                                            final ShardManager shardManager,
                                            final ExpressionPredicateFactory expressionPredicateFactory,
                                            final SecurityContext securityContext,
                                            final FieldInfoResultPageFactory fieldInfoResultPageFactory,
                                            final DocFinder docFinder) {
        this.executor = executor;
        this.eventStoreStore = eventStoreStore;
        this.planBDocCache = planBDocCache;
        this.coprocessorsFactory = coprocessorsFactory;
        this.resultStoreFactory = resultStoreFactory;
        this.taskManager = taskManager;
        this.taskContextFactory = taskContextFactory;
        this.shardManager = shardManager;
        this.expressionPredicateFactory = expressionPredicateFactory;
        this.securityContext = securityContext;
        this.fieldInfoResultPageFactory = fieldInfoResultPageFactory;
        this.docFinder = docFinder;
    }

    /// Resolves the store by name through Plan B's cache, as ingest does.
    ///
    /// Whatever `PlanBDocCache` does about permissions is what happens here: it throws a
    /// `PermissionException` for a document the user may not read and a
    /// `PlanBDocNotFoundException` for one that is absent. Deliberately not given different
    /// behaviour from every other Plan B store — a store of ours answering differently would be its
    /// own kind of disclosure, and if that distinction is wrong it is wrong for Plan B as a whole.
    private FloorMapEventStoreDoc getDoc(final DocRef docRef) {
        return securityContext.useAsReadResult(() -> {
            Objects.requireNonNull(docRef, "Null doc reference");
            Objects.requireNonNull(docRef.getName(), "Null doc key");
            final PlanBDocument doc = planBDocCache.get(docRef.getName());
            Objects.requireNonNull(doc, "Null event store doc");
            return requireEventStore(doc);
        });
    }

    /// The document as this provider's own type, or a refusal.
    ///
    /// `PlanBDocCache` resolves by **name across every registered Plan B type**, so a
    /// name that belongs to some other type resolves to that other type's document — and then every
    /// read here would run against its shard. Checked once, here, rather than on the snapshot path
    /// alone: the range read would otherwise serve another store's rows quite happily, and the
    /// snapshot read would seek over an encoding that is not prefix-free, dropping keys in silence.
    static FloorMapEventStoreDoc requireEventStore(final PlanBDocument doc) {
        if (doc instanceof final FloorMapEventStoreDoc eventStore) {
            return eventStore;
        }
        throw new IllegalStateException(
                "'" + doc.getName() + "' is a " + doc.getType() + ", not a "
                + FloorMapEventStoreDoc.TYPE + ". A name shared with another Plan B store resolves "
                + "to whichever document holds it.");
    }

    @Override
    public String getDataSourceType() {
        return FloorMapEventStoreDoc.TYPE;
    }

    @Override
    public List<DocRef> getDataSourceDocRefs() {
        return eventStoreStore.list();
    }

    @Override
    public List<DocRef> findDataSourceByName(final String name) {
        return docFinder.findByName(getDataSourceType(), name);
    }

    @Override
    public Optional<QueryField> getTimeField(final DocRef docRef) {
        return Optional.ofNullable(StateFieldUtil.getTimeField(getDoc(docRef)));
    }

    @Override
    public ResultPage<QueryField> getFieldInfo(final FindFieldCriteria criteria) {
        return fieldInfoResultPageFactory.create(
                criteria,
                StateFieldUtil.getQueryableFields(getDoc(criteria.getDataSourceRef())));
    }

    @Override
    public int getFieldCount(final DocRef docRef) {
        return NullSafe.getOrElse(
                getDoc(docRef),
                StateFieldUtil::getQueryableFields,
                List::size,
                0);
    }

    @Override
    public IndexField getIndexField(final DocRef docRef, final String fieldName) {
        final Map<String, QueryField> fieldMap = StateFieldUtil.getFieldMap(getDoc(docRef));
        final QueryField queryField = fieldMap.get(fieldName);
        if (queryField == null) {
            return null;
        }
        return IndexFieldImpl.builder()
                .fldName(queryField.getFldName())
                .fldType(queryField.getFldType())
                .build();
    }

    @Override
    public Optional<String> fetchDocumentation(final DocRef docRef) {
        return Optional.ofNullable(getDoc(docRef)).map(PlanBDocument::getDescription);
    }

    @Override
    public ResultStore createResultStore(final SearchRequest searchRequest) {
        // Substitute any `${param}` references in the query expression with the values of the
        // matching query parameters, so everything below works with literal values.
        final SearchRequest modifiedSearchRequest =
                ExpressionUtil.replaceExpressionParameters(searchRequest);
        final Query query = modifiedSearchRequest.getQuery();
        final DocRef docRef = query.getDataSource();

        // Resolve the event store document being queried.
        // Checks permission as a side effect.
        final FloorMapEventStoreDoc doc = getDoc(docRef);
        Objects.requireNonNull(doc, "Unable to find event store with key: " + docRef.getName());

        // Work out which read the caller asked for. A null `asAt` means an ordinary range
        // read; otherwise it is a snapshot at that instant, with the cut-off for expired
        // entities derived from the store's own expiry setting rather than the request.
        final Instant asAt = readAsAt(query.getParams());
        final Instant notBefore = asAt == null
                ? null
                : expiryFloor(doc, asAt);

        // Build the coprocessors that will receive each row the read produces and aggregate
        // them into the results for each component of the request (e.g. a table or a
        // visualisation). One set of coprocessor settings is created per result component.
        final Set<String> highlights = Collections.emptySet();
        final List<CoprocessorSettings> coprocessorSettingsList =
                coprocessorsFactory.createSettings(modifiedSearchRequest);
        final DataStoreSettings dataStoreSettings = DataStoreSettings
                .createBasicSearchResultStoreSettings();
        final CoprocessorsImpl coprocessors = coprocessorsFactory.create(
                modifiedSearchRequest.getSearchRequestSource(),
                modifiedSearchRequest.getDateTimeSettings(),
                modifiedSearchRequest.getKey(),
                coprocessorSettingsList,
                query.getParams(),
                dataStoreSettings);

        // Create the result store that is returned to the caller. The caller polls it for
        // results while the search runs in the background and fills the coprocessors.
        // There is no free-text search here, so there is nothing to highlight.
        final String searchName = "Search '" + modifiedSearchRequest.getKey().toString() + "'";
        final ResultStore resultStore = resultStoreFactory.create(
                modifiedSearchRequest.getSearchRequestSource(),
                coprocessors);
        resultStore.addHighlights(highlights);

        // Values captured for use inside the background task: a prefix for task progress
        // messages, a name for debug logging, and the query expression as search criteria.
        final String infoPrefix = LogUtil.message(
                "Querying {} {} - ",
                getStoreName(docRef),
                modifiedSearchRequest.getKey().toString());
        final String taskName = getTaskName(docRef);
        final ExpressionCriteria criteria = new ExpressionCriteria(query.getExpression());

        // Wrap the search in a task context so it appears in the server tasks list, reports
        // progress and can be terminated by the user or by the result store.
        final Runnable runnable = taskContextFactory.context(searchName, taskContext -> {
            // Set if the search is terminated, so the read is skipped if termination was
            // requested before the task got the chance to start.
            final AtomicBoolean destroyed = new AtomicBoolean();

            // Links the result store to this task, letting it report the task's progress and
            // terminate the task when the search is cancelled or the result store destroyed.
            final SearchProcess searchProcess = new SearchProcess() {
                @Override
                public SearchTaskProgress getSearchTaskProgress() {
                    final TaskProgress taskProgress = taskManager.getTaskProgress(taskContext);
                    if (taskProgress != null) {
                        return new SearchTaskProgress(
                                taskProgress.getTaskName(),
                                taskProgress.getTaskInfo(),
                                taskProgress.getUserRef(),
                                taskProgress.getThreadName(),
                                taskProgress.getNodeName(),
                                taskProgress.getSubmitTimeMs(),
                                taskProgress.getTimeNowMs());
                    }
                    return null;
                }

                @Override
                public void onTerminate() {
                    destroyed.set(true);
                    taskManager.terminate(taskContext.getTaskId());
                }
            };

            // If the result store has already been terminated, this immediately calls
            // `onTerminate()`, setting `destroyed`, so the check below skips the read.
            resultStore.setSearchProcess(searchProcess);

            if (!destroyed.get()) {
                taskContext.info(() -> infoPrefix + "running query");

                // Run the range or snapshot read against the store's shard, passing each
                // matching row to the coprocessors. Any failure is recorded on the result
                // store so the caller sees it as a search error rather than losing it.
                final Instant queryStart = Instant.now();
                try {
                    readThrough(
                            shardManager,
                            doc.getName(),
                            criteria,
                            coprocessors.getFieldIndex(),
                            modifiedSearchRequest.getDateTimeSettings(),
                            expressionPredicateFactory,
                            coprocessors,
                            asAt,
                            notBefore);
                } catch (final RuntimeException e) {
                    LOGGER.debug(e::getMessage, e);
                    resultStore.addError(e);
                }

                // Mark the search as complete, whether it succeeded or failed, so the caller
                // stops waiting for more results.
                LOGGER.debug(() -> String.format("%s complete called, counter: %s",
                        taskName,
                        coprocessors.getValueCount()));
                taskContext.info(() -> infoPrefix + "complete");
                resultStore.signalComplete();
                LOGGER.debug(() -> taskName + " Query finished in "
                                   + Duration.between(queryStart, Instant.now()));
            }
        });

        // Start the search in the background and return the result store straight away; the
        // caller collects results from it as they arrive.
        CompletableFuture.runAsync(runnable, executor);

        return resultStore;
    }

    /// Opens the store and runs whichever read the caller asked for.
    ///
    /// The one decision this provider exists to make, and therefore the one worth being able to
    /// test: `asAt` present means the snapshot, absent means the ordinary range read that any
    /// data source gives. Package-private, and taking its collaborators as arguments, so that
    /// decision can be exercised without standing up a coprocessor stack and an async task.
    static void readThrough(final ShardManager shardManager,
                            final String storeName,
                            final ExpressionCriteria criteria,
                            final FieldIndex fieldIndex,
                            final DateTimeSettings dateTimeSettings,
                            final ExpressionPredicateFactory expressionPredicateFactory,
                            final ValuesConsumer consumer,
                            final Instant asAt,
                            final Instant notBefore) {
        shardManager.get(storeName, reader -> {
            if (asAt == null) {
                reader.search(
                        criteria,
                        fieldIndex,
                        dateTimeSettings,
                        expressionPredicateFactory,
                        consumer);
            } else {
                snapshotReader(reader).searchSnapshot(
                        criteria,
                        fieldIndex,
                        dateTimeSettings,
                        expressionPredicateFactory,
                        consumer,
                        asAt,
                        notBefore);
            }
            return null;
        });
    }

    /// The instant to snapshot at, or `null` for an ordinary range read.
    ///
    /// Rejects each half without the other rather than guessing. A `readMode` with no
    /// `asAt` has no instant to read at, and an `asAt` with no `readMode` is a
    /// caller who believes they asked for a snapshot and would otherwise silently get every row.
    ///
    /// Package-private so the parameter contract can be tested without standing up a search.
    static Instant readAsAt(final List<Param> params) {
        final String readMode = paramValue(params, PARAM_READ_MODE);
        final String asAt = paramValue(params, PARAM_AS_AT);

        if (readMode == null && asAt == null) {
            return null;
        }
        if (readMode != null && !READ_MODE_SNAPSHOT.equalsIgnoreCase(readMode)) {
            throw new IllegalArgumentException(
                    "Unknown '" + PARAM_READ_MODE + "': '" + readMode + "'. The only supported value is '"
                    + READ_MODE_SNAPSHOT + "'; omit it for an ordinary range read.");
        }
        if (readMode == null) {
            // Strictly, asAt alone could be taken to mean a snapshot - it is unambiguous. It is
            // refused so that the mode is always written down, which is what leaves room for a
            // second one later without changing what an existing query means.
            throw new IllegalArgumentException(
                    "'" + PARAM_AS_AT + "' was supplied without '" + PARAM_READ_MODE + "="
                    + READ_MODE_SNAPSHOT + "'. Supply both or neither.");
        }
        if (asAt == null) {
            throw new IllegalArgumentException(
                    "'" + PARAM_READ_MODE + "=" + READ_MODE_SNAPSHOT + "' requires '" + PARAM_AS_AT
                    + "', the instant to take the snapshot at.");
        }

        // Epoch milliseconds only, deliberately: a snapshot's instant must not depend on how a date
        // literal happens to be spelled, nor on the viewing user's time zone.
        final long millis;
        try {
            millis = Long.parseLong(asAt.trim());
        } catch (final NumberFormatException e) {
            throw new IllegalArgumentException(
                    "'" + PARAM_AS_AT + "' must be epoch milliseconds, but was: '" + asAt + "'");
        }
        // Range-checked here so an out-of-range instant is named, rather than surfacing from deep
        // inside the key encoding as "Negative values are not permitted".
        if (millis < 0 || millis > MAX_AS_AT_MILLIS) {
            throw new IllegalArgumentException(
                    "'" + PARAM_AS_AT + "' is outside the range this store can represent: " + millis);
        }
        return Instant.ofEpochMilli(millis);
    }

    /// The value of a named query parameter, or `null` where it was not supplied.
    private static String paramValue(final List<Param> params, final String key) {
        if (params == null) {
            return null;
        }
        for (final Param param : params) {
            if (param != null && key.equals(param.getKey())) {
                return param.getValue();
            }
        }
        return null;
    }

    /// The instant before which an entity is considered to have nothing in scope.
    ///
    /// Taken from the store, not from the request. An entity whose newest event predates this is
    /// omitted rather than drawn at a position it left long ago.
    ///
    /// If doc.getEventExpiry() returns null then this is replaced by the default time (24hrs) within
    /// FloorMapEventExpiry.millis().
    static Instant expiryFloor(final FloorMapEventStoreDoc doc, final Instant asAt) {
        return Instant.ofEpochMilli(
                FloorMapEventExpiry.cutoff(asAt.toEpochMilli(), doc.getEventExpiry()));
    }

    /// The reader as a [TemporalStateDb], which is the only shape that can serve a snapshot.
    ///
    /// A [FloorMapEventStoreDoc] always carries `stateType = TEMPORAL_STATE`, so this
    /// holds by construction; the check exists so that a store whose type was somehow changed fails
    /// with something a person can act on.
    private static TemporalStateDb snapshotReader(final Object reader) {
        if (reader instanceof final TemporalStateDb temporalStateDb) {
            return temporalStateDb;
        }
        throw new IllegalStateException(
                "A snapshot read needs a temporal state store, but this store is a "
                + (reader == null
                        ? "null"
                        : reader.getClass().getSimpleName()));
    }

    private String getStoreName(final DocRef docRef) {
        return NullSafe.toStringOrElse(docRef, DocRef::getName, "Unknown Store");
    }

    private String getTaskName(final DocRef docRef) {
        return getStoreName(docRef) + " Search";
    }
}
