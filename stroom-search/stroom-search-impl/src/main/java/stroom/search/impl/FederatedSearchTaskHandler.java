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
                    final List<CompletableFuture<Void>> futures = new ArrayList<>();
                    for (final Entry<String, NodeSearchTask> entry : nodeSearchTasks.entrySet()) {
                        final String nodeName = entry.getKey();
                        final NodeSearchTask nodeSearchTask = entry.getValue();
                        if (targetNodes.contains(nodeName)) {
                            final Runnable runnable = taskContextFactory.childContext(
                                    parentContext,
                                    "Search node: " + nodeName,
                                    taskContext -> {
                                        final NodeSearch nodeSearch;
                                        if (NodeCallUtil.shouldExecuteLocally(nodeInfo, nodeName)) {
                                            nodeSearch = localNodeSearchProvider.get();
                                        } else {
                                            nodeSearch = remoteNodeSearchProvider.get();
                                        }
                                        nodeSearch.searchNode(sourceNode,
                                                nodeName,
                                                task,
                                                nodeSearchTask,
                                                taskContext);
                                    });
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
