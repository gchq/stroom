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

import stroom.alert.client.event.AlertEvent;
import stroom.item.client.SelectionBox;
import stroom.query.api.datasource.ConditionSet;
import stroom.query.api.datasource.FieldType;
import stroom.query.api.datasource.QueryField;
import stroom.util.shared.NullSafe;
import stroom.util.shared.string.CIKey;
import stroom.widget.component.client.Component;
import stroom.widget.component.client.ComponentDialog;
import stroom.widget.component.client.HasComponentNode;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupType;

import com.google.gwt.core.client.GWT;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.TextBox;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import java.util.EnumSet;
import java.util.Set;
import java.util.function.Consumer;

/// A dialog to add or edit one of the receipt rules' fields: its type and its name, which must be
/// given and must not be another field's (ignoring case).
public class FieldEditor extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    private static final Set<FieldType> VALID_FIELD_TYPES = EnumSet.of(
            FieldType.TEXT,
            FieldType.DATE,
            FieldType.INTEGER,
            FieldType.LONG,
            FieldType.FLOAT,
            FieldType.DOUBLE,
            FieldType.BOOLEAN,
            FieldType.IPV4_ADDRESS);

    private Set<CIKey> otherFieldNames = Set.of();

    @UiField
    SelectionBox<FieldType> type;
    @UiField
    TextBox name;

    @Inject
    public FieldEditor(final EventBus eventBus) {
        super(eventBus);
        initWidget(BINDER.createAndBindUi(this));
        type.addItems(VALID_FIELD_TYPES);
    }

    /// Shows this editor as a dialog, owned by `opener` until it is closed. OK checks the field,
    /// and gives it to `onOk` if it is valid; otherwise the dialog stays open.
    ///
    /// @param opener          What shows it.
    /// @param caption         The dialog's caption, e.g. "New Field".
    /// @param field           The field to edit, or a new one.
    /// @param otherFieldNames The names of the other fields, which this one may not take.
    /// @param onOk            What to do with the field when OK is pressed.
    public void edit(final HasComponentNode opener,
                     final String caption,
                     final QueryField field,
                     final Set<CIKey> otherFieldNames,
                     final Consumer<QueryField> onOk) {
        read(field, otherFieldNames);
        ComponentDialog.showOnce(opener, this)
                .popupType(PopupType.OK_CANCEL_DIALOG)
                .popupSize(PopupSize.resizableX())
                .caption(caption)
                .onShow(e -> type.focus())
                .onHideRequest(e -> {
                    if (e.isOk()) {
                        final QueryField written = write();
                        if (written != null) {
                            onOk.accept(written);
                            e.hide();
                        } else {
                            e.reset();
                        }
                    } else {
                        e.hide();
                    }
                })
                .fire();
    }

    // Shows a field, to be checked against the others' names
    void read(final QueryField field, final Set<CIKey> otherFieldNames) {
        this.otherFieldNames = otherFieldNames;
        type.setValue(field.getFldType());
        name.setText(field.getFldName());
    }

    // The field shown, or null (with a warning) if it has no name or another field's
    QueryField write() {
        final String fieldName = NullSafe.trim(name.getText());
        if (fieldName.isEmpty()) {
            AlertEvent.fireWarn(this, "A field must have a name", null);
            return null;
        }
        if (otherFieldNames.contains(CIKey.of(fieldName))) {
            AlertEvent.fireWarn(this,
                    "A field with this name already exists. Field names are case insensitive.",
                    null);
            return null;
        }
        return QueryField
                .builder()
                .fldName(fieldName)
                .fldType(type.getValue())
                .queryable(true)
                .conditionSet(ConditionSet.RECEIPT_POLICY_CONDITIONS)
                .build();
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, FieldEditor> {

    }
}
