package stroom.search.impl.shard;

import stroom.dashboard.expression.v1.FieldIndex;
import stroom.dictionary.api.WordListProvider;
import stroom.index.impl.IndexStore;
import stroom.index.shared.IndexDoc;
import stroom.index.shared.IndexField;
import stroom.index.shared.IndexFieldsMap;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.common.v2.ErrorConsumer;
import stroom.query.common.v2.SearchProgressLog;
import stroom.query.common.v2.SearchProgressLog.SearchPhase;
import stroom.search.extraction.StoredDataQueue;
import stroom.search.impl.ClusterSearchTask;
import stroom.search.impl.SearchConfig;
import stroom.search.impl.SearchException;
import stroom.search.impl.SearchExpressionQueryBuilder;
import stroom.search.impl.SearchExpressionQueryBuilder.SearchExpressionQuery;
import stroom.task.api.ExecutorProvider;
import stroom.task.api.TaskContext;
import stroom.task.api.TaskContextFactory;
import stroom.task.api.TaskTerminatedException;
import stroom.task.api.ThreadPoolImpl;
import stroom.task.shared.ThreadPool;
import stroom.util.concurrent.CompleteException;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import org.apache.lucene.search.Query;
import org.apache.lucene.util.Version;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import javax.inject.Inject;
import javax.inject.Provider;

public class IndexShardSearchFactory {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(IndexShardSearchFactory.class);

    public static final ThreadPool INDEX_SHARD_SEARCH_THREAD_POOL = new ThreadPoolImpl("Search Index Shard");

    private final IndexStore indexStore;
    private final ExecutorProvider executorProvider;
    private final IndexShardSearchConfig indexShardSearchConfig;
    private final Provider<IndexShardSearchTaskHandler> indexShardSearchTaskHandlerProvider;
    private final WordListProvider dictionaryStore;
    private final TaskContextFactory taskContextFactory;
    private final int maxBooleanClauseCount;

    @Inject
    IndexShardSearchFactory(final IndexStore indexStore,
                            final ExecutorProvider executorProvider,
                            final IndexShardSearchConfig indexShardSearchConfig,
                            final Provider<IndexShardSearchTaskHandler> indexShardSearchTaskHandlerProvider,
                            final WordListProvider dictionaryStore,
                            final TaskContextFactory taskContextFactory,
                            final SearchConfig searchConfig) {
        this.indexStore = indexStore;
        this.executorProvider = executorProvider;
        this.indexShardSearchConfig = indexShardSearchConfig;
        this.indexShardSearchTaskHandlerProvider = indexShardSearchTaskHandlerProvider;
        this.dictionaryStore = dictionaryStore;
        this.taskContextFactory = taskContextFactory;
        this.maxBooleanClauseCount = searchConfig.getMaxBooleanClauseCount();
    }

    @SuppressWarnings("unchecked")
    public CompletableFuture<Void> search(final ClusterSearchTask task,
                                          final ExpressionOperator expression,
                                          final FieldIndex fieldIndex,
                                          final TaskContext parentContext,
                                          final AtomicLong hitCount,
                                          final StoredDataQueue storedDataQueue,
                                          final ErrorConsumer errorConsumer) {
        SearchProgressLog.increment(task.getKey(), SearchPhase.INDEX_SHARD_SEARCH_FACTORY_SEARCH);

        // Reload the index.
        final IndexDoc index = indexStore.readDocument(task.getQuery().getDataSource());

        // Make sure we have a search index.
        if (index == null) {
            throw new SearchException("Search index has not been set");
        }

        // Create a map of index fields keyed by name.
        final IndexFieldsMap indexFieldsMap = new IndexFieldsMap(index.getFields());

        final String[] storedFieldNames = new String[fieldIndex.size()];
        for (int i = 0; i < storedFieldNames.length; i++) {
            final String fieldName = fieldIndex.getField(i);
            if (fieldName != null) {
                final IndexField indexField = indexFieldsMap.get(fieldName);
                if (indexField != null && indexField.isStored()) {
                    storedFieldNames[i] = fieldName;
                }
            }
        }

        // Get the stored fields that search is hoping to use.
        if (storedFieldNames.length == 0) {
            throw new SearchException("No stored fields have been requested");
        }

        final int threadCount = indexShardSearchConfig.getMaxThreadsPerTask();
        final CompletableFuture<Void>[] futures = new CompletableFuture[threadCount];
        final Executor executor = executorProvider.get(INDEX_SHARD_SEARCH_THREAD_POOL);

        if (task.getShards().size() > 0) {
            try {
                final Map<Version, Optional<SearchExpressionQuery>> queryMap = new ConcurrentHashMap<>();
                final IndexShardQueryFactory queryFactory = createIndexShardQueryFactory(
                        task, expression, indexFieldsMap, queryMap, errorConsumer);

                // Create a queue of shards to search.
                final ShardIdQueue queue = new ShardIdQueue(task.getShards());
                final AtomicInteger shardNo = new AtomicInteger();
                for (int i = 0; i < threadCount; i++) {
                    futures[i] = CompletableFuture.runAsync(() -> taskContextFactory
                            .childContext(parentContext, "Search Index Shard", taskContext -> {
                                try {
                                    while (true) {
                                        taskContext.reset();
                                        taskContext.info(() -> "Waiting for index shard...");
                                        final long shardId = queue.take();
                                        final IndexShardSearchTaskHandler handler =
                                                indexShardSearchTaskHandlerProvider.get();
                                        handler.searchShard(
                                                taskContext,
                                                task.getKey(),
                                                queryFactory,
                                                storedFieldNames,
                                                hitCount,
                                                shardNo.incrementAndGet(),
                                                task.getShards().size(),
                                                shardId,
                                                storedDataQueue,
                                                errorConsumer);
                                    }
                                } catch (final CompleteException e) {
                                    LOGGER.trace(() -> "Complete");
                                } catch (final InterruptedException e) {
                                    LOGGER.trace(e::getMessage, e);
                                    // Keep interrupting this thread.
                                    Thread.currentThread().interrupt();
                                }
                            }).run(), executor);
                }
            } catch (final InterruptedException e) {
                LOGGER.trace(e::getMessage, e);
                // Keep interrupting this thread.
                Thread.currentThread().interrupt();
            }
        }

        // When we complete the index shard search tell the stored data queue we are complete.
        return CompletableFuture.allOf(futures).whenCompleteAsync((r, t) ->
                taskContextFactory.childContext(parentContext, "Search Index Shard", taskContext -> {
                    taskContext.info(() -> "Complete stored data queue");
                    LOGGER.debug("Complete stored data queue");
                    storedDataQueue.complete();
                }).run(), executor);
    }

    private IndexShardQueryFactory createIndexShardQueryFactory(
            final ClusterSearchTask task,
            final ExpressionOperator expression,
            final IndexFieldsMap indexFieldsMap,
            final Map<Version, Optional<SearchExpressionQuery>> queryMap,
            final ErrorConsumer errorConsumer) {

        return new IndexShardQueryFactory() {
            @Override
            public Query getQuery(final Version luceneVersion) {
                final Optional<SearchExpressionQuery> optional = queryMap.computeIfAbsent(luceneVersion, k -> {
                    // Get a query for the required lucene version.
                    return getQuery(k, expression, indexFieldsMap);
                });
                return optional.map(SearchExpressionQuery::getQuery).orElse(null);
            }

            private Optional<SearchExpressionQuery> getQuery(final Version version,
                                                             final ExpressionOperator expression,
                                                             final IndexFieldsMap indexFieldsMap) {
                try {
                    final SearchExpressionQueryBuilder searchExpressionQueryBuilder = new SearchExpressionQueryBuilder(
                            dictionaryStore,
                            indexFieldsMap,
                            maxBooleanClauseCount,
                            task.getDateTimeSettings(),
                            task.getNow());
                    final SearchExpressionQuery query = searchExpressionQueryBuilder.buildQuery(version, expression);

                    // Make sure the query was created successfully.
                    if (query.getQuery() == null) {
                        throw new SearchException("Failed to build Lucene query given expression");
                    } else {
                        LOGGER.debug(() -> "Lucene Query is " + query);
                    }

                    return Optional.of(query);
                } catch (final TaskTerminatedException e) {
                    LOGGER.debug(e::getMessage, e);
                } catch (final RuntimeException e) {
                    LOGGER.debug(e::getMessage, e);
                    errorConsumer.add(e);
                }

                return Optional.empty();
            }
        };
    }
}
