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

package stroom.search.solr.search;

import stroom.query.common.v2.Coprocessors;
import stroom.query.common.v2.DataStore;
import stroom.query.common.v2.Store;
import stroom.task.api.TaskContextFactory;
import stroom.task.api.TaskTerminatedException;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import javax.inject.Provider;

public class SolrSearchResultCollector implements Store {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(SolrSearchResultCollector.class);
    private static final String TASK_NAME = "SolrSearchTask";

    private final Executor executor;
    private final TaskContextFactory taskContextFactory;
    private final Provider<SolrAsyncSearchTaskHandler> solrAsyncSearchTaskHandlerProvider;
    private final SolrAsyncSearchTask task;
    private final Set<String> highlights;
    private final Coprocessors coprocessors;

    private SolrSearchResultCollector(final Executor executor,
                                      final TaskContextFactory taskContextFactory,
                                      final Provider<SolrAsyncSearchTaskHandler> solrAsyncSearchTaskHandlerProvider,
                                      final SolrAsyncSearchTask task,
                                      final Set<String> highlights,
                                      final Coprocessors coprocessors) {
        this.executor = executor;
        this.taskContextFactory = taskContextFactory;
        this.solrAsyncSearchTaskHandlerProvider = solrAsyncSearchTaskHandlerProvider;
        this.task = task;
        this.highlights = highlights;
        this.coprocessors = coprocessors;
    }

    public static SolrSearchResultCollector create(
            final Executor executor,
            final TaskContextFactory taskContextFactory,
            final Provider<SolrAsyncSearchTaskHandler> solrAsyncSearchTaskHandlerProvider,
            final SolrAsyncSearchTask task,
            final Set<String> highlights,
            final Coprocessors coprocessors) {

        return new SolrSearchResultCollector(executor,
                taskContextFactory,
                solrAsyncSearchTaskHandlerProvider,
                task,
                highlights,
                coprocessors);
    }

    public void start() {
        // Start asynchronous search execution.
        final Runnable runnable = taskContextFactory.context(TASK_NAME, taskContext -> {
            // Don't begin execution if we have been asked to complete already.
            if (!coprocessors.getCompletionState().isComplete()) {
                final SolrAsyncSearchTaskHandler asyncSearchTaskHandler = solrAsyncSearchTaskHandlerProvider.get();
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
        if (highlights == null || highlights.size() == 0) {
            return null;
        }
        return new ArrayList<>(highlights);
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
