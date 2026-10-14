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

package stroom.ai.impl.memory;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/// A change to a piece of memory, as stored (as JSON) in a MEMORY_UPDATE message once made, or in an
/// APPROVAL_REQUEST message while it waits for the user.
///
/// @param kind        Always {@link #KIND}, so approval requests can be told apart.
/// @param operation   What was done.
/// @param path        The memory's path.
/// @param baseVersion The version the change was worked out from, or null if the memory did not exist.
/// @param before      The content before the change, or null if the memory did not exist.
/// @param after       The content after the change, or null if it is deleted.
/// @param status      For an approval request, where it has got to; null for a change already made.
/// @param reason      For an approval request, why it needs approval.
/// @param undone      True once the user has undone a change that was made, otherwise null.
@JsonInclude(Include.NON_NULL)
@JsonPropertyOrder(alphabetic = true)
public record MemoryChange(@JsonProperty("kind") String kind,
                           @JsonProperty("operation") Operation operation,
                           @JsonProperty("path") String path,
                           @JsonProperty("baseVersion") Integer baseVersion,
                           @JsonProperty("before") String before,
                           @JsonProperty("after") String after,
                           @JsonProperty("status") ApprovalStatus status,
                           @JsonProperty("reason") String reason,
                           @JsonProperty("undone") Boolean undone) {

    public static final String KIND = "MEMORY";

    @JsonCreator
    public MemoryChange {
        // Explicit constructor for Jackson.
    }

    /// A change not undone.
    public MemoryChange(final String kind,
                        final Operation operation,
                        final String path,
                        final Integer baseVersion,
                        final String before,
                        final String after,
                        final ApprovalStatus status,
                        final String reason) {
        this(kind, operation, path, baseVersion, before, after, status, reason, null);
    }

    /// @return A copy with the status changed.
    public MemoryChange withStatus(final ApprovalStatus status) {
        return new MemoryChange(kind, operation, path, baseVersion, before, after, status, reason, undone);
    }

    /// @return A copy marked as undone by the user.
    public MemoryChange withUndone() {
        return new MemoryChange(kind, operation, path, baseVersion, before, after, status, reason, true);
    }

    /// @return True if the user has undone the change.
    public boolean isUndone() {
        return Boolean.TRUE.equals(undone);
    }

    /// What a change does.
    public enum Operation {
        CREATE,
        UPDATE,
        DELETE
    }

    /// Where an approval request has got to.
    public enum ApprovalStatus {
        PENDING,
        APPROVED,
        REJECTED,
        EXPIRED,
        /// Approved, but the memory had changed since, so it was not made.
        FAILED
    }
}
