/*
 * Copyright 2020 Crown Copyright
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

package stroom.index.client.component;

import stroom.alert.client.event.AlertEvent;
import stroom.index.shared.IndexFieldImpl;
import stroom.query.api.datasource.DenseVectorFieldConfig;
import stroom.query.api.datasource.FieldType;
import stroom.query.api.datasource.IndexField;
import stroom.util.shared.NullSafe;
import stroom.widget.component.client.Controller;
import stroom.widget.component.client.ControllerDialog;
import stroom.widget.component.client.HasControllerNode;
import stroom.widget.popup.client.PopupSize;
import stroom.widget.popup.client.PopupType;
import stroom.widget.popup.client.event.HidePopupRequestEvent;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import java.util.function.BiConsumer;
import javax.validation.ValidationException;

/// The New and Edit Field dialog ([IndexFieldEditor]) of a Lucene index, made for one use.
public class IndexFieldEditorController extends Controller {

    private final IndexFieldEditor view;
    private final DenseVectorFieldController denseVectorField;

    /// Made by GIN, with its content and the dense vector options it shows.
    ///
    /// @param eventBus         The application's event bus.
    /// @param view             The content.
    /// @param denseVectorField The dense vector options, attached to the content's.
    @Inject
    public IndexFieldEditorController(final EventBus eventBus,
                                      final IndexFieldEditor view,
                                      final DenseVectorFieldController denseVectorField) {
        super(eventBus);
        this.view = view;
        initWidget(view);
        this.denseVectorField = own(denseVectorField).attach(view.getDenseVectorField());
        view.setOnTypeChange(this::onTypeChange);
    }

    /// Edits a field in a dialog, owned by `opener` until it is closed.
    ///
    /// @param opener     What shows it.
    /// @param caption    The dialog's caption, e.g. "New Field".
    /// @param indexField The field to start from.
    /// @param onOk       Called with the field and the hide request when OK is clicked and the
    ///                   field is valid. It must either hide or reset the hide request.
    public void edit(final HasControllerNode opener,
                     final String caption,
                     final IndexFieldImpl indexField,
                     final BiConsumer<IndexFieldImpl, HidePopupRequestEvent> onOk) {
        read(indexField);
        ControllerDialog.showOnce(opener, this)
                .popupType(PopupType.OK_CANCEL_DIALOG)
                .popupSize(PopupSize.resizable(300, 770))
                .caption(caption)
                .onShow(e -> view.focus())
                .onHideRequest(e -> onHideRequest(e, onOk))
                .fire();
    }

    /// Handles a request to close the dialog. Cancel hides the dialog. OK passes the field to
    /// `onOk`, unless the field is invalid, in which case an error is shown and the request is
    /// reset so that the dialog's buttons are enabled again.
    ///
    /// @param e    The request to close the dialog.
    /// @param onOk Called with the field and the hide request when the field is valid.
    void onHideRequest(final HidePopupRequestEvent e,
                       final BiConsumer<IndexFieldImpl, HidePopupRequestEvent> onOk) {
        if (e.isOk()) {
            final IndexFieldImpl indexField;
            try {
                indexField = write();
            } catch (final RuntimeException ex) {
                AlertEvent.fireError(this, ex.getMessage(), e::reset);
                return;
            }
            onOk.accept(indexField, e);
        } else {
            e.hide();
        }
    }

    /// Fills the dialog with a field.
    ///
    /// @param indexField The field.
    void read(final IndexFieldImpl indexField) {
        view.setType(indexField.getFldType());
        view.setFieldName(indexField.getFldName());
        view.setStored(indexField.isStored());
        view.setIndexed(indexField.isIndexed());
        view.setTermPositions(indexField.isTermPositions());
        view.setAnalyzerType(indexField.getAnalyzerType());
        view.setCaseSensitive(indexField.isCaseSensitive());
        denseVectorField.read(NullSafe.getOrElse(
                indexField,
                IndexField::getDenseVectorFieldConfig,
                DenseVectorFieldConfig.builder().build()));
        onTypeChange();
    }

    private IndexFieldImpl write() {
        final String name = NullSafe.trim(view.getFieldName());
        if (name.isEmpty()) {
            throw new ValidationException("An index field must have a name");
        }
        return IndexFieldImpl
                .builder()
                .fldType(view.getType())
                .fldName(name)
                .indexed(view.isIndexed())
                .stored(view.isStored())
                .termPositions(view.isTermPositions())
                .analyzerType(view.getAnalyzerType())
                .caseSensitive(view.isCaseSensitive())
                .denseVectorFieldConfig(denseVectorField.write())
                .build();
    }

    private void onTypeChange() {
        view.setDenseVectorOptionsVisible(FieldType.DENSE_VECTOR.equals(view.getType()));
    }
}
