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

import stroom.ai.shared.AiAgentMode;
import stroom.ai.shared.AiAttachmentStatus;
import stroom.ai.shared.AiAttachmentType;
import stroom.ai.shared.AiChat;
import stroom.ai.shared.AiChatAttachment;
import stroom.ai.shared.AiChatMessage;
import stroom.ai.shared.AiChatStatus;
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

    /// Names a chat as the user chose, so it is no longer named automatically.
    void updateChatTitle(int chatId, String title);

    /// Names a chat automatically, unless the user has named it themselves. When the chat was last active is left
    /// as it is.
    ///
    /// @return True if the title was set.
    boolean setAutoTitle(int chatId, String title);

    /// @return True if the user has named the chat themselves, so it is not named automatically.
    boolean isTitleSetByUser(int chatId);

    /// Gives a chat the default title again, and lets it be named automatically.
    void resetChatTitle(int chatId);

    void deleteChat(int chatId);

    /// The title a chat has until its first question names it.
    String DEFAULT_CHAT_TITLE = "New chat";

    AiChatMessage storeMessage(int chatId, AiMessageType messageType, String message);

    AiChatMessage storeMessage(int chatId, AiMessageType messageType, Integer attachmentId, String message);

    List<AiChatMessage> getMessages(int chatId);

    List<AiChatMessage> getMessagesSince(int chatId, int lastSeenMessageId);

    /**
     * @return The chat's WORKING message if one is in place, i.e. if a question is being processed.
     */
    Optional<AiChatMessage> getWorkingMessage(int chatId);

    /**
     * Removes every WORKING message for the chat, and any answer or reasoning still being streamed. A server
     * that stopped mid-question leaves them behind, and a chat that appears to be working forever is one that
     * can never be polled to a conclusion.
     */
    void deleteWorkingMessages(int chatId);

    /**
     * @return The chat's STREAMING_ANSWER and STREAMING_THINKING messages, oldest first.
     */
    List<AiChatMessage> getStreamingMessages(int chatId);

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
     * Records the model a chat used, and its context window, when the model did not report the tokens it used.
     *
     * @param chatId              The chat.
     * @param modelDocUuid        The model used.
     * @param contextWindowTokens The model's context window, or 0 if not known.
     */
    void recordModel(int chatId, String modelDocUuid, int contextWindowTokens);

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
     * Changes a message's text, only if it is still the text expected, so that of two changes made at once (e.g.
     * answers to the same approval request from two browser tabs) only one is made.
     *
     * @return True if it was changed.
     */
    boolean compareAndSetMessageText(int messageId, String expected, String message);

    /**
     * @return True if 'approve everything' is on for the chat.
     */
    boolean isApproveEverything(int chatId);

    /**
     * Turns 'approve everything' on or off for the chat.
     */
    void setApproveEverything(int chatId, boolean enabled);

    /**
     * @return How the agent works in the chat.
     */
    AiAgentMode getAgentMode(int chatId);

    /**
     * Sets how the agent works in the chat.
     */
    void setAgentMode(int chatId, AiAgentMode mode);

    /**
     * @param messageType A type of message.
     * @param before      A time.
     * @return The messages of that type, in any chat, made before the time.
     */
    List<AiChatMessage> getMessagesOfTypeBefore(AiMessageType messageType, long before);

    /**
     * Records that the user has seen the chat's messages up to one.
     */
    void markRead(int chatId, int messageId);

    /// Marks everything in all of a user's chats as read, so none shows as unread. When each chat was last
    /// opened is left as it is.
    ///
    /// @param userUuid The user.
    /// @return How many chats had something unread.
    int markAllRead(String userUuid);

    /**
     * @param userUuid A user.
     * @param limit    The most to return.
     * @return The statuses of the user's chats, most recently changed first.
     */
    List<AiChatStatus> getChatStatuses(String userUuid, int limit);

    /// Finds the user's chats whose title, or one of whose questions, contains some text, ignoring case.
    ///
    /// @param userUuid The user.
    /// @param text     The text.
    /// @param limit    The most chats to return.
    /// @return The chats, most recently active first, each with the latest of its questions that matched, if
    /// any did.
    List<ChatSearchMatch> searchChats(String userUuid, String text, int limit);

    /**
     * @param userUuid A user.
     * @return A number that changes whenever the status of any of the user's chats might have.
     */
    long getStatusVersion(String userUuid);

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

    /// A chat found by [#searchChats].
    ///
    /// @param chatId       The chat.
    /// @param title        Its title.
    /// @param updateTimeMs When it was last active.
    /// @param question     The latest of its questions that matched, or null if only the title did.
    record ChatSearchMatch(int chatId, String title, long updateTimeMs, String question) {

    }
}
