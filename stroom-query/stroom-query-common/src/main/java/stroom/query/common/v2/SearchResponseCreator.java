/*
 * Copyright 2016-2025 Crown Copyright
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

import stroom.query.api.QueryKey;
import stroom.query.api.Result;
import stroom.query.api.ResultRequest;
import stroom.query.api.ResultRequest.Fetch;
import stroom.query.api.ResultRequest.ResultStyle;
import stroom.query.api.SearchRequest;
import stroom.query.api.SearchResponse;
import stroom.query.common.v2.format.FormatterFactory;
import stroom.query.language.functions.ExpressionContext;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.ErrorMessage;
import stroom.util.shared.Severity;
import stroom.util.string.ExceptionStringUtil;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

public class SearchResponseCreator {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(SearchResponseCreator.class);

    private static final Duration FALL_BACK_DEFAULT_TIMEOUT = Duration.ofMinutes(5);

    private final SizesProvider sizesProvider;
    private final ResultStore store;
    private final ExpressionContext expressionContext;
    private final MapDataStoreFactory mapDataStoreFactory;
    private final ExpressionPredicateFactory expressionPredicateFactory;

    private final Map<String, ResultCreator> cachedResultCreators = new HashMap<>();

    /**
     * @param store The underlying store to use for creating the search responses.
     */
    public SearchResponseCreator(final SizesProvider sizesProvider,
                                 final ResultStore store,
                                 final ExpressionContext expressionContext,
                                 final MapDataStoreFactory mapDataStoreFactory,
                                 final ExpressionPredicateFactory expressionPredicateFactory) {
        this.sizesProvider = sizesProvider;
        this.store = Objects.requireNonNull(store);
        this.expressionContext = expressionContext;
        this.mapDataStoreFactory = mapDataStoreFactory;
        this.expressionPredicateFactory = expressionPredicateFactory;
    }

    /**
     * @param throwable List of errors to add to the {@link SearchResponse}
     * @return An empty {@link SearchResponse} with the passed error messages
     */
    private static SearchResponse createErrorResponse(final QueryKey queryKey,
                                                      final ResultStore store,
                                                      final Throwable throwable) {
        Objects.requireNonNull(store);
        Objects.requireNonNull(throwable);

        final List<ErrorMessage> errors = new ArrayList<>();

        LOGGER.debug(throwable::getMessage, throwable);
        errors.add(new ErrorMessage(Severity.ERROR, ExceptionStringUtil.getMessage(throwable)));

        if (store.getErrors() != null) {
            errors.addAll(store.getErrors());
        }
        return new SearchResponse(
                queryKey,
                null,
                null,
                null,
                false,
                errors);
    }

    /**
     * Stop searching and destroy any stored data.
     */
    public void destroy() {
        LOGGER.trace(() -> "destroy()", new RuntimeException("destroy"));
        store.destroy();
    }

    /**
     * Build a {@link SearchResponse} from the passed {@link SearchRequest}.
     *
     * @param searchRequest The {@link SearchRequest} containing the query terms and the result requests
     * @return A {@link SearchResponse} object that may be one of:
     * <ul>
     * <li>A 'complete' {@link SearchResponse} containing all the data requested</li>
     * <li>An incomplete {@link SearchResponse} containing none, some or all of the data requested, i.e the
     * currently know results at the point the request is made.</li>
     * <li>An empty response with an error message indicating the request timed out waiting for a 'complete'
     * result set. This only applies to non-incremental queries.</li>
     * <li>An empty response with a different error message. This will happen when some unexpected error has
     * occurred while assembling the {@link SearchResponse}</li>
     * </ul>
     */
    public SearchResponse create(final SearchRequest searchRequest,
                                 final Map<String, ResultCreator> resultCreatorMap) {
        final boolean didSearchComplete;

        if (!store.isComplete()) {
            LOGGER.debug(() -> "Store not complete so will wait for completion or timeout");
            try {
                final Duration effectiveTimeout = getEffectiveTimeout(searchRequest);

                // Block and wait for the store to notify us of its completion/termination, or if the wait is too long
                // we will time out
                LOGGER.debug(() -> "Waiting: effectiveTimeout=" + effectiveTimeout);
                didSearchComplete = store.awaitCompletion(effectiveTimeout.toMillis(), TimeUnit.MILLISECONDS);
                LOGGER.debug(() -> "Finished waiting: effectiveTimeout=" +
                                   effectiveTimeout +
                                   ", didSearchComplete=" +
                                   didSearchComplete);

                if (!didSearchComplete && !searchRequest.incremental()) {
                    // Search didn't complete non-incremental search in time so return a timed out error response
                    return createErrorResponse(
                            searchRequest.getKey(),
                            store,
                            new RuntimeException(SearchResponse.TIMEOUT_MESSAGE + effectiveTimeout));
                }

            } catch (final InterruptedException e) {
                LOGGER.trace(e::getMessage, e);
                // Keep interrupting this thread.
                Thread.currentThread().interrupt();

                return createErrorResponse(
                        searchRequest.getKey(),
                        store,
                        new RuntimeException("Thread was interrupted before the search could complete"));
            }
        }

        // We will only get here if the search is complete, or it is an incremental search in which case we don't care
        // about completion state. Therefore, assemble whatever results we currently have
        try {
            // Get completion state before we get results.
            final boolean complete = store.isComplete();

            final List<Result> res = LOGGER.logDurationIfTraceEnabled(() ->
                    getResults(searchRequest, resultCreatorMap), "Getting results");
            LOGGER.debug(() -> "Returning new SearchResponse with results: " +
                               (res.isEmpty()
                                       ? "null"
                                       : res.size()) +
                               ", complete: " +
                               complete +
                               ", isComplete: " +
                               store.isComplete());

            List<Result> results = res;

            if (results.isEmpty()) {
                results = null;
            }

            final List<ErrorMessage> errors = buildCompoundErrorList(store, results);

            final SearchResponse searchResponse = new SearchResponse(
                    searchRequest.getKey(),
                    store.getHighlights(),
                    results,
                    null,
                    complete,
                    errors);

            if (complete) {
                SearchDebugUtil.writeRequest(searchRequest, false);
                SearchDebugUtil.writeResponse(searchResponse, false);
            }

            return searchResponse;

        } catch (final RuntimeException e) {
            LOGGER.error(() -> "Error getting search results for query " + searchRequest.getKey().toString(), e);

            return createErrorResponse(
                    searchRequest.getKey(),
                    store,
                    new RuntimeException("Error getting search results: [" +
                                         e.getMessage() +
                                         "], see service's logs for details", e));
        }
    }

    private List<ErrorMessage> buildCompoundErrorList(final ResultStore store, final List<Result> results) {
        final List<ErrorMessage> errors = new ArrayList<>();

        if (store.getErrors() != null) {
            errors.addAll(store.getErrors());
        }

        if (results != null) {
            errors.addAll(results.stream()
                    .map(Result::getErrorMessages)
                    .filter(Objects::nonNull)
                    .flatMap(Collection::stream)
                    .toList());
        }

        return errors.isEmpty()
                ? null
                : errors;
    }

    private Duration getEffectiveTimeout(final SearchRequest searchRequest) {
        final Duration requestedTimeout = searchRequest.getTimeout() == null
                ? null
                : Duration.ofMillis(searchRequest.getTimeout());
        if (requestedTimeout != null) {
            return requestedTimeout;
        } else if (searchRequest.incremental()) {
            // No timeout supplied so they want a response immediately
            return Duration.ZERO;
        }

        // This is synchronous so just use the service's default.
        return FALL_BACK_DEFAULT_TIMEOUT;
    }

    private List<Result> getResults(final SearchRequest searchRequest,
                                    final Map<String, ResultCreator> resultCreatorMap) {

        // Provide results if this search is incremental or the search is complete.
        final List<Result> results = new ArrayList<>(searchRequest.getResultRequests().size());
        // Copy the requested portion of the result cache into the result.
        for (final ResultRequest resultRequest : searchRequest.getResultRequests()) {
            final ResultCreator resultCreator = resultCreatorMap.get(resultRequest.getComponentId());
            if (resultCreator != null) {
                final String componentId = resultRequest.getComponentId();

                // Only deliver data to components that actually want it.
                final Fetch fetch = resultRequest.getFetch();
                if (!Fetch.NONE.equals(fetch)) {
                    final DataStore dataStore = store.getData(resultRequest.getComponentId());
                    final Result result = resultCreator.create(dataStore, resultRequest);
                    if (result != null) {
                        // Either we haven't returned a result before or this result
                        // is different from the one delivered previously so deliver it to the client.
                        results.add(result);
                        LOGGER.debug(() -> "Delivering " + result + " for " + componentId);
                    }
                }
            }
        }
        return results;
    }

    public Map<String, ResultCreator> makeDefaultResultCreators(final SearchRequest searchRequest) {
        final Map<String, ResultCreator> map = new HashMap<>();
        for (final ResultRequest resultRequest : searchRequest.getResultRequests()) {
            final String componentId = resultRequest.getComponentId();
            final DataStore dataStore = store.getData(componentId);
            if (dataStore != null) {
                try {
                    final ResultCreator resultCreator = getDefaultResultCreator(
                            searchRequest,
                            componentId,
                            expressionContext,
                            resultRequest,
                            true);
                    map.put(componentId, resultCreator);
                } catch (final RuntimeException e) {
                    LOGGER.error(e::getMessage, e);
                }
            }
        }
        return map;
    }

    private ResultCreator getDefaultResultCreator(final SearchRequest searchRequest,
                                                  final String componentId,
                                                  final ExpressionContext expressionContext,
                                                  final ResultRequest resultRequest,
                                                  final boolean cacheLastResult) {
        return cachedResultCreators.computeIfAbsent(componentId, k -> {
            final ResultCreator resultCreator;
            try {
                if (ResultStyle.TABLE.equals(resultRequest.getResultStyle())) {
                    final FormatterFactory formatterFactory = new FormatterFactory(searchRequest.getDateTimeSettings());
                    resultCreator = new TableResultCreator(
                            formatterFactory,
                            expressionPredicateFactory,
                            cacheLastResult);

                } else if (ResultStyle.VIS.equals(resultRequest.getResultStyle())) {
                    final FlatResultCreator flatResultCreator = new FlatResultCreator(
                            mapDataStoreFactory,
                            searchRequest,
                            componentId,
                            expressionContext,
                            null,
                            null,
                            expressionPredicateFactory,
                            sizesProvider.getDefaultMaxResultsSizes(),
                            cacheLastResult);
                    resultCreator = new VisResultCreator(flatResultCreator);

                } else if (ResultStyle.QL_VIS.equals(resultRequest.getResultStyle())) {
                    final FlatResultCreator flatResultCreator = new FlatResultCreator(
                            mapDataStoreFactory,
                            searchRequest,
                            componentId,
                            expressionContext,
                            null,
                            null,
                            expressionPredicateFactory,
                            sizesProvider.getDefaultMaxResultsSizes(),
                            cacheLastResult);
                    resultCreator = new QLVisResultCreator(flatResultCreator, resultRequest
                            .getMappings()
                            .getLast()
                            .getVisSettings());

                } else {
                    resultCreator = new FlatResultCreator(
                            mapDataStoreFactory,
                            searchRequest,
                            componentId,
                            expressionContext,
                            null,
                            null,
                            expressionPredicateFactory,
                            sizesProvider.getDefaultMaxResultsSizes(),
                            cacheLastResult);
                }
            } catch (final RuntimeException e) {
                throw new RuntimeException(e.getMessage());
            }

            return resultCreator;
        });
    }
}
