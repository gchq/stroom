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

import stroom.gwt.workbench.client.app.rest.RestReply;
import stroom.gwt.workbench.client.app.screen.StroomDom;
import stroom.gwt.workbench.framework.client.play.Play;
import stroom.gwt.workbench.framework.client.play.Query;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;

/// The dashboard stories of `App/Query/QueryResultsTable` (`DashboardExpressionEditor` and
/// `DashboardUrlLinkNamedByHostAndPath`, which shows a dashboard table names a link made by
/// `link(url)` as the query's table does): the other
/// stories of that title (`QueryResultsTableStories`) show a StroomQL query's table, but a column's
/// 'Expression' editor (`ColumnFunctionEditorController`) is only offered by a dashboard's table
/// (`TableController`), so this story opens a dashboard (see `DashboardSupport`) with a `Count`
/// column.
public final class QueryResultsTableDashboardStories {

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

    private QueryResultsTableDashboardStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        registry.component("App/Query/QueryResultsTable", QueryResultsTableDashboardStories.class)
                .layout(StoryLayout.FULLSCREEN)
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
                    DashboardPlays.clickHeader(play, DashboardPlays.header(play, "Count"));
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
