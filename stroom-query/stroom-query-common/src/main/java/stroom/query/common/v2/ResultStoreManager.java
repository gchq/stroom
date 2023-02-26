package stroom.query.common.v2;

import stroom.datasource.api.v2.DataSource;
import stroom.datasource.api.v2.DateField;
import stroom.docref.DocRef;
import stroom.query.api.v2.DestroyReason;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.ExpressionOperator.Op;
import stroom.query.api.v2.ExpressionTerm.Condition;
import stroom.query.api.v2.FindResultStoreCriteria;
import stroom.query.api.v2.FlatResult;
import stroom.query.api.v2.LifespanInfo;
import stroom.query.api.v2.OffsetRange;
import stroom.query.api.v2.Param;
import stroom.query.api.v2.Query;
import stroom.query.api.v2.QueryKey;
import stroom.query.api.v2.Result;
import stroom.query.api.v2.ResultStoreInfo;
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
import stroom.util.shared.PermissionException;
import stroom.util.shared.ResultPage;
import stroom.util.time.StroomDuration;

import com.google.common.base.Preconditions;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public final class ResultStoreManager implements Clearable {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ResultStoreManager.class);

    private final TaskContext taskContext;
    private final TaskContextFactory taskContextFactory;
    private final SecurityContext securityContext;
    private final ExecutorProvider executorProvider;
    private final Map<QueryKey, ResultStore> resultStoreMap;
    private final StoreFactoryRegistry storeFactoryRegistry;

    @Inject
    ResultStoreManager(final TaskContext taskContext,
                       final TaskContextFactory taskContextFactory,
                       final SecurityContext securityContext,
                       final ExecutorProvider executorProvider,
                       final StoreFactoryRegistry storeFactoryRegistry) {
        this.taskContext = taskContext;
        this.taskContextFactory = taskContextFactory;
        this.securityContext = securityContext;
        this.executorProvider = executorProvider;
        this.resultStoreMap = new ConcurrentHashMap<>();
        this.storeFactoryRegistry = storeFactoryRegistry;
    }

    public void update(final QueryKey queryKey,
                       final LifespanInfo searchProcessLifespan,
                       final LifespanInfo storeLifespan) {
        final Optional<ResultStore> optionalResultStore = getIfPresent(queryKey);
        if (optionalResultStore.isPresent()) {
            final ResultStore resultStore = optionalResultStore.get();
            checkPermissions(resultStore);

            final ResultStoreSettings newSettings = new ResultStoreSettings(
                    parseLifespanInfo(searchProcessLifespan),
                    parseLifespanInfo(storeLifespan));

            resultStore.setResultStoreSettings(newSettings);
        }
    }

    private Lifespan parseLifespanInfo(final LifespanInfo lifespanInfo) {
        return new Lifespan(StroomDuration.parse(lifespanInfo.getTimeToIdle()),
                StroomDuration.parse(lifespanInfo.getTimeToLive()),
                lifespanInfo.isDestroyOnTabClose(),
                lifespanInfo.isDestroyOnWindowClose());
    }

    private void checkPermissions(final ResultStore resultStore) {
        if (!securityContext.isAdmin() && !resultStore.getUserId().equals(securityContext.getUserId())) {
            throw new PermissionException(securityContext.getUserId(),
                    "You do not have permission to modify this store");
        }
    }

    private void destroyAndRemove(final QueryKey queryKey, final ResultStore resultStore) {
        try {
            securityContext.asProcessingUser(resultStore::destroy);
        } catch (final RuntimeException e) {
            LOGGER.error(e::getMessage, e);
        } finally {
            resultStoreMap.remove(queryKey);
        }
    }

    private Optional<ResultStore> getIfPresent(final QueryKey key) {
        return Optional.ofNullable(resultStoreMap.get(key));
    }

    public DataSource getDataSource(final DocRef docRef) {
        if (LOGGER.isDebugEnabled()) {
            String json = JsonUtil.writeValueAsString(docRef);
            LOGGER.debug("/dataSource called with docRef:\n{}", json);
        }
        final Optional<SearchProvider> optionalStoreFactory = storeFactoryRegistry.getStoreFactory(docRef);
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

        final ResultStore resultStore;
        if (modifiedRequest.getKey() != null) {
            final QueryKey queryKey = modifiedRequest.getKey();
            final Optional<ResultStore> optionalResultStore =
                    getIfPresent(queryKey);

            final String message = "No active search found for key = " + queryKey;
            resultStore = optionalResultStore.orElseThrow(() ->
                    new RuntimeException(message));

            // Check user identity.
            if (!resultStore.getUserId().equals(userId)) {
                throw new RuntimeException(
                        "You do not have permission to get the search results associated with this key");
            }

        } else {
            // If the query doesn't have a key then this is new.
            LOGGER.debug(() -> "New query");

            // Get the data source.
            Objects.requireNonNull(modifiedRequest.getQuery(),
                    "Query is null");
            final DocRef dataSourceRef = modifiedRequest.getQuery().getDataSource();
            if (dataSourceRef == null || dataSourceRef.getUuid() == null) {
                throw new RuntimeException("No search data source has been specified");
            }

            // Get a store factory to perform this new search.
            final Optional<SearchProvider> optionalStoreFactory =
                    storeFactoryRegistry.getStoreFactory(modifiedRequest.getQuery().getDataSource());
            final SearchProvider storeFactory = optionalStoreFactory
                    .orElseThrow(() ->
                            new RuntimeException("No store factory found for " +
                                    searchRequest.getQuery().getDataSource().getType()));


            // Create a new search UUID.
            modifiedRequest = addQueryKey(modifiedRequest);

            // Add a param for `currentUser()`
            modifiedRequest = addCurrentUserParam(userId, modifiedRequest);

            // Add partition time constraints to the query.
            modifiedRequest = addTimeRangeExpression(storeFactory.getTimeField(dataSourceRef), modifiedRequest);

            final SearchRequest finalModifiedRequest = modifiedRequest;
            final QueryKey queryKey = finalModifiedRequest.getKey();
            LOGGER.trace(() -> "get() " + queryKey);
            try {
                LOGGER.trace(() -> "create() " + queryKey);
                LOGGER.debug(() -> "Creating new store for key: " + queryKey);
                resultStore = storeFactory.createResultStore(finalModifiedRequest);
                resultStoreMap.put(queryKey, resultStore);
            } catch (final RuntimeException e) {
                LOGGER.debug(e.getMessage(), e);
                throw e;
            }
        }

        // Perform search.
        final SearchRequest finalSearchRequest = modifiedRequest;
        final Supplier<SearchResponse> supplier =
                taskContextFactory.contextResult("Getting search results", taskContext -> {
                    taskContext.info(() -> "Creating search result");
                    return securityContext.useAsReadResult(() -> doSearch(resultStore, finalSearchRequest));
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

    private SearchResponse doSearch(final ResultStore resultStore, final SearchRequest request) {
        Preconditions.checkNotNull(request);

        try {
            // Create a response from the data found so far, this could be complete/incomplete
            final SearchResponse response = resultStore.search(request);

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

    public Boolean exists(final QueryKey queryKey) {
        if (LOGGER.isDebugEnabled()) {
            String json = JsonUtil.writeValueAsString(queryKey);
            LOGGER.debug("/exists called with queryKey:\n{}", json);
        }

        final String userId = securityContext.getUserId();
        Objects.requireNonNull(userId, "No user is logged in");

        final Optional<ResultStore> optionalResultStore =
                getIfPresent(queryKey);

        if (optionalResultStore.isPresent()) {
            final ResultStore resultStore = optionalResultStore.get();
            return resultStore.getUserId().equals(userId);
        }

        return false;
    }

    /**
     * Terminate all running search processes associated with a result store.
     *
     * @param queryKey The key of the result store to terminate searches on.
     */
    public Boolean terminate(final QueryKey queryKey) {
        if (LOGGER.isDebugEnabled()) {
            String json = JsonUtil.writeValueAsString(queryKey);
            LOGGER.debug("/terminate called with queryKey:\n{}", json);
        }

        final Supplier<Boolean> supplier = taskContextFactory.contextResult("Terminate search",
                taskContext -> {
                    taskContext.info(queryKey::getUuid);
                    LOGGER.debug(() -> "terminate called for queryKey " + queryKey);
                    final Optional<ResultStore> optionalResultStore = getIfPresent(queryKey);
                    if (optionalResultStore.isPresent()) {
                        final ResultStore resultStore = optionalResultStore.get();
                        checkPermissions(resultStore);
                        resultStore.terminate();
                        return true;
                    }
                    return false;
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
     * Destroy a result store for the specified query key.
     *
     * @param queryKey The key of the result store to destroy.
     */
    public Boolean destroy(final QueryKey queryKey,
                           final DestroyReason destroyReason) {
        if (LOGGER.isDebugEnabled()) {
            String json = JsonUtil.writeValueAsString(queryKey);
            LOGGER.debug("/destroy called with queryKey:\n{}", json);
        }

        final Supplier<Boolean> supplier = taskContextFactory.contextResult("Destroy search",
                taskContext -> {
                    taskContext.info(queryKey::getUuid);
                    LOGGER.debug(() -> "remove called for queryKey " + queryKey);
                    final Optional<ResultStore> resultStore = getIfPresent(queryKey);
                    resultStore.ifPresent(store -> {
                        checkPermissions(store);
                        if (destroyReason == null) {
                            destroyAndRemove(queryKey, store);
                        } else {
                            switch (destroyReason) {
                                case NO_LONGER_NEEDED, MANUAL -> destroyAndRemove(queryKey, store);
                                case TAB_CLOSE -> {
                                    final ResultStoreSettings resultStoreSettings = store.getResultStoreSettings();
                                    if (resultStoreSettings.getStoreLifespan()
                                            .isDestroyOnTabClose()) {
                                        destroyAndRemove(queryKey, store);
                                    } else if (resultStoreSettings.getSearchProcessLifespan()
                                            .isDestroyOnTabClose()) {
                                        store.terminate();
                                    }
                                }
                                case WINDOW_CLOSE -> {
                                    final ResultStoreSettings resultStoreSettings = store.getResultStoreSettings();
                                    if (resultStoreSettings.getStoreLifespan()
                                            .isDestroyOnWindowClose()) {
                                        destroyAndRemove(queryKey, store);
                                    } else if (resultStoreSettings.getSearchProcessLifespan()
                                            .isDestroyOnWindowClose()) {
                                        store.terminate();
                                    }
                                }
                            }
                        }
                    });
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
     * Evicts any expired result stores.
     */
    public void evictExpiredElements() {
        taskContext.info(() -> "Evicting expired search responses");
        final Instant now = Instant.now();
        resultStoreMap.forEach((queryKey, resultStore) -> {
            try {
                final ResultStoreSettings settings = resultStore.getResultStoreSettings();
                final Instant createTime = resultStore.getCreationTime();
                final Instant accessTime = resultStore.getLastAccessTime();

                if (settings.getStoreLifespan().getTimeToLive() != null &&
                        now.isAfter(createTime.plus(settings.getStoreLifespan().getTimeToLive()))) {
                    destroyAndRemove(queryKey, resultStore);
                } else if (settings.getStoreLifespan().getTimeToIdle() != null &&
                        now.isAfter(accessTime.plus(settings.getStoreLifespan().getTimeToIdle()))) {
                    destroyAndRemove(queryKey, resultStore);
                } else if (settings.getSearchProcessLifespan().getTimeToLive() != null &&
                        now.isAfter(createTime.plus(settings.getSearchProcessLifespan().getTimeToLive()))) {
                    resultStore.terminate();
                } else if (settings.getSearchProcessLifespan().getTimeToIdle() != null &&
                        now.isAfter(accessTime.plus(settings.getSearchProcessLifespan().getTimeToIdle()))) {
                    resultStore.terminate();
                }
            } catch (final RuntimeException e) {
                LOGGER.error(e::getMessage, e);
            }
        });
    }

    @Override
    public void clear() {
        resultStoreMap.forEach(this::destroyAndRemove);
    }

    public ResultPage<ResultStoreInfo> find(final FindResultStoreCriteria criteria) {
        final List<ResultStoreInfo> list = new ArrayList<>();
        resultStoreMap.forEach((queryKey, resultStore) -> {
            if (securityContext.isAdmin() || resultStore.getUserId().equals(securityContext.getUserId())) {
                list.add(new ResultStoreInfo(
                        resultStore.getSearchRequestSource(),
                        queryKey,
                        resultStore.getUserId(),
                        resultStore.getCreationTime().toEpochMilli(),
                        resultStore.getNodeName(),
                        resultStore.getCoprocessors().getByteSize(),
                        resultStore.isComplete(),
                        resultStore.getSearchTaskProgress(),
                        getLifespanInfo(resultStore.getResultStoreSettings().getSearchProcessLifespan()),
                        getLifespanInfo(resultStore.getResultStoreSettings().getStoreLifespan())));
            }
        });
        return new ResultPage<>(list);
    }

    private LifespanInfo getLifespanInfo(final Lifespan lifespan) {
        return new LifespanInfo(
                getDurationString(lifespan.getTimeToIdle()),
                getDurationString(lifespan.getTimeToLive()),
                lifespan.isDestroyOnTabClose(),
                lifespan.isDestroyOnWindowClose());
    }

    private String getDurationString(final StroomDuration duration) {
        if (duration == null) {
            return null;
        }
        return duration.getValueAsStr();
    }
}
