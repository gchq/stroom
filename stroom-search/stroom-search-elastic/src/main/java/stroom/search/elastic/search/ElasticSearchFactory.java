package stroom.search.elastic.search;

import stroom.dictionary.server.DictionaryStore;
import stroom.query.api.v2.ExpressionOperator;
import stroom.search.coprocessor.Receiver;
import stroom.search.elastic.ElasticIndexService;
import stroom.search.elastic.shared.ElasticIndex;
import stroom.search.elastic.shared.ElasticIndexField;
import stroom.task.server.TaskContext;
import stroom.util.config.PropertyUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.HasTerminate;
import stroom.util.spring.StroomScope;

import org.elasticsearch.index.query.QueryBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import javax.inject.Inject;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

@Component
@Scope(StroomScope.TASK)
public class ElasticSearchFactory {
    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ElasticSearchFactory.class);

    /**
     * We don't want to collect more than 1 million doc's data into the queue by
     * default. When the queue is full the index shard data tasks will pause
     * until the docs are drained from the queue.
     */
    private static final int DEFAULT_MAX_STORED_DATA_QUEUE_SIZE = 1000000;
    private static final int DEFAULT_MAX_BOOLEAN_CLAUSE_COUNT = 1024;

    private final DictionaryStore dictionaryStore;
    private final int maxBooleanClauseCount;
    private final int maxStoredDataQueueSize;
    private final ElasticIndexService elasticIndexService;
    private final ElasticSearchTaskHandler elasticSearchTaskHandler;

    @Inject
    public ElasticSearchFactory(final DictionaryStore dictionaryStore,
                                @Value("#{propertyConfigurer.getProperty('stroom.search.maxBooleanClauseCount')}") final String maxBooleanClauseCount,
                                @Value("#{propertyConfigurer.getProperty('stroom.search.maxStoredDataQueueSize')}") final String maxStoredDataQueueSize,
                                final ElasticIndexService elasticIndexService,
                                final ElasticSearchTaskHandler elasticSearchTaskHandler
    ) {
        this.dictionaryStore = dictionaryStore;
        this.maxBooleanClauseCount = PropertyUtil.toInt(maxBooleanClauseCount, DEFAULT_MAX_BOOLEAN_CLAUSE_COUNT);
        this.maxStoredDataQueueSize = PropertyUtil.toInt(maxStoredDataQueueSize, DEFAULT_MAX_STORED_DATA_QUEUE_SIZE);
        this.elasticIndexService = elasticIndexService;
        this.elasticSearchTaskHandler = elasticSearchTaskHandler;
    }

    public void search(final ElasticClusterSearchTask task,
                       final ExpressionOperator expression,
                       final Receiver receiver,
                       final TaskContext taskContext,
                       final AtomicLong hitCount,
                       final HasTerminate hasTerminate) {
        // Reload the index.
        final ElasticIndex index = task.getElasticIndex();

        // Make sure we have a search index.
        if (index == null) {
            throw new SearchException("Search index has not been set");
        }

        // Create a map of index fields keyed by name.
        final Map<String, ElasticIndexField> indexFieldsMap = elasticIndexService.getFieldsMap(index);
        final QueryBuilder queryBuilder = getQuery(expression, indexFieldsMap, task.getDateTimeLocale(), task.getNow());

        final Tracker tracker = new Tracker(hitCount);
        final ElasticSearchTask elasticSearchTask = new ElasticSearchTask(index, queryBuilder, task.getStoredFields(), receiver, tracker);
        elasticSearchTaskHandler.exec(elasticSearchTask);

        // Wait until we finish.
        while (!hasTerminate.isTerminated() && !tracker.awaitCompletion(1, TimeUnit.SECONDS)) {
            taskContext.info("" +
                    "Searching... " +
                    "found " +
                    hitCount.get() +
                    " hits");
        }

        // Let the receiver know we are complete.
        receiver.getCompletionConsumer().accept(hitCount.get());
    }

    private QueryBuilder getQuery(final ExpressionOperator expression,
                                  final Map<String, ElasticIndexField> indexFieldsMap,
                                  final String timeZoneId,
                                  final long nowEpochMilli
    ) {
        final SearchExpressionQueryBuilder builder = new SearchExpressionQueryBuilder(dictionaryStore, indexFieldsMap, timeZoneId, nowEpochMilli);
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
