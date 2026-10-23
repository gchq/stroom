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

package stroom.gwt.workbench.client.app.vis;

import stroom.gwt.workbench.client.app.rest.JsonValues;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestVisData {

    // As BarChart's: grid series > series > x and y, each sorted ascending by default, with limits
    private static final String BAR_SETTINGS = """
            {"tabs": [{"name": "Data", "controls": [
                {"id": "x", "type": "field"}, {"id": "y", "type": "field"},
                {"id": "series", "type": "field"}, {"id": "gridSeries", "type": "field"},
                {"id": "sortXAxis", "type": "selection", "defaultValue": "Ascending"},
                {"id": "sortSeries", "type": "selection", "defaultValue": "Ascending"},
                {"id": "maxValues", "type": "number", "defaultValue": "500"},
                {"id": "maxSeries", "type": "number", "defaultValue": "100"}]}],
             "data": {"structure": {"nest": {
                "key": {"id": "${gridSeries}"},
                "nest": {
                    "key": {"id": "${series}",
                            "sort": {"direction": "${sortSeries}", "enabled": "true", "priority": 1}},
                    "values": {
                        "fields": [
                            {"id": "${x}", "sort": {"direction": "${sortXAxis}", "enabled": "true", "priority": 2}},
                            {"id": "${y}"}],
                        "limit": {"enabled": "true", "size": "${maxValues}"}},
                    "limit": {"enabled": "true", "size": "${maxSeries}"}}}}}}
            """;

    // As ChordLabels': only values
    private static final String VALUES_ONLY_SETTINGS = """
            {"tabs": [], "data": {"structure": {"values": {"fields": [{"id": "${source}"}, {"id": "${count}"}]}}}}
            """;

    private static final VisTable TABLE = VisTable.builder()
            .text("Host")
            .date("Hour")
            .number("Count")
            .row("web-02", "2026-10-12T01:00:00Z", 5)
            .row("web-01", "2026-10-12T01:00:00Z", 3)
            .row("web-02", "2026-10-12T00:00:00Z", 7)
            .row("web-01", "2026-10-12T00:00:00Z", null)
            .build();

    private static final double HOUR_0 = VisTable.date("2026-10-12T00:00:00Z");
    private static final double HOUR_1 = VisTable.date("2026-10-12T01:00:00Z");

    @Test
    void testNestedAndSorted() {
        final String json = VisData.create(BAR_SETTINGS, Map.of("x", "Hour", "y", "Count", "series", "Host"),
                TABLE);

        // The unset grid series gives one group with no key, of the type GENERAL. The series are
        // sorted by name and each one's values by hour, by the controls' default sorts
        final String web01 = store("\"key\": \"web-01\", ", "DATE_TIME", "\"Ascending\"",
                "[[" + num(HOUR_0) + ", null], [" + num(HOUR_1) + ", 3]]",
                num(HOUR_0) + ", 3", num(HOUR_1) + ", 3", num(HOUR_0 + HOUR_1) + ", 3");
        final String web02 = store("\"key\": \"web-02\", ", "DATE_TIME", "\"Ascending\"",
                "[[" + num(HOUR_0) + ", 7], [" + num(HOUR_1) + ", 5]]",
                num(HOUR_0) + ", 5", num(HOUR_1) + ", 7", num(HOUR_0 + HOUR_1) + ", 12");
        final String grid = store("", "TEXT", "\"Ascending\"", "[" + web01 + ", " + web02 + "]",
                num(HOUR_0) + ", 3", num(HOUR_1) + ", 7", num(2 * (HOUR_0 + HOUR_1)) + ", 15");
        final String root = store("", "GENERAL", null, "[" + grid + "]",
                num(HOUR_0) + ", 3", num(HOUR_1) + ", 7", num(2 * (HOUR_0 + HOUR_1)) + ", 15");
        assertThat(JsonValues.parse(json)).isEqualTo(JsonValues.parse(root));
    }

    @Test
    void testSortDescendingAndLimit() {
        final String json = VisData.create(BAR_SETTINGS, Map.of("x", "Hour", "y", "Count", "series", "Host",
                "sortSeries", "Descending", "sortXAxis", "Descending", "maxSeries", "1", "maxValues", "1"), TABLE);

        // Only web-02, the first series descending, and only its latest hour
        final Object root = JsonValues.parse(json);
        final Object grid = values(root).get(0);
        assertThat(values(grid)).hasSize(1);
        final Object series = values(grid).get(0);
        assertThat(((Map<?, ?>) series).get("key")).isEqualTo("web-02");
        assertThat(JsonValues.toJson(values(series))).isEqualTo("[[" + (long) HOUR_1 + ",5]]");
    }

    @Test
    void testUnsortedKeepsTheTableOrder() {
        final String json = VisData.create(BAR_SETTINGS, Map.of("x", "Hour", "y", "Count", "series", "Host",
                "sortSeries", "", "sortXAxis", ""), TABLE);

        final Object grid = values(JsonValues.parse(json)).get(0);
        assertThat(((Map<?, ?>) values(grid).get(0)).get("key")).isEqualTo("web-02");
        assertThat(((Map<?, ?>) grid).get("keySortDirection")).isNull();
        assertThat(JsonValues.toJson(values(values(grid).get(0))))
                .isEqualTo("[[" + (long) HOUR_1 + ",5],[" + (long) HOUR_0 + ",7]]");
    }

    @Test
    void testValuesOnly() {
        final String json = VisData.create(VALUES_ONLY_SETTINGS, Map.of("source", "Host", "count", "Count"), TABLE);

        // The root holds the rows, and its key type is the first value's
        assertThat(JsonValues.parse(json)).isEqualTo(JsonValues.parse(store("", "TEXT", null,
                "[[\"web-02\", 5], [\"web-01\", 3], [\"web-02\", 7], [\"web-01\", null]]",
                "null, 3", "null, 7", "null, 15")));
    }

    @Test
    void testUnknownColumn() {
        assertThatThrownBy(() -> VisData.create(BAR_SETTINGS, Map.of("x", "Nope"), TABLE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("'${x}' is the column 'Nope', which the table hasn't");
    }

    @Test
    void testNoStructure() {
        assertThatThrownBy(() -> VisData.create("{\"tabs\": []}", Map.of(), TABLE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no data.structure");
    }

    // A store's JSON, with x (or the first value) of the type DATE_TIME and y NUMBER, as BarChart's
    private static String store(final String key,
                                final String keyType,
                                final String keySortDirection,
                                final String values,
                                final String min,
                                final String max,
                                final String sum) {
        final boolean barChart = !keyType.equals("TEXT") || values.startsWith("[{");
        return "{" + key + "\"keyType\": \"" + keyType + "\", "
               + (keySortDirection == null
                ? ""
                : "\"keySortDirection\": " + keySortDirection + ", ")
               + "\"values\": " + values + ", "
               + (barChart
                ? "\"types\": [\"DATE_TIME\", \"NUMBER\"], \"sortDirections\": [\"Ascending\", null], "
                : "\"types\": [\"TEXT\", \"NUMBER\"], \"sortDirections\": [null, null], ")
               + "\"min\": [" + min + "], \"max\": [" + max + "], \"sum\": [" + sum + "]}";
    }

    private static String num(final double value) {
        return String.valueOf((long) value);
    }

    private static java.util.List<?> values(final Object store) {
        return (java.util.List<?>) ((Map<?, ?>) store).get("values");
    }
}
