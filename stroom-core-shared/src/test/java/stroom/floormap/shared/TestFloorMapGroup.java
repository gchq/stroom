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
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

class TestFloorMapGroup {

    private static FloorMapGroup group(final String id, final String name, final String... members) {
        return new FloorMapGroup(id, name, "#8e24aa", Arrays.asList(members));
    }

    // ------------------------------------------------------------------------
    // Membership
    // ------------------------------------------------------------------------

    @Test
    void testMembersKeepInsertionOrder() {
        final FloorMapGroup g = group("g1", "Maintenance")
                .withMember("bob@x.com")
                .withMember("gate-3")
                .withMember("alice@x.com");

        assertThat(g.getMemberIds())
                .containsExactly("bob@x.com", "gate-3", "alice@x.com");
    }

    /// Adding an existing member is a no-op, and does not reorder.
    @Test
    void testAddingExistingMemberIsIdempotent() {
        final FloorMapGroup g = group("g1", "Maintenance", "bob@x.com", "gate-3");
        final FloorMapGroup again = g.withMember("bob@x.com");

        assertThat(again).isSameAs(g);
        assertThat(again.getMemberIds()).containsExactly("bob@x.com", "gate-3");
    }

    @Test
    void testConstructorDropsDuplicateMembers() {
        final FloorMapGroup g = group("g1", "Maintenance", "bob@x.com", "gate-3", "bob@x.com");

        assertThat(g.getMemberIds()).containsExactly("bob@x.com", "gate-3");
    }

    @Test
    void testRemoveMember() {
        final FloorMapGroup g = group("g1", "Maintenance", "bob@x.com", "gate-3")
                .withoutMember("bob@x.com");

        assertThat(g.getMemberIds()).containsExactly("gate-3");
        assertThat(g.contains("bob@x.com")).isFalse();
    }

    @Test
    void testRemovingAbsentMemberIsNoOp() {
        final FloorMapGroup g = group("g1", "Maintenance", "gate-3");

        assertThat(g.withoutMember("nobody")).isSameAs(g);
        assertThat(g.withoutMember(null)).isSameAs(g);
    }

    @Test
    void testBlankMemberIdsAreRejected() {
        final FloorMapGroup g = group("g1", "Maintenance")
                .withMember(null)
                .withMember("");

        assertThat(g.getMemberIds()).isEmpty();
        assertThat(g.countMembers()).isZero();
    }

    @Test
    void testMemberListIsUnmodifiable() {
        final FloorMapGroup g = group("g1", "Maintenance", "bob@x.com");

        assertThat(g.getMemberIds()).isUnmodifiable();
    }

    // ------------------------------------------------------------------------
    // Identity is the id, never the name
    // ------------------------------------------------------------------------

    /// The whole point of giving groups ids: a rename must not disturb membership
    /// or the group's position in the list.
    @Test
    void testRenameKeepsIdentityMembersAndPosition() {
        final List<FloorMapGroup> groups = Arrays.asList(
                group("g1", "Maintenance", "bob@x.com"),
                group("g2", "Security", "gate-3"));

        final FloorMapGroup renamed = FloorMapGroup.find(groups, "g2")
                .withName("Night Security");
        final List<FloorMapGroup> after = FloorMapGroup.replace(groups, renamed);

        assertThat(after).hasSize(2);
        assertThat(after.get(1).getId()).isEqualTo("g2");
        assertThat(after.get(1).getName()).isEqualTo("Night Security");
        assertThat(after.get(1).getMemberIds()).containsExactly("gate-3");
        // Position preserved — a rename must not reorder the panel.
        assertThat(after.getFirst().getId()).isEqualTo("g1");
    }

    /// Two groups may share a name (ids are identity), and each stays
    /// independently editable — the case name-keyed helpers would have corrupted.
    @Test
    void testSameNamedGroupsRemainIndependent() {
        final List<FloorMapGroup> groups = Arrays.asList(
                group("g1", "Security", "gate-1"),
                group("g2", "Security", "gate-2"));

        final List<FloorMapGroup> after = FloorMapGroup.replace(groups,
                FloorMapGroup.find(groups, "g2").withMember("gate-3"));

        assertThat(after.getFirst().getMemberIds()).containsExactly("gate-1");
        assertThat(after.get(1).getMemberIds()).containsExactly("gate-2", "gate-3");
    }

    @Test
    void testReplaceAppendsWhenIdIsNew() {
        final List<FloorMapGroup> groups = Collections.singletonList(group("g1", "Maintenance"));

        final List<FloorMapGroup> after = FloorMapGroup.replace(groups, group("g2", "Security"));

        assertThat(after).hasSize(2);
        assertThat(after.get(1).getId()).isEqualTo("g2");
    }

    @Test
    void testWithoutRemovesById() {
        final List<FloorMapGroup> groups = Arrays.asList(
                group("g1", "Security", "gate-1"),
                group("g2", "Security", "gate-2"));

        final List<FloorMapGroup> after = FloorMapGroup.without(groups, "g1");

        assertThat(after).hasSize(1);
        assertThat(after.getFirst().getId()).isEqualTo("g2");
    }

    /// A hand-edited document with no id still opens: the name stands in.
    @Test
    void testMissingIdFallsBackToName() {
        assertThat(new FloorMapGroup(null, "Maintenance", null, null).getId())
                .isEqualTo("Maintenance");
        assertThat(new FloorMapGroup("", "Maintenance", null, null).getId())
                .isEqualTo("Maintenance");
    }

    @Test
    void testFindNullsAreSafe() {
        assertThat(FloorMapGroup.find(null, "g1")).isNull();
        assertThat(FloorMapGroup.find(Collections.singletonList(group("g1", "A")), null)).isNull();
        assertThat(FloorMapGroup.without(null, "g1")).isEmpty();
    }

    // ------------------------------------------------------------------------
    // Id generation
    // ------------------------------------------------------------------------

    @Test
    void testGeneratedIdIsPrefixed() {
        assertThat(FloorMapGroup.generateId(null, new Random(1)))
                .startsWith("group-");
    }

    /// The collision-retry path, driven rather than hoped for: a seeded generator
    /// produces a known first value, so seeding an existing group with exactly
    /// that id forces the retry.
    @Test
    void testGeneratedIdAvoidsCollision() {
        final String firstDraw = "group-" + new Random(42).nextInt(99999);
        final List<FloorMapGroup> existing =
                Collections.singletonList(group(firstDraw, "Taken"));

        final String generated = FloorMapGroup.generateId(existing, new Random(42));

        assertThat(generated)
                .startsWith("group-")
                .isNotEqualTo(firstDraw);
    }

    @Test
    void testGeneratedIdsAreDistinctAcrossManyGroups() {
        final Random random = new Random(7);
        List<FloorMapGroup> groups = Collections.emptyList();
        for (int i = 0; i < 50; i++) {
            groups = FloorMapGroup.replace(groups, FloorMapGroup.create(groups, random));
        }

        assertThat(groups).hasSize(50);
        assertThat(groups.stream().map(FloorMapGroup::getId).distinct()).hasSize(50);
    }

    // ------------------------------------------------------------------------
    // Defaults
    // ------------------------------------------------------------------------

    @Test
    void testUniqueNameNumbersFromTwo() {
        List<FloorMapGroup> groups = Collections.emptyList();
        assertThat(FloorMapGroup.uniqueName(groups, "Group")).isEqualTo("Group");

        groups = Arrays.asList(group("g1", "Group"), group("g2", "Group 2"));
        assertThat(FloorMapGroup.uniqueName(groups, "Group")).isEqualTo("Group 3");
    }

    @Test
    void testUniqueNameNullDefaultsToGroup() {
        assertThat(FloorMapGroup.uniqueName(null, null)).isEqualTo(FloorMapGroup.DEFAULT_NAME);
    }

    @Test
    void testCreateStartsEmptyWithDefaultColour() {
        final FloorMapGroup created = FloorMapGroup.create(null, new Random(1));

        assertThat(created.getMemberIds()).isEmpty();
        assertThat(created.getName()).isEqualTo("Group");
        assertThat(created.getColour()).isEqualTo(FloorMapGroup.DEFAULT_COLOUR);
    }

    /// The default colour must not collide with the three the canvas already means
    /// something specific with, or a user's first group would look like a
    /// selection or a containment hint.
    @Test
    void testDefaultColourAvoidsReservedColours() {
        assertThat(FloorMapGroup.DEFAULT_COLOUR)
                .isNotEqualToIgnoringCase("#1e88e5")   // accent / handles / area fill
                .isNotEqualToIgnoringCase("#ff9800");  // selected
        // The related-highlight colour is checked from TestFloorMapHighlight, which
        // lives with the client-side FloorMapHighlight.
    }

    @Test
    void testColourOrDefaultFillsBlanks() {
        assertThat(new FloorMapGroup("g1", "A", null, null).findColourOrDefault())
                .isEqualTo(FloorMapGroup.DEFAULT_COLOUR);
        assertThat(new FloorMapGroup("g1", "A", "", null).findColourOrDefault())
                .isEqualTo(FloorMapGroup.DEFAULT_COLOUR);
        assertThat(new FloorMapGroup("g1", "A", "#123456", null).findColourOrDefault())
                .isEqualTo("#123456");
    }

    // ------------------------------------------------------------------------
    // Equality — the document's dirty check diffs whole documents
    // ------------------------------------------------------------------------

    @Test
    void testEqualityIsContentBased() {
        assertThat(group("g1", "A", "bob")).isEqualTo(group("g1", "A", "bob"));
        // A rename, a recolour and a membership change must each register.
        assertThat(group("g1", "A", "bob")).isNotEqualTo(group("g1", "B", "bob"));
        assertThat(group("g1", "A", "bob")).isNotEqualTo(group("g1", "A", "bob", "sue"));
        assertThat(group("g1", "A", "bob"))
                .isNotEqualTo(new FloorMapGroup("g1", "A", "#000000", Collections.singletonList("bob")));
    }
}
