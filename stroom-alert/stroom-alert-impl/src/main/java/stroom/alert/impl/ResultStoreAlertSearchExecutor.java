package stroom.alert.impl;

import stroom.alert.api.AlertManager;
import stroom.alert.impl.RecordConsumer.Data;
import stroom.alert.impl.RecordConsumer.Record;
import stroom.alert.rule.impl.AlertRuleStore;
import stroom.alert.rule.shared.AlertRuleDoc;
import stroom.alert.rule.shared.AlertRuleProcessSettings;
import stroom.alert.rule.shared.AlertRuleType;
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
import stroom.query.common.v2.ErrorConsumer;
import stroom.query.common.v2.ErrorConsumerImpl;
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
import stroom.task.api.TaskContextFactory;
import stroom.util.concurrent.UncheckedInterruptedException;
import stroom.util.date.DateUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.pipeline.scope.PipelineScopeRunnable;
import stroom.util.shared.time.SimpleDuration;
import stroom.util.time.SimpleDurationUtil;
import stroom.view.impl.ViewStore;
import stroom.view.shared.ViewDoc;

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
public class ResultStoreAlertSearchExecutor {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ResultStoreAlertSearchExecutor.class);

    private final AlertRuleSearchRequestHelper alertRuleSearchRequestHelper;
    private final ExecutorProvider executorProvider;
    private final Provider<DetectionsWriter> detectionsWriterProvider;
    private final ViewStore viewStore;
    private final MetaService metaService;
    private final AlertRuleStore alertRuleStore;
    private final SecurityContext securityContext;
    private final PipelineScopeRunnable pipelineScopeRunnable;
    private final PipelineStore pipelineStore;
    private final PipelineDataCache pipelineDataCache;
    private final Provider<AlertStreamProcessor> alertStreamProcessorProvider;
    private final Provider<ExtractionStateHolder> extractionStateHolderProvider;
    private final AggregateRuleValuesConsumerFactory aggregateRuleValuesConsumerFactory;
    private final Provider<AlertWriter2> alertWriterProvider;
    private final TaskContextFactory taskContextFactory;
    private final SearchExpressionQueryBuilderFactory searchExpressionQueryBuilderFactory;

    // TODO : Make persistent with DB.
    private final Map<AlertRuleDoc, LocalDateTime> lastExecutionTimes = new ConcurrentHashMap<>();
    private final Map<AlertRuleDoc, Meta> lastProcessedMeta = new ConcurrentHashMap<>();

    @Inject
    public ResultStoreAlertSearchExecutor(final AlertRuleSearchRequestHelper alertRuleSearchRequestHelper,
                                          final ExecutorProvider executorProvider,
                                          final AlertRuleStore alertRuleStore,
                                          final SecurityContext securityContext,
                                          final Provider<DetectionsWriter> detectionsWriterProvider,
                                          final ViewStore viewStore,
                                          final MetaService metaService,
                                          final PipelineScopeRunnable pipelineScopeRunnable,
                                          final PipelineStore pipelineStore,
                                          final PipelineDataCache pipelineDataCache,
                                          final Provider<AlertStreamProcessor> alertStreamProcessorProvider,
                                          final Provider<ExtractionStateHolder> extractionStateHolderProvider,
                                          final TaskContextFactory taskContextFactory,
                                          final SearchExpressionQueryBuilderFactory searchExpressionQueryBuilderFactory,
                                          final AggregateRuleValuesConsumerFactory aggregateRuleValuesConsumerFactory,
                                          final Provider<AlertWriter2> alertWriterProvider) {
        this.alertRuleSearchRequestHelper = alertRuleSearchRequestHelper;
        this.executorProvider = executorProvider;
        this.detectionsWriterProvider = detectionsWriterProvider;
        this.alertRuleStore = alertRuleStore;
        this.securityContext = securityContext;
        this.viewStore = viewStore;
        this.metaService = metaService;
        this.pipelineScopeRunnable = pipelineScopeRunnable;
        this.pipelineStore = pipelineStore;
        this.pipelineDataCache = pipelineDataCache;
        this.alertStreamProcessorProvider = alertStreamProcessorProvider;
        this.extractionStateHolderProvider = extractionStateHolderProvider;
        this.taskContextFactory = taskContextFactory;
        this.searchExpressionQueryBuilderFactory = searchExpressionQueryBuilderFactory;
        this.aggregateRuleValuesConsumerFactory = aggregateRuleValuesConsumerFactory;
        this.alertWriterProvider = alertWriterProvider;
    }

    public void exec() {
        securityContext.asProcessingUser(() -> {
            // Get views for each alert rule.
            final List<DocRef> docRefList = alertRuleStore.list();
            for (final DocRef docRef : docRefList) {
                try {
                    final AlertRuleDoc alertRuleDoc = alertRuleStore.readDocument(docRef);
                    final SearchRequest searchRequest = alertRuleSearchRequestHelper.create(alertRuleDoc);
                    final DocRef dataSource = searchRequest.getQuery().getDataSource();
                    if (dataSource != null && ViewDoc.DOCUMENT_TYPE.equals(dataSource.getType())) {
                        process(alertRuleDoc, searchRequest, dataSource);
                    } else {
                        LOGGER.error("Rule needs to reference a view");
                    }
                } catch (final RuntimeException e) {
                    LOGGER.error(e::getMessage, e);
                }
            }
        });
    }

    private void process(final AlertRuleDoc alertRuleDoc,
                         final SearchRequest searchRequest,
                         final DocRef dataSource) {
        final AlertRuleProcessSettings processSettings = alertRuleDoc.getProcessSettings();

        if (processSettings != null && processSettings.isEnabled()) {
            final ViewDoc viewDoc = viewStore.readDocument(dataSource);
            if (viewDoc == null) {
                throw new RuntimeException("Unable to process alert '" +
                        alertRuleDoc.getName() +
                        "' (" +
                        alertRuleDoc.getUuid()
                        +
                        ") because selected view cannot be found");
            }

            final ExpressionOperator expressionOperator = viewDoc.getFilter();
            if (ExpressionUtil.termCount(viewDoc.getFilter()) == 0) {
                throw new RuntimeException("Unable to process alert '" +
                        alertRuleDoc.getName() +
                        "' (" +
                        alertRuleDoc.getUuid()
                        +
                        ") because selected view has no meta selected expression");
            }

            final Meta lastMeta = lastProcessedMeta.get(alertRuleDoc);
            final Long lastMetaId = lastMeta == null
                    ? null
                    : lastMeta.getId();
            final List<Meta> metaList = findMeta(expressionOperator,
                    lastMetaId,
                    processSettings.getMinMetaCreateTimeMs(),
                    processSettings.getMaxMetaCreateTimeMs(),
                    1000);

            if (AlertRuleType.EVENT.equals(alertRuleDoc.getAlertRuleType())) {
                processEventAlert(alertRuleDoc, searchRequest, metaList, viewDoc, searchRequest.getKey());

            } else if (AlertRuleType.AGGREGATE.equals(alertRuleDoc.getAlertRuleType())) {
                processAggregateAlert(alertRuleDoc, searchRequest, metaList, viewDoc, searchRequest.getKey());
            }
        }
    }

    private void processEventAlert(final AlertRuleDoc alertRuleDoc,
                                   final SearchRequest searchRequest,
                                   final List<Meta> metaList,
                                   final ViewDoc viewDoc,
                                   final QueryKey queryKey) {
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
                        pipelineScopeRunnable.scopeRunnable(() -> {
                            taskContextFactory.context("Alert Stream Processor", taskContext -> {
                                try {
                                    final DetectionsWriter detectionsWriter = detectionsWriterProvider.get();

                                    final AlertWriter2 alertWriter = alertWriterProvider.get();
                                    alertWriter.setAlertRuleDoc(alertRuleDoc);
                                    alertWriter.setCompiledFields(compiledFields);
                                    alertWriter.setFieldIndex(fieldIndex);
                                    alertWriter.setRecordConsumer(detectionsWriter);

                                    final AlertFieldListConsumer alertFieldListConsumer = new AlertFieldListConsumer(
                                            searchRequest,
                                            fieldIndex,
                                            alertWriter,
                                            searchExpressionQueryCache);

                                    final ExtractionStateHolder extractionStateHolder =
                                            extractionStateHolderProvider.get();
                                    extractionStateHolder.setQueryKey(queryKey);
                                    extractionStateHolder.setFieldListConsumer(alertFieldListConsumer);
                                    extractionStateHolder.setFieldIndex(fieldIndex);

                                    try {
                                        detectionsWriter.start();

                                        final ErrorConsumer errorConsumer = new ErrorConsumerImpl();
                                        alertStreamProcessorProvider.get().extract(
                                                taskContext,
                                                meta.getId(),
                                                extractionPipeline,
                                                errorConsumer,
                                                pipelineData);
                                    } finally {
                                        detectionsWriter.end();
                                    }
                                } catch (final RuntimeException e) {
                                    LOGGER.error(e::getMessage, e);
                                }
                            }).run();
                        });
                        lastProcessedMeta.put(alertRuleDoc, meta);
                    } else {
                        LOGGER.info("Complete for now");
                        break;
                    }
                } catch (final RuntimeException e) {
                    LOGGER.error(e::getMessage, e);
                    break;
                }
            }
        }
    }


    private void processAggregateAlert(final AlertRuleDoc alertRuleDoc,
                                       final SearchRequest searchRequest,
                                       final List<Meta> metaList,
                                       final ViewDoc viewDoc,
                                       final QueryKey queryKey) {
        LocalDateTime mostRecentData = null;
        if (metaList.size() == 0) {
            // There is no new meta to process so assume we are up to date.
            mostRecentData = LocalDateTime.now();

        } else {
            final DocRef extractionPipeline = viewDoc.getPipeline();
            final PipelineData pipelineData = getPipelineData(extractionPipeline);

            // Create coprocessors.
            final LmdbDataStore lmdbDataStore = aggregateRuleValuesConsumerFactory.create(searchRequest);
            // Get the field index.
            final FieldIndex fieldIndex = lmdbDataStore.getFieldIndex();

            // Cache the query for use across multiple streams.
            final SearchExpressionQueryCache searchExpressionQueryCache =
                    new SearchExpressionQueryCache(searchExpressionQueryBuilderFactory, searchRequest);


            for (final Meta meta : metaList) {
                try {
                    if (Status.UNLOCKED.equals(meta.getStatus())) {
                        mostRecentData = LocalDateTime
                                .ofInstant(Instant.ofEpochMilli(meta.getCreateMs()), ZoneOffset.UTC);
                        pipelineScopeRunnable.scopeRunnable(() -> {
                            taskContextFactory.context("Alert Stream Processor", taskContext -> {
                                try {
                                    final AlertFieldListConsumer alertFieldListConsumer = new AlertFieldListConsumer(
                                            searchRequest,
                                            fieldIndex,
                                            lmdbDataStore,
                                            searchExpressionQueryCache);

                                    final ExtractionStateHolder extractionStateHolder =
                                            extractionStateHolderProvider.get();
                                    extractionStateHolder.setQueryKey(queryKey);
                                    extractionStateHolder.setFieldListConsumer(alertFieldListConsumer);
                                    extractionStateHolder.setFieldIndex(fieldIndex);

                                    final ErrorConsumer errorConsumer = new ErrorConsumerImpl();
                                    alertStreamProcessorProvider.get().extract(
                                            taskContext,
                                            meta.getId(),
                                            extractionPipeline,
                                            errorConsumer,
                                            pipelineData);
                                } catch (final RuntimeException e) {
                                    LOGGER.error(e::getMessage, e);
                                }
                            }).run();
                        });
                        lastProcessedMeta.put(alertRuleDoc, meta);
                    } else {
                        LOGGER.info("Complete for now");
                        break;
                    }
                } catch (final RuntimeException e) {
                    LOGGER.error(e::getMessage, e);
                    break;
                }
            }
        }

        // Now run aggregate rule.
        if (mostRecentData != null) {
            runAggregateAlert(alertRuleDoc, mostRecentData);
        }
    }

    private void runAggregateAlert(final AlertRuleDoc alertRuleDoc,
                                   final LocalDateTime mostRecentData) {
        if (AlertRuleType.AGGREGATE.equals(alertRuleDoc.getAlertRuleType())) {
            final DocRef feedDocRef = alertRuleDoc.getDestinationFeed();
            pipelineScopeRunnable.scopeRunnable(() -> {
                final DetectionsWriter detectionsWriter = detectionsWriterProvider.get();
                detectionsWriter.setFeed(feedDocRef);
                detectionsWriter.start();
                try {
                    try {
                        execThresholdAlertRule(alertRuleDoc,
                                alertRuleDoc.getProcessSettings(),
                                detectionsWriter,
                                mostRecentData);
                    } catch (final RuntimeException e) {
                        LOGGER.error(e::getMessage, e);
                    }
                } finally {
                    detectionsWriter.end();
                }
            });
        }
    }

    private void execThresholdAlertRule(final AlertRuleDoc alertRuleDoc,
                                        final AlertRuleProcessSettings processSettings,
                                        final RecordConsumer recordConsumer,
                                        final LocalDateTime mostRecentData) {
        final SimpleDuration executionWindow = processSettings.getExecutionWindow();
        final SimpleDuration timeToWaitForData = processSettings.getTimeToWaitForData();

        final LocalDateTime lastTime = lastExecutionTimes.get(alertRuleDoc);
        if (lastTime == null) {
            // Execute from the beginning of time as this hasn't executed before.
            LocalDateTime from = LocalDateTime.of(1970, 1, 1, 0, 0, 0);

            // Execute up to most recent data minus the time to wait for data to arrive.
            LocalDateTime to = mostRecentData;
            to = SimpleDurationUtil.minus(to, timeToWaitForData);
            to = SimpleDurationUtil.roundDown(to, executionWindow);

            execThresholdAlertRule(alertRuleDoc, from, to, recordConsumer);

        } else {
            // Get the last time we executed and round down to the frequency.
            LocalDateTime from = lastTime;
            from = SimpleDurationUtil.roundDown(from, executionWindow);

            // Add the frequency to the `from` time.
            LocalDateTime maxTo = mostRecentData;
            maxTo = SimpleDurationUtil.minus(maxTo, timeToWaitForData);
            maxTo = SimpleDurationUtil.roundDown(maxTo, executionWindow);

            LocalDateTime to = SimpleDurationUtil.plus(from, executionWindow);
            to = SimpleDurationUtil.roundDown(to, executionWindow);

            if (to.isAfter(maxTo)) {
                to = maxTo;
            }

            // See if it is time to execute again.
            final LocalDateTime now = LocalDateTime.now();
            final LocalDateTime next = SimpleDurationUtil.minus(now, timeToWaitForData);
            if (to.isBefore(next)) {
                execThresholdAlertRule(alertRuleDoc, from, to, recordConsumer);
            }
        }
    }


    private void execThresholdAlertRule(final AlertRuleDoc alertRuleDoc,
                                        final LocalDateTime from,
                                        final LocalDateTime to,
                                        final RecordConsumer recordConsumer) {
        final QueryKey queryKey = alertRuleDoc.getQueryKey();
        final Optional<LmdbDataStore> optionalResultStore = aggregateRuleValuesConsumerFactory.getIfPresent(queryKey);
        if (optionalResultStore.isPresent()) {
            final LmdbDataStore lmdbDataStore = optionalResultStore.get();


            // TODO : TEMPORARY COMPLETION - NEEDS SYNC BLOCK
            try {
                lmdbDataStore.getCompletionState().signalComplete();
                lmdbDataStore.getCompletionState().awaitCompletion();
            } catch (final InterruptedException e) {
                throw UncheckedInterruptedException.create(e);
            }

            // Create a search request.
            SearchRequest searchRequest = alertRuleSearchRequestHelper.create(alertRuleDoc);
            // Create a time filter.
            final TimeFilter timeFilter = new TimeFilter(
                    from.toInstant(ZoneOffset.UTC).toEpochMilli(),
                    to.toInstant(ZoneOffset.UTC).toEpochMilli());

            searchRequest = searchRequest.copy().key(queryKey).incremental(true).build();
            // Perform the search.
            ResultRequest resultRequest = searchRequest.getResultRequests().get(0);
            resultRequest = resultRequest.copy().timeFilter(timeFilter).build();
            final TableResultConsumer tableResultConsumer =
                    new TableResultConsumer(alertRuleDoc, recordConsumer);

            final FieldFormatter fieldFormatter =
                    new FieldFormatter(new FormatterFactory(null));
            final TableResultCreator resultCreator = new TableResultCreator(
                    fieldFormatter,
                    Sizes.create(Integer.MAX_VALUE));

            // Create result.
            resultCreator.create(lmdbDataStore, resultRequest, tableResultConsumer);

            // Remember last successful execution.
            lastExecutionTimes.put(alertRuleDoc, to);
        } else {
            LOGGER.info(() -> LogUtil.message("No result store found to try alert query: {}",
                    alertRuleDoc.getUuid()));
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

    private List<Meta> findMeta(final ExpressionOperator expression,
                                final Long lastMetaId,
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
        if (lastMetaId != null) {
            builder = builder.addTerm(MetaFields.ID, Condition.GREATER_THAN, lastMetaId);
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

        private final AlertRuleDoc alertRuleDoc;
        private final RecordConsumer recordConsumer;

        private List<Field> fields;

        public TableResultConsumer(final AlertRuleDoc alertRuleDoc,
                                   final RecordConsumer recordConsumer) {
            this.alertRuleDoc = alertRuleDoc;
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
                rows.add(new Data("alertRuleUuid", alertRuleDoc.getUuid()));
                rows.add(new Data("alertRuleName", alertRuleDoc.getName()));
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
