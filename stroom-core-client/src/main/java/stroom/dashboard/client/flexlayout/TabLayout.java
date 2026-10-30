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

package stroom.dashboard.client.flexlayout;

import stroom.dashboard.client.main.DashboardPane;
import stroom.dashboard.client.model.MutableTabConfig;
import stroom.dashboard.client.model.MutableTabLayoutConfig;
import stroom.widget.button.client.ToolbarButton;
import stroom.widget.component.client.LayerContainer;
import stroom.widget.tab.client.LinkTabBar;
import stroom.widget.tab.client.TabData;
import stroom.widget.util.client.MouseUtil;

import com.google.gwt.core.client.GWT;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.ProvidesResize;
import com.google.gwt.user.client.ui.RequiresResize;
import com.google.gwt.user.client.ui.Widget;

public class TabLayout extends Composite implements RequiresResize, ProvidesResize {

    private static final Binder BINDER = GWT.create(Binder.class);

    private final MutableTabLayoutConfig tabLayoutConfig;
    private final FlexLayout flexLayout;
    private final FlexLayoutChangeHandler changeHandler;
    @UiField
    ToolbarButton settings;
    @UiField
    LinkTabBar tabBar;
    @UiField
    LayerContainer layerContainer;

    /// @param flexLayout      The layout the pane is in, which shows its tabs' menus.
    /// @param tabLayoutConfig The pane's tabs.
    /// @param changeHandler   Told when the user selects another tab.
    public TabLayout(final FlexLayout flexLayout,
                     final MutableTabLayoutConfig tabLayoutConfig,
                     final FlexLayoutChangeHandler changeHandler) {
        this.flexLayout = flexLayout;
        this.tabLayoutConfig = tabLayoutConfig;
        this.changeHandler = changeHandler;

        initWidget(BINDER.createAndBindUi(this));
        tabBar.setTabPanel(layerContainer.getElement());

        tabBar.addSelectionHandler(event -> {
            final TabData selected = event.getSelectedItem();
            selectTab(selected);
            final int index = tabBar.getTabs().indexOf(selected);
            getTabLayoutConfig().setSelected(index);
            changeHandler.onChange();
        });
        tabBar.addShowMenuHandler(flexLayout::showMenu);
        settings.addDomHandler(event -> {
            if (MouseUtil.isPrimary(event)) {
                final TabData selectedTab = tabBar.getSelectedTab();
                if (selectedTab instanceof final DashboardPane component) {
                    flexLayout.showTabMenu(settings.getElement(),
                            this,
                            component.getTabConfig());
                }
            }
        }, ClickEvent.getType());
    }

    public void addTab(final MutableTabConfig tabConfig, final DashboardPane component) {
        tabBar.addTab(component);

        component.setTabLayout(this);
        component.setTabConfig(tabConfig);

        layerContainer.show(component);
    }

    public void selectTab(final int index) {
        if (index >= 0 && getTabBar().getTabs().size() > index) {
            final TabData tabData = getTabBar().getTabs().get(index);
            selectTab(tabData);
        }
    }

    public void selectTab(final TabData tabData) {
        tabBar.selectTab(tabData);
        DashboardPane component = null;
        if (tabData instanceof DashboardPane) {
            component = (DashboardPane) tabData;
        }

        layerContainer.show(component);
    }

    public void refresh() {
        tabBar.refresh();
        onResize();
    }

    @Override
    public void onResize() {
        tabBar.onResize();
        layerContainer.onResize();
    }

    public void clear() {
        layerContainer.clear();
    }

    public LinkTabBar getTabBar() {
        return tabBar;
    }

    public MutableTabLayoutConfig getTabLayoutConfig() {
        return tabLayoutConfig;
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, TabLayout> {

    }
}
