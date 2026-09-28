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

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TestFloorMapClusterFilter {

    private static FloorMapClusterMember member(final String id,
                                                final String name,
                                                final List<String> areas,
                                                final List<String> groups) {
        return new FloorMapClusterMember(id, name, "person", areas, groups);
    }

    /** Alice in the Loading Bay and on nights; Bob in the Office, no group. */
    private static List<FloorMapClusterMember> twoMembers() {
        return Arrays.asList(
                member("user-1", "Alice", Collections.singletonList("Loading Bay"),
                        Collections.singletonList("Night Shift")),
                member("user-2", "Bob", Collections.singletonList("Office"),
                        Collections.emptyList()));
    }

    private static List<String> names(final List<FloorMapClusterMember> members) {
        return members.stream().map(FloorMapClusterMember::getName).toList();
    }

    // -----------------------------------------------------------------------
    // Search
    // -----------------------------------------------------------------------

    /** Blank search is not a filter — it must not hide anything. */
    @Test
    void testBlankSearchKeepsEverything() {
        final List<FloorMapClusterMember> members = twoMembers();
        assertThat(FloorMapClusterFilter.filter(members, null, null, null)).hasSize(2);
        assertThat(FloorMapClusterFilter.filter(members, "", null, null)).hasSize(2);
        assertThat(FloorMapClusterFilter.filter(members, "   ", null, null)).hasSize(2);
    }

    /** Matching is case-insensitive substring, not prefix or whole-word. */
    @Test
    void testSearchIsCaseInsensitiveSubstring() {
        assertThat(names(FloorMapClusterFilter.filter(twoMembers(), "ALI", null, null)))
                .containsExactly("Alice");
        assertThat(names(FloorMapClusterFilter.filter(twoMembers(), "lic", null, null)))
                .containsExactly("Alice");
    }

    /** Everything the row displays is searchable, not just the name. */
    @Test
    void testSearchCoversEveryDisplayedValue() {
        assertThat(names(FloorMapClusterFilter.filter(twoMembers(), "user-2", null, null)))
                .containsExactly("Bob");
        assertThat(names(FloorMapClusterFilter.filter(twoMembers(), "office", null, null)))
                .containsExactly("Bob");
        assertThat(names(FloorMapClusterFilter.filter(twoMembers(), "night", null, null)))
                .containsExactly("Alice");
        // Type is the same for every member of a cluster, so it selects all.
        assertThat(FloorMapClusterFilter.filter(twoMembers(), "person", null, null)).hasSize(2);
    }

    /**
     * Several words narrow rather than widen, and may land in different fields —
     * "ali bay" is a name and an area, and finds the one row that has both.
     */
    @Test
    void testEveryTermMustMatchSomewhere() {
        assertThat(names(FloorMapClusterFilter.filter(twoMembers(), "ali bay", null, null)))
                .containsExactly("Alice");
        assertThat(FloorMapClusterFilter.filter(twoMembers(), "ali office", null, null))
                .isEmpty();
    }

    /** No match is an empty list, not everything. */
    @Test
    void testUnmatchedSearchFindsNothing() {
        assertThat(FloorMapClusterFilter.filter(twoMembers(), "zzz", null, null)).isEmpty();
    }

    // -----------------------------------------------------------------------
    // Dropdowns
    // -----------------------------------------------------------------------

    /** The "any" option is first, named areas next, "in none" last. */
    @Test
    void testAreaOptionsOrder() {
        final List<FloorMapClusterMember> members = Arrays.asList(
                member("1", "A", Collections.singletonList("Office"), null),
                member("2", "B", Collections.singletonList("Loading Bay"), null),
                member("3", "C", Collections.emptyList(), null));
        assertThat(FloorMapClusterFilter.areaOptions(members))
                .containsExactly("Any area", "Loading Bay", "Office", "Not inside an area");
    }

    /** A member in several areas contributes all of them, deduplicated. */
    @Test
    void testAreaOptionsAreDistinct() {
        final List<FloorMapClusterMember> members = Arrays.asList(
                member("1", "A", Arrays.asList("Server Rack", "Server Room"), null),
                member("2", "B", Collections.singletonList("Server Room"), null));
        assertThat(FloorMapClusterFilter.areaOptions(members))
                .containsExactly("Any area", "Server Rack", "Server Room");
    }

    /**
     * A dropdown that could only ever select all or nothing is not offered: every
     * member in one area, or none of them in any, is no choice at all.
     */
    @Test
    void testNoDropdownWithoutAChoice() {
        final List<FloorMapClusterMember> allSameArea = Arrays.asList(
                member("1", "A", Collections.singletonList("Office"), null),
                member("2", "B", Collections.singletonList("Office"), null));
        assertThat(FloorMapClusterFilter.areaOptions(allSameArea)).isEmpty();

        final List<FloorMapClusterMember> noneInAnyArea = Arrays.asList(
                member("1", "A", null, null),
                member("2", "B", null, null));
        assertThat(FloorMapClusterFilter.areaOptions(noneInAnyArea)).isEmpty();

        assertThat(FloorMapClusterFilter.areaOptions(Collections.emptyList())).isEmpty();
        assertThat(FloorMapClusterFilter.areaOptions(null)).isEmpty();
    }

    /** Groups are offered on the same terms as areas. */
    @Test
    void testGroupOptions() {
        assertThat(FloorMapClusterFilter.groupOptions(twoMembers()))
                .containsExactly("Any group", "Night Shift", "Not in a group");
        assertThat(FloorMapClusterFilter.groupOptions(Collections.singletonList(
                member("1", "A", null, Collections.singletonList("Security")))))
                .isEmpty();
    }

    // -----------------------------------------------------------------------
    // Dropdown filtering
    // -----------------------------------------------------------------------

    /** The "any" option, an unset control and an empty one all mean no constraint. */
    @Test
    void testAnyOptionConstrainsNothing() {
        assertThat(FloorMapClusterFilter.filter(twoMembers(), null, "Any area", "Any group"))
                .hasSize(2);
        assertThat(FloorMapClusterFilter.filter(twoMembers(), null, "", "")).hasSize(2);
    }

    /** Selecting an area keeps only the members standing in it. */
    @Test
    void testAreaSelectionNarrows() {
        assertThat(names(FloorMapClusterFilter.filter(twoMembers(), null, "Office", null)))
                .containsExactly("Bob");
    }

    /** A member in several areas is kept by any one of them. */
    @Test
    void testMemberInSeveralAreasMatchesEach() {
        final List<FloorMapClusterMember> members = Collections.singletonList(
                member("1", "A", Arrays.asList("Server Rack", "Server Room"), null));
        assertThat(FloorMapClusterFilter.filter(members, null, "Server Rack", null)).hasSize(1);
        assertThat(FloorMapClusterFilter.filter(members, null, "Server Room", null)).hasSize(1);
        assertThat(FloorMapClusterFilter.filter(members, null, "Office", null)).isEmpty();
    }

    /**
     * The option offered for a name spelled inconsistently keeps <em>every</em> member with
     * that name, whatever its case.
     *
     * <p>This is the whole point of matching case-insensitively. The options are collected
     * into a {@code TreeSet(String.CASE_INSENSITIVE_ORDER)}, so "Lobby" and "lobby" collapse
     * to a single offered option — and matching used to use a case-sensitive
     * {@code List.contains}, so selecting that one option silently dropped the member stored
     * under the other spelling. There is no third option to pick instead and no message: the
     * member is simply not in the list.</p>
     */
    @Test
    void testCaseVariantAreaNamesAreAllKeptByTheOfferedOption() {
        final List<FloorMapClusterMember> members = Arrays.asList(
                member("1", "Alice", Collections.singletonList("Lobby"), null),
                member("2", "Bob", Collections.singletonList("lobby"), null),
                member("3", "Carol", Collections.singletonList("Office"), null));

        // Exactly one option is offered for the two spellings.
        assertThat(FloorMapClusterFilter.areaOptions(members))
                .containsExactly("Any area", "Lobby", "Office");

        // ...and it must reach both of them.
        assertThat(names(FloorMapClusterFilter.filter(members, null, "Lobby", null)))
                .containsExactly("Alice", "Bob");
    }

    /** The same for groups, which share the de-duplication and had the same mismatch. */
    @Test
    void testCaseVariantGroupNamesAreAllKeptByTheOfferedOption() {
        final List<FloorMapClusterMember> members = Arrays.asList(
                member("1", "Alice", null, Collections.singletonList("Security")),
                member("2", "Bob", null, Collections.singletonList("SECURITY")),
                member("3", "Carol", null, Collections.singletonList("Maintenance")));

        assertThat(FloorMapClusterFilter.groupOptions(members))
                .containsExactly("Any group", "Maintenance", "Security");

        assertThat(names(FloorMapClusterFilter.filter(members, null, null, "Security")))
                .containsExactly("Alice", "Bob");
    }

    /**
     * A selection whose case matches no stored spelling still matches, so the filter cannot
     * be broken by the option list being rebuilt from a differently-cased first sighting.
     */
    @Test
    void testAreaSelectionIgnoresCaseEntirely() {
        final List<FloorMapClusterMember> members = Collections.singletonList(
                member("1", "Alice", Collections.singletonList("Loading Bay"), null));

        assertThat(FloorMapClusterFilter.filter(members, null, "LOADING BAY", null)).hasSize(1);
        assertThat(FloorMapClusterFilter.filter(members, null, "loading bay", null)).hasSize(1);
        // Still a name test, not a substring one.
        assertThat(FloorMapClusterFilter.filter(members, null, "Loading", null)).isEmpty();
    }

    /** The "in none" options select exactly the members with nothing. */
    @Test
    void testNoneOptions() {
        assertThat(names(FloorMapClusterFilter.filter(
                twoMembers(), null, null, "Not in a group")))
                .containsExactly("Bob");
        final List<FloorMapClusterMember> members = Arrays.asList(
                member("1", "A", Collections.singletonList("Office"), null),
                member("2", "B", null, null));
        assertThat(names(FloorMapClusterFilter.filter(
                members, null, "Not inside an area", null)))
                .containsExactly("B");
    }

    /** The three controls narrow together — search within the filtered set. */
    @Test
    void testControlsCombineWithAnd() {
        final List<FloorMapClusterMember> members = Arrays.asList(
                member("1", "Alice", Collections.singletonList("Office"),
                        Collections.singletonList("Night Shift")),
                member("2", "Alina", Collections.singletonList("Office"),
                        Collections.emptyList()),
                member("3", "Alan", Collections.singletonList("Loading Bay"),
                        Collections.singletonList("Night Shift")));
        assertThat(names(FloorMapClusterFilter.filter(
                members, "al", "Office", "Night Shift")))
                .containsExactly("Alice");
    }

    // -----------------------------------------------------------------------
    // Sorting
    // -----------------------------------------------------------------------

    /** Alphabetical by name regardless of case, with the id as a stable tiebreak. */
    @Test
    void testSortedByName() {
        final List<FloorMapClusterMember> members = Arrays.asList(
                member("z", "bob", null, null),
                member("b", "Alice", null, null),
                member("a", "Alice", null, null),
                member("y", "Carol", null, null));
        assertThat(FloorMapClusterFilter.sortedByName(members).stream()
                .map(FloorMapClusterMember::getId).toList())
                .containsExactly("a", "b", "z", "y");
    }

    /** Sorting copies rather than reordering the caller's list. */
    @Test
    void testSortDoesNotMutateInput() {
        final List<FloorMapClusterMember> members = twoMembers();
        FloorMapClusterFilter.sortedByName(members);
        assertThat(names(members)).containsExactly("Alice", "Bob");
        assertThat(FloorMapClusterFilter.sortedByName(null)).isEmpty();
    }
}
