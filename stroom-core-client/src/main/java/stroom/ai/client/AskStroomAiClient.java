package stroom.ai.client;

import stroom.ai.shared.AskStroomAIConfig;
import stroom.ai.shared.AskStroomAiRequest;
import stroom.ai.shared.AskStroomAiResource;
import stroom.ai.shared.AskStroomAiResponse;
import stroom.ai.shared.ChatMemoryConfig;
import stroom.ai.shared.TableSummaryConfig;
import stroom.dispatch.client.RestErrorHandler;
import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.task.client.TaskMonitorFactory;

import com.google.gwt.core.client.GWT;
import com.google.inject.Inject;
import com.google.inject.Singleton;

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

    void setDefaultModel(final DocRef modelRef,
                         final Consumer<Boolean> consumer,
                         final TaskMonitorFactory taskMonitorFactory) {
        restFactory
                .create(RESOURCE)
                .method(res -> res.setDefaultModel(modelRef))
                .onSuccess(consumer)
                .taskMonitorFactory(taskMonitorFactory)
                .exec();
    }

    void setDefaultTableSummaryConfig(final TableSummaryConfig tableSummaryConfig,
                                      final Consumer<Boolean> consumer,
                                      final TaskMonitorFactory taskMonitorFactory) {
        restFactory
                .create(RESOURCE)
                .method(res -> res.setDefaultTableSummaryConfig(tableSummaryConfig))
                .onSuccess(consumer)
                .taskMonitorFactory(taskMonitorFactory)
                .exec();
    }

    void setDefaultChatMemoryConfigConfig(final ChatMemoryConfig chatMemoryConfig,
                                          final Consumer<Boolean> consumer,
                                          final TaskMonitorFactory taskMonitorFactory) {
        restFactory
                .create(RESOURCE)
                .method(res -> res.setDefaultChatMemoryConfigConfig(chatMemoryConfig))
                .onSuccess(consumer)
                .taskMonitorFactory(taskMonitorFactory)
                .exec();
    }

    void sendMessage(final String node,
                     final AskStroomAiRequest request,
                     final Consumer<AskStroomAiResponse> consumer,
                     final RestErrorHandler errorHandler,
                     final TaskMonitorFactory taskMonitorFactory) {
        restFactory
                .create(RESOURCE)
                .method(res -> res.askStroomAi(
                        node,
                        request))
                .onSuccess(consumer)
                .onFailure(errorHandler)
                .taskMonitorFactory(taskMonitorFactory)
                .exec();
    }
}
