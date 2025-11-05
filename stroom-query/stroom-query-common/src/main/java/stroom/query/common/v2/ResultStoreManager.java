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

package stroom.query.common.v2;

import stroom.docref.DocRef;
import stroom.query.api.DateTimeSettings;
import stroom.query.api.DestroyReason;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionOperator.Op;
import stroom.query.api.ExpressionTerm.Condition;
import stroom.query.api.FindResultStoreCriteria;
import stroom.query.api.FlatResult;
import stroom.query.api.LifespanInfo;
import stroom.query.api.OffsetRange;
import stroom.query.api.Param;
import stroom.query.api.Query;
import stroom.query.api.QueryKey;
import stroom.query.api.Result;
import stroom.query.api.ResultStoreInfo;
import stroom.query.api.SearchRequest;
import stroom.query.api.SearchResponse;
import stroom.query.api.TableResult;
import stroom.query.api.TimeRange;
import stroom.query.api.datasource.QueryField;
import stroom.query.language.functions.ParamKeys;
import stroom.security.api.SecurityContext;
import stroom.security.user.api.UserRefLookup;
import stroom.task.api.ExecutorProvider;
import stroom.task.api.TaskContextFactory;
import stroom.util.json.JsonUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.Clearable;
import stroom.util.shared.ErrorMessage;
import stroom.util.shared.NullSafe;
import stroom.util.shared.PermissionException;
import stroom.util.shared.ResultPage;
import stroom.util.shared.Severity;
import stroom.util.shared.UserRef;
import stroom.util.time.StroomDuration;

import com.google.common.base.Preconditions;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

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

@Singleton
public final class ResultStoreManager implements Clearable, HasResultStoreInfo {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ResultStoreManager.class);

    private final TaskContextFactory taskContextFactory;
    private final SecurityContext securityContext;
    private final ExecutorProvider executorProvider;
    private final Map<QueryKey, ResultStore> resultStoreMap;
    private final SearchProviderRegistry searchProviderRegistry;
    private final UserRefLookup userRefLookup;

    @Inject
    ResultStoreManager(final TaskContextFactory taskContextFactory,
                       final SecurityContext securityContext,
                       final ExecutorProvider executorProvider,
                       final SearchProviderRegistry searchProviderRegistry,
                       final UserRefLookup userRefLookup) {
        this.taskContextFactory = taskContextFactory;
        this.securityContext = securityContext;
        this.executorProvider = executorProvider;
        this.userRefLookup = userRefLookup;
        this.resultStoreMap = new ConcurrentHashMap<>();
        this.searchProviderRegistry = searchProviderRegistry;
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

    private boolean hasPermission(final ResultStore resultStore) {
        return securityContext.isAdmin() || Objects.equals(securityContext.getUserRef(), resultStore.getUserRef());
    }

    private void checkPermissions(final ResultStore resultStore) {
        if (!hasPermission(resultStore)) {
            throw new PermissionException(securityContext.getUserRef(),
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

    public Optional<ResultStore> getIfPresent(final QueryKey key) {
        return Optional.ofNullable(resultStoreMap.get(key));
    }

    public SearchResponse search(final SearchRequest searchRequest) {
        final RequestAndStore requestAndStore = getResultStore(searchRequest);
        final Map<String, ResultCreator> defaultResultCreators =
                requestAndStore.resultStore.makeDefaultResultCreators(requestAndStore.searchRequest);
        return search(requestAndStore, defaultResultCreators);
    }

    public SearchResponse search(final RequestAndStore requestAndStore,
                                 final Map<String, ResultCreator> resultCreatorMap) {
        // Perform search.
        final Supplier<SearchResponse> supplier =
                taskContextFactory.contextResult("Getting search results", taskContext -> {
                    taskContext.info(() -> "Creating search result");
                    return securityContext.useAsReadResult(() ->
                            doSearch(requestAndStore.resultStore, requestAndStore.searchRequest, resultCreatorMap));
                });
        final Executor executor = executorProvider.get();
        final CompletableFuture<SearchResponse> completableFuture = CompletableFuture.supplyAsync(supplier, executor);
        try {
            return completableFuture.get();
        } catch (final InterruptedException | ExecutionException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    public RequestAndStore getResultStore(final SearchRequest searchRequest) {
        if (LOGGER.isDebugEnabled()) {
            final String json = JsonUtil.writeValueAsString(searchRequest);
            LOGGER.debug("/search called with searchRequest:\n{}", json);
        }

        SearchRequest modifiedRequest = searchRequest;

        final UserRef userRef = securityContext.getUserRef();
        Objects.requireNonNull(userRef, "No user is logged in");

        final ResultStore resultStore;
        if (modifiedRequest.getKey() != null) {
            final QueryKey queryKey = modifiedRequest.getKey();
            final Optional<ResultStore> optionalResultStore =
                    getIfPresent(queryKey);

            final String message = "No active search found for key = " + queryKey;
            resultStore = optionalResultStore.orElseThrow(() ->
                    new RuntimeException(message));

            // Check user identity.
            if (!Objects.equals(resultStore.getUserRef(), userRef)) {
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
            final Optional<SearchProvider> optionalSearchProvider =
                    searchProviderRegistry.getSearchProvider(modifiedRequest.getQuery().getDataSource());
            final SearchProvider searchProvider = optionalSearchProvider
                    .orElseThrow(() ->
                            new RuntimeException("No search provider found for " +
                                                 searchRequest.getQuery().getDataSource().getType()));


            // Create a new search UUID.
            modifiedRequest = addQueryKey(modifiedRequest);

            // Add a param for `currentUser()`
            modifiedRequest = addCurrentUserParam(modifiedRequest);

            // Add partition time constraints to the query.
            modifiedRequest = addTimeRangeExpression(searchProvider.getTimeField(dataSourceRef), modifiedRequest);

            // Ensure we have a reference time so relative time expression work
            modifiedRequest = addReferenceTime(modifiedRequest);

            final SearchRequest finalModifiedRequest = modifiedRequest;
            final QueryKey queryKey = finalModifiedRequest.getKey();
            LOGGER.trace(() -> "get() " + queryKey);
            try {
                LOGGER.trace(() -> "create() " + queryKey);
                LOGGER.debug(() -> "Creating new store for key: " + queryKey);
                resultStore = searchProvider.createResultStore(finalModifiedRequest);
                resultStoreMap.put(queryKey, resultStore);
            } catch (final RuntimeException e) {
                LOGGER.debug(e.getMessage(), e);
                throw e;
            }
        }

        return new RequestAndStore(modifiedRequest, resultStore);
    }

    private SearchRequest addQueryKey(final SearchRequest searchRequest) {
        // Create a new search UUID.
        final String searchUuid = UUID.randomUUID().toString();
        LOGGER.debug(() -> "Creating new search UUID = " + searchUuid);
        final QueryKey queryKey = new QueryKey(searchUuid);
        return searchRequest.copy().key(queryKey).build();
    }

    private SearchRequest addCurrentUserParam(final SearchRequest searchRequest) {
        final List<Param> params = NullSafe.mutableList(searchRequest.getQuery().getParams());
        final UserRef userRef = securityContext.getUserRef();
        params.add(new Param(ParamKeys.CURRENT_USER_UUID, userRef.getUuid()));
        params.add(new Param(ParamKeys.CURRENT_USER, userRef.toDisplayString()));
        if (userRef.getDisplayName() != null) {
            params.add(new Param(ParamKeys.CURRENT_USER_DISPLAY_NAME, userRef.getDisplayName()));
        }
        if (userRef.getSubjectId() != null) {
            params.add(new Param(ParamKeys.CURRENT_USER_SUBJECT_ID, userRef.getSubjectId()));
        }
        if (userRef.getFullName() != null) {
            params.add(new Param(ParamKeys.CURRENT_USER_FULL_NAME, userRef.getFullName()));
        }

        return searchRequest
                .copy()
                .query(searchRequest
                        .getQuery()
                        .copy()
                        .params(params)
                        .build())
                .build();
    }

    private SearchRequest addTimeRangeExpression(final Optional<QueryField> optionalPartitionTimeField,
                                                 final SearchRequest searchRequest) {
        SearchRequest result = searchRequest;

        // Add the time range to the expression.
        if (optionalPartitionTimeField.isPresent()) {
            final QueryField partitionTimeField = optionalPartitionTimeField.get();
            final TimeRange timeRange = result.getQuery().getTimeRange();
            if (timeRange != null && (timeRange.getFrom() != null || timeRange.getTo() != null)) {
                final ExpressionOperator.Builder and = ExpressionOperator.builder().op(Op.AND);
                if (timeRange.getFrom() != null) {
                    and.addDateTerm(
                            partitionTimeField,
                            Condition.GREATER_THAN_OR_EQUAL_TO,
                            timeRange.getFrom());
                }
                if (timeRange.getTo() != null) {
                    and.addDateTerm(
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

    private SearchRequest addReferenceTime(final SearchRequest searchRequest) {
        DateTimeSettings dateTimeSettings = searchRequest.getDateTimeSettings();
        if (dateTimeSettings == null) {
            LOGGER.debug("Adding dateTimeSettings");
            dateTimeSettings = DateTimeSettings.builder().referenceTime(System.currentTimeMillis()).build();
            return searchRequest.copy().dateTimeSettings(dateTimeSettings).build();
        } else if (dateTimeSettings.getReferenceTime() == null) {
            LOGGER.debug("Adding referenceTime");
            dateTimeSettings = dateTimeSettings.copy().referenceTime(System.currentTimeMillis()).build();
            return searchRequest.copy().dateTimeSettings(dateTimeSettings).build();
        } else {
            return searchRequest;
        }
    }

    private SearchResponse doSearch(final ResultStore resultStore,
                                    final SearchRequest request,
                                    final Map<String, ResultCreator> resultCreatorMap) {
        Preconditions.checkNotNull(request);

        try {
            // Create a response from the data found so far, this could be complete/incomplete
            final SearchResponse response = resultStore.search(request, resultCreatorMap);

            LOGGER.trace(() -> getResponseInfoForLogging(request, response));

            return response;

        } catch (final RuntimeException e) {
            // Create an error response.
            final List<Result> results;
            if (request.getResultRequests() != null) {
                results = request.getResultRequests().stream()
                        .map(resultRequest -> new TableResult(
                                resultRequest.getComponentId(),
                                Collections.emptyList(),
                                Collections.emptyList(),
                                new OffsetRange(0, 0),
                                0L,
                                null,
                                null))
                        .collect(Collectors.toList());
            } else {
                results = Collections.emptyList();
            }

            return new SearchResponse(
                    request.getKey(),
                    Collections.emptyList(),
                    results,
                    null,
                    true,
                    Collections.singletonList(new ErrorMessage(Severity.ERROR, e.getMessage())));
        }
    }

    private String getResponseInfoForLogging(final SearchRequest request, final SearchResponse searchResponse) {
        final String resultInfo;

        if (searchResponse.getResults() != null) {
            resultInfo = "\n" + searchResponse.getResults().stream()
                    .map(result -> {
                        if (result instanceof final FlatResult flatResult) {
                            return LogUtil.message(
                                    "  FlatResult - componentId: {}, size: {}, ",
                                    flatResult.getComponentId(),
                                    flatResult.getSize());
                        } else if (result instanceof final TableResult tableResult) {
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
                searchResponse.getErrorMessages(),
                resultInfo);
    }

    public Boolean exists(final QueryKey queryKey) {
        if (LOGGER.isDebugEnabled()) {
            final String json = JsonUtil.writeValueAsString(queryKey);
            LOGGER.debug("/exists called with queryKey:\n{}", json);
        }

        final UserRef userRef = securityContext.getUserRef();
        Objects.requireNonNull(userRef, "No user is logged in");

        final Optional<ResultStore> optionalResultStore =
                getIfPresent(queryKey);

        if (optionalResultStore.isPresent()) {
            final ResultStore resultStore = optionalResultStore.get();
            return Objects.equals(resultStore.getUserRef(), userRef);
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
            final String json = JsonUtil.writeValueAsString(queryKey);
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
            final String json = JsonUtil.writeValueAsString(queryKey);
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
            LOGGER.debug(e::getMessage, e);
            return false;
        }
    }

    /**
     * Evicts any expired result stores.
     */
    public void evictExpiredElements() {
        securityContext.asProcessingUser(() -> {
            taskContextFactory.current().info(() -> "Evicting expired search responses");
            final Instant now = Instant.now();
            resultStoreMap.forEach((queryKey, resultStore) -> {
                try {
                    final ResultStoreSettings settings = resultStore.getResultStoreSettings();
                    final Instant createTime = resultStore.getCreationTime();
                    final Instant accessTime = resultStore.getLastAccessTime();
                    final UserRef userRef = resultStore.getUserRef();

                    if (settings.getStoreLifespan().getTimeToLive() != null &&
                        now.isAfter(createTime.plus(settings.getStoreLifespan().getTimeToLive()))) {
                        LOGGER.debug("Destroying resultStore for queryKey {} for user {} that is beyond the store TTL",
                                queryKey, resultStore);
                        destroyAndRemove(queryKey, resultStore);
                    } else if (settings.getStoreLifespan().getTimeToIdle() != null &&
                               now.isAfter(accessTime.plus(settings.getStoreLifespan().getTimeToIdle()))) {
                        LOGGER.debug("Destroying resultStore for queryKey {} for user {} that is beyond the store TTI",
                                queryKey, resultStore);
                        destroyAndRemove(queryKey, resultStore);
                    } else if (settings.getSearchProcessLifespan().getTimeToLive() != null &&
                               now.isAfter(createTime.plus(settings.getSearchProcessLifespan().getTimeToLive()))) {
                        LOGGER.debug("Terminating resultStore for queryKey {} for user {} that is beyond the " +
                                     "search process TTL", queryKey, resultStore);
                        resultStore.terminate();
                    } else if (settings.getSearchProcessLifespan().getTimeToIdle() != null &&
                               now.isAfter(accessTime.plus(settings.getSearchProcessLifespan().getTimeToIdle()))) {
                        LOGGER.debug("Terminating resultStore for queryKey {} for user {} that is beyond the " +
                                     "search process TTI", queryKey, resultStore);
                        resultStore.terminate();
                    } else {
                        final String ownerUuid = NullSafe.get(userRef, UserRef::getUuid);
                        final Optional<UserRef> optUserRef = userRefLookup.getByUuid(ownerUuid);
                        if (optUserRef.isEmpty()) {
                            // User has been deleted so destroy the store
                            LOGGER.debug("Destroying resultStore for queryKey {} for deleted user {}",
                                    queryKey, resultStore);
                            destroyAndRemove(queryKey, resultStore);
                        }
                    }
                } catch (final RuntimeException e) {
                    LOGGER.error(e::getMessage, e);
                }
            });
        });
    }

    @Override
    public void clear() {
        resultStoreMap.forEach(this::destroyAndRemove);
    }

    @Override
    public ResultPage<ResultStoreInfo> find(final FindResultStoreCriteria criteria) {
        final List<ResultStoreInfo> list = new ArrayList<>();
        resultStoreMap.forEach((queryKey, resultStore) -> {
            if (hasPermission(resultStore)) {
                final UserRef createUser = resultStore.getUserRef();
                list.add(new ResultStoreInfo(
                        resultStore.getSearchRequestSource(),
                        queryKey,
                        createUser,
                        resultStore.getCreationTime().toEpochMilli(),
                        resultStore.getNodeName(),
                        resultStore.getCoprocessors().getByteSize(),
                        resultStore.isComplete(),
                        resultStore.getSearchTaskProgress(),
                        getLifespanInfo(resultStore.getResultStoreSettings().getSearchProcessLifespan()),
                        getLifespanInfo(resultStore.getResultStoreSettings().getStoreLifespan())));
            } else {
                LOGGER.debug(() -> LogUtil.message("User {} has no perms on resultStore {} with owner: {}",
                        securityContext.getUserRef(),
                        resultStore,
                        resultStore.getUserRef().toDebugString()));
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

    public void put(final QueryKey queryKey, final ResultStore resultStore) {
        resultStoreMap.put(queryKey, resultStore);
    }


    // --------------------------------------------------------------------------------


    public record RequestAndStore(SearchRequest searchRequest, ResultStore resultStore) {

    }
}
