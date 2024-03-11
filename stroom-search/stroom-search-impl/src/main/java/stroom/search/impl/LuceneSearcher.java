package stroom.search.impl;

import stroom.index.impl.IndexShardSearchConfig;
import stroom.index.impl.IndexShardService;
import stroom.index.impl.IndexStore;
import stroom.index.impl.LuceneProviderFactory;
import stroom.index.impl.LuceneShardSearcher;
import stroom.index.shared.LuceneIndexDoc;
import stroom.index.shared.LuceneIndexField;
import stroom.index.shared.IndexFieldsMap;
import stroom.index.shared.IndexShard;
import stroom.index.shared.LuceneVersion;
import stroom.index.shared.LuceneVersionUtil;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.common.v2.SearchProgressLog;
import stroom.query.common.v2.SearchProgressLog.SearchPhase;
import stroom.query.language.functions.FieldIndex;
import stroom.query.language.functions.ref.ErrorConsumer;
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

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;

public class LuceneSearcher {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(LuceneSearcher.class);

    public static final ThreadPool INDEX_SHARD_SEARCH_THREAD_POOL = new ThreadPoolImpl("Search Index Shard");

    private final IndexStore indexStore;
    private final ExecutorProvider executorProvider;
    private final IndexShardSearchConfig indexShardSearchConfig;
    private final IndexShardService indexShardService;
    private final LuceneProviderFactory luceneProviderFactory;
    private final TaskContextFactory taskContextFactory;


    private final Map<LuceneVersion, LuceneShardSearcher> searcherMap = new ConcurrentHashMap<>();

    @Inject
    LuceneSearcher(final IndexStore indexStore,
                   final ExecutorProvider executorProvider,
                   final IndexShardSearchConfig indexShardSearchConfig,
                   final IndexShardService indexShardService,
                   final LuceneProviderFactory luceneProviderFactory,
                   final TaskContextFactory taskContextFactory) {
        this.indexStore = indexStore;
        this.executorProvider = executorProvider;
        this.indexShardSearchConfig = indexShardSearchConfig;
        this.indexShardService = indexShardService;
        this.luceneProviderFactory = luceneProviderFactory;
        this.taskContextFactory = taskContextFactory;
    }

    @SuppressWarnings("unchecked")
    public CompletableFuture<Void> search(final NodeSearchTask task,
                                          final ExpressionOperator expression,
                                          final FieldIndex fieldIndex,
                                          final TaskContext parentContext,
                                          final LongAdder hitCount,
                                          final StoredDataQueue storedDataQueue,
                                          final ErrorConsumer errorConsumer) {
        SearchProgressLog.increment(task.getKey(), SearchPhase.INDEX_SHARD_SEARCH_FACTORY_SEARCH);

        // Reload the index.
        final LuceneIndexDoc index = indexStore.readDocument(task.getQuery().getDataSource());

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
                final LuceneIndexField indexField = indexFieldsMap.get(fieldName);
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
//            final IndexShardQueryFactory queryFactory = createIndexShardQueryFactory(
//                    task, expression, indexFieldsMap, errorConsumer);

            // Create a queue of shards to search.
            final ShardIdQueue shardIdQueue = new ShardIdQueue(task.getShards());
            final AtomicInteger shardNo = new AtomicInteger();
            for (int i = 0; i < threadCount; i++) {
                futures[i] = CompletableFuture.runAsync(() -> taskContextFactory
                        .childContext(parentContext,
                                "Search Index Shard",
                                TerminateHandlerFactory.NOOP_FACTORY,
                                taskContext -> {
                                    boolean complete = false;
                                    while (!complete && !taskContext.isTerminated()) {
                                        try {
                                            taskContext.reset();
                                            taskContext.info(() -> "Waiting for index shard...");
                                            final Long shardId = shardIdQueue.next();
                                            if (shardId != null) {
                                                final IndexShard indexShard = indexShardService.loadById(shardId);
                                                if (indexShard == null) {
                                                    throw new SearchException("Unable to find index shard with id = " +
                                                            shardId);
                                                }

                                                final LuceneVersion luceneVersion = LuceneVersionUtil
                                                        .getLuceneVersion(indexShard.getIndexVersion());
                                                final LuceneShardSearcher luceneShardSearcher = searcherMap
                                                        .computeIfAbsent(luceneVersion, k ->
                                                                luceneProviderFactory.get(k)
                                                                        .createLuceneShardSearcher(
                                                                                expression,
                                                                                indexFieldsMap,
                                                                                task.getDateTimeSettings(),
                                                                                task.getKey()));

                                                luceneShardSearcher.searchShard(
                                                        taskContext,
                                                        indexShard,
                                                        storedFieldNames,
                                                        hitCount,
                                                        shardNo.incrementAndGet(),
                                                        task.getShards().size(),
                                                        storedDataQueue,
                                                        errorConsumer);
                                            } else {
                                                complete = true;
                                            }
                                        } catch (final Exception e) {
                                            LOGGER.error(e::getMessage, e);
                                            throw new RuntimeException(e);
                                        }
                                    }
                                }).run(), executor);
            }
        }

        // When we complete the index shard search tell the stored data queue we are complete.
        return CompletableFuture.allOf(futures).whenCompleteAsync((r, t) ->
                taskContextFactory.childContext(parentContext,
                        "Search Index Shard",
                        TerminateHandlerFactory.NOOP_FACTORY,
                        taskContext -> {
                            taskContext.info(() -> "Complete stored data queue");
                            LOGGER.debug("Complete stored data queue");
                            storedDataQueue.complete();
                        }).run(), executor);
    }
}
