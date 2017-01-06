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

package stroom.entity.server.util;

import stroom.entity.shared.BaseResultList;
import stroom.entity.shared.HasPrimitiveValue;
import stroom.entity.shared.PrimitiveValueConverter;
import stroom.entity.shared.SummaryDataRow;
import stroom.util.config.StroomProperties;
import stroom.util.logging.LogExecutionTime;
import stroom.util.logging.StroomLogger;
import stroom.util.shared.ModelStringUtil;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class ConnectionUtil {
    public static final String JDBC_DRIVER_CLASS_NAME = "stroom.jdbcDriverClassName";
    public static final String JDBC_DRIVER_URL = "stroom.jdbcDriverUrl";
    public static final String JDBC_DRIVER_USERNAME = "stroom.jdbcDriverUsername";
    public static final String JDBC_DRIVER_PASSWORD = "stroom.jdbcDriverPassword";
    private static final StroomLogger LOGGER = StroomLogger.getLogger(ConnectionUtil.class);

    public static final Connection getConnection() throws SQLException {
        final String driverClassname = StroomProperties.getProperty(JDBC_DRIVER_CLASS_NAME);
        final String driverUrl = StroomProperties.getProperty(JDBC_DRIVER_URL);
        final String driverUsername = StroomProperties.getProperty(JDBC_DRIVER_USERNAME);
        final String driverPassword = StroomProperties.getProperty(JDBC_DRIVER_PASSWORD);

        if (driverClassname == null || driverUrl == null) {
            LOGGER.fatal("Properties are not set for DB connection");
            throw new RuntimeException("Properties are not set for DB connection");
        }

        try {
            Class.forName(driverClassname);
        } catch (final Exception ex) {
            throw new RuntimeException(ex);
        }

        LOGGER.info("Connecting to database using classname: %s, url: %s, username: %s", driverClassname, driverUrl,
                driverUsername);

        return DriverManager.getConnection(driverUrl, driverUsername, driverPassword);
    }

    public static final void close(final Connection connection) {
        if (connection != null) {
            try {
                connection.close();
            } catch (final SQLException ex) {
                LOGGER.error(ex, ex);
            }
        }
    }

    public static boolean tableExists(final Connection connection, final String tableName) throws SQLException {
        final DatabaseMetaData databaseMetaData = connection.getMetaData();
        final ResultSet resultSet = databaseMetaData.getTables(null, null, tableName, new String[] { "TABLE" });
        final boolean hasTable = resultSet.next();
        resultSet.close();
        return hasTable;
    }

    @SuppressWarnings("SQL_PREPARED_STATEMENT_GENERATED_FROM_NONCONSTANT_STRING")
    public static int executeUpdate(final Connection connection, final String sql, final List<Object> args)
            throws SQLException {
        LOGGER.debug(">>> %s", sql);
        final LogExecutionTime logExecutionTime = new LogExecutionTime();
        try {
            final PreparedStatement preparedStatement = connection.prepareStatement(sql);
            PreparedStatementUtil.setArguments(preparedStatement, args);
            final int result = preparedStatement.executeUpdate();

            preparedStatement.close();

            log(logExecutionTime, result, sql, args);

            return result;
        } catch (final SQLException sqlException) {
            LOGGER.error("executeUpdate() - %s %s", sql, args, sqlException);
            throw sqlException;
        }
    }

    @SuppressWarnings("SQL_PREPARED_STATEMENT_GENERATED_FROM_NONCONSTANT_STRING")
    public static List<Long> executeInsert(final Connection connection, final String sql, final List<Object> args)
            throws SQLException {
        LOGGER.debug(">>> %s", sql);
        final List<Long> keyList = new ArrayList<Long>();
        final LogExecutionTime logExecutionTime = new LogExecutionTime();
        try (PreparedStatement preparedStatement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            PreparedStatementUtil.setArguments(preparedStatement, args);
            final int result = preparedStatement.executeUpdate();

            try (ResultSet keySet = preparedStatement.getGeneratedKeys()) {
                while (keySet.next()) {
                    keyList.add(keySet.getLong(1));
                }
            }

            log(logExecutionTime, result, sql, args);

            return keyList;
        } catch (final SQLException sqlException) {
            LOGGER.error("executeUpdate() - " + sql + " " + args, sqlException);
            throw sqlException;
        }
    }

    @SuppressWarnings("SQL_PREPARED_STATEMENT_GENERATED_FROM_NONCONSTANT_STRING")
    public static Long executeQueryLongResult(final Connection connection, final String sql, final List<Object> args)
            throws SQLException {
        LOGGER.debug(">>> %s", sql);
        final LogExecutionTime logExecutionTime = new LogExecutionTime();
        Long result = null;

        try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            PreparedStatementUtil.setArguments(preparedStatement, args);
            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                if (resultSet.next()) {
                    result = SQLUtil.getLong(resultSet, 1);
                }
            }

            log(logExecutionTime, result, sql, args);

            return result;
        } catch (final SQLException sqlException) {
            LOGGER.error("executeQueryLongResult() - " + sql + " " + args, sqlException);
            throw sqlException;
        }
    }

    @SuppressWarnings("SQL_PREPARED_STATEMENT_GENERATED_FROM_NONCONSTANT_STRING")
    public static BaseResultList<SummaryDataRow> executeQuerySummaryDataResult(final Connection connection,
            final String sql, final int numberKeys, final List<Object> args,
            final List<? extends HasPrimitiveValue> stats,
            final PrimitiveValueConverter<? extends HasPrimitiveValue> converter) throws SQLException {
        LOGGER.debug(">>> %s", sql);
        final LogExecutionTime logExecutionTime = new LogExecutionTime();
        final ArrayList<SummaryDataRow> summaryData = new ArrayList<>();
        try {
            try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
                PreparedStatementUtil.setArguments(preparedStatement, args);
                try (ResultSet resultSet = preparedStatement.executeQuery()) {
                    while (resultSet.next()) {
                        final SummaryDataRow summaryDataRow = new SummaryDataRow();
                        int pos = 1;
                        for (int i = 0; i < numberKeys; i++) {
                            summaryDataRow.getKey().add(resultSet.getLong(pos++));
                        }
                        for (int i = 0; i < numberKeys; i++) {
                            summaryDataRow.getLabel().add(resultSet.getString(pos++));
                        }
                        summaryDataRow.setCount(resultSet.getLong(pos++));
                        summaryData.add(summaryDataRow);
                    }
                }
            }
            log(logExecutionTime, summaryData, sql, args);

            return BaseResultList.createUnboundedList(summaryData);
        } catch (final SQLException sqlException) {
            LOGGER.error("executeQueryLongResult() - " + sql + " " + args, sqlException);
            throw sqlException;
        }
    }

    @SuppressWarnings("SQL_PREPARED_STATEMENT_GENERATED_FROM_NONCONSTANT_STRING")
    public static ResultSet executeQueryResultSet(final Connection connection, final String sql,
            final List<Object> args) throws SQLException {
        LOGGER.debug(">>> %s", sql);
        final LogExecutionTime logExecutionTime = new LogExecutionTime();
        try {
            final PreparedStatement preparedStatement = connection.prepareStatement(sql);

            PreparedStatementUtil.setArguments(preparedStatement, args);
            final ResultSet resultSet = PreparedStatementUtil.createCloseStatementResultSet(preparedStatement);

            log(logExecutionTime, null, sql, args);

            return resultSet;
        } catch (final SQLException sqlException) {
            LOGGER.error("executeQueryResultSet() - " + sql + " " + args, sqlException);
            throw sqlException;
        }
    }

    private static void log(final LogExecutionTime logExecutionTime, final Object result, final String sql,
            final List<Object> args) {
        final long time = logExecutionTime.getDuration();
        if (LOGGER.isDebugEnabled() || time > 1000) {
            final String message = "<<< " + sql + " " + args + " took " + ModelStringUtil.formatDurationString(time)
                    + " with result " + result;
            if (time > 1000) {
                LOGGER.warn(message);
            } else {
                LOGGER.debug(message);
            }
        }
    }
}
