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

import stroom.floormap.shared.FloorMapEventColumns;
import stroom.floormap.shared.FloorMapEventRole;
import stroom.query.api.Column;
import stroom.query.api.SpecialColumns;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/// Covers what the Events Query column dropdowns offer.
///
/// Before a run the dropdowns were empty, so no role could be changed. They are now seeded from
/// the mapping's own column names, which is what [FloorMapQueryPresenter#mappedColumnNames] returns.
/// After a run they offered the result's hidden special columns too, such as `__event_id__`;
/// [FloorMapQueryPresenter#selectableColumnNames] leaves those out.
class TestFloorMapQueryPresenter {

    @Test
    void testFullMappingGivesEveryColumnInRoleOrder() {
        final FloorMapEventColumns columns = new FloorMapEventColumns(null)
                .with(FloorMapEventRole.TYPE, "Kind")
                .with(FloorMapEventRole.LOCATION_REF, "Desk")
                .with(FloorMapEventRole.LOCATION, "XY")
                .with(FloorMapEventRole.ENTITY_ID, "UserId");

        final List<String> names = FloorMapQueryPresenter.mappedColumnNames(columns);

        assertThat(names).containsExactly("UserId", "XY", "Desk", "Kind");
    }

    @Test
    void testUnmappedRolesAreSkipped() {
        final FloorMapEventColumns columns = new FloorMapEventColumns(null)
                .with(FloorMapEventRole.ENTITY_ID, "UserId")
                .with(FloorMapEventRole.LOCATION, null)
                .with(FloorMapEventRole.LOCATION_REF, "Desk");

        final List<String> names = FloorMapQueryPresenter.mappedColumnNames(columns);

        assertThat(names).containsExactly("UserId", "Desk");
    }

    @Test
    void testColumnSharedByTwoRolesAppearsOnce() {
        final FloorMapEventColumns columns = new FloorMapEventColumns(null)
                .with(FloorMapEventRole.ENTITY_ID, "UserId")
                .with(FloorMapEventRole.TYPE, "UserId");

        final List<String> names = FloorMapQueryPresenter.mappedColumnNames(columns);

        assertThat(names).containsExactly("UserId");
    }

    @Test
    void testBlankColumnsAreSkipped() {
        final FloorMapEventColumns columns = new FloorMapEventColumns(null)
                .with(FloorMapEventRole.ENTITY_ID, "UserId")
                .with(FloorMapEventRole.LOCATION, "")
                .with(FloorMapEventRole.TYPE, "   ");

        final List<String> names = FloorMapQueryPresenter.mappedColumnNames(columns);

        assertThat(names).containsExactly("UserId");
    }

    @Test
    void testDefaultsGiveEachRolesDefaultColumn() {
        final List<String> names = FloorMapQueryPresenter.mappedColumnNames(FloorMapEventColumns.defaults());

        assertThat(names).containsExactly(
                FloorMapEventRole.ENTITY_ID.getDefaultColumn(),
                FloorMapEventRole.LOCATION.getDefaultColumn(),
                FloorMapEventRole.LOCATION_REF.getDefaultColumn(),
                FloorMapEventRole.TYPE.getDefaultColumn());
    }

    @Test
    void testEmptyMappingGivesEmptyList() {
        assertThat(FloorMapQueryPresenter.mappedColumnNames(new FloorMapEventColumns(null)))
                .isNotNull()
                .isEmpty();
    }

    @Test
    void testNullMappingGivesEmptyList() {
        assertThat(FloorMapQueryPresenter.mappedColumnNames(null))
                .isNotNull()
                .isEmpty();
    }

    @Test
    void testSpecialColumnsAreNotSelectable() {
        final List<Column> columns = new ArrayList<>();
        columns.add(column("UserId"));
        columns.add(SpecialColumns.RESERVED_STREAM_ID_COLUMN);
        columns.add(column("Desk"));
        columns.add(SpecialColumns.RESERVED_EVENT_ID_COLUMN);
        columns.add(SpecialColumns.RESERVED_ANNOTATION_ID_COLUMN);

        final List<String> names = FloorMapQueryPresenter.selectableColumnNames(columns);

        assertThat(names).containsExactly("UserId", "Desk");
    }

    @Test
    void testOrdinaryColumnsKeepResultOrder() {
        final List<Column> columns = new ArrayList<>();
        columns.add(column("Type"));
        columns.add(column("UserId"));
        columns.add(Column.builder().id("Desk").name("Desk").special(false).build());

        final List<String> names = FloorMapQueryPresenter.selectableColumnNames(columns);

        assertThat(names).containsExactly("Type", "UserId", "Desk");
    }

    @Test
    void testOnlySpecialColumnsGivesEmptyList() {
        final List<Column> columns = new ArrayList<>();
        columns.add(SpecialColumns.RESERVED_STREAM_ID_COLUMN);
        columns.add(SpecialColumns.RESERVED_EVENT_ID_COLUMN);

        assertThat(FloorMapQueryPresenter.selectableColumnNames(columns)).isEmpty();
    }

    @Test
    void testNullColumnsGiveEmptyList() {
        final List<Column> columns = new ArrayList<>();
        columns.add(null);

        assertThat(FloorMapQueryPresenter.selectableColumnNames(columns)).isEmpty();
        assertThat(FloorMapQueryPresenter.selectableColumnNames(null))
                .isNotNull()
                .isEmpty();
    }

    private static Column column(final String name) {
        return Column.builder().id(name).name(name).build();
    }
}
