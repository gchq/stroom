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

package stroom.ai.impl.tools.timeline;

import stroom.ai.impl.FakeTabWorkspace;
import stroom.ai.impl.TabSnapshots;
import stroom.ai.impl.TabSnapshots.StoredTabSnapshot;
import stroom.ai.impl.UserActions;
import stroom.ai.impl.tools.ToolCall;
import stroom.ai.impl.tools.ToolCallContext;
import stroom.ai.impl.tools.ToolResult;
import stroom.ai.impl.tools.ToolRunContext;
import stroom.ai.impl.tools.ToolSession;
import stroom.ai.impl.tools.UserQuestions;
import stroom.ai.impl.tools.documents.DocumentAccess;
import stroom.ai.impl.tools.propose.ProposeCodeChangeTool;
import stroom.ai.impl.tools.query.DataSourceAccess;
import stroom.ai.impl.vis.ChartLibraries;
import stroom.ai.impl.vis.ChartLibraries.EChartsInstall;
import stroom.ai.impl.workspace.SessionWorkspace;
import stroom.ai.impl.workspace.WorkspacePath;
import stroom.dashboard.shared.DashboardDoc;
import stroom.dataconnection.shared.DataConnectionDoc;
import stroom.docref.DocRef;
import stroom.explorer.shared.ExplorerConstants;
import stroom.explorer.shared.ExplorerNode;
import stroom.script.shared.ScriptDoc;
import stroom.util.json.JsonUtil;
import stroom.visualisation.shared.VisualisationDoc;

import io.modelcontextprotocol.spec.McpSchema.ElicitResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/// propose_timeline_dashboard: the button it offers for a timeline the chat built (the table's hash, its rows, the
/// chart's columns, the earlier run's Data Connection to replace), the folder asked (the attached dashboard's
/// suggested), and what it refuses: no timeline, an empty table, a folder the user can't create in, a Data
/// Connection to replace they can't edit, no ECharts.
@SuppressWarnings("unchecked")
class TestProposeTimelineDashboardTool {

    private static final int CHAT = 7;
    private static final String FOLDER_PATH = "/Investigations/Case 12";
    private static final DocRef FOLDER = new DocRef(ExplorerConstants.FOLDER_TYPE,
            "0f5b7a2e-6a1d-4c3b-9d7e-2a1b3c4d5e6f", "Case 12");
    private static final DocRef SCRIPT = new DocRef(ScriptDoc.TYPE, "echarts-uuid", "ECharts");
    private static final String SPANS = "/outputs/timelines/jsmith/spans.csv";
    private static final String CSV = String.join(",", TimelineTables.SPAN_COLUMNS) + "\r\n"
                                      + "s1,2026-11-10T09:00:00Z,2026-11-10T09:20:00Z,WINWORD.EXE,ws-01,focus,,,"
                                      + "ws-01,Endpoint,3,focus,\r\n"
                                      + "a1,2026-11-10T09:25:00Z,2026-11-10T09:27:00Z,research.example.org,ws-01,"
                                      + "activity,,,ws-01,Proxy,2,time,\r\n";

    @TempDir
    Path tempDir;

    private final DocumentAccess documentAccess = Mockito.mock(DocumentAccess.class);
    private final ChartLibraries chartLibraries = Mockito.mock(ChartLibraries.class);
    private final TabSnapshots tabSnapshots = Mockito.mock(TabSnapshots.class);
    private final List<Map<String, Object>> forms = new ArrayList<>();
    private FakeTabWorkspace files;
    private SessionWorkspace workspace;
    private ProposeTimelineDashboardTool tool;

    @BeforeEach
    void setUp() {
        files = new FakeTabWorkspace(tempDir);
        workspace = new SessionWorkspace(files.workspace(), CHAT);
        Mockito.when(documentAccess.findFolder(FOLDER_PATH)).thenReturn(Optional.of(FOLDER));
        // The path of the folder holding it.
        Mockito.when(documentAccess.path(FOLDER)).thenReturn("/Investigations");
        Mockito.when(documentAccess.children(FOLDER)).thenReturn(List.of());
        Mockito.when(documentAccess.canCreate(Mockito.eq(FOLDER), Mockito.anyString())).thenReturn(true);
        Mockito.when(chartLibraries.findECharts()).thenReturn(Optional.of(new EChartsInstall(SCRIPT, "6.1.0")));
        Mockito.when(chartLibraries.currentUserCanUse(SCRIPT)).thenReturn(true);
        tool = new ProposeTimelineDashboardTool(files.workspace(), documentAccess, chartLibraries,
                () -> tabSnapshots, Mockito.mock(DataSourceAccess.class));
    }

    @Test
    void aButtonIsOfferedForTheSpansAsTheyAreInTheFolderGiven() {
        timeline(CSV);

        final ToolResult result = run(Map.of("entity", "jsmith", "table", "spans", "folder", FOLDER_PATH));

        assertThat(result.text()).contains("'Create Data Connection and dashboard…' button",
                "writes Data Connection 'Timeline jsmith 2026-11-09 to 2026-11-13 (spans)' in folder "
                + "'/Investigations/Case 12': the 2 rows of spans.csv", "label label, lane lane, colour kind",
                "anyone who can view folder '/Investigations/Case 12' can read it");
        final Map<String, Object> action = result.userActions().getFirst().toMap();
        assertThat(action).containsEntry("action", UserActions.CREATE_TIMELINE_DASHBOARD)
                .containsEntry("doc", Map.of("type", ExplorerConstants.FOLDER_TYPE, "uuid", FOLDER.getUuid()))
                .containsEntry("name", "Timeline jsmith 2026-11-09 to 2026-11-13 (spans)")
                .doesNotContainKey("replace");
        final TimelineDashboardPlan plan = TimelineDashboardPlan.fromJson((String) action.get("plan"));
        assertThat(plan.path()).isEqualTo(SPANS);
        assertThat(plan.hash()).isEqualTo(ProposeCodeChangeTool.sha256(CSV));
        assertThat(plan.rows()).isEqualTo(2);
        assertThat(plan.fromMs()).isEqualTo(Instant.parse("2026-11-09T00:00:00Z").toEpochMilli());
        assertThat(plan.mappings()).containsEntry("lane", "lane").containsEntry("colour", "kind");
        assertThat(plan.description()).contains("A snapshot of jsmith's activity timeline", "from Endpoint, Proxy");
        assertThat(plan.replace()).isNull();
    }

    @Test
    void withNoFolderGivenTheUserIsAskedTheAttachedDashboardsFolderSuggested() {
        timeline(CSV);
        final DocRef dashboard = new DocRef(DashboardDoc.TYPE, "dash-uuid", "Case 12 dashboard");
        Mockito.when(tabSnapshots.forLatestQuestion(CHAT)).thenReturn(Optional.of(new StoredTabSnapshot(1, 1,
                dashboard, FOLDER_PATH, "Case 12 dashboard", null, false, null, null, 0L, List.of(), List.of(),
                null)));
        Mockito.when(documentAccess.path(dashboard)).thenReturn(FOLDER_PATH);

        final ToolResult result = run(Map.of("entity", "jsmith", "table", "spans"),
                answering(Map.of("folder", FOLDER_PATH)));

        final Map<String, Object> field = (Map<String, Object>) ((Map<String, Object>) forms.getFirst()
                .get("properties")).get("folder");
        assertThat(field).containsEntry("default", FOLDER_PATH);
        assertThat(result.userActions()).hasSize(1);
    }

    @Test
    void aFolderTheUserDoesntChooseOffersNothing() {
        timeline(CSV);

        final ToolResult result = run(Map.of("entity", "jsmith", "table", "spans"), new UserQuestions() {
            @Override
            public boolean canAsk() {
                return true;
            }

            @Override
            public ElicitResult ask(final String toolName, final String callId, final String intro,
                                    final Map<String, Object> form) {
                return new ElicitResult(ElicitResult.Action.DECLINE, null);
            }
        });

        assertThat(result.text()).contains("the user didn't choose a folder");
        assertThat(result.userActions()).isEmpty();
    }

    @Test
    void anEarlierRunsDataConnectionInTheFolderIsReplaced() {
        timeline(CSV);
        final DocRef earlier = new DocRef(DataConnectionDoc.TYPE, "dc-uuid",
                "Timeline jsmith 2026-10-01 to 2026-10-07 (spans)");
        Mockito.when(documentAccess.children(FOLDER)).thenReturn(List.of(
                ExplorerNode.builder().docRef(new DocRef(DataConnectionDoc.TYPE, "other-uuid",
                        "Timeline jsmith 2026-10-01 (activities)")).build(),
                ExplorerNode.builder().docRef(earlier).build()));
        Mockito.when(documentAccess.canEdit(earlier)).thenReturn(true);

        final ToolResult result = run(Map.of("entity", "jsmith", "table", "spans", "folder", FOLDER_PATH));

        assertThat(result.text()).contains("replaces Data Connection 'Timeline jsmith 2026-10-01 to 2026-10-07 "
                                           + "(spans)' (an earlier timeline of jsmith), renamed 'Timeline jsmith "
                                           + "2026-11-09 to 2026-11-13 (spans)'");
        final Map<String, Object> action = result.userActions().getFirst().toMap();
        assertThat(TimelineDashboardPlan.fromJson((String) action.get("plan")).replace()).isEqualTo(earlier);
    }

    @Test
    void anEarlierRunsDataConnectionTheUserCantEditOffersNothing() {
        timeline(CSV);
        final DocRef earlier = new DocRef(DataConnectionDoc.TYPE, "dc-uuid",
                "Timeline jsmith 2026-10-01 to 2026-10-07 (spans)");
        Mockito.when(documentAccess.children(FOLDER)).thenReturn(List.of(ExplorerNode.builder().docRef(earlier)
                .build()));

        final ToolResult result = run(Map.of("entity", "jsmith", "table", "spans", "folder", FOLDER_PATH));

        assertThat(result.text()).contains("you don't have permission to edit it");
        assertThat(result.userActions()).isEmpty();
    }

    @Test
    void aFolderTheUserCantCreateInOffersNothing() {
        timeline(CSV);
        Mockito.when(documentAccess.canCreate(FOLDER, DataConnectionDoc.TYPE)).thenReturn(false);
        Mockito.when(documentAccess.canCreate(FOLDER, VisualisationDoc.TYPE)).thenReturn(false);

        final ToolResult result = run(Map.of("entity", "jsmith", "table", "spans", "folder", FOLDER_PATH));

        assertThat(result.text()).contains("you don't have permission to create a Data Connection or a "
                                           + "Visualisation in folder '/Investigations/Case 12'");
        assertThat(result.userActions()).isEmpty();
    }

    @Test
    void aFolderTheUserCantSeeIsRefused() {
        timeline(CSV);

        final ToolResult result = run(Map.of("entity", "jsmith", "table", "spans", "folder", "/Secret"));

        assertThat(result.text()).startsWith("Error: No folder '/Secret'");
        assertThat(result.userActions()).isEmpty();
    }

    @Test
    void withoutEChartsNothingIsOffered() {
        timeline(CSV);
        Mockito.when(chartLibraries.findECharts()).thenReturn(Optional.empty());

        final ToolResult result = run(Map.of("entity", "jsmith", "table", "spans", "folder", FOLDER_PATH));

        assertThat(result.text()).contains("isn't installed as a Script doc", "gantt_chart");
        assertThat(result.userActions()).isEmpty();
    }

    @Test
    void aTimelineNotBuiltInTheChatIsRefused() {
        final ToolResult result = run(Map.of("entity", "jsmith", "table", "spans", "folder", FOLDER_PATH));

        assertThat(result.text()).startsWith("Error: this chat has no timeline of jsmith yet");
        assertThat(result.userActions()).isEmpty();
    }

    @Test
    void anEmptyActivitiesTableSaysHowToFillIt() {
        timeline(CSV);
        workspace.write(WorkspacePath.parse("/outputs/timelines/jsmith/activities.csv"),
                String.join(",", TimelineTables.ACTIVITY_COLUMNS) + "\r\n", null);

        final ToolResult result = run(Map.of("entity", "jsmith", "table", "activities", "folder", FOLDER_PATH));

        assertThat(result.text()).contains("has no rows", "view activities");
        assertThat(result.userActions()).isEmpty();
    }

    @Test
    void aTableOfOtherColumnsIsRefused() {
        timeline("span,start,end\r\ns1,2026-11-10T09:00:00Z,2026-11-10T09:20:00Z\r\n");

        final ToolResult result = run(Map.of("entity", "jsmith", "table", "spans", "folder", FOLDER_PATH));

        assertThat(result.text()).contains("can't be written as a Data Connection", "'span,start,end'");
        assertThat(result.userActions()).isEmpty();
    }

    @Test
    void aTableIsNeeded() {
        timeline(CSV);

        final ToolResult result = run(Map.of("entity", "jsmith", "table", "sessions", "folder", FOLDER_PATH));

        assertThat(result.text()).startsWith("Error: Give table: spans or activities.");
    }

    // A timeline of jsmith, built in the chat: its spans and the run's snapshot.
    private void timeline(final String spans) {
        workspace.write(WorkspacePath.parse(SPANS), spans, null);
        new TimelineSnapshot("jsmith", Instant.parse("2026-11-09T00:00:00Z").toEpochMilli(),
                Instant.parse("2026-11-14T00:00:00Z").toEpochMilli(), "UTC", "detail", null,
                List.of("Endpoint", "Proxy"), false, Instant.parse("2026-11-24T10:00:00Z").toEpochMilli())
                .write(workspace, "/outputs/timelines/jsmith/");
    }

    private UserQuestions answering(final Map<String, Object> answers) {
        return new UserQuestions() {
            @Override
            public boolean canAsk() {
                return true;
            }

            @Override
            public ElicitResult ask(final String toolName, final String callId, final String intro,
                                    final Map<String, Object> form) {
                forms.add(form);
                return new ElicitResult(ElicitResult.Action.ACCEPT, answers);
            }
        };
    }

    private ToolResult run(final Map<String, Object> arguments) {
        return run(arguments, UserQuestions.NONE);
    }

    private ToolResult run(final Map<String, Object> arguments, final UserQuestions questions) {
        final ToolCallContext context = ToolCallContext.builder(ToolSession.chat(CHAT))
                .workspace(workspace)
                .userQuestions(questions)
                .runContext(new ToolRunContext(null, null, () -> false))
                .build();
        return tool.execute(new ToolCall("c1", ProposeTimelineDashboardTool.NAME,
                JsonUtil.writeValueAsString(arguments)), context);
    }
}
