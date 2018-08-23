/*
 * Copyright 2017 Crown Copyright
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

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;
import stroom.alert.client.event.AlertEvent;
import stroom.index.shared.IndexField;
import stroom.index.shared.IndexField.AnalyzerType;
import stroom.index.shared.IndexFieldType;
import stroom.widget.popup.client.event.HidePopupEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupUiHandlers;
import stroom.widget.popup.client.presenter.PopupView.PopupType;

import java.util.Set;

public class IndexFieldEditPresenter extends MyPresenterWidget<IndexFieldEditPresenter.IndexFieldEditView> {
    private Set<String> otherFieldNames;

    @Inject
    public IndexFieldEditPresenter(final EventBus eventBus, final IndexFieldEditView view) {
        super(eventBus, view);
    }

    public void read(final IndexField indexField, final Set<String> otherFieldNames) {
        this.otherFieldNames = otherFieldNames;
        getView().setFieldUse(indexField.getFieldType());
        getView().setFieldName(indexField.getFieldName());
        getView().setStored(indexField.isStored());
        getView().setIndexed(indexField.isIndexed());
        getView().setTermPositions(indexField.isTermPositions());
        getView().setAnalyzerType(indexField.getAnalyzerType());
        getView().setCaseSensitive(indexField.isCaseSensitive());
    }

    public boolean write(final IndexField indexField) {
        String name = getView().getFieldName();
        name = name.trim();

        indexField.setFieldType(getView().getFieldUse());
        indexField.setFieldName(name);
        indexField.setStored(getView().isStored());
        indexField.setIndexed(getView().isIndexed());
        indexField.setTermPositions(getView().isTermPositions());
        indexField.setAnalyzerType(getView().getAnalyzerType());
        indexField.setCaseSensitive(getView().isCaseSensitive());

        if (name.length() == 0) {
            AlertEvent.fireWarn(this, "An index field must have a name", null);
            return false;
        }
        if (otherFieldNames.contains(indexField.getFieldName())) {
            AlertEvent.fireWarn(this, "An index field with this name already exists", null);
            return false;
        }

        return true;
    }

    public void show(final String caption, final PopupUiHandlers uiHandlers) {
        final PopupSize popupSize = new PopupSize(305, 220, 305, 220, 800, 220, true);
        ShowPopupEvent.fire(this, this, PopupType.OK_CANCEL_DIALOG, popupSize, caption, uiHandlers);
    }

    public void hide() {
        HidePopupEvent.fire(this, this);
    }

    public interface IndexFieldEditView extends View {
        IndexFieldType getFieldUse();

        void setFieldUse(IndexFieldType fieldUse);

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
    }
}
