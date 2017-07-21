/*
 * Copyright 2016 Crown Copyright
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

package stroom.policy.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import stroom.entity.shared.Period;
import stroom.jobsystem.server.ClusterLockService;
import stroom.jobsystem.server.JobTrackedSchedule;
import stroom.streamstore.server.DataRetentionAgeUtil;
import stroom.streamstore.server.fs.DataRetentionTransactionHelper;
import stroom.policy.shared.DataRetentionPolicy;
import stroom.policy.shared.DataRetentionRule;
import stroom.util.logging.LogExecutionTime;
import stroom.util.spring.StroomScope;
import stroom.util.spring.StroomSimpleCronSchedule;
import stroom.util.task.TaskMonitor;

import javax.inject.Inject;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
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

    private final TaskMonitor taskMonitor;
    private final ClusterLockService clusterLockService;
    private final DataRetentionService dataRetentionService;
    private final DataRetentionTransactionHelper dataRetentionTransactionHelper;
    private final AtomicBoolean running = new AtomicBoolean();

    private volatile DataRetentionPolicy lastPolicy;
    private volatile Long lastRun;

    @Inject
    DataRetentionExecutor(final TaskMonitor taskMonitor, final ClusterLockService clusterLockService, final DataRetentionService dataRetentionService, final DataRetentionTransactionHelper dataRetentionTransactionHelper) {
        this.taskMonitor = taskMonitor;
        this.clusterLockService = clusterLockService;
        this.dataRetentionService = dataRetentionService;
        this.dataRetentionTransactionHelper = dataRetentionTransactionHelper;
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
                // Calculate the data retention ages for all enabled rules.
                final LocalDateTime now = LocalDateTime.now();
                final Map<DataRetentionRule, Optional<Long>> ageMap = rules.stream()
                        .filter(DataRetentionRule::isEnabled)
                        .collect(Collectors.toMap(Function.identity(), rule -> getAge(now, rule)));

                // If the data retention policy has changed then we need to assume it has never been run before,
                // i.e. all data must be considered for retention checking.
                if (!dataRetentionPolicy.equals(lastPolicy)) {
                    this.lastPolicy = dataRetentionPolicy;
                    this.lastRun = null;
                }

                // Calculate how long it has been since we last ran this process if we have run it before.
                final long nowMs = now.toInstant(ZoneOffset.UTC).toEpochMilli();
                Long timeElapsed = null;
                if (this.lastRun != null) {
                    timeElapsed = nowMs - this.lastRun;
                }
                final Long timeElapsedSinceLastRun = timeElapsed;
                this.lastRun = nowMs;

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

                    info("Considering stream retention for period: " + ageRange);

                    dataRetentionTransactionHelper.deleteMatching(ageRange, rules, ageMap, taskMonitor);
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
}
