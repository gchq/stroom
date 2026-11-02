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

package stroom.mcp.impl.tools;

import stroom.ai.impl.tools.StreamSummaries;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TestIndexingInputs {

    @Test
    void eachNamedDataEntryIsCountedAsWellAsItsPath() {
        // Seen: every field read from EventDetail//Data[@Name='...']/@Value documented as "not in the sample", as
        // the population held the Data paths alone.
        final Map<String, Object> summary = Map.of(
                "path_population", Map.of("EventDetail/View/Data/@Value", 40.0),
                "data_names", Map.of("EventDetail/View/Data", Map.of("secret_id", 40.0, "ticket", 12.5)));

        assertThat(IndexingInputs.populationWithDataNames(summary))
                .containsEntry("EventDetail/View/Data/@Value", 40.0)
                .containsEntry("EventDetail/View/Data[@Name='secret_id']/@Value", 40.0)
                .containsEntry("EventDetail/View/Data[@Name='ticket']/@Value", 12.5);
        // The drafting's population is left as it was.
        assertThat(IndexingInputs.population(summary)).containsOnlyKeys("EventDetail/View/Data/@Value");
        assertThat(IndexingInputs.populationWithDataNames(Map.of())).isEmpty();
    }

    @Test
    void theTypeIdsOfEachActionElementAreRead() {
        final Map<String, Object> summary = Map.of(StreamSummaries.TYPE_IDS_BY_ACTION,
                Map.of("View", Map.of("Secret-View", 9.2)));

        assertThat(IndexingInputs.byAction(summary).typeIds()).isEqualTo(Map.of("View", Map.of("Secret-View", 9.2)));
        assertThat(IndexingInputs.byAction(Map.of()).typeIds()).isEmpty();
    }
}
