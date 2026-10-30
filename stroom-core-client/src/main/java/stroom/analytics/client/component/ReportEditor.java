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

package stroom.analytics.client.component;

import stroom.analytics.shared.AnalyticProcessType;
import stroom.analytics.shared.QueryLanguageVersion;
import stroom.analytics.shared.ReportDoc;
import stroom.docref.DocRef;
import stroom.document.client.component.DocumentationTabController;
import stroom.document.client.editor.DocumentEditor;
import stroom.document.client.editor.LinkTabPanel;
import stroom.security.client.component.DocumentUserPermissionsTabController;
import stroom.widget.tab.client.TabData;
import stroom.widget.tab.client.TabDataImpl;
import stroom.widget.util.client.KeyBinding.Action;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

/// A report's editor: its Query, Settings, Notifications, Execution, Documentation and Permissions
/// tabs (see [DocumentEditor]). With the Query tab showing, OK runs the query and Close stops it.
public class ReportEditor extends DocumentEditor<ReportDoc> {

    private static final TabData QUERY = new TabDataImpl("Query");
    private static final TabData SETTINGS = new TabDataImpl("Settings");
    private static final TabData NOTIFICATIONS = new TabDataImpl("Notifications");
    private static final TabData EXECUTION = new TabDataImpl("Execution");
    private static final TabData DOCUMENTATION = new TabDataImpl("Documentation");
    private static final TabData PERMISSIONS = new TabDataImpl("Permissions");

    private final ReportQueryEditorController reportQueryEdit;
    private final NotificationListController notificationList;

    /// Makes the editor, with its Query, Settings, Notifications, Execution, Documentation and
    /// Permissions tabs.
    ///
    /// @param eventBus                   The application's event bus.
    /// @param view                       Its component.
    /// @param reportQueryEdit            The report query edit.
    /// @param reportSettingsProvider     Makes the report settings.
    /// @param notificationListProvider   Makes the notification list.
    /// @param analyticProcessingProvider Makes the analytic processing.
    /// @param documentationTabProvider       Makes the Documentation tab's editor.
    /// @param permissionsProvider        Makes the Permissions tab.
    @Inject
    public ReportEditor(final EventBus eventBus,
                        final LinkTabPanel view,
                        final ReportQueryEditorController reportQueryEdit,
                        final Provider<ReportSettingsTabController> reportSettingsProvider,
                        final Provider<NotificationListController> notificationListProvider,
                        final Provider<AnalyticProcessingController> analyticProcessingProvider,
                        final Provider<DocumentationTabController> documentationTabProvider,
                        final Provider<DocumentUserPermissionsTabController> permissionsProvider) {
        super(eventBus, view);
        this.reportQueryEdit = own(reportQueryEdit);
        final AnalyticProcessingController analyticProcessing = make(analyticProcessingProvider);
        analyticProcessing.setProcessingTypes(AnalyticProcessType.SCHEDULED_QUERY);
        analyticProcessing.setIsDocumentDirty(this::isDirty);
        // Made up front rather than by its tab, so it can be told the processing type whether or not
        // the tab has been opened
        this.notificationList = make(notificationListProvider);

        addOwnedTab(QUERY, reportQueryEdit, ReportQueryEditorController::read, ReportQueryEditorController::write);
        addTab(SETTINGS,
                reportSettingsProvider::get,
                ReportSettingsTabController::read,
                ReportSettingsTabController::write);
        addOwnedTab(NOTIFICATIONS,
                notificationList,
                (list, docRef, doc) -> list.read(docRef, doc.getNotifications()),
                (list, doc) -> doc
                        .copy()
                        .notifications(list.getNotifications())
                        .languageVersion(QueryLanguageVersion.STROOM_QL_VERSION_0_1)
                        .build());
        addOwnedTab(EXECUTION,
                analyticProcessing,
                AnalyticProcessingController::read,
                (processing, doc) -> doc
                        .copy()
                        .languageVersion(QueryLanguageVersion.STROOM_QL_VERSION_0_1)
                        .analyticProcessType(processing.getProcessingType())
                        .analyticProcessConfig(processing.writeProcessConfig())
                        .build());
        addDocumentationTab(DOCUMENTATION,
                documentationTabProvider,
                ReportDoc::getDescription,
                (doc, description) -> doc.copy().description(description).build());
        addPermissionsTab(PERMISSIONS, permissionsProvider);
        setDefaultTab(QUERY);
    }

    @Override
    public boolean handleKeyAction(final Action action) {
        if (Action.OK == action && QUERY.equals(getSelectedTab())) {
            reportQueryEdit.start();
            return true;
        } else if (Action.CLOSE == action && QUERY.equals(getSelectedTab())) {
            reportQueryEdit.stop();
            return true;
        } else {
            return false;
        }
    }

    @Override
    protected void onRead(final DocRef docRef, final ReportDoc doc) {
        // A report is always a scheduled query, but tell the notifications tab explicitly rather
        // than leaving it to infer anything
        notificationList.setAnalyticProcessType(doc.getAnalyticProcessType());
    }
}
