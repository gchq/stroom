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

import stroom.task.client.SimpleTask;
import stroom.task.client.Task;
import stroom.task.client.TaskMonitor;
import stroom.widget.menu.client.presenter.MenuPresenter.MenuView;
import stroom.widget.popup.client.event.HidePopupEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupPosition;
import stroom.widget.popup.client.presenter.PopupPosition.PopupLocation;
import stroom.widget.popup.client.presenter.PopupType;
import stroom.widget.util.client.ElementUtil;
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
    // How far a menu's items sit in from its top edge (its border and its viewport's padding, menu.css),
    // so that a sub menu's first item lines up with the item that opened it
    private static final int VERTICAL_PADDING = 6;
    private static final String ITEM_TEXT_CLASS = "menu__text";

    private final Provider<MenuPresenter> menuPresenterProvider;
    private MenuPresenter currentMenu;
    private MenuItem currentItem;
    // Whether the current sub menu was opened (or since clicked) by a click on its item, rather
    // than by hovering over it or the keyboard
    private boolean subMenuOpenedByClick;

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
    public void toggleSubMenu(final MenuItem menuItem, final Element element) {
        if (currentItem != null && Objects.equals(currentItem, menuItem)) {
            if (subMenuOpenedByClick) {
                hideChildren(false, false);
            } else {
                // The sub menu was opened by hovering over its item, perhaps only just, so a click
                // on the item (likely meant to open it) leaves it open. A further click closes it
                subMenuOpenedByClick = true;
            }
        } else {
            showSubMenu(menuItem, element, true);
        }
    }

    @Override
    public void showSubMenu(final MenuItem menuItem, final Element element) {
        showSubMenu(menuItem, element, false);
    }

    private void showSubMenu(final MenuItem menuItem, final Element element, final boolean byClick) {
        if (!Objects.equals(currentItem, menuItem)) {
            subMenuOpenedByClick = byClick;
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
                        // Named by the text of its parent item. Not by reference to the item's
                        // element, which loses any id it is given when the menu redraws its rows
                        presenter.setName(getItemText(element));
//                        presenter.setHighlightItems(getHighlightItems());
                        presenter.setData(children);

                        // Set the current presenter telling us that the
                        // popup is showing.
                        currentMenu = presenter;
                        setCurrentItem(menuItem);

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
                                    if (!e.isAutoClose() && !e.isOk()) {
                                        // Escape, which the sub menu's popup takes as its close
                                        // action: close the sub menu, as Escape in it does
                                        presenter.escape();
                                    } else {
                                        presenter.hideChildren(e.isAutoClose(), e.isOk());
                                        presenter.hideSelf(e.isAutoClose(), e.isOk());
                                        currentMenu = null;
                                        setCurrentItem(null);
                                    }
                                })
                                .fire();
                    }
                });
            }
        }
    }

    // The text of a menu item's row, without its shortcut or arrow
    private static String getItemText(final Element rowElement) {
        if (rowElement == null) {
            return null;
        }
        final Element text = ElementUtil.findChild(rowElement, MenuPresenter::isItemText);
        final String name = text != null
                ? text.getInnerText()
                : rowElement.getInnerText();
        return name == null
                ? null
                : name.trim();
    }

    // Whether an element holds a menu item's text. Its class is read as an attribute, as an SVG
    // element's className isn't a string
    private static boolean isItemText(final Element element) {
        final String className = ElementUtil.getClassName(element);
        if (className == null) {
            return false;
        }
        for (final String name : className.split(" ")) {
            if (ITEM_TEXT_CLASS.equals(name)) {
                return true;
            }
        }
        return false;
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

    /// Focuses an item of the menu, or its first if the item is null or not in the menu.
    ///
    /// @param item The item to focus. Can be null.
    public void focusItem(final Item item) {
        if (item != null) {
            getView().ensureItemSelected(item);
        }
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
        setCurrentItem(null);
    }

    // The item whose sub menu is open, or null for none
    private void setCurrentItem(final MenuItem item) {
        currentItem = item;
        getView().setOpenItem(item);
    }

    private void hideParent(final boolean autoClose, final boolean ok) {
        // First make sure all children are hidden.
        if (parent != null) {
            parent.getView().cancelDelayedSubMenu();
            parent.hideSelf(autoClose, ok);
            parent.hideParent(autoClose, ok);
        }
    }

    /// Closes this menu: a sub menu closes and puts the focus back on the item that opened it, as
    /// Left does (the WAI-ARIA menu pattern); the root menu closes with any sub menus.
    @Override
    public void escape() {
        if (parent != null) {
            focusParent(true);
        } else {
            hideAll(true, false);
        }
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

    /// Gives the menu an accessible name, which screen readers announce on entering it.
    ///
    /// @param name The name, as plain text without the word "menu", or null for none.
    public void setName(final String name) {
        getView().setName(name);
    }

    /// Names the menu by another element, e.g. the button that opened it.
    ///
    /// @param id The id of the element that names the menu, or null for none.
    public void setLabelledBy(final String id) {
        getView().setLabelledBy(id);
    }

    /// Gives the menu a fixed width rather than fitting it to its items. See
    /// [ShowMenuEvent.Builder#width(int)].
    ///
    /// @param width The menu's width in pixels.
    public void setWidth(final int width) {
        getView().setWidth(width);
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

        /// Gives the menu a fixed width, cutting items too long for it short with an ellipsis.
        ///
        /// @param width The menu's width in pixels.
        void setWidth(int width);

        /// Gives the menu an accessible name (`aria-label`), replacing any element it was
        /// labelled by.
        ///
        /// @param name The name, as plain text, or null or empty for none.
        void setName(String name);

        /// Names the menu by another element (`aria-labelledby`), replacing any name it was given.
        ///
        /// @param id The id of the element that names the menu, or null or empty for none.
        void setLabelledBy(String id);

        /// Says which item's sub menu is open (`aria-expanded` on the items that have one).
        ///
        /// @param item The item whose sub menu is open, or null for none.
        void setOpenItem(Item item);
    }
}
