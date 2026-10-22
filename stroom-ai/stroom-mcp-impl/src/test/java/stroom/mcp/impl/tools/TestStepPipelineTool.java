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

import static org.assertj.core.api.Assertions.assertThat;

/// step_pipeline (the Stroom MCP server's `tests/test_stepping.py` and `tests/test_authoring_guards.py`, its
/// step_pipeline cases).
class TestStepPipelineTool {

    private final SteppingWorld world = new SteppingWorld();

    @Test
    void aRecordIsSteppedWhereItIsWithTheDraftCode() {
        world.stepper = (stream, at) -> SteppingWorld.step(stream, at.getPartIndex(), at.getRecordIndex(),
                "<Events>" + at.getRecordIndex() + "</Events>");

        final Map<String, Object> result = step("{\"pipeline_uuid\": \"p-1\", \"stream_id\": 7, \"record\": 4, "
                                                + "\"draft_code\": {\"translationFilter\": \"<xsl/>\"}}");

        assertThat(world.steps).containsExactly("REFRESH 0:4");
        assertThat(world.opened.getFirst().getValue()).isEqualTo(Map.of("translationFilter", "<xsl/>"));
        Mockito.verify(world.walks.getFirst()).close();
        assertThat(result.get("record")).isEqualTo(4);
        assertThat(result.get("verdict")).isEqualTo("clean");
        assertThat(result.get("draft_code_used")).isEqualTo(List.of("translationFilter"));
        assertThat(result.get("elements")).isEqualTo(Map.of("translationFilter", Map.of("type", "XSLTFilter",
                "input", "<records/>", "output", "<Events>4</Events>")));
    }

    @Test
    void theFirstOrLastRecordIsSteppedByName() {
        world.records(10, "<Events/>");

        assertThat(step("{\"pipeline_uuid\": \"p-1\", \"stream_id\": 7, \"record\": \"last\"}").get("record"))
                .isEqualTo(9);
        assertThat(step("{\"pipeline_uuid\": \"p-1\", \"stream_id\": 7}").get("record")).isEqualTo(0);
        assertThat(world.steps).containsExactly("LAST", "FIRST");
    }

    @Test
    void anXsltWhoseTemplatesMatchNothingIsAnError() {
        // When no template matches the input (typically a wrong xpath-default-namespace), XSLT's built-in rules copy
        // the text through and nothing raises an error.
        world.records(1, "some text copied through");

        final Map<String, Object> result = step("{\"pipeline_uuid\": \"p-1\", \"stream_id\": 7}");

        assertThat(result.get("verdict")).isNotEqualTo("clean");
        assertThat(TestStepSampleTool.firstMessage(TestStepSampleTool.groups(result).getFirst()))
                .startsWith("Output contains no XML elements");
    }

    @Test
    void noRecordFoundIsRefused() {
        final ToolResult result = new StepPipelineTool(world.stepping()).execute(PortedToolCalls.call(
                StepPipelineTool.NAME, "{\"pipeline_uuid\": \"p-1\", \"stream_id\": 7, \"record\": 3}"),
                world.context());

        assertThat(result.isError()).isTrue();
        assertThat(result.text()).isEqualTo("No record found in stream 7 for 3");
    }

    @Test
    void anUnreadablePipelineIsRefused() {
        final ToolResult result = new StepPipelineTool(world.stepping()).execute(PortedToolCalls.call(
                StepPipelineTool.NAME, "{\"pipeline_uuid\": \"nope\", \"stream_id\": 7}"), world.context());

        assertThat(result.text()).startsWith("Pipeline nope can't be read");
    }

    private Map<String, Object> step(final String arguments) {
        final ToolResult result = new StepPipelineTool(world.stepping()).execute(PortedToolCalls.call(
                StepPipelineTool.NAME, arguments), world.context());
        assertThat(result.isError()).as(result.text()).isFalse();
        return PortedToolCalls.json(result.text());
    }
}
