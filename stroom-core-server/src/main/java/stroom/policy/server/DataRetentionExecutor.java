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
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.stereotype.Component;
import stroom.dictionary.shared.DictionaryService;
import stroom.entity.server.util.XMLMarshallerUtil;
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
import stroom.util.io.FileUtil;
import stroom.util.io.StreamUtil;
import stroom.util.logging.LogExecutionTime;
import stroom.util.spring.StroomScope;
import stroom.util.spring.StroomSimpleCronSchedule;
import stroom.util.task.TaskMonitor;

import javax.inject.Inject;
import javax.sql.DataSource;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;
import javax.xml.bind.annotation.XmlType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
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
    private final StroomPropertyService propertyService;
    private final DictionaryService dictionaryService;
    private final DataSource dataSource;
    private final AtomicBoolean running = new AtomicBoolean();

    @Inject
    DataRetentionExecutor(final TaskMonitor taskMonitor,
                          final ClusterLockService clusterLockService,
                          final DataRetentionService dataRetentionService,
                          final StroomPropertyService propertyService,
                          final DictionaryService dictionaryService,
                          final DataSource dataSource) {
        this.taskMonitor = taskMonitor;
        this.clusterLockService = clusterLockService;
        this.dataRetentionService = dataRetentionService;
        this.propertyService = propertyService;
        this.dictionaryService = dictionaryService;
        this.dataSource = dataSource;
    }

    @StroomSimpleCronSchedule(cron = "0 0 *")
    @JobTrackedSchedule(jobName = "Data Retention", description = "Delete data that exceeds the retention period specified by data retention policy")
    public void exec() {
        if (running.compareAndSet(false, true)) {
            try {
                final LogExecutionTime logExecutionTime = new LogExecutionTime();
                info("Starting data retention process");
                if (clusterLockService.tryLock(LOCK_NAME)) {
                    try {
                        process();
                        info("Finished data retention process in " + logExecutionTime);
                    } catch (final Throwable t) {
                        LOGGER.error(t.getMessage(), t);
                    } finally {
                        clusterLockService.releaseLock(LOCK_NAME);
                    }
                } else {
                    info("Stream Retention Executor - Skipped as did not get lock in " + logExecutionTime);
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
                final int batchSize = propertyService.getIntProperty(STREAM_DELETE_BATCH_SIZE_PROPERTY, 1000);

                // Calculate the data retention ages for all enabled rules.
                final LocalDateTime now = LocalDateTime.now();
                final Map<DataRetentionRule, Optional<Long>> ageMap = rules.stream()
                        .filter(DataRetentionRule::isEnabled)
                        .collect(Collectors.toMap(Function.identity(), rule -> getAge(now, rule)));

                // Load the last tracker used.
                Tracker tracker = Tracker.load();

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

                // Get a database connection.
                final Connection connection = DataSourceUtils.getConnection(dataSource);
                try {
                    final AtomicBoolean allSuccesful = new AtomicBoolean(true);

                    // Process the different data ages separately as they can consider different sets of streams.
                    ages.forEach(age -> {
                        // Skip if we have terminated processing.
                        if (!taskMonitor.isTerminated()) {
                            final boolean success = processAge(connection, age, timeElapsedSinceLastRun, rules, batchSize, ageMap);
                            if (!success) {
                                allSuccesful.set(false);
                            }
                        }
                    });

                    // If we finished running then save the tracker for use next time.
                    if (!taskMonitor.isTerminated() && allSuccesful.get()) {
                        tracker.save();
                    }
                } finally {
                    // Release the database connection.
                    DataSourceUtils.releaseConnection(connection, dataSource);
                }
            }
        }
    }

    private boolean processAge(final Connection connection, final long age, final Long timeElapsedSinceLastRun, final List<DataRetentionRule> rules, final int batchSize, final Map<DataRetentionRule, Optional<Long>> ageMap) {
        boolean success = true;

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
            // Create an object that can find streams with prepared statements and see if they match rules.
            try (final DataRetentionStreamFinder finder = new DataRetentionStreamFinder(connection, dictionaryService)) {

                // Find out how many rows we are likely to examine.
                final long rowCount = finder.getRowCount(ageRange, activeRules.getFieldSet());

                // If we aren't likely to be touching any rows then ignore.
                if (rowCount > 0) {

                    // Create an object that can delete streams with prepared statements.
                    try (final DataRetentionStreamDeleter deleter = new DataRetentionStreamDeleter(connection)) {
                        List<Long> streamIdDeleteList = new ArrayList<>();

                        boolean more = true;
                        final Progress progress = new Progress(ageRange, rowCount);
                        Range<Long> streamIdRange = new Range<>(0L, null);
                        while (more && !taskMonitor.isTerminated()) {
                            if (progress.getStreamId() != null) {
                                // Process from the next stream id onwards.
                                streamIdRange = new Range<>(progress.getStreamId() + 1, null);
                            }

                            more = finder.findMatches(ageRange, streamIdRange, batchSize, activeRules, ageMap, taskMonitor, progress, streamIdDeleteList);

                            // Delete a batch of streams.
                            while (streamIdDeleteList.size() > batchSize) {
                                final List<Long> batch = streamIdDeleteList.subList(0, batchSize - 1);
                                streamIdDeleteList = streamIdDeleteList.subList(batchSize, streamIdDeleteList.size() - 1);
                                deleter.deleteStreams(batch);
                            }
                        }

                        // Delete any remaining streams in the list.
                        if (streamIdDeleteList.size() > 0) {
                            deleter.deleteStreams(streamIdDeleteList);
                        }

                    } catch (final SQLException e) {
                        success = false;
                        LOGGER.error(e.getMessage(), e);
                    }
                }
            } catch (final SQLException e) {
                success = false;
                LOGGER.error(e.getMessage(), e);
            }
        }

        return success;
    }

    private Optional<Long> getAge(final LocalDateTime now, final DataRetentionRule rule) {
        return Optional.ofNullable(DataRetentionAgeUtil.minus(now, rule));
    }

    private void info(final String info) {
        LOGGER.info(info);
        taskMonitor.info(info);
    }

    @XmlAccessorType(XmlAccessType.FIELD)
    @XmlType(name = "Tracker", propOrder = {"lastRun", "dataRetentionPolicy", "policyVersion", "policyHash"})
    @XmlRootElement(name = "tracker")
    static class Tracker {
        private static final String FILE_NAME = "dataRetentionTracker.json";

        @XmlElement(name = "lastRun")
        private Long lastRun;

        @XmlElement(name = "dataRetentionPolicy")
        private DataRetentionPolicy dataRetentionPolicy;
        @XmlElement(name = "policyVersion")
        private int policyVersion;
        @XmlElement(name = "policyHash")
        private int policyHash;

        @XmlTransient
        private static JAXBContext jaxbContext;

        Tracker() {
        }

        Tracker(final Long lastRun, final DataRetentionPolicy dataRetentionPolicy) {
            this.lastRun = lastRun;

            this.dataRetentionPolicy = dataRetentionPolicy;
            this.policyVersion = dataRetentionPolicy.getVersion();
            this.policyHash = dataRetentionPolicy.hashCode();
        }

        boolean policyEquals(final DataRetentionPolicy dataRetentionPolicy) {
            return policyVersion == dataRetentionPolicy.getVersion() && policyHash == dataRetentionPolicy.hashCode() && this.dataRetentionPolicy.equals(dataRetentionPolicy);
        }

        public DataRetentionPolicy getDataRetentionPolicy() {
            return dataRetentionPolicy;
        }

        static Tracker load() {
            try {
                final Path path = FileUtil.getTempDir().toPath().resolve(FILE_NAME);
                if (Files.isRegularFile(path)) {
                    final String data = new String(Files.readAllBytes(path), StreamUtil.DEFAULT_CHARSET);
                    return XMLMarshallerUtil.unmarshal(getContext(), Tracker.class, data);
                }
            } catch (final Exception e) {
                LOGGER.error(e.getMessage(), e);
            }
            return null;
        }

        void save() {
            try {
                final String data = XMLMarshallerUtil.marshal(getContext(), this);
                final Path path = FileUtil.getTempDir().toPath().resolve(FILE_NAME);
                Files.write(path, data.getBytes(StreamUtil.DEFAULT_CHARSET));
            } catch (final Exception e) {
                LOGGER.error(e.getMessage(), e);
            }
        }

        private static JAXBContext getContext() {
            if (jaxbContext == null) {
                try {
                    jaxbContext = JAXBContext.newInstance(Tracker.class);
                } catch (final JAXBException e) {
                    LOGGER.error(e.getMessage(), e);
                    throw new RuntimeException(e.getMessage());
                }
            }

            return jaxbContext;
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
