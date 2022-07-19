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
import stroom.datasource.api.v2.FieldTypes;
import stroom.datasource.api.v2.FloatField;
import stroom.datasource.api.v2.IdField;
import stroom.datasource.api.v2.IntegerField;
import stroom.datasource.api.v2.IpV4AddressField;
import stroom.datasource.api.v2.LongField;
import stroom.datasource.api.v2.TextField;
import stroom.widget.popup.client.event.HidePopupRequestEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupType;

import com.google.gwt.user.client.ui.Focus;
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

    public void show(final String caption, final HidePopupRequestEvent.Handler handler) {
        final PopupSize popupSize = PopupSize.resizableX();
        ShowPopupEvent.builder(this)
                .popupType(PopupType.OK_CANCEL_DIALOG)
                .popupSize(popupSize)
                .caption(caption)
                .onShow(e -> getView().focus())
                .onHideRequest(handler)
                .fire();
    }

    public interface FieldEditView extends View, Focus {

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
            case FieldTypes.IPV4_ADDRESS:
                return new IpV4AddressField(name);
            case FieldTypes.DOC_REF:
                return new DocRefField(null, name);
            default:
                AlertEvent.fireWarn(this, "Unexpected type " + type, null);
        }

        return null;
    }
}
