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
import stroom.ai.impl.extraction.ExtractionChange;
import stroom.ai.impl.extraction.ExtractionField;
import stroom.ai.impl.extraction.ExtractionPipelines;
import stroom.ai.impl.extraction.ExtractionPlan;
import stroom.ai.impl.extraction.ExtractionXslt;
import stroom.ai.shared.AiTabSearch;
import stroom.ai.shared.DashboardTableContext;
import stroom.dashboard.shared.DashboardDoc;
import stroom.dashboard.shared.DashboardSearchRequest;
import stroom.dashboard.shared.Search;
import stroom.dashboard.shared.TableResultRequest;
import stroom.docref.DocRef;
import stroom.pipeline.shared.PipelineDoc;
import stroom.pipeline.shared.XsltDoc;
import stroom.pipeline.shared.data.PipelineDataBuilder;
import stroom.pipeline.shared.data.PipelineProperty;
import stroom.pipeline.shared.data.PipelinePropertyValue;
import stroom.query.api.Column;
import stroom.query.api.TableSettings;
import stroom.security.shared.DocumentPermission;
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
    private static final DocRef PIPELINE = new DocRef(PipelineDoc.TYPE, "p1", "Logon extraction");
    private static final DocRef XSLT = new DocRef(XsltDoc.TYPE, "x1", "Logon extraction");

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
    void aTableThatExtractsAlreadyIsOfferedAChangeInPlaceFirstAndANewChild() {
        final String code = ExtractionXslt.xslt("event-logging:3", List.of(new ExtractionField("UserId", "Id",
                null)));
        extracting(code);
        Mockito.when(fixture.securityContext.hasDocumentPermission(XSLT, DocumentPermission.EDIT)).thenReturn(true);

        final String result = run(List.of(Map.of("name", "UserId", "xpath", "Id"),
                Map.of("name", "Organisation", "xpath", "Org")));

        assertThat(result).startsWith("The user has been shown two buttons for table 'Events'")
                .contains("its XSLT written again from the fields UserId, Organisation, adding column(s) "
                          + "Organisation")
                .contains("'Change extraction pipeline' changes pipeline 'Logon extraction' in place");
        assertThat(offered).extracting(action -> action.get("action")).containsExactly(
                UserActions.CHANGE_EXTRACTION_PIPELINE, UserActions.CREATE_EXTRACTION_PIPELINE);
        final ExtractionPlan plan = ExtractionPlan.fromJson((String) offered.getFirst().get("plan"));
        assertThat(plan.existing()).isEqualTo(new ExtractionPlan.Existing(PIPELINE, ExtractionPipelines.XSLT_ELEMENT,
                XSLT, ExtractionChange.sha256(code)));
        assertThat(plan.newColumns()).containsExactly("Organisation");
        assertThat(offered.get(1).get("plan")).isEqualTo(offered.getFirst().get("plan"));
    }

    @Test
    void aUserWhoCantEditThePipelinesXsltIsOfferedOnlyANewChild() {
        extracting(ExtractionXslt.xslt("", List.of(new ExtractionField("UserId", "Id", null))));

        assertThat(run(List.of(Map.of("name", "UserId", "xpath", "Id"), Map.of("name", "Org", "xpath", "Org"))))
                .contains("They can't change pipeline 'Logon extraction' in place, as they can't edit its XSLT.");
        assertThat(offered).extracting(action -> action.get("action"))
                .containsExactly(UserActions.CREATE_EXTRACTION_PIPELINE);
    }

    @Test
    void fieldsAreAddedToAnXsltWrittenByHandButNotOnesItGives() {
        extracting("""
                <xsl:stylesheet version="2.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
                  <xsl:template match="/*/*"><record><data name="UserId" value="{Id}"/></record></xsl:template>
                </xsl:stylesheet>""");

        assertThat(run(List.of(Map.of("name", "UserId", "xpath", "Id"))))
                .startsWith("Nothing was offered: The extraction's XSLT, written by hand, gives UserId already.");
        assertThat(offered).isEmpty();

        // The User column shows UserId, which it gives already, so isn't left blank.
        assertThat(run(List.of(Map.of("name", "Organisation", "xpath", "Org"))))
                .contains("the fields Organisation added to its XSLT, adding column(s) Organisation");
        assertThat(ExtractionPlan.fromJson((String) offered.getFirst().get("plan")).fields())
                .extracting(ExtractionField::name).containsExactly("Organisation");
    }

    @Test
    void anXsltNamingFieldsAsItRunsIsntRefusedForColumnsItMayFill() {
        // UserId, which the User column shows, is named as it runs, so isn't in its code.
        extracting("""
                <xsl:stylesheet version="2.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
                  <xsl:template match="/*/*"><record><xsl:for-each select="Data">
                    <data name="{@Name}" value="{@Value}"/></xsl:for-each></record></xsl:template>
                </xsl:stylesheet>""");

        assertThat(run(List.of(Map.of("name", "Organisation", "xpath", "Org"))))
                .contains("its XSLT names some fields as it runs");
        assertThat(offered).hasSize(1);
    }

    @Test
    void aPipelineFindingItsXsltByNameCantBeChangedThisWay() {
        extracting("<xsl:stylesheet/>");
        final PipelineDataBuilder data = new PipelineDataBuilder(ExtractionPipelines.data(null, XSLT));
        data.addProperty(new PipelineProperty(ExtractionPipelines.XSLT_ELEMENT, "xsltNamePattern",
                new PipelinePropertyValue("${feed}")));
        fixture.add(PIPELINE, PipelineDoc.builder().uuid(PIPELINE.getUuid()).name(PIPELINE.getName())
                .pipelineData(data.build()).build(), true);

        assertThat(run(List.of(Map.of("name", "UserId", "xpath", "Id"))))
                .contains("finds its XSLT by the name pattern '${feed}'");
        assertThat(offered).isEmpty();
    }

    @Test
    void aPipelineWhoseXsltCantBeReadCantBeChanged() {
        extracting("<xsl:stylesheet/>");
        fixture.visible.remove(XSLT.getUuid());

        assertThat(run(List.of(Map.of("name", "UserId", "xpath", "Id"))))
                .contains("whose XSLT 'Logon extraction' isn't one you can read");
        assertThat(offered).isEmpty();
    }

    @Test
    void theDashboardMustBeOneTheUserCanView() {
        fixture.visible.remove(DASHBOARD.getUuid());

        assertThat(run(List.of(Map.of("name", "UserId", "xpath", "Id")))).isEqualTo(DocumentAccess.NO_PERMISSION);
        assertThat(offered).isEmpty();
    }

    // The table extracts its values with a pipeline whose XSLT has this code.
    private void extracting(final String code) {
        fixture.add(PIPELINE, PipelineDoc.builder().uuid(PIPELINE.getUuid()).name(PIPELINE.getName())
                .pipelineData(ExtractionPipelines.data(null, XSLT)).build(), true);
        fixture.add(XSLT, XsltDoc.builder().uuid(XSLT.getUuid()).name(XSLT.getName()).data(code).build(), true);
        dashboard(TableSettings.builder()
                .columns(List.of(Column.builder().id("c1").name("User").expression("${UserId}").build()))
                .extractValues(true)
                .extractionPipeline(PIPELINE)
                .build());
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
