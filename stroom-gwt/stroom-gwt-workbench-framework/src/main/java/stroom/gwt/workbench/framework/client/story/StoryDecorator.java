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

import com.google.gwt.user.client.ui.Widget;

/// Wraps every story's widget, e.g. to apply a theme, equivalent to a global decorator in
/// React Storybook's `.storybook/preview.tsx`.
@FunctionalInterface
public interface StoryDecorator {

    /// A decorator that leaves the story's widget as it is.
    StoryDecorator NONE = story -> story;

    /// @param story The widget created by the story.
    /// @return The widget to show in the preview, which may be the story's widget itself.
    Widget decorate(Widget story);

    /// Applies the theme to the preview page before a story is rendered, e.g. by setting the
    /// classes that select it. Does nothing by default.
    ///
    /// @param theme The theme chosen in the workbench's toolbar.
    default void applyTheme(final StoryTheme theme) {
        // Nothing to do
    }
}
