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

package stroom.statistics.impl.sql.client.presenter;

import stroom.docref.DocRef;
import stroom.entity.client.presenter.ContentCallback;
import stroom.entity.client.presenter.DocumentEditTabPresenter;
import stroom.entity.client.presenter.LinkTabPanelView;
import stroom.entity.client.presenter.TabContentProvider;
import stroom.statistics.impl.sql.shared.StatisticStoreDoc;
import stroom.statistics.impl.sql.shared.StatisticsDataSourceData;
import stroom.widget.tab.client.presenter.TabData;
import stroom.widget.tab.client.presenter.TabDataImpl;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

public class StatisticsDataSourcePresenter extends DocumentEditTabPresenter<LinkTabPanelView, StatisticStoreDoc> {

    private static final TabData SETTINGS = new TabDataImpl("Settings");
    private static final TabData FIELDS = new TabDataImpl("Fields");
    private static final TabData CUSTOM_ROLLUPS = new TabDataImpl("Custom Roll-ups");

    private final TabContentProvider<StatisticStoreDoc> tabContentProvider = new TabContentProvider<>();

    @Inject
    public StatisticsDataSourcePresenter(
            final EventBus eventBus,
            final LinkTabPanelView view,
            final Provider<StatisticsDataSourceSettingsPresenter> statisticsDataSourceSettingsPresenter,
            final Provider<StatisticsFieldListPresenter> statisticsFieldListPresenter,
            final Provider<StatisticsCustomMaskListPresenter> statisticsCustomMaskListPresenter) {
        super(eventBus, view);

        tabContentProvider.setDirtyHandler(event -> {
            if (event.isDirty()) {
                setDirty(true);
            }
        });

        tabContentProvider.add(SETTINGS, statisticsDataSourceSettingsPresenter);
        tabContentProvider.add(FIELDS, statisticsFieldListPresenter);
        tabContentProvider.add(CUSTOM_ROLLUPS, statisticsCustomMaskListPresenter);

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
    public void onRead(final DocRef docRef, final StatisticStoreDoc statisticsDataSource, final boolean readOnly) {
        super.onRead(docRef, statisticsDataSource, readOnly);
        if (statisticsDataSource != null) {
            if (statisticsDataSource.getConfig() == null) {
                statisticsDataSource.setConfig(new StatisticsDataSourceData());
            }
        }

        tabContentProvider.read(docRef, statisticsDataSource, readOnly);

        // the field and rollup presenters need to know about each other as
        // changes in one affect the other
        ((StatisticsFieldListPresenter) tabContentProvider.getPresenter(FIELDS)).setCustomMaskListPresenter(
                (StatisticsCustomMaskListPresenter) tabContentProvider.getPresenter(CUSTOM_ROLLUPS));
    }

    @Override
    protected StatisticStoreDoc onWrite(final StatisticStoreDoc statisticsDataSource) {
        return tabContentProvider.write(statisticsDataSource);
    }

    @Override
    public String getType() {
        return StatisticStoreDoc.DOCUMENT_TYPE;
    }
}
