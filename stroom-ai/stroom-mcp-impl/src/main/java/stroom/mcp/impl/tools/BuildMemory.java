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

package stroom.mcp.impl.tools;

import stroom.ai.impl.tools.ToolSession;
import stroom.util.shared.NullSafe;

import jakarta.inject.Singleton;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/// The build each session is working on, so tools called without `build` fall back to it (the Stroom MCP server's
/// `remember_build`, kept per user in its memory). A convenience only, kept in this node's memory per session (a
/// chat, or an MCP session): a call it can't help is told to name the build, and the build itself is in Stroom.
@Singleton
class BuildMemory {

    /// The most sessions remembered: the least recently used are forgotten first.
    static final int MAX_SESSIONS = 10_000;

    private final Map<String, String> builds = Collections.synchronizedMap(
            new LinkedHashMap<>(16, 0.75F, true) {
                @Override
                protected boolean removeEldestEntry(final Map.Entry<String, String> eldest) {
                    return size() > MAX_SESSIONS;
                }
            });

    /// @param session A session.
    /// @param build   The build it is working on now, or null (nothing is remembered).
    void remember(final ToolSession session, final String build) {
        if (session != null && NullSafe.isNonBlankString(build)) {
            builds.put(key(session), build);
        }
    }

    /// @param session A session.
    /// @return The build it last worked on, if any.
    Optional<String> current(final ToolSession session) {
        return session == null
                ? Optional.empty()
                : Optional.ofNullable(builds.get(key(session)));
    }

    private static String key(final ToolSession session) {
        return session.kind() + ":" + session.id();
    }
}
