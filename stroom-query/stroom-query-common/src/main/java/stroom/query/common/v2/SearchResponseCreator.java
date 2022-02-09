/*
 * Copyright 2017 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.query.common.v2;

import stroom.query.api.v2.DateTimeSettings;
import stroom.query.api.v2.QueryKey;
import stroom.query.api.v2.Result;
import stroom.query.api.v2.ResultRequest;
import stroom.query.api.v2.ResultRequest.Fetch;
import stroom.query.api.v2.ResultRequest.ResultStyle;
import stroom.query.api.v2.SearchRequest;
import stroom.query.api.v2.SearchResponse;
import stroom.query.api.v2.TableResult;
import stroom.query.common.v2.format.FieldFormatter;
import stroom.query.common.v2.format.FormatterFactory;
import stroom.query.util.LambdaLogger;
import stroom.query.util.LambdaLoggerFactory;
import stroom.util.string.ExceptionStringUtil;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class SearchResponseCreator {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(SearchResponseCreator.class);

    private static final Duration FALL_BACK_DEFAULT_TIMEOUT = Duration.ofMinutes(5);

    private final SizesProvider sizesProvider;
    private final Store store;

    private final Map<String, ResultCreator> cachedResultCreators = new HashMap<>();

    // Cache the last results for each component.
    private final Map<String, Result> resultCache = new HashMap<>();

    /**
     * @param store The underlying store to use for creating the search responses.
     */
    public SearchResponseCreator(final SizesProvider sizesProvider,
                                 final Store store) {
        this.sizesProvider = sizesProvider;
        this.store = Objects.requireNonNull(store);
    }

    /**
     * @param throwable List of errors to add to the {@link SearchResponse}
     * @return An empty {@link SearchResponse} with the passed error messages
     */
    private static SearchResponse createErrorResponse(final Store store, final Throwable throwable) {
        Objects.requireNonNull(store);
        Objects.requireNonNull(throwable);

        final List<String> errors = new ArrayList<>();

        LOGGER.debug(throwable::getMessage, throwable);
        errors.add(ExceptionStringUtil.getMessage(throwable));

        if (store.getErrors() != null) {
            errors.addAll(store.getErrors());
        }
        return new SearchResponse(
                null,
                null,
                errors,
                false);
    }

    public boolean keepAlive() {
        LOGGER.trace(() -> "keepAlive()", new RuntimeException("keepAlive"));
        return true;
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
    public SearchResponse create(final SearchRequest searchRequest) {
        final boolean didSearchComplete;

        if (!store.isComplete()) {
            LOGGER.debug(() -> "Store not complete so will wait for completion or timeout");
            try {
                final Duration effectiveTimeout = getEffectiveTimeout(searchRequest);

                // Block and wait for the store to notify us of its completion/termination, or if the wait is too long
                // we will timeout
                LOGGER.debug(() -> "Waiting: effectiveTimeout=" + effectiveTimeout);
                didSearchComplete = store.awaitCompletion(effectiveTimeout.toMillis(), TimeUnit.MILLISECONDS);
                LOGGER.debug(() -> "Finished waiting: effectiveTimeout=" +
                        effectiveTimeout +
                        ", didSearchComplete=" +
                        didSearchComplete);

                if (!didSearchComplete && !searchRequest.incremental()) {
                    // Search didn't complete non-incremental search in time so return a timed out error response
                    return createErrorResponse(
                            store,
                            new RuntimeException(SearchResponse.TIMEOUT_MESSAGE + effectiveTimeout));
                }

            } catch (InterruptedException e) {
                LOGGER.trace(e::getMessage, e);
                // Keep interrupting this thread.
                Thread.currentThread().interrupt();

                return createErrorResponse(
                        store,
                        new RuntimeException("Thread was interrupted before the search could complete"));
            }
        }

        // We will only get here if the search is complete or it is an incremental search in which case we don't care
        // about completion state. Therefore, assemble whatever results we currently have
        try {
            // Get completion state before we get results.
            final boolean complete = store.isComplete();

            final List<Result> res = LOGGER.logDurationIfTraceEnabled(() ->
                    getResults(searchRequest), "Getting results");
            LOGGER.debug(() -> "Returning new SearchResponse with results: " +
                    (res.size() == 0
                            ? "null"
                            : res.size()) +
                    ", complete: " +
                    complete +
                    ", isComplete: " +
                    store.isComplete());

            List<Result> results = res;

            if (results.size() == 0) {
                results = null;
            }

            final List<String> errors = buildCompoundErrorList(store, results);

            final SearchResponse searchResponse = new SearchResponse(
                    store.getHighlights(),
                    results,
                    errors,
                    complete);

            if (complete) {
                SearchDebugUtil.writeRequest(searchRequest, false);
                SearchDebugUtil.writeResponse(searchResponse, false);
            }

            return searchResponse;

        } catch (final RuntimeException e) {
            LOGGER.error(() -> "Error getting search results for query " + searchRequest.getKey().toString(), e);

            return createErrorResponse(store,
                    new RuntimeException("Error getting search results: [" +
                            e.getMessage() +
                            "], see service's logs for details", e));
        }
    }

    private List<String> buildCompoundErrorList(final Store store, final List<Result> results) {
        final List<String> errors = new ArrayList<>();

        if (store.getErrors() != null) {
            errors.addAll(store.getErrors());
        }

        if (results != null) {
            errors.addAll(results.stream()
                    .map(Result::getErrors)
                    .filter(Objects::nonNull)
                    .flatMap(Collection::stream)
                    .collect(Collectors.toList()));
        }

        return errors.isEmpty()
                ? null
                : errors;
    }

    private Duration getEffectiveTimeout(final SearchRequest searchRequest) {
        Duration requestedTimeout = searchRequest.getTimeout() == null
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

    private List<Result> getResults(final SearchRequest searchRequest) {

        // Provide results if this search is incremental or the search is complete.
        List<Result> results = new ArrayList<>(searchRequest.getResultRequests().size());
        // Copy the requested portion of the result cache into the result.
        for (final ResultRequest resultRequest : searchRequest.getResultRequests()) {
            final String componentId = resultRequest.getComponentId();

            // Only deliver data to components that actually want it.
            final Fetch fetch = resultRequest.getFetch();
            if (!Fetch.NONE.equals(fetch)) {
                final Result result = getResult(searchRequest, resultRequest);

                if (result != null) {
                    if (fetch == null || Fetch.ALL.equals(fetch)) {
                        // If the fetch option has not been set or is set to ALL we deliver the full result.
                        results.add(result);
                        LOGGER.debug(() -> "Delivering " + result + " for " + componentId);

                    } else if (Fetch.CHANGES.equals(fetch)) {
                        // Cache the new result and get the previous one.
                        final Result lastResult = resultCache.put(componentId, result);

                        // See if we have delivered an identical result before so we
                        // don't send more data to the client than we need to.
                        if (!result.equals(lastResult)) {
                            //
                            // CODE TO HELP DEBUGGING.
                            //

                            // try {
                            // if (lastComponentResult instanceof
                            // ChartResult) {
                            // final ChartResult lr = (ChartResult)
                            // lastComponentResult;
                            // final ChartResult cr = (ChartResult)
                            // componentResult;
                            // final File dir = new
                            // File(FileUtil.getTempDir());
                            // StreamUtil.stringToFile(lr.getJSON(), new
                            // File(dir, "last.json"));
                            // StreamUtil.stringToFile(cr.getJSON(), new
                            // File(dir, "current.json"));
                            // }
                            // } catch (final RuntimeException e) {
                            // LOGGER.error(e.getMessage(), e);
                            // }

                            // Either we haven't returned a result before or this result
                            // is different from the one delivered previously so deliver it to the client.
                            results.add(result);
                            LOGGER.debug(() -> "Delivering " + result + " for " + componentId);
                        }
                    }
                }
            }
        }
        return results;
    }

    private Result getResult(final SearchRequest searchRequest,
                             final ResultRequest resultRequest) {
        final String componentId = resultRequest.getComponentId();
        Result result = null;

        final DataStore dataStore = store.getData(componentId);
        if (dataStore != null) {
            try {
                final ResultCreator resultCreator = getResultCreator(
                        searchRequest.getKey(),
                        componentId,
                        resultRequest,
                        searchRequest.getDateTimeSettings());
                if (resultCreator != null) {
                    result = resultCreator.create(dataStore, resultRequest);
                }
            } catch (final RuntimeException e) {
                result = new TableResult(componentId, null, null, null, 0,
                        Collections.singletonList(ExceptionStringUtil.getMessage(e)));
            }
        }

        return result;
    }

    private ResultCreator getResultCreator(final QueryKey queryKey,
                                           final String componentId,
                                           final ResultRequest resultRequest,
                                           final DateTimeSettings dateTimeSettings) {
        return cachedResultCreators.computeIfAbsent(componentId, k -> {
            ResultCreator resultCreator;
            try {
                if (ResultStyle.TABLE.equals(resultRequest.getResultStyle())) {
                    final FieldFormatter fieldFormatter =
                            new FieldFormatter(
                                    new FormatterFactory(dateTimeSettings));
                    resultCreator = new TableResultCreator(fieldFormatter, sizesProvider.getDefaultMaxResultsSizes());
                } else {
                    resultCreator = new FlatResultCreator(
                            new MapDataStoreFactory(),
                            queryKey,
                            componentId,
                            resultRequest,
                            null,
                            null,
                            sizesProvider.getDefaultMaxResultsSizes());
                }
            } catch (final RuntimeException e) {
                throw new RuntimeException(e.getMessage());
            }

            return resultCreator;
        });
    }
}
