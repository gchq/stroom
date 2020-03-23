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

import org.slf4j.MarkerFactory;
import stroom.cluster.api.ClusterCallService;
import stroom.cluster.api.ClusterCallServiceRemote;
import stroom.cluster.api.ServiceName;
import stroom.cluster.task.api.ClusterResult;
import stroom.cluster.task.api.ClusterTask;
import stroom.cluster.task.api.ClusterTaskHandler;
import stroom.cluster.task.api.ClusterTaskRef;
import stroom.cluster.task.api.ClusterWorker;
import stroom.cluster.task.api.CollectorId;
import stroom.node.api.NodeInfo;
import stroom.security.api.SecurityContext;
import stroom.task.api.TaskContext;
import stroom.task.api.TaskTerminatedException;
import stroom.task.shared.TaskId;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import javax.inject.Inject;
import javax.inject.Provider;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;

public class ClusterWorkerImpl implements ClusterWorker {
    static final ServiceName SERVICE_NAME = new ServiceName("clusterWorker");
    static final String EXEC_ASYNC_METHOD = "execAsync";
    static final Class<?>[] EXEC_ASYNC_METHOD_ARGS = {ClusterTask.class, String.class, TaskId.class, CollectorId.class};

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ClusterWorkerImpl.class);
    private static final String EXEC_ASYNC = "execAsync";
    private static final String SEND_RESULT = "sendResult";

    private final Executor executor;
    private final Provider<TaskContext> taskContextProvider;
    private final ClusterTaskHandlerRegistry taskHandlerRegistry;
    private final NodeInfo nodeInfo;
    private final ClusterCallService clusterCallService;
    private final SecurityContext securityContext;

    @Inject
    public ClusterWorkerImpl(final Executor executor,
                             final Provider<TaskContext> taskContextProvider,
                             final ClusterTaskHandlerRegistry taskHandlerRegistry,
                             final NodeInfo nodeInfo,
                             final ClusterCallServiceRemote clusterCallService,
                             final SecurityContext securityContext) {
        this.executor = executor;
        this.taskContextProvider = taskContextProvider;
        this.taskHandlerRegistry = taskHandlerRegistry;
        this.nodeInfo = nodeInfo;
        this.clusterCallService = clusterCallService;
        this.securityContext = securityContext;
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
    @SuppressWarnings("unused") // Called by hessian from a remote node
    public <R> void execAsync(final ClusterTask<R> task, final String sourceNode,
                              final TaskId sourceTaskId, final CollectorId collectorId) {
        DebugTrace.debugTraceIn(task, EXEC_ASYNC, true);
        try {
            // Trace the source of this task if trace is enabled.
            LOGGER.trace("Executing task '{}' for node '{}'", task.getTaskName(), sourceNode);

            final String targetNode = nodeInfo.getThisNodeName();

            // Assign the id for this worker node prior to execution.
            task.assignId(sourceTaskId);

            final ClusterTaskRef<R> clusterTaskRef = new ClusterTaskRef<>(task, sourceNode, targetNode, sourceTaskId, collectorId);
            Runnable runnable = () -> {
                final ClusterTaskHandler<ClusterTask<R>, R> handler = taskHandlerRegistry.findHandler(task);
                handler.exec(task, clusterTaskRef);
            };
            runnable = taskContextProvider.get().subTask(runnable);

            // Execute this task asynchronously so we don't hold on to the HTTP
            // connection.
            CompletableFuture
                    .runAsync(runnable, executor)
                    .whenComplete((result, throwable) -> {
                        if (throwable != null) {
                            while (throwable instanceof CompletionException) {
                                throwable = throwable.getCause();
                            }
                            final Throwable t = throwable;

                            final String taskName = task.getTaskName();

                            if (t instanceof ThreadDeath || t instanceof TaskTerminatedException) {
                                LOGGER.warn(() -> "exec() - Task killed! (" + taskName + ")");
                                LOGGER.debug(() -> "exec() (" + taskName + ")", t);
                            } else {
                                LOGGER.error(() -> t.getMessage() + " (" + taskName + ")", t);
                            }
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
     * @param clusterResult Result details.
     */
    @Override
    public <R> void sendResult(final ClusterResult<R> clusterResult) {
        final ClusterTaskRef<R> ref = clusterResult.getClusterTaskRef();

        int tryCount = 1;
        boolean done = false;
        Exception lastException = null;
        Object ok = null;

        DebugTrace.debugTraceIn(ref.getTask(), SEND_RESULT, true);
        try {
            LOGGER.debug("{}() - {}", SEND_RESULT, ref.getTask());
            while (!done && tryCount <= 10) {
                try {
                    // Trace attempt to send result.
                    LOGGER.trace("Sending result for task '{}' to node '{}' (attempt={})", ref.getTask().getTaskName(), ref.getSourceNode(), tryCount);
                    // Send result.
                    ok = clusterCallService.call(ref.getTargetNode(), ref.getSourceNode(), securityContext.getUserIdentity(), ClusterDispatchAsyncImpl.SERVICE_NAME,
                            ClusterDispatchAsyncImpl.RECEIVE_RESULT_METHOD,
                            ClusterDispatchAsyncImpl.RECEIVE_RESULT_METHOD_ARGS, new Object[]{clusterResult});
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
            DebugTrace.debugTraceOut(ref.getTask(), SEND_RESULT, true);
        }

        LOGGER.trace("success={}, done={}", ok, done);

        // If the source node could not be contacted then throw an exception.
        if (!done) {
            final String message = "Unable to send result for task '" +
                    ref.getTask().getTaskName() +
                    "' back to source node '" +
                    ref.getSourceNode() +
                    "' after " +
                    tryCount +
                    " attempts";
            LOGGER.error(message, lastException);
        }

        // If the source node rejected the result then throw an exception. This
        // normally happens because the task has terminated on the source node.
        if (!Boolean.TRUE.equals(ok)) {
            final String message = "Unable to send result for task '" +
                    ref.getTask().getTaskName() +
                    "' back to source node '" +
                    ref.getSourceNode() +
                    "' because source node rejected result";
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
