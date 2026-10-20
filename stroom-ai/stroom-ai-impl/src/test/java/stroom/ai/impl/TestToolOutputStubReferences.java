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
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/// The exact stub the model is given in place of a tool result too large to give in full ([ToolOutputs#stub]),
/// with the result as it is saved to the workspace ([ToolOutputs#forSaving]), in reference files (see
/// [GoldenFiles]): plain text, a JSON object, a JSON array, and a review the user must be shown that is over the
/// cap for results shown in full.
class TestToolOutputStubReferences {

    private static final String FOLDER = "tools/stub/";
    private static final String PATH = ToolOutputs.outputPath(3, 42);
    private static final int SAVED_LINES = 40;
    private static final int SAVED_LINE_CHARS = 300;

    @Test
    void plainText() {
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 400; i++) {
            sb.append("row ").append(i).append(": host fw").append(i % 7).append(", user u").append(i % 13)
                    .append('\n');
        }

        assertStub("plain-text", sb.toString());
    }

    @Test
    void aJsonObject() {
        final Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("build", "twitter-onboard");
        fields.put("size", 12.5);
        for (int i = 0; i < 40; i++) {
            fields.put("field" + i, "value " + i + " " + "x".repeat(100));
        }
        fields.put("next", Map.of("step", "promoted"));

        assertStub("json-object", JsonUtil.writeValueAsString(fields, false));
    }

    @Test
    void aJsonArray() {
        final StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < 200; i++) {
            sb.append(i == 0
                    ? ""
                    : ",").append("{\"id\":").append(i).append(",\"host\":\"fw").append(i % 7).append("\"}");
        }

        assertStub("json-array", sb.append(']').toString());
    }

    @Test
    void aReviewOverTheCapForResultsShownInFull() {
        final Map<String, String> fields = new LinkedHashMap<>();
        fields.put(ToolOutputs.STATUS_FIELD, "needs_review");
        fields.put("dev_tools", "PUT _index_template/x\n" + "{\"a\":1}\n".repeat(ToolOutputs.MAX_SHOWN_IN_FULL_CHARS
                                                                            / 8 + 10));
        fields.put("review_id", "0123456789ab");
        fields.put("hint", "Show the user this index template before anything else.");
        final String output = JsonUtil.writeValueAsString(fields, false);
        assertThat(ToolOutputs.isLarge(output)).isTrue();

        assertStub("needs-review-over-the-cap", output);
    }

    // The stub of the output, as saved, and the start of what is saved (its first lines, each cut short, as the
    // model only sees what it reads of the file).
    private static void assertStub(final String name, final String output) {
        assertThat(ToolOutputs.isLarge(output)).isTrue();
        final String saved = ToolOutputs.forSaving(output);
        final List<String> start = saved.lines()
                .limit(SAVED_LINES)
                .map(line -> line.length() > SAVED_LINE_CHARS
                        ? line.substring(0, SAVED_LINE_CHARS) + " [line cut here, " + line.length() + " characters]"
                        : line)
                .toList();
        final String text = "=== Stub given to the model ===\n" + ToolOutputs.stub(saved, PATH)
                            + "\n\n=== Saved to " + PATH + " (" + saved.length() + " characters, "
                            + saved.lines().count() + " lines; the first " + start.size() + ") ===\n"
                            + String.join("\n", start) + "\n";
        GoldenFiles.assertMatches(FOLDER + name + ".txt", text);
    }
}
