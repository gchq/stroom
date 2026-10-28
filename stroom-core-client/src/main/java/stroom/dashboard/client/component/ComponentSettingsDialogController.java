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

package stroom.dashboard.client.component;

import stroom.dashboard.client.main.ComponentDataModifier;
import stroom.dashboard.client.main.DashboardContext;
import stroom.dashboard.shared.ComponentConfig;
import stroom.widget.component.client.Controller;
import stroom.widget.component.client.Layer;
import stroom.widget.form.client.HasInvalidInput;
import stroom.widget.form.client.InvalidInputs;
import stroom.widget.form.client.RefusesInvalidInput;
import stroom.widget.tab.client.TabData;
import stroom.widget.tab.client.TabDataImpl;

import com.google.gwt.user.client.ui.Focus;
import com.google.gwt.user.client.ui.IsWidget;
import com.google.web.bindery.event.shared.EventBus;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

/// A dashboard pane's settings dialog ([ComponentSettingsDialog]): its tabs read the pane's config,
/// write it back, and say whether it is valid and changed. A subclass adds the pane's tabs; a tab
/// that is a [ComponentDataModifier] reads and writes the config, and a hidden tab doesn't.
///
/// The pane keeps it and shows it again each time, with `ControllerDialog.show`.
public class ComponentSettingsDialogController extends Controller implements Focus, RefusesInvalidInput {

    private final ComponentSettingsDialog view;
    private final Map<TabData, Layer> tabViewMap = new HashMap<>();
    private final Map<TabData, ComponentDataModifier> modifiers = new HashMap<>();

    private TabData firstTab;
    private TabData selectedTab;
    private boolean firstShowing = true;

    /// @param eventBus The application's event bus.
    /// @param view     The content.
    public ComponentSettingsDialogController(final EventBus eventBus, final ComponentSettingsDialog view) {
        super(eventBus);
        this.view = view;
        initWidget(view);
        view.setOnTabSelected(tab -> {
            if (tab != null && tab != selectedTab) {
                changeSelectedTab(tab);
            }
        });
        view.setOnShowMenu(event -> getEventBus().fireEvent(event));
    }

    @Override
    public void focus() {
        if (selectedTab != null) {
            final Layer layer = tabViewMap.get(selectedTab);
            if (layer instanceof final Focus focus) {
                focus.focus();
            }
        } else {
            view.focusTabBar();
        }
    }

    /// Adds a tab.
    ///
    /// @param text  The tab's label.
    /// @param layer Its content; if a [ComponentDataModifier], it reads and writes the pane's config.
    /// @return The tab.
    public TabData addTab(final String text, final Layer layer) {
        final TabData tab = new TabDataImpl(text, false);
        tabViewMap.put(tab, layer);
        view.addTab(tab);

        if (layer instanceof final ComponentDataModifier componentDataModifier) {
            modifiers.put(tab, componentDataModifier);
        }

        if (firstTab == null) {
            firstTab = tab;
        }

        return tab;
    }

    /// Takes a tab off, showing the first.
    ///
    /// @param tab The tab.
    public void removeTab(final TabData tab) {
        view.removeTab(tab);
        changeSelectedTab(firstTab);
    }

    /// Gives the tabs the dashboard's context, e.g. to list its other panes, and shows the first tab
    /// the first time.
    ///
    /// @param dashboardContext The dashboard's context.
    public void setDashboardContext(final DashboardContext dashboardContext) {
        for (final ComponentDataModifier modifier : modifiers.values()) {
            modifier.setDashboardContext(dashboardContext);
        }

        if (firstShowing) {
            firstShowing = false;
            if (firstTab != null) {
                changeSelectedTab(firstTab);
            }
        }
    }

    /// @param componentConfig The pane's config, for the tabs to show.
    public void read(final ComponentConfig componentConfig) {
        for (final ComponentDataModifier modifier : modifiers.values()) {
            modifier.read(componentConfig);
        }
    }

    /// @param componentConfig The pane's config.
    /// @return A copy with what the shown tabs hold.
    public ComponentConfig write(final ComponentConfig componentConfig) {
        ComponentConfig result = componentConfig;
        for (final Entry<TabData, ComponentDataModifier> entry : modifiers.entrySet()) {
            if (!view.isTabHidden(entry.getKey())) {
                result = entry.getValue().write(result);
            }
        }
        return result;
    }

    /// Refuses OK while a tab holds a field whose input can't be kept, e.g. text in Maximum Results
    /// that isn't a list of numbers: shows that tab, says why under the field and focuses it. Every
    /// tab is looked in, as only the one showing is on the page.
    ///
    /// @return Whether it refused.
    @Override
    public boolean refuseInvalidInput() {
        for (final Entry<TabData, Layer> entry : tabViewMap.entrySet()) {
            if (!view.isTabHidden(entry.getKey()) && entry.getValue() instanceof final IsWidget content) {
                final HasInvalidInput input = InvalidInputs.find(content.asWidget());
                if (input != null) {
                    changeSelectedTab(entry.getKey());
                    InvalidInputs.show(input);
                    return true;
                }
            }
        }
        return false;
    }

    /// @return Whether every tab's values are valid.
    public boolean validate() {
        return modifiers.values().stream().allMatch(ComponentDataModifier::validate);
    }

    /// @param componentConfig The pane's config.
    /// @return Whether a shown tab holds something different.
    public boolean isDirty(final ComponentConfig componentConfig) {
        for (final Entry<TabData, ComponentDataModifier> entry : modifiers.entrySet()) {
            if (!view.isTabHidden(entry.getKey())) {
                if (entry.getValue().isDirty(componentConfig)) {
                    return true;
                }
            }
        }

        return false;
    }

    private void changeSelectedTab(final TabData tab) {
        if (selectedTab != tab) {
            selectedTab = tab;
            if (selectedTab != null) {
                final Layer layer = tabViewMap.get(selectedTab);
                if (layer != null) {
                    view.showTab(tab, layer);
                }
            }
        }
    }
}
