package stroom.search.elastic.search;

import stroom.dashboard.expression.v1.FieldIndex;
import stroom.dictionary.api.WordListProvider;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.common.v2.Receiver;
import stroom.search.elastic.ElasticIndexService;
import stroom.search.elastic.shared.ElasticIndexDoc;
import stroom.search.elastic.shared.ElasticIndexField;
import stroom.task.api.TaskContext;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import org.elasticsearch.index.query.QueryBuilder;

import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import javax.inject.Inject;

public class ElasticSearchFactory {
    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ElasticSearchFactory.class);

    private final WordListProvider wordListProvider;
    private final ElasticSearchTaskHandler elasticSearchTaskHandler;
    private final ElasticIndexService elasticIndexService;

    @Inject
    public ElasticSearchFactory(final WordListProvider wordListProvider,
                                final ElasticSearchConfig elasticSearchConfig,
                                final ElasticSearchTaskHandler elasticSearchTaskHandler,
                                final ElasticIndexService elasticIndexService
    ) {
        this.wordListProvider = wordListProvider;
        this.elasticSearchTaskHandler = elasticSearchTaskHandler;
        this.elasticIndexService = elasticIndexService;
    }

    public void search(final ElasticAsyncSearchTask asyncSearchTask,
                       final ElasticIndexDoc index,
                       final FieldIndex fieldIndex,
                       final long now,
                       final ExpressionOperator expression,
                       final Receiver receiver,
                       final TaskContext taskContext,
                       final AtomicLong hitCount,
                       final String dateTimeLocale
    ) {
        if (index == null) {
            throw new SearchException("Search index has not been set");
        }

        // Create a map of index fields keyed by name.
        final Map<String, ElasticIndexField> indexFieldsMap = elasticIndexService.getFieldsMap(index);
        final QueryBuilder queryBuilder = getQuery(expression, indexFieldsMap, dateTimeLocale, now);
        final Tracker tracker = new Tracker(hitCount);
        final ElasticSearchTask elasticSearchTask = new ElasticSearchTask(
                asyncSearchTask, index, queryBuilder, fieldIndex, receiver, tracker,
                asyncSearchTask.getResultCollector());

        elasticSearchTaskHandler.exec(taskContext, elasticSearchTask);

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
            // Keep interrupting
            Thread.currentThread().interrupt();
        }

        // Let the receiver know we are complete
        receiver.getCompletionConsumer().accept(hitCount.get());
    }

    private QueryBuilder getQuery(final ExpressionOperator expression,
                                  final Map<String, ElasticIndexField> indexFieldsMap,
                                  final String timeZoneId,
                                  final long nowEpochMilli
    ) {
        final SearchExpressionQueryBuilder builder = new SearchExpressionQueryBuilder(
                wordListProvider,
                indexFieldsMap,
                timeZoneId,
                nowEpochMilli);

        final QueryBuilder query = builder.buildQuery(expression);

        // Make sure the query was created successfully.
        if (query == null) {
            throw new SearchException("Failed to build query given expression");
        } else {
            LOGGER.debug(() -> "Query is " + query.toString());
        }

        return query;
    }
}
