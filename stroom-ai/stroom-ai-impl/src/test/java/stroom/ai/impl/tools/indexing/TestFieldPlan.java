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

package stroom.ai.impl.tools.indexing;

import stroom.ai.impl.tools.JsonValues;
import stroom.ai.impl.tools.generation.XsltStyle;
import stroom.ai.impl.tools.indexing.ExampleTemplates.Built;
import stroom.ai.impl.tools.sample.PyJson;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/// A field plan rendered for Lucene or Elasticsearch: its index doc fields or index template, and the indexing XSLT,
/// run with Saxon. Ported from the Stroom MCP server's `tests/test_indexing.py` (its cases of the plan itself) and
/// `tests/test_index_style.py`, case by case.
class TestFieldPlan {

    private static final EcsSchema ECS = IndexingFixtures.ECS;
    private static final String EVENTS = """
            <Events xmlns="event-logging:3"><Event StreamId="7" EventId="2">
            <EventTime><TimeCreated>2026-09-28T10:00:00.000Z</TimeCreated></EventTime>
            <EventSource><Device><HostName>ws01</HostName></Device><User><Id>alice</Id></User></EventSource>
            <EventDetail><Authenticate><Outcome><Success>false</Success></Outcome></Authenticate></EventDetail>
            </Event></Events>""";
    private static final List<PlannedField> FIELDS = List.of(
            PlannedField.of("StreamId", "id", "@StreamId"),
            PlannedField.of("EventId", "id", "@EventId"),
            PlannedField.of("@timestamp", "date", "EventTime/TimeCreated"),
            PlannedField.of("user.name", "keyword", "EventSource/User/Id"),
            PlannedField.of("event.outcome", "boolean", "EventDetail/Authenticate/Outcome/Success"),
            PlannedField.of("source.ip", "ip", "EventSource/Client/IPAddress"));
    private static final String RAW = """
            <array xmlns="http://www.w3.org/2013/XSL/json"><map><string key="ts">2026-10-01T09:00:00Z</string>
            <map key="user"><string key="name">alice</string><array key="roles"><string>admin</string></array></map>
            <number key="status">200</number><string key="token">secret</string>
            <string key="message">{"action": "login", "client": {"ip": "10.1.1.1"}}</string>
            <string key="note">{not json}</string></map></array>""";
    private static final String ACTIONS = """
            <Events xmlns="event-logging:3">
            <Event><EventDetail><Copy><Source><Resource><Id>a.txt</Id><Type>File</Type></Resource></Source>
              <Destination><Resource><Id>b.txt</Id></Resource></Destination><Outcome><Success>true</Success></Outcome>\
            </Copy></EventDetail></Event>
            <Event><EventDetail><View><Resource><Id>secret-1</Id></Resource></View></EventDetail></Event>
            <Event><EventDetail><Move><Outcome><Success>false</Success></Outcome></Move></EventDetail></Event>
            </Events>""";
    private static final String REPEATED = """
            <Events xmlns="event-logging:3"><Event StreamId="7" EventId="1">
            <EventTime><TimeCreated>2026-10-20T08:00:00.000Z</TimeCreated></EventTime><EventSource><User><Id>alice\
            </Id></User></EventSource>
            <EventDetail><Authenticate><Outcome><Success>false</Success></Outcome><Data Name="port" Value="80"/>\
            <Data Name="port" Value="443"/>
            <Data Name="tag" Value="a"/></Authenticate></EventDetail></Event></Events>""";
    private static final List<PlannedField> STYLED = List.of(
            PlannedField.of("StreamId", "id", "@StreamId"), PlannedField.of("EventId", "id", "@EventId"),
            PlannedField.of("@timestamp", "date", "EventTime/TimeCreated"),
            PlannedField.of("user.name", "keyword", "EventSource/User/Id"),
            PlannedField.of("http.request.method", "keyword", "EventDetail/*/Resource/HTTPMethod"),
            PlannedField.of("http.request.body.bytes", "long", "EventDetail/*/Resource/InboundSize"),
            new PlannedField("event.outcome", "keyword", "EventDetail/*/Outcome/Success", "", "outcome", false));
    private static final String STYLED_EVENTS = """
            <Events xmlns="event-logging:3"><Event StreamId="7" EventId="1">
            <EventTime><TimeCreated>2026-10-11T09:00:00.000Z</TimeCreated></EventTime><EventSource><User><Id>alice\
            </Id></User></EventSource>
            <EventDetail><View><Resource><HTTPMethod>GET</HTTPMethod><InboundSize>512</InboundSize></Resource>
            <Outcome><Success>false</Success></Outcome></View></EventDetail></Event></Events>""";
    private static final String STYLED_EXPECTED = "<array><map><number key=\"StreamId\">7</number><number "
                                                  + "key=\"EventId\">1</number><string key=\"@timestamp\">"
                                                  + "2026-10-11T09:00:00.000Z</string><map key=\"user\"><string "
                                                  + "key=\"name\">alice</string></map><map key=\"http\"><map "
                                                  + "key=\"request\"><string key=\"method\">GET</string><map "
                                                  + "key=\"body\"><number key=\"bytes\">512</number></map></map>"
                                                  + "</map><map key=\"event\"><string key=\"outcome\">failure"
                                                  + "</string></map></map></array>";

    @Test
    void anElasticPlanRendersANestedTemplateAndAJsonXmlXslt() {
        final FieldPlan plan = FieldPlan.of("elasticsearch", "stroom-acme-v1", "@timestamp", FIELDS);
        assertThat(plan.required()).isEmpty();
        final Map<String, Object> body = JsonValues.map(plan.elasticTemplate("stroom-acme-v1", ECS).get("body"));
        assertThat(body.get("index_patterns")).isEqualTo(List.of("stroom-acme-v1*"));
        final Map<String, Object> types = new java.util.LinkedHashMap<>();
        TemplateMappings.readMappingFields(body).forEach((path, spec) -> {
            if (!"object".equals(spec.get("type"))) {
                types.put(path, spec.get("type"));
            }
        });
        assertThat(types).isEqualTo(Map.of("StreamId", "long", "EventId", "long", "@timestamp", "date", "user.name",
                "keyword", "event.outcome", "boolean", "source.ip", "ip"));
        final String output = IndexingFixtures.transform(plan.xslt(), EVENTS);
        final String compact = IndexingFixtures.compact(output);
        assertThat(compact).contains("<number key=\"StreamId\">7</number>")
                // Structure kept: dotted names become nested objects, not flat "user.name" keys.
                .contains("<map key=\"user\"><string key=\"name\">alice</string></map>")
                .contains("<map key=\"event\"><boolean key=\"outcome\">false</boolean></map>")
                .doesNotContain("user.name")
                // Absent in the event, so left out rather than written empty, and so is the object holding only
                // it.
                .doesNotContain("key=\"source\"").doesNotContain("key=\"ip\"");
    }

    @Test
    void aLucenePlanMapsKeywordsToTextWithTheKeywordAnalyzer() {
        final FieldPlan plan = FieldPlan.of("lucene", "ACME-INDEX", "EventTime", List.of(
                PlannedField.of("StreamId", "id", "@StreamId"), PlannedField.of("EventId", "id", "@EventId"),
                PlannedField.of("EventTime", "date", "EventTime/TimeCreated"),
                PlannedField.of("UserId", "keyword", "EventSource/User/Id")));
        final List<Map<String, Object>> fields = plan.luceneFields();
        assertThat(fields.get(3)).containsEntry("fldName", "UserId").containsEntry("fldType", "TEXT")
                .containsEntry("analyzerType", "KEYWORD");
        assertThat(fields.getFirst()).containsEntry("fldType", "ID");
        assertThat(IndexingFixtures.transform(plan.xslt(), EVENTS)).contains("<data name=\"UserId\" value=\"alice\"/>");
    }

    @Test
    void aPlanReportsMissingRequiredFields() {
        final FieldPlan plan = FieldPlan.of("elasticsearch", "x", "when", FIELDS.subList(3, FIELDS.size()));
        assertThat(plan.required()).containsExactlyInAnyOrder("Missing required field 'StreamId'",
                "Missing required field 'EventId'", "The time field 'when' is not in the plan",
                "Elasticsearch data streams need '@timestamp'");
    }

    @Test
    void aDiscoveryPlanCopiesRecordsAsTheyAreAndMapsOnlyWhatStroomNeeds() {
        final FieldPlan plan = FieldPlan.forDiscovery("stroom-discovery-acme-v1", new Discovery("json", null, "ts",
                false, null, Map.of("stroom.feed", "Feed"), List.of("token"), true, 1024, 2000, Map.of()));
        assertThat(plan.required()).isEmpty();
        assertThat(plan.fields()).extracting(PlannedField::name).containsExactly("StreamId", "EventId", "@timestamp");
        final Map<String, Object> body = JsonValues.map(plan.elasticTemplate("stroom-discovery-acme-v1", ECS)
                .get("body"));
        final Map<String, Object> mappings = TemplateMappings.mappingsOf(body);
        assertThat(mappings.get("dynamic")).isEqualTo(true);
        assertThat(mappings.get("date_detection")).isEqualTo(false);
        assertThat(PyJson.dumps(mappings.get("dynamic_templates"))).isEqualTo("[{\"strings_as_keywords\": "
                + "{\"match_mapping_type\": \"string\", \"mapping\": {\"type\": \"keyword\", \"ignore_above\": "
                + "1024}}}]");
        assertThat(PyJson.dumps(mappings.get("properties"))).isEqualTo("{\"StreamId\": {\"type\": \"long\"}, "
                + "\"EventId\": {\"type\": \"long\"}, \"@timestamp\": {\"type\": \"date\"}}");
        assertThat(PyJson.dumps(JsonValues.map(JsonValues.mapAt(JsonValues.mapAt(body, "template"), "settings")
                .get("index")).get("mapping")))
                .isEqualTo("{\"total_fields\": {\"limit\": 2000}, \"ignore_malformed\": true}");
        final String output = IndexingFixtures.compact(runRaw(plan.xslt(), RAW));
        assertThat(output).contains("<number key=\"StreamId\">7</number>").contains("<number key=\"EventId\">1"
                                                                                    + "</number>")
                .contains("<string key=\"@timestamp\">2026-10-01T09:00:00Z</string>")
                .contains("<string key=\"stroom.feed\">ACME</string>")
                .contains("<map key=\"user\"><string key=\"name\">alice</string><array key=\"roles\"><string>admin"
                          + "</string></array></map>")
                .contains("<number key=\"status\">200</number>").doesNotContain("secret")
                // JSON held in a string: kept, and parsed beside it; text that only looks like JSON is left alone.
                .contains("<map key=\"message_json\"><string key=\"action\">login</string><map key=\"client\">"
                          + "<string key=\"ip\">10.1.1.1</string>")
                .contains("<string key=\"note\">{not json}</string>").doesNotContain("note_json");
    }

    @Test
    void aDiscoveryTimestampCanBeNestedAndFormattedAndUnpackingTurnedOff() {
        final FieldPlan plan = FieldPlan.forDiscovery("d", new Discovery("json", null, "event.created", false,
                "dd/MM/yyyy HH:mm:ss", Map.of(), List.of(), false, 1024, 2000, Map.of()));
        final String xslt = plan.xslt();
        assertThat(xslt).contains("stroom:format-date(string(*[@key='event']/*[@key='created']), "
                                  + "'dd/MM/yyyy HH:mm:ss')")
                .doesNotContain("json-to-xml");
    }

    @Test
    void aDiscoveryTemplateTakesTheExamplesSettingsButNotItsFieldsOrRules() {
        final FieldPlan plan = FieldPlan.forDiscovery("stroom-discovery-acme-v1", Discovery.of("json", "ts"));
        final Map<String, Object> example = IndexingFixtures.json("""
                {"index_patterns": ["ecs-x*"], "composed_of": ["base"], "template": {
                 "settings": {"index": {"number_of_shards": 3, "mapping": {"total_fields": {"limit": 500}}}},
                 "mappings": {"dynamic": "strict", "properties": {"User": {"properties": {"Id": {"type":
                  "keyword"}}}, "@timestamp": {"type": "date", "format": "epoch_millis"}}}}}""");
        final Built built = ExampleTemplates.fromExample(JsonValues.map(plan.elasticTemplate("x", ECS).get("body")),
                example, Map.of("base", IndexingFixtures.json("{\"template\": {\"mappings\": {\"properties\": "
                                                              + "{\"StreamId\": {\"type\": \"long\"}}}}}")), true,
                List.of(), List.of(), false, ECS);
        final Map<String, Object> mappings = TemplateMappings.mappingsOf(built.body());
        assertThat(mappings.get("dynamic")).isEqualTo(true);
        assertThat(mappings).containsKey("dynamic_templates");
        // The example maps @timestamp itself: its definition is kept, as in any template built from an example.
        assertThat(PyJson.dumps(mappings.get("properties"))).isEqualTo("{\"EventId\": {\"type\": \"long\"}, "
                + "\"@timestamp\": {\"type\": \"date\", \"format\": \"epoch_millis\"}}");
        assertThat(PyJson.dumps(JsonValues.mapAt(JsonValues.mapAt(built.body(), "template"), "settings").get("index")))
                .isEqualTo("{\"number_of_shards\": 3, \"mapping\": {\"total_fields\": {\"limit\": 2000}, "
                           + "\"ignore_malformed\": true}}");
        assertThat(built.notes()).anyMatch(n -> n.startsWith("a discovery index"))
                .noneMatch(n -> n.contains("named unlike"));
    }

    @Test
    void documentsAreNestedWhateverSubobjectsSaysAndAValueCannotAlsoBeAnObject() {
        final List<PlannedField> fields = FIELDS.subList(0, 4);
        final FieldPlan flat = IndexingFixtures.withSubobjects(FieldPlan.of("elasticsearch", "x", "@timestamp", fields),
                false);
        assertThat(IndexingFixtures.compact(IndexingFixtures.transform(flat.xslt(), EVENTS)))
                .contains("<map key=\"user\"><string key=\"name\">alice</string></map>");
        final List<PlannedField> clashing = new ArrayList<>(fields);
        clashing.add(PlannedField.of("user", "keyword", "EventSource/User/Id"));
        assertThat(FieldPlan.of("elasticsearch", "x", "@timestamp", clashing).required())
                .anyMatch(p -> p.contains("'user' is a value and also the object holding ['user.name']"));
    }

    @Test
    void aFieldStartingWithAnUnderscoreIsReportedAsDropped() {
        final List<PlannedField> fields = new ArrayList<>(FIELDS.subList(0, 3));
        fields.add(PlannedField.of("meta._source_host", "keyword", "EventSource/Device/HostName"));
        assertThat(FieldPlan.of("elasticsearch", "x", "@timestamp", fields).required())
                .anyMatch(p -> p.contains("['meta._source_host']: Stroom's Elasticsearch indexing filter drops "
                                          + "fields"));
    }

    @Test
    void discoveryKeysStroomOrElasticsearchWouldLoseAreRenamed() {
        final String raw = "<array xmlns=\"http://www.w3.org/2013/XSL/json\"><map><string key=\"ts\">t</string>"
                           + "<string key=\"_id\">s</string><string key=\"\">blank</string><number key=\"a..b\">2"
                           + "</number><map key=\"deep\"><string key=\"_x\">in</string></map><string "
                           + "key=\"StreamId\">theirs</string></map></array>";
        final FieldPlan plan = FieldPlan.forDiscovery("d", Discovery.of("json", "ts"));
        final String output = runRaw(plan.xslt(), raw);
        for (final String kept : List.of("<string key=\"id_original\">s</string>",
                "<string key=\"empty_key\">blank</string>", "<number key=\"a.b\">2</number>",
                "<map key=\"deep\"><string key=\"x_original\">in</string></map>",
                "<string key=\"StreamId_original\">theirs</string>", "<number key=\"StreamId\">7</number>")) {
            assertThat(IndexingFixtures.compact(output)).as(kept).contains(kept);
        }
    }

    @Test
    void aDelimitedDiscoveryPlanIndexesEachColumnByItsHeader() {
        final FieldPlan plan = FieldPlan.forDiscovery("d", new Discovery("delimited", null, "time", false, null,
                Map.of(), List.of("secret"), true, 1024, 2000, Map.of()));
        assertThat(TemplateMappings.mappingsOf(JsonValues.map(plan.elasticTemplate("d", ECS).get("body")))
                .get("numeric_detection")).isEqualTo(true);
        final String raw = "<records xmlns=\"records:2\"><record><data name=\"time\" value=\"2026-10-01T09:00:00Z\"/>"
                           + "<data name=\"user\" value=\"alice\"/><data name=\"secret\" value=\"x\"/><data "
                           + "name=\"_id\" value=\"s1\"/><data name=\"msg\" value=\"{&quot;a&quot;: 1}\"/></record>"
                           + "</records>";
        final String out = IndexingFixtures.compact(runRaw(plan.xslt(), raw));
        assertThat(out).contains("<string key=\"@timestamp\">2026-10-01T09:00:00Z</string>")
                .contains("<string key=\"user\">alice</string>").contains("<string key=\"id_original\">s1</string>")
                .contains("<map key=\"msg_json\"><number key=\"a\">1</number></map>").doesNotContain("secret");
    }

    @Test
    void anEventsIndexLeavesOutOrNullsStandInsForNoneAsTheUserChose() {
        // Seen in testing: W3C logs' '-' for a missing address kept in the Events' Data, refused by an ip field.
        final Map<String, String> standIns = new LinkedHashMap<>();
        standIns.put("-", Discovery.LEAVE_OUT);
        standIns.put("N/A", Discovery.NULL);
        standIns.put("none", Discovery.KEEP);
        final FieldPlan plan = FieldPlan.of("elasticsearch", "x", "@timestamp", List.of(
                PlannedField.of("@timestamp", "date", "EventTime/TimeCreated"),
                PlannedField.of("server.ip", "ip", "EventDetail/Network/*/Data[@Name='Server-IP']/@Value"),
                PlannedField.of("client.ip", "ip", "EventSource/Client/IPAddress"),
                PlannedField.of("user.id", "keyword", "EventSource/User/Id"),
                PlannedField.of("host.name", "keyword", "EventSource/Device/HostName"))).withStandIns(standIns);
        final String events = """
                <Events xmlns="event-logging:3"><Event>
                <EventTime><TimeCreated>2026-09-28T10:00:00.000Z</TimeCreated></EventTime>
                <EventSource><Device><HostName>none</HostName></Device><Client><IPAddress>N/A</IPAddress></Client>
                <User><Id>-</Id></User></EventSource>
                <EventDetail><Network><Connect><Data Name="Server-IP" Value="-"/></Connect></Network></EventDetail>
                </Event><Event>
                <EventTime><TimeCreated>2026-09-28T10:01:00.000Z</TimeCreated></EventTime>
                <EventSource><Client><IPAddress>10.0.0.1</IPAddress></Client><User><Id>alice</Id></User></EventSource>
                <EventDetail><Network><Permit><Data Name="Server-IP" Value="10.0.0.2"/></Permit></Network></EventDetail>
                </Event></Events>""";

        final String xslt = plan.xslt();
        final String out = IndexingFixtures.compact(IndexingFixtures.transform(xslt, events));

        assertThat(xslt).contains("<xsl:variable name=\"stand-ins-left-out\" select=\"('-')\"",
                "<xsl:variable name=\"stand-ins-null\" select=\"('N/A')\"");
        final String first = out.substring(0, out.indexOf("2026-09-28T10:01"));
        // '-' left out, in a Data entry and an element alike, and with its object when it is all the object holds.
        assertThat(first).doesNotContain("server", "\"user\"", "<string key=\"id\">-</string>")
                .contains("<map key=\"client\"><null key=\"ip\"/></map>")
                .contains("<map key=\"host\"><string key=\"name\">none</string></map>");
        // Real values as they are.
        assertThat(out).contains("<map key=\"server\"><string key=\"ip\">10.0.0.2</string></map>",
                "<map key=\"client\"><string key=\"ip\">10.0.0.1</string></map>",
                "<map key=\"user\"><string key=\"id\">alice</string></map>");
        // Kept with the plan, and written as before without any.
        assertThat(FieldPlan.fromJson(plan.toJson()).standIns()).isEqualTo(standIns);
        assertThat(plan.withStandIns(Map.of()).toJson()).containsKey("stand_ins");
        assertThat(FieldPlan.of("elasticsearch", "x", "@timestamp", FIELDS).xslt()).doesNotContain("stand-ins");
    }

    @Test
    void aJsonDiscoveryIndexesStandInsForNoneAsTheUserChose() {
        // Asked for by the user: W3C logs' '-' for a missing address, refused by an ip field.
        final Map<String, String> standIns = new LinkedHashMap<>();
        standIns.put("-", Discovery.LEAVE_OUT);
        standIns.put("N/A", Discovery.NULL);
        standIns.put("?", Discovery.EMPTY);
        standIns.put("none", Discovery.KEEP);
        final FieldPlan plan = FieldPlan.forDiscovery("d", Discovery.of("json", "ts").withStandIns(standIns));
        final String raw = """
                <array xmlns="http://www.w3.org/2013/XSL/json"><map><string key="ts">2026-10-01T09:00:00Z</string>
                <string key="s-ip">-</string><map key="client"><string key="ip">N/A</string></map>
                <string key="host">?</string><string key="user">none</string><string key="ok">10.0.0.1</string>
                <array key="tags"><string>-</string></array></map></array>""";

        final String out = IndexingFixtures.compact(runRaw(plan.xslt(), raw));

        assertThat(out).doesNotContain("s-ip")
                .contains("<map key=\"client\"><null key=\"ip\"/></map>")
                .contains("<string key=\"host\"/>")
                .contains("<string key=\"user\">none</string>")
                .contains("<string key=\"ok\">10.0.0.1</string>")
                // A value in an array has no key to leave out or write as null: kept.
                .contains("<array key=\"tags\"><string>-</string></array>");
        // Without stand-ins, the XSLT is as before.
        assertThat(FieldPlan.forDiscovery("d", Discovery.of("json", "ts")).xslt())
                .doesNotContain("Values the source writes for none");
    }

    @Test
    void aDelimitedDiscoveryIndexesStandInsForNoneAsTheUserChose() {
        final FieldPlan plan = FieldPlan.forDiscovery("d", Discovery.of("delimited", "time")
                .withStandIns(Map.of("-", Discovery.LEAVE_OUT, "N/A", Discovery.NULL, "it's \"<none>\"",
                        Discovery.EMPTY)));
        final String raw = "<records xmlns=\"records:2\"><record><data name=\"time\" value=\"2026-10-01T09:00:00Z\"/>"
                           + "<data name=\"s-ip\" value=\"-\"/><data name=\"user\" value=\"N/A\"/>"
                           + "<data name=\"host\" value=\"it's &quot;&lt;none&gt;&quot;\"/>"
                           + "<data name=\"ok\" value=\"ws01\"/></record></records>";

        final String out = IndexingFixtures.compact(runRaw(plan.xslt(), raw));

        assertThat(out).doesNotContain("s-ip")
                .contains("<null key=\"user\"/>")
                // Quotes and markup in a stand-in are escaped in the XSLT.
                .contains("<string key=\"host\"/>")
                .contains("<string key=\"ok\">ws01</string>");
    }

    @Test
    void aDiscoveryKeepsItsStandInsAndRefusesAnUnknownTreatment() {
        final Discovery discovery = Discovery.of("json", "ts").withStandIns(Map.of("-", Discovery.NULL));

        assertThat(discovery.toJson()).containsEntry("stand_ins", Map.of("-", "null"));
        assertThat(Discovery.fromJson(discovery.toJson())).isEqualTo(discovery);
        // Without any, written as the Python model writes it.
        assertThat(Discovery.of("json", "ts").toJson()).doesNotContainKey("stand_ins");
        final Map<String, Object> bad = new LinkedHashMap<>(discovery.toJson());
        bad.put("stand_ins", Map.of("-", "zero"));
        assertThatThrownBy(() -> Discovery.fromJson(bad)).hasMessageContaining("stand_ins");
    }

    @Test
    void anXmlDiscoveryPlanKeepsTheStructureAndNeedsItsRecordElement() {
        final FieldPlan plan = FieldPlan.forDiscovery("d", new Discovery("xml", "logon", "@at", false, null,
                Map.of(), List.of(), true, 1024, 2000, Map.of()));
        final String raw = "<logons xmlns=\"urn:x\"><logon id=\"7\" at=\"2026-10-01T09:00:00Z\"><who>alice</who>"
                           + "<roles><role>admin</role><role>ops</role></roles><client ip=\"10.1.1.1\">laptop"
                           + "</client></logon></logons>";
        final String out = IndexingFixtures.compact(runRaw(plan.xslt(), raw));
        assertThat(out).contains("<string key=\"@timestamp\">2026-10-01T09:00:00Z</string>")
                .contains("<string key=\"id\">7</string>").contains("<string key=\"who\">alice</string>")
                .contains("<map key=\"roles\"><array key=\"role\"><string>admin</string><string>ops</string></array>"
                          + "</map>")
                .contains("<map key=\"client\"><string key=\"ip\">10.1.1.1</string><string key=\"value\">laptop"
                          + "</string></map>");
        assertThatThrownBy(() -> FieldPlan.forDiscovery("d", Discovery.of("xml", "when")).xslt())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("needs the record element's name");
    }

    @Test
    void elasticsearchOutputSaysWhichJsonSchemaItFollows() {
        // A template's schema filter with group JSON holds two schemas (json.xsd, xpath-functions.xsd): without
        // xsi:schemaLocation every record fails with "No schema locations specified".
        final String declared = "xsi:schemaLocation=\"http://www.w3.org/2005/xpath-functions "
                                + "file://xpath-functions.xsd\"";
        final String indexed = IndexingFixtures.transform(FieldPlan.of("elasticsearch", "stroom-acme-v1", "@timestamp",
                FIELDS).xslt(), EVENTS);
        final String discovered = runRaw(FieldPlan.forDiscovery("stroom-raw-v1", Discovery.of("json", "time")).xslt(),
                RAW);
        final Pattern root = Pattern.compile("<array\\b[^>]*>");
        for (final String output : List.of(indexed, discovered)) {
            final Matcher matcher = root.matcher(output);
            assertThat(matcher.find()).isTrue();
            assertThat(matcher.group()).contains(declared);
            assertThat(output).contains("xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"");
        }
    }

    @Test
    void anObjectRepeatedAcrossActionsIsWrittenByOneTemplate() {
        // Asked for by the user: the SecretServer index wrote Outcome, Resource, Source and Destination out in full
        // under every action, each object's single field tested twice; the translation XSLT writes each thing once.
        final List<PlannedField> fields = new ArrayList<>();
        under(fields, "Copy", "Source.Resource.Id", "Source.Resource.Type", "Destination.Resource.Id",
                "Destination.Resource.Type", "Outcome.Success");
        under(fields, "Move", "Source.Resource.Id", "Source.Resource.Type", "Destination.Resource.Id",
                "Destination.Resource.Type", "Outcome.Success");
        under(fields, "View", "Resource.Id", "Resource.Type", "Outcome.Success");
        under(fields, "Delete", "Resource.Id", "Resource.Type", "Outcome.Success");
        fields.add(PlannedField.of("Alert.Severity", "keyword", "EventDetail/Alert/Severity"));
        final String xslt = FieldPlan.of("elasticsearch", "x", "@timestamp", fields).xslt();
        // Modes named in the style's naming (snake_case by default), as the Events translation's are.
        for (final String mode : List.of("resource", "outcome", "source", "destination")) {
            assertThat(xslt.split(Pattern.quote("<xsl:template match=\"*\" mode=\"" + mode + "\">"), -1)).hasSize(2);
        }
        assertThat(xslt).contains("<xsl:apply-templates select=\"(EventDetail/Copy/Source)[1]\" mode=\"source\"/>")
                // Once only: written in place, and its one field tested once.
                .doesNotContain("match=\"*\" mode=\"alert\"");
        assertThat(xslt.replaceAll(">\\s+<", "><")).contains("<map key=\"Alert\"><string key=\"Severity\">");
        final String json = IndexingFixtures.xmlToJson(IndexingFixtures.transform(xslt, ACTIONS));
        assertThat(PyJson.dumps(PyJson.loads(json))).isEqualTo(PyJson.dumps(PyJson.loads("""
                [{"Copy": {"Source": {"Resource": {"Id": "a.txt", "Type": "File"}},
                           "Destination": {"Resource": {"Id": "b.txt"}}, "Outcome": {"Success": true}}},
                 {"View": {"Resource": {"Id": "secret-1"}}},
                 {"Move": {"Outcome": {"Success": false}}}]""")));
    }

    @Test
    void standInsAreLeftOutOrNulledInsideTheTemplatesOfRepeatedObjects() {
        // The templates' paths are relative to their element, and an object is tested by its fields' sources: a
        // stand-in left out takes its object with it when it is all the object holds.
        final List<PlannedField> fields = new ArrayList<>();
        under(fields, "Copy", "Source.Resource.Id", "Source.Resource.Type", "Destination.Resource.Id",
                "Destination.Resource.Type", "Outcome.Success");
        under(fields, "Move", "Source.Resource.Id", "Source.Resource.Type", "Destination.Resource.Id",
                "Destination.Resource.Type", "Outcome.Success");
        under(fields, "View", "Resource.Id", "Resource.Type", "Outcome.Success");
        under(fields, "Delete", "Resource.Id", "Resource.Type", "Outcome.Success");
        final String xslt = FieldPlan.of("elasticsearch", "x", "@timestamp", fields)
                .withStandIns(Map.of("b.txt", Discovery.LEAVE_OUT, "File", Discovery.NULL)).xslt();

        assertThat(xslt).contains("<xsl:template match=\"*\" mode=\"resource\">");
        final String json = IndexingFixtures.xmlToJson(IndexingFixtures.transform(xslt, ACTIONS));
        assertThat(PyJson.dumps(PyJson.loads(json))).isEqualTo(PyJson.dumps(PyJson.loads("""
                [{"Copy": {"Source": {"Resource": {"Id": "a.txt", "Type": null}}, "Outcome": {"Success": true}}},
                 {"View": {"Resource": {"Id": "secret-1"}}},
                 {"Move": {"Outcome": {"Success": false}}}]""")));
    }

    private static void under(final List<PlannedField> fields, final String action, final String... names) {
        for (final String name : names) {
            fields.add(PlannedField.of(action + "." + name, name.endsWith("Success")
                    ? "boolean"
                    : "keyword", "EventDetail/" + action + "/" + name.replace('.', '/')));
        }
    }

    @Test
    void aFieldOfSeveralValuesIsWrittenWholeOnEitherBackend() {
        // Asked about by the user: written with value-of, two ports were indexed as the one number "80 443" (an
        // invalid number for Elasticsearch), two tags as "a b". A field the schema lets repeat writes each value.
        final List<PlannedField> fields = List.of(PlannedField.of("StreamId", "id", "@StreamId"),
                PlannedField.of("@timestamp", "date", "EventTime/TimeCreated"),
                PlannedField.of("user.name", "keyword", "EventSource/User/Id"),
                data("port", "long"), data("tag", "keyword"), data("none", "keyword"));
        final FieldPlan es = FieldPlan.of("elasticsearch", "x", "@timestamp", fields)
                .withConvention("ecs", List.of("acme"));
        final String written = IndexingFixtures.compact(IndexingFixtures.transform(es.xslt(), REPEATED));
        final String lucene = IndexingFixtures.compact(IndexingFixtures.transform(
                IndexingFixtures.withBackend(es, "lucene").xslt(), REPEATED));
        assertThat(written).contains("<array key=\"port\"><number>80</number><number>443</number></array>")
                // One value; none, nothing.
                .contains("<string key=\"tag\">a</string>").doesNotContain("none")
                .contains("<string key=\"name\">alice</string>");
        assertThat(lucene).contains("<data name=\"acme.port\" value=\"80\"/><data name=\"acme.port\" value=\"443\"/>")
                .contains("<data name=\"acme.tag\" value=\"a\"/>").doesNotContain("acme.none");
        // A field the schema doesn't let repeat is written as before.
        assertThat(FieldPlan.of("lucene", "x", "@timestamp", fields.subList(0, 3)).xslt())
                .doesNotContain("<xsl:for-each");
    }

    private static PlannedField data(final String name, final String type) {
        return new PlannedField("acme." + name, type, "EventDetail/Authenticate/Data[@Name='" + name + "']/@Value",
                "", null, true);
    }

    @Test
    void aTemplateATopLevelObjectEachWithACommentAndItsInputsReadOnce() {
        final String xslt = styled(XsltStyle.DEFAULT).xslt();
        for (final String part : List.of("user", "http", "event")) {
            assertThat(xslt).contains("<xsl:apply-templates select=\".\" mode=\"" + part + "\"/>")
                    .contains("<xsl:template match=\"Event\" mode=\"" + part + "\">");
        }
        assertThat(xslt).contains("<!-- http: http.request.method from EventDetail/*/Resource/HTTPMethod; "
                                  + "http.request.body.bytes from")
                // Read three times (the object's guard, the field's guard and its value): a variable, declared
                // before its first use.
                .contains("<xsl:variable name=\"http_request_method\" select=\"EventDetail/*/Resource/HTTPMethod\"/>")
                .contains("<xsl:if test=\"$http_request_method or $http_request_body_bytes\">")
                // Read twice: inline, as the Events translation writes it.
                .doesNotContain("name=\"user_name\"").contains("<xsl:value-of select=\"EventSource/User/Id\"/>");
        assertThat(IndexingFixtures.compact(IndexingFixtures.transform(xslt, STYLED_EVENTS)))
                .isEqualTo(STYLED_EXPECTED);
    }

    @Test
    void theLayoutNamingAndVariablesFollowTheStyle() {
        final String named = styled(new XsltStyle("camelCase", 3, 3, 2, "interpolated", "function", 4, "top",
                "as_given", "named")).xslt();
        assertThat(named).contains("<xsl:call-template name=\"http\"/>").contains("<xsl:template name=\"http\">")
                .contains("<xsl:variable name=\"httpRequestMethod\"");
        assertThat(named.split(Pattern.quote("<xsl:template name=\"http\">"), -1)[1].stripLeading())
                .startsWith("<xsl:variable name=\"httpRequestMethod\"");
        final String inline = styled(new XsltStyle("snake_case", 3, 3, 2, "interpolated", "function", 4,
                "just_in_time", "as_given", "inline")).xslt();
        assertThat(inline).doesNotContain("mode=\"http\"");
        assertThat(inline.split("<xsl:template", -1)).hasSize(3);
        final String always = styled(new XsltStyle("snake_case", 1, 3, 2, "interpolated", "function", 4,
                "just_in_time", "as_given", "modes")).xslt();
        assertThat(always).contains("<xsl:variable name=\"user_name\" select=\"EventSource/User/Id\"/>");
        for (final String xslt : List.of(named, inline, always)) {
            assertThat(IndexingFixtures.compact(IndexingFixtures.transform(xslt, STYLED_EVENTS)))
                    .isEqualTo(STYLED_EXPECTED);
        }
    }

    @Test
    void aLuceneRecordIsGroupedAsTheEventIs() {
        final String lucene = FieldPlan.of("lucene", "ACME", "EventTime", List.of(
                PlannedField.of("StreamId", "id", "@StreamId"),
                PlannedField.of("EventTime", "date", "EventTime/TimeCreated"),
                PlannedField.of("UserId", "keyword", "EventSource/User/Id"))).xslt();
        assertThat(lucene).contains("<xsl:apply-templates select=\".\" mode=\"event_source\"/>")
                .contains("<!-- event_source: UserId from EventSource/User/Id -->")
                .contains("<data name=\"UserId\" value=\"{EventSource/User/Id}\"/>");
    }

    @Test
    void eventsTheIndexMustNotHoldAreDroppedAndSharedTemplatesAreCalled() {
        final FieldPlan plan = new FieldPlan("elasticsearch", "x", "@timestamp", FIELDS,
                List.of("EventDetail/TypeId = 'Heartbeat'", "EventSource/User/Id = \"monitor\" and 1 < 2"), null,
                List.of("Common-Functions"), List.of(new stroom.ai.impl.tools.generation.SharedTemplate("Common-Fields",
                "device", "device", Map.of("host", "EventSource/Device/HostName"))), true, null, List.of(),
                XsltStyle.DEFAULT, Map.of());
        assertThat(plan.events()).isEqualTo("Event[not((EventDetail/TypeId = 'Heartbeat') or (EventSource/User/Id = "
                                            + "&quot;monitor&quot; and 1 &lt; 2))]");
        assertThat(plan.writtenBy("device.name")).isNotNull();
        assertThat(plan.writtenBy("devices")).isNull();
        final String xslt = plan.xslt();
        assertThat(xslt).contains("<xsl:import href=\"Common-Fields\"/>").contains("<xsl:import href=\"Common-"
                                                                                   + "Functions\"/>")
                .contains("<xsl:call-template name=\"device\">")
                .contains("<xsl:with-param name=\"host\" select=\"EventSource/Device/HostName\"/>");
    }

    private static FieldPlan styled(final XsltStyle style) {
        return FieldPlan.of("elasticsearch", "ecs-acme-v1", "@timestamp", STYLED).withStyle(style);
    }

    // Plain Saxon has no stroom: functions: the stream id, record number and meta are given fixed values here.
    private static String runRaw(final String xslt, final String raw) {
        return IndexingFixtures.transform(xslt.replace("stroom:stream-id()", "7").replace("stroom:record-no()", "1")
                .replace("stroom:meta('Feed')", "'ACME'"), raw);
    }
}
