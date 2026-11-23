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

/// Zooming the chat's charts: a drag's span in time, the tasks a span shows and how much of each, nested zooms,
/// resetting, the ticks for a span, and a drag too short to zoom.
class TestAiGanttZoom {

    private static final long SECOND = 1_000L;
    private static final long MINUTE = 60 * SECOND;
    private static final long HOUR = 60 * MINUTE;
    private static final long DAY = MermaidGantt.MILLIS_PER_DAY;
    private static final long NINE = MermaidGantt.wallClock(2026, 11, 23, 9, 0, 0, 0);
    private static final String BLOCK = """
            gantt
                title Activity of alice (times in UTC)
                dateFormat YYYY-MM-DD HH:mm:ss
                axisFormat %H:%M
                section host-1
                Chrome :2026-11-23 09:00:00, 2026-11-23 10:00:00
                USB copy :milestone, 2026-11-23 10:30:00, 0d
                Word :2026-11-23 11:00:00, 2026-11-23 12:00:00
                section host-2
                Excel :2026-11-23 13:00:00, 2026-11-23 15:00:00
                Logon :milestone, 2026-11-23 17:00:00, 0d
            """;

    @Test
    void thePointerIsMappedToTheChartsPixelsAndTimes() {
        // A chart drawn 720 wide, shown 360 wide from x 100 on the page: scaled by a half.
        assertThat(AiGanttZoom.toSvg(280, 100, 360, 720)).isEqualTo(360.0);
        assertThat(AiGanttZoom.toSvg(150, 100, 0, 720)).isEqualTo(50.0);

        final Scale scale = new Scale(NINE, NINE + 10 * HOUR, 100, 1100);
        assertThat(AiGanttZoom.timeAt(scale, 100)).isEqualTo(NINE);
        assertThat(AiGanttZoom.timeAt(scale, 600)).isEqualTo(NINE + 5 * HOUR);
        // Held to the span outside the plot.
        assertThat(AiGanttZoom.timeAt(scale, 0)).isEqualTo(NINE);
        assertThat(AiGanttZoom.timeAt(scale, 5000)).isEqualTo(NINE + 10 * HOUR);
        // And the other way, as the chart draws it.
        assertThat(scale.position(AiGanttZoom.timeAt(scale, 350))).isEqualTo(350.0);
    }

    @Test
    void aDragGivesTheSpanItCoveredEarlierFirstAndHeldToThePlot() {
        final Scale scale = new Scale(NINE, NINE + 10 * HOUR, 100, 1100);

        assertThat(AiGanttZoom.dragRange(scale, 200, 400)).containsExactly(NINE + HOUR, NINE + 3 * HOUR);
        // Dragged right to left: the same span.
        assertThat(AiGanttZoom.dragRange(scale, 400, 200)).containsExactly(NINE + HOUR, NINE + 3 * HOUR);
        // Past the plot's ends (over the lane labels, or off the chart): held to the period.
        assertThat(AiGanttZoom.dragRange(scale, 20, 300)).containsExactly(NINE, NINE + 2 * HOUR);
        assertThat(AiGanttZoom.dragRange(scale, 900, 2000)).containsExactly(NINE + 8 * HOUR, NINE + 10 * HOUR);
    }

    @Test
    void aDragUnderTheMinimumIsAClickNotAZoom() {
        final Scale scale = new Scale(NINE, NINE + 10 * HOUR, 100, 1100);

        assertThat(AiGanttZoom.dragRange(scale, 300, 300)).isNull();
        assertThat(AiGanttZoom.dragRange(scale, 300, 304.9)).isNull();
        assertThat(AiGanttZoom.dragRange(scale, 304.9, 300)).isNull();
        // Wholly outside the plot, however long.
        assertThat(AiGanttZoom.dragRange(scale, 0, 90)).isNull();
        assertThat(AiGanttZoom.dragRange(scale, 300, 305)).isNotNull();
    }

    @Test
    void aSpanIsNeverShorterThanTheAxissLeastStep() {
        // A minute across a thousand pixels: five pixels is 300 ms, widened to a second.
        final Scale scale = new Scale(NINE, NINE + MINUTE, 0, 1000);

        assertThat(AiGanttZoom.dragRange(scale, 100, 105)).containsExactly(NINE + 6 * SECOND,
                NINE + 7 * SECOND);
        // At the period's end, widened back into it.
        assertThat(AiGanttZoom.dragRange(scale, 995, 1000)).containsExactly(NINE + MINUTE - SECOND, NINE + MINUTE);
    }

    @Test
    void zoomingAgainWithinAZoomedChartNarrowsFurther() {
        final MermaidGantt gantt = MermaidGanttParser.parse(BLOCK);
        final long[] whole = AiGanttChart.range(gantt);
        assertThat(whole).containsExactly(NINE, NINE + 8 * HOUR);
        final Scale wholeScale = new Scale(whole[0], whole[1], 0, 800);

        final long[] first = AiGanttZoom.dragRange(wholeScale, 100, 300);
        assertThat(first).containsExactly(NINE + HOUR, NINE + 3 * HOUR);
        // The same pixels on the zoomed chart are a quarter of the time.
        final Scale zoomed = new Scale(first[0], first[1], 0, 800);
        final long[] second = AiGanttZoom.dragRange(zoomed, 100, 300);
        assertThat(second).containsExactly(NINE + HOUR + 15 * MINUTE, NINE + HOUR + 45 * MINUTE);
        assertThat(second[0]).isGreaterThanOrEqualTo(first[0]);
        assertThat(second[1]).isLessThanOrEqualTo(first[1]);
    }

    @Test
    void aSpanShowsTheBarsRunningIntoItAndTheMarkersInIt() {
        final Task bar = task(NINE + HOUR, NINE + 2 * HOUR);
        final Task marker = new Task("m", null, List.of(Task.MILESTONE), NINE + 3 * HOUR, NINE + 3 * HOUR);

        // A bar overlapping either end, or holding the span, or inside it.
        assertThat(AiGanttZoom.isVisible(bar, NINE, NINE + HOUR + MINUTE)).isTrue();
        assertThat(AiGanttZoom.isVisible(bar, NINE + 2 * HOUR - MINUTE, NINE + 3 * HOUR)).isTrue();
        assertThat(AiGanttZoom.isVisible(bar, NINE + HOUR + MINUTE, NINE + HOUR + 2 * MINUTE)).isTrue();
        assertThat(AiGanttZoom.isVisible(bar, NINE, NINE + 3 * HOUR)).isTrue();
        // Wholly outside, or only touching an end.
        assertThat(AiGanttZoom.isVisible(bar, NINE + 2 * HOUR, NINE + 3 * HOUR)).isFalse();
        assertThat(AiGanttZoom.isVisible(bar, NINE, NINE + HOUR)).isFalse();
        assertThat(AiGanttZoom.isVisible(bar, NINE + 5 * HOUR, NINE + 6 * HOUR)).isFalse();
        // A marker in the span, its ends included; not one outside it.
        assertThat(AiGanttZoom.isVisible(marker, NINE + 2 * HOUR, NINE + 4 * HOUR)).isTrue();
        assertThat(AiGanttZoom.isVisible(marker, NINE + 3 * HOUR, NINE + 4 * HOUR)).isTrue();
        assertThat(AiGanttZoom.isVisible(marker, NINE + 2 * HOUR, NINE + 3 * HOUR)).isTrue();
        assertThat(AiGanttZoom.isVisible(marker, NINE, NINE + 2 * HOUR)).isFalse();
        assertThat(AiGanttZoom.visible(List.of(bar, marker), NINE + HOUR + 30 * MINUTE, NINE + 2 * HOUR + 30 * MINUTE))
                .containsExactly(bar);
    }

    @Test
    void aBarIsCutToTheSpan() {
        final Task bar = task(NINE + HOUR, NINE + 3 * HOUR);

        assertThat(AiGanttZoom.clip(bar, NINE + 2 * HOUR, NINE + 4 * HOUR)).containsExactly(NINE + 2 * HOUR,
                NINE + 3 * HOUR);
        assertThat(AiGanttZoom.clip(bar, NINE, NINE + 2 * HOUR)).containsExactly(NINE + HOUR, NINE + 2 * HOUR);
        assertThat(AiGanttZoom.clip(bar, NINE + 90 * MINUTE, NINE + 2 * HOUR)).containsExactly(NINE + 90 * MINUTE,
                NINE + 2 * HOUR);
        assertThat(AiGanttZoom.clip(bar, NINE, NINE + 5 * HOUR)).containsExactly(NINE + HOUR, NINE + 3 * HOUR);
    }

    @Test
    void aZoomedChartDrawsOnlyWhatTheSpanShowsCutToThePlot() {
        final MermaidGantt gantt = MermaidGanttParser.parse(BLOCK);
        // 09:30 to 11:30: half of Chrome, the USB copy, half of Word; nothing on host-2.
        final long[] view = {NINE + 30 * MINUTE, NINE + 2 * HOUR + 30 * MINUTE};

        final String svg = AiGanttChart.svg(gantt, view, AiGanttChart.WIDTH);

        assertThat(count(svg, "<rect class=\"ai-gantt-bar ")).isEqualTo(2);
        assertThat(count(svg, "<path class=\"ai-gantt-marker ")).isEqualTo(1);
        assertThat(svg).contains("<title>Chrome: 23 Nov 09:00:00 to 23 Nov 10:00:00 (1 h)</title>")
                .contains("<title>USB copy: 23 Nov 10:30:00</title>")
                .doesNotContain("<title>Excel", "<title>Logon")
                // Both lanes still drawn, so the chart keeps its shape; the legend of all the names, its colours.
                .contains(">host-1</text>", ">host-2</text>", ">Excel</text>", ">Logon</text>")
                .contains("data-from=\"" + view[0] + "\"", "data-to=\"" + view[1] + "\"")
                .contains(">09:30</text>", ">11:30</text>")
                .doesNotContain(">09:00</text>", ">12:00</text>");
        // Every bar inside the plot (162 to 700 at 720 wide), cut at its edges.
        final Matcher bar = Pattern.compile("<rect class=\"ai-gantt-bar [^\"]+\" x=\"([0-9.]+)\" y=\"[0-9]+\" "
                                            + "width=\"([0-9.]+)\"").matcher(svg);
        final List<Double> lefts = new ArrayList<>();
        final List<Double> rights = new ArrayList<>();
        while (bar.find()) {
            lefts.add(Double.parseDouble(bar.group(1)));
            rights.add(Double.parseDouble(bar.group(1)) + Double.parseDouble(bar.group(2)));
        }
        assertThat(lefts).containsExactlyInAnyOrder(162.0, 565.5);
        assertThat(rights).containsExactlyInAnyOrder(296.5, 700.0);
    }

    @Test
    void resettingDrawsTheWholePeriodAgain() {
        final MermaidGantt gantt = MermaidGanttParser.parse(BLOCK);
        final long[] view = {NINE + 30 * MINUTE, NINE + HOUR};
        final String zoomed = AiGanttChart.svg(gantt, view, AiGanttChart.WIDTH);
        final String attribute = AiGanttZoom.toAttribute(view);
        assertThat(AiGanttZoom.fromAttribute(attribute)).containsExactly(view);

        // The whole period: no span held, so the chart is drawn as it first was.
        assertThat(AiGanttZoom.toAttribute(null)).isNull();
        assertThat(AiGanttZoom.fromAttribute(null)).isNull();
        assertThat(AiGanttZoom.fromAttribute("")).isNull();
        final String reset = AiGanttChart.svg(gantt, AiGanttZoom.fromAttribute(null), AiGanttChart.WIDTH);
        assertThat(reset).isEqualTo(AiGanttChart.svg(gantt)).isNotEqualTo(zoomed)
                .contains("data-from=\"" + NINE + "\"", "data-to=\"" + (NINE + 8 * HOUR) + "\"");
        assertThat(count(reset, "<rect class=\"ai-gantt-bar ")).isEqualTo(3);
        assertThat(count(reset, "<path class=\"ai-gantt-marker ")).isEqualTo(2);
    }

    @Test
    void aSpanThatCantBeReadIsTheWholePeriod() {
        assertThat(AiGanttZoom.fromAttribute("1,x")).isNull();
        assertThat(AiGanttZoom.fromAttribute("5,5")).isNull();
        assertThat(AiGanttZoom.fromAttribute("9,1")).isNull();
        assertThat(AiGanttZoom.fromAttribute(",1")).isNull();
        assertThat(AiGanttZoom.fromAttribute("12")).isNull();
    }

    @Test
    void ticksForASpanOfMinutesShowSeconds() {
        final long from = NINE + 10 * MINUTE;
        final List<Long> ticks = AiGanttChart.ticks(from, from + 2 * MINUTE, AiGanttChart.MAX_TICKS);

        // The chart's %H:%M would write the same tick twice, so seconds are added.
        assertThat(AiGanttChart.tickFormat("%H:%M", ticks)).isEqualTo("%H:%M:%S");
        assertThat(ticks).extracting(tick -> AiGanttChart.format(tick, "%H:%M:%S")).containsExactly(
                "09:10:00", "09:10:30", "09:11:00", "09:11:30", "09:12:00");
    }

    @Test
    void ticksForASpanOfHoursShowHoursAndMinutes() {
        final List<Long> ticks = AiGanttChart.ticks(NINE, NINE + 3 * HOUR, AiGanttChart.MAX_TICKS);

        // A chart over a week writes days; zoomed to hours, the hours and minutes.
        assertThat(AiGanttChart.tickFormat("%d %b", ticks)).isEqualTo("%H:%M");
        // The chart's own format, where it writes each tick differently.
        assertThat(AiGanttChart.tickFormat("%H:%M", ticks)).isEqualTo("%H:%M");
        assertThat(AiGanttChart.tickFormat("%d %b %H:%M", ticks)).isEqualTo("%d %b %H:%M");
        // Across midnight, the day too.
        final List<Long> overnight = AiGanttChart.ticks(NINE + 13 * HOUR, NINE + 17 * HOUR, AiGanttChart.MAX_TICKS);
        assertThat(AiGanttChart.tickFormat("%d %b", overnight)).isEqualTo("%d %b %H:%M");
        // Mermaid's default, a date, for a chart that gives none.
        assertThat(AiGanttChart.tickFormat(null, ticks)).isEqualTo("%H:%M");
    }

    @Test
    void ticksForASpanOfDaysShowTheDay() {
        final List<Long> ticks = AiGanttChart.ticks(NINE, NINE + 5 * DAY, AiGanttChart.MAX_TICKS);

        assertThat(AiGanttChart.tickFormat("%d %b", ticks)).isEqualTo("%d %b");
        // A chart's %H:%M over days writes midnight each time.
        assertThat(AiGanttChart.tickFormat("%H:%M", ticks)).isEqualTo("%d %b");
        // One tick, or none: the chart's own.
        assertThat(AiGanttChart.tickFormat("%H:%M", List.of(NINE))).isEqualTo("%H:%M");
    }

    @Test
    void aWiderChartHasMoreTicksAndALongerPlot() {
        final MermaidGantt gantt = MermaidGanttParser.parse(BLOCK);
        assertThat(AiGanttChart.maxTicks(new Scale(0, 1, 162, 700))).isEqualTo(AiGanttChart.MAX_TICKS);
        assertThat(AiGanttChart.maxTicks(new Scale(0, 1, 162, 1580))).isEqualTo(20);

        final String wide = AiGanttChart.svg(gantt, null, 1600);
        assertThat(wide).contains("viewBox=\"0 0 1600 ", "data-plot-right=\"1580\"", "data-width=\"1600\"");
        assertThat(count(wide, "class=\"ai-gantt-tick\"")).isGreaterThan(count(AiGanttChart.svg(gantt),
                "class=\"ai-gantt-tick\""));
        // Never narrower than the least width.
        assertThat(AiGanttChart.svg(gantt, null, 100)).contains("viewBox=\"0 0 " + AiGanttChart.MIN_WIDTH + " ");
    }

    private static Task task(final long start, final long end) {
        return new Task("t", null, List.of(), start, end);
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
