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
 *
 */

package stroom.policy.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import stroom.entity.shared.Period;
import stroom.entity.shared.Range;
import stroom.jobsystem.server.ClusterLockService;
import stroom.jobsystem.server.JobTrackedSchedule;
import stroom.node.server.StroomPropertyService;
import stroom.policy.shared.DataRetentionPolicy;
import stroom.policy.shared.DataRetentionRule;
import stroom.query.shared.ExpressionItem;
import stroom.query.shared.ExpressionOperator;
import stroom.query.shared.ExpressionTerm;
import stroom.streamstore.server.DataRetentionAgeUtil;
import stroom.streamstore.server.StreamFields;
import stroom.util.date.DateUtil;
import stroom.util.logging.LogExecutionTime;
import stroom.util.spring.StroomScope;
import stroom.util.spring.StroomSimpleCronSchedule;
import stroom.util.task.TaskMonitor;

import javax.inject.Inject;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Factory for creating stream clean tasks.
 */
@Component
@Scope(value = StroomScope.TASK)
public class DataRetentionExecutor {
    private static final Logger LOGGER = LoggerFactory.getLogger(DataRetentionExecutor.class);

    private static final String LOCK_NAME = "DataRetentionExecutor";
    private static final String STREAM_DELETE_BATCH_SIZE_PROPERTY = "stroom.stream.deleteBatchSize";

    private final TaskMonitor taskMonitor;
    private final ClusterLockService clusterLockService;
    private final DataRetentionService dataRetentionService;
    private final DataRetentionTransactionHelper dataRetentionTransactionHelper;
    private final StroomPropertyService propertyService;
    private final AtomicBoolean running = new AtomicBoolean();

    private volatile Tracker tracker;

    @Inject
    DataRetentionExecutor(final TaskMonitor taskMonitor, final ClusterLockService clusterLockService, final DataRetentionService dataRetentionService, final DataRetentionTransactionHelper dataRetentionTransactionHelper, final StroomPropertyService propertyService) {
        this.taskMonitor = taskMonitor;
        this.clusterLockService = clusterLockService;
        this.dataRetentionService = dataRetentionService;
        this.dataRetentionTransactionHelper = dataRetentionTransactionHelper;
        this.propertyService = propertyService;
    }

    @StroomSimpleCronSchedule(cron = "0 0 *")
    @JobTrackedSchedule(jobName = "Data Retention", description = "Job to delete data that has past it's retention period")
    public void exec() {
        if (running.compareAndSet(false, true)) {
            try {
                final LogExecutionTime logExecutionTime = new LogExecutionTime();
                info("Starting data retention process");
                if (clusterLockService.tryLock(LOCK_NAME)) {
                    try {
                        process();
                        LOGGER.info("Finished data retention process in {}", logExecutionTime);
                    } catch (final Throwable t) {
                        LOGGER.error(t.getMessage(), t);
                    } finally {
                        clusterLockService.releaseLock(LOCK_NAME);
                    }
                } else {
                    LOGGER.info("Stream Retention Executor - Skipped as did not get lock in {}", logExecutionTime);
                }
            } finally {
                running.set(false);
            }
        }
    }

    private synchronized void process() {
        final DataRetentionPolicy dataRetentionPolicy = dataRetentionService.load();
        if (dataRetentionPolicy != null) {
            final List<DataRetentionRule> rules = dataRetentionPolicy.getRules();
            if (rules != null && rules.size() > 0) {
                // Figure out what the batch size will be for deletion.
                final long batchSize = propertyService.getLongProperty(STREAM_DELETE_BATCH_SIZE_PROPERTY, 1000);

                // Calculate the data retention ages for all enabled rules.
                final LocalDateTime now = LocalDateTime.now();
                final Map<DataRetentionRule, Optional<Long>> ageMap = rules.stream()
                        .filter(DataRetentionRule::isEnabled)
                        .collect(Collectors.toMap(Function.identity(), rule -> getAge(now, rule)));

                // If the data retention policy has changed then we need to assume it has never been run before,
                // i.e. all data must be considered for retention checking.
                if (tracker == null || !tracker.policyEquals(dataRetentionPolicy)) {
                    tracker = new Tracker(null, dataRetentionPolicy);
                }

                // Calculate how long it has been since we last ran this process if we have run it before.
                final long nowMs = now.toInstant(ZoneOffset.UTC).toEpochMilli();
                Long timeElapsed = null;
                if (tracker.lastRun != null) {
                    timeElapsed = nowMs - tracker.lastRun;
                }
                final Long timeElapsedSinceLastRun = timeElapsed;
                tracker = new Tracker(nowMs, dataRetentionPolicy);

                // Now figure out what unique ages we have.
                final Set<Long> ages = ageMap.values().stream()
                        .filter(Optional::isPresent)
                        .map(Optional::get)
                        .collect(Collectors.toSet());

                // Process the different data ages separately as they can consider different sets of streams.
                ages.forEach(age -> {
                    Long minAge = null;
                    Long maxAge = age;

                    if (timeElapsedSinceLastRun != null) {
                        minAge = maxAge - timeElapsedSinceLastRun;
                    }

                    final Period ageRange = new Period(minAge, maxAge);

                    final StringBuilder message = new StringBuilder();
                    message.append("Considering stream retention for streams created ");
                    if (minAge != null) {
                        message.append("between ");
                        message.append(DateUtil.createNormalDateTimeString(minAge));
                        message.append(" and ");
                        message.append(DateUtil.createNormalDateTimeString(maxAge));
                    } else {
                        message.append("before ");
                        message.append(DateUtil.createNormalDateTimeString(maxAge));
                    }
                    info(message.toString());

                    // Figure out which rules are active and get all used fields.
                    final ActiveRules activeRules = new ActiveRules(rules);

                    // Ignore rules if none are active.
                    if (activeRules.getActiveRules().size() > 0) {
                        // Find out how many rows we are likely to examine.
                        final long rowCount = dataRetentionTransactionHelper.getRowCount(ageRange, activeRules.getFieldSet());

                        // If we aren't likely to be touching any rows then ignore.
                        if (rowCount > 0) {
                            boolean more = true;
                            final Progress progress = new Progress(rowCount);
                            while (more) {
                                Range<Long> streamIdRange = null;
                                if (progress.getStreamId() != null) {
                                    // Process from the next stream id onwards.
                                    streamIdRange = new Range<>(progress.getStreamId() + 1, null);
                                }

                                more = dataRetentionTransactionHelper.deleteMatching(ageRange, streamIdRange, batchSize, activeRules, ageMap, taskMonitor, progress);
                            }
                        }
                    }
                });
            }
        }
    }

    private Optional<Long> getAge(final LocalDateTime now, final DataRetentionRule rule) {
        return Optional.ofNullable(DataRetentionAgeUtil.minus(now, rule));
    }

    private void info(final String info) {
        LOGGER.info("Starting data retention process");
        taskMonitor.info(info);
    }

    private static class Tracker {
        private final Long lastRun;

        private final DataRetentionPolicy dataRetentionPolicy;
        private final int policyVersion;
        private final int policyHash;

        Tracker(final Long lastRun, final DataRetentionPolicy dataRetentionPolicy) {
            this.lastRun = lastRun;

            this.dataRetentionPolicy = dataRetentionPolicy;
            this.policyVersion = dataRetentionPolicy.getVersion();
            this.policyHash = dataRetentionPolicy.hashCode();
        }

        boolean policyEquals(final DataRetentionPolicy dataRetentionPolicy) {
            return policyVersion == dataRetentionPolicy.getVersion() && policyHash == dataRetentionPolicy.hashCode() && this.dataRetentionPolicy.equals(dataRetentionPolicy);
        }
    }

    public static class ActiveRules {
        private final Set<String> fieldSet;
        private final List<DataRetentionRule> activeRules;

        ActiveRules(final List<DataRetentionRule> rules) {
            final Set<String> fieldSet = new HashSet<>();
            final List<DataRetentionRule> activeRules = new ArrayList<>();

            // Find out which fields are used by the expressions so we don't have to do unnecessary joins.
            fieldSet.add(StreamFields.STREAM_ID);
            fieldSet.add(StreamFields.CREATED_ON);

            // Also make sure we create a list of rules that are enabled and have at least one enabled term.
            rules.forEach(rule -> {
                if (rule.isEnabled() && rule.getExpression() != null && rule.getExpression().enabled()) {
                    final Set<String> fields = new HashSet<>();
                    addToFieldSet(rule, fields);
                    if (fields.size() > 0) {
                        fieldSet.addAll(fields);
                        activeRules.add(rule);
                    }
                }
            });

            this.fieldSet = Collections.unmodifiableSet(fieldSet);
            this.activeRules = Collections.unmodifiableList(activeRules);
        }

        private void addToFieldSet(final DataRetentionRule rule, final Set<String> fieldSet) {
            if (rule.isEnabled() && rule.getExpression() != null) {
                addChildren(rule.getExpression(), fieldSet);
            }
        }

        private void addChildren(final ExpressionItem item, final Set<String> fieldSet) {
            if (item.enabled()) {
                if (item instanceof ExpressionOperator) {
                    final ExpressionOperator operator = (ExpressionOperator) item;
                    operator.getChildren().forEach(i -> addChildren(i, fieldSet));
                } else if (item instanceof ExpressionTerm) {
                    final ExpressionTerm term = (ExpressionTerm) item;
                    fieldSet.add(term.getField());
                }
            }
        }

        public List<DataRetentionRule> getActiveRules() {
            return activeRules;
        }

        public Set<String> getFieldSet() {
            return fieldSet;
        }
    }

    static class Progress {
        private long rowNum;
        private long rowCount;
        private Long streamId;

        Progress(final long rowCount) {
            this.rowCount = rowCount;
        }

        public void nextStream(final long streamId) {
            this.streamId = streamId;
            rowNum++;
            rowCount = Math.max(rowCount, rowNum);
        }

        public Long getStreamId() {
            return streamId;
        }

        public String toString() {
            return "stream " + rowNum + " of " + rowCount + " (stream id=" + streamId + ")";
        }
    }
}
