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
import stroom.ai.shared.DownloadChatHistoryRequest;
import stroom.ai.shared.FindAiChatHistoryCriteria;
import stroom.ai.shared.GeneralTableContext;
import stroom.ai.shared.QueryTableContext;
import stroom.dashboard.shared.DashboardSearchRequest;
import stroom.dashboard.shared.Search;
import stroom.docref.DocRef;
import stroom.event.logging.api.StroomEventLoggingService;
import stroom.event.logging.api.StroomEventLoggingUtil;
import stroom.event.logging.rs.api.AutoLogged;
import stroom.event.logging.rs.api.AutoLogged.OperationType;
import stroom.query.api.SearchRequestSource;
import stroom.query.shared.QuerySearchRequest;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.NullSafe;
import stroom.util.shared.ResourceGeneration;
import stroom.util.shared.ResultPage;

import event.logging.Chat;
import event.logging.ComplexLoggedOutcome;
import event.logging.CreateEventAction;
import event.logging.Data;
import event.logging.DataSources;
import event.logging.DeleteEventAction;
import event.logging.MultiObject;
import event.logging.OtherObject;
import event.logging.Query;
import event.logging.SearchEventAction;
import event.logging.UpdateEventAction;
import event.logging.ViewEventAction;
import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.math.BigInteger;
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
        return "Asking AI about " + context.getDescription();
    }

    private SearchEventAction getSearchEventAction(final AskStroomAiRequest request) {
        final AskStroomAiContext context = request.getContext();

        final SearchEventAction.Builder<Void> builder = SearchEventAction.builder();

        // Data sources — categorise the context type.
        final String dataSourceInfo;
        if (context == null) {
            dataSourceInfo = "Conversational";
        } else {
            dataSourceInfo = switch (context) {
                case final DashboardTableContext d -> "Dashboard Table";
                case final QueryTableContext q -> "Stroom QL Query";
                case final GeneralTableContext g -> "General Table";
            };
        }
        builder.withDataSources(DataSources.builder().addDataSource(dataSourceInfo).build());

        // Query raw text.
        builder.withQuery(Query.builder().withRaw(request.getMessage()).build());

        // Model name.
        builder.addData(Data.builder()
                .withName("Model")
                .withValue(NullSafe.get(
                        request,
                        AskStroomAiRequest::getConfig,
                        AskStroomAIConfig::getModelRef,
                        DocRef::getName))
                .build());

        // Context-specific structured data.
        if (context != null) {
            builder.addData(Data.builder()
                    .withName("ContextType")
                    .withValue(dataSourceInfo)
                    .build());
            builder.addData(Data.builder()
                    .withName("ContextDescription")
                    .withValue(context.getDescription())
                    .build());

            switch (context) {
                case final DashboardTableContext d -> {
                    // Extract dashboard name and table component from SearchRequestSource.
                    final SearchRequestSource source = NullSafe.get(
                            d.getSearchRequest(),
                            DashboardSearchRequest::getSearchRequestSource);
                    NullSafe.consume(source,
                            SearchRequestSource::getOwnerDocRef, ownerRef -> {
                                builder.addData(Data.builder()
                                        .withName("DashboardName")
                                        .withValue(ownerRef.getName())
                                        .build());
                                builder.addData(Data.builder()
                                        .withName("DashboardUuid")
                                        .withValue(ownerRef.getUuid())
                                        .build());
                            });
                    NullSafe.consume(source,
                            SearchRequestSource::getComponentName, name -> {
                                builder.addData(Data.builder()
                                        .withName("TableComponent")
                                        .withValue(name)
                                        .build());
                            });
                    // Extract data source from the Search object.
                    NullSafe.consume(d.getSearchRequest(),
                            DashboardSearchRequest::getSearch,
                            Search::getDataSourceRef, dsRef -> {
                                builder.addData(Data.builder()
                                        .withName("DataSource")
                                        .withValue(dsRef.getName() + " {" + dsRef.getUuid() + "}")
                                        .build());
                            });
                }
                case final QueryTableContext q -> {
                    // Extract query name from SearchRequestSource.
                    final SearchRequestSource source = NullSafe.get(
                            q.getSearchRequest(),
                            QuerySearchRequest::getSearchRequestSource);
                    NullSafe.consume(source,
                            SearchRequestSource::getOwnerDocRef, ownerRef -> {
                                builder.addData(Data.builder()
                                        .withName("QueryName")
                                        .withValue(ownerRef.getName())
                                        .build());
                                builder.addData(Data.builder()
                                        .withName("QueryUuid")
                                        .withValue(ownerRef.getUuid())
                                        .build());
                            });
                    // Include the StroomQL query text.
                    NullSafe.consume(q.getSearchRequest(),
                            QuerySearchRequest::getQuery, ql -> {
                                builder.addData(Data.builder()
                                        .withName("StroomQL")
                                        .withValue(ql)
                                        .build());
                            });
                }
                case final GeneralTableContext g -> {
                    // Include data dimensions.
                    builder.addData(Data.builder()
                            .withName("Rows")
                            .withValue(String.valueOf(NullSafe.list(g.getRows()).size()))
                            .build());
                    builder.addData(Data.builder()
                            .withName("Columns")
                            .withValue(String.valueOf(NullSafe.list(g.getColumns()).size()))
                            .build());
                }
            }
        }

        // Chat ID for traceability.
        NullSafe.consume(request.getAiChat(), aiChat -> {
            builder.addData(Data.builder()
                    .withName("ChatId")
                    .withValue(String.valueOf(aiChat.getId()))
                    .build());
        });

        return builder.build();
    }

    @AutoLogged(OperationType.UNLOGGED)
    @Override
    public AskStroomAIConfig getDefaultConfig() {
        return askStroomAIServiceProvider.get().getDefaultConfig();
    }

    @AutoLogged(OperationType.MANUALLY_LOGGED)
    @Override
    public Boolean setDefaultAskStroomAIConfig(final AskStroomAIConfig config) {
        return stroomEventLoggingServiceProvider.get().loggedWorkBuilder()
                .withTypeId(StroomEventLoggingUtil.buildTypeId(this, "setDefaultAskStroomAIConfig"))
                .withDescription("Update default AI configuration")
                .withDefaultEventAction(UpdateEventAction.builder()
                        .withAfter(MultiObject.builder()
                                .addObject(OtherObject.builder()
                                        .withType("AskStroomAIConfig")
                                        .withName(NullSafe.get(config,
                                                AskStroomAIConfig::getModelRef,
                                                DocRef::getName))
                                        .addData(Data.builder()
                                                .withName("Model")
                                                .withValue(NullSafe.get(config,
                                                        AskStroomAIConfig::getModelRef,
                                                        DocRef::getName))
                                                .build())
                                        .build())
                                .build())
                        .build())
                .withSimpleLoggedResult(() ->
                        askStroomAIServiceProvider.get().setDefaultAskStroomAIConfig(config))
                .getResultAndLog();
    }

    @AutoLogged(OperationType.MANUALLY_LOGGED)
    @Override
    public AiChat createChat() {
        return stroomEventLoggingServiceProvider.get().loggedWorkBuilder()
                .withTypeId(StroomEventLoggingUtil.buildTypeId(this, "createChat"))
                .withDescription("Create a new AI chat conversation")
                .withDefaultEventAction(CreateEventAction.builder()
                        .addObject(OtherObject.builder()
                                .withType("AiChat")
                                .build())
                        .build())
                .withComplexLoggedResult(createEventAction -> {
                    final AiChat chat = askStroomAIServiceProvider.get().createChat();

                    final CreateEventAction newAction = createEventAction.newCopyBuilder()
                            .withObjects(OtherObject.builder()
                                    .withType("AiChat")
                                    .withId(String.valueOf(chat.getId()))
                                    .build())
                            .build();

                    return ComplexLoggedOutcome.success(chat, newAction);
                })
                .getResultAndLog();
    }

    @AutoLogged(OperationType.MANUALLY_LOGGED)
    @Override
    public ResultPage<AiChat> listChats(final FindAiChatHistoryCriteria criteria) {
        return stroomEventLoggingServiceProvider.get().loggedWorkBuilder()
                .withTypeId(StroomEventLoggingUtil.buildTypeId(this, "listChats"))
                .withDescription("List AI chat conversations")
                .withDefaultEventAction(SearchEventAction.builder()
                        .withQuery(Query.builder()
                                .withRaw(NullSafe.get(criteria,
                                        FindAiChatHistoryCriteria::getFilter))
                                .build())
                        .build())
                .withComplexLoggedResult(searchEventAction -> {
                    final ResultPage<AiChat> result =
                            askStroomAIServiceProvider.get().listChats(criteria);

                    final SearchEventAction newAction = searchEventAction.newCopyBuilder()
                            .withTotalResults(BigInteger.valueOf(result.size()))
                            .build();

                    return ComplexLoggedOutcome.success(result, newAction);
                })
                .getResultAndLog();
    }

    @AutoLogged(OperationType.MANUALLY_LOGGED)
    @Override
    public AiChat getChat(final int chatId) {
        return stroomEventLoggingServiceProvider.get().loggedWorkBuilder()
                .withTypeId(StroomEventLoggingUtil.buildTypeId(this, "getChat"))
                .withDescription("View AI chat conversation " + chatId)
                .withDefaultEventAction(ViewEventAction.builder()
                        .addObject(chatObject(chatId))
                        .build())
                .withSimpleLoggedResult(() ->
                        askStroomAIServiceProvider.get().getChat(chatId))
                .getResultAndLog();
    }

    @AutoLogged(OperationType.MANUALLY_LOGGED)
    @Override
    public Boolean deleteChat(final int chatId) {
        return stroomEventLoggingServiceProvider.get().loggedWorkBuilder()
                .withTypeId(StroomEventLoggingUtil.buildTypeId(this, "deleteChat"))
                .withDescription("Delete AI chat conversation " + chatId)
                .withDefaultEventAction(DeleteEventAction.builder()
                        .addObject(chatObject(chatId))
                        .build())
                .withSimpleLoggedResult(() -> {
                    askStroomAIServiceProvider.get().deleteChat(chatId);
                    return true;
                })
                .getResultAndLog();
    }

    @AutoLogged(OperationType.MANUALLY_LOGGED)
    @Override
    public Boolean deleteMessage(final int chatId, final int messageId) {
        return stroomEventLoggingServiceProvider.get().loggedWorkBuilder()
                .withTypeId(StroomEventLoggingUtil.buildTypeId(this, "deleteMessage"))
                .withDescription("Delete message " + messageId + " from AI chat " + chatId)
                .withDefaultEventAction(DeleteEventAction.builder()
                        .addObject(OtherObject.builder()
                                .withType("AiChatMessage")
                                .withId(String.valueOf(messageId))
                                .addData(Data.builder()
                                        .withName("ChatId")
                                        .withValue(String.valueOf(chatId))
                                        .build())
                                .build())
                        .build())
                .withSimpleLoggedResult(() -> {
                    askStroomAIServiceProvider.get().deleteMessage(chatId, messageId);
                    return true;
                })
                .getResultAndLog();
    }

    @AutoLogged(OperationType.MANUALLY_LOGGED)
    @Override
    public Boolean deleteAllMessages(final int chatId) {
        return stroomEventLoggingServiceProvider.get().loggedWorkBuilder()
                .withTypeId(StroomEventLoggingUtil.buildTypeId(this, "deleteAllMessages"))
                .withDescription("Delete all messages from AI chat " + chatId)
                .withDefaultEventAction(DeleteEventAction.builder()
                        .addObject(OtherObject.builder()
                                .withType("AiChat")
                                .withId(String.valueOf(chatId))
                                .addData(Data.builder()
                                        .withName("Action")
                                        .withValue("DeleteAllMessages")
                                        .build())
                                .build())
                        .build())
                .withSimpleLoggedResult(() -> {
                    askStroomAIServiceProvider.get().deleteAllMessages(chatId);
                    return true;
                })
                .getResultAndLog();
    }

    @AutoLogged(OperationType.MANUALLY_LOGGED)
    @Override
    public List<AiChatMessage> getMessages(final int chatId) {
        return stroomEventLoggingServiceProvider.get().loggedWorkBuilder()
                .withTypeId(StroomEventLoggingUtil.buildTypeId(this, "getMessages"))
                .withDescription("View messages for AI chat " + chatId)
                .withDefaultEventAction(ViewEventAction.builder()
                        .addObject(chatObject(chatId))
                        .build())
                .withSimpleLoggedResult(() ->
                        askStroomAIServiceProvider.get().getMessages(chatId))
                .getResultAndLog();
    }

    @AutoLogged(OperationType.MANUALLY_LOGGED)
    @Override
    public Boolean updateChatTitle(final int chatId, final String title) {
        return stroomEventLoggingServiceProvider.get().loggedWorkBuilder()
                .withTypeId(StroomEventLoggingUtil.buildTypeId(this, "updateChatTitle"))
                .withDescription("Update title of AI chat " + chatId)
                .withDefaultEventAction(UpdateEventAction.builder()
                        .withAfter(MultiObject.builder()
                                .addObject(OtherObject.builder()
                                        .withType("AiChat")
                                        .withId(String.valueOf(chatId))
                                        .withName(title)
                                        .build())
                                .build())
                        .build())
                .withSimpleLoggedResult(() -> {
                    askStroomAIServiceProvider.get().updateChatTitle(chatId, title);
                    return true;
                })
                .getResultAndLog();
    }

    @AutoLogged(OperationType.UNLOGGED)
    @Override
    public AiChatPollResponse pollMessages(final int chatId, final AiChatPollRequest request) {
        return askStroomAIServiceProvider.get().pollMessages(chatId, request);
    }

    @AutoLogged(OperationType.MANUALLY_LOGGED)
    @Override
    public Boolean cancelProcessing(final int chatId) {
        return stroomEventLoggingServiceProvider.get().loggedWorkBuilder()
                .withTypeId(StroomEventLoggingUtil.buildTypeId(this, "cancelProcessing"))
                .withDescription("Cancel AI processing for chat " + chatId)
                .withDefaultEventAction(UpdateEventAction.builder()
                        .withAfter(MultiObject.builder()
                                .addObject(OtherObject.builder()
                                        .withType("AiChat")
                                        .withId(String.valueOf(chatId))
                                        .addData(Data.builder()
                                                .withName("Action")
                                                .withValue("CancelProcessing")
                                                .build())
                                        .build())
                                .build())
                        .build())
                .withSimpleLoggedResult(() ->
                        askStroomAIServiceProvider.get().cancelProcessing(chatId))
                .getResultAndLog();
    }

    private OtherObject chatObject(final int chatId) {
        return OtherObject.builder()
                .withType("AiChat")
                .withId(String.valueOf(chatId))
                .build();
    }

    @AutoLogged(OperationType.MANUALLY_LOGGED)
    @Override
    public ResourceGeneration downloadChatHistory(final DownloadChatHistoryRequest request) {
        return stroomEventLoggingServiceProvider.get().loggedWorkBuilder()
                .withTypeId(StroomEventLoggingUtil.buildTypeId(this, "downloadChatHistory"))
                .withDescription("Download chat history for AI chat " + request.getChatId())
                .withDefaultEventAction(ViewEventAction.builder()
                        .addObject(chatObject(request.getChatId()))
                        .build())
                .withSimpleLoggedResult(() ->
                        askStroomAIServiceProvider.get().downloadChatHistory(request))
                .getResultAndLog();
    }
}
