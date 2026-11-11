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

import stroom.credentials.api.StoredSecrets;
import stroom.externaldatasource.shared.AthenaConnectionSettings;
import stroom.externaldatasource.shared.ExternalDataSourceDoc;
import stroom.externaldatasource.shared.ExternalDataSourceType;
import stroom.query.api.DateTimeSettings;
import stroom.query.common.v2.ExpressionPredicateFactory;
import stroom.query.language.functions.FieldIndex;
import stroom.query.language.functions.Val;
import stroom.query.language.functions.ValLong;
import stroom.query.language.functions.ValNull;
import stroom.query.language.functions.ValString;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import software.amazon.awssdk.services.athena.AthenaClient;
import software.amazon.awssdk.services.athena.model.ColumnInfo;
import software.amazon.awssdk.services.athena.model.Datum;
import software.amazon.awssdk.services.athena.model.GetQueryExecutionRequest;
import software.amazon.awssdk.services.athena.model.GetQueryExecutionResponse;
import software.amazon.awssdk.services.athena.model.GetQueryResultsRequest;
import software.amazon.awssdk.services.athena.model.GetQueryResultsResponse;
import software.amazon.awssdk.services.athena.model.QueryExecutionState;
import software.amazon.awssdk.services.athena.model.Row;
import software.amazon.awssdk.services.athena.model.StartQueryExecutionRequest;
import software.amazon.awssdk.services.athena.model.StartQueryExecutionResponse;
import software.amazon.awssdk.services.athena.model.StatementType;
import software.amazon.awssdk.services.athena.model.StopQueryExecutionRequest;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

// Athena itself cannot be run in a test, so the client is a mock that plays its part.
class TestAthenaQueryRunner {

    private static final String QUERY_ID = "q-123";
    private static final List<ColumnInfo> COLUMNS = List.of(
            ColumnInfo.builder().name("id").type("bigint").build(),
            ColumnInfo.builder().name("name").type("varchar").build());

    private final AthenaClient client = Mockito.mock(AthenaClient.class);
    private final List<Long> sleeps = new ArrayList<>();
    private final List<String> progress = new ArrayList<>();
    private final List<Val[]> received = new ArrayList<>();
    private final List<String> requestedTokens = new ArrayList<>();
    // The data each poll reports scanned.
    private long scannedBytes = 1_500_000L;
    private Runnable onSleep = () -> {
    };
    private boolean interruptSleep;
    private long pageDelayMillis;
    private AthenaQueryRunner runner;

    @BeforeEach
    void setUp() {
        final ExternalDataSourceConfig config = new ExternalDataSourceConfig(
                null, null, null, null, null, null, null, null,
                List.of("^eu-west-2/primary$"), null, true, null, null);
        final AthenaConnectionResolver resolver = new AthenaConnectionResolver(
                () -> config,
                () -> Mockito.mock(StoredSecrets.class));
        runner = new AthenaQueryRunner(resolver, connection -> client, () -> config, millis -> {
            sleeps.add(millis);
            if (interruptSleep) {
                throw new InterruptedException();
            }
            onSleep.run();
        });
        Mockito.when(client.startQueryExecution(Mockito.any(StartQueryExecutionRequest.class)))
                .thenReturn(StartQueryExecutionResponse.builder().queryExecutionId(QUERY_ID).build());
    }

    private static ExternalDataSourceDoc doc(final int maxRows, final int timeoutSeconds) {
        return ExternalDataSourceDoc.builder()
                .uuid("uuid-1")
                .sourceType(ExternalDataSourceType.ATHENA)
                .maxRows(maxRows)
                .queryTimeoutSeconds(timeoutSeconds)
                .athena(AthenaConnectionSettings.builder()
                        .region("eu-west-2")
                        .build())
                .build();
    }

    // Athena reports these states in turn, then the last one for ever.
    @SuppressWarnings("unchecked")
    private void givenStates(final StatementType statementType, final QueryExecutionState... states) {
        final int[] call = new int[1];
        Mockito.when(client.getQueryExecution(Mockito.any(Consumer.class))).thenAnswer(invocation -> {
            final QueryExecutionState state = states[Math.min(call[0]++, states.length - 1)];
            return GetQueryExecutionResponse.builder()
                    .queryExecution(execution -> execution
                            .queryExecutionId(QUERY_ID)
                            .statementType(statementType)
                            .status(status -> status
                                    .state(state)
                                    .athenaError(error -> error.errorMessage("TABLE_NOT_FOUND: events")))
                            .statistics(statistics -> statistics.dataScannedInBytes(scannedBytes)))
                    .build();
        });
        // The consumer overloads are default methods, which the mock does not run, so check they
        // are given the right query.
        Mockito.when(client.getQueryExecution(Mockito.any(GetQueryExecutionRequest.class))).thenThrow(
                new AssertionError("expected the consumer overload"));
    }

    // Each page is a list of rows, each row its values.
    @SuppressWarnings("unchecked")
    private void givenPages(final List<List<List<String>>> pages) {
        Mockito.when(client.getQueryResults(Mockito.any(Consumer.class))).thenAnswer(invocation -> {
            final GetQueryResultsRequest.Builder builder = GetQueryResultsRequest.builder();
            ((Consumer<GetQueryResultsRequest.Builder>) invocation.getArgument(0)).accept(builder);
            final GetQueryResultsRequest request = builder.build();
            assertThat(request.queryExecutionId()).isEqualTo(QUERY_ID);
            assertThat(request.maxResults()).isEqualTo(AthenaQueryRunner.PAGE_SIZE);
            requestedTokens.add(request.nextToken());
            if (pageDelayMillis > 0) {
                Thread.sleep(pageDelayMillis);
            }
            final int pageIndex = request.nextToken() == null
                    ? 0
                    : Integer.parseInt(request.nextToken());
            final List<Row> rows = pages.get(pageIndex).stream()
                    .map(values -> Row.builder()
                            .data(values.stream()
                                    .map(value -> Datum.builder().varCharValue(value).build())
                                    .toList())
                            .build())
                    .toList();
            return GetQueryResultsResponse.builder()
                    .resultSet(resultSet -> resultSet
                            .rows(rows)
                            .resultSetMetadata(metadata -> metadata.columnInfo(COLUMNS)))
                    .nextToken(pageIndex + 1 < pages.size()
                            ? String.valueOf(pageIndex + 1)
                            : null)
                    .build();
        });
    }

    private QueryRunSummary run(final ExternalDataSourceDoc doc,
                                final String sql,
                                final QueryCancellation cancellation) {
        return runner.run(
                doc,
                SqlStatementParser.parse(sql),
                List.of(),
                columns -> SqlRowConsumer.create(
                        columns,
                        fieldIndex(),
                        null,
                        DateTimeSettings.builder().build(),
                        new ExpressionPredicateFactory(),
                        received::add),
                cancellation,
                progress::add);
    }

    private static FieldIndex fieldIndex() {
        final FieldIndex fieldIndex = new FieldIndex();
        fieldIndex.create("id");
        fieldIndex.create("name");
        return fieldIndex;
    }

    private static List<List<String>> rows(final String... idsAndNames) {
        final List<List<String>> rows = new ArrayList<>();
        for (int i = 0; i < idsAndNames.length; i += 2) {
            rows.add(Arrays.asList(idsAndNames[i], idsAndNames[i + 1]));
        }
        return rows;
    }

    @Test
    void queryIsPolledUntilItFinishesThenItsRowsAreRead() {
        givenStates(StatementType.DML,
                QueryExecutionState.QUEUED, QueryExecutionState.RUNNING, QueryExecutionState.SUCCEEDED);
        // The first row of a SELECT's first page holds the column names.
        givenPages(List.of(
                rows("id", "name", "1", "Smith", "2", "Jones"),
                rows("3", null)));

        final QueryRunSummary summary = run(doc(100, 60), "select id, name from staff", new QueryCancellation());

        assertThat(sleeps).containsExactly(250L, 500L);
        assertThat(requestedTokens).containsExactly(null, "1");
        assertThat(summary.rowsRead()).isEqualTo(3);
        assertThat(summary.limitReached()).isFalse();
        assertThat(received)
                .extracting(row -> row[0], row -> row[1])
                .containsExactly(
                        tuple(ValLong.create(1), ValString.create("Smith")),
                        tuple(ValLong.create(2), ValString.create("Jones")),
                        tuple(ValLong.create(3),
                                ValNull.INSTANCE));
        assertThat(summary.info()).contains(QUERY_ID).contains("scanned");
        assertThat(progress).anySatisfy(message -> assertThat(message).contains("queued"));
        Mockito.verify(client).close();
    }

    @Test
    void statementThatIsNotASelectHasNoHeaderRow() {
        givenStates(StatementType.UTILITY, QueryExecutionState.SUCCEEDED);
        givenPages(List.of(rows("1", "staff")));

        final QueryRunSummary summary = run(doc(100, 60), "show tables", new QueryCancellation());

        assertThat(summary.rowsRead()).isEqualTo(1);
    }

    @Test
    void readingStopsAtTheRowLimitAndSaysSo() {
        givenStates(StatementType.DML, QueryExecutionState.SUCCEEDED);
        givenPages(List.of(
                rows("id", "name", "1", "a", "2", "b"),
                rows("3", "c", "4", "d"),
                rows("5", "e")));

        final QueryRunSummary summary = run(doc(3, 60), "select id, name from staff", new QueryCancellation());

        assertThat(summary.rowsRead()).isEqualTo(3);
        assertThat(summary.limitReached()).isTrue();
        assertThat(requestedTokens)
                .as("the third page is never asked for")
                .containsExactly(null, "1");
    }

    @Test
    void failedQueryReportsAthenasError() {
        givenStates(StatementType.DML, QueryExecutionState.RUNNING, QueryExecutionState.FAILED);

        assertThatThrownBy(() -> run(doc(100, 60), "select * from events", new QueryCancellation()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(QUERY_ID)
                .hasMessageContaining("TABLE_NOT_FOUND: events");
        Mockito.verify(client).close();
    }

    @Test
    @SuppressWarnings("unchecked")
    void queryThatRunsOutOfTimeIsStoppedInAthena() {
        givenStates(StatementType.DML, QueryExecutionState.RUNNING);
        onSleep = () -> {
            try {
                Thread.sleep(sleeps.getLast());
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        };

        assertThatThrownBy(() -> run(doc(100, 1), "select * from events", new QueryCancellation()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("did not finish within 1 seconds");
        Mockito.verify(client).stopQueryExecution(Mockito.any(Consumer.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void cancellingTheSearchStopsTheQueryInAthena() {
        givenStates(StatementType.DML, QueryExecutionState.RUNNING);
        final QueryCancellation cancellation = new QueryCancellation();
        onSleep = cancellation::cancel;

        final QueryRunSummary summary = run(doc(100, 60), "select * from events", cancellation);

        assertThat(summary.rowsRead()).isZero();
        final ArgumentCaptorHolder captor = new ArgumentCaptorHolder();
        Mockito.verify(client).stopQueryExecution(Mockito.argThat((Consumer<StopQueryExecutionRequest.Builder> c) -> {
            final StopQueryExecutionRequest.Builder builder = StopQueryExecutionRequest.builder();
            c.accept(builder);
            captor.id = builder.build().queryExecutionId();
            return true;
        }));
        assertThat(captor.id).isEqualTo(QUERY_ID);
        Mockito.verify(client, Mockito.never()).getQueryResults(Mockito.any(Consumer.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void failedPollStopsTheQueryInAthena() {
        // Otherwise the query would go on running, and billing, after the search has failed.
        Mockito.when(client.getQueryExecution(Mockito.any(Consumer.class)))
                .thenThrow(new IllegalStateException("Rate exceeded"));

        assertThatThrownBy(() -> run(doc(100, 60), "select * from events", new QueryCancellation()))
                .hasMessage("Rate exceeded");
        Mockito.verify(client).stopQueryExecution(Mockito.any(Consumer.class));
    }

    @Test
    void queryCancelledOutsideStroomIsReported() {
        givenStates(StatementType.DML, QueryExecutionState.RUNNING, QueryExecutionState.CANCELLED);

        assertThatThrownBy(() -> run(doc(100, 60), "select * from events", new QueryCancellation()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("was cancelled outside Stroom");
    }

    @Test
    @SuppressWarnings("unchecked")
    void interruptStopsTheQueryAndKeepsTheInterrupt() {
        givenStates(StatementType.DML, QueryExecutionState.RUNNING);
        interruptSleep = true;

        try {
            assertThatThrownBy(() -> run(doc(100, 60), "select * from events", new QueryCancellation()))
                    .hasMessageContaining("Interrupted");
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            // Clear the interrupt so it does not affect other tests.
            assertThat(Thread.interrupted()).isTrue();
        }
        Mockito.verify(client).stopQueryExecution(Mockito.any(Consumer.class));
    }

    @Test
    void readingResultsCountsTowardsTheTimeout() {
        givenStates(StatementType.DML, QueryExecutionState.SUCCEEDED);
        givenPages(List.of(
                rows("id", "name", "1", "a"),
                rows("2", "b")));
        pageDelayMillis = 1_100;

        assertThatThrownBy(() -> run(doc(100, 1), "select id, name from staff", new QueryCancellation()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("reading its results took too long");
        assertThat(requestedTokens)
                .as("the second page is not asked for once the time is up")
                .containsExactly((String) null);
    }

    @Test
    void timeOutsideAthenasYearsIsRefused() {
        assertThatThrownBy(() -> AthenaQueryRunner.toLiteral(Instant.parse("-0001-06-01T00:00:00Z")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("years 1 to 9999");
        assertThatThrownBy(() -> AthenaQueryRunner.toLiteral(Instant.parse("+10000-01-01T00:00:00Z")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(AthenaQueryRunner.toLiteral(Instant.parse("0001-01-01T00:00:00Z")))
                .isEqualTo("TIMESTAMP '0001-01-01 00:00:00.000'");
    }

    @Test
    void unexpectedValueTypeIsRefused() {
        assertThatThrownBy(() -> AthenaQueryRunner.toLiteral(1.5))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void statementThatWritesIsRefusedBeforeAthenaIsCalled() {
        assertThatThrownBy(() -> run(doc(100, 60), "insert into copy select * from events", new QueryCancellation()))
                .hasMessageContaining("must start with SELECT");
        assertThatThrownBy(() -> run(doc(100, 60),
                "unload (select * from events) to 's3://elsewhere/' with (format = 'json')",
                new QueryCancellation()))
                .hasMessageContaining("must start with SELECT");
        Mockito.verifyNoInteractions(client);
    }

    @Test
    void readingStatementsMayStartWithACommentOrBracket() {
        AthenaQueryRunner.requireReadOnly(SqlStatementParser.parse("-- Daily totals\n(SELECT 1)"));
        AthenaQueryRunner.requireReadOnly(SqlStatementParser.parse("/* x */ WITH t AS (SELECT 1) SELECT * FROM t"));
        AthenaQueryRunner.requireReadOnly(SqlStatementParser.parse("describe events"));
    }

    @Test
    void startRequestCarriesTheSettingsAndLiteralParameters() {
        final ResolvedAthenaConnection connection = new ResolvedAthenaConnection(
                "eu-west-2", "stroom", "AwsDataCatalog", "events", "s3://results/stroom/", 30, null);
        final ParsedSql parsedSql = SqlStatementParser.parse(
                "select * from logs where t >= :from and user = :user and n = :from{epochMillis}");

        final StartQueryExecutionRequest request = AthenaQueryRunner.startRequest(
                connection,
                parsedSql,
                List.of(Instant.parse("2026-11-01T00:00:00Z"), "O'Brien", 42L));

        assertThat(request.queryString()).isEqualTo("select * from logs where t >= ? and user = ? and n = ?");
        assertThat(request.workGroup()).isEqualTo("stroom");
        assertThat(request.queryExecutionContext().catalog()).isEqualTo("AwsDataCatalog");
        assertThat(request.queryExecutionContext().database()).isEqualTo("events");
        assertThat(request.resultConfiguration().outputLocation()).isEqualTo("s3://results/stroom/");
        assertThat(request.resultReuseConfiguration().resultReuseByAgeConfiguration().maxAgeInMinutes())
                .isEqualTo(30);
        assertThat(request.executionParameters()).containsExactly(
                "TIMESTAMP '2026-11-01 00:00:00.000'",
                "'O''Brien'",
                "42");
    }

    @Test
    void requestWithoutOptionsLeavesThemOut() {
        final ResolvedAthenaConnection connection = new ResolvedAthenaConnection(
                "eu-west-2", "primary", "AwsDataCatalog", "default", null, 0, null);

        final StartQueryExecutionRequest request = AthenaQueryRunner.startRequest(
                connection, SqlStatementParser.parse("select 1"), List.of());

        assertThat(request.hasExecutionParameters()).isFalse();
        assertThat(request.resultConfiguration()).isNull();
        assertThat(request.resultReuseConfiguration()).isNull();
    }

    @Test
    void resultReuseIsCappedAtAthenasMaximum() {
        final ResolvedAthenaConnection connection = new ResolvedAthenaConnection(
                "eu-west-2", "primary", "AwsDataCatalog", "default", null, 100_000, null);

        final StartQueryExecutionRequest request = AthenaQueryRunner.startRequest(
                connection, SqlStatementParser.parse("select 1"), List.of());

        assertThat(request.resultReuseConfiguration().resultReuseByAgeConfiguration().maxAgeInMinutes())
                .isEqualTo(AthenaQueryRunner.MAX_RESULT_REUSE_MINUTES);
    }

    @Test
    void textParameterCanOnlyEverBeOneLiteral() {
        assertThat(AthenaQueryRunner.toLiteral("x' or '1'='1")).isEqualTo("'x'' or ''1''=''1'");
    }


    private static ExternalDataSourceDoc docWithScanLimit(final int maxDataScannedMb) {
        final ExternalDataSourceDoc doc = doc(100, 60);
        return doc.copy()
                .athena(doc.getAthena().copy()
                        .maxDataScannedMb(maxDataScannedMb)
                        .build())
                .build();
    }

    @Test
    @SuppressWarnings("unchecked")
    void runningQueryOverTheScanLimitIsStoppedInAthena() {
        // Each poll reports 1.5 MB scanned.
        givenStates(StatementType.DML, QueryExecutionState.RUNNING);

        assertThatThrownBy(() -> run(docWithScanLimit(1), "select * from events", new QueryCancellation()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("was stopped after scanning 1.5 MB")
                .hasMessageContaining("more than the limit of 1 MB");
        final ArgumentCaptorHolder captor = new ArgumentCaptorHolder();
        Mockito.verify(client).stopQueryExecution(Mockito.argThat((Consumer<StopQueryExecutionRequest.Builder> c) -> {
            final StopQueryExecutionRequest.Builder builder = StopQueryExecutionRequest.builder();
            c.accept(builder);
            captor.id = builder.build().queryExecutionId();
            return true;
        }));
        assertThat(captor.id).isEqualTo(QUERY_ID);
        Mockito.verify(client, Mockito.never()).getQueryResults(Mockito.any(Consumer.class));
    }

    @Test
    void queryAtExactlyTheScanLimitRuns() {
        scannedBytes = 1_000_000L;
        givenStates(StatementType.DML, QueryExecutionState.RUNNING, QueryExecutionState.SUCCEEDED);
        givenPages(List.of(rows("id", "name", "1", "Smith")));

        final QueryRunSummary summary = run(docWithScanLimit(1), "select id, name from staff",
                new QueryCancellation());

        assertThat(summary.rowsRead()).isEqualTo(1);
    }

    @Test
    void queryWithinTheScanLimitRuns() {
        givenStates(StatementType.DML, QueryExecutionState.RUNNING, QueryExecutionState.SUCCEEDED);
        givenPages(List.of(rows("id", "name", "1", "Smith")));

        final QueryRunSummary summary = run(docWithScanLimit(2), "select id, name from staff",
                new QueryCancellation());

        assertThat(summary.rowsRead()).isEqualTo(1);
    }

    @Test
    void finishedQueryOverTheScanLimitIsStillRead() {
        // Its scan is already done and billed, so its rows are not thrown away.
        givenStates(StatementType.DML, QueryExecutionState.SUCCEEDED);
        givenPages(List.of(rows("id", "name", "1", "Smith")));

        final QueryRunSummary summary = run(docWithScanLimit(1), "select id, name from staff",
                new QueryCancellation());

        assertThat(summary.rowsRead()).isEqualTo(1);
        assertThat(summary.info()).contains("scanned 1.5M");
    }

    @Test
    void scanLimitIsNoneUnlessSet() {
        assertThat(AthenaQueryRunner.maxDataScannedMb(null)).isZero();
        assertThat(AthenaQueryRunner.maxDataScannedMb(AthenaConnectionSettings.builder().build())).isZero();
        assertThat(AthenaQueryRunner.maxDataScannedMb(AthenaConnectionSettings.builder()
                .maxDataScannedMb(-5)
                .build())).isZero();
    }

    @Test
    void megabytesAreWrittenToOneDecimalPlace() {
        assertThat(AthenaQueryRunner.megabytes(1_500_000L)).isEqualTo("1.5");
        assertThat(AthenaQueryRunner.megabytes(2_000_000_000L)).isEqualTo("2000");
        assertThat(AthenaQueryRunner.megabytes(49_999L)).isEqualTo("0");
    }


    // --------------------------------------------------------------------------------


    private static final class ArgumentCaptorHolder {

        private String id;
    }
}
