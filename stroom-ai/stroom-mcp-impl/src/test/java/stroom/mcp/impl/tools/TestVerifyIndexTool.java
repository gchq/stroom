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

package stroom.mcp.impl.tools;

import stroom.ai.impl.tools.IndexSearches;
import stroom.ai.impl.tools.IndexSearches.Found;
import stroom.ai.impl.tools.IndexSearches.Probe;
import stroom.ai.impl.tools.StreamReader.Fetched;
import stroom.ai.impl.tools.ToolCallContext;
import stroom.ai.impl.tools.ToolResult;
import stroom.dashboard.shared.DashboardDoc;
import stroom.dashboard.shared.TableComponentSettings;
import stroom.query.api.Column;
import stroom.query.api.datasource.QueryField;
import stroom.search.elastic.shared.ElasticIndexDoc;
import stroom.util.shared.ResultPage;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;

/// verify_index: the build's verification dashboard, confirmed by the user, and the test searches through it (the
/// checks themselves are [TestIndexChecks]').
class TestVerifyIndexTool {

    private final IndexingWorld world = new IndexingWorld();
    private final IndexSearches indexSearches = Mockito.mock(IndexSearches.class);
    private final VerifyIndexTool tool = new VerifyIndexTool(
            new IndexChecks(indexSearches, Mockito.mock(PipelineStepping.class), world.processing.feeds.streamReader,
                    world.memory, world.processing.agreements, wait -> {
            }),
            world.indexFields, indexSearches, world.plans, world.world.documentAccess, world.world.guard(),
            world.processing.feeds.consent, world.processing.feeds.plan, world.world.records(),
            world.world.pipelineShapes);

    TestVerifyIndexTool() {
        Mockito.when(world.queryService.findFields(any())).thenReturn(new ResultPage<>(List.of(
                QueryField.createId("StreamId"), QueryField.createId("EventId"), QueryField.createDate("@timestamp"),
                QueryField.createKeyword("user.name"))));
        Mockito.when(world.processing.feeds.streamReader.fetch(anyLong(), anyLong(), Mockito.nullable(String.class)))
                .thenReturn(new Fetched("ACME", "Events", "", true, 3L, List.of(), List.of()));
        Mockito.when(indexSearches.search(any(), any(), anyInt(), any())).thenAnswer(inv -> {
            final Probe probe = inv.getArgument(1);
            if (probe.columns().stream().map(Column::getName).toList().equals(List.of("All", "Count"))) {
                return new Found(List.of(Map.of("All", "all", "Count", "3")), List.of(), true);
            }
            return new Found(List.of(Map.of("StreamId", "6", "EventId", "1", "user.name", "alice", "@timestamp",
                    "2026-10-11T08:00:00.000Z")), List.of(), true);
        });
        Mockito.when(indexSearches.count(any(), any(), any())).thenCallRealMethod();
    }

    private void index(final boolean inBuild) {
        world.elasticIndex("i", "ACME-ES", "ecs-acme-v1", null, inBuild);
        world.world.docs.put("i", ((ElasticIndexDoc) world.world.docs.get("i")).copy().timeField("@timestamp")
                .build());
    }

    private static String args(final String more) {
        return "{\"build\": \"" + IndexingWorld.BUILD + "\", \"index_uuid\": \"i\", \"backend\": \"elasticsearch\", "
               + "\"stream_ids\": [6], \"expected_documents\": 3, \"fields\": [\"@timestamp\", \"user.name\"], "
               + "\"exact\": [{\"field\": \"user.name\", \"value\": \"alice\"}]" + more + "}";
    }

    private Map<String, Object> call(final String arguments) {
        return call(arguments, IndexingWorld.noForms());
    }

    private Map<String, Object> call(final String arguments, final ToolCallContext context) {
        final ToolResult result = tool.execute(BuildToolCalls.call(VerifyIndexTool.NAME, arguments), context);
        assertThat(result.isError()).as(result.text()).isFalse();
        return BuildToolCalls.json(result.text());
    }

    // A user who agrees in every form.
    private ToolCallContext agreeing() {
        return world.forms(question -> Map.of("value", true));
    }

    @Test
    void theBuildsDashboardIsMadeOnceTheUserConfirmsItsColumns() {
        index(true);

        // Without a form the user can't confirm it: nothing is made.
        final ToolResult refused = tool.execute(BuildToolCalls.call(VerifyIndexTool.NAME, args("")),
                IndexingWorld.noForms());
        assertThat(refused.text()).startsWith("Not done: Create the verification dashboard 'ACME-ES-VERIFY' on "
                                              + "index 'ACME-ES'.");
        assertThat(world.world.created).isEmpty();

        final Map<String, Object> verified = call(args(""), agreeing());
        assertThat(world.asked).singleElement().asString()
                .startsWith("Create the verification dashboard 'ACME-ES-VERIFY' on index 'ACME-ES'")
                .contains("- initial query: @timestamp from 2026-10-04T00:00:00.000Z through today");
        assertThat(world.world.created).containsExactly("Folder:Indexing", "Dashboard:ACME-ES-VERIFY");
        final Map<?, ?> dashboard = (Map<?, ?>) verified.get("dashboard");
        assertThat(dashboard.get("saved")).isEqualTo(true);
        assertThat((String) dashboard.get("link")).contains("docType=Dashboard");
        assertThat(verified.get("passed")).isEqualTo(true);
        final DashboardDoc made = (DashboardDoc) world.world.docs.get(String.valueOf(dashboard.get("uuid")));
        final TableComponentSettings table = (TableComponentSettings) made.getDashboardConfig().getComponents()
                .get(1).getSettings();
        assertThat(table.getColumns().stream().map(Column::getName))
                .containsExactly("@timestamp", "user.name", "StreamId", "EventId");
        assertThat(world.world.nodes.get(made.getUuid()).parent)
                .isEqualTo(world.world.folder("System/" + BuildWorld.WORKSPACE + "/" + IndexingWorld.BUILD
                                              + "/Indexing").uuid);
    }

    @Test
    void anIndexFromElsewhereIsSearchedThroughAnUnsavedDashboard() {
        index(false);

        final Map<String, Object> verified = call(args(""));

        final Map<?, ?> dashboard = (Map<?, ?>) verified.get("dashboard");
        assertThat(dashboard.get("saved")).isEqualTo(false);
        assertThat(dashboard.containsKey("link")).isFalse();
        assertThat(verified.get("passed")).isEqualTo(true);
        assertThat(world.world.created).isEmpty();
    }

    @Test
    void checksFalseMakesTheDashboardAndGivesItsLinkWithNoSearches() {
        index(true);
        final Map<String, Object> skipped = call(args(", \"checks\": false"), agreeing());

        assertThat(skipped.get("checks_skipped")).isEqualTo("as the user asked");
        assertThat(skipped).doesNotContainKey("checks");
        assertThat((String) ((Map<?, ?>) skipped.get("dashboard")).get("link")).contains("docType=Dashboard");
        // Not the build's index: nothing to give without the checks.
        world.world.docs.remove("i");
        final TestVerifyIndexTool other = new TestVerifyIndexTool();
        other.index(false);
        final ToolResult refused = other.tool.execute(BuildToolCalls.call(VerifyIndexTool.NAME,
                args(", \"checks\": false")), IndexingWorld.noForms());
        assertThat(refused.isError()).isTrue();
        assertThat(refused.text()).contains("so no dashboard is saved for it");
    }

    @Test
    void columnsNotInTheIndexAreRefused() {
        index(true);

        final ToolResult refused = tool.execute(BuildToolCalls.call(VerifyIndexTool.NAME, args("").replace(
                "\"user.name\"]", "\"EventSource.User.Id\"]")), IndexingWorld.noForms());

        assertThat(refused.isError()).isTrue();
        assertThat(refused.text()).contains("Not fields of index 'ACME-ES' in Stroom")
                .contains("EventSource.User.Id");
    }

    @Test
    void aSearchThatWouldMisleadIsRefusedBeforeAnythingIsMade() {
        index(true);

        final ToolResult refused = tool.execute(BuildToolCalls.call(VerifyIndexTool.NAME, args(
                ", \"searches\": [{\"field\": \"user.name\", \"condition\": \"STARTS_WITH\", \"value\": \"al\"}]")),
                IndexingWorld.noForms());

        assertThat(refused.text()).contains("These searches would give a misleading answer")
                .contains("use EQUALS 'al*'");
        assertThat(world.world.created).isEmpty();
    }
}
