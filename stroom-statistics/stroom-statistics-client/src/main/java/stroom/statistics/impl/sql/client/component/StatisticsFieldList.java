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

import stroom.statistics.impl.sql.client.State.Field;
import stroom.widget.button.client.ToolbarButton;
import stroom.widget.component.client.Component;
import stroom.widget.datagrid.client.DataGrid;
import stroom.widget.util.client.MultiSelectionModelImpl;

import com.google.gwt.cell.client.TextCell;
import com.google.gwt.core.client.GWT;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.cellview.client.Column;
import com.google.gwt.user.client.ui.Widget;

import java.util.List;
import java.util.Objects;

/// A statistic store's Fields tab: its fields' names, with New, Edit and Remove.
public class StatisticsFieldList extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);
    private static final String NEW_TITLE = "New Statistic Field";
    private static final String EDIT_TITLE = "Edit Selected Statistic Field";
    private static final String REMOVE_TITLE = "Remove Selected Statistic Fields";

    @UiField
    ToolbarButton newButton;
    @UiField
    ToolbarButton editButton;
    @UiField
    ToolbarButton removeButton;
    @UiField
    DataGrid<Field> dataGrid;

    private final MultiSelectionModelImpl<Field> selectionModel;
    private Runnable onNew = () -> {
    };
    private Runnable onEdit = () -> {
    };
    private Runnable onRemove = () -> {
    };

    /// Makes an empty list.
    public StatisticsFieldList() {
        initWidget(BINDER.createAndBindUi(this));
        selectionModel = dataGrid.addDefaultSelectionModel(true);
        dataGrid.addResizableColumn(new Column<Field, String>(new TextCell()) {
            @Override
            public String getValue(final Field row) {
                return row.getName();
            }
        }, "Name", 150);

        newButton.setOnClick(() -> {
            if (!isReadOnly()) {
                onNew.run();
            }
        });
        editButton.setOnClick(() -> {
            if (!isReadOnly()) {
                onEdit.run();
            }
        });
        removeButton.setOnClick(() -> {
            if (!isReadOnly()) {
                onRemove.run();
            }
        });
        selectionModel.addSelectionHandler(event -> {
            enableButtons();
            if (event.getSelectionType().isDoubleSelect() && !isReadOnly()) {
                onEdit.run();
            }
        });
        enableButtons();
    }

    /// @param fields The fields, in the order to show them.
    public void setFields(final List<Field> fields) {
        dataGrid.setRowData(0, fields);
        dataGrid.setRowCount(fields.size(), true);
    }

    /// @param name The store's name, for the table's accessible name.
    public void setStoreName(final String name) {
        dataGrid.setTableName("Statistics '" + name + "' Fields");
    }

    /// @return The selection.
    public MultiSelectionModelImpl<Field> getSelectionModel() {
        return selectionModel;
    }

    /// @param onNew Run when New is pressed (not when read only).
    public void setOnNew(final Runnable onNew) {
        this.onNew = Objects.requireNonNull(onNew, "onNew");
    }

    /// @param onEdit Run when Edit is pressed, or a field is double clicked (not when read only).
    public void setOnEdit(final Runnable onEdit) {
        this.onEdit = Objects.requireNonNull(onEdit, "onEdit");
    }

    /// @param onRemove Run when Remove is pressed (not when read only).
    public void setOnRemove(final Runnable onRemove) {
        this.onRemove = Objects.requireNonNull(onRemove, "onRemove");
    }

    @Override
    protected void onReadOnlyChange(final boolean readOnly) {
        enableButtons();
    }

    private void enableButtons() {
        final boolean readOnly = isReadOnly();
        newButton.setEnabled(!readOnly);
        final boolean enabled = !readOnly && selectionModel.getSelected() != null;
        editButton.setEnabled(enabled);
        removeButton.setEnabled(enabled);
        final String reason = readOnly
                ? " (fields are read only)"
                : "";
        newButton.setTitle(NEW_TITLE + reason);
        editButton.setTitle(EDIT_TITLE + reason);
        removeButton.setTitle(REMOVE_TITLE + reason);
    }

    interface Binder extends UiBinder<Widget, StatisticsFieldList> {

    }
}
