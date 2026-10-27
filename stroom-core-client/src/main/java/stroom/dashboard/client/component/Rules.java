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

/// A table's conditional formatting rules: the list of rules, and the actions on them (add, edit,
/// copy, enable or disable, delete, move), which are put on the list's toolbar.
/// [RulesController] fills it and does the actions.
public class Rules extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    private final Map<RulesAction, ButtonView> buttons = new EnumMap<>(RulesAction.class);
    private Consumer<RulesAction> onAction = action -> {
    };

    @UiField
    RuleList ruleList;

    /// Makes the tab, with no rules.
    public Rules() {
        initWidget(BINDER.createAndBindUi(this));
        final Toolbar toolbar = ruleList.getToolbar();
        toolbar.setName("Conditional formatting rule actions");
        addButton(toolbar, RulesAction.ADD, SvgImage.ADD, "New Formatting Rule");
        addButton(toolbar, RulesAction.EDIT, SvgImage.EDIT, "Edit Selected Formatting Rule");
        addButton(toolbar, RulesAction.COPY, SvgImage.COPY, "Duplicate Selected Formatting Rule");
        addButton(toolbar, RulesAction.TOGGLE, SvgImage.DISABLE, "Disable Selected Formatting Rule");
        addButton(toolbar, RulesAction.DELETE, SvgImage.DELETE, "Delete Selected Formatting Rule");
        addButton(toolbar, RulesAction.MOVE_UP, SvgImage.UP, "Move Formatting Rule Up");
        addButton(toolbar, RulesAction.MOVE_DOWN, SvgImage.DOWN, "Move Formatting Rule Down");
        setAvailableActions(EnumSet.noneOf(RulesAction.class));
    }

    /// @return The list of rules.
    public RuleList getRuleList() {
        return ruleList;
    }

    /// Puts the focus on the New button.
    public void focusAdd() {
        buttons.get(RulesAction.ADD).focus();
    }

    /// @param onAction Given each action the user takes.
    public void setOnAction(final Consumer<RulesAction> onAction) {
        this.onAction = onAction;
    }

    /// Sets the actions that apply to the rule selected; the others are disabled.
    ///
    /// @param actions The actions that apply.
    public void setAvailableActions(final Set<RulesAction> actions) {
        buttons.forEach((action, button) -> button.setEnabled(actions.contains(action)));
    }

    /// Names the enable or disable action for the rule selected.
    ///
    /// @param selectedEnabled Whether the rule selected is enabled.
    public void setSelectedEnabled(final boolean selectedEnabled) {
        buttons.get(RulesAction.TOGGLE).setTitle(selectedEnabled
                ? "Disable Selected Formatting Rule"
                : "Enable Selected Formatting Rule");
    }

    private void addButton(final Toolbar toolbar, final RulesAction action, final SvgImage icon, final String title) {
        final ButtonView button = toolbar.button(icon, title, () -> onAction.accept(action));
        buttons.put(action, button);
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, Rules> {

    }


    // --------------------------------------------------------------------------------


    /// An action on the rules.
    public enum RulesAction {
        /// Adds a rule.
        ADD,
        /// Edits the rule selected.
        EDIT,
        /// Duplicates the rule selected.
        COPY,
        /// Enables or disables the rule selected.
        TOGGLE,
        /// Deletes the rule selected.
        DELETE,
        /// Moves the rule selected up.
        MOVE_UP,
        /// Moves the rule selected down.
        MOVE_DOWN
    }
}
