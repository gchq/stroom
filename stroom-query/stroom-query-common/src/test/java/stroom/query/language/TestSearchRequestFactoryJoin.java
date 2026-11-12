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
import stroom.query.api.ExpressionOperator.Op;
import stroom.query.api.ExpressionUtil;
import stroom.query.api.JoinKey;
import stroom.query.api.JoinType;
import stroom.query.api.JoinWindow;
import stroom.query.api.Query;
import stroom.query.api.QueryJoin;
import stroom.query.api.QueryKey;
import stroom.query.api.ResultRequest;
import stroom.query.api.SearchRequest;
import stroom.query.api.TableSettings;
import stroom.query.api.datasource.QueryField;
import stroom.query.api.token.TokenException;
import stroom.query.language.functions.ExpressionContext;
import stroom.security.mock.MockSecurityContext;
import stroom.util.json.JsonUtil;
import stroom.util.shared.ResultPage;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/// Join lines and bracketed queries in StroomQL, as parsed into a [SearchRequest].
///
/// [MockDataSourceResolver] resolves any name to a data source of that name, and treats names
/// starting `sql_` as data sources that take a native query.
class TestSearchRequestFactoryJoin {

    private final SearchRequestFactory factory = new SearchRequestFactory(
            (keywordGroup, parentTableSettings) -> null,
            MockDataSourceResolver.getInstance(),
            () -> criteria -> ResultPage.createUnboundedList(List.of(QueryField.createText("name"))),
            MockSecurityContext.getInstance());

    @Test
    void plainQueryHasNoJoins() {
        final Query query = create("from events where UserId = 'jbloggs' select UserId").getQuery();

        assertThat(query.needsJoinSupport()).isFalse();
        assertThat(query.getJoins()).isNull();
        assertThat(query.getAlias()).isNull();
        assertThat(fields(query.getExpression())).containsExactly("UserId");
    }

    @Test
    void innerJoinToADataSource() {
        final Query query = create("""
                from events
                join hr_users as hr on UserId = hr.Username
                select UserId, hr.Department""").getQuery();

        assertThat(query.needsJoinSupport()).isTrue();
        assertThat(query.getDataSource().getName()).isEqualTo("events");
        final QueryJoin join = query.getJoins().getFirst();
        assertThat(join.getType()).isEqualTo(JoinType.INNER);
        assertThat(join.getAlias()).isEqualTo("hr");
        assertThat(join.getQuery().getDataSource().getName()).isEqualTo("hr_users");
        assertThat(join.getTableSettings()).isNull();
        assertThat(join.getWindow()).isNull();
        assertThat(join.getKeys()).containsExactly(new JoinKey("${UserId}", "${hr.Username}"));
    }

    @Test
    void joinKindsAreReadFromTheJoinLine() {
        assertThat(firstJoin("from events left join hr as h on UserId = h.Name select UserId").getType())
                .isEqualTo(JoinType.LEFT);
        assertThat(firstJoin("from events keep join hr as h on UserId = h.Name select UserId").getType())
                .isEqualTo(JoinType.KEEP);
        assertThat(firstJoin("from events drop join hr as h on UserId = h.Name select UserId").getType())
                .isEqualTo(JoinType.DROP);
        assertThat(firstJoin("from events\nLEFT   JOIN hr as h on UserId = h.Name select UserId").getType())
                .isEqualTo(JoinType.LEFT);
    }

    @Test
    void aliasDefaultsToTheDataSourceName() {
        final QueryJoin join = firstJoin("from events join hr on UserId = hr.Username select UserId");

        assertThat(join.getAlias()).isEqualTo("hr");
        assertThat(join.getKeys()).containsExactly(new JoinKey("${UserId}", "${hr.Username}"));
    }

    @Test
    void keysCanBeExpressionsWrittenEitherWayRound() {
        final QueryJoin join = firstJoin("""
                from events
                join hr on hr.Username = lowerCase(UserId) and Host = hr.Host
                select UserId""");

        assertThat(join.getKeys()).containsExactly(
                new JoinKey("lowerCase(${UserId})", "${hr.Username}"),
                new JoinKey("${Host}", "${hr.Host}"));
    }

    @Test
    void chainedJoinsUseEarlierJoinedFields() {
        final Query query = create("""
                from events
                left join hr on UserId = hr.Username
                left join departments as dept on hr.Department = dept.Name
                select UserId, dept.CostCentre""").getQuery();

        assertThat(query.getJoins()).extracting(QueryJoin::getAlias).containsExactly("hr", "dept");
        assertThat(query.getJoins().get(1).getKeys())
                .containsExactly(new JoinKey("${hr.Department}", "${dept.Name}"));
    }

    @Test
    void bracketedJoinHasItsOwnQuery() {
        final QueryJoin join = firstJoin("""
                from logons
                left join (
                  from file_events
                  where Action = 'Export'
                  group by UserId
                  select UserId, count() as Exports
                ) as ex on UserId = ex.UserId
                select UserId, ex.Exports""");

        assertThat(join.getQuery().getDataSource().getName()).isEqualTo("file_events");
        assertThat(fields(join.getQuery().getExpression())).containsExactly("Action");
        final TableSettings tableSettings = join.getTableSettings();
        assertThat(tableSettings).isNotNull();
        assertThat(tableSettings.getColumns()).extracting(Column::getName).containsExactly("UserId", "Exports");
        // A bracketed query's rows are not events, so it has no hidden event link columns.
        assertThat(tableSettings.getColumns()).noneMatch(Column::isSpecial);
    }

    @Test
    void bracketedFromReadsItsOwnQuery() {
        final SearchRequest request = create("""
                from (
                  from file_events
                  where Action = 'Export'
                  group by UserId
                  select UserId, count() as Exports
                ) as ex
                left join hr on ex.UserId = hr.Username
                where Exports > 10
                select UserId, Exports, hr.Department""");
        final Query query = request.getQuery();

        assertThat(query.needsJoinSupport()).isTrue();
        assertThat(query.getAlias()).isEqualTo("ex");
        assertThat(query.getDataSource().getName()).isEqualTo("file_events");
        assertThat(fields(query.getExpression())).containsExactly("Action");
        assertThat(query.getSourceTableSettings().getColumns())
                .extracting(Column::getName)
                .containsExactly("UserId", "Exports");
        // The outer where runs on the joined rows.
        assertThat(filteredFields(request)).containsExactly("Exports");
        // Its rows are not events, so the query has no hidden event link columns.
        assertThat(tableSettings(request).getColumns())
                .extracting(Column::getName)
                .noneMatch(name -> name.startsWith("__stream_id") || name.startsWith("__event_id"));
    }

    @Test
    void bracketedFromWithoutJoinsIsStillABracketedQuery() {
        final Query query = create("""
                from (from events group by UserId select UserId, count() as Logons)
                select UserId, Logons""").getQuery();

        assertThat(query.needsJoinSupport()).isTrue();
        assertThat(query.getJoins()).isNull();
        assertThat(query.getSourceTableSettings()).isNotNull();
    }

    @Test
    void windowedJoin() {
        final QueryJoin join = firstJoin("""
                from events as logon
                join file_events as export
                  on logon.UserId = export.UserId
                  within 10m after logon.EventTime
                select logon.UserId, export.FileName""");

        assertThat(join.getKeys()).containsExactly(new JoinKey("${logon.UserId}", "${export.UserId}"));
        assertThat(join.getWindow()).isEqualTo(new JoinWindow("10m", true, "logon.EventTime"));
        assertThat(firstJoin("""
                from events join file_events as x on UserId = x.UserId within 1h before EventTime
                select UserId""").getWindow())
                .isEqualTo(new JoinWindow("1h", false, "EventTime"));
    }

    @Test
    void whereConditionsGoToTheSearchesThatCanApplyThem() {
        final SearchRequest request = create("""
                from events as logon
                join file_events as export on UserId = export.UserId
                where logon.EventType = 'Logon'
                  and export.Action = 'Export'
                  and (Host = 'a' or export.Host = 'b')
                select UserId""");
        final Query query = request.getQuery();

        // A first-source condition loses the first source's alias.
        assertThat(fields(query.getExpression())).containsExactly("EventType");
        // An inner join's own condition moves into its search, without its alias.
        assertThat(fields(query.getJoins().getFirst().getQuery().getExpression())).containsExactly("Action");
        // A condition across both sources runs on the joined rows, through a hidden column for each field.
        assertThat(filteredFields(request)).containsExactly("Host", "export.Host");
    }

    @Test
    void leftJoinConditionsOnJoinedFieldsRunOnTheJoinedRows() {
        final SearchRequest request = create("""
                from events
                left join hr on UserId = hr.Username
                where hr.Department = 'Sales'
                select UserId""");

        assertThat(request.getQuery().getJoins().getFirst().getQuery().getExpression().hasChildren()).isFalse();
        assertThat(filteredFields(request)).containsExactly("hr.Department");
    }

    @Test
    void whereConditionsOnJoinedRowsAreAddedToFilter() {
        final SearchRequest request = create("""
                from events
                left join hr on UserId = hr.Username
                where hr.Department = 'Sales'
                filter UserId = 'jbloggs'
                select UserId""");

        final ExpressionOperator valueFilter = tableSettings(request).getValueFilter();
        assertThat(valueFilter.op()).isEqualTo(Op.AND);
        assertThat(filteredFields(request)).containsExactlyInAnyOrder("hr.Department", "UserId");
    }

    @Test
    void conditionOnJoinedRowsTestsTheFieldNotAColumnOfTheSameName() {
        // A column named Host holds UserId and an eval changes Host, but where tests the field Host.
        final SearchRequest request = create("""
                from events
                join hr as h on UserId = h.Name
                where Host = 'a' or h.Dept = 'b'
                eval Host = upperCase(Host)
                select UserId as Host, Host""");

        assertThat(filteredFields(request)).containsExactly("Host", "h.Dept");
    }

    @Test
    void conditionOnAQuotedFieldTestsTheField() {
        final SearchRequest request = create("""
                from events
                left join hr on UserId = hr.Username
                where "hr.Department" = 'Sales'
                select UserId""");

        assertThat(filteredFields(request)).containsExactly("hr.Department");
    }

    @Test
    void aliasWithoutJoinsIsTakenOffWhereFields() {
        final Query query = create("from events as e where e.UserId = 'x' select UserId").getQuery();

        assertThat(query.getAlias()).isEqualTo("e");
        // The alias is still in other fields' names, so running it needs join support.
        assertThat(query.needsJoinSupport()).isTrue();
        assertThat(query.getJoins()).isNull();
        assertThat(fields(query.getExpression())).containsExactly("UserId");
    }

    @Test
    void keysCanNameFieldsInBraces() {
        // A default alias with a space can only be written in braces.
        final QueryJoin join = firstJoin("from events join \"hr users\" on UserId = ${hr users.Username} "
                                         + "within 10m after ${Event Time} select UserId");

        assertThat(join.getAlias()).isEqualTo("hr users");
        assertThat(join.getKeys()).containsExactly(new JoinKey("${UserId}", "${hr users.Username}"));
        assertThat(join.getWindow().getTimeField()).isEqualTo("Event Time");
    }

    @Test
    void keyExampleUsesBracesForAnAliasWithASpace() {
        assertThatThrownBy(() -> create("from events join \"hr users\" on UserId = Host select UserId"))
                .isInstanceOf(TokenException.class)
                .hasMessageContaining("${hr users.UserId}");
    }

    @Test
    void keepJoinFieldsCanOnlyBeUsedInItsKeysOrItsOwnSearch() {
        // Pushed into the keep join's search, so it means 'keep rows with a match in Sales'.
        final Query query = create("""
                from events
                keep join hr as h on UserId = h.Name
                where h.Dept = 'Sales'
                select UserId""").getQuery();
        assertThat(fields(query.getJoins().getFirst().getQuery().getExpression())).containsExactly("Dept");

        assertThatThrownBy(() -> create("from events keep join hr as h on UserId = h.Name select UserId, h.Dept"))
                .isInstanceOf(TokenException.class)
                .hasMessageContaining("can only be used in its own keys");
        assertThatThrownBy(() -> create("""
                from events
                keep join hr as h on UserId = h.Name
                where Host = 'a' or h.Dept = 'b'
                select UserId"""))
                .isInstanceOf(TokenException.class)
                .hasMessageContaining("can only be used in its own keys");
    }

    @Test
    void dropJoinFieldsCanOnlyBeUsedInItsKeys() {
        assertThatThrownBy(() -> create("""
                from events
                drop join hr as h on UserId = h.Name
                where h.Dept = 'x'
                select UserId"""))
                .isInstanceOf(TokenException.class)
                .hasMessageContaining("can only be used in its own keys");
        assertThatThrownBy(() -> create("""
                from events
                drop join hr as h on UserId = h.Name
                left join depts as d on h.Dept = d.Name
                select UserId"""))
                .isInstanceOf(TokenException.class)
                .hasMessageContaining("can only be used in its own keys");
        assertThatThrownBy(() -> create("""
                from events
                drop join hr as h on UserId = h.Name
                eval x = h.Dept
                select UserId"""))
                .isInstanceOf(TokenException.class)
                .hasMessageContaining("can only be used in its own keys");
    }

    @Test
    void fieldNamedJoinIsStillAField() {
        final Query query = create("from events where join = 1 eval join = 2 select join").getQuery();

        assertThat(fields(query.getExpression())).containsExactly("join");
        assertThat(query.getJoins()).isNull();
    }

    @Test
    void dataSourceNamedLikeAJoinKindIsStillADataSource() {
        final Query query = create("from Left join hr on UserId = hr.Name select UserId").getQuery();

        assertThat(query.getDataSource().getName()).isEqualTo("Left");
        assertThat(query.getJoins().getFirst().getType()).isEqualTo(JoinType.INNER);
        assertThat(create("from join select a").getQuery().getDataSource().getName()).isEqualTo("join");
    }

    @Test
    void joinLineCanHaveItsOwnSql() {
        final Query query = create("""
                from sql_events sql \"""select * from events\"""
                join sql_hr as hr sql \"""select * from staff\""" on UserId = hr.Username
                select UserId""").getQuery();

        assertThat(query.getNativeQuery()).isEqualTo("select * from events");
        assertThat(query.getJoins().getFirst().getQuery().getNativeQuery()).isEqualTo("select * from staff");
    }

    @Test
    void joinedSourceThatRunsSqlNeedsIt() {
        assertThatThrownBy(() -> create("from events join sql_hr as hr on UserId = hr.Username select UserId"))
                .isInstanceOf(TokenException.class)
                .hasMessageContaining("needs the SQL to run");
        assertThatThrownBy(() -> create("from events join hr sql 'select 1' on UserId = hr.Username select UserId"))
                .isInstanceOf(TokenException.class)
                .hasMessageContaining("A sql clause can only be used");
    }

    @Test
    void sqlAfterAJoinLinesKeysIsOutOfPlace() {
        assertThatThrownBy(() -> create("""
                from sql_events
                join hr on UserId = hr.Username
                sql \"""select 1\"""
                select UserId"""))
                .isInstanceOf(TokenException.class)
                .hasMessageContaining("straight after the data source");
    }

    @Test
    void joinLineNeedsKeys() {
        assertThatThrownBy(() -> create("from events join hr select UserId"))
                .isInstanceOf(TokenException.class)
                .hasMessageContaining("Expected on");
        assertThatThrownBy(() -> create("from events join hr"))
                .isInstanceOf(TokenException.class)
                .hasMessageContaining("Expected on");
        assertThatThrownBy(() -> create("from events join hr on select UserId"))
                .isInstanceOf(TokenException.class)
                .hasMessageContaining("Expected the keys");
    }

    @Test
    void quotedTextInAKeyIsAValueNotAField() {
        assertThatThrownBy(() -> create("from events join hr on UserId = \"hr.Username\" select UserId"))
                .isInstanceOf(TokenException.class)
                .hasMessageContaining("must compare a field of 'hr'");
    }

    @Test
    void keyMustCompareAJoinedFieldWithAnother() {
        assertThatThrownBy(() -> create("from events join hr on UserId = Host select UserId"))
                .isInstanceOf(TokenException.class)
                .hasMessageContaining("must compare a field of 'hr'");
        assertThatThrownBy(() -> create("from events join hr on hr.UserId = hr.Host select UserId"))
                .isInstanceOf(TokenException.class)
                .hasMessageContaining("must compare a field of 'hr'");
        assertThatThrownBy(() -> create("from events join hr on UserId select UserId"))
                .isInstanceOf(TokenException.class)
                .hasMessageContaining("two expressions with =");
        assertThatThrownBy(() -> create("from events join hr on UserId = hr.Name and select UserId"))
                .isInstanceOf(TokenException.class)
                .hasMessageContaining("Expected a key");
    }

    @Test
    void aliasesMustBeDifferent() {
        assertThatThrownBy(() -> create("""
                from events as e
                join hr as e on UserId = e.Username
                select UserId"""))
                .isInstanceOf(TokenException.class)
                .hasMessageContaining("already used");
        assertThatThrownBy(() -> create("""
                from events
                join hr on UserId = hr.Username
                join hr on Host = hr.Host
                select UserId"""))
                .isInstanceOf(TokenException.class)
                .hasMessageContaining("already used");
    }

    @Test
    void bracketedJoinNeedsAnAlias() {
        assertThatThrownBy(() -> create("from events join (from hr select Name) on UserId = Name select UserId"))
                .isInstanceOf(TokenException.class)
                .hasMessageContaining("needs an alias");
    }

    @Test
    void bracketedQueryMustStartWithFrom() {
        assertThatThrownBy(() -> create("from events join (select Name) as h on UserId = h.Name select UserId"))
                .isInstanceOf(TokenException.class)
                .hasMessageContaining("must start with from");
    }

    @Test
    void badWindowIsReported() {
        assertThatThrownBy(() -> create("from events join x on UserId = x.U within 10m select UserId"))
                .isInstanceOf(TokenException.class)
                .hasMessageContaining("Expected a window");
        assertThatThrownBy(() -> create("from events join x on UserId = x.U within ten after T select UserId"))
                .isInstanceOf(TokenException.class)
                .hasMessageContaining("Expected a duration");
        assertThatThrownBy(() -> create("from events join x on UserId = x.U within 10m during T select UserId"))
                .isInstanceOf(TokenException.class)
                .hasMessageContaining("Expected after or before");
    }

    @Test
    void joinMustComeBeforeOtherClauses() {
        assertThatThrownBy(() -> create("from events where a = 1 join hr on a = hr.a select a"))
                .isInstanceOf(TokenException.class)
                .hasMessageContaining("JOIN after WHERE");
    }

    @Test
    void fieldNamedOnIsStillAField() {
        // on is only a keyword in a join line.
        final Query query = create("from events where on = 'x' eval on = 'y' select on").getQuery();

        assertThat(fields(query.getExpression())).containsExactly("on");
    }

    @Test
    void joinedQuerySurvivesJsonRoundTrip() {
        final Query query = create("""
                from events as e
                left join (from hr where Active = true group by Name select Name, count() as N) as h
                  on e.UserId = h.Name within 10m after e.EventTime
                select UserId""").getQuery();

        final Query copy = JsonUtil.readValue(JsonUtil.writeValueAsString(query), Query.class);

        assertThat(copy).isEqualTo(query);
    }

    // The fields the filter tests: a condition on joined rows names a hidden column holding its field.
    private List<String> filteredFields(final SearchRequest request) {
        final TableSettings tableSettings = tableSettings(request);
        return fields(tableSettings.getValueFilter())
                .stream()
                .map(name -> tableSettings.getColumns()
                        .stream()
                        .filter(column -> column.getName().equals(name) && column.getName().startsWith("__join_where"))
                        .map(column -> column.getExpression().replaceAll("^\\$\\{(.*)}$", "$1"))
                        .findFirst()
                        .orElse(name))
                .toList();
    }

    private QueryJoin firstJoin(final String stroomQl) {
        return create(stroomQl).getQuery().getJoins().getFirst();
    }

    private TableSettings tableSettings(final SearchRequest request) {
        return request.getResultRequests().getFirst().getMappings().getFirst();
    }

    private List<String> fields(final ExpressionOperator expression) {
        if (expression == null) {
            return List.of();
        }
        return ExpressionUtil.fields(expression);
    }

    private SearchRequest create(final String stroomQl) {
        final DateTimeSettings dateTimeSettings = DateTimeSettings.builder().referenceTime(0L).build();
        final SearchRequest in = new SearchRequest(
                null,
                new QueryKey("test"),
                Query.builder().build(),
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
}
