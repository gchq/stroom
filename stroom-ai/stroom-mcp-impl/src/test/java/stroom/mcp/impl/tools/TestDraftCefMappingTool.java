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

import stroom.ai.impl.tools.AgentInstructions;
import stroom.ai.impl.tools.DocumentAccess;
import stroom.ai.impl.tools.InstructionRules.RequiredImport;
import stroom.ai.impl.tools.KeptMapping;
import stroom.ai.impl.tools.ToolCall;
import stroom.ai.impl.tools.ToolCallContext;
import stroom.ai.impl.tools.ToolResult;
import stroom.ai.impl.tools.cef.CefField;
import stroom.ai.impl.tools.cef.CefOverride;
import stroom.ai.impl.tools.cef.CefPlan;
import stroom.ai.impl.tools.cef.CefValue;
import stroom.ai.impl.tools.validation.EventStreams;
import stroom.ai.impl.tools.validation.XsltCompileCheck;
import stroom.pipeline.shared.SharedElementData;
import stroom.pipeline.shared.stepping.SharedStepData;
import stroom.pipeline.shared.stepping.StepLocation;
import stroom.pipeline.shared.stepping.SteppingResult;
import stroom.security.api.SecurityContext;
import stroom.util.json.JsonUtil;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;

/// The Stroom MCP server's `tests/test_cef.py` tool cases: asking the user about keys outside ArcSight's
/// dictionary, what the standing instructions say, and saving; and the overrides on a plan, a review of a CEF
/// pipeline and a save the user is asked about.
class TestDraftCefMappingTool {

    static final String EVENTS = """
            <Events xmlns="event-logging:3" Version="3.5.2">
            <Event><EventTime><TimeCreated>2026-09-27T23:48:10.000Z</TimeCreated></EventTime>
             <EventSource><System><Name>SecretServer</Name><Environment>Prod</Environment>\
            <Organisation>Delinea</Organisation><Version>11.4</Version></System>
              <Device><HostName>ss-web-02</HostName><IPAddress>10.1.2.3</IPAddress></Device><Client>\
            <IPAddress>192.0.2.45</IPAddress></Client><User><Id>priya.patel</Id></User></EventSource>
             <EventDetail><TypeId>User-Login</TypeId><Description>User logged in</Description><Authenticate>\
            <Action>Logon</Action><User><Id>priya.patel</Id></User><Outcome><Success>false</Success></Outcome>
              <Data Name="session_id" Value="6857"/><Data Name="note" Value="a=b|c\\d&#10;second line"/>\
            </Authenticate></EventDetail></Event>
            <Event><EventTime><TimeCreated>2026-09-27T23:56:15.000Z</TimeCreated></EventTime>
             <EventSource><System><Name>SecretServer</Name><Organisation>Delinea</Organisation>\
            <Version>11.4</Version></System>
              <Device><HostName>ss-web-01</HostName></Device><Client><IPAddress>ss-client.domain.com</IPAddress>\
            </Client><User><Id>riley.chen</Id></User></EventSource>
             <EventDetail><TypeId>Secret-View</TypeId><View><Resource><Type>Secret</Type><Name>Firewall | Edge</Name>\
            <Id>10005</Id></Resource></View></EventDetail></Event>
            <Event><EventTime><TimeCreated>2026-09-28T00:01:06.000Z</TimeCreated></EventTime>
             <EventSource><System><Name>SecretServer</Name><Organisation>Delinea</Organisation></System><Device>\
            <HostName>fw1</HostName></Device></EventSource>
             <EventDetail><TypeId>Net</TypeId><Network><Deny><Source><Device><IPAddress>10.0.0.1</IPAddress></Device>\
            <Port>5555</Port></Source>
              <Destination><Device><IPAddress>10.0.0.2</IPAddress></Device><Port>443</Port></Destination>\
            <TransportProtocol>TCP</TransportProtocol></Deny></Network></EventDetail></Event>
            </Events>""";

    private final SteppingWorld world = new SteppingWorld();
    private final AgentInstructions agentInstructions = Mockito.mock(AgentInstructions.class);
    private final Consent consent = Mockito.mock(Consent.class);
    private final EventStreams eventStreams = Mockito.mock(EventStreams.class);
    private final KeptMappings keptMappings = Mockito.mock(KeptMappings.class);
    private final DocumentAccess documentAccess = Mockito.mock(DocumentAccess.class);
    private final CefSources sources = Mockito.mock(CefSources.class);
    private final XsltSaves saves = Mockito.mock(XsltSaves.class);
    private final BuildPlan plan = Mockito.mock(BuildPlan.class);
    // Compiles everything, unless a test says.
    private final XsltCompileCheck compileCheck = Mockito.mock(XsltCompileCheck.class);
    private final DraftCefMappingTool tool = new DraftCefMappingTool(agentInstructions, consent, eventStreams,
            world.stepping(), keptMappings, documentAccess, sources, saves, plan, compileCheck,
            new CefChoices(Mockito.mock(SecurityContext.class)));
    private List<Map<String, Object>> instructions = List.of();

    @BeforeEach
    void setUp() {
        Mockito.when(compileCheck.compile(anyString())).thenReturn(new XsltCompileCheck.Compiled(List.of()));
        Mockito.when(agentInstructions.targets(anyList(), anyList(), anyList(), anyList())).thenReturn(
                new AgentInstructions.Targets(List.of("/"), List.of()));
        Mockito.when(agentInstructions.applicableInstructions(any(), anyString(), any())).thenAnswer(inv ->
                Map.of("instructions", instructions));
        Mockito.when(consent.choose(any(), any(), anyString(), anyString(), anyList(), any()))
                .thenReturn(Optional.empty());
        Mockito.when(eventStreams.read(anyList(), anyInt())).thenReturn(new EventStreams.Read(EVENTS, Map.of()));
        Mockito.when(sources.templates(any(), anyList())).thenReturn(Map.of());
        Mockito.when(sources.kafkaConfigs()).thenReturn(List.of());
        Mockito.when(keptMappings.keptXslt(anyString())).thenReturn(Optional.empty());
        Mockito.when(saves.requiredFor(any(), anyString(), any(), any())).thenReturn(List.of());
        Mockito.when(plan.buildOf(any())).thenReturn(Optional.empty());
    }

    @Test
    void theUserIsAskedAboutKeysOutsideTheDictionaryUnlessTheInstructionsSay() throws Exception {
        final Map<String, Object> asked = draft("{\"stream_ids\": [7], \"topic\": \"t\"}");
        assertThat(asked.get("status")).isEqualTo("needs_guidance");
        assertThat(asked.get("options")).isEqualTo(DraftCefMappingTool.CUSTOM_OPTIONS);

        instructions = List.of(Map.of("instructions", "Custom CEF keys are not allowed. Kafka topic: arcsight-ss"));
        final Map<String, Object> result = draft("{\"stream_ids\": [7]}");

        assertThat(result.get("custom_keys")).isEqualTo(false);
        assertThat(((Map<?, ?>) result.get("plan")).get("topic")).isEqualTo("arcsight-ss");
        assertThat((List<?>) result.get("problems")).isEmpty();
        assertThat(result.get("from_standing_instructions")).isEqualTo(Map.of("custom_keys", false, "topic",
                "arcsight-ss"));
        assertThat((String) result.get("example_line")).startsWith("CEF:0|Delinea|SecretServer|11.4|User-Login|");
        assertThat((String) result.get("documentation")).startsWith("## Field mapping");
        assertThat((String) result.get("hint")).startsWith("Show the user the plan's tables");
    }

    @Test
    void theUsersAnswerIsKeptForTheBuildSoItIsAskedOnce() throws Exception {
        // Seen: asked whether keys outside ArcSight's dictionary may be used on every call of one CEF pipeline's
        // building.
        Mockito.when(consent.choose(any(), any(), eq(DraftCefMappingTool.NAME),
                        eq(DraftCefMappingTool.CUSTOM_QUESTION), anyList(), isNull()))
                .thenReturn(Optional.of(DraftCefMappingTool.CUSTOM_OPTIONS.get(1)));

        assertThat(draft("{\"stream_ids\": [7], \"topic\": \"t\", \"build\": \"b\"}").get("custom_keys"))
                .isEqualTo(true);
        assertThat(draft("{\"stream_ids\": [7], \"topic\": \"t\", \"build\": \"b\"}").get("custom_keys"))
                .isEqualTo(true);
        Mockito.verify(consent, Mockito.times(1)).choose(any(), any(), eq(DraftCefMappingTool.NAME),
                eq(DraftCefMappingTool.CUSTOM_QUESTION), anyList(), isNull());
        // Another build's is its own.
        draft("{\"stream_ids\": [7], \"topic\": \"t\", \"build\": \"other\"}");
        Mockito.verify(consent, Mockito.times(2)).choose(any(), any(), eq(DraftCefMappingTool.NAME),
                eq(DraftCefMappingTool.CUSTOM_QUESTION), anyList(), isNull());
    }

    @Test
    @SuppressWarnings("unchecked")
    void theFieldsNotSentAreGivenOnceByWhy() throws Exception {
        // Seen: 125 not-sent entries, each saying the same why, given in the plan and again beside it.
        final Map<String, Object> result = draft("{\"stream_ids\": [7], \"topic\": \"t\", \"custom_keys\": false, "
                                                 + "\"overrides\": [{\"path\": "
                                                 + "\"EventDetail/Authenticate/Data[@Name='note']/@Value\", "
                                                 + "\"drop\": true}]}");

        assertThat((Map<String, Object>) result.get("plan")).doesNotContainKey("not_sent");
        assertThat((List<Map<String, Object>>) result.get("not_sent")).anySatisfy(group -> {
            assertThat(group.get("why")).isEqualTo("left out as instructed");
            assertThat(group.get("count")).isEqualTo(1);
            assertThat(group.get("paths")).isEqualTo(Map.of("Authenticate",
                    List.of("EventDetail/Authenticate/Data[@Name='note']/@Value")));
        });
    }

    @Test
    void notSentEntriesAreGroupedByWhyThenEventTypeKeepingEachPath() {
        final List<Map<String, Object>> grouped = DraftCefMappingTool.groupedNotSent(List.of(
                Map.of("path", "EventDetail/Alert/Data[@Name='a']/@Value", "event_type", "Alert", "why", "no key"),
                Map.of("path", "EventSource/Client/HostName", "event_type", "common", "why", "left out"),
                Map.of("path", "EventDetail/Alert/Data[@Name='b']/@Value", "event_type", "Alert", "why", "no key"),
                Map.of("path", "EventDetail/View/Data[@Name='c']/@Value", "event_type", "View", "why", "no key")));

        assertThat(grouped).containsExactly(
                Map.of("why", "no key", "count", 3, "paths", Map.of(
                        "Alert", List.of("EventDetail/Alert/Data[@Name='a']/@Value",
                                "EventDetail/Alert/Data[@Name='b']/@Value"),
                        "View", List.of("EventDetail/View/Data[@Name='c']/@Value"))),
                Map.of("why", "left out", "count", 1, "paths", Map.of("common",
                        List.of("EventSource/Client/HostName"))));
        assertThat(DraftCefMappingTool.groupedNotSent(List.of())).isEmpty();
    }

    @Test
    void theUsersAnswerInAFormIsTaken() throws Exception {
        Mockito.when(consent.choose(any(), any(), eq(DraftCefMappingTool.NAME),
                        eq(DraftCefMappingTool.CUSTOM_QUESTION), anyList(), isNull()))
                .thenReturn(Optional.of(DraftCefMappingTool.CUSTOM_OPTIONS.get(1)));

        final Map<String, Object> result = draft("{\"stream_ids\": [7], \"topic\": \"t\"}");

        assertThat(result.get("custom_keys")).isEqualTo(true);
    }

    @Test
    @SuppressWarnings("unchecked")
    void aPlanWithProblemsIsNotSaved() throws Exception {
        // No topic.
        final Map<String, Object> result = draft("{\"stream_ids\": [7], \"custom_keys\": false, \"build\": \"b\", "
                                                 + "\"name\": \"CEF\"}");

        assertThat(result).containsEntry("saved", null);
        assertThat(String.join(" ", (List<String>) result.get("problems"))).contains("needs topic");
        Mockito.verify(saves, Mockito.never()).create(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void anXsltThatDoesNotCompileIsNotSaved() throws Exception {
        // Seen: a record key's undeclared prefix (//d:EventSource/d:SessionId) found only by Stroom's log when the
        // pipeline ran.
        Mockito.when(compileCheck.compile(anyString())).thenReturn(new XsltCompileCheck.Compiled(List.of(
                "XPST0081 Namespace prefix 'q' has not been declared")));

        final Map<String, Object> result = draft("{\"stream_ids\": [7], \"custom_keys\": false, \"topic\": \"t\", "
                                                 + "\"build\": \"b\", \"name\": \"CEF\"}");

        assertThat(result).containsEntry("saved", null);
        assertThat((List<String>) result.get("problems")).containsExactly(
                "the XSLT doesn't compile: XPST0081 Namespace prefix 'q' has not been declared");
        Mockito.verify(saves, Mockito.never()).create(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void aPlanIsSavedWithItsXsltAndTheImportsTheInstructionsRequire() throws Exception {
        Mockito.when(saves.requiredFor(any(), eq("cef"), eq("b"), any())).thenReturn(List.of(
                new RequiredImport("CEF-Shared", "/Feeds")));
        Mockito.when(saves.create(any(), any(), eq("b"), eq("CEF"), anyString(), any(), anyString())).thenReturn(Map.of(
                "type", "XSLT", "uuid", "x-1", "name", "CEF", "version", "v1", "next", Map.of("step", "pipeline")));

        final Map<String, Object> result = draft("{\"stream_ids\": [7], \"custom_keys\": false, \"topic\": \"t\", "
                                                 + "\"build\": \"b\", \"name\": \"CEF\"}");

        final ArgumentCaptor<String> code = ArgumentCaptor.forClass(String.class);
        final ArgumentCaptor<TranslationDocs.Source> source = ArgumentCaptor.forClass(TranslationDocs.Source.class);
        Mockito.verify(saves).create(any(), any(), eq("b"), eq("CEF"), code.capture(), source.capture(),
                eq("Created from its CEF plan"));
        assertThat(code.getValue()).contains("<xsl:import href=\"CEF-Shared\"");
        // Saved with the CEF plan it is generated from.
        assertThat(source.getValue().cefPlan().imports()).isEqualTo(List.of("CEF-Shared"));
        assertThat(result.get("required_imports")).isEqualTo(List.of("CEF-Shared"));
        assertThat(result.get("saved")).isEqualTo(Map.of("type", "XSLT", "uuid", "x-1", "name", "CEF", "version",
                "v1"));
        assertThat(result.get("next")).isEqualTo(Map.of("step", "pipeline"));
        assertThat((String) result.get("hint")).startsWith("Pipeline: create_pipeline from ")
                .contains("setting the XSLT step's xslt to 'x-1'")
                .contains("standardKafkaProducer.kafkaConfig");
    }

    @Test
    void aSaveTheUserIsAskedAboutComesBackWithThePlan() throws Exception {
        final CefPlan kept = CefPlan.of(CefPlan.TEXT, null, false, header(),
                List.of(CefField.of("EventSource/User/Id", "suser")), Map.of());
        Mockito.when(documentAccess.read(any(), eq(stroom.pipeline.shared.XsltDoc.class))).thenReturn(Optional.of(
                stroom.pipeline.shared.XsltDoc.builder().uuid("x-1").name("CEF")
                        .description(KeptMapping.withMapping(null, "cef",
                                kept.toJson(stroom.ai.impl.tools.generation.DumpMode.COMPACT)))
                        .build()));
        final Map<String, Object> guidance = new LinkedHashMap<>();
        guidance.put("status", "needs_guidance");
        guidance.put("saved", null);
        guidance.put("hint", "Not saved: the user edited these fields by hand");
        Mockito.when(saves.update(any(), any(), eq("x-1"), anyString(), any(), any(), anyString(), anyBoolean(), any()))
                .thenReturn(guidance);

        final Map<String, Object> result = draft("{\"stream_ids\": [7], \"custom_keys\": false, \"uuid\": \"x-1\", "
                                                 + "\"overrides\": [{\"path\": \"EventSource/User/Id\", \"key\": "
                                                 + "\"duser\"}]}");

        assertThat(result.get("status")).isEqualTo("needs_guidance");
        assertThat(result.get("applied")).isEqualTo(List.of("[common] EventSource/User/Id -> duser"));
        Mockito.verify(saves).update(any(), any(), eq("x-1"), anyString(), any(), any(),
                eq("CEF plan changes: [common] EventSource/User/Id -> duser"), eq(false), isNull());
    }

    @Test
    void anXsltWithNoCefPlanIsRefused() {
        Mockito.when(documentAccess.read(any(), eq(stroom.pipeline.shared.XsltDoc.class))).thenReturn(Optional.of(
                stroom.pipeline.shared.XsltDoc.builder().uuid("x-2").name("Hand").build()));
        Mockito.when(saves.lostMapping(any(), any())).thenReturn(Optional.empty());

        final String said = tool.execute(call("{\"stream_ids\": [7], \"custom_keys\": false, \"uuid\": \"x-2\"}"),
                world.context()).text();

        assertThat(said).contains("XSLT x-2 keeps no CEF plan: draft without uuid, or review its pipeline "
                                  + "(pipeline_uuid)");
    }

    @Test
    void overridesMoveDropAndAddPathsOnAPlan() {
        final CefPlan drafted = CefPlan.of(CefPlan.KAFKA, "t", false, header(),
                List.of(CefField.of("EventSource/User/Id", "suser")),
                Map.of("View", List.of(CefField.of("EventDetail/View/Resource/Name", "fname"))));

        final DraftCefMappingTool.Applied applied = DraftCefMappingTool.apply(drafted, List.of(
                CefOverride.to("EventSource/User/Id", "destinationUserName", null),
                CefOverride.dropped("EventDetail/*/Resource/Name", null),
                CefOverride.to("EventDetail/View/Resource/Id", "cs1", "Resource Id")));

        assertThat(applied.done()).containsExactly(
                "[common] EventSource/User/Id -> duser",
                "[View] EventDetail/View/Resource/Name left out",
                "[every event] EventDetail/View/Resource/Id -> cs1 (added)");
        assertThat(applied.plan().common()).extracting(CefField::path, CefField::key, CefField::label)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("EventSource/User/Id", "duser", null),
                        org.assertj.core.groups.Tuple.tuple("EventDetail/View/Resource/Id", "cs1", "Resource Id"));
        assertThat(applied.plan().events().get("View")).isEmpty();
        assertThat(applied.plan().notSent()).containsExactly(Map.of("path", "EventDetail/View/Resource/Name",
                "event_type", "View", "why", "left out as instructed"));
    }

    @Test
    void aCefPipelineIsReviewedFromTheLinesItWrites() throws Exception {
        final String event = EVENTS.substring(0, EVENTS.indexOf("</Event>") + "</Event>".length()) + "</Events>";
        world.stepper = (stream, at) -> at.getRecordIndex() < 1
                ? step(stream, at.getRecordIndex(), event, "<kafkaRecords xmlns=\"kafka-records:1\"><kafkaRecord "
                                                           + "topic=\"arcsight\"><value>CEF:0|Delinea|SecretServer|"
                                                           + "11.4|User-Login|User logged in|3|suser=priya.patel"
                                                           + "</value></kafkaRecord></kafkaRecords>")
                : null;

        final Map<String, Object> result = draft("{\"stream_ids\": [7], \"custom_keys\": false, \"pipeline_uuid\": "
                                                 + "\"p-1\"}");

        assertThat(((Map<?, ?>) result.get("review")).get("lines")).isEqualTo(1);
        assertThat((String) result.get("plan_from")).startsWith("the mapping its lines imply");
        final Map<?, ?> drafted = (Map<?, ?>) result.get("plan");
        assertThat(drafted.get("topic")).isEqualTo("arcsight");
        assertThat(drafted.get("vendor")).isEqualTo(Map.of("value", "Delinea"));
        assertThat(result).doesNotContainKey("templates");
    }

    @Test
    void aPipelineThatWritesNoCefLinesIsRefused() {
        world.records(1, "<Events/>");

        final String said = tool.execute(call("{\"stream_ids\": [7], \"custom_keys\": false, \"pipeline_uuid\": "
                                              + "\"p-1\"}"), world.context()).text();

        assertThat(said).contains("Stepping the pipeline over streams [7] gave no CEF lines at its element "
                                  + "'translationFilter'");
    }

    @Test
    void anUnknownHeaderFieldIsRefused() {
        final String said = tool.execute(call("{\"stream_ids\": [7], \"header\": {\"colour\": \"red\"}}"),
                world.context()).text();

        assertThat(said).contains("header has no ['colour']: its fields are ['vendor', 'product', 'version', "
                                  + "'signature', 'name', 'severity']");
    }

    @Test
    void onlyACallThatSavesChangesAnything() {
        assertThat(tool.isReadOnly(call("{\"stream_ids\": [7]}"))).isTrue();
        assertThat(tool.isReadOnly(call("{\"stream_ids\": [7], \"build\": \"b\", \"name\": \"n\"}"))).isFalse();
        assertThat(tool.isReadOnly(call("{\"stream_ids\": [7], \"uuid\": \"x\"}"))).isFalse();
        assertThat(tool.confirmsItself()).isFalse();
        assertThat(tool.changesOnlyItsBuild()).isTrue();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> draft(final String arguments) throws Exception {
        final ToolCallContext context = world.context();
        final ToolResult result = tool.execute(call(arguments), context);
        // The structured content keeps what the JSON text leaves out (a null).
        return result.structuredContent() != null
                ? result.structuredContent()
                : JsonUtil.readValue(result.text(), Map.class);
    }

    private static Map<String, CefValue> header() {
        return Map.of("vendor", CefValue.constant("Delinea"), "product", CefValue.constant("SecretServer"),
                "version", CefValue.constant("11.4"), "signature", CefValue.from("EventDetail/TypeId"),
                "name", CefValue.from("EventDetail/TypeId"));
    }

    private static ToolCall call(final String arguments) {
        return new ToolCall("1", DraftCefMappingTool.NAME, arguments);
    }

    private static SteppingResult step(final long stream, final long record, final String input, final String output) {
        final Map<String, SharedElementData> elements = new LinkedHashMap<>();
        elements.put("translationFilter", new SharedElementData(input, output, null, false, false, true));
        return new SteppingResult("s", null, null, new StepLocation(stream, 0L, record),
                new SharedStepData(null, elements), null, true, null, false, true);
    }
}
