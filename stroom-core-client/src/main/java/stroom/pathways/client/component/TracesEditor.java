/*
 * Copyright 2025 Crown Copyright
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

import stroom.docref.DocRef;
import stroom.entity.client.component.DocumentEditor;
import stroom.entity.client.component.LinkTabPanel;
import stroom.entity.client.component.MarkdownEditController;
import stroom.pathways.shared.TracesDoc;
import stroom.pathways.shared.pathway.Pathway;
import stroom.planb.client.component.PlanBSettingsController;
import stroom.planb.shared.StateType;
import stroom.security.client.component.DocumentUserPermissionsTabController;
import stroom.widget.tab.client.TabData;
import stroom.widget.tab.client.TabDataImpl;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

/// A traces store's editor: its Traces (the traces found), Settings (Plan B's, for traces only),
/// Documentation and Permissions tabs (see [DocumentEditor]). It is also opened on its own to show
/// the traces of a pathway ([#setDataSourceRef], [#setPathway], [#setFilter], [#forceRefresh]).
public class TracesEditor extends DocumentEditor<TracesDoc> {

    public static final String TAB_TYPE = "Traces";

    private static final TabData TRACES = new TabDataImpl("Traces");
    private static final TabData SETTINGS = new TabDataImpl("Settings");
    private static final TabData DOCUMENTATION = new TabDataImpl("Documentation");
    private static final TabData PERMISSIONS = new TabDataImpl("Permissions");

    private final TracesListTabController tracesListTab;

    /// Makes the editor, with its Traces, Settings, Documentation and Permissions tabs.
    @Inject
    public TracesEditor(final EventBus eventBus,
                        final LinkTabPanel view,
                        final TracesListTabController tracesListTab,
                        final Provider<PlanBSettingsController> planBSettingsProvider,
                        final Provider<MarkdownEditController> markdownEditProvider,
                        final Provider<DocumentUserPermissionsTabController> permissionsProvider) {
        super(eventBus, view);
        this.tracesListTab = own(tracesListTab);
        addOwnedTab(TRACES, tracesListTab, TracesListTabController::read);
        // Plan B's settings, for traces only
        addTab(SETTINGS,
                () -> {
                    final PlanBSettingsController planBSettings = planBSettingsProvider.get();
                    planBSettings.setStateTypeLocked(true);
                    return planBSettings;
                },
                (planBSettings, docRef, doc) -> {
                    planBSettings.readSettings(doc.getSettings(), StateType.TRACE);
                    planBSettings.setSharedFileStoreLocked(doc.hasSharedFileStoreData());
                },
                (planBSettings, doc) -> doc.copyTraces()
                        .stateType(StateType.TRACE)
                        .settings(planBSettings.writeSettings())
                        .build());
        addDocumentationTab(DOCUMENTATION,
                markdownEditProvider,
                TracesDoc::getDescription,
                (doc, description) -> doc.copyTraces().description(description).build());
        addPermissionsTab(PERMISSIONS, permissionsProvider);
        setDefaultTab(TRACES);
    }

    @Override
    public String getTabLabel() {
        // Opened on its own (e.g. a pathway's traces) there is no store to name it after
        return getDocRef() == null
                ? "Traces"
                : null;
    }

    /// Finds the traces again.
    public void forceRefresh() {
        tracesListTab.refresh();
    }

    /// @param dataSourceRef The store to find the traces in.
    public void setDataSourceRef(final DocRef dataSourceRef) {
        tracesListTab.setDataSourceRef(dataSourceRef);
    }

    /// @param filter What the traces found must match, or null for all.
    public void setFilter(final String filter) {
        tracesListTab.setFilter(filter);
    }

    /// @param pathway The pathway the traces found must follow, or null for any.
    public void setPathway(final Pathway pathway) {
        tracesListTab.setPathway(pathway);
    }
}
