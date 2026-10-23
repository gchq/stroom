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


/// The stories of the content pack's visualisations of a single value: `Gauge`, `RAGStatus`,
/// `TrafficLights` and `TextValue`. See [VisStories].
///
/// Each shows a value for each of three servers, one in each band of the controls' default
/// thresholds: green (0 to 1999), amber (2000 to 4999) and red (5000 to 10000).
public final class StatusVisStories {

    // A queue length for each server: one green, one amber and one red
    private static final VisTable QUEUE_LENGTHS = VisTable.builder()
            .text("Server")
            .number("Queue")
            .row("server-1", 1200)
            .row("server-2", 3500)
            .row("server-3", 8000)
            .build();

    // A chart of each server's queue length
    private static final Map<String, String> QUEUE_BY_SERVER = VisStories.settings("field", "Queue",
            "gridSeries", "Server");

    private static final VisTable MESSAGES = VisTable.builder()
            .text("Server")
            .text("Status")
            .row("server-1", "Running")
            .row("server-2", "Draining")
            .row("server-3", "Stopped")
            .build();

    // The asset-based gauge's dials
    private static final String DIAL = "g.vis-gauge__dial";
    // The asset-based RAG status's tiles, traffic lights' lights and text value's values
    private static final String TILE = "div.vis-rag__tile";
    private static final String LIGHT = "g.vis-traffic__light";
    private static final String VALUE = "div.vis-text-value__value";

    // Too long for one line at the smallest size the text value writes
    private static final String LONG_MESSAGE = "The nightly export to the archive failed after four retries because "
                                               + "the destination volume was full, so nothing was copied and the "
                                               + "job will run again tomorrow unless someone frees some room; these "
                                               + "are the last words";

    private StatusVisStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        registry.component(VisStories.TITLE + "Gauge", StatusVisStories.class)
                .layout(StoryLayout.FULLSCREEN)
                .tags(VisStories.TAG)
                // A gauge for each server, each pointing into a different band, with its value and
                // status under the pointer, so the status isn't told by colour alone
                .story("Thresholds", VisStories.story("Gauge", QUEUE_BY_SERVER, QUEUE_LENGTHS))
                .withPlay(play -> {
                    final Query dials = VisStories.drawn(play, DIAL, 3);
                    final Play inFrame = play.withinFrame(VisStories.frame(play));
                    play.expect(dials.first()).toHaveAttribute("aria-label",
                            "Gauge of Queue, server-1: 1,200, Green (0 to 1,999)");
                    play.expect(dials.nth(1)).toHaveAttribute("aria-label",
                            "Gauge of Queue, server-2: 3,500, Amber (2,000 to 4,999)");
                    final Query statuses = inFrame.querySelectorAll(".vis-gauge__status");
                    play.expect("the gauges' statuses", () -> statuses.textContents().get())
                            .toEqual(List.of("Green", "Amber", "Red"));
                    // The grid is one tab stop (the arrow keys go from gauge to gauge), named for all
                    // its gauges
                    play.expect(inFrame.querySelectorAll("[tabindex='0']")).toHaveLength(1);
                    play.waitFor(() -> play.expect(inFrame.querySelector(".vis")).toHaveAttribute("aria-label",
                            "Gauge of Queue, 3 cells: 1 Green, 1 Amber, 1 Red"));
                    VisStories.focusTabStop(play);
                    VisStories.press(play, "{ArrowRight}");
                    play.waitFor(() -> play.expect(dials.nth(1)).toHaveFocus());
                    // The band the value is in is wider than the others
                    play.expect(inFrame.querySelectorAll(".vis-gauge__band--active")).toHaveLength(3);
                    // The pointer is in the theme's text colour, not the dark red it was on any theme
                    final Query vis = inFrame.querySelector(".vis");
                    play.expect("the text's colour and the pointer's",
                                    () -> List.of(vis.computedStyle("color").get(),
                                            inFrame.querySelectorAll(".vis-gauge__pointer").first()
                                                    .computedStyle("fill").get()))
                            .toSatisfy("the same", colours -> new HashSet<>((List<?>) colours).size() == 1);
                    if (StroomThemeDecorator.isForcedColours()) {
                        // With a high contrast theme, each band keeps its colour, edged in the theme's
                        // text colour
                        final Query band = inFrame.querySelectorAll(".vis-gauge__band").first();
                        play.expect("the text's colour and a band's edge's",
                                        () -> List.of(vis.computedStyle("color").get(),
                                                band.computedStyle("stroke").get()))
                                .toSatisfy("the same", colours -> new HashSet<>((List<?>) colours).size() == 1);
                        play.expect("the background's colour and a band's",
                                        () -> List.of(vis.computedStyle("background-color").get(),
                                                band.computedStyle("fill").get()))
                                .toSatisfy("different", colours -> new HashSet<>((List<?>) colours).size() == 2);
                    }
                    VisStories.expectNoProblems(play);
                })
                // A value at the top of a band is in it: 1,999 is green, not an outlier as it was
                // With Display Green cells off and only a green value, the gauge says the legend hides
                // it (rather than that there is no data)
                .story("AllHidden", VisStories.story("Gauge", VisStories.settings("field", "Queue",
                        "displayGreens", "false"), VisTable.builder().number("Queue").row(1200).build()))
                .withPlay(play -> {
                    final Play inFrame = play.withinFrame(VisStories.frame(play));
                    play.waitFor(() -> play.expect(inFrame.querySelector(".vis-empty"))
                            .toHaveTextContent("All hidden by the legend"));
                    VisStories.expectNoProblems(play);
                })
                // With Show Value off, the value isn't shown but the status still is, in words, so that
                // it isn't told by the band's colour alone
                .story("NoValue", VisStories.story("Gauge", VisStories.settings("field", "Queue",
                        "showLabels", "False"), VisTable.builder().number("Queue").row(3500).build()))
                .withPlay(play -> {
                    VisStories.drawn(play, DIAL, 1);
                    final Play inFrame = play.withinFrame(VisStories.frame(play));
                    play.expect(inFrame.querySelector("text.vis-gauge__value")).toHaveTextContent("");
                    play.expect(inFrame.querySelector("text.vis-gauge__status")).toHaveTextContent("Amber");
                    VisStories.expectNoProblems(play);
                })
                .story("TopOfBand", VisStories.story("Gauge", VisStories.settings("field", "Queue"),
                        VisTable.builder().number("Queue").row(1999).build()))
                .withPlay(play -> {
                    final Query dial = VisStories.drawn(play, DIAL, 1).first();
                    play.expect(dial).toHaveAttribute("aria-label", "Gauge of Queue: 1,999, Green (0 to 1,999)");
                    VisStories.expectNoProblems(play);
                })
                // Green at the top (more is better), the bands compared as numbers (900 is less than
                // 1,000, though not as text)
                .story("GreenHigh", VisStories.story("Gauge", VisStories.settings("field", "Throughput",
                                "RedLo", "0", "RedHi", "900", "AmberLo", "901", "AmberHi", "999",
                                "GreenLo", "1000", "GreenHi", "5000"),
                        VisTable.builder().number("Throughput").row(950).build()))
                .withPlay(play -> {
                    final Query dial = VisStories.drawn(play, DIAL, 1).first();
                    play.expect(dial).toHaveAttribute("aria-label", "Gauge of Throughput: 950, Amber (901 to 999)");
                    VisStories.expectNoProblems(play);
                })
                // A value off the dial: the pointer just past its end, and the value marked by more
                // than its colour
                .story("OffTheDial", VisStories.story("Gauge", VisStories.settings("field", "Queue"),
                        VisTable.builder().number("Queue").row(12000).build()))
                .withPlay(play -> {
                    final Query dial = VisStories.drawn(play, DIAL, 1).first();
                    play.expect(dial).toHaveAttribute("aria-label", "Gauge of Queue: 12,000, Above the dial");
                    final Query value = play.withinFrame(VisStories.frame(play)).querySelector(".vis-gauge__value");
                    play.expect(value).toHaveClass("vis-gauge__value--outside");
                    play.expect(value.computedStyle("text-decoration-line")).toBe("underline");
                    VisStories.expectNoProblems(play);
                })
                // The legend shows or hides the gauges of each status, e.g. to see only the ones that
                // aren't green
                .story("HideGreens", VisStories.story("Gauge", QUEUE_BY_SERVER, QUEUE_LENGTHS))
                .withPlay(play -> {
                    final Query dials = VisStories.drawn(play, DIAL, 3);
                    play.click(play.screen().getByRole("button", "Show legend"));
                    final Query green = play.withinFrame(VisStories.frame(play))
                            .getByRole("button", "Green (0 to 1,999)");
                    play.waitFor(() -> play.expect(green).toBeVisible());
                    play.click(green);
                    play.waitFor(() -> play.expect(dials).toHaveLength(2));
                    play.expect(dials.first()).toHaveAttribute("aria-label",
                            "Gauge of Queue, server-2: 3,500, Amber (2,000 to 4,999)");
                    VisStories.expectNoProblems(play);
                })
                // The gauge is a tab stop, named and with its tooltip on Stroom's page
                .story("Focus", VisStories.story("Gauge", VisStories.settings("field", "Queue"),
                        VisTable.builder().number("Queue").row(3500).build()))
                .withPlay(play -> {
                    VisStories.drawn(play, DIAL, 1);
                    final Query tooltip = VisStories.tooltip(play);
                    VisStories.focusTabStop(play);
                    play.waitFor(() -> play.expect(tooltip).toBeVisible());
                    play.expect(tooltip).toHaveTextContent(TextMatch.containing("2,000 to 4,999"));
                    play.expect(VisStories.focusedPoint(play).computedStyle("outline-style")).toBe("solid");
                    VisStories.expectNoProblems(play);
                })
                // Bands without numbers: nothing to point into
                .story("NoBands", VisStories.story("Gauge", VisStories.settings("field", "Queue",
                                "GreenLo", "", "GreenHi", "", "AmberLo", "", "AmberHi", "", "RedLo", "", "RedHi", ""),
                        VisTable.builder().number("Queue").row(10).build()))
                .withPlay(play -> {
                    VisStories.drawn(play, "svg.vis-gauge", 1);
                    // The visualisation (its one tab stop, see gridPoints.js) is named for what it shows
                    final Query chart = play.withinFrame(VisStories.frame(play)).querySelector(".vis");
                    play.expect(play.withinFrame(VisStories.frame(play)).querySelector(".vis-empty"))
                            .toHaveTextContent("No bands set");
                    play.waitFor(() -> play.expect(chart)
                            .toHaveAttribute("aria-label", "Gauge of Queue: no bands set"));
                    VisStories.expectNoProblems(play);
                })
                // A search that found nothing: 'No data' where the dial would be
                .story("NoData", VisStories.story("Gauge", VisStories.settings("field", "Queue"),
                        VisTable.builder().number("Queue").build()))
                .withPlay(play -> {
                    VisStories.drawn(play, "svg.vis-gauge", 1);
                    // The visualisation (its one tab stop, see gridPoints.js) is named for what it shows
                    final Query chart = play.withinFrame(VisStories.frame(play)).querySelector(".vis");
                    play.expect(play.withinFrame(VisStories.frame(play)).querySelector(".vis-empty"))
                            .toHaveTextContent("No data");
                    play.waitFor(() -> play.expect(chart).toHaveAttribute("aria-label", "Gauge of Queue: no data"));
                    VisStories.expectNoProblems(play);
                });

        addRagStatus(registry);
        addTrafficLights(registry);
        addTextValue(registry);
    }

    private static void addRagStatus(final StoryRegistry registry) {
        registry.component(VisStories.TITLE + "RAGStatus", StatusVisStories.class)
                .layout(StoryLayout.FULLSCREEN)
                .tags(VisStories.TAG)
                // A tile for each server in its status's colour, with its value and status written
                // on it, so the status isn't told by colour alone
                .story("Thresholds", VisStories.story("RAGStatus", QUEUE_BY_SERVER, QUEUE_LENGTHS))
                .withPlay(play -> {
                    final Query tiles = VisStories.drawn(play, TILE, 3);
                    play.expect(tiles.first()).toHaveAttribute("aria-label",
                            "RAG status of Queue, server-1: 1,200, Green (0 to 1,999)");
                    play.expect(tiles.nth(2)).toHaveAttribute("aria-label",
                            "RAG status of Queue, server-3: 8,000, Red (5,000 to 10,000)");
                    final Play inFrame = play.withinFrame(VisStories.frame(play));
                    final Query statuses = inFrame.querySelectorAll(".vis-rag__status");
                    play.expect("the tiles' statuses", () -> statuses.textContents().get())
                            .toEqual(List.of("Green", "Amber", "Red"));
                    final Query values = inFrame.querySelectorAll(".vis-rag__value");
                    play.expect("the tiles' values", () -> values.textContents().get())
                            .toEqual(List.of("1,200", "3,500", "8,000"));
                    if (StroomThemeDecorator.isForcedColours()) {
                        // With a high contrast theme, each tile keeps its status's colour, which the
                        // browser would otherwise make the background's, edged in the theme's text
                        // colour
                        final Query vis = inFrame.querySelector(".vis");
                        play.expect("the background's colour and the tiles'",
                                        () -> List.of(vis.computedStyle("background-color").get(),
                                                tiles.first().computedStyle("background-color").get(),
                                                tiles.nth(1).computedStyle("background-color").get(),
                                                tiles.nth(2).computedStyle("background-color").get()))
                                .toSatisfy("all different", colours -> new HashSet<>((List<?>) colours).size() == 4);
                        play.expect("the text's colour and a tile's edge's",
                                        () -> List.of(vis.computedStyle("color").get(),
                                                tiles.first().computedStyle("border-top-color").get()))
                                .toSatisfy("the same", colours -> new HashSet<>((List<?>) colours).size() == 1);
                    }
                    VisStories.expectNoProblems(play);
                })
                // A value at the top of a band is in it: 1,999 is green, where it was left out
                .story("TopOfBand", VisStories.story("RAGStatus", VisStories.settings("field", "Queue"),
                        VisTable.builder().number("Queue").row(1999).build()))
                .withPlay(play -> {
                    final Query tile = VisStories.drawn(play, TILE, 1).first();
                    play.expect(tile).toHaveAttribute("aria-label", "RAG status of Queue: 1,999, Green (0 to 1,999)");
                    VisStories.expectNoProblems(play);
                })
                // A value outside the bands is a tile that says where it is, where it was left out
                .story("OutsideBands", VisStories.story("RAGStatus", VisStories.settings("field", "Queue"),
                        VisTable.builder().number("Queue").row(12000).build()))
                .withPlay(play -> {
                    final Query tile = VisStories.drawn(play, TILE, 1).first();
                    play.expect(tile).toHaveAttribute("aria-label", "RAG status of Queue: 12,000, Above the bands");
                    final Play inFrame = play.withinFrame(VisStories.frame(play));
                    play.expect(inFrame.querySelector(".vis-rag__status")).toHaveTextContent("Above the bands");
                    play.expect(inFrame.querySelector(".vis-rag__value").computedStyle("text-decoration-line"))
                            .toBe("underline");
                    VisStories.expectNoProblems(play);
                })
                // The Display Green cells setting hides the green tiles, as the legend does, and the
                // legend says so
                .story("NoGreens", VisStories.story("RAGStatus", VisStories.settings("field", "Queue",
                        "gridSeries", "Server", "displayGreens", "False"), QUEUE_LENGTHS))
                .withPlay(play -> {
                    final Query tiles = VisStories.drawn(play, TILE, 2);
                    play.expect(tiles.first()).toHaveAttribute("aria-label",
                            "RAG status of Queue, server-2: 3,500, Amber (2,000 to 4,999)");
                    play.click(play.screen().getByRole("button", "Show legend"));
                    final Query green = play.withinFrame(VisStories.frame(play))
                            .getByRole("button", "Green (0 to 1,999)");
                    play.waitFor(() -> play.expect(green).toBeVisible());
                    play.expect(green).toHaveAttribute("aria-pressed", "false");
                    VisStories.expectNoProblems(play);
                })
                // Without the value, the status is still written
                .story("NoValue", VisStories.story("RAGStatus", VisStories.settings("field", "Queue",
                        "showLabels", "False"), VisTable.builder().number("Queue").row(3500).build()))
                .withPlay(play -> {
                    VisStories.drawn(play, TILE, 1);
                    final Play inFrame = play.withinFrame(VisStories.frame(play));
                    play.expect(inFrame.querySelector(".vis-rag__value")).not().toBeVisible();
                    play.expect(inFrame.querySelector(".vis-rag__status")).toHaveTextContent("Amber");
                    VisStories.expectNoProblems(play);
                })
                // The tile is a tab stop, named and with its tooltip on Stroom's page
                .story("Focus", VisStories.story("RAGStatus", VisStories.settings("field", "Queue"),
                        VisTable.builder().number("Queue").row(3500).build()))
                .withPlay(play -> {
                    VisStories.drawn(play, TILE, 1);
                    final Query tooltip = VisStories.tooltip(play);
                    VisStories.focusTabStop(play);
                    play.waitFor(() -> play.expect(tooltip).toBeVisible());
                    play.expect(tooltip).toHaveTextContent(TextMatch.containing("2,000 to 4,999"));
                    play.expect(VisStories.focusedPoint(play).computedStyle("outline-style")).toBe("solid");
                    // Just outside the tile, against the background (inside, on the tile's own colour,
                    // it couldn't be seen)
                    play.expect(VisStories.focusedPoint(play).computedStyle("outline-offset")).toBe("1px");
                    VisStories.expectNoProblems(play);
                })
                // A search that found nothing: 'No data' where the tile would be
                .story("NoData", VisStories.story("RAGStatus", VisStories.settings("field", "Queue"),
                        VisTable.builder().number("Queue").build()))
                .withPlay(play -> {
                    VisStories.drawn(play, "div.vis-rag", 1);
                    // The visualisation (its one tab stop, see gridPoints.js) is named for what it shows
                    final Query chart = play.withinFrame(VisStories.frame(play)).querySelector(".vis");
                    play.expect(play.withinFrame(VisStories.frame(play)).querySelector(".vis-rag__empty"))
                            .toHaveTextContent("No data");
                    play.waitFor(() -> play.expect(chart)
                            .toHaveAttribute("aria-label", "RAG status of Queue: no data"));
                    VisStories.expectNoProblems(play);
                });
    }

    private static void addTrafficLights(final StoryRegistry registry) {
        registry.component(VisStories.TITLE + "TrafficLights", StatusVisStories.class)
                .layout(StoryLayout.FULLSCREEN)
                .tags(VisStories.TAG)
                // A light for each server with its status's lamp lit, red at the top and green at the
                // bottom, and its value and status written under it
                .story("Thresholds", VisStories.story("TrafficLights", QUEUE_BY_SERVER, QUEUE_LENGTHS))
                .withPlay(play -> {
                    final Query lights = VisStories.drawn(play, LIGHT, 3);
                    play.expect(lights.first()).toHaveAttribute("aria-label",
                            "Traffic light of Queue, server-1: 1,200, Green (0 to 1,999)");
                    final Play inFrame = play.withinFrame(VisStories.frame(play));
                    final Query lamps = inFrame.querySelectorAll(".vis-traffic__lamp");
                    play.expect(lamps).toHaveLength(9);
                    // Server 1's green lamp (its third), server 2's amber (its second) and server 3's
                    // red (its first)
                    play.expect(lamps.nth(2)).toHaveClass("vis-traffic__lamp--lit");
                    play.expect(lamps.nth(4)).toHaveClass("vis-traffic__lamp--lit");
                    play.expect(lamps.nth(6)).toHaveClass("vis-traffic__lamp--lit");
                    play.expect(inFrame.querySelectorAll(".vis-traffic__lamp--lit")).toHaveLength(3);
                    final Query statuses = inFrame.querySelectorAll(".vis-traffic__status");
                    play.expect("the lights' statuses", () -> statuses.textContents().get())
                            .toEqual(List.of("Green", "Amber", "Red"));
                    if (StroomThemeDecorator.isForcedColours()) {
                        // With a high contrast theme, the lit lamp is filled in the theme's text colour
                        // and the unlit ones are its background, so which is lit still shows, and the
                        // housing is edged in the text colour
                        final Query vis = inFrame.querySelector(".vis");
                        play.expect("the text's colour, the lit lamp's and the housing's edge's",
                                        () -> List.of(vis.computedStyle("color").get(),
                                                lamps.nth(2).computedStyle("fill").get(),
                                                inFrame.querySelectorAll(".vis-traffic__housing").first()
                                                        .computedStyle("stroke").get()))
                                .toSatisfy("the same", colours -> new HashSet<>((List<?>) colours).size() == 1);
                        play.expect("the background's colour and an unlit lamp's",
                                        () -> List.of(vis.computedStyle("background-color").get(),
                                                lamps.nth(0).computedStyle("fill").get()))
                                .toSatisfy("the same", colours -> new HashSet<>((List<?>) colours).size() == 1);
                    }
                    VisStories.expectNoProblems(play);
                })
                // A value at the top of a band is in it: 1,999 is green, where it lit no lamp
                .story("TopOfBand", VisStories.story("TrafficLights", VisStories.settings("field", "Queue"),
                        VisTable.builder().number("Queue").row(1999).build()))
                .withPlay(play -> {
                    final Query light = VisStories.drawn(play, LIGHT, 1).first();
                    play.expect(light).toHaveAttribute("aria-label",
                            "Traffic light of Queue: 1,999, Green (0 to 1,999)");
                    play.expect(play.withinFrame(VisStories.frame(play)).querySelectorAll(".vis-traffic__lamp--lit"))
                            .toHaveLength(1);
                    VisStories.expectNoProblems(play);
                })
                // A value outside the bands lights no lamp, and says where it is
                .story("OutsideBands", VisStories.story("TrafficLights", VisStories.settings("field", "Queue"),
                        VisTable.builder().number("Queue").row(-5).build()))
                .withPlay(play -> {
                    final Query light = VisStories.drawn(play, LIGHT, 1).first();
                    play.expect(light).toHaveAttribute("aria-label", "Traffic light of Queue: −5, Below the bands");
                    final Play inFrame = play.withinFrame(VisStories.frame(play));
                    play.expect(inFrame.querySelectorAll(".vis-traffic__lamp--lit")).toHaveLength(0);
                    play.expect(inFrame.querySelector(".vis-traffic__status")).toHaveTextContent("Below the bands");
                    VisStories.expectNoProblems(play);
                })
                // The light is a tab stop, named and with its tooltip on Stroom's page
                .story("Focus", VisStories.story("TrafficLights", VisStories.settings("field", "Queue"),
                        VisTable.builder().number("Queue").row(8000).build()))
                .withPlay(play -> {
                    VisStories.drawn(play, LIGHT, 1);
                    final Query tooltip = VisStories.tooltip(play);
                    VisStories.focusTabStop(play);
                    play.waitFor(() -> play.expect(tooltip).toBeVisible());
                    play.expect(tooltip).toHaveTextContent(TextMatch.containing("5,000 to 10,000"));
                    play.expect(VisStories.focusedPoint(play).computedStyle("outline-style")).toBe("solid");
                    VisStories.expectNoProblems(play);
                })
                // A search that found nothing: 'No data' where the light would be
                .story("NoData", VisStories.story("TrafficLights", VisStories.settings("field", "Queue"),
                        VisTable.builder().number("Queue").build()))
                .withPlay(play -> {
                    VisStories.drawn(play, "svg.vis-traffic", 1);
                    // The visualisation (its one tab stop, see gridPoints.js) is named for what it shows
                    final Query chart = play.withinFrame(VisStories.frame(play)).querySelector(".vis");
                    play.expect(play.withinFrame(VisStories.frame(play)).querySelector(".vis-empty"))
                            .toHaveTextContent("No data");
                    play.waitFor(() -> play.expect(chart)
                            .toHaveAttribute("aria-label", "Traffic light of Queue: no data"));
                    VisStories.expectNoProblems(play);
                });
    }

    private static void addTextValue(final StoryRegistry registry) {
        registry.component(VisStories.TITLE + "TextValue", StatusVisStories.class)
                .layout(StoryLayout.FULLSCREEN)
                .tags(VisStories.TAG)
                // A value for each server, in the theme's text colour, and no legend, as there is
                // nothing to tell apart
                .story("ByServer", VisStories.story("TextValue",
                        VisStories.settings("field", "Status", "gridSeries", "Server"), MESSAGES))
                .withPlay(play -> {
                    final Query values = VisStories.drawn(play, VALUE, 3);
                    play.expect(values.first()).toHaveAttribute("aria-label", "Status, server-1: Running");
                    play.expect("the values", () -> values.textContents().get())
                            .toEqual(List.of("Running", "Draining", "Stopped"));
                    play.expect(play.screen().queryByRole("button", "Show legend")).toBeNull();
                    VisStories.expectNoProblems(play);
                })
                // A number with thousands separators, where it was written as it came
                .story("Number", VisStories.story("TextValue", VisStories.settings("field", "Queue"),
                        VisTable.builder().number("Queue").row(1234567).build()))
                .withPlay(play -> {
                    final Query value = VisStories.drawn(play, VALUE, 1).first();
                    play.expect(value).toHaveTextContent("1,234,567");
                    VisStories.expectNoProblems(play);
                })
                // A date as Stroom's tables show it, where it was written in milliseconds
                .story("Date", VisStories.story("TextValue", VisStories.settings("field", "Time"),
                        VisTable.builder().date("Time").row("2026-10-12T09:00:00Z").build()))
                .withPlay(play -> {
                    final Query value = VisStories.drawn(play, VALUE, 1).first();
                    play.expect(value).toHaveTextContent("2026-10-12T09:00:00.000Z");
                    VisStories.expectNoProblems(play);
                })
                // Text too long for one line wraps, all of it in its name and tooltip
                .story("LongText", VisStories.story("TextValue", VisStories.settings("field", "Message"),
                        VisTable.builder().text("Message").row(LONG_MESSAGE).build()))
                .withPlay(play -> {
                    final Query value = VisStories.drawn(play, VALUE, 1).first();
                    play.expect(value).toHaveClass("vis-text-value__value--wrapped");
                    play.expect(value).toHaveAttribute("aria-label", "Message: " + LONG_MESSAGE);
                    final Query tooltip = VisStories.tooltip(play);
                    VisStories.focusTabStop(play);
                    play.waitFor(() -> play.expect(tooltip).toBeVisible());
                    play.expect(tooltip).toHaveTextContent(TextMatch.containing("the last words"));
                    VisStories.expectNoProblems(play);
                })
                // A search that found nothing: 'No data' where the value would be
                .story("NoData", VisStories.story("TextValue", VisStories.settings("field", "Status"),
                        VisTable.builder().text("Status").build()))
                .withPlay(play -> {
                    VisStories.drawn(play, "div.vis-text-value", 1);
                    // The visualisation (its one tab stop, see gridPoints.js) is named for what it shows
                    final Query chart = play.withinFrame(VisStories.frame(play)).querySelector(".vis");
                    play.expect(play.withinFrame(VisStories.frame(play)).querySelector(".vis-text-value__empty"))
                            .toHaveTextContent("No data");
                    play.waitFor(() -> play.expect(chart).toHaveAttribute("aria-label", "Status: no data"));
                    VisStories.expectNoProblems(play);
                });
    }
}
