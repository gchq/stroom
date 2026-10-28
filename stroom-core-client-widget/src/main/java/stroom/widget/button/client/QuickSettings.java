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

package stroom.widget.button.client;

import stroom.widget.text.client.Label;

import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.FlowPanel;

/// Presets that fill in a value, in titled groups side by side, e.g. a time range's Relative,
/// Present and Past ranges, or a schedule's Minute and Hour intervals. Each group is a `group`
/// named by its title, and each preset a [LinkButton] named by its text, at least 24px high.
///
/// Styled by `quick-settings.css`: `quick-settings`, `__group`, `__title`, `__setting`.
public final class QuickSettings extends Composite {

    private static final String BLOCK = "quick-settings";

    private final FlowPanel panel = new FlowPanel();

    /// Makes an empty set of presets.
    public QuickSettings() {
        panel.setStyleName(BLOCK);
        initWidget(panel);
    }

    /// Adds a group of presets after the last.
    ///
    /// @param title The group's title, which names it, e.g. "Minute".
    /// @return The group, to add presets to.
    public Group addGroup(final String title) {
        final Group group = new Group(title);
        panel.add(group.panel);
        return group;
    }

    /// Removes every group.
    public void clear() {
        panel.clear();
    }


    // --------------------------------------------------------------------------------


    /// A titled group of presets.
    public static final class Group {

        private final FlowPanel panel = new FlowPanel();

        private Group(final String title) {
            panel.setStyleName(BLOCK + "__group");
            final Label label = new Label(title, false);
            label.setStyleName(BLOCK + "__title");
            final String labelId = DOM.createUniqueId();
            label.getElement().setId(labelId);
            panel.add(label);
            panel.getElement().setAttribute("role", "group");
            panel.getElement().setAttribute("aria-labelledby", labelId);
        }

        /// Adds a preset after the last.
        ///
        /// @param name    The preset's text, which names it, e.g. "Every 10 minutes".
        /// @param onPress What pressing it does, e.g. fill in its value.
        /// @return The group, to add more presets.
        public Group add(final String name, final Runnable onPress) {
            final LinkButton button = new LinkButton(name);
            button.addStyleName(BLOCK + "__setting");
            button.setOnClick(onPress);
            panel.add(button);
            return this;
        }
    }
}
