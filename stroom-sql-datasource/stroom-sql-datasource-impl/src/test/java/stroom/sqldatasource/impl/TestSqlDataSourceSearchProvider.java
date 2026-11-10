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

package stroom.sqldatasource.impl;

import stroom.docref.DocRef;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionOperator.Op;
import stroom.query.api.ExpressionTerm;
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
import stroom.sqldatasource.shared.AthenaConnectionSettings;
import stroom.sqldatasource.shared.JdbcConnectionSettings;
import stroom.sqldatasource.shared.SqlConnectionType;
import stroom.sqldatasource.shared.SqlDataSourceDoc;
import stroom.task.api.TaskContext;
import stroom.task.api.TaskContextFactory;
import stroom.task.api.TaskManager;
import stroom.util.shared.PageRequest;
import stroom.util.shared.PermissionException;
import stroom.util.shared.ResultPage;
import stroom.util.shared.Severity;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

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

class TestSqlDataSourceSearchProvider {

    private static final DocRef DOC_REF = SqlDataSourceDoc.buildDocRef()
            .uuid("uuid-1")
            .name("HR")
            .build();
    private static final TimeRange TIME_RANGE = new TimeRange(null, "2026-11-01T00:00:00.000Z",
            "2026-11-10T00:00:00.000Z");
    private static final ExpressionOperator WHERE = ExpressionOperator.builder()
            .addTerm("name", Condition.EQUALS, "Jones")
            .build();

    private final SecurityContext securityContext = Mockito.mock(SecurityContext.class);
    private final SqlDataSourceStore store = Mockito.mock(SqlDataSourceStore.class);
    private final JdbcQueryRunner runner = Mockito.mock(JdbcQueryRunner.class);
    private final JdbcConnectionResolver resolver = Mockito.mock(JdbcConnectionResolver.class);
    private final SqlFieldCache fieldCache = Mockito.mock(SqlFieldCache.class);
    private final AthenaQueryRunner athenaRunner = Mockito.mock(AthenaQueryRunner.class);
    private final AthenaConnectionResolver athenaResolver = Mockito.mock(AthenaConnectionResolver.class);
    private final FieldInfoResultPageFactory fieldInfoResultPageFactory =
            Mockito.mock(FieldInfoResultPageFactory.class);
    private SqlDataSourceSearchProvider provider;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        Mockito.when(securityContext.asProcessingUserResult(Mockito.any(Supplier.class)))
                .thenAnswer(invocation -> ((Supplier<?>) invocation.getArgument(0)).get());
        provider = new SqlDataSourceSearchProvider(
                null, store, null, securityContext, null, null, null, null, null, fieldInfoResultPageFactory,
                runner, resolver, athenaRunner, athenaResolver, fieldCache);
        Mockito.when(fieldInfoResultPageFactory.create(Mockito.any(), Mockito.any()))
                .thenAnswer(invocation -> ResultPage.createUnboundedList(invocation.getArgument(1)));
    }

    private void givenDoc(final SqlDataSourceDoc doc, final boolean canUse) {
        Mockito.when(store.readDocument(DOC_REF)).thenReturn(doc);
        Mockito.when(securityContext.hasDocumentPermission(DOC_REF, DocumentPermission.USE)).thenReturn(canUse);
    }

    private static SqlDataSourceDoc.Builder doc() {
        return SqlDataSourceDoc.builder()
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
    void takesItsSqlInEachQuery() {
        assertThat(provider.takesNativeQuery()).isTrue();
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
        assertThat(provider.getFieldInfo(fieldCriteria(null)).getValues()).isEmpty();
        Mockito.verifyNoInteractions(store, fieldCache);
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
                .hasValue("Staff records.\n\n" + SqlDataSourceSearchProvider.FIELDS_HINT);
    }

    private static SqlDataSourceDoc athenaDoc() {
        return doc()
                .connectionType(SqlConnectionType.ATHENA)
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
                .hasMessage(SqlDataSourceSearchProvider.ATHENA_TIME_RANGE_REFUSED);
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
        final SqlDataSourceSearchProvider searchingProvider = new SqlDataSourceSearchProvider(
                Runnable::run, store, null, securityContext, coprocessorsFactory, resultStoreFactory,
                Mockito.mock(TaskManager.class), taskContextFactory, new ExpressionPredicateFactory(), null,
                runner, resolver, athenaRunner, athenaResolver, fieldCache);
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
        givenDoc(doc().connectionType(SqlConnectionType.ATHENA).build(), true);

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

    @Test
    void timeRangeFiltersOnTheTimeColumnWhenTheSqlDoesNotTakeIt() {
        final ExpressionOperator expression = SqlDataSourceSearchProvider.addTimeRange(
                WHERE, TIME_RANGE, SqlStatementParser.parse("select * from events"), "event_time");

        assertThat(expression.op()).isEqualTo(Op.AND);
        assertThat(expression.getChildren()).hasSize(3);
        final ExpressionTerm from = (ExpressionTerm) expression.getChildren().get(0);
        final ExpressionTerm to = (ExpressionTerm) expression.getChildren().get(1);
        assertThat(from.getField()).isEqualTo("event_time");
        assertThat(from.getCondition()).isEqualTo(Condition.GREATER_THAN_OR_EQUAL_TO);
        assertThat(from.getValue()).isEqualTo(TIME_RANGE.getFrom());
        assertThat(to.getCondition()).isEqualTo(Condition.LESS_THAN);
        assertThat(to.getValue()).isEqualTo(TIME_RANGE.getTo());
        assertThat(expression.getChildren().get(2)).isEqualTo(WHERE);
    }

    @Test
    void timeRangeIsNotFilteredAgainWhenTheSqlTakesIt() {
        final ExpressionOperator expression = SqlDataSourceSearchProvider.addTimeRange(
                WHERE,
                TIME_RANGE,
                SqlStatementParser.parse("select * from events where t >= :from"),
                "event_time");

        assertThat(expression).isSameAs(WHERE);
    }

    @Test
    void timeRangeIsNotUsedWithoutATimeColumn() {
        final ExpressionOperator expression = SqlDataSourceSearchProvider.addTimeRange(
                null, TIME_RANGE, SqlStatementParser.parse("select * from events"), null);

        assertThat(expression).isNull();
    }

    @Test
    void openEndedTimeRangeAddsOneTerm() {
        final ExpressionOperator expression = SqlDataSourceSearchProvider.addTimeRange(
                null,
                new TimeRange(null, "2026-11-01T00:00:00.000Z", null),
                SqlStatementParser.parse("select * from events"),
                "event_time");

        assertThat(expression.getChildren()).hasSize(1);
    }

    @Test
    void noTimeRangeLeavesTheExpressionAlone() {
        final ExpressionOperator expression = SqlDataSourceSearchProvider.addTimeRange(
                WHERE, new TimeRange(null, null, null), SqlStatementParser.parse("select 1"), "event_time");

        assertThat(expression).isSameAs(WHERE);
    }
}
