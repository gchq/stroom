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

import stroom.docref.DocRef;
import stroom.util.json.JsonUtil;
import stroom.util.shared.NullSafe;
import stroom.visualisation.shared.VisualisationDoc;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

/// A chart the agent plans to generate with ECharts, as kept in a `create_visualisation` button until the user
/// clicks it: what to draw, from which table, and where the new Visualisation goes on the dashboard (or in the
/// query's `show` clause).
///
/// @param kind         The chart kind's id, e.g. `graph`.
/// @param mappings     The column mapped to each role, by role id.
/// @param style        The style settings chosen, by setting id.
/// @param options      ECharts option overrides as a JSON object, or null.
/// @param table        The name of the table it draws, as the user sees it; null for a StroomQL query.
/// @param componentId  The dashboard table's component id, or null for a StroomQL query; for an embedded query on a
///                     dashboard, its component id.
/// @param componentName The name of the new vis component on the dashboard, or null for a StroomQL query.
/// @param placement    Where it goes on the dashboard: below the table or as a tab beside it.
/// @param snapshotHash The hash of the dashboard description it was planned against, or of the query's text.
/// @param query        True if it is for a StroomQL query's `show` clause rather than a dashboard.
/// @param baseQuery    For a StroomQL query, its text when the chart was planned, else null.
/// @param replace      For a chart replacing one Stroom AI generated earlier on the dashboard, which one; else null
///                     for a new chart. [#componentName()] is then the vis component's name once replaced.
@JsonInclude(Include.NON_NULL)
public record VisualisationPlan(@JsonProperty("kind") String kind,
                                @JsonProperty("mappings") Map<String, String> mappings,
                                @JsonProperty("style") Map<String, String> style,
                                @JsonProperty("options") String options,
                                @JsonProperty("table") String table,
                                @JsonProperty("componentId") String componentId,
                                @JsonProperty("componentName") String componentName,
                                @JsonProperty("placement") String placement,
                                @JsonProperty("snapshotHash") String snapshotHash,
                                @JsonProperty("query") Boolean query,
                                @JsonProperty("baseQuery") String baseQuery,
                                @JsonProperty("replace") Replace replace) {

    /// Places a new vis component in a new pane below its table.
    public static final String BELOW = "below";
    /// Places a new vis component as a tab beside its table.
    public static final String TAB = "tab";
    static final Set<String> PLACEMENTS = Set.of(BELOW, TAB);
    static final int MAX_TEXT = 1_000;
    static final int MAX_OPTIONS = 20_000;

    /// The top-level ECharts option keys the adapter accepts as overrides (kept in step with
    /// `ALLOWED_TOP_LEVEL_KEYS` in `echarts-generic.js`).
    static final Set<String> OPTION_KEYS = Set.of("legend", "grid", "xAxis", "yAxis", "tooltip", "color",
            "textStyle", "animation", "animationDuration", "dataZoom", "seriesDefaults");

    /// Reads a plan from JSON, by its components.
    @JsonCreator
    public VisualisationPlan {
    }

    /// A plan for a new chart.
    ///
    /// @param kind          The chart kind's id.
    /// @param mappings      The column mapped to each role, by role id.
    /// @param style         The style settings chosen, by setting id.
    /// @param options       ECharts option overrides as a JSON object, or null.
    /// @param table         The name of the table it draws; null for a StroomQL query.
    /// @param componentId   The dashboard table's component id, or null for a StroomQL query.
    /// @param componentName The name of the new vis component, or null for a StroomQL query.
    /// @param placement     Where it goes on the dashboard.
    /// @param snapshotHash  The hash of what it was planned against.
    /// @param query         True if it is for a StroomQL query's `show` clause.
    /// @param baseQuery     For a StroomQL query, its text when the chart was planned, else null.
    public VisualisationPlan(final String kind,
                             final Map<String, String> mappings,
                             final Map<String, String> style,
                             final String options,
                             final String table,
                             final String componentId,
                             final String componentName,
                             final String placement,
                             final String snapshotHash,
                             final Boolean query,
                             final String baseQuery) {
        this(kind, mappings, style, options, table, componentId, componentName, placement, snapshotHash, query,
                baseQuery, null);
    }

    /// @return The plan as JSON, to keep in a button.
    public String toJson() {
        return JsonUtil.writeValueAsString(this, false);
    }

    /// Reads a plan kept in a button and checks it again, as the button's content is stored and read back.
    ///
    /// @param json The plan's JSON.
    /// @return The plan.
    /// @throws IllegalArgumentException if it isn't a valid plan.
    public static VisualisationPlan fromJson(final String json) {
        final VisualisationPlan plan;
        try {
            plan = JsonUtil.readValue(json, VisualisationPlan.class);
        } catch (final RuntimeException e) {
            throw new IllegalArgumentException("The visualisation's plan couldn't be read.", e);
        }
        if (plan == null) {
            throw new IllegalArgumentException("The visualisation's plan is empty.");
        }
        plan.validate();
        return plan;
    }

    /// @return The chart kind.
    public ChartKind chartKind() {
        return ChartKind.find(kind).orElseThrow(() -> new IllegalArgumentException("Unknown chart kind '" + kind
                                                                                   + "'"));
    }

    /// @return True if it is for a StroomQL query.
    public boolean isQuery() {
        return Boolean.TRUE.equals(query);
    }

    /// Checks the plan's own content: a known kind, known roles and settings with values they take, options that
    /// are a JSON object, and text within limits. Whether its columns exist is checked against the table by
    /// [ChartCheck].
    ///
    /// @throws IllegalArgumentException saying what is wrong.
    public void validate() {
        final ChartKind chartKind = chartKind();
        for (final Entry<String, String> entry : NullSafe.map(mappings).entrySet()) {
            if (chartKind.role(entry.getKey()).isEmpty()) {
                throw new IllegalArgumentException("A " + chartKind.displayName() + " has no '" + entry.getKey()
                                                   + "'.");
            }
            checkText("the column for " + entry.getKey(), entry.getValue());
        }
        normaliseStyle(chartKind, style);
        normaliseOptions(options);
        checkText("the table's name", table);
        checkText("the component's name", componentName);
        if (placement != null && !PLACEMENTS.contains(placement)) {
            throw new IllegalArgumentException("Placement must be " + BELOW + " or " + TAB + ".");
        }
        if (!isQuery() && (NullSafe.isBlankString(componentId) || NullSafe.isBlankString(componentName))) {
            throw new IllegalArgumentException("A dashboard's visualisation needs its table and a component "
                                               + "name.");
        }
        if (replace != null) {
            if (isQuery()) {
                throw new IllegalArgumentException("Only a chart on a dashboard can replace another.");
            }
            if (NullSafe.isBlankString(replace.componentId())
                || replace.visualisation() == null
                || !VisualisationDoc.TYPE.equals(replace.visualisation().getType())
                || NullSafe.isBlankString(replace.visualisation().getUuid())) {
                throw new IllegalArgumentException("A chart replacing another needs the vis component and the "
                                                   + "Visualisation it shows.");
            }
            checkText("the replaced component's name", replace.componentName());
        }
    }

    /// @return True if it is for the `show` clause of an embedded query on a dashboard, rather than a Query's.
    @JsonIgnore
    public boolean isEmbeddedQuery() {
        return isQuery() && NullSafe.isNonBlankString(componentId);
    }

    /// @return True if it replaces a chart Stroom AI generated earlier, rather than adding a new one.
    @JsonIgnore
    public boolean isReplacement() {
        return replace != null;
    }

    /// Checks style settings and puts their values in the form the visualisation takes.
    ///
    /// @param kind  The chart kind.
    /// @param style The settings chosen, by setting id.
    /// @return The settings, normalised.
    /// @throws IllegalArgumentException naming a setting the kind doesn't have or a value it doesn't take.
    public static Map<String, String> normaliseStyle(final ChartKind kind, final Map<String, String> style) {
        final Map<String, String> normalised = new LinkedHashMap<>();
        for (final Entry<String, String> entry : NullSafe.map(style).entrySet()) {
            final StyleSetting setting = kind.styleSetting(entry.getKey())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "A " + kind.displayName().toLowerCase(Locale.ROOT) + " has no setting '"
                            + entry.getKey() + "'. Its settings are: " + String.join(", ",
                                    kind.styleSettings().stream().map(StyleSetting::id).toList()) + "."));
            final String value = setting.normalise(entry.getValue())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "'" + entry.getValue() + "' isn't a value " + setting.id() + " takes: give "
                            + setting.describeValues() + "."));
            normalised.put(setting.id(), value);
        }
        return normalised;
    }

    /// Checks ECharts option overrides: a JSON object of at most [#MAX_OPTIONS] characters, whose top-level keys
    /// the adapter accepts. Keys that can carry HTML, code or URLs are removed by the adapter itself.
    ///
    /// @param options The overrides, or null.
    /// @return The overrides, compacted, or null if none.
    /// @throws IllegalArgumentException if they aren't a JSON object, or are too long.
    public static String normaliseOptions(final String options) {
        if (NullSafe.isBlankString(options)) {
            return null;
        }
        if (options.length() > MAX_OPTIONS) {
            throw new IllegalArgumentException("Options may be at most " + MAX_OPTIONS + " characters.");
        }
        final Object parsed;
        try {
            parsed = JsonUtil.readValue(options, Object.class);
        } catch (final RuntimeException e) {
            throw new IllegalArgumentException("Options must be a JSON object, e.g. {\"color\": [\"#5470c6\"]}.");
        }
        if (!(parsed instanceof final Map<?, ?> map)) {
            throw new IllegalArgumentException("Options must be a JSON object, e.g. {\"color\": [\"#5470c6\"]}.");
        }
        final List<String> unknown = new ArrayList<>();
        for (final Object key : map.keySet()) {
            if (!OPTION_KEYS.contains(String.valueOf(key))) {
                unknown.add(String.valueOf(key));
            }
        }
        if (!unknown.isEmpty()) {
            throw new IllegalArgumentException("Options can't set " + String.join(", ", unknown) + ". They may "
                                               + "set: " + String.join(", ", OPTION_KEYS.stream().sorted()
                                                       .toList()) + ".");
        }
        return JsonUtil.writeValueAsString(parsed, false);
    }

    private static void checkText(final String what, final String text) {
        if (text != null && (text.length() > MAX_TEXT || text.chars().anyMatch(c -> c < 0x20))) {
            throw new IllegalArgumentException("The text of " + what + " is too long or has control characters.");
        }
    }

    // ---------------------------------------------------------------------------------------------------------

    /// The chart a plan replaces: a vis component on the dashboard showing a Visualisation Stroom AI generated.
    ///
    /// @param componentId   The vis component's id.
    /// @param componentName Its name, as the user sees it.
    /// @param visualisation The Visualisation it shows, which is rewritten for the new chart.
    @JsonInclude(Include.NON_NULL)
    public record Replace(@JsonProperty("componentId") String componentId,
                          @JsonProperty("componentName") String componentName,
                          @JsonProperty("visualisation") DocRef visualisation) {

    }
}
