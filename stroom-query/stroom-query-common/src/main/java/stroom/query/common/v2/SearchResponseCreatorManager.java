package stroom.query.common.v2;

import stroom.cache.api.CacheManager;
import stroom.cache.api.StroomCache;
import stroom.datasource.api.v2.DataSource;
import stroom.datasource.api.v2.DateField;
import stroom.docref.DocRef;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.ExpressionOperator.Op;
import stroom.query.api.v2.ExpressionTerm.Condition;
import stroom.query.api.v2.FlatResult;
import stroom.query.api.v2.OffsetRange;
import stroom.query.api.v2.Param;
import stroom.query.api.v2.Query;
import stroom.query.api.v2.QueryKey;
import stroom.query.api.v2.Result;
import stroom.query.api.v2.SearchRequest;
import stroom.query.api.v2.SearchResponse;
import stroom.query.api.v2.TableResult;
import stroom.query.api.v2.TimeRange;
import stroom.security.api.SecurityContext;
import stroom.task.api.ExecutorProvider;
import stroom.task.api.TaskContext;
import stroom.task.api.TaskContextFactory;
import stroom.util.json.JsonUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.Clearable;

import com.google.common.base.Preconditions;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;

@Singleton
public final class SearchResponseCreatorManager implements Clearable {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(SearchResponseCreatorManager.class);

    private static final String CACHE_NAME = "Search Results";

    private final SearchResponseCreatorFactory searchResponseCreatorFactory;
    private final TaskContext taskContext;
    private final TaskContextFactory taskContextFactory;
    private final SecurityContext securityContext;
    private final ExecutorProvider executorProvider;
    private final StroomCache<QueryKey, SearchResponseCreator> cache;
    private final StoreFactoryRegistry storeFactoryRegistry;

    @Inject
    SearchResponseCreatorManager(final CacheManager cacheManager,
                                 final Provider<ResultStoreConfig> resultStoreConfigProvider,
                                 final SearchResponseCreatorFactory searchResponseCreatorFactory,
                                 final TaskContext taskContext,
                                 final TaskContextFactory taskContextFactory,
                                 final SecurityContext securityContext,
                                 final ExecutorProvider executorProvider,
                                 final StoreFactoryRegistry storeFactoryRegistry) {
        this.searchResponseCreatorFactory = searchResponseCreatorFactory;
        this.taskContext = taskContext;
        this.taskContextFactory = taskContextFactory;
        this.securityContext = securityContext;
        this.executorProvider = executorProvider;
        this.cache = cacheManager.create(
                CACHE_NAME,
                () -> resultStoreConfigProvider.get().getSearchResultCache(),
                this::destroy);
        this.storeFactoryRegistry = storeFactoryRegistry;
    }

    public Optional<SearchResponseCreator> getIfPresent(final QueryKey key) {
        return cache.getIfPresent(key);
    }

    private void destroy(final QueryKey key, final SearchResponseCreator value) {
        LOGGER.trace(() -> "destroy() " + key);
        if (value != null) {
            LOGGER.debug(() -> "Destroying key: " + key);
            securityContext.asProcessingUser(value::destroy);
        }
    }

    public DataSource getDataSource(final DocRef docRef) {
        if (LOGGER.isDebugEnabled()) {
            String json = JsonUtil.writeValueAsString(docRef);
            LOGGER.debug("/dataSource called with docRef:\n{}", json);
        }
        final Optional<StoreFactory> optionalStoreFactory = storeFactoryRegistry.getStoreFactory(docRef);
        return optionalStoreFactory
                .map(sf -> sf.getDataSource(docRef))
                .orElseThrow(() -> new RuntimeException("Unknown data source type: " + docRef));
    }

    public SearchResponse search(final SearchRequest searchRequest) {
        if (LOGGER.isDebugEnabled()) {
            String json = JsonUtil.writeValueAsString(searchRequest);
            LOGGER.debug("/search called with searchRequest:\n{}", json);
        }

        SearchRequest modifiedRequest = searchRequest;

        final String userId = securityContext.getUserId();
        Objects.requireNonNull(userId, "No user is logged in");

        Objects.requireNonNull(modifiedRequest.getQuery(),
                "Query is null");
        final DocRef dataSourceRef = modifiedRequest.getQuery().getDataSource();
        if (dataSourceRef == null || dataSourceRef.getUuid() == null) {
            throw new RuntimeException("No search data source has been specified");
        }

        final Optional<StoreFactory> optionalStoreFactory =
                storeFactoryRegistry.getStoreFactory(modifiedRequest.getQuery().getDataSource());
        final StoreFactory storeFactory = optionalStoreFactory
                .orElseThrow(() ->
                        new RuntimeException("No store factory found for " +
                                searchRequest.getQuery().getDataSource().getType()));

        final SearchResponseCreator searchResponseCreator;
        if (modifiedRequest.getKey() != null) {
            final QueryKey queryKey = modifiedRequest.getKey();
            final Optional<SearchResponseCreator> optionalSearchResponseCreator =
                    getIfPresent(queryKey);

            final String message = "No active search found for key = " + queryKey;
            searchResponseCreator = optionalSearchResponseCreator.orElseThrow(() ->
                    new RuntimeException(message));

            // Check user identity.
            if (!searchResponseCreator.getUserId().equals(userId)) {
                throw new RuntimeException(
                        "You do not have permission to get the search results associated with this key");
            }

        } else {
            // If the query doesn't have a key then this is new.
            LOGGER.debug(() -> "New query");

            // Create a new search UUID.
            modifiedRequest = addQueryKey(modifiedRequest);

            // Add a param for `currentUser()`
            modifiedRequest = addCurrentUserParam(userId, modifiedRequest);

            // Add partition time constraints to the query.
            modifiedRequest = addTimeRangeExpression(storeFactory.getTimeField(dataSourceRef), modifiedRequest);

            final SearchRequest finalModifiedRequest = modifiedRequest;
            final QueryKey queryKey = finalModifiedRequest.getKey();
            LOGGER.trace(() -> "get() " + queryKey);
            searchResponseCreator = cache.get(queryKey, k -> {
                try {
                    LOGGER.trace(() -> "create() " + queryKey);
                    LOGGER.debug(() -> "Creating new store for key: " + queryKey);
                    final Store store = storeFactory.create(finalModifiedRequest);
                    return searchResponseCreatorFactory.create(userId, store);
                } catch (final RuntimeException e) {
                    LOGGER.debug(e.getMessage(), e);
                    throw e;
                }
            });
        }

        // Perform search.
        final SearchRequest finalSearchRequest = modifiedRequest;
        final Supplier<SearchResponse> supplier =
                taskContextFactory.contextResult("Getting search results", taskContext -> {
                    taskContext.info(() -> "Creating search result");
                    return securityContext.useAsReadResult(() -> doSearch(searchResponseCreator, finalSearchRequest));
                });
        final Executor executor = executorProvider.get();
        final CompletableFuture<SearchResponse> completableFuture = CompletableFuture.supplyAsync(supplier, executor);
        try {
            return completableFuture.get();
        } catch (final InterruptedException | ExecutionException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    private SearchRequest addQueryKey(final SearchRequest searchRequest) {
        // Create a new search UUID.
        final String uuid = UUID.randomUUID().toString();
        LOGGER.debug(() -> "Creating new search UUID = " + uuid);
        final QueryKey queryKey = new QueryKey(uuid);
        return searchRequest.copy().key(queryKey).build();
    }

    private SearchRequest addCurrentUserParam(final String userId,
                                              final SearchRequest searchRequest) {
        // Add a param for `currentUser()`
        List<Param> params = searchRequest.getQuery().getParams();
        if (params != null) {
            params = new ArrayList<>(params);
        } else {
            params = new ArrayList<>();
        }
        params.add(new Param("currentUser()", userId));
        return searchRequest
                .copy()
                .query(searchRequest
                        .getQuery()
                        .copy()
                        .params(params)
                        .build())
                .build();
    }

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

    private SearchResponse doSearch(final SearchResponseCreator searchResponseCreator, final SearchRequest request) {
        Preconditions.checkNotNull(request);

        try {
            // Create a response from the data found so far, this could be complete/incomplete
            final SearchResponse response = searchResponseCreator.create(request);

            LOGGER.trace(() -> getResponseInfoForLogging(request, response));

            return response;

        } catch (final RuntimeException e) {
            // Create an error response.
            List<Result> results;
            if (request.getResultRequests() != null) {
                results = request.getResultRequests().stream()
                        .map(resultRequest -> new TableResult(
                                resultRequest.getComponentId(),
                                Collections.emptyList(),
                                Collections.emptyList(),
                                new OffsetRange(0, 0),
                                0,
                                null))
                        .collect(Collectors.toList());
            } else {
                results = Collections.emptyList();
            }

            return new SearchResponse(
                    request.getKey(),
                    Collections.emptyList(),
                    results,
                    Collections.singletonList(e.getMessage()),
                    true);
        }
    }

    private String getResponseInfoForLogging(final SearchRequest request, final SearchResponse searchResponse) {
        String resultInfo;

        if (searchResponse.getResults() != null) {
            resultInfo = "\n" + searchResponse.getResults().stream()
                    .map(result -> {
                        if (result instanceof FlatResult) {
                            FlatResult flatResult = (FlatResult) result;
                            return LogUtil.message(
                                    "  FlatResult - componentId: {}, size: {}, ",
                                    flatResult.getComponentId(),
                                    flatResult.getSize());
                        } else if (result instanceof TableResult) {
                            TableResult tableResult = (TableResult) result;
                            return LogUtil.message(
                                    "  TableResult - componentId: {}, rows: {}, totalResults: {}, " +
                                            "resultRange: {}",
                                    tableResult.getComponentId(),
                                    tableResult.getRows().size(),
                                    tableResult.getTotalResults(),
                                    tableResult.getResultRange());
                        } else {
                            return "  Unknown type " + result.getClass().getName();
                        }
                    })
                    .collect(Collectors.joining("\n"));
        } else {
            resultInfo = "null";
        }

        return LogUtil.message("Return search response, key: {}, result sets: {}, " +
                        "complete: {}, errors: {}, results: {}",
                request.getKey().toString(),
                searchResponse.getResults(),
                searchResponse.complete(),
                searchResponse.getErrors(),
                resultInfo);
    }

    /**
     * Get an existing {@link SearchResponseCreator} from the cache if possible
     *
     * @param queryKey The key of the entry to retrieve.
     * @return Get a {@link SearchResponseCreator} from the cache
     */
    public Boolean keepAlive(final QueryKey queryKey) {
        if (LOGGER.isDebugEnabled()) {
            String json = JsonUtil.writeValueAsString(queryKey);
            LOGGER.debug("/keepAlive called with queryKey:\n{}", json);
        }

        LOGGER.trace(() -> "keepAlive() " + queryKey);
        final Optional<SearchResponseCreator> optionalSearchResponseCreator = cache
                .getIfPresent(queryKey);
        return optionalSearchResponseCreator
                .map(SearchResponseCreator::keepAlive)
                .orElse(Boolean.FALSE);
    }

    /**
     * Remove an entry from the cache, this will also terminate any running search for that entry
     *
     * @param queryKey The key of the entry to remove.
     */
    public Boolean destroy(final QueryKey queryKey) {
        if (LOGGER.isDebugEnabled()) {
            String json = JsonUtil.writeValueAsString(queryKey);
            LOGGER.debug("/destroy called with queryKey:\n{}", json);
        }

        final Supplier<Boolean> supplier = taskContextFactory.contextResult("Destroy search",
                taskContext -> {
                    taskContext.info(queryKey::getUuid);
                    LOGGER.debug(() -> "remove called for queryKey " + queryKey);
                    cache.remove(queryKey);
                    return true;
                });
        final Executor executor = executorProvider.get();
        final CompletableFuture<Boolean> completableFuture = CompletableFuture.supplyAsync(supplier, executor);
        try {
            return completableFuture.get();
        } catch (final InterruptedException | ExecutionException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    /**
     * Evicts any expired entries from the underlying cache
     */
    public void evictExpiredElements() {
        taskContext.info(() -> "Evicting expired search responses");
        cache.evictExpiredElements();
    }

    @Override
    public void clear() {
        cache.clear();
    }
}
