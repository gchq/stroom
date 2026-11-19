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

package stroom.receive.rules.client.component;

import stroom.query.client.component.ExpressionTree;
import stroom.svg.shared.SvgImage;
import stroom.widget.button.client.IsButton;
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

/// The receipt rules' Rules tab: the list of rules above the selected rule's expression, and the
/// actions on the rules (add, edit, copy, enable or disable, delete, move), which are put on the
/// list's toolbar. [RuleSetSettingsController] fills it and does the actions. While it is read only
/// every action is disabled.
public class RuleSetSettings extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    private static final String ENABLE_DISABLE_TITLE = "Enable or Disable Selected Rules";

    private final Map<RuleAction, IsButton> buttons = new EnumMap<>(RuleAction.class);
    private final Set<RuleAction> available = EnumSet.noneOf(RuleAction.class);
    private Consumer<RuleAction> onAction = action -> {
    };

    @UiField
    RuleSetList ruleList;
    @UiField
    ExpressionTree expressionTree;

    /// Makes the screen, with no rules or expression yet, and the actions on the list's toolbar.
    public RuleSetSettings() {
        initWidget(BINDER.createAndBindUi(this));
        final Toolbar toolbar = ruleList.getToolbar();
        toolbar.setName("Receipt rule actions");
        addButton(toolbar, RuleAction.ADD, SvgImage.ADD, "New Rule");
        addButton(toolbar, RuleAction.EDIT, SvgImage.EDIT, "Edit Selected Rule");
        addButton(toolbar, RuleAction.COPY, SvgImage.COPY, "Duplicate Selected Rule");
        addButton(toolbar, RuleAction.TOGGLE, SvgImage.DISABLE, ENABLE_DISABLE_TITLE);
        addButton(toolbar, RuleAction.DELETE, SvgImage.DELETE, "Delete Selected Rules");
        addButton(toolbar, RuleAction.MOVE_UP, SvgImage.UP, "Move Rule Up");
        addButton(toolbar, RuleAction.MOVE_DOWN, SvgImage.DOWN, "Move Rule Down");
    }

    /// @return The list of rules, which its controller attaches the list's controller to.
    public RuleSetList getRuleList() {
        return ruleList;
    }

    /// @return The expression's tree, which its controller attaches the tree's controller to.
    public ExpressionTree getExpressionTree() {
        return expressionTree;
    }

    /// @param onAction Given each action the user takes.
    public void setOnAction(final Consumer<RuleAction> onAction) {
        this.onAction = onAction;
    }

    /// Sets the actions that apply to the rules selected; the others are disabled, as all are while
    /// it is read only.
    ///
    /// @param actions The actions that apply.
    public void setAvailableActions(final Set<RuleAction> actions) {
        available.clear();
        available.addAll(actions);
        updateButtons();
    }

    /// Names the enable or disable action for the rules selected.
    ///
    /// @param selectedEnabled Whether the rules selected are enabled, or null if none is selected or
    ///                        they differ.
    /// @param several         Whether more than one rule is selected.
    public void setSelectedEnabled(final Boolean selectedEnabled, final boolean several) {
        final String title;
        if (selectedEnabled != null) {
            title = selectedEnabled
                    ? "Disable Selected Rules"
                    : "Enable Selected Rules";
        } else if (several) {
            title = ENABLE_DISABLE_TITLE + " (selected rules must share the same enabled state)";
        } else {
            title = ENABLE_DISABLE_TITLE;
        }
        buttons.get(RuleAction.TOGGLE).setTitle(title);
    }

    @Override
    protected void onReadOnlyChange(final boolean readOnly) {
        updateButtons();
    }

    private void addButton(final Toolbar toolbar, final RuleAction action, final SvgImage icon, final String title) {
        final IsButton button = toolbar.button(icon, title, () -> onAction.accept(action));
        button.setEnabled(false);
        buttons.put(action, button);
    }

    private void updateButtons() {
        buttons.forEach((action, button) -> button.setEnabled(!isReadOnly() && available.contains(action)));
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, RuleSetSettings> {

    }


    // --------------------------------------------------------------------------------


    /// An action on the rules.
    public enum RuleAction {
        ADD,
        EDIT,
        COPY,
        TOGGLE,
        DELETE,
        MOVE_UP,
        MOVE_DOWN
    }
}
