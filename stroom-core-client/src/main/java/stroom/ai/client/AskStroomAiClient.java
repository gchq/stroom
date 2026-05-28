package stroom.ai.client;

import stroom.ai.shared.AiChat;
import stroom.ai.shared.AiChatMessage;
import stroom.ai.shared.AiChatPollRequest;
import stroom.ai.shared.AiChatPollResponse;
import stroom.ai.shared.AskStroomAIConfig;
import stroom.ai.shared.AskStroomAiRequest;
import stroom.ai.shared.AskStroomAiResource;
import stroom.ai.shared.AskStroomAiResponse;
import stroom.ai.shared.DownloadChatHistoryRequest;
import stroom.ai.shared.FindAiChatHistoryCriteria;
import stroom.dispatch.client.RestErrorHandler;
import stroom.dispatch.client.RestFactory;
import stroom.task.client.TaskMonitorFactory;
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
    private AskStroomAIConfig config;

    @Inject
    public AskStroomAiClient(final RestFactory restFactory) {
        this.restFactory = restFactory;
    }

    public void setConfig(final AskStroomAIConfig config) {
        this.config = config;
    }

    void getConfig(final Consumer<AskStroomAIConfig> consumer, final TaskMonitorFactory taskMonitorFactory) {
        if (config != null) {
            consumer.accept(config);
        } else {
            restFactory
                    .create(RESOURCE)
                    .method(AskStroomAiResource::getDefaultConfig)
                    .onSuccess(conf -> {
                        if (config == null) {
                            config = conf;
                        }
                        consumer.accept(config);
                    })
                    .taskMonitorFactory(taskMonitorFactory)
                    .exec();
        }
    }

    void setDefaultAskStroomAIConfig(final AskStroomAIConfig config,
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
}
