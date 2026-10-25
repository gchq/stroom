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

import stroom.ai.impl.AiAuditLog;
import stroom.ai.impl.AiWorkspaceConfig;
import stroom.ai.impl.DashboardComponents;
import stroom.ai.impl.TabSnapshots;
import stroom.ai.impl.TabSnapshots.StoredPart;
import stroom.ai.impl.TabSnapshots.StoredTabSnapshot;
import stroom.ai.impl.TabSnapshots.UnsavedPart;
import stroom.ai.impl.UserActions;
import stroom.ai.impl.vis.ChartKind;
import stroom.ai.impl.vis.ChartLibraries;
import stroom.ai.impl.vis.ChartLibraries.EChartsInstall;
import stroom.ai.impl.vis.D3Visualisation;
import stroom.ai.impl.vis.VisualisationPlan;
import stroom.ai.impl.vis.VisualisationPlan.Replace;
import stroom.ai.impl.workspace.ChatWorkspace;
import stroom.ai.impl.workspace.WorkspacePath;
import stroom.ai.shared.AiTabSearch;
import stroom.ai.shared.AiTabSnapshotPart;
import stroom.ai.shared.DashboardTableContext;
import stroom.ai.shared.QueryTableContext;
import stroom.dashboard.shared.DashboardSearchRequest;
import stroom.dashboard.shared.TableResultRequest;
import stroom.docref.DocRef;
import stroom.query.api.Column;
import stroom.query.api.Format;
import stroom.query.api.TableSettings;
import stroom.query.shared.QuerySearchRequest;
import stroom.util.json.JsonUtil;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

/// propose_visualisation: plans a chart of a table on the dashboard (or the StroomQL query) the user attached,
/// checks the table's sampled rows can make it, and offers a 'Create visualisation' button (ECharts) or the change
/// that wires the content pack's D3 Visualisation to the table (D3).
class TestProposeVisualisationTool {

    private static final int CHAT = 3;
    private static final DocRef DASHBOARD = new DocRef("Dashboard", "1c2d3e4f-5a6b-4c7d-8e9f-0a1b2c3d4e5f",
            "Messages");
    private static final DocRef QUERY = new DocRef("Query", "2d3e4f5a-6b7c-4d8e-9f0a-1b2c3d4e5f6a", "Tweets");
    private static final DocRef ECHARTS_SCRIPT = new DocRef("Script", "echarts-uuid", "ECharts");
    private static final DocRef FORCE = new DocRef("Visualisation", D3Visualisation.GRAPH.uuid(), "Force");
    private static final DocRef SCATTER = new DocRef("Visualisation", D3Visualisation.SCATTER.uuid(), "Scatter");
    // The Visualisation the dashboard's 'Old chart' pane shows.
    private static final DocRef OLD_CHART = new DocRef("Visualisation", "bar-uuid", "Bar chart");
    private static final String RENDER_REPORT_LINE = DashboardComponents.RENDER_REPORT + "{\"rendered\":true}";
    private static final String DASHBOARD_TEXT = "## Table 'Links' (id table-1)\n- From\n- To\n- Count\n"
                                                 + "## Visualisation 'Old chart' (id vis-1)\n"
                                                 + "Visualisation: 'Bar chart' (uuid bar-uuid)\n"
                                                 + "Linked table: 'Links'\n"
                                                 + RENDER_REPORT_LINE + "\n";
    private static final String EMBEDDED_QUERY_TEXT = "from \"Messages\"\nselect From, To, count() as Count";
    private static final String QUERY_TEXT = "from \"Messages\"\nselect From, To, count() as Count\n";
    private static final String SAMPLE_PATH = "/tabs/1/results/links.csv";
    private static final String SAMPLE_CSV = "From,To,Count\nalice,bob,3\nbob,carol,2\ncarol,alice,1\n";

    private final ChatWorkspace workspace = Mockito.mock(ChatWorkspace.class);
    private final TabSnapshots tabSnapshots = Mockito.mock(TabSnapshots.class);
    private final DocumentAccess documentAccess = Mockito.mock(DocumentAccess.class);
    private final CurrentResultsTool currentResultsTool = Mockito.mock(CurrentResultsTool.class);
    private final ChartLibraries chartLibraries = Mockito.mock(ChartLibraries.class);
    private final AiAuditLog auditLog = Mockito.mock(AiAuditLog.class);
    private final ToolRunContext context = new ToolRunContext(null, null, () -> false);
    private final List<Map<String, Object>> offered = new ArrayList<>();
    private ProposeVisualisationTool tool;

    @TempDir
    private Path tempDir;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() throws IOException {
        Mockito.when(workspace.limits()).thenReturn(new AiWorkspaceConfig());
        Mockito.when(workspace.withTimeLimit(Mockito.any(Duration.class), Mockito.any()))
                .thenAnswer(invocation -> ((Supplier<?>) invocation.getArgument(1)).get());
        // The table's current_results snapshot, as a CSV in the chat's workspace.
        final Path sample = tempDir.resolve("links.csv");
        Files.writeString(sample, SAMPLE_CSV);
        Mockito.when(workspace.localFile(CHAT, WorkspacePath.parse(SAMPLE_PATH))).thenReturn(sample);
        Mockito.when(currentResultsTool.outcome(Mockito.any(ToolCall.class), Mockito.any(ToolCallContext.class)))
                .thenReturn(new CurrentResultsTool.Outcome("Snapshot of table 'Links'.", SAMPLE_PATH));

        Mockito.when(documentAccess.canView(DASHBOARD)).thenReturn(true);
        Mockito.when(documentAccess.canView(QUERY)).thenReturn(true);
        Mockito.when(chartLibraries.findECharts()).thenReturn(Optional.of(new EChartsInstall(ECHARTS_SCRIPT,
                "6.1.0")));
        Mockito.when(chartLibraries.currentUserCanUse(ECHARTS_SCRIPT)).thenReturn(true);
        Mockito.when(chartLibraries.findD3()).thenReturn(Map.of());
        Mockito.when(chartLibraries.findUnrecognisedECharts()).thenReturn(List.of());

        tool = new ProposeVisualisationTool(workspace, tabSnapshots, documentAccess, currentResultsTool,
                chartLibraries, auditLog);
        dashboardTab(List.of(linksTable()), false);
    }

    @Test
    void anEChartsNetworkGraphIsOfferedAsACreateVisualisationButton() {
        final String result = call(graphArgs());

        assertThat(result)
                .contains("shown a 'Create visualisation' button for a network graph of table 'Links' "
                          + "(source = 'From', target = 'To', weight = 'Count'), drawn with ECharts 6.1.0")
                .contains("with the name 'Message links' in the dashboard's folder")
                .contains("added to dashboard 'Messages' below table 'Links', unsaved")
                .contains("Sample: 3 rows from snapshot " + SAMPLE_PATH)
                .contains("From the sample: rows 3, nodes 3, links 3.");
        assertThat(takeUserActions()).singleElement().satisfies(action -> {
            assertThat(action)
                    .containsEntry("action", UserActions.CREATE_VISUALISATION)
                    .containsEntry("doc", Map.of("type", DASHBOARD.getType(), "uuid", DASHBOARD.getUuid()))
                    .containsEntry("name", "Message links")
                    .containsEntry("summary", "Who messages whom.");
            assertThat((String) action.get("id")).startsWith("create-visualisation-");
            final VisualisationPlan plan = VisualisationPlan.fromJson((String) action.get("plan"));
            assertThat(plan.chartKind()).isEqualTo(ChartKind.GRAPH);
            assertThat(plan.mappings()).containsExactlyEntriesOf(mapOf(
                    "source", "From", "target", "To", "weight", "Count"));
            assertThat(plan.table()).isEqualTo("Links");
            assertThat(plan.componentId()).isEqualTo("table-1");
            assertThat(plan.componentName()).isEqualTo("Message links");
            assertThat(plan.placement()).isEqualTo(VisualisationPlan.BELOW);
            // The render report, which changes as the chart draws, is left out of the hash.
            assertThat(plan.snapshotHash())
                    .isEqualTo(AiTabSnapshotPart.hashText(DashboardComponents.withoutRenderReports(DASHBOARD_TEXT)))
                    .isEqualTo(AiTabSnapshotPart.hashText(DASHBOARD_TEXT.replace(RENDER_REPORT_LINE + "\n", "")))
                    .isNotEqualTo(AiTabSnapshotPart.hashText(DASHBOARD_TEXT));
            assertThat(plan.query()).isNull();
            assertThat(plan.isQuery()).isFalse();
            assertThat(plan.baseQuery()).isNull();
            assertThat(plan.options()).isNull();
        });
        Mockito.verify(auditLog).documentViewed(Mockito.eq(ToolSession.chat(CHAT)), Mockito.eq(DASHBOARD),
                Mockito.contains("Proposed a network graph of table 'Links'"));
    }

    @Test
    void aTableWithAGeneratedChartAsksWhetherToReplaceItOrAddANewOne() {
        Mockito.when(chartLibraries.isGenerated(OLD_CHART)).thenReturn(true);

        assertThat(call(graphArgs()))
                .startsWith("No chart was offered yet: table 'Links' already has a chart Stroom AI generated: "
                            + "pane 'Old chart' (Visualisation 'Bar chart').")
                .contains("ask them (ask_user) whether to replace it")
                .contains("with replace (the pane's name) or addNew true");
        assertThat(takeUserActions()).isEmpty();
        Mockito.verifyNoInteractions(auditLog);
    }

    @Test
    void addNewOffersANewChartBesideAGeneratedOne() {
        Mockito.when(chartLibraries.isGenerated(OLD_CHART)).thenReturn(true);
        final Map<String, Object> args = graphArgs();
        args.put(ProposeVisualisationTool.ADD_NEW_ARG, true);

        assertThat(call(args)).contains("'Create visualisation' button");
        assertThat(takeUserActions()).singleElement().satisfies(action ->
                assertThat(VisualisationPlan.fromJson((String) action.get("plan")).isReplacement()).isFalse());
    }

    @Test
    void aChartNotGeneratedByStroomAiIsntAskedAbout() {
        // A content pack chart can't be rewritten, so a new chart is simply added.
        assertThat(call(graphArgs())).contains("'Create visualisation' button");
    }

    @Test
    void replaceOffersAButtonThatRewritesTheGeneratedChart() {
        Mockito.when(chartLibraries.isGenerated(OLD_CHART)).thenReturn(true);
        Mockito.when(documentAccess.canEdit(OLD_CHART)).thenReturn(true);
        final Map<String, Object> args = graphArgs();
        args.put(ProposeVisualisationTool.REPLACE_ARG, "old chart");

        assertThat(call(args))
                .contains("shown a 'Replace visualisation' button for a network graph of table 'Links'")
                .contains("Visualisation 'Bar chart' is rewritten for it and renamed 'Message links', saved at "
                          + "once")
                .contains("pane 'Old chart' shows it, unsaved on the dashboard");
        assertThat(takeUserActions()).singleElement().satisfies(action -> {
            assertThat(action).containsEntry("action", UserActions.CREATE_VISUALISATION)
                    .containsEntry("name", "Message links");
            final VisualisationPlan plan = VisualisationPlan.fromJson((String) action.get("plan"));
            assertThat(plan.replace()).isEqualTo(new Replace("vis-1", "Old chart", OLD_CHART));
            // The pane keeps its name unless given another.
            assertThat(plan.componentName()).isEqualTo("Old chart");
            assertThat(plan.componentId()).isEqualTo("table-1");
        });
    }

    @Test
    void replaceCanRenameThePane() {
        Mockito.when(chartLibraries.isGenerated(OLD_CHART)).thenReturn(true);
        Mockito.when(documentAccess.canEdit(OLD_CHART)).thenReturn(true);
        final Map<String, Object> args = graphArgs();
        args.put(ProposeVisualisationTool.REPLACE_ARG, "Old chart");
        args.put(ProposeVisualisationTool.COMPONENT_NAME_ARG, "Who messages whom");

        assertThat(call(args)).contains("pane 'Old chart' shows it, renamed 'Who messages whom'");
        assertThat(takeUserActions()).singleElement().satisfies(action ->
                assertThat(VisualisationPlan.fromJson((String) action.get("plan")).componentName())
                        .isEqualTo("Who messages whom"));
    }

    @Test
    void replaceIsRefusedForWhatCantBeRewritten() {
        Mockito.when(chartLibraries.isGenerated(OLD_CHART)).thenReturn(true);
        assertThat(callWith(ProposeVisualisationTool.REPLACE_ARG, "Old chart"))
                .isEqualTo("Error: The user doesn't have Edit permission on Visualisation 'Bar chart', so it "
                           + "can't be replaced. Give addNew true to add the new chart beside it instead.");
        assertThat(callWith(ProposeVisualisationTool.REPLACE_ARG, "Nope"))
                .isEqualTo("Error: The dashboard has no Visualisation pane 'Nope'. Its Visualisation panes are: "
                           + "'Old chart'.");
        // Not a pane of a Visualisation.
        assertThat(callWith(ProposeVisualisationTool.REPLACE_ARG, "Links"))
                .startsWith("Error: The dashboard has no Visualisation pane 'Links'.");

        final Map<String, Object> both = graphArgs();
        both.put(ProposeVisualisationTool.REPLACE_ARG, "Old chart");
        both.put(ProposeVisualisationTool.ADD_NEW_ARG, true);
        assertThat(call(both)).isEqualTo("Error: Give replace or addNew, not both.");

        final Map<String, Object> d3 = graphArgs();
        d3.put(ProposeVisualisationTool.LIBRARY_ARG, ProposeVisualisationTool.D3);
        d3.put(ProposeVisualisationTool.REPLACE_ARG, "Old chart");
        assertThat(call(d3)).startsWith("Error: Only an ECharts chart (library echarts) can replace one");

        Mockito.when(chartLibraries.isGenerated(OLD_CHART)).thenReturn(false);
        Mockito.when(documentAccess.canEdit(OLD_CHART)).thenReturn(true);
        assertThat(callWith(ProposeVisualisationTool.REPLACE_ARG, "Old chart"))
                .isEqualTo("Error: Pane 'Old chart' shows Visualisation 'Bar chart', which Stroom AI didn't "
                           + "generate, so it isn't rewritten. Give addNew true to add the new chart beside it.");
        assertThat(takeUserActions()).isEmpty();
    }

    @Test
    void replaceIsRefusedForAPaneOfAnotherTable() {
        Mockito.when(tabSnapshots.part(Mockito.eq(CHAT), Mockito.any(), Mockito.eq(AiTabSnapshotPart.DASHBOARD)))
                .thenReturn(Optional.of(new UnsavedPart(DASHBOARD_TEXT.replace("Linked table: 'Links'",
                        "Linked table: 'Other'"), false, "/tabs/1/dashboard.md")));
        Mockito.when(chartLibraries.isGenerated(OLD_CHART)).thenReturn(true);
        Mockito.when(documentAccess.canEdit(OLD_CHART)).thenReturn(true);

        assertThat(callWith(ProposeVisualisationTool.REPLACE_ARG, "Old chart"))
                .isEqualTo("Error: Pane 'Old chart' draws table 'Other', not 'Links'. Chart table 'Other' to "
                           + "replace it, or give addNew true.");
        // Nor is its chart asked about, as it draws another table.
        assertThat(call(graphArgs())).contains("'Create visualisation' button");
    }

    @Test
    void replaceIsRefusedForAQuery() {
        queryTab();
        assertThat(callWith(ProposeVisualisationTool.REPLACE_ARG, "Old chart"))
                .startsWith("Error: Only a chart on a dashboard replaces another with replace");
    }

    @Test
    void aComponentNamePlacementStyleAndOptionsAreKeptInThePlan() {
        final Map<String, Object> args = graphArgs();
        args.put(ProposeVisualisationTool.COMPONENT_NAME_ARG, "Links chart");
        args.put(ProposeVisualisationTool.PLACEMENT_ARG, VisualisationPlan.TAB);
        args.put(ProposeVisualisationTool.STYLE_ARG, mapOf("directed", "yes", "maxNodes", " 50 "));
        args.put(ProposeVisualisationTool.OPTIONS_ARG, "{ \"color\": [\"#5470c6\"] }");

        assertThat(call(args)).contains("added to dashboard 'Messages' as a tab beside table 'Links', unsaved");

        assertThat(takeUserActions()).singleElement().satisfies(action -> {
            final VisualisationPlan plan = VisualisationPlan.fromJson((String) action.get("plan"));
            assertThat(plan.componentName()).isEqualTo("Links chart");
            assertThat(plan.placement()).isEqualTo(VisualisationPlan.TAB);
            assertThat(plan.style()).containsExactlyEntriesOf(mapOf("directed", "true", "maxNodes", "50"));
            assertThat(plan.options()).isEqualTo("{\"color\":[\"#5470c6\"]}");
            // The Visualisation's own name is still the one given.
            assertThat(action).containsEntry("name", "Message links");
        });
    }

    @Test
    void optionsGivenAsAnObjectRatherThanTextAreKept() {
        final Map<String, Object> args = graphArgs();
        args.put(ProposeVisualisationTool.OPTIONS_ARG, mapOf("color", List.of("#5470c6")));

        call(args);

        assertThat(takeUserActions()).singleElement().satisfies(action -> assertThat(
                VisualisationPlan.fromJson((String) action.get("plan")).options())
                .isEqualTo("{\"color\":[\"#5470c6\"]}"));
    }

    @Test
    void columnNamesWithSurroundingSpacesAreStrippedInThePlan() {
        final Map<String, Object> args = graphArgs();
        args.put(ProposeVisualisationTool.COLUMNS_ARG, mapOf(" source ", " From ", "target", "To  ",
                "weight", "  "));

        assertThat(call(args)).contains("(source = 'From', target = 'To')");

        assertThat(takeUserActions()).singleElement().satisfies(action -> assertThat(
                VisualisationPlan.fromJson((String) action.get("plan")).mappings())
                .containsExactlyEntriesOf(mapOf("source", "From", "target", "To")));
    }

    @Test
    void aStroomQlQueryIsGivenTheChartInItsShowClauseByUuid() {
        queryTab();

        final String result = call(graphArgs());

        assertThat(result)
                .contains("in the query's folder")
                .contains("put in the query's show clause, naming it by its UUID (show \"<its uuid>\" "
                          + "(source = \"From\", target = \"To\", weight = \"Count\")), unsaved");
        assertThat(takeUserActions()).singleElement().satisfies(action -> {
            assertThat(action).containsEntry("doc", Map.of("type", QUERY.getType(), "uuid", QUERY.getUuid()));
            final VisualisationPlan plan = VisualisationPlan.fromJson((String) action.get("plan"));
            assertThat(plan.isQuery()).isTrue();
            assertThat(plan.query()).isTrue();
            assertThat(plan.baseQuery()).isEqualTo(QUERY_TEXT);
            assertThat(plan.snapshotHash()).isEqualTo(AiTabSnapshotPart.hashText(QUERY_TEXT));
            assertThat(plan.componentId()).isNull();
            assertThat(plan.componentName()).isNull();
            assertThat(plan.table()).isNull();
            assertThat(plan.placement()).isNull();
        });
    }

    @Test
    void aColumnWithTheWrongFormatForItsRoleStopsTheChart() {
        final Map<String, Object> args = graphArgs();
        args.put(ProposeVisualisationTool.KIND_ARG, "bar");
        args.put(ProposeVisualisationTool.COLUMNS_ARG, mapOf("category", "From", "value", "To"));

        final String result = call(args);

        assertThat(result)
                .startsWith("No chart was offered: the table 'Links' can't make this bar chart:")
                .contains("'To' can't be value: its format is TEXT, and value needs numbers.")
                .contains("Tell the user why");
        assertThat(takeUserActions()).isEmpty();
        Mockito.verifyNoInteractions(auditLog);
    }

    @Test
    void aDateTimeColumnOfFormattedTextStopsTheChartSayingToUseTheDate() {
        // The table's expression, from its dashboard settings, shows the Date Time column holds text.
        dashboardTab(List.of(linksTable(Column.builder().id("c1").name("From")
                .expression("formatDate(floorHour(${EventTime}), 'yyyy-MM-dd HH:mm')")
                .format(Format.DATE_TIME)
                .build())), false);
        final Map<String, Object> args = graphArgs();
        args.put(ProposeVisualisationTool.KIND_ARG, "line");
        args.put(ProposeVisualisationTool.COLUMNS_ARG, mapOf("x", "From", "y", "Count", "series", "To"));

        assertThat(call(args))
                .startsWith("No chart was offered: the table 'Links' can't make this line chart:")
                .contains("'From' can't be x: its format is Date Time but its expression makes text "
                          + "(formatDate())")
                .contains("the date itself, floorHour(${EventTime})");
        assertThat(takeUserActions()).isEmpty();
    }

    @Test
    void aColumnGroupedBelowTheTopLevelStopsTheChart() {
        // The table's grouping, from its dashboard settings: To grouped under From.
        dashboardTab(List.of(linksTable(
                Column.builder().id("c1").name("From").expression("${From}").format(Format.TEXT).group(0).build(),
                Column.builder().id("c2").name("To").expression("${To}").format(Format.TEXT).group(1).build())),
                false);

        assertThat(call(graphArgs()))
                .startsWith("No chart was offered: the table 'Links' can't make this network graph:")
                .contains("'To' can't be target: it is grouped at level 2");
        assertThat(takeUserActions()).isEmpty();
    }

    @Test
    void sampledValuesThatArentNumbersStopTheChart() {
        // A StroomQL query's column formats aren't known, so the sample is what shows the column isn't numeric.
        queryTab();
        final Map<String, Object> args = graphArgs();
        args.put(ProposeVisualisationTool.KIND_ARG, "pie");
        args.put(ProposeVisualisationTool.COLUMNS_ARG, mapOf("name", "To", "value", "From"));

        assertThat(call(args))
                .startsWith("No chart was offered")
                .contains("'From' can't be value: only 0 of the 3 values sampled are numbers");
        assertThat(takeUserActions()).isEmpty();
    }

    @Test
    void anUnknownColumnStopsTheChartNamingTheNearOne() {
        final Map<String, Object> args = graphArgs();
        args.put(ProposeVisualisationTool.COLUMNS_ARG, mapOf("source", "from", "target", "To"));

        assertThat(call(args))
                .startsWith("No chart was offered")
                .contains("The table has no column 'from' for source: did you mean 'From'?");
        assertThat(takeUserActions()).isEmpty();
    }

    @Test
    void withoutASampleOnlyTheColumnsAreCheckedAndTheReasonIsNoted() {
        Mockito.when(currentResultsTool.outcome(Mockito.any(ToolCall.class), Mockito.any(ToolCallContext.class)))
                .thenReturn(CurrentResultsTool.Outcome.problem("The table hasn't run yet.\nRun it first."));

        final String result = call(graphArgs());

        assertThat(result)
                .contains("'Create visualisation' button")
                .contains("Sample: The table hasn't run yet.")
                .doesNotContain("Run it first.")
                .contains("There were no rows to check the data against");
        assertThat(takeUserActions()).hasSize(1);
    }

    @Test
    void echartsNotInstalledIsSaidWithTheWayToInstallIt() {
        Mockito.when(chartLibraries.findECharts()).thenReturn(Optional.empty());

        assertThat(call(graphArgs()))
                .startsWith("No chart was offered: ECharts isn't installed in Stroom.")
                .contains("from an Apache ECharts release, version 6 or later.")
                .contains("No other charting library is installed either.")
                .doesNotContain("library d3");

        Mockito.when(chartLibraries.findD3()).thenReturn(Map.of(ChartKind.GRAPH, FORCE));
        Mockito.when(chartLibraries.findUnrecognisedECharts()).thenReturn(List.of(new DocRef("Script",
                "dev-uuid", "echarts-dev")));

        assertThat(call(graphArgs()))
                .contains("Script 'echarts-dev' doesn't hold an ECharts 6 or later build")
                .contains("offer the user a D3 chart instead (library d3)");
        assertThat(takeUserActions()).isEmpty();
    }

    @Test
    void aUserWithoutUseOnEChartsIsntOfferedAChart() {
        Mockito.when(chartLibraries.currentUserCanUse(ECHARTS_SCRIPT)).thenReturn(false);

        assertThat(call(graphArgs()))
                .startsWith("No chart was offered: the user doesn't have Use permission on Script 'ECharts'");
        assertThat(takeUserActions()).isEmpty();
        Mockito.verifyNoInteractions(auditLog);
    }

    @Test
    void theD3RouteOnADashboardGivesTheAddVisChangeToPropose() {
        Mockito.when(chartLibraries.findD3()).thenReturn(Map.of(ChartKind.GRAPH, FORCE));
        final Map<String, Object> args = graphArgs();
        args.put(ProposeVisualisationTool.LIBRARY_ARG, "D3");
        args.remove(ProposeVisualisationTool.NAME_ARG);

        final String result = call(args);

        assertThat(result)
                .startsWith("The data can make the chart with the content pack's D3 Visualisation 'Force' ("
                            + FORCE.getUuid() + ")")
                .contains("propose this change with propose_dashboard_change (component 'Links')")
                .doesNotContain("left out");
        assertThat(changeIn(result))
                .containsEntry("op", "add_vis")
                .containsEntry("table", "Links")
                // The D3 Visualisation's name, as none was given.
                .containsEntry("name", "Force")
                .containsEntry("visualisation", FORCE.getUuid())
                .containsEntry("settings", mapOf("source", "From", "target", "To", "count", "Count"))
                .containsEntry("placement", VisualisationPlan.BELOW);
        assertThat(takeUserActions()).isEmpty();
        Mockito.verifyNoInteractions(auditLog);
    }

    @Test
    void aRoleTheD3VisualisationLacksIsReportedAsLeftOut() {
        Mockito.when(chartLibraries.findD3()).thenReturn(Map.of(ChartKind.SCATTER, SCATTER));
        final Map<String, Object> args = graphArgs();
        args.put(ProposeVisualisationTool.LIBRARY_ARG, ProposeVisualisationTool.D3);
        args.put(ProposeVisualisationTool.KIND_ARG, "scatter");
        args.put(ProposeVisualisationTool.COLUMNS_ARG, mapOf("x", "Count", "y", "Count", "size", "Count"));

        final String result = call(args);

        assertThat(result).contains("The D3 scatter plot has no input for size, so it is left out");
        assertThat(changeIn(result))
                .containsEntry("name", "Message links")
                .containsEntry("settings", mapOf("x", "Count", "y", "Count"));
        assertThat(takeUserActions()).isEmpty();
    }

    @Test
    void theD3RouteOnAQueryGivesAShowClauseByUuidWithTheD3ControlIds() {
        queryTab();
        Mockito.when(chartLibraries.findD3()).thenReturn(Map.of(ChartKind.GRAPH, FORCE));
        final Map<String, Object> args = graphArgs();
        args.put(ProposeVisualisationTool.LIBRARY_ARG, ProposeVisualisationTool.D3);

        assertThat(call(args))
                .contains("with propose_query_change (it names 'Force' by its UUID")
                .contains("show \"" + FORCE.getUuid() + "\" (source = \"From\", target = \"To\", "
                          + "count = \"Count\")");
        assertThat(takeUserActions()).isEmpty();
    }

    @Test
    void aD3KindThatIsntInstalledIsSaid() {
        Mockito.when(chartLibraries.findD3()).thenReturn(Map.of(ChartKind.SCATTER, SCATTER));
        final Map<String, Object> args = graphArgs();
        args.put(ProposeVisualisationTool.LIBRARY_ARG, ProposeVisualisationTool.D3);

        assertThat(call(args))
                .isEqualTo("No chart was offered: the visualisations content pack's D3 network graph isn't "
                           + "installed. ECharts is: offer an ECharts chart instead (library echarts).");

        Mockito.when(chartLibraries.findECharts()).thenReturn(Optional.empty());

        assertThat(call(args)).endsWith("Tell the user it would need the content pack installed.");
        assertThat(takeUserActions()).isEmpty();
    }

    @Test
    void badArgumentsAreErrorsSayingWhatToGive() {
        assertThat(callWith(ProposeVisualisationTool.LIBRARY_ARG, "plotly"))
                .isEqualTo("Error: Give library as echarts or d3.");
        assertThat(callWith(ProposeVisualisationTool.KIND_ARG, "radar"))
                .isEqualTo("Error: Give kind as one of " + ChartKind.ids() + ".");
        assertThat(callWith(ProposeVisualisationTool.COLUMNS_ARG, "From, To"))
                .isEqualTo("Error: Give columns as an object of names to text values.");
        assertThat(callWith(ProposeVisualisationTool.STYLE_ARG, mapOf("maxNodes", "1")))
                .isEqualTo("Error: '1' isn't a value maxNodes takes: give a whole number from 2 to 5000.");
        assertThat(callWith(ProposeVisualisationTool.STYLE_ARG, mapOf("smooth", "true")))
                .startsWith("Error: A network graph has no setting 'smooth'. Its settings are: legend, ");
        assertThat(callWith(ProposeVisualisationTool.OPTIONS_ARG, "[\"#5470c6\"]"))
                .startsWith("Error: Options must be a JSON object");
        assertThat(callWith(ProposeVisualisationTool.OPTIONS_ARG, "not json"))
                .startsWith("Error: Options must be a JSON object");
        assertThat(callWith(ProposeVisualisationTool.OPTIONS_ARG, "{\"series\":[]}"))
                .startsWith("Error: Options can't set series. They may set: ");
        assertThat(callWith(ProposeVisualisationTool.NAME_ARG, null))
                .isEqualTo("Error: Give the new Visualisation a name of up to 100 characters, saying what it "
                           + "shows.");
        assertThat(callWith(ProposeVisualisationTool.NAME_ARG, "x".repeat(101)))
                .startsWith("Error: Give the new Visualisation a name");
        assertThat(callWith(ProposeVisualisationTool.PLACEMENT_ARG, "left"))
                .isEqualTo("Error: Give placement as below or tab.");
        assertThat(callWith(ProposeVisualisationTool.SUMMARY_ARG, "x".repeat(501)))
                .isEqualTo("Error: The summary may be at most 500 characters.");
        assertThat(takeUserActions()).isEmpty();
        Mockito.verifyNoInteractions(auditLog);
    }

    @Test
    void theKindCanBeGivenByName() {
        final Map<String, Object> args = graphArgs();
        args.put(ProposeVisualisationTool.KIND_ARG, "Network Graph");

        assertThat(call(args)).contains("'Create visualisation' button for a network graph");
        assertThat(takeUserActions()).hasSize(1);
    }

    @Test
    void withoutAnAttachedDashboardOrQueryNothingIsProposed() {
        Mockito.when(tabSnapshots.forLatestQuestion(CHAT)).thenReturn(Optional.empty());
        assertThat(call(graphArgs())).isEqualTo(ProposeVisualisationTool.NOT_ATTACHED);

        Mockito.when(tabSnapshots.forLatestQuestion(CHAT)).thenReturn(Optional.of(new StoredTabSnapshot(5, 1,
                new DocRef("XSLT", "x", "X"), "/", "X", null, false, null, "h", 0L, List.of(), List.of(), null)));
        assertThat(call(graphArgs())).isEqualTo(ProposeVisualisationTool.NOT_ATTACHED);
        assertThat(takeUserActions()).isEmpty();
    }

    @Test
    void aDashboardTheUserCantViewIsNotRevealed() {
        Mockito.when(documentAccess.canView(DASHBOARD)).thenReturn(false);

        assertThat(call(graphArgs())).isEqualTo(DocumentAccess.NO_PERMISSION).doesNotContain("Messages");
        assertThat(takeUserActions()).isEmpty();
        Mockito.verifyNoInteractions(auditLog);
    }

    @Test
    void aDashboardWithSeveralTablesNeedsToBeToldWhichToChart() {
        final AiTabSearch other = new AiTabSearch("Other", "table-2", null, null, List.of("A"), 1L, false);
        dashboardTab(List.of(linksTable(), other), false);

        assertThat(call(graphArgs())).isEqualTo("Say which table to chart: 'Links', 'Other'.");
        assertThat(takeUserActions()).isEmpty();

        final Map<String, Object> args = graphArgs();
        args.put(ProposeVisualisationTool.TABLE_ARG, "Links");
        assertThat(call(args)).contains("'Create visualisation' button");
        assertThat(takeUserActions()).hasSize(1);
    }

    @Test
    void aTabWithNoTablesOrSettingsCutShortCantBeCharted() {
        dashboardTab(List.of(), false);
        assertThat(call(graphArgs())).isEqualTo("The tab shows no table of results to chart. Run its query first.");

        dashboardTab(List.of(linksTable()), true);
        assertThat(call(graphArgs())).startsWith("The tab's settings aren't in its snapshot in full");
        assertThat(takeUserActions()).isEmpty();
    }

    @Test
    void aChartIsOnlyOfferedWhileAnsweringAQuestion() {
        final ToolResult result = tool.execute(new ToolCall("c1", ProposeVisualisationTool.NAME,
                JsonUtil.writeValueAsString(graphArgs())), ToolCalls.inChat(CHAT, workspace));

        assertThat(result.text()).isEqualTo("Error: a chart can only be offered while answering a question in a "
                                            + "chat.");
        assertThat(result.userActions()).isEmpty();
    }

    // The arguments for an ECharts network graph of who messages whom, to change per test.
    private static Map<String, Object> graphArgs() {
        return mapOf(
                ProposeVisualisationTool.LIBRARY_ARG, ProposeVisualisationTool.ECHARTS,
                ProposeVisualisationTool.KIND_ARG, "graph",
                ProposeVisualisationTool.COLUMNS_ARG, mapOf("source", "From", "target", "To", "weight", "Count"),
                ProposeVisualisationTool.NAME_ARG, "Message links",
                ProposeVisualisationTool.SUMMARY_ARG, "Who messages whom.");
    }

    // A map of the keys and values given, in order.
    @SuppressWarnings("unchecked")
    private static <T> Map<String, T> mapOf(final Object... keysAndValues) {
        final Map<String, T> map = new LinkedHashMap<>();
        for (int i = 0; i < keysAndValues.length; i += 2) {
            map.put((String) keysAndValues[i], (T) keysAndValues[i + 1]);
        }
        return map;
    }

    // The dashboard table 'Links': From and To as text, Count as a number.
    private static AiTabSearch linksTable() {
        return linksTable(Column.builder().id("c1").name("From").expression("${From}").format(Format.TEXT).build());
    }

    // The dashboard table 'Links', with the From column given.
    private static AiTabSearch linksTable(final Column from) {
        return linksTable(from,
                Column.builder().id("c2").name("To").expression("${To}").format(Format.TEXT).build());
    }

    // The dashboard table 'Links', with the From and To columns given.
    private static AiTabSearch linksTable(final Column from, final Column to) {
        final List<Column> columns = List.of(
                from,
                to,
                Column.builder().id("c3").name("Count").expression("count()").format(Format.NUMBER).build());
        final DashboardTableContext live = new DashboardTableContext("Dashboard 'Messages'", null,
                DashboardSearchRequest.builder()
                        .componentResultRequests(List.of(TableResultRequest.builder()
                                .componentId("table-1")
                                .tableSettings(TableSettings.builder().columns(columns).build())
                                .build()))
                        .build());
        return new AiTabSearch("Links", "table-1", null, live, List.of("From", "To", "Count"), 3L, false);
    }

    private void dashboardTab(final List<AiTabSearch> tables, final boolean cut) {
        final StoredTabSnapshot snapshot = new StoredTabSnapshot(5, 1, DASHBOARD, "/", "Messages", "Dashboard",
                false, null, "h", 0L, List.of(), tables, null);
        Mockito.when(tabSnapshots.forLatestQuestion(CHAT)).thenReturn(Optional.of(snapshot));
        Mockito.when(tabSnapshots.part(CHAT, snapshot, AiTabSnapshotPart.DASHBOARD))
                .thenReturn(Optional.of(new UnsavedPart(DASHBOARD_TEXT, cut, "/tabs/1/dashboard.md")));
    }

    // A StroomQL query tab, whose table's column formats aren't known.
    @Test
    void anEmbeddedQueryIsChartedInItsShowClause() {
        embeddedQueryTab("Query: embedded in the dashboard");

        assertThat(call(graphArgs()))
                .contains("shown a 'Create visualisation' button for a network graph of table 'Pairs'")
                .contains("in the dashboard's folder")
                .contains("put in the show clause of embedded query 'Pairs' on dashboard 'Messages', naming it by "
                          + "its UUID (show \"<its uuid>\" (source = \"From\", target = \"To\", weight = "
                          + "\"Count\")), which then runs, unsaved");
        assertThat(takeUserActions()).singleElement().satisfies(action -> {
            assertThat(action).containsEntry("doc", Map.of("type", DASHBOARD.getType(), "uuid",
                    DASHBOARD.getUuid()));
            final VisualisationPlan plan = VisualisationPlan.fromJson((String) action.get("plan"));
            assertThat(plan.isEmbeddedQuery()).isTrue();
            assertThat(plan.componentId()).isEqualTo("query-1");
            assertThat(plan.table()).isEqualTo("Pairs");
            assertThat(plan.componentName()).isNull();
            assertThat(plan.placement()).isNull();
            // Planned against its StroomQL as it stands, and the dashboard as it is.
            assertThat(plan.baseQuery()).isEqualTo(EMBEDDED_QUERY_TEXT);
            assertThat(plan.snapshotHash()).isEqualTo(AiTabSnapshotPart.hashText(embeddedDashboardText(
                    "Query: embedded in the dashboard")));
        });
    }

    @Test
    void anEmbeddedQueryShowingAQueryByReferenceIsChartedInThatQuery() {
        embeddedQueryTab("Query: Query 'Saved pairs' (uuid q-uuid) by reference");

        assertThat(call(graphArgs()))
                .startsWith("No chart was offered: embedded query 'Pairs' shows Query 'Saved pairs' by reference")
                .contains("Ask the user to open Query 'Saved pairs', attach its tab, and ask again.");
        assertThat(takeUserActions()).isEmpty();
    }

    @Test
    void anEmbeddedQueryIsChartedWithEChartsOnly() {
        embeddedQueryTab("Query: embedded in the dashboard");
        Mockito.when(chartLibraries.findD3()).thenReturn(Map.of(ChartKind.GRAPH, FORCE));

        assertThat(callWith(ProposeVisualisationTool.LIBRARY_ARG, ProposeVisualisationTool.D3))
                .startsWith("No chart was offered: an embedded query is charted by a show clause")
                .endsWith("Use library echarts.");
        assertThat(callWith(ProposeVisualisationTool.REPLACE_ARG, "Old chart"))
                .startsWith("Error: Only a chart on a dashboard replaces another with replace");
        assertThat(takeUserActions()).isEmpty();
    }

    // A dashboard 'Messages' whose embedded query 'Pairs' gives From, To and Count.
    private void embeddedQueryTab(final String queryLine) {
        final QueryTableContext live = new QueryTableContext("Embedded query 'Pairs'", null,
                QuerySearchRequest.builder().query(EMBEDDED_QUERY_TEXT).build());
        final AiTabSearch pairs = new AiTabSearch("Pairs", "query-1", null, live, List.of("From", "To", "Count"),
                3L, false);
        final StoredTabSnapshot snapshot = new StoredTabSnapshot(5, 1, DASHBOARD, "/", "Messages", "Dashboard",
                false, null, "h", 0L, List.of(), List.of(pairs), null);
        Mockito.when(tabSnapshots.forLatestQuestion(CHAT)).thenReturn(Optional.of(snapshot));
        Mockito.when(tabSnapshots.part(CHAT, snapshot, AiTabSnapshotPart.DASHBOARD))
                .thenReturn(Optional.of(new UnsavedPart(embeddedDashboardText(queryLine), false,
                        "/tabs/1/dashboard.md")));
    }

    private static String embeddedDashboardText(final String queryLine) {
        return "Time range: All time\nShown components: Pairs\n\n## Embedded Query 'Pairs' (id query-1)\n"
               + queryLine + "\nStroomQL:\n```\n" + EMBEDDED_QUERY_TEXT + "\n```\n";
    }

    private void queryTab() {
        final AiTabSearch table = new AiTabSearch(null, "table", null, null, List.of("From", "To", "Count"), 3L,
                false);
        final StoredTabSnapshot snapshot = new StoredTabSnapshot(5, 1, QUERY, "/", "Tweets", null, false, null,
                "snap-hash", 0L, List.of(new StoredPart(AiTabSnapshotPart.QUERY, "application/x-stroomql",
                "/tabs/1/query.sql", 10, 10, false)), List.of(table), null);
        Mockito.when(tabSnapshots.forLatestQuestion(CHAT)).thenReturn(Optional.of(snapshot));
        Mockito.when(tabSnapshots.part(CHAT, snapshot, AiTabSnapshotPart.QUERY))
                .thenReturn(Optional.of(new UnsavedPart(QUERY_TEXT, false, "/tabs/1/query.sql")));
    }

    // The add_vis change a D3 result gives, read from its JSON line.
    @SuppressWarnings("unchecked")
    private static Map<String, Object> changeIn(final String result) {
        final String json = result.lines()
                .filter(line -> line.startsWith("{"))
                .findFirst()
                .orElseThrow();
        return JsonUtil.readValue(json, Map.class);
    }

    // Runs a call with one argument changed (or removed, for null) from graphArgs.
    private String callWith(final String name, final Object value) {
        final Map<String, Object> args = graphArgs();
        if (value == null) {
            args.remove(name);
        } else {
            args.put(name, value);
        }
        return call(args);
    }

    // Runs a call as part of the agent run, keeping the user actions its result offers for takeUserActions.
    private String call(final Map<String, Object> args) {
        final ToolResult result = context.run(() -> tool.execute(
                new ToolCall("c1", ProposeVisualisationTool.NAME, JsonUtil.writeValueAsString(args)),
                ToolCalls.inChat(CHAT, workspace)));
        result.userActions().forEach(action -> offered.add(action.toMap()));
        return result.text();
    }

    // The user actions offered by the calls since this was last called, as the chat shows them.
    private List<Map<String, Object>> takeUserActions() {
        final List<Map<String, Object>> taken = List.copyOf(offered);
        offered.clear();
        return taken;
    }
}
