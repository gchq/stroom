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

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/// The plan's source types step for an aggregated feed: added once the survey's field is confirmed, done once the
/// registry is written and the scope chosen; never for a feed of one source; the whole plan of an assessment.
class TestBuildPlanSourceTypes {

    private final FakeBuildFacts facts = new FakeBuildFacts();
    private final BuildPlan plan = new BuildPlan(facts, new BuildWorld().guard(),
            new Consent(() -> Mockito.mock(AiAuditLog.class)), new BuildMemory());

    @Test
    void aFeedOfOneSourceNeverSeesTheStep() {
        surveyed(Map.of("aggregated", false));
        assertThat(state(status(), "source_types")).isEqualTo("not needed");
        surveyed(null);
        assertThat(state(status(), "source_types")).isEqualTo("not needed");
    }

    @Test
    void aConfirmedSourceTypeFieldAddsTheStepBeforeClassifying() {
        surveyed(Map.of("field", "sourcetype"));
        final Map<String, Object> result = status();
        assertThat(state(result, "source_types")).isEqualTo("to do");
        final Map<String, Object> next = JsonValues.map(result.get("next"));
        assertThat(next.get("step")).isEqualTo("source_types");
        assertThat(next.get("call")).isEqualTo(Map.of("tool", "record_source_types", "arguments",
                Map.of("build", "b", "feed", "ACME")));
        final List<Object> steps = JsonValues.listOrEmpty(result.get("steps")).stream()
                .map(step -> JsonValues.map(step).get("step"))
                .toList();
        assertThat(steps.indexOf("source_types")).isLessThan(steps.indexOf("classify"))
                .isGreaterThan(steps.indexOf("converter"));
    }

    @Test
    void theStepIsDoneOnceTheRegistryIsWrittenAndTheScopeChosen() {
        surveyed(Map.of("field", "sourcetype"));
        facts.docs.add(FakeBuildFacts.doc("Documentation", "r", "ACME source types"));
        assertThat(state(status(), "source_types")).isEqualTo("to do");
        surveyed(Map.of("field", "sourcetype", "scope", Map.of("source_types", List.of("PegaDB"))));
        assertThat(state(status(), "source_types")).isEqualTo("done");
    }

    @Test
    void anAssessmentEndsAtTheRegistry() {
        facts.kind = "assessment";
        facts.sources = List.of("ACME");
        final Map<String, Object> before = status();
        assertThat(JsonValues.map(before.get("next")).get("step")).isEqualTo("covered");
        assertThat(plan.plannedSteps("b")).extracting(step -> step.get("step"))
                .containsExactly("covered", "promoted");
        surveyed(Map.of("field", "sourcetype"));
        assertThat(plan.plannedSteps("b")).extracting(step -> step.get("step"))
                .containsExactly("source_types", "covered", "promoted");
        facts.docs.add(FakeBuildFacts.doc("Documentation", "r", "ACME source types"));
        assertThat(state(status(), "source_types")).isEqualTo("done");
    }

    @Test
    void aBuildWithAScopeDraftsThroughItAndJudgesCoverageWithinIt() {
        final Map<String, Object> scope = Map.of("build", "b", "source_types", List.of("PegaDB"), "share_percent",
                40.0);
        final BuildPlan.CallArgs args = new BuildPlan.CallArgs("b", List.of(), List.of(1L), List.of(), null, null,
                null, null, List.of("ACME"), false, null, null, null, null, null, scope);

        // The draft is given the build, so it reads the sample through the scope.
        assertThat(BuildPlan.nextCall("translation", args).call().get("arguments"))
                .isEqualTo(Map.of("stream_ids", List.of(1L), "build", "b"));
        assertThat(BuildPlan.nextCall("covered", args).then()).contains("Coverage is judged within the build's "
                                                                       + "scope, PegaDB (about 40.0% of the feed's "
                                                                       + "records read)");
        final BuildPlan.CallArgs without = BuildPlan.CallArgs.of("b", List.of(), List.of(1L), List.of(), null, null);
        assertThat(BuildPlan.nextCall("translation", without).call().get("arguments"))
                .isEqualTo(Map.of("stream_ids", List.of(1L)));
        assertThat(BuildPlan.nextCall("covered", without).then()).doesNotContain("scope");
    }

    @Test
    void theStepIsNoOnboardingStepInToolDescriptions() {
        assertThat(BuildPlan.onboardingChecklist()).extracting(step -> step.get("step"))
                .doesNotContain("source_types");
        assertThat(BuildPlan.checklist()).extracting(step -> step.get("step")).contains("source_types");
    }

    // The build surveys feed ACME, its survey state's source types as given.
    private void surveyed(final Map<String, Object> sourceTypes) {
        facts.docs.removeIf(doc -> "s".equals(doc.get("uuid")));
        facts.docs.add(FakeBuildFacts.doc("Documentation", "s", "ACME - Survey"));
        facts.sources = List.of("ACME");
        final Map<String, Object> survey = new LinkedHashMap<>();
        survey.put("saturated", false);
        survey.put("format", "json lines");
        if (sourceTypes != null) {
            survey.put("source_types", sourceTypes);
        }
        facts.survey = survey;
    }

    private Map<String, Object> status() {
        return plan.status("b", null);
    }

    private static Object state(final Map<String, Object> result, final String step) {
        return JsonValues.listOrEmpty(result.get("steps")).stream()
                .map(JsonValues::map)
                .filter(item -> step.equals(item.get("step")))
                .findFirst()
                .orElseThrow()
                .get("state");
    }
}
