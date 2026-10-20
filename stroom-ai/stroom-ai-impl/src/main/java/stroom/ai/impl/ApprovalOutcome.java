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

package stroom.ai.impl;

import java.util.Objects;

/// What became of asking the user to approve something (see [ToolConsentGate]): answered at once (as a form in an
/// MCP client can be), or waiting for an answer that comes later (as on a chat's approval card, or an approval id the
/// client passes back).
///
/// @param status Where the approval has got to.
/// @param id     The id the answer is matched to (a chat's APPROVAL_REQUEST message id), or null if answered at
///               once or not known.
/// @param note   What the user said with their answer, or null.
public record ApprovalOutcome(Status status, String id, String note) {

    /// @param status Where the approval has got to.
    /// @param id     The id the answer is matched to, or null.
    /// @param note   What the user said with their answer, or null.
    public ApprovalOutcome {
        Objects.requireNonNull(status, "status");
    }

    /// @param id The id the answer will be matched to, or null if it isn't known.
    /// @return An approval waiting for the user's answer.
    public static ApprovalOutcome pending(final String id) {
        return new ApprovalOutcome(Status.PENDING, id, null);
    }

    /// @return True if the user is still to answer.
    public boolean isPending() {
        return status == Status.PENDING;
    }

    /// Where an approval has got to.
    public enum Status {
        /// The user allowed it.
        ALLOWED,
        /// The user did not allow it.
        DENIED,
        /// The user is still to answer.
        PENDING
    }
}
