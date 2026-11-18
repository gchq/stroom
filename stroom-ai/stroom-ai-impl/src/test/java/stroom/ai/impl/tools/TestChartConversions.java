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

package stroom.ai.impl.tools;

import stroom.ai.impl.DashboardComponents;
import stroom.ai.impl.DashboardComponents.Component;
import stroom.ai.impl.tools.ChartConversions.Conversion;
import stroom.ai.impl.tools.ChartConversions.SelectionFilter;
import stroom.ai.impl.tools.ChartConversions.Suggestion;
import stroom.ai.impl.vis.ChartCheck.Sample;
import stroom.ai.impl.vis.ChartCheck.TableColumn;
import stroom.ai.impl.vis.ChartKind;
import stroom.docref.DocRef;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/// Converting a chart Stroom AI generated to another kind: its filters follow it, what it loses is said, and a
/// conversion the table can't make gets the kinds it can.
class TestChartConversions {

    private static final DocRef VISUALISATION = new DocRef("Visualisation", "vis-uuid", "Chats");
    private static final List<TableColumn> COLUMNS = List.of(
            new TableColumn("Day", "DATE_TIME", "floorDay(${EventTime})", 0),
            new TableColumn("User", "TEXT", "${User}", 1),
            new TableColumn("Host", "TEXT", "${Host}", 1),
            new TableColumn("Count", "NUMBER", "count()", null));
    private static final Sample SAMPLE = new Sample(List.of("Day", "User", "Host", "Count"), List.of(
            List.of("2026-10-01T00:00:00.000Z", "alice", "web-1", "3"),
            List.of("2026-10-01T00:00:00.000Z", "bob", "web-2", "2"),
            List.of("2026-10-02T00:00:00.000Z", "alice", "web-1", "5")));
    // A table without a time to chart.
    private static final List<TableColumn> NO_DATES = List.of(
            new TableColumn("User", "TEXT", "${User}", 0),
            new TableColumn("Host", "TEXT", "${Host}", 0),
            new TableColumn("Count", "NUMBER", "count()", null));
    private static final Sample NO_DATES_SAMPLE = new Sample(List.of("User", "Host", "Count"), List.of(
            List.of("alice", "web-1", "3"),
            List.of("bob", "web-2", "2")));

    @Test
    void theFiltersAChartsClicksDriveAreFoundWithTheKeysTheyUse() {
        final String text = dashboard(
                "Query selection filters: [id f1] AND {User = ${component.vis-1.selection.series}}; [id f2] AND "
                + "{Day >= ${component.vis-1.selection.from}, Day <= ${component.vis-1.selection.to}}",
                "Table selection filters: [id f3] AND {Host = ${component.vis-2.selection.x}} (disabled)");

        assertThat(ChartConversions.filtersOn(text, "vis-1")).containsExactly(
                new SelectionFilter("Query", "f1", Set.of("series")),
                new SelectionFilter("Query", "f2", Set.of("from", "to")));
        assertThat(ChartConversions.filtersOn(text, "vis-2")).containsExactly(
                new SelectionFilter("Events", "f3", Set.of("x")));
        assertThat(ChartConversions.filtersOn(text, "vis-3")).isEmpty();
        assertThat(ChartConversions.filtersOn(null, "vis-1")).isEmpty();
    }

    @Test
    void thePanesOwnSettingsOverrideItsVisualisations() {
        final Component pane = pane(mapOf("x", "Host", "smooth", ""));

        final Component chart = ChartConversions.drawnWith(pane, mapOf("kind", "line", "x", "Day", "y", "Count",
                "smooth", "true"));

        assertThat(ChartConversions.kindOf(chart)).contains(ChartKind.LINE);
        // The pane's x; its blank smooth leaves the Visualisation's.
        assertThat(ChartConversions.mappingsOf(chart)).containsExactlyEntriesOf(mapOf("x", "Host", "y", "Count"));
        assertThat(chart.settings()).containsEntry("smooth", "true");
        assertThat(ChartConversions.mappingsOf(pane(Map.of()))).isEmpty();
    }

    @Test
    void aLineChartConvertedToAStackedBarChartTakesItsFiltersWithIt() {
        // Seen in testing: 'Chats by User Over Time', a line chart, converted to a stacked bar chart, left its
        // filter on x, which a bar chart doesn't select, so it stopped filtering.
        final String text = dashboard(
                "Query selection filters: [id f1] AND {Day = ${component.vis-1.selection.x}, User = "
                + "${component.vis-1.selection.series}}",
                "");
        final Component line = line();

        final Conversion conversion = ChartConversions.convert(line, ChartKind.BAR,
                mapOf("category", "Day", "value", "Count", "series", "User"), COLUMNS, SAMPLE, text);

        // x is the bar chart's category; series is still series.
        assertThat(conversion.renamed()).containsExactlyEntriesOf(Map.of("x", "category"));
        assertThat(conversion.notes()).containsExactly(
                "Selection filter [id f1] on 'Query' follows the new chart: x is now category.");
        // Its bars of days still take a time range dragged across them.
        assertThat(conversion.warnings()).containsExactly(
                "The line chart's time axis goes: a bar chart draws each time as a bar of its own. A time range is "
                + "still dragged across the bars (its range tool), while they are in time order rather than sorted "
                + "by value.");
    }

    @Test
    void whatTheNewKindCantSelectOrShowIsSaid() {
        final String text = dashboard(
                "Query selection filters: [id f2] AND {Day >= ${component.vis-1.selection.from}, Day <= "
                + "${component.vis-1.selection.to}}; [id f3] AND {User = ${component.vis-1.selection.series}}",
                "");

        final Conversion conversion = ChartConversions.convert(line(), ChartKind.PIE,
                mapOf("name", "User", "value", "Count"), COLUMNS, SAMPLE, text);

        // The series column is the pie's name.
        assertThat(conversion.renamed()).containsExactlyEntriesOf(Map.of("series", "name"));
        assertThat(conversion.warnings()).hasSize(3);
        assertThat(conversion.warnings().get(0))
                .startsWith("Selection filter [id f2] on 'Query' uses the line chart's selection selected time "
                            + "(from and to), which a click on the pie chart doesn't select (it selects: ")
                .contains("name, User")
                .endsWith("so it stops filtering. It is left as it is: offer to remove it "
                          + "(propose_dashboard_change, remove_selection_filter), and to add one on what the new "
                          + "chart selects.");
        assertThat(conversion.warnings().get(1)).isEqualTo("'Day' (the line chart's x) isn't in the pie chart.");
        assertThat(conversion.warnings().get(2)).isEqualTo("The line chart's time axis goes, with its zoom and the "
                                                           + "time range dragged across it.");
    }

    @Test
    void aPieChartAsALineChartOfCategoriesIsntATimeSeries() {
        final Component pie = ChartConversions.drawnWith(pane(Map.of()), mapOf("kind", "pie", "name", "User",
                "value", "Count"));

        final Conversion conversion = ChartConversions.convert(pie, ChartKind.LINE,
                mapOf("x", "User", "y", "Count"), NO_DATES, NO_DATES_SAMPLE, dashboard("", ""));

        assertThat(conversion.warnings()).singleElement().asString()
                .startsWith("x 'User' isn't a Date Time column, so this line chart isn't a time series")
                .contains(ChartConversions.timeColumnHint())
                .endsWith("Or recommend a bar chart of these columns.");
    }

    @Test
    void reconfiguringAChartOfTheSameKindLosesNothing() {
        final Conversion conversion = ChartConversions.convert(line(), ChartKind.LINE,
                mapOf("x", "Day", "y", "Count", "series", "User"), COLUMNS, SAMPLE, dashboard(
                        "Query selection filters: [id f1] AND {Day = ${component.vis-1.selection.x}}", ""));

        assertThat(conversion.renamed()).isEmpty();
        assertThat(conversion.notes()).isEmpty();
        assertThat(conversion.warnings()).isEmpty();
    }

    @Test
    void aNetworksNodeFiltersFollowItToAFlow() {
        final Component graph = ChartConversions.drawnWith(pane(Map.of()), mapOf("kind", "graph", "source", "User",
                "target", "Host", "weight", "Count"));

        final Conversion conversion = ChartConversions.convert(graph, ChartKind.SANKEY,
                mapOf("source", "User", "target", "Host", "value", "Count"), COLUMNS, SAMPLE, dashboard(
                        "Query selection filters: [id f1] OR {User = ${component.vis-1.selection.node}, Host = "
                        + "${component.vis-1.selection.node}}", ""));

        assertThat(conversion.renamed()).isEmpty();
        assertThat(conversion.warnings()).isEmpty();
    }

    @Test
    void aReplacedChartKeepsItsStyleUnlessGivenAnother() {
        // Seen in testing: a network graph weighted by a count lost its colours, its nodes coloured each their own
        // going back to one colour.
        final Component graph = ChartConversions.drawnWith(pane(mapOf("physics", "spread")), mapOf("kind", "graph",
                "source", "User", "target", "Host", "nodeColours", "node", "directed", "true", "showLabels", "auto",
                "smooth", "true"));

        assertThat(ChartConversions.styleKept(graph, ChartKind.GRAPH, Map.of("directed", "false")))
                // As given: directed; a default (showLabels auto): left out; another kind's (smooth): left out.
                .containsExactlyInAnyOrderEntriesOf(Map.of("nodeColours", "node", "physics", "spread"));
        // Another kind keeps what it has of them.
        assertThat(ChartConversions.styleKept(graph, ChartKind.SANKEY, Map.of())).isEmpty();
    }

    @Test
    void whatATableLacksForAKindIsSaid() {
        assertThat(ChartConversions.lacks(ChartKind.CALENDAR, NO_DATES, NO_DATES_SAMPLE)).containsExactly(
                "Date Time column for date (the day, e.g. EventTime rounded with floorDay(), formatted as Date "
                + "Time)");
        assertThat(ChartConversions.lacks(ChartKind.SCATTER, List.of(new TableColumn("User", "TEXT")), null))
                .hasSize(2)
                .allMatch(lack -> lack.startsWith("number or Date Time column for "));
        assertThat(ChartConversions.lacks(ChartKind.BAR, NO_DATES, NO_DATES_SAMPLE)).isEmpty();
        assertThat(ChartConversions.hasDates(NO_DATES, NO_DATES_SAMPLE)).isFalse();
        assertThat(ChartConversions.hasDates(COLUMNS, SAMPLE)).isTrue();
    }

    @Test
    void theKindsATableCanMakeKeepTheOldChartsColumnsFirst() {
        final List<Suggestion> suggestions = ChartConversions.suggestions(NO_DATES, NO_DATES_SAMPLE,
                mapOf("name", "Host", "value", "Count"), Set.of(ChartKind.CALENDAR));

        assertThat(suggestions).hasSizeLessThanOrEqualTo(ChartConversions.MAX_SUGGESTIONS);
        assertThat(suggestions.getFirst().describe()).isEqualTo("pie (name = 'Host', value = 'Count')");
        assertThat(suggestions).extracting(Suggestion::kind)
                // No time to draw over, and nothing over time.
                .doesNotContain(ChartKind.LINE, ChartKind.CALENDAR)
                .contains(ChartKind.BAR);
        assertThat(suggestions.stream().filter(suggestion -> suggestion.kind() == ChartKind.BAR).findFirst()
                .orElseThrow().describe()).isEqualTo("bar (category = 'Host', value = 'Count')");
    }

    @Test
    void aTableWithATimeCanMakeATimeSeries() {
        final List<Suggestion> suggestions = ChartConversions.suggestions(COLUMNS, SAMPLE, Map.of(), Set.of());

        assertThat(suggestions).extracting(Suggestion::describe)
                .contains("line (x = 'Day', y = 'Count')");
    }

    // A line chart of chats per user per day, as its pane and Visualisation draw it.
    private static Component line() {
        return ChartConversions.drawnWith(pane(Map.of()), mapOf("kind", "line", "x", "Day", "y", "Count",
                "series", "User", "smooth", "false"));
    }

    private static Component pane(final Map<String, String> settings) {
        return new Component(DashboardComponents.VISUALISATION, "Chats", "vis-1", null, VISUALISATION, "Events",
                settings);
    }

    // A dashboard with a query and a table of chats, and its filters' lines.
    private static String dashboard(final String queryFilters, final String tableFilters) {
        return "## Query 'Query' (id query-1)\n"
               + queryFilters + "\n"
               + "## Table 'Events' (id table-1)\n"
               + "Columns:\n"
               + "- Day: floorDay(${EventTime}) (format Date Time; group 0)\n"
               + "- User: ${User} (group 1)\n"
               + "- Host: ${Host} (group 1)\n"
               + "- Count: count() (format Number)\n"
               + tableFilters + "\n"
               + "## Visualisation 'Chats' (id vis-1)\n"
               + "Linked table: 'Events'\n";
    }

    private static Map<String, String> mapOf(final String... keysAndValues) {
        final Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i < keysAndValues.length; i += 2) {
            map.put(keysAndValues[i], keysAndValues[i + 1]);
        }
        return map;
    }
}
