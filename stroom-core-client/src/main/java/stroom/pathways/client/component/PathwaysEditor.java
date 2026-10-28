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

package stroom.pathways.client.component;

import stroom.entity.client.component.DocumentEditor;
import stroom.entity.client.component.LinkTabPanel;
import stroom.entity.client.component.MarkdownEditController;
import stroom.pathways.shared.PathwaysDoc;
import stroom.security.client.component.DocumentUserPermissionsTabController;
import stroom.widget.tab.client.TabData;
import stroom.widget.tab.client.TabDataImpl;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

/// A pathways document's editor: its Pathways, Settings, Documentation and Permissions tabs
/// (see [DocumentEditor]).
public class PathwaysEditor extends DocumentEditor<PathwaysDoc> {

    private static final TabData SETTINGS = new TabDataImpl("Settings");
    private static final TabData PATHWAYS = new TabDataImpl("Pathways");
    private static final TabData DOCUMENTATION = new TabDataImpl("Documentation");
    private static final TabData PERMISSIONS = new TabDataImpl("Permissions");

    /// Makes the editor, with its tabs.
    @Inject
    public PathwaysEditor(final EventBus eventBus,
                          final LinkTabPanel view,
                          final Provider<PathwaysSettingsController> pathwaysSettingsProvider,
                          final Provider<PathwaysSplitController> pathwaysSplitProvider,
                          final Provider<MarkdownEditController> markdownEditProvider,
                          final Provider<DocumentUserPermissionsTabController> permissionsProvider) {
        super(eventBus, view);

        addTab(PATHWAYS,
                pathwaysSplitProvider::get,
                PathwaysSplitController::read);
        addTab(SETTINGS,
                pathwaysSettingsProvider::get,
                PathwaysSettingsController::read,
                PathwaysSettingsController::write);
        addDocumentationTab(DOCUMENTATION,
                markdownEditProvider,
                PathwaysDoc::getDescription,
                (doc, description) -> doc.copy().description(description).build());
        addPermissionsTab(PERMISSIONS, permissionsProvider);
        setDefaultTab(PATHWAYS);
    }
}
