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

import stroom.ai.impl.AiWorkspaceConfig;
import stroom.ai.impl.vis.ChartKind;
import stroom.ai.impl.vis.ChartLibraries;
import stroom.ai.impl.vis.ChartLibraries.EChartsInstall;
import stroom.ai.impl.vis.ChartLibraries.InstalledVisualisation;
import stroom.ai.impl.vis.ChartRole;
import stroom.ai.impl.vis.D3Visualisation;
import stroom.ai.impl.vis.StyleSetting;
import stroom.ai.impl.workspace.ChatWorkspace;
import stroom.docref.DocRef;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/// list_visualisations: says which charting libraries are installed (ECharts as a Script doc, the content pack's
/// D3 Visualisations), each chart kind's columns and style settings, and the Visualisations already in Stroom.
class TestListVisualisationsTool {

    private static final int CHAT = 3;
    private static final DocRef ECHARTS_SCRIPT = new DocRef("Script", "echarts-uuid", "ECharts");
    private static final DocRef FORCE = new DocRef("Visualisation", D3Visualisation.GRAPH.uuid(), "Force");
    private static final DocRef SCATTER = new DocRef("Visualisation", D3Visualisation.SCATTER.uuid(), "Scatter");

    private final ChatWorkspace workspace = Mockito.mock(ChatWorkspace.class);
    private final ChartLibraries chartLibraries = Mockito.mock(ChartLibraries.class);
    private ListVisualisationsTool tool;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        Mockito.when(workspace.limits()).thenReturn(new AiWorkspaceConfig());
        Mockito.when(workspace.withTimeLimit(Mockito.any(Duration.class), Mockito.any()))
                .thenAnswer(invocation -> ((Supplier<?>) invocation.getArgument(1)).get());
        Mockito.when(chartLibraries.findECharts()).thenReturn(Optional.empty());
        Mockito.when(chartLibraries.findUnrecognisedECharts()).thenReturn(List.of());
        Mockito.when(chartLibraries.findD3()).thenReturn(Map.of());
        Mockito.when(chartLibraries.listVisualisations()).thenReturn(List.of());
        tool = new ListVisualisationsTool(workspace, chartLibraries);
    }

    @Test
    void installedEChartsIsListedWithItsVersionAndScript() {
        Mockito.when(chartLibraries.findECharts()).thenReturn(Optional.of(new EChartsInstall(ECHARTS_SCRIPT,
                "6.1.0")));
        Mockito.when(chartLibraries.currentUserCanUse(ECHARTS_SCRIPT)).thenReturn(true);

        assertThat(run())
                .startsWith("# Charting libraries")
                .contains("- ECharts 6.1.0: installed as Script 'ECharts' (echarts-uuid). Generates any of the "
                          + "kinds below (library echarts)")
                .doesNotContain("Use permission");
    }

    @Test
    void installedEChartsTheUserCantUseIsFlagged() {
        Mockito.when(chartLibraries.findECharts()).thenReturn(Optional.of(new EChartsInstall(ECHARTS_SCRIPT,
                "5.6.0")));
        Mockito.when(chartLibraries.currentUserCanUse(ECHARTS_SCRIPT)).thenReturn(false);

        assertThat(run()).contains("- ECharts 5.6.0: installed as Script 'ECharts' (echarts-uuid), but the user "
                                   + "doesn't have Use permission on it, so its charts won't load for them.");
    }

    @Test
    void echartsNotInstalledSaysHowToInstallItAndNamesUnrecognisedBuilds() {
        assertThat(run())
                .contains("- ECharts: not installed. An administrator installs it")
                .contains("from an Apache ECharts release, version 5 or later.")
                .doesNotContain("doesn't hold an ECharts");

        Mockito.when(chartLibraries.findUnrecognisedECharts()).thenReturn(List.of(
                new DocRef("Script", "dev-uuid", "echarts-dev"), new DocRef("Script", "old-uuid", "ECharts 4")));

        assertThat(run()).contains("- ECharts: not installed. Script 'echarts-dev', 'ECharts 4' doesn't hold an "
                                   + "ECharts 5 or later build");
    }

    @Test
    void installedD3KindsAreListedOrSaidToBeMissing() {
        assertThat(run()).contains("- D3 (the visualisations content pack): not installed.");

        Mockito.when(chartLibraries.findD3()).thenReturn(Map.of(ChartKind.GRAPH, FORCE));

        assertThat(run())
                .contains("- D3 (the visualisations content pack): graph ('Force'). Wires these existing "
                          + "Visualisations to a table (library d3)")
                .doesNotContain("not in the D3 one");
    }

    @Test
    void everyChartKindIsListedWithItsColumnsAndStyleSettings() {
        final String result = run();

        for (final ChartKind kind : ChartKind.values()) {
            assertThat(result).contains("## " + kind.id() + ": " + kind.displayName() + "\n" + kind.summary()
                                        + ".\nColumns:\n");
            for (final ChartRole role : kind.roles()) {
                assertThat(result).contains("- " + role.id() + " (" + (role.required()
                        ? "required"
                        : "optional") + ", " + role.values().description() + "): " + role.description());
            }
            for (final StyleSetting setting : kind.styleSettings()) {
                assertThat(result).contains(setting.id() + " (" + setting.describeValues() + ", default '"
                                            + setting.defaultValue() + "'): " + setting.description());
            }
        }
        assertThat(result).contains("maxNodes (a whole number from 2 to 5000, default '300')");
    }

    @Test
    void aRoleTheD3VisualisationLacksIsFlagged() {
        Mockito.when(chartLibraries.findD3()).thenReturn(Map.of(ChartKind.SCATTER, SCATTER));

        final String result = run();

        assertThat(result)
                .contains("- size (optional, " + ChartKind.SCATTER.role("size").orElseThrow().values()
                        .description() + "): each point's size (not in the D3 one)\n")
                // Only the kind whose D3 Visualisation is installed is checked.
                .containsOnlyOnce("(not in the D3 one)");
    }

    @Test
    void theVisualisationsInStroomAreListedWithWhatTheyAre() {
        assertThat(run()).endsWith("# Visualisations in Stroom\n\nNone the user can see.");

        Mockito.when(chartLibraries.listVisualisations()).thenReturn(List.of(
                new InstalledVisualisation(new DocRef("Visualisation", "gen-uuid", "Message links"),
                        "stroom.ai.EChartsGeneric", true),
                new InstalledVisualisation(FORCE, D3Visualisation.GRAPH.functionName(), false),
                new InstalledVisualisation(new DocRef("Visualisation", "own-uuid", "Home made"),
                        "my.Chart", false)));

        assertThat(run()).endsWith("# Visualisations in Stroom\n\n"
                                   + "- 'Message links' (gen-uuid): generated by Stroom AI (ECharts)\n"
                                   + "- 'Force' (" + FORCE.getUuid() + "): content pack D3 graph\n"
                                   + "- 'Home made' (own-uuid)");
    }

    @Test
    void manyVisualisationsAreCappedSayingHowManyMore() {
        final int count = ListVisualisationsTool.MAX_LISTED + 3;
        Mockito.when(chartLibraries.listVisualisations()).thenReturn(IntStream.rangeClosed(1, count)
                .mapToObj(i -> new InstalledVisualisation(new DocRef("Visualisation", "uuid-" + i, "Vis " + i),
                        "my.Chart", false))
                .toList());

        final String result = run();

        assertThat(result)
                .contains("- 'Vis " + ListVisualisationsTool.MAX_LISTED + "' (uuid-"
                          + ListVisualisationsTool.MAX_LISTED + ")")
                .doesNotContain("'Vis " + (ListVisualisationsTool.MAX_LISTED + 1) + "'")
                .endsWith("... and 3 more.");
    }

    private String run() {
        return tool.execute(new ToolCall("c1", ListVisualisationsTool.NAME, "{}"), ToolCalls.inChat(CHAT,
                workspace)).text();
    }
}
