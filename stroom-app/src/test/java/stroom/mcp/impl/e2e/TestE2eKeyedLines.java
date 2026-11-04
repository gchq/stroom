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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/// An auditd log onboarded end to end: its events several lines sharing a `msg=audit(...)` envelope, tied into
/// records by Stroom's own Data Splitter, each line's fields under its type, each event timed by its envelope. Asked
/// for by the user; lines of events written interleaved are not put back together (the user's decision).
@SuppressWarnings("unchecked")
class TestE2eKeyedLines extends AbstractE2eTest {

    // auditd-lines-generator.py: 529 lines, 151 events, one pair interleaved, so 152 records.
    private static final String SAMPLE = textResource("auditd-lines.log");
    private static final int RECORDS = 152;
    private static final Pattern TIME_CREATED = Pattern.compile("<TimeCreated>([^<]+)</TimeCreated>");

    @Test
    void anAuditdLogsEventsAreEachOneEventAtTheirEnvelopesTime() {
        final String build = "auditd-lines";
        final String feed = "AUDITD-LINES";
        final Map<String, Object> profile = json("profile_sample", args("sample", SAMPLE));
        assertThat(profile).containsEntry("key_field", "msg").containsEntry("lead_field", "type");
        assertThat(number(profile.get("interleaved"))).isEqualTo(1);

        createFeed(build, feed);
        final long raw = number(json("upload_sample", args("feed", feed, "sample", SAMPLE)).get("stream_id"));
        final Map<String, Object> ds = json("build_data_splitter", args("stream_ids", List.of(raw),
                "save_as", feed, "build", build));
        dump("build_data_splitter", ds);
        assertThat(number(ds.get("records"))).as("every record read").isEqualTo(RECORDS);
        assertThat(ds.get("saved")).as("saved: %s", ds.get("not_saved")).isNotNull();
        final Object splitter = ds.get("spec");
        assertThat(((Map<String, Object>) splitter).get("record_key")).isNotNull();

        final Map<String, Object> draft = json("draft_translation_mapping", args("stream_ids", List.of(raw),
                "source_name", "Auditd", "system_name", "Auditd", "environment", "Test", "splitter", splitter));
        dump("draft_translation_mapping", draft);
        assertThat(String.valueOf(map(draft, "mapping").get("common"))).contains("time_format=epoch_s");
        final Map<String, Object> saved = agreed("build_translation_xslt", args("mapping", decide(map(draft,
                        "mapping")), "schema_version", draft.get("schema_version"), "stream_ids", List.of(raw),
                "splitter", splitter, "build", build, "name", feed + "-Events"));
        assertThat(saved).as("saved: %s", saved.get("problems")).containsEntry("ok", true);
        final String pipelineUuid = (String) json("create_pipeline", args("build", build, "name", feed + "-Events",
                "template_uuid", templateUuid("translation", "Event Data (Text)"))).get("uuid");
        final Map<String, Object> stepped = json("step_sample", args("pipeline_uuid", pipelineUuid,
                "stream_ids", List.of(raw)));
        dump("step_sample", stepped);
        assertThat(stepped).as("stepped in Stroom: %s", stepped.get("groups")).containsEntry("verdict", "clean");
        // Record 5: a SYSCALL of six lines (msg=audit(1791709234.390:4506)), its executable read from its own line.
        final String syscall = translationOutput(pipelineUuid, raw, 5);
        assertThat(timeOf(syscall)).isEqualTo("2026-10-11T09:00:34.390Z");
        assertThat(syscall).contains("/usr/bin/sudo");

        // Processed: an Event per record, each at its envelope's time.
        final Map<String, Object> gate = process(pipelineUuid, List.of(raw));
        assertThat(gate).as("processed: %s", gate.get("streams")).containsEntry("gate", "pass");
        final long eventsId = number(((List<?>) list(gate, "streams").getFirst().get("events")).getFirst());
        final List<String> times = new ArrayList<>();
        Object from = 0;
        while (from != null) {
            final Map<String, Object> page = json("fetch_events", args("streamId", eventsId, "firstRecord", from,
                    "recordCount", 200));
            ((List<?>) page.get("records")).stream()
                    .map(record -> record instanceof final Map<?, ?> fieldsOf
                            ? String.valueOf(fieldsOf.get("data"))
                            : String.valueOf(record))
                    .map(TestE2eKeyedLines::timeOf)
                    .forEach(times::add);
            from = page.get("next_first_record");
        }
        assertThat(times).hasSize(RECORDS).doesNotContainNull();
        assertThat(times.getFirst()).isEqualTo("2026-10-11T09:00:02.136Z");
    }

    private static String timeOf(final String events) {
        final Matcher time = TIME_CREATED.matcher(events);
        return time.find()
                ? time.group(1)
                : null;
    }

    // The agent's decisions beyond the draft: a host for the Events (a SYSCALL names none), and each rule kept as
    // Unknown on purpose.
    private static Map<String, Object> decide(final Map<String, Object> draft) {
        final Map<String, Object> mapping = new LinkedHashMap<>(draft);
        final List<Object> common = new ArrayList<>((List<Object>) mapping.get("common"));
        common.removeIf(entry -> String.valueOf(entry).contains("EventSource/Device/HostName"));
        common.add(Map.of("path", "EventSource/Device/HostName", "value", "audit-host"));
        mapping.put("common", common);
        final List<Map<String, Object>> events = new ArrayList<>();
        for (final Map<String, Object> rule : (List<Map<String, Object>>) mapping.get("events")) {
            final Map<String, Object> kept = new LinkedHashMap<>(rule);
            if (String.valueOf(rule.get("fields")).contains("/Unknown/")) {
                kept.put("allow_unknown", "kept Unknown for the test");
            }
            events.add(kept);
        }
        mapping.put("events", events);
        return mapping;
    }
}
