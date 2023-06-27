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
import stroom.entity.client.presenter.MarkdownEditPresenter;
import stroom.statistics.impl.sql.shared.StatisticStoreDoc;
import stroom.statistics.impl.sql.shared.StatisticsDataSourceData;
import stroom.widget.tab.client.presenter.TabData;
import stroom.widget.tab.client.presenter.TabDataImpl;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

public class StatisticsDataSourcePresenter extends DocumentEditTabPresenter<LinkTabPanelView, StatisticStoreDoc> {

    private static final TabData SETTINGS = new TabDataImpl("Settings");
    private static final TabData FIELDS = new TabDataImpl("Fields");
    private static final TabData CUSTOM_ROLLUPS = new TabDataImpl("Custom Roll-ups");
    private static final TabData DOCUMENTATION = new TabDataImpl("Documentation");

    private final StatisticsDataSourceSettingsPresenter statisticsDataSourceSettingsPresenter;
    private final StatisticsFieldListPresenter statisticsFieldListPresenter;
    private final StatisticsCustomMaskListPresenter statisticsCustomMaskListPresenter;
    private final MarkdownEditPresenter markdownEditPresenter;

    @Inject
    public StatisticsDataSourcePresenter(
            final EventBus eventBus,
            final LinkTabPanelView view,
            final StatisticsDataSourceSettingsPresenter statisticsDataSourceSettingsPresenter,
            final StatisticsFieldListPresenter statisticsFieldListPresenter,
            final StatisticsCustomMaskListPresenter statisticsCustomMaskListPresenter,
            final MarkdownEditPresenter markdownEditPresenter) {
        super(eventBus, view);
        this.statisticsDataSourceSettingsPresenter = statisticsDataSourceSettingsPresenter;
        this.statisticsFieldListPresenter = statisticsFieldListPresenter;
        this.statisticsCustomMaskListPresenter = statisticsCustomMaskListPresenter;
        this.markdownEditPresenter = markdownEditPresenter;

        addTab(SETTINGS);
        addTab(FIELDS);
        addTab(CUSTOM_ROLLUPS);
        addTab(DOCUMENTATION);
        selectTab(SETTINGS);
    }

    @Override
    protected void onBind() {
        super.onBind();
        registerHandler(statisticsDataSourceSettingsPresenter.addDirtyHandler(event -> {
            if (event.isDirty()) {
                setDirty(true);
            }
        }));
        registerHandler(statisticsFieldListPresenter.addDirtyHandler(event -> {
            if (event.isDirty()) {
                setDirty(true);
            }
        }));
        registerHandler(statisticsCustomMaskListPresenter.addDirtyHandler(event -> {
            if (event.isDirty()) {
                setDirty(true);
            }
        }));
        registerHandler(markdownEditPresenter.addDirtyHandler(event -> {
            if (event.isDirty()) {
                setDirty(true);
            }
        }));
    }

    @Override
    protected void getContent(final TabData tab, final ContentCallback callback) {
        if (SETTINGS.equals(tab)) {
            callback.onReady(statisticsDataSourceSettingsPresenter);
        } else if (FIELDS.equals(tab)) {
            callback.onReady(statisticsFieldListPresenter);
        } else if (CUSTOM_ROLLUPS.equals(tab)) {
            callback.onReady(statisticsCustomMaskListPresenter);
        } else if (DOCUMENTATION.equals(tab)) {
            callback.onReady(markdownEditPresenter);
        } else {
            callback.onReady(null);
        }
    }

    @Override
    public void onRead(final DocRef docRef, final StatisticStoreDoc doc, final boolean readOnly) {
        super.onRead(docRef, doc, readOnly);
        if (doc.getConfig() == null) {
            doc.setConfig(new StatisticsDataSourceData());
        }

        statisticsDataSourceSettingsPresenter.read(docRef, doc, readOnly);
        statisticsFieldListPresenter.read(docRef, doc, readOnly);
        statisticsCustomMaskListPresenter.read(docRef, doc, readOnly);
        markdownEditPresenter.setText(doc.getDescription());
        markdownEditPresenter.setReadOnly(readOnly);

        // the field and rollup presenters need to know about each other as
        // changes in one affect the other
        statisticsFieldListPresenter.setCustomMaskListPresenter(statisticsCustomMaskListPresenter);
    }

    @Override
    protected StatisticStoreDoc onWrite(StatisticStoreDoc doc) {
        doc = statisticsDataSourceSettingsPresenter.write(doc);
        doc = statisticsFieldListPresenter.write(doc);
        doc = statisticsCustomMaskListPresenter.write(doc);
        doc.setDescription(markdownEditPresenter.getText());
        return doc;
    }

    @Override
    public String getType() {
        return StatisticStoreDoc.DOCUMENT_TYPE;
    }
}
