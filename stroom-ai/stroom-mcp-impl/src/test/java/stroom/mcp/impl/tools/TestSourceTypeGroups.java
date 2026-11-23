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

import stroom.ai.impl.tools.DocumentAccess;
import stroom.ai.impl.tools.JsonValues;
import stroom.ai.impl.tools.KeptMapping;
import stroom.ai.impl.tools.ToolRefusal;
import stroom.ai.impl.tools.ToolResult;
import stroom.ai.impl.tools.generation.DumpMode;
import stroom.ai.impl.tools.generation.HelpersXslt;
import stroom.ai.impl.tools.generation.ModuleChecks;
import stroom.ai.impl.tools.generation.SeenSourceTypes;
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

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;

/// `build_translation_xslt group=`: a source type group of an aggregated feed saved with its mapping as the XSLT of
/// a pipeline of its own, the feed's common module and helpers made from the first group's mapping (a chain: the
/// group imports the common module, which imports the helpers), and the registry's rows set translated; a second
/// group reusing the common module, and a shared call of its own added to it; a group regenerated from its kept
/// mapping alone; an unsaved group stepped, compared and proven as draft code for its pipeline's XSLT; and one source
/// type group a build: the group and its source types taken from the build's scope, another group refused.
class TestSourceTypeGroups {

    private static final String BUILD = "pega-v1";
    private static final String WRAPPER = """
            [{"path": "EventTime/TimeCreated", "field": "timestamp"},
             {"path": "EventSource/System/Name", "value": "PEGA"},
             {"path": "EventSource/System/Environment", "value": "Test"},
             {"path": "EventSource/Generator", "value": "Pega"},
             {"path": "EventSource/Device/HostName", "field": "hostname"}]""";

    // The build's sample records, a line each, by source type: a security login, an integration call, and a DB
    // record written as a login (as the security group reads it).
    private static final String SECURITY_LOGIN = record("PegaSecurityEvent",
            "{\\\"eventType\\\": \\\"login\\\", \\\"operatorId\\\": \\\"alice\\\"}");
    private static final String INTEGRATION_CALL = record("PegaIntegration",
            "Request: <Envelope><Header><CorrelationId>c-1</CorrelationId></Header></Envelope>");
    private static final String DB_LOGIN = record("PegaDB",
            "{\\\"eventType\\\": \\\"login\\\", \\\"operatorId\\\": \\\"bob\\\"}");

    private final TranslationWorld world = new TranslationWorld();
    private final BuildTranslationXsltTool tool = world.buildTool();
    private final List<String> written = new ArrayList<>();
    // The text of the build's sample stream, which each save is checked against.
    private String sample = SECURITY_LOGIN + INTEGRATION_CALL + DB_LOGIN;

    @BeforeEach
    void setUp() {
        // The build's own sample stream: a save given neither sample nor stream_ids is checked against it.
        world.facts.ownRaw = List.of(1L);
        Mockito.when(world.sampleStreamTexts.read(List.of(1L))).thenAnswer(inv -> new SampleStreamTexts.Texts(
                Map.of("stream 1", sample), List.of()));
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
    void aGroupsPipelineIsNamedWithV10WhereNeitherTheFeedNorTheGroupHasAVersion() {
        // Decided by the user (2026-11-17): promotion files a pipeline by the version in its name.
        assertThat(SourceTypeGroups.pipelineName("PEGA", "security")).isEqualTo("PEGA security V1.0");
        // The feed's own token is not the pipeline's version (found in review, 2026-11-18): V1.0 is added.
        assertThat(SourceTypeGroups.pipelineName("Azure-Audit-V1.0-EVENTS", "M365 Messaging"))
                .isEqualTo("Azure-Audit-V1.0-EVENTS M365 Messaging V1.0");
        assertThat(SourceTypeGroups.pipelineName("CISCO-ASA-V9", "vpn")).isEqualTo("CISCO-ASA-V9 vpn V1.0");
        // A group's XSLT's new version follows its pipeline's, the feed's token left.
        assertThat(SourceTypeGroups.versionName("CISCO-ASA-V9 vpn V1.0", "CISCO-ASA-V9 vpn V1.0", "CISCO-ASA-V9",
                "vpn")).isEqualTo("CISCO-ASA-V9 vpn V1.1");
        assertThat(SourceTypeGroups.versionName("CISCO-ASA-V9 vpn", "CISCO-ASA-V9 vpn", "CISCO-ASA-V9", "vpn"))
                .isEqualTo("CISCO-ASA-V9 vpn-V1.1");
    }

    @Test
    void aWholeTranslationsSeenSourceTypesAreTheRegistrysRowsElseTheSurveysValues() {
        final TranslationBuilds builds = Mockito.mock(TranslationBuilds.class);
        final SourceTypeRegistries registries = Mockito.mock(SourceTypeRegistries.class);
        final SourceTypeGroups groups = new SourceTypeGroups(Mockito.mock(WriteGuard.class),
                Mockito.mock(DocumentAccess.class), registries, Mockito.mock(TranslationDocs.class), builds,
                Mockito.mock(BuildRecords.class));
        final Map<String, Object> values = new LinkedHashMap<>();
        values.put("PegaSecurityEvent", Map.of("records", 3));
        values.put("PegaDB", Map.of("records", 2));
        final Map<String, Object> surveyed = Map.of("feed", "PEGA", "source_types", Map.of("field", "sourcetype",
                "values", values));
        Mockito.when(builds.surveyStatesInBuild("whole")).thenReturn(List.of(surveyed));
        Mockito.when(registries.registry("whole", "PEGA")).thenReturn(Optional.empty());

        // No registry: the survey's values.
        assertThat(groups.seenSourceTypes("whole")).contains(new SeenSourceTypes("sourcetype", List.of(
                "PegaSecurityEvent", "PegaDB"), List.of()));
        // A registry (record_source_types ran, no group built): its rows.
        Mockito.when(registries.registry("whole", "PEGA")).thenReturn(Optional.of(registry()));
        assertThat(groups.seenSourceTypes("whole")).contains(new SeenSourceTypes("sourcetype", List.of(
                "PegaSecurityEvent", "PegaIntegration", "PegaDB"), List.of()));
        // More values than the survey keeps one by one: an unseen value can't be told.
        Mockito.when(registries.registry("whole", "PEGA")).thenReturn(Optional.empty());
        values.put("(other values)", Map.of("records", 9));
        assertThat(groups.seenSourceTypes("whole")).isEmpty();
        // A feed with no source type field, or no build: nothing.
        Mockito.when(builds.surveyStatesInBuild("plain")).thenReturn(List.of(Map.of("feed", "PLAIN",
                "source_types", Map.of("aggregated", false))));
        assertThat(groups.seenSourceTypes("plain")).isEmpty();
        assertThat(groups.seenSourceTypes(null)).isEmpty();
    }

    @Test
    void aSystemNameTypedWithControlCharactersIsWrittenWithoutThem() {
        // A source type's System Name recorded with control characters (a bell, a tab): the group's System/Name
        // has none (user, 2026-11-22).
        final Map<String, Object> state = new LinkedHashMap<>(registry().state());
        state.put("rows", List.of(row("PegaSecurityEvent", "PEGA\u0007 Security\t"), row("PegaIntegration", null),
                row("PegaDB", null)));
        written.add(SourceTypeRegistry.render(state));

        final Map<String, Object> saved = world.call(tool, args(security(), "PEGA security",
                List.of("PegaSecurityEvent")));

        assertThat(saved.get("problems")).as("%s", saved).isEqualTo(List.of());
        assertThat(JsonValues.mapOrEmpty(saved.get("group"))).containsEntry("system_name", "PEGA Security");
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
        assertThat(docs).containsOnlyKeys("PEGA PEGA security V1.0", "PEGA common", "PEGA helpers");
        // The group keeps its mapping, its module importing the build's common module by UUID (which imports the
        // helpers), with the registry's rows to tell its records by; the wrapper's entries went to the common module.
        final XsltDoc groupXslt = world.xslt(docs.get("PEGA PEGA security V1.0"));
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
        assertThat(JsonValues.mapOrEmpty(row.get("group_xslt"))).containsEntry("uuid",
                docs.get("PEGA PEGA security V1.0"));
        assertThat(JsonValues.mapOrEmpty(row.get("pipeline"))).doesNotContainKey("dispatcher");
        assertThat(rowNamed(written.getLast(), "PegaDB")).containsEntry("status", SourceTypeRegistry.PARKED);
    }

    @Test
    void aGroupsNewXsltIsNamedForTheGroupAsItsPipelineIsWhateverNameTheAgentPasses() {
        // Seen in chat 185: the agent passed a name of its own, and the group's XSLT was saved under it, beside the
        // pipeline create_pipeline proposed with the group's name.
        final Map<String, Object> given = args(security(), "PEGA security", List.of("PegaSecurityEvent"));
        given.put("name", "PEGA-V1.0-Events");

        final Map<String, Object> saved = world.call(tool, given);

        assertThat(saved.get("problems")).as("%s", saved).isEqualTo(List.of());
        assertThat(xsltsInBuild()).containsOnlyKeys("PEGA PEGA security V1.0", "PEGA common", "PEGA helpers");
        final String uuid = xsltsInBuild().get("PEGA PEGA security V1.0");
        assertThat(JsonValues.mapOrEmpty(saved.get("saved"))).containsEntry("name", "PEGA PEGA security V1.0");
        assertThat(String.valueOf(saved.get("name_note"))).contains("'PEGA PEGA security V1.0'",
                "group 'PEGA security'", "not 'PEGA-V1.0-Events'");
        // The pipeline it is to run in has the same name.
        assertThat(JsonValues.mapOrEmpty(rowNamed(written.getLast(), "PegaSecurityEvent").get("pipeline")))
                .containsEntry("name", "PEGA PEGA security V1.0");

        // Saved again under the name the agent passed: the group's XSLT is changed, no second one made, and nothing
        // is said of the name.
        final Map<String, Object> again = world.call(tool, given);

        assertThat(again.get("problems")).as("%s", again).isEqualTo(List.of());
        assertThat(xsltsInBuild()).containsOnlyKeys("PEGA PEGA security V1.0", "PEGA common", "PEGA helpers");
        assertThat(JsonValues.mapOrEmpty(again.get("saved"))).containsEntry("uuid", uuid);
        assertThat(again).doesNotContainKey("name_note");
    }

    @Test
    void whatTheUserAgreedToForAGroupSavedUnderTheAgentsOwnNameIsNotAskedAgain() {
        // What the user agreed to is kept with the group's XSLT, found by the name given: the group's XSLT is named
        // for the group, so a later call with the agent's own name finds it by the registry.
        final Map<String, Object> mapping = security();
        final List<Object> events = new ArrayList<>(JsonValues.listOrEmpty(mapping.get("events")));
        final Map<String, Object> other = new LinkedHashMap<>(JsonValues.mapOrEmpty(events.get(1)));
        other.put("allow_unknown", "the other security events, for a later build");
        events.set(1, other);
        mapping.put("events", events);
        sample = SECURITY_LOGIN + record("PegaSecurityEvent",
                "{\\\"eventType\\\": \\\"lock\\\", \\\"operatorId\\\": \\\"carol\\\"}");
        final Map<String, Object> given = args(mapping, "PEGA security", List.of("PegaSecurityEvent"));
        given.put("name", "PEGA-V1.0-Events");
        final Map<String, Object> shown = world.call(tool, given);
        assertThat(shown.get("status")).as("%s", shown).isEqualTo("needs_review");
        world.forms = true;
        world.answers.put("Translate these records", "yes");
        world.answers.put("Source type group 'PEGA security'", "Into the same feed (PEGA)");
        final Map<String, Object> reviewed = new LinkedHashMap<>(given);
        reviewed.put("reviewed", shown.get("review_id"));
        assertThat(world.call(tool, reviewed)).as("agreed and saved").containsKey("saved");
        assertThat(xsltsInBuild()).containsKey("PEGA PEGA security V1.0").doesNotContainKey("PEGA-V1.0-Events");
        world.asked.clear();

        final Map<String, Object> again = world.call(tool, given);

        assertThat(again).as("%s", again).containsKey("saved").doesNotContainEntry("status", "needs_review");
        assertThat(world.asked).isEmpty();
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
        final TranslationMapping kept = keptMapping(world.xslt(docs.get("PEGA PEGA integration V1.0")));
        // Its wrapper entries are the common module's (its own System Name aside): left to it.
        assertThat(kept.common()).isEmpty();
        assertThat(ModuleChecks.hrefs(world.xslt(docs.get("PEGA PEGA integration V1.0")).getData())).singleElement()
                .satisfies(href -> assertThat(href).contains(docs.get("PEGA common")));

        // Regenerated from its kept mapping alone, by uuid: built again as the group it is.
        final Map<String, Object> again = world.call(tool, Map.of("uuid", docs.get("PEGA PEGA integration V1.0"),
                "schema_version", TranslationWorld.VERSION, "feeds", List.of("PEGA")));
        assertThat(again.get("problems")).as("%s", again).isEqualTo(List.of());
        assertThat(JsonValues.mapOrEmpty(again.get("group"))).containsEntry("mode", "pega_integration");
        assertThat(xsltsInBuild()).hasSize(4);
    }

    @Test
    void aGroupNamedPerSourceTypeWritesItsOwnSystemAndALaterGroupItsOwnName() {
        // Seen in chat 182: a System/Name per source type (field and map) went to the feed's common module, which
        // every group imports: a later group's records got no System/Name at all.
        final Map<String, Object> saved = world.call(tool, args(perSourceType(security(), "PegaSecurityEvent",
                "PEGA Security"), "PEGA app", List.of("PegaSecurityEvent", "PegaDB")));

        assertThat(saved.get("problems")).as("%s", saved).isEqualTo(List.of());
        assertThat(JsonValues.strings(JsonValues.mapOrEmpty(saved.get("group")).get("common_parts")))
                .doesNotContain("EventSource/System").contains("EventTime", "EventSource/Device");
        final Map<String, String> docs = xsltsInBuild();
        // The feed's common module holds no group's names, nor a System part to call.
        final TranslationMapping common = keptMapping(world.xslt(docs.get("PEGA common")));
        assertThat(common.common()).noneSatisfy(entry -> assertThat(entry.strippedPath())
                .startsWith("EventSource/System"));
        assertThat(world.xslt(docs.get("PEGA common")).getData()).doesNotContain("PEGA Security", "PEGA DB");
        // The group writes its own System, each record's name by its source type.
        final XsltDoc group = world.xslt(docs.get("PEGA PEGA app V1.0"));
        assertThat(ToolJson.dumps(keptMapping(group).toJson(DumpMode.COMPACT)))
                .contains("\"field\": \"sourcetype\", \"map\": {\"PegaSecurityEvent\": \"PEGA Security\", "
                          + "\"PegaDB\": \"PEGA DB\"}");
        assertThat(group.getData()).contains("'PEGA Security'", "'PEGA DB'", "<Environment>Prod</Environment>");
        // Each source type's own name on its row of the registry; every other row as it was.
        assertThat(rowNamed(written.getLast(), "PegaSecurityEvent")).containsEntry("system_name", "PEGA Security");
        assertThat(rowNamed(written.getLast(), "PegaDB")).containsEntry("system_name", "PEGA DB")
                .containsEntry("status", SourceTypeRegistry.TRANSLATED);
        assertThat(JsonValues.maps(SourceTypeRegistry.read(written.getLast()).get("rows")))
                .extracting(row -> JsonValues.text(row.get("name"))).containsExactly("PegaSecurityEvent",
                        "PegaIntegration", "PegaDB");
        assertThat(rowNamed(written.getLast(), "PegaIntegration")).containsEntry("status", SourceTypeRegistry.PARKED)
                .doesNotContainKey("group");
        assertThat(rowNamed(written.getLast(), "PegaIntegration").get("system_name")).isNull();

        // A later group of one name: its own, written by itself (the common module has no System part).
        final Map<String, Object> later = world.call(tool, args(integration(), "PEGA integration",
                List.of("PegaIntegration")));
        assertThat(later.get("problems")).as("%s", later).isEqualTo(List.of());
        final XsltDoc integration = world.xslt(xsltsInBuild().get("PEGA PEGA integration V1.0"));
        assertThat(integration.getData()).contains("PEGA Integration").doesNotContain("PEGA Security");
    }

    @Test
    void aLaterGroupNamedPerSourceTypeDoesntLeaveItsSystemToTheCommonModule() {
        world.call(tool, args(security(), "PEGA security", List.of("PegaSecurityEvent")));
        final String commonCode = world.xslt(xsltsInBuild().get("PEGA common")).getData();

        final Map<String, Object> saved = world.call(tool, args(perSourceType(integration(), "PegaIntegration",
                "PEGA Calls"), "PEGA rest", List.of("PegaIntegration", "PegaDB")));

        assertThat(saved.get("problems")).as("%s", saved).isEqualTo(List.of());
        assertThat(world.xslt(xsltsInBuild().get("PEGA common")).getData()).isEqualTo(commonCode);
        final XsltDoc rest = world.xslt(xsltsInBuild().get("PEGA PEGA rest V1.0"));
        // The common module's System part not called: the group writes its own.
        assertThat(rest.getData()).doesNotContain("name=\"pega_common_system\"").contains("pega_common_device");
        assertThat(keptMapping(rest).common()).anySatisfy(entry -> assertThat(entry.strippedPath())
                .isEqualTo("EventSource/System/Name"));
        assertThat(rest.getData()).contains("'PEGA Calls'", "'PEGA DB'");
    }

    @Test
    void aLaterGroupsOwnGeneratorIsWrittenInItsOwnXsltAndNeverChangesTheFeedsSharedModule() {
        // Decided by the user 2026-11-23 (chat 189): a group's own value for a shared part is written in its own
        // XSLT in place of the module's part, for that group only; a group build never changes the shared module.
        // Seen there: the feed's registry, promoted into the feed's own folder, wasn't found, the build's registry
        // was made anew naming no module, and the Entra ID group's mapping made the feed's common module again,
        // its own Generator in it.
        world.call(tool, args(security(), "PEGA security", List.of("PegaSecurityEvent")));
        final Map<String, String> first = xsltsInBuild();
        final String folder = world.world.folder("System/Feeds/PEGA").uuid;
        first.values().forEach(uuid -> world.world.move(uuid, folder));
        final String commonCode = world.xslt(first.get("PEGA common")).getData();
        final String securityCode = world.xslt(first.get("PEGA PEGA security V1.0")).getData();
        final String promoted = written.getLast();
        Mockito.when(world.registries.registry(isNull(), eq("PEGA"))).thenReturn(Optional.of(
                new SourceTypeRegistries.Found(new SurveyRecord("p", "PEGA source types", promoted), true,
                        SourceTypeRegistry.read(promoted))));
        written.add(registry().doc().text());
        final Map<String, Object> mapping = withGenerator(integration(), "Pega integration bus");

        final Map<String, Object> saved = world.call(tool, args(mapping, "PEGA integration",
                List.of("PegaIntegration")));

        assertThat(saved.get("problems")).as("%s", saved).isEqualTo(List.of());
        // No module made or changed in the build: the group imports the feed's, as promoted.
        assertThat(xsltsInBuild()).containsOnlyKeys("PEGA PEGA integration V1.0");
        final XsltDoc group = world.xslt(xsltsInBuild().get("PEGA PEGA integration V1.0"));
        assertThat(group.getData()).contains("Pega integration bus", "pega_common_event_time",
                "uuid='" + first.get("PEGA common") + "'").doesNotContain("pega_common_generator");
        assertThat(JsonValues.strings(JsonValues.mapOrEmpty(saved.get("group")).get("notes")))
                .anySatisfy(note -> assertThat(note).startsWith("EventSource/Generator: this group writes its own"));
        // The shared module and the first group's events as they were.
        assertThat(world.xslt(first.get("PEGA common")).getData()).isEqualTo(commonCode).contains("Pega")
                .doesNotContain("Pega integration bus");
        assertThat(world.xslt(first.get("PEGA PEGA security V1.0")).getData()).isEqualTo(securityCode);
    }

    @Test
    void aLaterGroupOfAFeedWhoseCommonModuleCantBeFoundIsRefused() {
        // A group never makes the feed's shared module anew from its own mapping.
        written.add(withRows(registry().doc().text(), row -> {
            if ("PegaSecurityEvent".equals(row.get("name"))) {
                row.put("status", SourceTypeRegistry.TRANSLATED);
                row.put("group", "PEGA security");
            }
        }));

        final Map<String, Object> refused = world.call(tool, args(integration(), "PEGA integration",
                List.of("PegaIntegration")));

        assertThat(String.valueOf(refused.get("error"))).contains("common module ('PEGA common') can't be found",
                "'PEGA security'", "Nothing was saved");
        assertThat(xsltsInBuild()).isEmpty();
    }

    @Test
    void aGeneratorNamingTheGroupsSourceTypesIsItsOwnAndTheFeedsIsAskedOnce() {
        // Decided by the user 2026-11-23 (chat 188): the first group's source name, naming its source types, became
        // the shared module's Generator. The group's stays its own; the feed's is asked, proposed without them.
        world.forms = true;
        world.answers.put("Source type group 'PEGA security'", "Into the same feed (PEGA)");
        world.answers.put("The Generator this group gives", "PEGA audit trail");
        final Map<String, Object> mapping = withGenerator(security(), "Pega platform (PEGA Security)");

        final Map<String, Object> saved = world.call(tool, args(mapping, "PEGA security",
                List.of("PegaSecurityEvent")));

        assertThat(saved.get("problems")).as("%s", saved).isEqualTo(List.of());
        final int asked = world.asked.indexOf(world.asked.stream()
                .filter(intro -> intro.startsWith("The Generator this group gives")).findFirst().orElseThrow());
        assertThat(ToolJson.dumps(world.formsShown.get(asked))).contains("\"examples\": [\"Pega platform\"]");
        final Map<String, String> docs = xsltsInBuild();
        final XsltDoc common = world.xslt(docs.get("PEGA common"));
        assertThat(common.getData()).contains("PEGA audit trail").doesNotContain("PEGA Security");
        final XsltDoc group = world.xslt(docs.get("PEGA PEGA security V1.0"));
        assertThat(group.getData()).contains("Pega platform (PEGA Security)").doesNotContain("pega_common_generator");
        // Kept with the feed's registry for its later builds.
        assertThat(JsonValues.mapOrEmpty(SourceTypeRegistry.read(written.getLast()).get("generator")))
                .containsEntry("value", "PEGA audit trail").containsEntry("by", "user");

        // Not asked again.
        world.asked.clear();
        assertThat(world.call(tool, args(mapping, "PEGA security", List.of("PegaSecurityEvent"))).get("problems"))
                .isEqualTo(List.of());
        assertThat(world.asked).noneSatisfy(intro -> assertThat(intro).startsWith("The Generator this group"));
    }

    @Test
    void withoutFormsTheSharedGeneratorIsThePlaceholderUntilTheFeedsIsGiven() {
        final Map<String, Object> mapping = withGenerator(security(), "Pega platform (PEGA Security)");

        final Map<String, Object> saved = world.call(tool, args(mapping, "PEGA security",
                List.of("PegaSecurityEvent")));

        assertThat(saved.get("problems")).as("%s", saved).isEqualTo(List.of());
        final String common = xsltsInBuild().get("PEGA common");
        assertThat(world.xslt(common).getData()).contains(FeedGenerators.PLACEHOLDER)
                .doesNotContain("PEGA Security");
        assertThat(world.xslt(xsltsInBuild().get("PEGA PEGA security V1.0")).getData())
                .contains("Pega platform (PEGA Security)");
        assertThat(JsonValues.strings(JsonValues.mapOrEmpty(saved.get("group")).get("notes")))
                .anySatisfy(note -> assertThat(note).contains("propose 'Pega platform'", "feed_generator="));

        // The user's answer, said in the chat, replaces the placeholder in the build's module, and is kept.
        final Map<String, Object> given = args(mapping, "PEGA security", List.of("PegaSecurityEvent"));
        given.put("feed_generator", "PEGA audit trail");
        final Map<String, Object> again = world.call(tool, given);

        assertThat(again.get("problems")).as("%s", again).isEqualTo(List.of());
        assertThat(xsltsInBuild().get("PEGA common")).isEqualTo(common);
        assertThat(world.xslt(common).getData()).contains("PEGA audit trail")
                .doesNotContain(FeedGenerators.PLACEHOLDER);
        assertThat(JsonValues.mapOrEmpty(SourceTypeRegistry.read(written.getLast()).get("generator")))
                .containsEntry("value", "PEGA audit trail");
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
        final String integrationCode = world.xslt(xsltsInBuild().get("PEGA PEGA integration V1.0")).getData();
        assertThat(ModuleChecks.hrefs(integrationCode)).singleElement()
                .satisfies(href -> assertThat(href).contains(commonUuid));
        assertThat(keptMapping(world.xslt(xsltsInBuild().get("PEGA PEGA integration V1.0"))).shared()).isEmpty();
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
                "create_pipeline", "the group's XSLT", "'PEGA PEGA integration V1.0'");
        assertThat(xsltsInBuild()).containsOnlyKeys("PEGA PEGA security V1.0", "PEGA PEGA integration V1.0",
                "PEGA common", "PEGA helpers");
        // The registry says where it goes: its output feed to make, its reference data kept.
        // The registry says where it goes: its own pipeline to make, its output feed to make, its reference data
        // kept; the other group's Events go into the feed's.
        final Map<String, Object> row = rowNamed(written.getLast(), "PegaIntegration");
        assertThat(JsonValues.mapOrEmpty(row.get("pipeline"))).containsOnly(Map.entry("name",
                "PEGA PEGA integration V1.0"));
        assertThat(row).containsEntry("output_feed", "");
        assertThat(JsonValues.maps(row.get("references"))).singleElement()
                .satisfies(reference -> assertThat(reference).containsEntry("feed", "PEGA-HOSTS"));
        final Map<String, Object> security = rowNamed(written.getLast(), "PegaSecurityEvent");
        assertThat(JsonValues.mapOrEmpty(security.get("pipeline"))).containsOnly(Map.entry("name",
                "PEGA PEGA security V1.0"));
        assertThat(security).doesNotContainKey("output_feed");
    }

    @Test
    void aNewGroupIsPlacedOnlyAsTheUserChose() {
        final Map<String, Object> unasked = new LinkedHashMap<>(args(security(), "PEGA security",
                List.of("PegaSecurityEvent")));
        unasked.remove("own_output_feed");

        final Map<String, Object> refused = world.call(tool, unasked);

        assertThat(String.valueOf(refused.get("error"))).contains("is new", "'PEGA PEGA security V1.0'",
                "Ask the user where its Events go", "Into the same feed", "To an output feed of its own",
                "own_output_feed=false (the same feed)", "Nothing was saved").doesNotContain("index");
        assertThat(xsltsInBuild()).isEmpty();
        // Which pipeline is no longer asked: a new group has no pipeline in production to change.
        unasked.put("own_output_feed", false);
        final Map<String, Object> saved = world.call(tool, unasked);
        assertThat(saved.get("problems")).as("%s", saved).isEqualTo(List.of());
        assertThat(JsonValues.mapOrEmpty(rowNamed(written.getLast(), "PegaSecurityEvent").get("pipeline")))
                .doesNotContainKey("new_version");
    }

    @Test
    void promotionPointsTheBuildsModulesAtTheOriginalsOfItsWorkingCopies() {
        world.call(tool, args(security(), "PEGA security", List.of("PegaSecurityEvent")));
        final Map<String, String> docs = xsltsInBuild();
        final List<Map<String, Object>> listed = List.of(
                doc(docs.get("PEGA PEGA security V1.0"), "PEGA PEGA security V1.0", null),
                doc(docs.get("PEGA helpers"), "PEGA helpers", "o-helpers"),
                doc(docs.get("PEGA common"), "PEGA common", "o-common"));

        final List<String> done = world.groups.toOriginals(world.context(), BUILD, listed);

        assertThat(done).anySatisfy(line -> assertThat(line).contains("pointed 'PEGA PEGA security V1.0' at the "
                                                                      + "originals"));
        // The group (new) imports the common module's original, which imports the helpers' original; the kept
        // mappings name them too.
        assertThat(world.xslt(docs.get("PEGA PEGA security V1.0")).getData()).contains("uuid='o-common'")
                .doesNotContain("uuid='" + docs.get("PEGA common") + "'");
        assertThat(keptMapping(world.xslt(docs.get("PEGA PEGA security V1.0"))).module().common()).contains("o-common");
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
        assertThat(JsonValues.mapOrEmpty(db.get("pipeline"))).containsEntry("name", "PEGA PEGA integration V1.0");
        assertThat(db).containsEntry("output_feed", "PEGA-INTEGRATION-EVENTS");
        // The group's XSLT now translates both.
        assertThat(ModuleChecks.marker(world.xslt(xsltsInBuild().get("PEGA PEGA integration V1.0")).getData()))
                .hasValueSatisfying(marker -> assertThat(marker.sourceTypes()).containsExactly("PegaIntegration",
                        "PegaDB"));
        assertThat(JsonValues.strings(JsonValues.mapOrEmpty(saved.get("group")).get("notes")))
                .anySatisfy(note -> assertThat(note).contains("PegaDB", "PEGA-INTEGRATION-EVENTS"));
    }

    @Test
    void theGroupAndItsSourceTypesDefaultToTheScopesAManyTypeGroupTranslatingEach() {
        scoped("PEGA back end", List.of("PegaIntegration", "PegaDB"));
        final Map<String, Object> given = new LinkedHashMap<>(args(integration(), null, List.of()));
        given.remove("group");
        given.remove("source_types");

        final Map<String, Object> saved = world.call(tool, given);

        assertThat(saved.get("problems")).as("%s", saved).isEqualTo(List.of());
        final Map<String, Object> group = JsonValues.mapOrEmpty(saved.get("group"));
        assertThat(group).containsEntry("name", "PEGA back end").containsEntry("mode", "pega_back_end");
        assertThat(JsonValues.strings(group.get("source_types"))).containsExactly("PegaIntegration", "PegaDB");
        // One XSLT, one pipeline, for both source types; the third passed over.
        assertThat(xsltsInBuild()).containsOnlyKeys("PEGA PEGA back end V1.0", "PEGA common", "PEGA helpers");
        assertThat(ModuleChecks.marker(world.xslt(xsltsInBuild().get("PEGA PEGA back end V1.0")).getData()))
                .hasValueSatisfying(marker -> assertThat(marker.sourceTypes()).containsExactly("PegaIntegration",
                        "PegaDB"));
        for (final String type : List.of("PegaIntegration", "PegaDB")) {
            assertThat(rowNamed(written.getLast(), type)).containsEntry("status", SourceTypeRegistry.TRANSLATED)
                    .containsEntry("group", "PEGA back end");
            assertThat(JsonValues.mapOrEmpty(rowNamed(written.getLast(), type).get("pipeline")))
                    .containsEntry("name", "PEGA PEGA back end V1.0");
        }
        assertThat(rowNamed(written.getLast(), "PegaSecurityEvent")).containsEntry("status",
                SourceTypeRegistry.PARKED);
    }

    @Test
    void aSecondGroupInABuildIsRefused() {
        scoped("PEGA security", List.of("PegaSecurityEvent"));
        world.call(tool, args(security(), "PEGA security", List.of("PegaSecurityEvent")));

        final Map<String, Object> refused = world.call(tool, args(integration(), "PEGA integration",
                List.of("PegaIntegration")));

        assertThat(String.valueOf(refused.get("error"))).contains("one source type group a build",
                "'PEGA security'", "'PEGA integration'", "later build", "promoting");
        assertThat(xsltsInBuild()).containsKey("PEGA PEGA security V1.0")
                .doesNotContainKey("PEGA PEGA integration V1.0");
        assertThat(rowNamed(written.getLast(), "PegaIntegration")).containsEntry("status",
                SourceTypeRegistry.PARKED);
    }

    @Test
    void anExistingGroupTheScopeNamesKeepsItsSourceTypesAndTakesTheScopes() {
        world.call(tool, args(security(), "PEGA security", List.of("PegaSecurityEvent")));
        // A later scope adds PegaDB to the group.
        scoped("PEGA security", List.of("PegaDB"));
        final Map<String, Object> given = new LinkedHashMap<>(args(security(), null, List.of()));
        given.remove("group");
        given.remove("source_types");
        given.remove("own_output_feed");

        final Map<String, Object> saved = world.call(tool, given);

        assertThat(saved.get("problems")).as("%s", saved).isEqualTo(List.of());
        assertThat(JsonValues.strings(JsonValues.mapOrEmpty(saved.get("group")).get("source_types")))
                .containsExactly("PegaSecurityEvent", "PegaDB");
        assertThat(ModuleChecks.marker(world.xslt(xsltsInBuild().get("PEGA PEGA security V1.0")).getData()))
                .hasValueSatisfying(marker -> assertThat(marker.sourceTypes()).containsExactly("PegaSecurityEvent",
                        "PegaDB"));
        // PegaDB joins the group where it runs.
        assertThat(JsonValues.mapOrEmpty(rowNamed(written.getLast(), "PegaDB").get("pipeline")))
                .containsEntry("name", "PEGA PEGA security V1.0");
    }

    @Test
    void aPromotedGroupRegeneratedByUuidTakesTheSourceTypesTheScopeJoinsToIt() {
        // Seen in eval case 61: OneDrive joined the promoted Messaging group in the build's scope, yet
        // build_translation_xslt group=Messaging uuid=<its promoted XSLT> took the group's source types from the
        // XSLT's kept mapping: the sample check read no OneDrive record, and the group's XSLT would pass them over.
        final String original = promotedGroup();
        scoped("PEGA security", List.of("PegaSecurityEvent", "PegaDB"));
        final Map<String, Object> again = new LinkedHashMap<>();
        again.put("uuid", original);
        again.put("build", BUILD);
        again.put("feeds", List.of("PEGA"));
        again.put("group", "PEGA security");

        final Map<String, Object> saved = world.call(tool, again);

        assertThat(saved.get("problems")).as("%s", saved).isEqualTo(List.of());
        assertThat(JsonValues.strings(JsonValues.mapOrEmpty(saved.get("group")).get("source_types")))
                .containsExactly("PegaSecurityEvent", "PegaDB");
        // Saved in the build's new version of it, not over the promoted XSLT.
        final String copy = xsltsInBuild().get("PEGA PEGA security V1.1");
        assertThat(copy).isNotEqualTo(original);
        assertThat(world.world.records().entry(BUILD, copy)).containsEntry("version_of", original);
        assertThat(ModuleChecks.marker(world.xslt(copy).getData()))
                .hasValueSatisfying(marker -> assertThat(marker.sourceTypes()).containsExactly("PegaSecurityEvent",
                        "PegaDB"));
        assertThat(ModuleChecks.marker(world.xslt(original).getData()))
                .hasValueSatisfying(marker -> assertThat(marker.sourceTypes()).containsExactly(
                        "PegaSecurityEvent"));
        // The registry's rows: PegaDB translated by the group, where it runs.
        assertThat(rowNamed(written.getLast(), "PegaDB")).containsEntry("status", SourceTypeRegistry.TRANSLATED)
                .containsEntry("group", "PEGA security");
        assertThat(JsonValues.mapOrEmpty(rowNamed(written.getLast(), "PegaDB").get("group_xslt")))
                .containsEntry("uuid", copy);
        assertThat(rowNamed(written.getLast(), "PegaIntegration")).containsEntry("status",
                SourceTypeRegistry.PARKED);
        // Named again by the promoted uuid, the working copy's mapping is the one in hand.
        final Map<String, Object> regenerated = world.call(tool, again);
        assertThat(regenerated.get("problems")).as("%s", regenerated).isEqualTo(List.of());
        assertThat(JsonValues.mapOrEmpty(regenerated.get("saved"))).containsEntry("uuid", copy);
    }

    @Test
    void sourceTypesGivenThatLeaveOutOneTheScopePutsInTheGroupAreRefused() {
        final String original = promotedGroup();
        scoped("PEGA security", List.of("PegaSecurityEvent", "PegaDB"));
        final Map<String, Object> given = new LinkedHashMap<>();
        given.put("uuid", original);
        given.put("build", BUILD);
        given.put("feeds", List.of("PEGA"));
        given.put("group", "PEGA security");
        given.put("source_types", List.of("PegaSecurityEvent"));
        given.put("pipeline", "change");

        final Map<String, Object> refused = world.call(tool, given);

        assertThat(String.valueOf(refused.get("error"))).contains("source_types leaves out ['PegaDB']",
                "group 'PEGA security'", "Leave source_types out", "Nothing was saved");
        assertThat(xsltsInBuild()).doesNotContainKey("PEGA PEGA security V1.0");
        assertThat(rowNamed(written.getLast(), "PegaDB")).containsEntry("status", SourceTypeRegistry.PARKED);
        // All of them given: saved.
        given.put("source_types", List.of("PegaSecurityEvent", "PegaDB"));
        assertThat(world.call(tool, given).get("problems")).isEqualTo(List.of());
        assertThat(rowNamed(written.getLast(), "PegaDB")).containsEntry("status", SourceTypeRegistry.TRANSLATED);
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
        assertThat(xsltsInBuild()).doesNotContainKey("PEGA PEGA security V1.0");
    }

    @Test
    void aSourceTypeAnotherGroupTranslatesIsRefused() {
        world.call(tool, args(security(), "PEGA security", List.of("PegaSecurityEvent")));

        final Map<String, Object> refused = world.call(tool, args(integration(), "PEGA integration",
                List.of("PegaIntegration", "PegaSecurityEvent")));

        assertThat(String.valueOf(refused.get("error"))).contains("PegaSecurityEvent", "'PEGA security'");
        assertThat(rowNamed(written.getLast(), "PegaSecurityEvent")).containsEntry("group", "PEGA security");
        assertThat(xsltsInBuild()).doesNotContainKey("PEGA PEGA integration V1.0");
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
        world.answers.put("Source type group 'PEGA security'", "To an output feed of its own (made next)");
        final Map<String, Object> asked = new LinkedHashMap<>(args(security(), "PEGA security",
                List.of("PegaSecurityEvent")));
        asked.remove("own_output_feed");

        final Map<String, Object> saved = world.call(tool, asked);

        assertThat(saved.get("problems")).as("%s", saved).isEqualTo(List.of());
        assertThat(placementForms()).singleElement().satisfies(form -> assertThat(form).contains(
                "a pipeline of its own, 'PEGA PEGA security V1.0'", "where its Events go").doesNotContain("**", "`",
                "index"));
        assertThat(rowNamed(written.getLast(), "PegaSecurityEvent")).containsEntry("output_feed", "");
        // Saved again in the build: placed, so it isn't asked again.
        world.call(tool, asked);
        assertThat(placementForms()).hasSize(1);
    }

    @Test
    void aNewGroupIsAskedWhereItsEventsGoBeforeTheSampleIsChecked() {
        // Decided by the user (2026-11-20), seen in eval case 36 on Haiku over Claude Code: on a 1,000-record sample
        // the check ran ~61 s before the call's first form, and the client, which gives up on a call after 60 s with
        // nothing sent on it (a form resets its clock), gave up. A question that doesn't depend on the check is
        // asked before it.
        world.forms = true;
        world.answers.put("Source type group 'PEGA security'", "To an output feed of its own (made next)");
        final List<Integer> formsAtTheCheck = new ArrayList<>();
        world.clock = clockNoting(() -> formsAtTheCheck.add(placementForms().size()));
        final Map<String, Object> asked = new LinkedHashMap<>(args(security(), "PEGA security",
                List.of("PegaSecurityEvent")));
        asked.remove("own_output_feed");

        final Map<String, Object> saved = world.call(world.buildTool(), asked);

        assertThat(saved.get("problems")).as("%s", saved).isEqualTo(List.of());
        assertThat(formsAtTheCheck).as("forms asked when the check started").isNotEmpty().first().isEqualTo(1);
        assertThat(placementForms()).hasSize(1);
        // The client is told the check is going on, so one that would give up on a silent call waits.
        assertThat(world.progress).anySatisfy(message -> assertThat(message).startsWith("Checking the mapping"));
    }

    @Test
    void whereANewGroupsEventsGoIsNotAskedAgainAfterACallThatSavedNothing() {
        // Asked before the check, the answer is kept for the next call when this one saves nothing (here its check
        // fails).
        world.forms = true;
        world.answers.put("Source type group 'PEGA security'", "To an output feed of its own (made next)");
        world.clock = clockNoting(() -> {
            throw new ToolRefusal("the check failed");
        });
        final Map<String, Object> asked = new LinkedHashMap<>(args(security(), "PEGA security",
                List.of("PegaSecurityEvent")));
        asked.remove("own_output_feed");
        assertThat(world.call(world.buildTool(), asked).get("error")).asString().contains("the check failed");
        assertThat(placementForms()).hasSize(1);

        world.clock = Clock.systemUTC();
        final Map<String, Object> saved = world.call(world.buildTool(), asked);

        assertThat(saved.get("problems")).as("%s", saved).isEqualTo(List.of());
        assertThat(placementForms()).as("not asked again").hasSize(1);
        assertThat(rowNamed(written.getLast(), "PegaSecurityEvent")).containsEntry("output_feed", "");
    }

    @Test
    void whereANewGroupsEventsGoIsTakenAsTheUserSaidOnceItsFormCouldntBeShown() {
        // Seen over /mcp: the client answered the placement form with an error, and as own_output_feed was read
        // only after asking, every call was refused the same way.
        world.forms = true;
        world.unshowable.add("Source type group 'PEGA security'");
        final Map<String, Object> asked = new LinkedHashMap<>(args(security(), "PEGA security",
                List.of("PegaSecurityEvent")));
        asked.remove("own_output_feed");

        final Map<String, Object> refused = world.call(tool, asked);

        assertThat(String.valueOf(refused.get("error"))).contains("The client couldn't show the form",
                "Ask the user where its Events go", "own_output_feed=false (the same feed)", "Nothing was saved");
        assertThat(placementForms()).hasSize(1);
        asked.put("own_output_feed", true);
        final Map<String, Object> saved = world.call(tool, asked);
        assertThat(saved.get("problems")).as("%s", saved).isEqualTo(List.of());
        assertThat(placementForms()).as("not asked again").hasSize(1);
        assertThat(rowNamed(written.getLast(), "PegaSecurityEvent")).containsEntry("output_feed", "");
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
    void aChangeToAGroupWhosePipelineIsPromotedGoesToANewVersionWithoutAsking() {
        // Decided by the user (2026-11-17): a promoted pipeline and the code it runs are never changed in place, so
        // there is nothing to ask: the group's change is a new version of its XSLT, for a new version of its
        // pipeline.
        final String original = promotedGroup();
        final String code = world.xslt(original).getData();
        world.forms = true;
        final Map<String, Object> again = new LinkedHashMap<>(args(security(), "PEGA security",
                List.of("PegaSecurityEvent")));
        again.remove("own_output_feed");

        final Map<String, Object> saved = world.call(tool, again);

        assertThat(saved.get("problems")).as("%s", saved).isEqualTo(List.of());
        assertThat(world.asked).noneMatch(form -> form.contains("change that pipeline"));
        // Named for its pipeline's new version, as copy_pipeline names what it copies (seen in testing: under the
        // original's name, it was told apart only by its version folder).
        final String version = xsltsInBuild().get("PEGA PEGA security V1.1");
        assertThat(version).isNotNull().isNotEqualTo(original);
        assertThat(xsltsInBuild()).doesNotContainKey("PEGA PEGA security V1.0");
        assertThat(world.world.records().entry(BUILD, version)).containsEntry("version_of", original)
                .doesNotContainKey("copy_of");
        assertThat(world.xslt(original).getData()).isEqualTo(code);
        final Map<String, Object> row = rowNamed(written.getLast(), "PegaSecurityEvent");
        assertThat(JsonValues.mapOrEmpty(row.get("group_xslt"))).containsEntry("uuid", version);
        assertThat(JsonValues.mapOrEmpty(row.get("pipeline"))).containsEntry("uuid", "o-pipeline")
                .containsEntry("new_version", true);
        assertThat(String.valueOf(JsonValues.mapOrEmpty(saved.get("group")).get("group_next")))
                .contains("copy_pipeline").doesNotContain("working_copy=true");
    }

    @Test
    void aGroupsChangeToThePromotedCommonModuleMakesANewVersionOfItThatOnlyThatGroupImports() {
        // Decided by the user (2026-11-17): a promoted module is never changed in place. The group's new version
        // imports the common module's new version; the feed's other group keeps importing the one it imports.
        final Map<String, String> promoted = promotedFeed();
        final String commonCode = world.xslt(promoted.get("PEGA common")).getData();
        final String securityCode = world.xslt(promoted.get("PEGA PEGA security V1.0")).getData();
        eventMetaLibrary();
        final Map<String, Object> calling = new LinkedHashMap<>(integration());
        calling.put("shared", List.of(Map.of("href", SourceTypeGroups.href("u-meta", "Event Meta"), "template",
                "event-meta", "at", "Meta")));
        final Map<String, Object> again = new LinkedHashMap<>(args(calling, "PEGA integration",
                List.of("PegaIntegration")));
        again.remove("own_output_feed");

        final Map<String, Object> saved = world.call(tool, again);

        assertThat(saved.get("problems")).as("%s", saved).isEqualTo(List.of());
        // The common module's new version (V1.1: the promoted one has no version of its own), not the helpers'.
        assertThat(xsltsInBuild()).containsOnlyKeys("PEGA common V1.1", "PEGA PEGA integration V1.1");
        final String common = xsltsInBuild().get("PEGA common V1.1");
        assertThat(world.world.records().entry(BUILD, common)).containsEntry("version_of", promoted.get(
                "PEGA common")).doesNotContainKey("copy_of");
        assertThat(ModuleChecks.hrefs(world.xslt(common).getData()))
                .anySatisfy(href -> assertThat(href).contains("u-meta"))
                .anySatisfy(href -> assertThat(href).contains(promoted.get("PEGA helpers")));
        assertThat(ModuleChecks.hrefs(world.xslt(xsltsInBuild().get("PEGA PEGA integration V1.1")).getData()))
                .singleElement().satisfies(href -> assertThat(href).contains(common, "PEGA common V1.1"));
        // Nothing promoted rewritten: the other group imports the promoted common module as before.
        assertThat(world.xslt(promoted.get("PEGA common")).getData()).isEqualTo(commonCode);
        assertThat(world.xslt(promoted.get("PEGA PEGA security V1.0")).getData()).isEqualTo(securityCode)
                .contains("uuid='" + promoted.get("PEGA common") + "'");
        assertThat(JsonValues.strings(JsonValues.mapOrEmpty(saved.get("group")).get("notes")))
                .anySatisfy(note -> assertThat(note).contains("event-meta", "as a new version of it in this build"));
        // The registry names the feed's newest, for a new group to import.
        assertThat(JsonValues.mapOrEmpty(JsonValues.mapOrEmpty(SourceTypeRegistry.read(written.getLast())
                .get("modules")).get("common"))).containsEntry("uuid", common);

        // Saved again in the build: its new version changed where it is, not another made.
        world.call(tool, again);
        assertThat(xsltsInBuild()).containsOnlyKeys("PEGA common V1.1", "PEGA PEGA integration V1.1");

        // The feed's groups import different versions: said, as information.
        assertThat(world.groups.mixedModuleVersions(BUILD)).singleElement().satisfies(line -> assertThat(line)
                .contains("different versions of its common module", "'PEGA common' (PEGA security)",
                        "'PEGA common V1.1' (PEGA integration)"));
    }

    @Test
    void aCommonModulesFeedIsToldFromItsNameOrAVersionOfIt() {
        // Seen reviewing the docs' group line: a version's name no longer ended with " common".
        assertThat(SourceTypeGroups.feedOfCommon("PEGA common")).contains("PEGA");
        assertThat(SourceTypeGroups.feedOfCommon("PEGA common V1.1")).contains("PEGA");
        assertThat(SourceTypeGroups.feedOfCommon("ACME common feed common V2")).contains("ACME common feed");
        assertThat(SourceTypeGroups.feedOfCommon("PEGA helpers V1.1")).isEmpty();
        assertThat(SourceTypeGroups.feedOfCommon("PEGA common copy")).isEmpty();
        assertThat(SourceTypeGroups.feedOfCommon(null)).isEmpty();
    }

    @Test
    void outOfDateHelpersMakeANewVersionOfThemAndOfTheCommonModuleImportingThem() {
        // The generator writes the helpers otherwise now: a promoted one changes only by a new version, and the
        // common module importing it (by UUID) is a new version too, imported only by the build's group.
        final Map<String, String> promoted = promotedFeed();
        final String helpers = promoted.get("PEGA helpers");
        final String older = HelpersXslt.generate().replaceFirst("version=\"" + HelpersXslt.VERSION + "\"",
                "version=\"0\"");
        assertThat(HelpersXslt.isCurrent(older)).isFalse();
        world.world.docs.put(helpers, world.xslt(helpers).copy().data(older).build());
        final String commonCode = world.xslt(promoted.get("PEGA common")).getData();
        final Map<String, Object> again = new LinkedHashMap<>(args(security(), "PEGA security",
                List.of("PegaSecurityEvent")));
        again.remove("own_output_feed");

        final Map<String, Object> saved = world.call(tool, again);

        assertThat(saved.get("problems")).as("%s", saved).isEqualTo(List.of());
        assertThat(xsltsInBuild()).containsOnlyKeys("PEGA helpers V1.1", "PEGA common V1.1", "PEGA PEGA security V1.1");
        final String helpersVersion = xsltsInBuild().get("PEGA helpers V1.1");
        final String commonVersion = xsltsInBuild().get("PEGA common V1.1");
        assertThat(world.xslt(helpersVersion).getData()).isEqualTo(HelpersXslt.generate());
        assertThat(world.world.records().entry(BUILD, helpersVersion)).containsEntry("version_of", helpers);
        assertThat(ModuleChecks.hrefs(world.xslt(commonVersion).getData())).singleElement()
                .isEqualTo(SourceTypeGroups.href(helpersVersion, "PEGA helpers V1.1"));
        assertThat(keptMapping(world.xslt(commonVersion)).module().helpers())
                .isEqualTo(SourceTypeGroups.href(helpersVersion, "PEGA helpers V1.1"));
        assertThat(ModuleChecks.hrefs(world.xslt(xsltsInBuild().get("PEGA PEGA security V1.1")).getData()))
                .singleElement().satisfies(href -> assertThat(href).contains(commonVersion));
        // The promoted ones as they were: the other group's chain is production's.
        assertThat(world.xslt(helpers).getData()).isEqualTo(older);
        assertThat(world.xslt(promoted.get("PEGA common")).getData()).isEqualTo(commonCode);
        assertThat(world.groups.mixedModuleVersions(BUILD)).hasSize(2)
                .anySatisfy(line -> assertThat(line).contains("helpers", "'PEGA helpers' (PEGA integration)",
                        "'PEGA helpers V1.1' (PEGA security)"));
    }

    @Test
    void aLaterGroupWritesItsUsersIdsAsTheFeedsPromotedGroupsDo() {
        // Seen in an eval: a later build's group was asked again whether to split the domain off users' Ids, "Split"
        // first, though the feed's promoted group kept them whole; one feed's groups got different Id formats.
        promotedGroup();
        final Map<String, Object> db = new LinkedHashMap<>(args(security(), "PEGA db", List.of("PegaDB")));
        db.put("sample", "{\"timestamp\": \"2026-09-28T10:00:00.000Z\", \"hostname\": \"h1\", "
                         + "\"sourcetype\": \"PegaDB\", \"app\": \"pega\", \"message\": \"{\\\"eventType\\\": "
                         + "\\\"login\\\", \\\"operatorId\\\": \\\"ACME\\\\\\\\bob\\\"}\"}\n");
        final Map<String, Object> saved = world.call(tool, db);

        assertThat(saved.get("problems")).as("%s", saved).isEqualTo(List.of());
        assertThat(world.asked).noneMatch(form -> form.startsWith("Split the domain off"));
        assertThat(String.valueOf(saved.get("domain_split"))).contains("Not asked", DomainSplits.KEEP,
                "PEGA security");
        assertThat(JsonValues.listOrEmpty(saved.get("warnings"))).noneMatch(w -> String.valueOf(w).contains(
                "domain-qualified"));
        final XsltDoc xslt = world.xslt(xsltsInBuild().get("PEGA PEGA db V1.0"));
        assertThat(KeptMapping.readBlock(xslt.getDescription(), TranslationBuilds.DOMAIN_CHOSEN).orElseThrow())
                .containsValue(DomainSplits.KEEP);
        assertThat(String.valueOf(KeptMapping.readMapping(xslt.getDescription()).orElseThrow().payload()))
                .doesNotContain("strip_domain");
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
        assertThat(run.xslt()).isEqualTo(xsltsInBuild().get("PEGA PEGA security V1.0"));
        assertThat(run.outputFeed()).isEmpty();
        assertThat(world.groups.groupOf(BUILD, xsltsInBuild().get("PEGA PEGA security V1.0")))
                .hasValueSatisfying(found -> assertThat(found.group()).isEqualTo("PEGA security"));
        assertThat(world.groups.groupOf(BUILD, xsltsInBuild().get("PEGA common"))).isEmpty();
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

    // The build's scope recorded in its registry, as record_source_types records it: its group and source types.
    private void scoped(final String group, final List<String> sourceTypes) {
        final Map<String, Object> state = SourceTypeRegistry.read(written.isEmpty()
                ? registry().doc().text()
                : written.getLast());
        state.put("scope", Map.of("build", BUILD, "group", group, "source_types", sourceTypes, "by", "user", "at",
                "2026-11-13T00:00:00Z"));
        written.add(SourceTypeRegistry.render(state));
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
        final String uuid = xsltsInBuild().get("PEGA PEGA security V1.0");
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

    // The security and integration groups as a first build promoted them: their XSLTs and pipelines, and the feed's
    // common module and helpers, in production beside the feed, the registry naming them. Returns their UUIDs by
    // name.
    private Map<String, String> promotedFeed() {
        world.call(tool, args(security(), "PEGA security", List.of("PegaSecurityEvent")));
        world.call(tool, args(integration(), "PEGA integration", List.of("PegaIntegration")));
        final Map<String, String> docs = xsltsInBuild();
        assertThat(docs).containsOnlyKeys("PEGA PEGA security V1.0", "PEGA PEGA integration V1.0", "PEGA common",
                "PEGA helpers");
        final String folder = world.world.folder("System/Feeds/PEGA").uuid;
        docs.values().forEach(uuid -> world.world.move(uuid, folder));
        world.world.add("Pipeline", "PEGA PEGA security", folder, "o-security");
        world.world.add("Pipeline", "PEGA PEGA integration", folder, "o-integration");
        written.add(withRows(written.getLast(), row -> {
            if ("PegaSecurityEvent".equals(row.get("name"))) {
                row.put("pipeline", Map.of("name", "PEGA PEGA security", "uuid", "o-security"));
            } else if ("PegaIntegration".equals(row.get("name"))) {
                row.put("pipeline", Map.of("name", "PEGA PEGA integration", "uuid", "o-integration"));
            }
        }));
        return docs;
    }

    // A site library with a shared template, event-meta.
    private void eventMetaLibrary() {
        world.world.add(XsltDoc.TYPE, "Event Meta", world.world.folder("System/Shared").uuid, "u-meta");
        world.world.docs.put("u-meta", XsltDoc.builder().uuid("u-meta").name("Event Meta")
                .data("<xsl:stylesheet xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\" version=\"3.0\">"
                      + "<xsl:template name=\"event-meta\"><Meta/></xsl:template></xsl:stylesheet>").build());
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
                                        {"path": "EventDetail/Authenticate/Action", "value": "Logon"},
                                        {"path": "EventDetail/Authenticate/Device/HostName", "field": "hostname"},
                                        {"path": "EventDetail/Authenticate/Data", "data_name": "correlation",
                                         "field": "soap/Envelope/Header/CorrelationId"}]}]}"""
                .formatted(WRAPPER.replace("\"PEGA\"", "\"PEGA Integration\"")));
    }

    // The mapping with its Generator the value given.
    private static Map<String, Object> withGenerator(final Map<String, Object> mapping, final String generator) {
        final Map<String, Object> out = new LinkedHashMap<>(mapping);
        out.put("common", JsonValues.maps(mapping.get("common")).stream()
                .map(entry -> "EventSource/Generator".equals(entry.get("path"))
                        ? Map.<String, Object>of("path", "EventSource/Generator", "value", generator)
                        : entry)
                .toList());
        return out;
    }

    // The mapping with its System/Name each source type's own, read from the source type field.
    private static Map<String, Object> perSourceType(final Map<String, Object> mapping,
                                                     final String first,
                                                     final String firstName) {
        final Map<String, Object> out = new LinkedHashMap<>(mapping);
        final Map<String, Object> names = new LinkedHashMap<>();
        names.put(first, firstName);
        names.put("PegaDB", "PEGA DB");
        out.put("common", JsonValues.maps(mapping.get("common")).stream()
                .map(entry -> "EventSource/System/Name".equals(entry.get("path"))
                        ? Map.<String, Object>of("path", "EventSource/System/Name", "field", "sourcetype", "map", names)
                        : entry)
                .toList());
        return out;
    }

    // A sample record of the source type, its message as given (escaped for a JSON string).
    private static String record(final String sourceType, final String message) {
        return "{\"timestamp\": \"2026-09-28T10:00:00.000Z\", \"hostname\": \"h1\", \"sourcetype\": \""
               + sourceType + "\", \"app\": \"pega\", \"message\": \"" + message + "\"}\n";
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

    // A clock that does something first each time it is read: the sample check reads it as it starts.
    private static Clock clockNoting(final Runnable first) {
        return new Clock() {
            @Override
            public ZoneId getZone() {
                return ZoneOffset.UTC;
            }

            @Override
            public Clock withZone(final ZoneId zone) {
                return this;
            }

            @Override
            public Instant instant() {
                first.run();
                return Instant.now();
            }
        };
    }
}
