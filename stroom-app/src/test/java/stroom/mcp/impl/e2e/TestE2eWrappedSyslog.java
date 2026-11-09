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

import stroom.ai.impl.tools.sample.ToolJson;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/// A source's syslog lines inside a wrapper, onboarded end to end and run by Stroom itself: a relay's header (on some
/// of a stream's lines), a collector's words, a shipper's JSON holding the line or what follows the host in an RFC
/// 5424 header, and JSON encoded twice. Each is told by its format alone.
@SuppressWarnings("unchecked")
class TestE2eWrappedSyslog extends AbstractE2eTest {

    private static final String TEXT_TEMPLATE = "Event Data (Text)";
    private static final String JSON_TEMPLATE = "Event Data (JSON)";
    // The source's own lines, each with the kind of event in its pairs.
    private static final List<String> SOURCE = List.of(
            "<38>Nov  9 09:59:58 bastion01 sshd[2211]: user=alice action=login src=192.0.2.5",
            "<38>Nov  9 09:59:59 bastion01 sshd[2215]: user=bob action=logout src=192.0.2.9",
            "<38>Nov  9 10:00:00 bastion02 sshd[2219]: user=carol action=login src=192.0.2.7",
            "<38>Nov  9 10:00:04 bastion02 sshd[2223]: user=dave action=logout src=192.0.2.8");

    @Test
    void relayedAndDirectLinesInOneStreamAreEachTheSourcesOwn() {
        final String sample = String.join("\n",
                "<13>Nov  9 10:00:10 relay01 " + SOURCE.get(0),
                "<13>Nov  9 10:00:11 relay01 " + SOURCE.get(1),
                SOURCE.get(2),
                SOURCE.get(3)) + "\n";
        final Onboarded onboarded = onboardText("wrapped-relayed", "WRAPPED-RELAYED", sample);
        // One regex, so a login is one kind of event however its line came.
        assertThat(onboarded.splitter()).containsEntry("kind", "regex");

        final String relayed = translationOutput(onboarded.pipeline(), onboarded.raw(), 0);
        assertThat(relayed).contains("<HostName>bastion01</HostName>", "-11-09T09:59:58",
                "Name=\"relay_host\" Value=\"relay01\"");
        final String direct = translationOutput(onboarded.pipeline(), onboarded.raw(), 2);
        assertThat(direct).contains("<HostName>bastion02</HostName>", "-11-09T10:00:00")
                .doesNotContain("relay_host");
        processedClean(onboarded);
    }

    @Test
    void everyLineRelayedIsTheSourcesOwnWithTheRelayKeptAsData() {
        final String sample = SOURCE.stream()
                .map(line -> "<13>1 2026-11-09T10:00:10.250Z relay01 forwarder - - - " + line)
                .collect(Collectors.joining("\n", "", "\n"));
        final Onboarded onboarded = onboardText("wrapped-relay-all", "WRAPPED-RELAY-ALL", sample);
        assertThat(onboarded.splitter()).containsEntry("kind", "syslog").containsEntry("name_prefix", "relay_");

        // The relay's time is finer, but when it passed the line on.
        assertThat(translationOutput(onboarded.pipeline(), onboarded.raw(), 0))
                .contains("<HostName>bastion01</HostName>", "-11-09T09:59:58",
                        "Name=\"relay_host\" Value=\"relay01\"");
        processedClean(onboarded);
    }

    @Test
    void aCollectorsWordsBeforeEachLineAreKeptAsData() {
        final String sample = SOURCE.stream()
                .map(line -> "2026-11-09T10:00:10.250Z collector01 " + line)
                .collect(Collectors.joining("\n", "", "\n"));
        final Onboarded onboarded = onboardText("wrapped-collector", "WRAPPED-COLLECTOR", sample);
        assertThat(onboarded.splitter()).containsEntry("kind", "regex");

        assertThat(translationOutput(onboarded.pipeline(), onboarded.raw(), 0))
                .contains("<HostName>bastion01</HostName>", "-11-09T09:59:58",
                        "Name=\"relay_word2\" Value=\"collector01\"");
        processedClean(onboarded);
    }

    @Test
    void theEndOfAnRfc5424HeaderAShipperKeptAsOneValueIsReadIntoItsParts() {
        // A shipper's JSON array: the header read up to the host into keys of their own, the rest one value.
        final String sample = ToolJson.dumps(List.of(
                shipped("14", "2026-11-09T21:32:22+11:00",
                        "Connection - - [app@32473 event_id=\"0x0004\" action=\"login\" user=\"EXAMPLE\\\\alice\" "
                        + "event=\"User [EXAMPLE\\\\alice\\] from [192.0.2.10\\] opened [Projects\\].\"][meta "
                        + "sequenceId=\"1\"] ﻿User [EXAMPLE\\alice] opened [Projects]."),
                shipped("14", "2026-11-09T21:33:40+11:00",
                        "Connection - - [app@32473 event_id=\"0x0005\" action=\"logout\" user=\"bob\" "
                        + "event=\"User [bob\\] from [192.0.2.11\\] left.\"][meta sequenceId=\"2\"] "
                        + "﻿User [bob] from [192.0.2.11] left."),
                shipped("14", "2026-11-09T21:34:05+11:00",
                        "Connection 11886 - [app@32473 event_id=\"0x0004\" action=\"login\" user=\"carol\" "
                        + "event=\"User [carol\\] opened [Projects\\].\"] User [carol] opened [Projects].")));
        final Onboarded onboarded = onboardJson("wrapped-tail", "WRAPPED-TAIL", sample, false);

        assertThat(String.valueOf(onboarded.mapping().get("extract"))).contains("body.structured", "body.app");
        final String output = translationOutput(onboarded.pipeline(), onboarded.raw(), 0);
        // The event's time and host from the shipper's keys, the parts of the value read in the XSLT.
        assertThat(output).contains("<HostName>nas01</HostName>", "2026-11-09T10:32:22.000Z", "0x0004",
                "User [EXAMPLE\\alice] opened [Projects].");
        // The byte order mark before the message taken out by the regex, as it is in the draft.
        assertThat(output).doesNotContain("﻿");
        processedClean(onboarded);
    }

    @Test
    void aSyslogLineInAJsonFieldIsReadIntoItsParts() {
        // A container runtime's JSON lines, each holding the source's syslog line.
        final String sample = SOURCE.stream()
                .map(line -> ToolJson.dumps(Map.of("log", line, "stream", "stdout")))
                .collect(Collectors.joining("\n", "", "\n"));
        final Onboarded onboarded = onboardJson("wrapped-held", "WRAPPED-HELD", sample, true);

        assertThat(translationOutput(onboarded.pipeline(), onboarded.raw(), 0))
                .contains("<HostName>bastion01</HostName>", "-11-09T09:59:58");
        processedClean(onboarded);
    }

    @Test
    void jsonEncodedTwiceInAFieldIsReadByTheXslt() {
        final String sample = Stream.of("login", "logout", "login")
                .map(action -> ToolJson.dumps(Map.of("ts", "2026-11-09T10:00:00.000Z", "host", "web01",
                        "detail", ToolJson.dumps(ToolJson.dumps(Map.of("action", action, "user", "alice"))))))
                .collect(Collectors.joining("\n", "", "\n"));
        final Onboarded onboarded = onboardJson("wrapped-twice", "WRAPPED-TWICE", sample, true);

        assertThat(onboarded.mapping().get("json_fields")).isEqualTo(List.of("detail"));
        assertThat(translationOutput(onboarded.pipeline(), onboarded.raw(), 0)).contains("alice", "Logon");
        processedClean(onboarded);
    }

    @Test
    void jsonLinesEncodedTwiceAreReadALineARecord() {
        final String sample = Stream.of("login", "logout", "login")
                .map(action -> ToolJson.dumps(ToolJson.dumps(Map.of("ts", "2026-11-09T10:00:00.000Z",
                        "host", "web01", "action", action, "user", "alice"))))
                .collect(Collectors.joining("\n", "", "\n"));
        final String build = "wrapped-lines";
        final String feed = "WRAPPED-LINES";
        createFeed(build, feed);
        final long raw = upload(feed, sample);
        final Map<String, Object> draft = json("draft_translation_mapping", args("stream_ids", List.of(raw),
                "source_name", "Lines", "system_name", "Lines", "environment", "Test"));
        dump("draft_translation_mapping", draft);
        final Map<String, Object> splitter = (Map<String, Object>) draft.get("splitter");
        assertThat(splitter).as("a Data Splitter a line a record").containsEntry("kind", "regex");
        final Map<String, Object> ds = json("build_data_splitter", args("stream_ids", List.of(raw),
                "spec", splitter, "save_as", feed, "build", build));
        assertThat(ds.get("saved")).as("saved: %s", ds.get("not_saved")).isNotNull();
        final Onboarded onboarded = finish(build, feed, raw, draft, splitter, TEXT_TEMPLATE, List.of());

        assertThat(translationOutput(onboarded.pipeline(), onboarded.raw(), 0))
                .contains("<HostName>web01</HostName>", "alice");
        processedClean(onboarded);
    }

    // A text sample: its splitter inferred and saved, its mapping drafted and saved, and stepped clean in Stroom.
    private Onboarded onboardText(final String build, final String feed, final String sample) {
        createFeed(build, feed);
        final long raw = upload(feed, sample);
        final Map<String, Object> ds = json("build_data_splitter", args("stream_ids", List.of(raw),
                "save_as", feed, "build", build));
        dump("build_data_splitter", ds);
        assertThat(ds.get("saved")).as("saved: %s", ds.get("not_saved")).isNotNull();
        assertThat(ds.get("unmatched_count") == null || number(ds.get("unmatched_count")) == 0).isTrue();
        final Map<String, Object> splitter = (Map<String, Object>) ds.get("spec");
        final Map<String, Object> draft = json("draft_translation_mapping", args("stream_ids", List.of(raw),
                "source_name", "Wrapped", "system_name", "Wrapped", "environment", "Test", "splitter", splitter));
        dump("draft_translation_mapping", draft);
        return finish(build, feed, raw, draft, splitter, TEXT_TEMPLATE, List.of());
    }

    // A JSON sample: no text converter; its mapping drafted and saved, and stepped clean in Stroom.
    private Onboarded onboardJson(final String build, final String feed, final String sample, final boolean lines) {
        createFeed(build, feed);
        final long raw = upload(feed, sample);
        final Map<String, Object> draft = json("draft_translation_mapping", args("stream_ids", List.of(raw),
                "source_name", "Wrapped", "system_name", "Wrapped", "environment", "Test"));
        dump("draft_translation_mapping", draft);
        return finish(build, feed, raw, draft, null, JSON_TEMPLATE,
                List.of(valueProperty("jsonParser", "addRootObject", lines)));
    }

    private Onboarded finish(final String build,
                             final String feed,
                             final long raw,
                             final Map<String, Object> draft,
                             final Map<String, Object> splitter,
                             final String template,
                             final List<Map<String, Object>> properties) {
        final Map<String, Object> mapping = decide(map(draft, "mapping"));
        final Map<String, Object> arguments = new LinkedHashMap<>(args("mapping", mapping,
                "schema_version", draft.get("schema_version"), "stream_ids", List.of(raw), "build", build,
                "name", feed + "-Events"));
        if (splitter != null) {
            arguments.put("splitter", splitter);
        }
        final Map<String, Object> saved = agreed("build_translation_xslt", arguments);
        dump("build_translation_xslt", saved);
        assertThat(saved).as("the mapping checked against the sample and saved: %s", saved.get("problems"))
                .containsEntry("ok", true);
        final List<Map<String, Object>> set = new ArrayList<>(properties);
        set.add(docProperty("translationFilter", "xslt", uuidOf(map(saved, "saved")), "XSLT"));
        final String pipeline = uuidOf(agreed("create_pipeline", args("build", build, "name", feed + "-Events",
                "template_uuid", templateUuid("translation", template), "set_properties", set)));
        final Map<String, Object> stepped = stepSample(pipeline, List.of(raw));
        dump("step_sample", stepped);
        assertThat(stepped).as("stepped in Stroom: %s", stepped.get("groups")).containsEntry("verdict", "clean");
        return new Onboarded(raw, pipeline, splitter, mapping);
    }

    // Processed by Stroom, its Events stream valid.
    private void processedClean(final Onboarded onboarded) {
        final Map<String, Object> gate = process(onboarded.pipeline(), List.of(onboarded.raw()));
        assertThat(gate).as("processed: %s", gate.get("streams")).containsEntry("gate", "pass");
        final long eventsId = number(((List<?>) list(gate, "streams").getFirst().get("events")).getFirst());
        final Map<String, Object> checked = json("check_events", args("stream_ids", List.of(eventsId)));
        assertThat(map(checked, "schema")).as("Events valid: %s", checked).containsEntry("valid", true);
    }

    // The agent's decision beyond the draft: each rule kept as Unknown on purpose.
    private static Map<String, Object> decide(final Map<String, Object> draft) {
        final Map<String, Object> mapping = new LinkedHashMap<>(draft);
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

    private static Map<String, Object> shipped(final String priority, final String time, final String body) {
        final Map<String, Object> record = new LinkedHashMap<>();
        record.put("priority", priority);
        record.put("version", "1");
        record.put("timestamp", time);
        record.put("hostname", "nas01");
        record.put("body", body);
        return record;
    }


    // --------------------------------------------------------------------------------


    private record Onboarded(long raw, String pipeline, Map<String, Object> splitter, Map<String, Object> mapping) {

    }
}
