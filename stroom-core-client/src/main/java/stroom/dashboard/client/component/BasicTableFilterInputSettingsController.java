/*
 * Copyright 2025 Crown Copyright
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

package stroom.dashboard.client.component;

import stroom.dashboard.client.embeddedquery.EmbeddedQueryPresenter;
import stroom.dashboard.client.input.FilterableTable;
import stroom.dashboard.client.main.DashboardPane;
import stroom.dashboard.shared.ComponentConfig;
import stroom.dashboard.shared.TableFilterComponentSettings;
import stroom.query.api.Column;
import stroom.query.api.ColumnRef;
import stroom.util.shared.NullSafe;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/// A Table Filter pane's Basic settings tab ([BasicTableFilterInputSettings]): its name, the table it
/// filters and the columns to show filters for, which it tells the Conditional Formatting tab
/// ([#setMultiRulesController]).
public class BasicTableFilterInputSettingsController
        extends BasicSettingsTabController<BasicTableFilterInputSettings> {

    private final ColumnSelection columnSelection;
    private MultiRulesController multiRules;

    /// Made by GIN, with its content.
    ///
    /// @param eventBus The application's event bus.
    /// @param view     The content.
    @Inject
    public BasicTableFilterInputSettingsController(final EventBus eventBus,
                                                   final BasicTableFilterInputSettings view) {
        super(eventBus, view);
        this.columnSelection = view.getColumns();
        view.setOnTableChange(this::onTableChange);

        columnSelection.setOnChange(selected -> multiRules.setSelectedColumns(selected));
    }

    private void onTableChange() {
        updateFieldNames(getView().getTable());
    }

    private void updateFieldNames(final DashboardPane component) {
        final Map<ColumnRef, Column> allColumns = new HashMap<>();
        if (component instanceof final FilterableTable filterableTable) {
            final List<Column> columns = filterableTable.getColumns();
            if (!NullSafe.isEmptyCollection(columns)) {
                for (final Column column : columns) {
                    final ColumnRef ref = ColumnRef.builder().id(column.getId()).name(column.getName()).build();
                    allColumns.put(ref, column);
                }
            }
        }

        final Set<ColumnRef> selectedColumns = new HashSet<>(columnSelection.getSelectedColumns());
        final Set<ColumnRef> allColumnRefs = allColumns.keySet();
        selectedColumns.retainAll(allColumnRefs);
        columnSelection.setAllColumns(allColumnRefs);
        columnSelection.setSelectedColumns(selectedColumns);
        columnSelection.refresh();

        multiRules.setAllColumns(allColumns);
        multiRules.setSelectedColumns(columnSelection.getSelectedColumns());
    }

    @Override
    public void read(final ComponentConfig componentConfig) {
        super.read(componentConfig);

        final List<DashboardPane> list = getDashboardContext()
                .getComponents().getSortedComponentsByType(
                        TableController.TYPE.getId(),
                        EmbeddedQueryPresenter.TYPE.getId());
        getView().setTableList(list);

        final TableFilterComponentSettings settings = (TableFilterComponentSettings) componentConfig.getSettings();
        getView().setTable(getDashboardContext().getComponents().get(settings.getTableId()));
        columnSelection.setSelectedColumns(new HashSet<>(NullSafe.list(settings.getColumns())));
        updateFieldNames(getView().getTable());
    }

    @Override
    public ComponentConfig write(final ComponentConfig componentConfig) {
        final ComponentConfig result = super.write(componentConfig);
        final TableFilterComponentSettings oldSettings = (TableFilterComponentSettings) result.getSettings();
        final TableFilterComponentSettings newSettings = writeSettings(oldSettings);
        return result.copy().settings(newSettings).build();
    }

    private TableFilterComponentSettings writeSettings(final TableFilterComponentSettings settings) {
        return settings
                .copy()
                .tableId(getTableId())
                .columns(new ArrayList<>(columnSelection.getSelectedColumns()))
                .build();
    }

    private String getTableId() {
        final DashboardPane table = getView().getTable();
        if (table == null) {
            return null;
        }

        return table.getId();
    }

    @Override
    public boolean isDirty(final ComponentConfig componentConfig) {
        if (super.isDirty(componentConfig)) {
            return true;
        }

        final TableFilterComponentSettings oldSettings = (TableFilterComponentSettings) componentConfig.getSettings();
        final TableFilterComponentSettings newSettings = writeSettings(oldSettings);

        final boolean equal = Objects.equals(oldSettings, newSettings);

        return !equal;
    }

    /// @param multiRules The Conditional Formatting tab, told the columns chosen.
    public void setMultiRulesController(final MultiRulesController multiRules) {
        this.multiRules = multiRules;
    }
}
