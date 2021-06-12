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
 *
 */

package stroom.search.elastic.search;

import stroom.dictionary.api.WordListProvider;
import stroom.query.api.v2.ExpressionUtil;
import stroom.query.api.v2.Query;
import stroom.query.api.v2.SearchRequest;
import stroom.query.api.v2.TableSettings;
import stroom.query.common.v2.CoprocessorSettings;
import stroom.query.common.v2.Coprocessors;
import stroom.query.common.v2.CoprocessorsFactory;
import stroom.query.common.v2.Sizes;
import stroom.query.common.v2.Store;
import stroom.query.common.v2.StoreFactory;
import stroom.query.common.v2.TableCoprocessorSettings;
import stroom.search.elastic.ElasticIndexCache;
import stroom.search.elastic.shared.ElasticIndexDoc;
import stroom.security.api.SecurityContext;
import stroom.task.api.TaskContextFactory;
import stroom.ui.config.shared.UiConfig;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Provider;

@SuppressWarnings("unused")
public class ElasticSearchStoreFactory implements StoreFactory {
    public static final String ENTITY_TYPE = ElasticIndexDoc.DOCUMENT_TYPE;
    private static final Logger LOGGER = LoggerFactory.getLogger(ElasticSearchStoreFactory.class);

    private final ElasticIndexCache elasticIndexCache;
    private final WordListProvider wordListProvider;
    private final Executor executor;
    private final TaskContextFactory taskContextFactory;
    private final Provider<ElasticAsyncSearchTaskHandler> elasticAsyncSearchTaskHandlerProvider;
    private final ElasticSearchConfig searchConfig;
    private final UiConfig clientConfig;
    private final SecurityContext securityContext;
    private final CoprocessorsFactory coprocessorsFactory;

    @Inject
    public ElasticSearchStoreFactory(
            final ElasticIndexCache elasticIndexCache,
            final WordListProvider wordListProvider,
            final Executor executor,
            final TaskContextFactory taskContextFactory,
            final Provider<ElasticAsyncSearchTaskHandler> elasticAsyncSearchTaskHandlerProvider,
            final ElasticSearchConfig searchConfig,
            final UiConfig clientConfig,
            final SecurityContext securityContext,
            final CoprocessorsFactory coprocessorsFactory
    ) {
        this.elasticIndexCache = elasticIndexCache;
        this.wordListProvider = wordListProvider;
        this.executor = executor;
        this.taskContextFactory = taskContextFactory;
        this.elasticAsyncSearchTaskHandlerProvider = elasticAsyncSearchTaskHandlerProvider;
        this.searchConfig = searchConfig;
        this.clientConfig = clientConfig;
        this.securityContext = securityContext;
        this.coprocessorsFactory = coprocessorsFactory;
    }

    @Override
    public Store create(final SearchRequest searchRequest) {
        // Get the current time in millis since epoch.
        final long nowEpochMilli = System.currentTimeMillis();

        // Replace expression parameters.
        final SearchRequest modifiedSearchRequest = ExpressionUtil.replaceExpressionParameters(searchRequest);

        // Get the search.
        final Query query = modifiedSearchRequest.getQuery();

        // Load the index.
        final ElasticIndexDoc index = securityContext.useAsReadResult(() ->
                elasticIndexCache.get(query.getDataSource()));

        // Create a coprocessor settings list.
        final List<CoprocessorSettings> coprocessorSettingsList = coprocessorsFactory
                .createSettings(modifiedSearchRequest);

        // Create a handler for search results.
        final Coprocessors coprocessors = coprocessorsFactory.create(
                modifiedSearchRequest.getKey().getUuid(),
                coprocessorSettingsList,
                modifiedSearchRequest.getQuery().getParams());

        // Create an asynchronous search task.
        final String searchName = "Search '" + modifiedSearchRequest.getKey().toString() + "'";
        final ElasticAsyncSearchTask asyncSearchTask = new ElasticAsyncSearchTask(
                modifiedSearchRequest.getKey(),
                searchName,
                query,
                coprocessorSettingsList,
                modifiedSearchRequest.getDateTimeLocale(),
                nowEpochMilli);

        final Sizes maxResultSizes = calculateMaxResultSizes(coprocessorSettingsList);

        // Create the search result collector.
        final ElasticSearchResultCollector searchResultCollector = ElasticSearchResultCollector.create(
                executor,
                taskContextFactory,
                elasticAsyncSearchTaskHandlerProvider,
                asyncSearchTask,
                coprocessors,
                maxResultSizes);

        // Tell the task where results will be collected.
        asyncSearchTask.setResultCollector(searchResultCollector);

        // Start asynchronous search execution.
        searchResultCollector.start();

        return searchResultCollector;
    }

    private Sizes calculateMaxResultSizes(final List<CoprocessorSettings> coprocessorSettings) {
        final Sizes defaultMaxResultsSizes = getDefaultMaxResultsSizes();
        Sizes currentMaxResultSize = Sizes.create(0);

        // Find the largest maximum result set size of all table consumers
        for (final CoprocessorSettings settings : coprocessorSettings) {
            if (settings instanceof TableCoprocessorSettings) {
                final TableCoprocessorSettings tableCoprocessorSettings = (TableCoprocessorSettings) settings;
                final TableSettings tableSettings = tableCoprocessorSettings.getTableSettings();

                currentMaxResultSize = Sizes.max(Sizes.create(tableSettings.getMaxResults()), currentMaxResultSize);
            }
        }

        return Sizes.min(currentMaxResultSize, defaultMaxResultsSizes);
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
            } catch (Exception e) {
                LOGGER.warn(e.getMessage());
            }
        }
        return Sizes.create(Integer.MAX_VALUE);
    }
}
