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
import stroom.query.api.SearchRequest;
import stroom.query.api.TimeRange;
import stroom.security.api.SecurityContext;
import stroom.security.shared.DocumentPermission;
import stroom.sqldatasource.shared.JdbcConnectionSettings;
import stroom.sqldatasource.shared.SqlConnectionType;
import stroom.sqldatasource.shared.SqlDataSourceDoc;
import stroom.util.shared.PermissionException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
    private SqlDataSourceSearchProvider provider;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        Mockito.when(securityContext.asProcessingUserResult(Mockito.any(Supplier.class)))
                .thenAnswer(invocation -> ((Supplier<?>) invocation.getArgument(0)).get());
        provider = new SqlDataSourceSearchProvider(
                null, store, null, securityContext, null, null, null, null, null, null, runner, resolver, fieldCache);
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

    @Test
    void athenaIsNotAvailableYet() {
        givenDoc(doc().connectionType(SqlConnectionType.ATHENA).build(), true);

        assertThatThrownBy(() -> provider.getFields(DOC_REF, "select 1"))
                .hasMessage(SqlDataSourceSearchProvider.ATHENA_NOT_AVAILABLE);
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
