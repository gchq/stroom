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

package stroom.search.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.node.shared.Node;
import stroom.query.common.v2.CompletionState;
import stroom.query.common.v2.CoprocessorSettingsMap.CoprocessorKey;
import stroom.query.common.v2.Data;
import stroom.query.common.v2.Payload;
import stroom.query.common.v2.ResultHandler;
import stroom.query.common.v2.Sizes;
import stroom.query.common.v2.Store;
import stroom.search.resultsender.NodeResult;
import stroom.task.cluster.ClusterResultCollector;
import stroom.task.cluster.ClusterResultCollectorCache;
import stroom.task.cluster.CollectorId;
import stroom.task.cluster.CollectorIdFactory;
import stroom.task.server.TaskCallback;
import stroom.task.server.TaskManager;
import stroom.task.server.TaskTerminatedException;
import stroom.util.shared.Task;
import stroom.util.shared.VoidResult;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class ClusterSearchResultCollector implements Store, ClusterResultCollector<NodeResult> {
    private static final Logger LOGGER = LoggerFactory.getLogger(ClusterSearchResultCollector.class);

    public static final String PROP_KEY_STORE_SIZE = "stroom.search.storeSize";

    private final ClusterResultCollectorCache clusterResultCollectorCache;
    private final CollectorId id;
    private final ConcurrentHashMap<Node, Set<String>> errors = new ConcurrentHashMap<>();
    private final CompletionState completionState = new CompletionState();
    private final TaskManager taskManager;
    private final Task<VoidResult> task;
    private final Node node;
    private final Set<String> highlights;
    private final ResultHandler resultHandler;
    private final Sizes defaultMaxResultsSizes;
    private final Sizes storeSize;

    private ClusterSearchResultCollector(final TaskManager taskManager,
                                         final Task<VoidResult> task,
                                         final Node node,
                                         final Set<String> highlights,
                                         final ClusterResultCollectorCache clusterResultCollectorCache,
                                         final ResultHandler resultHandler,
                                         final Sizes defaultMaxResultsSizes,
                                         final Sizes storeSize) {
        this.taskManager = taskManager;
        this.task = task;
        this.node = node;
        this.highlights = highlights;
        this.clusterResultCollectorCache = clusterResultCollectorCache;
        this.resultHandler = resultHandler;
        this.defaultMaxResultsSizes = defaultMaxResultsSizes;
        this.storeSize = storeSize;

        id = CollectorIdFactory.create();

        clusterResultCollectorCache.put(id, this);
    }

    public static ClusterSearchResultCollector create(final TaskManager taskManager,
                                                      final Task<VoidResult> task,
                                                      final Node node,
                                                      final Set<String> highlights,
                                                      final ClusterResultCollectorCache clusterResultCollectorCache,
                                                      final ResultHandler resultHandler,
                                                      final Sizes defaultMaxResultsSizes,
                                                      final Sizes storeSize) {
        return new ClusterSearchResultCollector(taskManager, task, node, highlights,
                clusterResultCollectorCache, resultHandler, defaultMaxResultsSizes, storeSize);
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
                    complete();
                    throw new RuntimeException(t.getMessage(), t);
                }

                complete();
            }
        });
    }

    @Override
    public void destroy() {
        clusterResultCollectorCache.remove(id);
        task.terminate();
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
    public synchronized void onSuccess(final Node node, final NodeResult result) {
        try {
            final Map<CoprocessorKey, Payload> payloadMap = result.getPayloadMap();
            final List<String> errors = result.getErrors();

            if (payloadMap != null) {
                resultHandler.handle(payloadMap, task);
            }

            if (errors != null) {
                getErrorSet(node).addAll(errors);
            }
        } catch (final RuntimeException e) {
            getErrorSet(node).add(e.getMessage());
        }
    }

    @Override
    public synchronized void onFailure(final Node node, final Throwable throwable) {
        getErrorSet(node).add(throwable.getMessage());
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
        if (errors.size() == 0) {
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
}
