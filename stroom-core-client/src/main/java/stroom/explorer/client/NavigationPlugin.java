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

package stroom.explorer.client;

import stroom.content.client.event.ContentTabSelectionChangeEvent;
import stroom.core.client.MenuKeys;
import stroom.core.client.presenter.Plugin;
import stroom.docref.DocRef;
import stroom.document.client.DocumentPluginRegistry;
import stroom.document.client.event.SetDocumentAsFavouriteEvent;
import stroom.explorer.client.component.ExplorerNodeEditTagsController;
import stroom.explorer.client.component.ExplorerNodeRemoveTagsController;
import stroom.explorer.client.component.FindController;
import stroom.explorer.client.component.FindInContentController;
import stroom.explorer.client.component.RecentItemsController;
import stroom.explorer.client.event.DeleteTabSessionEvent;
import stroom.explorer.client.event.LocateDocEvent;
import stroom.explorer.client.event.OpenTabSessionEvent;
import stroom.explorer.client.event.SaveTabSessionEvent;
import stroom.explorer.client.event.ShowEditNodeTagsDialogEvent;
import stroom.explorer.client.event.ShowFindEvent;
import stroom.explorer.client.event.ShowFindInContentEvent;
import stroom.explorer.client.event.ShowRecentItemsEvent;
import stroom.explorer.client.event.ShowRemoveNodeTagsDialogEvent;
import stroom.explorer.client.event.TabSessionChangeEvent;
import stroom.explorer.shared.TabSession;
import stroom.menubar.client.event.BeforeRevealMenubarEvent;
import stroom.svg.shared.SvgImage;
import stroom.widget.component.client.AppRoot;
import stroom.widget.menu.client.IconMenuItem;
import stroom.widget.menu.client.Separator;
import stroom.widget.util.client.KeyBinding.Action;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

import java.util.List;
import javax.inject.Singleton;

@Singleton
public class NavigationPlugin extends Plugin {

    private DocRef selectedDoc;
    private List<TabSession> tabSessions;
    private FindController find;
    private FindInContentController findInContent;
    private RecentItemsController recentItems;

    @Inject
    public NavigationPlugin(final EventBus eventBus,
                            final DocumentPluginRegistry documentPluginRegistry,
                            final Provider<ExplorerNodeEditTagsController> editTagsProvider,
                            final Provider<ExplorerNodeRemoveTagsController> removeTagsProvider,
                            final Provider<FindInContentController> findInContentProvider,
                            final AppRoot appRoot,
                            final Provider<FindController> findProvider,
                            final Provider<RecentItemsController> recentItemsProvider) {
        super(eventBus);
        // track the currently selected doc.
        getEventBus().addHandler(ContentTabSelectionChangeEvent.getType(), e -> {
            selectedDoc = documentPluginRegistry.getExplorerDocRef(e.getTabData());
        });

        getEventBus().addHandler(TabSessionChangeEvent.getType(), e -> {
            tabSessions = e.getTabSessions();
        });

        // The screens it opens, made when first asked for
        eventBus.addHandler(ShowEditNodeTagsDialogEvent.getType(), event ->
                editTagsProvider.get().show(appRoot, event.getExplorerNodes()));
        eventBus.addHandler(ShowRemoveNodeTagsDialogEvent.getType(), event ->
                removeTagsProvider.get().show(appRoot, event.getExplorerNodes()));
        // Find, Find In Content and Recent Items are made once and kept, to keep their last filter
        eventBus.addHandler(ShowFindInContentEvent.getType(), event -> {
            if (findInContent == null) {
                findInContent = appRoot.make(findInContentProvider);
            }
            findInContent.show();
        });
        eventBus.addHandler(ShowFindEvent.getType(), event -> {
            if (find == null) {
                find = appRoot.make(findProvider);
            }
            find.show();
        });
        eventBus.addHandler(ShowRecentItemsEvent.getType(), event -> {
            if (recentItems == null) {
                recentItems = appRoot.make(recentItemsProvider);
            }
            recentItems.show();
        });
    }

    @Override
    public void onReveal(final BeforeRevealMenubarEvent event) {
        MenuKeys.addNavigationMenu(event.getMenuItems());
        event.getMenuItems().addMenuItem(MenuKeys.NAVIGATION_MENU,
                new IconMenuItem.Builder()
                        .priority(201)
                        .icon(SvgImage.FIND)
                        .text("Find by Name")
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

        event.getMenuItems().addMenuItem(MenuKeys.NAVIGATION_MENU, new Separator(210));

        event.getMenuItems().addMenuItem(MenuKeys.NAVIGATION_MENU,
                new IconMenuItem.Builder()
                        .priority(211)
                        .icon(SvgImage.OPEN)
                        .text("Open Tab Session")
                        .enabled(tabSessions != null && !tabSessions.isEmpty())
                        .command(() -> OpenTabSessionEvent.fire(this))
                        .build()
        );

        event.getMenuItems().addMenuItem(MenuKeys.NAVIGATION_MENU,
                new IconMenuItem.Builder()
                        .priority(212)
                        .icon(SvgImage.SAVE)
                        .text("Save Tab Session")
                        .command(() -> SaveTabSessionEvent.fire(this))
                        .build());

        event.getMenuItems().addMenuItem(MenuKeys.NAVIGATION_MENU,
                new IconMenuItem.Builder()
                        .priority(213)
                        .icon(SvgImage.DELETE)
                        .text("Delete Tab Session")
                        .enabled(tabSessions != null && !tabSessions.isEmpty())
                        .command(() -> DeleteTabSessionEvent.fire(this))
                        .build());
    }

    public void setTabSessions(final List<TabSession> tabSessions) {
        this.tabSessions = tabSessions;
    }
}
