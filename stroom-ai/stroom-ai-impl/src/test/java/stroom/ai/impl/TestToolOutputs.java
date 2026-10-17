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

import org.junit.jupiter.api.Test;

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
}
