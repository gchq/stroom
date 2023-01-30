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
import stroom.docref.DocRef;
import stroom.docstore.api.DocumentResourceHelper;
import stroom.event.logging.rs.api.AutoLogged;
import stroom.node.api.NodeInfo;
import stroom.query.api.v2.Query;
import stroom.query.api.v2.QueryKey;
import stroom.query.api.v2.ResultRequest;
import stroom.query.api.v2.SearchRequest;
import stroom.query.api.v2.SearchResponse;
import stroom.query.common.v2.ResultStoreManager;
import stroom.query.language.DataSourceResolver;
import stroom.query.language.SearchRequestBuilder;
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
import stroom.util.string.ExceptionStringUtil;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.function.Supplier;
import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;

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
    private final DataSourceResolver dataSourceResolver;
    private final ResultStoreManager searchResponseCreatorManager;
    private final NodeInfo nodeInfo;

    @Inject
    QueryServiceImpl(final QueryStore queryStore,
                     final DocumentResourceHelper documentResourceHelper,
                     final SearchEventLog searchEventLog,
                     final SecurityContext securityContext,
                     final HttpServletRequestHolder httpServletRequestHolder,
                     final ExecutorProvider executorProvider,
                     final TaskContextFactory taskContextFactory,
                     final DataSourceResolver dataSourceResolver,
                     final ResultStoreManager searchResponseCreatorManager,
                     final NodeInfo nodeInfo) {
        this.queryStore = queryStore;
        this.documentResourceHelper = documentResourceHelper;
        this.searchEventLog = searchEventLog;
        this.securityContext = securityContext;
        this.httpServletRequestHolder = httpServletRequestHolder;
        this.executorProvider = executorProvider;
        this.taskContextFactory = taskContextFactory;
        this.dataSourceResolver = dataSourceResolver;
        this.searchResponseCreatorManager = searchResponseCreatorManager;
        this.nodeInfo = nodeInfo;
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
        return null;
//        try {
//            final FieldIndex fieldIndex = new FieldIndex();
//            final ExpressionParser expressionParser = new ExpressionParser(new ParamFactory());
//            final Expression expression = expressionParser.parse(fieldIndex, expressionString);
//            String correctedExpression = "";
//            if (expression != null) {
//                correctedExpression = expression.toString();
//            }
//            return new ValidateExpressionResult(true, correctedExpression);
//        } catch (final ParseException e) {
//            return new ValidateExpressionResult(false, e.getMessage());
//        }
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
        QueryKey queryKey = searchRequest.getQueryKey();
        final String query = searchRequest.getQuery();
        QueryContext queryContext = searchRequest.getQueryContext();
        Query sampleQuery = Query
                .builder()
                .params(queryContext.getParams())
                .timeRange(queryContext.getTimeRange())
                .build();
        SearchRequest sampleRequest = new SearchRequest(
                queryKey,
                sampleQuery,
                null,
                queryContext.getDateTimeSettings(),
                searchRequest.isIncremental());
        SearchRequest mappedRequest = SearchRequestBuilder.create(query, sampleRequest);
        mappedRequest = dataSourceResolver.resolveDataSource(mappedRequest);

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
                            searchRequest.getQueryContext().getQueryInfo());
                }

            } catch (final RuntimeException e) {
                LOGGER.debug(() -> "Error processing search " + searchRequest, e);

                if (queryKey == null) {
                    // FIXME : FIX
                    // searchEventLog
                    // .search(search.getDataSourceRef(), search.getExpression(), search.getQueryInfo(), e);
                }

                result = new DashboardSearchResponse(
                        nodeInfo.getThisNodeName(),
                        queryKey,
                        null,
                        Collections.singletonList(ExceptionStringUtil.getMessage(e)),
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
}
