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

package stroom.search.server;

import org.springframework.context.annotation.Scope;
import stroom.cluster.server.ClusterCallService;
import stroom.entity.shared.Sort.Direction;
import stroom.index.server.IndexService;
import stroom.index.server.IndexShardService;
import stroom.index.shared.FindIndexShardCriteria;
import stroom.index.shared.Index;
import stroom.index.shared.IndexField;
import stroom.index.shared.IndexShard;
import stroom.index.shared.IndexShard.IndexShardStatus;
import stroom.node.shared.Node;
import stroom.query.api.v2.Query;
import stroom.query.api.v2.QueryKey;
import stroom.search.resultsender.NodeResult;
import stroom.security.SecurityContext;
import stroom.security.SecurityHelper;
import stroom.security.shared.UserIdentity;
import stroom.task.cluster.ClusterDispatchAsyncHelper;
import stroom.task.cluster.ClusterResultCollectorCache;
import stroom.task.cluster.TargetNodeSetFactory;
import stroom.task.cluster.TargetNodeSetFactory.TargetType;
import stroom.task.cluster.TerminateTaskClusterTask;
import stroom.task.server.AbstractTaskHandler;
import stroom.task.server.GenericServerTask;
import stroom.task.server.TaskContext;
import stroom.task.server.TaskHandlerBean;
import stroom.task.server.TaskManager;
import stroom.task.shared.FindTaskCriteria;
import stroom.util.concurrent.ExecutorProvider;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.TaskId;
import stroom.util.shared.VoidResult;
import stroom.util.spring.StroomScope;
import stroom.util.task.TaskMonitor;
import stroom.util.task.TaskWrapper;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@TaskHandlerBean(task = AsyncSearchTask.class)
@Scope(value = StroomScope.TASK)
class AsyncSearchTaskHandler extends AbstractTaskHandler<AsyncSearchTask, VoidResult> {
    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(AsyncSearchTaskHandler.class);

    private final TaskMonitor taskMonitor;
    private final TargetNodeSetFactory targetNodeSetFactory;
    private final ClusterDispatchAsyncHelper dispatchHelper;
    private final ClusterCallService clusterCallService;
    private final ClusterResultCollectorCache clusterResultCollectorCache;
    private final IndexService indexService;
    private final IndexShardService indexShardService;
    private final TaskManager taskManager;
    private final SecurityContext securityContext;
    private final ExecutorProvider executorProvider;
    private final Provider<TaskWrapper> taskWrapperProvider;
    private final TaskContext taskContext;

    @Inject
    AsyncSearchTaskHandler(final TaskMonitor taskMonitor,
                           final TargetNodeSetFactory targetNodeSetFactory,
                           final ClusterDispatchAsyncHelper dispatchHelper,
                           @Named("clusterCallServiceRemote") final ClusterCallService clusterCallService,
                           final ClusterResultCollectorCache clusterResultCollectorCache,
                           final IndexService indexService,
                           final IndexShardService indexShardService,
                           final TaskManager taskManager,
                           final SecurityContext securityContext,
                           final ExecutorProvider executorProvider,
                           final Provider<TaskWrapper> taskWrapperProvider,
                           final TaskContext taskContext) {
        this.taskMonitor = taskMonitor;
        this.targetNodeSetFactory = targetNodeSetFactory;
        this.dispatchHelper = dispatchHelper;
        this.clusterCallService = clusterCallService;
        this.clusterResultCollectorCache = clusterResultCollectorCache;
        this.indexService = indexService;
        this.indexShardService = indexShardService;
        this.taskManager = taskManager;
        this.securityContext = securityContext;
        this.executorProvider = executorProvider;
        this.taskWrapperProvider = taskWrapperProvider;
        this.taskContext = taskContext;
    }

    @Override
    public VoidResult exec(final AsyncSearchTask task) {
        try (final SecurityHelper securityHelper = SecurityHelper.elevate(securityContext)) {
            final ClusterSearchResultCollector resultCollector = task.getResultCollector();
            if (!task.isTerminated()) {
                final Node sourceNode = targetNodeSetFactory.getSourceNode();
                final Map<Node, List<Long>> shardMap = new HashMap<>();

                try {
                    // Get the nodes that we are going to send the search request
                    // to.
                    final Set<Node> targetNodes = targetNodeSetFactory.getEnabledActiveTargetNodeSet();
                    taskMonitor.info(task.getSearchName() + " - initialising");
                    final Query query = task.getQuery();

                    // Reload the index.
                    final Index index = indexService.loadByUuid(query.getDataSource().getUuid());

                    // Get an array of stored index fields that will be used for
                    // getting stored data.
                    // TODO : Specify stored fields based on the fields that all
                    // coprocessors will require. Also
                    // batch search only needs stream and event id stored fields.
                    final String[] storedFields = getStoredFields(index);

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
                    final Executor executor = executorProvider.getExecutor(task.getThreadPool());
                    final TaskWrapper taskWrapper = taskWrapperProvider.get();
                    final List<CompletableFuture<Void>> futures = new ArrayList<>();
                    for (final Entry<Node, List<Long>> entry : shardMap.entrySet()) {
                        final Node node = entry.getKey();
                        final List<Long> shards = entry.getValue();
                        if (targetNodes.contains(node)) {
                            Runnable runnable = () -> searchNode(sourceNode, node, shards, task, query, storedFields, taskContext);
                            runnable = taskWrapper.wrap(runnable);
                            final CompletableFuture<Void> completableFuture = CompletableFuture.runAsync(runnable, executor);
                            futures.add(completableFuture);
                        } else {
                            resultCollector.onFailure(node,
                                    new SearchException("Node is not enabled or active. Some search results may be missing."));
                        }
                    }

                    // Wait for all nodes to finish.
                    LOGGER.debug(() -> "Waiting for completion");
                    final CompletableFuture<Void> all = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
                    all.join();
                    LOGGER.debug(() -> "Done waiting for completion");

                } catch (final Exception e) {
                    resultCollector.getErrorSet(sourceNode).add(e.getMessage());

                } finally {
                    // Make sure we try and terminate any child tasks on worker
                    // nodes if we need to.
                    terminateTasks(task);

                    // Ensure search is complete even if we had errors.
                    LOGGER.debug(() -> "Search complete");
                    resultCollector.complete();

                    // We need to wait here for the client to keep getting results if
                    // this is an interactive search.
                    taskMonitor.info(task.getSearchName() + " - staying alive for UI requests");
                }
            }

            return VoidResult.INSTANCE;
        }
    }

    private void searchNode(final Node sourceNode,
                            final Node targetNode,
                            final List<Long> shards,
                            final AsyncSearchTask task,
                            final Query query,
                            final String[] storedFields,
                            final TaskContext taskContext) {
        LOGGER.debug(() -> task.getSearchName() + " - start searching node: " + targetNode.getName());
        taskContext.info(task.getSearchName() + " - start searching node: " + targetNode.getName());
        final ClusterSearchResultCollector resultCollector = task.getResultCollector();

        // Start remote cluster search execution.
        final ClusterSearchTask clusterSearchTask = new ClusterSearchTask(
                securityContext.getUserIdentity(),
                "Cluster Search",
                task.getKey(),
                query,
                shards,
                storedFields,
                task.getCoprocessorMap(),
                task.getDateTimeLocale(),
                task.getNow());
        LOGGER.debug(() -> "Dispatching clusterSearchTask to node: " + targetNode);
        try {
            final boolean success = (Boolean) clusterCallService.call(sourceNode, targetNode, RemoteSearchManager.BEAN_NAME,
                    RemoteSearchManager.START_SEARCH,
                    new Class[]{UserIdentity.class, TaskId.class, ClusterSearchTask.class},
                    new Object[]{securityContext.getUserIdentity(), task.getId(), clusterSearchTask});
            if (!success) {
                LOGGER.debug(() -> "Failed to start remote search on node: " + targetNode.getName());
                final SearchException searchException = new SearchException("Failed to start remote search on node: " + targetNode.getName());
                resultCollector.onFailure(targetNode, searchException);
                throw searchException;
            }

        } catch (final Throwable e) {
            final SearchException searchException = new SearchException(e.getMessage(), e);
            resultCollector.onFailure(targetNode, searchException);
            throw searchException;
        }

        try {
            LOGGER.debug(() -> task.getSearchName() + " - searching node: " + targetNode.getName() + "...");
            taskContext.info(task.getSearchName() + " - searching node: " + targetNode.getName() + "...");

            // Poll for results until completion.
            boolean complete = false;
            while (!task.isTerminated() && !complete) {
                final NodeResult nodeResult = (NodeResult) clusterCallService.call(
                        sourceNode,
                        targetNode,
                        RemoteSearchManager.BEAN_NAME,
                        RemoteSearchManager.POLL,
                        new Class[]{UserIdentity.class, QueryKey.class},
                        new Object[]{securityContext.getUserIdentity(), task.getKey()});
                if (nodeResult != null) {
                    LOGGER.debug(() -> "Receive result for node: " + targetNode.getName() + " " + nodeResult);
                    resultCollector.onSuccess(targetNode, nodeResult);
                    if (nodeResult.isComplete()) {
                        complete = true;
                    }
                }

                // If the collector is no longer in the cache then terminate
                // this search task.
                if (clusterResultCollectorCache.get(resultCollector.getId()) == null) {
                    LOGGER.debug(() -> "Terminate tasks for node: " + targetNode.getName());
                    terminateTasks(task);
                }
            }

        } catch (final Exception e) {
            resultCollector.getErrorSet(sourceNode).add(e.getMessage());

        } finally {
            LOGGER.debug(() -> task.getSearchName() + " - finished searching node: " + targetNode.getName());
            taskContext.info(task.getSearchName() + " - finished searching node: " + targetNode.getName());

            // Destroy remote search results.
            try {
                final boolean success = (Boolean) clusterCallService.call(
                        sourceNode,
                        targetNode,
                        RemoteSearchManager.BEAN_NAME,
                        RemoteSearchManager.DESTROY,
                        new Class[]{UserIdentity.class, QueryKey.class},
                        new Object[]{securityContext.getUserIdentity(), task.getKey()});
                if (!success) {
                    LOGGER.debug(() -> "Failed to destroy remote search on node: " + targetNode.getName());
                    resultCollector.onFailure(targetNode, new SearchException("Failed to destroy remote search"));
                }
            } catch (final Throwable e) {
                resultCollector.onFailure(targetNode, new SearchException(e.getMessage(), e));
            }
        }
    }

    private void terminateTasks(final AsyncSearchTask task) {
        // Terminate this task.
        task.terminate();

        // We have to wrap the cluster termination task in another task or
        // ClusterDispatchAsyncImpl
        // will not execute it if the parent task is terminated.
        final GenericServerTask outerTask = GenericServerTask.create(null, task.getUserIdentity(), "Terminate: " + task.getTaskName(), "Terminating cluster tasks");
        outerTask.setRunnable(() -> {
            taskMonitor.info(task.getSearchName() + " - terminating child tasks");
            final FindTaskCriteria findTaskCriteria = new FindTaskCriteria();
            findTaskCriteria.addAncestorId(task.getId());
            final TerminateTaskClusterTask terminateTask = new TerminateTaskClusterTask(task.getUserIdentity(), "Terminate: " + task.getTaskName(), findTaskCriteria, false);

            // Terminate matching tasks.
            dispatchHelper.execAsync(terminateTask, TargetType.ACTIVE);
        });
        taskManager.execAsync(outerTask);
    }

    private String[] getStoredFields(final Index index) {
        final List<IndexField> indexFields = index.getIndexFieldsObject().getIndexFields();
        return indexFields
                .stream()
                .filter(IndexField::isStored)
                .map(IndexField::getFieldName)
                .toArray(String[]::new);
    }
}
