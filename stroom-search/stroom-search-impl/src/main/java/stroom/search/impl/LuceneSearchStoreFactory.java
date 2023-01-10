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

import stroom.datasource.api.v2.DataSource;
import stroom.datasource.api.v2.DateField;
import stroom.dictionary.api.WordListProvider;
import stroom.docref.DocRef;
import stroom.index.impl.IndexStore;
import stroom.index.impl.LuceneVersionUtil;
import stroom.index.shared.IndexDoc;
import stroom.index.shared.IndexFieldsMap;
import stroom.node.api.NodeInfo;
import stroom.query.api.v2.DateTimeSettings;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.ExpressionUtil;
import stroom.query.api.v2.Query;
import stroom.query.api.v2.SearchRequest;
import stroom.query.common.v2.CoprocessorSettings;
import stroom.query.common.v2.Coprocessors;
import stroom.query.common.v2.CoprocessorsFactory;
import stroom.query.common.v2.Store;
import stroom.query.common.v2.StoreFactory;
import stroom.search.impl.SearchExpressionQueryBuilder.SearchExpressionQuery;
import stroom.security.api.SecurityContext;
import stroom.task.api.ExecutorProvider;
import stroom.task.api.TaskContextFactory;
import stroom.task.api.TerminateHandlerFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.function.Supplier;
import javax.inject.Inject;

public class LuceneSearchStoreFactory implements StoreFactory {

    private static final Logger LOGGER = LoggerFactory.getLogger(LuceneSearchStoreFactory.class);

    private final IndexStore indexStore;
    private final WordListProvider wordListProvider;
    private final NodeInfo nodeInfo;
    private final int maxBooleanClauseCount;
    private final SecurityContext securityContext;
    private final ClusterSearchResultCollectorFactory clusterSearchResultCollectorFactory;
    private final CoprocessorsFactory coprocessorsFactory;
    private final TaskContextFactory taskContextFactory;
    private final ExecutorProvider executorProvider;

    @Inject
    public LuceneSearchStoreFactory(final IndexStore indexStore,
                                    final WordListProvider wordListProvider,
                                    final SearchConfig searchConfig,
                                    final NodeInfo nodeInfo,
                                    final SecurityContext securityContext,
                                    final ClusterSearchResultCollectorFactory clusterSearchResultCollectorFactory,
                                    final CoprocessorsFactory coprocessorsFactory,
                                    final TaskContextFactory taskContextFactory,
                                    final ExecutorProvider executorProvider) {
        this.indexStore = indexStore;
        this.wordListProvider = wordListProvider;
        this.nodeInfo = nodeInfo;
        this.maxBooleanClauseCount = searchConfig.getMaxBooleanClauseCount();
        this.securityContext = securityContext;
        this.clusterSearchResultCollectorFactory = clusterSearchResultCollectorFactory;
        this.coprocessorsFactory = coprocessorsFactory;
        this.taskContextFactory = taskContextFactory;
        this.executorProvider = executorProvider;
    }

    @Override
    public DataSource getDataSource(final DocRef docRef) {
        return securityContext.useAsReadResult(() -> {
            final Supplier<DataSource> supplier = taskContextFactory.contextResult(
                    "Getting Data Source",
                    TerminateHandlerFactory.NOOP_FACTORY,
                    taskContext -> {
                        final IndexDoc index = indexStore.readDocument(docRef);
                        return DataSource
                                .builder()
                                .fields(IndexDataSourceFieldUtil.getDataSourceFields(index, securityContext))
                                .defaultExtractionPipeline(index.getDefaultExtractionPipeline())
                                .build();
                    });
            final Executor executor = executorProvider.get();
            final CompletableFuture<DataSource> completableFuture = CompletableFuture.supplyAsync(supplier, executor);
            try {
                return completableFuture.get();
            } catch (final InterruptedException | ExecutionException e) {
                throw new RuntimeException(e.getMessage(), e);
            }
        });
    }

    @Override
    public DateField getTimeField(final DocRef docRef) {
        return securityContext.useAsReadResult(() -> {
            final IndexDoc index = indexStore.readDocument(docRef);
            DateField timeField = null;
            if (index.getTimeField() != null && !index.getTimeField().isBlank()) {
                timeField = new DateField(index.getTimeField());
            }
            return timeField;
        });
    }

    public Store create(final SearchRequest searchRequest) {
        // Get the current time in millis since epoch.
        final long nowEpochMilli = System.currentTimeMillis();

        // Replace expression parameters.
        final SearchRequest modifiedSearchRequest = ExpressionUtil.replaceExpressionParameters(searchRequest);

        // Get the search.
        final Query query = modifiedSearchRequest.getQuery();

        // Load the index.
        final IndexDoc index = securityContext.useAsReadResult(() ->
                indexStore.readDocument(query.getDataSource()));

        // Extract highlights.
        final Set<String> highlights = getHighlights(
                index,
                query.getExpression(),
                modifiedSearchRequest.getDateTimeSettings(),
                nowEpochMilli);

        // Create a coprocessor settings list.
        final List<CoprocessorSettings> coprocessorSettingsList = coprocessorsFactory
                .createSettings(modifiedSearchRequest);

        // Create a handler for search results.
        final Coprocessors coprocessors = coprocessorsFactory.create(
                modifiedSearchRequest.getKey(),
                coprocessorSettingsList,
                query.getParams(),
                false);

        // Create an asynchronous search task.
        final String searchName = "Search '" + modifiedSearchRequest.getKey().toString() + "'";
        final AsyncSearchTask asyncSearchTask = new AsyncSearchTask(
                modifiedSearchRequest.getKey(),
                searchName,
                query,
                coprocessorSettingsList,
                modifiedSearchRequest.getDateTimeSettings(),
                nowEpochMilli);

        // Create the search result collector.
        final ClusterSearchResultCollector searchResultCollector = clusterSearchResultCollectorFactory.create(
                asyncSearchTask,
                nodeInfo.getThisNodeName(),
                highlights,
                coprocessors);

        // Tell the task where results will be collected.
        asyncSearchTask.setResultCollector(searchResultCollector);

        // Start asynchronous search execution.
        searchResultCollector.start();

        return searchResultCollector;
    }

    /**
     * Compiles the query, extracts terms and then returns them for use in hit
     * highlighting.
     */
    private Set<String> getHighlights(final IndexDoc index,
                                      final ExpressionOperator expression,
                                      DateTimeSettings dateTimeSettings,
                                      final long nowEpochMilli) {
        Set<String> highlights = Collections.emptySet();

        try {
            // Create a map of index fields keyed by name.
            final IndexFieldsMap indexFieldsMap = new IndexFieldsMap(index.getFields());
            // Parse the query.
            final SearchExpressionQueryBuilder searchExpressionQueryBuilder = new SearchExpressionQueryBuilder(
                    wordListProvider, indexFieldsMap, maxBooleanClauseCount, dateTimeSettings, nowEpochMilli);
            final SearchExpressionQuery query = searchExpressionQueryBuilder
                    .buildQuery(LuceneVersionUtil.CURRENT_LUCENE_VERSION, expression);

            highlights = query.getTerms();
        } catch (final RuntimeException e) {
            LOGGER.debug(e.getMessage(), e);
        }

        return highlights;
    }

    @Override
    public String getType() {
        return IndexDoc.DOCUMENT_TYPE;
    }
}
