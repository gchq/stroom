/*
 * Copyright 2025 Crown Copyright
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

import stroom.ai.shared.AiAttachmentDataPage;
import stroom.ai.shared.AiChat;
import stroom.ai.shared.AiChatMessage;
import stroom.ai.shared.AiChatPollRequest;
import stroom.ai.shared.AiChatPollResponse;
import stroom.ai.shared.AiChatStatusResponse;
import stroom.ai.shared.AiContextUsage;
import stroom.ai.shared.AiMcpPrompt;
import stroom.ai.shared.AiMcpPromptText;
import stroom.ai.shared.AiMemory;
import stroom.ai.shared.AiMemoryListResponse;
import stroom.ai.shared.AiToolApprovalsResponse;
import stroom.ai.shared.AnswerAiApprovalRequest;
import stroom.ai.shared.AnswerAiElicitationRequest;
import stroom.ai.shared.AskStroomAiConfig;
import stroom.ai.shared.AskStroomAiRequest;
import stroom.ai.shared.AskStroomAiResource;
import stroom.ai.shared.AskStroomAiResponse;
import stroom.ai.shared.CompactAiChatRequest;
import stroom.ai.shared.DeleteAiMemoryRequest;
import stroom.ai.shared.DownloadChatHistoryRequest;
import stroom.ai.shared.EditAiMessageRequest;
import stroom.ai.shared.FindAiChatHistoryCriteria;
import stroom.ai.shared.GetAiMcpPromptRequest;
import stroom.ai.shared.GetAttachmentDataRequest;
import stroom.ai.shared.SaveAiMemoryRequest;
import stroom.ai.shared.SetAiApproveEverythingRequest;
import stroom.ai.shared.UndoAiCompactionRequest;
import stroom.ai.shared.UndoAiMemoryChangeRequest;
import stroom.dispatch.client.RestErrorHandler;
import stroom.dispatch.client.RestFactory;
import stroom.preferences.client.UserPreferencesManager;
import stroom.task.client.TaskMonitorFactory;
import stroom.ui.config.shared.UserPreferences;
import stroom.util.shared.NullSafe;
import stroom.util.shared.ResourceGeneration;
import stroom.util.shared.ResultPage;

import com.google.gwt.core.client.GWT;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import java.util.List;
import java.util.function.Consumer;

@Singleton
public class AskStroomAiClient {

    private static final AskStroomAiResource RESOURCE = GWT.create(AskStroomAiResource.class);

    private final RestFactory restFactory;
    private final UserPreferencesManager userPreferencesManager;

    @Inject
    public AskStroomAiClient(final RestFactory restFactory,
                             final UserPreferencesManager userPreferencesManager) {
        this.restFactory = restFactory;
        this.userPreferencesManager = userPreferencesManager;
    }

    public void setConfig(final AskStroomAiConfig config, final TaskMonitorFactory taskMonitorFactory) {
        final UserPreferences currentPrefs = userPreferencesManager.getCurrentUserPreferences();
        final UserPreferences newPrefs = currentPrefs.copy()
                .askStroomAiConfig(config)
                .build();
        userPreferencesManager.setCurrentPreferences(newPrefs);
        userPreferencesManager.update(newPrefs, result -> {
        }, taskMonitorFactory);
    }

    void getConfig(final Consumer<AskStroomAiConfig> consumer, final TaskMonitorFactory taskMonitorFactory) {
        final AskStroomAiConfig config = NullSafe.get(userPreferencesManager.getCurrentUserPreferences(),
                UserPreferences::getAskStroomAiConfig);
        if (config != null) {
            consumer.accept(config);
        } else {
            // If the user preferences do not contain an AI config then load the defaults.
            getDefaultConfig(defaultConfig -> {
                final AskStroomAiConfig currentUserPrefConfig = NullSafe.get(
                        userPreferencesManager.getCurrentUserPreferences(), UserPreferences::getAskStroomAiConfig);
                if (currentUserPrefConfig == null) {
                    // Establish the user preference default config.
                    setConfig(defaultConfig, taskMonitorFactory);
                    consumer.accept(defaultConfig);
                } else {
                    consumer.accept(currentUserPrefConfig);
                }
            }, taskMonitorFactory);
        }
    }

    void getDefaultConfig(final Consumer<AskStroomAiConfig> consumer,
                          final TaskMonitorFactory taskMonitorFactory) {
        restFactory
                .create(RESOURCE)
                .method(AskStroomAiResource::getDefaultConfig)
                .onSuccess(consumer)
                .taskMonitorFactory(taskMonitorFactory)
                .exec();
    }

    void setDefaultAskStroomAIConfig(final AskStroomAiConfig config,
                                     final Consumer<Boolean> consumer,
                                     final TaskMonitorFactory taskMonitorFactory) {
        restFactory
                .create(RESOURCE)
                .method(res -> res.setDefaultAskStroomAIConfig(config))
                .onSuccess(consumer)
                .taskMonitorFactory(taskMonitorFactory)
                .exec();
    }

    void sendMessage(final AskStroomAiRequest request,
                     final Consumer<AskStroomAiResponse> consumer,
                     final RestErrorHandler errorHandler,
                     final TaskMonitorFactory taskMonitorFactory) {
        restFactory
                .create(RESOURCE)
                .method(res -> res.askStroomAi(
                        request))
                .onSuccess(consumer)
                .onFailure(errorHandler)
                .taskMonitorFactory(taskMonitorFactory)
                .exec();
    }

    void editMessage(final EditAiMessageRequest request,
                     final Consumer<AskStroomAiResponse> consumer,
                     final RestErrorHandler errorHandler,
                     final TaskMonitorFactory taskMonitorFactory) {
        restFactory
                .create(RESOURCE)
                .method(res -> res.editMessage(request))
                .onSuccess(consumer)
                .onFailure(errorHandler)
                .taskMonitorFactory(taskMonitorFactory)
                .exec();
    }

    void answerApproval(final AnswerAiApprovalRequest request,
                        final Consumer<Boolean> consumer,
                        final RestErrorHandler errorHandler,
                        final TaskMonitorFactory taskMonitorFactory) {
        restFactory
                .create(RESOURCE)
                .method(res -> res.answerApproval(request))
                .onSuccess(consumer)
                .onFailure(errorHandler)
                .taskMonitorFactory(taskMonitorFactory)
                .exec();
    }

    /**
     * Gets the memory a chat can see, and the changes the agent has made to memory in it.
     */
    void listMemory(final int chatId,
                    final Consumer<AiMemoryListResponse> consumer,
                    final RestErrorHandler errorHandler,
                    final TaskMonitorFactory taskMonitorFactory) {
        restFactory
                .create(RESOURCE)
                .method(res -> res.listMemory(chatId))
                .onSuccess(consumer)
                .onFailure(errorHandler)
                .taskMonitorFactory(taskMonitorFactory)
                .exec();
    }

    /**
     * Saves a piece of memory the user has written or edited.
     */
    void saveMemory(final SaveAiMemoryRequest request,
                    final Consumer<AiMemory> consumer,
                    final RestErrorHandler errorHandler,
                    final TaskMonitorFactory taskMonitorFactory) {
        restFactory
                .create(RESOURCE)
                .method(res -> res.saveMemory(request))
                .onSuccess(consumer)
                .onFailure(errorHandler)
                .taskMonitorFactory(taskMonitorFactory)
                .exec();
    }

    /**
     * Deletes a piece of memory, or all memory in a folder.
     */
    void deleteMemory(final DeleteAiMemoryRequest request,
                      final Consumer<Boolean> consumer,
                      final RestErrorHandler errorHandler,
                      final TaskMonitorFactory taskMonitorFactory) {
        restFactory
                .create(RESOURCE)
                .method(res -> res.deleteMemory(request))
                .onSuccess(consumer)
                .onFailure(errorHandler)
                .taskMonitorFactory(taskMonitorFactory)
                .exec();
    }

    /**
     * Undoes a change the agent made to memory.
     */
    void undoMemoryChange(final UndoAiMemoryChangeRequest request,
                          final Consumer<Boolean> consumer,
                          final RestErrorHandler errorHandler,
                          final TaskMonitorFactory taskMonitorFactory) {
        restFactory
                .create(RESOURCE)
                .method(res -> res.undoMemoryChange(request))
                .onSuccess(consumer)
                .onFailure(errorHandler)
                .taskMonitorFactory(taskMonitorFactory)
                .exec();
    }

    /**
     * Gets the decisions the user has made about tools in a chat.
     */
    void listToolApprovals(final int chatId,
                           final Consumer<AiToolApprovalsResponse> consumer,
                           final RestErrorHandler errorHandler,
                           final TaskMonitorFactory taskMonitorFactory) {
        restFactory
                .create(RESOURCE)
                .method(res -> res.listToolApprovals(chatId))
                .onSuccess(consumer)
                .onFailure(errorHandler)
                .taskMonitorFactory(taskMonitorFactory)
                .exec();
    }

    /**
     * Removes a decision the user made about a tool in a chat.
     */
    void removeToolApproval(final int chatId,
                            final int approvalId,
                            final Consumer<Boolean> consumer,
                            final RestErrorHandler errorHandler,
                            final TaskMonitorFactory taskMonitorFactory) {
        restFactory
                .create(RESOURCE)
                .method(res -> res.removeToolApproval(chatId, approvalId))
                .onSuccess(consumer)
                .onFailure(errorHandler)
                .taskMonitorFactory(taskMonitorFactory)
                .exec();
    }

    /**
     * Removes every decision the user made about tools in a chat.
     */
    void resetToolApprovals(final int chatId,
                            final Consumer<Boolean> consumer,
                            final RestErrorHandler errorHandler,
                            final TaskMonitorFactory taskMonitorFactory) {
        restFactory
                .create(RESOURCE)
                .method(res -> res.resetToolApprovals(chatId))
                .onSuccess(consumer)
                .onFailure(errorHandler)
                .taskMonitorFactory(taskMonitorFactory)
                .exec();
    }

    /**
     * Turns 'approve everything' on or off for a chat.
     */
    void setApproveEverything(final SetAiApproveEverythingRequest request,
                              final Consumer<Boolean> consumer,
                              final RestErrorHandler errorHandler,
                              final TaskMonitorFactory taskMonitorFactory) {
        restFactory
                .create(RESOURCE)
                .method(res -> res.setApproveEverything(request))
                .onSuccess(consumer)
                .onFailure(errorHandler)
                .taskMonitorFactory(taskMonitorFactory)
                .exec();
    }

    /**
     * Lists the prompts the model's MCP servers offer, as slash commands.
     */
    void listMcpPrompts(final AskStroomAiConfig config,
                        final Consumer<List<AiMcpPrompt>> consumer,
                        final RestErrorHandler errorHandler,
                        final TaskMonitorFactory taskMonitorFactory) {
        restFactory
                .create(RESOURCE)
                .method(res -> res.listMcpPrompts(config))
                .onSuccess(consumer)
                .onFailure(errorHandler)
                .taskMonitorFactory(taskMonitorFactory)
                .exec();
    }

    /**
     * Gets an MCP server's prompt, filled in.
     */
    void getMcpPrompt(final GetAiMcpPromptRequest request,
                      final Consumer<AiMcpPromptText> consumer,
                      final RestErrorHandler errorHandler,
                      final TaskMonitorFactory taskMonitorFactory) {
        restFactory
                .create(RESOURCE)
                .method(res -> res.getMcpPrompt(request))
                .onSuccess(consumer)
                .onFailure(errorHandler)
                .taskMonitorFactory(taskMonitorFactory)
                .exec();
    }

    /**
     * Answers a form an MCP tool asked the user to fill in.
     */
    void answerElicitation(final AnswerAiElicitationRequest request,
                           final Consumer<Boolean> consumer,
                           final RestErrorHandler errorHandler,
                           final TaskMonitorFactory taskMonitorFactory) {
        restFactory
                .create(RESOURCE)
                .method(res -> res.answerElicitation(request))
                .onSuccess(consumer)
                .onFailure(errorHandler)
                .taskMonitorFactory(taskMonitorFactory)
                .exec();
    }

    void undoCompaction(final UndoAiCompactionRequest request,
                        final Consumer<AiContextUsage> consumer,
                        final RestErrorHandler errorHandler,
                        final TaskMonitorFactory taskMonitorFactory) {
        restFactory
                .create(RESOURCE)
                .method(res -> res.undoCompaction(request))
                .onSuccess(consumer)
                .onFailure(errorHandler)
                .taskMonitorFactory(taskMonitorFactory)
                .exec();
    }

    void compactChat(final CompactAiChatRequest request,
                     final Consumer<AskStroomAiResponse> consumer,
                     final RestErrorHandler errorHandler,
                     final TaskMonitorFactory taskMonitorFactory) {
        restFactory
                .create(RESOURCE)
                .method(res -> res.compactChat(request))
                .onSuccess(consumer)
                .onFailure(errorHandler)
                .taskMonitorFactory(taskMonitorFactory)
                .exec();
    }

    void resumeRun(final AskStroomAiRequest request,
                   final Consumer<AskStroomAiResponse> consumer,
                   final RestErrorHandler errorHandler,
                   final TaskMonitorFactory taskMonitorFactory) {
        restFactory
                .create(RESOURCE)
                .method(res -> res.resumeRun(request))
                .onSuccess(consumer)
                .onFailure(errorHandler)
                .taskMonitorFactory(taskMonitorFactory)
                .exec();
    }

    void createChat(final Consumer<AiChat> consumer,
                    final TaskMonitorFactory taskMonitorFactory) {
        restFactory
                .create(RESOURCE)
                .method(AskStroomAiResource::createChat)
                .onSuccess(consumer)
                .taskMonitorFactory(taskMonitorFactory)
                .exec();
    }

    void listChats(final FindAiChatHistoryCriteria criteria,
                   final Consumer<ResultPage<AiChat>> consumer,
                   final TaskMonitorFactory taskMonitorFactory) {
        restFactory
                .create(RESOURCE)
                .method(res -> res.listChats(criteria))
                .onSuccess(consumer)
                .taskMonitorFactory(taskMonitorFactory)
                .exec();
    }

    void getChat(final int chatId,
                 final Consumer<AiChat> consumer,
                 final TaskMonitorFactory taskMonitorFactory) {
        restFactory
                .create(RESOURCE)
                .method(res -> res.getChat(chatId))
                .onSuccess(consumer)
                .taskMonitorFactory(taskMonitorFactory)
                .exec();
    }

    void deleteChat(final int chatId,
                    final Consumer<Boolean> consumer,
                    final TaskMonitorFactory taskMonitorFactory) {
        restFactory
                .create(RESOURCE)
                .method(res -> res.deleteChat(chatId))
                .onSuccess(consumer)
                .taskMonitorFactory(taskMonitorFactory)
                .exec();
    }

    void deleteMessage(final int chatId,
                       final int messageId,
                       final Consumer<Boolean> consumer,
                       final TaskMonitorFactory taskMonitorFactory) {
        restFactory
                .create(RESOURCE)
                .method(res -> res.deleteMessage(chatId, messageId))
                .onSuccess(consumer)
                .taskMonitorFactory(taskMonitorFactory)
                .exec();
    }

    void deleteAllMessages(final int chatId,
                           final Consumer<Boolean> consumer,
                           final TaskMonitorFactory taskMonitorFactory) {
        restFactory
                .create(RESOURCE)
                .method(res -> res.deleteAllMessages(chatId))
                .onSuccess(consumer)
                .taskMonitorFactory(taskMonitorFactory)
                .exec();
    }

    void getMessages(final int chatId,
                     final Consumer<List<AiChatMessage>> consumer,
                     final TaskMonitorFactory taskMonitorFactory) {
        restFactory
                .create(RESOURCE)
                .method(res -> res.getMessages(chatId))
                .onSuccess(consumer)
                .taskMonitorFactory(taskMonitorFactory)
                .exec();
    }

    void updateChatTitle(final int chatId,
                         final String title,
                         final Consumer<Boolean> consumer,
                         final TaskMonitorFactory taskMonitorFactory) {
        restFactory
                .create(RESOURCE)
                .method(res -> res.updateChatTitle(chatId, title))
                .onSuccess(consumer)
                .taskMonitorFactory(taskMonitorFactory)
                .exec();
    }

    void pollMessages(final int chatId,
                      final int lastSeenMessageId,
                      final boolean markRead,
                      final Consumer<AiChatPollResponse> consumer,
                      final RestErrorHandler errorHandler,
                      final TaskMonitorFactory taskMonitorFactory) {
        restFactory
                .create(RESOURCE)
                .method(res -> res.pollMessages(chatId, new AiChatPollRequest(lastSeenMessageId, markRead)))
                .onSuccess(consumer)
                .onFailure(errorHandler)
                .taskMonitorFactory(taskMonitorFactory)
                .exec();
    }

    void getChatStatuses(final long sinceVersion,
                         final Consumer<AiChatStatusResponse> consumer,
                         final TaskMonitorFactory taskMonitorFactory) {
        restFactory
                .create(RESOURCE)
                .method(res -> res.getChatStatuses(sinceVersion))
                .onSuccess(consumer)
                .onFailure(error -> {
                    // Badges are only a convenience, so a failure is ignored until the next check.
                })
                .taskMonitorFactory(taskMonitorFactory)
                .exec();
    }

    void cancelProcessing(final int chatId,
                          final Consumer<Boolean> consumer,
                          final TaskMonitorFactory taskMonitorFactory) {
        restFactory
                .create(RESOURCE)
                .method(res -> res.cancelProcessing(chatId))
                .onSuccess(consumer)
                .taskMonitorFactory(taskMonitorFactory)
                .exec();
    }

    void downloadChatHistory(final DownloadChatHistoryRequest request,
                             final Consumer<ResourceGeneration> consumer,
                             final RestErrorHandler errorHandler,
                             final TaskMonitorFactory taskMonitorFactory) {
        restFactory
                .create(RESOURCE)
                .method(res -> res.downloadChatHistory(request))
                .onSuccess(consumer)
                .onFailure(errorHandler)
                .taskMonitorFactory(taskMonitorFactory)
                .exec();
    }

    void getAttachmentData(final GetAttachmentDataRequest request,
                           final Consumer<AiAttachmentDataPage> consumer,
                           final RestErrorHandler errorHandler,
                           final TaskMonitorFactory taskMonitorFactory) {
        restFactory
                .create(RESOURCE)
                .method(res -> res.getAttachmentData(request))
                .onSuccess(consumer)
                .onFailure(errorHandler)
                .taskMonitorFactory(taskMonitorFactory)
                .exec();
    }
}
