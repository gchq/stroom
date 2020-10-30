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
 */

package stroom.dashboard.client.main;

import stroom.alert.client.event.AlertEvent;
import stroom.dashboard.client.flexlayout.FlexLayout;
import stroom.dashboard.client.flexlayout.TabLayout;
import stroom.dashboard.shared.ComponentConfig;
import stroom.dashboard.shared.TabConfig;
import stroom.dashboard.shared.TabLayoutConfig;
import stroom.svg.client.SvgPresets;
import stroom.widget.menu.client.presenter.IconMenuItem;
import stroom.widget.menu.client.presenter.Item;
import stroom.widget.menu.client.presenter.MenuListPresenter;
import stroom.widget.menu.client.presenter.SimpleParentMenuItem;
import stroom.widget.popup.client.event.HidePopupEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupPosition;
import stroom.widget.popup.client.presenter.PopupPosition.VerticalLocation;
import stroom.widget.popup.client.presenter.PopupUiHandlers;
import stroom.widget.popup.client.presenter.PopupView.PopupType;

import com.google.gwt.dom.client.Element;
import com.google.gwt.user.client.Timer;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Provider;

import java.util.ArrayList;
import java.util.List;

public class TabManager {
    private final Components components;
    private final Provider<RenameTabPresenter> renameTabPresenterProvider;
    private final MenuListPresenter menuListPresenter;
    private final DashboardPresenter dashboardPresenter;

    private FlexLayout flexLayout;
    private TabLayout tabLayout;
    private TabConfig currentTabConfig;

    public TabManager(final Components components,
                      final MenuListPresenter menuListPresenter,
                      final Provider<RenameTabPresenter> renameTabPresenterProvider,
                      final DashboardPresenter dashboardPresenter) {
        this.components = components;
        this.menuListPresenter = menuListPresenter;
        this.renameTabPresenterProvider = renameTabPresenterProvider;
        this.dashboardPresenter = dashboardPresenter;
    }

    public void onMouseUp(final Widget tabWidget, final FlexLayout flexLayout, final TabLayout tabLayout, final int index) {
        this.flexLayout = flexLayout;
        this.tabLayout = tabLayout;

        final TabConfig tabConfig = tabLayout.getTabLayoutConfig().get(index);
        final Component component = components.get(tabConfig.getId());
        if (component != null) {
            final ComponentConfig componentConfig = component.getComponentConfig();

            new Timer() {
                @Override
                public void run() {
                    if (currentTabConfig == tabConfig) {
                        currentTabConfig = null;
                        HidePopupEvent.fire(dashboardPresenter, menuListPresenter);

                    } else {
                        currentTabConfig = tabConfig;
                        final Element target = tabWidget.getElement();
                        final PopupPosition popupPosition = new PopupPosition(target.getAbsoluteLeft(),
                                target.getAbsoluteRight(), target.getAbsoluteTop(), target.getAbsoluteBottom(), null,
                                VerticalLocation.BELOW);

//                        final PopupPosition popupPosition = new PopupPosition(event.getClientX(),
//                                event.getClientX(), event.getClientY(), event.getClientY(), null,
//                                VerticalLocation.BELOW);
                        final PopupUiHandlers popupUiHandlers = new PopupUiHandlers() {
                            @Override
                            public void onHideRequest(final boolean autoClose, final boolean ok) {
                                HidePopupEvent.fire(dashboardPresenter, menuListPresenter);
                            }

                            @Override
                            public void onHide(final boolean autoClose, final boolean ok) {
                                currentTabConfig = null;
                            }
                        };

                        updateMenuItems(tabLayout.getTabLayoutConfig(), tabConfig, componentConfig);

//                            Element element = event.getEventTarget().cast();
//                            while (!element.getTagName().toLowerCase().equals("th")) {
//                                element = element.getParentElement();
//                            }

                        ShowPopupEvent.fire(dashboardPresenter, menuListPresenter, PopupType.POPUP, popupPosition,
                                popupUiHandlers, target);
                    }
                }
            }.schedule(0);
        }
    }


    public void showRename(final ComponentConfig componentConfig) {
        renameTabPresenterProvider.get().show(dashboardPresenter, tabLayout, componentConfig);
    }

    public void showSettings(final TabConfig tabConfig) {
        final Component component = components.get(tabConfig.getId());
        if (component != null) {
            component.showSettings();
        }
    }

    private void closeTab(final TabLayoutConfig tabLayoutConfig, final TabConfig tabConfig) {
        dashboardPresenter.requestTabClose(tabLayoutConfig, tabConfig);
    }

    private void showTab(final TabConfig tabConfig) {
        tabConfig.setVisible(true);
        flexLayout.clear();
        flexLayout.refresh();
        dashboardPresenter.onDirty();
    }

    private void hideTab(final TabLayoutConfig tabLayoutConfig, final TabConfig tabConfig) {
        if (tabLayoutConfig.getVisibleTabCount() <= 1) {
            AlertEvent.fireError(dashboardPresenter, "You cannot remove or hide all tabs", null);
        } else {
            tabConfig.setVisible(false);
            flexLayout.clear();
            flexLayout.refresh();
            dashboardPresenter.onDirty();
        }
    }

    private void updateMenuItems(final TabLayoutConfig tabLayoutConfig, final TabConfig tabConfig, final ComponentConfig componentConfig) {
        final List<Item> menuItems = new ArrayList<>();

        // Create rename menu.
        menuItems.add(createRenameMenu(componentConfig));

        // Create settings menu.
        menuItems.add(createSettingsMenu(tabConfig));

        // Create hide menu.
        menuItems.add(createHideMenu(tabLayoutConfig, tabConfig));

        // Create show menu.
        Item showMenu = createShowMenu(tabLayoutConfig);
        if (showMenu != null) {
            menuItems.add(showMenu);
        }

        // Create remove menu.
        menuItems.add(createRemoveMenu(tabLayoutConfig, tabConfig));

        menuListPresenter.setData(menuItems);
    }

    private Item createRenameMenu(final ComponentConfig componentConfig) {
        return new IconMenuItem(0, SvgPresets.EDIT, SvgPresets.EDIT, "Rename", null, true, () -> showRename(componentConfig));
    }

    private Item createSettingsMenu(final TabConfig tabConfig) {
        return new IconMenuItem(1, SvgPresets.SETTINGS_BLUE, SvgPresets.SETTINGS_BLUE, "Settings", null, true, () -> showSettings(tabConfig));
    }

    private Item createHideMenu(final TabLayoutConfig tabLayoutConfig, final TabConfig tabConfig) {
        return new IconMenuItem(6, SvgPresets.HIDE, SvgPresets.HIDE, "Hide", null, true, () -> hideTab(tabLayoutConfig, tabConfig));
    }

    private Item createShowMenu(final TabLayoutConfig tabLayoutConfig) {
        final List<Item> menuItems = new ArrayList<>();

        int i = 0;
        for (final TabConfig tc : tabLayoutConfig.getTabs()) {
            if (!tc.visible()) {
                final Component component = components.get(tc.getId());
                if (component != null) {
                    final Item item2 = new IconMenuItem(i++, SvgPresets.SHOW, SvgPresets.SHOW, component.getComponentConfig().getName(), null, true,
                            () -> showTab(tc));
                    menuItems.add(item2);
                }
            }
        }

        if (menuItems.size() == 0) {
            return null;
        }

        return new SimpleParentMenuItem(7, SvgPresets.SHOW, SvgPresets.SHOW, "Show", null, true, menuItems);
    }

    private Item createRemoveMenu(final TabLayoutConfig tabLayoutConfig, final TabConfig tabConfig) {
        return new IconMenuItem(8, SvgPresets.DELETE, SvgPresets.DELETE, "Close", null, true, () -> closeTab(tabLayoutConfig, tabConfig));
    }
}
