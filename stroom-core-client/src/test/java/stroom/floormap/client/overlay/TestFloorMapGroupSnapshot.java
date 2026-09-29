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
import stroom.floormap.shared.FloorMapGroup;
import stroom.floormap.shared.FloorMapJsonKeys;
import stroom.floormap.shared.FloorMapTransformationMatrix;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TestFloorMapGroupSnapshot {

    private static Fact area(final String key, final double half) {
        final double[][] local = new double[][]{
                {-half, -half}, {half, -half}, {half, half}, {-half, half}};
        return new Fact(key, FloorMapJsonKeys.AREA, null,
                FloorMapTransformationMatrix.translate(100, 100),
                new double[]{0, 0}, local, null, null);
    }

    private static Fact objectFact(final String key, final double x, final double y) {
        return new Fact(key, "gate", null,
                FloorMapTransformationMatrix.translate(x, y),
                new double[]{0, 0});
    }

    private static FloorMapObject event(final String id, final double x, final double y) {
        return new FloorMapObject(id, FloorMapJsonKeys.PERSON, x, y);
    }

    private static FloorMapGroup group(final String id, final String... members) {
        return new FloorMapGroup(id, id, null, Arrays.asList(members));
    }

    // ------------------------------------------------------------------------
    // Positioned-ness must NOT come from FloorMapAreaMembership
    // ------------------------------------------------------------------------

    /// The regression this class exists for. A map with **no areas at
    /// all** makes `FloorMapAreaMembership.compute` return
    /// `EMPTY`; sourcing positioned-ness from it would report zero members
    /// here.
    @Test
    void testPositionedCountOnMapWithNoAreas() {
        final List<FloorMapGroup> groups =
                Collections.singletonList(group("maintenance", "alice", "bob"));
        final List<FloorMapObject> events = Arrays.asList(
                event("alice", 10, 10), event("bob", 20, 20));
        final FloorMapAreaMembership membership =
                FloorMapAreaMembership.compute(Collections.emptyList(), events);

        // Precondition: the membership snapshot really is empty on this map.
        assertThat(membership).isSameAs(FloorMapAreaMembership.EMPTY);

        final FloorMapGroupSnapshot snapshot =
                FloorMapGroupSnapshot.compute(groups, Collections.emptyList(), events, membership);

        assertThat(snapshot.getPositionedCount("maintenance")).isEqualTo(2);
        assertThat(snapshot.getAreaCounts("maintenance")).isEmpty();
    }

    /// A member standing outside every area is still positioned — the second way
    /// `getEntityIds()` would have under-reported.
    @Test
    void testMemberOutsideEveryAreaIsStillPositioned() {
        final List<FloorMapGroup> groups = Collections.singletonList(group("g", "inside", "outside"));
        final List<Fact> facts = Collections.singletonList(area("bay", 20));
        final List<FloorMapObject> events = Arrays.asList(
                event("inside", 100, 100),      // within the bay at (100,100)
                event("outside", 900, 900));
        final FloorMapAreaMembership membership = FloorMapAreaMembership.compute(facts, events);

        assertThat(membership.getEntityIds()).containsExactly("inside");

        final FloorMapGroupSnapshot snapshot =
                FloorMapGroupSnapshot.compute(groups, facts, events, membership);

        assertThat(snapshot.getPositionedCount("g")).isEqualTo(2);
        assertThat(snapshot.getPositionedIds("g")).containsExactly("inside", "outside");
        assertThat(snapshot.getAreaCounts("g")).containsExactly(entry("bay", 1));
    }

    @Test
    void testMemberSeenInNeitherQueryIsNotPositioned() {
        final List<FloorMapGroup> groups = Collections.singletonList(group("g", "alice", "ghost"));
        final List<FloorMapObject> events = Collections.singletonList(event("alice", 10, 10));

        final FloorMapGroupSnapshot snapshot = FloorMapGroupSnapshot.compute(
                groups, Collections.emptyList(), events, FloorMapAreaMembership.EMPTY);

        assertThat(snapshot.getPositionedCount("g")).isEqualTo(1);
        assertThat(snapshot.getPositionedIds("g")).containsExactly("alice");
    }

    /// A static object fact counts as positioned — groups are generic over ids.
    @Test
    void testStaticFactMemberIsPositioned() {
        final List<FloorMapGroup> groups = Collections.singletonList(group("g", "gate-3"));
        final List<Fact> facts = Collections.singletonList(objectFact("gate-3", 50, 50));

        final FloorMapGroupSnapshot snapshot = FloorMapGroupSnapshot.compute(
                groups, facts, Collections.emptyList(), FloorMapAreaMembership.EMPTY);

        assertThat(snapshot.getPositionedCount("g")).isEqualTo(1);
    }

    /// An id present in both queries is counted once. Events are consulted first,
    /// matching `FloorMapAreaMembership`'s "a live event position beats a
    /// static fact twin" rule, so the positioned total and the area breakdown can
    /// never disagree about which ids count.
    @Test
    void testEventAndFactTwinCountOnce() {
        final List<FloorMapGroup> groups = Collections.singletonList(group("g", "alice"));
        final List<Fact> facts = Collections.singletonList(objectFact("alice", 900, 900));
        final List<FloorMapObject> events = Collections.singletonList(event("alice", 100, 100));
        final FloorMapAreaMembership membership = FloorMapAreaMembership.compute(
                Arrays.asList(area("bay", 20), objectFact("alice", 900, 900)), events);

        final FloorMapGroupSnapshot snapshot =
                FloorMapGroupSnapshot.compute(groups, facts, events, membership);

        assertThat(snapshot.getPositionedIds("g")).containsExactly("alice");
        // The bay contains alice via her *event* position, not her fact twin.
        assertThat(snapshot.getAreaCounts("g")).containsExactly(entry("bay", 1));
    }

    // ------------------------------------------------------------------------
    // Area breakdown
    // ------------------------------------------------------------------------

    /// Nested areas each count the member — membership is multi-valued.
    @Test
    void testNestedAreasBothCountTheMember() {
        final List<FloorMapGroup> groups = Collections.singletonList(group("g", "alice"));
        final List<Fact> facts = Arrays.asList(area("warehouse", 100), area("bay", 20));
        final List<FloorMapObject> events = Collections.singletonList(event("alice", 100, 100));

        final FloorMapGroupSnapshot snapshot = FloorMapGroupSnapshot.compute(
                groups, facts, events, FloorMapAreaMembership.compute(facts, events));

        assertThat(snapshot.getAreaCounts("g"))
                .containsOnly(entry("bay", 1), entry("warehouse", 1));
    }

    /// Areas are listed most-populated first, so the panel needs no re-sort.
    @Test
    void testAreaCountsOrderedMostPopulatedFirst() {
        final List<FloorMapGroup> groups =
                Collections.singletonList(group("g", "a1", "a2", "b1"));
        // Two disjoint areas: "alpha" at (100,100) holds two, "beta" at (500,500) one.
        final Fact alpha = area("alpha", 30);
        final Fact beta = new Fact("beta", FloorMapJsonKeys.AREA, null,
                FloorMapTransformationMatrix.translate(500, 500),
                new double[]{0, 0},
                new double[][]{{-30, -30}, {30, -30}, {30, 30}, {-30, 30}}, null, null);
        final List<Fact> facts = Arrays.asList(alpha, beta);
        final List<FloorMapObject> events = Arrays.asList(
                event("a1", 100, 100), event("a2", 110, 110), event("b1", 500, 500));

        final FloorMapGroupSnapshot snapshot = FloorMapGroupSnapshot.compute(
                groups, facts, events, FloorMapAreaMembership.compute(facts, events));

        assertThat(snapshot.getAreaCounts("g").keySet()).containsExactly("alpha", "beta");
        assertThat(snapshot.getAreaCounts("g")).containsExactly(entry("alpha", 2), entry("beta", 1));
    }

    /// Groups are independent: one group's members never leak into another's counts.
    @Test
    void testGroupsAreIndependent() {
        final List<FloorMapGroup> groups = Arrays.asList(
                group("maintenance", "alice"),
                group("security", "bob", "gate-3"));
        final List<Fact> facts = Collections.singletonList(objectFact("gate-3", 10, 10));
        final List<FloorMapObject> events = Arrays.asList(
                event("alice", 1, 1), event("bob", 2, 2));

        final FloorMapGroupSnapshot snapshot = FloorMapGroupSnapshot.compute(
                groups, facts, events, FloorMapAreaMembership.EMPTY);

        assertThat(snapshot.getPositionedCount("maintenance")).isEqualTo(1);
        assertThat(snapshot.getPositionedCount("security")).isEqualTo(2);
    }

    // ------------------------------------------------------------------------
    // Redraw guard and null-safety
    // ------------------------------------------------------------------------

    /// Content-based equality is what lets the panel skip redraws through ~300ms
    /// playback refreshes.
    @Test
    void testEqualityIsContentBased() {
        final List<FloorMapGroup> groups = Collections.singletonList(group("g", "alice", "bob"));
        final List<FloorMapObject> both = Arrays.asList(event("alice", 1, 1), event("bob", 2, 2));
        final List<FloorMapObject> moved = Arrays.asList(event("alice", 9, 9), event("bob", 8, 8));
        final List<FloorMapObject> one = Collections.singletonList(event("alice", 1, 1));

        final FloorMapGroupSnapshot a = FloorMapGroupSnapshot.compute(
                groups, null, both, FloorMapAreaMembership.EMPTY);
        final FloorMapGroupSnapshot sameMembersMoved = FloorMapGroupSnapshot.compute(
                groups, null, moved, FloorMapAreaMembership.EMPTY);
        final FloorMapGroupSnapshot fewer = FloorMapGroupSnapshot.compute(
                groups, null, one, FloorMapAreaMembership.EMPTY);

        // Movement alone is not a change to this snapshot — only membership of the
        // positioned set (and the area breakdown) is.
        assertThat(a).isEqualTo(sameMembersMoved);
        assertThat(a).hasSameHashCodeAs(sameMembersMoved);
        assertThat(a).isNotEqualTo(fewer);
    }

    @Test
    void testNoGroupsYieldsEmpty() {
        assertThat(FloorMapGroupSnapshot.compute(null, null, null, null))
                .isSameAs(FloorMapGroupSnapshot.EMPTY);
        assertThat(FloorMapGroupSnapshot.compute(Collections.emptyList(), null, null, null))
                .isSameAs(FloorMapGroupSnapshot.EMPTY);
    }

    @Test
    void testNullsAreSafe() {
        final List<FloorMapGroup> groups = Collections.singletonList(group("g", "alice"));

        final FloorMapGroupSnapshot snapshot =
                FloorMapGroupSnapshot.compute(groups, null, null, null);

        assertThat(snapshot).isSameAs(FloorMapGroupSnapshot.EMPTY);
        assertThat(snapshot.getPositionedCount(null)).isZero();
        assertThat(snapshot.getPositionedIds(null)).isEmpty();
        assertThat(snapshot.getAreaCounts(null)).isEmpty();
        assertThat(snapshot.getPositionedCount("unknown")).isZero();
    }

    @Test
    void testNullGroupEntryIsSkipped() {
        final List<FloorMapGroup> groups = Arrays.asList(null, group("g", "alice"));
        final List<FloorMapObject> events = Collections.singletonList(event("alice", 1, 1));

        final FloorMapGroupSnapshot snapshot = FloorMapGroupSnapshot.compute(
                groups, null, events, FloorMapAreaMembership.EMPTY);

        assertThat(snapshot.getPositionedCount("g")).isEqualTo(1);
    }

    private static org.assertj.core.data.MapEntry<String, Integer> entry(final String key,
                                                                        final int value) {
        return org.assertj.core.data.MapEntry.entry(key, value);
    }
}
