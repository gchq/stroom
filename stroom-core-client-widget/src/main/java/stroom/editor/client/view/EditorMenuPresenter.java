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

package stroom.editor.client.view;

import stroom.editor.client.presenter.Action;
import stroom.editor.client.presenter.EditorPresenter;
import stroom.editor.client.presenter.Option;
import stroom.widget.menu.client.presenter.IconMenuItem;
import stroom.widget.menu.client.presenter.Item;
import stroom.widget.menu.client.presenter.ShowMenuEvent;
import stroom.widget.popup.client.presenter.PopupPosition;

import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.user.client.Command;

import java.util.ArrayList;
import java.util.List;

/**
 * A context menu for the XML editor.
 */
public class EditorMenuPresenter {

    private EditorPresenter xmlEditorPresenter;

    public void show(final EditorPresenter xmlEditorPresenter,
                     final PopupPosition popupPosition) {
        this.xmlEditorPresenter = xmlEditorPresenter;
        ShowMenuEvent
                .builder()
                .items(getMenuItems())
                .popupPosition(popupPosition)
                .fire(xmlEditorPresenter);
    }

    /**
     * Update the labels of the context menu items depending on the current XML
     * editor settings.
     */
    private List<Item> getMenuItems() {
        // TODO @AT Consider using MenuBuilder.builder() instead
        int position = 0;
        final List<Item> menuItems = new ArrayList<>();
        addMenuItem(position++, menuItems, xmlEditorPresenter.getStylesOption());
        addMenuItem(position++, menuItems, xmlEditorPresenter.getIndicatorsOption());
        addMenuItem(position++, menuItems, xmlEditorPresenter.getLineNumbersOption());
        addMenuItem(position++, menuItems, xmlEditorPresenter.getLineWrapOption());
        addMenuItem(position++, menuItems, xmlEditorPresenter.getShowIndentGuides());
        addMenuItem(position++, menuItems, xmlEditorPresenter.getShowInvisiblesOption());
        addMenuItem(position++, menuItems, xmlEditorPresenter.getViewAsHexOption());
        addMenuItem(position++, menuItems, xmlEditorPresenter.getHighlightActiveLineOption());
        addMenuItem(position++, menuItems, xmlEditorPresenter.getUseVimBindingsOption());
        addMenuItem(position++, menuItems, xmlEditorPresenter.getBasicAutoCompletionOption());
        addMenuItem(position++, menuItems, xmlEditorPresenter.getSnippetsOption());
        addMenuItem(position++, menuItems, xmlEditorPresenter.getLiveAutoCompletionOption());
        addMenuItem(position++, menuItems, xmlEditorPresenter.getFormatAction());
        return menuItems;
    }

    private void addMenuItem(final int position, final List<Item> menuItems, final Option option) {
        if (option.isAvailable()) {
            menuItems.add(createItem(
                    option.getText(),
                    () -> option.setOn(!option.isOn()),
                    position));
        }
    }

    private void addMenuItem(final int position, final List<Item> menuItems, final Action action) {
        if (action.isAvailable()) {
            menuItems.add(createItem(
                    action.getText(),
                    action::execute,
                    position));
        }
    }

    private Item createItem(final SafeHtml text, final Command command, final int position) {
        return new IconMenuItem.Builder()
                .priority(position)
                .text(text)
                .command(command)
                .build();
    }
}
