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
import stroom.index.shared.IndexConstants;
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

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
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
    private final Provider<DetectionWriterProxy> alertWriterProvider;
    private final TaskContextFactory taskContextFactory;
    private final SearchExpressionQueryBuilderFactory searchExpressionQueryBuilderFactory;
    private final AnalyticProcessorFilterDao analyticProcessorFilterDao;
    private final AnalyticProcessorFilterTrackerDao analyticProcessorFilterTrackerDao;
    private final AnalyticNotificationDao analyticNotificationDao;
    private final AnalyticNotificationStateDao analyticNotificationStateDao;
    private final NodeInfo nodeInfo;

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
                             final Provider<ExtractionStateHolder> extractionStateHolderProvider,
                             final TaskContextFactory taskContextFactory,
                             final SearchExpressionQueryBuilderFactory searchExpressionQueryBuilderFactory,
                             final AnalyticDataStores analyticDataStores,
                             final Provider<DetectionWriterProxy> alertWriterProvider,
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

    private void info(final Supplier<String> messageSupplier) {
        LOGGER.info(messageSupplier);
        taskContextFactory.current().info(messageSupplier);
    }

    public void exec() {
        final LogExecutionTime logExecutionTime = new LogExecutionTime();
        try {
            info(() -> "Starting analytic processing");
            processAll();
            info(() -> LogUtil.message("Finished analytic processing in {}", logExecutionTime));
        } catch (final TaskTerminatedException | UncheckedInterruptedException e) {
            LOGGER.debug("Task terminated", e);
            LOGGER.debug(() -> LogUtil.message("Analytic processing terminated after {}", logExecutionTime));
        } catch (final RuntimeException e) {
            LOGGER.error(() -> LogUtil.message("Error during analytic processing: {}", e.getMessage()), e);
        }
    }

    private void processAll() {
        securityContext.asProcessingUser(() -> {
            // Delete old data stores.
            deleteOldStores();

            // Get views for each analytic rule.
            final List<DocRef> docRefList = analyticRuleStore.list();
            for (final DocRef docRef : docRefList) {
                final AnalyticRuleDoc analyticRuleDoc = analyticRuleStore.readDocument(docRef);
                if (analyticRuleDoc != null) {
                    processAnalytic(analyticRuleDoc);
                }
            }
        });
    }

    private void deleteOldStores() {
        final LogExecutionTime logExecutionTime = new LogExecutionTime();
        info(() -> "Deleting old stores");
        analyticDataStores.deleteOldStores();
        info(() -> LogUtil.message("Deleted old stores in {}", logExecutionTime));
    }

    private void processAnalytic(final AnalyticRuleDoc analyticRuleDoc) {
        final String ruleIdentity = getAnalyticRuleIdentity(analyticRuleDoc);
        taskContextFactory
                .context("Analytic: " + ruleIdentity,
                        parentTaskContext -> processAnalytic(analyticRuleDoc, ruleIdentity, parentTaskContext))
                .run();
    }

    private void processAnalytic(final AnalyticRuleDoc analyticRuleDoc,
                                 final String ruleIdentity,
                                 final TaskContext parentTaskContext) {
        final Optional<AnalyticProcessorFilter> optionalFilter =
                analyticProcessorFilterDao.getByAnalyticUuid(analyticRuleDoc.getUuid());
        optionalFilter.ifPresent(filter -> {
            if (filter.isEnabled() && nodeInfo.getThisNodeName().equals(filter.getNode())) {
                processAnalytic(analyticRuleDoc, ruleIdentity, parentTaskContext, filter);
            }
        });
    }

    private void processAnalytic(final AnalyticRuleDoc analyticRuleDoc,
                                 final String ruleIdentity,
                                 final TaskContext parentTaskContext,
                                 final AnalyticProcessorFilter filter) {
        final LogExecutionTime logExecutionTime = new LogExecutionTime();
        final AnalyticProcessorFilterTracker tracker = getFilterTracker(filter);
        final AnalyticProcessorFilterTracker.Builder trackerBuilder = tracker.copy();
        try {
            info(() -> LogUtil.message("Starting analytic processing for: {}",
                    ruleIdentity));
            if (AnalyticRuleType.EVENT.equals(analyticRuleDoc.getAnalyticRuleType())) {
                processEventAnalytic(
                        analyticRuleDoc,
                        ruleIdentity,
                        filter,
                        trackerBuilder,
                        parentTaskContext);
            } else if (AnalyticRuleType.AGGREGATE.equals(analyticRuleDoc.getAnalyticRuleType())) {
                processAggregateAnalytic(
                        analyticRuleDoc,
                        ruleIdentity,
                        filter,
                        trackerBuilder,
                        parentTaskContext);
            }
            info(() -> LogUtil.message("Finished analytic processing for: {} in {}",
                    ruleIdentity,
                    logExecutionTime));
        } catch (final TaskTerminatedException | UncheckedInterruptedException e) {
            LOGGER.debug(() -> "Task terminated", e);
            LOGGER.debug(() -> LogUtil.message("Analytic processing for: {} terminated after {}",
                    ruleIdentity,
                    logExecutionTime));
        } catch (final RuntimeException e) {
            LOGGER.error(() -> LogUtil.message("Error during analytic processing for: {} {}",
                            ruleIdentity,
                            e.getMessage()),
                    e);
            disableFilter(ruleIdentity, filter, trackerBuilder, e.getMessage());
        } finally {
            // Update tracker.
            analyticProcessorFilterTrackerDao.update(trackerBuilder.build());
        }
    }

    private void disableFilter(final String ruleIdentity,
                               final AnalyticProcessorFilter filter,
                               final AnalyticProcessorFilterTracker.Builder trackerBuilder,
                               final String message) {
        LOGGER.info("Disabling processing for: {}", ruleIdentity);
        trackerBuilder.message(message);

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

    private void disableNotification(final String ruleIdentity,
                                     final AnalyticNotification notification,
                                     final AnalyticNotificationState state,
                                     final String message) {
        LOGGER.info("Disabling notification {} for: {}", notification.getUuid(), ruleIdentity);

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
                                      final String ruleIdentity,
                                      final AnalyticProcessorFilter filter,
                                      final AnalyticProcessorFilterTracker.Builder trackerBuilder,
                                      final TaskContext parentTaskContext) {
        final List<AnalyticNotification> notifications =
                analyticNotificationDao.getByAnalyticUuid(analyticRuleDoc.getUuid());
        for (final AnalyticNotification notification : notifications) {
            if (notification.isEnabled()) {
                final AnalyticNotificationState analyticNotificationState = getNotificationState(notification);
                final AnalyticNotificationConfig config = notification.getConfig();
                if (config instanceof final AnalyticNotificationStreamConfig streamConfig) {
                    try {
                        processEventAnalytic(
                                analyticRuleDoc,
                                ruleIdentity,
                                notification,
                                streamConfig,
                                parentTaskContext,
                                filter,
                                trackerBuilder,
                                analyticNotificationState);
                    } catch (final TaskTerminatedException | UncheckedInterruptedException e) {
                        LOGGER.debug(e::getMessage, e);
                        throw e;
                    } catch (final RuntimeException e) {
                        LOGGER.error(e::getMessage, e);
                        disableNotification(ruleIdentity, notification, analyticNotificationState, e.getMessage());
                    }
                }
            }
        }
    }

    private void processEventAnalytic(final AnalyticRuleDoc analyticRuleDoc,
                                      final String ruleIdentity,
                                      final AnalyticNotification notification,
                                      final AnalyticNotificationStreamConfig streamConfig,
                                      final TaskContext parentTaskContext,
                                      final AnalyticProcessorFilter filter,
                                      final AnalyticProcessorFilterTracker.Builder trackerBuilder,
                                      AnalyticNotificationState analyticNotificationState) {
        final SearchRequest searchRequest = analyticRuleSearchRequestHelper.create(analyticRuleDoc);
        final DocRef dataSource = searchRequest.getQuery().getDataSource();
        if (dataSource == null || !ViewDoc.DOCUMENT_TYPE.equals(dataSource.getType())) {
            trackerBuilder.message("Error: Rule needs to reference a view").build();

        } else {
            final String analyticUuid = analyticRuleDoc.getUuid();

            // Load view.
            final ViewDoc viewDoc = loadViewDoc(ruleIdentity, dataSource);

            // Get a meta list to process.
            final AnalyticProcessorFilterTracker tracker = trackerBuilder.build();
            Long minMetaId = null;
            if (tracker.getLastMetaId() != null) {
                // Start at the next meta.
                minMetaId = tracker.getLastMetaId() + 1;
            }

            final List<Meta> metaList = getMetaList(
                    ruleIdentity,
                    filter,
                    minMetaId);

            trackerBuilder.lastPollMs(System.currentTimeMillis());
            trackerBuilder.lastPollTaskCount(metaList.size());

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

                                        final DetectionWriterProxy alertWriter = alertWriterProvider.get();
                                        alertWriter.setAnalyticRuleDoc(analyticRuleDoc);
                                        alertWriter.setCompiledFields(compiledFields);
                                        alertWriter.setFieldIndex(fieldIndex);
                                        alertWriter.setDetectionConsumer(detectionsWriter);

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

                                        trackerBuilder.lastEventId(analyticFieldListConsumer.getEventId());
                                        trackerBuilder.addEventCount(analyticFieldListConsumer.getEventId());
                                    });

                            // Update analytic rule state.
                            trackerBuilder.lastMetaId(meta.getId());
                            trackerBuilder.incrementMetaCount();

                        } else {
                            LOGGER.info("Complete for now");
                            trackerBuilder.message("Complete for now").build();
                            break;
                        }
                    }
                } catch (final TaskTerminatedException | UncheckedInterruptedException e) {
                    LOGGER.debug(e::getMessage, e);
                    throw e;
                } catch (final RuntimeException e) {
                    LOGGER.error(e::getMessage, e);
                    disableFilter(ruleIdentity, filter, trackerBuilder, e.getMessage());
                }
            }
        }

        analyticNotificationState = analyticNotificationState
                .copy()
                .lastExecutionTime(System.currentTimeMillis())
                .build();
        analyticNotificationStateDao.update(analyticNotificationState);
    }

    private void processAggregateAnalytic(final AnalyticRuleDoc analyticRuleDoc,
                                          final String ruleIdentity,
                                          final AnalyticProcessorFilter filter,
                                          final AnalyticProcessorFilterTracker.Builder trackerBuilder,
                                          final TaskContext parentTaskContext) {
        final long startTimeMs = System.currentTimeMillis();
        final AnalyticDataStore dataStore = analyticDataStores.get(analyticRuleDoc);
        final SearchRequest searchRequest = dataStore.searchRequest();
        final DocRef dataSource = searchRequest.getQuery().getDataSource();

        // Get or create LMDB data store.
        final LmdbDataStore lmdbDataStore = dataStore.lmdbDataStore();
        CurrentDbState currentDbState = lmdbDataStore.sync();

        // Establish the analytic tracker state.
        final AnalyticProcessorFilterTracker tracker = updateTrackerWithLmdbState(trackerBuilder, currentDbState);

        // Load view.
        final ViewDoc viewDoc = loadViewDoc(ruleIdentity, dataSource);

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
                ruleIdentity,
                filter,
                minMetaId);

        trackerBuilder.lastPollMs(System.currentTimeMillis());
        trackerBuilder.lastPollTaskCount(metaList.size());

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

                                    trackerBuilder.incrementMetaCount();
                                    trackerBuilder.addEventCount(analyticFieldListConsumer.getEventId());
                                });
                        metaId = meta.getId();

                    } else {
                        LOGGER.info("Complete for now");
                        trackerBuilder.message("Complete for now");
                        break;
                    }
                } catch (final TaskTerminatedException | UncheckedInterruptedException e) {
                    LOGGER.debug(e::getMessage, e);
                    throw e;
                } catch (final RuntimeException e) {
                    LOGGER.error(e::getMessage, e);
                    trackerBuilder.message(e.getMessage());
                    throw e;
                }
            }

            // Now we've added data synchronise LMDB and record state.
            if (metaId != null) {
                currentDbState = lmdbDataStore.sync();

                // Remember meta load state.
                updateTrackerWithLmdbState(trackerBuilder, currentDbState);
            }
        }

        // Determine if we have processed all of the current data.
        final boolean uptoDate = allowUpToDateProcessing && metaList.size() < maxMetaListSize;

        // Now execute notifications.
        executeNotifications(
                analyticRuleDoc,
                ruleIdentity,
                dataStore,
                currentDbState,
                parentTaskContext,
                uptoDate,
                startTimeMs);

        // Delete old data from the DB.
        applyDataRetentionRules(lmdbDataStore, analyticRuleDoc);
    }

    private void executeNotifications(final AnalyticRuleDoc analyticRuleDoc,
                                      final String ruleIdentity,
                                      final AnalyticDataStore dataStore,
                                      final CurrentDbState currentDbState,
                                      final TaskContext parentTaskContext,
                                      final boolean upToDate,
                                      final long startTimeMs) {
        final List<AnalyticNotification> notifications =
                analyticNotificationDao.getByAnalyticUuid(analyticRuleDoc.getUuid());
        for (final AnalyticNotification notification : notifications) {
            if (notification.isEnabled()) {
                final AnalyticNotificationState notificationState = getNotificationState(notification);
                try {
                    executeNotification(
                            analyticRuleDoc,
                            ruleIdentity,
                            notification,
                            notificationState,
                            dataStore,
                            currentDbState,
                            parentTaskContext,
                            upToDate,
                            startTimeMs);
                } catch (final TaskTerminatedException | UncheckedInterruptedException e) {
                    LOGGER.debug(e::getMessage, e);
                    throw e;
                } catch (final RuntimeException e) {
                    LOGGER.error(e::getMessage, e);
                    disableNotification(ruleIdentity, notification, notificationState, e.getMessage());
                }
            }
        }
    }

    private void executeNotification(final AnalyticRuleDoc analyticRuleDoc,
                                     final String ruleIdentity,
                                     final AnalyticNotification notification,
                                     final AnalyticNotificationState notificationState,
                                     final AnalyticDataStore dataStore,
                                     final CurrentDbState currentDbState,
                                     final TaskContext parentTaskContext,
                                     final boolean upToDate,
                                     final long startTimeMs) {
        final AnalyticNotificationConfig config = notification.getConfig();
        if (config instanceof final AnalyticNotificationStreamConfig streamConfig) {
            final DocRef feedDocRef = streamConfig.getDestinationFeed();
            if (feedDocRef == null) {
                throw new RuntimeException("Destination feed not specified for notification in: " +
                        ruleIdentity);
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
                                        currentDbState,
                                        upToDate,
                                        startTimeMs);
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
                    });

        }

    }

    private void runNotification(final AnalyticRuleDoc analyticRuleDoc,
                                 final AnalyticNotification notification,
                                 final AnalyticNotificationStreamConfig streamConfig,
                                 final AnalyticNotificationState notificationState,
                                 final DetectionConsumer detectionConsumer,
                                 final AnalyticDataStore dataStore,
                                 final CurrentDbState currentDbState,
                                 final boolean upToDate,
                                 final long startTimeMs) {

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
        LocalDateTime to;

        // If the current processing is up-to-date then use the processing start time as the basis for the window end.
        if (upToDate) {
            to = LocalDateTime.ofInstant(Instant.ofEpochMilli(startTimeMs), ZoneOffset.UTC);
        } else {
            to = Optional
                    .ofNullable(currentDbState)
                    .map(CurrentDbState::getLastEventTime)
                    .map(time -> LocalDateTime.ofInstant(Instant.ofEpochMilli(time), ZoneOffset.UTC))
                    .orElse(BEGINNING_OF_TIME);
        }

        // Subtract the waiting period from the window end.
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
                    new TableResultConsumer(analyticRuleDoc, detectionConsumer);

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

    private AnalyticProcessorFilterTracker updateTrackerWithLmdbState(final AnalyticProcessorFilterTracker.Builder
                                                                              trackerBuilder,
                                                                      final CurrentDbState currentDbState) {
        if (currentDbState != null) {
            trackerBuilder
                    .lastMetaId(currentDbState.getStreamId())
                    .lastEventId(currentDbState.getEventId())
                    .lastEventTime(currentDbState.getLastEventTime());
        }
        return trackerBuilder.build();
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

//    private AnalyticProcessorFilterTracker updateFilterTracker(AnalyticProcessorFilterTracker tracker) {
//        analyticProcessorFilterTrackerDao.update(tracker);
//        tracker = analyticProcessorFilterTrackerDao.get(tracker.getFilterUuid())
//                .orElseThrow(() -> new RuntimeException("Unable to load tracker"));
////        ruleStateCache.put(analyticProcessorFilter.analyticUuid(), analyticProcessorFilter);
//        return tracker;
//    }

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

    private List<Meta> getMetaList(final String ruleIdentity,
                                   final AnalyticProcessorFilter filter,
                                   final Long minMetaId) {
        final ExpressionOperator expressionOperator = filter.getExpression();
        if (ExpressionUtil.termCount(expressionOperator) == 0) {
            throw new RuntimeException("Unable to process analytic: " +
                    ruleIdentity +
                    " because filter has no meta expression");
        }

        return findMeta(expressionOperator,
                minMetaId,
                filter.getMinMetaCreateTimeMs(),
                filter.getMaxMetaCreateTimeMs(),
                maxMetaListSize);
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

    public void setAllowUpToDateProcessing(final boolean allowUpToDateProcessing) {
        this.allowUpToDateProcessing = allowUpToDateProcessing;
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
