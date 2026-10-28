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

import stroom.svg.shared.SvgImage;
import stroom.widget.button.client.ButtonView;
import stroom.widget.button.client.Toolbar;
import stroom.widget.component.client.Component;

import com.google.gwt.core.client.GWT;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.IsWidget;
import com.google.gwt.user.client.ui.SimplePanel;
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

    private final Map<RetentionRuleAction, ButtonView> buttons = new EnumMap<>(RetentionRuleAction.class);
    private final Set<RetentionRuleAction> available = EnumSet.noneOf(RetentionRuleAction.class);
    private Consumer<RetentionRuleAction> onAction = action -> {
    };

    @UiField
    SimplePanel table;
    @UiField
    SimplePanel expression;

    /// Makes the screen, with no list or expression yet.
    public DataRetentionPolicy() {
        initWidget(BINDER.createAndBindUi(this));
    }

    /// Shows the list of rules, and puts the actions on its toolbar.
    ///
    /// @param ruleList The list, a controller owned by this component's controller.
    /// @param toolbar  The list's toolbar.
    public void setRuleList(final IsWidget ruleList, final Toolbar toolbar) {
        table.setWidget(ruleList);
        toolbar.setName("Retention rule actions");
        addButton(toolbar, RetentionRuleAction.SAVE, SvgImage.SAVE, "Save Rules");
        addButton(toolbar, RetentionRuleAction.ADD_ABOVE, SvgImage.ADD_ABOVE, "Add Rule Above");
        addButton(toolbar, RetentionRuleAction.EDIT, SvgImage.EDIT, "Edit Selected Rule");
        addButton(toolbar, RetentionRuleAction.COPY, SvgImage.COPY, "Duplicate Selected Rule");
        addButton(toolbar, RetentionRuleAction.DELETE, SvgImage.DELETE, "Delete Selected Rule");
        addButton(toolbar, RetentionRuleAction.MOVE_UP, SvgImage.UP, "Move Rule Up");
        addButton(toolbar, RetentionRuleAction.MOVE_DOWN, SvgImage.DOWN, "Move Rule Down");
    }

    /// @param expressionTree The selected rule's expression, a controller owned by this component's controller.
    public void setExpression(final IsWidget expressionTree) {
        expression.setWidget(expressionTree);
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
        final ButtonView button = toolbar.button(icon, title, () -> onAction.accept(action));
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
