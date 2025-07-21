package stroom.search.extraction;

import stroom.meta.api.MetaService;
import stroom.pipeline.PipelineStore;
import stroom.pipeline.factory.PipelineDataCache;
import stroom.query.api.QueryKey;
import stroom.security.api.SecurityContext;
import stroom.task.api.ExecutorProvider;
import stroom.task.api.TaskContextFactory;
import stroom.util.pipeline.scope.PipelineScopeRunnable;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

public class ExtractionDecoratorFactory {

    private final FieldValueExtractorFactory fieldValueExtractorFactory;
    private final ExtractionConfig extractionConfig;
    private final ExecutorProvider executorProvider;
    private final TaskContextFactory taskContextFactory;
    private final PipelineScopeRunnable pipelineScopeRunnable;
    private final SecurityContext securityContext;
    private final MetaService metaService;
    private final PipelineStore pipelineStore;
    private final PipelineDataCache pipelineDataCache;
    private final Provider<ExtractionTaskHandler> handlerProvider;
    private final Provider<QueryInfoHolder> queryInfoHolderProvider;
    private final Provider<FieldListConsumerHolder> fieldListConsumerHolderProvider;

    @Inject
    ExtractionDecoratorFactory(final FieldValueExtractorFactory fieldValueExtractorFactory,
                               final ExtractionConfig extractionConfig,
                               final ExecutorProvider executorProvider,
                               final TaskContextFactory taskContextFactory,
                               final PipelineScopeRunnable pipelineScopeRunnable,
                               final SecurityContext securityContext,
                               final MetaService metaService,
                               final PipelineStore pipelineStore,
                               final PipelineDataCache pipelineDataCache,
                               final Provider<ExtractionTaskHandler> handlerProvider,
                               final Provider<QueryInfoHolder> queryInfoHolderProvider,
                               final Provider<FieldListConsumerHolder> fieldListConsumerHolderProvider) {
        this.fieldValueExtractorFactory = fieldValueExtractorFactory;
        this.extractionConfig = extractionConfig;
        this.executorProvider = executorProvider;
        this.taskContextFactory = taskContextFactory;
        this.pipelineScopeRunnable = pipelineScopeRunnable;
        this.securityContext = securityContext;
        this.metaService = metaService;
        this.pipelineStore = pipelineStore;
        this.pipelineDataCache = pipelineDataCache;
        this.handlerProvider = handlerProvider;
        this.queryInfoHolderProvider = queryInfoHolderProvider;
        this.fieldListConsumerHolderProvider = fieldListConsumerHolderProvider;
    }

    public ExtractionDecorator create(final QueryKey queryKey) {
        return new ExtractionDecorator(
                fieldValueExtractorFactory,
                extractionConfig,
                executorProvider,
                taskContextFactory,
                pipelineScopeRunnable,
                securityContext,
                metaService,
                pipelineStore,
                pipelineDataCache,
                handlerProvider,
                queryInfoHolderProvider,
                fieldListConsumerHolderProvider,
                queryKey);
    }
}
