/*
 * Copyright 2016-2025 Crown Copyright
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

import stroom.alert.client.event.AlertEvent;
import stroom.query.api.datasource.FieldType;
import stroom.search.solr.client.presenter.SolrIndexFieldEditPresenter.SolrIndexFieldEditView;
import stroom.search.solr.shared.SolrIndexField;
import stroom.widget.popup.client.event.HidePopupRequestEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupType;

import com.google.gwt.user.client.ui.Focus;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

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
        getView().setType(indexField.getFldType());
        getView().setFieldName(indexField.getFldName());
        getView().setFieldType(indexField.getNativeType());
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

    public SolrIndexField write() {
        String name = getView().getFieldName();
        name = name.trim();

        if (name.length() == 0) {
            AlertEvent.fireWarn(this, "An index field must have a name", null);
            return null;
        }
        if (!name.matches(SolrIndexField.VALID_FIELD_NAME_PATTERN)) {
            AlertEvent.fireWarn(this,
                    "An index field name must conform to the pattern '" + SolrIndexField.VALID_FIELD_NAME_PATTERN + "'",
                    null);
            return null;
        }
        if (otherFieldNames.contains(name)) {
            AlertEvent.fireWarn(this, "An index field with this name already exists", null);
            return null;
        }

        String defaultValue = null;
        if (getView().getDefaultValue().trim().length() > 0) {
            defaultValue = getView().getDefaultValue();
        }

        return SolrIndexField
                .builder()
                .fldType(getView().getType())
                .fldName(name)
                .nativeType(getView().getFieldType())
                .defaultValue(defaultValue)
                .stored(getView().isStored())
                .indexed(getView().isIndexed())
                .uninvertible(getView().isUninvertible())
                .docValues(getView().isDocValues())
                .multiValued(getView().isMultiValued())
                .required(getView().isRequired())
                .omitNorms(getView().isOmitNorms())
                .omitTermFreqAndPositions(getView().isOmitTermFreqAndPositions())
                .omitPositions(getView().isOmitPositions())
                .termVectors(getView().isTermVectors())
                .termPositions(getView().isTermPositions())
                .termOffsets(getView().isTermOffsets())
                .termPayloads(getView().isTermPayloads())
                .sortMissingFirst(getView().isSortMissingFirst())
                .sortMissingLast(getView().isSortMissingLast())
                .build();
    }

    public void show(final String caption, final HidePopupRequestEvent.Handler handler) {
        final PopupSize popupSize = PopupSize.resizable(300, 450);
        ShowPopupEvent.builder(this)
                .popupType(PopupType.OK_CANCEL_DIALOG)
                .popupSize(popupSize)
                .caption(caption)
                .onShow(e -> getView().focus())
                .onHideRequest(handler)
                .fire();
    }

    public interface SolrIndexFieldEditView extends View, Focus {

        FieldType getType();

        void setType(FieldType type);

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
