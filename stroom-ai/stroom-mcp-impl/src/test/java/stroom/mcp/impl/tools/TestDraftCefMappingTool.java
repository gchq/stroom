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

import stroom.ai.impl.tools.ToolCall;
import stroom.ai.impl.tools.ToolCallContext;
import stroom.ai.impl.tools.ToolResult;
import stroom.ai.impl.tools.cef.CefField;
import stroom.ai.impl.tools.cef.CefOverride;
import stroom.ai.impl.tools.cef.CefPlan;
import stroom.ai.impl.tools.cef.CefValue;
import stroom.ai.impl.tools.documents.DocumentAccess;
import stroom.ai.impl.tools.documents.KeptMapping;
import stroom.ai.impl.tools.instructions.AgentInstructions;
import stroom.ai.impl.tools.instructions.InstructionRules.RequiredImport;
import stroom.ai.impl.tools.validation.EventStreams;
import stroom.ai.impl.tools.validation.XsltCompileCheck;
import stroom.docref.DocRef;
import stroom.pipeline.shared.PipelineDoc;
import stroom.pipeline.shared.SharedElementData;
import stroom.pipeline.shared.XsltDoc;
import stroom.pipeline.shared.data.PipelineDataBuilder;
import stroom.pipeline.shared.data.PipelineElement;
import stroom.pipeline.shared.data.PipelineProperty;
import stroom.pipeline.shared.data.PipelinePropertyValue;
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

/// draft_cef_mapping, the tool: asking the user about keys outside ArcSight's dictionary, what the standing
/// instructions say, and saving; and the overrides on a plan, a review of a CEF pipeline and a save the user is asked
/// about.
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

    private static final String HANDOFF_NS = "urn:example:handoff";
    // A shared Kafka XSLT: records of data pairs in, a Kafka record each out, its value the pair named cef, the topic
    // set here.
    private static final String SHARED = "<xsl:stylesheet xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\" "
                                         + "version=\"2.0\" xpath-default-namespace=\"" + HANDOFF_NS + "\" "
                                         + "xmlns=\"kafka-records:1\"><xsl:template match=\"/records\"><kafkaRecords>"
                                         + "<xsl:for-each select=\"record\"><kafkaRecord topic=\"arcsight-prod\">"
                                         + "<value><xsl:value-of select=\"data[@name='cef']/@value\"/></value>"
                                         + "</kafkaRecord></xsl:for-each></kafkaRecords></xsl:template>"
                                         + "</xsl:stylesheet>";
    private static final DocRef TWO_STAGES = new DocRef(PipelineDoc.TYPE, "fwd", "Kafka Forwarding");
    private static final DocRef ONE_STAGE = new DocRef(PipelineDoc.TYPE, "one", "CEF Kafka");

    private final SteppingWorld world = new SteppingWorld();
    private final AgentInstructions agentInstructions = Mockito.mock(AgentInstructions.class);
    private final Consent consent = Mockito.mock(Consent.class);
    private final EventStreams eventStreams = Mockito.mock(EventStreams.class);
    private final KeptMappings keptMappings = Mockito.mock(KeptMappings.class);
    // The world's: it reads the world's pipelines (and the XSLTs a test gives).
    private final DocumentAccess documentAccess = world.documentAccess;
    private final Map<String, XsltDoc> xslts = new LinkedHashMap<>();
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
        Mockito.when(documentAccess.read(any(DocRef.class), eq(XsltDoc.class))).thenAnswer(inv -> Optional.ofNullable(
                xslts.get(((DocRef) inv.getArgument(0)).getUuid())));
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

    @Test
    @SuppressWarnings("unchecked")
    void aTemplateWithASharedKafkaXsltGetsOnlyTheFlattenerHandingEachLineOn() throws Exception {
        forwardingTemplates();
        Mockito.when(sources.pipeline("Kafka Forwarding")).thenReturn(Optional.of(TWO_STAGES));
        Mockito.when(saves.create(any(), any(), eq("b"), eq("CEF"), anyString(), any(), anyString())).thenReturn(Map.of(
                "type", "XSLT", "uuid", "x-9", "name", "CEF", "version", "v1"));

        final Map<String, Object> result = draft("{\"stream_ids\": [7], \"custom_keys\": false, \"topic\": "
                                                 + "\"mine\", \"template\": \"Kafka Forwarding\", \"build\": \"b\", "
                                                 + "\"name\": \"CEF\"}");

        final Map<String, Object> shape = (Map<String, Object>) result.get("shape");
        assertThat(shape).containsEntry("kind", "chained")
                .containsEntry("decided_by", "the call names 'Kafka Forwarding'")
                .containsEntry("flattener", "flattenFilter")
                .containsEntry("handoff_checked", true);
        assertThat((String) shape.get("handoff")).contains("<data name=\"cef\" value=\"CEF line\"/>");
        assertThat(((Map<String, Object>) result.get("plan")).get("output")).isEqualTo("chained");
        assertThat((Map<String, Object>) result.get("plan")).doesNotContainKey("topic");
        assertThat((List<String>) result.get("notes")).anySatisfy(note -> assertThat(note)
                .startsWith("topic 'mine' not used: the shared XSLT 'Shared Kafka output' the template supplies "
                            + "sets the topic"));
        assertThat((Map<String, Object>) result.get("chain_check")).containsEntry("ran", true)
                .containsEntry("problems", List.of());
        assertThat((List<?>) result.get("problems")).isEmpty();
        assertThat(result).doesNotContainKey("kafka_configs");
        final ArgumentCaptor<String> code = ArgumentCaptor.forClass(String.class);
        Mockito.verify(saves).create(any(), any(), eq("b"), eq("CEF"), code.capture(), any(), anyString());
        assertThat(code.getValue()).contains("<data name=\"cef\" value=\"{$line}\"").doesNotContain("kafkaRecord",
                "mine");
        assertThat((String) result.get("hint")).startsWith("Pipeline: create_pipeline template_uuid='fwd' (Kafka "
                                                           + "Forwarding), setting only flattenFilter.xslt to 'x-9': "
                                                           + "no topic or Kafka setting");
    }

    @Test
    @SuppressWarnings("unchecked")
    void aTemplateTheInstructionsNameOfOneStageKeepsTheXsltWritingItsRecords() throws Exception {
        forwardingTemplates();
        Mockito.when(sources.pipeline("CEF Kafka")).thenReturn(Optional.of(ONE_STAGE));
        instructions = List.of(Map.of("instructions", "Pipeline template: CEF Kafka\nKafka topic: arcsight"));

        final Map<String, Object> result = draft("{\"stream_ids\": [7], \"custom_keys\": false}");

        assertThat((Map<String, Object>) result.get("shape")).containsEntry("kind", "single")
                .containsEntry("decided_by", "the standing instructions name 'CEF Kafka'");
        assertThat((Map<String, Object>) result.get("plan")).containsEntry("topic", "arcsight")
                .doesNotContainKey("handoff");
        assertThat((String) result.get("example_line")).startsWith("CEF:0|Delinea|");
    }

    @Test
    void withForwardingTemplatesOfBothShapesAndNothingNamingOneTheUserIsAsked() throws Exception {
        forwardingTemplates();
        Mockito.when(sources.templates(any(), anyList())).thenReturn(Map.of("forwarding_templates", List.of(
                Map.of("uuid", "fwd", "name", "Kafka Forwarding"), Map.of("uuid", "one", "name", "CEF Kafka"))));

        final Map<String, Object> asked = draft("{\"stream_ids\": [7], \"custom_keys\": false}");

        assertThat(asked).containsEntry("status", "needs_guidance")
                .containsEntry("question", DraftCefMappingTool.TEMPLATE_QUESTION);
        assertThat(asked.get("options")).isEqualTo(List.of(
                "'Kafka Forwarding' (two stages: its shared XSLT 'Shared Kafka output' writes the Kafka records and "
                + "sets the topic)",
                "'CEF Kafka' (one stage: the pipeline's own XSLT writes the records)"));

        // Answered in a form: the two stages.
        Mockito.when(consent.choose(any(), any(), eq(DraftCefMappingTool.NAME),
                        eq(DraftCefMappingTool.TEMPLATE_QUESTION), anyList(), isNull()))
                .thenAnswer(inv -> Optional.of(((List<String>) inv.getArgument(4)).getFirst()));
        final Map<String, Object> chosen = draft("{\"stream_ids\": [7], \"custom_keys\": false}");
        assertThat(((Map<?, ?>) chosen.get("shape")).get("decided_by")).isEqualTo("the user chose 'Kafka Forwarding'");
        assertThat(((Map<?, ?>) chosen.get("plan")).get("output")).isEqualTo("chained");
    }

    @Test
    void aBuildsCopyOfACefPipelineOfTwoStagesDecidesWithoutAsking() throws Exception {
        forwardingTemplates();
        world.pipeline("copy", "Vault CEF V1.1", "fwd", new PipelineDataBuilder()
                .addProperty(new PipelineProperty("flattenFilter", "xslt",
                        new PipelinePropertyValue(new DocRef(XsltDoc.TYPE, "fx", "Vault flattener")))));
        xslt("fx", "Vault flattener", "<xsl:stylesheet xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\" "
                                      + "version=\"2.0\"/>");
        Mockito.when(sources.buildVersions("b")).thenReturn(List.of(new DocRef(PipelineDoc.TYPE, "copy",
                "Vault CEF V1.1")));

        final Map<String, Object> result = draft("{\"stream_ids\": [7], \"custom_keys\": false, \"build\": "
                                                 + "\"b\"}");

        assertThat(((Map<?, ?>) result.get("shape")).get("decided_by"))
                .isEqualTo("the build's copy 'Vault CEF V1.1' of a CEF pipeline has two stages");
        assertThat(((Map<?, ?>) ((Map<?, ?>) result.get("shape")).get("template")).get("uuid")).isEqualTo("fwd");
    }

    @Test
    void aTemplateThatCantBeFoundOrWhoseSharedXsltCantBeReadIsRefused() {
        forwardingTemplates();
        assertThat(tool.execute(call("{\"stream_ids\": [7], \"custom_keys\": false, \"template\": \"nope\"}"),
                world.context()).text()).contains("template 'nope' is no pipeline you may view");

        Mockito.when(sources.pipeline("fwd")).thenReturn(Optional.of(TWO_STAGES));
        xslt("sx", "Shared Kafka output", "<xsl:stylesheet xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\" "
                                          + "version=\"2.0\" xmlns=\"kafka-records:1\"><xsl:template match=\"/\">"
                                          + "<kafkaRecords/></xsl:template></xsl:stylesheet>");
        assertThat(tool.execute(call("{\"stream_ids\": [7], \"custom_keys\": false, \"template\": \"fwd\"}"),
                world.context()).text()).contains("Can't tell what the shared XSLT 'Shared Kafka output' (at "
                                                  + "'kafkaOutputFilter') reads");
    }

    @Test
    @SuppressWarnings("unchecked")
    void aForwarderOfTwoStagesIsReviewedThroughBoth() throws Exception {
        forwardingTemplates();
        world.pipeline("p-2", "Vault CEF", "fwd", new PipelineDataBuilder()
                .addProperty(new PipelineProperty("flattenFilter", "xslt",
                        new PipelinePropertyValue(new DocRef(XsltDoc.TYPE, "fx", "Vault flattener")))));
        final String event = EVENTS.substring(0, EVENTS.indexOf("</Event>") + "</Event>".length()) + "</Events>";
        final String line = "CEF:0|Delinea|SecretServer|11.4|User-Login|User logged in|3|suser=priya.patel";
        final String handedOn = "<records xmlns=\"" + HANDOFF_NS + "\"><record><data name=\"cef\" value=\"" + line
                                + "\"/></record></records>";
        final Map<String, SharedElementData> elements = new LinkedHashMap<>();
        elements.put("flattenFilter", new SharedElementData(event, handedOn, null, false, false, true));
        elements.put("kafkaOutputFilter", new SharedElementData(handedOn, "<kafkaRecords xmlns=\"kafka-records:1\">"
                                                                          + "<kafkaRecord topic=\"arcsight-prod\">"
                                                                          + "<value>" + line + "</value></kafkaRecord>"
                                                                          + "</kafkaRecords>", null, false, false,
                true));
        world.stepper = (stream, at) -> at.getRecordIndex() < 1
                ? new SteppingResult("s", null, null, new StepLocation(stream, 0L, at.getRecordIndex()),
                        new SharedStepData(null, elements), null, true, null, false, true)
                : null;

        final Map<String, Object> result = draft("{\"stream_ids\": [7], \"custom_keys\": false, \"pipeline_uuid\": "
                                                 + "\"p-2\"}");

        final Map<String, Object> review = (Map<String, Object>) result.get("review");
        assertThat(review.get("lines")).isEqualTo(1);
        assertThat(String.valueOf(review.get("implied_mapping"))).as("each key matched to the Event it carries")
                .contains("path=EventSource/User/Id, key=suser");
        assertThat((Map<String, Object>) result.get("shape")).containsEntry("kind", "chained")
                .containsEntry("flattener", "flattenFilter")
                .containsEntry("lines_read_at", "kafkaOutputFilter");
        assertThat(((Map<?, ?>) result.get("plan")).get("output")).as("a plan saved from it hands its lines on")
                .isEqualTo("chained");
        assertThat(((Map<?, ?>) ((Map<?, ?>) result.get("shape")).get("shared_xslt")).get("uuid")).isEqualTo("sx");
        assertThat((String) result.get("hint")).contains("Pipeline: its flattener runs the saved XSLT; the "
                                                         + "template's shared stage after it writes the Kafka "
                                                         + "records");
    }

    // Two forwarding templates: `fwd`, an open flattener slot then a shared XSLT writing Kafka records; and `one`, an
    // open XSLT slot writing them itself.
    private void forwardingTemplates() {
        xslt("sx", "Shared Kafka output", SHARED);
        world.pipeline("fwd", "Kafka Forwarding", null, new PipelineDataBuilder()
                .addElement(new PipelineElement("xmlParser", "XMLParser"))
                .addElement(new PipelineElement("flattenFilter", "XSLTFilter"))
                .addElement(new PipelineElement("kafkaOutputFilter", "XSLTFilter"))
                .addElement(new PipelineElement("schemaFilter", "SchemaFilter"))
                .addElement(new PipelineElement("kafkaProducer", "StandardKafkaProducer"))
                .addLink("xmlParser", "flattenFilter")
                .addLink("flattenFilter", "kafkaOutputFilter")
                .addLink("kafkaOutputFilter", "schemaFilter")
                .addLink("schemaFilter", "kafkaProducer")
                .addProperty(new PipelineProperty("kafkaOutputFilter", "xslt",
                        new PipelinePropertyValue(new DocRef(XsltDoc.TYPE, "sx", "Shared Kafka output")))));
        world.pipeline("one", "CEF Kafka", null, new PipelineDataBuilder()
                .addElement(new PipelineElement("xmlParser", "XMLParser"))
                .addElement(new PipelineElement("xsltFilter", "XSLTFilter"))
                .addElement(new PipelineElement("kafkaProducer", "StandardKafkaProducer"))
                .addLink("xmlParser", "xsltFilter")
                .addLink("xsltFilter", "kafkaProducer"));
    }

    private void xslt(final String uuid, final String name, final String code) {
        xslts.put(uuid, XsltDoc.builder().uuid(uuid).name(name).data(code).build());
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
