/*
 * Copyright 2026 Crown Copyright
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

package stroom.mcp.impl;

import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import io.modelcontextprotocol.server.transport.HttpServletStreamableServerTransportProvider;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.Set;

/// The sessions the MCP SDK's Streamable HTTP transport holds, which [StroomMcpServer] keeps in step with its own.
///
/// The transport (MCP SDK 2.0.1) keeps its sessions in a private map, and offers no way to end one as a client's
/// DELETE does, or to ask which it holds: a session closed any other way stays in the map, pinged and notified for
/// ever, and the transport drops a session from it when a send to it fails, without telling anyone. So the map is
/// reached by reflection. `TestTransportSessions` checks this against the SDK in use, so an SDK that changes it
/// fails the build; if it is ever missing at run time, this says so in the log and does nothing.
final class TransportSessions {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TransportSessions.class);

    /// The name of the transport's field holding its sessions.
    static final String FIELD_NAME = "sessions";

    private final Map<String, ?> sessions;

    /// @param transport The transport.
    TransportSessions(final HttpServletStreamableServerTransportProvider transport) {
        this.sessions = sessionsOf(transport);
    }

    /// @return True if the transport's sessions can be reached.
    boolean isLinked() {
        return sessions != null;
    }

    /// @param sessionId A session's id.
    /// @return True if the transport holds the session (or its sessions can't be reached).
    boolean has(final String sessionId) {
        return sessions == null || sessions.containsKey(sessionId);
    }

    /// @return The ids of the sessions the transport holds, or null if they can't be reached.
    Set<String> ids() {
        return sessions == null
                ? null
                : Set.copyOf(sessions.keySet());
    }

    /// Makes the transport forget a session, as a client's DELETE does, so it is no longer pinged or notified.
    ///
    /// @param sessionId A session's id.
    void remove(final String sessionId) {
        if (sessions != null) {
            sessions.remove(sessionId);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, ?> sessionsOf(final HttpServletStreamableServerTransportProvider transport) {
        try {
            final Field field = HttpServletStreamableServerTransportProvider.class.getDeclaredField(FIELD_NAME);
            field.setAccessible(true);
            return (Map<String, ?>) field.get(transport);
        } catch (final ReflectiveOperationException | RuntimeException e) {
            LOGGER.error(() -> "Unable to reach the MCP transport's sessions: sessions ended by Stroom stay in the "
                               + "transport until their clients end them", e);
            return null;
        }
    }
}
