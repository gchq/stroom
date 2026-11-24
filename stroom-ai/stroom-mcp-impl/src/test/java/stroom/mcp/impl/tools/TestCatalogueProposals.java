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

import stroom.ai.impl.tools.catalogue.Catalogue;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/// Proposals applied to a catalogue: what is refused, and the reason the agent is shown.
class TestCatalogueProposals {

    // Chat 194: every proposal's recognised_by written as bare strings ("evt=logon"); each was refused with only
    // "Input should be a valid dictionary or instance of Condition", and the agent read the mapping guide to find
    // the form. The reason now shows the condition the agent's own text would be.
    @Test
    void aConditionWrittenAsTextIsRefusedWithTheConditionItWouldBe() {
        final Catalogue empty = Catalogue.fromJson(Map.of("event_types", List.of()));
        final Map<String, Object> proposal = Map.of(
                "op", "add",
                "type_id", "Logon-User",
                "title", "Logon",
                "description", "A user logged on to a workstation.",
                "event_detail", "Authenticate",
                "action", "Logon",
                "recognised_by", List.of(Map.of("when", List.of("evt=logon"), "names", List.of())),
                "basis", "documentation",
                "confidence", "high");

        final CatalogueProposals.Applied applied = CatalogueProposals.apply(empty, List.of(proposal));

        assertThat(applied.refused()).singleElement().satisfies(refused -> {
            assertThat(refused.typeId()).isEqualTo("Logon-User");
            assertThat(refused.why()).contains("recognised_by.0.when.0: ")
                    .contains("\"evt=logon\" is {\"field\": \"evt\", \"equals\": \"logon\"}");
        });
    }
}
