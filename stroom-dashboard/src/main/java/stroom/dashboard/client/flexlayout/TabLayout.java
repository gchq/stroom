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

import com.google.gwt.core.shared.GWT;
import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.logical.shared.SelectionEvent;
import com.google.gwt.event.logical.shared.SelectionHandler;
import com.google.gwt.resources.client.ClientBundle;
import com.google.gwt.resources.client.CssResource;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.ProvidesResize;
import com.google.gwt.user.client.ui.RequiresResize;
import com.gwtplatform.mvp.client.HandlerRegistrations;
import stroom.app.client.StroomStyleNames;
import stroom.dashboard.client.main.Component;
import stroom.dashboard.shared.DashboardConfig.TabVisibility;
import stroom.dashboard.shared.TabConfig;
import stroom.dashboard.shared.TabLayoutConfig;
import stroom.widget.button.client.GlyphButton;
import stroom.widget.button.client.GlyphIcon;
import stroom.widget.tab.client.presenter.LayerContainer;
import stroom.widget.tab.client.presenter.TabData;
import stroom.widget.tab.client.view.LayerContainerImpl;
import stroom.widget.tab.client.view.LinkTabBar;

public class TabLayout extends Composite implements RequiresResize, ProvidesResize {
    public static final GlyphIcon.ColourSet SETTINGS_COLOUR_SET = GlyphIcon.ColourSet.create("#ddd", "#9E9E9E");
    public static final GlyphIcon SETTINGS = new GlyphIcon("fa fa-cog", SETTINGS_COLOUR_SET, "Settings", true);
    public static final GlyphIcon.ColourSet CLOSE_COLOUR_SET = GlyphIcon.ColourSet.create("#ddd", "#D32F2F");
    public static final GlyphIcon CLOSE = new GlyphIcon("fa fa-times", CLOSE_COLOUR_SET, "Settings", true);

    private static Resources resources;

    private final TabLayoutConfig tabLayoutData;
    private final FlexLayoutChangeHandler changeHandler;
    private final FlowPanel panel;
    private final FlowPanel contentOuter;
    private final FlowPanel contentInner;
    private final FlowPanel barOuter;
    private final FlowPanel buttons;
    private final GlyphButton settings;
    private final GlyphButton close;
    private final LinkTabBar tabBar;
    private final LayerContainer layerContainer;
    private final HandlerRegistrations handlerRegistrations = new HandlerRegistrations();

    private TabVisibility tabVisibility = TabVisibility.SHOW_ALL;
    private boolean tabsVisible = true;

    public TabLayout(final TabLayoutConfig tabLayoutData, final FlexLayoutChangeHandler changeHandler) {
        this.tabLayoutData = tabLayoutData;
        this.changeHandler = changeHandler;

        if (resources == null) {
            resources = GWT.create(Resources.class);
            resources.style().ensureInjected();
        }

        panel = new FlowPanel();
        panel.setStyleName(resources.style().tabLayout());
        initWidget(panel);

        contentOuter = new FlowPanel();
        contentOuter.setStyleName(resources.style().contentOuter());
        panel.add(contentOuter);

        contentInner = new FlowPanel();
        contentInner.setStyleName(resources.style().contentInner() + " " + StroomStyleNames.STROOM_CONTENT);
        contentOuter.add(contentInner);

        barOuter = new FlowPanel();
        barOuter.setStyleName(resources.style().barOuter());
        contentInner.add(barOuter);

        tabBar = new LinkTabBar();
        tabBar.addStyleName(resources.style().barInner());
        barOuter.add(tabBar);

        buttons = new FlowPanel();
        buttons.setStyleName(resources.style().buttons());
        contentInner.add(buttons);

        settings = GlyphButton.create(SETTINGS);
        buttons.add(settings);
        close = GlyphButton.create(CLOSE);
        buttons.add(close);

        final LayerContainerImpl layerContainerImpl = new LayerContainerImpl();
        layerContainerImpl.setFade(true);
        layerContainerImpl.setStyleName(resources.style().content());
        contentInner.add(layerContainerImpl);

        layerContainer = layerContainerImpl;

        bind();
    }

    public void bind() {
        handlerRegistrations.add(tabBar.addSelectionHandler(event -> selectTab(event.getSelectedItem())));

        handlerRegistrations.add(settings.addDomHandler(event -> {
            if ((event.getNativeButton() & NativeEvent.BUTTON_LEFT) != 0) {
                final TabData selectedTab = tabBar.getSelectedTab();
                if (selectedTab != null && selectedTab instanceof Component) {
                    final Component component = (Component) selectedTab;
                    component.showSettings();
                }
            }
        }, ClickEvent.getType()));

        handlerRegistrations.add(close.addDomHandler(event -> {
            if ((event.getNativeButton() & NativeEvent.BUTTON_LEFT) != 0) {
                final TabData selectedTab = tabBar.getSelectedTab();
                if (selectedTab != null && selectedTab instanceof Component) {
                    final Component component = (Component) selectedTab;
                    changeHandler.requestTabClose(component.getTabConfig());
                }
            }
        }, ClickEvent.getType()));
    }

    public void unbind() {
        handlerRegistrations.removeHandler();
    }

    public void addTab(final TabConfig tabConfig, final Component component) {
        tabBar.addTab(component);

        component.setTabLayout(this);
        component.setTabConfig(tabConfig);

        checkTabVisibility();

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
        Component component = null;
        if (tabData != null && tabData instanceof Component) {
            component = (Component) tabData;
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

    public TabLayoutConfig getTabLayoutData() {
        return tabLayoutData;
    }

    public void setTabVisibility(final TabVisibility tabVisibility) {
        this.tabVisibility = tabVisibility;
        checkTabVisibility();
    }

    private void checkTabVisibility() {
        if (tabVisibility == TabVisibility.SHOW_ALL) {
            setTabsVisibile(true);
        } else if (tabVisibility == TabVisibility.HIDE_ALL) {
            setTabsVisibile(false);
        } else if (tabVisibility == TabVisibility.HIDE_SINGLE) {
            setTabsVisibile(tabBar.getTabs().size() > 1);
        }
    }

    private void setTabsVisibile(final boolean tabsVisible) {
        if (this.tabsVisible != tabsVisible) {
            this.tabsVisible = tabsVisible;
        }
    }

    public interface Style extends CssResource {
        String tabLayout();

        String contentOuter();

        String contentInner();

        String barOuter();

        String barInner();

        String buttons();

        String content();
    }

    public interface Resources extends ClientBundle {
        @Source("TabLayout.css")
        Style style();
    }
}
