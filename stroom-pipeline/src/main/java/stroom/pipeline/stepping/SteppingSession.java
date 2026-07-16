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

    private final Map<Long, StreamSweep> sweeps = new ConcurrentHashMap<>();
    private volatile Instant lastAccessTime = Instant.now();

    public SteppingSession(final String sessionId,
                           final List<Long> streamIdList,
                           final ElementFingerprints fingerprints,
                           final SweepLauncher launcher,
                           final Consumer<SteppingSession> onClose) {
        this.sessionId = sessionId;
        this.streamIdList = List.copyOf(streamIdList);
        this.fingerprints = fingerprints;
        this.launcher = launcher;
        this.onClose = onClose;
    }

    /**
     * Get (launching a sweep the first time) the sweep for a stream.
     */
    public StreamSweep ensureStreamSwept(final long metaId) {
        lastAccessTime = Instant.now();
        return sweeps.computeIfAbsent(metaId, launcher::launch);
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

    public void close() {
        if (onClose != null) {
            onClose.accept(this);
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
