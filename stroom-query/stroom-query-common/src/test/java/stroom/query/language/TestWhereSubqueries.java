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

import stroom.query.api.DateTimeSettings;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionUtil;
import stroom.query.api.JoinKey;
import stroom.query.api.JoinType;
import stroom.query.api.Query;
import stroom.query.api.QueryJoin;
import stroom.query.api.QueryKey;
import stroom.query.api.ResultRequest;
import stroom.query.api.SearchRequest;
import stroom.query.api.datasource.QueryField;
import stroom.query.api.token.TokenException;
import stroom.query.language.functions.ExpressionContext;
import stroom.security.mock.MockSecurityContext;
import stroom.util.shared.ResultPage;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/// The SQL forms of semi and anti joins in StroomQL, `where exists`, `where not exists`,
/// `where X in (from ...)` and `where X not in (from ...)`, which are parsed into the join lines they
/// stand for.
class TestWhereSubqueries {

    private final SearchRequestFactory factory = new SearchRequestFactory(
            (keywordGroup, parentTableSettings) -> null,
            MockDataSourceResolver.getInstance(),
            () -> criteria -> ResultPage.createUnboundedList(List.of(QueryField.createText("name"))),
            MockSecurityContext.getInstance());

    @Test
    void existsIsASemiJoin() {
        assertSameRequest(
                "from People as p where exists (from Teams as t where t.team = p.team) select name",
                "from People as p semi join Teams as t on p.team = t.team select name");
    }

    @Test
    void notExistsIsAnAntiJoin() {
        assertSameRequest(
                "from People as p where not exists (from Teams as t where t.team = p.team) select name",
                "from People as p anti join Teams as t on p.team = t.team select name");
    }

    @Test
    void inIsASemiJoin() {
        assertSameRequest(
                "from People where team in (from Teams select team) select name",
                "from People semi join Teams on team = Teams.team select name");
    }

    @Test
    void notInIsAnAntiJoin() {
        assertSameRequest(
                "from People where team not in (from Teams select team) select name",
                "from People anti join Teams on team = Teams.team select name");
    }

    @Test
    void existsWrittenAsAFunctionIsTheSame() {
        assertSameRequest(
                "from People as p where exists(from Teams as t where t.team = p.team) select name",
                "from People as p semi join Teams as t on p.team = t.team select name");
        assertSameRequest(
                "from People as p where not exists(from Teams as t where t.team = p.team) select name",
                "from People as p anti join Teams as t on p.team = t.team select name");
        assertSameRequest(
                "from People where team in(from Teams select team) select name",
                "from People semi join Teams on team = Teams.team select name");
    }

    @Test
    void keywordsAndFieldsMayBeInAnyCaseAndEitherOrder() {
        assertSameRequest(
                "from People as p WHERE EXISTS (FROM Teams as t WHERE p.team = t.team) select name",
                "from People as p semi join Teams as t on p.team = t.team select name");
    }

    @Test
    void firstSourceWithNoAliasIsNamedByItsDataSource() {
        assertSameRequest(
                "from People where exists (from Teams where team = People.team) select name",
                "from People semi join Teams on team = Teams.team select name");
    }

    @Test
    void eachComparisonIsAKey() {
        final QueryJoin join = firstJoin("""
                from People as p
                where exists (from Teams as t where t.team = p.team and t.site = p.site)
                select name""");

        assertThat(join.getKeys()).containsExactly(
                new JoinKey("${p.team}", "${t.team}"),
                new JoinKey("${p.site}", "${t.site}"));
    }

    @Test
    void inWithAComparisonHasBothKeys() {
        final QueryJoin join = firstJoin("""
                from People as p
                where team in (from Teams as t where t.site = p.site select team)
                select name""");

        assertThat(join.getType()).isEqualTo(JoinType.SEMI);
        assertThat(join.getKeys()).containsExactly(
                new JoinKey("${team}", "${t.team}"),
                new JoinKey("${p.site}", "${t.site}"));
    }

    @Test
    void otherConditionsGoIntoTheJoinedSourcesSearch() {
        // The same as moving the condition out of where, which a semi join allows.
        assertSameRequest(
                "from People as p where exists (from Teams as t where t.team = p.team and on_call = true) "
                + "select name",
                "from People as p semi join Teams as t on p.team = t.team where t.on_call = true select name");

        // An anti join gives the rows with no match among the joined rows that pass the condition.
        final SearchRequest request = create("""
                from People as p
                where not exists (from Teams as t where t.team = p.team and t.on_call = true)
                select name""");
        final QueryJoin join = request.getQuery().getJoins().getFirst();
        assertThat(join.getType()).isEqualTo(JoinType.ANTI);
        assertThat(ExpressionUtil.fields(join.getQuery().getExpression())).containsExactly("on_call");
        assertThat(fields(request.getQuery().getExpression())).isEmpty();
        assertThat(fields(tableSettingsFilter(request))).isEmpty();
    }

    @Test
    void theRestOfWhereStays() {
        final SearchRequest request = create("""
                from People as p
                where age > 30 and exists (from Teams as t where t.team = p.team) and name = 'Ann'
                select name""");

        assertThat(fields(request.getQuery().getExpression())).containsExactly("age", "name");
        assertThat(request.getQuery().getJoins()).hasSize(1);
    }

    @Test
    void andAfterABracketedQueryIsStillAnd() {
        // The select in the brackets doesn't end the where clause around them.
        final SearchRequest request = create("""
                from People
                where team in (from Teams select team) and name = 'Ann'
                select name""");

        assertThat(fields(request.getQuery().getExpression())).containsExactly("name");
        assertThat(request.getQuery().getJoins()).hasSize(1);
    }

    @Test
    void formsComeAfterJoinLines() {
        final List<QueryJoin> joins = create("""
                from People as p
                left join Sites as s on p.site = s.site
                where not exists (from Leavers as l where l.name = p.name)
                select name, s.city""").getQuery().getJoins();

        assertThat(joins).extracting(QueryJoin::getType).containsExactly(JoinType.LEFT, JoinType.ANTI);
        assertThat(joins).extracting(QueryJoin::getAlias).containsExactly("s", "l");
    }

    @Test
    void severalFormsAreSeveralJoins() {
        final List<QueryJoin> joins = create("""
                from People as p
                where exists (from Teams as t where t.team = p.team)
                and site not in (from Closed select site)
                select name""").getQuery().getJoins();

        assertThat(joins).extracting(QueryJoin::getType).containsExactly(JoinType.SEMI, JoinType.ANTI);
    }

    @Test
    void sqlInTheBracketsIsTheJoinedSourcesSql() {
        final QueryJoin join = firstJoin("""
                from People as p
                where exists (from sql_hr as h sql 'select * from staff' where h.name = p.name)
                select name""");

        assertThat(join.getQuery().getNativeQuery()).isEqualTo("select * from staff");
    }

    @Test
    void formsBesideOrAreRefused() {
        assertThatThrownBy(() -> create("""
                from People as p where a = 1 or exists (from Teams as t where t.team = p.team)"""))
                .isInstanceOf(TokenException.class)
                .hasMessageContaining("top level of where");
        // 'and' comes before 'or', so this exists only applies to the rows where b = 2.
        assertThatThrownBy(() -> create("""
                from People as p where a = 1 or b = 2 and exists (from Teams as t where t.team = p.team)"""))
                .isInstanceOf(TokenException.class)
                .hasMessageContaining("beside or");
    }

    @Test
    void formsInsideBracketsOrNotAreRefused() {
        assertThatThrownBy(() -> create("""
                from People as p where not (exists (from Teams as t where t.team = p.team))"""))
                .isInstanceOf(TokenException.class)
                .hasMessageContaining("top level of where");
        assertThatThrownBy(() -> create("""
                from People as p where (a = 1 and team in (from Teams select team))"""))
                .isInstanceOf(TokenException.class)
                .hasMessageContaining("top level of where");
    }

    @Test
    void formsOutsideWhereAreRefused() {
        assertThatThrownBy(() -> create("from People filter team in (from Teams select team) select team"))
                .isInstanceOf(TokenException.class)
                .hasMessageContaining("top level of where");
        assertThatThrownBy(() -> create("""
                from People group by team having team in (from Teams select team) select team"""))
                .isInstanceOf(TokenException.class)
                .hasMessageContaining("top level of where");
    }

    @Test
    void inMustSelectOneField() {
        assertThatThrownBy(() -> create("from People where team in (from Teams select team, site)"))
                .isInstanceOf(TokenException.class)
                .hasMessageContaining("select exactly one field");
        assertThatThrownBy(() -> create("from People where team in (from Teams)"))
                .isInstanceOf(TokenException.class)
                .hasMessageContaining("select exactly one field");
        assertThatThrownBy(() -> create("from People where team in (from Teams select upperCase(team))"))
                .isInstanceOf(TokenException.class)
                .hasMessageContaining("select exactly one field");
    }

    @Test
    void comparisonsWithTheFirstSourceMustBeEquality() {
        assertThatThrownBy(() -> create("""
                from People as p where exists (from Teams as t where t.size > p.size)"""))
                .isInstanceOf(TokenException.class)
                .hasMessageContaining("can only be compared with =");
        assertThatThrownBy(() -> create("""
                from People as p where exists (from Teams as t where t.team = p.team or t.site = p.site)"""))
                .isInstanceOf(TokenException.class)
                .hasMessageContaining("can only be compared with =");
        assertThatThrownBy(() -> create("""
                from People as p where exists (from Teams as t where p.team = p.site)"""))
                .isInstanceOf(TokenException.class)
                .hasMessageContaining("can only be compared with =");
    }

    @Test
    void orBesideTheComparisonsIsRefused() {
        // This means 'on_call or (lead and the key)', which can't be split into a key and a search.
        assertThatThrownBy(() -> create("""
                from People as p
                where exists (from Teams as t where t.on_call = true or t.lead = true and t.team = p.team)"""))
                .isInstanceOf(TokenException.class)
                .hasMessageContaining("can't have or at the top level of its where");
        // In brackets it is one condition of its own, which goes into the search whole.
        final QueryJoin join = firstJoin("""
                from People as p
                where exists (from Teams as t where (t.on_call = true or t.lead = true) and t.team = p.team)""");
        assertThat(join.getQuery().getExpression().getOp()).isEqualTo(ExpressionOperator.Op.OR);
        // With nothing taken out, or stays as it was.
        final QueryJoin inJoin = firstJoin("""
                from People where team in (from Teams where on_call = true or lead = true select team)""");
        assertThat(inJoin.getQuery().getExpression().getOp()).isEqualTo(ExpressionOperator.Op.OR);
    }

    @Test
    void fieldBeforeInMayBeNamedByTheDataSource() {
        assertSameRequest(
                "from People where People.team in (from Teams select team) select name",
                "from People where team in (from Teams select team) select name");
    }

    @Test
    void onlyAFieldCanComeBeforeIn() {
        assertThatThrownBy(() -> create("from People where upperCase(team) in (from Teams select team)"))
                .isInstanceOf(TokenException.class)
                .hasMessageContaining("Only a field of the first source can come before in");
        assertThatThrownBy(() -> create("from People where 'x' in (from Teams select team)"))
                .isInstanceOf(TokenException.class)
                .hasMessageContaining("Only a field of the first source can come before in");
    }

    @Test
    void aClauseCanOnlyBeGivenOnce() {
        assertThatThrownBy(() -> create("""
                from People as p
                where exists (from Teams as t where t.team = p.team where t.active = true)"""))
                .isInstanceOf(TokenException.class)
                .hasMessageContaining("can only have one where clause");
    }

    @Test
    void existsMustCompareWithTheFirstSource() {
        assertThatThrownBy(() -> create("from People as p where exists (from Teams as t where t.on_call = true)"))
                .isInstanceOf(TokenException.class)
                .hasMessageContaining("must compare a field of 't' with a field of 'p'");
    }

    @Test
    void otherClausesInTheBracketsAreRefused() {
        assertThatThrownBy(() -> create("""
                from People as p
                where exists (from Teams as t where t.team = p.team group by team select team)"""))
                .isInstanceOf(TokenException.class)
                .hasMessageContaining("can only have from, sql, where and select");
        assertThatThrownBy(() -> create("""
                from People as p
                where exists (from (from Teams select team) as t where t.team = p.team)"""))
                .isInstanceOf(TokenException.class)
                .hasMessageContaining("can only have from, sql, where and select");
    }

    @Test
    void formsInsideABracketedQueryAreRefused() {
        assertThatThrownBy(() -> create("""
                from People as p
                semi join (
                  from Teams as t where exists (from Sites as s where s.site = t.site) select team
                ) as x on p.team = x.team"""))
                .isInstanceOf(TokenException.class)
                .hasMessageContaining("inside a bracketed query");
    }

    @Test
    void anAliasCanOnlyBeUsedOnce() {
        assertThatThrownBy(() -> create("from People where exists (from People where name = People.name)"))
                .isInstanceOf(TokenException.class)
                .hasMessageContaining("The alias 'People' is already used");
    }

    @Test
    void existsWithoutABracketedQueryIsNotAForm() {
        // 'exists' on its own is an ordinary word, so it is refused as a where condition, not read
        // as a form with no query.
        assertThatThrownBy(() -> create("from People as p where exists and name = 'a'"))
                .isInstanceOf(TokenException.class);
        assertThatThrownBy(() -> create("from People as p where name = 'a' and not exists"))
                .isInstanceOf(TokenException.class);
    }

    private void assertSameRequest(final String form, final String joinLine) {
        assertThat(create(form)).isEqualTo(create(joinLine));
    }

    private QueryJoin firstJoin(final String stroomQl) {
        return create(stroomQl).getQuery().getJoins().getFirst();
    }

    private ExpressionOperator tableSettingsFilter(final SearchRequest request) {
        return request.getResultRequests().getFirst().getMappings().getFirst().getValueFilter();
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
