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

package stroom.query.client;

import stroom.item.client.SelectionBox;
import stroom.query.api.ExpressionOperator.Op;

import com.google.gwt.core.client.GWT;
import com.google.gwt.core.client.Scheduler;
import com.google.gwt.dom.client.Element;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Widget;

/// Edits an expression's operator (AND, OR, NOT) in place, as a term is edited. Its markup is
/// `OperatorEditor.ui.xml`.
public class OperatorEditor extends Composite {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    FlowPanel layout;
    // The row of its field
    @UiField
    FlowPanel inner;
    @UiField
    SelectionBox<Op> listBox;
    private Operator operator;
    private boolean reading;
    private boolean editing;
    private Runnable onChange;

    /// Makes the editor, with every operator to choose from, hidden until [#startEdit] shows it.
    public OperatorEditor() {
        initWidget(BINDER.createAndBindUi(this));
        listBox.addItems(Op.values());
        // Its own child's handler, which goes with it
        listBox.addValueChangeHandler(event -> onChange());
    }

    /// Shows the editor for an operator, with its operator chosen; a change of choice changes it in
    /// place. Does nothing while it is already editing one.
    ///
    /// @param operator The operator to edit.
    public void startEdit(final Operator operator) {
        if (!editing) {
            reading = true;

            this.operator = operator;

            // Select the current value.
            listBox.setValue(operator.getOp());

            Scheduler.get().scheduleDeferred(() -> layout.setVisible(true));

            reading = false;
            editing = true;
        }
    }

    /// Does nothing: a change is written to the operator as it is made.
    public void write() {
        // A change is written as it is made
    }

    /// Hides the editor, if it is editing.
    public void endEdit() {
        if (editing) {
            layout.setVisible(false);

            editing = false;
        }
    }

    private void onChange() {
        if (!reading && operator != null) {
            operator.setOp(listBox.getValue());
            if (onChange != null) {
                onChange.run();
            }
        }
    }

    /// @return The row of its fields, on which the item it edits draws its looks (e.g. being
    /// selected).
    public Element getRow() {
        return inner.getElement();
    }

    /// @param onChange Run when the user changes the operator (which is changed in place), or null
    ///                 for nothing.
    public void setOnChange(final Runnable onChange) {
        this.onChange = onChange;
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, OperatorEditor> {

    }
}
