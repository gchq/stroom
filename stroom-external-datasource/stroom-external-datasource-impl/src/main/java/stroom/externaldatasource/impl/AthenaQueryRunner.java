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

import stroom.externaldatasource.shared.AthenaConnectionSettings;
import stroom.externaldatasource.shared.ExternalDataSourceDoc;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.ModelStringUtil;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;
import software.amazon.awssdk.services.athena.AthenaClient;
import software.amazon.awssdk.services.athena.model.AthenaError;
import software.amazon.awssdk.services.athena.model.ColumnInfo;
import software.amazon.awssdk.services.athena.model.Datum;
import software.amazon.awssdk.services.athena.model.GetQueryResultsResponse;
import software.amazon.awssdk.services.athena.model.QueryExecution;
import software.amazon.awssdk.services.athena.model.QueryExecutionState;
import software.amazon.awssdk.services.athena.model.QueryExecutionStatistics;
import software.amazon.awssdk.services.athena.model.ResultReuseInformation;
import software.amazon.awssdk.services.athena.model.Row;
import software.amazon.awssdk.services.athena.model.StartQueryExecutionRequest;
import software.amazon.awssdk.services.athena.model.StatementType;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.SQLException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;

/// Runs the SQL of a query on Amazon Athena.
///
/// The query is started, polled until it finishes, and its results read 1,000 rows at a time, the
/// most Athena returns per call. The query timeout and row limit come from the document within the
/// administrator's ceilings, the timeout covering both running and reading. A query that is
/// cancelled, runs out of time or fails in Stroom for any other reason is stopped in Athena, so it
/// is not left running and billing.
///
/// ## Placeholder values
///
/// Athena takes parameter values as SQL literals, which it puts in place of the `?`s. Each value
/// is turned into a literal here: text is quoted with any quote doubled, numbers are written as
/// digits and times as `TIMESTAMP` literals in UTC. A value can therefore only ever be one literal.
/// As text is compared with numbers only after a cast in Athena, SQL comparing a parameter with a
/// number column must cast it, as in `where id = cast(:id as bigint)`.
///
/// ## Read only
///
/// An Athena connection cannot be made read-only, and a statement such as `CREATE TABLE AS`,
/// `INSERT` or `UNLOAD` writes to S3. So only statements that start with `SELECT`, `WITH`,
/// `VALUES`, `SHOW` or `DESCRIBE` are run. The AWS identity, and the accounts of any federated
/// connectors, should still only be able to read: a `SELECT` can pass SQL through to a connector's
/// own database with `system.query`, and Stroom cannot see what that SQL does.
@Singleton
public class AthenaQueryRunner {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(AthenaQueryRunner.class);

    /// The most rows Athena returns for each request.
    static final int PAGE_SIZE = 1_000;
    /// The keywords a statement may start with.
    static final Set<String> READ_ONLY_KEYWORDS = Set.of("select", "with", "values", "show", "describe");
    /// The longest Athena will reuse a previous result for.
    static final int MAX_RESULT_REUSE_MINUTES = 10_080;

    private static final long FIRST_POLL_INTERVAL_MS = 250;
    private static final long MAX_POLL_INTERVAL_MS = 2_000;
    // Athena reports data in decimal units, as it bills.
    private static final long BYTES_PER_MB = 1_000_000L;
    private static final DateTimeFormatter TIMESTAMP_LITERAL_FORMAT = DateTimeFormatter
            .ofPattern("uuuu-MM-dd HH:mm:ss.SSS", Locale.ROOT)
            .withZone(ZoneOffset.UTC);

    private final AthenaConnectionResolver connectionResolver;
    private final AthenaClientFactory clientFactory;
    private final Provider<ExternalDataSourceConfig> configProvider;
    private final Sleeper sleeper;

    /// Creates the runner. Guice supplies every argument.
    @Inject
    AthenaQueryRunner(final AthenaConnectionResolver connectionResolver,
                      final AthenaClientFactory clientFactory,
                      final Provider<ExternalDataSourceConfig> configProvider) {
        this(connectionResolver, clientFactory, configProvider, Thread::sleep);
    }

    /// Creates the runner with a way to wait between polls, so tests need not wait.
    ///
    /// @param connectionResolver Resolves the document's settings and credential.
    /// @param clientFactory      Makes Athena clients.
    /// @param configProvider     Gives the administrator's ceilings.
    /// @param sleeper            Waits between polls.
    AthenaQueryRunner(final AthenaConnectionResolver connectionResolver,
                      final AthenaClientFactory clientFactory,
                      final Provider<ExternalDataSourceConfig> configProvider,
                      final Sleeper sleeper) {
        this.connectionResolver = connectionResolver;
        this.clientFactory = clientFactory;
        this.configProvider = configProvider;
        this.sleeper = sleeper;
    }

    /// Checks that a statement only reads, as far as its first keyword shows.
    ///
    /// @param parsedSql The query's SQL.
    /// @throws IllegalArgumentException If the statement does not start with a reading keyword.
    public static void requireReadOnly(final ParsedSql parsedSql) {
        final String keyword = SqlStatementParser.firstKeyword(parsedSql.jdbcSql());
        if (!READ_ONLY_KEYWORDS.contains(keyword)) {
            throw new IllegalArgumentException("Amazon Athena SQL must start with SELECT, WITH, VALUES, SHOW or "
                                               + "DESCRIBE, as statements that write are not allowed.");
        }
    }

    /// Runs the SQL and passes its rows to a row consumer.
    ///
    /// @param doc                The External Data Source.
    /// @param parsedSql          The query's SQL.
    /// @param values             The placeholder values from [SqlParameterBinder#resolveValues].
    /// @param rowConsumerFactory Makes the row consumer once the columns are known.
    /// @param cancellation       Stops the Athena query when the search is cancelled.
    /// @param progress           Takes messages about the query's progress.
    /// @return What the run read, with the data scanned as its information. A run that was
    ///         cancelled reads nothing.
    /// @throws IllegalArgumentException If the SQL writes, or the settings break the controls.
    /// @throws IllegalStateException    If the query fails or runs out of time in Athena.
    public QueryRunSummary run(final ExternalDataSourceDoc doc,
                               final ParsedSql parsedSql,
                               final List<Object> values,
                               final Function<List<SqlColumn>, SqlRowConsumer> rowConsumerFactory,
                               final QueryCancellation cancellation,
                               final Consumer<String> progress) {
        requireReadOnly(parsedSql);
        final SqlQueryLimits limits = SqlQueryLimits.of(doc, configProvider.get());
        final ResolvedAthenaConnection connection = connectionResolver.resolve(doc.getAthena());

        final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(limits.timeoutSeconds());
        final int maxDataScannedMb = maxDataScannedMb(doc.getAthena());

        try (final AthenaClient client = clientFactory.create(connection)) {
            final String id = client.startQueryExecution(startRequest(connection, parsedSql, values))
                    .queryExecutionId();
            LOGGER.debug("Started Amazon Athena query {}", id);
            cancellation.register(() -> client.stopQueryExecution(builder -> builder.queryExecutionId(id)));
            try {
                final QueryExecution execution = awaitCompletion(
                        client, id, limits, deadline, maxDataScannedMb, cancellation, progress);
                if (execution == null) {
                    return new QueryRunSummary(List.of(), List.of(), 0, 0, false, limits.maxRows(), null);
                }
                return readResults(client, execution, limits, deadline, rowConsumerFactory, cancellation);
            } catch (final RuntimeException e) {
                // Whatever went wrong, do not leave the query running and billing in Athena.
                stopQuietly(client, id);
                throw e;
            } finally {
                cancellation.unregister();
            }
        }
    }

    /// Makes the request that starts a query.
    ///
    /// @param connection The resolved connection.
    /// @param parsedSql  The query's SQL.
    /// @param values     The placeholder values.
    /// @return The request.
    static StartQueryExecutionRequest startRequest(final ResolvedAthenaConnection connection,
                                                   final ParsedSql parsedSql,
                                                   final List<Object> values) {
        final StartQueryExecutionRequest.Builder builder = StartQueryExecutionRequest.builder()
                .queryString(parsedSql.jdbcSql())
                .workGroup(connection.workGroup())
                .queryExecutionContext(context -> context
                        .catalog(connection.catalog())
                        .database(connection.database()));
        if (connection.outputLocation() != null) {
            builder.resultConfiguration(result -> result.outputLocation(connection.outputLocation()));
        }
        if (connection.resultReuseMinutes() > 0) {
            builder.resultReuseConfiguration(reuse -> reuse.resultReuseByAgeConfiguration(age -> age
                    .enabled(true)
                    .maxAgeInMinutes(Math.min(connection.resultReuseMinutes(), MAX_RESULT_REUSE_MINUTES))));
        }
        if (!values.isEmpty()) {
            builder.executionParameters(values.stream()
                    .map(AthenaQueryRunner::toLiteral)
                    .toList());
        }
        return builder.build();
    }

    /// Writes a placeholder value as an Athena SQL literal.
    ///
    /// @param value An [Instant], a [Long] or a [String].
    /// @return The literal.
    /// @throws IllegalArgumentException If a time is outside the years 1 to 9999, which an Athena
    ///                                  timestamp cannot hold.
    static String toLiteral(final Object value) {
        return switch (value) {
            case final Instant instant -> timestampLiteral(instant);
            case final Long number -> number.toString();
            case final String text -> "'" + text.replace("'", "''") + "'";
            default -> throw new IllegalStateException("Unexpected parameter value type " + value.getClass());
        };
    }

    private static String timestampLiteral(final Instant instant) {
        final int year = instant.atZone(ZoneOffset.UTC).getYear();
        if (year < 1 || year > 9999) {
            throw new IllegalArgumentException("The time " + instant + " is outside the years 1 to 9999, which "
                                               + "an Amazon Athena timestamp can hold.");
        }
        return "TIMESTAMP '" + TIMESTAMP_LITERAL_FORMAT.format(instant) + "'";
    }

    /// The most data a query of the settings may scan before Stroom stops it.
    ///
    /// @param settings Amazon Athena settings, or `null`.
    /// @return The limit in MB, 0 for none.
    static int maxDataScannedMb(final AthenaConnectionSettings settings) {
        final Integer limit = NullSafe.get(settings, AthenaConnectionSettings::getMaxDataScannedMb);
        return limit == null
                ? 0
                : Math.max(0, limit);
    }

    // Polls until the query finishes. Returns null if the search was cancelled. Any exception
    // leads the caller to stop the query.
    //
    // A query still running that has scanned more than the document's limit is stopped. Athena reports
    // the data scanned as it goes, so a query may pass the limit by what it scans between two polls;
    // the workgroup's own per-query limit is the one Athena enforces exactly.
    private QueryExecution awaitCompletion(final AthenaClient client,
                                           final String id,
                                           final SqlQueryLimits limits,
                                           final long deadline,
                                           final int maxDataScannedMb,
                                           final QueryCancellation cancellation,
                                           final Consumer<String> progress) {
        long interval = FIRST_POLL_INTERVAL_MS;
        while (!cancellation.isCancelled()) {
            final QueryExecution execution = client.getQueryExecution(builder -> builder.queryExecutionId(id))
                    .queryExecution();
            final QueryExecutionState state = execution.status().state();
            progress.accept(LogUtil.message("Amazon Athena query {} {}, {} scanned",
                    id,
                    execution.status().stateAsString().toLowerCase(Locale.ROOT),
                    dataScanned(execution.statistics())));

            if (QueryExecutionState.SUCCEEDED.equals(state)) {
                return execution;
            } else if (QueryExecutionState.FAILED.equals(state)) {
                throw new IllegalStateException("Amazon Athena query " + id + " failed: " + failureReason(execution));
            } else if (QueryExecutionState.CANCELLED.equals(state)) {
                if (cancellation.isCancelled()) {
                    return null;
                }
                throw new IllegalStateException("Amazon Athena query " + id + " was cancelled outside Stroom.");
            } else if (System.nanoTime() > deadline) {
                throw new IllegalStateException("Amazon Athena query " + id + " did not finish within "
                                                + limits.timeoutSeconds() + " seconds, so it was stopped.");
            } else if (isOverScanLimit(execution.statistics(), maxDataScannedMb)) {
                throw new IllegalStateException(LogUtil.message(
                        "Amazon Athena query {} was stopped after scanning {} MB, more than the limit of {} MB "
                        + "set on the External Data Source. Narrow the query, for example with a shorter time "
                        + "range or a filter on a partition column.",
                        id,
                        megabytes(NullSafe.get(execution.statistics(), QueryExecutionStatistics::dataScannedInBytes)),
                        maxDataScannedMb));
            }

            try {
                sleeper.sleep(interval);
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while waiting for Amazon Athena query " + id, e);
            }
            interval = Math.min(interval * 2, MAX_POLL_INTERVAL_MS);
        }
        return null;
    }

    private static QueryRunSummary readResults(final AthenaClient client,
                                               final QueryExecution execution,
                                               final SqlQueryLimits limits,
                                               final long deadline,
                                               final Function<List<SqlColumn>, SqlRowConsumer> rowConsumerFactory,
                                               final QueryCancellation cancellation) {
        final String id = execution.queryExecutionId();
        // The first row of a SELECT's first page holds the column names.
        boolean skipHeader = StatementType.DML.equals(execution.statementType());
        List<SqlColumn> columns = null;
        SqlRowConsumer rowConsumer = null;
        long rowsRead = 0;
        long rowsPassed = 0;
        boolean limitReached = false;
        String nextToken = null;

        do {
            final String token = nextToken;
            final GetQueryResultsResponse page = client.getQueryResults(builder -> builder
                    .queryExecutionId(id)
                    .maxResults(PAGE_SIZE)
                    .nextToken(token));
            if (columns == null) {
                columns = toColumns(page.resultSet().resultSetMetadata().columnInfo());
                rowConsumer = rowConsumerFactory.apply(columns);
            }
            final List<Row> rows = page.resultSet().rows();
            for (int i = skipHeader ? 1 : 0; i < rows.size() && !limitReached; i++) {
                if (rowsRead >= limits.maxRows()) {
                    limitReached = true;
                } else {
                    rowsRead++;
                    if (accept(rowConsumer, rows.get(i).data(), columns)) {
                        rowsPassed++;
                    }
                }
            }
            skipHeader = false;
            nextToken = page.nextToken();
            if (nextToken != null && !limitReached && System.nanoTime() > deadline) {
                throw new IllegalStateException("Amazon Athena query " + id + " did not finish within "
                                                + limits.timeoutSeconds() + " seconds, as reading its results "
                                                + "took too long.");
            }
        } while (nextToken != null && !limitReached && !cancellation.isCancelled());

        return new QueryRunSummary(
                columns,
                rowConsumer.getUnmatchedFields(),
                rowsRead,
                rowsPassed,
                limitReached,
                limits.maxRows(),
                summaryInfo(execution));
    }

    private static boolean accept(final SqlRowConsumer rowConsumer,
                                  final List<Datum> data,
                                  final List<SqlColumn> columns) {
        try {
            return rowConsumer.accept(columnIndex -> AthenaTypeMapper.readValue(
                    columnIndex < data.size()
                            ? data.get(columnIndex).varCharValue()
                            : null,
                    columns.get(columnIndex)));
        } catch (final SQLException e) {
            // Athena values are read from text and never throw this.
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

    /// Turns Athena's column descriptions into columns.
    ///
    /// @param columnInfos The column descriptions from the first page of results.
    /// @return The columns, in order.
    static List<SqlColumn> toColumns(final List<ColumnInfo> columnInfos) {
        final List<SqlColumn> columns = new ArrayList<>(columnInfos.size());
        for (final ColumnInfo columnInfo : columnInfos) {
            final int sqlType = AthenaTypeMapper.toSqlType(columnInfo.type());
            columns.add(new SqlColumn(
                    columnInfo.name(),
                    SqlTypeMapper.toFieldType(
                            sqlType,
                            Objects.requireNonNullElse(columnInfo.precision(), 0),
                            Objects.requireNonNullElse(columnInfo.scale(), 0)),
                    sqlType));
        }
        return List.copyOf(columns);
    }

    private static String summaryInfo(final QueryExecution execution) {
        final QueryExecutionStatistics statistics = execution.statistics();
        final boolean reused = NullSafe.isTrue(NullSafe.get(
                statistics,
                QueryExecutionStatistics::resultReuseInformation,
                ResultReuseInformation::reusedPreviousResult));
        return LogUtil.message("Amazon Athena query {} scanned {}{}.",
                execution.queryExecutionId(),
                dataScanned(statistics),
                reused
                        ? ", reusing an earlier result"
                        : "");
    }

    /// Writes a number of bytes as decimal megabytes, as Athena bills, to one decimal place.
    ///
    /// @param bytes A number of bytes, or `null` for none.
    /// @return The megabytes, e.g. `1.5`.
    static String megabytes(final Long bytes) {
        return BigDecimal.valueOf(bytes == null
                        ? 0L
                        : bytes)
                .divide(BigDecimal.valueOf(BYTES_PER_MB), 1, RoundingMode.HALF_UP)
                .stripTrailingZeros()
                .toPlainString();
    }

    private static boolean isOverScanLimit(final QueryExecutionStatistics statistics, final int maxDataScannedMb) {
        final Long bytes = NullSafe.get(statistics, QueryExecutionStatistics::dataScannedInBytes);
        return maxDataScannedMb > 0 && bytes != null && bytes > maxDataScannedMb * BYTES_PER_MB;
    }

    private static String dataScanned(final QueryExecutionStatistics statistics) {
        final Long bytes = NullSafe.get(statistics, QueryExecutionStatistics::dataScannedInBytes);
        return ModelStringUtil.formatMetricByteSizeString(bytes == null
                ? 0L
                : bytes);
    }

    private static String failureReason(final QueryExecution execution) {
        final String errorMessage = NullSafe.get(execution.status().athenaError(), AthenaError::errorMessage);
        return NullSafe.isNonBlankString(errorMessage)
                ? errorMessage
                : execution.status().stateChangeReason();
    }

    private static void stopQuietly(final AthenaClient client, final String id) {
        try {
            client.stopQueryExecution(builder -> builder.queryExecutionId(id));
        } catch (final RuntimeException e) {
            LOGGER.debug(() -> LogUtil.message("Unable to stop Amazon Athena query {}: {}", id, e.getMessage()), e);
        }
    }


    // --------------------------------------------------------------------------------


    /// Waits between polls.
    @FunctionalInterface
    interface Sleeper {

        /// @param millis How long to wait.
        /// @throws InterruptedException If the thread is interrupted while waiting.
        void sleep(long millis) throws InterruptedException;
    }
}
