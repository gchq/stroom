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
import stroom.pipeline.factory.PipelineDataHolderFactory;
import stroom.pipeline.factory.PipelineFactory;
import stroom.pipeline.factory.PipelineFactoryException;
import stroom.pipeline.shared.PipelineDoc;
import stroom.pipeline.shared.data.PipelineElement;
import stroom.pipeline.shared.data.PipelineProperty;
import stroom.pipeline.shared.stepping.FindElementDocRequest;
import stroom.pipeline.shared.stepping.GetPipelineForMetaRequest;
import stroom.pipeline.shared.stepping.PipelineStepRequest;
import stroom.pipeline.shared.stepping.SteppingResult;
import stroom.pipeline.stepping.StepResultResolver.SessionStepResult;
import stroom.security.api.SecurityContext;
import stroom.security.api.UserIdentity;
import stroom.security.shared.AppPermission;
import stroom.task.api.ExecutorProvider;
import stroom.task.api.TaskContext;
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
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

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
    // The durable stepping sessions, keyed by (user, session id). A session is NOT removed when a step
    // completes - it lives until terminate, idle reap, or close, and that is what makes subsequent steps
    // cheap.
    private final Map<Key, SteppingSession> currentSessions = new ConcurrentHashMap<>();
    private final TaskManager taskManager;
    private final StepDataStoreManager stepDataStoreManager;
    private final PipelineDataHolderFactory pipelineDataHolderFactory;
    private final ElementFingerprinter elementFingerprinter;
    private final SteppingConfig steppingConfig;
    private final StepResultResolver stepResultResolver;

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
                           final TaskManager taskManager,
                           final StepDataStoreManager stepDataStoreManager,
                           final PipelineDataHolderFactory pipelineDataHolderFactory,
                           final ElementFingerprinter elementFingerprinter,
                           final SteppingConfig steppingConfig,
                           final StepResultResolver stepResultResolver) {
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
        this.stepDataStoreManager = stepDataStoreManager;
        this.pipelineDataHolderFactory = pipelineDataHolderFactory;
        this.elementFingerprinter = elementFingerprinter;
        this.steppingConfig = steppingConfig;
        this.stepResultResolver = stepResultResolver;
    }

    /**
     * Phase 2 capture: process a whole stream in capture mode into a fresh {@link StepDataStore} and
     * return the store plus the fingerprints that key the captured IO. Synchronous. The caller owns the
     * returned session and should call {@link #deleteCaptureSession} when done. (The live step() path is
     * not yet cut over to this - that is the Phase 4 durable-session work.)
     */
    public SteppingCaptureResult capture(final PipelineStepRequest request, final long metaId) {
        final String sessionId = UUID.randomUUID().toString();
        try {
            final ElementFingerprints fingerprints = computeFingerprints(request);
            final StreamSweep sweep = launchSweep(sessionId, request, metaId, fingerprints);
            // Synchronous variant: wait for the whole stream to be captured. A timed-out wait must not be
            // mistaken for a complete capture - the sweep is still writing to the store we would return.
            if (!sweep.awaitComplete(request.getTimeout() == null ? Long.MAX_VALUE : request.getTimeout())) {
                final TaskContext taskContext = sweep.getTaskContext();
                sweep.requestTerminate();
                if (taskContext != null) {
                    taskManager.terminate(taskContext.getTaskId());
                }
                throw new RuntimeException("Timed out waiting for stepping capture of stream " + metaId);
            }
            if (sweep.getError() != null) {
                throw new RuntimeException("Stepping capture failed", sweep.getError());
            }
            return new SteppingCaptureResult(sessionId, sweep.getStore(), fingerprints);
        } catch (final RuntimeException e) {
            // Don't leak the store (open channels + temp dir) if capture failed part way through.
            deleteCaptureSession(sessionId);
            throw e;
        }
    }

    /**
     * Launch an asynchronous capture of one stream into a fresh {@link StreamSweep}; returns immediately.
     * Readers wait on the sweep for records to become available.
     */
    public StreamSweep launchSweep(final String sessionId,
                                   final PipelineStepRequest request,
                                   final long metaId,
                                   final ElementFingerprints fingerprints) {
        final StepDataStore store = stepDataStoreManager.getOrCreateStore(sessionId, metaId);
        final StreamSweep sweep = new StreamSweep(metaId, store);
        final Executor executor = executorProvider.get(THREAD_POOL);
        try {
            CompletableFuture
                    .runAsync(taskContextFactory.context("Stepping capture", taskContext ->
                            steppingRequestHandlerProvider.get().execCapture(
                                    taskContext, request, metaId, sweep, fingerprints)), executor)
                    // A reader blocks on this sweep until it signals, so every way the task can end must
                    // signal. execCapture handles its own failures, but anything it cannot catch (an Error,
                    // or a failure constructing the handler/task context) would otherwise leave the sweep
                    // neither complete nor errored, hanging every reader until its deadline - forever, for
                    // an unbounded await. This is the backstop for those paths.
                    .whenComplete((unused, t) -> {
                        if (t != null) {
                            sweep.markError(t);
                        } else if (!sweep.isComplete()) {
                            sweep.markError(new RuntimeException(
                                    "Stepping capture of stream " + metaId + " ended without completing"));
                        }
                    });
        } catch (final RuntimeException e) {
            // e.g. the executor rejected the task - nothing will ever run to signal the sweep.
            sweep.markError(e);
        }
        return sweep;
    }

    /**
     * Compute the element fingerprints (keys for the captured IO) for a request's pipeline. Computed once
     * per session as they are the same for every stream.
     */
    public ElementFingerprints computeFingerprints(final PipelineStepRequest request) {
        return elementFingerprinter.fingerprint(
                pipelineDataHolderFactory.create(request.getPipelineDoc()).getMergedPipelineData(),
                NullSafe.map(request.getCode()));
    }

    public void deleteCaptureSession(final String sessionId) {
        stepDataStoreManager.deleteSession(sessionId);
    }

    /**
     * Create a durable stepping session for a request: computes the fingerprints once and resolves the
     * ordered candidate stream id list. Streams are swept lazily as steps target them. Call
     * {@link SteppingSession#close()} when finished.
     */
    public SteppingSession createSession(final PipelineStepRequest request) {
        return securityContext.secureResult(AppPermission.STEPPING_PERMISSION, () ->
                createSession(request, computeFingerprints(request), getStreamIdList(request.getCriteria())));
    }

    private SteppingSession createSession(final PipelineStepRequest request,
                                          final ElementFingerprints fingerprints,
                                          final List<Long> streamIds) {
        final String sessionId = UUID.randomUUID().toString();
        return new SteppingSession(
                sessionId,
                streamIds,
                request,
                fingerprints,
                (metaId, sweepRequest, sweepFingerprints) ->
                        launchSweep(sessionId, sweepRequest, metaId, sweepFingerprints),
                this::closeSession,
                this::terminateSweep,
                steppingConfig.getMaxSweptStreamsPerSession());
    }

    private void closeSession(final SteppingSession session) {
        session.getActiveSweeps().forEach(this::terminateSweep);
        deleteCaptureSession(session.getSessionId());
    }

    private void terminateSweep(final StreamSweep sweep) {
        // Request termination BEFORE reading the task context. A sweep that has been launched but whose
        // task has not started yet has no context to terminate; the flag is what stops it, and this
        // ordering is the half of the handshake that guarantees the capture task sees it.
        sweep.requestTerminate();
        final TaskContext taskContext = sweep.getTaskContext();
        if (taskContext != null) {
            taskManager.terminate(taskContext.getTaskId());
        }
    }

    /**
     * Resolve the session's candidate streams as the requesting user. This must never run as the processing
     * user: the returned ids drive what the session will sweep and show, so doing so would let a user step
     * through data they have no permission to read.
     */
    private List<Long> getStreamIdList(final FindMetaCriteria criteria) {
        return securityContext.secureResult(AppPermission.STEPPING_PERMISSION, () ->
                securityContext.useAsReadResult(() ->
                        metaService.find(criteria).getValues().stream()
                                .map(Meta::getId)
                                .toList()));
    }

    /**
     * Serve one step from the user's durable stepping session, sweeping streams into the persisted store as
     * they are needed. Unlike the pipeline-per-step path this replaced, the session is NOT torn down when a
     * step completes - that is what lets subsequent steps be served from the store instead of re-running the
     * pipeline. It is removed only by terminate, by the idle reap below, or by close.
     */
    public SteppingResult step(final PipelineStepRequest request) {
        LOGGER.trace(() -> "step() - " + request);
        return securityContext.secureResult(AppPermission.STEPPING_PERMISSION, () -> {
            final UserIdentity userIdentity = securityContext.getUserIdentity();
            final ElementFingerprints fingerprints = computeFingerprints(request);
            final List<Long> streamIds = getStreamIdList(request.getCriteria());

            final SteppingSession session = getOrCreateSession(userIdentity, request, fingerprints, streamIds);
            // Point the session at this step's code. If an element was edited its fingerprint (and its
            // downstream) changed, so this step is served under new keys while the untouched elements are
            // still read from what was already captured.
            session.refresh(request, fingerprints);

            final SessionStepResult sessionResult = stepResultResolver.resolveSession(
                    session, request, request.getTimeout() == null ? 0L : request.getTimeout());

            reapIdleSessions();

            return toSteppingResult(request, session, sessionResult);
        });
    }

    private SteppingSession getOrCreateSession(final UserIdentity userIdentity,
                                               final PipelineStepRequest request,
                                               final ElementFingerprints fingerprints,
                                               final List<Long> streamIds) {
        final String requestedId = request.getSessionUuid();
        if (requestedId != null) {
            final SteppingSession existing = currentSessions.get(new Key(userIdentity, requestedId));
            // A session is scoped to the stream selection it was created with; if the selection changed
            // underneath us its stream list and captured data no longer answer this request.
            if (existing != null && !existing.isClosed() && existing.matchesSelection(streamIds)) {
                return existing;
            }
            if (existing != null) {
                removeSession(new Key(userIdentity, requestedId));
            }
            // Otherwise fall through and start a fresh session. The client is told the new id in the
            // response and adopts it, so a reaped or stale session self-heals into a re-sweep rather than
            // failing the step.
            LOGGER.debug(() -> "Replacing stale stepping session: " + requestedId);
        }

        final SteppingSession session = createSession(request, fingerprints, streamIds);
        currentSessions.put(new Key(userIdentity, session.getSessionId()), session);
        return session;
    }

    /**
     * Map the session's answer onto the wire result. {@code complete} means "this step query resolved" - the
     * client polls while a sweep is still working towards the requested record.
     */
    private SteppingResult toSteppingResult(final PipelineStepRequest request,
                                            final SteppingSession session,
                                            final SessionStepResult result) {
        final Set<String> generalErrors = result.generalError() == null
                ? Collections.emptySet()
                : Set.of(result.generalError());
        final Integer streamOffset = result.foundLocation() == null
                ? null
                : session.getStreamIdList().indexOf(result.foundLocation().getMetaId());

        return new SteppingResult(
                session.getSessionId(),
                request.getStepFilterMap(),
                result.progressLocation(),
                result.foundLocation(),
                result.stepData(),
                streamOffset,
                result.foundRecord(),
                generalErrors,
                result.segmentedData(),
                result.complete());
    }

    private void reapIdleSessions() {
        final Instant oldest = Instant.now().minus(steppingConfig.getMaxSessionIdleTime().getDuration());
        currentSessions.forEach((key, session) -> {
            if (session.getLastAccessTime().isBefore(oldest)) {
                LOGGER.debug(() -> "Reaping idle stepping session: " + key);
                removeSession(key);
            }
        });
    }

    private void removeSession(final Key key) {
        final SteppingSession session = currentSessions.remove(key);
        if (session != null) {
            session.close();
        }
    }

    public Boolean terminateStepping(final PipelineStepRequest request) {
        LOGGER.trace(() -> "terminateStepping() - " + request);

        if (request.getSessionUuid() != null) {
            LOGGER.debug(() -> "Terminate stepping: " + request.getSessionUuid());
            final UserIdentity userIdentity = securityContext.getUserIdentity();
            final Key key = new Key(userIdentity, request.getSessionUuid());
            if (currentSessions.containsKey(key)) {
                removeSession(key);
                return true;
            }
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


    // --------------------------------------------------------------------------------


    /**
     * The result of a {@link #capture} call: the session id owning the persisted data, the store, and the
     * fingerprints needed to read element chunks back (e.g. via {@code StepResultResolver}).
     */
    public record SteppingCaptureResult(String sessionId, StepDataStore store, ElementFingerprints fingerprints) {

    }
}
