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

package stroom.dashboard.client.flexlayout;

/// Keeps a layout's tabs pointing at the panes that hold them.
///
/// Maximising a dashboard puts every visible tab into a temporary pane, which becomes each tab's parent. Restoring
/// the layout brings back the original panes but not the tabs' parents, so a tab removed afterwards is removed from
/// the temporary pane only (staying in the layout without its component), and a component added next to a tab finds
/// no pane for it. See gchq/stroom#6034.
final class LayoutTabParents {

    private LayoutTabParents() {
        // Static only.
    }

    /// Makes each tab pane in the layout the parent of the tabs it holds.
    ///
    /// @param layoutConfig The layout, or a part of it; null is ignored.
    static void reparentTabs(final MutableLayoutConfig layoutConfig) {
        if (layoutConfig instanceof final MutableTabLayoutConfig tabLayoutConfig) {
            for (final MutableTabConfig tab : tabLayoutConfig.getTabs()) {
                tab.setParent(tabLayoutConfig);
            }
        } else if (layoutConfig instanceof final MutableSplitLayoutConfig splitLayoutConfig) {
            for (final MutableLayoutConfig child : splitLayoutConfig.getChildren()) {
                reparentTabs(child);
            }
        }
    }
}
