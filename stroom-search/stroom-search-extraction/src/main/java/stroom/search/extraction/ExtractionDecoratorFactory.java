package stroom.search.extraction;

import stroom.meta.api.MetaService;
import stroom.pipeline.PipelineStore;
import stroom.pipeline.factory.PipelineDataCache;
import stroom.query.api.v2.QueryKey;
import stroom.security.api.SecurityContext;
import stroom.task.api.ExecutorProvider;
import stroom.task.api.TaskContextFactory;
import stroom.util.pipeline.scope.PipelineScopeRunnable;

import javax.inject.Inject;
import javax.inject.Provider;

public class ExtractionDecoratorFactory {

    private final ExtractionConfig extractionConfig;
    private final ExecutorProvider executorProvider;
    private final TaskContextFactory taskContextFactory;
    private final PipelineScopeRunnable pipelineScopeRunnable;
    private final SecurityContext securityContext;
    private final AnnotationsDecoratorFactory receiverDecoratorFactory;
    private final MetaService metaService;
    private final PipelineStore pipelineStore;
    private final PipelineDataCache pipelineDataCache;
    private final Provider<ExtractionTaskHandler> handlerProvider;

    @Inject
    ExtractionDecoratorFactory(final ExtractionConfig extractionConfig,
                               final ExecutorProvider executorProvider,
                               final TaskContextFactory taskContextFactory,
                               final PipelineScopeRunnable pipelineScopeRunnable,
                               final SecurityContext securityContext,
                               final AnnotationsDecoratorFactory receiverDecoratorFactory,
                               final MetaService metaService,
                               final PipelineStore pipelineStore,
                               final PipelineDataCache pipelineDataCache,
                               final Provider<ExtractionTaskHandler> handlerProvider) {
        this.extractionConfig = extractionConfig;
        this.executorProvider = executorProvider;
        this.taskContextFactory = taskContextFactory;
        this.pipelineScopeRunnable = pipelineScopeRunnable;
        this.securityContext = securityContext;
        this.receiverDecoratorFactory = receiverDecoratorFactory;
        this.metaService = metaService;
        this.pipelineStore = pipelineStore;
        this.pipelineDataCache = pipelineDataCache;
        this.handlerProvider = handlerProvider;
    }

    public ExtractionDecorator create(final QueryKey queryKey) {
        return new ExtractionDecorator(
                extractionConfig,
                executorProvider,
                taskContextFactory,
                pipelineScopeRunnable,
                securityContext,
                receiverDecoratorFactory,
                metaService,
                pipelineStore,
                pipelineDataCache,
                handlerProvider,
                queryKey);
    }
}
