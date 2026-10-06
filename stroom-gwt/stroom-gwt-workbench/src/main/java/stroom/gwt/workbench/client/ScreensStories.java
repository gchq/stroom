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

import stroom.gwt.workbench.client.screens.signin.RedirectUrlStories;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;

/// Registers the GWT ports of the React Storybook's `Screens/*` stories (1 component,
/// 7 stories in the React Storybook), in the React sidebar's order.
///
/// ## Adding a story class
///
/// 1. Write the stories class in the `stroom.gwt.workbench.client.screens` package (see
///    `widgets/buttons/ButtonStories` for an example), giving it a
///    `public static void addTo(StoryRegistry registry)` method that calls
///    `registry.component("<React title>", XxxStories.class)` with exactly the React `title`,
///    and `.story("<React export name>", ...)` for each story, so that the story ids match the
///    React ones (they are checked by `TestReactStoryCoverage`).
/// 2. Call it in [#addTo] on the line after the comment holding its React title, e.g.
///    ```
///    // Screens/SignIn/redirectUrl
///    XxxStories.addTo(registry);
///    ```
///    There is a comment for every React component, so each porter edits a different line, which
///    keeps merge conflicts to a minimum. Leave the comments in place.
///
/// The comments are the titles in `src/test/resources/react-stories.json`. A React component
/// added since then can be added in its sidebar position, or before `return registry;`.
public final class ScreensStories {

    private ScreensStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    /// @return The registry.
    public static StoryRegistry addTo(final StoryRegistry registry) {
        // Screens/SignIn/redirectUrl
        RedirectUrlStories.addTo(registry);
        return registry;
    }
}
