/*
 * Copyright 2016-2025 Crown Copyright
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

import stroom.query.api.DateTimeSettings;
import stroom.query.api.ExpressionUtil;
import stroom.query.api.Query;
import stroom.query.common.v2.CoprocessorsFactory;
import stroom.query.common.v2.CoprocessorsImpl;
import stroom.query.common.v2.DataStoreSettings;
import stroom.query.common.v2.EventCoprocessor;
import stroom.query.common.v2.EventCoprocessorSettings;
import stroom.query.common.v2.EventRefs;
import stroom.query.common.v2.ResultStore;
import stroom.query.common.v2.ResultStoreFactory;
import stroom.security.api.SecurityContext;
import stroom.task.api.TaskContextFactory;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.util.Collections;
import java.util.concurrent.Executor;

public class SolrEventSearchTaskHandler {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(SolrEventSearchTaskHandler.class);

    private final Executor executor;
    private final TaskContextFactory taskContextFactory;
    private final Provider<SolrAsyncSearchTaskHandler> solrAsyncSearchTaskHandlerProvider;
    private final SecurityContext securityContext;
    private final CoprocessorsFactory coprocessorsFactory;
    private final ResultStoreFactory resultStoreFactory;
    private final SolrSearchExecutor solrSearchExecutor;

    @Inject
    SolrEventSearchTaskHandler(final Executor executor,
                               final TaskContextFactory taskContextFactory,
                               final Provider<SolrAsyncSearchTaskHandler> solrAsyncSearchTaskHandlerProvider,
                               final SecurityContext securityContext,
                               final CoprocessorsFactory coprocessorsFactory,
                               final ResultStoreFactory resultStoreFactory,
                               final SolrSearchExecutor solrSearchExecutor) {
        this.executor = executor;
        this.taskContextFactory = taskContextFactory;
        this.solrAsyncSearchTaskHandlerProvider = solrAsyncSearchTaskHandlerProvider;
        this.securityContext = securityContext;
        this.coprocessorsFactory = coprocessorsFactory;
        this.resultStoreFactory = resultStoreFactory;
        this.solrSearchExecutor = solrSearchExecutor;
    }

    public EventRefs exec(final SolrEventSearchTask task) {
        return securityContext.secureResult(() -> {
            final EventRefs eventRefs;

            // Get the current time in millis since epoch.
            final DateTimeSettings dateTimeSettings = DateTimeSettings.builder().build();

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
                    dateTimeSettings);

            final CoprocessorsImpl coprocessors = coprocessorsFactory.create(
                    task.getSearchRequestSource(),
                    DateTimeSettings.builder().build(),
                    task.getKey(),
                    Collections.singletonList(settings),
                    modifiedQuery.getParams(),
                    DataStoreSettings.createBasicSearchResultStoreSettings());
            final EventCoprocessor eventCoprocessor = (EventCoprocessor) coprocessors.get(coprocessorId);

            // Create the search result store.
            final ResultStore resultStore = resultStoreFactory.create(
                    null,
                    coprocessors);

            try {
                // Start asynchronous search execution.
                solrSearchExecutor.start(asyncSearchTask, resultStore);

                LOGGER.debug(() -> "Started searchResultCollector " + resultStore);

                // Wait for completion or termination
                resultStore.awaitCompletion();

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
                resultStore.destroy();
            }

            return eventRefs;
        });
    }
}
