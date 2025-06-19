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

import stroom.dashboard.impl.download.DelimitedTarget;
import stroom.dashboard.impl.download.ExcelTarget;
import stroom.dashboard.impl.download.SearchResultWriter;
import stroom.dashboard.impl.logging.SearchEventLog;
import stroom.dashboard.shared.ColumnValues;
import stroom.dashboard.shared.ColumnValuesRequest;
import stroom.dashboard.shared.ComponentResultRequest;
import stroom.dashboard.shared.DashboardDoc;
import stroom.dashboard.shared.DashboardSearchRequest;
import stroom.dashboard.shared.DashboardSearchResponse;
import stroom.dashboard.shared.DownloadSearchResultsRequest;
import stroom.dashboard.shared.Search;
import stroom.dashboard.shared.StoredQuery;
import stroom.dashboard.shared.TableResultRequest;
import stroom.dashboard.shared.ValidateExpressionResult;
import stroom.dashboard.shared.VisResultRequest;
import stroom.docref.DocRef;
import stroom.docref.DocRefInfo;
import stroom.docstore.api.DocumentResourceHelper;
import stroom.event.logging.rs.api.AutoLogged;
import stroom.node.api.NodeInfo;
import stroom.query.api.Column;
import stroom.query.api.OffsetRange;
import stroom.query.api.Query;
import stroom.query.api.QueryKey;
import stroom.query.api.QueryNodeResolver;
import stroom.query.api.ResultRequest;
import stroom.query.api.ResultRequest.Fetch;
import stroom.query.api.ResultRequest.ResultStyle;
import stroom.query.api.SearchRequest;
import stroom.query.api.SearchRequestSource;
import stroom.query.api.SearchResponse;
import stroom.query.api.TableResultBuilder;
import stroom.query.api.TimeFilter;
import stroom.query.common.v2.DataStore;
import stroom.query.common.v2.ExpressionPredicateFactory;
import stroom.query.common.v2.Key;
import stroom.query.common.v2.OpenGroupsImpl;
import stroom.query.common.v2.ResultCreator;
import stroom.query.common.v2.ResultStoreManager;
import stroom.query.common.v2.ResultStoreManager.RequestAndStore;
import stroom.query.common.v2.TableResultCreator;
import stroom.query.common.v2.ValPredicateFactory;
import stroom.query.common.v2.format.FormatterFactory;
import stroom.query.language.functions.Expression;
import stroom.query.language.functions.ExpressionContext;
import stroom.query.language.functions.ExpressionParser;
import stroom.query.language.functions.FieldIndex;
import stroom.query.language.functions.ParamFactory;
import stroom.query.language.functions.Val;
import stroom.resource.api.ResourceStore;
import stroom.security.api.SecurityContext;
import stroom.security.shared.AppPermission;
import stroom.storedquery.api.StoredQueryService;
import stroom.task.api.ExecutorProvider;
import stroom.task.api.TaskContextFactory;
import stroom.task.api.TerminateHandlerFactory;
import stroom.util.EntityServiceExceptionUtil;
import stroom.util.collections.TrimmedSortedList;
import stroom.util.json.JsonUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.servlet.HttpServletRequestHolder;
import stroom.util.shared.EntityServiceException;
import stroom.util.shared.NullSafe;
import stroom.util.shared.ResourceGeneration;
import stroom.util.shared.ResourceKey;
import stroom.util.shared.ResultPage;
import stroom.util.string.ExceptionStringUtil;

import jakarta.inject.Inject;
import jakarta.servlet.http.HttpServletRequest;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

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
    private final SecurityContext securityContext;
    private final HttpServletRequestHolder httpServletRequestHolder;
    private final ExecutorProvider executorProvider;
    private final TaskContextFactory taskContextFactory;
    private final ResultStoreManager searchResponseCreatorManager;
    private final NodeInfo nodeInfo;
    private final ExpressionPredicateFactory expressionPredicateFactory;
    private final ValPredicateFactory valPredicateFactory;
    private final QueryNodeResolver queryNodeResolver;

    @Inject
    DashboardServiceImpl(final DashboardStore dashboardStore,
                         final StoredQueryService queryService,
                         final DocumentResourceHelper documentResourceHelper,
                         final SearchRequestMapper searchRequestMapper,
                         final ResourceStore resourceStore,
                         final SearchEventLog searchEventLog,
                         final SecurityContext securityContext,
                         final HttpServletRequestHolder httpServletRequestHolder,
                         final ExecutorProvider executorProvider,
                         final TaskContextFactory taskContextFactory,
                         final ResultStoreManager searchResponseCreatorManager,
                         final NodeInfo nodeInfo,
                         final ExpressionPredicateFactory expressionPredicateFactory,
                         final ValPredicateFactory valPredicateFactory,
                         final QueryNodeResolver queryNodeResolver) {
        this.dashboardStore = dashboardStore;
        this.queryService = queryService;
        this.documentResourceHelper = documentResourceHelper;
        this.searchRequestMapper = searchRequestMapper;
        this.resourceStore = resourceStore;
        this.searchEventLog = searchEventLog;
        this.securityContext = securityContext;
        this.httpServletRequestHolder = httpServletRequestHolder;
        this.executorProvider = executorProvider;
        this.taskContextFactory = taskContextFactory;
        this.searchResponseCreatorManager = searchResponseCreatorManager;
        this.nodeInfo = nodeInfo;
        this.expressionPredicateFactory = expressionPredicateFactory;
        this.valPredicateFactory = valPredicateFactory;
        this.queryNodeResolver = queryNodeResolver;
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
            final ExpressionContext expressionContext = new ExpressionContext();
            final FieldIndex fieldIndex = new FieldIndex();
            final ExpressionParser expressionParser = new ExpressionParser(new ParamFactory(new HashMap<>()));
            final Expression expression = expressionParser.parse(expressionContext, fieldIndex, expressionString);
            String correctedExpression = "";
            if (expression != null) {
                correctedExpression = expression.toString();
            }
            return new ValidateExpressionResult(true, correctedExpression, false);
        } catch (final ParseException e) {
            return new ValidateExpressionResult(false, e.getMessage(), false);
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
                                if (componentResultRequest instanceof final TableResultRequest tableResultRequest) {
                                    // Remove special fields.
                                    tableResultRequest.getTableSettings().getColumns().removeIf(Column::isSpecial);
                                    newRequest = tableResultRequest
                                            .copy()
                                            .fetch(Fetch.ALL)
                                            .build();
                                } else if (componentResultRequest instanceof final VisResultRequest visResultRequest) {
                                    newRequest = visResultRequest
                                            .copy()
                                            .fetch(Fetch.ALL)
                                            .build();
                                }

                                componentResultRequests.add(newRequest);
                            });
                }

                builder.componentResultRequests(componentResultRequests);

                // API users don't want a fixed referenceTime for their queries. They want it null
                // so the backend sets it for them
                NullSafe.consume(request.getDateTimeSettings(), dateTimeSettings ->
                        builder.dateTimeSettings(dateTimeSettings.withoutReferenceTime()));

                // Convert our internal model to the model used by the api
                final SearchRequest apiSearchRequest = searchRequestMapper.mapRequest(builder.build());

                if (apiSearchRequest == null) {
                    throw new EntityServiceException("Query could not be mapped to a SearchRequest");
                }

                // Generate the export file
                final String fileName = getQueryFileName(request);

                final ResourceKey resourceKey = resourceStore.createTempFile(fileName);
                final Path tempFile = resourceStore.getTempFile(resourceKey);
                JsonUtil.writeValue(tempFile, apiSearchRequest);
                return new ResourceGeneration(resourceKey, new ArrayList<>());
            } catch (final RuntimeException e) {
                throw EntityServiceExceptionUtil.create(e);
            }
        });
    }

    @Override
    public ResourceGeneration downloadSearchResults(final DownloadSearchResultsRequest request) {
        return securityContext.secureResult(AppPermission.DOWNLOAD_SEARCH_RESULTS_PERMISSION, () -> {
            final DashboardSearchRequest searchRequest = request.getSearchRequest();
            final QueryKey queryKey = searchRequest.getQueryKey();
            final ResourceKey resourceKey;
            long totalRowCount = 0;

            try {
                if (queryKey == null) {
                    throw new EntityServiceException("No query is active");
                }

                final Map<String, TableResultRequest> tableRequestMap = request
                        .getSearchRequest()
                        .getComponentResultRequests()
                        .stream()
                        .filter(req -> req instanceof TableResultRequest)
                        .filter(req -> request.isDownloadAllTables() ||
                                       req.getComponentId().equals(request.getComponentId()))
                        .collect(Collectors.toMap(
                                ComponentResultRequest::getComponentId,
                                req -> (TableResultRequest) req));

                final SearchRequest mappedRequest = searchRequestMapper.mapRequest(searchRequest);
                final List<ResultRequest> resultRequests = mappedRequest
                        .getResultRequests()
                        .stream()
                        .filter(req -> ResultStyle.TABLE.equals(req.getResultStyle()))
                        .filter(req -> request.isDownloadAllTables() ||
                                       req.getComponentId().equals(request.getComponentId()))
                        .toList();

                if (resultRequests.isEmpty()) {
                    throw new EntityServiceException("No tables specified for download");
                }

                final RequestAndStore requestAndStore = searchResponseCreatorManager.getResultStore(mappedRequest);

                // Import file.
                final String fileName = getResultsFilename(request);
                resourceKey = resourceStore.createTempFile(fileName);
                final Path tempFile = resourceStore.getTempFile(resourceKey);

                final FormatterFactory formatterFactory =
                        new FormatterFactory(searchRequest.getDateTimeSettings());

                // Start target
                try (final OutputStream outputStream = new BufferedOutputStream(Files.newOutputStream(tempFile))) {
                    final SearchResultWriter.Target target = switch (request.getFileType()) {
                        case CSV -> new DelimitedTarget(outputStream, ",");
                        case TSV -> new DelimitedTarget(outputStream, "\t");
                        case EXCEL -> new ExcelTarget(outputStream, searchRequest.getDateTimeSettings());
                    };

                    // Write delimited file.
                    try {
                        target.start();

                        for (final ResultRequest resultRequest : resultRequests) {
                            final TableResultRequest tableResultRequest =
                                    tableRequestMap.get(resultRequest.getComponentId());
                            try {
                                target.startTable(tableResultRequest.getTableName());

                                final SampleGenerator sampleGenerator =
                                        new SampleGenerator(request.isSample(), request.getPercent());
                                final SearchResultWriter searchResultWriter = new SearchResultWriter(
                                        sampleGenerator,
                                        target);
                                final TableResultCreator tableResultCreator =
                                        new TableResultCreator(formatterFactory, expressionPredicateFactory) {
                                            @Override
                                            public TableResultBuilder createTableResultBuilder() {
                                                return searchResultWriter;
                                            }
                                        };

                                final Map<String, ResultCreator> resultCreatorMap =
                                        Map.of(resultRequest.getComponentId(), tableResultCreator);
                                searchResponseCreatorManager.search(requestAndStore, resultCreatorMap);
                                totalRowCount += searchResultWriter.getRowCount();

                            } catch (final Exception e) {
                                LOGGER.debug(e::getMessage, e);
                                throw e;
                            } finally {
                                target.endTable();
                            }
                        }
                    } catch (final Exception e) {
                        LOGGER.debug(e::getMessage, e);
                        throw e;
                    } finally {
                        target.end();
                    }

                } catch (final IOException e) {
                    throw EntityServiceExceptionUtil.create(e);
                }

                searchEventLog.downloadResults(request, totalRowCount);
            } catch (final RuntimeException e) {
                searchEventLog.downloadResults(request, totalRowCount, e);
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
        final SearchRequestSource searchRequestSource = request.getSearchRequestSource();
        String basename = searchRequestSource.getComponentId();
        if (searchRequestSource.getOwnerDocRef() != null) {
            final DocRefInfo dashDocRefInfo = dashboardStore.info(searchRequestSource.getOwnerDocRef());
            final String dashboardName = NullSafe.getOrElse(
                    dashDocRefInfo,
                    DocRefInfo::getDocRef,
                    DocRef::getName,
                    searchRequestSource.getOwnerDocRef().getName());
            if (dashboardName != null) {
                basename = dashboardName + "__" + searchRequestSource.getComponentId();
            }
        }
        return getFileName(basename, "json");
    }

    private String getFileName(final String baseName,
                               final String extension) {
        String fileName = baseName;
        fileName = NON_BASIC_CHARS.matcher(fileName).replaceAll("");
        fileName = MULTIPLE_SPACE.matcher(fileName).replaceAll(" ");
        fileName = fileName.replace(' ', '_');
        fileName = fileName + "." + extension;
        return fileName;
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
                                final DashboardSearchResponse searchResponse;
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
//    public Boolean destroy(final DestroySearchRequest request) {
//        return searchResponseCreatorManager.destroy(request.getQueryKey());
//    }

    private DashboardSearchResponse processRequest(final DashboardSearchRequest searchRequest) {
        LOGGER.trace(() -> "processRequest() " + searchRequest);
        DashboardSearchResponse result = null;

        final QueryKey queryKey = searchRequest.getQueryKey();
        final Search search = searchRequest.getSearch();

        if (search != null) {
            Exception exception = null;
            try {
                final SearchRequest mappedRequest = searchRequestMapper.mapRequest(searchRequest);

                // Perform the search or update results.
                final SearchResponse searchResponse = searchResponseCreatorManager.search(mappedRequest);
                result = new SearchResponseMapper().mapResponse(nodeInfo.getThisNodeName(), searchResponse);

                if (queryKey == null) {
                    // If the query doesn't have a key then this is new.
                    LOGGER.debug(() -> "New query");

                    // Add this search to the history so the user can get back to this
                    // search again.
                    storeSearchHistory(searchRequest);
                }
            } catch (final RuntimeException e) {
                exception = e;
                final Search finalSearch = search;
                LOGGER.debug(() -> "Error processing search " + finalSearch, e);

                result = new DashboardSearchResponse(
                        nodeInfo.getThisNodeName(),
                        queryKey,
                        null,
                        Collections.singletonList(ExceptionStringUtil.getMessage(e)),
                        null,
                        true,
                        null);
            } finally {
                // Log here so we don't log twice if there is an error
                if (queryKey == null) {
                    searchEventLog.search(
                            searchRequest.getQueryKey(),
                            NullSafe.get(
                                    searchRequest,
                                    DashboardSearchRequest::getSearchRequestSource,
                                    SearchRequestSource::getComponentId),
                            "Dashboard Search",
                            null,
                            search.getDataSourceRef(),
                            search.getExpression(),
                            search.getQueryInfo(),
                            search.getParams(),
                            NullSafe.get(result, DashboardSearchResponse::getResults),
                            exception);
                }
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
                final Query query = new Query(
                        search.getDataSourceRef(),
                        search.getExpression(),
                        search.getParams(),
                        search.getTimeRange());
                final SearchRequestSource searchRequestSource = request.getSearchRequestSource();
                final StoredQuery storedQuery = new StoredQuery();
                storedQuery.setName("History");
                storedQuery.setDashboardUuid(NullSafe
                        .get(searchRequestSource, SearchRequestSource::getOwnerDocRef, DocRef::getUuid));
                storedQuery.setComponentId(searchRequestSource.getComponentId());
                storedQuery.setQuery(query);
                queryService.create(storedQuery);

            } catch (final RuntimeException e) {
                LOGGER.error(e::getMessage, e);
            }
        }
    }

    @Override
    public ColumnValues getColumnValues(final ColumnValuesRequest request) {
        final DashboardSearchRequest searchRequest = request.getSearchRequest();
        final QueryKey queryKey = searchRequest.getQueryKey();
        try {
            if (queryKey == null) {
                throw new EntityServiceException("No query is active");
            }

//            final Map<String, TableResultRequest> tableRequestMap = request
//                    .getSearchRequest()
//                    .getComponentResultRequests()
//                    .stream()
//                    .filter(req -> req instanceof TableResultRequest)
//                    .collect(Collectors.toMap(
//                            ComponentResultRequest::getComponentId,
//                            req -> (TableResultRequest) req));

            final SearchRequest mappedRequest = searchRequestMapper.mapRequest(searchRequest);
            final List<ResultRequest> resultRequests = mappedRequest
                    .getResultRequests()
                    .stream()
                    .filter(req -> ResultStyle.TABLE.equals(req.getResultStyle()))
                    .toList();

            if (resultRequests.isEmpty()) {
                throw new EntityServiceException("No tables specified for download");
            }

            final Set<String> dedupe = new HashSet<>();
            final TrimmedSortedList<String> list = new TrimmedSortedList<>(
                    request.getPageRequest(),
                    new GenericComparator());
            for (final ResultRequest resultRequest : resultRequests) {
//                final TableResultRequest tableResultRequest =
//                        tableRequestMap.get(resultRequest.getComponentId());
                try {
                    final RequestAndStore requestAndStore = searchResponseCreatorManager
                            .getResultStore(mappedRequest);
                    final DataStore dataStore = requestAndStore
                            .resultStore()
                            .getData(resultRequest.getComponentId());

                    final TimeFilter timeFilter = null;
                    final Predicate<Val> predicate = valPredicateFactory.createValPredicate(
                            request.getColumn(),
                            request.getFilter(),
                            request.getSearchRequest().getDateTimeSettings());

                    final Set<Key> openGroups = dataStore.getKeyFactory().decodeSet(resultRequest.getOpenGroups());

                    final int index = dataStore
                            .getColumns()
                            .stream()
                            .map(Column::getId)
                            .toList()
                            .indexOf(request.getColumn().getId());
                    if (index != -1) {
                        dataStore.fetch(
                                dataStore.getColumns(),
                                OffsetRange.UNBOUNDED,
                                new OpenGroupsImpl(openGroups),
                                timeFilter,
                                item -> {
                                    final Val val = item.getValue(index);
                                    if (predicate.test(val)) {
                                        final String string = val.toString();
                                        if (string != null && dedupe.add(string)) {
                                            list.add(string);
                                        }
                                    }
                                    return null;
                                },
                                row -> {

                                },
                                count -> {

                                });
                    }
                } catch (final Exception e) {
                    LOGGER.debug(e::getMessage, e);
                    throw e;
                }
            }

            final ResultPage<String> resultPage = list.getResultPage();
            return new ColumnValues(resultPage.getValues(), resultPage.getPageResponse());
        } catch (final Exception e) {
            LOGGER.debug(e::getMessage, e);
            throw e;
        }
    }

    @Override
    public String getBestNode(final String nodeName, final DashboardSearchRequest request) {
        if (nodeName == null || nodeName.equals("null")) {
            if (queryNodeResolver == null) {
                return null;
            }
            final DocRef docRef = NullSafe.get(request, DashboardSearchRequest::getSearch, Search::getDataSourceRef);
            return queryNodeResolver.getNode(docRef);
        }
        return nodeName;
    }
}
