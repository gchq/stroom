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

package stroom.stats.client.presenter;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;
import stroom.entity.client.presenter.ContentCallback;
import stroom.entity.client.presenter.DocumentEditTabPresenter;
import stroom.entity.client.presenter.LinkTabPanelView;
import stroom.entity.client.presenter.TabContentProvider;
import stroom.query.api.v2.DocRef;
import stroom.security.client.ClientSecurityContext;
import stroom.stats.shared.StroomStatsStoreEntity;
import stroom.stats.shared.StroomStatsStoreEntityData;
import stroom.widget.tab.client.presenter.TabData;
import stroom.widget.tab.client.presenter.TabDataImpl;

public class StroomStatsStorePresenter extends DocumentEditTabPresenter<LinkTabPanelView, StroomStatsStoreEntity> {
    private static final TabData SETTINGS = new TabDataImpl("Settings");
    private static final TabData FIELDS = new TabDataImpl("Fields");
    private static final TabData CUSTOM_ROLLUPS = new TabDataImpl("Custom Roll-ups");

    private final TabContentProvider<StroomStatsStoreEntity> tabContentProvider = new TabContentProvider<>();

    @Inject
    public StroomStatsStorePresenter(final EventBus eventBus, final LinkTabPanelView view,
                                     final Provider<StroomStatsStoreSettingsPresenter> stroomStatsStoreSettingsPresenter,
                                     final Provider<StroomStatsStoreFieldListPresenter> stroomStatsStoreFieldListPresenter,
                                     final Provider<StroomStatsStoreCustomMaskListPresenter> stroomStatsStoreCustomMaskListPresenter,
                                     final ClientSecurityContext securityContext) {
        super(eventBus, view, securityContext);

        tabContentProvider.setDirtyHandler(event -> {
            if (event.isDirty()) {
                setDirty(true);
            }
        });

        tabContentProvider.add(SETTINGS, stroomStatsStoreSettingsPresenter);
        tabContentProvider.add(FIELDS, stroomStatsStoreFieldListPresenter);
        tabContentProvider.add(CUSTOM_ROLLUPS, stroomStatsStoreCustomMaskListPresenter);

        addTab(SETTINGS);
        addTab(FIELDS);
        addTab(CUSTOM_ROLLUPS);

        selectTab(SETTINGS);
    }

    @Override
    protected void getContent(final TabData tab, final ContentCallback callback) {
        callback.onReady(tabContentProvider.getPresenter(tab));
    }

    @Override
    public void onRead(final DocRef docRef, final StroomStatsStoreEntity stroomStatsStoreEntity) {
        if (stroomStatsStoreEntity != null) {
            if (stroomStatsStoreEntity.getDataObject() == null) {
                stroomStatsStoreEntity.setDataObject(new StroomStatsStoreEntityData());
            }
        }

        tabContentProvider.read(docRef, stroomStatsStoreEntity);

        // the field and rollup presenters need to know about each other as
        // changes in one affect the other
        ((StroomStatsStoreFieldListPresenter) tabContentProvider.getPresenter(FIELDS)).setCustomMaskListPresenter(
                (StroomStatsStoreCustomMaskListPresenter) tabContentProvider.getPresenter(CUSTOM_ROLLUPS));
    }

    @Override
    protected void onWrite(final StroomStatsStoreEntity stroomStatsStoreEntity) {
        tabContentProvider.write(stroomStatsStoreEntity);
    }

    @Override
    public String getType() {
        return StroomStatsStoreEntity.ENTITY_TYPE;
    }
}
