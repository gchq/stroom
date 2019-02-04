package stroom.index.impl.db;

import stroom.db.util.JooqUtil;
import stroom.index.dao.IndexShardDao;
import stroom.index.dao.IndexVolumeDao;
import stroom.index.shared.FindIndexShardCriteria;
import stroom.index.shared.IndexException;
import stroom.index.shared.IndexShard;
import stroom.index.shared.IndexShardKey;
import stroom.index.shared.IndexVolume;
import stroom.security.SecurityContext;

import javax.inject.Inject;
import java.util.Collections;
import java.util.List;
import java.util.NoSuchElementException;

import static stroom.index.impl.db.Tables.INDEX_SHARD;
import static stroom.index.impl.db.tables.IndexVolume.INDEX_VOLUME;

public class IndexShardDaoImpl implements IndexShardDao {

    private final ConnectionProvider connectionProvider;
    private final SecurityContext securityContext;
    private final IndexVolumeDao indexVolumeDao;

    @Inject
    public IndexShardDaoImpl(final ConnectionProvider connectionProvider,
                             final SecurityContext securityContext,
                             final IndexVolumeDao indexVolumeDao) {
        this.connectionProvider = connectionProvider;
        this.securityContext = securityContext;
        this.indexVolumeDao = indexVolumeDao;
    }

    @Override
    public IndexShard loadById(final Long id) {
        return JooqUtil.contextResult(connectionProvider, context -> context.select()
                .from(INDEX_SHARD)
                .where(INDEX_SHARD.ID.eq(id))
                .fetchOneInto(IndexShard.class)
        );
    }

    @Override
    public List<IndexShard> find(final FindIndexShardCriteria criteria) {
        return Collections.emptyList();
    }

    @Override
    public IndexShard create(final IndexShardKey indexShardKey,
                             final String volumeGroupName,
                             final String ownerNodeName,
                             final String indexVersion) {
        final List<IndexVolume> indexVolumes = indexVolumeDao.getVolumesInGroupOnNode(volumeGroupName, ownerNodeName);

        IndexVolume indexVolume;
        try {
            indexVolume = indexVolumes.iterator().next();
        } catch (NoSuchElementException e) {
            final String msg = String.format("No shard can be created as no volumes are available for group: %s, indexUuid: %s ",
                    volumeGroupName,
                    indexShardKey.getIndexUuid());
            throw new IndexException(msg);
        }

        return JooqUtil.contextResult(connectionProvider, context -> {
            final Long id = context.insertInto(INDEX_SHARD,
                    INDEX_SHARD.INDEX_UUID,
                    INDEX_SHARD.NODE_NAME,
                    INDEX_SHARD.PARTITION,
                    INDEX_SHARD.PARTITION_FROM_MS,
                    INDEX_SHARD.PARTITION_TO_MS,
                    INDEX_SHARD.FK_VOLUME_ID,
                    INDEX_SHARD.INDEX_VERSION,
                    INDEX_SHARD.STATUS
            )
                    .values(indexShardKey.getIndexUuid(),
                            ownerNodeName,
                            indexShardKey.getPartition(),
                            indexShardKey.getPartitionFromTime(),
                            indexShardKey.getPartitionToTime(),
                            indexVolume.getId(),
                            indexVersion,
                            IndexShard.IndexShardStatus.OPEN.getPrimitiveValue())
                    .returning(INDEX_VOLUME.ID)
                    .fetchOne()
                    .getId();

            return context.select()
                    .from(INDEX_SHARD)
                    .where(INDEX_SHARD.ID.eq(id))
                    .fetchOneInto(IndexShard.class);
        });
    }

    @Override
    public void delete(final Long id) {
        JooqUtil.context(connectionProvider, context -> context
                .deleteFrom(INDEX_SHARD)
                .where(INDEX_SHARD.ID.eq(id))
                .execute());
    }

    @Override
    public void setStatus(final Long id,
                          final IndexShard.IndexShardStatus status) {
        JooqUtil.context(connectionProvider, context -> context
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
        JooqUtil.context(connectionProvider, context -> context
                .update(INDEX_SHARD)
                .set(INDEX_SHARD.COMMIT_DOC_COUNT, documentCount)
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
