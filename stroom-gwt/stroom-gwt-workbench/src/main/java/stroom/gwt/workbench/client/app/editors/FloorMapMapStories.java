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

package stroom.gwt.workbench.client.app.editors;

import stroom.docref.DocRef;
import stroom.floormap.client.component.FloorMapMapController;
import stroom.floormap.shared.FloorMapDoc;
import stroom.floormap.shared.FloorMapResource;
import stroom.gwt.workbench.client.app.editors.DocEditors.DocResource;
import stroom.gwt.workbench.client.app.query.QueryFixtures;
import stroom.gwt.workbench.client.app.rest.RestFixtures;
import stroom.gwt.workbench.client.app.rest.RestReply;
import stroom.gwt.workbench.client.app.screen.StroomDom;
import stroom.gwt.workbench.framework.client.play.Play;
import stroom.gwt.workbench.framework.client.play.Query;
import stroom.gwt.workbench.framework.client.play.TextMatch;
import stroom.gwt.workbench.framework.client.story.StoryContext;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.Timer;
import com.google.gwt.user.client.ui.Widget;

import java.util.List;

/// The `App/Editors/FloorMapMap` stories, showing Stroom's real [FloorMapMapController] (a Floor
/// Map's read-only 'Map' tab: the map canvas, its timeline and its dock of Tracking, Layers and
/// Groups panels) with fake REST replies, for its three lists: the Tracking panel's entities
/// (`FloorMapTrackingController`), the Groups panel's groups (`FloorMapGroupsController`) and the
/// members of a cluster on the map (`FloorMapClusterDialogController`, a dialog opened by clicking the
/// cluster's glyph).
///
/// As `FloorMapDocEditor` does for its Map tab, the story fetches the document
/// (`GET /floormap/v1/{uuid}`) and reads it into the tab ([DocEditors#open]). The document's groups
/// fill the Groups panel. The tab then runs its StroomQL searches (`POST /query/v1/search/{node}`,
/// told apart by their component's name): the facts query (`factsHistoryTable`, built from the
/// document's default value schema), which finds a meeting room and a desk; the events query
/// (`eventsTable`), whose four people fill the Tracking panel (three of them stand at the same
/// place in the meeting room, so the map draws them as one cluster); and the timeline's histogram
/// and extent queries (`histogramTable`), which find nothing. A finished search's result store is
/// destroyed (`POST /result-store/v1/destroy/{node}`) when the next one starts.
public final class FloorMapMapStories {

    private static final DocRef DOC_REF = new DocRef(FloorMapDoc.TYPE, "floormap-1", "Office");

    // FloorMapResource.fetch(): a floor map reading its events from the store 'office-events',
    // with two groups of people
    private static final String DOC = """
            {"type": "FloorMap", "uuid": "floormap-1", "name": "Office",
              "eventsStoreRef": {"type": "FloorMapEventStore", "uuid": "events-1", "name": "office-events"},
              "eventsQuery": "from param('EventStore')\\nselect Key as \\"Entity ID\\", Location, Type",
              "groups": [
                {"id": "g-engineers", "name": "Engineers", "colour": "#1f77b4",
                  "memberIds": ["alice@hq", "bob@hq"]},
                {"id": "g-visitors", "name": "Visitors", "colour": "#ff7f0e",
                  "memberIds": ["dave@hq"]}
              ]}""";

    // The events query's result: one row per person, at the selected time. Alice, Bob and Carol
    // stand at the same place, so the map merges them into one cluster
    private static final String EVENTS = QueryFixtures.response(true, QueryFixtures.tableResult(
            "table",
            "[{\"id\": \"c-id\", \"name\": \"Entity ID\"}, {\"id\": \"c-location\", \"name\": \"Location\"},"
            + " {\"id\": \"c-type\", \"name\": \"Type\"}]",
            """
                    [{"values": ["alice@hq", "100, 100", "person"], "depth": 0},
                      {"values": ["bob@hq", "100, 100", "person"], "depth": 0},
                      {"values": ["carol@hq", "100, 100", "person"], "depth": 0},
                      {"values": ["dave@hq", "400, 300", "person"], "depth": 0}]""",
            4));

    // The facts query's columns (those of the document's default value schema that the map reads)
    private static final String FACT_COLUMNS =
            "[{\"id\": \"c-key\", \"name\": \"Key\"}, {\"id\": \"c-ms\", \"name\": \"Effective Time Ms\"},"
            + " {\"id\": \"c-type\", \"name\": \"type\"}, {\"id\": \"c-name\", \"name\": \"name\"},"
            + " {\"id\": \"c-coords\", \"name\": \"coords\"}, {\"id\": \"c-geometry\", \"name\": \"geometry\"},"
            + " {\"id\": \"c-fill\", \"name\": \"fill\"}]";

    // The meeting room: an area around the place where Alice, Bob and Carol stand
    private static final String MEETING_ROOM = """
            {"values": ["meeting-room", "1700000000000", "area", "Meeting Room", null,
                "[[0, 0], [200, 0], [200, 200], [0, 200]]", FILL], "depth": 0}""";

    // A desk beside Dave
    private static final String DESK = """
            {"values": ["desk-1", "1700000000000", "desk", null, "[420, 300]", null, null], "depth": 0}""";

    // The facts query's result: a meeting room and a desk, each effective since November 2023
    private static final String FACTS = facts(MEETING_ROOM.replace("FILL", "null"), DESK);

    // A desk whose key starts as a transform handle's id does (FloorMapJsonKeys.HANDLE_PREFIX)
    private static final String HANDLE_KEY_DESK = """
            {"values": ["__handle_rotate", "1700000000000", "desk", null, "[600, 100]", null, null], "depth": 0}""";

    // The colour the document gives areas
    private static final String AREA_COLOUR = "#00aa00";

    // The document, with a colour for areas
    private static final String DOC_WITH_AREA_COLOUR = DOC.replace("\"groups\": [",
            "\"typeStyles\": [{\"type\": \"area\", \"colour\": \"" + AREA_COLOUR + "\"}],\n  \"groups\": [");

    // An area fill that is markup, not a colour (as a JSON string)
    private static final String BAD_FILL_JSON = "\"red\\\"/><script>alert(1)</script>\"";
    // The timeline's histogram and extent queries: no events in the range
    private static final String NO_ROWS = QueryFixtures.response(true,
            QueryFixtures.tableResult("table", "[]", "[]", 0));

    private static final RestFixtures FIXTURES = fixtures(DOC, FACTS);

    // Grid-agnostic selectors: a row, and a cell
    private static final String ROW = "tr, [role=\"row\"]";
    private static final String CELL = "td, [data-col]";

    // A tab's label in the dock's tab bar
    private static final String TAB_LABEL = ".tab-bar__label";

    // The Tracking panel's grid (its id is made unique with a suffix)
    private static final String TRACKING_GRID = "[id^=\"floormap-tracking-grid\"]";

    // A cluster's glyph on the canvas (FloorMapJsonKeys.CLUSTER_PREFIX)
    private static final String CLUSTER_GLYPH = "[id^=\"__cluster_\"]";

    private FloorMapMapStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        registry.component("App/Editors/FloorMapMap", FloorMapMapStories.class)
                .layout(StoryLayout.FULLSCREEN)
                // The Tracking panel, the dock's first tab, lists the people the events query
                // found, by name, with their type and id
                .story("Tracking", FloorMapMapStories::render)
                .withPlay(play -> {
                    eventsShown(play);
                    final Play tracking = play.within(play.querySelector(TRACKING_GRID));
                    play.waitFor(() -> play.expect(tracking.getByText("alice").closest(ROW))
                            .toHaveTextContent("Meeting Room"));
                    expectRow(tracking, "alice", "person", "Meeting Room", "alice@hq");
                    expectRow(tracking, "bob", "person", "Meeting Room", "bob@hq");
                    expectRow(tracking, "carol", "person", "Meeting Room", "carol@hq");
                    expectRow(tracking, "dave", "person", "dave@hq");
                    play.expect(tracking.getByText("dave").closest(ROW)).not().toHaveTextContent("Meeting Room");
                    // One row per person, each with its type in a cell; the facts (the room and
                    // the desk) are hidden until 'Show Facts' is pressed
                    play.expect(tracking.getAllByText("person")).toHaveLength(4);
                    play.expect(tracking.getByText("dave").closest(CELL)).toBeInTheDocument();
                    play.expect(tracking.queryByText("desk-1")).toBeNull();
                    DocEditors.expectNoProblems(play);
                })
                // The timeline's Playback Settings button opens its settings, looping playback, with
                // Show All off until there is data; a second press closes them
                .story("TimelineSettings", FloorMapMapStories::render)
                .withPlay(play -> {
                    eventsShown(play);
                    final Play screen = play.screen();
                    final Query button = play.getByRole("button", "Playback Settings");
                    play.click(button);
                    final Query loop = screen.findByRole("checkbox", "Loop Playback");
                    play.expect(loop).toBeChecked();
                    play.expect(screen.getByRole("button", StroomDom.button("Show All"))).toBeDisabled();
                    play.expect(screen.getByRole("group", "Timeline start date and time")).toBeInTheDocument();
                    play.click(button);
                    play.waitFor(() -> play.expect(screen.queryByRole("checkbox", "Loop Playback")).toBeNull());
                    DocEditors.expectNoProblems(play);
                })
                // The timeline's bar is a slider from 0 to 100; its speed badge is a button named for
                // what it is and its speed, whose menu changes the speed. The Map tab's timeline has
                // no help button (only the Editor tab's has)
                .story("TimelineSpeed", FloorMapMapStories::render)
                .withPlay(play -> {
                    eventsShown(play);
                    final Play screen = play.screen();
                    final Query slider = play.getByRole("slider");
                    play.expect(slider).toHaveAttribute("aria-valuemin", "0");
                    play.expect(slider).toHaveAttribute("aria-valuemax", "100");
                    play.click(play.getByRole("button", "Playback speed x1, opens the speed menu"));
                    play.click(screen.findByRole("menuitem", "x10"));
                    play.waitFor(() -> play.expect(play.getByRole("button", "Playback speed x10, opens the speed menu"))
                            .toBeInTheDocument());
                    play.expect(play.queryByRole("button", "Timeline help")).toBeNull();
                    DocEditors.expectNoProblems(play);
                })
                // The Groups panel lists the document's groups, each with its number of members
                // and how many of them have a position on the map at this time
                .story("Groups", FloorMapMapStories::render)
                .withPlay(play -> {
                    eventsShown(play);
                    // The dock's tabs all fit, so all are shown (its tab bar once lost its own classes,
                    // drew its overflow button huge and put every tab but the first in an overflow
                    // menu that never opened)
                    for (final String tab : List.of("Tracking", "Layers", "Groups")) {
                        play.expect(play.getByText(tab, TAB_LABEL)).toBeVisible();
                    }
                    play.click(play.getByText("Groups", TAB_LABEL));
                    final Query engineers = play.findByText("Engineers").closest(ROW);
                    play.waitFor(() -> play.expect(engineers).toHaveTextContent("2 of 2"));
                    play.expect(engineers).toHaveTextContent("Meeting Room");
                    final Query visitors = play.getByText("Visitors").closest(ROW);
                    play.expect(visitors).toHaveTextContent("1 of 1");
                    play.expect(visitors).not().toHaveTextContent("Meeting Room");
                    DocEditors.expectNoProblems(play);
                })
                // Pressing a group selects it (a press once selected nothing, so it could never be
                // edited), and editing it opens its dialog, its members ticked among everyone seen on
                // the map; Cancel closes it
                .story("EditGroup", FloorMapMapStories::render)
                .withPlay(play -> {
                    final Play screen = play.screen();
                    eventsShown(play);
                    play.click(play.getByText("Groups", TAB_LABEL));
                    play.click(play.findByText("Engineers"));
                    play.waitFor(() -> play.expect(play.getByText("Engineers").closest(ROW))
                            .toHaveClass(StroomDom.SELECTED_ROW));
                    final Query edit = play.getByRole("button", "Edit Group (name, colour and members)");
                    play.waitFor(() -> play.expect(edit).toBeEnabled());
                    play.click(edit);
                    final Query title = screen.findByText("Group — Engineers", StroomDom.DIALOG_TITLE);
                    final Play dialog = screen.within(title.closest(StroomDom.DIALOG));
                    play.expect(dialog.getByRole("textbox", "Name")).toHaveValue("Engineers");
                    play.expect(dialog.getByRole("checkbox", "alice")).toBeChecked();
                    play.expect(dialog.getByRole("checkbox", "bob")).toBeChecked();
                    play.expect(dialog.getByRole("checkbox", "carol")).not().toBeChecked();
                    play.expect(dialog.getByText(TextMatch.startingWith("2 members selected of")))
                            .toBeInTheDocument();
                    play.click(dialog.getByRole("button", StroomDom.button("Cancel")));
                    play.waitFor(() -> play.expect(screen.queryByText("Group — Engineers")).toBeNull());
                    DocEditors.expectNoProblems(play);
                })
                // Pressing a group's eye highlights it without selecting the group, as the eye is a
                // button of its own
                .story("HighlightDoesNotSelect", FloorMapMapStories::render)
                .withPlay(play -> {
                    eventsShown(play);
                    play.click(play.getByText("Groups", TAB_LABEL));
                    final Query engineers = play.findByText("Engineers").closest(ROW);
                    final Query eye = play.within(engineers).querySelector(".icon-cell--button");
                    play.click(eye);
                    play.waitFor(() -> play.expect(play.within(engineers)
                            .querySelector(".icon-cell--button")).toHaveAttribute("title",
                            "Highlighted on the map — click to stop highlighting"));
                    play.expect(engineers).not().toHaveClass(StroomDom.SELECTED_ROW);
                    play.expect(play.getByRole("button", "Edit Group (name, colour and members)"))
                            .toHaveAttribute("aria-disabled", "true");
                    DocEditors.expectNoProblems(play);
                })
                // A new group is named 'Group' until renamed; OK adds it to the panel
                .story("NewGroup", FloorMapMapStories::render)
                .withPlay(play -> {
                    final Play screen = play.screen();
                    eventsShown(play);
                    play.click(play.getByText("Groups", TAB_LABEL));
                    play.findByText("Engineers");
                    play.click(play.getByRole("button", "New Group"));
                    final Query title = screen.findByText("New Group", StroomDom.DIALOG_TITLE);
                    final Play dialog = screen.within(title.closest(StroomDom.DIALOG));
                    play.expect(dialog.getByRole("textbox", "Name")).toHaveValue("Group");
                    play.expect(dialog.getByText("0 members selected of 6 entities")).toBeInTheDocument();
                    play.click(dialog.getByRole("button", StroomDom.button("OK")));
                    play.waitFor(() -> play.expect(screen.queryByText("New Group", StroomDom.DIALOG_TITLE))
                            .toBeNull());
                    play.expect(play.getByText("Group").closest(ROW)).toBeInTheDocument();
                    DocEditors.expectNoProblems(play);
                })
                // Clicking a cluster on the map lists its members in a dialog, with each one's
                // type and groups
                .story("Cluster", FloorMapMapStories::render)
                .withPlay(play -> {
                    final Play screen = play.screen();
                    eventsShown(play);
                    play.waitFor(() -> play.expect(play.querySelector(CLUSTER_GLYPH)).toBeInTheDocument());
                    play.click(play.querySelector(CLUSTER_GLYPH));
                    final Query title = screen.findByText(TextMatch.endingWith("in this cluster"),
                            StroomDom.DIALOG_TITLE);
                    final Play dialog = screen.within(title.closest(StroomDom.DIALOG));
                    expectRow(dialog, "alice", "person", "Meeting Room", "Engineers", "alice@hq");
                    expectRow(dialog, "bob", "person", "Meeting Room", "Engineers", "bob@hq");
                    expectRow(dialog, "carol", "person", "Meeting Room", "carol@hq");
                    play.expect(dialog.getByText("carol").closest(ROW)).not().toHaveTextContent("Engineers");
                    // Dave stands elsewhere, so is not in the cluster
                    play.expect(dialog.queryByText("dave")).toBeNull();
                    DocEditors.expectNoProblems(play);
                })
                // Pressing an object on the map selects it in the Tracking panel (showing the facts,
                // as the desk is one) and tracks it
                .story("SelectObject", FloorMapMapStories::render)
                .withPlay(play -> {
                    eventsShown(play);
                    final Play tracking = play.within(play.querySelector(TRACKING_GRID));
                    play.expect(tracking.queryByText("desk-1")).toBeNull();
                    play.waitFor(() -> play.expect(play.querySelector("[id=\"__obj_desk-1\"]")).toBeInTheDocument());
                    play.click(play.querySelector("[id=\"__obj_desk-1\"]"));
                    play.waitFor(() -> play.expect(tracking.getAllByText("desk-1").nth(0).closest(ROW))
                            .toHaveClass(StroomDom.SELECTED_ROW));
                    play.expect(tracking.querySelectorAll("." + StroomDom.SELECTED_ROW)).toHaveLength(1);
                    DocEditors.expectNoProblems(play);
                })
                // An object whose key starts as a transform handle's id does (`__handle_`) is still an
                // object: pressing it selects it, as any other (it was once taken for a handle and
                // ignored)
                .story("SelectObjectWithHandleKey", context -> render(context, fixtures(DOC,
                        facts(MEETING_ROOM.replace("FILL", "null"), DESK, HANDLE_KEY_DESK))))
                .withPlay(play -> {
                    eventsShown(play);
                    final Play tracking = play.within(play.querySelector(TRACKING_GRID));
                    final String glyph = "[id=\"__obj___handle_rotate\"]";
                    play.waitFor(() -> play.expect(play.querySelector(glyph)).toBeInTheDocument());
                    play.click(play.querySelector(glyph));
                    play.waitFor(() -> play.expect(tracking.getAllByText("__handle_rotate").nth(0).closest(ROW))
                            .toHaveClass(StroomDom.SELECTED_ROW));
                    play.expect(tracking.querySelectorAll("." + StroomDom.SELECTED_ROW)).toHaveLength(1);
                    DocEditors.expectNoProblems(play);
                })
                // An area's fill comes from the data, so one that isn't a hex colour (here markup) is
                // ignored: the area is drawn in its type's colour, and no markup reaches the map
                .story("BadAreaFill", context -> render(context, fixtures(DOC_WITH_AREA_COLOUR,
                        facts(MEETING_ROOM.replace("FILL", BAD_FILL_JSON), DESK))))
                .withPlay(play -> {
                    eventsShown(play);
                    final Query area = play.querySelector(
                            "[id=\"__g___obj_meeting-room\"] polygon[pointer-events=\"none\"]");
                    play.waitFor(() -> play.expect(area).toBeInTheDocument());
                    play.expect(area).toHaveAttribute("fill", AREA_COLOUR);
                    play.expect(play.screen().querySelector("svg script")).toBeNull();
                    play.expect(play.screen().querySelector("[fill*=\"script\"]")).toBeNull();
                    DocEditors.expectNoProblems(play);
                });
    }

    // The facts query's reply with these rows
    private static String facts(final String... rows) {
        return QueryFixtures.response(true, QueryFixtures.tableResult(
                "table",
                FACT_COLUMNS,
                "[" + String.join(", ", rows) + "]",
                rows.length));
    }

    // The tab's routes, for a document and the facts query's reply
    private static RestFixtures fixtures(final String doc, final String facts) {
        return QueryFixtures.resultStoreRoutes(
                        DocEditors.docSelectionRoutes(DocEditors.permissionRoutes(RestFixtures.builder())))
                .get("/floormap/v1/floormap-1", RestReply.json(doc))
                .put("/floormap/v1/floormap-1", request -> RestReply.json(request.getBody()))
                .route(QueryFixtures.SEARCH.withJsonBodyContaining(
                        "{\"searchRequestSource\": {\"componentName\": \"eventsTable\"}}"), RestReply.json(EVENTS))
                .route(QueryFixtures.SEARCH.withJsonBodyContaining(
                        "{\"searchRequestSource\": {\"componentName\": \"histogramTable\"}}"), RestReply.json(NO_ROWS))
                .route(QueryFixtures.SEARCH.withJsonBodyContaining(
                        "{\"searchRequestSource\": {\"componentName\": \"factsHistoryTable\"}}"), RestReply.json(facts))
                .build();
    }

    // Waits for the events query's people to reach the Tracking panel
    private static void eventsShown(final Play play) {
        play.waitFor(5000, () -> play.expect(play.within(play.querySelector(TRACKING_GRID)).getByText("alice"))
                .toBeInTheDocument());
    }

    // The row whose name is `name` also shows each of `texts`
    private static void expectRow(final Play scope, final String name, final String... texts) {
        final Query row = scope.getByText(name).closest(ROW);
        for (final String text : texts) {
            scope.expect(row).toHaveTextContent(text);
        }
    }

    private static Widget render(final StoryContext context) {
        return render(context, FIXTURES);
    }

    private static Widget render(final StoryContext context, final RestFixtures fixtures) {
        final FloorMapResource resource = GWT.create(FloorMapResource.class);
        return DocEditors.render(context, fixtures, false, (harness, injector) -> {
            final FloorMapMapController controller = injector.getFloorMapMap();
            // The tab checks every 10 seconds whether its facts are due a re-read, from when it
            // reads the document until it is closed (which a story never does)
            harness.addTimer(factsCadenceTimer(controller));
            DocEditors.open(harness,
                    DOC_REF,
                    // Shown as the document shows its Map tab
                    new TabStory<>(harness.getEventBus(), controller,
                            FloorMapMapController::read, FloorMapMapController::write),
                    DocResource.of(
                            restFactory -> restFactory.create(resource)
                                    .method(res -> res.fetch(DOC_REF.getUuid())),
                            (restFactory, doc) -> restFactory.create(resource)
                                    .method(res -> res.update(doc.getUuid(), doc))));
        });
    }

    // FloorMapMapController's private facts cadence timer
    private static native Timer factsCadenceTimer(FloorMapMapController controller) /*-{
        return controller.@stroom.floormap.client.component.FloorMapMapController::factsCadenceTimer;
    }-*/;
}
