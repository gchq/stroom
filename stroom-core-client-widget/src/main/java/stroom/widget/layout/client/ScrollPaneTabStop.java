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

package stroom.widget.layout.client;

/// Whether a [ScrollPane] is a tab stop.
///
/// A pane that scrolls must be reachable from the keyboard, or its hidden content can't be scrolled
/// into view without a mouse (WCAG 2.1.1). Tabbing to something focusable inside it scrolls it, so
/// it need only be a tab stop itself while its content overflows and holds nothing focusable. A
/// pane told whether it is a tab stop keeps to that.
final class ScrollPaneTabStop {

    private ScrollPaneTabStop() {
    }

    /// @param chosen          Whether the pane was made a tab stop (`setFocusable`), or `null` to
    ///                        decide from its content.
    /// @param overflows       Whether its content is bigger than it, so it scrolls.
    /// @param holdsFocusable  Whether its content holds something that takes the focus.
    /// @return Whether the pane is a tab stop.
    static boolean isTabStop(final Boolean chosen, final boolean overflows, final boolean holdsFocusable) {
        if (chosen != null) {
            return chosen;
        }
        return overflows && !holdsFocusable;
    }
}
