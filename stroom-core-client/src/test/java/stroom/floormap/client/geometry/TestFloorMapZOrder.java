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

package stroom.floormap.client.geometry;

import stroom.floormap.client.model.Fact;
import stroom.floormap.shared.FloorMapTransformationMatrix;
import stroom.floormap.shared.TypeStyle;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TestFloorMapZOrder {

    private static final List<TypeStyle> ORDER = List.of(
            new TypeStyle("background", null, null),
            new TypeStyle("gate", null, null),
            new TypeStyle("person", null, null));

    private static Fact fact(final String key, final String type) {
        return new Fact(key, type, null, FloorMapTransformationMatrix.identity(), null);
    }

    private static List<String> keys(final List<Fact> facts) {
        return facts.stream().map(Fact::getKey).toList();
    }

    @Test
    void testIndexOf_configuredAndUnconfigured() {
        assertThat(FloorMapZOrder.indexOf("background", ORDER)).isZero();
        assertThat(FloorMapZOrder.indexOf("person", ORDER)).isEqualTo(2);
        assertThat(FloorMapZOrder.indexOf("desk", ORDER)).isEqualTo(Integer.MAX_VALUE);
    }

    /** Facts paint back-to-front by configured type order. */
    @Test
    void testSort_byConfiguredOrder() {
        final List<Fact> sorted = FloorMapZOrder.sort(List.of(
                fact("p1", "person"),
                fact("bg", "background"),
                fact("g1", "gate")), ORDER);
        assertThat(keys(sorted)).containsExactly("bg", "g1", "p1");
    }

    /** Same-type facts keep their input order (stable). */
    @Test
    void testSort_stableWithinType() {
        final List<Fact> sorted = FloorMapZOrder.sort(List.of(
                fact("g1", "gate"),
                fact("g2", "gate"),
                fact("bg", "background")), ORDER);
        assertThat(keys(sorted)).containsExactly("bg", "g1", "g2");
    }

    /** Unconfigured types sort last (paint on top), preserving their order. */
    @Test
    void testSort_unconfiguredOnTop() {
        final List<Fact> sorted = FloorMapZOrder.sort(List.of(
                fact("new1", "sensor"),
                fact("g1", "gate"),
                fact("new2", "camera")), ORDER);
        // gate (configured) first; then the two unconfigured in input order.
        assertThat(keys(sorted)).containsExactly("g1", "new1", "new2");
    }

    /** A null/empty order leaves the facts in their original order. */
    @Test
    void testSort_nullOrder_preservesInput() {
        final List<Fact> input = List.of(fact("a", "x"), fact("b", "y"), fact("c", "z"));
        assertThat(keys(FloorMapZOrder.sort(input, null))).containsExactly("a", "b", "c");
    }
}
