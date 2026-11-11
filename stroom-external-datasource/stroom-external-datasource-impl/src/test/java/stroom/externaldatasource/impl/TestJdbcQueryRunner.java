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

import stroom.externaldatasource.impl.JdbcConnectionPoolCache.ConnectionFunction;
import stroom.externaldatasource.shared.ExternalDataSourceDoc;
import stroom.externaldatasource.shared.JdbcConnectionSettings;
import stroom.query.api.DateTimeSettings;
import stroom.query.api.datasource.FieldType;
import stroom.query.common.v2.ExpressionPredicateFactory;
import stroom.query.language.functions.FieldIndex;
import stroom.query.language.functions.Val;
import stroom.query.language.functions.ValInteger;

import com.mysql.cj.jdbc.JdbcConnection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

// Running SQL against a real database is tested by TestExternalDataSourceIntegration in stroom-app.
class TestJdbcQueryRunner {

    private static final String SQL = "select id from staff where name = :name";

    private final JdbcConnectionPoolCache poolCache = Mockito.mock(JdbcConnectionPoolCache.class);
    private final Connection connection = Mockito.mock(Connection.class);
    private final PreparedStatement statement = Mockito.mock(PreparedStatement.class);
    private final ResultSetMetaData metaData = Mockito.mock(ResultSetMetaData.class);
    private final List<Val[]> received = new ArrayList<>();

    private JdbcQueryRunner runner;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() throws SQLException {
        Mockito.when(poolCache.useConnection(Mockito.any(), Mockito.any()))
                .thenAnswer(invocation -> ((ConnectionFunction<?>) invocation.getArgument(1)).apply(connection));
        Mockito.when(connection.prepareStatement(Mockito.anyString(), Mockito.anyInt(), Mockito.anyInt()))
                .thenReturn(statement);
        Mockito.when(metaData.getColumnCount()).thenReturn(1);
        Mockito.when(metaData.getColumnLabel(1)).thenReturn("id");
        Mockito.when(metaData.getColumnType(1)).thenReturn(Types.INTEGER);
        runner = new JdbcQueryRunner(poolCache, ExternalDataSourceConfig::new);
    }

    private static ExternalDataSourceDoc doc(final int maxRows) {
        return ExternalDataSourceDoc.builder()
                .uuid("uuid-1")
                .maxRows(maxRows)
                .queryTimeoutSeconds(30)
                .jdbc(JdbcConnectionSettings.builder()
                        .url("jdbc:mysql://db:3306/hr")
                        .fetchSize(500)
                        .build())
                .build();
    }

    // A result set of rows 1 to rowCount in one INTEGER column.
    private ResultSet givenRows(final int rowCount) throws SQLException {
        final ResultSet resultSet = Mockito.mock(ResultSet.class);
        final AtomicInteger row = new AtomicInteger();
        Mockito.when(resultSet.getMetaData()).thenReturn(metaData);
        Mockito.when(resultSet.next()).thenAnswer(invocation -> row.incrementAndGet() <= rowCount);
        Mockito.when(resultSet.getInt(1)).thenAnswer(invocation -> row.get());
        Mockito.when(statement.executeQuery()).thenReturn(resultSet);
        return resultSet;
    }

    private QueryRunSummary run(final int maxRows, final QueryCancellation cancellation)
            throws SQLException {
        return runner.run(
                doc(maxRows),
                SqlStatementParser.parse(SQL),
                List.of("Jones"),
                columns -> SqlRowConsumer.create(
                        columns,
                        fieldIndex(),
                        null,
                        DateTimeSettings.builder().build(),
                        new ExpressionPredicateFactory(),
                        received::add),
                cancellation);
    }

    private static FieldIndex fieldIndex() {
        final FieldIndex fieldIndex = new FieldIndex();
        fieldIndex.create("id");
        return fieldIndex;
    }

    @Test
    void describeAsksTheDriverWithoutRunningTheQuery() throws SQLException {
        Mockito.when(statement.getMetaData()).thenReturn(metaData);

        final List<SqlColumn> columns = runner.describe(doc(100), SqlStatementParser.parse(SQL));

        assertThat(columns).containsExactly(new SqlColumn("id", FieldType.INTEGER, Types.INTEGER));
        Mockito.verify(statement).setNull(1, Types.VARCHAR);
        Mockito.verify(statement, Mockito.never()).executeQuery();
    }

    @Test
    void describeRunsTheQueryForNoRowsWhenTheDriverCannotDescribeIt() throws SQLException {
        Mockito.when(statement.getMetaData()).thenReturn(null);
        givenRows(0);

        final List<SqlColumn> columns = runner.describe(doc(100), SqlStatementParser.parse(SQL + " -- note"));

        assertThat(columns).extracting(SqlColumn::name).containsExactly("id");
        Mockito.verify(connection).prepareStatement(
                "select * from (select id from staff where name = ? -- note\n) q where 1 = 0",
                ResultSet.TYPE_FORWARD_ONLY,
                ResultSet.CONCUR_READ_ONLY);
    }

    @Test
    void mySqlStatementIsPreparedOnTheServerToDescribeIt() throws SQLException {
        // MySQL's driver runs a statement prepared on the client to describe it.
        final JdbcConnection mySqlConnection = Mockito.mock(JdbcConnection.class);
        final PreparedStatement serverStatement = Mockito.mock(PreparedStatement.class);
        Mockito.when(connection.isWrapperFor(JdbcConnection.class)).thenReturn(true);
        Mockito.when(connection.unwrap(JdbcConnection.class)).thenReturn(mySqlConnection);
        Mockito.when(mySqlConnection.serverPrepareStatement("select id from staff where name = ?"))
                .thenReturn(serverStatement);
        Mockito.when(serverStatement.getMetaData()).thenReturn(metaData);

        final List<SqlColumn> columns = runner.describe(doc(100), SqlStatementParser.parse(SQL));

        assertThat(columns).extracting(SqlColumn::name).containsExactly("id");
        Mockito.verify(serverStatement, Mockito.never()).executeQuery();
        Mockito.verify(serverStatement).close();
        Mockito.verify(connection, Mockito.never())
                .prepareStatement(Mockito.anyString(), Mockito.anyInt(), Mockito.anyInt());
    }

    @Test
    void mySqlStatementTheServerWillNotPrepareIsDescribedTheUsualWay() throws SQLException {
        // Such as a statement the server cannot prepare, or when it has no room for more.
        final JdbcConnection mySqlConnection = Mockito.mock(JdbcConnection.class);
        Mockito.when(connection.isWrapperFor(JdbcConnection.class)).thenReturn(true);
        Mockito.when(connection.unwrap(JdbcConnection.class)).thenReturn(mySqlConnection);
        Mockito.when(mySqlConnection.serverPrepareStatement(Mockito.anyString()))
                .thenThrow(new SQLException("Can't create more than max_prepared_stmt_count statements"));
        Mockito.when(statement.getMetaData()).thenReturn(metaData);

        final List<SqlColumn> columns = runner.describe(doc(100), SqlStatementParser.parse(SQL));

        assertThat(columns).extracting(SqlColumn::name).containsExactly("id");
    }

    @Test
    void runSetsTheLimitsAndBindsTheValues() throws SQLException {
        givenRows(2);

        final QueryRunSummary summary = run(100, new QueryCancellation());

        Mockito.verify(statement).setQueryTimeout(30);
        Mockito.verify(statement).setMaxRows(101);
        Mockito.verify(statement).setFetchSize(101);
        Mockito.verify(statement).setString(1, "Jones");
        assertThat(summary.rowsRead()).isEqualTo(2);
        assertThat(summary.limitReached()).isFalse();
        assertThat(received).extracting(row -> row[0]).containsExactly(ValInteger.create(1), ValInteger.create(2));
    }

    @Test
    void runStopsAtTheRowLimitAndSaysSo() throws SQLException {
        givenRows(5);

        final QueryRunSummary summary = run(3, new QueryCancellation());

        assertThat(summary.rowsRead()).isEqualTo(3);
        assertThat(summary.limitReached()).isTrue();
        assertThat(received).hasSize(3);
    }

    @Test
    void resultThatExactlyFillsTheLimitIsNotCutShort() throws SQLException {
        givenRows(3);

        final QueryRunSummary summary = run(3, new QueryCancellation());

        assertThat(summary.limitReached()).isFalse();
    }

    @Test
    void cancelledQueryReadsNoRowsAndStopsTheStatement() throws SQLException {
        givenRows(5);
        final QueryCancellation cancellation = new QueryCancellation();
        cancellation.cancel();

        final QueryRunSummary summary = run(100, cancellation);

        assertThat(summary.rowsRead()).isZero();
        Mockito.verify(statement).cancel();
    }

    @Test
    void finishedStatementIsForgotten() throws SQLException {
        givenRows(1);
        final QueryCancellation cancellation = new QueryCancellation();

        run(100, cancellation);
        cancellation.cancel();

        Mockito.verify(statement, Mockito.never()).cancel();
        Mockito.verify(statement).close();
    }

    @Test
    void statementAsksForOneRowMoreThanTheLimit() {
        assertThat(JdbcQueryRunner.statementMaxRows(new SqlQueryLimits(60, 100))).isEqualTo(101);
    }

    @Test
    void statementHasNoLimitWhenTheLimitCannotBeRaised() {
        assertThat(JdbcQueryRunner.statementMaxRows(new SqlQueryLimits(60, Integer.MAX_VALUE))).isZero();
    }

    @Test
    void fetchSizeIsNoLargerThanTheRowLimit() {
        // MySQL refuses a fetch size larger than the statement's row limit.
        assertThat(JdbcQueryRunner.fetchSize(1_000, 3)).isEqualTo(3);
    }

    @Test
    void fetchSizeIsKeptWithinTheRowLimit() {
        assertThat(JdbcQueryRunner.fetchSize(1_000, 100_001)).isEqualTo(1_000);
    }

    @Test
    void fetchSizeIsKeptWithNoRowLimit() {
        assertThat(JdbcQueryRunner.fetchSize(1_000, 0)).isEqualTo(1_000);
    }

    @Test
    void negativeFetchSizeIsZero() {
        // Zero leaves the fetch size to the driver.
        assertThat(JdbcQueryRunner.fetchSize(-1, 100)).isZero();
    }
}
