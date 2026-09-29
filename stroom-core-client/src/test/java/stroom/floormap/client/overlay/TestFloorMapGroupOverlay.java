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

import stroom.floormap.shared.FloorMapGroup;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TestFloorMapGroupOverlay {

    private static final String PURPLE = "#8e24aa";
    private static final String TEAL = "#00897b";

    private static FloorMapGroup group(final String id,
                                       final String colour,
                                       final String... members) {
        return new FloorMapGroup(id, id, colour, Arrays.asList(members));
    }

    @Test
    void testShownGroupColoursItsMembers() {
        final List<FloorMapGroup> groups = Collections.singletonList(
                group("maintenance", PURPLE, "alice", "gate-3"));

        final FloorMapGroupOverlay overlay = FloorMapGroupOverlay.of(
                groups, Collections.singleton("maintenance"));

        assertThat(overlay.colourFor("alice")).isEqualTo(PURPLE);
        assertThat(overlay.colourFor("gate-3")).isEqualTo(PURPLE);
        assertThat(overlay.colourFor("bob")).isNull();
        assertThat(overlay.hasAny()).isTrue();
    }

    /// Groups start hidden, so an unshown group contributes nothing.
    @Test
    void testHiddenGroupContributesNothing() {
        final List<FloorMapGroup> groups = Collections.singletonList(
                group("maintenance", PURPLE, "alice"));

        assertThat(FloorMapGroupOverlay.of(groups, Collections.emptySet()))
                .isSameAs(FloorMapGroupOverlay.EMPTY);
        assertThat(FloorMapGroupOverlay.of(groups, null))
                .isSameAs(FloorMapGroupOverlay.EMPTY);
    }

    @Test
    void testOnlyShownGroupsContribute() {
        final List<FloorMapGroup> groups = Arrays.asList(
                group("maintenance", PURPLE, "alice"),
                group("security", TEAL, "bob"));

        final FloorMapGroupOverlay overlay = FloorMapGroupOverlay.of(
                groups, Collections.singleton("security"));

        assertThat(overlay.colourFor("bob")).isEqualTo(TEAL);
        assertThat(overlay.colourFor("alice")).isNull();
    }

    /// An entity in two shown groups takes the colour of the first group in list
    /// order — predictable from the panel's row order rather than map iteration.
    @Test
    void testFirstShownGroupInListOrderWins() {
        final List<FloorMapGroup> groups = Arrays.asList(
                group("maintenance", PURPLE, "alice"),
                group("security", TEAL, "alice"));
        final List<String> shown = Arrays.asList("security", "maintenance");

        // Both shown, and deliberately passed in the *other* order: list order
        // decides, not the shown-set's iteration order.
        assertThat(FloorMapGroupOverlay.of(groups, shown).colourFor("alice"))
                .isEqualTo(PURPLE);
    }

    /// Highlighting keys on group id, so a rename cannot drop the highlight.
    @Test
    void testRenameDoesNotDropHighlight() {
        final FloorMapGroup renamed = group("maintenance", PURPLE, "alice")
                .withName("Night Maintenance");

        final FloorMapGroupOverlay overlay = FloorMapGroupOverlay.of(
                Collections.singletonList(renamed), Collections.singleton("maintenance"));

        assertThat(overlay.colourFor("alice")).isEqualTo(PURPLE);
    }

    @Test
    void testGroupWithoutColourFallsBackToDefault() {
        final List<FloorMapGroup> groups =
                Collections.singletonList(group("g", null, "alice"));

        assertThat(FloorMapGroupOverlay.of(groups, Collections.singleton("g")).colourFor("alice"))
                .isEqualTo(FloorMapGroup.DEFAULT_COLOUR);
    }

    @Test
    void testEmptyGroupYieldsEmptyOverlay() {
        final List<FloorMapGroup> groups = Collections.singletonList(group("g", PURPLE));

        assertThat(FloorMapGroupOverlay.of(groups, Collections.singleton("g")))
                .isSameAs(FloorMapGroupOverlay.EMPTY);
    }

    @Test
    void testNullsAreSafe() {
        assertThat(FloorMapGroupOverlay.of(null, Collections.singleton("g")))
                .isSameAs(FloorMapGroupOverlay.EMPTY);
        assertThat(FloorMapGroupOverlay.of(Collections.emptyList(), Collections.singleton("g")))
                .isSameAs(FloorMapGroupOverlay.EMPTY);
        assertThat(FloorMapGroupOverlay.EMPTY.colourFor(null)).isNull();
        assertThat(FloorMapGroupOverlay.EMPTY.colourFor("anything")).isNull();
        assertThat(FloorMapGroupOverlay.EMPTY.hasAny()).isFalse();
    }

    @Test
    void testNullGroupEntryIsSkipped() {
        final List<FloorMapGroup> groups = Arrays.asList(null, group("g", PURPLE, "alice"));

        assertThat(FloorMapGroupOverlay.of(groups, Collections.singleton("g")).colourFor("alice"))
                .isEqualTo(PURPLE);
    }
}
