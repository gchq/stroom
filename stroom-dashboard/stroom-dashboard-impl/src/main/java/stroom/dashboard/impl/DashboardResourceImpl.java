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
import stroom.dashboard.impl.datasource.DataSourceProvider;
import stroom.dashboard.impl.datasource.DataSourceProviderRegistry;
import stroom.dashboard.impl.download.DelimitedTarget;
import stroom.dashboard.impl.download.ExcelTarget;
import stroom.dashboard.impl.download.SearchResultWriter;
import stroom.dashboard.impl.logging.SearchEventLog;
import stroom.dashboard.shared.ComponentResultRequest;
import stroom.dashboard.shared.DashboardDoc;
import stroom.dashboard.shared.DashboardQueryKey;
import stroom.dashboard.shared.DashboardResource;
import stroom.dashboard.shared.DownloadQueryRequest;
import stroom.dashboard.shared.DownloadSearchResultFileType;
import stroom.dashboard.shared.DownloadSearchResultsRequest;
import stroom.dashboard.shared.FunctionSignature;
import stroom.dashboard.shared.Search;
import stroom.dashboard.shared.SearchBusPollRequest;
import stroom.dashboard.shared.DashboardSearchRequest;
import stroom.dashboard.shared.DashboardSearchResponse;
import stroom.dashboard.shared.StoredQuery;
import stroom.dashboard.shared.TableResultRequest;
import stroom.dashboard.shared.ValidateExpressionResult;
import stroom.dashboard.shared.VisResultRequest;
import stroom.docref.DocRef;
import stroom.docstore.api.DocumentResourceHelper;
import stroom.query.api.v2.Field;
import stroom.query.api.v2.Param;
import stroom.query.api.v2.Query;
import stroom.query.api.v2.Result;
import stroom.query.api.v2.ResultRequest.Fetch;
import stroom.query.api.v2.Row;
import stroom.query.api.v2.SearchRequest;
import stroom.query.api.v2.SearchResponse;
import stroom.query.api.v2.TableResult;
import stroom.resource.api.ResourceStore;
import stroom.security.api.SecurityContext;
import stroom.security.shared.PermissionNames;
import stroom.storedquery.api.StoredQueryService;
import stroom.task.api.ExecutorProvider;
import stroom.task.api.TaskContextFactory;
import stroom.util.EntityServiceExceptionUtil;
import stroom.util.json.JsonUtil;
import stroom.util.servlet.HttpServletRequestHolder;
import stroom.util.shared.EntityServiceException;
import stroom.util.shared.ResourceGeneration;
import stroom.util.shared.ResourceKey;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.servlet.http.HttpServletRequest;
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
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

class DashboardResourceImpl implements DashboardResource {
    private static final Logger LOGGER = LoggerFactory.getLogger(DashboardResourceImpl.class);

    private static final Pattern NON_BASIC_CHARS = Pattern.compile("[^A-Za-z0-9-_ ]");
    private static final Pattern MULTIPLE_SPACE = Pattern.compile(" +");

    private final DashboardStore dashboardStore;
    private final StoredQueryService queryService;
    private final DocumentResourceHelper documentResourceHelper;
    private final SearchRequestMapper searchRequestMapper;
    private final ResourceStore resourceStore;
    private final SearchEventLog searchEventLog;
    private final ActiveQueriesManager activeQueriesManager;
    private final DataSourceProviderRegistry searchDataSourceProviderRegistry;
    private final SecurityContext securityContext;
    private final HttpServletRequestHolder httpServletRequestHolder;
    private final ExecutorProvider executorProvider;
    private final TaskContextFactory taskContextFactory;
    private final Provider<FunctionService> functionServiceProvider;

    @Inject
    DashboardResourceImpl(final DashboardStore dashboardStore,
                          final StoredQueryService queryService,
                          final DocumentResourceHelper documentResourceHelper,
                          final SearchRequestMapper searchRequestMapper,
                          final ResourceStore resourceStore,
                          final SearchEventLog searchEventLog,
                          final ActiveQueriesManager activeQueriesManager,
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
        this.activeQueriesManager = activeQueriesManager;
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
    public ResourceGeneration downloadQuery(final DownloadQueryRequest request) {
        return securityContext.secureResult(() -> {
            try {
                if (request.getSearchRequest() == null) {
                    throw new EntityServiceException("Query is empty");
                }

                final DashboardSearchRequest searchRequest = request.getSearchRequest();
                final DashboardSearchRequest.Builder builder = searchRequest.copy();
                final List<ComponentResultRequest> componentResultRequests = new ArrayList<>();

                // API users will typically want all data so ensure Fetch.ALL is set regardless of what it was before
                if (searchRequest.getComponentResultRequests() != null) {
                    searchRequest.getComponentResultRequests()
                            .forEach(componentResultRequest -> {

                                ComponentResultRequest newRequest = null;
                                if (componentResultRequest instanceof TableResultRequest) {
                                    final TableResultRequest tableResultRequest = (TableResultRequest) componentResultRequest;
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
                SearchRequest apiSearchRequest = searchRequestMapper.mapRequest(
                        request.getDashboardQueryKey(),
                        builder.build());

                if (apiSearchRequest == null) {
                    throw new EntityServiceException("Query could not be mapped to a SearchRequest");
                }

                // Generate the export file
                String fileName = request.getDashboardQueryKey().toString();
                fileName = NON_BASIC_CHARS.matcher(fileName).replaceAll("");
                fileName = MULTIPLE_SPACE.matcher(fileName).replaceAll(" ");
                fileName = fileName + ".json";

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
            final DashboardQueryKey queryKey = searchRequest.getDashboardQueryKey();
            final Search search = searchRequest.getSearch();

            try {
                final ActiveQueries activeQueries = activeQueriesManager.get(securityContext.getUserIdentity(), request.getApplicationInstanceId());

                // Make sure we have active queries for all current UI queries.
                // Note: This also ensures that the active query cache is kept alive
                // for all open UI components.
                final ActiveQuery activeQuery = activeQueries.getExistingQuery(queryKey);
                if (activeQuery == null) {
                    throw new EntityServiceException("The requested search data is not available");
                }

                // Perform the search or update results.
                final DocRef dataSourceRef = search.getDataSourceRef();
                if (dataSourceRef == null || dataSourceRef.getUuid() == null) {
                    throw new RuntimeException("No search data source has been specified");
                }

                // Get the data source provider for this query.
                final DataSourceProvider dataSourceProvider = searchDataSourceProviderRegistry
                        .getDataSourceProvider(dataSourceRef)
                        .orElseThrow(() ->
                                new RuntimeException("No search provider found for '" + dataSourceRef.getType() + "' data source"));

                SearchRequest mappedRequest = searchRequestMapper.mapRequest(queryKey, searchRequest);
                SearchResponse searchResponse = dataSourceProvider.search(mappedRequest);

                if (searchResponse == null || searchResponse.getResults() == null) {
                    throw new EntityServiceException("No results can be found");
                }

                Result result = null;
                for (final Result res : searchResponse.getResults()) {
                    if (res.getComponentId().equals(request.getComponentId())) {
                        result = res;
                        break;
                    }
                }

                if (result == null) {
                    throw new EntityServiceException("No result for component can be found");
                }

                if (!(result instanceof TableResult)) {
                    throw new EntityServiceException("Result is not a table");
                }

                final TableResult tableResult = (TableResult) result;

                // Import file.
                String fileName = queryKey.toString();
                fileName = NON_BASIC_CHARS.matcher(fileName).replaceAll("");
                fileName = MULTIPLE_SPACE.matcher(fileName).replaceAll(" ");
                fileName = fileName + "." + request.getFileType().getExtension();

                resourceKey = resourceStore.createTempFile(fileName);
                final Path file = resourceStore.getTempFile(resourceKey);

                final Optional<ComponentResultRequest> optional = searchRequest.getComponentResultRequests()
                        .stream()
                        .filter(r -> r.getComponentId().equals(request.getComponentId()))
                        .findFirst();
                if (optional.isEmpty()) {
                    throw new EntityServiceException("No component result request found");
                }

                if (!(optional.get() instanceof TableResultRequest)) {
                    throw new EntityServiceException("Component result request is not a table");
                }

                final TableResultRequest tableResultRequest = (TableResultRequest) optional.get();
                final List<Field> fields = tableResultRequest.getTableSettings().getFields();
                final List<Row> rows = tableResult.getRows();

                download(fields, rows, file, request.getFileType(), request.isSample(), request.getPercent());

                searchEventLog.downloadResults(search.getDataSourceRef(), search.getExpression(), search.getQueryInfo());
            } catch (final RuntimeException e) {
                searchEventLog.downloadResults(search.getDataSourceRef(), search.getExpression(), search.getQueryInfo(), e);
                throw EntityServiceExceptionUtil.create(e);
            }

            return new ResourceGeneration(resourceKey, new ArrayList<>());
        });
    }

    private void download(final List<Field> fields, final List<Row> rows, final Path file,
                          final DownloadSearchResultFileType fileType, final boolean sample, final int percent) {
        try (final OutputStream outputStream = new BufferedOutputStream(Files.newOutputStream(file))) {
            SearchResultWriter.Target target = null;

            // Write delimited file.
            switch (fileType) {
                case CSV:
                    target = new DelimitedTarget(outputStream, ",");
                    break;
                case TSV:
                    target = new DelimitedTarget(outputStream, "\t");
                    break;
                case EXCEL:
                    target = new ExcelTarget(outputStream);
                    break;
            }

            final SampleGenerator sampleGenerator = new SampleGenerator(sample, percent);
            final SearchResultWriter searchResultWriter = new SearchResultWriter(fields, rows, sampleGenerator);
            searchResultWriter.write(target);

        } catch (final IOException e) {
            throw EntityServiceExceptionUtil.create(e);
        }
    }

    @Override
    public Set<DashboardSearchResponse> poll(final SearchBusPollRequest request) {
        return securityContext.secureResult(() -> {
            // Elevate the users permissions for the duration of this task so they can read the index if they have 'use' permission.
            return securityContext.useAsReadResult(() -> {
                if (LOGGER.isDebugEnabled()) {
                    final StringBuilder sb = new StringBuilder(
                            "Only the following search queries should be active for session '");
                    sb.append(activeQueriesManager.createKey(securityContext.getUserIdentity(), request.getApplicationInstanceId()));
                    sb.append("'\n");
                    for (final DashboardSearchRequest searchRequest : request.getSearchRequests()) {
                        sb.append("\t");
                        sb.append(searchRequest.getDashboardQueryKey().toString());
                    }
                    LOGGER.debug(sb.toString());
                }

                final ActiveQueries activeQueries = activeQueriesManager.get(securityContext.getUserIdentity(), request.getApplicationInstanceId());
                final Set<DashboardSearchResponse> searchResults = Collections.newSetFromMap(new ConcurrentHashMap<>());

//            // Fix query keys so they have session and user info.
//            for (final Entry<DashboardQueryKey, SearchRequest> entry : request.getSearchActionMap().entrySet()) {
//                final QueryKey queryKey = entry.getValues().getQueryKey();
//                queryKey.setSessionId(request.getSessionId());
//                queryKey.setUserId(request.getUserId());
//            }

                // Kill off any queries that are no longer required by the UI.
                final Set<DashboardQueryKey> keys = request.getSearchRequests().stream()
                        .map(DashboardSearchRequest::getDashboardQueryKey).collect(Collectors.toSet());
                activeQueries.destroyUnusedQueries(keys);

                // Get query results for every active query.
                final HttpServletRequest httpServletRequest = httpServletRequestHolder.get();
                final Executor executor = executorProvider.get();
                final CountDownLatch countDownLatch = new CountDownLatch(request.getSearchRequests().size());
                for (final DashboardSearchRequest searchRequest : request.getSearchRequests()) {
                    Runnable runnable = taskContextFactory.context("Search", taskContext -> {
                        try {
                            httpServletRequestHolder.set(httpServletRequest);
                            final DashboardQueryKey queryKey = searchRequest.getDashboardQueryKey();
                            if (searchRequest.getSearch() != null) {
                                final DashboardSearchResponse searchResponse = processRequest(activeQueries, queryKey, searchRequest);
                                if (searchResponse != null) {
                                    searchResults.add(searchResponse);
                                }
                            }
                        } finally {
                            countDownLatch.countDown();
                            httpServletRequestHolder.set(null);
                        }
                    });
                    executor.execute(runnable);
                }

                // Wait for all results to come back.
                try {
                    countDownLatch.await();
                } catch (final InterruptedException e) {
                    // Keep interrupting.
                    Thread.currentThread().interrupt();
                }

                return searchResults;
            });
        });
    }

    private DashboardSearchResponse processRequest(final ActiveQueries activeQueries,
                                                   final DashboardQueryKey queryKey,
                                                   final DashboardSearchRequest searchRequest) {
        DashboardSearchResponse result;

        boolean newSearch = false;
        DashboardSearchRequest updatedSearchRequest = searchRequest;
        Search search = updatedSearchRequest.getSearch();

        try {
            synchronized (DashboardResourceImpl.class) {
                // Make sure we have active queries for all current UI queries.
                // Note: This also ensures that the active query cache is kept alive
                // for all open UI components.
                final ActiveQuery activeQuery = activeQueries.getExistingQuery(queryKey);

                // If the query doesn't have an active query for this query key then
                // this is new.
                if (activeQuery == null) {
                    newSearch = true;

                    // Store the new active query for this query.
                    activeQueries.addNewQuery(queryKey, search.getDataSourceRef());

                    // Add this search to the history so the user can get back to this
                    // search again.
                    storeSearchHistory(queryKey, search);
                }
            }

            // Perform the search or update results.
            final DocRef dataSourceRef = search.getDataSourceRef();
            if (dataSourceRef == null || dataSourceRef.getUuid() == null) {
                throw new RuntimeException("No search data source has been specified");
            }

            // Get the data source provider for this query.
            final DataSourceProvider dataSourceProvider = searchDataSourceProviderRegistry
                    .getDataSourceProvider(dataSourceRef)
                    .orElseThrow(() ->
                            new RuntimeException(
                                    "No search provider found for '" + dataSourceRef.getType() + "' data source"));

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

            SearchRequest mappedRequest = searchRequestMapper.mapRequest(queryKey, updatedSearchRequest);
            SearchResponse searchResponse = dataSourceProvider.search(mappedRequest);
            result = new SearchResponseMapper().mapResponse(queryKey, searchResponse);

            if (newSearch) {
                // Log this search request for the current user.
                searchEventLog.search(search.getDataSourceRef(), search.getExpression(), search.getQueryInfo());
            }

        } catch (final RuntimeException e) {
            LOGGER.debug("Error processing search {}", search, e);

            if (newSearch) {
                searchEventLog.search(search.getDataSourceRef(), search.getExpression(), search.getQueryInfo(), e);
            }

            final String errors = e.getMessage() != null
                    ? e.getMessage()
                    : e.getClass().getName();

            result = new DashboardSearchResponse(queryKey, null, errors, true, null);
        }

        return result;
    }

    private void storeSearchHistory(final DashboardQueryKey queryKey, final Search search) {
        // We only want to record search history for user initiated searches.
        if (search.isStoreHistory()) {
            try {
                // Add this search to the history so the user can get back to
                // this search again.
                final List<Param> params = search.getParams();
                final Query query = new Query(search.getDataSourceRef(), search.getExpression(), params);

                final StoredQuery storedQuery = new StoredQuery();
                storedQuery.setName("History");
                storedQuery.setDashboardUuid(queryKey.getDashboardUuid());
                storedQuery.setComponentId(queryKey.getComponentId());
                storedQuery.setQuery(query);
                queryService.create(storedQuery);

            } catch (final RuntimeException e) {
                LOGGER.error(e.getMessage(), e);
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