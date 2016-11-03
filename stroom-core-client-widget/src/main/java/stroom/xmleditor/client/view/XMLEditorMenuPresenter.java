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

package stroom.xmleditor.client.view;

import com.google.gwt.user.client.Command;
import com.google.inject.Inject;
import stroom.widget.menu.client.presenter.IconMenuItem;
import stroom.widget.menu.client.presenter.Item;
import stroom.widget.menu.client.presenter.MenuListPresenter;
import stroom.widget.menu.client.presenter.Separator;
import stroom.widget.popup.client.event.HidePopupEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupPosition;
import stroom.widget.popup.client.presenter.PopupView.PopupType;
import stroom.xmleditor.client.presenter.BaseXMLEditorPresenter;

import java.util.ArrayList;
import java.util.List;

/**
 * A context menu for the XML editor.
 */
public class XMLEditorMenuPresenter {
    private BaseXMLEditorPresenter xmlEditorPresenter;
    private final MenuListPresenter menuListPresenter;

    @Inject
    public XMLEditorMenuPresenter(final MenuListPresenter menuListPresenter) {
        this.menuListPresenter = menuListPresenter;

    }

    public void show(final BaseXMLEditorPresenter xmlEditorPresenter, final int x, final int y) {
        this.xmlEditorPresenter = xmlEditorPresenter;
        hide();
        updateText();
        final PopupPosition popupPosition = new PopupPosition(x, y);
        ShowPopupEvent.fire(xmlEditorPresenter, menuListPresenter, PopupType.POPUP, popupPosition, null);
    }

    public void hide() {
        HidePopupEvent.fire(xmlEditorPresenter, menuListPresenter);
    }

    /**
     * Update the labels of the context menu items depending on the current XML
     * editor settings.
     */
    private void updateText() {
        int position = 0;
        final List<Item> menuItems = new ArrayList<Item>();
        menuItems.add(createItem("Refresh", new Command() {
            @Override
            public void execute() {
                xmlEditorPresenter.refresh(false);
            }
        }, position++));
        menuItems.add(new Separator(position++));

        if (xmlEditorPresenter.getStylesOption().isAvailable()) {
            menuItems.add(createItem(xmlEditorPresenter.getStylesOption().getText(), new Command() {
                @Override
                public void execute() {
                    xmlEditorPresenter.getStylesOption().setOn(!xmlEditorPresenter.getStylesOption().isOn());
                }
            }, position++));
        }
        if (xmlEditorPresenter.getIndicatorsOption().isAvailable()) {
            menuItems.add(createItem(xmlEditorPresenter.getIndicatorsOption().getText(), new Command() {
                @Override
                public void execute() {
                    xmlEditorPresenter.getIndicatorsOption().setOn(!xmlEditorPresenter.getIndicatorsOption().isOn());
                }
            }, position++));
        }
        if (xmlEditorPresenter.getLineNumbersOption().isAvailable()) {
            menuItems.add(createItem(xmlEditorPresenter.getLineNumbersOption().getText(), new Command() {
                @Override
                public void execute() {
                    xmlEditorPresenter.getLineNumbersOption().setOn(!xmlEditorPresenter.getLineNumbersOption().isOn());
                }
            }, position++));
        }

        menuItems.add(createItem("Format XML", new Command() {
            @Override
            public void execute() {
                xmlEditorPresenter.formatXML();
            }
        }, position++));

        if (xmlEditorPresenter.isShowFilterSettings()) {
            String title = null;
            if (xmlEditorPresenter.isInput()) {
                title = "Filter Input";
            } else {
                title = "Filter Output";
            }

            menuItems.add(createItem(title, new Command() {
                @Override
                public void execute() {
                    xmlEditorPresenter.changeFilterSettings();
                }
            }, position++));
        }

        menuListPresenter.setData(menuItems);
    }

    private Item createItem(final String text, final Command command, final int position) {
        return new IconMenuItem(position, text, null, true, command);
    }
}
