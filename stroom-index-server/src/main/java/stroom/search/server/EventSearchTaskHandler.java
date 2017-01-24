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

import org.springframework.context.annotation.Scope;
import stroom.node.server.NodeCache;
import stroom.node.shared.Node;
import stroom.query.CoprocessorSettings;
import stroom.query.api.Query;
import stroom.task.cluster.ClusterResultCollectorCache;
import stroom.task.server.AbstractTaskHandler;
import stroom.task.server.TaskHandlerBean;
import stroom.task.server.TaskManager;
import stroom.util.spring.StroomScope;
import stroom.util.thread.ThreadUtil;

import javax.inject.Inject;
import java.util.HashMap;
import java.util.Map;

@TaskHandlerBean(task = EventSearchTask.class)
@Scope(StroomScope.PROTOTYPE)
class EventSearchTaskHandler extends AbstractTaskHandler<EventSearchTask, EventRefs> {
    private final NodeCache nodeCache;
    private final TaskManager taskManager;
    private final ClusterResultCollectorCache clusterResultCollectorCache;

    @Inject
    EventSearchTaskHandler(final ClusterResultCollectorCache clusterResultCollectorCache, final TaskManager taskManager, final NodeCache nodeCache) {
        this.clusterResultCollectorCache = clusterResultCollectorCache;
        this.taskManager = taskManager;
        this.nodeCache = nodeCache;
    }

    @Override
    public EventRefs exec(final EventSearchTask task) {
        EventRefs eventRefs = null;

        // Get the current time in millis since epoch.
        final long nowEpochMilli = System.currentTimeMillis();

        // Get the search.
        final Query query = task.getQuery();

        // Get the current node.
        final Node node = nodeCache.getDefaultNode();

        final EventCoprocessorSettings settings = new EventCoprocessorSettings(task.getMinEvent(), task.getMaxEvent(),
                task.getMaxStreams(), task.getMaxEvents(), task.getMaxEventsPerStream());
        final Map<Integer, CoprocessorSettings> coprocessorMap = new HashMap<>();
        coprocessorMap.put(0, settings);

        // Create an asynchronous search task.
        final String searchName = "Search " + task.getSessionId();
        final AsyncSearchTask asyncSearchTask = new AsyncSearchTask(task.getSessionId(), task.getUserId(), searchName,
                query, node, task.getResultSendFrequency(), coprocessorMap, nowEpochMilli);

        // Create a collector to store search results.
        final EventSearchResultHandler resultHandler = new EventSearchResultHandler();
        final ClusterSearchResultCollector searchResultCollector = ClusterSearchResultCollector.create(taskManager,
                asyncSearchTask, nodeCache.getDefaultNode(), null, clusterResultCollectorCache, resultHandler);

        // Tell the task where results will be collected.
        asyncSearchTask.setResultCollector(searchResultCollector);

        try {
            // Start asynchronous search execution.
            searchResultCollector.start();

            // Wait for completion.
            while (!task.isTerminated() && !resultHandler.isComplete()) {
                ThreadUtil.sleep(task.getResultSendFrequency());
            }

            eventRefs = resultHandler.getStreamReferences();
            if (eventRefs != null) {
                eventRefs.trim();
            }
        } finally {
            searchResultCollector.destroy();
        }

        return eventRefs;
    }
}
