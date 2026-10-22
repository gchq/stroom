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

package stroom.mcp.impl.tools;

import stroom.ai.api.ElasticIndexTemplates.ReadException;
import stroom.ai.api.ElasticIndexTemplates.Reason;
import stroom.ai.impl.tools.KeptMapping;
import stroom.ai.impl.tools.StreamReader.RawHead;
import stroom.ai.impl.tools.ToolCallContext;
import stroom.ai.impl.tools.ToolResult;
import stroom.ai.impl.tools.indexing.FieldPlan;
import stroom.ai.impl.tools.indexing.PlannedField;
import stroom.ai.impl.tools.sample.PyJson;
import stroom.ai.impl.tools.validation.EventSchema;
import stroom.pipeline.shared.XsltDoc;
import stroom.search.elastic.shared.ElasticIndexDoc;
import stroom.search.elastic.shared.ElasticIndexField;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;

/// draft_index_mapping, as the Stroom MCP server's tests/test_indexing.py, test_index_names.py and
/// test_replaced_index.py test it.
class TestDraftIndexMappingTool {

    // The user's example in a VS Code run: a Twitter index's template, nesting some names (User.Id) and not others.
    private static final String EXAMPLE = "PUT _index_template/stroom_twitter\n" + PyJson.dumps(Map.of(
            "index_patterns", List.of("stroom-twitter*"), "priority", 1L, "template", Map.of("mappings", Map.of(
                    "properties", ordered(
                            "StreamId", Map.of("type", "long"), "EventId", Map.of("type", "long"),
                            "@timestamp", Map.of("type", "date"), "Active", Map.of("type", "boolean"),
                            "IpAddress", Map.of("type", "ip"), "Text", Map.of("type", "text"),
                            "Id", Map.of("type", "long"),
                            "User", Map.of("properties", ordered("Id", Map.of("type", "keyword"),
                                    "Name", Map.of("type", "keyword"), "Location", Map.of("type", "keyword"))))))));
    // The paths the firewall's Events populated, % of events.
    private static final Map<String, Integer> POPULATED = ordered(
            "EventTime/TimeCreated", 100, "EventSource/System/Name", 100, "EventSource/Device/HostName", 100,
            "EventDetail/TypeId", 100, "EventDetail/Description", 100,
            "EventDetail/Network/Permit/Source/Device/IPAddress", 35, "EventDetail/Network/Permit/Source/Port", 35,
            "EventDetail/Network/Deny/Source/Device/IPAddress", 35, "EventDetail/Network/Deny/Source/Port", 35,
            "EventDetail/Network/Deny/Destination/Device/IPAddress", 35, "EventDetail/Network/Permit/Data", 35,
            "EventDetail/Alert/Type", 15, "EventDetail/Alert/Severity", 15,
            "EventDetail/Authenticate/Action", 15, "EventDetail/Authenticate/User/Id", 15,
            "EventDetail/Authenticate/Outcome/Success", 10, "EventDetail/Process/Action", 5,
            "EventDetail/Process/Type", 5, "EventDetail/Process/Command", 5,
            "EventDetail/Update/After/Configuration/Type", 10);
    private static final String TWO_TIMES = "{\"ts\": \"2026-10-11T08:00:00Z\", \"received\": "
                                            + "\"2026-10-11T08:00:05Z\", \"user\": \"a\"}\n{\"ts\": "
                                            + "\"2026-10-11T08:01:00Z\", \"received\": "
                                            + "\"2026-10-11T08:01:05Z\", \"user\": \"b\"}\n";

    private final IndexingWorld world = new IndexingWorld();

    @BeforeEach
    void events() {
        world.processing.stream(1, "FW", "Events", null, null);
        world.processing.stream(7, "RAW", "Raw Events", null, null);
        world.processing.stream(9, "FW", "Events", null, null);
        world.populated(POPULATED, Map.of());
    }

    // --------------------------------------------------------------------------------
    // test_index_names.py
    // --------------------------------------------------------------------------------

    @Test
    void whatHappenedIsPlannedAndNestedWhereFieldsBelongTogether() {
        // Seen: the plan left out Alert, Authenticate, Process and Update; the user asked for Alert.Type and
        // Alert.Severity, then "the same wherever we establish nested structures"; the agent named them AuthAction,
        // then Auth.Action.
        final Map<String, Object> result = draft(Map.of("example_template", EXAMPLE), IndexingWorld.noForms());
        final Map<String, String> names = names(result);
        assertThat(names).containsEntry("EventDetail/Alert/Type", "Alert.Type")
                .containsEntry("EventDetail/Alert/Severity", "Alert.Severity")
                .containsEntry("EventDetail/Authenticate/Action", "Authenticate.Action")
                .containsEntry("EventDetail/Authenticate/Outcome/Success", "Authenticate.Outcome.Success")
                .containsEntry("EventDetail/Process/Command", "Process.Command")
                .containsEntry("EventDetail/Network/*/Source/Device/IPAddress", "Source.IPAddress")
                .containsEntry("EventDetail/Network/*/Source/Port", "Source.Port")
                // Alone in its group: flat, as the example's own single names are.
                .containsEntry("EventDetail/Update/After/Configuration/Type", "ConfigurationType")
                .containsEntry("EventSource/Device/HostName", "HostName")
                // The example's own name for the user.
                .containsEntry("EventDetail/Authenticate/User/Id", "User.Id");
        assertThat(names.keySet()).noneMatch(source -> source.contains("/Data"));
        assertThat(field(result, "Authenticate.Outcome.Success").get("type")).isEqualTo("boolean");
    }

    @Test
    void withoutAnExampleTheProfilesStyleNamesThem() {
        final Map<String, String> flat = names(draft(Map.of("convention", "stroom-flat", "without_example", true),
                agreeing()));
        final Map<String, String> nested = names(draft(Map.of("convention", "ecs", "without_example", true),
                agreeing()));
        assertThat(flat).containsEntry("EventDetail/Alert/Type", "AlertType")
                .containsEntry("EventDetail/Network/*/Source/Port", "SourcePort");
        // ECS: an action's own element under the source's namespace (named after the index), the profile's names as
        // ECS's.
        assertThat(nested).containsEntry("EventDetail/Alert/Type", "firewall_edge.alert.type")
                .containsEntry("EventDetail/Network/*/Source/Port", "source.port");
    }

    @Test
    @SuppressWarnings("unchecked")
    void followingEcsWhatHappenedAndDataElementsAreNamedAsEcsNamesThem() {
        // Asked by the user: the full ECS schema, not only the profile's few names.
        world.populated(POPULATED, Map.of("EventDetail/Network/Permit/Data", Map.of("user_agent_original", 35.0,
                "session_id", 35.0)));
        final Map<String, Object> result = draft("ecs-firewall-v1", Map.of("convention", "ecs", "without_example",
                true), agreeing());
        assertThat(field(result, "event.action").get("source")).isEqualTo("EventDetail/*/Action");
        assertThat(field(result, "event.outcome").get("transform")).isEqualTo("outcome");
        assertThat(field(result, "process.command_line").get("type")).isEqualTo("keyword");
        final List<String> names = fieldNames(result);
        assertThat(names).doesNotContain("authenticate.action", "authenticate.outcome.success", "process.action",
                "process.type", "alert.type");
        // An action's own elements go under the source's namespace, as ECS asks.
        assertThat(field(result, "firewall.process.type").get("source")).isEqualTo("EventDetail/Process/Type");
        assertThat(names).contains("firewall.alert.type");
        assertThat(((Map<String, Object>) result.get("plan")).get("custom_namespaces")).isEqualTo(List.of("firewall"));
        assertThat(field(result, "user_agent.original").get("source"))
                .isEqualTo("EventDetail/Network/Permit/Data[@Name='user_agent_original']/@Value");
        assertThat(names).noneMatch(name -> name.contains("session"));
        assertThat((List<String>) result.get("ecs_data_elements")).isNotEmpty();
        assertThat(result).doesNotContainKey("ecs_check");
        assertThat(((Map<String, Object>) result.get("plan")).get("convention")).isEqualTo("ecs");
        assertThat(((Map<String, Object>) ((Map<String, Object>) result.get("rendered")).get("body"))
                .get("composed_of")).isEqualTo(List.of("ecs@mappings"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void anExampleTemplateDeclaresItsOwnNamesNotReportedAsUnEcs() {
        final Map<String, Object> result = draft(Map.of("example_template", EXAMPLE), IndexingWorld.noForms());
        assertThat(result).doesNotContainKey("ecs_check");
        assertThat((List<String>) ((Map<String, Object>) result.get("plan")).get("custom_namespaces"))
                .contains("Alert", "Authenticate", "User");
    }

    @Test
    @SuppressWarnings("unchecked")
    void aPlanIsAmendedWithOnlyWhatChangesAndTheToolsTakeItFromTheSession() {
        // Seen in Ask Stroom AI: past the client's step cap the agent had lost its earlier extra_fields and spent
        // eight calls recovering them, and the 50-field plan went inline five times in all.
        final Map<String, Object> extra = ordered("name", "firewall.configuration", "type", "keyword", "source",
                "EventDetail//Configuration/Type");
        final Map<String, Object> drafted = draft("ecs-firewall-v1", Map.of("convention", "ecs", "without_example",
                true, "extra_fields", List.of(extra)), agreeing());
        // A // source covers the paths below it: not reported as unmapped.
        assertThat((List<String>) drafted.get("populated_paths_not_mapped"))
                .noneMatch(path -> path.contains("Configuration"));
        Mockito.clearInvocations(world.summaries);
        final Map<String, Object> changes = Map.of("fields", List.of(
                ordered("name", "firewall.alert.type", "remove", true),
                ordered("name", "firewall.configuration", "type", "keyword", "source",
                        "EventDetail/Update/After/Configuration/Type"),
                ordered("name", "acme.ticket", "type", "keyword", "source",
                        "EventDetail/Alert/Data[@Name='Ticket']/@Value")));
        final Map<String, Object> amended = call(Map.of("backend", "elasticsearch", "index_name", "ecs-firewall-v1",
                "changes", changes), IndexingWorld.noForms());
        final ToolResult other = world.draftTool().execute(PortedToolCalls.call(DraftIndexMappingTool.NAME,
                PyJson.dumps(Map.of("backend", "elasticsearch", "index_name", "ecs-other-v1", "changes",
                        Map.of("drop_when", List.of())))), IndexingWorld.noForms());
        assertThat(other.text()).contains("No plan for 'ecs-other-v1'")
                .contains("Drafted in this session: ['ecs-firewall-v1']");
        assertThat(amended.get("changed")).isEqualTo(List.of("field 'firewall.alert.type' removed",
                "field 'firewall.configuration' replaced", "field 'acme.ticket' added"));
        // No streams given: not read.
        Mockito.verify(world.summaries, Mockito.never()).summariseEvents(anyList(), anyInt());
        assertThat(amended).doesNotContainKey("populated_paths_not_mapped");
        final List<String> names = fieldNames(amended);
        assertThat(names).doesNotContain("firewall.alert.type").contains("firewall.configuration");
        assertThat(names.getLast()).isEqualTo("acme.ticket");
        assertThat(names).hasSize(fieldNames(drafted).size());
        assertThat(((List<String>) amended.get("ecs_check")).stream().map(p -> p.split(" ")[0]).toList())
                .containsExactly("acme.*");
        // The tools take the plan from the session.
        assertThat(world.plans.given(null, List.of("ecs-firewall-v1"), null).fields().getLast().name())
                .isEqualTo("acme.ticket");
        assertThat(world.plans.given(null, List.of("ecs-elsewhere-v1"), null)).isNull();
    }

    // --------------------------------------------------------------------------------
    // test_indexing.py
    // --------------------------------------------------------------------------------

    @Test
    @SuppressWarnings("unchecked")
    void aDiscoveryDraftReadsNothingAndIsElasticsearchOnly() {
        final Map<String, Object> draft = call(Map.of("backend", "elasticsearch", "index_name",
                "stroom-discovery-acme-v1", "discovery", Map.of("timestamp_field", "ts")), IndexingWorld.noForms());
        assertThat(((Map<String, Object>) ((Map<String, Object>) draft.get("plan")).get("discovery"))
                .get("timestamp_field")).isEqualTo("ts");
        assertThat(String.valueOf(draft.get("xslt"))).contains("json-to-xml");
        Mockito.verify(world.summaries, Mockito.never()).summariseEvents(anyList(), anyInt());
        assertThat(refused(Map.of("backend", "lucene", "index_name", "x", "discovery", Map.of("timestamp_field",
                "ts")))).startsWith("A discovery index is Elasticsearch");
        assertThat(refused(Map.of("backend", "lucene", "index_name", "x", "convention", "ecs")))
                .startsWith("Give events_stream_ids");
    }

    @Test
    @SuppressWarnings("unchecked")
    void anElasticsearchIndexIsDraftedFromTheUsersExampleOrTheirConfirmedChoice() {
        // A convention alone: not drafted; the user is to be offered their example first.
        final Map<String, Object> asked = draft("x", Map.of("convention", "stroom-flat"), IndexingWorld.noForms());
        assertThat(asked.get("drafted")).isEqualTo(false);
        assertThat(String.valueOf(((List<Map<String, Object>>) asked.get("options")).getFirst().get("option")))
                .startsWith("example index template");
        // Without an example only when the user confirms it.
        final Map<String, Object> gate = draft("x", Map.of("convention", "stroom-flat", "without_example", true),
                IndexingWorld.noForms());
        assertThat(gate.get("status")).isEqualTo("needs_confirmation");
        assertThat(String.valueOf(gate.get("summary"))).contains("without an example index template");
    }

    @Test
    @SuppressWarnings("unchecked")
    void followingAnExistingIndexIsTheUsersChoiceConfirmedInAForm() {
        // Seen in VS Code: the user chose to paste their own template, and the agent drafted from FortiOS-V1 anyway.
        world.elasticIndex("a96e", "FortiOS-V1", "ecs-fortios-v1", null, false);
        world.world.docs.put("a96e", ((ElasticIndexDoc) world.world.docs.get("a96e")).copy().fields(List.of(
                ElasticIndexField.builder().fldName("source.ip").nativeType("ip").build(),
                ElasticIndexField.builder().fldName("rule.id").nativeType("keyword").build())).build());
        Mockito.when(world.templates.read(any())).thenThrow(new ReadException(Reason.NOT_FOUND, "gone"));
        final Map<String, Object> gate = draft("fortinet-firewall-v1", Map.of("like_index", "a96e"),
                IndexingWorld.noForms());
        // The fields are read first, so the user sees what they'd get; nothing is drafted before they agree.
        assertThat(gate.get("status")).isEqualTo("needs_confirmation");
        Mockito.verify(world.summaries, Mockito.never()).summariseEvents(anyList(), anyInt());
        assertThat(gate.get("summary"))
                .isEqualTo("Name the fields of index 'fortinet-firewall-v1' after an existing index in Stroom");
        final Map<String, Object> details = (Map<String, Object>) gate.get("details");
        assertThat(details.get("follow")).isEqualTo("Elastic Index doc 'FortiOS-V1' (index ecs-fortios-v1)");
        assertThat(String.valueOf(details.get("read through Stroom"))).startsWith("2 fields and their types")
                .contains("source.ip (ip)");
        // What Stroom can't give: the template itself, which the user may still paste.
        assertThat(String.valueOf(details.get("not readable through Stroom"))).contains("component templates")
                .contains("paste its index template");
    }

    @Test
    @SuppressWarnings("unchecked")
    void severalTimeFieldsArePutToTheUserAndTheChoiceKept() {
        // Asked for by the user: when the records hold times in more than one field, the user picks @timestamp.
        rawHead(TWO_TIMES);
        final ToolCallContext forms = world.forms(question -> Map.of("choice", "received"));
        final Map<String, Object> result = call(discovery(Map.of("timestamp_field", "ts")), forms);
        assertThat(timestampField(result)).isEqualTo("received");
        assertThat(world.asked).hasSize(1);
        assertThat(world.asked.getFirst()).contains("@timestamp");
        // Kept for the session: drafting again doesn't ask again, whatever the agent proposes.
        final Map<String, Object> again = call(discovery(Map.of("timestamp_field", "ts")), forms);
        assertThat(world.asked).hasSize(1);
        assertThat(timestampField(again)).isEqualTo("received");
    }

    @Test
    @SuppressWarnings("unchecked")
    void withoutFormsTheCandidatesAreListedAndTheUsersAnswerGoesAhead() {
        rawHead(TWO_TIMES);
        final Map<String, Object> listed = call(discovery(Map.of("timestamp_field", "when")),
                IndexingWorld.noForms());
        assertThat(listed.get("status")).isEqualTo("needs_guidance");
        assertThat(listed.get("timestamp_candidates")).isEqualTo(List.of("ts", "received"));
        assertThat(String.valueOf(listed.get("hint"))).contains("'when' isn't one of them")
                .contains("timestamp_chosen_by_user=true");
        final Map<String, Object> answered = call(discovery(Map.of("timestamp_field", "received",
                "timestamp_chosen_by_user", true)), IndexingWorld.noForms());
        assertThat(timestampField(answered)).isEqualTo("received");
    }

    @Test
    void aRecordsStreamIsReadByDataNameAndOnlyAsDelimitedInput() {
        // Seen (records_output eval, Haiku): a Records stream profiled as XML offered only 'data@value' as its
        // time, 'last_seen' was refused, and the agent indexed each record as lists of names and values.
        rawHead("<?xml version=\"1.1\" encoding=\"UTF-8\"?>\n<records xmlns=\"records:2\" version=\"2.0\">"
                + "<record><data name=\"hostname\" value=\"ws-0141\"/><data name=\"last_seen\" "
                + "value=\"2026-10-01T08:00:00Z\"/></record><record><data name=\"hostname\" value=\"ws-0142\"/>"
                + "<data name=\"last_seen\" value=\"2026-10-01T08:05:00Z\"/></record></records>\n");
        final Map<String, Object> result = call(discovery(Map.of("input", "delimited", "timestamp_field",
                "last_seen")), world.forms(question -> Map.of("choice", "x")));
        assertThat(world.asked).isEmpty();
        assertThat(timestampField(result)).isEqualTo("last_seen");
        assertThat(refused(discovery(Map.of("input", "xml", "record", "record", "timestamp_field", "last_seen"))))
                .contains("input': 'delimited'");
    }

    @Test
    void oneTimeFieldAsProposedIsNotAskedAbout() {
        rawHead("{\"ts\": \"2026-10-11T08:00:00Z\", \"user\": \"a\"}\n{\"ts\": \"2026-10-11T08:01:00Z\", \"user\": "
                + "\"b\"}\n");
        final Map<String, Object> result = call(discovery(Map.of("timestamp_field", "ts")),
                world.forms(question -> Map.of("choice", "x")));
        assertThat(world.asked).isEmpty();
        assertThat(timestampField(result)).isEqualTo("ts");
    }

    @Test
    void theStreamsMustBeEvents() {
        assertThat(refused(Map.of("backend", "elasticsearch", "index_name", "x", "events_stream_ids", List.of(7),
                "example_template", EXAMPLE)))
                .startsWith("Stream 7 is 'Raw Events', not Events: the source's own data");
    }

    @Test
    void aPlanIsMarkedFromTheSchema() throws IOException {
        final EventSchema schema;
        try (InputStream xsd = getClass().getResourceAsStream("/stroom/mcp/impl/tools/event-logging-v4.1.0.xsd")) {
            schema = EventSchema.parse(new String(xsd.readAllBytes(), StandardCharsets.UTF_8));
        }
        Mockito.when(world.heldSchemas.chosenOrLatest(Mockito.any())).thenReturn("4.1.0");
        Mockito.when(world.heldSchemas.eventSchema(anyString())).thenReturn(schema);
        final FieldPlan plan = FieldPlan.of("elasticsearch", "x", "@timestamp", List.of(
                PlannedField.of("StreamId", "id", "@StreamId"),
                PlannedField.of("user.name", "keyword", "EventSource/User/Id"),
                PlannedField.of("acme.port", "long", "EventDetail/Authenticate/Data[@Name='port']/@Value")));
        assertThat(world.plans.withCardinality(plan).fields().stream().map(PlannedField::multiple).toList())
                .containsExactly(false, false, true);
        Mockito.when(world.heldSchemas.eventSchema(anyString())).thenThrow(new RuntimeException("no schema"));
        // Unread: as before.
        assertThat(world.plans.withCardinality(plan)).isEqualTo(plan);
    }

    // --------------------------------------------------------------------------------
    // test_replaced_index.py
    // --------------------------------------------------------------------------------

    @Test
    @SuppressWarnings("unchecked")
    void aDraftSaysWhatTheIndexItReplacesWroteThatItDoesnt() {
        // Written by hand, as the FortiOS index was: nested maps, the field names in their keys.
        final String old = """
                <xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" \
                xmlns="http://www.w3.org/2005/xpath-functions" xpath-default-namespace="event-logging:3" \
                version="3.0">
                  <xsl:template match="Event">
                    <map>
                      <number key="StreamId"><xsl:value-of select="@StreamId"/></number>
                      <xsl:variable name="dst" select="EventDetail/Network/*/Destination"/>
                      <map key="Destination">
                        <string key="IPAddress"><xsl:value-of select="$dst/Device/IPAddress"/></string>
                        <map key="Location"><string key="Country"><xsl:value-of \
                select="$dst/Device/Location/Country"/></string></map>
                      </map>
                      <string key="Rule"><xsl:value-of select="EventDetail/Network/*/Rule"/></string>
                    </map>
                  </xsl:template>
                </xsl:stylesheet>""";
        world.world.docs.put("x-old", XsltDoc.builder().uuid("x-old").name("FORTIGATE-Index-V1.0").data(old)
                .description("").version("v0").build());
        final FieldPlan plan = new FieldPlan("elasticsearch", "ecs-fortigate", "@timestamp", List.of(
                PlannedField.of("StreamId", "id", "@StreamId"),
                PlannedField.of("destination.ip", "ip", "EventDetail/Network/*/Destination/Device/IPAddress")),
                List.of(), null, List.of(), List.of(), true, "ecs", List.of(), null);
        final Map<String, Double> populated = Map.of("EventDetail/Network/Deny/Destination/Device/Location/Country",
                40.0);
        Map<String, Object> replaced = world.draftTool().replaced("x-old", plan, populated);
        assertThat(replaced.get("xslt")).isEqualTo("FORTIGATE-Index-V1.0");
        assertThat(((Number) replaced.get("fields")).intValue()).isEqualTo(4);
        assertThat(replaced.get("not_in_draft")).isEqualTo(List.of(
                ordered("field", "Destination.Location.Country", "source",
                        "EventDetail/Network/*/Destination/Device/Location/Country", "populated", "40%"),
                ordered("field", "Rule", "source", "EventDetail/Network/*/Rule", "populated", "not in the sample")));
        assertThat(String.valueOf(replaced.get("hint"))).contains("extra_fields");
        // Kept with a plan, the old XSLT's fields are the plan's.
        final FieldPlan kept = FieldPlan.of("elasticsearch", "old", "@timestamp", List.of(PlannedField.of("Rule",
                "keyword", "EventDetail/Network/*/Rule")));
        world.world.docs.put("x-old", XsltDoc.builder().uuid("x-old").name("Old").data("<x/>").version("v0")
                .description(KeptMapping.withMapping("", "index", kept.toJson())).build());
        replaced = world.draftTool().replaced("x-old", plan, populated);
        assertThat(((List<Map<String, Object>>) replaced.get("not_in_draft")).stream().map(m -> m.get("field"))
                .toList()).containsExactly("Rule");
    }

    // --------------------------------------------------------------------------------

    private Map<String, Object> draft(final Map<String, Object> arguments, final ToolCallContext context) {
        return draft("firewall-edge-v1", arguments, context);
    }

    private Map<String, Object> draft(final String indexName,
                                      final Map<String, Object> arguments,
                                      final ToolCallContext context) {
        final Map<String, Object> all = new LinkedHashMap<>();
        all.put("backend", "elasticsearch");
        all.put("index_name", indexName);
        all.put("events_stream_ids", List.of(1));
        all.putAll(arguments);
        return call(all, context);
    }

    private Map<String, Object> discovery(final Map<String, Object> discovery) {
        final Map<String, Object> all = new LinkedHashMap<>();
        all.put("backend", "elasticsearch");
        all.put("index_name", "stroom-discovery-acme-v1");
        all.put("events_stream_ids", List.of(7));
        all.put("discovery", discovery);
        return all;
    }

    private Map<String, Object> call(final Map<String, Object> arguments, final ToolCallContext context) {
        final ToolResult result = world.draftTool().execute(PortedToolCalls.call(DraftIndexMappingTool.NAME,
                PyJson.dumps(arguments)), context);
        assertThat(result.isError()).as(result.text()).isFalse();
        return PortedToolCalls.json(result.text());
    }

    private String refused(final Map<String, Object> arguments) {
        final ToolResult result = world.draftTool().execute(PortedToolCalls.call(DraftIndexMappingTool.NAME,
                PyJson.dumps(arguments)), IndexingWorld.noForms());
        assertThat(result.isError()).as(result.text()).isTrue();
        return result.text();
    }

    private ToolCallContext agreeing() {
        final Function<String, Map<String, Object>> yes = question -> Map.of("value", true);
        return world.forms(yes);
    }

    private void rawHead(final String text) {
        Mockito.when(world.processing.feeds.streamReader.readHead(anyLong(), anyInt(), anyInt()))
                .thenReturn(new RawHead(text, false, 1));
    }

    @SuppressWarnings("unchecked")
    private static String timestampField(final Map<String, Object> result) {
        return String.valueOf(((Map<String, Object>) ((Map<String, Object>) result.get("plan")).get("discovery"))
                .get("timestamp_field"));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String> names(final Map<String, Object> result) {
        final Map<String, String> names = new LinkedHashMap<>();
        for (final Map<String, Object> field : (List<Map<String, Object>>) ((Map<String, Object>) result.get("plan"))
                .get("fields")) {
            names.put(String.valueOf(field.get("source")), String.valueOf(field.get("name")));
        }
        return names;
    }

    @SuppressWarnings("unchecked")
    private static List<String> fieldNames(final Map<String, Object> result) {
        return ((List<Map<String, Object>>) ((Map<String, Object>) result.get("plan")).get("fields")).stream()
                .map(f -> String.valueOf(f.get("name")))
                .toList();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> field(final Map<String, Object> result, final String name) {
        return ((List<Map<String, Object>>) ((Map<String, Object>) result.get("plan")).get("fields")).stream()
                .filter(f -> name.equals(f.get("name")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No field " + name + " in " + result.get("plan")));
    }

    @SuppressWarnings("unchecked")
    private static <V> Map<String, V> ordered(final Object... pairs) {
        final Map<String, V> map = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put((String) pairs[i], (V) pairs[i + 1]);
        }
        return map;
    }
}
