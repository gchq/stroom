/*
 * Copyright 2017 Crown Copyright
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
 *
 */

package stroom.dashboard.client.main;

import stroom.dashboard.shared.DashboardDoc;
import stroom.docref.DocRef;
import stroom.entity.client.presenter.ContentCallback;
import stroom.entity.client.presenter.DocumentEditTabPresenter;
import stroom.entity.client.presenter.LinkTabPanelView;
import stroom.entity.client.presenter.TabContentProvider;
import stroom.security.client.api.ClientSecurityContext;
import stroom.widget.tab.client.presenter.TabData;
import stroom.widget.tab.client.presenter.TabDataImpl;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

public class DashboardSuperPresenter extends DocumentEditTabPresenter<LinkTabPanelView, DashboardDoc> {

    private static final TabData DASHBOARD = new TabDataImpl("Dashboard");
    private static final TabData SETTINGS = new TabDataImpl("Settings");

    private final TabContentProvider<DashboardDoc> tabContentProvider = new TabContentProvider<>();

    @Inject
    public DashboardSuperPresenter(final EventBus eventBus,
                                   final LinkTabPanelView view,
                                   final ClientSecurityContext securityContext,
                                   final Provider<DashboardSettingsPresenter> settingsPresenterProvider,
                                   final Provider<DashboardPresenter> dashboardPresenterProvider) {
        super(eventBus, view, securityContext);

        tabContentProvider.setDirtyHandler(event -> {
            if (event.isDirty()) {
                setDirty(true);
            }
        });

        addTab(DASHBOARD);
        tabContentProvider.add(DASHBOARD, dashboardPresenterProvider);

        addTab(SETTINGS);
        tabContentProvider.add(SETTINGS, settingsPresenterProvider);

        selectTab(DASHBOARD);
    }

    @Override
    protected void getContent(final TabData tab, final ContentCallback callback) {
        callback.onReady(tabContentProvider.getPresenter(tab));
    }

    @Override
    public void onRead(final DocRef docRef, final DashboardDoc feed) {
        super.onRead(docRef, feed);
        tabContentProvider.read(docRef, feed);
    }

    @Override
    protected void onWrite(final DashboardDoc feed) {
        tabContentProvider.write(feed);
    }

    @Override
    public void onReadOnly(final boolean readOnly) {
        super.onReadOnly(readOnly);
        tabContentProvider.onReadOnly(readOnly);
    }

    @Override
    public String getType() {
        return DashboardDoc.DOCUMENT_TYPE;
    }
}
