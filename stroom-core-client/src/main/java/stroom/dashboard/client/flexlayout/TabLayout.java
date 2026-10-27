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
import stroom.dashboard.client.main.TabManager;
import stroom.item.client.EventBinder;
import stroom.svg.shared.SvgImage;
import stroom.widget.button.client.Toolbar;
import stroom.widget.button.client.ToolbarButton;
import stroom.widget.presenter.client.LayerContainer;
import stroom.widget.tab.client.presenter.TabData;
import stroom.widget.tab.client.view.LayerContainerImpl;
import stroom.widget.tab.client.view.LinkTabBar;
import stroom.widget.util.client.MouseUtil;

import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.ProvidesResize;
import com.google.gwt.user.client.ui.RequiresResize;
import com.google.web.bindery.event.shared.EventBus;

public class TabLayout extends Composite implements RequiresResize, ProvidesResize {

    private final EventBus eventBus;
    private final MutableTabLayoutConfig tabLayoutConfig;
    private final FlexLayout flexLayout;
    private final TabManager tabManager;
    private final FlexLayoutChangeHandler changeHandler;
    private final ToolbarButton settings;
    private final LinkTabBar tabBar;
    private final LayerContainer layerContainer;
    private final EventBinder eventBinder = new EventBinder() {
        @Override
        protected void onBind() {
            registerHandler(tabBar.addSelectionHandler(event -> {
                final TabData selected = event.getSelectedItem();
                selectTab(selected);
                final int index = tabBar.getTabs().indexOf(selected);
                getTabLayoutConfig().setSelected(index);
                changeHandler.onChange();
            }));
            registerHandler(tabBar.addShowMenuHandler(eventBus::fireEvent));
            registerHandler(settings.addDomHandler(event -> {
                if (MouseUtil.isPrimary(event)) {
                    final TabData selectedTab = tabBar.getSelectedTab();
                    if (selectedTab instanceof final DashboardPane component) {
//                        component.showSettings();
                        tabManager.showMenu(settings.getElement(),
                                flexLayout,
                                TabLayout.this,
                                component.getTabConfig());
                    }
                }
            }, ClickEvent.getType()));
        }
    };

    public TabLayout(final EventBus eventBus,
                     final FlexLayout flexLayout,
                     final TabManager tabManager,
                     final MutableTabLayoutConfig tabLayoutConfig,
                     final FlexLayoutChangeHandler changeHandler) {
        this.eventBus = eventBus;
        this.flexLayout = flexLayout;
        this.tabManager = tabManager;

        this.tabLayoutConfig = tabLayoutConfig;
        this.changeHandler = changeHandler;

        tabBar = new LinkTabBar();
        tabBar.addStyleName("dock-max tabLayout-tabBar");

        final FlowPanel tabContainer = new FlowPanel();
        tabContainer.setStyleName("tabLayout-tabContainer");
        tabContainer.add(tabBar);

        final FlowPanel barOuter = new FlowPanel();
        barOuter.setStyleName("tabLayout-barOuter");
        barOuter.add(tabContainer);

        final FlowPanel contentInner = new FlowPanel();
        contentInner.setStyleName("tabLayout-contentInner");
        contentInner.add(barOuter);

        final FlowPanel contentOuter = new FlowPanel();
        contentOuter.setStyleName("tabLayout-contentOuter dashboard-panel");
        contentOuter.add(contentInner);

        final FlowPanel panel = new FlowPanel();
        panel.addStyleName("tabLayout");
        panel.add(contentOuter);
        initWidget(panel);

        // Shown when the pane is hovered over or has the focus (tabLayout-buttons)
        final Toolbar buttons = new Toolbar("Pane actions");
        buttons.addStyleName("dock-min tabLayout-buttons icon-colour__grey");
        barOuter.add(buttons);

        settings = buttons.button(SvgImage.ELLIPSES_VERTICAL, "Tab Actions", null);
        settings.addStyleName("tabLayout-settingsButton");
        settings.setOpensMenu(true);

        final LayerContainerImpl layerContainerImpl = new LayerContainerImpl();
        layerContainerImpl.setFade(true);
        layerContainerImpl.setStyleName("tabLayout-content");
        contentInner.add(layerContainerImpl);

        layerContainer = layerContainerImpl;
        tabBar.setTabPanel(layerContainerImpl.getElement());

        bind();
    }

    public void bind() {
        eventBinder.bind();
    }

    public void unbind() {
        eventBinder.unbind();
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
}
