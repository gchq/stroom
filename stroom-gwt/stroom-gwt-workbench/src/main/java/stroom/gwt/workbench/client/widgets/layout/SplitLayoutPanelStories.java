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


package stroom.gwt.workbench.client.widgets.layout;

import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;

import com.google.gwt.user.client.ui.MySplitLayoutPanel;
import com.google.gwt.user.client.ui.Widget;

/// Stories for Stroom's [MySplitLayoutPanel], the split panel with a wide (7px) dragger.
public final class SplitLayoutPanelStories {

    private static final String HINT = "Wide 7px dragger →";

    private SplitLayoutPanelStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        // Stroom's split panels have no maximum size: they only limit a drag to the panel's own
        // size.
        registry.component("Widgets/Layout/SplitLayoutPanel", SplitLayoutPanelStories.class)
                .layout(StoryLayout.FULLSCREEN)
                .story("Horizontal", context -> {
                    final MySplitLayoutPanel split = new MySplitLayoutPanel();
                    SplitPanes.layout(split, false, false, SplitPanes.DEFAULT_SIZE, HINT);
                    return SplitPanes.frame(split);
                })
                // The docked pane starts at 120px so the divider and bottom pane are visible
                .story("Vertical", context -> {
                    final MySplitLayoutPanel split = new MySplitLayoutPanel();
                    SplitPanes.layout(split, true, false, 120, HINT);
                    return SplitPanes.frame(split);
                })
                // With a positive minimum - the first pane cannot be dragged below 150px
                .story("WithMinimum", context -> {
                    final MySplitLayoutPanel split = new MySplitLayoutPanel();
                    final Widget first = SplitPanes.layout(
                            split, false, false, SplitPanes.DEFAULT_SIZE, HINT);
                    split.setWidgetMinSize(first, 150);
                    return SplitPanes.frame(split);
                })
                // End-anchored - the second (east) pane is the docked, fixed-size pane
                .story("AnchorEnd", context -> {
                    final MySplitLayoutPanel split = new MySplitLayoutPanel();
                    SplitPanes.layout(split, false, true, 250, HINT);
                    return SplitPanes.frame(split);
                })
                // Proportional sizing - the docked pane keeps 30% of the width across resizes
                .story("Proportional", context -> {
                    final MySplitLayoutPanel split = new MySplitLayoutPanel();
                    SplitPanes.layout(split, false, false, SplitPanes.DEFAULT_SIZE, HINT);
                    // As Stroom's .ui.xml files set hSplits="0.3"
                    split.setHSplits(0.3);
                    return SplitPanes.frame(split);
                });
    }
}
