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

package stroom.search.solr.search;

import stroom.cluster.task.api.ClusterTaskTerminator;
import stroom.query.api.Query;
import stroom.query.common.v2.Coprocessors;
import stroom.query.common.v2.ResultStore;
import stroom.security.api.SecurityContext;
import stroom.task.api.ExecutorProvider;
import stroom.task.api.TaskContext;
import stroom.task.api.TaskManager;
import stroom.task.shared.TaskId;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import jakarta.inject.Inject;

import java.util.concurrent.CompletableFuture;

public class SolrAsyncSearchTaskHandler {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(SolrAsyncSearchTaskHandler.class);

    private final SecurityContext securityContext;
    private final ExecutorProvider executorProvider;
    private final SolrClusterSearchTaskHandler clusterSearchTaskHandler;
    private final TaskManager taskManager;
    private final ClusterTaskTerminator clusterTaskTerminator;

    @Inject
    SolrAsyncSearchTaskHandler(final SecurityContext securityContext,
                               final ExecutorProvider executorProvider,
                               final SolrClusterSearchTaskHandler clusterSearchTaskHandler,
                               final TaskManager taskManager,
                               final ClusterTaskTerminator clusterTaskTerminator) {
        this.securityContext = securityContext;
        this.executorProvider = executorProvider;
        this.clusterSearchTaskHandler = clusterSearchTaskHandler;
        this.taskManager = taskManager;
        this.clusterTaskTerminator = clusterTaskTerminator;
    }

    public void search(final TaskContext parentContext,
                       final SolrAsyncSearchTask task,
                       final Coprocessors coprocessors,
                       final ResultStore resultStore) {
        securityContext.secure(() -> securityContext.useAsRead(() -> {
            if (!Thread.currentThread().isInterrupted()) {

                // Create an async call that will terminate the whole task if the coprocessors decide they have enough
                // data.
                CompletableFuture.runAsync(() -> awaitCompletionAndTerminate(resultStore, parentContext, task),
                        executorProvider.get());

                try {
                    parentContext.info(task::getSearchName);
                    final Query query = task.getQuery();

                    if (coprocessors != null && coprocessors.isPresent()) {
                        // Start searching.
                        clusterSearchTaskHandler.search(
                                parentContext,
                                task.getKey(),
                                query,
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
                    resultStore.signalComplete();

                    // Await final completion and terminate all tasks.
                    awaitCompletionAndTerminate(resultStore, parentContext, task);

                    // We need to wait here for the client to keep getting results if
                    // this is an interactive search.
                    parentContext.info(() -> task.getSearchName() + " - staying alive for UI requests");
                }
            }
        }));
    }

    private void awaitCompletionAndTerminate(final ResultStore resultStore,
                                             final TaskContext parentContext,
                                             final SolrAsyncSearchTask task) {
        // Wait for the result collector to complete.
        try {
            resultStore.awaitCompletion();
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

    public void terminateTasks(final SolrAsyncSearchTask task, final TaskId taskId) {
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
