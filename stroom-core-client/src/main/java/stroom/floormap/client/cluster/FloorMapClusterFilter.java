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

import stroom.util.shared.NullSafe;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Finding one member in a crowd: the search and the two dropdown filters over a
 * cluster's member list.
 *
 * <p>A cluster of ten needs no search. A cluster of several hundred — which is
 * exactly what the clustering feature exists to make readable — is unusable as a
 * flat list, and paging through it looking for one name is worse than not having
 * the dialog at all.</p>
 *
 * <h2>What can be filtered on, and what cannot</h2>
 * <p><strong>Not type.</strong> Clustering runs per type (see
 * {@link FloorMapClusterOverlay}), so every member of one cluster shares a type
 * and a type filter could only ever select all or nothing. What actually varies
 * within a cluster is who the members are, which area they are standing in, and
 * which groups they belong to.</p>
 *
 * <h2>Offering a choice, or none</h2>
 * <p>A dropdown whose every option selects the same rows is furniture. Both
 * option lists come back <strong>empty</strong> unless there are at least two
 * distinct answers among the members, and the view hides the control when they
 * do — so a cluster standing wholly inside one room shows no Area filter at all,
 * rather than one that does nothing.</p>
 *
 * <p>Holds no GWT or DOM types so it can be unit-tested on the JVM.</p>
 */
public final class FloorMapClusterFilter {

    /** The Area dropdown's "no constraint" option; always first when offered. */
    public static final String ANY_AREA = "Any area";

    /** The Area dropdown's option for members standing in no area at all. */
    public static final String NO_AREA = "Not inside an area";

    /** The Group dropdown's "no constraint" option; always first when offered. */
    public static final String ANY_GROUP = "Any group";

    /** The Group dropdown's option for members belonging to no group. */
    public static final String NO_GROUP = "Not in a group";

    private FloorMapClusterFilter() {
        // Utility class.
    }

    /**
     * The Area dropdown's options: {@link #ANY_AREA}, then every area any member
     * is standing in (alphabetically), then {@link #NO_AREA} if any member is in
     * none.
     *
     * <p>Areas come last-but-one and "not inside an area" last so the named
     * choices — the ones a reader is scanning for — are not separated by it.</p>
     *
     * @param members the cluster's members; may be {@code null}
     * @return the options, or an <strong>empty list</strong> when there is
     *         nothing to choose between (fewer than two distinct answers)
     */
    public static List<String> areaOptions(final List<FloorMapClusterMember> members) {
        // Sorted so the dropdown reads alphabetically; the cluster's own member
        // order says nothing about which areas matter.
        final Set<String> named = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        boolean anyWithout = false;
        if (members != null) {
            for (final FloorMapClusterMember member : members) {
                if (member.getAreaNames().isEmpty()) {
                    anyWithout = true;
                } else {
                    named.addAll(member.getAreaNames());
                }
            }
        }
        return options(named, anyWithout, ANY_AREA, NO_AREA);
    }

    /**
     * The Group dropdown's options: {@link #ANY_GROUP}, then every group any
     * member belongs to (alphabetically), then {@link #NO_GROUP} if any member
     * belongs to none.
     *
     * @param members the cluster's members; may be {@code null}
     * @return the options, or an <strong>empty list</strong> when there is
     *         nothing to choose between
     */
    public static List<String> groupOptions(final List<FloorMapClusterMember> members) {
        final Set<String> named = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        boolean anyWithout = false;
        if (members != null) {
            for (final FloorMapClusterMember member : members) {
                if (member.getGroupNames().isEmpty()) {
                    anyWithout = true;
                } else {
                    named.addAll(member.getGroupNames());
                }
            }
        }
        return options(named, anyWithout, ANY_GROUP, NO_GROUP);
    }

    /**
     * Assembles one dropdown's options, or none at all when every option would
     * select the same rows.
     */
    private static List<String> options(final Set<String> named,
                                        final boolean anyWithout,
                                        final String anyOption,
                                        final String noneOption) {
        final int choices = named.size() + (anyWithout
                ? 1
                : 0);
        if (choices < 2) {
            return Collections.emptyList();
        }
        final List<String> options = new ArrayList<>(choices + 1);
        options.add(anyOption);
        options.addAll(named);
        if (anyWithout) {
            options.add(noneOption);
        }
        return Collections.unmodifiableList(options);
    }

    /**
     * The members matching all three controls, in the order given.
     *
     * <p>The three combine with AND — each control narrows what the others left —
     * which is the only reading of "search within this filter" that does not
     * surprise.</p>
     *
     * @param members the cluster's members; {@code null} is treated as empty
     * @param search  the search text; blank matches everything. Whitespace
     *                separates terms and <strong>every</strong> term must appear
     *                somewhere in the row, so "ali bay" finds Alice in the
     *                Loading Bay. Matching is case-insensitive substring against
     *                the name, id, type, area names and group names — everything
     *                the row displays, so nothing a reader can see is unsearchable
     * @param area    the selected area option; {@code null}, blank or
     *                {@link #ANY_AREA} applies no constraint,
     *                {@link #NO_AREA} keeps members in no area, anything else
     *                keeps members standing in that area
     * @param group   the selected group option, read the same way against
     *                {@link #ANY_GROUP} / {@link #NO_GROUP}
     * @return the matching members; never {@code null}
     */
    public static List<FloorMapClusterMember> filter(final List<FloorMapClusterMember> members,
                                                     final String search,
                                                     final String area,
                                                     final String group) {
        if (NullSafe.isEmptyCollection(members)) {
            return Collections.emptyList();
        }
        final List<String> terms = terms(search);
        final List<FloorMapClusterMember> out = new ArrayList<>(members.size());
        for (final FloorMapClusterMember member : members) {
            if (matchesArea(member, area)
                    && matchesGroup(member, group)
                    && matchesSearch(member, terms)) {
                out.add(member);
            }
        }
        return out;
    }

    /**
     * The members sorted by display name, case-insensitively, with the id as the
     * tiebreak so equal names order stably rather than by the cluster's build
     * order.
     *
     * <p>Cluster membership comes out in the order the clustering lattice
     * happened to visit — meaningless to a reader, and unscannable once the list
     * is long enough to need a search box.</p>
     *
     * @param members the members; {@code null} is treated as empty
     * @return a new sorted list; never {@code null}
     */
    public static List<FloorMapClusterMember> sortedByName(
            final List<FloorMapClusterMember> members) {
        if (members == null) {
            return Collections.emptyList();
        }
        final List<FloorMapClusterMember> sorted = new ArrayList<>(members);
        sorted.sort((a, b) -> {
            final int byName = nullSafe(a.getName()).compareToIgnoreCase(nullSafe(b.getName()));
            return byName != 0
                    ? byName
                    : nullSafe(a.getId()).compareToIgnoreCase(nullSafe(b.getId()));
        });
        return sorted;
    }

    /**
     * The search text split into terms, lower-cased ready for matching. Blank
     * input yields no terms, which matches everything.
     */
    private static List<String> terms(final String search) {
        if (NullSafe.isBlankString(search)) {
            return Collections.emptyList();
        }
        // LinkedHashSet: a term repeated by a fumbled keystroke costs nothing to
        // test twice, but deduplicating keeps the common case to one pass.
        final Set<String> terms = new LinkedHashSet<>();
        for (final String term : search.trim().toLowerCase().split("\\s+")) {
            if (!term.isEmpty()) {
                terms.add(term);
            }
        }
        return new ArrayList<>(terms);
    }

    /** True if every term appears in at least one of the member's displayed values. */
    private static boolean matchesSearch(final FloorMapClusterMember member,
                                         final List<String> terms) {
        for (final String term : terms) {
            if (!containsTerm(member, term)) {
                return false;
            }
        }
        return true;
    }

    private static boolean containsTerm(final FloorMapClusterMember member, final String term) {
        return contains(member.getName(), term)
                || contains(member.getId(), term)
                || contains(member.getType(), term)
                || containsAny(member.getAreaNames(), term)
                || containsAny(member.getGroupNames(), term);
    }

    private static boolean containsAny(final List<String> values, final String term) {
        for (final String value : values) {
            if (contains(value, term)) {
                return true;
            }
        }
        return false;
    }

    private static boolean contains(final String value, final String lowerTerm) {
        return value != null && value.toLowerCase().contains(lowerTerm);
    }

    private static boolean matchesArea(final FloorMapClusterMember member, final String area) {
        if (isUnconstrained(area, ANY_AREA)) {
            return true;
        }
        return NO_AREA.equals(area)
                ? member.getAreaNames().isEmpty()
                : containsIgnoreCase(member.getAreaNames(), area);
    }

    private static boolean matchesGroup(final FloorMapClusterMember member, final String group) {
        if (isUnconstrained(group, ANY_GROUP)) {
            return true;
        }
        return NO_GROUP.equals(group)
                ? member.getGroupNames().isEmpty()
                : containsIgnoreCase(member.getGroupNames(), group);
    }

    /**
     * Case-insensitive membership test, so matching agrees with the case-insensitive
     * de-duplication that built the options.
     *
     * <p>The two used to disagree, and the disagreement lost data silently.
     * {@link #areaOptions} and {@link #groupOptions} collect names into a
     * {@code TreeSet(String.CASE_INSENSITIVE_ORDER)}, so "Lobby" and "lobby" collapse
     * into whichever spelling was seen first and only that one is offered. Matching
     * with a case-sensitive {@code List.contains} then kept only the members spelled
     * that way — so a user selecting the sole option the dropdown offered silently
     * lost every member whose stored name differed only in case, with the filter
     * reporting no reason. Either half could have been changed; matching
     * case-insensitively is the one that keeps every member reachable.</p>
     */
    private static boolean containsIgnoreCase(final List<String> names, final String target) {
        for (final String name : names) {
            if (name != null && name.equalsIgnoreCase(target)) {
                return true;
            }
        }
        return false;
    }

    /**
     * True when a dropdown imposes no constraint: unset, blank, or sitting on its
     * "any" option. Blank counts because a control that has never been populated
     * reads as empty, and an empty control must not hide every row.
     */
    private static boolean isUnconstrained(final String selected, final String anyOption) {
        return NullSafe.isBlankString(selected) || anyOption.equals(selected);
    }

    private static String nullSafe(final String s) {
        return s != null
                ? s
                : "";
    }
}
