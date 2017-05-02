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

package stroom.task.cluster;

import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import stroom.cluster.server.ClusterCallService;
import stroom.node.shared.Node;
import stroom.task.server.GenericServerTask;
import stroom.task.server.TaskManager;
import stroom.util.logging.LogExecutionTime;
import stroom.util.logging.StroomLogger;
import stroom.util.shared.ModelStringUtil;
import stroom.util.shared.SharedObject;
import stroom.util.shared.SimpleThreadPool;
import stroom.util.shared.Task;
import stroom.util.shared.TaskId;
import stroom.util.shared.ThreadPool;
import stroom.util.task.TaskScopeContextHolder;
import stroom.util.thread.ThreadUtil;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.Set;

/**
 * Entry to point to distribute cluster tasks in system.
 */
@Component(ClusterDispatchAsyncImpl.BEAN_NAME)
@Lazy
public class ClusterDispatchAsyncImpl implements ClusterDispatchAsync {
    private static final StroomLogger LOGGER = StroomLogger.getLogger(ClusterDispatchAsyncImpl.class);

    public static final String BEAN_NAME = "clusterDispatchAsync";
    public static final String RECEIVE_RESULT_METHOD = "receiveResult";
    static final Class<?>[] RECEIVE_RESULT_METHOD_ARGS = {ClusterTask.class, Node.class, TaskId.class,
            CollectorId.class, SharedObject.class, Throwable.class, Boolean.class};

    public static final ThreadPool THREAD_POOL = new SimpleThreadPool(5);
    private static final String RECEIVE_RESULT = "receiveResult";
    private static final Long DEBUG_REQUEST_DELAY = null;

    private final TaskManager taskManager;
    private final ClusterResultCollectorCache collectorCache;
    private final ClusterCallService clusterCallService;
    private String receiveResult;

    @Inject
    public ClusterDispatchAsyncImpl(final TaskManager taskManager, final ClusterResultCollectorCache collectorCache,
                                    @Named("clusterCallServiceRemote") final ClusterCallService clusterCallService) {
        this.taskManager = taskManager;
        this.collectorCache = collectorCache;
        this.clusterCallService = clusterCallService;
    }

    @Override
    public <R extends SharedObject> void execAsync(final ClusterTask<R> task, final ClusterResultCollector<R> collector,
                                                   final Node sourceNode, final Set<Node> targetNodes) {
        // Try and discover the parent task for this task as one hasn't been
        // supplied.
        if (!TaskScopeContextHolder.contextExists()) {
            throw new IllegalStateException("Task scope context does not exist!");
        }

        final Task<?> parentTask = TaskScopeContextHolder.getContext().getTask();
        execAsync(parentTask, task, collector, sourceNode, targetNodes);
    }

    private <R extends SharedObject> void execAsync(final Task<?> sourceTask, final ClusterTask<R> clusterTask,
                                                    final ClusterResultCollector<R> collector, final Node sourceNode, final Set<Node> targetNodes) {
        if (sourceTask == null) {
            throw new NullPointerException("A source task must be provided");
        }
        if (sourceTask.isTerminated()) {
            throw new RuntimeException("Task has been terminated");
        }
        if (sourceTask.getId() == null) {
            throw new NullPointerException("Null source task id");
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

        final TaskId sourceTaskId = sourceTask.getId();
        final CollectorId collectorId = collector.getId();
        for (final Node targetNode : targetNodes) {
            if (targetNode == null) {
                throw new NullPointerException("Null target node?");
            }

            // Create a task to make the cluster call.
            final StringBuilder sb = new StringBuilder();
            sb.append("Calling node '");
            sb.append(targetNode.getName());
            sb.append("' for task '");
            sb.append(clusterTask.getTaskName());
            sb.append("'");
            final String message = sb.toString();

            final GenericServerTask clusterCallTask = GenericServerTask.create(sourceTask, clusterTask.getUserToken(), "Cluster call", message);
            // Create a runnable so we can execute the remote call
            // asynchronously.
            clusterCallTask.setRunnable(() -> {
                try {
                    clusterCallService.call(sourceNode, targetNode, ClusterWorkerImpl.BEAN_NAME,
                            ClusterWorkerImpl.EXEC_ASYNC_METHOD, ClusterWorkerImpl.EXEC_ASYNC_METHOD_ARGS,
                            new Object[]{clusterTask, sourceNode, sourceTaskId, collectorId});
                } catch (final Throwable t) {
                    LOGGER.debug(t.getMessage(), t);
                    collector.onFailure(targetNode, t);
                }
            });

            // Execute the cluster call asynchronously so we don't block calls
            // to other nodes.
            LOGGER.trace(message);
            ThreadUtil.sleep(DEBUG_REQUEST_DELAY);
            taskManager.execAsync(clusterCallTask, THREAD_POOL);
        }
    }

    /**
     * This method receives results from worker nodes that have executed tasks
     * using the <code>execAsync()</code> method above. Received results are
     * processed by the named collector in a new task thread so that result
     * consumption does not hold on to the HTTP connection.
     *
     * @param task         The task that was executed on the target worker node.
     * @param targetNode   The worker node that is returning the result.
     * @param sourceTaskId The id of the parent task that owns this worker cluster task.
     * @param collectorId  The id of the collector to send results back to.
     * @param result       The result of the remote task execution.
     * @param throwable    An exception that may have been thrown during remote task
     *                     execution in the result of task failure.
     * @param success      Whether or not the remote task executed successfully.
     */
    @SuppressWarnings("unchecked")
    public <R extends SharedObject> Boolean receiveResult(final stroom.task.cluster.ClusterTask<R> task,
                                                          final Node targetNode, final TaskId sourceTaskId, final CollectorId collectorId, final R result,
                                                          final Throwable throwable, final Boolean success) {
        boolean successfullyReceived = false;

        DebugTrace.debugTraceIn(task, receiveResult, success);
        try {
            LOGGER.debug("%s() - %s %s", RECEIVE_RESULT, task, targetNode);

            // Get the source id and check it is valid.
            if (sourceTaskId == null) {
                throw new NullPointerException("No source id");
            }

            // Try and get an active task for this source task id.
            final Task<?> sourceTask = taskManager.getTaskById(sourceTaskId);
            if (sourceTask == null || sourceTask.isTerminated()) {
                // If we can't get an active source task then ignore the result
                // as we don't want to keep using the collector as it might not
                // have gone from the cache for some reason and we will just end
                // up keeping it alive.
                LOGGER.warn("Source task has terminated. Ignoring result...");

            } else {
                // See if we can get a collector for this result.
                final ClusterResultCollector<R> collector = (ClusterResultCollector<R>) collectorCache.get(collectorId);
                if (collector == null) {
                    // There is no collector to receive this result.
                    LOGGER.error("%s() - collector gone away - %s %s", RECEIVE_RESULT, task.getTaskName(), sourceTask);

                } else {
                    // Make sure the collector is happy to receive this result.
                    // It might not be if it was told to wait and it's wait time
                    // elapsed.
                    if (collector.onReceive()) {
                        // Build a task description.
                        final StringBuilder sb = new StringBuilder();
                        if (success) {
                            sb.append("Receiving success from ");
                        } else {
                            sb.append("Receiving failure from ");
                        }
                        sb.append(" '");
                        sb.append(targetNode.getName());
                        sb.append("' for task '");
                        sb.append(task.getTaskName());
                        sb.append("'");
                        final String message = sb.toString();

                        final GenericServerTask genericServerTask = GenericServerTask.create(sourceTask, task.getUserToken(), "Cluster result", message);
                        genericServerTask.setRunnable(() -> {
                            final LogExecutionTime logExecutionTime = new LogExecutionTime();
                            try {
                                if (success) {
                                    collector.onSuccess(targetNode, result);
                                } else {
                                    collector.onFailure(targetNode, throwable);
                                }
                            } finally {
                                if (LOGGER.isDebugEnabled()) {
                                    LOGGER.debug("%s() - collector %s %s took %s", RECEIVE_RESULT,
                                            task.getTaskName(), sourceTask, logExecutionTime);
                                }
                                if (logExecutionTime.getDuration() > 1000) {
                                    LOGGER.warn("%s() - collector %s %s took %s", RECEIVE_RESULT,
                                            task.getTaskName(), sourceTask, logExecutionTime);
                                }
                            }
                        });

                        // Execute the task asynchronously so that we do not
                        // block the receipt of data which would hold on to the
                        // HTTP connection longer than necessary.
                        LOGGER.trace(message);
                        taskManager.execAsync(genericServerTask, THREAD_POOL);
                        successfullyReceived = true;
                    }
                }
            }
        } catch (final Throwable e) {
            LOGGER.fatal(e.getMessage(), e);

        } finally {
            DebugTrace.debugTraceOut(task, RECEIVE_RESULT, success);
        }

        return successfullyReceived;
    }
}
