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

/// A stream whose records come in several formats (syslog with key=value and plain messages, key=value lines,
/// timestamped text lines whose errors carry Java stack traces, and a stream that starts in a trace), onboarded end
/// to end without a hand-written XSLT. Seen in testing: such a stream's splitter split it on lines, the agent
/// hand-wrote the XSLT over many turns, and its documentation had no event type's own fields.
@SuppressWarnings("unchecked")
class TestE2eMixedLines extends AbstractE2eTest {

    private static final String SAMPLE = textResource("mixed-lines.log");
    private static final int RECORDS = 970;
    private static final Pattern TIME_CREATED = Pattern.compile("<TimeCreated>([^<]+)</TimeCreated>");

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

    @Test
    void aLogOfTimesWithNoDateTakesItsHeadersDateAndRollsOverMidnight() {
        // dateless-lines-generator.py: "Log opened 2026-10-11", 120 time-only lines crossing midnight (stack traces on
        // errors), "Day changed to 2026-10-12" part-way, run by Stroom itself.
        final String build = "dateless-lines";
        final String feed = "DATELESS-LINES";
        createFeed(build, feed);
        final String sample = textResource("dateless-lines.log");
        final long raw = number(json("upload_sample", args("feed", feed, "sample", sample)).get("stream_id"));
        final Map<String, Object> ds = json("build_data_splitter", args("stream_ids", List.of(raw),
                "save_as", feed, "build", build));
        assertThat(number(ds.get("records"))).isEqualTo(122);
        assertThat(ds.get("saved")).as("saved: %s", ds.get("not_saved")).isNotNull();
        final Object splitter = ds.get("spec");
        final Map<String, Object> draft = json("draft_translation_mapping", args("stream_ids", List.of(raw),
                "source_name", "App", "system_name", "App", "environment", "Test", "splitter", splitter,
                "continuation_lines", 0));
        dump("draft_translation_mapping", draft);
        assertThat(String.valueOf(draft.get("mapping"))).contains("header_date", "rollover=true");
        final Map<String, Object> saved = agreed("build_translation_xslt", args("mapping", decide(map(draft,
                        "mapping")), "schema_version", draft.get("schema_version"), "stream_ids", List.of(raw),
                "splitter", splitter, "build", build, "name", feed + "-Events"));
        assertThat(saved).as("saved: %s", saved.get("problems")).containsEntry("ok", true);
        final String pipelineUuid = (String) json("create_pipeline", args("build", build, "name", feed + "-Events",
                "template_uuid", templateUuid("translation", "Event Data (Text)"))).get("uuid");
        final Map<String, Object> stepped = json("step_sample", args("pipeline_uuid", pipelineUuid,
                "stream_ids", List.of(raw)));
        assertThat(stepped).as("stepped in Stroom: %s", stepped.get("groups")).containsEntry("verdict", "clean");
        // Stepped: the header's date (carried by the splitter), and after the second header its date. Stepping scopes
        // stroom:put and stroom:get to the record (SteppingController), so a time past midnight before the second
        // header steps on the header's day: rolled over in processing only.
        assertThat(timeOf(translationOutput(pipelineUuid, raw, 1))).isEqualTo("2026-10-11T23:50:09.000Z");
        assertThat(timeOf(translationOutput(pipelineUuid, raw, 46))).isEqualTo("2026-10-11T00:00:12.315Z");
        assertThat(timeOf(translationOutput(pipelineUuid, raw, 82))).isEqualTo("2026-10-12T00:07:32.560Z");

        // Processed: every line an Event, the headers none, each time after the one before.
        final Map<String, Object> gate = process(pipelineUuid, List.of(raw));
        assertThat(gate).as("processed: %s", gate.get("streams")).containsEntry("gate", "pass");
        final long eventsId = number(((List<?>) list(gate, "streams").getFirst().get("events")).getFirst());
        // fetch_events gives as many records as fit a reply, and says where the next starts.
        final List<String> times = new ArrayList<>();
        Object from = 0;
        while (from != null) {
            final Map<String, Object> page = json("fetch_events", args("streamId", eventsId, "firstRecord", from,
                    "recordCount", 200));
            ((List<?>) page.get("records")).stream()
                    .map(record -> record instanceof final Map<?, ?> fieldsOf
                            ? String.valueOf(fieldsOf.get("data"))
                            : String.valueOf(record))
                    .map(TestE2eMixedLines::timeOf)
                    .forEach(times::add);
            from = page.get("next_first_record");
        }
        assertThat(times).hasSize(120).isSorted();
        assertThat(times.getFirst()).isEqualTo("2026-10-11T23:50:09.000Z");
        assertThat(times.getLast()).isEqualTo("2026-10-12T00:14:59.833Z");
    }

    // ### One event type written in two line formats: one way of recognising it a format, each placed in the
    // variant whose records it recognises (evaluated, not guessed), and processing agrees with the check's counts.
    @Test
    void oneEventTypeWrittenInTwoLineFormatsIsPlacedInEachByEvaluation() {
        final String build = "mixed-types";
        final String feed = "MIXED-TYPES";
        createFeed(build, feed);
        final StringBuilder sample = new StringBuilder();
        for (int i = 0; i < 18; i++) {
            final String action = i % 3 == 2
                    ? "logout"
                    : "login";
            sample.append(i % 2 == 0
                    ? "time=2026-11-06T09:%02d:00Z action=%s user=user%d\n".formatted(i, action, i % 4)
                    : "Nov  6 09:%02d:30 web%d sshd[%d]: %s for user%d from 10.0.0.%d\n".formatted(i, i % 3,
                            100 + i, action, i % 4, i));
        }
        final List<Long> raw = List.of(upload(feed, sample.toString()));
        surveyed(build, feed, raw);
        final Map<String, Object> splitter = Map.of("kind", "alternatives", "parts", List.of(
                Map.of("kind", "syslog", "variant", "syslog"),
                Map.of("kind", "key_value", "delimiter", " ", "variant", "key_value")));
        final Map<String, Object> ds = json("build_data_splitter", args("stream_ids", raw, "save_as", feed,
                "build", build, "spec", splitter));
        assertThat(ds.get("saved")).as("saved: %s", ds.get("not_saved")).isNotNull();

        final Map<String, Object> checked = proposed(build, raw, List.of(
                withKeys(eventType("Authenticate-Logon", "Authenticate", "high", when("action", "equals", "login"),
                        when("message", "starts_with", "login for")), "action", "Logon", "affected", "host",
                        "affected_as", "Device"),
                withKeys(eventType("Authenticate-Logoff", "Authenticate", "high", when("action", "equals",
                        "logout"), when("message", "starts_with", "logout for")), "action", "Logoff", "affected",
                        "host", "affected_as", "Device")));
        final Map<String, Object> logons = map(map(checkOf(checked), "counts"), "Authenticate-Logon");
        assertThat(number(logons.get("records"))).isEqualTo(12);
        assertThat((List<Integer>) logons.get("by_way")).containsExactly(6, 6);
        confirmedCatalogue(build);

        final Map<String, Object> draft = json("draft_translation_mapping", args("stream_ids", raw, "build", build,
                "source_name", "Mixed", "system_name", "Mixed", "environment", "Test", "splitter", ds.get("spec")));
        dump("draft_translation_mapping two formats", draft);
        final List<String> logonRules = list(map(draft, "mapping"), "events").stream()
                .map(AbstractE2eTest::jsonOf)
                .filter(rule -> rule.contains("Authenticate-Logon"))
                .toList();
        assertThat(logonRules).as("a rule a way of recognising it, each in its own variant").hasSize(2)
                .anySatisfy(rule -> assertThat(rule).contains("\"equals\":\"key_value\"", "\"action\"")
                        .doesNotContain("starts_with"))
                .anySatisfy(rule -> assertThat(rule).contains("\"equals\":\"syslog\"", "\"starts_with\"")
                        .doesNotContain("\"action\",\"equals\""));

        final Map<String, Object> saved = agreed("build_translation_xslt", args("mapping", decide(map(draft,
                        "mapping")), "schema_version", draft.get("schema_version"), "stream_ids", raw, "splitter",
                ds.get("spec"), "build", build, "name", feed + "-Events"));
        assertThat(saved).as("saved: %s", saved.get("problems")).containsEntry("ok", true);
        final String pipeline = (String) json("create_pipeline", args("build", build, "name", feed + "-Events",
                "template_uuid", templateUuid("translation", "Event Data (Text)"))).get("uuid");
        assertThat(stepSample(pipeline, raw).get("verdict")).as("stepped clean before processing").isEqualTo("clean");
        final Map<String, Object> gate = process(pipeline, raw);
        assertThat(gate).as("processed: %s", gate.get("streams")).containsEntry("gate", "pass");
        final long eventsId = number(((List<?>) list(gate, "streams").getFirst().get("events")).getFirst());
        final String events = storedText(eventsId);
        assertThat(events.split("<TypeId>Authenticate-Logon</TypeId>", -1)).hasSize(13);
        assertThat(events.split("<TypeId>Authenticate-Logoff</TypeId>", -1)).hasSize(7);
    }

    private static String timeOf(final String events) {
        final Matcher time = TIME_CREATED.matcher(events);
        return time.find()
                ? time.group(1)
                : null;
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
