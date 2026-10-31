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

import stroom.ai.impl.tools.ToolSchema.ObjectSchema;
import stroom.ai.impl.vis.ChartKind;
import stroom.ai.impl.vis.ChartLibraries;
import stroom.ai.impl.vis.ChartLibraries.D3Install;
import stroom.ai.impl.vis.ChartLibraries.EChartsInstall;
import stroom.ai.impl.vis.ChartLibraries.InstalledVisualisation;
import stroom.ai.impl.vis.ChartLibrary;
import stroom.ai.impl.vis.ChartRole;
import stroom.ai.impl.vis.D3Visualisation;
import stroom.ai.impl.vis.StyleSetting;
import stroom.ai.impl.vis.VisualisationPlan;
import stroom.ai.impl.workspace.ChatWorkspace;
import stroom.ai.shared.AiToolCategory;
import stroom.docref.DocRef;
import stroom.security.shared.DocumentPermission;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/// `list_visualisations` - says which charting libraries Stroom has (ECharts, installed by an administrator as a
/// Script doc; the visualisations content pack's D3 Visualisations), the chart kinds the agent can generate with
/// each and the table columns and style settings each kind takes, and the Visualisation docs already in Stroom.
class ListVisualisationsTool extends AbstractWorkspaceTool {

    static final String NAME = "list_visualisations";
    static final int MAX_LISTED = 100;

    // How a kind's chart behaves, and can be tuned with options, beyond its style settings.
    private static final Map<ChartKind, String> TUNING = Map.of(ChartKind.GRAPH,
            "Layout (echarts): force-directed, animating as it settles (up to 500 nodes); nodes can be dragged, "
            + "and the graph zooms and pans. Tune its physics with options {\"seriesDefaults\": {\"force\": "
            + "{...}}}: repulsion (default 160, 80 over 100 nodes), edgeLength (default [40, 140], [20, 80] over "
            + "100 nodes), gravity (default 0.1), friction (ECharts' 0.6; lower keeps nodes moving longer), "
            + "initLayout (\"circular\" starts the nodes on a ring) and layoutAnimation (true or false).");

    private final ChartLibraries chartLibraries;

    @Inject
    ListVisualisationsTool(final ChatWorkspace workspace, final ChartLibraries chartLibraries) {
        super(workspace);
        this.chartLibraries = chartLibraries;
    }

    @Override
    public ToolPermissions permissions() {
        // Declared as checked (found reviewing the tools: declared as none, so the permission matrix and the
        // documentation understated them).
        return ToolPermissions.builder()
                .checksDocuments(DocumentPermission.VIEW, DocumentPermission.USE)
                .note("Lists the Visualisations the user can view, and says whether they can use the chart "
                      + "libraries' scripts.")
                .build();
    }

    @Override
    public AiToolCategory category() {
        return AiToolCategory.CONTENT;
    }

    @Override
    public boolean readsUntrustedContent() {
        // Only names of documents and Stroom's own descriptions.
        return false;
    }

    @Override
    public ToolDefinition definition() {
        return ToolDefinition.builder()
                .name(NAME)
                .description(ToolDescription.builder()
                        .general("Lists the charting libraries installed in Stroom (ECharts and the "
                                 + "visualisations content pack's D3 ones), the chart kinds that can be "
                                 + "generated with each (bar, line, pie, scatter, network graph) with the table "
                                 + "columns and style settings each takes, and the Visualisation docs already in "
                                 + "Stroom.")
                        .chatOnly(" Call it before propose_visualisation, and ask the user which library to use "
                                  + "when more than one is installed.")
                        .build())
                .parameters(ObjectSchema.builder().build())
                .build();
    }

    @Override
    protected ToolResult run(final ToolCall request, final ToolCallContext context) {
        final StringBuilder sb = new StringBuilder("# Charting libraries\n\n");

        final EChartsInstall echarts = chartLibraries.findECharts().orElse(null);
        if (echarts == null) {
            sb.append("- ECharts: not installed. ")
                    .append(ProposeVisualisationTool.unrecognisedNote(chartLibraries.findUnrecognisedECharts()))
                    .append("An administrator installs it by creating a Script doc (e.g. "
                            + "Visualisations/ECharts/ECharts) holding echarts.min.js from an Apache ECharts "
                            + "release, version ")
                    .append(ChartLibraries.MIN_ECHARTS_MAJOR).append(" or later.\n");
        } else {
            sb.append("- ECharts ").append(echarts.version()).append(": installed as Script '")
                    .append(echarts.script().getName()).append("' (").append(echarts.script().getUuid())
                    .append(")");
            if (!chartLibraries.currentUserCanUse(echarts.script())) {
                sb.append(", but the user doesn't have Use permission on it, so its charts won't load for them");
            }
            sb.append(". Generates any of the kinds below (library echarts), drawn by Stroom's own adapter.\n");
        }

        final D3Install d3Library = chartLibraries.findD3Library().orElse(null);
        if (d3Library == null) {
            sb.append("- D3: not installed. An administrator installs it as a Script doc holding a D3 build, "
                      + "version 3 or later (the visualisations content pack includes D3 3).\n");
        } else {
            sb.append("- D3 ").append(d3Library.version()).append(": installed as Script '")
                    .append(d3Library.script().getName()).append("' (").append(d3Library.script().getUuid())
                    .append(")");
            if (!chartLibraries.currentUserCanUse(d3Library.script())) {
                sb.append(", but the user doesn't have Use permission on it, so its charts won't load for them");
            }
            sb.append(". Generates any of the kinds below (library d3), with any of their settings, drawn by Stroom's "
                      + "own adapter to look and work as ECharts' do. The newest D3 installed is used.\n");
        }

        final Map<ChartKind, DocRef> d3 = chartLibraries.findContentPackD3();
        if (d3.isEmpty()) {
            sb.append("- The visualisations content pack's D3 charts: not installed.\n");
        } else {
            sb.append("- The visualisations content pack's D3 charts: ").append(d3.entrySet().stream()
                    .map(entry -> entry.getKey().id() + " ('" + entry.getValue().getName() + "')")
                    .collect(Collectors.joining(", ")))
                    .append(". Wires these existing Visualisations to a table (library content-pack); nothing "
                            + "is generated, and only their own inputs apply.\n");
        }
        if (echarts != null && d3Library != null) {
            sb.append("Both ECharts and D3 can generate charts: ask the user which to use (ask_user) unless they "
                      + "have said.\n");
        }

        sb.append("\n# Chart kinds\n");
        for (final ChartKind kind : ChartKind.values()) {
            sb.append("\n## ").append(kind.id()).append(": ").append(kind.displayName()).append("\n")
                    .append(kind.summary()).append(".\nColumns:\n");
            for (final ChartRole role : kind.roles()) {
                sb.append("- ").append(role.id()).append(role.required()
                                ? " (required"
                                : " (optional")
                        .append(", ").append(role.values().description()).append("): ")
                        .append(role.description());
                if (d3.containsKey(kind) && D3Visualisation.forKind(kind).control(role.id()).isEmpty()) {
                    sb.append(" (not in the D3 one)");
                }
                sb.append("\n");
            }
            sb.append("Style settings: ").append(kind.styleSettings().stream()
                    .map(ListVisualisationsTool::describe)
                    .collect(Collectors.joining("; "))).append("\n");
            NullSafe.consume(TUNING.get(kind), tuning -> sb.append(tuning).append("\n"));
        }
        sb.append("\nClicks (echarts and d3): clicking a slice, bar, point, cell, link or node selects it "
                  + "(clicking it again, or empty space, clears it). The selection holds each column id it maps "
                  + "(e.g. category and series) and the same value under the table column's name, dates as ISO "
                  + "8601 text; a network graph's node gives node, and its link source and target. A query or "
                  + "table is filtered by it with an add_selection_filter change (propose_dashboard_change).\n");
        sb.append("\nOptions (echarts): for what the style settings can't do, ECharts option overrides as a JSON "
                  + "object, with top-level keys ").append(String.join(", ", VisualisationPlan.OPTION_KEYS.stream()
                        .sorted()
                        .toList()))
                .append(" (seriesDefaults is merged into every series). They are one setting: giving options "
                        + "again replaces all of them, so keep the ones wanted.\n")
                .append("Options (d3): only color, a list of colours for the series or categories.\n");

        final List<InstalledVisualisation> installed = chartLibraries.listVisualisations();
        sb.append("\n# Visualisations in Stroom\n\n");
        if (installed.isEmpty()) {
            sb.append("None the user can see.\n");
        } else {
            installed.stream().limit(MAX_LISTED).forEach(vis -> sb.append("- '").append(vis.docRef().getName())
                    .append("' (").append(vis.docRef().getUuid()).append(")")
                    .append(vis.generated()
                            ? ": generated by Stroom AI" + ChartLibrary.forFunctionName(vis.functionName())
                                    .map(library -> " (" + library.displayName() + ")")
                                    .orElse("")
                            : D3Visualisation.forFunctionName(vis.functionName())
                                    .map(d3Vis -> ": content pack D3 " + d3Vis.kind().id())
                                    .orElse(""))
                    .append("\n"));
            if (installed.size() > MAX_LISTED) {
                sb.append("... and ").append(installed.size() - MAX_LISTED).append(" more.\n");
            }
        }
        return ToolResult.text(sb.toString().strip());
    }

    private static String describe(final StyleSetting setting) {
        return setting.id() + " (" + setting.describeValues() + ", default '" + setting.defaultValue() + "'): "
               + setting.description();
    }
}
