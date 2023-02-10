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

import stroom.alert.client.event.AlertEvent;
import stroom.datasource.api.v2.AbstractField;
import stroom.datasource.api.v2.BooleanField;
import stroom.datasource.api.v2.DateField;
import stroom.datasource.api.v2.DocRefField;
import stroom.datasource.api.v2.DoubleField;
import stroom.datasource.api.v2.FieldType;
import stroom.datasource.api.v2.FloatField;
import stroom.datasource.api.v2.IdField;
import stroom.datasource.api.v2.IntegerField;
import stroom.datasource.api.v2.IpV4AddressField;
import stroom.datasource.api.v2.LongField;
import stroom.datasource.api.v2.TextField;
import stroom.widget.popup.client.event.HidePopupEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupUiHandlers;
import stroom.widget.popup.client.presenter.PopupView.PopupType;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

import java.util.Set;

public class FieldEditPresenter extends MyPresenterWidget<FieldEditPresenter.FieldEditView> {

    private Set<String> otherFieldNames;

    @Inject
    public FieldEditPresenter(final EventBus eventBus, final FieldEditView view) {
        super(eventBus, view);
    }

    public void read(final AbstractField field, final Set<String> otherFieldNames) {
        this.otherFieldNames = otherFieldNames;
        getView().setFieldType(field.getFieldType());
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

        return create(getView().getFieldType(), name);
    }

    public void show(final String caption, final PopupUiHandlers uiHandlers) {
        final PopupSize popupSize = PopupSize.resizableX();
        ShowPopupEvent.fire(this, this, PopupType.OK_CANCEL_DIALOG, popupSize, caption, uiHandlers);
    }

    public void hide() {
        HidePopupEvent.fire(this, this);
    }

    public interface FieldEditView extends View {

        FieldType getFieldType();

        void setFieldType(FieldType type);

        String getName();

        void setName(final String name);
    }

    private AbstractField create(final FieldType type, final String name) {
        switch (type) {
            case ID:
                return new IdField(name);
            case BOOLEAN:
                return new BooleanField(name);
            case INTEGER:
                return new IntegerField(name);
            case LONG:
                return new LongField(name);
            case FLOAT:
                return new FloatField(name);
            case DOUBLE:
                return new DoubleField(name);
            case DATE:
                return new DateField(name);
            case TEXT:
                return new TextField(name);
            case IPV4_ADDRESS:
                return new IpV4AddressField(name);
            case DOC_REF:
                return new DocRefField(null, name);
            default:
                AlertEvent.fireWarn(this, "Unexpected type " + type, null);
        }

        return null;
    }
}
