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

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Which entities are inside which areas, at one instant of the playback
 * timeline — the answer to "what area is this user/object in?".
 *
 * <p>This is a <strong>config-free geometric snapshot</strong>: it tests each
 * entity's map-space point against each area polygon with
 * {@link FloorMapGeometry#contains}. It is recomputed whenever the facts or
 * event entities change (i.e. on a timeline settle), never per animation
 * frame.</p>
 *
 * <h2>What this is and is not</h2>
 * <p>Membership here is a <em>snapshot of positioned entities</em>. It is not
 * occupancy in the sense of "who is present": an entity only has a position if
 * the events query returned one near the current time, so an entity that has
 * gone quiet simply drops out. It is deliberately unrelated to gate-derived
 * occupancy, which is under-determined without exit events.</p>
 *
 * <h2>Rules</h2>
 * <ul>
 *   <li><strong>Areas</strong> are facts with vertices and no image — matching
 *       the renderer's image-first dispatch, so anything painted as an area
 *       tests as one and vice versa.</li>
 *   <li><strong>Containment is in map space</strong> — area vertices go through
 *       the area's own matrix ({@link FloorMapGeometry#toMapVertices}), static
 *       facts through theirs ({@link FloorMapGeometry#mapTestPoint}), and event
 *       entities use their {@code x}/{@code y} directly (events carry no
 *       matrix).</li>
 *   <li><strong>Membership is multi-valued</strong> — areas overlap and can be
 *       drawn one within another, so an entity can be in several. Lists are
 *       ordered <em>innermost first</em> (smallest map-space polygon area), so
 *       the head is the most specific answer.</li>
 *   <li><strong>Only objects and users are located.</strong> Areas and
 *       backgrounds are never occupants, so they always report no containing
 *       area. Area-inside-area is deliberately <em>not</em> computed: the nesting
 *       relationship is not needed, and a geometric test for it cannot be made to
 *       match user expectation without either an area-overlap threshold or a
 *       user-declared parent. A background's placement origin is arbitrary and
 *       would only produce noise.</li>
 *   <li>When an id appears as both an event and a fact (an image-bearing fact
 *       twin), the <strong>event</strong> position wins — it is the live one.</li>
 * </ul>
 *
 * <p>Holds no GWT or DOM types so it can be unit-tested on the JVM.</p>
 */
public final class FloorMapAreaMembership {

    /** An empty membership — no areas, no occupants. */
    public static final FloorMapAreaMembership EMPTY =
            new FloorMapAreaMembership(
                    Collections.emptyMap(),
                    Collections.emptyMap(),
                    Collections.emptySet());

    private final Map<String, List<String>> areaKeysByEntity;
    private final Map<String, List<String>> occupantsByArea;
    private final Set<String> areaKeys;

    private FloorMapAreaMembership(final Map<String, List<String>> areaKeysByEntity,
                                   final Map<String, List<String>> occupantsByArea,
                                   final Set<String> areaKeys) {
        this.areaKeysByEntity = areaKeysByEntity;
        this.occupantsByArea = occupantsByArea;
        this.areaKeys = areaKeys;
    }

    /**
     * Computes the membership snapshot.
     *
     * @param facts  the static facts from the facts query (areas, objects,
     *               backgrounds); may be {@code null}
     * @param events the live event entities from the events query, whose
     *               {@code x}/{@code y} are already map-space; may be {@code null}
     * @return the membership; never {@code null}
     */
    public static FloorMapAreaMembership compute(final List<Fact> facts,
                                                 final List<FloorMapObject> events) {
        final List<AreaShape> areas = collectAreas(facts);
        if (areas.isEmpty()) {
            return EMPTY;
        }
        // Smallest first, so the per-entity lists come out innermost-first
        // without a second sort. The key is the tiebreak so equal-sized areas
        // order stably rather than by query row order.
        areas.sort((a, b) -> {
            final int cmp = Double.compare(a.size, b.size);
            return cmp != 0
                    ? cmp
                    : a.key.compareTo(b.key);
        });

        final Set<String> allAreaKeys = new LinkedHashSet<>();
        for (final AreaShape area : areas) {
            allAreaKeys.add(area.key);
        }

        final Map<String, List<String>> areaKeysByEntity = new LinkedHashMap<>();
        final Map<String, List<String>> occupantsByArea = new LinkedHashMap<>();

        // Events first — a live event position beats a static fact twin.
        final Set<String> placed = new LinkedHashSet<>();
        if (events != null) {
            for (final FloorMapObject event : events) {
                if (event != null && isUsableId(event.getId()) && placed.add(event.getId())) {
                    assign(event.getId(), event.getX(), event.getY(),
                            areas, areaKeysByEntity, occupantsByArea);
                }
            }
        }
        if (facts != null) {
            for (final Fact fact : facts) {
                if (fact != null
                        && isUsableId(fact.getKey())
                        && canBeOccupant(fact)
                        && placed.add(fact.getKey())) {
                    final double[] point = FloorMapGeometry.mapTestPoint(fact);
                    assign(fact.getKey(), point[0], point[1],
                            areas, areaKeysByEntity, occupantsByArea);
                }
            }
        }

        return new FloorMapAreaMembership(
                areaKeysByEntity,
                occupantsByArea,
                Collections.unmodifiableSet(allAreaKeys));
    }

    /**
     * Tests one occupant's map-space point against every area, recording both
     * directions of the relation. {@code areas} must already be sorted
     * smallest-first, so the resulting list is innermost-first.
     */
    private static void assign(final String id,
                               final double mapX,
                               final double mapY,
                               final List<AreaShape> areas,
                               final Map<String, List<String>> areaKeysByEntity,
                               final Map<String, List<String>> occupantsByArea) {
        List<String> containing = null;
        for (final AreaShape area : areas) {
            if (FloorMapGeometry.contains(area.mapVertices, mapX, mapY)) {
                if (containing == null) {
                    containing = new ArrayList<>(2);
                }
                containing.add(area.key);
                final List<String> occupants = occupantsByArea.computeIfAbsent(area.key, k -> new ArrayList<>());
                occupants.add(id);
            }
        }
        if (containing != null) {
            areaKeysByEntity.put(id, Collections.unmodifiableList(containing));
        }
    }

    /**
     * Extracts the renderable areas from the facts, each with its map-space
     * vertices and polygon size precomputed.
     */
    private static List<AreaShape> collectAreas(final List<Fact> facts) {
        final List<AreaShape> areas = new ArrayList<>();
        if (facts == null) {
            return areas;
        }
        for (final Fact fact : facts) {
            if (isAreaFact(fact) && isUsableId(fact.getKey())) {
                final double[][] mapVertices = FloorMapGeometry.toMapVertices(fact);
                if (mapVertices != null && mapVertices.length >= 3) {
                    areas.add(new AreaShape(
                            fact.getKey(),
                            mapVertices,
                            FloorMapGeometry.area(mapVertices)));
                }
            }
        }
        return areas;
    }

    /**
     * {@code true} if this fact is painted — and so tested — as an area. The
     * image check mirrors the renderer's image-first dispatch: a fact carrying
     * its own image renders as that image, never as a polygon.
     */
    public static boolean isAreaFact(final Fact fact) {
        return fact != null && fact.hasVertices() && !fact.hasImage();
    }

    /**
     * {@code true} if this fact can be <em>inside</em> an area.
     *
     * <p>Two exclusions:</p>
     * <ul>
     *   <li><strong>Areas</strong> — whether one area sits inside another is not
     *       needed, so areas are never occupants and always report no containing
     *       area. Only objects and users are located.</li>
     *   <li><strong>Backgrounds</strong> — a background's placement origin is
     *       arbitrary, so reporting one as being in an area is noise.</li>
     * </ul>
     */
    private static boolean canBeOccupant(final Fact fact) {
        return !isAreaFact(fact)
                && !FloorMapJsonKeys.BACKGROUND.equalsIgnoreCase(fact.getType());
    }

    private static boolean isUsableId(final String id) {
        return id != null && !id.isEmpty();
    }

    /**
     * The keys of every area containing the given entity, ordered innermost
     * (smallest) first.
     *
     * @param entityId the entity id; may be {@code null}
     * @return the containing area keys; empty when the entity is in no area or
     *         has no known position
     */
    public List<String> getAreaKeys(final String entityId) {
        final List<String> keys = entityId != null
                ? areaKeysByEntity.get(entityId)
                : null;
        return keys != null
                ? keys
                : Collections.emptyList();
    }

    /**
     * The most specific (smallest) area containing the given entity, or
     * {@code null} if it is in none.
     *
     * @param entityId the entity id; may be {@code null}
     * @return the innermost containing area key, or {@code null}
     */
    public String getInnermostAreaKey(final String entityId) {
        final List<String> keys = getAreaKeys(entityId);
        //noinspection SequencedCollectionMethodCanBeUsed Not on GWT
        return keys.isEmpty()
                ? null
                : keys.get(0);
    }

    /**
     * The ids of every entity currently inside the given area.
     *
     * @param areaKey the area's fact key; may be {@code null}
     * @return the occupant ids; empty when the area is empty or unknown
     */
    public List<String> getOccupants(final String areaKey) {
        final List<String> occupants = areaKey != null
                ? occupantsByArea.get(areaKey)
                : null;
        return occupants != null
                ? Collections.unmodifiableList(occupants)
                : Collections.emptyList();
    }

    /**
     * The number of entities currently inside the given area.
     *
     * @param areaKey the area's fact key; may be {@code null}
     * @return the occupant count, {@code 0} when empty or unknown
     */
    public int getOccupantCount(final String areaKey) {
        return getOccupants(areaKey).size();
    }

    /** {@code true} if the given key is one of the areas in this snapshot. */
    public boolean isArea(final String key) {
        return key != null && areaKeys.contains(key);
    }

    /** The keys of every area in this snapshot. */
    public Set<String> getAreaKeys() {
        return areaKeys;
    }

    /**
     * The ids of every entity that is inside at least one area. Entities with no
     * known position, or outside every area, are absent.
     */
    public Set<String> getEntityIds() {
        return Collections.unmodifiableSet(areaKeysByEntity.keySet());
    }

    /** Occupant counts for every non-empty area, keyed by area fact key. */
    public Map<String, Integer> getOccupantCounts() {
        final Map<String, Integer> counts = new LinkedHashMap<>();
        for (final Map.Entry<String, List<String>> entry : occupantsByArea.entrySet()) {
            counts.put(entry.getKey(), entry.getValue().size());
        }
        return counts;
    }

    /**
     * Two snapshots are equal when they describe the same containment. Lets
     * callers skip work — notably a grid redraw — when a query refresh produced
     * no actual change, which is the common case during playback.
     */
    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof final FloorMapAreaMembership that)) {
            return false;
        }
        return areaKeys.equals(that.areaKeys)
                && areaKeysByEntity.equals(that.areaKeysByEntity)
                && occupantsByArea.equals(that.occupantsByArea);
    }

    @Override
    public int hashCode() {
        return Objects.hash(areaKeys, areaKeysByEntity, occupantsByArea);
    }

    /** An area polygon in map space, with its size cached for nesting order. */
    private static final class AreaShape {

        private final String key;
        private final double[][] mapVertices;
        private final double size;

        private AreaShape(final String key, final double[][] mapVertices, final double size) {
            this.key = key;
            this.mapVertices = mapVertices;
            this.size = size;
        }
    }
}
