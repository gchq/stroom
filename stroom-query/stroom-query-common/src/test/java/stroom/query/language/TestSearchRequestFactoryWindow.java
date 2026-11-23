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
import stroom.query.api.HoppingWindow;
import stroom.query.api.Query;
import stroom.query.api.QueryKey;
import stroom.query.api.ResultRequest;
import stroom.query.api.SearchRequest;
import stroom.query.api.SpecialColumns;
import stroom.query.api.TableSettings;
import stroom.query.api.UserTimeZone;
import stroom.query.api.datasource.QueryField;
import stroom.query.api.token.TokenException;
import stroom.query.common.v2.SearchResultStoreConfig;
import stroom.query.language.functions.ExpressionContext;
import stroom.query.language.functions.FieldIndex;
import stroom.security.mock.MockSecurityContext;
import stroom.util.shared.ResultPage;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/// The `window` clause of StroomQL, as parsed into a [SearchRequest].
///
/// Every data source has a date field `EventTime` and a text field `UserId`.
class TestSearchRequestFactoryWindow {

    private final SearchRequestFactory factory = new SearchRequestFactory(
            (keywordGroup, parentTableSettings) -> null,
            MockDataSourceResolver.getInstance(),
            () -> criteria -> ResultPage.createUnboundedList(List.of(
                    QueryField.createDate("EventTime"),
                    QueryField.createText("UserId"))),
            MockSecurityContext.getInstance());

    @Test
    void tumblingWindow() {
        final TableSettings tableSettings = table("""
                from events
                window EventTime by 1d
                select count() as Events""");

        assertThat(tableSettings.getWindow())
                .isEqualTo(new HoppingWindow("EventTime", "1d", null, null, "Z"));
    }

    @Test
    void hoppingWindowWithAnOffset() {
        assertThat(table("from events window EventTime by 1d advance 1h offset 6h select count() as Events")
                .getWindow())
                .isEqualTo(new HoppingWindow("EventTime", "1d", "1h", "6h", "Z"));
        // The options can be in either order, and an offset can move starts earlier.
        assertThat(table("from events window EventTime by 1d offset -6h advance 1h select count() as Events")
                .getWindow())
                .isEqualTo(new HoppingWindow("EventTime", "1d", "1h", "-6h", "Z"));
        assertThat(table("from events window EventTime by 1w ADVANCE 1d select count() as Events")
                .getWindow())
                .isEqualTo(new HoppingWindow("EventTime", "1w", "1d", null, "Z"));
    }

    @Test
    void windowFollowsTheTimeZoneOfTheQuery() {
        final TableSettings tableSettings = table(
                "from events window EventTime by 1d select count() as Events",
                DateTimeSettings.builder().timeZone(UserTimeZone.fromId("Europe/London")).referenceTime(0L).build());

        assertThat(((HoppingWindow) tableSettings.getWindow()).getTimeZone()).isEqualTo("Europe/London");
    }

    @Test
    void rowsAreGroupedByWindowWithoutItBeingWritten() {
        final TableSettings tableSettings = table("""
                from events
                window EventTime by 1d
                group by UserId
                select UserId, count() as Events""");

        final Column windowColumn = column(tableSettings, SearchRequestFactory.WINDOW_COLUMN_NAME);
        assertThat(windowColumn.getGroup()).isZero();
        assertThat(windowColumn.isVisible()).isFalse();
        assertThat(windowColumn.getExpression()).isEqualTo("windowKey('window:1d|||Z')");
        // A result store that is keyed by time takes a row's time from the start of its window.
        final Column timeColumn = column(tableSettings, FieldIndex.DEFAULT_TIME_FIELD_NAME);
        assertThat(timeColumn.getGroup()).isZero();
        assertThat(timeColumn.isVisible()).isFalse();
        assertThat(timeColumn.getExpression()).isEqualTo("windowStart('window:1d|||Z')");
        // No visible columns are added: the time field is not selected unless the query selects it.
        assertThat(tableSettings.getColumns())
                .filteredOn(Column::isVisible)
                .extracting(Column::getName)
                .containsExactly("UserId", "Events");
    }

    @Test
    void windowWithNoGroupByIsStillGrouped() {
        final TableSettings tableSettings = table("from events window EventTime by 1h select count() as Events");

        // A query with no grouping is given the columns that link a row to its event, but the rows
        // of a window are not events.
        assertThat(tableSettings.getColumns())
                .extracting(Column::getName)
                .doesNotContain(SpecialColumns.RESERVED_STREAM_ID, SpecialColumns.RESERVED_EVENT_ID)
                .contains(SearchRequestFactory.WINDOW_COLUMN_NAME);
    }

    @Test
    void windowFunctionsCarryTheWindowsOfTheirQuery() {
        final TableSettings tableSettings = table("""
                from events
                window EventTime by 1d advance 1h offset -6h
                eval Ends = windowEnd()
                select windowStart() as Starts, Ends, count() as Events""");

        assertThat(column(tableSettings, "Starts").getExpression())
                .isEqualTo("windowStart('window:1d|1h|-6h|Z')");
        assertThat(column(tableSettings, "Ends").getExpression())
                .isEqualTo("windowEnd('window:1d|1h|-6h|Z')");
    }

    @Test
    void windowFunctionInAnEvalBeforeTheWindowClause() {
        final TableSettings tableSettings = table("""
                from events
                eval Day = windowStart()
                window EventTime by 1d
                select Day, count() as Events""");

        assertThat(column(tableSettings, "Day").getExpression()).isEqualTo("windowStart('window:1d|||Z')");
    }

    @Test
    void onlyOneWindowClauseIsAllowed() {
        assertThatThrownBy(() -> table(
                "from events window EventTime by 1d window EventTime by 1h select count() as Events"))
                .isInstanceOf(TokenException.class);
    }

    @Test
    void lagAndLeadCarryTheWindowsOfTheirQuery() {
        final TableSettings tableSettings = table("""
                from events
                window EventTime by 1d
                eval Before = lag(count(), 1)
                group by UserId
                select UserId, count() as Events, Before, lead(count(), 2, 0) as After""");

        assertThat(column(tableSettings, "Before").getExpression())
                .isEqualTo("lag(count(), 1, 'window:1d|||Z')");
        assertThat(column(tableSettings, "After").getExpression())
                .isEqualTo("lead(count(), 2, 0, 'window:1d|||Z')");
    }

    @Test
    void tooManyCopiesOfEachEventIsRefused() {
        // Each event is in 60 windows, which is allowed.
        assertThat(table("from events window EventTime by 1h advance 1m select count() as Events").getWindow())
                .isNotNull();
        // With lag each event is added to every one of them twice.
        assertRefused("from events window EventTime by 1h advance 1m "
                      + "eval Change = count() - lag(count(), 1) select Change",
                "Each event would be copied 120 times");
    }

    @Test
    void mostCopiesOfEachEventIsConfigured() {
        final SearchResultStoreConfig config = new SearchResultStoreConfig(
                null, null, null, null, null, null, null, null, null, null, null, 10);
        final SearchRequestFactory limited = new SearchRequestFactory(
                (keywordGroup, parentTableSettings) -> null,
                MockDataSourceResolver.getInstance(),
                () -> criteria -> ResultPage.createUnboundedList(List.of(QueryField.createDate("EventTime"))),
                MockSecurityContext.getInstance(),
                () -> config);
        final SearchRequest in = new SearchRequest(
                null,
                new QueryKey("test"),
                Query.builder().build(),
                new ArrayList<ResultRequest>(0),
                utc(),
                false);
        final ExpressionContext expressionContext = ExpressionContext
                .builder()
                .dateTimeSettings(utc())
                .maxStringLength(100)
                .build();

        assertThat(limited.create(
                "from events window EventTime by 10m advance 1m select count() as Events", in, expressionContext))
                .isNotNull();
        assertThatThrownBy(() -> limited.create(
                "from events window EventTime by 11m advance 1m select count() as Events", in, expressionContext))
                .isInstanceOf(TokenException.class)
                .hasMessageContaining("Each event would be in 11 windows, and the most allowed is 10");
    }

    @Test
    void lagDefaultCanNotLookLikeTheHiddenWindowParameter() {
        final String message = "A value given to 'lag' can't start with 'window:'";
        assertRefused("""
                from events
                window EventTime by 1d
                group by UserId
                select UserId, lag(max(UserId), 1, 'window:none') as Before""", message);
        // The same value is refused when the windows have been added after it.
        assertRefused("""
                from events
                window EventTime by 1d
                group by UserId
                select UserId, lag(max(UserId), 1, 'window:none', 'window:1d|||Z') as Before""", message);
    }

    @Test
    void lagTakesTheNameOfAnAggregate() {
        final TableSettings tableSettings = table("""
                from events
                window EventTime by 1d
                eval Events = count()
                eval Before = lag(Events, 1)
                group by UserId
                select UserId, Before""");

        assertThat(column(tableSettings, "Before").getExpression())
                .isEqualTo("lag(count(), 1, 'window:1d|||Z')");
    }

    @Test
    void periodIsRefusedWithWhatToWriteInstead() {
        assertRefused("from events window EventTime by 1d select period(1, count()) as Before",
                "The 'period' function has been removed");
    }

    @Test
    void lagNeedsAWindowClause() {
        assertRefused("from events select lag(count(), 1) as Before",
                "'lag' can only be used in a query that has a window clause");
    }

    @Test
    void windowFunctionNeedsAWindowClause() {
        assertThatThrownBy(() -> table("from events select windowStart() as Starts"))
                .isInstanceOf(TokenException.class)
                .hasMessageContaining("'windowStart' can only be used in a query that has a window clause");
    }

    @Test
    void bracketedQueryDoesNotTakeTheWindowOfTheQueryItIsIn() {
        assertRefused("""
                        from (
                          from events
                          select windowStart() as Day, UserId
                        ) as daily
                        window EventTime by 1d
                        group by UserId
                        select UserId, count() as Events""",
                "'windowStart' can only be used in a query that has a window clause");
    }

    @Test
    void windowInABracketedQuery() {
        final SearchRequest searchRequest = create("""
                from (
                  from events
                  window EventTime by 1d
                  group by UserId
                  select windowStart() as Day, UserId, count() as Events
                ) as daily
                select Day, UserId, Events""", utc());

        assertThat(searchRequest.getQuery().needsJoinSupport()).isTrue();
        // The outer query has no window of its own.
        assertThat(searchRequest.getResultRequests().getFirst().getMappings().getFirst().getWindow()).isNull();
    }

    @Test
    void usingIsRefusedWithWhatToWriteInstead() {
        assertThatThrownBy(() -> table("from events window EventTime by 1d using sum(Bytes) select UserId"))
                .isInstanceOf(TokenException.class)
                .hasMessageContaining("no longer takes 'using'")
                .hasMessageContaining("lag(count(), 1)");
    }

    @Test
    void badClausesAreReported() {
        assertRefused("from events window select UserId", "Expected the time field of the window");
        assertRefused("from events window EventTime select UserId", "Expected 'by' and the size of the window");
        assertRefused("from events window EventTime by select UserId", "Expected the size of the window");
        assertRefused("from events window EventTime by soon select UserId", "as a duration such as 1d or 30m");
        assertRefused("from events window EventTime by 1d advance select UserId",
                "Expected how far the window advances");
        assertRefused("from events window EventTime by 1d offset - select UserId",
                "Expected the offset of the window");
        assertRefused("from events window EventTime by 1d every 1h select UserId", "Expected 'advance' or 'offset'");
        assertRefused("from events window EventTime by 1d advance 1h advance 2h select UserId",
                "already has an advance");
        assertRefused("from events window EventTime by 1d offset 1h offset 2h select UserId",
                "already has an offset");
    }

    @Test
    void sizesThatCantBeUsedTogetherAreReported() {
        assertRefused("from events window EventTime by 1h advance 25m select UserId",
                "1h is not a multiple of 25m");
        assertRefused("from events window EventTime by 1M advance 1d select UserId",
                "must be in months or years");
        // The old window code never finished working out the windows of a zero advance.
        assertRefused("from events window EventTime by 1d advance 0m select UserId",
                "advance must be more than zero");
    }

    @Test
    void tooManyWindowsPerEventIsRefused() {
        assertThat(table("from events window EventTime by 100m advance 1m select count() as Events").getWindow())
                .isNotNull();
        assertRefused("from events window EventTime by 1d advance 1m select UserId",
                "Each event would be in 1440 windows, and the most allowed is 100");
    }

    @Test
    void windowFieldThatIsNotADateIsRefused() {
        assertRefused("from events window UserId by 1d select UserId",
                "The window field 'UserId' is not a date field");
        // A field the data source doesn't list may come from an extraction pipeline, so is allowed.
        assertThat(table("from events window Logged by 1d select count() as Events").getWindow()).isNotNull();
    }

    @Test
    void windowIsRefusedWithAnEventToEventJoin() {
        assertRefused("""
                from logons as logon
                join exports as export
                  on logon.UserId = export.UserId
                  within 10m after logon.EventTime
                window logon.EventTime by 1d
                select logon.UserId""", "can't be used in a query that has a join with a time window");
    }

    @Test
    void windowIsAllowedWithAnOrdinaryJoin() {
        assertThat(table("""
                from events
                join hr_users as hr on UserId = hr.Username
                window EventTime by 1d
                group by hr.Department
                select hr.Department, count() as Events""").getWindow()).isNotNull();
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
