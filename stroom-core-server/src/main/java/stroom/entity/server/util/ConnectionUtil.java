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

import com.google.common.base.Preconditions;
import com.google.common.base.Supplier;
import stroom.entity.shared.BaseResultList;
import stroom.entity.shared.HasPrimitiveValue;
import stroom.entity.shared.PrimitiveValueConverter;
import stroom.entity.shared.SummaryDataRow;
import stroom.util.collections.BatchingIterator;
import stroom.util.config.StroomProperties;
import stroom.util.logging.LogExecutionTime;
import stroom.util.logging.StroomLogger;
import stroom.util.shared.ModelStringUtil;

import javax.annotation.concurrent.NotThreadSafe;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class ConnectionUtil {
    private static final StroomLogger LOGGER = StroomLogger.getLogger(ConnectionUtil.class);

    public static final String JDBC_DRIVER_CLASS_NAME = "stroom.jdbcDriverClassName";
    public static final String JDBC_DRIVER_URL = "stroom.jdbcDriverUrl";
    public static final String JDBC_DRIVER_USERNAME = "stroom.jdbcDriverUsername";
    public static final String JDBC_DRIVER_PASSWORD = "stroom.jdbcDriverPassword";
    public static final String MULTI_INSERT_BATCH_SIZE = "stroom.databaseMultiInsertMaxBatchSize";
    public static final int MULTI_INSERT_BATCH_SIZE_DEFAULT = 500;

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

    public static final int getMultiInsertMaxBatchSize() {
        return StroomProperties.getIntProperty(MULTI_INSERT_BATCH_SIZE, MULTI_INSERT_BATCH_SIZE_DEFAULT);
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
        final ResultSet resultSet = databaseMetaData.getTables(null, null, tableName, new String[]{"TABLE"});
        final boolean hasTable = resultSet.next();
        resultSet.close();
        return hasTable;
    }

    @edu.umd.cs.findbugs.annotations.SuppressWarnings("SQL_PREPARED_STATEMENT_GENERATED_FROM_NONCONSTANT_STRING")
    public static int executeUpdate(final Connection connection, final String sql, final List<Object> args)
            throws SQLException {
        LOGGER.debug(">>> %s", sql);
        final LogExecutionTime logExecutionTime = new LogExecutionTime();
        try {
            final PreparedStatement preparedStatement = connection.prepareStatement(sql);
            PreparedStatementUtil.setArguments(preparedStatement, args);
            final int result = preparedStatement.executeUpdate();

            preparedStatement.close();

            log(logExecutionTime, result, () -> sql, args);

            return result;
        } catch (final SQLException sqlException) {
            LOGGER.error("executeUpdate() - %s %s", sql, args, sqlException);
            throw sqlException;
        }
    }

    @edu.umd.cs.findbugs.annotations.SuppressWarnings("SQL_PREPARED_STATEMENT_GENERATED_FROM_NONCONSTANT_STRING")
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

    @edu.umd.cs.findbugs.annotations.SuppressWarnings("SQL_PREPARED_STATEMENT_GENERATED_FROM_NONCONSTANT_STRING")
    public static Long executeQueryLongResult(final Connection connection, final String sql, final List<Object> args)
            throws SQLException {
        LOGGER.debug(">>> %s", sql);
        final LogExecutionTime logExecutionTime = new LogExecutionTime();
        Long result = null;

        try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            PreparedStatementUtil.setArguments(preparedStatement, args);
            try (ResultSet resultSet = preparedStatement.executeQuery()) {
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

    @edu.umd.cs.findbugs.annotations.SuppressWarnings("SQL_PREPARED_STATEMENT_GENERATED_FROM_NONCONSTANT_STRING")
    public static BaseResultList<SummaryDataRow> executeQuerySummaryDataResult(final Connection connection,
                                                                               final String sql,
                                                                               final int numberKeys,
                                                                               final List<Object> args,
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

    @edu.umd.cs.findbugs.annotations.SuppressWarnings("SQL_PREPARED_STATEMENT_GENERATED_FROM_NONCONSTANT_STRING")
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

    private static void log(final LogExecutionTime logExecutionTime,
                            final Object result,
                            final String sql,
                            final List<Object> args) {
        log(logExecutionTime, result, () -> sql, args);

    }
    private static void log(final LogExecutionTime logExecutionTime,
                            final Object result,
                            final Supplier<String> sqlSupplier,
                            final List<Object> args) {
        final long time = logExecutionTime.getDuration();
        if (LOGGER.isDebugEnabled() || time > 1000) {
            final String message = "<<< " + sqlSupplier.get() + " " + args + " took " + ModelStringUtil.formatDurationString(time)
                    + " with result " + result;
            if (time > 1000) {
                LOGGER.warn(message);
            } else {
                LOGGER.debug(message);
            }
        }
    }


    /**
     * Class for doing INSERT INTO x VALUES (...), (...), ... (...) type statements,
     * with a manageable batch size and re-using the prepared statements.
     * This is to avoid using a hibernate native sql approach that will
     * cache a query plan for each unique query. An insert with two rows is considered different to
     * an insert with three rows so the cache quickly fills up with hugh insert queries, each with
     * MANY param objects.
     */
    @NotThreadSafe
    public static class MultiInsertExecutor implements AutoCloseable {

        private final Map<Integer, PreparedStatement> preparedStatements = new HashMap<>();
        private final Connection connection;
        private final int columnCount;
        private final int maxBatchSize;
        private final String sqlHeader;
        private final String argsStr;

        /**
         * @param connection  The DB Connection
         * @param tableName   The name of the table, case sensitive if applicable
         * @param columnNames List of columns to insert values into, case sensitive if the DB is
         */
        public MultiInsertExecutor(final Connection connection,
                                   final String tableName,
                                   final List<String> columnNames) {

            this.connection = Preconditions.checkNotNull(connection);
            Preconditions.checkNotNull(tableName);
            Preconditions.checkNotNull(columnNames);
            this.columnCount = columnNames.size();
            this.maxBatchSize = getMultiInsertMaxBatchSize();

            final String columnNamesStr = columnNames.stream()
                    .collect(Collectors.joining(","));

            //build up the sql stmt
            final StringBuilder stringBuilder = new StringBuilder("INSERT INTO ")
                    .append(tableName)
                    .append(" (")
                    .append(columnNamesStr)
                    .append(") VALUES ");

            sqlHeader = stringBuilder.toString();

            //build args for one row
            this.argsStr = "(" + columnNames.stream()
                    .map(c -> "?")
                    .collect(Collectors.joining(",")) + ")";
        }


        /**
         * This method inserts multiple rows into a table, with many rows per statement as controlled
         * by a batch size property. This is to avoid using a hibernate native sql approach that will
         * cache a query plan for each unique query. An insert with two rows is considered different to
         * an insert with three rows so the cache quickly fills up with hugh insert queries, each with
         * MANY param objects.
         *
         * @param argsList    A List of args (in columnName order), one list of args for each row, will
         *                    be inserted in list order. Each sub list must have the same size as columnNames
         */
        public void execute(final List<List<Object>> argsList) {
            execute(argsList, false);
        }

        /**
         * This method inserts multiple rows into a table, with many rows per statement as controlled
         * by a batch size property. This is to avoid using a hibernate native sql approach that will
         * cache a query plan for each unique query. An insert with two rows is considered different to
         * an insert with three rows so the cache quickly fills up with hugh insert queries, each with
         * MANY param objects.
         *
         * @param argsList    A List of args (in columnName order), one list of args for each row, will
         *                    be inserted in list order. Each sub list must have the same size as columnNames
         * @return The generated IDs for each row inserted
         */
        public List<Long> executeAndFetchKeys(final List<List<Object>> argsList) {
            return execute(argsList, true);
        }

        private List<Long> execute(final List<List<Object>> argsList,
                                   boolean areKeysRequired) {

            final Instant startTime = Instant.now();
            final List<Long> ids;
            if (argsList.size() > 0) {
                validateArgsList(argsList);

                //batch up the inserts
                ids = BatchingIterator.batchedStreamOf(argsList.stream(), maxBatchSize)
                        .flatMap(argsBatch -> {
                            List<Long> batchIds = executeBatch(argsBatch, areKeysRequired);
                            return batchIds.stream();
                        })
                        .collect(Collectors.toList());
            } else {
                ids = Collections.emptyList();
            }
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("execute completed in %s for %s rows",
                        Duration.between(startTime, Instant.now()), argsList.size());
            }
            return ids;
        }

        private PreparedStatement getOrCreatePreparedStatement(final int batchSize,
                                                               final boolean areKeysRequired) {

            PreparedStatement preparedStatement = preparedStatements.get(batchSize);
            if (preparedStatement == null) {
                preparedStatement = createPreparedStatement(batchSize, areKeysRequired);
                preparedStatements.put(batchSize, preparedStatement);
            } else {
                //ensure it is cleaned after last use
                try {
                    preparedStatement.clearParameters();
                } catch (SQLException e) {
                    throw new RuntimeException("Error clearing parameters on preparedStatement", e);
                }
            }
            return preparedStatement;
        }

        private PreparedStatement createPreparedStatement(final int batchSize,
                                                          final boolean areKeysRequired) {

            //combine all row's args together
            final String argsSection = IntStream.rangeClosed(1, batchSize)
                    .boxed()
                    .map(i -> argsStr)
                    .collect(Collectors.joining(","));

            StringBuilder sql = new StringBuilder(sqlHeader)
                    .append(argsSection);

            try {
                if (areKeysRequired) {
                    return connection.prepareStatement(sql.toString(), Statement.RETURN_GENERATED_KEYS);
                } else {
                    return connection.prepareStatement(sql.toString());
                }
            } catch (SQLException e) {
                throw new RuntimeException(String.format("Error creating prepared statement for sql: %s",
                        sql.toString()), e);
            }
        }

        private List<Long> executeBatch(final List<List<Object>> argsList,
                                        final boolean areKeysRequired) {

            final int batchSize = argsList.size();
            final PreparedStatement preparedStatement = getOrCreatePreparedStatement(
                    batchSize,
                    areKeysRequired);

            final List<Object> allArgs = argsList.stream()
                    .flatMap(Collection::stream)
                    .collect(Collectors.toList());

            final List<Long> keyList;
            final LogExecutionTime logExecutionTime = new LogExecutionTime();
            try {
                PreparedStatementUtil.setArguments(preparedStatement, allArgs);
                final int result = preparedStatement.executeUpdate();

                if (areKeysRequired) {
                    keyList = new ArrayList<>();
                    try (ResultSet keySet = preparedStatement.getGeneratedKeys()) {
                        while (keySet.next()) {
                            keyList.add(keySet.getLong(1));
                        }
                    }
                } else {
                    keyList = Collections.emptyList();
                }

                log(logExecutionTime, result, preparedStatement::toString, allArgs);
                return keyList;

            } catch (final SQLException sqlException) {
                LOGGER.error("executeUpdate() - " + preparedStatement.toString() + " " + allArgs, sqlException);
                throw new RuntimeException(String.format("Error executing preparedStatement: %s",
                        preparedStatement), sqlException);
            }
        }

        private void validateArgsList(final List<List<Object>> argsList) {
            boolean areAllArgsCorrectLength = argsList.stream()
                    .allMatch(args -> args.size() == columnCount);

            if (!areAllArgsCorrectLength) {
                String arsSizes = argsList.stream()
                        .map(args -> String.valueOf(args.size()))
                        .distinct()
                        .collect(Collectors.joining(","));

                throw new RuntimeException(String.format("Not all args match the number of columns [%s], distinct args counts: [%s]",
                        columnCount, arsSizes));
            }
        }

        @Override
        public void close() throws Exception {
            final boolean allClosedWithoutError = preparedStatements.values().stream()
                    .filter(Objects::nonNull)
                    .allMatch(stmt -> {
                        try {
                            stmt.close();
                        } catch (SQLException e) {
                            LOGGER.error(String.format("Error closing prepareStatement %s", stmt), e);
                            //swallow exception so we can keep closing statements (we will handle it later)
                            return false;
                        }
                        return true;
                    });
            if (!allClosedWithoutError) {
                throw new RuntimeException("Error closing prepareStatements, see ERRORs in logs");
            }
        }
    }

}
