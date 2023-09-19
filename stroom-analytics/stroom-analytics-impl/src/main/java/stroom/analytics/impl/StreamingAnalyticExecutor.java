package stroom.analytics.impl;

import stroom.analytics.shared.AnalyticProcessConfig;
import stroom.analytics.shared.AnalyticProcessType;
import stroom.analytics.shared.AnalyticRuleDoc;
import stroom.analytics.shared.AnalyticTracker;
import stroom.analytics.shared.StreamingAnalyticProcessConfig;
import stroom.analytics.shared.StreamingAnalyticTrackerData;
import stroom.dashboard.expression.v1.FieldIndex;
import stroom.docref.DocRef;
import stroom.expression.api.ExpressionContext;
import stroom.expression.matcher.ExpressionMatcher;
import stroom.expression.matcher.ExpressionMatcherFactory;
import stroom.meta.shared.Meta;
import stroom.meta.shared.MetaAttributeMapUtil;
import stroom.meta.shared.MetaFields;
import stroom.meta.shared.Status;
import stroom.node.api.NodeInfo;
import stroom.pipeline.PipelineStore;
import stroom.pipeline.factory.PipelineDataCache;
import stroom.pipeline.shared.PipelineDoc;
import stroom.pipeline.shared.data.PipelineData;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.ExpressionUtil;
import stroom.query.api.v2.ParamUtil;
import stroom.query.api.v2.SearchRequest;
import stroom.query.api.v2.TableSettings;
import stroom.query.common.v2.CompiledFields;
import stroom.query.common.v2.ExpressionContextFactory;
import stroom.search.extraction.ExtractionException;
import stroom.search.extraction.ExtractionState;
import stroom.search.extraction.FieldListConsumerHolder;
import stroom.search.impl.SearchExpressionQueryBuilderFactory;
import stroom.security.api.SecurityContext;
import stroom.security.api.UserIdentity;
import stroom.task.api.ExecutorProvider;
import stroom.task.api.TaskContext;
import stroom.task.api.TaskContextFactory;
import stroom.task.api.TaskTerminatedException;
import stroom.task.api.TerminateHandlerFactory;
import stroom.util.concurrent.UncheckedInterruptedException;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogExecutionTime;
import stroom.util.logging.LogUtil;
import stroom.view.shared.ViewDoc;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;

@Singleton
public class StreamingAnalyticExecutor {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(StreamingAnalyticExecutor.class);
    private static final int DEFAULT_MAX_META_LIST_SIZE = 1000;

    private final ExecutorProvider executorProvider;
    private final SecurityContext securityContext;
    private final PipelineStore pipelineStore;
    private final PipelineDataCache pipelineDataCache;
    private final Provider<AnalyticsStreamProcessor> analyticsStreamProcessorProvider;
    private final Provider<FieldListConsumerHolder> fieldListConsumerHolderProvider;
    private final Provider<ExtractionState> extractionStateProvider;
    private final Provider<DetectionConsumerProxy> detectionConsumerProxyProvider;
    private final TaskContextFactory taskContextFactory;
    private final SearchExpressionQueryBuilderFactory searchExpressionQueryBuilderFactory;
    private final ExpressionMatcher metaExpressionMatcher;
    private final NodeInfo nodeInfo;
    private final AnalyticRuleSearchRequestHelper analyticRuleSearchRequestHelper;
    private final NotificationStateService notificationStateService;
    private final DetectionConsumerFactory detectionConsumerFactory;

    private final int maxMetaListSize = DEFAULT_MAX_META_LIST_SIZE;

    private final AnalyticErrorWritingExecutor analyticErrorWritingExecutor;
    private final AnalyticHelper analyticHelper;
    private final ExpressionContextFactory expressionContextFactory;

    @Inject
    public StreamingAnalyticExecutor(final ExecutorProvider executorProvider,
                                     final SecurityContext securityContext,
                                     final PipelineStore pipelineStore,
                                     final PipelineDataCache pipelineDataCache,
                                     final Provider<AnalyticsStreamProcessor> analyticsStreamProcessorProvider,
                                     final Provider<FieldListConsumerHolder> fieldListConsumerHolderProvider,
                                     final Provider<ExtractionState> extractionStateProvider,
                                     final TaskContextFactory taskContextFactory,
                                     final SearchExpressionQueryBuilderFactory searchExpressionQueryBuilderFactory,
                                     final Provider<DetectionConsumerProxy> detectionConsumerProxyProvider,
                                     final AnalyticErrorWritingExecutor analyticErrorWritingExecutor,
                                     final ExpressionMatcherFactory expressionMatcherFactory,
                                     final AnalyticHelper analyticHelper,
                                     final NodeInfo nodeInfo,
                                     final AnalyticRuleSearchRequestHelper analyticRuleSearchRequestHelper,
                                     final NotificationStateService notificationStateService,
                                     final DetectionConsumerFactory detectionConsumerFactory,
                                     final ExpressionContextFactory expressionContextFactory) {
        this.executorProvider = executorProvider;
        this.securityContext = securityContext;
        this.pipelineStore = pipelineStore;
        this.pipelineDataCache = pipelineDataCache;
        this.analyticsStreamProcessorProvider = analyticsStreamProcessorProvider;
        this.fieldListConsumerHolderProvider = fieldListConsumerHolderProvider;
        this.extractionStateProvider = extractionStateProvider;
        this.taskContextFactory = taskContextFactory;
        this.searchExpressionQueryBuilderFactory = searchExpressionQueryBuilderFactory;
        this.detectionConsumerProxyProvider = detectionConsumerProxyProvider;
        this.analyticErrorWritingExecutor = analyticErrorWritingExecutor;
        this.metaExpressionMatcher = expressionMatcherFactory.create(MetaFields.getFieldMap());
        this.analyticHelper = analyticHelper;
        this.nodeInfo = nodeInfo;
        this.analyticRuleSearchRequestHelper = analyticRuleSearchRequestHelper;
        this.notificationStateService = notificationStateService;
        this.detectionConsumerFactory = detectionConsumerFactory;
        this.expressionContextFactory = expressionContextFactory;
    }

    public void exec() {
        final LogExecutionTime logExecutionTime = new LogExecutionTime();
        try {
            analyticHelper.info(() -> "Starting analytic processing");
            processUntilAllComplete();
            analyticHelper.info(() -> LogUtil.message("Finished analytic processing in {}", logExecutionTime));
        } catch (final TaskTerminatedException | UncheckedInterruptedException e) {
            LOGGER.debug("Task terminated", e);
            LOGGER.debug(() -> LogUtil.message("Analytic processing terminated after {}", logExecutionTime));
        } catch (final RuntimeException e) {
            LOGGER.error(() -> LogUtil.message("Error during analytic processing: {}", e.getMessage()), e);
        }
    }

    private void processUntilAllComplete() {
        securityContext.asProcessingUser(() -> {
            boolean allComplete = false;
            // Keep going until we have processed everything we can.
            while (!allComplete) {
                allComplete = processAll();
            }
        });
    }

    private boolean processAll() {
        final AtomicBoolean allComplete = new AtomicBoolean(true);

        // Load rules.
        final List<StreamingAnalytic> streamingAnalytics = loadStreamingAnalytics();

        // Group rules by transformation pipeline.
        final Map<GroupKey, List<StreamingAnalytic>> analyticGroupMap = new HashMap<>();
        for (final StreamingAnalytic streamingAnalytic : streamingAnalytics) {
            try {
                final String ownerUuid = securityContext.getDocumentOwnerUuid(streamingAnalytic.viewDoc().getUuid());
                final GroupKey groupKey = new GroupKey(streamingAnalytic.viewDoc().getPipeline(), ownerUuid);
                analyticGroupMap
                        .computeIfAbsent(groupKey, k -> new ArrayList<>())
                        .add(streamingAnalytic);
            } catch (final RuntimeException e) {
                LOGGER.error(() -> "Error executing rule: " + streamingAnalytic.ruleIdentity(), e);
            }
        }

        // Process each group in parallel.
        analyticHelper.info(() -> "Processing rules");
        final List<CompletableFuture<Void>> completableFutures = new ArrayList<>();
        final TaskContext parentTaskContext = taskContextFactory.current();
        for (final Entry<GroupKey, List<StreamingAnalytic>> entry : analyticGroupMap.entrySet()) {
            final GroupKey groupKey = entry.getKey();
            final DocRef pipelineRef = groupKey.pipeline();
            final String ownerUuid = groupKey.ownerUuid();
            final List<StreamingAnalytic> analytics = entry.getValue();
            if (analytics.size() > 0) {
                final UserIdentity userIdentity = securityContext.createIdentityByUserUuid(ownerUuid);
                securityContext.asUser(userIdentity, () -> {
                    final String pipelineIdentity = pipelineRef.toInfoString();
                    final Runnable runnable = taskContextFactory.context(
                            "Pipeline: " + pipelineIdentity,
                            TerminateHandlerFactory.NOOP_FACTORY,
                            taskContext -> {
                                final boolean complete =
                                        processPipeline(pipelineRef, analytics, taskContext);
                                if (!complete) {
                                    allComplete.set(false);
                                }
                            });
                    completableFutures.add(CompletableFuture.runAsync(runnable, executorProvider.get()));
                });
            }
        }

        // Join.
        CompletableFuture.allOf(completableFutures.toArray(new CompletableFuture[0])).join();

        return allComplete.get() || parentTaskContext.isTerminated();
    }

    private boolean processPipeline(final DocRef pipelineDocRef,
                                    final List<StreamingAnalytic> analytics,
                                    final TaskContext parentTaskContext) {
        // Get a list of meta that will fit some of our analytics.
        final List<Meta> sortedMetaList = getMetaBatch(analytics);

        // Update the poll time for the trackers.
        final Instant startTime = Instant.now();
        analytics.forEach(analytic -> {
            analytic.trackerData().setLastExecutionTimeMs(startTime.toEpochMilli());
            analytic.trackerData().setLastStreamCount(sortedMetaList.size());
        });

        // Now process each stream with the pipeline.
        if (sortedMetaList.size() > 0) {
            final PipelineData pipelineData = getPipelineData(pipelineDocRef);
            for (final Meta meta : sortedMetaList) {
                if (!parentTaskContext.isTerminated()) {
                    try {
                        if (Status.UNLOCKED.equals(meta.getStatus())) {
                            processStream(pipelineDocRef, pipelineData, analytics, meta, parentTaskContext);
                        } else {
                            LOGGER.info("Complete for now");
                            analytics.forEach(loadedAnalytic ->
                                    loadedAnalytic.tracker()
                                            .getAnalyticTrackerData().setMessage("Complete for now"));
                            break;
                        }
                    } catch (final TaskTerminatedException | UncheckedInterruptedException e) {
                        LOGGER.debug(e::getMessage, e);
                        throw e;
                    } catch (final RuntimeException e) {
                        LOGGER.error(e::getMessage, e);
                        analytics.forEach(loadedAnalytic ->
                                loadedAnalytic.tracker().getAnalyticTrackerData().setMessage(e.getMessage()));
                        throw e;
                    }
                }
            }
        }

        // Update all trackers.
        for (final StreamingAnalytic analytic : analytics) {
            analyticHelper.updateTracker(analytic.tracker());
        }

        return sortedMetaList.size() < maxMetaListSize;
    }

    private List<Meta> getMetaBatch(final List<StreamingAnalytic> analytics) {
        // Group by filter.
        final Map<ExpressionOperator, List<StreamingAnalytic>> filterGroupMap = new HashMap<>();
        for (final StreamingAnalytic streamingAnalytic : analytics) {
            ExpressionOperator operator = streamingAnalytic.viewDoc().getFilter();
            if (operator == null) {
                operator = ExpressionOperator.builder().build();
            }
            filterGroupMap
                    .computeIfAbsent(operator, k -> new ArrayList<>())
                    .add(streamingAnalytic);
        }

        // For each group get matching meta.
        final Set<Meta> allMatchingMeta = new HashSet<>();
        for (final Entry<ExpressionOperator, List<StreamingAnalytic>> filterGroupEntry : filterGroupMap.entrySet()) {

            // Get a min meta id and/or min create time.
            Long minMetaId = null;
            Long minCreateTime = null;
            Long maxCreateTime = null;

            for (final StreamingAnalytic streamingAnalytic : filterGroupEntry.getValue()) {
                final StreamingAnalyticTrackerData trackerData = streamingAnalytic.trackerData();

                // Start at the next meta.
                Long lastMetaId = trackerData.getLastStreamId();
                minMetaId = AnalyticUtil.getMin(minMetaId, lastMetaId);
                minCreateTime = AnalyticUtil.getMin(minCreateTime,
                        streamingAnalytic.streamingAnalyticProcessConfig().getMinMetaCreateTimeMs());
                maxCreateTime = AnalyticUtil.getMax(maxCreateTime,
                        streamingAnalytic.streamingAnalyticProcessConfig().getMaxMetaCreateTimeMs());
            }

            final ExpressionOperator findMetaExpression = filterGroupEntry.getKey();

            if (ExpressionUtil.termCount(findMetaExpression) > 0) {
                final List<Meta> metaList = analyticHelper.findMeta(findMetaExpression,
                        minMetaId,
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
                               final List<StreamingAnalytic> analytics,
                               final Meta meta,
                               final TaskContext parentTaskContext) {
        final Map<String, Object> metaAttributeMap = MetaAttributeMapUtil
                .createAttributeMap(meta);

        final List<AnalyticFieldListConsumer> fieldListConsumers = new ArrayList<>();

        // Filter the rules that should be applied to this meta.
        final List<StreamingAnalytic> filteredAnalytics = analytics
                .stream()
                .filter(analytic -> !ignoreStream(analytic, meta, metaAttributeMap))
                .toList();

        for (final StreamingAnalytic analytic : filteredAnalytics) {
            Optional<AnalyticFieldListConsumer> consumer = createEventConsumer(analytic);
            consumer.ifPresent(fieldListConsumers::add);
        }

        final AnalyticFieldListConsumer fieldListConsumer;
        if (fieldListConsumers.size() > 1) {
            fieldListConsumer = new MultiAnalyticFieldListConsumer(fieldListConsumers);
        } else if (fieldListConsumers.size() == 1) {
            fieldListConsumer = fieldListConsumers.get(0);
        } else {
            fieldListConsumer = null;
        }

        if (fieldListConsumer != null) {
            analyticErrorWritingExecutor.wrap(
                    "Analytics Stream Processor",
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
                            final ExtractionState extractionState = extractionStateProvider.get();
                            filteredAnalytics.forEach(analytic -> {
                                analytic.trackerData().setLastStreamId(meta.getId());
                                analytic.trackerData().incrementStreamCount();
                                analytic.trackerData().addEventCount(extractionState.getCount());
                            });
                        }
                    }).run();
        }
    }


    private Optional<AnalyticFieldListConsumer> createEventConsumer(final StreamingAnalytic analytic) {
        // Create field index.
        final SearchRequest searchRequest = analytic.searchRequest();
        final ExpressionContext expressionContext = expressionContextFactory
                .createContext(searchRequest);
        final TableSettings tableSettings = searchRequest.getResultRequests().get(0).getMappings().get(0);
        final Map<String, String> paramMap = ParamUtil.createParamMap(searchRequest.getQuery().getParams());
        final CompiledFields compiledFields = CompiledFields.create(expressionContext,
                tableSettings.getFields(),
                paramMap);
        final FieldIndex fieldIndex = compiledFields.getFieldIndex();

        // Cache the query for use across multiple streams.
        final SearchExpressionQueryCache searchExpressionQueryCache =
                new SearchExpressionQueryCache(searchExpressionQueryBuilderFactory, searchRequest);

        // Determine if notifications have been disabled.
        final NotificationState notificationState = notificationStateService.getState(analytic.analyticRuleDoc);
        // Only execute if the state is enabled.
        notificationState.enableIfPossible();
        if (notificationState.isEnabled()) {
            try {
                final Provider<DetectionConsumer> detectionConsumerProvider =
                        detectionConsumerFactory.create(analytic.analyticRuleDoc);
                final DetectionConsumerProxy detectionConsumerProxy = detectionConsumerProxyProvider.get();
                detectionConsumerProxy.setAnalyticRuleDoc(analytic.analyticRuleDoc());
                detectionConsumerProxy.setCompiledFields(compiledFields);
                detectionConsumerProxy.setFieldIndex(fieldIndex);
                detectionConsumerProxy.setDetectionsConsumerProvider(detectionConsumerProvider);

                final AnalyticFieldListConsumer analyticFieldListConsumer =
                        new StreamingAnalyticFieldListConsumer(
                                searchRequest,
                                fieldIndex,
                                notificationState,
                                detectionConsumerProxy,
                                searchExpressionQueryCache,
                                null,
                                detectionConsumerProxy);
                return Optional.of(analyticFieldListConsumer);

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

        return Optional.empty();
    }

    private boolean ignoreStream(final StreamingAnalytic analytic,
                                 final Meta meta,
                                 final Map<String, Object> metaAttributeMap) {
        final Long lastStreamId = analytic.trackerData().getLastStreamId();
        final long minStreamId = AnalyticUtil.getMin(null, lastStreamId) + 1;

        final long minCreateTime =
                AnalyticUtil.getMin(null,
                        analytic.streamingAnalyticProcessConfig().getMinMetaCreateTimeMs());
        final long maxCreateTime =
                AnalyticUtil.getMax(null,
                        analytic.streamingAnalyticProcessConfig().getMaxMetaCreateTimeMs());

        // Check this analytic should process this meta.
        return meta.getId() < minStreamId ||
                meta.getCreateMs() < minCreateTime ||
                meta.getCreateMs() > maxCreateTime ||
                !metaExpressionMatcher.match(metaAttributeMap, analytic.viewDoc().getFilter());
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

    private List<StreamingAnalytic> loadStreamingAnalytics() {
        final LogExecutionTime logExecutionTime = new LogExecutionTime();
        analyticHelper.info(() -> "Loading rules");
        final List<StreamingAnalytic> analyticList = new ArrayList<>();
        final List<AnalyticRuleDoc> rules = analyticHelper.getRules();
        for (final AnalyticRuleDoc analyticRuleDoc : rules) {
            final AnalyticProcessConfig analyticProcessConfig = analyticRuleDoc.getAnalyticProcessConfig();
            if (analyticProcessConfig != null &&
                    analyticProcessConfig.isEnabled() &&
                    nodeInfo.getThisNodeName().equals(analyticProcessConfig.getNode()) &&
                    AnalyticProcessType.STREAMING.equals(analyticRuleDoc.getAnalyticProcessType())) {
                final AnalyticTracker tracker = analyticHelper.getTracker(analyticRuleDoc);

                StreamingAnalyticTrackerData analyticProcessorTrackerData;
                if (tracker.getAnalyticTrackerData() instanceof
                        StreamingAnalyticTrackerData) {
                    analyticProcessorTrackerData = (StreamingAnalyticTrackerData)
                            tracker.getAnalyticTrackerData();
                } else {
                    analyticProcessorTrackerData = new StreamingAnalyticTrackerData();
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
                            instanceof StreamingAnalyticProcessConfig)) {
                        LOGGER.debug("Error: Invalid process config {}", ruleIdentity);
                        tracker.getAnalyticTrackerData()
                                .setMessage("Error: Invalid process config.");

                    } else {
                        analyticList.add(new StreamingAnalytic(
                                ruleIdentity,
                                analyticRuleDoc,
                                (StreamingAnalyticProcessConfig) analyticRuleDoc.getAnalyticProcessConfig(),
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
        analyticHelper.info(() -> LogUtil.message("Finished loading rules in {}", logExecutionTime));
        return analyticList;
    }

    private record StreamingAnalytic(String ruleIdentity,
                                     AnalyticRuleDoc analyticRuleDoc,
                                     StreamingAnalyticProcessConfig streamingAnalyticProcessConfig,
                                     AnalyticTracker tracker,
                                     StreamingAnalyticTrackerData trackerData,
                                     SearchRequest searchRequest,
                                     ViewDoc viewDoc) {

    }

    private record GroupKey(DocRef pipeline, String ownerUuid) {

    }
}
