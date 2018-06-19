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

package stroom.data.meta.impl.db;

import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.data.meta.api.Stream;
import stroom.data.meta.impl.db.stroom.tables.records.MetaNumericValueRecord;
import stroom.data.meta.api.StreamDataRow;
import stroom.util.lifecycle.JobTrackedSchedule;
import stroom.util.lifecycle.StroomFrequencySchedule;
import stroom.util.lifecycle.StroomShutdown;
import stroom.util.logging.LogExecutionTime;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import static stroom.data.meta.impl.db.stroom.tables.MetaNumericValue.META_NUMERIC_VALUE;

@Singleton
class MetaValueServiceImpl implements MetaValueService {
    private static final Logger LOGGER = LoggerFactory.getLogger(MetaValueServiceImpl.class);

    private static final String LOCK_NAME = "MetaDeleteExecutor";

    private final DataSource dataSource;
    private final MetaKeyService metaKeyService;
    private final MetaValueConfig metaValueConfig;

    private final Queue<MetaNumericValueRecord> queue = new ConcurrentLinkedQueue<>();

    @Inject
    MetaValueServiceImpl(final StreamMetaDataSource dataSource,
                         final MetaKeyService metaKeyService,
                         final MetaValueConfig metaValueConfig) {
        this.dataSource = dataSource;
        this.metaKeyService = metaKeyService;
        this.metaValueConfig = metaValueConfig;
    }

    @Override
    public void addAttributes(final Stream stream, final Map<String, String> attributes) {
        attributes.forEach((k, v) -> {
            try {
                final Long longValue = Long.valueOf(v);
                final Optional<Integer> optional = metaKeyService.getIdForName(k);
                optional.ifPresent(keyId -> {
                    MetaNumericValueRecord record = new MetaNumericValueRecord();
                    record.setCreateTime(stream.getCreateMs());
                    record.setStreamId(stream.getId());
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
    public void clear() {
        queue.clear();
    }


    // TODO : @66 Add a shutdown hook in here to ensure attribute values are flushed on shutdown
    @StroomShutdown
    public void shutdown() {
        flush();
    }


    // TODO : @66 MAKE SURE THIS GETS CALLED
    @Override
    @StroomFrequencySchedule("10s")
    public void flush() {
        boolean ranOutOfItems = false;

//        final long applicableStreamAgeMs = getApplicableStreamAgeMs();

        while (!ranOutOfItems) {
            final List<MetaNumericValueRecord> records = new ArrayList<>();
            MetaNumericValueRecord record;
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
//            final Set<Long> streamIdSet = new HashSet<>();
//            AsyncFlush item;
//            while ((item = queue.poll()) != null && batchInsert.size() < DEFAULT_FLUSH_BATCH_SIZE) {
//                batchInsert.add(item);
//                streamIdSet.add(item.getStreamId());
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
//                final Map<Long, Map<Long, Meta>> streamToAttributeMap = new HashMap<>();
//                for (final Meta value : metaValueService.find(criteria)) {
//                    streamToAttributeMap.computeIfAbsent(value.getStreamId(), k -> new HashMap<>())
//                            .put(value.getMetaKeyId(), value);
//                }
//
//                final List<Meta> batchUpdate = new ArrayList<>();
//
//                // Work out the batch inserts
//                for (final MetaKey streamMDKey : keys) {
//                    for (final AsyncFlush asyncFlush : batchInsert) {
//                        if (asyncFlush.getStream().getCreateMs() > applicableStreamAgeMs) {
//                            // Found a key
//                            if (asyncFlush.getAttributeMap().containsKey(streamMDKey.getName())) {
//                                final String newValue = asyncFlush.getAttributeMap().get(streamMDKey.getName());
//                                boolean dirty = false;
//                                Meta metaValue = null;
//                                final Map<Long, Meta> map = streamToAttributeMap
//                                        .get(asyncFlush.getStream().getId());
//                                if (map != null) {
//                                    metaValue = map.get(streamMDKey.getId());
//                                }
//
//                                // Existing Item
//                                if (metaValue != null) {
//                                    if (streamMDKey.getFieldType().isNumeric()) {
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
//                                    metaValue = new Meta(asyncFlush.getStream().getId(), streamMDKey,
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
//                            LOGGER.debug("flush() - Skipping flush of old stream attributes {} {}",
//                                    asyncFlush.getStream(), DateUtil.createNormalDateTimeString(applicableStreamAgeMs));
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

    private void insertRecords(final List<MetaNumericValueRecord> records) {
        final LogExecutionTime logExecutionTime = new LogExecutionTime();
        LOGGER.debug("Processing batch of {}, queue size is {}", records.size(), queue.size());

        try (final Connection connection = dataSource.getConnection()) {
            final DSLContext context = DSL.using(connection, SQLDialect.MYSQL);
            context
                    .batchStore(records)
                    .execute();
        } catch (final SQLException e) {
            LOGGER.error(e.getMessage(), e);
            throw new RuntimeException(e.getMessage(), e);
        }

        if (logExecutionTime.getDuration() > 1000) {
            LOGGER.warn("Saved {} updates, queue size is {}, completed in {}", records.size(), queue.size(), logExecutionTime);
        } else {
            LOGGER.debug("Saved {} updates, queue size is {}, completed in {}", records.size(), queue.size(), logExecutionTime);
        }
    }

    // TODO : @66 MAKE SURE THIS GETS CALLED
    @StroomFrequencySchedule("1h")
    @JobTrackedSchedule(jobName = "Stream Attributes Retention", description = "Delete attributes older than system property stroom.meta.deleteAge)")
    public void deleteOldValues() {
        // TODO : @66 ACQUIRE A CLUSTER LOCK BEFORE PERFORMING A BATCH DELETE TO REDUCE DB CONTENTION AND TO LET A SINGLE NODE DO THE JOB.

        final Long age = getAttributeDatabaseAgeMs();
        final int batchSize = metaValueConfig.getDeleteBatchSize();
        if (age != null) {
            int count = batchSize;
            while (count >= batchSize) {
                count = deleteBatchOfOldValues(age, batchSize);
            }
        }
    }

    private int deleteBatchOfOldValues(final long age, final int batchSize) {
        final LogExecutionTime logExecutionTime = new LogExecutionTime();
        LOGGER.debug("Processing batch age {}, batch size is {}", age, batchSize);

        int count;

        try (final Connection connection = dataSource.getConnection()) {
            final DSLContext context = DSL.using(connection, SQLDialect.MYSQL);
            // TODO : @66 Maybe try delete with limits again after un upgrade to MySQL 5.7.
//            count = context
//                    .delete(META_NUMERIC_VALUE)
//                    .where(META_NUMERIC_VALUE.ID.in(
//                            context.select(META_NUMERIC_VALUE.ID)
//                                    .from(META_NUMERIC_VALUE)
//                                    .where(META_NUMERIC_VALUE.CREATE_TIME.lessThan(age))
//                                    .orderBy(META_NUMERIC_VALUE.ID)
//                                    .limit(batchSize)
//                    ))
//                    .execute();


            count = context.execute("DELETE FROM {0} WHERE {1} < {2} ORDER BY {3} LIMIT {4}",
                    META_NUMERIC_VALUE,
                    META_NUMERIC_VALUE.CREATE_TIME,
                    age,
                    META_NUMERIC_VALUE.ID,
                    batchSize);

        } catch (final SQLException e) {
            LOGGER.error(e.getMessage(), e);
            throw new RuntimeException(e.getMessage(), e);
        }

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
     * @return The oldest stream attribute that we should keep
     */
    private Long getAttributeDatabaseAgeMs() {
        final long age = metaValueConfig.getDeleteAge();
        return System.currentTimeMillis() - age;
    }

    /**
     * Convert a basic stream list to a list of stream meta data using stream attribute keys and values.
     */
    @Override
    public List<StreamDataRow> decorateStreamsWithAttributes(final List<Stream> streamList) {

//        final List<StreamDataRow> result = new ArrayList<>();
        final Map<Long, StreamDataRow> streamMap = new HashMap<>();

        // Get a list of valid stream ids.
//        final Map<EntityRef, Optional<Object>> entityCache = new HashMap<>();
//        final Map<DocRef, Optional<Object>> uuidCache = new HashMap<>();
        final List<Long> streamIds = new ArrayList<>();
        for (final Stream stream : streamList) {
            streamMap.put(stream.getId(), new StreamDataRow(stream));
            streamIds.add(stream.getId());
        }

//        if (streamMap.size() == 0) {
//            return;
//        }

//        final List<MetaKey> allKeys = metaKeyService.findAll();
//        final Map<Integer, MetaKey> keyMap = new HashMap<>();
//        for (final MetaKey key : allKeys) {
//            keyMap.put(key.getId(), key);
//        }


        try (final Connection connection = dataSource.getConnection()) {
            final DSLContext context = DSL.using(connection, SQLDialect.MYSQL);

            context
                    .select(
                            META_NUMERIC_VALUE.STREAM_ID,
                            META_NUMERIC_VALUE.META_KEY_ID,
                            META_NUMERIC_VALUE.VAL
                    )
                    .from(META_NUMERIC_VALUE)
                    .where(META_NUMERIC_VALUE.STREAM_ID.in(streamIds))
                    .fetch()
                    .forEach(r -> {
                        final long streamId = r.component1();
                        final int keyId = r.component2();

                        final Optional<String> optional = metaKeyService.getNameForId(keyId);
                        if (optional.isPresent()) {
                            final String value = String.valueOf(r.component3());
                            streamMap.get(streamId).addAttribute(optional.get(), value);
                        }
                    });

        } catch (final SQLException e) {
            LOGGER.error(e.getMessage(), e);
            throw new RuntimeException(e.getMessage(), e);
        }

        return new ArrayList<>(streamMap.values());


//        final SqlBuilder sql = new SqlBuilder();
//        sql.append("SELECT ");
//        sql.append(MetaValue.STREAM_ID);
//        sql.append(", ");
//        sql.append(MetaValue.META_KEY_ID);
//        sql.append(", ");
//        sql.append(MetaValue.VALUE_NUMBER);
//        sql.append(", ");
//        sql.append(MetaValue.VALUE_STRING);
//        sql.append(" FROM ");
//        sql.append(MetaValue.TABLE_NAME);
//        sql.append(" WHERE ");
//        sql.append(MetaValue.STREAM_ID);
//        sql.append(" in (");
//        sql.append(streamIds.toString());
//        sql.append(")");
//
//        // Status is a mandatory search
//
//        @SuppressWarnings("unchecked") final List<Object[]> list = entityManager.executeNativeQueryResultList(sql);
//
//        for (final Object[] row : list) {
//            final long streamId = ((Number) row[0]).longValue();
//            final String key = String.valueOf(row[1]);
//            String value = String.valueOf(row[2]);
//            if (row[3] != null) {
//                value = String.valueOf(row[3]);
//            }
//
//            final MetaKey metaKey = keyMap.get(Long.parseLong(key));
//            if (metaKey != null) {
//                streamMap.get(streamId).addAttribute(metaKey.getName(), value);
//            }
//        }

//        // Add additional data retention information.
//        streamMap.values().parallelStream().forEach(ruleDecorator::addMatchingRetentionRuleInfo);
//
//        return result;
    }

    int deleteAll() {
        try (final Connection connection = dataSource.getConnection()) {
            final DSLContext context = DSL.using(connection, SQLDialect.MYSQL);
            return context
                    .delete(META_NUMERIC_VALUE)
                    .execute();
        } catch (final SQLException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }
}