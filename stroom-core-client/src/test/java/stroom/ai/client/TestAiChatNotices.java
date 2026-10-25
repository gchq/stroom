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

import stroom.ai.shared.AiChatRunStatus;
import stroom.ai.shared.AiChatStatus;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TestAiChatNotices {

    @Test
    void aChatWaitingWithAnotherFormNeedsYouAgain() {
        // Seen: a form following one just answered, between two checks, the chat waiting at both, notified no one.
        assertThat(AiChatNotices.needsYouAgain(status(AiChatRunStatus.WAITING_FOR_USER, false, 10),
                status(AiChatRunStatus.WAITING_FOR_USER, false, 12))).isTrue();
        // Still waiting with the same form: told once.
        assertThat(AiChatNotices.needsYouAgain(status(AiChatRunStatus.WAITING_FOR_USER, false, 12),
                status(AiChatRunStatus.WAITING_FOR_USER, false, 12))).isFalse();
    }

    @Test
    void aChatThatHasComeToWaitNeedsYou() {
        assertThat(AiChatNotices.needsYouAgain(status(AiChatRunStatus.RUNNING, false, 10),
                status(AiChatRunStatus.WAITING_FOR_USER, false, 10))).isTrue();
        assertThat(AiChatNotices.needsYouAgain(null, status(AiChatRunStatus.WAITING_FOR_USER, false, 0))).isTrue();
        assertThat(AiChatNotices.needsYouAgain(status(AiChatRunStatus.WAITING_FOR_USER, false, 10),
                status(AiChatRunStatus.RUNNING, false, 10))).isFalse();
    }

    @Test
    void aChatThatHasFinishedHasAnswered() {
        assertThat(AiChatNotices.hasAnswered(status(AiChatRunStatus.RUNNING, false, 0),
                status(AiChatRunStatus.DONE, true, 0))).isTrue();
        // Already unread and idle: told before.
        assertThat(AiChatNotices.hasAnswered(status(AiChatRunStatus.DONE, true, 0),
                status(AiChatRunStatus.DONE, true, 0))).isFalse();
        // Not known before.
        assertThat(AiChatNotices.hasAnswered(null, status(AiChatRunStatus.DONE, true, 0))).isFalse();
        // Waiting is told as needing you instead.
        assertThat(AiChatNotices.hasAnswered(status(AiChatRunStatus.RUNNING, false, 0),
                status(AiChatRunStatus.WAITING_FOR_USER, true, 0))).isFalse();
    }

    private static AiChatStatus status(final AiChatRunStatus runStatus, final boolean unread, final int form) {
        return new AiChatStatus(1, "t", 0L, runStatus, unread, false, 0L, null, form);
    }
}
