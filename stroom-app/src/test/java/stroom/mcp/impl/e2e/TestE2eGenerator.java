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

import stroom.util.json.JsonUtil;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/// The Stroom MCP server's `dev/e2e_generator.py`, ported: for each of the translation suite's samples (and a
/// firewall's CSV), the translation is built the way a model would with build_translation_xslt, from a field mapping
/// and no hand-written XSLT, through the REAL tools on a real Stroom. Then the pipeline is created in a build, every
/// record stepped and each Event validated against the instance's event-logging schema and the quality checks. The
/// XML sample carries a record no rule matches, which must come back as the generator's warning, not be dropped.
///
/// Every one of the script's five mappings (firewall, csv, json, xml, syslog) is covered, one test each.
@SuppressWarnings("unchecked")
class TestE2eGenerator extends AbstractE2eTest {

    private static final List<Map<String, Object>> SYSTEM = List.of(
            Map.of("path", "EventSource/System/Name", "value", "E2E"),
            Map.of("path", "EventSource/System/Environment", "value", "Dev"),
            Map.of("path", "EventSource/Generator", "value", "e2e-generator"));
    private static final String XML_SAMPLE = TestE2eTranslation.XML.sample().replace("</logons>",
            "<logon><when>2026-09-28 12:02:00</when><who>judy</who><host>ws10</host><addr>10.0.2.10</addr>"
            + "<result>LOCKED</result></logon></logons>");
    private static final String FIREWALL_SAMPLE = textResource("firewall.csv");
    private static final Map<String, Object> ALLOW_DENY = Map.of("field", "action",
            "map", Map.of("ALLOW", "true", "DENY", "false"));
    private static final Map<String, Object> LOGIN = Map.of("field", "action",
            "map", Map.of("LOGIN_SUCCESS", "true", "LOGIN_FAILED", "false"));

    @ParameterizedTest
    @ValueSource(strings = {"firewall", "csv", "json", "xml", "syslog"})
    void aTranslationFromAMappingStepsCleanAndValidates(final String format) {
        final Map<String, Object> mapping = mappings().get(format);
        final Map<String, Object> generated = json("build_translation_xslt", args("mapping", mapping,
                "schema_version", VERSION));
        assertThat(generated).as("generated from the mapping: %s", generated.get("problems"))
                .containsEntry("ok", true);
        dump("warnings", generated.get("warnings"));
        final String build = "gen-" + format;
        final String feed = "GEN-" + format.toUpperCase();
        createFeed(build, feed);
        final long raw = upload(feed, sample(format));
        final List<Map<String, Object>> properties = new ArrayList<>();
        final String converter = converter(format);
        if (converter != null) {
            final Map<String, Object> saved = json("save_text_converter", args("build", build, "name", feed,
                    "converter_type", "DATA_SPLITTER", "code", converter));
            properties.add(docProperty("dsParser", "textConverter", (String) saved.get("uuid"), "TextConverter"));
        }
        final Map<String, Object> xslt = json("save_xslt", args("build", build, "name", feed + "-Events",
                "code", generated.get("xslt")));
        properties.add(docProperty("translationFilter", "xslt", (String) xslt.get("uuid"), "XSLT"));
        if ("json".equals(format)) {
            properties.add(valueProperty("jsonParser", "addRootObject", false));
        }
        final Map<String, Object> pipeline = json("create_pipeline", args("build", build, "name", feed + "-Events",
                "template_uuid", templateUuid("translation", template(format)), "set_properties", properties));
        final String pipelineUuid = (String) pipeline.get("uuid");
        final Map<String, Object> sample = json("step_sample", args("pipeline_uuid", pipelineUuid,
                "stream_ids", List.of(raw)));
        final List<Map<String, Object>> groups = list(sample, "groups");
        if ("xml".equals(format)) {
            final List<Map<String, Object>> unmatched = groups.stream()
                    .filter(group -> JsonUtil.writeValueAsString(group, false)
                            .contains("No event mapping matched record"))
                    .toList();
            assertThat(unmatched).as("the LOCKED record is reported by the generator's warning: %s", groups)
                    .singleElement()
                    .satisfies(group -> assertThat(number(group.get("count"))).isOne());
            assertThat(groups).as("nothing blocking").noneSatisfy(group ->
                    assertThat(group).containsEntry("class", "blocking"));
        } else {
            assertThat(sample).as("stepped clean: %s", groups).containsEntry("verdict", "clean");
        }
        for (int record = 0; record < number(sample.get("records_stepped")); record++) {
            final String output = translationOutput(pipelineUuid, raw, record, 0);
            if (!output.contains("<Event>")) {
                continue;
            }
            final Map<String, Object> checked = json("check_events", args("events_xml", output));
            assertThat(map(checked, "schema")).as("record %s valid: %s", record, checked)
                    .containsEntry("valid", true);
            assertThat(checked).as("record %s passes the quality checks: %s", record, checked.get("quality"))
                    .containsEntry("ok", true);
        }
    }

    private static String sample(final String format) {
        return switch (format) {
            case "firewall" -> FIREWALL_SAMPLE;
            case "csv" -> CSV_SAMPLE;
            case "json" -> TestE2eTranslation.JSON.sample();
            case "xml" -> XML_SAMPLE;
            default -> TestE2eTranslation.SYSLOG.sample();
        };
    }

    private static String converter(final String format) {
        return switch (format) {
            case "firewall", "csv" -> CSV_SPLITTER;
            case "syslog" -> TestE2eTranslation.SYSLOG_SPLITTER;
            default -> null;
        };
    }

    private static String template(final String format) {
        return switch (format) {
            case "json" -> "Event Data (JSON)";
            case "xml" -> "Event Data (XML)";
            default -> "Event Data (Text)";
        };
    }

    private static List<Map<String, Object>> logon(final String user, final Map<String, Object> success) {
        final List<Map<String, Object>> fields = new ArrayList<>(List.of(
                Map.of("path", "EventDetail/TypeId", "value", "Logon"),
                Map.of("path", "EventDetail/Authenticate/Action", "value", "Logon"),
                Map.of("path", "EventDetail/Authenticate/User/Id", "field", user)));
        if (success != null) {
            final Map<String, Object> outcome = new LinkedHashMap<>(success);
            outcome.put("path", "EventDetail/Authenticate/Outcome/Success");
            fields.add(outcome);
        }
        return fields;
    }

    private static List<Map<String, Object>> common(final String timeField,
                                                    final String pattern,
                                                    final String host,
                                                    final String ip,
                                                    final String user) {
        final List<Map<String, Object>> common = new ArrayList<>();
        common.add(Map.of("path", "EventTime/TimeCreated", "field", timeField, "time_format", pattern));
        common.addAll(SYSTEM);
        common.add(Map.of("path", "EventSource/Device/HostName", "field", host));
        common.add(Map.of("path", "EventSource/Device/IPAddress", "field", ip));
        common.add(Map.of("path", "EventSource/User/Id", "field", user));
        return common;
    }

    private static List<Map<String, Object>> plus(final List<Map<String, Object>> fields,
                                                  final List<Map<String, Object>> more) {
        final List<Map<String, Object>> all = new ArrayList<>(fields);
        all.addAll(more);
        return all;
    }

    private static Map<String, Object> with(final Map<String, Object> entry, final String path) {
        final Map<String, Object> withPath = new LinkedHashMap<>(entry);
        withPath.put("path", path);
        return withPath;
    }

    // The script's MAPPINGS.
    private static Map<String, Map<String, Object>> mappings() {
        final List<Map<String, Object>> admin = List.of(
                Map.of("path", "EventSource/Client/IPAddress", "field", "src_ip"),
                Map.of("path", "EventDetail/Authenticate/User/Id", "field", "username"));
        final List<Map<String, Object>> firewallCommon = new ArrayList<>();
        firewallCommon.add(Map.of("path", "EventTime/TimeCreated", "field", "timestamp",
                "time_format", "yyyy-MM-dd'T'HH:mm:ssXXX"));
        firewallCommon.addAll(SYSTEM);
        firewallCommon.add(Map.of("path", "EventSource/Device/HostName", "field", "device"));
        firewallCommon.add(Map.of("path", "EventSource/User/Id", "field", "username"));
        firewallCommon.add(Map.of("path", "EventDetail/TypeId",
                "xpath", "concat(data[@name='event_type']/@value, '-', data[@name='action']/@value)"));
        firewallCommon.add(Map.of("path", "EventDetail/Description", "field", "message"));
        final Map<String, Map<String, Object>> mappings = new LinkedHashMap<>();
        // Value maps as xsl:map: one shared by Success and Permitted, one with a default; repeated elements as
        // named templates.
        mappings.put("firewall", Map.of("input", "data_splitter", "common", firewallCommon, "events", List.of(
                Map.of("name", "traffic", "when", List.of(Map.of("field", "event_type", "equals", "TRAFFIC")),
                        "fields", List.of(
                                Map.of("path", "EventDetail/Network/Open/Source/Device/IPAddress", "field",
                                        "src_ip"),
                                Map.of("path", "EventDetail/Network/Open/Source/Port", "field", "src_port"),
                                Map.of("path", "EventDetail/Network/Open/Destination/Device/IPAddress", "field",
                                        "dst_ip"),
                                Map.of("path", "EventDetail/Network/Open/Destination/Port", "field", "dst_port"),
                                with(ALLOW_DENY, "EventDetail/Network/Open/Outcome/Success"),
                                with(ALLOW_DENY, "EventDetail/Network/Open/Outcome/Permitted"),
                                Map.of("path", "EventDetail/Network/Open/Data", "data_name", "RuleId", "field",
                                        "rule_id"))),
                Map.of("name", "logon", "when", List.of(Map.of("field", "event_type", "equals", "ADMIN"),
                                Map.of("field", "action", "one_of", List.of("LOGIN_SUCCESS", "LOGIN_FAILED"))),
                        "fields", plus(admin, List.of(
                                Map.of("path", "EventDetail/Authenticate/Action", "value", "Logon"),
                                with(LOGIN, "EventDetail/Authenticate/Outcome/Success")))),
                Map.of("name", "logoff", "when", List.of(Map.of("field", "event_type", "equals", "ADMIN"),
                                Map.of("field", "action", "equals", "LOGOUT")),
                        "fields", plus(admin, List.of(
                                Map.of("path", "EventDetail/Authenticate/Action", "value", "Logoff")))),
                Map.of("name", "system", "when", List.of(Map.of("field", "event_type", "equals", "SYSTEM")),
                        "fields", List.of(
                                Map.of("path", "EventDetail/Alert/Type", "value", "Other"),
                                Map.of("path", "EventDetail/Alert/Severity", "field", "severity",
                                        "map", Map.of("WARNING", "Minor", "ERROR", "Major", "CRITICAL",
                                                "Critical"), "default", "Info"))))));
        mappings.put("csv", Map.of("input", "data_splitter",
                "common", common("time", "yyyy-MM-dd'T'HH:mm:ss", "host", "ip", "user"),
                "events", List.of(
                        Map.of("name", "failed logon", "when", List.of(Map.of("field", "result", "equals", "fail")),
                                "fields", plus(logon("user", Map.of("value", "false")), List.of(
                                        Map.of("path", "EventDetail/Description", "value", "Failed logon"),
                                        Map.of("path", "EventDetail/Authenticate/Data", "data_name", "result",
                                                "field", "result")))),
                        Map.of("name", "logon", "fields", logon("user", Map.of("field", "result",
                                "map", Map.of("ok", "true")))))));
        mappings.put("json", Map.of("input", "json",
                "common", common("ts", "yyyy-MM-dd'T'HH:mm:ssX", "host", "src", "user"),
                "events", List.of(Map.of("name", "logon", "fields", logon("user", Map.of("field", "ok",
                        "map", Map.of("true", "true", "false", "false")))))));
        mappings.put("xml", Map.of("input", "xml", "root", "logons", "record", "logon",
                "common", common("when", "yyyy-MM-dd HH:mm:ss", "host", "addr", "who"),
                "events", List.of(Map.of("name", "logon",
                        "when", List.of(Map.of("field", "result", "one_of", List.of("SUCCESS", "FAILURE"))),
                        "fields", logon("who", Map.of("field", "result",
                                "map", Map.of("SUCCESS", "true", "FAILURE", "false")))))));
        mappings.put("syslog", Map.of("input", "data_splitter",
                "common", common("time", "yyyy-MM-dd'T'HH:mm:ss.SSSX", "host", "ip", "user"),
                "events", List.of(Map.of("name", "ssh logon", "when", List.of(Map.of("field", "app", "equals",
                                "sshd")),
                        "fields", plus(logon("user", Map.of("value", "true")), List.of(
                                Map.of("path", "EventDetail/Authenticate/Data", "data_name", "method",
                                        "field", "method")))))));
        return mappings;
    }
}
