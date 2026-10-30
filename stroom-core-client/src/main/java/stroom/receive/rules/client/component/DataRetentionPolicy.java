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

/// The data retention rules' Rules tab: the list of rules above the selected rule's expression,
/// and the actions on the rules (save, add, edit, copy, delete, move), which are put on the list's
/// toolbar. [DataRetentionPolicyController] fills it and does the actions.
public class DataRetentionPolicy extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    private final Map<RetentionRuleAction, IsButton> buttons = new EnumMap<>(RetentionRuleAction.class);
    private final Set<RetentionRuleAction> available = EnumSet.noneOf(RetentionRuleAction.class);
    private Consumer<RetentionRuleAction> onAction = action -> {
    };

    @UiField
    DataRetentionPolicyList ruleList;
    @UiField
    ExpressionTree expressionTree;

    /// Makes the screen, with no rules or expression yet, and the actions on the list's toolbar.
    public DataRetentionPolicy() {
        initWidget(BINDER.createAndBindUi(this));
        final Toolbar toolbar = ruleList.getToolbar();
        toolbar.setName("Retention rule actions");
        addButton(toolbar, RetentionRuleAction.SAVE, SvgImage.SAVE, "Save Rules");
        addButton(toolbar, RetentionRuleAction.ADD_ABOVE, SvgImage.ADD_ABOVE, "Add Rule Above");
        addButton(toolbar, RetentionRuleAction.EDIT, SvgImage.EDIT, "Edit Selected Rule");
        addButton(toolbar, RetentionRuleAction.COPY, SvgImage.COPY, "Duplicate Selected Rule");
        addButton(toolbar, RetentionRuleAction.DELETE, SvgImage.DELETE, "Delete Selected Rule");
        addButton(toolbar, RetentionRuleAction.MOVE_UP, SvgImage.UP, "Move Rule Up");
        addButton(toolbar, RetentionRuleAction.MOVE_DOWN, SvgImage.DOWN, "Move Rule Down");
    }

    /// @return The list of rules, which its controller attaches the list's controller to.
    public DataRetentionPolicyList getRuleList() {
        return ruleList;
    }

    /// @return The expression's tree, which its controller attaches the tree's controller to.
    public ExpressionTree getExpressionTree() {
        return expressionTree;
    }

    /// @param onAction Given each action the user takes.
    public void setOnAction(final Consumer<RetentionRuleAction> onAction) {
        this.onAction = onAction;
    }

    /// Sets the actions that apply now, e.g. to the rule selected; the others are disabled.
    ///
    /// @param actions The actions that apply.
    public void setAvailableActions(final Set<RetentionRuleAction> actions) {
        available.clear();
        available.addAll(actions);
        buttons.forEach((action, button) -> button.setEnabled(available.contains(action)));
    }

    private void addButton(final Toolbar toolbar,
                           final RetentionRuleAction action,
                           final SvgImage icon,
                           final String title) {
        final IsButton button = toolbar.button(icon, title, () -> onAction.accept(action));
        button.setEnabled(false);
        buttons.put(action, button);
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, DataRetentionPolicy> {

    }


    // --------------------------------------------------------------------------------


    /// An action on the rules.
    public enum RetentionRuleAction {
        /// Saves the rules.
        SAVE,
        /// Adds a rule above the selected one, or at the top.
        ADD_ABOVE,
        /// Edits the selected rule.
        EDIT,
        /// Duplicates the selected rule.
        COPY,
        /// Deletes the selected rule.
        DELETE,
        /// Moves the selected rule up.
        MOVE_UP,
        /// Moves the selected rule down.
        MOVE_DOWN
    }
}
