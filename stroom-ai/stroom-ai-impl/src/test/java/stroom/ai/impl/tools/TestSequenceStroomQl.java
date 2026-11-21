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

package stroom.ai.impl.tools;

import stroom.ai.impl.tools.EqlQueryWriter.Filter;
import stroom.ai.impl.tools.EqlQueryWriter.Step;
import stroom.ai.impl.tools.SequenceStroomQl.Side;
import stroom.ai.impl.workspace.WorkspaceException;
import stroom.docref.DocRef;
import stroom.index.shared.LuceneIndexDoc;
import stroom.query.api.Column;
import stroom.query.api.DateTimeSettings;
import stroom.query.api.ExpressionItem;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionTerm;
import stroom.query.api.ExpressionTerm.Condition;
import stroom.query.api.Query;
import stroom.query.api.QueryKey;
import stroom.query.api.ResultRequest;
import stroom.query.api.SearchRequest;
import stroom.query.api.TimeRange;
import stroom.query.language.DataSourceResolver;
import stroom.query.language.SearchRequestFactory;
import stroom.query.language.functions.ExpressionContext;
import stroom.security.api.SecurityContext;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/// The StroomQL [SequenceStroomQl] writes, parsed by Stroom's own parser as the query service parses it, so a
/// sequence run in Stroom, and the query the user can save, is always valid StroomQL.
class TestSequenceStroomQl {

    private static final DocRef EVENTS = new DocRef(LuceneIndexDoc.TYPE, "events-uuid", "Events");
    private static final DocRef FILES = new DocRef(LuceneIndexDoc.TYPE, "files-uuid", "File events");

    private SearchRequestFactory factory;

    @BeforeEach
    void setUp() {
        final DataSourceResolver dataSourceResolver = Mockito.mock(DataSourceResolver.class);
        Mockito.when(dataSourceResolver.resolveDataSourceRef(Mockito.anyString()))
                .thenAnswer(invocation -> new DocRef(LuceneIndexDoc.TYPE, "uuid-" + invocation.getArgument(0),
                        invocation.getArgument(0)));
        final SecurityContext securityContext = Mockito.mock(SecurityContext.class);
        Mockito.when(securityContext.useAsReadResult(Mockito.any())).thenAnswer(invocation ->
                invocation.<Supplier<?>>getArgument(0).get());
        factory = new SearchRequestFactory((keywordGroup, parentTableSettings) -> null, dataSourceResolver,
                () -> criteria -> null, securityContext);
    }

    @Test
    void twoStepsOnOneIndexAreAWindowedJoinOfItWithItself() {
        final String query = SequenceStroomQl.join(side(EVENTS, step(List.of(equalsTo("Tier", "A100")))),
                side(EVENTS, step(List.of(equalsTo("Action", "Export")))), Duration.ofMinutes(5), null, 1_000);

        assertThat(query).isEqualTo("""
                from "Events" as a
                join "Events" as e on a.UserId = e.UserId within 5m after a.EventTime
                where a.Tier = "A100"
                  and e.Action = "Export"
                limit 1000
                select a.UserId as "UserId", a.EventTime as "Step 1 time", a.StreamId as "Step 1 StreamId", \
                a.EventId as "Step 1 EventId", e.EventTime as "Step 2 time", e.StreamId as "Step 2 StreamId", \
                e.EventId as "Step 2 EventId\"""");
        final SearchRequest parsed = parse(query);
        assertThat(parsed.getQuery().needsJoinSupport()).isTrue();
        assertThat(columnNames(parsed)).containsSubsequence("UserId", "Step 1 time", "Step 1 StreamId",
                "Step 1 EventId", "Step 2 time", "Step 2 StreamId", "Step 2 EventId");
        assertThat(SequenceStroomQl.columnNames(side(EVENTS, step(List.of())), side(EVENTS, step(List.of()))))
                .containsExactly("UserId", "Step 1 time", "Step 1 StreamId", "Step 1 EventId", "Step 2 time",
                        "Step 2 StreamId", "Step 2 EventId");
    }

    @Test
    void eachConditionIsWrittenAsStroomQlTakesIt() {
        final Step first = new Step(1, List.of(
                new Filter("Action", Condition.CONTAINS, "copy", false),
                new Filter("Host", Condition.STARTS_WITH, "srv", false),
                new Filter("Image", Condition.ENDS_WITH, ".exe", false),
                new Filter("Bytes", Condition.BETWEEN, "10,20", false),
                new Filter("Port", Condition.GREATER_THAN_OR_EQUAL_TO, "1024", false),
                new Filter("User", Condition.NOT_EQUALS, "system", false),
                new Filter("Feed", Condition.IN, "a, b", false),
                new Filter("Level", Condition.IN, "debug,trace", false, true, false)),
                List.of(new Filter("Kind", Condition.EQUALS, "x", false), new Filter("Kind", Condition.EQUALS, "y",
                        false)),
                List.of("UserId"), false, 1, List.of("Action"), Map.of());
        final String query = SequenceStroomQl.join(side(EVENTS, first), side(FILES, step(List.of(
                equalsTo("Action", "Export")))), Duration.ofSeconds(90), new TimeRange(null,
                "2026-01-01T00:00:00.000Z", "day() - 1d"), 50);

        assertThat(query).contains("""
                where a.Action = "*copy*"
                  and a.Host = "srv*"
                  and a.Image = "*.exe"
                  and a.Bytes between "10" and "20"
                  and a.Port >= "1024"
                  and a.User != "system"
                  and a.Feed in ("a", "b")
                  and not (a.Level in ("debug", "trace"))
                  and (a.Kind = "x" or a.Kind = "y")
                  and e.Action = "Export"
                  and a.EventTime >= "2026-01-01T00:00:00.000Z"
                  and a.EventTime < day() - 1d
                  and e.EventTime >= "2026-01-01T00:00:00.000Z"
                  and e.EventTime < day() - 1d""")
                .contains("join \"File events\" as e on a.UserId = e.UserId within 90s after a.EventTime")
                .contains("a.Action as \"Step 1 Action\"");
        final SearchRequest parsed = parse(query);
        // The first source's conditions are its own search's; the window and the joined source's go to the join.
        final List<ExpressionTerm> terms = terms(parsed.getQuery().getExpression());
        assertThat(terms).extracting(ExpressionTerm::getValue)
                .contains("*copy*", "srv*", "10, 20", "1024", "2026-01-01T00:00:00.000Z", "day()-1d");
        assertThat(terms).filteredOn(term -> term.getCondition() == Condition.BETWEEN).singleElement()
                .extracting(ExpressionTerm::getField).isEqualTo("Bytes");
    }

    @Test
    void isoTimesAreQuotedAndDateExpressionsAreNot() {
        // Seen in eval case 73: an ISO time without milliseconds, unquoted, failed in Stroom with "Unexpected token".
        final List<String> isoTimes = List.of("2026-01-05T00:00:00Z", "2026-01-05T00:00:00.000Z",
                "2026-01-05T00:00:00+11:00", "2026-01-05T00:00:00.123+11:00", "2026-01-05T00:00Z", "2026-01-05");
        for (final String isoTime : isoTimes) {
            final String query = SequenceStroomQl.anchorCount(side(EVENTS, step(List.of(equalsTo("Action",
                    "Logon")))), new TimeRange(null, isoTime, isoTime));

            assertThat(query).contains("EventTime >= \"" + isoTime + "\"")
                    .contains("EventTime < \"" + isoTime + "\"");
            assertThat(terms(parse(query).getQuery().getExpression())).extracting(ExpressionTerm::getValue)
                    .contains(isoTime);
        }
        final List<String> expressions = List.of("day() + 2h", "now() - 7d", "day()");
        for (final String expression : expressions) {
            final String query = SequenceStroomQl.join(side(EVENTS, step(List.of())), side(FILES, step(List.of())),
                    Duration.ofMinutes(5), new TimeRange(null, expression, expression), 10);

            assertThat(query).contains("a.EventTime >= " + expression)
                    .contains("e.EventTime < " + expression);
            parse(query);
        }
    }

    @Test
    void theEndOfTheTimeRangeIsExclusive() {
        final String query = SequenceStroomQl.join(side(EVENTS, step(List.of())), side(FILES, step(List.of())),
                Duration.ofMinutes(5), new TimeRange(null, "2026-01-05T00:00:00Z", "2026-01-12T00:00:00Z"), 10);

        assertThat(query).contains("a.EventTime < \"2026-01-12T00:00:00Z\"")
                .contains("e.EventTime < \"2026-01-12T00:00:00Z\"")
                .doesNotContain("<=");
        assertThat(terms(parse(query).getQuery().getExpression()))
                .filteredOn(term -> "2026-01-12T00:00:00Z".equals(term.getValue()))
                .extracting(ExpressionTerm::getCondition)
                .containsOnly(Condition.LESS_THAN);
    }

    @Test
    void namesThatNeedBracesGetThem() {
        final Step first = new Step(1, List.of(equalsTo("Event Type", "Logon")), List.of("User Name"), false, 1,
                List.of(), Map.of());
        final Step second = new Step(1, List.of(equalsTo("Action", "Export")), List.of("User Name"), false, 1,
                List.of(), Map.of());
        final String query = SequenceStroomQl.join(new Side(EVENTS, "Event Time", first, false),
                new Side(EVENTS, "Event Time", second, false), Duration.ofMinutes(10), null, 10);

        assertThat(query).contains("on ${a.User Name} = ${e.User Name} within 10m after ${a.Event Time}")
                .contains("where ${a.Event Type} = \"Logon\"")
                .doesNotContain("StreamId");
        parse(query);
    }

    @Test
    void severalJoinFieldsAreAllKeysAndTheyMayHaveOtherNamesInTheSecondStep() {
        final Step first = new Step(1, List.of(equalsTo("action", "copy")), List.of("user", "host"), false, 1,
                List.of(), Map.of());
        final Step second = new Step(2, List.of(equalsTo("Image", "pwsh")), List.of("SubjectUserName", "Computer"),
                false, 1, List.of(), Map.of());
        final String query = SequenceStroomQl.join(new Side(EVENTS, "EventTime", first, true),
                new Side(FILES, "EventTime", second, true), Duration.ofMinutes(2), null, 10);

        assertThat(query).contains("on a.user = e.SubjectUserName and a.host = e.Computer within 2m");
        parse(query);
    }

    @Test
    void theFirstStepsCountIsOneGroupWithItsEventsAndDistinctJoinValues() {
        final String one = SequenceStroomQl.anchorCount(side(EVENTS, step(List.of(equalsTo("Tier", "A100")))),
                new TimeRange(null, "2026-01-01T00:00:00.000Z", null));

        assertThat(one).isEqualTo("""
                from "Events"
                where Tier = "A100"
                  and EventTime >= "2026-01-01T00:00:00.000Z"
                eval Sequence = 'all'
                group by Sequence
                select Sequence, count() as Anchors, countUnique(UserId) as Keys""");
        final SearchRequest parsed = parse(one);
        assertThat(columnNames(parsed)).contains(SequenceStroomQl.ANCHORS, SequenceStroomQl.KEYS);
        // Seen in Stroom: unless the group's column is selected, nothing is grouped and each event is a row of 1.
        assertThat(parsed.getResultRequests().getFirst().getMappings().getFirst().getColumns())
                .anySatisfy(column -> assertThat(column.getGroup()).isEqualTo(0));

        final Step twoKeys = new Step(1, List.of(), List.of("user", "host name"), false, 1, List.of(), Map.of());
        final String two = SequenceStroomQl.anchorCount(new Side(EVENTS, "EventTime", twoKeys, true), null);
        assertThat(two).isEqualTo("""
                from "Events"
                eval Sequence = 'all'
                group by Sequence
                select Sequence, count() as Anchors, countUnique(concat(user, '|', ${host name})) as Keys""");
        parse(two);
    }

    @Test
    void whatStroomQlCantSayExactlyIsRefusedNotApproximated() {
        assertThat(SequenceStroomQl.unwritable("Step 1", step(List.of(new Filter("User", Condition.EQUALS, "x",
                true))), EVENTS)).contains("caseInsensitive=true").contains("Leave caseInsensitive out");
        assertThat(SequenceStroomQl.unwritable("Step 1", step(List.of(new Filter("Cmd", Condition.GREATER_THAN, "9",
                false, false, true))), EVENTS)).contains("length=true");
        assertThat(SequenceStroomQl.unwritable("Step 2", step(List.of(new Filter("Cmd", Condition.IS_NULL, "",
                false))), EVENTS)).contains("Step 2's condition on 'Cmd' is IS_NULL");
        assertThat(SequenceStroomQl.unwritable("Step 1", step(List.of(new Filter("Cmd", Condition.MATCHES_REGEX,
                "a.*", false))), EVENTS)).contains("regex");
        assertThat(SequenceStroomQl.unwritable("Step 1", step(List.of(equalsTo("Cmd", "a*"))), EVENTS)).isNull();
        assertThatThrownBy(() -> SequenceStroomQl.join(side(EVENTS, step(List.of())), side(EVENTS, step(List.of())),
                Duration.ofMinutes(1), new TimeRange(null, "\"; drop", null), 1))
                .isInstanceOf(WorkspaceException.class).hasMessageContaining("can't be read");
    }

    @Test
    void quotesInValuesAndNamesAreEscaped() {
        final String query = SequenceStroomQl.join(new Side(new DocRef(LuceneIndexDoc.TYPE, "u", "My \"events\""),
                        "EventTime", step(List.of(equalsTo("Cmd", "say \"hi\" \\ bye"))), true),
                side(EVENTS, step(List.of(equalsTo("Action", "x")))), Duration.ofMinutes(1), null, 1);

        assertThat(query).contains("from \"My \\\"events\\\"\" as a")
                .contains("a.Cmd = \"say \\\"hi\\\" \\\\ bye\"");
        assertThat(terms(parse(query).getQuery().getExpression())).extracting(ExpressionTerm::getValue)
                .contains("say \"hi\" \\ bye");
    }

    private static Side side(final DocRef doc, final Step step) {
        return new Side(doc, "EventTime", step, true);
    }

    private static Step step(final List<Filter> filters) {
        return new Step(1, filters, List.of("UserId"), false, 1, List.of(), Map.of());
    }

    private static Filter equalsTo(final String field, final String value) {
        return new Filter(field, Condition.EQUALS, value, false);
    }

    private static List<ExpressionTerm> terms(final ExpressionOperator operator) {
        final List<ExpressionTerm> terms = new ArrayList<>();
        if (operator == null || operator.getChildren() == null) {
            return terms;
        }
        for (final ExpressionItem item : operator.getChildren()) {
            if (item instanceof final ExpressionTerm term) {
                terms.add(term);
            } else if (item instanceof final ExpressionOperator child) {
                terms.addAll(terms(child));
            }
        }
        return terms;
    }

    private static List<String> columnNames(final SearchRequest request) {
        return request.getResultRequests().getFirst().getMappings().getFirst().getColumns().stream()
                .map(Column::getName)
                .toList();
    }

    private SearchRequest parse(final String query) {
        final DateTimeSettings dateTimeSettings = DateTimeSettings.builder().referenceTime(0L).build();
        final SearchRequest in = new SearchRequest(null, new QueryKey("test"), Query.builder().build(),
                new ArrayList<ResultRequest>(0), dateTimeSettings, false);
        return factory.create(query, in, ExpressionContext.builder().dateTimeSettings(dateTimeSettings).build());
    }
}
