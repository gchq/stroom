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

import stroom.dashboard.impl.ActiveQueries;
import stroom.dashboard.impl.ActiveQuery;
import stroom.dashboard.impl.ApplicationInstance;
import stroom.dashboard.impl.ApplicationInstanceManager;
import stroom.dashboard.impl.FunctionService;
import stroom.dashboard.impl.SearchRequestMapper;
import stroom.dashboard.impl.SearchResponseMapper;
import stroom.dashboard.impl.logging.SearchEventLog;
import stroom.dashboard.shared.DashboardSearchResponse;
import stroom.dashboard.shared.ValidateExpressionResult;
import stroom.datasource.api.v2.DataSourceProvider;
import stroom.datasource.api.v2.DateField;
import stroom.docref.DocRef;
import stroom.docrefinfo.api.DocRefInfoService;
import stroom.docstore.api.DocumentResourceHelper;
import stroom.event.logging.rs.api.AutoLogged;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.ExpressionOperator.Op;
import stroom.query.api.v2.ExpressionTerm.Condition;
import stroom.query.api.v2.Param;
import stroom.query.api.v2.Query;
import stroom.query.api.v2.QueryKey;
import stroom.query.api.v2.ResultRequest;
import stroom.query.api.v2.SearchRequest;
import stroom.query.api.v2.SearchResponse;
import stroom.query.api.v2.TimeRange;
import stroom.query.common.v2.DataSourceProviderRegistry;
import stroom.query.language.DataSourceResolver;
import stroom.query.language.SearchRequestBuilder;
import stroom.query.shared.DestroyQueryRequest;
import stroom.query.shared.DownloadQueryResultsRequest;
import stroom.query.shared.QueryContext;
import stroom.query.shared.QueryDoc;
import stroom.query.shared.QuerySearchRequest;
import stroom.resource.api.ResourceStore;
import stroom.security.api.SecurityContext;
import stroom.storedquery.api.StoredQueryService;
import stroom.task.api.ExecutorProvider;
import stroom.task.api.TaskContextFactory;
import stroom.task.api.TerminateHandlerFactory;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.servlet.HttpServletRequestHolder;
import stroom.util.shared.EntityServiceException;
import stroom.util.shared.ResourceGeneration;
import stroom.util.string.ExceptionStringUtil;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.servlet.http.HttpServletRequest;

@AutoLogged
class QueryServiceImpl implements QueryService {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(QueryServiceImpl.class);

    private static final Pattern NON_BASIC_CHARS = Pattern.compile("[^A-Za-z0-9-_ ]");
    private static final Pattern MULTIPLE_SPACE = Pattern.compile(" +");

    private final QueryStore queryStore;
    private final StoredQueryService queryService;
    private final DocumentResourceHelper documentResourceHelper;
    private final SearchRequestMapper searchRequestMapper;
    private final ResourceStore resourceStore;
    private final SearchEventLog searchEventLog;
    private final ApplicationInstanceManager applicationInstanceManager;
    private final DataSourceProviderRegistry dataSourceProviderRegistry;
    private final SecurityContext securityContext;
    private final HttpServletRequestHolder httpServletRequestHolder;
    private final ExecutorProvider executorProvider;
    private final TaskContextFactory taskContextFactory;
    private final Provider<FunctionService> functionServiceProvider;
    private final DataSourceResolver dataSourceResolver;

    @Inject
    QueryServiceImpl(final QueryStore queryStore,
                     final StoredQueryService queryService,
                     final DocumentResourceHelper documentResourceHelper,
                     final SearchRequestMapper searchRequestMapper,
                     final ResourceStore resourceStore,
                     final SearchEventLog searchEventLog,
                     final ApplicationInstanceManager applicationInstanceManager,
                     final DataSourceProviderRegistry dataSourceProviderRegistry,
                     final SecurityContext securityContext,
                     final HttpServletRequestHolder httpServletRequestHolder,
                     final ExecutorProvider executorProvider,
                     final TaskContextFactory taskContextFactory,
                     final Provider<FunctionService> functionServiceProvider,
                     final DataSourceResolver dataSourceResolver) {
        this.queryStore = queryStore;
        this.queryService = queryService;
        this.documentResourceHelper = documentResourceHelper;
        this.searchRequestMapper = searchRequestMapper;
        this.resourceStore = resourceStore;
        this.searchEventLog = searchEventLog;
        this.applicationInstanceManager = applicationInstanceManager;
        this.dataSourceProviderRegistry = dataSourceProviderRegistry;
        this.securityContext = securityContext;
        this.httpServletRequestHolder = httpServletRequestHolder;
        this.executorProvider = executorProvider;
        this.taskContextFactory = taskContextFactory;
        this.functionServiceProvider = functionServiceProvider;
        this.dataSourceResolver = dataSourceResolver;
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

    @Override
    public Boolean destroy(final DestroyQueryRequest request) {
        return getApplicationInstance(request.getApplicationInstanceUuid())
                .getActiveQueries()
                .destroyActiveQuery(request.getQueryKey())
                .isPresent();
    }

    private ActiveQueries getActiveQueries(final QuerySearchRequest request) {
        return getApplicationInstance(request.getApplicationInstanceUuid())
                .getActiveQueries();
    }

    private ApplicationInstance getApplicationInstance(final String applicationInstanceUuid) {
        if (applicationInstanceUuid == null) {
            throw new EntityServiceException("""
                    Session expired, please refresh your browser.

                    Null application instance id.""");
        }
        final Optional<ApplicationInstance> optionalApplicationInstance =
                applicationInstanceManager.getOptApplicationInstance(applicationInstanceUuid);
        final ApplicationInstance applicationInstance = optionalApplicationInstance.orElseThrow(() ->
                new EntityServiceException(LogUtil.message("""
                                Session expired, please refresh your browser.

                                Application instance not found for: {}""",
                        applicationInstanceUuid)));
        if (!securityContext.getUserId().equals(applicationInstance.getUserId())) {
            throw new EntityServiceException("""
                    Session expired, please refresh your browser.

                    Attempt to use application instance for a different user.""");
        }
        return applicationInstance;
    }

    private DashboardSearchResponse processRequest(final QuerySearchRequest searchRequest) {
        LOGGER.trace(() -> "processRequest() " + searchRequest);
        DashboardSearchResponse result = null;

        QueryKey queryKey = searchRequest.getQueryKey();
        boolean newSearch = false;
        QuerySearchRequest updatedSearchRequest = searchRequest;
        final String query = searchRequest.getQuery();
        QueryContext queryContext = updatedSearchRequest.getQueryContext();

//        Search search = updatedSearchRequest.getSearch();
        ActiveQuery activeQuery;

        if (query != null) {
            try {
                // Add a param for `currentUser()`
                List<Param> params = queryContext.getParams();
                if (params != null) {
                    params = new ArrayList<>(params);
                } else {
                    params = new ArrayList<>();
                }
                params.add(new Param("currentUser()", securityContext.getUserId()));
                queryContext = queryContext.copy().params(params).build();

                Query sampleQuery = Query.builder().params(params).timeRange(queryContext.getTimeRange()).build();
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

                final DocRef dataSourceRef = mappedRequest.getQuery().getDataSource();
                synchronized (QueryServiceImpl.class) {
                    final ActiveQueries activeQueries = getActiveQueries(searchRequest);

                    if (queryKey != null) {
                        final Optional<ActiveQuery> optionalActiveQuery = activeQueries.getActiveQuery(queryKey);
                        final String message = "No active search found for key = " + queryKey;
                        activeQuery = optionalActiveQuery.orElseThrow(() ->
                                new RuntimeException(message));
                        // Check user identity.
                        if (!activeQuery.getUserId().equals(securityContext.getUserId())) {
                            throw new RuntimeException("Query belongs to different user");
                        }

                    } else {
                        // If the query doesn't have a key then this is new.
                        LOGGER.debug(() -> "New query");
                        newSearch = true;


                        if (dataSourceRef == null || dataSourceRef.getUuid() == null) {
                            throw new RuntimeException("No search data source has been specified");
                        }

                        // Get the data source provider for this query.
                        final DataSourceProvider dataSourceProvider = dataSourceProviderRegistry
                                .getDataSourceProvider(dataSourceRef)
                                .orElseThrow(() ->
                                        new RuntimeException(
                                                "No search provider found for '" +
                                                        dataSourceRef.getType() +
                                                        "' data source"));

                        // Add partition time constraints to the query.
                        final DateField partitionTimeField =
                                dataSourceProvider.getDataSource(dataSourceRef).getTimeField();
                        mappedRequest = addTimeRangeExpression(partitionTimeField, mappedRequest);

                        // Create a brand new query key and give it to the request.
                        queryKey = new QueryKey(UUID.randomUUID().toString());
                        mappedRequest = mappedRequest.copy().key(queryKey).build();

                        // Store the new active query for this query.
                        activeQuery = new ActiveQuery(
                                mappedRequest.getKey(),
                                dataSourceRef,
                                dataSourceProvider,
                                securityContext.getUserId());
                        activeQueries.addActiveQuery(queryKey, activeQuery);

                        // Add this search to the history so the user can get back to this
                        // search again.
                        storeSearchHistory(searchRequest);
                    }
                }

                // Perform the search or update results.
                SearchResponse searchResponse = activeQuery.search(mappedRequest);
                result = new SearchResponseMapper().mapResponse(searchResponse);

                if (newSearch) {
                    // Log this search request for the current user.
                    searchEventLog.search(
                            dataSourceRef,
                            mappedRequest.getQuery().getExpression(),
                            searchRequest.getQueryContext().getQueryInfo());
                }

            } catch (final RuntimeException e) {
                LOGGER.debug(() -> "Error processing search " + searchRequest, e);

                if (newSearch) {
                    // FIXME : FIX
//                    searchEventLog
//                    .search(search.getDataSourceRef(), search.getExpression(), search.getQueryInfo(), e);
                }

                result = new DashboardSearchResponse(
                        queryKey,
                        null,
                        Collections.singletonList(ExceptionStringUtil.getMessage(e)),
                        true,
                        null);
            }
        }

        return result;
    }

//    private SearchRequest resolveDataSource(SearchRequest request) {
//        String dataSourceName = null;
//        if (request != null &&
//                request.getQuery() != null &&
//                request.getQuery().getDataSource() != null) {
//            dataSourceName = request.getQuery().getDataSource().getName();
//        }
//        if (dataSourceName == null) {
//            throw new RuntimeException("Null data source name");
//        }
//
//        final List<DocRef> docRefs = docRefInfoService.findByName(
//                null,
//                dataSourceName,
//                false);
//        if (docRefs == null || docRefs.size() == 0) {
//            throw new RuntimeException("Data source \"" + dataSourceName + "\" not found");
//        }
//
//        // TODO : Deal with duplicate names.
//
//        DocRef resolved = null;
//        for (final DocRef docRef : docRefs) {
//            final Optional<DataSourceProvider> optional =
//                    dataSourceProviderRegistry.getDataSourceProvider(docRef);
//            if (optional.isPresent()) {
//                resolved = docRef;
//                break;
//            }
//        }
//
//        if (resolved == null) {
//            throw new RuntimeException("Unable to find data source \"" + dataSourceName + "\"");
//        }
//
//        return request.copy().query(request.getQuery().copy().dataSource(resolved).build()).build();
//    }

    private SearchRequest addTimeRangeExpression(final DateField partitionTimeField,
                                                 final SearchRequest searchRequest) {
        SearchRequest result = searchRequest;

        // Add the time range to the expression.
        if (partitionTimeField != null) {
            final TimeRange timeRange = result.getQuery().getTimeRange();
            if (timeRange != null && (timeRange.getFrom() != null || timeRange.getTo() != null)) {
                ExpressionOperator.Builder and = ExpressionOperator.builder().op(Op.AND);
                if (timeRange.getFrom() != null) {
                    and.addTerm(
                            partitionTimeField,
                            Condition.GREATER_THAN_OR_EQUAL_TO,
                            timeRange.getFrom());
                }
                if (timeRange.getTo() != null) {
                    and.addTerm(
                            partitionTimeField,
                            Condition.LESS_THAN,
                            timeRange.getTo());
                }
                Query query = result.getQuery();
                and.addOperator(query.getExpression());
                query = query.copy().expression(and.build()).build();
                result = result.copy().query(query).build();
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

//    @Override
//    public List<FunctionSignature> fetchFunctions() {
//        return functionServiceProvider.get()
//                .getSignatures();
//    }
}
