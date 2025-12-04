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
import stroom.task.api.TaskContextFactory;
import stroom.test.AbstractStatisticsCoreIntegrationTest;
import stroom.test.common.util.db.DbTestUtil;
import stroom.util.concurrent.ThreadUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogExecutionTime;

import io.vavr.Tuple;
import io.vavr.Tuple3;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestSQLStatisticFlushTaskHandler extends AbstractStatisticsCoreIntegrationTest {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestSQLStatisticFlushTaskHandler.class);

    @Inject
    private SQLStatisticsDbConnProvider sqlStatisticsDbConnProvider;
    @Inject
    private SQLStatisticValueBatchSaveService sqlStatisticValueBatchSaveService;
    @Inject
    private SQLStatisticAggregationManager sqlStatisticAggregationManager;
    @Inject
    private SecurityContext securityContext;
    @Inject
    private TaskContextFactory taskContextFactory;
    @Inject
    private SQLStatisticsConfig sqlStatisticsConfig;

    @Test
    void testExec_tenGoodRowsTwoBad() {
        assertThatThrownBy(() -> {
            deleteStatValSrcRows();

            assertThat(getStatValSrcRowCount())
                    .isEqualTo(0);

            final SQLStatisticFlushTaskHandler taskHandler = new SQLStatisticFlushTaskHandler(
                    sqlStatisticValueBatchSaveService, taskContextFactory, securityContext, SQLStatisticsConfig::new);

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

            taskHandler.exec(aggregateMap);
        }).isInstanceOf(StatisticsEventValidationException.class);
    }

    @Test
    void testExec_threeGoodRows() throws StatisticsEventValidationException, SQLException {
        deleteStatValSrcRows();

        assertThat(getStatValSrcRowCount())
                .isEqualTo(0);

        final SQLStatisticFlushTaskHandler taskHandler = new SQLStatisticFlushTaskHandler(
                sqlStatisticValueBatchSaveService, taskContextFactory, securityContext, SQLStatisticsConfig::new);

        final SQLStatisticAggregateMap aggregateMap = new SQLStatisticAggregateMap();

        aggregateMap.addRolledUpEvent(buildGoodEvent(1), 1000);
        aggregateMap.addRolledUpEvent(buildGoodEvent(2), 1000);
        aggregateMap.addRolledUpEvent(buildGoodEvent(3), 1000);

        taskHandler.exec(aggregateMap);

        assertThat(getStatValSrcRowCount())
                .isEqualTo(3);
    }

    @Test
    void testExec_twoBadRows() {
        assertThatThrownBy(() -> {
            deleteStatValSrcRows();

            assertThat(getStatValSrcRowCount())
                    .isEqualTo(0);

            final SQLStatisticFlushTaskHandler taskHandler = new SQLStatisticFlushTaskHandler(
                    sqlStatisticValueBatchSaveService, taskContextFactory, securityContext, SQLStatisticsConfig::new);

            final SQLStatisticAggregateMap aggregateMap = new SQLStatisticAggregateMap();

            aggregateMap.addRolledUpEvent(buildBadEvent(1), 1000);
            aggregateMap.addRolledUpEvent(buildBadEvent(2), 1000);

            taskHandler.exec(aggregateMap);
        }).isInstanceOf(StatisticsEventValidationException.class);
    }

    @Test
    void testExec_hugeNumbers() throws StatisticsEventValidationException, SQLException {
        deleteStatValSrcRows();

        assertThat(getStatValSrcRowCount())
                .isEqualTo(0);

        final SQLStatisticFlushTaskHandler taskHandler = new SQLStatisticFlushTaskHandler(
                sqlStatisticValueBatchSaveService, taskContextFactory, securityContext, SQLStatisticsConfig::new);

        SQLStatisticAggregateMap aggregateMap = new SQLStatisticAggregateMap();

        aggregateMap.addRolledUpEvent(
                buildCustomCountEvent(1, 66666666666L), 1000);

        taskHandler.exec(aggregateMap);

        assertThat(getStatValSrcRowCount())
                .isEqualTo(1);

        sqlStatisticAggregationManager.aggregate(Instant.now());

        assertThat(getStatValSrcRowCount())
                .isEqualTo(0);

        aggregateMap = new SQLStatisticAggregateMap();
        aggregateMap.addRolledUpEvent(
                buildCustomCountEvent(1, 66666666666L), 1000);

        taskHandler.exec(aggregateMap);

        assertThat(getStatValSrcRowCount())
                .isEqualTo(1);

        sqlStatisticAggregationManager.aggregate(Instant.now());

        assertThat(getStatValSrcRowCount())
                .isEqualTo(0);
    }

    @Disabled // manual testing only, too slow for CI
    @TestFactory
    Stream<DynamicTest> testBatchSavePerformance() throws SQLException {
        final int iterations = 20;
        final int batchSize = 5_000;

        final Consumer<List<SQLStatValSourceDO>> saveBatchStatisticValueSource_string =
                sqlStatisticValueBatchSaveService::saveBatchStatisticValueSource_String;
        final Consumer<List<SQLStatValSourceDO>> saveBatchStatisticValueSource_singlePreparedStatement =
                sqlStatisticValueBatchSaveService::saveBatchStatisticValueSource_SinglePreparedStatement;
        final Consumer<List<SQLStatValSourceDO>> saveBatchStatisticValueSource_batchPreparedStatement = batch -> {
            try {
                sqlStatisticValueBatchSaveService
                        .saveBatchStatisticValueSource_BatchPreparedStatement(batch);
            } catch (final Exception e) {
                throw new RuntimeException(e);
            }
        };

        final List<Tuple3<String, Integer, Consumer<List<SQLStatValSourceDO>>>> batchConsumers =
                List.of(
                        Tuple.of("string",
                                batchSize,
                                saveBatchStatisticValueSource_string),
                        Tuple.of("single_prep_stmt",
                                7_000,
                                saveBatchStatisticValueSource_singlePreparedStatement),
                        Tuple.of("single_prep_stmt",
                                8_000,
                                saveBatchStatisticValueSource_singlePreparedStatement),
                        Tuple.of("single_prep_stmt",
                                9_000,
                                saveBatchStatisticValueSource_singlePreparedStatement),
                        Tuple.of("single_prep_stmt",
                                10_000,
                                saveBatchStatisticValueSource_singlePreparedStatement),
                        Tuple.of("single_prep_stmt",
                                11_000,
                                saveBatchStatisticValueSource_singlePreparedStatement));
//                Tuple.of("batch_prep_stmt", saveBatchStatisticValueSource_batchPreparedStatement));

//        ThreadUtil.sleep(3_000);

        return batchConsumers.stream()
                .map(tuple3 ->
                        DynamicTest.dynamicTest(
                                tuple3._1 + " (" + tuple3._2 + ")", () ->
                                        doBatchSaveTest(tuple3._3, tuple3._2, iterations)));
    }

    /**
     * The aim of this test is for multiple threads to be flushing stats into SQL_STAT_VAL_SRC
     * and for the main thread to be constantly running the aggregation to exercise contention
     * on any db locks.
     * Useful for testing the performance of the two process when running concurrently.
     */
    @Disabled // Manual running only, too slow for CI
    @Test
    void testFlushAndAggregatePerformance() throws SQLException, InterruptedException {

        final LogExecutionTime totalRunTime = LogExecutionTime.start();
        final List<String> tagNames = List.of("Tag1", "Tag2");
        // Make sure each flush call executes two and a bit batches
        final int flushLimit = (int) (sqlStatisticsConfig.getStatisticFlushBatchSize() * 2.5);
        final int iterations = 100_000;
        final int keysPerIteration = 2;
        final int tagValuesPerKey = 2;
        final long initialEventDeltaMs = 1_000;
        final long eventDeltaDeltaMs = 20;
        final int flushWorkerThreads = 4;
        final int flushWorkers = 4;
        final int expectedEventCount = iterations * keysPerIteration * tagValuesPerKey * flushWorkers;
        final Instant startTime = Instant.from(
                ZonedDateTime.of(
                        2022,
                        6,
                        30,
                        12,
                        0,
                        0,
                        0,
                        ZoneOffset.UTC));
        final LongAdder totalEventCount = new LongAdder();
        sqlStatisticAggregationManager.setStage1BatchSize(500_000);
        sqlStatisticAggregationManager.setStage2BatchSize(500_000);

        LOGGER.info("Start time: {}", startTime);

        clearAllStatTables();

        assertThat(getStatValSrcRowCount())
                .isEqualTo(0);

        final SQLStatisticFlushTaskHandler sqlStatisticFlushTaskHandler = new SQLStatisticFlushTaskHandler(
                sqlStatisticValueBatchSaveService, taskContextFactory, securityContext, () -> sqlStatisticsConfig);
        final CountDownLatch doneFirstFlush = new CountDownLatch(1);

        final Runnable flushRunnable = () -> {
            final AtomicReference<SQLStatisticAggregateMap> aggregateMapRef = new AtomicReference<>(
                    new SQLStatisticAggregateMap());

            final Runnable doFlush = () -> {
                final SQLStatisticAggregateMap map = aggregateMapRef.get();
                LOGGER.logDurationIfInfoEnabled(() ->
                                sqlStatisticFlushTaskHandler.exec(map),
                        "Flush");
                totalEventCount.add(map.size());
                aggregateMapRef.set(new SQLStatisticAggregateMap());
            };
            Instant time = startTime;
            long eventDeltaMs = initialEventDeltaMs;
            long eventCount = 0;
            for (int i = 0; i < iterations; i++) {
//                time = startTime.minus(i * eventFreqSecs, ChronoUnit.SECONDS);
                time = time.minus(eventDeltaMs, ChronoUnit.MILLIS);
                // Make the gaps between events a bit bigger each time so we span a longer period
                eventDeltaMs += eventDeltaDeltaMs;
                for (int j = 0; j < keysPerIteration; j++) {
                    for (int k = 0; k < tagValuesPerKey; k++) {

                        final int finalK = k;
                        final List<StatisticTag> tags = tagNames.stream()
                                .map(name -> new StatisticTag(name, "Val" + finalK))
                                .collect(Collectors.toList());

                        final StatisticEvent event = StatisticEvent.createCount(
                                time.toEpochMilli(), "StatKey" + j, tags, 10);

                        try {
                            aggregateMapRef.get().addRolledUpEvent(
                                    new RolledUpStatisticEvent(event),
                                    1_000);
                        } catch (final StatisticsEventValidationException e) {
                            throw new RuntimeException(e);
                        }
                        eventCount++;
                        if (eventCount >= flushLimit) {
                            doFlush.run();
                            doneFirstFlush.countDown();
                            eventCount = 0;
                        }
                    }
                }
            }

            if (aggregateMapRef.get().size() > 0) {
                doFlush.run();
                doneFirstFlush.countDown();
            }
            LOGGER.info("Earliest time: {}, eventDeltaMs: {}", time, Duration.ofMillis(eventDeltaMs));
        };

        final Executor flushWorkersExecutor = Executors.newFixedThreadPool(flushWorkerThreads);
        final CountDownLatch countDownLatch = new CountDownLatch(flushWorkers);

        for (int i = 0; i < flushWorkers; i++) {
            final int finalI = i;
            CompletableFuture.runAsync(() -> {
                LOGGER.info("Running flush worker {}", finalI);
                flushRunnable.run();
                countDownLatch.countDown();
                if (countDownLatch.getCount() == 0) {
                    LOGGER.info("Finished last flush");
                }
            }, flushWorkersExecutor);
        }

        // Keep running aggregation until all flushes have finished
        final LogExecutionTime totalAggTime = LogExecutionTime.start();
        Instant aggregationNow = startTime;

        // Don't start aggregating till
        doneFirstFlush.await();
        ThreadUtil.sleep(100);
        while (countDownLatch.getCount() > 0) {
            sqlStatisticAggregationManager.aggregate(aggregationNow);
        }
        LOGGER.info("Flushing finished, running final aggregation");
        sqlStatisticAggregationManager.aggregate(aggregationNow);

        LOGGER.info("Running aggregation 2 hours after start time");
        aggregationNow = aggregationNow.plus(2, ChronoUnit.HOURS);
        // This ensures we run stage 2 agg.
        sqlStatisticAggregationManager.aggregate(aggregationNow);

        LOGGER.info("Running aggregation 2 days after the last time");
        aggregationNow = aggregationNow.plus(2, ChronoUnit.DAYS);
        sqlStatisticAggregationManager.aggregate(aggregationNow);

        LOGGER.info("Running aggregation 40 days after the last time");
        aggregationNow = aggregationNow.plus(40, ChronoUnit.DAYS);
        sqlStatisticAggregationManager.aggregate(aggregationNow);

        LOGGER.info("------------------------------------------------------------");
        LOGGER.info("""
                        Test settings:
                        iterations: {},
                        keysPerIteration: {},
                        tagValuesPerKey: {},
                        initialEventDeltaMs: {},
                        eventDeltaDeltaMs: {},
                        flushWorkerThreads: {},
                        flushWorkers: {}""",
                iterations,
                keysPerIteration,
                tagValuesPerKey,
                initialEventDeltaMs,
                eventDeltaDeltaMs,
                flushWorkerThreads,
                flushWorkers);
        LOGGER.info("Total event count flushed: {}", totalEventCount);
        LOGGER.info("Total agg time: {}", totalAggTime.getDuration());
        LOGGER.info("Total test run time: {}", totalRunTime.getDuration());
        LOGGER.info("------------------------------------------------------------");

        assertThat(getStatValSrcRowCount())
                .isEqualTo(0);
        assertThat(totalEventCount.longValue())
                .isEqualTo(expectedEventCount);
    }

    private void doBatchSaveTest(final Consumer<List<SQLStatValSourceDO>> batchConsumer,
                                 final int batchSize,
                                 final int iterations) {
        final LogExecutionTime logExecutionTime = LogExecutionTime.start();
        for (int l = 0; l < iterations; l++) {
            final List<SQLStatValSourceDO> batch = new ArrayList<>();
            for (int i = 0; i < batchSize; i++) {
                final long value = System.currentTimeMillis();
                final long count = 100;

                final SQLStatValSourceDO statisticValueSource = SQLStatValSourceDO.createValueStat(
                        System.currentTimeMillis(),
                        "BATCHTEST" + i,
                        value,
                        count);

                batch.add(statisticValueSource);
            }

            LOGGER.logDurationIfInfoEnabled(() -> {
                batchConsumer.accept(batch);
            }, "Inserting " + batchSize + " stats");
        }
        LOGGER.info("Total time :" + logExecutionTime.getDuration()
                + " rate: "
                + ((double) batchSize) * iterations / logExecutionTime.getDurationMs() * 1000
                + "/sec");
    }

    private RolledUpStatisticEvent buildGoodEvent(final int id) {
        final StatisticEvent goodEvent = StatisticEvent.createCount(
                123, "shortName" + id, null, 1);
        return new RolledUpStatisticEvent(goodEvent);
    }

    private RolledUpStatisticEvent buildBadEvent(final int id) {
        final StringBuilder sb = new StringBuilder(2010);
        for (int i = 0; i < 201; i++) {
            sb.append("0123456789");
        }

        final StatisticEvent badEvent = StatisticEvent.createCount(
                123, sb.toString() + id, null, 1);

        return new RolledUpStatisticEvent(badEvent);
    }

    private RolledUpStatisticEvent buildCustomCountEvent(final int id, final long countValue) {
        final StatisticEvent goodEvent = StatisticEvent.createCount(
                123, "shortName" + id, null, countValue);

        return new RolledUpStatisticEvent(goodEvent);
    }

    private int getStatValSrcRowCount() throws SQLException {
        final int count;
        try (final Connection connection = sqlStatisticsDbConnProvider.getConnection()) {
            try (final PreparedStatement preparedStatement = connection.prepareStatement(
                    "select count(*) from " + SQLStatisticNames.SQL_STATISTIC_VALUE_SOURCE_TABLE_NAME)) {
                try (final ResultSet resultSet = preparedStatement.executeQuery()) {
                    resultSet.next();
                    count = resultSet.getInt(1);
                }
            }
        }
        return count;
    }

    private void deleteStatValSrcRows() throws SQLException {
        try (final Connection connection = sqlStatisticsDbConnProvider.getConnection()) {
            DbTestUtil.truncateTables(connection, List.of(SQLStatisticNames.SQL_STATISTIC_VALUE_SOURCE_TABLE_NAME));
        }
    }

    private void deleteStatValRows() throws SQLException {
        try (final Connection connection = sqlStatisticsDbConnProvider.getConnection()) {
            DbTestUtil.truncateTables(connection, List.of(SQLStatisticNames.SQL_STATISTIC_VALUE_TABLE_NAME));
        }
    }

    private void deleteStatValKeyRows() throws SQLException {
        try (final Connection connection = sqlStatisticsDbConnProvider.getConnection()) {
            DbTestUtil.truncateTables(connection, List.of(SQLStatisticNames.SQL_STATISTIC_KEY_TABLE_NAME));
        }
    }

    private void clearAllStatTables() throws SQLException {
        try (final Connection connection = sqlStatisticsDbConnProvider.getConnection()) {
            DbTestUtil.truncateTables(connection, List.of(
                    SQLStatisticNames.SQL_STATISTIC_VALUE_SOURCE_TABLE_NAME,
                    SQLStatisticNames.SQL_STATISTIC_KEY_TABLE_NAME,
                    SQLStatisticNames.SQL_STATISTIC_VALUE_TABLE_NAME));
        }
    }
}
