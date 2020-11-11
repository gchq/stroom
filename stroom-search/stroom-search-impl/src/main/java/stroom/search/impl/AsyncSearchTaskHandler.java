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
import stroom.query.common.v2.NodeResult;
import stroom.search.impl.shard.IndexShardSearchTaskExecutor;
import stroom.security.api.SecurityContext;
import stroom.security.api.UserIdentity;
import stroom.task.api.ExecutorProvider;
import stroom.task.api.TaskContext;
import stroom.task.api.TaskContextFactory;
import stroom.task.api.TaskManager;
import stroom.task.shared.TaskId;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.ResultPage;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

class AsyncSearchTaskHandler {
    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(AsyncSearchTaskHandler.class);

    private final TargetNodeSetFactory targetNodeSetFactory;
    private final ClusterCallService clusterCallService;
    private final IndexStore indexStore;
    private final IndexShardService indexShardService;
    private final TaskManager taskManager;
    private final ClusterTaskTerminator clusterTaskTerminator;
    private final SecurityContext securityContext;
    private final ExecutorProvider executorProvider;
    private final TaskContextFactory taskContextFactory;

    @Inject
    AsyncSearchTaskHandler(final TargetNodeSetFactory targetNodeSetFactory,
                           final ClusterCallServiceRemote clusterCallService,
                           final IndexStore indexStore,
                           final IndexShardService indexShardService,
                           final TaskManager taskManager,
                           final ClusterTaskTerminator clusterTaskTerminator,
                           final SecurityContext securityContext,
                           final ExecutorProvider executorProvider,
                           final TaskContextFactory taskContextFactory) {
        this.targetNodeSetFactory = targetNodeSetFactory;
        this.clusterCallService = clusterCallService;
        this.indexStore = indexStore;
        this.indexShardService = indexShardService;
        this.taskManager = taskManager;
        this.clusterTaskTerminator = clusterTaskTerminator;
        this.securityContext = securityContext;
        this.executorProvider = executorProvider;
        this.taskContextFactory = taskContextFactory;
    }

    public void exec(final TaskContext parentContext, final AsyncSearchTask task) {
        securityContext.secure(() -> securityContext.useAsRead(() -> {
            final ClusterSearchResultCollector resultCollector = task.getResultCollector();

            if (!Thread.currentThread().isInterrupted()) {
                final String sourceNode = targetNodeSetFactory.getSourceNode();
                final Map<String, List<Long>> shardMap = new HashMap<>();

                try {
                    // Get the nodes that we are going to send the search request
                    // to.
                    final Set<String> targetNodes = targetNodeSetFactory.getEnabledActiveTargetNodeSet();
                    parentContext.info(() -> task.getSearchName() + " - initialising");
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
                    final Executor executor = executorProvider.get(IndexShardSearchTaskExecutor.THREAD_POOL);
                    final List<CompletableFuture<Void>> futures = new ArrayList<>();
                    for (final Entry<String, List<Long>> entry : shardMap.entrySet()) {
                        final String nodeName = entry.getKey();
                        final List<Long> shards = entry.getValue();
                        if (targetNodes.contains(nodeName)) {
                            final Runnable runnable = taskContextFactory.context(parentContext, "Node search", taskContext ->
                                    searchNode(sourceNode, nodeName, shards, task, query, storedFields, taskContext));
                            final CompletableFuture<Void> completableFuture = CompletableFuture.runAsync(runnable, executor);
                            futures.add(completableFuture);
                        } else {
                            resultCollector.onFailure(nodeName,
                                    new SearchException("Node is not enabled or active. Some search results may be missing."));
                        }
                    }

                    // Wait for all nodes to finish.
                    LOGGER.debug(() -> "Waiting for completion");
                    final CompletableFuture<Void> all = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
                    all.join();
                    LOGGER.debug(() -> "Done waiting for completion");

                } catch (final RuntimeException | NodeNotFoundException | NullClusterStateException e) {
                    resultCollector.getErrorSet(sourceNode).add(e.getMessage());

                } finally {
                    // Make sure we try and terminate any child tasks on worker
                    // nodes if we need to.
                    terminateTasks(task, parentContext.getTaskId());

                    // Ensure search is complete even if we had errors.
                    LOGGER.debug(() -> "Search complete");
                    resultCollector.complete();

                    // We need to wait here for the client to keep getting results if
                    // this is an interactive search.
                    parentContext.info(() -> task.getSearchName() + " - staying alive for UI requests");
                }
            }
        }));
    }

    private void searchNode(final String sourceNode,
                            final String targetNode,
                            final List<Long> shards,
                            final AsyncSearchTask task,
                            final Query query,
                            final String[] storedFields,
                            final TaskContext taskContext) {
        LOGGER.debug(() -> task.getSearchName() + " - start searching node: " + targetNode);
        taskContext.info(() -> task.getSearchName() + " - start searching node: " + targetNode);
        final ClusterSearchResultCollector resultCollector = task.getResultCollector();

        // Start remote cluster search execution.
        final ClusterSearchTask clusterSearchTask = new ClusterSearchTask(
                taskContext.getTaskId(),
                "Cluster Search",
                task.getKey(),
                query,
                shards,
                storedFields,
                task.getSettings(),
                task.getDateTimeLocale(),
                task.getNow());
        LOGGER.debug(() -> "Dispatching clusterSearchTask to node: " + targetNode);
        try {
            final boolean success = (Boolean) clusterCallService.call(
                    sourceNode,
                    targetNode,
                    securityContext.getUserIdentity(),
                    RemoteSearchManager.SERVICE_NAME,
                    RemoteSearchManager.START_SEARCH,
                    new Class[]{UserIdentity.class, TaskId.class, ClusterSearchTask.class},
                    new Object[]{securityContext.getUserIdentity(), taskContext.getTaskId(), clusterSearchTask});
            if (!success) {
                LOGGER.debug(() -> "Failed to start remote search on node: " + targetNode);
                final SearchException searchException = new SearchException("Failed to start remote search on node: " + targetNode);
                resultCollector.onFailure(targetNode, searchException);
                throw searchException;
            }

        } catch (final Throwable e) {
            final SearchException searchException = new SearchException(e.getMessage(), e);
            resultCollector.onFailure(targetNode, searchException);
            throw searchException;
        }

        try {
            LOGGER.debug(() -> task.getSearchName() + " - searching node: " + targetNode + "...");
            taskContext.info(() -> task.getSearchName() + " - searching node: " + targetNode + "...");

            // Poll for results until completion.
            boolean complete = false;
            while (!Thread.currentThread().isInterrupted() && !complete) {
                final NodeResult nodeResult = (NodeResult) clusterCallService.call(
                        sourceNode,
                        targetNode,
                        securityContext.getUserIdentity(),
                        RemoteSearchManager.SERVICE_NAME,
                        RemoteSearchManager.POLL,
                        new Class[]{UserIdentity.class, QueryKey.class},
                        new Object[]{securityContext.getUserIdentity(), task.getKey()});
                if (nodeResult != null) {
                    LOGGER.debug(() -> "Receive result for node: " + targetNode + " " + nodeResult);
                    final boolean success = resultCollector.onSuccess(targetNode, nodeResult);
                    // If the result collector returns false it is because we have already collected enough data and can
                    // therefore consider search complete.
                    if (nodeResult.isComplete() || !success) {
                        complete = true;
                    }
                }
            }

        } catch (final RuntimeException e) {
            resultCollector.getErrorSet(sourceNode).add(e.getMessage());

        } finally {
            LOGGER.debug(() -> task.getSearchName() + " - finished searching node: " + targetNode);
            taskContext.info(() -> task.getSearchName() + " - finished searching node: " + targetNode);

            // Destroy remote search results.
            try {
                final boolean success = (Boolean) clusterCallService.call(
                        sourceNode,
                        targetNode,
                        securityContext.getUserIdentity(),
                        RemoteSearchManager.SERVICE_NAME,
                        RemoteSearchManager.DESTROY,
                        new Class[]{UserIdentity.class, QueryKey.class},
                        new Object[]{securityContext.getUserIdentity(), task.getKey()});
                if (!success) {
                    LOGGER.debug(() -> "Failed to destroy remote search on node: " + targetNode);
                    resultCollector.onFailure(targetNode, new SearchException("Failed to destroy remote search"));
                }
            } catch (final Throwable e) {
                resultCollector.onFailure(targetNode, new SearchException(e.getMessage(), e));
            }
        }
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
