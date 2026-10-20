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

package stroom.gwt.workbench.framework.client.story;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/// Holds all the stories in the workbench, in the order they were added.
public class StoryRegistry {

    private final List<Story> stories = new ArrayList<>();
    private final Map<String, Story> storiesById = new HashMap<>();

    /// Starts adding the stories for a component, e.g.
    /// ```
    /// registry.component("Widgets/Buttons/Button")
    ///         .layout(StoryLayout.CENTERED)
    ///         .story("Default", context -> new Button());
    /// ```
    ///
    /// @param title The `/` separated title of the component, e.g. `Widgets/Buttons/Button`.
    /// @return A builder to add the component's stories with.
    public ComponentStories component(final String title) {
        return component(title, null);
    }

    /// Starts adding the stories for a component, recording the class that defines them so the
    /// workbench can open its source in an editor.
    ///
    /// @param title       The `/` separated title of the component, e.g. `Widgets/Buttons/Button`.
    /// @param sourceClass The class that defines the stories, e.g. `ButtonStories.class`, may be
    ///                    null.
    /// @return A builder to add the component's stories with.
    public ComponentStories component(final String title, final Class<?> sourceClass) {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("A component title is required");
        }
        return new ComponentStories(this, title, sourceClass != null
                ? sourceClass.getName()
                : null);
    }

    /// Adds a story.
    ///
    /// @param story The story to add.
    /// @throws IllegalArgumentException If a story with the same id already exists.
    public void add(final Story story) {
        Objects.requireNonNull(story);
        if (storiesById.containsKey(story.getId())) {
            throw new IllegalArgumentException("Duplicate story id " + story.getId());
        }
        stories.add(story);
        storiesById.put(story.getId(), story);
    }

    /// @return All the stories in the order they were added.
    public List<Story> getStories() {
        return Collections.unmodifiableList(stories);
    }

    /// @param id The id of a story.
    /// @return The story with the id, or null if there isn't one.
    public Story getStory(final String id) {
        if (id == null) {
            return null;
        }
        return storiesById.get(id);
    }

    /// @return The first story added, or null if there are no stories.
    public Story getFirstStory() {
        return stories.isEmpty()
                ? null
                : stories.get(0);
    }
}
