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

import stroom.ai.shared.AiChatMessage;
import stroom.ai.shared.AiChatRun;
import stroom.ai.shared.AiChatRunStatus;
import stroom.ai.shared.AiChatRunStopReason;
import stroom.ai.shared.AiMessageType;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TestAskStroomAIService {

    @Test
    void testSelectHistoryForModel_leavesOutErrorsAndUiOnlyMessages() {
        final List<AiChatMessage> history = List.of(
                message(1, AiMessageType.ATTACHMENT, "table"),
                message(2, AiMessageType.USER_MESSAGE, "question"),
                message(3, AiMessageType.THINKING, "thinking"),
                message(4, AiMessageType.ERROR, "The AI model took too long to respond"),
                message(5, AiMessageType.WORKING, "Working..."),
                message(6, AiMessageType.DEBUG_DETAIL, "detail"),
                message(7, AiMessageType.AI_RESPONSE, "answer"),
                message(8, AiMessageType.USER_MESSAGE, "question 2"));

        final List<AiChatMessage> selected = AskStroomAIService.selectHistoryForModel(history);

        // ERROR rows must not be replayed, as the model would read them as its own replies.
        assertThat(selected)
                .extracting(AiChatMessage::getId)
                .containsExactly(1, 2, 7, 8);
    }

    @Test
    void testSelectHistoryForModel_endsAtTheLastQuestion() {
        // A partial answer from an attempt that was stopped is left out, so the question can be answered again.
        final List<AiChatMessage> history = List.of(
                message(1, AiMessageType.USER_MESSAGE, "question 1"),
                message(2, AiMessageType.AI_RESPONSE, "answer 1"),
                message(3, AiMessageType.USER_MESSAGE, "question 2"),
                message(4, AiMessageType.AI_RESPONSE, "partial answer 2"));

        assertThat(AskStroomAIService.selectHistoryForModel(history))
                .extracting(AiChatMessage::getId)
                .containsExactly(1, 2, 3);
    }

    @Test
    void testResumeNote() {
        final AiChatRun interrupted = new AiChatRun(1, 1, AiChatRunStatus.INTERRUPTED,
                AiChatRunStopReason.NODE_LOST, 0L, 1L, false, null);
        final AiChatRun failed = new AiChatRun(2, 1, AiChatRunStatus.FAILED, null, 0L, 1L, false, "boom");

        assertThat(AskStroomAIService.resumeNote(interrupted))
                .contains("did not finish: interrupted because the stroom node running it stopped")
                .contains("check the current state")
                .endsWith("\n\n");
        assertThat(AskStroomAIService.resumeNote(failed)).contains("did not finish: failed");
    }

    @Test
    void testSelectHistoryForModel_empty() {
        assertThat(AskStroomAIService.selectHistoryForModel(List.of())).isEmpty();
    }

    private static AiChatMessage message(final int id, final AiMessageType type, final String text) {
        return new AiChatMessage(id, 1, 0L, type, null, text, 0, null);
    }
}
