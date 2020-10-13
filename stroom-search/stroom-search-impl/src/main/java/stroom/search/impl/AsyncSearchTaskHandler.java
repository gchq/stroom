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

import stroom.cluster.api.ClusterCallService;
import stroom.cluster.api.ClusterCallServiceRemote;
import stroom.cluster.task.api.ClusterTaskTerminator;
import stroom.cluster.task.api.NodeNotFoundException;
import stroom.cluster.task.api.NullClusterStateException;
import stroom.cluster.task.api.TargetNodeSetFactory;
import stroom.index.impl.IndexShardService;
import stroom.index.impl.IndexStore;
import stroom.index.shared.FindIndexShardCriteria;
import stroom.index.shared.IndexDoc;
import stroom.index.shared.IndexField;
import stroom.index.shared.IndexShard;
import stroom.index.shared.IndexShard.IndexShardStatus;
import stroom.query.api.v2.Query;
import stroom.query.api.v2.QueryKey;
import stroom.search.resultsender.NodeResult;
import stroom.security.api.SecurityContext;
import stroom.security.api.UserIdentity;
import stroom.task.api.TaskContext;
import stroom.task.api.TaskManager;
import stroom.task.shared.TaskId;
import stroom.util.shared.ResultPage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

class AsyncSearchTaskHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(AsyncSearchTaskHandler.class);

    private final TargetNodeSetFactory targetNodeSetFactory;
    private final ClusterCallService clusterCallService;
    private final IndexStore indexStore;
    private final IndexShardService indexShardService;
    private final TaskManager taskManager;
    private final ClusterTaskTerminator clusterTaskTerminator;
    private final SecurityContext securityContext;

    @Inject
    AsyncSearchTaskHandler(final TargetNodeSetFactory targetNodeSetFactory,
                           final ClusterCallServiceRemote clusterCallService,
                           final IndexStore indexStore,
                           final IndexShardService indexShardService,
                           final TaskManager taskManager,
                           final ClusterTaskTerminator clusterTaskTerminator,
                           final SecurityContext securityContext) {
        this.targetNodeSetFactory = targetNodeSetFactory;
        this.clusterCallService = clusterCallService;
        this.indexStore = indexStore;
        this.indexShardService = indexShardService;
        this.taskManager = taskManager;
        this.clusterTaskTerminator = clusterTaskTerminator;
        this.securityContext = securityContext;
    }

    public void exec(final TaskContext taskContext, final AsyncSearchTask task) {
        securityContext.secure(() -> securityContext.useAsRead(() -> {
            final ClusterSearchResultCollector resultCollector = task.getResultCollector();

            if (!Thread.currentThread().isInterrupted()) {
                final String sourceNode = targetNodeSetFactory.getSourceNode();
                final Map<String, List<Long>> shardMap = new HashMap<>();

                try {
                    final Set<String> remainingNodes = Collections.newSetFromMap(new ConcurrentHashMap<>());

                    // Get the nodes that we are going to send the search request
                    // to.
                    final Set<String> targetNodes = targetNodeSetFactory.getEnabledActiveTargetNodeSet();
                    taskContext.info(() -> task.getSearchName() + " - initialising");
                    final Query query = task.getQuery();

                    // Reload the index.
                    final IndexDoc index = indexStore.readDocument(query.getDataSource());

                    // Get an array of stored index fields that will be used for
                    // getting stored data.
                    // TODO : Specify stored fields based on the fields that all
                    // coprocessors will require. Also
                    // batch search only needs stream and event id stored fields.
                    final String[] storedFields = getStoredFields(index);

                    // Get a list of search index shards to look through.
                    final FindIndexShardCriteria findIndexShardCriteria = FindIndexShardCriteria.matchAll();
                    findIndexShardCriteria.getIndexUuidSet().add(query.getDataSource().getUuid());
                    // Only non deleted indexes.
                    findIndexShardCriteria.getIndexShardStatusSet().addAll(IndexShard.NON_DELETED_INDEX_SHARD_STATUS);
                    // Order by partition name and key.
                    findIndexShardCriteria.addSort(FindIndexShardCriteria.FIELD_PARTITION, true, false);
                    findIndexShardCriteria.addSort(FindIndexShardCriteria.FIELD_ID, true, false);
                    final ResultPage<IndexShard> indexShards = indexShardService.find(findIndexShardCriteria);

                    // Build a map of nodes that will deal with each set of shards.
                    for (final IndexShard indexShard : indexShards.getValues()) {
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
                                    resultCollector.onFailure(nodeName,
                                            new SearchException("Node is not enabled or active. Some search results may be missing."));
                                    return false;
                                }
                            })
                            .collect(Collectors.toMap(Entry::getKey, Entry::getValue));

                    // Tell the result collector which nodes we expect to get results from.
                    resultCollector.setExpectedNodes(filteredShardNodes.keySet());

                    // Now send out distributed search tasks to each worker node.
                    filteredShardNodes.forEach((node, shards) -> {
                        final ClusterSearchTask clusterSearchTask = new ClusterSearchTask(
                                "Cluster Search",
                                task.getKey(),
                                query,
                                shards,
                                storedFields,
                                task.getCoprocessorMap(),
                                task.getDateTimeLocale(),
                                task.getNow());
                        LOGGER.debug("Dispatching clusterSearchTask to node {}", node);

                        try {
                            final boolean success = (Boolean) clusterCallService.call(sourceNode, node, securityContext.getUserIdentity(), RemoteSearchManager.SERVICE_NAME,
                                    RemoteSearchManager.START_SEARCH,
                                    new Class[]{UserIdentity.class, TaskId.class, ClusterSearchTask.class},
                                    new Object[]{securityContext.getUserIdentity(), taskContext.getTaskId(), clusterSearchTask});
                            if (!success) {
                                resultCollector.onFailure(node, new SearchException("Failed to start remote search"));
                            } else {
                                remainingNodes.add(node);
                            }

                        } catch (final Throwable e) {
                            resultCollector.onFailure(node, new SearchException(e.getMessage(), e));
                        }
                    });
                    taskContext.info(() -> task.getSearchName() + " - searching...");

                    // Await completion.
                    while (!Thread.currentThread().isInterrupted() &&
                            remainingNodes.size() > 0) {

                        final long start = System.currentTimeMillis();

                        for (String node : remainingNodes) {
                            final NodeResult nodeResult = (NodeResult) clusterCallService.call(
                                    sourceNode,
                                    node,
                                    securityContext.getUserIdentity(),
                                    RemoteSearchManager.SERVICE_NAME,
                                    RemoteSearchManager.POLL,
                                    new Class[]{UserIdentity.class, QueryKey.class},
                                    new Object[]{securityContext.getUserIdentity(), task.getKey()});
                            if (nodeResult != null) {
                                resultCollector.onSuccess(node, nodeResult);
                                if (nodeResult.isComplete()) {
                                    remainingNodes.remove(node);
                                }
                            }
                        }

                        final long elapsed = System.currentTimeMillis() - start;
                        if (elapsed < 1000) {
                            Thread.sleep(1000 - elapsed);
                        }

//                        boolean awaitResult = LAMBDA_LOGGER.logDurationIfTraceEnabled(
//                                () -> {
//                                    try {
//                                        // block and wait for up to 10s for our search to be completed/terminated
//                                        return resultCollector.awaitCompletion(10, TimeUnit.SECONDS);
//                                    } catch (InterruptedException e) {
//                                        //Don't reset the interrupt status as we are at the top level of
//                                        //the task execution
//                                        throw new RuntimeException("Thread interrupted");
//                                    }
//                                },
//                                "waiting for completion condition");
//
//                        LOGGER.trace("await finished with result {}", awaitResult);
//
//                        // If the collector is no longer in the cache then terminate
//                        // this search task.
//                        if (clusterResultCollectorCache.get(resultCollector.getId()) == null) {
//                            terminateTasks(task);
//                        }
                    }

                    // Perform a final wait for the result collector to do final merges.
                    resultCollector.waitForPendingWork();
                    taskContext.info(() -> task.getSearchName() + " - complete");

                } catch (final NullClusterStateException | NodeNotFoundException | RuntimeException e) {
                    resultCollector.getErrorSet(sourceNode).add(e.getMessage());
                } catch (final InterruptedException e) {
                    resultCollector.getErrorSet(sourceNode).add(e.getMessage());

                    // Continue to interrupt this thread.
                    Thread.currentThread().interrupt();
                } finally {
                    // Destroy remote search results.
                    for (final String node : shardMap.keySet()) {
                        try {
                            final boolean success = (Boolean) clusterCallService.call(
                                    sourceNode,
                                    node,
                                    securityContext.getUserIdentity(),
                                    RemoteSearchManager.SERVICE_NAME,
                                    RemoteSearchManager.DESTROY,
                                    new Class[]{UserIdentity.class, QueryKey.class},
                                    new Object[]{securityContext.getUserIdentity(), task.getKey()});

                        } catch (final Throwable e) {
                            resultCollector.onFailure(node, new SearchException(e.getMessage(), e));
                        }
                    }

                    // Make sure we try and terminate any child tasks on worker
                    // nodes if we need to.
                    terminateTasks(task, taskContext.getTaskId());

                    // Ensure search is complete even if we had errors.
                    resultCollector.complete();

                    // We need to wait here for the client to keep getting results if
                    // this is an interactive search.
                    taskContext.info(() -> task.getSearchName() + " - staying alive for UI requests");
                }
            }
        }));
    }

    private void terminateTasks(final AsyncSearchTask task, final TaskId taskId) {
        // Terminate this task.
        taskManager.terminate(taskId);

        // We have to wrap the cluster termination task in another task or
        // ClusterDispatchAsyncImpl
        // will not execute it if the parent task is terminated.
        clusterTaskTerminator.terminate(task.getSearchName(), taskId, "AsyncSearchTask");
    }

    private String[] getStoredFields(final IndexDoc index) {
        return index.getFields()
                .stream()
                .filter(IndexField::isStored)
                .map(IndexField::getFieldName)
                .toArray(String[]::new);
    }
}
