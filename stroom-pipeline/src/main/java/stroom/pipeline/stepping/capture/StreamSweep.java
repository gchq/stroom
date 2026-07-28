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
import stroom.pipeline.stepping.store.StepDataStore;
import stroom.task.api.TaskContext;
import stroom.util.shared.ElementId;
import stroom.util.shared.Indicators;

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

    private final long metaId;
    private final StepDataStore store;
    private final CaptureWatermark watermark = new CaptureWatermark();

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
        this.metaId = metaId;
        this.store = store;
    }

    public long getMetaId() {
        return metaId;
    }

    public StepDataStore getStore() {
        return store;
    }

    /**
     * @return how far this sweep has got. A consumer that follows this sweep's progress needs only this, not
     * the sweep itself.
     */
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
}
