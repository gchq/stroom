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

package stroom.ai.impl;

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
import stroom.ai.shared.AskStroomAiContext;
import stroom.ai.shared.AskStroomAiRequest;
import stroom.ai.shared.AskStroomAiResource;
import stroom.ai.shared.AskStroomAiResponse;
import stroom.ai.shared.CompactAiChatRequest;
import stroom.ai.shared.DashboardTableContext;
import stroom.ai.shared.DeleteAiMemoryRequest;
import stroom.ai.shared.DocumentReference;
import stroom.ai.shared.DocumentReferenceContext;
import stroom.ai.shared.DownloadChatHistoryRequest;
import stroom.ai.shared.EditAiMessageRequest;
import stroom.ai.shared.FindAiChatHistoryCriteria;
import stroom.ai.shared.GeneralTableContext;
import stroom.ai.shared.GetAiMcpPromptRequest;
import stroom.ai.shared.GetAttachmentDataRequest;
import stroom.ai.shared.QueryTableContext;
import stroom.ai.shared.SaveAiMemoryRequest;
import stroom.ai.shared.SearchAiChatsRequest;
import stroom.ai.shared.SetAiApproveEverythingRequest;
import stroom.ai.shared.TableAnalysisConfig;
import stroom.ai.shared.UndoAiCompactionRequest;
import stroom.ai.shared.UndoAiMemoryChangeRequest;
import stroom.dashboard.shared.DashboardSearchRequest;
import stroom.dashboard.shared.Search;
import stroom.docref.DocRef;
import stroom.event.logging.api.StroomEventLoggingService;
import stroom.event.logging.api.StroomEventLoggingUtil;
import stroom.event.logging.rs.api.AutoLogged;
import stroom.event.logging.rs.api.AutoLogged.OperationType;
import stroom.query.api.SearchRequestSource;
import stroom.query.shared.QuerySearchRequest;
import stroom.ui.config.shared.UserPreferencesService;
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
    private final Provider<UserPreferencesService> userPreferencesServiceProvider;
    private final Provider<UserActionService> userActionServiceProvider;

    @Inject
    AskStroomAiResourceImpl(final Provider<AskStroomAIService> askStroomAIServiceProvider,
                            final Provider<StroomEventLoggingService> stroomEventLoggingServiceProvider,
                            final Provider<UserPreferencesService> userPreferencesServiceProvider,
                            final Provider<UserActionService> userActionServiceProvider) {
        this.userActionServiceProvider = userActionServiceProvider;
        this.askStroomAIServiceProvider = askStroomAIServiceProvider;
        this.stroomEventLoggingServiceProvider = stroomEventLoggingServiceProvider;
        this.userPreferencesServiceProvider = userPreferencesServiceProvider;
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
                case final DocumentReferenceContext r -> "Document Reference";
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
                        AskStroomAiConfig::getModelRef,
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
                case final DocumentReferenceContext r -> {
                    // The documents attached, as the user gave them.
                    NullSafe.list(r.getDocuments()).forEach(reference -> NullSafe.consume(reference,
                            DocumentReference::getDocRef, docRef -> builder.addData(Data.builder()
                                    .withName("Document")
                                    .withValue(docRef.getType() + " '" + docRef.getName() + "' {"
                                               + docRef.getUuid() + "}")
                                    .build())));
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
    public AskStroomAiConfig getDefaultConfig() {
        return askStroomAIServiceProvider.get().getDefaultConfig();
    }

    @AutoLogged(OperationType.UNLOGGED)
    @Override
    public Integer markAllChatsRead() {
        return askStroomAIServiceProvider.get().markAllChatsRead();
    }

    @AutoLogged(OperationType.UNLOGGED)
    @Override
    public Boolean setAgentMode(final int chatId, final AiAgentMode mode) {
        askStroomAIServiceProvider.get().setAgentMode(chatId, mode);
        return true;
    }

    // Logged by UserActionService, as coming from a suggestion in the chat.
    @AutoLogged(OperationType.MANUALLY_LOGGED)
    @Override
    public AiUserActionResult doUserAction(final int chatId,
                                           final int messageId,
                                           final String actionId,
                                           final String rejectedChanges) {
        return userActionServiceProvider.get().doUserAction(chatId, messageId, actionId, rejectedChanges);
    }

    // Logged by UserActionService, as undoing a change applied from a suggestion in the chat.
    @AutoLogged(OperationType.MANUALLY_LOGGED)
    @Override
    public AiUserActionResult undoUserAction(final int chatId, final int messageId, final String actionId) {
        return userActionServiceProvider.get().undoUserAction(chatId, messageId, actionId);
    }

    @AutoLogged(OperationType.MANUALLY_LOGGED)
    @Override
    public Integer resetAllUsersConfig() {
        return stroomEventLoggingServiceProvider.get().loggedWorkBuilder()
                .withTypeId(StroomEventLoggingUtil.buildTypeId(this, "resetAllUsersConfig"))
                .withDescription("Reset every user's Ask Stroom AI settings to the defaults")
                .withDefaultEventAction(UpdateEventAction.builder()
                        .withAfter(MultiObject.builder()
                                .addObject(OtherObject.builder()
                                        .withType("AskStroomAIConfig")
                                        .withName("All users")
                                        .build())
                                .build())
                        .build())
                .withSimpleLoggedResult(() -> userPreferencesServiceProvider.get().clearAskStroomAiConfigs())
                .getResultAndLog();
    }

    @AutoLogged(OperationType.MANUALLY_LOGGED)
    @Override
    public Boolean setDefaultAskStroomAIConfig(final AskStroomAiConfig config) {
        return stroomEventLoggingServiceProvider.get().loggedWorkBuilder()
                .withTypeId(StroomEventLoggingUtil.buildTypeId(this, "setDefaultAskStroomAIConfig"))
                .withDescription("Update default AI configuration")
                .withDefaultEventAction(UpdateEventAction.builder()
                        .withAfter(MultiObject.builder()
                                .addObject(OtherObject.builder()
                                        .withType("AskStroomAIConfig")
                                        .withName(NullSafe.get(config,
                                                AskStroomAiConfig::getModelRef,
                                                DocRef::getName))
                                        .addData(Data.builder()
                                                .withName("Model")
                                                .withValue(NullSafe.get(config,
                                                        AskStroomAiConfig::getModelRef,
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
                .withDescription("Create a new AI chat")
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
                .withDescription("List AI chats")
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
                .withDescription("View AI chat " + chatId)
                .withDefaultEventAction(ViewEventAction.builder()
                        .addObject(chatObject(chatId))
                        .build())
                .withSimpleLoggedResult(() ->
                        askStroomAIServiceProvider.get().getChat(chatId))
                .getResultAndLog();
    }

    @AutoLogged(OperationType.MANUALLY_LOGGED)
    @Override
    public Boolean setTableAnalysisConfig(final TableAnalysisConfig config) {
        return stroomEventLoggingServiceProvider.get().loggedWorkBuilder()
                .withTypeId(StroomEventLoggingUtil.buildTypeId(this, "setTableAnalysisConfig"))
                .withDescription("Update the AI table analysis settings")
                .withDefaultEventAction(UpdateEventAction.builder()
                        .withAfter(MultiObject.builder()
                                .addObject(OtherObject.builder()
                                        .withType("TableAnalysisConfig")
                                        .withName("AI table analysis settings")
                                        .build())
                                .build())
                        .build())
                .withSimpleLoggedResult(() -> askStroomAIServiceProvider.get().setTableAnalysisConfig(config))
                .getResultAndLog();
    }

    @AutoLogged(OperationType.UNLOGGED)
    @Override
    public List<AiChatSearchHit> searchChats(final SearchAiChatsRequest request) {
        return askStroomAIServiceProvider.get().searchChats(NullSafe.get(request, SearchAiChatsRequest::getText));
    }

    @AutoLogged(OperationType.MANUALLY_LOGGED)
    @Override
    public Boolean clearChat(final int chatId) {
        return stroomEventLoggingServiceProvider.get().loggedWorkBuilder()
                .withTypeId(StroomEventLoggingUtil.buildTypeId(this, "clearChat"))
                .withDescription("Clear AI chat " + chatId)
                .withDefaultEventAction(DeleteEventAction.builder()
                        .addObject(chatObject(chatId))
                        .build())
                .withSimpleLoggedResult(() -> {
                    askStroomAIServiceProvider.get().clearChat(chatId);
                    return true;
                })
                .getResultAndLog();
    }

    @AutoLogged(OperationType.MANUALLY_LOGGED)
    @Override
    public Integer deleteAllChats() {
        return stroomEventLoggingServiceProvider.get().loggedWorkBuilder()
                .withTypeId(StroomEventLoggingUtil.buildTypeId(this, "deleteAllChats"))
                .withDescription("Delete all the user's AI chats")
                .withDefaultEventAction(DeleteEventAction.builder()
                        .addObject(OtherObject.builder()
                                .withType("AiChat")
                                .withName("All the user's AI chats")
                                .build())
                        .build())
                .withSimpleLoggedResult(() -> askStroomAIServiceProvider.get().deleteAllChats())
                .getResultAndLog();
    }

    @AutoLogged(OperationType.MANUALLY_LOGGED)
    @Override
    public Boolean deleteChat(final int chatId) {
        return stroomEventLoggingServiceProvider.get().loggedWorkBuilder()
                .withTypeId(StroomEventLoggingUtil.buildTypeId(this, "deleteChat"))
                .withDescription("Delete AI chat " + chatId)
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
    public Boolean sendSteerNow(final int chatId, final int messageId) {
        return stroomEventLoggingServiceProvider.get().loggedWorkBuilder()
                .withTypeId(StroomEventLoggingUtil.buildTypeId(this, "sendSteerNow"))
                .withDescription("Send message " + messageId + " to the run of AI chat " + chatId + " now")
                .withDefaultEventAction(UpdateEventAction.builder()
                        .withAfter(MultiObject.builder()
                                .addObject(OtherObject.builder()
                                        .withType("AiChatMessage")
                                        .withId(String.valueOf(messageId))
                                        .addData(Data.builder()
                                                .withName("ChatId")
                                                .withValue(String.valueOf(chatId))
                                                .build())
                                        .addData(Data.builder()
                                                .withName("Action")
                                                .withValue("SendNow")
                                                .build())
                                        .build())
                                .build())
                        .build())
                .withSimpleLoggedResult(() -> askStroomAIServiceProvider.get().sendSteerNow(chatId, messageId))
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

    @AutoLogged(OperationType.MANUALLY_LOGGED)
    @Override
    public AskStroomAiResponse editMessage(final EditAiMessageRequest request) {
        final int chatId = NullSafe.getOrElse(request, EditAiMessageRequest::getAiChat, AiChat::getId, 0);
        return stroomEventLoggingServiceProvider.get().loggedWorkBuilder()
                .withTypeId(StroomEventLoggingUtil.buildTypeId(this, "editMessage"))
                .withDescription("Edit message " + request.getMessageId() + " in AI chat " + chatId)
                .withDefaultEventAction(UpdateEventAction.builder()
                        .withAfter(MultiObject.builder()
                                .addObject(OtherObject.builder()
                                        .withType("AiChat")
                                        .withId(String.valueOf(chatId))
                                        .addData(Data.builder()
                                                .withName("Action")
                                                .withValue(request.getMessage() == null
                                                        ? "RetryMessage"
                                                        : "EditMessage")
                                                .build())
                                        .addData(Data.builder()
                                                .withName("MessageId")
                                                .withValue(String.valueOf(request.getMessageId()))
                                                .build())
                                        .addData(Data.builder()
                                                .withName("NewMessage")
                                                .withValue(NullSafe.string(request.getMessage()))
                                                .build())
                                        .build())
                                .build())
                        .build())
                .withSimpleLoggedResult(() -> askStroomAIServiceProvider.get().editMessage(request))
                .getResultAndLog();
    }

    @AutoLogged(OperationType.MANUALLY_LOGGED)
    @Override
    public AskStroomAiResponse resumeRun(final AskStroomAiRequest request) {
        final int chatId = NullSafe.getOrElse(request, AskStroomAiRequest::getAiChat, AiChat::getId, 0);
        return stroomEventLoggingServiceProvider.get().loggedWorkBuilder()
                .withTypeId(StroomEventLoggingUtil.buildTypeId(this, "resumeRun"))
                .withDescription("Resume AI processing for chat " + chatId)
                .withDefaultEventAction(UpdateEventAction.builder()
                        .withAfter(MultiObject.builder()
                                .addObject(OtherObject.builder()
                                        .withType("AiChat")
                                        .withId(String.valueOf(chatId))
                                        .addData(Data.builder()
                                                .withName("Action")
                                                .withValue("ResumeRun")
                                                .build())
                                        .build())
                                .build())
                        .build())
                .withSimpleLoggedResult(() -> askStroomAIServiceProvider.get().resumeRun(request))
                .getResultAndLog();
    }

    @AutoLogged(OperationType.MANUALLY_LOGGED)
    @Override
    public AskStroomAiResponse compactChat(final CompactAiChatRequest request) {
        final int chatId = NullSafe.getOrElse(request, CompactAiChatRequest::getAiChat, AiChat::getId, 0);
        return stroomEventLoggingServiceProvider.get().loggedWorkBuilder()
                .withTypeId(StroomEventLoggingUtil.buildTypeId(this, "compactChat"))
                .withDescription("Compact AI chat " + chatId)
                .withDefaultEventAction(UpdateEventAction.builder()
                        .withAfter(MultiObject.builder()
                                .addObject(OtherObject.builder()
                                        .withType("AiChat")
                                        .withId(String.valueOf(chatId))
                                        .addData(Data.builder()
                                                .withName("Action")
                                                .withValue("CompactChat")
                                                .build())
                                        .addData(Data.builder()
                                                .withName("Instructions")
                                                .withValue(NullSafe.string(request.getInstructions()))
                                                .build())
                                        .build())
                                .build())
                        .build())
                .withSimpleLoggedResult(() -> askStroomAIServiceProvider.get().compactChat(request))
                .getResultAndLog();
    }

    @AutoLogged(OperationType.MANUALLY_LOGGED)
    @Override
    public AiContextUsage undoCompaction(final UndoAiCompactionRequest request) {
        final int chatId = NullSafe.getOrElse(request, UndoAiCompactionRequest::getAiChat, AiChat::getId, 0);
        return stroomEventLoggingServiceProvider.get().loggedWorkBuilder()
                .withTypeId(StroomEventLoggingUtil.buildTypeId(this, "undoCompaction"))
                .withDescription("Undo compaction " + request.getMessageId() + " of AI chat " + chatId)
                .withDefaultEventAction(UpdateEventAction.builder()
                        .withAfter(MultiObject.builder()
                                .addObject(OtherObject.builder()
                                        .withType("AiChat")
                                        .withId(String.valueOf(chatId))
                                        .addData(Data.builder()
                                                .withName("Action")
                                                .withValue("UndoCompaction")
                                                .build())
                                        .addData(Data.builder()
                                                .withName("MessageId")
                                                .withValue(String.valueOf(request.getMessageId()))
                                                .build())
                                        .build())
                                .build())
                        .build())
                .withSimpleLoggedResult(() -> askStroomAIServiceProvider.get().undoCompaction(request))
                .getResultAndLog();
    }

    @AutoLogged(OperationType.MANUALLY_LOGGED)
    @Override
    public Boolean answerApproval(final AnswerAiApprovalRequest request) {
        final int chatId = NullSafe.getOrElse(request, AnswerAiApprovalRequest::getAiChat, AiChat::getId, 0);
        return stroomEventLoggingServiceProvider.get().loggedWorkBuilder()
                .withTypeId(StroomEventLoggingUtil.buildTypeId(this, "answerApproval"))
                .withDescription((request.isApproved()
                        ? "Approve"
                        : "Reject") + " request " + request.getMessageId() + " in AI chat " + chatId)
                .withDefaultEventAction(UpdateEventAction.builder()
                        .withAfter(MultiObject.builder()
                                .addObject(OtherObject.builder()
                                        .withType("AiChat")
                                        .withId(String.valueOf(chatId))
                                        .addData(Data.builder()
                                                .withName("Action")
                                                .withValue(request.isApproved()
                                                        ? "Approve"
                                                        : "Reject")
                                                .build())
                                        .addData(Data.builder()
                                                .withName("MessageId")
                                                .withValue(String.valueOf(request.getMessageId()))
                                                .build())
                                        .addData(Data.builder()
                                                .withName("Scope")
                                                .withValue(String.valueOf(request.getScope()))
                                                .build())
                                        .addData(Data.builder()
                                                .withName("Calls")
                                                .withValue(request.getCallIds() == null
                                                        ? "all"
                                                        : String.join(",", request.getCallIds()))
                                                .build())
                                        .addData(Data.builder()
                                                .withName("Reason")
                                                .withValue(NullSafe.string(request.getReason()))
                                                .build())
                                        .build())
                                .build())
                        .build())
                .withSimpleLoggedResult(() -> askStroomAIServiceProvider.get().answerApproval(request))
                .getResultAndLog();
    }

    @AutoLogged(OperationType.MANUALLY_LOGGED)
    @Override
    public Boolean setApproveEverything(final SetAiApproveEverythingRequest request) {
        final int chatId = NullSafe.getOrElse(request, SetAiApproveEverythingRequest::getAiChat, AiChat::getId, 0);
        return stroomEventLoggingServiceProvider.get().loggedWorkBuilder()
                .withTypeId(StroomEventLoggingUtil.buildTypeId(this, "setApproveEverything"))
                .withDescription((request.isEnabled()
                        ? "Turn on"
                        : "Turn off") + " 'approve everything' for AI chat " + chatId)
                .withDefaultEventAction(UpdateEventAction.builder()
                        .withAfter(MultiObject.builder()
                                .addObject(OtherObject.builder()
                                        .withType("AiChat")
                                        .withId(String.valueOf(chatId))
                                        .addData(Data.builder()
                                                .withName("ApproveEverything")
                                                .withValue(String.valueOf(request.isEnabled()))
                                                .build())
                                        .build())
                                .build())
                        .build())
                .withSimpleLoggedResult(() -> askStroomAIServiceProvider.get().setApproveEverything(request))
                .getResultAndLog();
    }

    @AutoLogged(OperationType.UNLOGGED) // Polled for badges, and only reads the user's own chats.
    @Override
    public AiChatStatusResponse getChatStatuses(final long sinceVersion) {
        return askStroomAIServiceProvider.get().getChatStatuses(sinceVersion);
    }

    @AutoLogged(OperationType.MANUALLY_LOGGED)
    @Override
    public List<AiMcpPrompt> listMcpPrompts(final AskStroomAiConfig config) {
        return stroomEventLoggingServiceProvider.get().loggedWorkBuilder()
                .withTypeId(StroomEventLoggingUtil.buildTypeId(this, "listMcpPrompts"))
                .withDescription("List the prompts of the MCP servers of model "
                                 + NullSafe.get(config, AskStroomAiConfig::getModelRef))
                .withDefaultEventAction(ViewEventAction.builder()
                        .addObject(OtherObject.builder()
                                .withType("AiMcpPrompts")
                                .build())
                        .build())
                .withSimpleLoggedResult(() -> askStroomAIServiceProvider.get().listMcpPrompts(config))
                .getResultAndLog();
    }

    @AutoLogged(OperationType.MANUALLY_LOGGED)
    @Override
    public AiMcpPromptText getMcpPrompt(final GetAiMcpPromptRequest request) {
        return stroomEventLoggingServiceProvider.get().loggedWorkBuilder()
                .withTypeId(StroomEventLoggingUtil.buildTypeId(this, "getMcpPrompt"))
                .withDescription("Get prompt '" + request.getName() + "' from MCP server '" + request.getServer()
                                 + "'")
                .withDefaultEventAction(ViewEventAction.builder()
                        .addObject(OtherObject.builder()
                                .withType("AiMcpPrompt")
                                .withName(request.getServer() + "." + request.getName())
                                .build())
                        .build())
                .withSimpleLoggedResult(() -> askStroomAIServiceProvider.get().getMcpPrompt(request))
                .getResultAndLog();
    }

    @AutoLogged(OperationType.MANUALLY_LOGGED)
    @Override
    public Boolean answerElicitation(final AnswerAiElicitationRequest request) {
        final int chatId = NullSafe.getOrElse(request, AnswerAiElicitationRequest::getAiChat, AiChat::getId, 0);
        // The answer itself is not logged, as it may hold anything the server asked for.
        return stroomEventLoggingServiceProvider.get().loggedWorkBuilder()
                .withTypeId(StroomEventLoggingUtil.buildTypeId(this, "answerElicitation"))
                .withDescription("Answer (" + request.getAction() + ") form " + request.getMessageId()
                                 + " from an MCP tool in AI chat " + chatId)
                .withDefaultEventAction(UpdateEventAction.builder()
                        .withAfter(MultiObject.builder()
                                .addObject(OtherObject.builder()
                                        .withType("AiElicitation")
                                        .withId(String.valueOf(request.getMessageId()))
                                        .addData(Data.builder()
                                                .withName("Action")
                                                .withValue(request.getAction())
                                                .build())
                                        .build())
                                .build())
                        .build())
                .withSimpleLoggedResult(() -> askStroomAIServiceProvider.get().answerElicitation(request))
                .getResultAndLog();
    }

    @AutoLogged(OperationType.MANUALLY_LOGGED)
    @Override
    public AiMemoryListResponse listMemory(final int chatId) {
        return stroomEventLoggingServiceProvider.get().loggedWorkBuilder()
                .withTypeId(StroomEventLoggingUtil.buildTypeId(this, "listMemory"))
                .withDescription("View the AI memory of chat " + chatId)
                .withDefaultEventAction(ViewEventAction.builder()
                        .addObject(chatObject(chatId))
                        .build())
                .withSimpleLoggedResult(() -> askStroomAIServiceProvider.get().listMemory(chatId))
                .getResultAndLog();
    }

    @AutoLogged(OperationType.MANUALLY_LOGGED)
    @Override
    public AiMemory saveMemory(final SaveAiMemoryRequest request) {
        final int chatId = NullSafe.getOrElse(request, SaveAiMemoryRequest::getAiChat, AiChat::getId, 0);
        return stroomEventLoggingServiceProvider.get().loggedWorkBuilder()
                .withTypeId(StroomEventLoggingUtil.buildTypeId(this, "saveMemory"))
                .withDescription("Save AI memory " + request.getPath() + " from chat " + chatId)
                .withDefaultEventAction(UpdateEventAction.builder()
                        .withAfter(MultiObject.builder()
                                .addObject(memoryObject(request.getPath()))
                                .build())
                        .build())
                .withSimpleLoggedResult(() -> askStroomAIServiceProvider.get().saveMemory(request))
                .getResultAndLog();
    }

    @AutoLogged(OperationType.MANUALLY_LOGGED)
    @Override
    public Boolean deleteMemory(final DeleteAiMemoryRequest request) {
        final int chatId = NullSafe.getOrElse(request, DeleteAiMemoryRequest::getAiChat, AiChat::getId, 0);
        return stroomEventLoggingServiceProvider.get().loggedWorkBuilder()
                .withTypeId(StroomEventLoggingUtil.buildTypeId(this, "deleteMemory"))
                .withDescription("Delete AI memory " + request.getPath() + " from chat " + chatId)
                .withDefaultEventAction(DeleteEventAction.builder()
                        .addObject(memoryObject(request.getPath()))
                        .build())
                .withSimpleLoggedResult(() -> askStroomAIServiceProvider.get().deleteMemory(request))
                .getResultAndLog();
    }

    @AutoLogged(OperationType.MANUALLY_LOGGED)
    @Override
    public Boolean undoMemoryChange(final UndoAiMemoryChangeRequest request) {
        final int chatId = NullSafe.getOrElse(request, UndoAiMemoryChangeRequest::getAiChat, AiChat::getId, 0);
        return stroomEventLoggingServiceProvider.get().loggedWorkBuilder()
                .withTypeId(StroomEventLoggingUtil.buildTypeId(this, "undoMemoryChange"))
                .withDescription("Undo AI memory change " + request.getMessageId() + " in chat " + chatId)
                .withDefaultEventAction(UpdateEventAction.builder()
                        .withAfter(MultiObject.builder()
                                .addObject(OtherObject.builder()
                                        .withType("AiMemoryChange")
                                        .withId(String.valueOf(request.getMessageId()))
                                        .build())
                                .build())
                        .build())
                .withSimpleLoggedResult(() -> askStroomAIServiceProvider.get().undoMemoryChange(request))
                .getResultAndLog();
    }

    @AutoLogged(OperationType.MANUALLY_LOGGED)
    @Override
    public AiToolApprovalsResponse listToolApprovals(final int chatId) {
        return stroomEventLoggingServiceProvider.get().loggedWorkBuilder()
                .withTypeId(StroomEventLoggingUtil.buildTypeId(this, "listToolApprovals"))
                .withDescription("View the tool approvals of AI chat " + chatId)
                .withDefaultEventAction(ViewEventAction.builder()
                        .addObject(chatObject(chatId))
                        .build())
                .withSimpleLoggedResult(() -> askStroomAIServiceProvider.get().listToolApprovals(chatId))
                .getResultAndLog();
    }

    @AutoLogged(OperationType.MANUALLY_LOGGED)
    @Override
    public Boolean removeToolApproval(final int chatId, final int approvalId) {
        return stroomEventLoggingServiceProvider.get().loggedWorkBuilder()
                .withTypeId(StroomEventLoggingUtil.buildTypeId(this, "removeToolApproval"))
                .withDescription("Remove tool approval " + approvalId + " from AI chat " + chatId)
                .withDefaultEventAction(DeleteEventAction.builder()
                        .addObject(OtherObject.builder()
                                .withType("AiToolApproval")
                                .withId(String.valueOf(approvalId))
                                .build())
                        .build())
                .withSimpleLoggedResult(() ->
                        askStroomAIServiceProvider.get().removeToolApproval(chatId, approvalId))
                .getResultAndLog();
    }

    @AutoLogged(OperationType.MANUALLY_LOGGED)
    @Override
    public Boolean resetToolApprovals(final int chatId) {
        return stroomEventLoggingServiceProvider.get().loggedWorkBuilder()
                .withTypeId(StroomEventLoggingUtil.buildTypeId(this, "resetToolApprovals"))
                .withDescription("Remove all tool approvals from AI chat " + chatId)
                .withDefaultEventAction(DeleteEventAction.builder()
                        .addObject(chatObject(chatId))
                        .build())
                .withSimpleLoggedResult(() -> askStroomAIServiceProvider.get().resetToolApprovals(chatId))
                .getResultAndLog();
    }

    private static OtherObject memoryObject(final String path) {
        return OtherObject.builder()
                .withType("AiMemory")
                .withName(path)
                .build();
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

    @AutoLogged(OperationType.MANUALLY_LOGGED)
    @Override
    public AiAttachmentDataPage getAttachmentData(final GetAttachmentDataRequest request) {
        return stroomEventLoggingServiceProvider.get().loggedWorkBuilder()
                .withTypeId(StroomEventLoggingUtil.buildTypeId(this, "getAttachmentData"))
                .withDescription("View attachment data for attachment "
                                 + request.getAttachmentId()
                                 + " in AI chat " + request.getChatId())
                .withDefaultEventAction(ViewEventAction.builder()
                        .addObject(OtherObject.builder()
                                .withType("AiChatAttachment")
                                .withId(String.valueOf(request.getAttachmentId()))
                                .addData(Data.builder()
                                        .withName("ChatId")
                                        .withValue(String.valueOf(request.getChatId()))
                                        .build())
                                .build())
                        .build())
                .withSimpleLoggedResult(() ->
                        askStroomAIServiceProvider.get().getAttachmentData(request))
                .getResultAndLog();
    }
}
