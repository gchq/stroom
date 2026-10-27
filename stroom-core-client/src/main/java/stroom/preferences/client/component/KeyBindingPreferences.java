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

import stroom.util.client.DataGridUtil;
import stroom.widget.button.client.ToolbarButton;
import stroom.widget.component.client.Component;
import stroom.widget.datagrid.client.DataGrid;
import stroom.widget.datagrid.client.PagerPanel;
import stroom.widget.dropdowntree.client.view.QuickFilter;
import stroom.widget.tickbox.client.view.TickBox;
import stroom.widget.util.client.KeyBinding;
import stroom.widget.util.client.MultiSelectionModelImpl;

import com.google.gwt.core.client.GWT;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Widget;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/// The Keyboard Shortcuts tab of the user preferences: whether character key shortcuts are on, a
/// filter, and each action the user can bind with its shortcuts, a row per shortcut (as in e.g. VS
/// Code), with buttons ([KeyBindingAction]) to add, change, remove or reset them.
public class KeyBindingPreferences extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);
    private static final String NONE = "None";

    @UiField
    TickBox enableCharacterKeyShortcuts;
    @UiField
    QuickFilter quickFilter;
    @UiField
    PagerPanel pager;
    @UiField
    ToolbarButton addButton;
    @UiField
    ToolbarButton changeButton;
    @UiField
    ToolbarButton removeButton;
    @UiField
    ToolbarButton resetButton;
    @UiField
    ToolbarButton resetAllButton;
    @UiField
    DataGrid<KeyBindingRow> dataGrid;

    private final MultiSelectionModelImpl<KeyBindingRow> selectionModel;
    private final Map<KeyBindingAction, ToolbarButton> buttons = new EnumMap<>(KeyBindingAction.class);
    private Consumer<KeyBindingAction> onAction = action -> {
    };
    private Runnable onCharacterKeyShortcutsChange = () -> {
    };
    private Consumer<String> onFilterChange = filter -> {
    };
    private Runnable onSelectionChange = () -> {
    };

    /// Makes the tab, with no rows.
    public KeyBindingPreferences() {
        initWidget(BINDER.createAndBindUi(this));
        selectionModel = dataGrid.addDefaultSelectionModel(false);
        addColumns();
        buttons.put(KeyBindingAction.ADD, addButton);
        buttons.put(KeyBindingAction.CHANGE, changeButton);
        buttons.put(KeyBindingAction.REMOVE, removeButton);
        buttons.put(KeyBindingAction.RESET, resetButton);
        buttons.put(KeyBindingAction.RESET_ALL, resetAllButton);
        buttons.forEach((action, button) -> button.setOnClick(() -> onAction.accept(action)));
        enableCharacterKeyShortcuts.addValueChangeHandler(event -> {
            // Redrawn, for the shortcuts turned off or on
            dataGrid.redraw();
            onCharacterKeyShortcutsChange.run();
        });
        quickFilter.addValueChangeHandler(event -> onFilterChange.accept(event.getValue()));
        selectionModel.addSelectionHandler(event -> {
            onSelectionChange.run();
            if (event.getSelectionType().isDoubleSelect()) {
                onAction.accept(KeyBindingAction.CHANGE);
            }
        });
    }

    /// The status of a row's shortcut: built in, the default or changed, and whether it is turned
    /// off (a character key shortcut, while they are off).
    ///
    /// @param row                          The row.
    /// @param characterKeyShortcutsEnabled Whether character key shortcuts are on.
    /// @return The status, or nothing for an action with no shortcut.
    static String status(final KeyBindingRow row, final boolean characterKeyShortcutsEnabled) {
        final String source;
        if (row.isFixed()) {
            source = "Built in";
        } else if (row.getShortcut() == null) {
            return "";
        } else if (KeyBinding.getDefaultShortcuts(row.getAction()).contains(row.getShortcut())) {
            source = "Default";
        } else {
            source = "Changed";
        }
        if (!characterKeyShortcutsEnabled && row.getShortcut().isCharacterKeyShortcut()) {
            return source + ", turned off";
        }
        return source;
    }

    /// @return Whether character key shortcuts are turned on.
    public boolean isCharacterKeyShortcutsEnabled() {
        return enableCharacterKeyShortcuts.getValue();
    }

    /// @param enabled Whether character key shortcuts are turned on.
    public void setCharacterKeyShortcutsEnabled(final boolean enabled) {
        enableCharacterKeyShortcuts.setValue(enabled);
    }

    /// @param rows The rows to show.
    public void setRows(final List<KeyBindingRow> rows) {
        dataGrid.setRowData(0, rows);
        dataGrid.setRowCount(rows.size(), true);
    }

    /// @return The row selected, or `null`.
    public KeyBindingRow getSelected() {
        return selectionModel.getSelected();
    }

    /// @param row The row to select, or `null` to select none.
    public void setSelected(final KeyBindingRow row) {
        if (row != null) {
            selectionModel.setSelected(row);
        } else {
            selectionModel.clear();
        }
    }

    /// @param action  An action.
    /// @param enabled Whether its button can be pressed.
    public void setActionEnabled(final KeyBindingAction action, final boolean enabled) {
        buttons.get(action).setEnabled(enabled);
    }

    /// @param onAction Given the action whose button is pressed, or `CHANGE` when a row is double
    ///                 clicked.
    public void setOnAction(final Consumer<KeyBindingAction> onAction) {
        this.onAction = onAction;
    }

    /// @param onCharacterKeyShortcutsChange Run when character key shortcuts are turned on or off.
    public void setOnCharacterKeyShortcutsChange(final Runnable onCharacterKeyShortcutsChange) {
        this.onCharacterKeyShortcutsChange = onCharacterKeyShortcutsChange;
    }

    /// @param onFilterChange Given the filter's text when it changes.
    public void setOnFilterChange(final Consumer<String> onFilterChange) {
        this.onFilterChange = onFilterChange;
    }

    /// @param onSelectionChange Run when another row is selected.
    public void setOnSelectionChange(final Runnable onSelectionChange) {
        this.onSelectionChange = onSelectionChange;
    }

    private void addColumns() {
        dataGrid.addResizableColumn(
                DataGridUtil.textColumnBuilder((KeyBindingRow row) -> row.getAction().getDisplayName())
                        .build(),
                "Action",
                270);
        dataGrid.addResizableColumn(
                DataGridUtil.textColumnBuilder((KeyBindingRow row) -> row.getShortcut() == null
                                ? NONE
                                : row.getShortcut().toString())
                        .build(),
                "Shortcut",
                140);
        dataGrid.addResizableColumn(
                DataGridUtil.textColumnBuilder((KeyBindingRow row) -> status(row, isCharacterKeyShortcutsEnabled()))
                        .build(),
                "Status",
                150);
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, KeyBindingPreferences> {

    }
}
