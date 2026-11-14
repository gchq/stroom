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

package stroom.ai.impl.tools.sample;

import stroom.ai.impl.tools.draft.DraftMapping;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/// JSON string values Stroom's JSONParser would cut (longer than its stringTruncateLength) or its XML writer leave
/// unescaped (holding a NUL), said before processing. Seen: mail bodies of 20,000 to 46,000 characters in audit
/// JSON lines, cut at 10,000 with a Warning, and the agent then lowered the wrong property (maxStringLength).
class TestJsonStringValues {

    private static final String LONG = "x".repeat(12_000);

    @Test
    void aJsonLineWithAValueOverTheLimitIsReportedWithItsPathAndLength() {
        final String lines = line(SampleProfiler.map("id", 1, "body", "short")) + "\n"
                             + line(SampleProfiler.map("id", 2, "body", LONG)) + "\n"
                             + line(SampleProfiler.map("id", 3, "body", "short too"));

        final Map<String, Object> profile = SampleProfiler.profile(lines);

        assertThat(profile).containsEntry("format", SampleProfiler.JSON_LINES);
        assertThat(profile.get(JsonStringValues.LONG_STRINGS)).isEqualTo(List.of(
                SampleProfiler.map("field", "body", "length", 12_000, "record", 2)));
        assertThat((String) profile.get(JsonStringValues.LONG_STRINGS_NOTE))
                .contains("'body', 12000 characters, record 2")
                .contains("cuts each at 10000 characters (its stringTruncateLength) and logs a Warning")
                .contains("jsonParser.stringTruncateLength")
                .contains("cap it in the mapping")
                .contains("Leave maxStringLength alone");
        assertThat(profile).doesNotContainKey(JsonStringValues.NUL_STRINGS);
    }

    @Test
    void valuesUpToTheLimitAreNotReported() {
        final String lines = line(SampleProfiler.map("body", "x".repeat(10_000))) + "\n"
                             + line(SampleProfiler.map("body", "short"));

        final Map<String, Object> profile = SampleProfiler.profile(lines);

        assertThat(profile).containsEntry("format", SampleProfiler.JSON_LINES)
                .doesNotContainKey(JsonStringValues.LONG_STRINGS)
                .doesNotContainKey(JsonStringValues.LONG_STRINGS_NOTE);
    }

    @Test
    void nestedValuesAndArrayItemsAreNamedByTheirPath() {
        final Object record = SampleProfiler.map("event", SampleProfiler.map("Item", SampleProfiler.map(
                "Body", SampleProfiler.map("Content", LONG))), "attachments", List.of(
                SampleProfiler.map("name", "a.txt", "text", "y".repeat(15_000))), "tags", List.of("z".repeat(11_000)));

        final List<Map<String, Object>> found = JsonStringValues.longStrings(List.of(record),
                JsonStringValues.DEFAULT_TRUNCATE_LENGTH);

        // Longest first.
        assertThat(found).containsExactly(
                SampleProfiler.map("field", "attachments[].text", "length", 15_000, "record", 1),
                SampleProfiler.map("field", "event.Item.Body.Content", "length", 12_000, "record", 1),
                SampleProfiler.map("field", "tags[]", "length", 11_000, "record", 1));
    }

    @Test
    void aJsonArrayIsCheckedToo() {
        final String array = ToolJson.dumps(List.of(SampleProfiler.map("id", 1, "msg", "ok"),
                SampleProfiler.map("id", 2, "msg", LONG)));

        final Map<String, Object> profile = SampleProfiler.profile(array);

        assertThat(profile).containsEntry("format", SampleProfiler.JSON_ARRAY);
        assertThat(profile.get(JsonStringValues.LONG_STRINGS)).isEqualTo(List.of(
                SampleProfiler.map("field", "msg", "length", 12_000, "record", 2)));
    }

    @Test
    void eachFieldIsReportedOnceAtItsLongestAndAtMostFiveFields() {
        final List<Object> records = new ArrayList<>();
        records.add(SampleProfiler.map("body", "x".repeat(10_001)));
        records.add(SampleProfiler.map("body", "x".repeat(10_500)));
        for (int i = 0; i < 6; i++) {
            records.add(SampleProfiler.map("f" + i, "x".repeat(10_001 + i)));
        }

        final List<Map<String, Object>> found = JsonStringValues.longStrings(records,
                JsonStringValues.DEFAULT_TRUNCATE_LENGTH);

        assertThat(found).hasSize(JsonStringValues.MOST_REPORTED);
        assertThat(found.getFirst()).isEqualTo(SampleProfiler.map("field", "body", "length", 10_500, "record", 2));
        assertThat(found).extracting(m -> m.get("field")).doesNotContain("f0");
    }

    @Test
    void aValueHoldingANulIsReported() {
        // ToolJson writes the NUL (U+0000) escaped, as the source's JSON held it.
        final String lines = line(SampleProfiler.map("body", "<p>before" + (char) 0 + "after &nbsp;</p>")) + "\n"
                             + line(SampleProfiler.map("body", "plain"));

        final Map<String, Object> profile = SampleProfiler.profile(lines);

        assertThat(lines).contains("\\u0000");
        assertThat(profile.get(JsonStringValues.NUL_STRINGS)).isEqualTo(List.of("body"));
        assertThat((String) profile.get(JsonStringValues.NUL_STRINGS_NOTE))
                .contains("body")
                .contains("transform strip_control");
    }

    @Test
    void linesReadByADataSplitterAreNotChecked() {
        // With a line that isn't JSON the lines go through a Data Splitter and json-to-xml(), not the JSONParser.
        final StringBuilder lines = new StringBuilder(line(SampleProfiler.map("body", LONG))).append('\n')
                .append("not json at all\n");
        for (int i = 0; i < 5; i++) {
            lines.append(line(SampleProfiler.map("body", "short " + i))).append('\n');
        }

        final Map<String, Object> profile = SampleProfiler.profile(lines.toString());

        assertThat(profile).containsEntry("format", SampleProfiler.JSON_LINES)
                .containsKey("malformed_lines")
                .doesNotContainKey(JsonStringValues.LONG_STRINGS);
    }

    @Test
    void theDraftSaysSo() {
        final String lines = line(SampleProfiler.map("time", "2026-11-10T00:00:03Z", "op", "Send", "body", LONG))
                             + "\n" + line(SampleProfiler.map("time", "2026-11-10T00:00:04Z", "op", "Send", "body",
                "short"));

        final Map<String, Object> draft = DraftMapping.draft(List.of(lines), "", null, null, null, null, null);

        assertThat(String.valueOf(draft.get("notes"))).contains("stringTruncateLength").contains("'body'");
    }

    private static String line(final Object record) {
        return ToolJson.dumps(record);
    }
}
