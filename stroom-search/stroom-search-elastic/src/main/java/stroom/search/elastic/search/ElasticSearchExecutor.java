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

package stroom.search.elastic.search;

import stroom.query.api.SearchTaskProgress;
import stroom.query.common.v2.ResultStore;
import stroom.query.common.v2.SearchProcess;
import stroom.task.api.TaskContextFactory;
import stroom.task.api.TaskManager;
import stroom.task.api.TaskTerminatedException;
import stroom.task.api.TerminateHandlerFactory;
import stroom.task.shared.TaskProgress;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

public class ElasticSearchExecutor {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ElasticSearchExecutor.class);
    private static final String TASK_NAME = "Elasticsearch Cluster Search";

    private final Executor executor;
    private final TaskContextFactory taskContextFactory;
    private final Provider<ElasticAsyncSearchTaskHandler> elasticAsyncSearchTaskHandlerProvider;
    private final TaskManager taskManager;

    @Inject
    ElasticSearchExecutor(final Executor executor,
                          final TaskContextFactory taskContextFactory,
                          final Provider<ElasticAsyncSearchTaskHandler> elasticAsyncSearchTaskHandlerProvider,
                          final TaskManager taskManager) {
        this.executor = executor;
        this.taskContextFactory = taskContextFactory;
        this.elasticAsyncSearchTaskHandlerProvider = elasticAsyncSearchTaskHandlerProvider;
        this.taskManager = taskManager;
    }

    public void start(final ElasticAsyncSearchTask task,
                      final ResultStore resultStore) {
        // Start asynchronous search execution.
        final Runnable runnable = taskContextFactory.context(
                TASK_NAME,
                TerminateHandlerFactory.NOOP_FACTORY,
                taskContext -> {
                    final AtomicBoolean destroyed = new AtomicBoolean();
                    final ElasticAsyncSearchTaskHandler asyncSearchTaskHandler =
                            elasticAsyncSearchTaskHandlerProvider.get();

                    final SearchProcess searchProcess = new SearchProcess() {
                        @Override
                        public SearchTaskProgress getSearchTaskProgress() {
                            final TaskProgress taskProgress =
                                    taskManager.getTaskProgress(taskContext);
                            if (taskProgress != null) {
                                return new SearchTaskProgress(
                                        taskProgress.getTaskName(),
                                        taskProgress.getTaskInfo(),
                                        taskProgress.getUserRef(),
                                        taskProgress.getThreadName(),
                                        taskProgress.getNodeName(),
                                        taskProgress.getSubmitTimeMs(),
                                        taskProgress.getTimeNowMs());
                            }
                            return null;
                        }

                        @Override
                        public void onTerminate() {
                            destroyed.set(true);
                            asyncSearchTaskHandler.terminateTasks(task, taskContext.getTaskId());
                        }
                    };

                    // Set the search process.
                    resultStore.setSearchProcess(searchProcess);

                    // Don't begin execution if we have been asked to complete already.
                    if (!destroyed.get()) {
                        asyncSearchTaskHandler.search(taskContext, task, resultStore.getCoprocessors(), resultStore);
                    }
                });
        CompletableFuture
                .runAsync(runnable, executor)
                .whenComplete((result, t) -> {
                    if (t != null) {
                        while (t instanceof CompletionException) {
                            t = t.getCause();
                        }

                        // We can expect some tasks to throw a task terminated exception
                        // as they may be terminated before we even try to execute them.
                        if (!(t instanceof TaskTerminatedException)) {
                            LOGGER.error(t.getMessage(), t);
                            resultStore.addError(t);
                            resultStore.signalComplete();
                            throw new RuntimeException(t.getMessage(), t);
                        }

                        resultStore.signalComplete();
                    }
                });
    }
}
