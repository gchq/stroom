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

package stroom.ai.impl.tools.documents;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/// The layout of a JSON object saved to the workspace as too large to show. Seen in chats 184-186: a kept mapping
/// saved with the mapping under `.mapping` queried as if at the top level, eight of some 15 jq queries failing.
class TestSavedJsonLayout {

    @Test
    void aKeptMappingsLayoutSaysWhereItsPartsAreAndAQueryThatWorks() {
        final Map<String, Object> saved = KeptMapping.summary(
                KeptMapping.readMapping(TestKeptMapping.described("Acme VPN events")).orElseThrow(), "x-1", true);

        assertThat(SavedJsonLayout.describe(saved)).isEqualTo(
                "Its top-level keys: kind, schema_version, mapping. .mapping holds .mapping.input, "
                + ".mapping.unmatched, .mapping.common[] (6 items, each {path, field, value, default}), "
                + ".mapping.events[] (2 items, each {name, when, fields}). A query that works on it: "
                + "`.mapping.events[] | {name, fields: [.fields[] | .path]}`.");
    }

    @Test
    void aSummarysLayoutListsItsTopLevelListsAndOnlyThePartsPresent() {
        final Map<String, Object> saved = KeptMapping.summary(
                KeptMapping.readMapping(TestKeptMapping.described("Acme VPN events")).orElseThrow(), "x-1", false);

        assertThat(SavedJsonLayout.describe(saved))
                .startsWith("Its top-level keys: kind, schema_version, input, rules, common_entries, extractions, "
                            + "style, how.")
                .contains(" Lists: .rules[] (2 items).")
                .doesNotContain(".mapping")
                .endsWith("A query that works on it: `keys`.");
    }

    @Test
    void keysThatAreNotPlainAreQuotedAndLongKeyListsCapped() {
        final Map<String, Object> inner = new LinkedHashMap<>();
        for (int i = 0; i < 14; i++) {
            inner.put("k" + i, i);
        }
        final Map<String, Object> saved = new LinkedHashMap<>();
        final Map<String, Object> item = new LinkedHashMap<>();
        item.put("type id", "a");
        item.put("n", 1);
        saved.put("event-types", List.of(item));
        saved.put("inner", inner);

        assertThat(SavedJsonLayout.describe(saved))
                .contains(".inner holds .inner.k0, ")
                .contains(".inner.k11 and 2 more.")
                .contains("Lists: .\"event-types\"[] (1 item, each {type id, n}).")
                .endsWith("`.\"event-types\"[] | {\"type id\": .\"type id\", n}`.");
    }

    @Test
    void anObjectWithoutListsOrNestedObjectsGivesItsKeys() {
        assertThat(SavedJsonLayout.describe(Map.of("a", 1)))
                .isEqualTo("Its top-level keys: a. A query that works on it: `keys`.");
        assertThat(SavedJsonLayout.describe(Map.of()))
                .isEqualTo("It is an empty JSON object.");
        assertThat(SavedJsonLayout.describe(Map.of("x", List.of(1, 2))))
                .isEqualTo("Its top-level keys: x. Lists: .x[] (2 items). A query that works on it: `keys`.");
    }
}
