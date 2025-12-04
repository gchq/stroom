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

package stroom.dashboard.client.main;

import stroom.dashboard.client.flexlayout.TabLayout;
import stroom.dashboard.shared.ComponentConfig;
import stroom.dashboard.shared.ComponentSettings;
import stroom.dashboard.shared.TabConfig;
import stroom.document.client.event.DirtyEvent;
import stroom.document.client.event.DirtyEvent.DirtyHandler;
import stroom.document.client.event.HasDirtyHandlers;
import stroom.svg.shared.SvgImage;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupType;

import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;
import com.google.web.bindery.event.shared.HandlerRegistration;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

public abstract class AbstractComponentPresenter<V extends View>
        extends MyPresenterWidget<V>
        implements Component, HasDirtyHandlers {

    private final Provider<?> settingsPresenterProvider;
    private TabLayout tabLayout;
    private ComponentConfig componentConfig;
    private TabConfig tabConfig;
    private SettingsPresenter settingsPresenter;
    private DashboardContext dashboardContext;
    protected boolean designMode;

    public AbstractComponentPresenter(final EventBus eventBus,
                                      final V view,
                                      final Provider<?> settingsPresenterProvider) {
        super(eventBus, view);
        this.settingsPresenterProvider = settingsPresenterProvider;
    }

    @Override
    public DashboardContext getDashboardContext() {
        return dashboardContext;
    }

    @Override
    public void setDashboardContext(final DashboardContext dashboardContext) {
        this.dashboardContext = dashboardContext;
    }

    @Override
    public void read(final ComponentConfig componentConfig) {
        this.componentConfig = componentConfig;
    }

    @Override
    public ComponentConfig write() {
        if (settingsPresenter != null) {
            componentConfig = settingsPresenter.write(componentConfig);
        }
        return componentConfig;
    }

    @Override
    public ComponentConfig getComponentConfig() {
        return componentConfig;
    }

    public ComponentSettings getSettings() {
        return componentConfig.getSettings();
    }

    public void setSettings(final ComponentSettings componentSettings) {
        componentConfig = componentConfig
                .copy()
                .settings(componentSettings)
                .build();
    }

    @Override
    public void setComponentName(final String name) {
        componentConfig = componentConfig
                .copy()
                .name(name)
                .build();
    }

    @Override
    public void showSettings() {
        if (settingsPresenterProvider != null) {
            if (settingsPresenter == null) {
                settingsPresenter = (SettingsPresenter) settingsPresenterProvider.get();
            }

            settingsPresenter.setDashboardContext(dashboardContext);
            settingsPresenter.read(componentConfig);

            final PopupSize popupSize = PopupSize.resizable(800, 700);
            ShowPopupEvent.builder(settingsPresenter)
                    .popupType(PopupType.OK_CANCEL_DIALOG)
                    .popupSize(popupSize)
                    .caption("Settings")
                    .onShow(e -> settingsPresenter.focus())
                    .onHideRequest(e -> {
                        if (e.isOk()) {
                            if (settingsPresenter.validate()) {
                                final boolean dirty = settingsPresenter.isDirty(componentConfig);
                                componentConfig = settingsPresenter.write(componentConfig);

                                if (dirty) {
                                    changeSettings();
                                }

                                e.hide();
                            } else {
                                e.reset();
                            }
                        } else {
                            e.hide();
                        }
                    })
                    .fire();
        }
    }

    protected void changeSettings() {
        if (tabLayout != null) {
            tabLayout.refresh();
        }

        setDirty(true);
    }

    @Override
    public void setTabLayout(final TabLayout tabLayout) {
        this.tabLayout = tabLayout;
    }

    public void setDirty(final boolean dirty) {
        if (dirty) {
            DirtyEvent.fire(this, dirty);
        }
    }

    @Override
    public void onClose() {
        unbind();
    }

    @Override
    public void onRemove() {
        unbind();
    }

    @Override
    public String getId() {
        return componentConfig.getId();
    }

    @Override
    public String getDisplayValue() {
        return componentConfig.getName() + " (" + componentConfig.getId() + ")";
    }

    @Override
    public HandlerRegistration addDirtyHandler(final DirtyHandler handler) {
        return addHandlerToSource(DirtyEvent.getType(), handler);
    }

    @Override
    public TabConfig getTabConfig() {
        return tabConfig;
    }

    @Override
    public void setTabConfig(final TabConfig tabConfig) {
        this.tabConfig = tabConfig;
    }

    //###############
    //# Start TabData
    //###############
    @Override
    public SvgImage getIcon() {
        return null;
    }

    @Override
    public String getLabel() {
        return componentConfig.getName();
    }

    @Override
    public boolean isCloseable() {
        return true;
    }
    //###############
    //# End TabData
    //###############

    @Override
    public void setDesignMode(final boolean designMode) {
        this.designMode = designMode;
    }
}
