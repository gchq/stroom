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

import stroom.ai.impl.tools.AcceptedErrors;
import stroom.ai.impl.tools.ErrorTriage;
import stroom.ai.impl.tools.StreamSummaries;
import stroom.ai.impl.tools.ToolRefusal;
import stroom.ai.impl.tools.ToolResult;
import stroom.data.shared.StreamTypeNames;
import stroom.meta.shared.Meta;
import stroom.meta.shared.MetaRow;
import stroom.meta.shared.Status;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.isNull;

/// evaluate_pipeline on [SteppingWorld]'s pipeline ACME (`p-1`), built from template `Event Data (Text)`, whose own
/// XSLT `x` is on `translationFilter`. The streams it wrote, their summaries, the stepping and the event checks are
/// mocked; the error triage is the real one.
class TestEvaluatePipelineTool {

    private static final String EVENTS_XML = "<Events xmlns=\"event-logging:3\"><Event/></Events>";
    private static final List<String> EVENTS_SECTIONS = EvaluatePipelineTool.SECTIONS.get(
            EvaluatePipelineTool.EVENTS_CHECKS);

    private final SteppingWorld world = new SteppingWorld();
    private final CoverageReads coverageReads = Mockito.mock(CoverageReads.class);
    private final StreamSummaries summaries = Mockito.mock(StreamSummaries.class);
    private final StepSampleTool stepSample = Mockito.mock(StepSampleTool.class);
    private final CheckEventsTool checkEvents = Mockito.mock(CheckEventsTool.class);
    private final PipelineShapes pipelineShapes = Mockito.mock(PipelineShapes.class);
    private final IndexEvaluation indexEvaluation = Mockito.mock(IndexEvaluation.class);
    private final CefEvaluation cefEvaluation = Mockito.mock(CefEvaluation.class);
    private final ReferenceEvaluation referenceEvaluation = Mockito.mock(ReferenceEvaluation.class);

    TestEvaluatePipelineTool() {
        // Newest first: Error streams 21 and 20, Events streams 31 and 30, of raw streams 7 and 6.
        Mockito.when(coverageReads.newest(any(), Mockito.eq(StreamTypeNames.ERROR), anyInt()))
                .thenReturn(List.of(row(21, 7), row(20, 6)));
        Mockito.when(coverageReads.newest(any(), Mockito.eq(StreamTypeNames.EVENTS), anyInt()))
                .thenReturn(List.of(row(31, 7), row(30, 6)));
        Mockito.when(summaries.summariseErrorStreams(anyList())).thenAnswer(inv -> triaged());
        Mockito.when(stepSample.sampled(any(), any(), any(), anyList(), isNull(), any(), isNull(), anyBoolean()))
                .thenAnswer(inv -> new StepSampleTool.Sampled(stepped("clean", List.of()), List.of()));
        Mockito.when(pipelineShapes.shape(any())).thenReturn(shape("translation", null));
        Mockito.when(checkEvents.checkRead(anyList(), any(), anyInt())).thenReturn(checked());
        Mockito.when(summaries.summariseEvents(anyList(), anyInt())).thenReturn(profile());
    }

    @Test
    void everySectionIsGivenInOrderWithProcessingTriagedAndAcceptedErrorsBenign() {
        final Map<String, Object> result = evaluate("{\"pipeline_uuid\": \"p-1\"}");

        assertThat(result.keySet()).containsExactlyElementsOf(EVENTS_SECTIONS);

        final Map<String, Object> pipeline = map(result, "pipeline");
        assertThat(pipeline.get("name")).isEqualTo("ACME");
        assertThat(pipeline.get("templates")).isEqualTo(List.of(Map.of("type", "Pipeline", "uuid", "t", "name",
                "Event Data (Text)")));
        assertThat(list(pipeline, "translation_docs").getFirst().get("element")).isEqualTo("translationFilter");

        final Map<String, Object> processing = map(result, "processing");
        assertThat(processing.get("status")).isEqualTo("processed");
        assertThat(processing.get("events_streams")).isEqualTo(2);
        assertThat(processing.get("error_streams")).isEqualTo(2);
        assertThat(processing.get("error_streams_read")).isEqualTo(List.of(21, 20));
        assertThat(processing.get("verdict")).isEqualTo("blocking");
        assertThat(processing.get("markers_by_severity")).isEqualTo(Map.of("ERROR", 2, "WARNING", 5));
        final List<Map<String, Object>> groups = list(processing, "groups");
        assertThat(groups.getFirst()).containsEntry("class", "blocking").containsEntry("element", "schemaFilter")
                .containsEntry("count", 2);
        // The error the user accepted is reported as benign, saying so: not a problem.
        assertThat(groups).filteredOn(g -> Boolean.TRUE.equals(g.get("accepted")))
                .singleElement()
                .satisfies(g -> {
                    assertThat(g.get("class")).isEqualTo("benign");
                    assertThat((String) g.get("reason")).startsWith("Accepted as benign by the user");
                });

        final Map<String, Object> stepping = map(result, "stepping");
        assertThat(stepping.get("status")).isEqualTo("stepped");
        assertThat(stepping.get("verdict")).isEqualTo("clean");
        // The raw streams of the newest output.
        assertThat(stepping.get("streams")).isEqualTo(List.of(7, 6));
        assertThat(stepping.get("streams_from")).isEqualTo("the pipeline's newest inputs");
        Mockito.verify(stepSample).sampled(any(), any(), Mockito.eq("p-1"), Mockito.eq(List.of(7L, 6L)), isNull(),
                isNull(), isNull(), Mockito.eq(false));

        final Map<String, Object> events = map(result, "events");
        assertThat(events.get("status")).isEqualTo("checked");
        assertThat(events.get("stream_id")).isEqualTo(31);
        assertThat(events.get("ok")).isEqualTo(true);
        assertThat(events.get("events_profiled")).isEqualTo(2);
        // Least filled first.
        assertThat(map(events, "field_population").keySet()).containsExactly("EventSource/Device/IPAddress",
                "EventDetail/TypeId", "EventSource/User/Id");
        Mockito.verify(checkEvents).checkRead(List.of(31L), null, EvaluatePipelineTool.EVENTS);

        final Map<String, Object> coverage = map(result, "coverage");
        assertThat(coverage.get("status")).isEqualTo("records_missed");
        assertThat(coverage.get("records_no_rule_matched")).isEqualTo(3);
        assertThat(coverage.get("records_kept_as_unknown")).isEqualTo(1);
        assertThat(coverage.get("rules_keeping_unknown")).isEqualTo(List.of("other"));
        assertThat((String) coverage.get("next")).contains("review_coverage pipeline_uuid=p-1")
                .contains("don't run it");

        final Map<String, Object> report = map(result, "report");
        assertThat((String) report.get("outline")).startsWith("# ACME - Evaluation\n")
                .contains("## Summary", "## Processing errors", "## Stepping", "## Event validity", "## Coverage",
                        "## Recommendations");
        assertThat((String) report.get("next")).contains("show it to the user")
                .contains("write_documentation", "pipeline_uuid=p-1 evaluation=true");

        // The streams read are audited, as summarise_streams and check_events' own reads are.
        Mockito.verify(world.auditLog).streamsViewed(any(), Mockito.eq(List.of(21L, 20L)), any());
        Mockito.verify(world.auditLog).streamsViewed(any(), Mockito.eq(List.of(31L)), any());
        assertThat(world.said).contains("Evaluating ACME: reading its newest Error streams",
                "Evaluating ACME: stepping 2 input stream(s)",
                "Evaluating ACME: the checks for its stage (translation)");
    }

    @Test
    void withoutViewDataTheStreamSectionsSaySoAndTheRestIsGiven() {
        Mockito.when(world.documentAccess.canViewData()).thenReturn(false);

        final Map<String, Object> result = evaluate("{\"pipeline_uuid\": \"p-1\"}");

        assertThat(result.keySet()).containsExactlyElementsOf(EVENTS_SECTIONS);
        for (final String section : List.of("processing", "events", "coverage")) {
            assertThat(map(result, section)).as(section).containsEntry("status", "unavailable");
            assertThat((String) map(result, section).get("reason")).as(section).contains("View Data");
        }
        assertThat(map(result, "stepping").get("status")).isEqualTo("not_stepped");
        assertThat((String) map(result, "stepping").get("reason")).contains("View Data").endsWith("Give stream_ids "
                + "(the input streams it reads) to step.");
        assertThat(map(result, "pipeline").get("name")).isEqualTo("ACME");
        assertThat(map(result, "report").get("outline")).isNotNull();
        Mockito.verifyNoInteractions(coverageReads, summaries, stepSample, checkEvents);

        // Streams given are still stepped: stepping needs only Stepping.
        final Map<String, Object> given = evaluate("{\"pipeline_uuid\": \"p-1\", \"stream_ids\": [4]}");
        assertThat(map(given, "stepping").get("status")).isEqualTo("stepped");
        assertThat(map(given, "stepping").get("streams_from")).isEqualTo("stream_ids");
    }

    @Test
    void withoutSteppingTheSteppingSectionSaysSoAndTheRestIsGiven() {
        Mockito.when(stepSample.sampled(any(), any(), any(), anyList(), any(), any(), any(), anyBoolean()))
                .thenThrow(new ToolRefusal("You don't have permission to step pipelines (Stepping), so nothing can "
                                           + "be stepped."));

        final Map<String, Object> result = evaluate("{\"pipeline_uuid\": \"p-1\"}");

        assertThat(result.keySet()).containsExactlyElementsOf(EVENTS_SECTIONS);
        assertThat(map(result, "stepping")).containsEntry("status", "unavailable")
                .containsEntry("reason", "You don't have permission to step pipelines (Stepping), so nothing can "
                                         + "be stepped.")
                .containsEntry("streams", List.of(7, 6));
        assertThat(map(result, "processing").get("status")).isEqualTo("processed");
        assertThat(map(result, "events").get("status")).isEqualTo("checked");
    }

    @Test
    void aPipelineThatNeverProcessedIsSteppedOnlyOnStreamsGivenAndItsStepCheckedForEvents() {
        Mockito.when(coverageReads.newest(any(), any(), anyInt())).thenReturn(List.of());

        final Map<String, Object> none = evaluate("{\"pipeline_uuid\": \"p-1\"}");

        assertThat(map(none, "processing").get("status")).isEqualTo("never_processed");
        assertThat(map(none, "stepping").get("status")).isEqualTo("not_stepped");
        assertThat((String) map(none, "stepping").get("reason")).contains("Give stream_ids");
        assertThat(map(none, "events").get("status")).isEqualTo("not_checked");
        assertThat(map(none, "coverage").get("status")).isEqualTo("not_checked");
        Mockito.verifyNoInteractions(stepSample);

        // Given streams, the first record stepped is checked for its Events instead.
        final Map<String, Object> blocking = stepped("blocking", List.of(group("blocking", "schemaFilter",
                "ERROR", "Output fails schema validation", "The value 'n/a' of element 'IPAddress' is not valid.")));
        blocking.put("first_record_output", Map.of("translationFilter", EVENTS_XML));
        Mockito.when(stepSample.sampled(any(), any(), any(), anyList(), isNull(), any(), isNull(), anyBoolean()))
                .thenReturn(new StepSampleTool.Sampled(blocking, List.of()));
        Mockito.when(checkEvents.check(EVENTS_XML, "4.1.0")).thenReturn(checked());

        final Map<String, Object> result = evaluate("{\"pipeline_uuid\": \"p-1\", \"stream_ids\": [4, 5], "
                                                    + "\"schema_version\": \"4.1.0\"}");

        final Map<String, Object> stepping = map(result, "stepping");
        assertThat(stepping.get("verdict")).isEqualTo("blocking");
        assertThat(stepping.get("streams")).isEqualTo(List.of(4, 5));
        assertThat(list(stepping, "groups").getFirst()).containsEntry("element", "schemaFilter")
                .containsEntry("example", "The value 'n/a' of element 'IPAddress' is not valid.")
                .containsEntry("records", List.of("4:0"));
        final Map<String, Object> events = map(result, "events");
        assertThat(events.get("status")).isEqualTo("checked");
        assertThat((String) events.get("source")).startsWith("the first record stepped");
        assertThat((String) events.get("field_population")).startsWith("Not profiled");
    }

    @Test
    void maxRecordsIsBoundedAsStepSamplesAndBadArgumentsAreRefused() {
        evaluate("{\"pipeline_uuid\": \"p-1\", \"stream_ids\": [4], \"max_records\": 40}");
        Mockito.verify(stepSample).sampled(any(), any(), Mockito.eq("p-1"), Mockito.eq(List.of(4L)), isNull(),
                Mockito.eq(40), isNull(), Mockito.eq(false));
        evaluate("{\"pipeline_uuid\": \"p-1\", \"stream_ids\": [4], \"max_records\": 9000}");
        Mockito.verify(stepSample).sampled(any(), any(), Mockito.eq("p-1"), Mockito.eq(List.of(4L)), isNull(),
                Mockito.eq(PipelineStepping.MAX_SAMPLE_RECORDS), isNull(), Mockito.eq(false));

        assertThat(refused("{}")).isEqualTo("Give pipeline_uuid: the pipeline to evaluate.");
        assertThat(refused("{\"pipeline_uuid\": \"p-1\", \"max_records\": 0}"))
                .isEqualTo("max_records must be a whole number of at least 1, not '0'");
        assertThat(refused("{\"pipeline_uuid\": \"p-1\", \"stream_ids\": [\"x\"]}"))
                .isEqualTo("stream_ids are stream ids (numbers): 'x' is not one");
        assertThat(refused("{\"pipeline_uuid\": \"nope\"}"))
                .startsWith("Pipeline nope can't be read: it doesn't exist");
    }

    @Test
    void eachStageGetsItsOwnChecksAndOutline() {
        assertThat(EvaluatePipelineTool.checks("translation")).isEqualTo(EvaluatePipelineTool.EVENTS_CHECKS);
        assertThat(EvaluatePipelineTool.checks("indexing")).isEqualTo(EvaluatePipelineTool.INDEX_CHECKS);
        assertThat(EvaluatePipelineTool.checks("discovery")).isEqualTo(EvaluatePipelineTool.INDEX_CHECKS);
        assertThat(EvaluatePipelineTool.checks("forwarding")).isEqualTo(EvaluatePipelineTool.CEF_CHECKS);
        assertThat(EvaluatePipelineTool.checks("reference")).isEqualTo(EvaluatePipelineTool.REFERENCE_CHECKS);
        for (final String stage : List.of("records", "loader", "other")) {
            assertThat(EvaluatePipelineTool.checks(stage)).as(stage).isEqualTo(EvaluatePipelineTool.GENERAL);
        }
        assertThat(EvaluatePipelineTool.checks(null)).isEqualTo(EvaluatePipelineTool.GENERAL);

        final Map<String, String> headings = Map.of(EvaluatePipelineTool.EVENTS_CHECKS, "## Event validity",
                EvaluatePipelineTool.INDEX_CHECKS, "## Index fields", EvaluatePipelineTool.CEF_CHECKS,
                "## CEF output", EvaluatePipelineTool.REFERENCE_CHECKS, "## Reference maps");
        headings.forEach((checks, heading) -> assertThat(EvaluatePipelineTool.outline(checks)).as(checks)
                .containsKey(heading));
        assertThat(EvaluatePipelineTool.outline(EvaluatePipelineTool.GENERAL).keySet()).containsExactly(
                "## Summary", "## Pipeline", "## Processing errors", "## Stepping", "## Recommendations");
        assertThat(EvaluatePipelineTool.outline(EvaluatePipelineTool.INDEX_CHECKS))
                .doesNotContainKeys("## Event validity", "## Coverage");
    }

    @Test
    void anIndexingPipelineHasItsIndexCheckedFromTheDocumentsStepped() {
        Mockito.when(pipelineShapes.shape(any())).thenReturn(shape("indexing", "lucene"));
        Mockito.when(stepSample.sampled(any(), any(), any(), anyList(), isNull(), any(), isNull(), anyBoolean()))
                .thenReturn(new StepSampleTool.Sampled(stepped("clean", List.of()), List.of("<records/>")));
        Mockito.when(indexEvaluation.index(any(), any(), any(), any())).thenReturn(Map.of("status", "checked"));

        final Map<String, Object> result = evaluate("{\"pipeline_uuid\": \"p-1\"}");

        assertThat(result.keySet()).containsExactlyElementsOf(EvaluatePipelineTool.SECTIONS.get(
                EvaluatePipelineTool.INDEX_CHECKS));
        assertThat(map(result, "pipeline")).containsEntry("stage", "indexing").containsEntry("backend", "lucene");
        assertThat(map(result, "index")).containsEntry("status", "checked");
        assertThat((String) map(result, "report").get("outline")).contains("## Index fields")
                .doesNotContain("## Event validity");
        // Each record's output is kept, for the documents it writes.
        Mockito.verify(stepSample).sampled(any(), any(), Mockito.eq("p-1"), Mockito.eq(List.of(7L, 6L)), isNull(),
                isNull(), isNull(), Mockito.eq(true));
        Mockito.verify(indexEvaluation).index(any(), any(), Mockito.eq(List.of("<records/>")), any());
        Mockito.verifyNoInteractions(checkEvents);
    }

    @Test
    void anUnprocessedPipelineIsSteppedOverTheNewestInputsOfTheFeedsItsFiltersRead() {
        // A CEF pipeline that has written nothing yet: its filter's feed's newest Events streams.
        Mockito.when(pipelineShapes.shape(any())).thenReturn(shape("forwarding", "kafka"));
        Mockito.when(coverageReads.newest(any(), any(), anyInt())).thenReturn(List.of());
        Mockito.when(referenceEvaluation.feeds("p-1")).thenReturn(Set.of("ACME-EVENTS"));
        Mockito.when(coverageReads.newestOnFeed("ACME-EVENTS", StreamTypeNames.EVENTS,
                EvaluatePipelineTool.STEP_STREAMS)).thenReturn(List.of(41L, 40L));
        Mockito.when(cefEvaluation.cef(any(), any(), anyList(), any())).thenReturn(Map.of("status", "checked"));

        final Map<String, Object> result = evaluate("{\"pipeline_uuid\": \"p-1\"}");

        assertThat(result.keySet()).containsExactlyElementsOf(EvaluatePipelineTool.SECTIONS.get(
                EvaluatePipelineTool.CEF_CHECKS));
        // Not 'never processed': a pipeline that writes no Events can't tell that from processing without errors.
        assertThat(map(result, "processing").get("status")).isEqualTo("no_error_streams");
        assertThat(map(result, "stepping").get("streams")).isEqualTo(List.of(41, 40));
        Mockito.verify(cefEvaluation).cef(any(), Mockito.eq("p-1"), Mockito.eq(List.of(41L, 40L)),
                Mockito.eq("kafka"));
        assertThat((String) map(result, "report").get("outline")).contains("## CEF output");
    }

    @Test
    void aReferencePipelineHasItsMapsCheckedAndAnyOtherNoChecksOfItsOwn() {
        Mockito.when(pipelineShapes.shape(any())).thenReturn(shape("reference", null));
        Mockito.when(referenceEvaluation.reference(any(), any(), any())).thenReturn(Map.of("status", "checked"));

        final Map<String, Object> reference = evaluate("{\"pipeline_uuid\": \"p-1\"}");

        assertThat(reference.keySet()).containsExactlyElementsOf(EvaluatePipelineTool.SECTIONS.get(
                EvaluatePipelineTool.REFERENCE_CHECKS));
        Mockito.verify(referenceEvaluation).reference(any(), Mockito.eq("p-1"), Mockito.eq(List.of()));

        Mockito.when(pipelineShapes.shape(any())).thenReturn(shape("loader", null));
        final Map<String, Object> loader = evaluate("{\"pipeline_uuid\": \"p-1\"}");

        assertThat(loader.keySet()).containsExactlyElementsOf(EvaluatePipelineTool.SECTIONS.get(
                EvaluatePipelineTool.GENERAL));
        assertThat(map(loader, "checks")).containsEntry("status", "not_applicable");
        assertThat((String) map(loader, "checks").get("reason")).contains("stage 'loader'");
        assertThat(map(loader, "processing").get("status")).isEqualTo("processed");
    }

    @Test
    void theFieldPopulationIsKeptShortLeastFilledFirst() {
        final Map<String, Double> paths = new LinkedHashMap<>();
        for (int i = 0; i < EvaluatePipelineTool.POPULATION_PATHS + 5; i++) {
            paths.put("Event/Path" + i, 100.0 - i);
        }
        final Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("events", 100);
        summary.put("path_population", paths);

        final Map<String, Object> shown = EvaluatePipelineTool.population(summary);

        assertThat(map(shown, "field_population")).hasSize(EvaluatePipelineTool.POPULATION_PATHS);
        assertThat(map(shown, "field_population").keySet().iterator().next()).isEqualTo("Event/Path44");
        assertThat((String) shown.get("field_population_note")).startsWith("5 more paths, each in at least 96.0%");
    }

    // The newest Error streams' markers triaged as StreamSummaries does: a schema error in both, records logged as
    // matching no rule or kept as Unknown, and a lookup warning the user accepted as benign.
    private static Map<String, Object> triaged() {
        final List<ErrorTriage.Marker> markers = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            markers.add(marker("ERROR", "schemaFilter", "The value 'n/a' of element 'IPAddress' is not valid."));
        }
        for (int i = 0; i < 3; i++) {
            markers.add(marker("WARNING", "translationFilter", "Log - No event mapping matched record " + i
                                                                + " (action=view)"));
        }
        markers.add(marker("WARNING", "translationFilter", "Log - Kept as Unknown by rule 'other': record 4 "
                                                           + "(action=export)"));
        markers.add(marker("WARNING", "decorationFilter", "No HR record for user svc-backup"));
        final List<AcceptedErrors.Entry> accepted = List.of(new AcceptedErrors.Entry("decorationFilter",
                "No HR record for user svc-*", "No HR record for user svc-backup", "Service accounts have none",
                "2026-10-01"));
        final Map<String, Object> result = new LinkedHashMap<>();
        result.put("error_streams", List.of(21L, 20L));
        result.putAll(ErrorTriage.bundled().triage(markers, Set.of("translationFilter"), null, 3, accepted));
        return result;
    }

    private static ErrorTriage.Marker marker(final String severity, final String element, final String message) {
        return new ErrorTriage.Marker(severity, element, message, null, null, null);
    }

    private static Map<String, Object> stepped(final String verdict, final List<Map<String, Object>> groups) {
        final Map<String, Object> result = new LinkedHashMap<>();
        result.put("pipeline", "ACME");
        result.put("records_stepped", 6);
        result.put("records_with_errors", groups.isEmpty()
                ? 0
                : 1);
        result.put("verdict", verdict);
        result.put("groups_by_class", Map.of("blocking", groups.size(), "review", 0, "benign", 0));
        result.put("groups", groups);
        result.put("first_record_output", Map.of("translationFilter", EVENTS_XML));
        return result;
    }

    private static Map<String, Object> group(final String kind,
                                             final String element,
                                             final String severity,
                                             final String reason,
                                             final String message) {
        final Map<String, Object> group = new LinkedHashMap<>();
        group.put("class", kind);
        group.put("reason", reason);
        group.put("severity", severity);
        group.put("element", element);
        group.put("count", 1);
        group.put("records", List.of("4:0"));
        group.put("examples", List.of(Map.of("message", message)));
        return group;
    }

    private static Map<String, Object> checked() {
        final Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("valid", true);
        schema.put("schema", "file://event-logging-v4.1.0.xsd");
        schema.put("error_count", 0);
        schema.put("errors", List.of());
        final Map<String, Object> quality = new LinkedHashMap<>();
        quality.put("events_checked", 2);
        quality.put("ok", true);
        quality.put("rules", Map.of());
        final Map<String, Object> result = new LinkedHashMap<>();
        result.put("ok", true);
        result.put("schema", schema);
        result.put("quality", quality);
        return result;
    }

    private static Map<String, Object> profile() {
        final Map<String, Object> result = new LinkedHashMap<>();
        result.put("events", 2);
        result.put("event_types", Map.of("Authenticate", 2));
        result.put("type_ids", Map.of("Logon", 2));
        result.put("path_population", Map.of("EventSource/User/Id", 100.0, "EventDetail/TypeId", 100.0,
                "EventSource/Device/IPAddress", 50.0));
        result.put(StreamSummaries.VALUES_BY_PATH, Map.of("EventSource/User/Id", List.of("alice", "bob")));
        return result;
    }

    static PipelineShapes.Shape shape(final String stage, final String backend) {
        return new PipelineShapes.Shape(stage, backend, List.of(), null, List.of(), List.of(), List.of(), Map.of());
    }

    private static MetaRow row(final long id, final long parent) {
        return new MetaRow(Meta.builder().id(id).parentDataId(parent).status(Status.UNLOCKED).build(), null,
                Map.of());
    }

    private Map<String, Object> evaluate(final String arguments) {
        final ToolResult result = tool().execute(BuildToolCalls.call(EvaluatePipelineTool.NAME, arguments),
                world.context());
        assertThat(result.isError()).as(result.text()).isFalse();
        return BuildToolCalls.json(result.text());
    }

    private String refused(final String arguments) {
        final ToolResult result = tool().execute(BuildToolCalls.call(EvaluatePipelineTool.NAME, arguments),
                world.context());
        assertThat(result.isError()).as(result.text()).isTrue();
        return result.text();
    }

    private EvaluatePipelineTool tool() {
        return new EvaluatePipelineTool(world.documentAccess, pipelineShapes, coverageReads, summaries, stepSample,
                checkEvents, indexEvaluation, cefEvaluation, referenceEvaluation, world.auditLog);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(final Map<String, Object> result, final String key) {
        return (Map<String, Object>) result.get(key);
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> list(final Map<String, Object> result, final String key) {
        return (List<Map<String, Object>>) result.get(key);
    }
}
