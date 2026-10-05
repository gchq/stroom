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

/// Registers every story in the workbench, i.e. the GWT ports of the React Storybook's stories
/// (see the stroom-ui-react repository).
///
/// There is a class for each top level group of the React sidebar, called in the same order as
/// the React sidebar: [AppStories] (`App/*`), [ScreensStories] (`Screens/*`) and
/// [WidgetsStories] (`Widgets/*`). **Don't add story classes here**; add them to the class for
/// their group, after the comment holding their React title (see [WidgetsStories]).
///
/// A story must have the same title and export name as its React original so that its id is the
/// same, letting the two be compared at the same URL and the React story's play function be
/// ported step by step. `TestReactStoryCoverage` checks this against
/// `src/test/resources/react-stories.json`, and `./gradlew :stroom-gwt:stroom-gwt-workbench:workbenchCoverage`
/// reports how many of the React stories have been ported. See `test-runner/README.md`.
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
