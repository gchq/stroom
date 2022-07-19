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

package stroom.search.elastic.search;

import stroom.cluster.task.api.ClusterTaskTerminator;
import stroom.query.api.v2.Query;
import stroom.query.common.v2.Coprocessors;
import stroom.security.api.SecurityContext;
import stroom.task.api.ExecutorProvider;
import stroom.task.api.TaskContext;
import stroom.task.api.TaskManager;
import stroom.task.shared.TaskId;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import java.util.concurrent.CompletableFuture;
import javax.inject.Inject;

public class ElasticAsyncSearchTaskHandler {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ElasticAsyncSearchTaskHandler.class);

    private final SecurityContext securityContext;
    private final ExecutorProvider executorProvider;
    private final ElasticClusterSearchTaskHandler clusterSearchTaskHandler;
    private final TaskManager taskManager;
    private final ClusterTaskTerminator clusterTaskTerminator;

    @Inject
    ElasticAsyncSearchTaskHandler(final SecurityContext securityContext,
                                  final ExecutorProvider executorProvider,
                                  final ElasticClusterSearchTaskHandler clusterSearchTaskHandler,
                                  final TaskManager taskManager,
                                  final ClusterTaskTerminator clusterTaskTerminator) {
        this.securityContext = securityContext;
        this.executorProvider = executorProvider;
        this.clusterSearchTaskHandler = clusterSearchTaskHandler;
        this.taskManager = taskManager;
        this.clusterTaskTerminator = clusterTaskTerminator;
    }

    public void search(final TaskContext parentContext,
                       final ElasticAsyncSearchTask task,
                       final Coprocessors coprocessors,
                       final ElasticSearchResultCollector resultCollector) {
        securityContext.secure(() -> securityContext.useAsRead(() -> {
            if (!parentContext.isTerminated()) {

                // Create an async call that will terminate the whole task if the coprocessors decide they have enough
                // data.
                CompletableFuture.runAsync(() -> awaitCompletionAndTerminate(resultCollector, parentContext, task),
                        executorProvider.get());

                try {
                    parentContext.info(() -> task.getSearchName() + " - initialising");
                    final Query query = task.getQuery();

                    if (coprocessors != null && coprocessors.size() > 0) {
                        // Start searching.
                        clusterSearchTaskHandler.search(
                                parentContext,
                                task.getKey(),
                                query,
                                task.getNow(),
                                task.getDateTimeSettings(),
                                coprocessors);

                        // Await completion.
                        parentContext.info(() -> task.getSearchName() + " - searching");
                    }

                } catch (final RuntimeException e) {
                    LOGGER.debug(e::getMessage, e);
                    coprocessors.getErrorConsumer().add(e);

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

    private void awaitCompletionAndTerminate(final ElasticSearchResultCollector resultCollector,
                                             final TaskContext parentContext,
                                             final ElasticAsyncSearchTask task) {
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

    public void terminateTasks(final ElasticAsyncSearchTask task, final TaskId taskId) {
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
