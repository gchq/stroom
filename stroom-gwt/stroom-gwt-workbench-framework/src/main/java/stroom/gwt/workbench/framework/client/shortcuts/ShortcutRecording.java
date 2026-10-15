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

import java.util.Set;

/// The rules for recording a new shortcut on the keyboard shortcuts page: what each key press
/// does while a shortcut field has the focus.
public final class ShortcutRecording {

    private static final String TAB = "Tab";
    private static final String ESCAPE = "Escape";
    private static final Set<String> CLEAR_KEYS = Set.of("Backspace", "Delete");
    // Keys used to move around or activate things, which can't be a shortcut without a modifier
    private static final Set<String> NAVIGATION_KEYS = Set.of(
            "Enter", "NumpadEnter", "Space", "ArrowUp", "ArrowDown", "ArrowLeft", "ArrowRight",
            "Home", "End", "PageUp", "PageDown", "ContextMenu");

    private ShortcutRecording() {
        // Static utility
    }

    /// @param combo The keys pressed in a shortcut field.
    /// @return What the key press does.
    public static Outcome classify(final KeyCombo combo) {
        final String key = combo.getKey();
        if (TAB.equals(key)) {
            // Never trap the focus in the field
            return Outcome.PASS_THROUGH;
        }
        if (!combo.isPlainKey() || combo.isShift()) {
            if (combo.isPlainKey() && NAVIGATION_KEYS.contains(key)) {
                // e.g. shift+ArrowUp, which selects text
                return Outcome.IGNORE;
            }
            return Outcome.RECORD;
        }
        if (ESCAPE.equals(key)) {
            return Outcome.CANCEL;
        }
        if (CLEAR_KEYS.contains(key)) {
            return Outcome.CLEAR;
        }
        if (NAVIGATION_KEYS.contains(key)) {
            return Outcome.IGNORE;
        }
        return Outcome.RECORD;
    }


    // --------------------------------------------------------------------------------


    /// What a key press in a shortcut field does.
    public enum Outcome {
        /// Let the browser handle it, e.g. Tab moves the focus out of the field.
        PASS_THROUGH,
        /// Stop recording and keep the previous shortcut, i.e. Escape.
        CANCEL,
        /// Remove the shortcut, i.e. Backspace or Delete.
        CLEAR,
        /// Not a valid shortcut, e.g. a plain arrow key, so nothing happens.
        IGNORE,
        /// Use the keys as the new shortcut.
        RECORD
    }
}
