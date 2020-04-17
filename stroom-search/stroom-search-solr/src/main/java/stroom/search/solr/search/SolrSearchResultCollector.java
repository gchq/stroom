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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.query.common.v2.*;
import stroom.query.common.v2.CoprocessorSettingsMap.CoprocessorKey;
import stroom.search.resultsender.NodeResult;
import stroom.task.api.TaskContextFactory;
import stroom.task.api.TaskTerminatedException;

import javax.inject.Provider;
import java.util.*;
import java.util.concurrent.*;

public class SolrSearchResultCollector implements Store {
    private static final Logger LOGGER = LoggerFactory.getLogger(SolrSearchResultCollector.class);
    private static final String TASK_NAME = "SolrSearchTask";

    private final Set<String> errors = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Executor executor;
    private final TaskContextFactory taskContextFactory;
    private final Provider<SolrAsyncSearchTaskHandler> solrAsyncSearchTaskHandlerProvider;
    private final SolrAsyncSearchTask task;
    private final Set<String> highlights;
    private final ResultHandler resultHandler;
    private final Sizes defaultMaxResultsSizes;
    private final Sizes storeSize;
    private final CompletionState completionState;

    private SolrSearchResultCollector(final Executor executor,
                                      final TaskContextFactory taskContextFactory,
                                      final Provider<SolrAsyncSearchTaskHandler> solrAsyncSearchTaskHandlerProvider,
                                      final SolrAsyncSearchTask task,
                                      final Set<String> highlights,
                                      final ResultHandler resultHandler,
                                      final Sizes defaultMaxResultsSizes,
                                      final Sizes storeSize,
                                      final CompletionState completionState) {
        this.executor = executor;
        this.taskContextFactory = taskContextFactory;
        this.solrAsyncSearchTaskHandlerProvider = solrAsyncSearchTaskHandlerProvider;
        this.task = task;
        this.highlights = highlights;
        this.resultHandler = resultHandler;
        this.defaultMaxResultsSizes = defaultMaxResultsSizes;
        this.storeSize = storeSize;
        this.completionState = completionState;
    }

    public static SolrSearchResultCollector create(final Executor executor,
                                                   final TaskContextFactory taskContextFactory,
                                                   final Provider<SolrAsyncSearchTaskHandler> solrAsyncSearchTaskHandlerProvider,
                                                   final SolrAsyncSearchTask task,
                                                   final Set<String> highlights,
                                                   final ResultHandler resultHandler,
                                                   final Sizes defaultMaxResultsSizes,
                                                   final Sizes storeSize,
                                                   final CompletionState completionState) {
        return new SolrSearchResultCollector(executor,
                taskContextFactory,
                solrAsyncSearchTaskHandlerProvider,
                task,
                highlights,
                resultHandler,
                defaultMaxResultsSizes,
                storeSize,
                completionState);
    }

    public void start() {
        // Start asynchronous search execution.
        final Runnable runnable = taskContextFactory.context(TASK_NAME, taskContext -> {
            // Don't begin execution if we have been asked to complete already.
            if (!completionState.isComplete()) {
                final SolrAsyncSearchTaskHandler asyncSearchTaskHandler = solrAsyncSearchTaskHandlerProvider.get();
                asyncSearchTaskHandler.exec(taskContext, task);
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
                            getErrorSet().add(t.getMessage());
                            completionState.complete();
                            throw new RuntimeException(t.getMessage(), t);
                        }

                        completionState.complete();
                    }
                });
    }

    @Override
    public void destroy() {
        complete();
    }

    public void complete() {
        completionState.complete();
    }

    @Override
    public boolean isComplete() {
        return completionState.isComplete();
    }

    @Override
    public void awaitCompletion() throws InterruptedException {
        completionState.awaitCompletion();
    }

    @Override
    public boolean awaitCompletion(final long timeout, final TimeUnit unit) throws InterruptedException {
        return completionState.awaitCompletion(timeout, unit);
    }

    public void onSuccess(final NodeResult result) {
        try {
            final Map<CoprocessorKey, Payload> payloadMap = result.getPayloadMap();
            final List<String> errors = result.getErrors();

            if (payloadMap != null) {
                resultHandler.handle(payloadMap);
            }
            if (errors != null) {
                getErrorSet().addAll(errors);
            }

            if (result.isComplete()) {
                complete();
            }

        } catch (final RuntimeException e) {
            getErrorSet().add(e.getMessage());
            complete();

        }
    }

    public void onFailure(final Throwable throwable) {
        complete();
        errors.add(throwable.getMessage());
    }

    public void terminate() {
        complete();
    }

    public Set<String> getErrorSet() {
        return errors;
    }

    @Override
    public List<String> getErrors() {
        if (errors.size() == 0) {
            return null;
        }

        final List<String> err = new ArrayList<>();
        for (final String error : errors) {
            err.add("\t" + error);
        }

        return err;
    }

    @Override
    public List<String> getHighlights() {
        if (highlights == null || highlights.size() == 0) {
            return null;
        }
        return new ArrayList<>(highlights);
    }

    @Override
    public Sizes getDefaultMaxResultsSizes() {
        return defaultMaxResultsSizes;
    }

    @Override
    public Sizes getStoreSize() {
        return storeSize;
    }

    @Override
    public Data getData(final String componentId) {
        return resultHandler.getResultStore(componentId);
    }

    @Override
    public String toString() {
        return "ClusterSearchResultCollector{" +
                "task=" + task +
                '}';
    }
}
