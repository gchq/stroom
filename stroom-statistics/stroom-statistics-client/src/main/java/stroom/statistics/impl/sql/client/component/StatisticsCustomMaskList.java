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

import stroom.cell.tickbox.client.TickBoxCell;
import stroom.cell.tickbox.client.TickBoxState;
import stroom.statistics.impl.sql.client.StatisticFieldsAndMasks.Field;
import stroom.statistics.impl.sql.client.StatisticFieldsAndMasks.Mask;
import stroom.widget.button.client.ToolbarButton;
import stroom.widget.component.client.Component;
import stroom.widget.datagrid.client.DataGrid;
import stroom.widget.datagrid.client.ListDataProvider;
import stroom.widget.util.client.MultiSelectionModelImpl;

import com.google.gwt.core.client.GWT;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.cellview.client.Column;
import com.google.gwt.user.client.ui.Widget;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/// A statistic store's Custom Roll-ups tab: a row per roll-up permutation, with a tick box for each
/// field that it rolls up, and New, Auto-generate and Remove.
public class StatisticsCustomMaskList extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);
    private static final String NEW_TITLE = "New Roll-up Permutation";
    private static final String AUTO_GENERATE_TITLE = "Auto-generate Roll-up Permutations";
    private static final String REMOVE_TITLE = "Remove Selected Roll-up Permutation";

    @UiField
    ToolbarButton newButton;
    @UiField
    ToolbarButton autoGenerateButton;
    @UiField
    ToolbarButton removeButton;
    @UiField
    DataGrid<Mask> dataGrid;

    private final MultiSelectionModelImpl<Mask> selectionModel;
    private final List<Column<Mask, ?>> columns = new ArrayList<>();
    private ListDataProvider<Mask> dataProvider;
    private Runnable onNew = () -> {
    };
    private Runnable onAutoGenerate = () -> {
    };
    private Runnable onRemove = () -> {
    };
    private Runnable onMaskChange = () -> {
    };

    /// Makes an empty list, with no columns until [#setFields].
    public StatisticsCustomMaskList() {
        initWidget(BINDER.createAndBindUi(this));
        selectionModel = dataGrid.addDefaultSelectionModel(true);
        newButton.setOnClick(() -> {
            if (!isReadOnly()) {
                onNew.run();
            }
        });
        autoGenerateButton.setOnClick(() -> {
            if (!isReadOnly()) {
                onAutoGenerate.run();
            }
        });
        removeButton.setOnClick(() -> {
            if (!isReadOnly()) {
                onRemove.run();
            }
        });
        selectionModel.addSelectionHandler(event -> enableButtons());
        enableButtons();
    }

    /// Shows a tick box column for each field, in place of any shown.
    ///
    /// @param fields The store's fields.
    public void setFields(final List<Field> fields) {
        for (final Column<Mask, ?> column : columns) {
            dataGrid.removeColumn(column);
        }
        columns.clear();
        for (final Field field : fields) {
            final Column<Mask, TickBoxState> rolledUpColumn = new Column<Mask, TickBoxState>(
                    TickBoxCell.create(false, true)) {
                @Override
                public TickBoxState getValue(final Mask row) {
                    return TickBoxState.fromBoolean(row.getMask().contains(field));
                }
            };
            rolledUpColumn.setFieldUpdater((index, row, value) -> {
                if (value.toBoolean()) {
                    row.getMask().add(field);
                } else {
                    row.getMask().remove(field);
                }
                onMaskChange.run();
            });
            dataGrid.addResizableColumn(rolledUpColumn, field.getName(), 100);
            columns.add(rolledUpColumn);
        }
    }

    /// @param masks The roll-up permutations.
    public void setMasks(final List<Mask> masks) {
        if (dataProvider == null) {
            dataProvider = new ListDataProvider<>();
            dataProvider.addDataDisplay(dataGrid);
        }
        dataProvider.setCompleteList(masks);
    }

    /// @param name The store's name, for the table's accessible name.
    public void setStoreName(final String name) {
        dataGrid.setTableName("Statistics '" + name + "' Custom Masks");
    }

    /// @return The selection.
    public MultiSelectionModelImpl<Mask> getSelectionModel() {
        return selectionModel;
    }

    /// @param onNew Run when New is pressed (not when read only).
    public void setOnNew(final Runnable onNew) {
        this.onNew = Objects.requireNonNull(onNew, "onNew");
    }

    /// @param onAutoGenerate Run when Auto-generate is pressed (not when read only).
    public void setOnAutoGenerate(final Runnable onAutoGenerate) {
        this.onAutoGenerate = Objects.requireNonNull(onAutoGenerate, "onAutoGenerate");
    }

    /// @param onRemove Run when Remove is pressed (not when read only).
    public void setOnRemove(final Runnable onRemove) {
        this.onRemove = Objects.requireNonNull(onRemove, "onRemove");
    }

    /// @param onMaskChange Run when a field is ticked or unticked in a permutation.
    public void setOnMaskChange(final Runnable onMaskChange) {
        this.onMaskChange = Objects.requireNonNull(onMaskChange, "onMaskChange");
    }

    @Override
    protected void onReadOnlyChange(final boolean readOnly) {
        enableButtons();
    }

    private void enableButtons() {
        final boolean readOnly = isReadOnly();
        newButton.setEnabled(!readOnly);
        autoGenerateButton.setEnabled(!readOnly);
        removeButton.setEnabled(!readOnly && selectionModel.getSelected() != null);
        final String reason = readOnly
                ? " (read only)"
                : "";
        newButton.setTitle(NEW_TITLE + reason);
        autoGenerateButton.setTitle(AUTO_GENERATE_TITLE + reason);
        removeButton.setTitle(REMOVE_TITLE + reason);
    }

    interface Binder extends UiBinder<Widget, StatisticsCustomMaskList> {

    }
}
