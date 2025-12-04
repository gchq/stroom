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

import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupType;

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
            currentItems = event.getItems();

            ShowPopupEvent.builder(menuPresenter)
                    .popupType(PopupType.POPUP)
                    .popupPosition(event.getPopupPosition())
                    .addAutoHidePartner(event.getAutoHidePartners())
                    .onShow(e -> menuPresenter.focus())
                    .onHide(e -> {
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
