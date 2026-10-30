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

package stroom.planb.client.component;

import stroom.document.client.component.DocumentationTabController;
import stroom.document.client.editor.DocumentEditor;
import stroom.document.client.editor.LinkTabPanel;
import stroom.planb.shared.PlanBDoc;
import stroom.security.client.component.DocumentUserPermissionsTabController;
import stroom.widget.tab.client.TabData;
import stroom.widget.tab.client.TabDataImpl;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

/// A Plan B store's editor: its Settings, Data, Documentation and Permissions tabs (see
/// [DocumentEditor]). The Data tab is second, after Settings, which stays the tab a store opens
/// on: the Data tab is for inspecting what a store holds, not the first thing a user wants on
/// opening one.
public class PlanBEditor extends DocumentEditor<PlanBDoc> {

    private static final TabData SETTINGS = new TabDataImpl("Settings");
    private static final TabData DATA = new TabDataImpl("Data");
    private static final TabData DOCUMENTATION = new TabDataImpl("Documentation");
    private static final TabData PERMISSIONS = new TabDataImpl("Permissions");

    /// Makes the editor, with its tabs.
    ///
    /// @param eventBus              The application's event bus.
    /// @param view                  Its component.
    /// @param planBDataProvider     Makes the plan bdata.
    /// @param planBSettingsProvider Makes the plan bsettings.
    /// @param documentationTabProvider  Makes the Documentation tab's editor.
    /// @param permissionsProvider   Makes the Permissions tab.
    @Inject
    public PlanBEditor(
            final EventBus eventBus,
            final LinkTabPanel view,
            final Provider<PlanBDataController> planBDataProvider,
            final Provider<PlanBSettingsController> planBSettingsProvider,
            final Provider<DocumentationTabController> documentationTabProvider,
            final Provider<DocumentUserPermissionsTabController> permissionsProvider) {
        super(eventBus, view);

        addTab(SETTINGS,
                planBSettingsProvider::get,
                PlanBSettingsController::read,
                PlanBSettingsController::write);
        addTab(DATA,
                planBDataProvider::get,
                PlanBDataController::read);
        addDocumentationTab(DOCUMENTATION,
                documentationTabProvider,
                PlanBDoc::getDescription,
                (doc, description) -> doc.copy().description(description).build());
        addPermissionsTab(PERMISSIONS, permissionsProvider);
        setDefaultTab(SETTINGS);
    }
}
