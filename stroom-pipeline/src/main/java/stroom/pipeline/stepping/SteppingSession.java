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

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * A durable stepping session scoped to one pipeline + stream selection. It owns the ordered list of
 * candidate stream ids and lazily sweeps them: a {@link StreamSweep} is launched for a stream only the
 * first time a step targets it (never all streams up front), via the supplied {@link SweepLauncher}.
 * The element fingerprints (keys for the captured IO) are computed once as they are the same for every
 * stream. {@link #close()} tears down the session (cancels sweeps + deletes persisted data).
 */
public class SteppingSession {

    private final String sessionId;
    private final List<Long> streamIdList;
    private final ElementFingerprints fingerprints;
    private final SweepLauncher launcher;
    private final Consumer<SteppingSession> onClose;
    private final int maxSweptStreams;

    private final Map<Long, StreamSweep> sweeps = new ConcurrentHashMap<>();
    private volatile Instant lastAccessTime = Instant.now();

    // Guards sweep creation against teardown. StepDataStoreManager requires that a store is never created
    // for a session that is being deleted (a create racing a delete re-creates the map entry and directory
    // that the delete just removed, leaking its channels and temp dir forever). This session is the owner
    // that serialises the two, so ensureStreamSwept and close() are mutually exclusive.
    private final Object lifecycleLock = new Object();
    private boolean closed;

    public SteppingSession(final String sessionId,
                           final List<Long> streamIdList,
                           final ElementFingerprints fingerprints,
                           final SweepLauncher launcher,
                           final Consumer<SteppingSession> onClose,
                           final int maxSweptStreams) {
        this.sessionId = sessionId;
        this.streamIdList = List.copyOf(streamIdList);
        this.fingerprints = fingerprints;
        this.launcher = launcher;
        this.onClose = onClose;
        this.maxSweptStreams = maxSweptStreams;
    }

    /**
     * Get (launching a sweep the first time) the sweep for a stream.
     *
     * @throws IllegalStateException if the session has been closed.
     */
    public StreamSweep ensureStreamSwept(final long metaId) {
        synchronized (lifecycleLock) {
            if (closed) {
                throw new IllegalStateException(
                        "Stepping session " + sessionId + " has been closed");
            }
            lastAccessTime = Instant.now();
            final StreamSweep existing = sweeps.get(metaId);
            if (existing != null) {
                return existing;
            }
            if (sweeps.size() >= maxSweptStreams) {
                throw new StepDataStoreException(
                        "This stepping session has already swept the maximum of " + maxSweptStreams
                        + " streams; narrow your selection");
            }
            final StreamSweep sweep = launcher.launch(metaId);
            sweeps.put(metaId, sweep);
            return sweep;
        }
    }

    /**
     * @return true if the stream is one of this session's candidate streams.
     */
    public boolean containsStream(final long metaId) {
        return streamIdList.contains(metaId);
    }

    public String getSessionId() {
        return sessionId;
    }

    public List<Long> getStreamIdList() {
        return streamIdList;
    }

    public ElementFingerprints getFingerprints() {
        return fingerprints;
    }

    public Collection<StreamSweep> getActiveSweeps() {
        return sweeps.values();
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
     * Launches (starts capturing) a stream's sweep. Supplied by the owner (e.g. {@code SteppingService}),
     * bound to the session's request.
     */
    @FunctionalInterface
    public interface SweepLauncher {

        StreamSweep launch(long metaId);
    }
}
