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

import stroom.ai.impl.tools.ToolResult;
import stroom.ai.impl.workspace.ToolTimeoutException;
import stroom.docref.DocRef;
import stroom.pipeline.shared.TextConverterDoc;
import stroom.pipeline.shared.XsltDoc;
import stroom.pipeline.shared.data.PipelineDataBuilder;
import stroom.pipeline.shared.data.PipelineElement;
import stroom.pipeline.shared.data.PipelineProperty;
import stroom.pipeline.shared.data.PipelinePropertyValue;
import stroom.util.shared.HasData;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.isNull;

/// summarise_fix (the Stroom MCP server's `tests/test_diagnosis.py`, its summarise_fix and `_changed_outside`
/// cases), on [SteppingWorld]'s pipeline ACME (`p-1`), whose own XSLT `x` is on `translationFilter`. The
/// comparison and the verdicts it is made of are mocked, as the MCP server's tests mocked `compare` and
/// `step_sample`.
class TestSummariseFixTool {

    private static final Map<String, Object> SCHEMA = group("blocking", "schemaFilter", "ERROR",
            "Output fails schema validation", 1, "The value 'n/a' of element 'IPAddress' is not valid.");
    private static final Map<String, Object> FATAL = group("blocking", "translationFilter", "FATAL",
            "Fatal error", 3, "XPath error at line 12");

    private final SteppingWorld world = new SteppingWorld();
    private final EventLocations eventLocations = Mockito.mock(EventLocations.class);
    private final CompareOutputsTool compareOutputs = Mockito.mock(CompareOutputsTool.class);
    private final StepSampleTool stepSample = Mockito.mock(StepSampleTool.class);
    private final StepRecordsTool stepRecords = Mockito.mock(StepRecordsTool.class);
    /// Each XSLT's or text converter's saved code, by UUID.
    private final Map<String, String> code = new HashMap<>(Map.of("x", "<old/>"));

    TestSummariseFixTool() {
        Mockito.when(world.documentAccess.read(any(DocRef.class), Mockito.eq(HasData.class))).thenAnswer(inv ->
                Optional.ofNullable(code.get(((DocRef) inv.getArgument(0)).getUuid())).map(TestSummariseFixTool::doc));
        Mockito.when(compareOutputs.compareLocated(any(), any(), any(), anyList()))
                .thenReturn(compared(6, 2, "Event/EventDetail/X"));
        Mockito.when(stepSample.stepSample(any(), any(), any(), anyList(), any(), any(), any()))
                .thenReturn(verdict(List.of()));
    }

    @Test
    void aFixIsHeldBackOnlyByErrorsItBringsNotThoseAlreadyThere() {
        // There before too: not the fix's doing.
        final Map<String, Object> unrelated = run(List.of(SCHEMA), List.of(SCHEMA));
        assertThat(unrelated.get("ready")).isEqualTo(true);
        assertThat(first(unrelated, "errors_before_too").get("element")).isEqualTo("schemaFilter");
        assertThat(unrelated.get("errors_before_too_note")).asString().startsWith("The saved code has these as well");

        // The fix clears it.
        final Map<String, Object> resolved = run(List.of(), List.of(SCHEMA));
        assertThat(resolved.get("ready")).isEqualTo(true);
        assertThat(first(resolved, "errors_resolved").get("example")).asString().startsWith("The value 'n/a'");
        assertThat(resolved).doesNotContainKey("errors_before_too");

        // The fix brings a new one.
        final Map<String, Object> broken = run(List.of(SCHEMA, FATAL), List.of(SCHEMA));
        assertThat(broken.get("ready")).isEqualTo(false);
        assertThat(problems(broken).getFirst())
                .contains("blocking errors the saved code does not")
                .contains("XPath error")
                .endsWith("[('translationFilter', 'XPath error at line 12')]");
        assertThat(broken.get("hint")).isEqualTo("Revise the draft and call summarise_fix again.");
    }

    @Test
    void aFixSavedOnACopyIsProvenAgainstThePipelinesOwnCode() {
        // change_event_translation saves the change on a copy first: summarise_fix had nothing to diff a draft
        // against.
        copy("copy", "x-copy");
        copy("same", "x-same");
        code.put("x-copy", "<new/>");
        code.put("x-same", "<old/>");

        final Map<String, Object> result = summarise("{\"pipeline_uuid\": \"p-1\", \"stream_ids\": [1], "
                                                     + "\"expected_paths\": [\"Event/EventDetail/X\"], "
                                                     + "\"fixed_pipeline_uuid\": \"copy\"}");

        assertThat(result.get("ready")).isEqualTo(true);
        assertThat(result.get("element")).isEqualTo("translationFilter");
        assertThat(result.get("diff")).asString().contains("+<new/>").contains("-<old/>");
        // The copy's code, on the pipeline.
        final ArgumentCaptor<CompareOutputsTool.Request> compared = ArgumentCaptor.forClass(
                CompareOutputsTool.Request.class);
        Mockito.verify(compareOutputs).compareLocated(any(), any(), compared.capture(), anyList());
        assertThat(compared.getValue().pipelineUuid()).isEqualTo("p-1");
        assertThat(compared.getValue().draftCode()).isEqualTo(Map.of("translationFilter", "<new/>"));
        assertThat(compared.getValue().element()).isEqualTo("translationFilter");

        assertThat(refused("{\"pipeline_uuid\": \"p-1\", \"stream_ids\": [1], \"expected_paths\": [\"X\"], "
                           + "\"fixed_pipeline_uuid\": \"same\"}"))
                .isEqualTo("The copy's code is the same as the pipeline's: nothing to prove");
        assertThat(refused("{\"pipeline_uuid\": \"p-1\", \"stream_ids\": [1], \"expected_paths\": [\"X\"]}"))
                .startsWith("Give element and draft");
        assertThat(refused("{\"pipeline_uuid\": \"p-1\", \"stream_ids\": [1], \"expected_paths\": [\"X\"], "
                           + "\"fixed_pipeline_uuid\": \"copy\", \"element\": \"dsParser\"}"))
                .isEqualTo("The copy has no XSLT or text converter on 'dsParser'; it has: ['translationFilter']");
    }

    @Test
    void expectedPathsMatchTheFieldOrAnythingBelowIt() {
        final List<String> paths = List.of("Event/EventSource/User/Id", "Event/EventSource/Client/IPAddress",
                "Event/EventDetail/Description");
        assertThat(SummariseFixTool.changedOutside(paths, List.of("Event/EventSource/User",
                "Event/EventSource/Client/IPAddress")))
                .containsExactly("Event/EventDetail/Description");
        assertThat(SummariseFixTool.changedOutside(List.of("Event/EventSource/UserName"),
                List.of("Event/EventSource/User")))
                .containsExactly("Event/EventSource/UserName");
        // A trailing slash on an expected path still matches what is below it.
        assertThat(SummariseFixTool.changedOutside(List.of("Event/EventSource/User/Id"),
                List.of("Event/EventSource/User/")))
                .isEmpty();
    }

    @Test
    void aFixThatChangesNothingOrMoreThanExpectedIsNotReady() {
        Mockito.when(compareOutputs.compareLocated(any(), any(), any(), anyList())).thenReturn(compared(6, 0));
        final Map<String, Object> nothing = summarise(draftCall("[\"Event/EventDetail/X\"]"));
        assertThat(nothing.get("ready")).isEqualTo(false);
        assertThat(problems(nothing)).containsExactly("The draft changes no output on these records: it does not "
                                                      + "reach the reported case");

        Mockito.when(compareOutputs.compareLocated(any(), any(), any(), anyList()))
                .thenReturn(compared(6, 2, "Event/EventDetail/X", "Event/EventDetail/Description"));
        final Map<String, Object> more = summarise(draftCall("[\"Event/EventDetail/X\"]"));
        assertThat(more.get("ready")).isEqualTo(false);
        assertThat(problems(more)).containsExactly("Fields outside expected_paths change too: "
                                                   + "['Event/EventDetail/Description']");
    }

    @Test
    void aReadyFixIsPackagedToApplyOrToHandOver() {
        final Map<String, Object> result = summarise("{\"pipeline_uuid\": \"p-1\", \"element\": "
                                                     + "\"translationFilter\", \"draft\": \"<new/>\", "
                                                     + "\"stream_ids\": [9, 7], \"expected_paths\": "
                                                     + "[\"Event/EventDetail/X\"], \"issue\": \"X is wrong\"}");

        assertThat(result.get("issue")).isEqualTo("X is wrong");
        assertThat(result.get("pipeline")).isEqualTo(Map.of("uuid", "p-1", "name", "ACME"));
        assertThat(result.get("doc")).isEqualTo(Map.of("type", "XSLT", "uuid", "x", "name", "ACME"));
        assertThat(result.get("inherited_from_template")).isEqualTo(false);
        assertThat(result.get("records_compared")).isEqualTo(6);
        assertThat(result.get("records_changed")).isEqualTo(2);
        assertThat(result.get("step_verdict")).isEqualTo("clean");
        assertThat(result.get("diff")).isEqualTo("--- ACME (saved)\n+++ ACME (fixed)\n@@ -1 +1 @@\n-<old/>\n"
                                                 + "+<new/>\n");
        assertThat(result.get("draft")).isEqualTo("<new/>");
        assertThat(result.get("manual_steps")).isEqualTo(List.of(
                "Open the XSLT 'ACME' (uuid x) in Stroom.",
                "Apply the change in 'diff' below (or paste the complete fixed code), then save.",
                "Open pipeline 'ACME', enter stepping on raw stream(s) [7, 9] and check the reported record now "
                + "gives the expected output with no errors.",
                "Reprocess the affected streams yourself if you want existing events corrected; new data picks up "
                + "the fix automatically."));
        assertThat(result.get("hint")).asString().startsWith("Show the user the diff and the fields changed");
        // Stepped with the fix, then with the saved code.
        Mockito.verify(stepSample).stepSample(any(), any(), Mockito.eq("p-1"), Mockito.eq(List.of(9L, 7L)),
                Mockito.eq(Map.of("translationFilter", "<new/>")), isNull(), isNull());
        Mockito.verify(stepSample).stepSample(any(), any(), Mockito.eq("p-1"), Mockito.eq(List.of(9L, 7L)), isNull(),
                isNull(), isNull());
        // Told what it is doing.
        assertThat(world.said).contains("Proving the fix: comparing outputs with the saved code's",
                "Proving the fix: stepping with the fix", "Proving the fix: stepping with the saved code");
    }

    @Test
    void theIndexedEventsRecordsAreSteppedExactly() {
        // Asked for by the user: a fix proven on what a search found, across many streams.
        final Map<String, Object> located = new LinkedHashMap<>();
        located.put("stream_id", 8L);
        located.put("event_id", 3L);
        located.put("raw", 6L);
        located.put("part", 0L);
        located.put("record", 12L);
        Mockito.when(eventLocations.eventLocations(anyList())).thenReturn(List.of(located));
        Mockito.when(stepRecords.stepRecords(any(), any(), any(), anyList(), any())).thenReturn(verdict(List.of()));

        final Map<String, Object> result = summarise("{\"pipeline_uuid\": \"p-1\", \"element\": "
                                                     + "\"translationFilter\", \"draft\": \"<new/>\", \"events\": "
                                                     + "[{\"stream_id\": 8, \"event_id\": 3}], \"expected_paths\": "
                                                     + "\"Event/EventDetail/X\"}");

        assertThat(result.get("ready")).isEqualTo(true);
        Mockito.verify(eventLocations).eventLocations(List.of(new EventLocations.EventRef(8, 3)));
        Mockito.verify(compareOutputs).compareLocated(any(), any(), any(), Mockito.eq(List.of(located)));
        final List<StepRecordsTool.Location> where = List.of(new StepRecordsTool.Location(6, 0, 12, null,
                StepRecordsTool.EXPECT_EVENT));
        Mockito.verify(stepRecords).stepRecords(any(), any(), Mockito.eq("p-1"), Mockito.eq(where),
                Mockito.eq(Map.of("translationFilter", "<new/>")));
        Mockito.verify(stepRecords).stepRecords(any(), any(), Mockito.eq("p-1"), Mockito.eq(where), isNull());
        // No streams were given, so none were stepped whole.
        Mockito.verifyNoInteractions(stepSample);
        assertThat(String.valueOf(((List<?>) result.get("manual_steps")).get(2))).contains("raw stream(s) [6]");
    }

    @Test
    void codeATemplateSetsIsFlaggedAsChangingEveryPipelineBuiltFromIt() {
        world.pipeline(SteppingWorld.TEMPLATE, "Event Data (Text)", null, new PipelineDataBuilder()
                .addElement(new PipelineElement("dsParser", "DSParser"))
                .addElement(new PipelineElement("translationFilter", "XSLTFilter"))
                .addLink("dsParser", "translationFilter")
                .addProperty(new PipelineProperty("dsParser", "textConverter",
                        new PipelinePropertyValue(new DocRef(TextConverterDoc.TYPE, "tc", "Splitter")))));
        code.put("tc", "<dataSplitter/>");

        final Map<String, Object> result = summarise("{\"pipeline_uuid\": \"p-1\", \"element\": \"dsParser\", "
                                                     + "\"draft\": \"<dataSplitter version='3.0'/>\", "
                                                     + "\"stream_ids\": [7], \"expected_paths\": "
                                                     + "[\"Event/EventDetail/X\"]}");

        assertThat(result.get("inherited_from_template")).isEqualTo(true);
        assertThat(result.get("set_by")).isEqualTo("Event Data (Text)");
        assertThat(((List<?>) result.get("manual_steps")).getFirst())
                .isEqualTo("Note: this Text Converter is set by template pipeline 'Event Data (Text)', so the "
                           + "change affects every pipeline built from that template.");
        // A text converter's output is compared at the pipeline's own XSLT step.
        final ArgumentCaptor<CompareOutputsTool.Request> compared = ArgumentCaptor.forClass(
                CompareOutputsTool.Request.class);
        Mockito.verify(compareOutputs).compareLocated(any(), any(), compared.capture(), anyList());
        assertThat(compared.getValue().element()).isNull();
    }

    @Test
    void whatAFixIsProvenOnAndWhatItShouldChangeAreNeeded() {
        assertThat(refused("{\"element\": \"translationFilter\", \"draft\": \"<new/>\"}"))
                .isEqualTo("Give pipeline_uuid: the pipeline being fixed.");
        assertThat(refused("{\"pipeline_uuid\": \"p-1\", \"element\": \"translationFilter\", \"draft\": "
                           + "\"<new/>\", \"expected_paths\": [\"X\"]}"))
                .isEqualTo("Give stream_ids (raw streams) or events (from search_events with an index) to prove "
                           + "the fix on");
        assertThat(refused("{\"pipeline_uuid\": \"p-1\", \"element\": \"translationFilter\", \"draft\": "
                           + "\"<new/>\", \"stream_ids\": [1]}"))
                .isEqualTo("Give expected_paths: the output fields the fix should change");
        assertThat(refused("{\"pipeline_uuid\": \"p-1\", \"element\": \"schemaFilter\", \"draft\": \"<new/>\", "
                           + "\"stream_ids\": [1], \"expected_paths\": [\"X\"]}"))
                .isEqualTo("Element 'schemaFilter' has no XSLT or text converter; elements with code: "
                           + "['translationFilter']");
        assertThat(refused(draftCall("[\"X\"]").replace("<new/>", "<old/>")))
                .isEqualTo("The draft is the same as the saved code");
        Mockito.verifyNoInteractions(compareOutputs, stepSample);
    }

    @Test
    void aFixWhoseCodeCantBeReadIsRefusedSayingSo() {
        code.clear();
        assertThat(refused(draftCall("[\"X\"]")))
                .isEqualTo("XSLT 'ACME' (uuid x) can't be read: it doesn't exist, or you don't have permission to "
                           + "view it");
    }

    @Test
    void aProofCutShortByTheCallsTimeIsNotReadyAndStepsNoMore() {
        final Map<String, Object> stopped = compared(3, 1, "Event/EventDetail/X");
        stopped.put("stopped_early", true);
        Mockito.when(compareOutputs.compareLocated(any(), any(), any(), anyList())).thenReturn(stopped);

        final Map<String, Object> result = summarise(draftCall("[\"Event/EventDetail/X\"]"));

        assertThat(result.get("ready")).isEqualTo(false);
        assertThat(result.get("stopped_early")).isEqualTo(true);
        assertThat(result.get("hint")).asString()
                .startsWith("Stopped while proving the fix before this session's time limit for a call")
                .endsWith("The fix is not proven: give fewer stream_ids or events to prove it on.");
        Mockito.verifyNoInteractions(stepSample);
    }

    @Test
    void aCancelledCallStopsAtOnce() {
        Mockito.when(stepSample.stepSample(any(), any(), any(), anyList(), any(), any(), any()))
                .thenThrow(new ToolTimeoutException());

        assertThat(refused(draftCall("[\"Event/EventDetail/X\"]"))).isEqualTo(PipelineStepping.CANCELLED);
    }

    @Test
    void theVerdictOverStreamsAndRecordsIsTheMostSerious() {
        final SummariseFixTool.Stepped stepped = SummariseFixTool.combined(List.of(
                verdict(List.of(group("review", "translationFilter", "WARNING", "A warning", 1, "w"))),
                verdict(List.of(SCHEMA))));

        assertThat(stepped.verdict()).isEqualTo("blocking");
        assertThat(stepped.groups()).hasSize(2);
        assertThat(stepped.stopped()).isFalse();
        assertThat(SummariseFixTool.brief(SCHEMA)).isEqualTo(Map.of("element", "schemaFilter", "severity", "ERROR",
                "count", 1, "reason", "Output fails schema validation", "example",
                "The value 'n/a' of element 'IPAddress' is not valid."));
    }

    // summarise_fix with the draft giving these blocking groups, and the saved code those.
    private Map<String, Object> run(final List<Map<String, Object>> draftGroups,
                                    final List<Map<String, Object>> savedGroups) {
        Mockito.when(stepSample.stepSample(any(), any(), any(), anyList(), any(), any(), any()))
                .thenReturn(verdict(draftGroups), verdict(savedGroups));
        return summarise(draftCall("[\"Event/EventDetail/X\"]"));
    }

    private static String draftCall(final String expectedPaths) {
        return "{\"pipeline_uuid\": \"p-1\", \"element\": \"translationFilter\", \"draft\": \"<new/>\", "
               + "\"stream_ids\": [1], \"expected_paths\": " + expectedPaths + "}";
    }

    // Adds a copy of ACME whose XSLT is the one given.
    private void copy(final String uuid, final String xslt) {
        world.pipeline(uuid, uuid, SteppingWorld.TEMPLATE, new PipelineDataBuilder()
                .addProperty(new PipelineProperty("translationFilter", "xslt",
                        new PipelinePropertyValue(new DocRef(XsltDoc.TYPE, xslt, "X")))));
    }

    private Map<String, Object> summarise(final String arguments) {
        final ToolResult result = tool().execute(PortedToolCalls.call(SummariseFixTool.NAME, arguments),
                world.context());
        assertThat(result.isError()).as(result.text()).isFalse();
        return PortedToolCalls.json(result.text());
    }

    private String refused(final String arguments) {
        final ToolResult result = tool().execute(PortedToolCalls.call(SummariseFixTool.NAME, arguments),
                world.context());
        assertThat(result.isError()).as(result.text()).isTrue();
        return result.text();
    }

    private SummariseFixTool tool() {
        return new SummariseFixTool(world.stepping(), eventLocations, compareOutputs, stepSample, stepRecords,
                world.documentAccess);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> first(final Map<String, Object> result, final String key) {
        return ((List<Map<String, Object>>) result.get(key)).getFirst();
    }

    @SuppressWarnings("unchecked")
    private static List<String> problems(final Map<String, Object> result) {
        return (List<String>) result.get("problems");
    }

    private static HasData doc(final String data) {
        return XsltDoc.builder().uuid("any").name("any").data(data).build();
    }

    private static Map<String, Object> compared(final int records, final int changed, final String... paths) {
        final List<Map<String, Object>> fields = new ArrayList<>();
        for (final String path : paths) {
            fields.add(new LinkedHashMap<>(Map.of("path", path, "added", 0, "removed", 0, "changed", changed)));
        }
        final Map<String, Object> result = new LinkedHashMap<>();
        result.put("records_compared", records);
        result.put("records_changed", changed);
        result.put("element", "translationFilter");
        result.put("fields_changed", fields);
        result.put("unchanged", fields.isEmpty());
        return result;
    }

    private static Map<String, Object> verdict(final List<Map<String, Object>> groups) {
        final Map<String, Object> result = new LinkedHashMap<>();
        result.put("verdict", groups.stream().anyMatch(g -> "blocking".equals(g.get("class")))
                ? "blocking"
                : groups.isEmpty()
                        ? "clean"
                        : "review");
        result.put("groups", groups);
        return result;
    }

    private static Map<String, Object> group(final String kind,
                                             final String element,
                                             final String severity,
                                             final String reason,
                                             final int count,
                                             final String example) {
        final Map<String, Object> group = new LinkedHashMap<>();
        group.put("class", kind);
        group.put("element", element);
        group.put("severity", severity);
        group.put("reason", reason);
        group.put("count", count);
        group.put("examples", List.of(Map.of("message", example)));
        return group;
    }
}
