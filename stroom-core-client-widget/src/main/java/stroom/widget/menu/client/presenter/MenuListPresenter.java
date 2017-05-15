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

package stroom.widget.menu.client.presenter;

import com.google.gwt.core.client.Scheduler;
import com.google.gwt.dom.client.Element;
import com.google.gwt.user.client.Command;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;
import stroom.task.client.TaskEndEvent;
import stroom.task.client.TaskStartEvent;
import stroom.widget.popup.client.event.HidePopupEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupPosition;
import stroom.widget.popup.client.presenter.PopupPosition.HorizontalLocation;
import stroom.widget.popup.client.presenter.PopupUiHandlers;
import stroom.widget.popup.client.presenter.PopupView.PopupType;

import java.util.ArrayList;
import java.util.List;

public class MenuListPresenter extends MenuPresenter {
    private MenuListPresenter currentMenu;
    private MenuItem currentItem;
    private MenuListPresenter parent;
    private final Provider<MenuListPresenter> menuListPresenterProvider;
    private List<Element> autoHidePartners;

    @Inject
    public MenuListPresenter(final EventBus eventBus, final Provider<MenuListPresenter> menuListPresenterProvider) {
        super(eventBus);
        this.menuListPresenterProvider = menuListPresenterProvider;
    }

    @Override
    protected void onClick(final MenuItem menuItem, final Element element) {
        CommandMenuItem commandMenuItem = null;
        if (menuItem instanceof CommandMenuItem) {
            commandMenuItem = (CommandMenuItem) menuItem;
        }
        if (commandMenuItem != null && commandMenuItem.getCommand() != null) {
            hide(false, true, true);
            execute(commandMenuItem.getCommand());
        } else {
            onMouseOver(menuItem, element);
        }
    }

    private void execute(final Command command) {
        TaskStartEvent.fire(MenuListPresenter.this);
        Scheduler.get().scheduleDeferred(() -> {
            try {
                command.execute();
            } finally {
                TaskEndEvent.fire(MenuListPresenter.this);
            }
        });
    }

    @Override
    protected void onMouseOver(final MenuItem menuItem, final Element element) {
        super.onMouseOver(menuItem, element);

        // Only change the popup if the item selected is changing and we have
        // some sub items.
        if (currentItem == null || !currentItem.equals(menuItem)) {
            removeHover(currentItem);

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
                        presenter.setParent(MenuListPresenter.this);
                        presenter.setHighlightItems(getHighlightItems());
                        presenter.setData(children);

                        // Set the current presenter telling us that the
                        // popup is showing.
                        currentItem = menuItem;
                        currentMenu = presenter;

                        final PopupUiHandlers popupUiHandlers = new PopupUiHandlers() {
                            @Override
                            public void onHideRequest(final boolean autoClose, final boolean ok) {
                                HidePopupEvent.fire(MenuListPresenter.this, presenter);
                            }

                            @Override
                            public void onHide(final boolean autoClose, final boolean ok) {
                                currentItem = null;
                                currentMenu = null;
                            }
                        };

                        final List<Element> autoHidePartners = new ArrayList<Element>();

                        // Add parent auto hide partners.
                        if (MenuListPresenter.this.autoHidePartners != null
                                && MenuListPresenter.this.autoHidePartners.size() > 0) {
                            autoHidePartners.addAll(MenuListPresenter.this.autoHidePartners);
                        }

                        // Add this as an auto hide partner
                        autoHidePartners.add(element);
                        presenter.setAutoHidePartners(autoHidePartners);

                        Element[] partners = new Element[autoHidePartners.size()];
                        partners = autoHidePartners.toArray(partners);

                        final PopupPosition popupPosition = new PopupPosition(element.getAbsoluteRight(),
                                element.getAbsoluteLeft(), element.getAbsoluteTop(), element.getAbsoluteTop(),
                                HorizontalLocation.RIGHT, null);
                        ShowPopupEvent.fire(MenuListPresenter.this, presenter, PopupType.POPUP, popupPosition,
                                popupUiHandlers, partners);
                    }
                });
            }
        }
    }

    @Override
    protected void onMouseOut(final MenuItem menuItem, final Element element) {
        if (menuItem instanceof HasChildren) {
            final HasChildren hasChildren = (HasChildren) menuItem;

            hasChildren.getChildren()
                    .onSuccess(children -> {
                        if (children == null || children.size() == 0) {
                            MenuListPresenter.super.onMouseOut(menuItem, element);
                        }
                    })
                    .onFailure(caught -> MenuListPresenter.super.onMouseOut(menuItem, element));
        } else {
            super.onMouseOut(menuItem, element);
        }
    }

    @Override
    public void hide(final boolean autoClose, final boolean ok, final boolean hideParent) {
        // First make sure all children are hidden.
        if (currentMenu != null) {
            currentMenu.hide(autoClose, ok, hideParent);
        }

        super.hide(autoClose, ok, hideParent);

        // Hide parent menus.
        if (hideParent && parent != null) {
            parent.hide(autoClose, ok, hideParent);
        }
    }

    public void setParent(final MenuListPresenter parent) {
        this.parent = parent;
    }

    public void setAutoHidePartners(final List<Element> autoHidePartners) {
        this.autoHidePartners = autoHidePartners;
    }

    public List<Element> getAutoHidePartners() {
        return autoHidePartners;
    }

    @Override
    public void setData(final List<Item> items) {
        removeAllHovers();
        super.setData(items);
    }
}
