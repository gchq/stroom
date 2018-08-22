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

package stroom.properties.global.client.presenter;

import com.google.gwt.user.client.ui.HasText;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;
import stroom.dispatch.client.ClientDispatchAsync;
import stroom.properties.global.api.ConfigProperty;
import stroom.properties.global.api.LoadGlobalConfigAction;
import stroom.properties.global.api.SaveGlobalConfigAction;
import stroom.ui.config.client.UiConfigCache;
import stroom.security.client.ClientSecurityContext;
import stroom.widget.popup.client.event.HidePopupEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupUiHandlers;
import stroom.widget.popup.client.presenter.PopupView.PopupType;

public final class ManageGlobalPropertyEditPresenter extends MyPresenterWidget<ManageGlobalPropertyEditPresenter.GlobalPropertyEditView> {
    private final ClientDispatchAsync dispatcher;
    private final ClientSecurityContext securityContext;
    private final UiConfigCache clientPropertyCache;
    private ConfigProperty globalProperty;

    @Inject
    public ManageGlobalPropertyEditPresenter(final EventBus eventBus,
                                             final GlobalPropertyEditView view,
                                             final ClientDispatchAsync dispatcher,
                                             final ClientSecurityContext securityContext,
                                             final UiConfigCache clientPropertyCache) {
        super(eventBus, view);
        this.dispatcher = dispatcher;
        this.securityContext = securityContext;
        this.clientPropertyCache = clientPropertyCache;
    }

    protected ClientSecurityContext getSecurityContext() {
        return securityContext;
    }

    void showEntity(final ConfigProperty globalProperty, final PopupUiHandlers popupUiHandlers) {
        final String caption = getEntityDisplayType() + " - " + globalProperty.getName();

        final PopupUiHandlers internalPopupUiHandlers = new PopupUiHandlers() {
            @Override
            public void onHideRequest(final boolean autoClose, final boolean ok) {
                if (ok) {
                    write(true);
                } else {
                    hide();
                }

                popupUiHandlers.onHideRequest(autoClose, ok);
            }

            @Override
            public void onHide(final boolean autoClose, final boolean ok) {
                popupUiHandlers.onHide(autoClose, ok);
            }
        };

        final PopupType popupType = PopupType.OK_CANCEL_DIALOG;

        if (globalProperty.getId() != null) {
            // Reload it so we always have the latest version
            final LoadGlobalConfigAction action = new LoadGlobalConfigAction(globalProperty);
            dispatcher.exec(action).onSuccess(result -> {
                setEntity(result);
                read();
                ShowPopupEvent.fire(ManageGlobalPropertyEditPresenter.this, ManageGlobalPropertyEditPresenter.this, popupType,
                        getPopupSize(), caption, internalPopupUiHandlers);
            });
        } else {
            // new globalProperty
            setEntity(globalProperty);
            read();
            ShowPopupEvent.fire(ManageGlobalPropertyEditPresenter.this, ManageGlobalPropertyEditPresenter.this, popupType,
                    getPopupSize(), caption, internalPopupUiHandlers);
        }
    }

    protected void hide() {
        HidePopupEvent.fire(ManageGlobalPropertyEditPresenter.this, ManageGlobalPropertyEditPresenter.this);
    }

    private ConfigProperty getEntity() {
        return globalProperty;
    }

    private void setEntity(final ConfigProperty entity) {
        this.globalProperty = entity;
    }

    private String getEntityDisplayType() {
        return "Application Property";
    }

    private void read() {
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

    private void write(final boolean hideOnSave) {
        String value = getView().getValue().getText();
        if (value != null) {
            getEntity().setValue(value.trim());
        } else {
            getEntity().setValue(null);
        }

        // Save.
        dispatcher.exec(new SaveGlobalConfigAction(getEntity())).onSuccess(result -> {
            setEntity(result);
            if (hideOnSave) {
                hide();

                // Refresh client properties in case they were affected by this change.
                clientPropertyCache.refresh();
            }
        });
    }

    protected PopupSize getPopupSize() {
        return new PopupSize(550, 340, 550, 340, 1024, 340, true);
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
