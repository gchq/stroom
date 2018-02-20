package stroom.statistics.server.sql.search;

import org.assertj.core.api.Assertions;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;
import stroom.node.server.MockStroomPropertyService;
import stroom.query.api.v2.DocRef;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.ExpressionTerm;
import stroom.query.api.v2.Field;
import stroom.query.api.v2.OffsetRange;
import stroom.query.api.v2.Query;
import stroom.query.api.v2.ResultRequest;
import stroom.query.api.v2.SearchRequest;
import stroom.query.api.v2.SearchResponse;
import stroom.query.api.v2.TableResult;
import stroom.query.api.v2.TableSettings;
import stroom.statistics.server.sql.SQLStatisticEventStore;
import stroom.statistics.server.sql.StatisticTag;
import stroom.statistics.server.sql.StatisticsQueryService;
import stroom.statistics.server.sql.datasource.StatisticStoreCache;
import stroom.statistics.shared.StatisticStoreEntity;
import stroom.statistics.shared.StatisticType;
import stroom.statistics.shared.StatisticsDataSourceData;
import stroom.statistics.shared.common.StatisticField;
import stroom.util.date.DateUtil;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@RunWith(MockitoJUnitRunner.class)
public class TestStatisticsQueryServiceImpl {

    private static final String FIELD_DATE_TIME_STR = "dateTimeStr";
    private static final String STAT_NAME = "MyStat";
    private static final String QUERY_ID = UUID.randomUUID().toString();

    @Mock
    private StatisticStoreCache mockStatisticStoreCache;

    @Mock
    private SQLStatisticEventStore mockSqlStatisticEventStore;

    private MockStroomPropertyService mockStroomPropertyService = new MockStroomPropertyService();

    private DocRef docRef = new DocRef("StatisticStore", UUID.randomUUID().toString(), STAT_NAME);
    private LocalDateTime startDate = LocalDateTime.of(2016, 8, 1, 13, 0, 0);
    private LocalDateTime endDate = LocalDateTime.of(2018, 8, 1, 13, 0, 0);

    @Before
    public void setup() {
        mockStroomPropertyService.loadDefaults();
        StatisticStoreEntity statisticStoreEntity = buildStatisticsDataSource(true);
        Mockito.when(mockStatisticStoreCache.getStatisticsDataSource(Mockito.any(DocRef.class)))
                .thenReturn(statisticStoreEntity);

        StatisticDataSet statisticDataSet = buildStatisticDataSet();
        Mockito.when(mockSqlStatisticEventStore.searchStatisticsData(Mockito.any(), Mockito.any()))
                .thenReturn(statisticDataSet);
    }

    @Test
    public void search_allDataNoGrouping() {



        StatisticsQueryService statisticsQueryService = new StatisticsQueryServiceImpl(
                null,
                mockStatisticStoreCache,
                mockSqlStatisticEventStore,
                mockStroomPropertyService);

        SearchRequest searchRequest = buildSearchRequest(docRef, Optional.empty(), Optional.empty());
        SearchResponse searchResponse = statisticsQueryService.search(searchRequest);

        Assertions.assertThat(searchResponse).isNotNull();
        Assertions.assertThat(searchResponse.getResults()).isNotNull();
        TableResult tableResult = (TableResult) searchResponse.getResults().get(0);

        Assertions.assertThat(tableResult.getRows()).hasSize(500);
    }

    @Test
    public void search_groupOnTruncatedDates() {


        StatisticsQueryService statisticsQueryService = new StatisticsQueryServiceImpl(
                null,
                mockStatisticStoreCache,
                mockSqlStatisticEventStore,
                mockStroomPropertyService);

        final Query query = new Query.Builder()
                .dataSource(docRef)
                .expression(
                        new ExpressionOperator.Builder()
                                .addTerm(
                                        StatisticStoreEntity.FIELD_NAME_DATE_TIME,
                                        ExpressionTerm.Condition.BETWEEN,
                                        "year()-1y,year()")
                                .build())
                .build();

        final TableSettings tableSettings = new TableSettings.Builder()
                .queryId(QUERY_ID)
                .addFields(
                        new Field.Builder()
                                .name(StatisticStoreEntity.FIELD_NAME_DATE_TIME)
                                .expression("roundDay(" + nameToExpression(StatisticStoreEntity.FIELD_NAME_DATE_TIME) + ")")
                                .group(1)
                                .build(),
                        new Field.Builder()
                                .name(StatisticStoreEntity.FIELD_NAME_COUNT)
                                .expression("sum(" + nameToExpression(StatisticStoreEntity.FIELD_NAME_COUNT) + ")")
                                .build(),
                        new Field.Builder()
                                .name(FIELD_DATE_TIME_STR)
                                .expression(nameToExpression(FIELD_DATE_TIME_STR))
                                .group(1)
                                .build())
                .extractValues(false)
                .showDetail(false)
                .build();

        SearchRequest searchRequest = buildSearchRequest(docRef, Optional.of(query), Optional.of(tableSettings));

        SearchResponse searchResponse = statisticsQueryService.search(searchRequest);

        Assertions.assertThat(searchResponse).isNotNull();
        Assertions.assertThat(searchResponse.getResults()).isNotNull();
        TableResult tableResult = (TableResult) searchResponse.getResults().get(0);

        Assertions.assertThat(tableResult.getRows()).hasSize(365);
    }

    private SearchRequest buildSearchRequest(final DocRef docRef,
                                             final Optional<Query> queryOveride,
                                             final Optional<TableSettings> tableSettingOverride) {


        final Query defaultQuery = new Query.Builder()
                        .dataSource(docRef)
                        .expression(
                                new ExpressionOperator.Builder()
                                        .addTerm(
                                                StatisticStoreEntity.FIELD_NAME_DATE_TIME,
                                                ExpressionTerm.Condition.BETWEEN,
                                                DateUtil.createNormalDateTimeString(startDate) +
                                                        "," +
                                                        DateUtil.createNormalDateTimeString(endDate))
                                        .build())
                        .build();

        final TableSettings defaultTableSettings = new TableSettings.Builder()
                                        .queryId(QUERY_ID)
                                        .addFields(
                                                new Field.Builder()
                                                        .name(StatisticStoreEntity.FIELD_NAME_DATE_TIME)
                                                        .expression(nameToExpression(StatisticStoreEntity.FIELD_NAME_DATE_TIME))
                                                        .build(),
                                                new Field.Builder()
                                                        .name(StatisticStoreEntity.FIELD_NAME_COUNT)
                                                        .expression(nameToExpression(StatisticStoreEntity.FIELD_NAME_COUNT))
                                                        .build(),
                                                new Field.Builder()
                                                        .name(FIELD_DATE_TIME_STR)
                                                        .expression(nameToExpression(FIELD_DATE_TIME_STR))
                                                        .build())
                                        .extractValues(false)
                                        .showDetail(false)
                                        .build();

        SearchRequest searchRequest = new SearchRequest.Builder()
                .incremental(false)
                .key(QUERY_ID)
                .query(queryOveride.orElse(defaultQuery))
                .addResultRequests(
                        new ResultRequest.Builder()
                                .componentId(UUID.randomUUID().toString())
                                .resultStyle(ResultRequest.ResultStyle.TABLE)
                                .requestedRange(new OffsetRange.Builder()
                                        .offset(0L)
                                        .length(1000L)
                                        .build())
                                .addMappings(tableSettingOverride.orElse(defaultTableSettings))
                                .build())
                .build();

        return searchRequest;
    }

    private StatisticDataSet buildStatisticDataSet() {

        Set<StatisticDataPoint> dataPoints = Stream.iterate(
                LocalDateTime.of(2016, 8, 1, 13, 0, 0),
                localDateTime -> localDateTime.plusDays(1))
                .limit(500)
                .map(localDateTime ->
                        new CountStatisticDataPoint(
                                localDateTime.toInstant(ZoneOffset.UTC).toEpochMilli(),
                                1_000,
                                Collections.singletonList(new StatisticTag(FIELD_DATE_TIME_STR, localDateTime.toString())),
                                1L))
                .collect(Collectors.toSet());

        StatisticDataSet statisticDataSet = new StatisticDataSet(
                STAT_NAME, StatisticType.COUNT, 1_000, dataPoints);

        return statisticDataSet;
    }

    private StatisticStoreEntity buildStatisticsDataSource(final boolean addFields) {
        final StatisticsDataSourceData statisticsDataSourceData = new StatisticsDataSourceData();

        if (addFields) {
            statisticsDataSourceData.addStatisticField(new StatisticField(FIELD_DATE_TIME_STR));
        }

        final StatisticStoreEntity sds = new StatisticStoreEntity();
        sds.setStatisticDataSourceDataObject(statisticsDataSourceData);
        return sds;
    }

    private String nameToExpression(final String fieldName) {
        return "${" + fieldName + "}";
    }
}