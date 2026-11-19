/*
 * Copyright 2024 Crown Copyright
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

import stroom.widget.util.client.KeyBinding.Action;

import com.google.gwt.dom.client.NativeEvent;

import java.util.ArrayList;
import java.util.List;

/// Recognises sequences of two keys pressed one after the other, e.g. `G U`, and gives the
/// action bound to the sequence.
public class KeySequences {

    private static final int KEY_SEQUENCE_TIMER_DELAY = 500;

    private final List<KeyRec> currentKeySequence = new ArrayList<>();
    private Object[] keySequences = new Object[0];

    void addKeySequence(final Action action,
                        final int keyCode1,
                        final int keyCode2) {
        keySequences = growArr(keySequences, keyCode1);
        Object[] actions = (Object[]) keySequences[keyCode1];
        actions = growArr(actions, keyCode2);
        keySequences[keyCode1] = actions;

        final Action existingAction = (Action) actions[keyCode2];
        if (existingAction != null) {
            throw new RuntimeException("Key sequence " + KeyShortcut.sequence(keyCode1, keyCode2)
                    + " is already bound to action " + existingAction + ". "
                    + "Trying to bind it to " + action);
        }
        actions[keyCode2] = action;
    }

    void clear() {
        currentKeySequence.clear();
    }

    private Object[] growArr(final Object[] src, final int code) {
        if (src == null) {
            return new Object[code + 1];
        } else if (src.length <= code) {
            final Object[] arr = new Object[code + 1];
            System.arraycopy(src, 0, arr, 0, src.length);
            return arr;
        }
        return src;
    }

    boolean onKeyDown(final NativeEvent e) {
        final int keycode = KeyStroke.normaliseKeyCode(e.getKeyCode());

        currentKeySequence.add(0, new KeyRec(keycode, true, System.currentTimeMillis()));
        trim();

        // Test previous key.
        if (currentKeySequence.size() >= 3) {
            final KeyRec keyRec3 = currentKeySequence.get(0);
            final KeyRec keyRec2 = currentKeySequence.get(1);
            final KeyRec keyRec1 = currentKeySequence.get(2);
            if (!keyRec2.down && keyRec1.down && keyRec2.keycode == keyRec1.keycode) {

                // Check elapsed time from start to end of sequence.
                if (keyRec3.time - keyRec1.time < KEY_SEQUENCE_TIMER_DELAY) {
                    final int code1 = keyRec1.keycode;
                    final int code2 = keyRec3.keycode;
                    if (keySequences.length > code1) {
                        final Object[] actions = (Object[]) keySequences[code1];
                        if (actions != null && actions.length > code2) {
                            final Object action = actions[code2];
                            if (action != null) {
                                return true;
                            }
                        }
                    }
                }
            }
        }

        // Could this be part of a new sequence?
        if (keySequences.length > keycode && keySequences[keycode] != null) {
            return true;
        }

        // This key cannot be part of an expected sequence so clear.
        clear();

        return false;
    }

    Action onKeyUp(final NativeEvent e) {
        final int keycode = KeyStroke.normaliseKeyCode(e.getKeyCode());
        currentKeySequence.add(0, new KeyRec(keycode, false, System.currentTimeMillis()));
        trim();

        if (currentKeySequence.size() >= 4) {
            // Check key order.
            final KeyRec keyRec4 = currentKeySequence.get(0);
            final KeyRec keyRec3 = currentKeySequence.get(1);
            final KeyRec keyRec2 = currentKeySequence.get(2);
            final KeyRec keyRec1 = currentKeySequence.get(3);

            // Check down, up, down, up sequence.
            if ((!keyRec4.down && keyRec3.down && keyRec4.keycode == keyRec3.keycode) &&
                    (!keyRec2.down && keyRec1.down && keyRec2.keycode == keyRec1.keycode)) {

                // Check elapsed time from start to end of sequence.
                if (keyRec4.time - keyRec1.time < KEY_SEQUENCE_TIMER_DELAY) {
                    final int code1 = keyRec1.keycode;
                    final int code2 = keyRec4.keycode;
                    if (keySequences.length > code1) {
                        final Object[] actions = (Object[]) keySequences[code1];
                        if (actions != null && actions.length > code2) {
                            final Action action = (Action) actions[code2];
                            if (action != null) {
                                return action;
                            }
                        }
                    }
                }
            }
        }
        return null;
    }

    private void trim() {
        while (currentKeySequence.size() > 10) {
            currentKeySequence.remove(currentKeySequence.size() - 1);
        }
    }

    private static class KeyRec {

        private final int keycode;
        private final boolean down;
        private final long time;

        public KeyRec(final int keycode, final boolean down, final long time) {
            this.keycode = keycode;
            this.down = down;
            this.time = time;
        }

        @Override
        public String toString() {
            return KeyStroke.keyCodeToName(keycode) + " (" + ((down)
                    ? "down"
                    : "up") + ")";
        }
    }
}
