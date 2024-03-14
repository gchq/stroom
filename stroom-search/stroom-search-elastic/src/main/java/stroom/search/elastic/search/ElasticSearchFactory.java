package stroom.search.elastic.search;

import stroom.dictionary.api.WordListProvider;
import stroom.docref.DocRef;
import stroom.expression.api.DateTimeSettings;
import stroom.index.shared.IndexFieldCache;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.Query;
import stroom.query.api.v2.QueryKey;
import stroom.query.common.v2.Coprocessors;
import stroom.query.common.v2.ResultStore;
import stroom.query.common.v2.SearchProgressLog;
import stroom.query.common.v2.SearchProgressLog.SearchPhase;
import stroom.search.elastic.ElasticIndexCache;
import stroom.search.elastic.ElasticIndexService;
import stroom.search.elastic.shared.ElasticIndexDoc;
import stroom.search.extraction.StoredDataQueue;
import stroom.task.api.ExecutorProvider;
import stroom.task.api.TaskContext;
import stroom.task.api.TaskContextFactory;
import stroom.task.api.TerminateHandlerFactory;
import stroom.task.api.ThreadPoolImpl;
import stroom.task.shared.ThreadPool;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import jakarta.inject.Inject;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.search.fetch.subphase.highlight.HighlightBuilder;
import org.elasticsearch.search.fetch.subphase.highlight.HighlightBuilder.BoundaryScannerType;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicLong;

public class ElasticSearchFactory {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ElasticSearchFactory.class);
    private static final ThreadPool THREAD_POOL = new ThreadPoolImpl("Search Elasticsearch Cluster");

    private final WordListProvider wordListProvider;
    private final ElasticSearchTaskHandler elasticSearchTaskHandler;
    private final ElasticIndexService elasticIndexService;
    private final ElasticIndexCache elasticIndexCache;
    private final IndexFieldCache indexFieldCache;
    private final TaskContextFactory taskContextFactory;
    private final Executor executor;

    @Inject
    public ElasticSearchFactory(final WordListProvider wordListProvider,
                                final ElasticSearchTaskHandler elasticSearchTaskHandler,
                                final ElasticIndexService elasticIndexService,
                                final ElasticIndexCache elasticIndexCache,
                                final IndexFieldCache indexFieldCache,
                                final TaskContextFactory taskContextFactory,
                                final ExecutorProvider executorProvider) {
        this.wordListProvider = wordListProvider;
        this.elasticSearchTaskHandler = elasticSearchTaskHandler;
        this.elasticIndexService = elasticIndexService;
        this.elasticIndexCache = elasticIndexCache;
        this.indexFieldCache = indexFieldCache;
        this.taskContextFactory = taskContextFactory;
        this.executor = executorProvider.get(THREAD_POOL);
    }

    public CompletableFuture<Void> search(final QueryKey queryKey,
                                          final Query query,
                                          final DateTimeSettings dateTimeSettings,
                                          final ExpressionOperator expression,
                                          final Coprocessors coprocessors,
                                          final ResultStore resultStore,
                                          final TaskContext parentContext,
                                          final AtomicLong hitCount,
                                          final StoredDataQueue storedDataQueue) {
        SearchProgressLog.increment(queryKey, SearchPhase.INDEX_SHARD_SEARCH_FACTORY_SEARCH);

        // Reload the index.
        final ElasticIndexDoc index = elasticIndexCache.get(query.getDataSource());

        // Make sure we have a search index.
        if (index == null) {
            throw new SearchException("Search index has not been set");
        }

        final Runnable runnable = taskContextFactory.childContext(
                parentContext,
                "Search Elasticsearch Index",
                TerminateHandlerFactory.NOOP_FACTORY,
                taskContext -> elasticSearchTaskHandler.search(
                        taskContext,
                        index,
                        getQuery(query.getDataSource(), indexFieldCache, expression, dateTimeSettings),
                        getHighlighter(),
                        coprocessors,
                        resultStore,
                        storedDataQueue,
                        coprocessors.getErrorConsumer(),
                        hitCount));

        return CompletableFuture
                .runAsync(runnable, executor)
                .exceptionally(e -> {
                    resultStore.onFailure(resultStore.getNodeName(), e);
                    return null;
                })
                .whenCompleteAsync((r, t) -> taskContextFactory.childContext(parentContext,
                        "Search Elasticsearch Index",
                        TerminateHandlerFactory.NOOP_FACTORY,
                        taskContext -> {
                            taskContext.info(() -> "Complete stored data queue");
                            LOGGER.debug("Complete stored data queue");
                            storedDataQueue.complete();
                        }).run(), executor);
    }

    private QueryBuilder getQuery(final DocRef indexDocRef,
                                  final IndexFieldCache indexFieldCache,
                                  final ExpressionOperator expression,
                                  final DateTimeSettings dateTimeSettings) {
        final SearchExpressionQueryBuilder builder = new SearchExpressionQueryBuilder(
                indexDocRef,
                indexFieldCache,
                wordListProvider,
                dateTimeSettings);
        final QueryBuilder query = builder.buildQuery(expression);

        // Make sure the query was created successfully.
        if (query == null) {
            throw new SearchException("Failed to build query given expression");
        } else {
            LOGGER.debug(() -> "Query is " + query);
        }

        return query;
    }

    private HighlightBuilder getHighlighter() {
        return new HighlightBuilder()
                .field("*")
                .preTags("")
                .postTags("")
                .boundaryScannerType(BoundaryScannerType.WORD);
    }
}
