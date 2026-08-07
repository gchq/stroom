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
import stroom.query.api.Column;
import stroom.query.api.Row;
import stroom.query.api.TableResult;
import stroom.query.shared.QuerySearchRequest;
import stroom.util.shared.DefaultLocation;
import stroom.util.shared.ErrorMessage;
import stroom.util.shared.Severity;
import stroom.util.shared.TokenError;

import jakarta.ws.rs.BadRequestException;
import org.junit.jupiter.api.Test;
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
class TestQueryResourceImplCsvSearch {

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
     * Without diagnostics there is nothing for the caller to act on, so blaming their request would be
     * wrong; that stays a server error.
     */
    @Test
    void noResultsAndNoDiagnosticsIsNotBlamedOnTheCaller() {
        final DashboardSearchResponse response = failedResponse(null, null);

        assertThatThrownBy(() -> csvSearch(response))
                .isInstanceOf(RuntimeException.class)
                .isNotInstanceOf(BadRequestException.class);
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

        assertThat(csvSearch(response)).contains("Alice");
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

    private String csvSearch(final DashboardSearchResponse response) {
        final QueryService queryService = Mockito.mock(QueryService.class);
        // null node means execute locally rather than making a remote REST call.
        Mockito.when(queryService.getBestNode(Mockito.isNull(), Mockito.any(QuerySearchRequest.class)))
                .thenReturn(null);
        Mockito.when(queryService.search(Mockito.any(QuerySearchRequest.class))).thenReturn(response);

        final QueryResourceImpl resource = new QueryResourceImpl(
                () -> null,
                () -> queryService,
                null, null, null, null, null, null, null, null);
        return resource.csvSearch(QUERY, 0, 100);
    }
}
