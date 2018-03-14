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

import io.reactivex.Flowable;
import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.AbstractCoreIntegrationTest;
import stroom.CommonTestControl;
import stroom.dashboard.shared.ParamUtil;
import stroom.dashboard.shared.TableResultRequest;
import stroom.entity.server.util.StroomDatabaseInfo;
import stroom.entity.shared.DocRef;
import stroom.entity.shared.FolderService;
import stroom.query.CoprocessorMap;
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
import stroom.statistics.sql.SQLStatisticEventStore;
import stroom.statistics.sql.SQLStatisticFlushTask;
import stroom.statistics.sql.SQLStatisticFlushTaskHandler;
import stroom.statistics.sql.SQLStatisticNames;
import stroom.statistics.sql.SQLStatisticValueBatchSaveService;
import stroom.task.server.TaskManager;
import stroom.task.server.TaskMonitorImpl;
import stroom.util.task.ServerTask;
import stroom.util.test.FileSystemTestUtil;
import stroom.util.thread.ThreadUtil;

import javax.annotation.Resource;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class TestStatStoreSearchTaskHandler extends AbstractCoreIntegrationTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(TestStatStoreSearchTaskHandler.class);

    private static final String STAT_NAME = "MyStat";
    private static final String TAG1 = "Tag1";
    private static final String TAG1_VAL = "Tag1Value1";
    private static final String TAG2 = "Tag2";
    private static final String TAG2_VAL = "Tag2Value2";
    private static final String TAG2_OTHER_VALUE_1 = "Tag2OtherValue1";
    private static final String TAG2_OTHER_VALUE_2 = "Tag2OtherValue2";
    private static final String DATE_RANGE = "2000-01-01T00:00:00.000Z,3000-01-01T00:00:00.000Z";

//    private static final DocRef DOC_REF = new DocRef(StatisticStoreEntity.ENTITY_TYPE, UUID.randomUUID().toString(), STAT_NAME);

    public static final List<String> COMPONENT_IDS = Arrays.asList("1", "2");

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
    private SQLStatisticEventStore sqlStatisticEventStore;
    @Resource
    private StroomDatabaseInfo stroomDatabaseInfo;
    @Resource
    private TaskManager taskManager;
    @Resource
    private StatisticStoreEntityService statisticStoreEntityService;
    @Resource
    private FolderService folderService;

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

//            statisticStoreEntity = new StatisticStoreEntity();
//            statisticStoreEntity.setUuid(DOC_REF.getUuid());
//            statisticStoreEntity.setStatisticType(StatisticType.COUNT);
//            statisticStoreEntity.setName(STAT_NAME);
//            statisticStoreEntity.setRollUpType(StatisticRollUpType.NONE);
//            StatisticsDataSourceData statisticsDataSourceData = new StatisticsDataSourceData();
//            List<StatisticField> fields = Stream
//                    .of(TAG1, TAG2)
//                    .map(StatisticField::new)
//                    .collect(Collectors.toList());
//            statisticsDataSourceData.setStatisticFields(fields);
//
//
//            statisticStoreEntityService.create(Folder.)
            final DocRef folder = DocRef.create(folderService.create(null, FileSystemTestUtil.getUniqueTestString()));

            final StatisticStoreEntity statisticStoreEntity = statisticStoreEntityService.create(folder, STAT_NAME);
            statisticStoreEntity.setEngineName("EngineName1");
            statisticStoreEntity.setDescription("My Description");
            statisticStoreEntity.setStatisticType(StatisticType.COUNT);
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
            
            COMPONENT_IDS.forEach(id -> {
                ResultStore resultStore = collector.getResultStore(id);
                LOGGER.debug(resultStore.getChildMap().toString());
            });


//            final StatisticDataSet dataSet = doSearch(tags);
//
//            assertDataSetSize(dataSet, 1);
//
//            final StatisticDataPoint dataPoint = dataSet.iterator().next();
//            Assert.assertEquals(tags, dataPoint.getTags());
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

//            final StatisticDataSet dataSet = doSearch(searchTags, ExpressionOperator.Op.OR);
//
//            assertDataSetSize(dataSet, 2);
//
//            for (final StatisticDataPoint dataPoint : dataSet) {
//
//                final String tag2Value = dataPoint.getTagsAsMap().get(TAG2);
//
//                Assert.assertTrue(tag2Value.equals(TAG2_VAL) || tag2Value.equals(TAG2_OTHER_VALUE_1));
//            }
        }
    }

    @Test
    public void testSearchStatisticsData_TwoTagsOneNull() throws SQLException {
        if (!ignoreAllTests) {
            final List<StatisticTag> tags = new ArrayList<>();
            tags.add(new StatisticTag(TAG1, null));
            tags.add(new StatisticTag(TAG2, TAG2_VAL));

            fillStatValSrc(tags);

            // only search on the first tag
            final List<StatisticTag> searchTags = new ArrayList<>();
            searchTags.add(tags.get(1));

//            final StatisticDataSet dataSet = doSearch(searchTags);
//
//            assertDataSetSize(dataSet, 1);
//
//            final StatisticDataPoint dataPoint = dataSet.iterator().next();
//            Assert.assertEquals(tags, dataPoint.getTags());
        }
    }

    @Test
    public void testSearchStatisticsData_TwoTagsOneHasNastyChars() throws SQLException {

        if (!ignoreAllTests) {
            final List<StatisticTag> tags = new ArrayList<>();
            // search on a tag value with loads of regex special chars in
            tags.add(new StatisticTag(TAG1, "xxx\\^$.|?*+()[{xxx"));
            tags.add(new StatisticTag(TAG2, TAG2_VAL));

            fillStatValSrc(tags);

            // only search on the first tag
            final List<StatisticTag> searchTags = new ArrayList<>();
            searchTags.add(tags.get(0));

//            final StatisticDataSet dataSet = doSearch(searchTags);
//
//            assertDataSetSize(dataSet, 1);
//
//            final StatisticDataPoint dataPoint = dataSet.iterator().next();
//            Assert.assertEquals(tags, dataPoint.getTags());
        }
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
            final TableSettings tableSettings = createTableSettings(searchTags);

            resultComponentMap.put(componentId, tableSettings);

            final TableResultRequest tableResultRequest = new TableResultRequest();
            tableResultRequest.setTableSettings(tableSettings);
            tableResultRequest.setWantsData(true);
            componentResultRequests.put(componentId, tableResultRequest);
        });


        final Search search = new Search(null, rootOperator.build(), resultComponentMap);

        // Create a coprocessor map.
        final CoprocessorMap coprocessorMap = new CoprocessorMap(search.getComponentSettingsMap());

        StatStoreSearchTask task = new StatStoreSearchTask(
                ServerTask.INTERNAL_PROCESSING_USER_TOKEN,
                "mySearch",
                search,
                statisticStoreEntity,
                coprocessorMap.getMap());

        // Create a handler for search results.
        final SearchResultHandler resultHandler = new SearchResultHandler(coprocessorMap);

        // Create the search result collector.
        final StatStoreSearchResultCollector searchResultCollector = new StatStoreSearchResultCollector(taskManager,
                task, resultHandler);

        // Tell the task where results will be collected.
        task.setResultCollector(searchResultCollector);

        LOGGER.debug("Starting search");
        searchResultCollector.start();


        while (!searchResultCollector.isComplete()) {
            ThreadUtil.sleep(250);
        }
        LOGGER.debug("Search complete");

        return searchResultCollector;
    }

    private TableSettings createTableSettings(final List<StatisticTag> tags) {
        final TableSettings tableSettings = new TableSettings();

        addField(StatisticStoreEntity.FIELD_NAME_DATE_TIME, tableSettings);
        addField(StatisticStoreEntity.FIELD_NAME_PRECISION_MS, tableSettings);
        addField(StatisticStoreEntity.FIELD_NAME_VALUE, tableSettings);
        addField(StatisticStoreEntity.FIELD_NAME_COUNT, tableSettings);
        addField(TAG1, tableSettings);
        addField(TAG2, tableSettings);

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

    @Test
    public void testFlowable() {

        AtomicInteger counter = new AtomicInteger();
        Flowable
                .generate(
                        () -> {
                            LOGGER.debug("Init state");
                            return counter;
                        },
                        (i, emitter) -> {
                            int j = i.incrementAndGet();
                            if (j <= 10) {
                                LOGGER.debug("emit");
                                emitter.onNext(j);
                            } else {
                                LOGGER.debug("complete");
                                emitter.onComplete();
                            }

                        })
                .map(i -> {
                    LOGGER.debug("mapping");
                    return "xxx" + i;
                })
                .subscribe(
                        data -> {
                            LOGGER.debug("onNext called: {}", data);
                        },
                        throwable -> {
                            LOGGER.debug("onError called");
                            throw new RuntimeException(String.format("Error in flow, %s", throwable.getMessage()), throwable);
                        },
                        () -> {
                            LOGGER.debug("onComplete called");
                        });

    }

    @Test
    public void testFlowableWithUsing() {

        Flowable<Integer> flowableInt = Flowable
                .using(
                        () -> {
                            LOGGER.debug("Init resource");
                            return new AtomicInteger();
                        },
                        atomicInt -> {
                            LOGGER.debug("Converting resource to flowable");
                            return Flowable.generate(
                                    () -> {
                                        LOGGER.debug("Init state");
                                        return atomicInt;
                                    },
                                    (i, emitter) -> {
                                        int j = i.incrementAndGet();
                                        if (j <= 10) {
                                            LOGGER.debug("emit");
                                            emitter.onNext(j);
                                        } else {
                                            LOGGER.debug("complete");
                                            emitter.onComplete();
                                        }

                                    });
                        },
                        atomicInt -> {
                            LOGGER.debug("Close resource");
                        });

        LOGGER.debug("About to subscribe");

        flowableInt
                .map(i -> {
                    LOGGER.debug("mapping");
                    return "xxx" + i;
                })
                .subscribe(
                        data -> {
                            LOGGER.debug("onNext called: {}", data);
                        },
                        throwable -> {
                            LOGGER.debug("onError called");
                            throw new RuntimeException(String.format("Error in flow, %s", throwable.getMessage()), throwable);
                        },
                        () -> {
                            LOGGER.debug("onComplete called");
                        });

    }
}
