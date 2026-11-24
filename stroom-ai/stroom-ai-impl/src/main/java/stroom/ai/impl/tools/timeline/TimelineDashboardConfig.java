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

import stroom.ai.impl.tools.propose.ProposeDashboardChangeTool;
import stroom.ai.impl.vis.ChartCheck;
import stroom.ai.impl.vis.ChartKind;
import stroom.ai.impl.vis.ChartRole;
import stroom.ai.impl.vis.StyleSetting;
import stroom.dashboard.shared.Automate;
import stroom.dashboard.shared.ComponentConfig;
import stroom.dashboard.shared.ComponentSelectionHandler;
import stroom.dashboard.shared.DashboardConfig;
import stroom.dashboard.shared.Dimension;
import stroom.dashboard.shared.LayoutConfig;
import stroom.dashboard.shared.QueryComponentSettings;
import stroom.dashboard.shared.Size;
import stroom.dashboard.shared.SplitLayoutConfig;
import stroom.dashboard.shared.TabConfig;
import stroom.dashboard.shared.TabLayoutConfig;
import stroom.dashboard.shared.TableComponentSettings;
import stroom.dashboard.shared.VisComponentSettings;
import stroom.dataconnection.shared.DataColumn;
import stroom.dataconnection.shared.DataColumnType;
import stroom.dataconnection.shared.DataConnectionDoc;
import stroom.docref.DocRef;
import stroom.query.api.Column;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.Format;
import stroom.query.api.Sort;
import stroom.query.api.Sort.SortDirection;
import stroom.query.api.TimeRange;
import stroom.util.json.JsonUtil;
import stroom.util.shared.NullSafe;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/// The dashboard a timeline's Data Connection is drawn on: a query of the Data Connection over the timeline's
/// period, a `timeline` chart of its table (lanes by host or source for the spans, by day or activity for the
/// activities; coloured by activity; its lane axis titled by what the lanes are; each bar's tooltip saying what
/// happened), and the table, each column typed as the Data Connection types it (its times Date Time), in time order,
/// filtered by a click on a bar to the rows starting within the bar's interval.
///
/// The chart draws from a table of its own, of the same query with no filter, in a hidden tab behind it: drawn from
/// the filtered table, a click narrowed the chart's own data to the bar clicked, its axis shrank to it, and there was
/// no way back (chat 195). A click on the selected bar, or on the chart's background, clears the selection, and the
/// table shows every row again.
public final class TimelineDashboardConfig {

    /// The query's component id.
    public static final String QUERY_ID = "query-1";
    /// The table's component id.
    public static final String TABLE_ID = "table-1";
    /// The chart's component id.
    public static final String CHART_ID = "vis-1";
    /// The chart's own table, unfiltered, in a hidden tab behind it.
    public static final String CHART_TABLE_ID = "table-2";
    /// The chart's tooltip column, in its own table: what happened in each bar.
    public static final String DETAIL = "detail";
    // The versions of the settings that the browser's components write, so they don't upgrade what is built here.
    private static final String DASHBOARD_MODEL_VERSION = "7.2.0";
    private static final String TABLE_MODEL_VERSION = "6.1.26";
    private static final String SELECTION_FILTER_ID = "tl001";

    private TimelineDashboardConfig() {
        // Static only.
    }

    /// The column the timeline chart draws for each of its roles: start and end; for the spans, each span's label,
    /// a lane per host (or per source, if the run's lanes were by source), coloured by its activity where the run
    /// found activities, else by its kind; for the activities, each activity, a lane per day (or per activity),
    /// coloured by activity.
    ///
    /// @param table         [TimelineDataConnection#SPANS] or [TimelineDataConnection#ACTIVITIES].
    /// @param lanes         The run's lanes, or null for the view's default.
    /// @param characterised True if the run found activities.
    /// @return The columns, by role id.
    public static Map<String, String> mappings(final String table, final String lanes, final boolean characterised) {
        final Map<String, String> mappings = new LinkedHashMap<>();
        mappings.put("start", "start");
        mappings.put("end", "end");
        if (TimelineDataConnection.SPANS.equals(table)) {
            mappings.put("label", "label");
            mappings.put("lane", "source".equals(lanes)
                    ? "source"
                    : "lane");
            mappings.put("colour", characterised
                    ? "activity_label"
                    : "kind");
        } else {
            mappings.put("label", "activity_label");
            mappings.put("lane", "activity".equals(lanes)
                    ? "activity_label"
                    : "day");
            mappings.put("colour", "activity_label");
        }
        return Map.copyOf(mappings);
    }

    /// The table's columns as the chart check sees them: each with the format the dashboard's table gives it.
    ///
    /// @param table [TimelineDataConnection#SPANS] or [TimelineDataConnection#ACTIVITIES].
    /// @return The columns.
    public static List<ChartCheck.TableColumn> chartColumns(final String table) {
        return TimelineDataConnection.columns(table).stream()
                .map(column -> new ChartCheck.TableColumn(column.getName(), format(column.getType()).getType().name(),
                        expression(column)))
                .toList();
    }

    /// Builds the dashboard's settings.
    ///
    /// @param dataConnection The Data Connection holding the table.
    /// @param visualisation  The timeline chart's Visualisation.
    /// @param plan           What the button writes.
    /// @return The settings.
    public static DashboardConfig build(final DocRef dataConnection,
                                        final DocRef visualisation,
                                        final TimelineDashboardPlan plan) {
        final List<ComponentConfig> components = new ArrayList<>();
        components.add(ComponentConfig.builder()
                .type("query")
                .id(QUERY_ID)
                .name("Query")
                .settings(QueryComponentSettings.builder()
                        .dataSource(dataConnection)
                        .expression(ExpressionOperator.builder().build())
                        // Runs when the dashboard opens: the rows are a snapshot, so there is nothing to wait for.
                        .automate(Automate.builder().open(true).build())
                        .build())
                .build());
        components.add(ComponentConfig.builder()
                .type("vis")
                .id(CHART_ID)
                .name("Timeline")
                .settings(new VisComponentSettings(CHART_TABLE_ID, visualisation, componentSettings(plan)))
                .build());
        final long maxResults = Math.max(plan.rows(), DataConnectionDoc.DEFAULT_MAX_ROWS);
        components.add(ComponentConfig.builder()
                .type("table")
                .id(TABLE_ID)
                .name(TimelineDataConnection.SPANS.equals(plan.table())
                        ? "Spans"
                        : "Activities")
                .settings(TableComponentSettings.builder()
                        .queryId(QUERY_ID)
                        .dataSourceRef(dataConnection)
                        .columns(columns(plan))
                        .extractValues(false)
                        .maxResults(List.of(maxResults))
                        .selectionFilter(List.of(intervalFilter()))
                        .modelVersion(TABLE_MODEL_VERSION)
                        .build())
                .build());
        components.add(ComponentConfig.builder()
                .type("table")
                .id(CHART_TABLE_ID)
                .name("Timeline data")
                .settings(TableComponentSettings.builder()
                        .queryId(QUERY_ID)
                        .dataSourceRef(dataConnection)
                        .columns(chartTableColumns(plan))
                        .extractValues(false)
                        .maxResults(List.of(maxResults))
                        .modelVersion(TABLE_MODEL_VERSION)
                        .build())
                .build());
        return DashboardConfig.builder()
                .timeRange(new TimeRange(null, Instant.ofEpochMilli(plan.fromMs()).toString(),
                        Instant.ofEpochMilli(plan.toMs()).toString()))
                .components(components)
                .layout(SplitLayoutConfig.builder()
                        .preferredSize(new Size(200, 200))
                        .dimension(Dimension.Y)
                        .children(List.<LayoutConfig>of(pane(QUERY_ID, 120), chartPane(),
                                pane(TABLE_ID, 350)))
                        .build())
                .designMode(false)
                .modelVersion(DASHBOARD_MODEL_VERSION)
                .build();
    }

    /// The table's selection filter: a click on a bar keeps the rows starting within its interval (the chart's
    /// selection from and to); with nothing selected, every row.
    ///
    /// @return The filter.
    static ComponentSelectionHandler intervalFilter() {
        return ComponentSelectionHandler.builder()
                .id(SELECTION_FILTER_ID)
                .componentId(CHART_ID)
                .expression(ExpressionOperator.builder()
                        .addTerms(ProposeDashboardChangeTool.selectionTerms(TimelineDataConnection.TIME_FIELD,
                                ProposeDashboardChangeTool.RANGE_SELECTION, CHART_ID))
                        .build())
                .enabled(true)
                .build();
    }

    /// The chart's columns: the plan's, by role, and its tooltip's ([#DETAIL]), which its own table computes.
    ///
    /// @param plan What the button writes.
    /// @return The columns, by role id.
    public static Map<String, String> chartMappings(final TimelineDashboardPlan plan) {
        final Map<String, String> mappings = new LinkedHashMap<>(plan.mappings());
        mappings.put(DETAIL, DETAIL);
        return Map.copyOf(mappings);
    }

    /// The chart's style: its lane axis titled by what the lanes are, where the plan says.
    ///
    /// @param plan What the button writes.
    /// @return The style settings, by id.
    public static Map<String, String> chartStyle(final TimelineDashboardPlan plan) {
        return NullSafe.isBlankString(plan.laneTitle())
                ? Map.of()
                : Map.of(StyleSetting.Y_AXIS_NAME.id(), plan.laneTitle());
    }

    /// The expression of the chart's tooltip column ([#DETAIL]): for a span, what happened and its events (e.g.
    /// `Focus-Window to Unfocus-Window · 2 event(s)`); for an activity, its actions and active minutes.
    ///
    /// @param table [TimelineDataConnection#SPANS] or [TimelineDataConnection#ACTIVITIES].
    /// @return The expression.
    static String detailExpression(final String table) {
        return TimelineDataConnection.SPANS.equals(table)
                ? "concat(${action}, ' · ', ${events}, ' event(s)')"
                : "concat(${actions}, ' · ', ${active_minutes}, ' active minutes')";
    }

    // The chart's settings on the dashboard: its columns by role, and its style.
    private static String componentSettings(final TimelineDashboardPlan plan) {
        final Map<String, String> settings = new LinkedHashMap<>();
        final Map<String, String> mappings = chartMappings(plan);
        for (final ChartRole role : ChartKind.TIMELINE.roles()) {
            NullSafe.consume(mappings.get(role.id()), column -> settings.put(role.id(), column));
        }
        settings.putAll(chartStyle(plan));
        return JsonUtil.writeValueAsString(settings, false);
    }

    // The table's columns: each of the Data Connection's, formatted as it is typed, the rows in time order; the
    // lane column headed by what the lanes are.
    private static List<Column> columns(final TimelineDashboardPlan plan) {
        final String lane = plan.mappings().get("lane");
        final List<Column> columns = new ArrayList<>();
        for (final DataColumn column : TimelineDataConnection.columns(plan.table())) {
            columns.add(column(column, column.getName().equals(lane) && NullSafe.isNonBlankString(plan.laneTitle())
                    ? plan.laneTitle()
                    : column.getName()));
        }
        return columns;
    }

    // The chart's own table: the columns it draws, by their names (as its settings name them), and its tooltip's.
    private static List<Column> chartTableColumns(final TimelineDashboardPlan plan) {
        final Set<String> drawn = new LinkedHashSet<>(plan.mappings().values());
        final List<Column> columns = new ArrayList<>();
        for (final DataColumn column : TimelineDataConnection.columns(plan.table())) {
            if (drawn.contains(column.getName())) {
                columns.add(column(column, column.getName()));
            }
        }
        columns.add(Column.builder()
                .id(DETAIL)
                .name(DETAIL)
                .expression(detailExpression(plan.table()))
                .format(Format.TEXT)
                .build());
        return columns;
    }

    // A column of the Data Connection, formatted as it is typed; the time field sorts the rows.
    private static Column column(final DataColumn column, final String name) {
        final Column.Builder builder = Column.builder()
                .id(column.getName())
                .name(name)
                .expression(expression(column))
                .format(format(column.getType()));
        if (TimelineDataConnection.TIME_FIELD.equals(column.getName())) {
            builder.sort(new Sort(0, SortDirection.ASCENDING));
        }
        return builder.build();
    }

    private static String expression(final DataColumn column) {
        return "${" + column.getName() + "}";
    }

    private static Format format(final DataColumnType type) {
        if (type == null) {
            return Format.TEXT;
        }
        return switch (type) {
            case DATE -> Format.DATE_TIME;
            case LONG, DOUBLE -> Format.NUMBER;
            default -> Format.TEXT;
        };
    }

    // The chart, with its own table in a hidden tab behind it.
    private static TabLayoutConfig chartPane() {
        return TabLayoutConfig.builder()
                .preferredSize(new Size(200, 400))
                .tabs(List.of(new TabConfig(CHART_ID, true), new TabConfig(CHART_TABLE_ID, false)))
                .selected(0)
                .build();
    }

    private static TabLayoutConfig pane(final String componentId, final int height) {
        return TabLayoutConfig.builder()
                .preferredSize(new Size(200, height))
                .tabs(List.of(new TabConfig(componentId, true)))
                .selected(0)
                .build();
    }
}
