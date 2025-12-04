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

package stroom.explorer.client;

import stroom.content.client.event.ContentTabSelectionChangeEvent;
import stroom.core.client.MenuKeys;
import stroom.core.client.presenter.Plugin;
import stroom.docref.DocRef;
import stroom.document.client.DocumentTabData;
import stroom.document.client.event.SetDocumentAsFavouriteEvent;
import stroom.explorer.client.event.LocateDocEvent;
import stroom.explorer.client.event.ShowFindEvent;
import stroom.explorer.client.event.ShowFindInContentEvent;
import stroom.explorer.client.event.ShowRecentItemsEvent;
import stroom.menubar.client.event.BeforeRevealMenubarEvent;
import stroom.svg.shared.SvgImage;
import stroom.widget.menu.client.presenter.IconMenuItem;
import stroom.widget.util.client.KeyBinding.Action;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import javax.inject.Singleton;

@Singleton
public class NavigationPlugin extends Plugin {

    private DocRef selectedDoc;

    @Inject
    public NavigationPlugin(final EventBus eventBus) {
        super(eventBus);
        // track the currently selected doc.
        registerHandler(getEventBus().addHandler(ContentTabSelectionChangeEvent.getType(), e -> {
            if (e.getTabData() instanceof DocumentTabData) {
                final DocumentTabData documentTabData = (DocumentTabData) e.getTabData();
                selectedDoc = documentTabData.getDocRef();
            } else {
                selectedDoc = null;
            }
        }));
    }

    @Override
    public void onReveal(final BeforeRevealMenubarEvent event) {
        MenuKeys.addNavigationMenu(event.getMenuItems());
        event.getMenuItems().addMenuItem(MenuKeys.NAVIGATION_MENU,
                new IconMenuItem.Builder()
                        .priority(201)
                        .icon(SvgImage.FIND)
                        .text("Find")
                        .action(Action.FIND)
                        .command(() -> ShowFindEvent.fire(NavigationPlugin.this))
                        .build());
        event.getMenuItems().addMenuItem(MenuKeys.NAVIGATION_MENU,
                new IconMenuItem.Builder()
                        .priority(202)
                        .icon(SvgImage.FIND)
                        .text("Find in Content")
                        .action(Action.FIND_IN_CONTENT)
                        .command(() -> ShowFindInContentEvent.fire(NavigationPlugin.this))
                        .build());
        event.getMenuItems().addMenuItem(MenuKeys.NAVIGATION_MENU,
                new IconMenuItem.Builder()
                        .priority(203)
                        .icon(SvgImage.HISTORY)
                        .text("Recent Items")
                        .action(Action.RECENT_ITEMS)
                        .command(() -> ShowRecentItemsEvent.fire(NavigationPlugin.this))
                        .build());
        event.getMenuItems().addMenuItem(MenuKeys.NAVIGATION_MENU,
                new IconMenuItem.Builder()
                        .priority(204)
                        .icon(SvgImage.LOCATE)
                        .text("Locate Current Item")
                        .action(Action.LOCATE)
                        .enabled(selectedDoc != null)
                        .command(() -> LocateDocEvent.fire(NavigationPlugin.this, selectedDoc))
                        .build());
        event.getMenuItems().addMenuItem(MenuKeys.NAVIGATION_MENU,
                new IconMenuItem.Builder()
                        .priority(205)
                        .icon(SvgImage.FAVOURITES)
                        .text("Add Current Item to Favourites")
                        .enabled(selectedDoc != null)
                        .command(() -> SetDocumentAsFavouriteEvent.fire(
                                NavigationPlugin.this, selectedDoc, true))
                        .build());
    }
}
