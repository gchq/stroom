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

import stroom.alert.client.event.ConfirmEvent;
import stroom.preferences.client.UserPreferencesManager;
import stroom.ui.config.shared.UserPreferences;
import stroom.util.shared.NullSafe;
import stroom.widget.component.client.Controller;
import stroom.widget.util.client.KeyBinding;
import stroom.widget.util.client.KeyBinding.Action;
import stroom.widget.util.client.KeyShortcut;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/// The Keyboard Shortcuts tab of the user preferences ([KeyBindingPreferences]), listing each
/// action the user can bind with its shortcuts, one row per shortcut (as in e.g. VS Code), so that
/// the user can add, change, remove or reset them, and turn character key shortcuts off (WCAG
/// 2.1.4).
public final class KeyBindingPreferencesController extends Controller {

    private final KeyBindingPreferences view;
    private final Provider<KeyShortcutRecorderController> keyShortcutRecorderProvider;

    // The user's shortcuts for every configurable action, as being edited
    private Map<Action, List<KeyShortcut>> bindings = KeyBinding.getDefaultBindings();
    private String filter;

    private Runnable onChange = () -> {
    };

    /// Made by GIN, with its content.
    ///
    /// @param eventBus                    The application's event bus.
    /// @param view                        The content.
    /// @param keyShortcutRecorderProvider Makes the dialog that records a shortcut.
    @Inject
    public KeyBindingPreferencesController(final EventBus eventBus,
                                           final KeyBindingPreferences view,
                                           final Provider<KeyShortcutRecorderController> keyShortcutRecorderProvider) {
        super(eventBus);
        this.view = view;
        this.keyShortcutRecorderProvider = keyShortcutRecorderProvider;
        initWidget(view);
        view.setOnAction(this::onAction);
        view.setOnCharacterKeyShortcutsChange(() -> onChange.run());
        view.setOnFilterChange(text -> {
            filter = text;
            refresh(view.getSelected());
        });
        view.setOnSelectionChange(this::updateButtons);
        updateButtons();
    }

    /// Shows the user's keyboard shortcuts.
    ///
    /// @param userPreferences The user's preferences.
    public void read(final UserPreferences userPreferences) {
        bindings = KeyBinding.fromUserBindings(userPreferences.getKeyBindings());
        view.setCharacterKeyShortcutsEnabled(UserPreferencesManager.isCharacterKeyShortcutsEnabled(userPreferences));
        refresh(null);
    }

    /// Sets the user's keyboard shortcuts, as shown, in their preferences.
    ///
    /// @param builder The builder of the user's preferences.
    public void write(final UserPreferences.Builder builder) {
        builder.keyBindings(KeyBinding.toUserBindings(bindings))
                .enableCharacterKeyShortcuts(view.isCharacterKeyShortcutsEnabled());
    }

    /// @param onChange What to run when the user changes a key binding.
    public void setOnChange(final Runnable onChange) {
        this.onChange = onChange;
    }

    /// The rows for every action: one per shortcut the user has, then one per built in shortcut,
    /// or one with no shortcut for an action with none; only those that match the filter.
    ///
    /// @param bindings The user's shortcuts for every configurable action.
    /// @param filter   The filter: part of an action's name, its category's or a shortcut's; or
    ///                 blank for every row.
    /// @return The rows.
    static List<KeyBindingRow> rows(final Map<Action, List<KeyShortcut>> bindings, final String filter) {
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
                    .filter(row -> matchesFilter(row, filter))
                    .forEach(rows::add);
        });
        return rows;
    }

    private static boolean matchesFilter(final KeyBindingRow row, final String filter) {
        if (NullSafe.isBlankString(filter)) {
            return true;
        }
        final String lowerFilter = filter.trim().toLowerCase();
        return row.getAction().getDisplayName().toLowerCase().contains(lowerFilter)
               || row.getAction().getCategory().getDisplayName().toLowerCase().contains(lowerFilter)
               || (row.getShortcut() != null && row.getShortcut().toString().toLowerCase().contains(lowerFilter));
    }

    private void onAction(final KeyBindingAction action) {
        switch (action) {
            case ADD -> onAdd();
            case CHANGE -> onChangeShortcut();
            case REMOVE -> onRemove();
            case RESET -> onResetAction();
            case RESET_ALL -> onResetAll();
        }
    }

    private void onAdd() {
        final KeyBindingRow row = view.getSelected();
        if (row != null) {
            record(row.getAction(), null);
        }
    }

    private void onChangeShortcut() {
        final KeyBindingRow row = view.getSelected();
        if (row != null) {
            // A built-in shortcut can't be changed, so add another instead
            record(row.getAction(), row.isFixed()
                    ? null
                    : row.getShortcut());
        }
    }

    private void record(final Action action, final KeyShortcut existing) {
        keyShortcutRecorderProvider.get().show(this,
                action,
                existing,
                bindings,
                view.isCharacterKeyShortcutsEnabled(),
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
        final KeyBindingRow row = view.getSelected();
        if (row != null && !row.isFixed() && row.getShortcut() != null) {
            bindings.get(row.getAction()).remove(row.getShortcut());
            onBindingsChange(new KeyBindingRow(row.getAction(), null, false));
        }
    }

    private void onResetAction() {
        final KeyBindingRow row = view.getSelected();
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
                        onBindingsChange(view.getSelected());
                    }
                });
    }

    private void onBindingsChange(final KeyBindingRow rowToSelect) {
        refresh(rowToSelect);
        onChange.run();
    }

    private void refresh(final KeyBindingRow rowToSelect) {
        final List<KeyBindingRow> rows = rows(bindings, filter);
        view.setRows(rows);

        // Keep the selection on the row that was changed, or the first row of its action
        final KeyBindingRow selected = rows.stream()
                .filter(row -> row.equals(rowToSelect))
                .findFirst()
                .orElseGet(() -> rows.stream()
                        .filter(row -> rowToSelect != null && row.getAction() == rowToSelect.getAction())
                        .findFirst()
                        .orElse(null));
        view.setSelected(selected);
        updateButtons();
    }

    private void updateButtons() {
        final KeyBindingRow row = view.getSelected();
        view.setActionEnabled(KeyBindingAction.ADD, row != null);
        view.setActionEnabled(KeyBindingAction.CHANGE, row != null);
        view.setActionEnabled(KeyBindingAction.REMOVE, row != null && !row.isFixed() && row.getShortcut() != null);
        view.setActionEnabled(KeyBindingAction.RESET, row != null
                                                      && !bindings.get(row.getAction())
                .equals(KeyBinding.getDefaultShortcuts(row.getAction())));
        view.setActionEnabled(KeyBindingAction.RESET_ALL, KeyBinding.toUserBindings(bindings) != null);
    }
}
