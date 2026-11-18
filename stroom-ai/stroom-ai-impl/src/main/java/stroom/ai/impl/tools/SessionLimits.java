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

package stroom.ai.impl.tools;

import stroom.ai.impl.AiWorkspaceConfig;
import stroom.util.shared.NullSafe;
import stroom.util.time.StroomDuration;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/// The limits that apply to the tool calls of one session: how much a call may return (lines and characters per
/// read), how long a call may take (the tool and query time limits), how large a query and its results may be, how
/// many events and stream-store reads a call may make, and how much the session's workspace may hold. A tool reads
/// them through its call's context ([ToolCallContext#limits()]), so each session can have its own: a chat's are
/// the workspace limits Stroom is configured with (see [ChatSessionLimits]), and an MCP session can have others.
///
/// They are looked up each time they are read, rather than held, so a change to the configuration applies to the
/// next read, as it always has.
@FunctionalInterface
public interface SessionLimits {

    /// The limits of a session that sets none of its own: the defaults of [AiWorkspaceConfig].
    SessionLimits DEFAULTS = of(new AiWorkspaceConfig());

    /// @param limits The limits.
    /// @return Fixed limits.
    static SessionLimits of(final AiWorkspaceConfig limits) {
        Objects.requireNonNull(limits, "limits");
        return () -> limits;
    }

    /// @param limits Gives the limits each time they are read.
    /// @return Limits looked up each time they are read.
    static SessionLimits lookedUp(final Supplier<AiWorkspaceConfig> limits) {
        Objects.requireNonNull(limits, "limits");
        return limits::get;
    }

    /// @return The session's limits on what its tool calls return and do, and on its workspace.
    AiWorkspaceConfig workspace();

    /// @return How long one tool call in the session may take (a tool that runs a query has the query time limit,
    /// see [AiWorkspaceConfig#getQueryTimeLimit()]), or empty if no limits are set.
    default Optional<Duration> toolTimeLimit() {
        return Optional.ofNullable(NullSafe.get(workspace(),
                AiWorkspaceConfig::getToolTimeLimit,
                StroomDuration::getDuration));
    }
}
