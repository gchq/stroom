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

import stroom.ai.impl.tools.indexing.ExampleNames.Named;
import stroom.ai.impl.tools.indexing.ExampleTemplates.Built;
import stroom.ai.impl.tools.indexing.ExampleTemplates.ComponentsUsed;
import stroom.ai.impl.tools.indexing.ExampleTemplates.DataStream;
import stroom.ai.impl.tools.indexing.IndexTemplates.NamedTemplate;
import stroom.ai.impl.tools.indexing.TemplateCheck.DocValue;
import stroom.ai.impl.tools.indexing.TemplateMappings.NotIndexed;
import stroom.ai.impl.tools.sample.PyJson;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/// An Elasticsearch index template checked against the documents an indexing pipeline writes, read as the user
/// pastes it, and built from the user's example: ported from the Stroom MCP server's `tests/test_templatecheck.py`,
/// case by case (its pure cases; those of the tools come with the tools).
class TestTemplateCheck {

    private static final EcsSchema ECS = IndexingFixtures.ECS;
    private static final String OUTPUT = """
            <?xml version="1.1"?><array xmlns="http://www.w3.org/2005/xpath-functions">
            <map><number key="StreamId">7</number><number key="EventId">1</number>\
            <string key="@timestamp">2026-09-28T10:00:00.000Z</string>
            <string key="user.name">alice</string><string key="source.ip">10.0.0.1</string>\
            <boolean key="event.outcome">false</boolean>
            <map key="host"><string key="name">ws01</string></map></map>
            <map><number key="StreamId">7</number><number key="EventId">2</number>\
            <string key="@timestamp">2026-09-28T10:05:00.000Z</string>
            <string key="user.name">bob</string><string key="source.ip">unknown</string>\
            <boolean key="event.outcome">true</boolean>
            <map key="host"><string key="name">ws02</string></map></map></array>""";
    private static final List<Map<String, Object>> DOCS = TemplateCheck.jsonXmlDocuments(OUTPUT);
    private static final String GOOD = """
            {"StreamId": {"type": "long"}, "EventId": {"type": "long"}, "@timestamp": {"type": "date"},
             "user": {"properties": {"name": {"type": "keyword"}}},
             "source": {"properties": {"ip": {"type": "keyword"}}},
             "event": {"properties": {"outcome": {"type": "boolean"}}},
             "host": {"properties": {"name": {"type": "keyword"}}}}""";
    private static final String EXAMPLE = """
            PUT _index_template/ecs-keyfob-v1
            {"index_patterns": ["ecs-keyfob-v1*"], "priority": 300, "composed_of": ["ecs-base", "ecs-source"],
             "template": {"settings": {"index": {"lifecycle": {"name": "logs-90d"}, "number_of_shards": 2}},
                          "aliases": {"keyfob": {}},
                          "mappings": {"dynamic": "strict", "date_detection": false,
                                       "properties": {"user": {"properties": {"name": {"type": "keyword",
                                                                                       "ignore_above": 256}}},
                                                      "keyfob": {"properties": {"id": {"type": "keyword"}}}}}}}""";
    private static final List<String> COMPONENTS = List.of("""
            PUT _component_template/ecs-base
            {"template": {"mappings": {"properties": {"@timestamp": {"type": "date"}, "host": {"properties": {"name":
             {"type": "keyword"}}}}}}}""", """
            {"component_templates": [{"name": "ecs-source", "component_template": {"template": {"mappings":
             {"properties": {"source": {"properties": {"ip": {"type": "ip"}}}}}}}}]}""");
    // A door-access source, planned with the ECS convention, while the user's sibling index (key fobs) follows
    // Stroom-style PascalCase with explicit objects. The two don't hold the same fields.
    private static final List<Map<String, Object>> DOOR_PLAN = fields(
            "StreamId id @StreamId", "EventId id @EventId", "@timestamp date EventTime/TimeCreated",
            "user.name keyword EventSource/User/Id", "event.code keyword EventDetail/TypeId",
            "host.name keyword EventSource/Device/HostName", "source.ip ip EventSource/Client/IPAddress",
            "message text EventDetail/Description");
    private static final List<String> DOOR_POPULATED = List.of("EventTime/TimeCreated", "EventSource/User/Id",
            "EventDetail/TypeId", "EventSource/Device/HostName", "EventSource/Client/IPAddress",
            "EventDetail/Description", "EventDetail/Authenticate/Outcome/Success", "EventSource/Device/Location/Room");
    private static final Map<String, Set<String>> CONVENTION_NAMES = Map.of(
            "EventSource/User/Id", Set.of("user.name", "UserId"), "EventDetail/TypeId", Set.of("event.code", "TypeId"),
            "EventSource/Device/HostName", Set.of("host.name", "HostName"),
            "EventSource/Device/IPAddress", Set.of("host.ip", "IPAddress"),
            "EventSource/Client/IPAddress", Set.of("source.ip"),
            "EventDetail/Description", Set.of("message", "Description"),
            "EventTime/TimeCreated", Set.of("@timestamp", "EventTime"));
    private static final String KEYFOB = """
            {"index_patterns": ["stroom-keyfob-v1*"], "priority": 300, "composed_of": ["stroom-base"],
             "template": {"mappings": {"dynamic": "strict", "properties": {
              "TypeId": {"type": "keyword", "ignore_above": 512},
              "User": {"type": "object", "properties": {"Id": {"type": "keyword", "ignore_above": 512},
                                                        "Name": {"type": "keyword", "ignore_above": 512}}},
              "Device": {"type": "object", "properties": {"HostName": {"type": "keyword", "ignore_above": 512},
                                                          "IPAddress": {"type": "ip"}}},
              "Keyfob": {"type": "object", "properties": {"Serial": {"type": "keyword", "ignore_above": 512}}},
              "Outcome": {"type": "object", "dynamic": false, "properties": {"Success": {"type": "boolean"}}}}}}}""";
    private static final String STROOM_BASE = """
            {"stroom-base": {"template": {"mappings": {"properties": {
             "StreamId": {"type": "long"}, "EventId": {"type": "long"}, "EventTime": {"type": "date"},
             "@timestamp": {"type": "date"}}}}}}""";

    @Test
    void documentsAreReadFromTheIndexingXsltOutput() {
        assertThat(DOCS).hasSize(2);
        assertThat(DOCS.getFirst().get("host")).isEqualTo(Map.of("name", new DocValue("string", "ws01")));
        assertThat(DOCS.getFirst().get("StreamId")).isEqualTo(new DocValue("number", "7"));
        assertThat(TemplateCheck.jsonXmlDocuments("<not xml")).isEmpty();
    }

    @Test
    void aTemplateThatTakesEveryFieldIsCompatible() {
        final Map<String, Object> result = compare(template(GOOD, "false", "ecs-acme-v2*"), DOCS);
        assertThat(result.get("compatible")).isEqualTo(true);
        assertThat(result.get("pipeline_changes")).isEqualTo(List.of());
        assertThat(result.get("documents_checked")).isEqualTo(2L);
    }

    @Test
    void theTemplateMustApplyToThePipelinesIndex() {
        final Map<String, Object> result = compare(template(GOOD, "false", "ecs-acme-v1*"), DOCS);
        assertThat(result.get("compatible")).isEqualTo(false);
        assertThat(strings(result, "blocking").getFirst()).contains("do not match the pipeline's index 'ecs-acme-v2'");
    }

    @Test
    @SuppressWarnings("unchecked")
    void aTypeTheValuesDoNotFitAsksForAPipelineChange() {
        final String changed = GOOD.replace("\"source\": {\"properties\": {\"ip\": {\"type\": \"keyword\"}}}",
                "\"source\": {\"properties\": {\"ip\": {\"type\": \"ip\"}}}");
        final Map<String, Object> result = compare(template(changed, "false", "ecs-acme-v2*"), DOCS);
        assertThat(result.get("compatible")).isEqualTo(false);
        assertThat(result.get("blocking")).isEqualTo(List.of("source.ip is 'unknown', not an IP address"));
        assertThat(((List<Map<String, Object>>) result.get("pipeline_changes")).getFirst().get("field"))
                .isEqualTo("source.ip");
    }

    @Test
    @SuppressWarnings("unchecked")
    void aRenamedFieldIsFlaggedAsARenameInTheXslt() {
        final String renamed = GOOD.replace("\"user\": {\"properties\": {\"name\"", "\"user\": {\"properties\": "
                                                                                   + "{\"id\"");
        final Map<String, Object> result = compare(template(renamed, "\"strict\"", "ecs-acme-v2*"), DOCS);
        assertThat(result.get("compatible")).isEqualTo(false);
        assertThat((List<Object>) result.get("pipeline_changes")).contains(change("user.name",
                "not in the template, which has 'user.id' instead", "rename 'user.name' to 'user.id' in the indexing "
                                                                     + "XSLT"));
    }

    @ParameterizedTest
    @CsvSource({"'\"strict\"', false, blocking", "false, true, notes", "true, true, notes"})
    void fieldsMissingFromTheTemplateFollowItsDynamicSetting(final String dynamic,
                                                             final boolean compatible,
                                                             final String where) {
        final String withoutEvent = GOOD.replace("\"event\": {\"properties\": {\"outcome\": {\"type\": \"boolean\"}}},",
                "");
        final Map<String, Object> result = compare(template(withoutEvent, dynamic, "ecs-acme-v2*"), DOCS);
        assertThat(result.get("compatible")).isEqualTo(compatible);
        assertThat(strings(result, where)).anyMatch(m -> m.contains("event.outcome"));
    }

    @Test
    void aFieldMappedAsAValueCannotAlsoHoldChildren() {
        final String host = GOOD.replace("\"host\": {\"properties\": {\"name\": {\"type\": \"keyword\"}}}",
                "\"host\": {\"type\": \"keyword\"}");
        final Map<String, Object> result = compare(template(host, "false", "ecs-acme-v2*"), DOCS);
        assertThat(result.get("compatible")).isEqualTo(false);
        assertThat(strings(result, "blocking")).anyMatch(b -> b.contains("maps 'host' as keyword"));
    }

    @Test
    void fieldsTheTemplateAddsAreFlaggedAsMissingFromThePipeline() {
        final String more = GOOD.replace("\"event\": {\"properties\": {\"outcome\": {\"type\": \"boolean\"}}}",
                "\"event\": {\"properties\": {\"outcome\": {\"type\": \"boolean\"}, \"action\": {\"type\": "
                + "\"keyword\"}}}");
        final Map<String, Object> result = compare(template(more, "false", "ecs-acme-v2*"), DOCS);
        assertThat(result.get("compatible")).isEqualTo(true);
        assertThat(result.get("pipeline_changes")).isEqualTo(List.of(change("event.action",
                "in the template but the pipeline never writes it", "add 'event.action' to the indexing XSLT (from "
                                                                    + "the right event-logging path), or drop it "
                                                                    + "from the template")));
    }

    @Test
    void dataStreamsNeedTimestampAndDateFormatsAreChecked() {
        final List<Map<String, Object>> noTime = new ArrayList<>();
        for (final Map<String, Object> doc : DOCS) {
            final Map<String, Object> copy = new LinkedHashMap<>(doc);
            copy.remove("@timestamp");
            noTime.add(copy);
        }
        final Map<String, Object> stream = template(GOOD, "false", "ecs-acme-v2*");
        stream.put("data_stream", new LinkedHashMap<>());
        assertThat(compare(stream, noTime).get("compatible")).isEqualTo(false);
        final String epochOnly = GOOD.replace("\"@timestamp\": {\"type\": \"date\"}",
                "\"@timestamp\": {\"type\": \"date\", \"format\": \"epoch_millis\"}");
        assertThat(strings(compare(template(epochOnly, "false", "ecs-acme-v2*"), DOCS), "blocking"))
                .anyMatch(b -> b.contains("date format epoch_millis rejects"));
    }

    @Test
    void templatesAreReadAsUsersPasteThem() {
        final Map<String, Object> body = template(GOOD, "false", "ecs-acme-v2*");
        assertThat(IndexTemplates.parseTemplate("PUT _index_template/ecs-acme\n" + PyJson.dumps(body)))
                .isEqualTo(new NamedTemplate("ecs-acme", body));
        final Map<String, Object> got = Map.of("index_templates", List.of(Map.of("name", "x", "index_template",
                body)));
        assertThat(IndexTemplates.parseTemplate(PyJson.dumps(got))).isEqualTo(new NamedTemplate("x", body));
        assertThatThrownBy(() -> IndexTemplates.parseTemplate("PUT _index_template/x\n{oops"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not valid JSON");
    }

    @Test
    @SuppressWarnings("unchecked")
    void theFinalTemplateFollowsTheUsersExampleAndItsComponents() {
        final FieldPlan plan = FieldPlan.of("elasticsearch", "ecs-acme-v2", "@timestamp", List.of(
                PlannedField.of("@timestamp", "date", "EventTime/TimeCreated"),
                PlannedField.of("user.name", "text", "EventSource/User/Id"),
                PlannedField.of("source.ip", "keyword", "EventSource/Client/IPAddress"),
                PlannedField.of("event.outcome", "boolean", "x")));
        final Map<String, Object> planned = body(plan);
        final Map<String, Object> example = IndexTemplates.parseTemplate(EXAMPLE).body();
        final Map<String, Map<String, Object>> components = IndexTemplates.parseComponentTemplates(COMPONENTS);
        assertThat(components).containsOnlyKeys("ecs-base", "ecs-source");
        final Built built = fromExample(planned, example, components);
        final Map<String, Object> body = built.body();
        // The new index's pattern; the example's priority, composed_of, settings and mapping parameters; no
        // aliases.
        assertThat(body.get("index_patterns")).isEqualTo(List.of("ecs-acme-v2*"));
        assertThat(body.get("priority")).isEqualTo(300L);
        assertThat(body.get("composed_of")).isEqualTo(List.of("ecs-base", "ecs-source"));
        final Map<String, Object> template = PyValues.map(body.get("template"));
        assertThat(PyValues.map(PyValues.map(template.get("settings")).get("index")).get("number_of_shards"))
                .isEqualTo(2L);
        final Map<String, Object> mappings = PyValues.map(template.get("mappings"));
        assertThat(mappings.get("dynamic")).isEqualTo("strict");
        assertThat(mappings.get("date_detection")).isEqualTo(false);
        assertThat(template).doesNotContainKey("aliases");
        // Fields the components map are left to them; the example's own types are kept; the rest come from the
        // plan.
        final Map<String, Map<String, Object>> fields = TemplateMappings.readMapping(mappings).fields();
        assertThat(fields).doesNotContainKeys("@timestamp", "source");
        assertThat(fields.get("user.name")).isEqualTo(Map.of("type", "keyword", "ignore_above", 256L));
        assertThat(fields.get("event.outcome")).isEqualTo(Map.of("type", "boolean"));
        assertThat(built.notes()).anyMatch(n -> n.startsWith("left to the component templates")
                                                && n.contains("'source.ip'"))
                .anyMatch(n -> n.contains("aliases are not copied"));
        // Composed as Elasticsearch would, it maps every field the plan writes.
        final TemplateMappings.Composed composed = TemplateMappings.compose(body, components);
        assertThat(composed.missing()).isEmpty();
        assertThat(TemplateMappings.readMappingFields(composed.body()).get("source.ip")).isEqualTo(Map.of("type",
                "ip"));
        // Without the components, the note asks for them.
        assertThat(fromExample(planned, example, Map.of()).notes())
                .anyMatch(n -> n.startsWith("composed_of ['ecs-base', 'ecs-source'] not given"));
    }

    @Test
    void anIndexMappingServesAsTheExampleAndIsNotCheckedForPatterns() {
        final NamedTemplate read = IndexTemplates.parseTemplate(
                "{\"ecs-keyfob-v1-000001\": {\"mappings\": {\"properties\": {\"a\": {\"type\": \"keyword\"}}}}}");
        assertThat(read.name()).isEqualTo("ecs-keyfob-v1-000001");
        assertThat(PyJson.dumps(read.body()))
                .isEqualTo("{\"template\": {\"mappings\": {\"properties\": {\"a\": {\"type\": \"keyword\"}}}}}");
        assertThat(strings(TemplateCheck.compare(read.body(), List.of(), "ecs-acme-v2", ECS), "notes").getFirst())
                .startsWith("a mapping, not an index template");
        assertThatThrownBy(() -> IndexTemplates.parseComponentTemplates(List.of("{\"template\": {\"mappings\": {}}}")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("with its name");
    }

    @Test
    void newFieldsFollowTheExamplesTypeStyles() {
        final Map<String, Object> planned = IndexingFixtures.json("""
                {"index_patterns": ["ecs-door-v1*"], "template": {"mappings": {"properties": {
                 "user": {"properties": {"name": {"type": "keyword"}}},
                 "event": {"properties": {"action": {"type": "keyword"}, "created": {"type": "date"}}},
                 "message": {"type": "text"}}}}}""");
        final Map<String, Object> example = IndexingFixtures.json("""
                {"index_patterns": ["ecs-keyfob-v1*"], "template": {"mappings": {"properties": {
                 "host": {"properties": {"name": {"type": "keyword", "ignore_above": 1024}}},
                 "source": {"properties": {"ip": {"type": "ip"}}},
                 "event": {"properties": {"kind": {"type": "keyword", "ignore_above": 1024},
                                          "ingested": {"type": "date", "format": "strict_date_optional_time"}}},
                 "error": {"properties": {"message": {"type": "text", "fields": {"keyword": {"type":
                  "keyword"}}}}}}}}}""");
        final Built built = fromExample(planned, example, Map.of());
        final Map<String, Map<String, Object>> fields = TemplateMappings.readMappingFields(built.body());
        assertThat(fields.get("user.name")).isEqualTo(Map.of("type", "keyword", "ignore_above", 1024L));
        assertThat(fields.get("event.created")).isEqualTo(Map.of("type", "date", "format",
                "strict_date_optional_time"));
        assertThat(PyJson.dumps(fields.get("message")))
                .isEqualTo("{\"type\": \"text\", \"fields\": {\"keyword\": {\"type\": \"keyword\"}}}");
        assertThat(built.notes()).anyMatch(n -> n.contains("mapped in the example's style for their type")
                                                && n.contains("user.name"));
        // Objects the example leaves undeclared stay undeclared; its fields this source lacks are listed, not
        // copied.
        final Map<String, Object> properties = PyValues.map(TemplateMappings.mappingsOf(built.body())
                .get("properties"));
        assertThat(PyValues.map(properties.get("user"))).doesNotContainKey("type");
        assertThat(properties).doesNotContainKey("host");
        assertThat(built.notes()).anyMatch(n -> n.startsWith("in the example but not written by this pipeline")
                                                && n.contains("host.name"));
    }

    @Test
    void namingStylesAreJudgedPerDottedPart() {
        assertThat(List.of("User.Id", "TypeId", "IPAddress", "user.name", "source.ip", "src_ip", "message",
                "lastModified", "userName", "user.createdOn", "User.created_on").stream()
                .map(ExampleNames::namingStyle).toList())
                .containsExactly("pascal", "pascal", "pascal", "ecs", "ecs", "ecs", "ecs", "camel", "camel", "camel",
                        "other");
        assertThat(List.of("@timestamp", "_id", "StreamId", "EventId")).allMatch(n -> ExampleNames.namingStyle(n)
                                                                                      == null);
        assertThat(ExampleNames.exampleNaming(List.of("User.Id", "TypeId", "Device.HostName", "message")))
                .isEqualTo("pascal");
        // One-word lower-case names fit camelCase too: the camelCase names decide it.
        assertThat(ExampleNames.exampleNaming(List.of("lastModified", "userName", "user.createdOn", "message",
                "status"))).isEqualTo("camel");
        assertThat(ExampleNames.exampleNaming(List.of("user.name", "source.ip", "message"))).isEqualTo("ecs");
        assertThat(ExampleNames.exampleNaming(List.of("UserId", "user_name"))).isNull();
        assertThat(ExampleNames.exampleDotted(List.of("User.Id", "Device.HostName", "TypeId"))).isTrue();
        assertThat(ExampleNames.exampleDotted(List.of("UserId", "TypeId", "User.Id"))).isFalse();
    }

    @Test
    void aPascalCaseExampleWithObjectsNamesAndShapesASourceThatDiffersFromIt() {
        final Map<String, Map<String, Object>> base = components(STROOM_BASE);
        final Map<String, Object> example = TemplateMappings.compose(IndexingFixtures.json(KEYFOB), base).body();
        final Named named = ExampleNames.namesFromExample(DOOR_PLAN, TemplateMappings.readMappingFields(example),
                CONVENTION_NAMES, DOOR_POPULATED);
        final Map<String, Object> names = bySource(named.fields());
        // The key conventions: the user is User.Id and the event type TypeId, as in the example.
        assertThat(names).containsEntry("EventSource/User/Id", "User.Id")
                .containsEntry("EventDetail/TypeId", "TypeId")
                .containsEntry("EventSource/Device/HostName", "Device.HostName")
                // Not in the example: named in its style. In the sample and in the example: added, typed as it
                // types it.
                .containsEntry("EventSource/Client/IPAddress", "Client.IPAddress")
                .containsEntry("EventDetail/Description", "Description");
        assertThat(named.fields()).contains(Map.of("name", "Outcome.Success", "type", "boolean", "source",
                "EventDetail/Authenticate/Outcome/Success"));
        // Stroom's ids and the time field keep their names; a populated path the example has no field for is not
        // added.
        assertThat(List.of(names.get("@StreamId"), names.get("@EventId"), names.get("EventTime/TimeCreated")))
                .containsExactly("StreamId", "EventId", "@timestamp");
        assertThat(names).doesNotContainKey("EventSource/Device/Location/Room");
        assertThat(named.notes()).anyMatch(n -> n.startsWith("named as the example names them")
                                                && n.contains("user.name -> User.Id"))
                .anyMatch(n -> n.startsWith("not in the example, named in its style (PascalCase")
                               && n.contains("source.ip -> Client.IPAddress"))
                .anyMatch(n -> n.startsWith("in the example but not in this source") && n.contains("Keyfob.Serial")
                               && n.contains("User.Name"));

        final Built built = fromExample(planTemplate(named.fields()), IndexingFixtures.json(KEYFOB), base);
        final Map<String, Object> properties = PyValues.map(TemplateMappings.mappingsOf(built.body())
                .get("properties"));
        assertThat(PyJson.dumps(properties.get("User"))).isEqualTo(
                "{\"type\": \"object\", \"properties\": {\"Id\": {\"type\": \"keyword\", \"ignore_above\": 512}}}");
        assertThat(properties.get("TypeId")).isEqualTo(Map.of("type", "keyword", "ignore_above", 512L));
        assertThat(PyJson.dumps(properties.get("Outcome"))).isEqualTo(
                "{\"type\": \"object\", \"dynamic\": false, \"properties\": {\"Success\": {\"type\": \"boolean\"}}}");
        // A new object, declared as the example declares its objects; a new keyword in the example's keyword
        // style.
        assertThat(PyJson.dumps(properties.get("Client")))
                .isEqualTo("{\"type\": \"object\", \"properties\": {\"IPAddress\": {\"type\": \"ip\"}}}");
        assertThat(properties.get("Description")).isEqualTo(Map.of("type", "text"));
        assertThat(properties).doesNotContainKeys("Keyfob", "StreamId");
        assertThat(built.body().get("composed_of")).isEqualTo(List.of("stroom-base"));
        assertThat(TemplateMappings.mappingsOf(built.body()).get("dynamic")).isEqualTo("strict");
        assertThat(built.notes()).noneMatch(n -> n.startsWith("named unlike"));
    }

    @Test
    void anEcsExampleRenamesAFlatPlanWithKnownEcsNames() {
        final List<Map<String, Object>> flat = fields("StreamId id @StreamId", "UserId keyword EventSource/User/Id",
                "TypeId keyword EventDetail/TypeId", "IPAddress ip EventSource/Device/IPAddress",
                "DoorName keyword EventDetail/Authenticate/Door/Name");
        final Map<String, Object> ecs = IndexingFixtures.json("""
                {"template": {"mappings": {"properties": {
                 "user": {"properties": {"name": {"type": "keyword"}}}, "event": {"properties": {"code": {"type":
                 "keyword"}}}, "host": {"properties": {"name": {"type": "keyword"}}}, "message": {"type":
                 "text"}}}}}""");
        final Named named = ExampleNames.namesFromExample(flat, TemplateMappings.readMappingFields(ecs),
                CONVENTION_NAMES, List.of());
        assertThat(named.fields().stream().map(f -> f.get("name")).toList())
                .containsExactly("StreamId", "user.name", "event.code", "host.ip", "door.name");
        assertThat(named.notes()).anyMatch(n -> n.contains("IPAddress -> host.ip") && n.contains("DoorName -> "
                                                                                                 + "door.name"));
    }

    @Test
    void aCamelCaseExampleNamesTheNewSourceInCamelCase() {
        final Map<String, Object> camel = IndexingFixtures.json("""
                {"index_patterns": ["app-audit-v3*"], "template": {"mappings": {"properties": {
                 "userId": {"type": "keyword"}, "userName": {"type": "keyword"}, "typeId": {"type": "keyword"},
                 "hostName": {"type": "keyword"}, "lastModified": {"type": "date", "format": "epoch_millis"},
                 "user": {"type": "object", "properties": {"createdOn": {"type": "date", "format": "epoch_millis"}}},
                 "message": {"type": "text"}}}}}""");
        final Named named = ExampleNames.namesFromExample(DOOR_PLAN, TemplateMappings.readMappingFields(camel),
                CONVENTION_NAMES, DOOR_POPULATED);
        final Map<String, Object> names = bySource(named.fields());
        assertThat(names).containsEntry("EventSource/User/Id", "userId")
                .containsEntry("EventDetail/TypeId", "typeId")
                .containsEntry("EventSource/Device/HostName", "hostName")
                .containsEntry("EventSource/Client/IPAddress", "clientIpAddress")
                .containsEntry("EventDetail/Description", "message");
        assertThat(named.notes()).anyMatch(n -> n.startsWith("in the example but not in this source")
                                                && n.contains("lastModified") && n.contains("user.createdOn")
                                                && n.contains("userName"));
        final Built built = fromExample(planTemplate(named.fields()), camel, Map.of());
        final Map<String, Object> properties = PyValues.map(TemplateMappings.mappingsOf(built.body())
                .get("properties"));
        assertThat(properties.get("userId")).isEqualTo(Map.of("type", "keyword"));
        assertThat(properties.get("clientIpAddress")).isEqualTo(Map.of("type", "ip"));
        // A date new to the example takes its date format; its fields this source lacks are not copied.
        assertThat(properties.get("@timestamp")).isEqualTo(Map.of("type", "date", "format", "epoch_millis"));
        assertThat(properties).doesNotContainKeys("lastModified", "user");
    }

    @Test
    void theUsersOwnFieldsAreMatchedByPathBeforeConventionNames() {
        // The user's example has user.id, user.name and user.emailAddress: the id is user.id, though ECS (the plan's
        // convention) calls it user.name; the name and email, which the convention leaves out, are added from the
        // sample.
        final List<Map<String, Object>> plan = fields("StreamId id @StreamId", "user.name keyword EventSource/User/Id");
        final Map<String, Object> example = IndexingFixtures.json("""
                {"template": {"mappings": {"properties": {"user": {"properties": {
                 "id": {"type": "keyword"}, "name": {"type": "keyword"}, "emailAddress": {"type": "keyword"}}}}}}}""");
        final Named named = ExampleNames.namesFromExample(plan, TemplateMappings.readMappingFields(example),
                CONVENTION_NAMES, List.of("EventSource/User/Id", "EventSource/User/Name",
                        "EventSource/User/EmailAddress", "EventDetail/Authenticate/User/Id"));
        assertThat(bySource(named.fields())).isEqualTo(Map.of("@StreamId", "StreamId", "EventSource/User/Id",
                "user.id", "EventSource/User/Name", "user.name", "EventSource/User/EmailAddress",
                "user.emailAddress"));
    }

    @Test
    void anExampleWithSubobjectsFalseMapsNamesFlatAndAllowsAValueBesideItsDottedNames() {
        // subobjects: false: the index maps user.id as a field of its own (no user object), and time beside
        // time.min.
        final FieldPlan plan = FieldPlan.of("elasticsearch", "metrics-v1", "@timestamp", List.of(
                PlannedField.of("StreamId", "id", "@StreamId"), PlannedField.of("EventId", "id", "@EventId"),
                PlannedField.of("@timestamp", "date", "EventTime/TimeCreated"),
                PlannedField.of("time", "keyword", "EventDetail/Data[@Name=\"time\"]/@Value"),
                PlannedField.of("time.min", "keyword", "EventDetail/Data[@Name=\"min\"]/@Value"),
                PlannedField.of("user.id", "keyword", "EventSource/User/Id"))).withSubobjects(false);
        assertThat(plan.required()).isEmpty();
        final Map<String, Object> planned = body(plan);
        assertThat(TemplateMappings.mappingsOf(planned).get("subobjects")).isEqualTo(false);
        assertThat(PyValues.map(TemplateMappings.mappingsOf(planned).get("properties")))
                .containsOnlyKeys("StreamId", "EventId", "@timestamp", "time", "time.min", "user.id");
        final Map<String, Object> example = IndexingFixtures.json("""
                {"index_patterns": ["metrics-sibling*"], "template": {"mappings": {"subobjects": false, "properties":
                 {"user.id": {"type": "keyword", "ignore_above": 512}, "time": {"type": "keyword"}}}}}""");
        final Built built = fromExample(planned, example, Map.of());
        final Map<String, Object> properties = PyValues.map(TemplateMappings.mappingsOf(built.body())
                .get("properties"));
        assertThat(TemplateMappings.mappingsOf(built.body()).get("subobjects")).isEqualTo(false);
        assertThat(properties).doesNotContainKey("user").containsKeys("time", "time.min");
        assertThat(properties.get("user.id")).isEqualTo(Map.of("type", "keyword", "ignore_above", 512L));
        final FieldPlan nested = plan.withSubobjects(true);
        assertThat(nested.required()).anyMatch(p -> p.contains("'time' is a value and also the object holding "
                                                               + "['time.min']"));
        // The document is nested either way; only time.min, beside its value time, is a flat key.
        final String xslt = plan.xslt();
        assertThat(xslt).contains("<map key=\"user\">").doesNotContain("key=\"user.id\"")
                .contains("key=\"time\"").contains("key=\"time.min\"");
    }

    @Test
    @SuppressWarnings("unchecked")
    void anAliasInTheExampleBecomesAFieldWhenThePipelineWritesIt() {
        // Seen in VS Code: the example (stroom_twitter) had User.Id as an alias of User.Name. Copied into the new
        // template, Elasticsearch refused it ("an alias must refer to an existing field"), yet both checks passed it.
        final Map<String, Object> example = IndexingFixtures.json("""
                {"index_patterns": ["stroom-twitter*"], "priority": 1, "template": {"mappings": {"dynamic": true,
                 "properties": {"StreamId": {"type": "long"}, "@timestamp": {"type": "date"},
                  "User.Name": {"type": "text", "fields": {"keyword": {"type": "keyword"}}},
                  "User.Id": {"path": "User.Name", "type": "alias"}}}}}""");
        final FieldPlan plan = FieldPlan.of("elasticsearch", "fortios-firewall", "@timestamp", List.of(
                PlannedField.of("StreamId", "id", "@StreamId"), PlannedField.of("EventId", "id", "@EventId"),
                PlannedField.of("@timestamp", "date", "EventTime/TimeCreated"),
                PlannedField.of("User.Id", "keyword", "EventSource/User/Id")));
        final Map<String, Object> planned = PyValues.map(plan.elasticTemplate("fortios-firewall", 200L, true, ECS)
                .get("body"));
        final Built built = fromExample(planned, example, Map.of());
        // The target's type, not an alias.
        assertThat(PyJson.dumps(TemplateMappings.readMappingFields(built.body()).get("User.Id")))
                .isEqualTo("{\"type\": \"text\", \"fields\": {\"keyword\": {\"type\": \"keyword\"}}}");
        assertThat(built.notes()).anyMatch(n -> n.contains("aliases in the example, but written by this pipeline")
                                                && n.contains("User.Id"));
        final List<Map<String, Object>> docs = List.of(new LinkedHashMap<>(Map.of(
                "StreamId", new DocValue("number", "1"), "EventId", new DocValue("number", "1"),
                "@timestamp", new DocValue("string", "2026-10-01T00:00:00Z"),
                "User", Map.of("Id", new DocValue("string", "admin")))));
        assertThat(TemplateCheck.compare(built.body(), docs, "fortios-firewall", ECS).get("compatible")).isEqualTo(
                true);
        // The template as it was agreed in that session: both problems are blocking now.
        final Map<String, Object> agreed = IndexingFixtures.json("""
                {"index_patterns": ["fortios-firewall*"], "template": {"mappings": {"properties": {
                 "StreamId": {"type": "long"}, "EventId": {"type": "long"}, "@timestamp": {"type": "date"},
                 "User": {"type": "object", "properties": {"Id": {"path": "User.Name", "type": "alias"}}}}}}}""");
        final Map<String, Object> check = TemplateCheck.compare(agreed, docs, "fortios-firewall", ECS);
        assertThat(check.get("compatible")).isEqualTo(false);
        assertThat(strings(check, "blocking"))
                .anyMatch(b -> b.contains("an alias of 'User.Name', which this template doesn't map"))
                .anyMatch(b -> b.contains("User.Id: written by the pipeline, but mapped as an alias"));
    }

    @Test
    void aTemplateFromThePlanAloneIsADataStreamAndAnExampleDecidesOtherwise() {
        // Asked for by the user: a plan-only ECS template came out a plain index, where their indices are data
        // streams.
        final FieldPlan plan = FieldPlan.of("elasticsearch", "ecs-acme-v1", "@timestamp", List.of(
                PlannedField.of("@timestamp", "date", "EventTime/TimeCreated"),
                PlannedField.of("user.name", "keyword", "EventSource/User/Id"))).withConvention("ecs", List.of());
        final Map<String, Object> body = body(plan);
        assertThat(body.get("data_stream")).isEqualTo(Map.of());
        final List<String> keys = new ArrayList<>(body.keySet());
        assertThat(keys.indexOf("data_stream")).isLessThan(keys.indexOf("template"));
        final Map<String, Object> full = PyValues.map(plan.elasticTemplate("ecs-acme-v1", null, false, ECS)
                .get("body"));
        // An example index template that is a plain index keeps it a plain index.
        final Built plain = fromExample(full, IndexTemplates.parseTemplate("""
                {"index_patterns": ["ecs-other-v1*"], "template": {"mappings": {"properties": {"user":
                 {"properties": {"name": {"type": "keyword"}}}}}}}""").body(), Map.of());
        assertThat(plain.body()).doesNotContainKey("data_stream");
        // A data stream's backing index, pasted as GET <data stream>/_mapping, says it is one.
        final Built backing = fromExample(full, IndexTemplates.parseTemplate("""
                {".ds-ecs-other-v1-2026.10.11-000001": {"mappings": {"_data_stream_timestamp": {"enabled": true},
                 "properties": {"@timestamp": {"type": "date"}}}}}""").body(), Map.of());
        assertThat(backing.body().get("data_stream")).isEqualTo(Map.of());
        assertThat(backing.notes()).anyMatch(n -> n.contains("_data_stream_timestamp"));
    }

    @Test
    void aDataStreamMapsItsOwnTimestampButStillNeedsThePipelineToWriteIt() {
        final Map<String, Object> body = IndexingFixtures.json("""
                {"index_patterns": ["ecs-acme-v1*"], "data_stream": {},
                 "template": {"mappings": {"dynamic": "strict", "properties": {"StreamId": {"type": "long"}}}}}""");
        assertThat(TemplateCheck.compare(body, docs("<string key=\"@timestamp\">2026-10-11T08:00:00.000Z</string>"),
                "ecs-acme-v1", ECS).get("compatible")).isEqualTo(true);
        final Map<String, Object> missing = TemplateCheck.compare(body, docs(""), "ecs-acme-v1", ECS);
        assertThat(missing.get("compatible")).isEqualTo(false);
        assertThat(strings(missing, "blocking")).anyMatch(b -> b.contains("needs @timestamp"));
    }

    @Test
    void aDiscoveryIndexIsADataStreamOnlyWhenEveryRecordHasItsTime() {
        final Map<String, Object> body = IndexingFixtures.json(
                "{\"index_patterns\": [\"stroom-discovery-acme-v1*\"], \"priority\": 200, \"template\": "
                + "{\"mappings\": {}}}");
        final List<Map<String, Object>> timed = List.of(Map.of("@timestamp", "2026-10-11T08:00:00Z", "user", "a"),
                Map.of("@timestamp", "2026-10-11T08:01:00Z"));
        final DataStream stream = ExampleTemplates.discoveryDataStream(body, timed, "ts");
        assertThat(stream.body().get("data_stream")).isEqualTo(Map.of());
        assertThat(new ArrayList<>(stream.body().keySet()).getLast()).isEqualTo("template");
        assertThat(stream.note()).startsWith("a data stream: all 2");
        // One record without a time: a data stream would reject it, so a plain index, and why.
        final List<Map<String, Object>> untimed = new ArrayList<>(timed);
        untimed.add(Map.of("user", "b"));
        final DataStream plain = ExampleTemplates.discoveryDataStream(body, untimed, "ts");
        assertThat(plain.body()).doesNotContainKey("data_stream");
        assertThat(plain.note()).contains("1 of 3 sample records have no time in 'ts'");
    }

    @Test
    void aRecordWithAnEmptyTimeCountsAsWithoutOne() {
        final Map<String, Object> body = IndexingFixtures.json("{\"index_patterns\": [\"d*\"], \"template\": "
                                                               + "{\"mappings\": {}}}");
        final DataStream stream = ExampleTemplates.discoveryDataStream(body, List.of(
                Map.of("@timestamp", new DocValue("string", "2026-10-11T08:00:00Z")),
                Map.of("@timestamp", new DocValue("string", " "))), "ts");
        assertThat(stream.body()).doesNotContainKey("data_stream");
        assertThat(stream.note()).contains("1 of 2");
    }

    @Test
    void everyComponentButTheBuiltInEcsOneIsAskedFor() {
        final Map<String, Object> example = Map.of("composed_of", List.of("ecs@mappings", "ecs-base", "ecs-source",
                "logs@settings"));
        final Map<String, Object> asked = IndexingHelpers.componentsWanted(example, Map.of("ecs-base", Map.of()),
                List.of());
        assertThat(asked.get("missing")).isEqualTo(List.of("ecs-source", "logs@settings"));
        // One request per name: the API takes a single name or wildcard, not a list (seen in testing).
        assertThat(asked.get("request")).isEqualTo("GET _component_template/ecs-source\n"
                                                   + "GET _component_template/logs@settings");
        assertThat(IndexingHelpers.componentsWanted(example, Map.of("ecs-base", Map.of()),
                List.of("ecs-source", "logs@settings"))).isNull();
        // Composed of nothing.
        assertThat(IndexingHelpers.componentsWanted(Map.of("index_patterns", List.of("x*")), Map.of(), List.of()))
                .isNull();
    }

    @Test
    void aComponentIsReferencedOnlyIfThePipelineWritesAFieldItMaps() {
        // Asked for by the user: the example's component templates are referenced, their fields left to them (not
        // mapped again here), and only those mapping a field this pipeline writes.
        final FieldPlan plan = FieldPlan.of("elasticsearch", "ecs-acme-v2", "@timestamp", List.of(
                PlannedField.of("@timestamp", "date", "EventTime/TimeCreated"),
                PlannedField.of("user.name", "keyword", "EventSource/User/Id")));
        final Map<String, Object> example = IndexTemplates.parseTemplate(EXAMPLE).body();
        example.put("composed_of", List.of("ecs-base", "ecs-source", "ilm-90d", "ecs@mappings"));
        final List<String> texts = new ArrayList<>(COMPONENTS);
        texts.add("PUT _component_template/ilm-90d\n{\"template\": {\"settings\": {\"index.lifecycle.name\": "
                  + "\"logs-90d\"}}}");
        final Built built = fromExample(body(plan), example, IndexTemplates.parseComponentTemplates(texts));
        // ecs-base maps @timestamp, which the pipeline writes: referenced, and @timestamp left to it. ecs-source
        // maps only source.ip, which it doesn't write: not referenced. The lifecycle component has no fields:
        // referenced, said so.
        assertThat(built.body().get("composed_of")).isEqualTo(List.of("ecs-base", "ilm-90d"));
        assertThat(TemplateMappings.readMappingFields(built.body())).doesNotContainKey("@timestamp");
        assertThat(built.notes()).anyMatch(n -> n.contains("not referenced") && n.contains("['ecs-source', "
                                                                                           + "'ecs@mappings']"))
                .anyMatch(n -> n.contains("ilm-90d (settings only, no fields)"));
    }

    @Test
    void ecsMappingsIsReferencedWhenAFieldIsLeftToIt() {
        assertThat(ExampleTemplates.componentsUsed(List.of("ecs@mappings"), Map.of(), Map.of("user.name", Map.of()),
                List.of("user.name"), false, List.of()))
                .isEqualTo(new ComponentsUsed(List.of("ecs@mappings"), List.of(), Map.of()));
        assertThat(ExampleTemplates.componentsUsed(List.of("ecs@mappings"), Map.of(), Map.of("x", Map.of()),
                List.of(), false, List.of()))
                .isEqualTo(new ComponentsUsed(List.of(), List.of("ecs@mappings"), Map.of()));
        // One the user couldn't give, and every one of a discovery index: referenced, unweighed.
        assertThat(ExampleTemplates.componentsUsed(List.of("mine"), Map.of(), Map.of("x", Map.of()), List.of(),
                false, List.of()))
                .isEqualTo(new ComponentsUsed(List.of("mine"), List.of(), Map.of("mine", "not given")));
        final ComponentsUsed used = ExampleTemplates.componentsUsed(List.of("a"), components(
                        "{\"a\": {\"template\": {\"mappings\": {\"properties\": {\"z\": {\"type\": \"keyword\"}}}}}}"),
                Map.of("x", Map.of()), List.of(), true, List.of());
        assertThat(used.used()).containsExactly("a");
        assertThat(used.unsure().get("a")).contains("discovery");
    }

    @Test
    void nominatedComponentsAreReferencedAndExcludedOnesAreNot() {
        final FieldPlan plan = FieldPlan.of("elasticsearch", "ecs-acme-v2", "@timestamp", List.of(
                PlannedField.of("@timestamp", "date", "EventTime/TimeCreated"),
                PlannedField.of("user.name", "keyword", "EventSource/User/Id")));
        // Composed of ecs-base (maps @timestamp) and ecs-source.
        final Map<String, Object> example = IndexTemplates.parseTemplate(EXAMPLE).body();
        final List<String> texts = new ArrayList<>(COMPONENTS);
        texts.add("PUT _component_template/org-users\n{\"template\": {\"mappings\": {\"properties\": {\"user\": "
                  + "{\"properties\": {\"name\": {\"type\": \"keyword\"}, \"email\": {\"type\": \"keyword\"}}}}}}}");
        texts.add("PUT _component_template/org-extra\n{\"template\": {\"mappings\": {\"properties\": {\"zzz\": "
                  + "{\"type\": \"keyword\"}}}}}");
        final Built built = ExampleTemplates.fromExample(body(plan), example,
                IndexTemplates.parseComponentTemplates(texts), false, List.of("org-users", "org-extra"),
                List.of("ecs-base"), false, ECS);
        // ecs-base excluded; ecs-source unused; both nominated referenced, org-extra though it maps nothing
        // written.
        assertThat(built.body().get("composed_of")).isEqualTo(List.of("org-users", "org-extra"));
        final Map<String, Map<String, Object>> fields = TemplateMappings.readMappingFields(built.body());
        // user.name left to org-users; ecs-base excluded: @timestamp mapped here.
        assertThat(fields).doesNotContainKey("user");
        assertThat(fields.get("@timestamp")).isEqualTo(Map.of("type", "date"));
        assertThat(built.notes()).anyMatch(n -> n.contains("excluded by standing instructions")
                                                && n.contains("ecs-base"));
    }

    @Test
    void aTemplateFromThePlanAloneComposesNominatedComponentsToo() {
        final FieldPlan plan = FieldPlan.of("elasticsearch", "ecs-acme-v2", "@timestamp", List.of(
                PlannedField.of("@timestamp", "date", "EventTime/TimeCreated"),
                PlannedField.of("user.name", "keyword", "EventSource/User/Id")));
        final Built built = ExampleTemplates.withComponents(body(plan), components(
                        "{\"org-users\": {\"template\": {\"mappings\": {\"properties\": {\"user\": {\"properties\": "
                        + "{\"name\": {\"type\": \"keyword\"}}}}}}}}"), List.of("org-users"), List.of(), ECS);
        assertThat(PyValues.strings(built.body().get("composed_of")).getLast()).isEqualTo("org-users");
        assertThat(PyValues.map(TemplateMappings.mappingsOf(built.body()).get("properties")))
                .doesNotContainKey("user");
        assertThat(built.notes()).anyMatch(n -> n.contains("['user.name']"));
    }

    @Test
    void aFlatTemplateLeavesNominatedComponentsFieldsToThemToo() {
        final Map<String, Object> body = IndexingFixtures.json("""
                {"index_patterns": ["x*"], "template": {"mappings": {"subobjects": false, "properties": {
                 "user.name": {"type": "keyword"}, "host.name": {"type": "keyword"}}}}}""");
        final Built built = ExampleTemplates.withComponents(body, components(
                        "{\"org-users\": {\"template\": {\"mappings\": {\"properties\": {\"user\": {\"properties\": "
                        + "{\"name\": {\"type\": \"keyword\"}}}}}}}}"), List.of("org-users"), List.of(), ECS);
        assertThat(built.body().get("composed_of")).isEqualTo(List.of("org-users"));
        assertThat(PyValues.map(TemplateMappings.mappingsOf(built.body()).get("properties"))).containsOnlyKeys(
                "host.name");
    }

    @Test
    void aDevToolsRequestOnOneLineIsRead() {
        // Seen in Ask Stroom AI: the server's own dev_tools given on one line, refused as not JSON.
        final String body = "{\"index_patterns\": [\"stroom-twitter-onboard-test*\"], \"template\": {\"mappings\": "
                            + "{}}}";
        for (final String text : List.of("PUT _index_template/stroom-twitter-onboard-test " + body,
                "PUT _index_template/stroom-twitter-onboard-test\n" + body,
                "PUT /_index_template/stroom-twitter-onboard-test?pretty\n" + body)) {
            final NamedTemplate read = IndexTemplates.parseTemplate(text);
            assertThat(read.name()).isEqualTo("stroom-twitter-onboard-test");
            assertThat(PyJson.dumps(read.body())).isEqualTo(body);
        }
        assertThat(IndexTemplates.parseComponentTemplates(List.of("PUT _component_template/acme {\"template\": {}}")))
                .isEqualTo(Map.of("acme", Map.of("template", Map.of())));
    }

    @Test
    void componentTemplatesAreReadHoweverTheAgentSendsThem() {
        // Seen in testing: re-sent call after call, the components came back as a GET response inside a GET
        // response, and as an escaped JSON string; and a user pastes Kibana's answers to several requests at once.
        final String base = "{\"template\": {\"mappings\": {\"properties\": {\"host\": {\"properties\": {\"name\": "
                            + "{\"type\": \"keyword\"}}}}}}}";
        final String got = "{\"component_templates\": [{\"name\": \"ecs-base\", \"component_template\": " + base
                           + "}]}";
        final String kibana = "# GET _component_template/ecs-base 200 OK\n" + got
                              + "\n# GET _component_template/org-ilm 200 OK\n{\"component_templates\": [{\"name\": "
                              + "\"org-ilm\", \"component_template\": {\"template\": {\"settings\": {}}}}]}";
        assertThat(IndexTemplates.parseComponentTemplates(List.of(kibana))).containsOnlyKeys("ecs-base", "org-ilm");
        assertThat(PyJson.dumps(IndexTemplates.parseComponentTemplates(List.of(Map.of("component_templates",
                List.of(PyJson.loads(got)))))))
                .isEqualTo("{\"ecs-base\": " + base + "}");
        assertThat(PyJson.dumps(IndexTemplates.parseComponentTemplates(List.of(PyJson.dumps(
                "PUT _component_template/ecs-base\n" + base)))))
                .isEqualTo("{\"ecs-base\": " + base + "}");
        assertThat(PyJson.dumps(IndexTemplates.parseComponentTemplates(List.of(PyJson.loads(got),
                "PUT _component_template/b {}\nPUT _component_template/c {}"))))
                .isEqualTo("{\"ecs-base\": " + base + ", \"b\": {}, \"c\": {}}");
    }

    @Test
    void fieldsTheTemplateDoesntIndexAreSaidToBeUnsearchable() {
        // Asked about by the user: a field mapped not to be indexed is kept but no search finds it.
        final Map<String, Object> mappings = IndexingFixtures.json("""
                {"dynamic": true, "properties": {
                 "secret": {"type": "keyword", "index": false},
                 "raw": {"type": "object", "enabled": false},
                 "user": {"properties": {"name": {"type": "keyword"}, "token": {"type": "keyword", "index":
                  "false"}}}}}""");
        assertThat(TemplateMappings.notIndexed(mappings)).isEqualTo(new NotIndexed(Set.of("secret", "user.token"),
                Set.of("raw")));
        assertThat(TemplateMappings.unsearchable(mappings, List.of("secret", "raw.blob", "user.name", "user.token")))
                .containsExactly("raw.blob", "secret", "user.token");
        final List<Map<String, Object>> docs = TemplateCheck.jsonXmlDocuments(
                "<array xmlns=\"http://www.w3.org/2005/xpath-functions\"><map><string key=\"secret\">s</string>"
                + "<map key=\"raw\"><string key=\"blob\">b</string></map><map key=\"user\"><string key=\"name\">"
                + "alice</string></map></map></array>");
        final Map<String, Object> body = new LinkedHashMap<>();
        body.put("index_patterns", List.of("x*"));
        body.put("template", Map.of("mappings", mappings));
        final Map<String, Object> said = TemplateCheck.compare(body, docs, "x", ECS);
        assertThat(said.get("compatible")).isEqualTo(true);
        assertThat(strings(said, "notes")).anyMatch(n -> n.contains("['raw.blob', 'secret']")
                                                         && n.contains("not searchable"));
    }

    @Test
    void theTemplateIsShownAsADevToolsRequestLaidOutToRead() {
        // Indented throughout, a 50-field template ran to 150 lines and 10,000 characters, and the reply that showed
        // it to the user for review was too long for the client to show: an object of plain values on one line.
        final Map<String, Object> body = IndexingFixtures.json("""
                {"index_patterns": ["ecs-acme-v2*"], "template": {"mappings": {"properties": {
                 "user": {"properties": {"name": {"type": "keyword", "ignore_above": 1024}}}}}}}""");
        assertThat(IndexTemplates.devTools("ecs-acme-v2", body)).isEqualTo("""
                PUT _index_template/ecs-acme-v2
                {
                  "index_patterns": ["ecs-acme-v2*"],
                  "template": {
                    "mappings": {
                      "properties": {"user": {"properties": {"name": {"type": "keyword", "ignore_above": 1024}}}}
                    }
                  }
                }""");
        assertThat(IndexTemplates.devTools("x", IndexingFixtures.json("{\"index_patterns\": [\"x*\"], "
                                                                      + "\"priority\": 1}")))
                .isEqualTo("PUT _index_template/x\n{\"index_patterns\": [\"x*\"], \"priority\": 1}");
    }

    private static Map<String, Object> compare(final Map<String, Object> body, final List<Map<String, Object>> docs) {
        return TemplateCheck.compare(body, docs, "ecs-acme-v2", ECS);
    }

    private static Map<String, Object> template(final String properties, final String dynamic, final String pattern) {
        return IndexingFixtures.json("{\"index_patterns\": [\"" + pattern + "\"], \"template\": {\"mappings\": "
                                     + "{\"dynamic\": " + dynamic + ", \"properties\": " + properties + "}}}");
    }

    private static Map<String, Object> change(final String field, final String problem, final String change) {
        final Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("field", field);
        entry.put("problem", problem);
        entry.put("change", change);
        return entry;
    }

    @SuppressWarnings("unchecked")
    private static List<String> strings(final Map<String, Object> result, final String key) {
        return (List<String>) result.get(key);
    }

    private static Built fromExample(final Map<String, Object> planned,
                                     final Map<String, Object> example,
                                     final Map<String, Map<String, Object>> components) {
        return ExampleTemplates.fromExample(planned, example, components, false, List.of(), List.of(), false, ECS);
    }

    private static Map<String, Object> body(final FieldPlan plan) {
        return PyValues.map(plan.elasticTemplate(plan.indexName(), ECS).get("body"));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Map<String, Object>> components(final String json) {
        return (Map<String, Map<String, Object>>) (Map<?, ?>) IndexingFixtures.json(json);
    }

    private static Map<String, Object> planTemplate(final List<Map<String, Object>> fields) {
        final List<PlannedField> planned = fields.stream().map(PlannedField::fromJson).toList();
        return body(FieldPlan.of("elasticsearch", "stroom-door-v1", "@timestamp", planned));
    }

    private static Map<String, Object> bySource(final List<Map<String, Object>> fields) {
        final Map<String, Object> out = new LinkedHashMap<>();
        fields.forEach(field -> out.put((String) field.get("source"), field.get("name")));
        return out;
    }

    // Fields as "name type source".
    private static List<Map<String, Object>> fields(final String... specs) {
        final List<Map<String, Object>> out = new ArrayList<>();
        for (final String spec : specs) {
            final String[] parts = spec.split(" ", 3);
            final Map<String, Object> field = new LinkedHashMap<>();
            field.put("name", parts[0]);
            field.put("type", parts[1]);
            field.put("source", parts[2]);
            out.add(field);
        }
        return out;
    }

    private static List<Map<String, Object>> docs(final String extra) {
        return TemplateCheck.jsonXmlDocuments("<array xmlns=\"http://www.w3.org/2005/xpath-functions\"><map>"
                                              + "<number key=\"StreamId\">7</number>" + extra + "</map></array>");
    }
}
