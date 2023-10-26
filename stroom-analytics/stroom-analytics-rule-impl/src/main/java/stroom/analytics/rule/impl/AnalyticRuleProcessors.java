package stroom.analytics.rule.impl;

import stroom.analytics.shared.AnalyticProcessConfig;
import stroom.analytics.shared.AnalyticProcessType;
import stroom.analytics.shared.AnalyticRuleDoc;
import stroom.analytics.shared.StreamingAnalyticProcessConfig;
import stroom.datasource.api.v2.DataSource;
import stroom.docref.DocRef;
import stroom.docstore.shared.DocRefUtil;
import stroom.entity.shared.ExpressionCriteria;
import stroom.meta.shared.MetaFields;
import stroom.processor.api.ProcessorFilterService;
import stroom.processor.api.ProcessorService;
import stroom.processor.shared.CreateProcessFilterRequest;
import stroom.processor.shared.Processor;
import stroom.processor.shared.ProcessorFields;
import stroom.processor.shared.ProcessorFilter;
import stroom.processor.shared.ProcessorFilterFields;
import stroom.processor.shared.QueryData;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.ExpressionTerm;
import stroom.query.api.v2.ExpressionTerm.Condition;
import stroom.query.api.v2.ExpressionUtil;
import stroom.query.language.DataSourceResolver;
import stroom.query.language.SearchRequestBuilder;
import stroom.query.util.LambdaLogger;
import stroom.query.util.LambdaLoggerFactory;
import stroom.util.shared.ResultPage;
import stroom.view.api.ViewStore;
import stroom.view.shared.ViewDoc;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import javax.inject.Inject;
import javax.inject.Provider;

public class AnalyticRuleProcessors {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(AnalyticRuleProcessors.class);

    private final Provider<DataSourceResolver> dataSourceResolverProvider;
    private final SearchRequestBuilder searchRequestBuilder;
    private final ProcessorService processorService;
    private final ProcessorFilterService processorFilterService;
    private final ViewStore viewStore;

    @Inject
    public AnalyticRuleProcessors(final Provider<DataSourceResolver> dataSourceResolverProvider,
                                  final SearchRequestBuilder searchRequestBuilder,
                                  final ProcessorService processorService,
                                  final ProcessorFilterService processorFilterService,
                                  final ViewStore viewStore) {
        this.dataSourceResolverProvider = dataSourceResolverProvider;
        this.searchRequestBuilder = searchRequestBuilder;
        this.processorService = processorService;
        this.processorFilterService = processorFilterService;
        this.viewStore = viewStore;
    }

    public void deleteProcessorFilters(final AnalyticRuleDoc doc) {
        final List<ProcessorFilter> existing = getProcessorFilters(doc);
        deleteFilters(existing);
    }

    public void updateProcessorFilters(final AnalyticRuleDoc doc) {
        final List<ProcessorFilter> existing = getProcessorFilters(doc);
        if (AnalyticProcessType.STREAMING.equals(doc.getAnalyticProcessType())) {
            final AnalyticProcessConfig analyticProcessConfig = doc.getAnalyticProcessConfig();
            if (analyticProcessConfig instanceof final StreamingAnalyticProcessConfig streamingAnalyticProcessConfig) {
                if (existing.size() > 0) {
                    // Enable/disable filters.
                    enableFilters(existing, streamingAnalyticProcessConfig.isEnabled());
                } else {
                    // Create filter.
                    final Optional<ViewDoc> optionalViewDoc = getViewDoc(doc);
                    if (optionalViewDoc.isEmpty()) {
                        throw new RuntimeException("Unable to find referenced view to process data");
                    }
                    final ViewDoc viewDoc = optionalViewDoc.get();

//                    final Processor processor = getOrCreateProcessor(viewDoc.getPipeline());
                    final ExpressionOperator expressionOperator = getDataProcessingExpression(viewDoc);
                    final QueryData queryData = QueryData
                            .builder()
                            .dataSource(MetaFields.STREAM_STORE_DOC_REF)
                            .expression(expressionOperator)
                            .analyticRule(DocRefUtil.create(doc))
                            .build();
                    final CreateProcessFilterRequest request = CreateProcessFilterRequest
                            .builder()
                            .queryData(queryData)
                            .pipeline(viewDoc.getPipeline())
                            .minMetaCreateTimeMs(streamingAnalyticProcessConfig.getMinMetaCreateTimeMs())
                            .maxMetaCreateTimeMs(streamingAnalyticProcessConfig.getMaxMetaCreateTimeMs())
                            .enabled(analyticProcessConfig.isEnabled())
                            .priority(10)
                            .build();
                    processorFilterService.create(request);
                }
            } else {
                // Disable processor filters.
                enableFilters(existing, false);
            }
        } else {
            // Disable processor filters.
            enableFilters(existing, false);
        }
    }

    private void enableFilters(final List<ProcessorFilter> filters, final boolean enabled) {
        // Disable processor filters.
        for (final ProcessorFilter filter : filters) {
            processorFilterService.setEnabled(filter.getId(), enabled);
        }
    }

    private void deleteFilters(final List<ProcessorFilter> filters) {
        // Disable processor filters.
        for (final ProcessorFilter filter : filters) {
            processorFilterService.delete(filter.getId());
        }
    }

    private ExpressionOperator getDataProcessingExpression(final ViewDoc viewDoc) {
        final ExpressionOperator expressionOperator = viewDoc.getFilter();
        if (expressionOperator == null || ExpressionUtil.termCount(expressionOperator) == 0) {
            throw new RuntimeException("Referenced view has no stream filter");
        }
        return expressionOperator;
    }

    private List<ProcessorFilter> getProcessorFilters(final AnalyticRuleDoc doc) {
        final List<ProcessorFilter> list = new ArrayList<>();
        final Optional<ViewDoc> optional = getViewDoc(doc);
        optional.ifPresent(viewDoc -> {
            final DocRef pipelineDocRef = viewDoc.getPipeline();
            if (pipelineDocRef != null) {
                // First try to find the associated processors
                final ExpressionOperator processorExpression = ExpressionOperator.builder()
                        .addTerm(ProcessorFields.PIPELINE, Condition.IS_DOC_REF, pipelineDocRef).build();
                ResultPage<Processor> processorResultPage = processorService.find(new ExpressionCriteria(
                        processorExpression));
                if (processorResultPage.size() > 1) {
                    throw new RuntimeException("Unexpected number of processors");
                } else if (processorResultPage.size() == 1) {
                    final Processor processor = processorResultPage.getFirst();
                    final ExpressionOperator filterExpression = ExpressionOperator.builder()
                            .addTerm(ProcessorFilterFields.PROCESSOR_ID,
                                    ExpressionTerm.Condition.EQUALS,
                                    processor.getId())
                            .addTerm(ProcessorFilterFields.DELETED, Condition.EQUALS, false)
                            .build();
                    final ResultPage<ProcessorFilter> filterResultPage = processorFilterService
                            .find(new ExpressionCriteria(filterExpression));
                    for (final ProcessorFilter filter : filterResultPage.getValues()) {
                        if (filter.getQueryData() != null) {
                            final DocRef analyticRule = filter.getQueryData().getAnalyticRule();
                            if (Objects.equals(analyticRule.getUuid(), doc.getUuid())) {
                                list.add(filter);
                            }
                        }
                    }
                }
            }
        });

        return list;
    }

    private Optional<ViewDoc> getViewDoc(final AnalyticRuleDoc doc) {
        final AtomicReference<ViewDoc> reference = new AtomicReference<>();
        try {
            if (doc.getQuery() != null) {
                searchRequestBuilder.extractDataSourceNameOnly(doc.getQuery(), dataSourceName -> {
                    try {
                        if (dataSourceName != null) {
                            final DataSource dataSource = dataSourceResolverProvider
                                    .get()
                                    .resolveDataSource(dataSourceName);
                            if (dataSource != null && dataSource.getDocRef() != null) {
                                final DocRef ref = dataSource.getDocRef();
                                reference.set(viewStore.readDocument(ref));
                            }
                        }
                    } catch (final RuntimeException e) {
                        LOGGER.debug(e::getMessage, e);
                    }
                });
            }
        } catch (final RuntimeException e) {
            LOGGER.debug(e::getMessage, e);
        }

        return Optional.ofNullable(reference.get());
    }

//    private Processor getOrCreateProcessor(final DocRef pipeline) {
//        final ExpressionOperator expression = ExpressionOperator
//                .builder()
//                .addTerm(ProcessorFields.PIPELINE, Condition.EQUALS, pipeline.getUuid())
//                .build();
//        final ResultPage<Processor> processorResultPage = processorService.find(new ExpressionCriteria(expression));
//        for (final Processor processor : processorResultPage.getValues()) {
//            if (STREAMING_ANALYTIC_TASK.equals(processor.getTaskType())) {
//                return processor;
//            }
//        }
//        return createProcessor(pipeline);
//    }
//
//    private Processor createProcessor(final DocRef pipeline) {
//        final Processor processor = new Processor(
//                null,
//                null,
//                null,
//                null,
//                null,
//                null,
//                null,
//                STREAMING_ANALYTIC_TASK,
//                pipeline.getUuid(),
//                pipeline.getName(),
//                true,
//                false);
//        return processorService.create(processor);
//    }
}
