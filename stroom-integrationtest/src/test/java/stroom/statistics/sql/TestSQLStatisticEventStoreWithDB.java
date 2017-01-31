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

package stroom.statistics.sql;

import org.junit.Assert;
import org.junit.Test;
import stroom.AbstractCoreIntegrationTest;
import stroom.CommonTestControl;
import stroom.entity.server.util.ConnectionUtil;
import stroom.entity.server.util.StroomDatabaseInfo;
import stroom.query.api.ExpressionBuilder;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionTerm;
import stroom.query.api.ExpressionTerm.Condition;
import stroom.query.api.Query;
import stroom.statistics.common.RolledUpStatisticEvent;
import stroom.statistics.common.StatisticDataPoint;
import stroom.statistics.common.StatisticDataSet;
import stroom.statistics.common.StatisticEvent;
import stroom.statistics.common.StatisticTag;
import stroom.statistics.common.exception.StatisticsEventValidationException;
import stroom.statistics.shared.StatisticRollUpType;
import stroom.statistics.shared.StatisticStoreEntity;
import stroom.util.logging.StroomLogger;
import stroom.util.shared.Monitor;
import stroom.util.shared.TerminateHandler;
import stroom.util.task.TaskMonitor;

import javax.annotation.Resource;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class TestSQLStatisticEventStoreWithDB extends AbstractCoreIntegrationTest {
    private static final StroomLogger LOGGER = StroomLogger.getLogger(TestSQLStatisticEventStoreWithDB.class);
    private static final String STAT_NAME = "MyStat";
    private static final String TAG1 = "Tag1";
    private static final String TAG1_VAL = "Tag1Value1";
    private static final String TAG2 = "Tag2";
    private static final String TAG2_VAL = "Tag2Value2";
    private static final String TAG2_OTHER_VALUE_1 = "Tag2OtherValue1";
    private static final String TAG2_OTHER_VALUE_2 = "Tag2OtherValue2";
    private static final String DATE_RANGE = "2000-01-01T00:00:00.000Z,3000-01-01T00:00:00.000Z";
    @Resource
    private CommonTestControl commonTestControl;
    @Resource
    private DataSource cachedSqlDataSource;
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
        }
    }

    @Test
    public void testSearchStatisticsData_TwoTags() throws SQLException {

        if (!ignoreAllTests) {
            final List<StatisticTag> tags = new ArrayList<>();
            tags.add(new StatisticTag(TAG1, TAG1_VAL));
            tags.add(new StatisticTag(TAG2, TAG2_VAL));

            fillStatValSrc(tags);

            final StatisticDataSet dataSet = doSearch(tags);

            assertDataSetSize(dataSet, 1);

            final StatisticDataPoint dataPoint = dataSet.iterator().next();
            Assert.assertEquals(tags, dataPoint.getTags());
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

            final StatisticDataSet dataSet = doSearch(searchTags, ExpressionOperator.Op.OR);

            assertDataSetSize(dataSet, 2);

            for (final StatisticDataPoint dataPoint : dataSet) {

                final String tag2Value = dataPoint.getTagsAsMap().get(TAG2);

                Assert.assertTrue(tag2Value.equals(TAG2_VAL) || tag2Value.equals(TAG2_OTHER_VALUE_1));
            }
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

            final StatisticDataSet dataSet = doSearch(searchTags);

            assertDataSetSize(dataSet, 1);

            final StatisticDataPoint dataPoint = dataSet.iterator().next();
            Assert.assertEquals(tags, dataPoint.getTags());
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

            final StatisticDataSet dataSet = doSearch(searchTags);

            assertDataSetSize(dataSet, 1);

            final StatisticDataPoint dataPoint = dataSet.iterator().next();
            Assert.assertEquals(tags, dataPoint.getTags());
        }
    }

    private StatisticDataSet doSearch(final List<StatisticTag> searchTags) {
        return doSearch(searchTags, ExpressionOperator.Op.AND);
    }

    private StatisticDataSet doSearch(final List<StatisticTag> searchTags, final ExpressionOperator.Op op) {
        final ExpressionBuilder rootOperator = new ExpressionBuilder(op);
        rootOperator
                .addTerm(StatisticStoreEntity.FIELD_NAME_DATE_TIME, Condition.BETWEEN, DATE_RANGE);

        for (final StatisticTag tag : searchTags) {
            rootOperator.addTerm(tag.getTag(), Condition.EQUALS, tag.getValue());
        }

        final Query query = new Query(null, rootOperator.build(), null);
        final StatisticStoreEntity dataSource = new StatisticStoreEntity();
        dataSource.setName(STAT_NAME);
        dataSource.setRollUpType(StatisticRollUpType.NONE);

        return sqlStatisticEventStore.searchStatisticsData(query, dataSource);
    }

    private void assertDataSetSize(final StatisticDataSet dataSet, final int size) {

        Assert.assertEquals(size, dataSet.size());
        Assert.assertEquals(STAT_NAME, dataSet.getStatisticName());

        System.out.println(dataSet.toString());
        for (final StatisticDataPoint dataPoint : dataSet) {
            System.out.println(dataPoint.toString());
        }
    }

    private RolledUpStatisticEvent buildStatisticEvent(final List<StatisticTag> tags) {

        final long value = 1L;

        final long timeMs = System.currentTimeMillis();

        final StatisticEvent statisticEvent = new StatisticEvent(timeMs, STAT_NAME, tags, value);

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
                sqlStatisticValueBatchSaveService, new MockTaskMonitor());

        final SQLStatisticFlushTask flushTask = new SQLStatisticFlushTask(sqlStatisticAggregateMap);

        taskHandler.exec(flushTask);

        sqlStatisticAggregationManager.aggregate();

        Assert.assertEquals(3, getRowCount(SQLStatisticNames.SQL_STATISTIC_VALUE_TABLE_NAME));
        Assert.assertEquals(3, getRowCount(SQLStatisticNames.SQL_STATISTIC_KEY_TABLE_NAME));
    }

    private int getRowCount(final String tableName) throws SQLException {
        final Connection connection = getConnection();

        int count;

        try {

            final PreparedStatement preparedStatement = connection
                    .prepareStatement("select count(*) from " + tableName);

            final ResultSet resultSet = preparedStatement.executeQuery();

            resultSet.next();
            count = resultSet.getInt(1);
            resultSet.close();

        } finally {

            ConnectionUtil.close(connection);
        }
        return count;
    }

    private Connection getConnection() throws SQLException {
        return cachedSqlDataSource.getConnection();
    }

    private static class MockTaskMonitor implements TaskMonitor {
        private static final long serialVersionUID = -8415095958756818805L;

        @Override
        public Monitor getParent() {
            // TODO Auto-generated method stub
            return null;
        }

        @Override
        public void addTerminateHandler(final TerminateHandler handler) {
            // TODO Auto-generated method stub

        }

        @Override
        public void terminate() {
            // TODO Auto-generated method stub

        }

        @Override
        public boolean isTerminated() {

            return false;
        }

        @Override
        public String getInfo() {
            // TODO Auto-generated method stub
            return null;
        }

        @Override
        public void info(final Object... args) {
            // do nothing

        }
    }
}
