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

package stroom.preferences.client.model;

import stroom.widget.util.client.KeyBinding.Action;
import stroom.widget.util.client.KeyShortcut;

import java.util.Objects;

/// A row of the Keyboard Shortcuts tab's list: one shortcut of an action, or an action with no
/// shortcuts.
public final class KeyBindingRow {

    private final Action action;
    private final KeyShortcut shortcut;
    private final boolean fixed;

    /// @param action   The action.
    /// @param shortcut One of its shortcuts, or `null` if it has none.
    /// @param fixed    Whether the shortcut is built in, so can't be changed.
    public KeyBindingRow(final Action action, final KeyShortcut shortcut, final boolean fixed) {
        this.action = action;
        this.shortcut = shortcut;
        this.fixed = fixed;
    }

    /// @return The action.
    public Action getAction() {
        return action;
    }

    /// @return The shortcut, or `null` if the action has none.
    public KeyShortcut getShortcut() {
        return shortcut;
    }

    /// @return Whether the shortcut is built in, so can't be changed.
    public boolean isFixed() {
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

    @Override
    public String toString() {
        return action + " " + shortcut + (fixed
                ? " (fixed)"
                : "");
    }
}
