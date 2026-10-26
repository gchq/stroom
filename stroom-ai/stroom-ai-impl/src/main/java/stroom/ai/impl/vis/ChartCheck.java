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

import stroom.ai.impl.workspace.ExpressionFunctions;
import stroom.ai.impl.workspace.ExpressionFunctions.TextCall;
import stroom.util.shared.NullSafe;

import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/// Checks that a table's data can make a chart, before the chart is proposed: every required role is mapped to a
/// column the table has, each column holds the values its role needs, and the sample of rows isn't too big or too
/// sparse for the chart to read. Each problem says what to do about it, so the agent can tell the user and suggest
/// a way forward rather than propose a chart that can't draw.
public final class ChartCheck {

    // The share of a sample's non-blank values that must be numbers for a column to count as numeric.
    private static final double NUMERIC_SHARE = 0.9;
    // More categories than this make a bar chart's axis unreadable.
    static final int MAX_READABLE_CATEGORIES = 100;
    // More distinct times than this, raw, is more points than a chart over time reads well.
    static final int MAX_TIME_POINTS = 500;
    // More points than this on a category axis line chart are better on a time or value axis.
    static final int MAX_LINE_CATEGORIES = 500;

    private static final String FORMAT_DATE = "formatDate";
    // An expression that is a number when its rows are grouped: a count, or a sum, average, minimum or maximum.
    private static final Pattern AGGREGATE = Pattern.compile(
            "^\\s*(count|countUnique|countGroups|sum|average|avg|min|max|stDev|variance)\\s*\\(",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern NUMBER = Pattern.compile(
            "\\s*[-+]?((\\d{1,3}(,\\d{3})+|\\d+)(\\.\\d*)?|\\.\\d+)([eE][-+]?\\d+)?\\s*");
    private static final Pattern DATE = Pattern.compile(
            "\\s*\\d{4}-\\d{2}-\\d{2}([T ]\\d{2}:\\d{2}(:\\d{2}(\\.\\d+)?)?)?(Z|[+-]\\d{2}:?\\d{2})?\\s*");

    private ChartCheck() {
        // Static only.
    }

    /// Checks a chart against a table.
    ///
    /// @param kind     The chart kind.
    /// @param mappings The column mapped to each role, by role id.
    /// @param style    The style settings chosen, by setting id, already normalised.
    /// @param columns  The table's columns, with their formats where known.
    /// @param sample   A sample of the table's rows, or null if none could be had.
    /// @return What was found.
    public static Result check(final ChartKind kind,
                               final Map<String, String> mappings,
                               final Map<String, String> style,
                               final List<TableColumn> columns,
                               final Sample sample) {
        final Result result = new Result();
        final Map<String, String> mapped = new LinkedHashMap<>();
        NullSafe.map(mappings).forEach((roleId, column) -> {
            if (NullSafe.isNonBlankString(column)) {
                mapped.put(roleId, column.strip());
            }
        });

        checkRoles(kind, mapped, columns, result);
        if (!result.problems.isEmpty()) {
            return result;
        }
        checkGrouping(kind, mapped, columns, result);
        if (!result.problems.isEmpty()) {
            return result;
        }

        if (sample == null || sample.rows().isEmpty()) {
            result.warnings.add("There were no rows to check the data against, so only the columns were checked. "
                                + "Run the table's query first if it hasn't run.");
        }

        checkAutoInterval(kind, mapped, style, columns, sample, result);

        for (final Entry<String, String> entry : mapped.entrySet()) {
            final ChartRole role = kind.role(entry.getKey()).orElseThrow();
            final TableColumn column = findColumn(columns, entry.getValue()).orElseThrow();
            checkValues(kind, role, column, sample, result);
        }

        if (sample != null && !sample.rows().isEmpty()) {
            checkSize(kind, mapped, style, sample, result);
            checkTimeInterval(kind, mapped, style, columns, sample, result);
        }
        return result;
    }

    /// Gives a chart with Auto Interval on ([StyleSetting#AUTO_INTERVAL]) how it combines an interval's values, if
    /// not given: as its measure column's expression does, so the counts in an interval are summed and the maximums
    /// are the maximum (e.g. sum for count() or sum(), max for max(), average for average()).
    ///
    /// @param kind     The chart kind.
    /// @param mappings The column mapped to each role, by role id.
    /// @param style    The style settings chosen, normalised.
    /// @param columns  The table's columns, with their expressions where known.
    /// @return The style settings, with Interval Combine given if Auto Interval is on and it wasn't.
    public static Map<String, String> withIntervalCombine(final ChartKind kind,
                                                          final Map<String, String> mappings,
                                                          final Map<String, String> style,
                                                          final List<TableColumn> columns) {
        if (!"true".equals(NullSafe.map(style).get(StyleSetting.AUTO_INTERVAL.id()))
            || NullSafe.map(style).containsKey(StyleSetting.INTERVAL_COMBINE.id())
            || kind.styleSetting(StyleSetting.INTERVAL_COMBINE.id()).isEmpty()) {
            return style;
        }
        final String measure = NullSafe.map(mappings).get(kind == ChartKind.LINE
                ? "y"
                : "value");
        final String expression = findColumn(columns, NullSafe.string(measure).strip())
                .map(TableColumn::expression)
                .orElse("");
        final Matcher aggregate = AGGREGATE.matcher(NullSafe.string(expression));
        final String function = aggregate.find()
                ? aggregate.group(1).toLowerCase(Locale.ROOT)
                : "";
        final String combine = switch (function) {
            case "max" -> "Max";
            case "min" -> "Min";
            case "average", "avg" -> "Average";
            default -> "Sum";
        };
        final Map<String, String> filled = new LinkedHashMap<>(style);
        filled.put(StyleSetting.INTERVAL_COMBINE.id(), combine);
        return filled;
    }

    /// Says what a column holds, from its format, its expression and the values sampled, for choosing the
    /// columns of a chart the table can make.
    ///
    /// @param column A column of the table.
    /// @param sample A sample of the table's rows, or null if none could be had.
    /// @return [ChartRole.Values#NUMBER], [ChartRole.Values#DATE] or [ChartRole.Values#ANY] (text), or empty if
    /// it can't be told (no format, aggregate or values to go by).
    public static Optional<ChartRole.Values> holds(final TableColumn column, final Sample sample) {
        final String format = NullSafe.string(column.format()).toUpperCase(Locale.ROOT);
        switch (format) {
            case "DATE_TIME" -> {
                return Optional.of(ChartRole.Values.DATE);
            }
            case "NUMBER" -> {
                return Optional.of(ChartRole.Values.NUMBER);
            }
            case "TEXT" -> {
                return Optional.of(ChartRole.Values.ANY);
            }
            default -> {
                // General, or not known: by the values.
            }
        }
        final Values values = Values.of(column.name(), sample);
        if (values.nonBlank > 0) {
            if ((double) values.numbers / values.nonBlank >= NUMERIC_SHARE) {
                return Optional.of(ChartRole.Values.NUMBER);
            }
            return Optional.of((double) values.dates / values.nonBlank >= NUMERIC_SHARE
                    ? ChartRole.Values.DATE
                    : ChartRole.Values.ANY);
        }
        return AGGREGATE.matcher(NullSafe.string(column.expression())).find()
                ? Optional.of(ChartRole.Values.NUMBER)
                : Optional.empty();
    }

    // Every required role mapped, every mapping to a role the kind has and a column the table has.
    private static void checkRoles(final ChartKind kind,
                                   final Map<String, String> mapped,
                                   final List<TableColumn> columns,
                                   final Result result) {
        for (final String roleId : mapped.keySet()) {
            if (kind.role(roleId).isEmpty()) {
                result.problems.add("A " + kind.displayName().toLowerCase(Locale.ROOT) + " has no '" + roleId
                                    + "'. Its columns are: " + describeRoles(kind) + ".");
            }
        }
        for (final ChartRole role : kind.roles()) {
            if (role.required() && !mapped.containsKey(role.id())) {
                result.problems.add("Map a column to " + role.id() + " (" + role.description() + "). The table's "
                                    + "columns are: " + describeColumns(columns) + ".");
            }
        }
        for (final Entry<String, String> entry : mapped.entrySet()) {
            if (kind.role(entry.getKey()).isPresent() && findColumn(columns, entry.getValue()).isEmpty()) {
                final Optional<TableColumn> nearly = columns.stream()
                        .filter(column -> column.name().equalsIgnoreCase(entry.getValue()))
                        .findFirst();
                result.problems.add("The table has no column '" + entry.getValue() + "' for " + entry.getKey()
                                    + nearly.map(column -> ": did you mean '" + column.name() + "'?")
                                            .orElse(". Its columns are: " + describeColumns(columns) + ".")
                                    + " Add the column to the table first if it needs one.");
            }
        }
        if (kind == ChartKind.GRAPH
            && mapped.containsKey("source")
            && Objects.equals(mapped.get("source"), mapped.get("target"))) {
            result.problems.add("Source and target are both '" + mapped.get("source") + "', so every link would "
                                + "join a node to itself. A network needs two columns, e.g. the sender and the "
                                + "recipient of each message.");
        }
    }

    // A chart gets only its table's top-level rows (Stroom doesn't open a table's groups for it), so a column
    // grouped below the top level is blank in every row it gets.
    private static void checkGrouping(final ChartKind kind,
                                      final Map<String, String> mapped,
                                      final List<TableColumn> columns,
                                      final Result result) {
        final int top = columns.stream()
                .map(TableColumn::group)
                .filter(Objects::nonNull)
                .min(Integer::compare)
                .orElse(-1);
        if (top < 0) {
            return;
        }
        final List<String> topNames = columns.stream()
                .filter(column -> Objects.equals(column.group(), top))
                .map(column -> "'" + column.name() + "'")
                .toList();
        for (final Entry<String, String> entry : mapped.entrySet()) {
            final TableColumn column = findColumn(columns, entry.getValue()).orElseThrow();
            if (column.group() != null && column.group() > top) {
                final String name = "'" + column.name() + "'";
                final String topLevel = "level " + (top + 1);
                result.problems.add(name + " can't be " + entry.getKey() + ": it is grouped at level "
                                    + (column.group() + 1) + ", but a " + kind.displayName().toLowerCase(Locale.ROOT)
                                    + " gets only the table's top-level rows (one per "
                                    + String.join(" and ", topNames) + ", grouped at " + topLevel + "), where "
                                    + name + " is blank. Group " + name + " at " + topLevel + " too (a group "
                                    + "change), so each row is one combination of them.");
            }
        }
    }

    private static void checkValues(final ChartKind kind,
                                    final ChartRole role,
                                    final TableColumn column,
                                    final Sample sample,
                                    final Result result) {
        final Values values = Values.of(column.name(), sample);
        final String format = NullSafe.string(column.format()).toUpperCase(Locale.ROOT);
        final TextCall textCall = ExpressionFunctions.textCall(column.expression()).orElse(null);
        if (textCall != null && checkTextAsNumberOrDate(role, column, format, textCall, values, result)) {
            return;
        }
        // A StroomQL query's column formats aren't known, and a dashboard column with the General format sends
        // dates as dates (Stroom gives the chart their type from the values): take those as they are.
        final boolean known = !format.isEmpty() && !"GENERAL".equals(format);

        switch (role.values()) {
            case NUMBER -> {
                if ("DATE_TIME".equals(format) || "TEXT".equals(format)) {
                    result.problems.add(numberProblem(role, column, "its format is " + format));
                } else if (values.nonBlank > 0 && values.numberShare() < NUMERIC_SHARE) {
                    result.problems.add(numberProblem(role, column, "only " + values.numbers + " of the "
                                                                    + values.nonBlank + " values sampled are "
                                                                    + "numbers"));
                } else if (values.nonBlank > values.numbers) {
                    result.warnings.add((values.nonBlank - values.numbers) + " of the " + values.nonBlank
                                        + " values sampled in '" + column.name() + "' aren't numbers: those rows "
                                        + "won't be drawn.");
                }
            }
            case NUMBER_OR_DATE -> {
                if ("TEXT".equals(format) && values.dates > 0 && values.dates >= values.numbers) {
                    result.problems.add(dateFormatProblem(role, column, textCall));
                } else if ("TEXT".equals(format)) {
                    result.problems.add("'" + column.name() + "' can't be " + role.id() + ": its format is TEXT "
                                        + "and " + role.id() + " needs numbers or dates. Set the column's format "
                                        + "to Number or Date Time, or map another column.");
                } else if (known && !"DATE_TIME".equals(format) && values.dates > 0
                           && values.dates >= values.numbers) {
                    result.problems.add(dateFormatProblem(role, column, textCall));
                } else if (!"DATE_TIME".equals(format) && values.nonBlank > 0
                           && (double) (values.numbers + values.dates) / values.nonBlank < NUMERIC_SHARE) {
                    result.problems.add("'" + column.name() + "' can't be " + role.id() + ": only "
                                        + values.numbers + " of the " + values.nonBlank + " values sampled are "
                                        + "numbers, and " + role.id() + " needs numbers or dates.");
                }
            }
            case DATE -> {
                if ("TEXT".equals(format) || "NUMBER".equals(format)) {
                    result.problems.add("'" + column.name() + "' can't be " + role.id() + ": its format is "
                                        + format + " and " + role.id() + " needs dates. Set the column's format to "
                                        + "Date Time, or map another column.");
                } else if (!"DATE_TIME".equals(format) && values.nonBlank > 0
                           && (double) values.dates / values.nonBlank < NUMERIC_SHARE) {
                    result.problems.add("'" + column.name() + "' can't be " + role.id() + ": only " + values.dates
                                        + " of the " + values.nonBlank + " values sampled are dates, and "
                                        + role.id() + " needs dates.");
                } else if (known && !"DATE_TIME".equals(format) && values.dates > 0) {
                    result.problems.add(dateFormatProblem(role, column, textCall));
                }
            }
            case ANY -> {
                if (kind == ChartKind.LINE && "x".equals(role.id()) && known && !"DATE_TIME".equals(format)
                    && values.dates > 0 && values.dates >= values.nonBlank / 2) {
                    result.warnings.add("'" + column.name() + "' holds dates but its format isn't Date Time, so "
                                        + "they'll be drawn as text categories rather than on a time axis. For a "
                                        + "time axis, " + toDateTime(textCall) + ".");
                }
            }
        }
        if (values.nonBlank == 0 && values.rows > 0) {
            if (role.required()) {
                result.problems.add("Every value sampled in '" + column.name() + "' is blank, so there's "
                                    + "nothing to draw for " + role.id() + ".");
            } else {
                result.warnings.add("Every value sampled in '" + column.name() + "' (" + role.id() + ") is "
                                    + "blank.");
            }
        }
    }

    private static String numberProblem(final ChartRole role, final TableColumn column, final String why) {
        return "'" + column.name() + "' can't be " + role.id() + ": " + why + ", and " + role.id() + " needs "
               + "numbers. Map a numeric column, or add one, e.g. a count() column grouped by the category "
               + "(an add_column change), or convert it with toDouble() in the column's expression.";
    }

    private static String dateFormatProblem(final ChartRole role, final TableColumn column, final TextCall textCall) {
        return "'" + column.name() + "' holds dates as text, so they can't be placed on the " + role.id()
               + " axis. " + capitalise(toDateTime(textCall))
               + (textCall == null
                ? "; if the dates are in text of another field, parse them with parseDate() in the column's "
                  + "expression."
                : ".");
    }

    // How to make a column that holds dates as text a Date Time column. Its format alone doesn't do it when its
    // expression makes text, as the text stays text.
    private static String toDateTime(final TextCall textCall) {
        if (textCall == null) {
            return "set the column's format to Date Time (an edit_column change)";
        }
        if (FORMAT_DATE.equalsIgnoreCase(textCall.function()) && !textCall.arguments().isEmpty()) {
            return "change the column's expression to the date itself, " + textCall.arguments().getFirst()
                   + ", and its format to Date Time, which sets how it's shown (one edit_column change): "
                   + "formatDate() makes text, which stays text whatever the column's format";
        }
        return "change the column's expression to parse its text as a date, parseDate(<the expression>, '<its "
               + "pattern>'), and its format to Date Time (one edit_column change): " + textCall.function()
               + "() makes text, which stays text whatever the column's format";
    }

    // A column formatted as a Number or Date Time whose expression makes text: Stroom sends a chart each value as
    // a number, which text is only if it reads as one (a number, or an ISO 8601 date with a zone), else the row
    // has no value. Adds the problem, if any, and says whether it did.
    private static boolean checkTextAsNumberOrDate(final ChartRole role,
                                                   final TableColumn column,
                                                   final String format,
                                                   final TextCall textCall,
                                                   final Values values,
                                                   final Result result) {
        final String madeBy = "its expression makes text (" + textCall.function() + "())";
        if ("DATE_TIME".equals(format)) {
            // formatDate() with no pattern gives an ISO 8601 date, which Stroom reads back as a date.
            if (FORMAT_DATE.equalsIgnoreCase(textCall.function()) && textCall.arguments().size() == 1) {
                return false;
            }
            result.problems.add("'" + column.name() + "' can't be " + role.id() + ": its format is Date Time but "
                                + madeBy + ", which a chart gets as a date only if it is an ISO 8601 date with "
                                + "a zone (e.g. 2026-01-31T13:00:00.000Z), so its rows would have no "
                                + role.id() + ". To fix it, " + toDateTime(textCall) + ".");
            return true;
        }
        if ("NUMBER".equals(format) && values.nonBlank > values.numbers) {
            result.problems.add("'" + column.name() + "' can't be " + role.id() + ": its format is Number but "
                                + madeBy + ", and " + (values.nonBlank - values.numbers) + " of the "
                                + values.nonBlank + " values sampled aren't numbers, so those rows would have no "
                                + role.id() + ". Map a numeric column, or change the expression to give numbers, "
                                + "e.g. with toDouble() (an edit_column change).");
            return true;
        }
        return false;
    }

    private static String capitalise(final String text) {
        return text.isEmpty()
                ? text
                : Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }

    // Whether the sample makes a chart that can be read, and how much of it the limits leave out.
    private static void checkSize(final ChartKind kind,
                                  final Map<String, String> mapped,
                                  final Map<String, String> style,
                                  final Sample sample,
                                  final Result result) {
        result.stats.put("rows", sample.rows().size());
        final int maxSeries = intStyle(style, StyleSetting.MAX_SERIES);
        switch (kind) {
            case BAR -> {
                final int categories = distinct(sample, mapped.get("category"));
                result.stats.put("categories", categories);
                if (categories > MAX_READABLE_CATEGORIES) {
                    result.warnings.add(categories + " categories is too many bars to read. Group or filter "
                                        + "the table, or sort it by the value and lower the table's or the "
                                        + "chart's Max Rows to show the top ones.");
                }
                seriesWarning(mapped, sample, maxSeries, result);
            }
            case LINE -> {
                seriesWarning(mapped, sample, maxSeries, result);
                final int points = distinct(sample, mapped.get("x"));
                result.stats.put("xValues", points);
            }
            case PIE -> {
                final int slices = distinct(sample, mapped.get("name"));
                final int maxSlices = intStyle(style, StyleSetting.MAX_SLICES);
                result.stats.put("slices", slices);
                if (slices > maxSlices) {
                    result.warnings.add(slices + " names: the smallest " + (slices - maxSlices) + " will be "
                                        + "combined as 'Other' (Max Slices is " + maxSlices + "). A bar chart "
                                        + "reads better with many.");
                }
            }
            case SCATTER -> seriesWarning(mapped, sample, maxSeries, result);
            case GRAPH, SANKEY, CHORD -> networkSize(kind, mapped, style, sample, result);
            case HEATMAP -> {
                final int columns = distinct(sample, mapped.get("x"));
                final int rows = distinct(sample, mapped.get("y"));
                result.stats.put("xValues", columns);
                result.stats.put("yValues", rows);
                if (columns > MAX_READABLE_CATEGORIES || rows > MAX_READABLE_CATEGORIES) {
                    result.warnings.add(columns + " x values by " + rows + " y values is too many cells to read. "
                                        + "Group the table more coarsely (e.g. by hour rather than minute), or "
                                        + "filter it.");
                }
            }
            case CALENDAR -> result.stats.put("days", distinct(sample, mapped.get("date")));
            case HISTOGRAM -> seriesWarning(mapped, sample, maxSeries, result);
            case SUNBURST -> result.stats.put("innerSegments", distinct(sample, mapped.get("name")));
            case GAUGE -> {
                final int gauges = mapped.containsKey("name")
                        ? distinct(sample, mapped.get("name"))
                        : 1;
                final int maxGauges = intStyle(style, StyleSetting.MAX_GAUGES);
                result.stats.put("gauges", gauges);
                if (!mapped.containsKey("name") && sample.rows().size() > 1) {
                    result.warnings.add("A gauge without a name column shows only the first of the "
                                        + sample.rows().size() + "+ rows. Group the table to one row (e.g. max() "
                                        + "of the column), or map a name column for a gauge per row.");
                } else if (gauges > maxGauges) {
                    result.warnings.add(gauges + " names: only the first " + maxGauges + " gauges are drawn (Max "
                                        + "Gauges). Filter the table, or raise Max Gauges (up to 24).");
                }
            }
        }
        final int maxValues = intStyle(style, StyleSetting.MAX_VALUES);
        if (sample.rows().size() > maxValues) {
            result.warnings.add("The table has more rows (" + sample.rows().size() + "+) than the chart's Max "
                                + "Rows (" + maxValues + "), so only the first " + maxValues + " will be drawn.");
        }
    }

    // For a chart over time (a line chart's or a scatter plot's Date Time x) whose table gives a row per event, or
    // per raw time, rather than per interval: an interval to group the table by first, from the time the sample
    // spans, so Stroom aggregates over all the data rather than the chart showing only the first Max Rows rows.
    private static void checkTimeInterval(final ChartKind kind,
                                          final Map<String, String> mapped,
                                          final Map<String, String> style,
                                          final List<TableColumn> columns,
                                          final Sample sample,
                                          final Result result) {
        if (kind != ChartKind.LINE && kind != ChartKind.SCATTER) {
            return;
        }
        final TableColumn x = findColumn(columns, mapped.get("x")).orElse(null);
        if (x == null || !"DATE_TIME".equals(x.format()) || isRounded(x.expression())) {
            return;
        }
        final int maxValues = intStyle(style, StyleSetting.MAX_VALUES);
        final int times = distinct(sample, x.name());
        if (sample.rows().size() < maxValues && times <= MAX_TIME_POINTS) {
            return;
        }
        final Optional<Duration> span = span(sample, x.name());
        final Optional<String> floor = span.flatMap(ChartCheck::intervalFunction);
        if (floor.isEmpty()) {
            return;
        }
        result.stats.put("timeSpanDays", (int) Math.min(Integer.MAX_VALUE, span.get().toDays()));
        final String time = NullSafe.isNonBlankString(x.expression())
                ? x.expression().strip()
                : "${" + x.name() + "}";
        result.warnings.add("'" + x.name() + "' is a raw time spanning about " + describe(span.get()) + " ("
                            + sample.rows().size() + "+ rows sampled, " + times + " distinct times), so the "
                            + "chart would show only the first " + maxValues + " rows, unaggregated. Offer to "
                            + "aggregate the table first: a column " + floor.get() + "(" + time + "), formatted "
                            + "as Date Time and grouped, with count() (or the measure summed or averaged), then "
                            + "chart that column as x.");
    }

    // Auto Interval groups the rows over a line chart's x, or a bar chart's categories, which must be dates; and,
    // for the bars, in time order, not sorted by value.
    private static void checkAutoInterval(final ChartKind kind,
                                          final Map<String, String> mapped,
                                          final Map<String, String> style,
                                          final List<TableColumn> columns,
                                          final Sample sample,
                                          final Result result) {
        if (!"true".equals(NullSafe.map(style).get(StyleSetting.AUTO_INTERVAL.id()))) {
            return;
        }
        final String role = kind == ChartKind.LINE
                ? "x"
                : "category";
        final TableColumn column = findColumn(columns, mapped.get(role)).orElse(null);
        final boolean dates = column != null
                              && ("DATE_TIME".equals(NullSafe.string(column.format()).toUpperCase(Locale.ROOT))
                                  || Values.of(column.name(), sample).dates > 0);
        if (!dates) {
            result.warnings.add("Auto Interval groups the rows over time, so it does nothing here: " + role + " '"
                                + mapped.get(role) + "' isn't a Date Time column.");
        } else if (NullSafe.isNonBlankString(NullSafe.map(style).get(StyleSetting.SORT.id()))) {
            result.warnings.add("Auto Interval does nothing while the bars are sorted by value: they must be in time "
                                + "order. Leave sort blank.");
        }
    }

    // True if a column's expression already rounds its time to an interval (e.g. floorDay()).
    private static boolean isRounded(final String expression) {
        final String lower = NullSafe.string(expression).toLowerCase(Locale.ROOT);
        return lower.contains("floor") || lower.contains("round") || lower.contains("ceiling");
    }

    // The time a column's sampled dates span, if it holds any.
    private static Optional<Duration> span(final Sample sample, final String column) {
        final int index = sample.index(column);
        Instant min = null;
        Instant max = null;
        for (final List<String> row : sample.rows()) {
            try {
                final Instant time = Instant.parse(cell(row, index).strip());
                min = min == null || time.isBefore(min)
                        ? time
                        : min;
                max = max == null || time.isAfter(max)
                        ? time
                        : max;
            } catch (final DateTimeParseException e) {
                // Not a date: left out.
            }
        }
        return min == null
                ? Optional.empty()
                : Optional.of(Duration.between(min, max));
    }

    /// @param span The time a chart's data spans.
    /// @return The rounding function giving a few dozen to a few hundred intervals over it, or empty if it is
    /// short enough to chart as it is.
    static Optional<String> intervalFunction(final Duration span) {
        if (span.compareTo(Duration.ofDays(5 * 365)) > 0) {
            return Optional.of("floorMonth");
        } else if (span.compareTo(Duration.ofDays(365)) > 0) {
            return Optional.of("floorWeek");
        } else if (span.compareTo(Duration.ofDays(14)) > 0) {
            return Optional.of("floorDay");
        } else if (span.compareTo(Duration.ofHours(12)) > 0) {
            return Optional.of("floorHour");
        } else if (span.compareTo(Duration.ofMinutes(30)) > 0) {
            return Optional.of("floorMinute");
        }
        return Optional.empty();
    }

    private static String describe(final Duration span) {
        if (span.toDays() >= 2) {
            return span.toDays() + " days";
        }
        return Math.max(1, span.toHours()) + " hours";
    }

    // Nodes and links: a network graph's, a Sankey diagram's or a chord diagram's.
    private static void networkSize(final ChartKind kind,
                                    final Map<String, String> mapped,
                                    final Map<String, String> style,
                                    final Sample sample,
                                    final Result result) {
        final int sourceIndex = sample.index(mapped.get("source"));
        final int targetIndex = sample.index(mapped.get("target"));
        final Set<String> nodes = new HashSet<>();
        final Set<String> links = new HashSet<>();
        int selfLinks = 0;
        for (final List<String> row : sample.rows()) {
            final String source = cell(row, sourceIndex);
            final String target = cell(row, targetIndex);
            if (source.isBlank() || target.isBlank()) {
                continue;
            }
            if (source.equals(target)) {
                selfLinks++;
                continue;
            }
            nodes.add(source);
            nodes.add(target);
            links.add(source + "\u0000" + target);
        }
        final int maxNodes = intStyle(style, StyleSetting.MAX_NODES);
        result.stats.put("nodes", nodes.size());
        result.stats.put("links", links.size());
        final String chart = kind == ChartKind.GRAPH
                ? "network"
                : kind.displayName().toLowerCase(Locale.ROOT);
        if (links.isEmpty()) {
            result.problems.add("None of the rows sampled link two different nodes (each has a blank source or "
                                + "target, or the same one in both), so there's no " + chart + " to draw.");
        } else if (nodes.size() > maxNodes) {
            result.warnings.add(nodes.size() + " nodes: only the " + maxNodes + " most connected will be drawn "
                                + "(Max Nodes). Filter or group the table first, or raise Max Nodes"
                                + (kind == ChartKind.GRAPH
                    ? ", which makes the layout slower."
                    : "."));
        }
        if (selfLinks > 0) {
            result.warnings.add(selfLinks + " rows link a node to itself and won't be drawn.");
        }
        if (kind == ChartKind.SANKEY && hasLoop(links)) {
            result.warnings.add("Some flows loop back (e.g. a to b and b to a): a Sankey diagram can't draw a "
                                + "loop, so the flows closing one will be left out. A chord diagram or network "
                                + "graph draws them all.");
        }
    }

    // Whether links (source and target joined by \u0000) make a loop.
    private static boolean hasLoop(final Set<String> links) {
        final Map<String, List<String>> next = new HashMap<>();
        for (final String link : links) {
            final String[] ends = link.split("\u0000", 2);
            next.computeIfAbsent(ends[0], key -> new ArrayList<>()).add(ends[1]);
        }
        final Set<String> done = new HashSet<>();
        final Set<String> onPath = new HashSet<>();
        for (final String start : next.keySet()) {
            if (loopsFrom(start, next, done, onPath)) {
                return true;
            }
        }
        return false;
    }

    private static boolean loopsFrom(final String node,
                                     final Map<String, List<String>> next,
                                     final Set<String> done,
                                     final Set<String> onPath) {
        if (onPath.contains(node)) {
            return true;
        }
        if (!done.add(node)) {
            return false;
        }
        onPath.add(node);
        for (final String to : next.getOrDefault(node, List.of())) {
            if (loopsFrom(to, next, done, onPath)) {
                return true;
            }
        }
        onPath.remove(node);
        return false;
    }

    private static void seriesWarning(final Map<String, String> mapped,
                                      final Sample sample,
                                      final int maxSeries,
                                      final Result result) {
        if (mapped.containsKey("series")) {
            final int series = distinct(sample, mapped.get("series"));
            result.stats.put("series", series);
            if (series > maxSeries) {
                result.warnings.add(series + " series: only the " + maxSeries + " with the largest totals will "
                                    + "be drawn (Max Series).");
            }
        }
    }

    private static int intStyle(final Map<String, String> style, final StyleSetting setting) {
        final String value = NullSafe.get(style, map -> map.get(setting.id()));
        try {
            return Integer.parseInt(value != null
                    ? value
                    : setting.defaultValue());
        } catch (final NumberFormatException e) {
            return Integer.parseInt(setting.defaultValue());
        }
    }

    private static int distinct(final Sample sample, final String column) {
        final int index = sample.index(column);
        final Set<String> values = new HashSet<>();
        for (final List<String> row : sample.rows()) {
            values.add(cell(row, index));
        }
        return values.size();
    }

    private static String cell(final List<String> row, final int index) {
        return index >= 0 && index < row.size() && row.get(index) != null
                ? row.get(index)
                : "";
    }

    private static Optional<TableColumn> findColumn(final List<TableColumn> columns, final String name) {
        return columns.stream()
                .filter(column -> column.name().equals(name))
                .findFirst();
    }

    private static String describeRoles(final ChartKind kind) {
        return String.join(", ", kind.roles().stream()
                .map(role -> role.id() + (role.required()
                        ? ""
                        : " (optional)"))
                .toList());
    }

    private static String describeColumns(final List<TableColumn> columns) {
        return columns.isEmpty()
                ? "(none)"
                : String.join(", ", columns.stream()
                        .map(column -> "'" + column.name() + "'")
                        .toList());
    }

    // ---------------------------------------------------------------------------------------------------------

    /// A column of the table a chart is for.
    ///
    /// @param name       Its name, as the table shows it.
    /// @param format     Its format's type, e.g. NUMBER, DATE_TIME, TEXT or GENERAL, or null if not known (a
    ///                   StroomQL query's columns).
    /// @param expression Its expression, or null if not known (a StroomQL query's columns).
    /// @param group      The level it is grouped at, from 0 for the top level, or null if it isn't grouped (or not
    ///                   known).
    public record TableColumn(String name, String format, String expression, Integer group) {

        /// A column whose expression and grouping aren't known.
        ///
        /// @param name   Its name, as the table shows it.
        /// @param format Its format's type, or null if not known.
        public TableColumn(final String name, final String format) {
            this(name, format, null, null);
        }

        /// A column that isn't grouped.
        ///
        /// @param name       Its name, as the table shows it.
        /// @param format     Its format's type, or null if not known.
        /// @param expression Its expression, or null if not known.
        public TableColumn(final String name, final String format, final String expression) {
            this(name, format, expression, null);
        }
    }

    /// A sample of a table's rows, as text.
    ///
    /// @param header The column names.
    /// @param rows   The rows.
    public record Sample(List<String> header, List<List<String>> rows) {

        /// @param column A column's name.
        /// @return Its index, or -1.
        public int index(final String column) {
            return header.indexOf(column);
        }
    }

    /// What the check found.
    public static final class Result {

        private final List<String> problems = new ArrayList<>();
        private final List<String> warnings = new ArrayList<>();
        private final Map<String, Integer> stats = new LinkedHashMap<>();

        /// @return What stops the chart being drawn, each saying what to do about it.
        public List<String> problems() {
            return problems;
        }

        /// @return What the chart will leave out or show badly, but can still draw.
        public List<String> warnings() {
            return warnings;
        }

        /// @return Counts from the sample, e.g. nodes and links.
        public Map<String, Integer> stats() {
            return stats;
        }

        /// @return True if the chart can be drawn.
        public boolean ok() {
            return problems.isEmpty();
        }
    }

    // How the sampled values of one column look.
    private record Values(int rows, int nonBlank, int numbers, int dates) {

        static Values of(final String column, final Sample sample) {
            if (sample == null) {
                return new Values(0, 0, 0, 0);
            }
            final int index = sample.index(column);
            int nonBlank = 0;
            int numbers = 0;
            int dates = 0;
            for (final List<String> row : sample.rows()) {
                final String value = cell(row, index);
                if (value.isBlank()) {
                    continue;
                }
                nonBlank++;
                if (NUMBER.matcher(value).matches()) {
                    numbers++;
                } else if (DATE.matcher(value).matches()) {
                    dates++;
                }
            }
            return new Values(sample.rows().size(), nonBlank, numbers, dates);
        }

        double numberShare() {
            return nonBlank == 0
                    ? 1
                    : (double) numbers / nonBlank;
        }
    }
}
