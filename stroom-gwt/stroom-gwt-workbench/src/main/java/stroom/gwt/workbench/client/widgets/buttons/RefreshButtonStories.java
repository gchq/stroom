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

package stroom.gwt.workbench.client.widgets.buttons;

import stroom.gwt.workbench.client.StoryPanels;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;
import stroom.widget.button.client.RefreshButton;
import stroom.widget.button.client.Toolbar;

import com.google.gwt.user.client.ui.FlowPanel;

import java.util.function.Consumer;

/// Stories for [RefreshButton], a list's refresh button, which shows whether the list is refreshing
/// and, when it refreshes itself, whether that is paused. Each is made by [Toolbar#refresh], in a
/// toolbar of its own. Its states are layered icons, so these are mainly for screenshots
/// (`workbenchScreenshotDiff`); animations are stopped in them.
public final class RefreshButtonStories {

    private RefreshButtonStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        registry.component("Widgets/Buttons/RefreshButton", RefreshButtonStories.class)
                .layout(StoryLayout.CENTERED)
                // Ready to refresh
                .story("Idle", context -> refreshButton(button -> {
                }))
                .withPlay(play -> play.expect(play.getByRole("button", "Refresh")).toBeInTheDocument())
                // Refreshing: the spinner turns
                .story("Refreshing", context -> refreshButton(button -> button.setRefreshing(true)))
                // A list that refreshes itself, waiting for the next refresh
                .story("Pausable", context -> refreshButton(button -> button.setAllowPause(true)))
                // A list that refreshes itself, refreshing
                .story("RefreshingPausable", context -> refreshButton(button -> {
                    button.setAllowPause(true);
                    button.setRefreshing(true);
                }))
                // A list that refreshes itself, paused
                .story("Paused", context -> refreshButton(button -> {
                    button.setAllowPause(true);
                    button.setPaused(true);
                }))
                // Paused, refreshing once
                .story("PausedRefreshing", context -> refreshButton(button -> {
                    button.setAllowPause(true);
                    button.setPaused(true);
                    button.setRefreshing(true);
                }))
                .story("Disabled", context -> refreshButton(button -> button.setEnabled(false)))
                // Every combination of refreshing, pausable (allowPause) and paused, labelled, so a
                // screenshot shows which icons each shows
                .story("AllStates", context -> {
                    final FlowPanel grid = new FlowPanel();
                    grid.getElement().getStyle().setProperty("display", "grid");
                    grid.getElement().getStyle().setProperty("gridTemplateColumns", "auto auto");
                    grid.getElement().getStyle().setProperty("gap", "8px 16px");
                    grid.getElement().getStyle().setProperty("alignItems", "center");
                    for (int combination = 0; combination < 8; combination++) {
                        final boolean refreshing = (combination & 1) != 0;
                        final boolean pausable = (combination & 2) != 0;
                        final boolean paused = (combination & 4) != 0;
                        grid.add(StoryPanels.note((refreshing ? "refreshing " : "")
                                + (pausable ? "pausable " : "")
                                + (paused ? "paused" : "")
                                + (combination == 0 ? "idle" : ""), "#dce4e5", "12px"));
                        grid.add(refreshButton(button -> {
                            button.setAllowPause(pausable);
                            button.setPaused(paused);
                            button.setRefreshing(refreshing);
                        }));
                    }
                    return grid;
                });
    }

    // A toolbar holding a refresh button, set up as the story needs, that does nothing when pressed
    private static Toolbar refreshButton(final Consumer<RefreshButton> setUp) {
        final Toolbar toolbar = new Toolbar("Paging");
        setUp.accept(toolbar.refresh(null));
        return toolbar;
    }
}
