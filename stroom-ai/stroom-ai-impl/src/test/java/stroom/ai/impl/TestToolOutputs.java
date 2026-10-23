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

import stroom.util.json.JsonUtil;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
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
                .contains("It is a JSON object with the fields build, steps, next.")
                .contains("`.next`");

        assertThat(ToolOutputs.stub("[1, 2, 3]", "/p")).contains("It is a JSON array of 3 items.");
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

    // A JSON object of the given status and field name/value pairs, on one line, as an MCP server gives it.
    private static String reply(final String status, final String... namesAndValues) {
        final Map<String, String> fields = new LinkedHashMap<>();
        fields.put(ToolOutputs.STATUS_FIELD, status);
        for (int i = 0; i + 1 < namesAndValues.length; i += 2) {
            fields.put(namesAndValues[i], namesAndValues[i + 1]);
        }
        return JsonUtil.writeValueAsString(fields, false);
    }
}
