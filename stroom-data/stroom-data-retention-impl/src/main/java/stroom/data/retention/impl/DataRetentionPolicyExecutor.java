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
import stroom.data.retention.api.DataRetentionCreationTimeUtil;
import stroom.data.retention.api.DataRetentionRuleAction;
import stroom.data.retention.api.DataRetentionRulesProvider;
import stroom.data.retention.api.DataRetentionTracker;
import stroom.data.retention.api.RetentionRuleOutcome;
import stroom.data.retention.shared.DataRetentionRule;
import stroom.data.retention.shared.DataRetentionRules;
import stroom.meta.api.MetaService;
import stroom.task.api.TaskContextFactory;
import stroom.task.api.TaskTerminatedException;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogExecutionTime;
import stroom.util.logging.LogUtil;
import stroom.util.time.TimePeriod;
import stroom.util.time.TimeUtils;

import com.google.common.collect.Ordering;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import jakarta.annotation.Nullable;
import jakarta.inject.Inject;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * See also stroom-data/stroom-data-retention-impl/docs/design.md
 * <p>
 * This class is concerned with logically deleting meta records according to a set of data
 * retention rules. Rules have precedence, with a higher rule number meaning the rule is more
 * important. All rules need to be applied to all meta records in precedence order. The first
 * matching rule applies. If a rule matches, no other rules will be tested/applied. If no rules
 * match then that record will not be deleted.
 * <p>
 * Once the rules have been run over all the data then (assuming the rules have not changed) there
 * is no need to scan all the data on next run. To limit the data being scanned we split the time since
 * epoch to now into periods delimited by the earliest create times of each rule.  If a rule has an
 * age of 1 day then the min create time is now()-1d.
 * <p>
 * If we have a 1mth rule and a 1yr rule then the following periods will be used:
 * 1mnth ago => now (Ignored as ALL rules have age >= this, so all data in this period is retained)
 * 1yr ago => 1mnth ago
 * epoch => 1yr ago
 * <p>
 * Disabled rules are ignored and don't count towards the period splits.
 * <p>
 * A tracker record is used to keep track of what time the last run happened so we can offset the
 * periods by that amount. Using the period example from above, if the tracker had a last run time
 * of 1d ago then the periods become:
 * 1d ago => now (Ignored as ALL rules have age >= this, so all data is retained)
 * 1mnth+1d ago => 1mnth ago
 * 1yr+1d ago => 1yr ago
 */
public class DataRetentionPolicyExecutor {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(DataRetentionPolicyExecutor.class);

    public static final String JOB_NAME = "Policy Based Data Retention";
    private static final String LOCK_NAME = "DataRetentionExecutor";

    private final ClusterLockService clusterLockService;
    private final DataRetentionRulesProvider dataRetentionRulesProvider;
    private final DataRetentionConfig policyConfig;
    private final MetaService metaService;
    private final TaskContextFactory taskContextFactory;
    private final AtomicBoolean running = new AtomicBoolean();

    @Inject
    DataRetentionPolicyExecutor(final ClusterLockService clusterLockService,
                                final DataRetentionRulesProvider dataRetentionRulesProvider,
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
        clusterLockService.tryLock(LOCK_NAME, () -> {
            final LogExecutionTime logExecutionTime = new LogExecutionTime();
            try {
                info(() -> "Starting data retention process");
                // MUST truncate down to millis as the DB stores in millis and TimePeriod
                // also truncates to millis so we need to work to a consistent precision else
                // some of the date logic fails due to micro second differences
                process(now.truncatedTo(ChronoUnit.MILLIS));
                info(() -> "Finished data retention process in " + logExecutionTime);
            } catch (final TaskTerminatedException e) {
                LOGGER.debug("Task terminated", e);
                LOGGER.error(JOB_NAME + " - Task terminated after " + logExecutionTime);
            } catch (final RuntimeException e) {
                LOGGER.error(JOB_NAME + " - Error enforcing data retention policies: {}", e.getMessage(), e);
            }
        });
    }

    private synchronized void process(final Instant now) {
        final DataRetentionRules dataRetentionRules = dataRetentionRulesProvider.getOrCreate();
        LOGGER.info("All retention time calculations based on now()={}", now);
        if (dataRetentionRules != null) {
            final List<DataRetentionRule> activeRules = getActiveRules(dataRetentionRules.getRules());

            if (activeRules != null && activeRules.size() > 0) {

                // Create a map of unique periods with the set of rules that apply to them.
                final List<ProcessablePeriod> processablePeriods = getProcessPeriods(
                        dataRetentionRules,
                        activeRules,
                        now);

                // Rules must be in ascending order by rule number so they applied in the correct order
                processablePeriods.stream()
                        .sorted(Comparator.comparing(processablePeriod ->
                                // Work backwards in time
                                processablePeriod.timePeriod.getFrom(), Comparator.reverseOrder()))
                        .takeWhile(entry -> {
                            if (Thread.currentThread().isInterrupted()) {
                                LOGGER.error("Thread interrupted");
                                throw new TaskTerminatedException();
                            } else {
                                return true;
                            }
                        })
                        .forEach(processablePeriod -> {
                            final List<DataRetentionRuleAction> ruleActions =
                                    processablePeriod.dataRetentionRuleActions;
                            final TimePeriod period = processablePeriod.timePeriod;

                            final List<DataRetentionRuleAction> sortedActions = ruleActions.stream()
                                    .sorted(DataRetentionRuleAction.comparingByRuleNo())
                                    .collect(Collectors.toList());

                            processPeriod(period, sortedActions, processablePeriod.ruleAge, now);

                            // We have successfully processed this period so update the tracker
                            // so the next run on this period can work from where we got to
                            final DataRetentionTracker newTracker = new DataRetentionTracker(
                                    dataRetentionRules.getVersion(),
                                    processablePeriod.ruleAge,
                                    now);
                            metaService.setTracker(newTracker);
                        });
            } else {
                LOGGER.info("No active rules to process");
            }
        } else {
            LOGGER.info("No active rules to process");
        }
    }

    private Map<String, DataRetentionTracker> getTrackers(final DataRetentionRules dataRetentionRules) {
        final List<DataRetentionTracker> allTrackers = metaService.getRetentionTrackers();

        // Delete any trackers that have a different rules version to the one we have
        allTrackers.stream()
                .map(DataRetentionTracker::getRulesVersion)
                .distinct()
                .filter(rulesVersion ->
                        !rulesVersion.equals(dataRetentionRules.getVersion()))
                .forEach(metaService::deleteTrackers);

        // Return the valid trackers (if there are any) keyed no rule age
        return allTrackers.stream()
                .filter(tracker -> tracker.getRulesVersion().equals(dataRetentionRules.getVersion()))
                .collect(Collectors.toMap(DataRetentionTracker::getRuleAge, Function.identity()));
    }

    private Optional<Duration> getTimeSinceLastRun(
            final Instant now,
            @Nullable final DataRetentionTracker dataRetentionTracker) {

        final Optional<Duration> optTimeSinceLastRun = Optional.ofNullable(dataRetentionTracker)
                .map(tracker -> Duration.between(dataRetentionTracker.getLastRunTime(), now));

        LOGGER.debug(() -> LogUtil.message("Treating timeSinceLastRun as: {}, ruleAge: {}",
                optTimeSinceLastRun,
                (dataRetentionTracker != null
                        ? dataRetentionTracker.getRuleAge()
                        : "NO TRACKER")));

        return optTimeSinceLastRun;
    }

//    private Duration getTimeSinceLastRun(final Instant now, final DataRetentionRules dataRetentionRules) {
//        // Load the last tracker used, if there is one
//        // If the rules have changed since that run, then ignore it
//        // Calculate the amount of time that has elapsed since we last ran.
//        final Duration timeSinceLastRun = metaService.getRetentionTracker()
//                .flatMap(tracker -> {
//                    // If rules ver in tracker doesn't match, treat as if tracker isn't there
//                    if (tracker.getRulesVersion().equals(dataRetentionRules.getVersion())) {
//                        LOGGER.info("Found valid tracker {}", tracker);
//                        return Optional.of(tracker);
//                    } else {
//                        LOGGER.info("Tracker version is out of date, ignoring it. Current rules " +
//                                        "version: {}, tracker version {}",
//                                dataRetentionRules.getVersion(), tracker.getRulesVersion());
//                        return Optional.empty();
//                    }
//                })
//                .map(DataRetentionTracker::getLastRunTime)
//                .map(lastRun -> Duration.between(lastRun, now))
//                .orElseGet(() -> Duration.between(Instant.EPOCH, now));
//
//        LOGGER.info("Treating time since last run as: {}", timeSinceLastRun);
//        return timeSinceLastRun;
//    }

    private List<DataRetentionRule> getActiveRules(final List<DataRetentionRule> rules) {
        // make sure we create a list of rules that are enabled and have at least one enabled term.
        final List<DataRetentionRule> activeRules;
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

    private String getRuleInfo(final DataRetentionRule rule) {
        return String.join(" - ",
                rule.toString(),
                rule.getAgeString(),
                rule.getExpression().toString());
    }

    private String getRuleActionInfo(final DataRetentionRuleAction ruleAction) {
        return String.join(" - ",
                ruleAction.getOutcome().toString(),
                getRuleInfo(ruleAction.getRule()));
    }

    private String getPeriodInfo(final TimePeriod period, final Instant now) {
        final String fromTimeAgeStr = Instant.EPOCH.equals(period.getFrom())
                ? "EPOCH"
                : TimeUtils.instantAsAgeStr(period.getFrom(), now) + " ago";
        final String toTimeAgeStr = TimeUtils.instantAsAgeStr(period.getTo(), now);

        // Duration is better for shorter periods as Period will just say P0D for small stuff
        final String durationStr = period.getDuration().compareTo(Duration.ofDays(30)) < 0
                ? period.getDuration().toString()
                : period.getPeriod().toString();

        return LogUtil.message("{} => {} ago ({} => {}) [duration: {}]",
                fromTimeAgeStr,
                toTimeAgeStr,
                period.getFrom(),
                period.getTo(),
                TimeUtils.periodAsAgeStr(period));
    }

    private void processPeriod(final TimePeriod period,
                               final List<DataRetentionRuleAction> sortedRuleActions,
                               final String ruleAge,
                               final Instant now) {
        info(() -> {
            return LogUtil.message(
                    "Considering streams created " +
                    "between {}, {} rule actions:\n{}",
                    getPeriodInfo(period, now),
                    sortedRuleActions.size(),
                    sortedRuleActions.stream()
                            .map(this::getRuleActionInfo)
                            .collect(Collectors.joining("\n")));
        });

        LOGGER.logDurationIfInfoEnabled(
                () ->
                        metaService.delete(sortedRuleActions, period),
                count ->
                        LogUtil.message("Marked {} items as deleted for ruleAge: {}, period: {}",
                                count, ruleAge, period));
    }

    private List<ProcessablePeriod> getProcessPeriods(
            final DataRetentionRules dataRetentionRules,
            final List<DataRetentionRule> activeRules,
            final Instant now) {

        // Get effective earliest meta creation times for each rule.
        // A meta creation time earlier than one of these minimums is valid
        // for deletion unless retained by another rule
        final Map<Instant, Set<DataRetentionRule>> earliestRetainedCreationTimeMap =
                getEarliestRetainedCreationTimeMap(activeRules, now);

        final Map<Instant, DataRetentionTracker> trackersByEarliestRetainedCreateTime =
                getTrackersByEarliestCreateTime(dataRetentionRules, earliestRetainedCreationTimeMap);

        // We need to work down the list of min time thresholds in newest -> oldest order
        final List<Instant> descendingCreationTimes = earliestRetainedCreationTimeMap.keySet()
                .stream()
                .sorted(Comparator.reverseOrder())
                .collect(Collectors.toList());

        final Instant mostRecentCreationTime = descendingCreationTimes.get(0);

        // We don't need to delete anything newer than this time as all rules have a minCreateTime
        // older than or equal to it.  Thus make this the end of our first deletion period
        Instant toTime = mostRecentCreationTime;
        // Ignore the period from latestMinCreationTime => now
        descendingCreationTimes.remove(mostRecentCreationTime);

        final List<ProcessablePeriod> processablePeriods = new ArrayList<>();

        for (final Instant creationTime : descendingCreationTimes) {

            final String ruleAge = getRuleAge(earliestRetainedCreationTimeMap, creationTime);

            // Use tracker to establish how long ago we last ran this process so we can avoid
            // scanning over data that has already been evaluated. Tracker may be null
            final DataRetentionTracker tracker = trackersByEarliestRetainedCreateTime.get(creationTime);
            final Optional<Duration> optTimeSinceLastRun = getTimeSinceLastRun(now, tracker);

            // Calculate a from time for the period that takes account of the
            // time passed since the last execution.
            // e.g if rule is for 1 month and we are considering the period from
            // now - 1month  to now then from time is now - 1month but if
            // we ran this process 1day ago then the period is from now -1d to now
            // as the rest of the data has already been considered.

            final Instant fromTime;
            if (optTimeSinceLastRun.isPresent()) {
                final Instant adjustedFromTime = toTime.minus(optTimeSinceLastRun.get());

                // If the time since last run takes us before the time period we are working with
                // then leave it unadjusted
                fromTime = Ordering.natural()
                        .max(creationTime, adjustedFromTime);

                LOGGER.debug("toTime: {}, createTime: {}, adjustedToTime {}, fromTime: {}",
                        toTime, creationTime, adjustedFromTime, fromTime);
            } else {
                fromTime = creationTime;
                LOGGER.debug("No tracker so use un-adjusted fromTime: {}", fromTime);
            }

            final TimePeriod period = TimePeriod.between(fromTime, toTime);

            LOGGER.debug(() -> LogUtil.message("creationTime: {} ({} ago), ruleAge: '{}', tracker: ({}), period: {}",
                    creationTime,
                    TimeUtils.instantAsAge(creationTime, now),
                    ruleAge,
                    (tracker != null
                            ? "ruleAge: '" + tracker.getRuleAge() + "', lastRunTime: " + tracker.getLastRunTime()
                            : "null"),
                    getPeriodInfo(period, now)));

            final List<DataRetentionRuleAction> ruleActions = activeRules.stream()
                    .map(rule -> {
                        final RetentionRuleOutcome ruleOutcome = getRuleOutcome(period, rule, now);

                        final DataRetentionRuleAction ruleAction = new DataRetentionRuleAction(rule, ruleOutcome);

                        LOGGER.debug(() -> LogUtil.message(
                                "  {} Rule: {}, ruleMinCreateTime: {} ({} ago)",
                                ruleOutcome,
                                rule,
                                getEarliestRetainedCreateTime(rule, now),
                                TimeUtils.instantAsAge(getEarliestRetainedCreateTime(rule, now), now)));
                        return ruleAction;
                    })
                    .collect(Collectors.toList());

            final ProcessablePeriod processablePeriod = new ProcessablePeriod(
                    period,
                    ruleAge,
                    ruleActions,
                    tracker);
            processablePeriods.add(processablePeriod);

            // Move the toTime ready for the next time period in the loop
            toTime = creationTime;
        }

        return processablePeriods;
    }

    private String getRuleAge(final Map<Instant, Set<DataRetentionRule>> earliestRetainedCreationTimeMap,
                              final Instant createTime) {
        final Set<DataRetentionRule> rules = earliestRetainedCreationTimeMap.get(createTime);
        if (rules == null) {
            return null;
        } else if (rules.isEmpty()) {
            return DataRetentionRule.FOREVER;
        } else {
            return rules.iterator()
                    .next()
                    .getAgeString();
        }
    }

    private Map<Instant, DataRetentionTracker> getTrackersByEarliestCreateTime(
            final DataRetentionRules dataRetentionRules,
            final Map<Instant, Set<DataRetentionRule>> earliestRetainedCreationTimeMap) {

        // All valid trackers key by their rule age, e.g. "30 Days", "Forever", etc.
        final Map<String, DataRetentionTracker> trackersByRuleAge = getTrackers(dataRetentionRules);

        final Map<Instant, DataRetentionTracker> trackersByEarliestCreateTime =
                earliestRetainedCreationTimeMap.keySet()
                        .stream()
                        .map(createTime -> {
                            final String ruleAge = getRuleAge(earliestRetainedCreationTimeMap, createTime);
                            return Tuple.of(createTime, trackersByRuleAge.get(ruleAge));
                        })
                        .filter(tuple2 ->
                                tuple2._2() != null)
                        .collect(Collectors.toMap(
                                Tuple2::_1, Tuple2::_2));

        LOGGER.debug(() -> LogUtil.message("trackersByEarliestCreateTime:\n{}",
                trackersByEarliestCreateTime.entrySet()
                        .stream()
                        .sorted(Entry.comparingByKey())
                        .map(entry ->
                                "createTime: "
                                + entry.getKey()
                                + " => (ruleAge: "
                                + entry.getValue().getRuleAge()
                                + ", lastRunTime: "
                                + entry.getValue().getLastRunTime())
                        .collect(Collectors.joining("\n"))));

        return trackersByEarliestCreateTime;
    }

    private RetentionRuleOutcome getRuleOutcome(final TimePeriod period,
                                                final DataRetentionRule rule,
                                                final Instant now) {
        final Instant ruleMinCreateTime = getEarliestRetainedCreateTime(rule, now);

        // Work out if this rule should delete or retain data in this period
        return period.getFrom().isBefore(ruleMinCreateTime)
                ? RetentionRuleOutcome.DELETE
                : RetentionRuleOutcome.RETAIN;
    }

    private Map<Instant, Set<DataRetentionRule>> getEarliestRetainedCreationTimeMap(
            final List<DataRetentionRule> activeRules,
            final Instant now) {

        // Use the rule retention period to work out the earliest meta createTime that
        // would be RETAINED by the rule
        // e.g ruleAge: 30 days, so everything older than (now() minus 30d) will be deleted.
        final Map<Instant, Set<DataRetentionRule>> earliestRetainedCreationTimeMap = activeRules.stream()
                .collect(Collectors.groupingBy(
                        rule -> getEarliestRetainedCreateTime(rule, now),
                        Collectors.toSet()));

        // Ensure we have a min creation time of the epoch to ensure we delete any qualifying data
        // right back as far as the data goes. No rules in the set to retain the data so it gets deleted.
        earliestRetainedCreationTimeMap.computeIfAbsent(Instant.EPOCH, k -> new HashSet<>());

        LOGGER.debug(() -> "earliestRetainedCreationTimeMap:\n" + earliestRetainedCreationTimeMap.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.reverseOrder()))
                .map(entry -> "createTime: "
                              + entry.getKey() + " ("
                              + TimeUtils.instantAsAge(entry.getKey(), now)
                              + " ago) => "
                              + entry.getValue().stream()
                                      .sorted(Comparator.comparing(DataRetentionRule::getRuleNumber))
                                      .collect(Collectors.toList()))
                .collect(Collectors.joining("\n")));

        return earliestRetainedCreationTimeMap;
    }

    /**
     * Gets the earliest meta create time that would be retained by this rule.
     */
    private static Instant getEarliestRetainedCreateTime(final DataRetentionRule rule, final Instant now) {
        return DataRetentionCreationTimeUtil.minus(now, rule);
    }

    private void info(final Supplier<String> messageSupplier) {
        LOGGER.info(messageSupplier);
        taskContextFactory.current().info(messageSupplier);
    }

    /**
     * Holds state for each of the time periods that we will process
     */
    private static class ProcessablePeriod {

        private final TimePeriod timePeriod;
        private final String ruleAge;
        private final List<DataRetentionRuleAction> dataRetentionRuleActions;
        private final DataRetentionTracker tracker;

        private ProcessablePeriod(final TimePeriod timePeriod,
                                  final String ruleAge,
                                  final List<DataRetentionRuleAction> dataRetentionRuleActions,
                                  final DataRetentionTracker tracker) {
            this.timePeriod = timePeriod;
            this.ruleAge = ruleAge;
            this.dataRetentionRuleActions = dataRetentionRuleActions;
            this.tracker = tracker;
        }
    }
}
