package stroom.analytics.impl;

import stroom.analytics.api.NotificationState;
import stroom.analytics.shared.AnalyticProcessConfig;
import stroom.analytics.shared.AnalyticProcessType;
import stroom.analytics.shared.AnalyticRuleDoc;
import stroom.analytics.shared.AnalyticTracker;
import stroom.analytics.shared.ScheduledQueryAnalyticProcessConfig;
import stroom.analytics.shared.ScheduledQueryAnalyticTrackerData;
import stroom.docref.DocRef;
import stroom.expression.api.DateTimeSettings;
import stroom.expression.api.ExpressionContext;
import stroom.index.shared.IndexConstants;
import stroom.node.api.NodeInfo;
import stroom.pipeline.errorhandler.ErrorReceiverProxy;
import stroom.query.api.v2.DestroyReason;
import stroom.query.api.v2.ExpressionTerm.Condition;
import stroom.query.api.v2.OffsetRange;
import stroom.query.api.v2.ParamUtil;
import stroom.query.api.v2.Query;
import stroom.query.api.v2.ResultRequest;
import stroom.query.api.v2.Row;
import stroom.query.api.v2.SearchRequest;
import stroom.query.api.v2.SearchRequestSource;
import stroom.query.api.v2.SearchRequestSource.SourceType;
import stroom.query.api.v2.TableSettings;
import stroom.query.api.v2.TimeFilter;
import stroom.query.api.v2.TimeRange;
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
import stroom.query.language.functions.ref.ErrorConsumer;
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
import stroom.util.shared.Severity;
import stroom.util.shared.time.SimpleDuration;
import stroom.util.shared.time.TimeUnit;
import stroom.util.time.SimpleDurationUtil;
import stroom.view.shared.ViewDoc;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
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
    private final AnalyticRuleSearchRequestHelper analyticRuleSearchRequestHelper;
    private final NotificationStateService notificationStateService;
    private final Provider<ErrorReceiverProxy> errorReceiverProxyProvider;
    private final DetectionConsumerFactory detectionConsumerFactory;
    private final SearchRequestFactory searchRequestFactory;
    private final ExpressionContextFactory expressionContextFactory;
    private final SecurityContext securityContext;

    @Inject
    ScheduledQueryAnalyticExecutor(final AnalyticHelper analyticHelper,
                                   final ExecutorProvider executorProvider,
                                   final ResultStoreManager searchResponseCreatorManager,
                                   final Provider<DetectionConsumerProxy> detectionConsumerProxyProvider,
                                   final Provider<AnalyticErrorWriter> analyticErrorWriterProvider,
                                   final TaskContextFactory taskContextFactory,
                                   final NodeInfo nodeInfo,
                                   final AnalyticRuleSearchRequestHelper analyticRuleSearchRequestHelper,
                                   final NotificationStateService notificationStateService,
                                   final Provider<ErrorReceiverProxy> errorReceiverProxyProvider,
                                   final DetectionConsumerFactory detectionConsumerFactory,
                                   final SearchRequestFactory searchRequestFactory,
                                   final ExpressionContextFactory expressionContextFactory,
                                   final SecurityContext securityContext) {
        this.analyticHelper = analyticHelper;
        this.executorProvider = executorProvider;
        this.searchResponseCreatorManager = searchResponseCreatorManager;
        this.detectionConsumerProxyProvider = detectionConsumerProxyProvider;
        this.analyticErrorWriterProvider = analyticErrorWriterProvider;
        this.taskContextFactory = taskContextFactory;
        this.nodeInfo = nodeInfo;
        this.analyticRuleSearchRequestHelper = analyticRuleSearchRequestHelper;
        this.notificationStateService = notificationStateService;
        this.errorReceiverProxyProvider = errorReceiverProxyProvider;
        this.detectionConsumerFactory = detectionConsumerFactory;
        this.searchRequestFactory = searchRequestFactory;
        this.expressionContextFactory = expressionContextFactory;
        this.securityContext = securityContext;
    }

    public void exec() {
        final TaskContext taskContext = taskContextFactory.current();
        final LogExecutionTime logExecutionTime = new LogExecutionTime();
        try {
            info(() -> "Starting scheduled analytic processing");

            // Load rules.
            final List<ScheduledQueryAnalytic> analytics = loadScheduledQueryAnalytics();

            info(() -> "Executing " +
                    LogUtil.namedCount("scheduled analytic", NullSafe.size(analytics)));
            final List<CompletableFuture<Void>> completableFutures = new ArrayList<>();
            processScheduledQueryAnalytics(analytics, completableFutures, taskContext);

            // Join.
            info(() -> "Executing " +
                    LogUtil.namedCount("scheduled analytic", NullSafe.size(completableFutures)));
            CompletableFuture.allOf(completableFutures.toArray(new CompletableFuture[0])).join();
            info(() -> LogUtil.message("Finished scheduled analytic processing in {}", logExecutionTime));
        } catch (final TaskTerminatedException | UncheckedInterruptedException e) {
            LOGGER.debug("Task terminated", e);
            LOGGER.debug(() -> LogUtil.message("Scheduled Analytic processing terminated after {}", logExecutionTime));
        } catch (final RuntimeException e) {
            LOGGER.error(() -> LogUtil.message("Error during scheduled analytic processing: {}", e.getMessage()), e);
        }
    }

    private void processScheduledQueryAnalytics(final List<ScheduledQueryAnalytic> analytics,
                                                final List<CompletableFuture<Void>> completableFutures,
                                                final TaskContext parentTaskContext) {
        for (final ScheduledQueryAnalytic analytic : analytics) {
            info(() -> "Executing analytic: " + analytic.ruleIdentity);
            if (!parentTaskContext.isTerminated()) {
                try {
                    final String ownerUuid = securityContext.getDocumentOwnerUuid(analytic.analyticRuleDoc.asDocRef());
                    final UserIdentity userIdentity = securityContext.createIdentityByUserUuid(ownerUuid);
                    securityContext.asUser(userIdentity, () -> securityContext.useAsRead(() ->
                            processScheduledQueryAnalytic(analytic, completableFutures, parentTaskContext)));
                } catch (final RuntimeException e) {
                    LOGGER.error(() -> "Error executing analytic: " + analytic.ruleIdentity(), e);
                }
            }
        }
    }

    private void processScheduledQueryAnalytic(final ScheduledQueryAnalytic analytic,
                                               final List<CompletableFuture<Void>> completableFutures,
                                               final TaskContext parentTaskContext) {
        SimpleDuration timeToWaitForData = analytic.analyticProcessConfig.getTimeToWaitForData();
        SimpleDuration queryFrequency = analytic.analyticProcessConfig.getQueryFrequency();
        if (timeToWaitForData == null) {
            timeToWaitForData = SimpleDuration.builder().time(1).timeUnit(TimeUnit.HOURS).build();
        }
        if (queryFrequency == null) {
            queryFrequency = SimpleDuration.builder().time(1).timeUnit(TimeUnit.HOURS).build();
        }

        // See if it is time to execute this query.
        final Instant now = Instant.now();
        final Instant nextExecution = SimpleDurationUtil.minus(now, queryFrequency);
        final Long lastExecutionTimeMs = analytic.trackerData.getLastExecutionTimeMs();
        if (lastExecutionTimeMs == null || lastExecutionTimeMs < nextExecution.toEpochMilli()) {

            Instant from = Instant.ofEpochMilli(0);
            if (analytic.trackerData.getLastWindowEndTimeMs() != null) {
                from = Instant.ofEpochMilli(analytic.trackerData.getLastWindowEndTimeMs() + 1);
            } else if (analytic.analyticProcessConfig.getMinEventTimeMs() != null) {
                from = Instant.ofEpochMilli(analytic.analyticProcessConfig.getMinEventTimeMs());
            }

            Instant to = now;
            to = SimpleDurationUtil.minus(to, timeToWaitForData);
            if (analytic.analyticProcessConfig.getMaxEventTimeMs() != null) {
                Instant max = Instant.ofEpochMilli(analytic.analyticProcessConfig.getMaxEventTimeMs());
                if (max.isBefore(to)) {
                    to = max;
                }
            }

            if (to.isAfter(from)) {
                final String errorFeedName = analyticHelper.getErrorFeedName(analytic.analyticRuleDoc);
                final TimeFilter timeFilter = new TimeFilter(from.toEpochMilli(), to.toEpochMilli());

                final Runnable runnable = taskContextFactory.childContext(
                        parentTaskContext,
                        "Scheduled Query Analytic: " + analytic.ruleIdentity(),
                        TerminateHandlerFactory.NOOP_FACTORY,
                        taskContext -> {
                            final AnalyticErrorWriter analyticErrorWriter = analyticErrorWriterProvider.get();
                            analyticErrorWriter.exec(
                                    errorFeedName,
                                    null,
                                    () -> processScheduledQueryAnalytic(analytic, timeFilter));
                        });

                try {
                    completableFutures.add(CompletableFuture.runAsync(runnable, executorProvider.get()));
                } catch (final TaskTerminatedException | UncheckedInterruptedException e) {
                    LOGGER.debug(e::getMessage, e);
                    throw e;
                } catch (final RuntimeException e) {
                    LOGGER.error(e::getMessage, e);
                    analytic.trackerData().setMessage(e.getMessage());
                    LOGGER.info("Disabling: " + analytic.ruleIdentity());
                    analyticHelper.updateTracker(analytic.tracker);
                    analyticHelper.disableProcess(analytic.analyticRuleDoc());
                }
            }
        }
    }

    private void processScheduledQueryAnalytic(final ScheduledQueryAnalytic analytic,
                                               final TimeFilter timeFilter) {
        final ErrorConsumer errorConsumer = new ErrorConsumerImpl();

        try {
            final NotificationState notificationState = notificationStateService.getState(analytic.analyticRuleDoc);
            // Only execute if the state is enabled.
            notificationState.enableIfPossible();
            if (notificationState.isEnabled()) {
                final TimeRange timeRange = new TimeRange("Custom",
                        Condition.BETWEEN,
                        DateUtil.createNormalDateTimeString(timeFilter.getFrom()),
                        DateUtil.createNormalDateTimeString(timeFilter.getTo()));

                final SearchRequestSource searchRequestSource = SearchRequestSource
                        .builder()
                        .sourceType(SourceType.SCHEDULED_QUERY_ANALYTIC)
                        .componentId(SearchRequestFactory.TABLE_COMPONENT_ID)
                        .build();

                final String query = analytic.analyticRuleDoc().getQuery();
                final Query sampleQuery = Query
                        .builder()
                        .params(null)
                        .timeRange(timeRange)
                        .build();
                final SearchRequest sampleRequest = new SearchRequest(
                        searchRequestSource,
                        null,
                        sampleQuery,
                        null,
                        DateTimeSettings.builder().build(),
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

                    final RequestAndStore requestAndStore = searchResponseCreatorManager
                            .getResultStore(mappedRequest);
                    final SearchRequest modifiedRequest = requestAndStore.searchRequest();
                    try {
                        final DataStore dataStore = requestAndStore
                                .resultStore().getData(SearchRequestFactory.TABLE_COMPONENT_ID);
                        dataStore.getCompletionState().awaitCompletion();

                        final TableSettings tableSettings = resultRequest.getMappings().getFirst();
                        final Map<String, String> paramMap = ParamUtil
                                .createParamMap(mappedRequest.getQuery().getParams());
                        final CompiledColumns compiledColumns = CompiledColumns.create(
                                expressionContext,
                                tableSettings.getColumns(),
                                paramMap);

                        final Provider<DetectionConsumer> detectionConsumerProvider =
                                detectionConsumerFactory.create(analytic.analyticRuleDoc);
                        final DetectionConsumerProxy detectionConsumerProxy = detectionConsumerProxyProvider.get();
                        detectionConsumerProxy.setAnalyticRuleDoc(analytic.analyticRuleDoc());
                        detectionConsumerProxy.setCompiledColumns(compiledColumns);
                        detectionConsumerProxy.setDetectionsConsumerProvider(detectionConsumerProvider);

                        try {
                            detectionConsumerProxy.start();
                            final AnalyticRuleDoc analyticRuleDoc = analytic.analyticRuleDoc;
                            final Consumer<Row> itemConsumer = row -> {
                                // Only notify if the state is enabled.
                                notificationState.enableIfPossible();
                                if (notificationState.incrementAndCheckEnabled()) {

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

                                    final Detection detection = new Detection(
                                            DateUtil.createNormalDateTimeString(),
                                            analyticRuleDoc.getName(),
                                            analyticRuleDoc.getUuid(),
                                            analyticRuleDoc.getVersion(),
                                            null,
                                            null,
                                            analyticRuleDoc.getDescription(),
                                            null,
                                            UUID.randomUUID().toString(),
                                            0,
                                            false,
                                            values,
                                            linkedEvents);
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
                                    errorReceiverProxyProvider.get()
                                            .getErrorReceiver()
                                            .log(Severity.ERROR, null, null, error, null);
                                }
                            }

                            detectionConsumerProxy.end();
                        }
                    } finally {
                        searchResponseCreatorManager.destroy(modifiedRequest.getKey(), DestroyReason.NO_LONGER_NEEDED);
                    }
                }
            }

            // Remember last successful execution time.
            analytic.trackerData.setLastExecutionTimeMs(System.currentTimeMillis());
            analytic.trackerData.setLastWindowStartTimeMs(timeFilter.getFrom());
            analytic.trackerData.setLastWindowEndTimeMs(timeFilter.getTo());

            analyticHelper.updateTracker(analytic.tracker);

        } catch (final Exception e) {
            try {
                LOGGER.debug(e::getMessage, e);
                errorReceiverProxyProvider.get()
                        .getErrorReceiver()
                        .log(Severity.ERROR, null, null, e.getMessage(), e);
            } catch (final RuntimeException e2) {
                LOGGER.error(e2::getMessage, e2);
            }
        }
    }

    private List<ScheduledQueryAnalytic> loadScheduledQueryAnalytics() {
        final LogExecutionTime logExecutionTime = new LogExecutionTime();
        info(() -> "Loading rules");
        final List<ScheduledQueryAnalytic> analyticList = new ArrayList<>();
        final List<AnalyticRuleDoc> rules = analyticHelper.getRules();
        for (final AnalyticRuleDoc analyticRuleDoc : rules) {
            final AnalyticProcessConfig analyticProcessConfig = analyticRuleDoc.getAnalyticProcessConfig();
            if (analyticProcessConfig instanceof
                    final ScheduledQueryAnalyticProcessConfig scheduledQueryAnalyticProcessConfig) {
                if (scheduledQueryAnalyticProcessConfig.isEnabled() &&
                        nodeInfo.getThisNodeName().equals(scheduledQueryAnalyticProcessConfig.getNode()) &&
                        AnalyticProcessType.SCHEDULED_QUERY.equals(analyticRuleDoc.getAnalyticProcessType())) {
                    final AnalyticTracker tracker = analyticHelper.getTracker(analyticRuleDoc);


                    ScheduledQueryAnalyticTrackerData analyticProcessorTrackerData;
                    if (tracker.getAnalyticTrackerData() instanceof
                            ScheduledQueryAnalyticTrackerData) {
                        analyticProcessorTrackerData = (ScheduledQueryAnalyticTrackerData)
                                tracker.getAnalyticTrackerData();
                    } else {
                        analyticProcessorTrackerData = new ScheduledQueryAnalyticTrackerData();
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
                                instanceof ScheduledQueryAnalyticProcessConfig)) {
                            LOGGER.debug("Error: Invalid process config {}", ruleIdentity);
                            tracker.getAnalyticTrackerData()
                                    .setMessage("Error: Invalid process config.");

                        } else {
                            analyticList.add(new ScheduledQueryAnalytic(
                                    ruleIdentity,
                                    analyticRuleDoc,
                                    (ScheduledQueryAnalyticProcessConfig) analyticRuleDoc.getAnalyticProcessConfig(),
                                    tracker,
                                    analyticProcessorTrackerData,
                                    searchRequest,
                                    viewDoc));
                        }

                    } catch (final RuntimeException e) {
                        LOGGER.debug(e.getMessage(), e);
                        try {
                            tracker.getAnalyticTrackerData().setMessage(e.getMessage());
                            analyticHelper.updateTracker(tracker);
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

    private record ScheduledQueryAnalytic(String ruleIdentity,
                                          AnalyticRuleDoc analyticRuleDoc,
                                          ScheduledQueryAnalyticProcessConfig analyticProcessConfig,
                                          AnalyticTracker tracker,
                                          ScheduledQueryAnalyticTrackerData trackerData,
                                          SearchRequest searchRequest,
                                          ViewDoc viewDoc) {

    }
}
