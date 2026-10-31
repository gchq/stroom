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

package stroom.mcp.impl.e2e;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/// An extraction's regex checked in the dialect the generated XSLT runs it in (asked for by the user: it was checked
/// in another dialect, which refused `\p{L}`). A regex the build tool accepts must run in Stroom: `\p{L}+` reads
/// accented names from a CSV field, the XSLT generated from it steps clean in a real pipeline and writes them, and a
/// lookahead, which XPath has not, is refused before anything is saved. Driving the REAL tools on a real Stroom.
@SuppressWarnings("unchecked")
class TestE2eXPathRegex extends AbstractE2eTest {

    private static final String STAMP = String.valueOf(System.currentTimeMillis() % 100_000);
    private static final String SAMPLE = """
            time,host,message
            2026-10-01T10:00:00,ws01,user=José logged on
            2026-10-01T10:05:00,ws02,user=Zoë logged on
            2026-10-01T10:09:00,ws03,user=Łukasz logged on
            """;

    @Test
    void aUnicodeLetterClassExtractsAndStepsCleanInStroom() {
        final String build = "xpath-regex-" + STAMP;
        final String feed = "XPATH-REGEX-" + STAMP;
        final String template = templateUuid("translation", "Event Data (Text)");
        createFeed(build, feed);
        final long raw = upload(feed, SAMPLE);
        final Map<String, Object> converter = map(agreed("build_data_splitter", args("stream_ids", List.of(raw),
                "save_as", feed + "-CSV", "build", build)), "saved");
        final Map<String, Object> draft = json("draft_translation_mapping", args("stream_ids", List.of(raw),
                "build", build, "schema_version", VERSION));
        final Map<String, Object> mapping = withUserExtracted((Map<String, Object>) draft.get("mapping"),
                "^user=(\\p{L}+) ");

        final Map<String, Object> built = agreed("build_translation_xslt", args("mapping", mapping, "stream_ids",
                List.of(raw), "build", build, "name", feed + "-Events", "schema_version", VERSION));

        assertThat(built.get("ok")).as("accepted: %s", built.get("problems")).isEqualTo(true);
        final String xslt = (String) map(built, "saved").get("uuid");
        final String pipeline = (String) createPipeline(build, feed + "-Events", template,
                docProperty("dsParser", "textConverter", (String) converter.get("uuid"), "TextConverter"),
                docProperty("translationFilter", "xslt", xslt, "XSLT")).get("uuid");
        final Map<String, Object> stepped = stepSample(pipeline, List.of(raw));
        assertThat(stepped.get("verdict")).as("stepped: %s", stepped.get("groups")).isEqualTo("clean");
        final List<String> written = new ArrayList<>();
        for (int record = 0; record < 3; record++) {
            written.add(translationOutput(pipeline, raw, record));
        }
        assertThat(written).as("Stroom's Saxon read each name with \\p{L}").satisfiesExactly(
                output -> assertThat(output).contains("<Id>José</Id>"),
                output -> assertThat(output).contains("<Id>Zoë</Id>"),
                output -> assertThat(output).contains("<Id>Łukasz</Id>"));
    }

    @Test
    void aLookaheadIsRefusedBeforeAnythingIsSaved() {
        final String build = "xpath-lookahead-" + STAMP;
        final String feed = "XPATH-LOOKAHEAD-" + STAMP;
        createFeed(build, feed);
        final long raw = upload(feed, SAMPLE);
        agreed("build_data_splitter", args("stream_ids", List.of(raw), "save_as", feed + "-CSV", "build", build));
        final Map<String, Object> draft = json("draft_translation_mapping", args("stream_ids", List.of(raw),
                "build", build, "schema_version", VERSION));
        final Map<String, Object> mapping = withUserExtracted((Map<String, Object>) draft.get("mapping"),
                "^user=(\\w+)(?= logged)");

        final Map<String, Object> built = json("build_translation_xslt", args("mapping", mapping, "stream_ids",
                List.of(raw), "build", build, "name", feed + "-Events", "schema_version", VERSION));

        assertThat(built.get("ok")).isEqualTo(false);
        assertThat(String.valueOf(built.get("problems"))).contains("extract[0]", "lookaround");
        assertThat(built.get("saved")).as("nothing saved").isNull();
    }

    // The drafted mapping with the user read from the message by the regex, written as the event's user and as the
    // user who logged on, in a logon rule for the records (the draft has only its catch-all rule for them).
    private static Map<String, Object> withUserExtracted(final Map<String, Object> drafted, final String regex) {
        final Map<String, Object> mapping = new LinkedHashMap<>(drafted);
        mapping.put("extract", List.of(Map.of("field", "message", "regex", regex, "names", List.of("user"))));
        final List<Map<String, Object>> common = new ArrayList<>();
        for (final Map<String, Object> entry : (List<Map<String, Object>>) drafted.get("common")) {
            if (!"EventSource/User/Id".equals(entry.get("path"))) {
                common.add(entry);
            }
        }
        common.add(Map.of("path", "EventSource/User/Id", "field", "user"));
        mapping.put("common", common);
        mapping.put("events", List.of(Map.of("name", "logon",
                "when", List.of(Map.of("field", "message", "matches", " logged on$")),
                "fields", List.of(
                        Map.of("path", "EventDetail/TypeId", "value", "Logon"),
                        Map.of("path", "EventDetail/Authenticate/Action", "value", "Logon"),
                        Map.of("path", "EventDetail/Authenticate/User/Id", "field", "user")))));
        return mapping;
    }
}
