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

package stroom.index.mock;

import stroom.index.impl.IndexShardService;
import stroom.index.impl.IndexShardUtil;
import stroom.index.impl.LuceneVersionUtil;
import stroom.index.shared.FindIndexShardCriteria;
import stroom.index.shared.IndexShard;
import stroom.index.shared.IndexShardKey;
import stroom.index.shared.IndexShardResultPage;
import stroom.index.shared.IndexVolume;
import stroom.util.io.FileUtil;
import stroom.util.shared.Clearable;

import javax.inject.Singleton;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@Singleton
public class MockIndexShardService
        implements IndexShardService, Clearable {

    protected final Map<Object, IndexShard> map = new ConcurrentHashMap<>();
    private final AtomicInteger indexShardsCreated;
    private final AtomicLong indexShardId;

    public MockIndexShardService() {
        this.indexShardsCreated = new AtomicInteger(0);
        this.indexShardId = new AtomicLong(0);
    }

    public MockIndexShardService( final AtomicInteger indexShardsCreated,
                                  final AtomicLong indexShardId) {
        this.indexShardsCreated = indexShardsCreated;
        this.indexShardId = indexShardId;
    }

    @Override
    public IndexShard createIndexShard(final IndexShardKey indexShardKey, final String ownerNodeName) {
        indexShardsCreated.incrementAndGet();

        // checkedLimit.increment();
        final IndexShard indexShard = new IndexShard();
        indexShard.setVolume(new IndexVolume.Builder()
                .nodeName(ownerNodeName)
                .path(FileUtil.getCanonicalPath(FileUtil.getTempDir()))
                .build());
        indexShard.setIndexUuid(indexShardKey.getIndexUuid());
        indexShard.setPartition(indexShardKey.getPartition());
        indexShard.setPartitionFromTime(indexShardKey.getPartitionFromTime());
        indexShard.setPartitionToTime(indexShardKey.getPartitionToTime());
        indexShard.setNodeName(ownerNodeName);
        indexShard.setId(indexShardId.incrementAndGet());

        indexShard.setIndexVersion(LuceneVersionUtil.getCurrentVersion());
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
    public IndexShardResultPage find(final FindIndexShardCriteria criteria) {
        final List<IndexShard> results = new ArrayList<>();
        for (final IndexShard indexShard : map.values()) {
            boolean include = true;

            if (!criteria.getVolumeIdSet().isMatch(indexShard.getVolume().getId())) {
                include = false;

            } else if (!criteria.getNodeNameSet().isMatch(indexShard.getNodeName())) {
                include = false;
            } else if (!criteria.getIndexUuidSet().isMatch(indexShard.getIndexUuid())) {
                include = false;

            } else if (!criteria.getIndexShardStatusSet().isMatch(indexShard.getStatus())) {
                include = false;
            }

            if (include) {
                results.add(indexShard);
            }
        }

        return new IndexShardResultPage(results);
    }

    @Override
    public Boolean delete(IndexShard indexShard) {
        if (map.remove(indexShard.getId()) != null) {
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
            indexShard.setStatus(status);
            return Boolean.TRUE;
        }
        return Boolean.FALSE;
    }

    @Override
    public void update(final long indexShardId,
                       final Integer documentCount,
                       final Long commitDurationMs,
                       final Long commitMs,
                       final Long fileSize) {

    }

    @Override
    public void clear() {

    }
}
