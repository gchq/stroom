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

import stroom.util.logging.LogExecutionTime;

import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.List;

/**
 * SQL_STAT_VAL_SRC - Input Table SQL_STAT_KEY - Key Table SQL_STAT_VAL - Value
 * Table
 */
class SQLStatisticValueBatchSaveService {

    private static final Logger LOGGER = LoggerFactory.getLogger(SQLStatisticValueBatchSaveService.class);
    private static final String SINGLE_INSERT_SQL;
    private static final String INSERT_WITHOUT_VALUES;
    private static final String VALUES_SET = "(?,?,?,?,?)";

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
        sql.append(") VALUES ");
        INSERT_WITHOUT_VALUES = sql.toString();
        sql.append(VALUES_SET);
        SINGLE_INSERT_SQL = sql.toString();
    }

    private final SQLStatisticsDbConnProvider sqlStatisticsDbConnProvider;

    private Integer lastBatchSize = null;
    private String lastPreparedStatementSQL = null;

    @Inject
    SQLStatisticValueBatchSaveService(final SQLStatisticsDbConnProvider sqlStatisticsDbConnProvider) {
        this.sqlStatisticsDbConnProvider = sqlStatisticsDbConnProvider;
    }

    /**
     * Inserts the batch using a single big non-prepared statement.
     * As it is non-prepared we need to manually escape chars else risk SQL injection.
     */
    @SuppressWarnings("SQL_NONCONSTANT_STRING_PASSED_TO_EXECUTE")
    void saveBatchStatisticValueSource_String(final List<SQLStatValSourceDO> batch) {
        final LogExecutionTime logExecutionTime = new LogExecutionTime();

        try (final Connection connection = getConnection()) {
            final StringBuilder sql = new StringBuilder();
            sql.append(INSERT_WITHOUT_VALUES);
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
                sql.append(item.getValueSum().orElse(0));
                sql.append(",");
                sql.append(item.getCount());
                sql.append(")");
                doneOne = true;
            }

            try (final Statement statement = connection.createStatement()) {
                statement.executeUpdate(sql.toString());

                if (LOGGER.isDebugEnabled()) {
                    logDurationToDebug("saveBatchStatisticValueSource_String", batch, logExecutionTime);
                }
            }
        } catch (final SQLException sqlEx) {
            throw new RuntimeException(sqlEx);
        }
    }

    /**
     * Insert the batch using a single prepared statement with multiple sets of values in it.
     * Too high a batch size may blow the limit on max sql statement size or param count.
     * This is the fastest way to load the data, but if one record is bad all will fail.
     */
    void saveBatchStatisticValueSource_SinglePreparedStatement(final List<SQLStatValSourceDO> batch) {
        final LogExecutionTime logExecutionTime = new LogExecutionTime();

        try (final Connection connection = getConnection()) {
            final String sql = buildSinglePreparedStatementSql(batch.size());
            try (final PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
                for (int i = 0; i < batch.size(); i++) {
                    setPreparedStatementParams(preparedStatement, batch.get(i), i);
                }
                preparedStatement.execute();

                if (LOGGER.isDebugEnabled()) {
                    logDurationToDebug("saveBatchStatisticValueSource_SinglePreparedStatement",
                            batch,
                            logExecutionTime);
                }
            }
        } catch (final SQLException sqlEx) {
            throw new RuntimeException(sqlEx);
        }
    }

    private void logDurationToDebug(final String name,
                                    final List<SQLStatValSourceDO> batch,
                                    final LogExecutionTime logExecutionTime) {
        final int batchSize = batch.size();
        final Duration duration = logExecutionTime.getDuration();
        LOGGER.debug("{}() - Inserted {} records into {} in {} ({}/sec)",
                name,
                batchSize,
                SQLStatisticNames.SQL_STATISTIC_VALUE_SOURCE_TABLE_NAME,
                duration,
                ((double) batchSize) / duration.toMillis() * 1_000);
    }

    private String buildSinglePreparedStatementSql(final int batchSize) {
        if (lastBatchSize == null || lastBatchSize != batchSize || lastPreparedStatementSQL == null) {
            final StringBuilder sql = new StringBuilder();
            sql.append(INSERT_WITHOUT_VALUES);
            for (int i = 0; i < batchSize; i++) {
                sql.append(VALUES_SET);
                if (i < batchSize - 1) {
                    sql.append(",");
                }
            }
            // No point rebuilding this massive sql on each iteration
            lastBatchSize = batchSize;
            lastPreparedStatementSQL = sql.toString();
        }
        return lastPreparedStatementSQL;
    }

    /**
     * Insert the batch using individual statements, but sent over the network as a batch.
     * MySQL will execute each one individually unless the MySQL prop 'rewriteBatchedStatements'
     * is set to on/1, thus it is 10-20x slower than the single statement approaches.
     *
     * @param batch
     * @throws SQLException
     */
    void saveBatchStatisticValueSource_BatchPreparedStatement(final List<SQLStatValSourceDO> batch)
            throws SQLException {
        final LogExecutionTime logExecutionTime = new LogExecutionTime();

        try (final Connection connection = getConnection()) {
            try (final PreparedStatement preparedStatement = connection.prepareStatement(
                    SINGLE_INSERT_SQL)) {
                for (final SQLStatValSourceDO item : batch) {
                    setPreparedStatementParams(preparedStatement, item);
                    preparedStatement.addBatch();
                    preparedStatement.clearParameters();
                }

                preparedStatement.executeBatch();

                if (LOGGER.isDebugEnabled()) {
                    logDurationToDebug("saveBatchStatisticValueSource_PreparedStatement",
                            batch,
                            logExecutionTime);
                }
            }
        }
    }

    private void setPreparedStatementParams(final PreparedStatement preparedStatement,
                                            final SQLStatValSourceDO item) throws SQLException {
        setPreparedStatementParams(preparedStatement, item, 0);

    }

    private void setPreparedStatementParams(final PreparedStatement preparedStatement,
                                            final SQLStatValSourceDO item,
                                            final int iterationZeroBased) throws SQLException {
        int idx = iterationZeroBased * 5;
        preparedStatement.setLong(++idx, item.getCreateMs());
        preparedStatement.setString(++idx, item.getName());
        preparedStatement.setByte(++idx, item.getType().getPrimitiveValue());
        preparedStatement.setDouble(++idx, item.getValueSum().orElse(0));
        preparedStatement.setLong(++idx, item.getCount());
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
            try (final PreparedStatement preparedStatement = connection.prepareStatement(SINGLE_INSERT_SQL)) {
                for (final SQLStatValSourceDO item : batch) {
                    setPreparedStatementParams(preparedStatement, item);

                    try {
                        preparedStatement.execute();
                        savedCount++;
                    } catch (final RuntimeException e) {
                        // log the error and carry on with the rest
                        LOGGER.error("Error while tyring to insert a SQL statistic record.  SQL: [{}], " +
                                        "createMs: [{}], name: [{}], typePrimValue: [{}], type: [{}], " +
                                        "value: [{}], count: [{}]",
                                SINGLE_INSERT_SQL,
                                item.getCreateMs(),
                                item.getName(),
                                item.getType().getPrimitiveValue(),
                                item.getType().name(),
                                item.getValueSum(),
                                item.getCount(),
                                e);
                        failedCount++;
                    }
                    preparedStatement.clearParameters();
                }

                if (LOGGER.isDebugEnabled()) {
                    logDurationToDebug("saveBatchStatisticValueSource_IndividualPreparedStatements",
                            batch,
                            logExecutionTime);
                }
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
//        ((com.mysql.cj.jdbc.ConnectionImpl) connection).setRewriteBatchedStatements(true);
//        sqlStatisticsDbConnProvider.getConnection().unwrap(ConnectionImpl.class)
//                .getPropertySet()
//                .getBooleanProperty("rewriteBatchedStatements")
//                .setValue(true);
        return sqlStatisticsDbConnProvider.getConnection();
    }
}
