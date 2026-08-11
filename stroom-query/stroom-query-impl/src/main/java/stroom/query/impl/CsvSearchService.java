/*
 * Copyright 2016-2026 Crown Copyright
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

import stroom.dashboard.shared.DashboardSearchResponse;
import stroom.node.api.NodeService;
import stroom.query.api.OffsetRange;
import stroom.query.api.Result;
import stroom.query.api.TableResult;
import stroom.query.shared.QueryContext;
import stroom.query.shared.QueryResource;
import stroom.query.shared.QuerySearchRequest;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.rest.RestUtil;
import stroom.util.shared.ErrorMessage;
import stroom.util.shared.NullSafe;
import stroom.util.shared.ResourcePaths;
import stroom.util.shared.TokenError;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.ws.rs.client.Entity;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The CSV search itself, shared by the {@link QueryCsvResource} endpoint and the deprecated
 * {@code csvSearch} method on {@link QueryResource}, so that both report failures the same way.
 * See gh-5688.
 */
class CsvSearchService {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(CsvSearchService.class);

    // Matches the previous behaviour of QuerySearchRequest's builder default.
    private static final Duration DEFAULT_INCREMENTAL_CSV_TIMEOUT = Duration.ofSeconds(1);
    // Matches SearchResponseCreator's own fall back for a synchronous search.
    private static final Duration DEFAULT_NON_INCREMENTAL_CSV_TIMEOUT = Duration.ofMinutes(5);

    private final Provider<QueryService> queryServiceProvider;
    private final Provider<NodeService> nodeServiceProvider;

    @Inject
    CsvSearchService(final Provider<QueryService> queryServiceProvider,
                     final Provider<NodeService> nodeServiceProvider) {
        this.queryServiceProvider = queryServiceProvider;
        this.nodeServiceProvider = nodeServiceProvider;
    }

    CsvSearchResult search(final String query,
                           final int offset,
                           final int length,
                           final boolean incremental,
                           final Long timeout) {
        RestUtil.requireNonNull(query, "query not supplied");
        try {
            final QuerySearchRequest request = QuerySearchRequest.builder()
                    .requestedRange(OffsetRange.builder().offset(offset).length(length).build())
                    .queryContext(QueryContext.builder().build())
                    .query(query)
                    .incremental(incremental)
                    .timeout(effectiveTimeout(incremental, timeout))
                    .build();
            final DashboardSearchResponse response = search(request);

            if (response == null || NullSafe.isEmptyCollection(response.getResults())) {
                // A search that failed, e.g. a query that will not parse, comes back with no results and
                // the diagnostics in tokenError/errorMessages, so report those rather than dereferencing
                // null. Not just parse errors: QueryServiceImpl returns the same shape for any
                // RuntimeException, e.g. an unresolvable data source. See gh-5688.
                final String failure = describeFailure(response);
                if (failure != null) {
                    throw RestUtil.badRequest(failure);
                }
                // No results is not a failure. A query is entitled to match nothing, and an incremental
                // search may simply not have found anything yet, which the result below conveys.
                return result(null, request, response);
            }

            final Result firstResult = response.getResults().getFirst();
            if (!(firstResult instanceof final TableResult tableResult)) {
                throw new RuntimeException(LogUtil.message(
                        "Expected a {} from query '{}' but got {}",
                        TableResult.class.getSimpleName(),
                        query,
                        firstResult.getClass().getSimpleName()));
            }

            final String csv = tableResult.getColumns().isEmpty() || tableResult.getRows().isEmpty()
                    ? null
                    : new TableResultCsvWriter(tableResult).toCsv();
            return result(csv, request, response);
        } catch (final Exception e) {
            LOGGER.debug(e::getMessage, e);
            throw e;
        }
    }

    /**
     * Mirrors {@link QueryResourceImpl#search(String, QuerySearchRequest)} so that a CSV search is routed
     * to a node the same way any other search is. Kept here rather than delegated to the resource so that
     * the resource can depend on this service without the two depending on each other.
     */
    private DashboardSearchResponse search(final QuerySearchRequest request) {
        // If there is no best node then execute locally.
        final String node = queryServiceProvider.get().getBestNode(null, request);
        if (node == null) {
            return queryServiceProvider.get().search(request);
        }

        return nodeServiceProvider.get()
                .remoteRestResult(
                        node,
                        DashboardSearchResponse.class,
                        () -> ResourcePaths.buildAuthenticatedApiPath(
                                QueryResource.BASE_PATH,
                                QueryResource.SEARCH_PATH_PART,
                                node),
                        () -> queryServiceProvider.get().search(request),
                        builder -> builder.post(Entity.json(request)));
    }

    /**
     * An incremental search returns whatever it has when the timeout expires, so a short timeout gets the
     * caller a quick, probably partial, answer. A non incremental search is waiting for the whole result
     * set, so it needs long enough to get it; the request itself timing out is the caller's risk to take.
     * {@link QuerySearchRequest} cannot express "no timeout supplied", so the server side default can
     * never apply and we have to supply one here.
     */
    private long effectiveTimeout(final boolean incremental, final Long timeout) {
        if (timeout != null) {
            return timeout;
        }
        return incremental
                ? DEFAULT_INCREMENTAL_CSV_TIMEOUT.toMillis()
                : DEFAULT_NON_INCREMENTAL_CSV_TIMEOUT.toMillis();
    }

    private CsvSearchResult result(final String csv,
                                   final QuerySearchRequest request,
                                   final DashboardSearchResponse response) {
        // No response at all means we know nothing, so we cannot claim the search completed.
        final boolean complete = response != null && response.isComplete();
        return new CsvSearchResult(csv, request.isIncremental(), complete);
    }

    /**
     * @return The reason a search that returned no results failed, or null if it did not report one.
     * The query being at fault, e.g. one that will not parse, is a bad request rather than a server
     * error, so the caller is given the diagnostics the search gathered.
     */
    private String describeFailure(final DashboardSearchResponse response) {
        if (response == null) {
            return null;
        }

        final List<String> messages = new ArrayList<>();
        NullSafe.list(response.getErrorMessages())
                .stream()
                .map(ErrorMessage::getMessage)
                .filter(Objects::nonNull)
                .forEach(messages::add);
        // Older nodes in a mixed version cluster may only populate the deprecated errors field.
        if (messages.isEmpty()) {
            NullSafe.list(response.getErrors())
                    .stream()
                    .filter(Objects::nonNull)
                    .forEach(messages::add);
        }

        final TokenError tokenError = response.getTokenError();
        if (tokenError != null && tokenError.getFrom() != null) {
            messages.add(LogUtil.message("Error at line {}, column {}",
                    tokenError.getFrom().getLineNo(),
                    tokenError.getFrom().getColNo()));
        }

        return messages.isEmpty()
                ? null
                : String.join("\n", messages);
    }


    // --------------------------------------------------------------------------------


    /**
     * @param csv         The CSV found, or null if the search found no rows.
     * @param incremental Whether the search ran incrementally, i.e. whether the CSV can be partial.
     * @param complete    Whether the search finished. False means the CSV is a subset of the matching data.
     */
    record CsvSearchResult(String csv, boolean incremental, boolean complete) {

    }
}
