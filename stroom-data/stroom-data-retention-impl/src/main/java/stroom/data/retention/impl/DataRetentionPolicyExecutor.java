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
import stroom.datasource.api.v2.AbstractField;
import stroom.meta.shared.FindMetaCriteria;
import stroom.meta.shared.MetaFields;
import stroom.meta.shared.MetaService;
import stroom.meta.shared.Status;
import stroom.task.api.TaskContext;
import stroom.util.date.DateUtil;
import stroom.util.io.FileUtil;
import stroom.util.io.StreamUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogExecutionTime;
import stroom.util.shared.Period;
import stroom.util.xml.XMLMarshallerUtil;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;
import javax.xml.bind.annotation.XmlType;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class DataRetentionPolicyExecutor {
    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(DataRetentionPolicyExecutor.class);

    private static final String LOCK_NAME = "DataRetentionExecutor";

    private final TaskContext taskContext;
    private final ClusterLockService clusterLockService;
    private final Provider<DataRetentionRules> dataRetentionRulesProvider;
    private final DataRetentionConfig policyConfig;
    private final MetaService metaService;
    private final AtomicBoolean running = new AtomicBoolean();

    @Inject
    DataRetentionPolicyExecutor(final TaskContext taskContext,
                                final ClusterLockService clusterLockService,
                                final Provider<DataRetentionRules> dataRetentionRulesProvider,
                                final DataRetentionConfig policyConfig,
                                final MetaService metaService) {
        this.taskContext = taskContext;
        this.clusterLockService = clusterLockService;
        this.dataRetentionRulesProvider = dataRetentionRulesProvider;
        this.policyConfig = policyConfig;
        this.metaService = metaService;
    }

    public void exec() {
        if (running.compareAndSet(false, true)) {
            try {
                info(() -> "Starting data retention process");
                clusterLockService.tryLock(LOCK_NAME, () -> {
                    try {
                        final LogExecutionTime logExecutionTime = new LogExecutionTime();
                        process();
                        info(() -> "Finished data retention process in " + logExecutionTime);
                    } catch (final RuntimeException e) {
                        LOGGER.error(e::getMessage, e);
                    }
                });
            } finally {
                running.set(false);
            }
        }
    }

    private synchronized void process() {
        final DataRetentionRules dataRetentionRules = dataRetentionRulesProvider.get();
        if (dataRetentionRules != null) {
            final List<DataRetentionRule> rules = dataRetentionRules.getRules();
            final List<DataRetentionRule> activeRules = new ArrayList<>();

            // Also make sure we create a list of rules that are enabled and have at least one enabled term.
            if (rules != null) {
                rules.forEach(rule -> {
                    if (rule.isEnabled() && rule.getExpression() != null && rule.getExpression().isEnabled()) {
//                        final Set<String> fields = new HashSet<>();
//                        addToFieldSet(rule, fields);
//                        if (fields.size() > 0) {
                        activeRules.add(rule);
//                        }
                    }
                });
            }

            if (activeRules.size() > 0) {
                // Figure out what the batch size will be for deletion.
                final int batchSize = policyConfig.getDeleteBatchSize();

                // Calculate the data retention ages for all enabled rules.
                final long nowMs = System.currentTimeMillis();

                // Load the last tracker used.
                Tracker tracker = Tracker.load();

                // If the data retention policy has changed then we need to assume it has never been run before,
                // i.e. all data must be considered for retention checking.
                if (tracker == null || !tracker.rulesEquals(dataRetentionRules)) {
                    tracker = new Tracker(null, dataRetentionRules);
                }

                // Calculate the amount of time that has elapsed since we last ran.
                long elapsedTime = nowMs;
                if (tracker.lastRun != null) {
                    elapsedTime = nowMs - tracker.lastRun;
                }

                // Create a new tracker to save at the end of the process.
                tracker = new Tracker(nowMs, dataRetentionRules);

                // Create a map of unique periods with the set of rules that apply to them.
                final Map<Period, Set<DataRetentionRule>> rulesByPeriod = getRulesByPeriod(activeRules, nowMs, elapsedTime);

                final AtomicBoolean allSuccessful = new AtomicBoolean(true);

                // Process the different data ages separately as they can consider different sets of streams.
                rulesByPeriod.keySet().stream().sorted(Comparator.comparing(Period::getFromMs)).forEach(period -> {
                    final List<DataRetentionRule> sortedRules = rulesByPeriod.get(period)
                            .stream()
                            .sorted(Comparator.comparing(DataRetentionRule::getRuleNumber))
                            .collect(Collectors.toList());

                    // Skip if we have terminated processing.
                    if (!Thread.currentThread().isInterrupted()) {
                        processPeriod(period, sortedRules, batchSize);
                        if (!Thread.currentThread().isInterrupted()) {
                            allSuccessful.set(false);
                        }
                    }
                });

                // If we finished running then save the tracker for use next time.
                if (!Thread.currentThread().isInterrupted() && allSuccessful.get()) {
                    tracker.save();
                }
            }
        }
    }

    private void processPeriod(final Period period, final List<DataRetentionRule> rules, final int batchSize) {
        info(() -> "" +
                "Considering stream retention for streams created " +
                "between " +
                DateUtil.createNormalDateTimeString(period.getFromMs()) +
                " and " +
                DateUtil.createNormalDateTimeString(period.getToMs()));

        final FindMetaCriteria findMetaCriteria = DataRetentionMetaCriteriaUtil.createCriteria(period, rules, batchSize);

        int count = -1;
        while (count != 0 && !Thread.currentThread().isInterrupted()) {
            count = metaService.updateStatus(findMetaCriteria, Status.DELETED);
            final String message = "Marked " + count + " items as deleted";
            LOGGER.info(() -> message);
        }
    }

    private Map<Period, Set<DataRetentionRule>> getRulesByPeriod(final List<DataRetentionRule> activeRules, final long nowMs, final long elapsedTime) {
        // Get effective minimum meta creation times for each rule.
        final Map<Long, Set<DataRetentionRule>> minCreationTimeMap = getMinCreationTimeMap(activeRules, nowMs);

        // Create a map of unique periods with the set of rules that apply to them.
        final Map<Period, Set<DataRetentionRule>> rulesByPeriod = new HashMap<>();
        final List<Long> sortedCreationTimes = minCreationTimeMap.keySet()
                .stream()
                .sorted(Comparator.reverseOrder())
                .collect(Collectors.toList());

        long toMs = nowMs;
        for (final long creationTime : sortedCreationTimes) {
            // Calculate a from time for the period that takes account of the time passed since the last execution.
            final long fromMs = Math.max(creationTime, toMs - elapsedTime);

            final Period period = new Period(fromMs, toMs);
            minCreationTimeMap.forEach((k, v) -> {
                final Set<DataRetentionRule> set = rulesByPeriod.computeIfAbsent(period, p -> new HashSet<>());
                if (k < period.getToMs()) {
                    set.addAll(v);
                }
            });

            toMs = creationTime;
        }
        return rulesByPeriod;
    }

    private Map<Long, Set<DataRetentionRule>> getMinCreationTimeMap(final List<DataRetentionRule> activeRules, final long nowMs) {
        final Map<Long, Set<DataRetentionRule>> minCreationTimeMap = new HashMap<>();
        // Ensure we have a key for `forever`, so we know what rules, if any, we should use to retain data forever.
        minCreationTimeMap.computeIfAbsent(0L, ct -> new HashSet<>());
        // Add all other rules by minimum creation time.
        activeRules.forEach(rule -> {
            final long time = DataRetentionCreationTimeUtil.minus(nowMs, rule);
            minCreationTimeMap.computeIfAbsent(time, ct -> new HashSet<>()).add(rule);
        });
        return minCreationTimeMap;
    }

//    private void addToFieldSet(final DataRetentionRule rule, final Set<String> fieldSet) {
//        if (rule.isEnabled() && rule.getExpression() != null) {
//            addChildren(rule.getExpression(), fieldSet);
//        }
//    }
//
//    private void addChildren(final ExpressionItem item, final Set<String> fieldSet) {
//        if (item.getEnabled()) {
//            if (item instanceof ExpressionOperator) {
//                final ExpressionOperator operator = (ExpressionOperator) item;
//                operator.getChildren().forEach(i -> addChildren(i, fieldSet));
//            } else if (item instanceof ExpressionTerm) {
//                final ExpressionTerm term = (ExpressionTerm) item;
//                fieldSet.add(term.getField());
//            }
//        }
//    }

    private void info(final Supplier<String> messageSupplier) {
        LOGGER.info(messageSupplier);
        taskContext.info(messageSupplier);
    }

    @XmlAccessorType(XmlAccessType.FIELD)
    @XmlType(name = "Tracker", propOrder = {"lastRun", "dataRetentionRules", "rulesVersion", "rulesHash"})
    @XmlRootElement(name = "tracker")
    static class Tracker {
        private static final String FILE_NAME = "dataRetentionTracker.json";

        @XmlElement(name = "lastRun")
        private Long lastRun;

        @XmlElement(name = "dataRetentionRules")
        private DataRetentionRules dataRetentionRules;
        @XmlElement(name = "rulesVersion")
        private String rulesVersion;
        @XmlElement(name = "rulesHash")
        private int rulesHash;

        @XmlTransient
        private static JAXBContext jaxbContext;

        Tracker() {
        }

        Tracker(final Long lastRun, final DataRetentionRules dataRetentionRules) {
            this.lastRun = lastRun;

            this.dataRetentionRules = dataRetentionRules;
            this.rulesVersion = dataRetentionRules.getVersion();
            this.rulesHash = dataRetentionRules.hashCode();
        }

        boolean rulesEquals(final DataRetentionRules dataRetentionRules) {
            return Objects.equals(rulesVersion, dataRetentionRules.getVersion()) && rulesHash == dataRetentionRules.hashCode() && this.dataRetentionRules.equals(dataRetentionRules);
        }

        public DataRetentionRules getDataRetentionRules() {
            return dataRetentionRules;
        }

        static Tracker load() {
            try {
                final Path path = FileUtil.getTempDir().resolve(FILE_NAME);
                if (Files.isRegularFile(path)) {
                    final String data = Files.readString(path, StreamUtil.DEFAULT_CHARSET);
                    return XMLMarshallerUtil.unmarshal(getContext(), Tracker.class, data);
                }
            } catch (final IOException e) {
                LOGGER.error(e::getMessage, e);
            }
            return null;
        }

        void save() {
            try {
                final String data = XMLMarshallerUtil.marshal(getContext(), this);
                final Path path = FileUtil.getTempDir().resolve(FILE_NAME);
                Files.writeString(path, data, StreamUtil.DEFAULT_CHARSET);
            } catch (final IOException e) {
                LOGGER.error(e::getMessage, e);
            }
        }

        private static JAXBContext getContext() {
            if (jaxbContext == null) {
                try {
                    jaxbContext = JAXBContext.newInstance(Tracker.class);
                } catch (final JAXBException e) {
                    LOGGER.error(e::getMessage, e);
                    throw new RuntimeException(e.getMessage());
                }
            }

            return jaxbContext;
        }
    }

    public static class ActiveRules {
        private final Set<AbstractField> fieldSet;
        private final List<DataRetentionRule> activeRules;

        ActiveRules(final List<DataRetentionRule> rules) {
            final Set<AbstractField> fieldSet = new HashSet<>();
            final List<DataRetentionRule> activeRules = new ArrayList<>();

            // Find out which fields are used by the expressions so we don't have to do unnecessary joins.
            fieldSet.add(MetaFields.ID);
            fieldSet.add(MetaFields.CREATE_TIME);

            // Also make sure we create a list of rules that are enabled and have at least one enabled term.
            rules.forEach(rule -> {
                if (rule.isEnabled() && rule.getExpression() != null && rule.getExpression().isEnabled()) {
                    final Set<AbstractField> fields = new HashSet<>();
//                    addToFieldSet(rule, fields);
                    if (fields.size() > 0) {
                        fieldSet.addAll(fields);
                        activeRules.add(rule);
                    }
                }
            });

            this.fieldSet = Collections.unmodifiableSet(fieldSet);
            this.activeRules = Collections.unmodifiableList(activeRules);
        }

//        private void addToFieldSet(final DataRetentionRule rule, final Set<String> fieldSet) {
//            if (rule.isEnabled() && rule.getExpression() != null) {
//                addChildren(rule.getExpression(), fieldSet);
//            }
//        }
//
//        private void addChildren(final ExpressionItem item, final Set<String> fieldSet) {
//            if (item.getEnabled()) {
//                if (item instanceof ExpressionOperator) {
//                    final ExpressionOperator operator = (ExpressionOperator) item;
//                    operator.getChildren().forEach(i -> addChildren(i, fieldSet));
//                } else if (item instanceof ExpressionTerm) {
//                    final ExpressionTerm term = (ExpressionTerm) item;
//                    fieldSet.add(term.getField());
//                }
//            }
//        }

        public List<DataRetentionRule> getActiveRules() {
            return activeRules;
        }
//
//        public Set<String> getFieldSet() {
//            return fieldSet;
//        }
    }

    static class Progress {
        private final Period ageRange;
        private long rowCount;
        private long rowNum;
        private Long streamId;
        private Long createMs;

        Progress(final Period ageRange, final long rowCount) {
            this.ageRange = ageRange;
            this.rowCount = rowCount;
        }

        public void nextStream(final Long streamId, final Long createMs) {
            this.streamId = streamId;
            this.createMs = createMs;
            rowNum++;
            rowCount = Math.max(rowCount, rowNum);
        }

        public Long getStreamId() {
            return streamId;
        }

        private String getPeriodString() {
            if (ageRange.getFromMs() != null && ageRange.getToMs() != null) {
                return "age between " + DateUtil.createNormalDateTimeString(ageRange.getFromMs()) + " and " + DateUtil.createNormalDateTimeString(ageRange.getToMs());
            }
            if (ageRange.getFromMs() != null) {
                return "age after " + DateUtil.createNormalDateTimeString(ageRange.getFromMs());
            }
            if (ageRange.getToMs() != null) {
                return "age before " + DateUtil.createNormalDateTimeString(ageRange.getToMs());
            }
            return "";
        }

        private String getCounts() {
            return " (" +
                    rowNum +
                    " of " +
                    rowCount +
                    ")";
        }

        // Time based completion
//        private String getPercentComplete() {
//            if (createMs != null && ageRange.getFromMs() != null && ageRange.getToMs() != null) {
//                long diff = ageRange.getToMs() - ageRange.getFromMs();
//                long pos = createMs - ageRange.getFromMs();
//                int pct = (int) ((100D / diff) * pos);
//                return ", " + pct + "% complete";
//            }
//            return "";
//        }

        private String getPercentComplete() {
            final int pct = (int) ((100D / rowCount) * rowNum);
            return ", " + pct + "% complete";
        }

//        private String getStreamInfo() {
//            if (streamId != null && createMs != null) {
//                return ", current stream id=" +
//                        streamId +
//                        ", create time=" +
//                        DateUtil.createNormalDateTimeString(createMs);
//            }
//
//            return "";
//        }

        private String getStreamInfo() {
            if (streamId != null && createMs != null) {
                return ", current stream id=" +
                        streamId;
            }

            return "";
        }

        public String toString() {
            return getPeriodString() +
                    getCounts() +
                    getPercentComplete() +
                    getStreamInfo();
        }
    }
}
