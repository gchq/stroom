/*
 * Copyright 2016 Crown Copyright
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

import stroom.dashboard.client.component.ComponentSettingsDialogController;
import stroom.dashboard.client.flexlayout.MutableTabConfig;
import stroom.dashboard.client.flexlayout.TabLayout;
import stroom.dashboard.shared.ComponentConfig;
import stroom.dashboard.shared.ComponentSettings;
import stroom.svg.shared.SvgImage;
import stroom.widget.component.client.ControllerDialog;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupType;
import stroom.widget.presenter.client.MyPresenterWidget;
import stroom.widget.presenter.client.View;

import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

import java.util.Objects;

public abstract class AbstractComponentPresenter<V extends View>
        extends MyPresenterWidget<V>
        implements DashboardPane {

    private final Provider<?> settingsPresenterProvider;
    private TabLayout tabLayout;
    private ComponentConfig componentConfig;
    private MutableTabConfig tabConfig;
    private ComponentSettingsDialogController settingsPresenter;
    private DashboardContext dashboardContext;
    protected boolean designMode;

    private Runnable onChange = () -> {
    };

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
    public final void setComponentName(final String name) {
        final String originalName = componentConfig.getName();
        if (!Objects.equals(originalName, name)) {
            componentConfig = componentConfig
                    .copy()
                    .name(name)
                    .build();
            // Make sure settings presenter has the latest name.
            if (settingsPresenter != null) {
                settingsPresenter.read(componentConfig);
            }
            changeSettings();
        }
    }

    @Override
    public void showSettings() {
        if (settingsPresenterProvider != null) {
            if (settingsPresenter == null) {
                // Owned, so that it goes with the component and sees its dashboard's context
                settingsPresenter = own((ComponentSettingsDialogController) settingsPresenterProvider.get());
            }

            settingsPresenter.setDashboardContext(dashboardContext);
            settingsPresenter.read(componentConfig);

            final PopupSize popupSize = PopupSize.resizable(800, 700);
            ControllerDialog.show(settingsPresenter)
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

        onChange();
    }

    @Override
    public void setTabLayout(final TabLayout tabLayout) {
        this.tabLayout = tabLayout;
    }

    public void onChange() {
        onChange.run();
    }

    @Override
    public void onClose() {
        // Disposed with its dashboard
    }

    @Override
    public void onRemove() {
        // Disposed by its dashboard (Components.remove), which owns it
    }

    @Override
    public String getId() {
        return componentConfig.getId();
    }

    @Override
    public String getDisplayValue() {
        return componentConfig.getName() + " (" + componentConfig.getId() + ")";
    }

    /// @param onChange What to run when the user changes the component's settings.
    @Override
    public void setOnChange(final Runnable onChange) {
        this.onChange = onChange;
    }

    @Override
    public MutableTabConfig getTabConfig() {
        return tabConfig;
    }

    @Override
    public void setTabConfig(final MutableTabConfig tabConfig) {
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
