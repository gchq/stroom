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

package stroom.ai.impl.tools.query;

import stroom.dashboard.shared.DashboardSearchResponse;
import stroom.query.api.TableResult;
import stroom.util.shared.ErrorMessage;
import stroom.util.shared.Severity;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TestSearchProblems {

    @Test
    void errorsAndWarningsAreToldApartBySeverityFromTheResponseAndItsResults() {
        final TableResult table = new TableResult("table", null, null, null, 0L,
                List.of("A shard couldn't be read"), List.of(new ErrorMessage(Severity.WARNING, "Slow shard")));
        final List<ErrorMessage> messages = List.of(
                new ErrorMessage(Severity.ERROR, "Unknown field 'Hst'"),
                new ErrorMessage(Severity.FATAL_ERROR, "The join table is full"),
                new ErrorMessage(Severity.WARNING, "One row matched more than 1,000 rows"),
                new ErrorMessage(Severity.INFO, " "));
        final DashboardSearchResponse response = new DashboardSearchResponse(null, null, null,
                List.of("Unknown field 'Hst'"), null, true, List.of(table), messages);

        // Each error once: the same one in both lists isn't said twice.
        assertThat(SearchProblems.errors(response))
                .containsExactly("Unknown field 'Hst'", "The join table is full", "A shard couldn't be read");
        // Blank messages are left out.
        assertThat(SearchProblems.warnings(response))
                .containsExactly("One row matched more than 1,000 rows", "Slow shard");
    }

    @Test
    void aResponseWithNothingWrongHasNoErrorsOrWarnings() {
        final DashboardSearchResponse response = new DashboardSearchResponse(null, null, null, null, null, true,
                null, null);

        assertThat(SearchProblems.errors(response)).isEmpty();
        assertThat(SearchProblems.warnings(response)).isEmpty();
    }
}
