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

/// Creates the widget that a story displays in the preview canvas.
@FunctionalInterface
public interface StoryRenderer {

    /// Creates a new instance of the story's widget. Called each time the story is shown and
    /// whenever its args change.
    ///
    /// @param context The story's args and a way to report actions.
    /// @return The widget to display.
    Widget render(StoryContext context);
}
