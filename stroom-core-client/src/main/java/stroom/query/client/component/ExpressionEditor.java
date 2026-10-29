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

package stroom.query.client.component;

import stroom.widget.button.client.ToolbarButton;
import stroom.widget.component.client.Component;

import com.google.gwt.core.client.GWT;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Widget;

/// An expression's editor, which its [ExpressionEditorController] fills: a toolbar of the
/// expression's actions (add a term or an operator, duplicate, disable and delete the selected
/// item) over its tree ([ExpressionTree]), which scrolls.
public class ExpressionEditor extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    FlowPanel root;
    @UiField
    ToolbarButton addTermButton;
    @UiField
    ToolbarButton addOperatorButton;
    @UiField
    ToolbarButton copyButton;
    @UiField
    ToolbarButton disableItemButton;
    @UiField
    ToolbarButton deleteItemButton;
    @UiField
    ExpressionTree expressionTree;

    private Runnable onAddTerm = () -> {
    };
    private Runnable onAddOperator = () -> {
    };
    private Runnable onCopy = () -> {
    };
    private Runnable onDisable = () -> {
    };
    private Runnable onDelete = () -> {
    };

    /// Makes the editor, with its tree.
    public ExpressionEditor() {
        initWidget(BINDER.createAndBindUi(this));
        addTermButton.setOnClick(() -> onAddTerm.run());
        addOperatorButton.setOnClick(() -> onAddOperator.run());
        copyButton.setOnClick(() -> onCopy.run());
        disableItemButton.setOnClick(() -> onDisable.run());
        deleteItemButton.setOnClick(() -> onDelete.run());
    }

    /// @return The expression's tree, which its controller attaches the tree's controller to.
    public ExpressionTree getExpressionTree() {
        return expressionTree;
    }

    /// @param framed Whether the editor is framed as a control is (`stroom-control`), as it is
    ///               unless its owner frames it.
    public void setFramed(final boolean framed) {
        root.setStyleName("stroom-control", framed);
    }

    /// @param canAdd Whether terms and operators can be added.
    public void setCanAdd(final boolean canAdd) {
        addTermButton.setEnabled(canAdd);
        addOperatorButton.setEnabled(canAdd);
    }

    /// @param canChangeItem Whether the selected item can be duplicated, disabled and deleted.
    /// @param enableItem    Whether the selected item is disabled, so the Disable button enables it.
    public void setItemActions(final boolean canChangeItem, final boolean enableItem) {
        copyButton.setEnabled(canChangeItem);
        disableItemButton.setEnabled(canChangeItem);
        // The title is the button's name, so it stays when the button is disabled; it follows the
        // selected item, enabling a disabled one
        disableItemButton.setTitle((enableItem
                ? "Enable"
                : "Disable") + " Selected Expression Item");
        deleteItemButton.setEnabled(canChangeItem);
    }

    /// Puts the focus on Add Term.
    public void focus() {
        addTermButton.focus();
    }

    /// @param onAddTerm Run when Add Term is pressed.
    public void setOnAddTerm(final Runnable onAddTerm) {
        this.onAddTerm = onAddTerm;
    }

    /// @param onAddOperator Run when Add Operator is pressed.
    public void setOnAddOperator(final Runnable onAddOperator) {
        this.onAddOperator = onAddOperator;
    }

    /// @param onCopy Run when Duplicate is pressed.
    public void setOnCopy(final Runnable onCopy) {
        this.onCopy = onCopy;
    }

    /// @param onDisable Run when Disable (or Enable) is pressed.
    public void setOnDisable(final Runnable onDisable) {
        this.onDisable = onDisable;
    }

    /// @param onDelete Run when Delete is pressed.
    public void setOnDelete(final Runnable onDelete) {
        this.onDelete = onDelete;
    }



    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, ExpressionEditor> {

    }
}
