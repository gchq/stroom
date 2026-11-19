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

import stroom.ai.impl.tools.ChatUserQuestions;
import stroom.ai.impl.tools.ToolCallContext;
import stroom.ai.impl.tools.ToolSession;
import stroom.ai.shared.AiChatMessage;
import stroom.ai.shared.AiChatRun;
import stroom.ai.shared.AiChatRunStatus;
import stroom.ai.shared.AiChatRunStopReason;
import stroom.ai.shared.AiMessageType;

import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/// A Stroom AI chat's own forms, for the tests of Stroom's tools: the real [McpElicitations] (as
/// [ChatUserQuestions] asks through it in a chat) over a chat whose messages are kept in memory, so a tool call can
/// show a form, stop the run, and be made again once the user has answered it, or sent a message instead (as
/// `AskStroomAIService` steers a run waiting for a form's answer).
public final class ChatForms {

    private final int chatId;
    private final List<AiChatMessage> messages = new ArrayList<>();
    private final AiDao aiDao = Mockito.mock(AiDao.class);
    private final AiChatRunDao runDao = Mockito.mock(AiChatRunDao.class);
    private final AiChatRunManager runManager = Mockito.mock(AiChatRunManager.class);
    private final McpElicitations elicitations;

    /// @param chatId The chat.
    public ChatForms(final int chatId) {
        this.chatId = chatId;
        Mockito.when(aiDao.storeMessage(Mockito.eq(chatId), Mockito.any(), Mockito.anyString()))
                .thenAnswer(invocation -> store(invocation.getArgument(1), invocation.getArgument(2)));
        Mockito.when(aiDao.getMessages(chatId)).thenAnswer(invocation -> messages());
        Mockito.when(aiDao.compareAndSetMessageText(Mockito.anyInt(), Mockito.anyString(), Mockito.anyString()))
                .thenAnswer(invocation -> changeText(invocation.getArgument(0), invocation.getArgument(1),
                        invocation.getArgument(2)));
        // The run stopped for the form's answer.
        Mockito.when(runDao.getActive(chatId)).thenReturn(Optional.of(new AiChatRun(1, chatId,
                AiChatRunStatus.WAITING_FOR_USER, AiChatRunStopReason.NEEDS_ANSWERS, 0L, null, false, null)));
        Mockito.when(runManager.isUnattended(chatId)).thenReturn(false);
        elicitations = new McpElicitations(aiDao, runDao, runManager, AiConfig::new);
    }

    /// @return The context of a tool call in the chat, whose forms are shown in the chat.
    public ToolCallContext context() {
        return ToolCallContext.builder(ToolSession.chat(chatId))
                .userQuestions(new ChatUserQuestions(chatId, () -> elicitations, () -> runManager))
                .build();
    }

    /// The user types a message in the chat instead of answering the form waiting for them, as
    /// `AskStroomAIService` steers a run waiting for the user: the message is stored for the run, and the form put
    /// aside.
    ///
    /// @param message What the user typed.
    /// @return How many forms were put aside.
    public int userSendsInstead(final String message) {
        store(AiMessageType.STEER_MESSAGE, message);
        return elicitations.supersedePending(chatId);
    }

    /// @return The chat's messages, in the order stored.
    public List<AiChatMessage> messages() {
        synchronized (messages) {
            return List.copyOf(messages);
        }
    }

    private AiChatMessage store(final AiMessageType type, final String text) {
        synchronized (messages) {
            final AiChatMessage message = new AiChatMessage(messages.size() + 1, chatId, System.currentTimeMillis(),
                    type, null, text, 1, null);
            messages.add(message);
            return message;
        }
    }

    private boolean changeText(final int id, final String expected, final String text) {
        synchronized (messages) {
            for (int i = 0; i < messages.size(); i++) {
                final AiChatMessage row = messages.get(i);
                if (row.getId() == id && expected.equals(row.getMessage())) {
                    messages.set(i, new AiChatMessage(row.getId(), row.getChatId(), row.getCreateTimeMs(),
                            row.getMessageType(), row.getAttachmentId(), text, row.getTurnId(),
                            row.getTokenCount()));
                    return true;
                }
            }
            return false;
        }
    }
}
