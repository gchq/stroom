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
import stroom.pipeline.shared.stepping.StepType;
import stroom.pipeline.shared.stepping.SteppingResult;
import stroom.pipeline.stepping.capture.ReprocessDriver;
import stroom.pipeline.stepping.capture.StreamCaptureDriver;
import stroom.pipeline.stepping.capture.ReprocessDriver.RecordRange;
import stroom.pipeline.stepping.capture.StreamSweep;
import stroom.pipeline.stepping.fingerprint.ElementFingerprinter;
import stroom.pipeline.stepping.fingerprint.ElementFingerprints;
import stroom.pipeline.stepping.read.ReprocessPlanner;
import stroom.pipeline.stepping.read.ReprocessPlanner.Decision;
import stroom.pipeline.stepping.read.SessionStepResolver;
import stroom.pipeline.stepping.read.StagePlanner;
import stroom.pipeline.stepping.read.SessionStepResolver.SessionStepResult;
import stroom.pipeline.stepping.read.SteppingGraphBuilder;
import stroom.pipeline.stepping.read.SteppingGraphBuilder.Graph;
import stroom.pipeline.stepping.session.SteppingSession;
import stroom.pipeline.stepping.session.SteppingSessionRegistry;
import stroom.pipeline.stepping.store.Coverage;
import stroom.pipeline.stepping.store.StepDataStore;
import stroom.pipeline.stepping.store.StepDataStoreManager;
import stroom.pipeline.stepping.store.SteppingConfig;
import stroom.security.api.SecurityContext;
import stroom.util.shared.ElementId;
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

import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
    private final Provider<ReprocessDriver> reprocessDriverProvider;
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
    private final ReprocessPlanner reprocessPlanner = new ReprocessPlanner();
    // How each stream fill was served: a reprocess of just the changed elements, or a full sweep from source.
    // Observable so callers (and tests) can confirm an edit reused upstream rather than re-running it.
    private final java.util.concurrent.atomic.AtomicLong onDemandLaunches =
            new java.util.concurrent.atomic.AtomicLong();
    private final java.util.concurrent.atomic.AtomicLong reprocessLaunches =
            new java.util.concurrent.atomic.AtomicLong();
    private final java.util.concurrent.atomic.AtomicLong fullSweepLaunches =
            new java.util.concurrent.atomic.AtomicLong();

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
                           final SteppingConfig steppingConfig,
                           final SessionStepResolver sessionStepResolver,
                           final SteppingSessionRegistry sessionRegistry) {
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
     * Re-run {@code startElementId} and its downstream from {@code sourceStore}'s stored upstream output,
     * capturing the reprocessed IO into a fresh store, and return it. Synchronous, like {@link #capture}:
     * the caller owns the returned session and must {@link #deleteCaptureSession} it. This is the entry to
     * the split - re-running only the edited stage rather than the whole pipeline - and is currently exercised
     * by the reprocess de-risk test; {@link #step} does not use it yet.
     */
    public SteppingCaptureResult reprocess(final PipelineStepRequest request,
                                           final long metaId,
                                           final String startElementId,
                                           final String feedElementId,
                                           final StepDataStore sourceStore,
                                           final ElementFingerprints fingerprints) {
        return reprocess(request, metaId, startElementId, feedElementId, sourceStore, fingerprints,
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
                                           final String feedElementId,
                                           final StepDataStore sourceStore,
                                           final ElementFingerprints fingerprints,
                                           final MidPipelineScope scope) {
        return reprocess(request, metaId, startElementId, feedElementId, sourceStore, fingerprints, scope,
                (RecordRange) null);
    }

    /**
     * As above, but materialises only {@code onDemandTarget} - the single record the user is looking at -
     * rather than re-running the element over the whole stream.
     */
    public SteppingCaptureResult reprocess(final PipelineStepRequest request,
                                           final long metaId,
                                           final String startElementId,
                                           final String feedElementId,
                                           final StepDataStore sourceStore,
                                           final ElementFingerprints fingerprints,
                                           final MidPipelineScope scope,
                                           final RecordRange onDemandRange) {
        final String sessionId = UUID.randomUUID().toString();
        try {
            final StepDataStore targetStore = stepDataStoreManager.getOrCreateStore(sessionId, metaId);
            final StreamSweep sweep = new StreamSweep(metaId, targetStore, fingerprints);
            final Executor executor = executorProvider.get(THREAD_POOL);
            try {
                CompletableFuture
                        .runAsync(taskContextFactory.context("Stepping reprocess", taskContext ->
                                reprocessDriverProvider.get().reprocess(taskContext, request, metaId,
                                        startElementId, feedElementId, sourceStore, sweep, fingerprints,
                                        scope, onDemandRange)), executor)
                        .whenComplete((unused, t) -> {
                            if (t != null) {
                                sweep.markError(t);
                            } else if (!sweep.isFullyCaptured()) {
                                sweep.markError(new RuntimeException(
                                        "Stepping reprocess of stream " + metaId + " ended without completing"));
                            }
                        });
            } catch (final RuntimeException e) {
                sweep.markError(e);
            }

            if (!sweep.awaitFullyCaptured(request.getTimeout() == null ? Long.MAX_VALUE : request.getTimeout())) {
                final TaskContext taskContext = sweep.getTaskContext();
                sweep.requestTerminate();
                if (taskContext != null) {
                    taskManager.terminate(taskContext.getTaskId());
                }
                throw new RuntimeException("Timed out waiting for stepping reprocess of stream " + metaId);
            }
            if (sweep.getError() != null) {
                throw new RuntimeException("Stepping reprocess failed", sweep.getError());
            }
            return new SteppingCaptureResult(sessionId, targetStore, fingerprints);
        } catch (final RuntimeException e) {
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
        final Executor executor = executorProvider.get(THREAD_POOL);
        try {
            CompletableFuture
                    .runAsync(taskContextFactory.context("Stepping capture", taskContext ->
                            streamCaptureDriverProvider.get().capture(
                                    taskContext, request, metaId, sweep, fingerprints, stopAfter)), executor)
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
                (metaId, sweepRequest, sweepFingerprints, priorSweeps, running) ->
                        launchFor(sessionId, sweepRequest, metaId, sweepFingerprints, priorSweeps, running),
                this::closeSession,
                this::abandonSweep,
                steppingConfig.getMaxSweptStreamsPerSession());
    }

    /**
     * Decide how to fill a stream's sweep: reprocess just the changed elements from stored upstream output
     * when an edit allows it, otherwise sweep the whole stream from source.
     * <p>
     * Reprocess needs a prior fully-captured sweep (so the upstream chunks are complete) and a plan that is
     * the clean single-edit case ({@link ReprocessPlanner}); everything else falls back to a full sweep, which
     * is the normal once-per-stream capture and always correct.
     */
    /**
     * Decide whether this step can be answered by materialising the <b>one record</b> it is about, rather
     * than re-running the edited element over the whole stream.
     * <p>
     * Two conditions. The step must name the record it wants - a REFRESH does, which is what an edit-then-look
     * cycle produces. And no filter may sit on the edited element or below it: deciding whether a record
     * matches such a filter means running the element to find out, so the target cannot be known in advance
     * and records have to be worked through in order instead. A filter on an element <i>above</i> the edit is
     * no obstacle at all - its output is already in the store under an unchanged fingerprint, so it is
     * evaluated by reading, exactly as it is today.
     *
     * @return the record to materialise, or null to reprocess the whole stream as before.
     */
    private RecordRange onDemandRangeFor(final PipelineStepRequest request,
                                         final Graph graph,
                                         final Decision decision,
                                         final StepDataStore store,
                                         final ElementFingerprints fingerprints,
                                         final long metaId) {
        final StepLocation located = onDemandTargetFor(request, graph, decision, store, metaId);
        if (located != null) {
            return RecordRange.of(located);
        }
        return filteredWindowFor(request, graph, decision, store, fingerprints, metaId);
    }

    /**
     * The next run of records to materialise for a <b>filtered</b> step, or null to reprocess the stream.
     * <p>
     * A filter makes "the next record" mean "the next one that matches", which cannot be known without
     * running records to find out. So rather than materialise one record, materialise a window of them in the
     * direction of travel and let the resolver scan it; if nothing in the window matches, the client's next
     * poll asks again and the window moves on.
     * <p>
     * Where the next window starts is <b>read back from the store</b> rather than remembered: the records
     * already materialised for this element at this fingerprint are the frontier, so the window simply starts
     * past them. That is what keeps this on one side of the {@code capture/}-{@code read/} line - the two
     * sides meet at the store, as everything else here does, instead of one driving the other round a loop.
     */
    // Package-private rather than private so a test can drive it directly. Its output is a range, which is
    // otherwise only visible through what a whole step happens to materialise - and the window size defaults
    // to the value it had as a constant, so a test that went through step() would pass whether or not the
    // size is really read from config.
    RecordRange filteredWindowFor(final PipelineStepRequest request,
                                  final Graph graph,
                                  final Decision decision,
                                  final StepDataStore store,
                                  final ElementFingerprints fingerprints,
                                  final long metaId) {
        final StepType stepType = request.getStepType();
        final boolean forward = stepType == StepType.FORWARD || stepType == StepType.FIRST;
        final boolean backward = stepType == StepType.BACKWARD || stepType == StepType.LAST;
        if (!forward && !backward) {
            return null;
        }
        final String fingerprint = fingerprints.getCumulativeFingerprint(decision.startElementId());
        if (fingerprint == null) {
            return null;
        }
        final StepLocation ref = request.getStepLocation();
        // Two coverages frame the scan: the stream's records say where a scan may reach, and the scanned
        // element's own coverage says where the last window ended - the frontier read back from the store
        // rather than remembered between polls.
        final Coverage stream = store.recordCoverage(() -> false);
        final Coverage scanned = store.elementCoverage(
                new ElementId(decision.startElementId()), fingerprint, () -> false);
        // A step that names no record starts at the end of the stream it is walking from.
        final List<Long> parts = stream.parts();
        if (parts.isEmpty()) {
            return null;
        }
        final long part = ref != null && ref.getMetaId() == metaId
                ? ref.getPartIndex()
                : (forward ? parts.getFirst() : parts.getLast());
        final long streamFirst = stream.first(part);
        final long streamLast = stream.last(part);
        if (streamFirst < 0 || streamLast < 0) {
            return null;
        }

        final long frontier = forward ? scanned.last(part) : scanned.first(part);
        final long start;
        if (frontier >= 0) {
            // Carry on from where an earlier window of this same scan finished.
            start = forward ? frontier + 1 : frontier - 1;
        } else if (ref != null && ref.getMetaId() == metaId && ref.getPartIndex() == part
                   && (stepType == StepType.FORWARD || stepType == StepType.BACKWARD)) {
            start = forward ? ref.getRecordIndex() + 1 : ref.getRecordIndex() - 1;
        } else {
            start = forward ? streamFirst : streamLast;
        }
        if (start < streamFirst || start > streamLast) {
            // The scan has run out of stream; the whole-stream path handles crossing to the next one.
            return null;
        }

        final int window = steppingConfig.getFilteredScanWindow();
        final long end = forward
                ? Math.min(streamLast, start + window - 1)
                : Math.max(streamFirst, start - window + 1);
        return forward
                ? new RecordRange(part, start, end)
                : new RecordRange(part, end, start);
    }

    private StepLocation onDemandTargetFor(final PipelineStepRequest request,
                                           final Graph graph,
                                           final Decision decision,
                                           final StepDataStore store,
                                           final long metaId) {
        if (isFilteredAtOrBelow(request, graph, decision.startElementId())) {
            return null;
        }
        final StepType stepType = request.getStepType();
        final StepLocation ref = request.getStepLocation();
        if (stepType == StepType.REFRESH) {
            return ref;
        }

        // Navigating rather than refreshing: the record wanted is not named, it has to be worked out. That
        // is only simple arithmetic while nothing is filtered - a filter makes "the next record" mean "the
        // next one that matches", which cannot be known without running records to find out. Any filter at
        // all, even above the edit, therefore falls back to reprocessing the stream.
        if (isAnyFilterApplied(request)) {
            // Handled by the windowed scan instead - see filteredWindowFor.
            return null;
        }
        final Coverage stream = store.recordCoverage(() -> false);
        final List<Long> parts = stream.parts();
        if (parts.isEmpty()) {
            return null;
        }
        return switch (stepType) {
            case FIRST -> locationIn(metaId, stream, parts.getFirst(), true);
            case LAST -> locationIn(metaId, stream, parts.getLast(), false);
            case FORWARD, BACKWARD -> neighbourOf(metaId, stream, ref, stepType == StepType.FORWARD);
            default -> null;
        };
    }

    private StepLocation locationIn(final long metaId,
                                    final Coverage stream,
                                    final long partIndex,
                                    final boolean first) {
        final long record = first ? stream.first(partIndex) : stream.last(partIndex);
        return record < 0 ? null : new StepLocation(metaId, partIndex, record);
    }

    /**
     * @return the record either side of {@code ref}, continuing into the neighbouring <b>part</b> when it
     * runs off the end of its own. Null once the stream itself runs out: crossing to another stream is the
     * resolver's job - the next stream may not even be swept yet - and it needs the whole-stream path.
     */
    private StepLocation neighbourOf(final long metaId,
                                     final Coverage stream,
                                     final StepLocation ref,
                                     final boolean forward) {
        if (ref == null || ref.getMetaId() != metaId) {
            return null;
        }
        final long part = ref.getPartIndex();
        final long candidate = ref.getRecordIndex() + (forward ? 1 : -1);
        // Bounds, not holds(): the question is whether the record EXISTS in the stream - a hole punched by
        // an earlier on-demand materialisation is still a legitimate record to step to and materialise.
        if (candidate >= stream.first(part) && candidate <= stream.last(part)) {
            return new StepLocation(metaId, part, candidate);
        }

        // Off the end of this part. A multi-part stream is one stream to the user, so stepping should carry
        // on into the next part rather than drop to reprocessing the whole stream to do it.
        final List<Long> parts = stream.parts();
        final int index = parts.indexOf(part);
        if (index < 0) {
            return null;
        }
        final int neighbourIndex = forward ? index + 1 : index - 1;
        if (neighbourIndex < 0 || neighbourIndex >= parts.size()) {
            return null;
        }
        return locationIn(metaId, stream, parts.get(neighbourIndex), forward);
    }

    private boolean isAnyFilterApplied(final PipelineStepRequest request) {
        return request.getStepFilterMap() != null
               && request.getStepFilterMap().values().stream()
                       .anyMatch(settings -> settings != null && settings.isFilterApplied());
    }

    private boolean isFilteredAtOrBelow(final PipelineStepRequest request,
                                        final Graph graph,
                                        final String startElementId) {
        if (request.getStepFilterMap() == null || request.getStepFilterMap().isEmpty()) {
            return false;
        }
        final Set<String> atOrBelow = new HashSet<>();
        collectAtOrBelow(graph, startElementId, atOrBelow);
        return atOrBelow.stream()
                .map(request::getStepFilterSettings)
                .anyMatch(settings -> settings != null && settings.isFilterApplied());
    }

    private void collectAtOrBelow(final Graph graph, final String elementId, final Set<String> collected) {
        if (!collected.add(elementId)) {
            return;
        }
        graph.parentsOf().forEach((child, parents) -> {
            if (parents.contains(elementId)) {
                collectAtOrBelow(graph, child, collected);
            }
        });
    }

    private StreamSweep launchFor(final String sessionId,
                                  final PipelineStepRequest request,
                                  final long metaId,
                                  final ElementFingerprints fingerprints,
                                  final List<StreamSweep> priorSweeps,
                                  final List<StreamSweep> running) {
        // True only if a prior sweep of this stream captured it in full WITHOUT error, so its upstream chunks
        // are complete and a whole-stream reprocess from them is safe. An errored sweep is also "fully
        // captured" (markError stops readers waiting) but its store is truncated, so it must NOT count -
        // reprocessing from it would silently serve a short stream.
        final boolean priorCompleteCapture = priorSweeps.stream().anyMatch(StreamSweep::isSuccessfullyCaptured);
        // A capture of this stream that is still running. Its records are arriving as we speak, so a step
        // whose demand it has not reached yet can wait on it instead of launching a second capture.
        final StreamSweep liveProducer = priorSweeps.stream()
                .filter(prior -> !prior.isFullyCaptured())
                .findFirst()
                .orElse(null);

        // What must the feed cover? A whole-stream reprocess (span == null) still requires a prior COMPLETE,
        // error-free capture: hasCompleteElement measures against the store's own extent, so without the
        // completion gate a capture truncated by an error would count as "complete" and a reprocess from it
        // would silently serve a short stream. But a step that names its record does not need the whole
        // stream - it needs the feed to HOLD that record, which a partial capture legitimately can. That is
        // what lets an edit land while the stream's first capture is still running (scenario C, the
        // behind-the-frontier half) instead of costing a full re-sweep.
        final StagePlanner.RecordSpan span = priorCompleteCapture ? null : namedRecordSpanFor(request, metaId);
        if (priorCompleteCapture || span != null) {
            final StepDataStore store = stepDataStoreManager.getOrCreateStore(sessionId, metaId);
            final Set<String> capturedElementIds = store.getCapturedElementIds();
            if (!capturedElementIds.isEmpty()) {
                final PipelineData pipelineData = pipelineDataHolderFactory
                        .create(request.getPipelineDoc()).getMergedPipelineData();
                final Graph graph = SteppingGraphBuilder.build(pipelineData, capturedElementIds);
                final Decision decision =
                        reprocessPlanner.plan(graph.elements(), graph.parentsOf(), store, fingerprints, span);
                if (decision.satisfied()) {
                    // Everything the step demands is already in the store - materialised by an earlier step
                    // or a previous loop iteration of this one. Launching anything would be waste; the
                    // resolver just needs a sweep signalling the records so it navigates them as usual.
                    final StepLocation ref = request.getStepLocation();
                    return signalledSweep(metaId, store, new RecordRange(
                            ref.getPartIndex(), ref.getRecordIndex(), ref.getRecordIndex()));
                }
                if (!decision.fullSweep()) {
                    final RecordRange range =
                            onDemandRangeFor(request, graph, decision, store, fingerprints, metaId);
                    // A plan made against a span is only good for materialising that span. If the range
                    // computation declined (e.g. the step turned out to need whole-stream work), a
                    // whole-stream reprocess from a feed only checked for a few records would truncate the
                    // stream - fall back to the sweep instead of risking it.
                    if (span != null && range == null) {
                        fullSweepLaunches.incrementAndGet();
                        return launchSweep(sessionId, request, metaId, fingerprints);
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
                            return satisfied;
                        }
                        for (final StreamSweep producer : running) {
                            if (producer.isOnDemand() && covers(producer.getDemand(), range)) {
                                return producer;
                            }
                        }
                    }
                    reprocessLaunches.incrementAndGet();
                    final StreamSweep sweep = launchReprocess(request, metaId, decision.startElementId(),
                            decision.feedElementId(), store, fingerprints, range);
                    sweep.setDemand(range);
                    return sweep;
                }
                // Nothing can be planned from what has been captured so far. Before falling back to a second
                // capture of this stream, see whether the one already running will answer this step shortly.
                final StreamSweep waiting = waitForFrontier(liveProducer, graph, store, fingerprints, span);
                if (waiting != null) {
                    return waiting;
                }
            }
        }
        fullSweepLaunches.incrementAndGet();
        return launchSweep(sessionId, request, metaId, fingerprints);
    }

    /**
     * Wait for the capture already running, rather than start a second one?
     * <p>
     * Yes when the step names records the running capture simply has not reached yet. Its output for those
     * records will be exactly what a reprocess needs - an edit below the record boundary does not change the
     * fingerprints it is writing above - so the work is already being done, and duplicating it would parse the
     * same stream twice to produce the same chunks.
     * <p>
     * The test is a question the planner can actually answer: <b>would this step be answerable about a record
     * the capture has already produced?</b> If planning against the frontier yields a reprocess, the only thing
     * missing is the records, and they are on their way. If it yields a full sweep, the edit changed something
     * this capture cannot supply (a parser change re-keys everything) and waiting would only delay the sweep
     * that is genuinely needed.
     *
     * @return a handle to wait on, or null to go ahead and launch.
     */
    private StreamSweep waitForFrontier(final StreamSweep liveProducer,
                                        final Graph graph,
                                        final StepDataStore store,
                                        final ElementFingerprints fingerprints,
                                        final StagePlanner.RecordSpan span) {
        if (liveProducer == null || span == null) {
            return null;
        }
        final long frontier = liveProducer.coverage().last(span.partIndex());
        if (frontier < 0) {
            // Nothing captured for this part yet, so there is no record to test the plan against.
            return null;
        }
        final Decision atFrontier = reprocessPlanner.plan(graph.elements(), graph.parentsOf(), store,
                fingerprints, new StagePlanner.RecordSpan(span.partIndex(), frontier, frontier));
        if (atFrontier.fullSweep()) {
            return null;
        }
        LOGGER.debug(() -> "launchFor() - waiting for the running capture to reach " + span);
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
        //
        return signalledSweep(metaId, store, range);
    }

    /**
     * A completed on-demand sweep over records that are already in the store: nothing runs, the records are
     * just signalled into the watermark so the resolver navigates them exactly as it would a fresh
     * materialisation's.
     */
    private StreamSweep signalledSweep(final long metaId, final StepDataStore store, final RecordRange range) {
        final StreamSweep sweep = new StreamSweep(metaId, store);
        sweep.setOnDemand("(already materialised)");
        sweep.setDemand(range);
        for (long record = range.firstRecord(); record <= range.lastRecord(); record++) {
            sweep.recordCaptured(new StepLocation(metaId, range.partIndex(), record));
        }
        sweep.markFullyCaptured();
        return sweep;
    }

    /**
     * @return true if a producer's demand contains every record of the asked range - same part, asked range
     * within the demanded one.
     */
    private boolean covers(final RecordRange demand, final RecordRange asked) {
        return demand != null
               && demand.partIndex() == asked.partIndex()
               && demand.firstRecord() <= asked.firstRecord()
               && demand.lastRecord() >= asked.lastRecord();
    }

    /**
     * The span a step demands, when it names one: an unfiltered-or-filtered REFRESH is about exactly the
     * record it points at. Navigation names no record (its target is worked out from captured extents, which
     * a partial capture cannot yet answer), so everything else returns null and keeps the whole-stream
     * requirement.
     */
    private StagePlanner.RecordSpan namedRecordSpanFor(final PipelineStepRequest request, final long metaId) {
        final StepLocation ref = request.getStepLocation();
        if (request.getStepType() == StepType.REFRESH && ref != null && ref.getMetaId() == metaId) {
            return new StagePlanner.RecordSpan(ref.getPartIndex(), ref.getRecordIndex(), ref.getRecordIndex());
        }
        return null;
    }

    /**
     * @return the number of stream fills served by reprocessing just the changed elements (edit reuse).
     */
    /**
     * @return how many steps were answered by materialising a single record rather than re-running the
     * edited element over the whole stream. For metrics and tests.
     */
    public long getOnDemandLaunchCount() {
        return onDemandLaunches.get();
    }

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
     * Launch an asynchronous reprocess of just the changed elements into the stream's existing store, feeding
     * them {@code feedElementId}'s already-captured output. The reprocessed chunks land under their new
     * fingerprints alongside the reused upstream ones, so a later step reads both from the one store. As with
     * {@link #launchSweep}, every way the task can end signals the sweep.
     */
    private StreamSweep launchReprocess(final PipelineStepRequest request,
                                        final long metaId,
                                        final String startElementId,
                                        final String feedElementId,
                                        final StepDataStore store,
                                        final ElementFingerprints fingerprints,
                                        final RecordRange onDemandRange) {
        // The reprocess reads the feed's output from, and writes the reprocessed chunks to, the same store.
        final StreamSweep sweep = new StreamSweep(metaId, store, fingerprints);
        if (onDemandRange != null) {
            // Materialising one record rather than capturing the stream: what this sweep can answer is
            // decided by what the store holds, not by a contiguous range.
            sweep.setOnDemand(startElementId);
            onDemandLaunches.incrementAndGet();
        }
        final Executor executor = executorProvider.get(THREAD_POOL);
        try {
            CompletableFuture
                    .runAsync(taskContextFactory.context("Stepping reprocess", taskContext ->
                            reprocessDriverProvider.get().reprocess(taskContext, request, metaId,
                                    startElementId, feedElementId, store, sweep, fingerprints,
                                    MidPipelineScope.ELEMENT_AND_DESCENDANTS, onDemandRange)), executor)
                    .whenComplete((unused, t) -> {
                        if (t != null) {
                            sweep.markError(t);
                        } else if (!sweep.isFullyCaptured()) {
                            sweep.markError(new RuntimeException(
                                    "Stepping reprocess of stream " + metaId + " ended without completing"));
                        }
                    });
        } catch (final RuntimeException e) {
            sweep.markError(e);
        }
        return sweep;
    }

    private void closeSession(final SteppingSession session) {
        session.getActiveSweeps().forEach(this::terminateSweep);
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
