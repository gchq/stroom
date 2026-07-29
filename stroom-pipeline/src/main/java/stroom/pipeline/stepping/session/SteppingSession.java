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

package stroom.pipeline.stepping.session;

import stroom.pipeline.shared.stepping.PipelineStepRequest;
import stroom.pipeline.stepping.capture.StreamSweep;
import stroom.pipeline.stepping.fingerprint.ElementFingerprints;
import stroom.pipeline.stepping.store.StepDataStore;
import stroom.pipeline.stepping.store.StepDataStoreException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * A durable stepping session scoped to one pipeline + stream selection. It owns the ordered list of
 * candidate stream ids and lazily sweeps them: a {@link StreamSweep} is launched for a stream only the
 * first time a step targets it (never all streams up front), via the supplied {@link SweepLauncher}.
 * The session outlives an individual step - it is torn down only by terminate, idle reap, or close.
 * {@link #close()} cancels sweeps and deletes the persisted data.
 * <p>
 * The user edits code while stepping, and an edit changes the fingerprints that key the captured IO. A
 * sweep is therefore identified by <em>both</em> the stream and the fingerprint signature it captured
 * under. Superseded sweeps that have already completed are kept: reverting an edit restores the previous
 * signature, finds that sweep still cached, and serves the stream with no reprocessing at all. The
 * underlying {@link StepDataStore} is shared by every sweep of a stream and keys IO by fingerprint, so the
 * elements an edit did not touch are reused rather than recaptured.
 * <p>
 * The session holds no notion of a "current" request or fingerprints: every caller passes what it wants to
 * {@link #sweepFor}. It is a cache plus a lifecycle owner, nothing more.
 */
public class SteppingSession {

    private final String sessionId;
    private final List<Long> streamIdList;
    private final SweepLauncher launcher;
    private final Consumer<SteppingSession> onClose;
    private final Consumer<StreamSweep> onTerminateSweep;
    private final int maxSweptStreams;

    private final Map<SweepKey, StreamSweep> sweeps = new HashMap<>();
    // In-flight materialisations by (stream, signature). A registry of RUNNING producers, not a cache:
    // entries leave when their producer finishes, because the store is the only cache of materialised
    // records. Only ever touched under the lifecycle lock.
    private final Map<SweepKey, List<StreamSweep>> running = new HashMap<>();
    private volatile Instant lastAccessTime = Instant.now();

    // Guards sweep creation against teardown. StepDataStoreManager requires that a store is never created
    // for a session that is being deleted (a create racing a delete re-creates the map entry and directory
    // that the delete just removed, leaking its channels and temp dir forever). This session is the owner
    // that serialises the two, so sweepFor and close() are mutually exclusive. It also guards the sweeps map.
    private final Object lifecycleLock = new Object();
    private boolean closed;

    public SteppingSession(final String sessionId,
                           final List<Long> streamIdList,
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
    }

    /**
     * Get the sweep capturing a stream under the given configuration, launching it if this is the first
     * step to need it. Streams are swept on demand, never all up front.
     * <p>
     * Cached by {@code (stream, fingerprint signature)}, so an edit starts a new sweep while the pre-edit
     * one stays available - reverting the edit asks for the old signature again and gets the completed
     * sweep back for free.
     *
     * @throws IllegalStateException if the session has been closed.
     */
    public StreamSweep sweepFor(final long metaId,
                                final PipelineStepRequest request,
                                final ElementFingerprints fingerprints) {
        synchronized (lifecycleLock) {
            checkNotClosed();
            lastAccessTime = Instant.now();

            final SweepKey streamKey = new SweepKey(metaId, fingerprints.getSignature());
            final StreamSweep existing = sweeps.get(streamKey);
            if (existing != null) {
                return existing;
            }
            // A finished materialisation leaves the registry: its results live in the store, which is the
            // only cache of them - the launcher answers a repeated demand from the store's coverage, so
            // holding a dead producer here would add nothing and keep its error (if any) alive.
            running.values().forEach(list -> list.removeIf(StreamSweep::isFullyCaptured));
            running.values().removeIf(List::isEmpty);

            // This stream is wanted under different code, so a sweep of it still running under the old code
            // is now pointless - nothing will read its output. Abandon it rather than let it finish reading
            // a whole stream for nobody. It must also be dropped from the cache, not merely terminated: a
            // terminated sweep is an errored one, and leaving it here would make reverting the edit serve
            // that error instead of re-capturing.
            sweeps.entrySet().removeIf(entry -> {
                if (entry.getKey().metaId() == metaId && !entry.getValue().isFullyCaptured()) {
                    onTerminateSweep.accept(entry.getValue());
                    return true;
                }
                return false;
            });
            // Same rule for in-flight materialisations - but scoped to the signature explicitly. A full
            // sweep under the current signature never reaches this line (the cache hit returned it above);
            // a materialisation always gets here, because it is never cached, so without the signature guard
            // this would abandon the very producer the step is about to attach to.
            running.entrySet().removeIf(entry -> {
                if (entry.getKey().metaId() == metaId
                    && !entry.getKey().signature().equals(fingerprints.getSignature())) {
                    entry.getValue().forEach(onTerminateSweep);
                    return true;
                }
                return false;
            });

            if (!isStreamSwept(metaId) && countSweptStreams() >= maxSweptStreams) {
                throw new StepDataStoreException(
                        "This stepping session has already swept the maximum of " + maxSweptStreams
                        + " streams; narrow your selection");
            }
            // True only if a prior sweep of this stream captured it in full WITHOUT error, so its upstream
            // chunks are complete and the launcher may reprocess from them rather than re-sweep. An errored
            // sweep is also "fully captured" (markError stops readers waiting) but its store is truncated, so
            // it must NOT count here - reprocessing from it would silently serve a short stream. A still
            // in-flight prior sweep was dropped by the removeIf above, so it does not count either.
            final boolean priorCompleteCapture = sweeps.values().stream()
                    .anyMatch(prior -> prior.getMetaId() == metaId && prior.isSuccessfullyCaptured());
            final StreamSweep sweep = launcher.launch(metaId, request, fingerprints, priorCompleteCapture,
                    List.copyOf(running.getOrDefault(streamKey, List.of())));
            // The two producer kinds part ways here. A sweep that captures the stream (full sweep or
            // whole-stream reprocess) is cached by signature - durable, revert-friendly, exactly as before.
            // A materialisation has NO identity cache at all: its results are in the store, which answers a
            // repeated demand by coverage, so all the session tracks is that it is RUNNING - the launcher
            // uses that to attach a step to a producer already making its records rather than double-launch.
            if (sweep.isOnDemand()) {
                if (!sweep.isFullyCaptured()) {
                    running.computeIfAbsent(streamKey, k -> new ArrayList<>()).add(sweep);
                }
            } else {
                sweeps.put(streamKey, sweep);
            }
            return sweep;
        }
    }

    private boolean isStreamSwept(final long metaId) {
        return sweeps.keySet().stream().anyMatch(key -> key.metaId() == metaId)
               || running.keySet().stream().anyMatch(key -> key.metaId() == metaId);
    }

    private long countSweptStreams() {
        return Stream.concat(sweeps.keySet().stream(), running.keySet().stream())
                .map(SweepKey::metaId)
                .distinct()
                .count();
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

    public Collection<StreamSweep> getActiveSweeps() {
        synchronized (lifecycleLock) {
            final List<StreamSweep> all = new ArrayList<>(sweeps.values());
            running.values().forEach(all::addAll);
            return all;
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
    /**
     * Identifies a cached sweep. {@code onDemandRecord} is null for a sweep that captured a stream, and the
     * record index for one that materialised a single record - those are only interchangeable for the record
     * they actually hold.
     */
    private record SweepKey(long metaId, String signature) {

    }

    /**
     * Launches (starts filling) a stream's sweep. Supplied by the owner (e.g. {@code SteppingService}), bound
     * to the session's current request and fingerprints, which change as the user edits code.
     *
     * @param priorCompleteCapture true if this stream already has a fully-captured sweep in this session, so
     *                             its upstream chunks are complete and the launcher may reprocess just the
     *                             changed elements from them instead of re-sweeping the whole stream.
     */
    @FunctionalInterface
    public interface SweepLauncher {

        /**
         * @param running the materialisations currently in flight for this stream under these fingerprints,
         *                so the launcher can attach a step to a producer already making the records it wants
         *                instead of launching a duplicate.
         */
        StreamSweep launch(long metaId,
                           PipelineStepRequest request,
                           ElementFingerprints fingerprints,
                           boolean priorCompleteCapture,
                           List<StreamSweep> running);
    }
}
