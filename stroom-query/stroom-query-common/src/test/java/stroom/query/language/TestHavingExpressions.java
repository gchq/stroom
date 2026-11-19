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
import stroom.query.api.Query;
import stroom.query.api.QueryKey;
import stroom.query.api.ResultRequest;
import stroom.query.api.SearchRequest;
import stroom.query.api.TableSettings;
import stroom.query.api.UserTimeZone;
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

/// Expressions in the `having` clause of StroomQL, as parsed into a [SearchRequest].
///
/// Every data source has a date field `EventTime` and a text field `UserId`.
class TestHavingExpressions {

    private final SearchRequestFactory factory = new SearchRequestFactory(
            (keywordGroup, parentTableSettings) -> null,
            MockDataSourceResolver.getInstance(),
            () -> criteria -> ResultPage.createUnboundedList(List.of(
                    QueryField.createDate("EventTime"),
                    QueryField.createText("UserId"))),
            MockSecurityContext.getInstance());

    @Test
    void havingTakesAnExpression() {
        final TableSettings tableSettings = table("""
                from events
                window EventTime by 1d
                group by UserId
                having count() - lag(count(), 1) > 5 and UserId != 'admin'
                select UserId, count() as Events""");

        // The expression gets a hidden column, and the condition tests it.
        final Column hidden = column(tableSettings, "having_1");
        assertThat(hidden.getExpression()).isEqualTo("count()-lag(count(), 1, 'window:1d|||Z')");
        assertThat(hidden.isVisible()).isFalse();
        assertThat(tableSettings.getAggregateFilter().toString())
                .contains("having_1 > 5")
                .contains("UserId != admin");
        assertThat(tableSettings.getColumns())
                .filteredOn(Column::isVisible)
                .extracting(Column::getName)
                .containsExactly("UserId", "Events");
    }

    @Test
    void havingComparesTwoExpressions() {
        final TableSettings tableSettings = table("""
                from events
                window EventTime by 1d
                group by UserId
                having count() > lag(count(), 1) * 2
                select UserId, count() as Events""");

        // An expression on the right makes the whole comparison a column that is true or false.
        assertThat(column(tableSettings, "having_1").getExpression())
                .isEqualTo("count()>lag(count(), 1, 'window:1d|||Z')*2");
        assertThat(tableSettings.getAggregateFilter().toString()).contains("having_1 = true");
    }

    @Test
    void havingStillTakesColumnNamesAndValues() {
        final TableSettings tableSettings = table("""
                from events
                eval Events = count()
                group by UserId
                having Events > 3 and Events < param('Most')
                select UserId, Events""");

        assertThat(tableSettings.getColumns())
                .extracting(Column::getName)
                .noneMatch(name -> name.startsWith("having_"));
    }

    @Test
    void havingExpressionWithNoWindowClause() {
        final TableSettings tableSettings = table("""
                from events
                group by UserId
                having count() > 3
                select UserId""");

        assertThat(column(tableSettings, "having_1").getExpression()).isEqualTo("count()");
        assertThat(tableSettings.getAggregateFilter().toString()).contains("having_1 > 3");
    }

    @Test
    void badHavingExpressionIsReported() {
        assertRefused("from events group by UserId having nosuch(1) > 3 select UserId", "Unknown function");
    }

    @Test
    void havingExpressionUsesAColumnOfTheSelectClause() {
        final TableSettings tableSettings = table("""
                from events
                window EventTime by 1d
                group by UserId
                having Events > lag(Events, 1)
                select UserId, count() as Events""");

        // Events is the column of the select clause, and not a field of the data source.
        assertThat(column(tableSettings, "having_1").getExpression())
                .isEqualTo("count()>lag(count(), 1, 'window:1d|||Z')");
    }

    @Test
    void havingComparesWithSumsOnNames() {
        final TableSettings tableSettings = table("""
                from events
                eval Total = count()
                eval Most = max(Bytes)
                group by UserId
                having Most > Total / 2
                select UserId""");

        assertThat(column(tableSettings, "having_1").getExpression()).isEqualTo("max(${Bytes})>count()/2");
        assertThat(tableSettings.getAggregateFilter().toString()).contains("having_1 = true");
    }

    @Test
    void aNameOnTheRightIsAColumnIfThereIsOne() {
        final TableSettings tableSettings = table("""
                from events
                window EventTime by 1d
                eval Before = lag(count(), 1)
                group by UserId
                having count() > Before and Events >= Most and UserId != admin
                select UserId, count() as Events, max(Bytes) as Most""");

        // Before is an eval, and Most is a column of the select clause.
        assertThat(column(tableSettings, "having_1").getExpression())
                .isEqualTo("count()>lag(count(), 1, 'window:1d|||Z')");
        assertThat(column(tableSettings, "having_2").getExpression()).isEqualTo("count()>=max(${Bytes})");
        // There is no column called admin, so it is text.
        assertThat(tableSettings.getAggregateFilter().toString())
                .contains("having_1 = true")
                .contains("having_2 = true")
                .contains("UserId != admin");
    }

    @Test
    void aQuotedNameOnTheRightIsText() {
        final TableSettings tableSettings = table("""
                from events
                group by UserId
                having UserId = 'Events'
                select UserId, count() as Events""");

        assertThat(tableSettings.getAggregateFilter().toString()).contains("UserId = Events");
        assertThat(tableSettings.getColumns())
                .extracting(Column::getName)
                .noneMatch(name -> name.startsWith(HavingExpressions.NAME_PREFIX));
    }

    @Test
    void havingExpressionCanStartWithABracket() {
        final TableSettings tableSettings = table("""
                from events
                group by UserId
                having (count() - 1) * 2 > 5 and (count() < 100 or UserId = 'admin')
                select UserId""");

        assertThat(column(tableSettings, "having_1").getExpression()).isEqualTo("(count()-1)*2");
        // Brackets around conditions still group them.
        assertThat(column(tableSettings, "having_2").getExpression()).isEqualTo("count()");
        assertThat(tableSettings.getAggregateFilter().toString())
                .contains("having_1 > 5")
                .contains("having_2 < 100")
                .contains("UserId = admin");
    }

    @Test
    void anExpressionWrittenTwiceGetsOneColumn() {
        final TableSettings tableSettings = table("""
                from events
                group by UserId
                having count() > 3 and count() < 10
                select UserId""");

        assertThat(tableSettings.getColumns())
                .extracting(Column::getName)
                .filteredOn(name -> name.startsWith(HavingExpressions.NAME_PREFIX))
                .containsExactly("having_1");
        assertThat(tableSettings.getAggregateFilter().toString())
                .contains("having_1 > 3")
                .contains("having_1 < 10");
    }

    @Test
    void havingTakesInAndBetweenWithAnExpression() {
        final TableSettings tableSettings = table("""
                from events
                group by UserId
                having count() + 1 between 2 and 5 or count() in (7, 9)
                select UserId""");

        assertThat(column(tableSettings, "having_1").getExpression()).isEqualTo("count()+1");
        assertThat(column(tableSettings, "having_2").getExpression()).isEqualTo("count()");
        assertThat(tableSettings.getAggregateFilter().toString())
                .contains("having_1 between")
                .contains("having_2 in");
    }

    @Test
    void anEvalWithTheNameOfAHavingExpressionIsStepped() {
        final TableSettings tableSettings = table("""
                from events
                eval having_1 = max(Bytes)
                group by UserId
                having count() > 3
                select UserId, having_1""");

        assertThat(column(tableSettings, "having_1").getExpression()).isEqualTo("max(${Bytes})");
        assertThat(column(tableSettings, "having_2").getExpression()).isEqualTo("count()");
        assertThat(tableSettings.getAggregateFilter().toString()).contains("having_2 > 3");
    }

    @Test
    void aColumnWithTheNameOfAHavingExpressionIsRefused() {
        assertRefused(
                "from events group by UserId having count() > 3 select UserId, max(Bytes) as having_1",
                "The having clause uses the name 'having_1'");
    }

    @Test
    void anExpressionWithNoValueIsRefused() {
        assertRefused("from events group by UserId having count() + 1 > select UserId", "Incomplete term");
        assertRefused("from events group by UserId having count() + 1 in select UserId", "Incomplete term");
    }

    private void assertRefused(final String stroomQl, final String message) {
        assertThatThrownBy(() -> table(stroomQl))
                .as(stroomQl)
                .isInstanceOf(TokenException.class)
                .hasMessageContaining(message);
    }

    private Column column(final TableSettings tableSettings, final String name) {
        return tableSettings
                .getColumns()
                .stream()
                .filter(column -> name.equals(column.getName()))
                .findFirst()
                .orElseThrow();
    }

    private TableSettings table(final String stroomQl) {
        return table(stroomQl, utc());
    }

    private TableSettings table(final String stroomQl, final DateTimeSettings dateTimeSettings) {
        return create(stroomQl, dateTimeSettings).getResultRequests().getFirst().getMappings().getFirst();
    }

    private static DateTimeSettings utc() {
        return DateTimeSettings.builder().timeZone(UserTimeZone.utc()).referenceTime(0L).build();
    }

    private SearchRequest create(final String stroomQl, final DateTimeSettings dateTimeSettings) {
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
