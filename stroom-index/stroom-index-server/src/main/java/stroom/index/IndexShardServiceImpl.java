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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.docref.DocRef;
import stroom.index.dao.IndexShardDao;
import stroom.index.service.IndexShardService;
import stroom.index.service.IndexVolumeService;

import stroom.index.shared.IndexShardKey;
import stroom.util.shared.PermissionException;
import stroom.index.shared.FindIndexShardCriteria;
import stroom.index.shared.IndexDoc;
import stroom.index.shared.IndexShard;
import stroom.security.Security;
import stroom.security.SecurityContext;
import stroom.security.shared.DocumentPermissionNames;
import stroom.security.shared.PermissionNames;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.List;

@Singleton
public class IndexShardServiceImpl implements IndexShardService {
    private static final Logger LOGGER = LoggerFactory.getLogger(IndexShardServiceImpl.class);

    private static final String VOLUME_ERROR = "One or more volumes must been assigned to an index for a shard to be created";

    private final Security security;
    private final IndexShardDao indexShardDao;
    private final IndexVolumeService indexVolumeService;
    private final IndexStructureCache indexStructureCache;
    private final SecurityContext securityContext;

    @Inject
    IndexShardServiceImpl(final Security security,
                          final IndexShardDao indexShardDao,
                          final IndexVolumeService indexVolumeService,
                          final IndexStructureCache indexStructureCache,
                          final SecurityContext securityContext) {
        this.security = security;
        this.indexShardDao = indexShardDao;
        this.indexVolumeService = indexVolumeService;
        this.indexStructureCache = indexStructureCache;
        this.securityContext = securityContext;
    }

    @Override
    public IndexShard loadById(final Long id) {
        return security.secureResult(PermissionNames.MANAGE_INDEX_SHARDS_PERMISSION,
                () -> indexShardDao.loadById(id));
    }

    @Override
    public List<IndexShard> find(final FindIndexShardCriteria criteria) {
        return null;
    }

    @Override
    public IndexShard createIndexShard(final IndexShardKey indexShardKey,
                                       final String ownerNodeName) {

        return security.secureResult(PermissionNames.MANAGE_INDEX_SHARDS_PERMISSION,
            () -> {
                final IndexStructure indexStructure = indexStructureCache.get(new DocRef(IndexDoc.DOCUMENT_TYPE, indexShardKey.getIndexUuid()));
                final IndexDoc index = indexStructure.getIndex();

                return indexShardDao.create(indexShardKey, index.getVolumeGroupName(), ownerNodeName, LuceneVersionUtil.getCurrentVersion());
            });
    }

    @Override
    public Boolean delete(final IndexShard entity) {
        return security.secureResult(PermissionNames.MANAGE_INDEX_SHARDS_PERMISSION, () -> {
            if (!securityContext.hasDocumentPermission(IndexDoc.DOCUMENT_TYPE, entity.getIndexUuid(), DocumentPermissionNames.DELETE)) {
                throw new PermissionException(securityContext.getUserId(), "You do not have permission to delete index shard");
            }

            indexShardDao.delete(entity.getId());

            return Boolean.TRUE;
        });
    }

    @Override
    public Boolean setStatus(Long id, IndexShard.IndexShardStatus status) {
        return null;
    }

    @Override
    public void update(long indexShardId, Integer documentCount, Long commitDurationMs, Long commitMs, Long fileSize) {

    }
}
