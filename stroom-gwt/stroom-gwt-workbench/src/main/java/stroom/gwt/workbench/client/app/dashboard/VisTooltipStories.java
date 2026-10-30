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
import stroom.gwt.workbench.framework.client.play.EventInit;
import stroom.gwt.workbench.framework.client.play.Play;
import stroom.gwt.workbench.framework.client.play.Query;
import stroom.gwt.workbench.framework.client.play.TextMatch;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;

import com.google.gwt.dom.client.Element;

/// The `App/Dashboard/VisTooltip` stories: a visualisation's hover tooltip, which Stroom's
/// `VisTooltip` shows on the page rather than in the visualisation's frame, so that it isn't cut
/// off at the frame's edges. The visualisation asks for it through `stroom.showTooltip` in
/// `vis.js` (handled by `MessageSupport`).
///
/// The visualisation is the workbench's test visualisation, `vis-tooltip`: an `index.html` asset
/// (`src/main/webapp/assets/vis-tooltip/`), which Stroom's `VisController` loads into the frame as
/// `/assets/vis-tooltip/index.html` when the visualisation has one. Its bars are against the edges
/// of the frame, each named as an image, and each asks for its tooltip on the side facing out of
/// the frame, both when the mouse is over it and when it has the focus. It sits between the query
/// and the table, so that there is room for a tooltip either side of it.
///
/// Stroom only loads a dashboard's visualisation when its first result arrives, so each play runs
/// the query first.
///
/// | Stroom endpoint | Used for |
/// |---|---|
/// | A dashboard search (`DashboardSupport.SEARCH`) | the table's rows and the chart's (empty) data |
/// | `GET /visualisation/v1/vis-tooltip` | the visualisation |
/// | `GET /visualisationAssets/saveAs/vis-tooltip` | whether it has an `index.html` asset (`indexAssetExists`) |
public final class VisTooltipStories {

    private static final String TITLE = "App/Dashboard/VisTooltip";

    private static final String VIS_UUID = "vis-tooltip";

    private static final String VISUALISATION = "{\"type\": \"Visualisation\", \"uuid\": \"" + VIS_UUID + "\", "
                                                + "\"name\": \"Tooltip Test\", "
                                                + "\"functionName\": \"workbench.TooltipTestVis\", "
                                                + "\"settings\": \"{}\"}";

    // A Query, the visualisation and a Table side by side
    private static final String DASHBOARD = DashboardDocs.doc("dash-vis-tooltip", "Vis Tooltip", null,
            "\"designMode\": false",
            DashboardDocs.split(0,
                    DashboardDocs.sized(DashboardDocs.tabs(0, "q1"), 250, 100),
                    DashboardDocs.sized(DashboardDocs.tabs(0, "v1"), 400, 100),
                    DashboardDocs.sized(DashboardDocs.tabs(0, "t1"), 250, 100)),
            DashboardDocs.query("q1", "Q", ""),
            DashboardDocs.table("t1", "T", "q1", DashboardDocs.NAME_FIELD, ""),
            DashboardDocs.component("vis", "v1", "Chart", "\"tableId\": \"t1\", \"visualisation\": "
                                                          + "{\"type\": \"Visualisation\", \"uuid\": \"" + VIS_UUID
                                                          + "\", \"name\": \"Tooltip Test\"}, \"json\": \"{}\""));

    // The search's reply: the table's rows, and the chart's data, which the test visualisation
    // doesn't use
    private static final String SEARCH_RESPONSE = QueryFixtures.response(true,
            QueryFixtures.tableResult("t1", "[" + DashboardDocs.NAME_FIELD + "]",
                    "[{\"values\": [\"alpha\"], \"depth\": 0}]", 1),
            "{\"type\": \"vis\", \"componentId\": \"v1\", \"jsonData\": \"{}\", \"dataPoints\": 0}");

    private static final String TOOLTIP = ".vis-tooltip";

    private VisTooltipStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        registry.component(TITLE, VisTooltipStories.class)
                .layout(StoryLayout.FULLSCREEN)
                // A bar at the frame's right edge has its tooltip beside it on the page, past the
                // frame's edge, where a tooltip in the frame would be cut off. It goes when the
                // mouse leaves the bar
                .story("ShownOutsideFrame", DashboardSupport.story(DASHBOARD, VisTooltipStories::routes))
                .withPlay(play -> {
                    final Query frame = visFrame(play);
                    final Query right = bar(play, frame, "Right");
                    play.hover(right);
                    final Query tooltip = play.screen().querySelector(TOOLTIP);
                    play.waitFor(() -> play.expect(tooltip).toBeVisible());
                    play.expect(tooltip).toHaveTextContent(TextMatch.containing("Right"));
                    play.expect(tooltip).toHaveTextContent(TextMatch.containing("20"));
                    // A popup as Stroom's others are, so see-through as they are with the user's
                    // Enable Transparency preference (on in this page's classes): the popup colour
                    // at 80%, the page blurred behind it
                    play.expect(tooltip).toHaveClass("popup");
                    play.expect(tooltip.computedStyle("background-color")).toMatch(", 0.8)");
                    play.expect(tooltip.computedStyle("backdrop-filter")).toMatch("blur(20px)");
                    // East of the bar, so past the right of the frame
                    play.expect("the tooltip's left less the frame's right",
                                    () -> side(tooltip, "left") - side(frame, "right"))
                            .toBeGreaterThanOrEqual(0);
                    // Within the window
                    play.expect("the tooltip's right", () -> side(tooltip, "right"))
                            .toBeLessThanOrEqual(windowWidth());
                    // Visual only, as screen readers hear the bar's name
                    play.expect(tooltip).toHaveAttribute("aria-hidden", "true");
                    play.unhover(right);
                    play.waitFor(() -> play.expect(tooltip).not().toBeVisible());
                    DashboardPlays.expectNoProblems(play);
                })
                // A bar with the keyboard focus shows its tooltip too, and hides it when the focus
                // leaves
                .story("ShownOnFocus", DashboardSupport.story(DASHBOARD, VisTooltipStories::routes))
                .withPlay(play -> {
                    final Query frame = visFrame(play);
                    final Query left = bar(play, frame, "Left");
                    play.run("focus the Left bar", () -> left.element().get().focus());
                    final Query tooltip = play.screen().querySelector(TOOLTIP);
                    play.waitFor(() -> play.expect(tooltip).toBeVisible());
                    play.expect(tooltip).toHaveTextContent(TextMatch.containing("Left"));
                    // West of the bar, so past the left of the frame
                    play.expect("the frame's left less the tooltip's right",
                                    () -> side(frame, "left") - side(tooltip, "right"))
                            .toBeGreaterThanOrEqual(0);
                    play.run("blur the Left bar", () -> left.element().get().blur());
                    play.waitFor(() -> play.expect(tooltip).not().toBeVisible());
                    DashboardPlays.expectNoProblems(play);
                })
                // Escape in the frame hides the tooltip, as does a mouse press on the page
                // (WCAG 1.4.13, Content on Hover or Focus: dismissible)
                .story("HiddenByEscapeAndMousePress", DashboardSupport.story(DASHBOARD, VisTooltipStories::routes))
                .withPlay(play -> {
                    final Query frame = visFrame(play);
                    final Query right = bar(play, frame, "Right");
                    final Query tooltip = play.screen().querySelector(TOOLTIP);
                    play.hover(right);
                    play.waitFor(() -> play.expect(tooltip).toBeVisible());
                    // vis.js hears Escape in the frame, where Stroom's own listeners can't
                    play.fireEvent().keyDown(frame.frameBody(), EventInit.create().key("Escape"));
                    play.waitFor(() -> play.expect(tooltip).not().toBeVisible());
                    play.hover(right);
                    play.waitFor(() -> play.expect(tooltip).toBeVisible());
                    play.fireEvent().mouseDown(play.body());
                    play.waitFor(() -> play.expect(tooltip).not().toBeVisible());
                    DashboardPlays.expectNoProblems(play);
                })
                // A tooltip's markup is reduced to text and layout before it is shown: the image
                // and script in the Hostile bar's tooltip are removed, and neither runs
                .story("MarkupRemoved", DashboardSupport.story(DASHBOARD, VisTooltipStories::routes))
                .withPlay(play -> {
                    final Query frame = visFrame(play);
                    play.hover(bar(play, frame, "Hostile"));
                    final Query tooltip = play.screen().querySelector(TOOLTIP);
                    play.waitFor(() -> play.expect(tooltip).toBeVisible());
                    play.expect(tooltip).toHaveTextContent(TextMatch.containing("Hostile"));
                    // The rows of keys and values are kept
                    play.expect(play.screen().within(tooltip).querySelectorAll(".vis-tooltip__row")).toHaveLength(2);
                    // Laid out by the tooltip's own styles alone (not, say, the page's Bootstrap),
                    // so a short value doesn't wrap: the bar's name is no taller than its key
                    final Query key = play.screen().within(tooltip).querySelector(".vis-tooltip__key");
                    final Query name = play.screen().within(tooltip).querySelector(".vis-tooltip__value");
                    play.expect(name).toHaveTextContent(TextMatch.exact("Hostile"));
                    play.expect("the bar name's height less its key's",
                                    () -> side(name, "height") - side(key, "height"))
                            .toBeLessThanOrEqual(0);
                    play.expect(play.screen().within(tooltip).querySelectorAll("img, script")).toHaveLength(0);
                    // Give an image's error handler time to have run, had the image been kept
                    play.sleep(200);
                    play.expect("whether the tooltip's markup ran", VisTooltipStories::markupRan).toBe(false);
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
        DashboardPlays.runQuery(play, 0);
        final Query frame = play.screen().querySelector("iframe.vis-frame__frame");
        play.waitFor(() -> play.expect(frame).toBeVisible());
        return frame;
    }

    // A bar of the test visualisation, waiting for the visualisation to have drawn it
    private static Query bar(final Play play, final Query frame, final String name) {
        final Play inFrame = play.withinFrame(frame);
        final Query bar = inFrame.getByRole("img", name);
        play.waitFor(5000, () -> play.expect(bar).toBeInTheDocument());
        return bar;
    }

    // A side of an element's bounds in the window, e.g. "left"
    private static double side(final Query query, final String side) {
        return boundingClientValue(query.element().get(), side);
    }

    private static native double boundingClientValue(Element element, String side) /*-{
        return element.getBoundingClientRect()[side];
    }-*/;

    private static native double windowWidth() /*-{
        return $doc.documentElement.clientWidth;
    }-*/;

    // Whether the Hostile bar's markup ran: its image's error handler and its script set this on
    // the page
    private static native boolean markupRan() /*-{
        return $wnd.visTooltipTestXss === true;
    }-*/;
}
