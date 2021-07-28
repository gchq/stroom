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
import stroom.widget.menu.client.presenter.IconParentMenuItem;
import stroom.widget.menu.client.presenter.Item;
import stroom.widget.menu.client.presenter.ShowMenuEvent;
import stroom.widget.popup.client.presenter.PopupPosition;
import stroom.widget.popup.client.presenter.PopupPosition.VerticalLocation;

import com.google.gwt.dom.client.Element;
import com.google.gwt.user.client.Event;
import com.google.gwt.user.client.Timer;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Provider;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class TabManager {

    private final Components components;
    private final Provider<RenameTabPresenter> renameTabPresenterProvider;
    private final DashboardPresenter dashboardPresenter;

    private FlexLayout flexLayout;
    private TabLayout tabLayout;

    public TabManager(final Components components,
                      final Provider<RenameTabPresenter> renameTabPresenterProvider,
                      final DashboardPresenter dashboardPresenter) {
        this.components = components;
        this.renameTabPresenterProvider = renameTabPresenterProvider;
        this.dashboardPresenter = dashboardPresenter;
    }

    public void onMouseUp(final Event event,
                          final Widget tabWidget,
                          final FlexLayout flexLayout,
                          final TabLayout tabLayout,
                          final int index) {
        this.flexLayout = flexLayout;
        this.tabLayout = tabLayout;

        final TabConfig tabConfig = tabLayout.getTabLayoutConfig().get(index);
        final Component component = components.get(tabConfig.getId());
        if (component != null) {
            final ComponentConfig componentConfig = component.getComponentConfig();
            final Consumer<String> nameChangeConsumer = component::setComponentName;

            new Timer() {
                @Override
                public void run() {
                    final Element target = tabWidget.getElement();
                    final PopupPosition popupPosition = new PopupPosition(target.getAbsoluteLeft(),
                            target.getAbsoluteRight(), target.getAbsoluteTop(), target.getAbsoluteBottom(), null,
                            VerticalLocation.BELOW);
                    final List<Item> menuItems = updateMenuItems(tabLayout.getTabLayoutConfig(),
                            tabConfig,
                            componentConfig,
                            nameChangeConsumer);
                    ShowMenuEvent.fire(dashboardPresenter,
                            menuItems,
                            popupPosition);
                }
            }.schedule(0);
        }
    }


    public void showRename(final ComponentConfig componentConfig,
                           final Consumer<String> nameChangeConsumer) {
        renameTabPresenterProvider.get().show(dashboardPresenter,
                tabLayout,
                componentConfig.getName(),
                nameChangeConsumer);
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

    private List<Item> updateMenuItems(final TabLayoutConfig tabLayoutConfig,
                                       final TabConfig tabConfig,
                                       final ComponentConfig componentConfig,
                                       final Consumer<String> nameChangeConsumer) {
        final List<Item> menuItems = new ArrayList<>();

        // Create rename menu.
        menuItems.add(createRenameMenu(componentConfig, nameChangeConsumer));

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

        return menuItems;
    }

    private Item createRenameMenu(final ComponentConfig componentConfig,
                                  final Consumer<String> nameChangeConsumer) {
        return new IconMenuItem.Builder()
                .priority(0)
                .icon(SvgPresets.EDIT)
                .text("Rename")
                .command(() -> showRename(componentConfig, nameChangeConsumer))
                .build();
    }

    private Item createSettingsMenu(final TabConfig tabConfig) {
        return new IconMenuItem.Builder()
                .priority(1)
                .icon(SvgPresets.SETTINGS_BLUE)
                .text("Settings")
                .command(() -> showSettings(tabConfig))
                .build();
    }

    private Item createHideMenu(final TabLayoutConfig tabLayoutConfig, final TabConfig tabConfig) {
        return new IconMenuItem.Builder()
                .priority(6)
                .icon(SvgPresets.HIDE)
                .text("Hide")
                .command(() -> hideTab(tabLayoutConfig, tabConfig))
                .build();
    }

    private Item createShowMenu(final TabLayoutConfig tabLayoutConfig) {
        final List<Item> menuItems = new ArrayList<>();

        int i = 0;
        for (final TabConfig tc : tabLayoutConfig.getTabs()) {
            if (!tc.visible()) {
                final Component component = components.get(tc.getId());
                if (component != null) {
                    final Item item2 = new IconMenuItem.Builder()
                            .priority(i++)
                            .icon(SvgPresets.SHOW)
                            .text(component.getComponentConfig().getName())
                            .command(() -> showTab(tc))
                            .build();
                    menuItems.add(item2);
                }
            }
        }

        if (menuItems.size() == 0) {
            return null;
        }

        return new IconParentMenuItem.Builder()
                .priority(7)
                .icon(SvgPresets.SHOW)
                .text("Show")
                .children(menuItems)
                .build();
    }

    private Item createRemoveMenu(final TabLayoutConfig tabLayoutConfig, final TabConfig tabConfig) {
        return new IconMenuItem.Builder()
                .priority(8)
                .icon(SvgPresets.DELETE)
                .text("Close")
                .command(() -> closeTab(tabLayoutConfig, tabConfig))
                .build();
    }
}
