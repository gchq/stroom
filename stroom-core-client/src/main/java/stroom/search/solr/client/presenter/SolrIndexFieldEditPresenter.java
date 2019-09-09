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

package stroom.search.solr.client.presenter;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;
import stroom.alert.client.event.AlertEvent;
import stroom.search.solr.client.presenter.SolrIndexFieldEditPresenter.SolrIndexFieldEditView;
import stroom.search.solr.shared.SolrIndexField;
import stroom.search.solr.shared.SolrIndexFieldType;
import stroom.widget.popup.client.event.HidePopupEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupUiHandlers;
import stroom.widget.popup.client.presenter.PopupView.PopupType;

import java.util.List;
import java.util.Set;

public class SolrIndexFieldEditPresenter extends MyPresenterWidget<SolrIndexFieldEditView> {
    private Set<String> otherFieldNames;

    @Inject
    public SolrIndexFieldEditPresenter(final EventBus eventBus,
                                       final SolrIndexFieldEditView view) {
        super(eventBus, view);
    }

    public void read(final SolrIndexField indexField,
                     final Set<String> otherFieldNames,
                     final List<String> fieldTypes) {
        getView().setFieldTypes(fieldTypes);

        this.otherFieldNames = otherFieldNames;
        getView().setFieldUse(indexField.getFieldUse());
        getView().setFieldName(indexField.getFieldName());
        getView().setFieldType(indexField.getFieldType());
        getView().setDefaultValue(indexField.getDefaultValue());
        getView().setStored(indexField.isStored());
        getView().setIndexed(indexField.isIndexed());
        getView().setUninvertible(indexField.isUninvertible());
        getView().setDocValues(indexField.isDocValues());
        getView().setMultiValued(indexField.isMultiValued());
        getView().setRequired(indexField.isRequired());
        getView().setOmitNorms(indexField.isOmitNorms());
        getView().setOmitTermFreqAndPositions(indexField.isOmitTermFreqAndPositions());
        getView().setOmitPositions(indexField.isOmitPositions());
        getView().setTermVectors(indexField.isTermVectors());
        getView().setTermPositions(indexField.isTermPositions());
        getView().setTermOffsets(indexField.isTermOffsets());
        getView().setTermPayloads(indexField.isTermPayloads());
        getView().setSortMissingFirst(indexField.isSortMissingFirst());
        getView().setSortMissingLast(indexField.isSortMissingLast());
    }

    public boolean write(final SolrIndexField indexField) {
        String name = getView().getFieldName();
        name = name.trim();

        if (name.length() == 0) {
            AlertEvent.fireWarn(this, "An index field must have a name", null);
            return false;
        }
        if (!name.matches(SolrIndexField.VALID_FIELD_NAME_PATTERN)) {
            AlertEvent.fireWarn(this, "An index field name must conform to the pattern '" + SolrIndexField.VALID_FIELD_NAME_PATTERN + "'", null);
            return false;
        }
        if (otherFieldNames.contains(indexField.getFieldName())) {
            AlertEvent.fireWarn(this, "An index field with this name already exists", null);
            return false;
        }

        indexField.setFieldUse(getView().getFieldUse());
        indexField.setFieldName(name);
        indexField.setFieldType(getView().getFieldType());
        if (getView().getDefaultValue().trim().length() == 0) {
            indexField.setDefaultValue(null);
        } else {
            indexField.setDefaultValue(getView().getDefaultValue());
        }
        indexField.setStored(getView().isStored());
        indexField.setIndexed(getView().isIndexed());
        indexField.setUninvertible(getView().isUninvertible());
        indexField.setDocValues(getView().isDocValues());
        indexField.setMultiValued(getView().isMultiValued());
        indexField.setRequired(getView().isRequired());
        indexField.setOmitNorms(getView().isOmitNorms());
        indexField.setOmitTermFreqAndPositions(getView().isOmitTermFreqAndPositions());
        indexField.setOmitPositions(getView().isOmitPositions());
        indexField.setTermVectors(getView().isTermVectors());
        indexField.setTermPositions(getView().isTermPositions());
        indexField.setTermOffsets(getView().isTermOffsets());
        indexField.setTermPayloads(getView().isTermPayloads());
        indexField.setSortMissingFirst(getView().isSortMissingFirst());
        indexField.setSortMissingLast(getView().isSortMissingLast());

        return true;
    }

    public void show(final String caption, final PopupUiHandlers uiHandlers) {
        final PopupSize popupSize = new PopupSize(400, 500, 400, 500, 800, 500, true);
        ShowPopupEvent.fire(this, this, PopupType.OK_CANCEL_DIALOG, popupSize, caption, uiHandlers);
    }

    public void hide() {
        HidePopupEvent.fire(this, this);
    }

    public interface SolrIndexFieldEditView extends View {
        SolrIndexFieldType getFieldUse();

        void setFieldUse(SolrIndexFieldType fieldUse);

        String getFieldName();

        void setFieldName(String fieldName);

        String getFieldType();

        void setFieldType(String fieldType);

        String getDefaultValue();

        void setDefaultValue(String defaultValue);

        boolean isStored();

        void setStored(boolean stored);

        boolean isIndexed();

        void setIndexed(boolean indexed);

        boolean isUninvertible();

        void setUninvertible(boolean uninvertible);

        boolean isDocValues();

        void setDocValues(boolean docValues);

        boolean isMultiValued();

        void setMultiValued(boolean multiValued);

        boolean isRequired();

        void setRequired(boolean required);

        boolean isOmitNorms();

        void setOmitNorms(boolean omitNorms);

        boolean isOmitTermFreqAndPositions();

        void setOmitTermFreqAndPositions(boolean omitTermFreqAndPositions);

        boolean isOmitPositions();

        void setOmitPositions(boolean omitPositions);

        boolean isTermVectors();

        void setTermVectors(boolean termVectors);

        boolean isTermPositions();

        void setTermPositions(boolean termPositions);

        boolean isTermOffsets();

        void setTermOffsets(boolean termOffsets);

        boolean isTermPayloads();

        void setTermPayloads(boolean termPayloads);

        boolean isSortMissingFirst();

        void setSortMissingFirst(boolean sortMissingFirst);

        boolean isSortMissingLast();

        void setSortMissingLast(boolean sortMissingLast);

        void setFieldTypes(List<String> fieldTypes);
    }
}
