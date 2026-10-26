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
import stroom.ai.impl.DashboardBuilder;
import stroom.ai.impl.DashboardComponents;
import stroom.ai.impl.TabSnapshots;
import stroom.ai.impl.TabSnapshots.StoredPart;
import stroom.ai.impl.TabSnapshots.StoredTabSnapshot;
import stroom.ai.impl.TabSnapshots.UnsavedPart;
import stroom.ai.impl.UserActions;
import stroom.ai.impl.vis.ChartKind;
import stroom.ai.impl.vis.ChartLibraries;
import stroom.ai.impl.vis.ChartLibraries.D3Install;
import stroom.ai.impl.vis.ChartLibraries.EChartsInstall;
import stroom.ai.impl.vis.ChartLibrary;
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
import stroom.query.impl.QueryService;
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
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/// propose_visualisation: plans a chart of a table on the dashboard (or the StroomQL query) the user attached,
/// checks the table's sampled rows can make it, and offers a 'Create visualisation' button (ECharts or D3) or the
/// change that wires the content pack's D3 Visualisation to the table (content-pack).
class TestProposeVisualisationTool {

    private static final int CHAT = 3;
    private static final DocRef DASHBOARD = new DocRef("Dashboard", "1c2d3e4f-5a6b-4c7d-8e9f-0a1b2c3d4e5f",
            "Messages");
    private static final DocRef QUERY = new DocRef("Query", "2d3e4f5a-6b7c-4d8e-9f0a-1b2c3d4e5f6a", "Tweets");
    private static final DocRef ECHARTS_SCRIPT = new DocRef("Script", "echarts-uuid", "ECharts");
    private static final DocRef D3_SCRIPT = new DocRef("Script", "d3-uuid", "D3");
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
    private final DataSourceAccess dataSourceAccess = Mockito.mock(DataSourceAccess.class);
    private final QueryService queryService = Mockito.mock(QueryService.class);
    private final DashboardBuilder dashboardBuilder = Mockito.mock(DashboardBuilder.class);
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
        Mockito.when(chartLibraries.findContentPackD3()).thenReturn(Map.of());
        Mockito.when(chartLibraries.findD3Library()).thenReturn(Optional.empty());
        Mockito.when(chartLibraries.findUnrecognisedECharts()).thenReturn(List.of());

        tool = new ProposeVisualisationTool(workspace, tabSnapshots, documentAccess, currentResultsTool,
                chartLibraries, auditLog, new ProposeDashboardChangeTool(workspace, tabSnapshots, documentAccess,
                dataSourceAccess, () -> queryService, auditLog, dashboardBuilder));
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
                .contains("shown an 'Update visualisation' button for a network graph of table 'Links'")
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

        final Map<String, Object> contentPack = graphArgs();
        contentPack.put(ProposeVisualisationTool.LIBRARY_ARG, ProposeVisualisationTool.CONTENT_PACK);
        contentPack.put(ProposeVisualisationTool.REPLACE_ARG, "Old chart");
        assertThat(call(contentPack))
                .startsWith("Error: Only a generated chart (library echarts or d3) can replace one");

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

        Mockito.when(chartLibraries.findContentPackD3()).thenReturn(Map.of(ChartKind.GRAPH, FORCE));
        Mockito.when(chartLibraries.findUnrecognisedECharts()).thenReturn(List.of(new DocRef("Script",
                "dev-uuid", "echarts-dev")));

        assertThat(call(graphArgs()))
                .contains("Script 'echarts-dev' doesn't hold an ECharts 6 or later build")
                .contains("The content pack's D3 Visualisations are installed: offer one instead (library "
                          + "content-pack).");

        Mockito.when(chartLibraries.findD3Library()).thenReturn(Optional.of(new D3Install(D3_SCRIPT, "7.9.0")));

        assertThat(call(graphArgs())).contains("D3 is installed: offer a D3 chart instead (library d3).");
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
    void aD3ChartIsOfferedAsACreateVisualisationButtonDrawnWithD3() {
        d3Installed();
        final Map<String, Object> args = graphArgs();
        args.put(ProposeVisualisationTool.LIBRARY_ARG, ProposeVisualisationTool.D3);
        args.put(ProposeVisualisationTool.STYLE_ARG, mapOf("directed", "true"));
        args.put(ProposeVisualisationTool.OPTIONS_ARG, "{\"color\": [\"#5470c6\"]}");

        assertThat(call(args)).contains("shown a 'Create visualisation' button for a network graph of table 'Links' "
                                        + "(source = 'From', target = 'To', weight = 'Count'), drawn with D3 7.9.0");
        assertThat(takeUserActions()).singleElement().satisfies(action -> {
            final VisualisationPlan plan = VisualisationPlan.fromJson((String) action.get("plan"));
            assertThat(plan.chartLibrary()).isEqualTo(ChartLibrary.D3);
            assertThat(plan.style()).containsExactlyEntriesOf(mapOf("directed", "true"));
            assertThat(plan.options()).isEqualTo("{\"color\":[\"#5470c6\"]}");
        });
    }

    @Test
    void d3DrawsEverySettingButTakesOnlyColourOptions() {
        d3Installed();
        final Map<String, Object> args = graphArgs();
        args.put(ProposeVisualisationTool.LIBRARY_ARG, ProposeVisualisationTool.D3);
        args.put(ProposeVisualisationTool.KIND_ARG, "line");
        args.put(ProposeVisualisationTool.COLUMNS_ARG, mapOf("x", "Count", "y", "Count"));
        args.put(ProposeVisualisationTool.STYLE_ARG, mapOf("zoomSlider", "true", "breakOutliers", "true"));

        assertThat(call(args)).doesNotStartWith("Error: ");
        assertThat(takeUserActions()).singleElement().satisfies(action ->
                assertThat(VisualisationPlan.fromJson((String) action.get("plan")).style())
                        .containsEntry("zoomSlider", "true").containsEntry("breakOutliers", "true"));

        // Its options are ECharts' (here, its grid), which D3 doesn't draw.
        args.put(ProposeVisualisationTool.STYLE_ARG, mapOf());
        args.put(ProposeVisualisationTool.OPTIONS_ARG, "{\"grid\": {\"left\": 10}}");

        assertThat(call(args)).startsWith("Error: ").contains("color");
        assertThat(takeUserActions()).isEmpty();
    }

    @Test
    void d3NotInstalledIsSaidWithTheWayToInstallIt() {
        final Map<String, Object> args = graphArgs();
        args.put(ProposeVisualisationTool.LIBRARY_ARG, ProposeVisualisationTool.D3);

        assertThat(call(args))
                .startsWith("No chart was offered: D3 isn't installed in Stroom.")
                .contains("version 3 or later")
                .contains("ECharts is installed: offer an ECharts chart instead (library echarts).");

        d3Installed();
        Mockito.when(chartLibraries.currentUserCanUse(D3_SCRIPT)).thenReturn(false);

        assertThat(call(args))
                .startsWith("No chart was offered: the user doesn't have Use permission on Script 'D3' (D3)");
        assertThat(takeUserActions()).isEmpty();
    }

    @Test
    void theContentPackRouteOnADashboardGivesTheAddVisChangeToPropose() {
        Mockito.when(chartLibraries.findContentPackD3()).thenReturn(Map.of(ChartKind.GRAPH, FORCE));
        final Map<String, Object> args = graphArgs();
        args.put(ProposeVisualisationTool.LIBRARY_ARG, "Content-Pack");
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
    void aRoleTheContentPackVisualisationLacksIsReportedAsLeftOut() {
        Mockito.when(chartLibraries.findContentPackD3()).thenReturn(Map.of(ChartKind.SCATTER, SCATTER));
        final Map<String, Object> args = graphArgs();
        args.put(ProposeVisualisationTool.LIBRARY_ARG, ProposeVisualisationTool.CONTENT_PACK);
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
    void theContentPackRouteOnAQueryGivesAShowClauseByUuidWithTheD3ControlIds() {
        queryTab();
        Mockito.when(chartLibraries.findContentPackD3()).thenReturn(Map.of(ChartKind.GRAPH, FORCE));
        final Map<String, Object> args = graphArgs();
        args.put(ProposeVisualisationTool.LIBRARY_ARG, ProposeVisualisationTool.CONTENT_PACK);

        assertThat(call(args))
                .contains("with propose_query_change (it names 'Force' by its UUID")
                .contains("show \"" + FORCE.getUuid() + "\" (source = \"From\", target = \"To\", "
                          + "count = \"Count\")");
        assertThat(takeUserActions()).isEmpty();
    }

    @Test
    void aKindTheContentPackDoesntHaveIsGeneratedInstead() {
        final Map<String, Object> args = graphArgs();
        args.put(ProposeVisualisationTool.LIBRARY_ARG, ProposeVisualisationTool.CONTENT_PACK);
        args.put(ProposeVisualisationTool.KIND_ARG, "sankey");
        args.put(ProposeVisualisationTool.COLUMNS_ARG, mapOf("source", "From", "target", "To", "value", "Count"));

        assertThat(call(args)).isEqualTo("No chart was offered: the visualisations content pack has no D3 sankey "
                                         + "diagram. Generate it instead (library echarts).");

        Mockito.when(chartLibraries.findD3Library()).thenReturn(Optional.of(new D3Install(D3_SCRIPT, "7.9.0")));

        assertThat(call(args)).endsWith("Generate it instead (library echarts or d3).");
        assertThat(takeUserActions()).isEmpty();
    }

    @Test
    void aContentPackKindThatIsntInstalledIsSaid() {
        Mockito.when(chartLibraries.findContentPackD3()).thenReturn(Map.of(ChartKind.SCATTER, SCATTER));
        final Map<String, Object> args = graphArgs();
        args.put(ProposeVisualisationTool.LIBRARY_ARG, ProposeVisualisationTool.CONTENT_PACK);

        assertThat(call(args))
                .isEqualTo("No chart was offered: the visualisations content pack's D3 network graph isn't "
                           + "installed. Generate it instead (library echarts).");

        Mockito.when(chartLibraries.findECharts()).thenReturn(Optional.empty());

        assertThat(call(args)).endsWith("Neither ECharts nor D3 is installed to generate one instead: tell the "
                                        + "user an administrator would need to install one.");
        assertThat(takeUserActions()).isEmpty();
    }

    @Test
    void badArgumentsAreErrorsSayingWhatToGive() {
        assertThat(callWith(ProposeVisualisationTool.LIBRARY_ARG, "plotly"))
                .isEqualTo("Error: Give library as echarts, d3 or content-pack.");
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
    void clickFilteringIsOfferedWhenTheChartAndDataCanFilter() {
        // A bar's category reads a field as it is: the search can be filtered by it, or a table's rows.
        assertThat(ProposeVisualisationTool.clickFilterNote(ChartKind.BAR,
                Map.of("category", "User", "value", "Count"), Map.of("User", "${UserId}", "Count", "count()"),
                Map.of(), false, "Events"))
                .contains("a click on it selects category (column 'User')")
                .contains("the search of the query feeding 'Events' by field(s) UserId (by category)")
                .contains("or a table's rows by column(s) User")
                .contains("It was offered without a filter (next time, ask first and give filter, to add it with "
                          + "the chart)");

        // Columns made by an expression can only filter a table's rows.
        assertThat(ProposeVisualisationTool.clickFilterNote(ChartKind.GRAPH,
                Map.of("source", "User A", "target", "User B"),
                Map.of("User A", "if(${a} < ${b}, ${a}, ${b})", "User B", "if(${a} < ${b}, ${b}, ${a})"),
                Map.of(), false, "Pairs"))
                .contains("node (a node's name, from either end)")
                .contains("the rows of table 'Pairs' or another table by column(s)")
                .doesNotContain("the search of the query");

        // Not offered where nothing could be filtered: a histogram's bars select nothing, a StroomQL Query has
        // nothing else to filter, and a chart with no clicked column mapped selects nothing.
        assertThat(ProposeVisualisationTool.clickFilterNote(ChartKind.HISTOGRAM, Map.of("value", "Bytes",
                "series", "Host"), Map.of(), Map.of(), false, "Events")).isEmpty();
        assertThat(ProposeVisualisationTool.clickFilterNote(ChartKind.BAR, Map.of("category", "User"),
                Map.of("User", "${UserId}"), Map.of(), true, "Events")).isEmpty();
        assertThat(ProposeVisualisationTool.clickFilterNote(ChartKind.SCATTER, Map.of("size", "Bytes"),
                Map.of(), Map.of(), false, "Events")).isEmpty();
    }

    @Test
    void aTimeSeriesChartOffersFilteringByATimeRange() {
        // A line chart whose x is a Date Time column reading a field: its range tool can filter the search by it.
        assertThat(ProposeVisualisationTool.clickFilterNote(ChartKind.LINE,
                Map.of("x", "Time", "y", "Count", "series", "Host"), Map.of("Time", "${EventTime}"),
                Map.of("Time", "DATE_TIME"), false, "Events"))
                .contains("Dates: a term giving a date field (or a table's date column) the selection x (column "
                          + "'Time') keeps it within the selected time, never equal to it: a range dragged along "
                          + "its time axis (its range tool), or the time a clicked point stands for (e.g. its "
                          + "whole day, for floorDay() values), e.g. the query's field EventTime.");

        // A calendar of floorDay() values: a clicked day filters the field it rounds, with no range tool.
        assertThat(ProposeVisualisationTool.clickFilterNote(ChartKind.CALENDAR,
                Map.of("date", "Day", "value", "Count"), Map.of("Day", "floorDay(${EventTime})"),
                Map.of("Day", "DATE_TIME"), false, "Events"))
                .contains("the selection date (column 'Day') keeps it within the selected time, never equal to it: "
                          + "the time a clicked point stands for")
                .contains("e.g. the query's field EventTime.")
                .doesNotContain("range tool");

        // Not when x isn't a date.
        assertThat(ProposeVisualisationTool.clickFilterNote(ChartKind.LINE,
                Map.of("x", "Day", "y", "Count", "series", "Host"), Map.of("Day", "${Day}"),
                Map.of("Day", "TEXT"), false, "Events"))
                .doesNotContain("Dates:");
    }

    @Test
    void severalChartsAreOfferedTogetherAndAChartTheDataCantMakeIsLeftOut() {
        final Map<String, Object> args = chartsArgs();

        // How to arrange them is asked first.
        assertThat(call(args)).startsWith("No charts were offered yet: ask the user (ask_user) how to arrange the 3 "
                                          + "charts on the dashboard");
        assertThat(takeUserActions()).isEmpty();

        args.put(ProposeVisualisationTool.LAYOUT_ARG, ProposeVisualisationTool.TABS);
        final String result = call(args);

        assertThat(result)
                .contains("shown a 'Create visualisations' button for 2 chart(s)")
                .contains("'Messages by sender': a bar chart of 'Links' (category = 'From', value = 'Count')")
                .contains("stacked as tabs in one pane")
                .contains("Left out (tell the user why, and the fix):\n- Chart 3 ('Nope'): the table 'Links' "
                          + "can't make this bar chart");
        assertThat(takeUserActions()).singleElement().satisfies(action -> {
            assertThat(action).containsEntry("action", UserActions.CREATE_VISUALISATIONS);
            final List<?> charts = (List<?>) action.get(ProposeVisualisationTool.CHARTS_ARG);
            assertThat(charts).hasSize(2);
            final VisualisationPlan first = VisualisationPlan.fromJson((String) ((Map<?, ?>) charts.get(0))
                    .get("plan"));
            final VisualisationPlan second = VisualisationPlan.fromJson((String) ((Map<?, ?>) charts.get(1))
                    .get("plan"));
            assertThat(first.placement()).isEqualTo(VisualisationPlan.BELOW);
            assertThat(second.placement()).isEqualTo(VisualisationPlan.WITH);
            assertThat(second.chartKind()).isEqualTo(ChartKind.PIE);
        });
    }

    @Test
    void severalChartsAreGeneratedNotTakenFromTheContentPack() {
        final Map<String, Object> args = chartsArgs();
        args.put(ProposeVisualisationTool.LIBRARY_ARG, ProposeVisualisationTool.CONTENT_PACK);

        assertThat(call(args)).startsWith("No charts were offered: several charts at once are generated (library "
                                          + "echarts or d3)");
        assertThat(takeUserActions()).isEmpty();
    }

    @Test
    void severalChartsCanBeDrawnWithD3() {
        d3Installed();
        final Map<String, Object> args = chartsArgs();
        args.put(ProposeVisualisationTool.LIBRARY_ARG, ProposeVisualisationTool.D3);
        args.put(ProposeVisualisationTool.LAYOUT_ARG, ProposeVisualisationTool.ROW);

        assertThat(call(args)).contains("shown a 'Create visualisations' button for 2 chart(s), drawn with D3 7.9.0");
        assertThat(takeUserActions()).singleElement().satisfies(action -> {
            final List<?> charts = (List<?>) action.get(ProposeVisualisationTool.CHARTS_ARG);
            assertThat(charts).allSatisfy(chart -> assertThat(VisualisationPlan.fromJson(
                    (String) ((Map<?, ?>) chart).get("plan")).chartLibrary()).isEqualTo(ChartLibrary.D3));
        });
    }

    @Test
    void aLayoutPlacesEachChartAfterItsTablesFirst() {
        assertThat(IntStream.range(0, 5)
                .mapToObj(i -> ProposeVisualisationTool.placement(ProposeVisualisationTool.GRID, i)))
                .containsExactly(VisualisationPlan.BELOW, VisualisationPlan.RIGHT, VisualisationPlan.RIGHT,
                        VisualisationPlan.UNDER, VisualisationPlan.RIGHT);
        assertThat(ProposeVisualisationTool.placement(ProposeVisualisationTool.ROW, 1)).isEqualTo(
                VisualisationPlan.RIGHT);
        assertThat(ProposeVisualisationTool.placement(ProposeVisualisationTool.COLUMN, 1)).isEqualTo(
                VisualisationPlan.UNDER);
        assertThat(ProposeVisualisationTool.placement(ProposeVisualisationTool.TABS, 1)).isEqualTo(
                VisualisationPlan.WITH);
    }

    // D3 7.9.0 installed, which the user may use.
    private void d3Installed() {
        Mockito.when(chartLibraries.findD3Library()).thenReturn(Optional.of(new D3Install(D3_SCRIPT, "7.9.0")));
        Mockito.when(chartLibraries.currentUserCanUse(D3_SCRIPT)).thenReturn(true);
    }

    // Three charts of table 'Links': two it can make and one naming a column it doesn't have.
    private static Map<String, Object> chartsArgs() {
        return mapOf(
                ProposeVisualisationTool.LIBRARY_ARG, ProposeVisualisationTool.ECHARTS,
                ProposeVisualisationTool.SUMMARY_ARG, "Messages by sender and by recipient.",
                ProposeVisualisationTool.CHARTS_ARG, List.of(
                        mapOf(ProposeVisualisationTool.KIND_ARG, "bar",
                                ProposeVisualisationTool.COLUMNS_ARG, mapOf("category", "From", "value", "Count"),
                                ProposeVisualisationTool.NAME_ARG, "Messages by sender"),
                        mapOf(ProposeVisualisationTool.KIND_ARG, "pie",
                                ProposeVisualisationTool.COLUMNS_ARG, mapOf("name", "To", "value", "Count"),
                                ProposeVisualisationTool.NAME_ARG, "Messages by recipient"),
                        mapOf(ProposeVisualisationTool.KIND_ARG, "bar",
                                ProposeVisualisationTool.COLUMNS_ARG, mapOf("category", "Nope", "value", "Count"),
                                ProposeVisualisationTool.NAME_ARG, "Nope")));
    }

    @Test
    void withoutAnAttachedDashboardOrQueryNothingIsProposed() {
        Mockito.when(tabSnapshots.forLatestQuestion(CHAT)).thenReturn(Optional.empty());
        assertThat(call(graphArgs())).startsWith("No tab is attached to this question, so no chart can be proposed. "
                                                 + "No dashboard or StroomQL query has been attached to this chat");

        Mockito.when(tabSnapshots.forLatestQuestion(CHAT)).thenReturn(Optional.of(new StoredTabSnapshot(5, 1,
                new DocRef("XSLT", "x", "X"), "/", "X", null, false, null, "h", 0L, List.of(), List.of(), null)));
        assertThat(call(graphArgs())).startsWith("The tab attached to this question is XSLT 'X', not a dashboard or "
                                                 + "StroomQL query, so no chart can be proposed.");
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
    @Test
    void aSunburstRingsTheTablesColumnsFromTheInside() {
        final Map<String, Object> args = mapOf(
                ProposeVisualisationTool.LIBRARY_ARG, ProposeVisualisationTool.ECHARTS,
                ProposeVisualisationTool.KIND_ARG, "sunburst",
                ProposeVisualisationTool.COLUMNS_ARG, mapOf("name", "From", "level2", "To", "value", "Count"),
                ProposeVisualisationTool.NAME_ARG, "Messages by sender and recipient",
                ProposeVisualisationTool.SUMMARY_ARG, "Who sends, then to whom.",
                ProposeVisualisationTool.ADD_NEW_ARG, true);

        assertThat(call(args)).contains("'Create visualisation' button for a sunburst of table 'Links'")
                .contains("innerSegments 3")
                // A click selects its path, by which the dashboard can be filtered.
                .contains("a click on it selects name (column 'From'), level2 (column 'To')");
        assertThat(takeUserActions()).singleElement().satisfies(action ->
                assertThat(VisualisationPlan.fromJson((String) action.get("plan")).mappings())
                        .containsEntry("level2", "To"));

        // The inner ring is needed.
        args.put(ProposeVisualisationTool.COLUMNS_ARG, mapOf("level2", "To"));
        assertThat(call(args)).contains("Map a column to name");
    }

    @Test
    void aChartOfANewTableAddsTheTableWithIt() {
        // Seen in testing: a table, then a reload, then the chart took three steps; the chart's button now adds a
        // table of its own with it.
        final Map<String, Object> args = mapOf(
                ProposeVisualisationTool.LIBRARY_ARG, ProposeVisualisationTool.ECHARTS,
                ProposeVisualisationTool.KIND_ARG, "line",
                ProposeVisualisationTool.COLUMNS_ARG, mapOf("x", "Day", "y", "Messages", "series", "From"),
                ProposeVisualisationTool.NAME_ARG, "Messages by day",
                ProposeVisualisationTool.SUMMARY_ARG, "Messages each day, by sender.",
                ProposeVisualisationTool.NEW_TABLE_ARG, newTableChanges());

        assertThat(call(args))
                .contains("'Create visualisation' button for a line chart of table 'Daily'")
                .contains("the new table 'Daily' hasn't run, so only its columns were checked");
        assertThat(takeUserActions()).singleElement().satisfies(action -> {
            final VisualisationPlan plan = VisualisationPlan.fromJson((String) action.get("plan"));
            assertThat(plan.table()).isEqualTo("Daily");
            // Added beside table 'Links', whose component the browser applies the changes to.
            assertThat(plan.componentId()).isEqualTo("table-1");
            assertThat(plan.tableChanges()).extracting(change -> change.get("op"))
                    .containsExactly("add_table", "add_column", "add_column", "add_column", "group", "group");
            assertThat(plan.filterChanges()).isNull();
        });

        // A column the new table doesn't have.
        args.put(ProposeVisualisationTool.COLUMNS_ARG, mapOf("x", "Hour", "y", "Messages"));
        assertThat(call(args)).startsWith("No chart was offered: the table 'Daily' can't make this line chart");

        // The table's changes must start by adding it.
        args.put(ProposeVisualisationTool.NEW_TABLE_ARG, newTableChanges().subList(1, 3));
        assertThat(call(args)).startsWith("Error: newTable must start with add_table");
        assertThat(takeUserActions()).isEmpty();
    }

    @Test
    void aFilterIsAddedWithTheChartItsClicksDrive() {
        final Map<String, Object> args = graphArgs();
        args.put(ProposeVisualisationTool.ADD_NEW_ARG, true);
        args.put(ProposeVisualisationTool.FILTER_ARG, mapOf("mode", "table", "target", "Links", "match", "any",
                "terms", List.of(mapOf("field", "From", "selection", "node"), mapOf("field", "To", "selection",
                        "node"), mapOf("field", "From", "selection", "source"), mapOf("field", "To", "selection",
                        "target"))));

        assertThat(call(args)).contains("Its clicks filter the dashboard (added with it): Filter the rows of "
                                        + "'Links' by what is selected in chart 'Message links'");
        assertThat(takeUserActions()).singleElement().satisfies(action -> {
            final List<Map<String, Object>> filter = VisualisationPlan.fromJson((String) action.get("plan"))
                    .filterChanges();
            assertThat(filter).singleElement().satisfies(change -> {
                assertThat(change).containsEntry("op", "add_selection_filter")
                        .containsEntry("name", "Message links")
                        .containsEntry("ref", ProposeVisualisationTool.NEW_CHART_ID)
                        .containsEntry("target_id", "table-1");
                assertThat(JsonUtil.writeValueAsString(change.get("query"), false))
                        .contains("${component.new-chart.selection.node}")
                        .contains("${component.new-chart.selection.source}");
            });
        });

        // A chart that replaces another keeps its filters.
        args.remove(ProposeVisualisationTool.ADD_NEW_ARG);
        args.put(ProposeVisualisationTool.REPLACE_ARG, "Old chart");
        Mockito.when(chartLibraries.isGenerated(OLD_CHART)).thenReturn(true);
        Mockito.when(documentAccess.canEdit(OLD_CHART)).thenReturn(true);
        assertThat(call(args)).startsWith("Error: A chart that replaces another keeps its table and filters");

        // A selection the chart doesn't give.
        args.remove(ProposeVisualisationTool.REPLACE_ARG);
        args.put(ProposeVisualisationTool.ADD_NEW_ARG, true);
        args.put(ProposeVisualisationTool.FILTER_ARG, mapOf("mode", "table", "target", "Links",
                "terms", List.of(mapOf("field", "From", "selection", "colour"))));
        assertThat(call(args)).startsWith("No chart was offered: its filter isn't valid")
                .contains("doesn't select 'colour'");
        assertThat(takeUserActions()).isEmpty();
    }

    @Test
    void aNewTablesDayFiltersByItsTime() {
        // A day the new chart selects filters the new table's day column by time, as an existing table's would.
        final Map<String, Object> args = mapOf(
                ProposeVisualisationTool.LIBRARY_ARG, ProposeVisualisationTool.ECHARTS,
                ProposeVisualisationTool.KIND_ARG, "line",
                ProposeVisualisationTool.COLUMNS_ARG, mapOf("x", "Day", "y", "Messages"),
                ProposeVisualisationTool.NAME_ARG, "Messages by day",
                ProposeVisualisationTool.SUMMARY_ARG, "Messages each day.",
                ProposeVisualisationTool.NEW_TABLE_ARG, newTableChanges(),
                ProposeVisualisationTool.FILTER_ARG, mapOf("mode", "table", "target", "Daily",
                        "terms", List.of(mapOf("field", "Day", "selection", "x"))));

        assertThat(call(args)).contains("Day within its selected time");
        assertThat(takeUserActions()).singleElement().satisfies(action -> {
            final Map<String, Object> change = VisualisationPlan.fromJson((String) action.get("plan"))
                    .filterChanges().getFirst();
            // The new table is found by its name once added: it has no id yet.
            assertThat(change).containsEntry("target", "Daily");
            assertThat(JsonUtil.writeValueAsString(change.get("query"), false))
                    .contains("${component.new-chart.selection.from}")
                    .contains("${component.new-chart.selection.to}");
        });

        // A column the new table doesn't have.
        args.put(ProposeVisualisationTool.FILTER_ARG, mapOf("mode", "table", "target", "Daily",
                "terms", List.of(mapOf("field", "Hour", "selection", "x"))));
        assertThat(call(args)).startsWith("No chart was offered: its filter isn't valid: the new table has no "
                                          + "column Hour");
    }

    // A table of messages a day by sender, added beside 'Links'.
    private static List<Map<String, Object>> newTableChanges() {
        return new ArrayList<>(List.of(
                mapOf("op", "add_table", "name", "Daily"),
                mapOf("op", "add_column", "table", "Daily", "name", "Day", "expression", "floorDay(${EventTime})",
                        "format", "Date Time"),
                mapOf("op", "add_column", "table", "Daily", "name", "From", "expression", "${From}"),
                mapOf("op", "add_column", "table", "Daily", "name", "Messages", "expression", "count()",
                        "format", "Number"),
                mapOf("op", "group", "table", "Daily", "column", "Day", "level", 1),
                mapOf("op", "group", "table", "Daily", "column", "From", "level", 1)));
    }

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
    void anEmbeddedQueryIsChartedWithAGeneratedChartOnly() {
        embeddedQueryTab("Query: embedded in the dashboard");
        Mockito.when(chartLibraries.findContentPackD3()).thenReturn(Map.of(ChartKind.GRAPH, FORCE));

        assertThat(callWith(ProposeVisualisationTool.LIBRARY_ARG, ProposeVisualisationTool.CONTENT_PACK))
                .startsWith("No chart was offered: an embedded query is charted by a show clause")
                .endsWith("(library echarts or d3). Use one of those.");
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
