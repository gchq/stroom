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
import stroom.docref.DocRef;
import stroom.query.api.datasource.QueryField;
import stroom.query.impl.QueryService;
import stroom.util.json.JsonUtil;
import stroom.util.shared.ResultPage;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Duration;
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
    private static final String SETTINGS = "Time range: last hour\n## Table 'Events' (id table-1)\n- UserId\n";

    private final ChatWorkspace workspace = Mockito.mock(ChatWorkspace.class);
    private final TabSnapshots tabSnapshots = Mockito.mock(TabSnapshots.class);
    private final DocumentAccess documentAccess = Mockito.mock(DocumentAccess.class);
    private final DataSourceAccess dataSourceAccess = Mockito.mock(DataSourceAccess.class);
    private final QueryService queryService = Mockito.mock(QueryService.class);
    private final AiAuditLog auditLog = Mockito.mock(AiAuditLog.class);
    private final ToolRunContext context = new ToolRunContext(null, null, () -> false);
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
                () -> queryService, auditLog);
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
        assertThat(context.takeUserActions("c1")).singleElement().satisfies(action -> assertThat(action)
                .containsEntry("action", UserActions.APPLY_DASHBOARD_CHANGE)
                .containsEntry("component", "Events")
                .containsEntry("component_id", "table-1")
                .containsEntry("snapshot_hash", AiTabSnapshotPart.hashText(SETTINGS))
                .containsEntry("summary", "Counts events per user."));
        Mockito.verify(auditLog).documentViewed(Mockito.eq(CHAT), Mockito.eq(DASHBOARD),
                Mockito.contains("Proposed a change to table 'Events'"));
    }

    @Test
    void anUnknownColumnIsRefusedSayingWhy() {
        final String result = run(List.of(Map.of("op", "hide_column", "column", "Nope")));

        assertThat(result)
                .startsWith("No change was proposed, as it isn't valid")
                .contains("the table has no column 'Nope'. Its columns are: UserId, EventType.");
        assertThat(context.takeUserActions("c1")).isEmpty();
    }

    @Test
    void anInvalidExpressionIsRefusedSayingWhy() {
        assertThat(run(List.of(Map.of("op", "add_column", "name", "Bad", "expression", "count(("))))
                .contains("Change 1 (add_column): The expression 'count((' can't be read");
        assertThat(run(List.of(Map.of("op", "edit_column", "column", "UserId", "expression",
                "upperCase(${Usr})"))))
                .contains("names a field 'Usr' that the dashboard's data source doesn't have");
        assertThat(context.takeUserActions("c1")).isEmpty();
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
        assertThat(context.takeUserActions("c1")).isEmpty();
        Mockito.verifyNoInteractions(auditLog);
    }

    @Test
    void settingsCutShortCantBeProposedAgainst() {
        dashboardTab(true);

        assertThat(run(List.of(Map.of("op", "hide_column", "column", "UserId"))))
                .contains("aren't in the tab snapshot in full");
        assertThat(context.takeUserActions("c1")).isEmpty();
    }

    @Test
    void badChangesAreRefused() {
        assertThat(run(List.of(Map.of("op", "drop_table")))).startsWith("Error: Change 1: op must be one of");
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
        return context.run(() -> tool.execute(ToolExecutionRequest.builder().id("c1")
                .name(ProposeDashboardChangeTool.NAME).arguments(arguments).build(), CHAT));
    }
}
