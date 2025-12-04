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

package stroom.statistics.impl.sql.client.presenter;

import stroom.docref.DocRef;
import stroom.entity.client.presenter.DocumentEditTabPresenter;
import stroom.entity.client.presenter.DocumentEditTabProvider;
import stroom.entity.client.presenter.LinkTabPanelView;
import stroom.entity.client.presenter.MarkdownEditPresenter;
import stroom.entity.client.presenter.MarkdownTabProvider;
import stroom.security.client.presenter.DocumentUserPermissionsTabProvider;
import stroom.statistics.impl.sql.shared.StatisticStoreDoc;
import stroom.statistics.impl.sql.shared.StatisticsDataSourceData;
import stroom.widget.tab.client.presenter.TabData;
import stroom.widget.tab.client.presenter.TabDataImpl;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import javax.inject.Provider;

public class StatisticsDataSourcePresenter extends DocumentEditTabPresenter<LinkTabPanelView, StatisticStoreDoc> {

    private static final TabData SETTINGS = new TabDataImpl("Settings");
    private static final TabData FIELDS = new TabDataImpl("Fields");
    private static final TabData CUSTOM_ROLLUPS = new TabDataImpl("Custom Roll-ups");
    private static final TabData DOCUMENTATION = new TabDataImpl("Documentation");
    private static final TabData PERMISSIONS = new TabDataImpl("Permissions");

    @Inject
    public StatisticsDataSourcePresenter(
            final EventBus eventBus,
            final LinkTabPanelView view,
            final Provider<StatisticsDataSourceSettingsPresenter> statisticsDataSourceSettingsPresenterProvider,
            final StatisticsFieldListPresenter statisticsFieldListPresenter,
            final StatisticsCustomMaskListPresenter statisticsCustomMaskListPresenter,
            final Provider<MarkdownEditPresenter> markdownEditPresenterProvider,
            final DocumentUserPermissionsTabProvider<StatisticStoreDoc> documentUserPermissionsTabProvider) {
        super(eventBus, view);

        // the field and rollup presenters need to know about each other as
        // changes in one affect the other
        statisticsFieldListPresenter.setCustomMaskListPresenter(statisticsCustomMaskListPresenter);

        addTab(SETTINGS, new DocumentEditTabProvider<>(statisticsDataSourceSettingsPresenterProvider::get));
        addTab(FIELDS, new DocumentEditTabProvider<>(() -> statisticsFieldListPresenter));
        addTab(CUSTOM_ROLLUPS, new DocumentEditTabProvider<>(() -> statisticsCustomMaskListPresenter));
        addTab(DOCUMENTATION, new MarkdownTabProvider<StatisticStoreDoc>(eventBus, markdownEditPresenterProvider) {
            @Override
            public void onRead(final MarkdownEditPresenter presenter,
                               final DocRef docRef,
                               final StatisticStoreDoc document,
                               final boolean readOnly) {
                presenter.setText(document.getDescription());
                presenter.setReadOnly(readOnly);
            }

            @Override
            public StatisticStoreDoc onWrite(final MarkdownEditPresenter presenter,
                                             final StatisticStoreDoc document) {
                document.setDescription(presenter.getText());
                return document;
            }
        });
        addTab(PERMISSIONS, documentUserPermissionsTabProvider);
        selectTab(SETTINGS);
    }


    @Override
    public void onRead(final DocRef docRef, final StatisticStoreDoc doc, final boolean readOnly) {
        if (doc.getConfig() == null) {
            doc.setConfig(new StatisticsDataSourceData());
        }
        super.onRead(docRef, doc, readOnly);
    }

    @Override
    public String getType() {
        return StatisticStoreDoc.TYPE;
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
