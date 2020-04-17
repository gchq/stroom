package stroom.search.solr.search;

import org.apache.solr.client.solrj.SolrQuery;
import stroom.dictionary.api.WordListProvider;
import stroom.query.api.v2.ExpressionOperator;
import stroom.search.coprocessor.Receiver;
import stroom.search.solr.CachedSolrIndex;
import stroom.search.solr.search.SearchExpressionQueryBuilder.SearchExpressionQuery;
import stroom.search.solr.shared.SolrIndexField;
import stroom.task.api.TaskContext;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import javax.inject.Inject;
import java.util.Map;

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

    public void search(final SolrClusterSearchTask task, final ExpressionOperator expression, final Receiver receiver, final TaskContext taskContext) {
        try {
            // Reload the index.
            final CachedSolrIndex index = task.getCachedSolrIndex();

            // Make sure we have a search index.
            if (index == null) {
                throw new SearchException("Search index has not been set");
            }

            // Create a map of index fields keyed by name.
            final Map<String, SolrIndexField> indexFieldsMap = index.getFieldsMap();
            final SearchExpressionQuery query = getQuery(expression, indexFieldsMap, task.getDateTimeLocale(), task.getNow());
            final String queryString = query.toString();
            final SolrQuery solrQuery = new SolrQuery(queryString);
            solrQuery.setRows(Integer.MAX_VALUE);

            final Tracker tracker = new Tracker();
            final SolrSearchTask solrSearchTask = new SolrSearchTask(index, solrQuery, task.getStoredFields(), receiver, tracker);
            solrSearchTaskHandler.exec(taskContext, solrSearchTask);

            // Wait until we finish.
            while (!Thread.currentThread().isInterrupted() && (!tracker.isCompleted())) {
                taskContext.info(() ->
                        "Searching... " +
                                "found " + tracker.getHitCount() + " hits");
                Thread.sleep(1000);
            }
        } catch (final InterruptedException e) {
            // Continue to interrupt.
            Thread.currentThread().interrupt();
            LOGGER.debug(e::getMessage, e);
        }
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
