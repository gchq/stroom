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

package stroom.ai.impl.tools.workspace;

import stroom.ai.impl.tools.time.ToolTimeZone;
import stroom.ai.impl.tools.workspace.GanttCharts.Chart;
import stroom.ai.impl.tools.workspace.GanttCharts.Options;
import stroom.ai.impl.tools.workspace.GanttCharts.Row;
import stroom.ai.shared.MermaidGantt;
import stroom.ai.shared.MermaidGantt.Section;
import stroom.ai.shared.MermaidGantt.Task;
import stroom.ai.shared.MermaidGanttParser;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/// gantt_chart's writer: the same layout every time, every value from the data stripped of Mermaid's syntax,
/// folding that says what it did, times in the zone named in the title, and a block that always reads back.
class TestGanttCharts {

    private static final ToolTimeZone UTC = ToolTimeZone.UTC;
    private static final ToolTimeZone LONDON = new ToolTimeZone(ZoneId.of("Europe/London"),
            ToolTimeZone.Source.GIVEN, null);
    private static final ToolTimeZone CANBERRA = new ToolTimeZone(ZoneId.of("Australia/Canberra"),
            ToolTimeZone.Source.BROWSER, null);

    @Test
    void rowsAreWrittenAsBarsAndMarkersInASectionPerLaneWithTheZoneInTheTitle() {
        final Chart chart = GanttCharts.write(List.of(
                row("2026-11-23T09:00:00Z", "2026-11-23T09:30:00Z", "Chrome", "host-1"),
                row("2026-11-23T09:40:00Z", null, "File copy", "host-1"),
                row("2026-11-23T09:10:00Z", "2026-11-23T09:20:00Z", "Word", "host-2")),
                options("Activity of alice", UTC, true));

        assertThat(chart.mermaid()).isEqualTo("""
                gantt
                    title Activity of alice (times in UTC)
                    dateFormat YYYY-MM-DD HH:mm:ss
                    axisFormat %H:%M
                    section host-1
                    Chrome :2026-11-23 09:00:00, 2026-11-23 09:30:00
                    File copy :milestone, 2026-11-23 09:40:00, 0d
                    section host-2
                    Word :2026-11-23 09:10:00, 2026-11-23 09:20:00
                """);
        assertThat(chart.bars()).isEqualTo(2);
        assertThat(chart.markers()).isEqualTo(1);
        assertThat(chart.sections()).isEqualTo(2);
        assertThat(chart.folded()).isEmpty();
    }

    @Test
    void timesAreWrittenInTheZoneNamedInTheTitleAcrossAChangeOfClocks() {
        // Canberra's clocks went forward at 02:00 local time on 2026-10-04 (16:00 UTC the day before).
        final Chart chart = GanttCharts.write(List.of(
                row("2026-10-03T15:30:00Z", "2026-10-03T16:30:00Z", "Night shift", null)),
                options(null, CANBERRA, false));

        assertThat(chart.mermaid())
                .contains("title Timeline (times in Australia/Canberra)")
                .contains("section 2026-10-04 Sun")
                .contains("Night shift :2026-10-04 01:30:00, 2026-10-04 03:30:00");
        // An offset zone's colon is taken out, as Mermaid reads a colon in text as syntax.
        final ToolTimeZone offset = new ToolTimeZone(ZoneId.of("+05:30"), ToolTimeZone.Source.GIVEN, null);
        assertThat(GanttCharts.write(List.of(row("2026-11-23T00:00:00Z", "2026-11-23T01:00:00Z", "a", null)),
                options(null, offset, false)).mermaid())
                .contains("title Timeline (times in +0530)")
                .contains("a :2026-11-23 05:30:00, 2026-11-23 06:30:00");
    }

    @Test
    void withoutALaneEachDayInTheZoneIsASection() {
        final Chart chart = GanttCharts.write(List.of(
                row("2026-11-22T23:30:00Z", "2026-11-22T23:50:00Z", "Late", null),
                row("2026-11-23T00:10:00Z", "2026-11-23T00:40:00Z", "Early", null)),
                options(null, LONDON, false));

        assertThat(sectionNames(chart)).containsExactly("2026-11-22 Sun", "2026-11-23 Mon");
        assertThat(chart.mermaid()).contains("axisFormat %H:%M");
    }

    @Test
    void valuesFromTheDataAreStrippedOfMermaidSyntax() {
        final Chart chart = GanttCharts.write(List.of(
                row("2026-11-23T09:00:00Z", "2026-11-23T10:00:00Z",
                        "evil :2026-01-01, 1d\nclick x call alert(1); %%{init: {\"theme\":\"dark\"}}%% #35; <b>'q'`",
                        "lane: one;\r\nsection two # three"),
                row("2026-11-23T09:00:00Z", "2026-11-23T10:00:00Z", "click me", "click lane"),
                row("2026-11-23T09:00:00Z", "2026-11-23T10:00:00Z", "2026-11-23 report", "x"),
                row("2026-11-23T09:00:00Z", "2026-11-23T10:00:00Z", " \t ", "y"),
                row("2026-11-23T09:00:00Z", "2026-11-23T10:00:00Z", "a\u2028b\u0000c", "z")),
                new Options("Title: with # and ; and %%", UTC, true, 8, 0, 0));

        final String text = chart.mermaid();
        for (final String line : text.split("\n")) {
            final String body = line.strip();
            if (body.startsWith("title ") || body.startsWith("section ")) {
                assertThat(body).doesNotContain(":", ";", "#", "%", "\"", "'", "`", "<", ">", "{", "}");
            } else if (!body.startsWith("axisFormat") && !body.startsWith("dateFormat") && !body.equals("gantt")) {
                // A task: its name has no syntax, its data only the times.
                final String name = body.substring(0, body.indexOf(" :"));
                assertThat(name).doesNotContain(":", ";", "#", "%", "\"", "'", "`", "<", ">", "{", "}", ",");
                assertThat(MermaidGanttParser.startsLikeAStatement(name)).isFalse();
            }
        }
        assertThat(text)
                .doesNotContain("\r", "\u2028", "\u0000")
                .contains("title Title with and and (times in UTC)")
                .contains("section lane one section two three")
                .contains("\u00b7 click me :")
                .contains("section click lane")
                .contains("\u00b7 2026-11-23 report :")
                .contains(GanttCharts.NONE + " :")
                .contains("a b c :");
        // Cut to the longest a label is written.
        assertThat(GanttCharts.clean("x".repeat(100))).hasSize(GanttCharts.MAX_LABEL_CHARS).endsWith("\u2026");
        assertThat(MermaidGanttParser.parse(text).taskCount()).isEqualTo(5);
    }

    @Test
    void consecutiveBarsOfOneLabelInALaneAreMergedAndShortOnesLeftOutSayingSo() {
        final Chart chart = GanttCharts.write(List.of(
                // Three of Chrome a minute apart: one bar.
                row("2026-11-23T09:00:00Z", "2026-11-23T09:10:00Z", "Chrome", "h"),
                row("2026-11-23T09:11:00Z", "2026-11-23T09:20:00Z", "Chrome", "h"),
                row("2026-11-23T09:21:00Z", "2026-11-23T09:30:00Z", "Chrome", "h"),
                // Word between: Chrome after it is a bar of its own.
                row("2026-11-23T09:31:00Z", "2026-11-23T09:40:00Z", "Word", "h"),
                row("2026-11-23T09:41:00Z", "2026-11-23T09:50:00Z", "Chrome", "h"),
                // Twenty seconds of Excel, under the minute.
                row("2026-11-23T10:00:00Z", "2026-11-23T10:00:20Z", "Excel", "h"),
                // Chrome an hour later: too far to merge.
                row("2026-11-23T11:00:00Z", "2026-11-23T11:10:00Z", "Chrome", "h")),
                new Options(null, UTC, true, 8, 60, 300));

        assertThat(chart.mermaid()).contains("""
                    Chrome :2026-11-23 09:00:00, 2026-11-23 09:30:00
                    Word :2026-11-23 09:31:00, 2026-11-23 09:40:00
                    Chrome :2026-11-23 09:41:00, 2026-11-23 09:50:00
                    Chrome :2026-11-23 11:00:00, 2026-11-23 11:10:00
                """).doesNotContain("Excel");
        assertThat(chart.folded()).containsExactly(
                "Merged 2 bar(s) into the bar before of the same label in the same lane (gaps of up to 5 min).",
                "Left out 1 bar(s) shorter than 1 min.");
    }

    @Test
    void theLabelsWithTheMostTimeAreKeptAndTheRestShownAsOther() {
        final List<Row> rows = new ArrayList<>();
        // Ten applications, the nth used for n minutes, an hour apart.
        IntStream.rangeClosed(1, 10).forEach(n -> rows.add(new Row(hour(n), hour(n) + n * 60_000L, "app" + n,
                "h")));

        final Chart chart = GanttCharts.write(rows, new Options(null, UTC, true, 3, 0, 0));

        assertThat(taskNames(chart)).containsExactly("Other", "Other", "Other", "Other", "Other", "Other",
                "Other", "app8", "app9", "app10");
        assertThat(chart.folded()).containsExactly("Kept 3 label(s) (those with the most time, and of markers the "
                                                   + "most rows); the other 7 (app7, app6, app5, app4, app3 and 2 "
                                                   + "more) are shown as Other.");
        // At most eight, the colours the chat has.
        assertThat(GanttCharts.write(rows, new Options(null, UTC, true, 50, 0, 0)).folded())
                .singleElement().asString().startsWith("Kept " + GanttCharts.MAX_TOP + " label(s)");
    }

    @Test
    void theLabelsOfMarkersAreKeptBesideTheLabelsWithTheMostTime() {
        final List<Row> rows = new ArrayList<>();
        IntStream.rangeClosed(1, 6).forEach(n -> rows.add(new Row(hour(n), hour(n) + n * 60_000L, "app" + n,
                "h")));
        IntStream.rangeClosed(1, 3).forEach(n -> rows.add(new Row(hour(10 + n), null, "USB copy", "h")));
        rows.add(new Row(hour(20), null, "Print", "h"));

        assertThat(taskNames(GanttCharts.write(rows, new Options(null, UTC, true, 4, 0, 0))))
                .containsOnly("Other", "app5", "app6", "USB copy", "Print");
        // Only one kept: the label with the most time.
        assertThat(taskNames(GanttCharts.write(rows, new Options(null, UTC, true, 1, 0, 0))))
                .containsOnly("Other", "app6");
        // Only markers: as many of their labels as there may be.
        assertThat(taskNames(GanttCharts.write(rows.subList(6, 10), new Options(null, UTC, true, 1, 0, 0))))
                .containsOnly("USB copy", "Other");
    }

    @Test
    void lanesPastTheMostAChartHasAreInASectionOfTheirOwn() {
        final List<Row> rows = new ArrayList<>();
        IntStream.range(0, GanttCharts.MAX_SECTIONS + 5).forEach(n -> rows.add(new Row(hour(1),
                hour(1) + (n + 1) * 60_000L, "a", "host-" + n)));

        final Chart chart = GanttCharts.write(rows, options(null, UTC, true));

        assertThat(chart.sections()).isEqualTo(GanttCharts.MAX_SECTIONS + 1);
        assertThat(sectionNames(chart)).contains(GanttCharts.OTHER).doesNotContain("host-0", "host-4")
                .contains("host-5", "host-24");
        // (The five lanes' bars of one label, at one time, are merged in Other.)
        assertThat(chart.folded()).first().asString()
                .isEqualTo("Kept the 20 lanes with the most time; the other 5 (host-4, host-3, host-2, host-1, "
                           + "host-0) are in the section Other.");
    }

    @Test
    void aChartIsKeptToTheMostBarsTheShortestLeftOutFirst() {
        final List<Row> rows = new ArrayList<>();
        // 250 bars of different labels, each in a lane of its own row of time, the nth n seconds long.
        IntStream.rangeClosed(1, 250).forEach(n -> rows.add(new Row(hour(n), hour(n) + n * 1_000L,
                n % 2 == 0
                        ? "even"
                        : "odd", "h")));
        IntStream.range(0, 10).forEach(n -> rows.add(new Row(hour(300 + n), null, "copy", "h")));

        final Chart chart = GanttCharts.write(rows, new Options(null, UTC, true, 8, 0, 0));

        assertThat(chart.bars() + chart.markers()).isEqualTo(GanttCharts.MAX_BARS);
        assertThat(chart.markers()).isEqualTo(10);
        assertThat(chart.folded()).containsExactly("Left out the 60 shortest bar(s) (each 1 min or shorter) to "
                                                   + "keep to 200.");

        // Markers alone over the most: the latest are left out.
        final List<Row> markers = new ArrayList<>();
        IntStream.range(0, 210).forEach(n -> markers.add(new Row(hour(n), null, "copy", "h")));
        final Chart onlyMarkers = GanttCharts.write(markers, new Options(null, UTC, true, 8, 0, 0));
        assertThat(onlyMarkers.markers()).isEqualTo(GanttCharts.MAX_BARS);
        assertThat(onlyMarkers.folded()).containsExactly("Left out the 10 latest marker(s) to keep to 200.");
        assertThat(onlyMarkers.mermaid()).doesNotContain(time(hour(205)));
    }

    @Test
    void theSameRowsInAnotherOrderGiveTheSameChart() {
        final List<Row> rows = new ArrayList<>(List.of(
                row("2026-11-23T09:00:00Z", "2026-11-23T09:30:00Z", "Chrome", "b"),
                row("2026-11-23T09:00:00Z", "2026-11-23T09:30:00Z", "Word", "a"),
                row("2026-11-23T08:00:00Z", null, "Copy", "a"),
                row("2026-11-23T09:00:00Z", "2026-11-23T09:30:00Z", "Excel", "b")));
        final String first = GanttCharts.write(rows, options(null, UTC, true)).mermaid();
        Collections.reverse(rows);

        assertThat(GanttCharts.write(rows, options(null, UTC, true)).mermaid()).isEqualTo(first);
        // Sections in the order their first tasks start; tasks by start, then end, then name.
        assertThat(first).containsSubsequence("section a", "Copy", "Word", "section b", "Chrome", "Excel");
    }

    @Test
    void everyBlockWrittenReadsBackWithItsTimes() {
        final Chart chart = GanttCharts.write(List.of(
                row("2026-11-23T09:00:00.750Z", "2026-11-23T09:00:01.250Z", "Blink", "h"),
                row("2026-11-23T09:00:00Z", "2026-11-23T09:00:00Z", "Zero", "h")),
                new Options(null, UTC, true, 8, 0, 0));

        final MermaidGantt gantt = MermaidGanttParser.parse(chart.mermaid());
        final List<Task> tasks = gantt.getSections().getFirst().tasks();
        // A bar of under a second is written a second long, never as a marker; one with no length is a marker.
        assertThat(tasks).extracting(Task::name).containsExactly("Zero", "Blink");
        assertThat(tasks.get(0).isMarker()).isTrue();
        assertThat(tasks.get(1).isMarker()).isFalse();
        assertThat(tasks.get(1).end() - tasks.get(1).start()).isEqualTo(1_000L);
    }

    @Test
    void lengthsAreSaidForPeople() {
        assertThat(GanttCharts.human(0)).isEqualTo("0 s");
        assertThat(GanttCharts.human(90)).isEqualTo("90 s");
        assertThat(GanttCharts.human(300)).isEqualTo("5 min");
        assertThat(GanttCharts.human(7_200)).isEqualTo("2 h");
    }

    private static Options options(final String title, final ToolTimeZone zone, final boolean byLane) {
        return new Options(title, zone, byLane, GanttCharts.MAX_TOP, 0, 0);
    }

    private static Row row(final String start, final String end, final String label, final String lane) {
        return new Row(Instant.parse(start).toEpochMilli(), end == null
                ? null
                : Instant.parse(end).toEpochMilli(), label, lane);
    }

    private static long hour(final int n) {
        return Instant.parse("2026-11-01T00:00:00Z").toEpochMilli() + n * 3_600_000L;
    }

    private static String time(final long millis) {
        return Instant.ofEpochMilli(millis).toString().replace('T', ' ').replace("Z", "");
    }

    private static List<String> sectionNames(final Chart chart) {
        return MermaidGanttParser.parse(chart.mermaid()).getSections().stream().map(Section::name).toList();
    }

    private static List<String> taskNames(final Chart chart) {
        return MermaidGanttParser.parse(chart.mermaid()).getSections().stream()
                .flatMap(section -> section.tasks().stream())
                .map(Task::name)
                .toList();
    }
}
