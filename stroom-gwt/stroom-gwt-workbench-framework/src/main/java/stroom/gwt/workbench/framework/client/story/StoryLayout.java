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

/// How a story is laid out in the preview canvas, equivalent to Storybook's `layout` parameter.
public enum StoryLayout {
    /// The story is centred in the canvas.
    CENTERED("sb-main-centered"),
    /// The story has padding around it. This is Storybook's default.
    PADDED("sb-main-padded"),
    /// The story fills the canvas.
    FULLSCREEN("sb-main-fullscreen");

    private final String className;

    StoryLayout(final String className) {
        this.className = className;
    }

    /// @return The CSS class Storybook applies to the preview body for this layout.
    public String getClassName() {
        return className;
    }
}
