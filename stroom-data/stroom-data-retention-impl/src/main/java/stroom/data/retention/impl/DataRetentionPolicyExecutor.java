/*
 * Copyright 2017 Crown Copyright
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
import stroom.data.retention.api.DataRetentionConfig;
import stroom.data.retention.shared.DataRetentionRule;
import stroom.data.retention.api.DataRetentionRuleAction;
import stroom.data.retention.shared.DataRetentionRules;
import stroom.data.retention.api.DataRetentionTracker;
import stroom.data.retention.api.RetentionRuleOutcome;
import stroom.meta.api.MetaService;
import stroom.task.api.TaskContext;
import stroom.task.api.TaskContextFactory;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogExecutionTime;
import stroom.util.logging.LogUtil;
import stroom.util.time.TimePeriod;
import stroom.util.time.TimeUtils;

import com.google.common.collect.Ordering;

import javax.inject.Inject;
import javax.inject.Provider;
import java.time.Duration;
import java.time.Instant;
import java.time.Period;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * This class is concerned with logically deleting meta records according to a set of data
 * retention rules. Rules have precedence, with a higher rule number meaning the rule is more
 * important. All rules need to be applied to all meta records in precedence order. The first
 * matching rule applies. If a rule matches, no other rules will be tested/applied. If no rules
 * match then that record will not be deleted.
 *
 * Once the rules have been run over all the data then (assuming the rules have not changed) there
 * is no need to scan all the data on next run. To limit the data being scanned we split the time since
 * epoch to now into periods delimited by the earliest create times of each rule.  If a rule has an
 * age of 1 day then the min create time is now()-1d.
 *
 *  If we have a 1mth rule and a 1yr rule then the following periods will be used:
 *  1mnth ago => now (Ignored as ALL rules have age >= this, so all data is retained)
 *  1yr ago => 1mnth ago
 *  epoch => 1yr ago
 *
 *  Disabled rules are ignored and don't count towards the period splits.
 *
 *  A tracker record is used to keep track of what time the last run happened so we can offset the
 *  periods by that amount. Using the period example from above, if the tracker had a last run time
 *  of 1d ago then the periods become:
 *  1d ago => now (Ignored as ALL rules have age >= this, so all data is retained)
 *  1mnth+1d ago => 1mnth ago
 *  1yr+1d ago => 1yr ago
 */
public class DataRetentionPolicyExecutor {
    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(DataRetentionPolicyExecutor.class);

    private static final String LOCK_NAME = "DataRetentionExecutor";

    private final ClusterLockService clusterLockService;
    private final Provider<DataRetentionRules> dataRetentionRulesProvider;
    private final DataRetentionConfig policyConfig;
    private final MetaService metaService;
    private final TaskContextFactory taskContextFactory;
    private final AtomicBoolean running = new AtomicBoolean();

    @Inject
    DataRetentionPolicyExecutor(final ClusterLockService clusterLockService,
                                final Provider<DataRetentionRules> dataRetentionRulesProvider,
                                final DataRetentionConfig policyConfig,
                                final MetaService metaService,
                                final TaskContextFactory taskContextFactory) {
        this.clusterLockService = clusterLockService;
        this.dataRetentionRulesProvider = dataRetentionRulesProvider;
        this.policyConfig = policyConfig;
        this.metaService = metaService;
        this.taskContextFactory = taskContextFactory;
    }

    public void exec() {
        exec(Instant.now());
    }

    /**
     * Pkg private so we can test with a known now time
     */
    void exec(final Instant now) {
        if (running.compareAndSet(false, true)) {
            try {
                clusterLockService.tryLock(LOCK_NAME, () -> {
                    try {
                        taskContextFactory.context("Data Retention", taskContext -> {
                            info(taskContext, () -> "Starting data retention process");
                            final LogExecutionTime logExecutionTime = new LogExecutionTime();
                            process(taskContext, now);
                            info(taskContext, () -> "Finished data retention process in " + logExecutionTime);
                        }).run();
                    } catch (final RuntimeException e) {
                        LOGGER.error(e::getMessage, e);
                    }
                });
            } finally {
                running.set(false);
            }
        }
    }

    private synchronized void process(final TaskContext taskContext, final Instant now) {
        final DataRetentionRules dataRetentionRules = dataRetentionRulesProvider.get();
        LOGGER.info("Using run time {}", now);
        if (dataRetentionRules != null) {
            final List<DataRetentionRule> activeRules = getActiveRules(dataRetentionRules.getRules());

            if (activeRules.size() > 0) {

                // Use tracker to establish how long ago we last ran this process so we can avoid
                // scanning over data that has already been evaluated
                final Duration timeSinceLastRun = getTimeSinceLastRun(now, dataRetentionRules);

                // Create a map of unique periods with the set of rules that apply to them.
                final Map<TimePeriod, List<DataRetentionRuleAction>> ruleActionsByPeriod = getRulesByPeriod(
                        activeRules, now, timeSinceLastRun);

                final AtomicBoolean allSuccessful = new AtomicBoolean(true);

                // Rules must be in ascending order by rule number so they applied in the correct order
                ruleActionsByPeriod.entrySet()
                        .stream()
                        .sorted(Comparator.comparing(entry ->
                                // Work backwards in time
                                entry.getKey().getFrom(), Comparator.reverseOrder()))
                        .forEach(entry -> {
                            List<DataRetentionRuleAction> ruleActions = entry.getValue();
                            TimePeriod period = entry.getKey();

                            final List<DataRetentionRuleAction> sortedActions = ruleActions.stream()
                                    .sorted(DataRetentionRuleAction.comparingByRuleNo())
                                    .collect(Collectors.toList());

                            // Skip if we have terminated processing.
                            if (!Thread.currentThread().isInterrupted()) {
                                processPeriod(taskContext, period, sortedActions, now);
                                if (!Thread.currentThread().isInterrupted()) {
                                    allSuccessful.set(false);
                                }
                            }
                        });

                // If we finished running then update the tracker for use next time.
                if (!Thread.currentThread().isInterrupted() && allSuccessful.get()) {
                    metaService.setTracker(new DataRetentionTracker(now, dataRetentionRules.getVersion()));
                }
            } else {
                LOGGER.info("No active rules to process");
            }
        }
    }

    private Duration getTimeSinceLastRun(final Instant now, final DataRetentionRules dataRetentionRules) {
        // Load the last tracker used, if there is one
        // If the rules have changed since that run, then ignore it
        // Calculate the amount of time that has elapsed since we last ran.
        final Duration timeSinceLastRun = metaService.getRetentionTracker()
                .flatMap(tracker -> {
                        // If rules ver in tracker doesn't match, treat as if tracker isn't there
                        if (tracker.getRulesVersion().equals(dataRetentionRules.getVersion())) {
                            LOGGER.info("Found valid tracker {}", tracker);
                            return Optional.of(tracker);
                        } else {
                            LOGGER.info("Tracker version is out of date, ignoring it rules version: {}, {}",
                                    dataRetentionRules.getVersion(), tracker);
                            return Optional.empty();
                        }
                })
                .map(DataRetentionTracker::getLastRunTime)
                .map(lastRun -> Duration.between(lastRun, now))
                .orElseGet(() -> Duration.between(Instant.EPOCH, now));

        LOGGER.info("Treating time since last run as: {}", timeSinceLastRun);
        return timeSinceLastRun;
    }

    private List<DataRetentionRule> getActiveRules(final List<DataRetentionRule> rules) {
        final List<DataRetentionRule> activeRules;// make sure we create a list of rules that are enabled and have at least one enabled term.
        if (rules != null) {
            activeRules = rules.stream()
                    .filter(rule -> rule.isEnabled()
                            && rule.getExpression() != null
                            && rule.getExpression().enabled())
                    .collect(Collectors.toList());
        } else {
            activeRules = Collections.emptyList();
        }
        return activeRules;
    }

    private void processPeriod(final TaskContext taskContext,
                               final TimePeriod period,
                               final List<DataRetentionRuleAction> sortedRuleActions,
                               final Instant now) {
        info(taskContext, () -> {
            final Function<DataRetentionRuleAction, String> ruleInfo = rule ->
                    rule.toString() + " " +
                            rule.getRule().getAge() + " " + rule.getRule().getTimeUnit().getDisplayValue() + " " +
                            rule.getRule().getExpression() + " " + rule.getOutcome();

            // Get the ages of the two dates in the period
            final Period fromTimeAge = TimeUtils.instantAsAge(period.getFrom(), now);
            final Period toTimeAge = TimeUtils.instantAsAge(period.getTo(), now);

            return "" +
                    "Considering stream retention for streams created " +
                    "between " +
                    period.getFrom() + " (" + fromTimeAge + " ago)" +
                    " and " +
                    period.getTo() + " (" + toTimeAge + " ago)" +
                    " [" + period.getDurationStr() +
                    "], " + sortedRuleActions.size() + " rules:\n" +
                    sortedRuleActions.stream()
                            .map(ruleInfo)
                            .collect(Collectors.joining("\n"));
        });

        int count = -1;
        while (count != 0 && !Thread.currentThread().isInterrupted()) {
            count = metaService.delete(sortedRuleActions, period);
            final String message = "Marked " + count + " items as deleted";
            LOGGER.info(() -> message);
        }
    }

    private Map<TimePeriod, List<DataRetentionRuleAction>> getRulesByPeriod(
            final List<DataRetentionRule> activeRules,
            final Instant now,
            final Duration timeSinceLastRun) {

        // Get effective minimum meta creation times for each rule.
        // A meta creation time earlier than one of these minimums is valid
        // for deletion by the corresponding rules (if the expression criteria is matched)
        final Map<Instant, Set<DataRetentionRule>> minCreationTimeMap = getMinCreationTimeMap(
                activeRules, now);

        // We need to work down the list of min time thresholds in newest -> oldest order
        final List<Instant> descendingMinCreationTimes = minCreationTimeMap.keySet()
                .stream()
                .sorted(Comparator.reverseOrder())
                .collect(Collectors.toList());

        final Instant latestMinCreationTime = descendingMinCreationTimes.get(0);

        // Create a map of unique periods with the set of rules that apply to them.
        final Map<TimePeriod, List<DataRetentionRuleAction>> rulesByPeriod = new HashMap<>();

        // We don't need to delete anything newer than this time as all rules have a minCreateTime
        // older than or equal to it.  Thus make this the end of our first deletion period
        Instant toTime = latestMinCreationTime;
        // Ignore the period from latestMinCreationTime => now
        descendingMinCreationTimes.remove(latestMinCreationTime);

        for (final Instant creationTime : descendingMinCreationTimes) {
            // Calculate a from time for the period that takes account of the
            // time passed since the last execution.
            // e.g if rule is for 1 month and we are considering the period from
            // now - 1month  to now then from time is now - 1month but if
            // we ran this process 1day ago then the period is from now -1d to now
            // as the rest of the data has already been considered.
            final Instant fromTime = Ordering.natural()
                    .max(creationTime, toTime.minus(timeSinceLastRun));

            final TimePeriod period = TimePeriod.between(fromTime, toTime);

            LOGGER.debug(() -> LogUtil.message("creationTime: {} ({} ago), periodFrom {} ago, periodTo {} ago",
                    creationTime,
                    TimeUtils.instantAsAge(creationTime, now),
                    TimeUtils.instantAsAge(period.getFrom(), now),
                    TimeUtils.instantAsAge(period.getTo(), now)));

            activeRules.forEach(rule -> {
                final RetentionRuleOutcome ruleOutcome = getRuleOutcome(period, rule, now);

                final DataRetentionRuleAction ruleAction = new DataRetentionRuleAction(rule, ruleOutcome);

                rulesByPeriod.computeIfAbsent(period, k -> new ArrayList<>())
                        .add(ruleAction);

                LOGGER.debug(() -> LogUtil.message(
                        "  {} rule: {}, ruleMinCreateTime: {} ({} ago), from: {} ({} ago), to {} ({} ago)",
                        ruleOutcome,
                        rule,
                        getMinCreateTime(rule, now),
                        TimeUtils.instantAsAge(getMinCreateTime(rule, now), now),
                        period.getFrom(),
                        TimeUtils.instantAsAge(period.getFrom(), now),
                        period.getTo(),
                        TimeUtils.instantAsAge(period.getTo(), now)));
            });

            toTime = creationTime;
        }
        return rulesByPeriod;
    }

    private static RetentionRuleOutcome getRuleOutcome(final TimePeriod period,
                                                       final DataRetentionRule rule,
                                                       final Instant now) {
        Instant ruleMinCreateTime = getMinCreateTime(rule, now);

        // Work out if this rule should delete or retain data in this period
        return period.getFrom().isBefore(ruleMinCreateTime)
                ? RetentionRuleOutcome.DELETE
                : RetentionRuleOutcome.RETAIN;
    }

    private Map<Instant, Set<DataRetentionRule>> getMinCreationTimeMap(
            final List<DataRetentionRule> activeRules,
            final Instant now) {

        // Use the rule retention period to work out the earliest meta createTime that
        // we could delete matching records from
        final Map<Instant, Set<DataRetentionRule>> minCreationTimeMap = activeRules.stream()
                .collect(Collectors.groupingBy(
                        rule -> getMinCreateTime(rule, now),
                        Collectors.toSet()));

        // Ensure we have a min creation time of the epoch to ensure we delete any qualifying data
        // right back as far as the data goes.
        minCreationTimeMap.computeIfAbsent(Instant.EPOCH, k -> new HashSet<>());

        LOGGER.debug(() -> "minCreationTimes:\n" + minCreationTimeMap.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.reverseOrder()))
                .map(entry -> entry.getKey() + " (" +
                        TimeUtils.instantAsAge(entry.getKey(), now) +
                        " ago) - " +
                        entry.getValue().stream()
                                .sorted(Comparator.comparing(DataRetentionRule::getRuleNumber))
                                .collect(Collectors.toList()))
                .collect(Collectors.joining("\n")));

        return minCreationTimeMap;
    }

    private static Instant getMinCreateTime(final DataRetentionRule rule, Instant now) {
        return DataRetentionCreationTimeUtil.minus(now, rule);
    }

    private void info(final TaskContext taskContext, final Supplier<String> messageSupplier) {
        LOGGER.info(messageSupplier);
        taskContext.info(messageSupplier);
    }
}
