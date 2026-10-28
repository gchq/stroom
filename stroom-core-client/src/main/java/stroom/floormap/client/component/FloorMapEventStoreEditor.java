/*
 * Copyright 2026 Crown Copyright
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

package stroom.floormap.client.component;

import stroom.entity.client.component.DocumentEditor;
import stroom.entity.client.component.LinkTabPanel;
import stroom.entity.client.component.MarkdownEditController;
import stroom.floormap.shared.FloorMapEventStoreDoc;
import stroom.security.client.component.DocumentUserPermissionsTabController;
import stroom.widget.tab.client.TabData;
import stroom.widget.tab.client.TabDataImpl;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

/// A floor map event store's editor: its Settings, Data, Documentation and Permissions tabs (see
/// [DocumentEditor]).
public class FloorMapEventStoreEditor extends DocumentEditor<FloorMapEventStoreDoc> {

    private static final TabData SETTINGS = new TabDataImpl("Settings");
    private static final TabData DATA = new TabDataImpl("Data");
    private static final TabData DOCUMENTATION = new TabDataImpl("Documentation");
    private static final TabData PERMISSIONS = new TabDataImpl("Permissions");

    /// Makes the editor, with its tabs.
    @Inject
    public FloorMapEventStoreEditor(
            final EventBus eventBus,
            final LinkTabPanel view,
            final Provider<FloorMapEventStoreSettingsController> settingsProvider,
            final Provider<FloorMapEventStoreDataController> dataProvider,
            final Provider<MarkdownEditController> markdownEditProvider,
            final Provider<DocumentUserPermissionsTabController> permissionsProvider) {
        super(eventBus, view);

        addTab(SETTINGS,
                settingsProvider::get,
                FloorMapEventStoreSettingsController::read,
                FloorMapEventStoreSettingsController::write);
        addTab(DATA,
                dataProvider::get,
                FloorMapEventStoreDataController::read);
        addDocumentationTab(DOCUMENTATION,
                markdownEditProvider,
                FloorMapEventStoreDoc::getDescription,
                (doc, description) -> doc.copyEventStore().description(description).build());
        addPermissionsTab(PERMISSIONS, permissionsProvider);
        setDefaultTab(SETTINGS);
    }
}
