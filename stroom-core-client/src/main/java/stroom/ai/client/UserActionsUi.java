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

package stroom.ai.client;

/// The actions an MCP tool's result can offer the user as buttons, as the server sends them (see the server's
/// `UserActions`), and what their buttons say.
final class UserActionsUi {

    static final String ENABLE_PROCESSOR_FILTER = "enable_processor_filter";
    static final String DISABLE_PROCESSOR_FILTER = "disable_processor_filter";
    static final String OPEN_DOCUMENT = "open_document";
    static final String ELASTICSEARCH_REQUEST = "elasticsearch_request";

    private UserActionsUi() {
        // Static methods only.
    }

    /// @param status    The action's status, as stored.
    /// @param expiresMs When its button stops working.
    /// @param nowMs     The time now.
    /// @return True if its button can still be used.
    static boolean isPending(final String status, final long expiresMs, final long nowMs) {
        return "PENDING".equals(status) && expiresMs > nowMs;
    }

    /// @param status  The action's status, as stored.
    /// @param outcome What happened when it was used, or null.
    /// @return What to show in place of a button that can't be used.
    static String outcomeText(final String status, final String outcome) {
        if (outcome != null && !outcome.isBlank()) {
            return outcome;
        }
        return "PENDING".equals(status)
                ? "This button has expired."
                : String.valueOf(status);
    }

    /// @param action An action.
    /// @return What its button says.
    static String buttonText(final String action) {
        if (ENABLE_PROCESSOR_FILTER.equals(action)) {
            return "Enable";
        } else if (DISABLE_PROCESSOR_FILTER.equals(action)) {
            return "Disable";
        } else if (OPEN_DOCUMENT.equals(action)) {
            return "Open";
        }
        return "Do it";
    }
}
