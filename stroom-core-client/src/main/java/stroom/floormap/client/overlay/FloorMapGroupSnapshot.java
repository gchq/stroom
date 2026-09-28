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
import stroom.util.shared.NullSafe;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * The live state of every {@link FloorMapGroup} at one timeline instant — which
 * members are on the map right now, and which areas they are standing in.
 *
 * <p>Recomputed on every facts <em>or</em> events query refresh, exactly like
 * {@link FloorMapAreaMembership} (the two queries refresh independently). It is
 * value-equal on content so the Groups panel can skip a redraw when a refresh
 * changed nothing — playback refreshes land roughly every 300ms and would
 * otherwise re-render the grid continuously.</p>
 *
 * <h3>Where "positioned" comes from</h3>
 * <p>Deliberately <strong>not</strong> from {@link FloorMapAreaMembership}, which
 * cannot answer this question: {@code compute} returns
 * {@link FloorMapAreaMembership#EMPTY} when the map has no areas at all, and
 * {@link FloorMapAreaMembership#getEntityIds()} only ever lists entities inside
 * <em>at least one</em> area. Sourcing positioned-ness from it would report zero
 * members on every map without areas, and would omit any member standing outside
 * every area. So positioned-ness is taken from the events and facts lists
 * directly, and the membership snapshot is used <em>only</em> for the area
 * breakdown.</p>
 *
 * <p>A member counts as positioned when it appears in the events stream at this
 * instant, or exists as a fact — including an area or background, since a group
 * is generic over ids and those genuinely are on the map. Its area breakdown may
 * still be empty: {@link FloorMapAreaMembership} excludes areas and backgrounds
 * from occupancy for its own reasons, and this class does not second-guess it.</p>
 *
 * <h3>Honesty constraint (carried over from the occupant badge)</h3>
 * <p>The positioned count counts <strong>members with a position at this
 * instant</strong>. An entity whose events have gone quiet has no position and is
 * not counted, so the number can fall without anyone moving. It is not a
 * head-count of who is present on site, and must not be labelled as one.</p>
 *
 * <p>Holds no GWT or DOM types so it can be unit-tested on the JVM.</p>
 */
public final class FloorMapGroupSnapshot {

    /** No groups, or no positioned members in any of them. */
    public static final FloorMapGroupSnapshot EMPTY = new FloorMapGroupSnapshot(
            Collections.emptyMap(), Collections.emptyMap());

    /** Positioned member ids, by group id, in group member order. */
    private final Map<String, List<String>> positionedByGroup;

    /**
     * Per group id, how many of its positioned members are in each area, keyed by
     * area fact key. Ordered most-populated first (area key as the tiebreak) so
     * the panel can list them in a useful order without re-sorting.
     */
    private final Map<String, Map<String, Integer>> areaCountsByGroup;

    private FloorMapGroupSnapshot(final Map<String, List<String>> positionedByGroup,
                                  final Map<String, Map<String, Integer>> areaCountsByGroup) {
        this.positionedByGroup = positionedByGroup;
        this.areaCountsByGroup = areaCountsByGroup;
    }

    /**
     * Computes the snapshot for the given groups.
     *
     * @param groups     the document's groups; may be {@code null}
     * @param facts      the facts at this instant; may be {@code null}
     * @param events     the event entities at this instant; may be {@code null}
     * @param membership the area containment at this instant, used only for the
     *                   area breakdown; may be {@code null}
     * @return the snapshot; never {@code null}
     */
    public static FloorMapGroupSnapshot compute(final List<FloorMapGroup> groups,
                                                final List<Fact> facts,
                                                final List<FloorMapObject> events,
                                                final FloorMapAreaMembership membership) {
        if (NullSafe.isEmptyCollection(groups)) {
            return EMPTY;
        }

        final Set<String> positionedIds = positionedIds(facts, events);
        final FloorMapAreaMembership areas = membership != null
                ? membership
                : FloorMapAreaMembership.EMPTY;

        final Map<String, List<String>> positionedByGroup = new LinkedHashMap<>();
        final Map<String, Map<String, Integer>> areaCountsByGroup = new LinkedHashMap<>();

        for (final FloorMapGroup group : groups) {
            if (group == null) {
                continue;
            }
            final List<String> positioned = new ArrayList<>();
            final Map<String, Integer> areaCounts = new LinkedHashMap<>();
            for (final String memberId : group.getMemberIds()) {
                if (!positionedIds.contains(memberId)) {
                    continue;
                }
                positioned.add(memberId);
                // Count the member once per containing area. Nested areas mean a
                // member can legitimately count toward more than one.
                for (final String areaKey : areas.getAreaKeys(memberId)) {
                    areaCounts.merge(areaKey, 1, Integer::sum);
                }
            }
            if (!positioned.isEmpty()) {
                positionedByGroup.put(group.getId(), Collections.unmodifiableList(positioned));
            }
            if (!areaCounts.isEmpty()) {
                areaCountsByGroup.put(group.getId(), sortedByCountDesc(areaCounts));
            }
        }

        return positionedByGroup.isEmpty() && areaCountsByGroup.isEmpty()
                ? EMPTY
                : new FloorMapGroupSnapshot(
                        Collections.unmodifiableMap(positionedByGroup),
                        Collections.unmodifiableMap(areaCountsByGroup));
    }

    /**
     * Every id that has a position on the map at this instant.
     *
     * <p>Events first, then facts, matching {@link FloorMapAreaMembership}'s rule
     * that a live event position beats a static fact twin for the same id. For a
     * mere "is it positioned" test the order does not change the answer, but the
     * two must agree on <em>which</em> ids count or the positioned total and the
     * area breakdown could disagree.</p>
     */
    private static Set<String> positionedIds(final List<Fact> facts,
                                             final List<FloorMapObject> events) {
        final Set<String> ids = new LinkedHashSet<>();
        if (events != null) {
            for (final FloorMapObject event : events) {
                if (event != null && NullSafe.isNonEmptyString(event.getId())) {
                    ids.add(event.getId());
                }
            }
        }
        if (facts != null) {
            for (final Fact fact : facts) {
                if (fact != null && NullSafe.isNonEmptyString(fact.getKey())) {
                    ids.add(fact.getKey());
                }
            }
        }
        return ids;
    }

    /** Re-orders an area-count map most-populated first, area key as tiebreak. */
    private static Map<String, Integer> sortedByCountDesc(final Map<String, Integer> counts) {
        final List<Map.Entry<String, Integer>> entries = new ArrayList<>(counts.entrySet());
        entries.sort((a, b) -> {
            final int cmp = Integer.compare(b.getValue(), a.getValue());
            return cmp != 0
                    ? cmp
                    : a.getKey().compareTo(b.getKey());
        });
        final Map<String, Integer> sorted = new LinkedHashMap<>();
        for (final Map.Entry<String, Integer> entry : entries) {
            sorted.put(entry.getKey(), entry.getValue());
        }
        return Collections.unmodifiableMap(sorted);
    }

    /**
     * The ids of the group's members that have a position at this instant, in
     * membership order.
     *
     * @param groupId the group id; may be {@code null}
     * @return the positioned member ids; empty when none are
     */
    public List<String> getPositionedIds(final String groupId) {
        final List<String> ids = groupId != null
                ? positionedByGroup.get(groupId)
                : null;
        return ids != null
                ? ids
                : Collections.emptyList();
    }

    /**
     * How many of the group's members have a position at this instant.
     *
     * @param groupId the group id; may be {@code null}
     */
    public int getPositionedCount(final String groupId) {
        return getPositionedIds(groupId).size();
    }

    /**
     * How many of the group's positioned members are in each area, most-populated
     * area first.
     *
     * @param groupId the group id; may be {@code null}
     * @return area key → member count; empty when no member is in any area
     */
    public Map<String, Integer> getAreaCounts(final String groupId) {
        final Map<String, Integer> counts = groupId != null
                ? areaCountsByGroup.get(groupId)
                : null;
        return counts != null
                ? counts
                : Collections.emptyMap();
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof final FloorMapGroupSnapshot that)) {
            return false;
        }
        return positionedByGroup.equals(that.positionedByGroup)
                && areaCountsByGroup.equals(that.areaCountsByGroup);
    }

    @Override
    public int hashCode() {
        return Objects.hash(positionedByGroup, areaCountsByGroup);
    }
}
