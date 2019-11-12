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
 *
 */

package stroom.receive.rules.client.presenter;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;
import stroom.alert.client.event.AlertEvent;
import stroom.datasource.api.v2.AbstractField;
import stroom.datasource.api.v2.BooleanField;
import stroom.datasource.api.v2.DateField;
import stroom.datasource.api.v2.DocRefField;
import stroom.datasource.api.v2.DoubleField;
import stroom.datasource.api.v2.FieldTypes;
import stroom.datasource.api.v2.FloatField;
import stroom.datasource.api.v2.IdField;
import stroom.datasource.api.v2.IntegerField;
import stroom.datasource.api.v2.LongField;
import stroom.datasource.api.v2.TextField;
import stroom.widget.popup.client.event.HidePopupEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupUiHandlers;
import stroom.widget.popup.client.presenter.PopupView.PopupType;

import java.util.Set;

public class FieldEditPresenter extends MyPresenterWidget<FieldEditPresenter.FieldEditView> {
    private Set<String> otherFieldNames;

    @Inject
    public FieldEditPresenter(final EventBus eventBus, final FieldEditView view) {
        super(eventBus, view);
    }

    public void read(final AbstractField field, final Set<String> otherFieldNames) {
        this.otherFieldNames = otherFieldNames;
        getView().setType(field.getType());
        getView().setName(field.getName());
    }

    public AbstractField write() {
        String name = getView().getName();
        name = name.trim();

        if (name.length() == 0) {
            AlertEvent.fireWarn(this, "A field must have a name", null);
            return null;
        }
        if (otherFieldNames.contains(name)) {
            AlertEvent.fireWarn(this, "A field with this name already exists", null);
            return null;
        }

        return create(getView().getType(), name);
    }

    public void show(final String caption, final PopupUiHandlers uiHandlers) {
        final PopupSize popupSize = new PopupSize(305, 102, 305, 220, 800, 102, true);
        ShowPopupEvent.fire(this, this, PopupType.OK_CANCEL_DIALOG, popupSize, caption, uiHandlers);
    }

    public void hide() {
        HidePopupEvent.fire(this, this);
    }

    public interface FieldEditView extends View {
        String getType();

        void setType(String type);

        String getName();

        void setName(final String name);
    }

    private AbstractField create(final String type, final String name) {
        switch (type) {
            case FieldTypes.ID:
                return new IdField(name);
            case FieldTypes.BOOLEAN:
                return new BooleanField(name);
            case FieldTypes.INTEGER:
                return new IntegerField(name);
            case FieldTypes.LONG:
                return new LongField(name);
            case FieldTypes.FLOAT:
                return new FloatField(name);
            case FieldTypes.DOUBLE:
                return new DoubleField(name);
            case FieldTypes.DATE:
                return new DateField(name);
            case FieldTypes.TEXT:
                return new TextField(name);
            case FieldTypes.DOC_REF:
                return new DocRefField(null, name);
            default:
                AlertEvent.fireWarn(this, "Unexpected type " + type, null);
        }

        return null;
    }
}
