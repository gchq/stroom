/*
 * Copyright 2026 Crown Copyright
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

package stroom.externaldatasource.client.view;

import stroom.externaldatasource.shared.DataColumn;
import stroom.externaldatasource.shared.DataColumnType;
import stroom.item.client.SelectionBox;
import stroom.widget.form.client.DescriptionHTML;

import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Label;

import java.util.ArrayList;
import java.util.List;

/// The columns of data held in a document, such as CSV or an Excel sheet: each column's name beside
/// a box to choose its type.
public class DataColumnsPanel extends Composite {

    private final DescriptionHTML note = new DescriptionHTML();
    private final FlowPanel rows = new FlowPanel();
    private final List<ColumnRow> columnRows = new ArrayList<>();
    private Runnable changeHandler = () -> {
    };
    private boolean enabled = true;

    /// Creates the panel, with no columns.
    public DataColumnsPanel() {
        final FlowPanel panel = new FlowPanel();
        panel.add(note);
        panel.add(rows);
        initWidget(panel);
        setColumns(List.of());
    }

    /// @param changeHandler Called when the user changes a column's type.
    public void setChangeHandler(final Runnable changeHandler) {
        this.changeHandler = changeHandler;
    }

    /// @return The columns, with the types chosen.
    public List<DataColumn> getColumns() {
        final List<DataColumn> columns = new ArrayList<>(columnRows.size());
        for (final ColumnRow row : columnRows) {
            columns.add(new DataColumn(row.name(), row.type().getValue()));
        }
        return columns;
    }

    /// @param columns The columns to show, or `null` for none. A column with no type is shown as Text.
    public void setColumns(final List<DataColumn> columns) {
        rows.clear();
        columnRows.clear();
        final List<DataColumn> shown = columns == null
                ? List.of()
                : columns;
        for (final DataColumn column : shown) {
            final Label name = new Label(column.getName());
            name.setWidth("250px");
            final SelectionBox<DataColumnType> type = new SelectionBox<>();
            type.addItems(DataColumnType.values());
            type.setValue(column.getType() == null
                    ? DataColumnType.TEXT
                    : column.getType());
            type.setWidth("150px");
            type.setEnabled(enabled);
            // The name is a plain label beside the box, so the box carries it for screen readers.
            type.setAriaLabel("Type of column " + column.getName());
            type.addValueChangeHandler(event -> changeHandler.run());
            final FlowPanel row = new FlowPanel();
            row.setStyleName("dock-container-horizontal");
            row.add(name);
            row.add(type);
            rows.add(row);
            columnRows.add(new ColumnRow(column.getName(), type));
        }
        final String text = shown.isEmpty()
                ? "No columns yet. Add the data, then click Detect Columns."
                : null;
        note.setText(text);
        note.setVisible(text != null);
    }

    /// @param enabled False to stop the types being changed, e.g. when the document is read-only.
    public void setEnabled(final boolean enabled) {
        this.enabled = enabled;
        for (final ColumnRow row : columnRows) {
            row.type().setEnabled(enabled);
        }
    }


    // --------------------------------------------------------------------------------


    // A column shown, with the box its type is chosen in.
    private record ColumnRow(String name, SelectionBox<DataColumnType> type) {

    }
}
