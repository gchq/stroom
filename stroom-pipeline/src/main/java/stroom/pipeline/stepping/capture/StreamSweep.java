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

package stroom.pipeline.stepping.capture;

import stroom.pipeline.shared.stepping.StepLocation;
import stroom.pipeline.stepping.fingerprint.ElementFingerprints;
import stroom.pipeline.stepping.store.Coverage;
import stroom.pipeline.stepping.store.StepDataStore;
import stroom.task.api.TaskContext;
import stroom.util.shared.ElementId;
import stroom.util.shared.Indicators;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The asynchronous capture of a single stream: it owns the stream's {@link StepDataStore}, the
 * {@link CaptureWatermark} that says how far the capture has got, and the task handle its session uses to
 * terminate it. It also remembers the per-stream facts a step result needs that belong to no single record.
 * <p>
 * Progress and terminal state live in the watermark - see {@link CaptureWatermark} for the rule that every way
 * a producer can stop must wake its waiters. The methods here delegate, so callers that only care about
 * progress can hold the watermark instead of the whole sweep.
 */
public class StreamSweep {

    /**
     * Holds nothing, ever. The extent is deliberately NOT final: a wait handle's whole purpose is that more is
     * coming, and its producer's own {@link #isFullyCaptured()} is what says when that stops being true.
     */
    private static final Coverage EMPTY_COVERAGE = new Coverage() {
        @Override
        public List<Long> parts() {
            return List.of();
        }

        @Override
        public long first(final long partIndex) {
            return Coverage.NONE;
        }

        @Override
        public long last(final long partIndex) {
            return Coverage.NONE;
        }

        @Override
        public boolean holds(final long partIndex, final long recordIndex) {
            return false;
        }

        @Override
        public boolean isExtentFinal() {
            return false;
        }
    };

    private final long metaId;
    private final StepDataStore store;
    private final CaptureWatermark watermark;
    // The configuration this sweep captures under. Held so that the session can ask whether a later edit
    // invalidates any of what it is producing - see SteppingSession.sweepFor. Null only where no caller asks
    // (the synchronous capture entry points and unit tests).
    private final ElementFingerprints fingerprints;
    // True for a handle on somebody else's producer rather than a producer of its own - see waitingOn.
    private final boolean waitHandle;
    // Non-null for a sweep the session must own and keep but never serve a step from directly - a backbone,
    // cached under this key instead of the step's fingerprint signature so no step lookup can ever hit it.
    private volatile String cacheKey;
    private volatile ReprocessDriver.RecordRange demand;

    // Non-null when this sweep materialises individual records of one element on demand rather than
    // capturing the stream. Such a sweep holds only the records the user has actually visited, so what it
    // can answer is a question about the store rather than about a contiguous range.
    private volatile String onDemandElementId;

    // Set when the async capture task is launched, so the owning session can terminate it on close.
    private volatile TaskContext taskContext;

    // Set by the session when it wants this sweep to stop. Read by the capture task once it has published
    // its task context, closing the window where a close() sees a null context and skips termination.
    private volatile boolean terminateRequested;

    // Per-stream state that a step result needs but that is not part of any single record's IO. The live
    // path reads these off the handler after its one-shot run; a capture has to remember them because the
    // run that produced them is long gone by the time a step is served from the store.
    private final Map<Long, Boolean> segmentedByPart = new ConcurrentHashMap<>();
    private volatile Map<ElementId, Indicators> startProcessIndicators = Map.of();

    public StreamSweep(final long metaId, final StepDataStore store) {
        this(metaId, store, null);
    }

    public StreamSweep(final long metaId, final StepDataStore store, final ElementFingerprints fingerprints) {
        this(metaId, store, fingerprints, new CaptureWatermark(), false);
    }

    private StreamSweep(final long metaId,
                        final StepDataStore store,
                        final ElementFingerprints fingerprints,
                        final CaptureWatermark watermark,
                        final boolean waitHandle) {
        this.metaId = metaId;
        this.store = store;
        this.fingerprints = fingerprints;
        this.watermark = watermark;
        this.waitHandle = waitHandle;
    }

    /**
     * A handle on a producer that <b>somebody else</b> launched: it shares that producer's progress signal but
     * serves nothing of its own.
     * <p>
     * This is how a step whose records are still ahead of a running capture's frontier waits for the work
     * already in flight instead of launching a second capture of the same stream. Its {@link #coverage()} is
     * deliberately empty: the producer is capturing under a different configuration to the one this step is
     * being served under, so nothing it has written is servable here - only its <i>progress</i> is of interest.
     * Serving from the producer's own coverage would show the user a record with the edited element's pane
     * blank, which is precisely the half-truth the store's fingerprint keying exists to prevent.
     * <p>
     * A handle owns nothing, so it is never cached and never terminated; the producer's own lifecycle is
     * managed where it was launched.
     */
    public static StreamSweep waitingOn(final StreamSweep producer) {
        return new StreamSweep(producer.metaId, producer.store, producer.fingerprints, producer.watermark, true);
    }

    public long getMetaId() {
        return metaId;
    }

    /**
     * @return the fingerprints this sweep captures under, or null if it was created without them.
     */
    public ElementFingerprints getFingerprints() {
        return fingerprints;
    }

    /**
     * @return true if this is a handle on another producer rather than one in its own right - see
     * {@link #waitingOn}.
     */
    public boolean isWaitHandle() {
        return waitHandle;
    }

    /**
     * Give this sweep its own cache identity, distinct from the fingerprint signature of the step that
     * launched it. A backbone captures only the record-boundary elements, so a record it has "captured" is
     * <b>not servable</b> as a step - every below-boundary pane would be blank. Caching it under the step's
     * signature would hand it straight to the next step under the same code; caching it under its own key
     * means a step lookup can never hit it, while the session still owns it (termination, the swept-stream
     * cap, and offering it to the launcher as prior work to build on).
     */
    public void setCacheKey(final String cacheKey) {
        this.cacheKey = cacheKey;
    }

    public String getCacheKey() {
        return cacheKey;
    }

    public StepDataStore getStore() {
        return store;
    }

    /**
     * @return how far this sweep has got. A consumer that follows this sweep's progress needs only this, not
     * the sweep itself.
     */
    /**
     * Mark this as materialising records of {@code elementId} on demand. See
     * {@code stepping-design.md} §11.
     */
    public void setOnDemand(final String elementId) {
        this.onDemandElementId = elementId;
    }

    /**
     * The records an on-demand materialisation was launched to produce. What lets a second step attach to a
     * producer already making the records it wants instead of double-launching - the store cannot answer
     * that, because the records are precisely the ones not written yet.
     */
    public void setDemand(final ReprocessDriver.RecordRange demand) {
        this.demand = demand;
    }

    public ReprocessDriver.RecordRange getDemand() {
        return demand;
    }

    public boolean isOnDemand() {
        return onDemandElementId != null;
    }

    public CaptureWatermark getWatermark() {
        return watermark;
    }

    /**
     * Signal that a record has been fully committed to the store (advances the progress version).
     */
    public void recordCaptured(final StepLocation location) {
        watermark.recordCaptured(location);
    }

    /**
     * @return the first (lowest) record index this sweep has captured for the part, or -1 if none yet.
     */
    public long getCapturedFirstRecordIndex(final long partIndex) {
        return watermark.getCapturedFirstRecordIndex(partIndex);
    }

    /**
     * @return the last (highest) record index this sweep has captured for the part, or -1 if none yet.
     */
    public long getCapturedLastRecordIndex(final long partIndex) {
        return watermark.getCapturedLastRecordIndex(partIndex);
    }

    /**
     * This sweep's {@link Coverage}: what the producer has <b>signalled</b> captured, which readers must
     * navigate by rather than by the store - the store already holds a record's bytes for a moment before the
     * producer signals it whole, and a reprocess writes into a store whose reused upstream spans far past
     * what this sweep has re-produced. Contiguous per part (a sweep walks the stream in order), so holding is
     * answered from the bounds; the extent is final exactly when capture has ended, in success or failure -
     * an errored sweep adds no more records either, and saying otherwise would leave a waiter hanging (§5,
     * everything must signal).
     */
    public Coverage coverage() {
        if (waitHandle) {
            // Nothing of the producer's is servable under the configuration this handle was handed out for.
            return EMPTY_COVERAGE;
        }
        return new Coverage() {
            @Override
            public List<Long> parts() {
                return watermark.getCapturedPartIndices();
            }

            @Override
            public long first(final long partIndex) {
                return watermark.getCapturedFirstRecordIndex(partIndex);
            }

            @Override
            public long last(final long partIndex) {
                return watermark.getCapturedLastRecordIndex(partIndex);
            }

            @Override
            public boolean holds(final long partIndex, final long recordIndex) {
                final long first = first(partIndex);
                return first >= 0 && recordIndex >= first && recordIndex <= last(partIndex);
            }

            @Override
            public boolean isExtentFinal() {
                return watermark.isFullyCaptured();
            }
        };
    }

    public void markFullyCaptured() {
        watermark.markFullyCaptured();
    }

    public void markError(final Throwable t) {
        watermark.markError(t);
    }

    public long getVersion() {
        return watermark.getVersion();
    }

    public boolean isFullyCaptured() {
        return watermark.isFullyCaptured();
    }

    /**
     * @return true only if this sweep captured the whole stream <b>without error</b>. See
     * {@link CaptureWatermark#isSuccessfullyCaptured()} - a caller deciding whether the captured chunks can be
     * reused (e.g. to reprocess from them) must use this rather than {@link #isFullyCaptured()}.
     */
    public boolean isSuccessfullyCaptured() {
        return watermark.isSuccessfullyCaptured();
    }

    public Throwable getError() {
        return watermark.getError();
    }

    public StepLocation getLastCapturedLocation() {
        return watermark.getLastCapturedLocation();
    }

    /**
     * Wait until progress is made past {@code knownVersion}, the sweep stops, or the timeout elapses.
     *
     * @param knownVersion the version observed before reading the store.
     * @param timeoutMs    the maximum time to wait.
     * @return true if progress/completion occurred, false if the timeout elapsed or the wait was interrupted.
     */
    public boolean awaitChangeSince(final long knownVersion, final long timeoutMs) {
        return watermark.awaitChangeSince(knownVersion, timeoutMs);
    }

    /**
     * Wait until the whole stream has been captured (or the capture errored), or the timeout elapses.
     *
     * @return true if the capture finished, false on timeout.
     */
    public boolean awaitFullyCaptured(final long timeoutMs) {
        return watermark.awaitFullyCaptured(timeoutMs);
    }

    void setTaskContext(final TaskContext taskContext) {
        this.taskContext = taskContext;
    }

    public TaskContext getTaskContext() {
        return taskContext;
    }

    /**
     * Ask this sweep to stop. The capture task may not have published its {@link TaskContext} yet, so this
     * flag is the other half of a handshake: the session sets it <em>before</em> reading
     * {@link #getTaskContext()}, and the capture task publishes its context <em>before</em> reading this
     * flag. Whichever order the two threads run in, at least one of them sees the other's write, so a sweep
     * can never start (or keep running) after its session has been closed.
     */
    public void requestTerminate() {
        terminateRequested = true;
    }

    public boolean isTerminateRequested() {
        return terminateRequested;
    }

    /**
     * Record whether a part's data is segmented. Held per part, not per stream, because the live path
     * reports the flag for the part holding the found record - the last part processed would be wrong.
     */
    void setSegmented(final long partIndex, final boolean segmented) {
        segmentedByPart.put(partIndex, segmented);
    }

    public boolean isSegmented(final long partIndex) {
        return Boolean.TRUE.equals(segmentedByPart.get(partIndex));
    }

    /**
     * Indicators raised while the pipeline was starting up, before any record was processed. They belong to
     * the stream rather than to a record, and are merged into whichever record a step resolves to.
     */
    void setStartProcessIndicators(final Map<ElementId, Indicators> indicators) {
        this.startProcessIndicators = indicators == null ? Map.of() : Map.copyOf(indicators);
    }

    public Map<ElementId, Indicators> getStartProcessIndicators() {
        return startProcessIndicators;
    }

    /**
     * Take another sweep's per-stream facts. A materialisation runs no whole-stream capture, so it never
     * learns whether a part is segmented or what indicators the pipeline raised while starting up - but the
     * capture that swept this same stream did, and stream-level facts do not change with the code being
     * edited. Without this, every record served from a materialisation reports its part unsegmented and its
     * startup indicators absent.
     */
    public void copyStreamMetadataFrom(final StreamSweep source) {
        segmentedByPart.putAll(source.segmentedByPart);
        if (!source.startProcessIndicators.isEmpty()) {
            startProcessIndicators = source.startProcessIndicators;
        }
    }
}
