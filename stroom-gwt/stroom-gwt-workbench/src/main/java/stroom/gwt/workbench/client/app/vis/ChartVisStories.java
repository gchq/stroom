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

package stroom.gwt.workbench.client.app.vis;

import stroom.gwt.workbench.client.StroomThemeDecorator;
import stroom.gwt.workbench.framework.client.play.EventInit;
import stroom.gwt.workbench.framework.client.play.Play;
import stroom.gwt.workbench.framework.client.play.Query;
import stroom.gwt.workbench.framework.client.play.TextMatch;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;


/// The stories of the content pack's charts: `BarChart`, `LineChart`, `StackedArea`,
/// `TimeSeries`, `Scatter`, `Bubble`, `Doughnut`, `TreeMap` and `WordCloud`. See [VisStories].
public final class ChartVisStories {

    // The asset-based bar chart's bars
    private static final String BAR = "rect.vis-bar-chart__bar";

    private static final String[] HOSTS = {"web-01", "web-02", "db-01"};
    private static final String[] STATUSES = {"200 OK", "301 Moved", "404 Not Found", "500 Error"};

    // Requests by host and status (also the data of VisAccessibilityStories and VisSettingsStories)
    public static final VisTable REQUESTS_BY_STATUS = requestsByStatus();
    // Requests each hour of a day, by host
    private static final VisTable HOURLY_REQUESTS = hourlyRequests();

    // The asset-based line chart's lines and points
    private static final String LINE = "path.vis-line-chart__line";
    private static final String POINT = "circle.vis-line-chart__point";
    // The asset-based time series' lines and points, and the time its test data covers
    private static final String TS_LINE = "path.vis-time-series__line";
    private static final String TS_POINT = "circle.vis-time-series__point";
    private static final String TS_ALL = "2026-10-12T00:00:00.000Z to 2026-10-12T23:00:00.000Z";
    // The asset-based tree map's boxes
    private static final String TREE_BOX = "div.vis-treemap__box";
    // The asset-based doughnut's slices
    private static final String SLICE = "path.vis-doughnut__slice";

    // The asset-based word cloud's words
    private static final String WORD = "text.vis-word-cloud__word";
    // The asset-based bubble chart's bubbles, and its series' bubbles around them
    private static final String BUBBLE = "circle.vis-bubble__bubble";
    private static final String BUBBLE_GROUP = "circle.vis-bubble__group";
    // A zooming X axis (Scatter, LineChart), and its note of what is shown
    private static final String AXIS_ZOOM = "g.vis-axis-zoom";
    private static final String AXIS_ZOOM_NOTE = "text.vis-axis-zoom__note";
    // The asset-based scatter chart's points, and some of responseSizes()'s by name
    private static final String SCATTER_POINT = "circle.vis-scatter__point";
    private static final String DB_01_FIRST_RESPONSE = "db-01, Duration: 74, Bytes: 8,000";
    private static final String WEB_01_FIRST_RESPONSE = "web-01, Duration: 25, Bytes: 2,000";
    // The asset-based stacked area chart's areas and points
    private static final String AREA = "path.vis-stacked-area__area";
    private static final String AREA_POINT = "circle.vis-stacked-area__point";
    // Some of HOURLY_REQUESTS's points, stacked, by name
    private static final String DB_01_STACKED_AT_0 =
            "db-01, Hour: 2026-10-12T00:00:00.000Z, Requests: 26, Total Requests: 174";
    private static final String WEB_02_STACKED_AT_0 =
            "web-02, Hour: 2026-10-12T00:00:00.000Z, Requests: 88, Total Requests: 174";
    // Some of HOURLY_REQUESTS's points, by name
    private static final String DB_01_AT_0 = "db-01, Hour: 2026-10-12T00:00:00.000Z, Requests: 26";
    private static final String DB_01_AT_1 = "db-01, Hour: 2026-10-12T01:00:00.000Z, Requests: 63";
    private static final String WEB_01_AT_1 = "web-01, Hour: 2026-10-12T01:00:00.000Z, Requests: 97";

    private ChartVisStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        registry.component(VisStories.TITLE + "BarChart", ChartVisStories.class)
                .layout(StoryLayout.FULLSCREEN)
                .tags(VisStories.TAG)
                // In a grid of charts, a selected bar says which chart it is in (gridSeries), and has a
                // key of its chart's own
                .story("GridSelectionPayload", VisStories.story("BarChart",
                        VisStories.settings("x", "Status", "y", "Requests", "gridSeries", "Host"), REQUESTS_BY_STATUS))
                .withPlay(play -> {
                    VisStories.drawn(play, BAR);
                    final Query bar = play.withinFrame(VisStories.frame(play)).querySelectorAll(BAR).first();
                    VisStories.expectSelectionOf(play, bar,
                            "[{\"key\":\"db-01~#~~#~200 OK\",\"series\":\"\",\"x\":\"200 OK\",\"val"
                            + "ue\":1380,\"y\":1380,\"gridSeries\":\"db-01\"}]");
                    VisStories.expectNoProblems(play);
                })
                // What Stroom is told is selected: a click selects a bar, Ctrl+Click adds another, a
                // click selects just the one clicked, and a click on nothing selects nothing (an
                // empty selection). Each bar is told as the legacy chart told it
                .story("Selection", VisStories.story("BarChart",
                        VisStories.settings("x", "Status", "y", "Requests"), requestsForHost("web-01")))
                .withPlay(play -> {
                    VisStories.drawn(play, BAR, 4);
                    final Play inFrame = play.withinFrame(VisStories.frame(play));
                    final Query ok = inFrame.querySelector(BAR + "[aria-label^='Status: 200 OK']");
                    final Query moved = inFrame.querySelector(BAR + "[aria-label^='Status: 301 Moved']");
                    final String okItem = "{\"key\":\"~#~200 OK\",\"series\":\"\",\"x\":\"200 OK\",\"value\":1200,"
                                          + "\"y\":1200}";
                    final String movedItem = "{\"key\":\"~#~301 Moved\",\"series\":\"\",\"x\":\"301 Moved\","
                                             + "\"value\":425,\"y\":425}";
                    VisStories.recordSelections(play);
                    play.fireEvent().click(ok);
                    VisStories.expectSelection(play, "[" + okItem + "]");
                    play.fireEvent().click(moved, EventInit.create().ctrlKey());
                    VisStories.expectSelection(play, "[" + okItem + "," + movedItem + "]");
                    play.expect(ok.attribute("aria-label")).toMatch(", selected");
                    play.fireEvent().click(moved);
                    VisStories.expectSelection(play, "[" + movedItem + "]");
                    play.fireEvent().click(inFrame.querySelector("svg.vis-bar-chart"));
                    VisStories.expectSelection(play, "[]");
                    play.expect(inFrame.querySelectorAll(".vis-bar-chart__bar--selected")).toHaveLength(0);
                    VisStories.expectNoProblems(play);
                })
                // A bar for each status of each host, the hosts along the bottom
                .story("Ordinal", VisStories.story("BarChart",
                        VisStories.settings("x", "Host", "y", "Requests", "series", "Status"), REQUESTS_BY_STATUS))
                .withPlay(play -> {
                    VisStories.drawn(play, BAR, 12);
                    // The axes are labelled, as the controls' defaults (Display X Axis) say, though
                    // the data arrived before the visualisation, and so its defaults, had loaded
                    final Query labels = play.withinFrame(VisStories.frame(play)).querySelectorAll(".tick text");
                    play.expect("the axes' labels", () -> labels.textContents().get()).toContain("db-01");
                    final Play inFrame = play.withinFrame(VisStories.frame(play));
                    final Query axisLines = inFrame.querySelectorAll(".vis-axis__line");
                    if (StroomThemeDecorator.isForcedColours()) {
                        // With a high contrast theme, which leaves SVG's colours alone, the axes,
                        // their labels and the bars' outlines are in its text colour, which the
                        // browser gives the visualisation's element
                        final Query vis = inFrame.querySelector(".vis");
                        final Query label = inFrame.querySelectorAll(".vis-axis__label").first();
                        final Query bar = inFrame.querySelectorAll(BAR).first();
                        play.expect("the text's colour, then the axis line's, a label's and a bar's outline's",
                                        () -> List.of(vis.computedStyle("color").get(),
                                                axisLines.first().computedStyle("stroke").get(),
                                                label.computedStyle("fill").get(),
                                                bar.computedStyle("stroke").get()))
                                .toSatisfy("all the same", colours -> new HashSet<>((List<?>) colours).size() == 1);
                    } else {
                        // The axes' lines are the theme's axis colour (--vis-axis__color), not the
                        // black of the legacy '.vis-axis path' rule (vis-legacy.css, which only
                        // script-based visualisations load)
                        play.expect(axisLines.first().computedStyle("stroke")).toBe(StroomThemeDecorator.isDark()
                                ? "rgba(255, 255, 255, 0.54)"
                                : "rgba(0, 0, 0, 0.54)");
                    }
                    // The chart is described for screen readers
                    final Query chart = play.withinFrame(VisStories.frame(play)).querySelector("svg.vis-bar-chart");
                    play.waitFor(() -> play.expect(chart).toHaveAttribute("aria-label",
                            "Bar chart of Requests by Host: 12 bars in 4 series, highest Requests 1,380 at db-01"));
                    play.expect(chart).toHaveAttribute("aria-roledescription", "chart");
                    // A screen reader hears the description and the bars' names, not the axes'
                    // labels as a run of words, and a chart on its own isn't a region named 'Chart'
                    play.expect(inFrame.querySelectorAll(".vis-axis").first()).toHaveAttribute("aria-hidden", "true");
                    play.expect(inFrame.queryByRole("region")).toBeNull();
                    VisStories.expectNoProblems(play);
                })
                // The legend is opened from Stroom's toolbar above the chart, and each of its
                // entries shows or hides a series
                .story("Legend", VisStories.story("BarChart",
                        VisStories.settings("x", "Host", "y", "Requests", "series", "Status"), REQUESTS_BY_STATUS))
                .withPlay(play -> {
                    final Query bars = VisStories.drawn(play, BAR, 12);
                    final Play inFrame = play.withinFrame(VisStories.frame(play));
                    final Query legend = inFrame.querySelector(".vis-legend");
                    play.expect(legend).not().toBeVisible();
                    // A lone button: a group of buttons is only a toolbar with two or more
                    final Query showLegend = play.screen().getByRole("button", "Show legend");
                    play.waitFor(() -> play.expect(showLegend).toBeVisible());
                    play.expect(showLegend).toHaveAttribute("aria-pressed", "false");
                    play.click(showLegend);
                    play.waitFor(() -> play.expect(legend).toBeVisible());
                    play.waitFor(() -> play.expect(showLegend).toHaveAttribute("aria-pressed", "true"));

                    final Query moved = inFrame.getByRole("button", "301 Moved");
                    play.expect(moved).toHaveAttribute("aria-pressed", "true");
                    if (StroomThemeDecorator.isForcedColours()) {
                        // With a high contrast theme, a swatch keeps its series' colour, which the
                        // browser would otherwise make the background's, edged in the theme's text
                        // colour
                        final Query swatch = inFrame.querySelectorAll(".vis-legend__swatch").first();
                        final Query vis = inFrame.querySelector(".vis");
                        play.expect("the background's colour and a swatch's",
                                        () -> List.of(vis.computedStyle("background-color").get(),
                                                swatch.computedStyle("background-color").get()))
                                .toSatisfy("different", colours -> new HashSet<>((List<?>) colours).size() == 2);
                        play.expect("the text's colour and a swatch's edge's",
                                        () -> List.of(vis.computedStyle("color").get(),
                                                swatch.computedStyle("border-top-color").get()))
                                .toSatisfy("the same", colours -> new HashSet<>((List<?>) colours).size() == 1);
                    }
                    play.click(moved);
                    play.waitFor(() -> play.expect(bars).toHaveLength(9));
                    play.expect(moved).toHaveAttribute("aria-pressed", "false");
                    play.click(moved);
                    play.waitFor(() -> play.expect(bars).toHaveLength(12));

                    play.click(showLegend);
                    play.waitFor(() -> play.expect(legend).not().toBeVisible());
                    VisStories.expectNoProblems(play);
                })
                // Two bar charts that colour by the Status field in different ways, one by its series
                // and one (a chart for each host) by its categories, give a status the same colour:
                // the dashboard's colour list's, here the preferred colour of 200 OK of Status
                .story("SharedColours", VisStories.story("BarChart", List.of(
                                VisStories.settings("x", "Host", "y", "Requests", "series", "Status"),
                                VisStories.settings("x", "Status", "y", "Requests", "gridSeries", "Host")),
                        REQUESTS_BY_STATUS))
                .withPlay(play -> {
                    final Query frames = play.screen().querySelectorAll(VisStories.FRAME);
                    play.waitFor(() -> play.expect(frames).toHaveLength(2));
                    final Query bySeries = play.withinFrame(frames.nth(0))
                            .querySelector(BAR + "[aria-label^='200 OK, Host: db-01']");
                    final Query byCategory = play.withinFrame(frames.nth(1))
                            .querySelector(BAR + "[aria-label^='Status: 200 OK, Requests: 1,380']");
                    final String ok = StroomThemeDecorator.isDark()
                            ? "rgb(232, 107, 209)"
                            : "rgb(176, 54, 154)";
                    play.waitFor(() -> play.expect(bySeries.computedStyle("fill")).toBe(ok));
                    play.waitFor(() -> play.expect(byCategory.computedStyle("fill")).toBe(ok));
                    VisStories.expectNoProblems(play);
                })
                // A bar for each hour, a series for each host
                .story("DateTime", VisStories.story("BarChart",
                        VisStories.settings("x", "Hour", "y", "Requests", "series", "Host", "bucketSize", "hour"),
                        HOURLY_REQUESTS))
                .withPlay(play -> {
                    VisStories.drawn(play, BAR, 72);
                    VisStories.expectNoProblems(play);
                })
                // A chart for each host
                .story("GridSeries", VisStories.story("BarChart",
                        VisStories.settings("x", "Status", "y", "Requests", "gridSeries", "Host"), REQUESTS_BY_STATUS))
                .withPlay(play -> {
                    VisStories.drawn(play, BAR, 12);
                    // Each chart is named by its host, with a button to zoom in on it
                    final Play inFrame = play.withinFrame(VisStories.frame(play));
                    final Query names = inFrame.querySelectorAll(".vis-grid__name");
                    play.expect(inFrame.getByRole("region", "web-01")).toBeInTheDocument();
                    play.expect("the charts' names", () -> names.textContents().get())
                            .toEqual(List.of("db-01", "web-01", "web-02"));
                    play.click(inFrame.getByRole("button", "Zoom in on web-01"));
                    play.waitFor(() -> play.expect(names).toHaveLength(1));
                    play.expect(names.first()).toHaveTextContent("web-01");
                    play.click(inFrame.getByRole("button", "Show all charts"));
                    play.waitFor(() -> play.expect(names).toHaveLength(3));
                    VisStories.expectNoProblems(play);
                })
                // The Outline fill style: each bar a line of its colour around a fade of it, as
                // StackedArea's series are
                .story("Outline", VisStories.story("BarChart",
                        VisStories.settings("x", "Host", "y", "Requests", "series", "Status", "fillStyle", "Outline"),
                        REQUESTS_BY_STATUS))
                .withPlay(play -> {
                    final Query bars = VisStories.drawn(play, BAR, 12);
                    play.expect(bars.first().computedStyle("stroke-width")).toBe("2px");
                    VisStories.expectNoProblems(play);
                })
                .story("OutlineDateTime", VisStories.story("BarChart",
                        VisStories.settings("x", "Hour", "y", "Requests", "series", "Host", "bucketSize", "hour",
                                "fillStyle", "Outline"),
                        HOURLY_REQUESTS))
                .withPlay(play -> {
                    VisStories.drawn(play, BAR, 72);
                    VisStories.expectNoProblems(play);
                })
                // A search that found nothing: 'No data' where the bars would be, without axes,
                // which would have nothing to measure
                .story("NoData", VisStories.story("BarChart",
                        VisStories.settings("x", "Host", "y", "Requests", "series", "Status"),
                        VisTable.builder().text("Host").text("Status").number("Requests").build()))
                .withPlay(play -> {
                    final Query chart = VisStories.drawn(play, "svg.vis-bar-chart", 1).first();
                    final Play inFrame = play.withinFrame(VisStories.frame(play));
                    play.waitFor(() -> play.expect(chart).toHaveAttribute("aria-label",
                            "Bar chart of Requests by Host: no data"));
                    play.expect(inFrame.querySelector(".vis-empty")).toHaveTextContent("No data");
                    play.expect(inFrame.querySelectorAll(BAR)).toHaveLength(0);
                    play.expect(inFrame.querySelectorAll(".vis-axis__label")).toHaveLength(0);
                    VisStories.expectNoProblems(play);
                })
                // One row: one bar, no wider than a category's band
                .story("OneBar", VisStories.story("BarChart",
                        VisStories.settings("x", "Host", "y", "Requests"),
                        VisTable.builder().text("Host").number("Requests").row("web-01", 1200).build()))
                .withPlay(play -> {
                    final Query bar = VisStories.drawn(play, BAR, 1).first();
                    final Query chart = play.withinFrame(VisStories.frame(play)).querySelector("svg.vis-bar-chart");
                    play.expect(chart).toHaveAttribute("aria-label",
                            "Bar chart of Requests by Host: 1 bar, highest Requests 1,200 at web-01");
                    play.expect("the bar's height", () -> bar.height().get()).toBeGreaterThan(100);
                    play.expect(bar).toHaveAttribute("aria-label", "Host: web-01, Requests: 1,200");
                    VisStories.expectNoProblems(play);
                })
                // One time without a bucket size, so nothing to say how wide a bar is: it is still
                // wide enough to see and point at
                .story("OneTime", VisStories.story("BarChart",
                        VisStories.settings("x", "Hour", "y", "Requests"),
                        VisTable.builder().date("Hour").number("Requests").row("2026-10-12T09:00:00Z", 420).build()))
                .withPlay(play -> {
                    final Query bar = VisStories.drawn(play, BAR, 1).first();
                    play.expect("the bar's width", () -> bar.width().get()).toBeGreaterThanOrEqual(10);
                    VisStories.expectNoProblems(play);
                })
                // Changes that go up and down: a fall hangs below zero, from where a rise starts
                .story("Negative", VisStories.story("BarChart",
                        VisStories.settings("x", "Host", "y", "Change"),
                        VisTable.builder().text("Host").number("Change")
                                .row("web-01", 120).row("web-02", -80).row("db-01", 40).row("db-02", -200)
                                .build()))
                .withPlay(play -> {
                    // In the hosts' order: db-01's rise, then db-02's fall
                    final Query bars = VisStories.drawn(play, BAR, 4);
                    final Query rise = bars.nth(0);
                    final Query fall = bars.nth(1);
                    play.expect("where the fall starts less where the rise ends",
                            () -> fall.top().get() - rise.bottom().get()).toBeCloseTo(0, 0);
                    play.expect("the fall's height", () -> fall.height().get()).toBeGreaterThan(10);
                    final Query chart = play.withinFrame(VisStories.frame(play)).querySelector("svg.vis-bar-chart");
                    // With a minus sign (U+2212), which screen readers say as 'minus'
                    play.expect(chart).toHaveAttribute("aria-label",
                            "Bar chart of Change by Host: 4 bars, highest Change 120 at web-01, "
                            + "lowest Change −200 at db-02");
                    play.expect(fall).toHaveAttribute("aria-label", "Host: db-02, Change: −200");
                    VisStories.expectNoProblems(play);
                })
                // Missing values: a row without a value has no bar, as there is nothing to say how
                // high it is, nor one without a host, as there is nowhere to put it (rather than
                // in a band without a name). A row without a status is in a series of its own
                .story("Nulls", VisStories.story("BarChart",
                        VisStories.settings("x", "Host", "y", "Requests", "series", "Status"),
                        VisTable.builder().text("Host").text("Status").number("Requests")
                                .row("web-01", "200 OK", 1200)
                                .row("web-01", "404 Not Found", null)
                                .row("web-02", "200 OK", 900)
                                .row(null, "200 OK", 300)
                                .row("web-02", null, 50)
                                .build()))
                .withPlay(play -> {
                    final Query bars = VisStories.drawn(play, BAR, 3);
                    play.expect("the bars' names", () -> names(bars))
                            .toContain("No Status, Host: web-02, Requests: 50");
                    play.expect(play.withinFrame(VisStories.frame(play))
                            .querySelectorAll(".vis-axis--x .vis-axis__label")).toHaveLength(2);
                    VisStories.expectNoProblems(play);
                })
                // Hosts with long names, which are cut to fit under their bars, the whole name
                // kept in a tooltip
                .story("LongLabels", VisStories.story("BarChart",
                        VisStories.settings("x", "Host", "y", "Requests"),
                        VisTable.builder().text("Host").number("Requests")
                                .row("web-server-01.production.eu-west-2.example.internal", 1200)
                                .row("web-server-02.production.eu-west-2.example.internal", 1290)
                                .row("database-server-01.production.eu-west-2.example.internal", 1380)
                                .build()))
                .withPlay(play -> {
                    VisStories.drawn(play, BAR, 3);
                    final Play inFrame = play.withinFrame(VisStories.frame(play));
                    final Query labels = inFrame.querySelectorAll(".vis-axis--x .vis-axis__label");
                    play.expect(labels).toHaveLength(3);
                    play.expect(labels.first().textContent()).toMatch("…");
                    final Query wholeNames = inFrame.querySelectorAll(".vis-axis--x .vis-axis__label title");
                    play.expect("the labels' whole names", () -> wholeNames.textContents().get())
                            .toContain("web-server-01.production.eu-west-2.example.internal");
                    // Each label is no wider than the gap between the bars' middles
                    play.expect("the second label's left less the first's right",
                            () -> labels.nth(1).left().get() - labels.nth(0).right().get())
                            .toBeGreaterThanOrEqual(0);
                    VisStories.expectNoProblems(play);
                });

        registry.component(VisStories.TITLE + "LineChart", ChartVisStories.class)
                .layout(StoryLayout.FULLSCREEN)
                .tags(VisStories.TAG)
                // What Stroom is told of a selected point (the Selection column of the design): Ctrl+Click
                // tells it of the point, and again of nothing
                .story("SelectionPayload", VisStories.story("LineChart",
                        VisStories.settings("x", "Hour", "y", "Requests", "lineSeries", "Host"), HOURLY_REQUESTS))
                .withPlay(play -> {
                    VisStories.drawn(play, POINT);
                    final Query point = play.withinFrame(VisStories.frame(play))
                            .querySelectorAll(POINT).first();
                    VisStories.expectSelectionOf(play, point,
                            "[{\"key\":\"db-01~#~2026-10-12T00:00:00.000Z\",\"series\":\"db-01\",\""
                            + "x\":\"2026-10-12T00:00:00.000Z\",\"value\":26,\"y\":26}]");
                    VisStories.expectNoProblems(play);
                })
                // A line for each host through its requests each hour
                .story("ThreeSeries", VisStories.story("LineChart",
                        VisStories.settings("x", "Hour", "y", "Requests", "lineSeries", "Host"), HOURLY_REQUESTS))
                .withPlay(play -> {
                    final Query lines = VisStories.drawn(play, LINE, 3);
                    final Play inFrame = play.withinFrame(VisStories.frame(play));
                    final Query points = inFrame.querySelectorAll(POINT);
                    play.expect(points).toHaveLength(72);
                    // Each line has dashes of its own, the first solid, so that they aren't told apart
                    // by colour alone
                    play.expect("the lines' dashes", () -> List.of(
                                    lines.nth(0).computedStyle("stroke-dasharray").get(),
                                    lines.nth(1).computedStyle("stroke-dasharray").get(),
                                    lines.nth(2).computedStyle("stroke-dasharray").get()))
                            .toSatisfy("the first solid, each different",
                                    dashes -> "none".equals(((List<?>) dashes).get(0))
                                              && new HashSet<>((List<?>) dashes).size() == 3);
                    // The points' dots only show when a point is shown, focused or selected
                    play.expect(points.first().computedStyle("opacity")).toBe("0");
                    // By default a line is monotone, going through its first point (where d3 3's
                    // basis-open, the legacy default, started a third of the way to the second)
                    play.expect("where the first line starts and its first point is", () -> List.of(
                                    lines.first().attribute("d").get().replaceAll("^M([^,]+),([^LCZ]+).*$", "$1,$2"),
                                    points.first().attribute("cx").get() + "," + points.first().attribute("cy").get()))
                            .toSatisfy("the same", ends -> {
                                final List<?> list = (List<?>) ends;
                                return roundedPoint(list.get(0)).equals(roundedPoint(list.get(1)));
                            });
                    final Query chart = inFrame.querySelector("svg.vis-line-chart");
                    play.waitFor(() -> play.expect(chart.attribute("aria-label"))
                            .toMatch("Line chart of Requests by Hour: 3 lines of 72 points, highest Requests "));
                    play.expect(inFrame.querySelectorAll(".vis-axis").first()).toHaveAttribute("aria-hidden", "true");
                    if (StroomThemeDecorator.isForcedColours()) {
                        // With a high contrast theme, each line is over a casing in its text colour
                        final Query vis = inFrame.querySelector(".vis");
                        final Query casing = inFrame.querySelectorAll(".vis-line-chart__casing").first();
                        play.expect(casing.computedStyle("display")).not().toBe("none");
                        play.expect("the text's colour and a line's casing's",
                                        () -> List.of(vis.computedStyle("color").get(),
                                                casing.computedStyle("stroke").get()))
                                .toSatisfy("the same", colours -> new HashSet<>((List<?>) colours).size() == 1);
                    }
                    VisStories.expectNoProblems(play);
                })
                // A chart for each host, each named by its host
                .story("GridSeries", VisStories.story("LineChart",
                        VisStories.settings("x", "Hour", "y", "Requests", "gridSeries", "Host"), HOURLY_REQUESTS))
                .withPlay(play -> {
                    VisStories.drawn(play, LINE, 3);
                    final Query names = play.withinFrame(VisStories.frame(play)).querySelectorAll(".vis-grid__name");
                    play.expect("the charts' names", () -> names.textContents().get())
                            .toEqual(List.of("db-01", "web-01", "web-02"));
                    VisStories.expectNoProblems(play);
                })
                // The X axis is a slider: Up zooms in to the middle half (only its points, the lines cut
                // at the plot's edges and a note of what is shown), Right moves along, Escape shows all
                .story("KeyboardAxisZoom", VisStories.story("LineChart",
                        VisStories.settings("x", "Hour", "y", "Requests", "lineSeries", "Host"), HOURLY_REQUESTS))
                .withPlay(play -> {
                    VisStories.drawn(play, LINE, 3);
                    final Play inFrame = play.withinFrame(VisStories.frame(play));
                    final Query chart = inFrame.querySelector("svg.vis-line-chart");
                    final Query axis = inFrame.querySelector(AXIS_ZOOM);
                    final Query note = inFrame.querySelector(AXIS_ZOOM_NOTE);
                    final Query points = inFrame.querySelectorAll("circle.vis-line-chart__point");
                    play.expect(axis).toHaveAttribute("role", "slider");
                    play.expect(axis).toHaveAttribute("aria-label", "Hour axis zoom");
                    play.expect(axis.attribute("aria-valuetext")).toMatch("Showing all");
                    play.expect(points).toHaveLength(72);
                    play.run("focus the axis", () -> axis.element().get().focus());
                    play.expect(axis).toHaveFocus();
                    play.expect(inFrame.querySelector(".vis-axis-zoom__frame").computedStyle("stroke-width"))
                            .toBe("2px");
                    VisStories.press(play, "{ArrowUp}");
                    play.waitFor(() -> play.expect(chart.attribute("aria-label")).toMatch(
                            "zoomed to 2026-10-12T05:45:00.000Z to 2026-10-12T17:15:00.000Z: 3 lines of 36 points"));
                    play.expect(points).toHaveLength(36);
                    play.expect(note).toHaveTextContent("Zoomed: 2026-10-12T05:45:00.000Z to 2026-10-12T17:15:00.000Z");
                    play.expect(inFrame.querySelector(".vis-line-chart__lines").attribute("clip-path")).toBeTruthy();
                    play.expect(axis).toHaveFocus();
                    VisStories.press(play, "{ArrowRight}");
                    play.waitFor(() -> play.expect(note).toHaveTextContent(
                            "Zoomed: 2026-10-12T06:54:00.000Z to 2026-10-12T18:24:00.000Z"));
                    VisStories.press(play, "{Escape}");
                    play.waitFor(() -> play.expect(points).toHaveLength(72));
                    play.expect(note).toHaveTextContent("");
                    play.expect(chart.attribute("aria-label")).not().toMatch("zoomed");
                    VisStories.expectNoProblems(play);
                })
                // The mouse anywhere over the chart shows the point nearest to it, with its dot and
                // tooltip, and fades the other lines; leaving the chart hides them
                .story("HoverShowsNearestPoint", VisStories.story("LineChart",
                        VisStories.settings("x", "Hour", "y", "Requests", "lineSeries", "Host"), HOURLY_REQUESTS))
                .withPlay(play -> {
                    VisStories.drawn(play, LINE, 3);
                    final Play inFrame = play.withinFrame(VisStories.frame(play));
                    final Query point = inFrame.getByRole("img", WEB_01_AT_1);
                    final Query hoverArea = inFrame.querySelector(".vis-nearest");
                    final Query tooltip = VisStories.tooltip(play);
                    // Just beside the point's middle, not on it
                    play.fireEvent().pointerMove(hoverArea, EventInit.create().relativeTo(point, 5, 2));
                    play.waitFor(() -> play.expect(tooltip).toBeVisible());
                    play.expect(tooltip).toHaveTextContent(TextMatch.containing("web-01"));
                    play.expect(tooltip).toHaveTextContent(TextMatch.containing("97"));
                    play.expect(point.computedStyle("opacity")).toBe("1");
                    final Query webTwo = inFrame.querySelectorAll("g.vis-line-chart__series").nth(2);
                    play.waitFor(() -> play.expect(webTwo.computedStyle("opacity")).toBe("0.15"));
                    play.unhover(hoverArea);
                    play.waitFor(() -> play.expect(tooltip).not().toBeVisible());
                    play.expect(point.computedStyle("opacity")).toBe("0");
                    VisStories.expectNoProblems(play);
                })
                // The chart is one tab stop. Left and Right move along a host's points, Up and Down
                // to the same hour of another host; the point's dot shows with a focus ring
                .story("ArrowKeysMoveBetweenPoints", VisStories.story("LineChart",
                        VisStories.settings("x", "Hour", "y", "Requests", "lineSeries", "Host"), HOURLY_REQUESTS))
                .withPlay(play -> {
                    VisStories.drawn(play, LINE, 3);
                    final Play inFrame = play.withinFrame(VisStories.frame(play));
                    final Query tooltip = VisStories.tooltip(play);
                    VisStories.focusTabStop(play);
                    play.waitFor(() -> play.expect(inFrame.getByRole("img", DB_01_AT_0)).toHaveFocus());
                    play.waitFor(() -> play.expect(tooltip).toBeVisible());
                    play.expect(VisStories.focusedPoint(play).computedStyle("opacity")).toBe("1");
                    play.expect(VisStories.focusedPoint(play).computedStyle("outline-style")).toBe("solid");
                    VisStories.press(play, "{ArrowRight}");
                    play.waitFor(() -> play.expect(inFrame.getByRole("img", DB_01_AT_1)).toHaveFocus());
                    VisStories.press(play, "{ArrowDown}");
                    play.waitFor(() -> play.expect(inFrame.getByRole("img", WEB_01_AT_1)).toHaveFocus());
                    play.waitFor(() -> play.expect(tooltip).toHaveTextContent(TextMatch.containing("97")));
                    VisStories.expectNoProblems(play);
                })
                // A long line isn't a long walk: Page Down and Page Up move ten points along it, and
                // Ctrl+End and Ctrl+Home go to the last and first points of the whole chart
                .story("PageKeys", VisStories.story("LineChart",
                        VisStories.settings("x", "Hour", "y", "Requests", "lineSeries", "Host"), HOURLY_REQUESTS))
                .withPlay(play -> {
                    VisStories.drawn(play, LINE, 3);
                    final Play inFrame = play.withinFrame(VisStories.frame(play));
                    VisStories.focusTabStop(play);
                    play.waitFor(() -> play.expect(inFrame.getByRole("img", DB_01_AT_0)).toHaveFocus());
                    VisStories.press(play, "{PageDown}");
                    play.waitFor(() -> play.expect(VisStories.focusedPoint(play).attribute("aria-label"))
                            .toMatch("db-01, Hour: 2026-10-12T10:00:00.000Z"));
                    VisStories.press(play, "{PageUp}");
                    play.waitFor(() -> play.expect(inFrame.getByRole("img", DB_01_AT_0)).toHaveFocus());
                    VisStories.press(play, "{Control>}{End}{/Control}");
                    play.waitFor(() -> play.expect(VisStories.focusedPoint(play).attribute("aria-label"))
                            .toMatch("web-02, Hour: 2026-10-12T23:00:00.000Z"));
                    VisStories.press(play, "{Control>}{Home}{/Control}");
                    play.waitFor(() -> play.expect(inFrame.getByRole("img", DB_01_AT_0)).toHaveFocus());
                    VisStories.expectNoProblems(play);
                })
                // Enter selects the focused point, as Ctrl+Click does: its dot stays once the
                // highlight has gone, and Enter again deselects it
                .story("EnterSelectsPoint", VisStories.story("LineChart",
                        VisStories.settings("x", "Hour", "y", "Requests", "lineSeries", "Host"), HOURLY_REQUESTS))
                .withPlay(play -> {
                    VisStories.drawn(play, LINE, 3);
                    final Query point = play.withinFrame(VisStories.frame(play))
                            .getByRole("img", TextMatch.startingWith(DB_01_AT_0));
                    VisStories.focusTabStop(play);
                    VisStories.press(play, "{Enter}");
                    VisStories.press(play, "{Tab}");
                    play.waitFor(() -> play.expect(point).toHaveClass("vis-line-chart__point--selected"));
                    play.expect(point).toHaveAttribute("aria-label", DB_01_AT_0 + ", selected");
                    play.expect(point.computedStyle("opacity")).toBe("1");
                    VisStories.focusTabStop(play);
                    VisStories.press(play, "{Enter}");
                    VisStories.press(play, "{Tab}");
                    play.waitFor(() -> play.expect(point.computedStyle("opacity")).toBe("0"));
                    VisStories.expectNoProblems(play);
                })
                // State counting (the Counting tab): each IN starts a session and each OUT ends one,
                // and the line is how many are open
                .story("StateCounting", VisStories.story("LineChart",
                        VisStories.settings("x", "Time", "y", "State", "stateCounting", "True",
                                "openSessionText", "IN", "closeSessionText", "OUT", "interpolationMode", "step-after"),
                        VisTable.builder().date("Time").text("State")
                                .row("2026-10-12T09:00:00Z", "IN")
                                .row("2026-10-12T09:10:00Z", "IN")
                                .row("2026-10-12T09:20:00Z", "OUT")
                                .row("2026-10-12T09:30:00Z", "IN")
                                .row("2026-10-12T09:40:00Z", "OUT")
                                .row("2026-10-12T09:50:00Z", "OUT")
                                .build()))
                .withPlay(play -> {
                    VisStories.drawn(play, LINE, 1);
                    final Play inFrame = play.withinFrame(VisStories.frame(play));
                    final Query chart = inFrame.querySelector("svg.vis-line-chart");
                    play.waitFor(() -> play.expect(chart).toHaveAttribute("aria-label",
                            "Line chart of Count of State by Time: 1 line of 6 points, highest Count of State 2 at "
                            + "2026-10-12T09:10:00.000Z"));
                    play.expect(inFrame.getByRole("img", "Time: 2026-10-12T09:50:00.000Z, Count of State: 0"))
                            .toBeInTheDocument();
                    VisStories.expectNoProblems(play);
                })
                // A log scale, for small values beside large ones, takes zero too
                .story("LogScale", VisStories.story("LineChart",
                        VisStories.settings("x", "Hour", "y", "Errors", "scaleYAxis", "Log"),
                        VisTable.builder().date("Hour").number("Errors")
                                .row("2026-10-12T00:00:00Z", 0)
                                .row("2026-10-12T01:00:00Z", 3)
                                .row("2026-10-12T02:00:00Z", 40)
                                .row("2026-10-12T03:00:00Z", 5000)
                                .build()))
                .withPlay(play -> {
                    final Query line = VisStories.drawn(play, LINE, 1).first();
                    play.expect(line.attribute("d")).not().toMatch("NaN");
                    final Query points = play.withinFrame(VisStories.frame(play)).querySelectorAll(POINT);
                    play.expect(points).toHaveLength(4);
                    // 3 is well clear of 0, as it wouldn't be on a linear scale up to 5,000
                    play.expect("how far 3 is above 0",
                                    () -> Double.parseDouble(points.nth(0).attribute("cy").get())
                                          - Double.parseDouble(points.nth(1).attribute("cy").get()))
                            .toBeGreaterThan(50);
                    VisStories.expectNoProblems(play);
                })
                // A missing value is a gap in the line, and has no point
                .story("Gap", VisStories.story("LineChart",
                        VisStories.settings("x", "Hour", "y", "Requests"),
                        VisTable.builder().date("Hour").number("Requests")
                                .row("2026-10-12T00:00:00Z", 10)
                                .row("2026-10-12T01:00:00Z", 20)
                                .row("2026-10-12T02:00:00Z", null)
                                .row("2026-10-12T03:00:00Z", 15)
                                .row("2026-10-12T04:00:00Z", 25)
                                .build()))
                .withPlay(play -> {
                    final Query line = VisStories.drawn(play, LINE, 1).first();
                    play.expect(play.withinFrame(VisStories.frame(play)).querySelectorAll(POINT)).toHaveLength(4);
                    play.expect("how many pieces the line is in",
                            () -> line.attribute("d").get().split("M", -1).length - 1).toBe(2);
                    VisStories.expectNoProblems(play);
                })
                // Along a text axis a point is in the middle of its category
                .story("Ordinal", VisStories.story("LineChart",
                        VisStories.settings("x", "Status", "y", "Requests", "lineSeries", "Host"), REQUESTS_BY_STATUS))
                .withPlay(play -> {
                    VisStories.drawn(play, LINE, 3);
                    play.expect(play.withinFrame(VisStories.frame(play)).querySelectorAll(POINT)).toHaveLength(12);
                    VisStories.expectNoProblems(play);
                })
                // One value: a line through it would have no length, so its dot shows
                .story("OneValue", VisStories.story("LineChart",
                        VisStories.settings("x", "Hour", "y", "Requests"),
                        VisTable.builder().date("Hour").number("Requests").row("2026-10-12T09:00:00Z", 420).build()))
                .withPlay(play -> {
                    final Query point = VisStories.drawn(play, POINT, 1).first();
                    play.expect(point.computedStyle("opacity")).toBe("1");
                    final Query chart = play.withinFrame(VisStories.frame(play)).querySelector("svg.vis-line-chart");
                    play.waitFor(() -> play.expect(chart).toHaveAttribute("aria-label",
                            "Line chart of Requests by Hour: 1 line of 1 point, highest Requests 420 at "
                            + "2026-10-12T09:00:00.000Z"));
                    VisStories.expectNoProblems(play);
                })
                // A point between two gaps has no line to either side of it, so its dot shows
                .story("LonePoint", VisStories.story("LineChart",
                        VisStories.settings("x", "Hour", "y", "Requests"),
                        VisTable.builder().date("Hour").number("Requests")
                                .row("2026-10-12T00:00:00Z", 10)
                                .row("2026-10-12T01:00:00Z", 20)
                                .row("2026-10-12T02:00:00Z", null)
                                .row("2026-10-12T03:00:00Z", 15)
                                .row("2026-10-12T04:00:00Z", null)
                                .row("2026-10-12T05:00:00Z", 25)
                                .row("2026-10-12T06:00:00Z", 30)
                                .build()))
                .withPlay(play -> {
                    final Query points = VisStories.drawn(play, POINT, 5);
                    play.expect(points.nth(2).computedStyle("opacity")).toBe("1");
                    play.expect(points.nth(1).computedStyle("opacity")).toBe("0");
                    VisStories.expectNoProblems(play);
                })
                // A search that found nothing: 'No data' where the lines would be
                .story("NoData", VisStories.story("LineChart",
                        VisStories.settings("x", "Hour", "y", "Requests", "lineSeries", "Host"),
                        VisTable.builder().date("Hour").text("Host").number("Requests").build()))
                .withPlay(play -> {
                    final Query chart = VisStories.drawn(play, "svg.vis-line-chart", 1).first();
                    final Play inFrame = play.withinFrame(VisStories.frame(play));
                    play.waitFor(() -> play.expect(chart).toHaveAttribute("aria-label",
                            "Line chart of Requests by Hour: no data"));
                    play.expect(inFrame.querySelector(".vis-empty")).toHaveTextContent("No data");
                    play.expect(inFrame.querySelectorAll(POINT)).toHaveLength(0);
                    VisStories.expectNoProblems(play);
                });

        registry.component(VisStories.TITLE + "StackedArea", ChartVisStories.class)
                .layout(StoryLayout.FULLSCREEN)
                .tags(VisStories.TAG)
                // What Stroom is told of a selected point (the Selection column of the design): Ctrl+Click
                // tells it of the point, and again of nothing
                .story("SelectionPayload", VisStories.story("StackedArea",
                        VisStories.settings("x", "Hour", "y", "Requests", "lineSeries", "Host"), HOURLY_REQUESTS))
                .withPlay(play -> {
                    VisStories.drawn(play, AREA_POINT);
                    final Query point = play.withinFrame(VisStories.frame(play))
                            .querySelectorAll(AREA_POINT).first();
                    VisStories.expectSelectionOf(play, point,
                            "[{\"key\":\"db-01~#~2026-10-12T00:00:00.000Z\",\"series\":\"db-01\",\""
                            + "x\":\"2026-10-12T00:00:00.000Z\",\"value\":26,\"y\":26}]");
                    VisStories.expectNoProblems(play);
                })
                // Each host's requests each hour, stacked: the host with the most (web-01) at the
                // bottom, then web-02, then db-01
                .story("ThreeSeries", VisStories.story("StackedArea",
                        VisStories.settings("x", "Hour", "y", "Requests", "lineSeries", "Host"), HOURLY_REQUESTS))
                .withPlay(play -> {
                    VisStories.drawn(play, AREA, 3);
                    final Play inFrame = play.withinFrame(VisStories.frame(play));
                    play.expect(inFrame.querySelectorAll(AREA_POINT)).toHaveLength(72);
                    final Query chart = inFrame.querySelector("svg.vis-stacked-area");
                    play.waitFor(() -> play.expect(chart).toHaveAttribute("aria-label",
                            "Stacked area chart of Requests by Hour: 3 series at 24 values of Hour, highest total "
                            + "Requests 1,308 at 2026-10-12T14:00:00.000Z"));
                    // At midnight db-01's point is above web-02's, which is above web-01's
                    final Query dbOne = inFrame.getByRole("img", DB_01_STACKED_AT_0);
                    final Query webTwo = inFrame.getByRole("img", WEB_02_STACKED_AT_0);
                    final Query webOne = inFrame.getByRole("img", TextMatch.containing("web-01, Hour: "
                                                                                      + "2026-10-12T00:00:00.000Z"));
                    play.expect("how far db-01's point is above web-02's",
                            () -> webTwo.top().get() - dbOne.top().get()).toBeGreaterThan(0);
                    play.expect("how far web-02's point is above web-01's",
                            () -> webOne.top().get() - webTwo.top().get()).toBeGreaterThan(0);
                    if (StroomThemeDecorator.isForcedColours()) {
                        // With a high contrast theme, each band's line is over a casing in its text colour
                        final Query casing = inFrame.querySelectorAll(".vis-stacked-area__casing").first();
                        play.expect(casing.computedStyle("display")).not().toBe("none");
                    }
                    VisStories.expectNoProblems(play);
                })
                // A chart for each host, each named by its host
                .story("GridSeries", VisStories.story("StackedArea",
                        VisStories.settings("x", "Hour", "y", "Requests", "gridSeries", "Host"), HOURLY_REQUESTS))
                .withPlay(play -> {
                    VisStories.drawn(play, AREA, 3);
                    final Query names = play.withinFrame(VisStories.frame(play)).querySelectorAll(".vis-grid__name");
                    play.expect("the charts' names", () -> names.textContents().get())
                            .toEqual(List.of("db-01", "web-01", "web-02"));
                    VisStories.expectNoProblems(play);
                })
                // The chart is one tab stop, starting at the top series. Up and Down move to the
                // series above and below at the same hour; each point's tooltip has the total there
                .story("ArrowKeysMoveBetweenSeries", VisStories.story("StackedArea",
                        VisStories.settings("x", "Hour", "y", "Requests", "lineSeries", "Host"), HOURLY_REQUESTS))
                .withPlay(play -> {
                    VisStories.drawn(play, AREA, 3);
                    final Play inFrame = play.withinFrame(VisStories.frame(play));
                    final Query tooltip = VisStories.tooltip(play);
                    VisStories.focusTabStop(play);
                    play.waitFor(() -> play.expect(inFrame.getByRole("img", DB_01_STACKED_AT_0)).toHaveFocus());
                    play.waitFor(() -> play.expect(tooltip).toHaveTextContent(TextMatch.containing("174")));
                    play.expect(VisStories.focusedPoint(play).computedStyle("opacity")).toBe("1");
                    VisStories.press(play, "{ArrowDown}");
                    play.waitFor(() -> play.expect(inFrame.getByRole("img", WEB_02_STACKED_AT_0)).toHaveFocus());
                    VisStories.press(play, "{ArrowUp}");
                    play.waitFor(() -> play.expect(inFrame.getByRole("img", DB_01_STACKED_AT_0)).toHaveFocus());
                    VisStories.expectNoProblems(play);
                })
                // The mouse anywhere over the chart shows the point nearest to it, and fades the
                // other series
                .story("HoverShowsNearestPoint", VisStories.story("StackedArea",
                        VisStories.settings("x", "Hour", "y", "Requests", "lineSeries", "Host"), HOURLY_REQUESTS))
                .withPlay(play -> {
                    VisStories.drawn(play, AREA, 3);
                    final Play inFrame = play.withinFrame(VisStories.frame(play));
                    final Query point = inFrame.getByRole("img", WEB_02_STACKED_AT_0);
                    final Query tooltip = VisStories.tooltip(play);
                    play.fireEvent().pointerMove(inFrame.querySelector(".vis-nearest"),
                            EventInit.create().relativeTo(point, 5, 2));
                    play.waitFor(() -> play.expect(tooltip).toBeVisible());
                    play.expect(tooltip).toHaveTextContent(TextMatch.containing("web-02"));
                    play.expect(point.computedStyle("opacity")).toBe("1");
                    play.waitFor(() -> play.expect(inFrame.querySelectorAll("g.vis-stacked-area__series").first()
                            .computedStyle("opacity")).toBe("0.15"));
                    VisStories.expectNoProblems(play);
                })
                // A host without a value at an hour adds nothing to the stack there, and has no point
                .story("MissingValue", VisStories.story("StackedArea",
                        VisStories.settings("x", "Hour", "y", "Requests", "lineSeries", "Host"),
                        VisTable.builder().date("Hour").text("Host").number("Requests")
                                .row("2026-10-12T00:00:00Z", "web-01", 100)
                                .row("2026-10-12T01:00:00Z", "web-01", 120)
                                .row("2026-10-12T02:00:00Z", "web-01", 110)
                                .row("2026-10-12T00:00:00Z", "web-02", 40)
                                .row("2026-10-12T02:00:00Z", "web-02", 50)
                                .build()))
                .withPlay(play -> {
                    VisStories.drawn(play, AREA, 2);
                    final Play inFrame = play.withinFrame(VisStories.frame(play));
                    play.expect(inFrame.querySelectorAll(AREA_POINT)).toHaveLength(5);
                    play.expect(inFrame.queryByRole("img", TextMatch.containing("web-02, Hour: 2026-10-12T01:00")))
                            .toBeNull();
                    VisStories.expectNoProblems(play);
                })
                // A series that falls stacks down from zero, apart from those that rise
                .story("Negative", VisStories.story("StackedArea",
                        VisStories.settings("x", "Hour", "y", "Change", "lineSeries", "Host"),
                        VisTable.builder().date("Hour").text("Host").number("Change")
                                .row("2026-10-12T00:00:00Z", "web-01", 30)
                                .row("2026-10-12T01:00:00Z", "web-01", 40)
                                .row("2026-10-12T00:00:00Z", "web-02", -20)
                                .row("2026-10-12T01:00:00Z", "web-02", -10)
                                .build()))
                .withPlay(play -> {
                    VisStories.drawn(play, AREA, 2);
                    final Play inFrame = play.withinFrame(VisStories.frame(play));
                    final Query rise = inFrame.getByRole("img", TextMatch.containing("web-01, Hour: 2026-10-12T00:00"));
                    final Query fall = inFrame.getByRole("img", TextMatch.containing("web-02, Hour: 2026-10-12T00:00"));
                    play.expect("how far the fall's point is below the rise's",
                            () -> fall.top().get() - rise.top().get()).toBeGreaterThan(50);
                    play.expect(fall.attribute("aria-label")).toMatch("Total Change: 10");
                    VisStories.expectNoProblems(play);
                })
                // One hour: the areas have no width, so their points' dots show
                .story("OneValue", VisStories.story("StackedArea",
                        VisStories.settings("x", "Hour", "y", "Requests", "lineSeries", "Host"),
                        VisTable.builder().date("Hour").text("Host").number("Requests")
                                .row("2026-10-12T09:00:00Z", "web-01", 300)
                                .row("2026-10-12T09:00:00Z", "web-02", 200)
                                .build()))
                .withPlay(play -> {
                    final Query points = VisStories.drawn(play, AREA_POINT, 2);
                    play.expect(points.first().computedStyle("opacity")).toBe("1");
                    play.expect(points.nth(1).computedStyle("opacity")).toBe("1");
                    VisStories.expectNoProblems(play);
                })
                // A search that found nothing: 'No data' where the areas would be
                .story("NoData", VisStories.story("StackedArea",
                        VisStories.settings("x", "Hour", "y", "Requests", "lineSeries", "Host"),
                        VisTable.builder().date("Hour").text("Host").number("Requests").build()))
                .withPlay(play -> {
                    final Query chart = VisStories.drawn(play, "svg.vis-stacked-area", 1).first();
                    final Play inFrame = play.withinFrame(VisStories.frame(play));
                    play.waitFor(() -> play.expect(chart).toHaveAttribute("aria-label",
                            "Stacked area chart of Requests by Hour: no data"));
                    play.expect(inFrame.querySelector(".vis-empty")).toHaveTextContent("No data");
                    VisStories.expectNoProblems(play);
                });

        addTimeSeries(registry);

        registry.component(VisStories.TITLE + "Scatter", ChartVisStories.class)
                .layout(StoryLayout.FULLSCREEN)
                .tags(VisStories.TAG)
                // What Stroom is told of a selected point (the Selection column of the design): Ctrl+Click
                // tells it of the point, and again of nothing
                .story("SelectionPayload", VisStories.story("Scatter",
                        VisStories.settings("x", "Duration", "y", "Bytes", "pointSeries", "Host"), responseSizes()))
                .withPlay(play -> {
                    VisStories.drawn(play, SCATTER_POINT);
                    final Query point = play.withinFrame(VisStories.frame(play))
                            .querySelectorAll(SCATTER_POINT).first();
                    VisStories.expectSelectionOf(play, point,
                            "[{\"key\":\"db-01~#~74,8000\",\"series\":\"db-01\",\"x\":74,\"y\":8000"
                            + "}]");
                    VisStories.expectNoProblems(play);
                })
                // How long each host's responses took against how big they were
                .story("ThreeSeries", VisStories.story("Scatter",
                        VisStories.settings("x", "Duration", "y", "Bytes", "pointSeries", "Host"), responseSizes()))
                .withPlay(play -> {
                    final Query points = VisStories.drawn(play, SCATTER_POINT, 30);
                    final Play inFrame = play.withinFrame(VisStories.frame(play));
                    final Query chart = inFrame.querySelector("svg.vis-scatter");
                    play.waitFor(() -> play.expect(chart).toHaveAttribute("aria-label",
                            "Scatter chart of Bytes by Duration: 30 points in 3 series, highest Bytes 89,000 at 275 "
                            + "(db-01)"));
                    play.expect(points.first()).toHaveAttribute("r", "4");
                    if (StroomThemeDecorator.isForcedColours()) {
                        // With a high contrast theme, each point is edged in its text colour
                        final Query vis = inFrame.querySelector(".vis");
                        play.expect("the text's colour and a point's edge's",
                                        () -> List.of(vis.computedStyle("color").get(),
                                                points.first().computedStyle("stroke").get()))
                                .toSatisfy("the same", colours -> new HashSet<>((List<?>) colours).size() == 1);
                    }
                    VisStories.expectNoProblems(play);
                })
                // A chart for each host, each named by its host
                .story("GridSeries", VisStories.story("Scatter",
                        VisStories.settings("x", "Duration", "y", "Bytes", "gridSeries", "Host"), responseSizes()))
                .withPlay(play -> {
                    VisStories.drawn(play, SCATTER_POINT, 30);
                    final Query names = play.withinFrame(VisStories.frame(play)).querySelectorAll(".vis-grid__name");
                    play.expect("the charts' names", () -> names.textContents().get())
                            .toEqual(List.of("db-01", "web-01", "web-02"));
                    VisStories.expectNoProblems(play);
                })
                // Dragging along the X axis zooms in to the span dragged over, with only the points in
                // it; double-clicking the axis shows all again
                .story("DragAxisZoom", VisStories.story("Scatter",
                        VisStories.settings("x", "Duration", "y", "Bytes", "pointSeries", "Host"), responseSizes()))
                .withPlay(play -> {
                    final Query points = VisStories.drawn(play, SCATTER_POINT, 30);
                    final Play inFrame = play.withinFrame(VisStories.frame(play));
                    final Query overlay = inFrame.querySelector(AXIS_ZOOM + " .overlay");
                    final Query note = inFrame.querySelector(AXIS_ZOOM_NOTE);
                    play.fireEvent().mouseDown(overlay, EventInit.create().relativeTo(overlay, 20, 5));
                    play.fireEvent().mouseMove(overlay, EventInit.create().relativeTo(overlay, 200, 5));
                    play.fireEvent().mouseUp(overlay, EventInit.create().relativeTo(overlay, 200, 5));
                    play.waitFor(() -> play.expect(note.textContent()).toMatch("Zoomed: "));
                    play.expect(points.count()).toBeLessThan(30);
                    play.expect(points.count()).toBeGreaterThan(0);
                    play.expect(inFrame.querySelector("svg.vis-scatter").attribute("aria-label")).toMatch("zoomed to");
                    play.fireEvent().dblClick(overlay);
                    play.waitFor(() -> play.expect(points).toHaveLength(30));
                    play.expect(note).toHaveTextContent("");
                    VisStories.expectNoProblems(play);
                })
                // With Synch X Axis (the default), zooming one chart's X axis zooms them all
                .story("GridAxisZoom", VisStories.story("Scatter",
                        VisStories.settings("x", "Duration", "y", "Bytes", "gridSeries", "Host"), responseSizes()))
                .withPlay(play -> {
                    final Query points = VisStories.drawn(play, SCATTER_POINT, 30);
                    final Play inFrame = play.withinFrame(VisStories.frame(play));
                    final Query axis = inFrame.querySelectorAll(AXIS_ZOOM).first();
                    play.run("focus the first chart's axis", () -> axis.element().get().focus());
                    VisStories.press(play, "{ArrowUp}");
                    play.waitFor(() -> play.expect(inFrame.querySelectorAll(AXIS_ZOOM_NOTE).nth(2).textContent())
                            .toMatch("Zoomed: "));
                    play.expect(inFrame.querySelectorAll(AXIS_ZOOM_NOTE).nth(1).textContent()).toMatch("Zoomed: ");
                    play.expect(points.count()).toBeLessThan(30);
                    VisStories.press(play, "{Escape}");
                    play.waitFor(() -> play.expect(points).toHaveLength(30));
                    VisStories.expectNoProblems(play);
                })
                // The mouse anywhere over the chart shows the point nearest to it, ringed, with its
                // tooltip, and fades the other series
                .story("HoverShowsNearestPoint", VisStories.story("Scatter",
                        VisStories.settings("x", "Duration", "y", "Bytes", "pointSeries", "Host"), responseSizes()))
                .withPlay(play -> {
                    VisStories.drawn(play, SCATTER_POINT, 30);
                    final Play inFrame = play.withinFrame(VisStories.frame(play));
                    final Query point = inFrame.getByRole("img", WEB_01_FIRST_RESPONSE);
                    final Query tooltip = VisStories.tooltip(play);
                    play.fireEvent().pointerMove(inFrame.querySelector(".vis-nearest"),
                            EventInit.create().relativeTo(point, 6, 1));
                    play.waitFor(() -> play.expect(tooltip).toBeVisible());
                    play.expect(tooltip).toHaveTextContent(TextMatch.containing("2,000"));
                    play.expect(point).toHaveClass("vis-scatter__point--shown");
                    play.waitFor(() -> play.expect(inFrame.querySelectorAll("g.vis-scatter__series").first()
                            .computedStyle("opacity")).toBe("0.15"));
                    VisStories.expectNoProblems(play);
                })
                // The chart is one tab stop. Left and Right move along a host's points in order of
                // Duration, Up and Down between the hosts
                .story("ArrowKeysMoveBetweenPoints", VisStories.story("Scatter",
                        VisStories.settings("x", "Duration", "y", "Bytes", "pointSeries", "Host"), responseSizes()))
                .withPlay(play -> {
                    VisStories.drawn(play, SCATTER_POINT, 30);
                    final Play inFrame = play.withinFrame(VisStories.frame(play));
                    VisStories.focusTabStop(play);
                    play.waitFor(() -> play.expect(inFrame.getByRole("img", DB_01_FIRST_RESPONSE)).toHaveFocus());
                    play.expect(VisStories.focusedPoint(play).computedStyle("outline-style")).toBe("solid");
                    VisStories.press(play, "{ArrowRight}");
                    play.waitFor(() -> play.expect(inFrame.getByRole("img", "db-01, Duration: 87, Bytes: 17,000"))
                            .toHaveFocus());
                    VisStories.press(play, "{ArrowDown}");
                    play.waitFor(() -> play.expect(inFrame.getByRole("img", "web-01, Duration: 78, Bytes: 11,000"))
                            .toHaveFocus());
                    VisStories.expectNoProblems(play);
                })
                // Responses of the same size that took as long are each a point, and bigger points
                // with a larger Point Size
                .story("SamePlace", VisStories.story("Scatter",
                        VisStories.settings("x", "Duration", "y", "Bytes", "pointSize", "8"),
                        VisTable.builder().number("Duration").number("Bytes")
                                .row(40, 1200).row(40, 1200).row(55, 3000).build()))
                .withPlay(play -> {
                    final Query points = VisStories.drawn(play, SCATTER_POINT, 3);
                    play.expect(points.first()).toHaveAttribute("r", "8");
                    VisStories.expectNoProblems(play);
                })
                // One response: its point in the middle of the chart
                .story("OneValue", VisStories.story("Scatter",
                        VisStories.settings("x", "Duration", "y", "Bytes"),
                        VisTable.builder().number("Duration").number("Bytes").row(40, 1200).build()))
                .withPlay(play -> {
                    final Query point = VisStories.drawn(play, SCATTER_POINT, 1).first();
                    final Query chart = play.withinFrame(VisStories.frame(play)).querySelector("svg.vis-scatter");
                    play.expect("how far the point is from the chart's left, as a share of its width",
                            () -> (point.left().get() - chart.left().get()) / chart.width().get())
                            .toBeGreaterThan(0.3);
                    VisStories.expectNoProblems(play);
                })
                // A search that found nothing: 'No data' where the points would be
                .story("NoData", VisStories.story("Scatter",
                        VisStories.settings("x", "Duration", "y", "Bytes", "pointSeries", "Host"),
                        VisTable.builder().text("Host").number("Duration").number("Bytes").build()))
                .withPlay(play -> {
                    final Query chart = VisStories.drawn(play, "svg.vis-scatter", 1).first();
                    final Play inFrame = play.withinFrame(VisStories.frame(play));
                    play.waitFor(() -> play.expect(chart).toHaveAttribute("aria-label",
                            "Scatter chart of Bytes by Duration: no data"));
                    play.expect(inFrame.querySelector(".vis-empty")).toHaveTextContent("No data");
                    VisStories.expectNoProblems(play);
                });

        registry.component(VisStories.TITLE + "Bubble", ChartVisStories.class)
                .layout(StoryLayout.FULLSCREEN)
                .tags(VisStories.TAG)
                // What Stroom is told of a selected bubble (the Selection column of the design): Ctrl+Click
                // tells it of the bubble, and again of nothing
                .story("SelectionPayload", VisStories.story("Bubble",
                        VisStories.settings("name", "Status", "value", "Requests", "series", "Host"),
                        REQUESTS_BY_STATUS))
                .withPlay(play -> {
                    VisStories.drawn(play, BUBBLE);
                    final Query point = play.withinFrame(VisStories.frame(play))
                            .querySelectorAll(BUBBLE).first();
                    VisStories.expectSelectionOf(play, point,
                            "[{\"key\":\"db-01~#~200 OK\",\"name\":\"200 OK\",\"series\":\"db-01\","
                            + "\"value\":1380}]");
                    VisStories.expectNoProblems(play);
                })
                // + and − zoom and 0 puts it back, from the keyboard as the wheel and double click do
                // (the wheel zoom had no keyboard way to it)
                .story("KeyboardZoom", VisStories.story("Bubble",
                        VisStories.settings("name", "Status", "value", "Requests", "series", "Host"),
                        REQUESTS_BY_STATUS))
                .withPlay(play -> {
                    VisStories.drawn(play, BUBBLE, 12);
                    VisStories.expectKeyboardZoom(play, "svg.vis-bubble", "svg.vis-bubble > g");
                    VisStories.expectNoProblems(play);
                })
                // Every status's bubble together, coloured by host
                .story("Flat", VisStories.story("Bubble",
                        VisStories.settings("name", "Status", "value", "Requests", "series", "Host"),
                        REQUESTS_BY_STATUS))
                .withPlay(play -> {
                    final Query bubbles = VisStories.drawn(play, BUBBLE, 12);
                    final Play inFrame = play.withinFrame(VisStories.frame(play));
                    final Query chart = inFrame.querySelector("svg.vis-bubble");
                    play.waitFor(() -> play.expect(chart).toHaveAttribute("aria-label",
                            "Bubble chart of Requests by Status: 12 bubbles in 3 series, largest Requests 1,380 "
                            + "(200 OK, db-01)"));
                    play.expect(inFrame.querySelectorAll(BUBBLE_GROUP)).toHaveLength(0);
                    if (StroomThemeDecorator.isForcedColours()) {
                        // With a high contrast theme, each bubble is edged in its text colour
                        final Query vis = inFrame.querySelector(".vis");
                        play.expect("the text's colour and a bubble's edge's",
                                        () -> List.of(vis.computedStyle("color").get(),
                                                bubbles.first().computedStyle("stroke").get()))
                                .toSatisfy("the same", colours -> new HashSet<>((List<?>) colours).size() == 1);
                    }
                    VisStories.expectNoProblems(play);
                })
                // Each host's bubbles in a bubble of their own
                .story("Nested", VisStories.story("Bubble",
                        VisStories.settings("name", "Status", "value", "Requests", "series", "Host",
                                "flattenSeries", "False"),
                        REQUESTS_BY_STATUS))
                .withPlay(play -> {
                    VisStories.drawn(play, BUBBLE, 12);
                    final Play inFrame = play.withinFrame(VisStories.frame(play));
                    play.expect(inFrame.querySelectorAll(BUBBLE_GROUP)).toHaveLength(3);
                    // A host's bubble is no part of the keyboard's way round: only the statuses are
                    play.expect(inFrame.querySelectorAll(BUBBLE_GROUP + "[tabindex]")).toHaveLength(0);
                    VisStories.expectNoProblems(play);
                })
                // A chart of each host's statuses, each named by its host
                .story("GridSeries", VisStories.story("Bubble",
                        VisStories.settings("name", "Status", "value", "Requests", "gridSeries", "Host"),
                        REQUESTS_BY_STATUS))
                .withPlay(play -> {
                    VisStories.drawn(play, BUBBLE, 12);
                    final Query names = play.withinFrame(VisStories.frame(play)).querySelectorAll(".vis-grid__name");
                    play.expect("the charts' names", () -> names.textContents().get())
                            .toEqual(List.of("db-01", "web-01", "web-02"));
                    VisStories.expectNoProblems(play);
                })
                // Without a Series field each bubble has a colour of its own, and its name on it
                // where it fits, in black or white, whichever reads on its colour
                .story("Labels", VisStories.story("Bubble",
                        VisStories.settings("name", "Status", "value", "Requests", "showLabels", "True"),
                        requestsForHost("web-01")))
                .withPlay(play -> {
                    final Query bubbles = VisStories.drawn(play, BUBBLE, 4);
                    play.expect("how many colours the bubbles are",
                            () -> {
                                final List<String> fills = new ArrayList<>();
                                for (int i = 0; i < bubbles.count().get(); i++) {
                                    fills.add(bubbles.nth(i).computedStyle("fill").get());
                                }
                                return new HashSet<>(fills).size();
                            }).toBe(4);
                    final Query label = play.withinFrame(VisStories.frame(play))
                            .getByText("200 OK", "text.vis-bubble__label");
                    play.expect(label).toBeVisible();
                    play.expect(label.computedStyle("fill"))
                            .toSatisfy("black or white", fill -> "rgb(0, 0, 0)".equals(fill)
                                                                 || "rgb(255, 255, 255)".equals(fill));
                    // With another bubble shown, this one fades, and its name is then in the theme's
                    // text colour, which reads on the faded bubble
                    final Play inFrame = play.withinFrame(VisStories.frame(play));
                    play.hover(inFrame.getByRole("img", TextMatch.containing("301 Moved")));
                    play.waitFor(() -> play.expect(label).toHaveClass("vis-bubble__label--faded"));
                    final Query vis = inFrame.querySelector(".vis");
                    play.expect("the text's colour and the faded bubble's name's",
                                    () -> List.of(vis.computedStyle("color").get(), label.computedStyle("fill").get()))
                            .toSatisfy("the same", colours -> new HashSet<>((List<?>) colours).size() == 1);
                    VisStories.expectNoProblems(play);
                })
                // The chart is one tab stop. Left and Right move between a host's bubbles, Up and
                // Down to the same status of another host
                .story("ArrowKeysMoveBetweenBubbles", VisStories.story("Bubble",
                        VisStories.settings("name", "Status", "value", "Requests", "series", "Host"),
                        REQUESTS_BY_STATUS))
                .withPlay(play -> {
                    VisStories.drawn(play, BUBBLE, 12);
                    final Play inFrame = play.withinFrame(VisStories.frame(play));
                    final Query tooltip = VisStories.tooltip(play);
                    VisStories.focusTabStop(play);
                    play.waitFor(() -> play.expect(inFrame.getByRole("img", "db-01, Status: 200 OK, Requests: 1,380"))
                            .toHaveFocus());
                    play.waitFor(() -> play.expect(tooltip).toBeVisible());
                    play.expect(VisStories.focusedPoint(play).computedStyle("outline-style")).toBe("solid");
                    VisStories.press(play, "{ArrowDown}");
                    play.waitFor(() -> play.expect(inFrame.getByRole("img", "web-01, Status: 200 OK, Requests: 1,200"))
                            .toHaveFocus());
                    VisStories.press(play, "{ArrowRight}");
                    play.waitFor(() -> play.expect(inFrame.getByRole("img",
                            TextMatch.containing("web-01, Status: 301 Moved"))).toHaveFocus());
                    VisStories.expectNoProblems(play);
                })
                // The mouse over a bubble shows its tooltip and fades the others
                .story("HoverShowsTooltip", VisStories.story("Bubble",
                        VisStories.settings("name", "Status", "value", "Requests", "series", "Host"),
                        REQUESTS_BY_STATUS))
                .withPlay(play -> {
                    final Query bubbles = VisStories.drawn(play, BUBBLE, 12);
                    final Query bubble = play.withinFrame(VisStories.frame(play))
                            .getByRole("img", "db-01, Status: 200 OK, Requests: 1,380");
                    final Query tooltip = VisStories.tooltip(play);
                    play.hover(bubble);
                    play.waitFor(() -> play.expect(tooltip).toBeVisible());
                    play.expect(tooltip).toHaveTextContent(TextMatch.containing("1,380"));
                    play.waitFor(() -> play.expect(bubbles.nth(1).computedStyle("fill-opacity")).toBe("0.25"));
                    VisStories.expectNoProblems(play);
                })
                // A bubble's area is its value, so a status with none, or fewer than none, has no bubble
                .story("NothingToSize", VisStories.story("Bubble",
                        VisStories.settings("name", "Status", "value", "Requests"),
                        VisTable.builder().text("Status").number("Requests")
                                .row("200 OK", 900).row("301 Moved", 0).row("404 Not Found", -3).row("500 Error", null)
                                .row("503 Unavailable", 40)
                                .build()))
                .withPlay(play -> {
                    VisStories.drawn(play, BUBBLE, 2);
                    VisStories.expectNoProblems(play);
                })
                // One status: one bubble, filling the chart
                .story("OneValue", VisStories.story("Bubble",
                        VisStories.settings("name", "Status", "value", "Requests"),
                        VisTable.builder().text("Status").number("Requests").row("200 OK", 900).build()))
                .withPlay(play -> {
                    final Query bubble = VisStories.drawn(play, BUBBLE, 1).first();
                    play.expect("the bubble's width", () -> bubble.width().get()).toBeGreaterThan(200);
                    VisStories.expectNoProblems(play);
                })
                // A search that found nothing: 'No data' where the bubbles would be
                .story("NoData", VisStories.story("Bubble",
                        VisStories.settings("name", "Status", "value", "Requests", "series", "Host"),
                        VisTable.builder().text("Host").text("Status").number("Requests").build()))
                .withPlay(play -> {
                    final Query chart = VisStories.drawn(play, "svg.vis-bubble", 1).first();
                    final Play inFrame = play.withinFrame(VisStories.frame(play));
                    play.waitFor(() -> play.expect(chart).toHaveAttribute("aria-label",
                            "Bubble chart of Requests by Status: no data"));
                    play.expect(inFrame.querySelector(".vis-empty")).toHaveTextContent("No data");
                    VisStories.expectNoProblems(play);
                });

        registry.component(VisStories.TITLE + "Doughnut", ChartVisStories.class)
                .layout(StoryLayout.FULLSCREEN)
                .tags(VisStories.TAG)
                // What Stroom is told of a selected slice (the Selection column of the design): Ctrl+Click
                // tells it of the slice, and again of nothing
                .story("SelectionPayload", VisStories.story("Doughnut",
                        VisStories.settings("names", "Status", "values", "Requests"), requestsForHost("web-01")))
                .withPlay(play -> {
                    VisStories.drawn(play, SLICE);
                    final Query point = play.withinFrame(VisStories.frame(play))
                            .querySelectorAll(SLICE).first();
                    VisStories.expectSelectionOf(play, point,
                            "[{\"key\":\"200 OK\",\"name\":\"200 OK\",\"value\":1200}]");
                    VisStories.expectNoProblems(play);
                })
                // A selected slice, left selected so that its look can be seen: edged in the text's
                // colour, on the outside of the doughnut as between slices
                .story("Selected", VisStories.story("Doughnut",
                        VisStories.settings("names", "Status", "values", "Requests"), requestsForHost("web-01")))
                .withPlay(play -> {
                    VisStories.drawn(play, SLICE);
                    final Query slice = play.withinFrame(VisStories.frame(play)).querySelectorAll(SLICE).first();
                    play.fireEvent().click(slice, EventInit.create().ctrlKey());
                    play.waitFor(() -> play.expect(slice).toHaveClass("vis-doughnut__slice--selected"));
                    VisStories.expectNoProblems(play);
                })
                // One host's requests by status, the biggest first, with the total in the middle
                .story("Statuses", VisStories.story("Doughnut",
                        VisStories.settings("names", "Status", "values", "Requests"), requestsForHost("web-01")))
                .withPlay(play -> {
                    final Query slices = VisStories.drawn(play, SLICE, 4);
                    final Play inFrame = play.withinFrame(VisStories.frame(play));
                    final Query chart = inFrame.querySelector("svg.vis-doughnut");
                    play.waitFor(() -> play.expect(chart).toHaveAttribute("aria-label",
                            "Doughnut of Requests by Status: 4 slices, total 1,828, largest 200 OK 1,200 (65.6%)"));
                    play.expect(inFrame.querySelector(".vis-doughnut__total-value")).toHaveTextContent("1,828");
                    // The total is in the theme's text colour, not the dark grey it was on any theme
                    final Query vis = inFrame.querySelector(".vis");
                    play.expect("the text's colour and the total's",
                                    () -> List.of(vis.computedStyle("color").get(),
                                            inFrame.querySelector(".vis-doughnut__total-value")
                                                    .computedStyle("fill").get()))
                            .toSatisfy("the same", colours -> new HashSet<>((List<?>) colours).size() == 1);
                    if (StroomThemeDecorator.isForcedColours()) {
                        // With a high contrast theme, each slice is edged in its text colour
                        play.expect("the text's colour and a slice's edge's",
                                        () -> List.of(vis.computedStyle("color").get(),
                                                slices.first().computedStyle("stroke").get()))
                                .toSatisfy("the same", colours -> new HashSet<>((List<?>) colours).size() == 1);
                    }
                    VisStories.expectNoProblems(play);
                })
                // A doughnut for each host, each named by its host
                .story("GridSeries", VisStories.story("Doughnut",
                        VisStories.settings("names", "Status", "values", "Requests", "gridSeries", "Host"),
                        REQUESTS_BY_STATUS))
                .withPlay(play -> {
                    VisStories.drawn(play, SLICE, 12);
                    final Query names = play.withinFrame(VisStories.frame(play)).querySelectorAll(".vis-grid__name");
                    play.expect("the doughnuts' names", () -> names.textContents().get())
                            .toEqual(List.of("db-01", "web-01", "web-02"));
                    VisStories.expectNoProblems(play);
                })
                // The Outline fill style: each slice a line of its colour around a fade of it
                // across the ring, as StackedArea's series are
                .story("Outline", VisStories.story("Doughnut",
                        VisStories.settings("names", "Status", "values", "Requests", "fillStyle", "Outline"),
                        requestsForHost("web-01")))
                .withPlay(play -> {
                    final Query slices = VisStories.drawn(play, SLICE, 4);
                    play.expect(slices.first().computedStyle("stroke-width")).toBe("2px");
                    play.expect(slices.first().computedStyle("fill")).toMatch("url(");
                    VisStories.expectNoProblems(play);
                })
                .story("OutlineGridSeries", VisStories.story("Doughnut",
                        VisStories.settings("names", "Status", "values", "Requests", "gridSeries", "Host",
                                "fillStyle", "Outline"),
                        REQUESTS_BY_STATUS))
                .withPlay(play -> {
                    VisStories.drawn(play, SLICE, 12);
                    VisStories.expectNoProblems(play);
                })
                // Each slice of at least 5% has its name, value and share beside it, in the theme's
                // text colour (500 Error, 2%, hasn't)
                .story("Labels", VisStories.story("Doughnut",
                        VisStories.settings("names", "Status", "values", "Requests", "showLabels", "True"),
                        requestsForHost("web-01")))
                .withPlay(play -> {
                    VisStories.drawn(play, SLICE, 4);
                    final Play inFrame = play.withinFrame(VisStories.frame(play));
                    final Query names = inFrame.querySelectorAll(".vis-doughnut__label-name");
                    play.expect("the labels' names", () -> names.textContents().get())
                            .toEqual(List.of("200 OK", "301 Moved", "404 Not Found"));
                    play.expect(inFrame.querySelectorAll(".vis-doughnut__label-value").first())
                            .toHaveTextContent("1,200 (65.6%)");
                    final Query vis = inFrame.querySelector(".vis");
                    play.expect("the text's colour and a label's",
                                    () -> List.of(vis.computedStyle("color").get(),
                                            names.first().computedStyle("fill").get()))
                            .toSatisfy("the same", colours -> new HashSet<>((List<?>) colours).size() == 1);
                    VisStories.expectNoProblems(play);
                })
                // A Colour field gives a slice its own colour
                .story("ColourField", VisStories.story("Doughnut",
                        VisStories.settings("names", "Status", "values", "Requests", "colour", "Colour"),
                        VisTable.builder().text("Status").number("Requests").text("Colour")
                                .row("200 OK", 1200, "#2ca02c").row("500 Error", 37, "#d62728").build()))
                .withPlay(play -> {
                    final Query slices = VisStories.drawn(play, SLICE, 2);
                    play.expect(slices.first().computedStyle("fill")).toBe("rgb(44, 160, 44)");
                    play.expect(slices.nth(1).computedStyle("fill")).toBe("rgb(214, 39, 40)");
                    VisStories.expectNoProblems(play);
                })
                // A slice is its share of the total, so a status with none, or fewer than none, has
                // no slice
                .story("NothingToSlice", VisStories.story("Doughnut",
                        VisStories.settings("names", "Status", "values", "Requests"),
                        VisTable.builder().text("Status").number("Requests")
                                .row("200 OK", 900).row("301 Moved", 0).row("404 Not Found", -3).row("500 Error", null)
                                .row("503 Unavailable", 100)
                                .build()))
                .withPlay(play -> {
                    VisStories.drawn(play, SLICE, 2);
                    play.expect(play.withinFrame(VisStories.frame(play)).querySelector(".vis-doughnut__total-value"))
                            .toHaveTextContent("1,000");
                    VisStories.expectNoProblems(play);
                })
                // One status: the whole ring
                .story("OneValue", VisStories.story("Doughnut",
                        VisStories.settings("names", "Status", "values", "Requests"),
                        VisTable.builder().text("Status").number("Requests").row("200 OK", 900).build()))
                .withPlay(play -> {
                    final Query slice = VisStories.drawn(play, SLICE, 1).first();
                    play.expect("the slice's width", () -> slice.width().get()).toBeGreaterThan(200);
                    play.expect(slice).toHaveAttribute("aria-label", "200 OK, Requests: 900, Share: 100.0%");
                    VisStories.expectNoProblems(play);
                })
                // A search that found nothing: 'No data' where the ring would be
                .story("NoData", VisStories.story("Doughnut",
                        VisStories.settings("names", "Status", "values", "Requests"),
                        VisTable.builder().text("Status").number("Requests").build()))
                .withPlay(play -> {
                    final Query chart = VisStories.drawn(play, "svg.vis-doughnut", 1).first();
                    final Play inFrame = play.withinFrame(VisStories.frame(play));
                    play.waitFor(() -> play.expect(chart).toHaveAttribute("aria-label",
                            "Doughnut of Requests by Status: no data"));
                    play.expect(inFrame.querySelector(".vis-empty")).toHaveTextContent("No data");
                    VisStories.expectNoProblems(play);
                });

        registry.component(VisStories.TITLE + "TreeMap", ChartVisStories.class)
                .layout(StoryLayout.FULLSCREEN)
                .tags(VisStories.TAG)
                // What Stroom is told of a selected box (the Selection column of the design): Ctrl+Click
                // tells it of the box, and again of nothing
                .story("SelectionPayload", VisStories.story("TreeMap",
                        VisStories.settings("names", "Status", "values", "Requests", "series", "Host"),
                        REQUESTS_BY_STATUS))
                .withPlay(play -> {
                    VisStories.drawn(play, TREE_BOX);
                    final Query point = play.withinFrame(VisStories.frame(play))
                            .querySelectorAll(TREE_BOX).first();
                    VisStories.expectSelectionOf(play, point,
                            "[{\"key\":\"db-01~#~200 OK\",\"name\":\"200 OK\",\"series\":\"db-01\","
                            + "\"value\":1380}]");
                    VisStories.expectNoProblems(play);
                })
                // A block for each host, the biggest first, holding a box for each status, its words in
                // black or white, whichever reads on it
                .story("ByHost", VisStories.story("TreeMap",
                        VisStories.settings("names", "Status", "values", "Requests", "series", "Host"),
                        REQUESTS_BY_STATUS))
                .withPlay(play -> {
                    final Query boxes = VisStories.drawn(play, TREE_BOX, 12);
                    final Play inFrame = play.withinFrame(VisStories.frame(play));
                    final Query chart = inFrame.querySelector(".vis-treemap");
                    play.waitFor(() -> play.expect(chart).toHaveAttribute("aria-label",
                            "Tree map of Requests by Status: 12 boxes in 3 series, largest Requests 1,380 "
                            + "(200 OK, db-01)"));
                    final Query headers = inFrame.querySelectorAll(".vis-treemap__series");
                    play.expect("the blocks' headers", () -> headers.textContents().get())
                            .toEqual(List.of("db-01", "web-02", "web-01"));
                    play.expect(boxes.first().computedStyle("color"))
                            .toSatisfy("black or white", colour -> "rgb(0, 0, 0)".equals(colour)
                                                                   || "rgb(255, 255, 255)".equals(colour));
                    if (StroomThemeDecorator.isForcedColours()) {
                        // With a high contrast theme, the boxes keep their colours, edged in its text colour
                        final Query vis = inFrame.querySelector(".vis");
                        play.expect("the text's colour and a box's edge's",
                                        () -> List.of(vis.computedStyle("color").get(),
                                                boxes.first().computedStyle("border-top-color").get()))
                                .toSatisfy("the same", colours -> new HashSet<>((List<?>) colours).size() == 1);
                    }
                    VisStories.expectNoProblems(play);
                })
                // Without a Series field, no blocks: each box has a colour of its own
                .story("NoSeries", VisStories.story("TreeMap",
                        VisStories.settings("names", "Status", "values", "Requests"), requestsForHost("web-01")))
                .withPlay(play -> {
                    final Query boxes = VisStories.drawn(play, TREE_BOX, 4);
                    play.expect(play.withinFrame(VisStories.frame(play)).querySelectorAll(".vis-treemap__block"))
                            .toHaveLength(0);
                    play.expect("how many colours the boxes are", () -> {
                        final List<String> fills = new ArrayList<>();
                        for (int i = 0; i < boxes.count().get(); i++) {
                            fills.add(boxes.nth(i).computedStyle("background-color").get());
                        }
                        return new HashSet<>(fills).size();
                    }).toBe(4);
                    VisStories.expectNoProblems(play);
                })
                // The tree map is one tab stop, starting at the biggest host's header. Right moves on to
                // its biggest box, Down to the next host's, and each box's tooltip has its share of
                // the whole
                .story("Keyboard", VisStories.story("TreeMap",
                        VisStories.settings("names", "Status", "values", "Requests", "series", "Host"),
                        REQUESTS_BY_STATUS))
                .withPlay(play -> {
                    VisStories.drawn(play, TREE_BOX, 12);
                    final Play inFrame = play.withinFrame(VisStories.frame(play));
                    final Query tooltip = VisStories.tooltip(play);
                    VisStories.focusTabStop(play);
                    play.waitFor(() -> play.expect(VisStories.focusedPoint(play).attribute("aria-label"))
                            .toMatch("db-01, Boxes: 4, Requests: "));
                    VisStories.press(play, "{ArrowRight}");
                    play.waitFor(() -> play.expect(inFrame.getByRole("img",
                            "db-01, Status: 200 OK, Requests: 1,380, Share: 22.8%")).toHaveFocus());
                    play.waitFor(() -> play.expect(tooltip).toBeVisible());
                    play.expect(VisStories.focusedPoint(play).computedStyle("outline-style")).toBe("solid");
                    VisStories.press(play, "{ArrowDown}");
                    play.waitFor(() -> play.expect(inFrame.getByRole("img",
                            "web-02, Status: 200 OK, Requests: 1,290, Share: 21.3%")).toHaveFocus());
                    VisStories.expectNoProblems(play);
                })
                // Clicking a host's header zooms in to it, its block filling the tree map, and clicking
                // it again zooms back out (as the legacy ZoomableTreeMap did)
                .story("Zoom", VisStories.story("TreeMap",
                        VisStories.settings("names", "Status", "values", "Requests", "series", "Host"),
                        REQUESTS_BY_STATUS))
                .withPlay(play -> {
                    final Query boxes = VisStories.drawn(play, TREE_BOX, 12);
                    final Play inFrame = play.withinFrame(VisStories.frame(play));
                    final Query chart = inFrame.querySelector(".vis-treemap");
                    play.click(inFrame.getByText("web-02", ".vis-treemap__series"));
                    play.waitFor(() -> play.expect(boxes).toHaveLength(4));
                    play.waitFor(() -> play.expect(chart.attribute("aria-label")).toMatch("showing web-02 on its own"));
                    play.expect(inFrame.querySelector(".vis-treemap__zoom")).toHaveTextContent("−");
                    final Query block = inFrame.querySelector(".vis-treemap__block");
                    play.expect("the block's width less the tree map's",
                            () -> block.width().get() - chart.width().get()).toBeGreaterThan(-20);
                    play.click(inFrame.getByText("web-02", ".vis-treemap__series"));
                    play.waitFor(() -> play.expect(boxes).toHaveLength(12));
                    VisStories.expectNoProblems(play);
                })
                // Enter on a host's header zooms in to it, the focus staying on the header, and Enter
                // again zooms back out
                .story("KeyboardZoom", VisStories.story("TreeMap",
                        VisStories.settings("names", "Status", "values", "Requests", "series", "Host"),
                        REQUESTS_BY_STATUS))
                .withPlay(play -> {
                    final Query boxes = VisStories.drawn(play, TREE_BOX, 12);
                    VisStories.focusTabStop(play);
                    // A header is a button, saying whether its host is zoomed in to (expanded)
                    final Query header = VisStories.focusedPoint(play);
                    play.expect(header).toHaveAttribute("role", "button");
                    play.expect(header).toHaveAttribute("aria-expanded", "false");
                    VisStories.press(play, "{Enter}");
                    play.waitFor(() -> play.expect(boxes).toHaveLength(4));
                    play.waitFor(() -> play.expect(VisStories.focusedPoint(play).attribute("aria-label"))
                            .toMatch("db-01, Boxes: 4"));
                    play.waitFor(() -> play.expect(VisStories.focusedPoint(play).attribute("aria-label"))
                            .toMatch("Shown: on its own"));
                    play.expect(VisStories.focusedPoint(play)).toHaveAttribute("aria-expanded", "true");
                    VisStories.press(play, "{Enter}");
                    play.waitFor(() -> play.expect(boxes).toHaveLength(12));
                    VisStories.expectNoProblems(play);
                })
                // With Zoom Into Series off, a header does nothing and isn't a stop for the keyboard
                .story("NoZoom", VisStories.story("TreeMap",
                        VisStories.settings("names", "Status", "values", "Requests", "series", "Host",
                                "zoomSeries", "false"),
                        REQUESTS_BY_STATUS))
                .withPlay(play -> {
                    final Query boxes = VisStories.drawn(play, TREE_BOX, 12);
                    final Play inFrame = play.withinFrame(VisStories.frame(play));
                    play.click(inFrame.getByText("web-02", ".vis-treemap__series"));
                    play.expect(boxes).toHaveLength(12);
                    VisStories.focusTabStop(play);
                    play.waitFor(() -> play.expect(inFrame.getByRole("img",
                            "db-01, Status: 200 OK, Requests: 1,380, Share: 22.8%")).toHaveFocus());
                    VisStories.expectNoProblems(play);
                })
                // The mouse over a box shows its tooltip and fades the others
                .story("HoverShowsTooltip", VisStories.story("TreeMap",
                        VisStories.settings("names", "Status", "values", "Requests", "series", "Host"),
                        REQUESTS_BY_STATUS))
                .withPlay(play -> {
                    final Query boxes = VisStories.drawn(play, TREE_BOX, 12);
                    final Query tooltip = VisStories.tooltip(play);
                    play.hover(boxes.first());
                    play.waitFor(() -> play.expect(tooltip).toBeVisible());
                    play.expect(tooltip).toHaveTextContent(TextMatch.containing("1,380"));
                    // Only a faded box's background fades, its words in the theme's text colour so that they
                    // can still be read
                    play.waitFor(() -> play.expect(boxes.nth(1)).toHaveClass("vis-treemap__box--faded"));
                    final Query vis = play.withinFrame(VisStories.frame(play)).querySelector(".vis");
                    play.expect("the text's colour and a faded box's words'",
                                    () -> List.of(vis.computedStyle("color").get(),
                                            boxes.nth(1).computedStyle("color").get()))
                            .toSatisfy("the same", colours -> new HashSet<>((List<?>) colours).size() == 1);
                    VisStories.expectNoProblems(play);
                })
                // A box's area is its value, so a status with none, or fewer than none, has no box
                .story("NothingToSize", VisStories.story("TreeMap",
                        VisStories.settings("names", "Status", "values", "Requests"),
                        VisTable.builder().text("Status").number("Requests")
                                .row("200 OK", 900).row("301 Moved", 0).row("404 Not Found", -3).row("500 Error", null)
                                .row("503 Unavailable", 100)
                                .build()))
                .withPlay(play -> {
                    VisStories.drawn(play, TREE_BOX, 2);
                    VisStories.expectNoProblems(play);
                })
                // One status: one box, filling the tree map
                .story("OneValue", VisStories.story("TreeMap",
                        VisStories.settings("names", "Status", "values", "Requests"),
                        VisTable.builder().text("Status").number("Requests").row("200 OK", 900).build()))
                .withPlay(play -> {
                    final Query box = VisStories.drawn(play, TREE_BOX, 1).first();
                    play.expect("the box's width", () -> box.width().get()).toBeGreaterThan(300);
                    VisStories.expectNoProblems(play);
                })
                // A search that found nothing: 'No data' where the boxes would be
                .story("NoData", VisStories.story("TreeMap",
                        VisStories.settings("names", "Status", "values", "Requests", "series", "Host"),
                        VisTable.builder().text("Host").text("Status").number("Requests").build()))
                .withPlay(play -> {
                    final Query chart = VisStories.drawn(play, ".vis-treemap", 1).first();
                    play.waitFor(() -> play.expect(chart).toHaveAttribute("aria-label",
                            "Tree map of Requests by Status: no data"));
                    play.expect(play.withinFrame(VisStories.frame(play)).querySelector(".vis-treemap__empty"))
                            .toHaveTextContent("No data");
                    VisStories.expectNoProblems(play);
                });

        addWordCloud(registry);
    }

    private static void addWordCloud(final StoryRegistry registry) {
        registry.component(VisStories.TITLE + "WordCloud", ChartVisStories.class)
                .layout(StoryLayout.FULLSCREEN)
                .tags(VisStories.TAG)
                // What Stroom is told of a selected word (the Selection column of the design): Ctrl+Click
                // tells it of the word, and again of nothing
                .story("SelectionPayload", VisStories.story("WordCloud",
                        VisStories.settings("words", "Message"), messages()))
                .withPlay(play -> {
                    VisStories.drawn(play, WORD);
                    final Query point = play.withinFrame(VisStories.frame(play))
                            .querySelectorAll(WORD).first();
                    VisStories.expectSelectionOf(play, point,
                            "[{\"key\":\"user\",\"name\":\"user\"}]");
                    VisStories.expectNoProblems(play);
                })
                // The messages' words, the most seen (user) biggest and in the middle, common words
                // (the, from) left out. Words seen once are there too (the legacy cloud left them out,
                // so showed 9 of the 19)
                .story("Messages", VisStories.story("WordCloud", VisStories.settings("words", "Message"), messages()))
                .withPlay(play -> {
                    final Query words = VisStories.drawn(play, WORD, 19);
                    final Play inFrame = play.withinFrame(VisStories.frame(play));
                    play.waitFor(() -> play.expect(inFrame.querySelector("svg.vis-word-cloud")).toHaveAttribute(
                            "aria-label", "Word cloud of Message: 19 words, most seen user 7"));
                    final Query user = inFrame.getByRole("img", "user, Count: 7");
                    play.expect(inFrame.getByRole("img", "low, Count: 1")).toBeVisible();
                    play.expect(inFrame.queryByRole("img", "the, Count: 4")).toBeNull();
                    play.expect("the size of user and of low",
                                    () -> List.of(user.height().get(),
                                            inFrame.getByRole("img", "low, Count: 1").height().get()))
                            .toSatisfy("user bigger", sizes -> (Double) ((List<?>) sizes).get(0)
                                                               > 2 * (Double) ((List<?>) sizes).get(1));
                    if (StroomThemeDecorator.isForcedColours()) {
                        // With a high contrast theme, the words are in its text colour
                        final Query vis = inFrame.querySelector(".vis");
                        play.expect("the text's colour and a word's",
                                        () -> List.of(vis.computedStyle("color").get(),
                                                words.first().computedStyle("fill").get()))
                                .toSatisfy("the same", colours -> new HashSet<>((List<?>) colours).size() == 1);
                    }
                    VisStories.expectNoProblems(play);
                })
                // Each event counted as many times as its Count says, as it is (not split into words)
                .story("Counted", VisStories.story("WordCloud",
                        VisStories.settings("words", "Event", "count", "Events", "splitWords", "false"),
                        VisTable.builder().text("Event").number("Events")
                                .row("Logon", 120)
                                .row("Logoff", 90)
                                .row("Logon failed", 40)
                                .row("Logon failed", 10)
                                .row("File read", 5)
                                .build()))
                .withPlay(play -> {
                    VisStories.drawn(play, WORD, 4);
                    final Play inFrame = play.withinFrame(VisStories.frame(play));
                    play.expect(inFrame.getByRole("img", "Logon failed, Events: 50")).toBeVisible();
                    play.waitFor(() -> play.expect(inFrame.querySelector("svg.vis-word-cloud")).toHaveAttribute(
                            "aria-label", "Word cloud of Event: 4 words, most counted Logon 120"));
                    VisStories.expectNoProblems(play);
                })
                // With the mouse over a word, it is underlined (words never fade) and its tooltip shows
                .story("Hover", VisStories.story("WordCloud", VisStories.settings("words", "Message"), messages()))
                .withPlay(play -> {
                    VisStories.drawn(play, WORD, 19);
                    final Play inFrame = play.withinFrame(VisStories.frame(play));
                    final Query login = inFrame.getByRole("img", "login, Count: 6");
                    play.hover(login);
                    play.waitFor(() -> play.expect(login.closest("g")).toHaveClass("vis-word-cloud__item--shown"));
                    play.expect(login.computedStyle("text-decoration-line")).toBe("underline");
                    final Query tooltip = VisStories.tooltip(play);
                    play.waitFor(() -> play.expect(tooltip).toBeVisible());
                    play.expect(tooltip).toHaveTextContent(TextMatch.containing("login"));
                    VisStories.expectNoProblems(play);
                })
                // The words, the most seen first, are one tab stop; Enter selects one, which is framed
                .story("Keyboard", VisStories.story("WordCloud", VisStories.settings("words", "Message"), messages()))
                .withPlay(play -> {
                    VisStories.drawn(play, WORD, 19);
                    final Play inFrame = play.withinFrame(VisStories.frame(play));
                    VisStories.focusTabStop(play);
                    play.waitFor(() -> play.expect(inFrame.getByRole("img", "user, Count: 7")).toHaveFocus());
                    // The focus ring is drawn round the word's own letters, turned with it (the
                    // browser's outline went round the text's line box, and didn't fit the word)
                    final Query ring = inFrame.querySelector(
                            "g.vis-word-cloud__item:has(:focus) .vis-word-cloud__focus");
                    play.waitFor(() -> play.expect(ring.computedStyle("display")).toBe("inline"));
                    play.expect(VisStories.focusedPoint(play).computedStyle("outline-style")).toBe("none");
                    VisStories.press(play, "{ArrowRight}");
                    final Query login = inFrame.getByRole("img", TextMatch.startingWith("login, Count: 6"));
                    play.waitFor(() -> play.expect(login).toHaveFocus());
                    VisStories.press(play, "{Enter}");
                    play.waitFor(() -> play.expect(login.closest("g")).toHaveClass("vis-word-cloud__item--selected"));
                    play.expect(inFrame.querySelectorAll(".vis-word-cloud__item--selected")).toHaveLength(1);
                    VisStories.expectNoProblems(play);
                })
                // A cloud for each host, each named by its host
                .story("GridSeries", VisStories.story("WordCloud",
                        VisStories.settings("words", "Message", "gridSeries", "Host"),
                        VisTable.builder().text("Host").text("Message")
                                .row("web-01", "Disk full")
                                .row("web-01", "Disk error")
                                .row("web-02", "Login failed")
                                .build()))
                .withPlay(play -> {
                    VisStories.drawn(play, "svg.vis-word-cloud", 2);
                    final Query names = play.withinFrame(VisStories.frame(play)).querySelectorAll(".vis-grid__name");
                    play.expect("the clouds' names", () -> names.textContents().get())
                            .toEqual(List.of("web-01", "web-02"));
                    play.expect(play.withinFrame(VisStories.frame(play)).querySelectorAll(WORD)).toHaveLength(5);
                    VisStories.expectNoProblems(play);
                })
                // One word
                .story("OneValue", VisStories.story("WordCloud", VisStories.settings("words", "Message"),
                        VisTable.builder().text("Message").row("Hello").build()))
                .withPlay(play -> {
                    VisStories.drawn(play, WORD, 1);
                    play.expect(play.withinFrame(VisStories.frame(play)).getByRole("img", "hello, Count: 1"))
                            .toBeVisible();
                    VisStories.expectNoProblems(play);
                })
                // Rows without text, or with only common or short words, have no words; letters of any
                // language are kept, and a very long word is made small enough to fit
                .story("AwkwardData", VisStories.story("WordCloud", VisStories.settings("words", "Message"),
                        VisTable.builder().text("Message")
                                .row((Object) null)
                                .row("")
                                .row("the of and ok")
                                .row("Größe café café")
                                .row("supercalifragilisticexpialidocious-and-then-some-more-to-make-it-really-long")
                                .build()))
                .withPlay(play -> {
                    VisStories.drawn(play, WORD, 3);
                    final Play inFrame = play.withinFrame(VisStories.frame(play));
                    play.waitFor(() -> play.expect(inFrame.querySelector("svg.vis-word-cloud")).toHaveAttribute(
                            "aria-label", "Word cloud of Message: 3 words, most seen café 2"));
                    play.expect(inFrame.getByRole("img", "größe, Count: 1")).toBeVisible();
                    VisStories.expectNoProblems(play);
                })
                // A search that found nothing: 'No data' where the words would be
                .story("NoData", VisStories.story("WordCloud", VisStories.settings("words", "Message"),
                        VisTable.builder().text("Message").build()))
                .withPlay(play -> {
                    final Query chart = VisStories.drawn(play, "svg.vis-word-cloud", 1).first();
                    play.waitFor(() -> play.expect(chart).toHaveAttribute("aria-label",
                            "Word cloud of Message: no data"));
                    play.expect(play.withinFrame(VisStories.frame(play)).querySelector(".vis-empty"))
                            .toHaveTextContent("No data");
                    VisStories.expectNoProblems(play);
                });
    }

    // A point given as 'x,y' to the nearest pixel, e.g. '12,34'
    private static String roundedPoint(final Object point) {
        final String[] xy = String.valueOf(point).split(",");
        return Math.round(Double.parseDouble(xy[0].trim())) + "," + Math.round(Double.parseDouble(xy[1].trim()));
    }

    // The names (aria-label) of what a query finds
    private static List<String> names(final Query query) {
        final List<String> names = new ArrayList<>();
        for (int i = 0; i < query.count().get(); i++) {
            names.add(query.nth(i).attribute("aria-label").get());
        }
        return names;
    }

    private static VisTable requestsByStatus() {
        final VisTable.Builder builder = VisTable.builder().text("Host").text("Status").number("Requests");
        for (int host = 0; host < HOSTS.length; host++) {
            for (int status = 0; status < STATUSES.length; status++) {
                builder.row(HOSTS[host], STATUSES[status], requests(host, status));
            }
        }
        return builder.build();
    }

    /// @param host A host, e.g. `web-01`.
    /// @return The host's requests by status.
    static VisTable requestsForHost(final String host) {
        final VisTable.Builder builder = VisTable.builder().text("Status").number("Requests");
        for (int status = 0; status < STATUSES.length; status++) {
            builder.row(STATUSES[status], requests(indexOf(host), status));
        }
        return builder.build();
    }

    // Many more successes than redirects, and more of those than errors
    private static int requests(final int host, final int status) {
        return (1200 - status * 350 + host * 90) / (status + 1);
    }

    private static void addTimeSeries(final StoryRegistry registry) {
        registry.component(VisStories.TITLE + "TimeSeries", ChartVisStories.class)
                .layout(StoryLayout.FULLSCREEN)
                .tags(VisStories.TAG)
                // Stroom is told the window shown as soon as the series is drawn (as the legacy chart
                // told it on every draw), not only once a window is chosen
                .story("SelectionPayload", VisStories.story("TimeSeries",
                        VisStories.settings("x", "Hour", "y", "Requests"), hourlyRequestsFor("web-01")))
                .withPlay(play -> {
                    VisStories.recordSelections(play);
                    VisStories.drawn(play, TS_LINE, 1);
                    VisStories.expectSelection(play,
                            "[{\"minTime\":\"2026-10-12T00:00:00.000Z\",\"maxTime\":\"2026-10-12T23"
                            + ":00:00.000Z\"}]");
                    VisStories.expectNoProblems(play);
                })
                // One host's requests through a day, a fade under its line, with an overview below for
                // choosing the time shown
                .story("Requests", VisStories.story("TimeSeries",
                        VisStories.settings("x", "Hour", "y", "Requests"), hourlyRequestsFor("web-01")))
                .withPlay(play -> {
                    VisStories.drawn(play, TS_LINE, 1);
                    final Play inFrame = play.withinFrame(VisStories.frame(play));
                    play.expect(inFrame.querySelector("svg.vis-time-series")).toHaveAttribute("aria-label",
                            "Time series of Requests by Hour: 1 line of 24 points from " + TS_ALL
                            + ", highest Requests 489 at 2026-10-12T17:00:00.000Z");
                    play.expect(inFrame.querySelector(".vis-time-series__header"))
                            .toHaveTextContent("Showing " + TS_ALL);
                    play.expect(inFrame.querySelector(".vis-time-series__area").attribute("d")).toBeTruthy();
                    play.expect(inFrame.querySelectorAll(".vis-time-series__overview-line")).toHaveLength(1);
                    if (StroomThemeDecorator.isForcedColours()) {
                        // With a high contrast theme, the line is over a casing in its text colour
                        play.expect(inFrame.querySelector(".vis-time-series__casing").computedStyle("display"))
                                .not().toBe("none");
                    }
                    VisStories.expectNoProblems(play);
                })
                // For a user in India (UTC+5:30) with a 12-hour format: the times are as their clock
                // shows them, in their format (as the table's), and the axis has a 12-hour clock
                .story("UserTimeZone", VisStories.story("TimeSeries",
                        VisStories.settings("x", "Hour", "y", "Requests"), hourlyRequestsFor("web-01"),
                        VisStories.preferencesInZone("{\"use\": \"ID\", \"id\": \"Asia/Kolkata\"}",
                                "dd/MM/yyyy hh:mm:ss a")))
                .withPlay(play -> {
                    VisStories.drawn(play, TS_LINE, 1);
                    final Play inFrame = play.withinFrame(VisStories.frame(play));
                    play.expect(inFrame.querySelector(".vis-time-series__header"))
                            .toHaveTextContent("Showing 12/10/2026 05:30:00 am to 13/10/2026 04:30:00 am");
                    play.expect(inFrame.querySelector("svg.vis-time-series")).toHaveAttribute("aria-label",
                            "Time series of Requests by Hour: 1 line of 24 points from 12/10/2026 05:30:00 am to "
                            + "13/10/2026 04:30:00 am, highest Requests 489 at 12/10/2026 10:30:00 pm");
                    play.expect(inFrame.querySelector("svg.vis-time-series"))
                            .toHaveTextContent(TextMatch.containing("9 AM"));
                    VisStories.expectNoProblems(play);
                })
                // A line for each host, where only the first series was drawn
                .story("ByHost", VisStories.story("TimeSeries",
                        VisStories.settings("x", "Hour", "y", "Requests", "lineSeries", "Host"), HOURLY_REQUESTS))
                .withPlay(play -> {
                    VisStories.drawn(play, TS_LINE, 3);
                    play.expect(play.withinFrame(VisStories.frame(play)).querySelector("svg.vis-time-series"))
                            .toHaveAttribute("aria-label", "Time series of Requests by Hour: 3 lines of 72 points from "
                                                           + TS_ALL + ", highest Requests 489 at "
                                                           + "2026-10-12T17:00:00.000Z (web-01)");
                    VisStories.expectNoProblems(play);
                })
                // The overview is a slider for the keyboard: Up zooms in to half the time about the
                // middle, Right moves on a tenth, and Escape shows all of it again
                .story("KeyboardWindow", VisStories.story("TimeSeries",
                        VisStories.settings("x", "Hour", "y", "Requests"), hourlyRequestsFor("web-01")))
                .withPlay(play -> {
                    final Query points = VisStories.drawn(play, TS_POINT, 24);
                    final Play inFrame = play.withinFrame(VisStories.frame(play));
                    final Query overview = inFrame.querySelector(".vis-time-series__overview");
                    final Query header = inFrame.querySelector(".vis-time-series__header");
                    play.expect(overview).toHaveAttribute("role", "slider");
                    play.run("focus the overview", () -> overview.element().get().focus());
                    play.expect(overview).toHaveFocus();
                    VisStories.press(play, "{ArrowUp}");
                    play.waitFor(() -> play.expect(header).toHaveTextContent(
                            "Showing 2026-10-12T05:45:00.000Z to 2026-10-12T17:15:00.000Z"));
                    play.expect(points).toHaveLength(12);
                    play.expect(overview.attribute("aria-valuetext")).toMatch("05:45:00.000Z to");
                    VisStories.press(play, "{ArrowRight}");
                    play.waitFor(() -> play.expect(header).toHaveTextContent(
                            "Showing 2026-10-12T06:54:00.000Z to 2026-10-12T18:24:00.000Z"));
                    VisStories.press(play, "{Escape}");
                    play.waitFor(() -> play.expect(header).toHaveTextContent("Showing " + TS_ALL));
                    play.expect(points).toHaveLength(24);
                    play.expect(overview.computedStyle("outline-style")).toBe("none");
                    play.expect(inFrame.querySelector(".vis-time-series__overview-frame").computedStyle("stroke-width"))
                            .toBe("2px");
                    VisStories.expectNoProblems(play);
                })
                // The points are one tab stop, with its time and value in its tooltip, and the overview
                // is another
                .story("Focus", VisStories.story("TimeSeries",
                        VisStories.settings("x", "Hour", "y", "Requests"), hourlyRequestsFor("web-01")))
                .withPlay(play -> {
                    VisStories.drawn(play, TS_POINT, 24);
                    final Play inFrame = play.withinFrame(VisStories.frame(play));
                    play.expect(inFrame.querySelectorAll("[tabindex='0']")).toHaveLength(2);
                    final Query pointStop = inFrame.querySelector(TS_POINT + "[tabindex='0']");
                    final Query tooltip = VisStories.tooltip(play);
                    play.run("focus the points' tab stop", () -> pointStop.element().get().focus());
                    play.waitFor(() -> play.expect(tooltip).toBeVisible());
                    play.expect(tooltip).toHaveTextContent(TextMatch.containing("2026-10-12T00:00:00.000Z"));
                    play.expect(VisStories.focusedPoint(play).computedStyle("outline-style")).toBe("solid");
                    VisStories.expectNoProblems(play);
                })
                // One value is a dot, as there is no line to see
                .story("OneValue", VisStories.story("TimeSeries", VisStories.settings("x", "Hour", "y", "Requests"),
                        VisTable.builder().date("Hour").number("Requests").row("2026-10-12T09:00:00Z", 42).build()))
                .withPlay(play -> {
                    final Query point = VisStories.drawn(play, TS_POINT, 1).first();
                    play.expect(point).toHaveClass("vis-time-series__point--lone");
                    play.expect(point.computedStyle("opacity")).toBe("1");
                    VisStories.expectNoProblems(play);
                })
                // A search that found nothing: 'No data' where the lines would be, and no overview
                .story("NoData", VisStories.story("TimeSeries", VisStories.settings("x", "Hour", "y", "Requests"),
                        VisTable.builder().date("Hour").number("Requests").build()))
                .withPlay(play -> {
                    final Query chart = VisStories.drawn(play, "svg.vis-time-series", 1).first();
                    final Play inFrame = play.withinFrame(VisStories.frame(play));
                    play.expect(inFrame.querySelector(".vis-empty")).toHaveTextContent("No data");
                    play.expect(inFrame.querySelector(".vis-time-series__overview")).not().toBeVisible();
                    play.waitFor(() -> play.expect(chart)
                            .toHaveAttribute("aria-label", "Time series of Requests by Hour: no data"));
                    VisStories.expectNoProblems(play);
                });
    }

    private static VisTable hourlyRequests() {
        final VisTable.Builder builder = VisTable.builder().date("Hour").text("Host").number("Requests");
        for (int hour = 0; hour < 24; hour++) {
            for (int host = 0; host < HOSTS.length; host++) {
                builder.row(hourStart(hour), HOSTS[host], hourlyRequests(hour, host));
            }
        }
        return builder.build();
    }

    // One host's requests each hour of a day, the same as its rows of HOURLY_REQUESTS
    private static VisTable hourlyRequestsFor(final String host) {
        final VisTable.Builder builder = VisTable.builder().date("Hour").number("Requests");
        for (int hour = 0; hour < 24; hour++) {
            builder.row(hourStart(hour), hourlyRequests(hour, indexOf(host)));
        }
        return builder.build();
    }

    private static long hourStart(final int hour) {
        return VisTable.date("2026-10-12T00:00:00Z") + hour * 3_600_000L;
    }

    // Busy in the working day, quiet at night
    private static int hourlyRequests(final int hour, final int host) {
        final int daytime = hour >= 8 && hour < 18
                ? 400
                : 60;
        return daytime + (hour * 37 + host * 53) % 90 - host * 25;
    }

    /// @return Ten responses from each host, with how long each took and how big it was.
    static VisTable responseSizes() {
        final VisTable.Builder builder = VisTable.builder().text("Host").number("Duration").number("Bytes");
        for (int host = 0; host < HOSTS.length; host++) {
            for (int i = 0; i < 10; i++) {
                // Bigger responses take longer
                final int bytes = 2_000 + i * 9_000 + host * 3_000;
                builder.row(HOSTS[host], 20 + bytes / 400 + (i * 31 + host * 17) % 40, bytes);
            }
        }
        return builder.build();
    }

    private static VisTable messages() {
        return VisTable.builder()
                .text("Message")
                .row("User login succeeded")
                .row("User login failed: bad password")
                .row("User login failed: account locked")
                .row("User logout")
                .row("File uploaded to the shared folder")
                .row("File downloaded from the shared folder")
                .row("User login succeeded")
                .row("Password changed")
                .row("User login succeeded")
                .row("Disk space low on the shared folder")
                .row("User login failed: bad password")
                .row("File deleted from the shared folder")
                .build();
    }

    private static int indexOf(final String host) {
        for (int i = 0; i < HOSTS.length; i++) {
            if (HOSTS[i].equals(host)) {
                return i;
            }
        }
        throw new IllegalArgumentException("No host " + host);
    }
}
