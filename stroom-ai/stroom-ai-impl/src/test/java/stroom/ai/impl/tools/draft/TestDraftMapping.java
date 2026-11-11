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

package stroom.ai.impl.tools.draft;

import stroom.ai.impl.GoldenFiles;
import stroom.ai.impl.tools.JsonValues;
import stroom.ai.impl.tools.ToolRefusal;
import stroom.ai.impl.tools.generation.MappingCompaction;
import stroom.ai.impl.tools.generation.Pairs;
import stroom.ai.impl.tools.generation.TranslationMapping;
import stroom.ai.impl.tools.generation.XsltGenerator;
import stroom.ai.impl.tools.sample.KindFields;
import stroom.ai.impl.tools.sample.SplitterSpec;
import stroom.ai.impl.tools.sample.ToolJson;
import stroom.ai.impl.tools.validation.EventSchema;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/// A starting mapping drafted from the sample.
class TestDraftMapping extends DraftFixtures {

    /// A firewall's JSON lines: a TRAFFIC record and a LOGIN.
    static final String FW_JSON = """
            {"timestamp":"2026-10-01T09:00:12+10:00","device":"FW-EDGE-01","event_type":"TRAFFIC","severity":"INFO",\
            "src_ip":"192.0.2.10","src_port":54321,"dst_ip":"198.51.100.20","dst_port":443,"protocol":"TCP",\
            "action":"ALLOW","rule_id":1001,"bytes_sent":1520,"bytes_received":8450,"username":"",\
            "message":"Outbound HTTPS connection allowed"}
            {"timestamp":"2026-10-01T09:01:00+10:00","device":"FW-EDGE-01","event_type":"LOGIN","severity":"INFO",\
            "src_ip":"192.0.2.11","src_port":1,"dst_ip":"198.51.100.1","dst_port":22,"protocol":"TCP",\
            "action":"ALLOW","rule_id":7,"bytes_sent":0,"bytes_received":0,"username":"alice",\
            "message":"Admin login"}
            """;
    /// A firewall's CSV: TRAFFIC, SYSTEM and ADMIN records.
    static final String FIREWALL_CSV = """
            timestamp,device,event_type,severity,src_ip,src_port,dst_ip,dst_port,protocol,action,rule_id,username,\
            message
            2026-10-01T09:00:12+10:00,FW-EDGE-01,TRAFFIC,INFO,192.0.2.10,54321,198.51.100.20,443,TCP,ALLOW,1001,,\
            Outbound HTTPS allowed
            2026-10-01T09:01:05+10:00,FW-EDGE-01,TRAFFIC,WARNING,203.0.113.45,49822,192.0.2.25,22,UDP,DENY,2003,,\
            Inbound SSH blocked
            2026-10-01T09:10:03+10:00,FW-EDGE-01,SYSTEM,INFO,,,,,N/A,START,,,Firewall logging service started
            2026-10-01T09:10:30+10:00,FW-EDGE-01,SYSTEM,INFO,,,,,N/A,CONFIG_SAVED,,,Configuration saved
            2026-10-01T09:11:15+10:00,FW-EDGE-01,ADMIN,INFO,192.0.2.100,,,,HTTPS,LOGIN_SUCCESS,,admin,\
            Administrator logged in
            2026-10-01T09:12:02+10:00,FW-EDGE-01,ADMIN,WARNING,203.0.113.90,,,,HTTPS,LOGIN_FAILED,,admin,\
            Failed administrator login
            2026-10-01T09:16:08+10:00,FW-EDGE-01,ADMIN,INFO,192.0.2.100,,,,HTTPS,LOGOUT,,admin,\
            Administrator logged out
            2026-10-01T09:17:00+10:00,FW-EDGE-01,ADMIN,NOTICE,192.0.2.100,,,,HTTPS,CONFIG_CHANGE,,admin,\
            Policy 12 changed
            """;
    // The drafts the generator's tests start from, a golden file.
    private static final String DRAFTS = "/stroom/ai/impl/tools/generation/drafts.json";
    // The draft reply's firewall sample.
    private static final String REPLY_SAMPLE = """
            timestamp,device,event_type,severity,src_ip,src_port,dst_ip,dst_port,protocol,action,rule_id,bytes_sent,\
            bytes_received,username,message
            2026-10-01T09:00:12+10:00,FW1,TRAFFIC,INFO,192.0.2.10,54321,198.51.100.20,443,TCP,ALLOW,1001,1520,8450,,ok
            2026-10-01T09:01:05+10:00,FW1,TRAFFIC,WARNING,203.0.113.45,49822,192.0.2.25,22,TCP,DENY,2003,0,0,,blocked
            2026-10-01T09:10:00+10:00,FW1,ADMIN,INFO,192.0.2.5,50000,192.0.2.1,443,HTTPS,LOGIN_SUCCESS,0,0,0,admin,in
            2026-10-01T09:20:00+10:00,FW1,SYSTEM,WARNING,,,,,N/A,HIGH_CPU,0,0,0,,CPU over 85 percent
            """;
    // Codes that mean nothing without the vendor's documentation.
    private static final String NOTES_SAMPLE = """
            ts,host,evt,usr,src,res
            2026-10-01T08:00:00Z,dc01,4624,alice,10.0.0.1,0x0
            2026-10-01T08:05:00Z,dc01,4625,bob,10.0.0.2,0xC000006A
            2026-10-01T08:10:00Z,dc01,4634,alice,10.0.0.1,0x0
            2026-10-01T08:12:00Z,dc01,9999,carol,10.0.0.3,0x0
            """;

    /// @param samples The samples.
    /// @param source  The source's name.
    /// @return Their draft, with no system name or environment given.
    static Map<String, Object> draft(final Object samples, final String source) {
        return DraftMapping.draft(samples, source, null, null, null, null, null);
    }

    /// @param samples     The samples.
    /// @param source      The source's name.
    /// @param system      The system name.
    /// @param environment The environment.
    /// @return Their draft.
    static Map<String, Object> draft(final Object samples, final String source, final String system,
                                     final String environment) {
        return DraftMapping.draft(samples, source, system, environment, null, null, null);
    }

    /// @param mapping A mapping's JSON.
    /// @param schema  The schema.
    /// @param version Its version.
    /// @return What the generator makes of it.
    static XsltGenerator.Generated generate(final Map<String, Object> mapping, final EventSchema schema,
                                            final String version) {
        return XsltGenerator.generate(TranslationMapping.fromJson(mapping), schema, version);
    }

    /// @param mapping A draft's mapping.
    /// @return The draft as an agent leaves it once its placeholder kinds are decided; here, kept as
    /// Unknown on purpose.
    static Map<String, Object> decided(final Map<String, Object> mapping) {
        final Map<String, Object> out = new LinkedHashMap<>(mapping);
        final List<Map<String, Object>> events = new ArrayList<>();
        for (final Map<String, Object> rule : JsonValues.maps(mapping.get("events"))) {
            if (JsonValues.maps(rule.get("fields")).stream()
                    .anyMatch(f -> ((String) f.get("path")).contains("/Unknown/"))) {
                final Map<String, Object> kept = new LinkedHashMap<>(rule);
                kept.put("allow_unknown", "kept unknown for the test");
                events.add(kept);
            } else {
                events.add(rule);
            }
        }
        out.put("events", events);
        return out;
    }

    @Test
    void theNotesSayWhereTheEventsMessageGoes() {
        // Seen in eval cases 22, 23 and 42: the message (a CEF msg, a ticket's comment) went into a Document's
        // Description or a Data entry, not the event's own.
        final Map<String, Object> withMsg = draft(List.of("""
                {"time": "2026-10-02T09:00:00Z", "user": "jdoe", "action": "login", "msg": "SAML login"}
                {"time": "2026-10-02T09:45:00Z", "user": "jdoe", "action": "logout", "msg": "Session ended"}
                """), "Gateway");
        final Map<String, Object> withComment = draft(List.of("""
                {"time": "2026-10-02T09:00:00Z", "agent": "jdoe", "action": "create", "comment": "Printer offline"}
                {"time": "2026-10-02T09:45:00Z", "agent": "jdoe", "action": "close", "comment": "Resolved"}
                """), "Helpdesk");

        assertThat(common(withMsg).get("EventDetail/Description")).isEqualTo(map("path", "EventDetail/Description",
                "field", "msg"));
        assertThat(notes(withMsg)).contains("'msg' reads as each event's message, so it is EventDetail/Description, "
                                            + "the event's own description: keep it there in every rule, rather "
                                            + "than in an object's Description or a Data entry.");
        assertThat(notes(withComment)).contains("'comment' reads as free text written about each event: if it says "
                                                + "what happened, map it to EventDetail/Description, the event's own "
                                                + "description, rather than to an object's Description or a Data "
                                                + "entry.");
    }

    @Test
    void theDraftIsAValidMappingWithTheObviousHomesAndARulePerKind() {
        final Map<String, Object> draft = draft(Map.of("fw.jsonl", FW_JSON), "FortiOS firewall");
        final Map<String, Object> m = mappingOf(draft);
        assertThat(List.of(m.get("input"), m.get("json_layout"))).isEqualTo(List.of("json", "lines"));
        final Map<String, Map<String, Object>> byPath = common(draft);
        assertThat(byPath.get("EventTime/TimeCreated")).isEqualTo(map("path", "EventTime/TimeCreated", "field",
                "timestamp", "time_format", "yyyy-MM-dd'T'HH:mm:ssXXX"));
        assertThat(byPath.get("EventSource/Device/HostName").get("field")).isEqualTo("device");
        assertThat(byPath.get("EventSource/Client/IPAddress").get("field")).isEqualTo("src_ip");
        assertThat(byPath.get("EventSource/Client/Port").get("field")).isEqualTo("src_port");
        assertThat(byPath.get("EventSource/Server/IPAddress").get("field")).isEqualTo("dst_ip");
        assertThat(byPath.get("EventSource/Server/Port").get("field")).isEqualTo("dst_port");
        assertThat(byPath.get("EventSource/User/Id").get("field")).isEqualTo("username");
        assertThat(byPath.get("EventDetail/TypeId").get("field")).isEqualTo("event_type");
        assertThat(byPath.get("EventDetail/Description").get("field")).isEqualTo("message");
        // The source's name is the generator's; the system's name is the user's to give (seen in testing: taken
        // from the source unasked).
        assertThat(byPath.get("EventSource/System/Name").get("value"))
                .isEqualTo(DraftMapping.SYSTEM_NAME_PLACEHOLDER);
        assertThat(byPath.get("EventSource/Generator").get("value")).isEqualTo("FortiOS firewall");
        // TRAFFIC's records are connections allowed between addresses: Network/Permit, not an Unknown placeholder.
        assertThat(draft.get("kinds")).isEqualTo(List.of("TRAFFIC", "LOGIN"));
        assertThat(rules(draft).keySet()).containsExactly("traffic_permitted", "login", "other");
        final Map<String, Object> permitted = rules(draft).get("traffic_permitted");
        assertThat(permitted.get("when")).isEqualTo(json("""
                [{"field": "event_type", "equals": "TRAFFIC"}, {"field": "action", "equals": "ALLOW"}]"""));
        assertThat(JsonValues.maps(permitted.get("fields")))
                .contains(map("path", "EventDetail/Network/Permit/Destination/Port", "field", "dst_port"))
                .contains(map("path", "EventDetail/Network/Permit/Data", "data_name", "rule_id", "field", "rule_id"));
        final List<Map<String, Object>> login = JsonValues.maps(rules(draft).get("login").get("fields"));
        assertThat(login).contains(map("path", "EventDetail/Authenticate/Action", "value", "Logon"))
                .contains(map("path", "EventDetail/Authenticate/User/Id", "field", "username"));
        assertThat(notes(draft)).anyMatch(n -> n.startsWith("Drafted from the sample's values")
                                               && n.contains("Network/Permit for action ALLOW"));
        // Every kind has its action element: the note says so, rather than asking for Unknown placeholders to be
        // replaced.
        assertThat(notes(draft)).anyMatch(n -> n.contains("each with its action element"))
                .anyMatch(n -> n.contains("Environment"));
        // As drafted it generates (no kind is left Unknown), and once decided too.
        assertThat(generate(m, SCHEMA, "4.1.0").ok()).isTrue();
        final Map<String, Object> skipped = decided(m);
        skipped.put("unmatched", "skip");
        final XsltGenerator.Generated result = generate(skipped, SCHEMA, "4.1.0");
        assertThat(result.ok()).as("%s", result.problems()).isTrue();
    }

    @Test
    void theEnvironmentGivenIsTheDrafts() {
        // draft_translation_mapping's environment, from the field inventory test.
        final Map<String, Object> drafted = DraftMapping.draft(Map.of("fw.jsonl", FW_JSON), "FortiOS firewall", null,
                "Prod", null, null, null);
        assertThat(JsonValues.maps(mappingOf(drafted).get("common")).get(2).get("value")).isEqualTo("Prod");
        assertThat(generate(mappingOf(drafted), SCHEMA, "4.1.0").ok()).isTrue();
    }

    @Test
    void textFormatsComeWithTheirSplitterAndNaiveTimesGetAZoneNote() {
        final String kv = """
                date=2026-10-01 time=10:00:00 devname="fw01" srcip=10.0.0.1 dstip=203.0.113.5 dstport=443 \
                action="accept" logid=0000000013
                date=2026-10-01 time=10:00:05 devname="fw01" srcip=10.0.0.2 dstip=203.0.113.6 dstport=80 \
                action="deny" logid=0000000013
                """;
        final Map<String, Object> draft = draft(List.of(kv), "FortiOS");
        assertThat(mappingOf(draft).get("input")).isEqualTo("data_splitter");
        assertThat(((Map<?, ?>) draft.get("splitter")).get("kind")).isEqualTo("key_value");
        final Map<String, Map<String, Object>> byPath = common(draft);
        assertThat(byPath.get("EventSource/Device/HostName").get("field")).isEqualTo("devname");
        assertThat(byPath.get("EventSource/Client/IPAddress").get("field")).isEqualTo("srcip");
        assertThat(byPath.get("EventSource/Server/Port").get("field")).isEqualTo("dstport");
        assertThat(byPath.get("EventDetail/TypeId").get("field")).isEqualTo("action");
        assertThat(draft.get("kinds")).isEqualTo(List.of("accept", "deny"));
        // FortiOS splits date= and time=: the draft joins them, with no zone assumed (the user says it).
        assertThat(byPath.get("EventTime/TimeCreated")).isEqualTo(map("path", "EventTime/TimeCreated",
                "xpath", "concat(data[@name='date']/@value, ' ', data[@name='time']/@value)",
                "time_format", "yyyy-MM-dd HH:mm:ss"));
        assertThat(notes(draft)).anyMatch(n -> n.contains("'date' and 'time' are joined")
                                               && n.contains("the user's to say"));
        // accept and deny between addresses are Network/Permit and Network/Deny, so nothing is left to decide.
        assertThat(rules(draft).keySet()).containsExactly("permitted", "denied", "other");
        assertThat(generate(mappingOf(draft), SCHEMA, "4.1.0").ok()).isTrue();
    }

    @Test
    void timesTheEventTimeLeavesUnreadAreIgnoredWhenItIsInEveryRecord() {
        // Seen in testing: a proxy's CSV had date and time columns and a precise x-cs-timestamp; EventTime was read
        // from x-cs-timestamp, and processing logged date and time as fields no rule reads.
        final String csv = """
                date,time,x-cs-timestamp,host,c-ip,cs-host
                2026-11-09,10:00:00,2026-11-09T10:00:00.123Z,proxy01,10.0.0.1,example.com
                2026-11-09,10:00:05,2026-11-09T10:00:05.456Z,proxy01,10.0.0.2,example.org
                2026-11-09,10:00:09,2026-11-09T10:00:09.789Z,proxy01,10.0.0.3,example.net
                """;
        final Map<String, Object> draft = draft(Map.of("proxy.csv", csv), "Proxy", "Proxy", "Prod");
        assertThat(common(draft).get("EventTime/TimeCreated").get("field")).isEqualTo("x-cs-timestamp");
        assertThat(mappingOf(draft).get("ignored_fields")).isEqualTo(List.of("date", "time"));
        assertThat(JsonValues.strings(draft.get("unmapped_fields"))).doesNotContain("date", "time");
        assertThat(notes(draft)).anyMatch(n -> n.contains("ignored_fields") && n.contains("'date'")
                                               && n.contains("'time'"));
        final XsltGenerator.Generated generated = generate(mappingOf(draft), SCHEMA, "4.1.0");
        assertThat(generated.ok()).as(String.valueOf(generated.problems())).isTrue();
    }

    @Test
    void timesAreNotIgnoredWhereTheEventTimeIsMissingFromSomeRecords() {
        final String csv = """
                date,time,x-cs-timestamp,c-ip,cs-host
                2026-11-09,10:00:00,2026-11-09T10:00:00.123Z,10.0.0.1,example.com
                2026-11-09,10:00:05,,10.0.0.2,example.org
                2026-11-09,10:00:09,2026-11-09T10:00:09.789Z,10.0.0.3,example.net
                2026-11-09,10:00:12,2026-11-09T10:00:12.789Z,10.0.0.4,example.net
                """;
        final Map<String, Object> draft = draft(Map.of("proxy.csv", csv), "Proxy", "Proxy", "Prod");
        assertThat(mappingOf(draft)).doesNotContainKey("ignored_fields");
    }

    @Test
    void aFirewallsTrafficAndAdminRecordsAreDraftedWithTheirActionElements() {
        // Seen in VS Code (three sessions on this sample): the draft left every kind Unknown, the agent found
        // Network's and Authenticate's structure hard, gave up, and asked the user to accept traffic and admin as
        // Unknown.
        final Map<String, Object> draft = draft(Map.of("fw.csv", FIREWALL_CSV), "Firewall", "FW", "Prod");
        final Map<String, Map<String, Object>> rules = rules(draft);
        // Kinds most common first.
        assertThat(rules.keySet()).containsExactly("admin_logon", "admin_logoff", "admin_config_change",
                "traffic_permitted", "traffic_denied", "system_config_change", "system_service", "other");
        assertThat(fields(rules.get("traffic_permitted")).keySet()).contains(
                "EventDetail/Network/Permit/Source/Device/IPAddress", "EventDetail/Network/Permit/Destination/Port");
        assertThat(fields(rules.get("traffic_denied")).keySet()).contains("EventDetail/Network/Deny/Source/Port");
        final Map<String, Object> protocol = fields(rules.get("traffic_denied"))
                .get("EventDetail/Network/Deny/Source/TransportProtocol");
        // The values it takes.
        assertThat(protocol.get("map")).isEqualTo(map("TCP", "TCP", "UDP", "UDP"));
        assertThat(protocol.get("default")).isEqualTo("Other");
        assertThat(fields(rules.get("admin_logon")).get("EventDetail/Authenticate/Outcome/Success").get("map"))
                .isEqualTo(map("LOGIN_SUCCESS", "true", "LOGIN_FAILED", "false"));
        assertThat(JsonValues.maps(rules.get("admin_logoff").get("fields")))
                .contains(map("path", "EventDetail/Authenticate/Action", "value", "Logoff"));
        assertThat(fields(rules.get("admin_config_change")).keySet())
                .contains("EventDetail/Update/After/Configuration/Type");
        // The logging service's START is a service starting (Process); no kind is left Unknown, and all of it
        // validates.
        final Map<String, Map<String, Object>> service = fields(rules.get("system_service"));
        assertThat(service.get("EventDetail/Process/Action").get("map")).isEqualTo(map("START", "Startup"));
        assertThat(service.get("EventDetail/Process/Type").get("value")).isEqualTo("Service");
        assertThat(generate(mappingOf(draft), SCHEMA_352, "3.5.2").problems()).isEmpty();
    }

    @Test
    void fieldsWithNoElementAreDraftedAsDataOnTheSideTheyName() {
        // Eval case 21: a connection broker's CONNECT and DISCONNECT records, with fields the schema has no element
        // for.
        @SuppressWarnings("unchecked")
        final String sample = ((List<String>) samples("21_csv_connections_odd_fields (excerpt)"))
                .getFirst();
        final Map<String, Object> draft = draft(Map.of("broker.csv", sample), "Connection Broker", "Broker", "Eval");
        final Map<String, Map<String, Object>> rules = rules(draft);
        // The kinds are themselves the actions.
        assertThat(rules.keySet()).containsExactly("connected", "closed", "other");
        final Set<List<Object>> connect = pathsAndNames(rules.get("connected"));
        assertThat(connect).contains(
                pathAndName("EventDetail/Network/Connect/Destination/Port", null),
                pathAndName("EventDetail/Network/Connect/Destination/Device/IPAddress", null),
                pathAndName("EventDetail/Network/Connect/Destination/Data", "destination_key"),
                pathAndName("EventDetail/Network/Connect/Destination/Data", "destination_zone"),
                pathAndName("EventDetail/Network/Connect/Source/Data", "source_zone"),
                pathAndName("EventDetail/Network/Connect/Data", "tls_ja3"));
        assertThat(pathsAndNames(rules.get("closed")))
                .contains(pathAndName("EventDetail/Network/Close/Destination/Data", "destination_key"));
        assertThat(JsonValues.maps(mappingOf(draft).get("common")))
                .contains(map("path", "EventSource/Device/HostName", "field", "sensor"));
        // No kind is Unknown, and it generates as drafted.
        assertThat(generate(mappingOf(draft), SCHEMA, "4.1.0").ok()).isTrue();
    }

    private static Set<List<Object>> pathsAndNames(final Map<String, Object> rule) {
        final Set<List<Object>> out = new HashSet<>();
        JsonValues.maps(rule.get("fields")).forEach(f -> out.add(pathAndName((String) f.get("path"),
                (String) f.get("data_name"))));
        return out;
    }

    private static List<Object> pathAndName(final String path, final String name) {
        final List<Object> out = new ArrayList<>();
        out.add(path);
        out.add(name);
        return out;
    }

    @Test
    void healthAndStateRecordsAreDraftedAsAlerts() {
        // The firewall sample of a VS Code run: CPU and VPN tunnel records were left Unknown by the draft; the agent
        // made them Alert itself, taking two rounds over Alert/Type (it tried 'Firewall').
        final String sample = """
                timestamp,device,event_type,severity,src_ip,dst_ip,action,message
                2026-10-01T09:00:00Z,FW1,SYSTEM,WARNING,,,HIGH_CPU,CPU over 85 percent
                2026-10-01T09:01:00Z,FW1,SYSTEM,ERROR,,,VPN_TUNNEL_DOWN,Tunnel branch-01 down
                2026-10-01T09:02:00Z,FW1,SYSTEM,INFO,,,VPN_TUNNEL_UP,Tunnel branch-01 restored
                2026-10-01T09:03:00Z,FW1,SYSTEM,INFO,,,START,Logging service started
                """;
        final Map<String, Object> draft = draft(Map.of("fw.csv", sample), "FW", "FW", "Prod");
        final Map<String, Map<String, Object>> rules = rules(draft);
        final Map<String, Map<String, Object>> alert = fields(rules.get("system_alert"));
        assertThat(JsonValues.maps(rules.get("system_alert").get("when")).getLast()).isEqualTo(map("field", "action",
                "one_of", List.of("HIGH_CPU", "VPN_TUNNEL_DOWN", "VPN_TUNNEL_UP")));
        assertThat(alert.get("EventDetail/Alert/Type").get("map")).isEqualTo(map("HIGH_CPU", "Other",
                "VPN_TUNNEL_DOWN", "Network", "VPN_TUNNEL_UP", "Network"));
        final Map<?, ?> severity = (Map<?, ?>) alert.get("EventDetail/Alert/Severity").get("map");
        assertThat(List.of(severity.get("WARNING"), severity.get("ERROR"), severity.get("INFO")))
                .isEqualTo(List.of("Minor", "Major", "Info"));
        // A word the sample lacks, written as the sample writes them.
        assertThat(severity.get("CRITICAL")).isEqualTo("Critical");
        assertThat(rules).containsKey("system_service");
        assertThat(rules.values()).noneMatch(r -> JsonValues.truthy(r.get("when")) && JsonValues.maps(r.get("fields"))
                .stream().anyMatch(f -> ((String) f.get("path")).startsWith("EventDetail/Unknown")));
        assertThat(generate(mappingOf(draft), SCHEMA_352, "3.5.2").problems()).isEmpty();
    }

    @Test
    void recordsThatEachRunACommandAreDraftedAsProcessWithTheAccountRunAs() {
        // Eval case 07 on Haiku: sudo lines, with no field naming kinds, were drafted as Unknown and made Authorise.
        final String sudo = """
                <85>Sep 28 14:00:01 web02 sudo:    grace : TTY=pts/0 ; PWD=/home/grace ; USER=root ; \
                COMMAND=/usr/bin/systemctl restart nginx
                <85>Sep 28 14:10:44 web02 sudo:    henry : TTY=pts/1 ; PWD=/var/www ; USER=www-data ; \
                COMMAND=/usr/bin/git pull
                """;
        final SplitterSpec splitter = specOf("""
                {"kind": "syslog", "body": {"kind": "regex",
                 "pattern": "^\\\\s*(\\\\S+) : TTY=(\\\\S+) ; PWD=(\\\\S+) ; USER=(\\\\S+) ; COMMAND=(.*)$",
                 "names": ["user", "tty", "pwd", "run_as", "command"]}}""");
        final Map<String, Object> draft = DraftMapping.draft(List.of(sudo), "sudo", "Web", "Prod", null, null,
                splitter);
        final Map<String, Object> m = mappingOf(draft);
        assertThat(JsonValues.maps(m.get("common"))).contains(map("path", "EventSource/RunAs/Id", "field", "run_as"));
        assertThat(rules(draft).keySet()).containsExactly("command");
        assertThat(rules(draft).get("command")).doesNotContainKey("when");
        final Map<String, Map<String, Object>> fields = fields(rules(draft).get("command"));
        assertThat(fields.get("EventDetail/Process/Command")).isEqualTo(map("path", "EventDetail/Process/Command",
                "field", "command"));
        assertThat(fields.get("EventDetail/Process/Action").get("value")).isEqualTo("Execute");
        assertThat(notes(draft)).anyMatch(n -> n.startsWith("Every record runs a command ('command')")
                                               && n.contains("RunAs"))
                .noneMatch(n -> n.startsWith("No field names the kind"));
        assertThat(generate(m, SCHEMA, "4.1.0").problems()).isEmpty();
        assertThat(generate(m, SCHEMA_352, "3.5.2").problems()).isEmpty();
    }

    @Test
    void theDeviceAnEventHappenedAtIsDraftedFromTheFieldNamingIt() {
        // Eval cases 28 and 34 on Haiku: a door controller's door left as Data, with the sender's address
        // (stroom:meta('RemoteAddress')) as the device; a NAC's switch and the joining device's MAC put elsewhere.
        final String badges = """
                <?xml version="1.0" encoding="UTF-8"?>
                <records>
                <record><when>2026-10-02T07:58:00Z</when><holder>alice</holder><door>Main entrance</door>\
                <granted>true</granted></record>
                </records>
                """;
        final Map<String, Object> draft = draft(List.of(badges), "Doors", "Doors", "Prod");
        assertThat(common(draft).get("EventSource/Device/Name").get("field")).isEqualTo("door");
        assertThat(common(draft).get("EventSource/User/Id").get("field")).isEqualTo("holder");
        assertThat(notes(draft)).noneMatch(n -> n.startsWith("No host field"));
        final String nac = """
                {"time": "2026-10-02T12:00:00Z", "switch": "sw-core-01", "mac": "00:1A:2B:3C:4D:5E", \
                "ip": "10.4.0.21", "user": "alice"}
                {"time": "2026-10-02T12:00:30Z", "switch": "sw-core-01", "mac": "00:1A:2B:3C:4D:5F", \
                "ip": "10.4.0.22", "user": "bob"}
                """;
        final Map<String, Map<String, Object>> nacCommon = common(draft(List.of(nac), "NAC", "NAC", "Prod"));
        assertThat(nacCommon.get("EventSource/Device/HostName").get("field")).isEqualTo("switch");
        assertThat(nacCommon.get("EventSource/Device/MACAddress").get("field")).isEqualTo("mac");
        for (final String sample : List.of(badges, nac)) {
            assertThat(generate(mappingOf(draft(List.of(sample), "S", "S", "Prod")), SCHEMA, "4.1.0")
                    .problems()).isEmpty();
        }
        // With nothing naming the device, the note says what the device is, and the sender's address comes last.
        final Map<String, Object> plain = draft(List.of("{\"time\": \"2026-10-02T12:00:00Z\", \"user\": \"alice\"}\n"),
                "S", "S", "Prod");
        assertThat(notes(plain)).anyMatch(n -> n.startsWith("No host field was recognised: EventSource/Device is the "
                                                            + "device where the event happened"));
    }

    @Test
    void theNoTimestampNoteIsSaidOnlyWhenNoTimeWasMapped() {
        // Seen: the note hung on the time zone test, so a draft that mapped EventTime said no timestamp was
        // recognised.
        final Map<String, Object> timed = draft(Map.of("fw.jsonl", FW_JSON), "FW", "FW", "Prod");
        assertThat(common(timed)).containsKey("EventTime/TimeCreated");
        assertThat(notes(timed)).noneMatch(n -> n.startsWith("No timestamp field was recognised"));
        final Map<String, Object> untimed = draft(List.of("""
                {"user": "alice", "action": "login"}
                {"user": "bob", "action": "logout"}
                """), "S", "S", "Prod");
        assertThat(notes(untimed)).anyMatch(n -> n.startsWith("No timestamp field was recognised"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void keyValuePairsInATextFieldAreDraftedAsFieldsOfTheirOwn() {
        // Seen: survey_feed found the pairs (kv:Action=Login, Event=User) but the draft carried the text whole as
        // one Data, and the agent made 22 describe_document calls and 33 extractions by hand.
        final Map<String, Object> draft = DraftMapping.draft(samples("test audit"), "Secret Server",
                "Secret Server", "Prod", null, null, null);
        final Map<String, Object> m = mappingOf(draft);
        final Map<String, Map<String, Object>> extracted = new LinkedHashMap<>();
        JsonValues.maps(m.get("extract")).forEach(e -> extracted.put(((List<String>) e.get("names")).getFirst(), e));
        assertThat(extracted.keySet()).containsExactly("user", "event", "action", "by_user", "item_id",
                "source_ip", "session_id", "client", "secret", "secret_id");
        assertThat(extracted.values()).allMatch(e -> "EventData/Data".equals(e.get("field")));
        assertThat(notes(draft)).anyMatch(n -> n.startsWith("'EventData/Data' holds key/value pairs: 10 keys"));
        // The kinds come from the text's pairs, not from an EventID that is 1000 in every record.
        assertThat(common(draft).get("EventDetail/TypeId").get("field")).isEqualTo("event");
        assertThat(draft.get("kinds")).isEqualTo(List.of("User", "Secret"));
        assertThat(common(draft).get("EventSource/Client/IPAddress").get("field")).isEqualTo("source_ip");
        final Map<String, Object> logon = rules(draft).get("user_logon");
        assertThat(logon.get("when")).isEqualTo(json("""
                [{"field": "event", "equals": "User"}, {"field": "action", "one_of": ["Login", "Login Failed"]}]"""));
        final List<Object> data = JsonValues.maps(logon.get("fields")).stream()
                .filter(f -> ((String) f.get("path")).endsWith("/Data"))
                .map(f -> f.get("field"))
                .toList();
        // By User acted (EventSource/User); the user acted on is left as Data for the agent to place.
        assertThat(common(draft).get("EventSource/User/Id").get("field")).isEqualTo("by_user");
        assertThat(data).contains("user", "client").doesNotContain("EventData/Data");
        final XsltGenerator.Generated result = generate(decided(m), SCHEMA, "4.1.0");
        assertThat(result.ok()).as("%s", result.problems()).isTrue();
        // The keys alike are one function, called with the key as the text writes it.
        final String xslt = result.xslt();
        assertThat(xslt.split("<xsl:function name=\"mcp:keyed_value\"", -1)).hasSize(2);
        assertThat(xslt).contains("mcp:keyed_value($event_data_data, 'Source IP')");
    }

    @Test
    void theDraftsKeptAsAFixtureAreTheDraftsMadeNow() {
        // The generator's tests of what it does with a draft (TestMappingDrafts, TestPairs) start from drafts kept as
        // a golden file: the drafts made now, and the first compacted, with the file's other inputs kept as they are.
        final Map<String, Object> kept = json(resource(DRAFTS));
        kept.put("fw", mappingOf(draft(Map.of("fw.csv", REPLY_SAMPLE), "FW", "FW", "Prod")));
        kept.put("firewall_logs_20", mappingOf(draft(samples("test firewall logs 20"), "Firewall Logs")));
        final Map<String, Object> fortigate = mappingOf(draft(samples("test fortigate"), "Fortigate"));
        kept.put("fortigate", fortigate);
        kept.put("shapes", mappingOf(DraftMapping.draft(samples("test shapes"), "App", "App", "Prod", null, null,
                null)));
        kept.put("fortigate_compact_rules", MappingCompaction.compactRules(fortigate));
        GoldenFiles.assertResourceMatches(DRAFTS, ToolJson.dumps(kept, 1));
    }

    @Test
    void theSessionsDraftFitsTheClientInline() {
        // Copilot saved a 10.6 KB draft to a file; the agent read 100 of its 502 lines and wrote its own mapping.
        final Map<String, Object> draft = draft(samples("test firewall logs 20"), "Firewall Logs");
        draft.put("mapping", MappingCompaction.compactRules(mappingOf(draft)));
        final XsltGenerator.Generated checked = generate(mappingOf(draft), SCHEMA_352, "3.5.2");
        final List<String> warnings = MappingCompaction.grouped(checked.warnings());
        draft.put("schema_check", map("ok", checked.ok(), "problems", MappingCompaction.grouped(checked.problems()),
                "warnings", warnings.subList(0, Math.min(6, warnings.size()))));
        assertThat(ToolJson.dumpsCompact(draft).length()).isLessThan(8000);
    }

    @Test
    void theDraftFoldsItsKeyValueExtractsIntoPairsAndMeansTheSame() {
        final Map<String, Object> drafted = MappingCompaction.compactRules(mappingOf(draft(
                samples("test fortigate"), "Fortigate")));
        final Map<String, Object> folded = MappingCompaction.compactPairs(drafted);
        assertThat(folded).doesNotContainKey("extract");
        final List<Map<String, Object>> pairs = JsonValues.maps(folded.get("pairs"));
        assertThat(pairs.stream().map(p -> p.get("style")).distinct().sorted().toList())
                .isEqualTo(List.of("equals", "quoted"));
        final List<String> keys = new ArrayList<>();
        pairs.forEach(p -> keys.addAll(strings(p.get("keys"))));
        final List<String> extractKeys = JsonValues.maps(drafted.get("extract")).stream()
                .map(e -> Pairs.keyName(Pairs.pairOf((String) e.get("regex")).orElseThrow().key()))
                .sorted()
                .toList();
        assertThat(keys.stream().sorted().toList()).isEqualTo(extractKeys);
        assertThat(ToolJson.dumps(folded.get("pairs")).length())
                .isLessThan(ToolJson.dumps(drafted.get("extract")).length() / 3);
        assertThat(TranslationMapping.fromJson(folded).extract().stream().map(e -> e.toString()).sorted().toList())
                .isEqualTo(TranslationMapping.fromJson(drafted).extract().stream().map(e -> e.toString()).sorted()
                        .toList());
    }

    @SuppressWarnings("unchecked")
    private static List<String> strings(final Object value) {
        return (List<String>) value;
    }

    @Test
    void aSamplesRefusalSaysWhy() {
        assertThatThrownBy(() -> draft(List.of(42), "S")).isInstanceOf(ToolRefusal.class)
                .hasMessageContaining("a sample is the file's text, not int");
    }

    // Drafts of the formats sources send.

    private static Map<String, Map<String, Object>> formatRules(final String name) {
        return rules(draft(List.of(format(name)), ""));
    }

    @Test
    void cefNamesTheKindWithItsHeadersName() {
        assertThat(formatRules("cef").keySet()).contains("user_logged_in_logon", "user_logged_out_logoff",
                "login_failed");
    }

    @Test
    void cefsNameIsTakenForTheKindOverAnActThatSaysWhatWasDone() {
        // Seen in testing (chat 152): NetScaler's CEF split by act (blocked or not), not by its name.
        final String text = String.join("\n",
                "CEF:0|Citrix|NetScaler|NS13.1|APPFW|APPFW_SQL|6|src=10.0.0.1 act=blocked",
                "CEF:0|Citrix|NetScaler|NS13.1|APPFW|APPFW_XSS|6|src=10.0.0.2 act=not blocked",
                "CEF:0|Citrix|NetScaler|NS13.1|APPFW|APPFW_STARTURL|6|src=10.0.0.3 act=transformed",
                "CEF:0|Citrix|NetScaler|NS13.1|APPFW|APPFW_SQL|6|src=10.0.0.4 act=not blocked") + "\n";
        final Map<String, Map<String, Object>> rules = rules(draft(List.of(text), ""));
        assertThat(rules.keySet()).contains("appfw_sql", "appfw_xss", "appfw_starturl")
                .doesNotContain("blocked", "not_blocked");
    }

    @Test
    void cefsNameTheSameInEveryRecordLeavesTheKindToAnotherField() {
        final String text = String.join("\n",
                "CEF:0|Acme|Gateway|2.1|100|Access|3|suser=alice act=login",
                "CEF:0|Acme|Gateway|2.1|100|Access|3|suser=bob act=logout",
                "CEF:0|Acme|Gateway|2.1|100|Access|3|suser=carol act=login") + "\n";
        assertThat(rules(draft(List.of(text), "")).keySet()).contains("login", "logout");
    }

    @Test
    void aDoubledQuoteStaysDoubledAsInStroomAndTheDraftUnescapesIt() {
        final Map<String, Object> message = JsonValues.maps(mappingOf(draft(List.of(format(
                "csv_quoted")), "")).get("common")).stream()
                .filter(f -> "message".equals(f.get("field")))
                .findFirst()
                .orElseThrow();
        assertThat(message.get("transform")).isEqualTo("unescape_quotes");
    }

    @Test
    void syslogNilValuesAreLeftOutAndNeverNameTheKind() {
        final Map<String, Object> drafted = draft(List.of(format("syslog5424_kv")), "");
        assertThat(mappingOf(drafted).get("nil_values")).isEqualTo(List.of("-"));
        // Not msgid's '-'.
        assertThat(rules(drafted).keySet()).containsExactlyInAnyOrder("permitted", "denied", "other");
        assertThat(JsonValues.maps(rules(drafted).get("permitted").get("fields"))).anyMatch(f ->
                ((String) f.get("path")).endsWith("Network/Permit/Source/Device/IPAddress") && "src".equals(f.get(
                        "field")));
        assertThat(generate(mappingOf(drafted), SCHEMA, "4.1.0").xslt())
                .contains("[not(normalize-space(.) = ('-'))]");
    }

    @Test
    void theDraftReadsAttributesNestedKeysWindowsEventDataAndTheAgentsSplitter() {
        // action="login" on an element.
        assertThat(formatRules("xml_attributes").keySet()).containsExactlyInAnyOrder("login", "logout", "other");
        // user.name is user_name.
        assertThat(JsonValues.maps(mappingOf(draft(List.of(format("json_lines")), "")).get("common")))
                .contains(map("path", "EventSource/User/Id", "field", "user.name"));
        // Windows events: a rule for each event id, its EventData read by Name. What an id means isn't known here
        // (asked for by the user: the tools hold no one source's handling): it comes from the agent, the user's
        // documentation (theDraftFollowsTheDocumentationAndGenerates) and the user.
        final Map<String, Map<String, Object>> windows = formatRules("xml_fragments_ns");
        assertThat(windows).containsKeys("4624", "4634");
        assertThat(String.valueOf(windows.get("4624"))).contains("data_name=EventData/Data/@Name")
                .doesNotContain("EventDetail/Authenticate");
        final SplitterSpec named = SplitterSpec.builder("delimited").header(List.of("time", "user", "src_ip",
                "action")).build();
        assertThat(rules(DraftMapping.draft(List.of(format("csv_noheader")), "", null, null, null, null,
                named)).keySet()).containsExactlyInAnyOrder("login", "logout", "other");
        // A real array isn't JSON text.
        assertThat(mappingOf(draft(List.of(format("json_array_nested")), "")))
                .doesNotContainKey("json_fields");
    }

    @Test
    void theRuleForTheRestAlwaysHasAnActionElement() {
        // Seen: every field mapped already left the catch-all with nothing, and the whole mapping failed.
        for (final String name : List.of("csv_quoted", "json_lines", "xml_records")) {
            assertThat(JsonValues.maps(formatRules(name).get("other").get("fields")))
                    .anyMatch(f -> ((String) f.get("path")).startsWith("EventDetail/Unknown/"));
        }
    }

    // Drafts of record shapes, sample profiles, times and stream meta.

    @Test
    void everyShapeIsReadAndJsonHeldInAFieldIsReadInside() {
        final Map<String, Object> draft = DraftMapping.draft(samples("test shapes"), "App", "App",
                "Prod", null, null, null);
        final Map<String, Object> mapping = mappingOf(draft);
        final Map<String, Map<String, Object>> common = common(draft);
        assertThat(mapping.get("json_fields")).isEqualTo(List.of("message"));
        assertThat(common.get("EventTime/TimeCreated").get("any_of")).isEqualTo(List.of("timestamp", "@timestamp"));
        assertThat(common.get("EventDetail/TypeId").get("any_of")).isEqualTo(List.of("event_type",
                "message.event_type"));
        assertThat(common.get("EventSource/User/Id").get("any_of")).isEqualTo(List.of("user", "message.user"));
        // The JSON text itself isn't written into an element.
        assertThat(common).doesNotContainKey("EventDetail/Description");
        assertThat(rules(draft).keySet()).contains("login", "logout", "user_authentication_check", "password_change");
        assertThat(notes(draft)).anyMatch(n -> n.contains("2 shapes"))
                .anyMatch(n -> n.contains("'message' holds JSON"));
        // The local checks read the held keys as the XSLT does.
        final TranslationMapping model = TranslationMapping.fromJson(mapping);
        @SuppressWarnings("unchecked")
        final List<String> texts = new ArrayList<>(((Map<String, String>) samples("test shapes"))
                .values());
        final List<Object> records = LocalCheck.sampleRecords(model, texts, null).records();
        assertThat(records.stream().map(r -> LocalCheck.ruleOf(model, r, null)).toList()).containsExactly("login",
                "login", "logout", "user_authentication_check", "user_authentication_check", "password_change");
        assertThat(XsltGenerator.generate(model, SCHEMA_352, "3.5.2").ok()).isTrue();
    }

    @Test
    void oneShapeDraftsAsBefore() {
        final Map<String, Object> draft = DraftMapping.draft(samples("test one shape"), "App", "App",
                "Prod", null, null, null);
        assertThat(common(draft).get("EventTime/TimeCreated").get("field")).isEqualTo("timestamp");
        assertThat(mappingOf(draft)).doesNotContainKey("json_fields");
        assertThat(common(draft).get("EventDetail/TypeId").get("field")).isEqualTo("event_type");
    }

    @Test
    @SuppressWarnings("unchecked")
    void aDocumentReadOnlyToItsHeadDraftsAndChecksFromItsRecords() {
        final List<String> head = (List<String>) samples("test tweets head");
        final Map<String, Object> draft = draft(head, "Twitter");
        assertThat(mappingOf(draft).get("input")).isEqualTo("xml");
        assertThat(mappingOf(draft).get("record")).isEqualTo("tweet");
        final LocalCheck.SampleRecords records = LocalCheck.sampleRecords(TranslationMapping.fromJson(
                mappingOf(draft)), head.getFirst(), null);
        assertThat(records.records()).hasSize(30);
        assertThat(records.note()).isNull();
    }

    private static Map<String, Object> timeEntry(final Map<String, Object> draft) {
        return common(draft).get("EventTime/TimeCreated");
    }

    @Test
    void theMorePreciseTimeIsDraftedAndAClocksFractionKept() {
        final String w3c = """
                #Software: SGOS 7.3
                #Fields: date time x-timestamp-unix c-ip cs-username sc-status
                2026-10-11 08:00:00 1791712800.123 10.0.0.1 alice 200
                2026-10-11 08:00:01 1791712801.456 10.0.0.2 bob 403
                """;
        assertThat(timeEntry(draft(w3c, ""))).isEqualTo(map("path", "EventTime/TimeCreated", "field",
                "x-timestamp-unix", "time_format", "epoch_s"));
        final Map<String, Object> entry = timeEntry(draft("""
                date,time,user,action
                2026-10-11,08:00:00.123,alice,login
                2026-10-11,08:00:01.456,bob,logout
                """, ""));
        assertThat(entry.get("time_format")).isEqualTo("yyyy-MM-dd HH:mm:ss.SSS");
        assertThat((String) entry.get("xpath")).contains("concat(");
        // Without fractions anywhere, the date and time columns are joined as before.
        assertThat(timeEntry(draft("date,time,user\n2026-10-11,08:00:00,alice\n2026-10-11,08:00:01,bob\n", ""))
                .get("time_format")).isEqualTo("yyyy-MM-dd HH:mm:ss");
    }

    @Test
    void aZoneTheSourceNamesIsKeptWithTheEvent() {
        final Map<String, Object> draft = draft("""
                {"ts": "2026-10-01T18:00:00.123 Australia/Sydney", "user": "alice"}
                {"ts": "2026-10-01T18:01:00.456 Australia/Sydney", "user": "bob"}
                """, "");
        assertThat((String) timeEntry(draft).get("time_format")).endsWith(" VV");
        assertThat(timeEntry(draft)).doesNotContainKey("timezone");
        assertThat(JsonValues.maps(mappingOf(draft).get("common")))
                .contains(map("path", "EventSource/Device/Location/TimeZoneName", "field", "time_zone"));
        final Map<String, Object> extract = JsonValues.maps(mappingOf(draft).get("extract")).getFirst();
        assertThat(extract.get("names")).isEqualTo(List.of("time_zone"));
        assertThat(extract.get("field")).isEqualTo("ts");
        // A field of zone names, beside zone-less times.
        final Map<String, Object> zones = draft("""
                time,tz,user
                2026-10-01 18:00:00,Australia/Sydney,alice
                2026-10-01 18:01:00,Europe/London,bob
                """, "");
        assertThat(JsonValues.maps(mappingOf(zones).get("common")))
                .contains(map("path", "EventSource/Device/Location/TimeZoneName", "field", "tz"));
    }

    // Drafts that follow source notes.

    private static final String NOTES = """
            {"source": "Acme directory",
             "fields": [
              {"field": "evt", "meaning": "Event id"},
              {"field": "usr", "meaning": "Account name", "event_logging_path": "EventSource/User/Id"},
              {"field": "src", "meaning": "Workstation address", "event_logging_path": "EventSource/Client/IPAddress"},
              {"field": "res", "meaning": "Status code",
               "values": {"0x0": "success", "0xC000006A": "failed: bad password"}}],
             "events": [
              {"event": "4624", "description": "An account logged on", "event_detail": "Authenticate",
               "type_id": "Logon", "field": "evt", "value": "4624", "action": "Logon", "success": true},
              {"event": "4625", "description": "An account failed to log on", "event_detail": "Authenticate",
               "type_id": "Logon failed", "field": "evt", "value": "4625", "action": "Logon", "success": false},
              {"event": "4634", "description": "An account logged off", "event_detail": "Authenticate",
               "type_id": "Logoff", "field": "evt", "value": "4634", "action": "Logoff"},
              {"event": "4648", "description": "A logon with explicit credentials", "event_detail": "Authenticate",
               "type_id": "Explicit logon", "field": "evt", "value": "4648", "action": "Logon"}]}""";

    @Test
    @SuppressWarnings("unchecked")
    void theDraftFollowsTheDocumentationAndGenerates() {
        final Map<String, Object> draft = DraftMapping.draft(NOTES_SAMPLE, "Acme directory", "Acme", "Eval",
                json(NOTES), null, null);
        final Map<String, Map<String, Object>> common = common(draft);
        assertThat(common.get("EventSource/User/Id").get("field")).isEqualTo("usr");
        assertThat(common.get("EventSource/Client/IPAddress").get("field")).isEqualTo("src");
        final Map<String, Map<String, Object>> rules = rules(draft);
        final Map<String, Map<String, Object>> failed = fields(rules.get("4625"));
        assertThat(rules.get("4625").get("when")).isEqualTo(json("[{\"field\": \"evt\", \"equals\": \"4625\"}]"));
        assertThat(failed.get("EventDetail/TypeId").get("value")).isEqualTo("Logon failed");
        assertThat(failed.get("EventDetail/Authenticate/Action").get("value")).isEqualTo("Logon");
        assertThat(failed.get("EventDetail/Authenticate/Outcome/Success").get("value")).isEqualTo("false");
        assertThat(failed.get("EventDetail/Description").get("value")).isEqualTo("An account failed to log on");
        assertThat(fields(rules.get("4634")).get("EventDetail/Authenticate/Action").get("value")).isEqualTo("Logoff");
        // Documented, though the sample has none.
        assertThat(rules).containsKey("4648");
        final Map<String, Object> applied = (Map<String, Object>) draft.get("source_notes");
        assertThat(applied.get("not_in_sample")).isEqualTo(List.of("evt=4648"));
        assertThat(applied.get("not_in_catalogue")).isEqualTo(List.of("evt=9999"));
        assertThat(notes(draft).getFirst()).startsWith("Drafted from the source documentation");
        assertThat(notes(draft)).anyMatch(n -> n.contains("evt=9999"));
        final XsltGenerator.Generated checked = generate(mappingOf(draft), SCHEMA, "4.1.0");
        assertThat(checked.ok()).as("%s", checked.problems()).isTrue();
    }

    @Test
    void whoActedAndWhatWasActedOnAreDraftedWhereTheCatalogueSays() {
        // Asked for by the user: a kind's actor is its event's user, and what it was done to its action element's
        // object, so an action element without an Action (Delete, Create) isn't left empty.
        final List<String> lines = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            lines.add("ts=2026-11-06T09:%02d:00Z evt=%s subject=admin%d target=user%d host=dc01".formatted(i,
                    i % 2 == 0
                            ? "4720"
                            : "4726", i % 3, i));
        }
        final Map<String, Object> notes = json("""
                {"source": "Directory", "fields": [], "events": [
                  {"event": "4720", "description": "An account was created", "event_detail": "Create",
                   "field": "evt", "value": "4720", "actor": "subject", "affected": "target",
                   "affected_as": "User/Id", "basis": "user_confirmed"},
                  {"event": "4726", "description": "An account was deleted", "event_detail": "Delete",
                   "field": "evt", "value": "4726", "actor": "subject", "affected": "target",
                   "affected_as": "User/Id", "basis": "user_confirmed"}]}""");
        final Map<String, Object> draft = DraftMapping.draft(List.of(String.join("\n", lines) + "\n"), "Directory",
                "Directory", "Test", notes, detail -> SourceNotes.detailProblem(SCHEMA, detail), null);
        final Map<String, Map<String, Object>> deleted = fields(rules(draft).get("4726"));
        assertThat(deleted.get("EventSource/User/Id").get("field")).isEqualTo("subject");
        assertThat(deleted.get("EventDetail/Delete/User/Id").get("field")).isEqualTo("target");
        assertThat(fields(rules(draft).get("4720")).get("EventDetail/Create/User/Id").get("field")).isEqualTo("target");
        final XsltGenerator.Generated checked = generate(mappingOf(draft), SCHEMA, "4.1.0");
        assertThat(checked.problems()).noneMatch(problem -> problem.contains("needs one of"));
    }

    @Test
    void aClassifiedKindKeepsWhatTheSamplesRuleReadAndItsOwnMessage() {
        // Seen in the agent eval: a catalogue rule wrote one fixed description over each record's message, and its
        // actor, a label rather than a field, left the event's user empty.
        final List<String> lines = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            lines.add("ts=2026-11-06T09:%02d:00Z action=%s user=user%d src=10.0.0.%d msg=\"said %d\"".formatted(i,
                    i % 2 == 0
                            ? "login"
                            : "logout", i % 3, i, i));
        }
        final Map<String, Object> notes = json("""
                {"source": "App", "fields": [], "events": [
                  {"event": "login", "description": "A user logged on", "event_detail": "Authenticate",
                   "action": "Logon", "field": "action", "value": "login", "actor": "By User",
                   "basis": "user_confirmed"},
                  {"event": "logout", "description": "A user logged off", "event_detail": "Authenticate",
                   "action": "Logoff", "field": "action", "value": "logout", "actor": "src",
                   "basis": "user_confirmed"}]}""");
        final Map<String, Object> draft = DraftMapping.draft(List.of(String.join("\n", lines) + "\n"), "App", "App",
                "Test", notes, detail -> SourceNotes.detailProblem(SCHEMA, detail), null);
        final Map<String, Map<String, Object>> login = fields(rules(draft).get("login"));
        assertThat(login).doesNotContainKey("EventDetail/Description");
        assertThat(common(draft).get("EventDetail/Description").get("field")).isEqualTo("msg");
        assertThat(login.get("EventDetail/Authenticate/Action").get("value")).isEqualTo("Logon");
        // Neither a label nor an address is the event's user: the common mapping stands.
        assertThat(login).doesNotContainKey("EventSource/User/Id");
        assertThat(fields(rules(draft).get("logout"))).doesNotContainKey("EventSource/User/Id");
        assertThat(notes(draft)).anyMatch(note -> note.contains("login: actor 'By User' isn't a field of the "
                                                                + "records")
                                                  && note.contains("logout: actor 'src' holds addresses"));
    }

    @Test
    void aClassifiedKindIsDescribedByTheCommentAPersonWrote() {
        // Seen in the agent eval (case 42): every ticket event described as 'A ticket is closed', its comment kept
        // as Data.
        final List<String> lines = new ArrayList<>(List.of("time,action,user,ticket,comment"));
        for (int i = 0; i < 12; i++) {
            lines.add("2026-11-06T09:%02d:00Z,%s,user%d,T-%d,said thing %d".formatted(i, i % 2 == 0
                    ? "create"
                    : "close", i % 3, i, i));
        }
        final Map<String, Object> notes = json("""
                {"source": "Desk", "fields": [], "events": [
                  {"event": "create", "description": "A ticket is raised", "event_detail": "Create",
                   "field": "action", "value": "create", "basis": "user_confirmed"},
                  {"event": "close", "description": "A ticket is closed", "event_detail": "Update",
                   "field": "action", "value": "close", "basis": "user_confirmed"}]}""");
        final Map<String, Object> draft = DraftMapping.draft(List.of(String.join("\n", lines) + "\n"), "Desk", "Desk",
                "Test", notes, detail -> SourceNotes.detailProblem(SCHEMA, detail), null);
        final Map<String, Map<String, Object>> closed = fields(rules(draft).get("close"));
        assertThat(closed.get("EventDetail/Description").get("field")).isEqualTo("comment");
        assertThat(JsonValues.maps(rules(draft).get("close").get("fields")))
                .noneMatch(field -> "comment".equals(field.get("data_name")));
    }

    @Test
    void anAffectedObjectIsCheckedAgainstTheActionElement() {
        assertThatThrownBy(() -> SourceNotes.affectedPath(SCHEMA, "Network/Permit", "Destination"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("name its leaf, one of Destination/")
                .hasMessageContaining("Destination/Device/IPAddress");
        assertThat(SourceNotes.affectedPath(SCHEMA, "Delete", "User")).isEqualTo("User/Id");
        assertThat(SourceNotes.affectedPath(SCHEMA, "Delete", "User/Name")).isEqualTo("User/Name");
        assertThatThrownBy(() -> SourceNotes.affectedPath(SCHEMA, "Delete", "Mailbox"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("affected_as 'Mailbox' isn't under Delete, which holds");
    }

    @Test
    void withoutNotesTheDraftGuessesAsBefore() {
        final Map<String, Object> draft = draft(NOTES_SAMPLE, "Acme directory", "Acme", "Eval");
        assertThat(draft.get("source_notes")).isNull();
        final Map<String, Object> rule = rules(draft).get("4625");
        assertThat(rule == null || JsonValues.maps(rule.get("fields")).stream()
                .noneMatch(f -> "EventDetail/Authenticate/Action".equals(f.get("path")))).isTrue();
    }

    @Test
    @SuppressWarnings("unchecked")
    void whereTheCatalogueCantBeFollowedTheSamplesOwnRulesAreKept() {
        final Map<String, Object> entry = cases("draft_cases.json").stream()
                .filter(e -> "test guessed catalogue".equals(e.get("name")))
                .findFirst()
                .orElseThrow();
        final Map<String, Object> draft = DraftMapping.draft(entry.get("samples"), "Firewall", "FW", "Eval",
                (Map<String, Object>) entry.get("source_notes"),
                detail -> SourceNotes.detailProblem(SCHEMA, detail), null);
        final Map<String, Map<String, Object>> rules = rules(draft);
        assertThat(networkElements(rules.get("traffic_permitted"))).containsExactly("Permit");
        assertThat(networkElements(rules.get("traffic_denied"))).containsExactly("Deny");
        assertThat(rules.values()).allMatch(r -> JsonValues.maps(r.get("fields")).stream().noneMatch(f ->
                ((String) f.get("path")).startsWith("EventDetail/Allow")
                || ((String) f.get("path")).startsWith("EventDetail/Deny")));
        assertThat(notes(draft)).anyMatch(n -> n.startsWith("Kept from the sample's values")
                                               && n.contains("event_type=TRAFFIC"));
        // The catalogue's own events, completed from the sample's rule for the same records where the schema
        // wants more.
        final Map<String, Map<String, Object>> login = fields(rules.get("login_success"));
        assertThat(login).containsKey("EventDetail/Authenticate/Action");
        assertThat(login.get("EventDetail/Authenticate/Outcome/Success").get("value")).isEqualTo("true");
        assertThat(fields(rules.get("config_change"))).containsKey("EventDetail/Update/After/Configuration/Type");
        // Unknown is left for the user to agree when the XSLT is built, never agreed by the draft.
        assertThat(rules.values()).noneMatch(r -> JsonValues.truthy(r.get("allow_unknown")));
        final XsltGenerator.Generated checked = generate(mappingOf(draft), SCHEMA, "4.1.0");
        assertThat(checked.problems()).allMatch(p -> p.contains("vpn_down"));
    }

    private static Set<String> networkElements(final Map<String, Object> rule) {
        final Set<String> out = new HashSet<>();
        JsonValues.maps(rule.get("fields")).stream()
                .map(f -> (String) f.get("path"))
                .filter(path -> path.startsWith("EventDetail/Network/"))
                .forEach(path -> out.add(path.split("/")[2]));
        return out;
    }

    // Helpers drafted from.

    @Test
    void pairsInATextAreReadAsTheXsltReadsThem() {
        final Map<String, List<String>> values = new LinkedHashMap<>();
        values.put("msg", List.of("user=alice action=\"log in\" src=10.0.0.1", "user=bob action=\"log out\""));
        values.put("user", List.of("alice"));
        final Map<String, DraftMapping.PairExtracts> found = DraftMapping.pairExtracts(values, Set.of());
        assertThat(found.keySet()).containsExactly("msg");
        final List<Map<String, Object>> extracts = found.get("msg").extracts();
        // The key's name is taken: user_2.
        assertThat(extracts.stream().map(e -> e.get("names")).toList()).containsExactly(List.of("user_2"),
                List.of("action"), List.of("src"));
        assertThat(extracts.get(1).get("regex")).isEqualTo(Pairs.pairRegex("quoted", "action"));
        assertThat(found.get("msg").whole()).isTrue();
        final List<Map<String, Object>> records = new ArrayList<>(List.of(
                new LinkedHashMap<>(map("msg", values.get("msg").getFirst()))));
        DraftMapping.withPairs(records, extracts);
        assertThat(records.getFirst()).containsEntry("user_2", "alice").containsEntry("action", "log in")
                .containsEntry("src", "10.0.0.1");
        // Text without pairs in most values isn't read for them.
        assertThat(DraftMapping.pairExtracts(Map.of("msg", List.of("a=1 b=2", "plain", "text")), Set.of()))
                .isEmpty();
        assertThat(DraftMapping.pairExtracts(Map.of("msg", values.get("msg")), Set.of("msg"))).isEmpty();
    }

    @Test
    void pairsASpacedDashPartsAreReadAsFieldsTheirQuotesOff() {
        // Seen in chat 163: a gateway's bodies written "Key value - Key value", read by some 25 extract entries the
        // agent wrote by hand, one over-capturing every logout's method.
        final Map<String, List<String>> values = new LinkedHashMap<>();
        values.put("body", List.of(
                "Context grace@203.0.113.9 - SessionId: 2639 - User grace - Client_ip 203.0.113.33 - Nat_ip "
                + "\"Mapped Ip\" - Vserver 192.0.2.10:443 - Browser_type \"Citrix Workspace App 2409\"",
                "Context dave@203.0.113.12 - SessionId: 2640 - User dave - Client_ip 203.0.113.40 - Nat_ip "
                + "\"Mapped Ip\" - Vserver 192.0.2.10:443 - Browser_type \"Citrix Workspace App 2409\"",
                "Context erin@203.0.113.15 - SessionId: 2641 - User erin - Client_ip 203.0.113.51 - LogoutMethod "
                + "\"IdleTimeout\" - Group(s) \"Staff\"",
                "Context frank@203.0.113.16 - SessionId: 2642 - User frank - Client_ip 203.0.113.52 - LogoutMethod "
                + "\"Explicit\" - Group(s) \"Staff - North\" - Duration 00:10:02"));
        final Map<String, DraftMapping.PairExtracts> found = DraftMapping.pairExtracts(values, Set.of());
        assertThat(found.keySet()).containsExactly("body");
        final List<Map<String, Object>> extracts = found.get("body").extracts();
        final List<Map<String, Object>> records = new ArrayList<>(List.of(
                new LinkedHashMap<>(map("body", values.get("body").getFirst())),
                new LinkedHashMap<>(map("body", values.get("body").get(2))),
                new LinkedHashMap<>(map("body", values.get("body").getLast()))));
        DraftMapping.withPairs(records, extracts);
        assertThat(records.getFirst()).containsEntry("user", "grace").containsEntry("client_ip", "203.0.113.33")
                .containsEntry("nat_ip", "Mapped Ip").containsEntry("vserver", "192.0.2.10:443")
                .containsEntry("browser_type", "Citrix Workspace App 2409").containsEntry("sessionid", "2639");
        // Each value to its own dash: not the rest of the text (seen: a logout's method read as
        // '"IdleTimeout" - Group(s) "Staff"').
        assertThat(records.get(1)).containsEntry("logoutmethod", "IdleTimeout")
                .containsEntry("group_s", "Staff");
        // A quoted value to its closing quote: a spaced dash inside it is the value's, not the next pair's.
        assertThat(records.getLast()).containsEntry("group_s", "Staff - North").containsEntry("duration", "00:10:02")
                .containsEntry("user", "frank");
        // One spaced dash is as likely prose: not read for pairs.
        assertThat(DraftMapping.pairExtracts(Map.of("msg", List.of("Backup done - all good", "Restore done - "
                                                                                             + "partly", "x")),
                Set.of())).isEmpty();
    }

    @Test
    @SuppressWarnings("unchecked")
    void aMailLogsRecipientsReachTheMappingOnceAndWhole() {
        // Seen in testing: a mail gateway's syslog key=value lines. The splitter reads the pairs, and the draft read
        // its message for them again (every key twice, a list cut at its first comma).
        for (final String separator : List.of(",", "; ")) {
            final String text = IntStream.range(0, 4)
                    .mapToObj(r -> "<22>Nov 10 10:00:0" + r + " mail01 mailgw[123]: id=q" + r
                                   + " from=alice@example.org recipients=" + IntStream.range(0, 300)
                                           .mapToObj(n -> "u" + n + "@example.org")
                                           .collect(Collectors.joining(separator)) + " subject=\"Hi there\" size=5")
                    .collect(Collectors.joining("\n"));
            final Map<String, Object> draft = draft(Map.of("mail.log", text), "Mail");
            final Map<String, Object> mapping = mappingOf(draft);

            assertThat(mapping).as(separator).doesNotContainKey("extract");
            assertThat(String.valueOf(mapping)).contains("data_name=recipients, field=recipients")
                    .doesNotContain("recipients_2").doesNotContain("data_name=words");
            assertThat((List<String>) draft.get("notes")).anySatisfy(note -> assertThat(note)
                    .startsWith("'message' is the text the splitter read its key/value pairs from"));
        }
    }

    @Test
    void aMailsSubjectLineIsNotTheEventsUser() {
        // Seen in testing: a mail log's subject drafted as EventSource/User/Id.
        final String text = IntStream.range(0, 6)
                .mapToObj(r -> "time=2026-11-10T09:00:0" + r + "Z host=mx01 from=alice@example.org"
                               + " rcpt=bob@example.org subject=\"" + (r % 2 == 0
                        ? "Q3 report"
                        : "Invoice " + r) + "\" size=5")
                .collect(Collectors.joining("\n"));
        final Map<String, Object> mapping = mappingOf(draft(Map.of("mail.log", text), "Mail"));

        assertThat(String.valueOf(mapping)).doesNotContain("path=EventSource/User/Id, field=subject");
    }

    @Test
    void aMessagesOwnIdIsNotTheKindsOfEvent() {
        // Seen in testing: a mail log's msgid (a different value in every record) drafted as EventDetail/TypeId,
        // a rule a message, over its action.
        final String text = IntStream.range(0, 6)
                .mapToObj(r -> "time=2026-11-10T09:00:0" + r + "Z host=mx01 msgid=<" + r + "@mx01> action="
                               + (r % 2 == 0
                        ? "deliver"
                        : "reject") + " size=5")
                .collect(Collectors.joining("\n"));
        final Map<String, Object> draft = draft(Map.of("mail.log", text), "Mail");

        assertThat(common(draft).get("EventDetail/TypeId").get("field")).isEqualTo("action");
        assertThat(rules(draft).keySet()).contains("deliver", "reject");
    }

    @Test
    void aSubjectHoldingAccountsIsTheEventsUser() {
        // In an access log the subject is who acted.
        final String text = IntStream.range(0, 6)
                .mapToObj(r -> "time=2026-11-10T09:00:0" + r + "Z host=dc01 subject=CORP\\\\user" + r
                               + " action=logon")
                .collect(Collectors.joining("\n"));
        final Map<String, Object> mapping = mappingOf(draft(Map.of("access.log", text), "Access"));

        assertThat(String.valueOf(mapping)).contains("path=EventSource/User/Id, field=subject");
    }

    @Test
    void aPairNamedLikeAHeaderFieldIsStillReadFromTheMessage() {
        // The splitter's record holds the header's time, not the message's time= pair: reading the message again
        // is the only way to reach that pair, so the text isn't taken as read already.
        final String text = IntStream.range(0, 4)
                .mapToObj(r -> "<22>Nov 10 10:00:0" + r + " host01 app[123]: time=2026-11-10T09:00:0" + r
                               + "Z user=alice action=login")
                .collect(Collectors.joining("\n"));
        final Map<String, Object> draft = draft(Map.of("app.log", text), "App");

        assertThat(String.valueOf(mappingOf(draft))).contains("time_2");
    }

    @Test
    @SuppressWarnings("unchecked")
    void aKeyGivenOnceAValueIsWrittenForEachValue() {
        // The splitter writes each rcpt= as a data element of its own; the dry run's record keeps the first, and
        // the draft's Data entry writes them all.
        final String text = IntStream.range(0, 4)
                .mapToObj(r -> "<22>Nov 10 10:00:0" + r + " mail01 mailgw[123]: id=q" + r
                               + " from=alice@example.org rcpt=a" + r + "@example.org rcpt=b" + r
                               + "@example.org rcpt=c" + r + "@example.org subject=\"rcpt=x rcpt=y\" size=5")
                .collect(Collectors.joining("\n"));
        final Map<String, Object> draft = draft(Map.of("mail.log", text), "Mail");
        final List<Map<String, Object>> fields = new ArrayList<>(JsonValues.maps(((Map<String, Object>) JsonValues
                .maps(mappingOf(draft).get("events")).getFirst()).get("fields")));

        assertThat(fields).filteredOn(field -> "rcpt".equals(field.get("field"))).singleElement()
                .satisfies(field -> assertThat(field).containsEntry("data_name", "rcpt").containsEntry("repeat",
                        true));
        // A key given once is read once.
        assertThat(fields).filteredOn(field -> "from".equals(field.get("field"))).singleElement()
                .satisfies(field -> assertThat(field).doesNotContainKey("repeat"));
        assertThat((List<String>) draft.get("notes")).anySatisfy(note -> assertThat(note)
                .startsWith("'rcpt' is given more than once in a record"));
    }

    @Test
    void aListInAPairsValueIsReadWhole() {
        // Seen in testing: recipients=a@y,b@y read as its first address from a text field's pairs.
        final Map<String, List<String>> values = Map.of("msg", List.of(
                "from=alice@example.org recipients=a@example.org,b@example.org action=sent",
                "from=bob@example.org recipients=c@example.org,d@example.org;e@example.org action=sent",
                "from=carl@example.org recipients=f@example.org action=held",
                "a=1,b=2 from=dan@example.org recipients=g@example.org"));
        final List<Map<String, Object>> extracts = DraftMapping.pairExtracts(values, Set.of()).get("msg").extracts();
        final List<Map<String, Object>> records = new ArrayList<>();
        values.get("msg").forEach(value -> records.add(new LinkedHashMap<>(map("msg", value))));
        DraftMapping.withPairs(records, extracts);

        assertThat(records).extracting(record -> record.get("recipients")).containsExactly(
                "a@example.org,b@example.org", "c@example.org,d@example.org;e@example.org", "f@example.org",
                "g@example.org");
        // Pairs parted by a comma are still two pairs.
        assertThat(records.getLast()).containsEntry("a", "1").containsEntry("b", "2");
        assertThat(extracts).anySatisfy(extract -> assertThat(extract).containsEntry("regex",
                Pairs.pairRegex("listed", "recipients")));
    }

    @Test
    void aQuoteLeftOpenInADashedPairIsReadToTheNextSpacedDash() {
        // Seen in testing: a browser's name with its quote never closed was read as nothing, or (a quoted value
        // later in the text) to that value's opening quote.
        final String open = "Context john.smith@example.com - User john.smith@example.com - Client_ip 123.0.0.123 "
                            + "- Browser_type \"Mozilla/5.0 (Windows NT 10.0; Win64; x64) - SSLVPN_client_type "
                            + "Clientless";
        final String quotedLater = open + " - Group(s) \"N/A\"";
        final String balanced = "Context ann@example.com - User ann@example.com - Client_ip 10.0.0.9 - Browser_type "
                                + "\"Edge 120\" - SSLVPN_client_type Agent - Group(s) \"Staff - North\"";
        for (final List<String> body : List.of(List.of(open, open), List.of(quotedLater, quotedLater),
                List.of(open, balanced, quotedLater))) {
            final Map<String, DraftMapping.PairExtracts> found = DraftMapping.pairExtracts(Map.of("body", body),
                    Set.of());
            final List<Map<String, Object>> records = new ArrayList<>();
            body.forEach(text -> records.add(new LinkedHashMap<>(map("body", text))));
            DraftMapping.withPairs(records, found.get("body").extracts());

            assertThat(records.getFirst())
                    .containsEntry("browser_type", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                    .containsEntry("sslvpn_client_type", "Clientless")
                    .containsEntry("client_ip", "123.0.0.123");
            if (body.size() == 3) {
                // A balanced quote is still read to its closing quote, a spaced dash inside it kept.
                assertThat(records.get(1)).containsEntry("browser_type", "Edge 120")
                        .containsEntry("group_s", "Staff - North");
                assertThat(records.getLast()).containsEntry("group_s", "N/A")
                        .containsEntry("sslvpn_client_type", "Clientless");
            }
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void eachRulesDataIsWhatItsOwnRecordsHold() {
        // Seen: 23 SecretServer rules, each given the same 63 Data entries (a logon's checkout_id and old_role
        // among them), which the agent kept beside the elements it then mapped them to.
        final String sample = """
                {"ts": "2026-09-28T11:00:00Z", "event": "User", "user": "pat", "mfa_method": "totp"}
                {"ts": "2026-09-28T11:01:00Z", "event": "User", "user": "sam", "mfa_method": "sms"}
                {"ts": "2026-09-28T11:02:00Z", "event": "Secret", "user": "pat", "secret_id": "10023"}
                {"ts": "2026-09-28T11:03:00Z", "event": "Secret", "user": "sam", "secret_id": "10024"}
                """;
        final Map<String, Object> mapping = mappingOf(draft(Map.of("s.json", sample), "Vault"));
        final Map<String, List<String>> data = new LinkedHashMap<>();
        for (final Object rule : (List<Object>) mapping.get("events")) {
            final Map<String, Object> drafted = (Map<String, Object>) rule;
            data.put((String) drafted.get("name"), ((List<Map<String, Object>>) drafted.get("fields")).stream()
                    .filter(field -> field.containsKey("data_name"))
                    .map(field -> (String) field.get("data_name"))
                    .toList());
        }

        assertThat(data.get("user")).contains("mfa_method").doesNotContain("secret_id");
        assertThat(data.get("secret")).contains("secret_id").doesNotContain("mfa_method");
    }

    @Test
    void aSampleWithNoTextGivesNoDraft() {
        assertThatThrownBy(() -> DraftMapping.records(Map.of(), null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void theKindFieldTheUserChoseMakesTheRules() {
        // Seen in testing: a CSV mail log's kind in msg_act, a name the draft didn't know, so every record was one
        // 'other' event. The draft doesn't guess a field by its values: classify_kinds and survey_feed put the
        // candidates to the user, and the draft follows what they chose.
        assertThat(rules(DraftMapping.draft(List.of(msgActCsv()), "SEG", null, null, null, null, null)).keySet())
                .containsExactly("other");
        final Map<String, Object> byAct = DraftMapping.draft(List.of(msgActCsv()), "SEG", null, null,
                Map.of(SourceNotes.KIND_FIELDS, List.of("msg_act")), null, null);
        final Map<String, Object> byDirection = DraftMapping.draft(List.of(msgActCsv()), "SEG", null, null,
                Map.of(SourceNotes.KIND_FIELDS, List.of("direction")), null, null);
        final Map<String, Object> oneKind = DraftMapping.draft(List.of(msgActCsv()), "SEG", null, null,
                Map.of(SourceNotes.KIND_FIELDS, List.of(KindFields.ONE_KIND)), null, null);

        assertThat(rules(byAct).keySet()).contains("allow", "quarantine", "reject");
        assertThat(rules(byDirection).keySet()).contains("inbound", "outbound").doesNotContain("allow");
        assertThat(rules(oneKind).keySet()).containsExactly("other");
    }

    @Test
    void recordsClassifiedAsOneKindAreAllTakenByItsRule() {
        // Asked for by the user: one kind classified like any other. Told by a field every sample record shares
        // (product=Gate), its rule is the rule for every record, so another product value isn't left to Unknown.
        final StringBuilder csv = new StringBuilder("time,product,user,src\n");
        for (int i = 0; i < 6; i++) {
            csv.append("2026-11-06T09:00:0%dZ,Gate,u%d,10.0.0.%d\n".formatted(i, i, i));
        }
        final Map<String, Object> event = Map.of("event", "Door opened", "field", "product", "value", "Gate",
                "event_detail", "Authenticate", "action", "Logon", "type_id", "Badge");
        final Map<String, Object> notes = Map.of("fields", List.of(), "events", List.of(event),
                SourceNotes.WHOLE_KIND, List.of("product=Gate"));

        final Map<String, Object> drafted = DraftMapping.draft(List.of(csv.toString()), "Gate", null, null, notes,
                null, null);

        final List<Map<String, Object>> rules = JsonValues.maps(mappingOf(drafted).get("events"));
        assertThat(rules).singleElement().satisfies(rule -> {
            assertThat(rule).doesNotContainKey("when");
            assertThat(String.valueOf(rule)).contains("Authenticate");
        });
        // A single event from the user's documentation isn't the records as one kind: its rule keeps its condition,
        // and the rule for the rest stays (found in review: other values would have been taken as that event).
        final Map<String, Object> documented = DraftMapping.draft(List.of(csv.toString()), "Gate", null, null,
                Map.of("fields", List.of(), "events", List.of(event)), null, null);
        assertThat(JsonValues.maps(mappingOf(documented).get("events"))).hasSize(2)
                .anySatisfy(rule -> assertThat(rule).containsKey("when"));
    }

    @Test
    void aKindFieldTheUserNamedIsMatchedAsTheRecordsSpellItAndOneTheyLackIsPassedOver() {
        final Map<String, Object> cased = DraftMapping.draft(List.of(msgActCsv()), "SEG", null, null,
                Map.of(SourceNotes.KIND_FIELDS, List.of("Msg_Act")), null, null);
        final Map<String, Object> absent = DraftMapping.draft(List.of(msgActCsv()), "SEG", null, null,
                Map.of(SourceNotes.KIND_FIELDS, List.of("verdict")), null, null);

        assertThat(rules(cased).keySet()).contains("allow", "quarantine", "reject");
        // Not every kind lost: chosen by name as without a choice.
        assertThat(rules(absent).keySet()).containsExactly("other");
    }

    private static String msgActCsv() {
        final List<String> lines = new ArrayList<>(List.of("timestamp,sender,recipient,subject,msg_act,direction"));
        for (int i = 0; i < 60; i++) {
            lines.add("2026-11-11T08:" + String.format("%02d", i) + ":04Z,u" + i + "@example.com,r" + i
                      + "@example.com,Subject " + i + "," + List.of("allow", "quarantine", "reject").get(i % 3) + ","
                      + (i % 2 == 0
                    ? "inbound"
                    : "outbound"));
        }
        return String.join("\n", lines) + "\n";
    }
}
