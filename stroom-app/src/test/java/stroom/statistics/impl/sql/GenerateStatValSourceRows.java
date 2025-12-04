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

import stroom.db.util.DbUtil;
import stroom.statistics.impl.sql.exception.StatisticsEventValidationException;
import stroom.statistics.impl.sql.rollup.RolledUpStatisticEvent;
import stroom.test.CoreTestModule;
import stroom.test.common.util.db.DbTestModule;

import com.google.inject.Guice;
import com.google.inject.Injector;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.stream.LongStream;

/**
 * Useful for injecting stats into SQL_STAT_VAL_SRC, either to manually test aggregation
 * or to produce data to view in a dash
 */
public class GenerateStatValSourceRows {

    private static final Logger LOGGER = LoggerFactory.getLogger(GenerateStatValSourceRows.class);

    private final SQLStatisticsDbConnProvider statisticsDbConnProvider;
    private final SQLStatisticFlushTaskHandler sqlStatisticFlushTaskHandler;

    @Inject
    public GenerateStatValSourceRows(final SQLStatisticsDbConnProvider statisticsDbConnProvider,
                                     final SQLStatisticFlushTaskHandler sqlStatisticFlushTaskHandler) {
        this.statisticsDbConnProvider = statisticsDbConnProvider;
        this.sqlStatisticFlushTaskHandler = sqlStatisticFlushTaskHandler;
    }

    public static void main(final String[] args) throws SQLException {
        final Injector injector = Guice.createInjector(
                new DbTestModule(),
                new CoreTestModule());

        injector.getInstance(GenerateStatValSourceRows.class)
                .run(args);
    }

    private void run(final String[] args) throws SQLException {

        try (final Connection connection = statisticsDbConnProvider.getConnection()) {
            LOGGER.info("Clearing SQL_STAT_VAL_SRC");
            final PreparedStatement stmt = connection.prepareStatement("delete from SQL_STAT_VAL_SRC");
            final int count = stmt.executeUpdate();
            LOGGER.info("Deleted {} rows", count);
        }

        getRowCount();

        final Instant nowIsh = Instant.now()
                .truncatedTo(ChronoUnit.MINUTES);

        final SQLStatisticAggregateMap statisticAggregateMap = new SQLStatisticAggregateMap();

        Instant startTime = nowIsh;
        startTime = generateEvents(startTime, Duration.ofHours(1), Duration.ofSeconds(30), statisticAggregateMap);
        startTime = generateEvents(startTime, Duration.ofDays(1), Duration.ofMinutes(30), statisticAggregateMap);
        startTime = generateEvents(startTime, Duration.ofDays(31), Duration.ofHours(6), statisticAggregateMap);
        startTime = generateEvents(startTime, Duration.ofDays(365), Duration.ofDays(1), statisticAggregateMap);

        LOGGER.info("{}", statisticAggregateMap);

        // flush the map to SQL_STAT_VAL_SRC
        sqlStatisticFlushTaskHandler.exec(statisticAggregateMap);

        getRowCount();
    }

    private Instant generateEvents(final Instant startTime,
                                   final Duration totalDuration,
                                   final Duration interval,
                                   final SQLStatisticAggregateMap statisticAggregateMap) {

        final long iterations = totalDuration.dividedBy(interval);

        LongStream.rangeClosed(0, iterations)
                .boxed()
                .map(i -> startTime.minus(interval.multipliedBy(i)))
                .map(this::buildEvent)
                .forEach(event -> {
                    try {
                        statisticAggregateMap.addRolledUpEvent(event, 1000);
                    } catch (final StatisticsEventValidationException e) {
                        throw new RuntimeException(e);
                    }
                });
        return startTime.minus(totalDuration);
    }

    private void getRowCount() throws SQLException {
        try (final Connection connection = statisticsDbConnProvider.getConnection()) {
            LOGGER.info("Table count: {}",
                    DbUtil.countEntity(connection,
                            "SQL_STAT_VAL_SRC"));
        }
    }

    private RolledUpStatisticEvent buildEvent(final Instant eventTime) {
        return new RolledUpStatisticEvent(StatisticEvent.createCount(
                eventTime.toEpochMilli(),
                "StatAggTest",
                Collections.emptyList(),
                10L));
    }
}
