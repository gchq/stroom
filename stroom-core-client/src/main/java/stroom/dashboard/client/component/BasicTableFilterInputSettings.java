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

package stroom.dashboard.client.component;

import stroom.dashboard.client.main.DashboardPane;
import stroom.widget.component.client.Component;
import stroom.widget.form.client.TextBox;
import stroom.widget.selection.client.SelectionBox;
import stroom.widget.text.client.Label;

import com.google.gwt.core.client.GWT;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Widget;

import java.util.List;

/// A Table Filter pane's Basic settings: its id and name, the table or embedded query it filters,
/// and the columns to show value filters for.
public class BasicTableFilterInputSettings extends Component implements BasicSettingsFields {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    Label id;
    @UiField
    TextBox name;
    @UiField
    SelectionBox<DashboardPane> table;
    @UiField
    ColumnSelection columns;

    private Runnable onTableChange = () -> {
    };

    /// Makes the tab, empty.
    public BasicTableFilterInputSettings() {
        initWidget(BINDER.createAndBindUi(this));
        table.addValueChangeHandler(event -> onTableChange.run());
    }

    @Override
    public void focus() {
        name.setFocus(true);
    }

    @Override
    public void setId(final String id) {
        this.id.setText(id);
    }

    @Override
    public String getName() {
        return name.getText();
    }

    @Override
    public void setName(final String name) {
        this.name.setText(name);
    }

    /// Lists the tables to choose from, keeping the one chosen.
    ///
    /// @param tableList The dashboard's tables and embedded queries.
    public void setTableList(final List<DashboardPane> tableList) {
        final DashboardPane table = getTable();

        this.table.clear();
        this.table.addItems(tableList);

        // Reselect table id.
        setTable(table);
    }

    /// @return The table chosen, if any.
    public DashboardPane getTable() {
        return this.table.getValue();
    }

    /// @param table The table to show as chosen.
    public void setTable(final DashboardPane table) {
        this.table.setValue(table);
    }

    /// @return The columns to tick, for a controller to fill.
    public ColumnSelection getColumns() {
        return columns;
    }

    /// @param onTableChange Run when the user chooses another table.
    public void setOnTableChange(final Runnable onTableChange) {
        this.onTableChange = onTableChange;
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, BasicTableFilterInputSettings> {

    }
}
