package stroom.statistics.sql.search;

import com.google.common.base.Preconditions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.docref.DocRef;
import stroom.properties.shared.ClientProperties;
import stroom.properties.api.PropertyService;
import stroom.query.api.v2.SearchRequest;
import stroom.query.common.v2.Store;
import stroom.query.common.v2.StoreFactory;
import stroom.query.common.v2.StoreSize;
import stroom.statistics.shared.StatisticStoreDoc;
import stroom.statistics.sql.entity.StatisticStoreCache;
import stroom.task.ExecutorProvider;
import stroom.task.api.TaskContext;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import javax.inject.Inject;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

@SuppressWarnings("unused")
public class SqlStatisticStoreFactory implements StoreFactory {

    private static final Logger LOGGER = LoggerFactory.getLogger(SqlStatisticStoreFactory.class);
    private static final LambdaLogger LAMBDA_LOGGER = LambdaLoggerFactory.getLogger(SqlStatisticStoreFactory.class);

    private static final String PROP_KEY_STORE_SIZE = "stroom.search.storeSize";
    private static final String PROP_KEY_RESULT_HANDLER_BATCH_SIZE = "stroom.statistics.sql.search.resultHandlerBatchSize";
    private static final int DEFAULT_ROWS_IN_BATCH = 5_000;

    private final StatisticStoreCache statisticStoreCache;
    private final PropertyService propertyService;
    private final StatisticsSearchService statisticsSearchService;
    private final TaskContext taskContext;
    private final Executor executor;

    @Inject
    public SqlStatisticStoreFactory(final StatisticStoreCache statisticStoreCache,
                                    final PropertyService propertyService,
                                    final StatisticsSearchService statisticsSearchService,
                                    final TaskContext taskContext,
                                    final ExecutorProvider executorProvider) {
        this.statisticStoreCache = statisticStoreCache;
        this.propertyService = propertyService;
        this.statisticsSearchService = statisticsSearchService;
        this.taskContext = taskContext;

        // TODO do we want to limit this with a thread pool?
        this.executor = executorProvider.getExecutor();
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

        final Store store = buildStore(searchRequest, statisticStoreDoc);
        return store;
    }

    private Store buildStore(final SearchRequest searchRequest,
                             final StatisticStoreDoc statisticStoreDoc) {

        Preconditions.checkNotNull(searchRequest);
        Preconditions.checkNotNull(statisticStoreDoc);

        final StoreSize storeSize = new StoreSize(getStoreSizes());
        final List<Integer> defaultMaxResultsSizes = getDefaultMaxResultsSizes();
        final int resultHandlerBatchSize = getResultHandlerBatchSize();

        //wrap the resultHandler in a new store, initiating the search in the process
        final SqlStatisticsStore store = new SqlStatisticsStore(
                searchRequest,
                statisticStoreDoc,
                statisticsSearchService,
                defaultMaxResultsSizes,
                storeSize,
                resultHandlerBatchSize,
                executor,
                taskContext);

        return store;
    }

    private List<Integer> getDefaultMaxResultsSizes() {
        final String value = propertyService.getProperty(ClientProperties.DEFAULT_MAX_RESULTS);
        return extractValues(value);
    }

    private List<Integer> getStoreSizes() {
        final String value = propertyService.getProperty(PROP_KEY_STORE_SIZE);
        return extractValues(value);
    }

    private int getResultHandlerBatchSize() {
        return propertyService.getIntProperty(PROP_KEY_RESULT_HANDLER_BATCH_SIZE, DEFAULT_ROWS_IN_BATCH);
    }

    private List<Integer> extractValues(String value) {
        if (value != null) {
            try {
                return Arrays.stream(value.split(","))
                        .map(String::trim)
                        .map(Integer::valueOf)
                        .collect(Collectors.toList());
            } catch (Exception e) {
                LOGGER.warn(e.getMessage());
            }
        }
        return Collections.emptyList();
    }

}
