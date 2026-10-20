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
import stroom.ai.impl.TabSnapshots;
import stroom.ai.impl.TabSnapshots.StoredPart;
import stroom.ai.impl.TabSnapshots.StoredTabSnapshot;
import stroom.ai.impl.TabSnapshots.UnsavedPart;
import stroom.ai.impl.UserActions;
import stroom.ai.impl.workspace.ChatWorkspace;
import stroom.ai.shared.AiTabSnapshotPart;
import stroom.dashboard.shared.ValidateExpressionResult;
import stroom.docref.DocRef;
import stroom.query.impl.QueryService;
import stroom.util.json.JsonUtil;

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

class TestProposeQueryChangeTool {

    private static final int CHAT = 3;
    private static final DocRef QUERY = new DocRef("Query", "query-uuid", "Twitter tweets");
    private static final DocRef INDEX = new DocRef("ElasticIndex", "ecs-uuid", "TWITTER-ECS-v1");
    private static final String BASE = "from \"TWITTER-ECS-v1\"\nselect user.name\n";

    private final ChatWorkspace workspace = Mockito.mock(ChatWorkspace.class);
    private final TabSnapshots tabSnapshots = Mockito.mock(TabSnapshots.class);
    private final DataSourceAccess dataSourceAccess = Mockito.mock(DataSourceAccess.class);
    private final DocumentAccess documentAccess = Mockito.mock(DocumentAccess.class);
    private final QueryService queryService = Mockito.mock(QueryService.class);
    private final ToolRunContext context = new ToolRunContext(null, null, () -> false);
    private final List<Map<String, Object>> offered = new ArrayList<>();
    private ProposeQueryChangeTool tool;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        Mockito.when(workspace.limits()).thenReturn(new AiWorkspaceConfig());
        Mockito.when(workspace.withTimeLimit(Mockito.any(Duration.class), Mockito.any()))
                .thenAnswer(invocation -> ((Supplier<?>) invocation.getArgument(1)).get());
        Mockito.when(documentAccess.canView(QUERY)).thenReturn(true);
        Mockito.when(dataSourceAccess.canUse(INDEX)).thenReturn(true);
        Mockito.when(queryService.getReferencedDataSource(Mockito.anyString())).thenReturn(Optional.of(INDEX));
        Mockito.when(queryService.validateQuery(Mockito.anyString()))
                .thenReturn(new ValidateExpressionResult(true, "ok", false));
        tool = new ProposeQueryChangeTool(workspace, tabSnapshots, dataSourceAccess, documentAccess,
                () -> queryService);
        tab(QUERY, BASE);
    }

    @Test
    void aRevisedQueryIsOfferedLaidOutWithAnApplyToQueryButton() {
        final String result = run("from \"TWITTER-ECS-v1\" where event.code = \"Tweet\" select user.name");

        final String expected = "from \"TWITTER-ECS-v1\"\nwhere event.code = \"Tweet\"\nselect user.name\n";
        assertThat(result)
                .contains("'Apply to query' button")
                .contains("reading ElasticIndex 'TWITTER-ECS-v1'")
                .endsWith(expected.stripTrailing() + "\n");
        assertThat(takeUserActions()).singleElement().satisfies(action -> assertThat(action)
                .containsEntry("action", UserActions.APPLY_QUERY_CHANGE)
                .containsEntry("request", expected)
                .containsEntry("base", BASE)
                .containsEntry("base_hash", ProposeCodeChangeTool.sha256(BASE))
                .containsEntry("snapshot_hash", "snap-hash")
                .containsEntry("added", 1)
                .containsEntry("removed", 0)
                .containsEntry("summary", "Only tweets."));
        // It is the formatted query that is checked.
        Mockito.verify(queryService).validateQuery(expected);
        Mockito.verify(documentAccess).logView(Mockito.eq(ToolSession.chat(CHAT)), Mockito.eq(QUERY),
                Mockito.contains("query"));
    }

    @Test
    void onlyTheQueryOpenInTheActiveTabCanBeChanged() {
        tab(new DocRef("XSLT", "xslt-uuid", "X"), BASE);
        assertThat(run("from v select a")).isEqualTo(ProposeQueryChangeTool.NOT_A_QUERY);

        Mockito.when(tabSnapshots.forLatestQuestion(CHAT)).thenReturn(Optional.empty());
        assertThat(run("from v select a")).isEqualTo(ProposeQueryChangeTool.NOT_A_QUERY);

        tab(QUERY, BASE);
        Mockito.when(documentAccess.canView(QUERY)).thenReturn(false);
        assertThat(run("from v select a")).isEqualTo(DocumentAccess.NO_PERMISSION);
        assertThat(takeUserActions()).isEmpty();
    }

    @Test
    void anInvalidSameOrUnusableQueryIsRefused() {
        Mockito.when(queryService.validateQuery(Mockito.anyString()))
                .thenReturn(new ValidateExpressionResult(false, "Unexpected token", false));
        assertThat(run("from \"TWITTER-ECS-v1\" selectt a")).contains("not valid, so no button was offered: "
                                                                      + "Unexpected token");

        Mockito.when(queryService.validateQuery(Mockito.anyString()))
                .thenReturn(new ValidateExpressionResult(true, "ok", false));
        assertThat(run("from \"TWITTER-ECS-v1\" select user.name")).contains("the same as the tab's");

        Mockito.when(dataSourceAccess.canUse(INDEX)).thenReturn(false);
        assertThat(run("from \"TWITTER-ECS-v1\" select a")).isEqualTo(ValidateStroomQlTool.NO_DATA_SOURCE);
        assertThat(takeUserActions()).isEmpty();
    }

    private void tab(final DocRef docRef, final String query) {
        final StoredTabSnapshot snapshot = new StoredTabSnapshot(5, 1, docRef, "/", docRef.getName(), null, false,
                null, "snap-hash", 0L, List.of(new StoredPart(AiTabSnapshotPart.QUERY, "application/x-stroomql",
                "/tabs/1/query.sql", 10, 10, false)), List.of(), null);
        Mockito.when(tabSnapshots.forLatestQuestion(CHAT)).thenReturn(Optional.of(snapshot));
        Mockito.when(tabSnapshots.part(CHAT, snapshot, AiTabSnapshotPart.QUERY))
                .thenReturn(Optional.of(new UnsavedPart(query, false, "/tabs/1/query.sql")));
    }

    private String run(final String query) {
        final String arguments = JsonUtil.writeValueAsString(Map.of("query", query, "summary", "Only tweets."));
        return call(arguments);
    }

    // Runs a call as part of the agent run, keeping the user actions its result offers for takeUserActions.
    private String call(final String arguments) {
        final ToolResult result = context.run(() -> tool.execute(
                new ToolCall("c1", ProposeQueryChangeTool.NAME, arguments), ToolCalls.inChat(CHAT, workspace)));
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
