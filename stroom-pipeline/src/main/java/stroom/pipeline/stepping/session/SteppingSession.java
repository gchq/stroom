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
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

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
 * under. Superseded sweeps are kept: a completed one means reverting an edit restores the previous
 * signature, finds that sweep still cached, and serves the stream with no reprocessing at all - and a
 * <em>running</em> one is kept too unless the edit invalidates everything it is producing (see
 * {@code stillProduces}), because its upstream output is exactly what the edited element will be replayed
 * from. The underlying {@link StepDataStore} is shared by every sweep of a stream and keys IO by
 * fingerprint, so the elements an edit did not touch are reused rather than recaptured.
 * <p>
 * The session holds no notion of a "current" request or fingerprints: every caller passes what it wants to
 * {@link #sweepFor}. It is a cache plus a lifecycle owner, nothing more.
 */
public class SteppingSession {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(SteppingSession.class);

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
            running.values().forEach(list -> list.removeIf(StreamSweep::hasEnded));
            running.values().removeIf(List::isEmpty);

            // An in-flight capture of this stream is abandoned ONLY if the new configuration invalidates
            // everything it is producing (see stillProduces). A downstream edit invalidates none of its
            // upstream: those chunks keep the fingerprints they had, so the capture is - right now, while it
            // runs - materialising exactly the feed the edited element needs, as well as the pre-edit
            // downstream chunks a revert would want. Killing it was scenario C: the paid work was thrown
            // away and the stream re-parsed from record 0.
            // When it IS abandoned it must be dropped from the cache, not merely terminated: a terminated
            // sweep is an errored one, and leaving it here would make reverting the edit serve that error
            // instead of re-capturing.
            sweeps.entrySet().removeIf(entry -> {
                if (entry.getKey().metaId() == metaId
                    && !entry.getValue().hasEnded()
                    && !stillProduces(entry.getValue(), fingerprints)) {
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
            // Every capture of this stream the session knows about, whatever configuration it ran under:
            // completed ones say what may be reprocessed from, and an in-flight one is work the launcher can
            // wait on rather than duplicate. Which of those matters is the launcher's decision, not the
            // session's - the session's job is to hand over what it has.
            final List<StreamSweep> priorSweeps = sweeps.values().stream()
                    .filter(prior -> prior.getMetaId() == metaId)
                    .toList();
            final StreamSweep sweep = launcher.launch(metaId, request, fingerprints, priorSweeps,
                    List.copyOf(running.getOrDefault(streamKey, List.of())));
            // The two producer kinds part ways here. A sweep that captures the stream (full sweep or
            // whole-stream reprocess) is cached by signature - durable, revert-friendly, exactly as before.
            // A materialisation has NO identity cache at all: its results are in the store, which answers a
            // repeated demand by coverage, so all the session tracks is that it is RUNNING - the launcher
            // uses that to attach a step to a producer already making its records rather than double-launch.
            if (sweep.isWaitHandle()) {
                // A handle on somebody else's producer: it owns nothing, so there is nothing to cache or
                // register. Caching one would be actively wrong - it serves nothing, so every later poll
                // would get it back and the step could never be re-planned against the records that have
                // arrived in the meantime.
                LOGGER.debug(() -> "sweepFor() - waiting on the capture already running for stream " + metaId);
            } else if (sweep.getCacheKey() != null) {
                // A sweep the session must own but no step may be served from - a backbone, which captures
                // only the record-boundary elements. It is cached under its OWN key, which no step lookup
                // can produce, and the RESOLVER gets a wait handle on it: the session keeps the producer
                // (termination, the cap, prior work offered to the launcher, kept across downstream edits by
                // stillProduces) while the step waits on its progress and re-plans as records arrive.
                sweeps.put(new SweepKey(metaId, sweep.getCacheKey()), sweep);
                return StreamSweep.waitingOn(sweep);
            } else if (sweep.isOnDemand()) {
                if (!sweep.hasEnded()) {
                    running.computeIfAbsent(streamKey, k -> new ArrayList<>()).add(sweep);
                }
            } else {
                sweeps.put(streamKey, sweep);
            }
            return sweep;
        }
    }

    /**
     * Is an in-flight capture still producing something the new configuration wants?
     * <p>
     * Yes exactly when some element's cumulative fingerprint is the same in both. A cumulative fingerprint
     * folds in everything upstream, so an edit low in the pipeline leaves every element above it with the
     * fingerprint the capture is already writing - its remaining work is the feed the edited element will be
     * replayed from, and finishing it is the cheapest way to get that feed. An edit at the very top (the
     * parser, a reader) changes every cumulative fingerprint there is, so nothing the capture goes on to
     * write would be addressed by the new configuration at all, and it is stopped.
     * <p>
     * A sweep created without fingerprints cannot be judged, so it is treated as superseded - the old
     * behaviour, and the safe direction: stopping work that might have been useful costs time, whereas
     * keeping work that is genuinely dead costs the whole stream twice over.
     */
    private boolean stillProduces(final StreamSweep sweep, final ElementFingerprints current) {
        final ElementFingerprints captured = sweep.getFingerprints();
        if (captured == null) {
            return false;
        }
        return captured.getCumulativeFingerprints().entrySet().stream()
                .anyMatch(entry -> entry.getValue().equals(current.getCumulativeFingerprint(entry.getKey())));
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

    public Collection<StreamSweep> getOwnedSweeps() {
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
    private record SweepKey(long metaId, String signature) {

    }

    /**
     * Launches (starts filling) a stream's sweep. Supplied by the owner (e.g. {@code SteppingService}), bound
     * to the session's current request and fingerprints, which change as the user edits code.
     */
    @FunctionalInterface
    public interface SweepLauncher {

        /**
         * @param priorSweeps every stream-capturing sweep this session holds for this stream, in any
         *                    configuration: a completed one says its chunks may be reprocessed from, an
         *                    in-flight one is a capture to wait on rather than duplicate.
         * @param running     the materialisations currently in flight for this stream under these
         *                    fingerprints, so the launcher can attach a step to a producer already making the
         *                    records it wants instead of launching a duplicate.
         */
        StreamSweep launch(long metaId,
                           PipelineStepRequest request,
                           ElementFingerprints fingerprints,
                           List<StreamSweep> priorSweeps,
                           List<StreamSweep> running);
    }
}
