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

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/// The plan for a build of two or more feeds of one source on data already in Stroom (decided by the user,
/// 2026-11-25): every feed is surveyed, and covered, before the build goes on; `next` names them in turn. Before,
/// the plan surveyed only the first feed and any one survey passed.
class TestBuildPlanSeveralFeeds {

    private final FakeBuildFacts facts = new FakeBuildFacts();
    private final BuildPlan plan = new BuildPlan(facts, new BuildWorld().guard(),
            new Consent(() -> Mockito.mock(AiAuditLog.class)), new BuildMemory());

    @Test
    void everyFeedIsSurveyedInTurnBeforeTheBuildGoesOn() {
        facts.sources = List.of("ACME-A", "ACME-B", "ACME-C");
        facts.catalogued = false;
        survey("ACME-A", true, "json lines");

        Map<String, Object> result = plan.status("b", null);

        assertThat(state(result, BuildPlan.SURVEYED)).isEqualTo("to do");
        assertThat(next(result).get("call")).isEqualTo(Map.of("tool", "survey_feed", "arguments", Map.of("feed",
                "ACME-B", "build", "b")));
        assertThat(result.get(BuildPlan.FEED_SURVEYS)).isEqualTo(feedSurveys(List.of("ACME-A"),
                List.of("ACME-B", "ACME-C"), List.of()));

        survey("ACME-B", true, "json lines");
        result = plan.status("b", null);
        assertThat(next(result).get("call")).isEqualTo(Map.of("tool", "survey_feed", "arguments", Map.of("feed",
                "ACME-C", "build", "b")));

        survey("ACME-C", true, "json lines");
        result = plan.status("b", null);
        assertThat(state(result, BuildPlan.SURVEYED)).isEqualTo("done");
        assertThat(next(result).get("step")).isEqualTo(BuildPlan.CATALOGUE);
    }

    @Test
    void aFeedWhoseSurveyReadNothingCountsAsSurveyed() {
        facts.sources = List.of("ACME-A", "ACME-B");
        facts.catalogued = false;
        survey("ACME-A", true, "json lines");
        facts.unreadableSurveys.put("ACME-B", "no stream holds anything readable");

        final Map<String, Object> result = plan.status("b", null);

        assertThat(state(result, BuildPlan.SURVEYED)).isEqualTo("done");
        assertThat(result.get(BuildPlan.FEED_SURVEYS)).isEqualTo(feedSurveys(List.of("ACME-A", "ACME-B"),
                List.of(), List.of()));
    }

    @Test
    void coverageNamesTheFeedNotCoveredYet() {
        facts.sources = List.of("ACME-A", "ACME-B");
        facts.docs = List.of(FakeBuildFacts.doc("Pipeline", "ev", "ACME-Events"),
                FakeBuildFacts.doc("XSLT", "x", "ACME-Events"));
        facts.stages = Map.of("ev", "translation");
        facts.mappings = Map.of("x", FakeBuildFacts.kept("translation", Map.of()));
        facts.stepped = true;
        survey("ACME-A", true, "json lines");
        survey("ACME-B", false, "json lines");

        final Map<String, Object> result = plan.status("b", null);

        assertThat(next(result).get("step")).isEqualTo("covered");
        assertThat(next(result).get("call")).isEqualTo(Map.of("tool", "survey_feed", "arguments", Map.of("feed",
                "ACME-B", "build", "b")));
        assertThat(((Map<?, ?>) result.get(BuildPlan.FEED_SURVEYS)).get("not_covered")).isEqualTo(
                List.of("ACME-B"));
    }

    @Test
    void theSurveyedFormatNamesTheFeedsSurveyedAndEachFormatWhereTheyDiffer() {
        facts.sources = List.of("ACME-A", "ACME-B");
        survey("ACME-B", true, "json lines");
        assertThat(plan.sampleFormat("b").orElseThrow()).containsEntry("feed", "ACME-B")
                .doesNotContainKey("feeds");

        survey("ACME-A", true, "xml fragments");
        final Map<String, Object> format = plan.sampleFormat("b").orElseThrow();
        assertThat(format).containsEntry("feeds", List.of("ACME-B", "ACME-A"))
                .containsEntry("formats_by_feed", Map.of("ACME-B", "json lines", "ACME-A", "xml fragments"));
    }

    @Test
    void aBuildOfOneFeedIsSurveyedAsBefore() {
        facts.sources = List.of("ACME");
        facts.catalogued = false;
        survey("ACME", false, "json lines");

        final Map<String, Object> result = plan.status("b", null);

        assertThat(state(result, BuildPlan.SURVEYED)).isEqualTo("done");
        assertThat(result).doesNotContainKey(BuildPlan.FEED_SURVEYS);
    }

    @Test
    void onlyTheFeedsTheBuildIsForAreSurveyed() {
        // A copied production pipeline also reads ACME-OLD: the build is for ACME-A and ACME-B only.
        facts.sources = List.of("ACME-A", "ACME-B", "ACME-OLD");
        facts.buildSources = List.of("ACME-A", "ACME-B");
        facts.catalogued = false;
        survey("ACME-A", true, "json lines");
        survey("ACME-B", true, "json lines");

        assertThat(state(plan.status("b", null), BuildPlan.SURVEYED)).isEqualTo("done");
    }

    @Test
    void theExistingDataStartListsEveryFeed() {
        facts.sources = List.of("ACME-A", "ACME-B");

        final Map<String, Object> result = plan.status("b", null);

        assertThat(next(result).get("call")).isEqualTo(Map.of("tool", "stream_meta", "arguments", Map.of("feeds",
                List.of("ACME-A", "ACME-B"))));
        assertThat(String.valueOf(next(result).get("several_feeds"))).contains("for 2 feeds (ACME-A, ACME-B)",
                "survey_feed build=b feed=<each>");
    }

    private void survey(final String feed, final boolean saturated, final String format) {
        final Map<String, Object> state = new LinkedHashMap<>();
        state.put("saturated", saturated);
        state.put("format", format);
        state.put("streams", List.of(5L));
        facts.surveysByFeed.put(feed, state);
        facts.docs = new ArrayList<>(facts.docs);
        facts.docs.add(FakeBuildFacts.doc("Documentation", "s-" + feed, feed + StroomBuildFacts.SURVEY));
    }

    private static Map<String, Object> feedSurveys(final List<String> surveyed,
                                                   final List<String> toSurvey,
                                                   final List<String> notCovered) {
        final Map<String, Object> out = new LinkedHashMap<>();
        out.put("surveyed", surveyed);
        out.put("to_survey", toSurvey);
        out.put("not_covered", notCovered);
        out.put("note", "Each feed the build is for is surveyed and covered on its own (survey_feed build=... "
                        + "feed=<each>): next names them in turn.");
        return out;
    }

    private static Map<?, ?> next(final Map<String, Object> result) {
        return (Map<?, ?>) result.get("next");
    }

    private static Object state(final Map<String, Object> result, final String step) {
        return ((List<?>) result.get("steps")).stream()
                .map(Map.class::cast)
                .filter(item -> step.equals(item.get("step")))
                .findFirst()
                .map(item -> item.get("state"))
                .orElse(null);
    }
}
