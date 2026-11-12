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

import stroom.ai.impl.tools.KeptMapping;
import stroom.ai.impl.tools.StreamSummaries;
import stroom.ai.impl.tools.generation.DispatcherSpec;
import stroom.ai.impl.tools.generation.DispatcherXslt;
import stroom.ai.impl.tools.generation.FieldGroup;
import stroom.ai.impl.tools.generation.FieldMapping;
import stroom.ai.impl.tools.sample.ToolJson;

import org.junit.jupiter.api.Test;

import java.util.List;
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

    @Test
    void theFieldsOfTheKeptTranslationMappingAreReadWithTheirGroups() {
        final Object mapping = ToolJson.loads("""
                {"input": "data_splitter",
                 "common": [{"path": "EventTime/TimeCreated", "field": "time"}],
                 "events": [{"name": "send", "fields": [
                   {"path": "EventDetail/TypeId", "value": "Send"},
                   {"path": "EventDetail/Send/Destination/User", "for_each": {"split": "recipients", "on": ","},
                    "as": "recipient",
                    "fields": [{"path": "Id", "item": true, "transform": "strip_domain"},
                               {"path": "Domain", "item": true, "transform": "domain"}]}]}]}""");
        final String description = KeptMapping.withMapping("Mail events", "translation", Map.of("mapping", mapping));

        final List<FieldGroup.Leaf> leaves = IndexingInputs.mappingFields(List.of(description, "No mapping",
                KeptMapping.withMapping("", "translation", Map.of("mapping", Map.of("input", "nonsense")))));

        assertThat(leaves).extracting(leaf -> leaf.entry().path()).containsExactly("EventTime/TimeCreated",
                "EventDetail/TypeId", "EventDetail/Send/Destination/User/Id",
                "EventDetail/Send/Destination/User/Domain");
        assertThat(leaves.get(2).groups()).extracting(FieldMapping::path)
                .containsExactly("EventDetail/Send/Destination/User");
        assertThat(IndexingInputs.mappingFields(List.of())).isEmpty();
    }

    @Test
    void aDispatchersGroupsAreFoundByTheUuidsItImports() {
        final DispatcherSpec spec = new DispatcherSpec("PEGA", "pega", Map.of(), "sourcetype", null, List.of(),
                List.of(new DispatcherSpec.Group("PEGA security", "pega_security",
                                SourceTypeGroups.href("u-security", "PEGA security"), List.of("PegaSecurityEvent")),
                        new DispatcherSpec.Group("PEGA mail", "pega_mail", SourceTypeGroups.href("u-mail",
                                "PEGA mail"), List.of("PegaMail"))), Map.of());
        final String description = KeptMapping.withBlock("The feed's dispatcher", DispatcherXslt.SPEC_BLOCK,
                spec.toJson());

        assertThat(IndexingInputs.groupUuids(description)).containsExactly("u-security", "u-mail");
        assertThat(IndexingInputs.groupUuids("A translation")).isEmpty();
        assertThat(IndexingInputs.groupUuids(null)).isEmpty();
    }
}
