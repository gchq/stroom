/*
 * Copyright 2019 Crown Copyright
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

package stroom.search.solr.client.component;

import stroom.alert.client.event.AlertEvent;
import stroom.search.solr.shared.SolrIndexField;
import stroom.widget.component.client.Controller;
import stroom.widget.component.client.ControllerDialog;
import stroom.widget.component.client.HasControllerNode;
import stroom.widget.popup.client.PopupSize;
import stroom.widget.popup.client.PopupType;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/// The New and Edit Field dialog ([SolrIndexFieldEditor]) of a Solr index, made for one use.
public class SolrIndexFieldEditorController extends Controller {

    private final SolrIndexFieldEditor view;

    /// Made by GIN, with its content.
    ///
    /// @param eventBus The application's event bus.
    /// @param view     The content.
    @Inject
    public SolrIndexFieldEditorController(final EventBus eventBus, final SolrIndexFieldEditor view) {
        super(eventBus);
        this.view = view;
        initWidget(view);
    }

    /// Edits a field in a dialog, owned by `opener` until it is closed. On OK a field with no
    /// name, a name Solr doesn't allow or the name of another field is refused with a warning and
    /// the dialog stays open; otherwise the field is given to `onOk`.
    ///
    /// @param opener          What shows it.
    /// @param caption         The dialog's caption, e.g. "New Field".
    /// @param indexField      The field to start from.
    /// @param otherFieldNames The names of the index's other fields.
    /// @param fieldTypes      The field types Solr offers.
    /// @param onOk            Given the field entered.
    public void edit(final HasControllerNode opener,
                     final String caption,
                     final SolrIndexField indexField,
                     final Set<String> otherFieldNames,
                     final List<String> fieldTypes,
                     final Consumer<SolrIndexField> onOk) {
        read(indexField, fieldTypes);
        ControllerDialog.showOnce(opener, this)
                .popupType(PopupType.OK_CANCEL_DIALOG)
                .popupSize(PopupSize.resizable(300, 450))
                .caption(caption)
                .onShow(e -> view.focus())
                .onHideRequest(e -> {
                    if (e.isOk()) {
                        final String problem = problemWith(view.getFieldName().trim(), otherFieldNames);
                        if (problem != null) {
                            AlertEvent.fireWarn(this, problem, e::reset);
                            return;
                        }
                        onOk.accept(write());
                    }
                    e.hide();
                })
                .fire();
    }

    /// Says what is wrong with a field's name, if anything.
    ///
    /// @param name            The name, trimmed.
    /// @param otherFieldNames The names of the index's other fields.
    /// @return Why the name can't be used, or null if it can.
    static String problemWith(final String name, final Set<String> otherFieldNames) {
        if (name.isEmpty()) {
            return "An index field must have a name";
        }
        if (!name.matches(SolrIndexField.VALID_FIELD_NAME_PATTERN)) {
            return "An index field name must conform to the pattern '"
                   + SolrIndexField.VALID_FIELD_NAME_PATTERN + "'";
        }
        if (otherFieldNames.contains(name)) {
            return "An index field with this name already exists";
        }
        return null;
    }

    private void read(final SolrIndexField indexField, final List<String> fieldTypes) {
        view.setFieldTypes(fieldTypes);
        view.setType(indexField.getFldType());
        view.setFieldName(indexField.getFldName());
        view.setFieldType(indexField.getNativeType());
        view.setDefaultValue(indexField.getDefaultValue());
        view.setStored(indexField.isStored());
        view.setIndexed(indexField.isIndexed());
        view.setUninvertible(indexField.isUninvertible());
        view.setDocValues(indexField.isDocValues());
        view.setMultiValued(indexField.isMultiValued());
        view.setRequired(indexField.isRequired());
        view.setOmitNorms(indexField.isOmitNorms());
        view.setOmitTermFreqAndPositions(indexField.isOmitTermFreqAndPositions());
        view.setOmitPositions(indexField.isOmitPositions());
        view.setTermVectors(indexField.isTermVectors());
        view.setTermPositions(indexField.isTermPositions());
        view.setTermOffsets(indexField.isTermOffsets());
        view.setTermPayloads(indexField.isTermPayloads());
        view.setSortMissingFirst(indexField.isSortMissingFirst());
        view.setSortMissingLast(indexField.isSortMissingLast());
    }

    private SolrIndexField write() {
        final String defaultValue = view.getDefaultValue().trim().isEmpty()
                ? null
                : view.getDefaultValue();
        return SolrIndexField
                .builder()
                .fldType(view.getType())
                .fldName(view.getFieldName().trim())
                .nativeType(view.getFieldType())
                .defaultValue(defaultValue)
                .stored(view.isStored())
                .indexed(view.isIndexed())
                .uninvertible(view.isUninvertible())
                .docValues(view.isDocValues())
                .multiValued(view.isMultiValued())
                .required(view.isRequired())
                .omitNorms(view.isOmitNorms())
                .omitTermFreqAndPositions(view.isOmitTermFreqAndPositions())
                .omitPositions(view.isOmitPositions())
                .termVectors(view.isTermVectors())
                .termPositions(view.isTermPositions())
                .termOffsets(view.isTermOffsets())
                .termPayloads(view.isTermPayloads())
                .sortMissingFirst(view.isSortMissingFirst())
                .sortMissingLast(view.isSortMissingLast())
                .build();
    }
}
