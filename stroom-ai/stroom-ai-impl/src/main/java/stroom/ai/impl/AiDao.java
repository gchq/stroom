/*
 * Copyright 2016 Crown Copyright
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

import stroom.ai.shared.AiAttachmentStatus;
import stroom.ai.shared.AiAttachmentType;
import stroom.ai.shared.AiChat;
import stroom.ai.shared.AiChatAttachment;
import stroom.ai.shared.AiChatMessage;
import stroom.ai.shared.AiMessageType;
import stroom.ai.shared.FindAiChatHistoryCriteria;
import stroom.util.shared.ResultPage;
import stroom.util.shared.UserRef;

import java.util.List;
import java.util.Optional;

public interface AiDao {

    AiChat createChat(UserRef userRef);

    ResultPage<AiChat> listChats(UserRef userRef, FindAiChatHistoryCriteria criteria);

    Optional<AiChat> getChat(int chatId);

    void updateChatTitle(int chatId, String title);

    void deleteChat(int chatId);

    AiChatMessage storeMessage(int chatId, AiMessageType messageType, String message);

    AiChatMessage storeMessage(int chatId, AiMessageType messageType, Integer attachmentId, String message);

    List<AiChatMessage> getMessages(int chatId);

    List<AiChatMessage> getMessagesSince(int chatId, int lastSeenMessageId);

    /**
     * @return The chat's WORKING message if one is in place, i.e. if a question is being processed.
     */
    Optional<AiChatMessage> getWorkingMessage(int chatId);

    /**
     * Removes every WORKING message for the chat. A server that stopped mid-question leaves one
     * behind, and a chat that appears to be working forever is one that can never be polled to a
     * conclusion.
     */
    void deleteWorkingMessages(int chatId);

    void updateMessageText(int messageId, String message);

    void deleteMessage(int messageId);

    void deleteAttachment(int attachmentId);

    void deleteAllChatMessagesAndAttachments(int chatId);

    // ---------------------------------------------------------------------
    // Attachment operations
    // ---------------------------------------------------------------------

    AiChatAttachment createAttachment(int chatId, AiAttachmentType type, String contextJson);

    void updateAttachmentStatus(int attachmentId,
                                AiAttachmentStatus status,
                                Integer rowCount,
                                String description,
                                String errorMessage,
                                boolean truncated);

    Optional<AiChatAttachment> getAttachment(int attachmentId);

    List<AiChatAttachment> getAttachmentsByChatId(int chatId);

    /**
     * Records the tokens used by a model call in the chat.
     *
     * @param chatId              The chat.
     * @param modelDocUuid        The model used.
     * @param contextWindowTokens The model's context window, or 0 if not known.
     * @param inputTokens         The input tokens the model reported for the call.
     * @param outputTokens        The output tokens the model reported for the call.
     */
    void recordUsage(int chatId, String modelDocUuid, int contextWindowTokens, long inputTokens, long outputTokens);

    /**
     * @param chatId The chat.
     * @return What has been recorded about the chat's token use.
     */
    ChatUsage getUsage(int chatId);

    /**
     * Records that a browser has just checked on the chat. To save writes, the time is only stored when the last
     * one stored is older than the given interval.
     *
     * @param chatId        The chat.
     * @param minIntervalMs Only store the time if the stored one is older than this.
     */
    void recordPoll(int chatId, long minIntervalMs);

    /**
     * @param userUuid A user.
     * @return When a browser last checked on any of the user's chats, or null if never.
     */
    Long getLastPollMs(String userUuid);

    /**
     * @param timeMs A time.
     * @param limit  The most to return.
     * @return The ids of chats last changed before the time.
     */
    List<Integer> findChatsNotUpdatedSince(long timeMs, int limit);

    /**
     * @return The UUIDs of every user who has a chat.
     */
    List<String> getChatOwners();

    /**
     * @param userUuid A user.
     * @return The ids of the user's chats.
     */
    List<Integer> getChatIds(String userUuid);

    /**
     * What has been recorded about a chat's token use.
     *
     * @param modelDocUuid        The model last used, or null.
     * @param contextWindowTokens The model's context window, or null if not known.
     * @param lastInputTokens     The input tokens the model reported for the last call, or null.
     * @param totalInputTokens    All the input tokens used by the chat.
     * @param totalOutputTokens   All the output tokens used by the chat.
     */
    record ChatUsage(String modelDocUuid,
                     Integer contextWindowTokens,
                     Integer lastInputTokens,
                     long totalInputTokens,
                     long totalOutputTokens) {

    }
}
