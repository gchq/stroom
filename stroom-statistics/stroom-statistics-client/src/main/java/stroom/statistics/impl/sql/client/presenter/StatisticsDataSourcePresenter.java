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
import stroom.entity.client.presenter.DocTabPresenter;
import stroom.entity.client.presenter.DocTabProvider;
import stroom.entity.client.presenter.LinkTabPanelView;
import stroom.entity.client.presenter.MarkdownEditPresenter;
import stroom.entity.client.presenter.MarkdownTabProvider;
import stroom.security.client.presenter.DocumentUserPermissionsTabProvider;
import stroom.statistics.impl.sql.client.presenter.State.Field;
import stroom.statistics.impl.sql.shared.CustomRollUpMask;
import stroom.statistics.impl.sql.shared.StatisticField;
import stroom.statistics.impl.sql.shared.StatisticStoreDoc;
import stroom.statistics.impl.sql.shared.StatisticsDataSourceData;
import stroom.util.shared.NullSafe;
import stroom.widget.tab.client.presenter.TabData;
import stroom.widget.tab.client.presenter.TabDataImpl;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import javax.inject.Provider;

public class StatisticsDataSourcePresenter extends DocTabPresenter<LinkTabPanelView, StatisticStoreDoc> {

    private static final TabData SETTINGS = new TabDataImpl("Settings");
    private static final TabData FIELDS = new TabDataImpl("Fields");
    private static final TabData CUSTOM_ROLLUPS = new TabDataImpl("Custom Roll-ups");
    private static final TabData DOCUMENTATION = new TabDataImpl("Documentation");
    private static final TabData PERMISSIONS = new TabDataImpl("Permissions");

    private final State state = new State();

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
        statisticsFieldListPresenter.setState(state);
        statisticsCustomMaskListPresenter.setState(state);

        addTab(SETTINGS, new DocTabProvider<>(statisticsDataSourceSettingsPresenterProvider::get));
        addTab(FIELDS, new DocTabProvider<>(() -> statisticsFieldListPresenter));
        addTab(CUSTOM_ROLLUPS, new DocTabProvider<>(() -> statisticsCustomMaskListPresenter));
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
                return document.copy().description(presenter.getText()).build();
            }
        });
        addTab(PERMISSIONS, documentUserPermissionsTabProvider);
        selectTab(SETTINGS);
    }


    @Override
    public void onRead(final DocRef docRef, final StatisticStoreDoc doc, final boolean readOnly) {
        state.clear();
        final StatisticsDataSourceData config = doc.getConfig();
        if (config != null) {

            final List<Field> fields = NullSafe
                    .list(config.getFields())
                    .stream()
                    .map(f -> state.createField(f.getFieldName()))
                    .collect(Collectors.toList());
            fields.forEach(state::addField);

            NullSafe.collection(config.getCustomRollUpMasks()).forEach(customRollUpMask -> {
                final Set<Field> rollup = new HashSet<>();
                for (int i = 0; i < fields.size(); i++) {
                    if (customRollUpMask.isTagRolledUp(i)) {
                        final Field field = fields.get(i);
                        rollup.add(field);
                    }
                }
                state.addMask(rollup);
            });

            super.onRead(docRef, doc.copy().config(StatisticsDataSourceData.builder().build()).build(), readOnly);
        } else {
            super.onRead(docRef, doc, readOnly);
        }
    }

    @Override
    protected StatisticStoreDoc onWrite(final StatisticStoreDoc document) {
        final StatisticStoreDoc doc = super.onWrite(document);

        state.sortFields();
        final List<StatisticField> statisticFields = state.getFields()
                .stream()
                .map(field -> new StatisticField(field.getName()))
                .collect(Collectors.toList());

        final Set<List<Integer>> masks = state.getIntegerMaskSet();

        final Set<CustomRollUpMask> customRollUpMasks = masks
                .stream()
                .map(CustomRollUpMask::new)
                .sorted()
                .collect(Collectors.toCollection(LinkedHashSet::new));

        final StatisticsDataSourceData config = NullSafe.getOrElse(
                        doc,
                        StatisticStoreDoc::getConfig,
                        StatisticsDataSourceData::copy,
                        StatisticsDataSourceData.builder())
                .fields(statisticFields)
                .customRollUpMasks(customRollUpMasks)
                .build();
        return doc.copy().config(config).build();
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
