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

package stroom.floormap.client.model;

import stroom.floormap.shared.FloorMapTransformationMatrix;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TestFloorMapLocationResolver {

    // -----------------------------------------------------------------------
    // Telling the two location shapes apart
    // -----------------------------------------------------------------------

    /// The shape events carry when the position was baked in at ingest.
    @Test
    void testParseCoordinates() {
        assertThat(FloorMapLocationResolver.parseCoordinates("120.5, -40"))
                .containsExactly(120.5, -40.0);
        assertThat(FloorMapLocationResolver.parseCoordinates("10,20"))
                .containsExactly(10.0, 20.0);
        // Whitespace around either part, and a negative or fractional value in either position.
        assertThat(FloorMapLocationResolver.parseCoordinates("  -0.5 ,  7  "))
                .containsExactly(-0.5, 7.0);
    }

    /// Anything that is not two comma-separated numbers is not a position.
    @Test
    void testNonCoordinatesParseAsNull() {
        assertThat(FloorMapLocationResolver.parseCoordinates(null)).isNull();
        assertThat(FloorMapLocationResolver.parseCoordinates("")).isNull();
        assertThat(FloorMapLocationResolver.parseCoordinates("DSK-L1-03")).isNull();
        assertThat(FloorMapLocationResolver.parseCoordinates("left, right")).isNull();
        // A fact key that happens to contain a comma is still a key, because what disambiguates is
        // both parts being numeric rather than the number of parts.
        assertThat(FloorMapLocationResolver.parseCoordinates("Desk 12, North")).isNull();
        // One number is not a position.
        assertThat(FloorMapLocationResolver.parseCoordinates("120.5")).isNull();
    }

    /// The three-part form is not coordinates, and is now nothing special.
    ///
    /// It used to be *the* coordinate form, then a rejected legacy form that had to be
    /// recognised so it could be reported. With Location and Location Ref as separate columns there
    /// is nothing to disambiguate: a Location that is not two numbers is malformed, and the parser
    /// that reads it says so.
    @Test
    void testThreePartValueIsNotCoordinates() {
        assertThat(FloorMapLocationResolver.parseCoordinates("mapA, 120.5, -40")).isNull();
        assertThat(FloorMapLocationResolver.parseCoordinates("B-GND,10,20")).isNull();
        assertThat(FloorMapLocationResolver.parseCoordinates("1, 120.5, 340")).isNull();
    }

    /// A reference is whatever the Location Ref column says, trimmed.
    ///
    /// No shape rule at all, which is the point of the split: a fact key that looks like two
    /// numbers, or contains a comma, is now expressible. Under the single-column scheme neither
    /// was.
    @Test
    void testReferenceHasNoShapeRule() {
        assertThat(FloorMapLocationResolver.parseReference("100, 200")).isEqualTo("100, 200");
        assertThat(FloorMapLocationResolver.parseReference("Desk 12, North"))
                .isEqualTo("Desk 12, North");
        assertThat(FloorMapLocationResolver.parseReference("  desk-101  ")).isEqualTo("desk-101");
    }

    /// A non-coordinate value is the key of the fact the event happened at.
    @Test
    void testParseReference() {
        assertThat(FloorMapLocationResolver.parseReference("DSK-L1-03")).isEqualTo("DSK-L1-03");
        assertThat(FloorMapLocationResolver.parseReference("  G-MAIN_ENTRANCE  "))
                .isEqualTo("G-MAIN_ENTRANCE");
    }

    /// Nothing named means nothing to resolve.
    @Test
    void testBlankIsNotAReference() {
        assertThat(FloorMapLocationResolver.parseReference(null)).isNull();
        assertThat(FloorMapLocationResolver.parseReference("   ")).isNull();
    }

    // -----------------------------------------------------------------------
    // Placement
    // -----------------------------------------------------------------------

    /// The point of the whole class: the entity is drawn where its object is NOW.
    @Test
    void testReferencedEntityTakesTheFactsPosition() {
        final List<FloorMapObject> placed = FloorMapLocationResolver.resolve(
                Collections.singletonList(entityAt("DSK-L1-03")),
                Collections.singletonList(pointFact("DSK-L1-03", 300, 400)));

        assertThat(placed).hasSize(1);
        assertThat(placed.getFirst().getId()).isEqualTo("user-42");
        assertThat(placed.getFirst().getType()).isEqualTo("person");
        assertThat(placed.getFirst().getX()).isEqualTo(300);
        assertThat(placed.getFirst().getY()).isEqualTo(400);
    }

    /// Moving the object moves its visitors — the same events resolved against
    /// moved facts land somewhere else. This is the bug the class exists for:
    /// coordinates baked into an event at ingest cannot do this.
    @Test
    void testMovingTheFactMovesTheEntity() {
        final List<FloorMapObject> events = Collections.singletonList(entityAt("DSK-L1-03"));

        final List<FloorMapObject> before = FloorMapLocationResolver.resolve(
                events, Collections.singletonList(pointFact("DSK-L1-03", 300, 400)));
        final List<FloorMapObject> after = FloorMapLocationResolver.resolve(
                events, Collections.singletonList(pointFact("DSK-L1-03", 900, 100)));

        assertThat(before.getFirst().getX()).isEqualTo(300);
        assertThat(after.getFirst().getX()).isEqualTo(900);
        assertThat(after.getFirst().getY()).isEqualTo(100);
    }

    /// A fact placed by its matrix is followed there, not to its raw world coords.
    @Test
    void testPlacementFollowsTheFactsMatrix() {
        final Fact moved = new Fact("DSK-L1-03", "desk", null,
                FloorMapTransformationMatrix.translate(50, 60), new double[]{10, 20});

        final List<FloorMapObject> placed = FloorMapLocationResolver.resolve(
                Collections.singletonList(entityAt("DSK-L1-03")),
                Collections.singletonList(moved));

        assertThat(placed.getFirst().getX()).isEqualTo(60);
        assertThat(placed.getFirst().getY()).isEqualTo(80);
    }

    /// An entity that brought its own coordinates is left exactly where it is.
    @Test
    void testCoordinateBearingEntityPassesThrough() {
        final FloorMapObject baked = new FloorMapObject("user-42", "person", 11, 22);

        final List<FloorMapObject> placed = FloorMapLocationResolver.resolve(
                Collections.singletonList(baked),
                Collections.singletonList(pointFact("DSK-L1-03", 300, 400)));

        assertThat(placed).containsExactly(baked);
    }

    /// A reference to a fact that is not on the map has no position, so the
    /// entity is dropped rather than stacked on the origin.
    @Test
    void testDanglingReferenceIsDropped() {
        assertThat(FloorMapLocationResolver.resolve(
                Collections.singletonList(entityAt("DSK-GONE")),
                Collections.singletonList(pointFact("DSK-L1-03", 300, 400))))
                .isEmpty();
    }

    /// Facts arriving after the events is normal (the two queries refresh
    /// independently), so this must degrade to "not yet placed", not to a wrong
    /// placement — the caller resolves again once the facts land.
    @Test
    void testNoFactsYetDropsReferencedEntities() {
        assertThat(FloorMapLocationResolver.resolve(
                Arrays.asList(entityAt("DSK-L1-03"), new FloorMapObject("user-7", "person", 1, 2)),
                null))
                .extracting(FloorMapObject::getId)
                .containsExactly("user-7");
    }

    /// An area is located at its centroid, matching where containment tests it.
    @Test
    void testReferenceToAnAreaUsesItsCentroid() {
        final Fact area = new Fact("BAY", "area", null,
                FloorMapTransformationMatrix.translate(100, 100),
                null,
                new double[][]{{-10, -10}, {10, -10}, {10, 10}, {-10, 10}},
                null,
                null);

        final List<FloorMapObject> placed = FloorMapLocationResolver.resolve(
                Collections.singletonList(entityAt("BAY")),
                Collections.singletonList(area));

        assertThat(placed.getFirst().getX()).isEqualTo(100);
        assertThat(placed.getFirst().getY()).isEqualTo(100);
    }

    /// Null in, empty out — the caller always has a list to push.
    @Test
    void testNoEntities() {
        assertThat(FloorMapLocationResolver.resolve(null, null)).isEmpty();
    }

    // -----------------------------------------------------------------------
    // Change detection
    // -----------------------------------------------------------------------

    /// A facts refresh that moved nothing must not be pushed on as an update.
    @Test
    void testSamePositions() {
        assertThat(FloorMapLocationResolver.samePositions(
                Collections.singletonList(new FloorMapObject("user-42", "person", 1, 2)),
                Collections.singletonList(new FloorMapObject("user-42", "person", 1, 2))))
                .isTrue();
    }

    /// Any move, disappearance or new arrival is a change.
    @Test
    void testDifferentPositions() {
        final List<FloorMapObject> one =
                Collections.singletonList(new FloorMapObject("user-42", "person", 1, 2));

        assertThat(FloorMapLocationResolver.samePositions(one,
                Collections.singletonList(new FloorMapObject("user-42", "person", 1, 3))))
                .isFalse();
        assertThat(FloorMapLocationResolver.samePositions(one,
                Collections.singletonList(new FloorMapObject("user-99", "person", 1, 2))))
                .isFalse();
        assertThat(FloorMapLocationResolver.samePositions(one, new ArrayList<>())).isFalse();
        assertThat(FloorMapLocationResolver.samePositions(one, null)).isFalse();
        assertThat(FloorMapLocationResolver.samePositions(null, null)).isTrue();
    }

    // -----------------------------------------------------------------------

    /// An entity whose event named `ref` as the place it happened.
    private static FloorMapObject entityAt(final String ref) {
        final FloorMapObject object = new FloorMapObject("user-42", "person", 0, 0);
        object.setLocationRef(ref);
        return object;
    }

    @SuppressWarnings("SameParameterValue")
    private static Fact pointFact(final String key, final double x, final double y) {
        return new Fact(key, "desk", null,
                FloorMapTransformationMatrix.identity(), new double[]{x, y});
    }
}
