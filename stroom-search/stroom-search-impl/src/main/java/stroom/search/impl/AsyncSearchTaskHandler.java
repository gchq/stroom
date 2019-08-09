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

package stroom.search.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.cluster.task.api.ClusterDispatchAsync;
import stroom.cluster.task.api.ClusterDispatchAsyncHelper;
import stroom.cluster.task.api.NodeNotFoundException;
import stroom.cluster.task.api.NullClusterStateException;
import stroom.cluster.task.api.TargetNodeSetFactory;
import stroom.cluster.task.api.TargetType;
import stroom.cluster.task.api.TerminateTaskClusterTask;
import stroom.index.impl.IndexShardService;
import stroom.index.impl.IndexStore;
import stroom.index.shared.FindIndexShardCriteria;
import stroom.index.shared.IndexDoc;
import stroom.index.shared.IndexField;
import stroom.index.shared.IndexShard;
import stroom.index.shared.IndexShard.IndexShardStatus;
import stroom.query.api.v2.Query;
import stroom.security.api.SecurityContext;
import stroom.task.api.AbstractTaskHandler;
import stroom.task.api.GenericServerTask;
import stroom.task.api.TaskContext;
import stroom.task.api.TaskManager;
import stroom.task.shared.FindTaskCriteria;
import stroom.util.shared.Sort.Direction;
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


class AsyncSearchTaskHandler extends AbstractTaskHandler<AsyncSearchTask, VoidResult> {
    private static final Logger LOGGER = LoggerFactory.getLogger(AsyncSearchTaskHandler.class);

    private final TaskContext taskContext;
    private final TargetNodeSetFactory targetNodeSetFactory;
    private final Provider<ClusterDispatchAsync> dispatchAsyncProvider;
    private final ClusterDispatchAsyncHelper dispatchHelper;
    private final IndexStore indexStore;
    private final IndexShardService indexShardService;
    private final TaskManager taskManager;
    private final SecurityContext securityContext;

    @Inject
    AsyncSearchTaskHandler(final TaskContext taskContext,
                           final TargetNodeSetFactory targetNodeSetFactory,
                           final Provider<ClusterDispatchAsync> dispatchAsyncProvider,
                           final ClusterDispatchAsyncHelper dispatchHelper,
                           final IndexStore indexStore,
                           final IndexShardService indexShardService,
                           final TaskManager taskManager,
                           final SecurityContext securityContext) {
        this.taskContext = taskContext;
        this.targetNodeSetFactory = targetNodeSetFactory;
        this.dispatchAsyncProvider = dispatchAsyncProvider;
        this.dispatchHelper = dispatchHelper;
        this.indexStore = indexStore;
        this.indexShardService = indexShardService;
        this.taskManager = taskManager;
        this.securityContext = securityContext;
    }

    @Override
    public VoidResult exec(final AsyncSearchTask task) {
        return securityContext.secureResult(() -> securityContext.useAsReadResult(() -> {
            final ClusterSearchResultCollector resultCollector = task.getResultCollector();

            if (!Thread.currentThread().isInterrupted()) {
                final String sourceNode = targetNodeSetFactory.getSourceNode();

                try {
                    // Get the nodes that we are going to send the search request
                    // to.
                    final Set<String> targetNodes = targetNodeSetFactory.getEnabledActiveTargetNodeSet();
                    taskContext.info(task.getSearchName() + " - initialising");
                    final Query query = task.getQuery();

                    // Reload the index.
                    final IndexDoc index = indexStore.readDocument(query.getDataSource());

                    // Get an array of stored index fields that will be used for
                    // getting stored data.
                    // TODO : Specify stored fields based on the fields that all
                    // coprocessors will require. Also
                    // batch search only needs stream and event id stored fields.
                    final IndexField[] storedFields = getStoredFields(index);

                    // Get a list of search index shards to look through.
                    final FindIndexShardCriteria findIndexShardCriteria = new FindIndexShardCriteria();
                    findIndexShardCriteria.getIndexUuidSet().add(query.getDataSource().getUuid());
                    // Only non deleted indexes.
                    findIndexShardCriteria.getIndexShardStatusSet().addAll(IndexShard.NON_DELETED_INDEX_SHARD_STATUS);
                    // Order by partition name and key.
                    findIndexShardCriteria.addSort(FindIndexShardCriteria.FIELD_PARTITION, Direction.DESCENDING, false);
                    findIndexShardCriteria.addSort(FindIndexShardCriteria.FIELD_ID, Direction.DESCENDING, false);
                    final List<IndexShard> indexShards = indexShardService.find(findIndexShardCriteria);

                    // Build a map of nodes that will deal with each set of shards.
                    final Map<String, List<Long>> shardMap = new HashMap<>();
                    for (final IndexShard indexShard : indexShards) {
                        if (IndexShardStatus.CORRUPT.equals(indexShard.getStatus())) {
                            resultCollector.getErrorSet(indexShard.getNodeName()).add(
                                    "Attempt to search an index shard marked as corrupt: id=" + indexShard.getId() + ".");
                        } else {
                            final String nodeName = indexShard.getNodeName();
                            shardMap.computeIfAbsent(nodeName, k -> new ArrayList<>()).add(indexShard.getId());
                        }
                    }

                    // Start remote cluster search execution.
                    final Map<String, List<Long>> filteredShardNodes = shardMap.entrySet().stream()
                            .filter(entry -> {
                                final String nodeName = entry.getKey();
                                if (targetNodes.contains(nodeName)) {
                                    return true;
                                } else {
                                    resultCollector.getErrorSet(nodeName)
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
        }));
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

    private IndexField[] getStoredFields(final IndexDoc index) {
        final List<IndexField> indexFields = index.getIndexFields();
        final List<IndexField> list = new ArrayList<>(indexFields.size());
        for (final IndexField indexField : indexFields) {
            if (indexField.isStored()) {
                list.add(indexField);
            }
        }
        return list.toArray(new IndexField[0]);
    }
}
