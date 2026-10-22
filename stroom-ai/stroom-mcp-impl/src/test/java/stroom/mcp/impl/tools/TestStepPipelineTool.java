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

import stroom.ai.impl.AiChatRunManager;
import stroom.ai.impl.tools.ToolCallContext;
import stroom.ai.impl.tools.ToolResult;
import stroom.ai.impl.tools.UserQuestions;
import stroom.ai.impl.tools.validation.XsltSafety;
import stroom.docref.DocRef;
import stroom.pipeline.shared.XsltDoc;
import stroom.util.json.JsonUtil;

import io.modelcontextprotocol.spec.McpSchema.ElicitResult;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;

/// step_pipeline (the Stroom MCP server's `tests/test_stepping.py` and `tests/test_authoring_guards.py`, its
/// step_pipeline cases).
class TestStepPipelineTool {

    // A draft that looks up the host name of each record's text in DNS.
    private static final String LOOKUP_DRAFT = "<xsl:stylesheet xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\" "
                                               + "version=\"3.0\" xmlns:stroom=\"stroom\"><xsl:template match=\"/\">"
                                               + "<xsl:value-of select=\"stroom:host-name(.)\"/></xsl:template>"
                                               + "</xsl:stylesheet>";
    private static final String LOOKUP_STEP = "{\"pipeline_uuid\": \"p-1\", \"stream_id\": 7, \"draft_code\": "
                                              + "{\"translationFilter\": " + JsonUtil.writeValueAsString(LOOKUP_DRAFT)
                                              + "}}";

    private final SteppingWorld world = new SteppingWorld();
    // Each form's message, as the user was shown it.
    private final List<String> asked = new ArrayList<>();

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

    @Test
    void aDraftCallingADnsLookupIsSteppedOnceTheUserAgreesAndNotAskedAgain() {
        // Asked for by the user: stepping runs the draft on Stroom, so its DNS lookups are made then too, and the
        // user agrees to them as to saving them.
        draftsPassCheckXslt();
        world.records(1, "<Events/>");
        final ToolCallContext context = forms(true);

        assertThat(step(LOOKUP_STEP, context).get("draft_code_used")).isEqualTo(List.of("translationFilter"));
        assertThat(asked).singleElement().asString()
                .startsWith("Step draft XSLT for element 'translationFilter', which calls stroom:host-name(): it "
                            + XsltSafety.LOOKUP_EFFECT)
                .contains("used in: stroom:host-name() in template match='/'");
        // Stepped again: remembered for the session.
        step(LOOKUP_STEP, context);
        assertThat(asked).hasSize(1);
        assertThat(world.opened).hasSize(2);
    }

    @Test
    void aDraftCallingADnsLookupTheUserDeclinesIsNotStepped() {
        draftsPassCheckXslt();

        assertThat(refused(LOOKUP_STEP, forms(false)))
                .startsWith("draft_code['translationFilter'] is not stepped: the user declined stroom:host-name()");
        assertThat(world.opened).isEmpty();
    }

    @Test
    void aDraftCallingADnsLookupIsNotSteppedWhereTheSessionCannotShowAForm() {
        draftsPassCheckXslt();

        assertThat(refused(LOOKUP_STEP, world.context()))
                .startsWith("draft_code['translationFilter'] is not stepped: it calls stroom:host-name()")
                .contains("this session can't show one now");
        assertThat(world.opened).isEmpty();
    }

    @Test
    void aDnsLookupTheElementsSavedXsltAlreadyCallsIsSteppedWithoutAsking() {
        draftsPassCheckXslt();
        world.records(1, "<Events/>");
        Mockito.when(world.documentAccess.read(any(DocRef.class), Mockito.eq(XsltDoc.class)))
                .thenReturn(Optional.of(XsltDoc.builder().uuid("x").name("ACME").data(LOOKUP_DRAFT).build()));

        assertThat(step(LOOKUP_STEP, forms(false)).get("draft_code_used")).isEqualTo(List.of("translationFilter"));
        assertThat(asked).isEmpty();
    }

    @Test
    void aFormWaitingForTheUserInAChatStopsTheCallRatherThanRefusingIt() {
        // In a chat the run stops while the form waits, and the call is made again with the answer: the tool's own
        // catch of what goes wrong must not turn that into a refusal.
        draftsPassCheckXslt();
        final ToolCallContext chat = world.contextBuilder().userQuestions(new UserQuestions() {
            @Override
            public boolean canAsk() {
                return true;
            }

            @Override
            public ElicitResult ask(final String toolName,
                                    final String callId,
                                    final String intro,
                                    final Map<String, Object> form) {
                throw new AiChatRunManager.WaitingForUserException("A form waits");
            }
        }).build();

        assertThatThrownBy(() -> new StepPipelineTool(world.stepping()).execute(PortedToolCalls.call(
                StepPipelineTool.NAME, LOOKUP_STEP), chat))
                .isInstanceOf(AiChatRunManager.WaitingForUserException.class);
        assertThat(world.opened).isEmpty();
    }

    private void draftsPassCheckXslt() {
        Mockito.when(world.checkXslt.check(anyString(), any(), any())).thenReturn(Map.of("ok", true));
    }

    // A context whose forms are answered so: agreed, or declined.
    private ToolCallContext forms(final boolean agree) {
        return world.contextBuilder().userQuestions(new UserQuestions() {
            @Override
            public boolean canAsk() {
                return true;
            }

            @Override
            public ElicitResult ask(final String toolName,
                                    final String callId,
                                    final String intro,
                                    final Map<String, Object> form) {
                asked.add(intro);
                return new ElicitResult(ElicitResult.Action.ACCEPT, Map.of("value", agree));
            }
        }).build();
    }

    private Map<String, Object> step(final String arguments) {
        return step(arguments, world.context());
    }

    private Map<String, Object> step(final String arguments, final ToolCallContext context) {
        final ToolResult result = new StepPipelineTool(world.stepping()).execute(PortedToolCalls.call(
                StepPipelineTool.NAME, arguments), context);
        assertThat(result.isError()).as(result.text()).isFalse();
        return PortedToolCalls.json(result.text());
    }

    private String refused(final String arguments, final ToolCallContext context) {
        final ToolResult result = new StepPipelineTool(world.stepping()).execute(PortedToolCalls.call(
                StepPipelineTool.NAME, arguments), context);
        assertThat(result.isError()).as(result.text()).isTrue();
        return result.text();
    }
}
