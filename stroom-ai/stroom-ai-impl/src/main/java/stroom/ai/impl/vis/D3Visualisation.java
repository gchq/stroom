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
import java.util.Map;
import java.util.Optional;

/// The visualisations content pack's D3 Visualisations the agent can wire up to a table instead of generating a
/// chart, one per chart kind, with the id of the field control each role maps to. The pack's UUIDs are fixed, so
/// they find the docs; the function names are a fallback for a copy.
public enum D3Visualisation {

    BAR(ChartKind.BAR, "547b440d-4bb1-4d3b-86b7-ff2e41b49311", "visualisations.BarChart",
            Map.of("category", "x", "value", "y", "series", "series")),
    LINE(ChartKind.LINE, "567f4ba6-c420-4068-86a6-cc854165926b", "visualisations.LineChart",
            Map.of("x", "x", "y", "y", "series", "lineSeries")),
    PIE(ChartKind.PIE, "ce55cb67-22ef-4146-b866-f8ba955b9ba6", "visualisations.Doughnut",
            Map.of("name", "names", "value", "values")),
    SCATTER(ChartKind.SCATTER, "8b5e5f4f-c1a8-474f-9ff2-88bdb0f9a0a5", "visualisations.Scatter",
            Map.of("x", "x", "y", "y", "series", "pointSeries")),
    GRAPH(ChartKind.GRAPH, "bbbd4513-30f1-4c07-bc85-dafd4538d4df", "visualisations.Force",
            Map.of("source", "source", "target", "target", "weight", "count",
                    "sourceGroup", "sourceGroup", "targetGroup", "targetGroup"));

    private final ChartKind kind;
    private final String uuid;
    private final String functionName;
    private final Map<String, String> controls;

    D3Visualisation(final ChartKind kind,
                    final String uuid,
                    final String functionName,
                    final Map<String, String> controls) {
        this.kind = kind;
        this.uuid = uuid;
        this.functionName = functionName;
        this.controls = controls;
    }

    /// @return The chart kind it draws.
    public ChartKind kind() {
        return kind;
    }

    /// @return The content pack's UUID for it.
    public String uuid() {
        return uuid;
    }

    /// @return Its vis class.
    public String functionName() {
        return functionName;
    }

    /// @param roleId A role of its chart kind.
    /// @return The id of the field control the role maps to, if it has one (e.g. its scatter plot has no size).
    public Optional<String> control(final String roleId) {
        return Optional.ofNullable(controls.get(roleId));
    }

    /// @param kind A chart kind.
    /// @return The D3 Visualisation that draws it.
    /// @throws java.util.NoSuchElementException if the content pack has none for the kind (see [#find(ChartKind)]).
    public static D3Visualisation forKind(final ChartKind kind) {
        return find(kind).orElseThrow();
    }

    /// @param kind A chart kind.
    /// @return The D3 Visualisation that draws it, if the content pack has one: only ECharts draws the others.
    public static Optional<D3Visualisation> find(final ChartKind kind) {
        return Arrays.stream(values())
                .filter(d3 -> d3.kind == kind)
                .findFirst();
    }

    /// @param functionName A Visualisation's function name.
    /// @return The D3 Visualisation with that vis class, if any.
    public static Optional<D3Visualisation> forFunctionName(final String functionName) {
        return Arrays.stream(values())
                .filter(d3 -> d3.functionName.equals(functionName))
                .findFirst();
    }
}
