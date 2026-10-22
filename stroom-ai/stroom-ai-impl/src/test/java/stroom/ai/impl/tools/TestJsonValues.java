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

package stroom.ai.impl.tools;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/// JSON-like values handled as the Stroom MCP server's Python handled its dicts and lists.
class TestJsonValues {

    @Test
    void truthIsPythons() {
        assertThat(JsonValues.truthy(null)).isFalse();
        assertThat(JsonValues.truthy(false)).isFalse();
        assertThat(JsonValues.truthy(true)).isTrue();
        assertThat(JsonValues.truthy("")).isFalse();
        assertThat(JsonValues.truthy(" ")).isTrue();
        assertThat(JsonValues.truthy(0)).isFalse();
        assertThat(JsonValues.truthy(0L)).isFalse();
        assertThat(JsonValues.truthy(0.0)).isFalse();
        assertThat(JsonValues.truthy(new BigDecimal("0.5"))).isTrue();
        assertThat(JsonValues.truthy(2L)).isTrue();
        assertThat(JsonValues.truthy(List.of())).isFalse();
        assertThat(JsonValues.truthy(Set.of("a"))).isTrue();
        assertThat(JsonValues.truthy(Map.of())).isFalse();
        assertThat(JsonValues.truthy(Map.of("a", 1))).isTrue();
        assertThat(JsonValues.truthy(new Object())).isTrue();
    }

    @Test
    void aDictLiteralKeepsItsOrder() {
        assertThat(JsonValues.dict("b", 1, "a", null).keySet()).containsExactly("b", "a");
        assertThat(JsonValues.nullSafeSet("x").contains(null)).isFalse();
    }

    @Test
    void mapsAndListsAreReadAsDictGetOrEmptyReadsThem() {
        final Map<String, Object> given = new LinkedHashMap<>(Map.of("index", Map.of("name", "x"), "none", Map.of()));
        assertThat(JsonValues.map(given)).isSameAs(given);
        assertThat(JsonValues.map("text")).isNull();
        assertThat(JsonValues.mapOrEmpty(given.get("index"))).containsEntry("name", "x");
        assertThat(JsonValues.mapOrEmpty("text")).isEmpty();
        assertThat(JsonValues.mapAt(given, "index")).containsEntry("name", "x");
        assertThat(JsonValues.mapAt(given, "none")).isEmpty();
        assertThat(JsonValues.mapAt(null, "index")).isEmpty();
        final List<Object> list = new ArrayList<>(List.of(1));
        assertThat(JsonValues.listOrEmpty(list)).isSameAs(list);
        assertThat(JsonValues.listOrEmpty(Map.of())).isEmpty();
    }

    @Test
    void theMapsAListHoldsAreItsDicts() {
        assertThat(JsonValues.maps(List.of(Map.of("a", 1), "x", 2, Map.of("b", 2))))
                .containsExactly(Map.of("a", 1), Map.of("b", 2));
        assertThat(JsonValues.maps("not a list")).isEmpty();
        assertThat(JsonValues.maps(null)).isEmpty();
    }

    @Test
    void theStringsAListHoldsAreWrittenAsPythonsStrWritesThem() {
        assertThat(JsonValues.strings(Arrays.asList("a", 1, true, null, 1.5, 1e16, List.of("x"))))
                .containsExactly("a", "1", "True", "None", "1.5", "1e+16", "['x']");
        assertThat(JsonValues.strings("a")).isEmpty();
        assertThat(JsonValues.strings(null)).isEmpty();
    }

    @Test
    void aRecordsValueIsItsText() {
        assertThat(JsonValues.text(null)).isEmpty();
        assertThat(JsonValues.text(0)).isEmpty();
        assertThat(JsonValues.text("x")).isEqualTo("x");
        assertThat(JsonValues.text(2.5)).isEqualTo("2.5");
        assertThat(JsonValues.jsonText(Map.of("a", 1))).isEqualTo("{\"a\": 1}");
        assertThat(JsonValues.jsonText("x")).isEqualTo("x");
    }

    @Test
    void numbersAreComparedAcrossTypesAsPythonComparesThem() {
        assertThat(JsonValues.equalsNumber(1024.0, 1024)).isTrue();
        assertThat(JsonValues.equalsNumber(1024L, 1024)).isTrue();
        assertThat(JsonValues.equalsNumber(true, 1)).isTrue();
        assertThat(JsonValues.equalsNumber("1024", 1024)).isFalse();
        assertThat(JsonValues.equalsNumber(null, 0)).isFalse();
    }

    @Test
    @SuppressWarnings("unchecked")
    void aDeepCopyLeavesTheOriginalAlone() {
        final Map<String, Object> original = new LinkedHashMap<>();
        original.put("list", new ArrayList<>(List.of(1)));
        final Map<String, Object> copy = JsonValues.deepCopy(original);
        ((List<Object>) copy.get("list")).add(2);
        assertThat(original.get("list")).isEqualTo(List.of(1));
        assertThat(copy.get("list")).isEqualTo(List.of(1, 2));
    }

    @Test
    void countsAreAsCountersGiveThem() {
        final Map<String, Integer> counts = JsonValues.counted(List.of("b", "a", "a", "c"));
        assertThat(counts).containsExactly(Map.entry("b", 1), Map.entry("a", 2), Map.entry("c", 1));
        assertThat(JsonValues.mostCommon(counts)).containsExactly("a", "b", "c");
    }
}
