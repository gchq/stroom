/*
 * Copyright 2021 Crown Copyright
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

package stroom.dashboard.client.component;

import stroom.svg.shared.SvgImage;
import stroom.widget.button.client.ButtonView;
import stroom.widget.button.client.Toolbar;
import stroom.widget.component.client.Component;

import com.google.gwt.core.client.GWT;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Widget;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/// A pane's selection handlers (its Selection Query or Selection Filter): the list of handlers, and
/// the actions on them (add, edit, copy, enable or disable, delete, move), which are put on the
/// list's toolbar. [SelectionHandlersController] fills it and does the actions.
public class SelectionHandlers extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    private final Map<SelectionHandlersAction, ButtonView> buttons = new EnumMap<>(SelectionHandlersAction.class);
    private Consumer<SelectionHandlersAction> onAction = action -> {
    };

    @UiField
    SelectionHandlerList handlerList;

    /// Makes the tab, with no handlers.
    public SelectionHandlers() {
        initWidget(BINDER.createAndBindUi(this));
        final Toolbar toolbar = handlerList.getToolbar();
        toolbar.setName("Selection handler actions");
        addButton(toolbar, SelectionHandlersAction.ADD, SvgImage.ADD, "New Selection Handler");
        addButton(toolbar, SelectionHandlersAction.EDIT, SvgImage.EDIT, "Edit Selected Selection Handler");
        addButton(toolbar, SelectionHandlersAction.COPY, SvgImage.COPY, "Duplicate Selected Selection Handler");
        addButton(toolbar, SelectionHandlersAction.TOGGLE, SvgImage.DISABLE, "Disable Selected Selection Handler");
        addButton(toolbar, SelectionHandlersAction.DELETE, SvgImage.DELETE, "Delete Selected Selection Handler");
        addButton(toolbar, SelectionHandlersAction.MOVE_UP, SvgImage.UP, "Move Selection Handler Up");
        addButton(toolbar, SelectionHandlersAction.MOVE_DOWN, SvgImage.DOWN, "Move Selection Handler Down");
        setAvailableActions(EnumSet.noneOf(SelectionHandlersAction.class));
    }

    /// @return The list of handlers.
    public SelectionHandlerList getHandlerList() {
        return handlerList;
    }

    /// Puts the focus on the New button.
    public void focusAdd() {
        buttons.get(SelectionHandlersAction.ADD).focus();
    }

    /// @param onAction Given each action the user takes.
    public void setOnAction(final Consumer<SelectionHandlersAction> onAction) {
        this.onAction = onAction;
    }

    /// Sets the actions that apply to the handler selected; the others are disabled.
    ///
    /// @param actions The actions that apply.
    public void setAvailableActions(final Set<SelectionHandlersAction> actions) {
        buttons.forEach((action, button) -> button.setEnabled(actions.contains(action)));
    }

    /// Names the enable or disable action for the handler selected.
    ///
    /// @param selectedEnabled Whether the handler selected is enabled.
    public void setSelectedEnabled(final boolean selectedEnabled) {
        buttons.get(SelectionHandlersAction.TOGGLE).setTitle(selectedEnabled
                ? "Disable Selected Selection Handler"
                : "Enable Selected Selection Handler");
    }

    private void addButton(final Toolbar toolbar,
                           final SelectionHandlersAction action,
                           final SvgImage icon,
                           final String title) {
        final ButtonView button = toolbar.button(icon, title, () -> onAction.accept(action));
        buttons.put(action, button);
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, SelectionHandlers> {

    }


    // --------------------------------------------------------------------------------


    /// An action on the selection handlers.
    public enum SelectionHandlersAction {
        /// Adds a handler.
        ADD,
        /// Edits the handler selected.
        EDIT,
        /// Duplicates the handler selected.
        COPY,
        /// Enables or disables the handler selected.
        TOGGLE,
        /// Deletes the handler selected.
        DELETE,
        /// Moves the handler selected up.
        MOVE_UP,
        /// Moves the handler selected down.
        MOVE_DOWN
    }
}
