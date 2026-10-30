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

package stroom.widget.util.client;

import stroom.util.shared.NullSafe;

import com.google.gwt.event.dom.client.KeyCodes;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/// One press of a key, with the modifier keys held down at the time, e.g. `Ctrl+Shift+S`.
///
/// Its text form, given by [#toString()] and read by [#parse(String)], lists the modifiers in the
/// order `Ctrl`, `Alt`, `Shift`, `Meta`, then the key, joined with `+`. That form is what is shown
/// to the user and what is saved in their preferences.
public final class KeyStroke {

    private static final String SEPARATOR = "+";
    private static final String CTRL = "Ctrl";
    private static final String ALT = "Alt";
    private static final String SHIFT = "Shift";
    private static final String META = "Meta";

    // Key codes of punctuation keys on a UK/US keyboard, which GWT's KeyCodes doesn't have
    private static final int KEY_SEMICOLON = 186;
    private static final int KEY_EQUALS = 187;
    private static final int KEY_COMMA = 188;
    private static final int KEY_DASH = 189;
    private static final int KEY_PERIOD = 190;
    private static final int KEY_SLASH = 191;
    private static final int KEY_BACKTICK = 192;
    private static final int KEY_BACKSLASH = 220;
    private static final int KEY_SINGLE_QUOTE = 222;
    // Firefox gives these key codes for three of the keys above
    private static final int KEY_FF_SEMICOLON = 59;
    private static final int KEY_FF_EQUALS = 61;
    private static final int KEY_FF_DASH = 173;

    // Key codes of keys that aren't letters, digits or function keys, with their names
    private static final Map<Integer, String> KEY_CODE_TO_NAME = new HashMap<>();
    private static final Map<String, Integer> NAME_TO_KEY_CODE = new HashMap<>();

    static {
        addKeyName(KeyCodes.KEY_UP, "Up");
        addKeyName(KeyCodes.KEY_DOWN, "Down");
        addKeyName(KeyCodes.KEY_LEFT, "Left");
        addKeyName(KeyCodes.KEY_RIGHT, "Right");
        addKeyName(KeyCodes.KEY_PAGEUP, "PageUp");
        addKeyName(KeyCodes.KEY_PAGEDOWN, "PageDown");
        addKeyName(KeyCodes.KEY_HOME, "Home");
        addKeyName(KeyCodes.KEY_END, "End");
        addKeyName(KeyCodes.KEY_INSERT, "Insert");
        addKeyName(KeyCodes.KEY_DELETE, "Delete");
        addKeyName(KeyCodes.KEY_BACKSPACE, "Backspace");
        addKeyName(KeyCodes.KEY_TAB, "Tab");
        addKeyName(KeyCodes.KEY_ENTER, "Enter");
        addKeyName(KeyCodes.KEY_MAC_ENTER, "MacEnter");
        addKeyName(KeyCodes.KEY_ESCAPE, "Esc");
        addKeyName(KeyCodes.KEY_SPACE, "Space");
        addKeyName(KeyCodes.KEY_CONTEXT_MENU, "ContextMenu");
        addKeyName(KeyCodes.KEY_SHIFT, SHIFT);
        addKeyName(KeyCodes.KEY_CTRL, CTRL);
        addKeyName(KeyCodes.KEY_ALT, ALT);
        addKeyName(KeyCodes.KEY_WIN_KEY_LEFT_META, META);
        addKeyName(KEY_SEMICOLON, ";");
        addKeyName(KEY_EQUALS, "=");
        addKeyName(KEY_COMMA, ",");
        addKeyName(KEY_DASH, "-");
        addKeyName(KEY_PERIOD, ".");
        addKeyName(KEY_SLASH, "/");
        addKeyName(KEY_BACKTICK, "`");
        addKeyName(KeyCodes.KEY_OPEN_BRACKET, "[");
        addKeyName(KEY_BACKSLASH, "\\");
        addKeyName(KeyCodes.KEY_CLOSE_BRACKET, "]");
        addKeyName(KEY_SINGLE_QUOTE, "'");
    }

    private final int keyCode;
    private final boolean ctrl;
    private final boolean alt;
    private final boolean shift;
    private final boolean meta;

    /// @param keyCode The key code of the key, as given by a key event.
    /// @param ctrl    Whether the Ctrl key is held down.
    /// @param alt     Whether the Alt key is held down.
    /// @param shift   Whether the Shift key is held down.
    /// @param meta    Whether the Meta (Command or Windows) key is held down.
    public KeyStroke(final int keyCode,
                     final boolean ctrl,
                     final boolean alt,
                     final boolean shift,
                     final boolean meta) {
        this.keyCode = normaliseKeyCode(keyCode);
        this.ctrl = ctrl;
        this.alt = alt;
        this.shift = shift;
        this.meta = meta;
    }

    /// @param keyCode The key code of a key pressed without any modifier keys.
    /// @return The key stroke.
    public static KeyStroke of(final int keyCode) {
        return new KeyStroke(keyCode, false, false, false, false);
    }

    /// @return A builder for a key stroke.
    public static Builder builder() {
        return new Builder();
    }

    /// Reads the text form of a key stroke, e.g. `Ctrl+Shift+S`. Modifier and key names are
    /// not case-sensitive.
    ///
    /// @param text The text form of the key stroke.
    /// @return The key stroke, or null if the text isn't a key stroke.
    public static KeyStroke parse(final String text) {
        if (NullSafe.isBlankString(text)) {
            return null;
        }
        final String trimmed = text.trim();
        // The key itself may be '+' so split on the last '+' that isn't the final character
        final List<String> parts = new ArrayList<>();
        int start = 0;
        for (int i = 0; i < trimmed.length() - 1; i++) {
            if (trimmed.charAt(i) == '+' && i > start) {
                parts.add(trimmed.substring(start, i));
                start = i + 1;
            }
        }
        parts.add(trimmed.substring(start));

        final Builder builder = builder();
        for (int i = 0; i < parts.size() - 1; i++) {
            final String modifier = parts.get(i).trim();
            if (CTRL.equalsIgnoreCase(modifier)) {
                builder.ctrl();
            } else if (ALT.equalsIgnoreCase(modifier)) {
                builder.alt();
            } else if (SHIFT.equalsIgnoreCase(modifier)) {
                builder.shift();
            } else if (META.equalsIgnoreCase(modifier)) {
                builder.meta();
            } else {
                return null;
            }
        }
        final int keyCode = keyNameToKeyCode(parts.get(parts.size() - 1).trim());
        if (keyCode <= 0) {
            return null;
        }
        return builder.keyCode(keyCode).build();
    }

    /// @param keyCode The key code of a key.
    /// @return The name of the key, e.g. `S`, `F1`, `PageUp` or `/`.
    public static String keyCodeToName(final int keyCode) {
        final int normalised = normaliseKeyCode(keyCode);
        if (isLetter(normalised) || isDigit(normalised)) {
            return String.valueOf((char) normalised);
        } else if (isFunctionKey(normalised)) {
            return "F" + (normalised - KeyCodes.KEY_F1 + 1);
        } else {
            final String name = KEY_CODE_TO_NAME.get(normalised);
            return name != null
                    ? name
                    : "Key" + normalised;
        }
    }

    private static int keyNameToKeyCode(final String name) {
        if (NullSafe.isEmptyString(name)) {
            return -1;
        }
        if (name.length() == 1) {
            final char chr = Character.toUpperCase(name.charAt(0));
            if (isLetter(chr) || isDigit(chr)) {
                return chr;
            }
        }
        final String upper = name.toUpperCase();
        if (upper.length() > 1 && upper.charAt(0) == 'F') {
            final int number = parseNumber(upper.substring(1));
            if (number >= 1 && number <= 12) {
                return KeyCodes.KEY_F1 + number - 1;
            }
        }
        if (upper.startsWith("KEY")) {
            return parseNumber(upper.substring(3));
        }
        final Integer keyCode = NAME_TO_KEY_CODE.get(upper);
        return keyCode != null
                ? keyCode
                : -1;
    }

    private static int parseNumber(final String text) {
        if (text.isEmpty()) {
            return -1;
        }
        for (int i = 0; i < text.length(); i++) {
            if (!Character.isDigit(text.charAt(i))) {
                return -1;
            }
        }
        return Integer.parseInt(text);
    }

    private static void addKeyName(final int keyCode, final String name) {
        KEY_CODE_TO_NAME.put(keyCode, name);
        NAME_TO_KEY_CODE.put(name.toUpperCase(), keyCode);
    }

    /// Browsers differ in the key codes they give for a few keys, e.g. Firefox gives 59 for the
    /// semicolon key where others give 186, so this gives the same key code for each.
    ///
    /// @param keyCode The key code from a key event.
    /// @return The key code that the other browsers give for the key.
    static int normaliseKeyCode(final int keyCode) {
        //noinspection EnhancedSwitchMigration // cos GWT
        switch (keyCode) {
            case KEY_FF_SEMICOLON:
                return KEY_SEMICOLON;
            case KEY_FF_EQUALS:
                return KEY_EQUALS;
            case KEY_FF_DASH:
                return KEY_DASH;
            case KeyCodes.KEY_WIN_KEY:
                return KeyCodes.KEY_WIN_KEY_LEFT_META;
            default:
                return keyCode;
        }
    }

    private static boolean isLetter(final int keyCode) {
        return keyCode >= KeyCodes.KEY_A && keyCode <= KeyCodes.KEY_Z;
    }

    private static boolean isDigit(final int keyCode) {
        return keyCode >= KeyCodes.KEY_ZERO && keyCode <= KeyCodes.KEY_NINE;
    }

    private static boolean isFunctionKey(final int keyCode) {
        return keyCode >= KeyCodes.KEY_F1 && keyCode <= KeyCodes.KEY_F12;
    }

    /// @return The key code of the key.
    public int getKeyCode() {
        return keyCode;
    }

    /// @return Whether the Ctrl key is held down.
    public boolean isCtrl() {
        return ctrl;
    }

    /// @return Whether the Shift key is held down.
    public boolean isShift() {
        return shift;
    }

    /// @return Whether any of the modifier keys Ctrl, Alt or Meta are held down. Shift on its own
    /// doesn't count, as it only changes the character typed.
    public boolean hasCommandModifier() {
        return ctrl || alt || meta;
    }

    /// @return Whether any modifier key, including Shift, is held down.
    public boolean hasAnyModifier() {
        return ctrl || alt || shift || meta;
    }

    /// @return Whether the key is itself a modifier key, e.g. the Shift key.
    public boolean isModifierKey() {
        return keyCode == KeyCodes.KEY_SHIFT
               || keyCode == KeyCodes.KEY_CTRL
               || keyCode == KeyCodes.KEY_ALT
               || keyCode == KeyCodes.KEY_WIN_KEY_LEFT_META
               || keyCode == KeyCodes.KEY_WIN_KEY_RIGHT;
    }

    /// Whether this is a character key shortcut, as defined by WCAG 2.1.4, i.e. a key that types
    /// a letter, digit, punctuation or symbol (with or without Shift), pressed without Ctrl, Alt
    /// or Meta. Speech input users can trigger these by accident when they dictate text.
    ///
    /// @return Whether this is a character key shortcut.
    public boolean isCharacterKey() {
        if (hasCommandModifier()) {
            return false;
        }
        return isLetter(keyCode)
               || isDigit(keyCode)
               || keyCode == KeyCodes.KEY_SPACE
               || (keyCode >= KeyCodes.KEY_NUM_ZERO && keyCode <= KeyCodes.KEY_NUM_DIVISION)
               || (KEY_CODE_TO_NAME.containsKey(keyCode) && KEY_CODE_TO_NAME.get(keyCode).length() == 1);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final KeyStroke that = (KeyStroke) o;
        return keyCode == that.keyCode
               && ctrl == that.ctrl
               && alt == that.alt
               && shift == that.shift
               && meta == that.meta;
    }

    @Override
    public int hashCode() {
        return Objects.hash(keyCode, ctrl, alt, shift, meta);
    }

    /// @return The text form of the key stroke, e.g. `Ctrl+Shift+S`.
    @Override
    public String toString() {
        final List<String> parts = new ArrayList<>();
        if (ctrl) {
            parts.add(CTRL);
        }
        if (alt) {
            parts.add(ALT);
        }
        if (shift) {
            parts.add(SHIFT);
        }
        if (meta) {
            parts.add(META);
        }
        parts.add(keyCodeToName(keyCode));
        return String.join(SEPARATOR, parts);
    }


    // --------------------------------------------------------------------------------


    /// Builds a [KeyStroke].
    public static final class Builder {

        private int keyCode;
        private boolean ctrl;
        private boolean alt;
        private boolean shift;
        private boolean meta;

        private Builder() {
        }

        /// @param keyCode The key code of the key.
        /// @return This builder.
        public Builder keyCode(final int keyCode) {
            this.keyCode = keyCode;
            return this;
        }

        /// @param ctrl Whether the Ctrl key is held down.
        /// @return This builder.
        public Builder ctrl(final boolean ctrl) {
            this.ctrl = ctrl;
            return this;
        }

        /// Hold down the Ctrl key.
        ///
        /// @return This builder.
        public Builder ctrl() {
            return ctrl(true);
        }

        /// @param alt Whether the Alt key is held down.
        /// @return This builder.
        public Builder alt(final boolean alt) {
            this.alt = alt;
            return this;
        }

        /// Hold down the Alt key.
        ///
        /// @return This builder.
        public Builder alt() {
            return alt(true);
        }

        /// @param shift Whether the Shift key is held down.
        /// @return This builder.
        public Builder shift(final boolean shift) {
            this.shift = shift;
            return this;
        }

        /// Hold down the Shift key.
        ///
        /// @return This builder.
        public Builder shift() {
            return shift(true);
        }

        /// @param meta Whether the Meta key is held down.
        /// @return This builder.
        public Builder meta(final boolean meta) {
            this.meta = meta;
            return this;
        }

        /// Hold down the Meta key.
        ///
        /// @return This builder.
        public Builder meta() {
            return meta(true);
        }

        /// @return The key stroke.
        public KeyStroke build() {
            return new KeyStroke(keyCode, ctrl, alt, shift, meta);
        }
    }
}
