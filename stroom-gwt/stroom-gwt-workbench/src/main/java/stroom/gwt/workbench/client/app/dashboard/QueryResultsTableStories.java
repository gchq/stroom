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

import stroom.gwt.workbench.client.app.editors.QueryEditorStories;
import stroom.gwt.workbench.client.app.query.DocumentEditors;
import stroom.gwt.workbench.client.app.query.QueryFixtures;
import stroom.gwt.workbench.client.app.rest.RestFixtures;
import stroom.gwt.workbench.client.app.rest.RestReply;
import stroom.gwt.workbench.client.app.screen.ScreenHarness;
import stroom.gwt.workbench.client.app.screen.StroomDom;
import stroom.gwt.workbench.framework.client.play.Play;
import stroom.gwt.workbench.framework.client.play.Query;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;

/// The `App/Query/QueryResultsTable` stories, showing Stroom's results table (`ResultTable`) as a
/// query's and as a dashboard's, with fake REST replies.
///
/// Most show the query's (`QueryResultTableController`) in a Query editor, as `App/Editors/QueryEditor`
/// does: the table's result is the table result of the search's reply (`POST /query/v1/search/{node}`).
/// The dashboard's `TableController` shares the query table's component, header and cells
/// (`ColumnHeaderCell` with its `FilterCell`, `TableRow` decoration), so those stories use the
/// query's table. A column's 'Expression' editor (`ColumnFunctionEditorController`) is only offered
/// by a dashboard's table, so `DashboardExpressionEditor` opens a dashboard (see `DashboardSupport`)
/// with a `Count` column, and `DashboardUrlLinkNamedByHostAndPath` shows a dashboard table names a
/// link made by `link(url)` as the query's table does.
public final class QueryResultsTableStories {

    // The ColumnFilterRow result: a wrapped, grouped Name column and a Count column with a column
    // filter
    private static final RestFixtures FIXTURES = QueryEditorStories.searchFixtures(QueryFixtures.tableResult("table",
            """
                    [{"id": "f-name", "name": "Name", "format": {"type": "TEXT", "wrap": true}, "group": 0,
                      "visible": true},
                     {"id": "f-count", "name": "Count", "format": {"type": "NUMBER"},
                      "columnFilter": {"enabled": true, "filter": "x"}, "visible": true}]""",
            "[{\"values\": [\"alpha\", \"5\"], \"depth\": 0}]", 1));

    // What `link(url)` gives a table: StroomQL's link() URL encodes the URL it uses as the text
    // and the address, and XSLT's stroom:link() does not
    private static final String QUERY_URL_LINK =
            "[https%3A%2F%2Fexample.com%2Fdocs%2Fpage%3Fq%3D1](https%3A%2F%2Fexample.com%2Fdocs%2Fpage%3Fq%3D1)";
    private static final String XSLT_URL_LINK = "[https://example.org/help/](https://example.org/help/)";

    // The UrlLinkNamedByHostAndPath result: a Link column holding each link
    private static final RestFixtures URL_LINK_FIXTURES = QueryEditorStories.searchFixtures(
            QueryFixtures.tableResult("table",
                    "[{\"id\": \"f-link\", \"name\": \"Link\", \"visible\": true}]",
                    "[{\"values\": [\"" + QUERY_URL_LINK + "\"], \"depth\": 0}, "
                    + "{\"values\": [\"" + XSLT_URL_LINK + "\"], \"depth\": 0}]", 2));

    // A Query and a Table with a Count column
    private static final String DASHBOARD = DashboardDocs.doc("dash-qrt", "Query Results Table", null,
            "\"designMode\": false",
            DashboardDocs.split(0, DashboardDocs.sized(DashboardDocs.tabs(0, "q1"), 300, 100),
                    DashboardDocs.sized(DashboardDocs.tabs(0, "t1"), 600, 100)),
            DashboardDocs.query("q1", "The Query", ""),
            DashboardDocs.table("t1", "The Table", "q1", DashboardDocs.field("f-count", "Count"), ""));

    // The result: a Count of 5
    private static final String RESULT = DashboardSupport.tableResponse("t1",
            "[" + DashboardDocs.field("f-count", "Count") + "]", "[{\"values\": [\"5\"], \"depth\": 0}]", 1);

    // A Query and a Table with a Link column
    private static final String LINK_DASHBOARD = DashboardDocs.doc("dash-qrt-link", "Query Results Links", null,
            "\"designMode\": false",
            DashboardDocs.split(0, DashboardDocs.sized(DashboardDocs.tabs(0, "q1"), 300, 100),
                    DashboardDocs.sized(DashboardDocs.tabs(0, "t1"), 600, 100)),
            DashboardDocs.query("q1", "The Query", ""),
            DashboardDocs.table("t1", "The Table", "q1", DashboardDocs.field("f-link", "Link"), ""));

    // The result: what the dashboard function `link(url)` gives, the URL (URL encoded) as both the
    // text and the address
    private static final String LINK_RESULT = DashboardSupport.tableResponse("t1",
            "[" + DashboardDocs.field("f-link", "Link") + "]",
            "[{\"values\": [\"[https%3A%2F%2Fexample.com%2Fdocs%2Fpage%3Fq%3D1]"
            + "(https%3A%2F%2Fexample.com%2Fdocs%2Fpage%3Fq%3D1)\"], \"depth\": 0}]", 1);

    private QueryResultsTableStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        registry.component("App/Query/QueryResultsTable", QueryResultsTableStories.class)
                .layout(StoryLayout.FULLSCREEN)
                // Every column's header has a filter text box and an enable/disable button; the
                // cells have their value as their title, wrap as their format says and are bold
                // at or above their group level
                .story("ColumnFilterRow", context -> QueryEditorStories.renderEditor(context, FIXTURES))
                .withPlay(play -> {
                    // The table is the query's, so the query is run first
                    play.click(play.findByRole("button", "Execute Query"));
                    play.waitFor(() -> play.expect(play.getByText("alpha")).toBeInTheDocument());
                    final Query inputs = play.querySelectorAll(".filter-cell .text-field");
                    play.expect(inputs).toHaveLength(2);
                    play.expect(inputs.nth(1)).toHaveValue("x");
                    play.expect(play.querySelectorAll(".filter-cell__disable-button")).toHaveLength(2);
                    final Query nameCell = play.getByText("alpha");
                    play.expect(nameCell).toHaveAttribute("title", "alpha");
                    play.expect(nameCell).toHaveClass("result-table__cell--wrap");
                    play.expect(nameCell).toHaveClass("result-table__cell--grouped");
                    play.expect(play.getByText("5")).not().toHaveClass("result-table__cell--grouped");
                    // A filter's button turns it off: the next search asks for it off (the header
                    // shows the columns the server sends back, which here always have it on), and
                    // no column menu opens
                    final Query buttons = play.querySelectorAll(".filter-cell__disable-button");
                    play.expect(buttons.nth(1)).toHaveAttribute("title", "Disable Filter");
                    play.click(buttons.nth(1));
                    play.waitFor(() -> play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(
                            QueryFixtures.SEARCH.withBody("a column filter turned off", body ->
                                            body.contains("\"enabled\":false"))
                                    .toSpyMatcher()));
                    play.expect(play.screen().querySelector(".menuItem-text")).not().toBeInTheDocument();
                    DocumentEditors.expectNoProblems(play);
                })
                // A link made by `link(url)`, with no text of its own, shows its URL but is named by
                // the URL's host and path, so that a screen reader does not spell out the whole URL
                .story("UrlLinkNamedByHostAndPath", context -> QueryEditorStories.renderEditor(context,
                        URL_LINK_FIXTURES))
                .withPlay(play -> {
                    play.click(play.findByRole("button", "Execute Query"));
                    final Query queryLink = play.findByRole("link", "example.com/docs/page");
                    play.expect(queryLink).toHaveAttribute("aria-label", "example.com/docs/page");
                    play.expect(queryLink).toHaveTextContent("https://example.com/docs/page?q=1");
                    final Query xsltLink = play.getByRole("link", "example.org/help");
                    play.expect(xsltLink).toHaveAttribute("aria-label", "example.org/help");
                    play.expect(xsltLink).toHaveTextContent("https://example.org/help/");
                    DocumentEditors.expectNoProblems(play);
                })
                // A dashboard column's Expression opens the expression editor, with an Ace editor
                // and the query help
                .story("DashboardExpressionEditor", DashboardSupport.story(DASHBOARD,
                        routes -> routes.route(DashboardSupport.SEARCH, RestReply.json(RESULT))))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    DashboardPlays.opened(play);
                    // The table is a dashboard's, so its query is run first, and Stroom opens a
                    // column's menu when its header is clicked (not a context menu)
                    DashboardPlays.runQuery(play, 0);
                    play.waitFor(() -> play.expect(play.getByText("5")).toBeInTheDocument());
                    StroomDom.clickHeader(play, DashboardPlays.header(play, "Count"));
                    play.click(DashboardPlays.menuItem(screen, "Edit Expression"));
                    final Play dialog = DashboardPlays.dialog(screen, "Set Expression For 'Count'");
                    // The Ace editor has no label, and the help's quick filter's placeholder is
                    // 'Quick Filter'
                    play.waitFor(() -> play.expect(dialog.querySelector(".ace_editor")).toBeInTheDocument());
                    play.expect(dialog.getByPlaceholderText(StroomDom.QUICK_FILTER_PLACEHOLDER)).toBeInTheDocument();
                    DashboardPlays.expectNoProblems(play);
                })
                // A link made by `link(url)`, with no text of its own, shows its URL but is named by
                // the URL's host and path, so that a screen reader does not spell out the whole URL
                .story("DashboardUrlLinkNamedByHostAndPath", DashboardSupport.story(LINK_DASHBOARD,
                        routes -> routes.route(DashboardSupport.SEARCH, RestReply.json(LINK_RESULT))))
                .withPlay(play -> {
                    DashboardPlays.opened(play);
                    DashboardPlays.runQuery(play, 0);
                    final Query link = play.findByRole("link", "example.com/docs/page");
                    play.expect(link).toHaveAttribute("aria-label", "example.com/docs/page");
                    play.expect(link).toHaveTextContent("https://example.com/docs/page?q=1");
                    DashboardPlays.expectNoProblems(play);
                });
    }
}
