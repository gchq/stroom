package stroom.alert.impl;

import stroom.alert.api.AlertManager;
import stroom.alert.impl.RecordConsumer.Data;
import stroom.alert.impl.RecordConsumer.Record;
import stroom.alert.rule.impl.AlertRuleStore;
import stroom.alert.rule.shared.AlertRuleDoc;
import stroom.alert.rule.shared.AlertRuleType;
import stroom.alert.rule.shared.ThresholdAlertRule;
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
import stroom.query.api.v2.ResultBuilder;
import stroom.query.api.v2.ResultRequest;
import stroom.query.api.v2.Row;
import stroom.query.api.v2.SearchRequest;
import stroom.query.api.v2.TableResult;
import stroom.query.api.v2.TableResultBuilder;
import stroom.query.api.v2.TableSettings;
import stroom.query.common.v2.CompiledField;
import stroom.query.common.v2.CompiledFields;
import stroom.query.common.v2.Coprocessors;
import stroom.query.common.v2.ErrorConsumer;
import stroom.query.common.v2.ErrorConsumerImpl;
import stroom.query.common.v2.ResultStore;
import stroom.query.common.v2.ResultStoreManager;
import stroom.search.extraction.ExtractionException;
import stroom.search.extraction.ExtractionStateHolder;
import stroom.search.impl.SearchExpressionQueryBuilderFactory;
import stroom.security.api.SecurityContext;
import stroom.task.api.ExecutorProvider;
import stroom.task.api.TaskContextFactory;
import stroom.util.date.DateUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.pipeline.scope.PipelineScopeRunnable;
import stroom.view.impl.ViewStore;
import stroom.view.shared.ViewDoc;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
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

    private final ResultStoreManager resultStoreManager;
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
    private final Map<AlertRuleDoc, Instant> lastExecutionTimes = new ConcurrentHashMap<>();
    private final Map<AlertRuleDoc, Meta> lastProcessedMeta = new ConcurrentHashMap<>();

    @Inject
    public ResultStoreAlertSearchExecutor(final ResultStoreManager resultStoreManager,
                                          final AlertRuleSearchRequestHelper alertRuleSearchRequestHelper,
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
        this.resultStoreManager = resultStoreManager;
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
                    if (ViewDoc.DOCUMENT_TYPE.equals(dataSource.getType())) {
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
        final ViewDoc viewDoc = viewStore.readDocument(dataSource);
        final ExpressionOperator expressionOperator = viewDoc.getFilter();
        if (alertRuleDoc.isEnabled() && ExpressionUtil.termCount(expressionOperator) > 0) {
            final Meta lastMeta = lastProcessedMeta.get(alertRuleDoc);
            final Long lastMetaId = lastMeta == null
                    ? null
                    : lastMeta.getId();
            final List<Meta> metaList = findMeta(expressionOperator,
                    lastMetaId,
                    alertRuleDoc.getMinMetaCreateTimeMs(),
                    alertRuleDoc.getMaxMetaCreateTimeMs(),
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
        Instant mostRecentData = null;
        if (metaList.size() == 0) {
            // There is no new meta to process so assume we are up to date.
            mostRecentData = Instant.now();

        } else {
            final DocRef extractionPipeline = viewDoc.getPipeline();
            final PipelineData pipelineData = getPipelineData(extractionPipeline);

            // Create coprocessors.
            final Coprocessors coprocessors = aggregateRuleValuesConsumerFactory.create(searchRequest);
            // Get the field index.
            final FieldIndex fieldIndex = coprocessors.getFieldIndex();

            // Cache the query for use across multiple streams.
            final SearchExpressionQueryCache searchExpressionQueryCache =
                    new SearchExpressionQueryCache(searchExpressionQueryBuilderFactory, searchRequest);


            for (final Meta meta : metaList) {
                try {
                    if (Status.UNLOCKED.equals(meta.getStatus())) {
                        mostRecentData = Instant.ofEpochMilli(meta.getCreateMs());
                        pipelineScopeRunnable.scopeRunnable(() -> {
                            taskContextFactory.context("Alert Stream Processor", taskContext -> {
                                try {
                                    final AlertFieldListConsumer alertFieldListConsumer = new AlertFieldListConsumer(
                                            searchRequest,
                                            fieldIndex,
                                            coprocessors,
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
                                   final Instant mostRecentData) {
        if (AlertRuleType.AGGREGATE.equals(alertRuleDoc.getAlertRuleType())) {
            final DocRef feedDocRef = alertRuleDoc.getAlertRule().getDestinationFeed();
            pipelineScopeRunnable.scopeRunnable(() -> {
                final DetectionsWriter detectionsWriter = detectionsWriterProvider.get();
                detectionsWriter.setFeed(feedDocRef);
                detectionsWriter.start();
                try {
                    try {
                        execThresholdAlertRule(alertRuleDoc,
                                (ThresholdAlertRule) alertRuleDoc.getAlertRule(),
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
                                        final ThresholdAlertRule thresholdAlertRule,
                                        final RecordConsumer recordConsumer,
                                        final Instant mostRecentData) {
        final Duration executionFrequency = Duration.parse(thresholdAlertRule.getExecutionFrequency());
        final Duration executionDelay = Duration.parse(thresholdAlertRule.getExecutionDelay());

        final Instant lastTime = lastExecutionTimes.get(alertRuleDoc);
        if (lastTime == null) {
            // Execute from the beginning of time as this hasn't executed before.
            Instant from = Instant.ofEpochMilli(0);

            // Execute up to most recent data minus the execution delay.
            Instant to = mostRecentData;
            to = to.minus(executionDelay);
            to = DateUtil.roundDown(to, executionFrequency);

            execThresholdAlertRule(alertRuleDoc, thresholdAlertRule, from, to, recordConsumer);

        } else {
            // Get the last time we executed and round down to the frequency.
            Instant from = lastTime;
            from = DateUtil.roundDown(from, executionFrequency);

            // Add the frequency to the `from` time.
            Instant maxTo = mostRecentData;
            maxTo = maxTo.minus(executionDelay);
            maxTo = DateUtil.roundDown(maxTo, executionFrequency);

            Instant to = from.plus(executionFrequency);
            to = DateUtil.roundDown(to, executionFrequency);

            if (to.isAfter(maxTo)) {
                to = maxTo;
            }

            // See if it is time to execute again.
            if (to.isBefore(Instant.now().minus(executionDelay))) {
                execThresholdAlertRule(alertRuleDoc, thresholdAlertRule, from, to, recordConsumer);
            }
        }
    }

    private void execThresholdAlertRule(final AlertRuleDoc alertRuleDoc,
                                        final ThresholdAlertRule thresholdAlertRule,
                                        final Instant from,
                                        final Instant to,
                                        final RecordConsumer recordConsumer) {
        final QueryKey queryKey = alertRuleDoc.getQueryKey();
        final Optional<ResultStore> optionalResultStore = resultStoreManager.getIfPresent(queryKey);
        if (optionalResultStore.isPresent()) {


            // TODO : TEMPORARY COMPLETION - NEEDS SYNC BLOCK
            try {
                optionalResultStore.get().signalComplete();
                optionalResultStore.get().awaitCompletion();
            } catch (final InterruptedException e) {

            }

            // Create a search request.
            SearchRequest searchRequest = alertRuleSearchRequestHelper.create(alertRuleDoc);
            // Create a time filter. Note the filter times are exclusive.
            final TimeFilter timeFilter = new TimeFilter(thresholdAlertRule.getTimeField(),
                    from,
                    to.plusMillis(1));

            searchRequest = searchRequest.copy().key(queryKey).incremental(true).build();
            // Perform the search.
            final Map<String, ResultBuilder<?>> resultBuilderMap = new HashMap<>();
            for (final ResultRequest resultRequest : searchRequest.getResultRequests()) {
                final TableResultConsumer tableResultConsumer =
                        new TableResultConsumer(alertRuleDoc, recordConsumer, timeFilter);
                resultBuilderMap.put(resultRequest.getComponentId(), tableResultConsumer);
            }

            resultStoreManager.search(searchRequest, resultBuilderMap);

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
        private final TimeFilter timeFilter;

        private List<Field> fields;
        private int timeFieldIndex = -1;

        public TableResultConsumer(final AlertRuleDoc alertRuleDoc,
                                   final RecordConsumer recordConsumer,
                                   final TimeFilter timeFilter) {
            this.alertRuleDoc = alertRuleDoc;
            this.recordConsumer = recordConsumer;
            this.timeFilter = timeFilter;
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

            try {
                for (int i = 0; i < fields.size() && timeFieldIndex == -1; i++) {
                    if (timeFilter.timeField()
                            .equals(fields.get(i).getName())) {
                        timeFieldIndex = i;
                    }
                }
                if (timeFieldIndex == -1) {
                    throw new RuntimeException("Unable to find time field: " +
                            timeFilter.timeField());
                }
            } catch (final RuntimeException e) {
                LOGGER.error(e::getMessage, e);
                throw e;
            }

            return this;
        }

        @Override
        public TableResultConsumer addRow(final Row row) {
            try {
                final List<String> values = row.getValues();
                // See if we match the time filter.
                final String timeString = values.get(timeFieldIndex);
                Instant time = null;
                try {
                    time = Instant.ofEpochMilli(DateUtil.parseUnknownString(timeString));
                } catch (final RuntimeException e) {
                    LOGGER.debug(e::getMessage, e);
                }

                if (timeFilter.from().isBefore(time) && timeFilter.to().isAfter(time)) {
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
                }
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
