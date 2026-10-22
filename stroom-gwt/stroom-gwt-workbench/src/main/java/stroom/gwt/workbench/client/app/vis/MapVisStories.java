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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;


/// The stories of the content pack's maps: `GeoMap` and `FloorMap`. See [VisStories].
///
/// They need no network: the map's tiles and the floor plan are the workbench's own
/// (`src/main/webapp/assets/visualisations/`).
public final class MapVisStories {

    // A tile server URL template that gives the workbench's own tile for every tile
    private static final String TILE_URL = "/assets/visualisations/map-tile.svg?{z}/{x}/{y}";
    private static final String FLOOR_PLAN = "/assets/visualisations/floor-plan.svg";
    private static final long START = VisTable.date("2026-09-07T09:00:00Z");
    private static final long MINUTE = 60_000L;

    // The asset-based GeoMap's markers, and the first van's first stop's
    private static final String GEO_MARKER = "g.vis-map__marker";
    private static final String VAN_1_FIRST =
            "van-1, Time: 2026-09-07T09:00:00.000Z, Latitude: 51.505, Longitude: -0.09";

    // The FloorMap's zones, its first floor (as its list of floors has it) and the first sighting
    private static final String FLOOR_ZONES = "/assets/visualisations/floor-zones.json";
    // A Stroom dictionary of zones, which the workbench serves at /api/dictionary/v1/<uuid> (to read,
    // not to save)
    private static final String EDITABLE_ZONES = "floor-zones-dictionary";
    private static final String ZONE = "path.vis-map__area";
    private static final String FIRST_FLOOR_KEY = "[\"Head Office\",\"Main Building\",\"First Floor\"]";
    private static final String BADGE_17_FIRST = "badge-17, Time: 2026-09-07T09:00:00.000Z, "
                                                 + "Floor: Head Office, Main Building, Ground Floor, X: 10, Y: 15";

    // Where each delivery van was, through the morning
    private static final VisTable VAN_POSITIONS = vanPositions();
    // Where each badge was seen on the office floor
    private static final VisTable BADGE_SIGHTINGS = badgeSightings();

    private MapVisStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        addGeoMap(registry);

        addFloorMap(registry);
    }

    private static void addFloorMap(final StoryRegistry registry) {
        registry.component(VisStories.TITLE + "FloorMap", MapVisStories.class)
                .layout(StoryLayout.FULLSCREEN)
                .tags(VisStories.TAG)
                // What Stroom is told of a selected marker (the Selection column of the design): Ctrl+Click
                // tells it of the marker, and again of nothing
                .story("SelectionPayload", VisStories.story("FloorMap", floorSettings("series", "Badge"),
                        BADGE_SIGHTINGS))
                .withPlay(play -> {
                    VisStories.drawn(play, GEO_MARKER);
                    final Query point = play.withinFrame(VisStories.frame(play))
                            .querySelectorAll(GEO_MARKER).first();
                    VisStories.expectSelectionOf(play, point,
                            "[{\"key\":\"badge-17~#~0\",\"x\":10,\"y\":15,\"campus\":\"Head Office"
                            + "\",\"building\":\"Main Building\",\"floor\":\"Ground Floor\",\"name\":"
                            + "\"badge-17\",\"series\":\"badge-17\",\"eventTime\":\"2026-09-07T09:00:"
                            + "00.000Z\"}]");
                    VisStories.expectNoProblems(play);
                })
                // A marker where each badge was seen on one office floor, over its plan and its zones
                // (Reception and the meeting room; the old store is deleted)
                .story("Badges", VisStories.story("FloorMap", floorSettings("series", "Badge"), BADGE_SIGHTINGS))
                .withPlay(play -> {
                    final Query markers = VisStories.drawn(play, GEO_MARKER, 8);
                    final Play inFrame = play.withinFrame(VisStories.frame(play));
                    play.waitFor(() -> play.expect(inFrame.querySelectorAll(ZONE)).toHaveLength(2));
                    play.waitFor(() -> play.expect(inFrame.querySelector("svg.vis-map__overlay")).toHaveAttribute(
                            "aria-label", "Floor plan of Badge, Head Office, Main Building, Ground Floor: 8 markers "
                                          + "in 2 series, coloured by Time from 2026-09-07T09:00:00.000Z to "
                                          + "2026-09-07T10:30:00.000Z, 2 zones"));
                    play.expect(inFrame.getByRole("img", "Reception")).toBeVisible();
                    play.expect(inFrame.getByRole("img", BADGE_17_FIRST)).toBeVisible();
                    play.expect(inFrame.querySelector("img.leaflet-image-layer")).toHaveAttribute("alt",
                            "Plan of Head Office, Main Building, Ground Floor");
                    // One floor, so no list of floors
                    play.expect(inFrame.querySelectorAll(".vis-floor-map__floors")).toHaveLength(0);
                    if (StroomThemeDecorator.isForcedColours()) {
                        // With a high contrast theme, each marker is edged in its text colour
                        final Query vis = inFrame.querySelector(".vis");
                        play.expect("the text's colour and a marker's edge",
                                        () -> List.of(vis.computedStyle("color").get(),
                                                markers.first().computedStyle("stroke").get()))
                                .toSatisfy("the same", colours -> new HashSet<>((List<?>) colours).size() == 1);
                    }
                    VisStories.expectNoProblems(play);
                })
                // Badges seen on two floors: a list chooses the floor shown, with its own markers
                .story("Floors", VisStories.story("FloorMap", floorSettings("series", "Badge"), twoFloors()))
                .withPlay(play -> {
                    VisStories.drawn(play, GEO_MARKER, 2);
                    final Play inFrame = play.withinFrame(VisStories.frame(play));
                    final Query floors = inFrame.getByRole("combobox", "Floor");
                    play.expect(floors).toBeVisible();
                    play.selectOptions(floors, FIRST_FLOOR_KEY);
                    play.waitFor(() -> play.expect(inFrame.querySelectorAll(GEO_MARKER)).toHaveLength(1));
                    final Query plan = inFrame.querySelector("svg.vis-map__overlay");
                    play.waitFor(() -> play.expect(plan.attribute("aria-label"))
                            .toMatch("Head Office, Main Building, First Floor: 1 marker"));
                    VisStories.expectNoProblems(play);
                })
                // With Enable Zone Editing, a floor's zones in a Stroom dictionary are edited with the
                // mouse, as the legacy FloorMap's were (Leaflet.draw), its tools in Stroom's toolbar:
                // draw, reshape and delete them, a click on one to rename it ('Name #tag'), and Save
                // zones, which saves the dictionary as Stroom requires (the workbench, as Stroom,
                // refuses a save without the X-CSRF header, which the legacy map didn't send)
                .story("ZoneEditing", VisStories.story("FloorMap", floorSettings("series", "Badge",
                        "isEditZoneModeEnabled", "True", "config", floorMapConfig(EDITABLE_ZONES)), BADGE_SIGHTINGS))
                .withPlay(play -> {
                    VisStories.drawn(play, GEO_MARKER, 8);
                    final Play inFrame = play.withinFrame(VisStories.frame(play));
                    final Query zones = inFrame.querySelectorAll("path.leaflet-interactive:not(.vis-map__area)");
                    play.waitFor(() -> play.expect(zones).toHaveLength(2));
                    play.expect(inFrame.querySelectorAll("path.vis-map__area")).toHaveLength(0);
                    // The tools are in Stroom's toolbar; Leaflet.draw's own is hidden
                    play.expect(inFrame.querySelector(".leaflet-draw-toolbar")).not().toBeVisible();
                    play.expect(play.screen().getByRole("button", "Reshape zones")).toBeVisible();
                    play.expect(play.screen().getByRole("button", "Delete zones")).toBeVisible();
                    // Save zones can't be pressed with nothing to save
                    final Query save = play.screen().getByRole("button", "Save zones");
                    play.expect(save).toHaveAttribute("aria-disabled", "true");
                    // Drawing asks, at the map's edge, to finish or cancel
                    play.click(play.screen().getByRole("button", "Draw a new zone"));
                    final Query cancel = inFrame.querySelector(".leaflet-draw-actions a[title='Cancel drawing']");
                    play.waitFor(() -> play.expect(cancel).toBeVisible());
                    play.click(cancel);
                    play.waitFor(() -> play.expect(cancel).not().toBeVisible());
                    final Query status = inFrame.querySelector(".vis-floor-map__save-status");
                    play.click(zones.first());
                    final Query name = inFrame.getByRole("textbox", "Name and #tags");
                    play.waitFor(() -> play.expect(name).toHaveValue("Reception #public"));
                    play.fireEvent().input(name, "Front desk #public #staff");
                    play.click(inFrame.getByRole("button", "Rename"));
                    play.waitFor(() -> play.expect(status).toHaveTextContent("Unsaved changes"));
                    play.waitFor(() -> play.expect(save).not().toHaveAttribute("aria-disabled"));
                    play.click(save);
                    play.waitFor(() -> play.expect(status).toHaveTextContent("Zones saved"));
                    play.waitFor(() -> play.expect(save).toHaveAttribute("aria-disabled", "true"));
                    play.expect(status.className()).not().toMatch("vis-floor-map__save-status--failed");
                    VisStories.expectNoProblems(play);
                })
                // The plan's tab stop is its first marker (the zones come after it has drawn, and
                // don't move it); Up reaches the zones, which are a group before each badge's
                // markers. A zone's tooltip has its tags if the setting says
                .story("Keyboard", VisStories.story("FloorMap",
                        floorSettings("series", "Badge", "isShowTagsEnabled", "True"), BADGE_SIGHTINGS))
                .withPlay(play -> {
                    VisStories.drawn(play, GEO_MARKER, 8);
                    final Play inFrame = play.withinFrame(VisStories.frame(play));
                    final Query reception = inFrame.getByRole("img", "Reception, Tags: #public");
                    play.waitFor(() -> play.expect(reception).toBeVisible());
                    final Query tabStop = inFrame.querySelector(".vis-map__overlay [tabindex='0']");
                    play.run("focus the plan's tab stop", () -> tabStop.element().get().focus());
                    final Query first = inFrame.getByRole("img", TextMatch.startingWith(BADGE_17_FIRST));
                    play.waitFor(() -> play.expect(first).toHaveFocus());
                    VisStories.press(play, "{ArrowUp}");
                    play.waitFor(() -> play.expect(reception).toHaveFocus());
                    VisStories.press(play, "{ArrowDown}");
                    play.waitFor(() -> play.expect(first).toHaveFocus());
                    VisStories.press(play, "{Enter}");
                    play.waitFor(() -> play.expect(first).toHaveClass("vis-map__marker--selected"));
                    VisStories.expectNoProblems(play);
                })
                // A config that isn't JSON says so over the map (the legacy map logged it to the
                // console and drew nothing)
                .story("BadConfig", VisStories.story("FloorMap", floorSettings("config", "{not json"),
                        BADGE_SIGHTINGS))
                .withPlay(play -> {
                    VisStories.drawn(play, "div.leaflet-container", 1);
                    final Query note = play.withinFrame(VisStories.frame(play)).querySelector(".vis-map__empty");
                    play.waitFor(() -> play.expect(note).toBeVisible());
                    play.expect(note).toHaveTextContent("The FloorMap Config JSON setting isn't valid JSON");
                    VisStories.expectNoProblems(play);
                })
                // Rows on a floor without a plan, or without an x and y, are left out and said to be
                // (the legacy map raised an alert for each floor)
                .story("AwkwardData", VisStories.story("FloorMap", floorSettings(),
                        VisTable.builder().text("Badge").text("Campus").text("Building").text("Floor")
                                .number("X").number("Y").date("Time")
                                .row("badge-17", "Head Office", "Main Building", "Ground Floor", 10, 15, START)
                                .row("badge-42", "Head Office", "Main Building", "Basement", 10, 15, START)
                                .row("badge-99", "Head Office", "Main Building", "Ground Floor", null, 15, START)
                                .build()))
                .withPlay(play -> {
                    VisStories.drawn(play, GEO_MARKER, 1);
                    play.waitFor(() -> play.expect(play.withinFrame(VisStories.frame(play))
                                    .querySelector("svg.vis-map__overlay").attribute("aria-label"))
                            .toMatch("2 rows without a place left out"));
                    VisStories.expectNoProblems(play);
                })
                // A floor plan for each badge
                .story("GridSeries", VisStories.story("FloorMap", floorSettings("gridSeries", "Badge"),
                        BADGE_SIGHTINGS))
                .withPlay(play -> {
                    VisStories.drawn(play, "div.leaflet-container", 2);
                    VisStories.drawn(play, GEO_MARKER, 8);
                    VisStories.expectNoProblems(play);
                })
                // A search that found nothing
                .story("NoData", VisStories.story("FloorMap", floorSettings(),
                        VisTable.builder().text("Badge").text("Campus").text("Building").text("Floor")
                                .number("X").number("Y").date("Time").build()))
                .withPlay(play -> {
                    VisStories.drawn(play, "div.leaflet-container", 1);
                    final Play inFrame = play.withinFrame(VisStories.frame(play));
                    play.waitFor(() -> play.expect(inFrame.querySelector("svg.vis-map__overlay"))
                            .toHaveAttribute("aria-label", "Floor plan of Badge: no data"));
                    play.expect(inFrame.querySelector(".vis-map__empty")).toHaveTextContent("No data");
                    VisStories.expectNoProblems(play);
                });
    }

    // The FloorMap settings of the badge sightings, and any more
    private static Map<String, String> floorSettings(final String... more) {
        final Map<String, String> settings = VisStories.settings(
                "name", "Badge", "group", "Campus", "building", "Building", "floor", "Floor",
                "xPos", "X", "yPos", "Y", "eventTime", "Time", "config", floorMapConfig());
        settings.putAll(VisStories.settings(more));
        return settings;
    }

    // Two badges, one seen on each of two floors
    private static VisTable twoFloors() {
        return VisTable.builder().text("Badge").text("Campus").text("Building").text("Floor")
                .number("X").number("Y").date("Time")
                .row("badge-17", "Head Office", "Main Building", "Ground Floor", 10, 15, START)
                .row("badge-17", "Head Office", "Main Building", "Ground Floor", 30, 15, START + MINUTE)
                .row("badge-42", "Head Office", "Main Building", "First Floor", 50, 40, START)
                .build();
    }

    private static void addGeoMap(final StoryRegistry registry) {
        registry.component(VisStories.TITLE + "GeoMap", MapVisStories.class)
                .layout(StoryLayout.FULLSCREEN)
                .tags(VisStories.TAG)
                // What Stroom is told of a selected marker (the Selection column of the design): Ctrl+Click
                // tells it of the marker, and again of nothing. A click on the map, not on a marker,
                // selects nothing
                .story("SelectionPayload", VisStories.story("GeoMap", geoSettings("series", "Van"), VAN_POSITIONS))
                .withPlay(play -> {
                    VisStories.drawn(play, GEO_MARKER);
                    final Play inFrame = play.withinFrame(VisStories.frame(play));
                    final Query point = inFrame.querySelectorAll(GEO_MARKER).first();
                    final String json = "[{\"key\":\"van-1~#~0\",\"lat\":51.505,\"lon\":-0.09,\"name\":\"van-1"
                                        + "\",\"series\":\"van-1\",\"eventTime\":\"2026-09-07T09:00:00.000Z\"}]";
                    VisStories.expectSelectionOf(play, point, json);
                    play.fireEvent().click(point);
                    VisStories.expectSelection(play, json);
                    play.fireEvent().click(inFrame.querySelector(".vis-map"));
                    VisStories.expectSelection(play, "[]");
                    VisStories.expectNoProblems(play);
                })
                // A marker for each position of each van, around central London, the map fitted to
                // them: each coloured by its time, earliest dark, and edged in its van's colour
                .story("Vans", VisStories.story("GeoMap", geoSettings("series", "Van"), VAN_POSITIONS))
                .withPlay(play -> {
                    final Query markers = VisStories.drawn(play, GEO_MARKER, 10);
                    final Play inFrame = play.withinFrame(VisStories.frame(play));
                    play.waitFor(() -> play.expect(inFrame.querySelector("svg.vis-map__overlay")).toHaveAttribute(
                            "aria-label", "Map of Van: 10 markers in 2 series, coloured by Time from "
                                          + "2026-09-07T09:00:00.000Z to 2026-09-07T10:20:00.000Z"));
                    final Query first = inFrame.getByRole("img", VAN_1_FIRST);
                    final Query last = inFrame.getByRole("img",
                            "van-1, Time: 2026-09-07T10:20:00.000Z, Latitude: 51.513, Longitude: -0.078");
                    play.expect("the earliest and latest markers' colours",
                                    () -> List.of(first.computedStyle("fill").get(), last.computedStyle("fill").get()))
                            .toSatisfy("different", colours -> new HashSet<>((List<?>) colours).size() == 2);
                    play.expect(inFrame.querySelector(".vis-map__time-key")).toBeVisible();
                    // A marker can be pointed at 24 pixels across, though its dot is smaller (WCAG 2.5.8)
                    play.expect("a marker's width", () -> first.width().get()).toBeGreaterThanOrEqual(24);
                    play.expect("a marker's height", () -> first.height().get()).toBeGreaterThanOrEqual(24);
                    // The map itself (the first tab stop) says what it shows, and that it pans and zooms
                    // from the keyboard
                    play.expect(inFrame.querySelector(".leaflet-container")).toHaveAttribute("aria-label",
                            "Map of Van: 10 markers in 2 series, coloured by Time from 2026-09-07T09:00:00.000Z to "
                            + "2026-09-07T10:20:00.000Z. Arrow keys pan the map, plus and minus zoom it");
                    play.expect(inFrame.querySelector(".vis-map__empty")).not().toBeVisible();
                    if (StroomThemeDecorator.isForcedColours()) {
                        // With a high contrast theme, each marker is edged in its text colour
                        final Query vis = inFrame.querySelector(".vis");
                        play.expect("the text's colour and a marker's edge",
                                        () -> List.of(vis.computedStyle("color").get(),
                                                markers.first().computedStyle("stroke").get()))
                                .toSatisfy("the same", colours -> new HashSet<>((List<?>) colours).size() == 1);
                    } else {
                        // Otherwise each van's markers are edged in its colour
                        final Query van2 = inFrame.getByRole("img",
                                "van-2, Time: 2026-09-07T09:00:00.000Z, Latitude: 51.505, Longitude: -0.09");
                        play.expect("the vans' edges",
                                        () -> List.of(first.computedStyle("stroke").get(),
                                                van2.computedStyle("stroke").get()))
                                .toSatisfy("different", colours -> new HashSet<>((List<?>) colours).size() == 2);
                    }
                    VisStories.expectNoProblems(play);
                })
                // Not coloured by time, each marker is in its van's colour, with no time key
                .story("BySeries", VisStories.story("GeoMap",
                        geoSettings("series", "Van", "isColourByEventTimeEnabled", "False"), VAN_POSITIONS))
                .withPlay(play -> {
                    final Query markers = VisStories.drawn(play, GEO_MARKER, 10);
                    final Play inFrame = play.withinFrame(VisStories.frame(play));
                    play.expect("the markers' colours", () -> {
                        final List<Object> colours = new ArrayList<>();
                        for (int i = 0; i < 10; i++) {
                            colours.add(markers.nth(i).computedStyle("fill").get());
                        }
                        return colours;
                    }).toSatisfy("one for each van", colours -> new HashSet<>((List<?>) colours).size() == 2);
                    play.expect(inFrame.querySelectorAll(".vis-map__time-key")).toHaveLength(0);
                    VisStories.expectNoProblems(play);
                })
                // The markers are a tab stop of their own (the map is another): Right moves along a
                // van's stops, Down to the other van's, and Enter selects one
                .story("Keyboard", VisStories.story("GeoMap", geoSettings("series", "Van"), VAN_POSITIONS))
                .withPlay(play -> {
                    VisStories.drawn(play, GEO_MARKER, 10);
                    final Play inFrame = play.withinFrame(VisStories.frame(play));
                    final Query tabStop = inFrame.querySelector(GEO_MARKER + "[tabindex='0']");
                    play.waitFor(() -> play.expect(tabStop).toBeVisible());
                    play.run("focus the markers' tab stop", () -> tabStop.element().get().focus());
                    final Query first = inFrame.getByRole("img", VAN_1_FIRST);
                    play.waitFor(() -> play.expect(first).toHaveFocus());
                    VisStories.press(play, "{ArrowRight}");
                    play.waitFor(() -> play.expect(inFrame.getByRole("img",
                            "van-1, Time: 2026-09-07T09:20:00.000Z, Latitude: 51.507, Longitude: -0.087"))
                            .toHaveFocus());
                    VisStories.press(play, "{ArrowDown}");
                    final Query van2 = inFrame.getByRole("img", TextMatch.startingWith(
                            "van-2, Time: 2026-09-07T09:20:00.000Z, Latitude: 51.503, Longitude: -0.084"));
                    play.waitFor(() -> play.expect(van2).toHaveFocus());
                    VisStories.press(play, "{Enter}");
                    play.waitFor(() -> play.expect(van2).toHaveClass("vis-map__marker--selected"));
                    VisStories.expectNoProblems(play);
                })
                // With the mouse over a marker, Stroom's tooltip (not a Leaflet popup) says what it is
                .story("Hover", VisStories.story("GeoMap", geoSettings("series", "Van"), VAN_POSITIONS))
                .withPlay(play -> {
                    VisStories.drawn(play, GEO_MARKER, 10);
                    final Play inFrame = play.withinFrame(VisStories.frame(play));
                    play.hover(inFrame.getByRole("img", VAN_1_FIRST));
                    final Query tooltip = VisStories.tooltip(play);
                    play.waitFor(() -> play.expect(tooltip).toBeVisible());
                    play.expect(tooltip).toHaveTextContent(TextMatch.containing("09:00:00"));
                    play.expect(inFrame.querySelectorAll(".leaflet-popup")).toHaveLength(0);
                    VisStories.expectNoProblems(play);
                })
                // An Icon Name naming an icon (or FontAwesome 4's name for it, as the legacy map took,
                // e.g. fa-user) shows it on a badge in the marker's colour, black or white to read on
                // it; one named after a shape has that shape, and any other value another (the legacy
                // map's icons needed a font it never had, so never showed)
                .story("Icons", VisStories.story("GeoMap", geoSettings("iconName", "Icon"),
                        VisTable.builder().text("Van").number("Latitude").number("Longitude").date("Time").text("Icon")
                                .row("depot", 51.505, -0.09, START, "square")
                                .row("van-1", 51.51, -0.08, START, "truck")
                                .row("van-2", 51.50, -0.10, START, null)
                                .row("driver", 51.515, -0.095, START, "fa-user")
                                .row("yard", 51.495, -0.085, START, "unicorn")
                                .build()))
                .withPlay(play -> {
                    VisStories.drawn(play, GEO_MARKER, 5);
                    final Play inFrame = play.withinFrame(VisStories.frame(play));
                    final Query shapes = inFrame.querySelectorAll("path.vis-map__shape[d]");
                    final Query icons = inFrame.querySelectorAll("path.vis-map__icon[d]");
                    play.expect(shapes).toHaveLength(3);
                    play.expect(icons).toHaveLength(2);
                    play.expect(inFrame.querySelectorAll("circle.vis-map__badge[r='12']")).toHaveLength(2);
                    play.expect("the markers' shapes", () -> List.of(shapes.nth(0).attribute("d").get(),
                                    shapes.nth(1).attribute("d").get(), shapes.nth(2).attribute("d").get()))
                            .toSatisfy("three", found -> new HashSet<>((List<?>) found).size() == 3);
                    play.expect("the icons", () -> List.of(icons.nth(0).attribute("d").get(),
                                    icons.nth(1).attribute("d").get()))
                            .toSatisfy("two", found -> new HashSet<>((List<?>) found).size() == 2);
                    play.expect("an icon's colour", () -> icons.first().computedStyle("stroke").get())
                            .toSatisfy("black or white", colour -> "rgb(0, 0, 0)".equals(colour)
                                                                   || "rgb(255, 255, 255)".equals(colour));
                    play.expect(play.withinFrame(VisStories.frame(play)).getByRole("img",
                                    "van-1, Time: 2026-09-07T09:00:00.000Z, Latitude: 51.51, Longitude: -0.08, "
                                    + "Icon: truck"))
                            .toBeVisible();
                    VisStories.expectNoProblems(play);
                })
                // The map's own buttons, in Stroom's look: Zoom in spreads the markers apart, Fit to
                // markers brings them back, and Zoom out can't be pressed once the map is as far out
                // as it goes
                .story("Buttons", VisStories.story("GeoMap", geoSettings("series", "Van"), VAN_POSITIONS))
                .withPlay(play -> {
                    final Query markers = VisStories.drawn(play, GEO_MARKER, 10);
                    final Play inFrame = play.withinFrame(VisStories.frame(play));
                    play.expect(inFrame.getByRole("group", "Map zoom")).toBeVisible();
                    final Query zoomIn = inFrame.getByRole("button", "Zoom in");
                    final Query zoomOut = inFrame.getByRole("button", "Zoom out");
                    final Query fit = inFrame.getByRole("button", "Fit to markers");
                    play.expect(inFrame.querySelectorAll(".leaflet-control-zoom")).toHaveLength(0);
                    play.expect(zoomIn.computedStyle("width")).toBe("28px");
                    // How far apart the first and last markers are on screen
                    final Supplier<Double> spread = () -> Math.abs(
                            markers.first().left().get() - markers.nth(9).left().get())
                            + Math.abs(markers.first().top().get() - markers.nth(9).top().get());
                    final double[] fitted = new double[1];
                    play.run("note the fitted spread", () -> fitted[0] = spread.get());
                    // Leaflet's zoom animation ends when its map pane loses this class (a zoom asked for
                    // before then is ignored, as Leaflet's own buttons' are)
                    final Query zooming = inFrame.querySelectorAll(".leaflet-zoom-anim");
                    play.click(zoomIn);
                    play.waitFor(() -> play.expect("the spread after Zoom in over the fitted spread",
                            () -> spread.get() / fitted[0]).toBeGreaterThan(1.8));
                    play.waitFor(() -> play.expect(zooming).toHaveLength(0));
                    play.click(fit);
                    play.waitFor(() -> play.expect("the spread after Fit to markers over the fitted spread",
                            () -> Math.abs(spread.get() / fitted[0] - 1)).toBeLessThan(0.05));
                    // Zoom out until it can't go further (the clicks once it can't do nothing)
                    for (int i = 0; i < 20; i++) {
                        play.fireEvent().click(zoomOut);
                        play.waitFor(() -> play.expect(zooming).toHaveLength(0));
                    }
                    play.waitFor(() -> play.expect(zoomOut).toBeDisabled());
                    play.expect(zoomIn).toBeEnabled();
                    VisStories.expectNoProblems(play);
                })
                // Rows without a place on the earth are left out, and said to be (one such row stopped
                // the legacy map drawing at all)
                .story("AwkwardData", VisStories.story("GeoMap", geoSettings(),
                        VisTable.builder().text("Van").number("Latitude").number("Longitude").date("Time")
                                .row("van-1", 51.505, -0.09, START)
                                .row("van-2", null, -0.09, START)
                                .row("van-3", 95.0, -0.09, START)
                                .row("van-4", 51.505, 200.0, START)
                                .build()))
                .withPlay(play -> {
                    VisStories.drawn(play, GEO_MARKER, 1);
                    play.waitFor(() -> play.expect(play.withinFrame(VisStories.frame(play))
                                    .querySelector("svg.vis-map__overlay"))
                            .toHaveAttribute("aria-label", "Map of Van: 1 marker, coloured by Time from "
                                                           + "2026-09-07T09:00:00.000Z to 2026-09-07T09:00:00.000Z, "
                                                           + "3 rows without a place left out"));
                    VisStories.expectNoProblems(play);
                })
                // A map for each van
                .story("GridSeries", VisStories.story("GeoMap", geoSettings("gridSeries", "Van"), VAN_POSITIONS))
                .withPlay(play -> {
                    VisStories.drawn(play, "div.leaflet-container", 2);
                    VisStories.drawn(play, GEO_MARKER, 10);
                    final Query names = play.withinFrame(VisStories.frame(play)).querySelectorAll(".vis-grid__name");
                    play.expect("the maps' names", () -> names.textContents().get())
                            .toEqual(List.of("van-1", "van-2"));
                    VisStories.expectNoProblems(play);
                })
                // A search that found nothing: the map where the settings say to start, saying so
                .story("NoData", VisStories.story("GeoMap", geoSettings(),
                        VisTable.builder().text("Van").number("Latitude").number("Longitude").date("Time").build()))
                .withPlay(play -> {
                    VisStories.drawn(play, "div.leaflet-container", 1);
                    final Play inFrame = play.withinFrame(VisStories.frame(play));
                    play.waitFor(() -> play.expect(inFrame.querySelector("svg.vis-map__overlay"))
                            .toHaveAttribute("aria-label", "Map of Van: no data"));
                    play.expect(inFrame.querySelector(".vis-map__empty")).toBeVisible();
                    play.expect(inFrame.querySelector(".vis-map__empty")).toHaveTextContent("No data");
                    VisStories.expectNoProblems(play);
                });
    }

    // The GeoMap settings of the vans' positions, starting on central London, and any more
    private static Map<String, String> geoSettings(final String... more) {
        final Map<String, String> settings = VisStories.settings(
                "name", "Van", "lat", "Latitude", "lon", "Longitude", "eventTime", "Time",
                "initialLatitude", "51.505", "initialLongitude", "-0.09", "initialZoomLevel", "14",
                "tileServerUrl", TILE_URL, "tileServerAttribution", "Workbench");
        settings.putAll(VisStories.settings(more));
        return settings;
    }

    private static VisTable vanPositions() {
        final VisTable.Builder builder = VisTable.builder()
                .text("Van")
                .number("Latitude")
                .number("Longitude")
                .date("Time");
        final String[] vans = {"van-1", "van-2"};
        for (int van = 0; van < vans.length; van++) {
            for (int stop = 0; stop < 5; stop++) {
                // Each van heads off in its own direction from the depot. In thousandths of a
                // degree, so that the table shows them exactly
                final int direction = van == 0
                        ? 1
                        : -1;
                builder.row(vans[van], (51_505 + direction * stop * 2) / 1000.0, (-90 + stop * 3 * (van + 1)) / 1000.0,
                        START + stop * 20 * MINUTE);
            }
        }
        return builder.build();
    }

    private static VisTable badgeSightings() {
        final VisTable.Builder builder = VisTable.builder()
                .text("Badge")
                .text("Campus")
                .text("Building")
                .text("Floor")
                .number("X")
                .number("Y")
                .date("Time");
        final String[] badges = {"badge-17", "badge-42"};
        for (int badge = 0; badge < badges.length; badge++) {
            for (int sighting = 0; sighting < 4; sighting++) {
                builder.row(badges[badge], "Head Office", "Main Building", "Ground Floor",
                        10 + sighting * 20 + badge * 5, 15 + badge * 25, START + sighting * 30 * MINUTE);
            }
        }
        return builder.build();
    }

    // The floor plan of each floor of each building of each campus, as FloorMap's 'config' setting
    // (the ground floor's zones from the workbench's own zone dictionary)
    private static String floorMapConfig() {
        return floorMapConfig("staticUrl:" + FLOOR_ZONES);
    }

    // As floorMapConfig(), the ground floor's zones from a zone dictionary, e.g. a Stroom dictionary's
    // UUID
    private static String floorMapConfig(final String zoneDictionary) {
        return "{\"Head Office\": {\"Main Building\": {"
               + "\"Ground Floor\": {\"image\": \"" + FLOOR_PLAN + "\", \"width\": 100, \"height\": 60, "
               + "\"zoneDictionaryUuid\": \"" + zoneDictionary + "\"}, "
               + "\"First Floor\": {\"image\": \"" + FLOOR_PLAN + "\", \"width\": 100, \"height\": 60}}}}";
    }
}
