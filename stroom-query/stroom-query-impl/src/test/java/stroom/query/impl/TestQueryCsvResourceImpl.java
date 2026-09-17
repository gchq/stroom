/*
 * Copyright 2026 Crown Copyright
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
import stroom.query.api.Column;
import stroom.query.api.FlatResult;
import stroom.query.api.Row;
import stroom.query.api.TableResult;
import stroom.query.shared.QuerySearchRequest;
import stroom.util.shared.DefaultLocation;
import stroom.util.shared.ErrorMessage;
import stroom.util.shared.Severity;
import stroom.util.shared.TokenError;

import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * A search that fails comes back with null results and its diagnostics in tokenError/errorMessages, so
 * dereferencing the results gave the caller an HTTP 500 NPE instead of the reason their query was
 * rejected. See gh-5688.
 */
class TestQueryCsvResourceImpl {

    private static final String QUERY = "from \"Example Index\" take 3";

    /**
     * The reported case: a query that will not parse.
     */
    @Test
    void parseErrorIsABadRequestNamingTheProblem() {
        final DashboardSearchResponse response = failedResponse(
                new TokenError(new DefaultLocation(1, 6), new DefaultLocation(1, 9), "take"),
                "Expected one of [select, where] but got 'take'");

        assertThatThrownBy(() -> csvSearch(response))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Expected one of [select, where] but got 'take'")
                .hasMessageContaining("line 1")
                .hasMessageContaining("column 6");
    }

    /**
     * QueryServiceImpl returns the same null-results shape for any RuntimeException, not just parse
     * errors, e.g. an unresolvable data source. Those reached the same NPE.
     */
    @Test
    void anyOtherSearchFailureIsAlsoABadRequest() {
        final DashboardSearchResponse response = failedResponse(null, "Data source \"Nope\" not found");

        assertThatThrownBy(() -> csvSearch(response))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Data source \"Nope\" not found");
    }

    /**
     * A query is entitled to match nothing, so no results with nothing reported must not be turned
     * into an error. It is the same empty response as a search that returned no rows.
     */
    @Test
    void noResultsAndNoDiagnosticsIsNotAnError() {
        final DashboardSearchResponse response = failedResponse(null, null);

        assertThat(body(csvSearch(response))).isEmpty();
    }

    /**
     * SearchResponseMapper returns null for a null search response, so the response itself has to be
     * guarded as well as the results it carries.
     */
    @Test
    void noResponseAtAllIsNotAnError() {
        assertThat(body(csvSearch(null))).isEmpty();
    }

    /**
     * An empty list rather than null would have thrown IndexOutOfBoundsException, another 500.
     */
    @Test
    void emptyResultListIsHandledLikeNoResults() {
        final DashboardSearchResponse response = new DashboardSearchResponse(
                "node1", null, null, null, null, true, Collections.emptyList(),
                List.of(new ErrorMessage(Severity.ERROR, "Something went wrong")));

        assertThatThrownBy(() -> csvSearch(response))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Something went wrong");
    }

    @Test
    void successfulSearchStillReturnsCsv() {
        final TableResult tableResult = TableResult
                .builder()
                .componentId("table")
                .columns(List.of(Column.builder().id("0").name("Name").build()))
                .addRow(Row.builder().values(List.of("Alice")).build())
                .build();
        final DashboardSearchResponse response = new DashboardSearchResponse(
                "node1", null, null, null, null, true, List.of(tableResult), null);

        assertThat(body(csvSearch(response))).contains("Alice");
    }

    /**
     * A csv/search query always builds a table, so anything else means the server built something this
     * endpoint cannot render. That is an internal fault rather than a bad query, so unlike the other
     * failure paths it stays a 500 and must not be turned into a {@link BadRequestException}.
     */
    @Test
    void nonTableResultIsAServerErrorNamingBothTypes() {
        final DashboardSearchResponse response = new DashboardSearchResponse(
                "node1", null, null, null, null, true,
                List.of(FlatResult.builder().componentId("table").build()), null);

        assertThatThrownBy(() -> csvSearch(response))
                .isExactlyInstanceOf(RuntimeException.class)
                .hasMessageContaining("TableResult")
                .hasMessageContaining("FlatResult")
                .hasMessageContaining(QUERY);
    }

    /**
     * The defect 2 case: an incremental search that has not finished returns whatever rows it has so far.
     * Those are a subset of the matching data, so the caller has to be told, or truncated data looks
     * exactly like a complete result. See gh-5688.
     */
    @Test
    void partialResultsAreLabelledIncomplete() {
        final DashboardSearchResponse response = new DashboardSearchResponse(
                "node1", null, null, null, null, false, List.of(tableResult()), null);

        final Response actual = csvSearch(response);

        assertThat(body(actual)).contains("Alice");
        assertThat(header(actual, QueryCsvResource.CSV_COMPLETE_HEADER)).isEqualTo("false");
        assertThat(header(actual, QueryCsvResource.CSV_INCREMENTAL_HEADER)).isEqualTo("true");
    }

    /**
     * A complete search with no matches must be distinguishable from one that has not finished yet, which
     * an empty body alone cannot do.
     */
    @Test
    void completeEmptyResultIsDistinguishableFromUnfinished() {
        final DashboardSearchResponse complete = new DashboardSearchResponse(
                "node1", null, null, null, null, true, List.of(emptyTableResult()), null);
        final DashboardSearchResponse unfinished = new DashboardSearchResponse(
                "node1", null, null, null, null, false, List.of(emptyTableResult()), null);

        assertThat(body(csvSearch(complete))).isEmpty();
        assertThat(header(csvSearch(complete), QueryCsvResource.CSV_COMPLETE_HEADER)).isEqualTo("true");

        assertThat(body(csvSearch(unfinished))).isEmpty();
        assertThat(header(csvSearch(unfinished), QueryCsvResource.CSV_COMPLETE_HEADER)).isEqualTo("false");
    }

    /**
     * A non incremental search waits for the whole result set, so it must not be sent the 1s timeout that
     * suits an incremental one, or it would time out almost immediately.
     */
    @Test
    void nonIncrementalSearchWaitsRatherThanTimingOutImmediately() {
        final ArgumentCaptor<QuerySearchRequest> captor = ArgumentCaptor.forClass(QuerySearchRequest.class);
        final QueryService queryService = mockQueryService(new DashboardSearchResponse(
                "node1", null, null, null, null, true, List.of(tableResult()), null));

        resource(queryService).csvSearch(QUERY, 0, 100, false, null);

        Mockito.verify(queryService).search(captor.capture());
        assertThat(captor.getValue().isIncremental()).isFalse();
        assertThat(captor.getValue().getTimeout()).isGreaterThan(1_000L);
    }

    @Test
    void callerSuppliedTimeoutIsHonoured() {
        final ArgumentCaptor<QuerySearchRequest> captor = ArgumentCaptor.forClass(QuerySearchRequest.class);
        final QueryService queryService = mockQueryService(new DashboardSearchResponse(
                "node1", null, null, null, null, true, List.of(tableResult()), null));

        resource(queryService).csvSearch(QUERY, 0, 100, true, 250L);

        Mockito.verify(queryService).search(captor.capture());
        assertThat(captor.getValue().getTimeout()).isEqualTo(250L);
    }

    private TableResult tableResult() {
        return TableResult
                .builder()
                .componentId("table")
                .columns(List.of(Column.builder().id("0").name("Name").build()))
                .addRow(Row.builder().values(List.of("Alice")).build())
                .build();
    }

    private TableResult emptyTableResult() {
        return TableResult
                .builder()
                .componentId("table")
                .columns(List.of(Column.builder().id("0").name("Name").build()))
                .build();
    }

    private String body(final Response response) {
        assertThat(response.getStatus()).isEqualTo(Status.OK.getStatusCode());
        return (String) response.getEntity();
    }

    private String header(final Response response, final String name) {
        return response.getHeaderString(name);
    }

    private DashboardSearchResponse failedResponse(final TokenError tokenError, final String message) {
        return new DashboardSearchResponse(
                "node1",
                null,
                null,
                null,
                tokenError,
                true,
                // What QueryServiceImpl supplies when the search throws.
                null,
                message == null
                        ? null
                        : List.of(new ErrorMessage(Severity.ERROR, message)));
    }

    private Response csvSearch(final DashboardSearchResponse response) {
        return resource(mockQueryService(response)).csvSearch(QUERY, 0, 100, true, null);
    }

    private QueryService mockQueryService(final DashboardSearchResponse response) {
        final QueryService queryService = Mockito.mock(QueryService.class);
        // null node means execute locally rather than making a remote REST call.
        Mockito.when(queryService.getBestNode(Mockito.isNull(), Mockito.any(QuerySearchRequest.class)))
                .thenReturn(null);
        Mockito.when(queryService.search(Mockito.any(QuerySearchRequest.class))).thenReturn(response);
        return queryService;
    }

    private QueryCsvResourceImpl resource(final QueryService queryService) {
        // A real service rather than a mock, as the failure handling under test lives in it.
        final CsvSearchService csvSearchService = new CsvSearchService(() -> queryService, () -> null);
        return new QueryCsvResourceImpl(() -> csvSearchService);
    }
}
