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


package stroom.gwt.workbench.framework.client.play;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/// Parses `userEvent.keyboard(...)`/`userEvent.type(...)` key descriptions and knows the
/// properties of the `KeyboardEvent`s each key fires, e.g. `keyCode`, which GWT widgets read.
///
/// The syntax is user-event's:
///
/// * a character types itself, e.g. `abc`
/// * `{Name}` presses and releases a key by its `key` value, e.g. `{Enter}`, `{ArrowDown}`,
///   `{Escape}` or `{Tab}`; user-event 13's lower case names, e.g. `{enter}`, `{esc}`, `{del}` and
///   `{selectall}`, are also understood
/// * `[Code]` does the same by the key's `code`, e.g. `[KeyA]` or `[ShiftLeft]`
/// * `{Name>}` presses and holds a key, e.g. `{Shift>}` or `{Control>}`; `{Name>3}` presses it 3
///   times and holds it; `{Name>3/}` presses it 3 times and releases it
/// * `{/Name}` releases a held key
/// * `{{` and `[[` type a literal `{` and `[`
final class Keys {

    /// The bit for Shift in a modifier mask.
    static final int SHIFT = 1;
    /// The bit for Control in a modifier mask.
    static final int CONTROL = 2;
    /// The bit for Alt in a modifier mask.
    static final int ALT = 4;
    /// The bit for Meta in a modifier mask.
    static final int META = 8;
    /// The pseudo key for user-event 13's `{selectall}`, which selects all the text in a field.
    static final String SELECT_ALL = "{selectall}";

    private static final Map<String, Integer> KEY_CODES = new HashMap<>();
    private static final Map<String, String> ALIASES = new HashMap<>();
    private static final Map<String, String> PUNCTUATION_CODES = new HashMap<>();

    static {
        KEY_CODES.put("Backspace", 8);
        KEY_CODES.put("Tab", 9);
        KEY_CODES.put("Enter", 13);
        KEY_CODES.put("Shift", 16);
        KEY_CODES.put("Control", 17);
        KEY_CODES.put("Alt", 18);
        KEY_CODES.put("Pause", 19);
        KEY_CODES.put("CapsLock", 20);
        KEY_CODES.put("Escape", 27);
        KEY_CODES.put(" ", 32);
        KEY_CODES.put("PageUp", 33);
        KEY_CODES.put("PageDown", 34);
        KEY_CODES.put("End", 35);
        KEY_CODES.put("Home", 36);
        KEY_CODES.put("ArrowLeft", 37);
        KEY_CODES.put("ArrowUp", 38);
        KEY_CODES.put("ArrowRight", 39);
        KEY_CODES.put("ArrowDown", 40);
        KEY_CODES.put("Insert", 45);
        KEY_CODES.put("Delete", 46);
        KEY_CODES.put("Meta", 91);
        KEY_CODES.put("ContextMenu", 93);
        for (int i = 1; i <= 12; i++) {
            KEY_CODES.put("F" + i, 111 + i);
        }
        KEY_CODES.put(";", 186);
        KEY_CODES.put("=", 187);
        KEY_CODES.put(",", 188);
        KEY_CODES.put("-", 189);
        KEY_CODES.put(".", 190);
        KEY_CODES.put("/", 191);
        KEY_CODES.put("`", 192);
        KEY_CODES.put("[", 219);
        KEY_CODES.put("\\", 220);
        KEY_CODES.put("]", 221);
        KEY_CODES.put("'", 222);

        PUNCTUATION_CODES.put(";", "Semicolon");
        PUNCTUATION_CODES.put("=", "Equal");
        PUNCTUATION_CODES.put(",", "Comma");
        PUNCTUATION_CODES.put("-", "Minus");
        PUNCTUATION_CODES.put(".", "Period");
        PUNCTUATION_CODES.put("/", "Slash");
        PUNCTUATION_CODES.put("`", "Backquote");
        PUNCTUATION_CODES.put("[", "BracketLeft");
        PUNCTUATION_CODES.put("\\", "Backslash");
        PUNCTUATION_CODES.put("]", "BracketRight");
        PUNCTUATION_CODES.put("'", "Quote");
        PUNCTUATION_CODES.put(" ", "Space");

        // user-event 13's names, and codes, mapped to key values
        ALIASES.put("enter", "Enter");
        ALIASES.put("esc", "Escape");
        ALIASES.put("escape", "Escape");
        ALIASES.put("tab", "Tab");
        ALIASES.put("space", " ");
        ALIASES.put("backspace", "Backspace");
        ALIASES.put("del", "Delete");
        ALIASES.put("delete", "Delete");
        ALIASES.put("arrowup", "ArrowUp");
        ALIASES.put("arrowdown", "ArrowDown");
        ALIASES.put("arrowleft", "ArrowLeft");
        ALIASES.put("arrowright", "ArrowRight");
        ALIASES.put("home", "Home");
        ALIASES.put("end", "End");
        ALIASES.put("pageup", "PageUp");
        ALIASES.put("pagedown", "PageDown");
        ALIASES.put("shift", "Shift");
        ALIASES.put("ctrl", "Control");
        ALIASES.put("control", "Control");
        ALIASES.put("alt", "Alt");
        ALIASES.put("meta", "Meta");
        ALIASES.put("selectall", SELECT_ALL);
        ALIASES.put("shiftleft", "Shift");
        ALIASES.put("shiftright", "Shift");
        ALIASES.put("controlleft", "Control");
        ALIASES.put("controlright", "Control");
        ALIASES.put("altleft", "Alt");
        ALIASES.put("altright", "Alt");
        ALIASES.put("metaleft", "Meta");
        ALIASES.put("metaright", "Meta");
        ALIASES.put("osleft", "Meta");
        ALIASES.put("osright", "Meta");
    }

    private Keys() {
        // Static utility
    }

    /// @param keys The keys, e.g. `ab{Enter}` or `{Control>}a{/Control}`.
    /// @return What to do with each key, in order.
    static List<KeyAction> parse(final String keys) {
        final List<KeyAction> actions = new ArrayList<>();
        int i = 0;
        while (i < keys.length()) {
            final char chr = keys.charAt(i);
            final char close = chr == '{'
                    ? '}'
                    : ']';
            if ((chr == '{' || chr == '[') && i + 1 < keys.length() && keys.charAt(i + 1) == chr) {
                // {{ or [[ is a literal brace or bracket
                actions.add(new KeyAction(String.valueOf(chr), true, true, 1));
                i += 2;
            } else if ((chr == '{' || chr == '[') && keys.indexOf(close, i) > i + 1) {
                final int end = keys.indexOf(close, i);
                actions.add(parseDescriptor(keys.substring(i + 1, end), chr == '['));
                i = end + 1;
            } else {
                actions.add(new KeyAction(String.valueOf(chr), true, true, 1));
                i++;
            }
        }
        return actions;
    }

    private static KeyAction parseDescriptor(final String descriptor, final boolean byCode) {
        String name = descriptor;
        boolean press = true;
        boolean release = true;
        int repeat = 1;
        if (name.startsWith("/")) {
            name = name.substring(1);
            press = false;
        } else {
            final int hold = name.indexOf('>');
            if (hold > 0) {
                String rest = name.substring(hold + 1);
                name = name.substring(0, hold);
                release = false;
                if (rest.endsWith("/")) {
                    release = true;
                    rest = rest.substring(0, rest.length() - 1);
                }
                if (!rest.isEmpty()) {
                    repeat = parseRepeat(rest);
                }
            }
        }
        final String key = byCode
                ? keyForCode(name)
                : keyForName(name);
        return new KeyAction(key, press, release, repeat);
    }

    private static int parseRepeat(final String text) {
        int value = 0;
        for (int i = 0; i < text.length(); i++) {
            final char chr = text.charAt(i);
            if (chr < '0' || chr > '9') {
                throw new IllegalArgumentException("Invalid key repeat count: " + text);
            }
            value = value * 10 + (chr - '0');
        }
        return Math.max(1, value);
    }

    /// @param name A key name from between braces, e.g. `Enter` or `enter`.
    /// @return The key value, e.g. `Enter`.
    static String keyForName(final String name) {
        if (name.length() == 1 || KEY_CODES.containsKey(name)) {
            return name;
        }
        final String alias = ALIASES.get(name.toLowerCase());
        return alias != null
                ? alias
                : name;
    }

    /// @param code A key code from between brackets, e.g. `KeyA`, `Digit1` or `ShiftLeft`.
    /// @return The key value, e.g. `a`, `1` or `Shift`.
    static String keyForCode(final String code) {
        if (code.length() == 4 && code.startsWith("Key")) {
            return code.substring(3).toLowerCase();
        }
        if (code.length() == 6 && code.startsWith("Digit")) {
            return code.substring(5);
        }
        for (final Map.Entry<String, String> entry : PUNCTUATION_CODES.entrySet()) {
            if (entry.getValue().equals(code)) {
                return entry.getKey();
            }
        }
        return keyForName(code);
    }

    /// @param key A key value, e.g. `a`, `A`, `Enter` or ` `.
    /// @return The legacy `keyCode` browsers give it, e.g. 65 for `a`, which GWT's `KeyCodes`
    /// use; 0 if unknown.
    static int keyCode(final String key) {
        final Integer known = KEY_CODES.get(key);
        if (known != null) {
            return known;
        }
        if (key.length() == 1) {
            final char chr = key.charAt(0);
            if (chr >= 'a' && chr <= 'z') {
                return chr - 'a' + 'A';
            }
            if ((chr >= 'A' && chr <= 'Z') || (chr >= '0' && chr <= '9')) {
                return chr;
            }
        }
        return 0;
    }

    /// @param key A key value.
    /// @return The `code` of the physical key, e.g. `KeyA`, `Digit1`, `Space` or `Enter`.
    static String code(final String key) {
        if (key.length() == 1) {
            final char chr = key.charAt(0);
            if ((chr >= 'a' && chr <= 'z') || (chr >= 'A' && chr <= 'Z')) {
                return "Key" + String.valueOf(chr).toUpperCase();
            }
            if (chr >= '0' && chr <= '9') {
                return "Digit" + chr;
            }
            final String punctuation = PUNCTUATION_CODES.get(key);
            return punctuation != null
                    ? punctuation
                    : "";
        }
        if ("Shift".equals(key) || "Control".equals(key) || "Alt".equals(key) || "Meta".equals(key)) {
            return key + "Left";
        }
        return key;
    }

    /// @param key A key value.
    /// @return The `charCode` of the `keypress` event it fires, e.g. 97 for `a`, 13 for `Enter`;
    /// 0 if it fires none.
    static int charCode(final String key) {
        if (key.length() == 1) {
            return key.charAt(0);
        }
        return "Enter".equals(key)
                ? 13
                : 0;
    }

    /// @param key A key value.
    /// @return The modifier bit for Shift, Control, Alt or Meta, or 0 for other keys.
    static int modifierBit(final String key) {
        switch (key) {
            case "Shift":
                return SHIFT;
            case "Control":
                return CONTROL;
            case "Alt":
                return ALT;
            case "Meta":
                return META;
            default:
                return 0;
        }
    }

    /// @param key       A key value.
    /// @param modifiers The modifiers held, as a mask of [#SHIFT] etc.
    /// @return True if pressing the key fires a `keypress` event, i.e. it types a character (and
    /// no Control, Alt or Meta is held) or it's Enter.
    static boolean firesKeyPress(final String key, final int modifiers) {
        if ("Enter".equals(key)) {
            return true;
        }
        return key.length() == 1 && (modifiers & (CONTROL | ALT | META)) == 0;
    }

    /// @param key       A key value.
    /// @param modifiers The modifiers held, as a mask of [#SHIFT] etc.
    /// @return What the browser does by default when the key is pressed (unless the `keydown` is
    /// cancelled).
    static DefaultAction defaultAction(final String key, final int modifiers) {
        final boolean shortcut = (modifiers & (CONTROL | META)) != 0;
        if (SELECT_ALL.equals(key) || (shortcut && "a".equalsIgnoreCase(key))) {
            return DefaultAction.SELECT_ALL;
        }
        if (shortcut || (modifiers & ALT) != 0) {
            return DefaultAction.NONE;
        }
        switch (key) {
            case "Tab":
                return (modifiers & SHIFT) != 0
                        ? DefaultAction.FOCUS_PREVIOUS
                        : DefaultAction.FOCUS_NEXT;
            case "Enter":
                return DefaultAction.ENTER;
            case "Backspace":
                return DefaultAction.DELETE_BACKWARD;
            case "Delete":
                return DefaultAction.DELETE_FORWARD;
            case "Home":
                return DefaultAction.MOVE_TO_START;
            case "End":
                return DefaultAction.MOVE_TO_END;
            default:
                return key.length() == 1
                        ? DefaultAction.TYPE
                        : DefaultAction.NONE;
        }
    }


    // --------------------------------------------------------------------------------


    /// What a browser does by default when a key is pressed.
    enum DefaultAction {
        /// Nothing.
        NONE,
        /// Types the character into the focused text field.
        TYPE,
        /// Deletes the selection, or the character before the caret.
        DELETE_BACKWARD,
        /// Deletes the selection, or the character after the caret.
        DELETE_FORWARD,
        /// Selects all the text in the focused field.
        SELECT_ALL,
        /// Moves the caret to the start of the field.
        MOVE_TO_START,
        /// Moves the caret to the end of the field.
        MOVE_TO_END,
        /// Moves the focus to the next focusable element.
        FOCUS_NEXT,
        /// Moves the focus to the previous focusable element.
        FOCUS_PREVIOUS,
        /// Clicks a focused button or link, or types a new line in a text area.
        ENTER
    }


    // --------------------------------------------------------------------------------


    /// One key from a key description, e.g. `{Shift>}` presses Shift and holds it.
    static final class KeyAction {

        private final String key;
        private final boolean press;
        private final boolean release;
        private final int repeat;

        /// @param key     The key value, e.g. `a` or `Enter`.
        /// @param press   True to press the key.
        /// @param release True to release the key (after pressing it if `press`).
        /// @param repeat  How many times to press it.
        KeyAction(final String key, final boolean press, final boolean release, final int repeat) {
            this.key = key;
            this.press = press;
            this.release = release;
            this.repeat = repeat;
        }

        /// @return The key value, e.g. `a` or `Enter`.
        String getKey() {
            return key;
        }

        /// @return True to press the key.
        boolean isPress() {
            return press;
        }

        /// @return True to release the key.
        boolean isRelease() {
            return release;
        }

        /// @return How many times to press the key.
        int getRepeat() {
            return repeat;
        }

        @Override
        public String toString() {
            return (press
                    ? ""
                    : "/") + key + (press && !release
                    ? ">" + repeat
                    : "") + (press && release && repeat > 1
                    ? "x" + repeat
                    : "");
        }
    }
}
