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

import stroom.floormap.client.playback.FloorMapEventsQuery;
import stroom.query.api.Column;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/// The Data tab's default query, and its agreement with the events query it borrows columns from.
///
/// These live off the presenter because `AbstractQueryDataPresenter` calls
/// `GWT.create()` in a static initialiser: referring to any static member of a subclass, even
/// a string constant, initialises that superclass and throws `ExceptionInInitializerError`
/// outside a browser. Holding them apart is what makes them reachable at all.
///
/// What is worth pinning is agreement with the events query. A default that drifts from it is the
/// defect [FloorMapEventsQuery] exists to prevent, and it surfaces as columns that read as
/// empty rather than as anything that looks like a fault.
class TestFloorMapEventStoreDataDefaults {

    private static final String QUERY = FloorMapEventStoreDataDefaults.query("floor_map_events");

    // ------------------------------------------------------------------
    // What the query asks the store for.
    // ------------------------------------------------------------------

    @Test
    void namesTheStoreAndCapsTheRowCount() {
        // Named directly rather than through param('EventStore'): the Data tab runs against the
        // document it belongs to, and AbstractQueryDataPresenter.onRun sends no params at all.
        assertThat(QUERY).startsWith("from \"floor_map_events\" limit 100");
        assertThat(QUERY).doesNotContain("param(");
    }

    @Test
    void neitherReadModeNorAsAtIsSent() {
        // Absent on purpose. With both present this would take the map's point-in-time snapshot,
        // one row per entity; the Data tab wants the rows themselves.
        assertThat(QUERY).doesNotContain("readMode");
        assertThat(QUERY).doesNotContain("asAt");
    }

    @Test
    void keepsThePlanBColumnsAheadOfTheEventColumns() {
        assertThat(QUERY.indexOf(FloorMapEventsQuery.EFFECTIVE_TIME_COLUMN))
                .isLessThan(QUERY.indexOf("Key,"));
        assertThat(QUERY.indexOf("Key,")).isLessThan(QUERY.indexOf("Value,"));
        assertThat(QUERY.indexOf("Value,"))
                .as("Value is third, before the properties pulled out of it")
                .isLessThan(QUERY.indexOf(FloorMapEventsQuery.LOCATION_COLUMN));
    }

    /// The key column keeps Plan B's name rather than the events query's `Entity ID` alias.
    ///
    /// This tab reads the store, and the store's column is `Key`. Aliasing it here would
    /// describe the floor map's use of the data rather than the data.
    @Test
    void keyKeepsItsPlanBName() {
        assertThat(QUERY).contains(", Key,");
        assertThat(QUERY).doesNotContain("Entity ID");
    }

    /// The whole query sits on one line, because the editor cannot hold more than one.
    ///
    /// `QueryDataViewImpl` binds a `g:TextBox`, a single-line `<input>`, so
    /// `setText` silently drops newlines and fuses whatever sat either side of them. A
    /// multi-line default reached the parser as `limit 100select` and failed with a syntax
    /// error naming a column the user could not see a problem at.
    @Test
    void fitsOnOneLineBecauseTheEditorHoldsOnlyOne() {
        assertThat(QUERY).doesNotContain("\n").doesNotContain("\r");
    }

    @Test
    void pullsTheFiveEventPropertiesOutOfTheValue() {
        assertThat(QUERY)
                .contains("jq(Value, '.location') as \"" + FloorMapEventsQuery.LOCATION_COLUMN + "\"")
                .contains("jq(Value, '.locationRef') as \"" + FloorMapEventsQuery.LOCATION_REF_COLUMN + "\"")
                .contains("jq(Value, '.type') as \"" + FloorMapEventsQuery.EVENT_TYPE_COLUMN + "\"")
                .contains("jq(Value, '.status') as \"" + FloorMapEventsQuery.STATUS_COLUMN + "\"")
                .contains("jq(Value, '.message') as \"" + FloorMapEventsQuery.MESSAGE_COLUMN + "\"");
    }

    // ------------------------------------------------------------------
    // Agreement with the events query, which is the thing that can drift.
    // ------------------------------------------------------------------

    /// Every event property this tab shows is aliased exactly as the events query aliases it.
    ///
    /// Both are built from [FloorMapEventsQuery]'s constants, so renaming one moves both.
    /// This asserts the outcome rather than the mechanism, so replacing a constant with literal text
    /// fails here rather than silently later.
    @Test
    void eventColumnAliasesMatchTheEventsQuery() {
        final String eventsQuery = FloorMapEventsQuery.defaultQuery();
        for (final String alias : List.of(
                FloorMapEventsQuery.EFFECTIVE_TIME_COLUMN,
                FloorMapEventsQuery.LOCATION_COLUMN,
                FloorMapEventsQuery.LOCATION_REF_COLUMN,
                FloorMapEventsQuery.EVENT_TYPE_COLUMN,
                FloorMapEventsQuery.STATUS_COLUMN,
                FloorMapEventsQuery.MESSAGE_COLUMN)) {
            assertThat(QUERY).as("Data tab aliases " + alias).contains("\"" + alias + "\"");
            assertThat(eventsQuery).as("events query aliases " + alias).contains("\"" + alias + "\"");
        }
    }

    // ------------------------------------------------------------------
    // The column list, which is the grid shown before the query is first run.
    // ------------------------------------------------------------------

    @Test
    void columnNamesMatchTheAliasesTheQueryProduces() {
        // The headers of the empty grid have to read as the headers of the populated one, or the
        // tab appears to change shape the first time it is run.
        for (final Column column : FloorMapEventStoreDataDefaults.columns()) {
            assertThat(QUERY)
                    .as("query produces a column named " + column.getName())
                    .containsIgnoringWhitespaces(column.getName());
        }
    }

    @Test
    void columnOrderFollowsTheSelectClause() {
        final List<Column> columns = FloorMapEventStoreDataDefaults.columns();
        assertThat(columns).hasSize(8);

        int previous = -1;
        for (final Column column : columns) {
            final int position = QUERY.indexOf(column.getName());
            assertThat(position).as(column.getName() + " appears in the query").isNotNegative();
            assertThat(position).as(column.getName() + " follows the column before it").isGreaterThan(previous);
            previous = position;
        }
    }

    /// The value column carries no `substring`.
    ///
    /// It had one in the Plan B tab until 2026-09-18 and it never took effect - preferences are
    /// matched to query columns by id and carry presentation only, never an expression. Truncating
    /// would be wrong here regardless: it shortens the data, so hovering a cell would show the
    /// truncation rather than the value.
    @Test
    void valueIsNotTruncated() {
        assertThat(QUERY).doesNotContain("substring");
        assertThat(FloorMapEventStoreDataDefaults.columns())
                .noneMatch(column -> column.getExpression().contains("substring"));
    }
}
