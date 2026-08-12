package stroom.ai.client;

import stroom.ai.shared.AskStroomAIConfig;
import stroom.ai.shared.AskStroomAiRequest;
import stroom.ai.shared.AskStroomAiResource;
import stroom.ai.shared.AskStroomAiResponse;
import stroom.ai.shared.ChatMemoryConfig;
import stroom.ai.shared.TableSummaryConfig;
import stroom.config.global.shared.ConfigTarget;
import stroom.config.global.shared.GlobalConfigResource;
import stroom.config.global.shared.SetConfigValueRequest;
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
    private static final GlobalConfigResource CONFIG_RESOURCE = GWT.create(GlobalConfigResource.class);

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

    /**
     * Setting the default model is just setting a config property, so it goes through the one mechanism for that
     * rather than an endpoint of its own. The server converts the doc ref to the form the property expects.
     */
    void setDefaultModel(final DocRef modelRef,
                         final Consumer<Boolean> consumer,
                         final TaskMonitorFactory taskMonitorFactory) {
        restFactory
                .create(CONFIG_RESOURCE)
                .method(res -> res.setConfigValue(SetConfigValueRequest.docRef(
                        ConfigTarget.ASK_STROOM_AI,
                        AskStroomAIConfig.PROP_NAME_MODEL_REF,
                        modelRef)))
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
