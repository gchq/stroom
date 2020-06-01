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

package stroom.search.solr.search;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.dictionary.api.WordListProvider;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.ExpressionParamUtil;
import stroom.query.api.v2.ExpressionUtil;
import stroom.query.api.v2.Query;
import stroom.query.api.v2.SearchRequest;
import stroom.query.common.v2.*;
import stroom.search.solr.CachedSolrIndex;
import stroom.search.solr.SolrIndexCache;
import stroom.search.solr.search.SearchExpressionQueryBuilder.SearchExpressionQuery;
import stroom.search.solr.shared.SolrIndexDoc;
import stroom.search.solr.shared.SolrIndexField;
import stroom.security.api.SecurityContext;
import stroom.task.api.TaskContextFactory;
import stroom.ui.config.shared.UiConfig;

import javax.inject.Inject;
import javax.inject.Provider;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.function.Function;
import java.util.stream.Collectors;

// used by DI
@SuppressWarnings("unused")
class SolrSearchStoreFactory implements StoreFactory {
    public static final String ENTITY_TYPE = SolrIndexDoc.DOCUMENT_TYPE;
    private static final Logger LOGGER = LoggerFactory.getLogger(SolrSearchStoreFactory.class);
    private static final int SEND_INTERACTIVE_SEARCH_RESULT_FREQUENCY = 500;
    private static final int DEFAULT_MAX_BOOLEAN_CLAUSE_COUNT = 1024;

    private final SolrIndexCache solrIndexCache;
    private final WordListProvider wordListProvider;
    private final Executor executor;
    private final TaskContextFactory taskContextFactory;
    private final Provider<SolrAsyncSearchTaskHandler> solrAsyncSearchTaskHandlerProvider;
    private final SolrSearchConfig searchConfig;
    private final UiConfig clientConfig;
    private final SecurityContext securityContext;

    @Inject
    public SolrSearchStoreFactory(final SolrIndexCache solrIndexCache,
                                  final WordListProvider wordListProvider,
                                  final Executor executor,
                                  final TaskContextFactory taskContextFactory,
                                  final Provider<SolrAsyncSearchTaskHandler> solrAsyncSearchTaskHandlerProvider,
                                  final SolrSearchConfig searchConfig,
                                  final UiConfig clientConfig,
                                  final SecurityContext securityContext) {
        this.solrIndexCache = solrIndexCache;
        this.wordListProvider = wordListProvider;
        this.executor = executor;
        this.taskContextFactory = taskContextFactory;
        this.solrAsyncSearchTaskHandlerProvider = solrAsyncSearchTaskHandlerProvider;
        this.searchConfig = searchConfig;
        this.clientConfig = clientConfig;
        this.securityContext = securityContext;
    }

    @Override
    public Store create(final SearchRequest searchRequest) {
        // Get the current time in millis since epoch.
        final long nowEpochMilli = System.currentTimeMillis();

        // Get the search.
        final Query query = searchRequest.getQuery();

        // Replace expression parameters.
        ExpressionOperator expression = query.getExpression();
        final Map<String, String> paramMap = ExpressionParamUtil.createParamMap(query.getParams());
        expression = ExpressionUtil.replaceExpressionParameters(expression, paramMap);
        query.setExpression(expression);

        // Load the index.
        final CachedSolrIndex index = securityContext.useAsReadResult(() -> solrIndexCache.get(query.getDataSource()));

        // Extract highlights.
        final Set<String> highlights = getHighlights(index, query.getExpression(), searchRequest.getDateTimeLocale(), nowEpochMilli);

        // Create a coprocessor settings map.
        final CoprocessorSettingsMap coprocessorSettingsMap = CoprocessorSettingsMap.create(searchRequest);

        // Create an asynchronous search task.
        final String searchName = "Search '" + searchRequest.getKey().toString() + "'";
        final SolrAsyncSearchTask asyncSearchTask = new SolrAsyncSearchTask(
                searchName,
                query,
                SEND_INTERACTIVE_SEARCH_RESULT_FREQUENCY,
                coprocessorSettingsMap.getMap(),
                searchRequest.getDateTimeLocale(),
                nowEpochMilli);

        // Create a handler for search results.
        final Sizes storeSize = getStoreSizes();
        final Sizes defaultMaxResultsSizes = getDefaultMaxResultsSizes();
        final CompletionState completionState = new CompletionState();
        final SearchResultHandler resultHandler = new SearchResultHandler(
                completionState,
                coprocessorSettingsMap,
                defaultMaxResultsSizes,
                storeSize);

        // Create the search result collector.
        final SolrSearchResultCollector searchResultCollector = SolrSearchResultCollector.create(
                executor,
                taskContextFactory,
                solrAsyncSearchTaskHandlerProvider,
                asyncSearchTask,
                highlights,
                resultHandler,
                defaultMaxResultsSizes,
                storeSize,
                completionState);

        // Tell the task where results will be collected.
        asyncSearchTask.setResultCollector(searchResultCollector);

        // Start asynchronous search execution.
        searchResultCollector.start();

        return searchResultCollector;
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

    /**
     * Compiles the query, extracts terms and then returns them for use in hit
     * highlighting.
     */
    private Set<String> getHighlights(final CachedSolrIndex index, final ExpressionOperator expression, final String timeZoneId, final long nowEpochMilli) {
        Set<String> highlights = Collections.emptySet();

        try {
            // Create a map of index fields keyed by name.
            final Map<String, SolrIndexField> indexFieldsMap = index
                    .getFields()
                    .stream()
                    .collect(Collectors.toMap(SolrIndexField::getFieldName, Function.identity()));
            // Parse the query.
            final SearchExpressionQueryBuilder searchExpressionQueryBuilder = new SearchExpressionQueryBuilder(
                    wordListProvider, indexFieldsMap, searchConfig.getMaxBooleanClauseCount(), timeZoneId, nowEpochMilli);
            final SearchExpressionQuery query = searchExpressionQueryBuilder
                    .buildQuery(expression);

            highlights = query.getTerms();
        } catch (final RuntimeException e) {
            LOGGER.debug(e.getMessage(), e);
        }

        return highlights;
    }
}
