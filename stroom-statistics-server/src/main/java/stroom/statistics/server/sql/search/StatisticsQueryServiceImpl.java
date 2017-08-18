package stroom.statistics.server.sql.search;

import com.google.common.base.Preconditions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import stroom.dashboard.expression.v1.FieldIndexMap;
import stroom.datasource.api.v2.DataSource;
import stroom.node.server.StroomPropertyService;
import stroom.node.shared.ClientProperties;
import stroom.query.api.v2.DocRef;
import stroom.query.api.v2.OffsetRange;
import stroom.query.api.v2.Param;
import stroom.query.api.v2.QueryKey;
import stroom.query.api.v2.Result;
import stroom.query.api.v2.SearchRequest;
import stroom.query.api.v2.SearchResponse;
import stroom.query.api.v2.TableResult;
import stroom.query.v2.Coprocessor;
import stroom.query.v2.CoprocessorSettings;
import stroom.query.v2.CoprocessorSettingsMap;
import stroom.query.v2.Payload;
import stroom.query.v2.SearchResponseCreator;
import stroom.query.v2.TableCoprocessor;
import stroom.query.v2.TableCoprocessorSettings;
import stroom.statistics.server.sql.SQLStatisticEventStore;
import stroom.statistics.server.sql.StatisticsQueryService;
import stroom.statistics.server.sql.datasource.StatisticStoreCache;
import stroom.statistics.server.sql.datasource.StatisticsDataSourceProvider;
import stroom.statistics.shared.StatisticStoreEntity;
import stroom.statistics.shared.common.EventStoreTimeIntervalEnum;
import stroom.util.shared.HasTerminate;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class StatisticsQueryServiceImpl implements StatisticsQueryService {

    private static final Logger LOGGER = LoggerFactory.getLogger(StatisticsQueryServiceImpl.class);

    private final StatisticsDataSourceProvider statisticsDataSourceProvider;
    private final StatisticStoreCache statisticStoreCache;
    private final SQLStatisticEventStore sqlStatisticEventStore;
    private final StroomPropertyService stroomPropertyService;

    @Inject
    public StatisticsQueryServiceImpl(final StatisticsDataSourceProvider statisticsDataSourceProvider,
                                      final StatisticStoreCache statisticStoreCache,
                                      final SQLStatisticEventStore sqlStatisticEventStore,
                                      final StroomPropertyService stroomPropertyService) {
        this.statisticsDataSourceProvider = statisticsDataSourceProvider;
        this.statisticStoreCache = statisticStoreCache;
        this.sqlStatisticEventStore = sqlStatisticEventStore;
        this.stroomPropertyService = stroomPropertyService;
    }

    public static Coprocessor createCoprocessor(final CoprocessorSettings settings,
                                                final FieldIndexMap fieldIndexMap,
                                                final Map<String, String> paramMap,
                                                final HasTerminate taskMonitor) {
        if (settings instanceof TableCoprocessorSettings) {
            final TableCoprocessorSettings tableCoprocessorSettings = (TableCoprocessorSettings) settings;
            final TableCoprocessor tableCoprocessor = new TableCoprocessor(tableCoprocessorSettings,
                    fieldIndexMap, taskMonitor, paramMap);
            return tableCoprocessor;
        }
        return null;
    }

    private static String getPrecision(StatisticDataPoint statisticDataPoint) {

        final EventStoreTimeIntervalEnum interval = EventStoreTimeIntervalEnum.fromColumnInterval(
                statisticDataPoint.getPrecisionMs());
        if (interval != null) {
            return interval.longName();
        } else {
            // could be a precision that doesn't match one of our interval sizes
            return "-";
        }
    }

    @Override
    public DataSource getDataSource(final DocRef docRef) {
        return statisticsDataSourceProvider.getDataSource(docRef);
    }

    @Override
    public SearchResponse search(final SearchRequest searchRequest) {

        DocRef docRef = Preconditions.checkNotNull(
                Preconditions.checkNotNull(Preconditions.checkNotNull(searchRequest).getQuery()).getDataSource());
        Preconditions.checkNotNull(searchRequest.getResultRequests(), "searchRequest must have at least one resultRequest");
        Preconditions.checkArgument(!searchRequest.getResultRequests().isEmpty(), "searchRequest must have at least one resultRequest");

        StatisticStoreEntity statisticStoreEntity = statisticStoreCache.getStatisticsDataSource(docRef);
        if (statisticStoreEntity == null) {
            return buildEmptyResponse(
                    searchRequest,
                    "Statistic configuration could not be found for uuid " + docRef.getUuid());
        }

        StatisticDataSet statisticDataSet = sqlStatisticEventStore.searchStatisticsData(
                searchRequest,
                statisticStoreEntity);

        if (statisticDataSet.isEmpty()) {
            return buildEmptyResponse(searchRequest, Collections.emptyList());
        } else {
            return buildResponse(searchRequest, statisticStoreEntity, statisticDataSet);
        }
    }

    @Override
    public Boolean destroy(final QueryKey queryKey) {
        LOGGER.trace("destroy called for queryKey {}", queryKey);
        //No concept of destroying a search for sql statistics so just return true
        return Boolean.TRUE;
    }

    private SearchResponse buildResponse(final SearchRequest searchRequest,
                                         final StatisticStoreEntity statisticStoreEntity,
                                         final StatisticDataSet statisticDataSet) {

        Preconditions.checkNotNull(searchRequest);
        Preconditions.checkNotNull(statisticStoreEntity);
        Preconditions.checkNotNull(statisticDataSet);
        Preconditions.checkArgument(!statisticDataSet.isEmpty());

        // TODO: possibly the mapping from the componentId to the coprocessorsettings map is a bit odd.
        final CoprocessorSettingsMap coprocessorSettingsMap = CoprocessorSettingsMap.create(searchRequest);

        Map<CoprocessorSettingsMap.CoprocessorKey, Coprocessor> coprocessorMap = new HashMap<>();
        // TODO: Mapping to this is complicated! it'd be nice not to have to do this.
        final FieldIndexMap fieldIndexMap = new FieldIndexMap(true);

        // Compile all of the result component options to optimise pattern matching etc.
        if (coprocessorSettingsMap.getMap() != null) {
            for (final Map.Entry<CoprocessorSettingsMap.CoprocessorKey, CoprocessorSettings> entry : coprocessorSettingsMap.getMap().entrySet()) {
                final CoprocessorSettingsMap.CoprocessorKey coprocessorId = entry.getKey();
                final CoprocessorSettings coprocessorSettings = entry.getValue();

                // Create a parameter map.
                final Map<String, String> paramMap = Collections.emptyMap();
                if (searchRequest.getQuery().getParams() != null) {
                    for (final Param param : searchRequest.getQuery().getParams()) {
                        paramMap.put(param.getKey(), param.getValue());
                    }
                }

                final Coprocessor coprocessor = createCoprocessor(
                        coprocessorSettings, fieldIndexMap, paramMap, new HasTerminate() {
                            //TODO do something about this
                            @Override
                            public void terminate() {
                                System.out.println("terminating");
                            }

                            @Override
                            public boolean isTerminated() {
                                return false;
                            }
                        });

                if (coprocessor != null) {
                    coprocessorMap.put(coprocessorId, coprocessor);
                }
            }
        }

        Function<StatisticDataPoint, String[]> dataPointMapper = buildDataPointMapper(
                fieldIndexMap, statisticStoreEntity);

        statisticDataSet.stream()
                .map(dataPointMapper)
                .forEach(dataArray ->
                        coprocessorMap.forEach(
                                (key, value) -> value.receive(dataArray)));

        Map<CoprocessorSettingsMap.CoprocessorKey, Payload> payloadMap = coprocessorMap.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> entry.getValue().createPayload()));

        SqlStatisticsStore store = new SqlStatisticsStore(getDefaultTrimSizes());
        store.process(coprocessorSettingsMap);
        store.coprocessorMap(coprocessorMap);
        store.payloadMap(payloadMap);

        SearchResponseCreator searchResponseCreator = new SearchResponseCreator(store);
        SearchResponse searchResponse = searchResponseCreator.create(searchRequest);

        return searchResponse;
    }

    private Function<StatisticDataPoint, String[]> buildDataPointMapper(final FieldIndexMap fieldIndexMap,
                                                                        final StatisticStoreEntity statisticStoreEntity) {
        return statisticDataPoint -> {
            String[] dataArray = new String[fieldIndexMap.size()];

            //TODO should probably drive this off a new fieldIndexMap.getEntries() method or similar
            //then we only loop round fields we car about
            statisticStoreEntity.getAllFieldNames().forEach(fieldName -> {
                int posInDataArray = fieldIndexMap.get(fieldName);
                //if the fieldIndexMap returns -1 the field has not been requested
                if (posInDataArray != -1) {
                    dataArray[posInDataArray] = statisticDataPoint.getFieldValue(fieldName);
                }
            });
            return dataArray;
        };
    }

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
                            new OffsetRange(0,0),
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

    private List<Integer> getDefaultTrimSizes() {
        try {
            final String value = stroomPropertyService.getProperty(ClientProperties.DEFAULT_MAX_RESULTS);
            if (value != null) {
                final String[] parts = value.split(",");
                final List<Integer> list = new ArrayList<>(parts.length);
                for (int i = 0; i < parts.length; i++) {
                    list.add(Integer.valueOf(parts[i].trim()));
                }
                return list;
            }
        } catch (final Exception e) {
            LOGGER.warn(e.getMessage());
        }

        return null;
    }
}
