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

import stroom.gwt.workbench.framework.client.play.Keys.DefaultAction;
import stroom.gwt.workbench.framework.client.play.Keys.KeyAction;

import com.google.gwt.dom.client.Element;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/// Presses keys on the focused element as `userEvent.keyboard` (user-event 14's
/// `KeyboardHost`) does:
///
/// * pressing a key that is already held releases it first;
/// * a press fires `keydown` and, unless it is cancelled, does the key's keydown default (see
///   [Keys#keyDownAction(String, String, int)], e.g. moving the focus with Tab or deleting with
///   Backspace), then for a character or Enter (without Control or Alt) fires `keypress` and,
///   unless that is cancelled, does its default (typing, or for Enter clicking a button or
///   submitting a form);
/// * a release fires `keyup` and, for Space, if neither the `keydown` nor the `keyup` was
///   cancelled, clicks a focused button, check box, radio button etc.;
/// * a release of a key that isn't held does nothing.
final class Keyboard {

    private Keyboard() {
        // Static utility
    }

    /// @param root       Any element in the document, used if nothing has the focus.
    /// @param actions    The keys, from [Keys#parse(String)].
    /// @param releaseAll True to release the keys still held at the end, as `userEvent.type`
    ///                   does (`userEvent.keyboard` doesn't).
    static void press(final Element root, final List<KeyAction> actions, final boolean releaseAll) {
        Dom.prepareDocument(root);
        final State state = new State();
        for (final KeyAction action : actions) {
            final KeyAction held = state.pressed.get(action.getCode());
            if (held != null) {
                keyUp(root, held, state);
            }
            if (action.isPress()) {
                for (int i = 0; i < action.getRepeat(); i++) {
                    keyDown(root, action, state);
                }
                if (action.isRelease()) {
                    keyUp(root, action, state);
                }
            }
        }
        if (releaseAll) {
            for (final KeyAction held : new ArrayList<>(state.pressed.values())) {
                keyUp(root, held, state);
            }
        }
    }

    private static void keyDown(final Element root, final KeyAction action, final State state) {
        final String key = action.getKey();
        final String code = action.getCode();
        if (!state.pressed.containsKey(code)) {
            state.pressed.put(code, action);
            state.unprevented.put(code, false);
        }
        state.updateModifiers();
        final boolean unprevented = Dom.keyEvent(root, "keydown", key, code, Keys.keyCode(key), 0,
                state.modifiers);
        if (!unprevented) {
            return;
        }
        state.unprevented.put(code, true);
        final DefaultAction keyDownAction = Keys.keyDownAction(key, code, state.modifiers);
        if (keyDownAction != DefaultAction.NONE) {
            Dom.keyDefault(root, keyDownAction.name(), key, state.modifiers);
        }
        if (Keys.firesKeyPress(key, state.modifiers)) {
            final int charCode = Keys.charCode(key);
            // A keypress has the character's code as its keyCode too
            if (Dom.keyEvent(root, "keypress", key, code, charCode, charCode, state.modifiers)) {
                Dom.keyDefault(root, Keys.keyPressAction(key).name(), key, state.modifiers);
            }
        }
    }

    private static void keyUp(final Element root, final KeyAction action, final State state) {
        final String key = action.getKey();
        final String code = action.getCode();
        final boolean unprevented = Boolean.TRUE.equals(state.unprevented.remove(code));
        state.pressed.remove(code);
        state.updateModifiers();
        final boolean keyUpUnprevented = Dom.keyEvent(root, "keyup", key, code, Keys.keyCode(key), 0,
                state.modifiers);
        if (" ".equals(key) && unprevented && keyUpUnprevented) {
            Dom.spaceActivate(root, state.modifiers);
        }
    }


    // --------------------------------------------------------------------------------


    /// The keys held during one call, as user-event's keyboard state (which a direct API call
    /// starts afresh), by their `code`.
    private static final class State {

        // The keys held, in the order pressed
        private final Map<String, KeyAction> pressed = new LinkedHashMap<>();
        // Whether each held key's keydown wasn't cancelled
        private final Map<String, Boolean> unprevented = new LinkedHashMap<>();
        // The modifiers held, as a mask of Keys.SHIFT etc.
        private int modifiers;

        // A modifier is held while any key with its key value is held
        private void updateModifiers() {
            modifiers = 0;
            for (final KeyAction held : pressed.values()) {
                modifiers |= Keys.modifierBit(held.getKey());
            }
        }
    }
}
