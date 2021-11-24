package stroom.pipeline.stepping;

import stroom.docref.DocRef;
import stroom.docstore.shared.DocRefUtil;
import stroom.meta.api.MetaService;
import stroom.meta.shared.FindMetaCriteria;
import stroom.meta.shared.Meta;
import stroom.meta.shared.MetaExpressionUtil;
import stroom.meta.shared.Status;
import stroom.pipeline.PipelineStore;
import stroom.pipeline.SupportsCodeInjection;
import stroom.pipeline.factory.Element;
import stroom.pipeline.factory.ElementFactory;
import stroom.pipeline.factory.ElementRegistry;
import stroom.pipeline.factory.ElementRegistryFactory;
import stroom.pipeline.factory.PipelineFactory;
import stroom.pipeline.factory.PipelineFactoryException;
import stroom.pipeline.shared.PipelineDoc;
import stroom.pipeline.shared.data.PipelineElement;
import stroom.pipeline.shared.data.PipelineProperty;
import stroom.pipeline.shared.stepping.FindElementDocRequest;
import stroom.pipeline.shared.stepping.GetPipelineForMetaRequest;
import stroom.pipeline.shared.stepping.PipelineStepRequest;
import stroom.pipeline.shared.stepping.SteppingResult;
import stroom.security.api.SecurityContext;
import stroom.task.api.ExecutorProvider;
import stroom.task.api.TaskContext;
import stroom.task.api.TaskContextFactory;
import stroom.task.api.ThreadPoolImpl;
import stroom.task.shared.ThreadPool;
import stroom.util.pipeline.scope.PipelineScopeRunnable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Function;
import java.util.function.Supplier;
import javax.inject.Inject;
import javax.inject.Provider;

public class SteppingService {

    private static final Logger LOGGER = LoggerFactory.getLogger(SteppingService.class);

    static final ThreadPool THREAD_POOL = new ThreadPoolImpl("Stepping");

    private final TaskContextFactory taskContextFactory;
    private final Provider<SteppingRequestHandler> steppingRequestHandlerProvider;
    private final ExecutorProvider executorProvider;
    private final MetaService metaService;
    private final PipelineStore pipelineStore;
    private final SecurityContext securityContext;
    private final PipelineScopeRunnable pipelineScopeRunnable;
    private final ElementRegistryFactory pipelineElementRegistryFactory;
    private final ElementFactory elementFactory;

    @Inject
    public SteppingService(final TaskContextFactory taskContextFactory,
                           final Provider<SteppingRequestHandler> steppingRequestHandlerProvider,
                           final ExecutorProvider executorProvider,
                           final MetaService metaService,
                           final PipelineStore pipelineStore,
                           final SecurityContext securityContext,
                           final PipelineScopeRunnable pipelineScopeRunnable,
                           final ElementRegistryFactory pipelineElementRegistryFactory,
                           final ElementFactory elementFactory) {
        this.taskContextFactory = taskContextFactory;
        this.steppingRequestHandlerProvider = steppingRequestHandlerProvider;
        this.executorProvider = executorProvider;
        this.metaService = metaService;
        this.pipelineStore = pipelineStore;
        this.securityContext = securityContext;
        this.pipelineScopeRunnable = pipelineScopeRunnable;
        this.pipelineElementRegistryFactory = pipelineElementRegistryFactory;
        this.elementFactory = elementFactory;
    }

    public SteppingResult step(final PipelineStepRequest request) {
        // Execute the stepping task.
        final Function<TaskContext, SteppingResult> function = taskContext -> {
            final SteppingRequestHandler steppingRequestHandler = steppingRequestHandlerProvider.get();
            return steppingRequestHandler.exec(taskContext, request);
        };
        final Supplier<SteppingResult> supplier = taskContextFactory.contextResult("Translation stepping", function);
        final Executor executor = executorProvider.get(THREAD_POOL);
        return CompletableFuture.supplyAsync(supplier, executor).join();
    }

    public DocRef getPipelineForStepping(final GetPipelineForMetaRequest request) {
        DocRef docRef = null;

        // First try and get the pipeline from the selected child stream.
        Meta childMeta = getMeta(request.getChildMetaId());
        if (childMeta != null) {
            docRef = getPipeline(childMeta);
        }

        if (docRef == null) {
            // If we didn't get a pipeline docRef from a child stream then try and
            // find a child stream to get one from.
            childMeta = getFirstChildMeta(request.getMetaId());
            if (childMeta != null) {
                docRef = getPipeline(childMeta);
            }
        }

        return docRef;
    }

    private Meta getMeta(final Long id) {
        if (id == null) {
            return null;
        }

        return securityContext.asProcessingUserResult(() -> {
            final FindMetaCriteria criteria = FindMetaCriteria.createFromId(id);
            final List<Meta> streamList = metaService.find(criteria).getValues();
            if (streamList != null && streamList.size() > 0) {
                return streamList.get(0);
            }

            return null;
        });
    }

    private Meta getFirstChildMeta(final Long id) {
        if (id == null) {
            return null;
        }

        return securityContext.asProcessingUserResult(() -> {
            final FindMetaCriteria criteria =
                    new FindMetaCriteria(MetaExpressionUtil.createParentIdExpression(id, Status.UNLOCKED));
            return metaService.find(criteria).getFirst();
        });
    }

    private DocRef getPipeline(final Meta meta) {
        DocRef docRef = null;

        // So we have got the stream so try and get the first pipeline that was
        // used to produce children for this stream.
        String pipelineUuid = meta.getPipelineUuid();
        if (pipelineUuid != null) {
            try {
                // Ensure the current user is allowed to load this pipeline.
                final PipelineDoc pipelineDoc = pipelineStore.readDocument(new DocRef(PipelineDoc.DOCUMENT_TYPE,
                        pipelineUuid));
                docRef = DocRefUtil.create(pipelineDoc);
            } catch (final RuntimeException e) {
                // Ignore.
            }
        }

        return docRef;
    }

    public DocRef findElementDoc(final FindElementDocRequest request) {
        return pipelineScopeRunnable.scopeResult(() -> {
            final PipelineElement pipelineElement = request.getPipelineElement();
            final List<PipelineProperty> properties = request.getProperties();
            final String elementType = pipelineElement.getType();

            final ElementRegistry pipelineElementRegistry = pipelineElementRegistryFactory.get();
            LOGGER.debug("create() - loading element {}", pipelineElement);

            final Class<Element> elementClass = pipelineElementRegistry.getElementClass(elementType);

            if (elementClass == null) {
                throw new PipelineFactoryException("Unable to load elementClass for type " + elementType);
            }

            final Element elementInstance = elementFactory.getElementInstance(elementClass);
            if (elementInstance == null) {
                throw new PipelineFactoryException("Unable to load elementInstance for class " + elementClass);
            }

            // Set the properties on this instance.
            for (final PipelineProperty property : properties) {
                PipelineFactory.setProperty(pipelineElementRegistry,
                        pipelineElement.getId(),
                        elementType,
                        elementInstance,
                        property.getName(),
                        property.getValue(),
                        null);
            }

            if (elementInstance instanceof SupportsCodeInjection) {
                final SupportsCodeInjection supportsCodeInjection = (SupportsCodeInjection) elementInstance;
                return supportsCodeInjection.findDoc(request.getFeedName(), request.getPipelineName(), LOGGER::debug);
            }

            throw new PipelineFactoryException("Element does not support code injection " + elementClass);
        });
    }

}
