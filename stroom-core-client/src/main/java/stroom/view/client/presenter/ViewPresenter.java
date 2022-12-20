/*
 * Copyright 2022 Crown Copyright
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

package stroom.view.client.presenter;

import stroom.docref.DocRef;
import stroom.entity.client.presenter.ContentCallback;
import stroom.entity.client.presenter.DocumentEditTabPresenter;
import stroom.entity.client.presenter.LinkTabPanelView;
import stroom.entity.client.presenter.TabContentProvider;
import stroom.security.client.api.ClientSecurityContext;
import stroom.view.shared.ViewDoc;
import stroom.widget.tab.client.presenter.TabData;
import stroom.widget.tab.client.presenter.TabDataImpl;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

public class ViewPresenter extends DocumentEditTabPresenter<LinkTabPanelView, ViewDoc> {

    private static final TabData SETTINGS = new TabDataImpl("Settings");

    private final TabContentProvider<ViewDoc> tabContentProvider = new TabContentProvider<>();

    @Inject
    public ViewPresenter(final EventBus eventBus,
                         final LinkTabPanelView view,
                         final ClientSecurityContext securityContext,
                         final Provider<ViewSettingsPresenter> settingsPresenterProvider) {
        super(eventBus, view, securityContext);

        tabContentProvider.setDirtyHandler(event -> {
            if (event.isDirty()) {
                setDirty(true);
            }
        });

        TabData selectedTab = SETTINGS;

        addTab(SETTINGS);
        tabContentProvider.add(SETTINGS, settingsPresenterProvider);

        selectTab(selectedTab);
    }

    @Override
    protected void getContent(final TabData tab, final ContentCallback callback) {
        callback.onReady(tabContentProvider.getPresenter(tab));
    }

    @Override
    public void onRead(final DocRef docRef, final ViewDoc view) {
        super.onRead(docRef, view);
        tabContentProvider.read(docRef, view);
    }

    @Override
    protected void onWrite(final ViewDoc view) {
        tabContentProvider.write(view);
    }

    @Override
    public void onReadOnly(final boolean readOnly) {
        super.onReadOnly(readOnly);
        tabContentProvider.onReadOnly(readOnly);
    }

    @Override
    public String getType() {
        return ViewDoc.DOCUMENT_TYPE;
    }
}
