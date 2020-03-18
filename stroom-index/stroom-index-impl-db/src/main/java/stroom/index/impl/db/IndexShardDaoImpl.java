package stroom.index.impl.db;

import org.jooq.Condition;
import org.jooq.Field;
import org.jooq.OrderField;
import org.jooq.Record;
import stroom.db.util.GenericDao;
import stroom.db.util.JooqUtil;
import stroom.index.impl.IndexShardDao;
import stroom.index.impl.IndexVolumeDao;
import stroom.index.impl.db.jooq.tables.records.IndexShardRecord;
import stroom.index.impl.selection.RoundRobinVolumeSelector;
import stroom.index.shared.FindIndexShardCriteria;
import stroom.index.shared.IndexException;
import stroom.index.shared.IndexShard;
import stroom.index.shared.IndexShard.IndexShardStatus;
import stroom.index.shared.IndexShardKey;
import stroom.index.shared.IndexShardResultPage;
import stroom.index.shared.IndexVolume;
import stroom.util.shared.CriteriaSet;
import stroom.util.shared.ResultPage;

import javax.inject.Inject;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;

import static stroom.index.impl.db.jooq.Tables.INDEX_SHARD;
import static stroom.index.impl.db.jooq.tables.IndexVolume.INDEX_VOLUME;

class IndexShardDaoImpl implements IndexShardDao {
    private static final Function<Record, IndexShard> RECORD_TO_INDEX_SHARD_MAPPER = record -> {
        final IndexShard indexShard = new IndexShard();
        indexShard.setId(record.get(INDEX_SHARD.ID));
        indexShard.setPartition(record.get(INDEX_SHARD.PARTITION_NAME));
        indexShard.setPartitionFromTime(record.get(INDEX_SHARD.PARTITION_FROM_MS));
        indexShard.setPartitionToTime(record.get(INDEX_SHARD.PARTITION_TO_MS));
        indexShard.setDocumentCount(record.get(INDEX_SHARD.DOCUMENT_COUNT));
        indexShard.setCommitMs(record.get(INDEX_SHARD.COMMIT_MS));
        indexShard.setCommitDurationMs(record.get(INDEX_SHARD.COMMIT_DURATION_MS));
        indexShard.setCommitDocumentCount(record.get(INDEX_SHARD.COMMIT_DOCUMENT_COUNT));
        indexShard.setStatus(IndexShardStatus.PRIMITIVE_VALUE_CONVERTER.fromPrimitiveValue(record.get(INDEX_SHARD.STATUS)));
        indexShard.setFileSize(record.get(INDEX_SHARD.FILE_SIZE));
        indexShard.setIndexVersion(record.get(INDEX_SHARD.INDEX_VERSION));
        indexShard.setNodeName(record.get(INDEX_SHARD.NODE_NAME));
        indexShard.setIndexUuid(record.get(INDEX_SHARD.INDEX_UUID));
        return indexShard;
    };

    private static final BiFunction<IndexShard, IndexShardRecord, IndexShardRecord> INDEX_SHARD_TO_RECORD_MAPPER = (indexShard, record) -> {
        record.from(indexShard);
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

    private static Map<String, Field<?>> FIELD_MAP = new HashMap<>();

    static {
        FIELD_MAP.put(FindIndexShardCriteria.FIELD_ID, INDEX_SHARD.ID);
        FIELD_MAP.put(FindIndexShardCriteria.FIELD_PARTITION, INDEX_SHARD.PARTITION_NAME);
    }

    private final IndexDbConnProvider indexDbConnProvider;
    private final IndexVolumeDao indexVolumeDao;
    private final GenericDao<IndexShardRecord, IndexShard, Long> genericDao;
    private final RoundRobinVolumeSelector volumeSelector = new RoundRobinVolumeSelector();

    @Inject
    IndexShardDaoImpl(final IndexDbConnProvider indexDbConnProvider,
                      final IndexVolumeDao indexVolumeDao) {
        this.indexDbConnProvider = indexDbConnProvider;
        this.indexVolumeDao = indexVolumeDao;
        genericDao = new GenericDao<>(INDEX_SHARD, INDEX_SHARD.ID, IndexShard.class, indexDbConnProvider);
        genericDao.setRecordToObjectMapper(RECORD_TO_INDEX_SHARD_MAPPER);
        genericDao.setObjectToRecordMapper(INDEX_SHARD_TO_RECORD_MAPPER);
    }

    @Override
    public Optional<IndexShard> fetch(final long id) {
        return JooqUtil.contextResult(indexDbConnProvider, context -> context
                .select()
                .from(INDEX_SHARD)
                .join(INDEX_VOLUME).on(INDEX_VOLUME.ID.eq(INDEX_SHARD.FK_VOLUME_ID))
                .where(INDEX_SHARD.ID.eq(id))
                .fetchOptional()
                .map(r -> {
                    final IndexVolume indexVolume = IndexVolumeDaoImpl.RECORD_TO_INDEX_VOLUME_MAPPER.apply(r);
                    final IndexShard indexShard = RECORD_TO_INDEX_SHARD_MAPPER.apply(r);
                    indexShard.setVolume(indexVolume);
                    return indexShard;
                }));
    }

//        return JooqUtil.contextResult(connectionProvider, context ->
//         context
//                .select(
//                       INDEX_SHARD.ID,
//        INDEX_SHARD.PARTITION_NAME,
//        INDEX_SHARD.PARTITION_FROM_MS,
//        INDEX_SHARD.PARTITION_TO_MS,
//        INDEX_SHARD.DOCUMENT_COUNT,
//        INDEX_SHARD.COMMIT_MS,
//        INDEX_SHARD.COMMIT_DURATION_MS,
//        INDEX_SHARD.COMMIT_DOCUMENT_COUNT,
//        INDEX_SHARD.STATUS,
//        INDEX_SHARD.FILE_SIZE,
//        INDEX_SHARD.INDEX_VERSION,
//        INDEX_SHARD.FK_VOLUME_ID,
//        INDEX_SHARD.NODE_NAME,
//        INDEX_SHARD.INDEX_UUID,
//
//                 joi
//                .where(idField.eq(id))
//                .fetchOptional(record ->
//                        recordToObjectMapper.apply(record));
//
//        return genericDao.fetch(id).
//
//    orElse(null);


    @Override
    public IndexShardResultPage find(final FindIndexShardCriteria criteria) {
        final Collection<Condition> conditions = JooqUtil.conditions(
                JooqUtil.getRangeCondition(INDEX_SHARD.DOCUMENT_COUNT, criteria.getDocumentCountRange()),
                JooqUtil.getSetCondition(INDEX_SHARD.NODE_NAME, criteria.getNodeNameSet()),
                JooqUtil.getSetCondition(INDEX_SHARD.FK_VOLUME_ID, criteria.getVolumeIdSet()),
                JooqUtil.getSetCondition(INDEX_SHARD.ID, criteria.getIndexShardIdSet()),
                JooqUtil.getSetCondition(INDEX_SHARD.INDEX_UUID, criteria.getIndexUuidSet()),
                JooqUtil.getSetCondition(INDEX_SHARD.STATUS, CriteriaSet.convert(criteria.getIndexShardStatusSet(), IndexShard.IndexShardStatus::getPrimitiveValue)),
                JooqUtil.getStringCondition(INDEX_SHARD.PARTITION_NAME, criteria.getPartition())
        );

        final OrderField<?>[] orderFields = JooqUtil.getOrderFields(FIELD_MAP, criteria);

        final List<IndexShard> list = JooqUtil.contextResult(indexDbConnProvider, context ->
                context
                        .select()
                        .from(INDEX_SHARD)
                        .join(INDEX_VOLUME).on(INDEX_VOLUME.ID.eq(INDEX_SHARD.FK_VOLUME_ID))
                        .where(conditions)
                        .orderBy(orderFields)
                        .limit(JooqUtil.getLimit(criteria.getPageRequest()))
                        .offset(JooqUtil.getOffset(criteria.getPageRequest()))
                        .fetch()
                        .map(r -> {
                            final IndexVolume indexVolume = IndexVolumeDaoImpl.RECORD_TO_INDEX_VOLUME_MAPPER.apply(r);
                            final IndexShard indexShard = RECORD_TO_INDEX_SHARD_MAPPER.apply(r);
                            indexShard.setVolume(indexVolume);
                            return indexShard;
                        }));

        final ResultPage<IndexShard> resultPage = ResultPage.createCriterialBasedList(list, criteria);
        return new IndexShardResultPage(resultPage.getValues(), resultPage.getPageResponse());

//            shards.forEach(shard -> {
//                final IndexVolume indexVolume = context.select()
//                        .from(INDEX_VOLUME)
//                        .where(INDEX_VOLUME.ID.eq(shard.getVolumeId()))
//                        .fetchOneInto(IndexVolume.class);
//                shard.setVolume(indexVolume);
//            });
//
//            return shards;
//        });
    }

    @Override
    public IndexShard create(final IndexShardKey indexShardKey,
                             final String volumeGroupName,
                             final String ownerNodeName,
                             final String indexVersion) {
        // TODO : @66 Add some caching here. Maybe do this as part of volume selection.
        final List<IndexVolume> indexVolumes = indexVolumeDao.getVolumesInGroupOnNode(volumeGroupName, ownerNodeName);
        if (indexVolumes == null || indexVolumes.size() == 0) {
            throw new IndexException("Unable to find any index volumes for group with name " + volumeGroupName);
        }

        // TODO : @66 Add volume selection based on strategy for using least full etc, like we do for data store.
        final IndexVolume indexVolume = volumeSelector.select(indexVolumes);
        if (indexVolume == null) {
            final String msg = "No shard can be created as no volumes are available for group: " +
                    volumeGroupName +
                    " indexUuid: " +
                    indexShardKey.getIndexUuid();
            throw new IndexException(msg);
        }

        final IndexShard indexShard = new IndexShard();
        indexShard.setIndexUuid(indexShardKey.getIndexUuid());
        indexShard.setNodeName(ownerNodeName);
        indexShard.setPartition(indexShardKey.getPartition());
        indexShard.setPartitionFromTime(indexShardKey.getPartitionFromTime());
        indexShard.setPartitionToTime(indexShardKey.getPartitionToTime());
        indexShard.setVolume(indexVolume);
        indexShard.setIndexVersion(indexVersion);

        final IndexShard created = genericDao.create(indexShard);
        created.setVolume(indexVolume);

        return created;

//        return JooqUtil.contextResult(connectionProvider, context -> {
//            final Long id = context.insertInto(INDEX_SHARD,
//                    INDEX_SHARD.INDEX_UUID,
//                    INDEX_SHARD.NODE_NAME,
//                    INDEX_SHARD.PARTITION_NAME,
//                    INDEX_SHARD.PARTITION_FROM_MS,
//                    INDEX_SHARD.PARTITION_TO_MS,
//                    INDEX_SHARD.FK_VOLUME_ID,
//                    INDEX_SHARD.INDEX_VERSION,
//                    INDEX_SHARD.STATUS
//            )
//                    .values(indexShardKey.getIndexUuid(),
//                            ownerNodeName,
//                            indexShardKey.getPartition(),
//                            indexShardKey.getPartitionFromTime(),
//                            indexShardKey.getPartitionToTime(),
//                            indexVolume.getId(),
//                            indexVersion,
//                            IndexShard.IndexShardStatus.OPEN.getPrimitiveValue())
//                    .returning(INDEX_VOLUME.ID)
//                    .fetchOne()
//                    .getId();
//
//            final IndexShard result = context.select()
//                    .from(INDEX_SHARD)
//                    .where(INDEX_SHARD.ID.eq(id))
//                    .fetchOneInto(IndexShard.class);
//
//            result.setVolume(indexVolume);
//
//            return result;
//        });
    }

    @Override
    public void delete(final Long id) {
        genericDao.delete(id);

//        JooqUtil.context(connectionProvider, context -> context
//                .deleteFrom(INDEX_SHARD)
//                .where(INDEX_SHARD.ID.eq(id))
//                .execute());
    }

    @Override
    public void setStatus(final Long id,
                          final IndexShard.IndexShardStatus status) {
        JooqUtil.context(indexDbConnProvider, context -> context
                .update(INDEX_SHARD)
                .set(INDEX_SHARD.STATUS, status.getPrimitiveValue())
                .where(INDEX_SHARD.ID.eq(id))
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
                .execute());
    }

//    @Override
//    public void appendCriteria(final List<BaseAdvancedQueryItem> items, final FindIndexShardCriteria criteria) {
//        CriteriaLoggingUtil.appendRangeTerm(items, "documentCountRange", criteria.getDocumentCountRange());
//        //TODO include these when converting to jOOQ
//        //CriteriaLoggingUtil.appendEntityIdSet(items, "nodeIdSet", criteria.getNodeIdSet());
//        //CriteriaLoggingUtil.appendEntityIdSet(items, "volumeIdSet", criteria.getVolumeIdSet());
//        CriteriaLoggingUtil.appendEntityIdSet(items, "indexIdSet", criteria.getIndexShardIdSet());
//        CriteriaLoggingUtil.appendCriteriaSet(items, "indexShardStatusSet", criteria.getIndexShardStatusSet());
//        CriteriaLoggingUtil.appendStringTerm(items, "partition", criteria.getPartition().getString());
//
//        super.appendCriteria(items, criteria);
//    }
//    private static class IndexShardQueryAppender extends QueryAppender<IndexShard, FindIndexShardCriteria> {
//        IndexShardQueryAppender(final StroomEntityManager entityManager) {
//            super(entityManager);
//        }
//
//        @Override
//        protected void appendBasicJoin(final HqlBuilder sql, final String alias, final Set<String> fetchSet) {
//            super.appendBasicJoin(sql, alias, fetchSet);
//            if (fetchSet != null) {
//                if (fetchSet.contains(Node.ENTITY_TYPE)) {
//                    sql.append(" INNER JOIN FETCH " + alias + ".node");
//                }
//                if (fetchSet.contains(VolumeEntity.ENTITY_TYPE)) {
//                    sql.append(" INNER JOIN FETCH " + alias + ".volume");
//                }
//            }
//        }
//
//        @Override
//        protected void appendBasicCriteria(final HqlBuilder sql, final String alias,
//                                           final FindIndexShardCriteria criteria) {
//            super.appendBasicCriteria(sql, alias, criteria);
//
//            sql.appendDocRefSetQuery(alias + ".indexUuid", criteria.getIndexSet());
//            sql.appendEntityIdSetQuery(alias, criteria.getIndexShardIdSet());
//            // TODO include these in jOOQ
//            //sql.appendPrimitiveValueSetQuery(alias + ".node", criteria.getNodeIdSet());
//            //sql.appendEntityIdSetQuery(alias + ".volume", criteria.getVolumeIdSet());
//            sql.appendPrimitiveValueSetQuery(alias + ".pstatus", criteria.getIndexShardStatusSet());
//            sql.appendRangeQuery(alias + ".documentCount", criteria.getDocumentCountRange());
//            sql.appendValueQuery(alias + ".partition", criteria.getPartition());
//        }
//    }
}
