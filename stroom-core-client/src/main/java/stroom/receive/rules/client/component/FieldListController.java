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

import stroom.alert.client.event.ConfirmEvent;
import stroom.docref.DocRef;
import stroom.query.api.datasource.QueryField;
import stroom.receive.rules.shared.ReceiveDataRules;
import stroom.ui.config.client.UiConfigCache;
import stroom.util.shared.NullSafe;
import stroom.util.shared.string.CIKey;
import stroom.widget.component.client.Contexts;
import stroom.widget.component.client.Controller;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/// The receipt rules' Fields tab ([FieldList]): the fields the rules' expressions can use. New and
/// Edit open a field's editor, Delete deletes those selected once confirmed, unless the rules are
/// read only ([Contexts#READ_ONLY]); which fields are obfuscated comes from the UI config.
public class FieldListController extends Controller {

    private final FieldList view;
    private final Provider<FieldEditorController> fieldEditorProvider;
    private final List<QueryField> fields = new ArrayList<>();
    private Set<CIKey> obfuscatedFields;

    /// Made by GIN, with its content.
    ///
    /// @param eventBus            The application's event bus.
    /// @param view                The content.
    /// @param fieldEditorProvider Makes a field's editor.
    /// @param uiConfigCache       Gives the obfuscated fields.
    @Inject
    public FieldListController(final EventBus eventBus,
                               final FieldList view,
                               final Provider<FieldEditorController> fieldEditorProvider,
                               final UiConfigCache uiConfigCache) {
        super(eventBus);
        this.view = view;
        this.fieldEditorProvider = fieldEditorProvider;
        initWidget(view);

        view.addColumns(queryField -> obfuscatedFields.contains(CIKey.of(queryField.getFldName())));
        view.setOnNew(this::onAdd);
        view.setOnEdit(this::onEdit);
        view.setOnRemove(this::onRemove);
        view.setOnSortChange(this::refresh);
        watch(Contexts.READ_ONLY, view::setReadOnly);

        uiConfigCache.get(extendedUiConfig -> {
            obfuscatedFields = CIKey.setOf(extendedUiConfig.getObfuscatedFields());
            refresh();
        });
    }

    private void onAdd() {
        final Set<CIKey> otherNames = fields.stream()
                .map(QueryField::getFldName)
                .map(CIKey::of)
                .collect(Collectors.toSet());

        fieldEditorProvider.get().edit(this, "New Field", QueryField.createText(""), otherNames, newField -> {
            fields.add(newField);
            refresh();
            markChanged();
        });
    }

    private void onEdit() {
        final QueryField field = view.getSelectionModel().getSelected();
        if (field != null) {
            final Set<CIKey> otherNames = fields.stream()
                    .map(QueryField::getFldName)
                    .map(CIKey::of)
                    .collect(Collectors.toSet());

            otherNames.remove(CIKey.of(field.getFldName()));

            fieldEditorProvider.get().edit(this, "Edit Field", field, otherNames, newField -> {
                fields.set(fields.indexOf(field), newField);
                refresh();
                markChanged();
            });
        }
    }

    private void onRemove() {
        final List<QueryField> list = view.getSelectionModel().getSelectedItems();
        if (NullSafe.hasItems(list)) {
            String message = "Are you sure you want to delete the selected field?";
            if (list.size() > 1) {
                message = "Are you sure you want to delete the selected fields?";
            }

            ConfirmEvent.fire(this, message, result -> {
                if (result) {
                    fields.removeAll(list);
                    view.getSelectionModel().clear();
                    refresh();
                    markChanged();
                }
            });
        }
    }

    private void refresh() {
        if (!fields.isEmpty()) {
            final Comparator<QueryField> comparator = view.getComparator();
            NullSafe.consume(comparator, fields::sort);
        }
        view.setData(fields);
    }

    /// Shows the rules' fields.
    ///
    /// @param docRef   The rules' document.
    /// @param document The rules.
    public void read(final DocRef docRef, final ReceiveDataRules document) {
        if (document != null) {
            fields.clear();
            fields.addAll(document.getFields());
            refresh();
        }
    }

    /// @param document The rules.
    /// @return A copy of them with the fields shown.
    public ReceiveDataRules write(final ReceiveDataRules document) {
        return document.copy().fields(new ArrayList<>(fields)).build();
    }

    /// @return The fields shown, as they are now.
    public List<QueryField> getFields() {
        return fields;
    }
}
