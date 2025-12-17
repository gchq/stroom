/*
 * Copyright 2016-2025 Crown Copyright
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

package stroom.index.mock;

import stroom.entity.shared.ExpressionCriteria;
import stroom.index.impl.IndexShardDao;
import stroom.index.shared.FindIndexShardCriteria;
import stroom.index.shared.IndexShard;
import stroom.index.shared.IndexShard.IndexShardStatus;
import stroom.index.shared.IndexShardKey;
import stroom.index.shared.IndexVolume;
import stroom.index.shared.Partition;
import stroom.query.language.functions.FieldIndex;
import stroom.query.language.functions.ValuesConsumer;
import stroom.util.shared.ResultPage;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

public class MockIndexShardDao implements IndexShardDao {

    private final Map<Long, IndexShard> map = new HashMap<>();
    private final AtomicLong generatedId = new AtomicLong();

    @Override
    public Optional<IndexShard> fetch(final long id) {
        return Optional.ofNullable(map.get(id));
    }

    @Override
    public ResultPage<IndexShard> find(final FindIndexShardCriteria criteria) {
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

        return ResultPage.createUnboundedList(results);
    }

    @Override
    public void search(final ExpressionCriteria criteria,
                       final FieldIndex fieldIndex,
                       final ValuesConsumer consumer) {

    }

    @Override
    public IndexShard create(final IndexShardKey key,
                             final IndexVolume indexVolume,
                             final String ownerNodeName,
                             final String indexVersion) {
        final Partition partition = key.getPartition();
        final IndexShard indexShard = new IndexShard();
        indexShard.setId(generatedId.incrementAndGet());
        indexShard.setIndexUuid(key.getIndexUuid());
        indexShard.setNodeName(ownerNodeName);
        indexShard.setPartition(partition.getLabel());
        indexShard.setPartitionFromTime(partition.getPartitionFromTime());
        indexShard.setPartitionToTime(partition.getPartitionToTime());
        indexShard.setVolume(indexVolume);
        indexShard.setIndexVersion(indexVersion);

        map.put(indexShard.getId(), indexShard);

        return indexShard;
    }

    @Override
    public boolean delete(final Long id) {
        return map.remove(id) != null;
    }

    @Override
    public boolean setStatus(final Long id, final IndexShardStatus status) {
        final IndexShard indexShard = map.get(id);
        if (null != indexShard) {
            indexShard.setStatus(status);
            return true;
        }
        return false;
    }

    @Override
    public void logicalDelete(final Long id) {
        final IndexShard indexShard = map.get(id);
        if (null != indexShard) {
            indexShard.setStatus(IndexShardStatus.DELETED);
        }
    }

    @Override
    public void reset(final Long id) {
        final IndexShard indexShard = map.get(id);
        if (null != indexShard) {
            indexShard.setStatus(IndexShardStatus.CLOSED);
        }
    }

    @Override
    public void update(final Long id,
                       final Integer documentCount,
                       final Long commitDurationMs,
                       final Long commitMs,
                       final Long fileSize) {

    }

    public long getMaxId() {
        return generatedId.get();
    }
}
