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

import stroom.util.json.JsonUtil;
import stroom.util.shared.NullSafe;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/// Builds the settings JSON of a Visualisation drawn by the ECharts Generic adapter, from a chart kind, the
/// columns mapped to its roles and the style settings chosen. The agent never writes this JSON itself.
///
/// The settings have four tabs: Data (a field control per role), Style, Limits and Advanced (the kind, the order of
/// the fields in each data row, and ECharts option overrides). Every value chosen is the control's default, so the
/// Visualisation draws as planned with no settings of its own on a dashboard, and a StroomQL `show` clause needs no
/// arguments. The data structure is flat: each row holds every role's column, in role order.
public final class EChartsTemplate {

    /// The adapter's vis class, as the Visualisation's function name.
    public static final String FUNCTION_NAME = "visualisations.EChartsGeneric";

    /// The control holding the chart's kind.
    public static final String KIND_CONTROL = "kind";
    /// The control holding the order of the fields in each data row.
    public static final String FIELDS_CONTROL = "fields";
    static final String OPTIONS_CONTROL = "options";
    /// The controls every template has besides its kind's roles and style settings.
    static final List<String> CONTROLS = List.of(KIND_CONTROL, FIELDS_CONTROL, OPTIONS_CONTROL);
    /// The control type Stroom shows as a code editor for a JSON value, indented to read and edit.
    static final String JSON_CONTROL_TYPE = "json";
    /// The options control's label, which names no charting library: any library's charts can take their options
    /// the same way.
    static final String OPTIONS_LABEL = "Chart Options (JSON)";
    /// The options control's value with no overrides: an empty JSON object, so its editor shows the user what it
    /// takes, rather than an empty box that looks like a fault.
    static final String NO_OPTIONS = "{}";

    private EChartsTemplate() {
        // Static only.
    }

    /// Builds the settings JSON.
    ///
    /// @param kind        The chart kind.
    /// @param mappings    The column mapped to each role, by role id; roles left out or blank are unmapped.
    /// @param style       The style settings chosen, by setting id, already normalised; others take their
    ///                    defaults.
    /// @param optionsJson ECharts option overrides as a JSON object, or null for none.
    /// @return The settings JSON.
    public static String settingsJson(final ChartKind kind,
                                      final Map<String, String> mappings,
                                      final Map<String, String> style,
                                      final String optionsJson) {
        final List<Map<String, Object>> dataControls = new ArrayList<>();
        for (final ChartRole role : kind.roles()) {
            final Map<String, Object> control = control(role.id(), "field", role.label());
            final String column = NullSafe.get(mappings, map -> map.get(role.id()));
            if (NullSafe.isNonBlankString(column)) {
                control.put("defaultValue", column);
            }
            dataControls.add(control);
        }

        final List<Map<String, Object>> styleControls = new ArrayList<>();
        final List<Map<String, Object>> limitControls = new ArrayList<>();
        for (final StyleSetting setting : kind.styleSettings()) {
            final Map<String, Object> control = control(setting.id(), setting.control().type(), setting.label());
            final String chosen = NullSafe.get(style, map -> map.get(setting.id()));
            control.put("defaultValue", chosen != null
                    ? chosen
                    : setting.defaultValue());
            if (!setting.choices().isEmpty()) {
                control.put("values", setting.choices());
            }
            if (setting.min() != null) {
                control.put("min", Integer.toString(setting.min()));
            }
            if (setting.max() != null) {
                control.put("max", Integer.toString(setting.max()));
            }
            if (setting.isLimit()) {
                limitControls.add(control);
            } else {
                styleControls.add(control);
            }
        }

        final List<Map<String, Object>> advancedControls = new ArrayList<>();
        final Map<String, Object> kindControl = control(KIND_CONTROL, "text", "Chart Kind");
        kindControl.put("defaultValue", kind.id());
        advancedControls.add(kindControl);
        final Map<String, Object> fieldsControl = control(FIELDS_CONTROL, "text", "Field Order");
        fieldsControl.put("defaultValue", fieldOrder(kind));
        advancedControls.add(fieldsControl);
        final Map<String, Object> optionsControl = control(OPTIONS_CONTROL, JSON_CONTROL_TYPE, OPTIONS_LABEL);
        optionsControl.put("defaultValue", NullSafe.isNonBlankString(optionsJson)
                ? optionsJson.strip()
                : NO_OPTIONS);
        advancedControls.add(optionsControl);

        final List<Map<String, Object>> tabs = new ArrayList<>();
        tabs.add(tab("Data", dataControls));
        tabs.add(tab("Style", styleControls));
        tabs.add(tab("Limits", limitControls));
        tabs.add(tab("Advanced", advancedControls));

        final List<Map<String, Object>> fields = kind.roles().stream()
                .map(role -> {
                    final Map<String, Object> field = new LinkedHashMap<>();
                    field.put("id", "${" + role.id() + "}");
                    return field;
                })
                .collect(Collectors.toList());
        final Map<String, Object> limit = new LinkedHashMap<>();
        limit.put("enabled", "true");
        limit.put("size", "${" + StyleSetting.MAX_VALUES.id() + "}");
        final Map<String, Object> values = new LinkedHashMap<>();
        values.put("fields", fields);
        values.put("limit", limit);
        final Map<String, Object> structure = new LinkedHashMap<>();
        structure.put("values", values);
        final Map<String, Object> data = new LinkedHashMap<>();
        data.put("structure", structure);

        final Map<String, Object> settings = new LinkedHashMap<>();
        settings.put("tabs", tabs);
        settings.put("data", data);
        return JsonUtil.writeValueAsString(settings, true);
    }

    /// Reads a generated Visualisation's settings back: each control's default value, by its id, e.g. its chart
    /// kind and the column mapped to each role.
    ///
    /// @param settingsJson A generated Visualisation's settings JSON.
    /// @return The default value of each control that has one, in the order given; empty if they can't be read.
    public static Map<String, String> defaults(final String settingsJson) {
        final Map<String, String> defaults = new LinkedHashMap<>();
        controls(settingsJson).forEach(control -> {
            if (control.get("id") instanceof final String id && control.get("defaultValue") != null) {
                defaults.put(id, String.valueOf(control.get("defaultValue")));
            }
        });
        return defaults;
    }

    /// Builds a generated Visualisation's settings again with the template as it is now (e.g. with controls added
    /// since it was generated), keeping what was chosen: its chart kind, the column mapped to each role, each style
    /// setting and its options.
    ///
    /// @param settingsJson A generated Visualisation's settings JSON.
    /// @return The settings JSON, or empty if its chart kind can't be read from them.
    public static Optional<String> rebuilt(final String settingsJson) {
        final Map<String, String> defaults = defaults(settingsJson);
        return ChartKind.find(defaults.get(KIND_CONTROL)).map(kind -> {
            final Map<String, String> mappings = new LinkedHashMap<>();
            kind.roles().forEach(role -> NullSafe.consume(defaults.get(role.id()),
                    column -> mappings.put(role.id(), column)));
            final Map<String, String> style = new LinkedHashMap<>();
            kind.styleSettings().forEach(setting -> NullSafe.consume(defaults.get(setting.id()),
                    value -> style.put(setting.id(), value)));
            return settingsJson(kind, mappings, style, defaults.get(OPTIONS_CONTROL));
        });
    }

    /// Reads the choices each selection control of a generated Visualisation's settings offers, as they were when
    /// it was generated: a choice added since (e.g. a nodeColours of cluster) isn't among them, and the adapter it
    /// was generated with doesn't draw it.
    ///
    /// @param settingsJson A generated Visualisation's settings JSON.
    /// @return Each selection control's choices, by control id; empty if the settings can't be read.
    public static Map<String, List<String>> choices(final String settingsJson) {
        final Map<String, List<String>> choices = new LinkedHashMap<>();
        controls(settingsJson).forEach(control -> {
            if (control.get("id") instanceof final String id
                && control.get("values") instanceof final List<?> values) {
                choices.put(id, values.stream().map(String::valueOf).toList());
            }
        });
        return choices;
    }

    // Every control on every tab of a Visualisation's settings; none if they can't be read.
    private static List<Map<?, ?>> controls(final String settingsJson) {
        if (NullSafe.isBlankString(settingsJson)) {
            return List.of();
        }
        final Map<?, ?> settings;
        try {
            settings = JsonUtil.readValue(settingsJson, Map.class);
        } catch (final RuntimeException e) {
            return List.of();
        }
        final List<Map<?, ?>> found = new ArrayList<>();
        for (final Object tab : settings != null && settings.get("tabs") instanceof final List<?> tabs
                ? tabs
                : List.of()) {
            final Object controls = tab instanceof final Map<?, ?> tabMap
                    ? tabMap.get("controls")
                    : null;
            for (final Object item : controls instanceof final List<?> list
                    ? list
                    : List.of()) {
                if (item instanceof final Map<?, ?> control) {
                    found.add(control);
                }
            }
        }
        return found;
    }

    /// @param kind The chart kind.
    /// @return The role ids in the order the adapter receives their values, comma separated.
    public static String fieldOrder(final ChartKind kind) {
        return kind.roles().stream()
                .map(ChartRole::id)
                .collect(Collectors.joining(","));
    }

    private static Map<String, Object> control(final String id, final String type, final String label) {
        final Map<String, Object> control = new LinkedHashMap<>();
        control.put("id", id);
        control.put("type", type);
        control.put("label", label);
        return control;
    }

    private static Map<String, Object> tab(final String name, final List<Map<String, Object>> controls) {
        final Map<String, Object> tab = new LinkedHashMap<>();
        tab.put("name", name);
        tab.put("controls", controls);
        return tab;
    }
}
