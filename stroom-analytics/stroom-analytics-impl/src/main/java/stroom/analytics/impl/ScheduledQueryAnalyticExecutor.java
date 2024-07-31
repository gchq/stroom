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

import stroom.analytics.shared.AnalyticProcessType;
import stroom.analytics.shared.AnalyticRuleDoc;
import stroom.analytics.shared.ExecutionHistory;
import stroom.analytics.shared.ExecutionSchedule;
import stroom.analytics.shared.ExecutionScheduleRequest;
import stroom.analytics.shared.ExecutionTracker;
import stroom.analytics.shared.ScheduleBounds;
import stroom.docref.StringMatch;
import stroom.expression.api.DateTimeSettings;
import stroom.index.shared.IndexConstants;
import stroom.node.api.NodeInfo;
import stroom.pipeline.errorhandler.ErrorReceiverProxy;
import stroom.query.api.v2.DestroyReason;
import stroom.query.api.v2.OffsetRange;
import stroom.query.api.v2.ParamUtil;
import stroom.query.api.v2.Query;
import stroom.query.api.v2.ResultRequest;
import stroom.query.api.v2.Row;
import stroom.query.api.v2.SearchRequest;
import stroom.query.api.v2.SearchRequestSource;
import stroom.query.api.v2.SearchRequestSource.SourceType;
import stroom.query.api.v2.TableSettings;
import stroom.query.common.v2.CompiledColumns;
import stroom.query.common.v2.DataStore;
import stroom.query.common.v2.ErrorConsumerImpl;
import stroom.query.common.v2.ExpressionContextFactory;
import stroom.query.common.v2.FilteredRowCreator;
import stroom.query.common.v2.ItemMapper;
import stroom.query.common.v2.KeyFactory;
import stroom.query.common.v2.OpenGroups;
import stroom.query.common.v2.ResultStoreManager;
import stroom.query.common.v2.ResultStoreManager.RequestAndStore;
import stroom.query.common.v2.SimpleRowCreator;
import stroom.query.common.v2.format.ColumnFormatter;
import stroom.query.common.v2.format.FormatterFactory;
import stroom.query.language.SearchRequestFactory;
import stroom.query.language.functions.ExpressionContext;
import stroom.query.language.functions.ref.ErrorConsumer;
import stroom.security.api.SecurityContext;
import stroom.security.api.UserIdentity;
import stroom.task.api.ExecutorProvider;
import stroom.task.api.TaskContext;
import stroom.task.api.TaskContextFactory;
import stroom.task.api.TaskTerminatedException;
import stroom.util.NullSafe;
import stroom.util.concurrent.UncheckedInterruptedException;
import stroom.util.concurrent.WorkQueue;
import stroom.util.date.DateUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogExecutionTime;
import stroom.util.logging.LogUtil;
import stroom.util.scheduler.Trigger;
import stroom.util.scheduler.TriggerFactory;
import stroom.util.shared.ResultPage;
import stroom.util.shared.Severity;
import stroom.util.shared.scheduler.Schedule;
import stroom.util.shared.string.CIKey;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class ScheduledQueryAnalyticExecutor {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ScheduledQueryAnalyticExecutor.class);

    private final AnalyticHelper analyticHelper;
    private final ExecutorProvider executorProvider;
    private final ResultStoreManager searchResponseCreatorManager;
    private final Provider<DetectionConsumerProxy> detectionConsumerProxyProvider;
    private final Provider<AnalyticErrorWriter> analyticErrorWriterProvider;
    private final TaskContextFactory taskContextFactory;
    private final NodeInfo nodeInfo;
    private final Provider<ErrorReceiverProxy> errorReceiverProxyProvider;
    private final DetectionConsumerFactory detectionConsumerFactory;
    private final SearchRequestFactory searchRequestFactory;
    private final ExpressionContextFactory expressionContextFactory;
    private final SecurityContext securityContext;
    private final ExecutionScheduleDao executionScheduleDao;
    private final DuplicateCheckFactory duplicateCheckFactory;
    private final DuplicateCheckDirs duplicateCheckDirs;

    @Inject
    ScheduledQueryAnalyticExecutor(final AnalyticHelper analyticHelper,
                                   final ExecutorProvider executorProvider,
                                   final ResultStoreManager searchResponseCreatorManager,
                                   final Provider<DetectionConsumerProxy> detectionConsumerProxyProvider,
                                   final Provider<AnalyticErrorWriter> analyticErrorWriterProvider,
                                   final TaskContextFactory taskContextFactory,
                                   final NodeInfo nodeInfo,
                                   final Provider<ErrorReceiverProxy> errorReceiverProxyProvider,
                                   final DetectionConsumerFactory detectionConsumerFactory,
                                   final SearchRequestFactory searchRequestFactory,
                                   final ExpressionContextFactory expressionContextFactory,
                                   final SecurityContext securityContext,
                                   final ExecutionScheduleDao executionScheduleDao,
                                   final DuplicateCheckFactory duplicateCheckFactory,
                                   final DuplicateCheckDirs duplicateCheckDirs) {
        this.analyticHelper = analyticHelper;
        this.executorProvider = executorProvider;
        this.searchResponseCreatorManager = searchResponseCreatorManager;
        this.detectionConsumerProxyProvider = detectionConsumerProxyProvider;
        this.analyticErrorWriterProvider = analyticErrorWriterProvider;
        this.taskContextFactory = taskContextFactory;
        this.nodeInfo = nodeInfo;
        this.errorReceiverProxyProvider = errorReceiverProxyProvider;
        this.detectionConsumerFactory = detectionConsumerFactory;
        this.searchRequestFactory = searchRequestFactory;
        this.expressionContextFactory = expressionContextFactory;
        this.securityContext = securityContext;
        this.executionScheduleDao = executionScheduleDao;
        this.duplicateCheckFactory = duplicateCheckFactory;
        this.duplicateCheckDirs = duplicateCheckDirs;
    }

    public void exec() {
        final TaskContext taskContext = taskContextFactory.current();
        final LogExecutionTime logExecutionTime = new LogExecutionTime();
        try {
            info(() -> "Starting scheduled analytic processing");

            // Start by finding a set of UUIDs for existing rule checking stores.
            final List<String> duplicateStoreDirs = duplicateCheckDirs.getAnalyticRuleUUIDList();

            // Load rules.
            final List<AnalyticRuleDoc> analytics = loadScheduledQueryAnalytics();

            info(() -> "Processing " + LogUtil.namedCount("scheduled analytic rule", NullSafe.size(analytics)));
            final WorkQueue workQueue = new WorkQueue(executorProvider.get(), 1, 1);
            for (final AnalyticRuleDoc analytic : analytics) {
                final Runnable runnable = () -> process(
                        analytic,
                        taskContext);
                try {
                    workQueue.exec(runnable);
                } catch (final TaskTerminatedException | UncheckedInterruptedException e) {
                    LOGGER.debug(e::getMessage, e);
                    throw e;
                } catch (final RuntimeException e) {
                    LOGGER.error(e::getMessage, e);
                }
            }

            // Join.
            workQueue.join();

            // Delete unused duplicate stores.
            duplicateCheckDirs.deleteUnused(duplicateStoreDirs, analytics);

            info(() -> LogUtil.message("Finished scheduled analytic processing in {}", logExecutionTime));
        } catch (final TaskTerminatedException | UncheckedInterruptedException e) {
            LOGGER.debug("Task terminated", e);
            LOGGER.debug(() -> LogUtil.message("Scheduled Analytic processing terminated after {}", logExecutionTime));
        } catch (final RuntimeException e) {
            LOGGER.error(() -> LogUtil.message("Error during scheduled analytic processing: {}", e.getMessage()), e);
        }
    }

    private void process(final AnalyticRuleDoc analytic,
                         final TaskContext parentTaskContext) {
        if (!parentTaskContext.isTerminated()) {
            final String ruleIdentity = AnalyticUtil.getAnalyticRuleIdentity(analytic);
            try {
                final String ownerUuid = securityContext.getDocumentOwnerUuid(analytic.asDocRef());
                final UserIdentity userIdentity = securityContext.getIdentityByUserUuid(ownerUuid);
                securityContext.asUser(userIdentity, () ->
                        process(
                                ruleIdentity,
                                analytic,
                                userIdentity,
                                parentTaskContext));
            } catch (final RuntimeException e) {
                LOGGER.error(() -> "Error executing rule: " + ruleIdentity, e);
            }
        }
    }

    private void process(final String ruleIdentity,
                         final AnalyticRuleDoc analytic,
                         final UserIdentity userIdentity,
                         final TaskContext parentTaskContext) {
        // Load schedules for the analytic.
        final ExecutionScheduleRequest request = ExecutionScheduleRequest
                .builder()
                .ownerDocRef(analytic.asDocRef())
                .enabled(true)
                .nodeName(StringMatch.equalsIgnoreCase(nodeInfo.getThisNodeName()))
                .build();

        final ResultPage<ExecutionSchedule> executionSchedules = executionScheduleDao.fetchExecutionSchedule(request);
        final WorkQueue workQueue = new WorkQueue(executorProvider.get(), 1, 1);
        for (final ExecutionSchedule executionSchedule : executionSchedules.getValues()) {
            final Runnable runnable = () -> {
                try {
                    // We need to set the user again here as it will have been lost from the parent context as we are
                    // running within a new thread.
                    securityContext.asUser(userIdentity, () -> securityContext.useAsRead(() -> {
                        boolean success = true;
                        while (success && !parentTaskContext.isTerminated()) {
                            success = process(ruleIdentity, analytic, parentTaskContext, executionSchedule);
                        }
                    }));
                } catch (final RuntimeException e) {
                    LOGGER.error(() -> "Error executing rule: " + ruleIdentity, e);
                }
            };
            workQueue.exec(runnable);
        }
        workQueue.join();
    }

    private boolean process(final String ruleIdentity,
                            final AnalyticRuleDoc analytic,
                            final TaskContext parentTaskContext,
                            final ExecutionSchedule executionSchedule) {
        final Optional<ExecutionSchedule> optionalSchedule = executionScheduleDao
                .fetchScheduleById(executionSchedule.getId());
        if (optionalSchedule.isEmpty()) {
            return false;
        }
        final ExecutionSchedule schedule = optionalSchedule.get();
        if (!schedule.isEnabled()) {
            return false;
        }

        return taskContextFactory.childContextResult(
                parentTaskContext,
                "Scheduled Query Analytic: " +
                        ruleIdentity,
                taskContext -> process(
                        ruleIdentity,
                        analytic,
                        schedule,
                        taskContext)).get();
    }

    private boolean process(final String ruleIdentity,
                            final AnalyticRuleDoc analytic,
                            final ExecutionSchedule executionSchedule,
                            final TaskContext taskContext) {
        final ExecutionTracker currentTracker = executionScheduleDao.getTracker(executionSchedule).orElse(null);
        final Schedule schedule = executionSchedule.getSchedule();
        final ScheduleBounds scheduleBounds = executionSchedule.getScheduleBounds();

        // See if it is time to execute this query.
        final Instant executionTime = Instant.now();
        final Trigger trigger = TriggerFactory.create(schedule);

        final Instant effectiveExecutionTime;
        if (currentTracker != null) {
            effectiveExecutionTime = Instant.ofEpochMilli(currentTracker.getNextEffectiveExecutionTimeMs());
        } else {
            if (scheduleBounds != null && scheduleBounds.getStartTimeMs() != null) {
                effectiveExecutionTime = Instant.ofEpochMilli(scheduleBounds.getStartTimeMs());
            } else {
                effectiveExecutionTime = trigger.getNextExecutionTimeAfter(executionTime);
            }
        }

        // Calculate end bounds.
        Instant endTime = Instant.MAX;
        if (scheduleBounds != null && scheduleBounds.getEndTimeMs() != null) {
            endTime = Instant.ofEpochMilli(scheduleBounds.getEndTimeMs());
        }

        if (!effectiveExecutionTime.isAfter(executionTime) && !effectiveExecutionTime.isAfter(endTime)) {
            taskContext.info(() -> "Executing schedule '" +
                    executionSchedule.getName() +
                    "' with effective time: " +
                    DateUtil.createNormalDateTimeString(effectiveExecutionTime.toEpochMilli()));
            final String errorFeedName = analyticHelper.getErrorFeedName(analytic);
            final AnalyticErrorWriter analyticErrorWriter = analyticErrorWriterProvider.get();
            return analyticErrorWriter.exec(
                    errorFeedName,
                    null,
                    taskContext,
                    (t) -> process(
                            ruleIdentity,
                            analytic,
                            trigger,
                            executionTime,
                            effectiveExecutionTime,
                            executionSchedule,
                            currentTracker));
        }
        return false;
    }

    private boolean process(final String ruleIdentity,
                            final AnalyticRuleDoc analytic,
                            final Trigger trigger,
                            final Instant executionTime,
                            final Instant effectiveExecutionTime,
                            final ExecutionSchedule executionSchedule,
                            final ExecutionTracker currentTracker) {
        boolean success = false;
        final ErrorConsumer errorConsumer = new ErrorConsumerImpl();
        ExecutionResult executionResult = new ExecutionResult(null, null);

        try {
            final SearchRequestSource searchRequestSource = SearchRequestSource
                    .builder()
                    .sourceType(SourceType.SCHEDULED_QUERY_ANALYTIC)
                    .componentId(SearchRequestFactory.TABLE_COMPONENT_ID)
                    .build();

            final String query = analytic.getQuery();
            final Query sampleQuery = Query
                    .builder()
                    .params(analytic.getParameters())
                    .timeRange(analytic.getTimeRange())
                    .build();
            final SearchRequest sampleRequest = new SearchRequest(
                    searchRequestSource,
                    null,
                    sampleQuery,
                    null,
                    DateTimeSettings.builder().referenceTime(effectiveExecutionTime.toEpochMilli()).build(),
                    false);
            final ExpressionContext expressionContext = expressionContextFactory.createContext(sampleRequest);
            SearchRequest mappedRequest = searchRequestFactory.create(query, sampleRequest, expressionContext);

            // Fix table result requests.
            final List<ResultRequest> resultRequests = mappedRequest.getResultRequests();
            if (resultRequests != null && resultRequests.size() == 1) {
                final ResultRequest resultRequest = resultRequests.getFirst().copy()
                        .openGroups(null)
                        .requestedRange(OffsetRange.UNBOUNDED)
                        .build();

                // Create a result store and begin search.
                final RequestAndStore requestAndStore = searchResponseCreatorManager
                        .getResultStore(mappedRequest);
                final SearchRequest modifiedRequest = requestAndStore.searchRequest();
                try {
                    final DataStore dataStore = requestAndStore
                            .resultStore().getData(SearchRequestFactory.TABLE_COMPONENT_ID);
                    // Wait for search to complete.
                    dataStore.getCompletionState().awaitCompletion();

                    // Now consume all results as detections.
                    final TableSettings tableSettings = resultRequest.getMappings().getFirst();
                    final Map<CIKey, String> paramMap = ParamUtil
                            .createParamMap(mappedRequest.getQuery().getParams());
                    final CompiledColumns compiledColumns = CompiledColumns.create(
                            expressionContext,
                            tableSettings.getColumns(),
                            paramMap);

                    final Provider<DetectionConsumer> detectionConsumerProvider =
                            detectionConsumerFactory.create(analytic);
                    final DetectionConsumerProxy detectionConsumerProxy = detectionConsumerProxyProvider.get();
                    detectionConsumerProxy.setAnalyticRuleDoc(analytic);
                    detectionConsumerProxy.setExecutionSchedule(executionSchedule);
                    detectionConsumerProxy.setCompiledColumns(compiledColumns);
                    detectionConsumerProxy.setDetectionsConsumerProvider(detectionConsumerProvider);

                    try (final DuplicateCheck duplicateCheck =
                            duplicateCheckFactory.create(analytic, compiledColumns)) {
                        detectionConsumerProxy.start();
                        final Consumer<Row> itemConsumer = row -> {
                            if (duplicateCheck.check(row)) {
                                Long streamId = null;
                                Long eventId = null;
                                final List<DetectionValue> values = new ArrayList<>();
                                for (int i = 0; i < dataStore.getColumns().size(); i++) {
                                    if (i < row.getValues().size()) {
                                        final String columnName = dataStore.getColumns().get(i).getName();
                                        final String value = row.getValues().get(i);
                                        if (value != null) {
                                            if (IndexConstants.STREAM_ID.equals(columnName)) {
                                                streamId = DetectionConsumerProxy.getSafeLong(value);
                                            } else if (IndexConstants.EVENT_ID.equals(columnName)) {
                                                eventId = DetectionConsumerProxy.getSafeLong(value);
                                            }
                                            values.add(new DetectionValue(columnName, value));
                                        }
                                    }
                                }

                                List<DetectionLinkedEvent> linkedEvents = null;
                                if (streamId != null || eventId != null) {
                                    linkedEvents = List.of(new DetectionLinkedEvent(null, streamId, eventId));
                                }

                                final Detection detection = Detection
                                        .builder()
                                        .withDetectTime(DateUtil.createNormalDateTimeString())
                                        .withDetectorName(analytic.getName())
                                        .withDetectorUuid(analytic.getUuid())
                                        .withDetectorVersion(analytic.getVersion())
                                        .withDetailedDescription(analytic.getDescription())
                                        .withRandomDetectionUniqueId()
                                        .withDetectionRevision(0)
                                        .withExecutionSchedule(NullSafe
                                                .get(executionSchedule, ExecutionSchedule::getName))
                                        .withExecutionTime(executionTime)
                                        .withEffectiveExecutionTime(effectiveExecutionTime)
                                        .notDefunct()
                                        .withValues(values)
                                        .withLinkedEvents(linkedEvents)
                                        .build();
                                detectionConsumerProxy.getDetectionConsumer().accept(detection);
                            }
                        };
                        final Consumer<Long> countConsumer = count -> {

                        };

                        final KeyFactory keyFactory = dataStore.getKeyFactory();
                        final ColumnFormatter fieldFormatter =
                                new ColumnFormatter(
                                        new FormatterFactory(sampleRequest.getDateTimeSettings()));

                        // Create the row creator.
                        Optional<ItemMapper<Row>> optionalRowCreator = FilteredRowCreator.create(
                                fieldFormatter,
                                keyFactory,
                                tableSettings.getAggregateFilter(),
                                dataStore.getColumns(),
                                expressionContext.getDateTimeSettings(),
                                errorConsumer);

                        if (optionalRowCreator.isEmpty()) {
                            optionalRowCreator = SimpleRowCreator.create(fieldFormatter, keyFactory, errorConsumer);
                        }

                        final ItemMapper<Row> rowCreator = optionalRowCreator.orElse(null);

                        dataStore.fetch(
                                OffsetRange.UNBOUNDED,
                                OpenGroups.NONE,
                                resultRequest.getTimeFilter(),
                                rowCreator,
                                itemConsumer,
                                countConsumer);

                    } finally {
                        final List<String> errors = errorConsumer.getErrors();
                        if (errors != null) {
                            for (final String error : errors) {
                                if (executionResult.status == null) {
                                    executionResult = new ExecutionResult("Error", error);
                                }

                                errorReceiverProxyProvider.get()
                                        .getErrorReceiver()
                                        .log(Severity.ERROR, null, null, error, null);
                            }
                        }

                        detectionConsumerProxy.end();
                    }
                } finally {
                    // Destroy search result store.
                    searchResponseCreatorManager.destroy(modifiedRequest.getKey(), DestroyReason.NO_LONGER_NEEDED);
                }
            }

            // Remember last successful execution time and compute next execution time.
            final Instant now = Instant.now();
            final Instant nextExecutionTime;
            if (executionSchedule.isContiguous()) {
                nextExecutionTime = trigger.getNextExecutionTimeAfter(effectiveExecutionTime);
            } else {
                nextExecutionTime = trigger.getNextExecutionTimeAfter(now);
            }

            // Update tracker.
            final ExecutionTracker executionTracker = new ExecutionTracker(
                    now.toEpochMilli(),
                    effectiveExecutionTime.toEpochMilli(),
                    nextExecutionTime.toEpochMilli());
            if (currentTracker != null) {
                executionScheduleDao.updateTracker(executionSchedule, executionTracker);
            } else {
                executionScheduleDao.createTracker(executionSchedule, executionTracker);
            }

            if (executionResult.status == null) {
                executionResult = new ExecutionResult("Complete", executionResult.message);
                success = true;
            }

        } catch (final Exception e) {
            executionResult = new ExecutionResult("Error", e.getMessage());

            try {
                LOGGER.debug(e::getMessage, e);
                errorReceiverProxyProvider.get()
                        .getErrorReceiver()
                        .log(Severity.ERROR, null, null, e.getMessage(), e);
            } catch (final RuntimeException e2) {
                LOGGER.error(e2::getMessage, e2);
            }

            // Disable future execution.
            LOGGER.info("Disabling: " + ruleIdentity);
            executionScheduleDao.updateExecutionSchedule(executionSchedule.copy().enabled(false).build());

        } finally {
            // Record the execution.
            addExecutionHistory(executionSchedule,
                    executionTime,
                    effectiveExecutionTime,
                    executionResult);
        }

        return success;
    }

    private record ExecutionResult(String status, String message) {

    }

    private void addExecutionHistory(final ExecutionSchedule executionSchedule,
                                     final Instant executionTime,
                                     final Instant effectiveExecutionTime,
                                     final ExecutionResult executionResult) {
        try {
            final ExecutionHistory executionHistory = ExecutionHistory
                    .builder()
                    .executionSchedule(executionSchedule)
                    .executionTimeMs(executionTime.toEpochMilli())
                    .effectiveExecutionTimeMs(effectiveExecutionTime.toEpochMilli())
                    .status(executionResult.status)
                    .message(executionResult.message)
                    .build();
            executionScheduleDao.addExecutionHistory(executionHistory);
        } catch (final Exception e) {
            LOGGER.error(e::getMessage, e);
        }
    }

    private List<AnalyticRuleDoc> loadScheduledQueryAnalytics() {
        final LogExecutionTime logExecutionTime = new LogExecutionTime();
        info(() -> "Loading rules");
        final List<AnalyticRuleDoc> analyticList = new ArrayList<>();
        final List<AnalyticRuleDoc> rules = analyticHelper.getRules();
        for (final AnalyticRuleDoc analyticRuleDoc : rules) {
            if (AnalyticProcessType.SCHEDULED_QUERY.equals(analyticRuleDoc.getAnalyticProcessType())) {
                analyticList.add(analyticRuleDoc);
            }
        }
        info(() -> LogUtil.message("Finished loading rules in {}", logExecutionTime));
        return analyticList;
    }

    private void info(final Supplier<String> messageSupplier) {
        LOGGER.info(messageSupplier);
        taskContextFactory.current().info(messageSupplier);
    }
}
