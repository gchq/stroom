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

package stroom.ai.impl;

import stroom.ai.impl.tools.LargeResults;
import stroom.util.json.JsonUtil;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TestToolOutputs {

    @Test
    void testIsLarge() {
        assertThat(ToolOutputs.isLarge(null)).isFalse();
        assertThat(ToolOutputs.isLarge("x".repeat(ToolOutputs.MAX_INLINE_CHARS))).isFalse();
        assertThat(ToolOutputs.isLarge("x".repeat(ToolOutputs.MAX_INLINE_CHARS + 1))).isTrue();
    }

    @Test
    void testOutputPath() {
        assertThat(ToolOutputs.outputPath(12, 34)).isEqualTo("/outputs/turn-12/call-34.txt");
    }

    @Test
    void testStub() {
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 100; i++) {
            sb.append("row ").append(i).append('\n');
        }
        final String output = sb.toString();

        final String stub = ToolOutputs.stub(output, "/outputs/turn-1/call-2.txt");

        assertThat(stub)
                .contains(output.length() + " characters, 100 lines")
                .contains("/outputs/turn-1/call-2.txt")
                .contains("these " + ToolOutputs.PREVIEW_LINES + " line(s)")
                .contains("row 0\nrow 1")
                .contains("row " + (ToolOutputs.PREVIEW_LINES - 1))
                .doesNotContain("row " + ToolOutputs.PREVIEW_LINES + "\n");
    }

    @Test
    void testStub_oneHugeLine() {
        final String output = "x".repeat(10_000);

        final String stub = ToolOutputs.stub(output, "/p");

        assertThat(stub)
                .contains("10000 characters, 1 lines")
                .contains("these 1 line(s)")
                .contains("x".repeat(ToolOutputs.PREVIEW_CHARS) + "…")
                .hasSizeLessThan(ToolOutputs.PREVIEW_CHARS + 600);
    }

    @Test
    void jsonOnOneLineIsSavedAFieldPerLine() {
        // As build_status gave it: one long line, so reading or searching the file by line found it all at once.
        final String output = "{\"build\":\"twitter-onboard\",\"documents\":[{\"name\":\"TWITTER\"}],"
                              + "\"size\":12.50,\"id\":12345678901234567890,\"next\":{\"step\":\"promoted\"}}";

        final String saved = ToolOutputs.forSaving(output);

        assertThat(saved.lines()).contains(
                "  \"build\" : \"twitter-onboard\",",
                "  \"size\" : 12.50,",
                "  \"id\" : 12345678901234567890,",
                "    \"step\" : \"promoted\"");
    }

    @Test
    void outputThatIsNotJsonIsSavedAsItIs() {
        assertThat(ToolOutputs.forSaving("row 1\nrow 2")).isEqualTo("row 1\nrow 2");
        assertThat(ToolOutputs.forSaving("{not json")).isEqualTo("{not json");
        assertThat(ToolOutputs.forSaving("\"text\"")).isEqualTo("\"text\"");
    }

    @Test
    void theStubOfJsonSaysWhatItHolds() {
        final String object = ToolOutputs.forSaving("{\"build\":\"b\",\"steps\":[],\"next\":{\"step\":\"x\"}}");
        assertThat(ToolOutputs.stub(object, "/p"))
                .contains("It is a JSON object with the fields build, steps (0 items), next (object: step).")
                .contains("`.next`");

        assertThat(ToolOutputs.stub("[1, 2, 3]", "/p")).contains("It is a JSON array of 3 items.");
        // Seen: `.name` guessed where the items' key was `field`, a jq call each time.
        final String fields = ToolOutputs.forSaving("{\"fields\": [{\"field\": \"user\", \"fill_rate\": 1.0}], "
                                                    + "\"hint\": \"x\"}");
        assertThat(ToolOutputs.stub(fields, "/p")).contains("It is a JSON object with the fields fields (1 items, each "
                                                            + "an object with field, fill_rate), hint.");
        assertThat(ToolOutputs.stub("[{\"a\": 1, \"b\": 2}]", "/p"))
                .contains("It is a JSON array of 1 items, each an object with a, b.");
        assertThat(ToolOutputs.stub("row 1", "/p")).doesNotContain("JSON");
    }

    @Test
    void theStubOfJsonWithManyFieldsListsTheFirst() {
        final StringBuilder sb = new StringBuilder("{");
        for (int i = 0; i < ToolOutputs.MAX_LISTED_FIELDS + 5; i++) {
            sb.append(i == 0
                    ? ""
                    : ",").append("\"f").append(i).append("\":").append(i);
        }
        final String output = sb.append('}').toString();

        assertThat(ToolOutputs.stub(output, "/p"))
                .contains("f0, f1")
                .contains("and 5 more")
                .doesNotContain("f" + ToolOutputs.MAX_LISTED_FIELDS + ",");
    }

    @Test
    void aReviewWithDevToolsIsGivenInFull() {
        // As propose_index_template gave it: about 10,000 characters, which was stubbed, so the template was never
        // shown to the user for them to confirm.
        final String output = reply("needs_review", "dev_tools", "PUT _index_template/x\n" + "{}".repeat(5_000),
                "review_id", "0123456789ab");

        assertThat(output).hasSizeGreaterThan(ToolOutputs.MAX_INLINE_CHARS);
        assertThat(ToolOutputs.mustShowInFull(output)).isTrue();
        assertThat(ToolOutputs.isLarge(output)).isFalse();
    }

    @Test
    void aReviewWithShowUserIsGivenInFull() {
        final String output = reply("needs_review", "show_user", "| rule | records |\n".repeat(500),
                "review_id", "0123456789ab");

        assertThat(ToolOutputs.mustShowInFull(output)).isTrue();
        assertThat(ToolOutputs.isLarge(output)).isFalse();
    }

    @Test
    void contentToShowIsGivenInFull() {
        // Seen in testing: classify_kinds' table of ten proposals saved to the workspace, and the agent retyped it,
        // shortened and restyled.
        final String output = reply("needs_showing", "table", "## Medium-confidence classifications\n".repeat(300),
                "hint", "Show the table to the user in your reply, as it is");

        assertThat(ToolOutputs.mustShowInFull(output)).isTrue();
        assertThat(ToolOutputs.isLarge(output)).isFalse();
    }

    @Test
    void anApprovalOrConfirmationIsGivenInFull() {
        final String approval = reply("needs_approval", "summary", "Promote the build",
                "details", "x".repeat(5_000), "approval_id", "appr-abc.def");
        final String confirmation = reply("needs_confirmation", "summary", "Use the template",
                "details", "x".repeat(5_000), "confirmation_id", "conf-abc.def");

        assertThat(ToolOutputs.isLarge(approval)).isFalse();
        assertThat(ToolOutputs.isLarge(confirmation)).isFalse();
    }

    @Test
    void otherLargeResultsAreStillStubbed() {
        // Another status, no status, a status that isn't text, a review given as an error, and JSON that isn't an
        // object.
        assertThat(ToolOutputs.isLarge(reply("needs_guidance", "question", "x".repeat(5_000))))
                .isTrue();
        assertThat(ToolOutputs.isLarge("{\"dev_tools\":\"" + "x".repeat(5_000) + "\"}")).isTrue();
        assertThat(ToolOutputs.isLarge("{\"status\":[\"needs_review\"],\"x\":\"" + "x".repeat(5_000) + "\"}"))
                .isTrue();
        assertThat(ToolOutputs.isLarge(McpToolAdapter.ERROR_PREFIX
                                       + reply("needs_review", "dev_tools", "x".repeat(5_000))))
                .isTrue();
        assertThat(ToolOutputs.isLarge("[\"needs_review\",\"" + "x".repeat(5_000) + "\"]")).isTrue();
        assertThat(ToolOutputs.mustShowInFull(null)).isFalse();
        assertThat(ToolOutputs.mustShowInFull("{not json")).isFalse();
    }

    @Test
    void aReviewOverTheCapIsStubbedSayingTheUserMustSeeIt() {
        final String output = reply("needs_review", "dev_tools", "x".repeat(ToolOutputs.MAX_SHOWN_IN_FULL_CHARS),
                "review_id", "0123456789ab", "hint", "Show the user this index template before anything else.");
        assertThat(ToolOutputs.isLarge(output)).isTrue();

        final String stub = ToolOutputs.stub(ToolOutputs.forSaving(output), "/outputs/turn-1/call-2.txt");

        assertThat(stub)
                .contains("too large to show in full")
                .contains("this is a needs_review result")
                .contains("must be shown its dev_tools exactly as it is")
                .contains("query `.dev_tools` with raw true")
                .contains("read_file")
                .contains("in the workspace file /outputs/turn-1/call-2.txt")
                .contains("review_id '0123456789ab'")
                .contains("Its hint: Show the user this index template before anything else.")
                .hasSizeLessThan(ToolOutputs.MAX_INLINE_CHARS);
    }

    @Test
    void theStubOfAnOtherResultSaysNothingOfAReview() {
        final String stub = ToolOutputs.stub(ToolOutputs.forSaving(reply("needs_guidance", "q", "x".repeat(5_000))),
                "/p");

        assertThat(stub).doesNotContain("IMPORTANT").doesNotContain("must be shown");
    }

    @Test
    void theStubOfAResultRepeatsTheNextAndHintItEndsWith() {
        // As draft_translation_mapping gives it: a large mapping, then what to do next, last.
        final Map<String, Object> result = new LinkedHashMap<>();
        result.put("mapping", Map.of("events", "x".repeat(5_000)));
        for (int i = 0; i < 50; i++) {
            result.put("field" + i, "value " + i);
        }
        result.put("next", Map.of("tool", "build_translation_xslt", "args", Map.of("build", "b1")));
        result.put("hint", "Save the mapping with build_translation_xslt, then step the sample.");
        final String saved = ToolOutputs.forSaving(JsonUtil.writeValueAsString(result, false));

        final String stub = ToolOutputs.stub(saved, "/outputs/turn-1/call-2.txt");

        // Beyond the preview's lines.
        assertThat(saved.indexOf("\"next\"")).isGreaterThan(ToolOutputs.PREVIEW_CHARS);
        assertThat(stub)
                .contains("Its next: {")
                .contains("\"tool\":\"build_translation_xslt\"")
                .contains("\"build\":\"b1\"")
                .contains("Its hint: Save the mapping with build_translation_xslt, then step the sample.")
                .doesNotContain("IMPORTANT")
                .hasSizeLessThan(ToolOutputs.MAX_INLINE_CHARS);
    }

    @Test
    void aLongNextOrHintIsCutInTheStub() {
        final String output = reply("ok", "data", "x".repeat(5_000), "next", "n".repeat(5_000),
                "hint", "h".repeat(5_000));

        final String stub = ToolOutputs.stub(ToolOutputs.forSaving(output), "/p");

        assertThat(stub)
                .contains("Its next: " + "n".repeat(ToolOutputs.MAX_NEXT_CHARS) + "…")
                .contains("Its hint: " + "h".repeat(ToolOutputs.MAX_HINT_CHARS) + "…")
                .doesNotContain("n".repeat(ToolOutputs.MAX_NEXT_CHARS + 1))
                .doesNotContain("h".repeat(ToolOutputs.MAX_HINT_CHARS + 1));
    }

    @Test
    void theStubOfAResultWithoutNextOrHintSaysNothingOfThem() {
        final String stub = ToolOutputs.stub(ToolOutputs.forSaving(reply("ok", "data", "x".repeat(5_000))), "/p");

        assertThat(stub).doesNotContain("Its next:").doesNotContain("Its hint:").doesNotContain("what to do next");
        assertThat(ToolOutputs.stub("[\"next\", \"" + "x".repeat(5_000) + "\"]", "/p"))
                .doesNotContain("Its next:");
    }

    @Test
    void aDraftsNotesAreGivenWholeAndFirst() {
        // Seen in chat 184: 13 notes of a draft cut after 11 ("… 2 more"), the one offering a System/Name per source
        // type among those cut; the agent asked the user for one name.
        final Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "drafted");
        final List<String> notes = new ArrayList<>();
        for (int i = 0; i < 13; i++) {
            notes.add("Note " + i + ": " + "a decision the agent has to take before building. ".repeat(8));
        }
        result.put("notes", notes);
        result.put("problems", List.of("a problem"));
        result.put("filler", "x".repeat(20_000));
        final String saved = ToolOutputs.forSaving(JsonUtil.writeValueAsString(result, false));

        final String stub = ToolOutputs.stub(saved, "/outputs/turn-1/call-2.txt");

        assertThat(stub).contains("Note 12: ").doesNotContain("… 1 more", "… 2 more", "query `.notes[");
        assertThat(stub.indexOf("notes:\n")).as("notes before problems").isLessThan(stub.indexOf("problems:\n"));
    }

    @Test
    void theStubOfAResultWithoutAnOutlineStillCarriesWhatToActOn() {
        // Seen in chat 183: a catalogue check's problems and unplaced fields, and a draft's notes, past the preview's
        // lines, read from the workspace with jq in 60 calls.
        final Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "checked");
        for (int i = 0; i < 60; i++) {
            result.put("field" + i, "value " + i);
        }
        final Map<String, Object> check = new LinkedHashMap<>();
        check.put("counts", Map.of("Authenticate-Logon", 2));
        check.put("problems", List.of(Map.of("type_id", "Authenticate-Logon", "problem", "its regex never matches")));
        check.put("unplaced", List.of(Map.of("field", "src_port", "problem", "not placed in any event type")));
        check.put("typing", Map.of("Process-Run", Map.of("typed", 2, "data", 9,
                "could_type", Map.of("fields", List.of("pid -> Process/Id")))));
        result.put("check", check);
        result.put("notes", List.of("Ask the user for EventSource/System/Name.", "Decide the action element."));
        result.put("warnings", List.of("EventTime is left out when time is empty."));
        result.put("refused", false);
        result.put("problems", List.of());
        result.put("filler", "x".repeat(5_000));
        final String saved = ToolOutputs.forSaving(JsonUtil.writeValueAsString(result, false));
        assertThat(saved.indexOf("\"check\"")).isGreaterThan(ToolOutputs.PREVIEW_CHARS);

        final String stub = ToolOutputs.stub(saved, "/outputs/turn-1/call-2.txt");

        assertThat(stub)
                .contains("What it says to act on, in full")
                .containsPattern("check.problems:\n- \\{[^\n]*\"problem\":\"its regex never matches\"")
                .containsPattern("check.unplaced:\n- \\{[^\n]*\"field\":\"src_port\"")
                .contains("check.typing:\n- Process-Run: {")
                .contains("\"could_type\":{\"fields\":[\"pid -> Process/Id\"]}")
                .contains("notes:\n- Ask the user for EventSource/System/Name.\n- Decide the action element.")
                .contains("warnings:\n- EventTime is left out when time is empty.")
                // Empty or false parts aren't said.
                .doesNotContain("refused:")
                .doesNotContain("\nproblems:")
                // Still the preview, as there is no outline.
                .contains("It starts with these");
    }

    @Test
    void aFailedVerificationsFailedChecksAreGivenInTheStub() {
        // Seen in chat 188: verify_index's result past the inline limit, its stub showing its first lines (the
        // dashboard and the first check, which passed), and the agent grepped and read 130 lines of the saved
        // result to find the checks that failed.
        final List<Map<String, Object>> checks = new ArrayList<>();
        for (int i = 0; i < 9; i++) {
            checks.add(Map.of("check", "events with EventId " + i, "expected", 1, "returned", 1, "pass", true,
                    "sample", List.of(Map.of("raw", "x".repeat(400)))));
        }
        final Map<String, Object> failed = new LinkedHashMap<>();
        failed.put("check", "events with source.ip 10.0.0.1");
        failed.put("expected", 4);
        failed.put("returned", 0);
        failed.put("pass", false);
        checks.add(failed);
        final Map<String, Object> result = new LinkedHashMap<>();
        result.put("dashboard", Map.of("name", "EVAL-VERIFY", "saved", true));
        result.put("passed", false);
        result.put("checks", checks);
        result.put("failed", List.of(failed));
        final String saved = ToolOutputs.forSaving(JsonUtil.writeValueAsString(result, false));
        assertThat(saved.indexOf("source.ip 10.0.0.1")).isGreaterThan(ToolOutputs.PREVIEW_CHARS);

        assertThat(ToolOutputs.stub(saved, "/p"))
                .containsPattern("failed:\n- \\{[^\n]*\"check\":\"events with source.ip 10.0.0.1\"[^\n]*"
                                 + "\"returned\":0");
    }

    @Test
    void aNeedsDescriptionsRoundCarriesItsFieldsToDescribe() {
        final Map<String, Object> round = new LinkedHashMap<>();
        round.put("status", "needs_descriptions");
        round.put("written", false);
        round.put("hint", "NOT WRITTEN. Call write_documentation again with field_descriptions.");
        round.put("markdown", "m".repeat(5_000));
        final Map<String, Object> field = new LinkedHashMap<>();
        field.put("field", "src_ip");
        field.put("from", "Source/Device/IPAddress");
        round.put("fields", List.of(field));
        final Map<String, Object> other = new LinkedHashMap<>(round);
        other.put("status", "ok");

        assertThat(ToolOutputs.stub(ToolOutputs.forSaving(JsonUtil.writeValueAsString(round, false)), "/p"))
                .contains("fields:\n- {\"field\":\"src_ip\",\"from\":\"Source/Device/IPAddress\"}");
        // A result's fields are otherwise often only an inventory.
        assertThat(ToolOutputs.stub(ToolOutputs.forSaving(JsonUtil.writeValueAsString(other, false)), "/p"))
                .doesNotContain("What it says to act on");
    }

    @Test
    void theToolsOutlineTakesThePlaceOfThePreview() {
        final Map<String, Object> result = new LinkedHashMap<>();
        result.put("mapping", Map.of("events", "x".repeat(20_000)));
        result.put("notes", List.of("Ask the user for EventSource/System/Name."));
        result.put("hint", "Build it as drafted.");
        final Map<String, Object> outline = new LinkedHashMap<>();
        outline.put("whole_result", Map.of("saved", "/outputs/a_tool/acme.json", "outlined", List.of("mapping")));
        outline.put("mapping_outline", Map.of("events", List.of(Map.of("name", "Authenticate-Logon"))));
        outline.put("notes", List.of("Ask the user for EventSource/System/Name."));
        outline.put("hint", "The draft is too large to show whole here. Build it as drafted.");

        final String stub = ToolOutputs.stub(ToolOutputs.forSaving(JsonUtil.writeValueAsString(result, false)),
                "/outputs/turn-1/call-2.txt", outline);

        assertThat(stub)
                .contains("The tool's outline of it, in place of its first lines")
                .contains("\"mapping_outline\":{\"events\":[{\"name\":\"Authenticate-Logon\"}]}")
                .contains("\"saved\":\"/outputs/a_tool/acme.json\"")
                .contains("the whole is also in /outputs/turn-1/call-2.txt")
                // The outline's hint, which says how its parts are outlined.
                .contains("Its hint: The draft is too large to show whole here. Build it as drafted.")
                .contains("notes:\n- Ask the user for EventSource/System/Name.")
                .doesNotContain("It starts with these")
                .doesNotContain("x".repeat(100));
        // Said once: the notes above, not again in the outline.
        assertThat(stub.split("Ask the user for EventSource", -1)).hasSize(2);
    }

    @Test
    void aResultToShowTheUserIsStubbedAsBeforeWithoutAnOutline() {
        final String output = reply("needs_review", "dev_tools", "x".repeat(ToolOutputs.MAX_SHOWN_IN_FULL_CHARS),
                "review_id", "0123456789ab", "problems", "a problem", "hint", "Show the user this template.");
        final String saved = ToolOutputs.forSaving(output);

        final String stub = ToolOutputs.stub(saved, "/p", Map.of("dev_tools", "an outline", "review_id", "r"));

        assertThat(stub).isEqualTo(ToolOutputs.stub(saved, "/p"))
                .doesNotContain("What it says to act on")
                .doesNotContain("outline")
                .contains("It starts with these");
        // Given in full under the cap, as before.
        assertThat(ToolOutputs.isLarge(reply("needs_review", "dev_tools", "x".repeat(10_000)))).isFalse();
    }

    @Test
    void hugePartsToActOnAreCappedSayingHowManyMoreAndWhere() {
        final List<String> problems = new ArrayList<>();
        final Map<String, Object> typing = new LinkedHashMap<>();
        for (int i = 0; i < 2_000; i++) {
            problems.add("problem " + i + ": the regex of Process-Code" + i + " never matches");
            typing.put("Process-Code" + i, Map.of("typed", 1, "data", 12));
        }
        final Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "checked");
        result.put("error", "e".repeat(10_000));
        result.put("problems", problems);
        result.put("unplaced", problems);
        result.put("check", Map.of("problems", problems, "unplaced", problems, "typing", typing));
        // Short enough to leave room for the problems' cut list (notes come first, up to MAX_NOTES_CHARS).
        result.put("notes", List.of("n".repeat(3_000)));
        result.put("warnings", problems);
        result.put("user_chose", List.of("Process"));
        final String path = "/outputs/turn-1/call-2.txt";

        final String stub = ToolOutputs.stub(ToolOutputs.forSaving(JsonUtil.writeValueAsString(result, false)), path);

        final String actOn = stub.substring(stub.indexOf("What it says to act on"), stub.indexOf("It starts with"));
        assertThat(actOn.length()).isLessThanOrEqualTo(ToolOutputs.MAX_ACT_ON_CHARS + 1_000);
        assertThat(actOn)
                .contains("error:\n" + "e".repeat(ToolOutputs.MAX_ACT_ON_PART_CHARS) + "… (cut: the rest is in "
                          + path + ", jq query `.error` with raw true)")
                .containsPattern("… \\d+ more: read them from " + path
                                 + " with the jq tool, query `\\.problems\\[\\d+:]`")
                .contains("(Also to act on, not shown here for room: ")
                .contains("user_chose")
                .doesNotContain("e".repeat(ToolOutputs.MAX_ACT_ON_PART_CHARS + 1));
    }

    @Test
    void anOutlinePastItsBudgetIsCutSayingWhere() {
        final Map<String, Object> outline = new LinkedHashMap<>();
        for (int i = 0; i < 200; i++) {
            outline.put("rule" + i, "r".repeat(200));
        }
        final String output = "{\"rules\":\"" + "x".repeat(50_000) + "\"}";

        final String stub = ToolOutputs.stub(ToolOutputs.forSaving(output), "/p", outline);

        assertThat(stub).contains("… (the outline is cut here, at " + LargeResults.CHAT_OUTLINE_CHARS
                                  + " characters: the rest of the result is in /p)")
                .contains("\"rule0\"")
                .doesNotContain("\"rule199\"");
        assertThat(stub.length()).isLessThan(LargeResults.CHAT_OUTLINE_CHARS + 2_000);
    }

    // A JSON object of the given status and field name/value pairs, on one line, as an MCP server gives it.
    private static String reply(final String status, final String... namesAndValues) {
        final Map<String, String> fields = new LinkedHashMap<>();
        fields.put(ToolOutputs.STATUS_FIELD, status);
        for (int i = 0; i + 1 < namesAndValues.length; i += 2) {
            fields.put(namesAndValues[i], namesAndValues[i + 1]);
        }
        return JsonUtil.writeValueAsString(fields, false);
    }

    @Test
    void aTableTheChatShowsIsntGivenToTheModelToRepeat() {
        // Seen in chats 168 and 169: the agent retyped the table the chat had just shown the user.
        final String shown = "{\"status\": \"needs_showing\", \"table\": \"| # | Kind |\\n| 1 | Logon |\", "
                             + "\"hint\": \"Call confirm=true\"}";

        final String forModel = ToolOutputs.forModel(shown);

        assertThat(forModel).doesNotContain("| 1 | Logon |").contains(ToolOutputs.SHOWN_TABLE)
                .contains("Call confirm=true");
        // Anything else as it is, a review's records too (the agent shows those).
        final String review = "{\"status\": \"needs_review\", \"show_user\": \"records\", \"table\": \"t\"}";
        assertThat(ToolOutputs.forModel(review)).isEqualTo(review);
        assertThat(ToolOutputs.forModel("plain text")).isEqualTo("plain text");
    }

    @Test
    void anIndexTemplateUnderReviewIsTheChatsToShow() {
        // Seen in testing: told to print the template and call again without waiting, the agent called again, and
        // the form asked the user to confirm a template they were never shown. The chat shows it itself.
        final String review = "{\"status\": \"needs_review\", \"dev_tools\": \"PUT _index_template/acme\\n{}\", "
                              + "\"review_id\": \"r1\", \"notes\": [\"keyword from the example\"]}";

        final String forModel = ToolOutputs.forModel(review);

        assertThat(forModel).doesNotContain("PUT _index_template").contains(ToolOutputs.SHOWN_TABLE)
                .contains("\"review_id\":\"r1\"").contains("keyword from the example");
        // A template agreed (no review) is the agent's to give for committing.
        final String agreed = "{\"agreed\": true, \"dev_tools\": \"PUT _index_template/acme\"}";
        assertThat(ToolOutputs.forModel(agreed)).isEqualTo(agreed);
    }
}
