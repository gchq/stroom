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

package stroom.cluster.task.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MarkerFactory;
import stroom.cluster.api.ClusterCallService;
import stroom.cluster.api.ClusterCallServiceRemote;
import stroom.cluster.api.ServiceName;
import stroom.cluster.task.api.ClusterDispatchAsync;
import stroom.cluster.task.api.ClusterResult;
import stroom.cluster.task.api.ClusterResultCollector;
import stroom.cluster.task.api.ClusterTask;
import stroom.cluster.task.api.ClusterTaskRef;
import stroom.cluster.task.api.CollectorId;
import stroom.security.api.SecurityContext;
import stroom.task.api.ExecutorProvider;
import stroom.task.api.SimpleThreadPool;
import stroom.task.api.TaskContext;
import stroom.task.api.TaskManager;
import stroom.task.impl.CurrentTaskState;
import stroom.task.shared.TaskId;
import stroom.task.shared.ThreadPool;
import stroom.util.logging.LogExecutionTime;
import stroom.util.shared.ModelStringUtil;

import javax.inject.Inject;
import javax.inject.Provider;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Entry to point to distribute cluster tasks in system.
 */
public class ClusterDispatchAsyncImpl implements ClusterDispatchAsync {
    static final ServiceName SERVICE_NAME = new ServiceName("clusterDispatchAsync");
    static final String RECEIVE_RESULT_METHOD = "receiveResult";
    private static final ThreadPool THREAD_POOL = new SimpleThreadPool(5);
    static final Class<?>[] RECEIVE_RESULT_METHOD_ARGS = {ClusterResult.class};
    private static final Logger LOGGER = LoggerFactory.getLogger(ClusterDispatchAsyncImpl.class);
    private static final String RECEIVE_RESULT = "receiveResult";

    private final TaskManager taskManager;
    private final Executor executor;
    private final Provider<TaskContext> taskContextProvider;
    private final ClusterResultCollectorCacheImpl collectorCache;
    private final ClusterCallService clusterCallService;
    private final SecurityContext securityContext;

    @Inject
    ClusterDispatchAsyncImpl(final TaskManager taskManager,
                             final ExecutorProvider executorProvider,
                             final Provider<TaskContext> taskContextProvider,
                             final ClusterResultCollectorCacheImpl collectorCache,
                             final ClusterCallServiceRemote clusterCallService,
                             final SecurityContext securityContext) {
        this.taskManager = taskManager;
        this.executor = executorProvider.get(THREAD_POOL);
        this.taskContextProvider = taskContextProvider;
        this.collectorCache = collectorCache;
        this.clusterCallService = clusterCallService;
        this.securityContext = securityContext;
    }

    @Override
    public <R> void execAsync(final ClusterTask<R> task,
                              final ClusterResultCollector<R> collector,
                              final String sourceNode,
                              final Set<String> targetNodes) {
        final TaskId parentTaskId = CurrentTaskState.currentTaskId();
        if (parentTaskId == null) {
            throw new NullPointerException("A source task must be provided");
        }

        execAsync(parentTaskId, task, collector, sourceNode, targetNodes);
    }

    private <R> void execAsync(final TaskId sourceTaskId,
                               final ClusterTask<R> clusterTask,
                               final ClusterResultCollector<R> collector,
                               final String sourceNode,
                               final Set<String> targetNodes) {
        if (sourceTaskId == null) {
            throw new NullPointerException("Null source task id");
        }
        if (taskManager.isTerminated(sourceTaskId)) {
            throw new RuntimeException("Task has been terminated");
        }
        if (clusterTask == null) {
            throw new NullPointerException("A cluster task must be provided");
        }
        if (collector == null) {
            throw new NullPointerException("A collector must be provided");
        }
        if (collector.getId() == null) {
            throw new NullPointerException("Null collector id");
        }
        if (sourceNode == null) {
            throw new NullPointerException("Null source node");
        }
        if (targetNodes == null || targetNodes.size() == 0) {
            throw new NullPointerException("No target nodes");
        }

        if (clusterTask.getTaskName() == null) {
            // Can't be done in the UI as GWT knows nothing about class etc.
            clusterTask.setTaskName("Cluster Task: " + ModelStringUtil.toDisplayValue(clusterTask.getClass().getSimpleName()));
        }

        final CollectorId collectorId = collector.getId();
        for (final String targetNode : targetNodes) {
            if (targetNode == null) {
                throw new NullPointerException("Null target node?");
            }

            // Create a task to make the cluster call.
            final String message = "Calling node '" +
                    targetNode +
                    "' for task '" +
                    clusterTask.getTaskName() +
                    "'";

            // Create a runnable so we can execute the remote call
            // asynchronously.
            final TaskContext taskContext = taskContextProvider.get();
            Runnable runnable = () -> {
                try {
                    taskContext.setName("Cluster Task");
                    taskContext.info(() -> message);

                    clusterCallService.call(sourceNode, targetNode, securityContext.getUserIdentity(), ClusterWorkerImpl.SERVICE_NAME,
                            ClusterWorkerImpl.EXEC_ASYNC_METHOD, ClusterWorkerImpl.EXEC_ASYNC_METHOD_ARGS,
                            new Object[]{clusterTask, sourceNode, sourceTaskId, collectorId});
                } catch (final RuntimeException e) {
                    LOGGER.debug(e.getMessage(), e);
                    collector.onFailure(targetNode, e);
                }
            };
            runnable = taskContextProvider.get().sub(runnable);

            // Execute the cluster call asynchronously so we don't block calls
            // to other nodes.
            LOGGER.trace(message);
            executor.execute(runnable);
        }
    }

    /**
     * This method receives results from worker nodes that have executed tasks
     * using the <code>execAsync()</code> method above. Received results are
     * processed by the named collector in a new task thread so that result
     * consumption does not hold on to the HTTP connection.
     *
     * @param clusterResult Result details.
     */
    @SuppressWarnings({"unchecked", "unused"}) // Called by hessian from a remote node
    public <R> Boolean receiveResult(final ClusterResult<R> clusterResult) {
        final ClusterTaskRef<R> ref = clusterResult.getClusterTaskRef();
        final AtomicBoolean successfullyReceived = new AtomicBoolean();

        DebugTrace.debugTraceIn(ref.getTask(), RECEIVE_RESULT, clusterResult.isSuccess());
        try {
            LOGGER.debug("{}() - {} {}", RECEIVE_RESULT, ref.getTask(), ref.getTargetNode());

            // Get the source id and check it is valid.
            if (ref.getSourceTaskId() == null) {
                throw new NullPointerException("No source id");
            }

            // Try and get an active task for this source task id.
            if (taskManager.isTerminated(ref.getSourceTaskId())) {
                // If we can't get an active source task then ignore the result
                // as we don't want to keep using the collector as it might not
                // have gone from the cache for some reason and we will just end
                // up keeping it alive.
                LOGGER.warn("Source task has terminated. Ignoring result...");

            } else {
                // See if we can get a collector for this result.
                final ClusterResultCollector<R> collector = (ClusterResultCollector<R>) collectorCache.get(ref.getCollectorId());
                if (collector == null) {
                    // There is no collector to receive this result.
                    LOGGER.debug("{}() - collector gone away - {} {}", RECEIVE_RESULT, ref.getTask().getTaskName(), ref.getSourceTaskId());

                } else {
                    // Make sure the collector is happy to receive this result.
                    // It might not be if it was told to wait and it's wait time
                    // elapsed.
                    if (collector.onReceive()) {
                        // Build a task description.
                        final StringBuilder sb = new StringBuilder();
                        if (clusterResult.isSuccess()) {
                            sb.append("Receiving success from ");
                        } else {
                            sb.append("Receiving failure from ");
                        }
                        sb.append(" '");
                        sb.append(ref.getTargetNode());
                        sb.append("' for task '");
                        sb.append(ref.getTask().getTaskName());
                        sb.append("'");
                        final String message = sb.toString();

                        final TaskContext taskContext = taskContextProvider.get();
                        Runnable runnable = () -> {
                            final LogExecutionTime logExecutionTime = new LogExecutionTime();
                            try {
                                taskContext.setName("Cluster result");
                                taskContext.info(() -> message);

                                if (clusterResult.isSuccess()) {
                                    collector.onSuccess(ref.getTargetNode(), clusterResult.getResult());
                                } else {
                                    collector.onFailure(ref.getTargetNode(), clusterResult.getThrowable());
                                }
                            } finally {
                                if (LOGGER.isDebugEnabled()) {
                                    LOGGER.debug("{}() - collector {} {} took {}", RECEIVE_RESULT, ref.getTask().getTaskName(), ref.getSourceTaskId(), logExecutionTime);
                                }
                                if (logExecutionTime.getDuration() > 1000) {
                                    LOGGER.warn("{}() - collector {} {} took {}", RECEIVE_RESULT, ref.getTask().getTaskName(), ref.getSourceTaskId(), logExecutionTime);
                                }
                            }
                        };
                        runnable = taskContextProvider.get().sub(runnable);

                        // Execute the task asynchronously so that we do not
                        // block the receipt of data which would hold on to the
                        // HTTP connection longer than necessary.
                        LOGGER.trace(message);
                        executor.execute(runnable);

                        successfullyReceived.set(true);
                    }
                }
            }
        } catch (final RuntimeException e) {
            LOGGER.error(MarkerFactory.getMarker("FATAL"), e.getMessage(), e);

        } finally {
            DebugTrace.debugTraceOut(ref.getTask(), RECEIVE_RESULT, clusterResult.isSuccess());
        }

        return successfullyReceived.get();
    }
}
