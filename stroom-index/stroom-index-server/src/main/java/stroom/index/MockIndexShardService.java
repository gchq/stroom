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

import stroom.entity.MockEntityService;
import stroom.entity.shared.BaseResultList;
import stroom.index.shared.FindIndexShardCriteria;
import stroom.index.shared.IndexDoc;
import stroom.index.shared.IndexShard;
import stroom.index.shared.IndexShardKey;
import stroom.node.shared.Node;
import stroom.node.shared.VolumeEntity;
import stroom.node.shared.VolumeEntity.VolumeType;
import stroom.docref.DocRef;
import stroom.util.io.FileUtil;

import javax.inject.Singleton;
import java.nio.file.Files;
import java.nio.file.Path;

@Singleton
public class MockIndexShardService extends MockEntityService<IndexShard, FindIndexShardCriteria>
        implements IndexShardService {
    @Override
    public IndexShard createIndexShard(final IndexShardKey indexShardKey, final Node ownerNode) {
        final IndexShard indexShard = new IndexShard();
        indexShard.setVolume(
                VolumeEntity.create(ownerNode, FileUtil.getCanonicalPath(FileUtil.getTempDir()), VolumeType.PUBLIC));
        indexShard.setIndexUuid(indexShardKey.getIndexUuid());
        indexShard.setPartition(indexShardKey.getPartition());
        indexShard.setPartitionFromTime(indexShardKey.getPartitionFromTime());
        indexShard.setPartitionToTime(indexShardKey.getPartitionToTime());
        final IndexShard il = save(indexShard);
        final Path indexPath = IndexShardUtil.getIndexPath(indexShard);
        if (Files.isDirectory(indexPath)) {
            FileUtil.deleteContents(indexPath);
        }
        return il;
    }

    @Override
    public BaseResultList<IndexShard> find(final FindIndexShardCriteria criteria) {
        final BaseResultList<IndexShard> results = new BaseResultList<>();
        for (final IndexShard indexShard : map.values()) {
            boolean include = true;

            if (!criteria.getVolumeIdSet().isMatch(indexShard.getVolume())) {
                include = false;

            } else if (!criteria.getNodeIdSet().isMatch(indexShard.getNode())) {
                include = false;
            } else if (!criteria.getIndexSet().isMatch(new DocRef(IndexDoc.DOCUMENT_TYPE, indexShard.getIndexUuid()))) {
                include = false;

            } else if (!criteria.getIndexShardStatusSet().isMatch(indexShard.getStatus())) {
                include = false;
            }

            if (include) {
                results.add(indexShard);
            }
        }

        return results;
    }

    @Override
    public Class<IndexShard> getEntityClass() {
        return IndexShard.class;
    }
}
