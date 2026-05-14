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
import stroom.ai.shared.AskStroomAiContext;
import stroom.ai.shared.AskStroomAiRequest;
import stroom.ai.shared.AskStroomAiResource;
import stroom.ai.shared.AskStroomAiResponse;
import stroom.ai.shared.ChatMemoryConfig;
import stroom.ai.shared.DashboardTableContext;
import stroom.ai.shared.GeneralTableContext;
import stroom.ai.shared.QueryTableContext;
import stroom.ai.shared.TableSummaryConfig;
import stroom.dashboard.shared.DashboardSearchRequest;
import stroom.dashboard.shared.Search;
import stroom.docref.DocRef;
import stroom.docrefinfo.api.DocRefInfoService;
import stroom.event.logging.api.StroomEventLoggingService;
import stroom.event.logging.api.StroomEventLoggingUtil;
import stroom.event.logging.rs.api.AutoLogged;
import stroom.event.logging.rs.api.AutoLogged.OperationType;
import stroom.node.api.NodeService;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionUtil;
import stroom.query.api.Param;
import stroom.query.shared.QuerySearchRequest;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.NullSafe;
import stroom.util.shared.ResourcePaths;

import event.logging.Chat;
import event.logging.ComplexLoggedOutcome;
import event.logging.Data;
import event.logging.DataSources;
import event.logging.MultiObject;
import event.logging.Query;
import event.logging.SearchEventAction;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.ws.rs.client.Entity;

import java.util.List;

@AutoLogged
class AskStroomAiResourceImpl implements AskStroomAiResource {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(AskStroomAiResourceImpl.class);

    private final Provider<NodeService> nodeServiceProvider;
    private final Provider<AskStroomAIService> askStroomAIServiceProvider;
    private final Provider<StroomEventLoggingService> stroomEventLoggingServiceProvider;
    private final Provider<DocRefInfoService> docRefInfoServiceProvider;

    @Inject
    AskStroomAiResourceImpl(final Provider<NodeService> nodeServiceProvider,
                            final Provider<AskStroomAIService> askStroomAIServiceProvider,
                            final Provider<StroomEventLoggingService> stroomEventLoggingServiceProvider,
                            final Provider<DocRefInfoService> docRefInfoServiceProvider) {
        this.nodeServiceProvider = nodeServiceProvider;
        this.askStroomAIServiceProvider = askStroomAIServiceProvider;
        this.stroomEventLoggingServiceProvider = stroomEventLoggingServiceProvider;
        this.docRefInfoServiceProvider = docRefInfoServiceProvider;
    }

    @AutoLogged(OperationType.MANUALLY_LOGGED)
    @Override
    public AskStroomAiResponse askStroomAi(final String nodeName, final AskStroomAiRequest request) {

        final StroomEventLoggingService eventLoggingService = stroomEventLoggingServiceProvider.get();
        return eventLoggingService.loggedWorkBuilder()
                .withTypeId(StroomEventLoggingUtil.buildTypeId(this, "askStroomAi"))
                .withDescription(getDescription(request))
                .withDefaultEventAction(getSearchEventAction(request))
                .withComplexLoggedResult(searchEventAction -> {
                    // Do the work
                    final AskStroomAiResponse response;
                    final AskStroomAIService aiService = askStroomAIServiceProvider.get();
                    try {
                        // Make sure we ask the question on the right node.
                        final String node = aiService.getBestNode(nodeName, request);

                        if (node == null) {
                            // If the client doesn't specify a node then execute locally.
                            response = askStroomAIServiceProvider.get().askStroomAi(request);

                        } else {
                            // Execute remotely.
                            response = nodeServiceProvider.get()
                                    .remoteRestResult(
                                            node,
                                            AskStroomAiResponse.class,
                                            () -> ResourcePaths.buildAuthenticatedApiPath(
                                                    AskStroomAiResource.BASE_PATH,
                                                    AskStroomAiResource.ASK_STROOM_AI_PATH_PART,
                                                    node),
                                            () -> askStroomAIServiceProvider.get().askStroomAi(request),
                                            builder -> builder.post(Entity.json(request)));
                        }
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
        return switch (context) {
            case final DashboardTableContext dashboardTableContext -> "Asking AI about dashboard search results";
            case final QueryTableContext queryTableContext -> "Asking AI about Stroom QL search results";
            case final GeneralTableContext generalTableContext -> "Asking AI about general table data";
        };
    }

    private SearchEventAction getSearchEventAction(final AskStroomAiRequest request) {
        final AskStroomAiContext context = request.getContext();
        final String dataSourceInfo = switch (context) {
            case final DashboardTableContext dashboardTableContext -> getDashboardDataSource(dashboardTableContext);
            case final QueryTableContext queryTableContext ->
                    NullSafe.get(queryTableContext, QueryTableContext::getSearchRequest, QuerySearchRequest::getQuery);
            case final GeneralTableContext generalTableContext -> "General Table";
        };

        final event.logging.DataSources dataSources = DataSources.builder().addDataSource(dataSourceInfo).build();
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

    private String getDashboardDataSource(final DashboardTableContext dashboardTableContext) {
        final DashboardSearchRequest searchRequest = dashboardTableContext.getSearchRequest();
        final DocRef dataSourceRef = NullSafe.get(
                searchRequest,
                DashboardSearchRequest::getSearch,
                Search::getDataSourceRef);
        final Search search = NullSafe.get(searchRequest, DashboardSearchRequest::getSearch);
        final List<Param> params = NullSafe.get(search, Search::getParams);
        final ExpressionOperator deReferencedExpression = ExpressionUtil.replaceExpressionParameters(
                NullSafe.get(search, Search::getExpression),
                params);

        final String dataSourceInfo = getDataSourceString(dataSourceRef);
        return dataSourceInfo +
               " query:\n" + NullSafe.getOrElse(
                deReferencedExpression,
                ExpressionOperator::toMultiLineString,
                "");
    }

    private String getDataSourceString(final DocRef dataSourceRef) {
        final StringBuilder sb = new StringBuilder();

        final String type = NullSafe.get(dataSourceRef, DocRef::getType);
        if (NullSafe.isNonBlankString(type)) {
            sb.append(type);
        }

        final String dataSourceName = getDataSourceName(dataSourceRef);
        if (NullSafe.isNonBlankString(dataSourceName)) {
            if (!sb.isEmpty()) {
                sb.append(" ");
            }
            sb.append(dataSourceName);
        }

        final String uuid = NullSafe.get(dataSourceRef, DocRef::getUuid);
        if (NullSafe.isNonBlankString(uuid)) {
            if (!sb.isEmpty()) {
                sb.append(" ");
            }
            sb.append("{");
            sb.append(uuid);
            sb.append("}");
        }

        if (sb.isEmpty()) {
            sb.append("Unknown");
        }

        return sb.toString();
    }

    private String getDataSourceName(final DocRef docRef) {
        if (docRef == null) {
            return null;
        }

        try {
            return docRefInfoServiceProvider.get().name(docRef)
                    .orElse(docRef.getName());
        } catch (final RuntimeException e) {
            // We might not have an explorer handler capable of getting info.
            LOGGER.debug(e.getMessage(), e);
        }

        return docRef.getName();
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
