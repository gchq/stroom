/*
 * Copyright 2020 Crown Copyright
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

import stroom.alert.client.event.AlertEvent;
import stroom.dashboard.client.flexlayout.FlexLayout;
import stroom.dashboard.client.flexlayout.MutableTabConfig;
import stroom.dashboard.client.flexlayout.MutableTabLayoutConfig;
import stroom.dashboard.client.flexlayout.TabLayout;
import stroom.dashboard.client.main.Components;
import stroom.dashboard.client.main.DashboardPane;
import stroom.dashboard.shared.ComponentConfig;
import stroom.dashboard.shared.EmbeddedQueryComponentSettings;
import stroom.svg.client.IconColour;
import stroom.svg.shared.SvgImage;
import stroom.widget.component.client.Controller;
import stroom.widget.menu.client.presenter.HideMenuEvent;
import stroom.widget.menu.client.presenter.IconMenuItem;
import stroom.widget.menu.client.presenter.IconParentMenuItem;
import stroom.widget.menu.client.presenter.Item;
import stroom.widget.menu.client.presenter.ShowMenuEvent;
import stroom.widget.popup.client.presenter.PopupPosition;
import stroom.widget.popup.client.presenter.PopupPosition.PopupLocation;
import stroom.widget.util.client.Rect;

import com.google.gwt.dom.client.Element;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/// The menu of a dashboard's tab: rename it, show its settings, hide, show, duplicate or remove it
/// or its pane, maximise it or restore the layout, and an embedded query's own actions. It has no
/// widget of its own: the menu is the app's, and the rename dialog is its own until it is hidden.
public class TabMenuController extends Controller {

    private final Provider<RenameTabController> renameTabControllerProvider;
    private Components components;
    private TabActions dashboard;

    private FlexLayout flexLayout;
    private TabLayout tabLayout;
    private MutableTabConfig currentTabConfig;

    /// Made by GIN, with what it needs.
    ///
    /// @param eventBus                    The application's event bus.
    /// @param renameTabControllerProvider Makes the dialog that renames a tab.
    @Inject
    public TabMenuController(final EventBus eventBus,
                             final Provider<RenameTabController> renameTabControllerProvider) {
        super(eventBus);
        this.renameTabControllerProvider = renameTabControllerProvider;
    }

    /// @param components The dashboard's panes, one per tab.
    /// @param dashboard  What the menu's actions do to the dashboard.
    public void setDashboard(final Components components, final TabActions dashboard) {
        this.components = components;
        this.dashboard = dashboard;
    }

    /// Shows a tab's menu below an element, or hides it if it is already showing.
    ///
    /// @param target     The element, e.g. the tab or its pane's Tab Actions button.
    /// @param flexLayout The layout the tab is in.
    /// @param tabLayout  The pane the tab is in.
    /// @param tabConfig  The tab.
    public void showMenu(final Element target,
                         final FlexLayout flexLayout,
                         final TabLayout tabLayout,
                         final MutableTabConfig tabConfig) {
        this.flexLayout = flexLayout;
        this.tabLayout = tabLayout;

        final DashboardPane component = components.get(tabConfig.getId());
        if (component != null) {
            if (Objects.equals(currentTabConfig, tabConfig)) {
                HideMenuEvent.builder().fire(this);

//            } else if (!dashboard.isDesignMode()) {
//                showSettings(tabConfig);

            } else {
//                if (currentTabConfig != null) {
//                    HideMenuEvent.builder().fire(this);
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
                        .name("Actions for tab '" + component.getComponentConfig().getName() + "'")
                        .items(menuItems)
                        .opener(target)
                        .popupPosition(popupPosition)
                        .onHide(e -> currentTabConfig = null)
                        .fire(this);
//                    }
//                }.schedule(0);
            }
        }
    }


    /// Asks for a tab's new name.
    ///
    /// @param componentConfig    The tab's pane.
    /// @param nameChangeConsumer Given the new name.
    public void showRename(final ComponentConfig componentConfig,
                           final Consumer<String> nameChangeConsumer) {
        final TabLayout layout = tabLayout;
        renameTabControllerProvider.get().rename(this, componentConfig.getName(), name -> {
            nameChangeConsumer.accept(name);
            layout.refresh();
            dashboard.onChange();
        });
    }

    /// Shows a tab's pane's settings.
    ///
    /// @param tabConfig The tab.
    public void showSettings(final MutableTabConfig tabConfig) {
        final DashboardPane component = components.get(tabConfig.getId());
        if (component != null) {
            component.showSettings();
        }
    }

    /// Duplicates a tab to another dashboard.
    ///
    /// @param tabLayoutConfig The tab's pane.
    /// @param tabConfig       The tab.
    public void duplicateTabTo(final MutableTabLayoutConfig tabLayoutConfig, final MutableTabConfig tabConfig) {
        dashboard.duplicateTabTo(tabLayoutConfig, tabConfig);
    }

    private void duplicateTab(final MutableTabLayoutConfig tabLayoutConfig, final MutableTabConfig tabConfig) {
        dashboard.duplicateTab(tabLayoutConfig, tabConfig);
    }

    private void duplicateTabPanel(final MutableTabLayoutConfig tabLayoutConfig) {
        dashboard.duplicateTabPanel(tabLayoutConfig);
    }

    private void showTab(final MutableTabConfig tabConfig) {
        tabConfig.setVisible(true);
        flexLayout.clear();
        flexLayout.refresh();
        dashboard.onChange();
    }

    private void maximiseTab(final MutableTabConfig tabConfig) {
        dashboard.maximiseTabs(tabConfig);
    }

    private void restoreTabs() {
        dashboard.restoreTabs();
    }

    private void hideTab(final MutableTabLayoutConfig tabLayoutConfig, final MutableTabConfig tabConfig) {
        if (tabLayoutConfig.getVisibleTabCount() <= 1) {
            AlertEvent.fireError(this, "You cannot remove or hide all tabs", null);
        } else {
            tabConfig.setVisible(false);
            flexLayout.clear();
            flexLayout.refresh();
            dashboard.onChange();
        }
    }

    private void removeTab(final MutableTabLayoutConfig tabLayoutConfig, final MutableTabConfig tab) {
        dashboard.removeTab(tabLayoutConfig, tab);
    }

    private void removeTabPanel(final MutableTabLayoutConfig tabLayoutConfig) {
        dashboard.removeTabPanel(tabLayoutConfig);
    }

    private List<Item> updateMenuItems(final MutableTabLayoutConfig tabLayoutConfig,
                                       final MutableTabConfig tabConfig,
                                       final DashboardPane component) {
        final ComponentConfig componentConfig = component.getComponentConfig();
        final Consumer<String> nameChangeConsumer = component::setComponentName;

        final List<Item> menuItems = new ArrayList<>();

        // Create rename menu.
        menuItems.add(createRenameMenu(componentConfig, nameChangeConsumer));

        // Create settings menu.
        menuItems.add(createSettingsMenu(tabConfig));

        if (!dashboard.isMaximised()) {
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

        if (component instanceof final EmbeddedQueryController embeddedQuery) {
            final boolean showingVis = embeddedQuery.isShowingVis();
            final boolean canShowVis = embeddedQuery.canShowVis();
            if (showingVis || canShowVis) {
                menuItems.add(createShowTable(embeddedQuery, showingVis));
            }

            final EmbeddedQueryComponentSettings embeddedQueryComponentSettings =
                    (EmbeddedQueryComponentSettings) embeddedQuery.getSettings();
            if (embeddedQueryComponentSettings.getQueryRef() != null || !embeddedQueryComponentSettings.reference()) {
                menuItems.add(createEditQuery(embeddedQuery));
                menuItems.add(createRunQuery(embeddedQuery));
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

    private Item createSettingsMenu(final MutableTabConfig tabConfig) {
        return new IconMenuItem.Builder()
                .priority(1)
                .icon(SvgImage.SETTINGS)
                .text("Settings")
                .command(() -> showSettings(tabConfig))
                .build();
    }

    private Item createHideMenu(final MutableTabLayoutConfig tabLayoutConfig, final MutableTabConfig tabConfig) {
        return new IconMenuItem.Builder()
                .priority(6)
                .icon(SvgImage.HIDE)
                .text("Hide Tab")
                .command(() -> hideTab(tabLayoutConfig, tabConfig))
                .build();
    }

    private Item createShowMenu(final MutableTabLayoutConfig tabLayoutConfig) {
        final List<Item> menuItems = new ArrayList<>();

        int i = 0;
        for (final MutableTabConfig tc : tabLayoutConfig.getTabs()) {
            if (!tc.isVisible()) {
                final DashboardPane component = components.get(tc.getId());
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
                .text("Show Hidden Tab")
                .children(menuItems)
                .build();
    }

    private Item createDuplicateMenu(final MutableTabLayoutConfig tabLayoutConfig, final MutableTabConfig tabConfig) {
        return new IconMenuItem.Builder()
                .priority(8)
                .icon(SvgImage.COPY)
                .text("Duplicate")
                .command(() -> duplicateTab(tabLayoutConfig, tabConfig))
                .build();
    }

    private Item createDuplicateToMenu(final MutableTabLayoutConfig tabLayoutConfig, final MutableTabConfig tabConfig) {
        return new IconMenuItem.Builder()
                .priority(8)
                .icon(SvgImage.COPY)
                .text("Duplicate to Another Dashboard...")
                .command(() -> duplicateTabTo(tabLayoutConfig, tabConfig))
                .build();
    }

    private Item createDuplicateTabPanelMenu(final MutableTabLayoutConfig tabLayoutConfig) {
        return new IconMenuItem.Builder()
                .priority(9)
                .icon(SvgImage.COPY)
                .text("Duplicate All Tabs in Pane")
                .command(() -> duplicateTabPanel(tabLayoutConfig))
                .build();
    }

    private Item createRemoveMenu(final MutableTabLayoutConfig tabLayoutConfig, final MutableTabConfig tabConfig) {
        return new IconMenuItem.Builder()
                .priority(10)
                .icon(SvgImage.DELETE)
                .text("Remove")
                .command(() -> removeTab(tabLayoutConfig, tabConfig))
                .build();
    }

    private Item createRemoveTabPanel(final MutableTabLayoutConfig tabLayoutConfig) {
        return new IconMenuItem.Builder()
                .priority(11)
                .icon(SvgImage.DELETE)
                .text("Remove All Tabs in Pane")
                .command(() -> removeTabPanel(tabLayoutConfig))
                .build();
    }

    private Item createMaximiseMenu(final MutableTabConfig tabConfig) {
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
                .text("Restore Pane Layout")
                .command(this::restoreTabs)
                .build();
    }

    private Item createShowTable(final EmbeddedQueryController embeddedQuery,
                                 final boolean showingVis) {
        return new IconMenuItem.Builder()
                .priority(12)
                .icon(showingVis
                        ? SvgImage.TABLE
                        : SvgImage.DOCUMENT_VISUALISATION)
                .text(showingVis
                        ? "Show Table"
                        : "Show Visualisation")
                .command(() -> embeddedQuery.showTable(showingVis))
                .build();
    }

    private Item createEditQuery(final EmbeddedQueryController embeddedQuery) {
        return new IconMenuItem.Builder()
                .priority(13)
                .icon(SvgImage.DOCUMENT_QUERY)
                .text("Edit Query")
                .command(embeddedQuery::editQuery)
                .build();
    }

    private Item createRunQuery(final EmbeddedQueryController embeddedQuery) {
        return new IconMenuItem.Builder()
                .priority(14)
                .icon(SvgImage.PLAY)
                .iconColour(IconColour.GREEN)
                .text("Run Query")
                .command(embeddedQuery::runQuery)
                .build();
    }


    // --------------------------------------------------------------------------------


    /// What a tab's menu does to its dashboard.
    public interface TabActions {

        /// Tells the dashboard it has changed.
        void onChange();

        /// @return Whether a tab is maximised.
        boolean isMaximised();

        /// Duplicates a tab to another dashboard.
        ///
        /// @param tabLayoutConfig The tab's pane.
        /// @param tabConfig       The tab.
        void duplicateTabTo(MutableTabLayoutConfig tabLayoutConfig, MutableTabConfig tabConfig);

        /// Duplicates a tab in its pane.
        ///
        /// @param tabLayoutConfig The tab's pane.
        /// @param tabConfig       The tab.
        void duplicateTab(MutableTabLayoutConfig tabLayoutConfig, MutableTabConfig tabConfig);

        /// Duplicates every tab in a pane.
        ///
        /// @param tabLayoutConfig The pane.
        void duplicateTabPanel(MutableTabLayoutConfig tabLayoutConfig);

        /// Maximises a tab.
        ///
        /// @param tabConfig The tab.
        void maximiseTabs(MutableTabConfig tabConfig);

        /// Restores the layout a maximised tab hid.
        void restoreTabs();

        /// Removes a tab.
        ///
        /// @param tabLayoutConfig The tab's pane.
        /// @param tabConfig       The tab.
        void removeTab(MutableTabLayoutConfig tabLayoutConfig, MutableTabConfig tabConfig);

        /// Removes every tab in a pane.
        ///
        /// @param tabLayoutConfig The pane.
        void removeTabPanel(MutableTabLayoutConfig tabLayoutConfig);
    }
}
