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

package stroom.floormap.client.presenter;

import stroom.floormap.client.model.Fact;
import stroom.floormap.client.model.FloorMapLocationResolver;
import stroom.floormap.client.model.FloorMapObject;
import stroom.floormap.client.playback.FloorMapEventsQuery;
import stroom.floormap.shared.FloorMapEventColumns;
import stroom.floormap.shared.FloorMapEventRole;
import stroom.floormap.shared.FloorMapTransformationMatrix;
import stroom.query.api.Column;
import stroom.query.api.Row;
import stroom.query.api.TableResult;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/// Covers the whole path a single events row travels — query result → parsed
/// entity → position on the map — because the two halves fail identically from
/// the outside: the entities just stop appearing.
class TestFloorMapEventRowParsing {

    private static final String ENTITY_COLUMN = FloorMapEventsQuery.ENTITY_ID_COLUMN;
    private static final String LOCATION_COLUMN = FloorMapEventsQuery.LOCATION_COLUMN;
    private static final String REF_COLUMN = FloorMapEventsQuery.LOCATION_REF_COLUMN;
    private static final String TIME_COLUMN = FloorMapEventsQuery.EFFECTIVE_TIME_COLUMN;

    /// The mapping a new document gets, which is what the default query aliases.
    private static final FloorMapEventColumns DEFAULTS = FloorMapEventColumns.defaults();

    /// The default query must alias exactly the columns a new document is told to read.
    ///
    /// Both the query text and the default mapping are generated from
    /// [FloorMapEventRole], so they agree by construction. This asserts the construction
    /// actually holds — and that the surrounding StroomQL still quotes each name as a column alias
    /// rather than, say, interpolating it somewhere harmless. The `Event Type` defect was
    /// exactly this pairing coming apart.
    @Test
    void testDefaultQueryAliasesEveryRolesDefaultColumn() {
        final String query = FloorMapEventsQuery.defaultQuery();

        for (final FloorMapEventRole role : FloorMapEventRole.values()) {
            assertThat(query)
                    .as("default query aliases " + role)
                    .contains("as \"" + role.getDefaultColumn() + "\"");
            assertThat(DEFAULTS.getColumn(role))
                    .as("default mapping names " + role + "'s own default")
                    .isEqualTo(role.getDefaultColumn());
        }

        // And the parse agrees, given a result shaped the way that query describes.
        assertThat(FloorMapQueryPresenter.parseRows(
                result(row("joe.blogs@example.org", "1, 2", null)), DEFAULTS, null))
                .hasSize(1);
    }

    // -----------------------------------------------------------------------
    // latestPerEntity - reducing a query window to one row per entity
    // -----------------------------------------------------------------------

    /// ISO-8601 times, the form Stroom emits with no pattern preference set.
    @Test
    void testLatestPerEntityKeepsTheNewestIsoTimeForEachEntity() {
        final List<Row> reduced = FloorMapQueryPresenter.latestPerEntity(
                timedColumns(),
                List.of(
                        timedRow("2026-09-01T10:00:00.000Z", "a@x.org", "1, 1"),
                        timedRow("2026-09-01T10:00:05.000Z", "a@x.org", "2, 2"),
                        timedRow("2026-09-01T10:00:03.000Z", "b@x.org", "9, 9")),
                ENTITY_COLUMN,
                TIME_COLUMN);

        assertThat(reduced).hasSize(2);
        // a@x.org keeps the 10:00:05 position, not the 10:00:00 one.
        assertThat(reduced.get(0).getValues().get(2)).isEqualTo("2, 2");
        assertThat(reduced.get(1).getValues().get(2)).isEqualTo("9, 9");
    }

    /// Out-of-order rows must not fool it - the newest wins wherever it sits in the result.
    @Test
    void testLatestPerEntityIgnoresRowOrderWhenTimesAreComparable() {
        final List<Row> reduced = FloorMapQueryPresenter.latestPerEntity(
                timedColumns(),
                List.of(
                        timedRow("2026-09-01T10:00:09.000Z", "a@x.org", "newest"),
                        timedRow("2026-09-01T10:00:01.000Z", "a@x.org", "oldest")),
                ENTITY_COLUMN,
                TIME_COLUMN);

        assertThat(reduced).hasSize(1);
        assertThat(reduced.getFirst().getValues().get(2)).isEqualTo("newest");
    }

    /// Epoch milliseconds must compare numerically, not as text - "9" is not after "10".
    @Test
    void testLatestPerEntityComparesEpochMillisNumerically() {
        final List<Row> reduced = FloorMapQueryPresenter.latestPerEntity(
                timedColumns(),
                List.of(
                        timedRow("9", "a@x.org", "earlier"),
                        timedRow("10", "a@x.org", "later")),
                ENTITY_COLUMN,
                TIME_COLUMN);

        assertThat(reduced).hasSize(1);
        // As text "9" sorts above "10"; only a numeric compare gets this right.
        assertThat(reduced.getFirst().getValues().get(2)).isEqualTo("later");
    }

    /// With no time column the last row for an entity wins, deterministically.
    @Test
    void testLatestPerEntityFallsBackToLastRowWinsWithNoTimeColumn() {
        final List<Column> columns = new ArrayList<>();
        columns.add(Column.builder().id(ENTITY_COLUMN).name(ENTITY_COLUMN).build());
        columns.add(Column.builder().id(LOCATION_COLUMN).name(LOCATION_COLUMN).build());

        final List<Row> reduced = FloorMapQueryPresenter.latestPerEntity(
                columns,
                List.of(twoColumnRow("a@x.org", "first"), twoColumnRow("a@x.org", "last")),
                ENTITY_COLUMN,
                TIME_COLUMN);

        assertThat(reduced).hasSize(1);
        assertThat(reduced.getFirst().getValues().get(1)).isEqualTo("last");
    }

    /// A store that already deduplicates server-side must pass through untouched.
    ///
    /// SqlTemporalStore returns one row per key, so this pass has to be a no-op there rather
    /// than quietly dropping or reordering anything.
    @Test
    void testLatestPerEntityIsANoOpOnAlreadyUniqueRows() {
        final List<Row> rows = List.of(
                timedRow("2026-09-01T10:00:00.000Z", "a@x.org", "one"),
                timedRow("2026-09-01T10:00:00.000Z", "b@x.org", "two"),
                timedRow("2026-09-01T10:00:00.000Z", "c@x.org", "three"));

        final List<Row> reduced = FloorMapQueryPresenter.latestPerEntity(
                timedColumns(), rows, ENTITY_COLUMN, TIME_COLUMN);

        assertThat(reduced).containsExactlyElementsOf(rows);
    }

    /// Columns for the three-column shape the default events query produces.
    private static List<Column> timedColumns() {
        final List<Column> columns = new ArrayList<>();
        columns.add(Column.builder().id(TIME_COLUMN).name(TIME_COLUMN).build());
        columns.add(Column.builder().id(ENTITY_COLUMN).name(ENTITY_COLUMN).build());
        columns.add(Column.builder().id(LOCATION_COLUMN).name(LOCATION_COLUMN).build());
        return columns;
    }

    private static Row timedRow(final String time, final String entity, final String location) {
        return Row.builder().values(Arrays.asList(time, entity, location)).build();
    }

    /// Coordinates baked into the event, used as they stand.
    @Test
    void testCoordinateRowsAreParsedAndPassedThrough() {
        final List<FloorMapObject> parsed = FloorMapQueryPresenter.parseRows(
                result(row("joe.blogs@example.org", "120.5, 340", null)), DEFAULTS, null);

        assertThat(parsed).hasSize(1);
        assertThat(parsed.getFirst().getLocationRef()).isNull();
        assertThat(parsed.getFirst().getX()).isEqualTo(120.5);

        final List<FloorMapObject> placed = FloorMapLocationResolver.resolve(parsed, facts());
        assertThat(placed.getFirst().getX()).isEqualTo(120.5);
        assertThat(placed.getFirst().getY()).isEqualTo(340);
    }

    /// The shape that fixes the stale-position bug: the row names the object, and
    /// the entity lands wherever that object currently is.
    @Test
    void testReferenceRowsAreParsedAndPlacedOnTheObject() {
        final List<FloorMapObject> parsed = FloorMapQueryPresenter.parseRows(
                result(row("joe.blogs@example.org", null, "G-MAIN_ENTRANCE")), DEFAULTS, null);

        assertThat(parsed).hasSize(1);
        assertThat(parsed.getFirst().getLocationRef()).isEqualTo("G-MAIN_ENTRANCE");

        final List<FloorMapObject> placed = FloorMapLocationResolver.resolve(parsed, facts());
        assertThat(placed).hasSize(1);
        assertThat(placed.getFirst().getId()).isEqualTo("joe.blogs@example.org");
        assertThat(placed.getFirst().getX()).isEqualTo(10);
        assertThat(placed.getFirst().getY()).isEqualTo(20);
    }

    /// An email entity id with no type column still reads as a person.
    @Test
    void testTypeFallsBackToPersonForAnEmailId() {
        final List<FloorMapObject> parsed = FloorMapQueryPresenter.parseRows(
                result(row("joe.blogs@example.org", null, "G-MAIN_ENTRANCE")), DEFAULTS, null);

        assertThat(parsed.getFirst().getType()).isEqualTo("person");
    }

    /// Both location roles pointing at columns the query does not select yields nothing at all —
    /// the silent failure that looks like animation being switched off, and the reason
    /// `FloorMapMapPresenter` logs this case.
    @Test
    void testBothLocationRolesUnmappedYieldsNoEntities() {
        final FloorMapEventColumns mapping = DEFAULTS
                .with(FloorMapEventRole.LOCATION, "Nope")
                .with(FloorMapEventRole.LOCATION_REF, "Also Nope");

        assertThat(FloorMapQueryPresenter.parseRows(
                result(row("joe.blogs@example.org", null, "G-MAIN_ENTRANCE")), mapping, null))
                .isEmpty();
    }

    /// Either location role on its own is enough.
    ///
    /// A store whose events only ever carry fact keys has no coordinate column at all, and
    /// leaving that role unset must not disable the map.
    @Test
    void testOneLocationRoleIsEnough() {
        final FloorMapEventColumns refOnly = DEFAULTS.with(FloorMapEventRole.LOCATION, null);
        assertThat(FloorMapQueryPresenter.parseRows(
                result(row("joe.blogs@example.org", null, "G-MAIN_ENTRANCE")), refOnly, null))
                .hasSize(1);

        final FloorMapEventColumns coordsOnly = DEFAULTS.with(FloorMapEventRole.LOCATION_REF, null);
        assertThat(FloorMapQueryPresenter.parseRows(
                result(row("joe.blogs@example.org", "1, 2", null)), coordsOnly, null))
                .hasSize(1);
    }

    /// A row with neither is skipped, not placed at the origin.
    @Test
    void testRowWithNoLocationAtAllIsSkipped() {
        assertThat(FloorMapQueryPresenter.parseRows(
                result(row("joe.blogs@example.org", null, null)), DEFAULTS, null))
                .isEmpty();
    }

    /// Both set: coordinates win, and the contradiction is reported.
    ///
    /// Coordinates win because they need no lookup. It is reported because only one of the two
    /// can be true, so a row carrying both is bad data rather than a preference — and reported
    /// **once** per result, since the same rows arrive three times a second during playback.
    @Test
    void testCoordinatesWinOverAReferenceAndTheClashIsReportedOnce() {
        final List<String> warnings = new ArrayList<>();
        final List<FloorMapObject> parsed = FloorMapQueryPresenter.parseRows(
                result(
                        row("a@x.org", "1, 2", "G-MAIN_ENTRANCE"),
                        row("b@x.org", "3, 4", "G-MAIN_ENTRANCE")),
                DEFAULTS,
                warnings::add);

        assertThat(parsed).hasSize(2);
        assertThat(parsed.getFirst().getLocationRef()).isNull();
        assertThat(parsed.getFirst().getX()).isEqualTo(1);
        assertThat(warnings).hasSize(1);
        assertThat(warnings.getFirst()).contains("a@x.org").contains("both");
    }

    /// A malformed coordinate is reported as such, not read as a fact key.
    ///
    /// The single-column scheme could not do this: anything that was not two numbers became a
    /// reference, so a typo in a position was reported as a missing desk.
    @Test
    void testMalformedCoordinatesAreReportedRatherThanTreatedAsAKey() {
        final List<String> warnings = new ArrayList<>();
        final List<FloorMapObject> parsed = FloorMapQueryPresenter.parseRows(
                result(row("a@x.org", "120.5 340", null)), DEFAULTS, warnings::add);

        assertThat(parsed).isEmpty();
        assertThat(warnings).hasSize(1);
        assertThat(warnings.getFirst()).contains("120.5 340");
    }

    /// A fact key that looks like coordinates works, which the single-column scheme made impossible.
    @Test
    void testAReferenceMayLookLikeCoordinates() {
        final List<FloorMapObject> parsed = FloorMapQueryPresenter.parseRows(
                result(row("a@x.org", null, "100, 200")), DEFAULTS, null);

        assertThat(parsed).hasSize(1);
        assertThat(parsed.getFirst().getLocationRef()).isEqualTo("100, 200");
    }

    /// The type column is read from the mapping, not from a column literally named "type".
    @Test
    void testTypeComesFromTheMappedColumn() {
        final List<Column> columns = new ArrayList<>();
        columns.add(Column.builder().id(ENTITY_COLUMN).name(ENTITY_COLUMN).build());
        columns.add(Column.builder().id(REF_COLUMN).name(REF_COLUMN).build());
        columns.add(Column.builder().id("Kind").name("Kind").build());
        final TableResult res = new TableResult(
                "table", columns,
                List.of(Row.builder()
                        .values(Arrays.asList("forklift-7", "G-MAIN_ENTRANCE", "vehicle"))
                        .build()),
                null, 1L, null, null);

        final FloorMapEventColumns mapping = DEFAULTS.with(FloorMapEventRole.TYPE, "Kind");
        assertThat(FloorMapQueryPresenter.parseRows(res, mapping, null).getFirst().getType())
                .isEqualTo("vehicle");
    }

    // -----------------------------------------------------------------------

    /// The facts a floor plan would supply: the gate the events reference.
    private static List<Fact> facts() {
        return Collections.singletonList(new Fact(
                "G-MAIN_ENTRANCE", "gate", null,
                FloorMapTransformationMatrix.identity(), new double[]{10, 20}));
    }

    /// A two-value row, for the latestPerEntity cases that never reach the location split.
    private static Row twoColumnRow(@SuppressWarnings("SameParameterValue") final String entityId,
                                    final String location) {
        return Row.builder().values(Arrays.asList(entityId, location)).build();
    }

    private static Row row(final String entityId,
                           final String location,
                           final String locationRef) {
        return Row.builder()
                .values(Arrays.asList(entityId, location, locationRef))
                .build();
    }

    private static TableResult result(final Row... rows) {
        final List<Column> columns = new ArrayList<>();
        columns.add(Column.builder().id(ENTITY_COLUMN).name(ENTITY_COLUMN).build());
        columns.add(Column.builder().id(LOCATION_COLUMN).name(LOCATION_COLUMN).build());
        columns.add(Column.builder().id(REF_COLUMN).name(REF_COLUMN).build());
        return new TableResult(
                "table", columns, Arrays.asList(rows), null, (long) rows.length, null, null);
    }
}
