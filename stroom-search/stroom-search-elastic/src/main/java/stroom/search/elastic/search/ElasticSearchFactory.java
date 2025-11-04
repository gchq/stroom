package stroom.search.elastic.search;

import stroom.dictionary.api.WordListProvider;
import stroom.langchain.api.OpenAIService;
import stroom.query.api.DateTimeSettings;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.Query;
import stroom.query.api.QueryKey;
import stroom.query.common.v2.Coprocessors;
import stroom.query.common.v2.IndexFieldCache;
import stroom.query.common.v2.ResultStore;
import stroom.query.common.v2.SearchProgressLog;
import stroom.query.common.v2.SearchProgressLog.SearchPhase;
import stroom.search.elastic.ElasticIndexCache;
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

import co.elastic.clients.elasticsearch.core.search.BoundaryScanner;
import co.elastic.clients.elasticsearch.core.search.Highlight;
import co.elastic.clients.elasticsearch.core.search.HighlightField;
import co.elastic.clients.util.NamedValue;
import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicLong;

public class ElasticSearchFactory {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ElasticSearchFactory.class);
    private static final ThreadPool THREAD_POOL = new ThreadPoolImpl("Search Elasticsearch Cluster");

    private final WordListProvider wordListProvider;
    private final ElasticSearchTaskHandler elasticSearchTaskHandler;
    private final ElasticIndexCache elasticIndexCache;
    private final IndexFieldCache indexFieldCache;
    private final Provider<OpenAIService> openAIServiceProvider;
    private final TaskContextFactory taskContextFactory;
    private final Executor executor;

    @Inject
    public ElasticSearchFactory(final WordListProvider wordListProvider,
                                final ElasticSearchTaskHandler elasticSearchTaskHandler,
                                final ElasticIndexCache elasticIndexCache,
                                final IndexFieldCache indexFieldCache,
                                final Provider<OpenAIService> openAIServiceProvider,
                                final TaskContextFactory taskContextFactory,
                                final ExecutorProvider executorProvider) {
        this.wordListProvider = wordListProvider;
        this.elasticSearchTaskHandler = elasticSearchTaskHandler;
        this.elasticIndexCache = elasticIndexCache;
        this.indexFieldCache = indexFieldCache;
        this.openAIServiceProvider = openAIServiceProvider;
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
                        getQuery(index, indexFieldCache, expression, dateTimeSettings),
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

    private ElasticQueryParams getQuery(final ElasticIndexDoc index,
                                        final IndexFieldCache indexFieldCache,
                                        final ExpressionOperator expression,
                                        final DateTimeSettings dateTimeSettings) {
        final SearchExpressionQueryBuilder builder = new SearchExpressionQueryBuilder(
                openAIServiceProvider,
                index,
                indexFieldCache,
                wordListProvider,
                dateTimeSettings);
        final ElasticQueryParams queryParams = builder.buildQuery(expression);
        LOGGER.debug(() -> "Query: " + queryParams.getQuery());
        return queryParams;
    }

    private Highlight getHighlighter() {
        return Highlight.of(h -> h
                .fields(NamedValue.of("*", HighlightField.of(f -> f
                        .preTags("")
                        .postTags("")
                        .boundaryScanner(BoundaryScanner.Word)
                )))
        );
    }
}
