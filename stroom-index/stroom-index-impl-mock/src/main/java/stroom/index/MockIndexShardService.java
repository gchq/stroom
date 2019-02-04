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

import stroom.docref.DocRef;
import stroom.entity.shared.BaseResultList;
import stroom.index.service.IndexShardService;
import stroom.index.shared.FindIndexShardCriteria;
import stroom.index.shared.IndexDoc;
import stroom.index.shared.IndexShard;
import stroom.index.shared.IndexShardKey;
import stroom.index.shared.IndexVolume;
import stroom.util.io.FileUtil;

import javax.inject.Singleton;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Singleton
public class MockIndexShardService
        implements IndexShardService {

    protected final Map<Object, IndexShard> map = new ConcurrentHashMap<>();
    private final AtomicLong currentId = new AtomicLong();

    @Override
    public IndexShard createIndexShard(final IndexShardKey indexShardKey, final String ownerNodeName) {
        final IndexShard indexShard = new IndexShard();
        indexShard.setVolume(
                new IndexVolume.Builder()
                        .nodeName(ownerNodeName)
                        .path(FileUtil.getCanonicalPath(FileUtil.getTempDir()))
                        .build());
        indexShard.setIndexUuid(indexShardKey.getIndexUuid());
        indexShard.setPartition(indexShardKey.getPartition());
        indexShard.setPartitionFromTime(indexShardKey.getPartitionFromTime());
        indexShard.setPartitionToTime(indexShardKey.getPartitionToTime());
        map.put(indexShard.getId(), indexShard);
        final Path indexPath = IndexShardUtil.getIndexPath(indexShard);
        if (Files.isDirectory(indexPath)) {
            FileUtil.deleteContents(indexPath);
        }
        return indexShard;
    }

    @Override
    public IndexShard loadById(final Long id) {
        return map.get(id);
    }

    @Override
    public BaseResultList<IndexShard> find(final FindIndexShardCriteria criteria) {
        final BaseResultList<IndexShard> results = new BaseResultList<>();
        for (final IndexShard indexShard : map.values()) {
            boolean include = true;

            if (!criteria.getVolumeIdSet().isMatch(indexShard.getVolume().getId())) {
                include = false;

            } else if (!criteria.getNodeNameSet().isMatch(indexShard.getNodeName())) {
                include = false;
            } else if (!criteria.getIndexSet().isMatch(new DocRef(IndexDoc.DOCUMENT_TYPE, indexShard.getIndexUuid()))) {
                include = false;

            } else if (!criteria.getIndexShardStatusSet().isMatch(indexShard.getStatusE())) {
                include = false;
            }

            if (include) {
                results.add(indexShard);
            }
        }

        return results;
    }

    @Override
    public Boolean delete(IndexShard entity) {
        if (map.remove(entity.getId()) != null) {
            return Boolean.TRUE;
        } else {
            return Boolean.FALSE;
        }
    }

    @Override
    public Boolean setStatus(final Long id,
                             final IndexShard.IndexShardStatus status) {
        final IndexShard indexShard = map.get(id);
        if (null != indexShard) {
            indexShard.setStatusE(status);
            return Boolean.TRUE;
        }
        return Boolean.FALSE;
    }

    @Override
    public void update(long indexShardId, Integer documentCount, Long commitDurationMs, Long commitMs, Long fileSize) {

    }
}
