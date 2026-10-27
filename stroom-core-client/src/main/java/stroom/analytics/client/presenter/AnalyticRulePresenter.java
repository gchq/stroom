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
import stroom.analytics.client.component.AnalyticSettingsController;
import stroom.analytics.client.component.DuplicateManagementController;
import stroom.analytics.client.component.NotificationListController;
import stroom.analytics.shared.AnalyticProcessType;
import stroom.analytics.shared.AnalyticRuleDoc;
import stroom.analytics.shared.QueryLanguageVersion;
import stroom.docref.DocRef;
import stroom.entity.client.component.MarkdownEditController;
import stroom.entity.client.presenter.ControllerTabProvider;
import stroom.entity.client.presenter.DocTabPresenter;
import stroom.entity.client.presenter.DocTabProvider;
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

public class AnalyticRulePresenter
        extends DocTabPresenter<LinkTabPanelView, AnalyticRuleDoc> {

    private static final TabData QUERY = new TabDataImpl("Query");
    private static final TabData SETTINGS = new TabDataImpl("Settings");
    private static final TabData NOTIFICATIONS = new TabDataImpl("Notifications");
    private static final TabData EXECUTION = new TabDataImpl("Execution");
    private static final TabData SHARDS = new TabDataImpl("Shards");
    private static final TabData DUPLICATE_MANAGEMENT = new TabDataImpl("Duplicate Management");
    private static final TabData DOCUMENTATION = new TabDataImpl("Documentation");
    private static final TabData PERMISSIONS = new TabDataImpl("Permissions");

    private final AnalyticQueryEditPresenter analyticQueryEditPresenter;
    private final NotificationListController notificationList;

    @Inject
    public AnalyticRulePresenter(final EventBus eventBus,
                                 final LinkTabPanelView view,
                                 final AnalyticQueryEditPresenter analyticQueryEditPresenter,
                                 final Provider<AnalyticSettingsController> settingsProvider,
                                 final Provider<NotificationListController> notificationListProvider,
                                 final Provider<AnalyticProcessingController> analyticProcessingProvider,
                                 final Provider<AnalyticDataShardsPresenter> analyticDataShardsPresenterProvider,
                                 final Provider<DuplicateManagementController> duplicateManagementProvider,
                                 final Provider<MarkdownEditController> markdownEditProvider,
                                 final DocumentUserPermissionsTabProvider<AnalyticRuleDoc>
                                         documentUserPermissionsTabProvider) {
        super(eventBus, view);
        own(analyticQueryEditPresenter);
        this.analyticQueryEditPresenter = analyticQueryEditPresenter;

        // Created up front rather than by the tab provider, as the notifications tab has to be told when the
        // processing type changes on the execution tab, which can happen before the tab is ever opened.
        // Assigned before the change handler below is registered, as that handler reads it.
        this.notificationList = make(notificationListProvider);

        final AnalyticProcessingController analyticProcessing = make(analyticProcessingProvider);
        analyticProcessing.setProcessingTypes(AnalyticProcessType.SCHEDULED_QUERY,
                AnalyticProcessType.STREAMING,
                AnalyticProcessType.TABLE_BUILDER);
        analyticProcessing.setIsDocumentDirty(this::isDirty);
        analyticProcessing.setOnProcessingTypeChange(this::setRuleType);

        addTab(QUERY, new DocTabProvider<>(() -> analyticQueryEditPresenter));
        addTab(SETTINGS, new ControllerTabProvider<>(eventBus, settingsProvider::get,
                AnalyticSettingsController::read,
                AnalyticSettingsController::write));
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
        addTab(SHARDS, new DocTabProvider<>(analyticDataShardsPresenterProvider::get));
        addTab(DUPLICATE_MANAGEMENT, new ControllerTabProvider<>(eventBus, duplicateManagementProvider::get,
                DuplicateManagementController::read,
                DuplicateManagementController::write));
        addTab(DOCUMENTATION, new MarkdownTabProvider<AnalyticRuleDoc>(getEventBus(), markdownEditProvider::get) {
            @Override
            public void onRead(final MarkdownEditController markdownEdit,
                               final DocRef docRef,
                               final AnalyticRuleDoc document) {
                markdownEdit.setText(document.getDescription());
                markdownEdit.setReadOnly(isReadOnly());
            }

            @Override
            public AnalyticRuleDoc onWrite(final MarkdownEditController markdownEdit,
                                           final AnalyticRuleDoc document) {
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
            analyticQueryEditPresenter.start();
            return true;
        } else if (Action.CLOSE == action
                   && Objects.equals(getSelectedTab().getType(), QUERY.getType())) {
            analyticQueryEditPresenter.stop();
            return true;
        } else {
            return false;
        }
    }

    @Override
    protected void onRead(final DocRef docRef, final AnalyticRuleDoc document) {
        super.onRead(docRef, document);
        setRuleType(document.getAnalyticProcessType());
    }

    private void setRuleType(final AnalyticProcessType analyticProcessType) {
        setTabHidden(SHARDS, analyticProcessType != AnalyticProcessType.TABLE_BUILDER);
        setTabHidden(DUPLICATE_MANAGEMENT, analyticProcessType != AnalyticProcessType.SCHEDULED_QUERY);
        // Some notification settings only apply to one processing type, so the tab has to know about a change
        // made on the execution tab.
        notificationList.setAnalyticProcessType(analyticProcessType);
    }

    @Override
    public String getType() {
        return AnalyticRuleDoc.TYPE;
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
