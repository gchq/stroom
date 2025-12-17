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

import stroom.analytics.shared.ReportDoc;
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

public class ReportPresenter
        extends DocumentEditTabPresenter<LinkTabPanelView, ReportDoc> {

    private static final TabData QUERY = new TabDataImpl("Query");
    private static final TabData SETTINGS = new TabDataImpl("Settings");
    private static final TabData NOTIFICATIONS = new TabDataImpl("Notifications");
    private static final TabData EXECUTION = new TabDataImpl("Execution");
    private static final TabData DOCUMENTATION = new TabDataImpl("Documentation");
    private static final TabData PERMISSIONS = new TabDataImpl("Permissions");

    private final ReportQueryEditPresenter reportQueryEditPresenter;

    @Inject
    public ReportPresenter(final EventBus eventBus,
                           final LinkTabPanelView view,
                           final ReportQueryEditPresenter reportQueryEditPresenter,
                           final Provider<ReportSettingsPresenter> reportSettingsPresenterProvider,
                           final Provider<ReportNotificationListPresenter> notificationPresenterProvider,
                           final Provider<ReportProcessingPresenter> processPresenterProvider,
                           final Provider<MarkdownEditPresenter> markdownEditPresenterProvider,
                           final DocumentUserPermissionsTabProvider<ReportDoc>
                                   documentUserPermissionsTabProvider) {
        super(eventBus, view);
        this.reportQueryEditPresenter = reportQueryEditPresenter;

        final ReportProcessingPresenter analyticProcessingPresenter = processPresenterProvider.get();
        analyticProcessingPresenter.setDocumentEditPresenter(this);

        addTab(QUERY, new DocumentEditTabProvider<>(() -> reportQueryEditPresenter));
        addTab(SETTINGS, new DocumentEditTabProvider<>(reportSettingsPresenterProvider::get));
        addTab(NOTIFICATIONS, new DocumentEditTabProvider<>(notificationPresenterProvider::get));
        addTab(EXECUTION, new DocumentEditTabProvider<>(() -> analyticProcessingPresenter));
        addTab(DOCUMENTATION, new MarkdownTabProvider<ReportDoc>(eventBus, markdownEditPresenterProvider) {
            @Override
            public void onRead(final MarkdownEditPresenter presenter,
                               final DocRef docRef,
                               final ReportDoc document,
                               final boolean readOnly) {
                presenter.setText(document.getDescription());
                presenter.setReadOnly(readOnly);
            }

            @Override
            public ReportDoc onWrite(final MarkdownEditPresenter presenter,
                                     final ReportDoc document) {
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
            reportQueryEditPresenter.start();
            return true;
        } else if (Action.CLOSE == action
                   && Objects.equals(getSelectedTab().getType(), QUERY.getType())) {
            reportQueryEditPresenter.stop();
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
    protected TabData getPermissionsTab() {
        return PERMISSIONS;
    }

    @Override
    protected TabData getDocumentationTab() {
        return DOCUMENTATION;
    }
}
