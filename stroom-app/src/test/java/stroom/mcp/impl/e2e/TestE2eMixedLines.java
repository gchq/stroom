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

/// A stream whose records come in several formats (syslog with key=value and plain messages, key=value lines,
/// timestamped text lines whose errors carry Java stack traces, and a stream that starts in a trace), onboarded end
/// to end without a hand-written XSLT. Seen in testing: such a stream's splitter split it on lines, the agent
/// hand-wrote the XSLT over many turns, and its documentation had no event type's own fields.
@SuppressWarnings("unchecked")
class TestE2eMixedLines extends AbstractE2eTest {

    private static final String SAMPLE = textResource("mixed-lines.log");
    private static final int RECORDS = 970;

    @Test
    void aMixedStreamIsOnboardedFromItsFormatsNotByHand() {
        final String build = "mixed-lines";
        final String feed = "MIXED-LINES";
        final Map<String, Object> profile = json("profile_sample", args("sample", SAMPLE));
        assertThat(profile).containsEntry("format", "mixed lines").containsEntry("continuation", true);

        createFeed(build, feed);
        final long raw = number(json("upload_sample", args("feed", feed, "sample", SAMPLE)).get("stream_id"));
        final Map<String, Object> ds = json("build_data_splitter", args("stream_ids", List.of(raw),
                "save_as", feed, "build", build));
        dump("build_data_splitter", ds);
        assertThat(number(ds.get("records"))).as("every record read").isEqualTo(RECORDS);
        assertThat(ds.get("unmatched_count") == null || number(ds.get("unmatched_count")) == 0).isTrue();
        assertThat(ds.get("saved")).as("saved: %s", ds.get("not_saved")).isNotNull();
        final Object splitter = ds.get("spec");
        assertThat(((Map<String, Object>) splitter).get("kind")).isEqualTo("alternatives");

        // The user's choice of how much of each stack trace to keep, given as the agent would after asking.
        final Map<String, Object> draft = json("draft_translation_mapping", args("stream_ids", List.of(raw),
                "source_name", "Mixed", "system_name", "Mixed", "environment", "Test", "splitter", splitter,
                "continuation_lines", 20));
        dump("draft_translation_mapping", draft);
        final Map<String, Object> mapping = decide(map(draft, "mapping"));
        final Map<String, Object> saved = agreed("build_translation_xslt", args("mapping", mapping,
                "schema_version", draft.get("schema_version"), "stream_ids", List.of(raw), "splitter", splitter,
                "build", build, "name", feed + "-Events"));
        assertThat(saved).as("the mapping checked against the sample and saved: %s", saved.get("problems"))
                .containsEntry("ok", true);
        final Map<String, Object> pipeline = json("create_pipeline", args("build", build, "name", feed + "-Events",
                "template_uuid", templateUuid("translation", "Event Data (Text)")));
        final String pipelineUuid = (String) pipeline.get("uuid");
        final Map<String, Object> stepped = json("step_sample", args("pipeline_uuid", pipelineUuid,
                "stream_ids", List.of(raw)));
        dump("step_sample", stepped);
        assertThat(stepped).as("stepped in Stroom: %s", stepped.get("groups")).containsEntry("verdict", "clean");
        assertThat(number(stepped.get("records_stepped"))).isPositive();

        // A record with a stack trace: its exception a field of its own, the trace cut to the lines chosen.
        String failed = null;
        for (int record = 0; record < 60 && failed == null; record++) {
            final String output = translationOutput(pipelineUuid, raw, record, 0);
            if (output.contains("Name=\"exception\"")) {
                failed = output;
            }
        }
        assertThat(failed).as("an Event with its record's exception").isNotNull()
                .containsPattern("Name=\"exception\" Value=\"[\\w.$]+(Exception|Error)\"");

        final Map<String, Object> doc = agreed("write_documentation", args("build", build,
                "pipeline_uuid", pipelineUuid, "stream_ids", List.of(raw),
                "markdown", "# " + feed + "-Events\n\n## Purpose and data\n\nMixed formats.\n\n## Field mapping\n\n"
                            + "(Generated.)\n",
                "change", "Created"));
        final String text = documentation(uuidOf(doc));
        assertThat(text).contains("### Common fields", "### Event types", "`EventTime/TimeCreated`");
    }

    // The agent's decisions beyond the draft: for the formats with none of their own (the draft's notes say which),
    // the stream's received time and a host; and each rule kept as Unknown on purpose.
    private static Map<String, Object> decide(final Map<String, Object> draft) {
        final Map<String, Object> mapping = new LinkedHashMap<>(draft);
        final String common = String.valueOf(mapping.get("common"));
        final List<Map<String, Object>> events = new ArrayList<>();
        for (final Map<String, Object> rule : (List<Map<String, Object>>) mapping.get("events")) {
            final Map<String, Object> kept = new LinkedHashMap<>(rule);
            final List<Map<String, Object>> fields = new ArrayList<>((List<Map<String, Object>>) rule.get("fields"));
            final String written = common + fields;
            if (!written.contains("EventTime/TimeCreated")) {
                fields.addFirst(Map.of("path", "EventTime/TimeCreated", "meta", "ReceivedTime"));
            }
            if (!written.contains("EventSource/Device/")) {
                fields.add(Map.of("path", "EventSource/Device/HostName", "value", "mixed-host"));
            }
            kept.put("fields", fields);
            if (String.valueOf(fields).contains("/Unknown/")) {
                kept.put("allow_unknown", "kept Unknown for the test");
            }
            events.add(kept);
        }
        mapping.put("events", events);
        return mapping;
    }
}
