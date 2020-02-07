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

package stroom.config.global.client.presenter;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.ui.HasText;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.HasUiHandlers;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;
import stroom.alert.client.event.AlertEvent;
import stroom.config.global.shared.ConfigProperty;
import stroom.config.global.shared.ConfigResource;
import stroom.config.global.shared.OverrideValue;
import stroom.dispatch.client.Rest;
import stroom.dispatch.client.RestFactory;
import stroom.security.client.api.ClientSecurityContext;
import stroom.ui.config.client.UiConfigCache;
import stroom.widget.popup.client.event.HidePopupEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupUiHandlers;
import stroom.widget.popup.client.presenter.PopupView.PopupType;

public final class ManageGlobalPropertyEditPresenter
        extends MyPresenterWidget<ManageGlobalPropertyEditPresenter.GlobalPropertyEditView>
        implements ManageGlobalPropertyEditUiHandlers {
    private static final ConfigResource CONFIG_RESOURCE = GWT.create(ConfigResource.class);

    private final RestFactory restFactory;
    private final ClientSecurityContext securityContext;
    private final UiConfigCache clientPropertyCache;
    private ConfigProperty configProperty;

    @Inject
    public ManageGlobalPropertyEditPresenter(final EventBus eventBus,
                                             final GlobalPropertyEditView view,
                                             final RestFactory restFactory,
                                             final ClientSecurityContext securityContext,
                                             final UiConfigCache clientPropertyCache) {
        super(eventBus, view);
        this.restFactory = restFactory;
        this.securityContext = securityContext;
        this.clientPropertyCache = clientPropertyCache;
        view.setUiHandlers(this);
    }

    protected ClientSecurityContext getSecurityContext() {
        return securityContext;
    }

    void showEntity(final ConfigProperty configProperty, final PopupUiHandlers popupUiHandlers) {
        final String caption = getEntityDisplayType() + " - " + configProperty.getName();

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

        if (configProperty.getId() != null) {
            // Reload it so we always have the latest version
            final Rest<ConfigProperty> rest = restFactory.create();
            rest
                    .onSuccess(result -> {
                        setEntity(result);
                        read();
                        ShowPopupEvent.fire(ManageGlobalPropertyEditPresenter.this, ManageGlobalPropertyEditPresenter.this, popupType,
                                getPopupSize(), caption, internalPopupUiHandlers);
                    })
                    .call(CONFIG_RESOURCE)
                    .read(configProperty.getId());
        } else {
            // new configProperty
            setEntity(configProperty);
            read();
            ShowPopupEvent.fire(ManageGlobalPropertyEditPresenter.this, ManageGlobalPropertyEditPresenter.this, popupType,
                    getPopupSize(), caption, internalPopupUiHandlers);
        }
    }

    protected void hide() {
        HidePopupEvent.fire(ManageGlobalPropertyEditPresenter.this, ManageGlobalPropertyEditPresenter.this);
    }

    private ConfigProperty getEntity() {
        return configProperty;
    }

    private void setEntity(final ConfigProperty entity) {
        this.configProperty = entity;
    }

    private String getEntityDisplayType() {
        return "Application Property";
    }

    private void read() {
        getView().setPasswordStyle(getEntity().isPassword());
        getView().setRequireRestart(getEntity().isRequireRestart());
        getView().setRequireUiRestart(getEntity().isRequireUiRestart());
        getView().getName().setText(getEntity().getName().toString());
        getView().setUseOverride(getEntity().hasDatabaseOverride());
        String databaseOverrideValue = "";
        if (getEntity().hasDatabaseOverride()) {
            databaseOverrideValue = getEntity().getDatabaseOverrideValue().getValOrElse("");
        }
        String yamlOverrideValue = "";
        if (getEntity().hasYamlOverride()) {
            yamlOverrideValue = getEntity().getYamlOverrideValue().getValOrElse("");
        }
        getView().getDefaultValue().setText(getEntity().getDefaultValue().orElse(""));
        getView().getYamlValue().setText(yamlOverrideValue);
        getView().getDatabaseValue().setText(databaseOverrideValue);
        getView().getEffectiveValue().setText(getEntity().getEffectiveValue().orElse(""));
        getView().getDescription().setText(getEntity().getDescription());
        getView().getDataType().setText(getEntity().getDataTypeName());
        getView().getSource().setText(getEntity().getSource().getName());

        getView().setEditable(getEntity().isEditable());
    }

    private void write(final boolean hideOnSave) {
        refreshValuesOnChange();

        // Save.
        final Rest<ConfigProperty> rest = restFactory.create();
        rest
                .onSuccess(result -> {
                    setEntity(result);
                    if (hideOnSave) {
                        hide();

                        // Refresh client properties in case they were affected by this change.
                        clientPropertyCache.refresh();
                    }
                })
                .onFailure(throwable ->
                        AlertEvent.fireError(ManageGlobalPropertyEditPresenter.this,
                                "Error saving property",
                                throwable.getMessage(),
                                null))
                .call(CONFIG_RESOURCE)
                .update(getEntity());
    }

    private void refreshValuesOnChange() {
        if (getView().getUseOverride()) {
            final String value = getView().getDatabaseValue().getText();
            getEntity().setDatabaseOverride(OverrideValue.with(value.trim()));
        } else {
            getEntity().setDatabaseOverride(OverrideValue.unSet());
            // no override so clear the value
            getView().getDatabaseValue().setText(null);
        }

        getView().getEffectiveValue().setText(getEntity().getEffectiveValue().orElse(null));
        getView().getSource().setText(getEntity().getSource().getName());

        // Refresh the edit status of the override fields
        getView().setEditable(getEntity().isEditable());
    }

    protected PopupSize getPopupSize() {
        return new PopupSize(
                700, 513,
                700, 513,
                1024, 513,
                true);
    }

    @Override
    public void onChangeUseOverride() {
        refreshValuesOnChange();
    }

    @Override
    public void onChangeOverrideValue() {
        refreshValuesOnChange();
    }

    public interface GlobalPropertyEditView extends View, HasUiHandlers<ManageGlobalPropertyEditUiHandlers> {
        HasText getName();

        HasText getDescription();

        HasText getDefaultValue();

        HasText getYamlValue();

        boolean getUseOverride();

        HasText getDatabaseValue();

        HasText getEffectiveValue();

        HasText getSource();

        HasText getDataType();

        void setPasswordStyle(boolean password);

        void setRequireRestart(boolean requiresRestart);

        void setRequireUiRestart(boolean requiresRestart);

        void setUseOverride(boolean useOverride);

        void setEditable(boolean edit);
    }

}
