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

import stroom.gwt.workbench.client.FrameDocuments;
import stroom.gwt.workbench.client.StroomThemeDecorator;
import stroom.gwt.workbench.client.a11y.ButtonRuns;
import stroom.gwt.workbench.client.app.ai.AiFixtures;
import stroom.gwt.workbench.client.app.ai.AskStroomAiChat;
import stroom.gwt.workbench.client.app.query.DocumentEditors;
import stroom.gwt.workbench.client.app.rest.RecordedRequest;
import stroom.gwt.workbench.client.app.rest.RequestMatcher;
import stroom.gwt.workbench.client.app.rest.RestFixtures;
import stroom.gwt.workbench.client.app.rest.RestReply;
import stroom.gwt.workbench.client.app.screen.ScreenHarness;
import stroom.gwt.workbench.client.app.screen.StroomDom;
import stroom.gwt.workbench.framework.client.play.Play;
import stroom.gwt.workbench.framework.client.play.Query;
import stroom.gwt.workbench.framework.client.play.Spy;
import stroom.gwt.workbench.framework.client.play.TextMatch;
import stroom.gwt.workbench.framework.client.play.Value;
import stroom.gwt.workbench.framework.client.play.ValueMatcher;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;
import stroom.pipeline.stepping.client.event.BeginPipelineSteppingEvent;
import stroom.security.shared.AppPermission;

import com.google.gwt.dom.client.Element;

import java.util.List;

/// The component stories of `App/Editors/DashboardEditor` (see `DashboardEditorStories`): the
/// settings, tables, visualisations, text and stored queries of a dashboard's components.
///
/// | Stroom endpoint | Used for |
/// |---|---|
/// | `POST /dashboard/v1/downloadSearchResults/{node}` | a table's download |
/// | `POST /dashboard/v1/columnValues/{node}` | a column's values filter |
/// | `GET /visualisation/v1/{uuid}` | a visualisation |
/// | `POST /script/v1/fetchLinkedScripts` | a visualisation's scripts |
/// | `POST /data/v1/fetch` | a Text component's source |
/// | `POST /storedQuery/v1/find`, `create`, `DELETE /storedQuery/v1/delete` | query history and favourites |
/// | `POST /ai/v1/...` (`AiFixtures.chatRoutes`; the chat is shown by `AskStroomAiChat`) | Ask Stroom AI |
public final class DashboardComponentStories {

    // A table result with stream and event ids
    private static final String STREAM_RESPONSE = DashboardSupport.tableResponse("t1",
            "[" + DashboardDocs.NAME_FIELD + ", " + DashboardDocs.field("f-stream", "StreamId") + ", "
            + DashboardDocs.field("f-event", "EventId") + "]",
            "[{\"values\": [\"alpha\", \"1001\", \"1\"], \"depth\": 0}, "
            + "{\"values\": [\"beta\", \"1002\", \"2\"], \"depth\": 0}]", 2);

    // The Name column's values
    private static final String COLUMN_VALUES = "{\"values\": [{\"value\": \"alpha\"}, {\"value\": \"beta\"}], "
                                                + "\"pageResponse\": {\"offset\": 0, \"length\": 2, \"total\": 2, "
                                                + "\"exact\": true}}";

    private static final RequestMatcher COLUMN_VALUES_REQUEST = RequestMatcher.post(
            "/dashboard/v1/columnValues/" + RequestMatcher.PATH_WILDCARD);

    // A visualisation with no settings of its own
    private static final String VISUALISATION = "{\"type\": \"Visualisation\", \"uuid\": \"vis-1\", \"name\": \"Bar\", "
                                                + "\"functionName\": \"stub\", \"settings\": \"{}\"}";

    // A visualisation whose settings have a tab of controls
    private static final String VISUALISATION_WITH_CONTROLS = "{\"type\": \"Visualisation\", \"uuid\": \"vis-1\", "
            + "\"name\": \"Bar\", \"functionName\": \"bar\", "
            + "\"settings\": \"{\\\"tabs\\\": [{\\\"name\\\": \\\"Axes\\\", "
            + "\\\"controls\\\": [{\\\"id\\\": \\\"x\\\", \\\"type\\\": \\\"field\\\", \\\"label\\\": \\\"X Axis\\\"}, "
            + "{\\\"id\\\": \\\"title\\\", \\\"type\\\": \\\"text\\\", \\\"label\\\": \\\"Title\\\", "
            + "\\\"defaultValue\\\": \\\"Chart\\\"}]}]}\"}";

    // A favourite saved by the stories' 'My Fav'
    private static final String MY_FAV = "{\"id\": 100, \"name\": \"My Fav\", \"favourite\": true, "
                                         + "\"componentId\": \"q1\", \"dashboardUuid\": \"dash-3\", \"query\": "
                                         + "{\"dataSource\": " + DashboardDocs.INDEX + "}}";

    // The query history: a query of 'Name = alpha'
    private static final String HISTORY = "{\"id\": 1, \"favourite\": false, \"componentId\": \"q1\", "
                                          + "\"dashboardUuid\": \"dash-3\", \"createTimeMs\": 0, \"query\": "
                                          + "{\"dataSource\": " + DashboardDocs.INDEX + ", \"expression\": "
                                          + DashboardDocs.operator(DashboardDocs.term("Name", "EQUALS", "alpha"))
                                          + "}}";

    // The description of the 'Query + Table' dashboard's table, as the chat's context
    private static final String TABLE_CONTEXT = "Dashboard 'Query + Table' -> Table 'The Table'";

    // A Query and a Table that shows its filter row
    private static final String FILTER_ROW_DASHBOARD = DashboardDocs.doc("dash-filter-row", "Query + Table", null,
            "\"designMode\": false",
            DashboardDocs.split(0, DashboardDocs.sized(DashboardDocs.tabs(0, "q1"), 300, 100),
                    DashboardDocs.sized(DashboardDocs.tabs(0, "t1"), 400, 100)),
            DashboardDocs.THE_QUERY,
            DashboardDocs.table("t1", "The Table", "q1", DashboardDocs.NAME_FIELD, ", \"showValueFilters\": true"));

    // The spy of the app's stepping event
    private static final String BEGIN_STEPPING = "beginStepping";

    // The Text component's Step button
    private static final String STEP = "Enter Stepping Mode";

    private DashboardComponentStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        registry.component(DashboardSupport.TITLE, DashboardComponentStories.class)
                .layout(StoryLayout.FULLSCREEN)
                // The table's Ask Stroom AI button opens the chat
                .story("TableAskAiButton", DashboardSupport.story(DashboardDocs.QUERY_TABLE_DASHBOARD,
                        AiFixtures::chatRoutes,
                        options -> options.setup((harness, injector) ->
                                AskStroomAiChat.register(harness, injector::getAskStroomAiController))))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    DashboardPlays.opened(play);
                    DashboardPlays.runQuery(play, 0);
                    play.waitFor(() -> play.expect(play.getByText("alpha")).toBeInTheDocument());
                    play.click(play.getByRole("button", "Ask Stroom AI"));
                    // The chat is Stroom's 'Ask Stroom AI' dialog, and its 'How can I help?'
                    // greeting is hidden once the table is attached as the chat's context, so the
                    // play checks the message box's placeholder, which is the same text
                    play.expect(screen.findByText("Ask Stroom AI", StroomDom.DIALOG_TITLE)).toBeInTheDocument();
                    play.waitFor(() -> play.expect(screen.getByPlaceholderText("How can I help?")).toBeVisible());
                    // The chat is titled with the table's context, which is sent to the server
                    play.waitFor(() -> play.expect(screen.getByText(TABLE_CONTEXT)).toBeInTheDocument());
                    play.waitFor(() -> play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(
                            RequestMatcher.post("/ai/v1/askStroomAi")
                                    .withJsonBodyContaining("{\"context\": {\"type\": \"dashboardTable\", "
                                                            + "\"description\": \"" + TABLE_CONTEXT + "\"}}")
                                    .toSpyMatcher()));
                    DashboardPlays.expectNoProblems(play);
                })
                // A visualisation of a table shows its visualisation's frame
                .story("VisLinkedToTable", DashboardSupport.story(DashboardDocs.QUERY_TABLE_VIS_DASHBOARD,
                        DashboardComponentStories::visRoutes))
                .withPlay(play -> {
                    DashboardPlays.opened(play);
                    for (final String tab : List.of("Q", "T", "Chart")) {
                        play.expect(DashboardPlays.tab(play, tab)).toBeInTheDocument();
                    }
                    final Query visFrame = play.screen().querySelector("iframe.vis-frame__frame");
                    play.waitFor(() -> play.expect(visFrame).toBeInTheDocument());
                    // The visualisation frame (VisFrame) is named after its component, as its tab is
                    play.expect(visFrame).toHaveAttribute("title", "Chart");
                    // Its page (ui/vis.html) says it is in English, for screen readers (gchq/stroom#5408)
                    play.waitFor(() -> play.expect(FrameDocuments.language(visFrame)).toBe("en"));
                    DashboardPlays.expectNoProblems(play);
                })
                // The splitter beside a visualisation is above its frame (which is on the page's body,
                // over the panel), so its grab area, handle and focus ring are not hidden by it
                .story("SplitterAboveVisualisation", DashboardSupport.story(DashboardDocs.QUERY_TABLE_VIS_SPLIT,
                        DashboardComponentStories::visRoutes))
                .withPlay(play -> {
                    DashboardPlays.opened(play);
                    final Query visFrame = play.screen().querySelector("iframe.vis-frame__frame");
                    play.waitFor(() -> play.expect(visFrame).toBeVisible());
                    final Query splitter = play.getByRole("separator", "Resize T and Chart");
                    play.run("focus the splitter", () -> splitter.element().get().focus());
                    // Just right of its line, over the visualisation: its grab area (and its focus
                    // ring) is on top there
                    play.expect("what is on top just right of the splitter's line",
                            () -> topmostBeside(splitter.element().get(), 3)).toBe("splitter");
                    play.expect("what is on top a little further right",
                            () -> topmostBeside(splitter.element().get(), 20)).toBe("visualisation");
                    DashboardPlays.expectNoProblems(play);
                })
                // A component's settings rename it
                .story("ComponentSettings", DashboardSupport.story(DashboardDocs.QUERY_TABLE_DASHBOARD, routes -> {
                }))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    DashboardPlays.opened(play);
                    // The settings are an item of the tab's menu, in a dialog captioned 'Settings'
                    DashboardPlays.openTabMenu(play, "The Table");
                    play.click(DashboardPlays.menuItem(screen, "Settings"));
                    final Play dialog = DashboardPlays.dialog(screen, "Settings");
                    play.expect(dialog.getByText("Query", "label")).toBeInTheDocument();
                    // The name box has no id; it is the Name form group's
                    final Query name = dialog.getByLabelText("Name");
                    play.clear(name);
                    play.type(name, "Renamed Table");
                    play.click(dialog.getByRole("button", StroomDom.button("OK")));
                    play.waitFor(() -> play.expect(DashboardPlays.tab(play, "Renamed Table")).toBeInTheDocument());
                    DashboardPlays.expectNoProblems(play);
                })
                // Maximum Results that aren't numbers are refused, even from another tab: OK shows
                // the Basic tab again, says why under the box and focuses it. Before, the parts that
                // weren't numbers were dropped
                .story("TableMaxResultsRefused", DashboardSupport.story(DashboardDocs.QUERY_TABLE_DASHBOARD,
                        routes -> {
                        }))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    DashboardPlays.opened(play);
                    DashboardPlays.openTabMenu(play, "The Table");
                    play.click(DashboardPlays.menuItem(screen, "Settings"));
                    final Play dialog = DashboardPlays.dialog(screen, "Settings");
                    final Query maxResults = dialog.getByLabelText("Maximum Results");
                    play.clear(maxResults);
                    play.type(maxResults, "100,lots");
                    play.click(dialog.getByRole("tab", "Conditional Formatting"));
                    play.click(dialog.getByRole("button", StroomDom.button("OK")));
                    play.waitFor(() -> play.expect(dialog.getByText(
                            "Must be whole numbers separated by commas, e.g. 1000000,100,10")).toBeInTheDocument());
                    play.expect(dialog.getByLabelText("Maximum Results")).toHaveFocus();
                    play.expect(screen.getByText("Settings", StroomDom.DIALOG_TITLE)).toBeInTheDocument();
                    DashboardPlays.expectNoProblems(play);
                })
                // The table's Download exports its results
                .story("TableDownload", DashboardSupport.story(DashboardDocs.QUERY_TABLE_DASHBOARD,
                        routes -> routes.route(RequestMatcher.post("/dashboard/v1/downloadSearchResults/"
                                                                   + RequestMatcher.PATH_WILDCARD),
                                RestReply.json("{\"resourceKey\": {\"name\": \"results\", \"key\": \"k1\"}, "
                                               + "\"messageList\": []}"))))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    DashboardPlays.opened(play);
                    DashboardPlays.runQuery(play, 0);
                    play.waitFor(() -> play.expect(play.getByText("alpha")).toBeInTheDocument());
                    play.waitFor(() -> play.expect(play.getByRole("button", "Download Table Results")).toBeEnabled());
                    play.click(play.getByRole("button", "Download Table Results"));
                    final Play dialog = DashboardPlays.dialog(screen, "Download Options");
                    play.click(dialog.getByRole("button", StroomDom.button("OK")));
                    // Stroom opens the resource with the location manager, recorded by the download
                    // spy
                    play.waitFor(() -> play.expect(play.spy(ScreenHarness.DOWNLOAD_SPY)).toHaveBeenCalledWith(
                            ValueMatcher.stringContaining("resourcestore/results")));
                    DashboardPlays.expectNoProblems(play);
                })
                // Sample Size is enabled by Sample Only, and the sample is in the request
                .story("TableDownloadSample", DashboardSupport.story(DashboardDocs.QUERY_TABLE_DASHBOARD,
                        routes -> routes.route(RequestMatcher.post("/dashboard/v1/downloadSearchResults/"
                                                                   + RequestMatcher.PATH_WILDCARD),
                                RestReply.json("{\"resourceKey\": {\"name\": \"results\", \"key\": \"k1\"}, "
                                               + "\"messageList\": []}"))))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    DashboardPlays.opened(play);
                    DashboardPlays.runQuery(play, 0);
                    play.waitFor(() -> play.expect(play.getByText("alpha")).toBeInTheDocument());
                    play.waitFor(() -> play.expect(play.getByRole("button", "Download Table Results")).toBeEnabled());
                    play.click(play.getByRole("button", "Download Table Results"));
                    final Play dialog = DashboardPlays.dialog(screen, "Download Options");
                    final Query size = dialog.getByLabelText("Sample Size (%)");
                    play.expect(size).toBeDisabled();
                    play.expect(size).toHaveValue("100");
                    play.click(dialog.getByRole("checkbox", "Sample Only"));
                    play.waitFor(() -> play.expect(size).toBeEnabled());
                    play.click(dialog.getByRole("button", StroomDom.button("OK")));
                    play.waitFor(() -> play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(
                            RequestMatcher.post("/dashboard/v1/downloadSearchResults/" + RequestMatcher.PATH_WILDCARD)
                                    .withJsonBodyContaining("{\"sample\": true, \"percent\": 100}")
                                    .toSpyMatcher()));
                    DashboardPlays.expectNoProblems(play);
                })
                // Rename starts with the column's name; Enter renames it
                .story("TableColumnRename", DashboardSupport.story(DashboardDocs.QUERY_TABLE_DASHBOARD, routes -> {
                }))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    DashboardPlays.opened(play);
                    DashboardPlays.runQuery(play, 0);
                    play.waitFor(() -> play.expect(play.getByText("alpha")).toBeInTheDocument());
                    StroomDom.clickHeader(play, DashboardPlays.header(play, "Name"));
                    play.click(screen.findByText("Rename", StroomDom.MENU_ITEM_TEXT));
                    final Play rename = DashboardPlays.dialog(screen, "Rename Field");
                    final Query name = rename.getByLabelText("Name");
                    play.expect(name).toHaveValue("Name");
                    play.clear(name);
                    play.type(name, "Label");
                    play.keyboard("{Enter}");
                    play.waitFor(() -> play.expect(DashboardPlays.header(play, "Label")).toBeInTheDocument());
                    play.expect(screen.queryByText("Rename Field", StroomDom.DIALOG_TITLE)).toBeNull();
                    DashboardPlays.expectNoProblems(play);
                })
                // Rename's Cancel keeps the column's name
                .story("TableColumnRenameCancel", DashboardSupport.story(DashboardDocs.QUERY_TABLE_DASHBOARD,
                        routes -> {
                        }))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    DashboardPlays.opened(play);
                    DashboardPlays.runQuery(play, 0);
                    play.waitFor(() -> play.expect(play.getByText("alpha")).toBeInTheDocument());
                    StroomDom.clickHeader(play, DashboardPlays.header(play, "Name"));
                    play.click(screen.findByText("Rename", StroomDom.MENU_ITEM_TEXT));
                    final Play rename = DashboardPlays.dialog(screen, "Rename Field");
                    final Query name = rename.getByLabelText("Name");
                    play.clear(name);
                    play.type(name, "Label");
                    play.click(rename.getByRole("button", StroomDom.button("Cancel")));
                    play.waitFor(() -> play.expect(screen.queryByText("Rename Field", StroomDom.DIALOG_TITLE))
                            .toBeNull());
                    play.expect(DashboardPlays.header(play, "Name")).toBeInTheDocument();
                    play.expect(play.getByRole("button", "Save")).toHaveAttribute("aria-disabled", "true");
                    DashboardPlays.expectNoProblems(play);
                })
                // The column menu has Rename, Expression, Group, Duplicate and Remove, in GWT's order
                .story("TableColumnMenu", DashboardSupport.story(DashboardDocs.QUERY_TABLE_DASHBOARD, routes -> {
                }))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    DashboardPlays.opened(play);
                    DashboardPlays.runQuery(play, 0);
                    play.waitFor(() -> play.expect(play.getByText("alpha")).toBeInTheDocument());
                    // Stroom opens a column's menu when its header is clicked
                    StroomDom.clickHeader(play, DashboardPlays.header(play, "Name"));
                    play.waitFor(() -> play.expect(screen.getByText("Rename", StroomDom.MENU_ITEM_TEXT))
                            .toBeInTheDocument());
                    for (final String item : List.of("Edit Expression", "Group", "Duplicate", "Remove")) {
                        play.expect(screen.getByText(item, StroomDom.MENU_ITEM_TEXT)).toBeInTheDocument();
                    }
                    final Value<List<String>> labels = play.capture("labels",
                            screen.querySelectorAll(StroomDom.MENU_ITEM_TEXT).textContents());
                    play.expect("the items' order", () -> inOrder(labels.get(), List.of("Rename", "Edit Expression",
                            "Sort", "Group", "Format", "Filter", "Filter Values", "Move Column to Start",
                            "Move Column to End",
                            "Duplicate", "Hide Column", "Remove"))).toBe(true);
                    play.expect("index of Remove after Hide Column",
                                    () -> labels.get().indexOf("Remove") > labels.get().indexOf("Hide Column"))
                            .toBe(true);
                    play.expect(labels).not().toContain("Column Filter");
                    play.click(screen.getByText("Rename", StroomDom.MENU_ITEM_TEXT));
                    play.expect(screen.findByText("Rename Field", StroomDom.DIALOG_TITLE)).toBeInTheDocument();
                    DashboardPlays.expectNoProblems(play);
                })
                // Add Column's button opens its list, and a second press closes it. A column chosen
                // after that is added once: the second press left the list's choice handler behind,
                // so every later choice added the column again
                .story("TableAddColumnAfterClosingTheList", DashboardSupport.story(DashboardDocs.QUERY_TABLE_DASHBOARD,
                        // The data source's fields, for the list's Data Source level: none
                        routes -> routes.route(RequestMatcher.post("/dataSource/v1/findFields"), RestReply.json(
                                "{\"values\": [], \"pageResponse\": {\"offset\": 0, \"length\": 0, "
                                + "\"total\": 0, \"exact\": true}}"))))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    DashboardPlays.opened(play);
                    DashboardPlays.runQuery(play, 0);
                    play.waitFor(() -> play.expect(play.getByText("alpha")).toBeInTheDocument());
                    final Query addColumn = play.getByRole("button", "Add Column");
                    // How many columns the table has, before any is added (a second column of the
                    // same kind is named apart, so the columns are counted)
                    final Query headers = play.querySelectorAll(".column-header__label");
                    final Value<Integer> before = play.capture("the columns", headers.count());
                    // The list's options, not the columns' headers, which are named the same
                    play.click(addColumn);
                    play.waitFor(() -> play.expect(screen.getByText("Counts")).toBeVisible());
                    play.click(addColumn);
                    play.waitFor(() -> play.expect(screen.queryByText("Counts")).toBeNull());

                    play.click(addColumn);
                    play.click(play.within(screen.querySelector(".selection-popup")).findByText("Counts"));
                    play.click(play.within(screen.querySelector(".selection-popup")).findByText("Count Groups"));
                    play.waitFor(() -> play.expect(screen.queryByText("Counts")).toBeNull());
                    play.waitFor(() -> play.expect("the columns added", () -> headers.count().get() - before.get())
                            .toBe(1));
                    // Once more: one more column, so no choice handler was left behind by the last
                    play.click(addColumn);
                    play.click(play.within(screen.querySelector(".selection-popup")).findByText("Counts"));
                    play.click(play.within(screen.querySelector(".selection-popup")).findByText("Count Groups"));
                    play.waitFor(() -> play.expect("the columns added", () -> headers.count().get() - before.get())
                            .toBe(2));
                    DashboardPlays.expectNoProblems(play);
                })
                // The column's Filter dialog splits Include from Exclude with a splitter between them,
                // so Tab meets Include, the splitter, then Exclude
                .story("TableIncludeExcludeFilter", DashboardSupport.story(DashboardDocs.QUERY_TABLE_DASHBOARD,
                        routes -> {
                        }))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    DashboardPlays.opened(play);
                    DashboardPlays.runQuery(play, 0);
                    play.waitFor(() -> play.expect(play.getByText("alpha")).toBeInTheDocument());
                    StroomDom.clickHeader(play, DashboardPlays.header(play, "Name"));
                    play.click(DashboardPlays.menuItem(screen, "Filter"));
                    final Play dialog = DashboardPlays.dialog(screen, "Filter 'Name'");
                    play.click(dialog.getByText("Include Exclude", StroomDom.LINK_TAB_LABEL));
                    // The text areas by id: the splitter is also named Include, after the pane it sizes
                    final Query include = dialog.querySelector("textarea#filterInclude");
                    final Query splitter = dialog.getByRole("separator", "Include");
                    final Query exclude = dialog.querySelector("textarea#filterExclude");
                    play.waitFor(() -> play.expect(include).toBeVisible());
                    play.expect(exclude).toBeVisible();
                    play.expect("Include before the splitter", () -> follows(
                            include.element().get(), splitter.element().get())).toBe(true);
                    play.expect("the splitter before Exclude", () -> follows(
                            splitter.element().get(), exclude.element().get())).toBe(true);
                    DashboardPlays.expectNoProblems(play);
                })
                // The column values filter's whitelist is sent with the table's next search
                .story("TableColumnValuesFilter", DashboardSupport.story(DashboardDocs.QUERY_TABLE_DASHBOARD,
                        routes -> routes.route(COLUMN_VALUES_REQUEST, RestReply.json(COLUMN_VALUES))))
                .withPlay(play -> {
                    DashboardPlays.opened(play);
                    filterAlpha(play);
                    play.waitFor(() -> play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(
                            DashboardSupport.SEARCH
                                    .withBody("t1's Name column selects only alpha", body -> isAlphaSelection(
                                            DashboardPlays.at(DashboardPlays.componentRequest(body, "t1"),
                                                    "tableSettings", "fields", 0, "columnValueSelection")))
                                    .toSpyMatcher()));
                    DashboardPlays.expectNoProblems(play);
                })
                // The table's filter row: what is typed in a column's filter box is sent with the
                // table's next search once the box loses the focus (or Enter is pressed, see
                // real-input/dashboard-table.mjs), and the box's button turns the filter off and on,
                // by mouse or keyboard. The header is drawn again each time, and the focus stays
                // where it was
                .story("TableFilterRow", DashboardSupport.story(FILTER_ROW_DASHBOARD, routes -> {
                }))
                .withPlay(play -> {
                    final Spy requests = play.spy(ScreenHarness.REQUEST_SPY);
                    DashboardPlays.opened(play);
                    DashboardPlays.runQuery(play, 0);
                    play.waitFor(() -> play.expect(play.getByText("alpha")).toBeInTheDocument());

                    play.expect(filterBox(play)).toHaveAttribute("aria-label", "Filter Name");
                    play.type(filterBox(play), "alp");
                    play.tab();
                    play.waitFor(() -> play.expect(requests).toHaveBeenCalledWith(DashboardSupport.SEARCH
                            .withBody("t1's Name column filters on alp", body -> "alp".equals(nameFilter(body,
                                    "filter")))
                            .toSpyMatcher()));
                    play.expect(filterBox(play)).toHaveValue("alp");
                    play.expect(filterButton(play)).toHaveFocus();
                    play.expect(filterButton(play)).toHaveAttribute("title", "Disable Filter");

                    // Enter on the button turns the filter off, and the button keeps the focus
                    play.keyboard("{Enter}");
                    play.waitFor(() -> play.expect(requests).toHaveBeenCalledWith(DashboardSupport.SEARCH
                            .withBody("t1's Name column filter turned off", body -> Boolean.FALSE.equals(
                                    nameFilter(body, "enabled")))
                            .toSpyMatcher()));
                    play.waitFor(() -> play.expect(filterButton(play)).toHaveAttribute("title", "Enable Filter"));
                    play.expect(filterButton(play)).toHaveFocus();
                    play.expect(filter(play)).toHaveClass("dashboard-table-filter-cell--disabled");

                    // A click turns it on again, without opening the column's menu
                    StroomDom.clickHeader(play, filterButton(play));
                    play.waitFor(() -> play.expect(filterButton(play)).toHaveAttribute("title", "Disable Filter"));
                    play.expect(filter(play)).not().toHaveClass("dashboard-table-filter-cell--disabled");
                    play.expect(play.screen().querySelector(StroomDom.MENU_ITEM_TEXT)).not().toBeInTheDocument();
                    DashboardPlays.expectNoProblems(play);
                })
                // The values filter by keyboard: from a cell, Up goes to the column's header, Enter
                // opens its menu, and its Filter Values item opens the filter the header's icon does
                .story("TableColumnValuesFilterByKeyboard", DashboardSupport.story(
                        DashboardDocs.QUERY_TABLE_DASHBOARD,
                        routes -> routes.route(COLUMN_VALUES_REQUEST, RestReply.json(COLUMN_VALUES))))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    DashboardPlays.opened(play);
                    DashboardPlays.runQuery(play, 0);
                    play.waitFor(() -> play.expect(play.getByText("alpha")).toBeInTheDocument());
                    // Right goes to the Name cell: the group expander's column (1px wide, no groups)
                    // is no stop
                    play.click(play.getByText("alpha"));
                    play.keyboard("{ArrowRight}");
                    play.waitFor(() -> play.expect(play.getByText("alpha").closest("[data-col]")).toHaveFocus());
                    play.keyboard("{ArrowUp}");
                    play.waitFor(() -> play.expect(DashboardPlays.headerCell(play, "Name")).toHaveFocus());
                    play.keyboard("{Enter}");
                    play.click(screen.findByText("Filter Values", StroomDom.MENU_ITEM_TEXT));
                    play.waitFor(() -> play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(
                            COLUMN_VALUES_REQUEST.toSpyMatcher()));
                    play.expect(screen.findByText("Select None")).toBeInTheDocument();
                    DashboardPlays.expectNoProblems(play);
                })
                // A visualisation of a table fetches again when the table's column filter changes
                .story("VisRefetchesOnColumnFilter", DashboardSupport.story(DashboardDocs.QUERY_TABLE_VIS_SPLIT,
                        routes -> {
                            visRoutes(routes);
                            routes.route(COLUMN_VALUES_REQUEST, RestReply.json(COLUMN_VALUES));
                        }))
                .withPlay(play -> {
                    final Spy requests = play.spy(ScreenHarness.REQUEST_SPY);
                    DashboardPlays.opened(play);
                    DashboardPlays.runQuery(play, 0);
                    play.waitFor(() -> play.expect(play.getByText("alpha")).toBeInTheDocument());
                    final Value<Integer> before = play.capture("before", () -> visOnlySearches(requests));
                    filterAlpha(play);
                    play.waitFor(8000, () -> play.expect("searches for only v1's results",
                            () -> visOnlySearches(requests) - before.get()).toBeGreaterThan(0));
                    DashboardPlays.expectNoProblems(play);
                })
                // An Embedded Query's settings find the fields of its query's data source, for its
                // selection handlers
                .story("EmbeddedQuerySettingsFields", DashboardSupport.story(DashboardDocs.DASHBOARD_DOC, routes ->
                        routes.route(RequestMatcher.post("/query/v1/fetchDataSourceFromQueryString"),
                                        RestReply.json(
                                                "{\"type\": \"Index\", \"uuid\": \"idx-1\", \"name\": \"index\"}"))
                                // The data source's fields, for the field list
                                .route(RequestMatcher.post("/dataSource/v1/findFields"), RestReply.json(
                                        "{\"values\": [], \"pageResponse\": {\"offset\": 0, \"length\": 0, "
                                        + "\"total\": 0, \"exact\": true}}"))))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    DashboardPlays.opened(play);
                    DashboardPlays.openTabMenu(play, "My Panel");
                    play.click(DashboardPlays.menuItem(screen, "Settings"));
                    final Play settings = DashboardPlays.dialog(screen, "Settings");
                    // The selection query's handlers are a tab of the settings
                    play.click(settings.getByText("Selection Query", StroomDom.LINK_TAB_LABEL));
                    // The grid's Enabled column has no header text, and the toolbar's buttons are
                    // SelectionHandlersController's, each named for what it acts on
                    play.waitFor(() -> play.expect(settings.getByText("Expression", StroomDom.GRID_HEADER_TEXT))
                            .toBeInTheDocument());
                    play.expect(settings.querySelectorAll(StroomDom.GRID_HEADER_CELL)).toHaveLength(2);
                    for (final String title : List.of("New Selection Handler", "Edit Selected Selection Handler",
                            "Duplicate Selected Selection Handler", "Delete Selected Selection Handler",
                            "Move Selection Handler Up", "Move Selection Handler Down")) {
                        play.expect(settings.getAllByTitle(title).count()).toBeGreaterThan(0);
                    }
                    play.expect(settings.getAllByTitle("Edit Selected Selection Handler").nth(0))
                            .toHaveAttribute("aria-disabled", "true");
                    play.click(settings.getAllByTitle("New Selection Handler").nth(0));
                    // The caption is 'Add New Selection Handler'
                    play.expect(screen.findByText("Add New Selection Handler", StroomDom.DIALOG_TITLE))
                            .toBeInTheDocument();
                    // Add a term and open its field list, which asks for the query's data source
                    final Play handler = DashboardPlays.dialog(screen, "Add New Selection Handler");
                    play.click(handler.getByTitle("Add Term"));
                    final Query fieldPicker = handler.querySelector(
                            ".term-editor__item--field " + StroomDom.SELECTION_BOX);
                    play.waitFor(() -> play.expect(fieldPicker).toBeInTheDocument());
                    play.click(fieldPicker);
                    // The data source of the embedded (copied) query is found from its StroomQL, for
                    // its handlers' field suggestions (it was once only found for a referenced query)
                    play.waitFor(() -> play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(
                            RequestMatcher.post("/query/v1/fetchDataSourceFromQueryString")
                                    .withBodyContaining("from index")
                                    .toSpyMatcher()));

                    DashboardPlays.expectNoProblems(play);
                })
                // The table's settings have its conditional formatting rules
                .story("TableConditionalFormatting", DashboardSupport.story(DashboardDocs.QUERY_TABLE_DASHBOARD,
                        routes -> {
                        }))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    DashboardPlays.opened(play);
                    DashboardPlays.openTabMenu(play, "The Table");
                    play.click(DashboardPlays.menuItem(screen, "Settings"));
                    final Play settings = DashboardPlays.dialog(screen, "Settings");
                    // The rules are a tab of the settings dialog, not a dialog of their own
                    play.click(settings.getByText("Conditional Formatting", StroomDom.LINK_TAB_LABEL));
                    play.waitFor(() -> play.expect(settings.getByTitle("New Formatting Rule")).toBeInTheDocument());
                    DashboardPlays.expectNoProblems(play);
                })
                // A new rule's Set Custom Style: the example shows the colours typed, and a colour
                // that isn't one leaves the example's own
                .story("TableConditionalFormattingCustomStyle", DashboardSupport.story(
                        DashboardDocs.QUERY_TABLE_DASHBOARD, routes -> {
                        }))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    DashboardPlays.opened(play);
                    DashboardPlays.openTabMenu(play, "The Table");
                    play.click(DashboardPlays.menuItem(screen, "Settings"));
                    final Play settings = DashboardPlays.dialog(screen, "Settings");
                    play.click(settings.getByText("Conditional Formatting", StroomDom.LINK_TAB_LABEL));
                    play.click(settings.findByTitle("New Formatting Rule"));
                    final Play rule = DashboardPlays.dialog(screen, "Add New Rule");
                    play.click(rule.within(rule.getByText("Formatting Type", "label").closest(".form-group"))
                            .querySelector(StroomDom.SELECTION_BOX));
                    play.click(screen.findByText("Custom", ".selection-popup *"));
                    // The button, not its form group's help button
                    play.click(rule.within(rule.findByText("Custom Style", "label").closest(".form-group"))
                            .querySelector("button:not(.form-group__help)"));
                    final Play style = DashboardPlays.dialog(screen, "Edit Custom Row Style");
                    final Query swatch = style.querySelector(".cf-light-example .cf-colour-swatch");
                    play.type(style.getByLabelText("Light Background Colour"), "#ff0000");
                    play.type(style.getByLabelText("Light Text Colour"), "not a colour");
                    // A text box tells of its change when it loses the focus
                    play.tab();
                    play.waitFor(() -> play.expect(swatch).toHaveStyle("backgroundColor", "rgb(255, 0, 0)"));
                    // The light example's own text colour
                    play.expect(swatch).toHaveStyle("color", "rgb(21, 21, 21)");
                    play.click(style.getByRole("button", StroomDom.button("OK")));
                    play.waitFor(() -> play.expect(screen.queryByText("Edit Custom Row Style",
                            StroomDom.DIALOG_TITLE)).toBeNull());
                    DashboardPlays.expectNoProblems(play);
                })
                // A visualisation's settings show the controls its settings define
                .story("VisDynamicControls", DashboardSupport.story(DashboardDocs.VIS_DYNAMIC_DASHBOARD,
                        routes -> DocumentEditors.decorated(routes.get("/visualisation/v1/vis-1",
                                        RestReply.json(VISUALISATION_WITH_CONTROLS)),
                                "{\"type\": \"Visualisation\", \"uuid\": \"vis-1\", \"name\": \"Bar\"}")))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    DashboardPlays.opened(play);
                    DashboardPlays.openTabMenu(play, "Chart");
                    play.click(DashboardPlays.menuItem(screen, "Settings"));
                    final Play dialog = DashboardPlays.dialog(screen, "Settings");
                    // The controls are on a tab of their own, named after their tab
                    play.click(dialog.findByText("Axes", StroomDom.LINK_TAB_LABEL));
                    play.waitFor(() -> play.expect(dialog.getByText("X Axis", "label")).toBeInTheDocument());
                    play.expect(dialog.getByText("Title", "label")).toBeInTheDocument();
                    // The control has no id; it is the Title form group's
                    play.expect(dialog.within(dialog.getByText("Title", "label").closest(".form-group"))
                            .querySelector("input")).toHaveValue("Chart");
                    DashboardPlays.expectNoProblems(play);
                })
                // GWT-only: a component of a type Stroom doesn't know is shown as a placeholder
                // naming its type (UnknownComponentController), and the rest of the dashboard works
                .story("UnknownComponent", DashboardSupport.story(DashboardDocs.UNKNOWN_COMPONENT_DASHBOARD,
                        routes -> {
                        }))
                .withPlay(play -> {
                    DashboardPlays.opened(play);
                    play.waitFor(() -> play.expect(play.getByText("Unknown component type: mystery"))
                            .toBeInTheDocument());
                    play.expect(DashboardPlays.tab(play, "Mystery")).toBeInTheDocument();
                    DashboardPlays.expectNoProblems(play);
                })
                // A Text component shows the source of the row selected in its table
                .story("TextFollowsSelection", DashboardSupport.story(DashboardDocs.QUERY_TABLE_TEXT_DASHBOARD,
                        DashboardComponentStories::textRoutes))
                .withPlay(play -> {
                    DashboardPlays.opened(play);
                    play.waitFor(() -> play.expect(play.querySelector(".stroom-dashboard-text .ace_editor"))
                            .not().toBeNull());
                    play.expect(play.queryByText(TextMatch.containingIgnoreCase("select a row in the linked table")))
                            .toBeNull();
                    DashboardPlays.runQuery(play, 0);
                    play.waitFor(() -> play.expect(play.getByText("alpha")).toBeInTheDocument());
                    play.click(play.getByText("alpha"));
                    play.waitFor(() -> play.expect(play.getAllByText(TextMatch.containing("sample source data"))
                            .count()).toBeGreaterThanOrEqual(1));
                    DashboardPlays.expectNoProblems(play);
                })
                // A Text component with a pipeline shows its source as HTML
                .story("TextShowAsHtml", DashboardSupport.story(DashboardDocs.QUERY_TABLE_HTML_DASHBOARD,
                        DashboardComponentStories::htmlRoutes))
                .withPlay(play -> {
                    DashboardPlays.opened(play);
                    DashboardPlays.runQuery(play, 0);
                    play.waitFor(() -> play.expect(play.getByText("alpha")).toBeInTheDocument());
                    play.click(play.getByText("alpha"));
                    play.waitFor(() -> play.expect(play.getByText("Piped HTML")).toBeInTheDocument());
                    DashboardPlays.expectNoProblems(play);
                })
                // A Text component's Step button steps the selected row's source
                .story("TextSteppingButton", DashboardSupport.story(DashboardDocs.QUERY_TABLE_TEXT_STEP_DASHBOARD,
                        DashboardComponentStories::textRoutes,
                        // Stroom also needs VIEW_DATA_PERMISSION to show the source
                        options -> options.harness(builder -> builder.appPermissions(
                                        AppPermission.DOWNLOAD_SEARCH_RESULTS_PERMISSION,
                                        AppPermission.STEPPING_PERMISSION,
                                        AppPermission.VIEW_DATA_PERMISSION))
                                .setup((harness, injector) -> {
                                    harness.fn(BEGIN_STEPPING);
                                    harness.addRegistration(harness.getEventBus().addHandler(
                                            BeginPipelineSteppingEvent.getType(),
                                            event -> harness.spy(BEGIN_STEPPING, String.valueOf(
                                                    event.getStepLocation().getMetaId()))));
                                })))
                .withPlay(play -> {
                    DashboardPlays.opened(play);
                    // The button is titled 'Enter Stepping Mode'
                    DashboardPlays.runQuery(play, 0);
                    play.waitFor(() -> play.expect(play.getByText("alpha")).toBeInTheDocument());
                    // Disabled until a row is selected, as there is nothing to step, saying why (it was
                    // once enabled and alerted 'No stream id', then hidden). It is named for screen
                    // readers by its tooltip, and stays focusable
                    final Query waiting = play.getByRole("button", "Select a row to step through its source");
                    play.expect(waiting).toBeVisible();
                    play.expect(waiting).toHaveAttribute("aria-disabled", "true");
                    play.click(play.getByText("alpha"));
                    play.waitFor(() -> play.expect(play.getByRole("button", STEP))
                            .not().toHaveAttribute("aria-disabled"));
                    play.click(play.getByTitle(STEP));
                    // The play checks the app's stepping event, for the selected row's stream
                    play.expect(play.spy(BEGIN_STEPPING)).toHaveBeenCalledWith("1001");
                    DashboardPlays.expectNoProblems(play);
                })
                // The runs of icon buttons, and the tab stops each costs, once a query has run and a
                // row is selected (the dashboard's, each component's and the text's stepping button)
                .story("ToolbarTabStops", DashboardSupport.story(DashboardDocs.QUERY_TABLE_TEXT_STEP_DASHBOARD,
                        DashboardComponentStories::textRoutes,
                        options -> options.harness(builder -> builder.appPermissions(
                                AppPermission.DOWNLOAD_SEARCH_RESULTS_PERMISSION,
                                AppPermission.STEPPING_PERMISSION,
                                AppPermission.VIEW_DATA_PERMISSION))))
                .withPlay(play -> {
                    DashboardPlays.opened(play);
                    DashboardPlays.runQuery(play, 0);
                    play.waitFor(() -> play.expect(play.getByText("alpha")).toBeInTheDocument());
                    play.click(play.getByText("alpha"));
                    play.waitFor(() -> play.expect(play.getByRole("button", STEP))
                            .not().toHaveAttribute("aria-disabled"));
                    // The document's Save buttons and the dashboard's own sit together, one toolbar
                    // (one tab stop); the stepping button (a
                    // FabButton) is one tab stop (it was once two: its outer element and its button)
                    play.expect(ButtonRuns.of(play.body())).toEqual(List.of(
                            "Save, Save As, Design Mode, Maximise All Panes: 1 tab stop",
                            "Execute Query: 1 tab stop",
                            "Tab Actions: 1 tab stop",
                            "Tab Actions: 1 tab stop",
                            "Tab Actions: 1 tab stop",
                            "Add Term, Add Operator, Duplicate Selected Expression Item, "
                            + "Disable Selected Expression Item, Delete Selected Expression Item, "
                            + "Show Query History, Show Favourite Queries, Download Query: 1 tab stop",
                            "Execute Query: 1 tab stop",
                            "Add Column, Expand Groups, Collapse Groups, Download Table Results, Column Filters, "
                            + "Ask Stroom AI: 1 tab stop",
                            "First, Backward, Forward, Last, Not Updating: 1 tab stop",
                            "Enter Stepping Mode: 1 tab stop"));
                })
                // The table sends its conditional formatting rules, and colours the rows that match
                .story("TableConditionalFormattingRenders", DashboardSupport.story(DashboardDocs.CF_DASHBOARD,
                        routes -> routes.route(DashboardSupport.SEARCH, RestReply.json(cfResponse("cf1")))))
                .withPlay(play -> {
                    DashboardPlays.opened(play);
                    DashboardPlays.runQuery(play, 0);
                    DashboardPlays.selectTab(play, "T");
                    play.waitFor(() -> play.expect(play.getByText("beta")).toBeInTheDocument());
                    play.waitFor(() -> play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(
                            DashboardSupport.SEARCH
                                    .withBody("sends t1's rule cf1", body -> "cf1".equals(DashboardPlays.at(
                                            DashboardPlays.componentRequest(body, "t1"),
                                            "tableSettings", "conditionalFormattingRules", 0, "id")))
                                    .toSpyMatcher()));
                    // The rows (role="row")
                    play.expect(play.getByText("beta").closest("[role=\"row\"]")).toHaveClass("cf-red");
                    play.expect(play.getByText("alpha").closest("[role=\"row\"]")).not().toHaveClass("cf-red");
                    DashboardPlays.expectNoProblems(play);
                })
                // A custom conditional formatting rule colours the rows that match with its dark
                // theme's colour
                .story("TableConditionalFormattingCustomColour", DashboardSupport.story(
                        DashboardDocs.CF_CUSTOM_DASHBOARD,
                        routes -> routes.route(DashboardSupport.SEARCH, RestReply.json(cfResponse("cfc")))))
                .withPlay(play -> {
                    DashboardPlays.opened(play);
                    DashboardPlays.runQuery(play, 0);
                    DashboardPlays.selectTab(play, "T");
                    play.waitFor(() -> play.expect(play.getByText("beta")).toBeInTheDocument());
                    // Stroom styles the row with a generated class, not inline, so the play checks
                    // the row's computed background: the rule's colour for the theme
                    play.waitFor(() -> play.expect(play.getByText("beta").closest("tr, [role=\"row\"]"))
                            .toHaveStyle("backgroundColor", StroomThemeDecorator.isDark()
                                    ? "rgb(200, 100, 50)"
                                    : "rgb(10, 20, 30)"));
                    DashboardPlays.expectNoProblems(play);
                })
                // The Query's history recalls a past query, and its favourites save the current one
                // A favourite needs a name: OK with none says so, and the dialog stays open
                .story("FavouriteNoName", DashboardSupport.story(DashboardDocs.QUERY_TABLE_DASHBOARD,
                        DashboardComponentStories::storedQueryRoutes))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    DashboardPlays.opened(play);
                    play.click(play.getByRole("button", "Show Favourite Queries"));
                    final Play favourites = DashboardPlays.dialog(screen, "Query Favourites");
                    play.click(favourites.getByTitle("Create Favourite From Current Query"));
                    final Play name = DashboardPlays.dialog(screen, "Create New Favourite");
                    play.click(name.getByRole("button", StroomDom.button("OK")));
                    play.waitFor(() -> play.expect(play.spy(ScreenHarness.ALERT_SPY))
                            .toHaveBeenCalledWith(ValueMatcher.stringContaining("You must provide a name")));
                    play.expect(screen.getByText("Create New Favourite", StroomDom.DIALOG_TITLE)).toBeInTheDocument();
                    play.expect(play.spy(ScreenHarness.REQUEST_SPY)).not().toHaveBeenCalledWith(
                            RequestMatcher.post("/storedQuery/v1/create").toSpyMatcher());
                    play.expect(play.spy(ScreenHarness.UNHANDLED_REQUEST_SPY)).not().toHaveBeenCalled();
                })
                .story("QueryHistoryAndFavourites", DashboardSupport.story(DashboardDocs.QUERY_TABLE_DASHBOARD,
                        DashboardComponentStories::storedQueryRoutes))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    DashboardPlays.opened(play);
                    // The Query's toolbar button is titled 'Show Query History'
                    play.click(play.getByRole("button", "Show Query History"));
                    final Play history = DashboardPlays.dialog(screen, "Query History");
                    // The queries are a list (listbox), the first chosen and focused as the dialog
                    // opens; Enter on it (or a double click) opens it. A single click on the chosen
                    // query once did too, as two choices of a row within the double click time
                    final Play queries = screen.within(history.findByRole("listbox", "Query history"));
                    final Query first = queries.getAllByRole("option").nth(0);
                    play.expect(first).toHaveTextContent(TextMatch.containing("Name = alpha"));
                    // Its time and its query are apart in its text, so a copied row reads well (#1070)
                    play.expect(first).toHaveTextContent(TextMatch.containing("Z AND {Name = alpha}"));
                    play.expect(first).toBeVisible();
                    play.waitFor(() -> play.expect(first).toHaveFocus());
                    play.expect(first).toHaveAttribute("aria-selected", "true");
                    play.keyboard("{Enter}");
                    play.waitFor(() -> play.expect(screen.queryByText("Query History", StroomDom.DIALOG_TITLE))
                            .toBeNull());
                    play.waitFor(() -> play.expect(play.getByText("Name = alpha")).toBeInTheDocument());
                    play.click(play.getByRole("button", "Show Favourite Queries"));
                    final Play favourites = DashboardPlays.dialog(screen, "Query Favourites");
                    // A favourite is named in a dialog of its own
                    play.click(favourites.getByTitle("Create Favourite From Current Query"));
                    final Play name = DashboardPlays.dialog(screen, "Create New Favourite");
                    play.type(name.querySelector("input"), "My Fav");
                    // The list changes quickly (each refresh clears its selection), and the dialog
                    // stays open: clearing a selection twice within the double click period is not
                    // a double select (MySingleSelectionModel once counted it as one, which closed
                    // the dialog)
                    play.click(name.getByRole("button", StroomDom.button("OK")));
                    play.waitFor(() -> play.expect(favourites.getByText("My Fav")).toBeInTheDocument());
                    play.expect(screen.getByText("Query Favourites", StroomDom.DIALOG_TITLE)).toBeInTheDocument();
                    play.click(favourites.getByText("My Fav"));
                    play.click(favourites.getByTitle("Delete Favourite"));
                    play.click(screen.within(screen.findByText("Are you sure you want to delete this favourite?")
                            .closest(StroomDom.DIALOG)).getByRole("button", StroomDom.button("OK")));
                    play.waitFor(() -> play.expect(favourites.queryByText("My Fav")).toBeNull());
                    // Still open after the list's changes (a double select would have closed it)
                    play.sleep(100);
                    play.expect(screen.getByText("Query Favourites", StroomDom.DIALOG_TITLE)).toBeInTheDocument();
                    play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(
                            RequestMatcher.post("/storedQuery/v1/create")
                                    .withJsonBodyContaining("{\"name\": \"My Fav\", \"favourite\": true}")
                                    .toSpyMatcher());
                    DashboardPlays.expectNoProblems(play);
                });
    }

    // What is on top at the point the offset given right of the middle of an element: a dashboard
    // splitter, a visualisation's frame, something else, or nothing
    private static native String topmostBeside(Element element, double dx) /*-{
        var rect = element.getBoundingClientRect();
        var hit = $doc.elementFromPoint(rect.left + rect.width / 2 + dx, rect.top + rect.height / 2);
        if (!hit) {
            return 'nothing';
        }
        if (hit.closest('.flex-layout__splitter')) {
            return 'splitter';
        }
        if (hit.closest('.vis-frame') || hit.tagName === 'IFRAME') {
            return 'visualisation';
        }
        return 'something else (' + hit.className + ')';
    }-*/;

    // Filters the table's Name column to 'alpha' with its values filter: run the query, open the
    // header's values filter, Select None, then tick 'alpha'.
    // Stroom opens the values filter with the header's filter icon, and a choice applies at once
    // (the popup has no OK)
    private static void filterAlpha(final Play play) {
        final Play screen = play.screen();
        DashboardPlays.runQuery(play, 0);
        play.waitFor(() -> play.expect(play.getByText("alpha")).toBeInTheDocument());
        StroomDom.clickHeader(play, play.within(DashboardPlays.headerCell(play, "Name"))
                .querySelector(".column-header__value-filter-icon"));
        play.waitFor(() -> play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(
                COLUMN_VALUES_REQUEST.toSpyMatcher()));
        play.click(screen.findByText("Select None"));
        // The popup's 'alpha' is after the table's on the page
        play.waitFor(() -> play.expect(screen.getAllByText("alpha")).toHaveLength(2));
        play.click(screen.getAllByText("alpha").nth(1));
    }

    // The Name column's filter box
    private static Query filterBox(final Play play) {
        return play.within(DashboardPlays.headerCell(play, "Name"))
                .querySelector(".dashboard-table-filter-cell .text-field");
    }

    // The Name column's filter (its box and button), which says when it is off
    private static Query filter(final Play play) {
        return play.within(DashboardPlays.headerCell(play, "Name")).querySelector(".dashboard-table-filter-cell");
    }

    // The Name column's filter disable button
    private static Query filterButton(final Play play) {
        return play.within(DashboardPlays.headerCell(play, "Name"))
                .querySelector(".dashboard-table-filter-cell__disable-button");
    }

    // A property of the Name column's filter in a dashboard search's request for t1
    private static Object nameFilter(final String body, final String property) {
        return DashboardPlays.at(DashboardPlays.componentRequest(body, "t1"),
                "tableSettings", "fields", 0, "columnFilter", property);
    }

    // Whether a column value selection selects only 'alpha'
    private static boolean isAlphaSelection(final Object selection) {
        return Boolean.FALSE.equals(DashboardPlays.at(selection, "invert"))
               && List.of("alpha").equals(DashboardPlays.at(selection, "values"));
    }

    // The number of dashboard searches the spy recorded that ask for only v1's results
    private static int visOnlySearches(final Spy requests) {
        int count = 0;
        for (final List<Object> call : requests.getCalls()) {
            final RecordedRequest request = RecordedRequest.parse(String.valueOf(call.get(0)));
            if (DashboardSupport.SEARCH.matches(request)
                && List.of("v1").equals(DashboardPlays.componentIds(request.getBody()))) {
                count++;
            }
        }
        return count;
    }

    // Whether the second element comes after the first in the page
    private static native boolean follows(Element first, Element second) /*-{
        return (first.compareDocumentPosition(second) & 4) !== 0; // DOCUMENT_POSITION_FOLLOWING
    }-*/;

    // Whether the items are in the list in the order given
    private static boolean inOrder(final List<String> list, final List<String> items) {
        int last = -1;
        for (final String item : items) {
            final int index = list.indexOf(item);
            if (index <= last) {
                return false;
            }
            last = index;
        }
        return true;
    }

    // Conditional formatting searches: beta matches the rule
    private static String cfResponse(final String ruleId) {
        return DashboardSupport.tableResponse("t1", "[" + DashboardDocs.NAME_FIELD + "]",
                "[{\"values\": [\"alpha\"], \"depth\": 0}, {\"values\": [\"beta\"], \"depth\": 0, "
                + "\"matchingRule\": \"" + ruleId + "\"}]", 2);
    }

    // A visualisation's routes
    private static void visRoutes(final RestFixtures.Builder routes) {
        routes.get("/visualisation/v1/vis-1", RestReply.json(VISUALISATION))
                .post("/script/v1/fetchLinkedScripts", RestReply.json("[]"));
    }

    // A table result with stream and event ids, and the stream's source
    private static void textRoutes(final RestFixtures.Builder routes) {
        routes.route(DashboardSupport.SEARCH, RestReply.json(STREAM_RESPONSE))
                .post("/data/v1/fetch", RestReply.json(sourceData("sample source data")));
    }

    // The source piped to HTML
    private static void htmlRoutes(final RestFixtures.Builder routes) {
        routes.route(DashboardSupport.SEARCH, RestReply.json(STREAM_RESPONSE))
                .post("/data/v1/fetch", RestReply.json(sourceData("<b>Piped HTML</b>")
                        .replace("\"html\": false", "\"html\": true")));
    }

    // A stream's source (FetchDataResult)
    private static String sourceData(final String data) {
        return "{\"type\": \"data\", \"feedName\": \"TEST_FEED\", \"streamTypeName\": \"Events\", "
               + "\"sourceLocation\": {\"metaId\": 1001, \"partIndex\": 0, \"recordIndex\": 0}, "
               + "\"itemRange\": {\"offset\": 0, \"length\": 1}, \"totalItemCount\": {\"count\": 1, \"exact\": true}, "
               + "\"totalCharacterCount\": {\"count\": 18, \"exact\": true}, \"data\": \"" + data + "\", "
               + "\"html\": false, \"dataType\": \"NON_SEGMENTED\", \"displayMode\": \"TEXT\"}";
    }

    // Stored queries: a fixed history and favourites that are added and deleted
    private static void storedQueryRoutes(final RestFixtures.Builder routes) {
        routes.route(RequestMatcher.post("/storedQuery/v1/find").withJsonBodyContaining("{\"favourite\": false}"),
                        RestReply.json(page(HISTORY)))
                .route(RequestMatcher.post("/storedQuery/v1/find").withJsonBodyContaining("{\"favourite\": true}"),
                        RestReply.json(page()),
                        RestReply.json(page(MY_FAV)),
                        RestReply.json(page()))
                .post("/storedQuery/v1/create", RestReply.json(MY_FAV))
                .delete("/storedQuery/v1/delete", RestReply.json("true"));
    }

    // A page of stored queries
    private static String page(final String... values) {
        return "{\"values\": [" + String.join(", ", values) + "], \"pageResponse\": {\"offset\": 0, \"length\": "
               + values.length + ", \"total\": " + values.length + ", \"exact\": true}}";
    }
}
