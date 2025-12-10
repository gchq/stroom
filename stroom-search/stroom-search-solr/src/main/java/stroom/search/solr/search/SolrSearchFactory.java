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

import stroom.dictionary.api.WordListProvider;
import stroom.docref.DocRef;
import stroom.query.api.DateTimeSettings;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.Query;
import stroom.query.api.QueryKey;
import stroom.query.common.v2.IndexFieldCache;
import stroom.query.common.v2.SearchProgressLog;
import stroom.query.common.v2.SearchProgressLog.SearchPhase;
import stroom.query.language.functions.FieldIndex;
import stroom.query.language.functions.ref.ErrorConsumer;
import stroom.search.extraction.StoredDataQueue;
import stroom.search.solr.SolrIndexDocCache;
import stroom.search.solr.search.SearchExpressionQueryBuilder.SearchExpressionQuery;
import stroom.search.solr.shared.SolrIndexDoc;
import stroom.task.api.ExecutorProvider;
import stroom.task.api.TaskContext;
import stroom.task.api.TaskContextFactory;
import stroom.task.api.ThreadPoolImpl;
import stroom.task.shared.ThreadPool;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import jakarta.inject.Inject;
import org.apache.solr.client.solrj.SolrQuery;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.LongAdder;

public class SolrSearchFactory {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(SolrSearchFactory.class);

    private static final ThreadPool THREAD_POOL = new ThreadPoolImpl(
            "Search Solr Index");

    private final WordListProvider wordListProvider;
    private final SolrSearchConfig config;
    private final SolrSearchTaskHandler solrSearchTaskHandler;
    private final SolrIndexDocCache solrIndexDocCache;
    private final IndexFieldCache indexFieldCache;
    private final TaskContextFactory taskContextFactory;
    private final Executor executor;

    @Inject
    public SolrSearchFactory(final WordListProvider wordListProvider,
                             final SolrSearchConfig config,
                             final SolrSearchTaskHandler solrSearchTaskHandler,
                             final SolrIndexDocCache solrIndexDocCache,
                             final IndexFieldCache indexFieldCache,
                             final TaskContextFactory taskContextFactory,
                             final ExecutorProvider executorProvider) {
        this.wordListProvider = wordListProvider;
        this.config = config;
        this.solrSearchTaskHandler = solrSearchTaskHandler;
        this.solrIndexDocCache = solrIndexDocCache;
        this.indexFieldCache = indexFieldCache;
        this.taskContextFactory = taskContextFactory;
        this.executor = executorProvider.get(THREAD_POOL);
    }

    public CompletableFuture<Void> search(final QueryKey queryKey,
                                          final Query query,
                                          final DateTimeSettings dateTimeSettings,
                                          final ExpressionOperator expression,
                                          final FieldIndex fieldIndex,
                                          final TaskContext parentContext,
                                          final LongAdder hitCount,
                                          final StoredDataQueue storedDataQueue,
                                          final ErrorConsumer errorConsumer) {
        SearchProgressLog.increment(queryKey, SearchPhase.INDEX_SHARD_SEARCH_FACTORY_SEARCH);

        // Reload the index.
        final SolrIndexDoc index = solrIndexDocCache.get(query.getDataSource());

        // Make sure we have a search index.
        if (index == null) {
            throw new SearchException("Search index has not been set");
        }

        // Create a map of index fields keyed by name.
        final SearchExpressionQuery searchExpressionQuery =
                getQuery(query.getDataSource(), expression, dateTimeSettings);
        final String queryString = searchExpressionQuery.getQuery().toString();
        final SolrQuery solrQuery = new SolrQuery(queryString);
        solrQuery.setRows(Integer.MAX_VALUE);

        // When we complete the index shard search tell the stored data queue we are complete.
        return CompletableFuture
                .runAsync(() -> taskContextFactory
                        .childContext(parentContext, "Search Index", taskContext -> {
                            solrSearchTaskHandler.search(
                                    parentContext,
                                    query.getDataSource(),
                                    index,
                                    solrQuery,
                                    fieldIndex,
                                    storedDataQueue,
                                    errorConsumer,
                                    hitCount);
                        }), executor)
                .whenCompleteAsync((r, t) ->
                        taskContextFactory.childContext(parentContext, "Search Index", taskContext -> {
                            taskContext.info(() -> "Complete stored data queue");
                            LOGGER.debug("Complete stored data queue");
                            storedDataQueue.complete();
                        }).run(), executor);
    }

    private SearchExpressionQuery getQuery(final DocRef indexDocRef,
                                           final ExpressionOperator expression,
                                           final DateTimeSettings dateTimeSettings) {
        final SearchExpressionQueryBuilder builder = new SearchExpressionQueryBuilder(
                indexDocRef,
                indexFieldCache,
                wordListProvider,
                config.getMaxBooleanClauseCount(),
                dateTimeSettings);
        final SearchExpressionQuery query = builder.buildQuery(expression);

        // Make sure the query was created successfully.
        if (query.getQuery() == null) {
            throw new SearchException("Failed to build query given expression");
        } else {
            LOGGER.debug(() -> "Query is " + query);
        }

        return query;
    }
}
