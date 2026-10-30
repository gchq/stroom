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

import stroom.dashboard.client.input.FilterableTable;
import stroom.dashboard.client.main.DashboardPane;
import stroom.dashboard.client.main.PaneRegistry.ComponentType;
import stroom.dashboard.client.main.PaneRegistry.ComponentUse;
import stroom.dashboard.shared.ComponentConfig;
import stroom.dashboard.shared.ComponentSettings;
import stroom.dashboard.shared.TableFilterComponentSettings;
import stroom.query.api.Column;
import stroom.query.api.ColumnRef;
import stroom.query.api.ConditionalFormattingRule;
import stroom.util.shared.NullSafe;

import com.google.gwt.dom.client.Element;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;
import com.google.web.bindery.event.shared.HandlerRegistration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/// A dashboard's Table Filter pane ([TableFilterInput]): a value filter for each column chosen in
/// its settings of the table it filters, kept up to date as the table's columns change.
public class TableFilterInputController extends DashboardPaneController {

    /// The tab type of the pane.
    public static final String TAB_TYPE = "TableFilter";
    /// The pane's type, as the dashboard's Add menu offers it.
    public static final ComponentType TYPE =
            new ComponentType(1,
                    "table-filter",
                    "Table Filter",
                    ComponentUse.INPUT);

    private final TableFilterInput view;
    private final Provider<ColumnValuesFilterController> columnValuesFilterProvider;
    private final List<HandlerRegistration> registrations = new ArrayList<>();
    private final Map<String, ColumnValuesFilterController> columnFilters = new HashMap<>();
    private List<Column> currentColumns = Collections.emptyList();

    /// Made by GIN, with its content.
    ///
    /// @param eventBus                   The application's event bus.
    /// @param view                       The content.
    /// @param settingsProvider           Makes the pane's settings dialog.
    /// @param columnValuesFilterProvider Makes a column's value filter.
    @Inject
    public TableFilterInputController(final EventBus eventBus,
                                      final TableFilterInput view,
                                      final Provider<TableFilterInputSettingsController> settingsProvider,
                                      final Provider<ColumnValuesFilterController> columnValuesFilterProvider) {
        super(eventBus, settingsProvider);
        this.view = view;
        initWidget(view);
        this.columnValuesFilterProvider = columnValuesFilterProvider;
        onDispose(this::removeTableListeners);
    }

    @Override
    public void read(final ComponentConfig componentConfig) {
        super.read(componentConfig);

        final ComponentSettings settings = componentConfig.getSettings();
        if (!(settings instanceof TableFilterComponentSettings)) {
            setSettings(createSettings());
        }
    }

    private void reset() {
        view.clearColumns();
        // Made again for the new columns
        columnFilters.values().forEach(this::release);
        columnFilters.clear();
        currentColumns = Collections.emptyList();
    }

    private void removeTableListeners() {
        registrations.forEach(HandlerRegistration::removeHandler);
        registrations.clear();
    }

    private void update(final TableFilterComponentSettings settings) {
        reset();

        removeTableListeners();
        if (settings.getTableId() != null) {
            final DashboardPane component = getDashboardContext()
                    .getComponents()
                    .get(getTableFilterSettings().getTableId());
            if (component instanceof final FilterableTable table) {
                updateColumns(settings, table);
                registrations.add(table.addUpdateListener(() -> {
                    updateColumns(settings, table);
                    columnFilters.values().forEach(ColumnValuesFilterController::refresh);
                }));
            }
        }
    }

    private void updateColumns(final TableFilterComponentSettings settings,
                               final FilterableTable table) {
        final List<Column> columns = table.getColumns().stream()
                .sorted(Comparator.comparing(Column::getName))
                .collect(Collectors.toList());
        if (!Objects.equals(currentColumns, columns)) {
            reset();
            currentColumns = columns;

            final Set<String> included = NullSafe
                    .list(getTableFilterSettings().getColumns())
                    .stream()
                    .map(ColumnRef::getId)
                    .collect(Collectors.toSet());
            for (final Column column : columns) {
                if (included.contains(column.getId())) {
                    final ColumnValuesFilterController columnValuesFilter =
                            make(columnValuesFilterProvider);
                    columnFilters.put(column.getId(), columnValuesFilter);

                    final List<ConditionalFormattingRule> rules =
                            NullSafe.map(settings.getConditionalFormattingRules()).get(column.getId());

                    final Provider<Element> filterButtonProvider = () -> table.getFilterButton(column);
                    columnValuesFilter.init(
                            filterButtonProvider,
                            column,
                            () -> table.getDataSupplier(column, rules),
                            () -> columnFilters
                                    .entrySet()
                                    .stream()
                                    .collect(Collectors.toMap(
                                            Entry::getKey,
                                            entry -> entry.getValue().getSelection())),
                            column.getColumnValueSelection(),
                            table.getFilterCellManager(),
                            rules);

                    view.addColumn(column.getName(), columnValuesFilter);
                    columnValuesFilter.refresh();
                }
            }
        }
    }

    private TableFilterComponentSettings getTableFilterSettings() {
        return (TableFilterComponentSettings) getSettings();
    }

    private TableFilterComponentSettings createSettings() {
        return TableFilterComponentSettings.builder().build();
    }

    @Override
    public void link() {
        update(getTableFilterSettings());
    }

    @Override
    protected void changeSettings() {
        super.changeSettings();
        update(getTableFilterSettings());
    }

    @Override
    public ComponentType getComponentType() {
        return TYPE;
    }

    @Override
    public String getType() {
        return TAB_TYPE;
    }
}
