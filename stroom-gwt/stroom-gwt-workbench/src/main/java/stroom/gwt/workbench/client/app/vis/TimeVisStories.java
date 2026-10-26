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
import stroom.gwt.workbench.framework.client.play.Play;
import stroom.gwt.workbench.framework.client.play.Query;
import stroom.gwt.workbench.framework.client.play.TextMatch;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;

import java.util.HashSet;
import java.util.List;
import java.util.Map;


/// The stories of the content pack's visualisations of events over time: `DayWeekHeatMap`,
/// `HourDayMultiHeatMap`, `HourDayPointMap`, `HourDaySessionMap`, `Session` and
/// `SeriesSessionMap`. See [VisStories].
public final class TimeVisStories {

    private static final String[] USERS = {"alice", "bob", "carol"};
    private static final long HOUR = 3_600_000L;
    private static final long DAY = 24 * HOUR;
    // A Monday
    private static final long START = VisTable.date("2026-09-07T00:00:00Z");

    private static final Map<String, String> DAILY_LOGINS = VisStories.settings("days", "Day", "values", "Logins");
    private static final Map<String, String> HOURLY_LOGINS = VisStories.settings("series", "User", "hours", "Hour",
            "values", "Logins");
    // The asset-based heat maps' cells, and point map's points
    private static final String HEAT_CELL = "rect.vis-heat__cell";
    private static final String POINT = "circle.vis-point-map__point";
    // The asset-based session maps' and timelines' bars
    private static final String SESSION_MAP_BAR = "rect.vis-session-map__session";
    private static final String TIMELINE_BAR = "rect.vis-timeline__session";
    private static final long MINUTE = 60_000L;
    private static final Map<String, String> EVENTS_BY_USER = VisStories.settings("series", "User",
            "eventTime", "Time");
    private static final Map<String, String> SESSIONS_BY_USER = VisStories.settings("series", "User",
            "startTime", "Start", "endTime", "End");

    private TimeVisStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        addDayWeekHeatMap(registry);
        addHourDayMultiHeatMap(registry);
        addHourDayPointMap(registry);
        addHourDaySessionMap(registry);
        addSession(registry);
        addSeriesSessionMap(registry);
    }

    private static void addDayWeekHeatMap(final StoryRegistry registry) {
        registry.component(VisStories.TITLE + "DayWeekHeatMap", TimeVisStories.class)
                .layout(StoryLayout.FULLSCREEN)
                .tags(VisStories.TAG)
                // What Stroom is told of a selected day (the Selection column of the design): Ctrl+Click
                // tells it of the day, and again of nothing
                .story("SelectionPayload", VisStories.story("DayWeekHeatMap", DAILY_LOGINS, dailyLogins()))
                .withPlay(play -> {
                    VisStories.drawn(play, HEAT_CELL);
                    final Query point = play.withinFrame(VisStories.frame(play))
                            .querySelectorAll(HEAT_CELL).first();
                    VisStories.expectSelectionOf(play, point,
                            "[{\"key\":\"~#~2026-09-07T00:00:00.000Z\",\"series\":\"\",\"x\":\"2026"
                            + "-09-07T00:00:00.000Z\",\"value\":120}]");
                    VisStories.expectNoProblems(play);
                })
                // A cell for each day of six weeks, quieter at the weekends, the highest day outlined
                // and the lowest outlined in dashes, so that they don't rest on colour alone
                .story("SixWeeks", VisStories.story("DayWeekHeatMap", DAILY_LOGINS, dailyLogins()))
                .withPlay(play -> {
                    final Query cells = VisStories.drawn(play, HEAT_CELL, 42);
                    final Play inFrame = play.withinFrame(VisStories.frame(play));
                    play.expect(inFrame.querySelector("svg.vis-heat")).toHaveAttribute("aria-label",
                            "Heat map of Logins by day and week: 42 values, highest 159 on Fri 18 Sep 2026, "
                            + "lowest 15 on Sat 17 Oct 2026");
                    final Query highest = inFrame.querySelector(".vis-heat__cell--highest");
                    play.expect(highest).toHaveAttribute("aria-label", "Fri 18 Sep 2026: 159 (highest)");
                    play.expect(inFrame.querySelector(".vis-heat__cell--lowest"))
                            .toHaveAttribute("aria-label", "Sat 17 Oct 2026: 15 (lowest)");
                    // Each is ringed just outside it in the text's colour, the lowest in dashes, so that
                    // the ring shows whatever the cell's colour
                    final Query vis = inFrame.querySelector(".vis");
                    final Query highestRing = inFrame.querySelector(".vis-heat__ring--highest");
                    final Query lowestRing = inFrame.querySelector(".vis-heat__ring--lowest");
                    play.expect(highestRing.computedStyle("stroke-dasharray")).toBe("none");
                    play.expect(lowestRing.computedStyle("stroke-dasharray")).not().toBe("none");
                    play.expect("the text's colour and the highest cell's ring's",
                                    () -> List.of(vis.computedStyle("color").get(),
                                            highestRing.computedStyle("stroke").get()))
                            .toSatisfy("the same", colours -> new HashSet<>((List<?>) colours).size() == 1);
                    // A key of the nine colours beside the map
                    play.expect(inFrame.querySelectorAll(".vis-heat__key-swatch")).toHaveLength(9);
                    play.expect(cells).toHaveLength(42);
                    if (StroomThemeDecorator.isForcedColours()) {
                        // With a high contrast theme, each cell keeps its heat colour (the ring is in
                        // the theme's text colour, as above), and the places without a value are edged
                        // in the text colour
                        play.expect("the text's colour and an empty place's edge",
                                        () -> List.of(vis.computedStyle("color").get(),
                                                inFrame.querySelectorAll(".vis-heat__slot").first()
                                                        .computedStyle("stroke").get()))
                                .toSatisfy("the same", colours -> new HashSet<>((List<?>) colours).size() == 1);
                        play.expect("the background's colour and a cell's",
                                        () -> List.of(vis.computedStyle("background-color").get(),
                                                cells.first().computedStyle("fill").get()))
                                .toSatisfy("different", colours -> new HashSet<>((List<?>) colours).size() == 2);
                    }
                    VisStories.expectNoProblems(play);
                })
                // Right moves along a row to the same day of the next week
                .story("Keyboard", VisStories.story("DayWeekHeatMap", DAILY_LOGINS, dailyLogins()))
                .withPlay(play -> {
                    VisStories.drawn(play, HEAT_CELL, 42);
                    VisStories.focusTabStop(play);
                    play.expect(VisStories.focusedPoint(play)).toHaveAttribute("aria-label", "Mon 7 Sep 2026: 120");
                    VisStories.press(play, "{ArrowRight}");
                    play.expect(VisStories.focusedPoint(play)).toHaveAttribute("aria-label", "Mon 14 Sep 2026: 123");
                    final Query tooltip = VisStories.tooltip(play);
                    play.waitFor(() -> play.expect(tooltip).toBeVisible());
                    play.expect(tooltip).toHaveTextContent(TextMatch.containing("Logins"));
                    play.expect(VisStories.focusedPoint(play).computedStyle("outline-style")).toBe("solid");
                    VisStories.expectNoProblems(play);
                })
                // The legend hides the days of a colour: here the highest, from 143 to 159
                .story("HideColour", VisStories.story("DayWeekHeatMap", DAILY_LOGINS, dailyLogins()))
                .withPlay(play -> {
                    final Query cells = VisStories.drawn(play, HEAT_CELL, 42);
                    play.click(play.screen().getByRole("button", "Show legend"));
                    final Query top = play.withinFrame(VisStories.frame(play)).getByRole("button", "143 to 159");
                    play.waitFor(() -> play.expect(top).toBeVisible());
                    play.click(top);
                    play.waitFor(() -> play.expect(cells).toHaveLength(32));
                    VisStories.expectNoProblems(play);
                })
                // A day is its UTC day: 23:30 on a Sunday is Sunday, whatever the browser's time zone,
                // where it was the browser's day. One value is in the highest colour
                .story("OneValue", VisStories.story("DayWeekHeatMap", DAILY_LOGINS,
                        VisTable.builder().date("Day").number("Logins").row("2026-09-13T23:30:00Z", 5).build()))
                .withPlay(play -> {
                    final Query cell = VisStories.drawn(play, HEAT_CELL, 1).first();
                    play.expect(cell).toHaveAttribute("aria-label", "Sun 13 Sep 2026: 5 (highest)");
                    VisStories.expectNoProblems(play);
                })
                // Two values on the same day are added together, where the second was drawn over the
                // first
                .story("SameDay", VisStories.story("DayWeekHeatMap", DAILY_LOGINS,
                        VisTable.builder().date("Day").number("Logins")
                                .row("2026-09-07T09:00:00Z", 3)
                                .row("2026-09-07T15:00:00Z", 4)
                                .build()))
                .withPlay(play -> {
                    final Query cell = VisStories.drawn(play, HEAT_CELL, 1).first();
                    play.expect(cell).toHaveAttribute("aria-label", "Mon 7 Sep 2026: 7 (highest)");
                    VisStories.expectNoProblems(play);
                })
                // A search that found nothing: 'No data' where the map would be
                .story("NoData", VisStories.story("DayWeekHeatMap", DAILY_LOGINS,
                        VisTable.builder().date("Day").number("Logins").build()))
                .withPlay(play -> {
                    final Query chart = VisStories.drawn(play, "svg.vis-heat", 1).first();
                    play.expect(play.withinFrame(VisStories.frame(play)).querySelector(".vis-empty"))
                            .toHaveTextContent("No data");
                    play.waitFor(() -> play.expect(chart)
                            .toHaveAttribute("aria-label", "Heat map of Logins by day and week: no data"));
                    VisStories.expectNoProblems(play);
                });
    }

    private static void addHourDayMultiHeatMap(final StoryRegistry registry) {
        registry.component(VisStories.TITLE + "HourDayMultiHeatMap", TimeVisStories.class)
                .layout(StoryLayout.FULLSCREEN)
                .tags(VisStories.TAG)
                // What Stroom is told of a selected hour (the Selection column of the design): Ctrl+Click
                // tells it of the hour, and again of nothing
                .story("SelectionPayload", VisStories.story("HourDayMultiHeatMap", HOURLY_LOGINS, hourlyLoginsByUser()))
                .withPlay(play -> {
                    VisStories.drawn(play, HEAT_CELL);
                    final Query point = play.withinFrame(VisStories.frame(play))
                            .querySelectorAll(HEAT_CELL).first();
                    VisStories.expectSelectionOf(play, point,
                            "[{\"key\":\"alice~#~2026-09-07T00:00:00.000Z\",\"series\":\"alice\",\""
                            + "x\":\"2026-09-07T00:00:00.000Z\",\"value\":1}]");
                    VisStories.expectNoProblems(play);
                })
                // A heat map of each user's logins by hour of each day of a week, in the same colours
                // and over the same days
                .story("ByUser", VisStories.story("HourDayMultiHeatMap", HOURLY_LOGINS, hourlyLoginsByUser()))
                .withPlay(play -> {
                    final Query maps = VisStories.drawn(play, "svg.vis-heat", 3);
                    play.expect(maps.first()).toHaveAttribute("aria-label",
                            "Heat map of Logins by hour and day, alice: 57 values, highest 18 on Mon 7 Sep 2026, "
                            + "11:00, lowest 1 on Mon 7 Sep 2026, 00:00");
                    final Play inFrame = play.withinFrame(VisStories.frame(play));
                    play.expect(inFrame.querySelectorAll(HEAT_CELL)).toHaveLength(57 + 56 + 56);
                    // The highest value (18 in each map, at several hours) is in the same colour in
                    // each map
                    final Query highest = inFrame.querySelectorAll(".vis-heat__cell--highest");
                    play.expect("how many cells are the highest", () -> highest.count().get())
                            .toBeGreaterThan(2);
                    play.expect("the highest cells' colours in each map",
                                    () -> List.of(highestIn(inFrame, 1).computedStyle("fill").get(),
                                            highestIn(inFrame, 2).computedStyle("fill").get(),
                                            highestIn(inFrame, 3).computedStyle("fill").get()))
                            .toSatisfy("the same", colours -> new HashSet<>((List<?>) colours).size() == 1);
                    VisStories.expectNoProblems(play);
                })
                // Each heat map is a tab stop, as each chart of a grid is, its hours named by their
                // day and hour
                .story("Focus", VisStories.story("HourDayMultiHeatMap", HOURLY_LOGINS, hourlyLoginsByUser()))
                .withPlay(play -> {
                    VisStories.drawn(play, "svg.vis-heat", 3);
                    final Query tabStops = play.withinFrame(VisStories.frame(play)).querySelectorAll("[tabindex='0']");
                    play.waitFor(() -> play.expect(tabStops).toHaveLength(3));
                    final Query tooltip = VisStories.tooltip(play);
                    play.run("focus the first heat map's tab stop", () -> tabStops.first().element().get().focus());
                    play.expect(VisStories.focusedPoint(play)).toHaveAttribute("aria-label",
                            "Mon 7 Sep 2026, 00:00: 1 (lowest)");
                    play.waitFor(() -> play.expect(tooltip).toBeVisible());
                    play.expect(tooltip).toHaveTextContent(TextMatch.containing("alice"));
                    VisStories.expectNoProblems(play);
                })
                // A search that found nothing: 'No data' where the maps would be
                .story("NoData", VisStories.story("HourDayMultiHeatMap", HOURLY_LOGINS,
                        VisTable.builder().text("User").date("Hour").number("Logins").build()))
                .withPlay(play -> {
                    final Query chart = VisStories.drawn(play, "svg.vis-heat", 1).first();
                    play.expect(play.withinFrame(VisStories.frame(play)).querySelector(".vis-empty"))
                            .toHaveTextContent("No data");
                    play.waitFor(() -> play.expect(chart)
                            .toHaveAttribute("aria-label", "Heat map of Logins by hour and day: no data"));
                    VisStories.expectNoProblems(play);
                });
    }

    private static void addHourDayPointMap(final StoryRegistry registry) {
        registry.component(VisStories.TITLE + "HourDayPointMap", TimeVisStories.class)
                .layout(StoryLayout.FULLSCREEN)
                .tags(VisStories.TAG)
                // What Stroom is told of a selected event (the Selection column of the design): Ctrl+Click
                // tells it of the event, and again of nothing
                .story("SelectionPayload", VisStories.story("HourDayPointMap",
                        VisStories.settings("pointSeries", "User", "eventTime", "Time", "value", "Bytes"), events()))
                .withPlay(play -> {
                    VisStories.drawn(play, POINT);
                    final Query point = play.withinFrame(VisStories.frame(play))
                            .querySelectorAll(POINT).first();
                    VisStories.expectSelectionOf(play, point,
                            "[{\"key\":\"alice~#~2026-09-07T08:00:00.000Z\",\"series\":\"alice\",\""
                            + "x\":\"2026-09-07T08:00:00.000Z\",\"value\":500}]");
                    VisStories.expectNoProblems(play);
                })
                // Each user's events at their time of day, day by day, each user in a lane of their own:
                // all 108, where events at the same time of day on different days were drawn as one
                .story("ByUser", VisStories.story("HourDayPointMap",
                        VisStories.settings("pointSeries", "User", "eventTime", "Time", "value", "Bytes"), events()))
                .withPlay(play -> {
                    final Query points = VisStories.drawn(play, POINT, 108);
                    final Play inFrame = play.withinFrame(VisStories.frame(play));
                    play.expect(inFrame.querySelector("svg.vis-point-map"))
                            .toHaveAttribute("aria-label", "Point map of Time by hour and day: 108 events in 3 series "
                                                           + "over 3 days, first 2026-09-07T08:00:00.000Z");
                    if (StroomThemeDecorator.isForcedColours()) {
                        // With a high contrast theme, each point keeps its series' colour, edged in the
                        // theme's text colour
                        final Query vis = inFrame.querySelector(".vis");
                        play.expect("the text's colour and a point's edge",
                                        () -> List.of(vis.computedStyle("color").get(),
                                                points.first().computedStyle("stroke").get()))
                                .toSatisfy("the same", colours -> new HashSet<>((List<?>) colours).size() == 1);
                    }
                    VisStories.expectNoProblems(play);
                })
                // A chart for each user, over the same days
                .story("GridSeries", VisStories.story("HourDayPointMap",
                        VisStories.settings("gridSeries", "User", "eventTime", "Time", "value", "Bytes"), events()))
                .withPlay(play -> {
                    final Query maps = VisStories.drawn(play, "svg.vis-point-map", 3);
                    play.expect(maps.nth(1)).toHaveAttribute("aria-label", "Point map of Time by hour and day, bob: "
                                                                          + "36 events over 3 days, first "
                                                                          + "2026-09-07T09:00:00.000Z");
                    play.expect(play.withinFrame(VisStories.frame(play)).querySelectorAll(POINT)).toHaveLength(108);
                    VisStories.expectNoProblems(play);
                })
                // For a user on Kiritimati (UTC+14) with a 12-hour format: the days and times of day are
                // as their clock shows them, so the evening events (in UTC) are on the next morning,
                // over 4 days rather than 3, and the hours have a 12-hour clock
                .story("UserTimeZone", VisStories.story("HourDayPointMap",
                        VisStories.settings("pointSeries", "User", "eventTime", "Time", "value", "Bytes"), events(),
                        VisStories.preferencesInZone("{\"use\": \"ID\", \"id\": \"Pacific/Kiritimati\"}",
                                "dd/MM/yyyy hh:mm:ss a")))
                .withPlay(play -> {
                    VisStories.drawn(play, POINT, 108);
                    final Play inFrame = play.withinFrame(VisStories.frame(play));
                    play.expect(inFrame.querySelector("svg.vis-point-map"))
                            .toHaveAttribute("aria-label", "Point map of Time by hour and day: 108 events in 3 series "
                                                           + "over 4 days, first 07/09/2026 10:00:00 pm");
                    play.expect(inFrame.querySelector("svg.vis-point-map"))
                            .toHaveTextContent(TextMatch.containing("Thu 10 Sep"));
                    play.expect(inFrame.querySelector("svg.vis-point-map"))
                            .toHaveTextContent(TextMatch.containing("6 PM"));
                    final Query tooltip = VisStories.tooltip(play);
                    VisStories.focusTabStop(play);
                    play.waitFor(() -> play.expect(tooltip).toBeVisible());
                    play.expect(tooltip).toHaveTextContent(TextMatch.containing("07/09/2026 10:00:00 pm"));
                    VisStories.expectNoProblems(play);
                })
                // An event is a tab stop, with its time and value in its tooltip
                .story("Focus", VisStories.story("HourDayPointMap",
                        VisStories.settings("pointSeries", "User", "eventTime", "Time", "value", "Bytes"), events()))
                .withPlay(play -> {
                    VisStories.drawn(play, POINT, 108);
                    final Query tooltip = VisStories.tooltip(play);
                    VisStories.focusTabStop(play);
                    play.waitFor(() -> play.expect(tooltip).toBeVisible());
                    play.expect(tooltip).toHaveTextContent(TextMatch.containing("2026-09-07T08:00:00.000Z"));
                    play.expect(tooltip).toHaveTextContent(TextMatch.containing("Bytes"));
                    VisStories.expectNoProblems(play);
                })
                // A search that found nothing: 'No data' where the points would be
                .story("NoData", VisStories.story("HourDayPointMap",
                        VisStories.settings("pointSeries", "User", "eventTime", "Time", "value", "Bytes"),
                        VisTable.builder().text("User").date("Time").number("Bytes").build()))
                .withPlay(play -> {
                    final Query chart = VisStories.drawn(play, "svg.vis-point-map", 1).first();
                    play.expect(play.withinFrame(VisStories.frame(play)).querySelector(".vis-empty"))
                            .toHaveTextContent("No data");
                    play.waitFor(() -> play.expect(chart)
                            .toHaveAttribute("aria-label", "Point map of Time by hour and day: no data"));
                    VisStories.expectNoProblems(play);
                });
    }

    private static void addHourDaySessionMap(final StoryRegistry registry) {
        registry.component(VisStories.TITLE + "HourDaySessionMap", TimeVisStories.class)
                .layout(StoryLayout.FULLSCREEN)
                .tags(VisStories.TAG)
                // What Stroom is told of a selected session (the Selection column of the design): Ctrl+Click
                // tells it of the session, and again of nothing
                .story("SelectionPayload", VisStories.story("HourDaySessionMap", EVENTS_BY_USER, events()))
                .withPlay(play -> {
                    VisStories.drawn(play, SESSION_MAP_BAR);
                    final Query point = play.withinFrame(VisStories.frame(play))
                            .querySelectorAll(SESSION_MAP_BAR).first();
                    VisStories.expectSelectionOf(play, point,
                            "[{\"key\":\"alice~#~2026-09-07T08:00:00.000Z\",\"series\":\"alice\",\""
                            + "x\":\"2026-09-07T08:00:00.000Z\",\"end\":\"2026-09-07T08:12:00.000Z\"}"
                            + "]");
                    VisStories.expectNoProblems(play);
                })
                // Each user's sessions of events less than ten minutes apart, by time of day on the row
                // of their day, each user in a lane of their own
                .story("ByUser", VisStories.story("HourDaySessionMap", EVENTS_BY_USER, events()))
                .withPlay(play -> {
                    final Query bars = VisStories.drawn(play, SESSION_MAP_BAR, 27);
                    play.expect(bars.first()).toHaveAttribute("aria-label",
                            "2026-09-07T08:00:00.000Z to 08:12:00, 12 min, 4 events");
                    final Play inFrame = play.withinFrame(VisStories.frame(play));
                    play.expect(inFrame.querySelector("svg.vis-session-map")).toHaveAttribute("aria-label",
                            "Sessions by User by hour and day: 27 sessions in 3 series over 3 days, first "
                            + "2026-09-07T08:00:00.000Z");
                    expectEdgedWhenForced(play, inFrame, bars);
                    VisStories.expectNoProblems(play);
                })
                // A session over midnight is a bar on each day, its events counted once, where the event
                // after midnight was counted twice
                .story("OverMidnight", VisStories.story("HourDaySessionMap", EVENTS_BY_USER,
                        VisTable.builder().text("User").date("Time")
                                .row("alice", START - 5 * MINUTE)
                                .row("alice", START + 3 * MINUTE)
                                .build()))
                .withPlay(play -> {
                    final Query bars = VisStories.drawn(play, SESSION_MAP_BAR, 2);
                    final String label = "2026-09-06T23:55:00.000Z to 2026-09-07T00:03:00.000Z, 8 min, 2 events";
                    play.expect(bars.first()).toHaveAttribute("aria-label", label);
                    play.expect(bars.nth(1)).toHaveAttribute("aria-label", label);
                    VisStories.expectNoProblems(play);
                })
                // A map for each user, over the same days
                .story("GridSeries", VisStories.story("HourDaySessionMap",
                        VisStories.settings("gridSeries", "User", "eventTime", "Time"), events()))
                .withPlay(play -> {
                    VisStories.drawn(play, "svg.vis-session-map", 3);
                    play.expect(play.withinFrame(VisStories.frame(play)).querySelectorAll(SESSION_MAP_BAR))
                            .toHaveLength(27);
                    VisStories.expectNoProblems(play);
                })
                // A search that found nothing: 'No data' where the bars would be
                .story("NoData", VisStories.story("HourDaySessionMap", EVENTS_BY_USER,
                        VisTable.builder().text("User").date("Time").build()))
                .withPlay(play -> expectNoData(play, "svg.vis-session-map",
                        "Sessions by User by hour and day: no data"));
    }

    private static void addSession(final StoryRegistry registry) {
        registry.component(VisStories.TITLE + "Session", TimeVisStories.class)
                .layout(StoryLayout.FULLSCREEN)
                .tags(VisStories.TAG)
                // What Stroom is told of a selected session (the Selection column of the design): Ctrl+Click
                // tells it of the session, and again of nothing
                .story("SelectionPayload", VisStories.story("Session", SESSIONS_BY_USER, sessions()))
                .withPlay(play -> {
                    VisStories.drawn(play, TIMELINE_BAR);
                    final Query point = play.withinFrame(VisStories.frame(play))
                            .querySelectorAll(TIMELINE_BAR).first();
                    VisStories.expectSelectionOf(play, point,
                            "[{\"key\":\"alice~#~2026-09-07T08:00:00.000Z\",\"series\":\"alice\",\""
                            + "x\":\"2026-09-07T08:00:00.000Z\",\"end\":\"2026-09-07T09:00:00.000Z\"}"
                            + "]");
                    VisStories.expectNoProblems(play);
                })
                // Each user's sessions from their start to their end, each user on a row of their own
                .story("ByUser", VisStories.story("Session", SESSIONS_BY_USER, sessions()))
                .withPlay(play -> {
                    final Query bars = VisStories.drawn(play, TIMELINE_BAR, 9);
                    play.expect(bars.first())
                            .toHaveAttribute("aria-label", "2026-09-07T08:00:00.000Z to 09:00:00, 1 h");
                    final Play inFrame = play.withinFrame(VisStories.frame(play));
                    play.expect(inFrame.querySelector("svg.vis-timeline")).toHaveAttribute("aria-label",
                            "Sessions by User: 9 sessions in 3 series, from 2026-09-07T08:00:00.000Z to "
                            + "2026-09-07T17:00:00.000Z");
                    expectEdgedWhenForced(play, inFrame, bars);
                    VisStories.expectNoProblems(play);
                })
                // The Series Filter setting keeps only the users with one session, where it was ignored
                .story("ContiguousOnly", VisStories.story("Session", VisStories.settings("series", "User",
                                "startTime", "Start", "endTime", "End", "seriesFilter", "Contiguous Only"),
                        VisTable.builder().text("User").date("Start").date("End")
                                .row("alice", START, START + HOUR)
                                .row("bob", START, START + HOUR)
                                .row("bob", START + 2 * HOUR, START + 3 * HOUR)
                                .build()))
                .withPlay(play -> {
                    final Query bars = VisStories.drawn(play, TIMELINE_BAR, 1);
                    play.expect(play.withinFrame(VisStories.frame(play)).querySelector("svg.vis-timeline"))
                            .toHaveAttribute("aria-label", "Sessions by User: 1 session, from "
                                                           + "2026-09-07T00:00:00.000Z to 2026-09-07T01:00:00.000Z");
                    play.expect(bars.first())
                            .toHaveAttribute("aria-label", "2026-09-07T00:00:00.000Z to 01:00:00, 1 h");
                    VisStories.expectNoProblems(play);
                })
                // A session without an end is a moment, still a bar that can be seen
                .story("Moment", VisStories.story("Session", SESSIONS_BY_USER,
                        VisTable.builder().text("User").date("Start").date("End")
                                .row("alice", START, null)
                                .row("alice", START + HOUR, START + 2 * HOUR)
                                .build()))
                .withPlay(play -> {
                    final Query bars = VisStories.drawn(play, TIMELINE_BAR, 2);
                    play.expect(bars.first())
                            .toHaveAttribute("aria-label", "2026-09-07T00:00:00.000Z to 00:00:00, 0 s");
                    play.expect("the moment's width", () -> Double.parseDouble(bars.first().attribute("width").get()))
                            .toBeGreaterThanOrEqual(3);
                    VisStories.expectNoProblems(play);
                })
                // A session is a tab stop, with its start, end and duration in its tooltip
                .story("Focus", VisStories.story("Session", SESSIONS_BY_USER, sessions()))
                .withPlay(play -> {
                    VisStories.drawn(play, TIMELINE_BAR, 9);
                    final Query tooltip = VisStories.tooltip(play);
                    VisStories.focusTabStop(play);
                    play.waitFor(() -> play.expect(tooltip).toBeVisible());
                    play.expect(tooltip).toHaveTextContent(TextMatch.containing("Duration"));
                    play.expect(VisStories.focusedPoint(play).computedStyle("outline-style")).toBe("solid");
                    VisStories.expectNoProblems(play);
                })
                // A timeline for each user, over the same times
                .story("GridSeries", VisStories.story("Session",
                        VisStories.settings("gridSeries", "User", "startTime", "Start", "endTime", "End"), sessions()))
                .withPlay(play -> {
                    VisStories.drawn(play, "svg.vis-timeline", 3);
                    play.expect(play.withinFrame(VisStories.frame(play)).querySelectorAll(TIMELINE_BAR))
                            .toHaveLength(9);
                    VisStories.expectNoProblems(play);
                })
                // A search that found nothing: 'No data' where the bars would be
                .story("NoData", VisStories.story("Session", SESSIONS_BY_USER,
                        VisTable.builder().text("User").date("Start").date("End").build()))
                .withPlay(play -> expectNoData(play, "svg.vis-timeline", "Sessions by User: no data"));
    }

    private static void addSeriesSessionMap(final StoryRegistry registry) {
        registry.component(VisStories.TITLE + "SeriesSessionMap", TimeVisStories.class)
                .layout(StoryLayout.FULLSCREEN)
                .tags(VisStories.TAG)
                // What Stroom is told of a selected session (the Selection column of the design): Ctrl+Click
                // tells it of the session, and again of nothing
                .story("SelectionPayload", VisStories.story("SeriesSessionMap", EVENTS_BY_USER, events()))
                .withPlay(play -> {
                    VisStories.drawn(play, TIMELINE_BAR);
                    final Query point = play.withinFrame(VisStories.frame(play))
                            .querySelectorAll(TIMELINE_BAR).first();
                    VisStories.expectSelectionOf(play, point,
                            "[{\"key\":\"alice~#~2026-09-07T08:00:00.000Z\",\"series\":\"alice\",\""
                            + "x\":\"2026-09-07T08:00:00.000Z\",\"end\":\"2026-09-07T08:22:00.000Z\"}"
                            + "]");
                    VisStories.expectNoProblems(play);
                })
                // Each user's sessions of events less than ten minutes apart, each staying open ten
                // minutes after its last event
                .story("ByUser", VisStories.story("SeriesSessionMap", EVENTS_BY_USER, events()))
                .withPlay(play -> {
                    final Query bars = VisStories.drawn(play, TIMELINE_BAR, 27);
                    play.expect(bars.first()).toHaveAttribute("aria-label",
                            "2026-09-07T08:00:00.000Z to 08:22:00, 22 min, 4 events");
                    expectEdgedWhenForced(play, play.withinFrame(VisStories.frame(play)), bars);
                    VisStories.expectNoProblems(play);
                })
                // Sessions opened and closed by Start and Stop events, their states in the tooltip
                .story("StateChanges", VisStories.story("SeriesSessionMap",
                        VisStories.settings("series", "User", "eventTime", "Time", "stateChange", "Action"),
                        startsAndStops()))
                .withPlay(play -> {
                    final Query bars = VisStories.drawn(play, TIMELINE_BAR, 9);
                    play.expect(bars.first()).toHaveAttribute("aria-label",
                            "2026-09-07T08:00:00.000Z to 09:00:00, 1 h, 2 events");
                    final Query tooltip = VisStories.tooltip(play);
                    VisStories.focusTabStop(play);
                    play.waitFor(() -> play.expect(tooltip).toBeVisible());
                    play.expect(tooltip).toHaveTextContent(TextMatch.containing("Start, Stop"));
                    VisStories.expectNoProblems(play);
                })
                // A timeline for each user
                .story("GridSeries", VisStories.story("SeriesSessionMap",
                        VisStories.settings("gridSeries", "User", "eventTime", "Time"), events()))
                .withPlay(play -> {
                    VisStories.drawn(play, "svg.vis-timeline", 3);
                    play.expect(play.withinFrame(VisStories.frame(play)).querySelectorAll(TIMELINE_BAR))
                            .toHaveLength(27);
                    VisStories.expectNoProblems(play);
                })
                // A search that found nothing: 'No data' where the bars would be
                .story("NoData", VisStories.story("SeriesSessionMap", EVENTS_BY_USER,
                        VisTable.builder().text("User").date("Time").build()))
                .withPlay(play -> expectNoData(play, "svg.vis-timeline", "Sessions by User: no data"));
    }

    // With a high contrast theme, each bar keeps its series' colour but is edged in the theme's text
    // colour, so that it shows on the theme's background
    private static void expectEdgedWhenForced(final Play play, final Play inFrame, final Query bars) {
        if (StroomThemeDecorator.isForcedColours()) {
            final Query vis = inFrame.querySelector(".vis");
            play.expect("the text's colour and a bar's edge",
                            () -> List.of(vis.computedStyle("color").get(), bars.first().computedStyle("stroke").get()))
                    .toSatisfy("the same", colours -> new HashSet<>((List<?>) colours).size() == 1);
        }
    }

    // 'No data' where the chart would draw, which says so to a screen reader too
    private static void expectNoData(final Play play, final String chartSelector, final String summary) {
        final Query chart = VisStories.drawn(play, chartSelector, 1).first();
        play.expect(play.withinFrame(VisStories.frame(play)).querySelector(".vis-empty")).toHaveTextContent("No data");
        play.waitFor(() -> play.expect(chart).toHaveAttribute("aria-label", summary));
        VisStories.expectNoProblems(play);
    }

    // The first of the highest cells of the heat map in a cell of the grid, counting from 1
    private static Query highestIn(final Play inFrame, final int gridCell) {
        return inFrame.querySelector(".vis-grid__cell:nth-child(" + gridCell + ") .vis-heat__cell--highest");
    }

    private static VisTable dailyLogins() {
        final VisTable.Builder builder = VisTable.builder().date("Day").number("Logins");
        for (int day = 0; day < 42; day++) {
            final boolean weekend = day % 7 >= 5;
            builder.row(START + day * DAY, (weekend
                    ? 15
                    : 120) + (day * 29) % 40);
        }
        return builder.build();
    }

    private static VisTable hourlyLoginsByUser() {
        final VisTable.Builder builder = VisTable.builder().text("User").date("Hour").number("Logins");
        for (int user = 0; user < USERS.length; user++) {
            for (int hour = 0; hour < 7 * 24; hour++) {
                final int hourOfDay = hour % 24;
                // Each user starts work an hour later than the one before
                final boolean working = hourOfDay >= 8 + user && hourOfDay < 17 + user && hour / 24 < 5;
                if (working || (hour * 7 + user) % 11 == 0) {
                    builder.row(USERS[user], START + hour * HOUR, (working
                            ? 10
                            : 1) + (hour * 13 + user * 5) % 9);
                }
            }
        }
        return builder.build();
    }

    // Each user's events over three days: bursts of events a few minutes apart
    private static VisTable events() {
        final VisTable.Builder builder = VisTable.builder().text("User").date("Time").number("Bytes");
        for (int user = 0; user < USERS.length; user++) {
            for (int day = 0; day < 3; day++) {
                for (int burst = 0; burst < 3; burst++) {
                    final long burstStart = START + day * DAY + (8 + user + burst * 3) * HOUR;
                    for (int event = 0; event < 4; event++) {
                        builder.row(USERS[user], burstStart + event * 4 * 60_000L,
                                500 + (day * 97 + burst * 31 + event * 13 + user * 7) % 400);
                    }
                }
            }
        }
        return builder.build();
    }

    // Each user's sessions over a day
    private static VisTable sessions() {
        final VisTable.Builder builder = VisTable.builder().text("User").date("Start").date("End");
        for (int user = 0; user < USERS.length; user++) {
            for (int session = 0; session < 3; session++) {
                final long start = START + (8 + user + session * 3) * HOUR;
                builder.row(USERS[user], start, start + (1 + (session + user) % 2) * HOUR);
            }
        }
        return builder.build();
    }

    // Each user's Start and Stop events over a day
    private static VisTable startsAndStops() {
        final VisTable.Builder builder = VisTable.builder().text("User").date("Time").text("Action");
        for (int user = 0; user < USERS.length; user++) {
            for (int session = 0; session < 3; session++) {
                final long start = START + (8 + user + session * 3) * HOUR;
                builder.row(USERS[user], start, "Start");
                builder.row(USERS[user], start + (1 + (session + user) % 2) * HOUR, "Stop");
            }
        }
        return builder.build();
    }
}
