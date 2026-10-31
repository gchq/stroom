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

import stroom.ai.impl.tools.JsonValues;
import stroom.ai.impl.tools.generation.TranslationMapping;
import stroom.ai.impl.tools.generation.XsltGenerator;
import stroom.ai.impl.tools.sample.SplitterSpec;
import stroom.ai.impl.tools.sample.ToolJson;
import stroom.ai.impl.tools.sample.XmlTree;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/// A translation mapping checked against its sample before anything is created or stepped.
class TestLocalCheck extends DraftFixtures {

    private static final SplitterSpec CSV = specOf("{\"kind\": \"delimited\", \"delimiter\": \",\", "
                                                               + "\"header\": true}");

    @SuppressWarnings("unchecked")
    private static List<String> problems(final Map<String, Object> check) {
        return (List<String>) check.get("problems");
    }

    @SuppressWarnings("unchecked")
    private static List<String> warnings(final Map<String, Object> check) {
        return (List<String>) check.get("warnings");
    }

    private static List<Object> records(final TranslationMapping mapping, final Object sample,
                                        final SplitterSpec splitter) {
        return LocalCheck.sampleRecords(mapping, sample, splitter).records();
    }

    @Test
    void theMappingIsCheckedAgainstTheSampleBeforeStepping() {
        final TranslationMapping mapping = mapping(json("""
                {"input": "json", "json_layout": "lines", "common": [
                 {"path": "EventTime/TimeCreated", "field": "ts", "time_format": "yyyy-MM-dd HH:mm:ss"},
                 {"path": "EventSource/User/Id", "field": "usr"}],
                 "events": [{"name": "a", "fields": [{"path": "EventDetail/TypeId", "value": "x"}]}]}"""));
        final List<Object> records = records(mapping, """
                {"ts": "2026-10-01T10:00:00Z", "user": {"name": "a"}}
                {"ts": "2026-10-01T10:00:01Z", "user": {"name": "b"}}""", null);
        final Map<String, Object> check = LocalCheck.checkMapping(mapping, records);
        assertThat(check.get("records")).isEqualTo(2);
        assertThat(check.get("fields_seen")).isEqualTo(List.of("ts", "user.name"));
        assertThat(problems(check)).containsExactly("EventTime/TimeCreated: time_format 'yyyy-MM-dd HH:mm:ss' does "
                                                    + "not match the sample's values, e.g. ['2026-10-01T10:00:00Z', "
                                                    + "'2026-10-01T10:00:01Z']; the values look like "
                                                    + "\"yyyy-MM-dd'T'HH:mm:ssX\"");
        assertThat(warnings(check)).containsExactly(
                "field 'usr' (used for EventSource/User/Id) is in none of the 2 sample records. Fields seen: "
                + "['ts', 'user.name']",
                "fields in the sample that nothing reads: ['user.name']: map each to the element that means it (a "
                + "Data entry if nothing else fits), or leave it out on purpose.");
        // XML fragments: paths with attributes and predicates, by local name whatever the namespace.
        final Map<String, Object> fragmentsCase = check("12_xml_fragments_events reference");
        final TranslationMapping xmlMapping = mapping(fragmentsCase.get("mapping"));
        final List<Object> fragments = records(xmlMapping, fragmentsCase.get("texts"), null);
        assertThat(fragments).hasSize(3);
        final XmlTree.Element first = (XmlTree.Element) fragments.getFirst();
        assertThat(LocalCheck.xmlValue(first, "System/TimeCreated/@SystemTime")).isEqualTo("2026-10-01T10:00:00.000Z");
        assertThat(LocalCheck.xmlValue(first, "EventData/Data[@Name='IpAddress']")).isEqualTo("10.0.0.1");
        assertThat(problems(LocalCheck.checkMapping(xmlMapping, fragments))).isEmpty();
        // Data Splitter input needs the spec, else the check says it cannot look.
        final Map<String, Object> csvCase = check("14_csv_two_files_variants reference");
        final TranslationMapping csvMapping = mapping(csvCase.get("mapping"));
        @SuppressWarnings("unchecked")
        final List<String> texts = (List<String>) csvCase.get("texts");
        assertThat(LocalCheck.sampleRecords(csvMapping, texts.getFirst(), null).note()).startsWith("no splitter spec");
        // any_of covers both files' names.
        assertThat(warnings(LocalCheck.checkMapping(csvMapping, records(csvMapping, texts,
                spec(csvCase.get("splitter")))))).isEmpty();
    }

    @Test
    @SuppressWarnings("unchecked")
    void anXmlSamplesCheckListsItsFieldsAndSaysWhatItCouldNotCheck() {
        // Seen: 1000 XML records checked clean with fields_seen [], every rule testing a field extracted from an
        // xpath, so no record was checked against its rule and no field was said to be unread.
        final TranslationMapping m = mapping(json("""
                {"input": "xml_fragments", "record": "Event",
                 "xml_namespace": "http://schemas.microsoft.com/win/2004/08/events/event",
                 "extract": [{"xpath": ".//*:EventData/*:Data", "regex": "Event: \\\\[([^\\\\]]*)\\\\]",
                              "names": ["category"]}],
                 "common": [{"path": "EventTime/TimeCreated", "field": "System/TimeCreated/@SystemTime"},
                            {"path": "EventSource/Device/HostName", "field": "System/Computer"}],
                 "events": [{"name": "user", "when": [{"field": "category", "equals": "User"}],
                             "fields": [{"path": "EventDetail/Authenticate/Action", "value": "Logon"}]},
                            {"name": "other", "fields": [{"path": "EventDetail/Unknown/Data", "data_name": "c",
                             "field": "category"}], "allow_unknown": "for the test"}]}"""));
        final String audit = ((Map<String, String>) samples("test audit")).get("audit.xml");
        final Map<String, Object> check = LocalCheck.checkMapping(m, records(m, audit, null));
        assertThat((List<String>) check.get("fields_seen")).contains("System/Computer", "EventData/Data",
                "System/EventID");
        // Read through the xpath, EventData/Data is not unread; EventID and the Provider's Name are.
        final String unread = warnings(check).stream()
                .filter(w -> w.startsWith("fields in the sample that nothing reads"))
                .findFirst()
                .orElseThrow();
        assertThat(unread).contains("'System/Provider/@Name'", "'System/EventID'").doesNotContain("EventData/Data");
        assertThat(check.get("not_checked")).isEqualTo(List.of("the rule 5 of the 5 sample records fall to can't be "
                                                               + "told here (a condition on an xpath, or on a field "
                                                               + "extracted from one): Unknown and TypeId were not "
                                                               + "checked for them; step_sample shows what they "
                                                               + "become"));
    }

    @Test
    void aNearMissOfASampleFieldIsAProblemAndAFieldNothingLikeIsAWarning() {
        // Haiku mapped srcip, dstport and msg (FortiOS names) over a CSV with src_ip, dst_port and message.
        final String csv = "timestamp,src_ip,dst_port,message\n2026-10-01T09:00:12+10:00,192.0.2.10,443,allowed\n";
        final TranslationMapping m = mapping(json("""
                {"input": "data_splitter", "common": [
                 {"path": "EventTime/TimeCreated", "field": "timestamp", "time_format": "yyyy-MM-dd'T'HH:mm:ssXXX"},
                 {"path": "EventSource/Client/IPAddress", "field": "srcip"},
                 {"path": "EventSource/Device/HostName", "field": "appliance"}],
                 "events": [{"name": "any", "fields": [{"path": "EventDetail/TypeId", "value": "x"}]}]}"""));
        final Map<String, Object> check = LocalCheck.checkMapping(m, records(m, csv, CSV));
        assertThat(problems(check)).singleElement().satisfies(problem -> {
            assertThat(problem).startsWith("field 'srcip' (used for EventSource/Client/IPAddress)");
            assertThat(problem).contains("did you mean ['src_ip']");
        });
        // May be in other data.
        assertThat(warnings(check)).anyMatch(w -> w.startsWith("field 'appliance'"));
    }

    private static final String FIREWALL = """
            timestamp,device,event_type,action,username,message
            2026-10-01T09:00:12+10:00,FW-EDGE-01,TRAFFIC,ALLOW,,Outbound HTTPS allowed
            2026-10-01T09:11:15+10:00,FW-EDGE-01,ADMIN,LOGIN_SUCCESS,admin,Administrator logged in via web console
            2026-10-01T09:16:08+10:00,FW-EDGE-01,ADMIN,LOGOUT,admin,Administrator logged out
            2026-10-01T09:17:24+10:00,FW-EDGE-01,SYSTEM,VPN_TUNNEL_DOWN,,Site-to-site VPN tunnel branch-01 went down
            """;

    private static TranslationMapping firewallMapping(final String allowUnknown) {
        final Map<String, Object> admin = map("name", "admin", "when", json("[{\"field\": \"event_type\", "
                                                                            + "\"equals\": \"ADMIN\"}]"),
                "fields", json(UNKNOWN));
        if (allowUnknown != null) {
            admin.put("allow_unknown", allowUnknown);
        }
        final Map<String, Object> mapping = json("""
                {"input": "data_splitter", "common": [
                 {"path": "EventTime/TimeCreated", "field": "timestamp", "time_format": "yyyy-MM-dd'T'HH:mm:ssXXX"},
                 {"path": "EventSource/Device/HostName", "field": "device"},
                 {"path": "EventDetail/TypeId", "field": "action"}]}""");
        mapping.put("events", List.of(
                json("""
                        {"name": "traffic", "when": [{"field": "event_type", "equals": "TRAFFIC"}],
                         "fields": [{"path": "EventDetail/Network/Permit/Source/Device/HostName", "field": "device"}]}
                        """),
                admin,
                map("name", "other", "fields", json(UNKNOWN))));
        return mapping(mapping);
    }

    private static final String UNKNOWN = "[{\"path\": \"EventDetail/Unknown/Data\", \"data_name\": \"action\", "
                                          + "\"field\": \"action\"}]";

    @Test
    void theRecordsRulesKeepAsUnknownAreNamedByWhatTheyHold() {
        // Haiku kept a firewall's ADMIN records (logins) and SYSTEM records as Unknown, through allow_unknown: true.
        final TranslationMapping m = firewallMapping(null);
        final List<Object> records = records(m, FIREWALL, CSV);
        assertThat(records.stream().map(r -> LocalCheck.ruleOf(m, r, null)).toList())
                .containsExactly("traffic", "admin", "admin", "other");
        final List<String> problems = problems(LocalCheck.checkMapping(m, records));
        final String admin = problems.stream().filter(p -> p.startsWith("[admin]")).findFirst().orElseThrow();
        assertThat(admin).contains("keeps EventDetail/Unknown for 2 of the 4 sample records",
                "action: LOGIN_SUCCESS, LOGOUT", "username: admin",
                "e.g. message: \"Administrator logged in via web console\"");
        final String other = problems.stream().filter(p -> p.startsWith("[other]")).findFirst().orElseThrow();
        assertThat(other).contains("the rule for records no other rule matches", "VPN_TUNNEL_DOWN", "allow_unknown");
        // And the rules to add.
        assertThat(admin).contains("Authenticate for action LOGIN_SUCCESS; Authenticate for action LOGOUT");
        // A reason doesn't keep logons Unknown (seen in VS Code: the agent gave up on the schema and asked the user
        // to agree): refused, with the rules that describe them.
        final Map<String, Object> kept = LocalCheck.checkMapping(firewallMapping(
                "admin console activity has no action element"), records);
        final String refused = problems(kept).stream().filter(p -> p.startsWith("[admin]")).findFirst()
                .orElseThrow();
        assertThat(refused).contains("can't be kept as Unknown: 2 of its 2 sample records", "\"admin_logon\"");
        assertThat(kept).doesNotContainKey("kept_unknown");
    }

    @Test
    void aTypeIdSharedByDifferentKindsOfEventInTheSampleIsWarned() {
        // The SecretServer TypeId was its category ('Secret') for views, creates and deletes alike; a firewall's
        // action (already one value per kind) is fine though its rules test the event type as well.
        final List<Object> records = new ArrayList<>(List.of(
                new LinkedHashMap<>(map("category", "Secret", "action", "View", "item", "a")),
                new LinkedHashMap<>(map("category", "Secret", "action", "Delete", "item", "b"))));
        final List<String> warned = LocalCheck.sharedTypeIds(typed("category"), records, null);
        assertThat(warned).singleElement().asString().contains("'Secret' for rules delete, view");
        assertThat(LocalCheck.sharedTypeIds(typed("action"), records, null)).isEmpty();
    }

    private static TranslationMapping typed(final String typeField) {
        return mapping(json("""
                {"input": "data_splitter",
                 "common": [{"path": "EventSource/System/Name", "value": "A"},
                            {"path": "EventDetail/TypeId", "field": "%s"}],
                 "events": [
                  {"name": "view", "when": [{"field": "category", "equals": "Secret"},
                                            {"field": "action", "equals": "View"}],
                   "fields": [{"path": "EventDetail/View/Document/Name", "field": "item"}]},
                  {"name": "delete", "when": [{"field": "category", "equals": "Secret"},
                                              {"field": "action", "equals": "Delete"}],
                   "fields": [{"path": "EventDetail/Delete/Document/Name", "field": "item"}]}]}""".formatted(
                typeField)));
    }

    @Test
    @SuppressWarnings("unchecked")
    void theSampleCheckLooksInItemsAndRecordsByScope() {
        // Eval case 16, its batches' items one event each.
        final Map<String, Object> batches = check("16_json_batches_items reference");
        final Map<String, Object> plain = new LinkedHashMap<>((Map<String, Object>) batches.get("mapping"));
        plain.put("unmatched", "skip");
        final List<Map<String, Object>> common = new ArrayList<>();
        JsonValues.maps(plain.get("common")).forEach(e -> {
            final Map<String, Object> entry = new LinkedHashMap<>(e);
            entry.remove("time_format");
            common.add(entry);
        });
        common.add(map("path", "EventSource/User/Name", "field", "nickname"));
        plain.put("common", common);
        final TranslationMapping m = mapping(plain);
        final List<Object> records = records(m, ((List<String>) batches.get("texts")).getFirst(), null);
        final Map<String, Object> check = LocalCheck.checkMapping(m, records);
        assertThat(check.get("records")).isEqualTo(3);
        assertThat(warnings(check).stream().filter(w -> !w.startsWith("fields in the sample that nothing reads")))
                .singleElement().asString()
                .startsWith("field 'nickname' (used for EventSource/User/Name) is in none of the 5 sample items");
        assertThat((List<String>) check.get("fields_seen")).contains("roles", "host");
    }

    @Test
    void xmlHeldInAFieldIsReadByLocalName() {
        // A SOAP payload held in a JSON string.
        final String payload = "<soap:Envelope xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\"><soap:Body>"
                               + "<a:Login xmlns:a=\"urn:acme\"><a:User>alice</a:User><a:Account id=\"ACC-1\"/>"
                               + "</a:Login></soap:Body></soap:Envelope>";
        final TranslationMapping mapping = mapping(json("""
                {"input": "json", "json_layout": "lines", "xml_fields": ["payload"],
                 "common": [{"path": "EventSource/User/Id", "field": "payload//User"},
                            {"path": "EventDetail/TypeId", "value": "x"}],
                 "events": [{"name": "login", "fields": [{"path": "EventDetail/Authenticate/Action",
                                                          "value": "Logon"}]}]}"""));
        final List<Object> records = records(mapping, "{\"time\": \"2026-10-11T08:00:01Z\", \"payload\": "
                                                      + ToolJson.dumps(payload) + "}\n", null);
        @SuppressWarnings("unchecked")
        final Map<String, Object> first = (Map<String, Object>) records.getFirst();
        assertThat(first).containsEntry("payload//User", "alice").containsEntry("payload//Account/@id", "ACC-1")
                .containsEntry("payload/Envelope/Body/Login/User", "alice");
    }

    @Test
    void sampleValuesAreCheckedAgainstTheSchemasPatterns() {
        // Seen in eval case 40: CloudTrail's sourceIPAddress is sometimes an AWS service's name, which Stroom's
        // schema check refuses in an IPAddress; the step failed where the mapping check could have said so.
        final TranslationMapping mapping = mapping(json("""
                {"input": "json", "json_layout": "lines", "nil_values": ["-"],
                 "common": [{"path": "EventSource/Client/IPAddress", "field": "ip"},
                            {"path": "EventDetail/TypeId", "value": "x"}],
                 "events": [{"name": "x", "fields": [{"path": "EventDetail/Authenticate/Action",
                                                      "value": "Logon"}]}]}"""));
        final List<Object> records = new ArrayList<>(List.of(map("ip", "10.0.0.1"),
                map("ip", "cloudformation.amazonaws.com"), map("ip", "-")));
        assertThat(LocalCheck.checkSchemaValues(mapping, records, SCHEMA)).singleElement().asString()
                .contains("['cloudformation.amazonaws.com'] in 1 of the 2 sample records");
        assertThat(LocalCheck.checkSchemaValues(mapping, List.of(map("ip", "10.0.0.1")), SCHEMA))
                .isEmpty();
    }

    @Test
    void extractedValuesAreCheckedAgainstTheSchemasPatterns() {
        // Seen: SecretServer's fields, read from its message's key/value pairs, were never checked (only the
        // record's own fields were), so a value the schema refuses would have failed only at stepping.
        final TranslationMapping mapping = mapping(json("""
                {"input": "json", "json_layout": "lines",
                 "extract": [{"field": "msg", "regex": "Source IP: (\\\\S+)", "names": ["source_ip"]}],
                 "common": [{"path": "EventSource/Client/IPAddress", "field": "source_ip"},
                            {"path": "EventDetail/TypeId", "value": "x"}],
                 "events": [{"name": "x", "fields": [{"path": "EventDetail/Authenticate/Action",
                                                      "value": "Logon"}]}]}"""));
        final List<Object> records = List.of(map("msg", "Login - Source IP: 10.0.0.1"),
                map("msg", "Login - Source IP: Unknown"));

        assertThat(LocalCheck.checkSchemaValues(mapping, records, SCHEMA)).singleElement().asString()
                .contains("field 'source_ip' has ['Unknown'] in 1 of the 2 sample records");
    }

    @Test
    void valuesThatFitOnceCasedAreGivenTheTransformThatFitsThem() {
        // Seen in eval case 34: upper-case IPv6 addresses (the schema's pattern is lower case) were left out, as the
        // problem said to map only the values that fit, when transform: lower makes them fit.
        final TranslationMapping mapping = mapping(json("""
                {"input": "json", "json_layout": "lines",
                 "common": [{"path": "EventSource/Device/IPAddress", "field": "ip"},
                            {"path": "EventDetail/TypeId", "value": "x"}],
                 "events": [{"name": "x", "fields": [{"path": "EventDetail/Authenticate/Action",
                                                      "value": "Logon"}]}]}"""));
        final List<Object> records = List.of(map("ip", "2001:DB8::21"), map("ip", "10.4.0.21"));

        assertThat(LocalCheck.checkSchemaValues(mapping, records, SCHEMA)).singleElement().asString()
                .contains("['2001:DB8::21'] in 1 of the 2 sample records")
                .endsWith("They fit once lower-cased: give the entry transform: lower")
                .doesNotContain("Map only the values that fit");
    }

    @Test
    void domainQualifiedUserIdsAreWarnedOf() {
        // Seen in eval cases 35, 36 and 43: every Id kept its domain (domain.com\priya.patel, ACME\alice), the
        // account name alone wanted.
        final TranslationMapping mapping = mapping(json("""
                {"input": "json", "json_layout": "lines",
                 "common": [{"path": "EventSource/User/Id", "field": "by_user"},
                            {"path": "EventDetail/TypeId", "value": "x"}],
                 "events": [{"name": "x", "fields": [{"path": "EventDetail/Authenticate/Action",
                                                      "value": "Logon"},
                                                     {"path": "EventDetail/Authenticate/User/Id",
                                                      "field": "upn"}]}]}"""));
        final List<Object> records = List.of(map("by_user", "domain.com\\priya.patel", "upn", "joe@acme.example"),
                map("by_user", "alice", "upn", "bob"));

        assertThat(LocalCheck.domainQualifiedUserIds(mapping, records, SCHEMA)).containsExactly(
                "EventSource/User/Id takes field 'by_user', whose values are domain-qualified "
                + "(['domain.com\\\\priya.patel']): an Id is usually the account name alone. If so, give the entry "
                + "transform: strip_domain",
                "EventDetail/Authenticate/User/Id takes field 'upn', whose values are domain-qualified "
                + "(['joe@acme.example']): an Id is usually the account name alone. If so, give the entry transform: "
                + "strip_domain");
        // With the transform given, or values with no domain, nothing is said.
        assertThat(LocalCheck.domainQualifiedUserIds(mapping, List.of(map("by_user", "alice", "upn", "bob")),
                SCHEMA))
                .isEmpty();
    }

    @Test
    void domainQualifiedUserIdsAreFoundInExtractedFields() {
        // Seen: SecretServer's By User, read from its message's key/value pairs, was never checked (only the
        // record's own fields were), and the domain was kept in every Id.
        final TranslationMapping mapping = mapping(json("""
                {"input": "json", "json_layout": "lines",
                 "extract": [{"field": "msg", "regex": "By User: (\\\\S+)", "names": ["by_user"]}],
                 "common": [{"path": "EventSource/User/Id", "field": "by_user"},
                            {"path": "EventDetail/TypeId", "value": "x"}],
                 "events": [{"name": "x", "fields": [{"path": "EventDetail/Authenticate/Action",
                                                      "value": "Logon"},
                                                     {"path": "EventDetail/Authenticate/User/Id",
                                                      "field": "by_user"}]}]}"""));
        final List<Object> records = List.of(map("msg", "Event: Login - By User: domain.com\\sam.lee"),
                map("msg", "Event: Login - By User: alice"));

        assertThat(LocalCheck.domainQualifiedIds(mapping, records, SCHEMA)).containsExactly(new LocalCheck.QualifiedIds(
                "by_user", List.of("EventSource/User/Id", "EventDetail/Authenticate/User/Id"),
                List.of("domain.com\\sam.lee")));
    }

    @Test
    void theIdsCheckedAreTheSchemasUsersButNotAnEmailsAddressees() {
        // Asked for by the user: every element of the schema's user type (RunAs, Send/Destination/User), not only
        // those named User; an email's From, To, Cc and Bcc hold email addresses, the domain part of them.
        final TranslationMapping mapping = mapping(json("""
                {"input": "json", "json_layout": "lines",
                 "common": [{"path": "EventSource/RunAs/Id", "field": "run_as"},
                            {"path": "EventDetail/TypeId", "value": "x"}],
                 "events": [{"name": "x", "fields": [
                              {"path": "EventDetail/Send/Destination/User/Id", "field": "to_user"},
                              {"path": "EventDetail/Create/Email/To/Id", "field": "to"}]}]}"""));
        final List<Object> records = List.of(map("run_as", "ACME\\svc", "to_user", "ACME\\bob",
                "to", "bob@acme.example"));

        assertThat(LocalCheck.domainQualifiedIds(mapping, records, SCHEMA))
                .extracting(LocalCheck.QualifiedIds::field)
                .containsExactly("run_as", "to_user");
    }

    @Test
    void everySampleTextIsReadNotTheFirst200() {
        // Seen in production: a VPN rule's keys first appeared at the 557th of 3,933 records; reading only the
        // first 200, the check said they matched nothing.
        final StringBuilder sample = new StringBuilder();
        for (int n = 0; n < 600; n++) {
            final String body = n == 557
                    ? "type=\"event\" action=\"negotiate\" remip=\"10.0.0.1\" vpntunnel=\"hq\""
                    : "type=\"traffic\" action=\"deny\" n=\"" + n + "\"";
            sample.append(ToolJson.dumps(map("host", "fw1", "body", body))).append('\n');
        }
        final TranslationMapping m = mapping(json("""
                {"input": "json",
                 "extract": [{"field": "body", "regex": "(?:^|\\\\s)vpntunnel=\\"([^\\"]*)\\"",
                              "names": ["vpntunnel"]}],
                 "common": [{"path": "EventSource/Device/HostName", "field": "host"}],
                 "events": [{"name": "all", "fields": [{"path": "EventDetail/TypeId", "field": "vpntunnel"}]}]}"""));
        final List<Object> records = records(m, sample.toString(), null);
        assertThat(records).hasSize(600);
        assertThat(LocalCheck.extractedValues(m, records.get(557))).containsEntry("vpntunnel", "hq");
        assertThat(LocalCheck.extractedValues(m, records.getFirst())).containsEntry("vpntunnel", "");
    }

    @Test
    void anXmlFragmentsSampleIsReadARecordAFragment() {
        final String message = "Sep 27 2026 23:48:10 – User: domain.com\\Bloggs, Joe – [[AppServer]] "
                               + "Event: [User] Action: [Login] By User: domain.com\\joe.bloggs (Item Id: 219)";
        final StringBuilder sample = new StringBuilder();
        for (final String action : List.of("Login", "Logout", "Update")) {
            sample.append("<Event><System><Computer>app01</Computer></System><EventData><Data>")
                    .append(message.replace("Login", action)).append("</Data></EventData></Event>\n");
        }
        final TranslationMapping m = mapping(json("""
                {"input": "xml_fragments", "record": "Event",
                 "common": [{"path": "EventSource/User/Id", "field": "EventData/Data"}],
                 "events": [{"name": "logon", "fields": [{"path": "EventDetail/TypeId", "value": "x"}]}]}"""));
        final List<Object> records = records(m, sample.toString(), null);
        assertThat(records).hasSize(3);
        assertThat(LocalCheck.xmlFlat((XmlTree.Element) records.getFirst()))
                .containsEntry("System/Computer", "app01")
                .containsEntry("EventData/Data", message);
    }

    @Test
    void theUnknownRecordsAreRefusedWithTheRulesTheirValuesShow() {
        // The agent's give-up, each kind Unknown with a reason. Traffic and admin are refused with
        // the rules to use, and so is system now: every record it catches has a rule from its own values (a
        // service starting, a configuration saved), so there is nothing to put to the user.
        final Map<String, Object> draft = TestDraftMapping.draft(Map.of("fw.csv", TestDraftMapping.FIREWALL_CSV),
                "Firewall", "FW", "Prod");
        final Map<String, Object> gaveUp = new LinkedHashMap<>(mappingOf(draft));
        final List<Map<String, Object>> events = new ArrayList<>();
        for (final String kind : List.of("traffic", "admin", "system")) {
            events.add(map("name", kind, "when", List.of(map("field", "event_type", "equals",
                            kind.toUpperCase(Locale.ROOT))), "allow_unknown", "the schema is complex",
                    "fields", json(UNKNOWN)));
        }
        gaveUp.put("events", events);
        final List<Object> records = new ArrayList<>(DraftMapping.records(Map.of("fw.csv",
                TestDraftMapping.FIREWALL_CSV), null).records());
        final LocalCheck.Coverage coverage = LocalCheck.unknownCoverage(mapping(gaveUp), records, null);
        final List<String> problems = coverage.problems();
        assertThat(problems.stream().map(p -> p.split("]")[0]).sorted().toList())
                .containsExactly("[admin", "[system", "[traffic");
        assertThat(problems.getFirst()).contains("can't be kept as Unknown: 2 of its 2 sample records");
        final List<Object> offered = json(problems.getFirst().substring(problems.getFirst().indexOf("[{")));
        final Map<String, Object> withOffered = new LinkedHashMap<>(mappingOf(draft));
        withOffered.put("events", offered);
        assertThat(XsltGenerator.generate(mapping(withOffered), SCHEMA_352, "3.5.2").ok())
                .isTrue();
        final String system = problems.stream().filter(p -> p.startsWith("[system]")).findFirst().orElseThrow();
        assertThat(coverage.kept()).isEmpty();
        assertThat(system).contains("Update for action CONFIG_SAVED; Process for action START");
    }

    @Test
    void theUserCanKeepUnknownAfterSeeingWhatTheValuesSuggest() {
        // Refused first, with the rules the values show; kept only when the user, shown them, still wants Unknown,
        // and then put to them in the form with those suggestions.
        final Map<String, Object> draft = TestDraftMapping.draft(Map.of("fw.csv", TestDraftMapping.FIREWALL_CSV),
                "Firewall", "FW", "Prod");
        final List<Object> records = new ArrayList<>(DraftMapping.records(Map.of("fw.csv",
                TestDraftMapping.FIREWALL_CSV), null).records());
        LocalCheck.Coverage coverage = LocalCheck.unknownCoverage(traffic(draft, false), records, null);
        assertThat(coverage.problems().getFirst()).startsWith("[traffic] can't be kept as Unknown")
                .contains("keep_unknown: true");
        coverage = LocalCheck.unknownCoverage(traffic(draft, true), records, null);
        assertThat(coverage.problems()).isEmpty();
        assertThat(coverage.kept().getFirst()).containsEntry("rule", "traffic").containsEntry("against_suggestion",
                true);
        assertThat((String) coverage.kept().getFirst().get("suggested")).startsWith("Network/Permit for action ALLOW");
    }

    private static TranslationMapping traffic(final Map<String, Object> draft, final boolean keep) {
        final Map<String, Object> rule = map("name", "traffic", "when", List.of(map("field", "event_type", "equals",
                "TRAFFIC")), "allow_unknown", "the user said so", "fields", json(UNKNOWN));
        if (keep) {
            rule.put("keep_unknown", true);
        }
        final Map<String, Object> mapping = new LinkedHashMap<>(mappingOf(draft));
        mapping.put("events", List.of(rule));
        return mapping(mapping);
    }

    @Test
    void traitsSayWhatTheRecordsHold() {
        final List<Object> records = List.of(map("action", "LOGIN", "time", "2026-10-01 10:00:00", "message",
                "a user logged in from afar"), map("action", "LOGOUT", "n", 5));
        assertThat(LocalCheck.traits(records))
                .isEqualTo("action: LOGIN, LOGOUT; e.g. message: \"a user logged in from afar\"");
    }
}
