/*
 * Copyright 2016 Crown Copyright
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

package stroom.statistics.server.common.search;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.AbstractCoreIntegrationTest;
import stroom.CommonTestControl;
import stroom.dashboard.expression.v1.Generator;
import stroom.dashboard.expression.v1.Val;
import stroom.dashboard.shared.ParamUtil;
import stroom.dashboard.shared.TableResultRequest;
import stroom.entity.server.util.StroomDatabaseInfo;
import stroom.entity.shared.DocRef;
import stroom.entity.shared.FolderService;
import stroom.query.CoprocessorMap;
import stroom.query.Item;
import stroom.query.Items;
import stroom.query.ResultStore;
import stroom.query.SearchResultHandler;
import stroom.query.shared.ComponentResultRequest;
import stroom.query.shared.ComponentSettings;
import stroom.query.shared.ExpressionBuilder;
import stroom.query.shared.ExpressionOperator;
import stroom.query.shared.ExpressionTerm.Condition;
import stroom.query.shared.Field;
import stroom.query.shared.Search;
import stroom.query.shared.TableSettings;
import stroom.statistics.common.RolledUpStatisticEvent;
import stroom.statistics.common.StatisticEvent;
import stroom.statistics.common.StatisticStoreEntityService;
import stroom.statistics.common.StatisticTag;
import stroom.statistics.common.exception.StatisticsEventValidationException;
import stroom.statistics.shared.StatisticField;
import stroom.statistics.shared.StatisticStoreEntity;
import stroom.statistics.shared.StatisticType;
import stroom.statistics.shared.StatisticsDataSourceData;
import stroom.statistics.sql.SQLStatisticAggregateMap;
import stroom.statistics.sql.SQLStatisticAggregationManager;
import stroom.statistics.sql.SQLStatisticAggregationTransactionHelper;
import stroom.statistics.sql.SQLStatisticFlushTask;
import stroom.statistics.sql.SQLStatisticFlushTaskHandler;
import stroom.statistics.sql.SQLStatisticNames;
import stroom.statistics.sql.SQLStatisticValueBatchSaveService;
import stroom.task.server.TaskContext;
import stroom.task.server.TaskManager;
import stroom.task.server.TaskMonitorImpl;
import stroom.util.task.ServerTask;
import stroom.util.test.FileSystemTestUtil;
import stroom.util.thread.ThreadUtil;

import javax.annotation.Resource;
import javax.inject.Provider;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class TestStatStoreSearchTaskHandler extends AbstractCoreIntegrationTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(TestStatStoreSearchTaskHandler.class);

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
                    StatisticStoreEntity.FIELD_NAME_DATE_TIME, //0
                    StatisticStoreEntity.FIELD_NAME_VALUE, //1
                    StatisticStoreEntity.FIELD_NAME_PRECISION_MS, //2
                    TAG1), //3
            COMPONENT_ID_2, Arrays.asList(
                    StatisticStoreEntity.FIELD_NAME_DATE_TIME,
                    StatisticStoreEntity.FIELD_NAME_COUNT, //4
                    StatisticStoreEntity.FIELD_NAME_PRECISION_MS,
                    TAG1,
                    TAG2)); //5

    private static final Map<String, Integer> FIELD_POSITION_MAP = ImmutableMap.of(
            TAG1, 3,
            TAG2, 5);

//    private static final DocRef DOC_REF = new DocRef(StatisticStoreEntity.ENTITY_TYPE, UUID.randomUUID().toString(), STAT_NAME);


    @Resource
    private TaskManager taskManager;
    @Resource
    private CommonTestControl commonTestControl;
    @Resource
    private DataSource statisticsDataSource;
    @Resource
    private SQLStatisticValueBatchSaveService sqlStatisticValueBatchSaveService;
    @Resource
    private SQLStatisticAggregationManager sqlStatisticAggregationManager;
    @Resource
    private SQLStatisticAggregationTransactionHelper sqlStatisticAggregationTransactionHelper;
    @Resource
    private StroomDatabaseInfo stroomDatabaseInfo;
    @Resource
    private StatisticStoreEntityService statisticStoreEntityService;
    @Resource
    private FolderService folderService;
    @Resource
    private TaskContext taskContext;
    @Resource
    private Provider<StatStoreSearchTaskHandler> statStoreSearchTaskHandlerProvider;

    private StatisticStoreEntity statisticStoreEntity;

    private boolean ignoreAllTests = false;

    @Override
    public void onBefore() {
        if (!stroomDatabaseInfo.isMysql()) {
            LOGGER.warn("Database is not MySQL, skipping test");
            ignoreAllTests = true;
        } else {
            try {
                sqlStatisticAggregationTransactionHelper
                        .clearTable(SQLStatisticNames.SQL_STATISTIC_VALUE_SOURCE_TABLE_NAME);
                sqlStatisticAggregationTransactionHelper.clearTable(SQLStatisticNames.SQL_STATISTIC_VALUE_TABLE_NAME);
                sqlStatisticAggregationTransactionHelper.clearTable(SQLStatisticNames.SQL_STATISTIC_KEY_TABLE_NAME);
            } catch (final SQLException e) {
                throw new RuntimeException("Error tearing down tables", e);
            }

            commonTestControl.teardown();
            commonTestControl.setup();

            final DocRef folder = DocRef.create(folderService.create(null, FileSystemTestUtil.getUniqueTestString()));

            final StatisticStoreEntity statisticStoreEntity = statisticStoreEntityService.create(folder, STAT_NAME);
            statisticStoreEntity.setEngineName("EngineName1");
            statisticStoreEntity.setDescription("My Description");
            statisticStoreEntity.setStatisticType(StatisticType.VALUE);
            statisticStoreEntity.setStatisticDataSourceDataObject(new StatisticsDataSourceData());
            statisticStoreEntity.getStatisticDataSourceDataObject().addStatisticField(new StatisticField(TAG1));
            statisticStoreEntity.getStatisticDataSourceDataObject().addStatisticField(new StatisticField(TAG2));
            statisticStoreEntityService.save(statisticStoreEntity);
            this.statisticStoreEntity = statisticStoreEntity;
        }
    }

    @Test
    public void testSearchStatisticsData_TwoTags() throws SQLException {

        if (!ignoreAllTests) {
            final List<StatisticTag> tags = new ArrayList<>();
            tags.add(new StatisticTag(TAG1, TAG1_VAL));
            tags.add(new StatisticTag(TAG2, TAG2_VAL));

            fillStatValSrc(tags);
            StatStoreSearchResultCollector collector = doSearch(tags);
            Map<String, Set<String>> expectedValuesMap = ImmutableMap.of(
                    TAG1, ImmutableSet.of(TAG1_VAL),
                    TAG2, ImmutableSet.of(TAG2_VAL));

            doAsserts(collector, 1, expectedValuesMap);
        }
    }


    @Test
    public void testSearchStatisticsData_OneTagTwoOptions() throws SQLException {
        if (!ignoreAllTests) {
            final List<StatisticTag> tags = new ArrayList<>();
            tags.add(new StatisticTag(TAG1, TAG1_VAL));
            tags.add(new StatisticTag(TAG2, TAG2_VAL));

            fillStatValSrc(tags);

            final List<StatisticTag> searchTags = new ArrayList<>();
            searchTags.add(new StatisticTag(TAG2, TAG2_VAL));
            searchTags.add(new StatisticTag(TAG2, TAG2_OTHER_VALUE_1));

            Map<String, Set<String>> expectedValuesMap = ImmutableMap.of(
                    TAG1, ImmutableSet.of(TAG1_VAL),
                    TAG2, ImmutableSet.of(TAG2_VAL, TAG2_OTHER_VALUE_1));

            StatStoreSearchResultCollector collector = doSearch(searchTags, ExpressionOperator.Op.OR);
            doAsserts(collector, 2, expectedValuesMap);
        }
    }

    @Test
    public void testSearchStatisticsData_TwoTagsOneNull() throws SQLException {
        if (!ignoreAllTests) {
            final List<StatisticTag> tags = new ArrayList<>();
            tags.add(new StatisticTag(TAG1, null));
            tags.add(new StatisticTag(TAG2, TAG2_VAL));

            fillStatValSrc(tags);

            // only search on the second tag
            final List<StatisticTag> searchTags = new ArrayList<>();
            searchTags.add(tags.get(1));

            Map<String, Set<String>> expectedValuesMap = ImmutableMap.of(
                    TAG1, Collections.singleton(null),
                    TAG2, Collections.singleton(TAG2_VAL));

            StatStoreSearchResultCollector collector = doSearch(searchTags, ExpressionOperator.Op.OR);
            doAsserts(collector, 1, expectedValuesMap);
        }
    }

    @Test
    public void testSearchStatisticsData_TwoTagsOneHasNastyChars() throws SQLException {

        if (!ignoreAllTests) {
            final List<StatisticTag> tags = new ArrayList<>();
            // search on a tag value with loads of regex special chars in
            String nastyVal = "xxx\\^$.|?*+()[{xxx";
            tags.add(new StatisticTag(TAG1, nastyVal));
            tags.add(new StatisticTag(TAG2, TAG2_VAL));

            fillStatValSrc(tags);

            // only search on the first tag
            final List<StatisticTag> searchTags = new ArrayList<>();
            searchTags.add(tags.get(0));

            Map<String, Set<String>> expectedValuesMap = ImmutableMap.of(
                    TAG1, Collections.singleton(nastyVal),
                    TAG2, Collections.singleton(TAG2_VAL));

            StatStoreSearchResultCollector collector = doSearch(searchTags, ExpressionOperator.Op.OR);
            doAsserts(collector, 1, expectedValuesMap);
        }
    }

    private void doAsserts(final StatStoreSearchResultCollector collector,
                           final int expectedRowCount,
                           final Map<String, Set<String>> expectedValuesMap) {

        Map<String, Set<String>> actualValuesMap = new HashMap<>();
        COMPONENT_IDS.forEach(id -> {
            ResultStore resultStore = collector.getResultStore(id);

            Assert.assertNotNull(resultStore);
            Assert.assertEquals(expectedRowCount, resultStore.getTotalSize());

            List<String> fields = COMPONENT_ID_TO_FIELDS_MAP.get(id);
            int fieldCount = fields.size();


            //get the root level
            Items<Item> items = resultStore.getChildMap().get(null);

            //row count
            Assert.assertEquals(expectedRowCount, items.size());

            AtomicInteger counter = new AtomicInteger(0);
            items.forEach(item -> {
                Assert.assertEquals(fieldCount, item.getGenerators().length);

//                expectedValuesMap.forEach((tagField, expectedValues) -> {
//
//                });
//                if (fields.contains())

                //capture actual values for fields of interest
                expectedValuesMap.keySet().forEach(tagField -> {
                    int idx = fields.indexOf(tagField);
                    if (idx != -1) {
                        final Generator generator = item.getGenerators()[idx];
                        final Val evaluatedVal = generator.eval();
                        final String strVal = evaluatedVal == null ? null : evaluatedVal.toString();
                        actualValuesMap.computeIfAbsent(tagField, k -> new HashSet<>()).add(strVal);
                    }
                });

                String valuesStr = Arrays.stream(item.getGenerators())
                        .map(Generator::eval)
                        .map(evaluatedObj -> evaluatedObj == null ? "" : evaluatedObj.toString())
                        .collect(Collectors.joining(","));

                LOGGER.debug("component: {} - [{}]", String.valueOf(id), valuesStr);
            });
        });

        expectedValuesMap.forEach((tagField, expectedValues) -> {
            List<String> exp = new ArrayList<>(expectedValues);
            Collections.sort(exp);
            List<String> act = new ArrayList<>(actualValuesMap.get(tagField));
            Collections.sort(act);
            LOGGER.debug("Comparing {} and {}", exp.toString(), act.toString());
            Assert.assertEquals(exp, act);
        });
    }

    private StatStoreSearchResultCollector doSearch(final List<StatisticTag> searchTags) {
        return doSearch(searchTags, ExpressionOperator.Op.AND);
    }

    private StatStoreSearchResultCollector doSearch(final List<StatisticTag> searchTags, final ExpressionOperator.Op op) {
        final ExpressionBuilder rootOperator = new ExpressionBuilder(op);
        rootOperator
                .addTerm(StatisticStoreEntity.FIELD_NAME_DATE_TIME, Condition.BETWEEN, DATE_RANGE);

        for (final StatisticTag tag : searchTags) {
            rootOperator.addTerm(tag.getTag(), Condition.EQUALS, tag.getValue());
        }

        final Map<String, ComponentSettings> resultComponentMap = new HashMap<String, ComponentSettings>();
        final Map<String, ComponentResultRequest> componentResultRequests = new HashMap<String, ComponentResultRequest>();

        //TODO make the table settings different
        COMPONENT_IDS.forEach(componentId -> {
            final TableSettings tableSettings = createTableSettings(componentId);

            resultComponentMap.put(componentId, tableSettings);

            final TableResultRequest tableResultRequest = new TableResultRequest();
            tableResultRequest.setTableSettings(tableSettings);
            tableResultRequest.setWantsData(true);
            componentResultRequests.put(componentId, tableResultRequest);
        });


        final Search search = new Search(null, rootOperator.build(), resultComponentMap);

        // Create a coprocessor map.
        final CoprocessorMap coprocessorMap = new CoprocessorMap(search.getComponentSettingsMap());

        // Create a handler for search results.
        final SearchResultHandler resultHandler = new SearchResultHandler(coprocessorMap);

        // Create the search result collector.
        final StatStoreSearchResultCollector searchResultCollector = new StatStoreSearchResultCollector(
                ServerTask.INTERNAL_PROCESSING_USER_TOKEN,
                taskManager,
                taskContext,
                "mySearch",
                search,
                statisticStoreEntity,
                coprocessorMap.getMap(),
                statStoreSearchTaskHandlerProvider,
                resultHandler);

        LOGGER.debug("Starting search");
        searchResultCollector.start();

        while (!searchResultCollector.isComplete()) {
            ThreadUtil.sleep(250);
        }
        LOGGER.debug("Search complete");

        return searchResultCollector;
    }

    private TableSettings createTableSettings(final String componentId) {
        final TableSettings tableSettings = new TableSettings();

        List<String> fields = COMPONENT_ID_TO_FIELDS_MAP.get(componentId);
        Preconditions.checkNotNull(fields);
        fields.forEach(field -> addField(field, tableSettings));

        return tableSettings;
    }

    private void addField(String name, final TableSettings tableSettings) {
        final Field field = new Field(name);
        field.setExpression(ParamUtil.makeParam(name));
        tableSettings.addField(field);
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
                sqlStatisticValueBatchSaveService, new TaskMonitorImpl());

        final SQLStatisticFlushTask flushTask = new SQLStatisticFlushTask(sqlStatisticAggregateMap);

        taskHandler.exec(flushTask);

        sqlStatisticAggregationManager.aggregate();

        Assert.assertEquals(3, getRowCount(SQLStatisticNames.SQL_STATISTIC_VALUE_TABLE_NAME));
        Assert.assertEquals(3, getRowCount(SQLStatisticNames.SQL_STATISTIC_KEY_TABLE_NAME));
    }

    private int getRowCount(final String tableName) throws SQLException {
        int count;
        try (final Connection connection = statisticsDataSource.getConnection()) {
            try (final PreparedStatement preparedStatement = connection.prepareStatement("select count(*) from " + tableName)) {
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
