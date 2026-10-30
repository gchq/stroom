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

import stroom.gwt.workbench.client.app.screen.StroomDom;
import stroom.gwt.workbench.client.app.vis.ChartVisStories;
import stroom.gwt.workbench.client.app.vis.VisStories;
import stroom.gwt.workbench.framework.client.play.Play;
import stroom.gwt.workbench.framework.client.play.Query;
import stroom.gwt.workbench.framework.client.play.TextMatch;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;

/// The settings of a dashboard's visualisation (`BasicVisSettingsController`, `DynamicSettingsPane`)
/// for a visualisation that declares typed inputs: the content pack's BarChart, whose Y axis takes
/// numbers and is required. A field control offers only the table's columns of a type its input
/// takes, says what the input is for, and says what is wrong when the column chosen isn't one it
/// takes. Needs the visualisations content pack, as the `App/Visualisations` stories do.
public final class VisSettingsStories {

    private static final String TITLE = "App/Dashboard/VisSettings";

    private VisSettingsStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        registry.component(TITLE, VisSettingsStories.class)
                .layout(StoryLayout.FULLSCREEN)
                .tags(VisStories.TAG)
                // The Y axis was set to Host, a column of text, which the bar chart can't draw
                // bars of: the settings keep it but say so. Its list offers only the columns of
                // numbers, Requests, besides the one chosen; choosing Requests clears the warning
                .story("TypedInputs", VisStories.story("BarChart",
                        VisStories.settings("x", "Status", "y", "Host"), ChartVisStories.REQUESTS_BY_STATUS))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    DashboardPlays.opened(play);
                    DashboardPlays.openTabMenu(play, "BarChart");
                    play.click(DashboardPlays.menuItem(screen, "Settings"));
                    final Play dialog = DashboardPlays.dialog(screen, "Settings");
                    play.click(dialog.findByRole("tab", "Data"));

                    final Query yAxis = dialog.findByLabelText("Y Axis");
                    play.expect(yAxis).toHaveAttribute("aria-required", "true");
                    play.waitFor(() -> play.expect(yAxis).toHaveAttribute("aria-invalid", "true"));
                    play.expect(yAxis).toHaveAccessibleDescription(
                            TextMatch.containing("Host is text, but Y axis takes numbers"));
                    play.expect(dialog.getByText("Required. The height of each bar. Takes numbers."))
                            .toBeInTheDocument();

                    play.click(yAxis);
                    final Query requests = screen.findByRole("option", "Requests");
                    play.expect(screen.queryByRole("option", "Status")).toBeNull();
                    play.expect(screen.getByRole("option", "Host")).toBeInTheDocument();
                    play.click(requests);
                    play.waitFor(() -> play.expect(yAxis).toHaveValue("Requests"));
                    play.waitFor(() -> play.expect(yAxis).not().toHaveAttribute("aria-invalid", "true"));

                    // The X axis takes any column, so it offers them all
                    final Query xAxis = dialog.getByLabelText("X Axis");
                    play.expect(xAxis).toHaveAttribute("aria-required", "true");
                    play.click(xAxis);
                    play.expect(screen.findByRole("option", "Host")).toBeInTheDocument();
                    play.expect(screen.getByRole("option", "Status")).toBeInTheDocument();
                    play.expect(screen.getByRole("option", "Requests")).toBeInTheDocument();
                    play.keyboard("{Escape}");

                    play.click(dialog.getByRole("button", StroomDom.button("Cancel")));
                    DashboardPlays.expectNoProblems(play);
                });
    }
}
