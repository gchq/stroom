package stroom.query.common.v2;

import stroom.cache.api.CacheManager;
import stroom.cache.api.ICache;
import stroom.query.api.v2.FlatResult;
import stroom.query.api.v2.OffsetRange;
import stroom.query.api.v2.QueryKey;
import stroom.query.api.v2.Result;
import stroom.query.api.v2.SearchRequest;
import stroom.query.api.v2.SearchResponse;
import stroom.query.api.v2.TableResult;
import stroom.security.api.SecurityContext;
import stroom.task.api.ExecutorProvider;
import stroom.task.api.TaskContext;
import stroom.task.api.TaskContextFactory;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.Clearable;

import com.google.common.base.Preconditions;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import javax.inject.Inject;
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
    private final ICache<Key, SearchResponseCreator> cache;

    @Inject
    SearchResponseCreatorManager(final CacheManager cacheManager,
                                 final ResultStoreConfig resultStoreConfig,
                                 final SearchResponseCreatorFactory searchResponseCreatorFactory,
                                 final TaskContext taskContext,
                                 final TaskContextFactory taskContextFactory,
                                 final SecurityContext securityContext,
                                 final ExecutorProvider executorProvider) {
        this.searchResponseCreatorFactory = searchResponseCreatorFactory;
        this.taskContext = taskContext;
        this.taskContextFactory = taskContextFactory;
        this.securityContext = securityContext;
        this.executorProvider = executorProvider;
        this.cache = cacheManager.create(CACHE_NAME,
                resultStoreConfig::getSearchResultCache,
                this::create,
                this::destroy);
    }

    private SearchResponseCreator create(final Key key) {
        try {
            LOGGER.trace(() -> "create() " + key);
            LOGGER.debug(() -> "Creating new store for key: " + key);
            final Store store = key.getStoreFactory().create(key.getSearchRequest());
            return searchResponseCreatorFactory.create(key.getUserId(), store);
        } catch (final RuntimeException e) {
            LOGGER.debug(e.getMessage(), e);
            throw e;
        }
    }

    private void destroy(final Key key, final SearchResponseCreator value) {
        LOGGER.trace(() -> "destroy() " + key);
        if (value != null) {
            LOGGER.debug(() -> "Destroying key: " + key);
            securityContext.asProcessingUser(value::destroy);
        }
    }

    public SearchResponse search(final StoreFactory storeFactory, final SearchRequest request) {
        final Supplier<SearchResponse> supplier =
                taskContextFactory.contextResult("Getting search results", taskContext -> {
                    taskContext.info(() -> "Creating search result");
                    return securityContext.useAsReadResult(() -> doSearch(storeFactory, request));
                });
        final Executor executor = executorProvider.get();
        final CompletableFuture<SearchResponse> completableFuture = CompletableFuture.supplyAsync(supplier, executor);
        try {
            return completableFuture.get();
        } catch (final InterruptedException | ExecutionException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    private SearchResponse doSearch(final StoreFactory storeFactory, final SearchRequest request) {
        Preconditions.checkNotNull(request);

        // Create a request UUID if we don't already have one.
        final SearchRequest newRequest;
        if (request.getKey() == null) {
            final String uuid = UUID.randomUUID().toString();
            LOGGER.debug(() -> "Creating new search UUID = " + uuid);
            newRequest = request.copy().key(uuid).build();
        } else {
            newRequest = request;
        }

        try {
            // If this is the first call for this query key then it will create a searchResponseCreator (& store) that
            // have a lifespan beyond the scope of this request and then begin the search for the data.
            // If it is not the first call for this query key then it will return the existing searchResponseCreator
            // with access to whatever data has been found so far
            final SearchResponseCreator searchResponseCreator = get(storeFactory, newRequest);

            // Create a response from the data found so far, this could be complete/incomplete
            final SearchResponse response = searchResponseCreator.create(newRequest);

            LOGGER.trace(() -> getResponseInfoForLogging(request, response));

            return response;

        } catch (final RuntimeException e) {
            // Create an error response.
            List<Result> results;
            if (newRequest.getResultRequests() != null) {
                results = newRequest.getResultRequests().stream()
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
                    newRequest.getKey(),
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
     * Get a {@link SearchResponseCreator} from the cache or create one if it doesn't exist
     *
     * @param searchRequest The search request to get the response creator for.
     * @return Get a {@link SearchResponseCreator} from the cache or create one if it doesn't exist
     */
    private SearchResponseCreator get(final StoreFactory storeFactory, final SearchRequest searchRequest) {
        final String userId = securityContext.getUserId();
        if (userId == null) {
            throw new RuntimeException("No user is logged in");
        }

        final Key key = new Key(userId, storeFactory, searchRequest);
        LOGGER.trace(() -> "get() " + key);
        final SearchResponseCreator searchResponseCreator = cache.get(key);
        if (!searchResponseCreator.getUserId().equals(key.getUserId())) {
            throw new RuntimeException(
                    "You do not have permission to get the search results associated with this key");
        }
        return searchResponseCreator;
    }

    /**
     * Get an existing {@link SearchResponseCreator} from the cache if possible
     *
     * @param queryKey The key of the entry to retrieve.
     * @return Get a {@link SearchResponseCreator} from the cache
     */
    public Boolean keepAlive(final QueryKey queryKey) {
        LOGGER.trace(() -> "keepAlive() " + queryKey);
        final Optional<SearchResponseCreator> optionalSearchResponseCreator = cache
                .getOptional(new Key(queryKey));
        return optionalSearchResponseCreator
                .map(SearchResponseCreator::keepAlive)
                .orElse(Boolean.FALSE);
    }

    /**
     * Remove an entry from the cache, this will also terminate any running search for that entry
     *
     * @param queryKey The key of the entry to remove.
     */
    public Boolean remove(final QueryKey queryKey) {
        final Supplier<Boolean> supplier = taskContextFactory.contextResult("Destroy search",
                taskContext -> {
                    taskContext.info(queryKey::getUuid);
                    LOGGER.debug(() -> "remove called for queryKey " + queryKey);
                    cache.remove(new Key(queryKey));
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

    private static class Key {

        private final String userId;
        private final QueryKey queryKey;
        private final StoreFactory storeFactory;
        private final SearchRequest searchRequest;

        public Key(final String userId,
                   final StoreFactory storeFactory,
                   final SearchRequest searchRequest) {
            this.userId = userId;
            this.queryKey = searchRequest.getKey();
            this.storeFactory = storeFactory;
            this.searchRequest = searchRequest;
        }

        public Key(final QueryKey queryKey) {
            this.userId = null;
            this.queryKey = queryKey;
            this.storeFactory = null;
            this.searchRequest = null;
        }

        public String getUserId() {
            return userId;
        }

        public QueryKey getQueryKey() {
            return queryKey;
        }

        public StoreFactory getStoreFactory() {
            return storeFactory;
        }

        public SearchRequest getSearchRequest() {
            return searchRequest;
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            final Key key = (Key) o;

            return queryKey.equals(key.queryKey);
        }

        @Override
        public int hashCode() {
            return queryKey.hashCode();
        }

        @Override
        public String toString() {
            return "Key{" +
                    "queryKey=" + queryKey +
                    '}';
        }
    }
}
