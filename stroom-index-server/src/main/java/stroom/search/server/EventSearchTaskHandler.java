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
import org.springframework.context.annotation.Scope;
import stroom.node.server.NodeCache;
import stroom.node.server.StroomPropertyService;
import stroom.node.shared.ClientProperties;
import stroom.node.shared.Node;
import stroom.query.api.v2.Query;
import stroom.query.common.v2.CoprocessorSettings;
import stroom.query.common.v2.CoprocessorSettingsMap.CoprocessorKey;
import stroom.query.common.v2.StoreSize;
import stroom.task.cluster.ClusterResultCollectorCache;
import stroom.task.server.AbstractTaskHandler;
import stroom.task.server.TaskHandlerBean;
import stroom.task.server.TaskManager;
import stroom.util.logging.LambdaLogger;
import stroom.util.spring.StroomScope;

import javax.inject.Inject;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

@TaskHandlerBean(task = EventSearchTask.class)
@Scope(StroomScope.PROTOTYPE)
class EventSearchTaskHandler extends AbstractTaskHandler<EventSearchTask, EventRefs> {
    private static final Logger LOGGER = LoggerFactory.getLogger(EventSearchTaskHandler.class);

    private final NodeCache nodeCache;
    private final TaskManager taskManager;
    private final ClusterResultCollectorCache clusterResultCollectorCache;
    private final StroomPropertyService stroomPropertyService;

    @Inject
    EventSearchTaskHandler(final ClusterResultCollectorCache clusterResultCollectorCache,
                           final TaskManager taskManager,
                           final NodeCache nodeCache,
                           final StroomPropertyService stroomPropertyService) {
        this.clusterResultCollectorCache = clusterResultCollectorCache;
        this.taskManager = taskManager;
        this.nodeCache = nodeCache;
        this.stroomPropertyService = stroomPropertyService;
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
        final Map<CoprocessorKey, CoprocessorSettings> coprocessorMap = new HashMap<>();
        coprocessorMap.put(new CoprocessorKey(0, new String[]{"eventCoprocessor"}), settings);

        // Create an asynchronous search task.
        final String searchName = "Event Search";
        final AsyncSearchTask asyncSearchTask = new AsyncSearchTask(task, task.getUserToken(), searchName,
                query, node, task.getResultSendFrequency(), coprocessorMap, null, nowEpochMilli);

        // Create a collector to store search results.
        final StoreSize storeSize = new StoreSize(getStoreSizes());
        final List<Integer> defaultMaxResultsSizes = getDefaultMaxResultsSizes();
        final EventSearchResultHandler resultHandler = new EventSearchResultHandler();
        final ClusterSearchResultCollector searchResultCollector = ClusterSearchResultCollector.create(
                taskManager,
                asyncSearchTask,
                nodeCache.getDefaultNode(),
                null,
                clusterResultCollectorCache,
                resultHandler,
                defaultMaxResultsSizes,
                storeSize);

        // Tell the task where results will be collected.
        asyncSearchTask.setResultCollector(searchResultCollector);

        try {
            final Semaphore completionSemaphore = new Semaphore(0);

            //when the search completes release a permit so we can get past the semaphore
            resultHandler.registerCompletionListener(completionSemaphore::release);

            // Start asynchronous search execution.
            searchResultCollector.start();

            // Wait for completion or termination
            while (!task.isTerminated() && !resultHandler.isComplete()) {
                try {
                    //drop out of the waiting state every 2s to check we haven't been terminated.
                    //If the search completes while waiting we will drop out of the wait immediately
                    completionSemaphore.tryAcquire(2, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(
                            LambdaLogger.buildMessage("Thread {} interrupted executing task {}",
                                    Thread.currentThread().getName(), task));
                }
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

    private List<Integer> getDefaultMaxResultsSizes() {
        final String value = stroomPropertyService.getProperty(ClientProperties.DEFAULT_MAX_RESULTS);
        return extractValues(value);
    }

    private List<Integer> getStoreSizes() {
        final String value = stroomPropertyService.getProperty(ClusterSearchResultCollector.PROP_KEY_STORE_SIZE);
        return extractValues(value);
    }

    private List<Integer> extractValues(String value) {
        if (value != null) {
            try {
                return Arrays.stream(value.split(","))
                        .map(String::trim)
                        .map(Integer::valueOf)
                        .collect(Collectors.toList());
            } catch (Exception e) {
                LOGGER.warn(e.getMessage());
            }
        }
        return Collections.emptyList();
    }
}
