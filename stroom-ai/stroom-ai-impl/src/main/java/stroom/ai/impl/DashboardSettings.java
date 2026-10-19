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

package stroom.ai.impl;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/// The settings of dashboard components that the agent can propose changing (see `propose_dashboard_change`'s
/// set_setting), as each component's Settings dialog has them, with what kind of value each takes. Only these can
/// be changed; each value is checked here before it is offered.
public final class DashboardSettings {

    /// The most rows a table may show on a page, or an integer setting may be.
    static final int MAX_INTEGER = 1_000_000;
    // A refresh interval, as the query's settings take it, e.g. 30s, 10m or 1h.
    private static final Pattern DURATION = Pattern.compile("[1-9][0-9]{0,4}[smhd]");

    /// The settings, by component type.
    public static final List<Setting> SETTINGS = List.of(
            new Setting(DashboardComponents.TABLE, "pageSize", Kind.INTEGER, "rows per page"),
            new Setting(DashboardComponents.TABLE, "maxResults", Kind.INTEGERS,
                    "the most rows at each group level, e.g. 1000,10 (the top level first)"),
            new Setting(DashboardComponents.TABLE, "showDetail", Kind.BOOLEAN,
                    "show the detail rows under grouped rows"),
            new Setting(DashboardComponents.TABLE, "extractValues", Kind.BOOLEAN,
                    "extract values with the extraction pipeline"),
            new Setting(DashboardComponents.TABLE, "extractionPipeline", Kind.PIPELINE,
                    "the extraction pipeline (its UUID)"),
            new Setting(DashboardComponents.TABLE, "showValueFilters", Kind.BOOLEAN,
                    "show the row of column filters"),
            new Setting(DashboardComponents.TABLE, "query", Kind.QUERY_COMPONENT,
                    "the query that feeds it (a query component's name)"),
            new Setting(DashboardComponents.TABLE, "maxStringFieldLength", Kind.INTEGER,
                    "the longest text a cell shows"),
            new Setting(DashboardComponents.QUERY, "dataSource", Kind.DATA_SOURCE,
                    "the data source (its name or UUID)"),
            new Setting(DashboardComponents.QUERY, "queryOnOpen", Kind.BOOLEAN,
                    "run the query when the dashboard opens"),
            new Setting(DashboardComponents.QUERY, "autoRefresh", Kind.BOOLEAN, "run the query again on a timer"),
            new Setting(DashboardComponents.QUERY, "refreshInterval", Kind.DURATION,
                    "how often to refresh, e.g. 30s, 10m or 1h"),
            new Setting(DashboardComponents.TEXT, "table", Kind.TABLE_COMPONENT,
                    "the table whose selected row it shows (a table's name, or none for any)"),
            new Setting(DashboardComponents.TEXT, "pipeline", Kind.PIPELINE_OR_NONE,
                    "the pipeline it shows the data through (its UUID, or none for the raw data)"),
            new Setting(DashboardComponents.TEXT, "showAsHtml", Kind.BOOLEAN, "show the data as HTML"),
            new Setting(DashboardComponents.TEXT, "showStepping", Kind.BOOLEAN, "show the stepping button"));

    /// The value given for no document or component.
    public static final String NONE = "none";

    private DashboardSettings() {
        // Static methods only.
    }

    /// @param componentType A component's type, e.g. [DashboardComponents#TABLE].
    /// @param key           A setting's key.
    /// @return The setting, if a component of the type has it.
    public static Optional<Setting> find(final String componentType, final String key) {
        return SETTINGS.stream()
                .filter(setting -> setting.componentType().equals(componentType) && setting.key().equals(key))
                .findFirst();
    }

    /// @param componentType A component's type.
    /// @return The keys of the settings a component of the type has.
    public static List<String> keys(final String componentType) {
        return SETTINGS.stream()
                .filter(setting -> setting.componentType().equals(componentType))
                .map(Setting::key)
                .toList();
    }

    /// @param setting A setting.
    /// @param value   Its value as given.
    /// @return The value as the browser takes it: true or false, digits, digits separated by commas, or a duration.
    /// Documents and components are resolved by the caller, so are given back as they are.
    /// @throws IllegalArgumentException saying why, if the value isn't one the setting takes.
    public static String normalise(final Setting setting, final String value) {
        final String text = value == null
                ? ""
                : value.strip();
        return switch (setting.kind()) {
            case BOOLEAN -> switch (text.toLowerCase(Locale.ROOT)) {
                case "true", "yes", "on" -> "true";
                case "false", "no", "off" -> "false";
                default -> throw new IllegalArgumentException(setting.key() + " takes true or false.");
            };
            case INTEGER -> String.valueOf(integer(setting, text));
            case INTEGERS -> {
                final List<String> parts = List.of(text.split("\\s*,\\s*"));
                if (text.isEmpty() || parts.size() > 10) {
                    throw new IllegalArgumentException(setting.key() + " takes up to 10 whole numbers, separated "
                                                       + "by commas.");
                }
                yield String.join(",", parts.stream().map(part -> String.valueOf(integer(setting, part))).toList());
            }
            case DURATION -> {
                if (!DURATION.matcher(text).matches()) {
                    throw new IllegalArgumentException(setting.key() + " takes a duration such as 30s, 10m or 1h.");
                }
                yield text;
            }
            default -> {
                if (text.isEmpty()) {
                    throw new IllegalArgumentException(setting.key() + " needs a value.");
                }
                yield text;
            }
        };
    }

    /// @param setting A setting.
    /// @param value   Its value, as [#normalise(Setting, String)] gives it.
    /// @return The value in plain language, for the user.
    public static String describe(final Setting setting, final String value) {
        return switch (setting.kind()) {
            case BOOLEAN -> "true".equals(value)
                    ? "on"
                    : "off";
            default -> value;
        };
    }

    private static int integer(final Setting setting, final String text) {
        try {
            final int number = Integer.parseInt(text);
            if (number < 1 || number > MAX_INTEGER) {
                throw new NumberFormatException();
            }
            return number;
        } catch (final NumberFormatException e) {
            throw new IllegalArgumentException(setting.key() + " takes a whole number from 1 to " + MAX_INTEGER
                                               + ".");
        }
    }


    // --------------------------------------------------------------------------------


    /// A setting the agent can change.
    ///
    /// @param componentType The type of component that has it.
    /// @param key           Its key, as the model gives it.
    /// @param kind          What kind of value it takes.
    /// @param label         What it is, for the model and the user.
    public record Setting(String componentType, String key, Kind kind, String label) {

    }

    /// What kind of value a setting takes.
    public enum Kind {
        BOOLEAN,
        INTEGER,
        /// Whole numbers separated by commas.
        INTEGERS,
        DURATION,
        /// A Pipeline, by its UUID.
        PIPELINE,
        /// A Pipeline, by its UUID, or none.
        PIPELINE_OR_NONE,
        /// A data source, by its name or UUID.
        DATA_SOURCE,
        /// A query component, by its name.
        QUERY_COMPONENT,
        /// A table component, by its name, or none.
        TABLE_COMPONENT
    }
}
