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
 */

package stroom.dashboard.client.input;

import stroom.dashboard.client.input.TableFilterPresenter.TableFilterView;
import stroom.dashboard.client.main.AbstractComponentPresenter;
import stroom.dashboard.client.main.Component;
import stroom.dashboard.client.main.ComponentRegistry.ComponentType;
import stroom.dashboard.client.main.ComponentRegistry.ComponentUse;
import stroom.dashboard.client.table.ColumnValuesFilterPresenter;
import stroom.dashboard.client.table.TablePresenter;
import stroom.dashboard.shared.ComponentConfig;
import stroom.dashboard.shared.ComponentSettings;
import stroom.dashboard.shared.TableComponentSettings;
import stroom.dashboard.shared.TableFilterComponentSettings;
import stroom.query.api.Column;
import stroom.query.api.ColumnRef;
import stroom.query.api.ConditionalFormattingRule;
import stroom.util.shared.NullSafe;

import com.google.gwt.dom.client.Element;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Label;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;
import com.google.web.bindery.event.shared.HandlerRegistration;
import com.gwtplatform.mvp.client.View;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class TableFilterPresenter
        extends AbstractComponentPresenter<TableFilterView> {

    public static final String TAB_TYPE = "TableFilter";
    public static final ComponentType TYPE =
            new ComponentType(1,
                    "table-filter",
                    "Table Filter",
                    ComponentUse.INPUT);

    private final Provider<ColumnValuesFilterPresenter> columnValuesFilterPresenterProvider;
    private final List<HandlerRegistration> registrations = new ArrayList<>();

    @Inject
    public TableFilterPresenter(final EventBus eventBus,
                                final TableFilterView view,
                                final Provider<TableFilterSettingsPresenter> settingsPresenterProvider,
                                final Provider<ColumnValuesFilterPresenter> columnValuesFilterPresenterProvider) {
        super(eventBus, view, settingsPresenterProvider);
        this.columnValuesFilterPresenterProvider = columnValuesFilterPresenterProvider;
    }

    @Override
    protected void onUnbind() {
        super.onUnbind();
        registrations.forEach(HandlerRegistration::removeHandler);
        registrations.clear();
    }

    @Override
    public void read(final ComponentConfig componentConfig) {
        super.read(componentConfig);

        final ComponentSettings settings = componentConfig.getSettings();
        if (!(settings instanceof TableFilterComponentSettings)) {
            setSettings(createSettings());
        }

        update(getTableFilterSettings());
    }

    private void update(final TableFilterComponentSettings settings) {
        getView().getPanel().clear();
        registrations.forEach(HandlerRegistration::removeHandler);
        registrations.clear();

        if (settings.getTableId() != null) {
            final Component component = getDashboardContext()
                    .getComponents()
                    .get(getTableFilterSettings().getTableId());
            if (component instanceof final TablePresenter tablePresenter) {
                final List<Column> columns = NullSafe.getOrElse(
                        tablePresenter,
                        TablePresenter::getTableComponentSettings,
                        TableComponentSettings::getColumns,
                        list -> list
                                .stream()
                                .sorted(Comparator.comparing(Column::getName))
                                .collect(Collectors.toList()),
                        Collections.emptyList());

                final Set<String> included = NullSafe
                        .list(getTableFilterSettings().getColumns())
                        .stream()
                        .map(ColumnRef::getId)
                        .collect(Collectors.toSet());
                for (final Column column : columns) {
                    if (included.contains(column.getId())) {
                        final Label label = new Label(column.getName(), false);
                        label.addStyleName("table-filter-label");

                        final ColumnValuesFilterPresenter columnValuesFilterPresenter =
                                columnValuesFilterPresenterProvider.get();

                        final List<ConditionalFormattingRule> rules =
                                NullSafe.map(settings.getConditionalFormattingRules()).get(column.getId());

                        final Provider<Element> filterButtonProvider = () -> tablePresenter.getFilterButton(column);
                        columnValuesFilterPresenter.init(
                                filterButtonProvider,
                                column,
                                () -> tablePresenter.getDataSupplier(column, rules),
                                column.getColumnValueSelection(),
                                tablePresenter.getColumnsManager(),
                                rules);

                        final FlowPanel panel = new FlowPanel();
                        panel.addStyleName("table-filter-panel");
                        panel.add(label);
                        panel.add(columnValuesFilterPresenter.getWidget());

                        getView().getPanel().add(panel);

                        registrations.add(tablePresenter.addUpdateHandler(e -> columnValuesFilterPresenter.refresh()));
                        columnValuesFilterPresenter.refresh();
                    }
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
    }

    @Override
    public void changeSettings() {
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


    // --------------------------------------------------------------------------------


    public interface TableFilterView extends View {

        FlowPanel getPanel();
    }
}
