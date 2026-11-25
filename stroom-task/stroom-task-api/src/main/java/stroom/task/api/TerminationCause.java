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

package stroom.task.api;

import java.util.Objects;

/// Why a task was terminated.
///
/// @param type         What caused it.
/// @param terminatedBy Who terminated it, if its type is [Type#USER] and that is known, otherwise null.
public record TerminationCause(Type type, String terminatedBy) {

    /// The node is stopping.
    public static final TerminationCause NODE_STOPPING = new TerminationCause(Type.NODE_STOPPING, null);
    /// The node has not been able to renew the heartbeats of what it is processing.
    public static final TerminationCause HEARTBEAT_OVERDUE = new TerminationCause(Type.HEARTBEAT_OVERDUE, null);
    /// What the task was working on is no longer its to work on, e.g. as it has been given to something else.
    public static final TerminationCause WORK_LOST = new TerminationCause(Type.WORK_LOST, null);
    /// Whatever terminated the task did not say why.
    public static final TerminationCause NOT_GIVEN = new TerminationCause(Type.NOT_GIVEN, null);

    public TerminationCause {
        Objects.requireNonNull(type, "Null type");
    }

    /// Creates the cause for a task that was terminated by a user.
    ///
    /// @param terminatedBy The name of the user to show, or null if it is not known.
    /// @return The cause.
    public static TerminationCause user(final String terminatedBy) {
        return new TerminationCause(Type.USER, terminatedBy);
    }


    // --------------------------------------------------------------------------------


    public enum Type {
        NODE_STOPPING,
        USER,
        HEARTBEAT_OVERDUE,
        WORK_LOST,
        NOT_GIVEN
    }
}
