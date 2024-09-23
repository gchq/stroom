/*
 * Copyright 2024 Crown Copyright
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

import stroom.dashboard.shared.DashboardSearchResponse;
import stroom.dashboard.shared.ValidateExpressionResult;
import stroom.docref.DocRef;
import stroom.event.logging.rs.api.AutoLogged;
import stroom.event.logging.rs.api.AutoLogged.OperationType;
import stroom.node.api.NodeService;
import stroom.query.shared.CompletionItem;
import stroom.query.shared.CompletionsRequest;
import stroom.query.shared.DownloadQueryResultsRequest;
import stroom.query.shared.QueryDoc;
import stroom.query.shared.QueryHelpDetail;
import stroom.query.shared.QueryHelpRequest;
import stroom.query.shared.QueryHelpRow;
import stroom.query.shared.QueryHelpType;
import stroom.query.shared.QueryResource;
import stroom.query.shared.QuerySearchRequest;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.resultpage.InexactResultPageBuilder;
import stroom.util.resultpage.ResultPageBuilder;
import stroom.util.shared.EntityServiceException;
import stroom.util.shared.PageRequest;
import stroom.util.shared.ResourceGeneration;
import stroom.util.shared.ResourcePaths;
import stroom.util.shared.ResultPage;
import stroom.util.string.StringMatcher;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.ws.rs.client.Entity;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

@AutoLogged
class QueryResourceImpl implements QueryResource {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(QueryResourceImpl.class);

    private final Provider<NodeService> nodeServiceProvider;
    private final Provider<QueryService> queryServiceProvider;
    private final Provider<DataSources> dataSourcesProvider;
    private final Provider<Structures> structuresProvider;
    private final Provider<Fields> fieldsProvider;
    private final Provider<Functions> functionsProvider;
    private final Provider<Visualisations> visualisationProvider;

    @Inject
    QueryResourceImpl(final Provider<NodeService> nodeServiceProvider,
                      final Provider<QueryService> dashboardServiceProvider,
                      final Provider<DataSources> dataSourcesProvider,
                      final Provider<Structures> structuresProvider,
                      final Provider<Fields> fieldsProvider,
                      final Provider<Functions> functionsProvider,
                      final Provider<Visualisations> visualisationProvider) {
        this.nodeServiceProvider = nodeServiceProvider;
        this.queryServiceProvider = dashboardServiceProvider;
        this.dataSourcesProvider = dataSourcesProvider;
        this.structuresProvider = structuresProvider;
        this.fieldsProvider = fieldsProvider;
        this.functionsProvider = functionsProvider;
        this.visualisationProvider = visualisationProvider;
    }

    @Override
    public QueryDoc fetch(final String uuid) {
        return queryServiceProvider.get().read(getDocRef(uuid));
    }

    @Override
    public QueryDoc update(final String uuid, final QueryDoc doc) {
        if (doc.getUuid() == null || !doc.getUuid().equals(uuid)) {
            throw new EntityServiceException("The document UUID must match the update UUID");
        }
        return queryServiceProvider.get().update(doc);
    }

    private DocRef getDocRef(final String uuid) {
        return DocRef.builder()
                .uuid(uuid)
                .type(QueryDoc.DOCUMENT_TYPE)
                .build();
    }

    @Override
    @AutoLogged(OperationType.UNLOGGED)
    public ValidateExpressionResult validateQuery(final String query) {
        return queryServiceProvider.get().validateQuery(query);
    }

    @AutoLogged(OperationType.MANUALLY_LOGGED)
    @Override
    public ResourceGeneration downloadSearchResults(final String nodeName, final DownloadQueryResultsRequest request) {
        try {
            // If the client doesn't specify a node then execute locally.
            if (nodeName == null || nodeName.equals("null")) {
                return queryServiceProvider.get().downloadSearchResults(request);
            }

            return nodeServiceProvider.get()
                    .remoteRestResult(
                            nodeName,
                            ResourceGeneration.class,
                            () -> ResourcePaths.buildAuthenticatedApiPath(
                                    QueryResource.BASE_PATH,
                                    QueryResource.DOWNLOAD_SEARCH_RESULTS_PATH_PATH,
                                    nodeName),
                            () -> queryServiceProvider.get().downloadSearchResults(request),
                            builder -> builder.post(Entity.json(request)));
        } catch (final RuntimeException e) {
            LOGGER.debug(e.getMessage(), e);
            throw e;
        }
    }

    @AutoLogged(OperationType.MANUALLY_LOGGED)
    @Override
    public DashboardSearchResponse search(final String nodeName, final QuerySearchRequest request) {
        try {
            // If the client doesn't specify a node then execute locally.
            if (nodeName == null || nodeName.equals("null")) {
                return queryServiceProvider.get().search(request);
            }

            return nodeServiceProvider.get()
                    .remoteRestResult(
                            nodeName,
                            DashboardSearchResponse.class,
                            () -> ResourcePaths.buildAuthenticatedApiPath(
                                    QueryResource.BASE_PATH,
                                    QueryResource.SEARCH_PATH_PART,
                                    nodeName),
                            () -> queryServiceProvider.get().search(request),
                            builder -> builder.post(Entity.json(request)));
        } catch (final RuntimeException e) {
            LOGGER.debug(e.getMessage(), e);
            throw e;
        }
    }

    @Override
    @AutoLogged(OperationType.UNLOGGED)
    public List<String> fetchTimeZones() {
        return queryServiceProvider.get().fetchTimeZones();
    }

    @AutoLogged(OperationType.UNLOGGED) // Called on each keystroke and has little audit value
    @Override
    public ResultPage<QueryHelpRow> fetchQueryHelpItems(final QueryHelpRequest request) {
        final String parentPath = request.getParentPath();
        final StringMatcher stringMatcher = new StringMatcher(request.getStringMatch());
        final ResultPageBuilder<QueryHelpRow> resultPageBuilder =
                new InexactResultPageBuilder<>(request.getPageRequest());
        PageRequest pageRequest = request.getPageRequest();
        if (request.isTypeIncluded(QueryHelpType.DATA_SOURCE)) {
            dataSourcesProvider.get().addRows(pageRequest, parentPath, stringMatcher, resultPageBuilder);
            pageRequest = reducePageRequest(pageRequest, resultPageBuilder.size());
        }
        if (request.isTypeIncluded(QueryHelpType.STRUCTURE)) {
            structuresProvider.get().addRows(pageRequest, parentPath, stringMatcher, resultPageBuilder);
            pageRequest = reducePageRequest(pageRequest, resultPageBuilder.size());
        }
        request.setPageRequest(pageRequest);
        if (request.isTypeIncluded(QueryHelpType.FIELD)) {
            fieldsProvider.get().addRows(request, resultPageBuilder);
            pageRequest = reducePageRequest(pageRequest, resultPageBuilder.size());
        }
        if (request.isTypeIncluded(QueryHelpType.FUNCTION)) {
            functionsProvider.get().addRows(pageRequest, parentPath, stringMatcher, resultPageBuilder);
        }
        if (request.isTypeIncluded(QueryHelpType.VISUALISATION)) {
            visualisationProvider.get().addRows(pageRequest, parentPath, stringMatcher, resultPageBuilder);
        }
        return resultPageBuilder.build();
    }

    @Override
    @AutoLogged(OperationType.UNLOGGED)
    public ResultPage<CompletionItem> fetchCompletions(final CompletionsRequest request) {
        final List<CompletionItem> list = new ArrayList<>();
        final ContextualQueryHelp contextualQueryHelp = queryServiceProvider.get()
                .getQueryHelpContext(request.getText(), request.getRow(), request.getColumn());

        final Set<QueryHelpType> contextualHelpTypes = contextualQueryHelp.queryHelpTypes();

        LOGGER.debug("\n  request: {}, \n  contextualQueryHelp: {}", request, contextualQueryHelp);
        // Only return the types asked for by the client and that are appropriate for the context
        final AtomicInteger maxCompletions = new AtomicInteger(request.getMaxCompletions());
        if (isTypeIncluded(request, contextualHelpTypes, QueryHelpType.DATA_SOURCE)) {
            dataSourcesProvider.get().addCompletions(request, reduceMaxCompletions(maxCompletions, list), list);
        }
        if (isTypeIncluded(request, contextualHelpTypes, QueryHelpType.STRUCTURE)) {
            structuresProvider.get().addCompletions(
                    request,
                    reduceMaxCompletions(maxCompletions, list),
                    list,
                    contextualQueryHelp.applicableStructureItems());
        }
        if (isTypeIncluded(request, contextualHelpTypes, QueryHelpType.FIELD)) {
            fieldsProvider.get().addCompletions(request, reduceMaxCompletions(maxCompletions, list), list);
        }
        if (isTypeIncluded(request, contextualHelpTypes, QueryHelpType.FUNCTION)) {
            functionsProvider.get().addCompletions(request, reduceMaxCompletions(maxCompletions, list), list);
        }
        if (isTypeIncluded(request, contextualHelpTypes, QueryHelpType.VISUALISATION)) {
            visualisationProvider.get().addCompletions(request, reduceMaxCompletions(maxCompletions, list), list);
        }

        return ResultPage.createUnboundedList(list);
    }

    private boolean isTypeIncluded(final CompletionsRequest request,
                                   final Set<QueryHelpType> contextualHelpTypes,
                                   final QueryHelpType queryHelpType) {
        return request.isTypeIncluded(queryHelpType)
                && contextualHelpTypes.contains(queryHelpType);
    }

    @Override
    @AutoLogged(OperationType.UNLOGGED)
    public QueryHelpDetail fetchDetail(final QueryHelpRow row) {
        if (row == null) {
            return null;
        }

        Optional<QueryHelpDetail> result = Optional.empty();
        result = result.or(() -> dataSourcesProvider.get().fetchDetail(row));
        result = result.or(() -> structuresProvider.get().fetchDetail(row));
        result = result.or(() -> fieldsProvider.get().fetchDetail(row));
        result = result.or(() -> functionsProvider.get().fetchDetail(row));
        result = result.or(() -> visualisationProvider.get().fetchDetail(row));

        return result.orElse(null);
    }

    private PageRequest reducePageRequest(final PageRequest pageRequest, final int size) {
        return new PageRequest(pageRequest.getOffset(), pageRequest.getLength() - size);
    }

    private int reduceMaxCompletions(final AtomicInteger maxCompletions, final List<?> list) {
        final int newVal = maxCompletions.addAndGet(list.size() * -1);
        return Math.max(0, newVal);
    }
}
