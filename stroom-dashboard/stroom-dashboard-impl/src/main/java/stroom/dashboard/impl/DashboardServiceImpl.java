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

package stroom.dashboard.impl;

import stroom.dashboard.expression.v1.Expression;
import stroom.dashboard.expression.v1.ExpressionParser;
import stroom.dashboard.expression.v1.FieldIndex;
import stroom.dashboard.expression.v1.ParamFactory;
import stroom.dashboard.impl.datasource.DataSourceProviderRegistry;
import stroom.dashboard.impl.download.DelimitedTarget;
import stroom.dashboard.impl.download.ExcelTarget;
import stroom.dashboard.impl.download.SearchResultWriter;
import stroom.dashboard.impl.logging.SearchEventLog;
import stroom.dashboard.shared.ComponentResultRequest;
import stroom.dashboard.shared.DashboardDoc;
import stroom.dashboard.shared.DashboardSearchRequest;
import stroom.dashboard.shared.DashboardSearchResponse;
import stroom.dashboard.shared.DestroySearchRequest;
import stroom.dashboard.shared.DownloadSearchResultsRequest;
import stroom.dashboard.shared.FunctionSignature;
import stroom.dashboard.shared.Search;
import stroom.dashboard.shared.StoredQuery;
import stroom.dashboard.shared.TableResultRequest;
import stroom.dashboard.shared.ValidateExpressionResult;
import stroom.dashboard.shared.VisResultRequest;
import stroom.datasource.api.v2.DataSourceProvider;
import stroom.docref.DocRef;
import stroom.docref.DocRefInfo;
import stroom.docstore.api.DocumentResourceHelper;
import stroom.event.logging.rs.api.AutoLogged;
import stroom.query.api.v2.Field;
import stroom.query.api.v2.Param;
import stroom.query.api.v2.Query;
import stroom.query.api.v2.QueryKey;
import stroom.query.api.v2.ResultRequest.Fetch;
import stroom.query.api.v2.SearchRequest;
import stroom.query.api.v2.SearchResponse;
import stroom.query.api.v2.TableResult;
import stroom.resource.api.ResourceStore;
import stroom.security.api.SecurityContext;
import stroom.security.shared.PermissionNames;
import stroom.storedquery.api.StoredQueryService;
import stroom.task.api.ExecutorProvider;
import stroom.task.api.TaskContextFactory;
import stroom.task.api.TerminateHandlerFactory;
import stroom.util.EntityServiceExceptionUtil;
import stroom.util.NullSafe;
import stroom.util.json.JsonUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.servlet.HttpServletRequestHolder;
import stroom.util.shared.EntityServiceException;
import stroom.util.shared.ResourceGeneration;
import stroom.util.shared.ResourceKey;
import stroom.util.string.ExceptionStringUtil;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.ParseException;
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
class DashboardServiceImpl implements DashboardService {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(DashboardServiceImpl.class);

    private static final Pattern NON_BASIC_CHARS = Pattern.compile("[^A-Za-z0-9-_ ]");
    private static final Pattern MULTIPLE_SPACE = Pattern.compile(" +");

    private final DashboardStore dashboardStore;
    private final StoredQueryService queryService;
    private final DocumentResourceHelper documentResourceHelper;
    private final SearchRequestMapper searchRequestMapper;
    private final ResourceStore resourceStore;
    private final SearchEventLog searchEventLog;
    private final ApplicationInstanceManager applicationInstanceManager;
    private final DataSourceProviderRegistry searchDataSourceProviderRegistry;
    private final SecurityContext securityContext;
    private final HttpServletRequestHolder httpServletRequestHolder;
    private final ExecutorProvider executorProvider;
    private final TaskContextFactory taskContextFactory;
    private final Provider<FunctionService> functionServiceProvider;

    @Inject
    DashboardServiceImpl(final DashboardStore dashboardStore,
                         final StoredQueryService queryService,
                         final DocumentResourceHelper documentResourceHelper,
                         final SearchRequestMapper searchRequestMapper,
                         final ResourceStore resourceStore,
                         final SearchEventLog searchEventLog,
                         final ApplicationInstanceManager applicationInstanceManager,
                         final DataSourceProviderRegistry searchDataSourceProviderRegistry,
                         final SecurityContext securityContext,
                         final HttpServletRequestHolder httpServletRequestHolder,
                         final ExecutorProvider executorProvider,
                         final TaskContextFactory taskContextFactory,
                         final Provider<FunctionService> functionServiceProvider) {
        this.dashboardStore = dashboardStore;
        this.queryService = queryService;
        this.documentResourceHelper = documentResourceHelper;
        this.searchRequestMapper = searchRequestMapper;
        this.resourceStore = resourceStore;
        this.searchEventLog = searchEventLog;
        this.applicationInstanceManager = applicationInstanceManager;
        this.searchDataSourceProviderRegistry = searchDataSourceProviderRegistry;
        this.securityContext = securityContext;
        this.httpServletRequestHolder = httpServletRequestHolder;
        this.executorProvider = executorProvider;
        this.taskContextFactory = taskContextFactory;
        this.functionServiceProvider = functionServiceProvider;
    }

    @Override
    public DashboardDoc read(final DocRef docRef) {
        return documentResourceHelper.read(dashboardStore, docRef);
    }

    @Override
    public DashboardDoc update(final DashboardDoc doc) {
        return documentResourceHelper.update(dashboardStore, doc);
    }

    @Override
    public ValidateExpressionResult validateExpression(final String expressionString) {
        try {
            final FieldIndex fieldIndex = new FieldIndex();
            final ExpressionParser expressionParser = new ExpressionParser(new ParamFactory());
            final Expression expression = expressionParser.parse(fieldIndex, expressionString);
            String correctedExpression = "";
            if (expression != null) {
                correctedExpression = expression.toString();
            }
            return new ValidateExpressionResult(true, correctedExpression);
        } catch (final ParseException e) {
            return new ValidateExpressionResult(false, e.getMessage());
        }
    }

    @Override
    public ResourceGeneration downloadQuery(final DashboardSearchRequest request) {
        return securityContext.secureResult(() -> {
            try {
                if (request == null) {
                    throw new EntityServiceException("Query is empty");
                }

                final DashboardSearchRequest.Builder builder = request.copy();
                final List<ComponentResultRequest> componentResultRequests = new ArrayList<>();

                // API users will typically want all data so ensure Fetch.ALL is set regardless of what it was before
                if (request.getComponentResultRequests() != null) {
                    request.getComponentResultRequests()
                            .forEach(componentResultRequest -> {

                                ComponentResultRequest newRequest = null;
                                if (componentResultRequest instanceof TableResultRequest) {
                                    final TableResultRequest tableResultRequest =
                                            (TableResultRequest) componentResultRequest;
                                    // Remove special fields.
                                    tableResultRequest.getTableSettings().getFields().removeIf(Field::isSpecial);
                                    newRequest = tableResultRequest
                                            .copy()
                                            .fetch(Fetch.ALL)
                                            .build();
                                } else if (componentResultRequest instanceof VisResultRequest) {
                                    final VisResultRequest visResultRequest = (VisResultRequest) componentResultRequest;
                                    newRequest = visResultRequest
                                            .copy()
                                            .fetch(Fetch.ALL)
                                            .build();
                                }

                                componentResultRequests.add(newRequest);
                            });
                }

                builder.componentResultRequests(componentResultRequests);

                // Convert our internal model to the model used by the api
                SearchRequest apiSearchRequest = searchRequestMapper.mapRequest(builder.build());

                if (apiSearchRequest == null) {
                    throw new EntityServiceException("Query could not be mapped to a SearchRequest");
                }

                // Generate the export file
                String fileName = getQueryFileName(request);

                final ResourceKey resourceKey = resourceStore.createTempFile(fileName);
                final Path outputFile = resourceStore.getTempFile(resourceKey);

                JsonUtil.writeValue(outputFile, apiSearchRequest);

                return new ResourceGeneration(resourceKey, new ArrayList<>());
            } catch (final RuntimeException e) {
                throw EntityServiceExceptionUtil.create(e);
            }
        });
    }


    @Override
    public ResourceGeneration downloadSearchResults(final DownloadSearchResultsRequest request) {
        return securityContext.secureResult(PermissionNames.DOWNLOAD_SEARCH_RESULTS_PERMISSION, () -> {
            ResourceKey resourceKey;

            final DashboardSearchRequest searchRequest = request.getSearchRequest();
            final QueryKey queryKey = searchRequest.getQueryKey();
            Integer rowCount = null;

            try {
                if (queryKey == null) {
                    throw new EntityServiceException("No query is active");
                }
                final ActiveQueries activeQueries = getActiveQueries(searchRequest);
                final Optional<ActiveQuery> optionalActiveQuery = activeQueries.getActiveQuery(queryKey);
                final ActiveQuery activeQuery = optionalActiveQuery
                        .orElseThrow(() -> new EntityServiceException("The requested search data is not available"));
                SearchRequest mappedRequest = searchRequestMapper.mapRequest(searchRequest);
                SearchResponse searchResponse = activeQuery.search(mappedRequest);

                if (searchResponse == null || searchResponse.getResults() == null) {
                    throw new EntityServiceException("No results can be found");
                }

                final String componentId = request.getComponentId();
                final List<TableResult> tableResults = searchResponse.getResults()
                        .stream()
                        .filter(result -> result instanceof TableResult)
                        .filter(result -> request.isDownloadAllTables() || result.getComponentId().equals(componentId))
                        .map(result -> (TableResult) result)
                        .toList();
                rowCount = tableResults
                        .stream()
                        .map(TableResult::getTotalResults)
                        .reduce(0, Integer::sum);

                if (tableResults.isEmpty()) {
                    throw new EntityServiceException("No result for component can be found");
                }

                // Import file.
                final String fileName = getResultsFilename(request);
                resourceKey = resourceStore.createTempFile(fileName);
                final Path file = resourceStore.getTempFile(resourceKey);

                download(request, searchRequest, tableResults, file);

                searchEventLog.downloadResults(request, rowCount);
            } catch (final RuntimeException e) {
                searchEventLog.downloadResults(request, rowCount, e);
                throw EntityServiceExceptionUtil.create(e);
            }

            return new ResourceGeneration(resourceKey, new ArrayList<>());
        });
    }

    private String getResultsFilename(final DownloadSearchResultsRequest request) {
        final DashboardSearchRequest searchRequest = request.getSearchRequest();
        final String basename = request.getComponentId() + "__" + searchRequest.getQueryKey().getUuid();
        return getFileName(basename, request.getFileType().getExtension());
    }

    private String getQueryFileName(final DashboardSearchRequest request) {
        final DocRefInfo dashDocRefInfo = dashboardStore.info(request.getDashboardUuid());
        final String dashboardName = NullSafe.getOrElse(
                dashDocRefInfo,
                DocRefInfo::getDocRef,
                DocRef::getName,
                request.getDashboardUuid());
        final String basename = dashboardName + "__" + request.getComponentId();
        return getFileName(basename, "json");
    }

    private String getFileName(final String baseName,
                               final String extension) {
        String fileName = baseName;
        fileName = NON_BASIC_CHARS.matcher(fileName).replaceAll("");
        fileName = MULTIPLE_SPACE.matcher(fileName).replaceAll(" ");
        fileName = fileName.replace(" ", "_");
        fileName = fileName + "." + extension;
        return fileName;
    }

    private void download(final DownloadSearchResultsRequest request,
                          final DashboardSearchRequest searchRequest,
                          final List<TableResult> tableResults,
                          final Path file) {
        try (final OutputStream outputStream = new BufferedOutputStream(Files.newOutputStream(file))) {
            SearchResultWriter.Target target = null;

            // Write delimited file.
            switch (request.getFileType()) {
                case CSV:
                    target = new DelimitedTarget(outputStream, ",");
                    break;
                case TSV:
                    target = new DelimitedTarget(outputStream, "\t");
                    break;
                case EXCEL:
                    target = new ExcelTarget(outputStream, searchRequest.getDateTimeSettings());
                    break;
            }

            final SampleGenerator sampleGenerator = new SampleGenerator(request.isSample(), request.getPercent());
            final SearchResultWriter searchResultWriter = new SearchResultWriter(
                    searchRequest,
                    tableResults,
                    sampleGenerator);
            searchResultWriter.write(target);

        } catch (final IOException e) {
            throw EntityServiceExceptionUtil.create(e);
        }
    }

    @Override
    public DashboardSearchResponse search(final DashboardSearchRequest request) {
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
    public Boolean destroy(final DestroySearchRequest request) {
        return getApplicationInstance(request.getApplicationInstanceUuid())
                .getActiveQueries()
                .destroyActiveQuery(request.getQueryKey())
                .isPresent();
    }

    private ActiveQueries getActiveQueries(final DashboardSearchRequest request) {
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

    private DashboardSearchResponse processRequest(final DashboardSearchRequest searchRequest) {
        LOGGER.trace(() -> "processRequest() " + searchRequest);
        DashboardSearchResponse result = null;

        QueryKey queryKey = searchRequest.getQueryKey();
        boolean newSearch = false;
        DashboardSearchRequest updatedSearchRequest = searchRequest;
        Search search = updatedSearchRequest.getSearch();
        ActiveQuery activeQuery;

        if (search != null) {
            try {
                // Add a param for `currentUser()`
                List<Param> params = search.getParams();
                if (params != null) {
                    params = new ArrayList<>(params);
                } else {
                    params = new ArrayList<>();
                }
                params.add(new Param("currentUser()", securityContext.getUserId()));
                search = search.copy().params(params).build();
                updatedSearchRequest = updatedSearchRequest.copy().search(search).build();
                SearchRequest mappedRequest = searchRequestMapper.mapRequest(updatedSearchRequest);

                synchronized (DashboardServiceImpl.class) {
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

                        final DocRef dataSourceRef = search.getDataSourceRef();
                        if (dataSourceRef == null || dataSourceRef.getUuid() == null) {
                            throw new RuntimeException("No search data source has been specified");
                        }

                        // Get the data source provider for this query.
                        final DataSourceProvider dataSourceProvider = searchDataSourceProviderRegistry
                                .getDataSourceProvider(dataSourceRef)
                                .orElseThrow(() ->
                                        new RuntimeException(
                                                "No search provider found for '" +
                                                        dataSourceRef.getType() +
                                                        "' data source"));

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
                    searchEventLog.search(search.getDataSourceRef(),
                            search.getExpression(),
                            search.getQueryInfo(),
                            search.getParams());
                }

            } catch (final RuntimeException e) {
                final Search finalSearch = search;
                LOGGER.debug(() -> "Error processing search " + finalSearch, e);

                if (newSearch) {
                    searchEventLog.search(search.getDataSourceRef(),
                            search.getExpression(),
                            search.getQueryInfo(),
                            search.getParams(),
                            e);
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

    private void storeSearchHistory(final DashboardSearchRequest request) {
        // We only want to record search history for user initiated searches.
        if (request.isStoreHistory()) {
            try {
                // Add this search to the history so the user can get back to
                // this search again.
                final Search search = request.getSearch();
                final List<Param> params = search.getParams();
                final Query query = new Query(search.getDataSourceRef(), search.getExpression(), params);

                final StoredQuery storedQuery = new StoredQuery();
                storedQuery.setName("History");
                storedQuery.setDashboardUuid(request.getDashboardUuid());
                storedQuery.setComponentId(request.getComponentId());
                storedQuery.setQuery(query);
                queryService.create(storedQuery);

            } catch (final RuntimeException e) {
                LOGGER.error(e::getMessage, e);
            }
        }
    }

    @Override
    public List<String> fetchTimeZones() {
        final List<String> ids = new ArrayList<>(ZoneId.getAvailableZoneIds());
        ids.sort(Comparator.naturalOrder());
        return ids;
    }

    @Override
    public List<FunctionSignature> fetchFunctions() {
        return functionServiceProvider.get()
                .getSignatures();
    }
}
