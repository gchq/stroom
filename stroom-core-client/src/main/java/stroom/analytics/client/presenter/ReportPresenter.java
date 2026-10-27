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

package stroom.analytics.client.presenter;

import stroom.analytics.client.component.AnalyticProcessingController;
import stroom.analytics.client.component.NotificationListController;
import stroom.analytics.client.component.ReportQueryEditController;
import stroom.analytics.client.component.ReportSettingsTabController;
import stroom.analytics.shared.AnalyticProcessType;
import stroom.analytics.shared.QueryLanguageVersion;
import stroom.analytics.shared.ReportDoc;
import stroom.docref.DocRef;
import stroom.entity.client.component.MarkdownEditController;
import stroom.entity.client.presenter.ControllerTabProvider;
import stroom.entity.client.presenter.DocTabPresenter;
import stroom.entity.client.presenter.LinkTabPanelView;
import stroom.entity.client.presenter.MarkdownTabProvider;
import stroom.security.client.presenter.DocumentUserPermissionsTabProvider;
import stroom.widget.tab.client.presenter.TabData;
import stroom.widget.tab.client.presenter.TabDataImpl;
import stroom.widget.util.client.KeyBinding.Action;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

import java.util.Objects;

public class ReportPresenter
        extends DocTabPresenter<LinkTabPanelView, ReportDoc> {

    private static final TabData QUERY = new TabDataImpl("Query");
    private static final TabData SETTINGS = new TabDataImpl("Settings");
    private static final TabData NOTIFICATIONS = new TabDataImpl("Notifications");
    private static final TabData EXECUTION = new TabDataImpl("Execution");
    private static final TabData DOCUMENTATION = new TabDataImpl("Documentation");
    private static final TabData PERMISSIONS = new TabDataImpl("Permissions");

    private final ReportQueryEditController reportQueryEdit;
    private final NotificationListController notificationList;

    @Inject
    public ReportPresenter(final EventBus eventBus,
                           final LinkTabPanelView view,
                           final ReportQueryEditController reportQueryEdit,
                           final Provider<ReportSettingsTabController> reportSettingsProvider,
                           final Provider<NotificationListController> notificationListProvider,
                           final Provider<AnalyticProcessingController> analyticProcessingProvider,
                           final Provider<MarkdownEditController> markdownEditProvider,
                           final DocumentUserPermissionsTabProvider<ReportDoc>
                                   documentUserPermissionsTabProvider) {
        super(eventBus, view);
        own(reportQueryEdit);
        this.reportQueryEdit = reportQueryEdit;

        final AnalyticProcessingController analyticProcessing = make(analyticProcessingProvider);
        analyticProcessing.setProcessingTypes(AnalyticProcessType.SCHEDULED_QUERY);
        analyticProcessing.setIsDocumentDirty(this::isDirty);

        // Created up front rather than by the tab provider, so it can be told the processing type whether or
        // not the tab has been opened.
        this.notificationList = make(notificationListProvider);

        addTab(QUERY, ControllerTabProvider.ofOwned(eventBus, reportQueryEdit,
                ReportQueryEditController::read,
                ReportQueryEditController::write));
        addTab(SETTINGS, new ControllerTabProvider<>(eventBus, reportSettingsProvider::get,
                ReportSettingsTabController::read,
                ReportSettingsTabController::write));
        addTab(NOTIFICATIONS, ControllerTabProvider.ofOwned(eventBus, notificationList,
                (list, docRef, document) -> list.read(docRef, document.getNotifications()),
                (list, document) -> document
                        .copy()
                        .notifications(list.getNotifications())
                        .languageVersion(QueryLanguageVersion.STROOM_QL_VERSION_0_1)
                        .build()));
        addTab(EXECUTION, ControllerTabProvider.ofOwned(eventBus, analyticProcessing,
                AnalyticProcessingController::read,
                (processing, document) -> document
                        .copy()
                        .languageVersion(QueryLanguageVersion.STROOM_QL_VERSION_0_1)
                        .analyticProcessType(processing.getProcessingType())
                        .analyticProcessConfig(processing.writeProcessConfig())
                        .build()));
        addTab(DOCUMENTATION, new MarkdownTabProvider<ReportDoc>(getEventBus(), markdownEditProvider::get) {
            @Override
            public void onRead(final MarkdownEditController markdownEdit,
                               final DocRef docRef,
                               final ReportDoc document) {
                markdownEdit.setText(document.getDescription());
                markdownEdit.setReadOnly(isReadOnly());
            }

            @Override
            public ReportDoc onWrite(final MarkdownEditController markdownEdit,
                                     final ReportDoc document) {
                return document.copy().description(markdownEdit.getText()).build();
            }
        });
        addTab(PERMISSIONS, documentUserPermissionsTabProvider);
        selectTab(QUERY);
    }

    @Override
    public boolean handleKeyAction(final Action action) {
        if (Action.OK == action
            && Objects.equals(getSelectedTab().getType(), QUERY.getType())) {
            reportQueryEdit.start();
            return true;
        } else if (Action.CLOSE == action
                   && Objects.equals(getSelectedTab().getType(), QUERY.getType())) {
            reportQueryEdit.stop();
            return true;
        } else {
            return false;
        }
    }

    @Override
    public String getType() {
        return ReportDoc.TYPE;
    }

    @Override
    protected void onRead(final DocRef docRef, final ReportDoc document) {
        super.onRead(docRef, document);
        // A report is always a scheduled query, but tell the notifications tab explicitly rather than leaving
        // it to infer anything.
        notificationList.setAnalyticProcessType(document.getAnalyticProcessType());
    }

    @Override
    protected TabData getPermissionsTab() {
        return PERMISSIONS;
    }

    @Override
    protected TabData getDocumentationTab() {
        return DOCUMENTATION;
    }
}
