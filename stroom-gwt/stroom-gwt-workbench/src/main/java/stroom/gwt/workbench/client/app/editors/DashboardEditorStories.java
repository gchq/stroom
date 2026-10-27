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


package stroom.gwt.workbench.client.app.editors;

import stroom.gwt.workbench.client.app.dashboard.DashboardComponentStories;
import stroom.gwt.workbench.client.app.dashboard.DashboardKeyboardStories;
import stroom.gwt.workbench.client.app.dashboard.DashboardLayoutStories;
import stroom.gwt.workbench.client.app.dashboard.DashboardSearchStories;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;

/// The `App/Editors/DashboardEditor` stories: Stroom's real dashboard editor
/// (`DashboardEditor` with its `DashboardBodyController`, opened as `DashboardPlugin` opens it)
/// with dashboards as Stroom's `DashboardDoc` JSON, and fake REST replies.
///
/// The stories are in the dashboard batch's package (`client.app.dashboard`), by what they show:
/// `DashboardSearchStories` (searches, parameters and inputs), `DashboardLayoutStories` (tabs,
/// panels and design mode), `DashboardKeyboardStories` (the order Tab visits panels and splitters
/// in, and resizing panels with the keyboard) and `DashboardComponentStories` (the components' settings, tables,
/// visualisations and text). See `DashboardSupport` for how a dashboard is opened and its
/// fixtures.
public final class DashboardEditorStories {

    private DashboardEditorStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        DashboardLayoutStories.addTo(registry);
        DashboardKeyboardStories.addTo(registry);
        DashboardSearchStories.addTo(registry);
        DashboardComponentStories.addTo(registry);
    }
}
