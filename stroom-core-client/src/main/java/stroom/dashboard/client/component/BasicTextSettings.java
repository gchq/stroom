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
import stroom.docref.HasDisplayValue;
import stroom.item.client.SelectionBox;
import stroom.query.api.ColumnRef;
import stroom.widget.component.client.Component;
import stroom.widget.dropdowntree.client.DropDownBox;
import stroom.widget.form.client.TextBox;
import stroom.widget.tickbox.client.view.TickBox;

import com.google.gwt.core.client.GWT;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.Widget;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/// A Text pane's Basic settings: its id and name, the table (or Any) whose selected row it shows,
/// the columns that locate the row's data, the pipeline that processes it (a picker its controller
/// attaches), and whether it is shown as HTML and can be stepped.
public class BasicTextSettings extends Component implements BasicSettingsFields {

    private static final Binder BINDER = GWT.create(Binder.class);

    private static final HasDisplayValue ANY = () -> "Any";
    private static final HasDisplayValue NONE = () -> "";

    @UiField
    Label id;
    @UiField
    TextBox name;
    @UiField
    SelectionBox<HasDisplayValue> table;
    @UiField
    SelectionBox<HasDisplayValue> streamIdColumn;
    @UiField
    SelectionBox<HasDisplayValue> partNoColumn;
    @UiField
    SelectionBox<HasDisplayValue> recordNoColumn;
    @UiField
    SelectionBox<HasDisplayValue> lineFromColumn;
    @UiField
    SelectionBox<HasDisplayValue> colFromColumn;
    @UiField
    SelectionBox<HasDisplayValue> lineToColumn;
    @UiField
    SelectionBox<HasDisplayValue> colToColumn;
    @UiField
    DropDownBox pipeline;
    @UiField
    TickBox showAsHtml;
    @UiField
    TickBox showStepping;

    private Runnable onTableChange = () -> {
    };

    /// Makes the tab, empty.
    public BasicTextSettings() {
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

    /// Lists the tables to choose from, after Any, keeping the one chosen.
    ///
    /// @param tableList The dashboard's tables and embedded queries.
    public void setTableList(final List<DashboardPane> tableList) {
        final HasDisplayValue table = this.table.getValue();

        this.table.clear();
        this.table.addItem(ANY);

        final List<HasDisplayValue> newList = tableList.stream()
                .map(e ->
                        (HasDisplayValue) e)
                .collect(Collectors.toList());
        this.table.addItems(newList);

        // Reselect table id.
        this.table.setValue(table);
    }

    /// @return The table chosen, or `null` for Any.
    public DashboardPane getTable() {
        if (ANY.equals(this.table.getValue())) {
            return null;
        }
        return (DashboardPane) this.table.getValue();
    }

    /// @param table The table to show as chosen, or `null` for Any.
    public void setTable(final DashboardPane table) {
        if (table == null) {
            this.table.setValue(ANY);
        } else {
            this.table.setValue(table);
        }
    }

    /// Lists the columns to choose from in each of the column boxes, after a blank, keeping the ones
    /// chosen.
    ///
    /// @param columns The columns of the table chosen, or of all the tables.
    public void setColumns(final List<ColumnRef> columns) {
        setFieldNames(columns, streamIdColumn);
        setFieldNames(columns, partNoColumn);
        setFieldNames(columns, recordNoColumn);
        setFieldNames(columns, lineFromColumn);
        setFieldNames(columns, colFromColumn);
        setFieldNames(columns, lineToColumn);
        setFieldNames(columns, colToColumn);
    }

    private void setFieldNames(final List<ColumnRef> columns, final SelectionBox<HasDisplayValue> ctrl) {
        final HasDisplayValue selected = ctrl.getValue();
        ctrl.clear();
        ctrl.addItem(NONE);
        final List<HasDisplayValue> newList = columns.stream().map(e -> (HasDisplayValue) e)
                .sorted(Comparator.comparing(HasDisplayValue::getDisplayValue))
                .collect(Collectors.toList());
        ctrl.addItems(newList);
        ctrl.setValue(selected);
    }

    /// @return The column that holds the stream's id, or `null` for none.
    public ColumnRef getStreamIdColumn() {
        return getColumn(streamIdColumn);
    }

    /// @param column The column that holds the stream's id, or `null` for none.
    public void setStreamIdColumn(final ColumnRef column) {
        streamIdColumn.setValue(column);
    }

    /// @return The column that holds the part number, or `null` for none.
    public ColumnRef getPartNoColumn() {
        return getColumn(partNoColumn);
    }

    /// @param column The column that holds the part number, or `null` for none.
    public void setPartNoColumn(final ColumnRef column) {
        partNoColumn.setValue(column);
    }

    /// @return The column that holds the record number, or `null` for none.
    public ColumnRef getRecordNoColumn() {
        return getColumn(recordNoColumn);
    }

    /// @param column The column that holds the record number, or `null` for none.
    public void setRecordNoColumn(final ColumnRef column) {
        recordNoColumn.setValue(column);
    }

    /// @return The column that holds the line the text to highlight starts on, or `null` for none.
    public ColumnRef getLineFromColumn() {
        return getColumn(lineFromColumn);
    }

    /// @param column The column that holds the line the text to highlight starts on, or `null` for none.
    public void setLineFromColumn(final ColumnRef column) {
        lineFromColumn.setValue(column);
    }

    /// @return The column that holds the character the text to highlight starts at, or `null` for none.
    public ColumnRef getColFromColumn() {
        return getColumn(colFromColumn);
    }

    /// @param column The column that holds the character the text to highlight starts at, or `null` for none.
    public void setColFromColumn(final ColumnRef column) {
        colFromColumn.setValue(column);
    }

    /// @return The column that holds the line the text to highlight ends on, or `null` for none.
    public ColumnRef getLineToColumn() {
        return getColumn(lineToColumn);
    }

    /// @param column The column that holds the line the text to highlight ends on, or `null` for none.
    public void setLineToColumn(final ColumnRef column) {
        lineToColumn.setValue(column);
    }

    /// @return The column that holds the character the text to highlight ends at, or `null` for none.
    public ColumnRef getColToColumn() {
        return getColumn(colToColumn);
    }

    /// @param column The column that holds the character the text to highlight ends at, or `null` for none.
    public void setColToColumn(final ColumnRef column) {
        colToColumn.setValue(column);
    }

    private ColumnRef getColumn(final SelectionBox<HasDisplayValue> ctrl) {
        if (ctrl.getValue() == null || NONE.equals(ctrl.getValue())) {
            return null;
        }
        return (ColumnRef) ctrl.getValue();
    }

    /// @return The pipeline's picker, for a controller to attach.
    public DropDownBox getPipelineBox() {
        return pipeline;
    }

    /// @return Whether the data is shown as HTML.
    public boolean isShowAsHtml() {
        return this.showAsHtml.getValue();
    }

    /// @param showAsHtml Whether the data is shown as HTML.
    public void setShowAsHtml(final boolean showAsHtml) {
        this.showAsHtml.setValue(showAsHtml);
    }

    /// @return Whether the pane has a button to step the record.
    public boolean isShowStepping() {
        return this.showStepping.getValue();
    }

    /// @param showStepping Whether the pane has a button to step the record.
    public void setShowStepping(final boolean showStepping) {
        this.showStepping.setValue(showStepping);
    }

    /// @param onTableChange Run when the user chooses another table.
    public void setOnTableChange(final Runnable onTableChange) {
        this.onTableChange = onTableChange;
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, BasicTextSettings> {

    }
}
