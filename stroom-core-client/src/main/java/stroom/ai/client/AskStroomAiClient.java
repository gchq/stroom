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
import stroom.ai.shared.AskStroomAiConfig;
import stroom.ai.shared.AskStroomAiRequest;
import stroom.ai.shared.AskStroomAiResource;
import stroom.ai.shared.AskStroomAiResponse;
import stroom.ai.shared.DownloadChatHistoryRequest;
import stroom.ai.shared.FindAiChatHistoryCriteria;
import stroom.ai.shared.GetAttachmentDataRequest;
import stroom.dispatch.client.RestErrorHandler;
import stroom.dispatch.client.RestFactory;
import stroom.preferences.client.UserPreferencesManager;
import stroom.ui.config.shared.UserPreferences;
import stroom.util.shared.ResourceGeneration;
import stroom.util.shared.ResultPage;
import stroom.widget.component.client.HasControllerNode;
import stroom.widget.task.client.TaskMonitorFactory;

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

    /// Made by GIN.
    ///
    /// @param restFactory            Makes REST requests.
    /// @param userPreferencesManager The user's preferences, which hold their settings.
    @Inject
    public AskStroomAiClient(final RestFactory restFactory,
                             final UserPreferencesManager userPreferencesManager) {
        this.restFactory = restFactory;
        this.userPreferencesManager = userPreferencesManager;
    }

    /// Saves the user's own settings, in their preferences.
    ///
    /// @param config             The settings.
    /// @param taskMonitorFactory Shows the save is in progress.
    public void setConfig(final AskStroomAiConfig config,
                          final TaskMonitorFactory taskMonitorFactory) {
        // Wait for the user's preferences, so that saving the config doesn't lose them
        userPreferencesManager.whenLoaded(currentPrefs -> {
            final UserPreferences newPrefs = currentPrefs.copy()
                    .askStroomAiConfig(config)
                    .build();
            userPreferencesManager.setCurrentPreferences(newPrefs);
            userPreferencesManager.update(newPrefs, result -> {
            }, taskMonitorFactory);
        });
    }

    /// Gives the user's own settings or, if they have none, the default settings, which then become theirs.
    ///
    /// @param consumer           Given the settings.
    /// @param taskMonitorFactory Shows the request is in progress.
    public void getConfig(final Consumer<AskStroomAiConfig> consumer,
                          final TaskMonitorFactory taskMonitorFactory) {
        // Wait for the user's preferences, so that the user's own config is used if they have one
        userPreferencesManager.whenLoaded(userPreferences -> {
            final AskStroomAiConfig config = userPreferences.getAskStroomAiConfig();
            if (config != null) {
                consumer.accept(config);
            } else {
                // If the user preferences do not contain an AI config then load the defaults.
                getDefaultConfig(defaultConfig -> {
                    final AskStroomAiConfig currentUserPrefConfig =
                            userPreferencesManager.getCurrentUserPreferences().getAskStroomAiConfig();
                    if (currentUserPrefConfig == null) {
                        // Establish the user preference default config.
                        setConfig(defaultConfig, taskMonitorFactory);
                        consumer.accept(defaultConfig);
                    } else {
                        consumer.accept(currentUserPrefConfig);
                    }
                }, taskMonitorFactory);
            }
        });
    }

    /// Fetches the settings every user starts with.
    ///
    /// @param consumer           Given the default settings.
    /// @param taskMonitorFactory Shows the fetch is in progress.
    public void getDefaultConfig(final Consumer<AskStroomAiConfig> consumer,
                                 final TaskMonitorFactory taskMonitorFactory) {
        restFactory
                .create(RESOURCE)
                .method(AskStroomAiResource::getDefaultConfig)
                .onSuccess(consumer)
                .taskMonitorFactory(taskMonitorFactory)
                .exec();
    }

    /// Saves the settings every user starts with.
    ///
    /// @param config             The new default settings.
    /// @param consumer           Told whether they were saved.
    /// @param taskMonitorFactory Shows the save is in progress.
    public void setDefaultAskStroomAIConfig(final AskStroomAiConfig config,
                                            final Consumer<Boolean> consumer,
                                            final TaskMonitorFactory taskMonitorFactory) {
        restFactory
                .create(RESOURCE)
                .method(res -> res.setDefaultAskStroomAIConfig(config))
                .onSuccess(consumer)
                .taskMonitorFactory(taskMonitorFactory)
                .exec();
    }

    /// Sends a message to the AI.
    ///
    /// @param request            The message, with its conversation and attached data.
    /// @param consumer           Given the AI's reply.
    /// @param errorHandler       Told why the message couldn't be sent.
    /// @param taskMonitorFactory Shows the request is in progress.
    public void sendMessage(final AskStroomAiRequest request,
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

    /// Starts a new conversation.
    ///
    /// @param consumer           Given the conversation.
    /// @param taskMonitorFactory Shows the request is in progress.
    public void createChat(final Consumer<AiChat> consumer,
                           final TaskMonitorFactory taskMonitorFactory) {
        restFactory
                .create(RESOURCE)
                .method(AskStroomAiResource::createChat)
                .onSuccess(consumer)
                .taskMonitorFactory(taskMonitorFactory)
                .exec();
    }

    /// Finds the user's conversations.
    ///
    /// @param criteria           Which to find, and in what order.
    /// @param consumer           Given a page of them.
    /// @param taskMonitorFactory Shows the request is in progress.
    public void listChats(final FindAiChatHistoryCriteria criteria,
                          final Consumer<ResultPage<AiChat>> consumer,
                          final TaskMonitorFactory taskMonitorFactory) {
        restFactory
                .create(RESOURCE)
                .method(res -> res.listChats(criteria))
                .onSuccess(consumer)
                .taskMonitorFactory(taskMonitorFactory)
                .exec();
    }

    /// Fetches a conversation.
    ///
    /// @param chatId             The conversation's id.
    /// @param consumer           Given the conversation.
    /// @param taskMonitorFactory Shows the request is in progress.
    public void getChat(final int chatId,
                        final Consumer<AiChat> consumer,
                        final TaskMonitorFactory taskMonitorFactory) {
        restFactory
                .create(RESOURCE)
                .method(res -> res.getChat(chatId))
                .onSuccess(consumer)
                .taskMonitorFactory(taskMonitorFactory)
                .exec();
    }

    /// Deletes a conversation.
    ///
    /// @param chatId             The conversation's id.
    /// @param consumer           Told whether it was deleted.
    /// @param taskMonitorFactory Shows the request is in progress.
    public void deleteChat(final int chatId,
                           final Consumer<Boolean> consumer,
                           final TaskMonitorFactory taskMonitorFactory) {
        restFactory
                .create(RESOURCE)
                .method(res -> res.deleteChat(chatId))
                .onSuccess(consumer)
                .taskMonitorFactory(taskMonitorFactory)
                .exec();
    }

    /// Deletes a message from a conversation.
    ///
    /// @param chatId             The conversation's id.
    /// @param messageId          The message's id.
    /// @param consumer           Told whether it was deleted.
    /// @param taskMonitorFactory Shows the request is in progress.
    public void deleteMessage(final int chatId,
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

    /// Deletes all a conversation's messages.
    ///
    /// @param chatId             The conversation's id.
    /// @param consumer           Told whether they were deleted.
    /// @param taskMonitorFactory Shows the request is in progress.
    public void deleteAllMessages(final int chatId,
                                  final Consumer<Boolean> consumer,
                                  final TaskMonitorFactory taskMonitorFactory) {
        restFactory
                .create(RESOURCE)
                .method(res -> res.deleteAllMessages(chatId))
                .onSuccess(consumer)
                .taskMonitorFactory(taskMonitorFactory)
                .exec();
    }

    /// Fetches a conversation's messages.
    ///
    /// @param chatId             The conversation's id.
    /// @param consumer           Given the messages.
    /// @param taskMonitorFactory Shows the request is in progress.
    public void getMessages(final int chatId,
                            final Consumer<List<AiChatMessage>> consumer,
                            final TaskMonitorFactory taskMonitorFactory) {
        restFactory
                .create(RESOURCE)
                .method(res -> res.getMessages(chatId))
                .onSuccess(consumer)
                .taskMonitorFactory(taskMonitorFactory)
                .exec();
    }

    /// Renames a conversation.
    ///
    /// @param chatId             The conversation's id.
    /// @param title              Its new title.
    /// @param consumer           Told whether it was renamed.
    /// @param taskMonitorFactory Shows the request is in progress.
    public void updateChatTitle(final int chatId,
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

    /// Asks for a conversation's messages newer than those seen, and what the AI is doing.
    ///
    /// @param chatId             The conversation's id.
    /// @param lastSeenMessageId  The id of the newest message seen.
    /// @param consumer           Given the new messages and what the AI is doing.
    /// @param errorHandler       Told why the request failed.
    /// @param taskMonitorFactory Shows the request is in progress.
    public void pollMessages(final int chatId,
                             final int lastSeenMessageId,
                             final Consumer<AiChatPollResponse> consumer,
                             final RestErrorHandler errorHandler,
                             final TaskMonitorFactory taskMonitorFactory) {
        restFactory
                .create(RESOURCE)
                .method(res -> res.pollMessages(chatId, new AiChatPollRequest(lastSeenMessageId)))
                .onSuccess(consumer)
                .onFailure(errorHandler)
                .taskMonitorFactory(taskMonitorFactory)
                .exec();
    }

    /// Asks the AI to stop working on the last message sent.
    ///
    /// @param chatId             The conversation's id.
    /// @param consumer           Told whether it stopped.
    /// @param taskMonitorFactory Shows the request is in progress.
    public void cancelProcessing(final int chatId,
                                 final Consumer<Boolean> consumer,
                                 final TaskMonitorFactory taskMonitorFactory) {
        restFactory
                .create(RESOURCE)
                .method(res -> res.cancelProcessing(chatId))
                .onSuccess(consumer)
                .taskMonitorFactory(taskMonitorFactory)
                .exec();
    }

    /// Makes a file of a conversation, to download.
    ///
    /// @param request            The conversation, and the file to make.
    /// @param consumer           Given the file made.
    /// @param errorHandler       Told why it couldn't be made.
    /// @param taskMonitorFactory Shows the request is in progress.
    public void downloadChatHistory(final DownloadChatHistoryRequest request,
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

    /// Fetches a page of an attachment's table.
    ///
    /// @param owner              What the reply is for: it is dropped once that is disposed.
    /// @param request            The attachment, and the page of it.
    /// @param consumer           Given the page.
    /// @param errorHandler       Told why it failed.
    /// @param taskMonitorFactory Shows the request's progress.
    public void getAttachmentData(final HasControllerNode owner,
                                  final GetAttachmentDataRequest request,
                                  final Consumer<AiAttachmentDataPage> consumer,
                                  final RestErrorHandler errorHandler,
                                  final TaskMonitorFactory taskMonitorFactory) {
        restFactory
                .create(RESOURCE)
                .method(res -> res.getAttachmentData(request))
                .onSuccess(consumer)
                .onFailure(errorHandler)
                .owner(owner)
                .taskMonitorFactory(taskMonitorFactory)
                .exec();
    }
}
