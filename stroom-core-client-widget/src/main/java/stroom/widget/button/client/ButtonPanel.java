/*
 * Copyright 2016 Crown Copyright
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

import stroom.svg.client.Preset;

import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Widget;

import java.util.List;
import java.util.stream.StreamSupport;

/// A group of buttons that sit together, e.g. a toolbar. The group is one tab stop: the arrow keys
/// move between its buttons (see [ButtonGroupFocus]).
public class ButtonPanel extends FlowPanel {

    private final ButtonGroupFocus groupFocus;

    public ButtonPanel() {
        setStyleName("button-container");
        groupFocus = ButtonGroupFocus.install(getElement());
    }

    /// Names the group for screen readers, e.g. "Dictionary actions", as it is announced as a
    /// toolbar when it has more than one button.
    ///
    /// @param name The group's name.
    public void setAccessibleName(final String name) {
        groupFocus.setName(name);
    }

    @Override
    protected void onLoad() {
        super.onLoad();
        // Attaching the buttons resets their tab indexes (FocusWidget.onAttach)
        groupFocus.update();
    }

    public void addButton(final ButtonView view) {
        add(view.asWidget());
    }

    public void addButtons(final List<ButtonView> buttons) {
        for (final ButtonView buttonView : buttons) {
            addButton(buttonView);
        }
    }

    /// @deprecated Use a [Toolbar] and [Toolbar#button], with an icon and a name, instead of a preset.
    @Deprecated
    public ButtonView addButton(final Preset preset) {
        final ButtonView button = createButton(preset);
        add((Widget) button);
        return button;
    }

    public void removeButton(final ButtonView buttonView) {
        remove(buttonView.asWidget());
    }

    public boolean containsButton(final ButtonView buttonView) {
        if (buttonView == null) {
            return false;
        } else {
            return StreamSupport.stream(spliterator(), false)
                    .anyMatch(widget ->
                            widget.equals(buttonView));
        }
    }

    /// Makes a toolbar button (styled by toolbar.css) from a preset's icon, name and enabled state.
    ///
    /// @deprecated Use a [Toolbar] and [Toolbar#button], with an icon and a name, instead of a preset.
    @Deprecated
    public ButtonView createButton(final Preset preset) {
        final ToolbarButton button = new ToolbarButton();
        button.setSvg(preset.getSvgImage());
        button.setTitle(preset.getTitle());
        button.setEnabled(preset.isEnabled());
        return button;
    }

    /// Puts the buttons in a column, which Up and Down move along.
    ///
    /// @param vertical Whether the buttons are in a column.
    public void setVertical(final boolean vertical) {
        // Laid out as a column by its aria-orientation, which the group sets
        groupFocus.setVertical(vertical);
    }
}
