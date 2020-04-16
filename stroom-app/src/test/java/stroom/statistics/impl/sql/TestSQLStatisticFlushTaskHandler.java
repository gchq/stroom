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

package stroom.statistics.impl.sql;


import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.security.api.SecurityContext;
import stroom.statistics.impl.sql.exception.StatisticsEventValidationException;
import stroom.statistics.impl.sql.rollup.RolledUpStatisticEvent;
import stroom.task.api.TaskContextFactory;
import stroom.test.AbstractCoreIntegrationTest;

import javax.inject.Inject;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestSQLStatisticFlushTaskHandler extends AbstractCoreIntegrationTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(TestSQLStatisticFlushTaskHandler.class);

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

    @Test
    void testExec_tenGoodRowsTwoBad() {
        assertThatThrownBy(() -> {
            deleteRows();

            assertThat(getRowCount()).isEqualTo(0);

            final SQLStatisticFlushTaskHandler taskHandler = new SQLStatisticFlushTaskHandler(
                    sqlStatisticValueBatchSaveService, taskContextFactory, securityContext);

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
        deleteRows();

        assertThat(getRowCount()).isEqualTo(0);

        final SQLStatisticFlushTaskHandler taskHandler = new SQLStatisticFlushTaskHandler(
                sqlStatisticValueBatchSaveService, taskContextFactory, securityContext);

        final SQLStatisticAggregateMap aggregateMap = new SQLStatisticAggregateMap();

        aggregateMap.addRolledUpEvent(buildGoodEvent(1), 1000);
        aggregateMap.addRolledUpEvent(buildGoodEvent(2), 1000);
        aggregateMap.addRolledUpEvent(buildGoodEvent(3), 1000);

        taskHandler.exec(aggregateMap);

        assertThat(getRowCount()).isEqualTo(3);
    }

    @Test
    void testExec_twoBadRows() {
        assertThatThrownBy(() -> {
            deleteRows();

            assertThat(getRowCount()).isEqualTo(0);

            final SQLStatisticFlushTaskHandler taskHandler = new SQLStatisticFlushTaskHandler(
                    sqlStatisticValueBatchSaveService, taskContextFactory, securityContext);

            final SQLStatisticAggregateMap aggregateMap = new SQLStatisticAggregateMap();

            aggregateMap.addRolledUpEvent(buildBadEvent(1), 1000);
            aggregateMap.addRolledUpEvent(buildBadEvent(2), 1000);

            taskHandler.exec(aggregateMap);
        }).isInstanceOf(StatisticsEventValidationException.class);
    }

    @Test
    void testExec_hugeNumbers() throws StatisticsEventValidationException, SQLException {
        deleteRows();

        assertThat(getRowCount()).isEqualTo(0);

        final SQLStatisticFlushTaskHandler taskHandler = new SQLStatisticFlushTaskHandler(
                sqlStatisticValueBatchSaveService, taskContextFactory, securityContext);

        final SQLStatisticAggregateMap aggregateMap = new SQLStatisticAggregateMap();

        aggregateMap.addRolledUpEvent(buildCustomCountEvent(1, 66666666666L), 1000);

        taskHandler.exec(aggregateMap);

        assertThat(getRowCount()).isEqualTo(1);

        sqlStatisticAggregationManager.aggregate(Instant.now());

        aggregateMap.addRolledUpEvent(buildCustomCountEvent(1, 66666666666L), 1000);

        taskHandler.exec(aggregateMap);

        assertThat(getRowCount()).isEqualTo(1);

        sqlStatisticAggregationManager.aggregate(Instant.now());

        assertThat(getRowCount()).isEqualTo(0);
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
        int count;
        try (final Connection connection = sqlStatisticsDbConnProvider.getConnection()) {
            try (final PreparedStatement preparedStatement = connection.prepareStatement("select count(*) from " + SQLStatisticNames.SQL_STATISTIC_VALUE_SOURCE_TABLE_NAME)) {
                try (final ResultSet resultSet = preparedStatement.executeQuery()) {
                    resultSet.next();
                    count = resultSet.getInt(1);
                }
            }
        }
        return count;
    }

    private void deleteRows() throws SQLException {
        try (final Connection connection = sqlStatisticsDbConnProvider.getConnection()) {
            try (final PreparedStatement preparedStatement = connection.prepareStatement("delete from " + SQLStatisticNames.SQL_STATISTIC_VALUE_SOURCE_TABLE_NAME)) {
                preparedStatement.execute();
            }
        }
    }


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
