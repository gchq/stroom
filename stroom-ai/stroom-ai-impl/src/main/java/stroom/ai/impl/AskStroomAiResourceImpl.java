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

package stroom.ai.impl;

import stroom.ai.shared.AiChat;
import stroom.ai.shared.AiChatMessage;
import stroom.ai.shared.AiChatPollRequest;
import stroom.ai.shared.AiChatPollResponse;
import stroom.ai.shared.AskStroomAIConfig;
import stroom.ai.shared.AskStroomAiContext;
import stroom.ai.shared.AskStroomAiRequest;
import stroom.ai.shared.AskStroomAiResource;
import stroom.ai.shared.AskStroomAiResponse;
import stroom.ai.shared.DashboardTableContext;
import stroom.ai.shared.GeneralTableContext;
import stroom.ai.shared.QueryTableContext;
import stroom.docref.DocRef;
import stroom.event.logging.api.StroomEventLoggingService;
import stroom.event.logging.api.StroomEventLoggingUtil;
import stroom.event.logging.rs.api.AutoLogged;
import stroom.event.logging.rs.api.AutoLogged.OperationType;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.FindNamedEntityCriteria;
import stroom.util.shared.NullSafe;
import stroom.util.shared.ResultPage;

import event.logging.Chat;
import event.logging.ComplexLoggedOutcome;
import event.logging.Data;
import event.logging.DataSources;
import event.logging.MultiObject;
import event.logging.Query;
import event.logging.SearchEventAction;
import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.util.List;

@AutoLogged
class AskStroomAiResourceImpl implements AskStroomAiResource {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(AskStroomAiResourceImpl.class);

    private final Provider<AskStroomAIService> askStroomAIServiceProvider;
    private final Provider<StroomEventLoggingService> stroomEventLoggingServiceProvider;

    @Inject
    AskStroomAiResourceImpl(final Provider<AskStroomAIService> askStroomAIServiceProvider,
                            final Provider<StroomEventLoggingService> stroomEventLoggingServiceProvider) {
        this.askStroomAIServiceProvider = askStroomAIServiceProvider;
        this.stroomEventLoggingServiceProvider = stroomEventLoggingServiceProvider;
    }

    @AutoLogged(OperationType.MANUALLY_LOGGED)
    @Override
    public AskStroomAiResponse askStroomAi(final AskStroomAiRequest request) {
        final StroomEventLoggingService eventLoggingService = stroomEventLoggingServiceProvider.get();
        return eventLoggingService.loggedWorkBuilder()
                .withTypeId(StroomEventLoggingUtil.buildTypeId(this, "askStroomAi"))
                .withDescription(getDescription(request))
                .withDefaultEventAction(getSearchEventAction(request))
                .withComplexLoggedResult(searchEventAction -> {
                    final AskStroomAiResponse response;
                    try {
                        response = askStroomAIServiceProvider.get().askStroomAi(request);
                    } catch (final RuntimeException e) {
                        LOGGER.debug(e.getMessage(), e);
                        throw e;
                    }

                    final SearchEventAction newSearchEventAction = searchEventAction.newCopyBuilder()
                            .withResults(MultiObject
                                    .builder()
                                    .addChat(Chat
                                            .builder()
                                            .withContent(response.getMessage())
                                            .build())
                                    .build())
                            .build();

                    return ComplexLoggedOutcome.success(response, newSearchEventAction);
                })
                .getResultAndLog();
    }

    private String getDescription(final AskStroomAiRequest request) {
        final AskStroomAiContext context = request.getContext();
        if (context == null) {
            return "Asking AI a question";
        }
        return switch (context) {
            case final DashboardTableContext dashboardTableContext -> "Asking AI about dashboard search results";
            case final QueryTableContext queryTableContext -> "Asking AI about Stroom QL search results";
            case final GeneralTableContext generalTableContext -> "Asking AI about general table data";
        };
    }

    private SearchEventAction getSearchEventAction(final AskStroomAiRequest request) {
        final AskStroomAiContext context = request.getContext();
        final String dataSourceInfo;
        if (context == null) {
            dataSourceInfo = "Conversational";
        } else {
            dataSourceInfo = switch (context) {
                case final DashboardTableContext dashboardTableContext -> "Dashboard Table";
                case final QueryTableContext queryTableContext -> "Stroom QL Query";
                case final GeneralTableContext generalTableContext -> "General Table";
            };
        }

        final DataSources dataSources = DataSources.builder().addDataSource(dataSourceInfo).build();
        return SearchEventAction
                .builder()
                .withDataSources(dataSources)
                .withQuery(Query.builder()
                        .withRaw(request.getMessage())
                        .build())
                .withData(Data
                        .builder()
                        .withName("Model")
                        .withValue(NullSafe.get(
                                request,
                                AskStroomAiRequest::getConfig,
                                AskStroomAIConfig::getModelRef,
                                DocRef::getName))
                        .build())
                .build();
    }

    @AutoLogged(OperationType.UNLOGGED)
    @Override
    public AskStroomAIConfig getDefaultConfig() {
        return askStroomAIServiceProvider.get().getDefaultConfig();
    }

    @AutoLogged(OperationType.UNLOGGED)
    @Override
    public Boolean setDefaultAskStroomAIConfig(final AskStroomAIConfig config) {
        return askStroomAIServiceProvider.get().setDefaultAskStroomAIConfig(config);
    }

    @Override
    public AiChat createChat() {
        return askStroomAIServiceProvider.get().createChat();
    }

    @Override
    public ResultPage<AiChat> listChats(final FindNamedEntityCriteria criteria) {
        return askStroomAIServiceProvider.get().listChats(criteria);
    }

    @Override
    public AiChat getChat(final int chatId) {
        return askStroomAIServiceProvider.get().getChat(chatId);
    }

    @AutoLogged(OperationType.DELETE)
    @Override
    public Boolean deleteChat(final int chatId) {
        askStroomAIServiceProvider.get().deleteChat(chatId);
        return true;
    }

    @Override
    public List<AiChatMessage> getMessages(final int chatId) {
        return askStroomAIServiceProvider.get().getMessages(chatId);
    }

    @AutoLogged(OperationType.UPDATE)
    @Override
    public Boolean updateChatTitle(final int chatId, final String title) {
        askStroomAIServiceProvider.get().updateChatTitle(chatId, title);
        return true;
    }

    @AutoLogged(OperationType.UNLOGGED)
    @Override
    public AiChatPollResponse pollMessages(final int chatId, final AiChatPollRequest request) {
        return askStroomAIServiceProvider.get().pollMessages(chatId, request);
    }

    @AutoLogged(OperationType.UPDATE)
    @Override
    public Boolean cancelProcessing(final int chatId) {
        return askStroomAIServiceProvider.get().cancelProcessing(chatId);
    }
}
