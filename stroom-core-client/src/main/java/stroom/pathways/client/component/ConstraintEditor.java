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

package stroom.pathways.client.component;

import stroom.item.client.SelectionBox;
import stroom.pathways.shared.pathway.ConstraintValueType;
import stroom.widget.component.client.Component;
import stroom.widget.form.client.TextBox;
import stroom.widget.tickbox.client.TickBox;

import com.google.gwt.core.client.GWT;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Widget;

/// The New and Edit Constraint dialog's content, for a node of a pathway: the constraint's
/// name, the type of its value, the value as text and whether it is optional.
public class ConstraintEditor extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    TextBox name;
    @UiField
    SelectionBox<ConstraintValueType> type;
    @UiField
    TextBox value;
    @UiField
    TickBox optional;

    /// Makes the content.
    public ConstraintEditor() {
        initWidget(BINDER.createAndBindUi(this));
        type.addItems(ConstraintValueType.values());
    }

    /// Puts the focus in the name.
    public void focus() {
        name.setFocus(true);
    }

    /// @return The name entered.
    public String getName() {
        return name.getText();
    }

    /// @param name The constraint's name.
    public void setName(final String name) {
        this.name.setText(name);
    }

    /// @return The type of value chosen.
    public ConstraintValueType getType() {
        return type.getValue();
    }

    /// @param type The type of the constraint's value.
    public void setType(final ConstraintValueType type) {
        this.type.setValue(type);
    }

    /// @return The value entered, as text.
    public String getValue() {
        return value.getValue();
    }

    /// @param value The constraint's value, as text.
    public void setValue(final String value) {
        this.value.setValue(value);
    }

    /// @return Whether the constraint is optional.
    public boolean isOptional() {
        return optional.getValue();
    }

    /// @param optional Whether the constraint is optional.
    public void setOptional(final boolean optional) {
        this.optional.setValue(optional);
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, ConstraintEditor> {

    }
}
