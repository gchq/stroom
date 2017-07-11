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

package stroom.statistics.server.sql;

import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.AbstractCoreIntegrationTest;
import stroom.entity.server.util.ConnectionUtil;
import stroom.entity.server.util.StroomDatabaseInfo;
import stroom.statistics.server.sql.exception.StatisticsEventValidationException;
import stroom.statistics.server.sql.rollup.RolledUpStatisticEvent;
import stroom.util.shared.Monitor;
import stroom.util.shared.TerminateHandler;
import stroom.util.task.TaskMonitor;

import javax.annotation.Resource;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class TestSQLStatisticFlushTaskHandler extends AbstractCoreIntegrationTest {
    @Resource
    private DataSource statisticsDataSource;
    @Resource
    private SQLStatisticValueBatchSaveService sqlStatisticValueBatchSaveService;

    @Resource
    private SQLStatisticAggregationManager sqlStatisticAggregationManager;

    @Resource
    private StroomDatabaseInfo stroomDatabaseInfo;

    private static final Logger LOGGER = LoggerFactory.getLogger(TestSQLStatisticFlushTaskHandler.class);

    @Test(expected = StatisticsEventValidationException.class)
    public void testExec_tenGoodRowsTwoBad() throws Exception {
        if (!stroomDatabaseInfo.isMysql()) {
            LOGGER.warn("Database is not MySQL, skipping test");
            throw new StatisticsEventValidationException("Expected");
        } else {
            deleteRows();

            Assert.assertEquals(0, getRowCount());

            final SQLStatisticFlushTaskHandler taskHandler = new SQLStatisticFlushTaskHandler(
                    sqlStatisticValueBatchSaveService, new MockTaskMonitor());

            final SQLStatisticAggregateMap aggregateMap = new SQLStatisticAggregateMap();

            aggregateMap.addRolledUpEvent(buildGoodEvent(1), 1000);
            aggregateMap.addRolledUpEvent(buildBadEvent(1), 1000);
            aggregateMap.addRolledUpEvent(buildGoodEvent(2), 1000);
            aggregateMap.addRolledUpEvent(buildBadEvent(2), 1000);
            aggregateMap.addRolledUpEvent(buildGoodEvent(3), 1000);
            aggregateMap.addRolledUpEvent(buildGoodEvent(4), 1000);
            aggregateMap.addRolledUpEvent(buildGoodEvent(5), 1000);
            aggregateMap.addRolledUpEvent(buildGoodEvent(6), 1000);
            aggregateMap.addRolledUpEvent(buildGoodEvent(7), 1000);
            aggregateMap.addRolledUpEvent(buildGoodEvent(8), 1000);

            final SQLStatisticFlushTask flushTask = new SQLStatisticFlushTask(aggregateMap);

            taskHandler.exec(flushTask);
        }
    }

    @Test
    public void testExec_threeGoodRows() throws Exception {
        if (!stroomDatabaseInfo.isMysql()) {
            LOGGER.warn("Database is not MySQL, skipping test");
        } else {
            deleteRows();

            Assert.assertEquals(0, getRowCount());

            final SQLStatisticFlushTaskHandler taskHandler = new SQLStatisticFlushTaskHandler(
                    sqlStatisticValueBatchSaveService, new MockTaskMonitor());

            final SQLStatisticAggregateMap aggregateMap = new SQLStatisticAggregateMap();

            aggregateMap.addRolledUpEvent(buildGoodEvent(1), 1000);
            aggregateMap.addRolledUpEvent(buildGoodEvent(2), 1000);
            aggregateMap.addRolledUpEvent(buildGoodEvent(3), 1000);

            final SQLStatisticFlushTask flushTask = new SQLStatisticFlushTask(aggregateMap);

            taskHandler.exec(flushTask);

            Assert.assertEquals(3, getRowCount());
        }
    }

    @Test(expected = StatisticsEventValidationException.class)
    public void testExec_twoBadRows() throws Exception {
        if (!stroomDatabaseInfo.isMysql()) {
            LOGGER.warn("Database is not MySQL, skipping test");
            throw new StatisticsEventValidationException("Expected");
        } else {
            deleteRows();

            Assert.assertEquals(0, getRowCount());

            final SQLStatisticFlushTaskHandler taskHandler = new SQLStatisticFlushTaskHandler(
                    sqlStatisticValueBatchSaveService, new MockTaskMonitor());

            final SQLStatisticAggregateMap aggregateMap = new SQLStatisticAggregateMap();

            aggregateMap.addRolledUpEvent(buildBadEvent(1), 1000);
            aggregateMap.addRolledUpEvent(buildBadEvent(2), 1000);

            final SQLStatisticFlushTask flushTask = new SQLStatisticFlushTask(aggregateMap);

            taskHandler.exec(flushTask);
        }
    }

    @Test
    public void testExec_hugeNumbers() throws Exception {
        if (!stroomDatabaseInfo.isMysql()) {
            LOGGER.warn("Database is not MySQL, skipping test");
        } else {
            deleteRows();

            Assert.assertEquals(0, getRowCount());

            final SQLStatisticFlushTaskHandler taskHandler = new SQLStatisticFlushTaskHandler(
                    sqlStatisticValueBatchSaveService, new MockTaskMonitor());

            final SQLStatisticAggregateMap aggregateMap = new SQLStatisticAggregateMap();

            aggregateMap.addRolledUpEvent(buildCustomCountEvent(1, 66666666666L), 1000);

            final SQLStatisticFlushTask flushTask = new SQLStatisticFlushTask(aggregateMap);

            taskHandler.exec(flushTask);

            Assert.assertEquals(1, getRowCount());

            sqlStatisticAggregationManager.aggregate(System.currentTimeMillis());

            aggregateMap.addRolledUpEvent(buildCustomCountEvent(1, 66666666666L), 1000);

            final SQLStatisticFlushTask flushTask2 = new SQLStatisticFlushTask(aggregateMap);

            taskHandler.exec(flushTask2);

            Assert.assertEquals(1, getRowCount());

            sqlStatisticAggregationManager.aggregate(System.currentTimeMillis());

            Assert.assertEquals(0, getRowCount());
        }
    }

    private RolledUpStatisticEvent buildGoodEvent(final int id) {
        final StatisticEvent goodEvent = StatisticEvent.createCount(123, "shortName" + id, null, 1);
        return new RolledUpStatisticEvent(goodEvent);
    }

    private RolledUpStatisticEvent buildBadEvent(final int id) {
        final StringBuilder sb = new StringBuilder(2010);
        for (int i = 0; i < 201; i++) {
            sb.append("0123456789");
        }

        final StatisticEvent badEvent = StatisticEvent.createCount(123, sb.toString() + id, null, 1);

        return new RolledUpStatisticEvent(badEvent);
    }

    private RolledUpStatisticEvent buildCustomCountEvent(final int id, final long countValue) {
        final StatisticEvent goodEvent = StatisticEvent.createCount(123, "shortName" + id, null, countValue);

        return new RolledUpStatisticEvent(goodEvent);
    }

    private int getRowCount() throws SQLException {
        final Connection connection = getConnection();

        int count;

        try {
            final PreparedStatement preparedStatement = connection.prepareStatement(
                    "select count(*) from " + SQLStatisticNames.SQL_STATISTIC_VALUE_SOURCE_TABLE_NAME);

            final ResultSet resultSet = preparedStatement.executeQuery();

            resultSet.next();
            count = resultSet.getInt(1);
            resultSet.close();

        } finally {
            ConnectionUtil.close(connection);
        }
        return count;
    }

    private void deleteRows() throws SQLException {
        final Connection connection = getConnection();

        try {
            final PreparedStatement preparedStatement = connection
                    .prepareStatement("delete from " + SQLStatisticNames.SQL_STATISTIC_VALUE_SOURCE_TABLE_NAME);

            preparedStatement.execute();

        } finally {
            ConnectionUtil.close(connection);
        }
    }

    private Connection getConnection() throws SQLException {
        return statisticsDataSource.getConnection();
    }

    private static class MockTaskMonitor implements TaskMonitor {
        private static final long serialVersionUID = -8415095958756818805L;

        @Override
        public Monitor getParent() {
            return null;
        }

        @Override
        public void addTerminateHandler(final TerminateHandler handler) {
        }

        @Override
        public void terminate() {
        }

        @Override
        public boolean isTerminated() {
            return false;
        }

        @Override
        public String getInfo() {
            return null;
        }

        @Override
        public void info(final Object... args) {
            // do nothing
        }
    }
}
