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
import stroom.floormap.client.presenter.FloorMapMapPresenter;
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

/// The `App/Editors/FloorMapMap` stories, showing Stroom's real [FloorMapMapPresenter] (a Floor
/// Map's read-only 'Map' tab: the map canvas, its timeline and its dock of Tracking, Layers and
/// Groups panels) with fake REST replies, for its three lists: the Tracking panel's entities
/// (`FloorMapTrackingPresenter`), the Groups panel's groups (`FloorMapGroupsPresenter`) and the
/// members of a cluster on the map (`FloorMapClusterPresenter`, a dialog opened by clicking the
/// cluster's glyph).
///
/// As `FloorMapPresenter` does for its Map tab, the story fetches the document
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

    // The facts query's result (the columns of the document's default value schema that the map
    // reads): a meeting room, an area around the place where Alice, Bob and Carol stand, and a desk
    // beside Dave, each effective since November 2023
    private static final String FACTS = QueryFixtures.response(true, QueryFixtures.tableResult(
            "table",
            "[{\"id\": \"c-key\", \"name\": \"Key\"}, {\"id\": \"c-ms\", \"name\": \"Effective Time Ms\"},"
            + " {\"id\": \"c-type\", \"name\": \"type\"}, {\"id\": \"c-name\", \"name\": \"name\"},"
            + " {\"id\": \"c-coords\", \"name\": \"coords\"}, {\"id\": \"c-geometry\", \"name\": \"geometry\"}]",
            """
                    [{"values": ["meeting-room", "1700000000000", "area", "Meeting Room", null,
                        "[[0, 0], [200, 0], [200, 200], [0, 200]]"], "depth": 0},
                      {"values": ["desk-1", "1700000000000", "desk", null, "[420, 300]", null], "depth": 0}]""",
            2));

    // The timeline's histogram and extent queries: no events in the range
    private static final String NO_ROWS = QueryFixtures.response(true,
            QueryFixtures.tableResult("table", "[]", "[]", 0));

    private static final RestFixtures FIXTURES = QueryFixtures.resultStoreRoutes(
                    DocEditors.docSelectionRoutes(DocEditors.permissionRoutes(RestFixtures.builder())))
            .get("/floormap/v1/floormap-1", RestReply.json(DOC))
            .put("/floormap/v1/floormap-1", request -> RestReply.json(request.getBody()))
            .route(QueryFixtures.SEARCH.withJsonBodyContaining(
                    "{\"searchRequestSource\": {\"componentName\": \"eventsTable\"}}"), RestReply.json(EVENTS))
            .route(QueryFixtures.SEARCH.withJsonBodyContaining(
                    "{\"searchRequestSource\": {\"componentName\": \"histogramTable\"}}"), RestReply.json(NO_ROWS))
            .route(QueryFixtures.SEARCH.withJsonBodyContaining(
                    "{\"searchRequestSource\": {\"componentName\": \"factsHistoryTable\"}}"), RestReply.json(FACTS))
            .build();

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
                // The Groups panel lists the document's groups, each with its number of members
                // and how many of them have a position on the map at this time
                .story("Groups", FloorMapMapStories::render)
                .withPlay(play -> {
                    eventsShown(play);
                    // The dock's tab bar loses its link tab bar classes (FloorMapDockViewImpl.ui.xml
                    // sets its styleName), so it puts every tab but the first in its overflow menu,
                    // which never opens (FloorMapDockPresenter doesn't pass on the tab bar's
                    // ShowMenuEvent). So the play clicks the hidden Groups tab itself
                    play.click(play.getByText("Groups", TAB_LABEL));
                    final Query engineers = play.findByText("Engineers").closest(ROW);
                    play.waitFor(() -> play.expect(engineers).toHaveTextContent("2 of 2"));
                    play.expect(engineers).toHaveTextContent("Meeting Room");
                    final Query visitors = play.getByText("Visitors").closest(ROW);
                    play.expect(visitors).toHaveTextContent("1 of 1");
                    play.expect(visitors).not().toHaveTextContent("Meeting Room");
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
                });
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
        final FloorMapResource resource = GWT.create(FloorMapResource.class);
        return DocEditors.render(context, FIXTURES, false, (harness, injector) -> {
            final FloorMapMapPresenter presenter = injector.getFloorMapMapPresenter();
            // The tab checks every 10 seconds whether its facts are due a re-read, from when it
            // reads the document until it is closed (which a story never does)
            harness.addTimer(factsCadenceTimer(presenter));
            DocEditors.open(harness,
                    DOC_REF,
                    presenter,
                    DocResource.of(
                            restFactory -> restFactory.create(resource)
                                    .method(res -> res.fetch(DOC_REF.getUuid())),
                            (restFactory, doc) -> restFactory.create(resource)
                                    .method(res -> res.update(doc.getUuid(), doc))));
        });
    }

    // FloorMapMapPresenter's private facts cadence timer
    private static native Timer factsCadenceTimer(FloorMapMapPresenter presenter) /*-{
        return presenter.@stroom.floormap.client.presenter.FloorMapMapPresenter::factsCadenceTimer;
    }-*/;
}
