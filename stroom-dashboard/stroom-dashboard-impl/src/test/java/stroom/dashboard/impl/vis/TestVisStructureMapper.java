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

package stroom.dashboard.impl.vis;

import stroom.dashboard.impl.vis.VisStructureMapper.VisTable;
import stroom.query.api.Column;
import stroom.query.api.Format;
import stroom.query.api.Sort;
import stroom.query.api.Sort.SortDirection;
import stroom.util.json.JsonUtil;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestVisStructureMapper {

    // A bar chart's settings: a series level, then x and y values, with typed inputs for x and y
    private static final String SETTINGS = """
            {
              "inputs": [
                {"id": "x", "label": "X axis", "types": ["TEXT", "NUMBER", "DATE_TIME"], "required": true},
                {"id": "y", "label": "Y axis", "types": ["NUMBER"], "required": true}
              ],
              "tabs": [{"name": "Data", "controls": [
                {"id": "x", "type": "field", "label": "X"},
                {"id": "y", "type": "field", "label": "Y"},
                {"id": "series", "type": "field", "label": "Series"},
                {"id": "sortSeries", "type": "selection", "defaultValue": "Ascending"},
                {"id": "maxValues", "type": "number", "defaultValue": "500"},
                {"id": "maxSeries", "type": "number", "defaultValue": "10"}
              ]}],
              "data": {"structure": {
                "nest": {
                  "key": {"id": "${series}",
                          "sort": {"enabled": "true", "direction": "${sortSeries}", "priority": 0}},
                  "values": {
                    "fields": [{"id": "${x}"}, {"id": "${y}"}],
                    "limit": {"enabled": "true", "size": "${maxValues}"}
                  },
                  "limit": {"enabled": "true", "size": "${maxSeries}"}
                }
              }}
            }
            """;

    @Test
    void testMapsLevelsAndValues() {
        final VisTable table = VisStructureMapper.map(settings(SETTINGS),
                Map.of("x", "Host", "y", "Count", "series", "Status", "maxSeries", "4"),
                List.of(column("Host", Format.TEXT), column("Count", Format.NUMBER), column("Status", Format.TEXT)));

        assertThat(table.columns()).extracting(Column::getExpression)
                .containsExactly("${Status}", "${Host}", "${Count}");
        // The series level is grouped
        assertThat(table.columns()).extracting(Column::getGroup).containsExactly(0, null, null);
        // Sorted by the default of sortSeries
        assertThat(table.columns().get(0).getSort()).isEqualTo(new Sort(0, SortDirection.ASCENDING));
        // The chosen maxSeries, and the default maxValues
        assertThat(table.maxResults()).containsExactly(4L, 500L);
    }

    @Test
    void testColumnsHaveTheParentTablesFormats() {
        final VisTable table = VisStructureMapper.map(settings(SETTINGS),
                Map.of("x", "Time", "y", "Count", "series", "Status"),
                List.of(column("Time", Format.DATE_TIME), column("Count", Format.NUMBER),
                        column("Status", Format.TEXT)));
        assertThat(formatTypes(table)).containsExactly(Format.Type.TEXT, Format.Type.DATE_TIME, Format.Type.NUMBER);
    }

    @Test
    void testValuesAreConvertedToTheInputsType() {
        // The y axis takes numbers, so a column of text (or of no particular type) is converted
        // to numbers. The x axis takes any type, so a general column is left to be judged by its
        // values
        final VisTable table = VisStructureMapper.map(settings(SETTINGS),
                Map.of("x", "Host", "y", "Count", "series", "Status"),
                List.of(column("Host", Format.GENERAL), column("Count", Format.TEXT), column("Status", Format.TEXT)));
        assertThat(formatTypes(table)).containsExactly(Format.Type.TEXT, Format.Type.GENERAL, Format.Type.NUMBER);

        final VisTable noFormat = VisStructureMapper.map(settings(SETTINGS),
                Map.of("x", "Host", "y", "Count", "series", "Status"),
                List.of(column("Host", null), column("Count", null), column("Status", null)));
        assertThat(formatTypes(noFormat)).containsExactly(Format.Type.GENERAL, Format.Type.GENERAL,
                Format.Type.NUMBER);
    }

    @Test
    void testWithoutInputsColumnsAreAsTheParentTablesAre() {
        final String withoutInputs = SETTINGS.replaceFirst("(?s)\"inputs\": \\[.*?],\\s*\"tabs\"", "\"tabs\"");
        final VisTable table = VisStructureMapper.map(settings(withoutInputs),
                Map.of("x", "Host", "y", "Count", "series", "Status"),
                List.of(column("Host", Format.GENERAL), column("Count", Format.TEXT), column("Status", Format.TEXT)));
        assertThat(formatTypes(table)).containsExactly(Format.Type.TEXT, Format.Type.GENERAL, Format.Type.TEXT);
    }

    @Test
    void testUnsetFieldHasNoExpression() {
        // No series chosen, and the control has no default
        final VisTable table = VisStructureMapper.map(settings(SETTINGS),
                Map.of("x", "Host", "y", "Count"),
                List.of(column("Host", Format.TEXT), column("Count", Format.NUMBER)));
        assertThat(table.columns().get(0).getExpression()).isNull();
        assertThat(table.columns().get(0).getFormat()).isEqualTo(Format.GENERAL);
    }

    @Test
    void testDisabledLimitIsNone() {
        final VisTable table = VisStructureMapper.map(
                settings(SETTINGS.replace("\"limit\": {\"enabled\": \"true\", \"size\": \"${maxSeries}\"}",
                        "\"limit\": {\"enabled\": \"false\", \"size\": \"${maxSeries}\"}")),
                Map.of("x", "Host", "y", "Count", "series", "Status"),
                List.of());
        assertThat(table.maxResults()).containsExactly(null, 500L);
    }

    @Test
    void testNoStructure() {
        assertThat(VisStructureMapper.map(settings("{\"tabs\": []}"), Map.of(), List.of())).isNull();
        assertThat(VisStructureMapper.map(null, Map.of(), List.of())).isNull();
    }

    @Test
    void testFieldWithoutId() {
        assertThatThrownBy(() -> VisStructureMapper.map(
                settings("{\"data\": {\"structure\": {\"values\": {\"fields\": [{\"sort\": null}]}}}}"),
                Map.of(), List.of()))
                .hasMessage("Structure element with missing field name");
    }

    @Test
    void testChosenValuesFromJson() {
        assertThat(VisStructureMapper.chosenValuesFromJson("{\"x\": \"Host\", \"maxValues\": \"10\"}"))
                .isEqualTo(Map.of("x", "Host", "maxValues", "10"));
        assertThat(VisStructureMapper.chosenValuesFromJson("")).isEmpty();
        assertThat(VisStructureMapper.chosenValuesFromJson(null)).isEmpty();
    }

    private static VisSettings settings(final String json) {
        return JsonUtil.readValue(json, VisSettings.class);
    }

    private static Column column(final String name, final Format format) {
        return Column.builder().name(name).format(format).build();
    }

    private static List<Format.Type> formatTypes(final VisTable table) {
        return Arrays.asList(table.columns()
                .stream()
                .map(column -> column.getFormat().getType())
                .toArray(Format.Type[]::new));
    }
}
