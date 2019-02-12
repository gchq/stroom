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

package stroom.search;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.query.common.v2.CompletionState;
import stroom.query.common.v2.CoprocessorSettingsMap.CoprocessorKey;
import stroom.query.common.v2.Data;
import stroom.query.common.v2.Payload;
import stroom.query.common.v2.ResultHandler;
import stroom.query.common.v2.Store;
import stroom.query.common.v2.Sizes;
import stroom.task.impl.GenericServerTask;
import stroom.task.api.TaskManager;
import stroom.task.impl.TaskTerminatedException;
import stroom.task.api.TaskCallback;
import stroom.task.api.TaskContext;
import stroom.task.cluster.impl.ClusterResultCollectorCache;
import stroom.task.cluster.impl.CollectorIdFactory;
import stroom.task.cluster.impl.TerminateTaskClusterTask;
import stroom.task.cluster.api.ClusterDispatchAsyncHelper;
import stroom.task.cluster.api.ClusterResultCollector;
import stroom.task.cluster.api.CollectorId;
import stroom.task.cluster.api.TargetType;
import stroom.task.shared.FindTaskCriteria;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.VoidResult;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class ClusterSearchResultCollector implements Store, ClusterResultCollector<NodeResult> {
    private static final Logger LOGGER = LoggerFactory.getLogger(ClusterSearchResultCollector.class);
    private static final LambdaLogger LAMBDA_LOGGER = LambdaLoggerFactory.getLogger(ClusterSearchResultCollector.class);

    private final ClusterResultCollectorCache clusterResultCollectorCache;
    private final CollectorId id;
    private final ConcurrentHashMap<String, Set<String>> errors = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> remainingNodes = new ConcurrentHashMap<>();
    private final AtomicInteger remainingNodeCount = new AtomicInteger();
    private final TaskManager taskManager;
    private final TaskContext taskContext;
    private final AsyncSearchTask task;
    private final ClusterDispatchAsyncHelper dispatchHelper;
    private final String nodeName;
    private final Set<String> highlights;
    private final ResultHandler resultHandler;
    private final Sizes defaultMaxResultsSizes;
    private final Sizes storeSize;
    private final CompletionState completionState;

    ClusterSearchResultCollector(final TaskManager taskManager,
                                 final TaskContext taskContext,
                                 final AsyncSearchTask task,
                                 final ClusterDispatchAsyncHelper dispatchHelper,
                                 final String nodeName,
                                 final Set<String> highlights,
                                 final ClusterResultCollectorCache clusterResultCollectorCache,
                                 final ResultHandler resultHandler,
                                 final Sizes defaultMaxResultsSizes,
                                 final Sizes storeSize,
                                 final CompletionState completionState) {
        this.taskManager = taskManager;
        this.taskContext = taskContext;
        this.task = task;
        this.dispatchHelper = dispatchHelper;
        this.nodeName = nodeName;
        this.highlights = highlights;
        this.clusterResultCollectorCache = clusterResultCollectorCache;
        this.resultHandler = resultHandler;
        this.defaultMaxResultsSizes = defaultMaxResultsSizes;
        this.storeSize = storeSize;
        this.completionState = completionState;

        id = CollectorIdFactory.create();

        clusterResultCollectorCache.put(id, this);
    }

    public void start() {
        // Start asynchronous search execution.
        taskManager.execAsync(task, new TaskCallback<>() {
            @Override
            public void onSuccess(final VoidResult result) {
                // Do nothing here as the results go into the collector
            }

            @Override
            public void onFailure(final Throwable t) {
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
        complete();
    }

    public void complete() {
        completionState.complete();

        // We have to wrap the cluster termination task in another task or
        // ClusterDispatchAsyncImpl
        // will not execute it if the parent task is terminated.
        final GenericServerTask outerTask = GenericServerTask.create(null, task.getUserToken(), "Terminate: " + task.getTaskName(), "Terminating cluster tasks");
        outerTask.setRunnable(() -> {
            taskContext.info(task.getSearchName() + " - terminating child tasks");
            final FindTaskCriteria findTaskCriteria = new FindTaskCriteria();
            findTaskCriteria.addAncestorId(task.getId());
            final TerminateTaskClusterTask terminateTask = new TerminateTaskClusterTask(task.getUserToken(), "Terminate: " + task.getTaskName(), findTaskCriteria, false);

            // Terminate matching tasks.
            dispatchHelper.execAsync(terminateTask, TargetType.ACTIVE);
        });
        taskManager.execAsync(outerTask);
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
            nodeComplete(nodeName);
            getErrorSet(nodeName).add(e.getMessage());

        } finally {
            if (remainingNodeCount.compareAndSet(0, 0)) {
                // All the results are in but we may still have work pending, so wait
                waitForPendingWork();
                completionState.complete();
            }
        }
    }

    private void waitForPendingWork() {
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

    @Override
    public void terminate() {
        complete();
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
        if (errors == null || errors.size() == 0) {
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
                '}';
    }

    void setExpectedNodes(final Set<String> expectedNodes) {
        expectedNodes.forEach(node -> remainingNodes.put(node, new AtomicLong()));
        remainingNodeCount.set(expectedNodes.size());
    }
}
