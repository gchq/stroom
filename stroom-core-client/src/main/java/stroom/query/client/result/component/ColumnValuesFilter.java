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

package stroom.query.client.result.component;

import stroom.dashboard.shared.ColumnValue;
import stroom.query.api.ColumnValueSelection;
import stroom.query.client.result.table.ColumnValueCell;
import stroom.widget.button.client.LinkButton;
import stroom.widget.component.client.Component;
import stroom.widget.datagrid.client.DataGrid;
import stroom.widget.datagrid.client.DataGridSelectionEventManager;
import stroom.widget.datagrid.client.PagerPanel;
import stroom.widget.quickfilter.client.QuickFilter;
import stroom.widget.task.client.TaskMonitorFactory;
import stroom.widget.util.client.MultiSelectionModelImpl;

import com.google.gwt.core.client.GWT;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.cellview.client.Column;
import com.google.gwt.user.cellview.client.RowStyles;
import com.google.gwt.user.client.ui.Widget;
import com.google.gwt.view.client.AbstractDataProvider;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/// A table column's values, each ticked or not, below Select All and Select None and a filter. In
/// the list the arrows move, and Space, Enter or a press ticks or unticks the value with the focus,
/// Ctrl+A all of them; Escape goes on to the popup it is in, which closes.
public class ColumnValuesFilter extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    LinkButton selectAllLink;
    @UiField
    LinkButton selectNoneLink;
    @UiField
    QuickFilter quickFilter;
    @UiField
    PagerPanel pager;
    @UiField
    DataGrid<ColumnValue> list;

    private Consumer<ColumnValue> onToggle = value -> {
    };
    private Runnable onSelectAll = () -> {
    };
    private Runnable onSelectNone = () -> {
    };
    private Consumer<String> onFilterChange = text -> {
    };

    /// Makes an empty list.
    public ColumnValuesFilter() {
        initWidget(BINDER.createAndBindUi(this));
        selectAllLink.setOnClick(this::onSelectAllLink);
        selectNoneLink.setOnClick(this::onSelectNoneLink);
        quickFilter.setOnChange(text -> onFilterChange.accept(text));

        final DataGridSelectionEventManager<ColumnValue> selectionEventManager =
                new DataGridSelectionEventManager<>(list, new MultiSelectionModelImpl<>(), true);
        selectionEventManager.setChooser(value -> onToggle.accept(value));
        selectionEventManager.addSelectAllHandler(e -> onSelectAll.run());
        // The ticks are the rows' own state, not a selection
        list.setSelectionModel(null, selectionEventManager);
    }

    /// Ticks the values the selection holds, which the owner changes as values are ticked.
    ///
    /// @param selection The selection.
    public void setSelection(final ColumnValueSelection.Builder selection) {
        list.setRowChecked(value -> ColumnValueCell.stateOf(selection, value));
        list.addColumn(new Column<ColumnValue, ColumnValue>(new ColumnValueCell(selection)) {
            @Override
            public ColumnValue getValue(final ColumnValue value) {
                return value;
            }
        }, "", 100);
    }

    /// @param rowStyles Styles each value's row, e.g. as the column's conditional formatting does.
    public void setRowStyles(final RowStyles<ColumnValue> rowStyles) {
        list.setRowStyles(rowStyles);
    }

    /// @param onToggle Given a value to tick or untick (Space, Enter or a press on it).
    public void setOnToggle(final Consumer<ColumnValue> onToggle) {
        this.onToggle = Objects.requireNonNull(onToggle, "onToggle");
    }

    /// @param onSelectAll Run when Select All (or Ctrl+A in the list) is pressed.
    public void setOnSelectAll(final Runnable onSelectAll) {
        this.onSelectAll = Objects.requireNonNull(onSelectAll, "onSelectAll");
    }

    /// @param onSelectNone Run when Select None is pressed.
    public void setOnSelectNone(final Runnable onSelectNone) {
        this.onSelectNone = Objects.requireNonNull(onSelectNone, "onSelectNone");
    }

    /// @param onFilterChange Given the filter's text when it changes.
    public void setOnFilterChange(final Consumer<String> onFilterChange) {
        this.onFilterChange = Objects.requireNonNull(onFilterChange, "onFilterChange");
    }

    /// Puts the focus in the filter.
    public void focus() {
        quickFilter.forceFocus();
    }

    /// Puts the focus on the first value, if there is one.
    public void focusFirstValue() {
        if (list.getVisibleItemCount() > 0) {
            list.setKeyboardSelectedRow(0, true);
        }
    }

    /// Sets the filter's text, without the filter change callback being told.
    ///
    /// @param text The text.
    public void setFilterText(final String text) {
        quickFilter.setText(text, false);
    }

    /// Shows no values.
    public void clear() {
        list.setRowData(0, Collections.emptyList());
        list.setRowCount(0);
    }

    /// @param dataProvider Gives the list its values, a page at a time.
    public void setDataProvider(final AbstractDataProvider<ColumnValue> dataProvider) {
        dataProvider.addDataDisplay(list);
    }

    /// @return The values shown.
    public List<ColumnValue> getVisibleItems() {
        return list.getVisibleItems();
    }

    /// Draws the values again, e.g. once their ticks have changed.
    public void redraw() {
        list.redraw();
    }

    /// @return The task monitor that spins the pager's refresh button.
    public TaskMonitorFactory getTaskMonitor() {
        return pager;
    }

    private void onSelectAllLink() {
        onSelectAll.run();
    }

    private void onSelectNoneLink() {
        onSelectNone.run();
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, ColumnValuesFilter> {

    }
}
