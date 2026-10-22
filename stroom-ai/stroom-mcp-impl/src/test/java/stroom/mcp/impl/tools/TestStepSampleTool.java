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

import stroom.ai.impl.tools.KeptMapping;
import stroom.ai.impl.tools.StreamReader;
import stroom.ai.impl.tools.ToolCallContext;
import stroom.ai.impl.tools.ToolRefusal;
import stroom.ai.impl.tools.ToolResult;
import stroom.meta.shared.Meta;
import stroom.meta.shared.MetaRow;
import stroom.meta.shared.Status;
import stroom.pipeline.shared.data.PipelineDataBuilder;
import stroom.pipeline.shared.data.PipelineElement;
import stroom.pipeline.shared.data.PipelineProperty;
import stroom.pipeline.shared.data.PipelinePropertyValue;
import stroom.security.shared.AppPermission;
import stroom.util.shared.ResultPage;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;

/// step_sample (the Stroom MCP server's `tests/test_stepping.py`, and its stepping cases in
/// `tests/test_build_checks.py`, `tests/test_progress.py`, `tests/test_token_time.py` and
/// `tests/test_authoring_guards.py`): each case was seen in testing, as its comment says.
class TestStepSampleTool {

    private static final String NO_EVENT = "No record produced an Event";
    private static final String EMPTY_EVENTS = "<Events xmlns=\"event-logging:3\" Version=\"4.1.0\"/>";
    private static final String AN_EVENT = "<Events xmlns=\"event-logging:3\"><Event/></Events>";

    private final SteppingWorld world = new SteppingWorld();

    @Test
    void aStreamIsWalkedInOneSessionWhichIsEnded() {
        // Seen: a fresh session a step started a capture of the whole 52 MB stream for each of 500 steps, all at
        // once.
        world.stepper = (stream, at) -> switch ((int) at.getRecordIndex()) {
            case 0 -> SteppingWorld.step(stream, 0, 0, "<Events>0</Events>");
            case 1 -> SteppingWorld.step(stream, 0, 1, "<Events>1</Events>", "bad TimeCreated");
            default -> null;
        };

        final Map<String, Object> result = stepSample("{\"pipeline_uuid\": \"p-1\", \"stream_ids\": [7]}");

        assertThat(world.opened).extracting(Map.Entry::getKey).containsExactly(7L);
        assertThat(world.steps).containsExactly("FIRST", "FORWARD 0", "FORWARD 1");
        Mockito.verify(world.walks.getFirst()).close();
        assertThat(List.of(result.get("records_stepped"), result.get("records_with_errors"), result.get("verdict")))
                .containsExactly(2, 1, "blocking");
        // These outputs have no Event: that is blocking too (the record templates select nothing), besides the
        // error.
        assertThat(groups(result).get(0).get("reason")).isEqualTo(NO_EVENT);
        assertThat(groups(result).get(1).get("records")).isEqualTo(List.of("7:1"));
        assertThat(result.get("first_record_output")).isEqualTo(Map.of("translationFilter", "<Events>0</Events>"));
    }

    @Test
    void eventsWithNoEventInAnyRecordAreBlockingAndOneEventIsEnough() {
        // Seen: XML fragments read with no namespace where the wrapper gives them records:2: every record came out
        // as an empty Events, and stepping said clean.
        world.records(2, EMPTY_EVENTS);
        final Map<String, Object> result = stepSample("{\"pipeline_uuid\": \"p-1\", \"stream_ids\": [7]}");
        assertThat(result.get("verdict")).isEqualTo("blocking");
        assertThat(groups(result).getFirst().get("reason")).isEqualTo(NO_EVENT);
        assertThat(firstMessage(groups(result).getFirst())).contains("xml_namespace");

        world.stepper = (stream, at) -> switch ((int) at.getRecordIndex()) {
            case 0 -> SteppingWorld.step(stream, 0, 0, EMPTY_EVENTS);
            case 1 -> SteppingWorld.step(stream, 0, 1, AN_EVENT);
            default -> null;
        };
        final Map<String, Object> dropped = stepSample("{\"pipeline_uuid\": \"p-1\", \"stream_ids\": [7]}");
        // A dropped record is fine.
        assertThat(groups(dropped)).extracting(group -> group.get("reason")).doesNotContain(NO_EVENT);
    }

    @Test
    void referenceDataWithNoReferenceInAnyRecordIsBlocking() {
        // Seen: a directory's header read as a record (columns col1 ...), the key found nowhere: an empty
        // referenceData, no Reference stream, and stepping said clean.
        world.records(2, "<referenceData xmlns=\"reference-data:2\" version=\"2.0.1\"/>");

        final Map<String, Object> result = stepSample("{\"pipeline_uuid\": \"p-1\", \"stream_ids\": [7]}");

        assertThat(result.get("verdict")).isEqualTo("blocking");
        assertThat(groups(result).getFirst().get("reason")).isEqualTo("No record produced reference data");
        assertThat(firstMessage(groups(result).getFirst())).contains("col1, col2");
    }

    @Test
    void nothingSteppedIsNeverClean() {
        // An XSLT that doesn't compile: Stroom finds no record and reports the error on the element.
        world.stepper = (stream, at) -> SteppingWorld.none("XsltPool - Variable user has not been declared");
        final Map<String, Object> result = stepSample("{\"pipeline_uuid\": \"p-1\", \"stream_ids\": [7]}");
        assertThat(List.of(result.get("records_stepped"), result.get("verdict"))).containsExactly(0, "blocking");
        assertThat(firstMessage(groups(result).getFirst())).contains("Variable user has not been declared");
        assertThat(String.valueOf(result.get("hint"))).contains("before its first record");

        // No record and no error (an empty stream, a parser that reads nothing): still not clean.
        world.stepper = (stream, at) -> null;
        final Map<String, Object> empty = stepSample("{\"pipeline_uuid\": \"p-1\", \"stream_ids\": [7]}");
        assertThat(empty.get("verdict")).isEqualTo("blocking");
        assertThat(String.valueOf(empty.get("hint"))).contains("found none in streams [7]");
    }

    @Test
    void unknownFromAnXsltSavedWithoutAMappingBlocks() {
        // As an agent did: TRAFFIC records written as an empty EventDetail/Unknown by a hand-written XSLT, past
        // every check build_translation_xslt makes, then processed and documented.
        unknownThenAnEvent();
        Mockito.when(world.guard.tags(any())).thenReturn(Set.of(WriteGuard.MANAGED));

        final Map<String, Object> result = stepSample("{\"pipeline_uuid\": \"p-1\", \"stream_ids\": [7]}");

        final Map<String, Object> group = groups(result).getFirst();
        assertThat(result.get("verdict")).isEqualTo("blocking");
        assertThat(group.get("records")).isEqualTo(List.of("7:0"));
        assertThat(firstMessage(group)).startsWith("1 of 2 records come out as EventDetail/Unknown (e.g. 7:0; "
                                                   + "they hold TypeId: TRAFFIC)")
                .contains("build_translation_xslt");
    }

    @Test
    void unknownAgreedToOrSomeoneElsesDesignIsClean() {
        // Saved from a mapping, Unknown was checked and agreed there; a working copy of a production pipeline, or
        // a pipeline outside a build, is someone else's design.
        unknownThenAnEvent();
        Mockito.when(world.guard.tags(any())).thenReturn(Set.of(WriteGuard.MANAGED));
        Mockito.when(world.keptMappings.keptMapping(anyString())).thenReturn(Optional.of(
                new KeptMapping.Kept("translation", Map.of())));
        assertThat(stepSample("{\"pipeline_uuid\": \"p-1\", \"stream_ids\": [7]}").get("verdict"))
                .isEqualTo("clean");

        Mockito.when(world.keptMappings.keptMapping(anyString())).thenReturn(Optional.empty());
        Mockito.when(world.guard.copyOf(any())).thenReturn(Optional.of("p-9"));
        assertThat(stepSample("{\"pipeline_uuid\": \"p-1\", \"stream_ids\": [7]}").get("verdict"))
                .isEqualTo("clean");

        Mockito.when(world.guard.copyOf(any())).thenReturn(Optional.empty());
        Mockito.when(world.guard.tags(any())).thenReturn(Set.of());
        assertThat(stepSample("{\"pipeline_uuid\": \"p-1\", \"stream_ids\": [7]}").get("verdict"))
                .isEqualTo("clean");
    }

    @Test
    void largeStreamsAreSteppedFromTheirHead() {
        world.records(3, AN_EVENT);

        final Map<String, Object> result = PortedToolCalls.json(new StepSampleTool(world.stepping(), 2, 500)
                .execute(PortedToolCalls.call(StepSampleTool.NAME, "{\"pipeline_uuid\": \"p-1\", "
                                                                   + "\"stream_ids\": [7]}"), world.context())
                .text());

        assertThat(result.get("records_stepped")).isEqualTo(2);
        assertThat(String.valueOf(result.get("hint"))).startsWith("Stepped the first 2 records of each stream");
    }

    @Test
    void moreRecordsThanTheLimitSaysTheLimitAndHowTheRestAreChecked() {
        // Seen in production: max_records=1000 stepped 500, said only "the sample has more", and the agent called it
        // again the same way: three more minutes for the same records.
        world.records(4, AN_EVENT);

        final Map<String, Object> result = PortedToolCalls.json(new StepSampleTool(world.stepping(), 50, 3)
                .execute(PortedToolCalls.call(StepSampleTool.NAME, "{\"pipeline_uuid\": \"p-1\", "
                                                                   + "\"stream_ids\": [7], \"max_records\": 1000}"),
                        world.context())
                .text());

        assertThat(result.get("records_stepped")).isEqualTo(3);
        assertThat(String.valueOf(result.get("hint")))
                .contains("the server's limit for one step_sample (max_records=1000 is more than it steps)")
                .contains("calling it again steps the same 3")
                .contains("wait_for_processing");
    }

    @Test
    void steppingWaitsForTheReferenceDataAPipelineLooksUp() {
        // Eval case 15 on Haiku: stepped before the directory was processed; Stroom kept "no reference data" for 10
        // minutes, and every later step failed until the agent gave up on reference data.
        world.reference("ACME-USERS");
        world.records(1, AN_EVENT);
        Mockito.when(world.streamReader.find(any(), anyInt(), anyBoolean())).thenReturn(new ResultPage<>(List.of(
                new MetaRow(Meta.builder().id(9).status(Status.DELETED).build(), null, Map.of()))));

        final ToolResult refused = call("{\"pipeline_uuid\": \"p-1\", \"stream_ids\": [7]}", world.context());

        assertThat(refused.isError()).isTrue();
        assertThat(refused.text()).contains("ACME-USERS (Reference), which has no stream of that type yet");
        assertThat(world.steps).isEmpty();

        Mockito.when(world.streamReader.find(any(), anyInt(), anyBoolean())).thenReturn(new ResultPage<>(List.of(
                new MetaRow(Meta.builder().id(10).status(Status.UNLOCKED).build(), null, Map.of()))));
        assertThat(call("{\"pipeline_uuid\": \"p-1\", \"stream_ids\": [7]}", world.context()).isError()).isFalse();
    }

    @Test
    void steppingRefusesAPipelineWithAPropertyStroomCantTake() {
        // Seen: addRootObject given as "false" and written {'string': 'false'}; every step of the pipeline then
        // outlasted Stroom's wait, on any stream.
        world.pipeline("p-2", "FortiOS-Events", null, new PipelineDataBuilder()
                .addElement(new PipelineElement("jsonParser", "JSONParser"))
                .addProperty(new PipelineProperty("jsonParser", "addRootObject", new PipelinePropertyValue("false"))));
        final SteppedPipeline mistyped = SteppedPipeline.of(world.stepping().load("p-2").merged(),
                (type, name) -> "addRootObject".equals(name)
                        ? "boolean"
                        : null);

        assertThatThrownBy(mistyped::refuseMistyped)
                .isInstanceOf(ToolRefusal.class)
                .hasMessageContaining("jsonParser.addRootObject is 'false', where Stroom takes true or false")
                .hasMessageContaining("update_pipeline");

        world.pipeline("p-3", "FortiOS-Events", null, new PipelineDataBuilder()
                .addElement(new PipelineElement("jsonParser", "JSONParser"))
                .addProperty(new PipelineProperty("jsonParser", "addRootObject", new PipelinePropertyValue(false))));
        // Written right: steps.
        SteppedPipeline.of(world.stepping().load("p-3").merged(), (type, name) -> "boolean").refuseMistyped();
    }

    @Test
    void aJsonArrayReadWithTheRootMapIsOneRecordAndSaysSo() {
        // Seen: four 755 KB arrays each stepped as one record of 985 events, every step outlasting Stroom's wait.
        world.pipeline("p-2", "JSON", null, new PipelineDataBuilder()
                .addElement(new PipelineElement("jsonParser", "JSONParser"))
                .addElement(new PipelineElement("translationFilter", "XSLTFilter")));
        assertThat(world.stepping().load("p-2").jsonRootMap()).isTrue();
        world.pipeline("p-3", "JSON", null, new PipelineDataBuilder()
                .addElement(new PipelineElement("jsonParser", "JSONParser"))
                .addElement(new PipelineElement("translationFilter", "XSLTFilter"))
                .addProperty(new PipelineProperty("jsonParser", "addRootObject", new PipelinePropertyValue(false))));
        assertThat(world.stepping().load("p-3").jsonRootMap()).isFalse();

        world.records(1, AN_EVENT);
        Mockito.when(world.streamReader.readHead(Mockito.anyLong(), anyInt(), anyInt())).thenReturn(
                new StreamReader.RawHead("[{\"a\": 1}, {\"a\": 2}]", false, 1));
        final Map<String, Object> result = stepSample("{\"pipeline_uuid\": \"p-2\", \"stream_ids\": [7]}");
        assertThat(result.get("verdict")).isEqualTo("review");
        assertThat(groups(result).getFirst().get("reason")).isEqualTo("Each JSON array stepped as one record");
        assertThat(groups(result).getFirst().get("element")).isEqualTo("jsonParser");
    }

    @Test
    void stepSampleSaysWhenEachStreamStartsAndOnlyNowAndThenWhileSteppingIt() {
        // Asked for by the user: not an update for each and every record stepped.
        world.records(7, AN_EVENT);

        final Map<String, Object> result = stepSample("{\"pipeline_uuid\": \"p-1\", \"stream_ids\": [7, 8]}");

        assertThat(result.get("records_stepped")).isEqualTo(14);
        // Each step is quick here, so nothing is said while a stream is stepped.
        assertThat(world.said).containsExactly("Stepping stream 7 (1 of 2) through ACME: up to 50 records",
                "Stepping stream 8 (2 of 2) through ACME: up to 50 records");
    }

    @Test
    void stepSampleStopsBeforeTheTimeLimitRunsOutAndRecordsNoCleanStep() {
        // Seen with Stroom's internal identity provider (a token for the MCP server lives 5 minutes): a call that
        // outlasted its time failed part way. Here the session's time for a call leaves none for stepping once the
        // margin is kept: the first record is stepped, then it stops.
        world.records(4, AN_EVENT);
        final ToolCallContext context = world.contextBuilder()
                .limits(SteppingWorld.queryTimeLimit(PipelineStepping.MARGIN))
                .build();

        final Map<String, Object> result = PortedToolCalls.json(call("{\"pipeline_uuid\": \"p-1\", "
                                                                     + "\"stream_ids\": [7]}", context).text());

        assertThat(result.get("records_stepped")).isEqualTo(1);
        assertThat(result.get("stopped_early")).isEqualTo(true);
        assertThat(String.valueOf(result.get("hint"))).startsWith("Stopped after 1 records before this session's "
                                                                  + "time limit for a call (10 s) ran out. Call "
                                                                  + "again");
        Mockito.verify(world.buildRecords, Mockito.never()).recordPassed(any(), anyString(), any(), any());
    }

    @Test
    void aCallTheClientGaveUpOnStopsAtOnce() {
        // StroomMcpServer interrupts the thread of a call its client cancels.
        world.stepper = (stream, at) -> {
            if (at.getRecordIndex() == 1) {
                Thread.currentThread().interrupt();
            }
            return SteppingWorld.step(stream, 0, at.getRecordIndex(), AN_EVENT);
        };

        final ToolResult result = call("{\"pipeline_uuid\": \"p-1\", \"stream_ids\": [7]}", world.context());

        // Cleared, as the thread is the test's.
        assertThat(Thread.interrupted()).isTrue();
        assertThat(result.isError()).isTrue();
        assertThat(result.text()).isEqualTo(PipelineStepping.CANCELLED);
        Mockito.verify(world.walks.getFirst()).close();
    }

    @Test
    void aCleanStepIsRecordedWithTheStreamsItRanOnAndGivesTheNextStep() {
        world.records(2, AN_EVENT);
        Mockito.when(world.plan.buildOf(SteppingWorld.REF)).thenReturn(Optional.of("acme-v1"));

        final Map<String, Object> result = stepSample("{\"pipeline_uuid\": \"p-1\", \"stream_ids\": [8, 7, 8]}");

        assertThat(result.get("verdict")).isEqualTo("clean");
        Mockito.verify(world.buildRecords).recordPassed(SteppingWorld.REF, BuildRecords.STEPPED, null,
                Map.of("stepped_streams", List.of(7L, 8L)));
        Mockito.verify(world.plan).withNext(any(), Mockito.eq("acme-v1"), any());
    }

    @Test
    void aRecordThatCouldntBeSavedIsSaid() {
        world.records(1, AN_EVENT);
        Mockito.when(world.buildRecords.recordPassed(any(), anyString(), any(), any()))
                .thenReturn(Optional.of("Passed, but the record of it couldn't be saved"));

        assertThat(stepSample("{\"pipeline_uuid\": \"p-1\", \"stream_ids\": [7]}").get("record"))
                .isEqualTo("Passed, but the record of it couldn't be saved");
    }

    @Test
    void blockingOrEmptyRunsAreNotRecorded() {
        world.records(2, EMPTY_EVENTS);
        assertThat(stepSample("{\"pipeline_uuid\": \"p-1\", \"stream_ids\": [7]}").get("verdict"))
                .isEqualTo("blocking");
        world.stepper = (stream, at) -> null;
        stepSample("{\"pipeline_uuid\": \"p-1\", \"stream_ids\": [7]}");

        Mockito.verify(world.buildRecords, Mockito.never()).recordPassed(any(), anyString(), any(), any());
    }

    @Test
    void aCleanDraftIsRecordedForTheDraftAndSaysToSaveIt() {
        world.records(1, AN_EVENT);
        final Map<String, String> draft = Map.of("translationFilter", "<xsl:stylesheet/>");

        final Map<String, Object> result = stepSample("{\"pipeline_uuid\": \"p-1\", \"stream_ids\": [7], "
                                                      + "\"draft_code\": {\"translationFilter\": "
                                                      + "\"<xsl:stylesheet/>\"}}");

        assertThat(world.opened.getFirst().getValue()).isEqualTo(draft);
        assertThat(result.get("draft_code_used")).isEqualTo(List.of("translationFilter"));
        assertThat(String.valueOf(result.get("hint"))).startsWith("Clean with draft code: save it");
        Mockito.verify(world.buildRecords).recordPassed(SteppingWorld.REF, BuildRecords.STEPPED, draft, null);
        Mockito.verify(world.plan, Mockito.never()).withNext(any(), any(), any());
    }

    @Test
    void draftXsltIsCheckedBeforeItIsStepped() {
        Mockito.when(world.checkXslt.check(anyString(), any(), any())).thenReturn(Map.of("ok", false, "errors",
                List.of("stroom:json-parse is not a Stroom function")));

        final ToolResult result = call("{\"pipeline_uuid\": \"p-1\", \"stream_ids\": [7], \"draft_code\": "
                                       + "{\"translationFilter\": \"<xsl:stylesheet xmlns:xsl="
                                       + "\\\"http://www.w3.org/1999/XSL/Transform\\\"/>\"}}", world.context());

        assertThat(result.text()).startsWith("draft_code['translationFilter'] is not stepped: stroom:json-parse");
        assertThat(world.steps).isEmpty();
    }

    @Test
    void aUserWhoMayNotStepIsRefused() {
        Mockito.when(world.securityContext.hasAppPermission(Mockito.any(AppPermission.class))).thenReturn(false);

        final ToolResult result = call("{\"pipeline_uuid\": \"p-1\", \"stream_ids\": [7]}", world.context());

        assertThat(result.isError()).isTrue();
        assertThat(result.text()).contains("permission to step pipelines");
    }

    @Test
    void theStreamsSteppedAreAudited() {
        world.records(1, AN_EVENT);

        stepSample("{\"pipeline_uuid\": \"p-1\", \"stream_ids\": [7]}");

        Mockito.verify(world.auditLog).streamsViewed(any(), Mockito.eq(Set.of(7L)), Mockito.contains("step_sample"));
    }

    @Test
    void steppingChangesNothingButTakesARunSlot() {
        final StepSampleTool tool = new StepSampleTool(world.stepping());
        assertThat(tool.isReadOnly()).isTrue();
        assertThat(tool.usesRunSlot()).isTrue();
        assertThat(tool.confirmsItself()).isFalse();
    }

    private void unknownThenAnEvent() {
        world.stepper = (stream, at) -> switch ((int) at.getRecordIndex()) {
            case 0 -> SteppingWorld.step(stream, 0, 0, "<Events xmlns=\"event-logging:3\"><Event><EventDetail>"
                                                       + "<TypeId>TRAFFIC</TypeId><Unknown/></EventDetail></Event>"
                                                       + "</Events>");
            case 1 -> SteppingWorld.step(stream, 0, 1, AN_EVENT);
            default -> null;
        };
    }

    private Map<String, Object> stepSample(final String arguments) {
        final ToolResult result = call(arguments, world.context());
        assertThat(result.isError()).as(result.text()).isFalse();
        return PortedToolCalls.json(result.text());
    }

    private ToolResult call(final String arguments, final ToolCallContext context) {
        return new StepSampleTool(world.stepping()).execute(PortedToolCalls.call(StepSampleTool.NAME, arguments),
                context);
    }

    @SuppressWarnings("unchecked")
    static List<Map<String, Object>> groups(final Map<String, Object> result) {
        return (List<Map<String, Object>>) result.get("groups");
    }

    @SuppressWarnings("unchecked")
    static String firstMessage(final Map<String, Object> group) {
        return String.valueOf(((List<Map<String, Object>>) group.get("examples")).getFirst().get("message"));
    }
}
