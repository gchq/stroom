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

package stroom.externaldatasource.impl;

import stroom.externaldatasource.shared.ExternalDataSourceDoc;
import stroom.externaldatasource.shared.ExternalDataSourceTestResponse;
import stroom.externaldatasource.shared.ExternalDataSourceType;
import stroom.externaldatasource.shared.JdbcConnectionSettings;
import stroom.query.api.DateTimeSettings;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.Query;
import stroom.query.api.datasource.QueryField;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;

import com.zaxxer.hikari.HikariDataSource;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/// Queries a database through a JDBC driver, with the SQL given in each query.
///
/// ## The time range
///
/// The SQL decides how the query's time range is used:
///
/// - If the SQL has `:from` or `:to`, the time range is bound to them and the database filters.
/// - Otherwise, if the document names a time column, rows are filtered on it in Stroom.
/// - Otherwise the time range is not used, and the query warns that it was ignored.
@Singleton
class JdbcConnector implements Connector {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(JdbcConnector.class);

    static final String READ_ONLY_ADVICE = "Use a database account that can only read. Stroom asks for "
                                           + "read-only connections, but only the database can enforce it.";
    static final String TIME_RANGE_IGNORED = "The time range was ignored, as the SQL has no :from or :to "
                                             + "placeholder and the External Data Source has no time column.";

    private final JdbcQueryRunner queryRunner;
    private final JdbcConnectionResolver connectionResolver;
    private final JdbcDriverLoader driverLoader;
    private final SqlFieldCache fieldCache;

    /// Creates the connector. Guice supplies every argument.
    @Inject
    JdbcConnector(final JdbcQueryRunner queryRunner,
                  final JdbcConnectionResolver connectionResolver,
                  final JdbcDriverLoader driverLoader,
                  final SqlFieldCache fieldCache) {
        this.queryRunner = queryRunner;
        this.connectionResolver = connectionResolver;
        this.driverLoader = driverLoader;
        this.fieldCache = fieldCache;
    }

    @Override
    public ExternalDataSourceType getType() {
        return ExternalDataSourceType.JDBC;
    }

    /// The SQL of a JDBC External Data Source is given in each query.
    @Override
    public boolean takesNativeQuery() {
        return true;
    }

    /// The fields are the columns the SQL returns, described by the database and cached.
    @Override
    public List<QueryField> getFields(final ExternalDataSourceDoc doc, final String sql) {
        requireUrl(doc);
        final ParsedSql parsedSql = SqlStatementParser.parse(sql);
        // The cache may answer without connecting, so check here that the user may use the
        // credential, as connecting would.
        connectionResolver.resolve(doc.getJdbc());
        final List<SqlColumn> columns = fieldCache.get(doc, sql, ignored -> {
            try {
                return queryRunner.describe(doc, parsedSql);
            } catch (final SQLException e) {
                throw new RuntimeException("The database could not describe the SQL: " + e.getMessage(), e);
            }
        });
        return SqlColumn.toQueryFields(columns);
    }

    /// JDBC sources have no minimum unless the document sets one.
    @Override
    public int getMinRefreshMinutes(final ExternalDataSourceDoc doc) {
        final Integer minutes = NullSafe.get(doc.getJdbc(), JdbcConnectionSettings::getMinRefreshMinutes);
        return Math.max(0, Objects.requireNonNullElse(minutes, 0));
    }

    @Override
    public PreparedQuery prepare(final ExternalDataSourceDoc doc,
                                 final Query query,
                                 final DateTimeSettings dateTimeSettings) {
        requireUrl(doc);
        final String sql = query.getNativeQuery();
        final ParsedSql parsedSql = SqlStatementParser.parse(sql);
        final List<Object> values = SqlParameterBinder.resolveValues(
                parsedSql,
                query.getTimeRange(),
                query.getParams(),
                dateTimeSettings);
        final ExpressionOperator expression = parsedSql.usesTimeRange()
                ? query.getExpression()
                : TimeRanges.addTimeRange(query.getExpression(), query.getTimeRange(), doc.getTimeField());
        final boolean filtersOnTimeColumn = expression != query.getExpression();
        final List<String> warnings = TimeRanges.isSet(query.getTimeRange())
                                      && !parsedSql.usesTimeRange()
                                      && NullSafe.isBlankString(doc.getTimeField())
                ? List.of(TIME_RANGE_IGNORED)
                : List.of();

        return new PreparedQuery(expression, warnings, (rowConsumerFactory, cancellation, progress) -> {
            final Function<List<SqlColumn>, SqlRowConsumer> checkedFactory = columns -> {
                fieldCache.put(doc, sql, columns);
                if (filtersOnTimeColumn) {
                    requireTimeColumn(doc, columns);
                }
                return rowConsumerFactory.apply(columns);
            };
            return queryRunner.run(doc, parsedSql, values, checkedFactory, cancellation);
        });
    }

    /// Opens a connection with the document's settings and reports what it connected to.
    @Override
    public ExternalDataSourceTestResponse test(final ExternalDataSourceDoc doc) {
        try {
            final ResolvedJdbcConnection resolvedConnection = connectionResolver.resolve(doc.getJdbc());
            driverLoader.ensureLoaded();
            // A pool of one, closed straight after, so a test never leaves a pool behind.
            try (final HikariDataSource dataSource = JdbcDataSourceFactory.create(resolvedConnection, 1);
                    final Connection connection = dataSource.getConnection()) {
                return new ExternalDataSourceTestResponse(true, describe(connection.getMetaData()));
            }
        } catch (final SQLException | RuntimeException e) {
            LOGGER.debug(() -> LogUtil.message("Connection test failed for {}", doc.getUuid()), e);
            return new ExternalDataSourceTestResponse(false, e.getMessage());
        }
    }

    private static String describe(final DatabaseMetaData metaData) throws SQLException {
        return "Connected to " + metaData.getDatabaseProductName() + " " + metaData.getDatabaseProductVersion()
               + "\nDriver: " + metaData.getDriverName() + " " + metaData.getDriverVersion()
               + "\nUser: " + metaData.getUserName()
               + "\n\n" + READ_ONLY_ADVICE;
    }

    // The time range is filtered on the document's time column, so the SQL must return it.
    private static void requireTimeColumn(final ExternalDataSourceDoc doc, final List<SqlColumn> columns) {
        if (SqlRowConsumer.findColumn(columns, doc.getTimeField()) < 0) {
            throw new IllegalArgumentException("The time range cannot be applied, as the SQL does not return "
                                               + "the time column '" + doc.getTimeField()
                                               + "' of External Data Source '" + doc.getName()
                                               + "'. Add it to the SQL's columns, or use :from and :to in "
                                               + "the SQL.");
        }
    }

    private static void requireUrl(final ExternalDataSourceDoc doc) {
        if (doc.getJdbc() == null || NullSafe.isBlankString(doc.getJdbc().getUrl())) {
            throw new IllegalArgumentException("External Data Source '" + doc.getName() + "' has no JDBC URL.");
        }
    }
}
