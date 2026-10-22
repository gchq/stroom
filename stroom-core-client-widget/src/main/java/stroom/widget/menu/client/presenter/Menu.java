/*
 * Copyright 2021 Crown Copyright
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

import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupType;
import stroom.widget.util.client.InputModality;

import com.google.gwt.core.client.GWT;
import com.google.web.bindery.event.shared.EventBus;

import java.util.List;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;

@Singleton
public class Menu {

    private final Provider<MenuPresenter> menuPresenterProvider;

    private MenuPresenter menuPresenter;
    private List<Item> currentItems;

    @Inject
    public Menu(final EventBus eventBus,
                final Provider<MenuPresenter> menuPresenterProvider) {
        this.menuPresenterProvider = menuPresenterProvider;
        // So that a menu knows whether it was opened with the keyboard (and shows its focus ring)
        InputModality.install();
        eventBus.addHandler(ShowMenuEvent.getType(), this::show);
        eventBus.addHandler(HideMenuEvent.getType(), this::hide);
    }

    private void show(final ShowMenuEvent event) {
        if (event.getItems() == currentItems) {
            hide(false, false);

        } else {
            hide(false, false);

            menuPresenter = menuPresenterProvider.get();
            menuPresenter.setData(event.getItems());
            menuPresenter.setAllowCloseOnMoveLeft(event.isAllowCloseOnMoveLeft());
            setName(menuPresenter, event);
            if (event.getWidth() != null) {
                menuPresenter.setWidth(event.getWidth());
            }
            currentItems = event.getItems();

            ShowPopupEvent.builder(menuPresenter)
                    .popupType(PopupType.POPUP)
                    .popupPosition(event.getPopupPosition())
                    .addAutoHidePartner(event.getAutoHidePartners())
                    .onShow(e -> {
                        // Set once showing, after any menu this one replaced has said it closed, but
                        // not if this menu was itself closed before it got this far
                        if (currentItems == event.getItems()) {
                            MenuOpener.setExpanded(event.getOpener(), true);
                        }
                        menuPresenter.focusItem(event.getFocusItem());
                    })
                    .onHide(e -> {
                        MenuOpener.setExpanded(event.getOpener(), false);
                        if (event.getHideHandler() != null) {
                            event.getHideHandler().onHide(e);
                        }
                        // Call hide to ensure any delayed sub menus are closed
                        hide(false, false);
                        menuPresenter = null;
                        currentItems = null;
                    })
                    .fire();
        }
    }

    // Names the menu for screen readers: by the name it was given, else by the menu button that
    // opened it. Other openers (e.g. table cells or logos) are only where the menu is shown and
    // may be redrawn, so aren't referred to
    private static void setName(final MenuPresenter menuPresenter, final ShowMenuEvent event) {
        if (event.getName() != null) {
            menuPresenter.setName(event.getName());
        } else if (MenuOpener.isMarked(event.getOpener())) {
            menuPresenter.setLabelledBy(MenuOpener.ensureId(event.getOpener()));
        } else if (!GWT.isProdMode()) {
            GWT.log("Menu shown with no accessible name; give it one with ShowMenuEvent.Builder#name");
        }
    }

    private void hide(final HideMenuEvent e) {
        hide(false, false);
    }

    private void hide(final boolean autoClose, final boolean ok) {
        if (menuPresenter != null) {
            menuPresenter.hideAll(autoClose, ok);
            menuPresenter = null;
            currentItems = null;
        }
    }
}
