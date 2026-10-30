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

package stroom.sqlstore.client.component;

import stroom.entity.client.component.DocumentEditor;
import stroom.entity.client.component.DocumentationTabController;
import stroom.entity.client.component.LinkTabPanel;
import stroom.security.client.component.DocumentUserPermissionsTabController;
import stroom.sqlstore.shared.SqlTemporalStoreDoc;
import stroom.widget.tab.client.TabData;
import stroom.widget.tab.client.TabDataImpl;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

/// An SQL temporal store's editor: its Settings, Data, Documentation and Permissions tabs (see
/// [DocumentEditor]).
public class SqlTemporalStoreEditor extends DocumentEditor<SqlTemporalStoreDoc> {

    private static final TabData SETTINGS = new TabDataImpl("Settings");
    private static final TabData DATA = new TabDataImpl("Data");
    private static final TabData DOCUMENTATION = new TabDataImpl("Documentation");
    private static final TabData PERMISSIONS = new TabDataImpl("Permissions");

    /// Makes the editor, with its tabs.
    ///
    /// @param eventBus                     The application's event bus.
    /// @param view                         Its component.
    /// @param sqlTemporalStoreDataProvider Makes the SQL temporal store data.
    /// @param sqlStoreSettingsProvider     Makes the SQL store settings.
    /// @param documentationTabProvider         Makes the Documentation tab's editor.
    /// @param permissionsProvider          Makes the Permissions tab.
    @Inject
    public SqlTemporalStoreEditor(
            final EventBus eventBus,
            final LinkTabPanel view,
            final Provider<SqlTemporalStoreDataController> sqlTemporalStoreDataProvider,
            final Provider<SqlTemporalStoreSettingsController> sqlStoreSettingsProvider,
            final Provider<DocumentationTabController> documentationTabProvider,
            final Provider<DocumentUserPermissionsTabController> permissionsProvider) {
        super(eventBus, view);

        addTab(SETTINGS,
                sqlStoreSettingsProvider::get,
                SqlTemporalStoreSettingsController::read);
        addTab(DATA,
                sqlTemporalStoreDataProvider::get,
                SqlTemporalStoreDataController::read);
        addDocumentationTab(DOCUMENTATION,
                documentationTabProvider,
                SqlTemporalStoreDoc::getDescription,
                (doc, description) -> doc.copy().description(description).build());
        addPermissionsTab(PERMISSIONS, permissionsProvider);
        setDefaultTab(SETTINGS);
    }
}
