/*
 * Copyright 2023 Crown Copyright
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

package stroom.search.impl;

import stroom.cluster.task.api.NodeNotFoundException;
import stroom.cluster.task.api.NullClusterStateException;
import stroom.cluster.task.api.TargetNodeSetFactory;
import stroom.index.impl.IndexShardDao;
import stroom.index.impl.IndexStore;
import stroom.index.shared.FindIndexShardCriteria;
import stroom.index.shared.IndexShard;
import stroom.index.shared.IndexShard.IndexShardStatus;
import stroom.index.shared.LuceneIndexDoc;
import stroom.query.api.DateTimeSettings;
import stroom.query.api.Query;
import stroom.query.api.TimeFilter;
import stroom.query.api.TimeRange;
import stroom.query.common.v2.DateExpressionParser;
import stroom.query.common.v2.ResultStore;
import stroom.search.impl.ShardNodeAssigner.Reassignment;
import stroom.task.api.TaskContext;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.NullSafe;
import stroom.util.shared.Range;
import stroom.util.shared.ResultPage;

import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class NodeSearchTaskCreator implements NodeTaskCreator {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(NodeSearchTaskCreator.class);

    private final IndexStore indexStore;
    private final IndexShardDao indexShardDao;
    private final TargetNodeSetFactory targetNodeSetFactory;

    @Inject
    public NodeSearchTaskCreator(final IndexStore indexStore,
                                 final IndexShardDao indexShardDao,
                                 final TargetNodeSetFactory targetNodeSetFactory) {
        this.indexStore = indexStore;
        this.indexShardDao = indexShardDao;
        this.targetNodeSetFactory = targetNodeSetFactory;
    }

    @Override
    public Map<String, NodeSearchTask> createNodeSearchTasks(final FederatedSearchTask task,
                                                             final Query query,
                                                             final TaskContext parentContext) {
        // Get the partition time range.
        final Range<Long> partitionTimeRange = getPartitionTimeRange(task, query);

        // Get a list of search index shards to look through.
        final FindIndexShardCriteria findIndexShardCriteria = FindIndexShardCriteria
                .builder()
                .partitionTimeRange(partitionTimeRange)
                .build();
        findIndexShardCriteria.getIndexUuidSet().add(query.getDataSource().getUuid());
        // Only non deleted indexes.
        findIndexShardCriteria.getIndexShardStatusSet().addAll(IndexShard.NON_DELETED_INDEX_SHARD_STATUS);
        // Order by partition name and key.
        findIndexShardCriteria.addSort(FindIndexShardCriteria.FIELD_PARTITION, true, false);
        findIndexShardCriteria.addSort(FindIndexShardCriteria.FIELD_ID, true, false);

        final ResultPage<IndexShard> indexShards = indexShardDao.find(findIndexShardCriteria);

        // Build a map of nodes that will deal with each set of shards. A shard on a local volume is read by
        // the node that owns it. A shard on a shared volume is read by one of the nodes that are running.
        final Set<String> runningNodes = getRunningNodes();
        final Map<String, List<Long>> shardMap = new HashMap<>();
        for (final IndexShard indexShard : indexShards.getValues()) {
            if (IndexShardStatus.CORRUPT.equals(indexShard.getStatus())) {
                final ResultStore resultCollector = task.getResultStore();
                resultCollector.onFailure(indexShard.getNodeName(),
                        new SearchException("Attempt to search an index shard marked as corrupt: id=" +
                                indexShard.getId() +
                                "."));
            } else {
                ShardNodeAssigner.chooseNode(indexShard, runningNodes).ifPresent(nodeName ->
                        shardMap.computeIfAbsent(nodeName, k -> new ArrayList<>()).add(indexShard.getId()));
            }
        }
        final Map<String, NodeSearchTask> clusterTaskMap = new HashMap<>();
        shardMap.forEach((node, shards) -> {
            final NodeSearchTask nodeSearchTask = new NodeSearchTask(
                    NodeSearchTaskType.LUCENE,
                    parentContext.getTaskId(),
                    "Cluster Search",
                    task.getSearchRequestSource(),
                    task.getKey(),
                    query,
                    task.getSettings(),
                    task.getDateTimeSettings(),
                    shards,
                    null,
                    newDispatchId());
            clusterTaskMap.put(node, nodeSearchTask);
        });
        return clusterTaskMap;
    }

    @Override
    public NodeSearchReassignment reassign(final NodeSearchTask failedTask, final Set<String> failedNodes) {
        final List<Long> shardIds = NullSafe.list(failedTask.getShards());
        if (shardIds.isEmpty()) {
            return new NodeSearchReassignment(Map.of(), false);
        }

        final Set<String> remainingNodes = new HashSet<>(getRunningNodes());
        remainingNodes.removeAll(failedNodes);

        // Read the shards again as the task only holds their ids, and they may have changed since.
        final FindIndexShardCriteria findIndexShardCriteria = FindIndexShardCriteria
                .builder()
                .build();
        findIndexShardCriteria.getIndexShardIdSet().addAll(shardIds);
        final List<IndexShard> indexShards = indexShardDao.find(findIndexShardCriteria).getValues();

        boolean workLost = false;
        final Map<String, List<Long>> shardMap = new HashMap<>();
        for (final IndexShard indexShard : indexShards) {
            // A shard that has been deleted since the search started holds nothing to search.
            if (IndexShard.NON_DELETED_INDEX_SHARD_STATUS.contains(indexShard.getStatus())) {
                final Reassignment reassignment = ShardNodeAssigner.reassign(indexShard, remainingNodes);
                if (reassignment.nodeName() != null) {
                    shardMap.computeIfAbsent(reassignment.nodeName(), k -> new ArrayList<>())
                            .add(indexShard.getId());
                } else if (reassignment.notReadable()) {
                    workLost = true;
                }
            }
        }

        final Map<String, NodeSearchTask> tasks = new HashMap<>();
        shardMap.forEach((node, shards) -> tasks.put(node, failedTask.withShards(shards, newDispatchId())));
        LOGGER.debug("reassign() - failedNodes: {}, shards: {}, reassigned: {}, workLost: {}",
                failedNodes, shardIds.size(), shardMap, workLost);
        return new NodeSearchReassignment(tasks, workLost);
    }

    // The nodes that a shard on a shared volume can be given to.
    private Set<String> getRunningNodes() {
        try {
            return targetNodeSetFactory.getEnabledActiveTargetNodeSet();
        } catch (final NullClusterStateException | NodeNotFoundException e) {
            // With no known nodes every shard stays with its owner, as it did before shards could be shared.
            LOGGER.debug(e::getMessage, e);
            return Set.of();
        }
    }

    private static String newDispatchId() {
        return UUID.randomUUID().toString();
    }

    private Range<Long> getPartitionTimeRange(final FederatedSearchTask task,
                                              final Query query) {
        // Get the index doc.
        final LuceneIndexDoc indexDoc = indexStore.readDocument(query.getDataSource());
        if (indexDoc == null) {
            throw new SearchException("Index not found");
        }
        return partitionTimeRange(query.getTimeRange(), task.getDateTimeSettings());
    }

    /// Gets the time range a shard's partition must overlap for the shard to be searched. It is the
    /// query's own time range, not rounded to the index's partitions, as shards keep the partitioning
    /// they were written with, which may differ from the index's current setting.
    ///
    /// @param timeRange        The query's time range, may be null.
    /// @param dateTimeSettings The settings to read the time range's expressions with.
    /// @return The range, with a null from or to where the query's time range has no bound.
    static Range<Long> partitionTimeRange(final TimeRange timeRange,
                                          final DateTimeSettings dateTimeSettings) {
        Long from = null;
        Long to = null;
        if (timeRange != null) {
            final TimeFilter timeFilter = DateExpressionParser.getTimeFilter(timeRange, dateTimeSettings);
            if (timeRange.getFrom() != null && !timeRange.getFrom().isBlank()) {
                from = timeFilter.getFrom();
            }
            if (timeRange.getTo() != null && !timeRange.getTo().isBlank()) {
                to = timeFilter.getTo();
            }
        }
        return new Range<>(from, to);
    }
}
