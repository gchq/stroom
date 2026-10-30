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
import stroom.query.api.datasource.ConditionSet;
import stroom.query.api.datasource.FieldType;
import stroom.query.api.datasource.QueryField;
import stroom.util.shared.NullSafe;
import stroom.util.shared.string.CIKey;
import stroom.widget.component.client.Controller;
import stroom.widget.component.client.ControllerDialog;
import stroom.widget.component.client.HasControllerNode;
import stroom.widget.popup.client.PopupSize;
import stroom.widget.popup.client.PopupType;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import java.util.EnumSet;
import java.util.Set;
import java.util.function.Consumer;

/// Adds or edits one of the receipt rules' fields in a dialog ([FieldEditor]): its type and its
/// name, which must be given and must not be another field's (ignoring case).
public class FieldEditorController extends Controller {

    private static final Set<FieldType> VALID_FIELD_TYPES = EnumSet.of(
            FieldType.TEXT,
            FieldType.DATE,
            FieldType.INTEGER,
            FieldType.LONG,
            FieldType.FLOAT,
            FieldType.DOUBLE,
            FieldType.BOOLEAN,
            FieldType.IPV4_ADDRESS);

    private final FieldEditor view;
    private Set<CIKey> otherFieldNames = Set.of();

    @Inject
    public FieldEditorController(final EventBus eventBus, final FieldEditor view) {
        super(eventBus);
        this.view = view;
        initWidget(view);
        view.setTypes(VALID_FIELD_TYPES);
    }

    /// Shows the editor as a dialog, owned by `opener` until it is closed. OK checks the field,
    /// and gives it to `onOk` if it is valid; otherwise the dialog stays open.
    ///
    /// @param opener          What shows it.
    /// @param caption         The dialog's caption, e.g. "New Field".
    /// @param field           The field to edit, or a new one.
    /// @param otherFieldNames The names of the other fields, which this one may not take.
    /// @param onOk            What to do with the field when OK is pressed.
    public void edit(final HasControllerNode opener,
                     final String caption,
                     final QueryField field,
                     final Set<CIKey> otherFieldNames,
                     final Consumer<QueryField> onOk) {
        this.otherFieldNames = otherFieldNames;
        view.setType(field.getFldType());
        view.setName(field.getFldName());
        ControllerDialog.showOnce(opener, this)
                .popupType(PopupType.OK_CANCEL_DIALOG)
                .popupSize(PopupSize.resizableX())
                .caption(caption)
                .onShow(e -> view.focus())
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

    // The field entered, or null (with a warning) if it has no name or another field's
    private QueryField write() {
        final String fieldName = NullSafe.trim(view.getName());
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
                .fldType(view.getType())
                .queryable(true)
                .conditionSet(ConditionSet.RECEIPT_POLICY_CONDITIONS)
                .build();
    }
}
