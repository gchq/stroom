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

package stroom.datagen.client.component;

import stroom.analytics.client.component.ScheduledProcessingController;
import stroom.datagen.shared.DataGenDoc;
import stroom.entity.client.component.DocumentEditor;
import stroom.entity.client.component.LinkTabPanel;
import stroom.entity.client.component.MarkdownEditController;
import stroom.security.client.component.DocumentUserPermissionsTabController;
import stroom.widget.tab.client.TabData;
import stroom.widget.tab.client.TabDataImpl;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

/// A data generator's editor: its Settings, Execution (its schedules, which are saved straight away,
/// not with the document), Documentation and Permissions tabs (see [DocumentEditor]).
public class DataGenEditor extends DocumentEditor<DataGenDoc> {

    private static final TabData SETTINGS = new TabDataImpl("Settings");
    private static final TabData EXECUTION = new TabDataImpl("Execution");
    private static final TabData DOCUMENTATION = new TabDataImpl("Documentation");
    private static final TabData PERMISSIONS = new TabDataImpl("Permissions");

    /// Makes the editor, with its Settings, Execution, Documentation and Permissions tabs.
    @Inject
    public DataGenEditor(final EventBus eventBus,
                         final LinkTabPanel view,
                         final Provider<DataGenSettingsController> dataGenSettingsProvider,
                         final Provider<ScheduledProcessingController> scheduledProcessingProvider,
                         final Provider<MarkdownEditController> markdownEditProvider,
                         final Provider<DocumentUserPermissionsTabController> permissionsProvider) {
        super(eventBus, view);
        addTab(SETTINGS,
                dataGenSettingsProvider::get,
                DataGenSettingsController::read,
                DataGenSettingsController::write);
        addTab(EXECUTION,
                () -> {
                    final ScheduledProcessingController scheduledProcessing = scheduledProcessingProvider.get();
                    scheduledProcessing.setIsDocumentDirty(this::isDirty);
                    return scheduledProcessing;
                },
                (scheduledProcessing, docRef, doc) -> scheduledProcessing.read(docRef));
        addDocumentationTab(DOCUMENTATION,
                markdownEditProvider,
                DataGenDoc::getDescription,
                (doc, description) -> doc.copy().description(description).build());
        addPermissionsTab(PERMISSIONS, permissionsProvider);
        setDefaultTab(SETTINGS);
    }
}
