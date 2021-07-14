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

package stroom.explorer.client.presenter;

import stroom.activity.client.ActivityChangedEvent;
import stroom.activity.client.CurrentActivity;
import stroom.activity.shared.Activity.ActivityDetails;
import stroom.activity.shared.Activity.Prop;
import stroom.core.client.MenuKeys;
import stroom.dispatch.client.RestFactory;
import stroom.explorer.client.event.ExplorerTreeDeleteEvent;
import stroom.explorer.client.event.ExplorerTreeSelectEvent;
import stroom.explorer.client.event.HighlightExplorerNodeEvent;
import stroom.explorer.client.event.RefreshExplorerTreeEvent;
import stroom.explorer.client.event.ShowNewMenuEvent;
import stroom.explorer.client.presenter.NavigationPresenter.NavigationProxy;
import stroom.explorer.client.presenter.NavigationPresenter.NavigationView;
import stroom.main.client.presenter.MainPresenter;
import stroom.menubar.client.event.BeforeRevealMenubarEvent;
import stroom.security.client.api.event.CurrentUserChangedEvent;
import stroom.security.client.api.event.CurrentUserChangedEvent.CurrentUserChangedHandler;
import stroom.security.shared.DocumentPermissionNames;
import stroom.svg.client.Preset;
import stroom.ui.config.client.UiConfigCache;
import stroom.ui.config.shared.ActivityConfig;
import stroom.widget.button.client.SvgButton;
import stroom.widget.menu.client.presenter.HasChildren;
import stroom.widget.menu.client.presenter.IconMenuItem;
import stroom.widget.menu.client.presenter.Item;
import stroom.widget.menu.client.presenter.Menu;
import stroom.widget.menu.client.presenter.MenuItem;
import stroom.widget.menu.client.presenter.MenuItems;
import stroom.widget.menu.client.presenter.MenuPresenter;
import stroom.widget.popup.client.event.HidePopupEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupPosition;
import stroom.widget.popup.client.presenter.PopupUiHandlers;
import stroom.widget.popup.client.presenter.PopupView.PopupType;
import stroom.widget.tab.client.event.MaximiseEvent;

import com.google.gwt.core.client.Scheduler;
import com.google.gwt.dom.client.Element;
import com.google.gwt.event.dom.client.MouseDownEvent;
import com.google.gwt.user.client.Command;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.HasUiHandlers;
import com.gwtplatform.mvp.client.MyPresenter;
import com.gwtplatform.mvp.client.View;
import com.gwtplatform.mvp.client.annotations.ProxyCodeSplit;
import com.gwtplatform.mvp.client.annotations.ProxyEvent;
import com.gwtplatform.mvp.client.proxy.Proxy;
import com.gwtplatform.mvp.client.proxy.RevealContentEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class NavigationPresenter
        extends
        MyPresenter<NavigationView, NavigationProxy>
        implements
        NavigationUiHandlers,
        RefreshExplorerTreeEvent.Handler,
        HighlightExplorerNodeEvent.Handler,
        CurrentUserChangedHandler {

    private final DocumentTypeCache documentTypeCache;
    private final TypeFilterPresenter typeFilterPresenter;
    private final CurrentActivity currentActivity;
    private final ExplorerTree explorerTree;
    private final Menu menu;
    private final SimplePanel activityOuter = new SimplePanel();
    private final Button activityButton = new Button();

    private final MenuItems menuItems;
    private final List<Item> currentMenuItems = new ArrayList<>();

    private final Map<Item, List<Item>> allItems = new HashMap<>();

    private SvgButton add;
    private SvgButton delete;
    private SvgButton filter;


//    private LayerContainerImpl explorer;

//    @Inject
//    public NavigationPresenter(final EventBus eventBus,
//                               final NavigationView view,
//                               final NavigationProxy proxy,
//                               final MenuItems menuItems,
//                               final Provider<MenuListPresenter> menuListPresenterProvider) {
//        super(eventBus, view, proxy);
//        this.menuItems = menuItems;
//        this.menuListPresenterProvider = menuListPresenterProvider;
//    }


    @Inject
    public NavigationPresenter(final EventBus eventBus,
                               final NavigationView view,
                               final NavigationProxy proxy,
                               final MenuItems menuItems,
                               final RestFactory restFactory,
                               final DocumentTypeCache documentTypeCache,
                               final TypeFilterPresenter typeFilterPresenter,
                               final CurrentActivity currentActivity,
                               final UiConfigCache uiConfigCache,
                               final Menu menu) {
        super(eventBus, view, proxy);
        this.menuItems = menuItems;
        this.documentTypeCache = documentTypeCache;
        this.typeFilterPresenter = typeFilterPresenter;
        this.currentActivity = currentActivity;
        this.menu = menu;

        view.setUiHandlers(this);

        explorerTree = new ExplorerTree(restFactory, true);

        // Add views.
        uiConfigCache.get().onSuccess(uiConfig -> {
            final ActivityConfig activityConfig = uiConfig.getActivity();
            if (activityConfig.isEnabled()) {

                updateActivitySummary();
                activityButton.setStyleName("activityButton");
                activityOuter.setStyleName("activityOuter");
                activityOuter.setWidget(activityButton);

                getView().setActivityWidget(activityOuter);

//                final SimplePanel activityOuter = new SimplePanel();
//                activityOuter.setStyleName("dock-min activityOuter");
//                activityOuter.setWidget(activityButton);


//                final SimplePanel treeContainer = new SimplePanel();
//                treeContainer.setStyleName("dock-max stroom-content");
//                treeContainer.setWidget(explorerTree);
//
//                final FlowPanel explorerWrapper = new FlowPanel();
//                explorerWrapper.setStyleName("dock-container-vertical explorerWrapper");
//                explorerWrapper.add(treeContainer);
//                explorerWrapper.add(activityOuter);
//
//                view.setCellTree(explorerWrapper);


            } else {
//                view.setCellTree(explorerTree);
            }
        });
    }

    @Override
    protected void onBind() {
        super.onBind();

        // Register for refresh events.
        registerHandler(getEventBus().addHandler(RefreshExplorerTreeEvent.getType(), this));

        // Register for changes to the current activity.
        registerHandler(getEventBus().addHandler(ActivityChangedEvent.getType(), event -> updateActivitySummary()));

        // Register for highlight events.
        registerHandler(getEventBus().addHandler(HighlightExplorerNodeEvent.getType(), this));

        registerHandler(typeFilterPresenter.addDataSelectionHandler(event -> explorerTree.setIncludedTypeSet(
                typeFilterPresenter.getIncludedTypes())));

        // Fire events from the explorer tree globally.
        registerHandler(explorerTree.getSelectionModel().addSelectionHandler(event -> {
            getEventBus().fireEvent(new ExplorerTreeSelectEvent(
                    explorerTree.getSelectionModel(),
                    event.getSelectionType()));
            final boolean enabled = explorerTree.getSelectionModel().getSelectedItems().size() > 0;
            add.setEnabled(enabled);
            delete.setEnabled(enabled);
        }));
        registerHandler(explorerTree.addContextMenuHandler(event -> getEventBus().fireEvent(event)));

        registerHandler(activityButton.addClickHandler(event -> currentActivity.showActivityChooser()));
    }

    private void updateActivitySummary() {
        currentActivity.getActivity(activity -> {
            final StringBuilder sb = new StringBuilder("<h2>Current Activity</h2>");

            if (activity != null) {
                final ActivityDetails activityDetails = activity.getDetails();
                for (final Prop prop : activityDetails.getProperties()) {
                    if (prop.isShowInSelection()) {
                        sb.append("<b>");
                        sb.append(prop.getName());
                        sb.append(": </b>");
                        sb.append(prop.getValue());
                        sb.append("</br>");
                    }
                }
            } else {
                sb.append("<b>");
                sb.append("none");
            }

            activityButton.setHTML(sb.toString());
        });
    }

    public void newItem(final Element element) {
        final int x = element.getAbsoluteLeft() - 1;
        final int y = element.getAbsoluteTop() + element.getOffsetHeight() + 1;

        ShowNewMenuEvent.fire(this, element, x, y);
    }

    public void deleteItem() {
        if (explorerTree.getSelectionModel().getSelectedItems().size() > 0) {
            ExplorerTreeDeleteEvent.fire(this);
        }
    }

    @Override
    public void changeQuickFilter(final String name) {
        explorerTree.changeNameFilter(name);
    }

    @Override
    public void maximise() {
        MaximiseEvent.fire(this, null);
    }

    @Override
    public void showMenu(final Element target) {
        showMenuItems(
                currentMenuItems,
                target.getAbsoluteLeft(),
                target.getAbsoluteBottom() + 10,
                target);
    }

    public void showMenuItems(final List<Item> children,
                              final int x,
                              final int y,
                              final Element autoHidePartner) {
        if (menu.isShowing()) {
            menu.hide();
        } else if (children != null && children.size() > 0) {
            menu.show(children, x, y, autoHidePartner::focus, autoHidePartner);
        }
    }

    public void showTypeFilter(final MouseDownEvent event) {
        final Element target = event.getNativeEvent().getEventTarget().cast();

        final PopupPosition popupPosition = new PopupPosition(target.getAbsoluteLeft() - 1,
                target.getAbsoluteTop() + target.getClientHeight() + 2);
        ShowPopupEvent.fire(
                this,
                typeFilterPresenter,
                PopupType.POPUP,
                popupPosition,
                null,
                target);
    }

    @ProxyEvent
    @Override
    public void onCurrentUserChanged(final CurrentUserChangedEvent event) {
        documentTypeCache.clear();
        // Set the data for the type filter.
        documentTypeCache.fetch(typeFilterPresenter::setDocumentTypes);

        explorerTree.getTreeModel().reset();
        explorerTree.getTreeModel().setRequiredPermissions(DocumentPermissionNames.READ);
        explorerTree.getTreeModel().setIncludedTypeSet(null);


        // Clear the current menus.
        menuItems.clear();

        // Tell all plugins to add new menu items.
        BeforeRevealMenubarEvent.fire(this, menuItems);

        // Remove all current menu items.
        currentMenuItems.clear();

        // Get items from the providers.
        final List<Item> items = menuItems.getMenuItems(MenuKeys.MAIN_MENU);
        if (items != null) {
            for (final Item item : items) {
                if (item instanceof HasChildren) {
                    final HasChildren hasChildren = (HasChildren) item;
                    hasChildren.getChildren().onSuccess(children -> {
                        if (children != null && children.size() > 0) {
                            currentMenuItems.add(item);
                            refresh();
                        }
                    });
                } else {
                    currentMenuItems.add(item);
                    refresh();
                }
            }
        }


        // Show the tree.
        forceReveal();
    }
//
//    @Override
//    protected void revealInParent() {
//        explorerTree.getTreeModel().refresh();
//        OpenExplorerTabEvent.fire(this, this, this);
//    }

    @Override
    public void onHighlight(final HighlightExplorerNodeEvent event) {
        explorerTree.setSelectedItem(event.getExplorerNode());
        explorerTree.getTreeModel().setEnsureVisible(event.getExplorerNode());
        explorerTree.getTreeModel().refresh();
    }

    @Override
    public void onRefresh(final RefreshExplorerTreeEvent event) {
        explorerTree.getTreeModel().refresh();
    }

    public void refresh() {
        explorerTree.getTreeModel().refresh();
        // Sort the menu items by priority.
        currentMenuItems.sort(new MenuItems.ItemComparator());
        for (final Item item : currentMenuItems) {
            // Add the item to the view.
            if (item instanceof HasChildren) {
                final HasChildren hasChildren = (HasChildren) item;
                hasChildren.getChildren().onSuccess(children -> {
                    if (children != null && children.size() > 0) {
                        allItems.put(item, children);
                        refreshAll();
                    }
                });
            }
        }
    }


    @Override
    protected void revealInParent() {
        explorerTree.getTreeModel().refresh();
        refreshAll();
        RevealContentEvent.fire(this, MainPresenter.EXPLORER, this);
    }

//    @ProxyEvent
//    @Override
//    public void onOpen(final OpenExplorerTabEvent event) {
//        // Make sure this tab pane is revealed before we try and reveal child
//        // tabs.
//        revealInParent();
//
//        explorer = new LayerContainerImpl();
//        explorer.show(event.getLayer());
//
//        refreshAll();
//
////        getView().add(explorer, "Explorer", 20);
//
////        final TabData tabData = event.getTabData();
////        if (tabData != null && openItems.contains(tabData)) {
////            selectTab(tabData);
////        } else {
////            openItems.add(tabData);
////            add(tabData, event.getLayer());
////        }
//    }
//
//    @ProxyEvent
//    @Override
//    public void onClose(final CloseExplorerTabEvent event) {
//        final TabData tabData = event.getTabData();
//
////        // Remove from sets.
////        openItems.remove(tabData);
////
////        // Remove from display.
////        remove(tabData);
//    }
//
//    @ProxyEvent
//    @Override
//    public void onCurrentUserChanged(final CurrentUserChangedEvent event) {
//        // Clear the current menus.
//        menuItems.clear();
//
//        // Tell all plugins to add new menu items.
//        BeforeRevealMenubarEvent.fire(this, menuItems);
//
//        // Remove all current menu items.
//        currentMenuItems.clear();
//
//        // Get items from the providers.
//        final List<Item> items = menuItems.getMenuItems(MenuKeys.MAIN_MENU);
//        if (items != null) {
//            for (final Item item : items) {
//                if (item instanceof HasChildren) {
//                    final HasChildren hasChildren = (HasChildren) item;
//                    hasChildren.getChildren().onSuccess(children -> {
//                        if (children != null && children.size() > 0) {
//                            currentMenuItems.add(item);
//                            refresh();
//                        }
//                    });
//                } else {
//                    currentMenuItems.add(item);
//                    refresh();
//                }
//            }
//        }
////
////        // Show the menubar.
////        forceReveal();
//    }


    private void refreshAll() {
        final FlowPanel flowPanel = new FlowPanel();

        if (explorerTree != null) {
            explorerTree.asWidget().getElement().getChild(0).getChild(0);

            flowPanel.add(new NavigationPanel("Explorer", explorerTree, createButtons(), true));
        }

        allItems.keySet().stream().sorted(new MenuItems.ItemComparator()).forEach(item -> {
            boolean include = true;
            if (item instanceof MenuItem && ((MenuItem) item).getText().equals("Item")) {
                include = false;
            }

            if (include) {
                FlowPanel fp = new FlowPanel();
                fp.setStyleName("navigation-list");

                final List<Item> children = allItems.get(item);
                for (final Item child : children) {
                    if (child instanceof IconMenuItem) {
                        final IconMenuItem iconMenuItem = (IconMenuItem) child;
                        FlowPanel fpItem = new FlowPanel();
                        fpItem.setStyleName("navigation-item");
                        fpItem.add(iconMenuItem.getEnabledIcon().asWidget());
                        fpItem.add(new Label(iconMenuItem.getText()));
                        fp.add(fpItem);

                        final Command command = iconMenuItem.getCommand();
                        if (command != null) {
                            fpItem.addDomHandler(e -> command.execute(), MouseDownEvent.getType());
                        }
                    }
                }

                if (item instanceof MenuItem) {
                    flowPanel.add(new NavigationPanel(((MenuItem) item).getText(), fp, null, false));
                }
            }
        });

        getView().setNavigationWidget(flowPanel);
    }

    private FlowPanel createButtons() {
        add = SvgButton.create(new Preset("navigation-header-button navigation-header-button-add",
                "New",
                false));
        delete = SvgButton.create(new Preset("navigation-header-button navigation-header-button-delete",
                "Delete",
                false));
        filter = SvgButton.create(new Preset("navigation-header-button navigation-header-button-filter",
                "Filter",
                true));

        add.addMouseDownHandler(e -> newItem(add.getElement()));
        delete.addMouseDownHandler(e -> deleteItem());
        filter.addMouseDownHandler(this::showTypeFilter);

        final FlowPanel buttons = new FlowPanel();
        buttons.setStyleName("navigation-header-buttons");
        buttons.add(add);
        buttons.add(delete);
        buttons.add(filter);

        return buttons;
    }

    @ProxyCodeSplit
    public interface NavigationProxy extends Proxy<NavigationPresenter> {

    }

    public interface NavigationView extends View, HasUiHandlers<NavigationUiHandlers> {

        void setNavigationWidget(Widget widget);

        void setActivityWidget(Widget widget);
    }
}
