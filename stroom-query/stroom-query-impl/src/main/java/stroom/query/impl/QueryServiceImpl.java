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

import stroom.dashboard.impl.SampleGenerator;
import stroom.dashboard.impl.SearchResponseMapper;
import stroom.dashboard.impl.download.DelimitedTarget;
import stroom.dashboard.impl.download.ExcelTarget;
import stroom.dashboard.impl.download.SearchResultWriter;
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
import stroom.query.api.v2.ResultRequest.ResultStyle;
import stroom.query.api.v2.SearchRequest;
import stroom.query.api.v2.SearchResponse;
import stroom.query.api.v2.TableResultBuilder;
import stroom.query.api.v2.TableSettings;
import stroom.query.api.v2.TimeRange;
import stroom.query.common.v2.DataSourceProviderRegistry;
import stroom.query.common.v2.ExpressionContextFactory;
import stroom.query.common.v2.ResultCreator;
import stroom.query.common.v2.ResultStoreManager;
import stroom.query.common.v2.ResultStoreManager.RequestAndStore;
import stroom.query.common.v2.TableResultCreator;
import stroom.query.common.v2.format.ColumnFormatter;
import stroom.query.common.v2.format.FormatterFactory;
import stroom.query.language.SearchRequestFactory;
import stroom.query.language.functions.ExpressionContext;
import stroom.query.language.token.Token;
import stroom.query.language.token.TokenException;
import stroom.query.language.token.TokenType;
import stroom.query.language.token.Tokeniser;
import stroom.query.shared.DownloadQueryResultsRequest;
import stroom.query.shared.QueryContext;
import stroom.query.shared.QueryDoc;
import stroom.query.shared.QueryHelpType;
import stroom.query.shared.QuerySearchRequest;
import stroom.resource.api.ResourceStore;
import stroom.security.api.SecurityContext;
import stroom.security.shared.AppPermission;
import stroom.task.api.ExecutorProvider;
import stroom.task.api.TaskContextFactory;
import stroom.task.api.TerminateHandlerFactory;
import stroom.util.EntityServiceExceptionUtil;
import stroom.util.NullSafe;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.servlet.HttpServletRequestHolder;
import stroom.util.shared.EntityServiceException;
import stroom.util.shared.ResourceGeneration;
import stroom.util.shared.ResourceKey;
import stroom.util.shared.ResultPage;
import stroom.util.string.ExceptionStringUtil;
import stroom.util.string.StringUtil;

import jakarta.inject.Inject;
import jakarta.servlet.http.HttpServletRequest;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@AutoLogged
class QueryServiceImpl implements QueryService {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(QueryServiceImpl.class);

    private static final ContextualQueryHelp EMPTY_QUERY_CONTEXT = new ContextualQueryHelp(
            EnumSet.of(QueryHelpType.STRUCTURE),
            Set.of(Structures.name(TokenType.FROM)));
    private static final Pattern NON_BASIC_CHARS = Pattern.compile("[^A-Za-z0-9-_ ]");
    private static final Pattern MULTIPLE_SPACE = Pattern.compile(" +");

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
    private final ResourceStore resourceStore;

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
                     final ExpressionContextFactory expressionContextFactory,
                     final ResourceStore resourceStore) {
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
        this.resourceStore = resourceStore;
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
        return securityContext.secureResult(AppPermission.DOWNLOAD_SEARCH_RESULTS_PERMISSION, () -> {
            final QuerySearchRequest searchRequest = request.getSearchRequest();
            final QueryKey queryKey = searchRequest.getQueryKey();
            ResourceKey resourceKey;
            long totalRowCount = 0;
            final SearchRequest mappedRequest = mapRequest(searchRequest);

            try {
                if (queryKey == null) {
                    throw new EntityServiceException("No query is active");
                }

                final DateTimeSettings dateTimeSettings = searchRequest.getQueryContext().getDateTimeSettings();
                final List<ResultRequest> resultRequests = mappedRequest
                        .getResultRequests()
                        .stream()
                        .filter(req -> ResultStyle.TABLE.equals(req.getResultStyle()))
                        .toList();

                if (resultRequests.isEmpty()) {
                    throw new EntityServiceException("No tables specified for download");
                }

                final RequestAndStore requestAndStore = searchResponseCreatorManager
                        .getResultStore(mappedRequest);

                // Import file.
                final String fileName = getResultsFilename(request);
                resourceKey = resourceStore.createTempFile(fileName);
                final Path file = resourceStore.getTempFile(resourceKey);

                final ColumnFormatter fieldFormatter =
                        new ColumnFormatter(
                                new FormatterFactory(dateTimeSettings));

                // Start target
                try (final OutputStream outputStream = new BufferedOutputStream(Files.newOutputStream(file))) {
                    final SearchResultWriter.Target target = switch (request.getFileType()) {
                        case CSV -> new DelimitedTarget(outputStream, ",");
                        case TSV -> new DelimitedTarget(outputStream, "\t");
                        case EXCEL -> new ExcelTarget(outputStream, dateTimeSettings);
                    };

                    // Write delimited file.

                    try {
                        target.start();

                        for (final ResultRequest resultRequest : resultRequests) {
                            try {
                                target.startTable("table");

                                final SampleGenerator sampleGenerator =
                                        new SampleGenerator(request.isSample(), request.getPercent());
                                final SearchResultWriter searchResultWriter = new SearchResultWriter(
                                        sampleGenerator,
                                        target);
                                final TableResultCreator tableResultCreator =
                                        new TableResultCreator(fieldFormatter) {
                                            @Override
                                            public TableResultBuilder createTableResultBuilder() {
                                                return searchResultWriter;
                                            }
                                        };

                                final Map<String, ResultCreator> resultCreatorMap =
                                        Map.of(resultRequest.getComponentId(), tableResultCreator);
                                searchResponseCreatorManager.search(requestAndStore, resultCreatorMap);
                                totalRowCount += searchResultWriter.getRowCount();

                            } finally {
                                target.endTable();
                            }
                        }
                    } finally {
                        target.end();
                    }

                } catch (final IOException e) {
                    throw EntityServiceExceptionUtil.create(e);
                }

                searchEventLog.downloadResults(request, mappedRequest, totalRowCount);
            } catch (final RuntimeException e) {
                searchEventLog.downloadResults(request, mappedRequest, totalRowCount, e);
                throw EntityServiceExceptionUtil.create(e);
            }

            return new ResourceGeneration(resourceKey, new ArrayList<>());
        });
    }

    private String getResultsFilename(final DownloadQueryResultsRequest request) {
        final QuerySearchRequest searchRequest = request.getSearchRequest();
        final String basename = searchRequest.getQueryKey().getUuid();
        return getFileName(basename, request.getFileType().getExtension());
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
                            "StroomQL Search",
                            searchRequest.getQuery(),
                            mappedRequest.getQuery().getDataSource(),
                            mappedRequest.getQuery().getExpression(),
                            searchRequest.getQueryContext().getQueryInfo(),
                            searchRequest.getQueryContext().getParams(),
                            null);
                }

            } catch (final TokenException e) {
                LOGGER.debug(() -> "Error processing search " + searchRequest, e);

                if (queryKey == null) {
                    searchEventLog.search(
                            "StroomQL Search",
                            searchRequest.getQuery(),
                            null,
                            null,
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
                    searchEventLog.search(
                            "StroomQL Search",
                            searchRequest.getQuery(),
                            null,
                            null,
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
                // Will get an exception if the datasource does not exist, which is likely
                // if the user has not finished typing yet.
                LOGGER.debug(() -> e.getMessage() + ". Enable TRACE to see stacktrace");
                LOGGER.trace(e::getMessage, e);
            }
            return Optional.ofNullable(ref.get());
        });
    }

    @Override
    public ContextualQueryHelp getQueryHelpContext(final String query, final int row, final int col) {
        return securityContext.useAsReadResult(() -> {
            if (NullSafe.isBlankString(query) || (row == 0 && col == 0)) {
                return EMPTY_QUERY_CONTEXT;
            } else {
                final int cursorIdx = StringUtil.convertRowColToIndex(query, row, col);
                final String partialQuery = cursorIdx < query.length()
                        ? query.substring(0, cursorIdx)
                        : query;
                LOGGER.debug("row: {}, col: {}, cursorIdx: {}, Query:\n{}",
                        row, col, cursorIdx, partialQuery);

                return getQueryHelpContext(partialQuery);
            }
        });
    }

    /**
     * Here to help testing, so we don't have to work out the row/col postitions
     */
    ContextualQueryHelp getQueryHelpContext(final String partialQuery) {
        if (NullSafe.isBlankString(partialQuery)) {
            return EMPTY_QUERY_CONTEXT;
        } else {
            final Set<QueryHelpType> types = new HashSet<>();
            final Set<String> applicableStructureItems;
            final List<Token> tokens = Tokeniser.parse(partialQuery);
            TokenType lastKeyword = null;
            // The list of token types seen since (and including) lastKeyword
            final List<TokenType> lastKeywordSequence = new ArrayList<>();
            final Set<TokenType> keywordsSeen = new HashSet<>();

            for (final Token token : tokens) {
                final int endIdx = token.getEnd();
                if (endIdx >= partialQuery.length()) {
                    // Past the cursor position
                    break;
                }
                final TokenType tokenType = token.getTokenType();
                if (TokenType.isKeyword(tokenType)) {
                    keywordsSeen.add(tokenType);
                    lastKeyword = tokenType;
                    lastKeywordSequence.clear();
                }
                lastKeywordSequence.add(tokenType);

                LOGGER.debug("""

                                token: {}
                                endIdx: {}
                                lastKeyword: {}
                                keywordsSeen: {}
                                lastKeywordSequence: {}""",
                        token, token.getEnd(), lastKeyword, keywordsSeen, lastKeywordSequence);
            }

            // Determine what keywords can come after the last one seen
            final Set<TokenType> keywordsValidAfter = getKeywordsValidAfter(lastKeyword, lastKeywordSequence);

            if (seenInDictionary(tokens)) {
                // If we see 'in dictionary ' then the only thing we want after that is a dictionary
                types.add(QueryHelpType.DICTIONARY);
                applicableStructureItems = Collections.emptySet();
            } else {
                if (includeDataSources(lastKeywordSequence)) {
                    types.add(QueryHelpType.DATA_SOURCE);
                }

                applicableStructureItems = keywordsValidAfter.stream()
                        .filter(tokenType -> TokenType.BY != tokenType)
                        .map(tokenType -> {
                            if (TokenType.GROUP == tokenType) {
                                return Structures.name(TokenType.GROUP, TokenType.BY);
                            } else if (TokenType.SORT == tokenType) {
                                return Structures.name(TokenType.SORT, TokenType.BY);
                            } else if (TokenType.IN == tokenType) {
                                return Structures.name(TokenType.IN, TokenType.DICTIONARY);
                            } else {
                                return Structures.name(tokenType);
                            }
                        })
                        .collect(Collectors.toSet());
                final boolean includeStructure = !keywordsValidAfter.isEmpty();
                if (lastKeyword != null
                        && keywordsSeen.contains(TokenType.FROM)
                        && tokens.size() >= 4) {
                    types.addAll(getHelpTypes(lastKeyword, lastKeywordSequence, includeStructure));
                }
            }

            LOGGER.debug("returning type: {}, applicableStructureItems: {}", types, applicableStructureItems);
            return new ContextualQueryHelp(types, applicableStructureItems);
        }
    }

    private Set<QueryHelpType> getHelpTypes(final TokenType lastKeyword,
                                            final List<TokenType> lastKeywordSequence,
                                            final boolean includeStructure) {
        // Easier to include structure even if we know it is not required, then remove later
        final int count = lastKeywordSequence.size();
        final Set<QueryHelpType> queryHelpTypes;
        if (count == 1) {
            queryHelpTypes = EnumSet.of(QueryHelpType.STRUCTURE);
        } else {
            queryHelpTypes = switch (lastKeyword) {
                case FROM -> count >= 4
                        // 'from xxx '
                        ? EnumSet.of(QueryHelpType.STRUCTURE)
                        : QueryHelpType.NO_TYPES;
                case LIMIT -> count > 3
                        // LIMIT only allows numbers/strings
                        ? EnumSet.of(QueryHelpType.STRUCTURE)
                        : QueryHelpType.NO_TYPES;
                case EVAL -> { // 'eval x = z'
                    if (TokenType.haveSeen(
                            lastKeywordSequence,
                            true,
                            TokenType.EQUALS, TokenType.WHITESPACE)) {
                        yield count > 7
                                ? EnumSet.of(QueryHelpType.FIELD, QueryHelpType.FUNCTION, QueryHelpType.STRUCTURE)
                                : EnumSet.of(QueryHelpType.FIELD, QueryHelpType.FUNCTION);
                    } else {
                        yield QueryHelpType.NO_TYPES;
                    }
                }
                case GROUP, SORT -> count > 4
                        // 'GROUP BY '
                        ? EnumSet.of(QueryHelpType.FIELD, QueryHelpType.STRUCTURE)
                        : EnumSet.of(QueryHelpType.STRUCTURE);
                case SHOW -> count > 3
                        ? EnumSet.of(QueryHelpType.FIELD)
                        : EnumSet.of(QueryHelpType.VISUALISATION);
                default -> {
                    if (count >= 4) {
                        // 'select x '
                        yield EnumSet.of(QueryHelpType.FIELD, QueryHelpType.FUNCTION, QueryHelpType.STRUCTURE);
                    } else if (count >= 2) {
                        // 'select ' or 'select xxx'
                        yield EnumSet.of(QueryHelpType.FIELD, QueryHelpType.FUNCTION);
                    } else {
                        yield QueryHelpType.NO_TYPES;
                    }
                }
            };
        }

        // Need to remove structure in various instances
        if (queryHelpTypes.contains(QueryHelpType.STRUCTURE)) {
            final TokenType lastToken = lastKeywordSequence.getLast();
            boolean doRemove = false;
            if (!includeStructure) {
                // Structure is not valid here,
                doRemove = true;
            } else if (lastToken == TokenType.COMMENT || lastToken == TokenType.BLOCK_COMMENT) {
                doRemove = true;
            } else if (lastToken == TokenType.COMMA
                    || TokenType.haveSeenLast(lastKeywordSequence, TokenType.COMMA, TokenType.WHITESPACE)) {
                doRemove = true;
            } else if (TokenType.ALL_CONDITIONS.contains(lastToken)) {
                doRemove = true;
            } else if (TokenType.ALL_BODMAS.contains(lastToken)) {
                doRemove = true;
            } else if (lastToken == TokenType.OPEN_BRACKET) {
                doRemove = true;
            }

            if (doRemove) {
                final EnumSet<QueryHelpType> copy = EnumSet.copyOf(queryHelpTypes);
                copy.remove(QueryHelpType.STRUCTURE);
                return copy;
            } else {
                return queryHelpTypes;
            }
        } else {
            return queryHelpTypes;
        }
    }

    private static Set<TokenType> getKeywordsValidAfter(final TokenType lastKeyword,
                                                        final List<TokenType> lastKeywordSequence) {
        final Set<TokenType> keywordsValidAfter;
        if (lastKeyword == null) {
            // FROM must be first keyword
            keywordsValidAfter = Set.of(TokenType.FROM);
        } else if (lastKeyword == TokenType.GROUP || lastKeyword == TokenType.SORT) {
            if (lastKeywordSequence.contains(TokenType.BY)) {
                keywordsValidAfter = TokenType.getKeywordsValidAfter(lastKeyword);
            } else {
                keywordsValidAfter = Set.of(TokenType.BY);
            }
        } else {
            keywordsValidAfter = TokenType.getKeywordsValidAfter(lastKeyword);
//            if (lastKeywordSequence.size() <= 3) {
//                // e.g. 'select xxx'
//                keywordsValidAfter = Collections.emptySet();
//            } else {
//                keywordsValidAfter = TokenType.getKeywordsValidAfter(lastKeyword);
//            }
        }
        return keywordsValidAfter;
    }

    private boolean includeDataSources(final List<TokenType> lastKeywordSequence) {
        return isAtIndex(lastKeywordSequence, TokenType.FROM, 0)
                && isAtIndex(lastKeywordSequence, TokenType.WHITESPACE, 1)
                && lastKeywordSequence.size() <= 3;
    }

    private boolean isAtIndex(final List<TokenType> tokenTypes, final TokenType tokenType, final int idx) {
        return tokenTypes != null
                && tokenTypes.size() > idx
                && tokenTypes.get(idx) == tokenType;
    }

    private boolean seenInDictionary(final List<Token> tokens) {
        if (tokens.isEmpty()) {
            return false;
        } else {
            // Last token may be a string if we are part way through the dict name
            return containsTailElements(
                    tokens,
                    Token::getTokenType,
                    TokenType.IN,
                    TokenType.WHITESPACE,
                    TokenType.DICTIONARY,
                    TokenType.WHITESPACE)
                    || containsTailElements(
                    tokens,
                    Token::getTokenType,
                    TokenType.IN,
                    TokenType.WHITESPACE,
                    TokenType.DICTIONARY,
                    TokenType.WHITESPACE,
                    TokenType.STRING);
        }
    }

    private static <T1, T2> boolean containsTailElements(final List<T1> items,
                                                         final Function<T1, T2> mapper,
                                                         final T2... requiredTailTypes) {
        if (NullSafe.isEmptyArray(requiredTailTypes)) {
            return false;
        } else if (NullSafe.isEmptyCollection(items)) {
            return false;
        } else if (requiredTailTypes.length > items.size()) {
            return false;
        } else {
            int listIdx = items.size() - 1;
            boolean isMatch = true;
            Objects.requireNonNull(mapper);
            for (int arrIdx = requiredTailTypes.length - 1; arrIdx >= 0; arrIdx--) {
                final T1 item = items.get(listIdx);
                final T2 mappedItem = mapper.apply(item);
                if (!Objects.equals(requiredTailTypes[arrIdx], mappedItem)) {
                    isMatch = false;
                    break;
                }
                listIdx--;
            }
            return isMatch;
        }
    }

    @Override
    public ResultPage<QueryField> findFields(final FindFieldCriteria criteria) {
        return securityContext.useAsReadResult(() ->
                dataSourceProviderRegistry.getFieldInfo(criteria));
    }

    @Override
    public int getFieldCount(final DocRef dataSourceRef) {
        return securityContext.useAsReadResult(() ->
                dataSourceProviderRegistry.getFieldCount(dataSourceRef));
    }

    @Override
    public Optional<String> fetchDocumentation(final DocRef docRef) {
        return securityContext.useAsReadResult(() ->
                dataSourceProviderRegistry.fetchDocumentation(docRef));
    }
}
