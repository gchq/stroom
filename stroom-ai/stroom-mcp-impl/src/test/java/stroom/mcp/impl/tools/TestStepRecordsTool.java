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

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;

/// step_records (the Stroom MCP server's `tests/test_stepping.py`, its step_records cases).
class TestStepRecordsTool {

    private static final String AN_EVENT = "<Events xmlns=\"event-logging:3\"><Event/></Events>";

    private final SteppingWorld world = new SteppingWorld();

    @Test
    void nothingSteppedIsNeverClean() {
        // An XSLT that doesn't compile: Stroom finds no record and reports the error on the element.
        world.stepper = (stream, at) -> SteppingWorld.none("XsltPool - Variable user has not been declared");

        final Map<String, Object> result = stepRecords("{\"pipeline_uuid\": \"p-1\", \"locations\": "
                                                       + "[{\"stream\": 7, \"record\": 0}]}");

        assertThat(List.of(result.get("records_stepped"), result.get("verdict"))).containsExactly(0, "blocking");
        assertThat(TestStepSampleTool.groups(result)).isNotEmpty();
        assertThat(result.get("records_not_found")).isEqualTo(List.of("7:0"));
    }

    @Test
    void unknownFromAnXsltSavedWithoutAMappingBlocks() {
        world.stepper = (stream, at) -> SteppingWorld.step(stream, 0, at.getRecordIndex(), "<Events "
                + "xmlns=\"event-logging:3\"><Event><EventDetail><TypeId>TRAFFIC</TypeId><Unknown/></EventDetail>"
                + "</Event></Events>");
        Mockito.when(world.guard.tags(any())).thenReturn(Set.of(WriteGuard.MANAGED));

        final Map<String, Object> result = stepRecords("{\"pipeline_uuid\": \"p-1\", \"locations\": "
                                                       + "[{\"stream\": 7, \"record\": 0}]}");

        assertThat(result.get("verdict")).isEqualTo("blocking");
        assertThat(String.valueOf(TestStepSampleTool.groups(result).getFirst().get("reason")))
                .contains("EventDetail/Unknown");
    }

    @Test
    void locationsInARowFromOneStreamShareASession() {
        // Stroom captures the stream once for them.
        world.stepper = (stream, at) -> SteppingWorld.step(stream, at.getPartIndex(), at.getRecordIndex(), AN_EVENT);

        final Map<String, Object> result = stepRecords("{\"pipeline_uuid\": \"p-1\", \"locations\": ["
                                                       + "{\"stream\": 7, \"record\": 0}, "
                                                       + "{\"stream\": 7, \"part\": 1, \"record\": 2}, "
                                                       + "{\"stream\": 8, \"record\": 5}]}");

        assertThat(world.opened).extracting(Map.Entry::getKey).containsExactly(7L, 8L);
        assertThat(world.steps).containsExactly("REFRESH 0:0", "REFRESH 1:2", "REFRESH 0:5");
        assertThat(result.get("records_stepped")).isEqualTo(3);
        assertThat(result.get("verdict")).isEqualTo("clean");
        Mockito.verify(world.buildRecords).recordPassed(SteppingWorld.REF, BuildRecords.STEPPED, null,
                Map.of("stepped_streams", List.of(7L, 8L)));
    }

    @Test
    void aRecordThatGivesNoEventIsFlaggedAndItsShapeIsNotClean() {
        world.stepper = (stream, at) -> SteppingWorld.step(stream, 0, at.getRecordIndex(), at.getRecordIndex() == 0
                ? AN_EVENT
                : "<Events xmlns=\"event-logging:3\"/>");

        final Map<String, Object> result = stepRecords("{\"pipeline_uuid\": \"p-1\", \"locations\": ["
                                                       + "{\"stream\": 7, \"record\": 0, \"shape\": \"a\"}, "
                                                       + "{\"stream\": 7, \"record\": 1, \"shape\": \"b\"}]}");

        assertThat(result.get("shapes_not_clean")).isEqualTo(List.of("b"));
        assertThat(TestStepSampleTool.firstMessage(TestStepSampleTool.groups(result).getFirst()))
                .isEqualTo("The record produced no Event");
        // Not every shape is clean: not recorded.
        Mockito.verify(world.buildRecords, Mockito.never()).recordPassed(any(), anyString(), any(), any());
    }

    @Test
    void aKindLeftUntranslatedCountsAsCleanWhenItGivesNoEvent() {
        // survey_feed's set_shape_handling: the user chose to leave a kind untranslated, so no Event is right, and
        // an empty output isn't the no-elements error.
        world.stepper = (stream, at) -> SteppingWorld.step(stream, 0, at.getRecordIndex(), at.getRecordIndex() == 0
                ? ""
                : AN_EVENT);

        final Map<String, Object> result = stepRecords("{\"pipeline_uuid\": \"p-1\", \"locations\": ["
                                                       + "{\"stream\": 7, \"record\": 0, \"shape\": \"noise\", "
                                                       + "\"expect\": \"none\"}, "
                                                       + "{\"stream\": 7, \"record\": 1, \"shape\": \"noise\", "
                                                       + "\"expect\": \"none\"}]}");

        assertThat(result.get("left_untranslated_as_intended")).isEqualTo(1);
        assertThat(result.get("shapes_not_clean")).isEqualTo(List.of("noise"));
        assertThat(TestStepSampleTool.firstMessage(TestStepSampleTool.groups(result).getFirst()))
                .isEqualTo("The record produced 1 Event(s), but its kind is left untranslated");
    }

    @Test
    void locationsGivenAsJsonTextAreRead() {
        world.stepper = (stream, at) -> SteppingWorld.step(stream, 0, at.getRecordIndex(), AN_EVENT);

        final Map<String, Object> result = stepRecords("{\"pipeline_uuid\": \"p-1\", \"locations\": "
                                                       + "\"[{\\\"stream\\\": 7, \\\"record\\\": 3}]\"}");

        assertThat(world.steps).containsExactly("REFRESH 0:3");
        assertThat(result.get("records_stepped")).isEqualTo(1);
    }

    @Test
    void aLocationWithoutARecordIsRefused() {
        final ToolResult result = new StepRecordsTool(world.stepping()).execute(PortedToolCalls.call(
                StepRecordsTool.NAME, "{\"pipeline_uuid\": \"p-1\", \"locations\": [{\"stream\": 7}]}"),
                world.context());

        assertThat(result.isError()).isTrue();
        assertThat(result.text()).startsWith("locations[0] needs stream and record");
    }

    private Map<String, Object> stepRecords(final String arguments) {
        final ToolResult result = new StepRecordsTool(world.stepping()).execute(PortedToolCalls.call(
                StepRecordsTool.NAME, arguments), world.context());
        assertThat(result.isError()).as(result.text()).isFalse();
        return PortedToolCalls.json(result.text());
    }
}
