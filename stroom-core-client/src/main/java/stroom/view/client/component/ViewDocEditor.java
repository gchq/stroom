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

package stroom.view.client.component;

import stroom.document.client.component.DocumentationTabController;
import stroom.document.client.editor.DocumentEditor;
import stroom.document.client.editor.LinkTabPanel;
import stroom.security.client.component.DocumentUserPermissionsTabController;
import stroom.view.shared.ViewDoc;
import stroom.widget.tab.client.TabData;
import stroom.widget.tab.client.TabDataImpl;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

/// A view's editor: its Settings, Documentation and Permissions tabs (see [DocumentEditor]).
public class ViewDocEditor extends DocumentEditor<ViewDoc> {

    private static final TabData SETTINGS = new TabDataImpl("Settings");
    private static final TabData DOCUMENTATION = new TabDataImpl("Documentation");
    private static final TabData PERMISSIONS = new TabDataImpl("Permissions");

    /// Makes the editor, with its tabs.
    ///
    /// @param eventBus             The application's event bus.
    /// @param view                 Its component.
    /// @param settingsProvider     Makes the Settings tab.
    /// @param documentationTabProvider Makes the Documentation tab's editor.
    /// @param permissionsProvider  Makes the Permissions tab.
    @Inject
    public ViewDocEditor(final EventBus eventBus,
                         final LinkTabPanel view,
                         final Provider<ViewSettingsController> settingsProvider,
                         final Provider<DocumentationTabController> documentationTabProvider,
                         final Provider<DocumentUserPermissionsTabController> permissionsProvider) {
        super(eventBus, view);

        addTab(SETTINGS,
                settingsProvider::get,
                ViewSettingsController::read,
                ViewSettingsController::write);
        addDocumentationTab(DOCUMENTATION,
                documentationTabProvider,
                ViewDoc::getDescription,
                (doc, description) -> doc.copy().description(description).build());
        addPermissionsTab(PERMISSIONS, permissionsProvider);
        setDefaultTab(SETTINGS);
    }
}
