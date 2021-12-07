package stroom.search.elastic.search;

import stroom.dashboard.expression.v1.FieldIndex;
import stroom.dashboard.expression.v1.ValuesConsumer;
import stroom.dictionary.api.WordListProvider;
import stroom.query.api.v2.DateTimeSettings;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.common.v2.ErrorConsumer;
import stroom.search.elastic.ElasticIndexService;
import stroom.search.elastic.shared.ElasticIndexDoc;
import stroom.search.elastic.shared.ElasticIndexField;
import stroom.task.api.ExecutorProvider;
import stroom.task.api.TaskContext;
import stroom.task.api.ThreadPoolImpl;
import stroom.task.shared.ThreadPool;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import org.elasticsearch.index.query.QueryBuilder;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicLong;
import javax.inject.Inject;

public class ElasticSearchFactory {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ElasticSearchFactory.class);

    private static final ThreadPool THREAD_POOL = new ThreadPoolImpl(
            "Search Elastic Index");

    private final WordListProvider wordListProvider;
    private final ElasticSearchTaskHandler elasticSearchTaskHandler;
    private final ElasticIndexService elasticIndexService;
    private final Executor executor;

    @Inject
    public ElasticSearchFactory(final WordListProvider wordListProvider,
                                final ElasticSearchTaskHandler elasticSearchTaskHandler,
                                final ElasticIndexService elasticIndexService,
                                final ExecutorProvider executorProvider) {
        this.wordListProvider = wordListProvider;
        this.elasticSearchTaskHandler = elasticSearchTaskHandler;
        this.elasticIndexService = elasticIndexService;
        this.executor = executorProvider.get(THREAD_POOL);
    }

    public CompletableFuture<Void> search(final ElasticAsyncSearchTask asyncSearchTask,
                                          final ElasticIndexDoc index,
                                          final FieldIndex fieldIndex,
                                          final long now,
                                          final ExpressionOperator expression,
                                          final ValuesConsumer valuesConsumer,
                                          final ErrorConsumer errorConsumer,
                                          final TaskContext parentContext,
                                          final AtomicLong hitCount,
                                          final DateTimeSettings dateTimeSettings) {
        // Make sure we have a search index.
        if (index == null) {
            throw new SearchException("Search index has not been set");
        }

        // Create a map of index fields keyed by name.
        final Map<String, ElasticIndexField> indexFieldsMap = elasticIndexService.getFieldsMap(index);
        final QueryBuilder queryBuilder = getQuery(expression, indexFieldsMap, dateTimeSettings, now);
        final Tracker tracker = new Tracker(hitCount);
        final ElasticSearchTask elasticSearchTask = new ElasticSearchTask(
                asyncSearchTask, index, queryBuilder, fieldIndex, valuesConsumer, errorConsumer, tracker,
                asyncSearchTask.getResultCollector());

        return CompletableFuture.runAsync(elasticSearchTaskHandler.exec(parentContext, elasticSearchTask), executor);
    }

    private QueryBuilder getQuery(final ExpressionOperator expression,
                                  final Map<String, ElasticIndexField> indexFieldsMap,
                                  final DateTimeSettings dateTimeSettings,
                                  final long nowEpochMilli
    ) {
        final SearchExpressionQueryBuilder builder = new SearchExpressionQueryBuilder(
                wordListProvider,
                indexFieldsMap,
                dateTimeSettings,
                nowEpochMilli);

        final QueryBuilder query = builder.buildQuery(expression);

        // Make sure the query was created successfully.
        if (query == null) {
            throw new SearchException("Failed to build query given expression");
        } else {
            LOGGER.debug(() -> "Query is " + query);
        }

        return query;
    }
}
