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

package stroom.dashboard.client.input;

import stroom.dashboard.client.embeddedquery.EmbeddedQueryPresenter;
import stroom.dashboard.client.main.BasicSettingsTabPresenter;
import stroom.dashboard.client.main.BasicSettingsView;
import stroom.dashboard.client.main.Component;
import stroom.dashboard.client.table.TablePresenter;
import stroom.dashboard.shared.ComponentConfig;
import stroom.dashboard.shared.TableFilterComponentSettings;
import stroom.query.api.Column;
import stroom.query.api.ColumnRef;
import stroom.util.shared.NullSafe;

import com.google.gwt.user.client.ui.Focus;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.HasUiHandlers;
import com.gwtplatform.mvp.client.View;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class BasicTableFilterSettingsPresenter
        extends BasicSettingsTabPresenter<BasicTableFilterSettingsPresenter.BasicTableFilterSettingsView>
        implements BasicTableFilterSettingsUiHandlers, Focus {

    private final ColumnSelectionPresenter columnSelectionPresenter;
    private MultiRulesPresenter multiRulesPresenter;

    @Inject
    public BasicTableFilterSettingsPresenter(final EventBus eventBus,
                                             final BasicTableFilterSettingsView view,
                                             final ColumnSelectionPresenter columnSelectionPresenter) {
        super(eventBus, view);
        this.columnSelectionPresenter = columnSelectionPresenter;
        view.setColumnList(columnSelectionPresenter.getView());
        view.setUiHandlers(this);
    }

    @Override
    public void focus() {
        getView().focus();
    }

    @Override
    protected void onBind() {
        registerHandler(columnSelectionPresenter.addValueChangeHandler(e ->
                multiRulesPresenter.setSelectedColumns(columnSelectionPresenter.getSelectedColumns())));
    }

    @Override
    public void onTableChange() {
        updateFieldNames(getView().getTable());
    }

    private void updateFieldNames(final Component component) {
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

        final Set<ColumnRef> selectedColumns = new HashSet<>(columnSelectionPresenter.getSelectedColumns());
        final Set<ColumnRef> allColumnRefs = allColumns.keySet();
        selectedColumns.retainAll(allColumnRefs);
        columnSelectionPresenter.setAllColumns(allColumnRefs);
        columnSelectionPresenter.setSelectedColumns(selectedColumns);
        columnSelectionPresenter.refresh();

        multiRulesPresenter.setAllColumns(allColumns);
        multiRulesPresenter.setSelectedColumns(columnSelectionPresenter.getSelectedColumns());
    }

    @Override
    public void read(final ComponentConfig componentConfig) {
        super.read(componentConfig);

        final List<Component> list = getDashboardContext()
                .getComponents().getSortedComponentsByType(
                        TablePresenter.TYPE.getId(),
                        EmbeddedQueryPresenter.TYPE.getId());
        getView().setTableList(list);

        final TableFilterComponentSettings settings = (TableFilterComponentSettings) componentConfig.getSettings();
        getView().setTable(getDashboardContext().getComponents().get(settings.getTableId()));
        columnSelectionPresenter.setSelectedColumns(new HashSet<>(NullSafe.list(settings.getColumns())));
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
                .columns(new ArrayList<>(columnSelectionPresenter.getSelectedColumns()))
                .build();
    }

    private String getTableId() {
        final Component table = getView().getTable();
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

    public void setMultiRulesPresenter(final MultiRulesPresenter multiRulesPresenter) {
        this.multiRulesPresenter = multiRulesPresenter;
    }

    // --------------------------------------------------------------------------------


    public interface BasicTableFilterSettingsView
            extends BasicSettingsView, HasUiHandlers<BasicTableFilterSettingsUiHandlers> {

        void setTableList(List<Component> tableList);

        Component getTable();

        void setTable(Component table);

        void setColumnList(View view);
    }
}
