/*
 * Copyright 2022 Crown Copyright
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

import stroom.dashboard.impl.FunctionService;
import stroom.dashboard.impl.StructureElementService;
import stroom.dashboard.shared.DashboardSearchResponse;
import stroom.dashboard.shared.FunctionSignature;
import stroom.dashboard.shared.StructureElement;
import stroom.dashboard.shared.ValidateExpressionResult;
import stroom.datasource.api.v2.AbstractField;
import stroom.datasource.api.v2.DataSource;
import stroom.docref.DocRef;
import stroom.event.logging.rs.api.AutoLogged;
import stroom.event.logging.rs.api.AutoLogged.OperationType;
import stroom.node.api.NodeService;
import stroom.query.shared.DownloadQueryResultsRequest;
import stroom.query.shared.QueryDoc;
import stroom.query.shared.QueryHelpItemsRequest;
import stroom.query.shared.QueryHelpItemsRequest.HelpItemType;
import stroom.query.shared.QueryHelpItemsResult;
import stroom.query.shared.QueryResource;
import stroom.query.shared.QuerySearchRequest;
import stroom.util.NullSafe;
import stroom.util.filter.FilterFieldMapper;
import stroom.util.filter.FilterFieldMappers;
import stroom.util.filter.QuickFilterPredicateFactory;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.EntityServiceException;
import stroom.util.shared.ResourceGeneration;
import stroom.util.shared.ResourcePaths;
import stroom.util.shared.filter.FilterFieldDefinition;
import stroom.view.api.ViewStore;

import jakarta.ws.rs.client.Entity;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import jakarta.inject.Inject;
import jakarta.inject.Provider;

@AutoLogged
class QueryResourceImpl implements QueryResource {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(QueryResourceImpl.class);

    public static final String FIELD_NAME = "Name";
    public static final FilterFieldDefinition FIELD_DEF_NAME = FilterFieldDefinition.defaultField(FIELD_NAME);
    public static final FilterFieldMappers<DocRef> DOC_REF_FILTER_FIELD_MAPPERS = FilterFieldMappers.of(
            FilterFieldMapper.of(FIELD_DEF_NAME, DocRef::getName));
    public static final FilterFieldMappers<StructureElement> STRUCTURE_ELEMENTS_FILTER_FIELD_MAPPERS =
            FilterFieldMappers.of(FilterFieldMapper.of(FIELD_DEF_NAME, StructureElement::getTitle));
    public static final FilterFieldMappers<FunctionSignature> FUNC_SIG_FILTER_FIELD_MAPPERS = FilterFieldMappers.of(
            FilterFieldMapper.of(FIELD_DEF_NAME, FunctionSignature::getName));
    public static final FilterFieldMappers<AbstractField> FIELD_FILTER_FIELD_MAPPERS = FilterFieldMappers.of(
            FilterFieldMapper.of(FIELD_DEF_NAME, AbstractField::getName));

    private final Provider<NodeService> nodeServiceProvider;
    private final Provider<QueryService> queryServiceProvider;
    private final Provider<FunctionService> functionServiceProvider;
    private final Provider<StructureElementService> structureElementServiceProvider;
    private final Provider<ViewStore> viewStoreProvider;

    @Inject
    QueryResourceImpl(final Provider<NodeService> nodeServiceProvider,
                      final Provider<QueryService> dashboardServiceProvider,
                      final Provider<FunctionService> functionServiceProvider,
                      final Provider<StructureElementService> structureElementServiceProvider,
                      final Provider<ViewStore> viewStoreProvider) {
        this.nodeServiceProvider = nodeServiceProvider;
        this.queryServiceProvider = dashboardServiceProvider;
        this.functionServiceProvider = functionServiceProvider;
        this.structureElementServiceProvider = structureElementServiceProvider;
        this.viewStoreProvider = viewStoreProvider;
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

    @AutoLogged(OperationType.UNLOGGED)
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

    @AutoLogged(OperationType.UNLOGGED)
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

    @Override
    @AutoLogged(OperationType.UNLOGGED)
    public List<FunctionSignature> fetchFunctions() {
        return functionServiceProvider.get().getSignatures();
    }

    @Override
    @AutoLogged(OperationType.UNLOGGED)
    public List<StructureElement> fetchStructureElements() {
        return structureElementServiceProvider.get().getStructureElements();
    }

    @AutoLogged(OperationType.UNLOGGED) // Called on each keystroke and has little audit value
    @Override
    public QueryHelpItemsResult fetchQueryHelpItems(final QueryHelpItemsRequest request) {

        final List<DocRef> dataSources = getData(
                request,
                HelpItemType.DATA_SOURCE,
                DOC_REF_FILTER_FIELD_MAPPERS,
                () -> viewStoreProvider.get().list());

        final List<StructureElement> structureElements = getData(
                request,
                HelpItemType.STRUCTURE,
                STRUCTURE_ELEMENTS_FILTER_FIELD_MAPPERS,
                () -> structureElementServiceProvider.get().getStructureElements());

        final List<FunctionSignature> functionSignatures = getData(
                request,
                HelpItemType.FUNCTION,
                FUNC_SIG_FILTER_FIELD_MAPPERS,
                () -> functionServiceProvider.get()
                        .getSignatures()
                        .stream()
                        .flatMap(sig -> sig.asAliases().stream())
                        .collect(Collectors.toList()));

        final Optional<DataSource> optional = Optional.ofNullable(request.getDataSourceRef())
                .map(docRef -> queryServiceProvider.get().getDataSource(docRef))
                .orElse(queryServiceProvider.get().getDataSource(request.getQuery()));

        final List<AbstractField> dataSourceFields = optional
                .map(ds -> getData(request, HelpItemType.FIELD, FIELD_FILTER_FIELD_MAPPERS, ds::getFields))
                .orElse(Collections.emptyList());

        return new QueryHelpItemsResult(
                dataSources,
                structureElements,
                functionSignatures,
                dataSourceFields);
    }

    private <T> List<T> getData(final QueryHelpItemsRequest request,
                                final HelpItemType helpItemType,
                                final FilterFieldMappers<T> filterFieldMappers,
                                final Supplier<List<T>> dataSupplier) {
        final String filterInput = request.getFilterInput();
        final Set<HelpItemType> requestedTypes = request.getRequestedTypes();

        if (requestedTypes.contains(helpItemType)) {
            if (NullSafe.isBlankString(filterInput)) {
                return dataSupplier.get();
            } else {
                return QuickFilterPredicateFactory.filterStream(
                                filterInput,
                                filterFieldMappers,
                                NullSafe.stream(dataSupplier.get()))
                        .toList();
            }
        } else {
            return Collections.emptyList();
        }
    }
}
