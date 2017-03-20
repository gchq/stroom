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

package stroom.editor.client.view;

import com.google.gwt.user.client.Command;
import com.google.inject.Inject;
import stroom.editor.client.presenter.EditorPresenter;
import stroom.widget.menu.client.presenter.IconMenuItem;
import stroom.widget.menu.client.presenter.Item;
import stroom.widget.menu.client.presenter.MenuListPresenter;
import stroom.widget.popup.client.event.HidePopupEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupPosition;
import stroom.widget.popup.client.presenter.PopupView.PopupType;

import java.util.ArrayList;
import java.util.List;

/**
 * A context menu for the XML editor.
 */
public class EditorMenuPresenter {
    private boolean showFormatOption = true;
    private EditorPresenter xmlEditorPresenter;
    private final MenuListPresenter menuListPresenter;

    @Inject
    public EditorMenuPresenter(final MenuListPresenter menuListPresenter) {
        this.menuListPresenter = menuListPresenter;

    }

    public void show(final EditorPresenter xmlEditorPresenter, final int x, final int y) {
        this.xmlEditorPresenter = xmlEditorPresenter;
        HidePopupEvent.fire(xmlEditorPresenter, menuListPresenter);
        updateText();
        final PopupPosition popupPosition = new PopupPosition(x, y);
        ShowPopupEvent.fire(xmlEditorPresenter, menuListPresenter, PopupType.POPUP, popupPosition, null);
    }

    public void hide() {
        if (xmlEditorPresenter != null) {
            HidePopupEvent.fire(xmlEditorPresenter, menuListPresenter);
            xmlEditorPresenter = null;
        }
    }

    /**
     * Update the labels of the context menu items depending on the current XML
     * editor settings.
     */
    private void updateText() {
        int position = 0;
        final List<Item> menuItems = new ArrayList<>();
        if (xmlEditorPresenter.getStylesOption().isAvailable()) {
            menuItems.add(createItem(xmlEditorPresenter.getStylesOption().getText(), () ->
                    xmlEditorPresenter.getStylesOption().setOn(!xmlEditorPresenter.getStylesOption().isOn()), position++));
        }
        if (xmlEditorPresenter.getIndicatorsOption().isAvailable()) {
            menuItems.add(createItem(xmlEditorPresenter.getIndicatorsOption().getText(), () ->
                    xmlEditorPresenter.getIndicatorsOption().setOn(!xmlEditorPresenter.getIndicatorsOption().isOn()), position++));
        }
        if (xmlEditorPresenter.getLineNumbersOption().isAvailable()) {
            menuItems.add(createItem(xmlEditorPresenter.getLineNumbersOption().getText(), () ->
                    xmlEditorPresenter.getLineNumbersOption().setOn(!xmlEditorPresenter.getLineNumbersOption().isOn()), position++));
        }

        if (showFormatOption) {
            menuItems.add(createItem("Format", () -> xmlEditorPresenter.format(), position++));
        }

        if (xmlEditorPresenter.isShowFilterSettings()) {
            String title;
            if (xmlEditorPresenter.isInput()) {
                title = "Filter Input";
            } else {
                title = "Filter Output";
            }

            menuItems.add(createItem(title, () -> xmlEditorPresenter.changeFilterSettings(), position++));
        }

        menuListPresenter.setData(menuItems);
    }

    private Item createItem(final String text, final Command command, final int position) {
        return new IconMenuItem(position, text, null, true, command);
    }

    public void setShowFormatOption(final boolean showFormatOption) {
        this.showFormatOption = showFormatOption;
    }
}
