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

import stroom.alert.client.event.AlertEvent;
import stroom.dashboard.client.embeddedquery.EmbeddedQueryPresenter;
import stroom.dashboard.client.flexlayout.FlexLayout;
import stroom.dashboard.client.flexlayout.TabLayout;
import stroom.dashboard.shared.ComponentConfig;
import stroom.dashboard.shared.EmbeddedQueryComponentSettings;
import stroom.dashboard.shared.TabConfig;
import stroom.dashboard.shared.TabLayoutConfig;
import stroom.svg.client.IconColour;
import stroom.svg.shared.SvgImage;
import stroom.widget.menu.client.presenter.HideMenuEvent;
import stroom.widget.menu.client.presenter.IconMenuItem;
import stroom.widget.menu.client.presenter.IconParentMenuItem;
import stroom.widget.menu.client.presenter.Item;
import stroom.widget.menu.client.presenter.ShowMenuEvent;
import stroom.widget.popup.client.presenter.PopupPosition;
import stroom.widget.popup.client.presenter.PopupPosition.PopupLocation;
import stroom.widget.util.client.Rect;

import com.google.gwt.dom.client.Element;
import com.google.inject.Provider;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

public class TabManager {

    private final Components components;
    private final Provider<RenameTabPresenter> renameTabPresenterProvider;
    private final DashboardPresenter dashboardPresenter;

    private FlexLayout flexLayout;
    private TabLayout tabLayout;
    private TabConfig currentTabConfig;

    public TabManager(final Components components,
                      final Provider<RenameTabPresenter> renameTabPresenterProvider,
                      final DashboardPresenter dashboardPresenter) {
        this.components = components;
        this.renameTabPresenterProvider = renameTabPresenterProvider;
        this.dashboardPresenter = dashboardPresenter;
    }

    public void showMenu(final Element target,
                         final FlexLayout flexLayout,
                         final TabLayout tabLayout,
                         final TabConfig tabConfig) {
        this.flexLayout = flexLayout;
        this.tabLayout = tabLayout;

        final Component component = components.get(tabConfig.getId());
        if (component != null) {
            if (Objects.equals(currentTabConfig, tabConfig)) {
                HideMenuEvent.builder().fire(dashboardPresenter);

//            } else if (!dashboardPresenter.isDesignMode()) {
//                showSettings(tabConfig);

            } else {
//                if (currentTabConfig != null) {
//                    HideMenuEvent.builder().fire(dashboardPresenter);
//                }
//
                currentTabConfig = tabConfig;
//                new Timer() {
//                    @Override
//                    public void run() {
                Rect relativeRect = new Rect(target);
                relativeRect = relativeRect.grow(3);
                final PopupPosition popupPosition = new PopupPosition(
                        relativeRect,
                        PopupLocation.BELOW);
                final List<Item> menuItems = updateMenuItems(tabLayout.getTabLayoutConfig(),
                        tabConfig,
                        component);
                currentTabConfig = tabConfig;
                ShowMenuEvent
                        .builder()
                        .items(menuItems)
                        .addAutoHidePartner(target)
                        .popupPosition(popupPosition)
                        .onHide(e -> currentTabConfig = null)
                        .fire(dashboardPresenter);
//                    }
//                }.schedule(0);
            }
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

    public void duplicateTabTo(final TabLayoutConfig tabLayoutConfig, final TabConfig tabConfig) {
        dashboardPresenter.duplicateTabTo(tabLayoutConfig, tabConfig);
    }

    private void duplicateTab(final TabLayoutConfig tabLayoutConfig, final TabConfig tabConfig) {
        dashboardPresenter.duplicateTab(tabLayoutConfig, tabConfig);
    }

    private void duplicateTabPanel(final TabLayoutConfig tabLayoutConfig) {
        dashboardPresenter.duplicateTabPanel(tabLayoutConfig);
    }

    private void showTab(final TabConfig tabConfig) {
        tabConfig.setVisible(true);
        flexLayout.clear();
        flexLayout.refresh();
        dashboardPresenter.onDirty();
    }

    private void maximiseTab(final TabConfig tabConfig) {
        dashboardPresenter.maximiseTabs(tabConfig);
    }

    private void restoreTabs() {
        dashboardPresenter.restoreTabs();
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

    private void removeTab(final TabLayoutConfig tabLayoutConfig, final TabConfig tab) {
        dashboardPresenter.removeTab(tabLayoutConfig, tab);
    }

    private void removeTabPanel(final TabLayoutConfig tabLayoutConfig) {
        dashboardPresenter.removeTabPanel(tabLayoutConfig);
    }

    private List<Item> updateMenuItems(final TabLayoutConfig tabLayoutConfig,
                                       final TabConfig tabConfig,
                                       final Component component) {
        final ComponentConfig componentConfig = component.getComponentConfig();
        final Consumer<String> nameChangeConsumer = component::setComponentName;

        final List<Item> menuItems = new ArrayList<>();

        // Create rename menu.
        menuItems.add(createRenameMenu(componentConfig, nameChangeConsumer));

        // Create settings menu.
        menuItems.add(createSettingsMenu(tabConfig));

        if (!dashboardPresenter.isMaximised()) {
            // Create hide menu.
            menuItems.add(createHideMenu(tabLayoutConfig, tabConfig));

            // Create show menu.
            final Item showMenu = createShowMenu(tabLayoutConfig);
            if (showMenu != null) {
                menuItems.add(showMenu);
            }

            // Create duplicate menus.
            menuItems.add(createDuplicateMenu(tabLayoutConfig, tabConfig));
            menuItems.add(createDuplicateToMenu(tabLayoutConfig, tabConfig));
            if (tabLayoutConfig.getAllTabCount() > 1) {
                menuItems.add(createDuplicateTabPanelMenu(tabLayoutConfig));
            }

            // Create remove menus.
            menuItems.add(createRemoveMenu(tabLayoutConfig, tabConfig));
            if (tabLayoutConfig.getAllTabCount() > 1) {
                menuItems.add(createRemoveTabPanel(tabLayoutConfig));
            }
            menuItems.add(createMaximiseMenu(tabConfig));
        } else {
            menuItems.add(createRestoreMenu());
        }

        if (component instanceof EmbeddedQueryPresenter) {
            final EmbeddedQueryPresenter embeddedQueryPresenter = (EmbeddedQueryPresenter) component;
            final boolean showingVis = embeddedQueryPresenter.isShowingVis();
            final boolean canShowVis = embeddedQueryPresenter.canShowVis();
            if (showingVis || canShowVis) {
                menuItems.add(createShowTable(embeddedQueryPresenter, showingVis));
            }

            final EmbeddedQueryComponentSettings embeddedQueryComponentSettings =
                    (EmbeddedQueryComponentSettings) embeddedQueryPresenter.getSettings();
            if (embeddedQueryComponentSettings.getQueryRef() != null || !embeddedQueryComponentSettings.reference()) {
                menuItems.add(createEditQuery(embeddedQueryPresenter));
                menuItems.add(createRunQuery(embeddedQueryPresenter));
            }
        }

        return menuItems;
    }

    private Item createRenameMenu(final ComponentConfig componentConfig,
                                  final Consumer<String> nameChangeConsumer) {
        return new IconMenuItem.Builder()
                .priority(0)
                .icon(SvgImage.EDIT)
                .text("Rename")
                .command(() -> showRename(componentConfig, nameChangeConsumer))
                .build();
    }

    private Item createSettingsMenu(final TabConfig tabConfig) {
        return new IconMenuItem.Builder()
                .priority(1)
                .icon(SvgImage.SETTINGS)
                .text("Settings")
                .command(() -> showSettings(tabConfig))
                .build();
    }

    private Item createHideMenu(final TabLayoutConfig tabLayoutConfig, final TabConfig tabConfig) {
        return new IconMenuItem.Builder()
                .priority(6)
                .icon(SvgImage.HIDE)
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
                            .icon(SvgImage.SHOW)
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
                .icon(SvgImage.SHOW)
                .text("Show")
                .children(menuItems)
                .build();
    }

    private Item createDuplicateMenu(final TabLayoutConfig tabLayoutConfig, final TabConfig tabConfig) {
        return new IconMenuItem.Builder()
                .priority(8)
                .icon(SvgImage.COPY)
                .text("Duplicate")
                .command(() -> duplicateTab(tabLayoutConfig, tabConfig))
                .build();
    }

    private Item createDuplicateToMenu(final TabLayoutConfig tabLayoutConfig, final TabConfig tabConfig) {
        return new IconMenuItem.Builder()
                .priority(8)
                .icon(SvgImage.COPY)
                .text("Duplicate To...")
                .command(() -> duplicateTabTo(tabLayoutConfig, tabConfig))
                .build();
    }

    private Item createDuplicateTabPanelMenu(final TabLayoutConfig tabLayoutConfig) {
        return new IconMenuItem.Builder()
                .priority(9)
                .icon(SvgImage.COPY)
                .text("Duplicate All")
                .command(() -> duplicateTabPanel(tabLayoutConfig))
                .build();
    }

    private Item createRemoveMenu(final TabLayoutConfig tabLayoutConfig, final TabConfig tabConfig) {
        return new IconMenuItem.Builder()
                .priority(10)
                .icon(SvgImage.DELETE)
                .text("Remove")
                .command(() -> removeTab(tabLayoutConfig, tabConfig))
                .build();
    }

    private Item createRemoveTabPanel(final TabLayoutConfig tabLayoutConfig) {
        return new IconMenuItem.Builder()
                .priority(11)
                .icon(SvgImage.DELETE)
                .text("Remove All")
                .command(() -> removeTabPanel(tabLayoutConfig))
                .build();
    }

    private Item createMaximiseMenu(final TabConfig tabConfig) {
        return new IconMenuItem.Builder()
                .priority(10)
                .icon(SvgImage.MAXIMISE)
                .text("Maximise")
                .command(() -> maximiseTab(tabConfig))
                .build();
    }

    private Item createRestoreMenu() {
        return new IconMenuItem.Builder()
                .priority(10)
                .icon(SvgImage.MINIMISE)
                .text("Restore")
                .command(this::restoreTabs)
                .build();
    }

    private Item createShowTable(final EmbeddedQueryPresenter embeddedQueryPresenter,
                                 final boolean showingVis) {
        return new IconMenuItem.Builder()
                .priority(12)
                .icon(showingVis
                        ? SvgImage.TABLE
                        : SvgImage.DOCUMENT_VISUALISATION)
                .text(showingVis
                        ? "Show Table"
                        : "Show Visualisation")
                .command(() -> embeddedQueryPresenter.showTable(showingVis))
                .build();
    }

    private Item createEditQuery(final EmbeddedQueryPresenter embeddedQueryPresenter) {
        return new IconMenuItem.Builder()
                .priority(13)
                .icon(SvgImage.DOCUMENT_QUERY)
                .text("Edit Query")
                .command(embeddedQueryPresenter::editQuery)
                .build();
    }

    private Item createRunQuery(final EmbeddedQueryPresenter embeddedQueryPresenter) {
        return new IconMenuItem.Builder()
                .priority(14)
                .icon(SvgImage.PLAY)
                .iconColour(IconColour.GREEN)
                .text("Run Query")
                .command(embeddedQueryPresenter::runQuery)
                .build();
    }
}
