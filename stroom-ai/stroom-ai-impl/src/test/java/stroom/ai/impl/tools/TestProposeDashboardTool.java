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
import stroom.ai.impl.DashboardBuilder;
import stroom.ai.impl.UserActions;
import stroom.ai.impl.workspace.ChatWorkspace;
import stroom.dashboard.shared.DashboardConfig;
import stroom.dashboard.shared.ValidateExpressionResult;
import stroom.docref.DocRef;
import stroom.query.impl.QueryService;
import stroom.util.json.JsonUtil;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

class TestProposeDashboardTool {

    private static final int CHAT = 3;
    private static final DocRef VIEW = new DocRef("View", "view-uuid", "Windows events");
    private static final String QUERY = "from \"Windows events\"\nselect EventTime, User";

    private final ChatWorkspace workspace = Mockito.mock(ChatWorkspace.class);
    private final DataSourceAccess dataSourceAccess = Mockito.mock(DataSourceAccess.class);
    private final QueryService queryService = Mockito.mock(QueryService.class);
    private final DashboardBuilder dashboardBuilder = Mockito.mock(DashboardBuilder.class);
    private final ToolRunContext context = new ToolRunContext(null, null, () -> false);
    private final List<Map<String, Object>> offered = new ArrayList<>();
    private ProposeDashboardTool tool;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        Mockito.when(workspace.limits()).thenReturn(new AiWorkspaceConfig());
        Mockito.when(workspace.withTimeLimit(Mockito.any(Duration.class), Mockito.any()))
                .thenAnswer(invocation -> ((Supplier<?>) invocation.getArgument(1)).get());
        Mockito.when(dataSourceAccess.canUse(VIEW)).thenReturn(true);
        Mockito.when(queryService.getReferencedDataSource(Mockito.anyString())).thenReturn(Optional.of(VIEW));
        Mockito.when(queryService.validateQuery(Mockito.anyString()))
                .thenReturn(new ValidateExpressionResult(true, "ok", false));
        Mockito.when(dashboardBuilder.build(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.anyBoolean()))
                .thenAnswer(invocation -> new DashboardBuilder.Built(DashboardConfig.builder().build(), VIEW,
                        invocation.getArgument(2), List.of("EventTime", "User"), false, invocation.getArgument(3),
                        DashboardBuilder.CLASSIC.equals(invocation.getArgument(2))
                                ? List.of("Query: everything", "Table columns: EventTime, User")
                                : List.of()));
        tool = new ProposeDashboardTool(workspace, dataSourceAccess, () -> queryService, dashboardBuilder);
    }

    @Test
    void aDashboardIsOfferedWithACreateDashboardButton() {
        final String result = run(args("from \"Windows events\" select EventTime, User", "classic", true));

        assertThat(result)
                .contains("'Create dashboard…' button for 'Logons'")
                .contains("reading View 'Windows events'")
                .contains("2 columns (EventTime, User)")
                .contains("attached to the chat, so they can ask you to refine it (propose_dashboard_change)")
                .contains("Tell them about the button");
        assertThat(takeUserActions()).singleElement().satisfies(action -> assertThat(action)
                .containsEntry("action", UserActions.CREATE_DASHBOARD)
                .containsEntry("name", "Logons")
                .containsEntry("query", QUERY)
                .containsEntry("query_type", "classic")
                .containsEntry("preview", true)
                .containsEntry("description", "Who logged on")
                // What the dashboard will hold, shown in place of the StroomQL.
                .containsEntry("outline", List.of("Query: everything", "Table columns: EventTime, User")));
        // It is the formatted query that is checked and built from.
        Mockito.verify(queryService).validateQuery(QUERY);
        Mockito.verify(dashboardBuilder).build("Logons", QUERY, "classic", true);
    }

    @Test
    void aStroomQlDashboardHasNoOutline() {
        run(args("from \"Windows events\" select EventTime, User", "stroomql", false));

        assertThat(takeUserActions()).singleElement().satisfies(action -> assertThat(action)
                .containsEntry("query_type", "stroomql")
                .doesNotContainKey("outline"));
    }

    @Test
    void whetherToAddAPreviewMustBeSaid() {
        final Map<String, Object> args = args("from \"Windows events\" select User", "stroomql", true);
        args.remove(ProposeDashboardTool.PREVIEW_ARG);

        assertThat(run(args)).contains("data preview").contains("ask_user");
        assertThat(takeUserActions()).isEmpty();
    }

    @Test
    void anInvalidOrUnusableQueryIsRefused() {
        Mockito.when(queryService.validateQuery(Mockito.anyString()))
                .thenReturn(new ValidateExpressionResult(false, "Unexpected token", false));
        assertThat(run(args("from \"Windows events\" selectt User", "stroomql", false)))
                .contains("not valid, so no button was offered: Unexpected token");

        Mockito.when(queryService.validateQuery(Mockito.anyString()))
                .thenReturn(new ValidateExpressionResult(true, "ok", false));
        Mockito.when(dataSourceAccess.canUse(VIEW)).thenReturn(false);
        assertThat(run(args("from \"Windows events\" select User", "stroomql", false)))
                .isEqualTo(ValidateStroomQlTool.NO_DATA_SOURCE);
        assertThat(takeUserActions()).isEmpty();
    }

    @Test
    void aDashboardThatCantBeBuiltIsRefusedWithTheReason() {
        Mockito.when(dashboardBuilder.build(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.anyBoolean()))
                .thenThrow(new IllegalArgumentException("A classic dashboard can't hold a having clause."));

        assertThat(run(args("from \"Windows events\" select User", "classic", false)))
                .isEqualTo("No button was offered: A classic dashboard can't hold a having clause.");
        assertThat(takeUserActions()).isEmpty();
    }

    private static Map<String, Object> args(final String query, final String queryType, final boolean preview) {
        final Map<String, Object> args = new HashMap<>();
        args.put(ProposeDashboardTool.NAME_ARG, "Logons");
        args.put(ProposeDashboardTool.QUERY_ARG, query);
        args.put(ProposeDashboardTool.QUERY_TYPE_ARG, queryType);
        args.put(ProposeDashboardTool.PREVIEW_ARG, preview);
        args.put(ProposeDashboardTool.DESCRIPTION_ARG, "Who logged on");
        return args;
    }

    private String run(final Map<String, Object> args) {
        final String arguments = JsonUtil.writeValueAsString(args);
        return call(arguments);
    }

    // Runs a call as part of the agent run, keeping the user actions its result offers for takeUserActions.
    private String call(final String arguments) {
        final ToolResult result = context.run(() -> tool.execute(
                new ToolCall("c1", ProposeDashboardTool.NAME, arguments), ToolCalls.inChat(CHAT, workspace)));
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
