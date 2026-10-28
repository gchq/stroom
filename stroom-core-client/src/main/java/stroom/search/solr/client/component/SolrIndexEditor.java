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

package stroom.search.solr.client.component;

import stroom.entity.client.component.DocumentEditor;
import stroom.entity.client.component.LinkTabPanel;
import stroom.entity.client.component.MarkdownEditController;
import stroom.search.solr.shared.SolrIndexDoc;
import stroom.security.client.component.DocumentUserPermissionsTabController;
import stroom.widget.tab.client.TabData;
import stroom.widget.tab.client.TabDataImpl;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

/// A Solr index's editor: its Fields, Settings, Documentation and Permissions tabs (see
/// [DocumentEditor]).
public class SolrIndexEditor extends DocumentEditor<SolrIndexDoc> {

    private static final TabData SETTINGS = new TabDataImpl("Settings");
    private static final TabData FIELDS = new TabDataImpl("Fields");
    private static final TabData DOCUMENTATION = new TabDataImpl("Documentation");
    private static final TabData PERMISSIONS = new TabDataImpl("Permissions");

    /// Makes the editor, with its tabs.
    @Inject
    public SolrIndexEditor(final EventBus eventBus,
                           final LinkTabPanel view,
                           final Provider<SolrIndexSettingsController> indexSettingsProvider,
                           final Provider<SolrIndexFieldListController> indexFieldListProvider,
                           final Provider<MarkdownEditController> markdownEditProvider,
                           final Provider<DocumentUserPermissionsTabController> permissionsProvider) {
        super(eventBus, view);

        addTab(FIELDS,
                indexFieldListProvider::get,
                SolrIndexFieldListController::read,
                SolrIndexFieldListController::write);
        addTab(SETTINGS,
                indexSettingsProvider::get,
                SolrIndexSettingsController::read,
                SolrIndexSettingsController::write);
        addDocumentationTab(DOCUMENTATION,
                markdownEditProvider,
                SolrIndexDoc::getDescription,
                (doc, description) -> doc.copy().description(description).build());
        addPermissionsTab(PERMISSIONS, permissionsProvider);
        setDefaultTab(FIELDS);
    }
}
