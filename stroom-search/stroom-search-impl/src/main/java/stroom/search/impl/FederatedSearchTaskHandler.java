/*
 * Copyright 2016 Crown Copyright
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

import stroom.cluster.task.api.ClusterTaskTerminator;
import stroom.cluster.task.api.NodeNotFoundException;
import stroom.cluster.task.api.NullClusterStateException;
import stroom.cluster.task.api.TargetNodeSetFactory;
import stroom.node.api.NodeCallUtil;
import stroom.node.api.NodeInfo;
import stroom.query.api.Query;
import stroom.query.common.v2.ResultStore;
import stroom.security.api.SecurityContext;
import stroom.task.api.ExecutorProvider;
import stroom.task.api.TaskContext;
import stroom.task.api.TaskContextFactory;
import stroom.task.api.TaskManager;
import stroom.task.api.ThreadPoolImpl;
import stroom.task.shared.TaskId;
import stroom.task.shared.ThreadPool;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

class FederatedSearchTaskHandler {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(FederatedSearchTaskHandler.class);

    public static final ThreadPool THREAD_POOL = new ThreadPoolImpl("Search");

    private final TargetNodeSetFactory targetNodeSetFactory;
    private final TaskManager taskManager;
    private final ClusterTaskTerminator clusterTaskTerminator;
    private final SecurityContext securityContext;
    private final ExecutorProvider executorProvider;
    private final TaskContextFactory taskContextFactory;
    private final NodeInfo nodeInfo;
    private final Provider<LocalNodeSearch> localNodeSearchProvider;
    private final Provider<RemoteNodeSearch> remoteNodeSearchProvider;

    @Inject
    FederatedSearchTaskHandler(final TargetNodeSetFactory targetNodeSetFactory,
                               final TaskManager taskManager,
                               final ClusterTaskTerminator clusterTaskTerminator,
                               final SecurityContext securityContext,
                               final ExecutorProvider executorProvider,
                               final TaskContextFactory taskContextFactory,
                               final NodeInfo nodeInfo,
                               final Provider<LocalNodeSearch> localNodeSearchProvider,
                               final Provider<RemoteNodeSearch> remoteNodeSearchProvider) {
        this.targetNodeSetFactory = targetNodeSetFactory;
        this.taskManager = taskManager;
        this.clusterTaskTerminator = clusterTaskTerminator;
        this.securityContext = securityContext;
        this.executorProvider = executorProvider;
        this.taskContextFactory = taskContextFactory;
        this.nodeInfo = nodeInfo;
        this.localNodeSearchProvider = localNodeSearchProvider;
        this.remoteNodeSearchProvider = remoteNodeSearchProvider;
    }

    public void exec(final TaskContext parentContext,
                     final FederatedSearchTask task,
                     final NodeTaskCreator nodeTaskCreator) {
        securityContext.secure(() -> securityContext.useAsRead(() -> {
            final ResultStore resultCollector = task.getResultStore();

            if (!parentContext.isTerminated()) {
                final String sourceNode = targetNodeSetFactory.getSourceNode();

                // Create an async call that will terminate the whole task if the coprocessors decide they have enough
                // data.
                CompletableFuture.runAsync(() -> awaitCompletionAndTerminate(resultCollector, parentContext, task),
                        executorProvider.get());

                try {
                    parentContext.info(task::getSearchName);
                    final Query query = task.getQuery();

                    // Make sure we have been given a query.
                    if (query.getExpression() == null) {
                        throw new SearchException("Search expression has not been set");
                    }

                    // Get the cluster tasks we want to execute.
                    final Map<String, NodeSearchTask> nodeSearchTasks =
                            nodeTaskCreator.createNodeSearchTasks(task, query, parentContext);

                    // Get the nodes that we are going to send the search request to.
                    final Set<String> targetNodes = targetNodeSetFactory.getEnabledTargetNodeSet();

                    // Start remote cluster search execution.
                    final Executor executor = executorProvider.get(THREAD_POOL);
                    // The nodes that could not be reached, so that their work is not given to one another.
                    final Set<String> failedNodes = ConcurrentHashMap.newKeySet();
                    final List<CompletableFuture<Void>> futures = new ArrayList<>();
                    for (final Entry<String, NodeSearchTask> entry : nodeSearchTasks.entrySet()) {
                        final String nodeName = entry.getKey();
                        final NodeSearchTask nodeSearchTask = entry.getValue();
                        if (targetNodes.contains(nodeName)) {
                            final Runnable runnable = createNodeSearch(
                                    parentContext,
                                    task,
                                    nodeTaskCreator,
                                    sourceNode,
                                    nodeName,
                                    nodeSearchTask,
                                    failedNodes,
                                    executor);
                            final CompletableFuture<Void> completableFuture = CompletableFuture.runAsync(runnable,
                                    executor);
                            futures.add(completableFuture);
                        } else {
                            resultCollector.onFailure(nodeName,
                                    new SearchException(
                                            "Node is not enabled or active. Some search results may be missing."));
                        }
                    }

                    // Wait for all nodes to finish.
                    LOGGER.debug(() -> "Waiting for completion");
                    final CompletableFuture<Void> all = CompletableFuture.allOf(
                            futures.toArray(new CompletableFuture[0]));
                    all.join();
                    LOGGER.debug(() -> "Done waiting for completion");

                } catch (final RuntimeException | NodeNotFoundException | NullClusterStateException e) {
                    resultCollector.onFailure(sourceNode, e);

                } finally {
                    parentContext.info(() -> task.getSearchName() + " - complete");
                    LOGGER.debug(() -> task.getSearchName() + " - complete");

                    // Ensure search is complete even if we had errors.
                    resultCollector.signalComplete();

                    // Await final completion and terminate all tasks.
                    awaitCompletionAndTerminate(resultCollector, parentContext, task);

                    // We need to wait here for the client to keep getting results if
                    // this is an interactive search.
                    parentContext.info(() -> task.getSearchName() + " - staying alive for UI requests");
                }
            }
        }));
    }

    private Runnable createNodeSearch(final TaskContext parentContext,
                                      final FederatedSearchTask task,
                                      final NodeTaskCreator nodeTaskCreator,
                                      final String sourceNode,
                                      final String nodeName,
                                      final NodeSearchTask nodeSearchTask,
                                      final Set<String> failedNodes,
                                      final Executor executor) {
        return taskContextFactory.childContext(
                parentContext,
                "Search node: " + nodeName,
                taskContext -> {
                    final NodeSearch nodeSearch;
                    if (NodeCallUtil.shouldExecuteLocally(nodeInfo, nodeName)) {
                        nodeSearch = localNodeSearchProvider.get();
                    } else {
                        nodeSearch = remoteNodeSearchProvider.get();
                    }
                    try {
                        nodeSearch.searchNode(sourceNode,
                                nodeName,
                                task,
                                nodeSearchTask,
                                taskContext);
                    } catch (final NodeSearchStartException e) {
                        if (parentContext.isTerminated() || Thread.currentThread().isInterrupted()) {
                            // The search has been stopped, which is the likely reason the node could not
                            // be started, so there is no work to give to anyone.
                            LOGGER.debug(() -> task.getSearchName() + " - search of node " + nodeName
                                               + " not started as the search has ended: " + e.getMessage());
                            return;
                        }
                        // No results have been taken from the node, so its work can go to other nodes
                        // without any result being counted twice.
                        searchOtherNodes(
                                parentContext,
                                task,
                                nodeTaskCreator,
                                sourceNode,
                                nodeName,
                                nodeSearchTask,
                                failedNodes,
                                executor,
                                e);
                    }
                });
    }

    // Gives the work of a node that could not be reached to other nodes, and waits for them to do it.
    private void searchOtherNodes(final TaskContext parentContext,
                                  final FederatedSearchTask task,
                                  final NodeTaskCreator nodeTaskCreator,
                                  final String sourceNode,
                                  final String failedNode,
                                  final NodeSearchTask failedTask,
                                  final Set<String> failedNodes,
                                  final Executor executor,
                                  final NodeSearchStartException startException) {
        failedNodes.add(failedNode);

        NodeSearchReassignment reassignment;
        try {
            reassignment = nodeTaskCreator.reassign(failedTask, Set.copyOf(failedNodes));
        } catch (final RuntimeException e) {
            LOGGER.error(() -> "Error reassigning the search of node " + failedNode + ": " + e.getMessage(), e);
            reassignment = NodeSearchReassignment.NOT_POSSIBLE;
        }

        if (reassignment.workLost()) {
            // Some of what the node was to search can't be searched anywhere else.
            task.getResultStore().onFailure(failedNode, startException);
        } else {
            LOGGER.info(() -> task.getSearchName() + " - node " + failedNode
                              + " could not be reached so its shards are being searched by other nodes: "
                              + startException.getMessage());
        }

        // A node that also can't be reached has its share given out again in the same way. That ends
        // because each failure adds to the nodes that are left out.
        final List<CompletableFuture<Void>> futures = new ArrayList<>();
        reassignment.tasks().forEach((nodeName, nodeSearchTask) ->
                futures.add(CompletableFuture.runAsync(
                        createNodeSearch(
                                parentContext,
                                task,
                                nodeTaskCreator,
                                sourceNode,
                                nodeName,
                                nodeSearchTask,
                                failedNodes,
                                executor),
                        executor)));
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
    }

    private void awaitCompletionAndTerminate(final ResultStore resultCollector,
                                             final TaskContext parentContext,
                                             final FederatedSearchTask task) {
        // Wait for the result collector to complete.
        try {
            resultCollector.awaitCompletion();
        } catch (final InterruptedException e) {
            LOGGER.trace(e.getMessage(), e);
            // Keep interrupting this thread.
            Thread.currentThread().interrupt();
        } finally {
            // Make sure we try and terminate any child tasks on worker
            // nodes if we need to.
            terminateTasks(task, parentContext.getTaskId());
        }
    }

    public void terminateTasks(final FederatedSearchTask task, final TaskId taskId) {
        securityContext.asProcessingUser(() -> {
            // Terminate this task.
            taskManager.terminate(taskId);

            // We have to wrap the cluster termination task in another task or
            // ClusterDispatchAsyncImpl
            // will not execute it if the parent task is terminated.
            clusterTaskTerminator.terminate(task.getSearchName(), taskId, "AsyncSearchTask");
        });
    }
}
