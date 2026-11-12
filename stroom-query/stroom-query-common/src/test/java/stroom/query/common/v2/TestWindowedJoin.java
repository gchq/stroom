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

package stroom.query.common.v2;

import stroom.query.api.JoinKey;
import stroom.query.api.Query;
import stroom.query.api.QueryJoin;
import stroom.query.api.SearchRequest;
import stroom.query.api.datasource.QueryField;
import stroom.query.language.functions.ExpressionContext;
import stroom.query.language.functions.FieldIndex;
import stroom.util.shared.ErrorMessage;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/// Event-to-event joins, run by the [JoinSearchRunner] against fake logon and export sources.
class TestWindowedJoin extends AbstractJoinSearchTest {

    private static final String JOIN = """
            from logons as logon
            %s exports as export
              on logon.UserId = export.UserId
              within 10m %s logon.EventTime
            select %s""";
    private static final String ANCHOR_AND_FILE = "logon.UserId, logon.EventTime, export.File";
    private static final String ANCHOR = "logon.UserId, logon.EventTime";
    private static final String TEN = "2026-01-01T10:00:00.000Z";
    private static final String TWELVE = "2026-01-01T12:00:00.000Z";

    @Test
    void innerJoinGivesEachAnchorAndEventInItsWindow() throws InterruptedException {
        final List<List<String>> rows = search("join", "after", ANCHOR_AND_FILE);

        // f2 is 20 minutes after a's logon, f3 is before b's, and f4 is on the window's end.
        assertThat(rows).containsExactlyInAnyOrder(
                List.of("a", TEN, "f1"),
                List.of("c", TEN, "f4"));
    }

    @Test
    void windowCanEndAtTheAnchor() throws InterruptedException {
        final List<List<String>> rows = search("join", "before", ANCHOR_AND_FILE);

        assertThat(rows).containsExactly(List.of("b", TEN, "f3"));
    }

    @Test
    void leftJoinAlsoGivesAnchorsWithNoEventInTheirWindow() throws InterruptedException {
        final List<List<String>> rows = search("left join", "after", ANCHOR_AND_FILE);

        assertThat(rows).containsExactlyInAnyOrder(
                List.of("a", TEN, "f1"),
                List.of("c", TEN, "f4"),
                List.of("b", TEN, ""),
                List.of("a", TWELVE, ""),
                // A logon with no user has no key, so matches nothing, but is still given.
                List.of("", TEN, ""));
    }

    @Test
    void keepJoinGivesEachMatchedAnchorOnce() throws InterruptedException {
        final List<List<String>> rows = search("keep join", "after", ANCHOR);

        assertThat(rows).containsExactlyInAnyOrder(
                List.of("a", TEN),
                List.of("c", TEN));
    }

    @Test
    void dropJoinGivesEachUnmatchedAnchor() throws InterruptedException {
        final List<List<String>> rows = search("drop join", "after", ANCHOR);

        assertThat(rows).containsExactlyInAnyOrder(
                List.of("b", TEN),
                List.of("a", TWELVE),
                List.of("", TEN));
    }

    @Test
    void joinedSourceIsSearchedOnlyWithinTheAnchorsWindows() throws InterruptedException {
        sourceFields.put("exports", List.of(QueryField.createKeyword("UserId"), QueryField.createDate("EventTime")));

        search("join", "after", ANCHOR_AND_FILE);

        final SearchRequest exportsRequest = startedRequests.stream()
                .filter(request -> "exports".equals(request.getQuery().getDataSource().getName()))
                .findFirst()
                .orElseThrow();
        // The windows of the logons at ten and at twelve, a millisecond wider each way, not the
        // time between them.
        assertThat(exportsRequest.getQuery().getExpression().toString())
                .contains("EventTime between 2026-01-01T09:59:59.999Z,2026-01-01T10:10:00.001Z")
                .contains("EventTime between 2026-01-01T11:59:59.999Z,2026-01-01T12:10:00.001Z")
                .contains("UserId in a,b,c");
    }

    @Test
    void searchRangesAreTheWindowsMergedWhereTheyOverlap() {
        final WindowedJoin windowedJoin = plan("join", "after");

        final List<WindowedJoin.TimeSpan> ranges = windowedJoin.getSearchRanges(
                times("10:00", "10:05", "12:00", "10:30"), 10);

        assertThat(ranges).containsExactly(
                span("10:00", "10:15"),
                span("10:30", "10:40"),
                span("12:00", "12:10"));
    }

    @Test
    void searchRangesBeforeTheAnchorsEndAtThem() {
        final WindowedJoin windowedJoin = plan("join", "before");

        assertThat(windowedJoin.getSearchRanges(times("10:00"), 10)).containsExactly(span("09:50", "10:00"));
    }

    @Test
    void tooManySearchRangesAreMergedAcrossTheSmallestGaps() {
        final WindowedJoin windowedJoin = plan("join", "after");

        // Gaps of 20m, 10m and 1h50m between the windows.
        final List<WindowedJoin.TimeSpan> ranges = windowedJoin.getSearchRanges(
                times("10:00", "10:30", "10:50", "12:50"), 2);

        assertThat(ranges).containsExactly(
                span("10:00", "11:00"),
                span("12:50", "13:00"));
    }

    @Test
    void noAnchorTimesGiveNoSearchRanges() {
        assertThat(plan("join", "after").getSearchRanges(new long[0], 10)).isEmpty();
    }

    @Test
    void keyNamingTheJoinedSourceOnTheFirstSourcesSideIsRefused() {
        // The parser refuses such a key; this is the plan's own check.
        final Query parsed = parse(JOIN.formatted("join", "after", ANCHOR_AND_FILE)).getQuery();
        final QueryJoin join = parsed.getJoins().getFirst();
        final Query query = parsed.copy()
                .joins(List.of(new QueryJoin(join.getType(), join.getAlias(), join.getQuery(), null,
                        List.of(new JoinKey("${export.File}", "${export.UserId}")), join.getWindow())))
                .build();

        assertThatThrownBy(() -> WindowedJoin.create(
                query, new FieldIndex(), ExpressionContext.builder().build(), Map.of(), "EventTime"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("can't name a field of 'export', as 'export.File' does");
    }

    @Test
    void windowCanHoldManyEventsForOneAnchor() throws InterruptedException {
        final List<List<String>> rows = search("join", "after", ANCHOR_AND_FILE, "30m");

        assertThat(rows).containsExactlyInAnyOrder(
                List.of("a", TEN, "f1"),
                List.of("a", TEN, "f2"),
                List.of("c", TEN, "f4"));
    }

    @Test
    void joinedSourceWithNoTimeFieldIsRefused() {
        sourceTimeFields.remove("exports");

        assertThat(searchErrors("join", "after", ANCHOR_AND_FILE))
                .anySatisfy(message -> assertThat(message).contains("'export' has none"));
    }

    @Test
    void windowOnAJoinedFieldIsRefused() {
        final String stroomQl = """
                from logons as logon
                join exports as export on logon.UserId = export.UserId within 10m after export.EventTime
                select logon.UserId""";

        assertThat(searchErrors(stroomQl))
                .anySatisfy(message -> assertThat(message).contains("must be a field of the first source"));
    }

    @Test
    void windowedJoinWithOtherJoinsIsRefused() {
        final String stroomQl = """
                from logons as logon
                join exports as export on logon.UserId = export.UserId within 10m after logon.EventTime
                join hr on logon.UserId = hr.Username
                select logon.UserId""";

        assertThat(searchErrors(stroomQl))
                .anySatisfy(message -> assertThat(message).contains("can't have other join lines yet"));
    }

    @Test
    void tooManyEventsInTheWindowsFailsTheSearchBeforeAnyRowIsJoined() {
        final JoinSearchRunner runner = createRunner(1);
        final Run run = run(runner, """
                from logons as logon
                join exports as export on logon.UserId = export.UserId within 30m after logon.EventTime
                where logon.UserId = 'a' and logon.EventTime = '2026-01-01T10:00:00.000Z'
                select logon.UserId""", new FakeSources());

        awaitComplete(run.placeholder());
        assertThat(run.searchStore().get()).as("no store with partial rows took its place").isSameAs(
                run.placeholder());
        assertThat(run.placeholder().getErrors())
                .extracting(ErrorMessage::getMessage)
                .anySatisfy(message -> assertThat(message).contains("More than 1 rows of 'export'"));
    }

    private WindowedJoin plan(final String joinType, final String direction) {
        final SearchRequest request = parse(JOIN.formatted(joinType, direction, ANCHOR_AND_FILE));
        final FieldIndex fieldIndex = new FieldIndex();
        for (final String field : ANCHOR_AND_FILE.split(", ")) {
            fieldIndex.create(field);
        }
        return WindowedJoin.create(
                request.getQuery(),
                fieldIndex,
                ExpressionContext.builder().build(),
                Map.of(),
                "EventTime");
    }

    private static long[] times(final String... times) {
        return Arrays.stream(times).mapToLong(time -> instant(time).toEpochMilli()).toArray();
    }

    private static WindowedJoin.TimeSpan span(final String from, final String to) {
        return new WindowedJoin.TimeSpan(instant(from), instant(to));
    }

    // A time on 1 January 2026, e.g. 10:00.
    private static Instant instant(final String time) {
        return Instant.parse("2026-01-01T" + time + ":00Z");
    }

    private List<List<String>> search(final String joinType,
                                      final String direction,
                                      final String select) throws InterruptedException {
        return search(joinType, direction, select, "10m");
    }

    private List<List<String>> search(final String joinType,
                                      final String direction,
                                      final String select,
                                      final String window) throws InterruptedException {
        final JoinSearchRunner runner = createRunner(1_000_000);
        final Run run = run(runner, JOIN.formatted(joinType, direction, select).replace("10m", window),
                new FakeSources());
        final ResultStore searchStore = run.awaitSearchStore();
        awaitComplete(searchStore);
        assertThat(searchStore.getErrors()).isEmpty();
        return rows(searchStore, select.split(", "));
    }

    private List<String> searchErrors(final String joinType, final String direction, final String select) {
        return searchErrors(JOIN.formatted(joinType, direction, select));
    }

    // The errors of a search that fails before its joined rows are given.
    private List<String> searchErrors(final String stroomQl) {
        final JoinSearchRunner runner = createRunner(1_000_000);
        final Run run = run(runner, stroomQl, new FakeSources());
        awaitComplete(run.placeholder());
        return run.placeholder().getErrors().stream().map(ErrorMessage::getMessage).toList();
    }
}
