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

package stroom.gwt.workbench.framework.client.shortcuts;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

/// The current key combination for each [ShortcutAction], which the user can change on the
/// keyboard shortcuts page. An action's shortcut can also be cleared, leaving it without one.
///
/// Some combinations can be reserved for fixed shortcuts that the user can't change, e.g. the
/// zoom tool's, so that no action can be given them.
public class Shortcuts {

    /// The local storage key the manager keeps the user's shortcuts under, in the form
    /// [#save()] returns.
    public static final String STORAGE_KEY = "wbm-shortcuts";

    // A new line as no key is named with one
    private static final String ENTRY_SEPARATOR = "\n";
    private static final String VALUE_SEPARATOR = "=";

    // A null value means the user has cleared the action's shortcut
    private final Map<ShortcutAction, KeyCombo> combos = new EnumMap<>(ShortcutAction.class);
    private final Map<KeyCombo, String> reserved = new HashMap<>();

    /// Creates shortcuts with the default key combinations.
    public Shortcuts() {
        restoreDefaults();
    }

    /// Resets every shortcut to its default key combination.
    public void restoreDefaults() {
        combos.clear();
        for (final ShortcutAction action : ShortcutAction.values()) {
            combos.put(action, action.getDefaultCombo());
        }
        resolveConflicts();
    }

    /// Reserves a key combination for a fixed shortcut so that no action can use it. Any action
    /// already using it goes back to its default (or is cleared if that is reserved too).
    ///
    /// @param combo The key combination.
    /// @param label The name of the fixed shortcut, e.g. `Zoom in`, reported as the conflict when
    ///              the user tries to use the combination.
    public void reserve(final KeyCombo combo, final String label) {
        reserved.put(combo, label);
        resolveConflicts();
    }

    /// @param combo A key combination.
    /// @return True if the combination is reserved for a fixed shortcut.
    public boolean isReserved(final KeyCombo combo) {
        return reserved.containsKey(combo);
    }

    /// @param action An action.
    /// @return The key combination for the action, or null if the user has cleared it.
    public KeyCombo get(final ShortcutAction action) {
        return combos.get(action);
    }

    /// Changes the key combination for an action.
    ///
    /// @param action The action.
    /// @param combo  The new key combination.
    /// @return The label of the other action or fixed shortcut already using the combination, or
    /// null if it isn't used. The combination is only changed if it isn't already used.
    public String set(final ShortcutAction action, final KeyCombo combo) {
        final String reservedBy = reserved.get(combo);
        if (reservedBy != null) {
            return reservedBy;
        }
        final ShortcutAction existing = find(combo);
        if (existing != null && existing != action) {
            return existing.getLabel();
        }
        combos.put(action, combo);
        return null;
    }

    /// Removes the key combination for an action so it has no shortcut.
    ///
    /// @param action The action.
    public void clear(final ShortcutAction action) {
        combos.put(action, null);
    }

    /// @param combo A key combination.
    /// @return The action triggered by the combination, or null if there isn't one.
    public ShortcutAction find(final KeyCombo combo) {
        if (combo == null) {
            return null;
        }
        for (final Entry<ShortcutAction, KeyCombo> entry : combos.entrySet()) {
            if (combo.equals(entry.getValue())) {
                return entry.getKey();
            }
        }
        return null;
    }

    /// @return The combinations that differ from the defaults, in a form [#load(String)] accepts,
    /// one per line, e.g. `fullScreen=alt+G`. A cleared shortcut has no combination, e.g.
    /// `fullScreen=`.
    public String save() {
        final StringBuilder sb = new StringBuilder();
        for (final Entry<ShortcutAction, KeyCombo> entry : combos.entrySet()) {
            if (!entry.getKey().getDefaultCombo().equals(entry.getValue())) {
                if (sb.length() > 0) {
                    sb.append(ENTRY_SEPARATOR);
                }
                sb.append(entry.getKey().getId()).append(VALUE_SEPARATOR);
                if (entry.getValue() != null) {
                    sb.append(entry.getValue());
                }
            }
        }
        return sb.toString();
    }

    /// Applies combinations previously returned by [#save()]. Unknown actions and invalid
    /// combinations are ignored. All the entries are applied before checking for conflicts, so
    /// that e.g. two swapped shortcuts are kept. Entries that still conflict with another
    /// shortcut or a reserved combination go back to their defaults.
    ///
    /// @param saved The saved combinations, may be null.
    public void load(final String saved) {
        combos.clear();
        for (final ShortcutAction action : ShortcutAction.values()) {
            combos.put(action, action.getDefaultCombo());
        }
        if (saved != null && !saved.isEmpty()) {
            for (final String entry : saved.split(ENTRY_SEPARATOR)) {
                applySavedEntry(entry);
            }
        }
        resolveConflicts();
    }

    private void applySavedEntry(final String entry) {
        final int index = entry.indexOf(VALUE_SEPARATOR);
        if (index <= 0) {
            return;
        }
        final ShortcutAction action = ShortcutAction.fromId(entry.substring(0, index));
        if (action == null) {
            return;
        }
        final String value = entry.substring(index + 1);
        if (value.isEmpty()) {
            combos.put(action, null);
        } else {
            combos.put(action, KeyCombo.parse(value));
        }
    }

    /// Makes sure no two actions share a combination and no action uses a reserved one, by
    /// putting changed actions back to their defaults (and clearing any default that is
    /// reserved). The conflicts are found before any are fixed, so every changed action in a
    /// conflict is reverted, whatever the order of the actions. Each pass moves shortcuts
    /// towards their defaults, so this always finishes.
    private void resolveConflicts() {
        boolean changed = true;
        while (changed) {
            final Map<ShortcutAction, KeyCombo> fixes = new EnumMap<>(ShortcutAction.class);
            for (final ShortcutAction action : ShortcutAction.values()) {
                final KeyCombo combo = combos.get(action);
                if (combo == null || !isInConflict(action, combo)) {
                    continue;
                }
                if (!combo.equals(action.getDefaultCombo())) {
                    fixes.put(action, action.getDefaultCombo());
                } else if (reserved.containsKey(combo)) {
                    fixes.put(action, null);
                }
                // Otherwise the action has its default and the other action will be reverted
            }
            combos.putAll(fixes);
            changed = !fixes.isEmpty();
        }
    }

    private boolean isInConflict(final ShortcutAction action, final KeyCombo combo) {
        if (reserved.containsKey(combo)) {
            return true;
        }
        for (final Entry<ShortcutAction, KeyCombo> entry : combos.entrySet()) {
            if (entry.getKey() != action && combo.equals(entry.getValue())) {
                return true;
            }
        }
        return false;
    }
}
