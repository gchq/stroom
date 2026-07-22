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

import stroom.pipeline.stepping.SteppingService;
import stroom.pipeline.stepping.store.SteppingConfig;
import stroom.security.api.UserIdentity;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Supplier;

/**
 * Holds the live {@link SteppingSession}s, keyed by {@code (user, session id)}, and decides when one dies.
 * <p>
 * A session is deliberately <b>not</b> removed when a step completes - that is the whole point of it, and
 * what lets subsequent steps be served from data an earlier step captured. It goes only on terminate, on
 * {@link #reapIdle()}, or when it can no longer answer the request it is handed.
 * <p>
 * Creating a session needs the pipeline machinery, which lives in {@link SteppingService}; this class only
 * keys and reaps them, so {@link #getOrCreate} takes a factory rather than growing those dependencies.
 */
@Singleton
public class SteppingSessionRegistry {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(SteppingSessionRegistry.class);

    private final SteppingConfig config;

    private final ConcurrentMap<Key, SteppingSession> sessions = new ConcurrentHashMap<>();

    @Inject
    public SteppingSessionRegistry(final SteppingConfig config) {
        this.config = config;
    }

    /**
     * Return the session for this request's id, or start a fresh one.
     * <p>
     * An id we no longer know - reaped, terminated, or created for a different stream selection - does not
     * fail the step. It <b>self-heals</b>: a new session is created, its id goes back in the response, and
     * the client adopts it. The user pays for a re-sweep, not an error.
     *
     * @param streamIds the request's stream selection; a session created for a different one cannot answer.
     * @param factory   creates a session when there is nothing usable to return.
     */
    public SteppingSession getOrCreate(final UserIdentity userIdentity,
                                final String requestedSessionId,
                                final List<Long> streamIds,
                                final Supplier<SteppingSession> factory) {
        if (requestedSessionId != null) {
            final Key key = new Key(userIdentity, requestedSessionId);
            final SteppingSession existing = sessions.get(key);
            if (existing != null && !existing.isClosed() && existing.matchesSelection(streamIds)) {
                return existing;
            }
            if (existing != null) {
                LOGGER.debug(() -> "Replacing stepping session that can no longer serve the request: "
                                   + requestedSessionId);
                remove(key);
            }
        }

        final SteppingSession session = factory.get();
        sessions.put(new Key(userIdentity, session.getSessionId()), session);
        return session;
    }

    /**
     * Terminate a session by id.
     *
     * @return true if there was one to terminate.
     */
    public boolean terminate(final UserIdentity userIdentity, final String sessionId) {
        final Key key = new Key(userIdentity, sessionId);
        if (!sessions.containsKey(key)) {
            return false;
        }
        LOGGER.debug(() -> "Terminating stepping session: " + sessionId);
        remove(key);
        return true;
    }

    /**
     * Close sessions no step has touched for {@code maxSessionIdleTime}. A stepping session holds captured
     * data on disk, so leaving abandoned ones around is not free.
     */
    public void reapIdle() {
        final Instant oldest = Instant.now().minus(config.getMaxSessionIdleTime().getDuration());
        sessions.forEach((key, session) -> {
            if (session.getLastAccessTime().isBefore(oldest)) {
                LOGGER.debug(() -> "Reaping idle stepping session: " + key);
                remove(key);
            }
        });
    }

    private void remove(final Key key) {
        final SteppingSession session = sessions.remove(key);
        if (session != null) {
            // close() terminates the session's sweeps and deletes its captured data.
            session.close();
        }
    }

    Map<Key, SteppingSession> getSessions() {
        return Map.copyOf(sessions);
    }

    // --------------------------------------------------------------------------------

    /**
     * Sessions are keyed by user as well as id so that one user's id can never name another's session.
     */
    record Key(UserIdentity userIdentity, String uuid) {

    }
}
