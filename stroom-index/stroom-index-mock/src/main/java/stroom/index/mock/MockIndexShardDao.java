/*
 * Copyright 2024 Crown Copyright
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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

public class MockIndexShardDao implements IndexShardDao {

    private final Map<Long, IndexShard> map = new HashMap<>();
    private final AtomicLong generatedId = new AtomicLong();
    // The process that holds the lease on each leased shard.
    private final Map<Long, String> leases = new HashMap<>();
    // The shards whose last writer stopped without a final commit.
    private final Set<Long> needRecovery = new HashSet<>();

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
        final IndexShard indexShard = IndexShard
                .builder()
                .id(generatedId.incrementAndGet())
                .indexUuid(key.getIndexUuid())
                .nodeName(ownerNodeName)
                .partition(partition.getLabel())
                .partitionFromTime(partition.getPartitionFromTime())
                .partitionToTime(partition.getPartitionToTime())
                .volume(indexVolume)
                .indexVersion(indexVersion)
                .build();

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
        if (indexShard != null) {
            map.put(id, indexShard.copy().status(status).build());
            return true;
        }
        return false;
    }

    @Override
    public synchronized boolean claimLease(final long id,
                                           final String nodeName,
                                           final String instanceId,
                                           final long leaseDurationMs) {
        final IndexShard indexShard = map.get(id);
        if (indexShard == null
            || leases.containsKey(id)
            || !(IndexShardStatus.CLOSED.equals(indexShard.getStatus())
                 || IndexShardStatus.NEW.equals(indexShard.getStatus()))) {
            return false;
        }
        leases.put(id, instanceId);
        map.put(id, indexShard.copy().nodeName(nodeName).status(IndexShardStatus.OPENING).build());
        return true;
    }

    @Override
    public synchronized int renewLeases(final String instanceId, final long leaseDurationMs) {
        return findLeasedShardIds(instanceId).size();
    }

    @Override
    public synchronized Set<Long> findLeasedShardIds(final String instanceId) {
        return leases.entrySet()
                .stream()
                .filter(entry -> entry.getValue().equals(instanceId))
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
    }

    @Override
    public synchronized boolean needsPendingStreamRecovery(final long id) {
        return needRecovery.contains(id);
    }

    @Override
    public int releaseLapsedLeases(final long graceMs, final long replacedCommitRetentionMs) {
        // Leases here have no expiry time.
        return 0;
    }

    @Override
    public synchronized void setNeedsPendingStreamRecovery(final long id) {
        if (!leases.containsKey(id)) {
            needRecovery.add(id);
        }
    }

    @Override
    public synchronized void clearPendingStreamRecovery(final long id) {
        needRecovery.remove(id);
    }

    @Override
    public synchronized List<IndexShard> findClosedShardsNeedingPendingStreamRecovery(final int limit) {
        return map.values()
                .stream()
                .filter(indexShard -> needRecovery.contains(indexShard.getId()))
                .filter(indexShard -> IndexShardStatus.CLOSED.equals(indexShard.getStatus()))
                .limit(limit)
                .toList();
    }

    @Override
    public synchronized boolean releaseLease(final long id,
                                             final String instanceId,
                                             final Long pruneCommitsDelayMs,
                                             final boolean recoverPendingStreams) {
        if (recoverPendingStreams && instanceId.equals(leases.get(id))) {
            needRecovery.add(id);
        }
        if (!instanceId.equals(leases.get(id))) {
            return false;
        }
        leases.remove(id);
        final IndexShard indexShard = map.get(id);
        if (indexShard != null
            && (IndexShardStatus.OPENING.equals(indexShard.getStatus())
                || IndexShardStatus.OPEN.equals(indexShard.getStatus())
                || IndexShardStatus.CLOSING.equals(indexShard.getStatus()))) {
            map.put(id, indexShard.copy().status(IndexShardStatus.CLOSED).build());
        }
        return true;
    }

    @Override
    public synchronized List<IndexShard> findUnleasedShardsOnSharedVolumes(final String indexUuid,
                                                                           final String partitionLabel) {
        return map.values()
                .stream()
                .filter(indexShard -> indexShard.getVolume() != null && indexShard.getVolume().isShared())
                .filter(indexShard -> IndexShardStatus.CLOSED.equals(indexShard.getStatus()))
                .filter(indexShard -> !leases.containsKey(indexShard.getId()))
                .filter(indexShard -> Objects.equals(indexShard.getIndexUuid(), indexUuid))
                .filter(indexShard -> Objects.equals(indexShard.getPartition(), partitionLabel))
                .toList();
    }

    @Override
    public List<IndexShard> findShardsWithCommitsToPrune(final int limit) {
        return List.of();
    }

    @Override
    public void clearPruneCommitsTime(final long id) {
        // Prune times are not held.
    }

    @Override
    public void logicalDelete(final Long id) {
        setStatus(id, IndexShardStatus.DELETED);
    }

    @Override
    public synchronized boolean isLeased(final long id) {
        return leases.containsKey(id);
    }

    @Override
    public synchronized void reset(final Long id, final boolean recoverPendingStreams) {
        if (!leases.containsKey(id)) {
            setStatus(id, IndexShardStatus.CLOSED);
            if (recoverPendingStreams) {
                needRecovery.add(id);
            }
        }
    }

    @Override
    public synchronized void resetAfterRestart(final Long id, final String nodeName, final String instanceId) {
        final String holder = leases.get(id);
        final IndexShard indexShard = map.get(id);
        final boolean heldUnderThisNodesName = indexShard != null && nodeName.equals(indexShard.getNodeName());
        if (holder == null || (!instanceId.equals(holder) && heldUnderThisNodesName)) {
            leases.remove(id);
            setStatus(id, IndexShardStatus.CLOSED);
            needRecovery.add(id);
        }
    }

    @Override
    public void update(final Long id,
                       final Integer documentCount,
                       final Long commitDurationMs,
                       final Long commitMs,
                       final Long fileSize) {
        final IndexShard indexShard = map.get(id);
        if (indexShard != null) {
            map.put(id, indexShard
                    .copy()
                    .documentCount(documentCount)
                    .commitDurationMs(commitDurationMs)
                    .commitMs(commitMs)
                    .fileSize(fileSize)
                    .build());
        }
    }

    public long getMaxId() {
        return generatedId.get();
    }
}
