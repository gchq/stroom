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

package stroom.statistics.impl.sql.client.component;

import stroom.docref.DocRef;
import stroom.entity.client.component.DocumentEditor;
import stroom.entity.client.component.LinkTabPanel;
import stroom.entity.client.component.MarkdownEditController;
import stroom.security.client.component.DocumentUserPermissionsTabController;
import stroom.statistics.impl.sql.client.presenter.State;
import stroom.statistics.impl.sql.client.presenter.State.Field;
import stroom.statistics.impl.sql.shared.CustomRollUpMask;
import stroom.statistics.impl.sql.shared.StatisticField;
import stroom.statistics.impl.sql.shared.StatisticStoreDoc;
import stroom.statistics.impl.sql.shared.StatisticsDataSourceData;
import stroom.util.shared.NullSafe;
import stroom.widget.tab.client.presenter.TabData;
import stroom.widget.tab.client.presenter.TabDataImpl;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/// A statistic store's editor: its Settings, Fields, Custom Roll-ups, Documentation and Permissions
/// tabs (see [DocumentEditor]). The Fields and Custom Roll-ups tabs share the store's fields and
/// roll-up masks, as a change to one affects the other: they are read from the store before the
/// tabs are, and written into it after them.
public class StatisticsDataSourceEditor extends DocumentEditor<StatisticStoreDoc> {

    private static final TabData SETTINGS = new TabDataImpl("Settings");
    private static final TabData FIELDS = new TabDataImpl("Fields");
    private static final TabData CUSTOM_ROLLUPS = new TabDataImpl("Custom Roll-ups");
    private static final TabData DOCUMENTATION = new TabDataImpl("Documentation");
    private static final TabData PERMISSIONS = new TabDataImpl("Permissions");

    private final State state = new State();

    /// Makes the editor, with its Settings, Fields, Custom Roll-ups, Documentation and Permissions
    /// tabs.
    @Inject
    public StatisticsDataSourceEditor(
            final EventBus eventBus,
            final LinkTabPanel view,
            final Provider<StatisticsDataSourceSettingsController> settingsProvider,
            final StatisticsFieldListController fieldList,
            final StatisticsCustomMaskListController customMaskList,
            final Provider<MarkdownEditController> markdownEditProvider,
            final Provider<DocumentUserPermissionsTabController> permissionsProvider) {
        super(eventBus, view);
        own(fieldList);
        own(customMaskList);
        // The field and roll-up lists need to know about each other, as changes in one affect the
        // other
        fieldList.setState(state);
        customMaskList.setState(state);
        fieldList.setOnFieldsChange(customMaskList::update);

        addTab(SETTINGS,
                settingsProvider::get,
                StatisticsDataSourceSettingsController::read,
                StatisticsDataSourceSettingsController::write);
        addOwnedTab(FIELDS,
                fieldList,
                StatisticsFieldListController::read);
        addOwnedTab(CUSTOM_ROLLUPS,
                customMaskList,
                StatisticsCustomMaskListController::read);
        addDocumentationTab(DOCUMENTATION,
                markdownEditProvider,
                StatisticStoreDoc::getDescription,
                (doc, description) -> doc.copy().description(description).build());
        addPermissionsTab(PERMISSIONS, permissionsProvider);
        selectTab(SETTINGS);
    }

    @Override
    protected void beforeRead(final DocRef docRef, final StatisticStoreDoc doc) {
        state.clear();
        final StatisticsDataSourceData config = doc.getConfig();
        if (config != null) {
            final List<Field> fields = NullSafe
                    .list(config.getFields())
                    .stream()
                    .map(field -> state.createField(field.getFieldName()))
                    .collect(Collectors.toList());
            fields.forEach(state::addField);

            NullSafe.collection(config.getCustomRollUpMasks()).forEach(customRollUpMask -> {
                final Set<Field> rollup = new HashSet<>();
                for (int i = 0; i < fields.size(); i++) {
                    if (customRollUpMask.isTagRolledUp(i)) {
                        rollup.add(fields.get(i));
                    }
                }
                state.addMask(rollup);
            });
        }
    }

    @Override
    protected StatisticStoreDoc afterWrite(final StatisticStoreDoc written) {
        state.sortFields();
        final List<StatisticField> statisticFields = state.getFields()
                .stream()
                .map(field -> new StatisticField(field.getName()))
                .collect(Collectors.toList());

        final Set<CustomRollUpMask> customRollUpMasks = state.getIntegerMaskSet()
                .stream()
                .map(CustomRollUpMask::new)
                .sorted()
                .collect(Collectors.toCollection(LinkedHashSet::new));

        final StatisticsDataSourceData config = NullSafe.getOrElse(
                        written,
                        StatisticStoreDoc::getConfig,
                        StatisticsDataSourceData::copy,
                        StatisticsDataSourceData.builder())
                .fields(statisticFields)
                .customRollUpMasks(customRollUpMasks)
                .build();
        return written.copy().config(config).build();
    }
}
