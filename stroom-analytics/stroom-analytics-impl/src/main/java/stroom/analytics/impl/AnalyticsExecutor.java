package stroom.analytics.impl;

import stroom.analytics.api.AlertManager;
import stroom.analytics.impl.AnalyticDataStores.AnalyticDataStore;
import stroom.analytics.impl.RecordConsumer.Data;
import stroom.analytics.impl.RecordConsumer.Record;
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
import stroom.meta.api.MetaService;
import stroom.meta.shared.FindMetaCriteria;
import stroom.meta.shared.Meta;
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
import stroom.query.common.v2.Sizes;
import stroom.query.common.v2.TableResultCreator;
import stroom.query.common.v2.format.FieldFormatter;
import stroom.query.common.v2.format.FormatterFactory;
import stroom.search.extraction.ExtractionException;
import stroom.search.extraction.ExtractionStateHolder;
import stroom.search.impl.SearchExpressionQueryBuilderFactory;
import stroom.security.api.SecurityContext;
import stroom.task.api.ExecutorProvider;
import stroom.task.api.TaskContext;
import stroom.task.api.TaskContextFactory;
import stroom.util.date.DateUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.time.SimpleDuration;
import stroom.util.time.SimpleDurationUtil;
import stroom.view.impl.ViewStore;
import stroom.view.shared.ViewDoc;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;

@Singleton
public class AnalyticsExecutor {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(AnalyticsExecutor.class);
    private static final LocalDateTime BEGINNING_OF_TIME =
            LocalDateTime.ofInstant(Instant.ofEpochMilli(0), ZoneOffset.UTC);

    private final AnalyticRuleSearchRequestHelper analyticRuleSearchRequestHelper;
    private final ExecutorProvider executorProvider;
    private final Provider<DetectionsWriter> detectionsWriterProvider;
    private final ViewStore viewStore;
    private final MetaService metaService;
    private final AnalyticRuleStore analyticRuleStore;
    private final SecurityContext securityContext;
    private final PipelineStore pipelineStore;
    private final PipelineDataCache pipelineDataCache;
    private final Provider<AnalyticsStreamProcessor> analyticsStreamProcessorProvider;
    private final Provider<ExtractionStateHolder> extractionStateHolderProvider;
    private final AnalyticDataStores analyticDataStores;
    private final Provider<AlertWriter2> alertWriterProvider;
    private final TaskContextFactory taskContextFactory;
    private final SearchExpressionQueryBuilderFactory searchExpressionQueryBuilderFactory;
    private final AnalyticProcessorFilterDao analyticProcessorFilterDao;
    private final AnalyticProcessorFilterTrackerDao analyticProcessorFilterTrackerDao;
    private final AnalyticNotificationDao analyticNotificationDao;
    private final AnalyticNotificationStateDao analyticNotificationStateDao;
    private final NodeInfo nodeInfo;


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
                             final Provider<ExtractionStateHolder> extractionStateHolderProvider,
                             final TaskContextFactory taskContextFactory,
                             final SearchExpressionQueryBuilderFactory searchExpressionQueryBuilderFactory,
                             final AnalyticDataStores analyticDataStores,
                             final Provider<AlertWriter2> alertWriterProvider,
                             final AnalyticProcessorFilterDao analyticProcessorFilterDao,
                             final AnalyticProcessorFilterTrackerDao analyticProcessorFilterTrackerDao,
                             final AnalyticNotificationDao analyticNotificationDao,
                             final AnalyticNotificationStateDao analyticNotificationStateDao,
                             final AnalyticErrorWritingExecutor analyticErrorWritingExecutor,
                             final NodeInfo nodeInfo) {
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
        this.extractionStateHolderProvider = extractionStateHolderProvider;
        this.taskContextFactory = taskContextFactory;
        this.searchExpressionQueryBuilderFactory = searchExpressionQueryBuilderFactory;
        this.analyticDataStores = analyticDataStores;
        this.alertWriterProvider = alertWriterProvider;
        this.analyticProcessorFilterDao = analyticProcessorFilterDao;
        this.analyticProcessorFilterTrackerDao = analyticProcessorFilterTrackerDao;
        this.analyticNotificationDao = analyticNotificationDao;
        this.analyticNotificationStateDao = analyticNotificationStateDao;
        this.analyticErrorWritingExecutor = analyticErrorWritingExecutor;
        this.nodeInfo = nodeInfo;
    }

    public void exec() {
        securityContext.asProcessingUser(() -> {
            // Delete old data stores.
            analyticDataStores.deleteOldStores();

            // Get views for each analytic rule.
            final List<DocRef> docRefList = analyticRuleStore.list();
            for (final DocRef docRef : docRefList) {
                try {
                    final AnalyticRuleDoc analyticRuleDoc = analyticRuleStore.readDocument(docRef);
                    if (analyticRuleDoc != null) {
                        process(analyticRuleDoc);
                    }
                } catch (final RuntimeException e) {
                    LOGGER.error(e::getMessage, e);
                }
            }
        });
    }

    private void process(final AnalyticRuleDoc analyticRuleDoc) {
        taskContextFactory.context(
                "Analytic: " + getAnalyticRuleIdentity(analyticRuleDoc),
                parentTaskContext -> {
                    final Optional<AnalyticProcessorFilter> optionalFilter =
                            analyticProcessorFilterDao.getByAnalyticUuid(analyticRuleDoc.getUuid());
                    optionalFilter.ifPresent(filter -> {
                        if (filter.isEnabled() && nodeInfo.getThisNodeName().equals(filter.getNode())) {
                            final AnalyticProcessorFilterTracker tracker = getFilterTracker(filter);
                            try {
                                if (AnalyticRuleType.EVENT.equals(analyticRuleDoc.getAnalyticRuleType())) {
                                    processEventAnalytic(
                                            analyticRuleDoc,
                                            filter,
                                            tracker,
                                            parentTaskContext);
                                } else if (AnalyticRuleType.AGGREGATE.equals(analyticRuleDoc.getAnalyticRuleType())) {
                                    processAggregateAnalytic(
                                            analyticRuleDoc,
                                            filter,
                                            tracker,
                                            parentTaskContext);
                                }
                            } catch (final RuntimeException e) {
                                LOGGER.error(e::getMessage, e);
                                disableFilter(analyticRuleDoc, filter, tracker, e.getMessage());
                            }
                        }
                    });
                }).run();
    }

    private void disableFilter(final AnalyticRuleDoc doc,
                               final AnalyticProcessorFilter filter,
                               final AnalyticProcessorFilterTracker tracker,
                               final String message) {
        LOGGER.info("Disabling processing: " + doc.getName());

        try {
            final AnalyticProcessorFilterTracker updatedTracker = tracker
                    .copy()
                    .message(message)
                    .build();
            analyticProcessorFilterTrackerDao.update(updatedTracker);
        } catch (final RuntimeException e) {
            LOGGER.error(e::getMessage, e);
        }

        try {
            final AnalyticProcessorFilter updatedFilter = filter
                    .copy()
                    .enabled(false)
                    .build();
            analyticProcessorFilterDao.update(updatedFilter);
        } catch (final RuntimeException e) {
            LOGGER.error(e::getMessage, e);
        }
    }

    private void disableNotification(final AnalyticRuleDoc doc,
                                     final AnalyticNotification notification,
                                     final AnalyticNotificationState state,
                                     final String message) {
        LOGGER.info("Disabling notification: " + doc.getName());

        try {
            final AnalyticNotificationState updatedState = state
                    .copy()
                    .message(message)
                    .build();
            analyticNotificationStateDao.update(updatedState);
        } catch (final RuntimeException e) {
            LOGGER.error(e::getMessage, e);
        }

        try {
            final AnalyticNotification updatedNotification = notification
                    .copy()
                    .enabled(false)
                    .build();
            analyticNotificationDao.update(updatedNotification);
        } catch (final RuntimeException e) {
            LOGGER.error(e::getMessage, e);
        }
    }

    private void processEventAnalytic(final AnalyticRuleDoc analyticRuleDoc,
                                      final AnalyticProcessorFilter filter,
                                      final AnalyticProcessorFilterTracker tracker,
                                      final TaskContext parentTaskContext) {
        final List<AnalyticNotification> notifications = analyticNotificationDao.getByAnalyticUuid(analyticRuleDoc.getUuid());
        for (final AnalyticNotification notification : notifications) {
            if (notification.isEnabled()) {
                final AnalyticNotificationState analyticNotificationState = getNotificationState(notification);
                final AnalyticNotificationConfig config = notification.getConfig();
                if (config instanceof final AnalyticNotificationStreamConfig streamConfig) {
                    try {
                        processEventAnalytic(
                                analyticRuleDoc,
                                notification,
                                streamConfig,
                                parentTaskContext,
                                filter,
                                tracker,
                                analyticNotificationState);
                    } catch (final RuntimeException e) {
                        LOGGER.error(e::getMessage, e);
                        disableNotification(analyticRuleDoc, notification, analyticNotificationState, e.getMessage());
                    }
                }
            }
        }
    }

    private void processEventAnalytic(final AnalyticRuleDoc analyticRuleDoc,
                                      final AnalyticNotification notification,
                                      final AnalyticNotificationStreamConfig streamConfig,
                                      final TaskContext parentTaskContext,
                                      final AnalyticProcessorFilter filter,
                                      AnalyticProcessorFilterTracker tracker,
                                      AnalyticNotificationState analyticNotificationState) {

        final SearchRequest searchRequest = analyticRuleSearchRequestHelper.create(analyticRuleDoc);
        final DocRef dataSource = searchRequest.getQuery().getDataSource();
        if (dataSource == null || !ViewDoc.DOCUMENT_TYPE.equals(dataSource.getType())) {
            tracker = tracker.copy().message("Error: Rule needs to reference a view").build();

        } else {
            final String analyticUuid = analyticRuleDoc.getUuid();

            // Load view.
            final ViewDoc viewDoc = loadViewDoc(analyticRuleDoc, dataSource);

            // Get a meta list to process.
            Long minMetaId = null;
            if (tracker.getLastMetaId() != null) {
                // Start at the next meta.
                minMetaId = tracker.getLastMetaId() + 1;
            }

            final List<Meta> metaList = getMetaList(
                    analyticRuleDoc,
                    filter,
                    minMetaId);

            if (metaList.size() > 0) {
                final DocRef extractionPipeline = viewDoc.getPipeline();
                final PipelineData pipelineData = getPipelineData(extractionPipeline);

                // Create field index.
                final TableSettings tableSettings = searchRequest.getResultRequests().get(0).getMappings().get(0);
                final Map<String, String> paramMap = ParamUtil.createParamMap(searchRequest.getQuery().getParams());
                final CompiledFields compiledFields = CompiledFields.create(tableSettings.getFields(), paramMap);
                final FieldIndex fieldIndex = compiledFields.getFieldIndex();

                // Cache the query for use across multiple streams.
                final SearchExpressionQueryCache searchExpressionQueryCache =
                        new SearchExpressionQueryCache(searchExpressionQueryBuilderFactory, searchRequest);

                try {
                    for (final Meta meta : metaList) {
                        if (Status.UNLOCKED.equals(meta.getStatus())) {
                            analyticErrorWritingExecutor.exec(
                                    "Analytics Stream Processor",
                                    meta.getFeedName(),
                                    analyticUuid,
                                    extractionPipeline.getUuid(),
                                    parentTaskContext,
                                    taskContext -> {
                                        final DetectionsWriter detectionsWriter = detectionsWriterProvider.get();
                                        if (!streamConfig.isUseSourceFeedIfPossible()) {
                                            detectionsWriter.setFeed(streamConfig.getDestinationFeed());
                                        }

                                        final AlertWriter2 alertWriter = alertWriterProvider.get();
                                        alertWriter.setAnalyticRuleDoc(analyticRuleDoc);
                                        alertWriter.setCompiledFields(compiledFields);
                                        alertWriter.setFieldIndex(fieldIndex);
                                        alertWriter.setRecordConsumer(detectionsWriter);

                                        final AnalyticFieldListConsumer analyticFieldListConsumer =
                                                new AnalyticFieldListConsumer(
                                                        searchRequest,
                                                        fieldIndex,
                                                        alertWriter,
                                                        searchExpressionQueryCache,
                                                        null);

                                        final ExtractionStateHolder extractionStateHolder =
                                                extractionStateHolderProvider.get();
                                        extractionStateHolder.setQueryKey(searchRequest.getKey());
                                        extractionStateHolder.setFieldListConsumer(analyticFieldListConsumer);
                                        extractionStateHolder.setFieldIndex(fieldIndex);

                                        try {
                                            detectionsWriter.start();
                                            analyticsStreamProcessorProvider.get().extract(
                                                    taskContext,
                                                    meta.getId(),
                                                    extractionPipeline,
                                                    pipelineData);
                                        } finally {
                                            detectionsWriter.end();
                                        }
                                    });

                            // Update analytic rule state.
                            tracker = tracker.copy().lastMetaId(meta.getId()).build();

                        } else {
                            LOGGER.info("Complete for now");
                            tracker = tracker.copy().message("Complete for now").build();
                            break;
                        }
                    }
                } catch (final RuntimeException e) {
                    LOGGER.error(e::getMessage, e);
                    disableFilter(analyticRuleDoc, filter, tracker, e.getMessage());
                }
            }
        }

        analyticNotificationState = analyticNotificationState
                .copy()
                .lastExecutionTime(System.currentTimeMillis())
                .build();

        // Update tracker.
        analyticProcessorFilterTrackerDao.update(tracker);
        analyticNotificationStateDao.update(analyticNotificationState);
    }

    private void processAggregateAnalytic(final AnalyticRuleDoc analyticRuleDoc,
                                          final AnalyticProcessorFilter filter,
                                          AnalyticProcessorFilterTracker tracker,
                                          final TaskContext parentTaskContext) {
        final AnalyticDataStore dataStore = analyticDataStores.get(analyticRuleDoc);
        final SearchRequest searchRequest = dataStore.searchRequest();
        final DocRef dataSource = searchRequest.getQuery().getDataSource();

        // Get or create LMDB data store.
        final LmdbDataStore lmdbDataStore = dataStore.lmdbDataStore();
        CurrentDbState currentDbState = lmdbDataStore.sync();

        // Establish the analytic tracker state.
        tracker = updateTrackerWithLmdbState(tracker, currentDbState);

        // Load view.
        final ViewDoc viewDoc = loadViewDoc(analyticRuleDoc, dataSource);

        // Get a meta list to process.
        final Long lastMetaId = tracker.getLastMetaId();
        final Long lastEventId = tracker.getLastEventId();
        final Long minMetaId;
        if (lastMetaId != null && lastEventId == null) {
            // Start at the next meta.
            minMetaId = lastMetaId + 1;
        } else {
            minMetaId = lastMetaId;
        }

        final List<Meta> metaList = getMetaList(
                analyticRuleDoc,
                filter,
                minMetaId);

        Long metaId = null;
        if (metaList.size() > 0) {
            final DocRef extractionPipeline = viewDoc.getPipeline();
            final PipelineData pipelineData = getPipelineData(extractionPipeline);

            // Get the field index.
            final FieldIndex fieldIndex = lmdbDataStore.getFieldIndex();

            // Cache the query for use across multiple streams.
            final SearchExpressionQueryCache searchExpressionQueryCache =
                    new SearchExpressionQueryCache(searchExpressionQueryBuilderFactory, searchRequest);

            for (final Meta meta : metaList) {
                try {
                    if (Status.UNLOCKED.equals(meta.getStatus())) {
                        analyticErrorWritingExecutor.exec(
                                "Analytics Stream Processor",
                                meta.getFeedName(),
                                analyticRuleDoc.getUuid(),
                                extractionPipeline.getUuid(),
                                parentTaskContext,
                                taskContext -> {
                                    // After a shutdown we may wish to resume event processing from a specific event id.
                                    Long minEventId = null;
                                    if (lastEventId != null && meta.getId() == lastMetaId) {
                                        minEventId = lastEventId + 1;
                                    }

                                    final AnalyticFieldListConsumer analyticFieldListConsumer =
                                            new AnalyticFieldListConsumer(
                                                    searchRequest,
                                                    fieldIndex,
                                                    lmdbDataStore,
                                                    searchExpressionQueryCache,
                                                    minEventId);

                                    final ExtractionStateHolder extractionStateHolder =
                                            extractionStateHolderProvider.get();
                                    extractionStateHolder.setQueryKey(searchRequest.getKey());
                                    extractionStateHolder.setFieldListConsumer(analyticFieldListConsumer);
                                    extractionStateHolder.setFieldIndex(fieldIndex);

                                    analyticsStreamProcessorProvider.get().extract(
                                            taskContext,
                                            meta.getId(),
                                            extractionPipeline,
                                            pipelineData);
                                });
                        metaId = meta.getId();

                    } else {
                        LOGGER.info("Complete for now");
                        break;
                    }
                } catch (final RuntimeException e) {
                    LOGGER.error(e::getMessage, e);
                    throw e;
                }
            }

            // Now we've added data synchronise LMDB and record state.
            if (metaId != null) {
                currentDbState = lmdbDataStore.sync();

                // Remember meta load state.
                final AnalyticProcessorFilterTracker newState = tracker.copy()
                        .lastMetaId(metaId)
                        .lastEventId(null)
                        .lastEventTime(null)
                        .build();
                tracker = updateFilterTracker(newState);
            }
        }

        // Now execute notifications.
        executeNotifications(analyticRuleDoc, dataStore, currentDbState, parentTaskContext);

        // Delete old data from the DB.
        applyDataRetentionRules(lmdbDataStore, analyticRuleDoc);
    }

    private void executeNotifications(final AnalyticRuleDoc analyticRuleDoc,
                                      final AnalyticDataStore dataStore,
                                      final CurrentDbState currentDbState,
                                      final TaskContext parentTaskContext) {
        final List<AnalyticNotification> notifications = analyticNotificationDao.getByAnalyticUuid(analyticRuleDoc.getUuid());
        for (final AnalyticNotification notification : notifications) {
            if (notification.isEnabled()) {
                final AnalyticNotificationState notificationState = getNotificationState(notification);
                try {
                    executeNotification(
                            analyticRuleDoc,
                            notification,
                            notificationState,
                            dataStore,
                            currentDbState,
                            parentTaskContext);
                } catch (final RuntimeException e) {
                    LOGGER.error(e::getMessage, e);
                    disableNotification(analyticRuleDoc, notification, notificationState, e.getMessage());
                }
            }
        }
    }

    private void executeNotification(final AnalyticRuleDoc analyticRuleDoc,
                                     final AnalyticNotification notification,
                                     final AnalyticNotificationState notificationState,
                                     final AnalyticDataStore dataStore,
                                     final CurrentDbState currentDbState,
                                     final TaskContext parentTaskContext) {
        final AnalyticNotificationConfig config = notification.getConfig();
        if (config instanceof final AnalyticNotificationStreamConfig streamConfig) {
            final DocRef feedDocRef = streamConfig.getDestinationFeed();
            if (feedDocRef == null) {
                throw new RuntimeException("Destination feed not specified for notification in: " +
                        getAnalyticRuleIdentity(analyticRuleDoc));
            }

            analyticErrorWritingExecutor.exec(
                    "Analytics Aggregate Rule Executor",
                    feedDocRef.getName(),
                    analyticRuleDoc.getUuid(),
                    null,
                    parentTaskContext,
                    taskContext -> {
                        final DetectionsWriter detectionsWriter = detectionsWriterProvider.get();
                        detectionsWriter.setFeed(feedDocRef);
                        detectionsWriter.start();
                        try {
                            try {
                                runNotification(analyticRuleDoc,
                                        notification,
                                        streamConfig,
                                        notificationState,
                                        detectionsWriter,
                                        dataStore,
                                        currentDbState);
                            } catch (final RuntimeException e) {
                                LOGGER.error(e::getMessage, e);
                                throw e;
                            }
                        } finally {
                            detectionsWriter.end();
                        }
                    });

        }

    }

    private void runNotification(final AnalyticRuleDoc analyticRuleDoc,
                                 final AnalyticNotification notification,
                                 final AnalyticNotificationStreamConfig streamConfig,
                                 final AnalyticNotificationState notificationState,
                                 final RecordConsumer recordConsumer,
                                 final AnalyticDataStore dataStore,
                                 final CurrentDbState currentDbState) {

        SearchRequest searchRequest = dataStore.searchRequest();
        final LmdbDataStore lmdbDataStore = dataStore.lmdbDataStore();
        final QueryKey queryKey = analyticRuleDoc.getQueryKey();
        final SimpleDuration timeToWaitForData = streamConfig.getTimeToWaitForData();
        final Long lastTime = notificationState.getLastExecutionTime();
        final LocalDateTime from;
        if (lastTime == null) {
            // Execute from the beginning of time as this hasn't executed before.
            from = BEGINNING_OF_TIME;
        } else {
            // Get the last time we executed plus one millisecond as this will be the start of the new window.
            from = LocalDateTime.ofInstant(Instant.ofEpochMilli(lastTime), ZoneOffset.UTC).plus(Duration.ofMillis(1));
        }

        // Execute up to most recent data minus the time to wait for data to arrive.
        LocalDateTime to = Optional
                .ofNullable(currentDbState)
                .map(CurrentDbState::getLastEventTime)
                .map(time -> LocalDateTime.ofInstant(Instant.ofEpochMilli(time), ZoneOffset.UTC))
                .orElse(BEGINNING_OF_TIME);
        to = SimpleDurationUtil.minus(to, timeToWaitForData);
        if (to.isAfter(from)) {
            // Create a time filter.
            final TimeFilter timeFilter = new TimeFilter(
                    from.toInstant(ZoneOffset.UTC).toEpochMilli(),
                    to.toInstant(ZoneOffset.UTC).toEpochMilli());

            searchRequest = searchRequest.copy().key(queryKey).incremental(true).build();
            // Perform the search.
            ResultRequest resultRequest = searchRequest.getResultRequests().get(0);
            resultRequest = resultRequest.copy().timeFilter(timeFilter).build();
            final TableResultConsumer tableResultConsumer =
                    new TableResultConsumer(analyticRuleDoc, recordConsumer);

            final FieldFormatter fieldFormatter =
                    new FieldFormatter(new FormatterFactory(null));
            final TableResultCreator resultCreator = new TableResultCreator(
                    fieldFormatter,
                    Sizes.create(Integer.MAX_VALUE));

            // Create result.
            resultCreator.create(lmdbDataStore, resultRequest, tableResultConsumer);

            // Remember last successful execution time.
            final AnalyticNotificationState newState = notificationState.copy()
                    .lastExecutionTime(to.toInstant(ZoneOffset.UTC).toEpochMilli())
                    .build();
            updateNotificationState(newState);
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

    private AnalyticProcessorFilterTracker updateTrackerWithLmdbState(AnalyticProcessorFilterTracker tracker,
                                                                      final CurrentDbState currentDbState) {
        if (currentDbState != null) {
            tracker = tracker.copy()
                    .lastMetaId(currentDbState.getStreamId())
                    .lastEventId(currentDbState.getEventId())
                    .lastEventTime(currentDbState.getLastEventTime())
                    .build();
            analyticProcessorFilterTrackerDao.update(tracker);
            tracker = analyticProcessorFilterTrackerDao.get(tracker.getFilterUuid())
                    .orElseThrow(() -> new RuntimeException("Unable to fetch new state"));
        }
        return tracker;
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

    private AnalyticNotificationState getNotificationState(final AnalyticNotification notification) {
        Optional<AnalyticNotificationState> optionalAnalyticNotificationState =
                analyticNotificationStateDao.get(notification.getUuid());
        while (optionalAnalyticNotificationState.isEmpty()) {
            final AnalyticNotificationState state = AnalyticNotificationState.builder()
                    .notificationUuid(notification.getUuid())
                    .build();
            analyticNotificationStateDao.create(state);
            optionalAnalyticNotificationState = analyticNotificationStateDao.get(notification.getUuid());
        }
        return optionalAnalyticNotificationState.get();
    }

    private AnalyticNotificationState updateNotificationState(AnalyticNotificationState state) {
        analyticNotificationStateDao.update(state);
        state = analyticNotificationStateDao.get(state.getNotificationUuid())
                .orElseThrow(() -> new RuntimeException("Unable to load notification state"));
//        ruleStateCache.put(analyticProcessorFilter.analyticUuid(), analyticProcessorFilter);
        return state;
    }

    private ViewDoc loadViewDoc(final AnalyticRuleDoc analyticRuleDoc,
                                final DocRef viewDocRef) {
        final ViewDoc viewDoc = viewStore.readDocument(viewDocRef);
        if (viewDoc == null) {
            throw new RuntimeException("Unable to process analytic: " +
                    getAnalyticRuleIdentity(analyticRuleDoc) +
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

    private List<Meta> getMetaList(final AnalyticRuleDoc analyticRuleDoc,
                                   final AnalyticProcessorFilter filter,
                                   final Long minMetaId) {
        final ExpressionOperator expressionOperator = filter.getExpression();
        if (ExpressionUtil.termCount(expressionOperator) == 0) {
            throw new RuntimeException("Unable to process analytic: " +
                    getAnalyticRuleIdentity(analyticRuleDoc) +
                    " because filter has no meta selected expression");
        }

        return findMeta(expressionOperator,
                minMetaId,
                filter.getMinMetaCreateTimeMs(),
                filter.getMaxMetaCreateTimeMs(),
                1000);
    }

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

    private static class TableResultConsumer implements TableResultBuilder {

        private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TableResultConsumer.class);

        private final AnalyticRuleDoc analyticRuleDoc;
        private final RecordConsumer recordConsumer;

        private List<Field> fields;

        public TableResultConsumer(final AnalyticRuleDoc analyticRuleDoc,
                                   final RecordConsumer recordConsumer) {
            this.analyticRuleDoc = analyticRuleDoc;
            this.recordConsumer = recordConsumer;
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
                final List<String> values = row.getValues();

                // Match - dump record.
                final List<Data> rows = new ArrayList<>();
                rows.add(new Data(AlertManager.DETECT_TIME_DATA_ELEMENT_NAME_ATTR,
                        DateUtil.createNormalDateTimeString()));
                rows.add(new Data("analyticRuleUuid", analyticRuleDoc.getUuid()));
                rows.add(new Data("analyticRuleName", analyticRuleDoc.getName()));
                for (int i = 0; i < fields.size(); i++) {
                    final String value = values.get(i);
                    if (value != null) {
                        rows.add(new Data(fields.get(i).getName(), value));
                    }
                }
                recordConsumer.accept(new Record(rows));
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
        public TableResultConsumer totalResults(final Integer totalResults) {
            return this;
        }

        @Override
        public TableResult build() {
            return null;
        }
    }
}
