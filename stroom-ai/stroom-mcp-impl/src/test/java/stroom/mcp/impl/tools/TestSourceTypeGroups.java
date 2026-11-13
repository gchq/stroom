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
import stroom.ai.impl.tools.generation.DumpMode;
import stroom.ai.impl.tools.generation.HelpersXslt;
import stroom.ai.impl.tools.generation.ModuleChecks;
import stroom.ai.impl.tools.generation.SourceTypeModule;
import stroom.ai.impl.tools.generation.TranslationMapping;
import stroom.ai.impl.tools.generation.XsltGenerator;
import stroom.ai.impl.tools.sample.FeedSampling.SurveyRecord;
import stroom.ai.impl.tools.sample.SourceTypeRegistry;
import stroom.ai.impl.tools.sample.ToolJson;
import stroom.docref.DocRef;
import stroom.documentation.shared.DocumentationDoc;
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

/// `build_translation_xslt group=`: a source type group of an aggregated feed saved with its mapping as the XSLT of
/// a pipeline of its own, the feed's common module and helpers made from the first group's mapping (a chain: the
/// group imports the common module, which imports the helpers), and the registry's rows set translated; a second
/// group reusing the common module, and a shared call of its own added to it; a group regenerated from its kept
/// mapping alone; and an unsaved group stepped, compared and proven as draft code for its pipeline's XSLT.
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
        // The build holds the feed's registry, as record_source_types made it.
        world.world.inBuild(BUILD, DocumentationDoc.TYPE, "PEGA source types", "r");
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
    void aGroupIsSavedAsItsPipelinesXsltWithTheFeedsCommonModuleAndHelpers() {
        final Map<String, Object> saved = world.call(tool, args(security(), "PEGA security",
                List.of("PegaSecurityEvent")));

        assertThat(saved.get("problems")).as("%s", saved).isEqualTo(List.of());
        final Map<String, Object> group = JsonValues.mapOrEmpty(saved.get("group"));
        assertThat(group).containsEntry("mode", "pega_security").containsEntry("system_name", "PEGA Security")
                .doesNotContainKeys("dispatcher", "dispatchers");
        assertThat(JsonValues.strings(group.get("common_parts"))).containsExactly("EventTime", "EventSource/System",
                "EventSource/Generator", "EventSource/Device");
        // The build holds the group, the feed's common module and helpers: no dispatcher.
        final Map<String, String> docs = xsltsInBuild();
        assertThat(docs).containsOnlyKeys("PEGA security", "PEGA common", "PEGA helpers");
        // The group keeps its mapping, its module importing the build's common module by UUID (which imports the
        // helpers), with the registry's rows to tell its records by; the wrapper's entries went to the common module.
        final XsltDoc groupXslt = world.xslt(docs.get("PEGA security"));
        final TranslationMapping kept = keptMapping(groupXslt);
        assertThat(kept.module().kind()).isEqualTo(SourceTypeModule.GROUP);
        assertThat(kept.module().common()).isEqualTo("uuid='" + docs.get("PEGA common") + "', name='PEGA common'");
        assertThat(kept.module().helpers()).isNull();
        assertThat(kept.module().rows()).extracting(SourceTypeModule.Row::name).containsExactly("PegaSecurityEvent",
                "PegaIntegration", "PegaDB");
        assertThat(kept.common()).isEmpty();
        assertThat(ModuleChecks.hrefs(groupXslt.getData())).containsExactly("uuid='" + docs.get("PEGA common")
                                                                            + "', name='PEGA common'");
        assertThat(groupXslt.getData()).contains("<xsl:template match=\"/\">", "<Events Version=",
                "mode=\"pega_security\"", "$value = 'PegaDB'");
        final XsltDoc commonXslt = world.xslt(docs.get("PEGA common"));
        assertThat(ModuleChecks.hrefs(commonXslt.getData())).containsExactly("uuid='" + docs.get("PEGA helpers")
                                                                             + "', name='PEGA helpers'");
        final TranslationMapping common = keptMapping(commonXslt);
        assertThat(common.module().kind()).isEqualTo(SourceTypeModule.COMMON);
        // The feed's shared decisions replace the mapping's: the registry's Environment.
        assertThat(ToolJson.dumps(common.toJson(DumpMode.COMPACT)))
                .contains("\"value\": \"Prod\"").doesNotContain("Test");
        assertThat(world.xslt(docs.get("PEGA helpers")).getData()).isEqualTo(HelpersXslt.generate());
        // The registry's row translated, by the group and its XSLT.
        final Map<String, Object> row = rowNamed(written.getLast(), "PegaSecurityEvent");
        assertThat(row).containsEntry("status", SourceTypeRegistry.TRANSLATED).containsEntry("group",
                "PEGA security");
        assertThat(JsonValues.mapOrEmpty(row.get("group_xslt"))).containsEntry("uuid", docs.get("PEGA security"));
        assertThat(JsonValues.mapOrEmpty(row.get("pipeline"))).doesNotContainKey("dispatcher");
        assertThat(rowNamed(written.getLast(), "PegaDB")).containsEntry("status", SourceTypeRegistry.PARKED);
    }

    @Test
    void aSecondGroupLeavesTheWrappersPartsToTheCommonModule() {
        world.call(tool, args(security(), "PEGA security", List.of("PegaSecurityEvent")));
        final Map<String, String> before = xsltsInBuild();
        final String commonCode = world.xslt(before.get("PEGA common")).getData();

        final Map<String, Object> saved = world.call(tool, args(integration(), "PEGA integration",
                List.of("PegaIntegration")));

        assertThat(saved.get("problems")).as("%s", saved).isEqualTo(List.of());
        final Map<String, String> docs = xsltsInBuild();
        assertThat(docs.get("PEGA common")).isEqualTo(before.get("PEGA common"));
        assertThat(world.xslt(docs.get("PEGA common")).getData()).isEqualTo(commonCode);
        final TranslationMapping kept = keptMapping(world.xslt(docs.get("PEGA integration")));
        // Its wrapper entries are the common module's (its own System Name aside): left to it.
        assertThat(kept.common()).isEmpty();
        assertThat(ModuleChecks.hrefs(world.xslt(docs.get("PEGA integration")).getData())).singleElement()
                .satisfies(href -> assertThat(href).contains(docs.get("PEGA common")));

        // Regenerated from its kept mapping alone, by uuid: built again as the group it is.
        final Map<String, Object> again = world.call(tool, Map.of("uuid", docs.get("PEGA integration"),
                "schema_version", TranslationWorld.VERSION, "feeds", List.of("PEGA")));
        assertThat(again.get("problems")).as("%s", again).isEqualTo(List.of());
        assertThat(JsonValues.mapOrEmpty(again.get("group"))).containsEntry("mode", "pega_integration");
        assertThat(xsltsInBuild()).hasSize(4);
    }

    @Test
    void aSharedCallOfALaterGroupIsAddedToTheCommonModuleNotImportedByTheGroup() {
        world.world.add(XsltDoc.TYPE, "Event Meta", world.world.folder("System/Shared").uuid, "u-meta");
        world.world.docs.put("u-meta", XsltDoc.builder().uuid("u-meta").name("Event Meta")
                .data("<xsl:stylesheet xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\" version=\"3.0\">"
                      + "<xsl:template name=\"event-meta\"><Meta/></xsl:template></xsl:stylesheet>").build());
        world.call(tool, args(security(), "PEGA security", List.of("PegaSecurityEvent")));
        final String commonUuid = xsltsInBuild().get("PEGA common");
        final Map<String, Object> calling = new LinkedHashMap<>(integration());
        calling.put("shared", List.of(Map.of("href", SourceTypeGroups.href("u-meta", "Event Meta"), "template",
                "event-meta", "at", "Meta")));

        final Map<String, Object> saved = world.call(tool, args(calling, "PEGA integration",
                List.of("PegaIntegration")));

        assertThat(saved.get("problems")).as("%s", saved).isEqualTo(List.of());
        // The common module saved again, in place, importing the library; the group imports only the common module.
        assertThat(xsltsInBuild().get("PEGA common")).isEqualTo(commonUuid);
        assertThat(ModuleChecks.hrefs(world.xslt(commonUuid).getData()))
                .anySatisfy(href -> assertThat(href).contains("u-meta"));
        final String integrationCode = world.xslt(xsltsInBuild().get("PEGA integration")).getData();
        assertThat(ModuleChecks.hrefs(integrationCode)).singleElement()
                .satisfies(href -> assertThat(href).contains(commonUuid));
        assertThat(keptMapping(world.xslt(xsltsInBuild().get("PEGA integration"))).shared()).isEmpty();
        assertThat(JsonValues.strings(JsonValues.mapOrEmpty(saved.get("group")).get("notes")))
                .anySatisfy(note -> assertThat(note).contains("event-meta", "added to the feed's common module"));
        assertThat(JsonValues.strings(JsonValues.mapOrEmpty(saved.get("group")).get("common_parts")))
                .contains("Meta");
    }

    @Test
    void aGroupWithAnOutputFeedOfItsOwnIsSaidWhatToMakeNext() {
        world.call(tool, args(security(), "PEGA security", List.of("PegaSecurityEvent")));
        final Map<String, Object> own = new LinkedHashMap<>(args(integration(), "PEGA integration",
                List.of("PegaIntegration")));
        own.put("own_output_feed", true);
        own.put("references", List.of(Map.of("feed", "PEGA-HOSTS", "loader_pipeline", "Reference Loader")));

        final Map<String, Object> saved = world.call(tool, own);

        assertThat(saved.get("problems")).as("%s", saved).isEqualTo(List.of());
        final Map<String, Object> group = JsonValues.mapOrEmpty(saved.get("group"));
        assertThat(String.valueOf(group.get("group_next"))).contains("create_feed", "group='PEGA integration'",
                "create_pipeline", "the group's XSLT", "'PEGA PEGA integration'");
        assertThat(xsltsInBuild()).containsOnlyKeys("PEGA security", "PEGA integration", "PEGA common",
                "PEGA helpers");
        // The registry says where it goes: its output feed to make, its reference data kept.
        // The registry says where it goes: its own pipeline to make, its output feed to make, its reference data
        // kept; the other group's Events go into the feed's.
        final Map<String, Object> row = rowNamed(written.getLast(), "PegaIntegration");
        assertThat(JsonValues.mapOrEmpty(row.get("pipeline"))).containsOnly(Map.entry("name",
                "PEGA PEGA integration"));
        assertThat(row).containsEntry("output_feed", "");
        assertThat(JsonValues.maps(row.get("references"))).singleElement()
                .satisfies(reference -> assertThat(reference).containsEntry("feed", "PEGA-HOSTS"));
        final Map<String, Object> security = rowNamed(written.getLast(), "PegaSecurityEvent");
        assertThat(JsonValues.mapOrEmpty(security.get("pipeline"))).containsOnly(Map.entry("name",
                "PEGA PEGA security"));
        assertThat(security).doesNotContainKey("output_feed");
    }

    @Test
    void aNewGroupIsPlacedOnlyAsTheUserChose() {
        final Map<String, Object> unasked = new LinkedHashMap<>(args(security(), "PEGA security",
                List.of("PegaSecurityEvent")));
        unasked.remove("own_output_feed");

        final Map<String, Object> refused = world.call(tool, unasked);

        assertThat(String.valueOf(refused.get("error"))).contains("is new", "'PEGA PEGA security'",
                "Ask the user where its Events go", "own_output_feed", "Nothing was saved");
        assertThat(xsltsInBuild()).isEmpty();
        // Which pipeline is no longer asked: a new group has no pipeline in production to change.
        unasked.put("own_output_feed", false);
        unasked.put("pipeline", "new_version");
        final Map<String, Object> saved = world.call(tool, unasked);
        assertThat(saved.get("problems")).as("%s", saved).isEqualTo(List.of());
        assertThat(JsonValues.strings(JsonValues.mapOrEmpty(saved.get("group")).get("notes")))
                .anySatisfy(note -> assertThat(note).contains("pipeline=new_version was ignored"));
        assertThat(JsonValues.mapOrEmpty(rowNamed(written.getLast(), "PegaSecurityEvent").get("pipeline")))
                .doesNotContainKey("new_version");
    }

    @Test
    void promotionPointsTheBuildsModulesAtTheOriginalsOfItsWorkingCopies() {
        world.call(tool, args(security(), "PEGA security", List.of("PegaSecurityEvent")));
        final Map<String, String> docs = xsltsInBuild();
        final List<Map<String, Object>> listed = List.of(
                doc(docs.get("PEGA security"), "PEGA security", null),
                doc(docs.get("PEGA helpers"), "PEGA helpers", "o-helpers"),
                doc(docs.get("PEGA common"), "PEGA common", "o-common"));

        final List<String> done = world.groups.toOriginals(world.context(), BUILD, listed, Set.of());

        assertThat(done).anySatisfy(line -> assertThat(line).contains("pointed 'PEGA security' at the originals"));
        // The group (new) imports the common module's original, which imports the helpers' original; the kept
        // mappings name them too.
        assertThat(world.xslt(docs.get("PEGA security")).getData()).contains("uuid='o-common'")
                .doesNotContain("uuid='" + docs.get("PEGA common") + "'");
        assertThat(keptMapping(world.xslt(docs.get("PEGA security"))).module().common()).contains("o-common");
        assertThat(world.xslt(docs.get("PEGA common")).getData()).contains("uuid='o-helpers'");
    }

    @Test
    void sourceTypesAddedToAGroupJoinItsPipelineAndOutputFeed() {
        world.call(tool, args(security(), "PEGA security", List.of("PegaSecurityEvent")));
        final Map<String, Object> own = new LinkedHashMap<>(args(integration(), "PEGA integration",
                List.of("PegaIntegration")));
        own.put("own_output_feed", true);
        world.call(tool, own);
        // Its output feed made (create_feed group=).
        written.add(withRows(written.getLast(), row -> {
            if ("PegaIntegration".equals(row.get("name"))) {
                row.put("output_feed", "PEGA-INTEGRATION-EVENTS");
            }
        }));
        // The group grows: PegaDB added to it, with nothing asked (the group is placed).
        final Map<String, Object> grown = new LinkedHashMap<>(args(integration(), "PEGA integration",
                List.of("PegaIntegration", "PegaDB")));
        grown.remove("own_output_feed");

        final Map<String, Object> saved = world.call(tool, grown);

        assertThat(saved.get("problems")).as("%s", saved).isEqualTo(List.of());
        final Map<String, Object> db = rowNamed(written.getLast(), "PegaDB");
        assertThat(JsonValues.mapOrEmpty(db.get("pipeline"))).containsEntry("name", "PEGA PEGA integration");
        assertThat(db).containsEntry("output_feed", "PEGA-INTEGRATION-EVENTS");
        // The group's XSLT now translates both.
        assertThat(ModuleChecks.marker(world.xslt(xsltsInBuild().get("PEGA integration")).getData()))
                .hasValueSatisfying(marker -> assertThat(marker.sourceTypes()).containsExactly("PegaIntegration",
                        "PegaDB"));
        assertThat(JsonValues.strings(JsonValues.mapOrEmpty(saved.get("group")).get("notes")))
                .anySatisfy(note -> assertThat(note).contains("PegaDB", "PEGA-INTEGRATION-EVENTS"));
    }

    @Test
    void aTranslatedRowWithNoPlacementIsPlacedAsTheUserChooses() {
        final Map<String, Object> own = new LinkedHashMap<>(args(integration(), "PEGA integration",
                List.of("PegaIntegration")));
        own.put("own_output_feed", true);
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
        unplaced.remove("own_output_feed");

        final Map<String, Object> refused = world.call(tool, unplaced);

        assertThat(String.valueOf(refused.get("error"))).contains("Ask the user where its Events go");
        assertThat(xsltsInBuild()).doesNotContainKey("PEGA security");
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
    void aGroupNamedAsTheCommonModuleIsRefused() {
        final Map<String, Object> refused = world.call(tool, args(security(), "PEGA common",
                List.of("PegaSecurityEvent")));

        assertThat(String.valueOf(refused.get("error"))).contains("'PEGA common'", "mode of the feed's common "
                                                                                    + "module");
        assertThat(xsltsInBuild()).isEmpty();
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
    void aNewGroupIsAskedWhereItsEventsGoNotWhichPipeline() {
        world.forms = true;
        world.answers.put("Source type group 'PEGA security'", "To an output feed of its own (made next, in a form; "
                                                               + "an index of its own)");
        final Map<String, Object> asked = new LinkedHashMap<>(args(security(), "PEGA security",
                List.of("PegaSecurityEvent")));
        asked.remove("own_output_feed");

        final Map<String, Object> saved = world.call(tool, asked);

        assertThat(saved.get("problems")).as("%s", saved).isEqualTo(List.of());
        assertThat(placementForms()).singleElement().satisfies(form -> assertThat(form).contains(
                "a pipeline of its own, 'PEGA PEGA security'", "where its Events go").doesNotContain("**", "`"));
        assertThat(rowNamed(written.getLast(), "PegaSecurityEvent")).containsEntry("output_feed", "");
        // Saved again in the build: placed, so it isn't asked again.
        world.call(tool, asked);
        assertThat(placementForms()).hasSize(1);
    }

    @Test
    void anOwnOutputFeedGivenForAGroupPlacedAlreadyIsSaidToBeIgnored() {
        world.call(tool, args(security(), "PEGA security", List.of("PegaSecurityEvent")));
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
    void aChangeToAGroupWhosePipelineIsPromotedIsAskedForThenGoesToAWorkingCopy() {
        final String original = promotedGroup();
        final Map<String, Object> again = new LinkedHashMap<>(args(security(), "PEGA security",
                List.of("PegaSecurityEvent")));
        again.remove("own_output_feed");

        final Map<String, Object> refused = world.call(tool, again);

        assertThat(String.valueOf(refused.get("error"))).contains("runs in pipeline PEGA PEGA security, in "
                                                                  + "production", "pipeline=", "Nothing was saved");
        again.put("pipeline", "change");
        final Map<String, Object> saved = world.call(tool, again);
        assertThat(saved.get("problems")).as("%s", saved).isEqualTo(List.of());
        final String copy = xsltsInBuild().get("PEGA security");
        assertThat(copy).isNotEqualTo(original);
        assertThat(world.world.records().entry(BUILD, copy)).containsEntry("copy_of", original);
        final Map<String, Object> row = rowNamed(written.getLast(), "PegaSecurityEvent");
        assertThat(JsonValues.mapOrEmpty(row.get("group_xslt"))).containsEntry("uuid", copy);
        assertThat(JsonValues.mapOrEmpty(row.get("pipeline"))).containsEntry("uuid", "o-pipeline")
                .doesNotContainKey("new_version");
        assertThat(String.valueOf(JsonValues.mapOrEmpty(saved.get("group")).get("group_next")))
                .contains("copy_pipeline", "working_copy=true");
        // Decided for the build: saved again, nothing is asked.
        again.remove("pipeline");
        assertThat(world.call(tool, again).get("problems")).isEqualTo(List.of());
    }

    @Test
    void aNewVersionOfAGroupsPromotedPipelineGetsANewVersionOfItsXslt() {
        final String original = promotedGroup();
        world.forms = true;
        world.answers.put("Group 'PEGA security' runs in pipeline", "A new version of PEGA PEGA security "
                                                                    + "(copy_pipeline; PEGA PEGA security runs on "
                                                                    + "until the new one replaces it)");
        final String code = world.xslt(original).getData();
        final Map<String, Object> again = new LinkedHashMap<>(args(security(), "PEGA security",
                List.of("PegaSecurityEvent")));
        again.remove("own_output_feed");

        final Map<String, Object> saved = world.call(tool, again);

        assertThat(saved.get("problems")).as("%s", saved).isEqualTo(List.of());
        final String version = xsltsInBuild().get("PEGA security");
        assertThat(world.world.records().entry(BUILD, version)).containsEntry("version_of", original)
                .doesNotContainKey("copy_of");
        assertThat(world.xslt(original).getData()).isEqualTo(code);
        final Map<String, Object> row = rowNamed(written.getLast(), "PegaSecurityEvent");
        assertThat(JsonValues.mapOrEmpty(row.get("group_xslt"))).containsEntry("uuid", version);
        assertThat(JsonValues.mapOrEmpty(row.get("pipeline"))).containsEntry("uuid", "o-pipeline")
                .containsEntry("new_version", true);
        assertThat(String.valueOf(JsonValues.mapOrEmpty(saved.get("group")).get("group_next")))
                .contains("copy_pipeline", "working_copy=false");
        assertThat(world.asked).anySatisfy(form -> assertThat(form).contains("change that pipeline, or make a new "
                                                                             + "version of it"));
    }

    @Test
    void aNewVersionCopiedMovesTheGroupsRowsToIt() {
        final String original = promotedGroup();
        final DocRef copy = new DocRef(XsltDoc.TYPE, "v-security", "PEGA security v2");

        final List<String> moved = world.groups.newVersion(world.context(), BUILD, "o-pipeline",
                Map.of(original, copy), new DocRef("Pipeline", "v-pipeline", "PEGA PEGA security v2"));

        assertThat(moved).containsExactly("PegaSecurityEvent");
        final Map<String, Object> row = rowNamed(written.getLast(), "PegaSecurityEvent");
        assertThat(JsonValues.mapOrEmpty(row.get("pipeline"))).containsOnly(Map.entry("name",
                "PEGA PEGA security v2"), Map.entry("uuid", "v-pipeline"));
        assertThat(JsonValues.mapOrEmpty(row.get("group_xslt"))).containsEntry("uuid", "v-security");
        // A pipeline no row names moves nothing.
        assertThat(world.groups.newVersion(world.context(), BUILD, "other", Map.of(), new DocRef("Pipeline", "x",
                "X"))).isEmpty();
    }

    @Test
    void aPipelineMadeForAGroupIsNamedInItsRows() {
        world.call(tool, args(security(), "PEGA security", List.of("PegaSecurityEvent")));

        final List<String> types = world.groups.pipelineMade(world.context(), BUILD, "PEGA security",
                new DocRef("Pipeline", "p1", "PEGA-Security-Events"));

        assertThat(types).containsExactly("PegaSecurityEvent");
        assertThat(JsonValues.mapOrEmpty(rowNamed(written.getLast(), "PegaSecurityEvent").get("pipeline")))
                .containsOnly(Map.entry("name", "PEGA-Security-Events"), Map.entry("uuid", "p1"));
        final SourceTypeGroups.GroupRun run = world.groups.groupNamed(BUILD, "PEGA security");
        assertThat(run.xslt()).isEqualTo(xsltsInBuild().get("PEGA security"));
        assertThat(run.outputFeed()).isEmpty();
        assertThat(world.groups.groupOf(BUILD, xsltsInBuild().get("PEGA security"))).hasValueSatisfying(found ->
                assertThat(found.group()).isEqualTo("PEGA security"));
        assertThat(world.groups.groupOf(BUILD, xsltsInBuild().get("PEGA common"))).isEmpty();
    }

    @Test
    void promotionLeavesAWorkingCopyTheUserKeptAsItIs() {
        world.call(tool, args(security(), "PEGA security", List.of("PegaSecurityEvent")));
        final Map<String, String> docs = xsltsInBuild();
        // The common module's original, in production, as the kept copy's: it has the parts the group calls.
        final String groupCode = world.xslt(docs.get("PEGA security")).getData();
        final String commonCode = world.xslt(docs.get("PEGA common")).getData();
        world.world.add(XsltDoc.TYPE, "PEGA common", world.world.folder("System/Feeds/PEGA").uuid, "o-common");
        world.world.docs.put("o-common", XsltDoc.builder().uuid("o-common").name("PEGA common")
                .data(commonCode).build());
        final List<Map<String, Object>> listed = List.of(
                doc(docs.get("PEGA security"), "PEGA security", null),
                doc(docs.get("PEGA helpers"), "PEGA helpers", "o-helpers"),
                doc(docs.get("PEGA common"), "PEGA common", "o-common"));

        assertThat(world.groups.keptProblems(BUILD, listed, Set.of(docs.get("PEGA common")))).isEmpty();
        world.groups.toOriginals(world.context(), BUILD, listed, Set.of(docs.get("PEGA common")));

        // The kept copy is as it was; the group promoted imports the original.
        assertThat(world.xslt(docs.get("PEGA common")).getData()).isEqualTo(commonCode);
        assertThat(world.xslt(docs.get("PEGA security")).getData()).isNotEqualTo(groupCode)
                .contains("uuid='o-common'");

        // An original lacking a part the group calls: promotion would break it, so it is refused.
        world.world.docs.put("o-common", XsltDoc.builder().uuid("o-common").name("PEGA common")
                .data(commonCode.replace("pega_common_device", "pega_common_old")).build());
        assertThat(world.groups.keptProblems(BUILD, listed, Set.of(docs.get("PEGA common"))))
                .singleElement().satisfies(problem -> assertThat(problem).contains("PEGA security",
                        "PEGA common", "pega_common_device"));
    }

    @Test
    void sourceTypesTheRegistryLacksAreRefused() {
        final Map<String, Object> refused = world.call(tool, args(security(), "PEGA security",
                List.of("NoSuchType")));

        assertThat(String.valueOf(refused.get("error"))).contains("NoSuchType", "are not the registry's");
    }

    @Test
    void anUnsavedGroupIsSteppedAsDraftCodeForItsPipelinesXslt() {
        final SteppingWorld stepping = steppingAGroupPipeline();
        stepping.records(1, "<Events xmlns=\"event-logging:3\"><Event/></Events>");
        final String draft = securityDraft();

        new StepSampleTool(stepping.stepping()).execute(BuildToolCalls.call(StepSampleTool.NAME,
                JsonUtil.writeValueAsString(Map.of("pipeline_uuid", "p-1", "stream_ids", List.of(7),
                        "draft_code", Map.of("translationFilter", draft)), false)), stepping.context());

        // As given: no temporary dispatcher, nothing inlined.
        assertThat(stepping.opened.getFirst().getValue().get("translationFilter")).isEqualTo(draft);
    }

    @Test
    void aGroupsDraftIsComparedAsGiven() {
        final SteppingWorld stepping = steppingAGroupPipeline();
        stepping.records(1, "<Events xmlns=\"event-logging:3\"><Event/></Events>");
        final String draft = securityDraft();

        new CompareOutputsTool(stepping.stepping(), Mockito.mock(EventLocations.class)).execute(
                BuildToolCalls.call(CompareOutputsTool.NAME, JsonUtil.writeValueAsString(Map.of("pipeline_uuid",
                        "p-1", "stream_ids", List.of(7), "draft_code", Map.of("translationFilter", draft)),
                        false)), stepping.context());

        assertThat(stepping.opened).extracting(Map.Entry::getValue)
                .anySatisfy(code -> assertThat(code.get("translationFilter")).isEqualTo(draft));
    }

    @Test
    void aGroupsFixIsProvenAsGivenAndDiffedAgainstTheGroupItsPipelineRuns() {
        final SteppingWorld stepping = steppingAGroupPipeline();
        Mockito.when(stepping.documentAccess.read(any(DocRef.class), eq(HasData.class)))
                .thenAnswer(inv -> Optional.of(XsltDoc.builder().uuid("x").name("PEGA security")
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
        final String draft = securityDraft();

        final ToolResult result = fix.execute(BuildToolCalls.call(SummariseFixTool.NAME,
                JsonUtil.writeValueAsString(Map.of("pipeline_uuid", "p-1", "element", "translationFilter", "draft",
                        draft, "stream_ids", List.of(7), "expected_paths", List.of("Event/X")), false)),
                stepping.context());

        assertThat(result.isError()).as(result.text()).isFalse();
        final ArgumentCaptor<CompareOutputsTool.Request> request = ArgumentCaptor.forClass(
                CompareOutputsTool.Request.class);
        Mockito.verify(compare).compareLocated(any(), any(), request.capture(), Mockito.anyList());
        assertThat(request.getValue().draftCode().get("translationFilter")).isEqualTo(draft);
        final Map<String, Object> said = BuildToolCalls.json(result.text());
        assertThat(String.valueOf(said.get("diff"))).contains("-<old-group/>", "mode=\"pega_security\"");
    }

    // --- fixtures ---

    // A stepping world whose pipeline p-1 runs the security group's saved XSLT (x), as its own pipeline does.
    private static SteppingWorld steppingAGroupPipeline() {
        final SteppingWorld stepping = new SteppingWorld();
        Mockito.when(stepping.documentAccess.read(eq(new DocRef(XsltDoc.TYPE, "x")), eq(XsltDoc.class)))
                .thenReturn(Optional.of(XsltDoc.builder().uuid("x").name("PEGA security").data("<old-group/>")
                        .build()));
        Mockito.when(stepping.checkXslt.check(anyString(), any(), any())).thenReturn(Map.of("ok", true, "errors",
                List.of()));
        return stepping;
    }

    // The security group's draft, as build_translation_xslt writes it.
    private static String securityDraft() {
        final TranslationMapping mapping = TranslationMapping.parse(security());
        final TranslationMapping alone = mapping.withModule(new SourceTypeModule(SourceTypeModule.GROUP,
                "PEGA security", "pega_security", List.of("PegaSecurityEvent"), null, "PEGA helpers", List.of(), null,
                "sourcetype", List.of(new SourceTypeModule.Row("PegaSecurityEvent", "value", "PegaSecurityEvent"))));
        final XsltGenerator.Generated generated = XsltGenerator.generate(alone, TranslationWorld.SCHEMA,
                TranslationWorld.VERSION);
        assertThat(generated.problems()).isEmpty();
        return generated.xslt();
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

    // The forms asking where a new group's Events go, as the user was shown them.
    private List<String> placementForms() {
        return world.asked.stream().filter(form -> form.contains("is new. It gets a pipeline")).toList();
    }

    // The security group as a first build promoted it: its XSLT and its pipeline in production, beside the feed, the
    // registry naming them. Returns its XSLT's UUID.
    private String promotedGroup() {
        world.call(tool, args(security(), "PEGA security", List.of("PegaSecurityEvent")));
        final String uuid = xsltsInBuild().get("PEGA security");
        final String folder = world.world.folder("System/Feeds/PEGA").uuid;
        world.world.move(uuid, folder);
        world.world.add("Pipeline", "PEGA PEGA security", folder, "o-pipeline");
        written.add(withRows(written.getLast(), row -> {
            if ("PegaSecurityEvent".equals(row.get("name"))) {
                row.put("pipeline", Map.of("name", "PEGA PEGA security", "uuid", "o-pipeline"));
            }
        }));
        return uuid;
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
        // No form can be shown here: the user's choice for a new group, as the agent passes it.
        args.put("own_output_feed", false);
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
