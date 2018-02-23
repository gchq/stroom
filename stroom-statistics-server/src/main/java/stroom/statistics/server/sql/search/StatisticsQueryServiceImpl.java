package stroom.statistics.server.sql.search;

import com.google.common.base.Preconditions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import stroom.datasource.api.v2.DataSource;
import stroom.node.server.StroomPropertyService;
import stroom.query.api.v2.DocRef;
import stroom.query.api.v2.OffsetRange;
import stroom.query.api.v2.QueryKey;
import stroom.query.api.v2.Result;
import stroom.query.api.v2.SearchRequest;
import stroom.query.api.v2.SearchResponse;
import stroom.query.api.v2.TableResult;
import stroom.statistics.server.sql.SQLStatisticEventStore;
import stroom.statistics.server.sql.StatisticsQueryService;
import stroom.statistics.server.sql.datasource.StatisticStoreCache;
import stroom.statistics.server.sql.datasource.StatisticsDataSourceProvider;
import stroom.statistics.shared.StatisticStoreEntity;
import stroom.statistics.shared.common.EventStoreTimeIntervalEnum;

import javax.inject.Inject;
import java.util.Collections;
import java.util.List;
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
            return SqlStatisticsSearchResponseCreator.buildResponse(
                    searchRequest,
                    statisticStoreEntity,
                    statisticDataSet,
                    stroomPropertyService);
        }
    }

    @Override
    public Boolean destroy(final QueryKey queryKey) {
        LOGGER.trace("destroy called for queryKey {}", queryKey);
        //No concept of destroying a search for sql statistics so just return true
        return Boolean.TRUE;
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


}
