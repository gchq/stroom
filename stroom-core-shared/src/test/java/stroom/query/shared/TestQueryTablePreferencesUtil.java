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

package stroom.query.shared;

import stroom.query.api.Column;
import stroom.query.api.ResultRequest;
import stroom.query.api.Sort;
import stroom.query.api.Sort.SortDirection;
import stroom.query.api.TableSettings;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TestQueryTablePreferencesUtil {

    private static final Column STREAM_ID = Column.builder().id("streamid-1").name("StreamId").build();
    private static final Column EVENT_ID = Column.builder().id("eventid-1").name("EventId").build();
    private static final Column USER_ID = Column.builder().id("userid-1").name("UserId").build();

    @Test
    void hidesColumnMarkedAsHiddenInPreferences() {
        final ResultRequest result = applyPreferences(
                List.of(STREAM_ID, EVENT_ID, USER_ID),
                List.of(hidden(USER_ID)));

        assertThat(visibilityByName(result)).containsExactly(
                entry("StreamId", true),
                entry("EventId", true),
                entry("UserId", false));
    }

    @Test
    void leavesColumnsWithoutAPreferenceAlone() {
        final ResultRequest result = applyPreferences(
                List.of(STREAM_ID, EVENT_ID),
                List.of());

        assertThat(visibilityByName(result)).containsExactly(
                entry("StreamId", true),
                entry("EventId", true));
    }

    @Test
    void reShowsAColumnWhenThePreferenceIsChangedBackToVisible() {
        final ResultRequest result = applyPreferences(
                List.of(STREAM_ID, USER_ID),
                List.of(USER_ID.copy().visible(true).build()));

        assertThat(visibilityByName(result)).containsExactly(
                entry("StreamId", true),
                entry("UserId", true));
    }

    /**
     * Editing the query can leave preferences behind that no longer refer to any column. They must not affect the
     * columns that are still there.
     */
    @Test
    void ignoresStalePreferencesThatMatchNoColumn() {
        final ResultRequest result = applyPreferences(
                List.of(STREAM_ID, EVENT_ID),
                List.of(hidden(Column.builder().id("deleted-1").name("Deleted").build())));

        assertThat(visibilityByName(result)).containsExactly(
                entry("StreamId", true),
                entry("EventId", true));
    }

    /**
     * Special columns are never shown to the user, so a stale preference must not be able to make one visible.
     */
    @Test
    void doesNotChangeTheVisibilityOfSpecialColumns() {
        final Column special = Column.builder()
                .id("special-1")
                .name("__special__")
                .special(true)
                .visible(false)
                .build();

        final ResultRequest result = applyPreferences(
                List.of(STREAM_ID, special),
                List.of(special.copy().visible(true).build()));

        assertThat(visibilityByName(result)).containsExactly(
                entry("StreamId", true),
                entry("__special__", false));
    }

    @Test
    void toleratesDuplicateColumnIdsInPreferences() {
        final ResultRequest result = applyPreferences(
                List.of(STREAM_ID, USER_ID),
                List.of(USER_ID.copy().visible(true).build(), hidden(USER_ID)));

        assertThat(visibilityByName(result)).containsExactly(
                entry("StreamId", true),
                entry("UserId", false));
    }

    @Test
    void appliesOtherPreferencesAlongsideVisibility() {
        final Sort sort = new Sort(0, SortDirection.DESCENDING);
        final ResultRequest result = applyPreferences(
                List.of(STREAM_ID, USER_ID),
                List.of(STREAM_ID.copy().sort(sort).width(123).build(), hidden(USER_ID)));

        final List<Column> columns = columns(result);
        assertThat(columns.get(0).getSort()).isEqualTo(sort);
        assertThat(columns.get(0).getWidth()).isEqualTo(123);
        assertThat(columns.get(1).isVisible()).isFalse();
    }

    @Test
    void returnsTheRequestUnchangedWhenThereAreNoPreferences() {
        final ResultRequest resultRequest = resultRequest(List.of(STREAM_ID));
        assertThat(QueryTablePreferencesUtil.applyTablePreferences(resultRequest, null))
                .isSameAs(resultRequest);
    }

    @Test
    void returnsTheRequestUnchangedWhenThereAreNoMappings() {
        final ResultRequest resultRequest = ResultRequest.builder().componentId("table").build();
        assertThat(QueryTablePreferencesUtil.applyTablePreferences(
                resultRequest,
                QueryTablePreferences.builder().columns(List.of(hidden(USER_ID))).build()))
                .isSameAs(resultRequest);
    }

    private static Column hidden(final Column column) {
        return column.copy().visible(false).build();
    }

    private static ResultRequest resultRequest(final List<Column> columns) {
        return ResultRequest
                .builder()
                .componentId("table")
                .addMappings(TableSettings.builder().columns(columns).build())
                .build();
    }

    private static ResultRequest applyPreferences(final List<Column> columns,
                                                  final List<Column> preferredColumns) {
        return QueryTablePreferencesUtil.applyTablePreferences(
                resultRequest(columns),
                QueryTablePreferences.builder().columns(preferredColumns).build());
    }

    private static List<Column> columns(final ResultRequest resultRequest) {
        return resultRequest.getMappings().get(0).getColumns();
    }

    /**
     * @return One "name=visible" entry per column, in column order.
     */
    private static List<String> visibilityByName(final ResultRequest resultRequest) {
        return columns(resultRequest)
                .stream()
                .map(column -> entry(column.getName(), column.isVisible()))
                .toList();
    }

    private static String entry(final String name, final boolean visible) {
        return name + "=" + visible;
    }
}
