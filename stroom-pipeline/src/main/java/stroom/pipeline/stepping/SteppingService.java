/*
 * Copyright 2016-2025 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
import stroom.security.api.UserIdentity;
import stroom.task.api.ExecutorProvider;
import stroom.task.api.TaskContextFactory;
import stroom.task.api.TaskManager;
import stroom.task.api.ThreadPoolImpl;
import stroom.task.shared.ThreadPool;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.pipeline.scope.PipelineScopeRunnable;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;

@Singleton
public class SteppingService {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(SteppingService.class);

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
    private final Map<Key, SteppingRequestHandler> currentHandlers = new ConcurrentHashMap<>();
    private final TaskManager taskManager;

    @Inject
    public SteppingService(final TaskContextFactory taskContextFactory,
                           final Provider<SteppingRequestHandler> steppingRequestHandlerProvider,
                           final ExecutorProvider executorProvider,
                           final MetaService metaService,
                           final PipelineStore pipelineStore,
                           final SecurityContext securityContext,
                           final PipelineScopeRunnable pipelineScopeRunnable,
                           final ElementRegistryFactory pipelineElementRegistryFactory,
                           final ElementFactory elementFactory,
                           final TaskManager taskManager) {
        this.taskContextFactory = taskContextFactory;
        this.steppingRequestHandlerProvider = steppingRequestHandlerProvider;
        this.executorProvider = executorProvider;
        this.metaService = metaService;
        this.pipelineStore = pipelineStore;
        this.securityContext = securityContext;
        this.pipelineScopeRunnable = pipelineScopeRunnable;
        this.pipelineElementRegistryFactory = pipelineElementRegistryFactory;
        this.elementFactory = elementFactory;
        this.taskManager = taskManager;
    }

    public SteppingResult step(final PipelineStepRequest request) {
        LOGGER.trace(() -> "step() - " + request);
        final UserIdentity userIdentity = securityContext.getUserIdentity();

        final Key key;
        final SteppingResult result;
        if (request.getSessionUuid() == null) {
            LOGGER.debug("New Stepping Session");

            // Create a new session UUID on the request.
            final PipelineStepRequest modifiedRequest = request
                    .copy()
                    .sessionUuid(UUID.randomUUID().toString())
                    .build();
            key = new Key(userIdentity, modifiedRequest.getSessionUuid());
            final SteppingRequestHandler handler = currentHandlers.computeIfAbsent(key, k -> {
                final AtomicReference<SteppingRequestHandler> reference = new AtomicReference<>();
                final CountDownLatch countDownLatch = new CountDownLatch(1);
                final Executor executor = executorProvider.get(THREAD_POOL);

                CompletableFuture.runAsync(taskContextFactory.context("Translation stepping", taskContext -> {
                    final SteppingRequestHandler steppingRequestHandler = steppingRequestHandlerProvider.get();
                    reference.set(steppingRequestHandler);
                    countDownLatch.countDown();
                    steppingRequestHandler.exec(taskContext, modifiedRequest);
                }), executor);

                try {
                    countDownLatch.await();
                } catch (final InterruptedException e) {
                    // Continue to interrupt this thread.
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(e.getMessage(), e);
                }

                return reference.get();
            });
            result = handler.getResult(modifiedRequest);

        } else {
            LOGGER.debug(() -> "Polling stepping session: " + request.getSessionUuid());

            key = new Key(userIdentity, request.getSessionUuid());
            final SteppingRequestHandler handler = currentHandlers.get(key);
            if (handler == null) {
                throw new RuntimeException("No stepping session found for key: " + request.getSessionUuid());
            }

            result = handler.getResult(request);
        }

        // Remove handler if complete.
        if (result.isComplete()) {
            currentHandlers.remove(key);
        }

        // Also remove old handlers for dead stepping tasks and terminate them.
        final Instant oldest = Instant.now().minusSeconds(10);
        currentHandlers.forEach((key1, value) -> {
            if (value.getLastRequestTime().isBefore(oldest)) {
                terminate(key1);
            }
        });

        return result;
    }

    public Boolean terminateStepping(final PipelineStepRequest request) {
        LOGGER.trace(() -> "terminateStepping() - " + request);

        if (request.getSessionUuid() != null) {
            LOGGER.debug(() -> "Terminate stepping: " + request.getSessionUuid());
            final UserIdentity userIdentity = securityContext.getUserIdentity();
            final Key key = new Key(userIdentity, request.getSessionUuid());
            return terminate(key);
        }
        return false;
    }

    private Boolean terminate(final Key key) {
        LOGGER.debug(() -> "Terminate: " + key);
        final SteppingRequestHandler handler = currentHandlers.remove(key);
        if (handler != null) {
            taskManager.terminate(handler.getTaskContext().getTaskId());
            return true;
        }
        return false;
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
        } else {
            return securityContext.asProcessingUserResult(() -> {
                final FindMetaCriteria criteria = FindMetaCriteria.createFromId(id);
                final List<Meta> streamList = metaService.find(criteria).getValues();
                return NullSafe.first(streamList);
            });
        }
    }

    private Meta getFirstChildMeta(final Long id) {
        if (id == null) {
            return null;
        } else {
            return securityContext.asProcessingUserResult(() -> {
                final FindMetaCriteria criteria =
                        new FindMetaCriteria(MetaExpressionUtil.createParentIdExpression(id, Status.UNLOCKED));
                return metaService.find(criteria).getFirst();
            });
        }
    }

    private DocRef getPipeline(final Meta meta) {
        DocRef docRef = null;

        // So we have got the stream so try and get the first pipeline that was
        // used to produce children for this stream.
        final String pipelineUuid = meta.getPipelineUuid();
        if (pipelineUuid != null) {
            try {
                // Ensure the current user is allowed to load this pipeline.
                final PipelineDoc pipelineDoc = pipelineStore.readDocument(new DocRef(PipelineDoc.TYPE,
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
                PipelineFactory.setProperty(
                        pipelineElementRegistry,
                        pipelineElement.getId(),
                        elementType,
                        elementInstance,
                        property.getName(),
                        property.getValue(),
                        null);
            }

            if (elementInstance instanceof final SupportsCodeInjection supportsCodeInjection) {
                return supportsCodeInjection.findDoc(
                        request.getFeedName(),
                        request.getPipelineName(),
                        LOGGER::debug);
            }

            throw new PipelineFactoryException("Element does not support code injection " + elementClass);
        });
    }


    // --------------------------------------------------------------------------------


    public record Key(UserIdentity userIdentity, String uuid) {

    }
}
