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
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogExecutionTime;
import stroom.util.shared.ModelStringUtil;
import stroom.util.time.StroomDuration;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

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

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ConnectionUtil.class);

    private final Provider<SQLStatisticsConfig> sqlStatisticsConfigProvider;

    @Inject
    public ConnectionUtil(final Provider<SQLStatisticsConfig> sqlStatisticsConfigProvider) {
        this.sqlStatisticsConfigProvider = sqlStatisticsConfigProvider;
    }

    @SuppressWarnings("SQL_PREPARED_STATEMENT_GENERATED_FROM_NONCONSTANT_STRING")
    public int executeUpdate(final Connection connection, final String sql, final List<Object> args)
            throws SQLException {
        logSql(sql, args);
        final LogExecutionTime logExecutionTime = new LogExecutionTime();
        try (final PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            PreparedStatementUtil.setArguments(preparedStatement, args);
            final int result = preparedStatement.executeUpdate();

            logExecution(logExecutionTime, () -> Integer.toString(result), () -> sql, args);

            return result;
        } catch (final SQLException sqlException) {
            LOGGER.error(() -> "executeUpdate() - " + buildSQLTrace(sql, args), sqlException);
            throw sqlException;
        }
    }

    public void executeStatement(final Connection connection, final String sql) throws SQLException {
        executeStatements(connection, Collections.singletonList(sql));
    }

    public void executeStatements(final Connection connection, final List<String> sqlStatements)
            throws SQLException {
        LOGGER.debug(() -> ">>> " + sqlStatements.stream()
                .map(ConnectionUtil::cleanSqlForLogs)
                .collect(Collectors.joining("; ")));

        final LogExecutionTime logExecutionTime = new LogExecutionTime();
        try (final Statement statement = connection.createStatement()) {

            sqlStatements.forEach(sql -> {
                try {
                    statement.addBatch(sql);
                } catch (final SQLException e) {
                    throw new RuntimeException(String.format("Error adding sql [%s] to batch", sql), e);
                }
            });
            final int[] results = statement.executeBatch();
            final boolean isFailure = Arrays.stream(results)
                    .anyMatch(val -> val == Statement.EXECUTE_FAILED);

            if (isFailure) {
                throw new RuntimeException(String.format("Got error code for batch %s", sqlStatements));
            }

            logExecution(logExecutionTime,
                    () -> Arrays.stream(results)
                            .mapToObj(Integer::toString)
                            .collect(Collectors.joining(",")),
                    sqlStatements::toString,
                    Collections.emptyList());

        } catch (final RuntimeException e) {
            LOGGER.error(() -> "executeStatement() - " + sqlStatements, e);
            throw e;
        }
    }

    @SuppressWarnings("SQL_PREPARED_STATEMENT_GENERATED_FROM_NONCONSTANT_STRING")
    public Long executeQueryLongResult(final Connection connection, final String sql, final List<Object> args)
            throws SQLException {
        logSql(sql, args);
        final LogExecutionTime logExecutionTime = new LogExecutionTime();
        Long result = null;

        try (final PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            PreparedStatementUtil.setArguments(preparedStatement, args);
            try (final ResultSet resultSet = preparedStatement.executeQuery()) {
                if (resultSet.next()) {
                    result = DbUtil.getLong(resultSet, 1);
                }
            }

            logExecution(logExecutionTime, result, sql, args);

            return result;
        } catch (final SQLException sqlException) {
            LOGGER.error(() -> "executeQueryLongResult() - " + sql + " " + args, sqlException);
            throw sqlException;
        }
    }

    private static void logSql(final String sql,
                               final List<Object> args) {
        if (LOGGER.isDebugEnabled()) {
            final String formattedSql = buildSQLTrace(sql, args);
            LOGGER.debug(">>> " + formattedSql);
        }
    }

    private void logExecution(final LogExecutionTime logExecutionTime,
                              final Object result,
                              final String sql,
                              final List<Object> args) {
        if (result == null) {
            logExecution(logExecutionTime, () -> "", () -> sql, args);
        } else {
            logExecution(logExecutionTime, result::toString, () -> sql, args);
        }
    }

    private void logExecution(final LogExecutionTime logExecutionTime,
                              final Supplier<String> resultSupplier,
                              final Supplier<String> sqlSupplier,
                              final List<Object> args) {
        final long time = logExecutionTime.getDurationMs();
        final StroomDuration warningThreshold = sqlStatisticsConfigProvider.get()
                .getSlowQueryWarningThreshold();
        final long warningThresholdMs = warningThreshold.toMillis();

        if (LOGGER.isDebugEnabled() || isSlowQueryWarningRequired(time, warningThresholdMs)) {
            final String sql = buildSQLTrace(sqlSupplier.get(), args);
            final String message = "<<< " + sql
                    + " took " + ModelStringUtil.formatDurationString(time)
                    + " with result " + resultSupplier.get() + ".";
            if (isSlowQueryWarningRequired(time, warningThresholdMs)) {
                LOGGER.warn(() -> message
                        + " Warning threshold: " + warningThreshold
                        + ". Set property stroom.statistics.sql.slowQueryWarningThreshold to change this threshold.");
            } else {
                LOGGER.debug(() -> message);
            }
        }
    }

    private boolean isSlowQueryWarningRequired(final long timeMs, final long warningThresholdMs) {
        return warningThresholdMs > 0 && timeMs > warningThresholdMs;
    }

    private static String cleanSqlForLogs(final String sql) {
        return sql.replaceAll("\\s+", " ");
    }

    private static String buildSQLTrace(final String sql, final List<Object> args) {
        final StringBuilder sqlString = new StringBuilder();
        int arg = 0;
        for (int i = 0; i < sql.length(); i++) {
            final char c = sql.charAt(i);
            if (c == '?') {
                try {
                    sqlString.append(args.get(arg++));
                } catch (final IndexOutOfBoundsException e) {
                    LOGGER.warn("Mismatch between '?' and args. sql: {}, args: {}", sql, args);
                    sqlString.append(c);
                }
            } else {
                sqlString.append(c);
            }
        }
        return cleanSqlForLogs(sqlString.toString());
    }
}
