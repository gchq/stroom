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

import stroom.ai.impl.AiAuditLog;
import stroom.ai.impl.tools.JsonValues;
import stroom.ai.impl.tools.ToolCallContext;
import stroom.ai.impl.tools.processing.XsltStages.How;
import stroom.ai.impl.tools.processing.XsltStages.Stage;
import stroom.ai.impl.tools.processing.XsltStages.StagedXslt;
import stroom.ai.impl.tools.sample.SurveyDoc;
import stroom.ai.impl.tools.validation.HeldSchemas;
import stroom.docref.DocRef;
import stroom.pipeline.shared.PipelineDoc;
import stroom.pipeline.shared.XsltDoc;
import stroom.util.json.JsonUtil;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;

/// `catalogue_event_types`' start choice offering to describe an existing hand-written pipeline in place (decided
/// with the user 2026-11-25), beside copying it as a new version and drafting afresh: what describing it covers
/// (its pipelines, feeds and an estimate) stated in the choice first; where its XSLT serves several feeds, the user
/// chooses which the survey covers, then it is described without asking and its feeds become the build's to survey.
class TestCatalogueDescribeInPlace {

    private static final String BUILD = "acme";

    private final BuildWorld world = new BuildWorld();
    private final HeldSchemas heldSchemas = Mockito.mock(HeldSchemas.class);
    private final SampleStreamTexts sampleStreamTexts = SampleStreamTextsMocks.mock();
    private final Consent consent = new Consent(() -> Mockito.mock(AiAuditLog.class));
    private final FakeBuildFacts facts = new FakeBuildFacts();
    private final BuildPlan plan = new BuildPlan(facts, world.guard(), consent, new BuildMemory());
    private final EventVersions eventVersions = new EventVersions(heldSchemas, consent, new BuildMemory(),
            world::guard, world::records, () -> Mockito.mock(EventsTargetInference.class));
    private final RecordSourceNotesTool notesTool = new RecordSourceNotesTool(world.guard(), world.documentAccess,
            new DocumentWrites(world.documentAccess), eventVersions, plan);
    private final TranslationBuilds builds = Mockito.mock(TranslationBuilds.class);
    private final ExistingTranslations existing = Mockito.mock(ExistingTranslations.class);
    private final InPlaceInduction inPlace = Mockito.mock(InPlaceInduction.class);
    private final CatalogueEventTypesTool tool = new CatalogueEventTypesTool(notesTool, sampleStreamTexts, consent,
            plan, builds, world::records, new AtomicLong(1_800_000_000_000L)::get);
    private InPlaceInduction.Scope scope;

    @BeforeEach
    void setUp() {
        tool.setExistingTranslations(() -> existing);
        tool.setInPlaceInduction(() -> inPlace);
        facts.sources = List.of("ACME-A");
        facts.docs.add(FakeBuildFacts.doc("Documentation", "s", "ACME-A - Survey"));
        Mockito.when(builds.surveyStatesInBuild(BUILD)).thenReturn(List.of(Map.of("feed", "ACME-A")));
        facts.existing = Map.of("name", "ACME-Events", "uuid", "prod-ev");
        final Map<String, Object> described = new LinkedHashMap<>(facts.existing);
        described.put("built_by_tools", false);
        described.put("event_types", 2);
        Mockito.when(existing.describe(Mockito.anyMap())).thenReturn(described);
        world.folder("System/" + BuildWorld.WORKSPACE + "/" + BUILD);
        final Map<String, FeedVolumes.Volume> volumes = new LinkedHashMap<>();
        for (final String feed : List.of("ACME-A", "ACME-B")) {
            volumes.put(feed, new FeedVolumes.Volume(feed, XsltUsers.RAW_EVENTS, 4, 4000L, false, null, null,
                    false));
        }
        scope = new InPlaceInduction.Scope(new DocRef(PipelineDoc.TYPE, "prod-ev", "ACME-Events"),
                new StagedXslt("translationFilter", new DocRef(XsltDoc.TYPE, "tr", "ACME-Events"),
                        Stage.TRANSLATION, false, "ACME-Events", How.CONTENT), false,
                new XsltUsers.Users("tr", List.of(new XsltUsers.User(new DocRef(PipelineDoc.TYPE, "prod-ev",
                        "ACME-Events"), true, List.of("ACME-A", "ACME-B"))), volumes, true));
        Mockito.when(inPlace.scope("prod-ev")).thenReturn(Optional.of(scope));
        Mockito.when(inPlace.describe(any(), eq(BUILD), anyString(), any(), anyList())).thenAnswer(inv -> {
            final Map<String, Object> out = new LinkedHashMap<>();
            out.put("status", "described_in_place");
            out.put("feeds", inv.getArgument(4));
            return out;
        });
    }

    @Test
    void theChoiceOffersToDescribeItInPlaceStatingWhatThatCoversFirst() {
        final BuildToolCalls.FormUser user = BuildToolCalls.user(Map.of("choice",
                CatalogueEventTypesTool.START_IN_PLACE, "answer", "All 2 feeds"));

        final Map<String, Object> result = call(Map.of("build", BUILD), user.context());

        assertThat(result).containsEntry("status", "described_in_place");
        // The choice said what describing it in place covers: its pipeline, both feeds and the estimate.
        assertThat(user.shown().getFirst()).contains(CatalogueEventTypesTool.START_IN_PLACE,
                "hidden blocks only", "no new version", "It runs in 1 pipeline ('ACME-Events')",
                "ACME-A (Raw Events): 4 streams", "ACME-B (Raw Events): 4 streams", "survey call");
        // Then which feeds the survey covers: every one, as answered.
        assertThat(user.shown()).hasSize(2);
        Mockito.verify(inPlace).describe(any(), eq(BUILD), anyString(), eq(scope), eq(List.of("ACME-A",
                "ACME-B")));
        assertThat(world.records().startFrom(BUILD).orElseThrow()).containsEntry("choice",
                InPlaceInduction.IN_PLACE);
        // Both feeds are the build's to survey now.
        assertThat(JsonValues.strings(world.records().load(BUILD).get(BuildRecords.SOURCE_FEEDS))).contains("ACME-A",
                "ACME-B");
        assertThat(String.valueOf(result.get("hint"))).contains("survey each feed (ACME-A, ACME-B)",
                "No new version");
    }

    @Test
    void copyingItRecordsWhereItsTranslationReadsItsRecords() {
        final BuildToolCalls.FormUser user = BuildToolCalls.user(Map.of("choice",
                CatalogueEventTypesTool.START_EXISTING));

        call(Map.of("build", BUILD), user.context());

        assertThat(world.records().startFrom(BUILD).orElseThrow()).containsEntry("choice",
                ExistingTranslations.EXISTING).containsEntry(InPlaceInduction.ELEMENT, "translationFilter")
                .containsEntry(InPlaceInduction.PRE_PROCESSED, false);
    }

    @Test
    void aRawSurveyIsRefusedForTheCatalogueOnceAPreProcessedTranslationIsDescribed() {
        // Seen in eval case 106 on Haiku: the catalogue was listed and confirmed against a survey of the raw records,
        // the plan's next (survey again at the translation's input) passed over.
        facts.startFrom = Map.of("choice", InPlaceInduction.IN_PLACE, "pipeline", Map.of("uuid", "prod-ev"),
                InPlaceInduction.IN_PLACE, Map.of(InPlaceInduction.ELEMENT, "translationFilter",
                        InPlaceInduction.PRE_PROCESSED, true));

        assertThat(text(Map.of("build", BUILD), BuildToolCalls.context())).contains("Survey again first",
                "survey_feed feed=ACME-A build=" + BUILD, "translation's input");

        // Surveyed there: on with the catalogue.
        Mockito.when(builds.surveyStatesInBuild(BUILD)).thenReturn(List.of(Map.of("feed", "ACME-A",
                SurveyDoc.READ_AT, SurveyDoc.AT_TRANSLATION_INPUT)));
        assertThat(text(Map.of("build", BUILD), BuildToolCalls.context())).doesNotContain("Survey again first");
    }

    @Test
    void theListingOfACatalogueDescribedInPlaceSaysWhereItsEventTypesCameFrom() {
        facts.startFrom = Map.of("choice", InPlaceInduction.IN_PLACE, "pipeline", Map.of("name", "ACME-Events",
                "uuid", "prod-ev"), InPlaceInduction.IN_PLACE, Map.of(InPlaceInduction.ELEMENT, "translationFilter",
                InPlaceInduction.PRE_PROCESSED, false));
        Mockito.when(sampleStreamTexts.read(List.of(7L))).thenReturn(new SampleStreamTexts.Texts(
                Map.of("stream 7", "time=2026-11-06T09:00:00Z action=login user=u1\n"), List.of()));

        final Map<String, Object> listed = call(Map.of("build", BUILD, "stream_ids", List.of(7)),
                BuildToolCalls.context());

        assertThat(String.valueOf(listed.get("origin"))).contains("from 'ACME-Events', the existing pipeline this "
                                                                  + "build describes in place", "TypeIds kept",
                "seed is the draft's");
    }

    @Test
    void theListingOfACatalogueDescribedWithoutAMappingSaysItsEventTypesComeFromTheSurvey() {
        facts.startFrom = Map.of("choice", InPlaceInduction.IN_PLACE, "pipeline", Map.of("name", "ACME-Events",
                "uuid", "prod-ev"), InPlaceInduction.IN_PLACE, Map.of(InPlaceInduction.ELEMENT, "translationFilter",
                InPlaceInduction.PRE_PROCESSED, false, InPlaceInduction.MAPPING_NOT_READ,
                "nothing in it reads as a mapping"));
        Mockito.when(sampleStreamTexts.read(List.of(7L))).thenReturn(new SampleStreamTexts.Texts(
                Map.of("stream 7", "time=2026-11-06T09:00:00Z action=login user=u1\n"), List.of()));

        final Map<String, Object> listed = call(Map.of("build", BUILD, "stream_ids", List.of(7)),
                BuildToolCalls.context());

        assertThat(String.valueOf(listed.get("origin"))).contains("'ACME-Events', the existing pipeline this "
                                                                  + "build describes in place",
                "couldn't be read back into a mapping (nothing in it reads as a mapping)", "proposed from the "
                                                                                          + "survey's evidence")
                .doesNotContain("TypeIds kept");
    }

    @Test
    void theUserNarrowsTheSurveyByNamingTheFeeds() {
        final BuildToolCalls.FormUser user = BuildToolCalls.user(Map.of("choice",
                CatalogueEventTypesTool.START_IN_PLACE, "answer", "only acme-b for now"));

        call(Map.of("build", BUILD), user.context());

        Mockito.verify(inPlace).describe(any(), eq(BUILD), anyString(), eq(scope), eq(List.of("ACME-B")));
    }

    @Test
    void anAnswerNamingNoFeedIsRefusedAndNothingIsDescribed() {
        final BuildToolCalls.FormUser user = BuildToolCalls.user(Map.of("choice",
                CatalogueEventTypesTool.START_IN_PLACE, "answer", "the usual ones"));

        assertThat(text(Map.of("build", BUILD), user.context())).contains("names none of the feeds",
                "survey_feeds=");
        Mockito.verify(inPlace, Mockito.never()).describe(any(), any(), any(), any(), any());
    }

    @Test
    void anXsltThatCouldntBeReadBackIsDescribedAsFarAsItCanBeTheHintSayingWhy() {
        // Decided by the user (2026-11-25): no mapping; its catalogue proposed from the survey's evidence.
        Mockito.when(inPlace.describe(any(), eq(BUILD), anyString(), any(), anyList())).thenAnswer(inv -> {
            final Map<String, Object> out = new LinkedHashMap<>();
            out.put("status", "described_in_place");
            out.put(InPlaceInduction.MAPPING_NOT_READ, "nothing in it reads as a mapping");
            out.put("feeds", inv.getArgument(4));
            return out;
        });
        final BuildToolCalls.FormUser user = BuildToolCalls.user(Map.of("choice",
                CatalogueEventTypesTool.START_IN_PLACE, "answer", "All 2 feeds"));

        final Map<String, Object> result = call(Map.of("build", BUILD), user.context());

        assertThat(result).containsEntry("status", "described_in_place")
                .containsEntry(InPlaceInduction.MAPPING_NOT_READ, "nothing in it reads as a mapping");
        assertThat(String.valueOf(result.get("hint"))).contains("couldn't be read back into a mapping (nothing in "
                                                                + "it reads as a mapping)", "no mapping is kept",
                "survey each feed (ACME-A, ACME-B)", "proposals=", "timeline role", "beside the pipeline",
                "No new version", "Say why to the user").doesNotContain("checks the catalogue read back");
        assertThat(JsonValues.strings(world.records().load(BUILD).get(BuildRecords.SOURCE_FEEDS))).contains("ACME-A",
                "ACME-B");
    }

    @Test
    void withoutAFormTheChoiceAndThenTheFeedsAreAskedInTheChat() {
        final Map<String, Object> asked = call(Map.of("build", BUILD), BuildToolCalls.context());
        assertThat(map(asked.get("routes"))).containsOnlyKeys(ExistingTranslations.EXISTING,
                InPlaceInduction.IN_PLACE, ExistingTranslations.DRAFT);
        assertThat(String.valueOf(asked.get("hint"))).contains("start_from=in_place");

        final Map<String, Object> feeds = call(Map.of("build", BUILD, "start_from", "in_place"),
                BuildToolCalls.context());
        assertThat(feeds).containsEntry("status", "needs_feeds").containsEntry("feeds", List.of("ACME-A",
                "ACME-B"));
        assertThat(String.valueOf(feeds.get("hint"))).contains("survey_feeds=");
        // Chosen already (the plan reads the build record): the next call asks only for the feeds.
        facts.startFrom = world.records().startFrom(BUILD).orElseThrow();
        final Map<String, Object> described = call(Map.of("build", BUILD, "survey_feeds", List.of("ACME-A")),
                BuildToolCalls.context());
        assertThat(described).containsEntry("status", "described_in_place");
        Mockito.verify(inPlace).describe(any(), eq(BUILD), anyString(), eq(scope), eq(List.of("ACME-A")));
    }

    @Test
    void aSingleFeedIsDescribedWithoutAskingWhich() {
        final XsltUsers.Users one = new XsltUsers.Users("tr", List.of(), Map.of("ACME-A", new FeedVolumes.Volume(
                "ACME-A", XsltUsers.RAW_EVENTS, 4, 4000L, false, null, null, false)), true);
        Mockito.when(inPlace.scope("prod-ev")).thenReturn(Optional.of(new InPlaceInduction.Scope(scope.pipeline(),
                scope.translation(), false, one)));
        final BuildToolCalls.FormUser user = BuildToolCalls.user(Map.of("choice",
                CatalogueEventTypesTool.START_IN_PLACE));

        call(Map.of("build", BUILD), user.context());

        assertThat(user.shown()).hasSize(1);
        Mockito.verify(inPlace).describe(any(), eq(BUILD), anyString(), any(), eq(List.of("ACME-A")));
    }

    @Test
    void aPipelineWithNoTranslationToDescribeIsOfferedOnlyTheCopyAndTheDraft() {
        Mockito.when(inPlace.scope("prod-ev")).thenReturn(Optional.empty());

        final Map<String, Object> asked = call(Map.of("build", BUILD), BuildToolCalls.context());

        assertThat(map(asked.get("routes"))).containsOnlyKeys(ExistingTranslations.EXISTING,
                ExistingTranslations.DRAFT);
        assertThat(text(Map.of("build", BUILD, "start_from", "in_place"), BuildToolCalls.context()))
                .contains("existing or draft, not 'in_place'");
    }

    private Map<String, Object> call(final Map<String, Object> arguments, final ToolCallContext context) {
        final String text = text(arguments, context);
        assertThat(text).as("a JSON result").startsWith("{");
        return BuildToolCalls.json(text);
    }

    private String text(final Map<String, Object> arguments, final ToolCallContext context) {
        return tool.execute(BuildToolCalls.call(CatalogueEventTypesTool.NAME, JsonUtil.writeValueAsString(arguments,
                false)), context).text();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(final Object value) {
        return (Map<String, Object>) value;
    }
}
