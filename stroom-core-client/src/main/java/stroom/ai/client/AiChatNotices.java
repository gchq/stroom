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

import stroom.ai.shared.AiChatStatus;

/// What a change in a chat's status, between two checks on it, tells the user with a browser notification.
final class AiChatNotices {

    private AiChatNotices() {
        // Static only.
    }

    /// @param was The chat's status at the check before, or null if it wasn't known then.
    /// @param now Its status now.
    /// @return True if the chat has come to need the user: it wasn't waiting for them before, or it is waiting
    /// with another form. A form that follows one just answered can come between two checks, the chat waiting at
    /// both (seen: such forms notified no one).
    static boolean needsYouAgain(final AiChatStatus was, final AiChatStatus now) {
        return now.isNeedsYou()
               && (was == null || !was.isNeedsYou() || was.getFormMessageId() != now.getFormMessageId());
    }

    /// @param was The chat's status at the check before, or null if it wasn't known then.
    /// @param now Its status now.
    /// @return True if the chat has just finished with something new for the user to read.
    static boolean hasAnswered(final AiChatStatus was, final AiChatStatus now) {
        return now.isUnread() && !now.isBusy() && !now.isNeedsYou()
               && was != null && (was.isBusy() || !was.isUnread());
    }
}
