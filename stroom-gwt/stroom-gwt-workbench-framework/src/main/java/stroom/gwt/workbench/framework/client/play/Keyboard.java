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

import java.util.List;

/// Presses keys on the focused element as `userEvent.keyboard` does: `keydown`, `keypress` (for
/// characters and Enter) and `keyup`, with the browser's default action for each key (typing,
/// deleting, moving the focus with Tab, clicking a button with Enter or Space, etc.) unless the
/// `keydown` or `keypress` is cancelled.
final class Keyboard {

    private Keyboard() {
        // Static utility
    }

    /// @param root    Any element in the document, used if nothing has the focus.
    /// @param actions The keys, from [Keys#parse(String)].
    static void press(final Element root, final List<KeyAction> actions) {
        // The modifiers held, as a mask of Keys.SHIFT etc.
        int modifiers = 0;
        for (final KeyAction action : actions) {
            final String key = action.getKey();
            if (Keys.SELECT_ALL.equals(key)) {
                Dom.keyDefault(root, DefaultAction.SELECT_ALL.name(), key);
                continue;
            }
            final int bit = Keys.modifierBit(key);
            final String code = Keys.code(key);
            boolean notCancelled = true;
            if (action.isPress()) {
                for (int i = 0; i < action.getRepeat(); i++) {
                    modifiers |= bit;
                    notCancelled = keyDown(root, key, code, modifiers);
                }
            }
            if (action.isRelease()) {
                modifiers &= ~bit;
                Dom.keyEvent(root, "keyup", key, code, Keys.keyCode(key), 0, modifiers);
                if (notCancelled && action.isPress() && " ".equals(key) && modifiers == 0) {
                    Dom.spaceActivate(root);
                }
            }
        }
    }

    private static boolean keyDown(final Element root, final String key, final String code, final int modifiers) {
        boolean notCancelled = Dom.keyEvent(root, "keydown", key, code, Keys.keyCode(key), 0, modifiers);
        if (notCancelled && Keys.firesKeyPress(key, modifiers)) {
            final int charCode = Keys.charCode(key);
            // A keypress has the character's code as its keyCode too
            notCancelled = Dom.keyEvent(root, "keypress", key, code, charCode, charCode, modifiers);
        }
        if (notCancelled) {
            final DefaultAction defaultAction = Keys.defaultAction(key, modifiers);
            if (defaultAction != DefaultAction.NONE) {
                Dom.keyDefault(root, defaultAction.name(), key);
            }
        }
        return notCancelled;
    }
}
