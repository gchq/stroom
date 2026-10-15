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

/// Registers the workbench's `Screens/*` stories.
///
/// ## Adding a story class
///
/// 1. Write the stories class in a sub-package of `stroom.gwt.workbench.client.screens` (see
///    `widgets/buttons/ButtonStories` for an example), giving it a `public static void
///    addTo(StoryRegistry registry)` method that calls `registry.component("<title>",
///    XxxStories.class)` and `.story("<export name>", ...)` for each story. The title and export
///    name make the story's id and URL (see `WRITING-STORIES.md`).
/// 2. Call it in [#addTo] on the line after the comment holding its title, e.g.
///    ```
///    // Screens/SignIn/redirectUrl
///    XxxStories.addTo(registry);
///    ```
///    or, for a new title, add a comment for it next to the related titles. Each title has its
///    own line, so that parallel work rarely touches the same lines. Leave the comments in place.
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
