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
import stroom.meta.impl.MetaValueDao;
import stroom.meta.shared.Meta;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogExecutionTime;
import stroom.util.shared.Clearable;

import org.jooq.BatchBindStep;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static stroom.meta.impl.db.jooq.tables.MetaVal.META_VAL;

@Singleton
class MetaValueDaoImpl implements MetaValueDao, Clearable {
    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(MetaValueDaoImpl.class);

    private static final String LOCK_NAME = "MetaDeleteExecutor";

    private final MetaDbConnProvider metaDbConnProvider;
    private final MetaKeyDao metaKeyService;
    private final MetaValueConfig metaValueConfig;
    private final ClusterLockService clusterLockService;

    private volatile List<Row> queue = new ArrayList<>();

    @Inject
    MetaValueDaoImpl(final MetaDbConnProvider metaDbConnProvider,
                     final MetaKeyDao metaKeyService,
                     final MetaValueConfig metaValueConfig,
                     final ClusterLockService clusterLockService) {
        this.metaDbConnProvider = metaDbConnProvider;
        this.metaKeyService = metaKeyService;
        this.metaValueConfig = metaValueConfig;
        this.clusterLockService = clusterLockService;
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
                                        LOGGER.debug(e::getMessage, e);
                                        return Optional.empty();
                                    }
                                }))
                .filter(Optional::isPresent)
                .map(Optional::get);

        final List<Row> records = stream.collect(Collectors.toList());
        if (metaValueConfig.isAddAsync()) {
            final Optional<List<Row>> optional = add(records, metaValueConfig.getFlushBatchSize());
            optional.ifPresent(this::insertRecords);
        } else {
            insertRecords(records);
        }
    }

    private synchronized Optional<List<Row>> add(final List<Row> records, final int batchSize) {
        List<Row> readyForFlush = null;
        queue.addAll(records);
        if (queue.size() >= batchSize) {
            // Switch out the current queue.
            readyForFlush = queue;
            queue = new ArrayList<>();
        }
        // Return the old queue for flushing if it was switched.
        return Optional.ofNullable(readyForFlush);
    }

    @Override
    public void flush() {
        final Optional<List<Row>> optional = add(Collections.emptyList(), 1);
        optional.ifPresent(this::insertRecords);
    }

    private void insertRecords(final List<Row> rows) {
        final LogExecutionTime logExecutionTime = new LogExecutionTime();
        LOGGER.debug(() -> "Processing batch of " + rows.size());

        JooqUtil.context(metaDbConnProvider, context -> {
            BatchBindStep batchBindStep = context
                    .batch(context
                            .insertInto(META_VAL, META_VAL.CREATE_TIME, META_VAL.META_ID, META_VAL.META_KEY_ID, META_VAL.VAL)
                            .values((Long) null, (Long) null, (Integer) null, (Long) null));
            for (final Row row : rows) {
                batchBindStep = batchBindStep.bind(row.getCreateMs(), row.getMetaId(), row.getKeyId(), row.getValue());
            }
            batchBindStep.execute();
        });

        if (logExecutionTime.getDuration() > 1000) {
            LOGGER.warn(() -> "Saved " + rows.size() + " updates, completed in " + logExecutionTime);
        } else {
            LOGGER.debug(() -> "Saved " + rows.size() + " updates, completed in " + logExecutionTime);
        }
    }

    @Override
    public void deleteOldValues() {
        // Acquire a cluster lock before performing a batch delete to reduce db contention and to let a single node do the job.
        clusterLockService.tryLock(LOCK_NAME, () -> {
            final Long createTimeThresholdEpochMs = getAttributeCreateTimeThresholdEpochMs();
            final int batchSize = metaValueConfig.getDeleteBatchSize();
            int count = batchSize;
            while (count >= batchSize) {
                count = deleteBatchOfOldValues(createTimeThresholdEpochMs, batchSize);
            }
        });
    }

    private int deleteBatchOfOldValues(final long createTimeThresholdEpochMs, final int batchSize) {
        final LogExecutionTime logExecutionTime = new LogExecutionTime();
        LOGGER.debug(() -> "Processing batch age " + createTimeThresholdEpochMs + ", batch size is " + batchSize);

        final int count = JooqUtil.contextResult(metaDbConnProvider, context -> context
                        // TODO : @66 Maybe try delete with limits again after un upgrade to MySQL 5.7.
//            count = context
//                    .delete(META_VAL)
//                    .where(META_VAL.ID.in(
//                            context.select(META_VAL.ID)
//                                    .from(META_VAL)
//                                    .where(META_VAL.CREATE_TIME.lessThan(age))
//                                    .orderBy(META_VAL.ID)
//                                    .limit(batchSize)
//                    ))
//                    .execute();


                        .execute("DELETE FROM {0} WHERE {1} < {2} ORDER BY {3} LIMIT {4}",
                                META_VAL,
                                META_VAL.CREATE_TIME,
                                createTimeThresholdEpochMs,
                                META_VAL.ID,
                                batchSize)
        );

        if (logExecutionTime.getDuration() > 1000) {
            LOGGER.warn(() -> "Deleted " + count + ", completed in " + logExecutionTime);
        } else {
            LOGGER.debug(() -> "Deleted " + count + ", completed in " + logExecutionTime);
        }

        return count;

//        final SqlBuilder sql = new SqlBuilder();
//        sql.append("SELECT ");
//        sql.append(MetaValue.ID);
//        sql.append(" FROM ");
//        sql.append(MetaValue.TABLE_NAME);
//        sql.append(" WHERE ");
//        sql.append(MetaValue.CREATE_MS);
//        sql.append(" < ");
//        sql.arg(age);
//        sql.append(" ORDER BY ");
//        sql.append(MetaValue.ID);
//        sql.append(" LIMIT ");
//        sql.arg(batchSize);
//        return stroomEntityManager.executeNativeQueryResultList(sql);
    }

    /**
     * @return The oldest data attribute that we should keep
     */
    private Long getAttributeCreateTimeThresholdEpochMs() {
        final Duration deleteAge = metaValueConfig.getDeleteAge().getDuration();
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

        JooqUtil.context(metaDbConnProvider, context -> context
                .select(
                        META_VAL.META_ID,
                        META_VAL.META_KEY_ID,
                        META_VAL.VAL
                )
                .from(META_VAL)
                .where(META_VAL.META_ID.in(idList))
                .fetch()
                .forEach(r -> {
                    final int keyId = r.get(META_VAL.META_KEY_ID);
                    metaKeyService.getNameForId(keyId).ifPresent(name -> {
                        final long dataId = r.get(META_VAL.META_ID);
                        final String value = String.valueOf(r.get(META_VAL.VAL));
                        attributeMap.computeIfAbsent(dataId, k -> new HashMap<>()).put(name, value);
                    });
                })
        );

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
    public int delete(final List<Long> metaIdList) {
        return JooqUtil.contextResult(metaDbConnProvider, context -> context
                .deleteFrom(META_VAL)
                .where(META_VAL.META_ID.in(metaIdList))
                .execute());
    }

    @Override
    public void clear() {
        clearQueue();
    }

    private synchronized void clearQueue() {
        queue.clear();
    }

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