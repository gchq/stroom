package stroom.statistics.server.sql.search;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import io.reactivex.Flowable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import stroom.dashboard.expression.v1.FieldIndexMap;
import stroom.node.server.StroomPropertyService;
import stroom.node.shared.ClientProperties;
import stroom.query.api.v2.DocRef;
import stroom.query.api.v2.Param;
import stroom.query.api.v2.SearchRequest;
import stroom.query.common.v2.Coprocessor;
import stroom.query.common.v2.CoprocessorSettings;
import stroom.query.common.v2.CoprocessorSettingsMap;
import stroom.query.common.v2.SearchResultHandler;
import stroom.query.common.v2.Store;
import stroom.query.common.v2.StoreFactory;
import stroom.query.common.v2.StoreSize;
import stroom.query.common.v2.TableCoprocessor;
import stroom.query.common.v2.TableCoprocessorSettings;
import stroom.statistics.server.sql.datasource.StatisticStoreCache;
import stroom.statistics.shared.StatisticStoreEntity;
import stroom.task.server.ExecutorProvider;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.HasTerminate;

import javax.inject.Inject;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

@SuppressWarnings("unused")
@Component("sqlStatisticStoreFactory")
public class SqlStatisticStoreFactory implements StoreFactory {

    private static final Logger LOGGER = LoggerFactory.getLogger(SqlStatisticStoreFactory.class);
    private static final LambdaLogger LAMBDA_LOGGER = LambdaLoggerFactory.getLogger(SqlStatisticStoreFactory.class);


    private static final String PROP_KEY_STORE_SIZE = "stroom.search.storeSize";

    private final StatisticStoreCache statisticStoreCache;
    private final StroomPropertyService stroomPropertyService;
    private final StatisticsSearchService statisticsSearchService;
    private final Executor executor;

    @Inject
    public SqlStatisticStoreFactory(final StatisticStoreCache statisticStoreCache,
                                    final StroomPropertyService stroomPropertyService,
                                    final StatisticsSearchService statisticsSearchService,
                                    final ExecutorProvider executorProvider) {
        this.statisticStoreCache = statisticStoreCache;
        this.stroomPropertyService = stroomPropertyService;
        this.statisticsSearchService = statisticsSearchService;

        // TODO do we want to limit this with a thread pool?
        this.executor = executorProvider.getExecutor();
    }

    @Override
    public Store create(final SearchRequest searchRequest) {
        LOGGER.debug("create called for searchRequest {} ", searchRequest.toString());

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

        final CoprocessorSettingsMap coprocessorSettingsMap = CoprocessorSettingsMap.create(searchRequest);
        Preconditions.checkNotNull(coprocessorSettingsMap);

        final FieldIndexMap fieldIndexMap = new FieldIndexMap(true);
        final Map<String, String> paramMap = getParamMap(searchRequest);

        final HasTerminate taskMonitor = getTaskMonitor();

        final Map<CoprocessorSettingsMap.CoprocessorKey, Coprocessor> coprocessorMap = coprocessorSettingsMap.getMap()
                .entrySet()
                .stream()
                .map(entry -> Maps.immutableEntry(
                        entry.getKey(),
                        createCoprocessor(entry.getValue(), fieldIndexMap, paramMap, taskMonitor)))
                .filter(entry -> entry.getKey() != null)
                .collect(ImmutableMap.toImmutableMap(Map.Entry::getKey, Map.Entry::getValue));

        // convert the search into something stats understands
        final FindEventCriteria criteria = StatStoreCriteriaBuilder.buildCriteria(searchRequest, statisticStoreEntity);


        final StoreSize storeSize = new StoreSize(getStoreSizes());
        final List<Integer> defaultMaxResultsSizes = getDefaultMaxResultsSizes();

        //TODO should be inside a Store which should be inside a SearchResponseCreator
        final SearchResultHandler resultHandler = new SearchResultHandler(
                coprocessorSettingsMap,
                defaultMaxResultsSizes,
                storeSize);

        //get the flowable for the search results
        final Flowable<String[]> searchResultsFlowable = statisticsSearchService.search(
                statisticStoreEntity, criteria, fieldIndexMap);

        //wrap the resultHandler in a new store, initiating the search in the process
        final SqlStatisticsStore store = new SqlStatisticsStore(
                defaultMaxResultsSizes,
                storeSize,
                resultHandler,
                searchResultsFlowable,
                coprocessorMap,
                executor,
                taskMonitor);

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

    private static Coprocessor createCoprocessor(final CoprocessorSettings settings,
                                                 final FieldIndexMap fieldIndexMap,
                                                 final Map<String, String> paramMap,
                                                 final HasTerminate taskMonitor) {
        if (settings instanceof TableCoprocessorSettings) {
            final TableCoprocessorSettings tableCoprocessorSettings = (TableCoprocessorSettings) settings;
            final TableCoprocessor tableCoprocessor = new TableCoprocessor(
                    tableCoprocessorSettings, fieldIndexMap, taskMonitor, paramMap);
            return tableCoprocessor;
        }
        return null;
    }

    private Map<String, String> getParamMap(final SearchRequest searchRequest) {
        final Map<String, String> paramMap;
        if (searchRequest.getQuery().getParams() != null) {
            paramMap = searchRequest.getQuery().getParams().stream()
                    .collect(Collectors.toMap(Param::getKey, Param::getValue));
        } else {
            paramMap = Collections.emptyMap();
        }
        return paramMap;
    }

    private List<Integer> getDefaultMaxResultsSizes() {
        final String value = stroomPropertyService.getProperty(ClientProperties.DEFAULT_MAX_RESULTS);
        return extractValues(value);
    }

    private List<Integer> getStoreSizes() {
        final String value = stroomPropertyService.getProperty(PROP_KEY_STORE_SIZE);
        return extractValues(value);
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
