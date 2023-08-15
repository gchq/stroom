package stroom.analytics.impl;

import stroom.analytics.impl.AnalyticDataStores.AnalyticDataStore;
import stroom.analytics.impl.DetectionConsumer.Detection;
import stroom.analytics.impl.DetectionConsumer.LinkedEvent;
import stroom.analytics.impl.DetectionConsumer.Value;
import stroom.analytics.rule.impl.AnalyticRuleStore;
import stroom.analytics.shared.AnalyticNotification;
import stroom.analytics.shared.AnalyticNotificationConfig;
import stroom.analytics.shared.AnalyticNotificationState;
import stroom.analytics.shared.AnalyticNotificationStreamConfig;
import stroom.analytics.shared.AnalyticProcessorFilter;
import stroom.analytics.shared.AnalyticProcessorFilterTracker;
import stroom.analytics.shared.AnalyticRuleDoc;
import stroom.analytics.shared.AnalyticRuleType;
import stroom.dashboard.expression.v1.FieldIndex;
import stroom.docref.DocRef;
import stroom.expression.matcher.ExpressionMatcher;
import stroom.expression.matcher.ExpressionMatcherFactory;
import stroom.index.shared.IndexConstants;
import stroom.meta.api.MetaService;
import stroom.meta.shared.FindMetaCriteria;
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
import stroom.query.api.v2.ExpressionOperator.Op;
import stroom.query.api.v2.ExpressionTerm.Condition;
import stroom.query.api.v2.ExpressionUtil;
import stroom.query.api.v2.Field;
import stroom.query.api.v2.OffsetRange;
import stroom.query.api.v2.ParamUtil;
import stroom.query.api.v2.QueryKey;
import stroom.query.api.v2.ResultRequest;
import stroom.query.api.v2.Row;
import stroom.query.api.v2.SearchRequest;
import stroom.query.api.v2.TableResult;
import stroom.query.api.v2.TableResultBuilder;
import stroom.query.api.v2.TableSettings;
import stroom.query.api.v2.TimeFilter;
import stroom.query.common.v2.CompiledFields;
import stroom.query.common.v2.CurrentDbState;
import stroom.query.common.v2.DeleteCommand;
import stroom.query.common.v2.Key;
import stroom.query.common.v2.LmdbDataStore;
import stroom.query.common.v2.TableResultCreator;
import stroom.query.common.v2.format.FieldFormatter;
import stroom.query.common.v2.format.FormatterFactory;
import stroom.search.extraction.ExtractionException;
import stroom.search.extraction.ExtractionState;
import stroom.search.extraction.FieldListConsumerHolder;
import stroom.search.impl.SearchExpressionQueryBuilderFactory;
import stroom.security.api.SecurityContext;
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
import stroom.util.time.SimpleDurationUtil;
import stroom.view.impl.ViewStore;
import stroom.view.shared.ViewDoc;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;

@Singleton
public class AnalyticsExecutor {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(AnalyticsExecutor.class);
    private static final LocalDateTime BEGINNING_OF_TIME =
            LocalDateTime.ofInstant(Instant.ofEpochMilli(0), ZoneOffset.UTC);
    private static final int DEFAULT_MAX_META_LIST_SIZE = 1000;

    private final AnalyticRuleSearchRequestHelper analyticRuleSearchRequestHelper;
    private final ExecutorProvider executorProvider;

    private final ViewStore viewStore;
    private final MetaService metaService;
    private final AnalyticRuleStore analyticRuleStore;
    private final SecurityContext securityContext;
    private final Provider<DetectionsWriter> detectionsWriterProvider;
    private final PipelineStore pipelineStore;
    private final PipelineDataCache pipelineDataCache;
    private final Provider<AnalyticsStreamProcessor> analyticsStreamProcessorProvider;
    private final Provider<FieldListConsumerHolder> fieldListConsumerHolderProvider;
    private final Provider<ExtractionState> extractionStateProvider;
    private final AnalyticDataStores analyticDataStores;
    private final Provider<DetectionWriterProxy> detectionWriterProxyProvider;
    private final TaskContextFactory taskContextFactory;
    private final SearchExpressionQueryBuilderFactory searchExpressionQueryBuilderFactory;
    private final AnalyticProcessorFilterDao analyticProcessorFilterDao;
    private final AnalyticProcessorFilterTrackerDao analyticProcessorFilterTrackerDao;
    private final AnalyticNotificationDao analyticNotificationDao;
    private final AnalyticNotificationService analyticNotificationService;
    private final NodeInfo nodeInfo;
    private final ExpressionMatcher metaExpressionMatcher;
    private final BatchAnalyticExecutor batchAnalyticExecutor;

    private final int maxMetaListSize = DEFAULT_MAX_META_LIST_SIZE;
    private boolean allowUpToDateProcessing = true;


    private final AnalyticErrorWritingExecutor analyticErrorWritingExecutor;

    @Inject
    public AnalyticsExecutor(final AnalyticRuleSearchRequestHelper analyticRuleSearchRequestHelper,
                             final ExecutorProvider executorProvider,
                             final AnalyticRuleStore analyticRuleStore,
                             final SecurityContext securityContext,
                             final Provider<DetectionsWriter> detectionsWriterProvider,
                             final ViewStore viewStore,
                             final MetaService metaService,
                             final PipelineStore pipelineStore,
                             final PipelineDataCache pipelineDataCache,
                             final Provider<AnalyticsStreamProcessor> analyticsStreamProcessorProvider,
                             final Provider<FieldListConsumerHolder> fieldListConsumerHolderProvider,
                             final Provider<ExtractionState> extractionStateProvider,
                             final TaskContextFactory taskContextFactory,
                             final SearchExpressionQueryBuilderFactory searchExpressionQueryBuilderFactory,
                             final AnalyticDataStores analyticDataStores,
                             final Provider<DetectionWriterProxy> detectionWriterProxyProvider,
                             final AnalyticProcessorFilterDao analyticProcessorFilterDao,
                             final AnalyticProcessorFilterTrackerDao analyticProcessorFilterTrackerDao,
                             final AnalyticNotificationDao analyticNotificationDao,
                             final AnalyticNotificationService analyticNotificationService,
                             final AnalyticErrorWritingExecutor analyticErrorWritingExecutor,
                             final NodeInfo nodeInfo,
                             final ExpressionMatcherFactory expressionMatcherFactory,
                             final BatchAnalyticExecutor batchAnalyticExecutor) {
        this.analyticRuleSearchRequestHelper = analyticRuleSearchRequestHelper;
        this.executorProvider = executorProvider;
        this.detectionsWriterProvider = detectionsWriterProvider;
        this.analyticRuleStore = analyticRuleStore;
        this.securityContext = securityContext;
        this.viewStore = viewStore;
        this.metaService = metaService;
        this.pipelineStore = pipelineStore;
        this.pipelineDataCache = pipelineDataCache;
        this.analyticsStreamProcessorProvider = analyticsStreamProcessorProvider;
        this.fieldListConsumerHolderProvider = fieldListConsumerHolderProvider;
        this.extractionStateProvider = extractionStateProvider;
        this.taskContextFactory = taskContextFactory;
        this.searchExpressionQueryBuilderFactory = searchExpressionQueryBuilderFactory;
        this.analyticDataStores = analyticDataStores;
        this.detectionWriterProxyProvider = detectionWriterProxyProvider;
        this.analyticProcessorFilterDao = analyticProcessorFilterDao;
        this.analyticProcessorFilterTrackerDao = analyticProcessorFilterTrackerDao;
        this.analyticNotificationDao = analyticNotificationDao;
        this.analyticNotificationService = analyticNotificationService;
        this.analyticErrorWritingExecutor = analyticErrorWritingExecutor;
        this.nodeInfo = nodeInfo;
        this.metaExpressionMatcher = expressionMatcherFactory.create(MetaFields.getFieldMap());
        this.batchAnalyticExecutor = batchAnalyticExecutor;
    }

    private void info(final Supplier<String> messageSupplier) {
        LOGGER.info(messageSupplier);
        taskContextFactory.current().info(messageSupplier);
    }

    public void exec() {
        final LogExecutionTime logExecutionTime = new LogExecutionTime();
        try {
            info(() -> "Starting analytic processing");
            processUntilAllComplete();
            info(() -> LogUtil.message("Finished analytic processing in {}", logExecutionTime));
        } catch (final TaskTerminatedException | UncheckedInterruptedException e) {
            LOGGER.debug("Task terminated", e);
            LOGGER.debug(() -> LogUtil.message("Analytic processing terminated after {}", logExecutionTime));
        } catch (final RuntimeException e) {
            LOGGER.error(() -> LogUtil.message("Error during analytic processing: {}", e.getMessage()), e);
        }
    }

    private void processUntilAllComplete() {
        securityContext.asProcessingUser(() -> {
            // Delete old data stores.
            deleteOldStores();

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
        final List<LoadedAnalytic> loadedAnalyticList = loadAnalyticRules();

        // Group rules by transformation pipeline.
        final Map<DocRef, List<LoadedAnalytic>> pipelineGroupMap = new HashMap<>();
        final List<LoadedAnalytic> batchAnalytics = new ArrayList<>();
        for (final LoadedAnalytic loadedAnalytic : loadedAnalyticList) {
            if (loadedAnalytic.viewDoc() != null && loadedAnalytic.viewDoc().getPipeline() != null) {
                if (AnalyticRuleType.INDEX_QUERY.equals(loadedAnalytic.analyticRuleDoc().getAnalyticRuleType())) {
                    batchAnalytics.add(loadedAnalytic);
                } else {
                    pipelineGroupMap
                            .computeIfAbsent(loadedAnalytic.viewDoc().getPipeline(), k -> new ArrayList<>())
                            .add(loadedAnalytic);
                }
            } else {
                // If there isn't a view then update tracker and forget this rule.
                analyticProcessorFilterTrackerDao.update(loadedAnalytic.trackerBuilder().build());
            }
        }

        // Process each group in parallel.
        info(() -> "Processing rules");
        final List<CompletableFuture<Void>> completableFutures = new ArrayList<>();
        final TaskContext parentTaskContext = taskContextFactory.current();
        for (final Entry<DocRef, List<LoadedAnalytic>> entry : pipelineGroupMap.entrySet()) {
            final DocRef pipelineRef = entry.getKey();
            final List<LoadedAnalytic> analytics = entry.getValue();
            if (analytics.size() > 0) {
                final String pipelineIdentity = pipelineRef.toInfoString();
                final Runnable runnable = taskContextFactory.childContext(parentTaskContext,
                        "Pipeline: " + pipelineIdentity,
                        taskContext -> {
                            final boolean complete =
                                    processPipeline(pipelineRef, analytics, taskContext);
                            if (!complete) {
                                allComplete.set(false);
                            }
                        });
                completableFutures.add(CompletableFuture.runAsync(runnable, executorProvider.get()));
            }
        }

        // Process batch analytics.
        batchAnalyticExecutor.processBatchAnalytics(batchAnalytics, completableFutures, parentTaskContext);

        // Join.
        CompletableFuture.allOf(completableFutures.toArray(new CompletableFuture[0])).join();

        return allComplete.get();
    }

    private boolean processPipeline(final DocRef pipelineDocRef,
                                    final List<LoadedAnalytic> analytics,
                                    final TaskContext parentTaskContext) {
        // Get a list of meta that will fit some of our analytics.
        final List<Meta> sortedMetaList = getMetaBatch(analytics);

        // Update the poll time for the trackers.
        final LocalDateTime startTime = LocalDateTime.now();
        analytics.forEach(loadedAnalytic -> {
            loadedAnalytic.trackerBuilder().lastPollMs(startTime.toInstant(ZoneOffset.UTC).toEpochMilli());
            loadedAnalytic.trackerBuilder().lastPollTaskCount(sortedMetaList.size());
        });

        // Now process each stream with the pipeline.
        if (sortedMetaList.size() > 0) {
            final PipelineData pipelineData = getPipelineData(pipelineDocRef);
            for (final Meta meta : sortedMetaList) {
                try {
                    if (Status.UNLOCKED.equals(meta.getStatus())) {
                        processStream(pipelineDocRef, pipelineData, analytics, meta, parentTaskContext);
                    } else {
                        LOGGER.info("Complete for now");
                        analytics.forEach(loadedAnalytic ->
                                loadedAnalytic.trackerBuilder().message("Complete for now"));
                        break;
                    }
                } catch (final TaskTerminatedException | UncheckedInterruptedException e) {
                    LOGGER.debug(e::getMessage, e);
                    throw e;
                } catch (final RuntimeException e) {
                    LOGGER.error(e::getMessage, e);
                    analytics.forEach(loadedAnalytic ->
                            loadedAnalytic.trackerBuilder().message(e.getMessage()));
                    throw e;
                }
            }
        }

        // Execute notifications on LMDB stores and sync.
        executePostProcessNotifications(analytics, parentTaskContext, startTime, sortedMetaList);

        // Update all trackers.
        for (final LoadedAnalytic analytic : analytics) {
            updateFilterTracker(analytic.trackerBuilder().build());
        }

        return sortedMetaList.size() < maxMetaListSize;
    }

    private List<Meta> getMetaBatch(final List<LoadedAnalytic> analytics) {
        // Group by filter.
        final Map<ExpressionOperator, List<LoadedAnalytic>> filterGroupMap = new HashMap<>();
        for (final LoadedAnalytic loadedAnalytic : analytics) {
            ExpressionOperator operator = loadedAnalytic.viewDoc().getFilter();
            if (operator == null) {
                operator = ExpressionOperator.builder().build();
            }
            filterGroupMap
                    .computeIfAbsent(operator, k -> new ArrayList<>())
                    .add(loadedAnalytic);
        }

        // For each group get matching meta.
        final Set<Meta> allMatchingMeta = new HashSet<>();
        for (final Entry<ExpressionOperator, List<LoadedAnalytic>> filterGroupEntry : filterGroupMap.entrySet()) {

            // Get a min meta id and/or min create time.
            Long minMetaId = null;
            Long minCreateTime = null;
            Long maxCreateTime = null;

            for (final LoadedAnalytic loadedAnalytic : filterGroupEntry.getValue()) {
                final AnalyticProcessorFilterTracker tracker = loadedAnalytic.trackerBuilder().build();
                // Start at the next meta.
                Long lastMetaId = tracker.getLastMetaId();
                final Long lastEventId = tracker.getLastEventId();
                if (lastMetaId != null && lastEventId == null) {
                    lastMetaId++;
                }

                minMetaId = getMin(minMetaId, lastMetaId);
                minCreateTime = getMin(minCreateTime,
                        loadedAnalytic.analyticProcessorFilter().getMinMetaCreateTimeMs());
                maxCreateTime = getMax(maxCreateTime,
                        loadedAnalytic.analyticProcessorFilter().getMaxMetaCreateTimeMs());
            }

            final ExpressionOperator findMetaExpression = filterGroupEntry.getKey();

            if (ExpressionUtil.termCount(findMetaExpression) > 0) {
                final List<Meta> metaList = findMeta(findMetaExpression,
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
                               final List<LoadedAnalytic> analytics,
                               final Meta meta,
                               final TaskContext parentTaskContext) {
        final Map<String, Object> metaAttributeMap = MetaAttributeMapUtil
                .createAttributeMap(meta);

        final List<AnalyticFieldListConsumer> fieldListConsumers = new ArrayList<>();

        // Determine which rules will take part in this process.
        for (final LoadedAnalytic analytic : analytics) {
            if (analytic.analyticRuleDoc().getAnalyticRuleType() == null ||
                    AnalyticRuleType.TABLE_CREATION.equals(analytic.analyticRuleDoc().getAnalyticRuleType())) {
                fieldListConsumers.addAll(createLmdbConsumer(analytic, meta, metaAttributeMap));
            } else if (AnalyticRuleType.STREAMING.equals(analytic.analyticRuleDoc().getAnalyticRuleType())) {
                fieldListConsumers.addAll(createEventConsumer(analytic, meta, metaAttributeMap));
            }
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

                        final ExtractionState extractionState = extractionStateProvider.get();

                        analytics.forEach(analytic -> {
                            analytic.trackerBuilder().incrementMetaCount();
                            analytic.trackerBuilder().addEventCount(extractionState.getCount());
                        });
                    }).run();
        }
    }

    private List<AnalyticFieldListConsumer> createLmdbConsumer(final LoadedAnalytic analytic,
                                                               final Meta meta,
                                                               final Map<String, Object> metaAttributeMap) {
        // Check this analytic should process this stream.
        if (ignoreStream(analytic, meta, metaAttributeMap)) {
            return Collections.emptyList();
        }

        final AnalyticProcessorFilterTracker tracker = analytic.trackerBuilder().build();

        // Create receiver to insert into the pipeline.
        // After a shutdown we may wish to resume event processing from a specific event id.
        Long minEventId = null;
        if (tracker.getLastEventId() != null && meta.getId() == tracker.getLastMetaId()) {
            minEventId = tracker.getLastEventId() + 1;
        }

        final AnalyticDataStore dataStore = analyticDataStores.get(analytic.analyticRuleDoc());
        final SearchRequest searchRequest = dataStore.searchRequest();

        // Get or create LMDB data store.
        final LmdbDataStore lmdbDataStore = dataStore.lmdbDataStore();

        // Cache the query for use across multiple streams.
        final SearchExpressionQueryCache searchExpressionQueryCache =
                new SearchExpressionQueryCache(searchExpressionQueryBuilderFactory,
                        searchRequest);
        // Get the field index.
        final FieldIndex fieldIndex = lmdbDataStore.getFieldIndex();
        final AnalyticFieldListConsumer analyticFieldListConsumer =
                new LmdbAnalyticFieldListConsumer(
                        searchRequest,
                        fieldIndex,
                        lmdbDataStore,
                        searchExpressionQueryCache,
                        minEventId);
        return Collections.singletonList(analyticFieldListConsumer);
    }

    private List<AnalyticFieldListConsumer> createEventConsumer(final LoadedAnalytic analytic,
                                                                final Meta meta,
                                                                final Map<String, Object> metaAttributeMap) {
        // Check this analytic should process this stream.
        if (ignoreStream(analytic, meta, metaAttributeMap)) {
            return Collections.emptyList();
        }

        final List<AnalyticNotification> notifications =
                analyticNotificationDao.getByAnalyticUuid(analytic.analyticRuleDoc().getUuid());

        // Create field index.
        final SearchRequest searchRequest = analytic.searchRequest();
        final TableSettings tableSettings = searchRequest.getResultRequests().get(0).getMappings().get(0);
        final Map<String, String> paramMap = ParamUtil.createParamMap(searchRequest.getQuery().getParams());
        final CompiledFields compiledFields = CompiledFields.create(tableSettings.getFields(), paramMap);
        final FieldIndex fieldIndex = compiledFields.getFieldIndex();

        // Cache the query for use across multiple streams.
        final SearchExpressionQueryCache searchExpressionQueryCache =
                new SearchExpressionQueryCache(searchExpressionQueryBuilderFactory, searchRequest);

        final List<AnalyticFieldListConsumer> analyticFieldListConsumers = new ArrayList<>(notifications.size());
        for (final AnalyticNotification notification : notifications) {
            final AnalyticNotificationState analyticNotificationState =
                    analyticNotificationService.getNotificationState(notification);
            final AnalyticNotificationConfig config = notification.getConfig();
            if (notification.isEnabled() &&
                    config instanceof final AnalyticNotificationStreamConfig streamConfig) {
                try {
                    final DetectionWriterProxy detectionWriter = detectionWriterProxyProvider.get();
                    detectionWriter.setAnalyticRuleDoc(analytic.analyticRuleDoc());
                    detectionWriter.setCompiledFields(compiledFields);
                    detectionWriter.setFieldIndex(fieldIndex);
                    if (!streamConfig.isUseSourceFeedIfPossible()) {
                        detectionWriter.setDestinationFeed(streamConfig.getDestinationFeed());
                    }

                    final AnalyticFieldListConsumer analyticFieldListConsumer =
                            new EventAnalyticFieldListConsumer(
                                    searchRequest,
                                    fieldIndex,
                                    detectionWriter,
                                    searchExpressionQueryCache,
                                    null,
                                    detectionWriter);
                    analyticFieldListConsumers.add(analyticFieldListConsumer);

                } catch (final TaskTerminatedException | UncheckedInterruptedException e) {
                    LOGGER.debug(e::getMessage, e);
                    throw e;
                } catch (final RuntimeException e) {
                    LOGGER.error(e::getMessage, e);
                    analyticNotificationService.disableNotification(analytic.ruleIdentity(),
                            notification,
                            analyticNotificationState,
                            e.getMessage());
                }
            }
        }

        return analyticFieldListConsumers;
    }

    private boolean ignoreStream(final LoadedAnalytic analytic,
                                 final Meta meta,
                                 final Map<String, Object> metaAttributeMap) {
        final AnalyticProcessorFilterTracker tracker = analytic.trackerBuilder().build();
        final Long lastMetaId = tracker.getLastMetaId();
        final Long lastEventId = tracker.getLastEventId();

        long minMetaId;
        if (lastEventId == null) {
            // Start at the next meta.
            minMetaId = getMin(null, lastMetaId) + 1;
        } else {
            minMetaId = getMin(null, lastMetaId);
        }

        final long minCreateTime =
                getMin(null, analytic.analyticProcessorFilter().getMinMetaCreateTimeMs());
        final long maxCreateTime =
                getMax(null, analytic.analyticProcessorFilter().getMaxMetaCreateTimeMs());

        // Check this analytic should process this meta.
        return meta.getId() < minMetaId ||
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

    private List<LoadedAnalytic> loadAnalyticRules() {
        final LogExecutionTime logExecutionTime = new LogExecutionTime();
        info(() -> "Loading rules");
        final List<LoadedAnalytic> loadedAnalyticList = new ArrayList<>();
        final List<DocRef> docRefList = analyticRuleStore.list();
        for (final DocRef docRef : docRefList) {
            final AnalyticRuleDoc analyticRuleDoc = analyticRuleStore.readDocument(docRef);
            if (analyticRuleDoc != null) {
                final Optional<AnalyticProcessorFilter> optionalFilter =
                        analyticProcessorFilterDao.getByAnalyticUuid(analyticRuleDoc.getUuid());
                optionalFilter.ifPresent(filter -> {
                    if (filter.isEnabled() && nodeInfo.getThisNodeName().equals(filter.getNode())) {
                        final AnalyticProcessorFilterTracker tracker = getFilterTracker(filter);
                        final AnalyticProcessorFilterTracker.Builder trackerBuilder = tracker.copy();
                        ViewDoc viewDoc = null;

                        // Try and get view.
                        final String ruleIdentity = getAnalyticRuleIdentity(analyticRuleDoc);
                        final SearchRequest searchRequest = analyticRuleSearchRequestHelper.create(analyticRuleDoc);
                        final DocRef dataSource = searchRequest.getQuery().getDataSource();
                        if (dataSource == null || !ViewDoc.DOCUMENT_TYPE.equals(dataSource.getType())) {
                            trackerBuilder.message("Error: Rule needs to reference a view").build();

                        } else {
                            // Load view.
                            viewDoc = loadViewDoc(ruleIdentity, dataSource);

                            // Update tracker state from LMDB if we are using LMDB.
                            if (useLmdb(analyticRuleDoc)) {
                                final AnalyticDataStore dataStore = analyticDataStores.get(analyticRuleDoc);

                                // Get or create LMDB data store.
                                final LmdbDataStore lmdbDataStore = dataStore.lmdbDataStore();
                                final CurrentDbState currentDbState = lmdbDataStore.sync();

                                // Establish the analytic tracker state.
                                updateTrackerWithLmdbState(trackerBuilder, currentDbState);
                            }
                        }

                        loadedAnalyticList.add(new LoadedAnalytic(
                                ruleIdentity,
                                analyticRuleDoc,
                                filter,
                                trackerBuilder,
                                searchRequest,
                                viewDoc));
                    }
                });
            }
        }
        info(() -> LogUtil.message("Finished loading rules in {}", logExecutionTime));
        return loadedAnalyticList;
    }

//    private void processAnalytic(final AnalyticRuleDoc analyticRuleDoc) {
//        final String ruleIdentity = getAnalyticRuleIdentity(analyticRuleDoc);
//        taskContextFactory
//                .childContext(taskContextFactory.current(), "Analytic: " + ruleIdentity,
//                        taskContext -> processAnalytic(analyticRuleDoc, ruleIdentity, taskContext))
//                .run();
//    }
//
//    private void processAnalytic(final AnalyticRuleDoc analyticRuleDoc,
//                                 final String ruleIdentity,
//                                 final TaskContext parentTaskContext) {
//        final Optional<AnalyticProcessorFilter> optionalFilter =
//                analyticProcessorFilterDao.getByAnalyticUuid(analyticRuleDoc.getUuid());
//        optionalFilter.ifPresent(filter -> {
//            if (filter.isEnabled() && nodeInfo.getThisNodeName().equals(filter.getNode())) {
//                processAnalytic(analyticRuleDoc, ruleIdentity, parentTaskContext, filter);
//            }
//        });
//    }
//
//    private void processAnalytic(final AnalyticRuleDoc analyticRuleDoc,
//                                 final String ruleIdentity,
//                                 final TaskContext parentTaskContext,
//                                 final AnalyticProcessorFilter filter) {
//        final LogExecutionTime logExecutionTime = new LogExecutionTime();
//        final AnalyticProcessorFilterTracker tracker = getFilterTracker(filter);
//        final AnalyticProcessorFilterTracker.Builder trackerBuilder = tracker.copy();
//        try {
//            info(() -> LogUtil.message("Starting analytic processing for: {}",
//                    ruleIdentity));
//            if (AnalyticRuleType.EVENT.equals(analyticRuleDoc.getAnalyticRuleType())) {
//                processEventAnalytic(
//                        analyticRuleDoc,
//                        ruleIdentity,
//                        filter,
//                        trackerBuilder,
//                        parentTaskContext);
//            } else if (AnalyticRuleType.AGGREGATE.equals(analyticRuleDoc.getAnalyticRuleType())) {
//                processAggregateAnalytic(
//                        analyticRuleDoc,
//                        ruleIdentity,
//                        filter,
//                        trackerBuilder,
//                        parentTaskContext);
//            }
//            info(() -> LogUtil.message("Finished analytic processing for: {} in {}",
//                    ruleIdentity,
//                    logExecutionTime));
//        } catch (final TaskTerminatedException | UncheckedInterruptedException e) {
//            LOGGER.debug(() -> "Task terminated", e);
//            LOGGER.debug(() -> LogUtil.message("Analytic processing for: {} terminated after {}",
//                    ruleIdentity,
//                    logExecutionTime));
//        } catch (final RuntimeException e) {
//            LOGGER.error(() -> LogUtil.message("Error during analytic processing for: {} {}",
//                            ruleIdentity,
//                            e.getMessage()),
//                    e);
//            disableFilter(ruleIdentity, filter, trackerBuilder, e.getMessage());
//        } finally {
//            // Update tracker.
//            analyticProcessorFilterTrackerDao.update(trackerBuilder.build());
//        }
//    }
//
//    private void disableFilter(final String ruleIdentity,
//                               final AnalyticProcessorFilter filter,
//                               final AnalyticProcessorFilterTracker.Builder trackerBuilder,
//                               final String message) {
//        LOGGER.info("Disabling processing for: {}", ruleIdentity);
//        trackerBuilder.message(message);
//
//        try {
//            final AnalyticProcessorFilter updatedFilter = filter
//                    .copy()
//                    .enabled(false)
//                    .build();
//            analyticProcessorFilterDao.update(updatedFilter);
//        } catch (final RuntimeException e) {
//            LOGGER.error(e::getMessage, e);
//        }
//    }


//    private void processEventAnalytic(final AnalyticRuleDoc analyticRuleDoc,
//                                      final String ruleIdentity,
//                                      final AnalyticProcessorFilter filter,
//                                      final AnalyticProcessorFilterTracker.Builder trackerBuilder,
//                                      final TaskContext parentTaskContext) {
//        final List<AnalyticNotification> notifications =
//                analyticNotificationDao.getByAnalyticUuid(analyticRuleDoc.getUuid());
//        for (final AnalyticNotification notification : notifications) {
//            if (notification.isEnabled()) {
//                final AnalyticNotificationState analyticNotificationState = getNotificationState(notification);
//                final AnalyticNotificationConfig config = notification.getConfig();
//                if (config instanceof final AnalyticNotificationStreamConfig streamConfig) {
//                    try {
//                        processEventAnalytic(
//                                analyticRuleDoc,
//                                ruleIdentity,
//                                notification,
//                                streamConfig,
//                                parentTaskContext,
//                                filter,
//                                trackerBuilder,
//                                analyticNotificationState);
//                    } catch (final TaskTerminatedException | UncheckedInterruptedException e) {
//                        LOGGER.debug(e::getMessage, e);
//                        throw e;
//                    } catch (final RuntimeException e) {
//                        LOGGER.error(e::getMessage, e);
//                        disableNotification(ruleIdentity, notification, analyticNotificationState, e.getMessage());
//                    }
//                }
//            }
//        }
//    }

//    private void processEventAnalytic(final AnalyticRuleDoc analyticRuleDoc,
//                                      final String ruleIdentity,
//                                      final AnalyticNotification notification,
//                                      final AnalyticNotificationStreamConfig streamConfig,
//                                      final TaskContext parentTaskContext,
//                                      final AnalyticProcessorFilter filter,
//                                      final AnalyticProcessorFilterTracker.Builder trackerBuilder,
//                                      AnalyticNotificationState analyticNotificationState) {
//        final SearchRequest searchRequest = analyticRuleSearchRequestHelper.create(analyticRuleDoc);
//        final DocRef dataSource = searchRequest.getQuery().getDataSource();
//        if (dataSource == null || !ViewDoc.DOCUMENT_TYPE.equals(dataSource.getType())) {
//            trackerBuilder.message("Error: Rule needs to reference a view").build();
//
//        } else {
//            final String analyticUuid = analyticRuleDoc.getUuid();
//
//            // Load view.
//            final ViewDoc viewDoc = loadViewDoc(ruleIdentity, dataSource);
//
//            // Get a meta list to process.
//            final AnalyticProcessorFilterTracker tracker = trackerBuilder.build();
//            Long minMetaId = null;
//            if (tracker.getLastMetaId() != null) {
//                // Start at the next meta.
//                minMetaId = tracker.getLastMetaId() + 1;
//            }
//
//            final List<Meta> metaList = getMetaList(
//                    ruleIdentity,
//                    viewDoc.getFilter(),
//                    filter,
//                    minMetaId);
//
//            trackerBuilder.lastPollMs(System.currentTimeMillis());
//            trackerBuilder.lastPollTaskCount(metaList.size());
//
//            if (metaList.size() > 0) {
//                final DocRef extractionPipeline = viewDoc.getPipeline();
//                final PipelineData pipelineData = getPipelineData(extractionPipeline);
//
//                // Create field index.
//                final TableSettings tableSettings = searchRequest.getResultRequests().get(0).getMappings().get(0);
//                final Map<String, String> paramMap = ParamUtil.createParamMap(searchRequest.getQuery().getParams());
//                final CompiledFields compiledFields = CompiledFields.create(tableSettings.getFields(), paramMap);
//                final FieldIndex fieldIndex = compiledFields.getFieldIndex();
//
//                // Cache the query for use across multiple streams.
//                final SearchExpressionQueryCache searchExpressionQueryCache =
//                        new SearchExpressionQueryCache(searchExpressionQueryBuilderFactory, searchRequest);
//
//                try {
//                    for (final Meta meta : metaList) {
//                        if (Status.UNLOCKED.equals(meta.getStatus())) {
//                            analyticErrorWritingExecutor.exec(
//                                    "Analytics Stream Processor",
//                                    meta.getFeedName(),
//                                    analyticUuid,
//                                    extractionPipeline.getUuid(),
//                                    parentTaskContext,
//                                    taskContext -> {
//                                        final DetectionsWriter detectionsWriter = detectionsWriterProvider.get();
//                                        if (!streamConfig.isUseSourceFeedIfPossible()) {
//                                            detectionsWriter.setFeed(streamConfig.getDestinationFeed());
//                                        }
//
//                                        final DetectionWriterProxy alertWriter = alertWriterProvider.get();
//                                        alertWriter.setAnalyticRuleDoc(analyticRuleDoc);
//                                        alertWriter.setCompiledFields(compiledFields);
//                                        alertWriter.setFieldIndex(fieldIndex);
//                                        alertWriter.setDetectionConsumer(detectionsWriter);
//
//                                        final AnalyticFieldListConsumer analyticFieldListConsumer =
//                                                new AnalyticFieldListConsumer(
//                                                        searchRequest,
//                                                        fieldIndex,
//                                                        alertWriter,
//                                                        searchExpressionQueryCache,
//                                                        null);
//
//                                        final ExtractionStateHolder extractionStateHolder =
//                                                fieldListConsumerHolderProvider.get();
//                                        extractionStateHolder.setQueryKey(searchRequest.getKey());
//                                        extractionStateHolder.setFieldListConsumer(analyticFieldListConsumer);
//                                        extractionStateHolder.setFieldIndex(fieldIndex);
//
//                                        try {
//                                            detectionsWriter.start();
//                                            analyticsStreamProcessorProvider.get().extract(
//                                                    taskContext,
//                                                    meta.getId(),
//                                                    extractionPipeline,
//                                                    pipelineData);
//                                        } finally {
//                                            detectionsWriter.end();
//                                        }
//
//                                        trackerBuilder.lastEventId(analyticFieldListConsumer.getEventId());
//                                        trackerBuilder.addEventCount(analyticFieldListConsumer.getEventId());
//                                    });
//
//                            // Update analytic rule state.
//                            trackerBuilder.lastMetaId(meta.getId());
//                            trackerBuilder.incrementMetaCount();
//
//                        } else {
//                            LOGGER.info("Complete for now");
//                            trackerBuilder.message("Complete for now").build();
//                            break;
//                        }
//                    }
//                } catch (final TaskTerminatedException | UncheckedInterruptedException e) {
//                    LOGGER.debug(e::getMessage, e);
//                    throw e;
//                } catch (final RuntimeException e) {
//                    LOGGER.error(e::getMessage, e);
//                    disableFilter(ruleIdentity, filter, trackerBuilder, e.getMessage());
//                }
//            }
//        }
//
//        analyticNotificationState = analyticNotificationState
//                .copy()
//                .lastExecutionTime(System.currentTimeMillis())
//                .build();
//        analyticNotificationStateDao.update(analyticNotificationState);
//    }
//
//    private void processAggregateAnalytic(final AnalyticRuleDoc analyticRuleDoc,
//                                          final String ruleIdentity,
//                                          final AnalyticProcessorFilter filter,
//                                          final AnalyticProcessorFilterTracker.Builder trackerBuilder,
//                                          final TaskContext parentTaskContext) {
//        final long startTimeMs = System.currentTimeMillis();
//        final AnalyticDataStore dataStore = analyticDataStores.get(analyticRuleDoc);
//        final SearchRequest searchRequest = dataStore.searchRequest();
//        final DocRef dataSource = searchRequest.getQuery().getDataSource();
//
//        // Get or create LMDB data store.
//        final LmdbDataStore lmdbDataStore = dataStore.lmdbDataStore();
//        CurrentDbState currentDbState = lmdbDataStore.sync();
//
//        // Establish the analytic tracker state.
//        final AnalyticProcessorFilterTracker tracker = updateTrackerWithLmdbState(trackerBuilder, currentDbState);
//
//        // Load view.
//        final ViewDoc viewDoc = loadViewDoc(ruleIdentity, dataSource);
//
//        // Get a meta list to process.
//        final Long lastMetaId = tracker.getLastMetaId();
//        final Long lastEventId = tracker.getLastEventId();
//        final Long minMetaId;
//        if (lastMetaId != null && lastEventId == null) {
//            // Start at the next meta.
//            minMetaId = lastMetaId + 1;
//        } else {
//            minMetaId = lastMetaId;
//        }
//
//        final List<Meta> metaList = getMetaList(
//                ruleIdentity,
//                viewDoc.getFilter(),
//                filter,
//                minMetaId);
//
//        trackerBuilder.lastPollMs(System.currentTimeMillis());
//        trackerBuilder.lastPollTaskCount(metaList.size());
//
//        Long metaId = null;
//        if (metaList.size() > 0) {
//            final DocRef extractionPipeline = viewDoc.getPipeline();
//            final PipelineData pipelineData = getPipelineData(extractionPipeline);
//
//            // Get the field index.
//            final FieldIndex fieldIndex = lmdbDataStore.getFieldIndex();
//
//            // Cache the query for use across multiple streams.
//            final SearchExpressionQueryCache searchExpressionQueryCache =
//                    new SearchExpressionQueryCache(searchExpressionQueryBuilderFactory, searchRequest);
//
//            for (final Meta meta : metaList) {
//                try {
//                    if (Status.UNLOCKED.equals(meta.getStatus())) {
//                        analyticErrorWritingExecutor.exec(
//                                "Analytics Stream Processor",
//                                meta.getFeedName(),
//                                analyticRuleDoc.getUuid(),
//                                extractionPipeline.getUuid(),
//                                parentTaskContext,
//                                taskContext -> {
//                                    // After a shutdown we may wish to resume event processing from a specific event
//                                    // id.
//                                    Long minEventId = null;
//                                    if (lastEventId != null && meta.getId() == lastMetaId) {
//                                        minEventId = lastEventId + 1;
//                                    }
//
//                                    final AnalyticFieldListConsumer analyticFieldListConsumer =
//                                            new AnalyticFieldListConsumer(
//                                                    searchRequest,
//                                                    fieldIndex,
//                                                    lmdbDataStore,
//                                                    searchExpressionQueryCache,
//                                                    minEventId);
//
//                                    final ExtractionStateHolder extractionStateHolder =
//                                            fieldListConsumerHolderProvider.get();
//                                    extractionStateHolder.setQueryKey(searchRequest.getKey());
//                                    extractionStateHolder.setFieldListConsumer(analyticFieldListConsumer);
//                                    extractionStateHolder.setFieldIndex(fieldIndex);
//
//                                    analyticsStreamProcessorProvider.get().extract(
//                                            taskContext,
//                                            meta.getId(),
//                                            extractionPipeline,
//                                            pipelineData);
//
//                                    trackerBuilder.incrementMetaCount();
//                                    trackerBuilder.addEventCount(analyticFieldListConsumer.getEventId());
//                                });
//                        metaId = meta.getId();
//
//                    } else {
//                        LOGGER.info("Complete for now");
//                        trackerBuilder.message("Complete for now");
//                        break;
//                    }
//                } catch (final TaskTerminatedException | UncheckedInterruptedException e) {
//                    LOGGER.debug(e::getMessage, e);
//                    throw e;
//                } catch (final RuntimeException e) {
//                    LOGGER.error(e::getMessage, e);
//                    trackerBuilder.message(e.getMessage());
//                    throw e;
//                }
//            }
//
//            // Now we've added data synchronise LMDB and record state.
//            if (metaId != null) {
//                currentDbState = lmdbDataStore.sync();
//
//                // Remember meta load state.
//                updateTrackerWithLmdbState(trackerBuilder, currentDbState);
//            }
//        }
//
//        // Determine if we have processed all of the current data.
//        final boolean uptoDate = allowUpToDateProcessing && metaList.size() < maxMetaListSize;
//
//        // Now execute notifications.
//        executeNotifications(
//                analyticRuleDoc,
//                ruleIdentity,
//                dataStore,
//                currentDbState,
//                parentTaskContext,
//                uptoDate,
//                startTimeMs);
//
//        // Delete old data from the DB.
//        applyDataRetentionRules(lmdbDataStore, analyticRuleDoc);
//    }

    private void executePostProcessNotifications(final List<LoadedAnalytic> analytics,
                                                 final TaskContext parentTaskContext,
                                                 final LocalDateTime startTime,
                                                 final List<Meta> metaList) {
        // Execute notifications on LMDB stores and sync.
        for (final LoadedAnalytic analytic : analytics) {
            if (useLmdb(analytic.analyticRuleDoc())) {
                final AnalyticDataStore dataStore = analyticDataStores.get(analytic.analyticRuleDoc());

                // Get or create LMDB data store.
                final LmdbDataStore lmdbDataStore = dataStore.lmdbDataStore();
                CurrentDbState currentDbState = lmdbDataStore.sync();

                // Remember meta load state.
                updateTrackerWithLmdbState(analytic.trackerBuilder(), currentDbState);

                // Determine if we have processed all of the current data.
                final boolean uptoDate = allowUpToDateProcessing && metaList.size() < maxMetaListSize;

                // Now execute notifications.
                executeLmdbNotifications(
                        analytic,
                        dataStore,
                        currentDbState,
                        parentTaskContext,
                        uptoDate,
                        startTime);

                // Delete old data from the DB.
                applyDataRetentionRules(lmdbDataStore, analytic.analyticRuleDoc());
            }
        }
    }

    private void executeLmdbNotifications(final LoadedAnalytic analytic,
                                          final AnalyticDataStore dataStore,
                                          final CurrentDbState currentDbState,
                                          final TaskContext parentTaskContext,
                                          final boolean upToDate,
                                          final LocalDateTime startTime) {
        final List<AnalyticNotification> notifications =
                analyticNotificationDao.getByAnalyticUuid(analytic.analyticRuleDoc().getUuid());
        for (final AnalyticNotification notification : notifications) {
            if (notification.isEnabled()) {
                final AnalyticNotificationState notificationState =
                        analyticNotificationService.getNotificationState(notification);
                try {
                    executeLmdbNotification(
                            analytic,
                            notification,
                            notificationState,
                            dataStore,
                            currentDbState,
                            parentTaskContext,
                            upToDate,
                            startTime);
                } catch (final TaskTerminatedException | UncheckedInterruptedException e) {
                    LOGGER.debug(e::getMessage, e);
                    throw e;
                } catch (final RuntimeException e) {
                    LOGGER.error(e::getMessage, e);
                    analyticNotificationService.disableNotification(analytic.ruleIdentity(),
                            notification,
                            notificationState,
                            e.getMessage());
                }
            }
        }
    }

    private void executeLmdbNotification(final LoadedAnalytic analytic,
                                         final AnalyticNotification notification,
                                         final AnalyticNotificationState notificationState,
                                         final AnalyticDataStore dataStore,
                                         final CurrentDbState currentDbState,
                                         final TaskContext parentTaskContext,
                                         final boolean upToDate,
                                         final LocalDateTime startTime) {
        final AnalyticNotificationConfig config = notification.getConfig();
        if (config instanceof final AnalyticNotificationStreamConfig streamConfig) {
            final DocRef feedDocRef = streamConfig.getDestinationFeed();
            if (feedDocRef == null) {
                throw new RuntimeException("Destination feed not specified for notification in: " +
                        analytic.ruleIdentity());
            }

            analyticErrorWritingExecutor.wrap(
                    "Analytics Aggregate Rule Executor",
                    feedDocRef.getName(),
                    null,
                    parentTaskContext,
                    taskContext -> {
                        final DetectionsWriter detectionsWriter = detectionsWriterProvider.get();
                        detectionsWriter.setFeed(feedDocRef);
                        detectionsWriter.start();
                        try {
                            try {
                                runNotification(analytic,
                                        notification,
                                        streamConfig,
                                        notificationState,
                                        detectionsWriter,
                                        dataStore,
                                        currentDbState,
                                        upToDate,
                                        startTime);
                            } catch (final TaskTerminatedException | UncheckedInterruptedException e) {
                                LOGGER.debug(e::getMessage, e);
                                throw e;
                            } catch (final RuntimeException e) {
                                LOGGER.error(e::getMessage, e);
                                throw e;
                            }
                        } finally {
                            detectionsWriter.end();
                        }
                    }).run();

        }

    }

    private void runNotification(final LoadedAnalytic analytic,
                                 final AnalyticNotification notification,
                                 final AnalyticNotificationStreamConfig streamConfig,
                                 final AnalyticNotificationState notificationState,
                                 final DetectionConsumer detectionConsumer,
                                 final AnalyticDataStore dataStore,
                                 final CurrentDbState currentDbState,
                                 final boolean upToDate,
                                 final LocalDateTime startTime) {

        final Optional<LocalDateTime> lastEventTime = Optional
                .ofNullable(currentDbState)
                .map(CurrentDbState::getLastEventTime)
                .map(time -> LocalDateTime.ofInstant(Instant.ofEpochMilli(time), ZoneOffset.UTC));

        SearchRequest searchRequest = dataStore.searchRequest();
        final LmdbDataStore lmdbDataStore = dataStore.lmdbDataStore();
        final QueryKey queryKey = analytic.analyticRuleDoc().getQueryKey();
        final Optional<TimeFilter> optionalTimeFilter = analytic.createTimeFilter(
                streamConfig,
                notificationState,
                upToDate,
                startTime,
                lastEventTime);

        if (optionalTimeFilter.isPresent()) {
            // Create a time filter.
            final TimeFilter timeFilter = optionalTimeFilter.get();

            searchRequest = searchRequest.copy().key(queryKey).incremental(true).build();
            // Perform the search.
            ResultRequest resultRequest = searchRequest.getResultRequests().get(0);
            resultRequest = resultRequest.copy().timeFilter(timeFilter).build();
            final TableResultConsumer tableResultConsumer =
                    new TableResultConsumer(analytic.analyticRuleDoc(), detectionConsumer);

            final FieldFormatter fieldFormatter =
                    new FieldFormatter(new FormatterFactory(null));
            final TableResultCreator resultCreator = new TableResultCreator(fieldFormatter) {
                @Override
                public TableResultBuilder createTableResultBuilder() {
                    return tableResultConsumer;
                }
            };

            // Create result.
            resultCreator.create(lmdbDataStore, resultRequest);

            // Remember last successful execution time.
            final AnalyticNotificationState newState = notificationState.copy()
                    .lastExecutionTime(timeFilter.getTo())
                    .build();
            analyticNotificationService.updateNotificationState(newState);
        }
    }

    private void applyDataRetentionRules(final LmdbDataStore lmdbDataStore,
                                         final AnalyticRuleDoc analyticRuleDoc) {
        final SimpleDuration dataRetention = analyticRuleDoc.getDataRetention();
        if (dataRetention != null) {
            final CurrentDbState currentDbState = lmdbDataStore.sync();

            final LocalDateTime from = BEGINNING_OF_TIME;
            LocalDateTime to = Optional
                    .ofNullable(currentDbState)
                    .map(CurrentDbState::getLastEventTime)
                    .map(time -> LocalDateTime.ofInstant(Instant.ofEpochMilli(time), ZoneOffset.UTC))
                    .orElse(BEGINNING_OF_TIME);
            to = SimpleDurationUtil.minus(to, dataRetention);

            // Create a time filter.
            final TimeFilter timeFilter = new TimeFilter(
                    from.toInstant(ZoneOffset.UTC).toEpochMilli(),
                    to.toInstant(ZoneOffset.UTC).toEpochMilli());

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

//    private AnalyticProcessorFilterTracker getAnalyticRuleState(final String analyticUuid,
//                                                                final CurrentDbState currentDbState) {
//        AnalyticProcessorFilter analyticProcessorFilter = ruleStateCache.get(analyticUuid);
//        if (analyticProcessorFilter == null) {
//            final Optional<AnalyticProcessorFilter> optionalAnalyticRuleState = analyticProcessorFilterDao.getState(
//                    analyticUuid);
//            if (optionalAnalyticRuleState.isEmpty()) {
//                final long now = System.currentTimeMillis();
//                final String userId = securityContext.getUserId();
//                final AnalyticProcessorFilter newState = AnalyticProcessorFilter.builder()
//                        .version(1)
//                        .createTime(now)
//                        .createUser(userId)
//                        .updateTime(now)
//                        .updateUser(userId)
//                        .analyticUuid(analyticUuid)
//                        .build();
//                analyticProcessorFilterDao.createState(newState);
//                analyticProcessorFilter = analyticProcessorFilterDao.getState(analyticUuid)
//                        .orElseThrow(() -> new RuntimeException("Unable to fetch new state"));
//            } else {
//                analyticProcessorFilter = optionalAnalyticRuleState.get();
//                if (currentDbState != null) {
//                    final AnalyticProcessorFilter newState = analyticProcessorFilter.copy()
//                            .lastMetaId(currentDbState.getStreamId())
//                            .lastEventId(currentDbState.getEventId())
//                            .lastEventTime(currentDbState.getLastEventTime())
//                            .build();
//                    analyticProcessorFilterDao.updateState(newState);
//                    analyticProcessorFilter = analyticProcessorFilterDao.getState(analyticUuid)
//                            .orElseThrow(() -> new RuntimeException("Unable to fetch new state"));
//                }
//
//            }
//        }
//        ruleStateCache.put(analyticUuid, analyticProcessorFilter);
//        return analyticProcessorFilter;
//    }

    private void updateTrackerWithLmdbState(final AnalyticProcessorFilterTracker.Builder
                                                    trackerBuilder,
                                            final CurrentDbState currentDbState) {
        if (currentDbState != null) {
            trackerBuilder
                    .lastMetaId(currentDbState.getStreamId())
                    .lastEventId(currentDbState.getEventId())
                    .lastEventTime(currentDbState.getLastEventTime());
        }
    }


    private AnalyticProcessorFilterTracker getFilterTracker(final AnalyticProcessorFilter filter) {
        Optional<AnalyticProcessorFilterTracker> optionalTracker =
                analyticProcessorFilterTrackerDao.get(filter.getUuid());
        while (optionalTracker.isEmpty()) {
            final AnalyticProcessorFilterTracker tracker = AnalyticProcessorFilterTracker.builder()
                    .filterUuid(filter.getUuid())
                    .build();
            analyticProcessorFilterTrackerDao.create(tracker);
            optionalTracker = analyticProcessorFilterTrackerDao.get(filter.getUuid());
        }
        return optionalTracker.get();
    }

    private AnalyticProcessorFilterTracker updateFilterTracker(AnalyticProcessorFilterTracker tracker) {
        analyticProcessorFilterTrackerDao.update(tracker);
        tracker = analyticProcessorFilterTrackerDao.get(tracker.getFilterUuid())
                .orElseThrow(() -> new RuntimeException("Unable to load tracker"));
//        ruleStateCache.put(analyticProcessorFilter.analyticUuid(), analyticProcessorFilter);
        return tracker;
    }

    private boolean useLmdb(final AnalyticRuleDoc analyticRuleDoc) {
        return analyticRuleDoc.getAnalyticRuleType() == AnalyticRuleType.TABLE_CREATION ||
                analyticRuleDoc.getAnalyticRuleType() == null;
    }

    private ViewDoc loadViewDoc(final String ruleIdentity,
                                final DocRef viewDocRef) {
        final ViewDoc viewDoc = viewStore.readDocument(viewDocRef);
        if (viewDoc == null) {
            throw new RuntimeException("Unable to process analytic: " +
                    ruleIdentity +
                    " because selected view cannot be found");
        }
        return viewDoc;
    }

    private String getAnalyticRuleIdentity(final AnalyticRuleDoc analyticRuleDoc) {
        return analyticRuleDoc.getName() +
                " (" +
                analyticRuleDoc.getUuid() +
                ")";
    }

//    private List<Meta> getMetaList(final String ruleIdentity,
//                                   final ExpressionOperator findMetaExpression,
//                                   final AnalyticProcessorFilter filter,
//                                   final Long minMetaId) {
//        if (ExpressionUtil.termCount(findMetaExpression) == 0) {
//            throw new RuntimeException("Unable to process analytic: " +
//                    ruleIdentity +
//                    " because filter has no meta expression");
//        }
//
//        return findMeta(findMetaExpression,
//                minMetaId,
//                filter.getMinMetaCreateTimeMs(),
//                filter.getMaxMetaCreateTimeMs(),
//                maxMetaListSize);
//    }

    private List<Meta> findMeta(final ExpressionOperator expression,
                                final Long minMetaId,
                                final Long minMetaCreateTimeMs,
                                final Long maxMetaCreateTimeMs,
                                final int length) {
        // Don't select deleted streams.
        final ExpressionOperator statusExpression = ExpressionOperator.builder().op(Op.OR)
                .addTerm(MetaFields.STATUS, Condition.EQUALS, Status.UNLOCKED.getDisplayValue())
                .addTerm(MetaFields.STATUS, Condition.EQUALS, Status.LOCKED.getDisplayValue())
                .build();

        ExpressionOperator.Builder builder = ExpressionOperator.builder()
                .addOperator(expression);
        if (minMetaId != null) {
            builder = builder.addTerm(MetaFields.ID, Condition.GREATER_THAN_OR_EQUAL_TO, minMetaId);
        }

        if (minMetaCreateTimeMs != null) {
            builder = builder.addTerm(MetaFields.CREATE_TIME,
                    Condition.GREATER_THAN_OR_EQUAL_TO,
                    DateUtil.createNormalDateTimeString(minMetaCreateTimeMs));
        }
        if (maxMetaCreateTimeMs != null) {
            builder = builder.addTerm(MetaFields.CREATE_TIME,
                    Condition.LESS_THAN_OR_EQUAL_TO,
                    DateUtil.createNormalDateTimeString(maxMetaCreateTimeMs));
        }
        builder = builder.addOperator(statusExpression);

        final FindMetaCriteria findMetaCriteria = new FindMetaCriteria(builder.build());
        findMetaCriteria.setSort(MetaFields.ID.getName(), false, false);
        findMetaCriteria.obtainPageRequest().setLength(length);

        return metaService.find(findMetaCriteria).getValues();
    }

    public void setAllowUpToDateProcessing(final boolean allowUpToDateProcessing) {
        this.allowUpToDateProcessing = allowUpToDateProcessing;
    }

    private long getMin(Long currentValue, Long newValue) {
        if (newValue == null) {
            return 0L;
        } else if (currentValue == null) {
            return newValue;
        }
        return Math.min(currentValue, newValue);
    }

    private long getMax(Long currentValue, Long newValue) {
        if (newValue == null) {
            return Long.MAX_VALUE;
        } else if (currentValue == null) {
            return newValue;
        }
        return Math.max(currentValue, newValue);
    }

    private static class TableResultConsumer implements TableResultBuilder {

        private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TableResultConsumer.class);

        private final AnalyticRuleDoc analyticRuleDoc;
        private final DetectionConsumer detectionConsumer;

        private List<Field> fields;

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
        public TableResultConsumer fields(final List<Field> fields) {
            this.fields = fields;
            return this;
        }

        @Override
        public TableResultConsumer addRow(final Row row) {
            try {
                final List<Value> values = new ArrayList<>();

                int index = 0;
                Long streamId = null;
                Long eventId = null;
                for (final Field field : fields) {
                    final String fieldValue = row.getValues().get(index);
                    if (fieldValue != null) {
                        final String fieldName = field.getDisplayValue();

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
                            values.add(new Value(fieldName, fieldValue));
                        }
                    }

                    index++;
                }

                final Detection detection = new Detection(
                        Instant.now(),
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
                        List.of(new LinkedEvent(null, streamId, eventId))
                );

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
}
