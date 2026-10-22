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

import stroom.ai.impl.tools.AgentKnowledge;
import stroom.ai.impl.tools.JsonValues;
import stroom.ai.impl.tools.ToolRefusal;
import stroom.ai.impl.tools.indexing.ExampleTemplates.Built;
import stroom.ai.impl.tools.validation.EventSchema;
import stroom.ai.impl.tools.validation.HeldSchemasWorld;
import stroom.util.yaml.YamlUtil;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/// The Elastic Common Schema as the tools know it ([EcsSchema], the bundled `ecs_fields.json` and
/// `ecs_component.json`): asked for by the user, as the Stroom MCP server had known ECS only through the few names
/// its convention profile mapped. Ported from the server's `tests/test_ecs.py`, case by case.
class TestEcsSchema {

    private static final EcsSchema ECS = IndexingFixtures.ECS;
    private static final String EVENTS = """
            <Events xmlns="event-logging:3">
            <Event StreamId="7" EventId="1"><EventTime><TimeCreated>2026-10-01T09:00:00.000Z</TimeCreated></EventTime>
              <EventDetail><Authenticate><Action>Logon</Action><Outcome><Success>false</Success></Outcome>\
            </Authenticate></EventDetail></Event>
            <Event StreamId="7" EventId="2"><EventTime><TimeCreated>2026-10-01T09:01:00.000Z</TimeCreated></EventTime>
              <EventDetail><Authenticate><Action>Logon</Action><Outcome><Success>true</Success></Outcome>\
            </Authenticate></EventDetail></Event>
            <Event StreamId="7" EventId="3"><EventTime><TimeCreated>2026-10-01T09:02:00.000Z</TimeCreated></EventTime>
              <EventDetail><Authenticate><Action>Logoff</Action></Authenticate></EventDetail></Event>
            </Events>""";

    @Test
    void theBundledSchemaIsWhole() {
        assertThat(ECS.fieldNames()).hasSizeGreaterThan(2000);
        assertThat(ECS.fieldSets()).contains("process");
        assertThat(ECS.field("event.outcome").get("allowed")).isEqualTo(List.of("failure", "success", "unknown"));
    }

    @Test
    void theSchemaIsReadFromTheBundledKnowledgeOnce() {
        final AgentKnowledge knowledge = Mockito.mock(AgentKnowledge.class);
        final String fields = IndexingFixtures.bundled(EcsSchema.FIELDS_FILE);
        final String component = IndexingFixtures.bundled(EcsSchema.COMPONENT_FILE);
        Mockito.when(knowledge.conventionsFile(EcsSchema.FIELDS_FILE)).thenReturn(Optional.of(fields));
        Mockito.when(knowledge.conventionsFile(EcsSchema.COMPONENT_FILE)).thenReturn(Optional.of(component));
        final EcsSchema first = EcsSchema.from(knowledge).orElseThrow();
        assertThat(EcsSchema.from(knowledge)).containsSame(first);
        assertThat(first.version()).isEqualTo(ECS.version());
        // Without the module that bundles them, there is none.
        final AgentKnowledge none = Mockito.mock(AgentKnowledge.class);
        Mockito.when(none.conventionsFile(Mockito.anyString())).thenReturn(Optional.empty());
        assertThat(EcsSchema.from(none)).isEmpty();
    }

    @Test
    @SuppressWarnings("unchecked")
    void everyMappingIsAnEcsFieldOfItsTypeFromAnEventLoggingPath() {
        final Map<String, Object> profile = YamlUtil.getMapper().readValue(IndexingFixtures.bundled("ecs.yaml"),
                Map.class);
        final Map<String, Map<String, Object>> fieldMap = (Map<String, Map<String, Object>>) profile.get("field_map");
        final List<EventSchema> schemas = List.of(EventSchema.parse(HeldSchemasWorld.XSD_4_1_0),
                EventSchema.parse(IndexingFixtures.resource(
                        "/stroom/ai/impl/tools/generation/event-logging-v3.5.2.xsd")));
        // A wildcard stands for one element: the path must exist for at least one action that has it.
        final Map<String, List<String>> actions = new LinkedHashMap<>();
        actions.put("Network/*", List.of("Permit", "Deny", "Connect"));
        actions.put("EventDetail/*", List.of("Authenticate", "Process", "View", "Create", "Delete", "Alert",
                "Network"));
        fieldMap.forEach((path, spec) -> {
            final String name = (String) spec.get("name");
            assertThat(ECS.field(name)).as(path).isNotNull();
            assertThat(ECS.check(name, (String) spec.get("type"))).as(path).isNull();
            List<String> concrete = List.of(path);
            for (final Map.Entry<String, List<String>> wildcard : actions.entrySet()) {
                if (path.contains(wildcard.getKey())) {
                    final List<String> expanded = new ArrayList<>();
                    for (final String candidate : concrete) {
                        for (final String action : wildcard.getValue()) {
                            expanded.add(candidate.replaceFirst(java.util.regex.Pattern.quote(wildcard.getKey()),
                                    wildcard.getKey().replace("*", action)));
                        }
                    }
                    concrete = expanded;
                }
            }
            for (final EventSchema schema : schemas) {
                final List<String> resolved = concrete.stream().filter(candidate -> {
                    try {
                        schema.resolve(candidate);
                        return true;
                    } catch (final IllegalArgumentException e) {
                        return false;
                    }
                }).toList();
                assertThat(resolved).as(path + ": in no action").isNotEmpty();
            }
        });
    }

    @Test
    void aPlanIsCheckedAgainstEcsFieldByField() {
        assertThat(ECS.check("source.port", "keyword"))
                .isEqualTo("source.port is typed keyword, where ECS " + ECS.version() + " has long (plan type long)");
        final String said = ECS.check("user.nmae", "keyword");
        assertThat(said).contains("isn't an ECS").contains("did you mean user.name");
        // Outside ECS's field sets: a field of the user's own, which ECS allows. Stroom's own fields are exempt.
        assertThat(ECS.check("acme.ticket", "keyword")).isNull();
        assertThat(ECS.check("StreamId", "id")).isNull();
        assertThat(ECS.check("message", "text")).isNull();
        assertThat(ECS.check("process.command_line", "keyword")).isNull();
        final FieldPlan plan = FieldPlan.of("elasticsearch", "ecs-acme-v1", "@timestamp",
                List.of(PlannedField.of("source.port", "keyword", "EventSource/Client/Port")))
                .withConvention("ecs", List.of());
        assertThat(plan.conventionProblems(ECS)).isNotEmpty();
        assertThat(plan.withConvention(null, List.of()).conventionProblems(ECS)).isEmpty();
    }

    @Test
    void everyTopLevelNameNeitherEcsNorADeclaredNamespaceIsReported() {
        // Seen: an ECS plan drafted authenticate.*, authorise.*, copy.* and create.*, the check passed them as
        // custom fields, and the agent told the user only its own secret.* and secretserver.* fields weren't ECS.
        final List<PlannedField> fields = new ArrayList<>();
        fields.add(PlannedField.of("message", "text", "EventDetail/Description"));
        for (final String name : List.of("StreamId", "EventId", "tags", "labels.team", "user.name",
                "authenticate.user.id", "authenticate.user.domain", "copy.source.object.id", "secretserver.ticket",
                "secret.name")) {
            fields.add(PlannedField.of(name, "keyword", "EventDetail/TypeId"));
        }
        final FieldPlan plan = FieldPlan.of("elasticsearch", "stroom-ecs-secretserver-v1", "@timestamp", fields)
                .withConvention("ecs", List.of("secretserver"));
        final List<String> said = plan.conventionProblems(ECS);
        assertThat(said.stream().map(p -> p.split(" ")[0]).toList())
                .containsExactly("authenticate.*", "copy.*", "secret.*");
        assertThat(said.getFirst()).startsWith("authenticate.* (authenticate.user.id, authenticate.user.domain): "
                                               + "authenticate is neither an ECS")
                .contains("(secretserver.authenticate.user.id)");
        assertThat(plan.withConvention("ecs", List.of("secretserver", "secret", "authenticate", "copy"))
                .conventionProblems(ECS)).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({"stroom-ecs-secretserver-v1, secretserver", "ecs-keycloak-v1, keycloak",
                "'ecs-microsoft-windows{_suffix}v1', microsoft_windows", "ecs-host-v2, host_custom",
                "ecs-v1, custom"})
    void aSourcesNamespaceIsNamedAfterTheIndex(final String indexName, final String namespace) {
        assertThat(ECS.sourceNamespace(indexName)).isEqualTo(namespace);
    }

    @ParameterizedTest
    @CsvSource(value = {"source_ip, source.ip", "SourceIp, source.ip", "Source-IP, source.ip",
                        "user_full_name, user.full_name", "http_request_method, http.request.method",
                        "UserAgentOriginal, user_agent.original",
                        // An abbreviation or a name of the source's own is not guessed at.
                        "src_ip, NULL", "dstport, NULL", "session_id, NULL"}, nullValues = "NULL")
    void aDataElementsNameIsAnEcsFieldOnlyWrittenAnotherWay(final String dataName, final String expected) {
        assertThat(ECS.forDataName(dataName)).isEqualTo(expected);
    }

    @Test
    void outcomeIsWrittenAsEcsHasItAndLeftOutWithNone() {
        // event-logging's Success is true or false; ECS's event.outcome is success or failure. No Outcome is
        // success in event-logging, but ECS's own reading is left to the user: the field is left out.
        final FieldPlan plan = FieldPlan.of("elasticsearch", "ecs-acme-v1", "@timestamp", List.of(
                PlannedField.of("StreamId", "id", "@StreamId"), PlannedField.of("EventId", "id", "@EventId"),
                PlannedField.of("@timestamp", "date", "EventTime/TimeCreated"),
                PlannedField.of("event.action", "keyword", "EventDetail/*/Action"),
                new PlannedField("event.outcome", "keyword", "EventDetail/*/Outcome/Success", "", "outcome", false)))
                .withConvention("ecs", List.of());
        assertThat(plan.conventionProblems(ECS)).isEmpty();
        final List<String> outcomes = new ArrayList<>();
        for (final Map<String, Object> document : TemplateCheck.jsonXmlDocuments(IndexingFixtures.transform(
                plan.xslt(), EVENTS))) {
            final Object outcome = JsonValues.mapOrEmpty(document.get("event")).get("outcome");
            outcomes.add(outcome == null
                    ? null
                    : ((TemplateCheck.DocValue) outcome).text());
        }
        assertThat(outcomes).containsExactly("failure", "success", null);
    }

    @Test
    @SuppressWarnings("unchecked")
    void anEcsPlansIndexTemplateLeavesEcsFieldsToElasticsEcsMappings() {
        // Asked by the user: composed of ecs@mappings (the recommended way), and relying on it for the standard ECS
        // fields, mapping only those it doesn't. It maps as documents bring fields, so dynamic mapping is on.
        final FieldPlan plan = FieldPlan.of("elasticsearch", "ecs-acme-v1", "@timestamp", List.of(
                PlannedField.of("StreamId", "id", "@StreamId"),
                PlannedField.of("@timestamp", "date", "EventTime/TimeCreated"),
                PlannedField.of("source.ip", "ip", "EventSource/Client/IPAddress"),
                PlannedField.of("message", "text", "EventDetail/Description"),
                // Not ECS's type.
                PlannedField.of("source.port", "keyword", "EventSource/Client/Port"),
                // ecs@mappings maps it as integer.
                PlannedField.of("gen_ai.usage.input_tokens", "long", "Data"),
                // The user's own.
                PlannedField.of("acme.ticket", "keyword", "EventDetail/TypeId")))
                .withConvention("ecs", List.of());
        final Map<String, Object> body = body(plan.elasticTemplate("ecs-acme-v1", ECS));
        final Map<String, Object> mappings = TemplateMappings.mappingsOf(body);
        assertThat(body.get("composed_of")).isEqualTo(List.of("ecs@mappings"));
        assertThat(mappings.get("dynamic")).isEqualTo(true);
        final Map<String, Object> properties = (Map<String, Object>) mappings.get("properties");
        // message too: the component maps it as match_only_text, which Stroom can't search (seen in production).
        assertThat(properties).containsOnlyKeys("StreamId", "source", "gen_ai", "acme", "message");
        assertThat(((Map<String, Object>) properties.get("source")).get("properties"))
                .isEqualTo(Map.of("port", Map.of("type", "keyword")));
        assertThat(properties.get("message")).isEqualTo(Map.of("type", "text"));
        assertThat(TemplateMappings.readMapping(mappings).fields().get("gen_ai.usage.input_tokens"))
                .isEqualTo(Map.of("type", "long"));
        // Every field mapped, for a template built from the user's example (which says what it is composed of).
        final Map<String, Object> full = TemplateMappings.mappingsOf(body(plan.elasticTemplate("ecs-acme-v1", null,
                false, ECS)));
        assertThat(full.get("dynamic")).isEqualTo(false);
        assertThat(TemplateMappings.readMapping(full).fields().get("source.ip")).isEqualTo(Map.of("type", "ip"));
        final Map<String, Object> other = body(plan.withConvention("stroom-flat", List.of())
                .elasticTemplate("acme-v1", ECS));
        assertThat(other).doesNotContainKey("composed_of");
        assertThat(TemplateMappings.mappingsOf(other).get("dynamic")).isEqualTo(false);
    }

    @Test
    void theComponentIsMeasuredAndMapsEcsFieldsAsEcsSays() {
        // dev/ecs_component.py measured it against Elasticsearch: the few it maps otherwise are mapped by the
        // template.
        assertThat(ECS.componentType("source.ip")).isEqualTo("ip");
        assertThat(ECS.componentType("message")).isEqualTo("match_only_text");
        assertThat(ECS.componentType("gen_ai.usage.input_tokens")).isEqualTo("integer");
        assertThat(ECS.componentType("acme.ticket")).isNull();
        assertThat(ECS.leftToComponent("url.original", "keyword")).isTrue();
        assertThat(ECS.leftToComponent("source.port", "keyword")).isFalse();
        assertThat(ECS.leftToComponent("data_stream.dataset", "keyword")).isFalse();
        assertThat(ECS.leftToComponent("StreamId", "id")).isFalse();
        assertThat(ECS.leftToComponent("message", "text")).isFalse();
        assertThat(ECS.leftToComponent("error.message", "text")).isFalse();
    }

    @Test
    @SuppressWarnings("unchecked")
    void documentsAreCheckedAgainstWhatTheComponentMaps() {
        final FieldPlan plan = FieldPlan.of("elasticsearch", "ecs-acme-v1", "@timestamp", List.of(
                PlannedField.of("StreamId", "id", "@StreamId"),
                PlannedField.of("source.ip", "ip", "EventSource/Client/IPAddress"))).withConvention("ecs", List.of());
        final Map<String, Object> body = body(plan.elasticTemplate("ecs-acme-v1", ECS));
        final Map<String, Object> good = TemplateCheck.compare(body, sourceIp("10.0.0.1"), "ecs-acme-v1", ECS);
        assertThat(good.get("compatible")).isEqualTo(true);
        assertThat(good.get("notes")).isEqualTo(List.of());
        assertThat(good.get("pipeline_changes")).isEqualTo(List.of());
        final Map<String, Object> bad = TemplateCheck.compare(body, sourceIp("ws01"), "ecs-acme-v1", ECS);
        assertThat(bad.get("compatible")).isEqualTo(false);
        assertThat((List<Object>) bad.get("blocking")).contains("source.ip is 'ws01', not an IP address");
    }

    private static List<Map<String, Object>> sourceIp(final String ip) {
        return TemplateCheck.jsonXmlDocuments("<array xmlns=\"http://www.w3.org/2005/xpath-functions\"><map>"
                                              + "<number key=\"StreamId\">7</number><string key=\"@timestamp\">"
                                              + "2026-10-11T08:00:00.000Z</string><map key=\"source\"><string "
                                              + "key=\"ip\">" + ip + "</string></map></map></array>");
    }

    @Test
    @SuppressWarnings("unchecked")
    void anExampleComposingEcsMappingsLeavesEcsFieldsToIt() {
        final FieldPlan plan = FieldPlan.of("elasticsearch", "ecs-acme-v2", "@timestamp", List.of(
                PlannedField.of("StreamId", "id", "@StreamId"),
                PlannedField.of("source.ip", "ip", "EventSource/Client/IPAddress"),
                PlannedField.of("host.name", "keyword", "EventSource/Device/HostName")))
                .withConvention("ecs", List.of());
        final Map<String, Object> planned = body(plan.elasticTemplate("ecs-acme-v2", null, false, ECS));
        final Map<String, Object> example = IndexingFixtures.json("""
                {"index_patterns": ["ecs-acme-v1*"], "composed_of": ["ecs@mappings"],
                 "template": {"mappings": {"properties": {"host": {"properties": {"name": {"type": "keyword",
                  "ignore_above": 256}}}}}}}""");
        final Built built = fromExample(planned, example, false, List.of(), false);
        final Map<String, Object> properties = (Map<String, Object>) TemplateMappings.mappingsOf(built.body())
                .get("properties");
        // source.ip to the component; host.name as the example maps it; nothing said about ecs@mappings not being
        // given.
        assertThat(properties).doesNotContainKey("source");
        assertThat(TemplateMappings.readMapping(TemplateMappings.mappingsOf(built.body())).fields().get("host.name"))
                .containsEntry("ignore_above", 256L);
        assertThat(TemplateMappings.mappingsOf(built.body()).get("dynamic")).isEqualTo(true);
        assertThat(built.notes()).noneMatch(n -> n.contains("not given"))
                .anyMatch(n -> n.contains("left to the component templates (they map them): ['source.ip']"));
        // An example without it: every field mapped, as before.
        final Map<String, Object> without = new LinkedHashMap<>(example);
        without.put("composed_of", List.of());
        final Built plain = fromExample(planned, without, false, List.of(), false);
        assertThat(TemplateMappings.readMappingFields(plain.body()).get("source.ip")).isEqualTo(Map.of("type", "ip"));
    }

    @Test
    void anEcsPlanComposesEcsMappingsWhateverTheExampleComposes() {
        // A regression seen in testing: built from a sibling's index mapping (which never shows composed_of, and had
        // dynamic false), an ECS plan's template mapped every field explicitly, dynamic false, without ecs@mappings.
        final FieldPlan plan = FieldPlan.of("elasticsearch", "ecs-acme-v2", "@timestamp", List.of(
                PlannedField.of("StreamId", "id", "@StreamId"),
                PlannedField.of("source.ip", "ip", "EventSource/Client/IPAddress"),
                PlannedField.of("host.name", "keyword", "EventSource/Device/HostName"),
                PlannedField.of("acme.ticket", "keyword", "EventDetail/TypeId"))).withConvention("ecs", List.of());
        final Map<String, Object> planned = body(plan.elasticTemplate("ecs-acme-v2", null, false, ECS));
        for (final String dynamic : List.of("\"false\"", "\"strict\"", "false")) {
            final Map<String, Object> mapping = IndexingFixtures.json("{\"template\": {\"mappings\": {\"dynamic\": "
                    + dynamic + ", \"properties\": {\"StreamId\": {\"type\": \"long\"}, \"host\": {\"properties\": "
                    + "{\"name\": {\"type\": \"keyword\", \"ignore_above\": 256}}}}}}}");
            final Built built = fromExample(planned, mapping, false, List.of(), true);
            final Map<String, Object> mappings = TemplateMappings.mappingsOf(built.body());
            assertThat(built.body().get("composed_of")).isEqualTo(List.of("ecs@mappings"));
            assertThat(mappings.get("dynamic")).isEqualTo(true);
            // source.ip left to the component; host.name kept as the example maps it; the user's own field mapped
            // here.
            final Map<String, Map<String, Object>> fields = TemplateMappings.readMapping(mappings).fields();
            assertThat(fields).doesNotContainKey("source.ip").containsKey("acme.ticket");
            assertThat(fields.get("host.name")).isEqualTo(Map.of("type", "keyword", "ignore_above", 256L));
            assertThat(built.notes()).anyMatch(n -> n.contains("composed of ecs@mappings"))
                    .anyMatch(n -> n.contains("dynamic mapping on"));
        }
        // First, ahead of the example's own components, which override it.
        final Built first = ExampleTemplates.fromExample(planned, IndexingFixtures.json(
                        "{\"composed_of\": [\"org-base\"], \"template\": {\"mappings\": {}}}"),
                Map.of("org-base", IndexingFixtures.json("{\"template\": {\"settings\": {\"number_of_shards\": 1}}}")),
                false, List.of(), List.of(), true, ECS);
        assertThat(first.body().get("composed_of")).isEqualTo(List.of("ecs@mappings", "org-base"));
        // Standing instructions excluding it win; a plan not following ECS is untouched.
        final Map<String, Object> off = IndexingFixtures.json("{\"template\": {\"mappings\": {\"dynamic\": false}}}");
        final Built excluded = ExampleTemplates.fromExample(planned, off, Map.of(), false, List.of(),
                List.of("ecs@mappings"), true, ECS);
        assertThat(excluded.body()).doesNotContainKey("composed_of");
        assertThat(TemplateMappings.mappingsOf(excluded.body()).get("dynamic")).isEqualTo(false);
        final Built plain = fromExample(planned, off, false, List.of(), false);
        assertThat(plain.body()).doesNotContainKey("composed_of");
        assertThat(TemplateMappings.mappingsOf(plain.body()).get("dynamic")).isEqualTo(false);
    }

    @Test
    void anEcsComponentTemplateElasticsearchLacksIsRefused() {
        // Seen in testing: a proposed template composed ecs@settings, which Elasticsearch doesn't have.
        assertThatThrownBy(() -> IndexingHelpers.componentsWanted(
                Map.of("composed_of", List.of("ecs@mappings", "ecs@settings")), Map.of(), List.of("ecs@settings")))
                .isInstanceOf(ToolRefusal.class)
                .hasMessageContaining("ecs@mappings is its only ECS one");
        // Given, it exists on the user's cluster.
        assertThat(IndexingHelpers.componentsWanted(Map.of("composed_of", List.of("ecs@settings")),
                Map.of("ecs@settings", Map.of()), List.of())).isNull();
    }

    @Test
    @SuppressWarnings("unchecked")
    void dynamicOffStopsTheComponentAndSaysSo() {
        final FieldPlan plan = FieldPlan.of("elasticsearch", "ecs-acme-v1", "@timestamp", List.of(
                PlannedField.of("source.ip", "ip", "EventSource/Client/IPAddress"))).withConvention("ecs", List.of());
        final Map<String, Object> body = body(plan.elasticTemplate("ecs-acme-v1", ECS));
        TemplateMappings.mappingsOf(body).put("dynamic", "strict");
        final List<Map<String, Object>> docs = TemplateCheck.jsonXmlDocuments(
                "<array xmlns=\"http://www.w3.org/2005/xpath-functions\"><map><string key=\"@timestamp\">"
                + "2026-10-11T08:00:00.000Z</string><map key=\"source\"><string key=\"ip\">10.0.0.1</string></map>"
                + "</map></array>");
        final Map<String, Object> said = TemplateCheck.compare(body, docs, "ecs-acme-v1", ECS);
        assertThat(said.get("blocking")).isEqualTo(List.of("source.ip: an ECS field left to ecs@mappings, but "
                                                           + "dynamic is strict, so it isn't mapped and documents are "
                                                           + "rejected"));
        assertThat((String) ((List<Map<String, Object>>) said.get("pipeline_changes")).getFirst().get("change"))
                .contains("dynamic back to true");
    }

    @Test
    @SuppressWarnings("unchecked")
    void aFieldStroomCantListIsSaidWhenATemplateMapsItSo() {
        // Seen in production: Stroom leaves match_only_text (message, under ecs@mappings) out of an Elastic Index
        // doc's fields, so no search on it found anything.
        final List<Map<String, Object>> docs = TemplateCheck.jsonXmlDocuments(
                "<array xmlns=\"http://www.w3.org/2005/xpath-functions\"><map><number key=\"StreamId\">7</number>"
                + "<string key=\"message\">Connection Failed</string></map></array>");
        final Map<String, Object> body = IndexingFixtures.json("""
                {"index_patterns": ["ecs-acme-v1*"], "composed_of": ["ecs@mappings"],
                 "template": {"mappings": {"dynamic": true, "properties": {"StreamId": {"type": "long"}}}}}""");
        final Map<String, Object> left = TemplateCheck.compare(body, docs, "ecs-acme-v1", ECS);
        assertThat(left.get("compatible")).isEqualTo(true);
        assertThat((List<String>) left.get("notes")).anyMatch(n -> n.contains("message: mapped as match_only_text"));
        assertThat(((List<Map<String, Object>>) left.get("pipeline_changes")).stream()
                .filter(c -> "message".equals(c.get("field"))).findFirst().orElseThrow().get("change").toString())
                .startsWith("map 'message' in the template as text");
        ((Map<String, Object>) TemplateMappings.mappingsOf(body).get("properties"))
                .put("message", new LinkedHashMap<>(Map.of("type", "text")));
        assertThat(TemplateCheck.compare(body, docs, "ecs-acme-v1", ECS).get("notes")).isEqualTo(List.of());
    }

    @Test
    void anEarlierTemplatesEcsFieldsMappedAsTheComponentDoesAreLeftToIt() {
        // Asked for by the user: an amended indexing pipeline's template, built from v1's, mapped the ECS base
        // fields again, explicitly. Mapped just as ecs@mappings does, they are left to it; mapped otherwise, they
        // are kept.
        final FieldPlan plan = FieldPlan.of("elasticsearch", "ecs-acme-v2", "@timestamp", List.of(
                PlannedField.of("source.ip", "ip", "EventSource/Client/IPAddress"),
                PlannedField.of("host.name", "keyword", "EventSource/Device/HostName"),
                PlannedField.of("user.name", "keyword", "EventSource/User/Id"))).withConvention("ecs", List.of());
        final Map<String, Object> planned = body(plan.elasticTemplate("ecs-acme-v2", null, false, ECS));
        final Map<String, Object> v1 = IndexingFixtures.json("""
                {"index_patterns": ["ecs-acme-v1*"], "composed_of": ["ecs@mappings"], "template": {"mappings":
                 {"properties": {"source": {"properties": {"ip": {"type": "ip"}}},
                  "host": {"properties": {"name": {"type": "keyword", "ignore_above": 1024}}},
                  "user": {"properties": {"name": {"type": "keyword", "ignore_above": 256}}}}}}}""");
        final Built built = fromExample(planned, v1, false, List.of(), false);
        final Map<String, Map<String, Object>> fields = TemplateMappings.readMappingFields(built.body());
        // As ecs@mappings maps them; and an override.
        assertThat(fields).doesNotContainKeys("source", "host");
        assertThat(fields.get("user.name")).isEqualTo(Map.of("type", "keyword", "ignore_above", 256L));
        assertThat(built.body().get("composed_of")).isEqualTo(List.of("ecs@mappings"));
    }

    @Test
    void ecsMappingsNominatedLeavesTheEcsFieldsToIt() {
        // A regression seen in testing: an ECS index template didn't compose ecs@mappings; asked to, the agent's
        // next one composed it but still mapped every ECS field explicitly, with dynamic false, so the component
        // mapped nothing.
        final FieldPlan plan = FieldPlan.of("elasticsearch", "ecs-acme-v2", "@timestamp", List.of(
                PlannedField.of("StreamId", "id", "@StreamId"),
                PlannedField.of("source.ip", "ip", "EventSource/Client/IPAddress"),
                PlannedField.of("host.name", "keyword", "EventSource/Device/HostName"),
                PlannedField.of("acme.ticket", "keyword", "EventDetail/TypeId")));
        final Map<String, Object> planned = body(plan.elasticTemplate("ecs-acme-v2", null, false, ECS));
        // From an example mapping everything, dynamic false, with ecs@mappings nominated (no ECS convention on the
        // plan).
        final Map<String, Object> example = IndexingFixtures.json("""
                {"index_patterns": ["ecs-acme-v1*"], "composed_of": ["org-base"], "template": {"mappings": {
                 "dynamic": false, "properties": {"StreamId": {"type": "long"}, "source": {"properties": {"ip":
                 {"type": "ip"}}}, "host": {"properties": {"name": {"type": "keyword"}}}}}}}""");
        final Built built = ExampleTemplates.fromExample(planned, example, Map.of("org-base", IndexingFixtures.json(
                        "{\"template\": {\"settings\": {\"number_of_shards\": 1}}}")), false, List.of("ecs@mappings"),
                List.of(), false, ECS);
        final Map<String, Object> mappings = TemplateMappings.mappingsOf(built.body());
        assertThat(built.body().get("composed_of")).isEqualTo(List.of("ecs@mappings", "org-base"));
        assertThat(mappings.get("dynamic")).isEqualTo(true);
        assertThat(JsonValues.map(mappings.get("properties"))).containsOnlyKeys("StreamId", "acme");
        assertThat(ExampleTemplates.ecsDuplicates(built.body(), ECS)).isEmpty();
        // From the plan alone, ecs@mappings nominated: the same.
        final Built alone = ExampleTemplates.withComponents(planned, Map.of(), List.of("ecs@mappings"), List.of(),
                ECS);
        assertThat(alone.body().get("composed_of")).isEqualTo(List.of("ecs@mappings"));
        assertThat(TemplateMappings.mappingsOf(alone.body()).get("dynamic")).isEqualTo(true);
        assertThat(JsonValues.map(TemplateMappings.mappingsOf(alone.body()).get("properties")))
                .containsOnlyKeys("StreamId", "acme");
        assertThat(alone.notes()).anyMatch(n -> n.contains("['host.name', 'source.ip']"));
        // A template that composes it and maps them anyway is said to map them twice.
        final Map<String, Object> twice = new LinkedHashMap<>(planned);
        twice.put("composed_of", List.of("ecs@mappings"));
        assertThat(ExampleTemplates.ecsDuplicates(twice, ECS)).containsExactly("host.name", "source.ip");
    }

    @Test
    void aPlanGivenWithoutItsConventionStillComposesEcsMappings() {
        // The agent's copy of the plan left out convention='ecs': the template was built as if it followed none.
        final List<PlannedField> fields = List.of(PlannedField.of("StreamId", "id", "@StreamId"),
                PlannedField.of("source.ip", "ip", "EventSource/Client/IPAddress"));
        final FieldPlan drafted = FieldPlan.of("elasticsearch", "ecs-acme-v2", "@timestamp", fields)
                .withConvention("ecs", List.of("acme"));
        final FieldPlan given = drafted.withConvention(null, List.of());
        final FieldPlan plan = IndexingHelpers.withConvention(given, drafted);
        assertThat(plan.convention()).isEqualTo("ecs");
        assertThat(plan.customNamespaces()).containsExactly("acme");
        assertThat(body(plan.elasticTemplate("x", ECS)).get("composed_of")).isEqualTo(List.of("ecs@mappings"));
    }

    private static Built fromExample(final Map<String, Object> planned,
                                     final Map<String, Object> example,
                                     final boolean discovery,
                                     final List<String> include,
                                     final boolean ecsBase) {
        return ExampleTemplates.fromExample(planned, example, Map.of(), discovery, include, List.of(), ecsBase, ECS);
    }

    private static Map<String, Object> body(final Map<String, Object> template) {
        return JsonValues.map(template.get("body"));
    }
}
