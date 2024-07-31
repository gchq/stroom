/*
 * Copyright 2024 Crown Copyright
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

package stroom.analytics.impl;

import stroom.analytics.impl.AnalyticDataStores.AnalyticDataStore;
import stroom.analytics.shared.AnalyticProcessConfig;
import stroom.analytics.shared.AnalyticProcessType;
import stroom.analytics.shared.AnalyticRuleDoc;
import stroom.analytics.shared.AnalyticTracker;
import stroom.analytics.shared.TableBuilderAnalyticProcessConfig;
import stroom.analytics.shared.TableBuilderAnalyticTrackerData;
import stroom.docref.DocRef;
import stroom.expression.matcher.ExpressionMatcher;
import stroom.expression.matcher.ExpressionMatcherFactory;
import stroom.index.shared.IndexConstants;
import stroom.meta.shared.Meta;
import stroom.meta.shared.MetaAttributeMapUtil;
import stroom.meta.shared.MetaFields;
import stroom.meta.shared.Status;
import stroom.node.api.NodeInfo;
import stroom.pipeline.PipelineStore;
import stroom.pipeline.factory.PipelineDataCache;
import stroom.pipeline.shared.PipelineDoc;
import stroom.pipeline.shared.data.PipelineData;
import stroom.query.api.v2.Column;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.ExpressionUtil;
import stroom.query.api.v2.OffsetRange;
import stroom.query.api.v2.QueryKey;
import stroom.query.api.v2.ResultRequest;
import stroom.query.api.v2.Row;
import stroom.query.api.v2.SearchRequest;
import stroom.query.api.v2.TableResult;
import stroom.query.api.v2.TableResultBuilder;
import stroom.query.api.v2.TimeFilter;
import stroom.query.common.v2.CurrentDbState;
import stroom.query.common.v2.DeleteCommand;
import stroom.query.common.v2.Key;
import stroom.query.common.v2.LmdbDataStore;
import stroom.query.common.v2.TableResultCreator;
import stroom.query.common.v2.format.ColumnFormatter;
import stroom.query.common.v2.format.FormatterFactory;
import stroom.query.language.functions.FieldIndex;
import stroom.search.extraction.AnalyticFieldListConsumer;
import stroom.search.extraction.ExtractionException;
import stroom.search.extraction.ExtractionState;
import stroom.search.extraction.FieldListConsumerHolder;
import stroom.search.extraction.FieldValueExtractor;
import stroom.search.extraction.FieldValueExtractorFactory;
import stroom.search.extraction.MemoryIndex;
import stroom.security.api.SecurityContext;
import stroom.security.api.UserIdentity;
import stroom.task.api.ExecutorProvider;
import stroom.task.api.TaskContext;
import stroom.task.api.TaskContextFactory;
import stroom.task.api.TaskTerminatedException;
import stroom.task.api.TerminateHandlerFactory;
import stroom.util.NullSafe;
import stroom.util.concurrent.UncheckedInterruptedException;
import stroom.util.date.DateUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogExecutionTime;
import stroom.util.logging.LogUtil;
import stroom.util.shared.string.CIKey;
import stroom.util.shared.time.SimpleDuration;
import stroom.util.shared.time.TimeUnit;
import stroom.util.time.SimpleDurationUtil;
import stroom.view.shared.ViewDoc;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

@Singleton
public class TableBuilderAnalyticExecutor {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TableBuilderAnalyticExecutor.class);
    private static final int DEFAULT_MAX_META_LIST_SIZE = 1000;

    private final ExecutorProvider executorProvider;
    private final SecurityContext securityContext;
    private final DetectionConsumerFactory detectionConsumerFactory;
    private final PipelineStore pipelineStore;
    private final PipelineDataCache pipelineDataCache;
    private final Provider<AnalyticsStreamProcessor> analyticsStreamProcessorProvider;
    private final Provider<FieldListConsumerHolder> fieldListConsumerHolderProvider;
    private final Provider<ExtractionState> extractionStateProvider;
    private final AnalyticDataStores analyticDataStores;
    private final TaskContextFactory taskContextFactory;
    private final Provider<MemoryIndex> memoryIndexProvider;
    private final AnalyticTrackerDao analyticTrackerDao;
    private final ExpressionMatcher metaExpressionMatcher;
    private final NodeInfo nodeInfo;
    private final AnalyticRuleSearchRequestHelper analyticRuleSearchRequestHelper;
    private final FieldValueExtractorFactory fieldValueExtractorFactory;

    private final int maxMetaListSize = DEFAULT_MAX_META_LIST_SIZE;


    private final AnalyticErrorWritingExecutor analyticErrorWritingExecutor;
    private final AnalyticHelper analyticHelper;

    @Inject
    public TableBuilderAnalyticExecutor(final ExecutorProvider executorProvider,
                                        final SecurityContext securityContext,
                                        final DetectionConsumerFactory detectionConsumerFactory,
                                        final PipelineStore pipelineStore,
                                        final PipelineDataCache pipelineDataCache,
                                        final Provider<AnalyticsStreamProcessor> analyticsStreamProcessorProvider,
                                        final Provider<FieldListConsumerHolder> fieldListConsumerHolderProvider,
                                        final Provider<ExtractionState> extractionStateProvider,
                                        final TaskContextFactory taskContextFactory,
                                        final Provider<MemoryIndex> memoryIndexProvider,
                                        final AnalyticDataStores analyticDataStores,
                                        final AnalyticTrackerDao analyticTrackerDao,
                                        final AnalyticErrorWritingExecutor analyticErrorWritingExecutor,
                                        final ExpressionMatcherFactory expressionMatcherFactory,
                                        final AnalyticHelper analyticHelper,
                                        final NodeInfo nodeInfo,
                                        final AnalyticRuleSearchRequestHelper analyticRuleSearchRequestHelper,
                                        final FieldValueExtractorFactory fieldValueExtractorFactory) {
        this.executorProvider = executorProvider;
        this.detectionConsumerFactory = detectionConsumerFactory;
        this.securityContext = securityContext;
        this.pipelineStore = pipelineStore;
        this.pipelineDataCache = pipelineDataCache;
        this.analyticsStreamProcessorProvider = analyticsStreamProcessorProvider;
        this.fieldListConsumerHolderProvider = fieldListConsumerHolderProvider;
        this.extractionStateProvider = extractionStateProvider;
        this.taskContextFactory = taskContextFactory;
        this.memoryIndexProvider = memoryIndexProvider;
        this.analyticDataStores = analyticDataStores;
        this.analyticTrackerDao = analyticTrackerDao;
        this.analyticErrorWritingExecutor = analyticErrorWritingExecutor;
        this.metaExpressionMatcher = expressionMatcherFactory.create(MetaFields.getFieldMap());
        this.analyticHelper = analyticHelper;
        this.nodeInfo = nodeInfo;
        this.analyticRuleSearchRequestHelper = analyticRuleSearchRequestHelper;
        this.fieldValueExtractorFactory = fieldValueExtractorFactory;
    }

    public void exec() {
        final TaskContext taskContext = taskContextFactory.current();
        final LogExecutionTime logExecutionTime = new LogExecutionTime();
        try {
            info(() -> "Starting table builder analytic processing");
            processUntilAllComplete(taskContext);
            info(() -> LogUtil.message("Finished table builder analytic processing in {}", logExecutionTime));
        } catch (final TaskTerminatedException | UncheckedInterruptedException e) {
            LOGGER.debug("Task terminated", e);
            LOGGER.debug(() ->
                    LogUtil.message("Table builder analytic processing terminated after {}", logExecutionTime));
        } catch (final RuntimeException e) {
            LOGGER.error(() ->
                    LogUtil.message("Error during table builder analytic processing: {}", e.getMessage()), e);
        }
    }

    private void processUntilAllComplete(final TaskContext taskContext) {
        securityContext.asProcessingUser(() -> {
            // Delete old data stores.
            deleteOldStores();

            boolean allComplete = false;
            // Keep going until we have processed everything we can.
            while (!allComplete) {
                allComplete = processAll(taskContext);
            }
        });
    }

    private boolean processAll(final TaskContext taskContext) {
        final AtomicBoolean allComplete = new AtomicBoolean(true);

        // Load event and aggregate rules.
        final List<TableBuilderAnalytic> analytics = loadAnalyticRules();

        // Group rules by transformation pipeline.
        final Map<GroupKey, List<TableBuilderAnalytic>> analyticGroupMap = new HashMap<>();
        for (final TableBuilderAnalytic analytic : analytics) {
            try {
                final String ownerUuid = securityContext.getDocumentOwnerUuid(analytic.analyticRuleDoc().asDocRef());
                final GroupKey groupKey = new GroupKey(analytic.viewDoc().getPipeline(), ownerUuid);
                analyticGroupMap
                        .computeIfAbsent(groupKey, k -> new ArrayList<>())
                        .add(analytic);
            } catch (final RuntimeException e) {
                LOGGER.error(() -> "Error executing rule: " + analytic.ruleIdentity(), e);
            }
        }

        // Process each group in parallel.
        info(() ->
                LogUtil.message("Processing {} ({})",
                        LogUtil.namedCount("table builder rule", analyticGroupMap.values()
                                .stream()
                                .mapToInt(List::size)
                                .sum()),
                        LogUtil.namedCount("group", analyticGroupMap.size())));

        final List<CompletableFuture<Void>> completableFutures = new ArrayList<>();
        for (final Entry<GroupKey, List<TableBuilderAnalytic>> entry : analyticGroupMap.entrySet()) {
            final Optional<CompletableFuture<Void>> optional =
                    processGroup(taskContext, entry.getKey(), entry.getValue(), allComplete);
            optional.ifPresent(completableFutures::add);
        }

        // Join.
        CompletableFuture.allOf(completableFutures.toArray(new CompletableFuture[0])).join();

        return allComplete.get() || taskContext.isTerminated();
    }

    private Optional<CompletableFuture<Void>> processGroup(final TaskContext parentTaskContext,
                                                           final GroupKey groupKey,
                                                           final List<TableBuilderAnalytic> analytics,
                                                           final AtomicBoolean allComplete) {
        final DocRef pipelineRef = groupKey.pipeline();
        final String ownerUuid = groupKey.ownerUuid();
        if (!analytics.isEmpty()) {
            final UserIdentity userIdentity = securityContext.getIdentityByUserUuid(ownerUuid);
            return securityContext.asUserResult(userIdentity, () -> securityContext.useAsReadResult(() -> {
                final String pipelineIdentity = pipelineRef.toInfoString();
                final Runnable runnable = taskContextFactory.childContext(
                        parentTaskContext,
                        "Pipeline: " + pipelineIdentity,
                        TerminateHandlerFactory.NOOP_FACTORY,
                        taskContext -> {
                            final boolean complete =
                                    processPipeline(pipelineRef, analytics, taskContext);
                            if (!complete) {
                                allComplete.set(false);
                            }
                        });
                return Optional.of(CompletableFuture.runAsync(runnable, executorProvider.get()));
            }));
        }
        return Optional.empty();
    }

    private boolean processPipeline(final DocRef pipelineDocRef,
                                    final List<TableBuilderAnalytic> analytics,
                                    final TaskContext parentTaskContext) {
        // Get a list of meta that will fit some of our analytics.
        final List<Meta> sortedMetaList = getMetaBatch(analytics);

        // Update the poll time for the trackers.
        final Instant startTime = Instant.now();
        analytics.forEach(analytic -> {
            analytic.trackerData.setLastExecutionTimeMs(startTime.toEpochMilli());
            analytic.trackerData.setLastStreamCount(sortedMetaList.size());
        });

        // Now process each stream with the pipeline.
        if (!sortedMetaList.isEmpty()) {
            final PipelineData pipelineData = getPipelineData(pipelineDocRef);
            for (final Meta meta : sortedMetaList) {
                if (!parentTaskContext.isTerminated()) {
                    try {
                        if (Status.UNLOCKED.equals(meta.getStatus())) {
                            processStream(pipelineDocRef, pipelineData, analytics, meta, parentTaskContext);
                        } else {
                            LOGGER.info("Complete for now");
                            analytics.forEach(analytic ->
                                    analytic.trackerData.setMessage("Complete for now"));
                            break;
                        }
                    } catch (final TaskTerminatedException | UncheckedInterruptedException e) {
                        LOGGER.debug(e::getMessage, e);
                        throw e;
                    } catch (final RuntimeException e) {
                        LOGGER.error(e::getMessage, e);
                        analytics.forEach(analytic ->
                                analytic.trackerData.setMessage(e.getMessage()));
                        throw e;
                    }
                }
            }
        }

        // Execute notifications on LMDB stores and sync.
        executePostProcessNotifications(analytics, parentTaskContext);

        // Update all trackers.
        for (final TableBuilderAnalytic analytic : analytics) {
            analyticHelper.updateTracker(analytic.tracker);
        }

        return sortedMetaList.size() < maxMetaListSize;
    }

    private List<Meta> getMetaBatch(final List<TableBuilderAnalytic> analytics) {
        // Group by filter.
        final Map<ExpressionOperator, List<TableBuilderAnalytic>> filterGroupMap = new HashMap<>();
        for (final TableBuilderAnalytic analytic : analytics) {
            ExpressionOperator operator = analytic.viewDoc().getFilter();
            if (operator == null) {
                operator = ExpressionOperator.builder().build();
            }
            filterGroupMap
                    .computeIfAbsent(operator, k -> new ArrayList<>())
                    .add(analytic);
        }

        // For each group get matching meta.
        final Set<Meta> allMatchingMeta = new HashSet<>();
        for (final Entry<ExpressionOperator, List<TableBuilderAnalytic>> filterGroupEntry : filterGroupMap.entrySet()) {

            // Get a min meta id and/or min create time.
            Long minStreamId = null;
            Long minCreateTime = null;
            Long maxCreateTime = null;

            for (final TableBuilderAnalytic analytic : filterGroupEntry.getValue()) {
                final TableBuilderAnalyticTrackerData trackerData = analytic.trackerData();

                // Start at the next meta.
                minStreamId = AnalyticUtil.getMin(minStreamId, trackerData.getMinStreamId());
                minCreateTime = AnalyticUtil.getMin(minCreateTime,
                        analytic.analyticProcessConfig.getMinMetaCreateTimeMs());
                maxCreateTime = AnalyticUtil.getMax(maxCreateTime,
                        analytic.analyticProcessConfig.getMaxMetaCreateTimeMs());
            }

            final ExpressionOperator findMetaExpression = filterGroupEntry.getKey();

            if (ExpressionUtil.termCount(findMetaExpression) > 0) {
                final List<Meta> metaList = analyticHelper.findMeta(findMetaExpression,
                        minStreamId,
                        minCreateTime,
                        maxCreateTime,
                        maxMetaListSize);
                allMatchingMeta.addAll(metaList);
            }
        }

        // Now trim to the max meta list size as some of our filters may be further ahead.
        List<Meta> sortedMetaList = allMatchingMeta.stream().sorted(Comparator.comparing(Meta::getId)).toList();
        if (sortedMetaList.size() > maxMetaListSize) {
            sortedMetaList = sortedMetaList.subList(0, maxMetaListSize);
        }
        return sortedMetaList;
    }

    private void processStream(final DocRef pipelineDocRef,
                               final PipelineData pipelineData,
                               final List<TableBuilderAnalytic> analytics,
                               final Meta meta,
                               final TaskContext parentTaskContext) {
        final Map<CIKey, Object> metaAttributeMap = MetaAttributeMapUtil
                .createAttributeMap(meta);

        final List<AnalyticFieldListConsumer> fieldListConsumers = new ArrayList<>();

        // Filter the rules that should be applied to this meta.
        final List<TableBuilderAnalytic> filteredAnalytics = analytics
                .stream()
                .filter(analytic -> !ignoreStream(analytic, meta, metaAttributeMap))
                .toList();

        for (final TableBuilderAnalytic analytic : filteredAnalytics) {
            fieldListConsumers.add(createLmdbConsumer(analytic, meta));
        }

        final AnalyticFieldListConsumer fieldListConsumer;
        if (fieldListConsumers.size() > 1) {
            fieldListConsumer = new MultiAnalyticFieldListConsumer(fieldListConsumers);
        } else if (fieldListConsumers.size() == 1) {
            fieldListConsumer = fieldListConsumers.getFirst();
        } else {
            fieldListConsumer = null;
        }

        if (fieldListConsumer != null) {
            analyticErrorWritingExecutor.wrap(
                    "Analytics Table Builder Processor",
                    meta.getFeedName(),
                    pipelineDocRef.getUuid(),
                    parentTaskContext,
                    taskContext -> {
                        final FieldListConsumerHolder fieldListConsumerHolder =
                                fieldListConsumerHolderProvider.get();
                        fieldListConsumerHolder.setFieldListConsumer(fieldListConsumer);

                        try {
                            fieldListConsumer.start();

                            analyticsStreamProcessorProvider.get().extract(
                                    taskContext,
                                    meta.getId(),
                                    pipelineDocRef,
                                    pipelineData);

                        } finally {
                            fieldListConsumer.end();
                        }

                        if (!taskContext.isTerminated()) {
                            // Update LMDB state.
                            analytics.forEach(analytic -> {
                                final LmdbDataStore lmdbDataStore = analytic.dataStore().getLmdbDataStore();

                                // Get current state and last event time.
                                final CurrentDbState currentDbState = lmdbDataStore.sync();
                                Long lastEventTime = null;
                                if (currentDbState != null) {
                                    lastEventTime = currentDbState.getLastEventTime();
                                }

                                // Update the state.
                                lmdbDataStore.putCurrentDbState(meta.getId(), null, lastEventTime);
                                lmdbDataStore.sync();
                            });

                            // Update extraction state.
                            final ExtractionState extractionState = extractionStateProvider.get();
                            filteredAnalytics.forEach(analytic -> {
                                analytic.trackerData.incrementStreamCount();
                                analytic.trackerData.addEventCount(extractionState.getCount());
                            });
                        }
                        return !taskContext.isTerminated();
                    }).get();
        }
    }

    private AnalyticFieldListConsumer createLmdbConsumer(final TableBuilderAnalytic analytic,
                                                         final Meta meta) {
        final TableBuilderAnalyticTrackerData trackerData = analytic.trackerData;

        // Create receiver to insert into the pipeline.
        // After a shutdown we may wish to resume event processing from a specific event id.
        Long minEventId = null;
        if (trackerData.getLastEventId() != null && meta.getId() == trackerData.getLastStreamId()) {
            minEventId = trackerData.getLastEventId() + 1;
        }

        final AnalyticDataStore dataStore = analytic.dataStore();
        final SearchRequest searchRequest = dataStore.searchRequest();
        final LmdbDataStore lmdbDataStore = dataStore.lmdbDataStore();

        // Cache the memory index for use across multiple streams.
        final MemoryIndex memoryIndex = memoryIndexProvider.get();

        // Get the field index.
        final FieldIndex fieldIndex = lmdbDataStore.getFieldIndex();

        final FieldValueExtractor fieldValueExtractor = fieldValueExtractorFactory
                .create(searchRequest.getQuery().getDataSource(), fieldIndex);

        return new TableBuilderAnalyticFieldListConsumer(
                searchRequest,
                fieldIndex,
                fieldValueExtractor,
                lmdbDataStore,
                memoryIndex,
                minEventId);
    }

    private boolean ignoreStream(final TableBuilderAnalytic analytic,
                                 final Meta meta,
                                 final Map<CIKey, Object> metaAttributeMap) {
        final TableBuilderAnalyticTrackerData trackerData = analytic.trackerData;
        final long minStreamId = trackerData.getMinStreamId();
        final long minCreateTime =
                AnalyticUtil.getMin(null, analytic.analyticProcessConfig.getMinMetaCreateTimeMs());
        final long maxCreateTime =
                AnalyticUtil.getMax(null, analytic.analyticProcessConfig.getMaxMetaCreateTimeMs());

        // Check this analytic should process this meta.
        return meta.getId() < minStreamId ||
                meta.getCreateMs() < minCreateTime ||
                meta.getCreateMs() > maxCreateTime ||
                !metaExpressionMatcher.match(metaAttributeMap, analytic.viewDoc().getFilter());
    }

    private void deleteOldStores() {
        final LogExecutionTime logExecutionTime = new LogExecutionTime();
        info(() -> "Deleting old stores");
        analyticDataStores.deleteOldStores();
        info(() -> LogUtil.message("Deleted old stores in {}", logExecutionTime));
    }

    private void executePostProcessNotifications(final List<TableBuilderAnalytic> analytics,
                                                 final TaskContext parentTaskContext) {
        // Execute notifications on LMDB stores and sync.
        for (final TableBuilderAnalytic analytic : analytics) {
            final AnalyticDataStore dataStore = analytic.dataStore();
            final LmdbDataStore lmdbDataStore = dataStore.lmdbDataStore();
            CurrentDbState currentDbState = lmdbDataStore.sync();

            // If we don't have any data in LMDB then we may have a currentDbState
            // but lastEventTime will not be present
            if (NullSafe.test(currentDbState, CurrentDbState::hasLastEventTime)) {
                // Remember meta load state.
                updateTrackerWithLmdbState(analytic.trackerData, currentDbState);

                // Now execute notifications.
                executeLmdbNotifications(
                        analytic,
                        dataStore,
                        currentDbState,
                        parentTaskContext);

                // Delete old data from the DB.
                applyDataRetentionRules(lmdbDataStore, analytic.analyticProcessConfig);
            }
        }
    }

    private void executeLmdbNotifications(final TableBuilderAnalytic analytic,
                                          final AnalyticDataStore dataStore,
                                          final CurrentDbState currentDbState,
                                          final TaskContext parentTaskContext) {
        try {
            executeLmdbNotification(
                    analytic,
                    dataStore,
                    currentDbState,
                    parentTaskContext);
        } catch (final TaskTerminatedException | UncheckedInterruptedException e) {
            LOGGER.debug(e::getMessage, e);
            throw e;
        } catch (final RuntimeException e) {
            LOGGER.error(e::getMessage, e);
            analytic.trackerData().setMessage(e.getMessage());
            LOGGER.info("Disabling: " + analytic.ruleIdentity());
            analyticHelper.updateTracker(analytic.tracker);
            analyticHelper.disableProcess(analytic.analyticRuleDoc);
        }
    }

    private void executeLmdbNotification(final TableBuilderAnalytic analytic,
                                         final AnalyticDataStore dataStore,
                                         final CurrentDbState currentDbState,
                                         final TaskContext parentTaskContext) {
        final Provider<DetectionConsumer> detectionConsumerProvider = detectionConsumerFactory
                .create(analytic.analyticRuleDoc());
        final String errorFeedName = analyticHelper.getErrorFeedName(analytic.analyticRuleDoc);
        analyticErrorWritingExecutor.wrap(
                "Analytics Aggregate Rule Executor",
                errorFeedName,
                null,
                parentTaskContext,
                taskContext -> {
                    final DetectionConsumer detectionConsumer = detectionConsumerProvider.get();
                    detectionConsumer.start();
                    try {
                        try {
                            runNotification(analytic,
                                    detectionConsumer,
                                    dataStore,
                                    currentDbState);
                            return true;
                        } catch (final TaskTerminatedException | UncheckedInterruptedException e) {
                            LOGGER.debug(e::getMessage, e);
                            throw e;
                        } catch (final RuntimeException e) {
                            LOGGER.error(e::getMessage, e);
                            throw e;
                        }
                    } finally {
                        detectionConsumer.end();
                    }
                }).get();
    }

    private void runNotification(final TableBuilderAnalytic analytic,
                                 final DetectionConsumer detectionConsumer,
                                 final AnalyticDataStore dataStore,
                                 final CurrentDbState currentDbState) {
        Objects.requireNonNull(currentDbState, "Null LMDB state");
        Objects.requireNonNull(currentDbState.getLastEventTime(), "Null LMDB last event time");

        final SimpleDuration timeToWaitForData = Objects.requireNonNullElseGet(
                analytic.analyticProcessConfig.getTimeToWaitForData(),
                () -> SimpleDuration.builder()
                        .time(1)
                        .timeUnit(TimeUnit.HOURS)
                        .build());

        final Instant from;
        if (analytic.trackerData.getLastWindowEndTimeMs() != null) {
            from = Instant.ofEpochMilli(analytic.trackerData.getLastWindowEndTimeMs() + 1);
        } else if (analytic.analyticProcessConfig.getMinMetaCreateTimeMs() != null) {
            from = Instant.ofEpochMilli(analytic.analyticProcessConfig.getMinMetaCreateTimeMs());
        } else {
            from = Instant.ofEpochMilli(0);
        }

        Instant to = Instant.ofEpochMilli(currentDbState.getLastEventTime());
        final Instant newTo = SimpleDurationUtil.minus(to, timeToWaitForData);
        if (!newTo.isBefore(Instant.EPOCH)) {
            to = newTo;
        }
        if (analytic.analyticProcessConfig.getMaxMetaCreateTimeMs() != null) {
            Instant max = Instant.ofEpochMilli(analytic.analyticProcessConfig.getMaxMetaCreateTimeMs());
            if (max.isBefore(to)) {
                to = max;
            }
        }

        if (to.isAfter(from)) {
            // Create a time filter.
            final TimeFilter timeFilter = new TimeFilter(from.toEpochMilli(), to.toEpochMilli());

            SearchRequest searchRequest = dataStore.searchRequest();
            final LmdbDataStore lmdbDataStore = dataStore.lmdbDataStore();
            final QueryKey queryKey = new QueryKey(analytic.analyticRuleDoc().getUuid() +
                    " - " +
                    analytic.analyticRuleDoc().getName());

            searchRequest = searchRequest.copy().key(queryKey).incremental(true).build();
            // Perform the search.
            ResultRequest resultRequest = searchRequest.getResultRequests().getFirst();
            resultRequest = resultRequest.copy().timeFilter(timeFilter).build();
            final TableResultConsumer tableResultConsumer =
                    new TableResultConsumer(analytic.analyticRuleDoc(), detectionConsumer);

            final ColumnFormatter columnFormatter =
                    new ColumnFormatter(new FormatterFactory(null));
            final TableResultCreator resultCreator = new TableResultCreator(columnFormatter) {
                @Override
                public TableResultBuilder createTableResultBuilder() {
                    return tableResultConsumer;
                }
            };

            // Create result.
            resultCreator.create(lmdbDataStore, resultRequest);

            // Remember last successful execution time.
            analytic.trackerData.setLastExecutionTimeMs(System.currentTimeMillis());
            analytic.trackerData.setLastWindowStartTimeMs(timeFilter.getFrom());
            analytic.trackerData.setLastWindowEndTimeMs(timeFilter.getTo());
            updateTrackerWithLmdbState(analytic.trackerData, currentDbState);

            analyticHelper.updateTracker(analytic.tracker);
        }
    }


    private void applyDataRetentionRules(final LmdbDataStore lmdbDataStore,
                                         final TableBuilderAnalyticProcessConfig tableBuilderAnalyticProcessConfig) {
        final SimpleDuration dataRetention = tableBuilderAnalyticProcessConfig.getDataRetention();
        if (dataRetention != null) {
            final CurrentDbState currentDbState = lmdbDataStore.sync();

            final long to = Optional
                    .ofNullable(currentDbState)
                    .map(CurrentDbState::getLastEventTime)
                    .map(ms -> SimpleDurationUtil.minus(Instant.ofEpochMilli(ms), dataRetention).toEpochMilli())
                    .orElse(0L);

            // Create a time filter.
            final TimeFilter timeFilter = new TimeFilter(
                    0,
                    to);

            // Delete old data from the DB.
            lmdbDataStore.delete(new DeleteCommand(Key.ROOT_KEY, timeFilter));
        }
    }

    private PipelineData getPipelineData(final DocRef pipelineRef) {
        // Get the translation that will be used to display results.
        final PipelineDoc pipelineDoc = pipelineStore.readDocument(pipelineRef);
        if (pipelineDoc == null) {
            throw new ExtractionException("Unable to find result pipeline: " + pipelineRef);
        }

        // Create the parser.
        return pipelineDataCache.get(pipelineDoc);
    }

    private void updateTrackerWithLmdbState(final TableBuilderAnalyticTrackerData trackerData,
                                            final CurrentDbState currentDbState) {
        if (currentDbState != null) {
            trackerData.setLastStreamId(currentDbState.getStreamId());
            trackerData.setLastEventId(currentDbState.getEventId());
            trackerData.setLastEventTime(currentDbState.getLastEventTime());
        }
    }

    private static class TableResultConsumer implements TableResultBuilder {

        private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TableResultConsumer.class);

        private final AnalyticRuleDoc analyticRuleDoc;
        private final DetectionConsumer detectionConsumer;

        private List<Column> columns;

        public TableResultConsumer(final AnalyticRuleDoc analyticRuleDoc,
                                   final DetectionConsumer detectionConsumer) {
            this.analyticRuleDoc = analyticRuleDoc;
            this.detectionConsumer = detectionConsumer;
        }

        @Override
        public TableResultConsumer componentId(final String componentId) {
            return this;
        }

        @Override
        public TableResultConsumer errors(final List<String> errors) {
            for (final String error : errors) {
                LOGGER.error(error);
            }
            return this;
        }

        @Override
        public TableResultConsumer columns(final List<Column> columns) {
            this.columns = columns;
            return this;
        }

        @Override
        public TableResultConsumer addRow(final Row row) {
            try {
                final List<DetectionValue> values = new ArrayList<>();

                int index = 0;
                Long streamId = null;
                Long eventId = null;
                for (final Column column : columns) {
                    final String fieldValue = row.getValues().get(index);
                    if (fieldValue != null) {
                        final String fieldName = column.getDisplayValue();

                        if (IndexConstants.STREAM_ID.equals(fieldName)) {
                            try {
                                streamId = Long.parseLong(fieldValue);
                            } catch (final RuntimeException e) {
                                LOGGER.debug(e.getMessage(), e);
                            }
                        } else if (IndexConstants.EVENT_ID.equals(fieldName)) {
                            try {
                                eventId = Long.parseLong(fieldValue);
                            } catch (final RuntimeException e) {
                                LOGGER.debug(e.getMessage(), e);
                            }
                        } else {
                            values.add(new DetectionValue(fieldName, fieldValue));
                        }
                    }

                    index++;
                }

                final List<DetectionLinkedEvent> linkedEvents =
                        List.of(new DetectionLinkedEvent(null, streamId, eventId));
                final Detection detection = Detection
                        .builder()
                        .withDetectTime(DateUtil.createNormalDateTimeString())
                        .withDetectorName(analyticRuleDoc.getName())
                        .withDetectorUuid(analyticRuleDoc.getUuid())
                        .withDetectorVersion(analyticRuleDoc.getVersion())
                        .withDetailedDescription(analyticRuleDoc.getDescription())
                        .withDetectionUniqueId(UUID.randomUUID().toString())
                        .withDetectionRevision(0)
                        .notDefunct()
                        .withValues(values)
                        .withLinkedEvents(linkedEvents)
                        .build();

                detectionConsumer.accept(detection);

            } catch (final TaskTerminatedException | UncheckedInterruptedException e) {
                LOGGER.debug(e::getMessage, e);
                throw e;
            } catch (final RuntimeException e) {
                LOGGER.error(e::getMessage, e);
                throw e;
            }

            return this;
        }

        @Override
        public TableResultConsumer resultRange(final OffsetRange resultRange) {
            return this;
        }

        @Override
        public TableResultConsumer totalResults(final Long totalResults) {
            return this;
        }

        @Override
        public TableResult build() {
            return null;
        }
    }

    private List<TableBuilderAnalytic> loadAnalyticRules() {
        final LogExecutionTime logExecutionTime = new LogExecutionTime();
        info(() -> "Loading rules");
        final List<TableBuilderAnalytic> analyticList = new ArrayList<>();
        final List<AnalyticRuleDoc> rules = analyticHelper.getRules();
        for (final AnalyticRuleDoc analyticRuleDoc : rules) {
            final AnalyticProcessConfig analyticProcessConfig = analyticRuleDoc.getAnalyticProcessConfig();
            if (analyticProcessConfig instanceof
                    final TableBuilderAnalyticProcessConfig tableBuilderAnalyticProcessConfig) {
                if (tableBuilderAnalyticProcessConfig.isEnabled() &&
                        nodeInfo.getThisNodeName().equals(tableBuilderAnalyticProcessConfig.getNode()) &&
                        AnalyticProcessType.TABLE_BUILDER.equals(analyticRuleDoc.getAnalyticProcessType())) {
                    final AnalyticTracker tracker = analyticHelper.getTracker(analyticRuleDoc);

                    TableBuilderAnalyticTrackerData analyticProcessorTrackerData;
                    if (tracker.getAnalyticTrackerData() instanceof
                            TableBuilderAnalyticTrackerData) {
                        analyticProcessorTrackerData = (TableBuilderAnalyticTrackerData)
                                tracker.getAnalyticTrackerData();
                    } else {
                        analyticProcessorTrackerData = new TableBuilderAnalyticTrackerData();
                        tracker.setAnalyticTrackerData(analyticProcessorTrackerData);
                    }

                    try {
                        ViewDoc viewDoc = null;

                        // Try and get view.
                        final String ruleIdentity = AnalyticUtil.getAnalyticRuleIdentity(analyticRuleDoc);
                        final SearchRequest searchRequest = analyticRuleSearchRequestHelper
                                .create(analyticRuleDoc);
                        final DocRef dataSource = searchRequest.getQuery().getDataSource();

                        if (dataSource == null || !ViewDoc.DOCUMENT_TYPE.equals(dataSource.getType())) {
                            tracker.getAnalyticTrackerData()
                                    .setMessage("Error: Rule needs to reference a view");

                        } else {
                            // Load view.
                            viewDoc = analyticHelper.loadViewDoc(ruleIdentity, dataSource);
                        }

                        if (!(analyticRuleDoc.getAnalyticProcessConfig()
                                instanceof TableBuilderAnalyticProcessConfig)) {
                            LOGGER.debug("Error: Invalid process config {}", ruleIdentity);
                            tracker.getAnalyticTrackerData()
                                    .setMessage("Error: Invalid process config.");

                        } else {
                            final AnalyticDataStore dataStore = analyticDataStores.get(analyticRuleDoc);

                            // Get or create LMDB data store.
                            final LmdbDataStore lmdbDataStore = dataStore.lmdbDataStore();
                            final CurrentDbState currentDbState = lmdbDataStore.sync();

                            // Update tracker state from LMDB.
                            updateTrackerWithLmdbState(analyticProcessorTrackerData,
                                    currentDbState);

                            analyticList.add(new TableBuilderAnalytic(
                                    ruleIdentity,
                                    analyticRuleDoc,
                                    (TableBuilderAnalyticProcessConfig) analyticRuleDoc.getAnalyticProcessConfig(),
                                    tracker,
                                    analyticProcessorTrackerData,
                                    searchRequest,
                                    viewDoc,
                                    dataStore));
                        }

                    } catch (final RuntimeException e) {
                        LOGGER.debug(e.getMessage(), e);
                        try {
                            tracker.getAnalyticTrackerData().setMessage(e.getMessage());
                            analyticTrackerDao.update(tracker);
                        } catch (final RuntimeException e2) {
                            LOGGER.error(e2::getMessage, e2);
                        }
                    }
                }
            }
        }
        info(() -> LogUtil.message("Finished loading rules in {}", logExecutionTime));
        return analyticList;
    }

    private void info(final Supplier<String> messageSupplier) {
        LOGGER.info(messageSupplier);
        taskContextFactory.current().info(messageSupplier);
    }

    private record TableBuilderAnalytic(String ruleIdentity,
                                        AnalyticRuleDoc analyticRuleDoc,
                                        TableBuilderAnalyticProcessConfig analyticProcessConfig,
                                        AnalyticTracker tracker,
                                        TableBuilderAnalyticTrackerData trackerData,
                                        SearchRequest searchRequest,
                                        ViewDoc viewDoc,
                                        AnalyticDataStore dataStore) {

    }

    private record GroupKey(DocRef pipeline, String ownerUuid) {

    }
}
