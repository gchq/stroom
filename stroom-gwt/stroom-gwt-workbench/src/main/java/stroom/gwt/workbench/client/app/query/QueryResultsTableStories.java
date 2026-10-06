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
import stroom.gwt.workbench.framework.client.play.Query;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;

/// Stories matching `App/Query/QueryResultsTable` in the React Storybook, showing Stroom's real
/// `QueryResultTablePresenter` (the results table) in a Query editor, as `App/Editors/QueryEditor`
/// does, with fake REST replies: the React story's `result` is the table result of the search's
/// reply (`POST /query/v1/search/{node}`).
///
/// React's `columnMode: 'dashboard'` is the dashboard's `TablePresenter`, which shares the query
/// table's header and cells (`ColumnHeaderCell` with its `FilterCell`, `TableRow` decoration); the
/// story uses the query's table, as no story harness provides a dashboard yet.
/// `DashboardExpressionEditor` is recorded as blocked in `react-story-status.json`.
public final class QueryResultsTableStories {

    // React's ColumnFilterRow result: a wrapped, grouped Name column and a Count column with a
    // column filter
    private static final RestFixtures FIXTURES = QueryEditorStories.searchFixtures(QueryFixtures.tableResult("table",
            """
                    [{"id": "f-name", "name": "Name", "format": {"type": "TEXT", "wrap": true}, "group": 0,
                      "visible": true},
                     {"id": "f-count", "name": "Count", "format": {"type": "NUMBER"},
                      "columnFilter": {"enabled": true, "filter": "x"}, "visible": true}]""",
            "[{\"values\": [\"alpha\", \"5\"], \"depth\": 0}]", 1));

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
                    // Differs from React: the table is the query's, so the query is run first
                    play.click(play.findByRole("button", "Execute Query"));
                    play.waitFor(() -> play.expect(play.getByText("alpha")).toBeInTheDocument());
                    final Query inputs = play.querySelectorAll(".dashboard-table-filter-cell-text");
                    play.expect(inputs).toHaveLength(2);
                    play.expect(inputs.nth(1)).toHaveValue("x");
                    play.expect(play.querySelectorAll(".dashboard-table-filter-cell-disable-button")).toHaveLength(2);
                    final Query nameCell = play.getByText("alpha");
                    play.expect(nameCell).toHaveAttribute("title", "alpha");
                    // React checks the cells' inline styles (el.style), as these do
                    play.expect(nameCell.attribute("style")).toMatch("white-space:normal");
                    play.expect(nameCell.attribute("style")).toMatch("font-weight:bold");
                    play.expect(play.getByText("5").attribute("style")).not().toMatch("font-weight:bold");
                    DocumentEditors.expectNoProblems(play);
                });
    }
}
