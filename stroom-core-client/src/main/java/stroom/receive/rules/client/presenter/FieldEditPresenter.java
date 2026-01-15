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

package stroom.receive.rules.client.presenter;

import stroom.alert.client.event.AlertEvent;
import stroom.query.api.datasource.ConditionSet;
import stroom.query.api.datasource.FieldType;
import stroom.query.api.datasource.QueryField;
import stroom.util.shared.NullSafe;
import stroom.util.shared.string.CIKey;
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

    private Set<CIKey> otherFieldNames;

    @Inject
    public FieldEditPresenter(final EventBus eventBus, final FieldEditView view) {
        super(eventBus, view);
    }

    public void read(final QueryField field, final Set<CIKey> otherFieldNames) {
        this.otherFieldNames = otherFieldNames;
        getView().setFieldType(field.getFldType());
        getView().setName(field.getFldName());
    }

    public QueryField write() {
        final String name = NullSafe.trim(getView().getName());
        if (name.isEmpty()) {
            AlertEvent.fireWarn(this, "A field must have a name", null);
            return null;
        }
        final CIKey ciName = CIKey.of(name);
        if (otherFieldNames.contains(ciName)) {
            AlertEvent.fireWarn(this,
                    "A field with this name already exists. Field names are case insensitive.",
                    null);
            return null;
        }

        return create(getView().getFieldType(), name);
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

    private QueryField create(final FieldType type, final String name) {
        return QueryField
                .builder()
                .fldName(name)
                .fldType(type)
                .queryable(true)
                .conditionSet(ConditionSet.RECEIPT_POLICY_CONDITIONS)
                .build();
    }


    // --------------------------------------------------------------------------------


    public interface FieldEditView extends View, Focus {

        FieldType getFieldType();

        void setFieldType(FieldType type);

        String getName();

        void setName(final String name);
    }
}
