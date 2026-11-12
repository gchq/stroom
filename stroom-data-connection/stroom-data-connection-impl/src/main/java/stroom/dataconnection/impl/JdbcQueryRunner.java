/*
 * Copyright 2026 Crown Copyright
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

package stroom.dataconnection.impl;

import stroom.dataconnection.shared.DataConnectionDoc;
import stroom.dataconnection.shared.JdbcConnectionSettings;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import com.mysql.cj.jdbc.JdbcConnection;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/// Runs the SQL of a query against a JDBC Data Connection.
///
/// Statements are forward-only and read-only, on a read-only connection from the document's pool.
/// The query timeout, row limit and fetch size come from the document, within the administrator's
/// ceilings. Placeholder values are always bound, never pasted into the SQL.
@Singleton
public class JdbcQueryRunner {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(JdbcQueryRunner.class);

    private final JdbcConnectionPoolCache poolCache;
    private final Provider<DataConnectionConfig> configProvider;

    /// Creates the instance. Guice supplies every argument.
    @Inject
    JdbcQueryRunner(final JdbcConnectionPoolCache poolCache,
                    final Provider<DataConnectionConfig> configProvider) {
        this.poolCache = poolCache;
        this.configProvider = configProvider;
    }

    /// Finds the columns the SQL returns, without running it.
    ///
    /// The SQL is prepared and the driver asked to describe it. MySQL's driver normally prepares
    /// statements on the client and runs them to describe them, which for a slow query is as slow as
    /// the query, so on MySQL the statement is prepared on the server instead, which describes it
    /// without running it. A driver that cannot describe a statement before running it is given
    /// `select * from (<sql>) q where 1 = 0` instead. Placeholders are bound to `NULL`, which is
    /// enough to describe the statement.
    ///
    /// @param doc       The Data Connection.
    /// @param parsedSql The query's SQL.
    /// @return The columns, in order.
    /// @throws SQLException If the database refuses the SQL.
    public List<SqlColumn> describe(final DataConnectionDoc doc, final ParsedSql parsedSql) throws SQLException {
        final SqlQueryLimits limits = SqlQueryLimits.of(doc, configProvider.get());
        return poolCache.useConnection(doc.getJdbc(), connection -> {
            if (connection.isWrapperFor(JdbcConnection.class)) {
                try (final PreparedStatement statement = connection.unwrap(JdbcConnection.class)
                        .serverPrepareStatement(parsedSql.jdbcSql())) {
                    final ResultSetMetaData metaData = statement.getMetaData();
                    if (metaData != null) {
                        return SqlTypeMapper.toColumns(metaData);
                    }
                } catch (final SQLException e) {
                    // The server can refuse to prepare some statements, or have no room for more
                    // prepared statements, so describe it as for other databases.
                    LOGGER.debug(() -> LogUtil.message("MySQL could not prepare the SQL on the server: {}",
                            e.getMessage()));
                }
            }

            try (final PreparedStatement statement = prepare(connection, parsedSql.jdbcSql(), limits)) {
                bindNulls(statement, parsedSql);
                final ResultSetMetaData metaData = statement.getMetaData();
                if (metaData != null) {
                    return SqlTypeMapper.toColumns(metaData);
                }
            }

            LOGGER.debug("The driver cannot describe a statement before running it, so running it with no rows");
            // The new line ends any comment at the end of the SQL.
            final String emptySql = "select * from (" + parsedSql.jdbcSql() + "\n) q where 1 = 0";
            try (final PreparedStatement statement = prepare(connection, emptySql, limits)) {
                bindNulls(statement, parsedSql);
                try (final ResultSet resultSet = statement.executeQuery()) {
                    return SqlTypeMapper.toColumns(resultSet.getMetaData());
                }
            }
        });
    }

    /// Runs the SQL and passes its rows to a row consumer.
    ///
    /// @param doc                The Data Connection.
    /// @param parsedSql          The query's SQL.
    /// @param values             The placeholder values from [SqlParameterBinder#resolveValues].
    /// @param rowConsumerFactory Makes the row consumer once the columns are known.
    /// @param cancellation       Stops the statement when the search is cancelled.
    /// @return What the run read.
    /// @throws SQLException If the database refuses the SQL or fails while it runs.
    public QueryRunSummary run(final DataConnectionDoc doc,
                          final ParsedSql parsedSql,
                          final List<Object> values,
                          final Function<List<SqlColumn>, SqlRowConsumer> rowConsumerFactory,
                          final QueryCancellation cancellation) throws SQLException {
        final SqlQueryLimits limits = SqlQueryLimits.of(doc, configProvider.get());
        final int fetchSize = Objects.requireNonNullElse(
                doc.getJdbc().getFetchSize(),
                JdbcConnectionSettings.DEFAULT_FETCH_SIZE);

        return poolCache.useConnection(doc.getJdbc(), connection -> {
            try (final PreparedStatement statement = prepare(connection, parsedSql.jdbcSql(), limits)) {
                cancellation.register(statement);
                try {
                    // Ask for one row more than the limit, to know when the limit cut the result short.
                    final int statementMaxRows = statementMaxRows(limits);
                    statement.setMaxRows(statementMaxRows);
                    statement.setFetchSize(fetchSize(fetchSize, statementMaxRows));
                    SqlParameterBinder.bind(statement, values);
                    try (final ResultSet resultSet = statement.executeQuery()) {
                        return readRows(resultSet, limits, rowConsumerFactory, cancellation);
                    }
                } finally {
                    cancellation.unregister();
                }
            }
        });
    }

    private static QueryRunSummary readRows(final ResultSet resultSet,
                                       final SqlQueryLimits limits,
                                       final Function<List<SqlColumn>, SqlRowConsumer> rowConsumerFactory,
                                       final QueryCancellation cancellation) throws SQLException {
        final List<SqlColumn> columns = Collections.unmodifiableList(
                new ArrayList<>(SqlTypeMapper.toColumns(resultSet.getMetaData())));
        final SqlRowConsumer rowConsumer = rowConsumerFactory.apply(columns);
        final SqlRowConsumer.ColumnReader reader = columnIndex ->
                SqlTypeMapper.readValue(resultSet, columnIndex + 1, columns.get(columnIndex));

        long rowsRead = 0;
        long rowsPassed = 0;
        boolean limitReached = false;
        while (!cancellation.isCancelled() && resultSet.next()) {
            if (rowsRead >= limits.maxRows()) {
                limitReached = true;
                break;
            }
            rowsRead++;
            if (rowConsumer.accept(reader)) {
                rowsPassed++;
            }
        }
        return new QueryRunSummary(columns, rowConsumer.getUnmatchedFields(), rowsRead, rowsPassed, limitReached,
                limits.maxRows(), null);
    }

    /// @param limits The query's limits.
    /// @return The row limit to give the statement: one more than the query's, so a result cut short
    ///         by the limit can be told apart from one that fits it exactly.
    static int statementMaxRows(final SqlQueryLimits limits) {
        return limits.maxRows() == Integer.MAX_VALUE
                ? 0
                : limits.maxRows() + 1;
    }

    /// @param fetchSize        The document's fetch size.
    /// @param statementMaxRows The statement's row limit, or 0 for none.
    /// @return The fetch size to give the statement. Drivers refuse one larger than the row limit.
    static int fetchSize(final int fetchSize, final int statementMaxRows) {
        final int atLeastZero = Math.max(0, fetchSize);
        return statementMaxRows > 0
                ? Math.min(atLeastZero, statementMaxRows)
                : atLeastZero;
    }

    private static PreparedStatement prepare(final Connection connection,
                                             final String sql,
                                             final SqlQueryLimits limits) throws SQLException {
        final PreparedStatement statement = connection.prepareStatement(
                sql,
                ResultSet.TYPE_FORWARD_ONLY,
                ResultSet.CONCUR_READ_ONLY);
        statement.setQueryTimeout(limits.timeoutSeconds());
        return statement;
    }

    private static void bindNulls(final PreparedStatement statement, final ParsedSql parsedSql) throws SQLException {
        for (int i = 1; i <= parsedSql.placeholders().size(); i++) {
            statement.setNull(i, Types.VARCHAR);
        }
    }

}
