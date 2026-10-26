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

package stroom.ai.impl.vis;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

/// The kinds of chart the agent can generate, each with the table columns it takes (its roles) and the style
/// settings a user can change. The ECharts Generic adapter (`echarts-generic.js`) draws each kind from these, so
/// the role and setting ids here must match the ones it reads.
public enum ChartKind {

    BAR("bar", "Bar chart", "one bar per category, grouped or stacked by series",
            List.of(
                    new ChartRole("category", "Category", true, ChartRole.Values.ANY,
                            "the bars along the axis, e.g. UserId"),
                    new ChartRole("value", "Value", true, ChartRole.Values.NUMBER,
                            "each bar's length, e.g. a count() column"),
                    new ChartRole("series", "Series (Optional)", false, ChartRole.Values.ANY,
                            "splits each category's bar by this column, shown in the legend")),
            List.of(StyleSetting.X_AXIS_NAME, StyleSetting.Y_AXIS_NAME, StyleSetting.LEGEND,
                    StyleSetting.SORT, StyleSetting.HORIZONTAL, StyleSetting.STACK, StyleSetting.SHOW_LABELS_OFF,
                    StyleSetting.UNITS, StyleSetting.LOG_SCALE, StyleSetting.THRESHOLD, StyleSetting.THRESHOLD_LABEL,
                    StyleSetting.AVERAGE_LINE, StyleSetting.BREAK_OUTLIERS, StyleSetting.ZOOM_SLIDER,
                    StyleSetting.AUTO_INTERVAL, StyleSetting.INTERVAL_COMBINE, StyleSetting.MAX_SERIES,
                    StyleSetting.MAX_VALUES)),

    LINE("line", "Line chart", "one line per series over x; a time axis when x is a date",
            List.of(
                    new ChartRole("x", "X Axis", true, ChartRole.Values.ANY,
                            "along the bottom, usually a time, e.g. EventTime rounded with roundHour()"),
                    new ChartRole("y", "Y Axis", true, ChartRole.Values.NUMBER,
                            "the height of each point, e.g. a count() column"),
                    new ChartRole("series", "Series (Optional)", false, ChartRole.Values.ANY,
                            "one line per value of this column, shown in the legend")),
            List.of(StyleSetting.X_AXIS_NAME, StyleSetting.Y_AXIS_NAME, StyleSetting.LEGEND,
                    StyleSetting.SMOOTH, StyleSetting.AREA, StyleSetting.STACK, StyleSetting.SHOW_LABELS_OFF,
                    StyleSetting.UNITS, StyleSetting.LOG_SCALE, StyleSetting.THRESHOLD, StyleSetting.THRESHOLD_LABEL,
                    StyleSetting.AVERAGE_LINE, StyleSetting.BREAK_OUTLIERS, StyleSetting.ZOOM_SLIDER,
                    StyleSetting.AUTO_INTERVAL, StyleSetting.INTERVAL_COMBINE, StyleSetting.MAX_SERIES,
                    StyleSetting.MAX_VALUES)),

    PIE("pie", "Pie chart", "slices of a whole; a doughnut as an option",
            List.of(
                    new ChartRole("name", "Name", true, ChartRole.Values.ANY,
                            "what each slice is, e.g. EventType"),
                    new ChartRole("value", "Value", true, ChartRole.Values.NUMBER,
                            "each slice's size, e.g. a count() column; must not be negative")),
            List.of(StyleSetting.LEGEND, StyleSetting.DOUGHNUT, StyleSetting.SHOW_LABELS_ON, StyleSetting.UNITS,
                    StyleSetting.MAX_SLICES, StyleSetting.MAX_VALUES)),

    SCATTER("scatter", "Scatter plot", "points placed by two numbers or dates, optionally sized by a third",
            List.of(
                    new ChartRole("x", "X Axis", true, ChartRole.Values.NUMBER_OR_DATE,
                            "each point's position across"),
                    new ChartRole("y", "Y Axis", true, ChartRole.Values.NUMBER_OR_DATE,
                            "each point's position up"),
                    new ChartRole("size", "Size (Optional)", false, ChartRole.Values.NUMBER,
                            "each point's size"),
                    new ChartRole("series", "Series (Optional)", false, ChartRole.Values.ANY,
                            "colours points by this column, shown in the legend")),
            List.of(StyleSetting.X_AXIS_NAME, StyleSetting.Y_AXIS_NAME, StyleSetting.LEGEND,
                    StyleSetting.UNITS, StyleSetting.LOG_SCALE, StyleSetting.THRESHOLD, StyleSetting.THRESHOLD_LABEL,
                    StyleSetting.BREAK_OUTLIERS, StyleSetting.ZOOM_SLIDER, StyleSetting.JITTER,
                    StyleSetting.MAX_SERIES, StyleSetting.MAX_VALUES)),

    GRAPH("graph", "Network graph", "a force-directed graph of links between things; each node is sized by its "
                                    + "number of connections",
            List.of(
                    new ChartRole("source", "Source", true, ChartRole.Values.ANY,
                            "one end of each link, e.g. the sender"),
                    new ChartRole("target", "Target", true, ChartRole.Values.ANY,
                            "the other end, e.g. the recipient"),
                    new ChartRole("weight", "Weight (Optional)", false, ChartRole.Values.NUMBER,
                            "how strong each link is, e.g. a count() of messages; drawn as its width"),
                    new ChartRole("sourceGroup", "Source Group (Optional)", false, ChartRole.Values.ANY,
                            "colours the source node by this column"),
                    new ChartRole("targetGroup", "Target Group (Optional)", false, ChartRole.Values.ANY,
                            "colours the target node by this column"),
                    level(3, "a further node the target links to, e.g. a port after a destination IP"),
                    level(4, "a further node level 3 links to"),
                    level(5, "a further node level 4 links to"),
                    level(6, "a further node level 5 links to")),
            List.of(StyleSetting.LEGEND, StyleSetting.DIRECTED, StyleSetting.NODE_COLOURS,
                    StyleSetting.SHOW_LABELS_AUTO, StyleSetting.PHYSICS, StyleSetting.THUMBNAIL,
                    StyleSetting.MAX_NODES, StyleSetting.MAX_VALUES)),

    HEATMAP("heatmap", "Heatmap", "a grid with a cell for each x and y pair, coloured by its value",
            List.of(
                    new ChartRole("x", "X Axis", true, ChartRole.Values.ANY,
                            "the grid's columns, e.g. the hour of the day"),
                    new ChartRole("y", "Y Axis", true, ChartRole.Values.ANY,
                            "the grid's rows, e.g. the day of the week or a UserId"),
                    new ChartRole("value", "Value", true, ChartRole.Values.NUMBER,
                            "each cell's value, e.g. a count() column; drawn as its colour")),
            List.of(StyleSetting.COLOUR_SCALE, StyleSetting.SHOW_LABELS_OFF, StyleSetting.UNITS,
                    StyleSetting.MAX_VALUES)),

    CALENDAR("calendar", "Calendar heatmap", "a calendar of days, each coloured by its value",
            List.of(
                    new ChartRole("date", "Date", true, ChartRole.Values.DATE,
                            "the day, e.g. EventTime rounded with floorDay(), formatted as Date Time"),
                    new ChartRole("value", "Value", true, ChartRole.Values.NUMBER,
                            "each day's value, e.g. a count() column; days in several rows are added up")),
            List.of(StyleSetting.COLOUR_SCALE, StyleSetting.UNITS, StyleSetting.MAX_VALUES)),

    HISTOGRAM("histogram", "Histogram", "how a number is spread: bars counting the rows in each range of it",
            List.of(
                    new ChartRole("value", "Value", true, ChartRole.Values.NUMBER,
                            "the numbers to count, e.g. bytes or a duration; one per row, not grouped"),
                    new ChartRole("series", "Series (Optional)", false, ChartRole.Values.ANY,
                            "splits each range's bar by this column, shown in the legend")),
            List.of(StyleSetting.BINS, StyleSetting.X_AXIS_NAME, StyleSetting.Y_AXIS_NAME, StyleSetting.LEGEND,
                    StyleSetting.STACK, StyleSetting.SHOW_LABELS_OFF, StyleSetting.UNITS, StyleSetting.MAX_SERIES,
                    StyleSetting.MAX_VALUES)),

    SANKEY("sankey", "Sankey diagram", "flows from one thing to the next, each as wide as how much flows",
            List.of(
                    new ChartRole("source", "Source", true, ChartRole.Values.ANY,
                            "where each flow starts, e.g. a source IP"),
                    new ChartRole("target", "Target", true, ChartRole.Values.ANY,
                            "where it goes, e.g. a destination port"),
                    new ChartRole("value", "Value (Optional)", false, ChartRole.Values.NUMBER,
                            "how much flows, e.g. a count() column; each row counts once if not given"),
                    level(3, "where it goes after the target, e.g. a port after a destination IP; each level's "
                             + "nodes are its own"),
                    level(4, "where it goes after level 3"),
                    level(5, "where it goes after level 4"),
                    level(6, "where it goes after level 5")),
            List.of(StyleSetting.ORIENT, StyleSetting.SHOW_LABELS_ON, StyleSetting.MAX_NODES,
                    StyleSetting.MAX_VALUES)),

    CHORD("chord", "Chord diagram", "links between things round a circle, each as wide as its weight",
            List.of(
                    new ChartRole("source", "Source", true, ChartRole.Values.ANY,
                            "one end of each link, e.g. the sender"),
                    new ChartRole("target", "Target", true, ChartRole.Values.ANY,
                            "the other end, e.g. the recipient"),
                    new ChartRole("weight", "Weight (Optional)", false, ChartRole.Values.NUMBER,
                            "how strong each link is, e.g. a count() of messages; each row counts once if not "
                            + "given"),
                    level(3, "a further thing the target links to"),
                    level(4, "a further thing level 3 links to"),
                    level(5, "a further thing level 4 links to"),
                    level(6, "a further thing level 5 links to")),
            List.of(StyleSetting.SHOW_LABELS_ON, StyleSetting.MAX_NODES, StyleSetting.MAX_VALUES)),

    GAUGE("gauge", "Gauge", "a dial showing a value against its range; one per name, when a name column is mapped",
            List.of(
                    new ChartRole("value", "Value", true, ChartRole.Values.NUMBER,
                            "the value shown, e.g. max(Bytes) in a table grouped to one row"),
                    new ChartRole("name", "Name (Optional)", false, ChartRole.Values.ANY,
                            "a gauge per value of this column, e.g. Host; without it, the first row is shown")),
            List.of(StyleSetting.GAUGE_MIN, StyleSetting.GAUGE_MAX, StyleSetting.BANDS, StyleSetting.UNITS,
                    StyleSetting.MAX_GAUGES, StyleSetting.MAX_VALUES)),

    SUNBURST("sunburst", "Sunburst", "rings of a whole: the inner ring by name, each ring out splitting the one "
                                     + "inside it by a further column",
            List.of(
                    new ChartRole("name", "Name", true, ChartRole.Values.ANY,
                            "the inner ring's segments, e.g. a region"),
                    ring(2, "splits each inner segment, e.g. the hosts in a region"),
                    ring(3, "splits each level 2 segment, e.g. the processes on a host"),
                    ring(4, "splits each level 3 segment"),
                    ring(5, "splits each level 4 segment"),
                    ring(6, "splits each level 5 segment"),
                    new ChartRole("value", "Value (Optional)", false, ChartRole.Values.NUMBER,
                            "each segment's size, e.g. a count() column; each row counts once if not given")),
            List.of(StyleSetting.SHOW_LABELS_ON, StyleSetting.UNITS, StyleSetting.MAX_VALUES));

    private final String id;
    private final String displayName;
    private final String summary;
    private final List<ChartRole> roles;
    private final List<StyleSetting> styleSettings;

    ChartKind(final String id,
              final String displayName,
              final String summary,
              final List<ChartRole> roles,
              final List<StyleSetting> styleSettings) {
        this.id = id;
        this.displayName = displayName;
        this.summary = summary;
        this.roles = roles;
        this.styleSettings = styleSettings;
    }

    /// @return The id the adapter knows the kind by, e.g. `graph`.
    public String id() {
        return id;
    }

    /// @return The name a user would call it, e.g. "Network graph".
    public String displayName() {
        return displayName;
    }

    /// @return A short description of what it draws.
    public String summary() {
        return summary;
    }

    /// @return The columns it takes, in the order the adapter receives their values.
    public List<ChartRole> roles() {
        return roles;
    }

    /// @return The style settings a user can change.
    public List<StyleSetting> styleSettings() {
        return styleSettings;
    }

    // A further level of a flow or network after its target (3 to 6): a row links each level it gives to the next,
    // up to the first it leaves empty.
    private static ChartRole level(final int level, final String description) {
        return new ChartRole("level" + level, "Level " + level + " (Optional)", false, ChartRole.Values.ANY,
                description + "; a row's links stop at the first level it leaves empty");
    }

    // A further ring of a sunburst (2 to 6), splitting the segments of the ring inside it.
    private static ChartRole ring(final int level, final String description) {
        return new ChartRole("level" + level, "Level " + level + " (Optional)", false, ChartRole.Values.ANY,
                description + "; a row's rings stop at the first level it leaves empty");
    }

    /// @param roleId A role's id.
    /// @return The role, if the kind has it.
    public Optional<ChartRole> role(final String roleId) {
        return roles.stream()
                .filter(role -> role.id().equals(roleId))
                .findFirst();
    }

    /// @param settingId A style setting's id.
    /// @return The setting, if the kind has it.
    public Optional<StyleSetting> styleSetting(final String settingId) {
        return styleSettings.stream()
                .filter(setting -> setting.id().equals(settingId))
                .findFirst();
    }

    /// Finds a kind by its id or name, ignoring case, e.g. `graph`, `network graph` or `NETWORK`.
    ///
    /// @param text The id or name.
    /// @return The kind, if one matches.
    public static Optional<ChartKind> find(final String text) {
        if (text == null) {
            return Optional.empty();
        }
        final String wanted = text.strip().toLowerCase(Locale.ROOT);
        return Arrays.stream(values())
                .filter(kind -> kind.id.equals(wanted)
                                || kind.name().toLowerCase(Locale.ROOT).equals(wanted)
                                || kind.displayName.toLowerCase(Locale.ROOT).equals(wanted)
                                || (kind == GRAPH && (wanted.contains("network") || wanted.contains("force"))))
                .findFirst();
    }

    /// @return The ids of all kinds, e.g. for a tool's argument description.
    public static String ids() {
        return Arrays.stream(values())
                .map(ChartKind::id)
                .collect(Collectors.joining(", "));
    }
}
