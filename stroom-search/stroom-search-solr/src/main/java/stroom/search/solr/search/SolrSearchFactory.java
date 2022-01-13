package stroom.search.solr.search;

import stroom.dashboard.expression.v1.FieldIndex;
import stroom.dictionary.api.WordListProvider;
import stroom.query.api.v2.DateTimeSettings;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.Query;
import stroom.query.api.v2.QueryKey;
import stroom.query.common.v2.ErrorConsumer;
import stroom.query.common.v2.SearchProgressLog;
import stroom.query.common.v2.SearchProgressLog.SearchPhase;
import stroom.search.extraction.StoredDataQueue;
import stroom.search.solr.CachedSolrIndex;
import stroom.search.solr.SolrIndexCache;
import stroom.search.solr.search.SearchExpressionQueryBuilder.SearchExpressionQuery;
import stroom.search.solr.shared.SolrIndexField;
import stroom.task.api.ExecutorProvider;
import stroom.task.api.TaskContext;
import stroom.task.api.TaskContextFactory;
import stroom.task.api.ThreadPoolImpl;
import stroom.task.shared.ThreadPool;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import org.apache.solr.client.solrj.SolrQuery;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicLong;
import javax.inject.Inject;

public class SolrSearchFactory {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(SolrSearchFactory.class);

    private static final ThreadPool THREAD_POOL = new ThreadPoolImpl(
            "Search Solr Index");

    private final WordListProvider wordListProvider;
    private final SolrSearchConfig config;
    private final SolrSearchTaskHandler solrSearchTaskHandler;
    private final SolrIndexCache solrIndexCache;
    private final TaskContextFactory taskContextFactory;
    private final Executor executor;

    @Inject
    public SolrSearchFactory(final WordListProvider wordListProvider,
                             final SolrSearchConfig config,
                             final SolrSearchTaskHandler solrSearchTaskHandler,
                             final SolrIndexCache solrIndexCache,
                             final TaskContextFactory taskContextFactory,
                             final ExecutorProvider executorProvider) {
        this.wordListProvider = wordListProvider;
        this.config = config;
        this.solrSearchTaskHandler = solrSearchTaskHandler;
        this.solrIndexCache = solrIndexCache;
        this.taskContextFactory = taskContextFactory;
        this.executor = executorProvider.get(THREAD_POOL);
    }

    public CompletableFuture<Void> search(final QueryKey queryKey,
                                          final Query query,
                                          final long now,
                                          final DateTimeSettings dateTimeSettings,
                                          final ExpressionOperator expression,
                                          final FieldIndex fieldIndex,
                                          final TaskContext parentContext,
                                          final AtomicLong hitCount,
                                          final StoredDataQueue storedDataQueue,
                                          final ErrorConsumer errorConsumer) {
        SearchProgressLog.increment(queryKey, SearchPhase.INDEX_SHARD_SEARCH_FACTORY_SEARCH);

        // Reload the index.
        final CachedSolrIndex index = solrIndexCache.get(query.getDataSource());

        // Make sure we have a search index.
        if (index == null) {
            throw new SearchException("Search index has not been set");
        }

        // Create a map of index fields keyed by name.
        final Map<String, SolrIndexField> indexFieldsMap = index.getFieldsMap();

        final String[] storedFieldNames = new String[fieldIndex.size()];
        for (int i = 0; i < storedFieldNames.length; i++) {
            final String fieldName = fieldIndex.getField(i);
            if (fieldName != null) {
                final SolrIndexField indexField = indexFieldsMap.get(fieldName);
                if (indexField != null && indexField.isStored()) {
                    storedFieldNames[i] = fieldName;
                }
            }
        }

        // Get the stored fields that search is hoping to use.
        if (storedFieldNames.length == 0) {
            throw new SearchException("No stored fields have been requested");
        }

        // Create a map of index fields keyed by name.
        final SearchExpressionQuery searchExpressionQuery = getQuery(expression, indexFieldsMap, dateTimeSettings, now);
        final String queryString = searchExpressionQuery.getQuery().toString();
        final SolrQuery solrQuery = new SolrQuery(queryString);
        solrQuery.setRows(Integer.MAX_VALUE);

        // When we complete the index shard search tell the stored data queue we are complete.
        return CompletableFuture
                .runAsync(() -> taskContextFactory
                        .childContext(parentContext, "Search Index", taskContext -> {
                            solrSearchTaskHandler.search(
                                    parentContext,
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

    private SearchExpressionQuery getQuery(final ExpressionOperator expression,
                                           final Map<String, SolrIndexField> indexFieldsMap,
                                           final DateTimeSettings dateTimeSettings,
                                           final long nowEpochMilli) {
        final SearchExpressionQueryBuilder builder = new SearchExpressionQueryBuilder(
                wordListProvider,
                indexFieldsMap,
                config.getMaxBooleanClauseCount(),
                dateTimeSettings,
                nowEpochMilli);
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
