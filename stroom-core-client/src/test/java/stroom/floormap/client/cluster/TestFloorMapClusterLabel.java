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

import stroom.floormap.client.model.FloorMapObject;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TestFloorMapClusterLabel {

    /// The reported wording: a count plus the thing being counted.
    @Test
    void testDescribeNamesWhatIsCounted() {
        assertThat(FloorMapClusterLabel.describe(10, "user")).isEqualTo("10 users");
        assertThat(FloorMapClusterLabel.describe(3, "object")).isEqualTo("3 objects");
        assertThat(FloorMapClusterLabel.describe(10, "person")).isEqualTo("10 people");
    }

    /// One of something is not pluralised, even though a cluster never has one.
    @Test
    void testSingularCount() {
        assertThat(FloorMapClusterLabel.describe(1, "user")).isEqualTo("1 user");
        assertThat(FloorMapClusterLabel.describe(1, "person")).isEqualTo("1 person");
        assertThat(FloorMapClusterLabel.describe(1, null)).isEqualTo("1 entity");
    }

    /// A type-less entity falls back to the panel's union term. "objects" would
    /// collide with the `object` fact type and read as excluding people.
    @Test
    void testNoTypeFallsBackToEntities() {
        assertThat(FloorMapClusterLabel.describe(4, null)).isEqualTo("4 entities");
        assertThat(FloorMapClusterLabel.describe(4, "")).isEqualTo("4 entities");
        assertThat(FloorMapClusterLabel.describe(4, "   ")).isEqualTo("4 entities");
        assertThat(FloorMapClusterLabel.plural(null)).isEqualTo("entities");
    }

    /// The irregulars the rules below cannot derive.
    @Test
    void testIrregularPlurals() {
        assertThat(FloorMapClusterLabel.plural("person")).isEqualTo("people");
        assertThat(FloorMapClusterLabel.plural("man")).isEqualTo("men");
        assertThat(FloorMapClusterLabel.plural("woman")).isEqualTo("women");
        assertThat(FloorMapClusterLabel.plural("child")).isEqualTo("children");
        assertThat(FloorMapClusterLabel.plural("mouse")).isEqualTo("mice");
    }

    /// A capitalised type keeps its capital through an irregular substitution.
    @Test
    void testCapitalisationIsPreserved() {
        assertThat(FloorMapClusterLabel.plural("Person")).isEqualTo("People");
        assertThat(FloorMapClusterLabel.describe(6, "Person")).isEqualTo("6 People");
        assertThat(FloorMapClusterLabel.plural("Device")).isEqualTo("Devices");
    }

    /// Sibilant endings take "es" rather than a bare "s".
    @Test
    void testSibilantEndingsTakeEs() {
        assertThat(FloorMapClusterLabel.plural("box")).isEqualTo("boxes");
        assertThat(FloorMapClusterLabel.plural("batch")).isEqualTo("batches");
        assertThat(FloorMapClusterLabel.plural("press")).isEqualTo("presses");
        assertThat(FloorMapClusterLabel.plural("brush")).isEqualTo("brushes");
    }

    /// Consonant + y becomes "ies"; vowel + y just takes an "s".
    @Test
    void testYEndings() {
        assertThat(FloorMapClusterLabel.plural("entity")).isEqualTo("entities");
        assertThat(FloorMapClusterLabel.plural("trolley")).isEqualTo("trolleys");
    }

    /// A type name that is already plural is left alone. Nothing stops a document
    /// naming its types `"users"`, and `"userses"` would be nonsense —
    /// see the class javadoc for the trade-off this makes.
    @Test
    void testAlreadyPluralTypesAreLeftAlone() {
        assertThat(FloorMapClusterLabel.plural("users")).isEqualTo("users");
        assertThat(FloorMapClusterLabel.describe(9, "users")).isEqualTo("9 users");
        assertThat(FloorMapClusterLabel.plural("devices")).isEqualTo("devices");
    }

    /// An irregular plural used as the type name is also left alone — these end in
    /// no `s`, so the trailing-s rule cannot catch them.
    @Test
    void testAlreadyIrregularPluralTypesAreLeftAlone() {
        assertThat(FloorMapClusterLabel.plural("people")).isEqualTo("people");
        assertThat(FloorMapClusterLabel.plural("children")).isEqualTo("children");
        assertThat(FloorMapClusterLabel.plural("men")).isEqualTo("men");
        assertThat(FloorMapClusterLabel.describe(12, "people")).isEqualTo("12 people");
    }

    /// A cluster holding the tracked entity names them, so a user following someone
    /// into a crowd can see the glyph is still theirs.
    @Test
    void testDescribeWithFocus() {
        assertThat(FloorMapClusterLabel.describeWithFocus(10, "Alice"))
                .isEqualTo("Alice + 9 others");
        assertThat(FloorMapClusterLabel.describeWithFocus(3, "Alice"))
                .isEqualTo("Alice + 2 others");
    }

    /// Singular is handled: "+ 1 other", never "+ 1 others".
    @Test
    void testDescribeWithFocusSingularOther() {
        assertThat(FloorMapClusterLabel.describeWithFocus(2, "Alice"))
                .isEqualTo("Alice + 1 other");
    }

    /// A count of one has no "others" to report. Not reachable for a real cluster,
    /// but "Alice + 0 others" would be worse than just the name.
    @Test
    void testDescribeWithFocusDegenerateCount() {
        assertThat(FloorMapClusterLabel.describeWithFocus(1, "Alice")).isEqualTo("Alice");
        assertThat(FloorMapClusterLabel.describeWithFocus(0, "Alice")).isEqualTo("Alice");
    }

    /// An unfocused cluster keeps the counted wording.
    @Test
    void testCaptionForUnfocusedCluster() {
        final FloorMapCluster cluster = FloorMapClusterOverlay.compute(
                        null,
                        Arrays.asList(
                                new FloorMapObject("alice", "user", 0, 0),
                                new FloorMapObject("bob", "user", 1, 0)),
                        10,
                        null)
                .getClusters().getFirst();

        assertThat(FloorMapClusterLabel.captionFor(cluster, ignored -> "Ignored"))
                .isEqualTo("2 users");
    }

    /// A focused cluster is captioned with the focused member's *display
    /// name*, resolved by the caller — the shared code has only ids.
    @Test
    void testCaptionForFocusedClusterUsesTheResolvedName() {
        final FloorMapCluster cluster = focusedCluster();

        assertThat(FloorMapClusterLabel.captionFor(
                cluster, id -> "alice@example.com".equals(id) ? "Alice" : null))
                .isEqualTo("Alice + 2 others");
    }

    /// With no resolver, or one that does not know the id, the caption falls back to
    /// the id rather than dropping the name — an unnamed glyph in a crowd is the
    /// thing this wording exists to prevent.
    @Test
    void testCaptionForFocusedClusterFallsBackToTheId() {
        final FloorMapCluster cluster = focusedCluster();

        assertThat(FloorMapClusterLabel.captionFor(cluster, null))
                .isEqualTo("alice@example.com + 2 others");
        assertThat(FloorMapClusterLabel.captionFor(cluster, ignored -> null))
                .isEqualTo("alice@example.com + 2 others");
        assertThat(FloorMapClusterLabel.captionFor(cluster, ignored -> "   "))
                .isEqualTo("alice@example.com + 2 others");
    }

    /// A cluster of three users, focused on alice.
    private static FloorMapCluster focusedCluster() {
        return FloorMapClusterOverlay.compute(
                        null,
                        Arrays.asList(
                                new FloorMapObject("alice@example.com", "user", 0, 0),
                                new FloorMapObject("bob", "user", 1, 0),
                                new FloorMapObject("carol", "user", 2, 0)),
                        10,
                        Collections.singleton("alice@example.com"))
                .getClusters().getFirst();
    }

    /// A cluster small enough to list in full is listed in full.
    @Test
    void testHoverNamesListsEveryNameWhenItFits() {
        final List<String> names = Arrays.asList("Alice", "Bob", "Carol");

        assertThat(FloorMapClusterLabel.hoverNames(names, 20))
                .containsExactly("Alice", "Bob", "Carol");
    }

    /// A cluster too big to list says how many were left out, rather than trailing
    /// off or hiding them behind an opaque marker.
    @Test
    void testHoverNamesReportsHowManyAreNotShown() {
        final List<String> names = new ArrayList<>();
        for (int i = 0; i < 400; i++) {
            names.add("user" + i);
        }

        final List<String> lines = FloorMapClusterLabel.hoverNames(names, 20);

        assertThat(lines).hasSize(21);
        assertThat(lines.getFirst()).isEqualTo("user0");
        assertThat(lines.get(19)).isEqualTo("user19");
        assertThat(lines.get(20)).isEqualTo("…and 380 more — click to see all");
    }

    /// One over the cap is shown in full: dropping a name to say "and 1 more"
    /// would cost information to convey none.
    @Test
    void testHoverNamesDoesNotTruncateForASingleExtra() {
        final List<String> names = new ArrayList<>();
        for (int i = 0; i < 21; i++) {
            names.add("user" + i);
        }

        assertThat(FloorMapClusterLabel.hoverNames(names, 20))
                .hasSize(21)
                .doesNotContain("…and 1 more — click to see all");
    }

    /// Two over the cap does truncate, and reports both.
    @Test
    void testHoverNamesTruncatesForTwoExtra() {
        final List<String> names = new ArrayList<>();
        for (int i = 0; i < 22; i++) {
            names.add("user" + i);
        }

        assertThat(FloorMapClusterLabel.hoverNames(names, 20))
                .hasSize(21)
                .endsWith("…and 2 more — click to see all");
    }

    /// Degenerate inputs are safe.
    @Test
    void testHoverNamesEdgeCases() {
        assertThat(FloorMapClusterLabel.hoverNames(null, 20)).isEmpty();
        assertThat(FloorMapClusterLabel.hoverNames(Collections.emptyList(), 20)).isEmpty();
        // A cap below one is treated as one rather than producing an empty list
        // with a summary line, which would name nothing at all.
        assertThat(FloorMapClusterLabel.hoverNames(
                Arrays.asList("a", "b", "c", "d"), 0))
                .containsExactly("a", "…and 3 more — click to see all");
    }

    /// The ordinary case, and the one most type names fall into.
    @Test
    void testRegularPlurals() {
        assertThat(FloorMapClusterLabel.plural("user")).isEqualTo("users");
        assertThat(FloorMapClusterLabel.plural("device")).isEqualTo("devices");
        assertThat(FloorMapClusterLabel.plural("laptop")).isEqualTo("laptops");
        assertThat(FloorMapClusterLabel.plural("desk")).isEqualTo("desks");
    }
}
