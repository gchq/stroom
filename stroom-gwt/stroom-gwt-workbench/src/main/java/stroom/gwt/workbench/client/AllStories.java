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

import stroom.gwt.workbench.client.widgets.ButtonStories;
import stroom.gwt.workbench.client.widgets.TickBoxStories;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;

/// Registers every story in the workbench. Add new story classes here.
///
/// Stories appear in the sidebar in the order they are added, so keep this in the same order
/// as the React Storybook where possible.
public final class AllStories {

    private AllStories() {
        // Static utility
    }

    /// @return A registry containing all the stories.
    public static StoryRegistry create() {
        final StoryRegistry registry = new StoryRegistry();
        ButtonStories.addTo(registry);
        TickBoxStories.addTo(registry);
        return registry;
    }
}
