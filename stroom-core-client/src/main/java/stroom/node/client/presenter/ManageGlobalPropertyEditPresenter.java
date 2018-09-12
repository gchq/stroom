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

package stroom.node.client.presenter;

import com.google.gwt.user.client.ui.HasText;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.View;
import stroom.dispatch.client.ClientDispatchAsync;
import stroom.entity.client.presenter.ManageEntityEditPresenter;
import stroom.entity.shared.EntityServiceSaveAction;
import stroom.node.client.ClientPropertyCache;
import stroom.node.shared.GlobalProperty;
import stroom.security.client.ClientSecurityContext;
import stroom.widget.popup.client.presenter.PopupSize;

public final class ManageGlobalPropertyEditPresenter
        extends ManageEntityEditPresenter<ManageGlobalPropertyEditPresenter.GlobalPropertyEditView, GlobalProperty> {
    private final ClientDispatchAsync dispatcher;
    private final ClientPropertyCache clientPropertyCache;

    @Inject
    public ManageGlobalPropertyEditPresenter(final EventBus eventBus,
                                             final GlobalPropertyEditView view,
                                             final ClientDispatchAsync dispatcher,
                                             final ClientSecurityContext securityContext,
                                             final ClientPropertyCache clientPropertyCache) {
        super(eventBus, dispatcher, view, securityContext);
        this.dispatcher = dispatcher;
        this.clientPropertyCache = clientPropertyCache;
    }

    @Override
    protected String getEntityDisplayType() {
        return "Application Property";
    }

    @Override
    protected void read() {
        getView().setEditable(getEntity().isEditable());// && isCurrentUserUpdate());
        getView().setPasswordStyle(getEntity().isPassword());
        getView().setRequireRestart(getEntity().isRequireRestart());
        getView().setRequireUiRestart(getEntity().isRequireUiRestart());
        getView().getName().setText(getEntity().getName());
        getView().getValue().setText(getEntity().getValue());
        getView().getDescription().setText(getEntity().getDescription());
        getView().getDefaultValue().setText(getEntity().getDefaultValue());
        getView().getSource().setText(getEntity().getSource());
    }

    @Override
    protected void write(final boolean hideOnSave) {
        String value = getView().getValue().getText();
        if (value != null) {
            getEntity().setValue(value.trim());
        } else {
            getEntity().setValue(null);
        }

        // Save the device.
        dispatcher.exec(new EntityServiceSaveAction<>(getEntity())).onSuccess(result -> {
            setEntity(result);
            if (hideOnSave) {
                hide();

                // Refresh client properties in case they were affected by this change.
                clientPropertyCache.refresh();
            }
        });
    }

    @Override
    protected PopupSize getPopupSize() {
        return new PopupSize(550, 340, 550, 340, true);
    }

    public interface GlobalPropertyEditView extends View {
        HasText getName();

        HasText getValue();

        HasText getDefaultValue();

        HasText getDescription();

        HasText getSource();

        void setEditable(boolean edit);

        void setPasswordStyle(boolean password);

        void setRequireRestart(boolean requiresRestart);

        void setRequireUiRestart(boolean requiresRestart);
    }
}
