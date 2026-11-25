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
import stroom.ai.impl.tools.sample.SurveyDoc;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/// The plan for a build whose feed (or source type group) a pipeline already translates (decided by the user,
/// 2026-11-24): after the survey, the catalogue's first call asks the user how the build starts; from the existing
/// pipeline, the plan routes through its copy (copy_pipeline), its XSLT read back (rebuild_mapping) and the catalogue
/// read back confirmed. Seen in chat 192: an existing promoted pipeline translated the build's source types, the plan
/// routed to drafting from scratch, and the agent spent some ten calls copying the old catalogue by hand.
class TestBuildPlanStartFromExisting {

    private static final Map<String, Object> EXISTING = Map.of("name", "ACME-V1.0-Events", "uuid", "prod-ev");

    private final FakeBuildFacts facts = new FakeBuildFacts();
    private final BuildPlan plan = new BuildPlan(facts, new BuildWorld().guard(),
            new Consent(() -> Mockito.mock(AiAuditLog.class)), new BuildMemory());

    @BeforeEach
    void surveyed() {
        facts.docs = List.of(FakeBuildFacts.doc("Documentation", "s", "ACME - Survey"));
        facts.sources = List.of("ACME");
        facts.catalogued = false;
    }

    @Test
    void afterTheSurveyTheCataloguesFirstCallAsksHowTheBuildStarts() {
        facts.existing = EXISTING;
        final Map<String, Object> result = status();
        assertThat(next(result).get("step")).isEqualTo(BuildPlan.CATALOGUE);
        assertThat(call(result).get("tool")).isEqualTo(CatalogueEventTypesTool.NAME);
        assertThat(String.valueOf(next(result).get("then")))
                .contains("how the build starts", "'ACME-V1.0-Events'", "the original isn't touched");
        assertThat(plan.existingToOffer("b")).contains(EXISTING);
    }

    @Test
    void withNoExistingPipelineTheCatalogueIsDraftedAsBefore() {
        final Map<String, Object> result = status();
        assertThat(next(result).get("step")).isEqualTo(BuildPlan.CATALOGUE);
        assertThat(String.valueOf(next(result).get("then"))).startsWith("lists the evidence");
        assertThat(plan.existingToOffer("b")).isEmpty();
    }

    @Test
    void theOfferIsMadeOnceAndNotToABuildWithATranslationOfItsOwn() {
        facts.existing = EXISTING;
        facts.startFrom = Map.of("choice", ExistingTranslations.DRAFT, "pipeline", EXISTING);
        assertThat(plan.existingToOffer("b")).isEmpty();
        assertThat(String.valueOf(next(status()).get("then"))).startsWith("lists the evidence");
        facts.startFrom = null;
        facts.docs = List.of(FakeBuildFacts.doc("Documentation", "s", "ACME - Survey"),
                FakeBuildFacts.doc("XSLT", "x", "ACME-V1.1-Events"));
        assertThat(plan.existingToOffer("b")).isEmpty();
    }

    @Test
    void startingFromTheExistingPipelineCopiesItFirst() {
        facts.startFrom = Map.of("choice", ExistingTranslations.EXISTING, "pipeline", EXISTING);
        final Map<String, Object> result = status();
        assertThat(next(result).get("step")).isEqualTo(BuildPlan.CATALOGUE);
        assertThat(call(result).get("tool")).isEqualTo("copy_pipeline");
        assertThat(arguments(result).get("source_uuid")).isEqualTo("prod-ev");
        assertThat(arguments(result).get("build")).isEqualTo("b");
        assertThat(String.valueOf(arguments(result).get("new_name"))).contains("ACME-V1.0-Events");
    }

    @Test
    void aCatalogueCarriedInAlreadyIsNoReasonToSkipTheCopy() {
        // A promoted catalogue beside the feed, carried into the build confirmed: still copied first.
        facts.startFrom = Map.of("choice", ExistingTranslations.EXISTING, "pipeline", EXISTING);
        facts.catalogued = true;
        assertThat(call(status()).get("tool")).isEqualTo("copy_pipeline");
        // The copy of a pipeline the tools built keeps its mapping: read back already, so on to stepping.
        facts.docs = List.of(FakeBuildFacts.doc("Documentation", "s", "ACME - Survey"),
                FakeBuildFacts.doc("Pipeline", "ev", "ACME-V1.1-Events", "version_of", "prod-ev"),
                FakeBuildFacts.doc("XSLT", "x", "ACME-V1.1-Events"));
        facts.stages = Map.of("ev", "translation");
        facts.mappings = Map.of("x", FakeBuildFacts.kept("translation", Map.of()));
        assertThat(next(status()).get("step")).isEqualTo("stepped");
    }

    @Test
    void theCopysXsltWrittenByHandIsReadBackThenTheCatalogueConfirmed() {
        facts.startFrom = Map.of("choice", ExistingTranslations.EXISTING, "pipeline", EXISTING);
        facts.docs = List.of(FakeBuildFacts.doc("Documentation", "s", "ACME - Survey"),
                FakeBuildFacts.doc("Pipeline", "ev", "ACME-V1.1-Events", "version_of", "prod-ev"),
                FakeBuildFacts.doc("XSLT", "x", "ACME-V1.1-Events"));
        facts.stages = Map.of("ev", "translation");
        final Map<String, Object> copied = status();
        // The catalogue stays to do though the build holds a translation: it is read back and confirmed.
        assertThat(next(copied).get("step")).isEqualTo(BuildPlan.CATALOGUE);
        assertThat(call(copied)).isEqualTo(Map.of("tool", "rebuild_mapping", "arguments", Map.of("uuid", "x",
                "pipeline_uuid", "ev")));
        assertThat(String.valueOf(next(copied).get("then"))).contains("existing_run", "accept_differences");
        // Read back (the mapping kept): the catalogue read back is checked and put to the user.
        facts.mappings = Map.of("x", FakeBuildFacts.kept("translation", Map.of()));
        final Map<String, Object> read = status();
        assertThat(next(read).get("step")).isEqualTo(BuildPlan.CATALOGUE);
        assertThat(call(read).get("tool")).isEqualTo(CatalogueEventTypesTool.NAME);
        assertThat(String.valueOf(next(read).get("then"))).contains("read back from 'ACME-V1.0-Events'",
                "confirm=true");
        // Confirmed: on to stepping the copy, as any new version.
        facts.catalogued = true;
        final Map<String, Object> confirmed = status();
        assertThat(TestBuildPlan.states(confirmed).get(BuildPlan.CATALOGUE)).isEqualTo("done");
        assertThat(next(confirmed).get("step")).isEqualTo("stepped");
    }

    @Test
    void draftingANewTranslationLeavesTheCatalogueNotNeededOnceTranslated() {
        facts.startFrom = Map.of("choice", ExistingTranslations.DRAFT, "pipeline", EXISTING);
        facts.docs = List.of(FakeBuildFacts.doc("Documentation", "s", "ACME - Survey"),
                FakeBuildFacts.doc("XSLT", "x", "ACME-V1.1-Events"));
        facts.mappings = Map.of("x", FakeBuildFacts.kept("translation", Map.of()));
        assertThat(TestBuildPlan.states(status()).get(BuildPlan.CATALOGUE)).isEqualTo("not needed");
    }

    @Test
    void aPipelineDescribedInPlaceIsSurveyedAndCataloguedWithNoNewVersion() {
        // Described in place (decided with the user 2026-11-25): no translation, pipeline, processing or promotion.
        facts.startFrom = Map.of("choice", InPlaceInduction.IN_PLACE, "pipeline", EXISTING);
        final Map<String, Object> result = status();
        assertThat(next(result).get("step")).isEqualTo(BuildPlan.CATALOGUE);
        assertThat(call(result).get("tool")).isEqualTo(CatalogueEventTypesTool.NAME);
        assertThat(String.valueOf(next(result).get("then"))).contains("described in place", "no new version",
                "'ACME-V1.0-Events'");
        @SuppressWarnings("unchecked") final List<Map<String, Object>> steps =
                (List<Map<String, Object>>) result.get("steps");
        assertThat(steps).filteredOn(step -> !"not needed".equals(step.get("state")))
                .extracting(step -> step.get("step"))
                .contains(BuildPlan.SURVEYED, BuildPlan.CATALOGUE)
                .doesNotContain("translation", "pipeline", "stepped", "processed", "promoted");

        // Catalogued: the build is done.
        facts.catalogued = true;
        assertThat(status().get("next")).isNull();
    }

    @Test
    void aRawSurveyNoLongerCountsOnceAPreProcessedTranslationIsDescribedInPlace() {
        // Decided 2026-11-25: its records are read at the translation's input; the survey made before the choice
        // read them as the parser does, so the plan has it made again (survey_feed resets it), no stream_ids asked.
        facts.survey = Map.of("saturated", true);
        facts.startFrom = Map.of("choice", InPlaceInduction.IN_PLACE, "pipeline", EXISTING, InPlaceInduction.IN_PLACE,
                Map.of(InPlaceInduction.ELEMENT, "translationFilter", InPlaceInduction.PRE_PROCESSED, true));
        final Map<String, Object> stale = status();
        assertThat(next(stale).get("step")).isEqualTo(BuildPlan.SURVEYED);
        assertThat(call(stale).get("tool")).isEqualTo("survey_feed");
        assertThat(arguments(stale).get("feed")).isEqualTo("ACME");
        assertThat(arguments(stale).containsKey("stream_ids")).isFalse();

        // Surveyed at the translation's input: on to the catalogue.
        facts.survey = Map.of("saturated", true, SurveyDoc.READ_AT, SurveyDoc.AT_TRANSLATION_INPUT);
        assertThat(next(status()).get("step")).isEqualTo(BuildPlan.CATALOGUE);
        // Copied as a new version too: the raw survey made again before the copy.
        facts.survey = Map.of("saturated", true);
        facts.startFrom = Map.of("choice", ExistingTranslations.EXISTING, "pipeline", EXISTING,
                InPlaceInduction.ELEMENT, "translationFilter", InPlaceInduction.PRE_PROCESSED, true);
        assertThat(next(status()).get("step")).isEqualTo(BuildPlan.SURVEYED);
        // No pre-processor: the raw survey counts.
        facts.startFrom = Map.of("choice", InPlaceInduction.IN_PLACE, "pipeline", EXISTING, InPlaceInduction.IN_PLACE,
                Map.of(InPlaceInduction.ELEMENT, "translationFilter", InPlaceInduction.PRE_PROCESSED, false));
        assertThat(next(status()).get("step")).isEqualTo(BuildPlan.CATALOGUE);
    }

    @Test
    void aCopyMadeWithoutTheStartChoiceStillHasItsCatalogueToDo() {
        // Seen in the multi-feed lane's eval runs: a hand-written pipeline copied before catalogue_event_types' start
        // offer was answered, and the plan let the catalogue drop to "not needed".
        facts.docs = List.of(FakeBuildFacts.doc("Documentation", "s", "ACME - Survey"),
                FakeBuildFacts.doc("Pipeline", "ev", "ACME-V1.1-Events", "version_of", "prod-ev"),
                FakeBuildFacts.doc("XSLT", "x", "ACME-V1.1-Events"));
        facts.stages = Map.of("ev", "translation");
        final Map<String, Object> unread = status();
        assertThat(next(unread).get("step")).isEqualTo(BuildPlan.CATALOGUE);
        assertThat(call(unread).get("tool")).isEqualTo("rebuild_mapping");

        // Read back: the catalogue it gave confirmed before going on.
        facts.mappings = Map.of("x", FakeBuildFacts.kept("translation", Map.of()));
        final Map<String, Object> readBack = status();
        assertThat(next(readBack).get("step")).isEqualTo(BuildPlan.CATALOGUE);
        assertThat(call(readBack).get("tool")).isEqualTo(CatalogueEventTypesTool.NAME);
        facts.catalogued = true;
        assertThat(next(status()).get("step")).isNotEqualTo(BuildPlan.CATALOGUE);
    }

    @Test
    void theOfferSaysTheBuildMayBeDescribedInPlace() {
        facts.existing = EXISTING;
        assertThat(String.valueOf(next(status()).get("then"))).contains("described in place", "no new version");
    }

    private Map<String, Object> status() {
        return plan.status("b", null);
    }

    private static Map<?, ?> next(final Map<String, Object> result) {
        return (Map<?, ?>) result.get("next");
    }

    private static Map<?, ?> call(final Map<String, Object> result) {
        return (Map<?, ?>) next(result).get("call");
    }

    private static Map<?, ?> arguments(final Map<String, Object> result) {
        return (Map<?, ?>) call(result).get("arguments");
    }
}
