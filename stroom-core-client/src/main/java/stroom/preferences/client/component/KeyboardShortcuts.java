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

package stroom.preferences.client.component;

import stroom.preferences.client.component.KeyboardShortcutsController.ShortcutRow;
import stroom.util.client.DataGridUtil;
import stroom.widget.component.client.Component;
import stroom.widget.datagrid.client.DataGrid;
import stroom.widget.quickfilter.client.QuickFilter;

import com.google.gwt.core.client.GWT;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Widget;

import java.util.List;
import java.util.function.Consumer;

/// The keyboard shortcuts dialog's content: a filter above the list of shortcuts, each action with
/// its shortcuts and its group.
public class KeyboardShortcuts extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    QuickFilter quickFilter;
    @UiField
    DataGrid<ShortcutRow> dataGrid;

    private Consumer<String> onFilterChange = filter -> {
    };

    /// Makes the content, with no rows.
    public KeyboardShortcuts() {
        initWidget(BINDER.createAndBindUi(this));
        dataGrid.addDefaultSelectionModel(false);
        dataGrid.addResizableColumn(
                DataGridUtil.textColumnBuilder(ShortcutRow::action).build(),
                "Action",
                270);
        dataGrid.addResizableColumn(
                DataGridUtil.textColumnBuilder(ShortcutRow::shortcuts).build(),
                "Shortcut",
                180);
        dataGrid.addResizableColumn(
                DataGridUtil.textColumnBuilder(ShortcutRow::group).build(),
                "Group",
                100);
        quickFilter.setOnChange(text -> onFilterChange.accept(text));
    }

    /// @param rows The rows to show.
    public void setRows(final List<ShortcutRow> rows) {
        dataGrid.setRowData(0, rows);
        dataGrid.setRowCount(rows.size(), true);
    }

    /// Puts the focus in the filter.
    public void focus() {
        quickFilter.focus();
    }

    /// @param onFilterChange Given the filter's text each time it changes.
    public void setOnFilterChange(final Consumer<String> onFilterChange) {
        this.onFilterChange = onFilterChange;
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, KeyboardShortcuts> {

    }
}
