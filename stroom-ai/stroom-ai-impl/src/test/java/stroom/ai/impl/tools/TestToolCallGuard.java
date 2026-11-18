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

import stroom.ai.impl.tools.ToolSchema.ArraySchema;
import stroom.ai.impl.tools.ToolSchema.ObjectSchema;
import stroom.ai.impl.tools.ToolSchema.StringSchema;
import stroom.ai.impl.workspace.ChatWorkspace;
import stroom.ai.impl.workspace.SessionWorkspace;
import stroom.ai.impl.workspace.WorkspacePath;
import stroom.ai.shared.AiToolCategory;
import stroom.util.json.JsonUtil;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

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
    void theArgumentsAToolsReplyAsksForBackAreKeptThoughNoToolDeclaresThem() {
        // Values too many for a confirmation's form are shown in the chat first: the reply asks for shown_id back,
        // and dropping it as unknown would ask again for ever.
        final StroomTool tool = tool("copy_pipeline", ObjectSchema.builder()
                .addStringProperty("name", "The name.")
                .build(), call -> ToolResult.text("{\"copied\":true}"));

        final String arguments = "{\"name\":\"a\",\"shown_id\":\"def\"}";

        final ToolResult result = call(tool, arguments);

        assertThat(calls).containsExactly(arguments);
        assertThat(result.text()).doesNotContain("Ignored");
    }

    @Test
    void argumentsFoldedIntoAnotherArgumentsTextAreUnfolded() {
        // Seen (a small model, the table tool): the rest of the call written inside eval's text, refused for no
        // path five times.
        final StroomTool tool = table();

        final ToolResult result = call(tool, JsonUtil.writeValueAsString(Map.of("eval",
                "[{\"as\": \"t\", \"expression\": \"contains(${text}, 'roster')\"}], \"limit\": 5, "
                + "\"path\": \"/outputs/topics.md\"}"), false));

        assertThat(JsonUtil.readValue(calls.getFirst(), Map.class)).isEqualTo(Map.of(
                "eval", List.of(Map.of("as", "t", "expression", "contains(${text}, 'roster')")),
                "limit", 5,
                "path", "/outputs/topics.md"));
        assertThat(result.text()).isEqualTo("done Read the text of eval as the arguments it held: give each "
                                            + "argument on its own, its list or object as JSON, not inside "
                                            + "another's text.");
    }

    @Test
    void anArgumentGivenAlreadyIsKeptOverOneFoldedIntoAnothersText() {
        final StroomTool tool = table();

        call(tool, JsonUtil.writeValueAsString(Map.of(
                "aggregates", "[{\"function\": \"count\", \"as\": \"n\"}], \"groupBy\": [\"code\"], "
                              + "\"path\": \"/outputs/other.md\"}",
                "path", "/outputs/given.md"), false));

        assertThat(JsonUtil.readValue(calls.getFirst(), Map.class)).isEqualTo(Map.of(
                "aggregates", List.of(Map.of("function", "count", "as", "n")),
                "groupBy", List.of("code"),
                "path", "/outputs/given.md"));
    }

    @Test
    void plainJsonTextAndTextArgumentsAreLeftToTheTool() {
        final StroomTool tool = table();
        final String plainList = "[{\"function\": \"count\", \"as\": \"n\"}]";
        final String textLikeJson = "[1], \"limit\": 5}";

        final ToolResult result = call(tool, JsonUtil.writeValueAsString(Map.of(
                "aggregates", plainList, "path", textLikeJson), false));

        assertThat(JsonUtil.readValue(calls.getFirst(), Map.class))
                .isEqualTo(Map.of("aggregates", plainList, "path", textLikeJson));
        assertThat(result.text()).isEqualTo("done");
        // Text that, read as the argument's start, isn't JSON is left too.
        final Map<String, Object> arguments = new HashMap<>(Map.of("eval", "[{\"as\": }], \"limit\": 5}"));
        assertThat(ToolCallGuard.unfolded(arguments, table().definition().parameters().properties())).isEmpty();
        assertThat(arguments).containsOnlyKeys("eval");
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
    void aRunOfNewProblemsIsGettingOnAndIsToldOnlyLater() {
        // Seen: five build_translation_xslt failures in a row, each a different problem fixed in turn (the last no
        // name to save under), told to start again from a clean mapping.
        final StroomTool tool = tool("build_translation_xslt", null, call -> ToolResult.text(
                "{\"ok\":false,\"problems\":[\"" + JsonUtil.readValue(call.arguments(), Map.class).get("problem")
                + "\"]}"));
        final List<String> notes = new ArrayList<>();
        for (int n = 0; n < 10; n++) {
            notes.add(String.valueOf(JsonUtil.readValue(call(tool, "{\"problem\":\"problem " + (char) ('a' + n)
                                                                   + "\"}").text(), Map.class).get("repeated")));
        }

        assertThat(notes.subList(0, 9)).containsOnly("null");
        assertThat(notes.get(9)).startsWith("That is 10 failed build_translation_xslt calls in a row, each a new "
                                            + "problem. Start again from a clean mapping");
    }

    @Test
    void aProblemComingBackInTheRunIsToldAtTheFifth() {
        // Its numbers and quoted values aside, as a regex's stopping place moves while it fails the same way.
        final StroomTool tool = tool("build_translation_xslt", null, call -> ToolResult.text(
                "{\"ok\":false,\"problems\":[\"" + JsonUtil.readValue(call.arguments(), Map.class).get("problem")
                + "\"]}"));
        final List<String> problems = List.of("when: not a list", "Purpose holds other elements",
                "regex stops matching at 12", "no name to save", "regex stops matching at 40");
        final List<String> notes = new ArrayList<>();
        for (final String problem : problems) {
            notes.add(String.valueOf(JsonUtil.readValue(call(tool, "{\"problem\":\"" + problem + "\"}").text(),
                    Map.class).get("repeated")));
        }

        assertThat(notes.get(4)).startsWith("That is 5 failed build_translation_xslt calls in a row. Start again");
        assertThat(ToolCallGuard.problem("'x-type' reads 3 values")).isEqualTo("'' reads # values");
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

        // The same arguments, spaced otherwise, are the same call.
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
    void aBusyOrOverloadedClusterIsTransientButAQueryNeedingTooMuchMemoryIsNot() {
        // A cluster short of memory or overloaded right now (HTTP 429) may pass, so trying again shortly isn't
        // repeating a failure; a query that needs more memory than allowed must be changed, not run again as it is.
        final List<String> busy = List.of(
                "Error: Elasticsearch refused the query: HTTP 429 Too Many Requests",
                "Error: Elasticsearch refused the query: status 429",
                "Error: Elasticsearch is overloaded right now, so it refused the query: try again shortly. "
                + "Elasticsearch said: rejected execution of TimedRunnable on search",
                "Error: Elasticsearch is short of memory right now, so it refused the search: try again shortly. "
                + "Elasticsearch said: [parent] Data too large");
        for (final String message : busy) {
            final StroomTool esql = tool("esql_query", null, call -> ToolResult.text(message));
            for (int n = 0; n < 6; n++) {
                assertThat(call(esql, "{\"pipeline\":\"| STATS c = COUNT(*)\"}").text())
                        .as(message)
                        .doesNotContain("already failed", "in a row");
            }
        }
        final StroomTool tooBig = tool("esql_query", null, call -> ToolResult.text(
                "Error: The query needs more memory than Elasticsearch allows it: narrow the time range, group by "
                + "fewer high-cardinality fields, avoid MV_EXPAND on large multi-value fields, or lower the LIMIT. "
                + "Elasticsearch said: [request] Data too large, data for [<reused_arrays>] would be "
                + "[429/429b], which is larger than the limit of [100/100b]."));

        call(tooBig, "{\"pipeline\":\"| STATS c = COUNT(*) BY user\"}");
        assertThat(call(tooBig, "{\"pipeline\":\"| STATS c = COUNT(*) BY user\"}").text())
                .contains("The same arguments as a call that already failed (2 times): change them.");
    }

    @Test
    void theUsersAnswerToAFormIsNoFailure() {
        // Seen in testing (chat 145): told "no indexing for now" as the backend form showed, get_field_conventions was
        // answered "The same arguments as a call that already failed (2 times): change them. Step back..."
        final StroomTool conventions = tool("get_field_conventions", null,
                call -> ToolResult.error("The user made no choice: Which backend should the new index use?"));
        for (int n = 0; n < 4; n++) {
            assertThat(call(conventions, "{}").text()).doesNotContain("already failed", "in a row");
        }
        assertThat(calls).hasSize(4);
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
    void theThirdIdenticalReadInARunSaysNothingHasChangedButStillRuns() {
        // Seen in a UniFi OS onboarding chat: the same read_file calls on three /outputs files about 14 times in 70
        // seconds, and about 15 jq calls on the same file.
        final StroomTool readFile = tool("read_file", ObjectSchema.builder()
                .addStringProperty("path", "The file.")
                .build(), call -> ToolResult.text("line 1"));
        final ToolRunContext run = new ToolRunContext(null, null, () -> false);
        final ToolCallContext context = ToolCallContext.builder(ToolSession.chat(1)).runContext(run).build();

        assertThat(read(readFile, "{\"path\":\"/outputs/a.md\"}", context).text()).isEqualTo("line 1");
        // The same arguments, spaced otherwise, are the same call.
        assertThat(read(readFile, "{ \"path\": \"/outputs/a.md\" }", context).text()).isEqualTo("line 1");
        // Another file is another call.
        assertThat(read(readFile, "{\"path\":\"/outputs/b.md\"}", context).text()).isEqualTo("line 1");
        final ToolResult third = read(readFile, "{\"path\":\"/outputs/a.md\"}", context);
        final ToolResult fourth = read(readFile, "{\"path\":\"/outputs/a.md\"}", context);

        // Run, its result returned, with the note after it.
        assertThat(calls).hasSize(5);
        assertThat(third.isError()).isFalse();
        assertThat(third.text()).isEqualTo("line 1\n\nYou have made this same call 3 times in this turn with the "
                                           + "same result; nothing has been written since. Use what it returned, "
                                           + "or try something different.");
        assertThat(fourth.text()).contains("this same call 4 times");
        // In a new run (turn), the count starts again.
        final ToolCallContext next = ToolCallContext.builder(ToolSession.chat(1))
                .runContext(new ToolRunContext(null, null, () -> false))
                .build();
        assertThat(read(readFile, "{\"path\":\"/outputs/a.md\"}", next).text()).isEqualTo("line 1");
    }

    @Test
    void anIdenticalReadInsideJsonIsNotedInsideIt() {
        final StroomTool jq = tool("jq", ObjectSchema.builder()
                .addStringProperty("filter", "The filter.")
                .build(), call -> ToolResult.text("{\"count\":3}"));
        for (int n = 0; n < 2; n++) {
            call(jq, "{\"filter\":\".a\"}");
        }

        final Map<?, ?> third = JsonUtil.readValue(call(jq, "{\"filter\":\".a\"}").text(), Map.class);

        assertThat(third.get("count")).isEqualTo(3);
        assertThat(String.valueOf(third.get("repeated"))).startsWith("You have made this same call 3 times in this "
                                                                     + "session");
    }

    @Test
    void identicalReadsFarApartAreNotCountedTogether() {
        // Over a long MCP session, the same read for each of several questions is reasonable, and what it reads may
        // have been changed outside the tools meanwhile.
        final StroomTool describe = tool("describe_data_source", ObjectSchema.builder()
                .addStringProperty("data_source", "The data source.")
                .build(), call -> ToolResult.text("Fields (3)"));
        final String args = "{\"data_source\":\"Proxy uploads\"}";

        call(describe, args);
        now.addAndGet(ToolCallGuard.SAME_READ_WINDOW.toMillis() + 1);
        call(describe, args);
        now.addAndGet(ToolCallGuard.SAME_READ_WINDOW.toMillis() + 1);
        final ToolResult third = call(describe, args);
        // Within the window of the one before, it is counted with it.
        now.addAndGet(ToolCallGuard.SAME_READ_WINDOW.toMillis());
        call(describe, args);
        now.addAndGet(1_000);
        final ToolResult fifth = call(describe, args);

        assertThat(calls).hasSize(5);
        assertThat(third.text()).isEqualTo("Fields (3)");
        assertThat(fifth.text()).contains("this same call 3 times");
    }

    @Test
    void anyWriteStartsTheCountOfIdenticalReadsAgain() {
        final StroomTool ls = tool("ls", ObjectSchema.builder().build(), call -> ToolResult.text("a.md"));
        final StroomTool table = table();
        final StroomTool change = new StroomTool() {
            @Override
            public ToolDefinition definition() {
                return ToolDefinition.builder().name("propose_query").description("propose_query").build();
            }

            @Override
            public AiToolCategory category() {
                return AiToolCategory.CONTENT;
            }

            @Override
            public boolean isReadOnly() {
                return false;
            }

            @Override
            public ToolResult execute(final ToolCall call, final ToolCallContext context) {
                return ToolResult.text("proposed");
            }
        };
        tools.put("propose_query", call -> ToolResult.text("proposed"));
        final ChatWorkspace chatWorkspace = Mockito.mock(ChatWorkspace.class);

        call(ls, "{}");
        call(ls, "{}");
        // A file written to the workspace, by any tool.
        final ToolCallContext writing = ToolCallContext.builder(ToolSession.chat(1))
                .workspace(new SessionWorkspace(chatWorkspace, 1))
                .build();
        guard.run(table, new ToolCall("c", "table", "{\"path\":\"/outputs/t.csv\"}"), writing, (call, context) -> {
            context.workspace().orElseThrow().write(WorkspacePath.parse("/outputs/t.csv"), "a,b", null);
            return ToolResult.text("Wrote /outputs/t.csv");
        });
        assertThat(call(ls, "{}").text()).isEqualTo("a.md");
        assertThat(call(ls, "{}").text()).isEqualTo("a.md");
        // A tool that may change something.
        call(change, "{}");
        assertThat(call(ls, "{}").text()).isEqualTo("a.md");
        assertThat(call(ls, "{}").text()).isEqualTo("a.md");
        // A tool whose answer can change without a write (a query, as data arrives) isn't counted.
        final StroomTool query = tool("run_stroomql", ObjectSchema.builder().build(), call -> ToolResult.text("1"));
        for (int n = 0; n < 4; n++) {
            assertThat(call(query, "{}").text()).isEqualTo("1");
        }
        // A failed read isn't counted.
        final StroomTool grep = tool("grep", ObjectSchema.builder().build(), call -> ToolResult.error("Error: no"));
        for (int n = 0; n < 4; n++) {
            assertThat(call(grep, "{}").text()).doesNotContain("same call");
        }
        assertThat(call(ls, "{}").text()).contains("this same call 3 times");
    }

    @Test
    void aStackOverflowInsideACallIsTheCallsErrorNotTheRuns() {
        // Seen in testing: a regex over a mail gateway's 168 recipients overflowed the stack, and the chat's whole
        // run failed.
        final StroomTool tool = tool("build_data_splitter", ObjectSchema.builder().build(), call -> {
            throw new StackOverflowError();
        });

        final ToolResult result = call(tool, "{}");

        assertThat(result.isError()).isTrue();
        assertThat(result.text()).contains("build_data_splitter ran out of stack", "regular expression");
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

    // A tool taking lists, a number and a path, as the table tool does.
    private StroomTool table() {
        return tool("table", ObjectSchema.builder()
                .addProperty("eval", ArraySchema.builder().items(ObjectSchema.builder().build()).build())
                .addProperty("aggregates", ArraySchema.builder().items(ObjectSchema.builder().build()).build())
                .addProperty("groupBy", ArraySchema.builder().items(StringSchema.builder().build()).build())
                .addIntegerProperty("limit", "The most rows.")
                .addStringProperty("path", "The table.")
                .build(), call -> ToolResult.text("done"));
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

    private ToolResult read(final StroomTool tool, final String arguments, final ToolCallContext context) {
        return guard.run(tool, new ToolCall("c", tool.definition().name(), arguments), context, this::run);
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
