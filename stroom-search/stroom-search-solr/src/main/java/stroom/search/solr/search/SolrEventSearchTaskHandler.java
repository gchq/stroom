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

import stroom.query.api.v2.ExpressionUtil;
import stroom.query.api.v2.Query;
import stroom.query.common.v2.Coprocessors;
import stroom.query.common.v2.CoprocessorsFactory;
import stroom.query.common.v2.EventCoprocessor;
import stroom.query.common.v2.EventCoprocessorSettings;
import stroom.query.common.v2.EventRefs;
import stroom.security.api.SecurityContext;
import stroom.task.api.TaskContextFactory;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import java.util.Collections;
import java.util.concurrent.Executor;
import javax.inject.Inject;
import javax.inject.Provider;

public class SolrEventSearchTaskHandler {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(SolrEventSearchTaskHandler.class);

    private final Executor executor;
    private final TaskContextFactory taskContextFactory;
    private final Provider<SolrAsyncSearchTaskHandler> solrAsyncSearchTaskHandlerProvider;
    private final SecurityContext securityContext;
    private final CoprocessorsFactory coprocessorsFactory;

    @Inject
    SolrEventSearchTaskHandler(final Executor executor,
                               final TaskContextFactory taskContextFactory,
                               final Provider<SolrAsyncSearchTaskHandler> solrAsyncSearchTaskHandlerProvider,
                               final SecurityContext securityContext,
                               final CoprocessorsFactory coprocessorsFactory) {
        this.executor = executor;
        this.taskContextFactory = taskContextFactory;
        this.solrAsyncSearchTaskHandlerProvider = solrAsyncSearchTaskHandlerProvider;
        this.securityContext = securityContext;
        this.coprocessorsFactory = coprocessorsFactory;
    }

    public EventRefs exec(final SolrEventSearchTask task) {
        return securityContext.secureResult(() -> {
            EventRefs eventRefs;

            // Get the current time in millis since epoch.
            final long nowEpochMilli = System.currentTimeMillis();

            // Get the search.
            final Query query = task.getQuery();

            // Replace expression parameters.
            final Query modifiedQuery = ExpressionUtil.replaceExpressionParameters(query);

            final int coprocessorId = 0;
            final EventCoprocessorSettings settings = new EventCoprocessorSettings(
                    coprocessorId,
                    task.getMinEvent(),
                    task.getMaxEvent(),
                    task.getMaxStreams(),
                    task.getMaxEvents(),
                    task.getMaxEventsPerStream());

            // Create an asynchronous search task.
            final String searchName = "Event Search";
            final SolrAsyncSearchTask asyncSearchTask = new SolrAsyncSearchTask(
                    task.getKey(),
                    searchName,
                    modifiedQuery,
                    Collections.singletonList(settings),
                    null,
                    nowEpochMilli);

            final Coprocessors coprocessors = coprocessorsFactory.create(
                    task.getKey(),
                    Collections.singletonList(settings),
                    modifiedQuery.getParams(),
                    false);
            final EventCoprocessor eventCoprocessor = (EventCoprocessor) coprocessors.get(coprocessorId);

            // Create a collector to store search results.
            final SolrSearchResultCollector searchResultCollector = SolrSearchResultCollector.create(
                    executor,
                    taskContextFactory,
                    solrAsyncSearchTaskHandlerProvider,
                    asyncSearchTask,
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

                eventRefs = eventCoprocessor.getEventRefs();
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
