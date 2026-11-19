/*
 * Copyright 2019 Crown Copyright
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

package stroom.index.impl.dao;

import stroom.db.util.ExpressionMapper;
import stroom.db.util.ExpressionMapperFactory;
import stroom.db.util.GenericDao;
import stroom.db.util.JooqUtil;
import stroom.db.util.ValueMapper;
import stroom.db.util.ValueMapper.Mapper;
import stroom.docref.DocRef;
import stroom.docstore.api.DocFinder;
import stroom.entity.shared.ExpressionCriteria;
import stroom.index.impl.IndexShardDao;
import stroom.index.impl.db.IndexDbConnProvider;
import stroom.index.impl.db.jooq.tables.IndexVolumeGroup;
import stroom.index.impl.db.jooq.tables.records.IndexShardRecord;
import stroom.index.shared.FindIndexShardCriteria;
import stroom.index.shared.IndexShard;
import stroom.index.shared.IndexShard.IndexShardStatus;
import stroom.index.shared.IndexShardFields;
import stroom.index.shared.IndexShardKey;
import stroom.index.shared.IndexVolume;
import stroom.index.shared.IndexVolume.VolumeType;
import stroom.index.shared.LuceneIndexDoc;
import stroom.index.shared.Partition;
import stroom.query.api.ExpressionItem;
import stroom.query.api.ExpressionUtil;
import stroom.query.common.v2.DateExpressionParser;
import stroom.query.language.functions.FieldIndex;
import stroom.query.language.functions.Val;
import stroom.query.language.functions.ValDate;
import stroom.query.language.functions.ValInteger;
import stroom.query.language.functions.ValLong;
import stroom.query.language.functions.ValNull;
import stroom.query.language.functions.ValString;
import stroom.query.language.functions.ValuesConsumer;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.PageRequest;
import stroom.util.shared.Range;
import stroom.util.shared.ResultPage;
import stroom.util.shared.Selection;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jooq.Condition;
import org.jooq.Cursor;
import org.jooq.Field;
import org.jooq.OrderField;
import org.jooq.Record;
import org.jooq.Result;
import org.jooq.SelectJoinStep;
import org.jooq.impl.DSL;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;

import static stroom.index.impl.db.jooq.tables.IndexShard.INDEX_SHARD;
import static stroom.index.impl.db.jooq.tables.IndexVolume.INDEX_VOLUME;
import static stroom.index.impl.db.jooq.tables.IndexVolumeGroup.INDEX_VOLUME_GROUP;

@Singleton // holding all the volume selectors
class IndexShardDaoImpl implements IndexShardDao {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(IndexShardDaoImpl.class);

    // The database's clock, so that lease times do not depend on the clocks of the nodes agreeing. Taken
    // from UTC so that it does not depend on the session's time zone and never jumps when the clocks change.
    private static final Field<Long> DB_TIME_MS = DSL.field(
            "CAST(TIMESTAMPDIFF(MICROSECOND, '1970-01-01 00:00:00', UTC_TIMESTAMP(3)) DIV 1000 AS SIGNED)",
            Long.class);

    private static final Function<Record, IndexShard> RECORD_TO_INDEX_SHARD_MAPPER = record -> IndexShard
            .builder()
            .id(record.get(INDEX_SHARD.ID))
            .partition(record.get(INDEX_SHARD.PARTITION_NAME))
            .partitionFromTime(record.get(INDEX_SHARD.PARTITION_FROM_MS))
            .partitionToTime(record.get(INDEX_SHARD.PARTITION_TO_MS))
            .documentCount(record.get(INDEX_SHARD.DOCUMENT_COUNT))
            .commitMs(record.get(INDEX_SHARD.COMMIT_MS))
            .commitDurationMs(record.get(INDEX_SHARD.COMMIT_DURATION_MS))
            .commitDocumentCount(record.get(INDEX_SHARD.COMMIT_DOCUMENT_COUNT))
            .status(IndexShardStatus.PRIMITIVE_VALUE_CONVERTER.fromPrimitiveValue(
                    record.get(INDEX_SHARD.STATUS)))
            .fileSize(record.get(INDEX_SHARD.FILE_SIZE))
            .indexVersion(record.get(INDEX_SHARD.INDEX_VERSION))
            .nodeName(record.get(INDEX_SHARD.NODE_NAME))
            .indexUuid(record.get(INDEX_SHARD.INDEX_UUID))
            .build();

    private static final BiFunction<IndexShard, IndexShardRecord, IndexShardRecord> INDEX_SHARD_TO_RECORD_MAPPER =
            (indexShard, record) -> {
                record.set(INDEX_SHARD.ID, indexShard.getId());
                record.set(INDEX_SHARD.PARTITION_NAME, indexShard.getPartition());
                record.set(INDEX_SHARD.PARTITION_FROM_MS, indexShard.getPartitionFromTime());
                record.set(INDEX_SHARD.PARTITION_TO_MS, indexShard.getPartitionToTime());
                record.set(INDEX_SHARD.DOCUMENT_COUNT, indexShard.getDocumentCount());
                record.set(INDEX_SHARD.COMMIT_MS, indexShard.getCommitMs());
                record.set(INDEX_SHARD.COMMIT_DURATION_MS, indexShard.getCommitDurationMs());
                record.set(INDEX_SHARD.COMMIT_DOCUMENT_COUNT, indexShard.getCommitDocumentCount());
                record.set(INDEX_SHARD.STATUS, indexShard.getStatus().getPrimitiveValue());
                record.set(INDEX_SHARD.FILE_SIZE, indexShard.getFileSize());
                record.set(INDEX_SHARD.INDEX_VERSION, indexShard.getIndexVersion());
                record.set(INDEX_SHARD.FK_VOLUME_ID, indexShard.getVolume().getId());
                record.set(INDEX_SHARD.NODE_NAME, indexShard.getNodeName());
                record.set(INDEX_SHARD.INDEX_UUID, indexShard.getIndexUuid());
                return record;
            };

    private static final Map<String, Field<?>> FIELD_MAP = Map.of(
            FindIndexShardCriteria.FIELD_ID, INDEX_SHARD.ID,
            FindIndexShardCriteria.FIELD_NODE, INDEX_SHARD.NODE_NAME,
            FindIndexShardCriteria.FIELD_PARTITION, INDEX_SHARD.PARTITION_NAME,
            FindIndexShardCriteria.FIELD_STATUS, INDEX_SHARD.STATUS,
            FindIndexShardCriteria.FIELD_DOC_COUNT, INDEX_SHARD.DOCUMENT_COUNT,
            FindIndexShardCriteria.FIELD_LAST_COMMIT, INDEX_SHARD.COMMIT_MS,
            FindIndexShardCriteria.FIELD_FILE_SIZE, INDEX_SHARD.FILE_SIZE);

    private final IndexDbConnProvider indexDbConnProvider;
    private final GenericDao<IndexShardRecord, IndexShard, Long> genericDao;
    private final IndexShardValueMapper indexShardValueMapper;
    private final IndexShardExpressionMapper indexShardExpressionMapper;

    @Inject
    IndexShardDaoImpl(final IndexDbConnProvider indexDbConnProvider,
                      final IndexShardValueMapper indexShardValueMapper,
                      final IndexShardExpressionMapper indexShardExpressionMapper) {

        this.indexDbConnProvider = indexDbConnProvider;
        this.indexShardValueMapper = indexShardValueMapper;
        this.indexShardExpressionMapper = indexShardExpressionMapper;
        genericDao = new GenericDao<>(
                indexDbConnProvider,
                INDEX_SHARD,
                INDEX_SHARD.ID,
                INDEX_SHARD_TO_RECORD_MAPPER,
                RECORD_TO_INDEX_SHARD_MAPPER);
    }

    @Override
    public Optional<IndexShard> fetch(final long id) {
        return JooqUtil.contextResult(indexDbConnProvider, context -> context
                        .select()
                        .from(INDEX_SHARD)
                        .join(INDEX_VOLUME).on(INDEX_VOLUME.ID.eq(INDEX_SHARD.FK_VOLUME_ID))
                        .where(INDEX_SHARD.ID.eq(id))
                        .fetchOptional())
                .map(r -> {
                    final IndexVolume indexVolume = IndexVolumeDaoImpl.RECORD_TO_INDEX_VOLUME_MAPPER.apply(r);
                    return RECORD_TO_INDEX_SHARD_MAPPER.apply(r).copy().volume(indexVolume).build();
                });
    }

    @Override
    public ResultPage<IndexShard> find(final FindIndexShardCriteria criteria) {
        final Collection<Condition> conditions = JooqUtil.conditions(
                JooqUtil.getRangeCondition(INDEX_SHARD.DOCUMENT_COUNT, criteria.getDocumentCountRange()),
                JooqUtil.getSetCondition(INDEX_SHARD.NODE_NAME, criteria.getNodeNameSet()),
                JooqUtil.getSetCondition(INDEX_SHARD.FK_VOLUME_ID, criteria.getVolumeIdSet()),
                JooqUtil.getSetCondition(INDEX_SHARD.ID, criteria.getIndexShardIdSet()),
                JooqUtil.getSetCondition(INDEX_SHARD.INDEX_UUID, criteria.getIndexUuidSet()),
                JooqUtil.getSetCondition(INDEX_SHARD.STATUS,
                        Selection.convert(criteria.getIndexShardStatusSet(),
                                IndexShard.IndexShardStatus::getPrimitiveValue)),
                JooqUtil.getStringCondition(INDEX_SHARD.PARTITION_NAME, criteria.getPartition()),
                // A shard is found if its partition overlaps the time range. Shards keep the partitioning
                // they were written with, which may be coarser than the index's current setting, and a
                // shard written while its index had No partition has no partition times, as it may hold
                // documents from any time.
                Optional.ofNullable(criteria.getPartitionTimeRange())
                        .map(Range::getTo)
                        .map(to -> INDEX_SHARD.PARTITION_FROM_MS.isNull()
                                .or(INDEX_SHARD.PARTITION_FROM_MS.lessOrEqual(to))),
                Optional.ofNullable(criteria.getPartitionTimeRange())
                        .map(Range::getFrom)
                        .map(from -> INDEX_SHARD.PARTITION_TO_MS.isNull()
                                .or(INDEX_SHARD.PARTITION_TO_MS.greaterThan(from)))
        );

        final Collection<OrderField<?>> orderFields = JooqUtil.getOrderFields(FIELD_MAP, criteria);

        final List<IndexShard> list = JooqUtil.contextResult(indexDbConnProvider, context ->
                        context
                                .select()
                                .from(INDEX_SHARD)
                                .join(INDEX_VOLUME).on(INDEX_VOLUME.ID.eq(INDEX_SHARD.FK_VOLUME_ID))
                                .where(conditions)
                                .orderBy(orderFields)
                                .limit(JooqUtil.getLimit(criteria.getPageRequest(), true))
                                .offset(JooqUtil.getOffset(criteria.getPageRequest()))
                                .fetch())
                .map(r -> {
                    final IndexVolume indexVolume = IndexVolumeDaoImpl.RECORD_TO_INDEX_VOLUME_MAPPER.apply(r);
                    return RECORD_TO_INDEX_SHARD_MAPPER.apply(r).copy().volume(indexVolume).build();
                });

        return ResultPage.createCriterialBasedList(list, criteria);
    }

    @Override
    public void search(final ExpressionCriteria criteria,
                       final FieldIndex fieldIndex,
                       final ValuesConsumer consumer) {
        final String[] fieldNames = fieldIndex.getFields();
        final Collection<OrderField<?>> orderFields = JooqUtil.getOrderFields(FIELD_MAP, criteria);
        final List<Field<?>> dbFields = indexShardValueMapper.getDbFieldsByName(fieldNames);
        final Mapper<?>[] mappers = indexShardValueMapper.getMappersForFieldNames(fieldNames);
        final PageRequest pageRequest = criteria.getPageRequest();
        final Condition condition = indexShardExpressionMapper.apply(criteria.getExpression());

        final boolean volumeUsed = isUsed(
                Set.of(IndexShardFields.FIELD_VOLUME_PATH.getFldName(),
                        IndexShardFields.FIELD_VOLUME_GROUP.getFldName()),
                fieldNames,
                criteria);
        final boolean volumeGroupUsed = isUsed(
                Set.of(IndexShardFields.FIELD_VOLUME_GROUP.getFldName()),
                fieldNames,
                criteria);

        JooqUtil.context(indexDbConnProvider, context -> {
            Integer offset = null;
            Integer numberOfRows = null;

            if (pageRequest != null) {
                offset = pageRequest.getOffset();
                numberOfRows = pageRequest.getLength();
            }

            SelectJoinStep<Record> select = context
                    .select(dbFields)
                    .from(INDEX_SHARD);

            if (volumeUsed) {
                select = select.join(INDEX_VOLUME)
                        .on(INDEX_VOLUME.ID.eq(INDEX_SHARD.FK_VOLUME_ID));
            }

            if (volumeGroupUsed) {
                select = select.join(INDEX_VOLUME_GROUP)
                        .on(INDEX_VOLUME_GROUP.ID.eq(INDEX_VOLUME.FK_INDEX_VOLUME_GROUP_ID));
            }

            try (final Cursor<?> cursor = select
                    .where(condition)
                    .orderBy(orderFields)
                    .limit(offset, numberOfRows)
                    .fetchLazy()) {

                while (cursor.hasNext()) {
                    final Result<?> result = cursor.fetchNext(1000);

                    result.forEach(r -> {
                        final Val[] arr = new Val[fieldNames.length];
                        for (int i = 0; i < fieldNames.length; i++) {
                            Val val = ValNull.INSTANCE;
                            final Mapper<?> mapper = mappers[i];
                            if (mapper != null) {
                                val = mapper.map(r);
                            }
                            arr[i] = val;
                        }
                        consumer.accept(Val.of(arr));
                    });
                }
            }
        });
    }

    @Override
    public IndexShard create(final IndexShardKey indexShardKey,
                             final IndexVolume indexVolume,
                             final String ownerNodeName,
                             final String indexVersion) {
        final Partition partition = indexShardKey.getPartition();
        final IndexShard indexShard = IndexShard
                .builder()
                .indexUuid(indexShardKey.getIndexUuid())
                .nodeName(ownerNodeName)
                .partition(partition.getLabel())
                .partitionFromTime(partition.getPartitionFromTime())
                .partitionToTime(partition.getPartitionToTime())
                .volume(indexVolume)
                .indexVersion(indexVersion)
                .build();

        return genericDao.create(indexShard).copy().volume(indexVolume).build();
    }

    @Override
    public boolean delete(final Long id) {
        return genericDao.delete(id);
    }

    @Override
    public boolean setStatus(final Long id,
                             final IndexShardStatus status) {
        final Condition currentStateCondition = switch (status) {
            case NEW -> DSL.falseCondition();
            case OPENING -> INDEX_SHARD.STATUS.eq(IndexShardStatus.CLOSED.getPrimitiveValue())
                    .or(INDEX_SHARD.STATUS.eq(IndexShardStatus.NEW.getPrimitiveValue()));
            case OPEN -> INDEX_SHARD.STATUS.eq(IndexShardStatus.OPENING.getPrimitiveValue());
            case CLOSING -> INDEX_SHARD.STATUS.eq(IndexShardStatus.OPEN.getPrimitiveValue());
            case CLOSED -> INDEX_SHARD.STATUS.eq(IndexShardStatus.OPENING.getPrimitiveValue())
                    .or(INDEX_SHARD.STATUS.eq(IndexShardStatus.CLOSING.getPrimitiveValue()));
            case DELETED -> DSL.trueCondition();
            case CORRUPT -> INDEX_SHARD.STATUS.ne(IndexShardStatus.DELETED.getPrimitiveValue());
        };

        final boolean didUpdate = JooqUtil.contextResult(indexDbConnProvider, context -> context
                .update(INDEX_SHARD)
                .set(INDEX_SHARD.STATUS, status.getPrimitiveValue())
                .where(INDEX_SHARD.ID.eq(id))
                .and(currentStateCondition)
                .execute()) > 0;

        if (LOGGER.isDebugEnabled()) {
            if (didUpdate) {
                LOGGER.debug("Set shard status to {} for shard id {}", status, id);
            } else {
                try {
                    final Optional<IndexShardStatus> optStatus = fetch(id)
                            .map(IndexShard::getStatus);
                    LOGGER.debug("Unable to update status to {} for shard id {}, optStatus: {}", status, id, optStatus);
                } catch (final Exception e) {
                    LOGGER.debug("Error trying to fetch shard for debug", e);
                }
            }
        }

        return didUpdate;
    }

    @Override
    public void logicalDelete(final Long id) {
        JooqUtil.context(indexDbConnProvider, context -> context
                .update(INDEX_SHARD)
                .set(INDEX_SHARD.STATUS, IndexShardStatus.DELETED.getPrimitiveValue())
                .where(INDEX_SHARD.ID.eq(id))
                .execute());
    }

    @Override
    public boolean claimLease(final long id,
                              final String nodeName,
                              final String instanceId,
                              final long leaseDurationMs) {
        return JooqUtil.contextResult(indexDbConnProvider, context -> context
                .update(INDEX_SHARD)
                .set(INDEX_SHARD.STATUS, IndexShardStatus.OPENING.getPrimitiveValue())
                .set(INDEX_SHARD.NODE_NAME, nodeName)
                .set(INDEX_SHARD.LEASE_INSTANCE_ID, instanceId)
                .set(INDEX_SHARD.LEASE_EXPIRY_MS, DB_TIME_MS.plus(leaseDurationMs))
                .where(INDEX_SHARD.ID.eq(id))
                .and(INDEX_SHARD.STATUS.in(
                        IndexShardStatus.CLOSED.getPrimitiveValue(),
                        IndexShardStatus.NEW.getPrimitiveValue()))
                .and(INDEX_SHARD.LEASE_INSTANCE_ID.isNull())
                .execute()) > 0;
    }

    @Override
    public int renewLeases(final String instanceId, final long leaseDurationMs) {
        return JooqUtil.contextResult(indexDbConnProvider, context -> context
                .update(INDEX_SHARD)
                .set(INDEX_SHARD.LEASE_EXPIRY_MS, DB_TIME_MS.plus(leaseDurationMs))
                .where(INDEX_SHARD.LEASE_INSTANCE_ID.eq(instanceId))
                .and(INDEX_SHARD.LEASE_EXPIRY_MS.ge(DB_TIME_MS))
                .execute());
    }

    @Override
    public Set<Long> findLeasedShardIds(final String instanceId) {
        return new HashSet<>(JooqUtil.contextResult(indexDbConnProvider, context -> context
                .select(INDEX_SHARD.ID)
                .from(INDEX_SHARD)
                .where(INDEX_SHARD.LEASE_INSTANCE_ID.eq(instanceId))
                .and(INDEX_SHARD.LEASE_EXPIRY_MS.ge(DB_TIME_MS))
                .fetch(INDEX_SHARD.ID)));
    }

    @Override
    public boolean releaseLease(final long id,
                                final String instanceId,
                                final Long pruneCommitsDelayMs,
                                final boolean recoverPendingStreams) {
        final Field<Long> pruneCommitsAfterMs = pruneCommitsDelayMs == null
                ? INDEX_SHARD.PRUNE_COMMITS_AFTER_MS
                : DB_TIME_MS.plus(pruneCommitsDelayMs);
        return JooqUtil.contextResult(indexDbConnProvider, context -> context
                .update(INDEX_SHARD)
                .set(INDEX_SHARD.STATUS, closedIfOpen())
                .setNull(INDEX_SHARD.LEASE_INSTANCE_ID)
                .setNull(INDEX_SHARD.LEASE_EXPIRY_MS)
                .set(INDEX_SHARD.PRUNE_COMMITS_AFTER_MS, pruneCommitsAfterMs)
                .set(INDEX_SHARD.RECOVER_PENDING_STREAMS, recoverPendingStreams(recoverPendingStreams))
                .where(INDEX_SHARD.ID.eq(id))
                .and(INDEX_SHARD.LEASE_INSTANCE_ID.eq(instanceId))
                .execute()) > 0;
    }

    private static Condition isOpen() {
        return INDEX_SHARD.STATUS.in(
                IndexShardStatus.OPENING.getPrimitiveValue(),
                IndexShardStatus.OPEN.getPrimitiveValue(),
                IndexShardStatus.CLOSING.getPrimitiveValue());
    }

    // The status of a shard whose writer has gone. A shard that has been deleted or marked corrupt while
    // open keeps that status.
    private static Field<Byte> closedIfOpen() {
        return DSL
                .when(isOpen(), IndexShardStatus.CLOSED.getPrimitiveValue())
                .otherwise(INDEX_SHARD.STATUS);
    }

    // The flag is only ever set here. It stays set until the streams have been marked for recovery.
    private static Field<Byte> recoverPendingStreams(final boolean recoverPendingStreams) {
        return recoverPendingStreams
                ? DSL.inline((byte) 1)
                : INDEX_SHARD.RECOVER_PENDING_STREAMS;
    }

    @Override
    public boolean needsPendingStreamRecovery(final long id) {
        return JooqUtil.contextResult(indexDbConnProvider, context -> context
                .fetchExists(context
                        .selectOne()
                        .from(INDEX_SHARD)
                        .where(INDEX_SHARD.ID.eq(id))
                        .and(INDEX_SHARD.RECOVER_PENDING_STREAMS.ne((byte) 0))));
    }

    @Override
    public int releaseLapsedLeases(final long graceMs, final long replacedCommitRetentionMs) {
        // A lease that has not expired is never taken, whatever the grace period is set to.
        final Condition lapsed = INDEX_SHARD.LEASE_INSTANCE_ID.isNotNull()
                .and(INDEX_SHARD.LEASE_EXPIRY_MS.lt(DB_TIME_MS.minus(Math.max(0, graceMs))));
        return JooqUtil.contextResult(indexDbConnProvider, context -> {
            // There is rarely anything to take, and the update would lock every leased shard it looked at
            // while their holders are trying to renew them, so look first without locking.
            final boolean anyLapsed = context.fetchExists(context
                    .selectOne()
                    .from(INDEX_SHARD)
                    .where(lapsed));
            if (!anyLapsed) {
                return 0;
            }
            return context
                    .update(INDEX_SHARD)
                    // A deleted or corrupt shard is never opened again, so nothing would clear the flag.
                    // Set before the status, as MySQL gives later assignments the new values of columns.
                    .set(INDEX_SHARD.RECOVER_PENDING_STREAMS, DSL
                            .when(isOpen(), DSL.inline((byte) 1))
                            .otherwise(INDEX_SHARD.RECOVER_PENDING_STREAMS))
                    .set(INDEX_SHARD.STATUS, closedIfOpen())
                    .setNull(INDEX_SHARD.LEASE_INSTANCE_ID)
                    .setNull(INDEX_SHARD.LEASE_EXPIRY_MS)
                    // The old writer may have been keeping commits it had replaced.
                    .set(INDEX_SHARD.PRUNE_COMMITS_AFTER_MS, DSL.coalesce(
                            INDEX_SHARD.PRUNE_COMMITS_AFTER_MS,
                            DB_TIME_MS.plus(replacedCommitRetentionMs)))
                    .where(lapsed)
                    .execute();
        });
    }

    @Override
    public void setNeedsPendingStreamRecovery(final long id) {
        JooqUtil.context(indexDbConnProvider, context -> context
                .update(INDEX_SHARD)
                .set(INDEX_SHARD.RECOVER_PENDING_STREAMS, (byte) 1)
                .where(INDEX_SHARD.ID.eq(id))
                .and(INDEX_SHARD.LEASE_INSTANCE_ID.isNull())
                .execute());
    }

    @Override
    public void clearPendingStreamRecovery(final long id) {
        JooqUtil.context(indexDbConnProvider, context -> context
                .update(INDEX_SHARD)
                .set(INDEX_SHARD.RECOVER_PENDING_STREAMS, (byte) 0)
                .where(INDEX_SHARD.ID.eq(id))
                .execute());
    }

    @Override
    public List<IndexShard> findClosedShardsNeedingPendingStreamRecovery(final int limit) {
        return findClosedShards(
                INDEX_SHARD.RECOVER_PENDING_STREAMS.ne((byte) 0),
                limit);
    }

    @Override
    public List<IndexShard> findUnleasedShardsOnSharedVolumes(final String indexUuid,
                                                              final String partitionLabel) {
        return findUnleasedClosedShardsOnSharedVolumes(
                INDEX_SHARD.INDEX_UUID.eq(indexUuid).and(INDEX_SHARD.PARTITION_NAME.eq(partitionLabel)),
                Integer.MAX_VALUE);
    }

    @Override
    public List<IndexShard> findShardsWithCommitsToPrune(final int limit) {
        return findUnleasedClosedShardsOnSharedVolumes(
                INDEX_SHARD.PRUNE_COMMITS_AFTER_MS.le(DB_TIME_MS),
                limit);
    }

    private List<IndexShard> findUnleasedClosedShardsOnSharedVolumes(final Condition condition,
                                                                     final int limit) {
        return findClosedShards(
                condition.and(INDEX_VOLUME.VOLUME_TYPE.eq(VolumeType.SHARED.getPrimitiveValue())),
                limit);
    }

    private List<IndexShard> findClosedShards(final Condition condition, final int limit) {
        return JooqUtil.contextResult(indexDbConnProvider, context -> context
                        .select()
                        .from(INDEX_SHARD)
                        .join(INDEX_VOLUME).on(INDEX_VOLUME.ID.eq(INDEX_SHARD.FK_VOLUME_ID))
                        .where(condition)
                        .and(INDEX_SHARD.STATUS.eq(IndexShardStatus.CLOSED.getPrimitiveValue()))
                        .and(INDEX_SHARD.LEASE_INSTANCE_ID.isNull())
                        .orderBy(INDEX_SHARD.ID)
                        .limit(limit)
                        .fetch())
                .map(r -> {
                    final IndexVolume indexVolume = IndexVolumeDaoImpl.RECORD_TO_INDEX_VOLUME_MAPPER.apply(r);
                    return RECORD_TO_INDEX_SHARD_MAPPER.apply(r).copy().volume(indexVolume).build();
                });
    }

    @Override
    public void clearPruneCommitsTime(final long id) {
        JooqUtil.context(indexDbConnProvider, context -> context
                .update(INDEX_SHARD)
                .setNull(INDEX_SHARD.PRUNE_COMMITS_AFTER_MS)
                .where(INDEX_SHARD.ID.eq(id))
                .execute());
    }

    @Override
    public boolean isLeased(final long id) {
        return JooqUtil.contextResult(indexDbConnProvider, context -> context
                .fetchExists(context
                        .selectOne()
                        .from(INDEX_SHARD)
                        .where(INDEX_SHARD.ID.eq(id))
                        .and(INDEX_SHARD.LEASE_INSTANCE_ID.isNotNull())));
    }

    @Override
    public void reset(final Long id, final boolean recoverPendingStreams) {
        reset(id, INDEX_SHARD.LEASE_INSTANCE_ID.isNull(), recoverPendingStreams);
    }

    @Override
    public void resetAfterRestart(final Long id, final String nodeName, final String instanceId) {
        // A lease held under another node's name belongs to a node that has taken the shard on since.
        reset(id,
                INDEX_SHARD.LEASE_INSTANCE_ID.isNull()
                        .or(INDEX_SHARD.LEASE_INSTANCE_ID.ne(instanceId)
                                .and(INDEX_SHARD.NODE_NAME.eq(nodeName))),
                true);
    }

    private void reset(final Long id, final Condition leaseCondition, final boolean recoverPendingStreams) {
        JooqUtil.context(indexDbConnProvider, context -> context
                .update(INDEX_SHARD)
                .set(INDEX_SHARD.STATUS, IndexShardStatus.CLOSED.getPrimitiveValue())
                .setNull(INDEX_SHARD.LEASE_INSTANCE_ID)
                .setNull(INDEX_SHARD.LEASE_EXPIRY_MS)
                .set(INDEX_SHARD.RECOVER_PENDING_STREAMS, recoverPendingStreams(recoverPendingStreams))
                .where(INDEX_SHARD.ID.eq(id))
                .and(leaseCondition)
                .and(isOpen())
                .execute());
    }

    @Override
    public void update(final Long id,
                       final Integer documentCount,
                       final Long commitDurationMs,
                       final Long commitMs,
                       final Long fileSize) {
        JooqUtil.context(indexDbConnProvider, context -> context
                .update(INDEX_SHARD)
                .set(INDEX_SHARD.DOCUMENT_COUNT, documentCount)
                .set(INDEX_SHARD.COMMIT_DOCUMENT_COUNT, 0)
                .set(INDEX_SHARD.COMMIT_DURATION_MS, commitDurationMs)
                .set(INDEX_SHARD.COMMIT_MS, commitMs)
                .set(INDEX_SHARD.FILE_SIZE, fileSize)
                .where(INDEX_SHARD.ID.eq(id))
                .and(INDEX_SHARD.DOCUMENT_COUNT.ne(documentCount)
                        .or(INDEX_SHARD.FILE_SIZE.ne(fileSize))
                )
                .execute());
    }

    private boolean isUsed(final Set<String> fieldSet,
                           final String[] fields,
                           final ExpressionCriteria criteria) {
        return Arrays.stream(fields).filter(Objects::nonNull).anyMatch(fieldSet::contains) ||
               ExpressionUtil.termCount(criteria.getExpression(), fieldSet) > 0;
    }


    // ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~


    @Singleton
    private static class IndexShardValueMapper {

        private final ValueMapper valueMapper;
        private final DocFinder docFinder;

        @Inject
        private IndexShardValueMapper(final ValueMapper valueMapper,
                                      final DocFinder docFinder) {
            this.valueMapper = valueMapper;
            this.docFinder = docFinder;

            valueMapper.map(IndexShardFields.FIELD_NODE, INDEX_SHARD.NODE_NAME, ValString::create);
            valueMapper.map(IndexShardFields.FIELD_INDEX, INDEX_SHARD.INDEX_UUID, this::getDocRefName);
            valueMapper.map(IndexShardFields.FIELD_INDEX_NAME, INDEX_SHARD.INDEX_UUID, this::getDocRefName);
            valueMapper.map(IndexShardFields.FIELD_PARTITION, INDEX_SHARD.PARTITION_NAME, ValString::create);
            valueMapper.map(IndexShardFields.FIELD_DOC_COUNT, INDEX_SHARD.DOCUMENT_COUNT, ValInteger::create);
            valueMapper.map(IndexShardFields.FIELD_FILE_SIZE, INDEX_SHARD.FILE_SIZE, ValLong::create);
            valueMapper.map(IndexShardFields.FIELD_STATUS, INDEX_SHARD.STATUS, this::getStatus);
            valueMapper.map(IndexShardFields.FIELD_LAST_COMMIT, INDEX_SHARD.COMMIT_MS, ValDate::create);
            valueMapper.map(IndexShardFields.FIELD_VOLUME_PATH, INDEX_VOLUME.PATH, ValString::create);
            valueMapper.map(IndexShardFields.FIELD_VOLUME_GROUP, INDEX_VOLUME_GROUP.NAME, ValString::create);
            valueMapper.map(IndexShardFields.FIELD_SHARD_ID, INDEX_SHARD.ID, ValLong::create);
            valueMapper.map(IndexShardFields.FIELD_INDEX_VERSION, INDEX_SHARD.INDEX_VERSION, ValString::create);
        }

        private Val getDocRefName(final String uuid) {
            String val = uuid;
            if (docFinder != null) {
                val = docFinder.getName(new DocRef(LuceneIndexDoc.TYPE, uuid))
                        .orElse(uuid);
            }
            return ValString.create(val);
        }

        private Val getStatus(final byte statusPrimitive) {
            final IndexShardStatus indexShardStatus = IndexShardStatus.PRIMITIVE_VALUE_CONVERTER.fromPrimitiveValue(
                    statusPrimitive);
            return ValString.create(indexShardStatus.getDisplayValue());
        }

        public List<Field<?>> getDbFieldsByName(final String[] fieldNames) {
            return valueMapper.getDbFieldsByNameAsList(fieldNames);
        }

        public Mapper<?>[] getMappersForFieldNames(final String[] fieldNames) {
            return valueMapper.getMappersForFieldNames(fieldNames);
        }
    }


    // ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~


    @Singleton
    private static class IndexShardExpressionMapper {

        private final ExpressionMapper expressionMapper;
        private final DocFinder docFinder;

        @Inject
        private IndexShardExpressionMapper(final ExpressionMapperFactory expressionMapperFactory,
                                           final DocFinder docFinder) {
            this.docFinder = docFinder;

            this.expressionMapper = expressionMapperFactory.create();
            expressionMapper.map(IndexShardFields.FIELD_NODE, INDEX_SHARD.NODE_NAME, value -> value);
            expressionMapper.map(IndexShardFields.FIELD_INDEX, INDEX_SHARD.INDEX_UUID, value ->
                    value, false);
            expressionMapper.multiMap(IndexShardFields.FIELD_INDEX_NAME, INDEX_SHARD.INDEX_UUID,
                    this::getIndexUuids, true);
            expressionMapper.map(IndexShardFields.FIELD_PARTITION, INDEX_SHARD.PARTITION_NAME, value -> value);
            expressionMapper.map(IndexShardFields.FIELD_DOC_COUNT, INDEX_SHARD.DOCUMENT_COUNT, Integer::valueOf);
            expressionMapper.map(IndexShardFields.FIELD_FILE_SIZE, INDEX_SHARD.FILE_SIZE, Long::valueOf);
            expressionMapper.map(IndexShardFields.FIELD_STATUS, INDEX_SHARD.STATUS, value ->
                    IndexShardStatus.fromDisplayValue(value).getPrimitiveValue());
            expressionMapper.map(IndexShardFields.FIELD_LAST_COMMIT, INDEX_SHARD.COMMIT_MS, value ->
                    DateExpressionParser.getMs(IndexShardFields.FIELD_LAST_COMMIT.getFldName(), value));
            expressionMapper.map(IndexShardFields.FIELD_VOLUME_PATH, INDEX_VOLUME.PATH, value -> value);
            expressionMapper.map(IndexShardFields.FIELD_VOLUME_GROUP, INDEX_VOLUME_GROUP.NAME, value -> value);
            expressionMapper.map(IndexShardFields.FIELD_SHARD_ID, INDEX_SHARD.ID, Long::valueOf);
            expressionMapper.map(IndexShardFields.FIELD_INDEX_VERSION, INDEX_SHARD.INDEX_VERSION, value -> value);
        }

        private List<String> getIndexUuids(final List<String> indexNames) {
            return docFinder.findByNames(LuceneIndexDoc.TYPE, indexNames, true)
                    .stream()
                    .map(DocRef::getUuid)
                    .collect(Collectors.toList());
        }

        public Condition apply(final ExpressionItem expressionItem) {
            return expressionMapper.apply(expressionItem);
        }
    }
}
