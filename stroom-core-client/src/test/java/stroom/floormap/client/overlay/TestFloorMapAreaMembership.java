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

package stroom.floormap.client.overlay;

import stroom.floormap.client.model.Fact;
import stroom.floormap.client.model.FloorMapObject;
import stroom.floormap.shared.FloorMapJsonKeys;
import stroom.floormap.shared.FloorMapTransformationMatrix;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TestFloorMapAreaMembership {

    /**
     * An axis-aligned rectangular area, stored the way the editor stores one:
     * vertices centred on the centroid in a local frame, placed by a
     * translation to the centroid.
     */
    private static Fact area(final String key,
                             final double centreX,
                             final double centreY,
                             final double halfWidth,
                             final double halfHeight) {
        final double[][] local = new double[][]{
                {-halfWidth, -halfHeight},
                {halfWidth, -halfHeight},
                {halfWidth, halfHeight},
                {-halfWidth, halfHeight}};
        return new Fact(key, FloorMapJsonKeys.AREA, null,
                FloorMapTransformationMatrix.translate(centreX, centreY),
                new double[]{0, 0}, local, null, null);
    }

    private static Fact pointFact(final String key,
                                  final String type,
                                  final double mapX,
                                  final double mapY) {
        return new Fact(key, type, null,
                FloorMapTransformationMatrix.identity(), new double[]{mapX, mapY});
    }

    private static FloorMapObject event(final String id, final double x, final double y) {
        return new FloorMapObject(id, FloorMapJsonKeys.PERSON, x, y);
    }

    // -----------------------------------------------------------------------
    // Basic containment
    // -----------------------------------------------------------------------

    @Test
    void testEventInsideArea() {
        final FloorMapAreaMembership membership = FloorMapAreaMembership.compute(
                Collections.singletonList(area("bay", 100, 100, 50, 50)),
                Collections.singletonList(event("alice@example.com", 110, 90)));

        assertThat(membership.getInnermostAreaKey("alice@example.com")).isEqualTo("bay");
        assertThat(membership.getOccupants("bay")).containsExactly("alice@example.com");
        assertThat(membership.getOccupantCount("bay")).isEqualTo(1);
    }

    @Test
    void testEventOutsideArea() {
        final FloorMapAreaMembership membership = FloorMapAreaMembership.compute(
                Collections.singletonList(area("bay", 100, 100, 50, 50)),
                Collections.singletonList(event("alice@example.com", 500, 500)));

        assertThat(membership.getInnermostAreaKey("alice@example.com")).isNull();
        assertThat(membership.getAreaKeys("alice@example.com")).isEmpty();
        assertThat(membership.getOccupants("bay")).isEmpty();
        assertThat(membership.getOccupantCount("bay")).isZero();
    }

    /**
     * An entity with no known position at this instant is simply absent — the
     * roster keeps its row, but membership has nothing to say about it.
     */
    @Test
    void testUnknownEntity() {
        final FloorMapAreaMembership membership = FloorMapAreaMembership.compute(
                Collections.singletonList(area("bay", 100, 100, 50, 50)),
                Collections.emptyList());

        assertThat(membership.getAreaKeys("nobody")).isEmpty();
        assertThat(membership.getInnermostAreaKey("nobody")).isNull();
        assertThat(membership.getAreaKeys(null)).isEmpty();
    }

    @Test
    void testNoAreasGivesEmpty() {
        final FloorMapAreaMembership membership = FloorMapAreaMembership.compute(
                Collections.singletonList(pointFact("gate1", "gate", 5, 5)),
                Collections.singletonList(event("alice", 5, 5)));

        assertThat(membership.getAreaKeys()).isEmpty();
        assertThat(membership.getOccupantCounts()).isEmpty();
    }

    @Test
    void testNullInputs() {
        assertThat(FloorMapAreaMembership.compute(null, null).getAreaKeys()).isEmpty();
    }

    // -----------------------------------------------------------------------
    // Multi-valued / nested membership
    // -----------------------------------------------------------------------

    /**
     * Nested areas both contain the entity, and the list is ordered innermost
     * (smallest) first so the head is the most specific answer — regardless of
     * the order the areas arrived in.
     */
    @Test
    void testNestedAreasOrderedInnermostFirst() {
        final List<Fact> facts = Arrays.asList(
                area("warehouse", 100, 100, 100, 100),
                area("bay", 100, 100, 20, 20));
        final FloorMapAreaMembership membership = FloorMapAreaMembership.compute(
                facts, Collections.singletonList(event("alice", 100, 100)));

        assertThat(membership.getAreaKeys("alice")).containsExactly("bay", "warehouse");
        assertThat(membership.getInnermostAreaKey("alice")).isEqualTo("bay");
        // Both hold alice and nothing else — the nested bay is not an occupant.
        assertThat(membership.getOccupantCount("warehouse")).isEqualTo(1);
        assertThat(membership.getOccupantCount("bay")).isEqualTo(1);
    }

    /** The same holds when the smaller area is supplied first. */
    @Test
    void testNestedAreasOrderIndependentOfInput() {
        final List<Fact> facts = Arrays.asList(
                area("bay", 100, 100, 20, 20),
                area("warehouse", 100, 100, 100, 100));
        final FloorMapAreaMembership membership = FloorMapAreaMembership.compute(
                facts, Collections.singletonList(event("alice", 100, 100)));

        assertThat(membership.getAreaKeys("alice")).containsExactly("bay", "warehouse");
    }

    /** A point in the outer area but outside the inner one is only in the outer. */
    @Test
    void testInOuterAreaOnly() {
        final List<Fact> facts = Arrays.asList(
                area("warehouse", 100, 100, 100, 100),
                area("bay", 100, 100, 20, 20));
        final FloorMapAreaMembership membership = FloorMapAreaMembership.compute(
                facts, Collections.singletonList(event("alice", 180, 180)));

        assertThat(membership.getAreaKeys("alice")).containsExactly("warehouse");
    }

    /**
     * Areas are never located inside anything — only objects and users are. An
     * area drawn wholly within another reports no containing area, and does not
     * count as an occupant of it.
     */
    @Test
    void testAreaIsNeverInsideAnotherArea() {
        final List<Fact> facts = Arrays.asList(
                area("warehouse", 100, 100, 100, 100),
                area("bay", 100, 100, 20, 20));
        final FloorMapAreaMembership membership = FloorMapAreaMembership.compute(
                facts, Collections.emptyList());

        assertThat(membership.getAreaKeys("bay")).isEmpty();
        assertThat(membership.getAreaKeys("warehouse")).isEmpty();
        assertThat(membership.getOccupants("warehouse")).isEmpty();
        assertThat(membership.getOccupants("bay")).isEmpty();
        assertThat(membership.getEntityIds()).isEmpty();
        // Both areas are still known as areas — only the nesting relation is gone.
        assertThat(membership.getAreaKeys()).containsExactlyInAnyOrder("warehouse", "bay");
    }

    /**
     * A nested area does not inflate the enclosing area's occupant count — the
     * count is objects and users only, which is what the canvas badge shows.
     */
    @Test
    void testNestedAreaDoesNotCountAsOccupant() {
        final List<Fact> facts = Arrays.asList(
                area("warehouse", 100, 100, 100, 100),
                area("bay", 100, 100, 20, 20));
        final FloorMapAreaMembership membership = FloorMapAreaMembership.compute(
                facts, Collections.singletonList(event("alice", 100, 100)));

        assertThat(membership.getOccupants("warehouse")).containsExactly("alice");
        assertThat(membership.getOccupants("bay")).containsExactly("alice");
    }

    /** Objects and users near an area's edge are still located inside it. */
    @Test
    void testPointOccupantsNearEdgeAreLocated() {
        final List<Fact> facts = Arrays.asList(
                area("bay", 100, 100, 50, 50),
                pointFact("gate1", "gate", 145, 145));
        final FloorMapAreaMembership membership = FloorMapAreaMembership.compute(
                facts, Collections.singletonList(event("alice", 149, 149)));

        assertThat(membership.getInnermostAreaKey("gate1")).isEqualTo("bay");
        assertThat(membership.getInnermostAreaKey("alice")).isEqualTo("bay");
    }

    // -----------------------------------------------------------------------
    // Which facts count
    // -----------------------------------------------------------------------

    /** A static point fact (a gate, a computer) is located inside its area. */
    @Test
    void testStaticFactInsideArea() {
        final List<Fact> facts = Arrays.asList(
                area("bay", 100, 100, 50, 50),
                pointFact("computer1", "computer", 120, 120));
        final FloorMapAreaMembership membership =
                FloorMapAreaMembership.compute(facts, Collections.emptyList());

        assertThat(membership.getInnermostAreaKey("computer1")).isEqualTo("bay");
    }

    /**
     * A background is never an occupant — its placement origin is arbitrary, so
     * "Background is in the loading bay" would be noise.
     */
    @Test
    void testBackgroundIsNeverAnOccupant() {
        final List<Fact> facts = Arrays.asList(
                area("bay", 100, 100, 50, 50),
                pointFact("plan", FloorMapJsonKeys.BACKGROUND, 100, 100));
        final FloorMapAreaMembership membership =
                FloorMapAreaMembership.compute(facts, Collections.emptyList());

        assertThat(membership.getAreaKeys("plan")).isEmpty();
        assertThat(membership.getOccupants("bay")).isEmpty();
    }

    /**
     * A fact carrying its own image renders as that image, not as a polygon, so
     * it must not be treated as an area either — the test and the paint agree.
     */
    @Test
    void testImageBearingFactIsNotAnArea() {
        final double[][] local = new double[][]{{-5, -5}, {5, -5}, {5, 5}, {-5, 5}};
        final Fact imageWithVertices = new Fact("odd", FloorMapJsonKeys.AREA, "/assets/x.png",
                FloorMapTransformationMatrix.translate(100, 100),
                new double[]{0, 0}, local, null, null);

        assertThat(FloorMapAreaMembership.isAreaFact(imageWithVertices)).isFalse();

        final FloorMapAreaMembership membership = FloorMapAreaMembership.compute(
                Collections.singletonList(imageWithVertices),
                Collections.singletonList(event("alice", 100, 100)));
        assertThat(membership.getAreaKeys()).isEmpty();
    }

    /** Null/empty ids are ignored rather than producing phantom rows. */
    @Test
    void testUnusableIdsIgnored() {
        final List<Fact> facts = Arrays.asList(
                area("bay", 100, 100, 50, 50),
                pointFact(null, "gate", 100, 100),
                pointFact("", "gate", 100, 100));
        final FloorMapAreaMembership membership = FloorMapAreaMembership.compute(
                facts, Collections.singletonList(event(null, 100, 100)));

        assertThat(membership.getOccupants("bay")).isEmpty();
    }

    /**
     * When an id exists both as an event and as an image-bearing fact twin, the
     * live event position wins — the twin must not overwrite it with the fact's
     * static placement.
     */
    @Test
    void testEventPositionWinsOverFactTwin() {
        final List<Fact> facts = Arrays.asList(
                area("bay", 100, 100, 50, 50),
                pointFact("alice", FloorMapJsonKeys.PERSON, 900, 900));
        final FloorMapAreaMembership membership = FloorMapAreaMembership.compute(
                facts, Collections.singletonList(event("alice", 100, 100)));

        assertThat(membership.getInnermostAreaKey("alice")).isEqualTo("bay");
        assertThat(membership.getOccupants("bay")).containsExactly("alice");
    }

    // -----------------------------------------------------------------------
    // Aggregates
    // -----------------------------------------------------------------------

    @Test
    void testOccupantCountsAcrossAreas() {
        final List<Fact> facts = Arrays.asList(
                area("bay", 100, 100, 50, 50),
                area("office", 500, 500, 50, 50));
        final FloorMapAreaMembership membership = FloorMapAreaMembership.compute(
                facts,
                Arrays.asList(
                        event("alice", 100, 100),
                        event("bob", 120, 80),
                        event("carol", 500, 500),
                        event("dave", 900, 900)));

        assertThat(membership.getOccupantCount("bay")).isEqualTo(2);
        assertThat(membership.getOccupantCount("office")).isEqualTo(1);
        assertThat(membership.getOccupantCounts())
                .containsEntry("bay", 2)
                .containsEntry("office", 1);
        assertThat(membership.getAreaKeys()).containsExactlyInAnyOrder("bay", "office");
        assertThat(membership.isArea("bay")).isTrue();
        assertThat(membership.isArea("alice")).isFalse();
    }

    /** Empty areas are absent from the counts map, so no badge is drawn. */
    @Test
    void testEmptyAreaAbsentFromCounts() {
        final FloorMapAreaMembership membership = FloorMapAreaMembership.compute(
                Collections.singletonList(area("bay", 100, 100, 50, 50)),
                Collections.singletonList(event("alice", 900, 900)));

        assertThat(membership.getOccupantCounts()).doesNotContainKey("bay");
    }

    /**
     * Only entities that are inside something appear in the entity set — it
     * drives the tracking panel's "last seen in" history, which has nothing to
     * record for an entity in no area.
     */
    @Test
    void testEntityIds() {
        final List<Fact> facts = Arrays.asList(
                area("bay", 100, 100, 50, 50),
                pointFact("computer1", "computer", 120, 120));
        final FloorMapAreaMembership membership = FloorMapAreaMembership.compute(
                facts, Arrays.asList(event("alice", 100, 100), event("dave", 900, 900)));

        assertThat(membership.getEntityIds()).containsExactlyInAnyOrder("alice", "computer1");
    }

    // -----------------------------------------------------------------------
    // equals — used to skip redraws when a refresh changed nothing
    // -----------------------------------------------------------------------

    /**
     * Two snapshots of the same unchanged scene are equal, so a playback refresh
     * that moved nobody between areas costs no redraw.
     */
    @Test
    void testEqualForUnchangedScene() {
        final List<Fact> facts = Collections.singletonList(area("bay", 100, 100, 50, 50));
        final List<FloorMapObject> events = Collections.singletonList(event("alice", 100, 100));

        assertThat(FloorMapAreaMembership.compute(facts, events))
                .isEqualTo(FloorMapAreaMembership.compute(facts, events))
                .hasSameHashCodeAs(FloorMapAreaMembership.compute(facts, events));
    }

    /** Moving an entity out of an area makes the snapshots unequal. */
    @Test
    void testNotEqualWhenEntityLeaves() {
        final List<Fact> facts = Collections.singletonList(area("bay", 100, 100, 50, 50));

        assertThat(FloorMapAreaMembership.compute(facts,
                Collections.singletonList(event("alice", 100, 100))))
                .isNotEqualTo(FloorMapAreaMembership.compute(facts,
                        Collections.singletonList(event("alice", 900, 900))));
    }

    /**
     * Moving <em>within</em> the same area leaves the snapshots equal — position
     * is not part of the containment relation, so a walk across a room costs no
     * grid redraw.
     */
    @Test
    void testEqualWhenMovingWithinSameArea() {
        final List<Fact> facts = Collections.singletonList(area("bay", 100, 100, 50, 50));

        assertThat(FloorMapAreaMembership.compute(facts,
                Collections.singletonList(event("alice", 90, 90))))
                .isEqualTo(FloorMapAreaMembership.compute(facts,
                        Collections.singletonList(event("alice", 110, 110))));
    }

    /** A rotated area still contains the points that visually fall inside it. */
    @Test
    void testRotatedArea() {
        final double[][] local = new double[][]{{-10, -10}, {10, -10}, {10, 10}, {-10, 10}};
        final Fact rotated = new Fact("bay", FloorMapJsonKeys.AREA, null,
                FloorMapTransformationMatrix.translate(100, 100)
                        .multiply(FloorMapTransformationMatrix.rotate(45)),
                new double[]{0, 0}, local, null, null);

        final FloorMapAreaMembership membership = FloorMapAreaMembership.compute(
                Collections.singletonList(rotated),
                Arrays.asList(
                        // The centre stays inside under any rotation.
                        event("centre", 100, 100),
                        // A former corner is thrown outside by the rotation.
                        event("corner", 109, 109)));

        assertThat(membership.getInnermostAreaKey("centre")).isEqualTo("bay");
        assertThat(membership.getInnermostAreaKey("corner")).isNull();
    }
}
