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
import stroom.analytics.shared.AnalyticRuleDoc;
import stroom.analytics.shared.QueryLanguageVersion;
import stroom.docref.DocRef;
import stroom.entity.client.component.DocumentEditor;
import stroom.entity.client.component.DocumentationTabController;
import stroom.entity.client.component.LinkTabPanel;
import stroom.security.client.component.DocumentUserPermissionsTabController;
import stroom.widget.tab.client.TabData;
import stroom.widget.tab.client.TabDataImpl;
import stroom.widget.util.client.KeyBinding.Action;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

/// An analytic rule's editor: its Query, Settings, Notifications, Execution, Shards (for a table
/// builder), Duplicate Management (for a scheduled query), Documentation and Permissions tabs (see
/// [DocumentEditor]). With the Query tab showing, OK runs the query and Close stops it.
public class AnalyticRuleEditor extends DocumentEditor<AnalyticRuleDoc> {

    private static final TabData QUERY = new TabDataImpl("Query");
    private static final TabData SETTINGS = new TabDataImpl("Settings");
    private static final TabData NOTIFICATIONS = new TabDataImpl("Notifications");
    private static final TabData EXECUTION = new TabDataImpl("Execution");
    private static final TabData SHARDS = new TabDataImpl("Shards");
    private static final TabData DUPLICATE_MANAGEMENT = new TabDataImpl("Duplicate Management");
    private static final TabData DOCUMENTATION = new TabDataImpl("Documentation");
    private static final TabData PERMISSIONS = new TabDataImpl("Permissions");

    private final AnalyticQueryEditorController analyticQueryEdit;
    private final NotificationListController notificationList;

    /// Makes the editor, with its tabs.
    ///
    /// @param eventBus                    The application's event bus.
    /// @param view                        Its component.
    /// @param analyticQueryEdit           The analytic query edit.
    /// @param settingsProvider            Makes the Settings tab.
    /// @param notificationListProvider    Makes the notification list.
    /// @param analyticProcessingProvider  Makes the analytic processing.
    /// @param analyticDataShardsProvider  Makes the analytic data shards.
    /// @param duplicateManagementProvider Makes the duplicate management.
    /// @param documentationTabProvider        Makes the Documentation tab's editor.
    /// @param permissionsProvider         Makes the Permissions tab.
    @Inject
    public AnalyticRuleEditor(final EventBus eventBus,
                              final LinkTabPanel view,
                              final AnalyticQueryEditorController analyticQueryEdit,
                              final Provider<AnalyticSettingsController> settingsProvider,
                              final Provider<NotificationListController> notificationListProvider,
                              final Provider<AnalyticProcessingController> analyticProcessingProvider,
                              final Provider<AnalyticDataShardsController> analyticDataShardsProvider,
                              final Provider<DuplicateManagementController> duplicateManagementProvider,
                              final Provider<DocumentationTabController> documentationTabProvider,
                              final Provider<DocumentUserPermissionsTabController> permissionsProvider) {
        super(eventBus, view);
        this.analyticQueryEdit = own(analyticQueryEdit);
        // Made up front rather than by its tab, as the notifications tab has to be told when the
        // processing type changes on the execution tab, which can happen before the tab is ever
        // opened. Made before the change callback below is set, as that reads it.
        this.notificationList = make(notificationListProvider);
        final AnalyticProcessingController analyticProcessing = make(analyticProcessingProvider);
        analyticProcessing.setProcessingTypes(AnalyticProcessType.SCHEDULED_QUERY,
                AnalyticProcessType.STREAMING,
                AnalyticProcessType.TABLE_BUILDER);
        analyticProcessing.setIsDocumentDirty(this::isDirty);
        analyticProcessing.setOnProcessingTypeChange(this::setRuleType);

        addOwnedTab(QUERY,
                analyticQueryEdit,
                AnalyticQueryEditorController::read,
                AnalyticQueryEditorController::write);
        addTab(SETTINGS,
                settingsProvider::get,
                AnalyticSettingsController::read,
                AnalyticSettingsController::write);
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
        addTab(SHARDS,
                analyticDataShardsProvider::get,
                AnalyticDataShardsController::read);
        addTab(DUPLICATE_MANAGEMENT,
                duplicateManagementProvider::get,
                DuplicateManagementController::read,
                DuplicateManagementController::write);
        addDocumentationTab(DOCUMENTATION,
                documentationTabProvider,
                AnalyticRuleDoc::getDescription,
                (doc, description) -> doc.copy().description(description).build());
        addPermissionsTab(PERMISSIONS, permissionsProvider);
        setDefaultTab(QUERY);
    }

    @Override
    public boolean handleKeyAction(final Action action) {
        if (Action.OK == action && QUERY.equals(getSelectedTab())) {
            analyticQueryEdit.start();
            return true;
        } else if (Action.CLOSE == action && QUERY.equals(getSelectedTab())) {
            analyticQueryEdit.stop();
            return true;
        } else {
            return false;
        }
    }

    @Override
    protected void onRead(final DocRef docRef, final AnalyticRuleDoc doc) {
        setRuleType(doc.getAnalyticProcessType());
    }

    private void setRuleType(final AnalyticProcessType analyticProcessType) {
        setTabHidden(SHARDS, analyticProcessType != AnalyticProcessType.TABLE_BUILDER);
        setTabHidden(DUPLICATE_MANAGEMENT, analyticProcessType != AnalyticProcessType.SCHEDULED_QUERY);
        // Some notification settings only apply to one processing type, so the tab has to know
        // about a change made on the execution tab
        notificationList.setAnalyticProcessType(analyticProcessType);
    }
}
