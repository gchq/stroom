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

import stroom.item.client.SelectionBox;
import stroom.query.client.component.ExpressionEditor;
import stroom.receive.rules.shared.ReceiveAction;
import stroom.widget.component.client.Component;
import stroom.widget.form.client.TextBox;

import com.google.gwt.core.client.GWT;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Widget;

import java.util.Collection;

/// The content of the dialog that adds or edits a receipt rule: the expression that selects the
/// data it applies to, an optional name, and what to do with that data. [RuleEditorController]
/// shows it and checks the rule.
public class RuleEditor extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    ExpressionEditor expressionEditor;
    @UiField
    TextBox name;
    @UiField
    SelectionBox<ReceiveAction> action;

    /// Makes an empty editor.
    public RuleEditor() {
        initWidget(BINDER.createAndBindUi(this));
    }

    /// @return The expression's editor, which its controller attaches the editor's controller to.
    public ExpressionEditor getExpressionEditor() {
        return expressionEditor;
    }

    /// @param actions The actions a rule may take, in the order they are offered.
    public void setActions(final Collection<ReceiveAction> actions) {
        action.clear();
        action.addItems(actions);
    }

    /// @param ruleName The rule's name, or null.
    public void setName(final String ruleName) {
        name.setText(ruleName);
    }

    /// @return The name entered.
    public String getName() {
        return name.getText();
    }

    /// @param ruleAction The rule's action.
    public void setAction(final ReceiveAction ruleAction) {
        action.setValue(ruleAction);
    }

    /// @return The action chosen.
    public ReceiveAction getAction() {
        return action.getValue();
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, RuleEditor> {

    }
}
