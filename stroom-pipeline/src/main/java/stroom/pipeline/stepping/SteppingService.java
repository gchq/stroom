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

import stroom.meta.api.MetaService;
import stroom.meta.shared.FindMetaCriteria;
import stroom.meta.shared.Meta;
import stroom.pipeline.factory.PipelineDataHolderFactory;
import stroom.pipeline.shared.stepping.PipelineStepRequest;
import stroom.pipeline.shared.stepping.SteppingResult;
import stroom.pipeline.stepping.capture.StreamCaptureDriver;
import stroom.pipeline.stepping.capture.StreamSweep;
import stroom.pipeline.stepping.fingerprint.ElementFingerprinter;
import stroom.pipeline.stepping.fingerprint.ElementFingerprints;
import stroom.pipeline.stepping.read.SessionStepResolver;
import stroom.pipeline.stepping.read.SessionStepResolver.SessionStepResult;
import stroom.pipeline.stepping.session.SteppingSession;
import stroom.pipeline.stepping.session.SteppingSessionRegistry;
import stroom.pipeline.stepping.store.StepDataStore;
import stroom.pipeline.stepping.store.StepDataStoreManager;
import stroom.pipeline.stepping.store.SteppingConfig;
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
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * The way in to stepping: the client's poll lands here.
 * <p>
 * This orchestrates, it does not implement. A step is: check the permission, compute the fingerprints for
 * the code the client just sent, resolve the stream selection <b>as the requesting user</b>, get the
 * session, resolve the step against it, map the answer onto the wire. Each of those belongs to something
 * else - {@link SteppingSessionRegistry} keys and reaps sessions, {@link SessionStepResolver} does the
 * waiting and stream-crossing, {@link SteppingResultMapper} builds the result.
 * <p>
 * It also owns the one thing that must not move: launching a {@link StreamSweep}. Every path out of a
 * launched sweep has to signal it, because readers block on it - see {@link #launchSweep}.
 */
@Singleton
public class SteppingService {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(SteppingService.class);

    static final ThreadPool THREAD_POOL = new ThreadPoolImpl("Stepping");

    private final TaskContextFactory taskContextFactory;
    private final Provider<StreamCaptureDriver> streamCaptureDriverProvider;
    private final ExecutorProvider executorProvider;
    private final MetaService metaService;
    private final SecurityContext securityContext;
    private final TaskManager taskManager;
    private final StepDataStoreManager stepDataStoreManager;
    private final PipelineDataHolderFactory pipelineDataHolderFactory;
    private final ElementFingerprinter elementFingerprinter;
    private final SteppingConfig steppingConfig;
    private final SessionStepResolver sessionStepResolver;
    private final SteppingSessionRegistry sessionRegistry;
    private final SteppingResultMapper resultMapper = new SteppingResultMapper();

    @Inject
    public SteppingService(final TaskContextFactory taskContextFactory,
                           final Provider<StreamCaptureDriver> streamCaptureDriverProvider,
                           final ExecutorProvider executorProvider,
                           final MetaService metaService,
                           final SecurityContext securityContext,
                           final TaskManager taskManager,
                           final StepDataStoreManager stepDataStoreManager,
                           final PipelineDataHolderFactory pipelineDataHolderFactory,
                           final ElementFingerprinter elementFingerprinter,
                           final SteppingConfig steppingConfig,
                           final SessionStepResolver sessionStepResolver,
                           final SteppingSessionRegistry sessionRegistry) {
        this.taskContextFactory = taskContextFactory;
        this.streamCaptureDriverProvider = streamCaptureDriverProvider;
        this.executorProvider = executorProvider;
        this.metaService = metaService;
        this.securityContext = securityContext;
        this.taskManager = taskManager;
        this.stepDataStoreManager = stepDataStoreManager;
        this.pipelineDataHolderFactory = pipelineDataHolderFactory;
        this.elementFingerprinter = elementFingerprinter;
        this.steppingConfig = steppingConfig;
        this.sessionStepResolver = sessionStepResolver;
        this.sessionRegistry = sessionRegistry;
    }

    /**
     * Capture a whole stream into a fresh {@link StepDataStore} and return the store plus the fingerprints
     * that key the captured IO, blocking until it is complete. The caller owns the returned session and
     * must call {@link #deleteCaptureSession} when done.
     * <p>
     * Stepping itself does not use this - {@link #step} sweeps lazily via a {@link SteppingSession}, so a
     * user never waits for a whole stream. This is the synchronous door in, for tests and for callers that
     * genuinely want the entire stream captured up front.
     */
    public SteppingCaptureResult capture(final PipelineStepRequest request, final long metaId) {
        final String sessionId = UUID.randomUUID().toString();
        try {
            final ElementFingerprints fingerprints = computeFingerprints(request);
            final StreamSweep sweep = launchSweep(sessionId, request, metaId, fingerprints);
            // Synchronous variant: wait for the whole stream to be captured. A timed-out wait must not be
            // mistaken for a complete capture - the sweep is still writing to the store we would return.
            if (!sweep.awaitFullyCaptured(request.getTimeout() == null ? Long.MAX_VALUE : request.getTimeout())) {
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
                            streamCaptureDriverProvider.get().capture(
                                    taskContext, request, metaId, sweep, fingerprints)), executor)
                    // A reader blocks on this sweep until it signals, so every way the task can end must
                    // signal. The driver handles its own failures, but anything it cannot catch (an Error,
                    // or a failure constructing the handler/task context) would otherwise leave the sweep
                    // neither complete nor errored, hanging every reader until its deadline - forever, for
                    // an unbounded await. This is the backstop for those paths.
                    .whenComplete((unused, t) -> {
                        if (t != null) {
                            sweep.markError(t);
                        } else if (!sweep.isFullyCaptured()) {
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
     * Create a durable stepping session for a request's stream selection. Streams are swept lazily as steps
     * target them, each under whatever configuration that step is served with. Call
     * {@link SteppingSession#close()} when finished.
     */
    public SteppingSession createSession(final PipelineStepRequest request) {
        return securityContext.secureResult(AppPermission.STEPPING_PERMISSION, () ->
                createSession(getStreamIdList(request.getCriteria())));
    }

    private SteppingSession createSession(final List<Long> streamIds) {
        final String sessionId = UUID.randomUUID().toString();
        return new SteppingSession(
                sessionId,
                streamIds,
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

            final SteppingSession session = sessionRegistry.getOrCreate(
                    userIdentity,
                    request.getSessionUuid(),
                    streamIds,
                    () -> createSession(streamIds));

            // The fingerprints are what this step is served under. If an element was edited they have
            // changed, so it is served under new keys while the untouched elements are still read from what
            // was already captured.
            final SessionStepResult sessionResult = sessionStepResolver.resolve(
                    session, request, fingerprints, request.getTimeout() == null ? 0L : request.getTimeout());

            sessionRegistry.reapIdle();

            return resultMapper.toResult(request, session, sessionResult);
        });
    }

    public Boolean terminateStepping(final PipelineStepRequest request) {
        LOGGER.trace(() -> "terminateStepping() - " + request);

        if (request.getSessionUuid() != null) {
            return sessionRegistry.terminate(securityContext.getUserIdentity(), request.getSessionUuid());
        }
        return false;
    }


    // --------------------------------------------------------------------------------


    /**
     * The result of a {@link #capture} call: the session id owning the persisted data, the store, and the
     * fingerprints needed to read element chunks back (e.g. via {@code StoreStepResolver}).
     */
    public record SteppingCaptureResult(String sessionId, StepDataStore store, ElementFingerprints fingerprints) {

    }
}
