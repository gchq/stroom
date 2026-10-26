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
import stroom.ai.impl.vis.ChartCheck;
import stroom.ai.impl.vis.ChartCheck.Sample;
import stroom.ai.impl.vis.ChartCheck.TableColumn;
import stroom.ai.impl.vis.ChartKind;
import stroom.ai.impl.vis.ChartRole;
import stroom.ai.impl.vis.EChartsTemplate;
import stroom.util.shared.NullSafe;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/// What converting a chart Stroom AI generated to another kind (propose_visualisation's replace) does: to the
/// dashboard's selection filters its clicks drive, which follow it where the new chart selects the same column
/// under another key (e.g. a line chart's x as a bar chart's category), and to what it showed. And, for a chart
/// the table can't make, what the table lacks and the kinds it can make, to recommend instead.
final class ChartConversions {

    /// The setting a generated chart's kind is held in.
    static final String KIND = EChartsTemplate.KIND_CONTROL;
    /// The selection keys of a clicked date's time, or a time range dragged along a time axis.
    static final List<String> TIME_KEYS = List.of("from", "to");
    /// The most kinds recommended.
    static final int MAX_SUGGESTIONS = 5;

    private static final Pattern HEADING = Pattern.compile("^## (.+?) '(.*)' \\(id ([^)]+)\\)$");
    private static final Pattern FILTER_LINE = Pattern.compile("^(?:Query|Table) selection filters: (.*)$");
    private static final Pattern FILTER = Pattern.compile("\\[id ([^\\]\\s]+)] (.*?)(?=; \\[id |$)");

    private ChartConversions() {
        // Static methods only.
    }

    /// @param pane     A vis component showing a chart Stroom AI generated, as the dashboard's description gives
    ///                 it.
    /// @param defaults The settings of the Visualisation it shows (`ChartLibraries.generatedChart`).
    /// @return The pane, its settings those it draws with: the Visualisation's, overridden by the pane's own.
    static Component drawnWith(final Component pane, final Map<String, String> defaults) {
        final Map<String, String> settings = new LinkedHashMap<>(NullSafe.map(defaults));
        NullSafe.map(pane.settings()).forEach((key, value) -> {
            if (NullSafe.isNonBlankString(value)) {
                settings.put(key, value);
            }
        });
        return new Component(pane.type(), pane.name(), pane.id(), pane.dataSource(), pane.visualisation(),
                pane.table(), settings);
    }

    /// @param chart A chart, its settings those it draws with ([#drawnWith(Component, Map)]).
    /// @return Its kind, if known.
    static Optional<ChartKind> kindOf(final Component chart) {
        return ChartKind.find(NullSafe.map(chart.settings()).get(KIND));
    }

    /// @param chart A chart, its settings those it draws with.
    /// @return The column mapped to each of its kind's roles, by role id; empty if its kind isn't known.
    static Map<String, String> mappingsOf(final Component chart) {
        final Map<String, String> mappings = new LinkedHashMap<>();
        kindOf(chart).ifPresent(kind -> kind.roles().forEach(role -> {
            final String column = NullSafe.map(chart.settings()).get(role.id());
            if (NullSafe.isNonBlankString(column)) {
                mappings.put(role.id(), column.strip());
            }
        }));
        return mappings;
    }

    /// @param dashboardText The dashboard's description.
    /// @param chartId       A chart's component id.
    /// @return The selection filters the chart's clicks drive, with the keys of its selection each uses.
    static List<SelectionFilter> filtersOn(final String dashboardText, final String chartId) {
        final Pattern keyPattern = Pattern.compile(Pattern.quote("${component." + chartId + ".selection.")
                                                   + "([^}]+)}");
        final List<SelectionFilter> filters = new ArrayList<>();
        String component = null;
        for (final String line : NullSafe.string(dashboardText).split("\n")) {
            final Matcher heading = HEADING.matcher(line);
            if (heading.matches()) {
                component = heading.group(2);
                continue;
            }
            final Matcher filterLine = FILTER_LINE.matcher(line);
            if (component == null || !filterLine.matches()) {
                continue;
            }
            final Matcher filter = FILTER.matcher(filterLine.group(1));
            while (filter.find()) {
                final Set<String> keys = new LinkedHashSet<>();
                final Matcher key = keyPattern.matcher(filter.group(2));
                while (key.find()) {
                    keys.add(key.group(1));
                }
                if (!keys.isEmpty()) {
                    filters.add(new SelectionFilter(component, filter.group(1), keys));
                }
            }
        }
        return filters;
    }

    /// Works out what converting a chart to another kind (or the same kind with other columns) does.
    ///
    /// @param old           The chart replaced, its settings those it draws with ([#drawnWith(Component, Map)]).
    /// @param kind          The new chart's kind.
    /// @param mappings      The column mapped to each of its roles, by role id.
    /// @param columns       The table's columns.
    /// @param sample        A sample of the table's rows, or null.
    /// @param dashboardText The dashboard's description.
    /// @return The selection keys renamed for the new chart, and what to tell the user.
    static Conversion convert(final Component old,
                              final ChartKind kind,
                              final Map<String, String> mappings,
                              final List<TableColumn> columns,
                              final Sample sample,
                              final String dashboardText) {
        final ChartKind oldKind = kindOf(old).orElse(null);
        final Map<String, String> oldMappings = mappingsOf(old);
        final Component chart = withMappings(old, kind, mappings);
        final Set<String> keys = selectedKeys(chart, dashboardText);
        final String oldName = oldKind == null
                ? "old chart"
                : oldKind.displayName().toLowerCase(Locale.ROOT);
        final String newName = kind.displayName().toLowerCase(Locale.ROOT);

        final Map<String, String> renamed = new LinkedHashMap<>();
        final List<String> notes = new ArrayList<>();
        final List<String> warnings = new ArrayList<>();
        for (final SelectionFilter filter : filtersOn(dashboardText, old.id())) {
            final List<String> followed = new ArrayList<>();
            final List<String> lost = new ArrayList<>();
            for (final String key : filter.keys()) {
                if (keys.contains(key)) {
                    continue;
                }
                final String to = renamedKey(key, oldMappings, mappings, keys);
                if (to != null) {
                    renamed.put(key, to);
                    followed.add(key + " is now " + to);
                } else {
                    lost.add(key);
                }
            }
            final String which = "Selection filter [id " + filter.id() + "] on '" + filter.component() + "'";
            if (!followed.isEmpty()) {
                notes.add(which + " follows the new chart: " + String.join(", ", followed) + ".");
            }
            if (!lost.isEmpty()) {
                warnings.add(which + " uses the " + oldName + "'s " + describeKeys(lost) + ", which a click on "
                             + "the " + newName + " doesn't select" + (keys.isEmpty()
                        ? ""
                        : " (it selects: " + String.join(", ", keys) + ")") + ", so it stops filtering. It is "
                             + "left as it is: offer to remove it (propose_dashboard_change, "
                             + "remove_selection_filter), and to add one on what the new chart selects.");
            }
        }

        // What the old chart showed that the new one doesn't.
        if (oldKind != null) {
            final Set<String> shown = mappings.values().stream()
                    .map(column -> column.toLowerCase(Locale.ROOT))
                    .collect(Collectors.toSet());
            oldMappings.forEach((role, column) -> {
                if (!shown.contains(column.toLowerCase(Locale.ROOT))) {
                    warnings.add("'" + column + "' (the " + oldName + "'s " + role + ") isn't in the " + newName
                                 + ".");
                }
            });
            if (timeAxis(oldKind, oldMappings, columns, sample) && !timeAxis(kind, mappings, columns, sample)) {
                warnings.add("The " + oldName + "'s time axis goes, with its zoom and the time range dragged "
                             + "across it" + (kind == ChartKind.BAR
                        ? ": a bar chart draws each time as a bar of its own"
                        : "") + ".");
            }
        }
        if (kind == ChartKind.LINE && oldKind != ChartKind.LINE
            && !timeAxis(kind, mappings, columns, sample)) {
            warnings.add("x '" + mappings.get("x") + "' isn't a Date Time column, so this line chart isn't a "
                         + "time series: it joins one category to the next, as if they were in order. "
                         + timeColumnHint() + " Or recommend a bar chart of these columns.");
        }
        return new Conversion(renamed, notes, warnings);
    }

    /// @param kind    A chart kind.
    /// @param columns The table's columns.
    /// @param sample  A sample of the table's rows, or null.
    /// @return For each of the kind's required roles that no column of the table can be, what it needs; empty if
    /// the table has a column for every one.
    static List<String> lacks(final ChartKind kind, final List<TableColumn> columns, final Sample sample) {
        final List<String> lacks = new ArrayList<>();
        for (final ChartRole role : kind.roles()) {
            if (role.required() && columns.stream()
                    .noneMatch(column -> fits(role, holds(column, sample)))) {
                lacks.add(describe(role.values()) + " column for " + role.id() + " (" + role.description()
                          + ")");
            }
        }
        return lacks;
    }

    /// @param columns The table's columns.
    /// @param sample  A sample of the table's rows, or null.
    /// @return True if a column holds dates, for a chart over time.
    static boolean hasDates(final List<TableColumn> columns, final Sample sample) {
        return columns.stream()
                .anyMatch(column -> holds(column, sample).filter(ChartRole.Values.DATE::equals).isPresent());
    }

    /// @return How to give a table a time to chart: for recommending when a time series is asked for.
    static String timeColumnHint() {
        return "For a time series, the table needs a Date Time column: a time field rounded to an interval (e.g. "
               + "floorHour(EventTime) or floorDay(EventTime)), with the Date Time format, grouped, and the "
               + "values counted (or summed) per interval. Offer that table change (propose_dashboard_change) "
               + "first.";
    }

    /// Finds the kinds of chart a table can make as it is, choosing their columns: the columns given first, by the
    /// same role and then by any, so a chart converted keeps what it showed where it can.
    ///
    /// @param columns   The table's columns.
    /// @param sample    A sample of the table's rows, or null.
    /// @param preferred Columns to choose first, by the role they had (e.g. the chart replaced's); may be empty.
    /// @param leaveOut  Kinds not to suggest, e.g. the one asked for.
    /// @return Each kind the table can make with the columns chosen, best first: those keeping most of the
    /// preferred columns (in the same role, best), with fewest warnings; at most [#MAX_SUGGESTIONS].
    static List<Suggestion> suggestions(final List<TableColumn> columns,
                                        final Sample sample,
                                        final Map<String, String> preferred,
                                        final Set<ChartKind> leaveOut) {
        final List<Suggestion> suggestions = new ArrayList<>();
        for (final ChartKind kind : ChartKind.values()) {
            if (leaveOut.contains(kind)) {
                continue;
            }
            final Map<String, String> mappings = choose(kind, columns, sample, preferred);
            if (mappings.isEmpty()) {
                continue;
            }
            final ChartCheck.Result check = ChartCheck.check(kind, mappings, Map.of(), columns, sample);
            if (check.ok()) {
                // A column kept in the same role (e.g. a pie's name as a pie's name) counts twice.
                final long kept = mappings.entrySet().stream()
                        .mapToLong(entry -> NullSafe.map(preferred).containsValue(entry.getValue())
                                ? (entry.getValue().equals(NullSafe.map(preferred).get(entry.getKey()))
                                        ? 2
                                        : 1)
                                : 0)
                        .sum();
                suggestions.add(new Suggestion(kind, mappings, (int) kept, check.warnings().size()));
            }
        }
        return suggestions.stream()
                .sorted(Comparator.comparingInt(Suggestion::kept).reversed()
                        .thenComparingInt(Suggestion::warnings))
                .limit(MAX_SUGGESTIONS)
                .toList();
    }

    // The chart with the new kind's columns, for the keys a click on it selects.
    private static Component withMappings(final Component chart,
                                          final ChartKind kind,
                                          final Map<String, String> mappings) {
        final Map<String, String> settings = new LinkedHashMap<>();
        settings.put(KIND, kind.id());
        NullSafe.map(mappings).forEach((role, column) -> {
            if (NullSafe.isNonBlankString(column)) {
                settings.put(role, column.strip());
            }
        });
        return new Component(DashboardComponents.VISUALISATION, chart.name(), chart.id(), null,
                chart.visualisation(), chart.table(), settings);
    }

    // The keys a click on a chart selects, with a clicked date's time if it selects a date.
    private static Set<String> selectedKeys(final Component chart, final String dashboardText) {
        final Set<String> keys = ProposeDashboardChangeTool.selectionKeys(chart);
        keys.remove(ProposeDashboardChangeTool.RANGE_SELECTION);
        if (!ProposeDashboardChangeTool.dateKeys(chart, dashboardText).isEmpty()) {
            keys.addAll(TIME_KEYS);
        }
        return keys;
    }

    // The key a filter's key becomes on the new chart: the role it maps the old role's column to, if a click
    // selects it.
    private static String renamedKey(final String key,
                                     final Map<String, String> oldMappings,
                                     final Map<String, String> mappings,
                                     final Set<String> keys) {
        final String column = oldMappings.get(key);
        if (column == null) {
            return null;
        }
        return NullSafe.map(mappings).entrySet().stream()
                .filter(entry -> column.equalsIgnoreCase(NullSafe.string(entry.getValue()).strip()))
                .map(Map.Entry::getKey)
                .filter(keys::contains)
                .findFirst()
                .orElse(null);
    }

    private static String describeKeys(final List<String> keys) {
        final List<String> described = new ArrayList<>();
        if (keys.stream().anyMatch(TIME_KEYS::contains)) {
            described.add("selected time (from and to)");
        }
        keys.stream()
                .filter(key -> !TIME_KEYS.contains(key))
                .forEach(key -> described.add("'" + key + "'"));
        return "selection " + String.join(" and ", described);
    }

    // Whether a chart has a time axis: a line chart's or scatter plot's x of dates.
    private static boolean timeAxis(final ChartKind kind,
                                    final Map<String, String> mappings,
                                    final List<TableColumn> columns,
                                    final Sample sample) {
        if (kind != ChartKind.LINE && kind != ChartKind.SCATTER) {
            return false;
        }
        final String x = mappings.get("x");
        return columns.stream()
                .filter(column -> column.name().equals(x))
                .findFirst()
                .flatMap(column -> holds(column, sample))
                .filter(ChartRole.Values.DATE::equals)
                .isPresent();
    }

    // Chooses a column for each of a kind's required roles: empty if one can't be found.
    private static Map<String, String> choose(final ChartKind kind,
                                              final List<TableColumn> columns,
                                              final Sample sample,
                                              final Map<String, String> preferred) {
        final Map<String, String> chosen = new LinkedHashMap<>();
        for (final ChartRole role : kind.roles()) {
            if (!role.required()) {
                continue;
            }
            final List<TableColumn> candidates = new ArrayList<>();
            NullSafe.consume(NullSafe.map(preferred).get(role.id()), column -> columnNamed(columns, column)
                    .ifPresent(candidates::add));
            NullSafe.map(preferred).values().forEach(column -> columnNamed(columns, column)
                    .ifPresent(candidates::add));
            candidates.addAll(columns);
            // A line chart is recommended only over time: across categories, a bar chart reads better.
            final boolean time = kind == ChartKind.LINE && "x".equals(role.id());
            final Optional<TableColumn> column = candidates.stream()
                    .filter(candidate -> !chosen.containsValue(candidate.name()))
                    .filter(candidate -> time
                            ? holds(candidate, sample).filter(ChartRole.Values.DATE::equals).isPresent()
                            : fits(role, holds(candidate, sample)))
                    .findFirst();
            if (column.isEmpty()) {
                return Map.of();
            }
            chosen.put(role.id(), column.get().name());
        }
        return chosen;
    }

    private static Optional<TableColumn> columnNamed(final List<TableColumn> columns, final String name) {
        return columns.stream()
                .filter(column -> column.name().equalsIgnoreCase(NullSafe.string(name).strip()))
                .findFirst();
    }

    private static Optional<ChartRole.Values> holds(final TableColumn column, final Sample sample) {
        return ChartCheck.holds(column, sample);
    }

    // Whether a column holding these values suits a role: a number for a measure, a date for a date, and text
    // (or a date, or what isn't known) for what is picked out by name.
    private static boolean fits(final ChartRole role, final Optional<ChartRole.Values> holds) {
        return switch (role.values()) {
            case NUMBER -> holds.filter(ChartRole.Values.NUMBER::equals).isPresent();
            case DATE -> holds.filter(ChartRole.Values.DATE::equals).isPresent();
            case NUMBER_OR_DATE -> holds.filter(values -> values == ChartRole.Values.NUMBER
                                                          || values == ChartRole.Values.DATE).isPresent();
            case ANY -> holds.filter(ChartRole.Values.NUMBER::equals).isEmpty();
        };
    }

    private static String describe(final ChartRole.Values values) {
        return switch (values) {
            case NUMBER -> "number";
            case DATE -> "Date Time";
            case NUMBER_OR_DATE -> "number or Date Time";
            case ANY -> "text";
        };
    }

    // ---------------------------------------------------------------------------------------------------------

    /// A selection filter a chart's clicks drive.
    ///
    /// @param component The name of the component it filters.
    /// @param id        Its id, as the dashboard's description gives it.
    /// @param keys      The keys of the chart's selection it uses, e.g. x.
    record SelectionFilter(String component, String id, Set<String> keys) {

    }

    /// What converting a chart does.
    ///
    /// @param renamed  The keys of the old chart's selection its filters use, renamed for the new chart (e.g. x to
    ///                 category), by old key.
    /// @param notes    What follows the new chart, to tell the agent.
    /// @param warnings What it loses, for the agent to tell the user, with what to offer.
    record Conversion(Map<String, String> renamed, List<String> notes, List<String> warnings) {

    }

    /// A kind of chart a table can make.
    ///
    /// @param kind     The kind.
    /// @param mappings The column chosen for each of its required roles, by role id.
    /// @param kept     How many of the preferred columns it keeps, each kept in the same role counted twice.
    /// @param warnings How many warnings its check gave.
    record Suggestion(ChartKind kind, Map<String, String> mappings, int kept, int warnings) {

        /// @return The kind and its columns, e.g. `bar (category = 'UserId', value = 'Count')`.
        String describe() {
            return kind.id() + " (" + mappings.entrySet().stream()
                    .map(entry -> entry.getKey() + " = '" + entry.getValue() + "'")
                    .collect(Collectors.joining(", ")) + ")";
        }
    }
}
