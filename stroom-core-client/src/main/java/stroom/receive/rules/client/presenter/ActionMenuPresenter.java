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

package stroom.receive.rules.client.presenter;

import stroom.editor.client.presenter.Action;
import stroom.editor.client.presenter.Option;
import stroom.widget.menu.client.presenter.IconMenuItem;
import stroom.widget.menu.client.presenter.Item;
import stroom.widget.menu.client.presenter.MenuListPresenter;
import stroom.widget.popup.client.event.HidePopupEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupPosition;
import stroom.widget.popup.client.presenter.PopupView.PopupType;

import com.google.gwt.event.shared.HasHandlers;
import com.google.gwt.user.client.Command;
import com.google.inject.Inject;

import java.util.ArrayList;
import java.util.List;

/**
 * A context menu for the XML editor.
 */
public class ActionMenuPresenter {

    private final MenuListPresenter menuListPresenter;
    private HasHandlers hasHandlers;

    @Inject
    public ActionMenuPresenter(final MenuListPresenter menuListPresenter) {
        this.menuListPresenter = menuListPresenter;

    }

    public void show(final HasHandlers hasHandlers,
                     final List<Item> items,
                     final int x,
                     final int y) {
        this.hasHandlers = hasHandlers;
        HidePopupEvent.fire(hasHandlers, menuListPresenter);
        menuListPresenter.setData(items);
//        updateText();
        final PopupPosition popupPosition = new PopupPosition(x + 10, y);
        ShowPopupEvent.fire(hasHandlers, menuListPresenter, PopupType.POPUP, popupPosition, null);
    }

    public void hide() {
        if (hasHandlers != null) {
            HidePopupEvent.fire(hasHandlers, menuListPresenter);
            hasHandlers = null;
        }
    }

    /**
     * Update the labels of the context menu items depending on the current XML
     * editor settings.
     */
    private void updateText() {
        int position = 0;
        final List<Item> menuItems = new ArrayList<>();
//        addMenuItem(position++, menuItems, xmlEditorPresenter.getStylesOption());
//        addMenuItem(position++, menuItems, xmlEditorPresenter.getIndicatorsOption());
//        addMenuItem(position++, menuItems, xmlEditorPresenter.getLineNumbersOption());
//        addMenuItem(position++, menuItems, xmlEditorPresenter.getLineWrapOption());
//        addMenuItem(position++, menuItems, xmlEditorPresenter.getShowInvisiblesOption());
//        addMenuItem(position++, menuItems, xmlEditorPresenter.getHighlightActiveLineOption());
//        addMenuItem(position++, menuItems, xmlEditorPresenter.getUseVimBindingsOption());
//        addMenuItem(position++, menuItems, xmlEditorPresenter.getBasicAutoCompletionOption());
//        addMenuItem(position++, menuItems, xmlEditorPresenter.getSnippetsOption());
//        addMenuItem(position++, menuItems, xmlEditorPresenter.getLiveAutoCompletionOption());
//
//        addMenuItem(position++, menuItems, xmlEditorPresenter.getFormatAction());

//        if (xmlEditorPresenter.isShowFilterSettings()) {
//            String title;
//            if (xmlEditorPresenter.isInput()) {
//                title = "Filter Input";
//            } else {
//                title = "Filter Output";
//            }
//
//            menuItems.add(createItem(title, () -> xmlEditorPresenter.changeFilterSettings(), position++));
//        }

        menuListPresenter.setData(menuItems);
    }

    private void addMenuItem(int position, final List<Item> menuItems, final Option option) {
        if (option.isAvailable()) {
            menuItems.add(createItem(option.getText(), () ->
                    option.setOn(!option.isOn()), position));
        }
    }

    private void addMenuItem(int position, final List<Item> menuItems, final Action action) {
        if (action.isAvailable()) {
            menuItems.add(createItem(action.getText(), action::execute, position));
        }
    }

    private Item createItem(final String text, final Command command, final int position) {
        return new IconMenuItem(position, text, null, true, command);
    }


//    public static interface ActionMenuItemsProvider<R> {
//
//        List<Item> supplyItems(final R row);
//    }
}
