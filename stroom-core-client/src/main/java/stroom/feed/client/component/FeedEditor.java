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

package stroom.feed.client.component;

import stroom.data.client.component.MetaBrowserController;
import stroom.data.client.component.ProcessorTasksTabController;
import stroom.document.client.component.DocumentationTabController;
import stroom.document.client.editor.DocumentEditor;
import stroom.document.client.editor.LinkTabPanel;
import stroom.feed.shared.FeedDoc;
import stroom.security.client.api.ClientSecurityContext;
import stroom.security.client.component.DocumentUserPermissionsTabController;
import stroom.security.shared.AppPermission;
import stroom.widget.tab.client.TabData;
import stroom.widget.tab.client.TabDataImpl;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

/// A feed's editor: its Data (for a user who may view data, and then shown first), Active Tasks (for
/// a user who may manage processors), Settings, Documentation and Permissions tabs (see
/// [DocumentEditor]).
public class FeedEditor extends DocumentEditor<FeedDoc> {

    private static final TabData SETTINGS = new TabDataImpl("Settings");
    private static final TabData DATA = new TabDataImpl("Data");
    private static final TabData TASKS = new TabDataImpl("Active Tasks");
    private static final TabData DOCUMENTATION = new TabDataImpl("Documentation");
    private static final TabData PERMISSIONS = new TabDataImpl("Permissions");

    /// Makes the editor, with the tabs the user may see.
    ///
    /// @param eventBus               The application's event bus.
    /// @param view                   Its component.
    /// @param securityContext        The user's permissions.
    /// @param settingsProvider       Makes the Settings tab.
    /// @param metaBrowserProvider    Makes the meta browser.
    /// @param taskControllerProvider Makes the task controller.
    /// @param documentationTabProvider   Makes the Documentation tab's editor.
    /// @param permissionsProvider    Makes the Permissions tab.
    @Inject
    public FeedEditor(final EventBus eventBus,
                      final LinkTabPanel view,
                      final ClientSecurityContext securityContext,
                      final Provider<FeedSettingsController> settingsProvider,
                      final Provider<MetaBrowserController> metaBrowserProvider,
                      final Provider<ProcessorTasksTabController> taskControllerProvider,
                      final Provider<DocumentationTabController> documentationTabProvider,
                      final Provider<DocumentUserPermissionsTabController> permissionsProvider) {
        super(eventBus, view);
        TabData selectedTab = SETTINGS;
        if (securityContext.hasAppPermission(AppPermission.VIEW_DATA_PERMISSION)) {
            addTab(DATA,
                    metaBrowserProvider::get,
                    (metaBrowser, docRef, doc) -> {
                        metaBrowser.read(docRef, doc);
                        // Refresh any displayed data so it reflects the (now-saved) feed settings,
                        // e.g. a changed encoding. A feed is read on opening and after each save
                        // (with the saved document, so the server has the new encoding);
                        // refreshData() does nothing unless a stream is displayed.
                        metaBrowser.refreshData();
                    });
            selectedTab = DATA;
        }
        if (securityContext.hasAppPermission(AppPermission.MANAGE_PROCESSORS_PERMISSION)) {
            addTab(TASKS,
                    taskControllerProvider::get,
                    (tasks, docRef, doc) -> tasks.read(docRef, doc));
        }
        addTab(SETTINGS,
                settingsProvider::get,
                FeedSettingsController::read,
                FeedSettingsController::write);
        addDocumentationTab(DOCUMENTATION,
                documentationTabProvider,
                FeedDoc::getDescription,
                (doc, description) -> doc.copy().description(description).build());
        addPermissionsTab(PERMISSIONS, permissionsProvider);
        setDefaultTab(selectedTab);
    }
}
