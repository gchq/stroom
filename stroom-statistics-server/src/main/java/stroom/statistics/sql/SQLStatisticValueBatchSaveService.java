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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import stroom.util.logging.LogExecutionTime;

import javax.inject.Inject;
import javax.inject.Named;
import javax.sql.DataSource;
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
public class SQLStatisticValueBatchSaveService {
    private static final Logger LOGGER = LoggerFactory.getLogger(SQLStatisticValueBatchSaveService.class);
    public static final String SAVE_CALL;

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
        sql.append(") VALUES ( ?, ?, ?, ?) ");
        SAVE_CALL = sql.toString();
    }

    private final DataSource statisticsDataSource;

    @Inject
    SQLStatisticValueBatchSaveService(@Named("statisticsDataSource") final DataSource statisticsDataSource) {
        this.statisticsDataSource = statisticsDataSource;
    }

    @SuppressWarnings("SQL_NONCONSTANT_STRING_PASSED_TO_EXECUTE")
    public void saveBatchStatisticValueSource_String(final List<SQLStatisticValueSourceDO> batch) {
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
            sql.append(") VALUES ");
            boolean doneOne = false;
            for (final SQLStatisticValueSourceDO item : batch) {
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

    public void saveBatchStatisticValueSource_PreparedStatement(final List<SQLStatisticValueSourceDO> batch)
            throws SQLException {
        final LogExecutionTime logExecutionTime = new LogExecutionTime();

        try (final Connection connection = getConnection()) {
            try (final PreparedStatement preparedStatement = connection.prepareStatement(SAVE_CALL)) {
                for (final SQLStatisticValueSourceDO item : batch) {
                    preparedStatement.setLong(1, item.getCreateMs());
                    preparedStatement.setString(2, item.getName());
                    preparedStatement.setByte(3, item.getType().getPrimitiveValue());
                    preparedStatement.setLong(4, item.getValue());
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
    public int saveBatchStatisticValueSource_IndividualPreparedStatements(final List<SQLStatisticValueSourceDO> batch) {
        final LogExecutionTime logExecutionTime = new LogExecutionTime();

        int savedCount = 0;
        int failedCount = 0;

        try (final Connection connection = getConnection()) {
            try (final PreparedStatement preparedStatement = connection.prepareStatement(SAVE_CALL)) {
                for (final SQLStatisticValueSourceDO item : batch) {
                    preparedStatement.setLong(1, item.getCreateMs());
                    preparedStatement.setString(2, item.getName());
                    preparedStatement.setByte(3, item.getType().getPrimitiveValue());
                    preparedStatement.setLong(4, item.getValue());

                    try {
                        preparedStatement.execute();
                        savedCount++;
                    } catch (final RuntimeException e) {
                        // log the error and carry on with the rest
                        LOGGER.error(
                                "Error while tyring to insert a SQL statistic record.  SQL: [{}], createMs: [{}], name: [{}], "
                                        + "typePrimValue: [{}], type: [{}], value: [{}]",
                                SAVE_CALL, item.getCreateMs(), item.getName(), item.getType().getPrimitiveValue(),
                                item.getType().name(), item.getValue(), e);
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
        return statisticsDataSource.getConnection();
    }
}
