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
import stroom.util.shared.http.HttpClientConfig;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.scoring.ScoringModel;

import java.util.List;
import java.util.Optional;

public interface AiService {

    HttpClientConfig getDefaultHttpClientConfig();

    // ---------------------------------------------------------------------
    // Model operations
    // ---------------------------------------------------------------------

    OpenAIModelDoc getOpenAIModelDoc(DocRef docRef);

    /**
     * Resolve a model reference of the sort supplied to the {@code ai()} XSLT and StroomQL functions, i.e.
     * either the UUID or the name of an {@link OpenAIModelDoc}. UUID is tried first, so a model whose name
     * happens to be another model's UUID cannot be used to reach that other model.
     *
     * @return The matching model, or empty if there is no match or the current user cannot view it. Where
     * more than one model shares the supplied name, which of them is returned is stable but arbitrary, as
     * the store orders by UUID, so a name that is not unique is not a dependable way to reach a model.
     */
    Optional<DocRef> findModelByNameOrUuid(String nameOrUuid);

    /**
     * Ask a model a single question and get its answer, i.e. a one-shot chat with no history.
     *
     * @param modelRef     The model to ask.
     * @param systemPrompt The system prompt to send ahead of the message. May be null.
     * @param message      The message to ask the model.
     * @return The model's answer, or null if it had nothing to say.
     */
    String chat(DocRef modelRef, String systemPrompt, String message);

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

    /**
     * @return The chat's WORKING message if one is in place, i.e. if a question is being processed.
     */
    Optional<AiChatMessage> getWorkingMessage(int chatId);

    /**
     * Removes every WORKING message for the chat, including any left behind by a server that stopped
     * mid-question.
     */
    void deleteWorkingMessages(int chatId);

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
