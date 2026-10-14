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

import stroom.ai.api.AiService;
import stroom.ai.shared.AiChat;
import stroom.ai.shared.AiChatMessage;
import stroom.ai.shared.AiChatRun;
import stroom.ai.shared.AiChatRunStatus;
import stroom.ai.shared.AiChatRunStopReason;
import stroom.ai.shared.AiMessageType;
import stroom.ai.shared.AskStroomAiRequest;
import stroom.ai.shared.EditAiMessageRequest;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestAskStroomAIService {

    private final AiService aiService = Mockito.mock(AiService.class);
    private final AiChatRunManager runManager = Mockito.mock(AiChatRunManager.class);
    private final AiChatRunDao runDao = Mockito.mock(AiChatRunDao.class);
    private final AiChat chat = new AiChat(1, 0L, 0L, "user1", "chat");

    private AskStroomAIService service() {
        return new AskStroomAIService(aiService, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, runManager, runDao, null, null, null, null);
    }

    @Test
    void testAskStroomAi_startsARunWhenNoneIsActive() {
        Mockito.when(runDao.getActive(1)).thenReturn(Optional.empty());

        service().askStroomAi(new AskStroomAiRequest(chat, null, null, "question"));

        Mockito.verify(runManager).start(Mockito.eq(1), Mockito.any());
        Mockito.verify(aiService, Mockito.never())
                .storeMessage(Mockito.eq(1), Mockito.eq(AiMessageType.STEER_MESSAGE), Mockito.anyString());
    }

    @Test
    void testAskStroomAi_steersAnActiveRun() {
        final AiChatRun running = new AiChatRun(5, 1, AiChatRunStatus.RUNNING, null, 0L, null, false, null);
        Mockito.when(runDao.getActive(1)).thenReturn(Optional.of(running));
        Mockito.when(aiService.storeMessage(1, AiMessageType.STEER_MESSAGE, "also check yesterday"))
                .thenReturn(message(9, AiMessageType.STEER_MESSAGE, "also check yesterday"));

        service().askStroomAi(new AskStroomAiRequest(chat, null, null, "also check yesterday"));

        Mockito.verify(aiService).storeMessage(1, AiMessageType.STEER_MESSAGE, "also check yesterday");
        Mockito.verify(runManager, Mockito.never()).start(Mockito.anyInt(), Mockito.any());
    }

    @Test
    void testAskStroomAi_runFinishedAsTheSteerArrived() {
        final AiChatRun running = new AiChatRun(5, 1, AiChatRunStatus.RUNNING, null, 0L, null, false, null);
        // Active when checked, but gone once the message has been stored.
        Mockito.when(runDao.getActive(1)).thenReturn(Optional.of(running), Optional.empty());
        Mockito.when(aiService.storeMessage(1, AiMessageType.STEER_MESSAGE, "late"))
                .thenReturn(message(9, AiMessageType.STEER_MESSAGE, "late"));

        service().askStroomAi(new AskStroomAiRequest(chat, null, null, "late"));

        // The steer is taken back and the message is asked as a new question.
        Mockito.verify(aiService).deleteMessage(9);
        Mockito.verify(runManager).start(Mockito.eq(1), Mockito.any());
    }

    @Test
    void testAskStroomAi_stoppingRunIsNotSteered() {
        final AiChatRun stopping = new AiChatRun(5, 1, AiChatRunStatus.STOPPING, null, 0L, null, false, null);
        Mockito.when(runDao.getActive(1)).thenReturn(Optional.of(stopping));

        service().askStroomAi(new AskStroomAiRequest(chat, null, null, "question"));

        // It is started, which fails as busy, rather than steering a run that is about to stop.
        Mockito.verify(runManager).start(Mockito.eq(1), Mockito.any());
    }

    @Test
    void testEditMessage_onlyQuestionsCanBeEdited() {
        Mockito.when(aiService.getMessages(1)).thenReturn(List.of(
                message(1, AiMessageType.USER_MESSAGE, "q"),
                message(2, AiMessageType.AI_RESPONSE, "a")));

        assertThatThrownBy(() -> service().editMessage(new EditAiMessageRequest(chat, null, 2, "x")))
                .hasMessageContaining("Only your own questions");
        Mockito.verify(runManager, Mockito.never()).start(Mockito.anyInt(), Mockito.any());
    }

    @Test
    void testEditMessage_stopsTheRunThenReplacesTheQuestionAndWhatFollows() {
        Mockito.when(aiService.getMessages(1)).thenReturn(List.of(
                message(1, AiMessageType.USER_MESSAGE, "q1"),
                message(2, AiMessageType.AI_RESPONSE, "a1"),
                message(3, AiMessageType.USER_MESSAGE, "q2"),
                message(4, AiMessageType.AI_RESPONSE, "a2")));
        final AiChatRun running = new AiChatRun(5, 1, AiChatRunStatus.RUNNING, null, 0L, null, false, null);
        // Active until it has been asked to stop.
        Mockito.when(runDao.getActive(1)).thenReturn(Optional.of(running), Optional.of(running), Optional.empty());
        // Run the new run's work straight away.
        Mockito.when(runManager.start(Mockito.eq(1), Mockito.any())).thenAnswer(invocation -> {
            final java.util.function.Consumer<AiChatRun> work = invocation.getArgument(1);
            try {
                work.accept(running);
            } catch (final RuntimeException e) {
                // The answer cannot be worked out with mocks, which is fine here.
            }
            return running;
        });

        service().editMessage(new EditAiMessageRequest(chat, null, 3, "q2 edited"));

        Mockito.verify(runManager).stop(1, AiChatRunStopReason.EDITED);
        // The edited question and its answer are removed, and the earlier turn is kept.
        Mockito.verify(aiService).deleteMessage(3);
        Mockito.verify(aiService).deleteMessage(4);
        Mockito.verify(aiService, Mockito.never()).deleteMessage(1);
        Mockito.verify(aiService, Mockito.never()).deleteMessage(2);
        Mockito.verify(aiService).storeMessage(1, AiMessageType.USER_MESSAGE, "q2 edited");
    }

    @Test
    void testEditMessage_retryKeepsTheText() {
        Mockito.when(aiService.getMessages(1)).thenReturn(List.of(message(1, AiMessageType.USER_MESSAGE, "q1")));
        Mockito.when(runDao.getActive(1)).thenReturn(Optional.empty());
        Mockito.when(runManager.start(Mockito.eq(1), Mockito.any())).thenAnswer(invocation -> {
            final java.util.function.Consumer<AiChatRun> work = invocation.getArgument(1);
            try {
                work.accept(null);
            } catch (final RuntimeException e) {
                // The answer cannot be worked out with mocks, which is fine here.
            }
            return null;
        });

        service().editMessage(new EditAiMessageRequest(chat, null, 1, null));

        Mockito.verify(aiService).storeMessage(1, AiMessageType.USER_MESSAGE, "q1");
    }

    @Test
    void testEditMessage_runThatWillNotStop() {
        Mockito.when(aiService.getMessages(1)).thenReturn(List.of(message(1, AiMessageType.USER_MESSAGE, "q1")));
        final AiChatRun running = new AiChatRun(5, 1, AiChatRunStatus.RUNNING, null, 0L, null, false, null);
        Mockito.when(runDao.getActive(1)).thenReturn(Optional.of(running));
        final AskStroomAIService service = service();
        service.setStopWait(java.time.Duration.ofMillis(300));

        assertThatThrownBy(() -> service.editMessage(new EditAiMessageRequest(chat, null, 1, "x")))
                .isInstanceOf(AiChatRunManager.AiChatBusyException.class);
        Mockito.verify(aiService, Mockito.never()).deleteMessage(Mockito.anyInt());
    }

    @Test
    void testSelectHistoryForModel_leavesOutErrorsAndUiOnlyMessages() {
        final List<AiChatMessage> history = List.of(
                message(1, AiMessageType.ATTACHMENT, "table"),
                message(2, AiMessageType.USER_MESSAGE, "question"),
                message(3, AiMessageType.THINKING, "thinking"),
                message(4, AiMessageType.ERROR, "The AI model took too long to respond"),
                message(41, AiMessageType.STEER_MESSAGE, "steer"),
                message(5, AiMessageType.WORKING, "Working..."),
                message(6, AiMessageType.DEBUG_DETAIL, "detail"),
                message(7, AiMessageType.AI_RESPONSE, "answer"),
                message(8, AiMessageType.USER_MESSAGE, "question 2"));

        final List<AiChatMessage> selected = AskStroomAIService.selectHistoryForModel(history);

        // ERROR rows must not be replayed, as the model would read them as its own replies.
        assertThat(selected)
                .extracting(AiChatMessage::getId)
                .containsExactly(1, 2, 41, 7, 8);
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
