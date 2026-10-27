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

import stroom.ai.impl.TabSnapshots;
import stroom.ai.impl.TabSnapshots.StoredTabSnapshot;
import stroom.ai.impl.UserActions;
import stroom.ai.impl.extraction.ExtractionField;
import stroom.ai.impl.extraction.ExtractionPlan;
import stroom.ai.shared.AiTabSearch;
import stroom.ai.shared.DashboardTableContext;
import stroom.dashboard.shared.DashboardDoc;
import stroom.dashboard.shared.DashboardSearchRequest;
import stroom.dashboard.shared.Search;
import stroom.dashboard.shared.TableResultRequest;
import stroom.docref.DocRef;
import stroom.pipeline.shared.PipelineDoc;
import stroom.query.api.Column;
import stroom.query.api.TableSettings;
import stroom.util.json.JsonUtil;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

/// propose_extraction_pipeline: offers a button to make the extraction tried, once checked as the preview checks
/// it (without stepping). Nothing is written by it.
class TestProposeExtractionPipelineTool {

    private static final int CHAT = 3;
    private static final DocRef DASHBOARD = new DocRef(DashboardDoc.TYPE, "1c2d3e4f-5a6b-4c7d-8e9f-0a1b2c3d4e5f",
            "Logons");
    private static final DocRef INDEX = new DocRef("Index", "index-uuid", "Windows events");

    @TempDir
    Path tempDir;

    private ToolTestFixture fixture;
    private final TabSnapshots tabSnapshots = Mockito.mock(TabSnapshots.class);
    private final ToolRunContext runContext = new ToolRunContext(null, null, () -> false);
    private final List<Map<String, Object>> offered = new ArrayList<>();
    private ProposeExtractionPipelineTool tool;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        fixture = new ToolTestFixture(tempDir);
        Mockito.when(fixture.workspace.withTimeLimit(Mockito.any(Duration.class), Mockito.any()))
                .thenAnswer(invocation -> ((Supplier<?>) invocation.getArgument(1)).get());
        fixture.add(DASHBOARD, null, true);
        dashboard(TableSettings.builder()
                .columns(List.of(Column.builder().id("c1").name("User").expression("${UserId}").build()))
                .build());
        tool = new ProposeExtractionPipelineTool(fixture.workspace, tabSnapshots, fixture.documentAccess(),
                fixture.auditLog());
    }

    @Test
    void aButtonIsOfferedWithTheExtractionAndTheColumnsToAdd() {
        final String result = run(List.of(
                Map.of("name", "UserId", "xpath", "EventSource/User/Id"),
                Map.of("name", "Organisation", "xpath", "EventSource/User/Organisation")));

        assertThat(result)
                .startsWith("The user has been shown a 'Create extraction pipeline' button for table 'Events'")
                .contains("extracting UserId, Organisation, adding column(s) Organisation")
                .contains("Nothing changes until they confirm it");
        assertThat(offered).singleElement().satisfies(action -> {
            assertThat(action).containsEntry("action", UserActions.CREATE_EXTRACTION_PIPELINE)
                    .containsEntry("name", "Logons extraction")
                    .containsEntry("summary", "Adds each user's organisation.");
            final ExtractionPlan plan = ExtractionPlan.fromJson((String) action.get("plan"));
            assertThat(plan.componentId()).isEqualTo("table-1");
            assertThat(plan.namespace()).isEqualTo("event-logging:3");
            assertThat(plan.fields()).extracting(ExtractionField::name).containsExactly("UserId", "Organisation");
            // UserId has a column already.
            assertThat(plan.newColumns()).containsExactly("Organisation");
        });
    }

    @Test
    void aColumnTheExtractionWouldLeaveBlankIsRefused() {
        final String result = run(List.of(Map.of("name", "Organisation", "xpath", "EventSource/User/Organisation")));

        assertThat(result).startsWith("Nothing was offered, as the extraction can't be used as it is:\n- Column "
                                      + "'User' uses UserId");
        assertThat(offered).isEmpty();
    }

    @Test
    void aFieldNamedAsAColumnShowingSomethingElseIsRefused() {
        dashboard(TableSettings.builder()
                .columns(List.of(Column.builder().id("c1").name("Organisation").expression("${Dept}").build()))
                .build());

        assertThat(run(List.of(Map.of("name", "Dept", "xpath", "Dept"),
                Map.of("name", "Organisation", "xpath", "Org"))))
                .contains("The table has a column named 'Organisation' already");
        assertThat(offered).isEmpty();
    }

    @Test
    void aTableThatExtractsAlreadyIsntOfferedANewPipelineHere() {
        dashboard(TableSettings.builder()
                .columns(List.of(Column.builder().id("c1").name("User").expression("${UserId}").build()))
                .extractValues(true)
                .extractionPipeline(new DocRef(PipelineDoc.TYPE, "p1", "Logon extraction"))
                .build());

        assertThat(run(List.of(Map.of("name", "UserId", "xpath", "Id"))))
                .contains("extracts its values with pipeline 'Logon extraction' already");
        assertThat(offered).isEmpty();
    }

    @Test
    void theDashboardMustBeOneTheUserCanView() {
        fixture.visible.remove(DASHBOARD.getUuid());

        assertThat(run(List.of(Map.of("name", "UserId", "xpath", "Id")))).isEqualTo(DocumentAccess.NO_PERMISSION);
        assertThat(offered).isEmpty();
    }

    private void dashboard(final TableSettings settings) {
        final DashboardTableContext live = new DashboardTableContext("Dashboard 'Logons'", null,
                DashboardSearchRequest.builder()
                        .search(Search.builder().dataSourceRef(INDEX).build())
                        .componentResultRequests(List.of(TableResultRequest.builder()
                                .componentId("table-1")
                                .tableSettings(settings)
                                .build()))
                        .build());
        final AiTabSearch table = new AiTabSearch("Events", "table-1", null, live, List.of("User"), 10L, false);
        final StoredTabSnapshot snapshot = new StoredTabSnapshot(5, 1, DASHBOARD, "/", "Logons", "Dashboard", false,
                null, "h", 0L, List.of(), List.of(table), null);
        Mockito.when(tabSnapshots.forLatestQuestion(CHAT)).thenReturn(Optional.of(snapshot));
    }

    private String run(final List<Map<String, String>> fields) {
        final String arguments = JsonUtil.writeValueAsString(Map.of("table", "Events", "fields", fields,
                "namespace", "event-logging:3", "name", "Logons extraction",
                "summary", "Adds each user's organisation."));
        final ToolResult result = runContext.run(() -> tool.execute(new ToolCall("c1",
                ProposeExtractionPipelineTool.NAME, arguments), ToolCalls.inChat(CHAT, fixture.workspace)));
        result.userActions().forEach(action -> offered.add(action.toMap()));
        return result.text();
    }
}
