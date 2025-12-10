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

import stroom.security.api.SecurityContext;
import stroom.statistics.impl.sql.exception.StatisticsEventValidationException;
import stroom.statistics.impl.sql.rollup.RolledUpStatisticEvent;
import stroom.statistics.impl.sql.shared.StatisticType;
import stroom.task.api.TaskContextFactory;
import stroom.test.AbstractStatisticsCoreIntegrationTest;
import stroom.util.logging.LogExecutionTime;
import stroom.util.time.StroomDuration;

import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

class TestSQLStatisticAggregationManager extends AbstractStatisticsCoreIntegrationTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestSQLStatisticAggregationManager.class);
    private static final long STAT_VALUE = 10L;
    private static final String COL_NAME_VAL = "VAL";
    private static final String COL_NAME_CNT = "CT";

    @Inject
    private SQLStatisticsDbConnProvider sqlStatisticsDbConnProvider;
    @Inject
    private SQLStatisticValueBatchSaveService sqlStatisticValueBatchSaveService;
    @Inject
    private SQLStatisticAggregationManager sqlStatisticAggregationManager;
    @Inject
    private SQLStatisticAggregationTransactionHelper sqlStatisticAggregationTransactionHelper;
    @Inject
    private SecurityContext securityContext;
    @Inject
    private SQLStatisticsConfig sqlStatisticsConfig;
    @Inject
    private TaskContextFactory taskContextFactory;

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
    void testCountAggregation() throws SQLException {

        final StatisticType statisticType = StatisticType.COUNT;
        // final long startDateMs =
        // DateUtil.parseNormalDateTimeString("2015-01-01T00:00:00.000Z");
        //Use a fixed start date to avoid any oddities caused by the power of 10 rounding
        final Instant startDate = LocalDateTime.of(
                2016, 12, 13, 11, 59, 3)
                .toInstant(ZoneOffset.UTC);
        final int statNameCount = 4;
        final int timesCount = 10;
        final int numberOfDifferentPrecisions = 4;
        final int totalFlushCount = statNameCount * timesCount * numberOfDifferentPrecisions;

        final long expectedCountTotalByPrecision = statNameCount * timesCount
                * (statisticType.equals(StatisticType.COUNT)
                ? STAT_VALUE
                : 1);
        final long expectedValueTotalByPrecision = statNameCount * timesCount * STAT_VALUE;

        final long expectedCountTotal = expectedCountTotalByPrecision * numberOfDifferentPrecisions;

        // Ensure we exercise batching with two full and one partial batch
        final int stage1BatchSize = (int) (totalFlushCount * 0.4);
        final int stage2BatchSize = (int) (timesCount * 0.4);
        sqlStatisticAggregationManager.setStage1BatchSize(stage1BatchSize);
        sqlStatisticAggregationManager.setStage2BatchSize(stage2BatchSize);
        LOGGER.info("Stage 1 batch size: {}", stage1BatchSize);
        LOGGER.info("Stage 2 batch size: {}", stage2BatchSize);

        final LogExecutionTime time = new LogExecutionTime();

        loadData(startDate, statNameCount, timesCount, statisticType);

        LOGGER.info("First aggregation run");
        LOGGER.info("startDate: " + startDate);
        runAggregation(startDate);

        assertThat(getAggregateTotal(COL_NAME_CNT))
                .isEqualTo(expectedCountTotal);

        // val col meaningless for count stats
        assertThat(getAggregateTotal(COL_NAME_VAL))
                .isZero();

        assertThat(getAggregateByPrecision(COL_NAME_CNT, SQLStatisticAggregationTransactionHelper.DEFAULT_PRECISION))
                .isEqualTo(expectedValueTotalByPrecision);

        assertThat(getAggregateByPrecision(COL_NAME_CNT, SQLStatisticAggregationTransactionHelper.HOUR_PRECISION))
                .isEqualTo(expectedValueTotalByPrecision);

        assertThat(getAggregateByPrecision(COL_NAME_CNT, SQLStatisticAggregationTransactionHelper.DAY_PRECISION))
                .isEqualTo(expectedValueTotalByPrecision);

        assertThat(getAggregateByPrecision(COL_NAME_CNT, SQLStatisticAggregationTransactionHelper.MONTH_PRECISION))
                .isEqualTo(expectedValueTotalByPrecision);

        LOGGER.info("run aggregation again with no new data in SVS");
        runAggregation(startDate);

        assertThat(getAggregateTotal(COL_NAME_CNT))
                .isEqualTo(expectedCountTotal);
        assertThat(getAggregateTotal(COL_NAME_VAL))
                .isZero();

        assertThat(getAggregateByPrecision(COL_NAME_CNT, SQLStatisticAggregationTransactionHelper.DEFAULT_PRECISION))
                .isEqualTo(expectedValueTotalByPrecision);

        assertThat(getAggregateByPrecision(COL_NAME_CNT, SQLStatisticAggregationTransactionHelper.HOUR_PRECISION))
                .isEqualTo(expectedValueTotalByPrecision);

        assertThat(getAggregateByPrecision(COL_NAME_CNT, SQLStatisticAggregationTransactionHelper.DAY_PRECISION))
                .isEqualTo(expectedValueTotalByPrecision);

        assertThat(getAggregateByPrecision(COL_NAME_CNT, SQLStatisticAggregationTransactionHelper.MONTH_PRECISION))
                .isEqualTo(expectedValueTotalByPrecision);

        LOGGER.info(
                "run aggregation again but pretend we are 2hrs in the future so it rolls up the zero precision");
        Instant futureDate = startDate.plus(2, ChronoUnit.HOURS);
        LOGGER.info("futureDate: " + futureDate);
        // load more data into each precision so it has to merge existing
        // and roll up existing default moves to hour one new of each
        // precision
        loadData(futureDate, statNameCount, timesCount, statisticType);
        runAggregation(futureDate);

        assertThat(getAggregateTotal(COL_NAME_CNT))
                .isEqualTo(expectedCountTotal * 2);
        assertThat(getAggregateTotal(COL_NAME_VAL))
                .isZero();

        assertThat(getAggregateByPrecision(COL_NAME_CNT, SQLStatisticAggregationTransactionHelper.DEFAULT_PRECISION))
                .isEqualTo(expectedValueTotalByPrecision * 1);

        assertThat(getAggregateByPrecision(COL_NAME_CNT, SQLStatisticAggregationTransactionHelper.HOUR_PRECISION))
                .isEqualTo(expectedValueTotalByPrecision * 3);

        assertThat(getAggregateByPrecision(COL_NAME_CNT, SQLStatisticAggregationTransactionHelper.DAY_PRECISION))
                .isEqualTo(expectedValueTotalByPrecision * 2);

        assertThat(getAggregateByPrecision(COL_NAME_CNT, SQLStatisticAggregationTransactionHelper.MONTH_PRECISION))
                .isEqualTo(expectedValueTotalByPrecision * 2);

        LOGGER.info("run aggregation again but pretend we are 2days in the future");
        futureDate = startDate.plus(2, ChronoUnit.DAYS);
        LOGGER.info("futureDate: " + futureDate);
        // load more data into each precision so it has to merge existing
        // and roll up one new in each precision one default from above
        // moves into day three hour from above move into day already two in
        // day
        loadData(futureDate, statNameCount, timesCount, statisticType);
        runAggregation(futureDate);

        assertThat(getAggregateTotal(COL_NAME_CNT))
                .isEqualTo(expectedCountTotal * 3);
        assertThat(getAggregateTotal(COL_NAME_VAL))
                .isZero();

        assertThat(getAggregateByPrecision(COL_NAME_CNT, SQLStatisticAggregationTransactionHelper.DEFAULT_PRECISION))
                .isEqualTo(expectedValueTotalByPrecision * 1);

        assertThat(getAggregateByPrecision(COL_NAME_CNT, SQLStatisticAggregationTransactionHelper.HOUR_PRECISION))
                .isEqualTo(expectedValueTotalByPrecision * 1);

        assertThat(getAggregateByPrecision(COL_NAME_CNT, SQLStatisticAggregationTransactionHelper.DAY_PRECISION))
                .isEqualTo(expectedValueTotalByPrecision * 7);

        assertThat(getAggregateByPrecision(COL_NAME_CNT, SQLStatisticAggregationTransactionHelper.MONTH_PRECISION))
                .isEqualTo(expectedValueTotalByPrecision * 3);

        LOGGER.info("run aggregation again but pretend we are 32days in the future");
        futureDate = startDate.plus(65, ChronoUnit.DAYS);
        LOGGER.info("futureDate: " + futureDate);
        // load more data into each precision so it has to merge existing
        // and roll up one new in each precision existing default moves to
        // hour existing hour moves to day
        loadData(futureDate, statNameCount, timesCount, statisticType);
        runAggregation(futureDate);

        assertThat(getAggregateTotal(COL_NAME_CNT))
                .isEqualTo(expectedCountTotal * 4);
        assertThat(getAggregateTotal(COL_NAME_VAL))
                .isZero();

        assertThat(getAggregateByPrecision(COL_NAME_CNT, SQLStatisticAggregationTransactionHelper.DEFAULT_PRECISION))
                .isEqualTo(expectedValueTotalByPrecision * 1);

        assertThat(getAggregateByPrecision(COL_NAME_CNT, SQLStatisticAggregationTransactionHelper.HOUR_PRECISION))
                .isEqualTo(expectedValueTotalByPrecision * 1);

        assertThat(getAggregateByPrecision(COL_NAME_CNT, SQLStatisticAggregationTransactionHelper.DAY_PRECISION))
                .isEqualTo(expectedValueTotalByPrecision * 2);

        assertThat(getAggregateByPrecision(COL_NAME_CNT, SQLStatisticAggregationTransactionHelper.MONTH_PRECISION))
                .isEqualTo(expectedValueTotalByPrecision * 12);

        LOGGER.info("run aggregation again with no new data so day data can roll up to month");
        LOGGER.info("futureDate: " + futureDate);
        runAggregation(futureDate);

        assertThat(getAggregateTotal(COL_NAME_CNT))
                .isEqualTo(expectedCountTotal * 4);
        assertThat(getAggregateTotal(COL_NAME_VAL))
                .isZero();

        assertThat(getAggregateByPrecision(COL_NAME_CNT, SQLStatisticAggregationTransactionHelper.DEFAULT_PRECISION))
                .isEqualTo(expectedValueTotalByPrecision * 1);

        assertThat(getAggregateByPrecision(COL_NAME_CNT, SQLStatisticAggregationTransactionHelper.HOUR_PRECISION))
                .isEqualTo(expectedValueTotalByPrecision * 1);

        assertThat(getAggregateByPrecision(COL_NAME_CNT, SQLStatisticAggregationTransactionHelper.DAY_PRECISION))
                .isEqualTo(expectedValueTotalByPrecision * 1);

        assertThat(getAggregateByPrecision(COL_NAME_CNT, SQLStatisticAggregationTransactionHelper.MONTH_PRECISION))
                .isEqualTo(expectedValueTotalByPrecision * 13);

        LOGGER.info("Test ran in {}", time);
    }

    @Test
    void testValueAggregation() throws SQLException {
        final StatisticType statisticType = StatisticType.VALUE;
        // final long startDateMs =
        // DateUtil.parseNormalDateTimeString("2015-01-01T00:00:00.000Z");
        //Use a fixed start date to avoid any oddities caused by the power of 10 rounding
        final Instant startDate = LocalDateTime.of(
                2016, 12, 13, 11, 59, 3).toInstant(ZoneOffset.UTC);
        final int statNameCount = 4;
        final int timesCount = 10;
        final int numberOfDifferentPrecisions = 3;
        final int totalFlushCount = statNameCount * timesCount * numberOfDifferentPrecisions;

        final long expectedCountTotalByPrecision = statNameCount * timesCount
                * (statisticType.equals(StatisticType.COUNT)
                ? STAT_VALUE
                : 1);
        final long expectedValueTotalByPrecision = statNameCount * timesCount * STAT_VALUE;

        final long expectedCountTotal = expectedCountTotalByPrecision * numberOfDifferentPrecisions;
        final long expectedValueTotal = expectedValueTotalByPrecision * numberOfDifferentPrecisions;

        // Ensure we exercise batching with two full and one partial batch
        final int stage1BatchSize = (int) (totalFlushCount * 0.4);
        final int stage2BatchSize = (int) (timesCount * 0.4);
        sqlStatisticAggregationManager.setStage1BatchSize(stage1BatchSize);
        sqlStatisticAggregationManager.setStage2BatchSize(stage2BatchSize);
        LOGGER.info("Stage 1 batch size: {}", stage1BatchSize);
        LOGGER.info("Stage 2 batch size: {}", stage2BatchSize);

        final LogExecutionTime time = new LogExecutionTime();

        loadData(startDate, statNameCount, timesCount, statisticType);

        LOGGER.info("First aggregation run");
        LOGGER.info("startDate: " + startDate);
        runAggregation(startDate);

        assertThat(getAggregateTotal(COL_NAME_CNT))
                .isEqualTo(expectedCountTotal);
        assertThat(getAggregateTotal(COL_NAME_VAL))
                .isEqualTo(expectedValueTotal);

        assertThat(getAggregateByPrecision(COL_NAME_VAL, SQLStatisticAggregationTransactionHelper.DEFAULT_PRECISION))
                .isEqualTo(expectedValueTotalByPrecision);

        assertThat(getAggregateByPrecision(COL_NAME_VAL, SQLStatisticAggregationTransactionHelper.DAY_PRECISION))
                .isEqualTo(expectedValueTotalByPrecision);

        assertThat(getAggregateByPrecision(COL_NAME_VAL, SQLStatisticAggregationTransactionHelper.MONTH_PRECISION))
                .isEqualTo(expectedValueTotalByPrecision);

        assertThat(getAggregateByPrecision(COL_NAME_CNT, SQLStatisticAggregationTransactionHelper.DEFAULT_PRECISION))
                .isEqualTo(expectedCountTotalByPrecision);

        assertThat(getAggregateByPrecision(COL_NAME_CNT, SQLStatisticAggregationTransactionHelper.DAY_PRECISION))
                .isEqualTo(expectedCountTotalByPrecision);

        assertThat(getAggregateByPrecision(COL_NAME_CNT, SQLStatisticAggregationTransactionHelper.MONTH_PRECISION))
                .isEqualTo(expectedCountTotalByPrecision);

        LOGGER.info("run aggregation again with no new data in SVS");
        runAggregation(startDate);

        assertThat(getAggregateTotal(COL_NAME_CNT))
                .isEqualTo(expectedCountTotal);
        assertThat(getAggregateTotal(COL_NAME_VAL))
                .isEqualTo(expectedValueTotal);

        assertThat(getAggregateByPrecision(COL_NAME_VAL, SQLStatisticAggregationTransactionHelper.DEFAULT_PRECISION))
                .isEqualTo(expectedValueTotalByPrecision);

        assertThat(getAggregateByPrecision(COL_NAME_VAL, SQLStatisticAggregationTransactionHelper.DAY_PRECISION))
                .isEqualTo(expectedValueTotalByPrecision);

        assertThat(getAggregateByPrecision(COL_NAME_VAL, SQLStatisticAggregationTransactionHelper.MONTH_PRECISION))
                .isEqualTo(expectedValueTotalByPrecision);

        assertThat(getAggregateByPrecision(COL_NAME_CNT, SQLStatisticAggregationTransactionHelper.DEFAULT_PRECISION))
                .isEqualTo(expectedCountTotalByPrecision);

        assertThat(getAggregateByPrecision(COL_NAME_CNT, SQLStatisticAggregationTransactionHelper.DAY_PRECISION))
                .isEqualTo(expectedCountTotalByPrecision);

        assertThat(getAggregateByPrecision(COL_NAME_CNT, SQLStatisticAggregationTransactionHelper.MONTH_PRECISION))
                .isEqualTo(expectedCountTotalByPrecision);

        LOGGER.info("run aggregation again but pretend we are 2days in the future");
        Instant futureDate = startDate.plus(2, ChronoUnit.DAYS);
        LOGGER.info("futureDate: " + futureDate);
        // load more data into each precision so it has to merge existing
        // and roll up one new in each precision one default from above
        // moves into day
        loadData(futureDate, statNameCount, timesCount, statisticType);
        runAggregation(futureDate);

        assertThat(getAggregateByPrecision(COL_NAME_VAL, SQLStatisticAggregationTransactionHelper.DEFAULT_PRECISION))
                .isEqualTo(expectedValueTotalByPrecision * 1);

        assertThat(getAggregateByPrecision(COL_NAME_VAL, SQLStatisticAggregationTransactionHelper.DAY_PRECISION))
                .isEqualTo(expectedValueTotalByPrecision * 3);

        assertThat(getAggregateByPrecision(COL_NAME_VAL, SQLStatisticAggregationTransactionHelper.MONTH_PRECISION))
                .isEqualTo(expectedValueTotalByPrecision * 2);

        assertThat(getAggregateByPrecision(COL_NAME_CNT, SQLStatisticAggregationTransactionHelper.DEFAULT_PRECISION))
                .isEqualTo(expectedCountTotalByPrecision * 1);

        assertThat(getAggregateByPrecision(COL_NAME_CNT, SQLStatisticAggregationTransactionHelper.DAY_PRECISION))
                .isEqualTo(expectedCountTotalByPrecision * 3);

        assertThat(getAggregateByPrecision(COL_NAME_CNT, SQLStatisticAggregationTransactionHelper.MONTH_PRECISION))
                .isEqualTo(expectedCountTotalByPrecision * 2);

        assertThat(getAggregateTotal(COL_NAME_CNT))
                .isEqualTo(expectedCountTotal * 2);
        assertThat(getAggregateTotal(COL_NAME_VAL))
                .isEqualTo(expectedValueTotal * 2);

        LOGGER.info("run aggregation again but pretend we are 32days in the future");
        futureDate = startDate.plus(65, ChronoUnit.DAYS);
        LOGGER.info("futureDate: " + futureDate);
        // load more data into each precision so it has to merge existing
        // and roll up one new in each precision existing default moves to
        // hour existing hour moves to day
        loadData(futureDate, statNameCount, timesCount, statisticType);
        runAggregation(futureDate);

        assertThat(getAggregateByPrecision(COL_NAME_VAL, SQLStatisticAggregationTransactionHelper.DEFAULT_PRECISION))
                .isEqualTo(expectedValueTotalByPrecision * 1);

        assertThat(getAggregateByPrecision(COL_NAME_VAL, SQLStatisticAggregationTransactionHelper.DAY_PRECISION))
                .isEqualTo(expectedValueTotalByPrecision * 1);

        assertThat(getAggregateByPrecision(COL_NAME_VAL, SQLStatisticAggregationTransactionHelper.MONTH_PRECISION))
                .isEqualTo(expectedValueTotalByPrecision * 7);

        assertThat(getAggregateByPrecision(COL_NAME_CNT, SQLStatisticAggregationTransactionHelper.DEFAULT_PRECISION))
                .isEqualTo(expectedCountTotalByPrecision * 1);

        assertThat(getAggregateByPrecision(COL_NAME_CNT, SQLStatisticAggregationTransactionHelper.DAY_PRECISION))
                .isEqualTo(expectedCountTotalByPrecision * 1);

        assertThat(getAggregateByPrecision(COL_NAME_CNT, SQLStatisticAggregationTransactionHelper.MONTH_PRECISION))
                .isEqualTo(expectedCountTotalByPrecision * 7);

        assertThat(getAggregateTotal(COL_NAME_CNT))
                .isEqualTo(expectedCountTotal * 3);
        assertThat(getAggregateTotal(COL_NAME_VAL))
                .isEqualTo(expectedValueTotal * 3);

        LOGGER.info("run aggregation again with no new data so day data can roll up to month");
        LOGGER.info("futureDate: " + futureDate);
        runAggregation(futureDate);

        assertThat(getAggregateByPrecision(COL_NAME_VAL, SQLStatisticAggregationTransactionHelper.DEFAULT_PRECISION))
                .isEqualTo(expectedValueTotalByPrecision * 1);

        assertThat(getAggregateByPrecision(COL_NAME_VAL, SQLStatisticAggregationTransactionHelper.DAY_PRECISION))
                .isEqualTo(expectedValueTotalByPrecision * 1);

        assertThat(getAggregateByPrecision(COL_NAME_VAL, SQLStatisticAggregationTransactionHelper.MONTH_PRECISION))
                .isEqualTo(expectedValueTotalByPrecision * 7);

        assertThat(getAggregateByPrecision(COL_NAME_CNT, SQLStatisticAggregationTransactionHelper.DEFAULT_PRECISION))
                .isEqualTo(expectedCountTotalByPrecision * 1);

        assertThat(getAggregateByPrecision(COL_NAME_CNT, SQLStatisticAggregationTransactionHelper.DAY_PRECISION))
                .isEqualTo(expectedCountTotalByPrecision * 1);

        assertThat(getAggregateByPrecision(COL_NAME_CNT, SQLStatisticAggregationTransactionHelper.MONTH_PRECISION))
                .isEqualTo(expectedCountTotalByPrecision * 7);

        assertThat(getAggregateTotal(COL_NAME_CNT))
                .isEqualTo(expectedCountTotal * 3);
        assertThat(getAggregateTotal(COL_NAME_VAL))
                .isEqualTo(expectedValueTotal * 3);

        LOGGER.info("Test ran in {}", time);
    }

    @Test
    void testDeletingOldStats() throws SQLException {
        final StatisticType statisticType = StatisticType.VALUE;
        // Use a fixed start date to avoid any oddities caused by the power of 10 rounding
        final Instant startDate = LocalDateTime.of(
                2016, 12, 13, 11, 59, 3).toInstant(ZoneOffset.UTC);
        // the number of different stats names to use in the test
        final int statNameCount = 4;
        // the number of different data points per stat name
        final int timesCount = 100;
        final int numberOfDifferentPrecisions = 3 + 1;
        final int totalFlushCount = statNameCount * timesCount * numberOfDifferentPrecisions;

        final long expectedCountTotalByPrecision = statNameCount * timesCount
                * (statisticType.equals(StatisticType.COUNT)
                ? STAT_VALUE
                : 1);
        final long expectedValueTotalByPrecision = statNameCount * timesCount * STAT_VALUE;

        final long expectedCountTotal = expectedCountTotalByPrecision * numberOfDifferentPrecisions;
        final long expectedValueTotal = expectedValueTotalByPrecision * numberOfDifferentPrecisions;

        // Ensure we exercise batching with two full and one partial batch
        final int stage1BatchSize = (int) (totalFlushCount * 0.4);
        final int stage2BatchSize = (int) (statNameCount * timesCount * 0.4);
        sqlStatisticAggregationManager.setStage1BatchSize(stage1BatchSize);
        sqlStatisticAggregationManager.setStage2BatchSize(stage2BatchSize);
        LOGGER.info("Stage 1 batch size: {}", stage1BatchSize);
        LOGGER.info("Stage 2 batch size: {}", stage2BatchSize);

        final LogExecutionTime time = new LogExecutionTime();

        //put the data into SQL_STAT_VAL_SRC
        loadData(startDate, statNameCount, timesCount, statisticType);

        final Instant newStartDate = startDate.minus(200, ChronoUnit.DAYS);
        LOGGER.info("Adding stats working back from: " + newStartDate);
        //Put some very old data in to SQL_STAT_VAL_SRC so that it will get deleted but leave behind some of the
        // data loaded above
        fillStatValSrc(newStartDate, statNameCount, timesCount, statisticType);

        LOGGER.info("First aggregation run");
        LOGGER.info("startDate: " + startDate);
        runAggregation(startDate);

        assertThat(getAggregateTotal(COL_NAME_CNT))
                .isEqualTo(expectedCountTotal);
        assertThat(getAggregateTotal(COL_NAME_VAL))
                .isEqualTo(expectedValueTotal);

        assertThat(getAggregateByPrecision(COL_NAME_VAL, SQLStatisticAggregationTransactionHelper.DEFAULT_PRECISION))
                .isEqualTo(expectedValueTotalByPrecision);

        assertThat(getAggregateByPrecision(COL_NAME_VAL, SQLStatisticAggregationTransactionHelper.DAY_PRECISION))
                .isEqualTo(expectedValueTotalByPrecision);

        assertThat(getAggregateByPrecision(COL_NAME_VAL, SQLStatisticAggregationTransactionHelper.MONTH_PRECISION))
                .isEqualTo(expectedValueTotalByPrecision * 2);


        assertThat(getAggregateByPrecision(COL_NAME_CNT, SQLStatisticAggregationTransactionHelper.DEFAULT_PRECISION))
                .isEqualTo(expectedCountTotalByPrecision);

        assertThat(getAggregateByPrecision(COL_NAME_CNT, SQLStatisticAggregationTransactionHelper.DAY_PRECISION))
                .isEqualTo(expectedCountTotalByPrecision);

        assertThat(getAggregateByPrecision(COL_NAME_CNT, SQLStatisticAggregationTransactionHelper.MONTH_PRECISION))
                .isEqualTo(expectedCountTotalByPrecision * 2);

        // TODO now move time a long way into future and set the max
        // processing age so stuff gets deleted on next
        // aggregation
        // run aggregation and check the right amount of stats are left

        final Instant futureDate = startDate.plus(2, ChronoUnit.DAYS);

        sqlStatisticsConfig.setMaxProcessingAge(StroomDuration.ofDays(30));

        runAggregation(futureDate);

        assertThat(getAggregateByPrecision(COL_NAME_VAL, SQLStatisticAggregationTransactionHelper.DEFAULT_PRECISION))
                .isEqualTo(0);

        assertThat(getAggregateByPrecision(COL_NAME_VAL, SQLStatisticAggregationTransactionHelper.DAY_PRECISION))
                .isEqualTo(expectedValueTotalByPrecision * 2);

        assertThat(getAggregateByPrecision(COL_NAME_VAL, SQLStatisticAggregationTransactionHelper.MONTH_PRECISION))
                .isEqualTo(expectedValueTotalByPrecision);


        assertThat(getAggregateByPrecision(COL_NAME_CNT, SQLStatisticAggregationTransactionHelper.DEFAULT_PRECISION))
                .isEqualTo(0);

        assertThat(getAggregateByPrecision(COL_NAME_CNT, SQLStatisticAggregationTransactionHelper.DAY_PRECISION))
                .isEqualTo(expectedCountTotalByPrecision * 2);

        assertThat(getAggregateByPrecision(COL_NAME_CNT, SQLStatisticAggregationTransactionHelper.MONTH_PRECISION))
                .isEqualTo(expectedCountTotalByPrecision);
    }


    @Test
    void testKeyDeletion() throws SQLException {
        final StatisticType statisticType = StatisticType.VALUE;
        //Use a fixed start date to avoid any oddities caused by the power of 10 rounding
        final Instant startDate = LocalDateTime.of(
                2016, 12, 13, 11, 59, 3)
                .toInstant(ZoneOffset.UTC);
        //the number of different stats names to use in the test
        final int statNameCount = 4;
        //the number of different data points per stat name
        final int timesCount = 100;
        final int numberOfDifferentPrecisions = 3 + 1;

        final long expectedCountTotalByPrecision = statNameCount * timesCount
                * (statisticType.equals(StatisticType.COUNT)
                ? STAT_VALUE
                : 1);
        final long expectedValueTotalByPrecision = statNameCount * timesCount * STAT_VALUE;

        final long expectedCountTotal = expectedCountTotalByPrecision * numberOfDifferentPrecisions;
        final long expectedValueTotal = expectedValueTotalByPrecision * numberOfDifferentPrecisions;

        final LogExecutionTime time = new LogExecutionTime();

        //put the data into SQL_STAT_VAL_SRC
        loadData(startDate, statNameCount, timesCount, statisticType);

        final Instant newStartDate = startDate.minus(200, ChronoUnit.DAYS);
        LOGGER.info("Adding stats working back from: " + newStartDate);
        //Put some very old data in to SQL_STAT_VAL_SRC so that it will get deleted
        fillStatValSrc(newStartDate, statNameCount, timesCount, statisticType);

        LOGGER.info("First aggregation run");
        LOGGER.info("startDate: " + startDate);
        runAggregation(startDate);

        assertThat(getAggregateTotal(COL_NAME_CNT))
                .isEqualTo(expectedCountTotal);
        assertThat(getAggregateTotal(COL_NAME_VAL))
                .isEqualTo(expectedValueTotal);

        assertThat(getAggregateByPrecision(COL_NAME_VAL, SQLStatisticAggregationTransactionHelper.DEFAULT_PRECISION))
                .isEqualTo(expectedValueTotalByPrecision);

        assertThat(getAggregateByPrecision(COL_NAME_VAL, SQLStatisticAggregationTransactionHelper.DAY_PRECISION))
                .isEqualTo(expectedValueTotalByPrecision);

        assertThat(getAggregateByPrecision(COL_NAME_VAL, SQLStatisticAggregationTransactionHelper.MONTH_PRECISION))
                .isEqualTo(expectedValueTotalByPrecision * 2);


        assertThat(getAggregateByPrecision(COL_NAME_CNT, SQLStatisticAggregationTransactionHelper.DEFAULT_PRECISION))
                .isEqualTo(expectedCountTotalByPrecision);

        assertThat(getAggregateByPrecision(COL_NAME_CNT, SQLStatisticAggregationTransactionHelper.DAY_PRECISION))
                .isEqualTo(expectedCountTotalByPrecision);

        assertThat(getAggregateByPrecision(COL_NAME_CNT, SQLStatisticAggregationTransactionHelper.MONTH_PRECISION))
                .isEqualTo(expectedCountTotalByPrecision * 2);

        assertThat(getRowCount("SQL_STAT_KEY"))
                .isEqualTo(statNameCount);

        // TODO now move time a long way into future and set the max
        // processing age so stuff gets deleted on next
        // aggregation
        // run aggregation and check the right amount of stats are left

        final Instant futureDate = startDate.plus(300, ChronoUnit.DAYS);

        sqlStatisticsConfig.setMaxProcessingAge(StroomDuration.ofDays(2));

        runAggregation(futureDate);

        assertThat(getAggregateByPrecision(COL_NAME_VAL, SQLStatisticAggregationTransactionHelper.DEFAULT_PRECISION))
                .isZero();

        assertThat(getAggregateByPrecision(COL_NAME_VAL, SQLStatisticAggregationTransactionHelper.DAY_PRECISION))
                .isZero();

        assertThat(getAggregateByPrecision(COL_NAME_VAL, SQLStatisticAggregationTransactionHelper.MONTH_PRECISION))
                .isZero();

        assertThat(getAggregateByPrecision(COL_NAME_CNT, SQLStatisticAggregationTransactionHelper.DEFAULT_PRECISION))
                .isZero();

        assertThat(getAggregateByPrecision(COL_NAME_CNT, SQLStatisticAggregationTransactionHelper.DAY_PRECISION))
                .isZero();

        assertThat(getAggregateByPrecision(COL_NAME_CNT, SQLStatisticAggregationTransactionHelper.MONTH_PRECISION))
                .isZero();

        // All the stat data has been deleted due to being too old so the orphaned keys should have gone too.
        assertThat(getRowCount("SQL_STAT_KEY"))
                .isZero();
    }

    private void loadData(final Instant startDate,
                          final int statNameCount,
                          final int timesCount,
                          final StatisticType statisticType) throws SQLException {

        LOGGER.info("Filling STAT_VAL_SRC");

        // initial load of data just before now
        Instant newStartDate = startDate;
        LOGGER.info("Adding stats working back from: " + newStartDate);
        fillStatValSrc(newStartDate, statNameCount, timesCount, statisticType);
        int iteration = 0;
        assertThat(getRowCount(SQLStatisticNames.SQL_STATISTIC_VALUE_SOURCE_TABLE_NAME))
                .isEqualTo(statNameCount * timesCount * ++iteration);

        // Value stats doesn't do hour granularity
        if (statisticType.equals(StatisticType.COUNT)) {
            // load of data two hours old
            newStartDate = startDate.minus(2, ChronoUnit.HOURS);
            LOGGER.info("Adding stats working back from: " + newStartDate);
            fillStatValSrc(newStartDate, statNameCount, timesCount, statisticType);
            assertThat(getRowCount(SQLStatisticNames.SQL_STATISTIC_VALUE_SOURCE_TABLE_NAME))
                    .isEqualTo(statNameCount * timesCount * ++iteration);
        }

        // load of data 2 days old
        newStartDate = startDate.minus(2, ChronoUnit.DAYS);
        LOGGER.info("Adding stats working back from: " + newStartDate);
        fillStatValSrc(newStartDate, statNameCount, timesCount, statisticType);
        assertThat(getRowCount(SQLStatisticNames.SQL_STATISTIC_VALUE_SOURCE_TABLE_NAME))
                .isEqualTo(statNameCount * timesCount * ++iteration);

        // load of data 65 days old
        newStartDate = startDate.minus(65, ChronoUnit.DAYS);
        LOGGER.info("Adding stats working back from: " + newStartDate);
        fillStatValSrc(newStartDate, statNameCount, timesCount, statisticType);
        assertThat(getRowCount(SQLStatisticNames.SQL_STATISTIC_VALUE_SOURCE_TABLE_NAME))
                .isEqualTo(statNameCount * timesCount * ++iteration);
    }

    private void fillStatValSrc(final Instant startDate,
                                final int statNameCount,
                                final int timesCount,
                                final StatisticType statisticType) {
        final SQLStatisticAggregateMap sqlStatisticAggregateMap = new SQLStatisticAggregateMap();
        final long value = STAT_VALUE;
        final long precision = 0L;

        for (int i = 0; i < statNameCount; i++) {
            final String statName = "stat" + i;
            for (int j = 0; j < timesCount; j++) {
                // make each time 1ms earlier
                final long timeMs = startDate.toEpochMilli() - j;
                final StatisticEvent statisticEvent;

                if (statisticType.equals(StatisticType.COUNT)) {
                    statisticEvent = StatisticEvent.createCount(timeMs, statName, Collections.emptyList(),
                            value);
                } else {
                    final double valueValue = value;
                    statisticEvent = StatisticEvent.createValue(timeMs, statName, Collections.emptyList(),
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
                sqlStatisticValueBatchSaveService, taskContextFactory, securityContext, SQLStatisticsConfig::new);
        taskHandler.exec(sqlStatisticAggregateMap);
    }

    private void runAggregation() throws SQLException {
        runAggregation(Instant.now());
    }

    private void runAggregation(final Instant timeNow) throws SQLException {
        sqlStatisticAggregationManager.aggregate(timeNow);

        assertThat(getRowCount(SQLStatisticNames.SQL_STATISTIC_VALUE_SOURCE_TABLE_NAME))
                .isEqualTo(0);

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

    private int getAggregateByPrecision(final String colName, final byte precision) throws SQLException {
        final int count;
        try (final Connection connection = sqlStatisticsDbConnProvider.getConnection()) {
            try (final PreparedStatement preparedStatement = connection.prepareStatement(
                    "select sum(coalesce(" + colName + ",0)) " +
                            "from " + SQLStatisticNames.SQL_STATISTIC_VALUE_TABLE_NAME + " " +
                            "where PRES = " + precision)) {
                try (final ResultSet resultSet = preparedStatement.executeQuery()) {
                    resultSet.next();
                    count = resultSet.getInt(1);
                }
            }
        }
        return count;
    }

    private int getAggregateTotal(final String colName) throws SQLException {
        final int count;
        try (final Connection connection = sqlStatisticsDbConnProvider.getConnection()) {
            try (final PreparedStatement preparedStatement = connection.prepareStatement(
                    "select sum(coalesce(" + colName + ",0)) " +
                            "from " + SQLStatisticNames.SQL_STATISTIC_VALUE_TABLE_NAME)) {
                try (final ResultSet resultSet = preparedStatement.executeQuery()) {
                    resultSet.next();
                    count = resultSet.getInt(1);
                }
            }
        }
        return count;
    }

//    private void deleteRows(final String tableName) throws SQLException {
//        try (final Connection connection = connectionProvider.getConnection()) {
//            try (final PreparedStatement preparedStatement =
//            connection.prepareStatement("delete from " + tableName)) {
//                preparedStatement.execute();
//            }
//        }
//    }
//
//    private static class MockTaskMonitor implements TaskMonitor {
//        private static final long serialVersionUID = -8415095958756818805L;
//
//        @Override
//        public Monitor getParent() {
//            return null;
//        }
//
//        @Override
//        public void addTerminateHandler(final TerminateHandler handler) {
//        }
//
//        @Override
//        public void terminate() {
//        }
//
//        @Override
//        public boolean isTerminated() {
//            return false;
//        }
//
//        @Override
//        public String getInfo() {
//            return null;
//        }
//
//        @Override
//        public void info(final Object... args) {
//            // do nothing
//        }
//    }
}
