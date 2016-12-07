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
import stroom.statistics.common.RolledUpStatisticEvent;
import stroom.statistics.common.StatisticEvent;
import stroom.statistics.common.exception.StatisticsEventValidationException;
import stroom.statistics.shared.StatisticType;
import stroom.util.config.StroomProperties;
import stroom.util.date.DateUtil;
import stroom.util.logging.LogExecutionTime;
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
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

public class TestSQLStatisticAggregationManager extends AbstractCoreIntegrationTest {
    private static final StroomLogger LOGGER = StroomLogger.getLogger(TestSQLStatisticAggregationManager.class);

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
    private StroomDatabaseInfo stroomDatabaseInfo;

    private static final long STAT_VALUE = 10L;

    private static final String COL_NAME_VAL = "VAL";
    private static final String COL_NAME_CNT = "CT";

    @Override
    public void onBefore() {
        if (!stroomDatabaseInfo.isMysql()) {
            LOGGER.warn("Database is not MySQL, skipping test");
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

    /**
     * Test loads data into stat val src, with stats aged so that there are
     * equal numbers falling into each precision. It then does runs the
     * aggregation process and checks the right data is in stat_val. Following
     * this it does further iterations of loading more data and running
     * aggregation to verify the new data is being merged in and the old data is
     * rolled up. It uses a future 'current time' to replicate aggregation
     * running against stats that have been in stat_val for a while.
     */
    @Test
    public void testCountAggregation() throws SQLException {
        if (!stroomDatabaseInfo.isMysql()) {
            LOGGER.warn("Database is not MySQL, skipping test");
        } else {
            // System.setProperty("stroom.stats.sql.statisticAggregationBatchSize",
            // Integer.toString(10));
            sqlStatisticAggregationManager.setBatchSize(Integer.toString(55));

            final StatisticType statisticType = StatisticType.COUNT;
            // final long startDateMs =
            // DateUtil.parseNormalDateTimeString("2015-01-01T00:00:00.000Z");
            //Use a fixed start date to avoid any oddities caused by the power of 10 rounding
            final long startDateMs = LocalDateTime.of(2016,12,13,11,59,3).toInstant(ZoneOffset.UTC).toEpochMilli();
            final int statNameCount = 4;
            final int timesCount = 10;
            final int numberOfDifferentPrecisions = 4;

            final long expectedCountTotalByPrecision = statNameCount * timesCount
                    * (statisticType.equals(StatisticType.COUNT) ? STAT_VALUE : 1);
            final long expectedValueTotalByPrecision = statNameCount * timesCount * STAT_VALUE;

            final long expectedCountTotal = expectedCountTotalByPrecision * numberOfDifferentPrecisions;
            final long expectedValueTotal = expectedValueTotalByPrecision * numberOfDifferentPrecisions;

            final LogExecutionTime time = new LogExecutionTime();

            loadData(startDateMs, statNameCount, timesCount, statisticType);

            LOGGER.info("First aggregation run");
            LOGGER.info("startDate: " + DateUtil.createNormalDateTimeString(startDateMs));
            runAggregation(startDateMs);

            Assert.assertEquals(expectedCountTotal, getAggregateTotal(COL_NAME_CNT));
            Assert.assertEquals(expectedValueTotal, getAggregateTotal(COL_NAME_VAL));

            Assert.assertEquals(expectedValueTotalByPrecision,
                    getAggregateByPrecision(COL_NAME_VAL, SQLStatisticAggregationTransactionHelper.DEFAULT_PRECISION));

            Assert.assertEquals(expectedValueTotalByPrecision,
                    getAggregateByPrecision(COL_NAME_VAL, SQLStatisticAggregationTransactionHelper.HOUR_PRECISION));

            Assert.assertEquals(expectedValueTotalByPrecision,
                    getAggregateByPrecision(COL_NAME_VAL, SQLStatisticAggregationTransactionHelper.DAY_PRECISION));

            Assert.assertEquals(expectedValueTotalByPrecision,
                    getAggregateByPrecision(COL_NAME_VAL, SQLStatisticAggregationTransactionHelper.MONTH_PRECISION));

            Assert.assertEquals(expectedCountTotalByPrecision,
                    getAggregateByPrecision(COL_NAME_CNT, SQLStatisticAggregationTransactionHelper.DEFAULT_PRECISION));

            Assert.assertEquals(expectedCountTotalByPrecision,
                    getAggregateByPrecision(COL_NAME_CNT, SQLStatisticAggregationTransactionHelper.HOUR_PRECISION));

            Assert.assertEquals(expectedCountTotalByPrecision,
                    getAggregateByPrecision(COL_NAME_CNT, SQLStatisticAggregationTransactionHelper.DAY_PRECISION));

            Assert.assertEquals(expectedCountTotalByPrecision,
                    getAggregateByPrecision(COL_NAME_CNT, SQLStatisticAggregationTransactionHelper.MONTH_PRECISION));

            LOGGER.info("run aggregation again with no new data in SVS");
            runAggregation(startDateMs);

            Assert.assertEquals(expectedCountTotal, getAggregateTotal(COL_NAME_CNT));
            Assert.assertEquals(expectedValueTotal, getAggregateTotal(COL_NAME_VAL));

            Assert.assertEquals(expectedValueTotalByPrecision,
                    getAggregateByPrecision(COL_NAME_VAL, SQLStatisticAggregationTransactionHelper.DEFAULT_PRECISION));

            Assert.assertEquals(expectedValueTotalByPrecision,
                    getAggregateByPrecision(COL_NAME_VAL, SQLStatisticAggregationTransactionHelper.HOUR_PRECISION));

            Assert.assertEquals(expectedValueTotalByPrecision,
                    getAggregateByPrecision(COL_NAME_VAL, SQLStatisticAggregationTransactionHelper.DAY_PRECISION));

            Assert.assertEquals(expectedValueTotalByPrecision,
                    getAggregateByPrecision(COL_NAME_VAL, SQLStatisticAggregationTransactionHelper.MONTH_PRECISION));

            Assert.assertEquals(expectedCountTotalByPrecision,
                    getAggregateByPrecision(COL_NAME_CNT, SQLStatisticAggregationTransactionHelper.DEFAULT_PRECISION));

            Assert.assertEquals(expectedCountTotalByPrecision,
                    getAggregateByPrecision(COL_NAME_CNT, SQLStatisticAggregationTransactionHelper.HOUR_PRECISION));

            Assert.assertEquals(expectedCountTotalByPrecision,
                    getAggregateByPrecision(COL_NAME_CNT, SQLStatisticAggregationTransactionHelper.DAY_PRECISION));

            Assert.assertEquals(expectedCountTotalByPrecision,
                    getAggregateByPrecision(COL_NAME_CNT, SQLStatisticAggregationTransactionHelper.MONTH_PRECISION));

            LOGGER.info(
                    "run aggregation again but pretend we are 2hrs in the future so it rolls up the zero precision");
            long futureDateMs = startDateMs + TimeUnit.HOURS.toMillis(2);
            LOGGER.info("futureDate: " + DateUtil.createNormalDateTimeString(futureDateMs));
            // load more data into each precision so it has to merge existing
            // and roll up existing default moves to hour one new of each
            // precision
            loadData(futureDateMs, statNameCount, timesCount, statisticType);
            runAggregation(futureDateMs);

            Assert.assertEquals(expectedCountTotal * 2, getAggregateTotal(COL_NAME_CNT));
            Assert.assertEquals(expectedValueTotal * 2, getAggregateTotal(COL_NAME_VAL));

            Assert.assertEquals(expectedValueTotalByPrecision * 1,
                    getAggregateByPrecision(COL_NAME_VAL, SQLStatisticAggregationTransactionHelper.DEFAULT_PRECISION));

            Assert.assertEquals(expectedValueTotalByPrecision * 3,
                    getAggregateByPrecision(COL_NAME_VAL, SQLStatisticAggregationTransactionHelper.HOUR_PRECISION));

            Assert.assertEquals(expectedValueTotalByPrecision * 2,
                    getAggregateByPrecision(COL_NAME_VAL, SQLStatisticAggregationTransactionHelper.DAY_PRECISION));

            Assert.assertEquals(expectedValueTotalByPrecision * 2,
                    getAggregateByPrecision(COL_NAME_VAL, SQLStatisticAggregationTransactionHelper.MONTH_PRECISION));

            Assert.assertEquals(expectedCountTotalByPrecision * 1,
                    getAggregateByPrecision(COL_NAME_CNT, SQLStatisticAggregationTransactionHelper.DEFAULT_PRECISION));

            Assert.assertEquals(expectedCountTotalByPrecision * 3,
                    getAggregateByPrecision(COL_NAME_CNT, SQLStatisticAggregationTransactionHelper.HOUR_PRECISION));

            Assert.assertEquals(expectedCountTotalByPrecision * 2,
                    getAggregateByPrecision(COL_NAME_CNT, SQLStatisticAggregationTransactionHelper.DAY_PRECISION));

            Assert.assertEquals(expectedCountTotalByPrecision * 2,
                    getAggregateByPrecision(COL_NAME_CNT, SQLStatisticAggregationTransactionHelper.MONTH_PRECISION));

            LOGGER.info("run aggregation again but pretend we are 2days in the future");
            futureDateMs = startDateMs + TimeUnit.DAYS.toMillis(2);
            LOGGER.info("futureDate: " + DateUtil.createNormalDateTimeString(futureDateMs));
            // load more data into each precision so it has to merge existing
            // and roll up one new in each precision one default from above
            // moves into day three hour from above move into day already two in
            // day
            loadData(futureDateMs, statNameCount, timesCount, statisticType);
            runAggregation(futureDateMs);

            Assert.assertEquals(expectedCountTotal * 3, getAggregateTotal(COL_NAME_CNT));
            Assert.assertEquals(expectedValueTotal * 3, getAggregateTotal(COL_NAME_VAL));

            Assert.assertEquals(expectedValueTotalByPrecision * 1,
                    getAggregateByPrecision(COL_NAME_VAL, SQLStatisticAggregationTransactionHelper.DEFAULT_PRECISION));

            Assert.assertEquals(expectedValueTotalByPrecision * 1,
                    getAggregateByPrecision(COL_NAME_VAL, SQLStatisticAggregationTransactionHelper.HOUR_PRECISION));

            Assert.assertEquals(expectedValueTotalByPrecision * 7,
                    getAggregateByPrecision(COL_NAME_VAL, SQLStatisticAggregationTransactionHelper.DAY_PRECISION));

            Assert.assertEquals(expectedValueTotalByPrecision * 3,
                    getAggregateByPrecision(COL_NAME_VAL, SQLStatisticAggregationTransactionHelper.MONTH_PRECISION));

            Assert.assertEquals(expectedCountTotalByPrecision * 1,
                    getAggregateByPrecision(COL_NAME_CNT, SQLStatisticAggregationTransactionHelper.DEFAULT_PRECISION));

            Assert.assertEquals(expectedCountTotalByPrecision * 1,
                    getAggregateByPrecision(COL_NAME_CNT, SQLStatisticAggregationTransactionHelper.HOUR_PRECISION));

            Assert.assertEquals(expectedCountTotalByPrecision * 7,
                    getAggregateByPrecision(COL_NAME_CNT, SQLStatisticAggregationTransactionHelper.DAY_PRECISION));

            Assert.assertEquals(expectedCountTotalByPrecision * 3,
                    getAggregateByPrecision(COL_NAME_CNT, SQLStatisticAggregationTransactionHelper.MONTH_PRECISION));

            LOGGER.info("run aggregation again but pretend we are 32days in the future");
            futureDateMs = startDateMs + TimeUnit.DAYS.toMillis(65);
            LOGGER.info("futureDate: " + DateUtil.createNormalDateTimeString(futureDateMs));
            // load more data into each precision so it has to merge existing
            // and roll up one new in each precision existing default moves to
            // hour existing hour moves to day
            loadData(futureDateMs, statNameCount, timesCount, statisticType);
            runAggregation(futureDateMs);

            Assert.assertEquals(expectedCountTotal * 4, getAggregateTotal(COL_NAME_CNT));
            Assert.assertEquals(expectedValueTotal * 4, getAggregateTotal(COL_NAME_VAL));

            Assert.assertEquals(expectedValueTotalByPrecision * 1,
                    getAggregateByPrecision(COL_NAME_VAL, SQLStatisticAggregationTransactionHelper.DEFAULT_PRECISION));

            Assert.assertEquals(expectedValueTotalByPrecision * 1,
                    getAggregateByPrecision(COL_NAME_VAL, SQLStatisticAggregationTransactionHelper.HOUR_PRECISION));

            Assert.assertEquals(expectedValueTotalByPrecision * 2,
                    getAggregateByPrecision(COL_NAME_VAL, SQLStatisticAggregationTransactionHelper.DAY_PRECISION));

            Assert.assertEquals(expectedValueTotalByPrecision * 12,
                    getAggregateByPrecision(COL_NAME_VAL, SQLStatisticAggregationTransactionHelper.MONTH_PRECISION));

            Assert.assertEquals(expectedCountTotalByPrecision * 1,
                    getAggregateByPrecision(COL_NAME_CNT, SQLStatisticAggregationTransactionHelper.DEFAULT_PRECISION));

            Assert.assertEquals(expectedCountTotalByPrecision * 1,
                    getAggregateByPrecision(COL_NAME_CNT, SQLStatisticAggregationTransactionHelper.HOUR_PRECISION));

            Assert.assertEquals(expectedCountTotalByPrecision * 2,
                    getAggregateByPrecision(COL_NAME_CNT, SQLStatisticAggregationTransactionHelper.DAY_PRECISION));

            Assert.assertEquals(expectedCountTotalByPrecision * 12,
                    getAggregateByPrecision(COL_NAME_CNT, SQLStatisticAggregationTransactionHelper.MONTH_PRECISION));

            LOGGER.info("run aggregation again with no new data so day data can roll up to month");
            LOGGER.info("futureDate: " + DateUtil.createNormalDateTimeString(futureDateMs));
            runAggregation(futureDateMs);

            Assert.assertEquals(expectedCountTotal * 4, getAggregateTotal(COL_NAME_CNT));
            Assert.assertEquals(expectedValueTotal * 4, getAggregateTotal(COL_NAME_VAL));

            Assert.assertEquals(expectedValueTotalByPrecision * 1,
                    getAggregateByPrecision(COL_NAME_VAL, SQLStatisticAggregationTransactionHelper.DEFAULT_PRECISION));

            Assert.assertEquals(expectedValueTotalByPrecision * 1,
                    getAggregateByPrecision(COL_NAME_VAL, SQLStatisticAggregationTransactionHelper.HOUR_PRECISION));

            Assert.assertEquals(expectedValueTotalByPrecision * 1,
                    getAggregateByPrecision(COL_NAME_VAL, SQLStatisticAggregationTransactionHelper.DAY_PRECISION));

            Assert.assertEquals(expectedValueTotalByPrecision * 13,
                    getAggregateByPrecision(COL_NAME_VAL, SQLStatisticAggregationTransactionHelper.MONTH_PRECISION));

            Assert.assertEquals(expectedCountTotalByPrecision * 1,
                    getAggregateByPrecision(COL_NAME_CNT, SQLStatisticAggregationTransactionHelper.DEFAULT_PRECISION));

            Assert.assertEquals(expectedCountTotalByPrecision * 1,
                    getAggregateByPrecision(COL_NAME_CNT, SQLStatisticAggregationTransactionHelper.HOUR_PRECISION));

            Assert.assertEquals(expectedCountTotalByPrecision * 1,
                    getAggregateByPrecision(COL_NAME_CNT, SQLStatisticAggregationTransactionHelper.DAY_PRECISION));

            Assert.assertEquals(expectedCountTotalByPrecision * 13,
                    getAggregateByPrecision(COL_NAME_CNT, SQLStatisticAggregationTransactionHelper.MONTH_PRECISION));

            LOGGER.info("Test ran in %s", time);
        }
    }

    @Test
    public void testValueAggregation() throws SQLException {
        if (!stroomDatabaseInfo.isMysql()) {
            LOGGER.warn("Database is not MySQL, skipping test");
        } else {
            final StatisticType statisticType = StatisticType.VALUE;
            // final long startDateMs =
            // DateUtil.parseNormalDateTimeString("2015-01-01T00:00:00.000Z");
            //Use a fixed start date to avoid any oddities caused by the power of 10 rounding
            final long startDateMs = LocalDateTime.of(2016,12,13,11,59,3).toInstant(ZoneOffset.UTC).toEpochMilli();
            final int statNameCount = 4;
            final int timesCount = 100;
            final int numberOfDifferentPrecisions = 3;

            final long expectedCountTotalByPrecision = statNameCount * timesCount
                    * (statisticType.equals(StatisticType.COUNT) ? STAT_VALUE : 1);
            final long expectedValueTotalByPrecision = statNameCount * timesCount * STAT_VALUE;

            final long expectedCountTotal = expectedCountTotalByPrecision * numberOfDifferentPrecisions;
            final long expectedValueTotal = expectedValueTotalByPrecision * numberOfDifferentPrecisions;

            final LogExecutionTime time = new LogExecutionTime();

            loadData(startDateMs, statNameCount, timesCount, statisticType);

            LOGGER.info("First aggregation run");
            LOGGER.info("startDate: " + DateUtil.createNormalDateTimeString(startDateMs));
            runAggregation(startDateMs);

            Assert.assertEquals(expectedCountTotal, getAggregateTotal(COL_NAME_CNT));
            Assert.assertEquals(expectedValueTotal, getAggregateTotal(COL_NAME_VAL));

            Assert.assertEquals(expectedValueTotalByPrecision,
                    getAggregateByPrecision(COL_NAME_VAL, SQLStatisticAggregationTransactionHelper.DEFAULT_PRECISION));

            Assert.assertEquals(expectedValueTotalByPrecision,
                    getAggregateByPrecision(COL_NAME_VAL, SQLStatisticAggregationTransactionHelper.DAY_PRECISION));

            Assert.assertEquals(expectedValueTotalByPrecision,
                    getAggregateByPrecision(COL_NAME_VAL, SQLStatisticAggregationTransactionHelper.MONTH_PRECISION));

            Assert.assertEquals(expectedCountTotalByPrecision,
                    getAggregateByPrecision(COL_NAME_CNT, SQLStatisticAggregationTransactionHelper.DEFAULT_PRECISION));

            Assert.assertEquals(expectedCountTotalByPrecision,
                    getAggregateByPrecision(COL_NAME_CNT, SQLStatisticAggregationTransactionHelper.DAY_PRECISION));

            Assert.assertEquals(expectedCountTotalByPrecision,
                    getAggregateByPrecision(COL_NAME_CNT, SQLStatisticAggregationTransactionHelper.MONTH_PRECISION));

            LOGGER.info("run aggregation again with no new data in SVS");
            runAggregation(startDateMs);

            Assert.assertEquals(expectedCountTotal, getAggregateTotal(COL_NAME_CNT));
            Assert.assertEquals(expectedValueTotal, getAggregateTotal(COL_NAME_VAL));

            Assert.assertEquals(expectedValueTotalByPrecision,
                    getAggregateByPrecision(COL_NAME_VAL, SQLStatisticAggregationTransactionHelper.DEFAULT_PRECISION));

            Assert.assertEquals(expectedValueTotalByPrecision,
                    getAggregateByPrecision(COL_NAME_VAL, SQLStatisticAggregationTransactionHelper.DAY_PRECISION));

            Assert.assertEquals(expectedValueTotalByPrecision,
                    getAggregateByPrecision(COL_NAME_VAL, SQLStatisticAggregationTransactionHelper.MONTH_PRECISION));

            Assert.assertEquals(expectedCountTotalByPrecision,
                    getAggregateByPrecision(COL_NAME_CNT, SQLStatisticAggregationTransactionHelper.DEFAULT_PRECISION));

            Assert.assertEquals(expectedCountTotalByPrecision,
                    getAggregateByPrecision(COL_NAME_CNT, SQLStatisticAggregationTransactionHelper.DAY_PRECISION));

            Assert.assertEquals(expectedCountTotalByPrecision,
                    getAggregateByPrecision(COL_NAME_CNT, SQLStatisticAggregationTransactionHelper.MONTH_PRECISION));

            LOGGER.info("run aggregation again but pretend we are 2days in the future");
            long futureDateMs = startDateMs + TimeUnit.DAYS.toMillis(2);
            LOGGER.info("futureDate: " + DateUtil.createNormalDateTimeString(futureDateMs));
            // load more data into each precision so it has to merge existing
            // and roll up one new in each precision one default from above
            // moves into day
            loadData(futureDateMs, statNameCount, timesCount, statisticType);
            runAggregation(futureDateMs);

            Assert.assertEquals(expectedCountTotal * 2, getAggregateTotal(COL_NAME_CNT));
            Assert.assertEquals(expectedValueTotal * 2, getAggregateTotal(COL_NAME_VAL));

            Assert.assertEquals(expectedValueTotalByPrecision * 1,
                    getAggregateByPrecision(COL_NAME_VAL, SQLStatisticAggregationTransactionHelper.DEFAULT_PRECISION));

            Assert.assertEquals(expectedValueTotalByPrecision * 3,
                    getAggregateByPrecision(COL_NAME_VAL, SQLStatisticAggregationTransactionHelper.DAY_PRECISION));

            Assert.assertEquals(expectedValueTotalByPrecision * 2,
                    getAggregateByPrecision(COL_NAME_VAL, SQLStatisticAggregationTransactionHelper.MONTH_PRECISION));

            Assert.assertEquals(expectedCountTotalByPrecision * 1,
                    getAggregateByPrecision(COL_NAME_CNT, SQLStatisticAggregationTransactionHelper.DEFAULT_PRECISION));

            Assert.assertEquals(expectedCountTotalByPrecision * 3,
                    getAggregateByPrecision(COL_NAME_CNT, SQLStatisticAggregationTransactionHelper.DAY_PRECISION));

            Assert.assertEquals(expectedCountTotalByPrecision * 2,
                    getAggregateByPrecision(COL_NAME_CNT, SQLStatisticAggregationTransactionHelper.MONTH_PRECISION));

            LOGGER.info("run aggregation again but pretend we are 32days in the future");
            futureDateMs = startDateMs + TimeUnit.DAYS.toMillis(65);
            LOGGER.info("futureDate: " + DateUtil.createNormalDateTimeString(futureDateMs));
            // load more data into each precision so it has to merge existing
            // and roll up one new in each precision existing default moves to
            // hour existing hour moves to day
            loadData(futureDateMs, statNameCount, timesCount, statisticType);
            runAggregation(futureDateMs);

            Assert.assertEquals(expectedCountTotal * 3, getAggregateTotal(COL_NAME_CNT));
            Assert.assertEquals(expectedValueTotal * 3, getAggregateTotal(COL_NAME_VAL));

            Assert.assertEquals(expectedValueTotalByPrecision * 1,
                    getAggregateByPrecision(COL_NAME_VAL, SQLStatisticAggregationTransactionHelper.DEFAULT_PRECISION));

            Assert.assertEquals(expectedValueTotalByPrecision * 1,
                    getAggregateByPrecision(COL_NAME_VAL, SQLStatisticAggregationTransactionHelper.DAY_PRECISION));

            Assert.assertEquals(expectedValueTotalByPrecision * 7,
                    getAggregateByPrecision(COL_NAME_VAL, SQLStatisticAggregationTransactionHelper.MONTH_PRECISION));

            Assert.assertEquals(expectedCountTotalByPrecision * 1,
                    getAggregateByPrecision(COL_NAME_CNT, SQLStatisticAggregationTransactionHelper.DEFAULT_PRECISION));

            Assert.assertEquals(expectedCountTotalByPrecision * 1,
                    getAggregateByPrecision(COL_NAME_CNT, SQLStatisticAggregationTransactionHelper.DAY_PRECISION));

            Assert.assertEquals(expectedCountTotalByPrecision * 7,
                    getAggregateByPrecision(COL_NAME_CNT, SQLStatisticAggregationTransactionHelper.MONTH_PRECISION));

            LOGGER.info("run aggregation again with no new data so day data can roll up to month");
            LOGGER.info("futureDate: " + DateUtil.createNormalDateTimeString(futureDateMs));
            runAggregation(futureDateMs);

            Assert.assertEquals(expectedCountTotal * 3, getAggregateTotal(COL_NAME_CNT));
            Assert.assertEquals(expectedValueTotal * 3, getAggregateTotal(COL_NAME_VAL));

            Assert.assertEquals(expectedValueTotalByPrecision * 1,
                    getAggregateByPrecision(COL_NAME_VAL, SQLStatisticAggregationTransactionHelper.DEFAULT_PRECISION));

            Assert.assertEquals(expectedValueTotalByPrecision * 1,
                    getAggregateByPrecision(COL_NAME_VAL, SQLStatisticAggregationTransactionHelper.DAY_PRECISION));

            Assert.assertEquals(expectedValueTotalByPrecision * 7,
                    getAggregateByPrecision(COL_NAME_VAL, SQLStatisticAggregationTransactionHelper.MONTH_PRECISION));

            Assert.assertEquals(expectedCountTotalByPrecision * 1,
                    getAggregateByPrecision(COL_NAME_CNT, SQLStatisticAggregationTransactionHelper.DEFAULT_PRECISION));

            Assert.assertEquals(expectedCountTotalByPrecision * 1,
                    getAggregateByPrecision(COL_NAME_CNT, SQLStatisticAggregationTransactionHelper.DAY_PRECISION));

            Assert.assertEquals(expectedCountTotalByPrecision * 7,
                    getAggregateByPrecision(COL_NAME_CNT, SQLStatisticAggregationTransactionHelper.MONTH_PRECISION));

            LOGGER.info("Test ran in %s", time);
        }
    }

    @Test
    public void testDeletingOldStats() throws SQLException {
        if (!stroomDatabaseInfo.isMysql()) {
            LOGGER.warn("Database is not MySQL, skipping test");
        } else {
            final StatisticType statisticType = StatisticType.VALUE;
            //Use a fixed start date to avoid any oddities caused by the power of 10 rounding
            final long startDateMs = LocalDateTime.of(2016,12,13,11,59,3).toInstant(ZoneOffset.UTC).toEpochMilli();
            //the number of different satst names to use in the test
            final int statNameCount = 4;
            //the number of different data points per stat name
            final int timesCount = 100;
            final int numberOfDifferentPrecisions = 3 + 1;

            final long expectedCountTotalByPrecision = statNameCount * timesCount
                    * (statisticType.equals(StatisticType.COUNT) ? STAT_VALUE : 1);
            final long expectedValueTotalByPrecision = statNameCount * timesCount * STAT_VALUE;

            final long expectedCountTotal = expectedCountTotalByPrecision * numberOfDifferentPrecisions;
            final long expectedValueTotal = expectedValueTotalByPrecision * numberOfDifferentPrecisions;

            final LogExecutionTime time = new LogExecutionTime();

            //put the data into SQL_STAT_VAL_SRC
            loadData(startDateMs, statNameCount, timesCount, statisticType);

            final long newStartDate = startDateMs - TimeUnit.DAYS.toMillis(200);
            LOGGER.info("Adding stats working back from: " + DateUtil.createNormalDateTimeString(newStartDate));
            //Put some very old data in to SQL_STAT_VAL_SRC so that it will get deleted but leave behind some of the data loaded above
            fillStatValSrc(newStartDate, statNameCount, timesCount, statisticType);

            LOGGER.info("First aggregation run");
            LOGGER.info("startDate: " + DateUtil.createNormalDateTimeString(startDateMs));
            runAggregation(startDateMs);

            Assert.assertEquals(expectedCountTotal, getAggregateTotal(COL_NAME_CNT));
            Assert.assertEquals(expectedValueTotal, getAggregateTotal(COL_NAME_VAL));

            Assert.assertEquals(expectedValueTotalByPrecision,
                    getAggregateByPrecision(COL_NAME_VAL, SQLStatisticAggregationTransactionHelper.DEFAULT_PRECISION));

            Assert.assertEquals(expectedValueTotalByPrecision,
                    getAggregateByPrecision(COL_NAME_VAL, SQLStatisticAggregationTransactionHelper.DAY_PRECISION));

            Assert.assertEquals(expectedValueTotalByPrecision * 2,
                    getAggregateByPrecision(COL_NAME_VAL, SQLStatisticAggregationTransactionHelper.MONTH_PRECISION));


            Assert.assertEquals(expectedCountTotalByPrecision,
                    getAggregateByPrecision(COL_NAME_CNT, SQLStatisticAggregationTransactionHelper.DEFAULT_PRECISION));

            Assert.assertEquals(expectedCountTotalByPrecision,
                    getAggregateByPrecision(COL_NAME_CNT, SQLStatisticAggregationTransactionHelper.DAY_PRECISION));

            Assert.assertEquals(expectedCountTotalByPrecision * 2,
                    getAggregateByPrecision(COL_NAME_CNT, SQLStatisticAggregationTransactionHelper.MONTH_PRECISION));

            // TODO now move time a long way into future and set the max
            // processing age so stuff gets deleted on next
            // aggregation
            // run aggregation and check the right amount of stats are left

            final long futureDateMs = startDateMs + TimeUnit.DAYS.toMillis(2);

//            StroomProperties.setProperty(SQLStatisticConstants.PROP_KEY_STATS_MAX_PROCESSING_AGE, "30d", StroomProperties.Source.TEST);
            String newPropVal = "30d";
            StroomProperties.setOverrideProperty(SQLStatisticConstants.PROP_KEY_STATS_MAX_PROCESSING_AGE, newPropVal, StroomProperties.Source.TEST);
            Assert.assertEquals(newPropVal, StroomProperties.getProperty(SQLStatisticConstants.PROP_KEY_STATS_MAX_PROCESSING_AGE));

            runAggregation(futureDateMs);

            Assert.assertEquals(0,
                    getAggregateByPrecision(COL_NAME_VAL, SQLStatisticAggregationTransactionHelper.DEFAULT_PRECISION));

            Assert.assertEquals(expectedValueTotalByPrecision * 2,
                    getAggregateByPrecision(COL_NAME_VAL, SQLStatisticAggregationTransactionHelper.DAY_PRECISION));

            Assert.assertEquals(expectedValueTotalByPrecision,
                    getAggregateByPrecision(COL_NAME_VAL, SQLStatisticAggregationTransactionHelper.MONTH_PRECISION));


            Assert.assertEquals(0,
                    getAggregateByPrecision(COL_NAME_CNT, SQLStatisticAggregationTransactionHelper.DEFAULT_PRECISION));

            Assert.assertEquals(expectedCountTotalByPrecision * 2,
                    getAggregateByPrecision(COL_NAME_CNT, SQLStatisticAggregationTransactionHelper.DAY_PRECISION));

            Assert.assertEquals(expectedCountTotalByPrecision,
                    getAggregateByPrecision(COL_NAME_CNT, SQLStatisticAggregationTransactionHelper.MONTH_PRECISION));
        }
    }

    private void loadData(final long startDateMs, final int statNameCount, final int timesCount,
            final StatisticType statisticType) throws SQLException {
        int iteration = 0;

        LOGGER.info("Filling STAT_VAL_SRC");

        // initial load of data just before now
        long newStartDate = startDateMs;
        LOGGER.info("Adding stats working back from: " + DateUtil.createNormalDateTimeString(newStartDate));
        fillStatValSrc(newStartDate, statNameCount, timesCount, statisticType);
        Assert.assertEquals(statNameCount * timesCount * ++iteration,
                getRowCount(SQLStatisticNames.SQL_STATISTIC_VALUE_SOURCE_TABLE_NAME));

        // Value stats doesn't do hour granularity
        if (statisticType.equals(StatisticType.COUNT)) {
            // load of data two hours old
            newStartDate = startDateMs - TimeUnit.HOURS.toMillis(2);
            LOGGER.info("Adding stats working back from: " + DateUtil.createNormalDateTimeString(newStartDate));
            fillStatValSrc(newStartDate, statNameCount, timesCount, statisticType);
            Assert.assertEquals(statNameCount * timesCount * ++iteration,
                    getRowCount(SQLStatisticNames.SQL_STATISTIC_VALUE_SOURCE_TABLE_NAME));
        }

        // load of data 2 days old
        newStartDate = startDateMs - TimeUnit.DAYS.toMillis(2);
        LOGGER.info("Adding stats working back from: " + DateUtil.createNormalDateTimeString(newStartDate));
        fillStatValSrc(newStartDate, statNameCount, timesCount, statisticType);
        Assert.assertEquals(statNameCount * timesCount * ++iteration,
                getRowCount(SQLStatisticNames.SQL_STATISTIC_VALUE_SOURCE_TABLE_NAME));

        // load of data 65 days old
        newStartDate = startDateMs - TimeUnit.DAYS.toMillis(65);
        LOGGER.info("Adding stats working back from: " + DateUtil.createNormalDateTimeString(newStartDate));
        fillStatValSrc(newStartDate, statNameCount, timesCount, statisticType);
        Assert.assertEquals(statNameCount * timesCount * ++iteration,
                getRowCount(SQLStatisticNames.SQL_STATISTIC_VALUE_SOURCE_TABLE_NAME));
    }

    private void fillStatValSrc(final long startDateMs, final int statNameCount, final int timesCount,
            final StatisticType statisticType) throws SQLException {
        final SQLStatisticAggregateMap sqlStatisticAggregateMap = new SQLStatisticAggregateMap();
        final long value = STAT_VALUE;
        final long precision = 0L;

        for (int i = 0; i < statNameCount; i++) {
            final String statName = "stat" + i;
            for (int j = 0; j < timesCount; j++) {
                // make each time 1ms earlier
                final long timeMs = startDateMs - j;
                StatisticEvent statisticEvent;

                if (statisticType.equals(StatisticType.COUNT)) {
                    statisticEvent = new StatisticEvent(timeMs, statName, Collections.emptyList(),
                            value);
                } else {
                    final double valueValue = value;
                    statisticEvent = new StatisticEvent(timeMs, statName, Collections.emptyList(),
                            valueValue);
                }

                final RolledUpStatisticEvent rolledUpStatisticEvent = new RolledUpStatisticEvent(statisticEvent);
                try {
                    sqlStatisticAggregateMap.addRolledUpEvent(rolledUpStatisticEvent, precision);
                } catch (final StatisticsEventValidationException e) {
                    throw new RuntimeException("error", e);
                }
            }
        }

        final SQLStatisticFlushTaskHandler taskHandler = new SQLStatisticFlushTaskHandler(
                sqlStatisticValueBatchSaveService, new MockTaskMonitor());

        final SQLStatisticFlushTask flushTask = new SQLStatisticFlushTask(sqlStatisticAggregateMap);

        taskHandler.exec(flushTask);

    }

    private void runAggregation() throws SQLException {
        runAggregation(System.currentTimeMillis());
    }

    private void runAggregation(final long timeNowMs) throws SQLException {
        sqlStatisticAggregationManager.aggregate(timeNowMs);

        Assert.assertEquals(0, getRowCount(SQLStatisticNames.SQL_STATISTIC_VALUE_SOURCE_TABLE_NAME));

    }

    private Connection getConnection() throws SQLException {
        return cachedSqlDataSource.getConnection();
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

    private int getAggregateByPrecision(final String colName, final byte precision) throws SQLException {
        final Connection connection = getConnection();

        int count;

        try {
            final PreparedStatement preparedStatement = connection.prepareStatement("select sum(" + colName + ") from "
                    + SQLStatisticNames.SQL_STATISTIC_VALUE_TABLE_NAME + " where PRES = " + precision);

            final ResultSet resultSet = preparedStatement.executeQuery();

            resultSet.next();
            count = resultSet.getInt(1);
            resultSet.close();

        } finally {
            ConnectionUtil.close(connection);
        }
        return count;
    }

    private int getAggregateTotal(final String colName) throws SQLException {
        final Connection connection = getConnection();

        int count;

        try {
            final PreparedStatement preparedStatement = connection.prepareStatement(
                    "select sum(" + colName + ") from " + SQLStatisticNames.SQL_STATISTIC_VALUE_TABLE_NAME);

            final ResultSet resultSet = preparedStatement.executeQuery();

            resultSet.next();
            count = resultSet.getInt(1);
            resultSet.close();

        } finally {
            ConnectionUtil.close(connection);
        }
        return count;
    }

    private void deleteRows(final String tableName) throws SQLException {
        final Connection connection = getConnection();

        try {
            final PreparedStatement preparedStatement = connection.prepareStatement("delete from " + tableName);

            preparedStatement.execute();

        } finally {
            ConnectionUtil.close(connection);
        }
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
