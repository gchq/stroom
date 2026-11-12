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
import stroom.svg.shared.SvgImage;
import stroom.widget.button.client.Button;
import stroom.widget.form.client.DescriptionHTML;

import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.TextBox;

import java.util.ArrayList;
import java.util.List;

/// The columns of an External Data Source's data, such as CSV, an Excel sheet or LDAP attributes: each
/// column's name beside a box to choose its type.
///
/// The names of CSV and Excel columns come from the data, so they cannot be changed here. LDAP
/// attributes can also be removed and added by name, as a directory's entries have many attributes and
/// some, such as `modifyTimestamp`, are only returned when asked for by name.
public class DataColumnsPanel extends Composite {

    private static final String DEFAULT_EMPTY_TEXT = "No columns yet. Add the data, then click Detect Columns.";

    private final FlowPanel panel = new FlowPanel();
    private final DescriptionHTML note = new DescriptionHTML();
    private final FlowPanel rows = new FlowPanel();
    private final FlowPanel addRow = new FlowPanel();
    private final TextBox newName = new TextBox();
    private final Button addButton = new Button();
    private final List<ColumnRow> columnRows = new ArrayList<>();
    private Runnable changeHandler = () -> {
    };
    private boolean enabled = true;
    private boolean namesEditable;
    private String emptyText = DEFAULT_EMPTY_TEXT;

    /// Creates the panel, with no columns.
    public DataColumnsPanel() {
        newName.setWidth("250px");
        newName.getElement().setAttribute("placeholder", "Attribute name");
        newName.getElement().setAttribute("aria-label", "Name of the attribute to add");
        newName.addKeyDownHandler(event -> {
            if (event.getNativeKeyCode() == KeyCodes.KEY_ENTER) {
                addColumn();
            }
        });
        addButton.setText("Add Attribute");
        addButton.setIcon(SvgImage.ADD);
        addButton.addStyleName("allow-focus");
        addButton.addClickHandler(event -> addColumn());
        addRow.setStyleName("button-group");
        addRow.add(newName);
        addRow.add(addButton);
        addRow.setVisible(false);

        panel.add(note);
        panel.add(rows);
        panel.add(addRow);
        initWidget(panel);
        setColumns(List.of());
    }

    /// @param changeHandler Called when the user changes a column's type, or removes or adds one.
    public void setChangeHandler(final Runnable changeHandler) {
        this.changeHandler = changeHandler;
    }

    /// Lets the user remove columns and add them by name, as LDAP attributes need.
    ///
    /// @param namesEditable True to show a Remove button on each column and a box to add one by name.
    /// @param emptyText     What to say when there are no columns.
    public void setNamesEditable(final boolean namesEditable, final String emptyText) {
        this.namesEditable = namesEditable;
        this.emptyText = emptyText;
        addRow.setVisible(namesEditable);
        // Spaced apart only when there are buttons to keep apart, so CSV and Excel look as they did.
        panel.setStyleName("form-section", namesEditable);
        setColumns(getColumns());
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
            addRow(column);
        }
        updateNote();
    }

    private void addRow(final DataColumn column) {
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
        row.setStyleName(namesEditable
                ? "button-group"
                : "dock-container-horizontal");
        row.add(name);
        row.add(type);
        final ColumnRow columnRow = new ColumnRow(column.getName(), type);
        if (namesEditable) {
            final Button remove = new Button();
            remove.setText("Remove");
            remove.setIcon(SvgImage.DELETE);
            remove.addStyleName("allow-focus");
            remove.setTitle("Remove attribute " + column.getName());
            remove.setEnabled(enabled);
            remove.addClickHandler(event -> removeColumn(columnRow));
            row.add(remove);
        }
        rows.add(row);
        columnRows.add(columnRow);
    }

    private void removeColumn(final ColumnRow columnRow) {
        final List<DataColumn> columns = new ArrayList<>();
        for (final ColumnRow row : columnRows) {
            if (row != columnRow) {
                columns.add(new DataColumn(row.name(), row.type().getValue()));
            }
        }
        setColumns(columns);
        changeHandler.run();
    }

    // A name already listed, whatever its case, is not added again, as LDAP names ignore case.
    private void addColumn() {
        final String name = newName.getValue() == null
                ? ""
                : newName.getValue().trim();
        if (name.isEmpty() || !enabled) {
            return;
        }
        for (final ColumnRow row : columnRows) {
            if (row.name().equalsIgnoreCase(name)) {
                newName.setValue("");
                return;
            }
        }
        addRow(new DataColumn(name, DataColumnType.TEXT));
        newName.setValue("");
        updateNote();
        changeHandler.run();
    }

    private void updateNote() {
        final String text = columnRows.isEmpty()
                ? emptyText
                : null;
        note.setText(text);
        note.setVisible(text != null);
    }

    /// @param enabled False to stop the columns being changed, e.g. when the document is read-only.
    public void setEnabled(final boolean enabled) {
        this.enabled = enabled;
        newName.setEnabled(enabled);
        addButton.setEnabled(enabled);
        setColumns(getColumns());
    }


    // --------------------------------------------------------------------------------


    // A column shown, with the box its type is chosen in.
    private record ColumnRow(String name, SelectionBox<DataColumnType> type) {

    }
}
