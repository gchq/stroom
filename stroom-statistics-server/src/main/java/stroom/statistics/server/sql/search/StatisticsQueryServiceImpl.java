package stroom.statistics.server.sql.search;

import com.google.common.base.Preconditions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import stroom.datasource.api.v2.DataSource;
import stroom.query.api.v2.DocRef;
import stroom.query.api.v2.OffsetRange;
import stroom.query.api.v2.QueryKey;
import stroom.query.api.v2.Result;
import stroom.query.api.v2.SearchRequest;
import stroom.query.api.v2.SearchResponse;
import stroom.query.api.v2.TableResult;
import stroom.query.common.v2.SearchResponseCreator;
import stroom.query.common.v2.SearchResponseCreatorCache;
import stroom.query.common.v2.SearchResponseCreatorManager;
import stroom.statistics.server.sql.SQLStatisticCacheImpl;
import stroom.statistics.server.sql.StatisticsQueryService;
import stroom.statistics.server.sql.datasource.StatisticStoreCache;
import stroom.statistics.server.sql.datasource.StatisticsDataSourceProvider;
import stroom.statistics.shared.StatisticStoreEntity;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@SuppressWarnings("unused") //used by DI
@Component
public class StatisticsQueryServiceImpl implements StatisticsQueryService {

    private static final Logger LOGGER = LoggerFactory.getLogger(StatisticsQueryServiceImpl.class);
    private static final LambdaLogger LAMBDA_LOGGER = LambdaLoggerFactory.getLogger(SQLStatisticCacheImpl.class);

    private static final String PROP_KEY_STORE_SIZE = "stroom.search.storeSize";
    public static final long PROCESS_PAYLOAD_INTERVAL_SECS = 1L;

    private final StatisticsDataSourceProvider statisticsDataSourceProvider;
    private final StatisticStoreCache statisticStoreCache;
    private final SearchResponseCreatorManager searchResponseCreatorManager;

    @Inject
    public StatisticsQueryServiceImpl(final StatisticsDataSourceProvider statisticsDataSourceProvider,
                                      final StatisticStoreCache statisticStoreCache,
                                      @Named("sqlStatisticsSearchResponseCreatorManager")
                                          final SearchResponseCreatorManager searchResponseCreatorManager) {
        this.statisticsDataSourceProvider = statisticsDataSourceProvider;
        this.statisticStoreCache = statisticStoreCache;
        this.searchResponseCreatorManager = searchResponseCreatorManager;
    }


    @Override
    public DataSource getDataSource(final DocRef docRef) {
        LOGGER.debug("getDataSource called for docRef {}", docRef);
        return statisticsDataSourceProvider.getDataSource(docRef);
    }

    @Override
    public SearchResponse search(final SearchRequest searchRequest) {
        LOGGER.debug("search called for searchRequest {}", searchRequest);

        final DocRef docRef = Preconditions.checkNotNull(
                Preconditions.checkNotNull(
                        Preconditions.checkNotNull(searchRequest)
                                .getQuery())
                        .getDataSource());
        Preconditions.checkNotNull(searchRequest.getResultRequests(), "searchRequest must have at least one resultRequest");
        Preconditions.checkArgument(!searchRequest.getResultRequests().isEmpty(), "searchRequest must have at least one resultRequest");

        final StatisticStoreEntity statisticStoreEntity = statisticStoreCache.getStatisticsDataSource(docRef);

        if (statisticStoreEntity == null) {
            return buildEmptyResponse(
                    searchRequest,
                    "Statistic configuration could not be found for uuid " + docRef.getUuid());
        } else {
            return buildResponse(searchRequest, statisticStoreEntity);
        }
    }

    @Override
    public Boolean destroy(final QueryKey queryKey) {
        LOGGER.debug("destroy called for queryKey {}", queryKey);
        searchResponseCreatorManager.remove(new SearchResponseCreatorCache.Key(queryKey));
        return Boolean.TRUE;
    }

//    private static Coprocessor createCoprocessor(final CoprocessorSettings settings,
//                                                 final FieldIndexMap fieldIndexMap,
//                                                 final Map<String, String> paramMap,
//                                                 final HasTerminate taskMonitor) {
//        if (settings instanceof TableCoprocessorSettings) {
//            final TableCoprocessorSettings tableCoprocessorSettings = (TableCoprocessorSettings) settings;
//            final TableCoprocessor tableCoprocessor = new TableCoprocessor(
//                    tableCoprocessorSettings, fieldIndexMap, taskMonitor, paramMap);
//            return tableCoprocessor;
//        }
//        return null;
//    }
//

    private SearchResponse buildResponse(final SearchRequest searchRequest,
                                         final StatisticStoreEntity statisticStoreEntity) {

        Preconditions.checkNotNull(searchRequest);
        Preconditions.checkNotNull(statisticStoreEntity);

//        final CoprocessorSettingsMap coprocessorSettingsMap = CoprocessorSettingsMap.create(searchRequest);
//        Preconditions.checkNotNull(coprocessorSettingsMap);
//
//        final FieldIndexMap fieldIndexMap = new FieldIndexMap(true);
//        final Map<String, String> paramMap = getParamMap(searchRequest);

//        final HasTerminate taskMonitor = new HasTerminate() {
//            //TODO do something about this
//            @Override
//            public void terminate() {
//                System.out.println("terminating");
//            }
//            @Override
//            public boolean isTerminated() {
//                return false;
//            }
//        };

//        final Map<CoprocessorSettingsMap.CoprocessorKey, Coprocessor> coprocessorMap = coprocessorSettingsMap.getMap()
//                .entrySet()
//                .stream()
//                .map(entry -> Maps.immutableEntry(
//                        entry.getKey(),
//                        createCoprocessor(entry.getValue(), fieldIndexMap, paramMap, taskMonitor)))
//                .filter(entry -> entry.getKey() != null)
//                .collect(ImmutableMap.toImmutableMap(Map.Entry::getKey, Map.Entry::getValue));

        // convert the search into something stats understands
//        final FindEventCriteria criteria = StatStoreCriteriaBuilder.buildCriteria(searchRequest, statisticStoreEntity);
//
//        final AtomicLong counter = new AtomicLong(0);
//
//        final StoreSize storeSize = new StoreSize(getStoreSizes());
//        final List<Integer> defaultMaxResultsSizes = getDefaultMaxResultsSizes();
//
//        //TODO should be inside a Store which should be inside a SearchResponseCreator
//        final SearchResultHandler resultHandler = new SearchResultHandler(
//                coprocessorSettingsMap,
//                defaultMaxResultsSizes,
//                storeSize);


        // subscribe to the flowable, mapping each resultSet to a String[]
        // After the window period has elapsed a new flowable is create for those rows received
        // in that window, which can all be processed and sent
        // If the task is canceled, the flowable produced by search() will stop emitting
        // Set up the results flowable, the search wont be executed until subscribe is called
//        statisticsSearchService.search(statisticStoreEntity, criteria, fieldIndexMap)
//                .window(PROCESS_PAYLOAD_INTERVAL_SECS, TimeUnit.SECONDS)
//                .subscribe(
//                        windowedFlowable -> {
//                            LOGGER.trace("onNext called for outer flowable");
//                            windowedFlowable.subscribe(
//                                    data -> {
//                                        LAMBDA_LOGGER.trace(() -> String.format("data: [%s]", Arrays.toString(data)));
//                                        counter.incrementAndGet();
//
//                                        // give the data array to each of our coprocessors
//                                        coprocessorMap.values().forEach(coprocessor ->
//                                                coprocessor.receive(data));
//                                    },
//                                    throwable -> {
//                                        throw new RuntimeException(String.format("Error in flow, %s",
//                                                throwable.getMessage()), throwable);
//                                    },
//                                    () -> {
//                                        LAMBDA_LOGGER.debug(() ->
//                                                String.format("onComplete of inner flowable called, processing results so far, counter: %s",
//                                                        counter.get()));
//                                        //completed our timed window so create and pass on a payload for the
//                                        //data we have gathered so far
//                                        processPayloads(resultHandler, coprocessorMap, taskMonitor);
//
////                                        taskMonitor.info(task.getSearchName() +
////                                                " - running database query (" + counter.get() + " rows fetched)");
//                                    });
//                        },
//                        throwable -> {
//                            throw new RuntimeException(String.format("Error in flow, %s",
//                                    throwable.getMessage()), throwable);
//                        },
//                        () -> LOGGER.debug("onComplete of outer flowable called"));
//
//        LOGGER.debug("Out of flowable");
//
//        //flows all complete, so process any remaining data
//        processPayloads(resultHandler, coprocessorMap, taskMonitor);

        //wrap the resulthandler with a store
//        final SqlStatisticsStore store = new SqlStatisticsStore(
//                defaultMaxResultsSizes,
//                storeSize,
//                resultHandler, statisticsSearchService, statisticStoreEntity);

//        final SearchResponseCreator searchResponseCreator = new SearchResponseCreator(store);

        // This will create/get a searchResponseCreator for this query key
        final SearchResponseCreator searchResponseCreator = searchResponseCreatorManager.get(
                new SearchResponseCreatorCache.Key(searchRequest));

        // This will build a response from the search whether it is still running or has finished
        final SearchResponse searchResponse = searchResponseCreator.create(searchRequest);

        return searchResponse;
    }

//    private Map<String, String> getParamMap(final SearchRequest searchRequest) {
//        final Map<String, String> paramMap;
//        if (searchRequest.getQuery().getParams() != null) {
//            paramMap = searchRequest.getQuery().getParams().stream()
//                    .collect(Collectors.toMap(Param::getKey, Param::getValue));
//        } else {
//            paramMap = Collections.emptyMap();
//        }
//        return paramMap;
//    }

//    /**
//     * Synchronized to ensure multiple threads don't fight over the coprocessors which is unlikely to
//     * happen anyway as it is mostly used in
//     */
//    private synchronized void processPayloads(final ResultHandler resultHandler,
//                                              final Map<CoprocessorSettingsMap.CoprocessorKey, Coprocessor> coprocessorMap,
//                                              final HasTerminate taskMonitor) {
//
//        LAMBDA_LOGGER.debug(() ->
//                LambdaLogger.buildMessage("processPayloads called for {} coprocessors", coprocessorMap.size()));
//
//        //build a payload map from whatever the coprocessors have in them, if anything
//        final Map<CoprocessorSettingsMap.CoprocessorKey, Payload> payloadMap = coprocessorMap.entrySet().stream()
//                .map(entry ->
//                        Maps.immutableEntry(entry.getKey(), entry.getValue().createPayload()))
//                .filter(entry ->
//                        entry.getValue() != null)
//                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
//
//        // give the processed results to the collector, it will handle nulls
//        resultHandler.handle(payloadMap, taskMonitor);
//    }

    private SearchResponse buildEmptyResponse(final SearchRequest searchRequest, final String errorMessage) {
        return buildEmptyResponse(searchRequest, Collections.singletonList(errorMessage));
    }

    private SearchResponse buildEmptyResponse(final SearchRequest searchRequest, final List<String> errorMessages) {

        List<Result> results;
        if (searchRequest.getResultRequests() != null) {
            results = searchRequest.getResultRequests().stream()
                    .map(resultRequest -> new TableResult(
                            resultRequest.getComponentId(),
                            Collections.emptyList(),
                            new OffsetRange(0, 0),
                            0,
                            null))
                    .collect(Collectors.toList());
        } else {
            results = Collections.emptyList();
        }

        return new SearchResponse(
                Collections.emptyList(),
                results,
                errorMessages,
                true);
    }

//    private List<Integer> getDefaultMaxResultsSizes() {
//        final String value = stroomPropertyService.getProperty(ClientProperties.DEFAULT_MAX_RESULTS);
//        return extractValues(value);
//    }
//
//    private List<Integer> getStoreSizes() {
//        final String value = stroomPropertyService.getProperty(PROP_KEY_STORE_SIZE);
//        return extractValues(value);
//    }
//
//    private List<Integer> extractValues(String value) {
//        if (value != null) {
//            try {
//                return Arrays.stream(value.split(","))
//                        .map(String::trim)
//                        .map(Integer::valueOf)
//                        .collect(Collectors.toList());
//            } catch (Exception e) {
//                LOGGER.warn(e.getMessage());
//            }
//        }
//        return Collections.emptyList();
//    }
}
