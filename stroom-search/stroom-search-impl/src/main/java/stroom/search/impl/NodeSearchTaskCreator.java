package stroom.search.impl;

import stroom.index.impl.IndexShardService;
import stroom.index.impl.IndexStore;
import stroom.index.impl.TimePartitionFactory;
import stroom.index.shared.FindIndexShardCriteria;
import stroom.index.shared.IndexDoc;
import stroom.index.shared.IndexShard;
import stroom.index.shared.IndexShard.IndexShardStatus;
import stroom.index.shared.TimePartition;
import stroom.query.api.v2.Query;
import stroom.query.api.v2.TimeFilter;
import stroom.query.api.v2.TimeRange;
import stroom.query.common.v2.DateExpressionParser;
import stroom.query.common.v2.ResultStore;
import stroom.task.api.TaskContext;
import stroom.util.shared.Range;
import stroom.util.shared.ResultPage;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;

public class NodeSearchTaskCreator implements NodeTaskCreator {

    private final IndexStore indexStore;
    private final IndexShardService indexShardService;
    private final TimePartitionFactory timePartitionFactory = new TimePartitionFactory();

    @Inject
    public NodeSearchTaskCreator(final IndexStore indexStore,
                                 final IndexShardService indexShardService) {
        this.indexStore = indexStore;
        this.indexShardService = indexShardService;
    }

    @Override
    public Map<String, NodeSearchTask> createNodeSearchTasks(final FederatedSearchTask task,
                                                             final Query query,
                                                             final TaskContext parentContext) {
        // Get a list of search index shards to look through.
        final FindIndexShardCriteria findIndexShardCriteria = FindIndexShardCriteria.matchAll();
        findIndexShardCriteria.getIndexUuidSet().add(query.getDataSource().getUuid());
        // Only non deleted indexes.
        findIndexShardCriteria.getIndexShardStatusSet().addAll(IndexShard.NON_DELETED_INDEX_SHARD_STATUS);
        // Order by partition name and key.
        findIndexShardCriteria.addSort(FindIndexShardCriteria.FIELD_PARTITION, true, false);
        findIndexShardCriteria.addSort(FindIndexShardCriteria.FIELD_ID, true, false);

        // Set the partition time range.
        setPartitionTimeRange(findIndexShardCriteria, task, query);

        final ResultPage<IndexShard> indexShards = indexShardService.find(findIndexShardCriteria);

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

    private void setPartitionTimeRange(final FindIndexShardCriteria findIndexShardCriteria,
                                       final FederatedSearchTask task,
                                       final Query query) {
        // Get the index doc.
        final IndexDoc indexDoc = indexStore.readDocument(query.getDataSource());
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
        final Range<Long> range = new Range<>(partitionFrom, partitionTo);
        findIndexShardCriteria.setPartitionTimeRange(range);
    }
}
