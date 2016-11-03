/*
 * Copyright 2016 Crown Copyright
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

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import stroom.query.Payload;
import stroom.query.ResultHandler;
import stroom.query.ResultStore;
import stroom.query.SearchResultCollector;
import stroom.node.shared.Node;
import stroom.task.cluster.ClusterResultCollector;
import stroom.task.cluster.ClusterResultCollectorCache;
import stroom.task.cluster.CollectorId;
import stroom.task.cluster.CollectorIdFactory;
import stroom.task.server.TaskCallback;
import stroom.task.server.TaskManager;
import stroom.task.server.TaskTerminatedException;
import stroom.util.logging.StroomLogger;
import stroom.util.shared.Task;
import stroom.util.shared.VoidResult;

public class ClusterSearchResultCollector implements SearchResultCollector, ClusterResultCollector<NodeResult> {
    private static final StroomLogger LOGGER = StroomLogger.getLogger(ClusterSearchResultCollector.class);

    private final ClusterResultCollectorCache clusterResultCollectorCache;
    private final CollectorId id;
    private final ConcurrentHashMap<Node, Set<String>> errors = new ConcurrentHashMap<>();
    private final Set<Node> completedNodes = Collections.synchronizedSet(new HashSet<>());
    private final TaskManager taskManager;
    private final Task<VoidResult> task;
    private final Node node;
    private final Set<String> highlights;
    private final ResultHandler resultHandler;

    private volatile boolean terminated;

    private ClusterSearchResultCollector(final TaskManager taskManager, final Task<VoidResult> task, final Node node,
            final Set<String> highlights, final ClusterResultCollectorCache clusterResultCollectorCache,
            final ResultHandler resultHandler) {
        this.taskManager = taskManager;
        this.task = task;
        this.node = node;
        this.highlights = highlights;
        this.clusterResultCollectorCache = clusterResultCollectorCache;
        this.resultHandler = resultHandler;

        id = CollectorIdFactory.create();

        clusterResultCollectorCache.put(id, this);
    }

    public static ClusterSearchResultCollector create(final TaskManager taskManager, final Task<VoidResult> task,
            final Node node, final Set<String> highlights,
            final ClusterResultCollectorCache clusterResultCollectorCache, final ResultHandler resultHandler) {
        return new ClusterSearchResultCollector(taskManager, task, node, highlights, clusterResultCollectorCache,
                resultHandler);
    }

    @Override
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
                    resultHandler.setComplete(true);
                    throw new RuntimeException(t.getMessage(), t);
                }

                resultHandler.setComplete(true);
            }
        });
    }

    @Override
    public void destroy() {
        clusterResultCollectorCache.remove(id);
        task.terminate();
    }

    @Override
    public boolean isComplete() {
        return terminated || resultHandler.isComplete();
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
        final Map<Integer, Payload> payloadMap = result.getPayloadMap();
        final List<String> errors = result.getErrors();

        if (payloadMap != null) {
            resultHandler.handle(payloadMap, task);
        }
        if (errors != null) {
            getErrorSet(node).addAll(errors);
        }
        if (result.isComplete()) {
            completedNodes.add(node);
        }
    }

    @Override
    public void onFailure(final Node node, final Throwable throwable) {
        completedNodes.add(node);
        getErrorSet(node).add(throwable.getMessage());
    }

    @Override
    public void terminate() {
        terminated = true;
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
    public String getErrors() {
        final StringBuilder sb = new StringBuilder();
        for (final Entry<Node, Set<String>> entry : errors.entrySet()) {
            final Node node = entry.getKey();
            final Set<String> errors = entry.getValue();

            if (errors.size() > 0) {
                sb.append("Node: ");
                sb.append(node.getName());
                sb.append("\n");

                for (final String error : errors) {
                    sb.append("\t");
                    sb.append(error);
                    sb.append("\n");
                }
            }
        }

        // Remove last new line.
        if (sb.length() > 0) {
            sb.setLength(sb.length() - 1);
        }

        return sb.toString();
    }

    @Override
    public Set<String> getHighlights() {
        return highlights;
    }

    @Override
    public ResultStore getResultStore(final String componentId) {
        return resultHandler.getResultStore(componentId);
    }

    public Set<Node> getCompletedNodes() {
        return completedNodes;
    }

    public ResultHandler getResultHandler() {
        return resultHandler;
    }
}
