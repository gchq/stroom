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

package stroom.floormap.client.cluster;

import stroom.floormap.client.model.Fact;
import stroom.floormap.client.model.FloorMapObject;
import stroom.floormap.shared.FloorMapGeometry;
import stroom.floormap.shared.FloorMapJsonKeys;
import stroom.util.shared.NullSafe;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

/**
 * Which entities the canvas merges into summary glyphs for one frame, because
 * they are closer together on screen than a glyph is wide.
 *
 * <h2>The problem this solves</h2>
 * <p>Point glyphs are drawn at a <strong>fixed screen size</strong> while their
 * positions live in map space, so the ratio of glyph size to the distance
 * between neighbours is unbounded. Two consequences, which are the same bug:
 * entities at the <em>same</em> position are zero pixels apart at every zoom, so
 * no amount of zooming in separates them and only the last one painted is
 * visible; and zooming out drives every distance toward zero while the glyphs
 * stay the same size, clumping the whole map into a mass.</p>
 *
 * <h2>Rules</h2>
 * <ul>
 *   <li><strong>Only fixed-size glyphs cluster.</strong> Facts with an image or
 *       with vertices — floor plans, backgrounds, areas — scale with the map, so
 *       they shrink out of the way on their own and are excluded. That is
 *       exactly the set the renderer draws through {@code appendStyledGlyph}:
 *       event entities plus imageless, vertex-less facts.</li>
 *   <li><strong>Work in map space</strong>, with the screen radius converted
 *       once via {@link #mapThreshold}. This keeps projection maths out of the
 *       algorithm, and it means <em>panning cannot change the clustering</em> —
 *       only zoom can. A screen-space lattice would reshuffle every cluster as
 *       the user dragged.</li>
 *   <li><strong>One type per cluster.</strong> Clustering runs per type, so a
 *       cluster keeps its type's colour, shape and layer dimming, and its
 *       caption can read "10 users" instead of "12 entities".</li>
 *   <li><strong>The focus clusters like everything else, but its cluster is
 *       drawn around it.</strong> A cluster holding the tracked or selected entity
 *       is anchored at <em>that member's</em> position rather than the centroid,
 *       reports it via {@link FloorMapCluster#getFocusedMemberId()}, and is drawn
 *       with the selection ring and a caption naming it. Excluding the focus from
 *       clustering instead would leave two glyphs a few pixels apart fighting for
 *       the same space — the crowding this whole class exists to remove. One glyph
 *       suffices because clusters are homogeneous: the glyph comes from the shared
 *       type, so a focused member's glyph and its clusters are the same shape and
 *       colour anyway.</li>
 *   <li><strong>A cluster of one is not a cluster</strong> — its member renders
 *       normally.</li>
 *   <li>When an id appears as both an event and a fact (an image-bearing fact
 *       twin) the <strong>event</strong> wins, as it does everywhere else; it is
 *       the live position.</li>
 * </ul>
 *
 * <h2>The merge pass, and why it runs more than once</h2>
 * <p>One pass works like this: items are bucketed into a lattice anchored at the
 * map origin with cells the size of the threshold, cells are visited row-major,
 * and items within a cell are visited by id. The first unassigned item seeds a
 * cluster and absorbs every unassigned item within reach <em>of the seed</em>,
 * searching only the neighbouring cells — enough, because a cell is one threshold
 * wide. Absorbing around the seed rather than a moving centroid is what prevents
 * the long chains single-linkage clustering produces across a crowded floor.</p>
 *
 * <p>A single pass is not enough, and that was the substance of the complaint
 * that clustering "only works when entities are in the same position". It leaves
 * two kinds of residue. A later seed is always more than a threshold from every
 * earlier one, but what gets <em>drawn</em> is the centroid, which drifts — so two
 * clusters end up with their badges overlapping. And an entity just outside the
 * reach of every seed stays a lone glyph even when it is sitting on top of a
 * cluster's badge, because nothing ever offers it a second chance. Measured on a
 * screenful of 150 entities, one pass left 85 glyphs, a third of them overlapping
 * something.</p>
 *
 * <p>So the pass is <strong>repeated over the clusters it produced</strong>, each
 * standing at its members' centroid, until a round merges nothing or
 * {@link #MAX_MERGE_ROUNDS} is reached — the same coarsening a slippy map gets by
 * clustering each zoom level from the level below, applied within one level.
 * Colliding clusters merge; stragglers are adopted. Rounds are capped because
 * this runs on every animation frame, and in practice a crowd settles in two or
 * three.</p>
 *
 * <p>Two things bound what a round may do. A cluster's glyph grows with its count
 * ({@link FloorMapCluster#sizeFactor}), so the merge distance is the average of
 * the two glyphs' reach rather than a flat threshold: a big badge covers more
 * ground and must speak for what it covers. And a merge is refused when it would
 * put a member further than {@link #SPREAD_LIMIT} thresholds from the seed, which
 * is what stops a corridor of desks chaining into one badge whose members are off-screen.
 * Note what that leash does and does not guarantee: it bounds an absorbed candidate's
 * members relative to the absorbing seed's anchor, but nothing bounds the seed's own spread, and a
 * merged node's spread is recomputed from its moved centroid. In practice this keeps every member
 * within roughly {@code 2 × SPREAD_LIMIT} thresholds of every other, but that is an approximation,
 * not an invariant - later rounds can exceed it as centroids drift, so do not rely on it for
 * hit-testing or badge sizing.</p>
 *
 * <h2>Determinism</h2>
 * <p>This is recomputed on every frame, including every animation frame, so an
 * order-dependent result would make badges flicker while entities move. The
 * partition is fixed by geometry alone — the lattice order above, with ids only
 * breaking ties inside a cell, and each round's clusters carrying their lowest
 * member id so the next round's tie-breaks are equally fixed.</p>
 *
 * <p>Two consequences worth knowing, neither of which a user action can trigger:
 * an entity sitting exactly at the threshold during playback flips in and out of
 * its cluster from frame to frame, so a badge can alternate between 9 and 10
 * (damping that needs state carried between frames and is deliberately not done
 * here); and because the lattice is anchored at the map origin, where the cell
 * boundaries fall relative to a crowd can decide which of its members seeds it,
 * so the same relative arrangement of entities elsewhere on the map may partition
 * differently. Both outcomes are always valid partitions — bounded spread, no
 * entity in two clusters — and panning cannot cause either, because the pan is
 * not an input here.</p>
 *
 * <h2>What a count means</h2>
 * <p>The same thing an area's occupant badge means: entities that <em>have a
 * position</em> at the current timeline instant. An entity whose events have
 * gone quiet has no position and is not counted, so a cluster count is not an
 * occupancy figure.</p>
 *
 * <p>Holds no GWT or DOM types so it can be unit-tested on the JVM.</p>
 */
public final class FloorMapClusterOverlay {

    /** Nothing merged — every entity renders on its own. */
    public static final FloorMapClusterOverlay EMPTY =
            new FloorMapClusterOverlay(Collections.emptyList(),
                    Collections.emptyMap(),
                    Collections.emptyMap());

    /**
     * Stands in for "no type" as a map key, since a {@link TreeMap} cannot hold
     * a null one. Converted back to {@code null} on the way out.
     */
    private static final String NO_TYPE = "";

    /**
     * How many times the merge pass is repeated over its own output before the
     * frame is drawn as it stands.
     *
     * <p>A cap rather than "until nothing changes" because this runs on every
     * animation frame: a pathological arrangement must not be able to spend an
     * unbounded number of passes. Crowds converge in two or three rounds — a
     * screenful of 150 entities settles by the third and is unchanged by a
     * tenth — so the cap is a backstop rather than a limit that normally
     * bites.</p>
     */
    private static final int MAX_MERGE_ROUNDS = 4;

    /**
     * How far, in thresholds, a member may end up from the seed of the cluster
     * that absorbed it.
     *
     * <p>Repeating the merge pass is what makes clustering actually clear the
     * screen, but left unchecked it would also let a dense corridor chain
     * together round after round into one badge standing for entities nowhere
     * near it. This is the leash. Tuned by simulation over real-shaped layouts:
     * at 1.5 a tight desk grid still leaves overlapping badges, and at 2.5 a
     * whole grid of 48 collapses into a single badge spanning 215px, which is
     * over-merging. At 2 the same layouts come out with no overlapping badges
     * and no cluster wider than about 180px.</p>
     */
    private static final double SPREAD_LIMIT = 2.0;

    private final List<FloorMapCluster> clusters;
    private final Map<String, FloorMapCluster> byMemberId;
    private final Map<String, FloorMapCluster> byKey;

    private FloorMapClusterOverlay(final List<FloorMapCluster> clusters,
                                   final Map<String, FloorMapCluster> byMemberId,
                                   final Map<String, FloorMapCluster> byKey) {
        this.clusters = clusters;
        this.byMemberId = byMemberId;
        this.byKey = byKey;
    }

    /**
     * Converts a screen-space clustering radius into the map-space threshold the
     * algorithm works in.
     *
     * <p>Guards the divisor. A zoomed-right-out or not-yet-laid-out canvas can
     * present a zero, negative or NaN scale, and letting that through would
     * produce a NaN threshold — which does not fail loudly, it silently merges
     * nothing (or everything). Returns {@code 0}, which {@link #compute} treats
     * as "do not cluster".</p>
     *
     * @param radiusPx the clustering radius in screen pixels
     * @param scale    the current zoom, i.e. screen pixels per map unit
     * @return the map-space threshold, or {@code 0} when it cannot be computed
     */
    public static double mapThreshold(final double radiusPx, final double scale) {
        if (!Double.isFinite(radiusPx) || !Double.isFinite(scale) || radiusPx <= 0 || scale <= 0) {
            return 0;
        }
        return radiusPx / scale;
    }

    /**
     * Computes the frame's clusters.
     *
     * @param facts       the static facts already filtered to the visible layers;
     *                    may be {@code null}
     * @param events      the live event entities, already filtered to the visible
     *                    layers, whose {@code x}/{@code y} are map-space; may be
     *                    {@code null}
     * @param thresholdMap the map-space merge distance, from
     *                    {@link #mapThreshold}; {@code 0} or invalid disables
     *                    clustering entirely
     * @param focusedIds  the tracked and selected ids. These still cluster, but a
     *                    cluster containing one is anchored on it and reports it as
     *                    its focused member, so it can be drawn as that entity.
     *                    May be {@code null}
     * @return the overlay; never {@code null}
     */
    public static FloorMapClusterOverlay compute(final List<Fact> facts,
                                                 final List<FloorMapObject> events,
                                                 final double thresholdMap,
                                                 final Set<String> focusedIds) {
        if (!Double.isFinite(thresholdMap) || thresholdMap <= 0) {
            return EMPTY;
        }
        // Types are visited in name order so the cluster list — and therefore
        // the paint order — does not depend on query row order.
        final Map<String, List<Item>> byType = collectByType(facts, events);
        if (byType.isEmpty()) {
            return EMPTY;
        }

        final List<FloorMapCluster> clusters = new ArrayList<>();
        for (final Map.Entry<String, List<Item>> entry : byType.entrySet()) {
            final String type = NO_TYPE.equals(entry.getKey())
                    ? null
                    : entry.getKey();
            clusterOneType(type, entry.getValue(), thresholdMap, focusedIds, clusters);
        }
        if (clusters.isEmpty()) {
            return EMPTY;
        }

        final Map<String, FloorMapCluster> byMemberId = new HashMap<>();
        final Map<String, FloorMapCluster> byKey = new LinkedHashMap<>();
        for (final FloorMapCluster cluster : clusters) {
            byKey.put(cluster.getKey(), cluster);
            for (final String memberId : cluster.getMemberIds()) {
                byMemberId.put(memberId, cluster);
            }
        }
        return new FloorMapClusterOverlay(
                Collections.unmodifiableList(clusters),
                byMemberId,
                byKey);
    }

    /**
     * Gathers the clusterable entities, keyed by type. Events are taken first so
     * a live event position beats a static fact twin of the same id.
     */
    private static Map<String, List<Item>> collectByType(final List<Fact> facts,
                                                         final List<FloorMapObject> events) {
        final Map<String, List<Item>> byType = new TreeMap<>();
        final Set<String> seen = new HashSet<>();

        if (events != null) {
            for (final FloorMapObject event : events) {
                if (event != null
                        && NullSafe.isNonEmptyString(event.getId())
                        && seen.add(event.getId())) {
                    add(byType, new Item(event.getId(), event.getType(),
                            event.getX(), event.getY()));
                }
            }
        }
        if (facts != null) {
            for (final Fact fact : facts) {
                if (isClusterableFact(fact)
                    && NullSafe.isNonEmptyString(fact.getKey())
                    && seen.add(fact.getKey())) {
                    // The same route the renderer takes: a fact's map position is
                    // worldToMap applied to its coords, never either alone.
                    final double[] point = fact.mapTestPoint();
                    add(byType, new Item(fact.getKey(), fact.getType(), point[0], point[1]));
                }
            }
        }

        return byType;
    }

    private static void add(final Map<String, List<Item>> byType, final Item item) {
        final String key = NullSafe.isNonEmptyString(item.type)
                ? item.type
                : NO_TYPE;
        //noinspection unused
        byType.computeIfAbsent(key, k -> new ArrayList<>()).add(item);
    }

    /**
     * Clusters one type's entities, appending any cluster of two or more to
     * {@code out}: one merge pass, then the same pass over its own output until a
     * round merges nothing. See the class javadoc for why once is not enough.
     */
    private static void clusterOneType(final String type,
                                       final List<Item> items,
                                       final double thresholdMap,
                                       final Set<String> focusedIds,
                                       final List<FloorMapCluster> out) {
        if (items.size() < 2) {
            // One entity of a type can never cluster, so skip building a lattice
            // for it. Not just an optimisation for sparse maps — every type with
            // a lone member takes this path on every frame.
            return;
        }

        List<Node> nodes = new ArrayList<>(items.size());
        for (final Item item : items) {
            nodes.add(new Node(item));
        }
        for (int round = 0; round < MAX_MERGE_ROUNDS; round++) {
            final List<Node> merged = mergeRound(nodes, thresholdMap);
            final boolean settled = merged.size() == nodes.size();
            nodes = merged;
            if (settled) {
                // Nothing merged, so the next round would be handed exactly this
                // input and reach exactly this answer.
                break;
            }
        }

        for (final Node node : nodes) {
            if (node.members.size() >= 2) {
                out.add(node.toCluster(type, focusedIds));
            }
        }
    }

    /**
     * One merge pass over the given nodes — entities on the first round, clusters
     * on the rest.
     *
     * <p>Nodes are bucketed into a lattice anchored at the map origin, one
     * threshold per cell, then visited row-major with ids breaking ties inside a
     * cell. Each unclaimed node seeds a merge and absorbs the unclaimed nodes
     * within reach of it.</p>
     *
     * @return the nodes after merging: one entry per seed, in traversal order
     */
    private static List<Node> mergeRound(final List<Node> nodes, final double thresholdMap) {
        final Map<String, List<Node>> cells = new HashMap<>();
        final List<int[]> occupied = new ArrayList<>();
        // The widest glyph on the map decides how far a seed has to look to be
        // sure it has seen everything that could reach it.
        double widestFactor = 1;
        for (final Node node : nodes) {
            final int cx = cellIndex(node.mapX, thresholdMap);
            final int cy = cellIndex(node.mapY, thresholdMap);
            final String cellKey = cellKey(cx, cy);
            List<Node> cell = cells.get(cellKey);
            if (cell == null) {
                cell = new ArrayList<>();
                cells.put(cellKey, cell);
                occupied.add(new int[]{cx, cy});
            }
            cell.add(node);
            widestFactor = Math.max(widestFactor, node.sizeFactor());
        }

        // Row-major cell order, and id order within a cell: a total order fixed
        // by geometry, so renaming an entity cannot reshuffle the map.
        occupied.sort((a, b) -> a[1] != b[1]
                ? Integer.compare(a[1], b[1])
                : Integer.compare(a[0], b[0]));
        for (final List<Node> cell : cells.values()) {
            //noinspection ComparatorCombinators
            cell.sort((a, b) -> a.id.compareTo(b.id));
        }

        final List<Node> out = new ArrayList<>();
        final Set<String> assigned = new HashSet<>();
        for (final int[] cell : occupied) {
            for (final Node seed : cells.get(cellKey(cell[0], cell[1]))) {
                if (assigned.contains(seed.id)) {
                    continue;
                }
                assigned.add(seed.id);
                out.add(absorb(seed, cell, cells, assigned, thresholdMap, widestFactor));
                // A seed that absorbed nobody stays marked. That is safe rather
                // than wasteful: it had already offered itself to every
                // unassigned neighbour within range, and reach is symmetric, so
                // no later seed can be close enough to want it.
            }
        }
        return out;
    }

    /**
     * Merges into the seed every unclaimed node within reach of it, marking each
     * as claimed.
     *
     * <p>Reach is {@link #mergeDistance}, so it depends on how big the two glyphs
     * are drawn; the search therefore covers as many rings of cells as the widest
     * possible partner could need, which is one for lone entities and two once a
     * grown cluster glyph is involved.</p>
     *
     * @return the merged node, or the seed itself when nothing was in reach
     */
    private static Node absorb(final Node seed,
                               final int[] seedCell,
                               final Map<String, List<Node>> cells,
                               final Set<String> assigned,
                               final double thresholdMap,
                               final double widestFactor) {
        final double reach = thresholdMap * 0.5 * (seed.sizeFactor() + widestFactor);
        final int rings = (int) Math.ceil(reach / thresholdMap);
        final double spreadLimit = SPREAD_LIMIT * thresholdMap;

        List<Node> absorbed = null;
        for (int dy = -rings; dy <= rings; dy++) {
            for (int dx = -rings; dx <= rings; dx++) {
                final List<Node> neighbours = cells.get(cellKey(seedCell[0] + dx, seedCell[1] + dy));
                if (neighbours == null) {
                    continue;
                }
                for (final Node candidate : neighbours) {
                    if (assigned.contains(candidate.id)) {
                        continue;
                    }
                    final double distance = distance(seed, candidate);
                    // The candidate's own members trail behind it by its spread,
                    // so that is what has to clear the leash — checking only the
                    // gap between the two anchors would let a cluster drag its
                    // far side along without ever being asked.
                    if (distance <= mergeDistance(seed, candidate, thresholdMap)
                            && distance + candidate.spread <= spreadLimit) {
                        if (absorbed == null) {
                            absorbed = new ArrayList<>();
                        }
                        absorbed.add(candidate);
                        assigned.add(candidate.id);
                    }
                }
            }
        }
        return absorbed == null
                ? seed
                : new Node(seed, absorbed);
    }

    /**
     * How close two nodes must be to merge: the average of the two glyphs' reach.
     *
     * <p>For two lone entities that is exactly the threshold — a glyph's width —
     * which is the rule the whole class is built on. A cluster whose glyph has
     * grown reaches proportionally further, because the badge it draws covers
     * proportionally more of the map and must speak for what it covers.</p>
     */
    private static double mergeDistance(final Node a, final Node b, final double thresholdMap) {
        return thresholdMap * 0.5 * (a.sizeFactor() + b.sizeFactor());
    }

    private static double distance(final Node a, final Node b) {
        return FloorMapGeometry.distance(a.mapX, a.mapY, b.mapX, b.mapY);
    }

    /**
     * The first member (in sorted id order) that is focused, or {@code null} if
     * none is.
     */
    private static Item firstFocused(final List<String> sortedIds,
                                     final List<Item> members,
                                     final Set<String> focusedIds) {
        if (NullSafe.isEmptyCollection(focusedIds)) {
            return null;
        }
        for (final String id : sortedIds) {
            if (focusedIds.contains(id)) {
                for (final Item member : members) {
                    if (member.id.equals(id)) {
                        return member;
                    }
                }
            }
        }
        return null;
    }

    private static String cellKey(final int cx, final int cy) {
        return cx + "," + cy;
    }

    /**
     * The lattice cell index a map coordinate falls in.
     *
     * <p>An {@code int} rather than a {@code long} deliberately: this runs for
     * every entity on every animation frame, and GWT emulates {@code long} as a
     * three-{@code int} tuple, so long arithmetic here would be paid for 60 times
     * a second. The clamp covers the coordinate range that would overflow — far
     * outside any real floor plan, and harmless if reached, since two entities
     * sharing an edge cell are still separated by the distance test.</p>
     */
    private static int cellIndex(final double value, final double cell) {
        final double index = Math.floor(value / cell);
        if (index <= Integer.MIN_VALUE) {
            return Integer.MIN_VALUE;
        }
        if (index >= Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        return (int) index;
    }

    /**
     * {@code true} if this fact is drawn as a fixed-size point glyph, and so
     * needs clustering.
     *
     * <p>Mirrors the renderer's dispatch. A fact with an image renders as that
     * image and a fact with vertices renders as an area — both scale with the
     * map, so they shrink as you zoom out instead of crowding. A background is
     * excluded outright: its placement origin is arbitrary, and it covers the
     * map.</p>
     *
     * @param fact the fact to test; may be {@code null}
     */
    public static boolean isClusterableFact(final Fact fact) {
        return fact != null
                && !fact.hasImage()
                && !fact.hasVertices()
                && !FloorMapJsonKeys.BACKGROUND.equalsIgnoreCase(fact.getKey())
                && !FloorMapJsonKeys.BACKGROUND.equalsIgnoreCase(fact.getType());
    }

    /**
     * Every cluster this frame, in paint order (by type name, then row-major by
     * position). Empty when nothing merged.
     */
    public List<FloorMapCluster> getClusters() {
        return clusters;
    }

    /**
     * {@code true} if this entity has been merged into a cluster, and so must
     * <strong>not</strong> be drawn on its own.
     *
     * @param id an entity id or fact key; may be {@code null}
     */
    public boolean isClustered(final String id) {
        return id != null && byMemberId.containsKey(id);
    }

    /**
     * The cluster this entity was merged into, or {@code null} if it renders on
     * its own.
     *
     * @param id an entity id or fact key; may be {@code null}
     */
    public FloorMapCluster getClusterFor(final String id) {
        return id != null
                ? byMemberId.get(id)
                : null;
    }

    /**
     * The cluster with the given key, or {@code null} if there is none — which is
     * how a hover or click on a cluster that has since dissolved resolves.
     *
     * @param key a cluster key, as given by {@link FloorMapCluster#getKey()};
     *            may be {@code null}
     */
    public FloorMapCluster getCluster(final String key) {
        return key != null
                ? byKey.get(key)
                : null;
    }

    /**
     * The cluster nearest to a map-space point and within {@code radiusMap} of
     * it, or {@code null} if none is. Used to resolve what the pointer is over.
     *
     * <p>Works in map space, like everything else here, so the caller converts
     * the cursor once ({@code screenToMap}) rather than this projecting every
     * cluster. The radius is the glyph's own half-width converted by
     * {@link #mapThreshold}, which is what makes the hit area track the zoom.</p>
     *
     * <p>Nearest rather than first: per-type clustering means two clusters can
     * overlap on screen, and the pointer should resolve to the one whose glyph is
     * actually under it.</p>
     *
     * <p>Each cluster's own hit radius is scaled by
     * {@link FloorMapCluster#getSizeFactor()}, because that is what its glyph is
     * scaled by. A fixed radius would leave the outer ring of a big badge inert —
     * visibly part of the glyph, but not hoverable.</p>
     *
     * @param mapX      the test point in map space
     * @param mapY      the test point in map space
     * @param radiusMap the hit radius in map units for a glyph drawn at its base
     *                  size; {@code 0} or invalid matches nothing
     * @return the cluster, or {@code null}
     */
    public FloorMapCluster clusterNear(final double mapX,
                                       final double mapY,
                                       final double radiusMap) {
        if (!Double.isFinite(radiusMap) || radiusMap <= 0) {
            return null;
        }
        FloorMapCluster nearest = null;
        double nearestSquared = Double.MAX_VALUE;
        for (final FloorMapCluster cluster : clusters) {
            final double distanceSquared = FloorMapGeometry.distanceSquared(
                    cluster.getMapX(), cluster.getMapY(), mapX, mapY);
            final double radius = radiusMap * cluster.getSizeFactor();
            if (distanceSquared <= radius * radius && distanceSquared < nearestSquared) {
                nearest = cluster;
                nearestSquared = distanceSquared;
            }
        }
        return nearest;
    }

    /** {@code true} if nothing at all merged. */
    public boolean isEmpty() {
        return clusters.isEmpty();
    }

    /** How many entities are hidden inside clusters, across the whole frame. */
    public int getClusteredEntityCount() {
        return byMemberId.size();
    }

    /**
     * Two overlays are equal when they describe the same merging. Lets callers
     * skip work when a frame changed nothing.
     */
    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof final FloorMapClusterOverlay that)) {
            return false;
        }
        return clusters.equals(that.clusters);
    }

    @Override
    public int hashCode() {
        return Objects.hash(clusters);
    }

    @Override
    public String toString() {
        return "FloorMapClusterOverlay" + clusters;
    }

    /** One clusterable entity, reduced to what the algorithm needs. */
    private static final class Item {

        private final String id;
        private final String type;
        private final double mapX;
        private final double mapY;

        private Item(final String id, final String type, final double mapX, final double mapY) {
            this.id = id;
            this.type = type;
            this.mapX = mapX;
            this.mapY = mapY;
        }
    }

    /**
     * A cluster part-way through being formed: one entity on the first round, a
     * merged group on the rounds after it.
     *
     * <p>Carrying the members between rounds rather than just a count and a
     * position is what lets the next round place the node at its true centroid
     * and know how far its members really trail behind it.</p>
     */
    private static final class Node {

        /**
         * The node's identity: its <em>lowest</em> member id.
         *
         * <p>The lowest rather than the seed's, so that a cluster keeps its
         * identity when it gains a member — which decides whether an open hover
         * panel survives the next frame, and which is not something the seed's id
         * can promise, since the seed changes with the membership.</p>
         */
        private final String id;
        private final List<Item> members;
        /** The members' centroid — where the glyph is drawn. */
        private final double mapX;
        private final double mapY;
        /** How far the furthest member sits from the centroid. */
        private final double spread;

        /** A lone entity: its own centroid, with nothing trailing behind it. */
        private Node(final Item item) {
            this.id = item.id;
            this.members = Collections.singletonList(item);
            this.mapX = item.mapX;
            this.mapY = item.mapY;
            this.spread = 0;
        }

        /** The union of a seed and everything it absorbed this round. */
        private Node(final Node seed, final List<Node> absorbed) {
            final List<Item> all = new ArrayList<>(seed.members);
            String lowestId = seed.id;
            for (final Node node : absorbed) {
                all.addAll(node.members);
                if (node.id.compareTo(lowestId) < 0) {
                    lowestId = node.id;
                }
            }

            double sumX = 0;
            double sumY = 0;
            for (final Item member : all) {
                sumX += member.mapX;
                sumY += member.mapY;
            }
            final double centroidX = sumX / all.size();
            final double centroidY = sumY / all.size();

            double furthest = 0;
            for (final Item member : all) {
                furthest = Math.max(furthest,
                        FloorMapGeometry.distance(member.mapX, member.mapY, centroidX, centroidY));
            }

            this.id = lowestId;
            this.members = all;
            this.mapX = centroidX;
            this.mapY = centroidY;
            this.spread = furthest;
        }

        /** How much bigger than a lone entity's this node's glyph is drawn. */
        private double sizeFactor() {
            return FloorMapCluster.sizeFactor(members.size());
        }

        /**
         * Builds the finished cluster: id-sorted members, anchored at the centroid
         * — or, when one member is focused, at that member's own position.
         */
        private FloorMapCluster toCluster(final String type, final Set<String> focusedIds) {
            final List<String> ids = new ArrayList<>(members.size());
            for (final Item member : members) {
                ids.add(member.id);
            }
            Collections.sort(ids);

            // The representative focused member, chosen in sorted member order so
            // it does not depend on the traversal. Only reachable as more than one
            // on a tab with multi-select, which is a tab where clustering is off.
            final Item focused = firstFocused(ids, members, focusedIds);
            if (focused != null) {
                // Anchored on the focused member, not the crowd's average: this
                // glyph IS that entity as far as the user is concerned — it carries
                // their selection ring and their name — so it has to sit where they
                // are.
                return new FloorMapCluster(id, type,
                        Collections.unmodifiableList(ids),
                        focused.mapX, focused.mapY, focused.id);
            }
            // The centroid, not any one member's position, so the glyph drifts
            // smoothly as members move rather than jumping when the seed changes.
            return new FloorMapCluster(id, type,
                    Collections.unmodifiableList(ids),
                    mapX, mapY, null);
        }
    }
}
