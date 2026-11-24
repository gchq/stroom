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

package stroom.ai.impl.tools.timeline;

import stroom.ai.impl.vis.ChartCheck;
import stroom.ai.impl.vis.ChartKind;
import stroom.dashboard.shared.ComponentConfig;
import stroom.dashboard.shared.ComponentSelectionHandler;
import stroom.dashboard.shared.DashboardConfig;
import stroom.dashboard.shared.QueryComponentSettings;
import stroom.dashboard.shared.TableComponentSettings;
import stroom.dashboard.shared.VisComponentSettings;
import stroom.dataconnection.shared.DataConnectionDoc;
import stroom.docref.DocRef;
import stroom.query.api.Column;
import stroom.query.api.ExpressionItem;
import stroom.query.api.ExpressionTerm;
import stroom.query.api.ExpressionTerm.Condition;
import stroom.query.api.Format.Type;
import stroom.query.api.Sort.SortDirection;
import stroom.util.json.JsonUtil;
import stroom.visualisation.shared.VisualisationDoc;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/// The dashboard of a timeline's Data Connection: the chart's columns for each table and its lanes, the query, the
/// chart wired to the table, the table typed and in time order, filtered by a bar's click to its interval.
class TestTimelineDashboardConfig {

    private static final DocRef DATA_CONNECTION = new DocRef(DataConnectionDoc.TYPE, "dc-uuid",
            "Timeline jsmith 2026-11-09 to 2026-11-13 (spans)");
    private static final DocRef VISUALISATION = new DocRef(VisualisationDoc.TYPE, "vis-uuid",
            "Timeline jsmith 2026-11-09 to 2026-11-13 (spans)");
    private static final long FROM = Instant.parse("2026-11-09T00:00:00Z").toEpochMilli();
    private static final long TO = Instant.parse("2026-11-14T00:00:00Z").toEpochMilli();

    @Test
    void theSpansAreLanedByHostOrSourceAndColouredByActivityWhereFound() {
        assertThat(TimelineDashboardConfig.mappings(TimelineDataConnection.SPANS, null, true))
                .isEqualTo(Map.of("start", "start", "end", "end", "label", "label", "lane", "lane",
                        "colour", "activity_label"));
        assertThat(TimelineDashboardConfig.mappings(TimelineDataConnection.SPANS, "source", false))
                .containsEntry("lane", "source").containsEntry("colour", "kind");
    }

    @Test
    void theActivitiesAreLanedByDayOrActivity() {
        assertThat(TimelineDashboardConfig.mappings(TimelineDataConnection.ACTIVITIES, null, true))
                .isEqualTo(Map.of("start", "start", "end", "end", "label", "activity_label", "lane", "day",
                        "colour", "activity_label"));
        assertThat(TimelineDashboardConfig.mappings(TimelineDataConnection.ACTIVITIES, "activity", true))
                .containsEntry("lane", "activity_label");
    }

    @Test
    void theTimelineChartCanBeDrawnFromTheSpansColumns() {
        final Map<String, String> mappings = TimelineDashboardConfig.mappings(TimelineDataConnection.SPANS, null,
                true);
        final List<List<String>> rows = List.of(
                List.of("s1", "2026-11-10T09:00:00Z", "2026-11-10T09:20:00Z", "WINWORD.EXE", "ws-01", "focus",
                        "a1", "Q3 finance proposal", "ws-01", "Endpoint", "4", "focus", ""),
                List.of("p1", "2026-11-10T09:05:00Z", "2026-11-10T09:05:00Z", "locked", "ws-01", "pair", "", "",
                        "ws-01", "Logons", "2", "pair", ""));

        final ChartCheck.Result check = ChartCheck.check(ChartKind.TIMELINE, mappings, Map.of(),
                TimelineDashboardConfig.chartColumns(TimelineDataConnection.SPANS),
                new ChartCheck.Sample(TimelineTables.SPAN_COLUMNS, rows));

        assertThat(check.problems()).isEmpty();
        assertThat(check.stats()).containsEntry("bars", 1).containsEntry("markers", 1).containsEntry("lanes", 1);
        assertThat(TimelineDashboardConfig.chartColumns(TimelineDataConnection.SPANS))
                .filteredOn(column -> column.name().equals("start") || column.name().equals("end"))
                .extracting(ChartCheck.TableColumn::format)
                .containsOnly("DATE_TIME");
    }

    @Test
    void theQueryReadsTheDataConnectionOverThePeriodAndRunsWhenOpened() {
        final DashboardConfig config = build();

        assertThat(config.getTimeRange().getFrom()).isEqualTo("2026-11-09T00:00:00Z");
        assertThat(config.getTimeRange().getTo()).isEqualTo("2026-11-14T00:00:00Z");
        assertThat(config.getComponents()).extracting(ComponentConfig::getType, ComponentConfig::getId)
                .containsExactly(tuple("query", TimelineDashboardConfig.QUERY_ID),
                        tuple("vis", TimelineDashboardConfig.CHART_ID),
                        tuple("table", TimelineDashboardConfig.TABLE_ID));
        final QueryComponentSettings query = (QueryComponentSettings) component(config, "query").getSettings();
        assertThat(query.getDataSource()).isEqualTo(DATA_CONNECTION);
        assertThat(query.getAutomate().isOpen()).isTrue();
    }

    @Test
    void theChartDrawsTheTableWithEachRolesColumn() {
        final VisComponentSettings chart = (VisComponentSettings) component(build(), "vis").getSettings();

        assertThat(chart.getTableId()).isEqualTo(TimelineDashboardConfig.TABLE_ID);
        assertThat(chart.getVisualisation()).isEqualTo(VISUALISATION);
        assertThat(JsonUtil.readValue(chart.getJson(), Map.class)).isEqualTo(Map.of("start", "start",
                "end", "end", "label", "label", "lane", "lane", "colour", "activity_label"));
    }

    @Test
    void theTableIsTypedInTimeOrderAndAClickOnABarFiltersItToTheBarsInterval() {
        final TableComponentSettings table = (TableComponentSettings) component(build(), "table").getSettings();

        assertThat(table.getQueryId()).isEqualTo(TimelineDashboardConfig.QUERY_ID);
        assertThat(table.getDataSourceRef()).isEqualTo(DATA_CONNECTION);
        assertThat(table.extractValues()).isFalse();
        assertThat(table.getColumns()).extracting(Column::getName).isEqualTo(TimelineTables.SPAN_COLUMNS);
        assertThat(table.getColumns()).filteredOn(column -> column.getFormat().getType() != Type.TEXT)
                .extracting(Column::getName, column -> column.getFormat().getType())
                .containsExactly(tuple("start", Type.DATE_TIME), tuple("end", Type.DATE_TIME),
                        tuple("events", Type.NUMBER));
        assertThat(table.getColumns()).filteredOn(column -> column.getSort() != null)
                .singleElement()
                .satisfies(column -> {
                    assertThat(column.getName()).isEqualTo("start");
                    assertThat(column.getSort().getDirection()).isEqualTo(SortDirection.ASCENDING);
                });
        assertThat(table.getColumns().getFirst().getExpression()).isEqualTo("${span}");

        final ComponentSelectionHandler filter = table.getSelectionFilter().getFirst();
        assertThat(filter.getComponentId()).isEqualTo(TimelineDashboardConfig.CHART_ID);
        assertThat(filter.isEnabled()).isTrue();
        final List<ExpressionItem> terms = filter.getExpression().getChildren();
        assertThat(terms).extracting(item -> ((ExpressionTerm) item).getField(),
                        item -> ((ExpressionTerm) item).getCondition(), item -> ((ExpressionTerm) item).getValue())
                .containsExactly(
                        tuple("start", Condition.GREATER_THAN_OR_EQUAL_TO, "${component.vis-1.selection.from}"),
                        tuple("start", Condition.LESS_THAN_OR_EQUAL_TO, "${component.vis-1.selection.to}"));
    }

    @Test
    void theTableShowsEveryRowOfALargeTimeline() {
        final TimelineDashboardPlan plan = plan(150_000);

        final TableComponentSettings table = (TableComponentSettings) component(TimelineDashboardConfig.build(
                DATA_CONNECTION, VISUALISATION, plan), "table").getSettings();

        assertThat(table.getMaxResults()).containsExactly(150_000L);
    }

    private static DashboardConfig build() {
        return TimelineDashboardConfig.build(DATA_CONNECTION, VISUALISATION, plan(2));
    }

    private static TimelineDashboardPlan plan(final int rows) {
        return new TimelineDashboardPlan("jsmith", TimelineDataConnection.SPANS,
                "/outputs/timelines/jsmith/spans.csv", "hash", rows, FROM, TO, "A snapshot.",
                TimelineDashboardConfig.mappings(TimelineDataConnection.SPANS, null, true), null);
    }

    private static ComponentConfig component(final DashboardConfig config, final String type) {
        return config.getComponents().stream()
                .filter(component -> type.equals(component.getType()))
                .findFirst()
                .orElseThrow();
    }
}
