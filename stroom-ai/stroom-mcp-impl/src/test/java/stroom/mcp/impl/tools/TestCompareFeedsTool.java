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

import stroom.ai.impl.tools.ToolCallContext;
import stroom.ai.impl.tools.ToolResult;
import stroom.ai.impl.tools.instructions.AgentInstructions.InstructionDoc;
import stroom.docref.DocRef;
import stroom.documentation.shared.DocumentationDoc;
import stroom.util.json.JsonUtil;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/// `compare_feeds` ([CompareFeedsTool]): for a build of several feeds, their differences shown and the union
/// confirmed by the user, each feed's zone and each AGENTS conflict answered, all kept in the build (decided by the
/// user, 2026-11-25: nothing merges silently).
class TestCompareFeedsTool {

    private static final String BUILD = "acme";

    private final BuildWorld world = new BuildWorld();
    private final FakeBuildFacts facts = new FakeBuildFacts();
    private final TranslationBuilds builds = Mockito.mock(TranslationBuilds.class);
    private final FeedOverviews overviews = Mockito.mock(FeedOverviews.class);
    private final AgentsConflicts agents = Mockito.mock(AgentsConflicts.class);
    private final Consent consent = new Consent(() -> world.auditLog);
    private final BuildPlan plan = new BuildPlan(facts, world.guard(), consent, new BuildMemory());
    private final CompareFeedsTool tool = new CompareFeedsTool(plan, builds, world.records(), overviews, agents,
            consent);

    @BeforeEach
    void setUp() {
        world.folder("System/" + BuildWorld.WORKSPACE + "/" + BUILD);
        facts.sources = List.of("ACME-A", "ACME-B");
        Mockito.when(overviews.overviews(Mockito.anyList())).thenReturn(List.of(
                Map.of("feed", "ACME-A", "streams", 3, "dormant", false),
                Map.of("feed", "ACME-B", "streams", 2, "dormant", true)));
        Mockito.when(agents.applyingByFeed(Mockito.any(), Mockito.anyList())).thenReturn(Map.of());
    }

    @Test
    void aBuildOfOneFeedHasNothingToCompare() {
        facts.sources = List.of("ACME");

        assertThat(error(call(Map.of("build", BUILD), BuildToolCalls.context()))).contains("is for one feed (ACME)",
                "nothing to compare");
    }

    @Test
    void everyFeedIsSurveyedFirst() {
        surveys(state("ACME-A", "+10:00"));

        assertThat(error(call(Map.of("build", BUILD), BuildToolCalls.context()))).contains(
                "Not every feed of build 'acme' is surveyed yet: ACME-B", "survey_feed build=acme feed=ACME-B");
    }

    @Test
    void withoutFormsTheDifferencesAreGivenForTheAgentToAskAndNothingIsKept() {
        surveys(state("ACME-A", "+10:00"), state("ACME-B", "Z"));
        conflict();

        final Map<String, Object> result = call(Map.of("build", BUILD), BuildToolCalls.context());

        assertThat(result).containsEntry("status", "needs_user");
        assertThat(result.get("feeds")).asList().hasSize(2);
        assertThat(String.valueOf(result.get("differences_said"))).contains("Times written in different zones");
        assertThat(String.valueOf(result.get("conflicts"))).contains("fields: user", "Initiator.Id", "user.name");
        assertThat(String.valueOf(result.get("hint"))).contains("confirm=true", "time_zones={feed: zone}",
                "agents={key: value}", "only as they said");
        assertThat(world.records().feedComparison(BUILD)).isEmpty();
    }

    @Test
    void theAnswersGivenAreCheckedAndKept() {
        surveys(state("ACME-A", "+10:00"), state("ACME-B", "Z"));
        conflict();

        // A zone Stroom can't read, a conflict answered with a value neither doc gives: refused, nothing kept.
        assertThat(error(call(Map.of("build", BUILD, "confirm", true, "time_zones", Map.of("ACME-A", "Sydney",
                "ACME-B", "UTC"), "agents", Map.of("fields: user", "user.name")), BuildToolCalls.context())))
                .contains("'Sydney' (for feed ACME-A) is not a zone Stroom reads");
        assertThat(error(call(Map.of("build", BUILD, "confirm", true, "time_zones", Map.of("ACME-A",
                "Australia/Sydney", "ACME-B", "UTC"), "agents", Map.of("fields: user", "user.id")),
                BuildToolCalls.context()))).contains("differ on fields: user", "'Initiator.Id', 'user.name'");
        assertThat(error(call(Map.of("build", BUILD, "confirm", true, "agents", Map.of("fields: user",
                "user.name")), BuildToolCalls.context()))).contains("give time_zones with each of ACME-A, ACME-B");
        assertThat(world.records().feedComparison(BUILD)).isEmpty();

        final Map<String, Object> kept = call(Map.of("build", BUILD, "confirm", true, "time_zones",
                Map.of("acme-a", "Australia/Sydney", "ACME-B", "UTC"), "agents", Map.of("fields: user",
                        "user.name")), BuildToolCalls.context());

        assertThat(kept).doesNotContainKey("status");
        assertThat(world.records().feedTimeZones(BUILD)).isEqualTo(Map.of("ACME-A", "Australia/Sydney", "ACME-B",
                "UTC"));
        assertThat(world.records().agentsChoices(BUILD)).isEqualTo(Map.of("fields: user", Map.of("value",
                "user.name", "folder", "/Feeds/Acme/Site-B")));
        assertThat(world.records().feedComparison(BUILD)).containsEntry("feeds", List.of("ACME-A", "ACME-B"));

        // Asked again: confirmed already, nothing asked.
        final Map<String, Object> again = call(Map.of("build", BUILD), BuildToolCalls.context());
        assertThat(again).doesNotContainKey("status");
        assertThat(String.valueOf(again.get("status_said"))).startsWith("Confirmed by the user on ");
    }

    @Test
    void theUserConfirmsTheUnionAndAnswersTheZonesInForms() {
        surveys(state("ACME-A", "+10:00"), state("ACME-B", "Z"));
        final Map<String, Object> answer = new LinkedHashMap<>();
        answer.put("q1", CompareFeedsTool.UNION);
        answer.put("a1", "+10:00");
        answer.put("a2", "Another zone (type it)");
        answer.put("a2" + Consent.TYPED_SUFFIX, "Europe/London");
        final BuildToolCalls.FormUser user = BuildToolCalls.user(answer);

        final Map<String, Object> kept = call(Map.of("build", BUILD), user.context());

        assertThat(kept).doesNotContainKey("status");
        assertThat(user.shown()).hasSize(2);
        assertThat(user.shown().getFirst()).contains("Times written in different zones").doesNotContain("|", "**");
        assertThat(world.records().feedTimeZones(BUILD)).isEqualTo(Map.of("ACME-A", "+10:00", "ACME-B",
                "Europe/London"));
    }

    @Test
    void theUserNotWantingOneTranslationIsAskedWhatToDoAndNothingIsKept() {
        surveys(state("ACME-A", "Z"), state("ACME-B", "Z"));
        final BuildToolCalls.FormUser user = BuildToolCalls.user(Map.of("q1", CompareFeedsTool.NOT_UNION));

        assertThat(error(call(Map.of("build", BUILD), user.context()))).contains("doesn't want one translation",
                "nothing was kept", "Ask them what to do");
        assertThat(world.records().feedComparison(BUILD)).isEmpty();
        assertThat(error(call(Map.of("build", BUILD, "confirm", false), BuildToolCalls.context())))
                .contains("doesn't want one translation");
    }

    @Test
    void feedsAlikeAreConfirmedWithNoZoneAsked() {
        surveys(state("ACME-A", "Z"), state("ACME-B", "Z"));
        final BuildToolCalls.FormUser user = BuildToolCalls.user(Map.of("q1", CompareFeedsTool.UNION));

        final Map<String, Object> kept = call(Map.of("build", BUILD), user.context());

        assertThat(user.shown()).hasSize(1);
        assertThat(user.shown().getFirst()).contains("its feeds' records look alike");
        assertThat(String.valueOf(kept.get("differences_said"))).contains("look alike");
        assertThat(world.records().feedTimeZones(BUILD)).isEmpty();
        assertThat(world.records().feedComparison(BUILD)).containsKey("digest");
    }

    private void conflict() {
        final Map<String, List<InstructionDoc>> byFeed = new LinkedHashMap<>();
        byFeed.put("ACME-A", List.of(doc("/Feeds/Acme/Site-A", "## Fields\n- user: Initiator.Id")));
        byFeed.put("ACME-B", List.of(doc("/Feeds/Acme/Site-B", "## Fields\n- user: user.name")));
        Mockito.when(agents.applyingByFeed(Mockito.any(), Mockito.anyList())).thenReturn(byFeed);
    }

    @SafeVarargs
    private void surveys(final Map<String, Object>... states) {
        Mockito.when(builds.surveyStatesInBuild(BUILD)).thenReturn(List.of(states));
    }

    // A feed's survey state: one kind of event whose time is written with the zone given.
    private static Map<String, Object> state(final String feed, final String zone) {
        final Map<String, Object> state = new LinkedHashMap<>();
        state.put("feed", feed);
        state.put("format", "json lines");
        state.put("saturated", true);
        state.put("shapes", Map.of("fields:action,time,user | action=login", Map.of("count", 1, "examples",
                List.of(Map.of("text", "{\"time\":\"2026-01-01T10:00:00" + zone + "\",\"action\":\"login\"}")))));
        return state;
    }

    private static InstructionDoc doc(final String folder, final String text) {
        return new InstructionDoc(new DocRef(DocumentationDoc.TYPE, folder, "AGENTS"), folder, text, List.of());
    }

    private Map<String, Object> call(final Map<String, Object> args, final ToolCallContext context) {
        final ToolResult result = tool.execute(BuildToolCalls.call(CompareFeedsTool.NAME,
                JsonUtil.writeValueAsString(args)), context);
        return result.isError()
                ? Map.of("error", result.text())
                : BuildToolCalls.json(result.text());
    }

    private static String error(final Map<String, Object> result) {
        return String.valueOf(result.get("error"));
    }
}
