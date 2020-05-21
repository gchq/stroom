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
import stroom.data.retention.shared.DataRetentionRule;
import stroom.data.retention.shared.DataRetentionRules;
import stroom.meta.api.MetaService;
import stroom.query.api.v2.ExpressionOperator;
import stroom.task.api.TaskContext;
import stroom.task.api.TaskContextFactory;
import stroom.util.io.FileUtil;
import stroom.util.json.JsonUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogExecutionTime;
import stroom.util.logging.LogUtil;
import stroom.util.time.TimePeriod;
import stroom.util.time.TimeUtils;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.google.common.collect.Ordering;

import javax.inject.Inject;
import javax.inject.Provider;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.Period;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

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
        if (running.compareAndSet(false, true)) {
            try {
                clusterLockService.tryLock(LOCK_NAME, () -> {
                    try {
                        taskContextFactory.context("Data Retention", taskContext -> {
                            info(taskContext, () -> "Starting data retention process");
                            final LogExecutionTime logExecutionTime = new LogExecutionTime();
                            process(taskContext);
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

    private synchronized void process(final TaskContext taskContext) {
        final DataRetentionRules dataRetentionRules = dataRetentionRulesProvider.get();
        if (dataRetentionRules != null) {
            final List<DataRetentionRule> rules = dataRetentionRules.getRules();

            final List<DataRetentionRule> activeRules = getActiveRules(rules);

            if (activeRules.size() > 0) {
                // Figure out what the batch size will be for deletion.
                final int batchSize = policyConfig.getDeleteBatchSize();

                // Calculate the data retention ages for all enabled rules.
                final Instant now = Instant.now();

                // Load the last tracker used.
                Tracker tracker = Tracker.load();

                // If the data retention policy has changed then we need to assume it has never been run before,
                // i.e. all data must be considered for retention checking.
                if (tracker == null || !tracker.rulesEquals(dataRetentionRules)) {
                    tracker = new Tracker(null, dataRetentionRules);
                }

                // Calculate the amount of time that has elapsed since we last ran.
//                long elapsedTime = nowMs;
                final Duration timeSinceLastRun = tracker.getLastRun()
                        .map(lastRun -> Duration.between(lastRun, now))
                        .orElseGet(() -> Duration.between(Instant.EPOCH, now));

                LOGGER.info("Time since last run: {}", timeSinceLastRun);

//                if (tracker.getLastRun() != null) {
//                    timeSinceLastRun = Duration.between(tracker.getLastRun(), now);
//                } else {
//                    timeSinceLastRun = Duration.between(Instant.EPOCH, now);
//                }

                // Create a new tracker to save at the end of the process.
                tracker = new Tracker(now.toEpochMilli(), dataRetentionRules);

                // Create a map of unique periods with the set of rules that apply to them.
                final Map<TimePeriod, Set<DataRetentionRule>> rulesByPeriod = getRulesByPeriod(
                        activeRules, now, timeSinceLastRun);

                final AtomicBoolean allSuccessful = new AtomicBoolean(true);

                // Process the different data ages separately as they can consider different sets of streams.
                rulesByPeriod.keySet()
                        .stream()
                        .sorted(TimePeriod.comparingByFromTime())
                        .forEach(period -> {
                            final List<DataRetentionRule> reverseSortedRules = rulesByPeriod.get(period)
                                    .stream()
                                    .sorted(Comparator.comparing(DataRetentionRule::getRuleNumber).reversed())
                                    .collect(Collectors.toList());

                            // Skip if we have terminated processing.
                            if (!Thread.currentThread().isInterrupted()) {
                                processPeriod(taskContext, period, reverseSortedRules, batchSize, now);
                                if (!Thread.currentThread().isInterrupted()) {
                                    allSuccessful.set(false);
                                }
                            }
                        });

                // If we finished running then save the tracker for use next time.
                if (!Thread.currentThread().isInterrupted() && allSuccessful.get()) {
                    tracker.save();
                }
            } else {
                LOGGER.info("No active rules to process");
            }
        }
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
                               final List<DataRetentionRule> reverseSortedRules,
                               final int batchSize,
                               final Instant now) {
        info(taskContext, () -> {
            final Function<DataRetentionRule, String> ruleInfo = rule ->
                    rule.toString() + " " +
                            rule.getAge() + " " + rule.getTimeUnit().getDisplayValue() + " " +
                            rule.getExpression();

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
                    "], " + reverseSortedRules.size() + " rules:\n" +
                    reverseSortedRules.stream()
                            .map(ruleInfo)
                            .collect(Collectors.joining("\n"));
        });

        final List<ExpressionOperator> ruleExpressions = reverseSortedRules.stream()
                .filter(rule -> rule.getExpression() != null)
                .map(DataRetentionRule::getExpression)
                .collect(Collectors.toList());

        int count = -1;
        while (count != 0 && !Thread.currentThread().isInterrupted()) {
            count = metaService.delete(ruleExpressions, period, batchSize);
            final String message = "Marked " + count + " items as deleted";
            LOGGER.info(() -> message);
        }
    }

    private Map<TimePeriod, Set<DataRetentionRule>> getRulesByPeriod(
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
        final Map<TimePeriod, Set<DataRetentionRule>> rulesByPeriod = new HashMap<>();

        // We don't need to delete anything newer than this time as all rules have a minCreateTime
        // older than or equal to it.  Thus make this the end of our first deletion period
        Instant toTime = latestMinCreationTime;
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

            minCreationTimeMap.entrySet()
                    .stream()
                    .sorted(Map.Entry.comparingByKey(Comparator.reverseOrder()))
                    .forEach(entry -> {
                        final Instant minCreationTime = entry.getKey();
                        final Set<DataRetentionRule> ruleSet = entry.getValue();

                        final Set<DataRetentionRule> set = rulesByPeriod.computeIfAbsent(
                                period,
                                p -> new HashSet<>());

//                        LOGGER.debug(() -> Duration.between(minCreationTime, period.getFrom()).toString());
//                        boolean doRulesApplyToPeriod = minCreationTime.truncatedTo(ChronoUnit.MILLIS)
//                                .isBefore(period.getFrom().truncatedTo(ChronoUnit.MILLIS));
//                        boolean doRulesApplyToPeriod = minCreationTime
//                                .isBefore(period.getFrom());
                        boolean doRulesApplyToPeriod = minCreationTime.isAfter(period.getFrom());

                        LOGGER.debug(() -> LogUtil.message(
                                "  {}, minCreationTime: {} ({} ago), from: {} ({} ago), to {} ({} ago), ruleSet {}",
                                doRulesApplyToPeriod,
                                minCreationTime,
                                TimeUtils.instantAsAge(creationTime, now),
                                period.getFrom(),
                                TimeUtils.instantAsAge(period.getFrom(), now),
                                period.getTo(),
                                TimeUtils.instantAsAge(period.getTo(), now),
                                ruleSet.stream()
                                        .sorted(DataRetentionRule.comparingByDescendingRuleNumber())
                                        .collect(Collectors.toList())));

                        if (doRulesApplyToPeriod) {
                            set.addAll(ruleSet);
                        }
                    });

            toTime = creationTime;
        }
        return rulesByPeriod;
    }

    private Map<Instant, Set<DataRetentionRule>> getMinCreationTimeMap(
            final List<DataRetentionRule> activeRules,
            final Instant now) {

        // Use the rule retention period to work out the earliest meta createTime that
        // we could delete matching records from
        final Map<Instant, Set<DataRetentionRule>> minCreationTimeMap = activeRules.stream()
                .collect(Collectors.groupingBy(
                        rule -> DataRetentionCreationTimeUtil.minus(now, rule),
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
                                .sorted(Comparator.comparing(DataRetentionRule::getRuleNumber).reversed())
                                .collect(Collectors.toList()))
                .collect(Collectors.joining("\n")));

        return minCreationTimeMap;
    }

    private void info(final TaskContext taskContext, final Supplier<String> messageSupplier) {
        LOGGER.info(messageSupplier);
        taskContext.info(messageSupplier);
    }

    // TODO Consider removing as there seems little point in the tracker.  A hang over from
    //   a previous way of doing data retention

    @JsonPropertyOrder({"lastRun", "dataRetentionRules", "rulesVersion", "rulesHash"})
    @JsonInclude(Include.NON_NULL)
    static class Tracker {
        private static final String FILE_NAME = "dataRetentionTracker.json";

        @JsonProperty
        private Long lastRun;

        @JsonProperty
        private DataRetentionRules dataRetentionRules;
        @JsonProperty
        private String rulesVersion;
        @JsonProperty
        private int rulesHash;

        Tracker(final Long lastRun, final DataRetentionRules dataRetentionRules) {
            this.lastRun = lastRun;

            this.dataRetentionRules = dataRetentionRules;
            this.rulesVersion = dataRetentionRules.getVersion();
            this.rulesHash = dataRetentionRules.hashCode();
        }

        @JsonCreator
        Tracker(@JsonProperty("lastRun") final Long lastRun,
                @JsonProperty("dataRetentionRules") final DataRetentionRules dataRetentionRules,
                @JsonProperty("rulesVersion") final String rulesVersion,
                @JsonProperty("rulesHash") final int rulesHash) {
            this.lastRun = lastRun;
            this.dataRetentionRules = dataRetentionRules;
            this.rulesVersion = rulesVersion;
            this.rulesHash = rulesHash;
        }

        boolean rulesEquals(final DataRetentionRules dataRetentionRules) {
            return Objects.equals(rulesVersion, dataRetentionRules.getVersion())
                    && rulesHash == dataRetentionRules.hashCode()
                    && this.dataRetentionRules.equals(dataRetentionRules);
        }

        public DataRetentionRules getDataRetentionRules() {
            return dataRetentionRules;
        }

        @JsonIgnore
        public Optional<Instant> getLastRun() {
            return Optional.ofNullable(lastRun)
                    .map(Instant::ofEpochMilli);
        }

        static Tracker load() {
            try {
                final Path path = FileUtil.getTempDir().resolve(FILE_NAME);
                if (Files.isRegularFile(path)) {
                    try (final InputStream inputStream = new BufferedInputStream(Files.newInputStream(path))) {
                        return JsonUtil.getMapper().readValue(inputStream, Tracker.class);
                    }
                }
            } catch (final IOException e) {
                LOGGER.error(e::getMessage, e);
            }
            return null;
        }

        void save() {
            try {
                final Path path = FileUtil.getTempDir().resolve(FILE_NAME);
                try (final OutputStream outputStream = new BufferedOutputStream(Files.newOutputStream(path))) {
                    JsonUtil.getMapper().writeValue(outputStream, this);
                }
            } catch (final IOException e) {
                LOGGER.error(e::getMessage, e);
            }
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            final Tracker tracker = (Tracker) o;
            return rulesHash == tracker.rulesHash &&
                    Objects.equals(lastRun, tracker.lastRun) &&
                    Objects.equals(dataRetentionRules, tracker.dataRetentionRules) &&
                    Objects.equals(rulesVersion, tracker.rulesVersion);
        }

        @Override
        public int hashCode() {
            return Objects.hash(lastRun, dataRetentionRules, rulesVersion, rulesHash);
        }

    }
}
