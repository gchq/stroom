package stroom.analytics.impl;

import stroom.analytics.shared.AnalyticNotificationStreamConfig;
import stroom.analytics.shared.AnalyticProcess;
import stroom.analytics.shared.AnalyticProcessTracker;
import stroom.analytics.shared.AnalyticProcessType;
import stroom.analytics.shared.AnalyticRuleDoc;
import stroom.analytics.shared.ScheduledQueryAnalyticProcessConfig;
import stroom.analytics.shared.ScheduledQueryAnalyticProcessTrackerData;
import stroom.dashboard.expression.v1.FieldIndex;
import stroom.dashboard.expression.v1.Val;
import stroom.docref.DocRef;
import stroom.node.api.NodeInfo;
import stroom.query.api.v2.DestroyReason;
import stroom.query.api.v2.ExpressionTerm.Condition;
import stroom.query.api.v2.OffsetRange;
import stroom.query.api.v2.ParamUtil;
import stroom.query.api.v2.Query;
import stroom.query.api.v2.ResultRequest;
import stroom.query.api.v2.SearchRequest;
import stroom.query.api.v2.SearchRequestSource;
import stroom.query.api.v2.SearchRequestSource.SourceType;
import stroom.query.api.v2.TableSettings;
import stroom.query.api.v2.TimeFilter;
import stroom.query.api.v2.TimeRange;
import stroom.query.common.v2.CompiledFields;
import stroom.query.common.v2.DataStore;
import stroom.query.common.v2.IdentityItemMapper;
import stroom.query.common.v2.Item;
import stroom.query.common.v2.OpenGroups;
import stroom.query.common.v2.ResultStoreManager;
import stroom.query.common.v2.ResultStoreManager.RequestAndStore;
import stroom.query.language.DataSourceResolver;
import stroom.query.language.SearchRequestBuilder;
import stroom.task.api.ExecutorProvider;
import stroom.task.api.TaskContext;
import stroom.task.api.TaskContextFactory;
import stroom.task.api.TaskTerminatedException;
import stroom.util.concurrent.UncheckedInterruptedException;
import stroom.util.date.DateUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogExecutionTime;
import stroom.util.logging.LogUtil;
import stroom.util.shared.time.SimpleDuration;
import stroom.util.shared.time.TimeUnit;
import stroom.util.time.SimpleDurationUtil;
import stroom.view.shared.ViewDoc;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import javax.inject.Inject;
import javax.inject.Provider;

public class ScheduledQueryAnalyticExecutor {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ScheduledQueryAnalyticExecutor.class);

    private final AnalyticHelper analyticHelper;
    private final DataSourceResolver dataSourceResolver;
    private final ExecutorProvider executorProvider;
    private final ResultStoreManager searchResponseCreatorManager;
    private final Provider<DetectionWriterProxy> detectionWriterProxyProvider;
    private final AnalyticErrorWritingExecutor analyticErrorWritingExecutor;
    private final TaskContextFactory taskContextFactory;
    private final NodeInfo nodeInfo;
    private final AnalyticRuleSearchRequestHelper analyticRuleSearchRequestHelper;

    @Inject
    ScheduledQueryAnalyticExecutor(final AnalyticHelper analyticHelper,
                                   final DataSourceResolver dataSourceResolver,
                                   final ExecutorProvider executorProvider,
                                   final ResultStoreManager searchResponseCreatorManager,
                                   final Provider<DetectionWriterProxy> detectionWriterProxyProvider,
                                   final AnalyticErrorWritingExecutor analyticErrorWritingExecutor,
                                   final TaskContextFactory taskContextFactory,
                                   final NodeInfo nodeInfo,
                                   final AnalyticRuleSearchRequestHelper analyticRuleSearchRequestHelper) {
        this.analyticHelper = analyticHelper;
        this.dataSourceResolver = dataSourceResolver;
        this.executorProvider = executorProvider;
        this.searchResponseCreatorManager = searchResponseCreatorManager;
        this.detectionWriterProxyProvider = detectionWriterProxyProvider;
        this.analyticErrorWritingExecutor = analyticErrorWritingExecutor;
        this.taskContextFactory = taskContextFactory;
        this.nodeInfo = nodeInfo;
        this.analyticRuleSearchRequestHelper = analyticRuleSearchRequestHelper;
    }

    public void exec() {
        // Load rules.
        final List<ScheduledQueryAnalytic> analytics = loadScheduledQueryAnalytics();

        analyticHelper.info(() -> "Processing batch rules");
        final List<CompletableFuture<Void>> completableFutures = new ArrayList<>();
        processScheduledQueryAnalytics(analytics, completableFutures, taskContextFactory.current());

        // Join.
        CompletableFuture.allOf(completableFutures.toArray(new CompletableFuture[0])).join();
    }

    private void processScheduledQueryAnalytics(final List<ScheduledQueryAnalytic> analytics,
                                                final List<CompletableFuture<Void>> completableFutures,
                                                final TaskContext parentTaskContext) {
        for (final ScheduledQueryAnalytic analytic : analytics) {
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
                    if (analytic.analyticRuleDoc.getAnalyticNotificationConfig() instanceof
                            final AnalyticNotificationStreamConfig analyticNotificationStreamConfig) {
                        final String errorFeedName = analyticNotificationStreamConfig.getDestinationFeed().getName();
                        final TimeFilter timeFilter = new TimeFilter(from.toEpochMilli(), to.toEpochMilli());
                        final Runnable runnable = analyticErrorWritingExecutor.wrap(
                                "Scheduled Query Analytic: " + analytic.ruleIdentity(),
                                errorFeedName,
                                null,
                                parentTaskContext,
                                taskContext -> processScheduledQueryAnalytic(
                                        analytic,
                                        analyticNotificationStreamConfig,
                                        timeFilter));

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
                            analyticHelper.disableProcess(analytic.analyticProcess());
                        }
                    }
                }
            }
        }
    }

    private void processScheduledQueryAnalytic(final ScheduledQueryAnalytic analytic,
                                               final AnalyticNotificationStreamConfig streamConfig,
                                               final TimeFilter timeFilter) {
        try {
            final TimeRange timeRange = new TimeRange("Custom",
                    Condition.BETWEEN,
                    DateUtil.createNormalDateTimeString(timeFilter.getFrom()),
                    DateUtil.createNormalDateTimeString(timeFilter.getTo()));

            final SearchRequestSource searchRequestSource = SearchRequestSource
                    .builder()
                    .sourceType(SourceType.BATCH_ANALYTIC_RULE)
                    .componentId(SearchRequestBuilder.COMPONENT_ID)
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
                    null,
                    false);
            SearchRequest mappedRequest = SearchRequestBuilder.create(query, sampleRequest);
            mappedRequest = dataSourceResolver.resolveDataSource(mappedRequest);

            // Fix table result requests.
            final List<ResultRequest> resultRequests = mappedRequest.getResultRequests();
            if (resultRequests != null && resultRequests.size() == 1) {
                final ResultRequest resultRequest = resultRequests.get(0).copy()
                        .openGroups(null)
                        .requestedRange(OffsetRange.UNBOUNDED)
                        .build();

                final RequestAndStore requestAndStore = searchResponseCreatorManager
                        .getResultStore(mappedRequest);
                final SearchRequest modifiedRequest = requestAndStore.searchRequest();
                try {
                    final DataStore dataStore = requestAndStore
                            .resultStore().getData(SearchRequestBuilder.COMPONENT_ID);
                    dataStore.getCompletionState().awaitCompletion();

                    final TableSettings tableSettings = resultRequest.getMappings().get(0);
                    final Map<String, String> paramMap = ParamUtil
                            .createParamMap(mappedRequest.getQuery().getParams());
                    final CompiledFields compiledFields = CompiledFields.create(tableSettings.getFields(),
                            paramMap);
                    final FieldIndex fieldIndex = compiledFields.getFieldIndex();

                    final DetectionWriterProxy detectionWriter = detectionWriterProxyProvider.get();
                    detectionWriter.setAnalyticRuleDoc(analytic.analyticRuleDoc());
                    detectionWriter.setCompiledFields(compiledFields);
                    detectionWriter.setFieldIndex(fieldIndex);
                    detectionWriter.setDestinationFeed(streamConfig.getDestinationFeed());

                    try {
                        detectionWriter.start();

                        final Consumer<Item> itemConsumer = item -> {
                            final Val[] vals = new Val[compiledFields.getCompiledFields().length];
                            for (int i = 0; i < vals.length; i++) {
                                vals[i] = item.getValue(i);
                            }
                            detectionWriter.accept(vals);
                        };
                        final Consumer<Long> countConsumer = count -> {

                        };
                        dataStore.fetch(
                                OffsetRange.UNBOUNDED,
                                OpenGroups.NONE,
                                resultRequest.getTimeFilter(),
                                IdentityItemMapper.INSTANCE,
                                itemConsumer,
                                countConsumer);

                    } finally {
                        detectionWriter.end();
                    }
                } finally {
                    searchResponseCreatorManager.destroy(modifiedRequest.getKey(), DestroyReason.NO_LONGER_NEEDED);
                }
            }

            // Remember last successful execution time.
            analytic.trackerData.setLastExecutionTimeMs(System.currentTimeMillis());
            analytic.trackerData.setLastWindowStartTimeMs(timeFilter.getFrom());
            analytic.trackerData.setLastWindowEndTimeMs(timeFilter.getTo());

            analyticHelper.updateTracker(analytic.tracker);

        } catch (final Exception e) {
            LOGGER.error(e::getMessage, e);
        }
    }

    private List<ScheduledQueryAnalytic> loadScheduledQueryAnalytics() {
        final LogExecutionTime logExecutionTime = new LogExecutionTime();
        analyticHelper.info(() -> "Loading rules");
        final List<ScheduledQueryAnalytic> analyticList = new ArrayList<>();
        final List<AnalyticRuleDoc> rules = analyticHelper.getRules();
        for (final AnalyticRuleDoc analyticRuleDoc : rules) {
            final Optional<AnalyticProcess> optionalProcess = analyticHelper.getProcess(analyticRuleDoc);
            optionalProcess.ifPresent(process -> {
                if (process.isEnabled() &&
                        nodeInfo.getThisNodeName().equals(process.getNode()) &&
                        AnalyticProcessType.SCHEDULED_QUERY.equals(analyticRuleDoc.getAnalyticProcessType())) {
                    final AnalyticProcessTracker tracker = analyticHelper.getTracker(process);


                    ScheduledQueryAnalyticProcessTrackerData analyticProcessorTrackerData;
                    if (tracker.getAnalyticProcessTrackerData() instanceof
                            ScheduledQueryAnalyticProcessTrackerData) {
                        analyticProcessorTrackerData = (ScheduledQueryAnalyticProcessTrackerData)
                                tracker.getAnalyticProcessTrackerData();
                    } else {
                        analyticProcessorTrackerData = new ScheduledQueryAnalyticProcessTrackerData();
                        tracker.setAnalyticProcessTrackerData(analyticProcessorTrackerData);
                    }

                    try {
                        ViewDoc viewDoc = null;

                        // Try and get view.
                        final String ruleIdentity = AnalyticHelper.getAnalyticRuleIdentity(analyticRuleDoc);
                        final SearchRequest searchRequest = analyticRuleSearchRequestHelper
                                .create(analyticRuleDoc);
                        final DocRef dataSource = searchRequest.getQuery().getDataSource();

                        if (dataSource == null || !ViewDoc.DOCUMENT_TYPE.equals(dataSource.getType())) {
                            tracker.getAnalyticProcessTrackerData()
                                    .setMessage("Error: Rule needs to reference a view");

                        } else {
                            // Load view.
                            viewDoc = analyticHelper.loadViewDoc(ruleIdentity, dataSource);
                        }

                        if (!(analyticRuleDoc.getAnalyticProcessConfig()
                                instanceof ScheduledQueryAnalyticProcessConfig)) {
                            LOGGER.debug("Error: Invalid process config {}",
                                    AnalyticHelper.getAnalyticRuleIdentity(analyticRuleDoc));
                            tracker.getAnalyticProcessTrackerData()
                                    .setMessage("Error: Invalid process config.");

                        } else {
                            analyticList.add(new ScheduledQueryAnalytic(
                                    ruleIdentity,
                                    analyticRuleDoc,
                                    process,
                                    (ScheduledQueryAnalyticProcessConfig) analyticRuleDoc.getAnalyticProcessConfig(),
                                    tracker,
                                    analyticProcessorTrackerData,
                                    searchRequest,
                                    viewDoc));
                        }

                    } catch (final RuntimeException e) {
                        LOGGER.debug(e.getMessage(), e);
                        try {
                            tracker.getAnalyticProcessTrackerData().setMessage(e.getMessage());
                            analyticHelper.updateTracker(tracker);
                        } catch (final RuntimeException e2) {
                            LOGGER.error(e2::getMessage, e2);
                        }
                    }
                }
            });
        }
        analyticHelper.info(() -> LogUtil.message("Finished loading rules in {}", logExecutionTime));
        return analyticList;
    }

    private record ScheduledQueryAnalytic(String ruleIdentity,
                                          AnalyticRuleDoc analyticRuleDoc,
                                          AnalyticProcess analyticProcess,
                                          ScheduledQueryAnalyticProcessConfig analyticProcessConfig,
                                          AnalyticProcessTracker tracker,
                                          ScheduledQueryAnalyticProcessTrackerData trackerData,
                                          SearchRequest searchRequest,
                                          ViewDoc viewDoc) {

    }
}
