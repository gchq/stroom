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
import stroom.query.api.v2.DateTimeSettings;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.ExpressionUtil;
import stroom.query.api.v2.Query;
import stroom.query.api.v2.SearchRequest;
import stroom.query.common.v2.CoprocessorSettings;
import stroom.query.common.v2.Coprocessors;
import stroom.query.common.v2.CoprocessorsFactory;
import stroom.query.common.v2.DataStoreSettings;
import stroom.query.common.v2.ResultStore;
import stroom.query.common.v2.ResultStoreFactory;
import stroom.query.common.v2.SearchProvider;
import stroom.search.impl.SearchExpressionQueryBuilder.SearchExpressionQuery;
import stroom.security.api.SecurityContext;
import stroom.task.api.ExecutorProvider;
import stroom.task.api.TaskContextFactory;
import stroom.task.api.TerminateHandlerFactory;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.function.Supplier;
import javax.inject.Inject;

public class LuceneSearchProvider implements SearchProvider {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(LuceneSearchProvider.class);

    private final IndexStore indexStore;
    private final WordListProvider wordListProvider;
    private final int maxBooleanClauseCount;
    private final SecurityContext securityContext;
    private final CoprocessorsFactory coprocessorsFactory;
    private final ResultStoreFactory resultStoreFactory;
    private final TaskContextFactory taskContextFactory;
    private final ExecutorProvider executorProvider;
    private final LuceneSearchExecutor luceneSearchExecutor;

    @Inject
    public LuceneSearchProvider(final IndexStore indexStore,
                                final WordListProvider wordListProvider,
                                final SearchConfig searchConfig,
                                final SecurityContext securityContext,
                                final CoprocessorsFactory coprocessorsFactory,
                                final ResultStoreFactory resultStoreFactory,
                                final TaskContextFactory taskContextFactory,
                                final ExecutorProvider executorProvider,
                                final LuceneSearchExecutor luceneSearchExecutor) {
        this.indexStore = indexStore;
        this.wordListProvider = wordListProvider;
        this.maxBooleanClauseCount = searchConfig.getMaxBooleanClauseCount();
        this.securityContext = securityContext;
        this.coprocessorsFactory = coprocessorsFactory;
        this.resultStoreFactory = resultStoreFactory;
        this.taskContextFactory = taskContextFactory;
        this.executorProvider = executorProvider;
        this.luceneSearchExecutor = luceneSearchExecutor;
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

    public ResultStore createResultStore(final SearchRequest searchRequest) {
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
                DataStoreSettings.BASIC_SETTINGS);

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
        final ResultStore resultStore = resultStoreFactory.create(
                searchRequest.getSearchRequestSource(),
                coprocessors);
        resultStore.addHighlights(highlights);

        luceneSearchExecutor.start(asyncSearchTask, resultStore);

        return resultStore;
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
