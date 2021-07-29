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

import stroom.task.client.TaskEndEvent;
import stroom.task.client.TaskStartEvent;
import stroom.widget.menu.client.presenter.MenuPresenter.MenuView;
import stroom.widget.popup.client.event.HidePopupEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupPosition;
import stroom.widget.popup.client.presenter.PopupPosition.HorizontalLocation;
import stroom.widget.popup.client.presenter.PopupUiHandlers;
import stroom.widget.popup.client.presenter.PopupView.PopupType;

import com.google.gwt.core.client.Scheduler;
import com.google.gwt.dom.client.Element;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.HasUiHandlers;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

import java.util.List;

public class MenuPresenter
        extends MyPresenterWidget<MenuView>
        implements MenuUiHandlers {

    private final Provider<MenuPresenter> menuPresenterProvider;
    private MenuPresenter currentMenu;
    private MenuItem currentItem;
    private MenuPresenter parent;

    @Inject
    public MenuPresenter(final EventBus eventBus,
                         final MenuView view,
                         final Provider<MenuPresenter> menuPresenterProvider) {
        super(eventBus, view);
        this.menuPresenterProvider = menuPresenterProvider;
        view.setUiHandlers(this);
    }

    @Override
    public void showSubMenu(final MenuItem menuItem, final Element element) {
        // Only change the popup if the item selected is changing and we have
        // some sub items.
        if (currentItem == null || !currentItem.equals(menuItem)) {
            // We are changing the highlighted item so close the current popup
            // if it is open.
            hideChildren(false, false);

            if (menuItem instanceof HasChildren) {
                // Try and get some sub items.
                final HasChildren hasChildren = (HasChildren) menuItem;

                hasChildren.getChildren().onSuccess(children -> {
                    if (children != null && children.size() > 0) {
                        // We are changing the highlighted item so close the current popup
                        // if it is open.
                        hideChildren(false, false);

                        final MenuPresenter presenter = menuPresenterProvider.get();
                        presenter.setParent(MenuPresenter.this);
//                        presenter.setHighlightItems(getHighlightItems());
                        presenter.setData(children);

                        // Set the current presenter telling us that the
                        // popup is showing.
                        currentMenu = presenter;
                        currentItem = menuItem;

                        final PopupUiHandlers popupUiHandlers = new PopupUiHandlers() {
                            @Override
                            public void onHideRequest(final boolean autoClose, final boolean ok) {
                                presenter.hideChildren(autoClose, ok);
                                presenter.hideSelf(autoClose, ok);
                                currentMenu = null;
                                currentItem = null;
                            }
                        };

                        final PopupPosition popupPosition = new PopupPosition(
                                element.getAbsoluteRight(),
                                element.getAbsoluteLeft(),
                                element.getAbsoluteTop(),
                                element.getAbsoluteTop(),
                                HorizontalLocation.RIGHT,
                                null);

                        ShowPopupEvent.fire(
                                MenuPresenter.this,
                                presenter,
                                PopupType.POPUP,
                                popupPosition,
                                popupUiHandlers,
                                element);
                    }
                });
            }
        }
    }

    @Override
    public void focusSubMenu() {
        if (currentMenu != null) {
            currentMenu.selectFirstItem(true);
        }
    }

    public void selectFirstItem(final boolean stealFocus) {
        getView().selectFirstItem(stealFocus);
    }

    @Override
    public void focusParent() {
        if (parent != null) {
            hideChildren(false, false);
            parent.getView().focus();
        }
    }

    @Override
    public void execute(final MenuItem menuItem) {
        if (menuItem != null && menuItem.getCommand() != null) {
            TaskStartEvent.fire(MenuPresenter.this);
            Scheduler.get().scheduleDeferred(() -> {
                try {
                    hideAll(false, false);
                    menuItem.getCommand().execute();
                } finally {
                    TaskEndEvent.fire(MenuPresenter.this);
                }
            });
        }
    }

    private void hideSelf(final boolean autoClose, final boolean ok) {
        HidePopupEvent.fire(this, this, autoClose, ok);
    }

    private void hideChildren(final boolean autoClose, final boolean ok) {
        // First make sure all children are hidden.
        if (currentMenu != null) {
            currentMenu.hideChildren(autoClose, ok);
            currentMenu.hideSelf(autoClose, ok);
            currentMenu = null;
            currentItem = null;
        }
    }

    private void hideParent(final boolean autoClose, final boolean ok) {
        // First make sure all children are hidden.
        if (parent != null) {
            parent.hideSelf(autoClose, ok);
            parent.hideParent(autoClose, ok);
        }
    }

    @Override
    public void escape() {
        hideAll(true, false);
    }

    public void hideAll(final boolean autoClose, final boolean ok) {
        hideChildren(autoClose, ok);
        hideSelf(autoClose, ok);
        hideParent(autoClose, ok);
    }

    public void setParent(final MenuPresenter parent) {
        this.parent = parent;
    }

    public void setData(final List<Item> items) {
        getView().setData(items);
    }

    public interface MenuView extends View, HasUiHandlers<MenuUiHandlers> {

        void setData(List<Item> items);

        void selectFirstItem(boolean stealFocus);

        void focus();
    }
}
