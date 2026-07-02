/*
 * Copyright 2016-2026 Crown Copyright
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

package stroom.ai.api;

import stroom.ai.shared.AiAttachmentStatus;
import stroom.ai.shared.AiAttachmentType;
import stroom.ai.shared.AiChat;
import stroom.ai.shared.AiChatAttachment;
import stroom.ai.shared.AiChatMessage;
import stroom.ai.shared.AiMessageType;
import stroom.ai.shared.FindAiChatHistoryCriteria;
import stroom.docref.DocRef;
import stroom.openai.shared.OpenAIModelDoc;
import stroom.util.shared.ResultPage;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.scoring.ScoringModel;

import java.util.List;
import java.util.Optional;

public interface AiService {

    // ---------------------------------------------------------------------
    // Model operations
    // ---------------------------------------------------------------------

    OpenAIModelDoc getOpenAIModelDoc(DocRef docRef);

    String getModel(OpenAIModelDoc modelDoc);

    ChatModel getChatModel(OpenAIModelDoc modelDoc);

    EmbeddingModel getEmbeddingModel(OpenAIModelDoc modelDoc);

    ScoringModel getCohereScoringModel(OpenAIModelDoc modelDoc);

    ScoringModel getJinaScoringModel(OpenAIModelDoc modelDoc);

    // ---------------------------------------------------------------------
    // Chat persistence operations
    // ---------------------------------------------------------------------

    AiChat createChat();

    ResultPage<AiChat> listChats(FindAiChatHistoryCriteria criteria);

    AiChat getChat(int chatId);

    void updateChatTitle(int chatId, String title);

    void deleteChat(int chatId);

    AiChatMessage storeMessage(int chatId, AiMessageType messageType, String message);

    AiChatMessage storeMessage(int chatId, AiMessageType messageType, Integer attachmentId, String message);

    List<AiChatMessage> getMessages(int chatId);

    List<AiChatMessage> getMessagesSince(int chatId, int lastSeenMessageId);

    void updateMessageText(int messageId, String message);

    void deleteMessage(int messageId);

    void deleteAttachment(int attachmentId);

    void deleteAllChatMessagesAndAttachments(int chatId);

    void verifyOwnership(int chatId);

    void verifyOwnership(AiChat chat);

    // ---------------------------------------------------------------------
    // Attachment operations
    // ---------------------------------------------------------------------

    AiChatAttachment createAttachment(int chatId, AiAttachmentType type, String contextJson);

    void updateAttachmentStatus(int attachmentId, AiAttachmentStatus status,
                                Integer rowCount, String description,
                                String errorMessage, boolean truncated);

    Optional<AiChatAttachment> getAttachment(int attachmentId);

    List<AiChatAttachment> getAttachmentsByChatId(int chatId);
}
