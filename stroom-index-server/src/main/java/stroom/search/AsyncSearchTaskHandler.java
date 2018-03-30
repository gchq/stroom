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

package stroom.search;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.entity.shared.Sort.Direction;
import stroom.index.IndexService;
import stroom.index.IndexShardService;
import stroom.index.shared.FindIndexShardCriteria;
import stroom.index.shared.Index;
import stroom.index.shared.IndexField;
import stroom.index.shared.IndexShard;
import stroom.index.shared.IndexShard.IndexShardStatus;
import stroom.node.shared.Node;
import stroom.query.api.v2.Query;
import stroom.security.Security;
import stroom.task.AbstractTaskHandler;
import stroom.task.GenericServerTask;
import stroom.task.TaskContext;
import stroom.task.TaskHandlerBean;
import stroom.task.TaskManager;
import stroom.task.cluster.ClusterDispatchAsync;
import stroom.task.cluster.ClusterDispatchAsyncHelper;
import stroom.task.cluster.NodeNotFoundException;
import stroom.task.cluster.NullClusterStateException;
import stroom.task.cluster.TargetNodeSetFactory;
import stroom.task.cluster.TargetNodeSetFactory.TargetType;
import stroom.task.cluster.TerminateTaskClusterTask;
import stroom.task.shared.FindTaskCriteria;
import stroom.util.shared.VoidResult;

import javax.inject.Inject;
import javax.inject.Provider;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;

@TaskHandlerBean(task = AsyncSearchTask.class)
class AsyncSearchTaskHandler extends AbstractTaskHandler<AsyncSearchTask, VoidResult> {
    private static final Logger LOGGER = LoggerFactory.getLogger(AsyncSearchTaskHandler.class);

    private final TaskContext taskContext;
    private final TargetNodeSetFactory targetNodeSetFactory;
    private final Provider<ClusterDispatchAsync> dispatchAsyncProvider;
    private final ClusterDispatchAsyncHelper dispatchHelper;
    private final IndexService indexService;
    private final IndexShardService indexShardService;
    private final TaskManager taskManager;
    private final Security security;

    @Inject
    AsyncSearchTaskHandler(final TaskContext taskContext,
                           final TargetNodeSetFactory targetNodeSetFactory,
                           final Provider<ClusterDispatchAsync> dispatchAsyncProvider,
                           final ClusterDispatchAsyncHelper dispatchHelper,
                           final IndexService indexService,
                           final IndexShardService indexShardService,
                           final TaskManager taskManager,
                           final Security security) {
        this.taskContext = taskContext;
        this.targetNodeSetFactory = targetNodeSetFactory;
        this.dispatchAsyncProvider = dispatchAsyncProvider;
        this.dispatchHelper = dispatchHelper;
        this.indexService = indexService;
        this.indexShardService = indexShardService;
        this.taskManager = taskManager;
        this.security = security;
    }

    @Override
    public VoidResult exec(final AsyncSearchTask task) {
        return security.useAsReadResult(() -> {
            final ClusterSearchResultCollector resultCollector = task.getResultCollector();

            if (!taskContext.isTerminated()) {
                final Node sourceNode = targetNodeSetFactory.getSourceNode();

                try {
                    // Get the nodes that we are going to send the search request
                    // to.
                    final Set<Node> targetNodes = targetNodeSetFactory.getEnabledActiveTargetNodeSet();
                    taskContext.info(task.getSearchName() + " - initialising");
                    final Query query = task.getQuery();

                    // Reload the index.
                    final Index index = indexService.loadByUuid(query.getDataSource().getUuid());

                    // Get an array of stored index fields that will be used for
                    // getting stored data.
                    // TODO : Specify stored fields based on the fields that all
                    // coprocessors will require. Also
                    // batch search only needs stream and event id stored fields.
                    final IndexField[] storedFields = getStoredFields(index);

                    // Get a list of search index shards to look through.
                    final FindIndexShardCriteria findIndexShardCriteria = new FindIndexShardCriteria();
                    findIndexShardCriteria.getIndexSet().add(query.getDataSource());
                    // Only non deleted indexes.
                    findIndexShardCriteria.getIndexShardStatusSet().addAll(IndexShard.NON_DELETED_INDEX_SHARD_STATUS);
                    // Order by partition name and key.
                    findIndexShardCriteria.addSort(FindIndexShardCriteria.FIELD_PARTITION, Direction.DESCENDING, false);
                    findIndexShardCriteria.addSort(FindIndexShardCriteria.FIELD_ID, Direction.DESCENDING, false);
                    findIndexShardCriteria.getFetchSet().add(Node.ENTITY_TYPE);
                    final List<IndexShard> indexShards = indexShardService.find(findIndexShardCriteria);

                    // Build a map of nodes that will deal with each set of shards.
                    final Map<Node, List<Long>> shardMap = new HashMap<>();
                    for (final IndexShard indexShard : indexShards) {
                        if (IndexShardStatus.CORRUPT.equals(indexShard.getStatus())) {
                            resultCollector.getErrorSet(indexShard.getNode()).add(
                                    "Attempt to search an index shard marked as corrupt: id=" + indexShard.getId() + ".");
                        } else {
                            final Node node = indexShard.getNode();
                            shardMap.computeIfAbsent(node, k -> new ArrayList<>()).add(indexShard.getId());
                        }
                    }

                    // Start remote cluster search execution.
                    final Map<Node, List<Long>> filteredShardNodes = shardMap.entrySet().stream()
                            .filter(entry -> {
                                final Node node = entry.getKey();
                                if (targetNodes.contains(node)) {
                                    return true;
                                } else {
                                    resultCollector.getErrorSet(node)
                                            .add("Node is not enabled or active. Some search results may be missing.");
                                    return false;
                                }
                            })
                            .collect(Collectors.toMap(Entry::getKey, Entry::getValue));

                    // Tell the result collector which nodes we expect to get results from.
                    resultCollector.setExpectedNodes(filteredShardNodes.keySet());

                    // Now send out distributed search tasks to each worker node.
                    filteredShardNodes.forEach((node, shards) -> {
                        final ClusterSearchTask clusterSearchTask = new ClusterSearchTask(task, task.getUserToken(), "Cluster Search", query, shards, sourceNode, storedFields,
                                task.getResultSendFrequency(), task.getCoprocessorMap(), task.getDateTimeLocale(), task.getNow());
                        LOGGER.debug("Dispatching clusterSearchTask to node {}", node);
                        dispatchAsyncProvider.get().execAsync(clusterSearchTask, resultCollector, sourceNode,
                                Collections.singleton(node));
                    });
                    taskContext.info(task.getSearchName() + " - searching...");

                    // Await completion.
                    resultCollector.awaitCompletion();

                } catch (final NullClusterStateException | NodeNotFoundException | RuntimeException e) {
                    resultCollector.getErrorSet(sourceNode).add(e.getMessage());
                } catch (final InterruptedException e) {
                    resultCollector.getErrorSet(sourceNode).add(e.getMessage());

                    // Continue to interrupt this thread.
                    Thread.currentThread().interrupt();
                } finally {
                    taskContext.info(task.getSearchName() + " - complete");

                    // Make sure we try and terminate any child tasks on worker
                    // nodes if we need to.
                    terminateTasks(task);

                    // Let the result handler know search has finished.
                    resultCollector.complete();

                    // We need to wait here for the client to keep getting results if
                    // this is an interactive search.
                    taskContext.info(task.getSearchName() + " - staying alive for UI requests");
                }
            }

            return VoidResult.INSTANCE;
        });
    }

    private void terminateTasks(final AsyncSearchTask task) {
        // Terminate this task.
        taskManager.terminate(task.getId());

        // We have to wrap the cluster termination task in another task or
        // ClusterDispatchAsyncImpl
        // will not execute it if the parent task is terminated.
        final GenericServerTask outerTask = GenericServerTask.create(null, task.getUserToken(), "Terminate: " + task.getTaskName(), "Terminating cluster tasks");
        outerTask.setRunnable(() -> {
            taskContext.info(task.getSearchName() + " - terminating child tasks");
            final FindTaskCriteria findTaskCriteria = new FindTaskCriteria();
            findTaskCriteria.addAncestorId(task.getId());
            final TerminateTaskClusterTask terminateTask = new TerminateTaskClusterTask(task.getUserToken(), "Terminate: " + task.getTaskName(), findTaskCriteria, false);

            // Terminate matching tasks.
            dispatchHelper.execAsync(terminateTask, TargetType.ACTIVE);
        });
        taskManager.execAsync(outerTask);
    }

    private IndexField[] getStoredFields(final Index index) {
        final List<IndexField> indexFields = index.getIndexFieldsObject().getIndexFields();
        final List<IndexField> list = new ArrayList<>(indexFields.size());
        for (final IndexField indexField : indexFields) {
            if (indexField.isStored()) {
                list.add(indexField);
            }
        }
        IndexField[] array = new IndexField[list.size()];
        array = list.toArray(array);
        return array;
    }
}
