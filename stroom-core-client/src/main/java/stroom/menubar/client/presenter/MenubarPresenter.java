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

package stroom.menubar.client.presenter;

import com.google.gwt.core.client.Scheduler;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.event.dom.client.DomEvent;
import com.google.gwt.resources.client.ImageResource;
import com.google.gwt.user.client.Command;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenter;
import com.gwtplatform.mvp.client.annotations.ProxyCodeSplit;
import com.gwtplatform.mvp.client.annotations.ProxyEvent;
import com.gwtplatform.mvp.client.proxy.Proxy;
import com.gwtplatform.mvp.client.proxy.RevealContentEvent;
import stroom.core.client.MenuKeys;
import stroom.item.client.presenter.ListPresenter.ListView;
import stroom.main.client.presenter.MainPresenter;
import stroom.menubar.client.event.BeforeRevealMenubarEvent;
import stroom.menubar.client.view.MenubarItem;
import stroom.security.client.event.CurrentUserChangedEvent;
import stroom.security.client.event.CurrentUserChangedEvent.CurrentUserChangedHandler;
import stroom.task.client.TaskEndEvent;
import stroom.task.client.TaskStartEvent;
import stroom.widget.menu.client.presenter.HasChildren;
import stroom.widget.menu.client.presenter.Item;
import stroom.widget.menu.client.presenter.MenuItem;
import stroom.widget.menu.client.presenter.MenuItemPresenter;
import stroom.widget.menu.client.presenter.MenuItemPresenter.MenuItemView;
import stroom.widget.menu.client.presenter.MenuItems;
import stroom.widget.menu.client.presenter.MenuListPresenter;
import stroom.widget.popup.client.event.HidePopupEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupPosition;
import stroom.widget.popup.client.presenter.PopupUiHandlers;
import stroom.widget.popup.client.presenter.PopupView.PopupType;

import java.util.ArrayList;
import java.util.List;

public class MenubarPresenter extends MyPresenter<MenubarPresenter.MenubarView, MenubarPresenter.MenubarProxy>
        implements CurrentUserChangedHandler {
    private final Provider<MenuListPresenter> menuListPresenterProvider;
    private final Provider<MenubarItem> menubarItemProvider;
    private final MenuItems menuItems;
    private final List<Item> currentMenuItems = new ArrayList<>();
    private MenuListPresenter currentMenu;
    private String currentItem;

    @Inject
    public MenubarPresenter(final EventBus eventBus, final MenubarView display, final MenubarProxy proxy,
                            final Provider<MenuListPresenter> menuListPresenterProvider,
                            final Provider<MenubarItem> menubarItemProvider, final MenuItems menuItems) {
        super(eventBus, display, proxy);
        this.menuListPresenterProvider = menuListPresenterProvider;
        this.menubarItemProvider = menubarItemProvider;
        this.menuItems = menuItems;
    }

    private void addItem(final Item item) {
        if (item instanceof MenuItem) {
            final MenuItem menuItem = (MenuItem) item;

            // Create the presenter for the menu item.
            final MenuItemPresenter presenter = addItem(null, null, menuItem.getText(), null, true, null);
            registerHandler(presenter.getView().addClickHandler(event -> showPopup(menuItem, event)));
            registerHandler(presenter.getView().addMouseOverHandler(event -> {
                if (currentMenu != null) {
                    showPopup(menuItem, event);
                }
            }));
        }
    }

    protected MenuItemPresenter addItem(final ImageResource enabledImage, final ImageResource disabledImage,
                                        final String text, final String shortcut, final boolean enabled, final Command command) {
        final MenuItemView display = menubarItemProvider.get();
        final MenuItemPresenter presenter = new MenuItemPresenter(getEventBus(), display, enabledImage, disabledImage,
                text, shortcut, enabled);
        if (command != null) {
            presenter.addClickHandler(event -> {
                if ((event.getNativeButton() & NativeEvent.BUTTON_LEFT) != 0) {
                    execute(command);
                }
            });
        }
        getView().add(display);

        return presenter;
    }

    private void execute(final Command command) {
        TaskStartEvent.fire(MenubarPresenter.this);
        Scheduler.get().scheduleDeferred(() -> {
            try {
                command.execute();
            } finally {
                TaskEndEvent.fire(MenubarPresenter.this);
            }
        });
    }

    private void showPopup(final MenuItem menuItem, final DomEvent<?> event) {
        final int x = event.getRelativeElement().getAbsoluteLeft();
        final int y = event.getRelativeElement().getAbsoluteBottom();
        final Element element = event.getRelativeElement();
        showPopup(menuItem, x, y, element);
    }

    public void showPopup(final MenuItem menuItem, final int x, final int y, final Element autoHidePartner) {
        // Only change the popup if the item selected is changing and we have
        // some sub items.
        if (currentItem == null || !currentItem.equals(menuItem.getText())) {
            // We are changing the highlighted item so close the current popup
            // if it is open.
            if (currentMenu != null) {
                currentMenu.hide(true, true, false);
            }

            // Try and get some sub items.
            if (menuItem instanceof HasChildren) {
                final HasChildren hasChildren = (HasChildren) menuItem;

                hasChildren.getChildren().onSuccess(children -> {
                    if (children != null && children.size() > 0) {
                        final MenuListPresenter presenter = menuListPresenterProvider.get();
                        presenter.setData(children);

                        // Set the current presenter telling us that the
                        // popup is
                        // showing.
                        currentItem = menuItem.getText();
                        currentMenu = presenter;

                        final PopupUiHandlers popupUiHandlers = new PopupUiHandlers() {
                            @Override
                            public void onHideRequest(final boolean autoClose, final boolean ok) {
                                HidePopupEvent.fire(MenubarPresenter.this, presenter);
                            }

                            @Override
                            public void onHide(final boolean autoClose, final boolean ok) {
                                currentItem = null;
                                currentMenu = null;
                            }
                        };

                        // Add parent auto hide partners.
                        final List<Element> autoHidePartners = new ArrayList<>();
                        autoHidePartners.add(autoHidePartner);
                        presenter.setAutoHidePartners(autoHidePartners);

                        final Element[] partners = autoHidePartners.toArray(new Element[0]);
                        final PopupPosition popupPosition = new PopupPosition(x, y);
                        ShowPopupEvent.fire(MenubarPresenter.this, presenter, PopupType.POPUP, popupPosition,
                                popupUiHandlers, partners);
                    }
                });
            }
        }
    }

    @ProxyEvent
    @Override
    public void onCurrentUserChanged(final CurrentUserChangedEvent event) {
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

        // Show the menubar.
        forceReveal();
    }

    private void refresh() {
        // Clear the current view.
        getView().clear();
        // Sort the menu items by priority.
        currentMenuItems.sort(new MenuItems.ItemComparator());
        for (final Item item : currentMenuItems) {
            // Add the item to the view.
            addItem(item);
        }
    }

    @Override
    protected void revealInParent() {
        RevealContentEvent.fire(this, MainPresenter.MENUBAR, this);
    }

    public interface MenubarView extends ListView {
    }

    @ProxyCodeSplit
    public interface MenubarProxy extends Proxy<MenubarPresenter> {
    }
}
