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

import stroom.ai.shared.AiAgentMode;
import stroom.ai.shared.AiAttachmentDataPage;
import stroom.ai.shared.AiChat;
import stroom.ai.shared.AiChatMessage;
import stroom.ai.shared.AiChatPollRequest;
import stroom.ai.shared.AiChatPollResponse;
import stroom.ai.shared.AiChatSearchHit;
import stroom.ai.shared.AiChatStatusResponse;
import stroom.ai.shared.AiContextUsage;
import stroom.ai.shared.AiMcpPrompt;
import stroom.ai.shared.AiMcpPromptText;
import stroom.ai.shared.AiMemory;
import stroom.ai.shared.AiMemoryListResponse;
import stroom.ai.shared.AiToolApprovalsResponse;
import stroom.ai.shared.AiUserActionResult;
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
import stroom.ai.shared.GetAiMcpPromptRequest;
import stroom.ai.shared.GetAttachmentDataRequest;
import stroom.ai.shared.SaveAiMemoryRequest;
import stroom.ai.shared.SearchAiChatsRequest;
import stroom.ai.shared.SetAiApproveEverythingRequest;
import stroom.ai.shared.TableAnalysisConfig;
import stroom.ai.shared.UndoAiCompactionRequest;
import stroom.ai.shared.UndoAiMemoryChangeRequest;
import stroom.dispatch.client.RestErrorHandler;
import stroom.dispatch.client.RestFactory;
import stroom.preferences.client.UserPreferencesManager;
import stroom.task.client.TaskMonitorFactory;
import stroom.ui.config.shared.UserPreferences;
import stroom.util.shared.NullSafe;
import stroom.util.shared.ResourceGeneration;

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
    // The configured defaults, once fetched.
    private AskStroomAiConfig defaultConfig;

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

    /// Gets the user's Ask Stroom AI settings. A user who has not changed any gets the configured defaults, which
    /// are not copied into their preferences, so they follow any later change to the defaults. A user whose
    /// settings have no model (e.g. saved before a model was set up) gets the default model.
    void getConfig(final Consumer<AskStroomAiConfig> consumer, final TaskMonitorFactory taskMonitorFactory) {
        final AskStroomAiConfig config = NullSafe.get(userPreferencesManager.getCurrentUserPreferences(),
                UserPreferences::getAskStroomAiConfig);
        if (config != null && config.getModelRef() != null) {
            consumer.accept(config);
        } else {
            getDefaultConfig(defaultConfig -> consumer.accept(config == null
                    ? defaultConfig
                    : config.copy().modelRef(defaultConfig.getModelRef()).build()), taskMonitorFactory);
        }
    }

    /// Resets every user's Ask Stroom AI settings to the configured defaults. Needs the Manage Properties
    /// permission.
    ///
    /// @param consumer Given how many users' (and default) preference records were changed.
    void resetAllUsersConfig(final Consumer<Integer> consumer, final TaskMonitorFactory taskMonitorFactory) {
        restFactory
                .create(RESOURCE)
                .method(AskStroomAiResource::resetAllUsersConfig)
                .onSuccess(count -> {
                    defaultConfig = null;
                    // This user's own settings were reset too, so stop using the ones loaded earlier.
                    final UserPreferences current = userPreferencesManager.getCurrentUserPreferences();
                    if (current != null) {
                        userPreferencesManager.setCurrentPreferences(current.copy()
                                .askStroomAiConfig(null)
                                .build());
                    }
                    consumer.accept(count);
                })
                .taskMonitorFactory(taskMonitorFactory)
                .exec();
    }

    void getDefaultConfig(final Consumer<AskStroomAiConfig> consumer,
                          final TaskMonitorFactory taskMonitorFactory) {
        // Users who have not changed their settings use the defaults all the time, so keep them.
        if (defaultConfig != null) {
            consumer.accept(defaultConfig);
            return;
        }
        restFactory
                .create(RESOURCE)
                .method(AskStroomAiResource::getDefaultConfig)
                .onSuccess(config -> {
                    defaultConfig = config;
                    consumer.accept(config);
                })
                .taskMonitorFactory(taskMonitorFactory)
                .exec();
    }

    /// Sets the table analysis settings, which apply to everyone. Needs permission to manage properties.
    void setTableAnalysisConfig(final TableAnalysisConfig config,
                                final Consumer<Boolean> consumer,
                                final TaskMonitorFactory taskMonitorFactory) {
        restFactory
                .create(RESOURCE)
                .method(res -> res.setTableAnalysisConfig(config))
                .onSuccess(result -> {
                    // Fetch the new defaults next time.
                    defaultConfig = null;
                    consumer.accept(result);
                })
                .taskMonitorFactory(taskMonitorFactory)
                .exec();
    }

    void setDefaultAskStroomAIConfig(final AskStroomAiConfig config,
                                     final Consumer<Boolean> consumer,
                                     final TaskMonitorFactory taskMonitorFactory) {
        restFactory
                .create(RESOURCE)
                .method(res -> res.setDefaultAskStroomAIConfig(config))
                .onSuccess(result -> {
                    // Fetch the new defaults next time.
                    defaultConfig = null;
                    consumer.accept(result);
                })
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

    /// Does what a button that an MCP tool's result offered does, once the server has checked it.
    void doUserAction(final int chatId,
                      final int messageId,
                      final String actionId,
                      final Consumer<AiUserActionResult> consumer,
                      final TaskMonitorFactory taskMonitorFactory) {
        restFactory
                .create(RESOURCE)
                .method(res -> res.doUserAction(chatId, messageId, actionId))
                .onSuccess(consumer)
                .taskMonitorFactory(taskMonitorFactory)
                .exec();
    }

    /// Deletes all the user's chats. The consumer is given how many were deleted.
    void deleteAllChats(final Consumer<Integer> consumer, final TaskMonitorFactory taskMonitorFactory) {
        restFactory
                .create(RESOURCE)
                .method(AskStroomAiResource::deleteAllChats)
                .onSuccess(consumer)
                .taskMonitorFactory(taskMonitorFactory)
                .exec();
    }

    /// Marks everything in all the user's chats as read, clearing their unread marks.
    void markAllChatsRead(final Consumer<Integer> consumer, final TaskMonitorFactory taskMonitorFactory) {
        restFactory
                .create(RESOURCE)
                .method(AskStroomAiResource::markAllChatsRead)
                .onSuccess(consumer)
                .taskMonitorFactory(taskMonitorFactory)
                .exec();
    }

    /// Sets how the agent works in a chat.
    void setAgentMode(final int chatId,
                      final AiAgentMode mode,
                      final TaskMonitorFactory taskMonitorFactory) {
        restFactory
                .create(RESOURCE)
                .method(res -> res.setAgentMode(chatId, mode))
                .onSuccess(done -> {
                })
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

    /// Searches the user's chats by their titles and the user's questions in them.
    void searchChats(final String text,
                     final Consumer<List<AiChatSearchHit>> consumer,
                     final TaskMonitorFactory taskMonitorFactory) {
        restFactory
                .create(RESOURCE)
                .method(res -> res.searchChats(new SearchAiChatsRequest(text)))
                .onSuccess(consumer)
                .taskMonitorFactory(taskMonitorFactory)
                .exec();
    }

    /// Clears a chat so it starts afresh, keeping the chat itself.
    void clearChat(final int chatId,
                   final Consumer<Boolean> consumer,
                   final TaskMonitorFactory taskMonitorFactory) {
        restFactory
                .create(RESOURCE)
                .method(res -> res.clearChat(chatId))
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

    void getMessages(final int chatId,
                     final Consumer<List<AiChatMessage>> consumer,
                     final RestErrorHandler errorHandler,
                     final TaskMonitorFactory taskMonitorFactory) {
        restFactory
                .create(RESOURCE)
                .method(res -> res.getMessages(chatId))
                .onSuccess(consumer)
                .onFailure(errorHandler)
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
                      final List<Integer> watchedMessageIds,
                      final Consumer<AiChatPollResponse> consumer,
                      final RestErrorHandler errorHandler,
                      final TaskMonitorFactory taskMonitorFactory) {
        restFactory
                .create(RESOURCE)
                .method(res -> res.pollMessages(chatId, new AiChatPollRequest(lastSeenMessageId, markRead,
                        watchedMessageIds)))
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
