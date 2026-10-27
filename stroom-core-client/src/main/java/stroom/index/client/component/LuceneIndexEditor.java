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

package stroom.index.client.component;

import stroom.entity.client.component.DocumentEditor;
import stroom.entity.client.component.MarkdownEditController;
import stroom.entity.client.presenter.LinkTabPanelView;
import stroom.index.shared.LuceneIndexDoc;
import stroom.security.client.api.ClientSecurityContext;
import stroom.security.client.component.DocumentUserPermissionsTabController;
import stroom.security.shared.AppPermission;
import stroom.widget.tab.client.presenter.TabData;
import stroom.widget.tab.client.presenter.TabDataImpl;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

/// A Lucene index's editor: its Shards (for a user who may manage index shards, and then shown
/// first), Fields, Settings, Data, Documentation and Permissions tabs (see [DocumentEditor]).
public class LuceneIndexEditor extends DocumentEditor<LuceneIndexDoc> {

    private static final TabData SETTINGS = new TabDataImpl("Settings");
    private static final TabData FIELDS = new TabDataImpl("Fields");
    private static final TabData SHARDS = new TabDataImpl("Shards");
    private static final TabData DATA = new TabDataImpl("Data");
    private static final TabData DOCUMENTATION = new TabDataImpl("Documentation");
    private static final TabData PERMISSIONS = new TabDataImpl("Permissions");

    /// Makes the editor, with its tabs.
    @Inject
    public LuceneIndexEditor(final EventBus eventBus,
                             final LinkTabPanelView view,
                             final Provider<IndexSettingsController> indexSettingsProvider,
                             final Provider<IndexFieldListController> indexFieldListProvider,
                             final Provider<IndexShardListController> indexShardListProvider,
                             final Provider<IndexDataController> indexDataProvider,
                             final Provider<MarkdownEditController> markdownEditProvider,
                             final Provider<DocumentUserPermissionsTabController> permissionsProvider,
                             final ClientSecurityContext securityContext) {
        super(eventBus, view);

        TabData selectFirst = FIELDS;

        if (securityContext.hasAppPermission(AppPermission.MANAGE_INDEX_SHARDS_PERMISSION)) {
            selectFirst = SHARDS;
            addTab(SHARDS,
                    indexShardListProvider::get,
                    IndexShardListController::read);
        }

        addTab(FIELDS,
                indexFieldListProvider::get,
                IndexFieldListController::read);
        addTab(SETTINGS,
                indexSettingsProvider::get,
                IndexSettingsController::read,
                IndexSettingsController::write);
        addTab(DATA,
                indexDataProvider::get,
                IndexDataController::read);
        addDocumentationTab(DOCUMENTATION,
                markdownEditProvider,
                LuceneIndexDoc::getDescription,
                (doc, description) -> doc.copy().description(description).build());
        addPermissionsTab(PERMISSIONS, permissionsProvider);
        selectTab(selectFirst);
    }
}
