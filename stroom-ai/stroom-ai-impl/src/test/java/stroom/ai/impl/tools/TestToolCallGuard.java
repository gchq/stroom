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

import stroom.ai.impl.tools.ToolSchema.ObjectSchema;
import stroom.ai.shared.AiToolCategory;
import stroom.util.json.JsonUtil;

import org.junit.jupiter.api.Test;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

class TestToolCallGuard {

    private static final Charset CP1252 = Charset.forName("windows-1252");
    // What a decoder gives for a byte it can't decode.
    private static final int REPLACEMENT_CHARACTER = 0xFFFD;

    private final AtomicLong now = new AtomicLong(1_000_000);
    private final ToolCallGuard guard = new ToolCallGuard(now::get);
    private final List<String> calls = new ArrayList<>();
    private final Map<String, Function<ToolCall, ToolResult>> tools = new HashMap<>();

    @Test
    void unknownArgumentsAreIgnoredAndReported() {
        final StroomTool tool = tool("build_data_splitter", ObjectSchema.builder()
                .addStringProperty("sample", "The sample.")
                .build(), call -> ToolResult.text("{\"inferred\":true}"));

        final ToolResult result = call(tool, "{\"sample\":\"a,b\",\"save_text_converter\":\"true\"}");

        assertThat(calls).containsExactly("{\"sample\":\"a,b\"}");
        // Inside the JSON, first, so a client can still read the result as JSON.
        assertThat(result.text()).isEqualTo("{\"argument_notes\":[\"Ignored unknown argument(s) "
                                            + "[save_text_converter]: this tool takes [sample].\"],"
                                            + "\"inferred\":true}");
    }

    @Test
    void aChoiceWithStrayPunctuationIsReadAsTheChoice() {
        // Seen: an agent sent converter_type ",XML_FRAGMENT" again and again, refused each time.
        final StroomTool tool = tool("create_text_converter", ObjectSchema.builder()
                .addEnumProperty("converter_type", List.of("DATA_SPLITTER", "XML_FRAGMENT"), "The type.")
                .addEnumProperty("mode", List.of("a", "b"), "The mode.")
                .build(), call -> ToolResult.text("made"));

        final ToolResult result = call(tool, "{\"converter_type\":\",XML_FRAGMENT\",\"mode\":\" B\"}");

        assertThat(JsonUtil.readValue(calls.getFirst(), Map.class))
                .isEqualTo(Map.of("converter_type", "XML_FRAGMENT", "mode", "b"));
        assertThat(result.text()).isEqualTo("made Read converter_type ',XML_FRAGMENT' as 'XML_FRAGMENT', "
                                            + "mode ' B' as 'b': give the choice exactly as listed.");
        // Not a choice at all: left for the tool to refuse.
        assertThat(ToolCallGuard.plainChoice("XML", List.of("DATA_SPLITTER", "XML_FRAGMENT"))).isNull();
        assertThat(ToolCallGuard.plainChoice("XML_FRAGMENT", List.of("XML_FRAGMENT"))).isNull();
    }

    @Test
    void textMangledByAWindowsCodePageRoundTripIsPutBack() {
        // Seen in VS Code: a doc body read back through PowerShell came with mojibake, and the agent stalled a
        // terminal trying to fix it.
        final StroomTool tool = tool("write_documentation", ObjectSchema.builder()
                .addStringProperty("markdown", "The body.")
                .addProperty("accept_errors", AbstractBuiltInTool.stringArraySchema("Errors."))
                .build(), call -> ToolResult.text("{\"ok\":true}"));
        final String mangled = "The user" + mangled("’") + "s logon " + mangled("—");

        final ToolResult result = call(tool, JsonUtil.writeValueAsString(Map.of(
                "markdown", mangled,
                "accept_errors", List.of(Map.of("reason", "caf" + mangled("é")))), false));

        assertThat(JsonUtil.readValue(calls.getFirst(), Map.class)).isEqualTo(Map.of(
                "markdown", "The user’s logon —",
                "accept_errors", List.of(Map.of("reason", "café"))));
        assertThat(result.text()).contains("Windows code page round trip, in 3 place(s)");

        final ToolResult clean = call(tool, "{\"markdown\":\"café — ok\"}");
        assertThat(calls.getLast()).isEqualTo("{\"markdown\":\"café — ok\"}");
        assertThat(clean.text()).isEqualTo("{\"ok\":true}");
    }

    @Test
    void aRunOfFailuresSaysToStepBackAndASuccessResetsIt() {
        final StroomTool tool = translation();
        final List<String> notes = new ArrayList<>();
        for (int n = 0; n < 5; n++) {
            notes.add(String.valueOf(JsonUtil.readValue(
                    call(tool, "{\"mapping\":{\"try\":" + n + "}}").text(), Map.class).get("repeated")));
        }

        assertThat(notes.subList(0, 4)).containsOnly("null");
        assertThat(notes.get(4)).startsWith("That is 5 failed build_translation_xslt calls in a row. Start again "
                                            + "from a clean mapping");
        assertThat(call(tool, "{\"mapping\":{\"good\":true}}").text()).doesNotContain("repeated");
        // The count started again.
        assertThat(call(tool, "{\"mapping\":{\"try\":9}}").text()).doesNotContain("repeated");
    }

    @Test
    void theSameFailingCallIsAnsweredFromItsFailureAfterThree() {
        final StroomTool tool = translation();
        for (int n = 0; n < 3; n++) {
            final ToolResult result = call(tool, "{\"mapping\":{\"raise\":true}}");
            assertThat(result.isError()).isTrue();
            if (n > 0) {
                assertThat(result.text()).contains("The same arguments as a call that already failed");
            }
        }

        // The same arguments in another order are the same call.
        final ToolResult fourth = call(tool, "{ \"mapping\" : { \"raise\" : true } }");

        assertThat(fourth.isError()).isTrue();
        assertThat(fourth.text()).startsWith("Not run again: this exact call has already failed 3 times, last "
                                             + "with: Error: mapping is not a translation mapping");
        // The fourth was not run.
        assertThat(calls).hasSize(3);
    }

    @Test
    void transientFailuresAndToolsWhoseAnswerCanChangeAreRunAgain() {
        final StroomTool tool = translation();
        for (int n = 0; n < 4; n++) {
            final ToolResult result = call(tool, "{\"mapping\":{\"flaky\":true}}");
            assertThat(result.text()).contains("timed out").doesNotContain("already failed");
        }
        final StroomTool stepping = tool("step_sample", null,
                call -> ToolResult.text("{\"ok\":false,\"problems\":[\"blocking\"]}"));
        for (int n = 0; n < 4; n++) {
            call(stepping, "{\"pipeline_uuid\":\"p\"}");
        }

        // step_sample's answer changes with the pipeline: always run.
        assertThat(calls).hasSize(8);
    }

    @Test
    void failuresAreCountedPerSessionAndForgottenAfterAWhile() {
        final StroomTool tool = translation();
        for (int n = 0; n < 3; n++) {
            call(tool, "{\"mapping\":{\"raise\":true}}");
        }
        // Another chat's call is its own.
        final ToolResult elsewhere = guard.run(tool, new ToolCall("c", "build_translation_xslt",
                "{\"mapping\":{\"raise\":true}}"), ToolCallContext.builder(ToolSession.chat(2)).build(), this::run);
        assertThat(elsewhere.text()).doesNotContain("Not run again");

        now.addAndGet(ToolCallGuard.TTL.toMillis() + 1);

        assertThat(call(tool, "{\"mapping\":{\"raise\":true}}").text()).doesNotContain("already failed");
    }

    @Test
    void theRepairPutsBackOnlyWhatIsMojibake() {
        assertThat(MojibakeRepair.repair("café — ok")).isEqualTo(new MojibakeRepair.Repaired("café — ok", 0));
        assertThat(MojibakeRepair.repair(mangled("é"))).isEqualTo(new MojibakeRepair.Repaired("é", 1));
        // Mangled twice over.
        assertThat(MojibakeRepair.repair(mangled(mangled("—"))).text()).isEqualTo("—");
        // A lone lead byte's character, as in ordinary text.
        assertThat(MojibakeRepair.repair("Ã and Â").count()).isZero();
        assertThat(MojibakeRepair.repair(null).text()).isNull();
    }

    private StroomTool translation() {
        return tool("build_translation_xslt", ObjectSchema.builder()
                .addProperty("mapping", ObjectSchema.builder().build())
                .build(), TestToolCallGuard::translate);
    }

    // A stand-in for build_translation_xslt: fails, or fails as if for a while, or succeeds, as the mapping says.
    private static ToolResult translate(final ToolCall call) {
        final Map<?, ?> mapping = (Map<?, ?>) JsonUtil.readValue(call.arguments(), Map.class).get("mapping");
        if (Boolean.TRUE.equals(mapping.get("raise"))) {
            return ToolResult.error("Error: mapping is not a translation mapping: events: Input should be a "
                                    + "valid list");
        }
        if (Boolean.TRUE.equals(mapping.get("flaky"))) {
            return ToolResult.error("Error: Stroom timed out: call again");
        }
        final boolean good = Boolean.TRUE.equals(mapping.get("good"));
        return ToolResult.text(good
                ? "{\"ok\":true,\"problems\":[]}"
                : "{\"ok\":false,\"problems\":[\"extract[0]: no match\"]}");
    }

    private ToolResult call(final StroomTool tool, final String arguments) {
        return guard.run(tool, new ToolCall("c", tool.definition().name(), arguments),
                ToolCallContext.builder(ToolSession.chat(1)).build(), this::run);
    }

    private ToolResult run(final ToolCall call, final ToolCallContext context) {
        calls.add(call.arguments());
        return tools.get(call.name()).apply(call);
    }


    private StroomTool tool(final String name,
                            final ObjectSchema parameters,
                            final Function<ToolCall, ToolResult> behaviour) {
        tools.put(name, behaviour);
        return new StroomTool() {
            @Override
            public ToolDefinition definition() {
                final ToolDefinition.Builder builder = ToolDefinition.builder().name(name).description(name);
                return (parameters == null
                        ? builder
                        : builder.parameters(parameters)).build();
            }

            @Override
            public AiToolCategory category() {
                return AiToolCategory.CONTENT;
            }

            @Override
            public ToolResult execute(final ToolCall call, final ToolCallContext context) {
                return behaviour.apply(call);
            }
        };
    }

    // The text as a Windows code page round trip leaves it: its UTF-8 bytes read as cp1252, with the five bytes
    // cp1252 has no characters for kept as C1 controls, as PowerShell keeps them.
    private static String mangled(final String text) {
        final StringBuilder out = new StringBuilder();
        for (final byte b : text.getBytes(StandardCharsets.UTF_8)) {
            final String read = new String(new byte[]{b}, CP1252);
            out.append(read.length() == 1 && read.charAt(0) == REPLACEMENT_CHARACTER
                    ? (char) (b & 0xFF)
                    : read);
        }
        return out.toString();
    }
}
