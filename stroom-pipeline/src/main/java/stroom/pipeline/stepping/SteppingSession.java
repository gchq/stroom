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

import stroom.pipeline.shared.stepping.PipelineStepRequest;

import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.function.Consumer;

/**
 * A durable stepping session scoped to one pipeline + stream selection. It owns the ordered list of
 * candidate stream ids and lazily sweeps them: a {@link StreamSweep} is launched for a stream only the
 * first time a step targets it (never all streams up front), via the supplied {@link SweepLauncher}.
 * The session outlives an individual step - it is torn down only by terminate, idle reap, or close.
 * {@link #close()} cancels sweeps and deletes the persisted data.
 * <p>
 * The user edits code while stepping, and an edit changes the fingerprints that key the captured IO. A
 * sweep is therefore identified by <em>both</em> the stream and the fingerprint signature it captured
 * under, and {@link #refresh} re-points the session at the current code on every step. Superseded sweeps
 * that have already completed are kept: reverting an edit restores the previous signature, finds that
 * sweep still cached, and serves the stream with no reprocessing at all. The underlying
 * {@link StepDataStore} is shared by every sweep of a stream and keys IO by fingerprint, so the elements
 * an edit did not touch are reused rather than recaptured.
 */
public class SteppingSession {

    private final String sessionId;
    private final List<Long> streamIdList;
    private final SweepLauncher launcher;
    private final Consumer<SteppingSession> onClose;
    private final Consumer<StreamSweep> onTerminateSweep;
    private final int maxSweptStreams;

    private final Map<SweepKey, StreamSweep> sweeps = new HashMap<>();
    private volatile Instant lastAccessTime = Instant.now();

    // The code/config this session is currently stepping. Updated by refresh() on every step.
    private PipelineStepRequest currentRequest;
    private ElementFingerprints currentFingerprints;
    private Map<String, String> currentSignature;

    // Guards sweep creation against teardown. StepDataStoreManager requires that a store is never created
    // for a session that is being deleted (a create racing a delete re-creates the map entry and directory
    // that the delete just removed, leaking its channels and temp dir forever). This session is the owner
    // that serialises the two, so ensureStreamSwept and close() are mutually exclusive. It also guards the
    // sweeps map and the current-request fields.
    private final Object lifecycleLock = new Object();
    private boolean closed;

    public SteppingSession(final String sessionId,
                           final List<Long> streamIdList,
                           final PipelineStepRequest request,
                           final ElementFingerprints fingerprints,
                           final SweepLauncher launcher,
                           final Consumer<SteppingSession> onClose,
                           final Consumer<StreamSweep> onTerminateSweep,
                           final int maxSweptStreams) {
        this.sessionId = sessionId;
        this.streamIdList = List.copyOf(streamIdList);
        this.launcher = launcher;
        this.onClose = onClose;
        this.onTerminateSweep = onTerminateSweep;
        this.maxSweptStreams = maxSweptStreams;
        this.currentRequest = request;
        this.currentFingerprints = fingerprints;
        this.currentSignature = fingerprints.getCumulativeFingerprints();
    }

    /**
     * Point the session at the code/config of the step about to be served. If the fingerprints changed (the
     * user edited an element), any sweep still capturing under the old signature is abandoned - its output
     * can no longer be reached - but completed ones are kept so that reverting the edit is free.
     *
     * @throws IllegalStateException if the session has been closed.
     */
    public void refresh(final PipelineStepRequest request, final ElementFingerprints fingerprints) {
        synchronized (lifecycleLock) {
            checkNotClosed();
            lastAccessTime = Instant.now();
            currentRequest = request;

            final Map<String, String> signature = fingerprints.getCumulativeFingerprints();
            if (!signature.equals(currentSignature)) {
                sweeps.forEach((key, sweep) -> {
                    if (!key.signature().equals(signature) && !sweep.isComplete()) {
                        onTerminateSweep.accept(sweep);
                    }
                });
                currentSignature = signature;
                currentFingerprints = fingerprints;
            }
        }
    }

    /**
     * Get (launching a sweep the first time) the sweep for a stream under the session's current
     * fingerprints.
     *
     * @throws IllegalStateException if the session has been closed.
     */
    public StreamSweep ensureStreamSwept(final long metaId) {
        synchronized (lifecycleLock) {
            checkNotClosed();
            lastAccessTime = Instant.now();

            final SweepKey key = new SweepKey(metaId, currentSignature);
            final StreamSweep existing = sweeps.get(key);
            if (existing != null) {
                return existing;
            }
            if (!isStreamSwept(metaId) && countSweptStreams() >= maxSweptStreams) {
                throw new StepDataStoreException(
                        "This stepping session has already swept the maximum of " + maxSweptStreams
                        + " streams; narrow your selection");
            }
            final StreamSweep sweep = launcher.launch(metaId, currentRequest, currentFingerprints);
            sweeps.put(key, sweep);
            return sweep;
        }
    }

    private boolean isStreamSwept(final long metaId) {
        return sweeps.keySet().stream().anyMatch(key -> key.metaId() == metaId);
    }

    private long countSweptStreams() {
        return sweeps.keySet().stream().map(SweepKey::metaId).distinct().count();
    }

    private void checkNotClosed() {
        if (closed) {
            throw new IllegalStateException("Stepping session " + sessionId + " has been closed");
        }
    }

    /**
     * @return true if the stream is one of this session's candidate streams.
     */
    public boolean containsStream(final long metaId) {
        return streamIdList.contains(metaId);
    }

    /**
     * @return true if this session was created for the same stream selection, i.e. it can serve the request.
     */
    public boolean matchesSelection(final List<Long> streamIds) {
        return streamIdList.equals(streamIds);
    }

    public String getSessionId() {
        return sessionId;
    }

    public List<Long> getStreamIdList() {
        return streamIdList;
    }

    public ElementFingerprints getFingerprints() {
        synchronized (lifecycleLock) {
            return currentFingerprints;
        }
    }

    public Collection<StreamSweep> getActiveSweeps() {
        synchronized (lifecycleLock) {
            return List.copyOf(sweeps.values());
        }
    }

    public Instant getLastAccessTime() {
        return lastAccessTime;
    }

    /**
     * Terminate this session's sweeps and delete its persisted data. Idempotent. Held under the lifecycle
     * lock so that a reader crossing a stream boundary cannot launch a new sweep (and re-create the store
     * directory) while teardown is in progress.
     */
    public void close() {
        synchronized (lifecycleLock) {
            if (closed) {
                return;
            }
            closed = true;
            if (onClose != null) {
                onClose.accept(this);
            }
        }
    }

    public boolean isClosed() {
        synchronized (lifecycleLock) {
            return closed;
        }
    }

    // --- stream neighbour navigation ------------------------------------------------------------

    public OptionalLong firstStreamId() {
        return streamIdList.isEmpty() ? OptionalLong.empty() : OptionalLong.of(streamIdList.get(0));
    }

    public OptionalLong lastStreamId() {
        return streamIdList.isEmpty()
                ? OptionalLong.empty()
                : OptionalLong.of(streamIdList.get(streamIdList.size() - 1));
    }

    public OptionalLong nextStreamId(final long metaId) {
        final int idx = streamIdList.indexOf(metaId);
        return (idx >= 0 && idx + 1 < streamIdList.size())
                ? OptionalLong.of(streamIdList.get(idx + 1))
                : OptionalLong.empty();
    }

    public OptionalLong prevStreamId(final long metaId) {
        final int idx = streamIdList.indexOf(metaId);
        return (idx > 0)
                ? OptionalLong.of(streamIdList.get(idx - 1))
                : OptionalLong.empty();
    }

    // --------------------------------------------------------------------------------

    /**
     * Identifies a sweep by the stream it captured and the fingerprint signature it captured under, so that
     * an edit starts a new sweep while the pre-edit one stays available for a revert.
     */
    private record SweepKey(long metaId, Map<String, String> signature) {

    }

    /**
     * Launches (starts capturing) a stream's sweep. Supplied by the owner (e.g. {@code SteppingService}),
     * bound to the session's current request and fingerprints, which change as the user edits code.
     */
    @FunctionalInterface
    public interface SweepLauncher {

        StreamSweep launch(long metaId, PipelineStepRequest request, ElementFingerprints fingerprints);
    }
}
