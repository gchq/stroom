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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MarkerFactory;
import stroom.cluster.ClusterCallService;
import stroom.node.NodeCache;
import stroom.node.shared.Node;
import stroom.task.TaskCallbackAdaptor;
import stroom.task.TaskManager;
import stroom.docref.SharedObject;
import stroom.util.shared.TaskId;

import javax.inject.Inject;
import javax.inject.Named;

public class ClusterWorkerImpl implements ClusterWorker {
    public static final String BEAN_NAME = "clusterWorker";
    static final String EXEC_ASYNC_METHOD = "execAsync";
    static final Class<?>[] EXEC_ASYNC_METHOD_ARGS = {ClusterTask.class, Node.class, TaskId.class, CollectorId.class};

    private static final Logger LOGGER = LoggerFactory.getLogger(ClusterWorkerImpl.class);
    private static final String EXEC_ASYNC = "execAsync";
    private static final String SEND_RESULT = "sendResult";

    private final TaskManager taskManager;
    private final NodeCache nodeCache;
    private final ClusterCallService clusterCallService;

    @Inject
    public ClusterWorkerImpl(final TaskManager taskManager,
                             final NodeCache nodeCache,
                             @Named("clusterCallServiceRemote") final ClusterCallService clusterCallService) {
        this.taskManager = taskManager;
        this.nodeCache = nodeCache;
        this.clusterCallService = clusterCallService;
    }

    /**
     * When a node wants to execute a task on another node in the cluster they
     * dispatch the task with <code>ClusterDispatchAsync.execAsync()</code>.
     * <code>ClusterDispatchAsync.execAsync()</code> makes a cluster call which
     * executes this method on target worker nodes. This method then hands off
     * execution to the task manager so that each worker node will execute the
     * task asynchronously without the source node waiting for a response which
     * would result in the HTTP connection being held too long. Once
     * asynchronous execution has completed another cluster call is made to pass
     * the execution result back to the source node.
     *
     * @param task         The task to execute on the target worker node.
     * @param sourceNode   The node that this task originated from.
     * @param sourceTaskId The id of the parent task that owns this worker cluster task.
     * @param collectorId  The id of the collector to send results back to.
     */
    @Override
    public <R extends SharedObject> void execAsync(final ClusterTask<R> task, final Node sourceNode,
                                                   final TaskId sourceTaskId, final CollectorId collectorId) {
        DebugTrace.debugTraceIn(task, EXEC_ASYNC, true);
        try {
            // Trace the source of this task if trace is enabled.
            LOGGER.trace("Executing task '{}' for node '{}'", task.getTaskName(), sourceNode.getName());

            final Node targetNode = nodeCache.getDefaultNode();

            // Assign the id for this worker node prior to execution.
            task.assignId(sourceTaskId);

            // Execute this task asynchronously so we don't hold on to the HTTP
            // connection.
            taskManager.execAsync(task, new TaskCallbackAdaptor<R>() {
                @Override
                public void onSuccess(final R result) {
                    // Send the successful result back to the source node.
                    sendResult(task, sourceNode, targetNode, sourceTaskId, collectorId, result, null, true);
                }

                @Override
                public void onFailure(final Throwable t) {
                    // Send the failure back to the source node.
                    sendResult(task, sourceNode, targetNode, sourceTaskId, collectorId, null, t, false);
                }
            });
        } catch (final RuntimeException e) {
            LOGGER.error(MarkerFactory.getMarker("FATAL"), e.getMessage(), e);

        } finally {
            DebugTrace.debugTraceOut(task, EXEC_ASYNC, true);
        }
    }

    /**
     * Once the target worker node has finished executing the task, this method
     * sends the result back to the source node.
     *
     * @param task         The task that was executed on the target worker node.
     * @param sourceNode   The source node to send the result back to.
     * @param targetNode   The target worker node that executed the task.
     * @param sourceTaskId The id of the parent task that owns this worker cluster task.
     * @param collectorId  The id of the collector to send results back to.
     * @param result       The result of the task execution.
     * @param t            An exception that may have been thrown during task execution
     *                     in the result of task failure.
     * @param success      Whether or not the task executed successfully.
     */
    private <R extends SharedObject> void sendResult(final ClusterTask<R> task, final Node sourceNode,
                                                     final Node targetNode, final TaskId sourceTaskId, final CollectorId collectorId, final R result,
                                                     final Throwable t, final boolean success) {
        int tryCount = 1;
        boolean done = false;
        Exception lastException = null;
        Object ok = null;

        DebugTrace.debugTraceIn(task, SEND_RESULT, true);
        try {
            LOGGER.debug("{}() - {}", SEND_RESULT, task);
            while (!done && tryCount <= 10) {
                try {
                    // Trace attempt to send result.
                    LOGGER.trace("Sending result for task '{}' to node '{}' (attempt={})", task.getTaskName(), sourceNode.getName(), tryCount);
                    // Send result.
                    ok = clusterCallService.call(targetNode, sourceNode, ClusterDispatchAsyncImpl.BEAN_NAME,
                            ClusterDispatchAsyncImpl.RECEIVE_RESULT_METHOD,
                            ClusterDispatchAsyncImpl.RECEIVE_RESULT_METHOD_ARGS, new Object[]{task, targetNode,
                                    sourceTaskId, collectorId, result, t, Boolean.valueOf(success)});
                    done = true;
                } catch (final RuntimeException e) {
                    lastException = e;
                    LOGGER.warn(e.getMessage());
                    LOGGER.debug(e.getMessage(), e);
                    sleepUpTo(1000L * tryCount);
                    tryCount++;
                }
            }
        } catch (final InterruptedException e) {
            LOGGER.error(MarkerFactory.getMarker("FATAL"), e.getMessage(), e);

            // Continue to interrupt this thread.
            Thread.currentThread().interrupt();

        } catch (final RuntimeException e) {
            LOGGER.error(MarkerFactory.getMarker("FATAL"), e.getMessage(), e);

        } finally {
            DebugTrace.debugTraceOut(task, SEND_RESULT, true);
        }

        LOGGER.trace("success={}, done={}", ok, done);

        // If the source node could not be contacted then throw an exception.
        if (!done) {
            final StringBuilder sb = new StringBuilder();
            sb.append("Unable to send result for task '");
            sb.append(task.getTaskName());
            sb.append("' back to source node '");
            sb.append(sourceNode.getName());
            sb.append("' after ");
            sb.append(tryCount);
            sb.append(" attempts");
            final String message = sb.toString();
            LOGGER.error(message, lastException);
        }

        // If the source node rejected the result then throw an exception. This
        // normally happens because the task has terminated on the source node.
        if (!Boolean.TRUE.equals(ok)) {
            final StringBuilder sb = new StringBuilder();
            sb.append("Unable to send result for task '");
            sb.append(task.getTaskName());
            sb.append("' back to source node '");
            sb.append(sourceNode.getName());
            sb.append("' because source node rejected result");
            final String message = sb.toString();
            LOGGER.info(message);

            // We must throw an exception here so that any code that is trying
            // to return a result knows that the result did not make it to the
            // requesting node.
            throw new RuntimeException("Unable to return result to requesting node or requesting node rejected result");
        }
    }

    private void sleepUpTo(final long millis) throws InterruptedException {
        if (millis > 0) {
            int realSleep = (int) Math.floor(Math.random() * (millis + 1));
            if (realSleep > 0) {
                Thread.sleep(realSleep);
            }
        }
    }
}
