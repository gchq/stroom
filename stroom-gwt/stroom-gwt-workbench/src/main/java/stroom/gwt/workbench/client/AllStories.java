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

package stroom.gwt.workbench.client;

import stroom.gwt.workbench.framework.client.story.StoryRegistry;

/// Registers every story in the workbench.
///
/// There is a class for each top level group of the sidebar: [AppStories] (`App/*`),
/// [ScreensStories] (`Screens/*`) and [WidgetsStories] (`Widgets/*`). **Don't add story classes
/// here**; add them to the class for their group, after the comment holding their title (see
/// [WidgetsStories]). See `WRITING-STORIES.md` and `test-runner/README.md`.
public final class AllStories {

    private AllStories() {
        // Static utility
    }

    /// @return A registry containing all the stories.
    public static StoryRegistry create() {
        final StoryRegistry registry = new StoryRegistry();
        AppStories.addTo(registry);
        ScreensStories.addTo(registry);
        WidgetsStories.addTo(registry);
        return registry;
    }
}
