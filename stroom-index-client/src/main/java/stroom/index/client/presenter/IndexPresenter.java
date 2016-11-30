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

package stroom.index.client.presenter;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;
import stroom.entity.client.event.DirtyEvent;
import stroom.entity.client.event.DirtyEvent.DirtyHandler;
import stroom.entity.client.presenter.ContentCallback;
import stroom.entity.client.presenter.EntityEditTabPresenter;
import stroom.entity.client.presenter.LinkTabPanelView;
import stroom.entity.client.presenter.TabContentProvider;
import stroom.index.shared.Index;
import stroom.security.client.ClientSecurityContext;
import stroom.widget.tab.client.presenter.TabData;
import stroom.widget.tab.client.presenter.TabDataImpl;

public class IndexPresenter extends EntityEditTabPresenter<LinkTabPanelView, Index> {
    private static final TabData SETTINGS = new TabDataImpl("Settings");
    private static final TabData FIELDS = new TabDataImpl("Fields");
    private static final TabData SHARDS = new TabDataImpl("Shards");

    private final TabContentProvider<Index> tabContentProvider = new TabContentProvider<>();

    @Inject
    public IndexPresenter(final EventBus eventBus, final LinkTabPanelView view,
                          final Provider<IndexSettingsPresenter> indexSettingsPresenter,
                          final Provider<IndexFieldListPresenter> indexFieldListPresenter,
                          final Provider<IndexShardPresenter> indexShardPresenter, final ClientSecurityContext securityContext) {
        super(eventBus, view, securityContext);

        tabContentProvider.setDirtyHandler(new DirtyHandler() {
            @Override
            public void onDirty(final DirtyEvent event) {
                if (event.isDirty()) {
                    setDirty(true);
                }
            }
        });

        tabContentProvider.add(SETTINGS, indexSettingsPresenter);
        tabContentProvider.add(FIELDS, indexFieldListPresenter);
        tabContentProvider.add(SHARDS, indexShardPresenter);
        addTab(SETTINGS);
        addTab(FIELDS);
        addTab(SHARDS);
        selectTab(SETTINGS);
    }

    @Override
    protected void getContent(final TabData tab, final ContentCallback callback) {
        callback.onReady(tabContentProvider.getPresenter(tab));
    }

    @Override
    public void onRead(final Index index) {
        tabContentProvider.read(index);
    }

    @Override
    protected void onWrite(final Index index) {
        tabContentProvider.write(index);
    }

    @Override
    public String getType() {
        return Index.ENTITY_TYPE;
    }
}
