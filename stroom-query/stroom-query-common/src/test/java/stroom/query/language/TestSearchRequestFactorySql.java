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

package stroom.query.language;

import stroom.query.api.Column;
import stroom.query.api.DateTimeSettings;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionTerm;
import stroom.query.api.Query;
import stroom.query.api.QueryKey;
import stroom.query.api.ResultRequest;
import stroom.query.api.SearchRequest;
import stroom.query.api.datasource.FindFieldCriteria;
import stroom.query.api.datasource.QueryField;
import stroom.query.api.token.TokenException;
import stroom.query.language.functions.ExpressionContext;
import stroom.security.mock.MockSecurityContext;
import stroom.util.shared.ResultPage;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

/// The `sql` clause, which gives the native query of a data source that takes one, such as a SQL
/// Data Source.
///
/// [MockDataSourceResolver] treats data sources named `sql_...` as ones that take a native query.
class TestSearchRequestFactorySql {

    private final List<FindFieldCriteria> fieldRequests = new ArrayList<>();
    private final SearchRequestFactory factory = new SearchRequestFactory(
            (keywordGroup, parentTableSettings) -> null,
            MockDataSourceResolver.getInstance(),
            () -> criteria -> {
                fieldRequests.add(criteria);
                return ResultPage.createUnboundedList(List.of(QueryField.createText("name")));
            },
            MockSecurityContext.getInstance());

    private SearchRequest create(final String stroomQl) {
        return create(stroomQl, Query.builder().build());
    }

    private SearchRequest create(final String stroomQl, final Query sampleQuery) {
        final DateTimeSettings dateTimeSettings = DateTimeSettings.builder().referenceTime(0L).build();
        final SearchRequest in = new SearchRequest(
                null,
                new QueryKey("test"),
                sampleQuery,
                new ArrayList<ResultRequest>(0),
                dateTimeSettings,
                false);
        final ExpressionContext expressionContext = ExpressionContext
                .builder()
                .dateTimeSettings(dateTimeSettings)
                .maxStringLength(100)
                .build();
        return factory.create(stroomQl, in, expressionContext);
    }

    @Test
    void tripleQuotedSqlIsTakenAsWritten() {
        final SearchRequest request = create("""
                from sql_hr
                sql \"""
                select "Name", 'it''s' as quote, 'a\\b' as backslash
                from staff
                \"""
                select Name""");

        assertThat(request.getQuery().getDataSource().getName()).isEqualTo("sql_hr");
        assertThat(request.getQuery().getNativeQuery()).isEqualTo("""

                select "Name", 'it''s' as quote, 'a\\b' as backslash
                from staff
                """);
    }

    @Test
    void sqlCanBeAnOrdinaryQuotedString() {
        final SearchRequest request = create("from sql_hr sql 'select name from staff' select name");

        assertThat(request.getQuery().getNativeQuery()).isEqualTo("select name from staff");
    }

    @Test
    void keywordsInTheSqlAreNotStroomQl() {
        // The SQL's own select, where and group by are inside the quotes, so they are not tokens.
        final SearchRequest request = create("""
                from sql_hr
                sql \"""select feed from events where id > 1 group by feed\"""
                where feed = 'TEST'
                select feed""");

        assertThat(request.getQuery().getNativeQuery())
                .isEqualTo("select feed from events where id > 1 group by feed");
        final ExpressionOperator expression = request.getQuery().getExpression();
        assertThat(((ExpressionTerm) expression.getChildren().getFirst()).getField()).isEqualTo("feed");
    }

    @Test
    void sqlMustComeStraightAfterFrom() {
        assertThatThrownBy(() -> create("from sql_hr where a = 1 sql \"\"\"select 1\"\"\" select a"))
                .isInstanceOf(TokenException.class)
                .hasMessageContaining("SQL");
    }

    @Test
    void misplacedSqlClauseIsReportedAsOutOfPlace() {
        assertThatThrownBy(() -> create("from sql_hr where a = 1 sql \"\"\"select 1\"\"\" select a"))
                .isInstanceOf(TokenException.class)
                .hasMessageContaining("SQL after WHERE");
        assertThatThrownBy(() -> create("sql \"\"\"select 1\"\"\" from sql_hr select a"))
                .isInstanceOf(TokenException.class);
    }

    @Test
    void onlyOneSqlClauseIsAllowed() {
        assertThatThrownBy(() -> create("from sql_hr sql 'select 1' sql \"\"\"select 2\"\"\" select a"))
                .isInstanceOf(TokenException.class);
    }

    @Test
    void blankSqlIsMissingSql() {
        assertThatThrownBy(() -> create("from sql_hr sql \"\"\"   \"\"\" select a"))
                .isInstanceOf(TokenException.class)
                .hasMessageContaining("needs the SQL to run");
    }

    @Test
    void sqlCannotBeAParameter() {
        // The SQL is always written in the query, never substituted in.
        assertThatThrownBy(() -> create("from sql_hr sql ${mySql} select a"))
                .isInstanceOf(TokenException.class)
                .hasMessageContaining("must be a quoted string");
    }

    @Test
    void fieldAndDataSourceNamedSqlStillWork() {
        // sql is only a keyword straight after from and the data source.
        final SearchRequest request = create("from index_view where sql = 'x' eval sql = 'y' select sql");
        assertThat(request.getQuery().getNativeQuery()).isNull();
        assertThat(((ExpressionTerm) request.getQuery().getExpression().getChildren().getFirst()).getField())
                .isEqualTo("sql");

        assertThat(create("from sql select a").getQuery().getDataSource().getName()).isEqualTo("sql");
    }

    @Test
    void missingSqlErrorPointsAtTheFromClause() {
        final Throwable thrown = catchThrowable(() -> create("from sql_hr select a"));

        assertThat(thrown).isInstanceOf(TokenException.class);
        assertThat(((TokenException) thrown).getToken()).isNotNull();
        assertThat(((TokenException) thrown).getToken().getText()).startsWith("from");
    }

    @Test
    void sqlFromTheCallerOnAnotherDataSourceIsRefused() {
        // No sql clause was written, so the error must not talk about one.
        assertThatThrownBy(() -> create("from index_view select a", Query.builder().nativeQuery("select 1").build()))
                .isInstanceOf(TokenException.class)
                .hasMessageStartingWith("The query has SQL");
    }

    @Test
    void selectStarAsksForTheColumnsOfTheSql() {
        final SearchRequest request = create("from sql_hr sql 'select name from staff' select *");

        assertThat(fieldRequests).extracting(FindFieldCriteria::getNativeQuery)
                .containsExactly("select name from staff");
        assertThat(request.getResultRequests().getFirst().getMappings().getFirst().getColumns())
                .extracting(Column::getName)
                .contains("name");
    }

    @Test
    void sqlCannotBeUsedWithAnotherDataSource() {
        assertThatThrownBy(() -> create("from index_view sql \"\"\"select 1\"\"\" select a"))
                .isInstanceOf(TokenException.class)
                .hasMessageContaining("can only be used with a data source that runs SQL")
                .hasMessageContaining("'index_view'");
    }

    @Test
    void dataSourceThatTakesSqlNeedsIt() {
        assertThatThrownBy(() -> create("from sql_hr select a"))
                .isInstanceOf(TokenException.class)
                .hasMessageContaining("needs the SQL to run, in a sql clause");
    }

    @Test
    void sqlMustBeQuoted() {
        assertThatThrownBy(() -> create("from sql_hr sql 42 select a"))
                .isInstanceOf(TokenException.class)
                .hasMessageContaining("must be a quoted string");
    }

    @Test
    void sqlClauseNeedsTheSql() {
        assertThatThrownBy(() -> create("from sql_hr sql where a = 1 select a"))
                .isInstanceOf(TokenException.class)
                .hasMessageContaining("Expected the SQL to run after sql");
    }

    @Test
    void onlyOneSqlStringIsAllowed() {
        assertThatThrownBy(() -> create("from sql_hr sql 'select 1' 'select 2' select a"))
                .isInstanceOf(TokenException.class)
                .hasMessageContaining("Unexpected token after the SQL");
    }

    @Test
    void nativeQueryGivenByTheCallerIsKept() {
        // Requests built in code may carry the SQL themselves rather than in the text.
        final SearchRequest request = create(
                "from sql_hr select a",
                Query.builder().nativeQuery("select a from t").build());

        assertThat(request.getQuery().getNativeQuery()).isEqualTo("select a from t");
    }

    @Test
    void sqlClauseWinsOverTheCallersNativeQuery() {
        final SearchRequest request = create(
                "from sql_hr sql 'select b from t' select b",
                Query.builder().nativeQuery("select a from t").build());

        assertThat(request.getQuery().getNativeQuery()).isEqualTo("select b from t");
    }

    @Test
    void nativeQueryIsFoundInAnUnfinishedQuery() {
        // As it is while the query is typed, for listing the SQL's columns.
        assertThat(factory.extractNativeQuery("from sql_hr sql \"\"\"select a, b from t\"\"\" select "))
                .hasValue("select a, b from t");
        assertThat(factory.extractNativeQuery("from sql_hr sql \"\"\"select a, b fr"))
                .as("the SQL is not finished")
                .isEqualTo(Optional.empty());
        assertThat(factory.extractNativeQuery("from index_view select a")).isEmpty();
        assertThat(factory.extractNativeQuery("from sql_hr sql ")).isEmpty();
    }
}
