/*
 * Copyright 2016-2025 Crown Copyright
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

import stroom.analytics.shared.AnalyticProcessType;
import stroom.analytics.shared.AnalyticRuleDoc;
import stroom.docref.DocRef;
import stroom.entity.client.presenter.DocumentEditTabPresenter;
import stroom.entity.client.presenter.DocumentEditTabProvider;
import stroom.entity.client.presenter.LinkTabPanelView;
import stroom.entity.client.presenter.MarkdownEditPresenter;
import stroom.entity.client.presenter.MarkdownTabProvider;
import stroom.security.client.presenter.DocumentUserPermissionsTabProvider;
import stroom.widget.tab.client.presenter.TabData;
import stroom.widget.tab.client.presenter.TabDataImpl;
import stroom.widget.util.client.KeyBinding.Action;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import java.util.Objects;
import javax.inject.Provider;

public class AnalyticRulePresenter
        extends DocumentEditTabPresenter<LinkTabPanelView, AnalyticRuleDoc> {

    private static final TabData QUERY = new TabDataImpl("Query");
    private static final TabData NOTIFICATIONS = new TabDataImpl("Notifications");
    private static final TabData EXECUTION = new TabDataImpl("Execution");
    private static final TabData SHARDS = new TabDataImpl("Shards");
    private static final TabData DUPLICATE_MANAGEMENT = new TabDataImpl("Duplicate Management");
    private static final TabData DOCUMENTATION = new TabDataImpl("Documentation");
    private static final TabData PERMISSIONS = new TabDataImpl("Permissions");

    private final AnalyticQueryEditPresenter analyticQueryEditPresenter;

    @Inject
    public AnalyticRulePresenter(final EventBus eventBus,
                                 final LinkTabPanelView view,
                                 final AnalyticQueryEditPresenter analyticQueryEditPresenter,
                                 final Provider<AnalyticNotificationPresenter> notificationPresenterProvider,
                                 final Provider<AnalyticProcessingPresenter> processPresenterProvider,
                                 final Provider<AnalyticDataShardsPresenter> analyticDataShardsPresenterProvider,
                                 final Provider<AnalyticDuplicateManagementPresenter>
                                         duplicateManagementPresenterProvider,
                                 final Provider<MarkdownEditPresenter> markdownEditPresenterProvider,
                                 final DocumentUserPermissionsTabProvider<AnalyticRuleDoc>
                                         documentUserPermissionsTabProvider) {
        super(eventBus, view);
        this.analyticQueryEditPresenter = analyticQueryEditPresenter;

        final AnalyticProcessingPresenter analyticProcessingPresenter = processPresenterProvider.get();
        analyticProcessingPresenter.setDocumentEditPresenter(this);
        analyticProcessingPresenter.addChangeDataHandler(e ->
                setRuleType(analyticProcessingPresenter.getView().getProcessingType()));

        addTab(QUERY, new DocumentEditTabProvider<>(() -> analyticQueryEditPresenter));
        addTab(NOTIFICATIONS, new DocumentEditTabProvider<>(notificationPresenterProvider::get));
        addTab(EXECUTION, new DocumentEditTabProvider<>(() -> analyticProcessingPresenter));
        addTab(SHARDS, new DocumentEditTabProvider<>(analyticDataShardsPresenterProvider::get));
        addTab(DUPLICATE_MANAGEMENT, new DocumentEditTabProvider<>(duplicateManagementPresenterProvider::get));
        addTab(DOCUMENTATION, new MarkdownTabProvider<AnalyticRuleDoc>(eventBus, markdownEditPresenterProvider) {
            @Override
            public void onRead(final MarkdownEditPresenter presenter,
                               final DocRef docRef,
                               final AnalyticRuleDoc document,
                               final boolean readOnly) {
                presenter.setText(document.getDescription());
                presenter.setReadOnly(readOnly);
            }

            @Override
            public AnalyticRuleDoc onWrite(final MarkdownEditPresenter presenter,
                                           final AnalyticRuleDoc document) {
                return document.copy().description(presenter.getText()).build();
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
    protected void onRead(final DocRef docRef, final AnalyticRuleDoc document, final boolean readOnly) {
        super.onRead(docRef, document, readOnly);
        setRuleType(document.getAnalyticProcessType());
    }

    private void setRuleType(final AnalyticProcessType analyticProcessType) {
        setTabHidden(SHARDS, analyticProcessType != AnalyticProcessType.TABLE_BUILDER);
        setTabHidden(DUPLICATE_MANAGEMENT, analyticProcessType != AnalyticProcessType.SCHEDULED_QUERY);
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
