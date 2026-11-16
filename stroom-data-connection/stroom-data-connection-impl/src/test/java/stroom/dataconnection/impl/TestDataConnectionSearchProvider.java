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

import stroom.dataconnection.shared.AthenaConnectionSettings;
import stroom.dataconnection.shared.DataConnectionDoc;
import stroom.dataconnection.shared.DataConnectionType;
import stroom.dataconnection.shared.JdbcConnectionSettings;
import stroom.docref.DocRef;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionTerm.Condition;
import stroom.query.api.Query;
import stroom.query.api.QueryKey;
import stroom.query.api.SearchRequest;
import stroom.query.api.TimeRange;
import stroom.query.api.datasource.FieldType;
import stroom.query.api.datasource.FindFieldCriteria;
import stroom.query.api.datasource.QueryField;
import stroom.query.common.v2.CoprocessorsFactory;
import stroom.query.common.v2.CoprocessorsImpl;
import stroom.query.common.v2.ErrorConsumerImpl;
import stroom.query.common.v2.ExpressionPredicateFactory;
import stroom.query.common.v2.FieldInfoResultPageFactory;
import stroom.query.common.v2.ResultStore;
import stroom.query.common.v2.ResultStoreFactory;
import stroom.query.language.functions.FieldIndex;
import stroom.security.api.SecurityContext;
import stroom.security.shared.DocumentPermission;
import stroom.task.api.TaskContext;
import stroom.task.api.TaskContextFactory;
import stroom.task.api.TaskManager;
import stroom.util.shared.PageRequest;
import stroom.util.shared.PermissionException;
import stroom.util.shared.ResultPage;
import stroom.util.shared.Severity;
import stroom.util.shared.time.SimpleDuration;
import stroom.util.shared.time.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

class TestDataConnectionSearchProvider {

    private static final DocRef DOC_REF = DataConnectionDoc.buildDocRef()
            .uuid("uuid-1")
            .name("HR")
            .build();
    private static final TimeRange TIME_RANGE = new TimeRange(null, "2026-11-01T00:00:00.000Z",
            "2026-11-10T00:00:00.000Z");
    private static final ExpressionOperator WHERE = ExpressionOperator.builder()
            .addTerm("name", Condition.EQUALS, "Jones")
            .build();

    private final SecurityContext securityContext = Mockito.mock(SecurityContext.class);
    private final DataConnectionStore store = Mockito.mock(DataConnectionStore.class);
    private final JdbcQueryRunner runner = Mockito.mock(JdbcQueryRunner.class);
    private final JdbcConnectionResolver resolver = Mockito.mock(JdbcConnectionResolver.class);
    private final SqlFieldCache fieldCache = Mockito.mock(SqlFieldCache.class);
    private final AthenaQueryRunner athenaRunner = Mockito.mock(AthenaQueryRunner.class);
    private final AthenaConnectionResolver athenaResolver = Mockito.mock(AthenaConnectionResolver.class);
    private final FieldInfoResultPageFactory fieldInfoResultPageFactory =
            Mockito.mock(FieldInfoResultPageFactory.class);
    private Connectors connectors;
    private DataConnectionSearchProvider provider;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        Mockito.when(securityContext.asProcessingUserResult(Mockito.any(Supplier.class)))
                .thenAnswer(invocation -> ((Supplier<?>) invocation.getArgument(0)).get());
        connectors = new Connectors(
                new JdbcConnector(runner, resolver, null, fieldCache),
                new AthenaConnector(athenaRunner, athenaResolver, null, fieldCache),
                new CsvConnector(null, DataConnectionConfig::new),
                new ExcelConnector(null, DataConnectionConfig::new),
                new LdapConnector(null, null, null, DataConnectionConfig::new));
        provider = new DataConnectionSearchProvider(
                null, store, null, securityContext, null, null, null, null, null, fieldInfoResultPageFactory,
                connectors);
        Mockito.when(fieldInfoResultPageFactory.create(Mockito.any(), Mockito.any()))
                .thenAnswer(invocation -> ResultPage.createUnboundedList(invocation.getArgument(1)));
    }

    private void givenDoc(final DataConnectionDoc doc, final boolean canUse) {
        Mockito.when(store.readDocument(DOC_REF)).thenReturn(doc);
        Mockito.when(securityContext.hasDocumentPermission(DOC_REF, DocumentPermission.USE)).thenReturn(canUse);
    }

    private static DataConnectionDoc.Builder doc() {
        return DataConnectionDoc.builder()
                .uuid(DOC_REF.getUuid())
                .name(DOC_REF.getName())
                .jdbc(JdbcConnectionSettings.builder()
                        .url("jdbc:mysql://reporting-db:3306/hr")
                        .build());
    }

    private static FindFieldCriteria fieldCriteria(final String sql) {
        return new FindFieldCriteria(
                PageRequest.unlimited(), FindFieldCriteria.DEFAULT_SORT_LIST, DOC_REF, null, null, sql);
    }

    @Test
    void jdbcSourceTakesItsSqlInEachQuery() {
        givenDoc(doc().build(), true);

        assertThat(provider.takesNativeQuery(DOC_REF)).isTrue();
    }

    @Test
    void athenaSourceTakesItsSqlInEachQuery() {
        givenDoc(athenaDoc(), true);

        assertThat(provider.takesNativeQuery(DOC_REF)).isTrue();
    }

    @Test
    void sourceTheUserMayNotUseIsTakenToTakeSql() {
        // So the query fails for want of permission, not over its sql clause.
        givenDoc(doc().build(), false);

        assertThat(provider.takesNativeQuery(DOC_REF)).isTrue();
    }

    @Test
    void missingSourceIsTakenToTakeSql() {
        givenDoc(null, true);

        assertThat(provider.takesNativeQuery(DOC_REF)).isTrue();
    }

    @Test
    void missingSourceIsReported() {
        givenDoc(null, true);

        assertThatThrownBy(() -> provider.getFields(DOC_REF, "select 1"))
                .hasMessageContaining("was not found");
    }

    @Test
    void fieldInfoListsTheColumnsOfTheSql() {
        // As StroomQL completion asks, with the SQL from the query's sql clause.
        givenDoc(doc().build(), true);
        final String sql = "select id from staff";
        Mockito.when(fieldCache.get(Mockito.any(), Mockito.eq(sql), Mockito.any()))
                .thenReturn(List.of(new SqlColumn("id", FieldType.INTEGER, Types.INTEGER)));

        assertThat(provider.getFieldInfo(fieldCriteria(sql)).getValues())
                .extracting(QueryField::getFldName, QueryField::getFldType)
                .containsExactly(tuple("id", FieldType.INTEGER));
    }

    @Test
    void fieldInfoWithoutSqlHasNoFields() {
        givenDoc(doc().build(), true);

        assertThat(provider.getFieldInfo(fieldCriteria(null)).getValues()).isEmpty();
        Mockito.verifyNoInteractions(fieldCache, resolver);
    }

    @Test
    void fieldInfoForSqlThatCannotBeDescribedHasNoFields() {
        // Completion runs while the SQL is typed, so a failure must not be an error.
        givenDoc(doc().build(), true);
        Mockito.when(fieldCache.get(Mockito.any(), Mockito.any(), Mockito.any()))
                .thenThrow(new RuntimeException("You have an error in your SQL syntax"));

        assertThat(provider.getFieldInfo(fieldCriteria("select id fro")).getValues()).isEmpty();
    }

    @Test
    void fieldInfoForADocumentTheUserMayNotUseHasNoFields() {
        givenDoc(doc().build(), false);

        assertThat(provider.getFieldInfo(fieldCriteria("select 1")).getValues()).isEmpty();
        Mockito.verifyNoInteractions(fieldCache);
    }

    @Test
    void userWithoutUseCannotListFields() {
        givenDoc(doc().build(), false);

        assertThatThrownBy(() -> provider.getFields(DOC_REF, "select 1"))
                .isInstanceOf(PermissionException.class);
        Mockito.verifyNoInteractions(runner);
    }

    @Test
    void userWithoutUseOnTheCredentialCannotListFieldsEvenWhenCached() {
        // Another user may have run the SQL, so the columns are in the cache; the credential is
        // still checked first.
        givenDoc(doc().build(), true);
        Mockito.when(resolver.resolve(Mockito.any()))
                .thenThrow(new PermissionException(null, "Unable to use credential 'hr-read-only'"));

        assertThatThrownBy(() -> provider.getFields(DOC_REF, "select 1"))
                .isInstanceOf(PermissionException.class);
        Mockito.verifyNoInteractions(fieldCache, runner);
    }

    @Test
    void userWithoutUseCannotRunAQuery() {
        givenDoc(doc().build(), false);
        final SearchRequest searchRequest = SearchRequest.builder()
                .query(Query.builder()
                        .dataSource(DOC_REF)
                        .nativeQuery("select 1")
                        .build())
                .build();

        assertThatThrownBy(() -> provider.createResultStore(searchRequest))
                .isInstanceOf(PermissionException.class);
        Mockito.verifyNoInteractions(runner);
    }

    @Test
    void userWithoutUseCannotReadDocumentation() {
        givenDoc(doc().build(), false);

        assertThatThrownBy(() -> provider.fetchDocumentation(DOC_REF))
                .isInstanceOf(PermissionException.class);
    }

    @Test
    void documentationEndsWithTheFieldsHint() {
        givenDoc(doc().description("Staff records.").build(), true);

        assertThat(provider.fetchDocumentation(DOC_REF))
                .hasValue("Staff records.\n\n" + DataConnectionSearchProvider.FIELDS_HINT);
    }

    @Test
    void athenaSourceDefaultsToFiveMinuteMinimumRefresh() {
        givenDoc(athenaDoc(), true);

        assertThat(provider.fetchMinRefreshInterval(DOC_REF))
                .hasValue(new SimpleDuration(5L, TimeUnit.MINUTES));
    }

    @Test
    void athenaSourceUsesItsOwnMinimumRefresh() {
        givenDoc(doc()
                .sourceType(DataConnectionType.ATHENA)
                .athena(AthenaConnectionSettings.builder()
                        .region("eu-west-2")
                        .minRefreshMinutes(30)
                        .build())
                .build(), true);

        assertThat(provider.fetchMinRefreshInterval(DOC_REF))
                .hasValue(new SimpleDuration(30L, TimeUnit.MINUTES));
    }

    @Test
    void athenaSourceWithZeroMinimumHasNone() {
        givenDoc(doc()
                .sourceType(DataConnectionType.ATHENA)
                .athena(AthenaConnectionSettings.builder()
                        .region("eu-west-2")
                        .minRefreshMinutes(0)
                        .build())
                .build(), true);

        assertThat(provider.fetchMinRefreshInterval(DOC_REF)).isEmpty();
    }

    @Test
    void jdbcSourceHasNoMinimumRefreshByDefault() {
        givenDoc(doc().build(), true);

        assertThat(provider.fetchMinRefreshInterval(DOC_REF)).isEmpty();
    }

    @Test
    void jdbcSourceUsesItsOwnMinimumRefresh() {
        givenDoc(doc()
                .jdbc(JdbcConnectionSettings.builder()
                        .url("jdbc:mysql://reporting-db:3306/hr")
                        .minRefreshMinutes(2)
                        .build())
                .build(), true);

        assertThat(provider.fetchMinRefreshInterval(DOC_REF))
                .hasValue(new SimpleDuration(2L, TimeUnit.MINUTES));
    }

    @Test
    void jdbcMinimumIsIgnoredOnceSwitchedToAthena() {
        // Each type keeps its own settings; only the chosen type's minimum applies.
        givenDoc(doc()
                .sourceType(DataConnectionType.ATHENA)
                .jdbc(JdbcConnectionSettings.builder()
                        .minRefreshMinutes(0)
                        .build())
                .build(), true);

        assertThat(provider.fetchMinRefreshInterval(DOC_REF))
                .hasValue(new SimpleDuration(5L, TimeUnit.MINUTES));
    }

    @Test
    void userWithoutUseCannotReadMinimumRefresh() {
        givenDoc(athenaDoc(), false);

        assertThatThrownBy(() -> provider.fetchMinRefreshInterval(DOC_REF))
                .isInstanceOf(PermissionException.class);
    }

    private static DataConnectionDoc athenaDoc() {
        return doc()
                .sourceType(DataConnectionType.ATHENA)
                .athena(AthenaConnectionSettings.builder()
                        .region("eu-west-2")
                        .build())
                .build();
    }

    private static SearchRequest searchRequest(final String sql, final TimeRange timeRange) {
        return SearchRequest.builder()
                .query(Query.builder()
                        .dataSource(DOC_REF)
                        .nativeQuery(sql)
                        .timeRange(timeRange)
                        .build())
                .build();
    }

    @Test
    void athenaFieldsAreNoneUntilTheSqlHasRun() {
        // Describing an Athena query means running it, which is billed.
        givenDoc(athenaDoc(), true);
        Mockito.when(fieldCache.getIfPresent(Mockito.any(), Mockito.any())).thenReturn(Optional.empty());

        assertThat(provider.getFields(DOC_REF, "select 1")).isEmpty();
        Mockito.verify(athenaResolver).resolve(Mockito.any());
        Mockito.verifyNoInteractions(athenaRunner);
    }

    @Test
    void athenaFieldsComeFromAnEarlierRun() {
        givenDoc(athenaDoc(), true);
        Mockito.when(fieldCache.getIfPresent(Mockito.any(), Mockito.any())).thenReturn(Optional.of(
                List.of(new SqlColumn("event_time", FieldType.DATE, Types.TIMESTAMP))));

        assertThat(provider.getFields(DOC_REF, "select event_time from events"))
                .extracting(QueryField::getFldName)
                .containsExactly("event_time");
    }

    @Test
    void athenaFieldsNeedUseOnTheCredential() {
        givenDoc(athenaDoc(), true);
        Mockito.when(athenaResolver.resolve(Mockito.any()))
                .thenThrow(new PermissionException(null, "Unable to use credential 'aws'"));

        assertThatThrownBy(() -> provider.getFields(DOC_REF, "select 1"))
                .isInstanceOf(PermissionException.class);
        Mockito.verifyNoInteractions(fieldCache);
    }

    @Test
    void athenaQueryWithATimeRangeTheSqlDoesNotTakeIsRefused() {
        // Filtering in Stroom after Athena has scanned everything would be billed for nothing.
        givenDoc(athenaDoc(), true);

        assertThatThrownBy(() -> provider.createResultStore(searchRequest("select * from events", TIME_RANGE)))
                .hasMessage(AthenaConnector.TIME_RANGE_REFUSED);
        Mockito.verifyNoInteractions(athenaRunner);
    }

    @Test
    @SuppressWarnings("unchecked")
    void athenaSearchRunsTheSqlCachesItsColumnsAndReportsTheDataScanned() {
        givenDoc(athenaDoc(), true);
        final TaskContext taskContext = Mockito.mock(TaskContext.class);
        final TaskContextFactory taskContextFactory = Mockito.mock(TaskContextFactory.class);
        Mockito.when(taskContextFactory.context(Mockito.anyString(), Mockito.any(Consumer.class)))
                .thenAnswer(invocation -> (Runnable) () ->
                        ((Consumer<TaskContext>) invocation.getArgument(1)).accept(taskContext));
        final FieldIndex fieldIndex = new FieldIndex();
        fieldIndex.create("event_time");
        final ErrorConsumerImpl errorConsumer = new ErrorConsumerImpl();
        final CoprocessorsImpl coprocessors = Mockito.mock(CoprocessorsImpl.class);
        Mockito.when(coprocessors.getFieldIndex()).thenReturn(fieldIndex);
        Mockito.when(coprocessors.getErrorConsumer()).thenReturn(errorConsumer);
        final CoprocessorsFactory coprocessorsFactory = Mockito.mock(CoprocessorsFactory.class);
        Mockito.when(coprocessorsFactory.create(Mockito.any(SearchRequest.class), Mockito.any()))
                .thenReturn(coprocessors);
        final ResultStore resultStore = Mockito.mock(ResultStore.class);
        final ResultStoreFactory resultStoreFactory = Mockito.mock(ResultStoreFactory.class);
        Mockito.when(resultStoreFactory.create(Mockito.any(), Mockito.any())).thenReturn(resultStore);
        final List<SqlColumn> columns = List.of(new SqlColumn("event_time", FieldType.DATE, Types.TIMESTAMP));
        Mockito.when(athenaRunner.run(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(),
                Mockito.any())).thenAnswer(invocation -> {
                    ((Function<List<SqlColumn>, SqlRowConsumer>) invocation.getArgument(3)).apply(columns);
                    ((Consumer<String>) invocation.getArgument(5)).accept("Amazon Athena query q-1 running");
                    return new QueryRunSummary(columns, List.of(), 0, 0, false, 100,
                            "Amazon Athena query q-1 scanned 2 MB.");
                });
        final DataConnectionSearchProvider searchingProvider = new DataConnectionSearchProvider(
                Runnable::run, store, null, securityContext, coprocessorsFactory, resultStoreFactory,
                Mockito.mock(TaskManager.class), taskContextFactory, new ExpressionPredicateFactory(), null,
                connectors);
        final String sql = "select event_time from events where event_time >= :from";

        searchingProvider.createResultStore(searchRequest(sql, TIME_RANGE).copy()
                .key(new QueryKey("key-1"))
                .build());

        Mockito.verify(athenaRunner).run(
                Mockito.any(),
                Mockito.any(),
                Mockito.eq(List.of(Instant.parse(TIME_RANGE.getFrom()))),
                Mockito.any(),
                Mockito.any(),
                Mockito.any());
        Mockito.verifyNoInteractions(runner);
        Mockito.verify(fieldCache).put(Mockito.any(), Mockito.eq(sql), Mockito.eq(columns));
        assertThat(errorConsumer.getErrorMessages())
                .anySatisfy(message -> {
                    assertThat(message.getSeverity()).isEqualTo(Severity.INFO);
                    assertThat(message.getMessage()).contains("scanned 2 MB");
                });
        Mockito.verify(taskContext, Mockito.atLeastOnce()).info(Mockito.any());
        Mockito.verify(resultStore).signalComplete();
    }

    @Test
    void athenaQueryThatWritesIsRefused() {
        givenDoc(athenaDoc(), true);

        assertThatThrownBy(() -> provider.createResultStore(searchRequest(
                "create table copy as select * from events", null)))
                .hasMessageContaining("must start with SELECT");
        Mockito.verifyNoInteractions(athenaRunner);
    }

    @Test
    void athenaDocumentWithoutSettingsIsReported() {
        givenDoc(doc().sourceType(DataConnectionType.ATHENA).build(), true);

        assertThatThrownBy(() -> provider.getFields(DOC_REF, "select 1"))
                .hasMessageContaining("has no Amazon Athena settings");
    }

    @Test
    void documentWithoutUrlIsReported() {
        givenDoc(doc().jdbc(null).build(), true);

        assertThatThrownBy(() -> provider.getFields(DOC_REF, "select 1"))
                .hasMessageContaining("has no JDBC URL");
    }

    @Test
    void noTimeFieldIsReported() {
        // Otherwise ResultStoreManager would add the time range to the where expression itself.
        assertThat(provider.getTimeField(DOC_REF)).isEmpty();
    }

    @SuppressWarnings("unchecked")
    private DataConnectionSearchProvider searchingProvider(final ErrorConsumerImpl errorConsumer,
                                                               final ResultStore resultStore) {
        final TaskContext taskContext = Mockito.mock(TaskContext.class);
        final TaskContextFactory taskContextFactory = Mockito.mock(TaskContextFactory.class);
        Mockito.when(taskContextFactory.context(Mockito.anyString(), Mockito.any(Consumer.class)))
                .thenAnswer(invocation -> (Runnable) () ->
                        ((Consumer<TaskContext>) invocation.getArgument(1)).accept(taskContext));
        final CoprocessorsImpl coprocessors = Mockito.mock(CoprocessorsImpl.class);
        Mockito.when(coprocessors.getFieldIndex()).thenReturn(new FieldIndex());
        Mockito.when(coprocessors.getErrorConsumer()).thenReturn(errorConsumer);
        final CoprocessorsFactory coprocessorsFactory = Mockito.mock(CoprocessorsFactory.class);
        Mockito.when(coprocessorsFactory.create(Mockito.any(SearchRequest.class), Mockito.any()))
                .thenReturn(coprocessors);
        final ResultStoreFactory resultStoreFactory = Mockito.mock(ResultStoreFactory.class);
        Mockito.when(resultStoreFactory.create(Mockito.any(), Mockito.any())).thenReturn(resultStore);
        return new DataConnectionSearchProvider(
                Runnable::run, store, null, securityContext, coprocessorsFactory, resultStoreFactory,
                Mockito.mock(TaskManager.class), taskContextFactory, new ExpressionPredicateFactory(), null,
                connectors);
    }

    private static SearchRequest keyedSearchRequest(final String sql, final TimeRange timeRange) {
        return searchRequest(sql, timeRange).copy()
                .key(new QueryKey("key-1"))
                .build();
    }

    private void givenJdbcRunReturns(final QueryRunSummary summary) throws SQLException {
        Mockito.when(runner.run(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any()))
                .thenReturn(summary);
    }

    @Test
    void jdbcSearchWarnsThatTheTimeRangeWasIgnored() throws SQLException {
        // The SQL has no :from or :to and the document no time column, so the connector warns.
        givenDoc(doc().build(), true);
        givenJdbcRunReturns(new QueryRunSummary(List.of(), List.of(), 0, 0, false, 100, null));
        final ErrorConsumerImpl errorConsumer = new ErrorConsumerImpl();
        final ResultStore resultStore = Mockito.mock(ResultStore.class);

        searchingProvider(errorConsumer, resultStore)
                .createResultStore(keyedSearchRequest("select * from events", TIME_RANGE));

        assertThat(errorConsumer.getErrorMessages())
                .anySatisfy(message -> {
                    assertThat(message.getSeverity()).isEqualTo(Severity.WARNING);
                    assertThat(message.getMessage()).isEqualTo(JdbcConnector.TIME_RANGE_IGNORED);
                });
        Mockito.verify(resultStore).signalComplete();
    }

    @Test
    void rowLimitAndUnmatchedFieldsAreWarned() throws SQLException {
        givenDoc(doc().build(), true);
        givenJdbcRunReturns(new QueryRunSummary(List.of(), List.of("salary"), 100, 100, true, 100, null));
        final ErrorConsumerImpl errorConsumer = new ErrorConsumerImpl();
        final ResultStore resultStore = Mockito.mock(ResultStore.class);

        searchingProvider(errorConsumer, resultStore).createResultStore(keyedSearchRequest("select 1", null));

        assertThat(errorConsumer.getErrorMessages())
                .anySatisfy(message -> assertThat(message.getMessage())
                        .contains("more than 100 rows, so only the first 100 were read"))
                .anySatisfy(message -> assertThat(message.getMessage())
                        .contains("not columns returned by the query")
                        .contains("salary"));
        Mockito.verify(resultStore, Mockito.never()).addError(Mockito.any());
    }

    @Test
    void interruptedSearchIsReportedAndKeepsTheInterrupt() throws SQLException {
        givenDoc(doc().build(), true);
        final InterruptedException interrupted = new InterruptedException("Stopped");
        Mockito.when(runner.run(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any()))
                .thenAnswer(invocation -> {
                    throw interrupted;
                });
        final ResultStore resultStore = Mockito.mock(ResultStore.class);

        try {
            searchingProvider(new ErrorConsumerImpl(), resultStore)
                    .createResultStore(keyedSearchRequest("select 1", null));

            // The search runs on this thread in the test, so the interrupt is on it.
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
            Mockito.verify(resultStore).addError(interrupted);
            Mockito.verify(resultStore).signalComplete();
        } finally {
            Thread.interrupted();
        }
    }
}
