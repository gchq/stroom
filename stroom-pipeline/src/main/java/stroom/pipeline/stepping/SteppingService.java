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
import stroom.pipeline.factory.PipelineFactory.MidPipelineScope;
import stroom.pipeline.shared.data.PipelineData;
import stroom.pipeline.shared.stepping.PipelineStepRequest;
import stroom.pipeline.shared.stepping.StepLocation;
import stroom.pipeline.shared.stepping.SteppingResult;
import stroom.pipeline.stepping.capture.ReprocessDriver;
import stroom.pipeline.stepping.capture.StreamCaptureDriver;
import stroom.pipeline.stepping.capture.StreamSweep;
import stroom.pipeline.stepping.fingerprint.ElementFingerprinter;
import stroom.pipeline.stepping.fingerprint.ElementFingerprints;
import stroom.pipeline.stepping.read.ReprocessPlanner;
import stroom.pipeline.stepping.read.ReprocessPlanner.Decision;
import stroom.pipeline.stepping.read.SessionStepResolver;
import stroom.pipeline.stepping.read.SessionStepResolver.SessionStepResult;
import stroom.pipeline.stepping.read.StepDemandPlanner;
import stroom.pipeline.stepping.read.SteppableElements;
import stroom.pipeline.stepping.read.SteppingGraphBuilder;
import stroom.pipeline.stepping.read.SteppingGraphBuilder.Graph;
import stroom.pipeline.stepping.session.SteppingSession;
import stroom.pipeline.stepping.session.SteppingSessionRegistry;
import stroom.pipeline.stepping.store.Coverage;
import stroom.pipeline.stepping.store.RecordRange;
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
import stroom.util.shared.ElementId;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.stream.Collectors;

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
    private final Provider<ReprocessDriver> reprocessDriverProvider;
    private final ExecutorProvider executorProvider;
    private final MetaService metaService;
    private final SecurityContext securityContext;
    private final TaskManager taskManager;
    private final StepDataStoreManager stepDataStoreManager;
    private final PipelineDataHolderFactory pipelineDataHolderFactory;
    private final ElementFingerprinter elementFingerprinter;
    // A Provider, not the object: config is live (the UI can change it, and tests override it per class),
    // and this is a singleton - injecting the object directly would freeze whatever the value was at startup.
    private final Provider<SteppingConfig> steppingConfigProvider;
    private final SessionStepResolver sessionStepResolver;
    private final SteppingSessionRegistry sessionRegistry;
    private final SteppableElements steppableElements;
    private final SteppingResultMapper resultMapper = new SteppingResultMapper();
    private final ReprocessPlanner reprocessPlanner = new ReprocessPlanner();
    private final StepDemandPlanner demandPlanner;
    // How each stream fill was served, observable so callers (and tests) can confirm an edit reused
    // upstream rather than re-running it. Four counters, NOT disjoint: reprocessLaunches counts every
    // launched reprocess (whole-stream and ranged alike), and onDemandLaunches counts the subset that
    // materialised a bounded range of records. fullSweepLaunches and backboneLaunches count captures
    // from source (whole pipeline vs truncated at the record boundary).
    private final AtomicLong onDemandLaunches = new AtomicLong();
    private final AtomicLong reprocessLaunches = new AtomicLong();
    private final AtomicLong fullSweepLaunches = new AtomicLong();
    private final AtomicLong backboneLaunches = new AtomicLong();

    @Inject
    public SteppingService(final TaskContextFactory taskContextFactory,
                           final Provider<StreamCaptureDriver> streamCaptureDriverProvider,
                           final Provider<ReprocessDriver> reprocessDriverProvider,
                           final ExecutorProvider executorProvider,
                           final MetaService metaService,
                           final SecurityContext securityContext,
                           final TaskManager taskManager,
                           final StepDataStoreManager stepDataStoreManager,
                           final PipelineDataHolderFactory pipelineDataHolderFactory,
                           final ElementFingerprinter elementFingerprinter,
                           final Provider<SteppingConfig> steppingConfigProvider,
                           final SessionStepResolver sessionStepResolver,
                           final SteppingSessionRegistry sessionRegistry,
                           final SteppableElements steppableElements) {
        this.taskContextFactory = taskContextFactory;
        this.streamCaptureDriverProvider = streamCaptureDriverProvider;
        this.reprocessDriverProvider = reprocessDriverProvider;
        this.executorProvider = executorProvider;
        this.metaService = metaService;
        this.securityContext = securityContext;
        this.taskManager = taskManager;
        this.stepDataStoreManager = stepDataStoreManager;
        this.pipelineDataHolderFactory = pipelineDataHolderFactory;
        this.elementFingerprinter = elementFingerprinter;
        this.steppingConfigProvider = steppingConfigProvider;
        this.demandPlanner = new StepDemandPlanner(steppingConfigProvider);
        this.sessionStepResolver = sessionStepResolver;
        this.sessionRegistry = sessionRegistry;
        this.steppableElements = steppableElements;
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
        return capture(request, metaId, Set.of());
    }

    /**
     * As {@link #capture(PipelineStepRequest, long)}, but builds the pipeline only as far as
     * {@code stopAfter} - capturing the head stage rather than the whole pipeline.
     */
    public SteppingCaptureResult capture(final PipelineStepRequest request,
                                         final long metaId,
                                         final Set<String> stopAfter) {
        final String sessionId = UUID.randomUUID().toString();
        try {
            final ElementFingerprints fingerprints = computeFingerprints(request);
            final StreamSweep sweep = launchSweep(sessionId, request, metaId, fingerprints, stopAfter);
            // Synchronous variant: wait for the whole stream to be captured.
            awaitProducer(sweep, request.getTimeout(), "Stepping capture", metaId);
            return new SteppingCaptureResult(sessionId, sweep.getStore(), fingerprints);
        } catch (final RuntimeException e) {
            // Don't leak the store (open channels + temp dir) if capture failed part way through.
            deleteCaptureSession(sessionId);
            throw e;
        }
    }

    /**
     * Re-run {@code startElementId} and its downstream from {@code sourceStore}'s stored upstream output,
     * capturing the reprocessed IO into a fresh store, and return it. Synchronous, like {@link #capture}:
     * the caller owns the returned session and must {@link #deleteCaptureSession} it. This is the
     * synchronous test entry to the split - re-running only the edited stage rather than the whole
     * pipeline; {@link #step} reaches the same machinery through {@code launchReprocess}.
     */
    public SteppingCaptureResult reprocess(final PipelineStepRequest request,
                                           final long metaId,
                                           final String startElementId,
                                           final String upstreamElementId,
                                           final StepDataStore sourceStore,
                                           final ElementFingerprints fingerprints) {
        return reprocess(request, metaId, startElementId, upstreamElementId, sourceStore, fingerprints,
                MidPipelineScope.ELEMENT_AND_DESCENDANTS);
    }

    /**
     * As {@link #reprocess(PipelineStepRequest, long, String, String, StepDataStore, ElementFingerprints)},
     * but with control over how much of the pipeline below the start element is re-run - {@code ELEMENT_ONLY}
     * runs just that element, which is how a per-element stage runs.
     */
    public SteppingCaptureResult reprocess(final PipelineStepRequest request,
                                           final long metaId,
                                           final String startElementId,
                                           final String upstreamElementId,
                                           final StepDataStore sourceStore,
                                           final ElementFingerprints fingerprints,
                                           final MidPipelineScope scope) {
        return reprocess(request, metaId, startElementId, upstreamElementId, sourceStore, fingerprints, scope,
                (RecordRange) null);
    }

    /**
     * As above, but materialises only {@code onDemandTarget} - the single record the user is looking at -
     * rather than re-running the element over the whole stream.
     */
    public SteppingCaptureResult reprocess(final PipelineStepRequest request,
                                           final long metaId,
                                           final String startElementId,
                                           final String upstreamElementId,
                                           final StepDataStore sourceStore,
                                           final ElementFingerprints fingerprints,
                                           final MidPipelineScope scope,
                                           final RecordRange onDemandRange) {
        final String sessionId = UUID.randomUUID().toString();
        try {
            final StepDataStore targetStore = stepDataStoreManager.getOrCreateStore(sessionId, metaId);
            final StreamSweep sweep = new StreamSweep(metaId, targetStore, fingerprints);
            runProducer("Stepping reprocess", metaId, sweep, taskContext ->
                    reprocessDriverProvider.get().reprocess(taskContext, request, metaId,
                            startElementId, upstreamElementId, sourceStore, sweep, fingerprints,
                            scope, onDemandRange));
            awaitProducer(sweep, request.getTimeout(), "Stepping reprocess", metaId);
            return new SteppingCaptureResult(sessionId, targetStore, fingerprints);
        } catch (final RuntimeException e) {
            deleteCaptureSession(sessionId);
            throw e;
        }
    }

    /**
     * Run a producer's work asynchronously, guaranteeing the sweep is signalled however the task ends. A
     * reader blocks on the sweep until it signals, so <b>every way the task can end must signal</b>. The
     * driver handles its own failures, but anything it cannot catch (an Error, or a failure constructing
     * the handler/task context) would otherwise leave the sweep neither complete nor errored, hanging every
     * reader until its deadline - forever, for an unbounded await. The {@code whenComplete} backstop and
     * the rejected-execution catch cover those paths.
     */
    private void runProducer(final String taskName,
                             final long metaId,
                             final StreamSweep sweep,
                             final Consumer<TaskContext> work) {
        final Executor executor = executorProvider.get(THREAD_POOL);
        try {
            CompletableFuture
                    .runAsync(taskContextFactory.context(taskName, work), executor)
                    .whenComplete((unused, t) -> {
                        if (t != null) {
                            sweep.markError(t);
                        } else if (!sweep.hasEnded()) {
                            sweep.markError(new RuntimeException(
                                    taskName + " of stream " + metaId + " ended without completing"));
                        }
                    });
        } catch (final RuntimeException e) {
            // e.g. the executor rejected the task - nothing will ever run to signal the sweep.
            sweep.markError(e);
        }
    }

    /**
     * Synchronously wait for a producer to finish, terminating it on timeout. A timed-out wait must not be
     * mistaken for a complete capture - the sweep is still writing to the store the caller would return.
     */
    private void awaitProducer(final StreamSweep sweep,
                               final Long timeout,
                               final String what,
                               final long metaId) {
        if (!sweep.awaitEnd(timeout == null ? Long.MAX_VALUE : timeout)) {
            // Flag first, context second - the producer publishes its context before reading the flag, so
            // whichever of us runs second sees the other's write (the same load-bearing ordering as
            // terminateSweep; reversed, a still-queued task could start against a deleted store).
            sweep.requestTerminate();
            final TaskContext taskContext = sweep.getTaskContext();
            if (taskContext != null) {
                taskManager.terminate(taskContext.getTaskId());
            }
            throw new RuntimeException("Timed out waiting for " + what + " of stream " + metaId);
        }
        if (sweep.getError() != null) {
            throw new RuntimeException(what + " failed", sweep.getError());
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
        return launchSweep(sessionId, request, metaId, fingerprints, Set.of());
    }

    /**
     * As {@link #launchSweep(String, PipelineStepRequest, long, ElementFingerprints)}, but builds only as far
     * as {@code stopAfter}.
     */
    public StreamSweep launchSweep(final String sessionId,
                                   final PipelineStepRequest request,
                                   final long metaId,
                                   final ElementFingerprints fingerprints,
                                   final Set<String> stopAfter) {
        final StepDataStore store = stepDataStoreManager.getOrCreateStore(sessionId, metaId);
        final StreamSweep sweep = new StreamSweep(metaId, store, fingerprints);
        runProducer("Stepping capture", metaId, sweep, taskContext ->
                streamCaptureDriverProvider.get().capture(
                        taskContext, request, metaId, sweep, fingerprints, stopAfter));
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
                (metaId, sweepRequest, sweepFingerprints, priorSweeps, running) ->
                        producerFor(sessionId, sweepRequest, metaId, sweepFingerprints, priorSweeps, running),
                this::closeSession,
                this::abandonSweep,
                steppingConfigProvider.get().getMaxSweptStreamsPerSession());
    }


    /**
     * The producer that will serve this step - launching one only when nothing cheaper answers. The ladder,
     * in order: everything the step demands is already in the store (a signalled no-op sweep); a plan
     * against captured upstream (a reprocess of just the changed elements - materialising a bounded range
     * where the step's records can be named, or attaching to a running producer already making them);
     * waiting on the frontier of a capture already in flight; a skeleton backbone; and finally a capture
     * from source - the once-per-stream sweep that is always correct. Despite living behind
     * {@link SteppingSession.SweepLauncher}, most calls launch nothing.
     */
    private StreamSweep producerFor(final String sessionId,
                                  final PipelineStepRequest request,
                                  final long metaId,
                                  final ElementFingerprints fingerprints,
                                  final List<StreamSweep> priorSweeps,
                                  final List<StreamSweep> running) {
        // True only if a prior sweep of this stream ran to the end WITHOUT error. Under the skeleton
        // default that is normally a completed BACKBONE (boundary IO only) - what makes a reprocess from it
        // safe is the planner's per-element completeness check (StagePlanner.covers), not whole-pipeline
        // capture. An errored sweep also "ends" (markError stops readers waiting) but its store is
        // truncated, so it must NOT count - reprocessing from it would silently serve a short stream.
        final boolean priorCompleteCapture = priorSweeps.stream().anyMatch(StreamSweep::isSuccessfullyCaptured);
        // A capture of this stream that is still running. Its records are arriving as we speak, so a step
        // whose demand it has not reached yet can wait on it instead of launching a second capture.
        final StreamSweep liveProducer = priorSweeps.stream()
                .filter(prior -> !prior.hasEnded())
                .findFirst()
                .orElse(null);

        final StepDataStore store = stepDataStoreManager.getOrCreateStore(sessionId, metaId);
        final Set<String> capturedElementIds = store.getCapturedElementIds();
        final PipelineData pipelineData = pipelineDataHolderFactory
                .create(request.getPipelineDoc()).getMergedPipelineData();
        if (!capturedElementIds.isEmpty()) {
            // The steppable set comes from the PIPELINE, not from what has been captured: a capture that
            // stopped at the record boundary has captured nothing below it, and a set read from the store
            // would tell the planner there is nothing below it to run. What the store holds is unioned in so
            // that an element type the registry cannot classify, but which was captured anyway, is still
            // planned for.
            final Set<String> steppableIds = new LinkedHashSet<>(steppableElements.in(pipelineData));
            steppableIds.addAll(capturedElementIds);
            final Graph graph = SteppingGraphBuilder.build(pipelineData, steppableIds);

            // What must the feed cover? A whole-stream reprocess (span == null) still requires a prior
            // COMPLETE, error-free capture: hasCompleteElement measures against the store's own extent, so
            // without the completion gate a capture truncated by an error would count as "complete" and a
            // reprocess from it would silently serve a short stream. But a step that is about ONE record does
            // not need the whole stream - it needs the feed to HOLD that record, which a partial capture
            // legitimately can. That is what lets an edit land while the stream's first capture is still
            // running (scenario C) instead of costing a full re-sweep.
            final StepLocation demanded = priorCompleteCapture
                    ? null
                    : demandPlanner.demandedRecordFor(request, metaId, store, false);
            final RecordRange span = demanded == null ? null : RecordRange.of(demanded);
            if (priorCompleteCapture || span != null) {
                final Decision decision =
                        reprocessPlanner.plan(graph.elements(), graph.parentsOf(), store, fingerprints, span);
                if (decision.satisfied()) {
                    // Everything the step demands is already in the store - materialised by an earlier step
                    // or a previous loop iteration of this one. Launching anything would be waste; the
                    // resolver just needs a sweep signalling the records so it navigates them as usual.
                    // Signalled for the record the DEMAND named, not for the request's reference location -
                    // navigation demands the record next to its reference, and FIRST has no reference at all.
                    return applyStreamMetadata(signalledSweep(metaId, store, RecordRange.of(demanded)),
                            priorSweeps);
                }
                if (!decision.fullSweep()) {
                    RecordRange range = demandPlanner.onDemandRangeFor(request, graph, decision, store, fingerprints,
                            metaId, priorCompleteCapture);
                    // A plan made against a span is only good for materialising that span. If the range
                    // computation declined (e.g. the step turned out to need whole-stream work), a
                    // whole-stream reprocess from a feed only checked for a few records would truncate the
                    // stream - fall back to the sweep instead of risking it.
                    if (span != null && range == null) {
                        fullSweepLaunches.incrementAndGet();
                        return launchSweep(sessionId, request, metaId, fingerprints);
                    }
                    // The small-stream policy: a demand on a skeleton-swept stream whose extent is small is
                    // promoted to the whole extent - one reprocess, roughly what the whole-pipeline sweep it
                    // replaced would have cost, and every later step is a store read. Only once the backbone
                    // has COMPLETED (span == null; a whole-stream reprocess from a partial feed would serve a
                    // truncated stream), and only while the code is still exactly what that backbone ran
                    // under - an edit changes the signature and is never promoted, whatever the stream size,
                    // because the per-record post-edit refresh is the inner loop this design exists to keep
                    // fast.
                    if (range != null && span == null
                        && shouldMaterialiseEagerly(store, fingerprints, priorSweeps)) {
                        LOGGER.debug(() -> "producerFor() - small stream, materialising the whole extent");
                        range = null;
                    }
                    if (range != null) {
                        // The store is the cache. A demand whose records the edited element already holds is
                        // answered without constructing a pipeline at all; a demand a running producer is
                        // already making attaches to that producer rather than double-launching. Only the
                        // gap is ever launched. (This is also what lets a windowed scan advance across
                        // polls: each poll recomputes its window from the frontier, the finished window is
                        // satisfied from the store, and the next one launches.)
                        final StreamSweep satisfied =
                                satisfiedFromStore(metaId, store, decision.startElementId(),
                                        fingerprints, range);
                        if (satisfied != null) {
                            return applyStreamMetadata(satisfied, priorSweeps);
                        }
                        for (final StreamSweep producer : running) {
                            if (producer.isOnDemand() && overlaps(producer.getDemand(), range)) {
                                return producer;
                            }
                        }
                    }
                    reprocessLaunches.incrementAndGet();
                    final StreamSweep sweep = launchReprocess(request, metaId, decision.startElementId(),
                            decision.upstreamElementId(), store, fingerprints, range);
                    sweep.setDemand(range);
                    return applyStreamMetadata(sweep, priorSweeps);
                }
            }
            // Nothing could be planned - either the step's demand cannot be named until more has been
            // captured, or the plan came back a full sweep. Before starting a second capture of this stream,
            // see whether the one already running will answer this step once it gets further.
            final StreamSweep waiting = waitForFrontier(liveProducer, request, graph, store, fingerprints);
            if (waiting != null) {
                return waiting;
            }
        }

        // Nothing to build on: the stream needs capturing from source. With the skeleton sweep enabled the
        // capture is truncated at the record boundary - the parser and everything above it, whose
        // fingerprints no below-boundary edit can change - and everything below is materialised on demand
        // by the machinery above, exactly as it already is after an edit. The whole-pipeline sweep remains
        // the path for pipelines with no boundary (reader/text, no parser) and the fallback when a
        // completed backbone still could not satisfy the planner.
        if (steppingConfigProvider.get().isSkeletonSweep()) {
            final StreamSweep backbone = backboneFor(sessionId, request, metaId, fingerprints,
                    pipelineData, priorSweeps);
            if (backbone != null) {
                return backbone;
            }
        }
        fullSweepLaunches.incrementAndGet();
        return launchSweep(sessionId, request, metaId, fingerprints);
    }

    /**
     * Materialise the whole stream now rather than the demanded records?
     * <p>
     * Yes when a backbone of this stream has completed, the request's code is still <b>exactly what that
     * backbone captured under</b> (same fingerprint signature), and the extent - known, because the backbone
     * finished - is small enough that doing it all costs no more than the whole-pipeline sweep the backbone
     * replaced. The signature test is what protects the inner loop: an edit changes the signature, so an
     * edit's materialisation is never promoted, whatever the stream size. Records already materialised (a
     * step served behind the backbone's frontier while it ran) are skipped by the store's idempotent writes,
     * so promotion never redoes them.
     */
    private boolean shouldMaterialiseEagerly(final StepDataStore store,
                                             final ElementFingerprints fingerprints,
                                             final List<StreamSweep> priorSweeps) {
        final boolean unEditedBackboneComplete = priorSweeps.stream()
                .anyMatch(prior -> prior.getCacheKey() != null
                                   && prior.isSuccessfullyCaptured()
                                   && prior.getFingerprints() != null
                                   && prior.getFingerprints().getSignature()
                                           .equals(fingerprints.getSignature()));
        if (!unEditedBackboneComplete) {
            return false;
        }
        long totalRecords = 0;
        for (final long partIndex : store.getPartIndices()) {
            totalRecords += store.getRecordCount(partIndex);
        }
        return totalRecords <= steppingConfigProvider.get().getEagerMaterialisationRecords();
    }

    /**
     * Hand a sweep that serves records without capturing the stream the per-stream facts a step result
     * needs - is a part segmented, what did the pipeline raise while starting up. Those belong to the
     * stream, so they are taken from a capture of it: preferably a complete one, else whatever capture is
     * running. Without this, every record served from a materialisation reports its part unsegmented.
     */
    private StreamSweep applyStreamMetadata(final StreamSweep sweep, final List<StreamSweep> priorSweeps) {
        priorSweeps.stream()
                .max(Comparator.comparing(StreamSweep::isSuccessfullyCaptured))
                .ifPresent(sweep::copyStreamMetadataFrom);
        return sweep;
    }

    /**
     * The backbone answer for a stream that needs capturing, or null to fall back to a full sweep.
     * <p>
     * One backbone per {@code (stream, boundary fingerprints)}: if one is already running, wait on it (the
     * launcher was consulted because the step could not be planned yet, and the records it is waiting for
     * are on their way); if none exists, launch one - truncated at the boundary via {@code stopAfter} - and
     * mark it with its own cache key so no step lookup can ever be served from it directly. A backbone that
     * has already FINISHED and still left the planner unable to plan is not waited on - waiting on a
     * completed producer would spin, and whatever the plan's problem was (an errored backbone, most likely),
     * the full sweep is the answer that is always correct.
     */
    private StreamSweep backboneFor(final String sessionId,
                                    final PipelineStepRequest request,
                                    final long metaId,
                                    final ElementFingerprints fingerprints,
                                    final PipelineData pipelineData,
                                    final List<StreamSweep> priorSweeps) {
        final Set<String> boundary = steppableElements.boundaryIn(pipelineData);
        final String cacheKey = backboneKeyFor(boundary, fingerprints);
        if (cacheKey == null) {
            return null;
        }
        final StreamSweep existing = priorSweeps.stream()
                .filter(prior -> cacheKey.equals(prior.getCacheKey()))
                .findFirst()
                .orElse(null);
        if (existing != null) {
            return existing.hasEnded() ? null : StreamSweep.waitingOn(existing);
        }
        backboneLaunches.incrementAndGet();
        LOGGER.debug(() -> "backboneFor() - launching skeleton capture of stream " + metaId
                           + " truncated at " + boundary);
        final StreamSweep backbone = launchSweep(sessionId, request, metaId, fingerprints, boundary);
        backbone.setCacheKey(cacheKey);
        return backbone;
    }

    /**
     * The cache identity of a backbone: the boundary elements' cumulative fingerprints, which a
     * below-boundary edit cannot change - so every step of an editing session finds the same backbone -
     * and a boundary edit does, which is exactly when a fresh backbone is needed. Null (no backbone
     * possible) for a pipeline with no boundary or one whose boundary has no computed fingerprint.
     */
    private String backboneKeyFor(final Set<String> boundary, final ElementFingerprints fingerprints) {
        if (boundary.isEmpty()) {
            return null;
        }
        final List<String> boundaryFingerprints = boundary.stream()
                .map(fingerprints::getCumulativeFingerprint)
                .toList();
        if (boundaryFingerprints.contains(null)) {
            // A boundary element with no fingerprint cannot be keyed; sorted after the null check because
            // sorting nulls throws.
            return null;
        }
        return "backbone:" + boundaryFingerprints.stream().sorted().collect(Collectors.joining("+"));
    }

    /**
     * Wait for the capture already running, rather than start a second one?
     * <p>
     * Yes when this step is about records the running capture simply has not reached yet. Its output for those
     * records will be exactly what a reprocess needs - an edit below the record boundary does not change the
     * fingerprints it is writing above - so the work is already being done, and duplicating it would parse the
     * same stream twice to produce the same chunks. This covers both a step that named its record and one
     * whose record cannot be named until the capture gets further (stepping off the captured end), and LAST,
     * which needs a complete capture however it is served and so can only be better off waiting for the one in
     * flight than starting another.
     * <p>
     * The test is a question the planner can actually answer: <b>would this step be answerable about a record
     * the capture has already produced?</b> If planning against the frontier yields a reprocess, the only thing
     * missing is the records, and they are on their way. If it yields a full sweep, the edit changed something
     * this capture cannot supply (a parser change re-keys everything) and waiting would only delay the sweep
     * that is genuinely needed.
     * <p>
     * A <b>filtered</b> step does not wait. Its answer is the next record that <i>matches</i>, which a second
     * capture can start finding as it goes, whereas waiting here would block until the first capture finished;
     * the windowed scan takes over once there is a complete capture to scan.
     *
     * @return a handle to wait on, or null to go ahead and launch.
     */
    private StreamSweep waitForFrontier(final StreamSweep liveProducer,
                                        final PipelineStepRequest request,
                                        final Graph graph,
                                        final StepDataStore store,
                                        final ElementFingerprints fingerprints) {
        if (liveProducer == null || demandPlanner.isAnyFilterApplied(request)) {
            return null;
        }
        // The frontier: the furthest record the capture has signalled, in the furthest part it has reached.
        final Coverage captured = liveProducer.coverage();
        final List<Long> parts = captured.parts();
        if (parts.isEmpty()) {
            return null;
        }
        final long part = parts.getLast();
        final long frontier = captured.last(part);
        if (frontier < 0) {
            // Nothing captured yet, so there is no record to test the plan against.
            return null;
        }
        final Decision atFrontier = reprocessPlanner.plan(graph.elements(), graph.parentsOf(), store,
                fingerprints, new RecordRange(part, frontier, frontier));
        if (atFrontier.fullSweep()) {
            return null;
        }
        LOGGER.debug(() -> "waitForFrontier() - waiting for the capture already running past " + part + ":" + frontier);
        return StreamSweep.waitingOn(liveProducer);
    }

    /**
     * A sweep that is already answered: every record of the demand is held by the element the plan would
     * have re-run, so there is nothing to launch. The records were committed atomically with all their
     * sibling elements' IO ({@code putRecord} is all-or-nothing per record), so the one element holding a
     * record means the whole materialisation of that record is present. The returned sweep just signals the
     * demanded records into its watermark so the resolver navigates them exactly as it would a fresh
     * materialisation's.
     */
    private StreamSweep satisfiedFromStore(final long metaId,
                                           final StepDataStore store,
                                           final String startElementId,
                                           final ElementFingerprints fingerprints,
                                           final RecordRange range) {
        final String fingerprint = fingerprints.getCumulativeFingerprint(startElementId);
        if (fingerprint == null) {
            return null;
        }
        final Coverage held = store.elementCoverage(new ElementId(startElementId), fingerprint, () -> true);
        for (long record = range.firstRecord(); record <= range.lastRecord(); record++) {
            if (!held.holds(range.partIndex(), record)) {
                return null;
            }
        }
        return signalledSweep(metaId, store, range);
    }

    /**
     * A completed on-demand sweep over records that are already in the store: nothing runs, the records are
     * just signalled into the watermark so the resolver navigates them exactly as it would a fresh
     * materialisation's.
     */
    private StreamSweep signalledSweep(final long metaId, final StepDataStore store, final RecordRange range) {
        final StreamSweep sweep = new StreamSweep(metaId, store);
        sweep.markOnDemand();
        sweep.setDemand(range);
        for (long record = range.firstRecord(); record <= range.lastRecord(); record++) {
            sweep.recordCaptured(new StepLocation(metaId, range.partIndex(), record));
        }
        sweep.markFullyCaptured();
        return sweep;
    }

    /**
     * A running producer is worth waiting on if it is producing <b>any</b> of the asked records, not only
     * all of them. A filtered scan recomputes its window from the committed frontier on every wakeup, so
     * the asked window slides a few records past the in-flight producer's demand each time; requiring full
     * coverage here meant no recomputation ever attached, and every wakeup launched a new overlapping
     * reprocess - one per record signalled, hundreds over one scan, each re-running records the store
     * already held. Attaching on overlap lets the producer finish its window; the next poll then plans the
     * remainder from the advanced frontier, and only that remainder is ever launched.
     */
    private boolean overlaps(final RecordRange demand, final RecordRange asked) {
        return demand != null
               && demand.partIndex() == asked.partIndex()
               && demand.firstRecord() <= asked.lastRecord()
               && demand.lastRecord() >= asked.firstRecord();
    }

    /**
     * @return how many launches materialised a <b>bounded range</b> of records rather than re-running over
     * the whole stream. A subset of {@link #getReprocessLaunchCount}. For metrics and tests.
     */
    public long getOnDemandLaunchCount() {
        return onDemandLaunches.get();
    }

    /**
     * @return how many reprocesses were launched - stream fills that re-ran only the changed elements from
     * stored upstream output (edit reuse), whole-stream and ranged alike. Includes every launch counted by
     * {@link #getOnDemandLaunchCount}. For metrics and tests.
     */
    public long getReprocessLaunchCount() {
        return reprocessLaunches.get();
    }

    /**
     * @return the number of stream fills served by a full sweep from source.
     */
    public long getFullSweepLaunchCount() {
        return fullSweepLaunches.get();
    }

    /**
     * @return the number of skeleton (backbone) captures launched. For metrics and tests.
     */
    public long getBackboneLaunchCount() {
        return backboneLaunches.get();
    }

    /**
     * Launch an asynchronous reprocess of just the changed elements into the stream's existing store, feeding
     * them {@code upstreamElementId}'s already-captured output. The reprocessed chunks land under their new
     * fingerprints alongside the reused upstream ones, so a later step reads both from the one store. As with
     * {@link #launchSweep}, every way the task can end signals the sweep.
     */
    private StreamSweep launchReprocess(final PipelineStepRequest request,
                                        final long metaId,
                                        final String startElementId,
                                        final String upstreamElementId,
                                        final StepDataStore store,
                                        final ElementFingerprints fingerprints,
                                        final RecordRange onDemandRange) {
        // The reprocess reads the feed's output from, and writes the reprocessed chunks to, the same store.
        LOGGER.debug(() -> "launchReprocess() - stream " + metaId + ", start " + startElementId
                           + ", range " + onDemandRange);
        final StreamSweep sweep = new StreamSweep(metaId, store, fingerprints);
        if (onDemandRange != null) {
            // Materialising a bounded range rather than capturing the stream: what this sweep can answer
            // is decided by what the store holds, not by a contiguous range.
            sweep.markOnDemand();
            onDemandLaunches.incrementAndGet();
        }
        runProducer("Stepping reprocess", metaId, sweep, taskContext ->
                reprocessDriverProvider.get().reprocess(taskContext, request, metaId,
                        startElementId, upstreamElementId, store, sweep, fingerprints,
                        MidPipelineScope.ELEMENT_AND_DESCENDANTS, onDemandRange));
        return sweep;
    }

    private void closeSession(final SteppingSession session) {
        session.getOwnedSweeps().forEach(this::terminateSweep);
        deleteCaptureSession(session.getSessionId());
    }

    /**
     * Stop a sweep whose work has become pointless - it was superseded by an edit - <b>without</b>
     * interrupting its thread.
     * <p>
     * Interrupting would be actively harmful here. {@code TaskContextImpl.terminate()} interrupts the worker,
     * and a {@link java.nio.channels.FileChannel} is closed permanently, for every user, when a thread is
     * interrupted during I/O on it. The session deliberately <b>keeps its store</b> across an edit - that is
     * what makes reverting free - so interrupting a superseded sweep can leave the very file the next step
     * reads unusable, and every later step in the session then fails.
     * <p>
     * The flag is enough: the capture and reprocess loops check it at every record and part boundary, so the
     * worst case is that one more record is captured before the sweep unwinds. The old engine interrupted
     * because it wanted to stop the moment it reached the record it was looking for; this engine captures
     * everything, so that reason no longer exists.
     */
    private void abandonSweep(final StreamSweep sweep) {
        sweep.requestTerminate();
    }

    /**
     * Stop a sweep because the session itself is going away (the user closed stepping, changed selection, or
     * the session was reaped). Interrupting is right here: the work is genuinely unwanted, and the store is
     * deleted immediately afterwards, so a channel closed by the interrupt costs nothing.
     */
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
                                // The criteria carry no sort, so find()'s order is whatever the database
                                // happens to produce - which can differ from one query to the next under
                                // load. This list is load-bearing twice over: it defines what FIRST/LAST
                                // mean and which stream a step crosses into, and the session registry
                                // compares it as a LIST to decide whether a session still matches its
                                // selection - an order flip would tear down a session mid-walk and lose
                                // every capture in it. Sort by id: creation order, stable, and what the
                                // unsorted query returned in practice when the golden corpus was recorded.
                                .sorted()
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
