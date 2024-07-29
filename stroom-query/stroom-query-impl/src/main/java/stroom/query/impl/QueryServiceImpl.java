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

import stroom.dashboard.impl.SearchResponseMapper;
import stroom.dashboard.impl.logging.SearchEventLog;
import stroom.dashboard.shared.DashboardSearchResponse;
import stroom.dashboard.shared.ValidateExpressionResult;
import stroom.datasource.api.v2.FindFieldCriteria;
import stroom.datasource.api.v2.QueryField;
import stroom.docref.DocRef;
import stroom.docstore.api.DocumentResourceHelper;
import stroom.event.logging.rs.api.AutoLogged;
import stroom.expression.api.DateTimeSettings;
import stroom.node.api.NodeInfo;
import stroom.query.api.v2.Column;
import stroom.query.api.v2.Param;
import stroom.query.api.v2.Query;
import stroom.query.api.v2.QueryKey;
import stroom.query.api.v2.ResultRequest;
import stroom.query.api.v2.SearchRequest;
import stroom.query.api.v2.SearchResponse;
import stroom.query.api.v2.TableSettings;
import stroom.query.api.v2.TimeRange;
import stroom.query.common.v2.DataSourceProviderRegistry;
import stroom.query.common.v2.ExpressionContextFactory;
import stroom.query.common.v2.ResultStoreManager;
import stroom.query.language.SearchRequestFactory;
import stroom.query.language.functions.ExpressionContext;
import stroom.query.language.token.TokenException;
import stroom.query.shared.DownloadQueryResultsRequest;
import stroom.query.shared.QueryContext;
import stroom.query.shared.QueryDoc;
import stroom.query.shared.QuerySearchRequest;
import stroom.security.api.SecurityContext;
import stroom.task.api.ExecutorProvider;
import stroom.task.api.TaskContextFactory;
import stroom.task.api.TerminateHandlerFactory;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.servlet.HttpServletRequestHolder;
import stroom.util.shared.ResourceGeneration;
import stroom.util.shared.ResultPage;
import stroom.util.string.ExceptionStringUtil;

import jakarta.inject.Inject;
import jakarta.servlet.http.HttpServletRequest;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

@AutoLogged
class QueryServiceImpl implements QueryService {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(QueryServiceImpl.class);

    private final QueryStore queryStore;
    private final DocumentResourceHelper documentResourceHelper;
    private final SearchEventLog searchEventLog;
    private final SecurityContext securityContext;
    private final HttpServletRequestHolder httpServletRequestHolder;
    private final ExecutorProvider executorProvider;
    private final TaskContextFactory taskContextFactory;
    private final DataSourceProviderRegistry dataSourceProviderRegistry;
    private final ResultStoreManager searchResponseCreatorManager;
    private final NodeInfo nodeInfo;
    private final SearchRequestFactory searchRequestFactory;
    private final ExpressionContextFactory expressionContextFactory;

    @Inject
    QueryServiceImpl(final QueryStore queryStore,
                     final DocumentResourceHelper documentResourceHelper,
                     final SearchEventLog searchEventLog,
                     final SecurityContext securityContext,
                     final HttpServletRequestHolder httpServletRequestHolder,
                     final ExecutorProvider executorProvider,
                     final TaskContextFactory taskContextFactory,
                     final DataSourceProviderRegistry dataSourceProviderRegistry,
                     final ResultStoreManager searchResponseCreatorManager,
                     final NodeInfo nodeInfo,
                     final SearchRequestFactory searchRequestFactory,
                     final ExpressionContextFactory expressionContextFactory) {
        this.queryStore = queryStore;
        this.documentResourceHelper = documentResourceHelper;
        this.searchEventLog = searchEventLog;
        this.securityContext = securityContext;
        this.httpServletRequestHolder = httpServletRequestHolder;
        this.executorProvider = executorProvider;
        this.taskContextFactory = taskContextFactory;
        this.dataSourceProviderRegistry = dataSourceProviderRegistry;
        this.searchResponseCreatorManager = searchResponseCreatorManager;
        this.nodeInfo = nodeInfo;
        this.searchRequestFactory = searchRequestFactory;
        this.expressionContextFactory = expressionContextFactory;
    }

    @Override
    public QueryDoc read(final DocRef docRef) {
        return documentResourceHelper.read(queryStore, docRef);
    }

    @Override
    public QueryDoc update(final QueryDoc doc) {
        return documentResourceHelper.update(queryStore, doc);
    }

    @Override
    public ValidateExpressionResult validateQuery(final String expressionString) {
        try {
            final QuerySearchRequest searchRequest = QuerySearchRequest.builder().query(expressionString).build();
            final SearchRequest mappedRequest = mapRequest(searchRequest);
            boolean groupBy = false;
            int fieldCount = 0;
            for (final ResultRequest resultRequest : mappedRequest.getResultRequests()) {
                for (final TableSettings tableSettings : resultRequest.getMappings()) {
                    for (final Column column : tableSettings.getColumns()) {
                        fieldCount++;
                        if (column.getGroup() != null) {
                            groupBy = true;
                        }
                    }
                }
            }
            if (fieldCount == 0) {
                throw new RuntimeException("No fields found");
            }

            return new ValidateExpressionResult(true, expressionString, groupBy);
        } catch (final RuntimeException e) {
            return new ValidateExpressionResult(false, e.getMessage(), false);
        }
    }

    @Override
    public ResourceGeneration downloadSearchResults(final DownloadQueryResultsRequest request) {
        return null;
//        return securityContext.secureResult(PermissionNames.DOWNLOAD_SEARCH_RESULTS_PERMISSION, () -> {
//            ResourceKey resourceKey;
//
//            final QuerySearchRequest searchRequest = request.getSearchRequest();
//            final QueryKey queryKey = searchRequest.getQueryKey();
//            final Search search = searchRequest.getSearch();
//
//            try {
//                if (queryKey == null) {
//                    throw new EntityServiceException("No query is active");
//                }
//                final ActiveQueries activeQueries = getActiveQueries(searchRequest);
//                final Optional<ActiveQuery> optionalActiveQuery = activeQueries.getActiveQuery(queryKey);
//                final ActiveQuery activeQuery = optionalActiveQuery
//                        .orElseThrow(() -> new EntityServiceException("The requested search data is not available"));
//                SearchRequest mappedRequest = searchRequestMapper.mapRequest(searchRequest);
//                SearchResponse searchResponse = activeQuery.search(mappedRequest);
//
//                if (searchResponse == null || searchResponse.getResults() == null) {
//                    throw new EntityServiceException("No results can be found");
//                }
//
//                Result result = null;
//                for (final Result res : searchResponse.getResults()) {
//                    if (res.getComponentId().equals(request.getComponentId())) {
//                        result = res;
//                        break;
//                    }
//                }
//
//                if (result == null) {
//                    throw new EntityServiceException("No result for component can be found");
//                }
//
//                if (!(result instanceof TableResult)) {
//                    throw new EntityServiceException("Result is not a table");
//                }
//
//                final TableResult tableResult = (TableResult) result;
//
//                // Import file.
//                String fileName = getResultsFilename(request);
//
//                resourceKey = resourceStore.createTempFile(fileName);
//                final Path file = resourceStore.getTempFile(resourceKey);
//
//                final Optional<ComponentResultRequest> optional = searchRequest.getComponentResultRequests()
//                        .stream()
//                        .filter(r -> r.getComponentId().equals(request.getComponentId()))
//                        .findFirst();
//                if (optional.isEmpty()) {
//                    throw new EntityServiceException("No component result request found");
//                }
//
//                if (!(optional.get() instanceof TableResultRequest)) {
//                    throw new EntityServiceException("Component result request is not a table");
//                }
//
//                final TableResultRequest tableResultRequest = (TableResultRequest) optional.get();
//                final List<Field> fields = tableResultRequest.getTableSettings().getFields();
//                final List<Row> rows = tableResult.getRows();
//
//                download(fields, rows, file, request.getFileType(), request.isSample(), request.getPercent(),
//                        searchRequest.getDateTimeSettings());
//
//                searchEventLog.downloadResults(search.getDataSourceRef(),
//                        search.getExpression(),
//                        search.getQueryInfo());
//            } catch (final RuntimeException e) {
//                searchEventLog.downloadResults(search.getDataSourceRef(),
//                        search.getExpression(),
//                        search.getQueryInfo(),
//                        e);
//                throw EntityServiceExceptionUtil.create(e);
//            }
//
//            return new ResourceGeneration(resourceKey, new ArrayList<>());
//        });
    }
//
//    private String getResultsFilename(final DownloadSearchResultsRequest request) {
//        final DashboardSearchRequest searchRequest = request.getSearchRequest();
//        final String basename = request.getComponentId() + "__" + searchRequest.getQueryKey().getUuid();
//        return getFileName(basename, request.getFileType().getExtension());
//    }
//
//    private String getQueryFileName(final DashboardSearchRequest request) {
//        final DocRefInfo dashDocRefInfo = queryStore.info(request.getDashboardUuid());
//        final String dashboardName = NullSafe.getOrElse(
//                dashDocRefInfo,
//                DocRefInfo::getDocRef,
//                DocRef::getName,
//                request.getDashboardUuid());
//        final String basename = dashboardName + "__" + request.getComponentId();
//        return getFileName(basename, "json");
//    }
//
//    private String getFileName(final String baseName,
//                               final String extension) {
//        String fileName = baseName;
//        fileName = NON_BASIC_CHARS.matcher(fileName).replaceAll("");
//        fileName = MULTIPLE_SPACE.matcher(fileName).replaceAll(" ");
//        fileName = fileName.replace(" ", "_");
//        fileName = fileName + "." + extension;
//        return fileName;
//    }
//
//    private void download(final List<Field> fields,
//                          final List<Row> rows,
//                          final Path file,
//                          final DownloadSearchResultFileType fileType,
//                          final boolean sample,
//                          final int percent,
//                          final DateTimeSettings dateTimeSettings) {
//        try (final OutputStream outputStream = new BufferedOutputStream(Files.newOutputStream(file))) {
//            SearchResultWriter.Target target = null;
//
//            // Write delimited file.
//            switch (fileType) {
//                case CSV:
//                    target = new DelimitedTarget(outputStream, ",");
//                    break;
//                case TSV:
//                    target = new DelimitedTarget(outputStream, "\t");
//                    break;
//                case EXCEL:
//                    target = new ExcelTarget(outputStream, dateTimeSettings);
//                    break;
//            }
//
//            final SampleGenerator sampleGenerator = new SampleGenerator(sample, percent);
//            final SearchResultWriter searchResultWriter = new SearchResultWriter(fields, rows, sampleGenerator);
//            searchResultWriter.write(target);
//
//        } catch (final IOException e) {
//            throw EntityServiceExceptionUtil.create(e);
//        }
//    }

    @Override
    public DashboardSearchResponse search(final QuerySearchRequest request) {
        LOGGER.trace(() -> "search() " + request);
        return securityContext.secureResult(() -> {
            // Elevate the users permissions for the duration of this task so they can read the index if they have
            // 'use' permission.
            return securityContext.useAsReadResult(() -> {
                // Get query results for every active query.
                final HttpServletRequest httpServletRequest = httpServletRequestHolder.get();
                final Executor executor = executorProvider.get();

                try {
                    final Supplier<DashboardSearchResponse> supplier = taskContextFactory.contextResult(
                            "Dashboard Search",
                            TerminateHandlerFactory.NOOP_FACTORY,
                            taskContext -> {
                                DashboardSearchResponse searchResponse;
                                try {
                                    taskContext.info(() -> "Polling for new search results");
                                    httpServletRequestHolder.set(httpServletRequest);
                                    searchResponse = processRequest(request);
                                } finally {
                                    httpServletRequestHolder.set(null);
                                }
                                return searchResponse;
                            });
                    return CompletableFuture.supplyAsync(supplier, executor).get();

                } catch (final InterruptedException | ExecutionException e) {
                    LOGGER.debug(e.getMessage(), e);
                    return null;
                }
            });
        });
    }

//    @Override
//    public Boolean destroy(final DestroyQueryRequest request) {
//        return searchResponseCreatorManager.destroy(request.getQueryKey());
//    }

    private SearchRequest mapRequest(final QuerySearchRequest searchRequest) {
        final QueryKey queryKey = searchRequest.getQueryKey();
        final String query = searchRequest.getQuery();
        final QueryContext queryContext = searchRequest.getQueryContext();

        List<Param> params = null;
        TimeRange timeRange = null;
        DateTimeSettings dateTimeSettings = null;
        if (queryContext != null) {
            params = queryContext.getParams();
            timeRange = queryContext.getTimeRange();
            dateTimeSettings = queryContext.getDateTimeSettings();
        }
        final Query sampleQuery = Query
                .builder()
                .params(params)
                .timeRange(timeRange)
                .build();
        final SearchRequest sampleRequest = new SearchRequest(
                searchRequest.getSearchRequestSource(),
                queryKey,
                sampleQuery,
                null,
                dateTimeSettings,
                searchRequest.isIncremental(),
                searchRequest.getTimeout());
        final ExpressionContext expressionContext = expressionContextFactory.createContext(sampleRequest);
        SearchRequest mappedRequest = searchRequestFactory.create(query, sampleRequest, expressionContext);

        // Fix table result requests.
        final List<ResultRequest> resultRequests = mappedRequest.getResultRequests();
        if (resultRequests != null) {
            List<ResultRequest> modifiedResultRequests = new ArrayList<>();
            for (final ResultRequest resultRequest : resultRequests) {
                modifiedResultRequests.add(
                        resultRequest
                                .copy()
                                .openGroups(searchRequest.getOpenGroups())
                                .requestedRange(searchRequest.getRequestedRange())
                                .build()
                );
            }
            mappedRequest = mappedRequest.copy().resultRequests(modifiedResultRequests).build();
        }

        return mappedRequest;
    }

    private DashboardSearchResponse processRequest(final QuerySearchRequest searchRequest) {
        LOGGER.trace(() -> "processRequest() " + searchRequest);
        DashboardSearchResponse result = null;

        QueryKey queryKey = searchRequest.getQueryKey();
        final String query = searchRequest.getQuery();

        if (query != null) {
            try {
                final SearchRequest mappedRequest = mapRequest(searchRequest);
                LOGGER.debug("searchRequest:\n{}\nmappedRequest:\n{}", searchRequest, mappedRequest);

                // Perform the search or update results.
                final SearchResponse searchResponse = searchResponseCreatorManager.search(mappedRequest);
                result = new SearchResponseMapper().mapResponse(nodeInfo.getThisNodeName(), searchResponse);

                if (queryKey == null) {
                    // If the query doesn't have a key then this is new.
                    LOGGER.debug(() -> "New query");

                    // Add this search to the history so the user can get back to this
                    // search again.
                    storeSearchHistory(searchRequest);

                    // Log this search request for the current user.
                    searchEventLog.search(
                            mappedRequest.getQuery().getDataSource(),
                            mappedRequest.getQuery().getExpression(),
                            searchRequest.getQueryContext().getQueryInfo(),
                            searchRequest.getQueryContext().getParams());
                }

            } catch (final TokenException e) {
                LOGGER.debug(() -> "Error processing search " + searchRequest, e);

                if (queryKey == null) {
                    searchEventLog.search(searchRequest.getQuery(),
                            searchRequest.getQueryContext().getQueryInfo(),
                            searchRequest.getQueryContext().getParams(),
                            e);
                }

                result = new DashboardSearchResponse(
                        nodeInfo.getThisNodeName(),
                        queryKey,
                        null,
                        Collections.singletonList(ExceptionStringUtil.getMessage(e)),
                        TokenExceptionUtil.toTokenError(e),
                        true,
                        null);

            } catch (final RuntimeException e) {
                LOGGER.debug(() -> "Error processing search " + searchRequest, e);

                if (queryKey == null) {
                    searchEventLog.search(searchRequest.getQuery(),
                            searchRequest.getQueryContext().getQueryInfo(),
                            searchRequest.getQueryContext().getParams(),
                            e);
                }

                result = new DashboardSearchResponse(
                        nodeInfo.getThisNodeName(),
                        queryKey,
                        null,
                        Collections.singletonList(ExceptionStringUtil.getMessage(e)),
                        null,
                        true,
                        null);
            }
        }

        return result;
    }

    private void storeSearchHistory(final QuerySearchRequest request) {
//        // We only want to record search history for user initiated searches.
//        if (request.isStoreHistory()) {
//            try {
//                // Add this search to the history so the user can get back to
//                // this search again.
//                final Search search = request.getSearch();
//                final Query query = new Query(
//                        search.getDataSourceRef(),
//                        search.getExpression(),
//                        search.getParams(),
//                        search.getTimeRange());
//
//                final StoredQuery storedQuery = new StoredQuery();
//                storedQuery.setName("History");
//                storedQuery.setDashboardUuid(request.getQueryDocUuid());
//                storedQuery.setComponentId(request.getComponentId());
//                storedQuery.setQuery(query);
//                queryService.create(storedQuery);
//
//            } catch (final RuntimeException e) {
//                LOGGER.error(e::getMessage, e);
//            }
//        }
    }

    @Override
    public List<String> fetchTimeZones() {
        final List<String> ids = new ArrayList<>(ZoneId.getAvailableZoneIds());
        ids.sort(Comparator.naturalOrder());
        return ids;
    }

    @Override
    public DocRef fetchDefaultExtractionPipeline(final DocRef dataSourceRef) {
        return securityContext.useAsReadResult(() ->
                dataSourceProviderRegistry.fetchDefaultExtractionPipeline(dataSourceRef));
    }

    @Override
    public Optional<DocRef> getReferencedDataSource(final String query) {
        return securityContext.useAsReadResult(() -> {
            final AtomicReference<DocRef> ref = new AtomicReference<>();
            try {
                searchRequestFactory.extractDataSourceOnly(query, ref::set);
            } catch (final RuntimeException e) {
                LOGGER.debug(e::getMessage, e);
            }
            return Optional.ofNullable(ref.get());
        });
    }

    @Override
    public ResultPage<QueryField> findFields(final FindFieldCriteria criteria) {
        return securityContext.useAsReadResult(() -> dataSourceProviderRegistry.getFieldInfo(criteria));
    }

    @Override
    public Optional<String> fetchDocumentation(final DocRef docRef) {
        return securityContext.useAsReadResult(() -> dataSourceProviderRegistry.fetchDocumentation(docRef));
    }
}
