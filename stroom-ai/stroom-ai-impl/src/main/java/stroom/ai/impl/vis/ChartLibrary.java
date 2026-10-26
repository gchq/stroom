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
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/// A charting library Stroom AI generates charts with. An administrator installs the library as a Script doc; Stroom
/// ships an adapter for it (a Script doc it puts beside the library's), whose vis class each generated Visualisation
/// names as its function. The chart kinds, their roles and the settings templates are shared by both; a few settings
/// only ECharts draws.
public enum ChartLibrary {

    ECHARTS("echarts", "ECharts", "visualisations.EChartsGeneric", "/stroom/ai/impl/vis/echarts-generic.js",
            "ECharts Generic", "stroom-echarts-generic-version:", Set.of()),
    D3("d3", "D3", "visualisations.D3Generic", "/stroom/ai/impl/vis/d3-generic.js",
            "D3 Generic", "stroom-d3-generic-version:",
            Set.of(StyleSetting.BREAK_OUTLIERS, StyleSetting.THUMBNAIL, StyleSetting.ZOOM_SLIDER));

    private final String id;
    private final String displayName;
    private final String functionName;
    private final String adapterResource;
    private final String adapterName;
    private final String adapterMarker;
    private final Set<StyleSetting> unsupported;

    ChartLibrary(final String id,
                 final String displayName,
                 final String functionName,
                 final String adapterResource,
                 final String adapterName,
                 final String adapterMarker,
                 final Set<StyleSetting> unsupported) {
        this.id = id;
        this.displayName = displayName;
        this.functionName = functionName;
        this.adapterResource = adapterResource;
        this.adapterName = adapterName;
        this.adapterMarker = adapterMarker;
        this.unsupported = unsupported;
    }

    /// @return Its id, as the agent names it, e.g. `d3`.
    public String id() {
        return id;
    }

    /// @return Its name, e.g. "D3".
    public String displayName() {
        return displayName;
    }

    /// @return The vis class its adapter defines: a generated Visualisation's function name.
    public String functionName() {
        return functionName;
    }

    /// @return Its adapter's script, bundled on the classpath.
    public String adapterResource() {
        return adapterResource;
    }

    /// @return The name its adapter's Script doc is created with.
    public String adapterName() {
        return adapterName;
    }

    /// @return The marker in its adapter's banner, followed by the adapter's version.
    public String adapterMarker() {
        return adapterMarker;
    }

    /// @param setting A style setting.
    /// @return True if its adapter draws the setting.
    public boolean supports(final StyleSetting setting) {
        return !unsupported.contains(setting);
    }

    /// @param id A library's id, ignoring case.
    /// @return The library, if there is one.
    public static Optional<ChartLibrary> find(final String id) {
        final String wanted = id == null
                ? ""
                : id.strip().toLowerCase(Locale.ROOT);
        return Arrays.stream(values())
                .filter(library -> library.id.equals(wanted))
                .findFirst();
    }

    /// @param functionName A Visualisation's function name.
    /// @return The library whose adapter draws it, if it is a chart Stroom AI generated.
    public static Optional<ChartLibrary> forFunctionName(final String functionName) {
        return Arrays.stream(values())
                .filter(library -> library.functionName.equals(functionName))
                .findFirst();
    }
}
