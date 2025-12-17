/*
 * Copyright 2017 Crown Copyright
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

package stroom.query.impl;

import stroom.ai.shared.AskStroomAIConfig;
import stroom.ai.shared.AskStroomAiRequest;
import stroom.ai.shared.AskStroomAiResource;
import stroom.ai.shared.AskStroomAiResponse;
import stroom.ai.shared.ChatMemoryConfig;
import stroom.ai.shared.TableSummaryConfig;
import stroom.docref.DocRef;
import stroom.event.logging.rs.api.AutoLogged;
import stroom.event.logging.rs.api.AutoLogged.OperationType;
import stroom.node.api.NodeService;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.ResourcePaths;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.ws.rs.client.Entity;

@AutoLogged
class AskStroomAiResourceImpl implements AskStroomAiResource {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(AskStroomAiResourceImpl.class);

    private final Provider<NodeService> nodeServiceProvider;
    private final Provider<AskStroomAIService> askStroomAIServiceProvider;

    @Inject
    AskStroomAiResourceImpl(final Provider<NodeService> nodeServiceProvider,
                            final Provider<AskStroomAIService> askStroomAIServiceProvider) {
        this.nodeServiceProvider = nodeServiceProvider;
        this.askStroomAIServiceProvider = askStroomAIServiceProvider;
    }

    @AutoLogged(OperationType.MANUALLY_LOGGED)
    @Override
    public AskStroomAiResponse askStroomAi(final String nodeName, final AskStroomAiRequest request) {
        final AskStroomAIService aiService = askStroomAIServiceProvider.get();
        try {
            // Make sure we ask the question on the right node.
            final String node = aiService.getBestNode(nodeName, request);

            // If the client doesn't specify a node then execute locally.
            if (node == null) {
                return askStroomAIServiceProvider.get().askStroomAi(request);
            }

            return nodeServiceProvider.get()
                    .remoteRestResult(
                            node,
                            AskStroomAiResponse.class,
                            () -> ResourcePaths.buildAuthenticatedApiPath(
                                    AskStroomAiResource.BASE_PATH,
                                    AskStroomAiResource.ASK_STROOM_AI_PATH_PART,
                                    node),
                            () -> askStroomAIServiceProvider.get().askStroomAi(request),
                            builder -> builder.post(Entity.json(request)));
        } catch (final RuntimeException e) {
            LOGGER.debug(e.getMessage(), e);
            throw e;
        }
    }

    @AutoLogged(OperationType.UNLOGGED)
    @Override
    public AskStroomAIConfig getDefaultConfig() {
        return askStroomAIServiceProvider.get().getDefaultConfig();
    }

    @AutoLogged(OperationType.UNLOGGED)
    @Override
    public Boolean setDefaultModel(final DocRef modelRef) {
        return askStroomAIServiceProvider.get().setDefaultModel(modelRef);
    }

    @AutoLogged(OperationType.UNLOGGED)
    @Override
    public Boolean setDefaultTableSummaryConfig(final TableSummaryConfig config) {
        return askStroomAIServiceProvider.get().setDefaultTableSummaryConfig(config);
    }

    @AutoLogged(OperationType.UNLOGGED)
    @Override
    public Boolean setDefaultChatMemoryConfigConfig(final ChatMemoryConfig config) {
        return askStroomAIServiceProvider.get().setDefaultChatMemoryConfigConfig(config);
    }
}
