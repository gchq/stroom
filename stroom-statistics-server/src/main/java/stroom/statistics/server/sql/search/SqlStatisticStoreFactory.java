package stroom.statistics.server.sql.search;

import com.google.common.base.Preconditions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import stroom.node.server.StroomPropertyService;
import stroom.node.shared.ClientProperties;
import stroom.query.api.v2.DocRef;
import stroom.query.api.v2.SearchRequest;
import stroom.query.common.v2.Sizes;
import stroom.query.common.v2.Store;
import stroom.query.common.v2.StoreFactory;
import stroom.statistics.server.sql.datasource.StatisticStoreCache;
import stroom.statistics.shared.StatisticStoreEntity;
import stroom.task.server.TaskContext;
import stroom.util.concurrent.ExecutorProvider;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.HasTerminate;
import stroom.util.task.TaskWrapper;

import javax.inject.Inject;
import javax.inject.Provider;
import java.util.Arrays;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

@SuppressWarnings("unused")
@Component("sqlStatisticStoreFactory")
public class SqlStatisticStoreFactory implements StoreFactory {

    private static final Logger LOGGER = LoggerFactory.getLogger(SqlStatisticStoreFactory.class);
    private static final LambdaLogger LAMBDA_LOGGER = LambdaLoggerFactory.getLogger(SqlStatisticStoreFactory.class);

    private static final String PROP_KEY_STORE_SIZE = "stroom.search.storeSize";
    private static final String PROP_KEY_RESULT_HANDLER_BATCH_SIZE = "stroom.statistics.sql.search.resultHandlerBatchSize";
    private static final int DEFAULT_ROWS_IN_BATCH = 5_000;

    private final StatisticStoreCache statisticStoreCache;
    private final StroomPropertyService stroomPropertyService;
    private final StatisticsSearchService statisticsSearchService;
    private final TaskContext taskContext;
    private final Provider<TaskWrapper> taskWrapperProvider;
    private final ExecutorProvider executorProvider;

    @Inject
    public SqlStatisticStoreFactory(final StatisticStoreCache statisticStoreCache,
                                    final StroomPropertyService stroomPropertyService,
                                    final StatisticsSearchService statisticsSearchService,
                                    final TaskContext taskContext,
                                    final Provider<TaskWrapper> taskWrapperProvider,
                                    final ExecutorProvider executorProvider) {
        this.statisticStoreCache = statisticStoreCache;
        this.stroomPropertyService = stroomPropertyService;
        this.statisticsSearchService = statisticsSearchService;
        this.taskContext = taskContext;
        this.taskWrapperProvider = taskWrapperProvider;
        this.executorProvider = executorProvider;
    }

    @Override
    public Store create(final SearchRequest searchRequest) {
        LOGGER.debug("create called for searchRequest {} ", searchRequest);

        final DocRef docRef = Preconditions.checkNotNull(
                Preconditions.checkNotNull(
                        Preconditions.checkNotNull(searchRequest)
                                .getQuery())
                        .getDataSource());
        Preconditions.checkNotNull(searchRequest.getResultRequests(), "searchRequest must have at least one resultRequest");
        Preconditions.checkArgument(!searchRequest.getResultRequests().isEmpty(), "searchRequest must have at least one resultRequest");

        final StatisticStoreEntity statisticStoreEntity = statisticStoreCache.getStatisticsDataSource(docRef);

        Preconditions.checkNotNull(statisticStoreEntity, "Statistic configuration could not be found for uuid "
                + docRef.getUuid());

        final Store store = buildStore(searchRequest, statisticStoreEntity);
        return store;
    }

    private Store buildStore(final SearchRequest searchRequest,
                             final StatisticStoreEntity statisticStoreEntity) {

        Preconditions.checkNotNull(searchRequest);
        Preconditions.checkNotNull(statisticStoreEntity);

        final Sizes storeSize = getStoreSizes();
        final Sizes defaultMaxResultsSizes = getDefaultMaxResultsSizes();
        final int resultHandlerBatchSize = getResultHandlerBatchSize();

        // TODO do we want to limit this with a thread pool?
        final Executor executor = executorProvider.getExecutor();

        // We have to create a wrapped executor so that the task context references a managed task.
        final Executor wrappedExecutor = command -> {
            final Runnable wrappedRunnable = taskWrapperProvider.get().wrap(command);
            executor.execute(wrappedRunnable);
        };

        //wrap the resultHandler in a new store, initiating the search in the process
        final SqlStatisticsStore store = new SqlStatisticsStore(
                searchRequest,
                statisticStoreEntity,
                statisticsSearchService,
                defaultMaxResultsSizes,
                storeSize,
                resultHandlerBatchSize,
                wrappedExecutor,
                taskContext);

        return store;
    }


    private HasTerminate getTaskMonitor() {

        return new HasTerminate() {

            private final AtomicBoolean isTerminated = new AtomicBoolean(false);

            @Override
            public void terminate() {
                isTerminated.set(true);
            }

            @Override
            public boolean isTerminated() {
                return isTerminated.get();
            }
        };
    }

    private Sizes getDefaultMaxResultsSizes() {
        final String value = stroomPropertyService.getProperty(ClientProperties.DEFAULT_MAX_RESULTS);
        return extractValues(value);
    }

    private Sizes getStoreSizes() {
        final String value = stroomPropertyService.getProperty(PROP_KEY_STORE_SIZE);
        return extractValues(value);
    }

    private int getResultHandlerBatchSize() {
        return stroomPropertyService.getIntProperty(PROP_KEY_RESULT_HANDLER_BATCH_SIZE, DEFAULT_ROWS_IN_BATCH);
    }

    private Sizes extractValues(String value) {
        if (value != null) {
            try {
                return Sizes.create(Arrays.stream(value.split(","))
                        .map(String::trim)
                        .map(Integer::valueOf)
                        .collect(Collectors.toList()));
            } catch (Exception e) {
                LOGGER.warn(e.getMessage());
            }
        }
        return Sizes.create(Integer.MAX_VALUE);
    }
}
