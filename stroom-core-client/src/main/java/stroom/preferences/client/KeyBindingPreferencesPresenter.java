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

package stroom.preferences.client;

import stroom.alert.client.event.ConfirmEvent;
import stroom.preferences.client.KeyBindingPreferencesPresenter.KeyBindingPreferencesView;
import stroom.svg.shared.SvgImage;
import stroom.ui.config.shared.UserPreferences;
import stroom.util.client.DataGridUtil;
import stroom.util.shared.NullSafe;
import stroom.widget.button.client.ButtonView;
import stroom.widget.datagrid.client.DataGrid;
import stroom.widget.datagrid.client.PagerView;
import stroom.widget.presenter.client.HasUiHandlers;
import stroom.widget.presenter.client.MyPresenterWidget;
import stroom.widget.presenter.client.View;
import stroom.widget.util.client.KeyBinding;
import stroom.widget.util.client.KeyBinding.Action;
import stroom.widget.util.client.KeyShortcut;
import stroom.widget.util.client.MultiSelectionModelImpl;

import com.google.gwt.user.client.ui.Focus;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/// The Keyboard Shortcuts tab of the user preferences, listing each action the user can bind
/// with its shortcuts, one row per shortcut (as in e.g. VS Code), so that the user can add,
/// change, remove or reset them, and turn character key shortcuts off (WCAG 2.1.4).
public final class KeyBindingPreferencesPresenter
        extends MyPresenterWidget<KeyBindingPreferencesView>
        implements KeyBindingPreferencesUiHandlers {

    private static final String NONE = "None";

    private final KeyShortcutRecorderPresenter keyShortcutRecorderPresenter;
    private final DataGrid<KeyBindingRow> dataGrid;
    private final MultiSelectionModelImpl<KeyBindingRow> selectionModel;
    private final ButtonView addButton;
    private final ButtonView changeButton;
    private final ButtonView removeButton;
    private final ButtonView resetButton;
    private final ButtonView resetAllButton;

    // The user's shortcuts for every configurable action, as being edited
    private Map<Action, List<KeyShortcut>> bindings = KeyBinding.getDefaultBindings();
    private String filter;

    private Runnable onChange = () -> {
    };

    @Inject
    public KeyBindingPreferencesPresenter(final EventBus eventBus,
                                          final KeyBindingPreferencesView view,
                                          final PagerView pagerView,
                                          final KeyShortcutRecorderPresenter keyShortcutRecorderPresenter) {
        super(eventBus, view);
        own(keyShortcutRecorderPresenter);
        this.keyShortcutRecorderPresenter = keyShortcutRecorderPresenter;
        view.setUiHandlers(this);

        dataGrid = new DataGrid<>(this);
        dataGrid.setTableName("Keyboard Shortcuts");
        selectionModel = dataGrid.addDefaultSelectionModel(false);
        pagerView.setDataWidget(dataGrid);
        addColumns();

        pagerView.getToolbar().setName("Keyboard shortcut actions");
        addButton = pagerView.getToolbar().button(SvgImage.ADD, "Add Shortcut", this::onAdd);
        changeButton = pagerView.getToolbar().button(SvgImage.EDIT, "Change Shortcut", this::onChangeShortcut);
        removeButton = pagerView.getToolbar().button(SvgImage.DELETE, "Remove Shortcut", this::onRemove);
        resetButton = pagerView.getToolbar().button(SvgImage.UNDO, "Reset Action to Default Shortcuts",
                this::onResetAction);
        resetAllButton = pagerView.getToolbar().button(SvgImage.HISTORY, "Reset All Shortcuts to Defaults",
                this::onResetAll);
        view.setList(pagerView);
        updateButtons();

        own(selectionModel.addSelectionHandler(event -> {
            updateButtons();
            if (event.getSelectionType().isDoubleSelect()) {
                onChangeShortcut();
            }
        }));
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
                DataGridUtil.textColumnBuilder(this::getStatus)
                        .build(),
                "Status",
                150);
    }

    private String getStatus(final KeyBindingRow row) {
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
        if (!getView().isCharacterKeyShortcutsEnabled() && row.getShortcut().isCharacterKeyShortcut()) {
            return source + ", turned off";
        }
        return source;
    }

    /// Shows the user's keyboard shortcuts.
    ///
    /// @param userPreferences The user's preferences.
    public void read(final UserPreferences userPreferences) {
        bindings = KeyBinding.fromUserBindings(userPreferences.getKeyBindings());
        getView().setCharacterKeyShortcutsEnabled(
                UserPreferencesManager.isCharacterKeyShortcutsEnabled(userPreferences));
        refresh(null);
    }

    /// Sets the user's keyboard shortcuts, as shown, in their preferences.
    ///
    /// @param builder The builder of the user's preferences.
    public void write(final UserPreferences.Builder builder) {
        builder.keyBindings(KeyBinding.toUserBindings(bindings))
                .enableCharacterKeyShortcuts(getView().isCharacterKeyShortcutsEnabled());
    }

    @Override
    public void onChange() {
        // Character key shortcuts turned on or off
        dataGrid.redraw();
        onChange.run();
    }

    @Override
    public void onFilterChange(final String filter) {
        this.filter = filter;
        refresh(selectionModel.getSelected());
    }

    private void onAdd() {
        final KeyBindingRow row = selectionModel.getSelected();
        if (row != null) {
            record(row.getAction(), null);
        }
    }

    private void onChangeShortcut() {
        final KeyBindingRow row = selectionModel.getSelected();
        if (row != null) {
            // A built-in shortcut can't be changed, so add another instead
            record(row.getAction(), row.isFixed()
                    ? null
                    : row.getShortcut());
        }
    }

    private void record(final Action action, final KeyShortcut existing) {
        keyShortcutRecorderPresenter.show(
                action,
                existing,
                bindings,
                getView().isCharacterKeyShortcutsEnabled(),
                (shortcut, clashes) -> {
                    // Move a clashing shortcut from the other actions to this one
                    clashes.forEach((otherAction, clashing) -> bindings.get(otherAction).removeAll(clashing));
                    final List<KeyShortcut> shortcuts = bindings.get(action);
                    final int index = shortcuts.indexOf(existing);
                    if (index >= 0) {
                        shortcuts.remove(index);
                    }
                    if (!shortcuts.contains(shortcut)) {
                        // In the place of the shortcut it replaces, if any
                        if (index >= 0) {
                            shortcuts.add(index, shortcut);
                        } else {
                            shortcuts.add(shortcut);
                        }
                    }
                    onBindingsChange(new KeyBindingRow(action, shortcut, false));
                });
    }

    private void onRemove() {
        final KeyBindingRow row = selectionModel.getSelected();
        if (row != null && !row.isFixed() && row.getShortcut() != null) {
            bindings.get(row.getAction()).remove(row.getShortcut());
            onBindingsChange(new KeyBindingRow(row.getAction(), null, false));
        }
    }

    private void onResetAction() {
        final KeyBindingRow row = selectionModel.getSelected();
        if (row != null) {
            bindings.put(row.getAction(), new ArrayList<>(KeyBinding.getDefaultShortcuts(row.getAction())));
            onBindingsChange(row);
        }
    }

    private void onResetAll() {
        ConfirmEvent.fire(this,
                "Are you sure you want to reset all your keyboard shortcuts to the defaults?",
                ok -> {
                    if (ok) {
                        bindings = KeyBinding.getDefaultBindings();
                        onBindingsChange(selectionModel.getSelected());
                    }
                });
    }

    private void onBindingsChange(final KeyBindingRow rowToSelect) {
        refresh(rowToSelect);
        onChange.run();
    }

    private void refresh(final KeyBindingRow rowToSelect) {
        final List<KeyBindingRow> rows = new ArrayList<>();
        bindings.forEach((action, shortcuts) -> {
            final List<KeyBindingRow> actionRows = new ArrayList<>();
            for (final KeyShortcut shortcut : shortcuts) {
                actionRows.add(new KeyBindingRow(action, shortcut, false));
            }
            for (final KeyShortcut shortcut : KeyBinding.getFixedShortcuts(action)) {
                actionRows.add(new KeyBindingRow(action, shortcut, true));
            }
            if (actionRows.isEmpty()) {
                actionRows.add(new KeyBindingRow(action, null, false));
            }
            actionRows.stream()
                    .filter(this::matchesFilter)
                    .forEach(rows::add);
        });
        dataGrid.setRowData(0, rows);
        dataGrid.setRowCount(rows.size(), true);

        // Keep the selection on the row that was changed, or the first row of its action
        final KeyBindingRow selected = rows.stream()
                .filter(row -> row.equals(rowToSelect))
                .findFirst()
                .orElseGet(() -> rows.stream()
                        .filter(row -> rowToSelect != null && row.getAction() == rowToSelect.getAction())
                        .findFirst()
                        .orElse(null));
        if (selected != null) {
            selectionModel.setSelected(selected);
        } else {
            selectionModel.clear();
        }
        updateButtons();
    }

    private boolean matchesFilter(final KeyBindingRow row) {
        if (NullSafe.isBlankString(filter)) {
            return true;
        }
        final String lowerFilter = filter.trim().toLowerCase();
        return row.getAction().getDisplayName().toLowerCase().contains(lowerFilter)
               || row.getAction().getCategory().getDisplayName().toLowerCase().contains(lowerFilter)
               || (row.getShortcut() != null && row.getShortcut().toString().toLowerCase().contains(lowerFilter));
    }

    private void updateButtons() {
        final KeyBindingRow row = selectionModel.getSelected();
        addButton.setEnabled(row != null);
        changeButton.setEnabled(row != null);
        removeButton.setEnabled(row != null && !row.isFixed() && row.getShortcut() != null);
        resetButton.setEnabled(row != null
                               && !bindings.get(row.getAction())
                .equals(KeyBinding.getDefaultShortcuts(row.getAction())));
        resetAllButton.setEnabled(KeyBinding.toUserBindings(bindings) != null);
    }

    /// @param onChange What to run when the user changes a key binding.
    public void setOnChange(final Runnable onChange) {
        this.onChange = onChange;
    }


    // --------------------------------------------------------------------------------


    public interface KeyBindingPreferencesView
            extends View, Focus, HasUiHandlers<KeyBindingPreferencesUiHandlers> {

        /// @param view The list of shortcuts.
        void setList(View view);

        /// @return Whether character key shortcuts are turned on.
        boolean isCharacterKeyShortcutsEnabled();

        /// @param enabled Whether character key shortcuts are turned on.
        void setCharacterKeyShortcutsEnabled(boolean enabled);
    }


    // --------------------------------------------------------------------------------


    /// A row of the list: one shortcut of an action, or an action with no shortcuts.
    private static final class KeyBindingRow {

        private final Action action;
        private final KeyShortcut shortcut;
        private final boolean fixed;

        private KeyBindingRow(final Action action, final KeyShortcut shortcut, final boolean fixed) {
            this.action = action;
            this.shortcut = shortcut;
            this.fixed = fixed;
        }

        private Action getAction() {
            return action;
        }

        private KeyShortcut getShortcut() {
            return shortcut;
        }

        private boolean isFixed() {
            return fixed;
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            final KeyBindingRow that = (KeyBindingRow) o;
            return fixed == that.fixed
                   && action == that.action
                   && Objects.equals(shortcut, that.shortcut);
        }

        @Override
        public int hashCode() {
            return Objects.hash(action, shortcut, fixed);
        }
    }
}
