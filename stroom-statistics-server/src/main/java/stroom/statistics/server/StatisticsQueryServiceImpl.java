package stroom.statistics.server;

import com.google.common.base.Preconditions;
import org.springframework.stereotype.Component;
import stroom.dashboard.expression.FieldIndexMap;
import stroom.datasource.api.v1.DataSource;
import stroom.query.Coprocessor;
import stroom.query.CoprocessorSettings;
import stroom.query.CoprocessorSettingsMap;
import stroom.query.Payload;
import stroom.query.SearchResponseCreator;
import stroom.query.TableCoprocessor;
import stroom.query.TableCoprocessorSettings;
import stroom.query.api.v1.DocRef;
import stroom.query.api.v1.Param;
import stroom.query.api.v1.QueryKey;
import stroom.query.api.v1.SearchRequest;
import stroom.query.api.v1.SearchResponse;
import stroom.statistics.common.StatisticDataPoint;
import stroom.statistics.common.StatisticDataSet;
import stroom.statistics.common.StatisticStoreCache;
import stroom.statistics.common.StatisticsQueryService;
import stroom.statistics.server.common.StatisticsDataSourceProvider;
import stroom.statistics.shared.StatisticStoreEntity;
import stroom.statistics.shared.common.EventStoreTimeIntervalEnum;
import stroom.statistics.sql.SQLStatisticEventStore;
import stroom.util.shared.HasTerminate;

import javax.inject.Inject;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class StatisticsQueryServiceImpl implements StatisticsQueryService {

    private static final Map<String, Function<StatisticDataPoint, String>> fieldMapperMap = new HashMap<>();

    static {
        fieldMapperMap.put(StatisticStoreEntity.FIELD_NAME_DATE_TIME, dataPoint -> Long.toString(dataPoint.getTimeMs()));
        fieldMapperMap.put(StatisticStoreEntity.FIELD_NAME_PRECISION, StatisticsQueryServiceImpl::getPrecision);
        fieldMapperMap.put(StatisticStoreEntity.FIELD_NAME_PRECISION_MS, dataPoint -> Long.toString(dataPoint.getPrecisionMs()));
        fieldMapperMap.put(StatisticStoreEntity.FIELD_NAME_COUNT, dataPoint -> Long.toString(dataPoint.getCount()));
        fieldMapperMap.put(StatisticStoreEntity.FIELD_NAME_VALUE, dataPoint -> Double.toString(dataPoint.getValue()));
        fieldMapperMap.put(StatisticStoreEntity.FIELD_NAME_MIN_VALUE, dataPoint -> Double.toString(dataPoint.getMinValue()));
        fieldMapperMap.put(StatisticStoreEntity.FIELD_NAME_MAX_VALUE, dataPoint -> Double.toString(dataPoint.getMaxValue()));
    }

    private final StatisticsDataSourceProvider statisticsDataSourceProvider;
    private final StatisticStoreCache statisticStoreCache;
    private final SQLStatisticEventStore sqlStatisticEventStore;

    @Inject
    public StatisticsQueryServiceImpl(final StatisticsDataSourceProvider statisticsDataSourceProvider,
                                      final StatisticStoreCache statisticStoreCache,
                                      final SQLStatisticEventStore sqlStatisticEventStore) {
        this.statisticsDataSourceProvider = statisticsDataSourceProvider;
        this.statisticStoreCache = statisticStoreCache;
        this.sqlStatisticEventStore = sqlStatisticEventStore;
    }

    @Override
    public DataSource getDataSource(final DocRef docRef) {
        return statisticsDataSourceProvider.getDataSource(docRef);
    }

    @Override
    public SearchResponse search(final SearchRequest searchRequest) {

        DocRef docRef = Preconditions.checkNotNull(
                Preconditions.checkNotNull(Preconditions.checkNotNull(searchRequest).getQuery()).getDataSource());

        StatisticStoreEntity statisticStoreEntity = statisticStoreCache.getStatisticsDataSource(docRef);
        if (statisticStoreEntity == null) {
            return buildEmptyResponse(
                    "Statistic configuration could not be found for uuid " + docRef.getUuid());
        }

        StatisticDataSet statisticDataSet = sqlStatisticEventStore.searchStatisticsData(
                searchRequest,
                statisticStoreEntity);

        if (statisticDataSet.isEmpty()) {
            return buildEmptyResponse(Collections.emptyList());
        } else {
            return buildResponse(searchRequest, statisticStoreEntity, statisticDataSet);
        }
    }

    @Override
    public Boolean destroy(final QueryKey queryKey) {
        throw new UnsupportedOperationException("Destroy is not currently support for SQL Statistics queries");
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

        SqlStatisticsStore store = new SqlStatisticsStore();
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
                    dataArray[posInDataArray] = statisticDataPoint.getTagValue(fieldName);
                }
            });
            return dataArray;
        };
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

    private SearchResponse buildEmptyResponse(final String errorMessage) {
        return buildEmptyResponse(Collections.singletonList(errorMessage));
    }

    private SearchResponse buildEmptyResponse(final List<String> errorMessages) {
        return new SearchResponse(
                Collections.emptyList(),
                Collections.emptyList(),
                errorMessages,
                true);
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
}
