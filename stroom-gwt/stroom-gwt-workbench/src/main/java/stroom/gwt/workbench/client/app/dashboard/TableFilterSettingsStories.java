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

import stroom.gwt.workbench.client.app.rest.RequestMatcher;
import stroom.gwt.workbench.client.app.screen.ScreenHarness;
import stroom.gwt.workbench.client.app.screen.StroomDom;
import stroom.gwt.workbench.framework.client.play.Play;
import stroom.gwt.workbench.framework.client.play.Query;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;

import java.util.List;
import java.util.Map;

/// The `App/Dashboard/TableFilterSettings` stories: the settings of a dashboard's Table Filter
/// (`BasicTableFilterSettingsPresenter` with its `ColumnSelectionPresenter`), which list the
/// columns of the table it filters as tick boxes.
///
/// The settings read the table from the dashboard's components, so the story opens a dashboard with
/// the table and the filter (Stroom's real `DashboardPresenter`, see `DashboardSupport`) and opens
/// the filter's settings from its tab's menu. The plays check the filter's settings in the dashboard
/// that is saved (`PUT /dashboard/v1/{uuid}`).
public final class TableFilterSettingsStories {

    // A table (Name and Count columns) with a Query, and the Table Filter of it with no columns
    private static final String DASHBOARD = DashboardDocs.doc("dash-tf", "Table Filter Settings", null,
            "\"designMode\": false",
            DashboardDocs.split(0, DashboardDocs.sized(DashboardDocs.tabs(0, "tf1"), 300, 100),
                    DashboardDocs.sized(DashboardDocs.tabs(0, "q1", "t1"), 400, 100)),
            DashboardDocs.query("q1", "The Query", ""),
            DashboardDocs.table("t1", "The Table", "q1",
                    DashboardDocs.NAME_FIELD + ", " + DashboardDocs.field("f-count", "Count"),
                    ""),
            DashboardDocs.component("table-filter", "tf1", "Filter", "\"tableId\": \"t1\", \"columns\": []"));

    // The ticked tick box class of Stroom's TickBoxCell
    private static final String TICKED = "tickBox-tick";

    private TableFilterSettingsStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        registry.component("App/Dashboard/TableFilterSettings", TableFilterSettingsStories.class)
                .layout(StoryLayout.FULLSCREEN)
                // The filter's columns are tick boxes of the linked table's columns; ticking one and
                // saving keeps it
                .story("ColumnTickboxes", DashboardSupport.story(DASHBOARD, routes -> {
                }))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    DashboardPlays.opened(play);
                    DashboardPlays.openTabMenu(play, "Filter");
                    play.click(DashboardPlays.menuItem(screen, "Settings"));
                    final Play dialog = DashboardPlays.dialog(screen, "Settings");
                    // The tick boxes are a grid's (TickBoxCell), found by their column's row; the
                    // select-all tick box is the grid's header
                    play.waitFor(() -> play.expect(dialog.getByText("Count")).toBeInTheDocument());
                    play.expect(dialog.querySelector("th .tickBox")).toBeInTheDocument();
                    play.expect(tickBox(dialog, "Name")).toBeInTheDocument();
                    final Query count = tickBox(dialog, "Count");
                    play.expect(count).not().toHaveClass(TICKED);
                    play.click(count);
                    play.waitFor(() -> play.expect(tickBox(dialog, "Count")).toHaveClass(TICKED));
                    play.click(dialog.getByRole("button", StroomDom.button("OK")));
                    // The choice is saved with the dashboard
                    play.waitFor(() -> play.expect(play.getByRole("button", "Save")).toBeEnabled());
                    play.click(play.getByRole("button", "Save"));
                    play.waitFor(() -> play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(
                            RequestMatcher.put("/dashboard/v1/dash-tf")
                                    .withBody("keeps the filter's Count column", body -> hasCountColumn(body))
                                    .toSpyMatcher()));
                    DashboardPlays.expectNoProblems(play);
                });
    }

    // The tick box of a column's row
    private static Query tickBox(final Play dialog, final String column) {
        return dialog.within(dialog.getByText(column, "td *").closest("tr")).querySelector(".tickBox");
    }

    // Whether a saved dashboard's Table Filter has the Count column
    private static boolean hasCountColumn(final String body) {
        if (DashboardPlays.at(body, "dashboardConfig", "components") instanceof final List<?> components) {
            for (final Object component : components) {
                if ("tf1".equals(DashboardPlays.at(component, "id"))
                    && DashboardPlays.at(component, "settings", "columns") instanceof final List<?> columns) {
                    for (final Object column : columns) {
                        if (column instanceof final Map<?, ?> map && "f-count".equals(map.get("id"))) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }
}
