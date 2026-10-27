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
import stroom.query.api.datasource.FieldType;
import stroom.widget.component.client.Component;
import stroom.widget.form.client.TextBox;

import com.google.gwt.core.client.GWT;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Widget;

import java.util.Collection;

/// The content of the dialog that adds or edits one of the receipt rules' fields: its type and its
/// name. [FieldEditorController] shows it and checks what is entered.
public class FieldEditor extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    SelectionBox<FieldType> type;
    @UiField
    TextBox name;

    /// Makes an empty editor.
    public FieldEditor() {
        initWidget(BINDER.createAndBindUi(this));
    }

    /// @param types The types a field may have, in the order they are offered.
    public void setTypes(final Collection<FieldType> types) {
        type.clear();
        type.addItems(types);
    }

    /// @param fieldType The field's type.
    public void setType(final FieldType fieldType) {
        type.setValue(fieldType);
    }

    /// @return The type chosen.
    public FieldType getType() {
        return type.getValue();
    }

    /// @param fieldName The field's name.
    public void setName(final String fieldName) {
        name.setText(fieldName);
    }

    /// @return The name entered, as typed.
    public String getName() {
        return name.getText();
    }

    /// Puts the focus on its first field, the type.
    public void focus() {
        type.focus();
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, FieldEditor> {

    }
}
