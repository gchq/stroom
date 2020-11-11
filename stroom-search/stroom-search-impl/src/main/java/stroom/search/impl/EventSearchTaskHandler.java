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

import stroom.node.api.NodeInfo;
import stroom.query.api.v2.ExpressionUtil;
import stroom.query.api.v2.Query;
import stroom.query.common.v2.CoprocessorKey;
import stroom.query.common.v2.Coprocessors;
import stroom.query.common.v2.CoprocessorsFactory;
import stroom.query.common.v2.EventCoprocessor;
import stroom.query.common.v2.EventCoprocessorSettings;
import stroom.query.common.v2.EventRefs;
import stroom.query.common.v2.EventRefsPayload;
import stroom.security.api.SecurityContext;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import javax.inject.Inject;
import java.util.Collections;


public class EventSearchTaskHandler {
    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(EventSearchTaskHandler.class);

    private final NodeInfo nodeInfo;
    private final ClusterSearchResultCollectorFactory clusterSearchResultCollectorFactory;
    private final SecurityContext securityContext;
    private final CoprocessorsFactory coprocessorsFactory;

    @Inject
    EventSearchTaskHandler(final NodeInfo nodeInfo,
                           final ClusterSearchResultCollectorFactory clusterSearchResultCollectorFactory,
                           final SecurityContext securityContext,
                           final CoprocessorsFactory coprocessorsFactory) {
        this.nodeInfo = nodeInfo;
        this.clusterSearchResultCollectorFactory = clusterSearchResultCollectorFactory;
        this.securityContext = securityContext;
        this.coprocessorsFactory = coprocessorsFactory;
    }

    public EventRefs exec(final EventSearchTask task) {
        return securityContext.secureResult(() -> {
            EventRefs eventRefs;

            // Get the current time in millis since epoch.
            final long nowEpochMilli = System.currentTimeMillis();

            // Get the search.
            final Query query = task.getQuery();

            // Replace expression parameters.
            ExpressionUtil.replaceExpressionParameters(query);

            final CoprocessorKey coprocessorKey = new CoprocessorKey(0, new String[]{"eventCoprocessor"});
            final EventCoprocessorSettings settings = new EventCoprocessorSettings(
                    coprocessorKey,
                    task.getMinEvent(),
                    task.getMaxEvent(),
                    task.getMaxStreams(),
                    task.getMaxEvents(),
                    task.getMaxEventsPerStream());

            // Create an asynchronous search task.
            final String searchName = "Event Search";
            final AsyncSearchTask asyncSearchTask = new AsyncSearchTask(
                    task.getKey(),
                    searchName,
                    query,
                    Collections.singletonList(settings),
                    null,
                    nowEpochMilli);

            final Coprocessors coprocessors = coprocessorsFactory.create(Collections.singletonList(settings), query.getParams());
            final EventCoprocessor eventCoprocessor = (EventCoprocessor) coprocessors.get(coprocessorKey);

            // Create a collector to store search results.
            final ClusterSearchResultCollector searchResultCollector = clusterSearchResultCollectorFactory.create(
                    asyncSearchTask,
                    nodeInfo.getThisNodeName(),
                    null,
                    coprocessors);

            // Tell the task where results will be collected.
            asyncSearchTask.setResultCollector(searchResultCollector);

            try {
                // Start asynchronous search execution.
                searchResultCollector.start();

                LOGGER.debug(() -> "Started searchResultCollector " + searchResultCollector);

                // Wait for completion or termination
                searchResultCollector.awaitCompletion();

                eventRefs = ((EventRefsPayload) eventCoprocessor.createPayload()).getEventRefs();
                if (eventRefs != null) {
                    eventRefs.trim();
                }
            } catch (final InterruptedException e) {
                // Continue to interrupt this thread.
                Thread.currentThread().interrupt();

                //Don't want to reset interrupt status as this thread will go back into
                //the executor's pool. Throwing an exception will terminate the task
                throw new RuntimeException(
                        LogUtil.message("Thread {} interrupted executing task {}",
                                Thread.currentThread().getName(), task));
            } finally {
                searchResultCollector.destroy();
            }

            return eventRefs;
        });
    }
}
