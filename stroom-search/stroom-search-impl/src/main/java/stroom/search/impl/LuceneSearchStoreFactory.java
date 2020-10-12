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

package stroom.search.impl;

import stroom.dictionary.api.WordListProvider;
import stroom.index.impl.IndexStore;
import stroom.index.impl.LuceneVersionUtil;
import stroom.index.shared.IndexDoc;
import stroom.index.shared.IndexFieldsMap;
import stroom.node.api.NodeInfo;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.ExpressionParamUtil;
import stroom.query.api.v2.ExpressionUtil;
import stroom.query.api.v2.Query;
import stroom.query.api.v2.SearchRequest;
import stroom.query.common.v2.CoprocessorSettingsMap;
import stroom.query.common.v2.SearchResultHandler;
import stroom.query.common.v2.Sizes;
import stroom.query.common.v2.Store;
import stroom.query.common.v2.StoreFactory;
import stroom.search.impl.SearchExpressionQueryBuilder.SearchExpressionQuery;
import stroom.security.api.SecurityContext;
import stroom.ui.config.shared.UiConfig;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class LuceneSearchStoreFactory implements StoreFactory {
    private static final Logger LOGGER = LoggerFactory.getLogger(LuceneSearchStoreFactory.class);

    private final IndexStore indexStore;
    private final WordListProvider wordListProvider;
    private final SearchConfig searchConfig;
    private final UiConfig clientConfig;
    private final NodeInfo nodeInfo;
    private final int maxBooleanClauseCount;
    private final SecurityContext securityContext;
    private final ClusterSearchResultCollectorFactory clusterSearchResultCollectorFactory;

    @Inject
    public LuceneSearchStoreFactory(final IndexStore indexStore,
                                    final WordListProvider wordListProvider,
                                    final SearchConfig searchConfig,
                                    final UiConfig clientConfig,
                                    final NodeInfo nodeInfo,
                                    final SecurityContext securityContext,
                                    final ClusterSearchResultCollectorFactory clusterSearchResultCollectorFactory) {
        this.indexStore = indexStore;
        this.wordListProvider = wordListProvider;
        this.searchConfig = searchConfig;
        this.clientConfig = clientConfig;
        this.nodeInfo = nodeInfo;
        this.maxBooleanClauseCount = searchConfig.getMaxBooleanClauseCount();
        this.securityContext = securityContext;
        this.clusterSearchResultCollectorFactory = clusterSearchResultCollectorFactory;
    }

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
        final IndexDoc index = securityContext.useAsReadResult(() -> indexStore.readDocument(query.getDataSource()));

        // Extract highlights.
        final Set<String> highlights = getHighlights(index, query.getExpression(), searchRequest.getDateTimeLocale(), nowEpochMilli);

        // Create a coprocessor settings map.
        final CoprocessorSettingsMap coprocessorSettingsMap = CoprocessorSettingsMap.create(searchRequest);

        // Create an asynchronous search task.
        final String searchName = "Search '" + searchRequest.getKey().toString() + "'";
        final AsyncSearchTask asyncSearchTask = new AsyncSearchTask(
                searchRequest.getKey(),
                searchName,
                query,
                coprocessorSettingsMap.getMap(),
                searchRequest.getDateTimeLocale(),
                nowEpochMilli);

        // Create a handler for search results.
        final Sizes storeSize = getStoreSizes();
        final Sizes defaultMaxResultsSizes = getDefaultMaxResultsSizes();
        final SearchResultHandler resultHandler = new SearchResultHandler(
                coprocessorSettingsMap,
                defaultMaxResultsSizes,
                storeSize);

        // Create the search result collector.
        final ClusterSearchResultCollector searchResultCollector = clusterSearchResultCollectorFactory.create(
                asyncSearchTask,
                nodeInfo.getThisNodeName(),
                highlights,
                resultHandler,
                defaultMaxResultsSizes,
                storeSize);

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
    private Set<String> getHighlights(final IndexDoc index, final ExpressionOperator expression, final String timeZoneId, final long nowEpochMilli) {
        Set<String> highlights = Collections.emptySet();

        try {
            // Create a map of index fields keyed by name.
            final IndexFieldsMap indexFieldsMap = new IndexFieldsMap(index.getFields());
            // Parse the query.
            final SearchExpressionQueryBuilder searchExpressionQueryBuilder = new SearchExpressionQueryBuilder(
                    wordListProvider, indexFieldsMap, maxBooleanClauseCount, timeZoneId, nowEpochMilli);
            final SearchExpressionQuery query = searchExpressionQueryBuilder
                    .buildQuery(LuceneVersionUtil.CURRENT_LUCENE_VERSION, expression);

            highlights = query.getTerms();
        } catch (final RuntimeException e) {
            LOGGER.debug(e.getMessage(), e);
        }

        return highlights;
    }
}
