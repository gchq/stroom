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

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/// The catalogue's order: more specific first, stable otherwise.
class TestCatalogueOrder {

    @Test
    void eventTypesWithMoreConditionsComeFirstTiesKeptInOrder() {
        final List<EventType> types = List.of(type("Process-A", 1), type("Process-B", 2), type("Process-C", 1),
                type("Process-D", 3), type("Process-E", 0), type("Process-F", 2));

        assertThat(CatalogueOrder.ordered(types)).extracting(EventType::typeId)
                .containsExactly("Process-D", "Process-B", "Process-F", "Process-A", "Process-C", "Process-E");
        assertThat(CatalogueOrder.ordered(List.of())).isEmpty();
    }

    @Test
    void waysAreOrderedAcrossEventTypesMoreSpecificFirst() {
        final EventType two = new EventType("Process-Two", "Two", null, null, null, null, null, null, null,
                List.of(way(1), way(3)), false, EventType.AGENT_KNOWLEDGE, "high", null, EventType.PROPOSED);
        final Catalogue catalogue = new Catalogue(List.of(type("Process-One", 2), two, type("Process-Three", 1)),
                null, null);

        assertThat(CatalogueOrder.ways(catalogue)).extracting(way -> way.type().typeId() + "#" + way.index())
                .containsExactly("Process-Two#1", "Process-One#0", "Process-Two#0", "Process-Three#0");
    }

    private static EventType type(final String typeId, final int conditions) {
        return new EventType(typeId, typeId, null, null, null, null, null, null, null, conditions == 0
                ? List.of()
                : List.of(way(conditions)), false, EventType.AGENT_KNOWLEDGE, "high", null, EventType.PROPOSED);
    }

    private static Way way(final int conditions) {
        final List<Condition> when = new ArrayList<>();
        for (int n = 0; n < conditions; n++) {
            when.add(Condition.fromJson(Map.of("field", "f" + n, "equals", "v")));
        }
        return new Way(when, List.of());
    }
}
