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
import stroom.ai.impl.tools.ToolRefusal;
import stroom.ai.impl.tools.ToolResult;
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
import stroom.util.shared.HasData;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

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
    // The saved security group a stepped pipeline's dispatcher imports.
    private static final String STEPPED_GROUP = "g0000000-0000-0000-0000-000000000001";
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
        // The registry as last written, as the build holds it.
        Mockito.when(world.registries.registry(eq(BUILD), eq("PEGA"))).thenAnswer(inv -> Optional.of(written.isEmpty()
                ? registry()
                : new SourceTypeRegistries.Found(new SurveyRecord("r", "PEGA source types", written.getLast()), false,
                        SourceTypeRegistry.read(written.getLast()))));
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
    void aGroupWithAPipelineOfItsOwnHasItsOwnDispatcherAndTheFeedsPassesItsRecordsOver() {
        world.call(tool, args(security(), "PEGA security", List.of("PegaSecurityEvent")));
        final Map<String, Object> own = new LinkedHashMap<>(args(integration(), "PEGA integration",
                List.of("PegaIntegration")));
        own.put("pipeline", "own");
        own.put("own_output_feed", true);
        own.put("references", List.of(Map.of("feed", "PEGA-HOSTS", "loader_pipeline", "Reference Loader")));

        final Map<String, Object> saved = world.call(tool, own);

        assertThat(saved.get("problems")).as("%s", saved).isEqualTo(List.of());
        final Map<String, Object> group = JsonValues.mapOrEmpty(saved.get("group"));
        assertThat(String.valueOf(group.get("group_next"))).contains("create_feed", "group='PEGA integration'",
                "create_pipeline");
        final Map<String, String> docs = xsltsInBuild();
        assertThat(docs).containsKeys("PEGA dispatcher", "PEGA PEGA integration dispatcher");
        assertThat(JsonValues.mapOrEmpty(group.get("dispatcher"))).containsEntry("uuid",
                docs.get("PEGA PEGA integration dispatcher"));
        // The feed's pipeline routes the security group alone, and passes the other over as handled elsewhere.
        final DispatcherSpec main = spec(docs.get("PEGA dispatcher"));
        assertThat(main.groups()).extracting(DispatcherSpec.Group::name).containsExactly("PEGA security");
        assertThat(main.rows()).filteredOn(row -> "PegaIntegration".equals(row.name())).singleElement()
                .satisfies(row -> assertThat(row.handledBy()).contains("PEGA PEGA integration dispatcher"));
        // The group's own pipeline routes it alone.
        final DispatcherSpec ownSpec = spec(docs.get("PEGA PEGA integration dispatcher"));
        assertThat(ownSpec.mode()).isEqualTo("pega_pega_integration_dispatch");
        assertThat(ownSpec.groups()).extracting(DispatcherSpec.Group::name).containsExactly("PEGA integration");
        assertThat(ownSpec.rows()).filteredOn(row -> "PegaSecurityEvent".equals(row.name())).singleElement()
                .satisfies(row -> assertThat(row.handledBy()).contains("PEGA dispatcher"));
        // The registry says where each is translated: the group in a pipeline of its own, its output feed to make,
        // its reference data kept.
        final Map<String, Object> row = rowNamed(written.getLast(), "PegaIntegration");
        assertThat(JsonValues.mapOrEmpty(row.get("pipeline"))).containsEntry("own", true);
        assertThat(row).containsEntry("output_feed", "");
        assertThat(JsonValues.maps(row.get("references"))).singleElement()
                .satisfies(reference -> assertThat(reference).containsEntry("feed", "PEGA-HOSTS"));
        assertThat(written.getLast()).contains("a pipeline of its own (to be made)");
        assertThat(JsonValues.mapOrEmpty(rowNamed(written.getLast(), "PegaSecurityEvent").get("pipeline")))
                .containsEntry("own", false);

        // Its pipeline made: the registry names it, and the feed's dispatcher says its records are handled by it.
        final List<String> named = world.groups.runBy(world.context(), BUILD, docs.get(
                "PEGA PEGA integration dispatcher"), "PEGA-Integration-Events");
        assertThat(named).containsExactly("PegaIntegration");
        assertThat(world.xslt(docs.get("PEGA dispatcher")).getData())
                .contains(DispatcherXslt.HANDLED_BY.strip() + " pipeline 'PEGA-Integration-Events'");
        final Optional<SourceTypeGroups.Routed> routed = world.groups.routedBy(BUILD,
                docs.get("PEGA PEGA integration dispatcher"));
        assertThat(routed).hasValueSatisfying(found -> {
            assertThat(found.groups()).containsExactly("PEGA integration");
            assertThat(found.outputFeed()).hasValue("");
            assertThat(found.references()).hasSize(1);
        });
    }

    @Test
    void aNewGroupIsPlacedOnlyAsTheUserChose() {
        final Map<String, Object> unasked = new LinkedHashMap<>(args(security(), "PEGA security",
                List.of("PegaSecurityEvent")));
        unasked.remove("pipeline");

        final Map<String, Object> refused = world.call(tool, unasked);

        assertThat(String.valueOf(refused.get("error"))).contains("ask the user which pipeline handles it",
                "Nothing was saved");
        assertThat(xsltsInBuild()).isEmpty();
        // A new version only of a pipeline in production: the feed has none yet.
        unasked.put("pipeline", "new_version");
        assertThat(String.valueOf(world.call(tool, unasked).get("error"))).contains("isn't a choice",
                "no pipeline in production");
    }

    @Test
    void promotionPointsTheBuildsModulesAtTheOriginalsOfItsWorkingCopies() {
        world.call(tool, args(security(), "PEGA security", List.of("PegaSecurityEvent")));
        final Map<String, String> docs = xsltsInBuild();
        final List<Map<String, Object>> listed = List.of(
                doc(docs.get("PEGA security"), "PEGA security", "o-security"),
                doc(docs.get("PEGA helpers"), "PEGA helpers", "o-helpers"),
                doc(docs.get("PEGA common"), "PEGA common", null),
                doc(docs.get("PEGA dispatcher"), "PEGA dispatcher", null));

        final List<String> done = world.groups.toOriginals(world.context(), BUILD, listed, Set.of());

        assertThat(done).anySatisfy(line -> assertThat(line).contains("regenerated dispatcher 'PEGA dispatcher'"));
        final XsltDoc dispatcher = world.xslt(docs.get("PEGA dispatcher"));
        assertThat(dispatcher.getData()).contains("uuid='o-security'", "uuid='o-helpers'")
                .doesNotContain("uuid='" + docs.get("PEGA security") + "'");
        assertThat(spec(docs.get("PEGA dispatcher")).groups()).singleElement()
                .satisfies(group -> assertThat(group.href()).contains("o-security"));
        // The common module (new) imports the helpers' original; the group's kept mapping names them too.
        assertThat(world.xslt(docs.get("PEGA common")).getData()).contains("uuid='o-helpers'");
        assertThat(keptMapping(world.xslt(docs.get("PEGA security"))).module().helpers()).contains("o-helpers");
    }

    @Test
    void sourceTypesAddedToAGroupWithAPipelineOfItsOwnJoinItsPipeline() {
        world.call(tool, args(security(), "PEGA security", List.of("PegaSecurityEvent")));
        final Map<String, Object> own = new LinkedHashMap<>(args(integration(), "PEGA integration",
                List.of("PegaIntegration")));
        own.put("pipeline", "own");
        own.put("own_output_feed", true);
        world.call(tool, own);
        // Its output feed made (create_feed group=).
        written.add(withRows(written.getLast(), row -> {
            if ("PegaIntegration".equals(row.get("name"))) {
                row.put("output_feed", "PEGA-INTEGRATION-EVENTS");
            }
        }));
        final Map<String, String> docs = xsltsInBuild();
        // The group grows: PegaDB added to it, with no placement asked (the group has its pipeline).
        final Map<String, Object> grown = new LinkedHashMap<>(args(integration(), "PEGA integration",
                List.of("PegaIntegration", "PegaDB")));
        grown.remove("pipeline");

        final Map<String, Object> saved = world.call(tool, grown);

        assertThat(saved.get("problems")).as("%s", saved).isEqualTo(List.of());
        final Map<String, Object> db = rowNamed(written.getLast(), "PegaDB");
        assertThat(JsonValues.mapOrEmpty(db.get("pipeline"))).containsEntry("own", true);
        assertThat(JsonValues.mapOrEmpty(JsonValues.mapOrEmpty(db.get("pipeline")).get("dispatcher")))
                .containsEntry("uuid", docs.get("PEGA PEGA integration dispatcher"));
        assertThat(db).containsEntry("output_feed", "PEGA-INTEGRATION-EVENTS");
        // Routed by the group's own dispatcher; the feed's passes it over as handled there.
        assertThat(spec(docs.get("PEGA PEGA integration dispatcher")).groups()).singleElement()
                .satisfies(group -> assertThat(group.sourceTypes()).containsExactly("PegaIntegration", "PegaDB"));
        assertThat(spec(docs.get("PEGA dispatcher")).groups()).extracting(DispatcherSpec.Group::name)
                .containsExactly("PEGA security");
        assertThat(spec(docs.get("PEGA dispatcher")).rows()).filteredOn(row -> "PegaDB".equals(row.name()))
                .singleElement().satisfies(row -> assertThat(row.handledBy()).contains("integration dispatcher"));
        assertThat(JsonValues.strings(JsonValues.mapOrEmpty(saved.get("group")).get("notes")))
                .anySatisfy(note -> assertThat(note).contains("PegaDB", "PEGA PEGA integration dispatcher",
                        "PEGA-INTEGRATION-EVENTS"));
    }

    @Test
    void aTranslatedRowWithNoPipelineToJoinIsPlacedNotGivenADispatcherNoPipelineRuns() {
        final Map<String, Object> own = new LinkedHashMap<>(args(integration(), "PEGA integration",
                List.of("PegaIntegration")));
        own.put("pipeline", "own");
        world.call(tool, own);
        // A registry from before pipelines were placed: a translated row of another group, in no pipeline.
        written.add(withRows(written.getLast(), row -> {
            if ("PegaSecurityEvent".equals(row.get("name"))) {
                row.put("status", SourceTypeRegistry.TRANSLATED);
                row.put("group", "PEGA security");
            }
        }));
        final Map<String, Object> unplaced = new LinkedHashMap<>(args(security(), "PEGA security",
                List.of("PegaSecurityEvent")));
        unplaced.remove("pipeline");

        final Map<String, Object> refused = world.call(tool, unplaced);

        assertThat(String.valueOf(refused.get("error"))).contains("ask the user which pipeline handles it");
        assertThat(xsltsInBuild()).doesNotContainKey("PEGA dispatcher");
    }

    @Test
    void aSourceTypeAnotherGroupTranslatesIsRefused() {
        world.call(tool, args(security(), "PEGA security", List.of("PegaSecurityEvent")));

        final Map<String, Object> refused = world.call(tool, args(integration(), "PEGA integration",
                List.of("PegaIntegration", "PegaSecurityEvent")));

        assertThat(String.valueOf(refused.get("error"))).contains("PegaSecurityEvent", "'PEGA security'");
        assertThat(rowNamed(written.getLast(), "PegaSecurityEvent")).containsEntry("group", "PEGA security");
        assertThat(xsltsInBuild()).doesNotContainKey("PEGA integration");
    }

    @Test
    void aGroupWhoseModeMeetsAnothersIsRefused() {
        world.call(tool, args(security(), "PEGA security", List.of("PegaSecurityEvent")));

        // Styled to the same mode, and to one starting with it.
        for (final String name : List.of("PEGA-Security", "PEGA security mail")) {
            final Map<String, Object> refused = world.call(tool, args(integration(), name,
                    List.of("PegaIntegration")));
            assertThat(String.valueOf(refused.get("error"))).as(name).contains("'" + name + "'", "'PEGA security'");
        }
        assertThat(xsltsInBuild()).containsOnlyKeys("PEGA security", "PEGA common", "PEGA helpers",
                "PEGA dispatcher");
    }

    @Test
    void aGroupBuiltFromTheRegistryBesideTheFeedIsRefusedWhileAnotherBuildIsOpenOnIt() {
        Mockito.when(world.registries.registry(eq(BUILD), eq("PEGA"))).thenReturn(Optional.of(
                new SourceTypeRegistries.Found(registry().doc(), true, registry().state())));
        Mockito.doThrow(new ToolRefusal("build 'other' is open on it"))
                .when(world.registries).refuseOpenBuild("PEGA", BUILD, true);

        final Map<String, Object> refused = world.call(tool, args(security(), "PEGA security",
                List.of("PegaSecurityEvent")));

        assertThat(String.valueOf(refused.get("error"))).contains("build 'other' is open on it");
        assertThat(xsltsInBuild()).isEmpty();
    }

    @Test
    void theEventsQuestionIsAskedOnlyForAPipelineOfItsOwn() {
        world.forms = true;
        final String shared = "The feed's pipeline, shared by its groups (reads each record once)";
        world.answers.put("Which pipeline handles group", shared);
        world.answers.put("Source type group 'PEGA security'", shared);
        final Map<String, Object> asked = new LinkedHashMap<>(args(security(), "PEGA security",
                List.of("PegaSecurityEvent")));
        asked.remove("pipeline");

        final Map<String, Object> saved = world.call(tool, asked);

        assertThat(saved.get("problems")).as("%s", saved).isEqualTo(List.of());
        assertThat(world.formsShown).noneSatisfy(form -> assertThat(String.valueOf(form))
                .contains("Where do its Events go"));
        assertThat(world.formsShown).anySatisfy(form -> assertThat(String.valueOf(form))
                .contains("Which pipeline handles group"));
    }

    @Test
    void anOwnOutputFeedGivenForASharedPipelineIsSaidToBeIgnored() {
        final Map<String, Object> given = new LinkedHashMap<>(args(security(), "PEGA security",
                List.of("PegaSecurityEvent")));
        given.put("own_output_feed", true);

        final Map<String, Object> saved = world.call(tool, given);

        assertThat(saved.get("problems")).as("%s", saved).isEqualTo(List.of());
        assertThat(rowNamed(written.getLast(), "PegaSecurityEvent")).doesNotContainKey("output_feed");
        assertThat(JsonValues.strings(JsonValues.mapOrEmpty(saved.get("group")).get("notes")))
                .anySatisfy(note -> assertThat(note).contains("own_output_feed", "ignored"));
    }

    @Test
    void promotionLeavesAWorkingCopyTheUserKeptAsItIs() {
        world.call(tool, args(security(), "PEGA security", List.of("PegaSecurityEvent")));
        final Map<String, String> docs = xsltsInBuild();
        // The group's original, in production, as the kept copy's: it has the mode the dispatcher routes to.
        final String groupCode = world.xslt(docs.get("PEGA security")).getData();
        final String commonCode = world.xslt(docs.get("PEGA common")).getData();
        world.world.add(XsltDoc.TYPE, "PEGA security", world.world.folder("System/Feeds/PEGA").uuid,
                "o-security");
        world.world.docs.put("o-security", XsltDoc.builder().uuid("o-security").name("PEGA security")
                .data(groupCode).build());
        final List<Map<String, Object>> listed = List.of(
                doc(docs.get("PEGA security"), "PEGA security", "o-security"),
                doc(docs.get("PEGA helpers"), "PEGA helpers", "o-helpers"),
                doc(docs.get("PEGA common"), "PEGA common", null),
                doc(docs.get("PEGA dispatcher"), "PEGA dispatcher", null));

        assertThat(world.groups.keptProblems(BUILD, listed, Set.of(docs.get("PEGA security"))))
                .isEmpty();
        world.groups.toOriginals(world.context(), BUILD, listed, Set.of(docs.get("PEGA security")));

        // The kept copy is as it was; the modules promoted import the originals.
        assertThat(world.xslt(docs.get("PEGA security")).getData()).isEqualTo(groupCode);
        assertThat(world.xslt(docs.get("PEGA common")).getData()).isNotEqualTo(commonCode).contains("o-helpers");
        assertThat(world.xslt(docs.get("PEGA dispatcher")).getData()).contains("uuid='o-security'");

        // An original lacking the mode the dispatcher routes to: promotion would break it, so it is refused.
        world.world.docs.put("o-security", XsltDoc.builder().uuid("o-security").name("PEGA security")
                .data(groupCode.replace("pega_security", "pega_old")).build());
        assertThat(world.groups.keptProblems(BUILD, listed, Set.of(docs.get("PEGA security"))))
                .singleElement().satisfies(problem -> assertThat(problem).contains("PEGA dispatcher",
                        "PEGA security", "pega_security"));
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

    @Test
    void aGroupsDraftIsComparedThroughTheFeedsDispatcher() {
        final SteppingWorld stepping = steppingThroughADispatcher();
        stepping.records(1, "<Events xmlns=\"event-logging:3\"><Event/></Events>");

        new CompareOutputsTool(stepping.stepping(), Mockito.mock(EventLocations.class)).execute(
                BuildToolCalls.call(CompareOutputsTool.NAME, JsonUtil.writeValueAsString(Map.of("pipeline_uuid",
                        "p-1", "stream_ids", List.of(7), "draft_code", Map.of("translationFilter", securityDraft())),
                        false)), stepping.context());

        assertThat(stepping.opened).extracting(Map.Entry::getValue)
                .anySatisfy(code -> assertThat(String.valueOf(code)).contains("<mcp:module kind=\"dispatcher\"",
                        "<xsl:template match=\"*\" mode=\"pega_security\"").doesNotContain("kind=\"group\""));
    }

    @Test
    void aGroupsFixIsProvenThroughTheFeedsDispatcherAndDiffedAgainstTheSavedGroup() {
        final SteppingWorld stepping = steppingThroughADispatcher();
        Mockito.when(stepping.documentAccess.read(any(DocRef.class), eq(HasData.class)))
                .thenAnswer(inv -> Optional.of(XsltDoc.builder().uuid("any").name("any").data(
                        STEPPED_GROUP.equals(((DocRef) inv.getArgument(0)).getUuid())
                                ? "<old-group/>"
                                : "<dispatcher/>").build()));
        Mockito.when(stepping.documentAccess.read(eq(new DocRef(XsltDoc.TYPE, STEPPED_GROUP)), eq(XsltDoc.class)))
                .thenReturn(Optional.of(XsltDoc.builder().uuid(STEPPED_GROUP).name("PEGA security")
                        .data("<old-group/>").build()));
        final CompareOutputsTool compare = Mockito.mock(CompareOutputsTool.class);
        final Map<String, Object> compared = new LinkedHashMap<>();
        compared.put("records_compared", 1);
        compared.put("records_changed", 1);
        compared.put("fields_changed", List.of(Map.of("path", "Event/X", "added", 1, "removed", 0, "changed", 0)));
        Mockito.when(compare.compareLocated(any(), any(), any(), Mockito.anyList())).thenReturn(compared);
        final StepSampleTool stepSample = Mockito.mock(StepSampleTool.class);
        Mockito.when(stepSample.stepSample(any(), any(), any(), Mockito.anyList(), any(), any(), any()))
                .thenReturn(Map.of("verdict", "clean", "groups", List.of()));
        final SummariseFixTool fix = new SummariseFixTool(stepping.stepping(), Mockito.mock(EventLocations.class),
                compare, stepSample, Mockito.mock(StepRecordsTool.class), stepping.documentAccess);

        final ToolResult result = fix.execute(BuildToolCalls.call(SummariseFixTool.NAME,
                JsonUtil.writeValueAsString(Map.of("pipeline_uuid", "p-1", "element", "translationFilter", "draft",
                        securityDraft(), "stream_ids", List.of(7), "expected_paths", List.of("Event/X")), false)),
                stepping.context());

        assertThat(result.isError()).as(result.text()).isFalse();
        final ArgumentCaptor<CompareOutputsTool.Request> request = ArgumentCaptor.forClass(
                CompareOutputsTool.Request.class);
        Mockito.verify(compare).compareLocated(any(), any(), request.capture(), Mockito.anyList());
        assertThat(request.getValue().draftCode().get("translationFilter")).contains(
                "<mcp:module kind=\"dispatcher\"", "mode=\"pega_security\"");
        final Map<String, Object> said = BuildToolCalls.json(result.text());
        assertThat(String.valueOf(said.get("diff"))).contains("-<old-group/>").doesNotContain("<dispatcher/>");
    }

    // --- fixtures ---

    // A stepping world whose pipeline p-1 runs the feed's dispatcher (x), routing the security group by UUID.
    private static SteppingWorld steppingThroughADispatcher() {
        final SteppingWorld stepping = new SteppingWorld();
        final String groupHref = "uuid='" + STEPPED_GROUP + "', name='PEGA security'";
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
        return stepping;
    }

    // The security group's draft, as build_translation_xslt writes it.
    private static String securityDraft() {
        return XsltGenerator.generate(TranslationMapping.parse(security()).withModule(
                new SourceTypeModule(SourceTypeModule.GROUP, "PEGA security", "pega_security",
                        List.of("PegaSecurityEvent"), null, "PEGA helpers", List.of(), null, "sourcetype")),
                TranslationWorld.SCHEMA, TranslationWorld.VERSION).xslt();
    }

    // The spec kept with a dispatcher.
    private DispatcherSpec spec(final String uuid) {
        return DispatcherSpec.fromJson(KeptMapping.readBlock(world.xslt(uuid).getDescription(),
                DispatcherXslt.SPEC_BLOCK).orElseThrow()).orElseThrow();
    }

    // The registry's text with each row changed so.
    private static String withRows(final String registry, final Consumer<Map<String, Object>> change) {
        final Map<String, Object> state = SourceTypeRegistry.read(registry);
        final List<Map<String, Object>> rows = new ArrayList<>();
        for (final Map<String, Object> row : JsonValues.maps(state.get("rows"))) {
            final Map<String, Object> copy = new LinkedHashMap<>(row);
            change.accept(copy);
            rows.add(copy);
        }
        state.put("rows", rows);
        return SourceTypeRegistry.render(state);
    }

    // A build document as promotion lists it.
    private static Map<String, Object> doc(final String uuid, final String name, final String copyOf) {
        final Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("type", XsltDoc.TYPE);
        doc.put("uuid", uuid);
        doc.put("name", name);
        doc.put("working_copy_of", copyOf);
        return doc;
    }

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
        // No form can be shown here: the user's choice, as the agent passes it.
        args.put("pipeline", "shared");
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
