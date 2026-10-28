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

import stroom.gwt.workbench.client.app.dashboard.DashboardDocs;
import stroom.gwt.workbench.client.app.dashboard.DashboardSupport;
import stroom.gwt.workbench.client.app.query.DocumentEditors;
import stroom.gwt.workbench.client.app.query.QueryFixtures;
import stroom.gwt.workbench.client.app.rest.JsonValues;
import stroom.gwt.workbench.client.app.rest.RequestMatcher;
import stroom.gwt.workbench.client.app.rest.RestFixtures;
import stroom.gwt.workbench.client.app.rest.RestReply;
import stroom.gwt.workbench.framework.client.play.EventInit;
import stroom.gwt.workbench.framework.client.play.Play;
import stroom.gwt.workbench.framework.client.play.Query;
import stroom.gwt.workbench.framework.client.story.StoryRenderer;
import stroom.widget.text.client.Html;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/// What the `App/Visualisations` stories share: a dashboard showing one of the visualisations of
/// the visualisations content pack (`stroom-visualisations-dev`), loaded as Stroom loads it, with
/// a story's test data.
///
/// The workbench server serves the content pack's visualisations and scripts (its `StroomContent`,
/// given by the build's `workbenchVisualisationsDir`): the scripts as Stroom's script servlet does,
/// at `/ui/script`, which the visualisation's frame (`VisFrame`, Stroom's real `vis.html`) loads,
/// and the visualisations' documents, which each story reads as it renders. A story fails to
/// render, saying why, if the visualisations aren't there; `workbenchTest` skips the stories
/// (tagged [#TAG]) then.
///
/// A story's dashboard has the visualisation, and beside it a Table of the test data, so that what
/// the visualisation shows can be checked against the rows it was given. The Query runs when the
/// dashboard opens. [VisData] makes the visualisation's data from the table and the story's
/// settings, as Stroom's server does.
///
/// | Stroom endpoint | Used for |
/// |---|---|
/// | A dashboard search (`DashboardSupport.SEARCH`) | the table's rows and the visualisation's data |
/// | `GET /visualisation/v1/{uuid}` | the visualisation |
/// | `GET /visualisationAssets/saveAs/{uuid}` | whether it has an `index.html` asset (as the content says) |
/// | `POST /script/v1/fetchLinkedScripts` | the scripts it needs, dependencies first |
/// | `POST /explorer/v2/decorate`, `POST /explorer/v2/getFromDocRef` | the settings' visualisation picker |
public final class VisStories {

    /// The tag of the stories, which `workbenchTest` skips when there are no visualisations.
    public static final String TAG = "visualisations";

    /// The title prefix of the stories.
    public static final String TITLE = "App/Visualisations/";

    /// The selector of the visualisation's frame.
    public static final String FRAME = "iframe.vis-frame__frame";

    // How long a visualisation may take to load its scripts and draw. The first story to load
    // the larger scripts (e.g. D3) is the slowest
    private static final int DRAW_TIMEOUT_MILLIS = 15_000;
    // Long enough for the visualisations' transitions (D3's default is 250ms, some take longer)
    private static final int TRANSITION_MILLIS = 1_200;

    private VisStories() {
        // Static utility
    }

    /// @param keysAndValues Pairs of a setting's key and its value, e.g. `"x", "Hour"`.
    /// @return The settings, in order.
    /// @throws IllegalArgumentException If there is a key without a value.
    public static Map<String, String> settings(final String... keysAndValues) {
        if (keysAndValues.length % 2 != 0) {
            throw new IllegalArgumentException("Settings need a value for each key");
        }
        final Map<String, String> settings = new LinkedHashMap<>();
        for (int i = 0; i < keysAndValues.length; i += 2) {
            settings.put(keysAndValues[i], keysAndValues[i + 1]);
        }
        return settings;
    }

    /// A story's renderer: a dashboard showing a visualisation of a table.
    ///
    /// @param visName  The visualisation's name in the content pack, e.g. `BarChart`.
    /// @param settings The dashboard's settings for the visualisation, e.g. which column is its
    ///                 `x` field. Any it doesn't set are the defaults of its controls.
    /// @param table    The test data.
    /// @return The renderer.
    public static StoryRenderer story(final String visName,
                                      final Map<String, String> settings,
                                      final VisTable table) {
        return story(visName, List.of(settings), table);
    }

    /// A story's renderer: a dashboard showing several of a visualisation side by side, each with
    /// its own settings, of the same table, e.g. to check that they colour a value alike. The first
    /// is named as the visualisation (e.g. `BarChart`), the others `BarChart 2` and so on.
    ///
    /// @param visName        The visualisation's name in the content pack, e.g. `BarChart`.
    /// @param settingsOfEach The dashboard's settings for each of the visualisations.
    /// @param table          The test data.
    /// @return The renderer.
    public static StoryRenderer story(final String visName,
                                      final List<Map<String, String>> settingsOfEach,
                                      final VisTable table) {
        return story(visName, settingsOfEach, table, DashboardSupport.DARK_PREFERENCES);
    }

    /// A story's renderer: a dashboard showing a visualisation of a table, for a user with their
    /// own preferences, e.g. their time zone and date and time format, which Stroom gives the
    /// visualisation (`stroom.time`).
    ///
    /// @param visName         The visualisation's name in the content pack, e.g. `TimeSeries`.
    /// @param settings        The dashboard's settings for the visualisation.
    /// @param table           The test data.
    /// @param userPreferences The user's preferences, as `GET /preferences/v1` gives them, e.g.
    ///                        from [#preferencesInZone].
    /// @return The renderer.
    public static StoryRenderer story(final String visName,
                                      final Map<String, String> settings,
                                      final VisTable table,
                                      final String userPreferences) {
        return story(visName, List.of(settings), table, userPreferences);
    }

    /// The dark theme's user preferences (as [DashboardSupport#DARK_PREFERENCES]) with a time zone
    /// and date and time format of their own.
    ///
    /// @param timeZoneJson The user's time zone, as Stroom's `UserTimeZone` JSON, e.g.
    ///                     `{"use": "ID", "id": "Europe/London"}`.
    /// @param pattern      The user's date and time format, a Java pattern, e.g.
    ///                     `dd/MM/yyyy hh:mm:ss a`.
    /// @return The preferences' JSON.
    public static String preferencesInZone(final String timeZoneJson, final String pattern) {
        return DashboardSupport.DARK_PREFERENCES
                .replace("\"timeZone\": {\"use\": \"UTC\"}", "\"timeZone\": " + timeZoneJson)
                .replace("\"yyyy-MM-dd'T'HH:mm:ss.SSSXX\"", "\"" + pattern + "\"");
    }

    // A dashboard of each of the visualisations, for a user with the given preferences
    private static StoryRenderer story(final String visName,
                                       final List<Map<String, String>> settingsOfEach,
                                       final VisTable table,
                                       final String userPreferences) {
        return context -> {
            final VisCatalogue catalogue;
            try {
                catalogue = VisCatalogue.parse(fetchCatalogue());
            } catch (final RuntimeException e) {
                return missing("The workbench server's visualisations couldn't be read: " + e.getMessage());
            }
            // e.g. a stray empty .meta in the content pack, which Stroom would fail to import
            for (final String problem : catalogue.getProblems()) {
                warn("Skipped a visualisations content file: " + problem);
            }
            if (!catalogue.has(visName)) {
                return missing(catalogue.getNames().isEmpty()
                        ? "The workbench server has no visualisations. Check out stroom-visualisations-dev "
                          + "beside this repository, or give its directory with "
                          + "-PworkbenchVisualisationsDir, and restart the server."
                        : "There is no visualisation " + visName + ", only " + catalogue.getNames());
            }
            final String uuid = catalogue.uuid(visName);
            final String docJson = dashboard(visName, uuid, settingsOfEach, table);
            final List<String> results = new ArrayList<>();
            results.add(QueryFixtures.tableResult("t1", "[" + table.columnsJson() + "]", table.rowsJson(),
                    table.getRows().size()));
            for (int i = 0; i < settingsOfEach.size(); i++) {
                final String data = VisData.create(catalogue.settings(visName), settingsOfEach.get(i), table);
                results.add("{\"type\": \"vis\", \"componentId\": \"v" + (i + 1) + "\", \"jsonData\": "
                            + JsonValues.toJson(data) + ", \"dataPoints\": " + table.getRows().size() + "}");
            }
            final String searchResponse = QueryFixtures.response(true, results.toArray(new String[0]));
            // The settings' visualisation picker checks that its document exists
            final RestFixtures fixtures = DashboardSupport.fixtures(docJson, routes -> DocumentEditors.decorated(routes
                            .route(DashboardSupport.SEARCH, RestReply.json(searchResponse))
                            .get("/visualisation/v1/" + uuid, RestReply.json(catalogue.visualisationDocJson(visName)))
                            .get("/visualisationAssets/saveAs/" + uuid,
                                    RestReply.json(String.valueOf(catalogue.hasIndexAsset(visName))))
                            .route(RequestMatcher.post("/script/v1/fetchLinkedScripts"),
                                    request -> RestReply.json(catalogue.linkedScriptsReply(request.getBody()))),
                    JsonValues.toJson(docRef(visName, uuid))))
                    .build();
            return DashboardSupport.render(context, fixtures, dashboardUuid(visName), visName, options ->
                    options.harness(builder -> builder.startup(startup -> startup.userPreferences(userPreferences))));
        };
    }

    /// Waits for the visualisation to have drawn, and its transitions to have finished.
    ///
    /// @param play     The play.
    /// @param selector What the visualisation draws, e.g. `rect` for a bar chart's bars.
    /// @return What it drew, within its frame.
    public static Query drawn(final Play play, final String selector) {
        final Query frame = frame(play);
        final Query drawn = play.withinFrame(frame).querySelectorAll(selector);
        play.waitFor(DRAW_TIMEOUT_MILLIS, () -> play.expect("how many '" + selector + "' it drew",
                () -> drawn.count().get()).toBeGreaterThan(0));
        play.sleep(TRANSITION_MILLIS);
        expectToolbarAbove(play, frame);
        return drawn;
    }

    // The toolbar row (its pause button and any buttons it asked for) is above the visualisation,
    // not over what it draws
    private static void expectToolbarAbove(final Play play, final Query frame) {
        final Query toolbarRow = play.screen().querySelector(".vis-toolbar-row");
        play.expect("the toolbar row's bottom less the visualisation's top",
                () -> toolbarRow.bottom().get() - frame.top().get()).toBeLessThanOrEqual(0);
    }

    /// Waits for the visualisation to have drawn a number of things, and its transitions to have
    /// finished.
    ///
    /// @param play     The play.
    /// @param selector What the visualisation draws, e.g. `rect.bar`.
    /// @param count    How many it should draw.
    /// @return What it drew, within its frame.
    public static Query drawn(final Play play, final String selector, final int count) {
        final Query frame = frame(play);
        final Query drawn = play.withinFrame(frame).querySelectorAll(selector);
        play.waitFor(DRAW_TIMEOUT_MILLIS, () -> play.expect(drawn).toHaveLength(count));
        play.sleep(TRANSITION_MILLIS);
        play.expect(drawn).toHaveLength(count);
        expectToolbarAbove(play, frame);
        return drawn;
    }

    /// Waits for a visualisation that is a grid of charts (its `gridSeries` set) to have drawn a
    /// number of cells, each with something drawn in it, and its transitions to have finished.
    ///
    /// @param play     The play.
    /// @param cells    How many cells it should draw, one for each grid series.
    /// @param selector What each cell's chart draws, e.g. `path.vis-coloured-element`.
    public static void drawnGrid(final Play play, final int cells, final String selector) {
        drawn(play, selector);
        play.expect(play.withinFrame(frame(play)).querySelectorAll("rect.vis-cellVisualisation-border"))
                .toHaveLength(cells);
    }

    /// The visualisation's frame, once the dashboard has opened.
    ///
    /// @param play The play.
    /// @return The frame.
    public static Query frame(final Play play) {
        final Query frame = play.screen().querySelector(FRAME);
        play.waitFor(DRAW_TIMEOUT_MILLIS, () -> play.expect(frame).toBeVisible());
        return frame;
    }

    /// The visualisation's one tab stop for its data points (a roving tabindex, see `stroom.points`
    /// in Stroom's `vis.js`), once it has drawn. A zooming X axis (Scatter, LineChart) is a tab stop
    /// of its own, which this leaves out.
    ///
    /// @param play The play.
    /// @return The point with `tabindex="0"`.
    public static Query tabStop(final Play play) {
        final Query tabStops = play.withinFrame(frame(play)).querySelectorAll("[tabindex='0']:not(.vis-axis-zoom)");
        play.waitFor(DRAW_TIMEOUT_MILLIS, () -> play.expect(tabStops).toHaveLength(1));
        return tabStops.first();
    }

    /// Gives the visualisation's tab stop the focus, as Tab from the page before it would.
    ///
    /// @param play The play.
    /// @return The tab stop.
    public static Query focusTabStop(final Play play) {
        final Query tabStop = tabStop(play);
        play.run("focus the visualisation's tab stop", () -> tabStop.element().get().focus());
        play.expect(tabStop).toHaveFocus();
        return tabStop;
    }

    /// @param play The play.
    /// @return The data point in the visualisation's frame that has the focus.
    public static Query focusedPoint(final Play play) {
        return play.withinFrame(frame(play)).querySelector(":focus");
    }

    /// Presses keys in the visualisation's frame, on whatever has the focus there.
    ///
    /// @param play The play.
    /// @param keys The keys, as `userEvent.keyboard` takes them, e.g. `{ArrowRight}`.
    public static void press(final Play play, final String keys) {
        play.withinFrame(frame(play)).keyboard(keys);
    }

    /// Checks that a visualisation that pans and zooms (`shared/panZoom.js`) does so from the keyboard
    /// as well as the mouse: with the focus on its tab stop, + zooms in and 0 fits it again, and a
    /// screen reader is told the keys.
    ///
    /// @param play          The play.
    /// @param chartSelector The visualisation's svg, e.g. `svg.vis-tree`.
    /// @param zoomedSelector What it pans and zooms, e.g. `svg.vis-tree > g`.
    public static void expectKeyboardZoom(final Play play, final String chartSelector, final String zoomedSelector) {
        final Play inFrame = play.withinFrame(frame(play));
        play.expect(inFrame.querySelector(chartSelector).attribute("aria-description"))
                .toMatch("Plus and minus zoom in and out");
        final Query zoomed = inFrame.querySelector(zoomedSelector);
        focusTabStop(play);
        // The fitted scale, noted when the steps run
        final double[] fitted = new double[1];
        play.run("note the fitted scale", () -> fitted[0] = scaleOf(zoomed.attribute("transform").get()));
        press(play, "+");
        play.waitFor(() -> play.expect("the scale after + over the fitted scale",
                () -> scaleOf(zoomed.attribute("transform").get()) / fitted[0]).toBeGreaterThan(1.2));
        press(play, "0");
        play.waitFor(() -> play.expect("the scale after 0, less the fitted scale",
                () -> Math.abs(scaleOf(zoomed.attribute("transform").get()) - fitted[0])).toBeLessThan(0.01));
    }

    /// The scale of a d3 zoom transform, e.g. 1.5 for `translate(10,20) scale(1.5)`.
    ///
    /// @param transform The transform attribute, or null.
    /// @return Its scale, or 1 if it has none.
    public static double scaleOf(final String transform) {
        if (transform == null || !transform.contains("scale(")) {
            return 1;
        }
        final int start = transform.indexOf("scale(") + "scale(".length();
        return Double.parseDouble(transform.substring(start, transform.indexOf(')', start)));
    }

    /// @param play The play.
    /// @return Stroom's tooltip for the visualisation, which it shows on the page (`VisTooltip`).
    public static Query tooltip(final Play play) {
        return play.screen().querySelector(".vis-tooltip");
    }

    /// Checks that the story had no alerts and made no request without a fixture.
    ///
    /// @param play The play.
    public static void expectNoProblems(final Play play) {
        DashboardSupport.expectNoProblems(play);
    }

    // The visualisation, and beside it the query and the table of the test data, with the table
    // shown. The query runs when the dashboard opens
    private static String dashboard(final String visName,
                                    final String uuid,
                                    final List<Map<String, String>> settingsOfEach,
                                    final VisTable table) {
        final String visualisation = JsonValues.toJson(docRef(visName, uuid));
        // The visualisations share the width one would have
        final int width = 700 / settingsOfEach.size();
        final List<String> panes = new ArrayList<>();
        final List<String> components = new ArrayList<>();
        components.add(DashboardDocs.query("q1", "Query", ", \"automate\": {\"open\": true}"));
        components.add(DashboardDocs.table("t1", "Test Data", "q1", table.columnsJson(), ""));
        for (int i = 0; i < settingsOfEach.size(); i++) {
            final String id = "v" + (i + 1);
            panes.add(DashboardDocs.sized(DashboardDocs.tabs(0, id), width, 100));
            components.add(DashboardDocs.component("vis", id, i == 0
                            ? visName
                            : visName + " " + (i + 1),
                    "\"tableId\": \"t1\", \"visualisation\": " + visualisation + ", \"json\": "
                    + JsonValues.toJson(JsonValues.toJson(settingsOfEach.get(i)))));
        }
        panes.add(DashboardDocs.sized(DashboardDocs.tabs(1, "q1", "t1"), 400, 100));
        return DashboardDocs.doc(dashboardUuid(visName), visName, null, "\"designMode\": false",
                DashboardDocs.split(0, panes.toArray(new String[0])),
                components.toArray(new String[0]));
    }

    // The test dashboard's id, which is in its URL: from the visualisation's name, without spaces
    // (e.g. 'dash-Force-3D')
    private static String dashboardUuid(final String visName) {
        return "dash-" + visName.replace(' ', '-');
    }

    private static Map<String, Object> docRef(final String visName, final String uuid) {
        final Map<String, Object> docRef = new LinkedHashMap<>();
        docRef.put("type", "Visualisation");
        docRef.put("uuid", uuid);
        docRef.put("name", visName);
        return docRef;
    }

    /// Starts recording what the visualisation tells Stroom it has selected (each selection message
    /// vis.js's `stroom.select` sends Stroom's page), for [#expectSelection]. Called first in a play,
    /// before the visualisation has drawn, it also records what it tells Stroom as it first draws
    /// (e.g. a time series' window).
    ///
    /// @param play The play.
    public static void recordSelections(final Play play) {
        play.run("record the visualisation's selections", VisStories::nativeRecordSelections);
    }

    /// Expects what the visualisation last told Stroom it has selected, as recorded since
    /// [#recordSelections], to be some JSON, e.g. `[{"key":"a","name":"a"}]`, or `[]` for nothing.
    /// The items are in the order selected, and each item's fields in the order the visualisation
    /// gave them.
    ///
    /// @param play The play.
    /// @param json The selection's JSON.
    public static void expectSelection(final Play play, final String json) {
        play.waitFor(() -> play.expect("the selection Stroom was last told of",
                VisStories::nativeLastSelection).toBe(json));
    }

    /// Checks what the visualisation tells Stroom when a point is selected: Ctrl+Click on it (which
    /// only selects, rather than e.g. opening a branch) tells Stroom of it, as some JSON, and its
    /// name for screen readers says it is selected; Ctrl+Click on it again tells Stroom of an empty
    /// selection, and its name no longer says so.
    ///
    /// @param play  The play.
    /// @param point The point, in the visualisation's frame.
    /// @param json  What Stroom is told of it, e.g. `[{"key":"a","name":"a"}]`.
    public static void expectSelectionOf(final Play play, final Query point, final String json) {
        recordSelections(play);
        play.fireEvent().click(point, EventInit.create().ctrlKey());
        expectSelection(play, json);
        play.waitFor(() -> play.expect(point.attribute("aria-label")).toMatch(", selected"));
        play.fireEvent().click(point, EventInit.create().ctrlKey());
        expectSelection(play, "[]");
        play.waitFor(() -> play.expect(point.attribute("aria-label")).not().toMatch(", selected"));
    }

    // Listens for the visualisation's selection messages where Stroom does (PostMessage listens on
    // the window its GWT code runs in, which vis.js sends its messages to), keeping each
    // selection's JSON, from now on
    private static native void nativeRecordSelections() /*-{
        window.__workbenchSelections = [];
        if (!window.__workbenchSelectionListener) {
            window.__workbenchSelectionListener = function (event) {
                var message;
                try {
                    message = typeof event.data === "string" ? JSON.parse(event.data) : event.data;
                } catch (e) {
                    return;
                }
                if (message && message.functionName === "select") {
                    window.__workbenchSelections.push(JSON.stringify(message.selection));
                }
            };
            window.addEventListener("message", window.__workbenchSelectionListener);
        }
    }-*/;

    // The JSON of the last selection recorded, or null if none
    private static native String nativeLastSelection() /*-{
        var selections = window.__workbenchSelections;
        return selections && selections.length > 0 ? selections[selections.length - 1] : null;
    }-*/;

    // Says why the story can't be shown
    private static Html missing(final String message) {
        final Html html = new Html();
        html.setText(message);
        html.getElement().getStyle().setProperty("padding", "16px");
        return html;
    }

    private static native void warn(String message) /*-{
        $wnd.console.warn(message);
    }-*/;

    // The catalogue's JSON. Fetched synchronously, so that the story renders at once, as every
    // story does, and fresh for each rendering, so that changes to the content pack show
    private static native String fetchCatalogue() /*-{
        var xhr = new XMLHttpRequest();
        xhr.open("GET", "/__workbench/content/visualisations", false);
        xhr.send();
        if (xhr.status !== 200) {
            throw new Error("GET /__workbench/content/visualisations gave " + xhr.status);
        }
        return xhr.responseText;
    }-*/;
}
