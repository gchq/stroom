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

package stroom.search.elastic.client.presenter;

import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.entity.client.presenter.ContentCallback;
import stroom.entity.client.presenter.DocumentEditTabPresenter;
import stroom.entity.client.presenter.LinkTabPanelView;
import stroom.entity.client.presenter.TabContentProvider;
import stroom.search.elastic.shared.ElasticIndexDoc;
import stroom.security.client.api.ClientSecurityContext;
import stroom.widget.tab.client.presenter.TabData;
import stroom.widget.tab.client.presenter.TabDataImpl;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

public class ElasticIndexPresenter extends DocumentEditTabPresenter<LinkTabPanelView, ElasticIndexDoc> {

    private static final TabData SETTINGS = new TabDataImpl("Settings");
    private static final TabData FIELDS = new TabDataImpl("Fields");

    private final TabContentProvider<ElasticIndexDoc> tabContentProvider = new TabContentProvider<>();

    @Inject
    public ElasticIndexPresenter(
            final EventBus eventBus,
            final LinkTabPanelView view,
            final RestFactory restFactory,
            final Provider<ElasticIndexSettingsPresenter> indexSettingsPresenter,
            final Provider<ElasticIndexFieldListPresenter> indexFieldListPresenter,
            final ClientSecurityContext securityContext
    ) {
        super(eventBus, view, securityContext, restFactory);

        tabContentProvider.setDirtyHandler(event -> {
            if (event.isDirty()) {
                setDirty(true);
            }
        });

        tabContentProvider.add(SETTINGS, indexSettingsPresenter);
        tabContentProvider.add(FIELDS, indexFieldListPresenter);
        addTab(SETTINGS);
        addTab(FIELDS);
        selectTab(SETTINGS);
    }

    @Override
    protected void getContent(final TabData tab, final ContentCallback callback) {
        callback.onReady(tabContentProvider.getPresenter(tab));
    }

    @Override
    public void onRead(final DocRef docRef, final ElasticIndexDoc index) {
        super.onRead(docRef, index);
        tabContentProvider.read(docRef, index);
    }

    @Override
    public void onReadOnly(final boolean readOnly) {
        super.onReadOnly(readOnly);
        tabContentProvider.onReadOnly(readOnly);
    }

    @Override
    protected void onWrite(final ElasticIndexDoc index) {
        tabContentProvider.write(index);
    }

    @Override
    public String getType() {
        return ElasticIndexDoc.DOCUMENT_TYPE;
    }
}
