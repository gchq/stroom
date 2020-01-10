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
import org.springframework.context.annotation.Scope;
import stroom.node.server.StroomPropertyService;
import stroom.node.shared.ClientProperties;
import stroom.query.api.v2.Query;
import stroom.query.common.v2.CoprocessorSettings;
import stroom.query.common.v2.CoprocessorSettingsMap.CoprocessorKey;
import stroom.query.common.v2.Sizes;
import stroom.search.coprocessor.EventCoprocessorSettings;
import stroom.search.server.EventRefs;
import stroom.task.server.AbstractTaskHandler;
import stroom.task.server.TaskHandlerBean;
import stroom.task.server.TaskManager;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.spring.StroomScope;

import javax.inject.Inject;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@TaskHandlerBean(task = SolrEventSearchTask.class)
@Scope(StroomScope.PROTOTYPE)
class SolrEventSearchTaskHandler extends AbstractTaskHandler<SolrEventSearchTask, EventRefs> {
    private static final Logger LOGGER = LoggerFactory.getLogger(SolrEventSearchTaskHandler.class);
    private static final LambdaLogger LAMBDA_LOGGER = LambdaLoggerFactory.getLogger(SolrEventSearchTaskHandler.class);

    private final TaskManager taskManager;
    private final StroomPropertyService stroomPropertyService;

    @Inject
    SolrEventSearchTaskHandler(final TaskManager taskManager,
                               final StroomPropertyService stroomPropertyService) {
        this.taskManager = taskManager;
        this.stroomPropertyService = stroomPropertyService;
    }

    @Override
    public EventRefs exec(final SolrEventSearchTask task) {
        EventRefs eventRefs = null;

        // Get the current time in millis since epoch.
        final long nowEpochMilli = System.currentTimeMillis();

        // Get the search.
        final Query query = task.getQuery();

        final EventCoprocessorSettings settings = new EventCoprocessorSettings(task.getMinEvent(), task.getMaxEvent(),
                task.getMaxStreams(), task.getMaxEvents(), task.getMaxEventsPerStream());
        final Map<CoprocessorKey, CoprocessorSettings> coprocessorMap = new HashMap<>();
        coprocessorMap.put(new CoprocessorKey(0, new String[]{"eventCoprocessor"}), settings);

        // Create an asynchronous search task.
        final String searchName = "Event Search";
        final SolrAsyncSearchTask asyncSearchTask = new SolrAsyncSearchTask(task, task.getUserIdentity(), searchName,
                query, task.getResultSendFrequency(), coprocessorMap, null, nowEpochMilli);

        // Create a collector to store search results.
        final Sizes storeSize = getStoreSizes();
        final Sizes defaultMaxResultsSizes = getDefaultMaxResultsSizes();
        final EventSearchResultHandler resultHandler = new EventSearchResultHandler();
        final SolrSearchResultCollector searchResultCollector = SolrSearchResultCollector.create(
                taskManager,
                asyncSearchTask,
                null,
                resultHandler,
                defaultMaxResultsSizes,
                storeSize);

        // Tell the task where results will be collected.
        asyncSearchTask.setResultCollector(searchResultCollector);

        try {
            final CountDownLatch completionLatch = new CountDownLatch(1);

            //when the search completes reduce our latch to zero to release the block below
            resultHandler.registerCompletionListener(completionLatch::countDown);

            // Start asynchronous search execution.
            searchResultCollector.start();

            LOGGER.debug("Started searchResultCollector {}", searchResultCollector);

            // Wait for completion or termination
            while (!task.isTerminated() && !resultHandler.isComplete()) {
                //Drop out of the waiting state every 30s to check we haven't been terminated.
                //This is a bit of extra protection as the resultHandler should notify our
                //completion listener if it is terminated.
                //If the search completes while waiting we will drop out of the wait immediately
                boolean awaitResult = LAMBDA_LOGGER.logDurationIfTraceEnabled(
                        () -> {
                            try {
                                return completionLatch.await(30, TimeUnit.SECONDS);
                            } catch (InterruptedException e) {
                                //Don't want to reset interrupt status as this thread will go back into
                                //the executor's pool. Throwing an exception will terminate the task
                                throw new RuntimeException(
                                        LambdaLogger.buildMessage("Thread {} interrupted executing task {}",
                                                Thread.currentThread().getName(), task));
                            }
                        },
                        "waiting for completionLatch");

                LOGGER.trace("await finished with result {}", awaitResult);
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

    private Sizes getDefaultMaxResultsSizes() {
        final String value = stroomPropertyService.getProperty(ClientProperties.DEFAULT_MAX_RESULTS);
        return extractValues(value);
    }

    private Sizes getStoreSizes() {
        final String value = stroomPropertyService.getProperty(SolrSearchResultCollector.PROP_KEY_STORE_SIZE);
        return extractValues(value);
    }

    private Sizes extractValues(String value) {
        if (value != null) {
            try {
                return Sizes.create(Arrays.stream(value.split(","))
                        .map(String::trim)
                        .map(Integer::valueOf)
                        .collect(Collectors.toList()));
            } catch (Exception e) {
                LOGGER.warn(e.getMessage());
            }
        }
        return Sizes.create(Integer.MAX_VALUE);
    }
}
