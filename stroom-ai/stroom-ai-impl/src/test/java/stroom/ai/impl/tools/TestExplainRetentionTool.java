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

package stroom.ai.impl.tools;

import stroom.data.retention.shared.DataRetentionDeleteSummary;
import stroom.data.retention.shared.DataRetentionRule;
import stroom.data.retention.shared.DataRetentionRules;
import stroom.job.api.JobStates.JobNodeState;
import stroom.job.api.JobStates.JobState;
import stroom.meta.shared.DataRetentionFields;
import stroom.meta.shared.Meta;
import stroom.meta.shared.MetaFields;
import stroom.meta.shared.MetaRow;
import stroom.meta.shared.Status;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionTerm.Condition;
import stroom.security.shared.AppPermission;
import stroom.util.date.DateUtil;
import stroom.util.json.JsonUtil;
import stroom.util.shared.PermissionException;
import stroom.util.shared.time.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class TestExplainRetentionTool {

    private static final Instant NOW = Instant.parse("2026-10-30T12:00:00Z");

    @TempDir
    Path tempDir;

    private ToolTestFixture fixture;
    private final RetentionFacts facts = Mockito.mock(RetentionFacts.class);
    // The streams the facts give, newest first.
    private final List<MetaRow> streams = new ArrayList<>();

    @BeforeEach
    void setUp() {
        fixture = new ToolTestFixture(tempDir);
        Mockito.when(facts.now()).thenReturn(NOW);
        // Saved out of order, as rule numbers are only for display until the screen renumbers them.
        final DataRetentionRules rules = DataRetentionRules.builder()
                .uuid("rules-uuid")
                .name("Data Retention Rules")
                .rules(List.of(
                        DataRetentionRule.foreverRule(2, 0L, "Keep audit", true, feed("AUDIT")),
                        DataRetentionRule.ageRule(1, 0L, "Short-lived", true, feed("NOISY"), 30, TimeUnit.DAYS),
                        DataRetentionRule.ageRule(3, 0L, "Everything else", false,
                                ExpressionOperator.builder().build(), 1, TimeUnit.YEARS)))
                .build();
        Mockito.when(facts.rules()).thenReturn(Optional.of(rules));
        Mockito.when(facts.streams(Mockito.any(), Mockito.anyInt())).thenAnswer(invocation -> streams);
        Mockito.when(facts.deletePurgeAge()).thenReturn(Optional.of("7d"));
        Mockito.when(facts.job(Mockito.anyString())).thenAnswer(invocation -> Optional.of(new JobState(
                invocation.getArgument(0), true, List.of(new JobNodeState("node1", true, "Cron", "0 0 0 * * ?", 1,
                0, NOW.minusSeconds(43_200).toEpochMilli(), NOW.plusSeconds(43_200).toEpochMilli(), null)))));
    }

    @Test
    void withManagePoliciesTheRulesAreGivenInTheOrderTheyApply() {
        managePolicies(true);

        final Map<String, Object> result = run("{}");

        assertThat(rules(result)).extracting(rule -> rule.get("number")).containsExactly(1, 2, 3);
        assertThat(rules(result).getFirst()).containsEntry("name", "Short-lived")
                .containsEntry("keep", "30 Days")
                .containsEntry("enabled", true);
        assertThat(String.valueOf(rules(result).getFirst().get("matches"))).contains("NOISY");
        assertThat(rules(result).get(1)).containsEntry("keep", "Forever");
        assertThat(rules(result).get(2)).containsEntry("enabled", false);
        assertThat(result).doesNotContainKey("view").containsEntry("delete_purge_age", "7d");
    }

    @Test
    void theBehaviourSaysRetentionIsByAgeAndNeverBySpace() {
        // Seen: asked what happens when the data volumes fill, the agent said Stroom deletes the oldest streams.
        managePolicies(false);

        final String behaviour = String.valueOf(run("{}").get("behaviour"));

        assertThat(behaviour).contains("Retention is by age alone")
                .contains("never deletes data because a volume is full")
                .contains("first whose expression matches the stream applies")
                .contains("kept forever");
    }

    @Test
    void withoutManagePoliciesTheRulesAreHiddenButEachStreamsRuleIsGiven() {
        managePolicies(false);
        streams.add(stream(42, "NOISY", "Raw Events", NOW.minusSeconds(86_400), Status.UNLOCKED,
                "1 Short-lived", "30 Days", "2026-11-28T12:00:00.000Z"));

        final Map<String, Object> result = run("{\"stream_id\":42}");

        assertThat(result).containsEntry("view", ExplainRetentionTool.REDUCED).doesNotContainKey("rules");
        assertThat(streams(result)).singleElement().satisfies(stream -> assertThat(stream)
                .containsEntry("id", 42)
                .containsEntry("rule", "1 Short-lived")
                .containsEntry("keep", "30 Days")
                .containsEntry("keep_until", "2026-11-28T12:00:00.000Z")
                .doesNotContainKey("overdue"));
        Mockito.verify(facts).streams(ExpressionOperator.builder()
                .addIdTerm(MetaFields.ID, Condition.EQUALS, 42L)
                .build(), 1);
    }

    @Test
    void aStreamPastItsPeriodButNotDeletedIsOverdue() {
        managePolicies(true);
        streams.add(stream(7, "NOISY", "Raw Events", NOW.minusSeconds(90 * 86_400L), Status.UNLOCKED,
                "1 Short-lived", "30 Days", "2026-09-01T12:00:00.000Z"));

        assertThat(streams(run("{\"stream_id\":7}")).getFirst())
                .hasEntrySatisfying("overdue", overdue -> assertThat(String.valueOf(overdue))
                        .contains(ExplainRetentionTool.RETENTION_JOB));
    }

    @Test
    void aStreamNoRuleMatchesIsKeptForever() {
        managePolicies(true);
        streams.add(stream(8, "OTHER", "Raw Events", NOW, Status.UNLOCKED, "None", "Forever", "Forever"));

        assertThat(streams(run("{\"stream_id\":8}")).getFirst())
                .containsEntry("rule", "None: no rule matches it, so it is kept forever")
                .doesNotContainKey("overdue");
    }

    @Test
    void aFeedIsGivenByTheNewestStreamOfEachType() {
        managePolicies(true);
        streams.add(stream(30, "NOISY", "Events", NOW, Status.UNLOCKED, "1 Short-lived", "30 Days", "x"));
        streams.add(stream(29, "NOISY", "Raw Events", NOW, Status.UNLOCKED, "1 Short-lived", "30 Days", "x"));
        streams.add(stream(28, "NOISY", "Events", NOW, Status.UNLOCKED, "1 Short-lived", "30 Days", "x"));

        final Map<String, Object> result = run("{\"feed\":\"NOISY\"}");

        assertThat(streams(result)).extracting(stream -> stream.get("id")).containsExactly(30, 29);
    }

    @Test
    void anUnknownStreamIsAnError() {
        managePolicies(true);

        final ToolResult result = execute("{\"stream_id\":99}");

        assertThat(result.text()).contains("No stream 99 that you can view.");
    }

    @Test
    void feedAndStreamTogetherAreRefused() {
        assertThat(execute("{\"feed\":\"NOISY\",\"stream_id\":1}").text()).contains("not both");
        Mockito.verify(facts, Mockito.never()).streams(Mockito.any(), Mockito.anyInt());
    }

    @Test
    void theImpactIsCountedByRuleWithTheLargestFeedsFirst() {
        managePolicies(true);
        Mockito.when(facts.impact(Mockito.any())).thenReturn(List.of(
                new DataRetentionDeleteSummary("NOISY", "Raw Events", 1, "Short-lived", 5),
                new DataRetentionDeleteSummary("CHATTY", "Raw Events", 1, "Short-lived", 50),
                new DataRetentionDeleteSummary("OLD", "Events", 3, "Everything else", 2)));

        final Map<String, Object> result = run("{\"impact\":true}");

        assertThat(String.valueOf(result.get("impact")))
                .contains("rule=1 Short-lived, streams_to_delete=55, by_feed_and_type=[{feed=CHATTY")
                .contains("rule=3 Everything else, streams_to_delete=2");
    }

    @Test
    void theImpactNeedsManagePolicies() {
        managePolicies(false);

        assertThat(run("{\"impact\":true}")).containsEntry("impact",
                "Not checked: it needs the Manage Policies permission.");
        Mockito.verify(facts, Mockito.never()).impact(Mockito.any());
    }

    @Test
    void theJobsSayWhenTheyRunAndWhenTheyDont() {
        managePolicies(true);
        Mockito.when(facts.job(ExplainRetentionTool.DELETE_JOB)).thenReturn(Optional.of(new JobState(
                ExplainRetentionTool.DELETE_JOB, false, List.of())));

        final Map<String, Map<String, Object>> jobs = jobs(run("{}"));

        assertThat(jobs.get(ExplainRetentionTool.RETENTION_JOB))
                .containsEntry("enabled", true)
                .containsEntry("schedule", "0 0 0 * * ?")
                .containsEntry("last_run",
                        DateUtil.createNormalDateTimeString(NOW.minusSeconds(43_200).toEpochMilli()));
        assertThat(jobs.get(ExplainRetentionTool.DELETE_JOB))
                .containsEntry("effect", "It isn't enabled on any node, so it doesn't run.");
    }

    @Test
    void theJobsNeedManageJobs() {
        managePolicies(true);
        Mockito.when(facts.job(Mockito.anyString())).thenThrow(new PermissionException(null, "No"));

        assertThat(String.valueOf(run("{}").get("jobs"))).contains("needs the Manage Jobs permission");
    }

    @Test
    void withNoRulesEverythingIsKeptForever() {
        managePolicies(true);
        Mockito.when(facts.rules()).thenReturn(Optional.empty());

        assertThat(run("{}")).containsEntry("rules", List.of())
                .containsEntry("rules_note", "There are no data retention rules, so all data is kept forever.");
    }

    private void managePolicies(final boolean held) {
        Mockito.when(fixture.securityContext.hasAppPermission(AppPermission.MANAGE_POLICIES_PERMISSION))
                .thenReturn(held);
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> rules(final Map<String, Object> result) {
        return (List<Map<String, Object>>) result.get("rules");
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> streams(final Map<String, Object> result) {
        return (List<Map<String, Object>>) result.get("streams");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Map<String, Object>> jobs(final Map<String, Object> result) {
        return (Map<String, Map<String, Object>>) result.get("jobs");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> run(final String arguments) {
        return JsonUtil.readValue(execute(arguments).text(), Map.class);
    }

    private ToolResult execute(final String arguments) {
        return new ExplainRetentionTool(() -> facts, fixture.securityContext, fixture.auditLog())
                .execute(ToolTestFixture.request(ExplainRetentionTool.NAME, arguments),
                        ToolCalls.inChat(1, fixture.workspace));
    }

    private static ExpressionOperator feed(final String feed) {
        return ExpressionOperator.builder().addTextTerm(MetaFields.FEED, Condition.EQUALS, feed).build();
    }

    private static MetaRow stream(final long id,
                                  final String feed,
                                  final String type,
                                  final Instant created,
                                  final Status status,
                                  final String rule,
                                  final String age,
                                  final String until) {
        return new MetaRow(Meta.builder()
                .id(id)
                .feedName(feed)
                .typeName(type)
                .createMs(created.toEpochMilli())
                .status(status)
                .build(), null, Map.of(
                DataRetentionFields.RETENTION_RULE, rule,
                DataRetentionFields.RETENTION_AGE, age,
                DataRetentionFields.RETENTION_UNTIL, until));
    }
}
