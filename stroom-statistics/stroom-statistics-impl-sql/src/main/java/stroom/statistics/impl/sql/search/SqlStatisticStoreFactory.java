package stroom.statistics.impl.sql.search;

import com.google.common.base.Preconditions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.docref.DocRef;
import stroom.query.api.v2.SearchRequest;
import stroom.query.common.v2.Sizes;
import stroom.query.common.v2.Store;
import stroom.query.common.v2.StoreFactory;
import stroom.statistics.impl.sql.entity.StatisticStoreCache;
import stroom.statistics.impl.sql.shared.StatisticStoreDoc;
import stroom.task.api.ExecutorProvider;
import stroom.task.api.TaskContext;
import stroom.ui.config.shared.UiConfig;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import javax.inject.Inject;
import java.util.Arrays;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

@SuppressWarnings("unused")
public class SqlStatisticStoreFactory implements StoreFactory {

    private static final Logger LOGGER = LoggerFactory.getLogger(SqlStatisticStoreFactory.class);
    private static final LambdaLogger LAMBDA_LOGGER = LambdaLoggerFactory.getLogger(SqlStatisticStoreFactory.class);

    private final StatisticStoreCache statisticStoreCache;
    private final StatisticsSearchService statisticsSearchService;
    private final TaskContext taskContext;
    private final SearchConfig searchConfig;
    private final UiConfig clientConfig;
    private final ExecutorProvider executorProvider;

    @Inject
    public SqlStatisticStoreFactory(final StatisticStoreCache statisticStoreCache,
                                    final StatisticsSearchService statisticsSearchService,
                                    final TaskContext taskContext,
                                    final ExecutorProvider executorProvider,
                                    final SearchConfig searchConfig,
                                    final UiConfig clientConfig) {
        this.statisticStoreCache = statisticStoreCache;
        this.statisticsSearchService = statisticsSearchService;
        this.taskContext = taskContext;
        this.searchConfig = searchConfig;
        this.clientConfig = clientConfig;
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

        final StatisticStoreDoc statisticStoreDoc = statisticStoreCache.getStatisticsDataSource(docRef);

        Preconditions.checkNotNull(statisticStoreDoc, "Statistic configuration could not be found for uuid "
                + docRef.getUuid());

        return buildStore(searchRequest, statisticStoreDoc);
    }

    private Store buildStore(final SearchRequest searchRequest,
                             final StatisticStoreDoc statisticStoreDoc) {

        Preconditions.checkNotNull(searchRequest);
        Preconditions.checkNotNull(statisticStoreDoc);

        final Sizes storeSize = getStoreSizes();
        final Sizes defaultMaxResultsSizes = getDefaultMaxResultsSizes();
        final int resultHandlerBatchSize = getResultHandlerBatchSize();

        // TODO do we want to limit this with a thread pool?
        final Executor executor = executorProvider.getExecutor();

        //wrap the resultHandler in a new store, initiating the search in the process
        return new SqlStatisticsStore(
                searchRequest,
                statisticStoreDoc,
                statisticsSearchService,
                defaultMaxResultsSizes,
                storeSize,
                resultHandlerBatchSize,
                executor,
                taskContext);
    }

    private Sizes getDefaultMaxResultsSizes() {
        final String value = clientConfig.getDefaultMaxResults();
        return extractValues(value);
    }

    private Sizes getStoreSizes() {
        final String value = searchConfig.getStoreSize();
        return extractValues(value);
    }

    private int getResultHandlerBatchSize() {
        return searchConfig.getResultHandlerBatchSize();
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
