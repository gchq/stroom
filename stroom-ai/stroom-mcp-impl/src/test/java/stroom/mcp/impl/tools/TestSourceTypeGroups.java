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

import stroom.ai.impl.tools.JsonValues;
import stroom.ai.impl.tools.KeptMapping;
import stroom.ai.impl.tools.generation.DispatcherSpec;
import stroom.ai.impl.tools.generation.DispatcherXslt;
import stroom.ai.impl.tools.generation.DumpMode;
import stroom.ai.impl.tools.generation.HelpersXslt;
import stroom.ai.impl.tools.generation.SourceTypeModule;
import stroom.ai.impl.tools.generation.TranslationMapping;
import stroom.ai.impl.tools.generation.XsltGenerator;
import stroom.ai.impl.tools.sample.FeedSampling.SurveyRecord;
import stroom.ai.impl.tools.sample.SourceTypeRegistry;
import stroom.ai.impl.tools.sample.ToolJson;
import stroom.docref.DocRef;
import stroom.pipeline.shared.XsltDoc;
import stroom.util.json.JsonUtil;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;

/// `build_translation_xslt group=`: a source type group of an aggregated feed saved with its mapping, the feed's
/// common module and helpers made from the first group's mapping, the dispatcher generated from the registry with
/// each group imported by UUID, and the registry's rows set translated; a second group reusing the common module;
/// a group regenerated from its kept mapping alone; and an unsaved group stepped through a temporary dispatcher.
class TestSourceTypeGroups {

    private static final String BUILD = "pega-v1";
    private static final String WRAPPER = """
            [{"path": "EventTime/TimeCreated", "field": "timestamp"},
             {"path": "EventSource/System/Name", "value": "PEGA"},
             {"path": "EventSource/System/Environment", "value": "Test"},
             {"path": "EventSource/Generator", "value": "Pega"},
             {"path": "EventSource/Device/HostName", "field": "hostname"}]""";

    private final TranslationWorld world = new TranslationWorld();
    private final BuildTranslationXsltTool tool = world.buildTool();
    private final List<String> written = new ArrayList<>();

    @BeforeEach
    void setUp() {
        Mockito.when(world.registries.registry(eq(BUILD), eq("PEGA"))).thenAnswer(inv -> Optional.of(registry()));
        Mockito.when(world.registries.writeRegistry(any(), eq(BUILD), eq("PEGA"), any(), anyString()))
                .thenAnswer(inv -> {
                    written.add(inv.getArgument(4));
                    return new SurveyRecord("r", "PEGA source types", inv.getArgument(4));
                });
    }

    @Test
    void aGroupIsSavedWithTheFeedsCommonModuleHelpersAndDispatcher() {
        final Map<String, Object> saved = world.call(tool, args(security(), "PEGA security",
                List.of("PegaSecurityEvent")));

        assertThat(saved.get("problems")).as("%s", saved).isEqualTo(List.of());
        final Map<String, Object> group = JsonValues.mapOrEmpty(saved.get("group"));
        assertThat(group).containsEntry("mode", "pega_security").containsEntry("system_name", "PEGA Security");
        assertThat(JsonValues.strings(group.get("common_parts"))).containsExactly("EventTime", "EventSource/System",
                "EventSource/Generator", "EventSource/Device");
        // The build holds the group, the feed's common module and helpers, and its dispatcher.
        final Map<String, String> docs = xsltsInBuild();
        assertThat(docs).containsOnlyKeys("PEGA security", "PEGA common", "PEGA helpers", "PEGA dispatcher");
        // The group keeps its mapping, its module importing the build's common and helpers modules by UUID; the
        // wrapper's entries went to the common module.
        final XsltDoc groupXslt = world.xslt(docs.get("PEGA security"));
        final TranslationMapping kept = keptMapping(groupXslt);
        assertThat(kept.module().kind()).isEqualTo(SourceTypeModule.GROUP);
        assertThat(kept.module().common()).isEqualTo("uuid='" + docs.get("PEGA common") + "', name='PEGA common'");
        assertThat(kept.module().helpers()).contains(docs.get("PEGA helpers"));
        assertThat(kept.common()).isEmpty();
        assertThat(groupXslt.getData()).contains("<xsl:import href=\"uuid='" + docs.get("PEGA common") + "'",
                "mode=\"pega_security\"").doesNotContain("<xsl:template match=\"/\"");
        final TranslationMapping common = keptMapping(world.xslt(docs.get("PEGA common")));
        assertThat(common.module().kind()).isEqualTo(SourceTypeModule.COMMON);
        // The feed's shared decisions replace the mapping's: the registry's Environment.
        assertThat(ToolJson.dumps(common.toJson(DumpMode.COMPACT)))
                .contains("\"value\": \"Prod\"").doesNotContain("Test");
        assertThat(world.xslt(docs.get("PEGA helpers")).getData()).isEqualTo(HelpersXslt.generate());
        // The dispatcher imports the group by UUID, its spec kept with it.
        final XsltDoc dispatcher = world.xslt(docs.get("PEGA dispatcher"));
        assertThat(dispatcher.getData()).contains("<xsl:import href=\"uuid='" + docs.get("PEGA security") + "'",
                "<xsl:apply-templates select=\".\" mode=\"pega_security\"/>");
        final DispatcherSpec spec = DispatcherSpec.fromJson(KeptMapping.readBlock(dispatcher.getDescription(),
                DispatcherXslt.SPEC_BLOCK).orElseThrow()).orElseThrow();
        assertThat(spec.groups()).singleElement().satisfies(one -> assertThat(one.sourceTypes())
                .containsExactly("PegaSecurityEvent"));
        assertThat(spec.rows()).extracting(DispatcherSpec.Row::name).containsExactly("PegaSecurityEvent",
                "PegaIntegration", "PegaDB");
        // The registry's row translated, by the group.
        final Map<String, Object> row = rowNamed(written.getLast(), "PegaSecurityEvent");
        assertThat(row).containsEntry("status", SourceTypeRegistry.TRANSLATED).containsEntry("group",
                "PEGA security");
        assertThat(rowNamed(written.getLast(), "PegaDB")).containsEntry("status", SourceTypeRegistry.PARKED);
    }

    @Test
    void aSecondGroupLeavesTheWrappersPartsToTheCommonModuleAndJoinsTheDispatcher() {
        world.call(tool, args(security(), "PEGA security", List.of("PegaSecurityEvent")));
        final Map<String, String> before = xsltsInBuild();

        final Map<String, Object> saved = world.call(tool, args(integration(), "PEGA integration",
                List.of("PegaIntegration")));

        assertThat(saved.get("problems")).as("%s", saved).isEqualTo(List.of());
        final Map<String, String> docs = xsltsInBuild();
        assertThat(docs.get("PEGA common")).isEqualTo(before.get("PEGA common"));
        final TranslationMapping kept = keptMapping(world.xslt(docs.get("PEGA integration")));
        // Its wrapper entries are the common module's (its own System Name aside): left to it.
        assertThat(kept.common()).isEmpty();
        final DispatcherSpec spec = DispatcherSpec.fromJson(KeptMapping.readBlock(world.xslt(docs.get(
                "PEGA dispatcher")).getDescription(), DispatcherXslt.SPEC_BLOCK).orElseThrow()).orElseThrow();
        assertThat(spec.groups()).extracting(DispatcherSpec.Group::mode).containsExactly("pega_security",
                "pega_integration");

        // Regenerated from its kept mapping alone, by uuid: built again as the group it is.
        final Map<String, Object> again = world.call(tool, Map.of("uuid", docs.get("PEGA integration"),
                "schema_version", TranslationWorld.VERSION, "feeds", List.of("PEGA")));
        assertThat(again.get("problems")).as("%s", again).isEqualTo(List.of());
        assertThat(JsonValues.mapOrEmpty(again.get("group"))).containsEntry("mode", "pega_integration");
        assertThat(xsltsInBuild()).hasSize(5);
    }

    @Test
    void sourceTypesTheRegistryLacksAreRefused() {
        final Map<String, Object> refused = world.call(tool, args(security(), "PEGA security",
                List.of("NoSuchType")));

        assertThat(String.valueOf(refused.get("error"))).contains("NoSuchType", "are not the registry's");
    }

    @Test
    void anUnsavedGroupIsSteppedThroughATemporaryDispatcher() {
        final SteppingWorld stepping = new SteppingWorld();
        final String groupHref = "uuid='g0000000-0000-0000-0000-000000000001', name='PEGA security'";
        final List<DispatcherSpec.Row> rows = List.of(
                new DispatcherSpec.Row("PegaSecurityEvent", "value", "PegaSecurityEvent", "translated"),
                new DispatcherSpec.Row("PegaIntegration", "value", "PegaIntegration", "parked"));
        final List<DispatcherSpec.Group> groups = List.of(new DispatcherSpec.Group("PEGA security", "pega_security",
                groupHref, List.of("PegaSecurityEvent")));
        final DispatcherSpec spec = new DispatcherSpec("PEGA", "pega_dispatch", Map.of("input", "json",
                "json_layout", "lines"), "sourcetype", "PEGA helpers", rows, groups, Map.of())
                .withEvents("4.1.0", "event-logging:3 file://event-logging-v4.1.0.xsd");
        final String saved = DispatcherXslt.generate(spec, null, null).xslt();
        Mockito.when(stepping.documentAccess.read(eq(new DocRef(XsltDoc.TYPE, "x")), eq(XsltDoc.class)))
                .thenReturn(Optional.of(XsltDoc.builder().uuid("x").name("PEGA dispatcher").data(saved)
                        .description(KeptMapping.withBlock("", DispatcherXslt.SPEC_BLOCK, spec.toJson())).build()));
        Mockito.when(stepping.checkXslt.check(anyString(), any(), any())).thenReturn(Map.of("ok", true, "errors",
                List.of()));
        stepping.records(1, "<Events xmlns=\"event-logging:3\"><Event/></Events>");
        // A new group, the integration's, unsaved: routed to its mode as it will be, inlined in place of an import.
        final String draft = XsltGenerator.generate(TranslationMapping.parse(integration()).withModule(
                new SourceTypeModule(SourceTypeModule.GROUP, "PEGA integration", "pega_integration",
                        List.of("PegaIntegration"), null, "PEGA helpers", List.of(), null, "sourcetype")),
                TranslationWorld.SCHEMA, TranslationWorld.VERSION).xslt();

        new StepSampleTool(stepping.stepping()).execute(BuildToolCalls.call(StepSampleTool.NAME,
                JsonUtil.writeValueAsString(Map.of("pipeline_uuid", "p-1", "stream_ids", List.of(7),
                        "draft_code", Map.of("translationFilter", draft)), false)), stepping.context());

        final String stepped = stepping.opened.getFirst().getValue().get("translationFilter");
        assertThat(stepped).contains("<mcp:module kind=\"dispatcher\"", "<xsl:import href=\"" + groupHref + "\"/>",
                        "<xsl:when test=\"$source_type = ('PegaIntegration')\">",
                        "<xsl:template match=\"*\" mode=\"pega_integration\"")
                .doesNotContain("kind=\"group\"");
    }

    // --- fixtures ---

    private static Map<String, Object> args(final Map<String, Object> mapping,
                                            final String group,
                                            final List<String> sourceTypes) {
        final Map<String, Object> args = new LinkedHashMap<>();
        args.put("mapping", mapping);
        args.put("schema_version", TranslationWorld.VERSION);
        args.put("build", BUILD);
        args.put("feeds", List.of("PEGA"));
        args.put("group", group);
        args.put("source_types", sourceTypes);
        return args;
    }

    private static Map<String, Object> security() {
        return mapping("""
                {"input": "json", "json_layout": "lines", "json_fields": ["message"],
                 "common": %s,
                 "ignored_fields": ["app"],
                 "events": [{"name": "login", "when": [{"field": "message.eventType", "equals": "login"}],
                             "fields": [{"path": "EventDetail/TypeId", "value": "Login"},
                                        {"path": "EventDetail/Authenticate/Action", "value": "Logon"},
                                        {"path": "EventDetail/Authenticate/User/Id", "field": "message.operatorId"}]},
                            {"name": "other",
                             "fields": [{"path": "EventDetail/TypeId", "field": "message.eventType"},
                                        {"path": "EventDetail/Unknown/Data", "data_name": "operator",
                                         "field": "message.operatorId"}]}]}""".formatted(WRAPPER));
    }

    private static Map<String, Object> integration() {
        return mapping("""
                {"input": "json", "json_layout": "lines", "xml_fields": ["soap"],
                 "extract": [{"field": "message", "regex": "^(Request|Response): (.*)$", "names": ["direction", "soap"],
                              "flags": "s"}],
                 "common": %s,
                 "ignored_fields": ["app"],
                 "events": [{"name": "call",
                             "fields": [{"path": "EventDetail/TypeId", "field": "direction"},
                                        {"path": "EventDetail/Unknown/Data", "data_name": "correlation",
                                         "field": "soap/Envelope/Header/CorrelationId"}]}]}"""
                .formatted(WRAPPER.replace("\"PEGA\"", "\"PEGA Integration\"")));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> mapping(final String json) {
        return (Map<String, Object>) ToolJson.loads(json);
    }

    private static SourceTypeRegistries.Found registry() {
        final Map<String, Object> state = new LinkedHashMap<>();
        state.put("feed", "PEGA");
        state.put("field", "sourcetype");
        state.put("environment", "Prod");
        state.put("system_name", "PEGA");
        state.put("rows", List.of(row("PegaSecurityEvent", "PEGA Security"), row("PegaIntegration", null),
                row("PegaDB", null)));
        return new SourceTypeRegistries.Found(new SurveyRecord("r", "PEGA source types",
                SourceTypeRegistry.render(state)), false, state);
    }

    private static Map<String, Object> row(final String name, final String systemName) {
        final Map<String, Object> row = new LinkedHashMap<>();
        row.put("name", name);
        row.put("match", "value");
        row.put("value", name);
        row.put("system_name", systemName);
        row.put("status", SourceTypeRegistry.PARKED);
        return row;
    }

    private static Map<String, Object> rowNamed(final String registry, final String name) {
        return JsonValues.maps(SourceTypeRegistry.read(registry).get("rows")).stream()
                .filter(row -> name.equals(row.get("name")))
                .findFirst()
                .orElseThrow();
    }

    // The build's XSLTs, by name.
    private Map<String, String> xsltsInBuild() {
        final Map<String, String> out = new LinkedHashMap<>();
        world.world.guard().folderContents(BUILD).stream()
                .filter(doc -> XsltDoc.TYPE.equals(doc.type()))
                .forEach(doc -> out.put(doc.name(), doc.uuid()));
        return out;
    }

    private static TranslationMapping keptMapping(final XsltDoc xslt) {
        return TranslationMapping.parse(KeptMapping.readMapping(xslt.getDescription()).orElseThrow().payload()
                .get("mapping"));
    }
}
