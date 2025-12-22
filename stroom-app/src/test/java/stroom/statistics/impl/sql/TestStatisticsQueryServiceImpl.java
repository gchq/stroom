/*
 * Copyright 2016-2025 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.statistics.impl.sql;

import stroom.docref.DocRef;
import stroom.docstore.shared.DocRefUtil;
import stroom.query.api.Column;
import stroom.query.api.DateTimeSettings;
import stroom.query.api.DestroyReason;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionOperator.Op;
import stroom.query.api.ExpressionTerm;
import stroom.query.api.ParamUtil;
import stroom.query.api.Query;
import stroom.query.api.QueryKey;
import stroom.query.api.ResultRequest;
import stroom.query.api.SearchRequest;
import stroom.query.api.SearchResponse;
import stroom.query.api.TableResult;
import stroom.query.api.TableSettings;
import stroom.query.common.v2.ResultStoreManager;
import stroom.security.api.SecurityContext;
import stroom.statistics.impl.sql.entity.StatisticStoreStore;
import stroom.statistics.impl.sql.exception.StatisticsEventValidationException;
import stroom.statistics.impl.sql.rollup.RolledUpStatisticEvent;
import stroom.statistics.impl.sql.search.SqlStatisticSearchProvider;
import stroom.statistics.impl.sql.shared.StatisticField;
import stroom.statistics.impl.sql.shared.StatisticStoreDoc;
import stroom.statistics.impl.sql.shared.StatisticType;
import stroom.statistics.impl.sql.shared.StatisticsDataSourceData;
import stroom.task.api.TaskContextFactory;
import stroom.task.api.TaskManager;
import stroom.test.AbstractCoreIntegrationTest;
import stroom.test.CommonTestControl;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class TestStatisticsQueryServiceImpl extends AbstractCoreIntegrationTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestStatisticsQueryServiceImpl.class);

    private static final String STAT_NAME = "MyStat";
    private static final String TAG1 = "Tag1";
    private static final String TAG1_VAL = "Tag1Value1";
    private static final String TAG2 = "Tag2";
    private static final String TAG2_VAL = "Tag2Value1";
    private static final String TAG2_OTHER_VALUE_1 = "Tag2Value2";
    private static final String TAG2_OTHER_VALUE_2 = "Tag2Value3";
    private static final String DATE_RANGE = "2000-01-01T00:00:00.000Z,3000-01-01T00:00:00.000Z";

    private static final String COMPONENT_ID_1 = "1";
    private static final String COMPONENT_ID_2 = "2";
    private static final List<String> COMPONENT_IDS = Arrays.asList(COMPONENT_ID_1, COMPONENT_ID_2);

    private static final Map<String, List<String>> COMPONENT_ID_TO_FIELDS_MAP = ImmutableMap.of(
            COMPONENT_ID_1, Arrays.asList(
                    StatisticStoreDoc.FIELD_NAME_DATE_TIME, //0
                    StatisticStoreDoc.FIELD_NAME_VALUE, //1
                    StatisticStoreDoc.FIELD_NAME_PRECISION_MS, //2
                    TAG1), //3
            COMPONENT_ID_2, Arrays.asList(
                    StatisticStoreDoc.FIELD_NAME_DATE_TIME,
                    StatisticStoreDoc.FIELD_NAME_COUNT, //4
                    StatisticStoreDoc.FIELD_NAME_PRECISION_MS,
                    TAG1,
                    TAG2)); //5

    private static final Map<String, Integer> FIELD_POSITION_MAP = ImmutableMap.of(
            TAG1, 3,
            TAG2, 5);

//    private static final DocRef DOC_REF = new DocRef(
//    StatisticStoreDoc.DOCUMENT_TYPE, UUID.randomUUID().toString(), STAT_NAME);


    @Inject
    private CommonTestControl commonTestControl;
    @Inject
    private SQLStatisticsDbConnProvider sqlStatisticsDbConnProvider;
    @Inject
    private SQLStatisticValueBatchSaveService sqlStatisticValueBatchSaveService;
    @Inject
    private SQLStatisticAggregationManager sqlStatisticAggregationManager;
    @Inject
    private SQLStatisticAggregationTransactionHelper sqlStatisticAggregationTransactionHelper;
    @Inject
    private SQLStatisticEventStore sqlStatisticEventStore;
    @Inject
    private TaskManager taskManager;
    @Inject
    private TaskContextFactory taskContextFactory;
    @Inject
    private StatisticStoreStore statisticStoreStore;
    @Inject
    private SqlStatisticSearchProvider statisticsQueryService;
    @Inject
    private ResultStoreManager searchResponseCreatorManager;
    @Inject
    private SecurityContext securityContext;

    private StatisticStoreDoc statisticStoreDoc;

    private final boolean ignoreAllTests = false;

    @BeforeEach
    void localSetup() {
//        try {
//            sqlStatisticAggregationTransactionHelper
//                    .clearTable(SQLStatisticNames.SQL_STATISTIC_VALUE_SOURCE_TABLE_NAME);
//            sqlStatisticAggregationTransactionHelper.clearTable(SQLStatisticNames.SQL_STATISTIC_VALUE_TABLE_NAME);
//            sqlStatisticAggregationTransactionHelper.clearTable(SQLStatisticNames.SQL_STATISTIC_KEY_TABLE_NAME);
//        } catch (final SQLException e) {
//            throw new RuntimeException("Error tearing down tables", e);
//        }
//
//        commonTestControl.teardown();
//        commonTestControl.setup(tempDir);

        final DocRef statisticStoreRef = statisticStoreStore.createDocument(STAT_NAME);
        final StatisticStoreDoc statisticStoreDoc = statisticStoreStore.readDocument(statisticStoreRef);
        statisticStoreDoc.setDescription("My Description");
        statisticStoreDoc.setStatisticType(StatisticType.VALUE);
        statisticStoreDoc.setConfig(new StatisticsDataSourceData());
        statisticStoreDoc.getConfig().addStatisticField(new StatisticField(TAG1));
        statisticStoreDoc.getConfig().addStatisticField(new StatisticField(TAG2));
        statisticStoreStore.writeDocument(statisticStoreDoc);
        this.statisticStoreDoc = statisticStoreDoc;
    }

    @Test
    void testSearchStatisticsData_TwoTags() throws SQLException {
        if (!ignoreAllTests) {
            final List<StatisticTag> tags = new ArrayList<>();
            tags.add(new StatisticTag(TAG1, TAG1_VAL));
            tags.add(new StatisticTag(TAG2, TAG2_VAL));

            fillStatValSrc(tags);
            final SearchResponse searchResponse = doSearch(tags, false, null);
            searchResponseCreatorManager.destroy(searchResponse.getKey(), DestroyReason.NO_LONGER_NEEDED);
            final Map<String, Set<String>> expectedValuesMap = ImmutableMap.of(
                    TAG1, ImmutableSet.of(TAG1_VAL),
                    TAG2, ImmutableSet.of(TAG2_VAL));

            doAsserts(searchResponse, 1, expectedValuesMap);
        }
    }

    @Test
    void testSearchStatisticsData_TwoTags_incrementalNoTimeout() throws SQLException {
        if (!ignoreAllTests) {
            final List<StatisticTag> tags = new ArrayList<>();
            tags.add(new StatisticTag(TAG1, TAG1_VAL));
            tags.add(new StatisticTag(TAG2, TAG2_VAL));

            fillStatValSrc(tags);
            final Map<String, Set<String>> expectedValuesMap = ImmutableMap.of(
                    TAG1, ImmutableSet.of(TAG1_VAL),
                    TAG2, ImmutableSet.of(TAG2_VAL));

            SearchResponse searchResponse = null;

            // Incremental search so first n responses may not be complete
            final Instant timeoutTime = Instant.now().plusSeconds(10);

            // keep asking for results till we get a complete one (or timeout)
            QueryKey queryKey = null;
            do {
                searchResponse = doSearch(tags, true, queryKey);
                queryKey = searchResponse.getKey();
            } while (!searchResponse.complete() && Instant.now().isBefore(timeoutTime));
            searchResponseCreatorManager.destroy(searchResponse.getKey(), DestroyReason.NO_LONGER_NEEDED);

            doAsserts(searchResponse, 1, expectedValuesMap);
        }
    }

    @Test
    void testSearchStatisticsData_TwoTags_incremental10sTimeout() throws SQLException {

        if (!ignoreAllTests) {
            final List<StatisticTag> tags = new ArrayList<>();
            tags.add(new StatisticTag(TAG1, TAG1_VAL));
            tags.add(new StatisticTag(TAG2, TAG2_VAL));

            fillStatValSrc(tags);
            final Map<String, Set<String>> expectedValuesMap = ImmutableMap.of(
                    TAG1, ImmutableSet.of(TAG1_VAL),
                    TAG2, ImmutableSet.of(TAG2_VAL));

            //incremental but with a 10s timeout so should get our results on 1st try
            final SearchResponse searchResponse = doSearch(
                    tags,
                    Op.AND,
                    true,
                    null,
                    OptionalLong.of(10_000));
            searchResponseCreatorManager.destroy(searchResponse.getKey(), DestroyReason.NO_LONGER_NEEDED);

            doAsserts(searchResponse, 1, expectedValuesMap);
        }
    }


    @Test
    void testSearchStatisticsData_OneTagTwoOptions() throws SQLException {
        if (!ignoreAllTests) {
            final List<StatisticTag> tags = new ArrayList<>();
            tags.add(new StatisticTag(TAG1, TAG1_VAL));
            tags.add(new StatisticTag(TAG2, TAG2_VAL));

            fillStatValSrc(tags);

            final List<StatisticTag> searchTags = new ArrayList<>();
            searchTags.add(new StatisticTag(TAG2, TAG2_VAL));
            searchTags.add(new StatisticTag(TAG2, TAG2_OTHER_VALUE_1));

            final Map<String, Set<String>> expectedValuesMap = ImmutableMap.of(
                    TAG1, ImmutableSet.of(TAG1_VAL),
                    TAG2, ImmutableSet.of(TAG2_VAL, TAG2_OTHER_VALUE_1));

            final SearchResponse searchResponse = doSearch(searchTags, Op.OR, false, null);
            searchResponseCreatorManager.destroy(searchResponse.getKey(), DestroyReason.NO_LONGER_NEEDED);
            doAsserts(searchResponse, 2, expectedValuesMap);
        }
    }

    @Test
    void testSearchStatisticsData_TwoTagsOneNull() throws SQLException {
        if (!ignoreAllTests) {
            final List<StatisticTag> tags = new ArrayList<>();
            tags.add(new StatisticTag(TAG1, null));
            tags.add(new StatisticTag(TAG2, TAG2_VAL));

            fillStatValSrc(tags);

            // only search on the second tag
            final List<StatisticTag> searchTags = new ArrayList<>();
            searchTags.add(tags.get(1));

            final Map<String, Set<String>> expectedValuesMap = ImmutableMap.of(
                    TAG1, Collections.singleton(null),
                    TAG2, Collections.singleton(TAG2_VAL));

            final SearchResponse searchResponse = doSearch(searchTags, Op.OR, false, null);
            searchResponseCreatorManager.destroy(searchResponse.getKey(), DestroyReason.NO_LONGER_NEEDED);
            doAsserts(searchResponse, 1, expectedValuesMap);
        }
    }

    @Test
    void testSearchStatisticsData_TwoTagsOneHasNastyChars() throws SQLException {

        if (!ignoreAllTests) {
            final List<StatisticTag> tags = new ArrayList<>();
            // search on a tag value with loads of regex special chars in
            final String nastyVal = "xxx\\^$.|?*+()[{xxx";
            tags.add(new StatisticTag(TAG1, nastyVal));
            tags.add(new StatisticTag(TAG2, TAG2_VAL));

            fillStatValSrc(tags);

            // only search on the first tag
            final List<StatisticTag> searchTags = new ArrayList<>();
            searchTags.add(tags.get(0));

            final Map<String, Set<String>> expectedValuesMap = ImmutableMap.of(
                    TAG1, Collections.singleton(nastyVal),
                    TAG2, Collections.singleton(TAG2_VAL));

            final SearchResponse searchResponse = doSearch(searchTags, Op.OR, false, null);
            searchResponseCreatorManager.destroy(searchResponse.getKey(), DestroyReason.NO_LONGER_NEEDED);
            doAsserts(searchResponse, 1, expectedValuesMap);
        }
    }

    private void doAsserts(final SearchResponse searchResponse,
                           final int expectedRowCount,
                           final Map<String, Set<String>> expectedValuesMap) {

        assertThat(searchResponse.getResults()).hasSize(2);

        searchResponse.getResults().stream()
                .map(result -> {
                    assertThat(result).isInstanceOf(TableResult.class);
                    return (TableResult) result;
                })
                .forEach(tableResult -> {
                    final String id = tableResult.getComponentId();
                    LOGGER.debug("id: {}", id);
                    tableResult.getRows().forEach(row -> LOGGER.debug(String.join(",", row.getValues())));

                    assertThat(tableResult.getTotalResults())
                            .isEqualTo(expectedRowCount);

                    final List<String> fields = COMPONENT_ID_TO_FIELDS_MAP.get(id);
                    assertThat(fields).isNotNull();

                    assertThat(tableResult.getRows()).hasSize(expectedRowCount);

                    final Map<String, Integer> fieldMap = IntStream.range(0, fields.size())
                            .boxed()
                            .map(i -> Tuple.of(fields.get(i), i))
                            .collect(Collectors.toMap(Tuple2::_1, Tuple2::_2));

                    expectedValuesMap.forEach((field, expectedValues) -> {
                        //expectedValuesMap covers all component Ids so only consider the fields for this component
                        if (fields.contains(field)) {
                            final Integer idx = fieldMap.get(field);
                            assertThat(idx).isNotNull();
                            final List<String> actualValues = tableResult.getRows().stream()
                                    .map(row -> row.getValues().get(idx))
                                    .collect(Collectors.toList());

                            assertThat(actualValues).contains(expectedValues.toArray(new String[0]));
                        }
                    });
                });
    }

    private SearchResponse doSearch(final List<StatisticTag> searchTags,
                                    final boolean isIncremental,
                                    final QueryKey queryKey) {
        return doSearch(searchTags, Op.AND, isIncremental, queryKey, OptionalLong.empty());
    }

    private SearchResponse doSearch(final List<StatisticTag> searchTags,
                                    final ExpressionOperator.Op op,
                                    final boolean isIncremental,
                                    final QueryKey queryKey) {

        return doSearch(searchTags, op, isIncremental, queryKey, OptionalLong.empty());
    }

    private SearchResponse doSearch(final List<StatisticTag> searchTags,
                                    final ExpressionOperator.Op op,
                                    final boolean isIncremental,
                                    final QueryKey queryKey,
                                    final OptionalLong optTimeout) {

        final ExpressionOperator.Builder operatorBuilder = ExpressionOperator.builder().op(op);
        operatorBuilder
                .addTerm(StatisticStoreDoc.FIELD_NAME_DATE_TIME, ExpressionTerm.Condition.BETWEEN, DATE_RANGE);

        for (final StatisticTag tag : searchTags) {
            operatorBuilder.addTerm(tag.getTag(), ExpressionTerm.Condition.EQUALS, tag.getValue());
        }

        final SearchRequest.Builder searchBuilder = SearchRequest.builder()
                .key(queryKey)
                .incremental(isIncremental)
                .query(Query.builder()
                        .expression(operatorBuilder.build())
                        .dataSource(DocRefUtil.create(statisticStoreDoc))
                        .build())
                .dateTimeSettings(DateTimeSettings.builder().build());

        optTimeout.ifPresent(searchBuilder::timeout);

        COMPONENT_IDS.forEach(componentId -> {
            final TableSettings tableSettings = createTableSettings(componentId);

            searchBuilder.addResultRequests(ResultRequest.builder()
                    .componentId(componentId)
                    .addMappings(tableSettings)
                    .resultStyle(ResultRequest.ResultStyle.TABLE)
                    .build());
        });

        final SearchRequest searchRequest = searchBuilder.build();

        final SearchResponse searchResponse = searchResponseCreatorManager.search(searchRequest);
        if (!isIncremental || searchResponse.complete()) {
            searchResponseCreatorManager.destroy(searchResponse.getKey(), DestroyReason.NO_LONGER_NEEDED);
        }

        LOGGER.debug("Search response returned with completion state {}", searchResponse.getComplete());

        return searchResponse;
    }

    private TableSettings createTableSettings(final String componentId) {
        final TableSettings.Builder tableSettingsBuilder = TableSettings.builder();


        final List<String> fields = COMPONENT_ID_TO_FIELDS_MAP.get(componentId);
        Preconditions.checkNotNull(fields);
        fields.forEach(field -> addColumn(field, tableSettingsBuilder));

        return tableSettingsBuilder.build();
    }

    private void addColumn(final String name, final TableSettings.Builder tableSettingsBuilder) {
        final Column column = Column.builder()
                .id(name)
                .name(name)
                .expression(ParamUtil.create(name))
                .build();
        tableSettingsBuilder.addColumns(column);
    }

    private RolledUpStatisticEvent buildStatisticEvent(final List<StatisticTag> tags) {

        final long value = 1L;

        final long timeMs = System.currentTimeMillis();

        final StatisticEvent statisticEvent = StatisticEvent.createCount(timeMs, STAT_NAME, tags, value);

        return new RolledUpStatisticEvent(statisticEvent);
    }

    private void fillStatValSrc(final List<StatisticTag> tags) throws SQLException {

        final SQLStatisticAggregateMap sqlStatisticAggregateMap = new SQLStatisticAggregateMap();
        final long precision = 0L;
        try {
            // this is the event we want to search for
            final RolledUpStatisticEvent eventToFind = buildStatisticEvent(tags);
            // These two are just other events to make sure we only find the one
            // we want
            final RolledUpStatisticEvent otherEvent1 = buildStatisticEvent(
                    Arrays.asList(new StatisticTag(TAG1, TAG1_VAL), new StatisticTag(TAG2, TAG2_OTHER_VALUE_1)));
            final RolledUpStatisticEvent otherEvent2 = buildStatisticEvent(
                    Arrays.asList(new StatisticTag(TAG1, TAG1_VAL), new StatisticTag(TAG2, TAG2_OTHER_VALUE_2)));

            sqlStatisticAggregateMap.addRolledUpEvent(eventToFind, precision);
            sqlStatisticAggregateMap.addRolledUpEvent(otherEvent1, precision);
            sqlStatisticAggregateMap.addRolledUpEvent(otherEvent2, precision);
        } catch (final StatisticsEventValidationException e) {
            throw new RuntimeException("error", e);
        }

        final SQLStatisticFlushTaskHandler taskHandler = new SQLStatisticFlushTaskHandler(
                sqlStatisticValueBatchSaveService, taskContextFactory, securityContext, SQLStatisticsConfig::new);
        taskHandler.exec(sqlStatisticAggregateMap);

        sqlStatisticAggregationManager.aggregate();

        assertThat(getRowCount(SQLStatisticNames.SQL_STATISTIC_VALUE_TABLE_NAME))
                .isEqualTo(3);
        assertThat(getRowCount(SQLStatisticNames.SQL_STATISTIC_KEY_TABLE_NAME))
                .isEqualTo(3);
    }

    private int getRowCount(final String tableName) throws SQLException {
        final int count;
        try (final Connection connection = sqlStatisticsDbConnProvider.getConnection()) {
            try (final PreparedStatement preparedStatement = connection.prepareStatement(
                    "select count(*) from " + tableName)) {
                try (final ResultSet resultSet = preparedStatement.executeQuery()) {
                    resultSet.next();
                    count = resultSet.getInt(1);
                }
            }
        }
        return count;
    }


//    private static class MockTaskMonitor implements TaskMonitor {
//        private static final long serialVersionUID = -8415095958756818805L;
//
//        @Override
//        public Monitor getParent() {
//            // TODO Auto-generated method stub
//            return null;
//        }
//
//        @Override
//        public void addTerminateHandler(final TerminateHandler handler) {
//            // TODO Auto-generated method stub
//
//        }
//
//        @Override
//        public void terminate() {
//            // TODO Auto-generated method stub
//
//        }
//
//        @Override
//        public boolean isTerminated() {
//
//            return false;
//        }
//
//
//
//        @Override
//        public String getInfo() {
//            // TODO Auto-generated method stub
//            return null;
//        }
//
//        @Override
//        public void info(final Object... args) {
//            // do nothing
//
//        }
//    }

}
