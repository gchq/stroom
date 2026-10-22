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

package stroom.index.client.presenter;

import stroom.alert.client.event.AlertEvent;
import stroom.document.client.event.ChangeUiHandlers;
import stroom.index.client.presenter.IndexFieldEditPresenter.IndexFieldEditView;
import stroom.index.shared.IndexFieldImpl;
import stroom.query.api.datasource.AnalyzerType;
import stroom.query.api.datasource.DenseVectorFieldConfig;
import stroom.query.api.datasource.FieldType;
import stroom.query.api.datasource.IndexField;
import stroom.util.shared.NullSafe;
import stroom.widget.popup.client.event.HidePopupRequestEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupType;
import stroom.widget.presenter.client.MyPresenterWidget;

import com.google.gwt.user.client.ui.Focus;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.HasUiHandlers;
import com.gwtplatform.mvp.client.View;

import java.util.function.BiConsumer;
import javax.validation.ValidationException;

public class IndexFieldEditPresenter
        extends MyPresenterWidget<IndexFieldEditView>
        implements ChangeUiHandlers {

    private final DenseVectorFieldPresenter denseVectorFieldPresenter;

    @Inject
    public IndexFieldEditPresenter(final EventBus eventBus,
                                   final IndexFieldEditView view,
                                   final DenseVectorFieldPresenter denseVectorFieldPresenter) {
        super(eventBus, view);
        this.denseVectorFieldPresenter = denseVectorFieldPresenter;
        view.setDenseVectorOptions(denseVectorFieldPresenter.getView());
        view.setUiHandlers(this);
    }

    public void read(final IndexFieldImpl indexField) {
        getView().setType(indexField.getFldType());
        getView().setFieldName(indexField.getFldName());
        getView().setStored(indexField.isStored());
        getView().setIndexed(indexField.isIndexed());
        getView().setTermPositions(indexField.isTermPositions());
        getView().setAnalyzerType(indexField.getAnalyzerType());
        getView().setCaseSensitive(indexField.isCaseSensitive());
        denseVectorFieldPresenter.read(NullSafe.getOrElse(
                indexField,
                IndexField::getDenseVectorFieldConfig,
                DenseVectorFieldConfig.builder().build()));
        onChange();
    }

    /// Reads the field from the dialog.
    ///
    /// @return The field.
    /// @throws ValidationException If the field isn't valid, e.g. it has no name.
    public IndexFieldImpl write() {
        final String name = NullSafe.trim(getView().getFieldName());

        if (name.isEmpty()) {
            throw new ValidationException("An index field must have a name");
        }

        return IndexFieldImpl
                .builder()
                .fldType(getView().getType())
                .fldName(name)
                .indexed(getView().isIndexed())
                .stored(getView().isStored())
                .termPositions(getView().isTermPositions())
                .analyzerType(getView().getAnalyzerType())
                .caseSensitive(getView().isCaseSensitive())
                .denseVectorFieldConfig(denseVectorFieldPresenter.write())
                .build();
    }

    /// Shows the dialog.
    ///
    /// @param caption The caption for the dialog.
    /// @param onOk    Called with the field and the hide request when OK is clicked and the
    ///                field is valid. It must either hide or reset the hide request.
    public void show(final String caption, final BiConsumer<IndexFieldImpl, HidePopupRequestEvent> onOk) {
        final PopupSize popupSize = PopupSize.resizable(300, 770);
        ShowPopupEvent.builder(this)
                .popupType(PopupType.OK_CANCEL_DIALOG)
                .popupSize(popupSize)
                .caption(caption)
                .onShow(e -> getView().focus())
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

    @Override
    public void onChange() {
        getView().setDenseVectorOptionsVisible(FieldType.DENSE_VECTOR.equals(getView().getType()));
    }

    public interface IndexFieldEditView extends View, Focus, HasUiHandlers<ChangeUiHandlers> {

        FieldType getType();

        void setType(FieldType type);

        String getFieldName();

        void setFieldName(final String fieldName);

        boolean isStored();

        void setStored(boolean stored);

        boolean isIndexed();

        void setIndexed(boolean indexed);

        boolean isTermPositions();

        void setTermPositions(boolean termPositions);

        AnalyzerType getAnalyzerType();

        void setAnalyzerType(AnalyzerType analyzerType);

        boolean isCaseSensitive();

        void setCaseSensitive(boolean caseSensitive);

        void setDenseVectorOptionsVisible(boolean visible);

        void setDenseVectorOptions(View view);
    }
}
