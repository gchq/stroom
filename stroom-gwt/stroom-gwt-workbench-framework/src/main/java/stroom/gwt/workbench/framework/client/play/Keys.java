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
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/// Parses `userEvent.keyboard(...)`/`userEvent.type(...)` key descriptions and knows the
/// properties of the `KeyboardEvent`s each key fires, e.g. `keyCode`, which GWT widgets read.
///
/// The syntax and keys are user-event 14's (its `readNextDescriptor`, `parseKeyDef` and default
/// US keyboard map):
///
/// * a character types itself, e.g. `abc`
/// * `{Name}` presses and releases a key by its `key` value, e.g. `{Enter}`, `{ArrowDown}`,
///   `{Escape}` or `{Tab}`, ignoring case, e.g. `{enter}`; a name that isn't on the keyboard is
///   pressed as a key with that `key` and the `code` `Unknown`, e.g. `{esc}` (user-event 13's
///   names aren't understood, as in user-event 14)
/// * `[Code]` does the same by the key's `code`, e.g. `[KeyA]` or `[ShiftRight]`; a code that
///   isn't on the keyboard, e.g. `[Period]`, presses a key `Unknown` with that `code`
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
    /// The `key` (or `code`) user-event gives a key that isn't on its keyboard.
    static final String UNKNOWN = "Unknown";

    private static final Map<String, Integer> KEY_CODES = new HashMap<>();
    // user-event 14's default keyboard map, as [key, code] pairs in its order (the first match
    // wins)
    private static final List<String[]> KEY_MAP = new ArrayList<>();

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

        for (char chr = '0'; chr <= '9'; chr++) {
            map(String.valueOf(chr), "Digit" + chr);
        }
        final String shiftedDigits = ")!@#$%^&*(";
        for (int i = 0; i < shiftedDigits.length(); i++) {
            map(String.valueOf(shiftedDigits.charAt(i)), "Digit" + i);
        }
        for (char chr = 'a'; chr <= 'z'; chr++) {
            map(String.valueOf(chr), "Key" + Character.toUpperCase(chr));
        }
        for (char chr = 'A'; chr <= 'Z'; chr++) {
            map(String.valueOf(chr), "Key" + chr);
        }
        map("[", "BracketLeft");
        map("{", "BracketLeft");
        map("]", "BracketRight");
        map("}", "BracketRight");
        map(" ", "Space");
        map("Alt", "AltLeft");
        map("Alt", "AltRight");
        map("Shift", "ShiftLeft");
        map("Shift", "ShiftRight");
        map("Control", "ControlLeft");
        map("Control", "ControlRight");
        map("Meta", "MetaLeft");
        map("Meta", "MetaRight");
        map("OS", "OSLeft");
        map("OS", "OSRight");
        for (final String named : Arrays.asList("ContextMenu", "Tab", "CapsLock", "Backspace", "Enter", "Escape",
                "ArrowUp", "ArrowDown", "ArrowLeft", "ArrowRight", "Home", "End", "Delete", "PageUp", "PageDown",
                "Fn", "Symbol")) {
            map(named, named);
        }
        map("AltGraph", "AltRight");
    }

    private Keys() {
        // Static utility
    }

    private static void map(final String key, final String code) {
        KEY_MAP.add(new String[]{key, code});
    }

    /// @param keys The keys, e.g. `ab{Enter}` or `{Control>}a{/Control}`.
    /// @return What to do with each key, in order.
    /// @throws IllegalArgumentException If a `{` or `[` isn't closed or is empty, as user-event
    ///                                  throws, e.g. for `{Enter`.
    static List<KeyAction> parse(final String keys) {
        final List<KeyAction> actions = new ArrayList<>();
        int i = 0;
        while (i < keys.length()) {
            final char chr = keys.charAt(i);
            if ((chr == '{' || chr == '[') && i + 1 < keys.length() && keys.charAt(i + 1) == chr) {
                // {{ or [[ is a literal brace or bracket
                actions.add(byCharacter(String.valueOf(chr)));
                i += 2;
            } else if (chr == '{' || chr == '[') {
                final char close = chr == '{'
                        ? '}'
                        : ']';
                final int end = keys.indexOf(close, i);
                if (end < 0) {
                    throw new IllegalArgumentException("Expected \"" + close + "\" after \"" + keys.substring(i)
                                                       + "\" in \"" + keys + "\"");
                }
                final String descriptor = keys.substring(i + 1, end);
                if (descriptor.isEmpty() || "/".equals(descriptor)) {
                    throw new IllegalArgumentException("Expected key descriptor but found \"" + close
                                                       + "\" in \"" + keys + "\"");
                }
                actions.add(parseDescriptor(descriptor, chr == '['));
                i = end + 1;
            } else {
                actions.add(byCharacter(String.valueOf(chr)));
                i++;
            }
        }
        return actions;
    }

    private static KeyAction byCharacter(final String character) {
        return new KeyAction(character, code(character), true, true, 1);
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
        final String[] keyAndCode = byCode
                ? lookUpCode(name)
                : lookUpName(name);
        return new KeyAction(keyAndCode[0], keyAndCode[1], press, release, repeat);
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

    // As user-event's parseKeyDef for {name}: the first key on the keyboard whose key matches,
    // ignoring case, or an unknown key with the name as its key
    private static String[] lookUpName(final String name) {
        for (final String[] keyAndCode : KEY_MAP) {
            if (keyAndCode[0].equalsIgnoreCase(name)) {
                return keyAndCode;
            }
        }
        return new String[]{name, UNKNOWN};
    }

    // As user-event's parseKeyDef for [code]: the first key on the keyboard whose code matches,
    // ignoring case, or an unknown key with the name as its code
    private static String[] lookUpCode(final String code) {
        for (final String[] keyAndCode : KEY_MAP) {
            if (keyAndCode[1].equalsIgnoreCase(code)) {
                return keyAndCode;
            }
        }
        return new String[]{UNKNOWN, code};
    }

    /// @param name A key name from between braces, e.g. `Enter` or `enter`.
    /// @return The key value, e.g. `Enter`; an unknown name is its own key value, as in
    /// user-event, e.g. `{Foo}` presses a key `Foo` whose `code` is `Unknown`.
    static String keyForName(final String name) {
        return lookUpName(name)[0];
    }

    /// @param code A key code from between brackets, e.g. `KeyA`, `Digit1` or `ShiftLeft`, in any
    ///             case, as user-event looks codes up ignoring case.
    /// @return The key value, e.g. `a`, `1` or `Shift`, or `Unknown` for a code that isn't on
    /// user-event's keyboard.
    static String keyForCode(final String code) {
        return lookUpCode(code)[0];
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
    /// @return The `code` user-event 14 gives a typed character or named key, from its US keyboard
    /// map, e.g. `KeyA`, `Digit1` (also for `!`), `BracketLeft` (also for `{`), `Space`,
    /// `ShiftLeft` or `Enter`, or `Unknown` for a key not on it, e.g. `.`, `-`, `F1` or `Foo`.
    static String code(final String key) {
        for (final String[] keyAndCode : KEY_MAP) {
            if (keyAndCode[0].equals(key)) {
                return keyAndCode[1];
            }
        }
        return UNKNOWN;
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
    /// @return True if pressing the key fires a `keypress` event, as user-event 14 decides: it
    /// types a character or it's Enter, and neither Control nor Alt is held (Meta doesn't stop it).
    static boolean firesKeyPress(final String key, final int modifiers) {
        return (key.length() == 1 || "Enter".equals(key)) && (modifiers & (CONTROL | ALT)) == 0;
    }

    /// What user-event 14 does by default when a key's `keydown` isn't cancelled (its keydown
    /// "behaviour"), whatever modifiers are held, except Control with the A key selecting all.
    ///
    /// @param key       A key value.
    /// @param code      The physical key, e.g. `KeyA`.
    /// @param modifiers The modifiers held, as a mask of [#SHIFT] etc.
    /// @return The default action, or [DefaultAction#NONE].
    static DefaultAction keyDownAction(final String key, final String code, final int modifiers) {
        switch (key) {
            case "ArrowDown":
                return DefaultAction.ARROW_DOWN;
            case "ArrowUp":
                return DefaultAction.ARROW_UP;
            case "ArrowLeft":
                return DefaultAction.MOVE_LEFT;
            case "ArrowRight":
                return DefaultAction.MOVE_RIGHT;
            case "Backspace":
                return DefaultAction.DELETE_BACKWARD;
            case "Delete":
                return DefaultAction.DELETE_FORWARD;
            case "End":
                return DefaultAction.MOVE_TO_END;
            case "Home":
                return DefaultAction.MOVE_TO_START;
            case "PageDown":
                return DefaultAction.PAGE_DOWN;
            case "PageUp":
                return DefaultAction.PAGE_UP;
            case "Tab":
                return (modifiers & SHIFT) != 0
                        ? DefaultAction.FOCUS_PREVIOUS
                        : DefaultAction.FOCUS_NEXT;
            default:
                // As user-event, only Control (not Meta) with the A key selects all
                return "KeyA".equals(code) && (modifiers & CONTROL) != 0
                        ? DefaultAction.SELECT_ALL
                        : DefaultAction.NONE;
        }
    }

    /// What user-event 14 does by default when a key's `keypress` (see
    /// [#firesKeyPress(String, int)]) isn't cancelled.
    ///
    /// @param key A key value that fires a `keypress`.
    /// @return [DefaultAction#ENTER] for Enter, otherwise [DefaultAction#TYPE].
    static DefaultAction keyPressAction(final String key) {
        return "Enter".equals(key)
                ? DefaultAction.ENTER
                : DefaultAction.TYPE;
    }

    /// The equivalent of [#keyDownAction(String, String, int)] then, if the key fires a `keypress`,
    /// [#keyPressAction(String)], for describing what pressing a key does.
    ///
    /// @param key       A key value.
    /// @param modifiers The modifiers held, as a mask of [#SHIFT] etc.
    /// @return The keydown's default action, or if it has none, the keypress's, or
    /// [DefaultAction#NONE].
    static DefaultAction defaultAction(final String key, final int modifiers) {
        final DefaultAction keyDown = keyDownAction(key, code(key), modifiers);
        if (keyDown != DefaultAction.NONE) {
            return keyDown;
        }
        return firesKeyPress(key, modifiers)
                ? keyPressAction(key)
                : DefaultAction.NONE;
    }


    // --------------------------------------------------------------------------------


    /// What user-event 14 does by default when a key is pressed (unless the event is cancelled).
    enum DefaultAction {
        /// Nothing.
        NONE,
        /// Types the character into the focused editable element.
        TYPE,
        /// Deletes the selection, or the character before the caret.
        DELETE_BACKWARD,
        /// Deletes the selection, or the character after the caret.
        DELETE_FORWARD,
        /// Selects all the text in the focused field (or content editable element, or page).
        SELECT_ALL,
        /// Moves the caret to the start of the field.
        MOVE_TO_START,
        /// Moves the caret to the end of the field.
        MOVE_TO_END,
        /// In a radio button, checks the previous one in its group; otherwise collapses the
        /// selection to its start, or moves the caret one character left.
        MOVE_LEFT,
        /// In a radio button, checks the next one in its group; otherwise collapses the
        /// selection to its end, or moves the caret one character right.
        MOVE_RIGHT,
        /// In a radio button, checks the previous one in its group.
        ARROW_UP,
        /// In a radio button, checks the next one in its group.
        ARROW_DOWN,
        /// In an input, moves the caret to the start.
        PAGE_UP,
        /// In an input, moves the caret to the end.
        PAGE_DOWN,
        /// Moves the focus to the next focusable element.
        FOCUS_NEXT,
        /// Moves the focus to the previous focusable element.
        FOCUS_PREVIOUS,
        /// Clicks a focused button, link or button-like input, submits the form of a focused
        /// input, or types a new line in an editable element.
        ENTER
    }


    // --------------------------------------------------------------------------------


    /// One key from a key description, e.g. `{Shift>}` presses Shift and holds it.
    static final class KeyAction {

        private final String key;
        private final String code;
        private final boolean press;
        private final boolean release;
        private final int repeat;

        /// @param key     The key value, e.g. `a` or `Enter`.
        /// @param code    The physical key, e.g. `KeyA` or `Enter`.
        /// @param press   True to press the key.
        /// @param release True to release the key (after pressing it if `press`).
        /// @param repeat  How many times to press it.
        KeyAction(final String key, final String code, final boolean press, final boolean release, final int repeat) {
            this.key = key;
            this.code = code;
            this.press = press;
            this.release = release;
            this.repeat = repeat;
        }

        /// @return The key value, e.g. `a` or `Enter`.
        String getKey() {
            return key;
        }

        /// @return The physical key, e.g. `KeyA`, `ShiftRight` or `Unknown`.
        String getCode() {
            return code;
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

        /// @return The action in a compact form for debugging, e.g. `a`, `/Shift` or `Shift>1`.
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
