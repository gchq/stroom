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

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/// The Stroom MCP server's `dev/e2e_formats.py`, ported: each format sources send (the samples of its
/// `dev/format_samples.py`, copied to `format_samples.json`) onboarded end to end through the REAL tools on a real
/// Stroom, with what the agent relies on the server for: profile_sample names the format and parser;
/// build_data_splitter infers the Data Splitter from the uploaded sample and saves it; draft_translation_mapping
/// drafts the mapping; the agent's own decisions are made (a host when none is recognised, a regex for a free-form
/// message, names for headerless columns); build_translation_xslt checks it against the sample and saves it; the
/// pipeline is a child of the template the profile names; Stroom steps every record clean, each Event valid, with
/// the record's user and time (and, where the script checks them, other values as the source wrote them).
///
/// Every one of the script's 22 cases is covered, one test each.
@SuppressWarnings("unchecked")
class TestE2eFormats extends AbstractE2eTest {

    private static final List<String> USERS = List.of("alice", "bob", "carol");
    private static final String TEXT = "Event Data (Text)";
    private static final String JSON = "Event Data (JSON)";
    private static final String XML = "Event Data (XML)";
    private static final Map<String, Object> SSH = Map.of("field", "message",
            "regex", "^(Accepted|Failed) (\\S+) for (\\S+) from (\\S+) port (\\d+)",
            "names", List.of("outcome", "method", "user", "ip", "port"));
    private static final List<String> UTC_MS = List.of("2026-10-01T08:00:00.123Z", "2026-10-01T08:05:00.456Z",
            "2026-10-01T08:07:00.789Z");
    private static final List<String> UTC_ROUNDED = List.of("2026-10-01T08:00:00.123Z", "2026-10-01T08:05:00.457Z",
            "2026-10-01T08:07:01.000Z");
    private static final List<String> QUOTED_USERS = List.of("alice", "o'brien, pat", "carol");
    private static final Map<String, Case> CASES = cases();

    static Stream<String> caseNames() {
        return CASES.keySet().stream();
    }

    @ParameterizedTest
    @MethodSource("caseNames")
    void eachFormatIsOnboardedEndToEnd(final String name) {
        final Case formatCase = CASES.get(name);
        final String sample = (String) jsonResource("format_samples.json").get(name);
        final String build = "fmt-" + name.replace('_', '-');
        final String feed = "FMT-" + name.toUpperCase().replace('_', '-');
        final Map<String, Object> profile = json("profile_sample", args("sample", sample));
        assertThat(profile.get("format")).as("profiled: %s", profile.get("suggested_parser"))
                .isEqualTo(formatCase.format());
        createFeed(build, feed);
        final Map<String, Object> uploaded = json("upload_sample", args("feed", feed, "sample", sample,
                "headers", formatCase.headers()));
        final long raw = number(uploaded.get("stream_id"));
        Object splitter = null;
        if (TEXT.equals(formatCase.template())) {
            final Map<String, Object> ds = json("build_data_splitter", args("stream_ids", List.of(raw),
                    "spec", formatCase.spec(), "save_as", feed, "build", build));
            dump("build_data_splitter", ds);
            assertThat(number(ds.get("records"))).as("the Data Splitter parses every line").isEqualTo(3);
            assertThat(ds.get("unmatched_count") == null || number(ds.get("unmatched_count")) == 0).isTrue();
            assertThat(ds.get("saved")).isNotNull();
            splitter = ds.get("spec");
        } else if (formatCase.replaceParser() != null) {
            json("save_text_converter", args("build", build, "name", feed, "converter_type", "XML_FRAGMENT",
                    "code", map(profile, "text_converter").get("code")));
        }
        final Map<String, Object> draft = json("draft_translation_mapping", args("stream_ids", List.of(raw),
                "source_name", "Acme", "system_name", "Acme", "environment", "Test", "splitter", splitter));
        final Map<String, Object> mapping = decide(map(draft, "mapping"), formatCase);
        final Map<String, Object> saved = agreed("build_translation_xslt", args("mapping", mapping,
                "schema_version", draft.get("schema_version"), "stream_ids", List.of(raw), "splitter", splitter,
                "build", build, "name", feed + "-Events"));
        assertThat(saved).as("the mapping checked against the sample and saved: %s", saved.get("problems"))
                .containsEntry("ok", true);
        assertThat(saved.get("saved")).isNotNull();
        final Map<String, Object> pipeline = json("create_pipeline", args("build", build, "name", feed + "-Events",
                "template_uuid", templateUuid("translation", formatCase.template()),
                "replace_parser", formatCase.replaceParser()));
        final String pipelineUuid = (String) pipeline.get("uuid");
        final Map<String, Object> stepped = json("step_sample", args("pipeline_uuid", pipelineUuid,
                "stream_ids", List.of(raw)));
        assertThat(stepped).as("stepped in Stroom: %s", stepped.get("groups")).containsEntry("verdict", "clean");
        assertThat(number(stepped.get("records_stepped"))).isEqualTo(3);
        final List<String> users = new ArrayList<>();
        final Map<String, List<String>> values = new LinkedHashMap<>();
        for (int record = 0; record < 3; record++) {
            final String output = translationOutput(pipelineUuid, raw, record, 0);
            final Map<String, Object> valid = json("check_events", args("events_xml", output));
            assertThat(map(valid, "schema")).as("record %s valid: %s", record, valid).containsEntry("valid", true);
            final Element event = descendants(parse(output).getDocumentElement(), "Event").stream()
                    .findFirst()
                    .orElse(null);
            assertThat(event).as("record %s gave an Event: %s", record, output).isNotNull();
            assertThat(textAt(event, "EventTime/TimeCreated")).as("record %s's time", record).isNotBlank();
            users.add(textAt(event, "EventSource/User/Id"));
            for (final String element : formatCase.values().keySet()) {
                values.computeIfAbsent(element, key -> new ArrayList<>()).add(descendants(event, element).stream()
                        .findFirst()
                        .map(Element::getTextContent)
                        .orElse(null));
            }
        }
        assertThat(users).as("each record's user").isEqualTo(formatCase.users());
        formatCase.values().forEach((element, expected) ->
                assertThat(values.get(element)).as("%s as the source wrote it", element).isEqualTo(expected));
    }

    // The agent's decisions beyond the draft: what only it (or the user) can say.
    private static Map<String, Object> decide(final Map<String, Object> draft, final Case formatCase) {
        final Map<String, Object> mapping = new LinkedHashMap<>(draft);
        final List<Map<String, Object>> common = new ArrayList<>(
                (List<Map<String, Object>>) mapping.getOrDefault("common", List.of()));
        final Set<String> paths = new HashSet<>();
        common.forEach(entry -> paths.add(String.valueOf(entry.get("path"))));
        formatCase.meta().forEach((path, attribute) -> {
            // A value the stream was sent with, not the records.
            common.add(Map.of("path", path, "meta", attribute));
            paths.add(path);
        });
        if (paths.stream().noneMatch(path -> path.startsWith("EventSource/Device/"))) {
            common.add(Map.of("path", "EventSource/Device/HostName", "value", "acme-gw01"));
        }
        if (!paths.contains("EventSource/User/Id")) {
            common.add(formatCase.userIsXpath()
                    ? Map.of("path", "EventSource/User/Id", "xpath", formatCase.user())
                    : Map.of("path", "EventSource/User/Id", "field", formatCase.user()));
        }
        mapping.put("common", common);
        if (formatCase.extract() != null) {
            mapping.put("extract", formatCase.extract());
        }
        if (formatCase.rules() != null) {
            mapping.put("events", formatCase.rules());
        }
        return mapping;
    }

    private static List<Map<String, Object>> logonRules(final String kindField,
                                                        final Object logon,
                                                        final String logoff,
                                                        final String user) {
        final List<Map<String, Object>> rules = new ArrayList<>();
        rules.add(Map.of("name", "logon",
                "when", List.of(logon instanceof List<?>
                        ? Map.of("field", kindField, "one_of", logon)
                        : Map.of("field", kindField, "equals", logon)),
                "fields", List.of(Map.of("path", "EventDetail/TypeId", "value", "Logon"),
                        Map.of("path", "EventDetail/Authenticate/Action", "value", "Logon"),
                        Map.of("path", "EventDetail/Authenticate/User/Id", "field", user))));
        if (logoff != null) {
            rules.add(Map.of("name", "logoff", "when", List.of(Map.of("field", kindField, "equals", logoff)),
                    "fields", List.of(Map.of("path", "EventDetail/TypeId", "value", "Logoff"),
                            Map.of("path", "EventDetail/Authenticate/Action", "value", "Logoff"),
                            Map.of("path", "EventDetail/Authenticate/User/Id", "field", user))));
        }
        return rules;
    }

    // Each case: the format the profile names, the template, the user field (the agent maps it where the draft
    // didn't), the users expected in order, and any decision the agent makes beyond the draft.
    private static Map<String, Case> cases() {
        final Map<String, Case> cases = new LinkedHashMap<>();
        cases.put("csv_quoted", Case.of("delimited", TEXT, "user", QUOTED_USERS)
                .withValues(Map.of("Description", List.of("Logged in, from the VPN", "Said \"bye\" and left",
                        "Plain message"))));
        cases.put("tsv", Case.of("delimited", TEXT, "user", USERS));
        cases.put("pipe", Case.of("delimited", TEXT, "user", USERS));
        // No header: the agent names the columns, and writes the rules on the named action column.
        cases.put("csv_noheader", Case.of("delimited", TEXT, "user", USERS)
                .withSpec(Map.of("kind", "delimited", "delimiter", ",", "header", List.of("time", "user", "src_ip",
                        "action")))
                .withRules(logonRules("action", "login", "logout", "user")));
        cases.put("csv_noheader_quoted", Case.of("delimited", TEXT, "user", QUOTED_USERS)
                .withSpec(Map.of("kind", "delimited", "delimiter", ",", "quote", "\"", "header",
                        List.of("time", "user", "src_ip", "action", "message")))
                .withValues(Map.of("Description", nullable("Logged in, from the VPN", "Said \"bye\" and left",
                        null))));
        // A free-form message: the agent writes the regex.
        cases.put("syslog3164_freeform", Case.of("syslog rfc3164", TEXT, "user", USERS)
                .withExtract(List.of(SSH))
                .withRules(logonRules("outcome", List.of("Accepted", "Failed"), null, "user")));
        cases.put("syslog5424_kv", Case.of("syslog rfc5424", TEXT, "user", nullable("alice", null, "carol")));
        cases.put("cef", Case.of("cef", TEXT, "suser", USERS));
        cases.put("syslog_cef", Case.of("cef", TEXT, "suser", USERS));
        cases.put("kv_quoted", Case.of("key=value", TEXT, "user", List.of("alice", "bob smith", "carol")));
        cases.put("json_lines", Case.of("json lines", JSON, "user.name", USERS));
        cases.put("json_array_nested", Case.of("json array", JSON, "actor.user", USERS));
        // Times converted to UTC with their milliseconds, whatever the offset or zone (asked for by the user); a
        // zone the source names kept as TimeZoneName; the sending host read from stream meta (MyHostName).
        cases.put("json_offsets", Case.of("json lines", JSON, "user.name", USERS)
                .withHeaders(Map.of("MyHostName", "gw-host-01"))
                .withMeta(Map.of("EventSource/Device/HostName", "MyHostName"))
                .withValues(Map.of("TimeCreated", UTC_MS, "HostName", List.of("gw-host-01", "gw-host-01",
                        "gw-host-01"))));
        cases.put("csv_zone_names", Case.of("delimited", TEXT, "user", USERS)
                .withValues(Map.of("TimeCreated", UTC_MS, "TimeZoneName", List.of("Australia/Sydney",
                        "Europe/London", "America/New_York"))));
        cases.put("json_epoch_fraction", Case.of("json lines", JSON, "user.name", USERS)
                .withValues(Map.of("TimeCreated", UTC_MS)));
        cases.put("json_micros", Case.of("json lines", JSON, "user.name", USERS)
                .withValues(Map.of("TimeCreated", UTC_ROUNDED)));
        cases.put("json_epoch_micros", Case.of("json lines", JSON, "user.name", USERS)
                .withValues(Map.of("TimeCreated", UTC_ROUNDED)));
        cases.put("json_epoch_s_micros", Case.of("json lines", JSON, "user.name", USERS)
                .withValues(Map.of("TimeCreated", UTC_ROUNDED)));
        cases.put("xml_records", Case.of("xml", XML, "user", USERS));
        cases.put("xml_attributes", Case.of("xml", XML, "@user", USERS));
        cases.put("xml_namespaced", Case.of("xml", XML, "User", USERS));
        cases.put("xml_fragments_ns", Case.of("xml fragments", XML, "EventData/Data[@Name='TargetUserName']", USERS)
                .withReplaceParser("XMLFragmentParser")
                .withUserIsXpath());
        return cases;
    }

    private static List<String> nullable(final String... values) {
        return Arrays.asList(values);
    }


    // --------------------------------------------------------------------------------


    /// A case of the script's CASES.
    private record Case(String format,
                        String template,
                        String user,
                        List<String> users,
                        boolean userIsXpath,
                        String replaceParser,
                        Map<String, Object> spec,
                        List<Map<String, Object>> extract,
                        List<Map<String, Object>> rules,
                        Map<String, String> headers,
                        Map<String, String> meta,
                        Map<String, List<String>> values) {

        static Case of(final String format, final String template, final String user, final List<String> users) {
            return new Case(format, template, user, users, false, null, null, null, null, null, Map.of(), Map.of());
        }

        Case withSpec(final Map<String, Object> newSpec) {
            return new Case(format, template, user, users, userIsXpath, replaceParser, newSpec, extract, rules,
                    headers, meta, values);
        }

        Case withExtract(final List<Map<String, Object>> newExtract) {
            return new Case(format, template, user, users, userIsXpath, replaceParser, spec, newExtract, rules,
                    headers, meta, values);
        }

        Case withRules(final List<Map<String, Object>> newRules) {
            return new Case(format, template, user, users, userIsXpath, replaceParser, spec, extract, newRules,
                    headers, meta, values);
        }

        Case withHeaders(final Map<String, String> newHeaders) {
            return new Case(format, template, user, users, userIsXpath, replaceParser, spec, extract, rules,
                    newHeaders, meta, values);
        }

        Case withMeta(final Map<String, String> newMeta) {
            return new Case(format, template, user, users, userIsXpath, replaceParser, spec, extract, rules,
                    headers, newMeta, values);
        }

        Case withValues(final Map<String, List<String>> newValues) {
            return new Case(format, template, user, users, userIsXpath, replaceParser, spec, extract, rules,
                    headers, meta, newValues);
        }

        Case withReplaceParser(final String parser) {
            return new Case(format, template, user, users, userIsXpath, parser, spec, extract, rules, headers, meta,
                    values);
        }

        Case withUserIsXpath() {
            return new Case(format, template, user, users, true, replaceParser, spec, extract, rules, headers, meta,
                    values);
        }
    }
}
