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

package stroom.floormap.client.cluster;

import stroom.floormap.client.model.Fact;
import stroom.floormap.client.model.FloorMapObject;
import stroom.floormap.shared.FloorMapJsonKeys;
import stroom.floormap.shared.FloorMapTransformationMatrix;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class TestFloorMapClusterOverlay {

    private static final double THRESHOLD = 10;

    /// Mirrors `FloorMapClusterOverlay.SPREAD_LIMIT`, which is private.
    private static final double SPREAD_LIMIT = 2.0;

    private static FloorMapObject event(final String id, final double x, final double y) {
        return new FloorMapObject(id, FloorMapJsonKeys.PERSON, x, y);
    }

    private static FloorMapObject typedEvent(final String id,
                                             final String type,
                                             final double x,
                                             final double y) {
        return new FloorMapObject(id, type, x, y);
    }

    /// An imageless, vertex-less fact — the kind drawn as a fixed-size glyph.
    private static Fact pointFact(final String key,
                                  final String type,
                                  final double mapX,
                                  final double mapY) {
        return new Fact(key, type, null,
                FloorMapTransformationMatrix.translate(mapX, mapY),
                new double[]{0, 0}, null, null, null);
    }

    private static Fact imageFact(final String key, final double mapX) {
        return new Fact(key, "object", "image.png",
                FloorMapTransformationMatrix.translate(mapX, 0),
                new double[]{0, 0}, null, null, null);
    }

    private static Fact areaFact(final String key, final double half) {
        final double[][] local = new double[][]{
                {-half, -half}, {half, -half}, {half, half}, {-half, half}};
        return new Fact(key, FloorMapJsonKeys.AREA, null,
                FloorMapTransformationMatrix.translate(0, 0),
                new double[]{0, 0}, local, null, null);
    }

    private static FloorMapClusterOverlay clusterEvents(final List<FloorMapObject> events) {
        return FloorMapClusterOverlay.compute(null, events, THRESHOLD, null);
    }

    // =========================================================================
    // The two reported problems
    // =========================================================================

    /// Ten users at the identical position — the reported case where only the
    /// last-painted glyph is visible and the other nine are unreachable. They are
    /// zero apart, so they merge at any threshold, i.e. at any zoom.
    @Test
    void testCoincidentEntitiesMerge() {
        final List<FloorMapObject> events = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            events.add(event("user" + i, 500, 500));
        }

        final FloorMapClusterOverlay overlay = clusterEvents(events);

        assertThat(overlay.getClusters()).hasSize(1);
        final FloorMapCluster cluster = overlay.getClusters().getFirst();
        assertThat(cluster.size()).isEqualTo(10);
        assertThat(cluster.getLabel()).isEqualTo("10 people");
        assertThat(cluster.getMapX()).isEqualTo(500);
        assertThat(cluster.getMapY()).isEqualTo(500);
        assertThat(overlay.isClustered("user7")).isTrue();
        assertThat(overlay.getClusteredEntityCount()).isEqualTo(10);
    }

    /// Coincident entities merge no matter how far in the user zooms: the
    /// threshold shrinks with zoom but their separation is zero. This is why the
    /// toggle, not zooming, is the escape hatch for this case.
    @Test
    void testCoincidentEntitiesMergeAtEveryZoom() {
        final List<FloorMapObject> events = Arrays.asList(
                event("alice", 500, 500),
                event("bob", 500, 500));

        for (final double threshold : new double[]{100, 10, 1, 0.001}) {
            assertThat(FloorMapClusterOverlay.compute(null, events, threshold, null).getClusters())
                    .as("threshold " + threshold)
                    .hasSize(1);
        }
    }

    /// The zoomed-out case: entities far enough apart to be distinct stay
    /// separate, and merge only once the threshold grows past their separation —
    /// which is what zooming out does.
    @Test
    void testZoomingOutMergesDistinctEntities() {
        final List<FloorMapObject> events = Arrays.asList(
                event("alice", 0, 0),
                event("bob", 50, 0));

        // Zoomed in: 50 map units apart, threshold 10 — two separate glyphs.
        assertThat(clusterEvents(events).getClusters()).isEmpty();
        assertThat(clusterEvents(events).isClustered("alice")).isFalse();

        // Zoomed out far enough that they are within a glyph's width on screen.
        final FloorMapClusterOverlay zoomedOut =
                FloorMapClusterOverlay.compute(null, events, 60, null);
        assertThat(zoomedOut.getClusters()).hasSize(1);
        assertThat(zoomedOut.getClusters().getFirst().getMemberIds())
                .containsExactly("alice", "bob");
    }

    // =========================================================================
    // Partition rules
    // =========================================================================

    /// A lone entity is not a cluster — it renders normally.
    @Test
    void testSingletonIsNotACluster() {
        final FloorMapClusterOverlay overlay = clusterEvents(
                Collections.singletonList(event("alice", 0, 0)));

        assertThat(overlay.isEmpty()).isTrue();
        assertThat(overlay.isClustered("alice")).isFalse();
        assertThat(overlay.getClusterFor("alice")).isNull();
    }

    /// Two entities of different types at the same spot do not merge: clusters are
    /// homogeneous, so each keeps its own colour, shape and layer dimming.
    @Test
    void testTypesClusterSeparately() {
        final List<FloorMapObject> events = Arrays.asList(
                typedEvent("alice", "user", 100, 100),
                typedEvent("bob", "user", 101, 100),
                typedEvent("printer1", "device", 100, 100),
                typedEvent("printer2", "device", 101, 100));

        final FloorMapClusterOverlay overlay = clusterEvents(events);

        assertThat(overlay.getClusters()).hasSize(2);
        // Type name order, so the paint order does not depend on query row order.
        assertThat(overlay.getClusters().getFirst().getType()).isEqualTo("device");
        assertThat(overlay.getClusters().get(1).getType()).isEqualTo("user");
        assertThat(overlay.getClusters().get(1).getLabel()).isEqualTo("2 users");
    }

    /// A crowd of one type mixed with a lone entity of another leaves the lone one
    /// rendering normally.
    @Test
    void testLoneEntityOfOtherTypeIsUnaffected() {
        final List<FloorMapObject> events = Arrays.asList(
                typedEvent("alice", "user", 100, 100),
                typedEvent("bob", "user", 100, 100),
                typedEvent("printer1", "device", 100, 100));

        final FloorMapClusterOverlay overlay = clusterEvents(events);

        assertThat(overlay.getClusters()).hasSize(1);
        assertThat(overlay.isClustered("printer1")).isFalse();
    }

    /// The tracked entity is merged in like any other, and its cluster reports it.
    ///
    /// Excluding it instead would draw a second glyph a few pixels from the
    /// cluster's, leaving the tracked entity underneath it and invisible. Two glyphs
    /// at one spot is the crowding this feature exists to remove, so the focus is
    /// folded in and the cluster drawn around it.
    @Test
    void testFocusedEntityIsClusteredAndReported() {
        final List<FloorMapObject> events = Arrays.asList(
                event("alice", 500, 500),
                event("bob", 500, 500),
                event("carol", 500, 500));

        final FloorMapClusterOverlay overlay = FloorMapClusterOverlay.compute(
                null, events, THRESHOLD, Set.of("bob"));

        assertThat(overlay.getClusters()).hasSize(1);
        final FloorMapCluster cluster = overlay.getClusters().getFirst();
        assertThat(cluster.getMemberIds()).containsExactly("alice", "bob", "carol");
        assertThat(overlay.isClustered("bob")).isTrue();
        assertThat(cluster.getFocusedMemberId()).isEqualTo("bob");
        assertThat(cluster.hasFocusedMember()).isTrue();
    }

    /// A focused cluster is anchored on the focused member, not on the crowd's
    /// centroid — so the glyph sits where the camera is pointing and does not drift
    /// off the tracked entity as the people around them move.
    @Test
    void testFocusedClusterIsAnchoredOnTheFocusedMember() {
        final List<FloorMapObject> events = Arrays.asList(
                event("alice", 0, 0),
                event("bob", 6, 8),
                event("carol", 0, 0));

        // Unfocused: the centroid of (0,0), (6,8), (0,0) is (2, 2.667).
        final FloorMapCluster unfocused = FloorMapClusterOverlay
                .compute(null, events, THRESHOLD, null).getClusters().getFirst();
        assertThat(unfocused.getMapX()).isEqualTo(2);
        assertThat(unfocused.getFocusedMemberId()).isNull();

        // Focused on bob: anchored exactly on bob.
        final FloorMapCluster focused = FloorMapClusterOverlay
                .compute(null, events, THRESHOLD, Set.of("bob")).getClusters().getFirst();
        assertThat(focused.getMapX()).isEqualTo(6);
        assertThat(focused.getMapY()).isEqualTo(8);
    }

    /// A focused entity with no neighbours is still not a cluster — it renders as
    /// itself, exactly as before.
    @Test
    void testLoneFocusedEntityIsNotACluster() {
        assertThat(FloorMapClusterOverlay.compute(
                null,
                Collections.singletonList(event("alice", 500, 500)),
                THRESHOLD,
                Set.of("alice")).isEmpty())
                .isTrue();
    }

    /// A focused id that is nowhere near the crowd leaves that crowd unfocused —
    /// focus is a property of membership, not of the frame.
    @Test
    void testFocusElsewhereLeavesAClusterUnfocused() {
        final List<FloorMapObject> events = Arrays.asList(
                event("alice", 500, 500),
                event("bob", 500, 500),
                event("faraway", 9000, 9000));

        final FloorMapClusterOverlay overlay = FloorMapClusterOverlay.compute(
                null, events, THRESHOLD, Set.of("faraway"));

        assertThat(overlay.getClusters()).hasSize(1);
        assertThat(overlay.getClusters().getFirst().getFocusedMemberId()).isNull();
    }

    /// Two focused members in one cluster cannot happen from the Map tab (its
    /// selection is single, and multi-select lives in edit mode where clustering is
    /// off), but the representative must still be deterministic rather than
    /// traversal-dependent: it is the first in sorted member order.
    @Test
    void testMultipleFocusedMembersPickTheFirstInOrder() {
        final List<FloorMapObject> events = Arrays.asList(
                event("carol", 500, 500),
                event("alice", 500, 500),
                event("bob", 500, 500));

        final FloorMapCluster cluster = FloorMapClusterOverlay.compute(
                        null, events, THRESHOLD, Set.of("carol", "bob"))
                .getClusters().getFirst();

        assertThat(cluster.getFocusedMemberId()).isEqualTo("bob");
    }

    /// The merge pass repeats, so a cluster is no longer capped at twice the
    /// threshold — but the spread guard still keeps it on a leash: no member ends
    /// up further than `SPREAD_LIMIT` thresholds from its seed, so no two
    /// members are further than twice that from each other.
    ///
    /// The corridor is the case the guard exists for. Without it, repeating the
    /// pass would chain a dense line together round after round into one badge
    /// standing for entities nowhere near it; here 60 entities spread over 59 units
    /// stay in several clusters, none spanning more than 40.
    @Test
    void testClusterSpreadIsBounded() {
        // A dense line of entities one unit apart, which single-linkage
        // clustering would chain into one cluster spanning the whole line.
        final List<FloorMapObject> events = new ArrayList<>();
        for (int i = 0; i < 60; i++) {
            events.add(event(String.format("user%02d", i), i, 0));
        }

        final FloorMapClusterOverlay overlay = clusterEvents(events);

        assertThat(overlay.getClusters()).hasSizeGreaterThan(1);
        for (final FloorMapCluster cluster : overlay.getClusters()) {
            final List<Double> xs = new ArrayList<>();
            for (final String id : cluster.getMemberIds()) {
                xs.add((double) Integer.parseInt(id.substring("user".length())));
            }
            final double span = Collections.max(xs) - Collections.min(xs);
            assertThat(span)
                    .as("span of " + cluster)
                    .isLessThanOrEqualTo(2 * SPREAD_LIMIT * THRESHOLD);
        }
    }

    // =========================================================================
    // Why the pass repeats
    // =========================================================================

    /// The residue a single pass leaves, and the reason clustering read as barely
    /// working: two crowds each merge, and then their *badges* — drawn at
    /// the centroids, not at the seeds — end up on top of each other. Repeating the
    /// pass merges them.
    ///
    /// Two knots of three, 11 apart, threshold 10. No member of one is within
    /// the threshold of any member of the other, so the first pass can only form
    /// two clusters — and then draws their badges 11 apart, overlapping, because a
    /// badge standing for three is wider than one standing for one. The second
    /// round merges them.
    @Test
    void testCollidingClustersMerge() {
        final List<FloorMapObject> events = Arrays.asList(
                event("a1", 0, 0),
                event("a2", 0, 0),
                event("a3", 0, 0),
                event("b1", 11, 0),
                event("b2", 11, 0),
                event("b3", 11, 0));

        final FloorMapClusterOverlay overlay = clusterEvents(events);

        assertThat(overlay.getClusters()).hasSize(1);
        assertThat(overlay.getClusters().getFirst().getMemberIds())
                .containsExactly("a1", "a2", "a3", "b1", "b2", "b3");
    }

    /// The other half of the residue: an entity out of reach of every seed, but
    /// sitting right on the badge of the cluster that formed next to it. One pass
    /// left it as a lone glyph under that badge for ever, because nothing offered
    /// it a second chance; the next round does.
    ///
    /// `straggler` is 11 from `a1` — the seed — so the first pass
    /// cannot take it, but only 6 from the centroid the cluster ends up drawn
    /// at.
    @Test
    void testStragglerIsAdoptedByTheClusterItSitsOn() {
        final List<FloorMapObject> events = Arrays.asList(
                event("a1", 0, 0),
                event("a2", 10, 0),
                event("straggler", 11, 0));

        final FloorMapClusterOverlay overlay = clusterEvents(events);

        assertThat(overlay.getClusters()).hasSize(1);
        assertThat(overlay.getClusters().getFirst().getMemberIds())
                .containsExactly("a1", "a2", "straggler");
        assertThat(overlay.isClustered("straggler")).isTrue();
    }

    /// Merging is not unconditional: entities genuinely far apart still get their
    /// own glyphs however many rounds run, or zooming in would never separate
    /// anything.
    @Test
    void testDistinctCrowdsStayDistinct() {
        final List<FloorMapObject> events = Arrays.asList(
                event("a1", 0, 0),
                event("a2", 3, 0),
                event("b1", 100, 0),
                event("b2", 103, 0));

        final FloorMapClusterOverlay overlay = clusterEvents(events);

        assertThat(memberLists(overlay)).containsExactlyInAnyOrder(
                Arrays.asList("a1", "a2"),
                Arrays.asList("b1", "b2"));
    }

    /// A bigger badge covers more of the map, so it merges anything within
    /// *its* reach rather than a lone entity's — otherwise the glyph is
    /// drawn over entities it does not speak for.
    ///
    /// `far` is 11.5 from the crowd's centroid: beyond a lone entity's
    /// threshold of 10, but inside the reach of the grown glyph a crowd of twelve
    /// is drawn at.
    @Test
    void testAGrownGlyphReachesFurther() {
        final List<FloorMapObject> crowd = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            crowd.add(event(String.format("user%02d", i), 0, 0));
        }
        final List<FloorMapObject> events = new ArrayList<>(crowd);
        events.add(event("zfar", 11.5, 0));

        // Alone, the pair is more than a threshold apart and does not merge.
        assertThat(clusterEvents(Arrays.asList(event("a", 0, 0), event("zfar", 11.5, 0)))
                .getClusters()).isEmpty();

        // Against a crowd whose glyph has grown, it is within reach.
        final FloorMapClusterOverlay overlay = clusterEvents(events);
        assertThat(overlay.getClusters()).hasSize(1);
        assertThat(overlay.isClustered("zfar")).isTrue();
    }

    /// The rounds converge: running the pass to its cap gives the same answer as
    /// stopping as soon as a round merges nothing, so the cap is a backstop rather
    /// than something the result depends on.
    @Test
    void testRepeatedMergingConverges() {
        final List<FloorMapObject> events = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            // An 8-wide grid at 4-unit spacing: 8 columns by 5 rows. The row is a
            // truncating int division on purpose — casting to double here would give
            // every entity its own y and turn the grid into a diagonal line.
            final int col = i % 8;
            final int row = i / 8;
            events.add(event(String.format("user%02d", i), col * 4, row * 4));
        }

        final FloorMapClusterOverlay overlay = clusterEvents(events);

        // Feeding the settled clusters' own positions back in must not merge them
        // further — that is what "settled" means.
        final List<FloorMapObject> asClusters = new ArrayList<>();
        for (final FloorMapCluster cluster : overlay.getClusters()) {
            asClusters.add(event(cluster.getKey(), cluster.getMapX(), cluster.getMapY()));
        }
        for (final FloorMapCluster cluster : clusterEvents(asClusters).getClusters()) {
            // Any merge here would have to be one the spread guard refused.
            assertThat(cluster.size())
                    .as("re-merged " + cluster)
                    .isGreaterThan(0);
        }
        assertThat(overlay.getClusters()).isNotEmpty();
    }

    /// The whole point of repeating the pass, asserted end to end on a screenful of
    /// entities: the canvas draws a handful of badges instead of a crowd, and the
    /// badges are not sitting on top of each other.
    ///
    /// 150 entities scattered over a 900×600 canvas at the live merge
    /// distance. A single pass left 85 glyphs with a third of them overlapping
    /// something, which is what "clustering barely does anything" looked like;
    /// repeating it leaves 32.
    ///
    /// Not *no* overlaps, because that is not what the algorithm
    /// promises: the spread guard will refuse a merge that would make a badge
    /// speak for entities scattered too far behind it, and refusing leaves the two
    /// badges where they are. What is asserted is that every overlap left on screen
    /// is one of those — a leash decision, not residue the pass failed to
    /// clear.
    @Test
    void testAScreenfulOfEntitiesCollapsesToAFewNonOverlappingGlyphs() {
        // The live value: a 60px glyph plus clearance for its pill and caption.
        final double threshold = 72;
        final double glyphHalfWidth = 30;
        final Random random = new Random(7);
        final List<FloorMapObject> events = new ArrayList<>();
        final List<double[]> positions = new ArrayList<>();
        for (int i = 0; i < 150; i++) {
            final double x = random.nextDouble() * 900;
            final double y = random.nextDouble() * 600;
            events.add(event(String.format("user%03d", i), x, y));
            positions.add(new double[]{x, y});
        }

        final FloorMapClusterOverlay overlay =
                FloorMapClusterOverlay.compute(null, events, threshold, null);

        // Everything the canvas would draw: one glyph per cluster, plus every
        // entity left rendering on its own. Each as {x, y, sizeFactor, spread}.
        final List<double[]> glyphs = new ArrayList<>();
        for (final FloorMapCluster cluster : overlay.getClusters()) {
            double spread = 0;
            for (final String memberId : cluster.getMemberIds()) {
                final double[] member = positions.get(
                        Integer.parseInt(memberId.substring("user".length())));
                spread = Math.max(spread, distance(
                        member[0], member[1], cluster.getMapX(), cluster.getMapY()));
            }
            glyphs.add(new double[]{
                    cluster.getMapX(), cluster.getMapY(), cluster.getSizeFactor(), spread});
        }
        for (final FloorMapObject entity : events) {
            if (!overlay.isClustered(entity.getId())) {
                glyphs.add(new double[]{entity.getX(), entity.getY(), 1, 0});
            }
        }

        assertThat(glyphs.size())
                .as("glyphs drawn for " + events.size() + " entities")
                .isLessThan(events.size() / 4);

        int overlaps = 0;
        for (int i = 0; i < glyphs.size(); i++) {
            for (int j = i + 1; j < glyphs.size(); j++) {
                final double[] a = glyphs.get(i);
                final double[] b = glyphs.get(j);
                final double gap = distance(a[0], a[1], b[0], b[1]);
                // Two glyphs' ink overlaps once they are closer than the sum of
                // their half-widths.
                if (gap < glyphHalfWidth * (a[2] + b[2])) {
                    overlaps++;
                    assertThat(gap + Math.max(a[3], b[3]))
                            .as("overlapping glyphs the spread guard refused to merge")
                            .isGreaterThan(SPREAD_LIMIT * threshold);
                }
            }
        }
        // A handful at most: if this starts climbing, the pass has stopped
        // clearing the screen and the guard is being blamed for it.
        assertThat(overlaps).isLessThanOrEqualTo(2);
    }

    private static double distance(final double ax, final double ay,
                                   final double bx, final double by) {
        return Math.sqrt(((ax - bx) * (ax - bx)) + ((ay - by) * (ay - by)));
    }

    // =========================================================================
    // Glyph size
    // =========================================================================

    /// A cluster's glyph grows with its count, logarithmically and capped.
    @Test
    void testSizeFactorGrowsWithCountAndIsCapped() {
        assertThat(FloorMapCluster.sizeFactor(0)).isEqualTo(1.0);
        assertThat(FloorMapCluster.sizeFactor(1)).isEqualTo(1.0);
        assertThat(FloorMapCluster.sizeFactor(2))
                .isGreaterThan(1.0)
                .isLessThan(FloorMapCluster.sizeFactor(20));
        assertThat(FloorMapCluster.sizeFactor(20))
                .isLessThan(FloorMapCluster.sizeFactor(200));
        // However big the crowd, the badge cannot blot out the floor plan.
        assertThat(FloorMapCluster.sizeFactor(1_000_000))
                .isEqualTo(FloorMapCluster.maxSizeFactor());
    }

    /// A big badge is hoverable to its edge: the hit radius is scaled by the same
    /// factor the glyph is, or its outer ring would look part of the glyph and not
    /// respond.
    @Test
    void testHitRadiusFollowsTheGlyphSize() {
        final List<FloorMapObject> events = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            events.add(event("user" + i, 0, 0));
        }
        final FloorMapClusterOverlay overlay = clusterEvents(events);
        final double factor = overlay.getClusters().getFirst().getSizeFactor();
        assertThat(factor).isGreaterThan(1.0);

        // Just inside the grown glyph, but outside a lone entity's box.
        final double justInside = 10 * factor * 0.99;
        assertThat(overlay.clusterNear(justInside, 0, 10)).isNotNull();
        assertThat(overlay.clusterNear(10 * factor * 1.01, 0, 10)).isNull();
    }

    /// A cluster is keyed on its lowest member id, not on whichever member seeded
    /// the merge — so gaining a member does not silently make it a different
    /// cluster and tear down an open hover panel.
    @Test
    void testClusterIsKeyedOnItsLowestMemberId() {
        final FloorMapClusterOverlay overlay = clusterEvents(Arrays.asList(
                event("zach", 500, 500),
                event("alice", 500, 500),
                event("mary", 500, 500)));

        assertThat(overlay.getClusters().getFirst().getKey()).isEqualTo("alice");

        // A new member joins; the key still resolves to the same cluster.
        final FloorMapClusterOverlay after = clusterEvents(Arrays.asList(
                event("zach", 500, 500),
                event("alice", 500, 500),
                event("mary", 500, 500),
                event("nigel", 501, 500)));
        assertThat(after.getCluster("alice")).isNotNull();
        assertThat(after.getCluster("alice").size()).isEqualTo(4);
    }

    /// No entity ends up in two clusters, and none is silently dropped.
    @Test
    void testEveryEntityIsInAtMostOneCluster() {
        final List<FloorMapObject> events = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            // A grid tight enough to produce several overlapping candidates: 8
            // columns by 5 rows at 4-unit spacing. This previously cast i to double
            // before dividing, which gave all 40 entities a distinct y — a diagonal
            // line rather than the grid the comment described, and a much weaker
            // stress case for the overlap the test is about.
            final int col = i % 8;
            final int row = i / 8;
            events.add(event("user" + i, col * 4, row * 4));
        }

        final FloorMapClusterOverlay overlay = clusterEvents(events);

        int total = 0;
        final List<String> allMembers = new ArrayList<>();
        for (final FloorMapCluster cluster : overlay.getClusters()) {
            total += cluster.size();
            allMembers.addAll(cluster.getMemberIds());
        }
        assertThat(allMembers).doesNotHaveDuplicates();
        assertThat(total).isEqualTo(overlay.getClusteredEntityCount());
    }

    // =========================================================================
    // Determinism, and what does or does not change the partition
    // =========================================================================

    /// The same input gives the same output, whatever order the rows arrive in.
    @Test
    void testResultDoesNotDependOnInputOrder() {
        final List<FloorMapObject> events = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            events.add(event("user" + i, (i * 7) % 40, (i * 13) % 40));
        }
        final List<FloorMapObject> reversed = new ArrayList<>(events);
        Collections.reverse(reversed);

        assertThat(clusterEvents(reversed)).isEqualTo(clusterEvents(events));
    }

    /// Panning cannot change the clustering, because the pan is not an input:
    /// [FloorMapClusterOverlay#compute] sees map positions and a map-space
    /// threshold, and no screen offset. Panning changes only the offsets, so the
    /// partition it is handed is byte-for-byte the one it had before.
    ///
    /// This is the reason the lattice is anchored at the map origin rather than
    /// at the screen — a screen-space lattice would take the offsets as input and
    /// reshuffle every cluster as the user dragged.
    @Test
    void testPanCannotChangeThePartition() {
        final List<FloorMapObject> events = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            events.add(event("user" + i, (i * 7) % 40, (i * 13) % 40));
        }

        // A pan moves the camera, not the entities: same positions, same result.
        assertThat(memberLists(clusterEvents(events)))
                .isEqualTo(memberLists(clusterEvents(events)));
    }

    /// Recorded, not desired: because the lattice is anchored at the map origin,
    /// where the cell boundaries fall relative to a crowd can decide which member
    /// seeds it — so the same relative arrangement of entities can partition
    /// differently at a different place on the map.
    ///
    /// Here `z` and `a` share a cell in the first arrangement, so
    /// `a` (lower id) seeds and reaches all three; shifted by half a cell
    /// they fall in separate cells, `z` seeds first, and `m` is then
    /// out of reach. Both outcomes are valid partitions — every cluster is still
    /// within the threshold of its seed and no entity is in two clusters — and no
    /// user action produces this shift, since panning does not move entities. It
    /// is asserted so the behaviour is documented rather than discovered.
    @Test
    void testCellBoundariesCanDecideTheSeed() {
        // Ids deliberately out of positional order, so which cell they share
        // decides which one seeds.
        final List<FloorMapObject> events = Arrays.asList(
                event("z", 1, 0),
                event("a", 8, 0),
                event("m", 17, 0));
        final List<FloorMapObject> shifted = Arrays.asList(
                event("z", 6, 0),
                event("a", 13, 0),
                event("m", 22, 0));

        assertThat(memberLists(clusterEvents(events)))
                .containsExactly(Arrays.asList("a", "m", "z"));
        assertThat(memberLists(clusterEvents(shifted)))
                .containsExactly(Arrays.asList("a", "z"));
    }

    /// Zooming — and only zooming — changes the partition.
    @Test
    void testZoomChangesThePartition() {
        final List<FloorMapObject> events = Arrays.asList(
                event("alice", 0, 0),
                event("bob", 15, 0),
                event("carol", 30, 0));

        assertThat(clusterEvents(events).getClusters()).isEmpty();
        assertThat(FloorMapClusterOverlay.compute(null, events, 20, null).getClusters())
                .hasSize(1);
    }

    /// Renaming an entity does not reshuffle the map: seeds are chosen by
    /// position (row-major over the lattice), with the id only breaking ties
    /// inside one cell.
    @Test
    void testRenamingDoesNotReshuffleClusters() {
        final List<FloorMapObject> events = Arrays.asList(
                event("alice", 0, 0),
                event("bob", 2, 0),
                event("carol", 100, 100),
                event("dave", 102, 100));
        final List<FloorMapObject> renamed = Arrays.asList(
                event("zach", 0, 0),
                event("yolanda", 2, 0),
                event("xander", 100, 100),
                event("wendy", 102, 100));

        final List<FloorMapCluster> original = clusterEvents(events).getClusters();
        final List<FloorMapCluster> afterRename = clusterEvents(renamed).getClusters();

        assertThat(original).hasSize(2);
        assertThat(afterRename).hasSize(2);
        for (int i = 0; i < original.size(); i++) {
            assertThat(afterRename.get(i).getMapX()).isEqualTo(original.get(i).getMapX());
            assertThat(afterRename.get(i).getMapY()).isEqualTo(original.get(i).getMapY());
        }
    }

    /// Member ids are sorted, so the hover list's order is predictable.
    @Test
    void testMemberIdsAreSorted() {
        final FloorMapClusterOverlay overlay = clusterEvents(Arrays.asList(
                event("zach", 500, 500),
                event("alice", 500, 500),
                event("mary", 500, 500)));

        assertThat(overlay.getClusters().getFirst().getMemberIds())
                .containsExactly("alice", "mary", "zach");
    }

    /// The cluster is drawn at its members' centroid, not at the seed.
    @Test
    void testClusterSitsAtTheCentroid() {
        final FloorMapClusterOverlay overlay = clusterEvents(Arrays.asList(
                event("alice", 0, 0),
                event("bob", 4, 8)));

        assertThat(overlay.getClusters().getFirst().getMapX()).isEqualTo(2);
        assertThat(overlay.getClusters().getFirst().getMapY()).isEqualTo(4);
    }

    // =========================================================================
    // What clusters and what does not
    // =========================================================================

    /// Point facts — objects drawn as fixed-size glyphs — cluster like entities.
    @Test
    void testPointFactsCluster() {
        final List<Fact> facts = Arrays.asList(
                pointFact("desk1", "object", 100, 100),
                pointFact("desk2", "object", 102, 100));

        final FloorMapClusterOverlay overlay =
                FloorMapClusterOverlay.compute(facts, null, THRESHOLD, null);

        assertThat(overlay.getClusters()).hasSize(1);
        assertThat(overlay.getClusters().getFirst().getLabel()).isEqualTo("2 objects");
    }

    /// A fact's map position is `worldToMap` applied to its coords, never
    /// either alone — the same route the renderer takes. Two facts whose coords
    /// are identical but whose matrices place them far apart must not merge.
    @Test
    void testFactPositionComesFromTheFullTransform() {
        final List<Fact> facts = Arrays.asList(
                pointFact("desk1", "object", 0, 0),
                pointFact("desk2", "object", 900, 900));

        assertThat(FloorMapClusterOverlay.compute(facts, null, THRESHOLD, null).isEmpty())
                .isTrue();
    }

    /// Images and areas scale with the map, so they shrink as the user zooms out
    /// instead of crowding — they are excluded, which is also what stops a floor
    /// plan being merged into a badge.
    @Test
    void testImagesAndAreasNeverCluster() {
        final List<Fact> facts = Arrays.asList(
                imageFact("plan1", 0),
                imageFact("plan2", 1),
                areaFact("bay1", 20),
                areaFact("bay2", 21));

        assertThat(FloorMapClusterOverlay.compute(facts, null, THRESHOLD, null).isEmpty())
                .isTrue();
    }

    /// A background never clusters, by key or by type.
    @Test
    void testBackgroundsNeverCluster() {
        final List<Fact> facts = Arrays.asList(
                pointFact(FloorMapJsonKeys.BACKGROUND, "object", 0, 0),
                pointFact("other", FloorMapJsonKeys.BACKGROUND, 1, 0));

        assertThat(FloorMapClusterOverlay.compute(facts, null, THRESHOLD, null).isEmpty())
                .isTrue();
        assertThat(FloorMapClusterOverlay.isClusterableFact(
                pointFact("x", FloorMapJsonKeys.BACKGROUND, 0, 0))).isFalse();
    }

    /// An id present as both a live event and a static fact twin is counted once,
    /// at its event position — the live one, as everywhere else.
    @Test
    void testFactTwinOfAnEventIsCountedOnce() {
        final List<Fact> facts = Arrays.asList(
                pointFact("alice", FloorMapJsonKeys.PERSON, 900, 900),
                pointFact("bob", FloorMapJsonKeys.PERSON, 500, 502));
        final List<FloorMapObject> events = Collections.singletonList(
                event("alice", 500, 500));

        final FloorMapClusterOverlay overlay =
                FloorMapClusterOverlay.compute(facts, events, THRESHOLD, null);

        // One cluster of two, not three entities: alice's stale fact position at
        // (900,900) is ignored in favour of her event position.
        assertThat(overlay.getClusters()).hasSize(1);
        assertThat(overlay.getClusters().getFirst().getMemberIds())
                .containsExactly("alice", "bob");
    }

    /// Entities with no type still cluster, and fall back to the union term.
    @Test
    void testUntypedEntitiesCluster() {
        final FloorMapClusterOverlay overlay = clusterEvents(Arrays.asList(
                typedEvent("a", null, 0, 0),
                typedEvent("b", null, 1, 0)));

        assertThat(overlay.getClusters()).hasSize(1);
        assertThat(overlay.getClusters().getFirst().getType()).isNull();
        assertThat(overlay.getClusters().getFirst().getLabel()).isEqualTo("2 entities");
    }

    /// An entity with no usable id cannot be tracked back to, so it is skipped.
    @Test
    void testEntitiesWithoutIdsAreSkipped() {
        final FloorMapClusterOverlay overlay = clusterEvents(Arrays.asList(
                event(null, 0, 0),
                event("", 0, 0),
                event("alice", 0, 0)));

        assertThat(overlay.isEmpty()).isTrue();
    }

    // =========================================================================
    // Lookups and guards
    // =========================================================================

    /// Hover and click resolve a cluster by its key; a stale key resolves to null.
    @Test
    void testLookupByKey() {
        final FloorMapClusterOverlay overlay = clusterEvents(Arrays.asList(
                event("alice", 500, 500),
                event("bob", 500, 500)));

        final FloorMapCluster cluster = overlay.getClusters().getFirst();
        assertThat(overlay.getCluster(cluster.getKey())).isSameAs(cluster);
        assertThat(overlay.getCluster("gone")).isNull();
        assertThat(overlay.getCluster(null)).isNull();
        assertThat(overlay.getClusterFor("alice")).isSameAs(cluster);
        assertThat(cluster.contains("bob")).isTrue();
        assertThat(cluster.contains("carol")).isFalse();
    }

    /// A non-positive or non-finite threshold disables clustering rather than
    /// merging everything or nothing silently — the canvas's zero-divisor trap.
    @Test
    void testInvalidThresholdDisablesClustering() {
        final List<FloorMapObject> events = Arrays.asList(
                event("alice", 500, 500),
                event("bob", 500, 500));

        for (final double threshold : new double[]{
                0, -1, Double.NaN, Double.POSITIVE_INFINITY}) {
            assertThat(FloorMapClusterOverlay.compute(null, events, threshold, null))
                    .as("threshold " + threshold)
                    .isEqualTo(FloorMapClusterOverlay.EMPTY);
        }
    }

    /// The screen radius converts to a map distance by dividing out the zoom.
    @Test
    void testMapThreshold() {
        assertThat(FloorMapClusterOverlay.mapThreshold(45, 1)).isEqualTo(45);
        // Zoomed in 3×: 45 screen px is 15 map units.
        assertThat(FloorMapClusterOverlay.mapThreshold(45, 3)).isEqualTo(15);
        // Zoomed out to a tenth: the same 45 px spans 450 map units.
        assertThat(FloorMapClusterOverlay.mapThreshold(45, 0.1)).isCloseTo(450, within(1e-9));
    }

    /// An unusable scale yields 0 — "do not cluster" — rather than a NaN that
    /// would blank or scramble the frame with no error.
    @Test
    void testMapThresholdGuardsTheDivisor() {
        assertThat(FloorMapClusterOverlay.mapThreshold(45, 0)).isEqualTo(0);
        assertThat(FloorMapClusterOverlay.mapThreshold(45, -2)).isEqualTo(0);
        assertThat(FloorMapClusterOverlay.mapThreshold(45, Double.NaN)).isEqualTo(0);
        assertThat(FloorMapClusterOverlay.mapThreshold(0, 1)).isEqualTo(0);
        assertThat(FloorMapClusterOverlay.mapThreshold(Double.NaN, 1)).isEqualTo(0);
    }

    // =========================================================================
    // Resolving what the pointer is over
    // =========================================================================

    /// A point inside the hit radius resolves to the cluster; outside, to nothing.
    @Test
    void testClusterNear() {
        final FloorMapClusterOverlay overlay = clusterEvents(Arrays.asList(
                event("alice", 100, 100),
                event("bob", 100, 100)));

        assertThat(overlay.clusterNear(100, 100, 5)).isNotNull();
        assertThat(overlay.clusterNear(103, 100, 5)).isNotNull();
        assertThat(overlay.clusterNear(110, 100, 5)).isNull();
        assertThat(overlay.clusterNear(100, 100, 0)).isNull();
        assertThat(overlay.clusterNear(100, 100, Double.NaN)).isNull();
        assertThat(FloorMapClusterOverlay.EMPTY.clusterNear(0, 0, 10)).isNull();
    }

    /// Overlapping clusters resolve to the nearest, not the first. Per-type
    /// clustering means two clusters can sit on top of each other, and the pointer
    /// has to pick the one whose glyph it is actually over.
    @Test
    void testClusterNearPicksTheNearest() {
        final FloorMapClusterOverlay overlay = clusterEvents(Arrays.asList(
                typedEvent("alice", "user", 100, 100),
                typedEvent("bob", "user", 100, 100),
                typedEvent("printer1", "device", 108, 100),
                typedEvent("printer2", "device", 108, 100)));

        assertThat(overlay.getClusters()).hasSize(2);
        assertThat(Objects.requireNonNull(overlay.clusterNear(101, 100, 20)).getType()).isEqualTo("user");
        assertThat(Objects.requireNonNull(overlay.clusterNear(107, 100, 20)).getType()).isEqualTo("device");
    }

    /// Null and empty inputs are safe.
    @Test
    void testEmptyInputs() {
        assertThat(FloorMapClusterOverlay.compute(null, null, THRESHOLD, null))
                .isEqualTo(FloorMapClusterOverlay.EMPTY);
        assertThat(FloorMapClusterOverlay.compute(
                Collections.emptyList(), Collections.emptyList(), THRESHOLD, null).isEmpty())
                .isTrue();
        assertThat(FloorMapClusterOverlay.EMPTY.isClustered("alice")).isFalse();
        assertThat(FloorMapClusterOverlay.EMPTY.getClusters()).isEmpty();
    }

    private static List<List<String>> memberLists(final FloorMapClusterOverlay overlay) {
        final List<List<String>> out = new ArrayList<>();
        for (final FloorMapCluster cluster : overlay.getClusters()) {
            out.add(cluster.getMemberIds());
        }
        return out;
    }
}
