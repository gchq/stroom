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

package stroom.floormap.shared;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TestFloorMapAreaOverlay {

    private static Fact area(final String key,
                             final double half) {
        final double[][] local = new double[][]{
                {-half, -half}, {half, -half}, {half, half}, {-half, half}};
        return new Fact(key, FloorMapJsonKeys.AREA, null,
                FloorMapTransformationMatrix.translate(100, 100),
                new double[]{0, 0}, local, null, null);
    }

    private static FloorMapObject event(final String id, final double x, final double y) {
        return new FloorMapObject(id, FloorMapJsonKeys.PERSON, x, y);
    }

    /**
     * Two concentric areas at (100,100) — a small "bay" drawn within a large
     * "warehouse" — with alice inside both and bob outside both. The bay is not
     * an occupant of the warehouse: areas are never located inside anything.
     */
    private static FloorMapAreaMembership nestedMembership() {
        final List<Fact> facts = Arrays.asList(
                area("warehouse", 100),
                area("bay", 20));
        return FloorMapAreaMembership.compute(facts,
                Arrays.asList(event("alice", 100, 100), event("bob", 900, 900)));
    }

    /**
     * Focusing an entity flags the areas containing it — the direction the
     * plan calls "highlight areas containing facts".
     */
    @Test
    void testFocusEntityHighlightsContainingAreas() {
        final FloorMapAreaOverlay overlay =
                FloorMapAreaOverlay.of(nestedMembership(), "alice");

        assertThat(overlay.isRelated("bay")).isTrue();
        assertThat(overlay.isRelated("warehouse")).isTrue();
        assertThat(overlay.isRelated("bob")).isFalse();
        assertThat(overlay.hasRelated()).isTrue();
    }

    /** Focusing an area flags its occupants — the reciprocal direction. */
    @Test
    void testFocusAreaHighlightsOccupants() {
        final FloorMapAreaOverlay overlay =
                FloorMapAreaOverlay.of(nestedMembership(), "bay");

        assertThat(overlay.isRelated("alice")).isTrue();
        assertThat(overlay.isRelated("bob")).isFalse();
    }

    /**
     * Focusing the outer area flags the person inside it but NOT the area drawn
     * within it — areas are never occupants, so nesting is never highlighted.
     */
    @Test
    void testFocusOuterAreaDoesNotHighlightEnclosedArea() {
        final FloorMapAreaOverlay overlay =
                FloorMapAreaOverlay.of(nestedMembership(), "warehouse");

        assertThat(overlay.isRelated("alice")).isTrue();
        assertThat(overlay.isRelated("bay")).isFalse();
    }

    /** The focused thing is never flagged as related to itself. */
    @Test
    void testFocusNotRelatedToItself() {
        assertThat(FloorMapAreaOverlay.of(nestedMembership(), "bay").isRelated("bay"))
                .isFalse();
        assertThat(FloorMapAreaOverlay.of(nestedMembership(), "alice").isRelated("alice"))
                .isFalse();
    }

    /** Badges are produced with nothing focused; only the highlight needs focus. */
    @Test
    void testCountsWithoutFocus() {
        final FloorMapAreaOverlay overlay = FloorMapAreaOverlay.of(nestedMembership(), null);

        assertThat(overlay.hasRelated()).isFalse();
        // Alice only — the enclosed area does not count towards either badge.
        assertThat(overlay.getOccupantCount("bay")).isEqualTo(1);
        assertThat(overlay.getOccupantCount("warehouse")).isEqualTo(1);
    }

    /**
     * An empty area carries no count, so the view draws no badge rather than a
     * "0".
     */
    @Test
    void testEmptyAreaHasNoCount() {
        final FloorMapAreaMembership membership = FloorMapAreaMembership.compute(
                Collections.singletonList(area("bay", 20)),
                Collections.singletonList(event("bob", 900, 900)));

        assertThat(FloorMapAreaOverlay.of(membership, null).getOccupantCount("bay")).isNull();
    }

    @Test
    void testNullsAreSafe() {
        assertThat(FloorMapAreaOverlay.of(null, "alice")).isSameAs(FloorMapAreaOverlay.EMPTY);
        assertThat(false).isFalse();
        assertThat(FloorMapAreaOverlay.EMPTY.isRelated("anything")).isFalse();
        assertThat(FloorMapAreaOverlay.EMPTY.getOccupantCount(null)).isNull();
        assertThat(FloorMapAreaOverlay.EMPTY.hasRelated()).isFalse();
    }

    /** A membership with no areas at all yields the shared empty instance. */
    @Test
    void testNoAreasYieldsEmpty() {
        assertThat(FloorMapAreaOverlay.of(FloorMapAreaMembership.EMPTY, "alice"))
                .isSameAs(FloorMapAreaOverlay.EMPTY);
    }
}
