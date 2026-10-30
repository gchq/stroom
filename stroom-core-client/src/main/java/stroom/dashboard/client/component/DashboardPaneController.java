/*
 * Copyright 2026 Crown Copyright
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

package stroom.dashboard.client.component;

import stroom.dashboard.client.flexlayout.TabLayout;
import stroom.dashboard.client.main.DashboardContext;
import stroom.dashboard.client.main.DashboardPane;
import stroom.dashboard.client.model.MutableTabConfig;
import stroom.dashboard.shared.ComponentConfig;
import stroom.dashboard.shared.ComponentSettings;
import stroom.svg.shared.SvgImage;
import stroom.widget.component.client.ControllerDialog;
import stroom.widget.component.client.LayerController;
import stroom.widget.popup.client.PopupSize;
import stroom.widget.popup.client.PopupType;

import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

import java.util.Objects;

/// The base of a dashboard pane's controller: it holds the pane's config, shows the pane's settings
/// dialog, and is shown (its component, given to `initWidget`) as a tab of the dashboard's layout.
/// A subclass reads the pane's settings into its component and gives the pane's type.
public abstract class DashboardPaneController extends LayerController implements DashboardPane {

    private final Provider<? extends PaneSettingsController> settingsProvider;
    private TabLayout tabLayout;
    private ComponentConfig componentConfig;
    private MutableTabConfig tabConfig;
    private PaneSettingsController settingsDialog;
    private DashboardContext dashboardContext;
    private boolean designMode;

    private Runnable onChange = () -> {
    };

    /// @param eventBus         The application's event bus.
    /// @param settingsProvider Makes the pane's settings dialog, or `null` if it has none.
    protected DashboardPaneController(
            final EventBus eventBus,
            final Provider<? extends PaneSettingsController> settingsProvider) {
        super(eventBus);
        this.settingsProvider = settingsProvider;
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
        if (settingsDialog != null) {
            componentConfig = settingsDialog.write(componentConfig);
        }
        return componentConfig;
    }

    @Override
    public ComponentConfig getComponentConfig() {
        return componentConfig;
    }

    /// @return The pane's settings.
    public ComponentSettings getSettings() {
        return componentConfig.getSettings();
    }

    /// @param componentSettings The pane's new settings.
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
            // Make sure the settings dialog has the latest name.
            if (settingsDialog != null) {
                settingsDialog.read(componentConfig);
            }
            changeSettings();
        }
    }

    @Override
    public void showSettings() {
        if (settingsProvider != null) {
            if (settingsDialog == null) {
                // Owned, so that it goes with the pane and sees its dashboard's context
                settingsDialog = make(settingsProvider);
            }

            settingsDialog.setDashboardContext(dashboardContext);
            settingsDialog.read(componentConfig);

            final PopupSize popupSize = PopupSize.resizable(800, 700);
            ControllerDialog.show(settingsDialog)
                    .popupType(PopupType.OK_CANCEL_DIALOG)
                    .popupSize(popupSize)
                    .caption("Settings")
                    .onShow(e -> settingsDialog.focus())
                    .onHideRequest(e -> {
                        if (e.isOk()) {
                            if (settingsDialog.validate()) {
                                final boolean dirty = settingsDialog.isDirty(componentConfig);
                                componentConfig = settingsDialog.write(componentConfig);

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

    /// Called when the pane's settings change: refreshes its tab (its name may have changed) and
    /// tells the dashboard. A subclass shows the new settings too.
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

    /// Tells the dashboard that the pane changed.
    protected void onChange() {
        onChange.run();
    }

    @Override
    public void setOnChange(final Runnable onChange) {
        this.onChange = onChange;
    }

    @Override
    public void onClose() {
        // Disposed with its dashboard
    }

    @Override
    public void onRemove() {
        // Disposed by its dashboard (Panes.remove), which owns it
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
    public MutableTabConfig getTabConfig() {
        return tabConfig;
    }

    @Override
    public void setTabConfig(final MutableTabConfig tabConfig) {
        this.tabConfig = tabConfig;
    }

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

    @Override
    public void setDesignMode(final boolean designMode) {
        this.designMode = designMode;
    }

    /// @return Whether the dashboard is being designed, rather than used.
    protected boolean isDesignMode() {
        return designMode;
    }
}
