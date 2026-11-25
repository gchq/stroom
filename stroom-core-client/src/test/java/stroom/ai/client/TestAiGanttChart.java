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

package stroom.ai.client;

import stroom.ai.client.AiGanttChart.Scale;
import stroom.ai.shared.MermaidGantt;
import stroom.ai.shared.MermaidGantt.Task;
import stroom.ai.shared.MermaidGanttParser;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/// The chat's own drawing of a Mermaid gantt chart: its layout's maths (the axis, rows of tasks that don't
/// overlap, colours by name) and SVG whose every text is escaped.
class TestAiGanttChart {

    private static final long MINUTE = 60_000L;
    private static final long HOUR = 60 * MINUTE;
    private static final long DAY = MermaidGantt.MILLIS_PER_DAY;

    @Test
    void ticksAreWholeStepsFewEnoughToRead() {
        final long nine = MermaidGantt.wallClock(2026, 11, 23, 9, 7, 0, 0);

        // Under an hour: ten minutes apart, from the first whole ten.
        assertThat(AiGanttChart.ticks(nine, nine + 50 * MINUTE, 8)).extracting(tick -> AiGanttChart.format(tick,
                "%H:%M")).containsExactly("09:10", "09:20", "09:30", "09:40", "09:50");
        // A day: six hours apart, on the wall clock's hours, midnight among them.
        assertThat(AiGanttChart.ticks(nine, nine + DAY, 8)).extracting(tick -> AiGanttChart.format(tick,
                "%d %H:%M")).containsExactly("23 12:00", "23 18:00", "24 00:00", "24 06:00");
        // A week: a tick a day.
        assertThat(AiGanttChart.ticks(nine, nine + 7 * DAY, 8)).hasSize(7)
                .allMatch(tick -> tick % DAY == 0);
        assertThat(AiGanttChart.ticks(nine, nine + 400 * DAY * 10, 8)).hasSizeLessThanOrEqualTo(11);
    }

    @Test
    void aChartInTheChatIsDrawnAtTheWidthItHasRoomForButNoNarrowerThanTheLeast() {
        // As wide as the chat gives it, narrower or wider than the width first drawn at.
        assertThat(AiGanttChart.fitWidth(600)).isEqualTo(600);
        assertThat(AiGanttChart.fitWidth(1400)).isEqualTo(1400);
        // Below the least, drawn at the least and scaled down to fit, so its labels don't crowd.
        assertThat(AiGanttChart.fitWidth(300)).isEqualTo(AiGanttChart.MIN_WIDTH);
        assertThat(AiGanttChart.fitWidth(AiGanttChart.MIN_WIDTH)).isEqualTo(AiGanttChart.MIN_WIDTH);
        // No room known (the chat hidden, say): left as drawn.
        assertThat(AiGanttChart.fitWidth(0)).isZero();
        assertThat(AiGanttChart.fitWidth(-5)).isZero();
    }

    @Test
    void timesAreFormattedWithTheAxisFormatsTokens() {
        final long time = MermaidGantt.wallClock(2026, 1, 5, 7, 3, 9, 0);

        assertThat(AiGanttChart.format(time, "%Y-%m-%d %H:%M:%S")).isEqualTo("2026-01-05 07:03:09");
        assertThat(AiGanttChart.format(time, "%a %e %b %y")).isEqualTo("Mon 5 Jan 26");
        // Mermaid's default.
        assertThat(AiGanttChart.format(time, null)).isEqualTo("2026-01-05");
    }

    @Test
    void tasksArePackedIntoRowsTheyDontOverlapIn() {
        final Scale scale = new Scale(0, 10 * HOUR, 0, 1000);
        final Task a = task("a", 0, HOUR);
        final Task b = task("b", HOUR / 2, 2 * HOUR);
        final Task c = task("c", 2 * HOUR + MINUTE * 5, 3 * HOUR);
        final Task marker = task("m", HOUR + 20 * MINUTE, HOUR + 20 * MINUTE);

        final List<List<Task>> rows = AiGanttChart.rows(List.of(c, marker, b, a), scale);

        // b overlaps a; the marker's diamond overlaps b, not a; c starts after b ends.
        assertThat(rows).hasSize(2);
        assertThat(rows.get(0)).containsExactly(a, marker, c);
        assertThat(rows.get(1)).containsExactly(b);
    }

    @Test
    void namesAreColouredInTheOrderTheyFirstAppearWithOtherAndThoseAfterTheEighthGrey() {
        final List<String> names = List.of("a", "b", "Other", "c", "d", "e", "f", "g", "h", "i");

        assertThat(AiGanttChart.colourClass("a", names)).isEqualTo("ai-gantt-c1");
        assertThat(AiGanttChart.colourClass("c", names)).isEqualTo("ai-gantt-c3");
        assertThat(AiGanttChart.colourClass("Other", names)).isEqualTo("ai-gantt-other");
        assertThat(AiGanttChart.colourClass("h", names)).isEqualTo("ai-gantt-c8");
        assertThat(AiGanttChart.colourClass("i", names)).isEqualTo("ai-gantt-other");
    }

    @Test
    void aChartIsDrawnWithALanePerSectionBarsMarkersAnAxisAndALegend() {
        final String svg = AiGanttChart.svg(MermaidGanttParser.parse("""
                gantt
                    title Activity of alice (times in UTC)
                    dateFormat YYYY-MM-DD HH:mm:ss
                    axisFormat %H:%M
                    section host-1
                    Chrome :2026-11-23 09:00:00, 2026-11-23 09:30:00
                    USB copy :milestone, 2026-11-23 09:40:00, 0d
                    section host-2
                    Word :crit, 2026-11-23 09:10:00, 2026-11-23 10:00:00
                """));

        assertThat(svg)
                .startsWith("<svg xmlns=\"http://www.w3.org/2000/svg\" class=\"ai-gantt-svg\" viewBox=\"0 0 720 ")
                .contains("aria-label=\"Activity of alice (times in UTC)\"")
                .contains(">Activity of alice (times in UTC)</text>")
                .contains(">host-1</text>", ">host-2</text>")
                .contains("class=\"ai-gantt-bar ai-gantt-c1\"")
                .contains("class=\"ai-gantt-marker ai-gantt-c2\"")
                .contains("class=\"ai-gantt-bar ai-gantt-c3 ai-gantt--crit\"")
                // A bar's tooltip: its title and duration only (chat 195).
                .contains("<title>Chrome (30 min)</title>")
                .doesNotContain("<title>Chrome: ")
                .contains(">09:00</text>", ">10:00</text>")
                .contains(">USB copy</text>")
                .endsWith("</svg>");
        assertThat(count(svg, "<rect class=\"ai-gantt-bar ")).isEqualTo(2);
        assertThat(count(svg, "<path class=\"ai-gantt-marker ")).isEqualTo(1);
        assertThat(count(svg, "<g class=\"ai-gantt-lane")).isEqualTo(2);
        // Every bar starts and ends inside the plot.
        final Matcher bar = Pattern.compile("<rect class=\"ai-gantt-bar [^\"]+\"[^>]*? x=\"([0-9.]+)\" "
                                            + "y=\"[0-9]+\" width=\"([0-9.]+)\"").matcher(svg);
        while (bar.find()) {
            final double x = Double.parseDouble(bar.group(1));
            assertThat(x).isBetween(162.0, 700.0);
            assertThat(x + Double.parseDouble(bar.group(2))).isBetween(162.0, 700.0);
        }
    }

    @Test
    void everyTextFromTheBlockIsEscaped() {
        // The subset has no angle brackets or quotes, but an ampersand is text, and the renderer escapes all.
        final String svg = AiGanttChart.svg(MermaidGanttParser.parse("""
                gantt
                    title R&D (times in UTC)
                    section A&B
                    Q&A :2026-11-23, 1d
                """));

        assertThat(svg).contains(">R&amp;D (times in UTC)</text>", ">A&amp;B</text>", "<title>Q&amp;A (")
                .doesNotContain("R&D", ">A&B<", ">Q&A<");
    }

    @Test
    void aChartWithNoTasksOrTasksAtOneTimeIsStillDrawn() {
        assertThat(AiGanttChart.svg(MermaidGanttParser.parse("gantt\n  title Nothing"))).contains(">No tasks<");
        final MermaidGantt one = MermaidGanttParser.parse("gantt\n  m :milestone, 2026-11-23, 0d");
        final long[] range = AiGanttChart.range(one);
        assertThat(range[1] - range[0]).isEqualTo(HOUR);
        assertThat(AiGanttChart.svg(one)).contains("ai-gantt-marker").doesNotContain("NaN", "Infinity");
    }

    @Test
    void theLegendGroupsTheNamesWithoutAColour() {
        final StringBuilder block = new StringBuilder("gantt\n");
        for (int i = 1; i <= 10; i++) {
            block.append("app").append(i).append(" :2026-11-23, 1d\n");
        }
        block.append("Other :2026-11-23, 1d\n");
        final String svg = AiGanttChart.svg(MermaidGanttParser.parse(block.toString()));

        final String legend = svg.substring(svg.indexOf("<g class=\"ai-gantt-legend\">"));
        assertThat(legend).contains(">app8</text>", ">Other and 2 more</text>").doesNotContain(">app9</text>");
        assertThat(AiGanttChart.duration(90 * MINUTE)).isEqualTo("1 h 30 min");
        assertThat(AiGanttChart.duration(45_000)).isEqualTo("45 s");
    }

    @Test
    void aBarsTitleIsShownInItWhereItFitsCutToItsWidthAndLeftOutWhereItWouldntBeLegible() {
        final String name = "Reviewing finance mail archive";

        assertThat(AiGanttChart.barLabel(name, 400)).isEqualTo(name);
        // As many characters as fit, the last an ellipsis.
        assertThat(AiGanttChart.barLabel(name, 60)).isEqualTo("Reviewin\u2026");
        assertThat(AiGanttChart.barLabel("Word", 30)).isEqualTo("Word");
        // Too narrow for a few characters: none.
        assertThat(AiGanttChart.barLabel(name, 20)).isNull();
        assertThat(AiGanttChart.barLabel(name, 0)).isNull();
        assertThat(AiGanttChart.barLabel("", 400)).isNull();

        final String svg = AiGanttChart.svg(MermaidGanttParser.parse("""
                gantt
                    dateFormat YYYY-MM-DD HH:mm:ss
                    Reviewing finance mail archive :2026-11-23 09:00:00, 2026-11-23 17:00:00
                    Short one :2026-11-23 17:00:00, 2026-11-23 17:01:00
                """));
        // In a box the width of the bar, so it never runs past it; never in the pointer's way.
        assertThat(barLabels(svg)).containsExactly("Reviewing finance mail archive");
        // Drawn again at another width (a zoom or a resize), it is cut to the bar's width there.
        final MermaidGantt gantt = MermaidGanttParser.parse("""
                gantt
                    dateFormat YYYY-MM-DD HH:mm:ss
                    Reviewing finance mail archive :2026-11-23 09:00:00, 2026-11-23 09:30:00
                    Other work :2026-11-23 09:30:00, 2026-11-23 17:00:00
                """);
        assertThat(barLabels(AiGanttChart.svg(gantt, null, AiGanttChart.MIN_WIDTH))).containsExactly("Other work");
        final long nine = MermaidGantt.wallClock(2026, 11, 23, 9, 0, 0, 0);
        assertThat(barLabels(AiGanttChart.svg(gantt, new long[]{nine, nine + 40 * MINUTE}, AiGanttChart.WIDTH)))
                .containsExactly("Reviewing finance mail archive", "Other work");
    }

    @Test
    void eachBarHasAStartAndAnEndMarkerNamingItsEventAndWhenItHappened() {
        final String svg = AiGanttChart.svg(MermaidGanttParser.parse("""
                gantt
                    dateFormat YYYY-MM-DD HH:mm:ss
                    logged on :2026-11-23 09:00:00, 2026-11-23 17:00:00
                    %% events Logon-User | Logoff-User
                    Chrome :2026-11-23 10:00:00, 2026-11-23 10:30:00
                """));

        assertThat(count(svg, "<path class=\"ai-gantt-edge")).isEqualTo(4);
        assertThat(svg).contains("<title>Logon-User: 23 Nov 2026 09:00:00</title>",
                "<title>Logoff-User: 23 Nov 2026 17:00:00</title>",
                // Where the block names no events, what they are.
                "<title>Start: 23 Nov 2026 10:00:00</title>", "<title>End: 23 Nov 2026 10:30:00</title>");
        // A milestone is a marker of its own, with no edges.
        assertThat(count(AiGanttChart.svg(MermaidGanttParser.parse("gantt\n  m :milestone, 2026-11-23, 0d")),
                "<path class=\"ai-gantt-edge")).isZero();
        // An end past the span drawn isn't drawn.
        final long nine = MermaidGantt.wallClock(2026, 11, 23, 9, 0, 0, 0);
        final String zoomed = AiGanttChart.svg(MermaidGanttParser.parse("""
                gantt
                    dateFormat YYYY-MM-DD HH:mm:ss
                    logged on :2026-11-23 09:00:00, 2026-11-23 17:00:00
                    %% events Logon-User | Logoff-User
                """), new long[]{nine - HOUR, nine + HOUR}, AiGanttChart.WIDTH);
        assertThat(zoomed).contains("Logon-User").doesNotContain("Logoff-User");
    }

    @Test
    void aNamePickedOutHasItsBarsHighlightedAndTheOthersDimmedAndTheLegendSaysSo() {
        final MermaidGantt gantt = MermaidGanttParser.parse("""
                gantt
                    Chrome :2026-11-23, 1d
                    Word :2026-11-24, 1d
                    Chrome :2026-11-25, 1d
                """);

        // Bars, markers and legend items say which name they are of, for a click to pick it out.
        final String plain = AiGanttChart.svg(gantt, null, AiGanttChart.WIDTH, null);
        assertThat(plain).contains("class=\"ai-gantt-bar ai-gantt-c1\" " + AiGanttChart.SERIES_ATTRIBUTE
                                   + "=\"ai-gantt-c1\"")
                .contains("<g class=\"ai-gantt-legend-item\" " + AiGanttChart.SERIES_ATTRIBUTE + "=\"ai-gantt-c2\" "
                          + "tabindex=\"0\" role=\"button\" aria-pressed=\"false\" aria-label=\"Pick out Word\">")
                .doesNotContain("ai-gantt--dimmed");

        final String picked = AiGanttChart.svg(gantt, null, AiGanttChart.WIDTH, "ai-gantt-c2");
        assertThat(count(picked, "<rect class=\"ai-gantt-bar ai-gantt-c1 ai-gantt--dimmed\"")).isEqualTo(2);
        assertThat(count(picked, "<rect class=\"ai-gantt-bar ai-gantt-c2\"")).isOne();
        assertThat(picked).contains(AiGanttChart.SERIES_ATTRIBUTE + "=\"ai-gantt-c2\" tabindex=\"0\" "
                                    + "role=\"button\" aria-pressed=\"true\"")
                .contains("class=\"ai-gantt-legend-item ai-gantt--dimmed\" " + AiGanttChart.SERIES_ATTRIBUTE
                          + "=\"ai-gantt-c1\"");
        // Picking it again, or the background, clears it.
        assertThat(AiGanttChart.toggled("ai-gantt-c2", "ai-gantt-c2")).isNull();
        assertThat(AiGanttChart.toggled("ai-gantt-c1", "ai-gantt-c2")).isEqualTo("ai-gantt-c2");
        assertThat(AiGanttChart.toggled(null, "ai-gantt-c2")).isEqualTo("ai-gantt-c2");
        assertThat(AiGanttChart.toggled("ai-gantt-c2", null)).isNull();
    }

    private static Task task(final String name, final long start, final long end) {
        return new Task(name, null, List.of(), start, end);
    }

    // The titles shown in bars.
    private static List<String> barLabels(final String svg) {
        final Matcher label = Pattern.compile("<text class=\"ai-gantt-bar-label\"[^>]*>([^<]*)</text>").matcher(svg);
        final List<String> labels = new ArrayList<>();
        while (label.find()) {
            labels.add(label.group(1));
        }
        return labels;
    }

    private static int count(final String text, final String part) {
        int count = 0;
        int at = text.indexOf(part);
        while (at >= 0) {
            count++;
            at = text.indexOf(part, at + part.length());
        }
        return count;
    }
}
