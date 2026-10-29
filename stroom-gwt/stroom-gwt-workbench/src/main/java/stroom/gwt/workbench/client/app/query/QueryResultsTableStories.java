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

package stroom.gwt.workbench.client.app.query;

import stroom.gwt.workbench.client.app.editors.QueryEditorStories;
import stroom.gwt.workbench.client.app.rest.RestFixtures;
import stroom.gwt.workbench.client.app.screen.ScreenHarness;
import stroom.gwt.workbench.framework.client.play.Query;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;

/// The `App/Query/QueryResultsTable` stories, showing Stroom's real `QueryResultTableController`
/// (the results table) in a Query editor, as `App/Editors/QueryEditor` does, with fake REST
/// replies: the table's result is the table result of the search's reply
/// (`POST /query/v1/search/{node}`).
///
/// The dashboard's `TableController` shares the query table's header and cells (`ColumnHeaderCell`
/// with its `FilterCell`, `TableRow` decoration), so these stories use the query's table.
/// `DashboardExpressionEditor` is in [stroom.gwt.workbench.client.app.dashboard.QueryResultsTableDashboardStories].
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
                    final Query inputs = play.querySelectorAll(".dashboard-table-filter-cell .text-field");
                    play.expect(inputs).toHaveLength(2);
                    play.expect(inputs.nth(1)).toHaveValue("x");
                    play.expect(play.querySelectorAll(".dashboard-table-filter-cell__disable-button")).toHaveLength(2);
                    final Query nameCell = play.getByText("alpha");
                    play.expect(nameCell).toHaveAttribute("title", "alpha");
                    play.expect(nameCell).toHaveClass("result-table__cell--wrap");
                    play.expect(nameCell).toHaveClass("result-table__cell--grouped");
                    play.expect(play.getByText("5")).not().toHaveClass("result-table__cell--grouped");
                    // A filter's button turns it off: the next search asks for it off (the header
                    // shows the columns the server sends back, which here always have it on), and
                    // no column menu opens
                    final Query buttons = play.querySelectorAll(".dashboard-table-filter-cell__disable-button");
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
                });
    }
}
