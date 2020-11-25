package stroom.search.solr.search;

import stroom.dictionary.api.WordListProvider;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.common.v2.Receiver;
import stroom.search.solr.CachedSolrIndex;
import stroom.search.solr.search.SearchExpressionQueryBuilder.SearchExpressionQuery;
import stroom.search.solr.shared.SolrIndexField;
import stroom.task.api.TaskContext;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import org.apache.solr.client.solrj.SolrQuery;

import javax.inject.Inject;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public class SolrSearchFactory {
    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(SolrSearchFactory.class);

    private final WordListProvider wordListProvider;
    private final SolrSearchConfig config;
    private final SolrSearchTaskHandler solrSearchTaskHandler;

    @Inject
    public SolrSearchFactory(final WordListProvider wordListProvider,
                             final SolrSearchConfig config,
                             final SolrSearchTaskHandler solrSearchTaskHandler) {
        this.wordListProvider = wordListProvider;
        this.config = config;
        this.solrSearchTaskHandler = solrSearchTaskHandler;
    }

    public void search(final CachedSolrIndex index,
                       final String[] storedFields,
                       final long now,
                       final ExpressionOperator expression,
                       final Receiver receiver,
                       final TaskContext taskContext,
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
        final SolrSearchTask solrSearchTask = new SolrSearchTask(index, solrQuery, storedFields, receiver, tracker);
        solrSearchTaskHandler.exec(taskContext, solrSearchTask);

        // Wait until we finish.
        try {
            while (!tracker.awaitCompletion(1, TimeUnit.SECONDS)) {
                taskContext.info(() -> "" +
                        "Searching... " +
                        "found " +
                        hitCount.get() +
                        " hits");
            }
        } catch (final InterruptedException e) {
            LOGGER.debug(this::toString);
            // Keep interrupting.
            Thread.currentThread().interrupt();
        }

        // Let the receiver know we are complete.
        receiver.getCompletionConsumer().accept(hitCount.get());
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
            LOGGER.debug(() -> "Query is " + query.toString());
        }

        return query;
    }
}
