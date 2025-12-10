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

package stroom.widget.menu.client.presenter;

import stroom.task.client.SimpleTask;
import stroom.task.client.Task;
import stroom.task.client.TaskMonitor;
import stroom.widget.menu.client.presenter.MenuPresenter.MenuView;
import stroom.widget.popup.client.event.HidePopupEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupPosition;
import stroom.widget.popup.client.presenter.PopupPosition.PopupLocation;
import stroom.widget.popup.client.presenter.PopupType;
import stroom.widget.util.client.Rect;

import com.google.gwt.core.client.Scheduler;
import com.google.gwt.dom.client.Element;
import com.google.gwt.user.client.ui.Focus;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.HasUiHandlers;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

import java.util.List;
import java.util.Objects;

public class MenuPresenter
        extends MyPresenterWidget<MenuView>
        implements MenuUiHandlers {

    private static final int HORIZONTAL_PADDING = 2;
    private static final int VERTICAL_PADDING = 4;

    private final Provider<MenuPresenter> menuPresenterProvider;
    private MenuPresenter currentMenu;
    private MenuItem currentItem;

    private MenuPresenter parent;
    private MenuItem parentItem;

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
        if (!Objects.equals(currentItem, menuItem)) {
            if (currentItem != null) {
                // We are changing the highlighted item so close the current popup
                // if it is open.
                hideChildren(false, false);
            }

            if (menuItem instanceof HasChildren) {
                // Try and get some sub items.
                //noinspection PatternVariableCanBeUsed cos GWT
                final HasChildren hasChildren = (HasChildren) menuItem;

                hasChildren.getChildren().onSuccess(children -> {
                    if (children != null && children.size() > 0) {
                        // We are changing the highlighted item so close the current popup
                        // if it is open.
                        hideChildren(false, false);

                        final MenuPresenter presenter = menuPresenterProvider.get();
                        presenter.setParent(MenuPresenter.this, menuItem);
//                        presenter.setHighlightItems(getHighlightItems());
                        presenter.setData(children);

                        // Set the current presenter telling us that the
                        // popup is showing.
                        currentMenu = presenter;
                        currentItem = menuItem;

                        final Rect dialog = new Rect(getWidget().getElement());
                        final Rect selection = new Rect(element);
                        Rect relativeRect = Rect.min(dialog, selection);
                        relativeRect = relativeRect.growX(HORIZONTAL_PADDING);
                        relativeRect = relativeRect.growY(VERTICAL_PADDING);

                        final PopupPosition popupPosition = new PopupPosition(relativeRect, PopupLocation.RIGHT);

                        ShowPopupEvent.builder(presenter)
                                .popupType(PopupType.POPUP)
                                .popupPosition(popupPosition)
                                .addAutoHidePartner(element)
                                .onHideRequest(e -> {
                                    presenter.hideChildren(e.isAutoClose(), e.isOk());
                                    presenter.hideSelf(e.isAutoClose(), e.isOk());
                                    currentMenu = null;
                                    currentItem = null;
                                })
                                .fire();
                    }
                });
            }
        }
    }

    @Override
    public void hideExistingSubMenu(final MenuItem newMenuItem) {
        if (currentItem != null && !Objects.equals(currentItem, newMenuItem)) {
            hideChildren(false, false);
        }
    }

    @Override
    public void ensureParentItemSelected() {
        if (parent != null && parentItem != null) {
            parent.getView().ensureItemSelected(parentItem);
        }
    }

    public void focus() {
        getView().focus();
    }

    public void focus(final boolean hideChildren) {
        hideChildren(hideChildren, false);
        getView().focus();
    }

    @Override
    public void focusSubMenu() {
        if (currentMenu != null) {
            currentMenu.selectFirstItem();
        }
    }

    public void selectFirstItem() {
        getView().selectFirstItem();
    }

    @Override
    public void focusParent(final boolean hideChildren) {
        if (parent != null) {
            hideChildren(hideChildren, false);
            parent.focus(hideChildren);
        }
    }

    @Override
    public boolean hasParent() {
        return parent != null;
    }

    @Override
    public void execute(final MenuItem menuItem) {
        if (menuItem != null && menuItem.getCommand() != null) {
            final TaskMonitor taskMonitor = createTaskMonitor();
            final Task task = new SimpleTask("Executing menu item " + menuItem.getText());
            taskMonitor.onStart(task);
            Scheduler.get().scheduleDeferred(() -> {
                try {
                    hideAll(false, false);
                    menuItem.getCommand().execute();
                } finally {
                    taskMonitor.onEnd(task);
                }
            });
        }
    }

    private void hideSelf(final boolean autoClose, final boolean ok) {
        getView().cancelDelayedSubMenu();
        HidePopupEvent.builder(this)
                .autoClose(autoClose)
                .ok(ok)
                .fire();
    }

    private void hideChildren(final boolean autoClose, final boolean ok) {
        // First make sure all children are hidden.
        if (currentMenu != null) {
            currentMenu.getView().cancelDelayedSubMenu();
            currentMenu.hideChildren(autoClose, ok);
            currentMenu.hideSelf(autoClose, ok);
            currentMenu = null;
        }
        currentItem = null;
    }

    private void hideParent(final boolean autoClose, final boolean ok) {
        // First make sure all children are hidden.
        if (parent != null) {
            parent.getView().cancelDelayedSubMenu();
            parent.hideSelf(autoClose, ok);
            parent.hideParent(autoClose, ok);
        }
    }

    @Override
    public void escape() {
        hideAll(true, false);
    }

    public void hideAll(final boolean autoClose, final boolean ok) {
        getView().cancelDelayedSubMenu();
        hideChildren(autoClose, ok);
        hideSelf(autoClose, ok);
        hideParent(autoClose, ok);
    }

    public void setParent(final MenuPresenter parent, final MenuItem parentItem) {
        this.parent = parent;
        this.parentItem = parentItem;
    }

    public void setData(final List<Item> items) {
        getView().setData(items);
    }

    public void setAllowCloseOnMoveLeft(final boolean allowCloseOnMoveLeft) {
        getView().setAllowCloseOnMoveLeft(allowCloseOnMoveLeft);
    }


    // --------------------------------------------------------------------------------


    public interface MenuView extends View, Focus, HasUiHandlers<MenuUiHandlers> {

        void ensureItemSelected(Item parentItem);

        void setData(List<Item> items);

        void selectFirstItem();

        void cancelDelayedSubMenu();

        /**
         * If allowCloseOnMoveLeft is true and the menu item has no parent (i.e. a root item)
         * then the menu will be closed. Useful when the menu is triggered by move right on
         * an explorer tree or similar.
         */
        void setAllowCloseOnMoveLeft(final boolean allowCloseOnMoveLeft);
    }
}
