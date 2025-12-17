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

package stroom.search.impl;

import stroom.index.impl.IndexShardDao;
import stroom.index.impl.IndexStore;
import stroom.index.impl.TimePartitionFactory;
import stroom.index.shared.FindIndexShardCriteria;
import stroom.index.shared.IndexShard;
import stroom.index.shared.IndexShard.IndexShardStatus;
import stroom.index.shared.LuceneIndexDoc;
import stroom.index.shared.TimePartition;
import stroom.query.api.Query;
import stroom.query.api.TimeFilter;
import stroom.query.api.TimeRange;
import stroom.query.common.v2.DateExpressionParser;
import stroom.query.common.v2.ResultStore;
import stroom.task.api.TaskContext;
import stroom.util.shared.Range;
import stroom.util.shared.ResultPage;

import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class NodeSearchTaskCreator implements NodeTaskCreator {

    private final IndexStore indexStore;
    private final IndexShardDao indexShardDao;
    private final TimePartitionFactory timePartitionFactory = new TimePartitionFactory();

    @Inject
    public NodeSearchTaskCreator(final IndexStore indexStore,
                                 final IndexShardDao indexShardDao) {
        this.indexStore = indexStore;
        this.indexShardDao = indexShardDao;
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

        // Build a map of nodes that will deal with each set of shards.
        final Map<String, List<Long>> shardMap = new HashMap<>();
        for (final IndexShard indexShard : indexShards.getValues()) {
            if (IndexShardStatus.CORRUPT.equals(indexShard.getStatus())) {
                final ResultStore resultCollector = task.getResultStore();
                resultCollector.onFailure(indexShard.getNodeName(),
                        new SearchException("Attempt to search an index shard marked as corrupt: id=" +
                                indexShard.getId() +
                                "."));
            } else {
                final String nodeName = indexShard.getNodeName();
                shardMap.computeIfAbsent(nodeName, k -> new ArrayList<>()).add(indexShard.getId());
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
                    shards);
            clusterTaskMap.put(node, nodeSearchTask);
        });
        return clusterTaskMap;
    }

    private Range<Long> getPartitionTimeRange(final FederatedSearchTask task,
                                              final Query query) {
        // Get the index doc.
        final LuceneIndexDoc indexDoc = indexStore.readDocument(query.getDataSource());
        if (indexDoc == null) {
            throw new SearchException("Index not found");
        }

        final TimeRange timeRange = query.getTimeRange();
        Long partitionFrom = null;
        Long partitionTo = null;

        if (timeRange != null) {
            final TimeFilter timeFilter = DateExpressionParser
                    .getTimeFilter(timeRange, task.getDateTimeSettings());

            if (timeRange.getFrom() != null && !timeRange.getFrom().isBlank()) {
                final TimePartition timePartition = timePartitionFactory.create(indexDoc, timeFilter.getFrom());
                partitionFrom = timePartition.getPartitionFromTime();
            }
            if (timeRange.getTo() != null && !timeRange.getTo().isBlank()) {
                final TimePartition timePartition = timePartitionFactory.create(indexDoc, timeFilter.getTo());
                partitionTo = timePartition.getPartitionToTime();
            }
        }
        return new Range<>(partitionFrom, partitionTo);
    }
}
