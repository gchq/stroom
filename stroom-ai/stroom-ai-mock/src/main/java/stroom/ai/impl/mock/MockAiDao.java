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

package stroom.ai.impl.mock;

import stroom.ai.impl.AiDao;
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

public class MockAiDao implements AiDao {

    @Override
    public AiChat createChat(final UserRef userRef) {
        return null;
    }

    @Override
    public ResultPage<AiChat> listChats(final UserRef userRef, final FindAiChatHistoryCriteria criteria) {
        return null;
    }

    @Override
    public Optional<AiChat> getChat(final int chatId) {
        return Optional.empty();
    }

    @Override
    public void updateChatTitle(final int chatId, final String title) {

    }

    @Override
    public void deleteChat(final int chatId) {

    }

    @Override
    public AiChatMessage storeMessage(final int chatId, final AiMessageType messageType, final String message) {
        return null;
    }

    @Override
    public AiChatMessage storeMessage(final int chatId,
                                      final AiMessageType messageType,
                                      final Integer attachmentId,
                                      final String message) {
        return null;
    }

    @Override
    public List<AiChatMessage> getMessages(final int chatId) {
        return List.of();
    }

    @Override
    public List<AiChatMessage> getMessagesSince(final int chatId, final int lastSeenMessageId) {
        return List.of();
    }

    @Override
    public Optional<AiChatMessage> getWorkingMessage(final int chatId) {
        return Optional.empty();
    }

    @Override
    public void deleteWorkingMessages(final int chatId) {

    }

    @Override
    public List<AiChatMessage> getStreamingMessages(final int chatId) {
        return List.of();
    }

    @Override
    public void updateMessageText(final int messageId, final String message) {

    }

    @Override
    public void deleteMessage(final int messageId) {

    }

    @Override
    public void deleteAttachment(final int attachmentId) {

    }

    @Override
    public void deleteAllChatMessagesAndAttachments(final int chatId) {

    }

    @Override
    public AiChatAttachment createAttachment(final int chatId, final AiAttachmentType type, final String contextJson) {
        return null;
    }

    @Override
    public void updateAttachmentStatus(final int attachmentId,
                                       final AiAttachmentStatus status,
                                       final Integer rowCount,
                                       final String description,
                                       final String errorMessage,
                                       final boolean truncated) {

    }

    @Override
    public Optional<AiChatAttachment> getAttachment(final int attachmentId) {
        return Optional.empty();
    }

    @Override
    public List<AiChatAttachment> getAttachmentsByChatId(final int chatId) {
        return List.of();
    }

    @Override
    public void recordPoll(final int chatId, final long minIntervalMs) {
        // Presence is not tracked by the mock.
    }

    @Override
    public Long getLastPollMs(final String userUuid) {
        return null;
    }

    @Override
    public void recordUsage(final int chatId,
                            final String modelDocUuid,
                            final int contextWindowTokens,
                            final long inputTokens,
                            final long outputTokens) {
        // Usage is not tracked by the mock.
    }

    @Override
    public ChatUsage getUsage(final int chatId) {
        return new ChatUsage(null, null, null, 0, 0);
    }

    @Override
    public List<Integer> findChatsNotUpdatedSince(final long timeMs, final int limit) {
        return List.of();
    }

    @Override
    public List<String> getChatOwners() {
        return List.of();
    }

    @Override
    public List<Integer> getChatIds(final String userUuid) {
        return List.of();
    }

    @Override
    public boolean compareAndSetMessageText(final int messageId, final String expected, final String message) {
        return false;
    }

    @Override
    public boolean isApproveEverything(final int chatId) {
        return false;
    }

    @Override
    public void setApproveEverything(final int chatId, final boolean enabled) {
        // Not supported by the mock.
    }

    @Override
    public List<AiChatMessage> getMessagesOfTypeBefore(final AiMessageType messageType, final long before) {
        return List.of();
    }

    @Override
    public void markRead(final int chatId, final int messageId) {
        // Not tracked by the mock.
    }

    @Override
    public List<AiChatStatus> getChatStatuses(final String userUuid, final int limit) {
        return List.of();
    }

    @Override
    public long getStatusVersion(final String userUuid) {
        return 0;
    }
}
