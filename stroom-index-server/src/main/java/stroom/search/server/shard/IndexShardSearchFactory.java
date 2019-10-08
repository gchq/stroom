package stroom.search.server.shard;

import org.apache.lucene.search.Query;
import org.apache.lucene.util.Version;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import stroom.dictionary.server.DictionaryStore;
import stroom.index.server.IndexService;
import stroom.index.shared.Index;
import stroom.index.shared.IndexFieldsMap;
import stroom.pipeline.server.errorhandler.MessageUtil;
import stroom.query.api.v2.ExpressionOperator;
import stroom.search.coprocessor.Error;
import stroom.search.coprocessor.Receiver;
import stroom.search.server.ClusterSearchTask;
import stroom.search.server.SearchException;
import stroom.search.server.SearchExpressionQueryBuilder;
import stroom.search.server.SearchExpressionQueryBuilder.SearchExpressionQuery;
import stroom.search.server.shard.IndexShardSearchTask.IndexShardQueryFactory;
import stroom.task.server.ExecutorProvider;
import stroom.task.server.TaskContext;
import stroom.task.server.TaskTerminatedException;
import stroom.util.config.PropertyUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.HasTerminate;
import stroom.util.spring.StroomScope;
import stroom.util.thread.ThreadUtil;

import javax.inject.Inject;
import javax.inject.Provider;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

@Component
@Scope(StroomScope.TASK)
public class IndexShardSearchFactory {
    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(IndexShardSearchFactory.class);

    /**
     * We don't want to collect more than 1 million doc's data into the queue by
     * default. When the queue is full the index shard data tasks will pause
     * until the docs are drained from the queue.
     */
    private static final int DEFAULT_MAX_BOOLEAN_CLAUSE_COUNT = 1024;

    private final IndexService indexService;
    private final IndexShardSearchTaskExecutor indexShardSearchTaskExecutor;
    private final IndexShardSearchTaskProperties indexShardSearchTaskProperties;
    private final Provider<IndexShardSearchTaskHandler> indexShardSearchTaskHandlerProvider;
    private final DictionaryStore dictionaryStore;
    private final ExecutorProvider executorProvider;
    private final int maxBooleanClauseCount;

    @Inject
    IndexShardSearchFactory(final IndexService indexService,
                            final IndexShardSearchTaskExecutor indexShardSearchTaskExecutor,
                            final IndexShardSearchTaskProperties indexShardSearchTaskProperties,
                            final Provider<IndexShardSearchTaskHandler> indexShardSearchTaskHandlerProvider,
                            final DictionaryStore dictionaryStore,
                            final ExecutorProvider executorProvider,
                            @Value("#{propertyConfigurer.getProperty('stroom.search.maxBooleanClauseCount')}") final String maxBooleanClauseCount) {
        this.indexService = indexService;
        this.indexShardSearchTaskExecutor = indexShardSearchTaskExecutor;
        this.indexShardSearchTaskProperties = indexShardSearchTaskProperties;
        this.indexShardSearchTaskHandlerProvider = indexShardSearchTaskHandlerProvider;
        this.dictionaryStore = dictionaryStore;
        this.executorProvider = executorProvider;
        this.maxBooleanClauseCount = PropertyUtil.toInt(maxBooleanClauseCount, DEFAULT_MAX_BOOLEAN_CLAUSE_COUNT);
    }

    public void search(final ClusterSearchTask task, final ExpressionOperator expression, final Receiver receiver, final TaskContext taskContext, final HasTerminate hasTerminate) {
        // Reload the index.
        final Index index = indexService.loadByUuid(task.getQuery().getDataSource().getUuid());

        // Make sure we have a search index.
        if (index == null) {
            throw new SearchException("Search index has not been set");
        }

        // Create a map of index fields keyed by name.
        final IndexFieldsMap indexFieldsMap = new IndexFieldsMap(index.getIndexFieldsObject());

        final Tracker tracker = new Tracker();

        if (task.getShards().size() > 0) {
            // Update config for the index shard search task executor.
            indexShardSearchTaskExecutor.setMaxThreads(indexShardSearchTaskProperties.getMaxThreads());

            final Map<Version, Optional<SearchExpressionQuery>> queryMap = new HashMap<>();
            final IndexShardQueryFactory queryFactory = createIndexShardQueryFactory(
                    task, expression, indexFieldsMap, queryMap, receiver.getErrorConsumer());

            final IndexShardSearchTaskProducer indexShardSearchTaskProducer = new IndexShardSearchTaskProducer(
                    indexShardSearchTaskExecutor,
                    receiver,
                    task.getShards(),
                    queryFactory,
                    task.getStoredFields(),
                    indexShardSearchTaskProperties.getMaxThreadsPerTask(),
                    executorProvider,
                    indexShardSearchTaskHandlerProvider,
                    tracker,
                    hasTerminate);

            indexShardSearchTaskProducer.process();

        } else {
            tracker.complete();
        }

        // Wait until we finish.
        while (!hasTerminate.isTerminated() && (!tracker.isCompleted())) {
            taskContext.info(
                    "Searching... " +
                            "found " + tracker.getHitCount() + " hits");
            ThreadUtil.sleep(1000);
        }
    }

    private IndexShardQueryFactory createIndexShardQueryFactory(final ClusterSearchTask task,
                                                                final ExpressionOperator expression,
                                                                final IndexFieldsMap indexFieldsMap,
                                                                final Map<Version, Optional<SearchExpressionQuery>> queryMap,
                                                                final Consumer<Error> errorConsumer) {
        return new IndexShardQueryFactory() {
            @Override
            public Query getQuery(final Version luceneVersion) {
                final Optional<SearchExpressionQuery> optional = queryMap.computeIfAbsent(luceneVersion, k -> {
                    // Get a query for the required lucene version.
                    return getQuery(k, expression, indexFieldsMap);
                });
                return optional.map(SearchExpressionQuery::getQuery).orElse(null);
            }

            private Optional<SearchExpressionQuery> getQuery(final Version version, final ExpressionOperator expression,
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
                        LOGGER.debug(() -> "Lucene Query is " + query.toString());
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
