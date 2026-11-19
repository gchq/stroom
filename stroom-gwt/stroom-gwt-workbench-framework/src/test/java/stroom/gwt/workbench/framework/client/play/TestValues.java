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


package stroom.gwt.workbench.framework.client.play;

import stroom.gwt.workbench.framework.client.play.Values.Comparison;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestValues {

    @Test
    void testFormat() {
        assertThat(Values.format(null)).isEqualTo("null");
        assertThat(Values.format("a\"b")).isEqualTo("\"a\\\"b\"");
        assertThat(Values.format('c')).isEqualTo("\"c\"");
        assertThat(Values.format(1)).isEqualTo("1");
        assertThat(Values.format(1.0)).isEqualTo("1");
        assertThat(Values.format(1.5)).isEqualTo("1.5");
        assertThat(Values.format(-3L)).isEqualTo("-3");
        assertThat(Values.format(Double.NaN)).isEqualTo("NaN");
        assertThat(Values.format(Double.NEGATIVE_INFINITY)).isEqualTo("-Infinity");
        assertThat(Values.format(true)).isEqualTo("true");
        assertThat(Values.format(List.of(1, "a"))).isEqualTo("[1, \"a\"]");
        assertThat(Values.format(new Object[]{1, null})).isEqualTo("[1, null]");
        assertThat(Values.format(Map.of())).isEqualTo("{}");
        assertThat(Values.format(orderedMap("id", 1, "name", "x"))).isEqualTo("{ id: 1, name: \"x\" }");
        assertThat(Values.format(ValueMatcher.anything())).isEqualTo("Anything");
        assertThat(Values.format(new StringBuilder("sb"))).isEqualTo("sb");
    }

    @Test
    void testIsSame() {
        assertThat(Values.isSame(null, null)).isTrue();
        assertThat(Values.isSame("a", "a")).isTrue();
        assertThat(Values.isSame("a", "b")).isFalse();
        // JavaScript has one number type
        assertThat(Values.isSame(1, 1L)).isTrue();
        assertThat(Values.isSame(1, 1.0)).isTrue();
        assertThat(Values.isSame(1, 2)).isFalse();
        assertThat(Values.isSame(Double.NaN, Double.NaN)).isTrue();
        assertThat(Values.isSame(1, "1")).isFalse();
        assertThat(Values.isSame('a', "a")).isTrue();
        assertThat(Values.isSame("x", TextMatch.containing("x"))).isTrue();
        assertThat(Values.isSame(null, ValueMatcher.anything())).isFalse();
    }

    @Test
    void testDeepEquals() {
        assertThat(Values.deepEquals(List.of(1, 3), Arrays.asList(1L, 3.0))).isTrue();
        assertThat(Values.deepEquals(List.of(1, 3), List.of(3, 1))).isFalse();
        assertThat(Values.deepEquals(List.of(1), List.of(1, 2))).isFalse();
        assertThat(Values.deepEquals(new Object[]{"a"}, List.of("a"))).isTrue();
        assertThat(Values.deepEquals(Map.of("a", List.of(1)), Map.of("a", List.of(1.0)))).isTrue();
        assertThat(Values.deepEquals(Map.of("a", 1), Map.of("a", 1, "b", 2))).isFalse();
        assertThat(Values.deepEquals(Map.of("a", 1), Map.of("b", 1))).isFalse();
        assertThat(Values.deepEquals(Map.of("a", 1, "b", "x"), Map.of("a", ValueMatcher.anything(), "b", "x")))
                .isTrue();
        assertThat(Values.deepEquals("a", List.of("a"))).isFalse();
    }

    @Test
    void testMatchesObject() {
        final Map<String, Object> actual = orderedMap("type", "Annotation", "uuid", "77",
                "nested", orderedMap("a", 1, "b", 2));
        assertThat(Values.matchesObject(actual, Map.of("type", "Annotation"))).isTrue();
        assertThat(Values.matchesObject(actual, Map.of("type", "Annotation", "uuid", "78"))).isFalse();
        assertThat(Values.matchesObject(actual, Map.of("missing", "x"))).isFalse();
        // Nested maps only need to match part of the actual one too
        assertThat(Values.matchesObject(actual, Map.of("nested", Map.of("a", 1)))).isTrue();
        assertThat(Values.matchesObject(actual, Map.of("nested", Map.of("a", 2)))).isFalse();
        assertThat(Values.matchesObject("not a map", Map.of())).isFalse();
        assertThat(Values.matchesObject(null, Map.of())).isFalse();
    }

    @Test
    void testContainsItem() {
        assertThat(Values.containsItem("The Query", "Query", false)).isTrue();
        assertThat(Values.containsItem("The Query", "query", false)).isFalse();
        assertThat(Values.containsItem("abc", null, false)).isFalse();
        assertThat(Values.containsItem(List.of("revoke:1", "x"), "revoke:1", false)).isTrue();
        assertThat(Values.containsItem(Set.of(1, 2), 2L, false)).isTrue();
        assertThat(Values.containsItem(new String[]{"blue"}, "blue", false)).isTrue();
        assertThat(Values.containsItem(List.of(List.of(1)), List.of(1), false)).isTrue();
        assertThat(Values.containsItem(List.of(List.of(1)), List.of(1.0), false)).isFalse();
        assertThat(Values.containsItem(List.of(List.of(1)), List.of(1.0), true)).isTrue();
        assertThat(Values.containsItem(42, 4, false)).isFalse();
    }

    @Test
    void testLengthOf() {
        assertThat(Values.lengthOf("abc")).isEqualTo(3);
        assertThat(Values.lengthOf(List.of(1, 2))).isEqualTo(2);
        // As Jest, which reads a length property, which a Map doesn't have
        assertThat(Values.lengthOf(Map.of("a", 1))).isNull();
        assertThat(Values.lengthOf(new Object[0])).isZero();
        assertThat(Values.lengthOf(5)).isNull();
        assertThat(Values.lengthOf(null)).isNull();
    }

    @Test
    void testCompare() {
        assertThat(Values.compare(2, 1, Comparison.GREATER_THAN)).isTrue();
        assertThat(Values.compare(1.5, 2L, Comparison.LESS_THAN)).isTrue();
        assertThat(Values.compare(2.0, 2, Comparison.GREATER_THAN_OR_EQUAL)).isTrue();
        assertThat(Values.compare(2.0, 2, Comparison.LESS_THAN_OR_EQUAL)).isTrue();
        assertThat(Values.compare(2.0, 2, Comparison.GREATER_THAN)).isFalse();
        assertThatThrownBy(() -> Values.compare("2", 1, Comparison.GREATER_THAN))
                .isInstanceOf(PlayException.class)
                .hasMessageContaining("must be a number");
        assertThatThrownBy(() -> Values.compare(1, null, Comparison.GREATER_THAN))
                .isInstanceOf(PlayException.class);
    }

    @Test
    void testCompare_nanAndNegativeZero() {
        // Regression: Double.compare made NaN greater than everything and -0.0 less than 0
        for (final Comparison comparison : Comparison.values()) {
            assertThat(Values.compare(Double.NaN, 1, comparison)).isFalse();
            assertThat(Values.compare(1, Double.NaN, comparison)).isFalse();
        }
        assertThat(Values.compare(-0.0, 0, Comparison.GREATER_THAN_OR_EQUAL)).isTrue();
        assertThat(Values.compare(-0.0, 0, Comparison.LESS_THAN)).isFalse();
    }

    @Test
    void testFloatsCompareAsInTheBrowser() {
        // Regression: 0.1f widened to 0.10000000149011612 on the JVM
        assertThat(Values.isSame(0.1f, 0.1)).isTrue();
        assertThat(Values.deepEquals(List.of(0.1f), List.of(0.1))).isTrue();
        assertThat(Values.format(0.1f)).isEqualTo("0.1");
        assertThat(Values.isSame(Float.NaN, Double.NaN)).isTrue();
    }

    @Test
    void testDeepEquals_sets() {
        // Regression: sets were compared in iteration order, and a list equalled a set
        final Set<Integer> set = new LinkedHashSet<>(List.of(1, 2));
        final Set<Integer> reversed = new LinkedHashSet<>(List.of(2, 1));
        assertThat(Values.deepEquals(set, reversed)).isTrue();
        assertThat(Values.deepEquals(set, Set.of(1, 3))).isFalse();
        assertThat(Values.deepEquals(set, Set.of(1))).isFalse();
        assertThat(Values.deepEquals(List.of(1, 2), set)).isFalse();
        assertThat(Values.deepEquals(set, List.of(1, 2))).isFalse();
        assertThat(Values.deepEquals(Set.of(Map.of("a", 1)), Set.of(Map.of("a", 1)))).isTrue();
        assertThat(Values.format(set)).isEqualTo("Set {1, 2}");
    }

    @Test
    void testDeepEquals_nullValuesAreUndefined() {
        // Regression: as Jest ignores undefined properties, a null-valued key is as if absent
        final Map<String, Object> withNull = new LinkedHashMap<>();
        withNull.put("a", 1);
        withNull.put("b", null);
        assertThat(Values.deepEquals(withNull, Map.of("a", 1))).isTrue();
        assertThat(Values.deepEquals(Map.of("a", 1), withNull)).isTrue();
        assertThat(Values.deepEquals(withNull, Map.of("a", 1, "b", 2))).isFalse();
    }

    @Test
    void testDeepEquals_primitiveArrays() {
        // Regression: primitive arrays were compared by reference
        assertThat(Values.deepEquals(new int[]{1, 2}, new int[]{1, 2})).isTrue();
        assertThat(Values.deepEquals(new int[]{1, 2}, List.of(1, 2))).isTrue();
        assertThat(Values.deepEquals(new double[]{1.5}, new long[]{1})).isFalse();
        assertThat(Values.deepEquals(new boolean[]{true}, List.of(true))).isTrue();
        assertThat(Values.deepEquals(new char[]{'a'}, List.of("a"))).isTrue();
        assertThat(Values.containsItem(new int[]{1, 2}, 2, false)).isTrue();
        assertThat(Values.lengthOf(new byte[3])).isEqualTo(3);
        assertThat(Values.format(new int[]{1, 2})).isEqualTo("[1, 2]");
    }

    @Test
    void testMatchesObject_partialInLists() {
        // Regression: maps inside lists had to match exactly
        final Map<String, Object> actual = Map.of("rows", List.of(Map.of("id", 1, "name", "a"),
                Map.of("id", 2, "name", "b")), "total", 2);
        assertThat(Values.matchesObject(actual, Map.of("rows", List.of(Map.of("id", 1), Map.of("id", 2)))))
                .isTrue();
        assertThat(Values.matchesObject(actual, Map.of("rows", List.of(Map.of("id", 1))))).isFalse();
        assertThat(Values.matchesObject(actual, Map.of("rows", List.of(Map.of("id", 1), Map.of("id", 3)))))
                .isFalse();
        assertThat(Values.matchesObject(actual, Map.of("total", 2))).isTrue();
    }

    @Test
    void testIsCloseTo() {
        assertThat(Values.isCloseTo(0.1 + 0.2, 0.3, 2)).isTrue();
        assertThat(Values.isCloseTo(0.31, 0.3, 2)).isFalse();
        assertThat(Values.isCloseTo(0.304, 0.3, 2)).isTrue();
        assertThat(Values.isCloseTo("0.3", 0.3, 2)).isFalse();
    }

    @Test
    void testIsTruthy() {
        assertThat(Values.isTruthy(null)).isFalse();
        assertThat(Values.isTruthy(false)).isFalse();
        assertThat(Values.isTruthy(true)).isTrue();
        assertThat(Values.isTruthy(0)).isFalse();
        assertThat(Values.isTruthy(0.0)).isFalse();
        assertThat(Values.isTruthy(Double.NaN)).isFalse();
        assertThat(Values.isTruthy(-1)).isTrue();
        assertThat(Values.isTruthy("")).isFalse();
        assertThat(Values.isTruthy("0")).isTrue();
        assertThat(Values.isTruthy(List.of())).isTrue();
    }

    @Test
    void testArgumentsMatch() {
        assertThat(Values.argumentsMatch(List.of(1, "a"), List.of(1L, "a"))).isTrue();
        assertThat(Values.argumentsMatch(List.of(1), List.of(1, "a"))).isFalse();
        assertThat(Values.argumentsMatch(Arrays.asList((Object) null), Arrays.asList((Object) null))).isTrue();
        assertThat(Values.argumentsMatch(List.of(Map.of("id", 1, "x", 2)),
                List.of(ValueMatcher.objectContaining(Map.of("id", 1))))).isTrue();
        assertThat(Values.formatAll(List.of(1, "a"))).isEqualTo("1, \"a\"");
        assertThat(Values.formatAll(List.of())).isEmpty();
    }

    @Test
    void testValueMatchers() {
        assertThat(ValueMatcher.anything().matchesValue(0)).isTrue();
        assertThat(ValueMatcher.anything().matchesValue(null)).isFalse();
        assertThat(ValueMatcher.stringContaining("ell").matchesValue("hello")).isTrue();
        assertThat(ValueMatcher.stringContaining("ell").matchesValue(1)).isFalse();
        assertThat(ValueMatcher.stringContaining("ell").describe()).isEqualTo("StringContaining(\"ell\")");
        assertThat(ValueMatcher.arrayContaining(List.of(3, 1)).matchesValue(List.of(1, 2, 3))).isTrue();
        assertThat(ValueMatcher.arrayContaining(List.of(4)).matchesValue(List.of(1, 2, 3))).isFalse();
        assertThat(ValueMatcher.arrayContaining(List.of()).matchesValue("abc")).isFalse();
        assertThat(ValueMatcher.objectContaining(Map.of("id", 1)).describe()).isEqualTo("ObjectContaining({ id: 1 })");
        assertThat(ValueMatcher.satisfying("isEven", value -> ((Integer) value) % 2 == 0).matchesValue(4)).isTrue();
    }

    private static Map<String, Object> orderedMap(final Object... keysAndValues) {
        final Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < keysAndValues.length; i += 2) {
            map.put((String) keysAndValues[i], keysAndValues[i + 1]);
        }
        return map;
    }

    @Test
    void testZeroAndNegativeZeroAreNotTheSame() {
        // Regression: toBe and toEqual treated 0 and -0 as the same, unlike Object.is in Jest
        assertThat(Values.isSame(0.0, -0.0)).isFalse();
        assertThat(Values.isSame(-0.0, 0)).isFalse();
        assertThat(Values.isSame(-0.0, -0.0)).isTrue();
        assertThat(Values.isSame(0, 0.0)).isTrue();
        assertThat(Values.isSame(Double.NaN, Float.NaN)).isTrue();
        assertThat(Values.deepEquals(List.of(0.0), List.of(-0.0))).isFalse();
        assertThat(Values.deepEquals(Map.of("a", -0.0), Map.of("a", -0.0))).isTrue();
    }
}
