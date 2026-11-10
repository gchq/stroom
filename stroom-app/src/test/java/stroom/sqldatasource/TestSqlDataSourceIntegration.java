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

package stroom.sqldatasource;

import stroom.credentials.impl.CredentialsService;
import stroom.credentials.shared.AwsKeySecret;
import stroom.docref.DocRef;
import stroom.query.api.DateTimeSettings;
import stroom.query.api.DestroyReason;
import stroom.query.api.Param;
import stroom.query.api.Query;
import stroom.query.api.Result;
import stroom.query.api.Row;
import stroom.query.api.SearchRequest;
import stroom.query.api.SearchResponse;
import stroom.query.api.TableResult;
import stroom.query.api.TimeRange;
import stroom.query.api.datasource.FieldType;
import stroom.query.api.datasource.FindFieldCriteria;
import stroom.query.api.datasource.QueryField;
import stroom.query.common.v2.DataSourceProviderRegistry;
import stroom.query.common.v2.ExpressionContextFactory;
import stroom.query.common.v2.ResultStoreManager;
import stroom.query.common.v2.ResultStoreManager.RequestAndStore;
import stroom.query.language.SearchRequestFactory;
import stroom.query.language.functions.ExpressionContext;
import stroom.sqldatasource.impl.AthenaClientFactory;
import stroom.sqldatasource.impl.JdbcConnectionPoolCache;
import stroom.sqldatasource.impl.ResolvedAthenaConnection;
import stroom.sqldatasource.impl.SqlDataSourceConfig;
import stroom.sqldatasource.impl.SqlDataSourceSearchProvider;
import stroom.sqldatasource.impl.SqlDataSourceStore;
import stroom.sqldatasource.shared.JdbcConnectionSettings;
import stroom.sqldatasource.shared.SqlDataSourceDoc;
import stroom.test.AbstractCoreIntegrationTest;
import stroom.test.common.util.db.DbTestUtil;
import stroom.util.shared.ErrorMessage;
import stroom.util.shared.PageRequest;
import stroom.util.shared.Severity;

import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.athena.AthenaClient;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.UnaryOperator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

/// A SQL Data Source pointed at the test MySQL database, through the real store, credentials store,
/// connection pools and query machinery.
///
/// These tests run with a security context that allows everything, so permissions are tested by
/// TestSqlDataSourcePermissions instead.
class TestSqlDataSourceIntegration extends AbstractCoreIntegrationTest {

    private static final String READER_USER = SqlDataSourceTestDatabase.READER_USER;
    private static final String TABLE = SqlDataSourceTestDatabase.TABLE;
    private static final String ALL_STAFF_SQL = SqlDataSourceTestDatabase.ALL_STAFF_SQL;

    @Inject
    private SqlDataSourceStore store;
    @Inject
    private JdbcConnectionPoolCache poolCache;
    @Inject
    private CredentialsService credentialsService;
    @Inject
    private SqlDataSourceSearchProvider searchProvider;
    @Inject
    private ResultStoreManager resultStoreManager;
    @Inject
    private SearchRequestFactory searchRequestFactory;
    @Inject
    private ExpressionContextFactory expressionContextFactory;
    @Inject
    private AthenaClientFactory athenaClientFactory;
    @Inject
    private DataSourceProviderRegistry dataSourceProviderRegistry;

    private String url;
    private String credentialName;

    @BeforeEach
    void setUp() throws SQLException {
        url = SqlDataSourceTestDatabase.prepare();
        credentialName = "sqlds-reader-" + UUID.randomUUID();
        credentialsService.storeCredentials(
                SqlDataSourceTestDatabase.readerCredential(UUID.randomUUID().toString(), credentialName));
        setConfigValueMapper(SqlDataSourceConfig.class,
                config -> SqlDataSourceTestDatabase.allowingOnly(config, url));
    }

    private SqlDataSourceDoc saveDocument() {
        return saveDocument(UnaryOperator.identity());
    }

    private SqlDataSourceDoc saveDocument(final UnaryOperator<SqlDataSourceDoc.Builder> changes) {
        final DocRef docRef = store.createDocument("HR " + UUID.randomUUID());
        final SqlDataSourceDoc doc = store.readDocument(docRef);
        return store.writeDocument(changes.apply(doc.copy()
                        .jdbc(JdbcConnectionSettings.builder()
                                .url(url)
                                .credentialName(credentialName)
                                .build()))
                .build());
    }

    // Runs a StroomQL query against the document, with the SQL in a triple-quoted sql clause.
    private SearchResponse search(final SqlDataSourceDoc doc,
                                  final String stroomQlAfterFrom,
                                  final String sql,
                                  final TimeRange timeRange,
                                  final List<Param> params) {
        final SearchRequest withSql = searchRequest(doc, stroomQlAfterFrom, sql, timeRange, params);
        final SearchResponse response = resultStoreManager.search(withSql);
        resultStoreManager.destroy(response.getKey(), DestroyReason.NO_LONGER_NEEDED);
        assertThat(response.complete()).as("search is complete").isTrue();
        return response;
    }

    private SearchRequest searchRequest(final SqlDataSourceDoc doc,
                                        final String stroomQlAfterFrom,
                                        final String sql,
                                        final TimeRange timeRange,
                                        final List<Param> params) {
        final SearchRequest sampleRequest = new SearchRequest(
                null,
                null,
                Query.builder()
                        .timeRange(timeRange)
                        .params(params)
                        .build(),
                null,
                DateTimeSettings.builder().build(),
                false);
        final ExpressionContext expressionContext = expressionContextFactory.createContext(sampleRequest);
        final String sqlClause = sql == null
                ? ""
                : "sql \"\"\"\n" + sql + "\n\"\"\"\n";
        return searchRequestFactory.create(
                "from \"" + doc.getName() + "\"\n" + sqlClause + stroomQlAfterFrom,
                sampleRequest,
                expressionContext);
    }

    private static List<List<String>> rows(final SearchResponse response, final int selectedFields) {
        final List<List<String>> rows = new ArrayList<>();
        for (final Result result : response.getResults()) {
            for (final Row row : ((TableResult) result).getRows()) {
                rows.add(row.getValues().subList(0, selectedFields));
            }
        }
        return rows;
    }

    private static List<String> messages(final SearchResponse response, final Severity severity) {
        if (response.getErrorMessages() == null) {
            return List.of();
        }
        return response.getErrorMessages()
                .stream()
                .filter(errorMessage -> severity.equals(errorMessage.getSeverity()))
                .map(ErrorMessage::getMessage)
                .toList();
    }

    @Test
    void savedDocumentQueriesTheDatabaseWithItsCredential() throws SQLException {
        final SqlDataSourceDoc doc = saveDocument();

        final List<String> names = poolCache.useConnection(doc.getJdbc(), connection -> {
            try (final Statement statement = connection.createStatement();
                    final ResultSet resultSet = statement.executeQuery(
                            "SELECT name FROM " + TABLE + " ORDER BY id")) {
                final List<String> rows = new ArrayList<>();
                while (resultSet.next()) {
                    rows.add(resultSet.getString(1));
                }
                return rows;
            }
        });

        assertThat(names).containsExactly("Smith", "Jones", "Brown");
    }

    @Test
    void connectionIsReadOnly() {
        final SqlDataSourceDoc doc = saveDocument();

        assertThatThrownBy(() -> poolCache.useConnection(doc.getJdbc(), connection -> {
            assertThat(connection.isReadOnly()).isTrue();
            assertThat(connection.getAutoCommit()).isFalse();
            try (final Statement statement = connection.createStatement()) {
                return statement.executeUpdate("DELETE FROM " + TABLE);
            }
        }))
                .isInstanceOf(SQLException.class);
    }

    @Test
    void documentWithUrlNotAllowedCannotBeSaved() {
        final DocRef docRef = store.createDocument("Elsewhere " + UUID.randomUUID());
        final SqlDataSourceDoc doc = store.readDocument(docRef);

        assertThatThrownBy(() -> store.writeDocument(doc.copy()
                .jdbc(JdbcConnectionSettings.builder()
                        .url("jdbc:mysql://elsewhere:3306/hr")
                        .build())
                .build()))
                .hasMessageContaining("allowedUrlPatterns");
    }

    @Test
    void fieldsAreTheColumnsOfTheSql() {
        final SqlDataSourceDoc doc = saveDocument();

        final List<QueryField> fields = searchProvider.getFields(doc.asDocRef(), ALL_STAFF_SQL);

        assertThat(fields)
                .extracting(QueryField::getFldName, QueryField::getFldType)
                .containsExactly(
                        tuple("id", FieldType.INTEGER),
                        tuple("name", FieldType.TEXT),
                        tuple("joined", FieldType.DATE));
    }

    @Test
    void queryReturnsTheRowsOfTheSql() {
        final SqlDataSourceDoc doc = saveDocument();

        final SearchResponse response = search(doc, "select id, name", ALL_STAFF_SQL, null, null);

        assertThat(messages(response, Severity.ERROR)).isEmpty();
        assertThat(rows(response, 2)).containsExactly(
                List.of("1", "Smith"),
                List.of("2", "Jones"),
                List.of("3", "Brown"));
    }

    @Test
    void whereClauseFiltersTheRowsInStroom() {
        final SqlDataSourceDoc doc = saveDocument();

        final SearchResponse response = search(
                doc, "where id > 1 and name != 'Brown' select name", ALL_STAFF_SQL, null, null);

        assertThat(messages(response, Severity.ERROR)).isEmpty();
        assertThat(rows(response, 1)).containsExactly(List.of("Jones"));
    }

    @Test
    void stroomFunctionsWorkOnTheRows() {
        final SqlDataSourceDoc doc = saveDocument();

        final SearchResponse response = search(
                doc,
                "eval year = formatDate(joined, 'yyyy') eval initial = substring(name, 0, 1) select initial, year",
                ALL_STAFF_SQL,
                null,
                null);

        assertThat(messages(response, Severity.ERROR)).isEmpty();
        assertThat(rows(response, 2)).containsExactly(
                List.of("S", "2020"),
                List.of("J", "2024"),
                List.of("B", "2026"));
    }

    @Test
    void sqlPlaceholdersTakeTheTimeRange() {
        final SqlDataSourceDoc doc = saveDocument();

        final SearchResponse response = search(
                doc,
                "select name",
                "select name from " + TABLE + " where joined >= :from and joined < :to order by id",
                new TimeRange(null, "2023-01-01T00:00:00.000Z", "2025-01-01T00:00:00.000Z"),
                null);

        assertThat(messages(response, Severity.ERROR)).isEmpty();
        assertThat(rows(response, 1)).containsExactly(List.of("Jones"));
    }

    @Test
    void timeColumnFiltersTheRowsWhenTheSqlDoesNotTakeTheTimeRange() {
        final SqlDataSourceDoc doc = saveDocument(builder -> builder.timeField("joined"));

        final SearchResponse response = search(
                doc,
                "select name",
                ALL_STAFF_SQL,
                new TimeRange(null, "2024-06-01T09:30:00.000Z", "2026-01-01T00:00:00.000Z"),
                null);

        assertThat(messages(response, Severity.ERROR)).isEmpty();
        assertThat(rows(response, 1))
                .as("from is inclusive and to exclusive, with times read as UTC")
                .containsExactly(List.of("Jones"));
    }

    @Test
    void timeRangeIsIgnoredWithAWarningWhenNothingCanTakeIt() {
        final SqlDataSourceDoc doc = saveDocument();

        final SearchResponse response = search(
                doc,
                "select name",
                ALL_STAFF_SQL,
                new TimeRange(null, "2024-01-01T00:00:00.000Z", "2025-01-01T00:00:00.000Z"),
                null);

        assertThat(rows(response, 1)).hasSize(3);
        assertThat(messages(response, Severity.WARNING)).anySatisfy(message ->
                assertThat(message).contains("time range was ignored"));
    }

    @Test
    void parameterIsBoundIntoTheSql() {
        final SqlDataSourceDoc doc = saveDocument();

        final SearchResponse response = search(
                doc,
                "select id",
                "select id from " + TABLE + " where name = :who",
                null,
                List.of(new Param("who", "Brown")));

        assertThat(messages(response, Severity.ERROR)).isEmpty();
        assertThat(rows(response, 1)).containsExactly(List.of("3"));
    }

    @Test
    void parameterCannotInjectSql() {
        final SqlDataSourceDoc doc = saveDocument();

        final SearchResponse response = search(
                doc,
                "select id",
                "select id from " + TABLE + " where name = :who",
                null,
                List.of(new Param("who", "x' or '1'='1")));

        assertThat(messages(response, Severity.ERROR)).isEmpty();
        assertThat(rows(response, 1)).isEmpty();
    }

    @Test
    void rowLimitStopsTheQueryWithAWarning() {
        final SqlDataSourceDoc doc = saveDocument(builder -> builder.maxRows(2));

        final SearchResponse response = search(doc, "select name", ALL_STAFF_SQL, null, null);

        // A small row limit must not be refused for being below the document's fetch size.
        assertThat(messages(response, Severity.ERROR)).isEmpty();
        assertThat(rows(response, 1)).containsExactly(List.of("Smith"), List.of("Jones"));
        assertThat(messages(response, Severity.WARNING)).anySatisfy(message ->
                assertThat(message).contains("more than 2 rows"));
    }

    @Test
    void sqlErrorIsReportedAsASearchError() {
        final SqlDataSourceDoc doc = saveDocument();

        final SearchResponse response = search(doc, "select name", "select name from no_such_table", null, null);

        assertThat(rows(response, 1)).isEmpty();
        assertThat(messages(response, Severity.ERROR)).anySatisfy(message ->
                assertThat(message).contains("no_such_table"));
    }

    @Test
    void whereFieldThatIsNotAColumnIsReported() {
        final SqlDataSourceDoc doc = saveDocument();

        final SearchResponse response = search(
                doc, "where surname = 'Jones' select name", ALL_STAFF_SQL, null, null);

        assertThat(messages(response, Severity.ERROR)).anySatisfy(message ->
                assertThat(message).contains("'surname'"));
    }

    @Test
    void secondStatementIsRefusedBeforeReachingTheDatabase() {
        final SqlDataSourceDoc doc = saveDocument();

        assertThatThrownBy(() -> search(
                doc, "select name", "select name from " + TABLE + "; delete from " + TABLE, null, null))
                .hasMessageContaining("single statement");
    }

    @Test
    void timeColumnMissingFromTheSqlIsExplained() {
        final SqlDataSourceDoc doc = saveDocument(builder -> builder.timeField("joined"));

        final SearchResponse response = search(
                doc,
                "select name",
                "select name from " + TABLE,
                new TimeRange(null, "2024-01-01T00:00:00.000Z", "2025-01-01T00:00:00.000Z"),
                null);

        assertThat(messages(response, Severity.ERROR)).anySatisfy(message ->
                assertThat(message).contains("does not return the time column 'joined'"));
    }

    @Test
    void destroyingTheSearchStopsTheSqlInTheDatabase() throws InterruptedException {
        final SqlDataSourceDoc doc = saveDocument();
        final SearchRequest searchRequest = searchRequest(doc, "select s", "select sleep(60) as s", null, null);

        final RequestAndStore requestAndStore = resultStoreManager.getResultStore(searchRequest);
        waitForSleepingQueries(1);

        resultStoreManager.destroy(requestAndStore.searchRequest().getKey(), DestroyReason.NO_LONGER_NEEDED);

        assertThat(requestAndStore.resultStore().awaitCompletion(15, TimeUnit.SECONDS))
                .as("the search finished long before the SQL's 60 second sleep")
                .isTrue();
        waitForSleepingQueries(0);
    }

    // Waits up to 15 seconds for the reader's 'sleep(60)' queries running in MySQL to number the count.
    private void waitForSleepingQueries(final int count) throws InterruptedException {
        final long deadline = System.currentTimeMillis() + 15_000;
        int running = -1;
        while (System.currentTimeMillis() < deadline) {
            final int[] found = new int[1];
            DbTestUtil.doWithRootConnection(connection -> {
                try (final Statement statement = connection.createStatement();
                        final ResultSet resultSet = statement.executeQuery(
                                "SELECT COUNT(*) FROM information_schema.PROCESSLIST "
                                + "WHERE USER = '" + READER_USER + "' AND INFO LIKE 'select sleep(60)%'")) {
                    resultSet.next();
                    found[0] = resultSet.getInt(1);
                } catch (final SQLException e) {
                    throw new RuntimeException(e);
                }
            });
            running = found[0];
            if (running == count) {
                return;
            }
            Thread.sleep(200);
        }
        assertThat(running).as("sleeping queries running in MySQL").isEqualTo(count);
    }

    @Test
    void athenaClientCanBeBuiltOnStroomsClassPath() {
        // Stroom has several AWS HTTP clients on its class path, which the SDK refuses to choose
        // between unless told. Building a client makes no call to AWS.
        final ResolvedAthenaConnection connection = new ResolvedAthenaConnection(
                "eu-west-2", "primary", "AwsDataCatalog", "default", null, 0,
                new AwsKeySecret("AKIAEXAMPLE", "not-a-real-secret"));

        try (final AthenaClient client = athenaClientFactory.create(connection)) {
            assertThat(client.serviceClientConfiguration().region().id()).isEqualTo("eu-west-2");
        }
    }

    @Test
    void missingSqlIsReported() {
        final SqlDataSourceDoc doc = saveDocument();

        assertThatThrownBy(() -> search(doc, "select name", null, null, null))
                .hasMessageContaining("needs the SQL to run, in a sql clause");
    }

    @Test
    void selectStarGivesEveryColumnOfTheSql() {
        final SqlDataSourceDoc doc = saveDocument();

        final SearchResponse response = search(doc, "select *", ALL_STAFF_SQL, null, null);

        assertThat(messages(response, Severity.ERROR)).isEmpty();
        assertThat(rows(response, 3).getFirst())
                .as("id, name and joined, in some order")
                .contains("1", "Smith");
    }

    @Test
    void sqlWithDoubleQuotesAndBackslashesNeedsNoEscaping() {
        final SqlDataSourceDoc doc = saveDocument();

        final SearchResponse response = search(
                doc,
                "select quoted, slash",
                "select '\"Smith\"' as quoted, 'a\\\\b' as slash from " + TABLE + " where id = 1",
                null,
                null);

        assertThat(messages(response, Severity.ERROR)).isEmpty();
        assertThat(rows(response, 2)).containsExactly(List.of("\"Smith\"", "a\\b"));
    }

    @Test
    void completionListsTheColumnsOfTheSqlInTheQuery() {
        final SqlDataSourceDoc doc = saveDocument();
        final String stroomQl = "from \"" + doc.getName() + "\" sql \"\"\"" + ALL_STAFF_SQL + "\"\"\" select ";

        final String sql = searchRequestFactory.extractNativeQuery(stroomQl).orElseThrow();
        final List<String> fieldNames = dataSourceProviderRegistry.getFieldInfo(new FindFieldCriteria(
                        PageRequest.unlimited(), FindFieldCriteria.DEFAULT_SORT_LIST, doc.asDocRef(), null, null, sql))
                .getValues()
                .stream()
                .map(QueryField::getFldName)
                .toList();

        assertThat(fieldNames).containsExactlyInAnyOrder("id", "name", "joined");
    }

    @Test
    void describingTheSqlDoesNotRunIt() {
        // MySQL's driver may run a prepared statement to describe it, which for a slow query would
        // make listing its fields as slow as running it.
        final SqlDataSourceDoc doc = saveDocument();
        final long start = System.nanoTime();

        final List<QueryField> fields = searchProvider.getFields(doc.asDocRef(), "select sleep(5) as s");

        assertThat(fields).extracting(QueryField::getFldName).containsExactly("s");
        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(4));
    }
}
