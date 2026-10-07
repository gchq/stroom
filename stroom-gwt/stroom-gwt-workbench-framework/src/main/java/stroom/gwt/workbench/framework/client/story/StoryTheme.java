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

/// The theme the stories are shown in, chosen with the workbench's Theme toolbar item and given
/// to the preview as the `theme` global (`globals=theme:light`, as in React Storybook). The
/// [StoryDecorator] applies it to the page, and stories can read it from their [StoryContext], e.g.
/// to give the code they show the same theme.
public enum StoryTheme {

    /// The dark theme, the default.
    DARK("dark", "Dark"),
    /// The light theme.
    LIGHT("light", "Light");

    /// The theme stories are shown in unless another is chosen.
    public static final StoryTheme DEFAULT = DARK;

    private final String id;
    private final String label;

    StoryTheme(final String id, final String label) {
        this.id = id;
        this.label = label;
    }

    /// @return The theme's id in a URL's globals, e.g. `light`.
    public String getId() {
        return id;
    }

    /// @return The theme's name in the toolbar, e.g. `Light`.
    public String getLabel() {
        return label;
    }

    /// @param id A theme's id, e.g. `light`; may be null.
    /// @return The theme, or [#DEFAULT] if there is none with the id.
    public static StoryTheme fromId(final String id) {
        for (final StoryTheme theme : values()) {
            if (theme.id.equals(id)) {
                return theme;
            }
        }
        return DEFAULT;
    }
}
