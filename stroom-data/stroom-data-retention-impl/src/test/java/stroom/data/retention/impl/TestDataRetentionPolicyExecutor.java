/*
 * Copyright 2016-2025 Crown Copyright
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

package stroom.data.retention.impl;

import stroom.cluster.lock.api.ClusterLockService;
import stroom.cluster.lock.mock.MockClusterLockService;
import stroom.data.retention.api.DataRetentionConfig;
import stroom.data.retention.api.DataRetentionCreationTimeUtil;
import stroom.data.retention.api.DataRetentionRuleAction;
import stroom.data.retention.api.DataRetentionTracker;
import stroom.data.retention.api.RetentionRuleOutcome;
import stroom.data.retention.shared.DataRetentionRule;
import stroom.data.retention.shared.DataRetentionRules;
import stroom.meta.api.MetaService;
import stroom.meta.shared.MetaFields;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionTerm;
import stroom.task.api.SimpleTaskContextFactory;
import stroom.task.api.TaskContextFactory;
import stroom.util.shared.time.TimeUnit;
import stroom.util.time.TimePeriod;

import io.vavr.Tuple;
import io.vavr.Tuple2;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TestDataRetentionPolicyExecutor {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestDataRetentionPolicyExecutor.class);
    private static final String RULES_VERSION = "1234567";

    private final ClusterLockService clusterLockService = new MockClusterLockService();
    private final DataRetentionConfig dataRetentionConfig = new DataRetentionConfig();
    private final TaskContextFactory taskContextFactory = new SimpleTaskContextFactory();

    @Mock
    private MetaService metaService;

    @Captor
    private ArgumentCaptor<List<DataRetentionRuleAction>> ruleExpressionsCaptor;

    @Captor
    private ArgumentCaptor<TimePeriod> periodCaptor;

    @Test
    void testDataRetention_gh1636() {

        final List<DataRetentionRule> rules = List.of(
                buildRule(1, true, 2, TimeUnit.DAYS),
                buildRule(2, true, 1, TimeUnit.MONTHS));

        final Instant now = Instant.now();

        runDataRetention(rules, now, null);

        final List<List<DataRetentionRuleAction>> allRuleActions = ruleExpressionsCaptor.getAllValues();
        final List<TimePeriod> allPeriods = periodCaptor.getAllValues();

        final int expectedPeriodCount = 2;
        assertThat(allPeriods).hasSize(expectedPeriodCount);
        assertThat(allRuleActions).hasSize(expectedPeriodCount);

        // The method call number
        int callNo = 0;

        // -------------------------------------------------

        assertPeriod(allPeriods.get(callNo), Period.ofMonths(1), Period.ofDays(2), now);
        assertRuleActions(allRuleActions.get(callNo), List.of(
                Tuple.of(1, RetentionRuleOutcome.DELETE),
                Tuple.of(2, RetentionRuleOutcome.RETAIN)));
        callNo++;

        // -------------------------------------------------

        assertEpochPeriod(allPeriods.get(callNo), Period.ofMonths(1), now);
        assertRuleActions(allRuleActions.get(callNo), List.of(
                Tuple.of(1, RetentionRuleOutcome.DELETE),
                Tuple.of(2, RetentionRuleOutcome.DELETE)));
    }

    @Test
    void testDataRetention_fourRules1() {

        final List<DataRetentionRule> rules = List.of(
                buildRule(1, true, 10, TimeUnit.DAYS),
                buildRule(2, true, 1, TimeUnit.MONTHS),
                buildRule(3, true, 1, TimeUnit.YEARS),
                buildForeverRule(4, true));

        final Instant now = Instant.now();

        runDataRetention(rules, now, null);

        final List<List<DataRetentionRuleAction>> allRuleActions = ruleExpressionsCaptor.getAllValues();
        final List<TimePeriod> allPeriods = periodCaptor.getAllValues();

        final int expectedPeriodCount = 3;
        assertThat(allPeriods).hasSize(expectedPeriodCount);
        assertThat(allRuleActions).hasSize(expectedPeriodCount);

        // The method call number
        int callNo = 0;

        // -------------------------------------------------

        assertPeriod(allPeriods.get(callNo), Period.ofMonths(1), Period.ofDays(10), now);
        assertRuleActions(allRuleActions.get(callNo), List.of(
                Tuple.of(1, RetentionRuleOutcome.DELETE),
                Tuple.of(2, RetentionRuleOutcome.RETAIN),
                Tuple.of(3, RetentionRuleOutcome.RETAIN),
                Tuple.of(4, RetentionRuleOutcome.RETAIN)));
        callNo++;

        // -------------------------------------------------

        assertPeriod(allPeriods.get(callNo), Period.ofYears(1), Period.ofMonths(1), now);
        assertRuleActions(allRuleActions.get(callNo), List.of(
                Tuple.of(1, RetentionRuleOutcome.DELETE),
                Tuple.of(2, RetentionRuleOutcome.DELETE),
                Tuple.of(3, RetentionRuleOutcome.RETAIN),
                Tuple.of(4, RetentionRuleOutcome.RETAIN)));
        callNo++;

        // -------------------------------------------------

        assertEpochPeriod(allPeriods.get(callNo), Period.ofYears(1), now);
        assertRuleActions(allRuleActions.get(callNo), List.of(
                Tuple.of(1, RetentionRuleOutcome.DELETE),
                Tuple.of(2, RetentionRuleOutcome.DELETE),
                Tuple.of(3, RetentionRuleOutcome.DELETE),
                Tuple.of(4, RetentionRuleOutcome.RETAIN)));
    }

    @Test
    void testDataRetention_fourRules2() {

        final List<DataRetentionRule> rules = List.of(
                buildForeverRule(1, true),
                buildRule(2, true, 1, TimeUnit.YEARS),
                buildRule(3, true, 1, TimeUnit.MONTHS),
                buildRule(4, true, 1, TimeUnit.DAYS));

        final Instant now = Instant.now();

        runDataRetention(rules, now, null);

        final List<List<DataRetentionRuleAction>> allRuleActions = ruleExpressionsCaptor.getAllValues();
        final List<TimePeriod> allPeriods = periodCaptor.getAllValues();

        final int expectedPeriodCount = 3;
        assertThat(allPeriods).hasSize(expectedPeriodCount);
        assertThat(allRuleActions).hasSize(expectedPeriodCount);

        // The method call number
        int callNo = 0;

        // -------------------------------------------------

        assertPeriod(allPeriods.get(callNo), Period.ofMonths(1), Period.ofDays(1), now);
        assertRuleActions(allRuleActions.get(callNo), List.of(
                Tuple.of(1, RetentionRuleOutcome.RETAIN),
                Tuple.of(2, RetentionRuleOutcome.RETAIN),
                Tuple.of(3, RetentionRuleOutcome.RETAIN),
                Tuple.of(4, RetentionRuleOutcome.DELETE)));
        callNo++;

        // -------------------------------------------------

        assertPeriod(allPeriods.get(callNo), Period.ofYears(1), Period.ofMonths(1), now);
        assertRuleActions(allRuleActions.get(callNo), List.of(
                Tuple.of(1, RetentionRuleOutcome.RETAIN),
                Tuple.of(2, RetentionRuleOutcome.RETAIN),
                Tuple.of(3, RetentionRuleOutcome.DELETE),
                Tuple.of(4, RetentionRuleOutcome.DELETE)));
        callNo++;

        // -------------------------------------------------

        assertEpochPeriod(allPeriods.get(callNo), Period.ofYears(1), now);
        assertRuleActions(allRuleActions.get(callNo), List.of(
                Tuple.of(1, RetentionRuleOutcome.RETAIN),
                Tuple.of(2, RetentionRuleOutcome.DELETE),
                Tuple.of(3, RetentionRuleOutcome.DELETE),
                Tuple.of(4, RetentionRuleOutcome.DELETE)));
    }

    @Test
    void testDataRetention_fourRulesWithRecentTracker() {

        final List<DataRetentionRule> rules = List.of(
                buildRule(1, true, 10, TimeUnit.DAYS),
                buildRule(2, true, 1, TimeUnit.MONTHS),
                buildRule(3, true, 1, TimeUnit.YEARS),
                buildForeverRule(4, true));

        final int trackerAgeDays = 2;
        final Instant now = Instant.now();
        final LocalDateTime nowUTC = LocalDateTime.ofInstant(now, ZoneOffset.UTC);

        final List<DataRetentionTracker> trackers = createTrackers(
                rules, RULES_VERSION, now.minus(Duration.ofDays(trackerAgeDays)));

        runDataRetention(rules, now, trackers);

        final List<List<DataRetentionRuleAction>> allRuleActions = ruleExpressionsCaptor.getAllValues();
        final List<TimePeriod> allPeriods = periodCaptor.getAllValues();

        final int expectedPeriodCount = 3;
        assertThat(allPeriods).hasSize(expectedPeriodCount);
        assertThat(allRuleActions).hasSize(expectedPeriodCount);

        // The method call number
        int callNo = 0;

        // -------------------------------------------------

        assertPeriod(
                allPeriods.get(callNo),
                Period.ofDays(10).plusDays(trackerAgeDays),
                Period.ofDays(10), now);

        assertRuleActions(allRuleActions.get(callNo), List.of(
                Tuple.of(1, RetentionRuleOutcome.DELETE),
                Tuple.of(2, RetentionRuleOutcome.RETAIN),
                Tuple.of(3, RetentionRuleOutcome.RETAIN),
                Tuple.of(4, RetentionRuleOutcome.RETAIN)));
        callNo++;

        // -------------------------------------------------

        assertPeriod(
                allPeriods.get(callNo),
                Period.ofMonths(1).plusDays(trackerAgeDays),
                Period.ofMonths(1), now);

        assertRuleActions(allRuleActions.get(callNo), List.of(
                Tuple.of(1, RetentionRuleOutcome.DELETE),
                Tuple.of(2, RetentionRuleOutcome.DELETE),
                Tuple.of(3, RetentionRuleOutcome.RETAIN),
                Tuple.of(4, RetentionRuleOutcome.RETAIN)));
        callNo++;

        // -------------------------------------------------

        assertPeriod(
                allPeriods.get(callNo),
                Period.ofYears(1).plusDays(trackerAgeDays),
                Period.ofYears(1), now);

        assertRuleActions(allRuleActions.get(callNo), List.of(
                Tuple.of(1, RetentionRuleOutcome.DELETE),
                Tuple.of(2, RetentionRuleOutcome.DELETE),
                Tuple.of(3, RetentionRuleOutcome.DELETE),
                Tuple.of(4, RetentionRuleOutcome.RETAIN)));
    }

    @Test
    void testDataRetention_fourRulesWithOldTracker() {

        final List<DataRetentionRule> rules = List.of(
                buildRule(1, true, 10, TimeUnit.DAYS),
                buildRule(2, true, 1, TimeUnit.MONTHS),
                buildRule(3, true, 2, TimeUnit.MONTHS),
                buildRule(4, true, 1, TimeUnit.YEARS)
        );

        final Instant now = Instant.now();

        final int trackerAgeDays = 90;
        // Tracker is 90days old so should be ignored for periods:
        // 1month ago => 10days ago
        // 2months ago => 1months ago
        final List<DataRetentionTracker> trackers = createTrackers(
                rules, RULES_VERSION, now.minus(Duration.ofDays(trackerAgeDays)));

        runDataRetention(rules, now, trackers);

        final List<List<DataRetentionRuleAction>> allRuleActions = ruleExpressionsCaptor.getAllValues();
        final List<TimePeriod> allPeriods = periodCaptor.getAllValues();

        final int expectedPeriodCount = 4;
        assertThat(allPeriods).hasSize(expectedPeriodCount);
        assertThat(allRuleActions).hasSize(expectedPeriodCount);

        // The method call number
        int callNo = 0;

        // -------------------------------------------------

        // period same as if no tracker as tracker is so old
        assertPeriod(allPeriods.get(callNo), Period.ofMonths(1), Period.ofDays(10), now);
        assertRuleActions(allRuleActions.get(callNo), List.of(
                Tuple.of(1, RetentionRuleOutcome.DELETE),
                Tuple.of(2, RetentionRuleOutcome.RETAIN),
                Tuple.of(3, RetentionRuleOutcome.RETAIN),
                Tuple.of(4, RetentionRuleOutcome.RETAIN)));
        callNo++;

        // -------------------------------------------------

        assertPeriod(allPeriods.get(callNo), Period.ofMonths(2), Period.ofMonths(1), now);
        assertRuleActions(allRuleActions.get(callNo), List.of(
                Tuple.of(1, RetentionRuleOutcome.DELETE),
                Tuple.of(2, RetentionRuleOutcome.DELETE),
                Tuple.of(3, RetentionRuleOutcome.RETAIN),
                Tuple.of(4, RetentionRuleOutcome.RETAIN)));
        callNo++;

        // -------------------------------------------------

        assertPeriod(allPeriods.get(callNo), Period.ofMonths(2).plusDays(trackerAgeDays), Period.ofMonths(2), now);
        assertRuleActions(allRuleActions.get(callNo), List.of(
                Tuple.of(1, RetentionRuleOutcome.DELETE),
                Tuple.of(2, RetentionRuleOutcome.DELETE),
                Tuple.of(3, RetentionRuleOutcome.DELETE),
                Tuple.of(4, RetentionRuleOutcome.RETAIN)));
        callNo++;

        // -------------------------------------------------

        assertPeriod(allPeriods.get(callNo), Period.ofYears(1).plusDays(trackerAgeDays), Period.ofYears(1), now);
        assertRuleActions(allRuleActions.get(callNo), List.of(
                Tuple.of(1, RetentionRuleOutcome.DELETE),
                Tuple.of(2, RetentionRuleOutcome.DELETE),
                Tuple.of(3, RetentionRuleOutcome.DELETE),
                Tuple.of(4, RetentionRuleOutcome.DELETE)));
    }

    @Test
    void testDataRetention_fourRulesWithMissingTracker() {

        final List<DataRetentionRule> rules = List.of(
                buildRule(1, true, 10, TimeUnit.DAYS),
                buildRule(2, true, 1, TimeUnit.MONTHS),
                buildRule(3, true, 2, TimeUnit.MONTHS),
                buildRule(4, true, 1, TimeUnit.YEARS)
        );

        // Use a nice fixed date to make the time calcs easier on the brain
        final Instant now = LocalDate.of(2020, 10, 1)
                .atStartOfDay()
                .toInstant(ZoneOffset.UTC);

        final int trackerAgeDays = 1;
        // Tracker is 90days old so should be ignored for periods:
        // 1month ago => 10days ago
        // 2months ago => 1months ago
        List<DataRetentionTracker> trackers = createTrackers(
                rules, RULES_VERSION, now.minus(Duration.ofDays(trackerAgeDays)));

        Assertions.assertThat(trackers)
                .hasSize(rules.size());

        // Remove the 1 month and 1 year trackers
        trackers = trackers.stream()
                .filter(tracker ->
                        !(tracker.getRuleAge().toLowerCase().contains("1 month")
                          || tracker.getRuleAge().toLowerCase().contains("1 year")))
                .collect(Collectors.toList());

        Assertions.assertThat(trackers)
                .hasSize(rules.size() - 2);

        runDataRetention(rules, now, trackers);

        final List<List<DataRetentionRuleAction>> allRuleActions = ruleExpressionsCaptor.getAllValues();
        final List<TimePeriod> allPeriods = periodCaptor.getAllValues();

        final int expectedPeriodCount = 4;
        assertThat(allPeriods).hasSize(expectedPeriodCount);
        assertThat(allRuleActions).hasSize(expectedPeriodCount);

        // The method call number
        int callNo = 0;

        // -------------------------------------------------

        // non adjusted period as there is no tracker
        assertPeriod(allPeriods.get(callNo), Period.ofMonths(1), Period.ofDays(10), now);
        assertRuleActions(allRuleActions.get(callNo), List.of(
                Tuple.of(1, RetentionRuleOutcome.DELETE),
                Tuple.of(2, RetentionRuleOutcome.RETAIN),
                Tuple.of(3, RetentionRuleOutcome.RETAIN),
                Tuple.of(4, RetentionRuleOutcome.RETAIN)));
        callNo++;

        // -------------------------------------------------

        assertPeriod(allPeriods.get(callNo), Period.ofMonths(1).plusDays(trackerAgeDays), Period.ofMonths(1), now);
        assertRuleActions(allRuleActions.get(callNo), List.of(
                Tuple.of(1, RetentionRuleOutcome.DELETE),
                Tuple.of(2, RetentionRuleOutcome.DELETE),
                Tuple.of(3, RetentionRuleOutcome.RETAIN),
                Tuple.of(4, RetentionRuleOutcome.RETAIN)));
        callNo++;

        // -------------------------------------------------

        // non adjusted period as there is no tracker
        assertPeriod(allPeriods.get(callNo), Period.ofYears(1), Period.ofMonths(2), now);
        assertRuleActions(allRuleActions.get(callNo), List.of(
                Tuple.of(1, RetentionRuleOutcome.DELETE),
                Tuple.of(2, RetentionRuleOutcome.DELETE),
                Tuple.of(3, RetentionRuleOutcome.DELETE),
                Tuple.of(4, RetentionRuleOutcome.RETAIN)));
        callNo++;

        // -------------------------------------------------

        assertPeriod(allPeriods.get(callNo), Period.ofYears(1).plusDays(trackerAgeDays), Period.ofYears(1), now);
        assertRuleActions(allRuleActions.get(callNo), List.of(
                Tuple.of(1, RetentionRuleOutcome.DELETE),
                Tuple.of(2, RetentionRuleOutcome.DELETE),
                Tuple.of(3, RetentionRuleOutcome.DELETE),
                Tuple.of(4, RetentionRuleOutcome.DELETE)));
    }

    @Test
    void testDataRetention_noRules() {

        final List<DataRetentionRule> rules = Collections.emptyList();

        final Instant now = Instant.now();

        final DataRetentionPolicyExecutor dataRetentionPolicyExecutor = createExecutor(rules);

        dataRetentionPolicyExecutor.exec(now);

        Mockito.verifyNoInteractions(metaService);
    }

    @Test
    void testDataRetention_oneRule() {

        final List<DataRetentionRule> rules = List.of(
                buildRule(1, true, 2, TimeUnit.MONTHS)
        );

        final Instant now = Instant.now();

        runDataRetention(rules, now, null);

        final List<List<DataRetentionRuleAction>> allRuleActions = ruleExpressionsCaptor.getAllValues();
        final List<TimePeriod> allPeriods = periodCaptor.getAllValues();

        final int expectedPeriodCount = 1;
        assertThat(allPeriods).hasSize(expectedPeriodCount);
        assertThat(allRuleActions).hasSize(expectedPeriodCount);

        // The method call number
        final int callNo = 0;

        // -------------------------------------------------

        assertEpochPeriod(allPeriods.get(callNo), Period.ofMonths(2), now);
        assertRuleActions(allRuleActions.get(callNo), List.of(
                Tuple.of(1, RetentionRuleOutcome.DELETE)));
    }

    @Test
    void testDataRetention_twoActiveOneInactive() {

        // rule 2 gets ignored
        final List<DataRetentionRule> rules = List.of(
                buildRule(1, true, 1, TimeUnit.MONTHS),
                buildRule(2, false, 2, TimeUnit.MONTHS),
                buildRule(3, true, 3, TimeUnit.MONTHS)
        );

        final Instant now = Instant.now();

        runDataRetention(rules, now, null);

        final List<List<DataRetentionRuleAction>> allRuleActions = ruleExpressionsCaptor.getAllValues();
        final List<TimePeriod> allPeriods = periodCaptor.getAllValues();

        final int expectedPeriodCount = 2;
        assertThat(allPeriods).hasSize(expectedPeriodCount);
        assertThat(allRuleActions).hasSize(expectedPeriodCount);

        // The method call number
        int callNo = 0;

        // -------------------------------------------------

        assertPeriod(allPeriods.get(callNo), Period.ofMonths(3), Period.ofMonths(1), now);
        assertRuleActions(allRuleActions.get(callNo), List.of(
                Tuple.of(1, RetentionRuleOutcome.DELETE),
                Tuple.of(3, RetentionRuleOutcome.RETAIN)));
        callNo++;

        // -------------------------------------------------

        assertEpochPeriod(allPeriods.get(callNo), Period.ofMonths(3), now);
        assertRuleActions(allRuleActions.get(callNo), List.of(
                Tuple.of(1, RetentionRuleOutcome.DELETE),
                Tuple.of(3, RetentionRuleOutcome.DELETE)));
    }

    @Test
    void testDataRetention_sameRuleAges() {

        final List<DataRetentionRule> rules = List.of(
                buildRule(1, true, 2, TimeUnit.MONTHS),
                buildRule(2, true, 2, TimeUnit.MONTHS));

        final Instant now = Instant.now();

        runDataRetention(rules, now, null);

        final List<List<DataRetentionRuleAction>> allRuleActions = ruleExpressionsCaptor.getAllValues();
        final List<TimePeriod> allPeriods = periodCaptor.getAllValues();

        final int expectedPeriodCount = 1;
        assertThat(allPeriods).hasSize(expectedPeriodCount);
        assertThat(allRuleActions).hasSize(expectedPeriodCount);

        // The method call number
        final int callNo = 0;

        // -------------------------------------------------

        assertEpochPeriod(allPeriods.get(callNo), Period.ofMonths(2), now);
        assertRuleActions(allRuleActions.get(callNo), List.of(
                Tuple.of(1, RetentionRuleOutcome.DELETE),
                Tuple.of(2, RetentionRuleOutcome.DELETE)));
    }

    private void runDataRetention(final List<DataRetentionRule> rules,
                                  final Instant now,
                                  final List<DataRetentionTracker> trackers) {
        final DataRetentionPolicyExecutor dataRetentionPolicyExecutor = createExecutor(rules);

        when(metaService.delete(
                ruleExpressionsCaptor.capture(),
                periodCaptor.capture()))
                .thenReturn(0);

        when(metaService.getRetentionTrackers())
                .thenReturn(trackers != null
                        ? trackers
                        : Collections.emptyList());

        dataRetentionPolicyExecutor.exec(now);
    }

    private DataRetentionPolicyExecutor createExecutor(final List<DataRetentionRule> rules) {
        return new DataRetentionPolicyExecutor(
                clusterLockService,
                () -> buildRules(rules),
                dataRetentionConfig,
                metaService,
                taskContextFactory);
    }

    private void assertPeriod(final TimePeriod actualPeriod,
                              final Period expectedTimeSinceFrom,
                              final Period expectedTimeSinceTo,
                              final Instant now) {
        LOGGER.debug("actualPeriod: ({}), expectedTimeSinceFrom: {}, expectedTimeSinceTo: {}",
                actualPeriod, expectedTimeSinceFrom, expectedTimeSinceTo);

        assertThat(LocalDateTime.ofInstant(actualPeriod.getFrom(), ZoneOffset.UTC))
                .isEqualTo(LocalDateTime.ofInstant(now, ZoneOffset.UTC)
                        .truncatedTo(ChronoUnit.MILLIS)
                        .minus(expectedTimeSinceFrom));

        assertThat(LocalDateTime.ofInstant(actualPeriod.getTo(), ZoneOffset.UTC))
                .isEqualTo(LocalDateTime.ofInstant(now, ZoneOffset.UTC)
                        .truncatedTo(ChronoUnit.MILLIS)
                        .minus(expectedTimeSinceTo));
    }

    private void assertPeriod(final TimePeriod actualPeriod,
                              final Instant expectedFrom,
                              final Period expectedTimeSinceTo,
                              final Instant now) {
        LOGGER.debug("actualPeriod: {}", actualPeriod);
        assertThat(LocalDateTime.ofInstant(actualPeriod.getFrom(), ZoneOffset.UTC))
                .isEqualTo(expectedFrom);
        assertThat(LocalDateTime.ofInstant(actualPeriod.getTo(), ZoneOffset.UTC))
                .isEqualTo(LocalDateTime.ofInstant(now.truncatedTo(ChronoUnit.MILLIS), ZoneOffset.UTC)
                        .minus(expectedTimeSinceTo));
    }

    private void assertEpochPeriod(final TimePeriod actualPeriod,
                                   final Period expectedTimeSinceTo,
                                   final Instant now) {
        assertThat(actualPeriod.getFrom())
                .isEqualTo(Instant.EPOCH);
        assertThat(LocalDateTime.ofInstant(actualPeriod.getTo(), ZoneOffset.UTC))
                .isEqualTo(LocalDateTime.ofInstant(now.truncatedTo(ChronoUnit.MILLIS), ZoneOffset.UTC)
                        .minus(expectedTimeSinceTo));
    }

    private void assertRuleActions(final List<DataRetentionRuleAction> actualRuleActions,
                                   final List<Tuple2<Integer, RetentionRuleOutcome>> expectedRuleOutcomes) {

        final List<Tuple2<Integer, RetentionRuleOutcome>> actualOutcomes = actualRuleActions.stream()
                .map(ruleAction ->
                        Tuple.of(ruleAction.getRule().getRuleNumber(), ruleAction.getOutcome())
                )
                .collect(Collectors.toList());

        assertThat(actualOutcomes)
                .containsExactlyElementsOf(expectedRuleOutcomes);
    }

    private DataRetentionRules buildRules(final List<DataRetentionRule> rules) {
        final DataRetentionRules dataRetentionRules = DataRetentionRules
                .builder()
                .uuid(UUID.randomUUID().toString())
                .rules(rules)
                .build();
        dataRetentionRules.setVersion(RULES_VERSION);
        return dataRetentionRules;
    }

    private DataRetentionRule buildRule(final int ruleNo,
                                        final boolean isEnabled,
                                        final int age,
                                        final TimeUnit timeUnit,
                                        final ExpressionOperator expressionOperator) {


        return DataRetentionRule.ageRule(ruleNo,
                Instant.now().toEpochMilli(),
                "Rule" + ruleNo + "_" + age + timeUnit.getDisplayValue(),
                isEnabled,
                expressionOperator,
                age,
                timeUnit);
    }

    private DataRetentionRule buildRule(final int ruleNo,
                                        final boolean isEnabled,
                                        final int age,
                                        final TimeUnit timeUnit) {
        final ExpressionOperator expressionOperator = ExpressionOperator.builder()
                .addTerm(MetaFields.FIELD_FEED, ExpressionTerm.Condition.EQUALS, "RULE_" + ruleNo + "FEED")
                .build();
        return buildRule(ruleNo, isEnabled, age, timeUnit, expressionOperator);
    }

    private DataRetentionRule buildForeverRule(final int ruleNo,
                                               final boolean isEnabled) {
        final ExpressionOperator expressionOperator = ExpressionOperator.builder()
                .addTerm(MetaFields.FIELD_FEED, ExpressionTerm.Condition.EQUALS, "RULE_" + ruleNo + "FEED")
                .build();
        return buildForeverRule(ruleNo, isEnabled, expressionOperator);

    }

    private DataRetentionRule buildForeverRule(final int ruleNo,
                                               final boolean isEnabled,
                                               final ExpressionOperator expressionOperator) {
        return DataRetentionRule.foreverRule(ruleNo,
                Instant.now().toEpochMilli(),
                "Rule" + ruleNo + "_Forever",
                isEnabled,
                expressionOperator);
    }

    private List<DataRetentionTracker> createTrackers(final List<DataRetentionRule> dataRetentionRules,
                                                      final String rulesVersion,
                                                      final Instant lastRunTime) {
        final Instant aFixedTime = Instant.now();
        final AtomicBoolean seenForeverRule = new AtomicBoolean(false);
        final List<DataRetentionTracker> trackersFromRules = dataRetentionRules.stream()
                .sorted(Comparator.comparing(
                        rule ->
                                // Usa a random but fixed time to convert the ages into instants for comparisons
                                DataRetentionCreationTimeUtil.minus(aFixedTime, rule),
                        Comparator.reverseOrder()))
                .map(rule -> {
                    if (rule.isForever()) {
                        seenForeverRule.set(true);
                    }
                    return new DataRetentionTracker(
                            rulesVersion,
                            rule.getAgeString(),
                            lastRunTime);
                })
                .collect(Collectors.toList());

        final List<DataRetentionTracker> trackers;
        if (seenForeverRule.get()) {
            trackers = trackersFromRules;
        } else {
            // If there is no forever rule then make sure we have a tracker for that as the
            // retention would have created a forever tracker on its last run
            trackers = new ArrayList<>(trackersFromRules);
            trackers.add(new DataRetentionTracker(rulesVersion, DataRetentionRule.FOREVER, lastRunTime));
        }

        // We will never have a tracker for the youngest age as we always retain that data
        // so remove it
        trackers.remove(0);

        LOGGER.debug("Mocked up trackers:\n{}", trackers.stream()
                .map(DataRetentionTracker::toString)
                .collect(Collectors.joining("\n")));

        return trackers;
    }
}
