package stroom.analytics.impl;

import stroom.analytics.shared.AnalyticNotification;
import stroom.analytics.shared.AnalyticNotificationState;
import stroom.analytics.shared.AnalyticNotificationStreamConfig;
import stroom.analytics.shared.AnalyticProcessorFilterTracker;
import stroom.analytics.shared.AnalyticRuleType;
import stroom.analytics.shared.ScheduledQueryAnalyticConfig;
import stroom.dashboard.expression.v1.FieldIndex;
import stroom.dashboard.expression.v1.Val;
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
import stroom.util.shared.time.SimpleDuration;
import stroom.util.shared.time.TimeUnit;
import stroom.util.time.SimpleDurationUtil;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.inject.Inject;
import javax.inject.Provider;

public class ScheduledQueryAnalyticExecutor {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ScheduledQueryAnalyticExecutor.class);

    private final AnalyticLoader analyticLoader;
    private final AnalyticNotificationDao analyticNotificationDao;
    private final AnalyticProcessorFilterTrackerDao analyticProcessorFilterTrackerDao;
    private final AnalyticNotificationService analyticNotificationService;
    private final DataSourceResolver dataSourceResolver;
    private final ExecutorProvider executorProvider;
    private final ResultStoreManager searchResponseCreatorManager;
    private final Provider<DetectionWriterProxy> detectionWriterProxyProvider;
    private final AnalyticErrorWritingExecutor analyticErrorWritingExecutor;
    private final TaskContextFactory taskContextFactory;

    @Inject
    ScheduledQueryAnalyticExecutor(final AnalyticLoader analyticLoader,
                                   final AnalyticNotificationDao analyticNotificationDao,
                                   final AnalyticProcessorFilterTrackerDao analyticProcessorFilterTrackerDao,
                                   final AnalyticNotificationService analyticNotificationService,
                                   final DataSourceResolver dataSourceResolver,
                                   final ExecutorProvider executorProvider,
                                   final ResultStoreManager searchResponseCreatorManager,
                                   final Provider<DetectionWriterProxy> detectionWriterProxyProvider,
                                   final AnalyticErrorWritingExecutor analyticErrorWritingExecutor,
                                   final TaskContextFactory taskContextFactory) {
        this.analyticLoader = analyticLoader;
        this.analyticNotificationDao = analyticNotificationDao;
        this.analyticProcessorFilterTrackerDao = analyticProcessorFilterTrackerDao;
        this.analyticNotificationService = analyticNotificationService;
        this.dataSourceResolver = dataSourceResolver;
        this.executorProvider = executorProvider;
        this.searchResponseCreatorManager = searchResponseCreatorManager;
        this.detectionWriterProxyProvider = detectionWriterProxyProvider;
        this.analyticErrorWritingExecutor = analyticErrorWritingExecutor;
        this.taskContextFactory = taskContextFactory;
    }

    public void exec() {
        // Load rules.
        final List<LoadedAnalytic> loadedAnalytics = analyticLoader
                .loadAnalyticRules(Set.of(AnalyticRuleType.SCHEDULED_QUERY));

        info(() -> "Processing batch rules");
        final List<CompletableFuture<Void>> completableFutures = new ArrayList<>();
        processScheduledQueryAnalytics(loadedAnalytics, completableFutures, taskContextFactory.current());

        // Join.
        CompletableFuture.allOf(completableFutures.toArray(new CompletableFuture[0])).join();
    }

    private void processScheduledQueryAnalytics(final List<LoadedAnalytic> loadedAnalytics,
                                                final List<CompletableFuture<Void>> completableFutures,
                                                final TaskContext parentTaskContext) {
        for (final LoadedAnalytic loadedAnalytic : loadedAnalytics) {
            final List<AnalyticNotification> notifications =
                    analyticNotificationDao.getByAnalyticUuid(loadedAnalytic.analyticRuleDoc().getUuid());
            for (final AnalyticNotification notification : notifications) {
                if (notification.isEnabled()) {
                    final AnalyticNotificationState notificationState = analyticNotificationService
                            .getNotificationState(notification);
                    final AnalyticNotificationStreamConfig streamConfig =
                            (AnalyticNotificationStreamConfig) notification.getConfig();

                    SimpleDuration timeToWaitForData = null;
                    SimpleDuration queryFrequency = null;
                    if (loadedAnalytic.analyticRuleDoc().getAnalyticConfig() instanceof
                            final ScheduledQueryAnalyticConfig scheduledQueryAnalyticConfig) {
                        timeToWaitForData = scheduledQueryAnalyticConfig.getTimeToWaitForData();
                        queryFrequency = scheduledQueryAnalyticConfig.getQueryFrequency();
                    }
                    if (timeToWaitForData == null) {
                        timeToWaitForData = SimpleDuration.builder().time(1).timeUnit(TimeUnit.HOURS).build();
                    }
                    if (queryFrequency == null) {
                        queryFrequency = SimpleDuration.builder().time(1).timeUnit(TimeUnit.HOURS).build();
                    }

                    // See if it is time to execute this query.
                    final Instant now = Instant.now();
                    final Instant nextExecution = SimpleDurationUtil.minus(now, queryFrequency);
                    final Long lastPollMs = loadedAnalytic.trackerBuilder().build().getLastPollMs();
                    final long nowMs = now.toEpochMilli();
                    if (lastPollMs == null || lastPollMs < nextExecution.toEpochMilli()) {

                        // We are executing the query.
                        final Long lastTimeFilterTo = notificationState.getLastTimeFilterTo();
                        final Optional<TimeFilter> optionalTimeFilter =
                                loadedAnalytic.createTimeFilter(
                                        timeToWaitForData,
                                        lastTimeFilterTo,
                                        true,
                                        now,
                                        Optional.empty());
                        if (optionalTimeFilter.isPresent()) {
                            // Update the last poll time and end event time range.
                            loadedAnalytic.trackerBuilder().lastPollMs(nowMs);
                            loadedAnalytic.trackerBuilder().lastEventTime(optionalTimeFilter.get().getTo());

                            final Runnable runnable = analyticErrorWritingExecutor.wrap(
                                    "Scheduled Query Analytic: " + loadedAnalytic.ruleIdentity(),
                                    streamConfig.getDestinationFeed().getName(),
                                    null,
                                    parentTaskContext,
                                    taskContext -> processScheduledQueryAnalytic(
                                            loadedAnalytic,
                                            streamConfig,
                                            notificationState,
                                            optionalTimeFilter.get()));

                            try {
                                completableFutures.add(CompletableFuture.runAsync(runnable, executorProvider.get()));
                            } catch (final TaskTerminatedException | UncheckedInterruptedException e) {
                                LOGGER.debug(e::getMessage, e);
                                throw e;
                            } catch (final RuntimeException e) {
                                LOGGER.error(e::getMessage, e);
                                analyticNotificationService.disableNotification(
                                        loadedAnalytic.ruleIdentity(), notification, notificationState, e.getMessage());
                            }
                        }
                    }
                }
            }
        }
    }

    private void processScheduledQueryAnalytic(final LoadedAnalytic loadedAnalytic,
                                               final AnalyticNotificationStreamConfig streamConfig,
                                               final AnalyticNotificationState notificationState,
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

            final String query = loadedAnalytic.analyticRuleDoc().getQuery();
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
                    detectionWriter.setAnalyticRuleDoc(loadedAnalytic.analyticRuleDoc());
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
            final AnalyticNotificationState newState = notificationState.copy()
                    .lastTimeFilterTo(timeFilter.getTo())
                    .build();
            analyticNotificationService.updateNotificationState(newState);
            updateFilterTracker(loadedAnalytic.trackerBuilder().build());

        } catch (final Exception e) {
            LOGGER.error(e::getMessage, e);
        }
    }

    private void info(final Supplier<String> messageSupplier) {
        LOGGER.info(messageSupplier);
        taskContextFactory.current().info(messageSupplier);
    }

    private AnalyticProcessorFilterTracker updateFilterTracker(AnalyticProcessorFilterTracker tracker) {
        analyticProcessorFilterTrackerDao.update(tracker);
        tracker = analyticProcessorFilterTrackerDao.get(tracker.getFilterUuid())
                .orElseThrow(() -> new RuntimeException("Unable to load tracker"));
//        ruleStateCache.put(analyticProcessorFilter.analyticUuid(), analyticProcessorFilter);
        return tracker;
    }
}
