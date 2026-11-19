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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/// A key with optional modifiers, e.g. `alt+F` or `control+shift+ArrowUp`, as used by keyboard
/// shortcuts.
///
/// Keys are named as in [#fromKeyCode(String)], e.g. `F`, `1`, `,`, `ArrowUp`, `F6`.
public final class KeyCombo {

    private static final String SEPARATOR = "+";
    private static final String ALT = "alt";
    private static final String CONTROL = "control";
    private static final String SHIFT = "shift";
    private static final String META = "meta";

    private final boolean alt;
    private final boolean control;
    private final boolean shift;
    private final boolean meta;
    private final String key;

    /// @param alt     True if the alt (option) key is held.
    /// @param control True if the control key is held.
    /// @param shift   True if the shift key is held.
    /// @param meta    True if the meta (command/windows) key is held.
    /// @param key     The name of the key, e.g. `F` or `ArrowUp`.
    public KeyCombo(final boolean alt,
                    final boolean control,
                    final boolean shift,
                    final boolean meta,
                    final String key) {
        this.alt = alt;
        this.control = control;
        this.shift = shift;
        this.meta = meta;
        this.key = Objects.requireNonNull(key);
    }

    /// Parses a combo from its string form, e.g. `alt+F` or `control+shift+,`.
    ///
    /// @param value The string form, as created by [#toString()].
    /// @return The combo.
    /// @throws IllegalArgumentException If the value has no key.
    public static KeyCombo parse(final String value) {
        if (value == null || value.isEmpty()) {
            throw new IllegalArgumentException("No key combination");
        }
        boolean alt = false;
        boolean control = false;
        boolean shift = false;
        boolean meta = false;
        String remaining = value;
        boolean foundModifier = true;
        while (foundModifier) {
            foundModifier = false;
            for (final String modifier : List.of(ALT, CONTROL, SHIFT, META)) {
                final String prefix = modifier + SEPARATOR;
                if (remaining.startsWith(prefix) && remaining.length() > prefix.length()) {
                    remaining = remaining.substring(prefix.length());
                    alt |= ALT.equals(modifier);
                    control |= CONTROL.equals(modifier);
                    shift |= SHIFT.equals(modifier);
                    meta |= META.equals(modifier);
                    foundModifier = true;
                }
            }
        }
        return new KeyCombo(alt, control, shift, meta, remaining);
    }

    /// Converts a DOM `KeyboardEvent.code` into the name of the key, ignoring the keyboard layout
    /// and modifiers, e.g. `KeyF` => `F`, `Digit1` => `1`, `Comma` => `,`.
    ///
    /// @param code The `code` of a keyboard event.
    /// @return The name of the key, or null for a modifier key on its own or an unknown code.
    public static String fromKeyCode(final String code) {
        if (code == null || code.isEmpty()) {
            return null;
        }
        if (code.startsWith("Key") && code.length() == 4) {
            return code.substring(3);
        }
        if (code.startsWith("Digit") && code.length() == 6) {
            return code.substring(5);
        }
        if (code.startsWith("Numpad") && code.length() == 7 && Character.isDigit(code.charAt(6))) {
            return code.substring(6);
        }
        switch (code) {
            case "Comma":
                return ",";
            case "Period":
                return ".";
            case "Slash":
                return "/";
            case "Minus":
            case "NumpadSubtract":
                return "-";
            case "Equal":
            case "NumpadAdd":
                return "=";
            case "Semicolon":
                return ";";
            case "Quote":
                return "'";
            case "BracketLeft":
                return "[";
            case "BracketRight":
                return "]";
            case "Backslash":
                return "\\";
            case "Backquote":
                return "`";
            case "AltLeft":
            case "AltRight":
            case "ControlLeft":
            case "ControlRight":
            case "ShiftLeft":
            case "ShiftRight":
            case "MetaLeft":
            case "MetaRight":
                return null;
            default:
                // ArrowUp, F6, Escape, Enter etc.
                return code;
        }
    }

    /// @return True if this has no modifiers other than shift, e.g. `1`, so shouldn't be
    /// triggered while the user is typing.
    public boolean isPlainKey() {
        return !alt && !control && !meta;
    }

    /// @return True if the shift key is held.
    public boolean isShift() {
        return shift;
    }

    /// @return The parts to show the user, e.g. `[alt, F]` or `[⌃, ⇧, ↑]`, as Storybook does.
    public List<String> getDisplayParts() {
        final List<String> parts = new ArrayList<>();
        if (meta) {
            parts.add("⌘");
        }
        if (control) {
            parts.add("⌃");
        }
        if (alt) {
            parts.add("alt");
        }
        if (shift) {
            parts.add("⇧");
        }
        parts.add(displayKey(key));
        return parts;
    }

    private static String displayKey(final String key) {
        switch (key) {
            case "ArrowUp":
                return "↑";
            case "ArrowDown":
                return "↓";
            case "ArrowLeft":
                return "←";
            case "ArrowRight":
                return "→";
            case "Escape":
                return "esc";
            case "Enter":
                return "enter";
            case "Space":
                return "space";
            case "=":
                // The key is shown by what it does when shifted, as Storybook does for zoom in
                return "+";
            default:
                return key;
        }
    }

    /// @return The display parts joined with spaces, e.g. `alt F`.
    public String toDisplayString() {
        return String.join(" ", getDisplayParts());
    }

    /// @return The name of the key, e.g. `F`.
    public String getKey() {
        return key;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final KeyCombo keyCombo = (KeyCombo) o;
        return alt == keyCombo.alt
               && control == keyCombo.control
               && shift == keyCombo.shift
               && meta == keyCombo.meta
               && key.equals(keyCombo.key);
    }

    @Override
    public int hashCode() {
        return Objects.hash(alt, control, shift, meta, key);
    }

    /// @return The string form, e.g. `alt+F`, which [#parse(String)] accepts.
    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        if (alt) {
            sb.append(ALT).append(SEPARATOR);
        }
        if (control) {
            sb.append(CONTROL).append(SEPARATOR);
        }
        if (shift) {
            sb.append(SHIFT).append(SEPARATOR);
        }
        if (meta) {
            sb.append(META).append(SEPARATOR);
        }
        sb.append(key);
        return sb.toString();
    }
}
