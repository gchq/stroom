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
 */

package stroom.search.elastic.search;

import stroom.query.common.v2.Coprocessors;
import stroom.query.common.v2.DataStore;
import stroom.query.common.v2.Sizes;
import stroom.query.common.v2.Store;
import stroom.task.api.TaskContext;
import stroom.task.api.TaskContextFactory;
import stroom.task.api.TaskTerminatedException;
import stroom.task.api.TerminateHandlerFactory;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import javax.inject.Provider;

public class ElasticSearchResultCollector implements Store {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ElasticSearchResultCollector.class);
    private static final String TASK_NAME = "Elasticsearch Cluster Search";

    private final Executor executor;
    private final TaskContextFactory taskContextFactory;
    private final Provider<ElasticAsyncSearchTaskHandler> elasticAsyncSearchTaskHandlerProvider;
    private final ElasticAsyncSearchTask task;
    private final Coprocessors coprocessors;
    private final Sizes maxResultSizes;

    private volatile ElasticAsyncSearchTaskHandler asyncSearchTaskHandler;
    private volatile TaskContext taskContext;
    private volatile boolean complete;

    private ElasticSearchResultCollector(
            final Executor executor,
            final TaskContextFactory taskContextFactory,
            final Provider<ElasticAsyncSearchTaskHandler> elasticAsyncSearchTaskHandlerProvider,
            final ElasticAsyncSearchTask task,
            final Coprocessors coprocessors,
            final Sizes maxResultSizes) {
        this.executor = executor;
        this.taskContextFactory = taskContextFactory;
        this.elasticAsyncSearchTaskHandlerProvider = elasticAsyncSearchTaskHandlerProvider;
        this.task = task;
        this.coprocessors = coprocessors;
        this.maxResultSizes = maxResultSizes;
    }

    public static ElasticSearchResultCollector create(
            final Executor executor,
            final TaskContextFactory taskContextFactory,
            final Provider<ElasticAsyncSearchTaskHandler> elasticAsyncSearchTaskHandlerProvider,
            final ElasticAsyncSearchTask task,
            final Coprocessors coprocessors,
            final Sizes maxResultSizes) {
        return new ElasticSearchResultCollector(
                executor,
                taskContextFactory,
                elasticAsyncSearchTaskHandlerProvider,
                task,
                coprocessors,
                maxResultSizes);
    }

    public void start() {
        // Start asynchronous search execution.
        final Runnable runnable = taskContextFactory.context(
                TASK_NAME,
                TerminateHandlerFactory.NOOP_FACTORY,
                taskContext -> {
                    this.taskContext = taskContext;
                    this.asyncSearchTaskHandler = elasticAsyncSearchTaskHandlerProvider.get();

                    // Don't begin execution if we have been asked to complete already.
                    if (!complete) {
                        asyncSearchTaskHandler.search(taskContext, task, coprocessors, this);
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
                            coprocessors.getErrorConsumer().add(t);
                            coprocessors.getCompletionState().signalComplete();
                            throw new RuntimeException(t.getMessage(), t);
                        }

                        coprocessors.getCompletionState().signalComplete();
                    }
                });
    }

    @Override
    public void destroy() {
        LOGGER.trace(() -> "destroy()", new RuntimeException("destroy"));
        complete = true;
        if (asyncSearchTaskHandler != null) {
            asyncSearchTaskHandler.terminateTasks(task, taskContext.getTaskId());
        }

        LOGGER.trace(() -> "coprocessors.clear()");
        coprocessors.clear();
    }

    public void signalComplete() {
        coprocessors.getCompletionState().signalComplete();
    }

    @Override
    public boolean isComplete() {
        return coprocessors.getCompletionState().isComplete();
    }

    @Override
    public void awaitCompletion() throws InterruptedException {
        coprocessors.getCompletionState().awaitCompletion();
    }

    @Override
    public boolean awaitCompletion(final long timeout, final TimeUnit unit) throws InterruptedException {
        return coprocessors.getCompletionState().awaitCompletion(timeout, unit);
    }

    @Override
    public List<String> getErrors() {
        return coprocessors.getErrorConsumer().getErrors();
    }

    @Override
    public List<String> getHighlights() {
        return null;
    }

    public Sizes getMaxResultSizes() {
        return maxResultSizes;
    }

    @Override
    public DataStore getData(final String componentId) {
        return coprocessors.getData(componentId);
    }

    @Override
    public String toString() {
        return "ClusterSearchResultCollector{" +
                "task=" + task +
                ", complete=" + coprocessors.getCompletionState() +
                '}';
    }
}
