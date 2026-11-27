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

package stroom.ai.impl.tools.catalogue;

import stroom.ai.impl.tools.generation.Condition;
import stroom.ai.impl.tools.validation.EventSchema;
import stroom.ai.impl.tools.validation.HeldSchemasWorld;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/// Event types read back from an existing pipeline a build starts from (decided by the user, 2026-11-24): proposed
/// for the user to confirm, their TypeIds kept as the pipeline writes them (hand-written content seldom writes
/// Verb-Object), so the new version's Events keep them; any other event type is still held to Verb-Object.
class TestCatalogueReadBack {

    private static final EventSchema SCHEMA = EventSchema.parse(HeldSchemasWorld.XSD_4_1_0);

    @Test
    void aTypeIdReadBackIsKeptAsItIsWrittenAndConfirmedSo() {
        final EventType read = type("vpn-login").readBack();
        assertThat(read.basis()).isEqualTo(EventType.EXISTING_PIPELINE);
        assertThat(read.confirmed()).isFalse();
        assertThat(CatalogueValidation.problems(catalogue(read), SCHEMA)).isEmpty();
        // Confirmed by the user, it keeps its basis, so it stays as written.
        final EventType confirmed = read.confirmedByUser();
        assertThat(confirmed.basis()).isEqualTo(EventType.EXISTING_PIPELINE);
        assertThat(CatalogueValidation.problems(catalogue(confirmed), SCHEMA)).isEmpty();
        // Round trip through the source notes' JSON.
        assertThat(Catalogue.fromJson(catalogue(confirmed).toJson()).eventTypes().getFirst().basis())
                .isEqualTo(EventType.EXISTING_PIPELINE);
    }

    @Test
    void anyOtherTypeIdIsStillVerbObject() {
        assertThat(CatalogueValidation.problems(catalogue(type("vpn-login")), SCHEMA)).singleElement()
                .satisfies(problem -> assertThat(problem.problem()).contains("isn't Verb-Object"));
        assertThat(TypeIds.keptProblem("1001")).isNull();
        assertThat(TypeIds.keptProblem("")).contains("type_id is needed");
        assertThat(TypeIds.keptProblem("x".repeat(TypeIds.MAX_LENGTH + 1))).contains("characters long");
    }

    private static Catalogue catalogue(final EventType type) {
        return new Catalogue(List.of(type), null, new Catalogue.Scope(null, List.of()));
    }

    private static EventType type(final String typeId) {
        return new EventType(typeId, typeId, null, "Authenticate", "Logon", null, null, null, null,
                List.of(new Way(List.of(Condition.fromJson(Map.of("field", "action", "equals", "login"))),
                        List.of())), false, EventType.AGENT_KNOWLEDGE, "high", null, EventType.PROPOSED);
    }
}
