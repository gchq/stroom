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

package stroom.meta.impl.db;

import stroom.cluster.lock.api.ClusterLockService;
import stroom.db.util.JooqUtil;
import stroom.meta.api.AttributeMap;
import stroom.meta.impl.MetaKeyDao;
import stroom.meta.impl.MetaValueConfig;
import stroom.meta.impl.MetaValueDao;
import stroom.meta.shared.Meta;
import stroom.task.api.TaskContext;
import stroom.task.api.TaskContextFactory;
import stroom.util.concurrent.BatchingQueue.ResizableBatchingQueue;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogExecutionTime;
import stroom.util.logging.LogUtil;
import stroom.util.shared.Clearable;
import stroom.util.thread.CustomThreadFactory;

import com.lmax.disruptor.BlockingWaitStrategy;
import org.jooq.BatchBindStep;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.LongAdder;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;

import static stroom.meta.impl.db.jooq.tables.MetaVal.META_VAL;

@Singleton
class MetaValueDaoImpl implements MetaValueDao, Clearable {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(MetaValueDaoImpl.class);

    static final int FLUSH_FREQUENCY_SECS = 10;
    static final Duration FLUSH_FREQUENCY = Duration.ofSeconds(FLUSH_FREQUENCY_SECS);

    private static final String LOCK_NAME = "MetaDeleteExecutor";
    private static final String FLUSH_TASK_NAME = "Meta Value Flush";

    private final MetaDbConnProvider metaDbConnProvider;
    private final MetaKeyDao metaKeyService;
    private final Provider<MetaValueConfig> metaValueConfigProvider;
    private final ClusterLockService clusterLockService;
    private final TaskContext taskContext;
    private final TaskContextFactory taskContextFactory;
    private final ResizableBatchingQueue<Row> batchingQueue;

    private volatile List<Row> queue = new ArrayList<>();
    private volatile Instant queueExpiryTime = Instant.MAX;

    @Inject
    MetaValueDaoImpl(final MetaDbConnProvider metaDbConnProvider,
                     final MetaKeyDao metaKeyService,
                     final Provider<MetaValueConfig> metaValueConfigProvider,
                     final ClusterLockService clusterLockService,
                     final TaskContext taskContext,
                     final TaskContextFactory taskContextFactory) {
        this.metaDbConnProvider = metaDbConnProvider;
        this.metaKeyService = metaKeyService;
        this.metaValueConfigProvider = metaValueConfigProvider;
        this.clusterLockService = clusterLockService;
        this.taskContext = taskContext;
        this.taskContextFactory = taskContextFactory;

        // TODO: 20/02/2023 Get values from config
        this.batchingQueue = new ResizableBatchingQueue<>(
                new CustomThreadFactory("MetaDaoBatching"),
                new BlockingWaitStrategy(),
                32_768,
                10,
                metaValueConfigProvider.get().getFlushBatchSize(),
                Duration.ofSeconds(10),
                this::insertRecords);
    }

    @Override
    public void addAttributes(final Meta meta, final AttributeMap attributes) {
        final Stream<Row> stream = attributes.entrySet()
                .stream()
                .map(entry ->
                        metaKeyService.getIdForName(entry.getKey())
                                .flatMap(keyId -> {
                                    try {
                                        final Long longValue = Long.valueOf(entry.getValue());

                                        final Row row = new Row(meta.getCreateMs(), meta.getId(), keyId, longValue);

                                        return Optional.of(row);
                                    } catch (final NumberFormatException e) {
                                        LOGGER.debug(() ->
                                                LogUtil.message(
                                                        "Ignoring meta attribute value with key: {}, " +
                                                                "value: {} as value can't be converted to a number. {}",
                                                        entry.getKey(), entry.getValue(), e.getMessage()));
                                        // Silently ignore entries with non-numeric values
                                        return Optional.empty();
                                    }
                                }))
                .filter(Optional::isPresent)
                .map(Optional::get);

        final List<Row> records = stream.collect(Collectors.toList());
        if (records.isEmpty()) {
            LOGGER.debug("records is empty");
        } else {
            if (metaValueConfigProvider.get().isAddAsync()) {
                batchingQueue.putAll(records);
            } else {
                insertRecords(records, records.size());
            }
        }
    }

//    private void add(final List<Row> records) {
//        batchingQueue.putAll(r);
//        List<Row> readyForFlush = null;
//        // Only update the time when we actuall
//        if (queue.isEmpty() && NullSafe.hasItems(records)) {
//            queueExpiryTime = Instant.now().plus(FLUSH_FREQUENCY);
//        }
//        queue.addAll(records);
//        if (queue.size() >= batchSize) {
//            // Switch out the current queue.
//            readyForFlush = queue;
//            queue = new ArrayList<>();
//        }
//        // Return the old queue for flushing if it was switched.
//        return Optional.ofNullable(readyForFlush);
//    }
//
//    private void add2(final List<Row> records, final int batchSize) {
//
//
//    }

    @Override
    public void flush() {

        if (metaValueConfigProvider.get().isAddAsync()) {
            // TODO: 20/02/2023 Might need to make this blocking
            batchingQueue.flush();
        }
    }

//
//    public void flush(final boolean isForcedFlush) {
//        if (isForcedFlush || Instant.now().isAfter(queueExpiryTime)) {
//            flush();
//        } else {
//            LOGGER.debug("Skipping flush, queueExpiryTime: {}", queueExpiryTime);
//        }
//    }

    private void insertRecords(final Iterable<Row> rows, final int batchSize) {

        taskContextFactory.context(FLUSH_TASK_NAME, taskContext2 -> {
            final LogExecutionTime logExecutionTime = new LogExecutionTime();
            taskContext.info(() -> "Flushing meta values to the DB, batch size: " + batchSize);
            LOGGER.debug(() -> "Inserting meta_val batch of " + batchSize);

            JooqUtil.context(metaDbConnProvider, context -> {
                BatchBindStep batchBindStep = context
                        .batch(context
                                .insertInto(META_VAL,
                                        META_VAL.CREATE_TIME,
                                        META_VAL.META_ID,
                                        META_VAL.META_KEY_ID,
                                        META_VAL.VAL)
                                .values(null, null, null, (Long) null));
                for (final Row row : rows) {
                    batchBindStep = batchBindStep.bind(
                            row.getCreateMs(), row.getMetaId(), row.getKeyId(), row.getValue());
                }
                batchBindStep.execute();
            });

            LOGGER.debug(() -> "Inserted " + batchSize + " meta_val rows, completed in " + logExecutionTime);
        }).run();
    }

    @Override
    public void deleteOldValues() {
        // Acquire a cluster lock before performing a batch delete to reduce db contention and to let a
        // single node do the job.
        clusterLockService.tryLock(LOCK_NAME, () -> {
            taskContext.info(() -> "Deleting old meta values");
            final long createTimeThresholdEpochMs = getAttributeCreateTimeThresholdEpochMs();
            final int batchSize = metaValueConfigProvider.get().getDeleteBatchSize();
            LOGGER.debug(() ->
                    "Processing batch age " + createTimeThresholdEpochMs + ", batch size is " + batchSize);
            final LongAdder totalCount = new LongAdder();
            int count = batchSize;
            while (count >= batchSize) {
                if (Thread.currentThread().isInterrupted()) {
                    LOGGER.warn("Aborting meta value deletion due to thread interruption. " +
                            "Deletion will continue as normal on the next run. Deleted so far: " +
                            totalCount + ".");
                    break;
                }
                count = deleteBatchOfOldValues(createTimeThresholdEpochMs, batchSize, totalCount);
            }
        });
    }

    private int deleteBatchOfOldValues(final long createTimeThresholdEpochMs,
                                       final int batchSize,
                                       final LongAdder totalCount) {
        final LogExecutionTime logExecutionTime = new LogExecutionTime();

        // We don't care what order we delete in, just that we eventually delete everything
        // prior to our threshold time.
        final int count = JooqUtil.contextResult(metaDbConnProvider, context -> context
                .delete(META_VAL)
                .where(META_VAL.CREATE_TIME.lessThan(createTimeThresholdEpochMs))
                .limit(batchSize)
                .execute());

        totalCount.add(count);

        LOGGER.debug(() -> "Deleted " + count
                + ", total so far " + totalCount
                + ", completed in " + logExecutionTime);

        taskContext.info(() ->
                LogUtil.message("Deleting old meta values ({} deleted so far, batch size {})", totalCount, batchSize));
        return count;
    }

    /**
     * @return The oldest data attribute that we should keep
     */
    private long getAttributeCreateTimeThresholdEpochMs() {
        final Duration deleteAge = metaValueConfigProvider.get()
                .getDeleteAge()
                .getDuration();
        return System.currentTimeMillis() - deleteAge.toMillis();
    }

    /**
     * Convert a basic data list to a list of meta data using data attribute keys and values.
     */
    @Override
    public Map<Long, Map<String, String>> getAttributes(final List<Meta> list) {
        final Map<Long, Map<String, String>> attributeMap = new HashMap<>();

        // Get a list of valid data ids.
        final List<Long> idList = list.parallelStream()
                .map(Meta::getId)
                .collect(Collectors.toList());

        JooqUtil.contextResult(metaDbConnProvider, context -> context
                        .select(
                                META_VAL.META_ID,
                                META_VAL.META_KEY_ID,
                                META_VAL.VAL
                        )
                        .from(META_VAL)
                        .where(META_VAL.META_ID.in(idList))
                        .fetch())
                .forEach(r -> {
                    final int keyId = r.get(META_VAL.META_KEY_ID);
                    metaKeyService.getNameForId(keyId).ifPresent(name -> {
                        final long dataId = r.get(META_VAL.META_ID);
                        final String value = String.valueOf(r.get(META_VAL.VAL));
                        attributeMap.computeIfAbsent(dataId, k -> new HashMap<>()).put(name, value);
                    });
                });

        return attributeMap;

//        final SqlBuilder sql = new SqlBuilder();
//        sql.append("SELECT ");
//        sql.append(MetaValue.ID);
//        sql.append(", ");
//        sql.append(MetaValue.META_KEY_ID);
//        sql.append(", ");
//        sql.append(MetaValue.VALUE_NUMBER);
//        sql.append(", ");
//        sql.append(MetaValue.VALUE_STRING);
//        sql.append(" FROM ");
//        sql.append(MetaValue.TABLE_NAME);
//        sql.append(" WHERE ");
//        sql.append(MetaValue.ID);
//        sql.append(" in (");
//        sql.append(idList.toString());
//        sql.append(")");
//
//        // Status is a mandatory search
//
//        @SuppressWarnings("unchecked") final List<Object[]> list = entityManager.executeNativeQueryResultList(sql);
//
//        for (final Object[] row : list) {
//            final long dataId = ((Number) row[0]).longValue();
//            final String key = String.valueOf(row[1]);
//            String value = String.valueOf(row[2]);
//            if (row[3] != null) {
//                value = String.valueOf(row[3]);
//            }
//
//            final MetaKey metaKey = keyMap.get(Long.parseLong(key));
//            if (metaKey != null) {
//                rowMap.get(dataId).addAttribute(metaKey.getName(), value);
//            }
//        }

//        // Add additional data retention information.
//        rowMap.values().parallelStream().forEach(ruleDecorator::addMatchingRetentionRuleInfo);
//
//        return result;
    }

    @Override
    public int delete(final Collection<Long> metaIds) {
        return JooqUtil.contextResult(metaDbConnProvider, context -> context
                .deleteFrom(META_VAL)
                .where(META_VAL.META_ID.in(metaIds))
                .execute());
    }

    @Override
    public void clear() {
        // TODO: 17/02/2023 How to clear down the queue
//        clearQueue();
    }

    void shutdown() {
        batchingQueue.shutdown();
    }

//    private synchronized void clearQueue() {
//        queue.clear();
//    }

    private static final class Row {

        private final long createMs;
        private final long metaId;
        private final int keyId;
        private final Long value;

        Row(final long createMs,
            final long metaId,
            final int keyId,
            final Long value) {
            this.createMs = createMs;
            this.metaId = metaId;
            this.keyId = keyId;
            this.value = value;
        }

        long getCreateMs() {
            return createMs;
        }

        long getMetaId() {
            return metaId;
        }

        int getKeyId() {
            return keyId;
        }

        Long getValue() {
            return value;
        }
    }
}
