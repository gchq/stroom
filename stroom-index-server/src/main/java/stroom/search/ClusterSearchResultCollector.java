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
import stroom.node.shared.Node;
import stroom.query.common.v2.CompletionState;
import stroom.query.common.v2.CoprocessorSettingsMap.CoprocessorKey;
import stroom.query.common.v2.Data;
import stroom.query.common.v2.Payload;
import stroom.query.common.v2.ResultHandler;
import stroom.query.common.v2.Store;
import stroom.query.common.v2.StoreSize;
import stroom.task.GenericServerTask;
import stroom.task.TaskCallback;
import stroom.task.TaskContext;
import stroom.task.TaskManager;
import stroom.task.TaskTerminatedException;
import stroom.task.cluster.ClusterDispatchAsyncHelper;
import stroom.task.cluster.ClusterResultCollector;
import stroom.task.cluster.ClusterResultCollectorCache;
import stroom.task.cluster.CollectorId;
import stroom.task.cluster.CollectorIdFactory;
import stroom.task.cluster.TargetNodeSetFactory.TargetType;
import stroom.task.cluster.TerminateTaskClusterTask;
import stroom.task.shared.FindTaskCriteria;
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

    public static final String PROP_KEY_STORE_SIZE = "stroom.search.storeSize";

    private final ClusterResultCollectorCache clusterResultCollectorCache;
    private final CollectorId id;
    private final ConcurrentHashMap<Node, Set<String>> errors = new ConcurrentHashMap<>();
    private final Map<Node, AtomicLong> remainingNodes = new ConcurrentHashMap<>();
    private final AtomicInteger remainingNodeCount = new AtomicInteger();
    private final TaskManager taskManager;
    private final TaskContext taskContext;
    private final AsyncSearchTask task;
    private final ClusterDispatchAsyncHelper dispatchHelper;
    private final Node node;
    private final Set<String> highlights;
    private final ResultHandler resultHandler;
    private final List<Integer> defaultMaxResultsSizes;
    private final StoreSize storeSize;
    private final CompletionState completionState;

    ClusterSearchResultCollector(final TaskManager taskManager,
                                 final TaskContext taskContext,
                                 final AsyncSearchTask task,
                                 final ClusterDispatchAsyncHelper dispatchHelper,
                                 final Node node,
                                 final Set<String> highlights,
                                 final ClusterResultCollectorCache clusterResultCollectorCache,
                                 final ResultHandler resultHandler,
                                 final List<Integer> defaultMaxResultsSizes,
                                 final StoreSize storeSize,
                                 final CompletionState completionState) {
        this.taskManager = taskManager;
        this.taskContext = taskContext;
        this.task = task;
        this.dispatchHelper = dispatchHelper;
        this.node = node;
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
        taskManager.execAsync(task, new TaskCallback<VoidResult>() {
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
                    getErrorSet(node).add(t.getMessage());
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
    public void onSuccess(final Node node, final NodeResult result) {
        try {
            final Map<CoprocessorKey, Payload> payloadMap = result.getPayloadMap();
            final List<String> errors = result.getErrors();

            if (payloadMap != null) {
                resultHandler.handle(payloadMap);
            }

            if (errors != null) {
                getErrorSet(node).addAll(errors);
            }

            if (result.isComplete()) {
                nodeComplete(node);
            } else {
                final AtomicLong atomicLong = remainingNodes.get(node);
                if (atomicLong == null) {
                    LOGGER.error("Received an unexpected node result from " + node);
                } else {
                    atomicLong.set(System.currentTimeMillis());
                }
            }

        } catch (final RuntimeException e) {
            nodeComplete(node);
            getErrorSet(node).add(e.getMessage());

        } finally {
            if (remainingNodeCount.compareAndSet(0, 0)) {
                completionState.complete();
            }
        }
    }

    @Override
    public void onFailure(final Node node, final Throwable throwable) {
        try {
            nodeComplete(node);
            getErrorSet(node).add(throwable.getMessage());
        } finally {
            if (remainingNodeCount.compareAndSet(0, 0)) {
                completionState.complete();
            }
        }
    }

    private void nodeComplete(final Node node) {
        if (remainingNodes.remove(node) != null) {
            remainingNodeCount.decrementAndGet();
        }
    }

    @Override
    public void terminate() {
        complete();
    }

    public Set<String> getErrorSet(final Node node) {
        Set<String> errorSet = errors.get(node);
        if (errorSet == null) {
            errorSet = new HashSet<>();
            final Set<String> existing = errors.putIfAbsent(node, errorSet);
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
        for (final Entry<Node, Set<String>> entry : errors.entrySet()) {
            final Node node = entry.getKey();
            final Set<String> errors = entry.getValue();

            if (errors.size() > 0) {
                err.add("Node: " + node.getName());

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
    public List<Integer> getDefaultMaxResultsSizes() {
        return defaultMaxResultsSizes;
    }

    @Override
    public StoreSize getStoreSize() {
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

    void setExpectedNodes(final Set<Node> expectedNodes) {
        expectedNodes.forEach(node -> remainingNodes.put(node, new AtomicLong()));
        remainingNodeCount.set(expectedNodes.size());
    }
}
