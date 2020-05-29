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

import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.ExpressionParamUtil;
import stroom.query.api.v2.ExpressionUtil;
import stroom.query.api.v2.Query;
import stroom.query.common.v2.CompletionState;
import stroom.query.common.v2.CoprocessorSettings;
import stroom.query.common.v2.CoprocessorSettingsMap.CoprocessorKey;
import stroom.query.common.v2.Sizes;
import stroom.search.api.EventRefs;
import stroom.search.coprocessor.EventCoprocessorSettings;
import stroom.security.api.SecurityContext;
import stroom.task.api.TaskContextFactory;
import stroom.ui.config.shared.UiConfig;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import javax.inject.Inject;
import javax.inject.Provider;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

public class SolrEventSearchTaskHandler {
    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(SolrEventSearchTaskHandler.class);

    private final Executor executor;
    private final TaskContextFactory taskContextFactory;
    private final Provider<SolrAsyncSearchTaskHandler> solrAsyncSearchTaskHandlerProvider;
    private final SolrSearchConfig searchConfig;
    private final UiConfig clientConfig;
    private final SecurityContext securityContext;

    @Inject
    SolrEventSearchTaskHandler(final Executor executor,
                               final TaskContextFactory taskContextFactory,
                               final Provider<SolrAsyncSearchTaskHandler> solrAsyncSearchTaskHandlerProvider,
                               final SolrSearchConfig searchConfig,
                               final UiConfig clientConfig,
                               final SecurityContext securityContext) {
        this.executor = executor;
        this.taskContextFactory = taskContextFactory;
        this.solrAsyncSearchTaskHandlerProvider = solrAsyncSearchTaskHandlerProvider;
        this.searchConfig = searchConfig;
        this.clientConfig = clientConfig;
        this.securityContext = securityContext;
    }

    public EventRefs exec(final SolrEventSearchTask task) {
        return securityContext.secureResult(() -> {
            EventRefs eventRefs;

            // Get the current time in millis since epoch.
            final long nowEpochMilli = System.currentTimeMillis();

            // Get the search.
            final Query query = task.getQuery();

            // Replace expression parameters.
            ExpressionOperator expression = query.getExpression();
            final Map<String, String> paramMap = ExpressionParamUtil.createParamMap(query.getParams());
            expression = ExpressionUtil.replaceExpressionParameters(expression, paramMap);
            query.setExpression(expression);

            final EventCoprocessorSettings settings = new EventCoprocessorSettings(task.getMinEvent(), task.getMaxEvent(),
                    task.getMaxStreams(), task.getMaxEvents(), task.getMaxEventsPerStream());
            final Map<CoprocessorKey, CoprocessorSettings> coprocessorMap = new HashMap<>();
            coprocessorMap.put(new CoprocessorKey(0, new String[]{"eventCoprocessor"}), settings);

            // Create an asynchronous search task.
            final String searchName = "Event Search";
            final SolrAsyncSearchTask asyncSearchTask = new SolrAsyncSearchTask(
                    searchName,
                    query,
                    task.getResultSendFrequency(),
                    coprocessorMap,
                    null,
                    nowEpochMilli);

            // Create a collector to store search results.
            final Sizes storeSize = getStoreSizes();
            final Sizes defaultMaxResultsSizes = getDefaultMaxResultsSizes();
            final CompletionState completionState = new CompletionState();
            final EventSearchResultHandler resultHandler = new EventSearchResultHandler();
            final SolrSearchResultCollector searchResultCollector = SolrSearchResultCollector.create(
                    executor,
                    taskContextFactory,
                    solrAsyncSearchTaskHandlerProvider,
                    asyncSearchTask,
                    null,
                    resultHandler,
                    defaultMaxResultsSizes,
                    storeSize,
                    completionState);

            // Tell the task where results will be collected.
            asyncSearchTask.setResultCollector(searchResultCollector);

            try {
                // Start asynchronous search execution.
                searchResultCollector.start();

                LOGGER.debug(() -> "Started searchResultCollector " + searchResultCollector);

                // Wait for completion or termination
                searchResultCollector.awaitCompletion();

                eventRefs = resultHandler.getStreamReferences();
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

    private Sizes getDefaultMaxResultsSizes() {
        final String value = clientConfig.getDefaultMaxResults();
        return extractValues(value);
    }

    private Sizes getStoreSizes() {
        final String value = searchConfig.getStoreSize();
        return extractValues(value);
    }

    private Sizes extractValues(String value) {
        if (value != null) {
            try {
                return Sizes.create(Arrays.stream(value.split(","))
                        .map(String::trim)
                        .map(Integer::valueOf)
                        .collect(Collectors.toList()));
            } catch (final RuntimeException e) {
                LOGGER.warn(e::getMessage);
            }
        }
        return Sizes.create(Integer.MAX_VALUE);
    }
}
