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

import stroom.svg.shared.SvgImage;

import com.google.gwt.uibinder.client.UiConstructor;
import com.google.gwt.user.client.ui.FlowPanel;

import java.util.function.Consumer;

/// A named group of icon buttons that sit together, e.g. a document's Save and Save As. The group
/// is one tab stop and the arrow keys move between its buttons (see [ButtonGroupFocus]); with two
/// or more buttons a screen reader announces it as a toolbar, by its name.
///
/// Buttons are made from an icon and a name, both always given, and start enabled:
///
/// ```
/// final Toolbar toolbar = new Toolbar("Dictionary actions");
/// final ToolbarButton save = toolbar.button(SvgImage.SAVE, "Save", this::save);
/// toolbar.toggle(SvgImage.FILTER, "Show Column Filters", this::setFiltering);
/// save.setEnabled(false);
/// ```
///
/// It can also be declared in a UiBinder template, with its buttons as children:
///
/// ```
/// <b:Toolbar ui:field="actions" name="Dictionary actions">
///     <b:ToolbarButton ui:field="save" svg="SAVE" title="Save"/>
/// </b:Toolbar>
/// ```
///
/// Buttons apart on a row (e.g. at either end) are separate toolbars, laid out by a [ToolbarRow].
/// A toolbar holds nothing outside its own elements, so it needs no clean-up.
public class Toolbar extends FlowPanel {

    private final ButtonGroupFocus groupFocus;

    /// @param name The toolbar's name, announced with it, e.g. "Dictionary actions".
    @UiConstructor
    public Toolbar(final String name) {
        setStyleName("toolbar");
        groupFocus = ButtonGroupFocus.install(getElement());
        groupFocus.setName(name);
    }

    /// Adds a button.
    ///
    /// @param icon    The button's icon.
    /// @param name    The button's name, shown as its tooltip and announced by screen readers.
    /// @param onClick What clicking it does.
    /// @return The button, e.g. to disable or hide it.
    public ToolbarButton button(final SvgImage icon, final String name, final Runnable onClick) {
        final ToolbarButton button = new ToolbarButton();
        button.setSvg(icon);
        button.setTitle(name);
        button.setOnClick(onClick);
        add(button);
        return button;
    }

    /// Adds a button that is pressed (on) or not, starting not pressed.
    ///
    /// @param icon     The button's icon.
    /// @param name     The button's name, shown as its tooltip and announced by screen readers.
    /// @param onToggle Given the button's new state when it is pressed.
    /// @return The button, e.g. to set its state.
    public ToolbarToggleButton toggle(final SvgImage icon,
                                      final String name,
                                      final Consumer<Boolean> onToggle) {
        final ToolbarToggleButton button = new ToolbarToggleButton();
        button.setSvg(icon);
        button.setTitle(name);
        button.setOnToggle(onToggle);
        add(button);
        return button;
    }

    /// Adds a refresh button, which also shows whether what it refreshes is refreshing and can be made
    /// to pause and resume updates (see [RefreshButton]). It names itself for what pressing it does.
    ///
    /// @param onClick What clicking it does.
    /// @return The button, e.g. to show that what it refreshes is refreshing.
    public RefreshButton refresh(final Runnable onClick) {
        final RefreshButton button = new RefreshButton();
        button.setOnClick(onClick);
        add(button);
        return button;
    }

    /// Adds a line between the buttons before and after it. They are still one group, so the arrow
    /// keys cross it.
    public void separator() {
        add(new ToolbarSeparator());
    }

    /// @param name The toolbar's name, announced with it.
    public void setName(final String name) {
        groupFocus.setName(name);
    }

    /// Puts the buttons in a column, which Up and Down move along rather than Left and Right.
    ///
    /// @param vertical Whether the buttons are in a column.
    public void setVertical(final boolean vertical) {
        // Laid out as a column by its aria-orientation, which the group sets
        groupFocus.setVertical(vertical);
    }

    @Override
    protected void onLoad() {
        super.onLoad();
        // Attaching the buttons resets their tab indexes (FocusWidget.onAttach)
        groupFocus.update();
    }
}
