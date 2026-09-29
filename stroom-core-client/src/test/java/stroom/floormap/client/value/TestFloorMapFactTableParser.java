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

package stroom.floormap.client.value;

import stroom.floormap.client.model.Fact;
import stroom.floormap.shared.FloorMapFieldMapping.Role;
import stroom.query.api.Column;
import stroom.query.api.Row;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/// Covers the Map tab's ingest path, which until this class was extracted from
/// `FloorMapMapPresenter` could not be tested at all — it was a private method on a GWT
/// presenter, so the largest single body of parsing logic in the feature had no coverage while
/// its structured-value sibling [FloorMapEntryParser] had a full suite.
class TestFloorMapFactTableParser {

    private List<String> warnings;

    @BeforeEach
    void setUp() {
        warnings = new ArrayList<>();
    }

    // -----------------------------------------------------------------------
    // Column matching
    // -----------------------------------------------------------------------

    /// Roles are matched to columns by the alias the query builder produced, ignoring case.
    @Test
    void testColumnsAreMatchedByAliasIgnoringCase() {
        final List<Fact> facts = parse(
                cols("key", "TYPE-COL", "pos-col"),
                List.of(row("gate-1", "gate", "[10, 20]")),
                aliases(Role.TYPE, "type-col", Role.POSITION, "pos-col"));

        assertThat(facts).hasSize(1);
        //noinspection SequencedCollectionMethodCanBeUsed
        final Fact fact = facts.get(0);
        assertThat(fact.getKey()).isEqualTo("gate-1");
        assertThat(fact.getType()).isEqualTo("gate");
        assertThat(fact.getPosition()).containsExactly(10.0, 20.0);
    }

    /// A role with no alias — the pre-area schemas have no geometry or opacity — goes
    /// unmatched rather than throwing, and the fact is still built.
    @Test
    void testUnmappedRolesAreNotAnError() {
        final List<Fact> facts = parse(
                cols("Key", "type"),
                List.of(row("gate-1", "gate")),
                aliases(Role.TYPE, "type"));

        assertThat(facts).hasSize(1);
        //noinspection SequencedCollectionMethodCanBeUsed
        assertThat(facts.get(0).hasVertices()).isFalse();
        assertThat(warnings).isEmpty();
    }

    /// A column the schema does not name is ignored.
    @Test
    void testUnknownColumnsAreIgnored() {
        final List<Fact> facts = parse(
                cols("Key", "type", "some-other-column"),
                List.of(row("gate-1", "gate", "noise")),
                aliases(Role.TYPE, "type"));

        assertThat(facts).hasSize(1);
    }

    /// A row shorter than the column list does not throw.
    @Test
    void testShortRowsAreTolerated() {
        final List<Fact> facts = parse(
                cols("Key", "type", "pos"),
                List.of(row("gate-1")),
                aliases(Role.TYPE, "type", Role.POSITION, "pos"));

        assertThat(facts).hasSize(1);
        //noinspection SequencedCollectionMethodCanBeUsed
        assertThat(facts.get(0).getPosition()).containsExactly(0.0, 0.0);
    }

    /// Null columns or rows yield an empty list rather than an exception.
    @Test
    void testNullInputs() {
        assertThat(parse(null, List.of(), aliases())).isEmpty();
        assertThat(parse(cols("Key"), null, aliases())).isEmpty();
    }

    // -----------------------------------------------------------------------
    // Collapsing shards
    // -----------------------------------------------------------------------

    /// The query returns every effective-time shard in ascending order, so the last row for a
    /// key wins — the canvas shows one current instance per object, not every version at once.
    @Test
    void testLaterShardOverwritesEarlier() {
        final List<Fact> facts = parse(
                cols("Key", "pos"),
                List.of(row("gate-1", "[1, 1]"),
                        row("gate-1", "[9, 9]")),
                aliases(Role.POSITION, "pos"));

        assertThat(facts).hasSize(1);
        //noinspection SequencedCollectionMethodCanBeUsed
        assertThat(facts.get(0).getPosition()).containsExactly(9.0, 9.0);
    }

    /// Distinct keys are all kept, in the order first seen.
    @Test
    void testDistinctKeysArePreservedInOrder() {
        final List<Fact> facts = parse(
                cols("Key"),
                List.of(row("b"), row("a"), row("b")),
                aliases());

        assertThat(facts).extracting(Fact::getKey).containsExactly("b", "a");
    }

    // -----------------------------------------------------------------------
    // Value parsing
    // -----------------------------------------------------------------------

    /// Brackets and quotes are optional decoration around the numbers.
    @Test
    void testCoordsAcceptBracketsAndQuotes() {
        assertThat(FloorMapFactTableParser.parseCoords("[1.5, 2.5]", null))
                .containsExactly(1.5, 2.5);
        assertThat(FloorMapFactTableParser.parseCoords("\"1.5\", \"2.5\"", null))
                .containsExactly(1.5, 2.5);
        assertThat(FloorMapFactTableParser.parseCoords("1.5,2.5", null))
                .containsExactly(1.5, 2.5);
    }

    /// Values beyond the two needed are ignored.
    ///
    /// Deliberately unlike [XmlValueText#parseCommaSeparatedNumbers(String)], which is
    /// all-or-nothing. Tightening this would stop placing objects that place correctly today,
    /// so the leniency is pinned here on purpose rather than left to chance.
    @Test
    void testCoordsIgnoreTrailingValues() {
        assertThat(FloorMapFactTableParser.parseCoords("[1, 2, 99, junk]", null))
                .containsExactly(1.0, 2.0);
    }

    /// An unparseable leading value fails the whole coordinate, with a warning.
    @Test
    void testCoordsRejectUnparseableLeadingValue() {
        assertThat(FloorMapFactTableParser.parseCoords("[abc, 2]", warnings::add)).isNull();
        assertThat(warnings).hasSize(1);
        //noinspection SequencedCollectionMethodCanBeUsed
        assertThat(warnings.get(0)).contains("coordinates");
    }

    /// Too few values is a failure, not a partial result.
    @Test
    void testCoordsRejectSingleValue() {
        assertThat(FloorMapFactTableParser.parseCoords("[1]", warnings::add)).isNull();
        assertThat(warnings).hasSize(1);
    }

    /// Blank and null are absence, not failure — no warning.
    @Test
    void testBlankValuesAreAbsenceNotFailure() {
        assertThat(FloorMapFactTableParser.parseCoords(null, warnings::add)).isNull();
        assertThat(FloorMapFactTableParser.parseCoords("   ", warnings::add)).isNull();
        assertThat(FloorMapFactTableParser.parseVertices(null, warnings::add)).isNull();
        assertThat(FloorMapFactTableParser.parseNullableDouble("  ")).isNull();
        assertThat(warnings).isEmpty();
    }

    /// A polygon needs three vertex pairs; a trailing odd value is ignored.
    @Test
    void testVertices() {
        assertThat(FloorMapFactTableParser.parseVertices("[0,0, 1,0, 1,1]", null))
                .hasDimensions(3, 2);
        // Trailing odd value ignored, matching FloorMapEntryParser.
        assertThat(FloorMapFactTableParser.parseVertices("[0,0, 1,0, 1,1, 5]", null))
                .hasDimensions(3, 2);
        // Two pairs is not a polygon.
        assertThat(FloorMapFactTableParser.parseVertices("[0,0, 1,1]", null)).isNull();
    }

    /// A bad vertex fails the whole polygon, with a warning — never a partial outline.
    @Test
    void testVerticesRejectUnparseableValue() {
        assertThat(FloorMapFactTableParser.parseVertices("[0,0, 1,x, 1,1]", warnings::add))
                .isNull();
        assertThat(warnings).hasSize(1);
        //noinspection SequencedCollectionMethodCanBeUsed
        assertThat(warnings.get(0)).contains("geometry");
    }

    /// A matrix falls back to identity rather than null, so a fact is always placeable.
    @Test
    void testMatrixFallsBackToIdentity() {
        assertThat(FloorMapFactTableParser.parseMatrix("[1,0,0,1,5,6]", null).getE())
                .isEqualTo(5.0);

        // Absent: identity, no warning.
        assertThat(FloorMapFactTableParser.parseMatrix(null, warnings::add).hasInverse())
                .isTrue();
        assertThat(warnings).isEmpty();

        // Too few components: identity, with a warning.
        assertThat(FloorMapFactTableParser.parseMatrix("[1,0,0]", warnings::add).hasInverse())
                .isTrue();
        assertThat(warnings).hasSize(1);

        // Unparseable: identity, with a warning.
        assertThat(FloorMapFactTableParser.parseMatrix("[1,0,0,1,x,6]", warnings::add)
                .hasInverse()).isTrue();
        assertThat(warnings).hasSize(2);
    }

    /// Opacity is optional and silently absent when unparseable — it is presentation only.
    @Test
    void testNullableDouble() {
        assertThat(FloorMapFactTableParser.parseNullableDouble("0.5")).isEqualTo(0.5);
        assertThat(FloorMapFactTableParser.parseNullableDouble("abc")).isNull();
        assertThat(FloorMapFactTableParser.parseNullableDouble(null)).isNull();
    }

    /// A full row exercises every role at once.
    @Test
    void testFullRow() {
        final List<Fact> facts = parse(
                cols("Key", "type", "pos", "img", "w2m", "geom", "fill", "opacity", "label"),
                List.of(row("area-1", "area", "[3, 4]", "asset://plan.png",
                        "[1,0,0,1,7,8]", "[0,0, 1,0, 1,1]", "#ff0000", "0.25", "Lobby")),
                aliases(Role.TYPE, "type",
                        Role.POSITION, "pos",
                        Role.IMAGE, "img",
                        Role.WORLD_TO_MAP, "w2m",
                        Role.GEOMETRY, "geom",
                        Role.FILL, "fill",
                        Role.OPACITY, "opacity",
                        Role.LABEL, "label"));

        assertThat(facts).hasSize(1);
        //noinspection SequencedCollectionMethodCanBeUsed
        final Fact fact = facts.get(0);
        assertThat(fact.getKey()).isEqualTo("area-1");
        assertThat(fact.getType()).isEqualTo("area");
        assertThat(fact.getImage()).isEqualTo("asset://plan.png");
        assertThat(fact.getPosition()).containsExactly(3.0, 4.0);
        assertThat(fact.getWorldToMap().getE()).isEqualTo(7.0);
        assertThat(fact.getVertices()).hasDimensions(3, 2);
        assertThat(fact.getFill()).isEqualTo("#ff0000");
        assertThat(fact.getOpacity()).isEqualTo(0.25);
        assertThat(fact.getLabel()).isEqualTo("Lobby");
        assertThat(warnings).isEmpty();
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private List<Fact> parse(final List<Column> columns,
                             final List<Row> rows,
                             final Map<Role, String> aliasByRole) {
        return FloorMapFactTableParser.parse(columns, rows, aliasByRole, warnings::add);
    }

    private static List<Column> cols(final String... names) {
        final List<Column> columns = new ArrayList<>(names.length);
        for (final String name : names) {
            columns.add(Column.builder().id(name).name(name).build());
        }
        return columns;
    }

    private static Row row(final String... values) {
        return Row.builder().values(Arrays.asList(values)).build();
    }

    /// Alias map from flat `role, alias, role, alias` pairs.
    private static Map<Role, String> aliases(final Object... pairs) {
        final Map<Role, String> map = new HashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put((Role) pairs[i], (String) pairs[i + 1]);
        }
        return map;
    }
}
