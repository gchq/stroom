package stroom.search.impl.shard;

import stroom.dashboard.expression.v1.FieldIndex;
import stroom.dictionary.api.WordListProvider;
import stroom.index.impl.IndexStore;
import stroom.index.shared.IndexDoc;
import stroom.index.shared.IndexField;
import stroom.index.shared.IndexFieldsMap;
import stroom.pipeline.errorhandler.MessageUtil;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.common.v2.Receiver;
import stroom.search.impl.ClusterSearchTask;
import stroom.search.impl.SearchConfig;
import stroom.search.impl.SearchException;
import stroom.search.impl.SearchExpressionQueryBuilder;
import stroom.search.impl.SearchExpressionQueryBuilder.SearchExpressionQuery;
import stroom.search.impl.shard.IndexShardSearchTask.IndexShardQueryFactory;
import stroom.task.api.ExecutorProvider;
import stroom.task.api.TaskContext;
import stroom.task.api.TaskContextFactory;
import stroom.task.api.TaskTerminatedException;
import stroom.task.api.ThreadPoolImpl;
import stroom.task.shared.ThreadPool;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.SearchProgressLog;
import stroom.util.logging.SearchProgressLog.SearchPhase;

import org.apache.lucene.search.Query;
import org.apache.lucene.util.Version;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
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

    public void search(final ClusterSearchTask task,
                       final ExpressionOperator expression,
                       final FieldIndex fieldIndex,
                       final Receiver receiver,
                       final TaskContext taskContext,
                       final AtomicLong hitCount) {
        SearchProgressLog.increment(SearchPhase.INDEX_SHARD_SEARCH_FACTORY_SEARCH);

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

        final IndexShardSearchProgressTracker tracker = new IndexShardSearchProgressTracker(
                hitCount,
                task.getShards().size());
        if (task.getShards().size() > 0) {
            final Map<Version, Optional<SearchExpressionQuery>> queryMap = new HashMap<>();
            final IndexShardQueryFactory queryFactory = createIndexShardQueryFactory(
                    task, expression, indexFieldsMap, queryMap, receiver.getErrorConsumer());

            final Executor executor = executorProvider.get(INDEX_SHARD_SEARCH_THREAD_POOL);
            final LinkedBlockingQueue<Optional<Long>> queue = new LinkedBlockingQueue<>();
            for (final Long shard : task.getShards()) {
                queue.add(Optional.of(shard));
            }

            final AtomicInteger shardNo = new AtomicInteger();
            for (int i = 0; i < indexShardSearchConfig.getMaxThreadsPerTask(); i++) {
                queue.add(Optional.empty());

                final Runnable runnable = taskContextFactory
                        .childContext(taskContext, "Search Index Shard", tc -> {
                            try {
                                boolean complete = false;
                                while (!complete) {
                                    final Optional<Long> optional = queue.take();
                                    if (optional.isEmpty()) {
                                        complete = true;

                                    } else {
                                        final long shard = optional.get();
                                        final IndexShardSearchTask t = new IndexShardSearchTask(queryFactory,
                                                shard,
                                                storedFieldNames,
                                                receiver,
                                                tracker.getHitCount());
                                        try {
                                            t.setShardTotal(tracker.getShardTotal());
                                            t.setShardNumber(shardNo.incrementAndGet());
                                            final IndexShardSearchTaskHandler handler =
                                                    indexShardSearchTaskHandlerProvider.get();
                                            handler.exec(tc, t);
                                        } finally {
                                            tracker.incrementCompleteShardCount();
                                        }
                                    }
                                }

                            } catch (final InterruptedException e) {
                                LOGGER.debug(e::getMessage, e);
                                Thread.currentThread().interrupt();
                            }
                        });

                CompletableFuture.runAsync(runnable, executor);
            }
        }

        // Wait until we finish.
        try {
            while (!tracker.awaitCompletion(1, TimeUnit.SECONDS)) {
                taskContext.info(() -> "" +
                        "Searching... " +
                        "found "
                        + hitCount.get() +
                        " hits");
                LOGGER.debug(tracker::toString);
            }
        } catch (final InterruptedException e) {
            LOGGER.debug(this::toString);
            // Keep interrupting.
            Thread.currentThread().interrupt();
        } finally {
            LOGGER.debug(() -> "Complete - " + tracker);
        }

        // Let the receiver know we are complete.
        receiver.getCompletionConsumer().accept(hitCount.get());
    }

    private IndexShardQueryFactory createIndexShardQueryFactory(
            final ClusterSearchTask task,
            final ExpressionOperator expression,
            final IndexFieldsMap indexFieldsMap,
            final Map<Version, Optional<SearchExpressionQuery>> queryMap,
            final Consumer<Throwable> errorConsumer) {

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
                            task.getDateTimeLocale(),
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
                    errorConsumer.accept(new Error(MessageUtil.getMessage(e.getMessage(), e), e));
                }

                return Optional.empty();
            }
        };
    }
}
