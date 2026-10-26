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
import stroom.ai.impl.DashboardChanges;
import stroom.ai.impl.DashboardChanges.Change;
import stroom.ai.impl.DashboardComponents;
import stroom.ai.impl.DashboardComponents.Component;
import stroom.ai.impl.TabSnapshots;
import stroom.ai.impl.TabSnapshots.StoredTabSnapshot;
import stroom.ai.impl.TabSnapshots.UnsavedPart;
import stroom.ai.impl.UserActions;
import stroom.ai.impl.vis.ChartKind;
import stroom.ai.impl.vis.EChartsTemplate;
import stroom.ai.impl.workspace.ChatWorkspace;
import stroom.ai.shared.AiTabSearch;
import stroom.ai.shared.AiTabSnapshotPart;
import stroom.ai.shared.DashboardTableContext;
import stroom.ai.shared.QueryTableContext;
import stroom.dashboard.shared.DashboardDoc;
import stroom.dashboard.shared.DashboardSearchRequest;
import stroom.dashboard.shared.Search;
import stroom.dashboard.shared.TableResultRequest;
import stroom.docref.DocRef;
import stroom.query.api.Column;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionOperator.Op;
import stroom.query.api.ExpressionTerm;
import stroom.query.api.ExpressionTerm.Condition;
import stroom.query.api.SpecialColumns;
import stroom.query.api.TableSettings;
import stroom.query.api.datasource.QueryField;
import stroom.query.impl.QueryService;
import stroom.query.shared.QuerySearchRequest;
import stroom.util.json.JsonUtil;
import stroom.util.shared.ResultPage;
import stroom.visualisation.shared.VisualisationDoc;

import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/// propose_dashboard_change: offers a change to a table on the dashboard in the tab the user attached as a button,
/// after checking the dashboard is the latest snapshot's tab, the user can view it, the table and its columns
/// exist and each expression is valid. It records the hash of the dashboard's settings and logs a View.
class TestProposeDashboardChangeTool {

    private static final int CHAT = 3;
    private static final DocRef DASHBOARD = new DocRef("Dashboard", "1c2d3e4f-5a6b-4c7d-8e9f-0a1b2c3d4e5f",
            "Logons");
    private static final DocRef INDEX = new DocRef("Index", "index-uuid", "Windows events");
    private static final String SETTINGS = "Time range: last hour\n"
                                           + "## Query 'Query' (id query-1)\n"
                                           + "Data source: Index 'Windows events' (uuid index-uuid)\n"
                                           + "## Table 'Events' (id table-1)\n- UserId\n"
                                           + "## Text 'Preview' (id text-1)\n";
    private static final DocRef PIPELINE = new DocRef("Pipeline", "pipeline-uuid", "Display");

    private final ChatWorkspace workspace = Mockito.mock(ChatWorkspace.class);
    private final TabSnapshots tabSnapshots = Mockito.mock(TabSnapshots.class);
    private final DocumentAccess documentAccess = Mockito.mock(DocumentAccess.class);
    private final DataSourceAccess dataSourceAccess = Mockito.mock(DataSourceAccess.class);
    private final QueryService queryService = Mockito.mock(QueryService.class);
    private final AiAuditLog auditLog = Mockito.mock(AiAuditLog.class);
    private final DashboardBuilder dashboardBuilder = Mockito.mock(DashboardBuilder.class);
    private final ToolRunContext context = new ToolRunContext(null, null, () -> false);
    private final List<Map<String, Object>> offered = new ArrayList<>();
    private ProposeDashboardChangeTool tool;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        Mockito.when(workspace.limits()).thenReturn(new AiWorkspaceConfig());
        Mockito.when(workspace.withTimeLimit(Mockito.any(Duration.class), Mockito.any()))
                .thenAnswer(invocation -> ((Supplier<?>) invocation.getArgument(1)).get());
        Mockito.when(documentAccess.canView(DASHBOARD)).thenReturn(true);
        Mockito.when(dataSourceAccess.canUse(INDEX)).thenReturn(true);
        Mockito.when(queryService.findFields(Mockito.any())).thenReturn(new ResultPage<>(List.of(
                QueryField.createText("UserId"), QueryField.createText("EventType"))));
        tool = new ProposeDashboardChangeTool(workspace, tabSnapshots, documentAccess, dataSourceAccess,
                () -> queryService, auditLog, dashboardBuilder);
        dashboardTab(false);
    }

    @Test
    void aChangeIsOfferedAsAButtonAndLogged() {
        final String result = run(List.of(
                Map.of("op", "group", "column", "UserId", "level", 1),
                Map.of("op", "add_column", "name", "Count", "expression", "count()", "format", "Number")));

        assertThat(result)
                .contains("shown the change to table 'Events' on dashboard 'Logons' with an 'Apply to dashboard' "
                          + "button")
                .contains("- Group by 'UserId' (level 1)")
                .contains("- Add column 'Count' with expression count(), formatted as Number, at the end")
                .contains("never saved");
        assertThat(takeUserActions()).singleElement().satisfies(action -> assertThat(action)
                .containsEntry("action", UserActions.APPLY_DASHBOARD_CHANGE)
                .containsEntry("component", "Events")
                .containsEntry("component_id", "table-1")
                .containsEntry("snapshot_hash", AiTabSnapshotPart.hashText(SETTINGS))
                .containsEntry("summary", "Counts events per user."));
        Mockito.verify(auditLog).documentViewed(Mockito.eq(ToolSession.chat(CHAT)), Mockito.eq(DASHBOARD),
                Mockito.contains("Proposed a change to table 'Events'"));
    }

    @Test
    void theClassicQuerysExpressionIsSetFromConditionsAndShownInWords() {
        final ExpressionOperator expression = ExpressionOperator.builder()
                .op(Op.OR)
                .addTerm(ExpressionTerm.builder().field("EventType").condition(Condition.EQUALS).value("Logon")
                        .build())
                .addTerm(ExpressionTerm.builder().field("EventType").condition(Condition.EQUALS).value("Logoff")
                        .build())
                .build();
        Mockito.when(dashboardBuilder.whereExpression(INDEX, "EventType = \"Logon\" or EventType = \"Logoff\""))
                .thenReturn(expression);

        final String result = run(List.of(Map.of("op", "set_expression",
                "where", "EventType = \"Logon\"\nor EventType = \"Logoff\"")));

        assertThat(result).contains("- Set the query to find: EventType is 'Logon' or EventType is 'Logoff'");
        assertThat(takeUserActions()).singleElement().satisfies(action -> assertThat(action)
                .extractingByKey("changes")
                .asInstanceOf(InstanceOfAssertFactories.LIST)
                .singleElement()
                .satisfies(change -> assertThat(asMap(change))
                        .containsEntry("op", "set_expression")
                        .containsEntry("query", DashboardChanges.expressionToMap(expression))
                        // The dashboard gives no expression for the query, so there is none to keep.
                        .doesNotContainKey("ref")));
    }

    @Test
    void theExpressionAChangeReplacesGoesWithItSoAChangedOneIsntWrittenOver() {
        // The rest of the dashboard may change before the change is applied, but not the expression it replaces.
        dashboardTab(false, "Time range: last hour\n"
                            + "## Query 'Query' (id query-1)\n"
                            + "Data source: Index 'Windows events' (uuid index-uuid)\n"
                            + "Expression:\n"
                            + "AND\n"
                            + "  EventType = Logon\n"
                            + "Settings: queryOnOpen true\n"
                            + "## Table 'Events' (id table-1)\n"
                            + "Query: query-1\n"
                            + "- UserId\n");
        Mockito.when(dashboardBuilder.whereExpression(Mockito.eq(INDEX), Mockito.anyString()))
                .thenReturn(ExpressionOperator.builder().build());

        run(List.of(Map.of("op", "set_expression", "where", "EventType = \"Logoff\"")));

        assertThat(takeUserActions()).singleElement().satisfies(action -> assertThat(action)
                .extractingByKey("changes")
                .asInstanceOf(InstanceOfAssertFactories.LIST)
                .singleElement()
                .satisfies(change -> assertThat(asMap(change))
                        .containsEntry("ref", AiTabSnapshotPart.hashLines("AND\n  EventType = Logon"))));
    }

    @Test
    void conditionsThatArentValidAreRefusedSayingWhy() {
        Mockito.when(dashboardBuilder.whereExpression(Mockito.eq(INDEX), Mockito.anyString()))
                .thenThrow(new IllegalArgumentException("The conditions aren't valid: unknown field 'Nope'"));

        assertThat(run(List.of(Map.of("op", "set_expression", "where", "Nope = 1"))))
                .startsWith("No change was proposed, as it isn't valid")
                .contains("Change 1 (set_expression): The conditions aren't valid: unknown field 'Nope'");
        assertThat(takeUserActions()).isEmpty();

        assertThat(run(List.of(Map.of("op", "set_expression"))))
                .startsWith("Error: Change 1: set_expression needs where");
    }

    @Test
    void anyComponentCanBeRenamedOrHaveItsSettingsChanged() {
        Mockito.when(documentAccess.findDocRef("pipeline-uuid")).thenReturn(Optional.of(PIPELINE));
        Mockito.when(documentAccess.canView(PIPELINE)).thenReturn(true);

        final String result = run(List.of(
                Map.of("op", "rename", "name", "Tweets"),
                Map.of("op", "set_setting", "target", "query", "setting", "queryOnOpen", "value", "yes"),
                Map.of("op", "set_setting", "target", "Preview", "setting", "pipeline", "value", "pipeline-uuid"),
                Map.of("op", "set_setting", "target", "Preview", "setting", "table", "value", "events")));

        assertThat(result)
                .contains("- Rename this table to 'Tweets'")
                .contains("- Set run the query when the dashboard opens (queryOnOpen) of 'query' to on")
                .contains("(pipeline) of 'Preview' to Display");
        assertThat(takeUserActions()).singleElement().satisfies(action -> assertThat(action)
                .extractingByKey("changes")
                .asInstanceOf(InstanceOfAssertFactories.LIST)
                .satisfiesExactly(
                        rename -> assertThat(asMap(rename)).containsEntry("target_id", "table-1"),
                        onOpen -> assertThat(asMap(onOpen)).containsEntry("target_id", "query-1")
                                .containsEntry("value", "true"),
                        pipeline -> assertThat(asMap(pipeline)).containsEntry("target_id", "text-1")
                                .containsEntry("doc", Map.of("type", "Pipeline", "uuid", "pipeline-uuid",
                                        "name", "Display")),
                        table -> assertThat(asMap(table)).containsEntry("ref", "table-1")
                                .containsEntry("value", "Events")));
    }

    @Test
    void componentChangesThatDontFitAreRefusedSayingWhy() {
        final String result = run(List.of(
                Map.of("op", "set_setting", "target", "Nope", "setting", "pageSize", "value", "10"),
                Map.of("op", "set_setting", "target", "Query", "setting", "pageSize", "value", "10"),
                Map.of("op", "set_setting", "setting", "pageSize", "value", "lots"),
                Map.of("op", "set_setting", "target", "Preview", "setting", "pipeline", "value", "nope"),
                Map.of("op", "set_setting", "setting", "query", "value", "Events"),
                Map.of("op", "rename", "target", "Query", "name", "preview")));

        assertThat(result)
                .startsWith("No change was proposed, as it isn't valid")
                .contains("Change 1 (set_setting): the dashboard has no component 'Nope'. Its components are: "
                          + "Query 'Query', Table 'Events', Text 'Preview'.")
                .contains("Change 2 (set_setting): Query 'Query' has no setting 'pageSize'. Its settings are: "
                          + "dataSource, queryOnOpen, autoRefresh, refreshInterval.")
                .contains("Change 3 (set_setting): pageSize takes a whole number")
                .contains("Change 4 (set_setting): no pipeline you can view has the UUID 'nope'")
                .contains("Change 5 (set_setting): the dashboard has no Query 'Events'.")
                .contains("Change 6 (rename): the dashboard already has a component 'preview'.");
        assertThat(takeUserActions()).isEmpty();
    }

    @Test
    void aTableAddedByTheChangesCanBeRenamedAndSetUp() {
        final String result = run(List.of(
                Map.of("op", "add_table", "name", "Logons"),
                Map.of("op", "set_setting", "target", "Logons", "setting", "pageSize", "value", "20"),
                Map.of("op", "rename", "target", "Logons", "name", "Sign-ins")));

        assertThat(result).contains("- Set rows per page (pageSize) of 'Logons' to 20")
                .contains("- Rename 'Logons' to 'Sign-ins'");
        assertThat(takeUserActions()).singleElement().satisfies(action -> assertThat(action)
                .extractingByKey("changes")
                .asInstanceOf(InstanceOfAssertFactories.LIST)
                .element(1)
                .satisfies(change -> assertThat(asMap(change)).doesNotContainKey("target_id")
                        .containsEntry("target", "Logons")));
    }

    @Test
    void aTextWidgetCanBeAddedAndSetUpButNotNamedAsAnotherComponent() {
        Mockito.when(documentAccess.findDocRef("pipeline-uuid")).thenReturn(Optional.of(PIPELINE));
        Mockito.when(documentAccess.canView(PIPELINE)).thenReturn(true);

        final String result = run(List.of(
                Map.of("op", "add_text", "name", "Raw event"),
                Map.of("op", "set_setting", "target", "Raw event", "setting", "pipeline", "value", "pipeline-uuid")));

        assertThat(result).contains("- Add a Text widget 'Raw event' below this table")
                .contains("(pipeline) of 'Raw event' to Display");
        assertThat(takeUserActions()).hasSize(1);

        assertThat(run(List.of(Map.of("op", "add_text", "name", "preview"))))
                .contains("Change 1 (add_text): the dashboard already has a component 'preview'");
    }

    @Test
    void aTextWidgetOnAGroupedTableWithoutDetailRowsIsNoted() {
        final Column grouped = Column.builder().id("c1").name("User").expression("${User}").group(0).build();
        final Column count = Column.builder().id("c2").name("Count").expression("count()").build();
        final AiTabSearch groupedTable = tableOf(List.of(grouped, count));
        final AiTabSearch withDetail = tableOf(List.of(grouped, count, SpecialColumns.RESERVED_STREAM_ID_COLUMN,
                SpecialColumns.RESERVED_EVENT_ID_COLUMN));
        final List<Change> stepping = DashboardChanges.parse(List.of(Map.of("op", "set_setting", "target", "Text",
                "setting", "showStepping", "value", "true")));
        final List<Change> addText = DashboardChanges.parse(List.of(Map.of("op", "add_text", "name", "Preview")));

        assertThat(ProposeDashboardChangeTool.eventNote(stepping, groupedTable, "Tweets"))
                .contains("table 'Tweets' groups its rows")
                .contains("set_setting showDetail true");
        assertThat(ProposeDashboardChangeTool.eventNote(addText, groupedTable, "Tweets")).isNotEmpty();
        // Not when the table shows its detail rows (so it has the events' ids), or the changes turn them on.
        assertThat(ProposeDashboardChangeTool.eventNote(stepping, withDetail, "Tweets")).isEmpty();
        assertThat(ProposeDashboardChangeTool.eventNote(DashboardChanges.parse(List.of(
                Map.of("op", "add_text", "name", "Preview"),
                Map.of("op", "set_setting", "setting", "showDetail", "value", "true"))).stream()
                .map(change -> "showDetail".equals(change.setting())
                        ? change.withResolved("table-1", "true", null, null)
                        : change)
                .toList(), groupedTable, "Tweets")).isEmpty();
        // Nor for changes that don't touch a Text widget.
        assertThat(ProposeDashboardChangeTool.eventNote(DashboardChanges.parse(List.of(Map.of("op", "hide_column",
                "column", "Count"))), groupedTable, "Tweets")).isEmpty();
    }

    @Test
    void anUnknownColumnIsRefusedSayingWhy() {
        final String result = run(List.of(Map.of("op", "hide_column", "column", "Nope")));

        assertThat(result)
                .startsWith("No change was proposed, as it isn't valid")
                .contains("the table has no column 'Nope'. Its columns are: UserId, EventType.");
        assertThat(takeUserActions()).isEmpty();
    }

    @Test
    void anInvalidExpressionIsRefusedSayingWhy() {
        assertThat(run(List.of(Map.of("op", "add_column", "name", "Bad", "expression", "count(("))))
                .contains("Change 1 (add_column): The expression 'count((' can't be read");
        assertThat(run(List.of(Map.of("op", "edit_column", "column", "UserId", "expression",
                "upperCase(${Usr})"))))
                .contains("names a field 'Usr' that the dashboard's data source doesn't have");
        assertThat(takeUserActions()).isEmpty();
    }

    @Test
    void anUnknownTableIsRefusedNamingTheTables() {
        final String result = run("Nope", List.of(Map.of("op", "hide_column", "column", "UserId")));

        assertThat(result).isEqualTo("The dashboard has no table or embedded query 'Nope'. Its tables and embedded "
                                     + "queries are: 'Events'.");
    }

    @Test
    void aTabThatIsntADashboardIsRefused() {
        Mockito.when(tabSnapshots.forLatestQuestion(CHAT)).thenReturn(Optional.of(new StoredTabSnapshot(5, 1,
                new DocRef("XSLT", "x", "X"), "/", "X", null, false, null, "h", 0L, List.of(), List.of(), null)));

        assertThat(run(List.of(Map.of("op", "hide_column", "column", "UserId"))))
                .startsWith("The tab attached to this question is XSLT 'X', not a dashboard, so no change was "
                            + "proposed. No dashboard has been attached to this chat, so it isn't known which one "
                            + "the user means");
    }

    @Test
    void withoutATabTheDashboardAttachedEarlierIsNamed() {
        // Seen in testing: asked a follow-up without the tab attached, the agent couldn't tell the user clearly
        // what to attach again.
        Mockito.when(tabSnapshots.forLatestQuestion(CHAT)).thenReturn(Optional.empty());
        Mockito.when(tabSnapshots.latestOfType(CHAT, Set.of(DashboardDoc.TYPE))).thenReturn(Optional.of(
                new StoredTabSnapshot(5, 9, new DocRef(DashboardDoc.TYPE, "d9", "Chat logs"), "/", "Chat logs",
                        null, false, null, "h", 0L, List.of(), List.of(), null)));

        assertThat(run(List.of(Map.of("op", "hide_column", "column", "UserId"))))
                .startsWith("No tab is attached to this question, so no change was proposed. Dashboard 'Chat logs' "
                            + "(uuid d9) was attached to an earlier question: its files in /tabs/9/ show it as it "
                            + "was then")
                .contains("ask them to open Dashboard 'Chat logs' and attach its tab");
    }

    @Test
    void aDashboardTheUserCantViewIsNotRevealed() {
        Mockito.when(documentAccess.canView(DASHBOARD)).thenReturn(false);

        final String result = run(List.of(Map.of("op", "hide_column", "column", "UserId")));

        assertThat(result).isEqualTo(DocumentAccess.NO_PERMISSION).doesNotContain("Logons");
        assertThat(takeUserActions()).isEmpty();
        Mockito.verifyNoInteractions(auditLog);
    }

    @Test
    void settingsCutShortCantBeProposedAgainst() {
        dashboardTab(true);

        assertThat(run(List.of(Map.of("op", "hide_column", "column", "UserId"))))
                .contains("aren't in the tab snapshot in full");
        assertThat(takeUserActions()).isEmpty();
    }

    @Test
    void badChangesAreRefused() {
        assertThat(run(List.of(Map.of("op", "drop_table")))).startsWith("Error: Change 1: op must be one of");
    }

    private static AiTabSearch tableOf(final List<Column> columns) {
        final DashboardTableContext live = new DashboardTableContext("Dashboard 'Logons'", null,
                DashboardSearchRequest.builder()
                        .search(Search.builder().dataSourceRef(INDEX).build())
                        .componentResultRequests(List.of(TableResultRequest.builder()
                                .componentId("table-1")
                                .tableSettings(TableSettings.builder().columns(columns).build())
                                .build()))
                        .build());
        return new AiTabSearch("Tweets", "table-1", null, live, List.of("User", "Count"), 10L, false);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(final Object value) {
        return (Map<String, Object>) value;
    }

    @Test
    void aVisualisationIsAddedWithItsNameAndSettingsChecked() {
        visualisation("vis-uuid", "Bar chart");

        final String result = run(List.of(Map.of("op", "add_vis", "name", "Users", "visualisation", "vis-uuid",
                "settings", Map.of("category", "UserId"), "placement", "tab")));

        assertThat(result).contains("- Add a visualisation 'Users' (Bar chart) of this table as a tab beside the "
                                    + "table, with category 'UserId'");
        assertThat(takeUserActions()).singleElement().satisfies(action -> assertThat(action.get("changes"))
                .asInstanceOf(InstanceOfAssertFactories.LIST)
                .singleElement()
                .satisfies(change -> assertThat(asMap(change))
                        .containsEntry("op", "add_vis")
                        .containsEntry("doc", Map.of("type", "Visualisation", "uuid", "vis-uuid", "name",
                                "Bar chart"))
                        .containsEntry("settings", Map.of("category", "UserId"))
                        .containsEntry("placement", "tab")));
    }

    @Test
    void aVisualisationMustBeOneTheUserCanViewWithItsControlsAndTheTablesColumns() {
        visualisation("vis-uuid", "Bar chart");

        final String result = run(List.of(
                Map.of("op", "add_vis", "name", "Users", "visualisation", "nope"),
                Map.of("op", "add_vis", "name", "preview", "visualisation", "vis-uuid",
                        "settings", Map.of("category", "userid")),
                Map.of("op", "add_vis", "name", "Types", "visualisation", "vis-uuid",
                        "settings", Map.of("colour", "red"))));

        assertThat(result)
                .startsWith("No change was proposed")
                .contains("Change 1 (add_vis): no Visualisation you can view has the UUID 'nope'.")
                .contains("Change 2 (add_vis): the dashboard already has a component 'preview'.")
                .contains("Change 2 (add_vis): control 'category' takes a column of table 'Events', which has no "
                          + "column 'userid' (did you mean 'UserId'?). Its columns are: UserId, EventType.")
                .contains("Change 3 (add_vis): Visualisation 'Bar chart' has no control 'colour'. Its controls "
                          + "are: category (field), value (field).");
        assertThat(takeUserActions()).isEmpty();
    }

    @Test
    void aVisualisationsSettingsAreChangedOnTheComponentTheDashboardHas() {
        final String withVis = SETTINGS + "## Visualisation 'Chart' (id vis-1)\n"
                               + "Visualisation: 'Bar chart' (uuid vis-uuid)\n"
                               + "Linked table: 'Events'\n"
                               + "Vis settings: {\"category\":\"UserId\"}\n"
                               + "Render report: {\"rendered\":true}\n";
        dashboardTab(false, withVis);
        visualisation("vis-uuid", "Bar chart");

        final String result = run(List.of(
                Map.of("op", "set_vis_settings", "target", "chart", "settings", Map.of("value", "EventType")),
                Map.of("op", "set_vis_settings", "target", "Preview", "settings", Map.of("value", "EventType"))));

        assertThat(result).contains("Change 2 (set_vis_settings): the dashboard has no Visualisation component "
                                    + "'Preview'. Its Visualisation components are: Visualisation 'Chart'.");

        final String offered = run(List.of(Map.of("op", "set_vis_settings", "target", "chart",
                "settings", Map.of("value", "EventType"))));

        assertThat(offered).contains("- Change visualisation 'chart': set value 'EventType'");
        assertThat(takeUserActions()).singleElement().satisfies(action -> {
            // The render report, which changes as it renders, is left out of the hash.
            assertThat(action).containsEntry("snapshot_hash", AiTabSnapshotPart.hashText(
                    withVis.replace("Render report: {\"rendered\":true}\n", "")));
            assertThat(asMap(((List<?>) action.get("changes")).getFirst()))
                    .containsEntry("target_id", "vis-1")
                    .doesNotContainKey("doc");
        });
    }

    @Test
    void aGeneratedChartIsReconfiguredByItsSettingsButNotMadeAnotherKind() {
        // Seen in testing: a line chart made a bar chart by its pane's settings asked for x and y, which its
        // Visualisation's Data tab still had.
        final String withVis = SETTINGS + "## Visualisation 'Chats' (id vis-1)\n"
                               + "Visualisation: 'Chats by user' (uuid line-uuid)\n"
                               + "Linked table: 'Events'\n"
                               + "Vis settings: {}\n";
        dashboardTab(false, withVis);
        final VisualisationDoc line = VisualisationDoc.builder()
                .uuid("line-uuid")
                .name("Chats by user")
                .functionName(EChartsTemplate.FUNCTION_NAME)
                .settings(EChartsTemplate.settingsJson(ChartKind.LINE, Map.of("x", "EventTime", "y", "UserId"),
                        Map.of(), null))
                .build();
        Mockito.when(documentAccess.read(new DocRef(VisualisationDoc.TYPE, "line-uuid"), VisualisationDoc.class))
                .thenReturn(Optional.of(line));

        assertThat(run(List.of(Map.of("op", "set_vis_settings", "target", "Chats",
                "settings", Map.of("kind", "bar", "stack", "true")))))
                .contains("Change 1 (set_vis_settings): a chart Stroom AI generated can't be made another kind by "
                          + "its settings (kind): its Visualisation has the line chart's columns. Convert it with "
                          + "propose_visualisation, replace (the pane's name)");
        assertThat(takeUserActions()).isEmpty();

        // Its style, its columns, and its own kind (or none, putting back its default) are fine.
        assertThat(run(List.of(Map.of("op", "set_vis_settings", "target", "Chats",
                "settings", Map.of("kind", "Line", "stack", "true", "smooth", "true", "x", "UserId")))))
                .contains("- Change visualisation 'Chats'");
        assertThat(takeUserActions()).hasSize(1);
        assertThat(run(List.of(Map.of("op", "set_vis_settings", "target", "Chats",
                "settings", Map.of("kind", "", "fields", "")))))
                .contains("- Change visualisation 'Chats'");
    }

    @Test
    void aChoiceAddedSinceAChartWasGeneratedIsRegeneratedFirst() {
        // A chart keeps the adapter it was generated with, so a choice added since (clusters) would do nothing.
        final String withVis = SETTINGS + "## Visualisation 'People' (id vis-1)\n"
                               + "Visualisation: 'Who messages whom' (uuid graph-uuid)\n"
                               + "Linked table: 'Events'\n"
                               + "Vis settings: {}\n";
        dashboardTab(false, withVis);
        final String current = EChartsTemplate.settingsJson(ChartKind.GRAPH,
                Map.of("source", "UserId", "target", "EventType"), Map.of(), null);
        final String earlier = current.replace("\"group\", \"node\", \"cluster\"", "\"group\", \"node\"");
        assertThat(earlier).isNotEqualTo(current);
        final VisualisationDoc graph = VisualisationDoc.builder()
                .uuid("graph-uuid")
                .name("Who messages whom")
                .functionName(EChartsTemplate.FUNCTION_NAME)
                .settings(earlier)
                .build();
        Mockito.when(documentAccess.read(new DocRef(VisualisationDoc.TYPE, "graph-uuid"), VisualisationDoc.class))
                .thenReturn(Optional.of(graph));

        assertThat(run(List.of(Map.of("op", "set_vis_settings", "target", "People",
                "settings", Map.of("nodeColours", "cluster")))))
                .contains("Change 1 (set_vis_settings): this chart's nodeColours offers group, node, not 'cluster': "
                          + "it was generated before that was added")
                .contains("Regenerate it first with propose_visualisation, replace (its pane's name)");
        assertThat(takeUserActions()).isEmpty();

        // A choice it offers, in any case, is fine.
        assertThat(run(List.of(Map.of("op", "set_vis_settings", "target", "People",
                "settings", Map.of("nodeColours", "Node")))))
                .contains("- Change visualisation 'People'");
        // A value no version offers isn't put down to the chart's age: regenerating wouldn't help.
        assertThat(ProposeDashboardChangeTool.newChoiceProblems(Map.of("nodeColours", "clusters"), graph))
                .containsExactly("'clusters' isn't a choice of nodeColours: group, node, cluster.");
        // A chart generated now offers it.
        assertThat(ProposeDashboardChangeTool.newChoiceProblems(Map.of("nodeColours", "cluster"),
                graph.copy().settings(current).build())).isEmpty();
    }

    @Test
    void aTableFilterFiltersRowsByTheChartsSelection() {
        chartTab();

        assertThat(run(List.of(Map.of("op", "add_selection_filter", "target", "Events", "chart", "Chart",
                "mode", "table", "terms", List.of(Map.of("field", "UserId", "selection", "category"))))))
                .contains("- Filter the rows of 'Events' by what is selected in chart 'Chart': UserId = its "
                          + "category");
        assertThat(takeUserActions()).singleElement().satisfies(action -> {
            final Map<String, Object> change = asMap(((List<?>) action.get("changes")).getFirst());
            assertThat(change).containsEntry("op", "add_selection_filter")
                    .containsEntry("target_id", "table-1")
                    .containsEntry("ref", "vis-1")
                    .containsEntry("value", "table");
            assertThat(asMap(asMap(change.get("query")).get("children") instanceof final List<?> children
                    ? children.getFirst()
                    : null))
                    .containsEntry("field", "UserId")
                    .containsEntry("value", "${component.vis-1.selection.category}");
        });
    }

    @Test
    void aSelectionFilterIsRemovedByItsId() {
        // Seen in testing: a selection filter that did the wrong thing couldn't be removed or changed.
        dashboardTab(false, SETTINGS.replace("## Table 'Events' (id table-1)\n- UserId\n",
                "## Table 'Events' (id table-1)\n- UserId\nTable selection filters: [id ab12c] OR {UserId = "
                + "${component.vis-1.selection.node}}; [id cd34e] UserId = x\n")
                                       + "## Visualisation 'Chart' (id vis-1)\n"
                                       + "Visualisation: 'Bar chart' (uuid vis-uuid)\n"
                                       + "Linked table: 'Events'\n"
                                       + "Vis settings: {\"category\":\"UserId\",\"value\":\"Count\"}\n");

        assertThat(run(List.of(Map.of("op", "remove_selection_filter", "target", "Events", "filter", "ab12c"))))
                .contains("- Remove the selection filter ab12c from 'Events'");
        assertThat(takeUserActions()).singleElement().satisfies(action ->
                assertThat(asMap(((List<?>) action.get("changes")).getFirst()))
                        .containsEntry("op", "remove_selection_filter")
                        .containsEntry("target_id", "table-1")
                        .containsEntry("filter", "ab12c"));

        // An id the component doesn't have is refused, naming those it has.
        assertThat(run(List.of(Map.of("op", "remove_selection_filter", "target", "Events", "filter", "zz999"))))
                .contains("'Events' has no selection filter 'zz999'. Its selection filters' ids are: ab12c, cd34e.");
        assertThat(takeUserActions()).isEmpty();
    }

    @Test
    void aFilterMatchingAnyTermGivesEachItsOwnOperator() {
        // Seen in testing: Stroom ANDs the terms of an operator that name the same chart's selection, whatever the
        // operator, so "sender or recipient is the clicked node" matched only messages to oneself: no data.
        chartTab();

        run(List.of(Map.of("op", "add_selection_filter", "target", "Events", "chart", "Chart", "mode", "table",
                "match", "any", "terms", List.of(Map.of("field", "UserId", "selection", "category"),
                        Map.of("field", "EventType", "selection", "category")))));

        assertThat(takeUserActions()).singleElement().satisfies(action -> {
            final Map<String, Object> query = asMap(asMap(((List<?>) action.get("changes")).getFirst())
                    .get("query"));
            assertThat(query).containsEntry("op", "OR");
            assertThat((List<?>) query.get("children")).hasSize(2).allSatisfy(child ->
                    assertThat((List<?>) asMap(child).get("children")).singleElement().satisfies(term ->
                            assertThat(asMap(term)).containsEntry("value", "${component.vis-1.selection.category}")));
        });

        // Matching all of them, they stay together.
        run(List.of(Map.of("op", "add_selection_filter", "target", "Events", "chart", "Chart", "mode", "table",
                "terms", List.of(Map.of("field", "UserId", "selection", "category"),
                        Map.of("field", "EventType", "selection", "category")))));

        assertThat(takeUserActions()).singleElement().satisfies(action -> {
            final Map<String, Object> query = asMap(asMap(((List<?>) action.get("changes")).getFirst())
                    .get("query"));
            assertThat(query).containsEntry("op", "AND");
            assertThat((List<?>) query.get("children")).hasSize(2).allSatisfy(child ->
                    assertThat(asMap(child)).containsKey("field").doesNotContainKey("children"));
        });
    }

    @Test
    void aTimeRangeKeepsWhatIsWithinItAndDropsOutWithoutOne() {
        // Two terms, not between: Stroom leaves out each whose value isn't held, where a between term's value would
        // be a bare comma while nothing is selected.
        assertThat(ProposeDashboardChangeTool.selectionTerms("EventTime", "range", "vis-1"))
                .extracting(ExpressionTerm::getField, ExpressionTerm::getCondition, ExpressionTerm::getValue)
                .containsExactly(
                        tuple("EventTime", Condition.GREATER_THAN_OR_EQUAL_TO, "${component.vis-1.selection.from}"),
                        tuple("EventTime", Condition.LESS_THAN_OR_EQUAL_TO, "${component.vis-1.selection.to}"));
        assertThat(ProposeDashboardChangeTool.selectionTerms("UserId", "category", "vis-1"))
                .extracting(ExpressionTerm::getCondition, ExpressionTerm::getValue)
                .containsExactly(tuple(Condition.EQUALS, "${component.vis-1.selection.category}"));
        // A chart with an x axis can select a range of it.
        assertThat(ProposeDashboardChangeTool.selectionKeys(new Component("Visualisation", "Trend", "vis-1", null,
                null, "Events", Map.of("x", "Time", "y", "Count")))).contains("range");
    }

    @Test
    void aDateTheChartSelectsIsFilteredByTimeNotEquality() {
        // Seen in testing: a line chart of days filtered the query by @timestamp = its x, which matched only the
        // instant a clicked day starts at, and nothing for a range dragged along its time axis (which selects only
        // from and to). The stub index's fields are UserId and EventType, so EventType stands in for a date field.
        final String chartOfDays = "Time range: last hour\n"
                                   + "## Query 'Query' (id query-1)\n"
                                   + "Data source: Index 'Windows events' (uuid index-uuid)\n"
                                   + "## Table 'Events' (id table-1)\n"
                                   + "Query: query-1\n"
                                   + "Columns:\n"
                                   + "- Day: floorDay(${EventType}) (grouped, level 1; sorted Ascending, order 1; "
                                   + "format Date Time)\n"
                                   + "- UserId: ${UserId} (grouped, level 1; format Text)\n"
                                   + "## Visualisation 'Trend' (id vis-1)\n"
                                   + "Visualisation: 'Trend' (uuid vis-uuid)\n"
                                   + "Linked table: 'Events'\n"
                                   + "Vis settings: {\"x\":\"Day\",\"y\":\"Count\",\"series\":\"UserId\"}\n";
        dashboardTab(false, chartOfDays);

        assertThat(run(List.of(Map.of("op", "add_selection_filter", "target", "Query", "chart", "Trend",
                "mode", "query", "terms", List.of(Map.of("field", "EventType", "selection", "x"),
                        Map.of("field", "UserId", "selection", "series"))))))
                .contains("by what is selected in chart 'Trend': EventType within its selected time (a range "
                          + "dragged along its time axis or across its bars of dates, or the time a clicked point "
                          + "stands for) and UserId = its series");
        assertThat(takeUserActions()).singleElement().satisfies(action -> {
            final Map<String, Object> query = asMap(asMap(((List<?>) action.get("changes")).getFirst())
                    .get("query"));
            assertThat((List<?>) query.get("children")).extracting(child -> asMap(child).get("condition"),
                            child -> asMap(child).get("value"))
                    .containsExactly(
                            tuple("GREATER_THAN_OR_EQUAL_TO", "${component.vis-1.selection.from}"),
                            tuple("LESS_THAN_OR_EQUAL_TO", "${component.vis-1.selection.to}"),
                            tuple("EQUALS", "${component.vis-1.selection.series}"));
        });

        // By the column's name too; and range and x together are one range.
        final Component trend = DashboardComponents.parse(chartOfDays).getLast();
        assertThat(ProposeDashboardChangeTool.dateKeys(trend, chartOfDays)).containsExactlyInAnyOrder("x", "Day");
        assertThat(run(List.of(Map.of("op", "add_selection_filter", "target", "Query", "chart", "Trend",
                "mode", "query", "terms", List.of(Map.of("field", "EventType", "selection", "Day"),
                        Map.of("field", "EventType", "selection", "range"))))))
                .contains("chart 'Trend': EventType within its selected time");
        assertThat(takeUserActions()).singleElement().satisfies(action ->
                assertThat((List<?>) asMap(asMap(((List<?>) action.get("changes")).getFirst()).get("query"))
                        .get("children")).hasSize(2));

        // A chart whose columns aren't dates has no date keys.
        assertThat(ProposeDashboardChangeTool.dateKeys(trend, chartOfDays.replace("format Date Time", "format Text")))
                .isEmpty();
    }

    @Test
    void aLinksEndsStayTogetherWhenAnyTermMayMatch() {
        // Seen in testing: clicking a link filtered nothing, as the filter had only node terms. With the link's
        // ends too, a node matches either end, and a link matches both its ends (either way round, as the chart
        // sends an undirected link both ways).
        dashboardTab(false, SETTINGS + "## Visualisation 'Network' (id vis-1)\n"
                            + "Visualisation: 'Network' (uuid vis-uuid)\n"
                            + "Linked table: 'Events'\n"
                            + "Vis settings: {\"source\":\"UserId\",\"target\":\"EventType\"}\n");

        run(List.of(Map.of("op", "add_selection_filter", "target", "Events", "chart", "Network", "mode", "table",
                "match", "any", "terms", List.of(Map.of("field", "UserId", "selection", "node"),
                        Map.of("field", "EventType", "selection", "node"),
                        Map.of("field", "UserId", "selection", "source"),
                        Map.of("field", "EventType", "selection", "target")))));

        assertThat(takeUserActions()).singleElement().satisfies(action -> {
            final Map<String, Object> query = asMap(asMap(((List<?>) action.get("changes")).getFirst())
                    .get("query"));
            assertThat(query).containsEntry("op", "OR");
            assertThat((List<?>) query.get("children")).extracting(child ->
                            ((List<?>) asMap(child).get("children")).size())
                    .containsExactly(1, 1, 2);
        });
    }

    @Test
    void anyEndOfALinkAlsoMatchesAClickedNode() {
        // Chat 120, as the agent gave it: asked for "the person as sender or as recipient", it gave only the link's
        // ends, so a clicked node (which selects node, not source or target) filtered nothing. Node terms are added
        // for the same columns, so a node matches either end, and a link still matches both its ends.
        dashboardTab(false, SETTINGS + "## Visualisation 'Chat flows' (id vis-1)\n"
                            + "Visualisation: 'Network' (uuid vis-uuid)\n"
                            + "Linked table: 'Events'\n"
                            + "Vis settings: {\"source\":\"UserId\",\"target\":\"EventType\"}\n");

        assertThat(run(List.of(Map.of("op", "add_selection_filter", "target", "Events", "chart", "Chat flows",
                "mode", "table", "match", "any", "terms", List.of(
                        Map.of("field", "UserId", "selection", "source"),
                        Map.of("field", "EventType", "selection", "target"))))))
                .contains("UserId = its node")
                .contains("EventType = its node");
        assertThat(takeUserActions()).singleElement().satisfies(action -> {
            final Map<String, Object> query = asMap(asMap(((List<?>) action.get("changes")).getFirst())
                    .get("query"));
            assertThat(query).containsEntry("op", "OR");
            assertThat((List<?>) query.get("children")).extracting(child ->
                            ((List<?>) asMap(child).get("children")).size())
                    .containsExactly(1, 1, 2);
        });

        // Matching all its terms, a link's pair: a node isn't added (it would never match both ends at once).
        run(List.of(Map.of("op", "add_selection_filter", "target", "Events", "chart", "Chat flows",
                "mode", "table", "terms", List.of(
                        Map.of("field", "UserId", "selection", "source"),
                        Map.of("field", "EventType", "selection", "target")))));
        assertThat(takeUserActions()).singleElement().satisfies(action ->
                assertThat(JsonUtil.writeValueAsString(asMap(((List<?>) action.get("changes")).getFirst())
                        .get("query"), false)).doesNotContain("selection.node"));
    }

    @Test
    void aLinksEndsAtAnyLevelStayTogether() {
        // A link between two levels selects only those two (e.g. target and level3): with the source and target
        // terms in one group, the rest drop out, so each link filters by both its own ends.
        dashboardTab(false, SETTINGS + "## Visualisation 'Flows' (id vis-1)\n"
                            + "Visualisation: 'Flows' (uuid vis-uuid)\n"
                            + "Linked table: 'Events'\n"
                            + "Vis settings: {\"source\":\"UserId\",\"target\":\"EventType\","
                            + "\"level3\":\"Host\"}\n");

        run(List.of(Map.of("op", "add_selection_filter", "target", "Events", "chart", "Flows", "mode", "table",
                "match", "any", "terms", List.of(Map.of("field", "UserId", "selection", "source"),
                        Map.of("field", "EventType", "selection", "target"),
                        Map.of("field", "UserId", "selection", "level3")))));

        assertThat(takeUserActions()).singleElement().satisfies(action -> {
            final Map<String, Object> query = asMap(asMap(((List<?>) action.get("changes")).getFirst())
                    .get("query"));
            // The link's ends together, and (matching any) a clicked node at either end, each on its own.
            assertThat((List<?>) query.get("children")).hasSize(3)
                    .extracting(group -> ((List<?>) asMap(group).get("children")).size())
                    .containsExactly(1, 1, 3);
        });
    }

    @Test
    void aTableFilterCanUseAColumnTheProposalAdds() {
        // Seen in testing: a column added for a table filter, and the filter on it, were refused together, as the
        // filter was checked against the table's columns before the proposal's changes.
        chartTab();

        assertThat(run(List.of(
                Map.of("op", "add_column", "name", "Sender", "expression", "${UserId}"),
                Map.of("op", "add_selection_filter", "target", "Events", "chart", "Chart", "mode", "table",
                        "terms", List.of(Map.of("field", "Sender", "selection", "category"))))))
                .contains("- Filter the rows of 'Events' by what is selected in chart 'Chart': Sender = its "
                          + "category");
        assertThat(takeUserActions()).hasSize(1);
    }

    @Test
    void aQueryFilterNeedsTheQuerysFields() {
        chartTab();

        assertThat(run(List.of(Map.of("op", "add_selection_filter", "target", "Query", "chart", "Chart",
                "mode", "query", "terms", List.of(Map.of("field", "EventType", "selection", "UserId"))))))
                .contains("- Filter the search of 'Query' by what is selected in chart 'Chart': EventType = its "
                          + "UserId");
        assertThat(takeUserActions()).hasSize(1);

        assertThat(run(List.of(Map.of("op", "add_selection_filter", "target", "Query", "chart", "Chart",
                "mode", "query", "terms", List.of(Map.of("field", "Usr", "selection", "category"))))))
                .contains("'Query' has no field 'Usr'. Its fields are: UserId, EventType.");
        assertThat(takeUserActions()).isEmpty();
    }

    @Test
    void aSelectionFilterIsCheckedAgainstWhatItFiltersAndTheChart() {
        chartTab();

        assertThat(run(List.of(Map.of("op", "add_selection_filter", "target", "Events", "chart", "Chart",
                "mode", "query", "terms", List.of(Map.of("field", "UserId", "selection", "category"))))))
                .contains("'Events' is a Table: a query filter filters a Query's or Embedded Query's search. Use "
                          + "mode table");
        assertThat(run(List.of(Map.of("op", "add_selection_filter", "target", "Query", "chart", "Chart",
                "mode", "table", "terms", List.of(Map.of("field", "UserId", "selection", "category"))))))
                .contains("'Query' is a Query: a table filter filters a Table's (or an Embedded Query's) rows.");
        assertThat(run(List.of(Map.of("op", "add_selection_filter", "target", "Events", "chart", "Chart",
                "mode", "table", "terms", List.of(Map.of("field", "UserId", "selection", "colour"))))))
                .contains("chart 'Chart' doesn't select 'colour'. A click on it selects: category, UserId.");
        assertThat(run(List.of(Map.of("op", "add_selection_filter", "target", "Events", "chart", "Chart",
                "mode", "table", "terms", List.of(Map.of("field", "Nope", "selection", "category"))))))
                .contains("'Events' has no column 'Nope'. Its columns are: UserId, EventType.");
        assertThat(run(List.of(Map.of("op", "add_selection_filter", "target", "Events", "chart", "Preview",
                "mode", "table", "terms", List.of(Map.of("field", "UserId", "selection", "category"))))))
                .contains("the dashboard has no Visualisation component 'Preview'.");
        assertThat(takeUserActions()).isEmpty();
    }

    @Test
    void aGraphsSelectionKeysIncludeItsNodes() {
        assertThat(ProposeDashboardChangeTool.selectionKeys(new Component("Visualisation",
                "Net", "vis-1", null, null, "Events", Map.of("source", "Sender", "target", "Recipient",
                "directed", "true"))))
                .containsExactlyInAnyOrder("source", "Sender", "target", "Recipient", "node");
        // A content pack chart's keys aren't known, so aren't checked.
        assertThat(ProposeDashboardChangeTool.selectionKeys(new Component("Visualisation",
                "D3", "vis-2", null, null, "Events", Map.of("gridSeries", "X")))).isEmpty();
    }

    // The dashboard with a bar chart 'Chart' of table 'Events', its category UserId.
    private void chartTab() {
        dashboardTab(false, SETTINGS + "## Visualisation 'Chart' (id vis-1)\n"
                            + "Visualisation: 'Bar chart' (uuid vis-uuid)\n"
                            + "Linked table: 'Events'\n"
                            + "Vis settings: {\"category\":\"UserId\",\"value\":\"Count\"}\n");
    }

    @Test
    void aChartsPaneNameIsTakenForItsLinkedTable() {
        final String withVis = SETTINGS + "## Visualisation 'Chart' (id vis-1)\n"
                               + "Visualisation: 'Bar chart' (uuid vis-uuid)\n"
                               + "Linked table: 'Events'\n"
                               + "Vis settings: {\"category\":\"UserId\"}\n"
                               + "## Visualisation 'Unlinked' (id vis-2)\n"
                               + "Visualisation: 'Bar chart' (uuid vis-uuid)\n"
                               + "Linked table: none\n";
        dashboardTab(false, withVis);
        visualisation("vis-uuid", "Bar chart");

        assertThat(run("Chart", List.of(Map.of("op", "set_vis_settings", "target", "Chart",
                "settings", Map.of("value", "EventType")))))
                .contains("shown the change to table 'Events'")
                .contains("- Change visualisation 'Chart': set value 'EventType'");
        assertThat(takeUserActions()).singleElement().satisfies(action ->
                assertThat(action).containsEntry("component", "Events"));

        // A pane linked to no table isn't taken for the only one.
        assertThat(run("Unlinked", List.of(Map.of("op", "set_vis_settings", "target", "Unlinked",
                "settings", Map.of("value", "EventType")))))
                .startsWith("The dashboard has no table or embedded query 'Unlinked'.");
        assertThat(takeUserActions()).isEmpty();
    }

    // A Visualisation the user can view, with two field controls.
    private void visualisation(final String uuid, final String name) {
        final VisualisationDoc doc = VisualisationDoc.builder()
                .uuid(uuid)
                .name(name)
                .settings("{\"tabs\":[{\"name\":\"Data\",\"controls\":[{\"id\":\"category\",\"type\":"
                          + "\"field\"},{\"id\":\"value\",\"type\":\"field\"}]}]}")
                .build();
        Mockito.when(documentAccess.read(new DocRef(VisualisationDoc.TYPE, uuid), VisualisationDoc.class))
                .thenReturn(Optional.of(doc));
    }

    @Test
    void anEmbeddedQueryTakesSettingChangesOnly() {
        embeddedQueryTab();

        assertThat(run("Pairs", List.of(Map.of("op", "set_setting", "target", "Pairs", "setting", "queryOnOpen",
                "value", "true"))))
                .doesNotStartWith("Error")
                .doesNotStartWith("No change");
        assertThat(takeUserActions()).singleElement().satisfies(action -> assertThat(action)
                .containsEntry("action", UserActions.APPLY_DASHBOARD_CHANGE)
                .containsEntry("component", "Pairs"));

        assertThat(run("Pairs", List.of(Map.of("op", "add_column", "name", "Count", "expression", "count()"))))
                .isEqualTo("No change was proposed: 'Pairs' is an embedded query, whose StroomQL makes its "
                           + "results, so it takes only set_setting (e.g. queryOnOpen), rename, "
                           + "add_selection_filter and remove_selection_filter changes, not add_column. Its "
                           + "StroomQL is in the dashboard's "
                           + "description: tell the user the StroomQL to use instead.");
        assertThat(takeUserActions()).isEmpty();
    }

    @Test
    void anEmbeddedQuerysSettingsAreChecked() {
        embeddedQueryTab();

        assertThat(run("Pairs", List.of(Map.of("op", "set_setting", "target", "Pairs", "setting", "pageSize",
                "value", "10"))))
                .startsWith("No change was proposed")
                .contains("pageSize");
        assertThat(takeUserActions()).isEmpty();
    }

    // A dashboard whose only component is an embedded query, 'Pairs'.
    private void embeddedQueryTab() {
        final QueryTableContext live = new QueryTableContext("Embedded query 'Pairs'", null,
                QuerySearchRequest.builder().query("from \"Chats\" select UserA, UserB").build());
        final AiTabSearch pairs = new AiTabSearch("Pairs", "query-1", null, live, List.of("UserA", "UserB"), 5L,
                false);
        final StoredTabSnapshot snapshot = new StoredTabSnapshot(5, 1, DASHBOARD, "/", "Logons", "Dashboard", false,
                null, "h", 0L, List.of(), List.of(pairs), null);
        Mockito.when(tabSnapshots.forLatestQuestion(CHAT)).thenReturn(Optional.of(snapshot));
        Mockito.when(tabSnapshots.part(CHAT, snapshot, AiTabSnapshotPart.DASHBOARD))
                .thenReturn(Optional.of(new UnsavedPart("""
                        Time range: All time
                        Shown components: Pairs

                        ## Embedded Query 'Pairs' (id query-1)
                        Query: embedded in the dashboard
                        StroomQL:
                        ```
                        from "Chats" select UserA, UserB
                        ```
                        Settings: queryOnOpen false; autoRefresh false; refreshInterval 10s
                        """, false, "/tabs/1/dashboard.md")));
    }

    private void dashboardTab(final boolean cut) {
        dashboardTab(cut, SETTINGS);
    }

    private void dashboardTab(final boolean cut, final String settings) {
        final DashboardTableContext live = new DashboardTableContext("Dashboard 'Logons'", null,
                DashboardSearchRequest.builder()
                        .search(Search.builder().dataSourceRef(INDEX).build())
                        .build());
        final AiTabSearch table = new AiTabSearch("Events", "table-1", null, live, List.of("UserId", "EventType"),
                10L, false);
        final StoredTabSnapshot snapshot = new StoredTabSnapshot(5, 1, DASHBOARD, "/", "Logons", "Dashboard", false,
                null, "h", 0L, List.of(), List.of(table), null);
        Mockito.when(tabSnapshots.forLatestQuestion(CHAT)).thenReturn(Optional.of(snapshot));
        Mockito.when(tabSnapshots.part(CHAT, snapshot, AiTabSnapshotPart.DASHBOARD))
                .thenReturn(Optional.of(new UnsavedPart(settings, cut, "/tabs/1/dashboard.md")));
    }

    private String run(final List<? extends Map<String, ?>> changes) {
        return run("Events", changes);
    }

    private String run(final String component, final List<? extends Map<String, ?>> changes) {
        final String arguments = JsonUtil.writeValueAsString(Map.of("component", component, "changes", changes,
                "summary", "Counts events per user."));
        return call(arguments);
    }

    // Runs a call as part of the agent run, keeping the user actions its result offers for takeUserActions.
    private String call(final String arguments) {
        final ToolResult result = context.run(() -> tool.execute(
                new ToolCall("c1", ProposeDashboardChangeTool.NAME, arguments), ToolCalls.inChat(CHAT, workspace)));
        result.userActions().forEach(action -> offered.add(action.toMap()));
        return result.text();
    }

    // The user actions offered by the calls since this was last called, as the chat shows them.
    private List<Map<String, Object>> takeUserActions() {
        final List<Map<String, Object>> taken = List.copyOf(offered);
        offered.clear();
        return taken;
    }

    // The selection filters Stroom AI builds, from what the user asks for, as a dashboard applies them to what each
    // chart's click selects. Written to a golden file (selection-filters/scenarios.json), which the client's
    // TestDashboardContextImpl reads: it puts each case's selections, as the charts give them, into the expression
    // built here, and checks the rows kept are those expected. Together they test the whole chain a click goes
    // through, which each side's own tests can't. Seen in testing: a filter on "the person as sender or as
    // recipient" filtered nothing for a clicked node, though every part passed its own tests.
    @Test
    void selectionFiltersKeepTheRowsTheUserMeans() {
        final List<Map<String, Object>> scenarios = List.of(
                scenario("network, either end (match any): a node at either end, or a link's pair",
                        "any", List.of(term("sender", "source"), term("recipient", "target")), Set.of(), List.of(
                                filterCase("nothing selected", List.of(), 0, 1, 2, 3, 4, 5),
                                filterCase("node mchen", List.of(Map.of("node", "mchen")), 2, 3, 5),
                                filterCase("link devans to phall (directed)", List.of(link("devans", "phall")), 0),
                                filterCase("link devans to phall (undirected, sent both ways)",
                                        List.of(link("devans", "phall"), link("phall", "devans")), 0, 1),
                                filterCase("nodes jlee and phall (Ctrl-click)",
                                        List.of(Map.of("node", "jlee"), Map.of("node", "phall")), 0, 1, 4, 5))),
                scenario("network, exact pair (match all): a link's pair; a node filters nothing",
                        "all", List.of(term("sender", "source"), term("recipient", "target")), Set.of(), List.of(
                                filterCase("link mchen to cdavis", List.of(link("mchen", "cdavis")), 2),
                                filterCase("node mchen", List.of(Map.of("node", "mchen")), 0, 1, 2, 3, 4, 5))),
                scenario("bar chart of senders: the clicked bar's sender",
                        "all", List.of(term("sender", "category")), Set.of(), List.of(
                                filterCase("bar mchen", List.of(Map.of("category", "mchen", "sender", "mchen")), 2, 5),
                                filterCase("bars jlee and phall (Ctrl-click)", List.of(
                                        Map.of("category", "jlee", "sender", "jlee"),
                                        Map.of("category", "phall", "sender", "phall")), 1, 4))),
                scenario("time series of days: within the time selected",
                        "all", List.of(term("EventTime", "x")), Set.of("x", "Day"), List.of(
                                filterCase("range dragged over 14 and 15 October", List.of(Map.of(
                                        "from", "2026-10-14T00:00:00.000Z",
                                        "to", "2026-10-15T23:59:59.999Z")), 0, 1, 2),
                                filterCase("day 16 October clicked", List.of(Map.of(
                                        "x", "2026-10-16T00:00:00.000Z", "Day", "2026-10-16T00:00:00.000Z",
                                        "from", "2026-10-16T00:00:00.000Z",
                                        "to", "2026-10-16T23:59:59.999Z")), 3, 4))),
                scenario("line per sender over days: the clicked point's day and sender",
                        "all", List.of(term("EventTime", "x"), term("sender", "series")), Set.of("x", "Day"),
                        List.of(filterCase("mchen on 15 October", List.of(Map.of(
                                "x", "2026-10-15T00:00:00.000Z", "series", "mchen",
                                "from", "2026-10-15T00:00:00.000Z", "to", "2026-10-15T23:59:59.999Z")), 2))));
        final Map<String, Object> fixture = new java.util.LinkedHashMap<>();
        fixture.put("chartId", "vis-1");
        fixture.put("rows", List.of(
                row("devans", "phall", "2026-10-14T09:00:00.000Z"),
                row("phall", "devans", "2026-10-14T15:00:00.000Z"),
                row("mchen", "cdavis", "2026-10-15T10:00:00.000Z"),
                row("cdavis", "mchen", "2026-10-16T11:00:00.000Z"),
                row("jlee", "swhite", "2026-10-16T12:00:00.000Z"),
                row("mchen", "jlee", "2026-10-17T08:00:00.000Z")));
        fixture.put("scenarios", scenarios);

        stroom.ai.impl.GoldenFiles.assertMatches("selection-filters/scenarios.json",
                JsonUtil.writeValueAsString(sorted(fixture), true));
    }

    // The fixture with each map's keys in order, so the file is the same each run (Map.of's order isn't).
    private static Object sorted(final Object value) {
        if (value instanceof final Map<?, ?> map) {
            final Map<String, Object> sorted = new java.util.TreeMap<>();
            map.forEach((key, item) -> sorted.put(String.valueOf(key), sorted(item)));
            return sorted;
        }
        if (value instanceof final List<?> list) {
            return list.stream().map(TestProposeDashboardChangeTool::sorted).toList();
        }
        return value;
    }

    // A case: what the chart's click selects, as the chart gives it, and the rows (by number) the filter keeps.
    private static Map<String, Object> filterCase(final String name,
                                                  final List<Map<String, String>> selections,
                                                  final Integer... rows) {
        final Map<String, Object> filterCase = new java.util.LinkedHashMap<>();
        filterCase.put("name", name);
        filterCase.put("selections", selections);
        filterCase.put("keeps", List.of(rows));
        return filterCase;
    }

    // A scenario: a filter as the agent gives it (match and terms), the chart's date keys, the expression the tool
    // builds for it, and its cases.
    private static Map<String, Object> scenario(final String name,
                                                final String match,
                                                final List<Map<String, String>> terms,
                                                final Set<String> dateKeys,
                                                final List<Map<String, Object>> cases) {
        final Change change = DashboardChanges.parse(List.of(Map.of("op", "add_selection_filter", "target", "Table",
                "chart", "Chart", "mode", "table", "match", match, "terms", terms))).getFirst();
        final Change built = ProposeDashboardChangeTool.withNodeEnds(
                ProposeDashboardChangeTool.withDatesAsRanges(change, dateKeys));
        final Map<String, Object> scenario = new java.util.LinkedHashMap<>();
        scenario.put("name", name);
        scenario.put("expression", JsonUtil.readValue(JsonUtil.writeValueAsString(
                ProposeDashboardChangeTool.filterExpression(built, "vis-1"), false), Map.class));
        scenario.put("cases", cases);
        return scenario;
    }

    private static Map<String, String> term(final String field, final String selection) {
        return Map.of("field", field, "selection", selection);
    }

    // A clicked link's selection, as a network graph gives it: its ends by role and by column.
    private static Map<String, String> link(final String source, final String target) {
        return Map.of("source", source, "target", target, "sender", source, "recipient", target);
    }

    private static Map<String, String> row(final String sender, final String recipient, final String time) {
        return Map.of("sender", sender, "recipient", recipient, "EventTime", time);
    }
}
