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

package stroom.script.client.component;

import stroom.dashboard.client.vis.ClearFunctionCacheEvent;
import stroom.dashboard.client.vis.ClearScriptCacheEvent;
import stroom.docref.DocRef;
import stroom.entity.client.component.CodeTabController;
import stroom.entity.client.component.DocumentEditor;
import stroom.entity.client.component.DocumentationTabController;
import stroom.entity.client.component.LinkTabPanel;
import stroom.script.shared.ScriptDoc;
import stroom.security.client.component.DocumentUserPermissionsTabController;
import stroom.widget.tab.client.TabData;
import stroom.widget.tab.client.TabDataImpl;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;
import edu.ycp.cs.dh.acegwt.client.ace.AceEditorMode;

/// A script's editor: its Script (JavaScript), Settings, Documentation and Permissions tabs (see
/// [DocumentEditor]). Once a script is read again (e.g. after it is saved), the dashboards' and
/// visualisations' caches of it are cleared, so they load it again.
public class ScriptEditor extends DocumentEditor<ScriptDoc> {

    private static final TabData SETTINGS = new TabDataImpl("Settings");
    private static final TabData SCRIPT = new TabDataImpl("Script");
    private static final TabData DOCUMENTATION = new TabDataImpl("Documentation");
    private static final TabData PERMISSIONS = new TabDataImpl("Permissions");

    private int readCount;

    /// Makes the editor, with its Script, Settings, Documentation and Permissions tabs.
    ///
    /// @param eventBus             The application's event bus.
    /// @param view                 Its component.
    /// @param settingsProvider     Makes the Settings tab.
    /// @param codeTabProvider      Makes the code tab.
    /// @param documentationTabProvider Makes the Documentation tab's editor.
    /// @param permissionsProvider  Makes the Permissions tab.
    @Inject
    public ScriptEditor(final EventBus eventBus,
                        final LinkTabPanel view,
                        final Provider<ScriptSettingsController> settingsProvider,
                        final Provider<CodeTabController> codeTabProvider,
                        final Provider<DocumentationTabController> documentationTabProvider,
                        final Provider<DocumentUserPermissionsTabController> permissionsProvider) {
        super(eventBus, view);
        addTab(SCRIPT,
                () -> {
                    final CodeTabController script = codeTabProvider.get();
                    script.setMode(AceEditorMode.JAVASCRIPT);
                    script.setOnChange(this::onChange);
                    return script;
                },
                (script, docRef, doc) -> script.setText(doc.getData()),
                (script, doc) -> doc.copy().data(script.getText()).build());
        addTab(SETTINGS,
                settingsProvider::get,
                ScriptSettingsController::read,
                ScriptSettingsController::write);
        addDocumentationTab(DOCUMENTATION,
                documentationTabProvider,
                ScriptDoc::getDescription,
                (doc, description) -> doc.copy().description(description).build());
        addPermissionsTab(PERMISSIONS, permissionsProvider);
        setDefaultTab(SCRIPT);
    }

    @Override
    protected void onRead(final DocRef docRef, final ScriptDoc doc) {
        readCount++;
        if (readCount > 1) {
            // Remove the script function from the cache so dashboards reload it
            ClearScriptCacheEvent.fire(this, docRef);
            // This script might be used by any visualisation so clear the vis function cache so
            // that scripts are requested again if needed
            ClearFunctionCacheEvent.fire(this);
        }
    }
}
