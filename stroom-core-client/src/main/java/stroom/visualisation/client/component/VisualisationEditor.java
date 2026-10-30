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

package stroom.visualisation.client.component;

import stroom.document.asset.client.component.DocumentAssetsTabController;
import stroom.document.client.component.DocumentationTabController;
import stroom.document.client.editor.DocumentEditor;
import stroom.document.client.editor.LinkTabPanel;
import stroom.security.client.component.DocumentUserPermissionsTabController;
import stroom.visualisation.shared.VisualisationDoc;
import stroom.widget.tab.client.TabData;
import stroom.widget.tab.client.TabDataImpl;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

/// A visualisation's editor: its Settings, Assets, Documentation and Permissions tabs (see
/// [DocumentEditor]). Its assets aren't held in the document but in a server-side draft and live
/// store, so they are saved after it, and their changes make it dirty too.
public class VisualisationEditor extends DocumentEditor<VisualisationDoc> {

    private static final TabData SETTINGS = new TabDataImpl("Settings");
    private static final TabData ASSETS = new TabDataImpl("Assets");
    private static final TabData DOCUMENTATION = new TabDataImpl("Documentation");
    private static final TabData PERMISSIONS = new TabDataImpl("Permissions");

    private final DocumentAssetsTabController<VisualisationDoc> assetsTab;

    /// Makes the editor, with its Settings, Assets, Documentation and Permissions tabs.
    ///
    /// @param eventBus             The application's event bus.
    /// @param view                 Its component.
    /// @param settingsProvider     Makes the Settings tab.
    /// @param assetsTab            The assets tab.
    /// @param documentationTabProvider Makes the Documentation tab's editor.
    /// @param permissionsProvider  Makes the Permissions tab.
    @Inject
    public VisualisationEditor(final EventBus eventBus,
                               final LinkTabPanel view,
                               final Provider<VisualisationSettingsController> settingsProvider,
                               final DocumentAssetsTabController<VisualisationDoc> assetsTab,
                               final Provider<DocumentationTabController> documentationTabProvider,
                               final Provider<DocumentUserPermissionsTabController> permissionsProvider) {
        super(eventBus, view);
        this.assetsTab = own(assetsTab);
        addTab(SETTINGS,
                settingsProvider::get,
                VisualisationSettingsController::read,
                VisualisationSettingsController::write);
        // The assets are saved after the document (getPostSaveCallback), so it writes nothing
        addOwnedTab(ASSETS, assetsTab, DocumentAssetsTabController::read);
        addDocumentationTab(DOCUMENTATION,
                documentationTabProvider,
                VisualisationDoc::getDescription,
                (doc, description) -> doc.copy().description(description).build());
        addPermissionsTab(PERMISSIONS, permissionsProvider);
        setDefaultTab(SETTINGS);
    }

    @Override
    protected void afterSelectTab(final TabData tab) {
        if (ASSETS.equals(tab)) {
            onChange();
        }
    }

    @Override
    protected boolean hasAssociatedDirty() {
        return assetsTab.isDirty();
    }

    @Override
    public BiConsumer<VisualisationDoc, Consumer<VisualisationDoc>> getPostSaveCallback() {
        return assetsTab::onSave;
    }

    @Override
    public BiConsumer<VisualisationDoc, Consumer<VisualisationDoc>> getPostSaveAsCallback() {
        // The assets are copied from the document being saved, even if its Assets tab was never shown
        return (copy, callback) -> assetsTab.onSaveAs(getDocument(), copy, callback);
    }
}
