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

package stroom.entity.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.util.logging.LogExecutionTime;
import stroom.util.shared.ModelStringUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class ConnectionUtil {
    private static final Logger LOGGER = LoggerFactory.getLogger(ConnectionUtil.class);

//    public static final Connection getConnection() throws SQLException {
//        final String driverClassname = StroomProperties.getProperty(JDBC_DRIVER_CLASS_NAME);
//        final String driverUrl = StroomProperties.getProperty(JDBC_DRIVER_URL);
//        final String driverUsername = StroomProperties.getProperty(JDBC_DRIVER_USERNAME);
//        final String driverPassword = StroomProperties.getProperty(JDBC_DRIVER_PASSWORD);
//
//        if (driverClassname == null || driverUrl == null) {
//            LOGGER.error(MarkerFactory.getMarker("FATAL"), "Properties are not set for DB connection");
//            throw new RuntimeException("Properties are not set for DB connection");
//        }
//
//        try {
//            Class.forName(driverClassname);
//        } catch (final ClassNotFoundException e) {
//            throw new RuntimeException(e);
//        }
//
//        LOGGER.info("Connecting to database using classname: {}, url: {}, username: {}", driverClassname, driverUrl, driverUsername);
//
//        return DriverManager.getConnection(driverUrl, driverUsername, driverPassword);
//
//    public static void close(final Connection connection) {
//        if (connection != null) {
//            try {
//                connection.close();
//            } catch (final SQLException ex) {
//                LOGGER.error(ex, ex);
//            }
//        }
//    }
//
//    public static boolean tableExists(final Connection connection, final String tableName) throws SQLException {
//        boolean hasTable = false;
//        final DatabaseMetaData databaseMetaData = connection.getMetaData();
//        try (final ResultSet resultSet = databaseMetaData.getTables(null, null, tableName, new String[]{"TABLE"})) {
//            hasTable = resultSet.next();
//        }
//        return hasTable;
//    }

    @SuppressWarnings("SQL_PREPARED_STATEMENT_GENERATED_FROM_NONCONSTANT_STRING")
    public static int executeUpdate(final Connection connection, final String sql, final List<Object> args)
            throws SQLException {
        LOGGER.debug(">>> {}", sql);
        final LogExecutionTime logExecutionTime = new LogExecutionTime();
        try (final PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            PreparedStatementUtil.setArguments(preparedStatement, args);
            final int result = preparedStatement.executeUpdate();

            log(logExecutionTime, () -> Integer.toString(result), () -> sql, args);

            return result;
        } catch (final SQLException sqlException) {
            LOGGER.error("executeUpdate() - {} {}", new Object[]{sql, args}, sqlException);
            throw sqlException;
        }
    }

//    @SuppressWarnings("SQL_PREPARED_STATEMENT_GENERATED_FROM_NONCONSTANT_STRING")
//    public static List<Long> executeInsert(final Connection connection, final String sql, final List<Object> args)
//            throws SQLException {
//        LOGGER.debug(">>> {}", sql);
//        final List<Long> keyList = new ArrayList<>();
//        final LogExecutionTime logExecutionTime = new LogExecutionTime();
//        try (final PreparedStatement preparedStatement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
//            PreparedStatementUtil.setArguments(preparedStatement, args);
//            final int result = preparedStatement.executeUpdate();
//
//            try (final ResultSet keySet = preparedStatement.getGeneratedKeys()) {
//                while (keySet.next()) {
//                    keyList.add(keySet.getLong(1));
//                }
//            }
//
//            log(logExecutionTime, result, sql, args);
//
//            return keyList;
//        } catch (final SQLException sqlException) {
//            LOGGER.error("executeUpdate() - " + sql + " " + args, sqlException);
//            throw sqlException;
//        }
//    }

    public static void executeStatement(final Connection connection, final String sql) throws SQLException {
        executeStatements(connection, Collections.singletonList(sql));
    }

    public static void executeStatements(final Connection connection, final List<String> sqlStatements) throws SQLException {
        LOGGER.debug(">>> %s", sqlStatements);
        final LogExecutionTime logExecutionTime = new LogExecutionTime();
        try (final Statement statement = connection.createStatement()) {

            sqlStatements.forEach(sql -> {
                try {
                    statement.addBatch(sql);
                } catch (SQLException e) {
                    throw new RuntimeException(String.format("Error adding sql [%s] to batch", sql), e);
                }
            });
            int[] results = statement.executeBatch();
            boolean isFailure = Arrays.stream(results)
                    .anyMatch(val -> val == Statement.EXECUTE_FAILED);

            if (isFailure) {
                throw new RuntimeException(String.format("Got error code for batch %s", sqlStatements));
            }

            log(logExecutionTime,
                    () -> Arrays.stream(results)
                            .mapToObj(Integer::toString)
                            .collect(Collectors.joining(",")),
                    sqlStatements::toString,
                    Collections.emptyList());

        } catch (final RuntimeException e) {
            LOGGER.error("executeStatement() - " + sqlStatements, e);
            throw e;
        }
    }

    @SuppressWarnings("SQL_PREPARED_STATEMENT_GENERATED_FROM_NONCONSTANT_STRING")
    public static Long executeQueryLongResult(final Connection connection, final String sql, final List<Object> args)
            throws SQLException {
        LOGGER.debug(">>> {}", sql);
        final LogExecutionTime logExecutionTime = new LogExecutionTime();
        Long result = null;

        try (final PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            PreparedStatementUtil.setArguments(preparedStatement, args);
            try (final ResultSet resultSet = preparedStatement.executeQuery()) {
                if (resultSet.next()) {
                    result = SqlUtil.getLong(resultSet, 1);
                }
            }

            log(logExecutionTime, result, sql, args);

            return result;
        } catch (final SQLException sqlException) {
            LOGGER.error("executeQueryLongResult() - " + sql + " " + args, sqlException);
            throw sqlException;
        }
    }

//    public static BaseResultList<SummaryDataRow> executeQuerySummaryDataResult(final Connection connection,
//                                                                               final String sql,
//                                                                               final int numberKeys,
//                                                                               final List<Object> args,
//                                                                               final List<? extends HasPrimitiveValue> stats,
//                                                                               final PrimitiveValueConverter<? extends HasPrimitiveValue> converter) throws SQLException {
//        LOGGER.debug(">>> %s", sql);
//        final LogExecutionTime logExecutionTime = new LogExecutionTime();
//        final ArrayList<SummaryDataRow> summaryData = new ArrayList<>();
//        try {
//            try (final PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
//                PreparedStatementUtil.setArguments(preparedStatement, args);
//                try (final ResultSet resultSet = preparedStatement.executeQuery()) {
//                    while (resultSet.next()) {
//                        final SummaryDataRow summaryDataRow = new SummaryDataRow();
//                        int pos = 1;
//                        for (int i = 0; i < numberKeys; i++) {
//                            summaryDataRow.getKey().add(resultSet.getLong(pos++));
//                        }
//                        for (int i = 0; i < numberKeys; i++) {
//                            summaryDataRow.getLabel().add(resultSet.getString(pos++));
//                        }
//                        summaryDataRow.setCount(resultSet.getLong(pos++));
//                        summaryData.add(summaryDataRow);
//                    }
//                }
//            }
//            log(logExecutionTime, summaryData, sql, args);
//
//            return BaseResultList.createUnboundedList(summaryData);
//        } catch (final SQLException sqlException) {
//            LOGGER.error("executeQueryLongResult() - " + sql + " " + args, sqlException);
//            throw sqlException;
//        }
//    }

    private static void log(final LogExecutionTime logExecutionTime,
                            final Object result,
                            final String sql,
                            final List<Object> args) {
        if (result == null) {
            log(logExecutionTime, () -> "", () -> sql, args);
        } else {
            log(logExecutionTime, result::toString, () -> sql, args);
        }
    }

    private static void log(final LogExecutionTime logExecutionTime,
                            final Supplier<String> resultSupplier,
                            final Supplier<String> sqlSupplier,
                            final List<Object> args) {
        final long time = logExecutionTime.getDuration();
        if (LOGGER.isDebugEnabled() || time > 1000) {
            final String message = "<<< " + sqlSupplier.get() + " " + args + " took " + ModelStringUtil.formatDurationString(time)
                    + " with result " + resultSupplier.get();
            if (time > 1000) {
                LOGGER.warn(message);
            } else {
                LOGGER.debug(message);
            }
        }
    }
}
