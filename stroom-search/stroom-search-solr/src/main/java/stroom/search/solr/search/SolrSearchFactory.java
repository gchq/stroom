package stroom.search.solr.search;

import stroom.dashboard.expression.v1.ValuesConsumer;
import stroom.dictionary.api.WordListProvider;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.common.v2.ErrorConsumer;
import stroom.search.solr.CachedSolrIndex;
import stroom.search.solr.search.SearchExpressionQueryBuilder.SearchExpressionQuery;
import stroom.search.solr.shared.SolrIndexField;
import stroom.task.api.ExecutorProvider;
import stroom.task.api.TaskContext;
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
            "Search Solr Index Shard");

    private final WordListProvider wordListProvider;
    private final SolrSearchConfig config;
    private final SolrSearchTaskHandler solrSearchTaskHandler;
    private final Executor executor;

    @Inject
    public SolrSearchFactory(final WordListProvider wordListProvider,
                             final SolrSearchConfig config,
                             final SolrSearchTaskHandler solrSearchTaskHandler,
                             final ExecutorProvider executorProvider) {
        this.wordListProvider = wordListProvider;
        this.config = config;
        this.solrSearchTaskHandler = solrSearchTaskHandler;
        this.executor = executorProvider.get(THREAD_POOL);
    }

    public CompletableFuture<Void> search(final CachedSolrIndex index,
                                          final String[] storedFields,
                                          final long now,
                                          final ExpressionOperator expression,
                                          final ValuesConsumer valuesConsumer,
                                          final ErrorConsumer errorConsumer,
                                          final TaskContext parentContext,
                                          final AtomicLong hitCount,
                                          final String dateTimeLocale) {
        // Make sure we have a search index.
        if (index == null) {
            throw new SearchException("Search index has not been set");
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
                valuesConsumer,
                errorConsumer,
                tracker);
        return CompletableFuture.runAsync(solrSearchTaskHandler.exec(parentContext, solrSearchTask), executor);
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
}
