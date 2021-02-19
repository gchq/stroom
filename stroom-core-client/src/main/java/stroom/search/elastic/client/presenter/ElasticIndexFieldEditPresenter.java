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

package stroom.search.elastic.client.presenter;

import stroom.alert.client.event.AlertEvent;
import stroom.search.elastic.client.presenter.ElasticIndexFieldEditPresenter.ElasticIndexFieldEditView;
import stroom.search.elastic.shared.ElasticIndexField;
import stroom.search.elastic.shared.ElasticIndexFieldType;
import stroom.search.solr.client.presenter.SolrIndexFieldEditPresenter.SolrIndexFieldEditView;
import stroom.search.solr.shared.SolrIndexField;
import stroom.search.solr.shared.SolrIndexFieldType;
import stroom.widget.popup.client.event.HidePopupEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupUiHandlers;
import stroom.widget.popup.client.presenter.PopupView.PopupType;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

import java.util.List;
import java.util.Set;

public class ElasticIndexFieldEditPresenter extends MyPresenterWidget<ElasticIndexFieldEditView> {
    private Set<String> otherFieldNames;

    @Inject
    public ElasticIndexFieldEditPresenter(final EventBus eventBus,
                                          final ElasticIndexFieldEditView view) {
        super(eventBus, view);
    }

    public void read(final ElasticIndexField indexField,
                     final Set<String> otherFieldNames,
                     final List<String> fieldTypes) {
        getView().setFieldTypes(fieldTypes);

        this.otherFieldNames = otherFieldNames;
        getView().setFieldUse(indexField.getFieldUse());
        getView().setFieldName(indexField.getFieldName());
        getView().setFieldType(indexField.getFieldType());
        getView().setStored(indexField.isStored());
        getView().setIndexed(indexField.isIndexed());
    }

    public boolean write(final ElasticIndexField indexField) {
        String name = getView().getFieldName();
        name = name.trim();

        if (name.length() == 0) {
            AlertEvent.fireWarn(this, "An index field must have a name", null);
            return false;
        }
        if (!name.matches(ElasticIndexField.VALID_FIELD_NAME_PATTERN)) {
            AlertEvent.fireWarn(this, "An index field name must conform to the pattern '" + ElasticIndexField.VALID_FIELD_NAME_PATTERN + "'", null);
            return false;
        }
        if (otherFieldNames.contains(indexField.getFieldName())) {
            AlertEvent.fireWarn(this, "An index field with this name already exists", null);
            return false;
        }

        indexField.setFieldUse(getView().getFieldUse());
        indexField.setFieldName(name);
        indexField.setFieldType(getView().getFieldType());
        indexField.setStored(getView().isStored());
        indexField.setIndexed(getView().isIndexed());

        return true;
    }

    public void show(final String caption, final PopupUiHandlers uiHandlers) {
        final PopupSize popupSize = new PopupSize(400, 500, 400, 500, 800, 500, true);
        ShowPopupEvent.fire(this, this, PopupType.OK_CANCEL_DIALOG, popupSize, caption, uiHandlers);
    }

    public void hide() {
        HidePopupEvent.fire(this, this);
    }

    public interface ElasticIndexFieldEditView extends View {
        ElasticIndexFieldType getFieldUse();

        void setFieldUse(ElasticIndexFieldType fieldUse);

        String getFieldName();

        void setFieldName(String fieldName);

        String getFieldType();

        void setFieldType(String fieldType);

        boolean isStored();

        void setStored(boolean stored);

        boolean isIndexed();

        void setIndexed(boolean indexed);
    }
}
