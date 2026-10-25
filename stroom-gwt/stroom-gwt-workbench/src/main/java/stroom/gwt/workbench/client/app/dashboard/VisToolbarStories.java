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

package stroom.gwt.workbench.client.app.dashboard;

import stroom.gwt.workbench.client.app.query.QueryFixtures;
import stroom.gwt.workbench.client.app.rest.RestFixtures;
import stroom.gwt.workbench.client.app.rest.RestReply;
import stroom.gwt.workbench.framework.client.play.Play;
import stroom.gwt.workbench.framework.client.play.Query;
import stroom.gwt.workbench.framework.client.play.TextMatch;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;

/// The `App/Dashboard/VisToolbar` stories: the toolbar row above a dashboard's visualisation
/// (`VisToolbarRow`), as a table has, with the buttons the visualisation asks for
/// (`stroom.setToolbarButtons` in `vis.js`) at its start and the pause and resume button at its end.
/// Being above the visualisation's frame, neither covers what it draws.
///
/// The visualisation is the workbench's test visualisation, `vis-toolbar`: an `index.html` asset
/// (`src/main/webapp/assets/vis-toolbar/`) that asks for the button 'Save as image' and the toggle
/// 'Show labels', and shows what it hears when they are pressed.
///
/// | Stroom endpoint | Used for |
/// |---|---|
/// | A dashboard search (`DashboardSupport.SEARCH`) | the table's rows and the chart's (empty) data |
/// | `GET /visualisation/v1/vis-toolbar` | the visualisation |
/// | `GET /visualisationAssets/saveAs/vis-toolbar` | whether it has an `index.html` asset (it has) |
public final class VisToolbarStories {

    private static final String TITLE = "App/Dashboard/VisToolbar";

    private static final String VIS_UUID = "vis-toolbar";

    private static final String VISUALISATION = "{\"type\": \"Visualisation\", \"uuid\": \"" + VIS_UUID + "\", "
                                                + "\"name\": \"Toolbar Test\", "
                                                + "\"functionName\": \"workbench.ToolbarTestVis\", "
                                                + "\"settings\": \"{}\"}";

    // The visualisation and, beside it, a Query and Table
    private static final String DASHBOARD = DashboardDocs.doc("dash-vis-toolbar", "Vis Toolbar", null,
            "\"designMode\": false",
            DashboardDocs.split(0,
                    DashboardDocs.sized(DashboardDocs.tabs(0, "v1"), 500, 100),
                    DashboardDocs.sized(DashboardDocs.tabs(1, "q1", "t1"), 300, 100)),
            DashboardDocs.query("q1", "Q", ", \"automate\": {\"open\": true}"),
            DashboardDocs.table("t1", "T", "q1", DashboardDocs.NAME_FIELD, ""),
            DashboardDocs.component("vis", "v1", "Chart", "\"tableId\": \"t1\", \"visualisation\": "
                                                          + "{\"type\": \"Visualisation\", \"uuid\": \"" + VIS_UUID
                                                          + "\", \"name\": \"Toolbar Test\"}, \"json\": \"{}\""));

    private static final String SEARCH_RESPONSE = QueryFixtures.response(true,
            QueryFixtures.tableResult("t1", "[" + DashboardDocs.NAME_FIELD + "]",
                    "[{\"values\": [\"alpha\"], \"depth\": 0}]", 1),
            "{\"type\": \"vis\", \"componentId\": \"v1\", \"jsonData\": \"{}\", \"dataPoints\": 0}");

    private VisToolbarStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        registry.component(TITLE, VisToolbarStories.class)
                .layout(StoryLayout.FULLSCREEN)
                // The pause button is in a toolbar row above the visualisation, as a table's is,
                // not over the visualisation's frame
                .story("ToolbarAboveVisualisation", DashboardSupport.story(DASHBOARD, VisToolbarStories::routes))
                .withPlay(play -> {
                    final Query frame = visFrame(play);
                    final Query pause = play.screen().querySelector(".vis-toolbar-row");
                    play.expect(pause).toBeVisible();
                    play.expect("the toolbar row's bottom less the frame's top",
                            () -> pause.bottom().get() - frame.top().get()).toBeLessThanOrEqual(0);
                    DashboardPlays.expectNoProblems(play);
                })
                // The visualisation's buttons are Stroom's own toolbar buttons, named as it asked,
                // and it hears when they are pressed: a button's id, and a toggle's new state
                .story("VisualisationButtons", DashboardSupport.story(DASHBOARD, VisToolbarStories::routes))
                .withPlay(play -> {
                    final Query frame = visFrame(play);
                    final Play inFrame = play.withinFrame(frame);
                    final Query pressed = inFrame.querySelector(".pressed");
                    final Query actions = play.screen().getByRole("toolbar", "Visualisation actions");
                    final Query save = play.within(actions).getByRole("button", "Save as image");
                    final Query labels = play.within(actions).getByRole("button", "Show labels");
                    play.waitFor(() -> play.expect(save).toBeVisible());
                    play.expect(labels).toHaveAttribute("aria-pressed", "true");

                    play.click(save);
                    play.waitFor(() -> play.expect(pressed).toHaveTextContent("Pressed save"));

                    play.click(labels);
                    play.waitFor(() -> play.expect(pressed).toHaveTextContent("Pressed labels false"));
                    play.expect(inFrame.querySelector(".label")).not().toBeVisible();
                    // The visualisation told Stroom the toggle's new state, which kept the button
                    // (and the focus on it) rather than making it again
                    play.waitFor(() -> play.expect(labels).toHaveAttribute("aria-pressed", "false"));
                    play.expect(labels).toHaveFocus();

                    play.click(labels);
                    play.waitFor(() -> play.expect(pressed).toHaveTextContent(TextMatch.exact("Pressed labels true")));
                    play.expect(inFrame.querySelector(".label")).toBeVisible();
                    DashboardPlays.expectNoProblems(play);
                });
    }

    // The search, the visualisation and whether it has an index.html asset (it has)
    private static void routes(final RestFixtures.Builder routes) {
        routes.route(DashboardSupport.SEARCH, RestReply.json(SEARCH_RESPONSE))
                .get("/visualisation/v1/" + VIS_UUID, RestReply.json(VISUALISATION))
                .get("/visualisationAssets/saveAs/" + VIS_UUID, RestReply.json("true"));
    }

    // The visualisation's frame (VisFrame), once the dashboard has opened and its query has run
    private static Query visFrame(final Play play) {
        DashboardPlays.opened(play);
        final Query frame = play.screen().querySelector("iframe.vis-frame__frame");
        play.waitFor(5000, () -> play.expect(frame).toBeVisible());
        final Query box = play.withinFrame(frame).querySelector(".pressed");
        play.waitFor(5000, () -> play.expect(box).toBeInTheDocument());
        return frame;
    }
}
