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
import stroom.ai.impl.extraction.ExtractionPipelines;
import stroom.ai.impl.extraction.ExtractionXslt;
import stroom.ai.shared.AiTabSearch;
import stroom.ai.shared.DashboardTableContext;
import stroom.dashboard.shared.DashboardDoc;
import stroom.dashboard.shared.DashboardSearchRequest;
import stroom.dashboard.shared.Search;
import stroom.dashboard.shared.TableResultRequest;
import stroom.docref.DocRef;
import stroom.pipeline.shared.PipelineDoc;
import stroom.pipeline.shared.SharedElementData;
import stroom.pipeline.shared.XsltDoc;
import stroom.pipeline.shared.stepping.PipelineStepRequest;
import stroom.pipeline.shared.stepping.SharedStepData;
import stroom.pipeline.shared.stepping.StepLocation;
import stroom.pipeline.shared.stepping.SteppingResult;
import stroom.pipeline.stepping.SteppingService;
import stroom.pipeline.xslt.XsltStore;
import stroom.query.api.Column;
import stroom.query.api.TableSettings;
import stroom.query.api.datasource.QueryField;
import stroom.query.impl.QueryService;
import stroom.security.shared.AppPermission;
import stroom.util.json.JsonUtil;
import stroom.util.shared.ResultPage;

import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.XsltTransformer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import javax.xml.transform.stream.StreamSource;

import static org.assertj.core.api.Assertions.assertThat;

/// preview_extraction: a draft extraction, written by Stroom from the fields given, stepped over the table's
/// events. Stepping is answered here by running the code each step is given (the XSLT) over the event, with Saxon,
/// as the pipeline's XSLT element would.
class TestPreviewExtractionTool {

    private static final int CHAT = 3;
    private static final DocRef DASHBOARD = new DocRef(DashboardDoc.TYPE, "1c2d3e4f-5a6b-4c7d-8e9f-0a1b2c3d4e5f",
            "Logons");
    private static final DocRef INDEX = new DocRef("Index", "index-uuid", "Windows events");
    private static final DocRef PLACEHOLDER = new DocRef(XsltDoc.TYPE, "xslt-uuid", "Any XSLT");
    // A table's own extraction XSLT, written by hand.
    private static final String HAND_WRITTEN = """
            <xsl:stylesheet version="2.0" xmlns="records:2" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
              <xsl:template match="/*"><records><xsl:apply-templates select="*"/></records></xsl:template>
              <xsl:template match="/*/*">
                <record>
                  <data name="StreamId" value="{@StreamId}"/>
                  <data name="EventId" value="{@EventId}"/>
                  <data name="UserId" value="{*:EventSource/*:User/*:Id}"/>
                </record>
              </xsl:template>
            </xsl:stylesheet>
            """;
    private static final String SNAPSHOT = """
            | User | StreamId | EventId |
            |---|---|---|
            | jsmith | 7 | 1 |
            | akhan | 7 | 2 |
            | jsmith | 7 | 1 |
            | bwu | 9 | 1 |
            """;

    @TempDir
    Path tempDir;

    private ToolTestFixture fixture;
    private final TabSnapshots tabSnapshots = Mockito.mock(TabSnapshots.class);
    private final DataSourceAccess dataSourceAccess = Mockito.mock(DataSourceAccess.class);
    private final QueryService queryService = Mockito.mock(QueryService.class);
    private final XsltStore xsltStore = Mockito.mock(XsltStore.class);
    private final SteppingService steppingService = Mockito.mock(SteppingService.class);
    private final Map<List<Long>, String> events = Map.of(
            List.of(7L, 1L), event(7, 1, "jsmith", "Finance"),
            List.of(7L, 2L), event(7, 2, "akhan", "Operations"),
            List.of(9L, 1L), event(9, 1, "bwu", null));
    private PreviewExtractionTool tool;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() throws Exception {
        fixture = new ToolTestFixture(tempDir);
        Mockito.when(fixture.workspace.withTimeLimit(Mockito.any(Duration.class), Mockito.any()))
                .thenAnswer(invocation -> ((Supplier<?>) invocation.getArgument(1)).get());
        final Path snapshot = tempDir.resolve("events.md");
        Files.writeString(snapshot, SNAPSHOT, StandardCharsets.UTF_8);
        Mockito.when(fixture.workspace.localFile(Mockito.anyInt(), Mockito.any())).thenReturn(snapshot);
        Mockito.when(fixture.securityContext.hasAppPermission(AppPermission.STEPPING_PERMISSION)).thenReturn(true);
        fixture.add(DASHBOARD, null, true);
        fixture.add(PLACEHOLDER, null, true);
        Mockito.when(xsltStore.list()).thenReturn(List.of(PLACEHOLDER));
        Mockito.when(dataSourceAccess.canUse(INDEX)).thenReturn(true);
        Mockito.when(queryService.findFields(Mockito.any())).thenReturn(new ResultPage<>(List.of(
                QueryField.createText("UserId"), QueryField.createText("EventTime"))));
        Mockito.when(steppingService.step(Mockito.any())).thenAnswer(invocation -> step(invocation.getArgument(0)));
        dashboard(List.of(Column.builder().id("c1").name("User").expression("${UserId}").build()));
        tool = new PreviewExtractionTool(fixture.workspace, tabSnapshots, fixture.documentAccess(),
                dataSourceAccess, () -> queryService, () -> xsltStore, () -> fixture.securityContext,
                new SteppingWalks(() -> steppingService), fixture.auditLog());
    }

    @Test
    void theFieldsAreExtractedFromTheTablesEventsAndCompared() {
        final String result = run(List.of(
                Map.of("name", "UserId", "xpath", "EventSource/User/Id"),
                Map.of("name", "Organisation", "xpath", "EventSource/User/Organisation")));

        assertThat(result)
                .startsWith("Tried the extraction for table 'Events' on 3 event(s), in a pipeline as the Search "
                            + "Extraction template makes")
                .contains("Nothing was changed.")
                .contains("| StreamId | EventId | UserId | Organisation |")
                .contains("| 7 | 1 | jsmith | Finance |")
                .contains("| 7 | 2 | akhan | Operations |")
                .contains("| 9 | 1 | bwu |  |")
                .contains("'Organisation' was found in 2 of 3 event(s).")
                .contains("'UserId' agrees with the indexed values column 'User' shows now, in all 3 event(s).")
                .contains("The data source has 'UserId' too")
                .doesNotContain("can't be used as it is");
        // The XSLT Stroom wrote, for the user to see.
        assertThat(fixture.written.get(PreviewExtractionTool.XSLT_OUT))
                .contains("xpath-default-namespace=\"event-logging:3\"")
                .contains("<data name=\"Organisation\">");
    }

    @Test
    void eachStreamIsSteppedInOneSessionTheNamespaceFoundFirst() {
        run(List.of(Map.of("name", "UserId", "xpath", "EventSource/User/Id")));

        final ArgumentCaptor<PipelineStepRequest> requests = ArgumentCaptor.forClass(PipelineStepRequest.class);
        Mockito.verify(steppingService, Mockito.times(4)).step(requests.capture());
        final List<PipelineStepRequest> steps = requests.getAllValues();
        // The probe, then each event with the XSLT; record indexes from 0, as event ids are from 1.
        assertThat(steps.getFirst().getCode()).containsEntry(ExtractionPipelines.XSLT_ELEMENT,
                ExtractionXslt.NAMESPACE_PROBE);
        assertThat(steps).extracting(step -> step.getStepLocation().getRecordIndex()).containsExactly(0L, 0L, 1L, 0L);
        assertThat(steps).extracting(step -> step.getStepLocation().getMetaId()).containsExactly(7L, 7L, 7L, 9L);
        assertThat(steps.get(1).getCode().get(ExtractionPipelines.XSLT_ELEMENT)).contains("<data name=\"UserId\">");
        // Tried in a pipeline whose XSLT is set (to one the user can view), as stepping needs.
        assertThat(steps.getFirst().getPipelineDoc().getPipelineData().getAddedProperties())
                .anySatisfy(property -> assertThat(property.getValue().getEntity()).isEqualTo(PLACEHOLDER));
        Mockito.verify(steppingService, Mockito.times(2)).terminateStepping(Mockito.any());
    }

    @Test
    void aColumnTheExtractionWouldLeaveBlankIsSaid() {
        final String result = run(List.of(Map.of("name", "Organisation",
                "xpath", "EventSource/User/Organisation")));

        assertThat(result).contains("It can't be used as it is:\n- Column 'User' uses UserId, which the extraction "
                                    + "doesn't give");
    }

    @Test
    void aTablesOwnXsltWrittenByHandIsTriedWithTheFieldsAddedToIt() {
        final DocRef pipeline = new DocRef(PipelineDoc.TYPE, "p1", "Logon extraction");
        final DocRef xslt = new DocRef(XsltDoc.TYPE, "x1", "Logon extraction");
        fixture.add(pipeline, PipelineDoc.builder().uuid("p1").name("Logon extraction")
                .pipelineData(ExtractionPipelines.data(null, xslt)).build(), true);
        fixture.add(xslt, XsltDoc.builder().uuid("x1").name("Logon extraction").data(HAND_WRITTEN).build(), true);
        dashboard(TableSettings.builder()
                .columns(List.of(Column.builder().id("c1").name("User").expression("${UserId}").build()))
                .extractValues(true)
                .extractionPipeline(pipeline)
                .build());

        // Only the field to add: the User column's UserId is given by the XSLT already.
        final String result = run(List.of(Map.of("name", "Organisation", "xpath", "EventSource/User/Organisation")));

        assertThat(result)
                .contains("in the table's extraction pipeline 'Logon extraction'")
                .contains("Its XSLT was written by hand, so the fields given were added to it; it gives StreamId, "
                          + "EventId, UserId already.")
                .contains("| 7 | 2 | Operations |")
                .doesNotContain("can't be used as it is")
                .contains("it offers to change the pipeline in place, or to save a new one");
        final ArgumentCaptor<PipelineStepRequest> requests = ArgumentCaptor.forClass(PipelineStepRequest.class);
        Mockito.verify(steppingService, Mockito.atLeastOnce()).step(requests.capture());
        assertThat(requests.getAllValues().getLast().getCode().get(ExtractionPipelines.XSLT_ELEMENT))
                .contains("<data name=\"UserId\" value=\"{*:EventSource/*:User/*:Id}\"/>")
                .contains("<data name=\"Organisation\"");

        // A field it gives already isn't added again.
        assertThat(run(List.of(Map.of("name", "UserId", "xpath", "EventSource/User/Id"))))
                .contains("gives UserId already")
                .contains("Nothing was tried.");
    }

    @Test
    void steppingNeedsItsPermission() {
        Mockito.when(fixture.securityContext.hasAppPermission(AppPermission.STEPPING_PERMISSION)).thenReturn(false);

        assertThat(run(List.of(Map.of("name", "UserId", "xpath", "EventSource/User/Id"))))
                .contains("You don't have the Stepping permission");
        Mockito.verifyNoInteractions(steppingService);
    }

    @Test
    void unsafeXPathsAreRefusedBeforeAnythingIsStepped() {
        assertThat(run(List.of(Map.of("name", "X", "xpath", "doc('file:///etc/passwd')"))))
                .contains("its XPath uses doc()");
        Mockito.verifyNoInteractions(steppingService);
    }

    @Test
    void theSnapshotsEventsAreTakenOnceEachUpToTheLimit() {
        final List<stroom.ai.impl.extraction.ExtractionPreview.Sample> samples = PreviewExtractionTool.samples(
                new stroom.ai.impl.workspace.TableEngine.Result(List.of("User", "StreamId", "EventId"),
                        List.of(List.of("a", "7", "1"), List.of("b", "7", "1"), List.of("c", "x", "2"),
                                List.of("d", "8", "3"), List.of("e", "9", "4")), 5, 5), 2);

        assertThat(samples).extracting(sample -> List.of(sample.streamId(), sample.eventId()))
                .containsExactly(List.of(7L, 1L), List.of(8L, 3L));
        assertThat(samples.getFirst().columnValues()).containsExactly(Map.entry("User", "a"));
    }

    private void dashboard(final List<Column> columns) {
        dashboard(TableSettings.builder().columns(columns).build());
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
        final String arguments = JsonUtil.writeValueAsString(Map.of("table", "Events",
                "path", "/attachments/events.md", "fields", fields));
        return tool.execute(new ToolCall("c1", PreviewExtractionTool.NAME, arguments),
                ToolCalls.inChat(CHAT, fixture.workspace)).text();
    }

    // A step: the XSLT element's code run over the event at the step's location.
    private SteppingResult step(final PipelineStepRequest request) {
        final StepLocation location = request.getStepLocation();
        final String event = events.get(List.of(location.getMetaId(), location.getRecordIndex() + 1));
        final String code = request.getCode().get(ExtractionPipelines.XSLT_ELEMENT);
        final String output = event == null || code == null
                ? ""
                : transform(code, event);
        return new SteppingResult("s" + location.getMetaId(), null, null, location,
                new SharedStepData(null, Map.of(ExtractionPipelines.XSLT_ELEMENT,
                        new SharedElementData(event, output, null, false, false, true))),
                null, true, Set.of(), false, true);
    }

    private static String event(final long streamId,
                                final long eventId,
                                final String user,
                                final String organisation) {
        return "<Events xmlns=\"event-logging:3\"><Event StreamId=\"" + streamId + "\" EventId=\"" + eventId
               + "\"><EventSource><User><Id>" + user + "</Id>" + (organisation == null
                ? ""
                : "<Organisation>" + organisation + "</Organisation>") + "</User></EventSource></Event></Events>";
    }

    private static String transform(final String xslt, final String xml) {
        try {
            final Processor processor = new Processor(false);
            final XsltTransformer transformer = processor.newXsltCompiler()
                    .compile(new StreamSource(new StringReader(xslt)))
                    .load();
            transformer.setSource(new StreamSource(new StringReader(xml)));
            final StringWriter out = new StringWriter();
            transformer.setDestination(processor.newSerializer(out));
            transformer.transform();
            return out.toString();
        } catch (final SaxonApiException e) {
            throw new IllegalStateException(e);
        }
    }
}
