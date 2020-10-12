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

package stroom.search.impl;

import stroom.cluster.task.api.ClusterResultCollector;
import stroom.cluster.task.api.ClusterResultCollectorCache;
import stroom.cluster.task.api.CollectorId;
import stroom.cluster.task.api.CollectorIdFactory;
import stroom.query.common.v2.CompletionState;
import stroom.query.common.v2.CoprocessorSettingsMap.CoprocessorKey;
import stroom.query.common.v2.Data;
import stroom.query.common.v2.Payload;
import stroom.query.common.v2.ResultHandler;
import stroom.query.common.v2.Sizes;
import stroom.query.common.v2.Store;
import stroom.search.resultsender.NodeResult;
import stroom.task.api.TaskContextFactory;
import stroom.task.api.TaskTerminatedException;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Provider;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class ClusterSearchResultCollector implements Store, ClusterResultCollector<NodeResult> {
    private static final Logger LOGGER = LoggerFactory.getLogger(ClusterSearchResultCollector.class);
    private static final LambdaLogger LAMBDA_LOGGER = LambdaLoggerFactory.getLogger(ClusterSearchResultCollector.class);
    private static final String TASK_NAME = "AsyncSearchTask";

    private final ClusterResultCollectorCache clusterResultCollectorCache;
    private final CollectorId id;
    private final ConcurrentHashMap<String, Set<String>> errors = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> remainingNodes = new ConcurrentHashMap<>();
    private final AtomicInteger remainingNodeCount = new AtomicInteger();
    private final CompletionState completionState = new CompletionState();
    private final Executor executor;
    private final TaskContextFactory taskContextFactory;
    private final Provider<AsyncSearchTaskHandler> asyncSearchTaskHandlerProvider;
    private final AsyncSearchTask task;
    private final String nodeName;
    private final Set<String> highlights;
    private final ResultHandler resultHandler;
    private final Sizes defaultMaxResultsSizes;
    private final Sizes storeSize;

    ClusterSearchResultCollector(final Executor executor,
                                 final TaskContextFactory taskContextFactory,
                                 final Provider<AsyncSearchTaskHandler> asyncSearchTaskHandlerProvider,
                                 final AsyncSearchTask task,
                                 final String nodeName,
                                 final Set<String> highlights,
                                 final ClusterResultCollectorCache clusterResultCollectorCache,
                                 final ResultHandler resultHandler,
                                 final Sizes defaultMaxResultsSizes,
                                 final Sizes storeSize) {
        this.executor = executor;
        this.taskContextFactory = taskContextFactory;
        this.asyncSearchTaskHandlerProvider = asyncSearchTaskHandlerProvider;
        this.task = task;
        this.nodeName = nodeName;
        this.highlights = highlights;
        this.clusterResultCollectorCache = clusterResultCollectorCache;
        this.resultHandler = resultHandler;
        this.defaultMaxResultsSizes = defaultMaxResultsSizes;
        this.storeSize = storeSize;

        id = CollectorIdFactory.create();

        clusterResultCollectorCache.put(id, this);
    }

    public void start() {
        // Start asynchronous search execution.
        final Runnable runnable = taskContextFactory.context(TASK_NAME, taskContext -> {
            // Don't begin execution if we have been asked to complete already.
            if (!completionState.isComplete()) {
                final AsyncSearchTaskHandler asyncSearchTaskHandler = asyncSearchTaskHandlerProvider.get();
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
                            getErrorSet(nodeName).add(t.getMessage());
                            completionState.complete();
                            throw new RuntimeException(t.getMessage(), t);
                        }

                        completionState.complete();
                    }
                });
    }

    @Override
    public void destroy() {
        clusterResultCollectorCache.remove(id);
        completionState.complete();
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

    @Override
    public CollectorId getId() {
        return id;
    }

    @Override
    public boolean onReceive() {
        return true;
    }

    @Override
    public void onSuccess(final String nodeName, final NodeResult result) {
        try {
            final Map<CoprocessorKey, Payload> payloadMap = result.getPayloadMap();
            final List<String> errors = result.getErrors();

            if (payloadMap != null) {
                resultHandler.handle(payloadMap);
            }

            if (errors != null) {
                getErrorSet(nodeName).addAll(errors);
            }

            if (result.isComplete()) {
                nodeComplete(nodeName);
            } else {
                final AtomicLong atomicLong = remainingNodes.get(nodeName);
                if (atomicLong == null) {
                    LOGGER.error("Received an unexpected node result from " + nodeName);
                } else {
                    atomicLong.set(System.currentTimeMillis());
                }
            }

        } catch (final RuntimeException e) {
            getErrorSet(nodeName).add(e.getMessage());
            nodeComplete(nodeName);

        } finally {
            if (remainingNodeCount.compareAndSet(0, 0)) {
                // All the results are in but we may still have work pending, so wait
                waitForPendingWork();
                completionState.complete();
            }
        }
    }

    public void waitForPendingWork() {
        LAMBDA_LOGGER.logDurationIfTraceEnabled(() -> {
            LOGGER.trace("No remaining nodes so wait for the result handler to clear any pending work");
            try {
                resultHandler.waitForPendingWork();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                LOGGER.debug("Thread interrupted waiting for resultHandler to finish pending work");
                // we will just let it complete as we have been interrupted
            }
        }, "Waiting for resultHandler to finish pending work");
    }

    @Override
    public void onFailure(final String nodeName, final Throwable throwable) {
        try {
            nodeComplete(nodeName);
            getErrorSet(nodeName).add(throwable.getMessage());
        } finally {
            if (remainingNodeCount.compareAndSet(0, 0)) {
                completionState.complete();
            }
        }
    }

    private void nodeComplete(final String nodeName) {
        if (remainingNodes.remove(nodeName) != null) {
            remainingNodeCount.decrementAndGet();
        }
    }

    public Set<String> getErrorSet(final String nodeName) {
        Set<String> errorSet = errors.get(nodeName);
        if (errorSet == null) {
            errorSet = new HashSet<>();
            final Set<String> existing = errors.putIfAbsent(nodeName, errorSet);
            if (existing != null) {
                errorSet = existing;
            }
        }
        return errorSet;
    }

    @Override
    public List<String> getErrors() {
        if (errors.size() == 0) {
            return null;
        }

        final List<String> err = new ArrayList<>();
        for (final Entry<String, Set<String>> entry : errors.entrySet()) {
            final String nodeName = entry.getKey();
            final Set<String> errors = entry.getValue();

            if (errors.size() > 0) {
                err.add("Node: " + nodeName);

                for (final String error : errors) {
                    err.add("\t" + error);
                }
            }
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
        // Keep the cluster result collector cache fresh.
        clusterResultCollectorCache.get(getId());

        return resultHandler.getResultStore(componentId);
    }

    @Override
    public String toString() {
        return "ClusterSearchResultCollector{" +
                "task=" + task +
                ", complete=" + completionState.isComplete() +
                '}';
    }

    void setExpectedNodes(final Set<String> expectedNodes) {
        expectedNodes.forEach(node -> remainingNodes.put(node, new AtomicLong()));
        remainingNodeCount.set(expectedNodes.size());
    }
}
