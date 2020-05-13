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

import stroom.util.logging.LogExecutionTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

/**
 * SQL_STAT_VAL_SRC - Input Table SQL_STAT_KEY - Key Table SQL_STAT_VAL - Value
 * Table
 */
// @Transactional
class SQLStatisticValueBatchSaveService {
    private static final Logger LOGGER = LoggerFactory.getLogger(SQLStatisticValueBatchSaveService.class);
    private static final String SAVE_CALL;

    static {
        final StringBuilder sql = new StringBuilder();
        sql.append("INSERT INTO ");
        sql.append(SQLStatisticNames.SQL_STATISTIC_VALUE_SOURCE_TABLE_NAME);
        sql.append("(");
        sql.append(SQLStatisticNames.TIME_MS);
        sql.append(",");
        sql.append(SQLStatisticNames.NAME);
        sql.append(",");
        sql.append(SQLStatisticNames.VALUE_TYPE);
        sql.append(",");
        sql.append(SQLStatisticNames.VALUE);
        sql.append(",");
        sql.append(SQLStatisticNames.COUNT);
        sql.append(") VALUES ( ?, ?, ?, ?, ?) ");
        SAVE_CALL = sql.toString();
    }

    private final SQLStatisticsDbConnProvider sqlStatisticsDbConnProvider;

    @Inject
    SQLStatisticValueBatchSaveService(final SQLStatisticsDbConnProvider sqlStatisticsDbConnProvider) {
        this.sqlStatisticsDbConnProvider = sqlStatisticsDbConnProvider;
    }

    @SuppressWarnings("SQL_NONCONSTANT_STRING_PASSED_TO_EXECUTE")
    void saveBatchStatisticValueSource_String(final List<SQLStatValSourceDO> batch) {
        final LogExecutionTime logExecutionTime = new LogExecutionTime();

        try (final Connection connection = getConnection()) {
            final StringBuilder sql = new StringBuilder();
            sql.append("INSERT INTO ");
            sql.append(SQLStatisticNames.SQL_STATISTIC_VALUE_SOURCE_TABLE_NAME);
            sql.append("(");
            sql.append(SQLStatisticNames.TIME_MS);
            sql.append(",");
            sql.append(SQLStatisticNames.NAME);
            sql.append(",");
            sql.append(SQLStatisticNames.VALUE_TYPE);
            sql.append(",");
            sql.append(SQLStatisticNames.VALUE);
            sql.append(",");
            sql.append(SQLStatisticNames.COUNT);
            sql.append(") VALUES ");
            boolean doneOne = false;
            for (final SQLStatValSourceDO item : batch) {
                if (doneOne) {
                    sql.append(",");
                }
                sql.append("(");
                sql.append(item.getCreateMs());
                sql.append(",'");
                sql.append(SQLSafe.escapeChars(item.getName())); // must escape
                // any bad chars as we risk sql injection (not an issue for prepared statements)
                sql.append("',");
                sql.append(item.getType().getPrimitiveValue());
                sql.append(",");
                sql.append(item.getValue());
                sql.append(",");
                sql.append(item.getCount());
                sql.append(")");
                doneOne = true;
            }

            try (final Statement statement = connection.createStatement()) {
                statement.executeUpdate(sql.toString());

                LOGGER.debug("saveBatchStatisticValueSource_String() - Saved {} records in {}", batch.size(),
                        logExecutionTime);
            }
        } catch (final SQLException sqlEx) {
            throw new RuntimeException(sqlEx);
        }
    }

    void saveBatchStatisticValueSource_PreparedStatement(final List<SQLStatValSourceDO> batch)
            throws SQLException {
        final LogExecutionTime logExecutionTime = new LogExecutionTime();

        try (final Connection connection = getConnection()) {
            try (final PreparedStatement preparedStatement = connection.prepareStatement(SAVE_CALL)) {
                for (final SQLStatValSourceDO item : batch) {
                    preparedStatement.setLong(1, item.getCreateMs());
                    preparedStatement.setString(2, item.getName());
                    preparedStatement.setByte(3, item.getType().getPrimitiveValue());
                    preparedStatement.setLong(4, item.getValue());
                    preparedStatement.setLong(5, item.getCount());
                    preparedStatement.addBatch();
                    preparedStatement.clearParameters();
                }

                preparedStatement.executeBatch();

                LOGGER.debug("saveBatchStatisticValueSource_PreparedStatement() - Saved {} records in {}", batch.size(),
                        logExecutionTime);
            }
        }
    }

    /**
     * This is a third line fall back for loading the stats and will be very
     * slow as it is inserting them one by one. Any failures will be logged and
     * processing will carry on hopefully some records will get through
     */
    int saveBatchStatisticValueSource_IndividualPreparedStatements(final List<SQLStatValSourceDO> batch) {
        final LogExecutionTime logExecutionTime = new LogExecutionTime();

        int savedCount = 0;
        int failedCount = 0;

        try (final Connection connection = getConnection()) {
            try (final PreparedStatement preparedStatement = connection.prepareStatement(SAVE_CALL)) {
                for (final SQLStatValSourceDO item : batch) {
                    preparedStatement.setLong(1, item.getCreateMs());
                    preparedStatement.setString(2, item.getName());
                    preparedStatement.setByte(3, item.getType().getPrimitiveValue());
                    preparedStatement.setLong(4, item.getValue());
                    preparedStatement.setLong(5, item.getCount());

                    try {
                        preparedStatement.execute();
                        savedCount++;
                    } catch (final RuntimeException e) {
                        // log the error and carry on with the rest
                        LOGGER.error(
                                "Error while tyring to insert a SQL statistic record.  SQL: [{}], createMs: [{}], name: [{}], "
                                        + "typePrimValue: [{}], type: [{}], value: [{}], count: [{}]",
                                SAVE_CALL, item.getCreateMs(), item.getName(), item.getType().getPrimitiveValue(),
                                item.getType().name(), item.getValue(), item.getCount(), e);
                        failedCount++;
                    }
                    preparedStatement.clearParameters();
                }

                LOGGER.debug("saveBatchStatisticValueSource_IndividualPreparedStatements() - Saved {} records in {}",
                        batch.size(), logExecutionTime);
            }
        } catch (final SQLException sqlEx) {
            throw new RuntimeException(sqlEx);
        }

        if (failedCount > 0) {
            LOGGER.error("Failed to insert [{}] SQL statistic records out of a batch size of [{}]",
                    failedCount, batch.size());
        }

        return savedCount;
    }

    Connection getConnection() throws SQLException {
        return sqlStatisticsDbConnProvider.getConnection();
    }
}
