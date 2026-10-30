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

package stroom.pathways.client.component;

import stroom.cell.tickbox.client.TickBoxCell;
import stroom.cell.tickbox.client.TickBoxState;
import stroom.pathways.shared.pathway.Constraint;
import stroom.util.client.DataGridUtil;
import stroom.widget.button.client.ToolbarButton;
import stroom.widget.component.client.Component;
import stroom.widget.datagrid.client.DataGrid;
import stroom.widget.util.client.MultiSelectEvent;
import stroom.widget.util.client.MultiSelectionModelImpl;

import com.google.gwt.core.client.GWT;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.cellview.client.Column;
import com.google.gwt.user.client.ui.Widget;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

/// A pathway node's constraints, by name, a row each (its name, its value's type, its value and
/// whether it is optional), with New, Edit and Delete; several can be selected. Its controller says
/// when the buttons are brought up to date ([#updateActions(boolean)]).
public class ConstraintList extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);
    private static final String READ_ONLY_SUFFIX = " (read only)";

    @UiField
    ToolbarButton newButton;
    @UiField
    ToolbarButton editButton;
    @UiField
    ToolbarButton removeButton;
    @UiField
    DataGrid<Constraint> dataGrid;

    private final String newTitle;
    private final String editTitle;
    private final String removeTitle;
    private final MultiSelectionModelImpl<Constraint> selectionModel;
    private Runnable onNew = () -> {
    };
    private Runnable onEdit = () -> {
    };
    private Runnable onRemove = () -> {
    };
    private Consumer<MultiSelectEvent> onSelectionChange = event -> {
    };

    /// Makes an empty list.
    public ConstraintList() {
        initWidget(BINDER.createAndBindUi(this));
        newTitle = newButton.getTitle();
        editTitle = editButton.getTitle();
        removeTitle = removeButton.getTitle();
        // Rows keyed by name, unique, so an edited row is still the same row
        dataGrid.setKeyProvider(Constraint::getName);
        selectionModel = dataGrid.addDefaultSelectionModel(true);

        newButton.setOnClick(() -> onNew.run());
        editButton.setOnClick(() -> onEdit.run());
        removeButton.setOnClick(() -> onRemove.run());
        addColumns();

        selectionModel.addSelectionHandler(event -> onSelectionChange.accept(event));
    }

    /// Brings the buttons up to date with the selection: New is enabled unless read only, and Edit and
    /// Delete when a constraint is selected; when read only, they say so.
    ///
    /// @param readOnly Whether the constraints are read only.
    public void updateActions(final boolean readOnly) {
        newButton.setEnabled(!readOnly);
        final boolean enabled = !readOnly && selectionModel.getSelected() != null;
        editButton.setEnabled(enabled);
        removeButton.setEnabled(enabled);

        // The names stay, with why the buttons are disabled when read only
        final String suffix = readOnly
                ? READ_ONLY_SUFFIX
                : "";
        newButton.setTitle(newTitle + suffix);
        editButton.setTitle(editTitle + suffix);
        removeButton.setTitle(removeTitle + suffix);
    }

    /// @param constraints The constraints, in order.
    public void setData(final List<Constraint> constraints) {
        dataGrid.setRowData(constraints);
        dataGrid.setRowCount(constraints.size());
    }

    /// @return The selection.
    public MultiSelectionModelImpl<Constraint> getSelectionModel() {
        return selectionModel;
    }

    /// @param onNew Run when New is pressed.
    public void setOnNew(final Runnable onNew) {
        this.onNew = onNew;
    }

    /// @param onEdit Run when Edit is pressed.
    public void setOnEdit(final Runnable onEdit) {
        this.onEdit = onEdit;
    }

    /// @param onRemove Run when Delete is pressed.
    public void setOnRemove(final Runnable onRemove) {
        this.onRemove = onRemove;
    }

    /// @param onSelectionChange Given the selection's event, e.g. a double click, when it changes.
    public void setOnSelectionChange(final Consumer<MultiSelectEvent> onSelectionChange) {
        this.onSelectionChange = onSelectionChange;
    }

    private void addColumns() {
        addTextColumn("Name", 300, Constraint::getName);
        addTextColumn("Type", 100, constraint -> {
            if (constraint == null || constraint.getValue() == null) {
                return null;
            }
            return constraint.getValue().valueType().getDisplayValue();
        });
        addTextColumn("Value", 400, constraint -> {
            if (constraint == null || constraint.getValue() == null) {
                return null;
            }
            return constraint.getValue().toString();
        });
        addOptionalColumn();
    }

    private void addTextColumn(final String name, final int width, final Function<Constraint, String> function) {
        final Column<Constraint, String> column = DataGridUtil.textColumnBuilder(function)
                .withSorting(name)
                .build();
        dataGrid.addResizableColumn(column,
                name,
                width);
    }

    private void addOptionalColumn() {
        final Function<Constraint, TickBoxState> valueExtractor = constraint -> {
            if (constraint == null || constraint.getValue() == null) {
                return null;
            }
            return TickBoxState.fromBoolean(constraint.isOptional());
        };

        final Column<Constraint, TickBoxState> selectionColumn = DataGridUtil.columnBuilder(
                        valueExtractor, () -> TickBoxCell.create(
                                new TickBoxCell.NoBorderAppearance(), true, true, false))
                .centerAligned()
                .build();
        dataGrid.addColumn(selectionColumn,
                "Optional",
                100);
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, ConstraintList> {

    }
}
