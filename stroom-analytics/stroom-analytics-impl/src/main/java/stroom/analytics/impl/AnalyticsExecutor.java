package stroom.analytics.impl;

import stroom.analytics.api.AlertManager;
import stroom.analytics.impl.RecordConsumer.Data;
import stroom.analytics.impl.RecordConsumer.Record;
import stroom.analytics.rule.impl.AnalyticRuleStore;
import stroom.analytics.shared.AnalyticRuleDoc;
import stroom.analytics.shared.AnalyticRuleProcessSettings;
import stroom.analytics.shared.AnalyticRuleType;
import stroom.dashboard.expression.v1.FieldIndex;
import stroom.docref.DocRef;
import stroom.meta.api.MetaService;
import stroom.meta.shared.FindMetaCriteria;
import stroom.meta.shared.Meta;
import stroom.meta.shared.MetaFields;
import stroom.meta.shared.Status;
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
import stroom.query.common.v2.CompiledField;
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
import stroom.util.pipeline.scope.PipelineScopeRunnable;
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
import java.util.concurrent.ConcurrentHashMap;
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
    private final PipelineScopeRunnable pipelineScopeRunnable;
    private final PipelineStore pipelineStore;
    private final PipelineDataCache pipelineDataCache;
    private final Provider<AnalyticsStreamProcessor> analyticsStreamProcessorProvider;
    private final Provider<ExtractionStateHolder> extractionStateHolderProvider;
    private final AggregateRuleValuesConsumerFactory aggregateRuleValuesConsumerFactory;
    private final Provider<AlertWriter2> alertWriterProvider;
    private final TaskContextFactory taskContextFactory;
    private final SearchExpressionQueryBuilderFactory searchExpressionQueryBuilderFactory;
    private final AnalyticRuleStateDao analyticRuleStateDao;

    private final Map<String, AnalyticRuleState> ruleStateCache = new ConcurrentHashMap<>();


    private final AnalyticErrorWritingExecutor analyticErrorWritingExecutor;

    @Inject
    public AnalyticsExecutor(final AnalyticRuleSearchRequestHelper analyticRuleSearchRequestHelper,
                             final ExecutorProvider executorProvider,
                             final AnalyticRuleStore analyticRuleStore,
                             final SecurityContext securityContext,
                             final Provider<DetectionsWriter> detectionsWriterProvider,
                             final ViewStore viewStore,
                             final MetaService metaService,
                             final PipelineScopeRunnable pipelineScopeRunnable,
                             final PipelineStore pipelineStore,
                             final PipelineDataCache pipelineDataCache,
                             final Provider<AnalyticsStreamProcessor> analyticsStreamProcessorProvider,
                             final Provider<ExtractionStateHolder> extractionStateHolderProvider,
                             final TaskContextFactory taskContextFactory,
                             final SearchExpressionQueryBuilderFactory searchExpressionQueryBuilderFactory,
                             final AggregateRuleValuesConsumerFactory aggregateRuleValuesConsumerFactory,
                             final Provider<AlertWriter2> alertWriterProvider,
                             final AnalyticRuleStateDao analyticRuleStateDao,
                             final AnalyticErrorWritingExecutor analyticErrorWritingExecutor) {
        this.analyticRuleSearchRequestHelper = analyticRuleSearchRequestHelper;
        this.executorProvider = executorProvider;
        this.detectionsWriterProvider = detectionsWriterProvider;
        this.analyticRuleStore = analyticRuleStore;
        this.securityContext = securityContext;
        this.viewStore = viewStore;
        this.metaService = metaService;
        this.pipelineScopeRunnable = pipelineScopeRunnable;
        this.pipelineStore = pipelineStore;
        this.pipelineDataCache = pipelineDataCache;
        this.analyticsStreamProcessorProvider = analyticsStreamProcessorProvider;
        this.extractionStateHolderProvider = extractionStateHolderProvider;
        this.taskContextFactory = taskContextFactory;
        this.searchExpressionQueryBuilderFactory = searchExpressionQueryBuilderFactory;
        this.aggregateRuleValuesConsumerFactory = aggregateRuleValuesConsumerFactory;
        this.alertWriterProvider = alertWriterProvider;
        this.analyticRuleStateDao = analyticRuleStateDao;
        this.analyticErrorWritingExecutor = analyticErrorWritingExecutor;
    }

    public void exec() {
        securityContext.asProcessingUser(() -> {
            // Get views for each analytic rule.
            final List<DocRef> docRefList = analyticRuleStore.list();
            for (final DocRef docRef : docRefList) {
                try {
                    final AnalyticRuleDoc analyticRuleDoc = analyticRuleStore.readDocument(docRef);
                    final SearchRequest searchRequest = analyticRuleSearchRequestHelper.create(analyticRuleDoc);
                    final DocRef dataSource = searchRequest.getQuery().getDataSource();
                    if (dataSource != null && ViewDoc.DOCUMENT_TYPE.equals(dataSource.getType())) {
                        process(analyticRuleDoc, searchRequest, dataSource);
                    } else {
                        LOGGER.error("Rule needs to reference a view");
                    }
                } catch (final RuntimeException e) {
                    LOGGER.error(e::getMessage, e);
                }
            }
        });
    }

    private void process(final AnalyticRuleDoc analyticRuleDoc,
                         final SearchRequest searchRequest,
                         final DocRef dataSource) {
        taskContextFactory.context(
                "Analytic: " + analyticRuleDoc.getName() + " (" + analyticRuleDoc.getUuid() + ")",
                parentTaskContext -> {
                    final AnalyticRuleProcessSettings processSettings = analyticRuleDoc.getProcessSettings();
                    if (processSettings != null && processSettings.isEnabled()) {
                        try {
                            if (AnalyticRuleType.EVENT.equals(analyticRuleDoc.getAnalyticRuleType())) {
                                processEventAnalytic(
                                        analyticRuleDoc,
                                        searchRequest,
                                        dataSource,
                                        searchRequest.getKey(),
                                        parentTaskContext);

                            } else if (AnalyticRuleType.AGGREGATE.equals(analyticRuleDoc.getAnalyticRuleType())) {
                                processAggregateAnalytic(
                                        analyticRuleDoc,
                                        searchRequest,
                                        dataSource,
                                        searchRequest.getKey(),
                                        parentTaskContext);
                            }
                        } catch (final RuntimeException e) {
                            LOGGER.error(e::getMessage, e);
                            disableRule(analyticRuleDoc);
                        }
                    }
                }).run();
    }

    private void disableRule(final AnalyticRuleDoc analyticRuleDoc) {
        LOGGER.info("Disabling: " + analyticRuleDoc.getName());
        final AnalyticRuleDoc disabledAnalyticRuleDoc = analyticRuleDoc
                .copy()
                .processSettings(
                        analyticRuleDoc.getProcessSettings()
                                .copy()
                                .enabled(false)
                                .build())
                .build();
        analyticRuleStore.writeDocument(disabledAnalyticRuleDoc);
    }

    private void processEventAnalytic(final AnalyticRuleDoc analyticRuleDoc,
                                      final SearchRequest searchRequest,
                                      final DocRef dataSource,
                                      final QueryKey queryKey,
                                      final TaskContext parentTaskContext) {
        // Establish the analytic rule state.
        final String analyticUuid = analyticRuleDoc.getUuid();
        AnalyticRuleState analyticRuleState = getAnalyticRuleState(analyticUuid, null);

        // Load view.
        final ViewDoc viewDoc = loadViewDoc(analyticRuleDoc, dataSource);

        // Get a meta list to process.
        Long minMetaId = null;
        if (analyticRuleState.lastMetaId() != null) {
            // Start at the next meta.
            minMetaId = analyticRuleState.lastMetaId() + 1;
        }

        final List<Meta> metaList = getMetaList(
                analyticRuleDoc,
                viewDoc,
                minMetaId);

        if (metaList.size() > 0) {
            final DocRef extractionPipeline = viewDoc.getPipeline();
            final PipelineData pipelineData = getPipelineData(extractionPipeline);

            // Create field index.
            final FieldIndex fieldIndex = new FieldIndex();
            final TableSettings tableSettings = searchRequest.getResultRequests().get(0).getMappings().get(0);
            final Map<String, String> paramMap = ParamUtil.createParamMap(searchRequest.getQuery().getParams());
            final CompiledField[] compiledFields =
                    CompiledFields.create(tableSettings.getFields(), fieldIndex, paramMap);

            // Cache the query for use across multiple streams.
            final SearchExpressionQueryCache searchExpressionQueryCache =
                    new SearchExpressionQueryCache(searchExpressionQueryBuilderFactory, searchRequest);

            for (final Meta meta : metaList) {
                try {
                    if (Status.UNLOCKED.equals(meta.getStatus())) {
                        analyticErrorWritingExecutor.exec(
                                "Analytics Stream Processor",
                                meta.getFeedName(),
                                analyticUuid,
                                extractionPipeline.getUuid(),
                                parentTaskContext,
                                taskContext -> {
                                    final DetectionsWriter detectionsWriter = detectionsWriterProvider.get();

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
                                    extractionStateHolder.setQueryKey(queryKey);
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
                        analyticRuleState = analyticRuleState.copy().lastMetaId(meta.getId()).build();
                        analyticRuleState = updateAnalyticRuleState(analyticRuleState);

                    } else {
                        LOGGER.info("Complete for now");
                        break;
                    }
                } catch (final RuntimeException e) {
                    LOGGER.error(e::getMessage, e);
                    throw e;
                }
            }
        }
    }

    private void processAggregateAnalytic(final AnalyticRuleDoc analyticRuleDoc,
                                          final SearchRequest searchRequest,
                                          final DocRef dataSource,
                                          final QueryKey queryKey,
                                          final TaskContext parentTaskContext) {
        // Get or create LMDB data store.
        final LmdbDataStore lmdbDataStore = aggregateRuleValuesConsumerFactory.create(searchRequest);
        CurrentDbState currentDbState = lmdbDataStore.sync();

        // Establish the analytic rule state.
        final String analyticUUid = analyticRuleDoc.getUuid();
        AnalyticRuleState analyticRuleState = getAnalyticRuleState(analyticUUid, currentDbState);

        // Load view.
        final ViewDoc viewDoc = loadViewDoc(analyticRuleDoc, dataSource);

        // Get a meta list to process.
        final Long lastMetaId = analyticRuleState.lastMetaId();
        final Long lastEventId = analyticRuleState.lastEventId();
        final Long minMetaId;
        if (lastMetaId != null && lastEventId == null) {
            // Start at the next meta.
            minMetaId = lastMetaId + 1;
        } else {
            minMetaId = lastMetaId;
        }

        final List<Meta> metaList = getMetaList(
                analyticRuleDoc,
                viewDoc,
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
                                    extractionStateHolder.setQueryKey(queryKey);
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
                final AnalyticRuleState newState = analyticRuleState.copy()
                        .lastMetaId(metaId)
                        .lastEventId(null)
                        .lastEventTime(null)
                        .build();
                analyticRuleState = updateAnalyticRuleState(newState);
            }
        }

        // Now run aggregate rule.
        runAggregateAnalyticRule(analyticRuleDoc, lmdbDataStore, currentDbState, analyticRuleState, parentTaskContext);
    }

    private void runAggregateAnalyticRule(final AnalyticRuleDoc analyticRuleDoc,
                                          final LmdbDataStore lmdbDataStore,
                                          final CurrentDbState currentDbState,
                                          final AnalyticRuleState analyticRuleState,
                                          final TaskContext parentTaskContext) {
        final DocRef feedDocRef = analyticRuleDoc.getDestinationFeed();

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
                            execThresholdAnalyticRule(analyticRuleDoc,
                                    analyticRuleDoc.getProcessSettings(),
                                    detectionsWriter,
                                    lmdbDataStore,
                                    currentDbState,
                                    analyticRuleState);
                        } catch (final RuntimeException e) {
                            LOGGER.error(e::getMessage, e);
                            throw e;
                        }
                    } finally {
                        detectionsWriter.end();
                    }
                });
    }

    private void execThresholdAnalyticRule(final AnalyticRuleDoc analyticRuleDoc,
                                           final AnalyticRuleProcessSettings processSettings,
                                           final RecordConsumer recordConsumer,
                                           final LmdbDataStore lmdbDataStore,
                                           final CurrentDbState currentDbState,
                                           final AnalyticRuleState analyticRuleState) {
        final QueryKey queryKey = analyticRuleDoc.getQueryKey();
        final SimpleDuration timeToWaitForData = processSettings.getTimeToWaitForData();
        final Long lastTime = analyticRuleState.lastExecutionTime();
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
            // Create a search request.
            SearchRequest searchRequest = analyticRuleSearchRequestHelper.create(analyticRuleDoc);
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
            final AnalyticRuleState newState = analyticRuleState.copy()
                    .lastExecutionTime(to.toInstant(ZoneOffset.UTC).toEpochMilli())
                    .build();
            updateAnalyticRuleState(newState);

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

    private AnalyticRuleState getAnalyticRuleState(final String analyticUuid, final CurrentDbState currentDbState) {
        AnalyticRuleState analyticRuleState = ruleStateCache.get(analyticUuid);
        if (analyticRuleState == null) {
            final Optional<AnalyticRuleState> optionalAnalyticRuleState = analyticRuleStateDao.getState(analyticUuid);
            if (optionalAnalyticRuleState.isEmpty()) {
                final long now = System.currentTimeMillis();
                final String userId = securityContext.getUserId();
                final AnalyticRuleState newState = AnalyticRuleState.builder()
                        .version(1)
                        .createTime(now)
                        .createUser(userId)
                        .updateTime(now)
                        .updateUser(userId)
                        .analyticUuid(analyticUuid)
                        .build();
                analyticRuleStateDao.createState(newState);
                analyticRuleState = analyticRuleStateDao.getState(analyticUuid)
                        .orElseThrow(() -> new RuntimeException("Unable to fetch new state"));
            } else {
                analyticRuleState = optionalAnalyticRuleState.get();
                if (currentDbState != null) {
                    final AnalyticRuleState newState = analyticRuleState.copy()
                            .lastMetaId(currentDbState.getStreamId())
                            .lastEventId(currentDbState.getEventId())
                            .lastEventTime(currentDbState.getLastEventTime())
                            .build();
                    analyticRuleStateDao.updateState(newState);
                    analyticRuleState = analyticRuleStateDao.getState(analyticUuid)
                            .orElseThrow(() -> new RuntimeException("Unable to fetch new state"));
                }

            }
        }
        ruleStateCache.put(analyticUuid, analyticRuleState);
        return analyticRuleState;
    }

    private AnalyticRuleState updateAnalyticRuleState(AnalyticRuleState analyticRuleState) {
        analyticRuleStateDao.updateState(analyticRuleState);
        analyticRuleState = analyticRuleStateDao.getState(analyticRuleState.analyticUuid())
                .orElseThrow(() -> new RuntimeException("Unable to load state"));
        ruleStateCache.put(analyticRuleState.analyticUuid(), analyticRuleState);
        return analyticRuleState;
    }

    private ViewDoc loadViewDoc(final AnalyticRuleDoc analyticRuleDoc,
                                final DocRef viewDocRef) {
        final ViewDoc viewDoc = viewStore.readDocument(viewDocRef);
        if (viewDoc == null) {
            throw new RuntimeException("Unable to process analytic '" +
                    analyticRuleDoc.getName() +
                    "' (" +
                    analyticRuleDoc.getUuid()
                    +
                    ") because selected view cannot be found");
        }
        return viewDoc;
    }

    private List<Meta> getMetaList(final AnalyticRuleDoc analyticRuleDoc,
                                   final ViewDoc viewDoc,
                                   final Long minMetaId) {
        final AnalyticRuleProcessSettings processSettings = analyticRuleDoc.getProcessSettings();
        final ExpressionOperator expressionOperator = viewDoc.getFilter();
        if (ExpressionUtil.termCount(viewDoc.getFilter()) == 0) {
            throw new RuntimeException("Unable to process analytic '" +
                    analyticRuleDoc.getName() +
                    "' (" +
                    analyticRuleDoc.getUuid()
                    +
                    ") because selected view has no meta selected expression");
        }

        return findMeta(expressionOperator,
                minMetaId,
                processSettings.getMinMetaCreateTimeMs(),
                processSettings.getMaxMetaCreateTimeMs(),
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
