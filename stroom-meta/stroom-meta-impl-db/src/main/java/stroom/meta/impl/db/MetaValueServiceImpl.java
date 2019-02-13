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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.cluster.lock.api.ClusterLockService;
import stroom.db.util.JooqUtil;
import stroom.meta.impl.db.jooq.tables.records.MetaValRecord;
import stroom.meta.shared.AttributeMap;
import stroom.meta.shared.Meta;
import stroom.meta.shared.MetaRow;
import stroom.util.logging.LogExecutionTime;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Collectors;

import static stroom.meta.impl.db.jooq.tables.MetaVal.META_VAL;

@Singleton
class MetaValueServiceImpl implements MetaValueService {
    private static final Logger LOGGER = LoggerFactory.getLogger(MetaValueServiceImpl.class);

    private static final String LOCK_NAME = "MetaDeleteExecutor";

    private final ConnectionProvider connectionProvider;
    private final MetaKeyService metaKeyService;
    private final MetaValueConfig metaValueConfig;
    private final ClusterLockService clusterLockService;

    private final Queue<MetaValRecord> queue = new ConcurrentLinkedQueue<>();

    @Inject
    MetaValueServiceImpl(final ConnectionProvider connectionProvider,
                         final MetaKeyService metaKeyService,
                         final MetaValueConfig metaValueConfig,
                         final ClusterLockService clusterLockService) {
        this.connectionProvider = connectionProvider;
        this.metaKeyService = metaKeyService;
        this.metaValueConfig = metaValueConfig;
        this.clusterLockService = clusterLockService;
    }

    @Override
    public void addAttributes(final Meta meta, final AttributeMap attributes) {
        attributes.forEach((k, v) -> {
            try {
                final Long longValue = Long.valueOf(v);
                final Optional<Integer> optional = metaKeyService.getIdForName(k);
                optional.ifPresent(keyId -> {
                    MetaValRecord record = new MetaValRecord();
                    record.setCreateTime(meta.getCreateMs());
                    record.setMetaId(meta.getId());
                    record.setMetaKeyId(keyId);
                    record.setVal(longValue);

                    if (metaValueConfig.isAddAsync()) {
                        queue.add(record);
                    } else {
                        insertRecords(Collections.singletonList(record));
                    }
                });
            } catch (final NumberFormatException e) {
                LOGGER.debug(e.getMessage(), e);
            }
        });
    }

    @Override
    public void flush() {
        boolean ranOutOfItems = false;

//        final long applicableStreamAgeMs = getApplicableStreamAgeMs();

        while (!ranOutOfItems) {
            final List<MetaValRecord> records = new ArrayList<>();
            MetaValRecord record;
            while ((record = queue.poll()) != null && records.size() < metaValueConfig.getFlushBatchSize()) {
                records.add(record);
            }

            if (records.size() < metaValueConfig.getFlushBatchSize()) {
                ranOutOfItems = true;
            }

            if (records.size() > 0) {
                insertRecords(records);
            }


//            final FindStreamCriteria criteria = new FindStreamCriteria();
//
//            final ArrayList<AsyncFlush> batchInsert = new ArrayList<>();
//            final Set<Long> idSet = new HashSet<>();
//            AsyncFlush item;
//            while ((item = queue.poll()) != null && batchInsert.size() < DEFAULT_FLUSH_BATCH_SIZE) {
//                batchInsert.add(item);
//                idSet.add(item.getMetaId());
//            }
//
//            if (batchInsert.size() < DEFAULT_FLUSH_BATCH_SIZE) {
//                ranOutOfItems = true;
//            }
//
//            if (batchInsert.size() > 0) {
//
//
//                int skipCount = 0;
//
//                // Key by the MetaKey pk
//                final Map<Long, Map<Long, Meta>> dataToAttributeMap = new HashMap<>();
//                for (final Meta value : metaValueService.find(criteria)) {
//                    dataToAttributeMap.computeIfAbsent(value.getMetaId(), k -> new HashMap<>())
//                            .put(value.getMetaKeyId(), value);
//                }
//
//                final List<Meta> batchUpdate = new ArrayList<>();
//
//                // Work out the batch inserts
//                for (final MetaKey metaKey : keys) {
//                    for (final AsyncFlush asyncFlush : batchInsert) {
//                        if (asyncFlush.getMeta().getCreateMs() > applicableStreamAgeMs) {
//                            // Found a key
//                            if (asyncFlush.getAttributeMap().containsKey(metaKey.getName())) {
//                                final String newValue = asyncFlush.getAttributeMap().get(metaKey.getName());
//                                boolean dirty = false;
//                                Meta metaValue = null;
//                                final Map<Long, Meta> map = dataToAttributeMap
//                                        .get(asyncFlush.getMeta().getId());
//                                if (map != null) {
//                                    metaValue = map.get(metaKey.getId());
//                                }
//
//                                // Existing Item
//                                if (metaValue != null) {
//                                    if (metaKey.getFieldType().isNumeric()) {
//                                        final Long oldValueLong = metaValue.getValueNumber();
//                                        final Long newValueLong = Long.parseLong(newValue);
//
//                                        if (!oldValueLong.equals(newValueLong)) {
//                                            dirty = true;
//                                            metaValue.setValueNumber(newValueLong);
//                                        }
//                                    } else {
//                                        final String oldValue = metaValue.getValueString();
//
//                                        if (!oldValue.equals(newValue)) {
//                                            dirty = true;
//                                            metaValue.setValueString(newValue);
//                                        }
//                                    }
//
//                                } else {
//                                    dirty = true;
//                                    metaValue = new Meta(asyncFlush.getMeta().getId(), metaKey,
//                                            newValue);
//                                }
//
//                                if (dirty) {
//                                    batchUpdate.add(metaValue);
//                                }
//
//                            }
//                        } else {
//                            skipCount++;
//                            LOGGER.debug("flush() - Skipping flush of old data attributes {} {}",
//                                    asyncFlush.getMeta(), DateUtil.createNormalDateTimeString(applicableStreamAgeMs));
//                        }
//                    }
//                }
//
//                // We might have no keys so will not have built any batch
//                // updates.
//                if (batchUpdate.size() > 0) {
//                    metaValueServiceTransactionHelper.saveBatch(batchUpdate);
//                }


        }
    }

    private void insertRecords(final List<MetaValRecord> records) {
        final LogExecutionTime logExecutionTime = new LogExecutionTime();
        LOGGER.debug("Processing batch of {}, queue size is {}", records.size(), queue.size());

        JooqUtil.context(connectionProvider, context -> context
                .batchStore(records)
                .execute());

        if (logExecutionTime.getDuration() > 1000) {
            LOGGER.warn("Saved {} updates, queue size is {}, completed in {}", records.size(), queue.size(), logExecutionTime);
        } else {
            LOGGER.debug("Saved {} updates, queue size is {}, completed in {}", records.size(), queue.size(), logExecutionTime);
        }
    }

    @Override
    public void deleteOldValues() {
        // Acquire a cluster lock before performing a batch delete to reduce db contention and to let a single node do the job.
        if (clusterLockService.tryLock(LOCK_NAME)) {
            try {
                final Long age = getAttributeDatabaseAgeMs();
                final int batchSize = metaValueConfig.getDeleteBatchSize();
                int count = batchSize;
                while (count >= batchSize) {
                    count = deleteBatchOfOldValues(age, batchSize);
                }
            } finally {
                clusterLockService.releaseLock(LOCK_NAME);
            }
        }
    }

    private int deleteBatchOfOldValues(final long age, final int batchSize) {
        final LogExecutionTime logExecutionTime = new LogExecutionTime();
        LOGGER.debug("Processing batch age {}, batch size is {}", age, batchSize);

        final int count = JooqUtil.contextResult(connectionProvider, context -> context
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
                                age,
                                META_VAL.ID,
                                batchSize)
        );

        if (logExecutionTime.getDuration() > 1000) {
            LOGGER.warn("Deleted {}, completed in {}", count, logExecutionTime);
        } else {
            LOGGER.debug("Deleted {}, completed in {}", count, logExecutionTime);
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
    private Long getAttributeDatabaseAgeMs() {
        final long age = metaValueConfig.getDeleteAgeMs();
        return System.currentTimeMillis() - age;
    }

    /**
     * Convert a basic data list to a list of meta data using data attribute keys and values.
     */
    @Override
    public List<MetaRow> decorateDataWithAttributes(final List<Meta> list) {
        final Map<Long, MetaRow> rowMap = new HashMap<>();

        // Get a list of valid data ids.
        final List<Long> idList = list.parallelStream()
                .map(Meta::getId)
                .collect(Collectors.toList());

        JooqUtil.context(connectionProvider, context -> context
                .select(
                        META_VAL.META_ID,
                        META_VAL.META_KEY_ID,
                        META_VAL.VAL
                )
                .from(META_VAL)
                .where(META_VAL.META_ID.in(idList))
                .fetch()
                .forEach(r -> {
                    final int keyId = r.component2();
                    metaKeyService.getNameForId(keyId).ifPresent(name -> {
                        final long dataId = r.component1();
                        final String value = String.valueOf(r.component3());
                        rowMap.computeIfAbsent(dataId, k -> new MetaRow()).addAttribute(name, value);
                    });
                })
        );

        final List<MetaRow> dataRows = new ArrayList<>();
        for (final Meta meta : list) {
            MetaRow dataRow = rowMap.get(meta.getId());
            if (dataRow != null) {
                dataRow.setMeta(meta);
            } else {
                dataRow = new MetaRow(meta);
            }
            dataRows.add(dataRow);
        }

        return dataRows;


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

    void clear() {
        queue.clear();
        deleteAll();
    }

    int deleteAll() {
        return JooqUtil.contextResult(connectionProvider, context -> context
                .delete(META_VAL)
                .execute());
    }
}