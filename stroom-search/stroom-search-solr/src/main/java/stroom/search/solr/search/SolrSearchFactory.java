package stroom.search.solr.search;

import stroom.dictionary.api.WordListProvider;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.Query;
import stroom.query.common.v2.ErrorConsumer;
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
import java.util.concurrent.atomic.LongAdder;
import javax.inject.Inject;

public class SolrSearchFactory {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(SolrSearchFactory.class);

    private static final ThreadPool THREAD_POOL = new ThreadPoolImpl(
            "Search Solr Index Shard");

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

    public CompletableFuture<Void> search(final Query query,
                                          final long now,
                                          final ExpressionOperator expression,
                                          final StoredDataQueue storedDataQueue,
                                          final ErrorConsumer errorConsumer,
                                          final TaskContext parentContext,
                                          final LongAdder hitCount,
                                          final String dateTimeLocale) {
        // Make sure we have been given a query.
        if (query.getExpression() == null) {
            throw new SearchException("Search expression has not been set");
        }

        // Make sure we have a search index.
        if (query.getDataSource() == null) {
            throw new SearchException("Search index has not been set");
        }

        // Reload the index.
        final CachedSolrIndex index = solrIndexCache.get(query.getDataSource());

        // Make sure we have a search index.
        if (index == null) {
            throw new SearchException("Search index has not been set");
        }

        // Get an array of stored index fields that will be used for
        // getting stored data.
        // TODO : Specify stored fields based on the fields that all
        // coprocessors will require. Also
        // batch search only needs stream and event id stored fields.
        final String[] storedFields = getStoredFields(index);

        // Get the stored fields that search is hoping to use.
        if (storedFields.length == 0) {
            throw new SearchException("No stored fields have been requested");
        }

        // Create a map of index fields keyed by name.
        final Map<String, SolrIndexField> indexFieldsMap = index.getFieldsMap();
        final SearchExpressionQuery searchExpressionQuery = getQuery(expression, indexFieldsMap, dateTimeLocale, now);
        final String queryString = searchExpressionQuery.getQuery().toString();
        final SolrQuery solrQuery = new SolrQuery(queryString);
        solrQuery.setRows(Integer.MAX_VALUE);

        final Tracker tracker = new Tracker(hitCount);
        final SolrSearchTask solrSearchTask = new SolrSearchTask(index,
                solrQuery,
                storedFields,
                storedDataQueue,
                errorConsumer,
                tracker);

        // When we complete the index shard search tell the stored data queue we are complete.
        return CompletableFuture
                .runAsync(solrSearchTaskHandler.exec(parentContext, solrSearchTask), executor)
                .whenCompleteAsync((r, t) ->
                        taskContextFactory.childContext(parentContext, "Index Searcher", taskContext -> {
                            taskContext.info(() -> "Complete stored data queue");
                            LOGGER.debug("Complete stored data queue");
                            storedDataQueue.complete();
                        }).run(), executor);
    }

    private SearchExpressionQuery getQuery(final ExpressionOperator expression,
                                           final Map<String, SolrIndexField> indexFieldsMap,
                                           final String timeZoneId,
                                           final long nowEpochMilli) {
        final SearchExpressionQueryBuilder searchExpressionQueryBuilder = new SearchExpressionQueryBuilder(
                wordListProvider,
                indexFieldsMap,
                config.getMaxBooleanClauseCount(),
                timeZoneId,
                nowEpochMilli);
        final SearchExpressionQuery query = searchExpressionQueryBuilder.buildQuery(expression);

        // Make sure the query was created successfully.
        if (query.getQuery() == null) {
            throw new SearchException("Failed to build query given expression");
        } else {
            LOGGER.debug(() -> "Query is " + query);
        }

        return query;
    }

    private String[] getStoredFields(final CachedSolrIndex index) {
        return index.getFields()
                .stream()
                .filter(SolrIndexField::isStored)
                .map(SolrIndexField::getFieldName)
                .toArray(String[]::new);
    }
}
