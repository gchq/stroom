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

package stroom.ai.impl.vis;

import stroom.ai.impl.vis.ChartCheck.Result;
import stroom.ai.impl.vis.ChartCheck.Sample;
import stroom.ai.impl.vis.ChartCheck.TableColumn;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TestChartCheck {

    private static final List<TableColumn> MESSAGE_COLUMNS = List.of(
            new TableColumn("Sender", "TEXT"),
            new TableColumn("Recipient", "TEXT"),
            new TableColumn("Count", "NUMBER"),
            new TableColumn("EventTime", "DATE_TIME"),
            new TableColumn("Note", "GENERAL"));

    private static final Sample MESSAGES = new Sample(
            List.of("Sender", "Recipient", "Count", "EventTime", "Note"),
            List.of(
                    List.of("alice", "bob", "3", "2026-10-24T01:00:00.000Z", "hi"),
                    List.of("bob", "carol", "5", "2026-10-24T02:00:00.000Z", "x"),
                    List.of("alice", "carol", "1", "2026-10-24T03:00:00.000Z", "y")));

    @Test
    void networkGraphOfMessagesIsOk() {
        final Result result = ChartCheck.check(ChartKind.GRAPH,
                Map.of("source", "Sender", "target", "Recipient", "weight", "Count"), Map.of(),
                MESSAGE_COLUMNS, MESSAGES);

        assertThat(result.ok()).isTrue();
        assertThat(result.warnings()).isEmpty();
        assertThat(result.stats()).containsEntry("nodes", 3).containsEntry("links", 3).containsEntry("rows", 3);
    }

    @Test
    void missingRequiredRoleIsAProblem() {
        final Result result = ChartCheck.check(ChartKind.GRAPH, Map.of("source", "Sender"), Map.of(),
                MESSAGE_COLUMNS, MESSAGES);

        assertThat(result.ok()).isFalse();
        assertThat(result.problems()).singleElement().asString()
                .startsWith("Map a column to target")
                .contains("'Recipient'");
    }

    @Test
    void unknownColumnSuggestsTheNearMiss() {
        final Result result = ChartCheck.check(ChartKind.BAR, Map.of("category", "sender", "value", "Count"),
                Map.of(), MESSAGE_COLUMNS, MESSAGES);

        assertThat(result.problems()).singleElement().asString()
                .contains("no column 'sender'")
                .contains("did you mean 'Sender'?");
    }

    @Test
    void unknownRoleIsAProblem() {
        final Result result = ChartCheck.check(ChartKind.PIE,
                Map.of("name", "Sender", "value", "Count", "colour", "Note"), Map.of(), MESSAGE_COLUMNS, MESSAGES);

        assertThat(result.problems()).anySatisfy(problem -> assertThat(problem).contains("has no 'colour'"));
    }

    @Test
    void textColumnCantBeAValue() {
        final Result result = ChartCheck.check(ChartKind.BAR, Map.of("category", "Recipient", "value", "Sender"),
                Map.of(), MESSAGE_COLUMNS, MESSAGES);

        assertThat(result.ok()).isFalse();
        assertThat(result.problems()).singleElement().asString()
                .contains("'Sender' can't be value")
                .contains("count()");
    }

    @Test
    void generalColumnOfWordsCantBeAValue() {
        final Result result = ChartCheck.check(ChartKind.BAR, Map.of("category", "Sender", "value", "Note"),
                Map.of(), MESSAGE_COLUMNS, MESSAGES);

        assertThat(result.problems()).singleElement().asString().contains("only 0 of the 3 values sampled");
    }

    @Test
    void aFewNonNumbersAreAWarning() {
        final List<List<String>> rows = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            rows.add(List.of("u" + i, Integer.toString(i)));
        }
        rows.add(List.of("u20", "n/a"));
        final Result result = ChartCheck.check(ChartKind.BAR, Map.of("category", "User", "value", "Total"),
                Map.of(), List.of(new TableColumn("User", "GENERAL"), new TableColumn("Total", "GENERAL")),
                new Sample(List.of("User", "Total"), rows));

        assertThat(result.ok()).isTrue();
        assertThat(result.warnings()).singleElement().asString().contains("1 of the 21 values sampled");
    }

    @Test
    void sameSourceAndTargetIsAProblem() {
        final Result result = ChartCheck.check(ChartKind.GRAPH, Map.of("source", "Sender", "target", "Sender"),
                Map.of(), MESSAGE_COLUMNS, MESSAGES);

        assertThat(result.problems()).singleElement().asString().contains("join a node to itself");
    }

    @Test
    void graphWithOnlySelfLinksIsAProblem() {
        final Sample sample = new Sample(List.of("Sender", "Recipient"),
                List.of(List.of("alice", "alice"), List.of("bob", "bob")));
        final Result result = ChartCheck.check(ChartKind.GRAPH, Map.of("source", "Sender", "target", "Recipient"),
                Map.of(), MESSAGE_COLUMNS, sample);

        assertThat(result.ok()).isFalse();
        assertThat(result.problems()).singleElement().asString().contains("no network to draw");
    }

    @Test
    void tooManyNodesIsAWarning() {
        final Result result = ChartCheck.check(ChartKind.GRAPH, Map.of("source", "Sender", "target", "Recipient"),
                Map.of("maxNodes", "2"), MESSAGE_COLUMNS, MESSAGES);

        assertThat(result.ok()).isTrue();
        assertThat(result.warnings()).singleElement().asString().contains("only the 2 most connected");
    }

    @Test
    void datesHeldAsTextAreAProblemForAScatterAxis() {
        final Sample sample = new Sample(List.of("When", "Count"),
                List.of(List.of("2026-10-24", "1"), List.of("2026-10-25", "2")));
        final Result result = ChartCheck.check(ChartKind.SCATTER, Map.of("x", "When", "y", "Count"), Map.of(),
                List.of(new TableColumn("When", "NUMBER"), new TableColumn("Count", "NUMBER")), sample);

        assertThat(result.problems()).singleElement().asString()
                .contains("holds dates as text")
                .contains("Date Time");
    }

    @Test
    void datesOfAGeneralColumnAreTakenAsDates() {
        // Stroom gives the chart a General column's dates as dates.
        final Sample sample = new Sample(List.of("When", "Count"),
                List.of(List.of("2026-10-24T01:00:00.000Z", "1"), List.of("2026-10-24T02:00:00.000Z", "2")));
        final Result scatter = ChartCheck.check(ChartKind.SCATTER, Map.of("x", "When", "y", "Count"), Map.of(),
                List.of(new TableColumn("When", "GENERAL"), new TableColumn("Count", "NUMBER")), sample);
        final Result line = ChartCheck.check(ChartKind.LINE, Map.of("x", "When", "y", "Count"), Map.of(),
                List.of(new TableColumn("When", "GENERAL"), new TableColumn("Count", "NUMBER")), sample);

        assertThat(scatter.ok()).isTrue();
        assertThat(line.ok()).isTrue();
        assertThat(line.warnings()).isEmpty();
    }

    @Test
    void numbersWithThousandsSeparatorsAreNumbers() {
        final Sample sample = new Sample(List.of("User", "Bytes"),
                List.of(List.of("a", "1,234"), List.of("b", "12,345,678.5"), List.of("c", "7")));
        final Result result = ChartCheck.check(ChartKind.BAR, Map.of("category", "User", "value", "Bytes"),
                Map.of(), List.of(new TableColumn("User", "TEXT"), new TableColumn("Bytes", "GENERAL")), sample);

        assertThat(result.ok()).isTrue();
        assertThat(result.warnings()).isEmpty();
    }

    @Test
    void datesOfAStroomQlQueryAreTakenAsTheyAre() {
        final Sample sample = new Sample(List.of("When", "Count"),
                List.of(List.of("2026-10-24T00:00:00.000Z", "1"), List.of("2026-10-25T00:00:00.000Z", "2")));
        final Result result = ChartCheck.check(ChartKind.SCATTER, Map.of("x", "When", "y", "Count"), Map.of(),
                List.of(new TableColumn("When", null), new TableColumn("Count", null)), sample);

        assertThat(result.ok()).isTrue();
    }

    @Test
    void lineOverTextDatesWarnsAboutTheTimeAxis() {
        final Sample sample = new Sample(List.of("When", "Count"),
                List.of(List.of("2026-10-24", "1"), List.of("2026-10-25", "2")));
        final Result result = ChartCheck.check(ChartKind.LINE, Map.of("x", "When", "y", "Count"), Map.of(),
                List.of(new TableColumn("When", "TEXT"), new TableColumn("Count", "NUMBER")), sample);

        assertThat(result.ok()).isTrue();
        assertThat(result.warnings()).singleElement().asString().contains("time axis");
    }

    @Test
    void aDateTimeColumnOfFormattedTextIsAProblemSayingToUseTheDateItself() {
        // The chat's case: the format is Date Time, but formatDate() makes text, which a chart gets as no value.
        final Sample sample = new Sample(List.of("Hour", "Count"),
                List.of(List.of("2026-09-28 13:00", "37"), List.of("2026-09-28 14:00", "12")));
        final Result result = ChartCheck.check(ChartKind.LINE, Map.of("x", "Hour", "y", "Count"), Map.of(),
                List.of(new TableColumn("Hour", "DATE_TIME",
                                "formatDate(floorHour(${@timestamp}), 'yyyy-MM-dd HH:mm')"),
                        new TableColumn("Count", "NUMBER", "count()")), sample);

        assertThat(result.problems()).singleElement().asString()
                .startsWith("'Hour' can't be x: its format is Date Time but its expression makes text "
                            + "(formatDate())")
                .contains("change the column's expression to the date itself, floorHour(${@timestamp}), and its "
                          + "format to Date Time");
    }

    @Test
    void aDateTimeColumnOfOtherTextIsAProblemSayingToParseIt() {
        final Sample sample = new Sample(List.of("Day", "Count"),
                List.of(List.of("24/10/2026", "1"), List.of("25/10/2026", "2")));
        for (final ChartKind kind : List.of(ChartKind.LINE, ChartKind.SCATTER)) {
            final Result result = ChartCheck.check(kind, Map.of("x", "Day", "y", "Count"), Map.of(),
                    List.of(new TableColumn("Day", "DATE_TIME", "substringBefore(${Logged}, ' ')"),
                            new TableColumn("Count", "NUMBER", "count()")), sample);

            assertThat(result.problems()).as(kind.name()).singleElement().asString()
                    .contains("its expression makes text (substringBefore())")
                    .contains("parseDate(<the expression>, '<its pattern>')");
        }
    }

    @Test
    void aDateTimeColumnOfIsoTextFromFormatDateIsOk() {
        // formatDate() with no pattern gives an ISO 8601 date, which a chart gets as a date.
        final Sample sample = new Sample(List.of("Hour", "Count"),
                List.of(List.of("2026-09-28T13:00:00.000Z", "37"), List.of("2026-09-28T14:00:00.000Z", "12")));
        final Result result = ChartCheck.check(ChartKind.LINE, Map.of("x", "Hour", "y", "Count"), Map.of(),
                List.of(new TableColumn("Hour", "DATE_TIME", "formatDate(floorHour(${@timestamp}))"),
                        new TableColumn("Count", "NUMBER", "count()")), sample);

        assertThat(result.ok()).isTrue();
        assertThat(result.warnings()).isEmpty();
    }

    @Test
    void aDateTimeColumnOfADateIsOk() {
        final Sample sample = new Sample(List.of("Hour", "Count"),
                List.of(List.of("2026-09-28 13:00", "37"), List.of("2026-09-28 14:00", "12")));
        final Result result = ChartCheck.check(ChartKind.LINE, Map.of("x", "Hour", "y", "Count"), Map.of(),
                List.of(new TableColumn("Hour", "DATE_TIME", "floorHour(${@timestamp})"),
                        new TableColumn("Count", "NUMBER", "count()")), sample);

        assertThat(result.ok()).isTrue();
        assertThat(result.warnings()).isEmpty();
    }

    @Test
    void aNumberColumnOfTextIsAProblemOnlyWhenTheTextIsntNumbers() {
        final Result words = ChartCheck.check(ChartKind.BAR, Map.of("category", "User", "value", "Total"),
                Map.of(), List.of(new TableColumn("User", "TEXT", "${User}"),
                        new TableColumn("Total", "NUMBER", "concat(sum(${Bytes}), ' bytes')")),
                new Sample(List.of("User", "Total"), List.of(List.of("a", "10 bytes"), List.of("b", "20 bytes"))));
        final Result numbers = ChartCheck.check(ChartKind.BAR, Map.of("category", "User", "value", "Total"),
                Map.of(), List.of(new TableColumn("User", "TEXT", "${User}"),
                        new TableColumn("Total", "NUMBER", "toString(sum(${Bytes}))")),
                new Sample(List.of("User", "Total"), List.of(List.of("a", "10"), List.of("b", "20"))));

        assertThat(words.problems()).singleElement().asString()
                .startsWith("'Total' can't be value: its format is Number but its expression makes text "
                            + "(concat()), and 2 of the 2 values sampled aren't numbers");
        assertThat(numbers.ok()).isTrue();
    }

    @Test
    void aNumberColumnOfTextIsAProblemForAnyRole() {
        // A category axis of a Number column gets no value for text that isn't a number, as a value axis does.
        final Result result = ChartCheck.check(ChartKind.LINE, Map.of("x", "Code", "y", "Count"), Map.of(),
                List.of(new TableColumn("Code", "NUMBER", "upperCase(${Code})"),
                        new TableColumn("Count", "NUMBER", "count()")),
                new Sample(List.of("Code", "Count"), List.of(List.of("ABC", "1"), List.of("DEF", "2"))));

        assertThat(result.problems()).singleElement().asString().startsWith("'Code' can't be x: its format is "
                                                                            + "Number");
    }

    @Test
    void textDatesFromFormatDateAreAdvisedToUseTheDateNotJustTheFormat() {
        // Setting the format to Date Time alone leaves formatDate()'s text as text, as the chat found.
        final Sample sample = new Sample(List.of("Hour", "Count"),
                List.of(List.of("2026-09-28 13:00", "37"), List.of("2026-09-28 14:00", "12")));
        final List<TableColumn> columns = List.of(
                new TableColumn("Hour", "TEXT", "formatDate(floorHour(${@timestamp}), 'yyyy-MM-dd HH:mm')"),
                new TableColumn("Count", "NUMBER", "count()"));
        final Result line = ChartCheck.check(ChartKind.LINE, Map.of("x", "Hour", "y", "Count"), Map.of(),
                columns, sample);
        final Result scatter = ChartCheck.check(ChartKind.SCATTER, Map.of("x", "Hour", "y", "Count"), Map.of(),
                columns, sample);

        assertThat(line.ok()).isTrue();
        assertThat(line.warnings()).singleElement().asString()
                .contains("change the column's expression to the date itself, floorHour(${@timestamp}), and its "
                          + "format to Date Time")
                .doesNotContain("Set the column's format to Date Time");
        assertThat(scatter.problems()).singleElement().asString()
                .contains("Change the column's expression to the date itself, floorHour(${@timestamp})");
    }

    @Test
    void textDatesOfAPlainFieldAreStillAdvisedToSetTheFormat() {
        final Sample sample = new Sample(List.of("When", "Count"),
                List.of(List.of("2026-10-24", "1"), List.of("2026-10-25", "2")));
        final Result result = ChartCheck.check(ChartKind.LINE, Map.of("x", "When", "y", "Count"), Map.of(),
                List.of(new TableColumn("When", "TEXT", "${When}"), new TableColumn("Count", "NUMBER", "count()")),
                sample);

        assertThat(result.warnings()).singleElement().asString()
                .contains("set the column's format to Date Time (an edit_column change)");
    }

    @Test
    void aColumnGroupedBelowTheTopLevelIsAProblem() {
        // The chat's case: pairs grouped at two levels give a chart one row per User A, with User B blank.
        final List<TableColumn> columns = List.of(
                new TableColumn("User A", "TEXT", "${user.name}", 0),
                new TableColumn("User B", "TEXT", "${user.target.id}", 1),
                new TableColumn("Messages", "NUMBER", "count()", null));
        final Result result = ChartCheck.check(ChartKind.GRAPH,
                Map.of("source", "User A", "target", "User B", "weight", "Messages"), Map.of(), columns, null);

        assertThat(result.problems()).singleElement().asString()
                .isEqualTo("'User B' can't be target: it is grouped at level 2, but a network graph gets only the "
                           + "table's top-level rows (one per 'User A', grouped at level 1), where 'User B' is "
                           + "blank. Group 'User B' at level 1 too (a group change), so each row is one "
                           + "combination of them.");
    }

    @Test
    void columnsGroupedTogetherAtTheTopLevelAreOk() {
        final Sample sample = new Sample(List.of("User A", "User B", "Messages"),
                List.of(List.of("alice", "bob", "3"), List.of("bob", "carol", "2")));
        final Result together = ChartCheck.check(ChartKind.GRAPH,
                Map.of("source", "User A", "target", "User B", "weight", "Messages"), Map.of(), List.of(
                        new TableColumn("User A", "TEXT", "${user.name}", 0),
                        new TableColumn("User B", "TEXT", "${user.target.id}", 0),
                        new TableColumn("Messages", "NUMBER", "count()", null)), sample);
        // A deeper level the chart doesn't use doesn't matter.
        final Result deeperUnused = ChartCheck.check(ChartKind.BAR,
                Map.of("category", "User A", "value", "Messages"), Map.of(), List.of(
                        new TableColumn("User A", "TEXT", "${user.name}", 0),
                        new TableColumn("User B", "TEXT", "${user.target.id}", 1),
                        new TableColumn("Messages", "NUMBER", "count()", null)),
                new Sample(List.of("User A", "Messages"), List.of(List.of("alice", "5"))));

        assertThat(together.ok()).isTrue();
        assertThat(deeperUnused.ok()).isTrue();
    }

    @Test
    void groupLevelsAreTakenFromTheTopmostGroupedLevel() {
        // Levels needn't start at 0 for the check to find the top one.
        final Result result = ChartCheck.check(ChartKind.BAR,
                Map.of("category", "A", "value", "N", "series", "B"), Map.of(), List.of(
                        new TableColumn("A", "TEXT", "${a}", 2),
                        new TableColumn("B", "TEXT", "${b}", 3),
                        new TableColumn("N", "NUMBER", "count()", null)), null);

        assertThat(result.problems()).singleElement().asString()
                .startsWith("'B' can't be series: it is grouped at level 4")
                .contains("(one per 'A', grouped at level 3)");
    }

    @Test
    void pieWithManyNamesWarnsAboutOther() {
        final List<List<String>> rows = new ArrayList<>();
        for (int i = 0; i < 15; i++) {
            rows.add(List.of("type" + i, "1"));
        }
        final Result result = ChartCheck.check(ChartKind.PIE, Map.of("name", "Type", "value", "Count"), Map.of(),
                List.of(new TableColumn("Type", "TEXT"), new TableColumn("Count", "NUMBER")),
                new Sample(List.of("Type", "Count"), rows));

        assertThat(result.warnings()).singleElement().asString().contains("combined as 'Other'");
        assertThat(result.stats()).containsEntry("slices", 15);
    }

    @Test
    void noSampleChecksOnlyTheColumns() {
        final Result result = ChartCheck.check(ChartKind.BAR, Map.of("category", "Sender", "value", "Count"),
                Map.of(), MESSAGE_COLUMNS, null);

        assertThat(result.ok()).isTrue();
        assertThat(result.warnings()).singleElement().asString().contains("no rows to check");
    }

    @Test
    void blankRequiredColumnIsAProblem() {
        final Sample sample = new Sample(List.of("Sender", "Count"),
                List.of(List.of("", "1"), List.of("", "2")));
        final Result result = ChartCheck.check(ChartKind.BAR, Map.of("category", "Sender", "value", "Count"),
                Map.of(), MESSAGE_COLUMNS, sample);

        assertThat(result.problems()).singleElement().asString().contains("is blank");
    }
}
