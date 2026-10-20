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
import stroom.ai.impl.TabSnapshots;
import stroom.ai.impl.TabSnapshots.StoredTabSnapshot;
import stroom.ai.impl.TabSnapshots.UnsavedPart;
import stroom.ai.impl.UserActions;
import stroom.ai.impl.workspace.ChatWorkspace;
import stroom.ai.shared.AiTabSearch;
import stroom.ai.shared.AiTabSnapshotPart;
import stroom.ai.shared.DashboardTableContext;
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
import stroom.util.json.JsonUtil;
import stroom.util.shared.ResultPage;

import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

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
                        .containsEntry("query", DashboardChanges.expressionToMap(expression))));
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

        assertThat(result).isEqualTo("The dashboard has no table 'Nope'. Its tables are: 'Events'.");
    }

    @Test
    void aTabThatIsntADashboardIsRefused() {
        Mockito.when(tabSnapshots.forLatestQuestion(CHAT)).thenReturn(Optional.of(new StoredTabSnapshot(5, 1,
                new DocRef("XSLT", "x", "X"), "/", "X", null, false, null, "h", 0L, List.of(), List.of(), null)));

        assertThat(run(List.of(Map.of("op", "hide_column", "column", "UserId"))))
                .isEqualTo(ProposeDashboardChangeTool.NOT_DASHBOARD);
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

    private void dashboardTab(final boolean cut) {
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
                .thenReturn(Optional.of(new UnsavedPart(SETTINGS, cut, "/tabs/1/dashboard.md")));
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
}
