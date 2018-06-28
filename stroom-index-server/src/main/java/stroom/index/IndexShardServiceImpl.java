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
 *
 */

package stroom.index;

import event.logging.BaseAdvancedQueryItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.docstore.shared.DocRefUtil;
import stroom.entity.CriteriaLoggingUtil;
import stroom.entity.QueryAppender;
import stroom.entity.StroomEntityManager;
import stroom.entity.SystemEntityServiceImpl;
import stroom.entity.shared.PermissionException;
import stroom.entity.util.FieldMap;
import stroom.entity.util.HqlBuilder;
import stroom.index.shared.FindIndexShardCriteria;
import stroom.index.shared.IndexDoc;
import stroom.index.shared.IndexShard;
import stroom.index.shared.IndexShardKey;
import stroom.node.VolumeService;
import stroom.node.shared.Node;
import stroom.node.shared.Volume;
import stroom.docref.DocRef;
import stroom.security.Security;
import stroom.security.SecurityContext;
import stroom.security.shared.DocumentPermissionNames;
import stroom.security.shared.PermissionNames;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.List;
import java.util.Set;

@Singleton
public class IndexShardServiceImpl
        extends SystemEntityServiceImpl<IndexShard, FindIndexShardCriteria> implements IndexShardService {
    private static final Logger LOGGER = LoggerFactory.getLogger(IndexShardServiceImpl.class);

    private static final String VOLUME_ERROR = "One or more volumes must been assigned to an index for a shard to be created";

    private final Security security;
    private final VolumeService volumeService;
    private final IndexVolumeService indexVolumeService;
    private final IndexConfigCache indexConfigCache;
    private final SecurityContext securityContext;

    @Inject
    IndexShardServiceImpl(final StroomEntityManager entityManager,
                          final Security security,
                          final VolumeService volumeService,
                          final IndexVolumeService indexVolumeService,
                          final IndexConfigCache indexConfigCache,
                          final SecurityContext securityContext) {
        super(entityManager, security);
        this.security = security;
        this.volumeService = volumeService;
        this.indexVolumeService = indexVolumeService;
        this.indexConfigCache = indexConfigCache;
        this.securityContext = securityContext;
    }

    @Override
    public IndexShard createIndexShard(final IndexShardKey indexShardKey, final Node ownerNode) {
        final IndexConfig indexConfig = indexConfigCache.get(new DocRef(IndexDoc.DOCUMENT_TYPE, indexShardKey.getIndexUuid()));
        final IndexDoc index = indexConfig.getIndex();
        final Set<Volume> allowedVolumes = indexVolumeService.getVolumesForIndex(DocRefUtil.create(index));
        if (allowedVolumes == null || allowedVolumes.size() == 0) {
            LOGGER.debug(VOLUME_ERROR);
            throw new IndexException(VOLUME_ERROR);
        }

        final Set<Volume> volumes = volumeService.getIndexVolumeSet(ownerNode, allowedVolumes);

        // The first set should be a set of cache volumes unless no caches have
        // been defined or they are full.
        Volume volume = null;

        if (volumes != null && volumes.size() > 0) {
            volume = volumes.iterator().next();
        }
        if (volume == null) {
            throw new IndexException("No shard can be created as no volumes are available for index: " + index.getName()
                    + " (" + index.getUuid() + ")");
        }

        final IndexShard indexShard = new IndexShard();
        indexShard.setIndexUuid(index.getUuid());
        indexShard.setNode(ownerNode);
        indexShard.setPartition(indexShardKey.getPartition());
        indexShard.setPartitionFromTime(indexShardKey.getPartitionFromTime());
        indexShard.setPartitionToTime(indexShardKey.getPartitionToTime());
        indexShard.setVolume(volume);
        indexShard.setIndexVersion(LuceneVersionUtil.getCurrentVersion());

        return save(indexShard);
    }

    @Override
    public Class<IndexShard> getEntityClass() {
        return IndexShard.class;
    }

    @Override
    public FindIndexShardCriteria createCriteria() {
        return new FindIndexShardCriteria();
    }

    @Override
    public void appendCriteria(final List<BaseAdvancedQueryItem> items, final FindIndexShardCriteria criteria) {
        CriteriaLoggingUtil.appendRangeTerm(items, "documentCountRange", criteria.getDocumentCountRange());
        CriteriaLoggingUtil.appendEntityIdSet(items, "nodeIdSet", criteria.getNodeIdSet());
        CriteriaLoggingUtil.appendEntityIdSet(items, "volumeIdSet", criteria.getVolumeIdSet());
        CriteriaLoggingUtil.appendEntityIdSet(items, "indexIdSet", criteria.getIndexShardSet());
        CriteriaLoggingUtil.appendCriteriaSet(items, "indexShardStatusSet", criteria.getIndexShardStatusSet());
        CriteriaLoggingUtil.appendStringTerm(items, "partition", criteria.getPartition().getString());

        super.appendCriteria(items, criteria);
    }

    @Override
    public Boolean delete(final IndexShard entity) {
        return security.secureResult(PermissionNames.MANAGE_INDEX_SHARDS_PERMISSION, () -> {
            if (!securityContext.hasDocumentPermission(IndexDoc.DOCUMENT_TYPE, entity.getIndexUuid(), DocumentPermissionNames.DELETE)) {
                throw new PermissionException(securityContext.getUserId(), "You do not have permission to delete index shard");
            }

            return super.delete(entity);
        });
    }

    @Override
    protected QueryAppender<IndexShard, FindIndexShardCriteria> createQueryAppender(StroomEntityManager entityManager) {
        return new IndexShardQueryAppender(entityManager);
    }

    @Override
    protected FieldMap createFieldMap() {
        return super.createFieldMap()
                .add(FindIndexShardCriteria.FIELD_PARTITION, IndexShard.PARTITION, "partition");
    }

    @Override
    protected String permission() {
        return null;
    }

    private static class IndexShardQueryAppender extends QueryAppender<IndexShard, FindIndexShardCriteria> {
        IndexShardQueryAppender(final StroomEntityManager entityManager) {
            super(entityManager);
        }

        @Override
        protected void appendBasicJoin(final HqlBuilder sql, final String alias, final Set<String> fetchSet) {
            super.appendBasicJoin(sql, alias, fetchSet);
            if (fetchSet != null) {
                if (fetchSet.contains(Node.ENTITY_TYPE)) {
                    sql.append(" INNER JOIN FETCH " + alias + ".node");
                }
                if (fetchSet.contains(Volume.ENTITY_TYPE)) {
                    sql.append(" INNER JOIN FETCH " + alias + ".volume");
                }
            }
        }

        @Override
        protected void appendBasicCriteria(final HqlBuilder sql, final String alias,
                                           final FindIndexShardCriteria criteria) {
            super.appendBasicCriteria(sql, alias, criteria);

            sql.appendDocRefSetQuery(alias + ".indexUuid", criteria.getIndexSet());
            sql.appendEntityIdSetQuery(alias, criteria.getIndexShardSet());
            sql.appendEntityIdSetQuery(alias + ".node", criteria.getNodeIdSet());
            sql.appendEntityIdSetQuery(alias + ".volume", criteria.getVolumeIdSet());
            sql.appendPrimitiveValueSetQuery(alias + ".pstatus", criteria.getIndexShardStatusSet());
            sql.appendRangeQuery(alias + ".documentCount", criteria.getDocumentCountRange());
            sql.appendValueQuery(alias + ".partition", criteria.getPartition());
        }
    }
}
