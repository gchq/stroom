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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/// The keys that trigger an action: either a single [KeyStroke], e.g. `Ctrl+S`, or a sequence
/// of two keys pressed one after the other without modifiers, e.g. `G U`.
///
/// Its text form, given by [#toString()] and read by [#parse(String)], is the text form of each
/// key stroke, separated by a space. That form is what is shown to the user and what is saved in
/// their preferences.
public final class KeyShortcut {

    private static final String SEQUENCE_SEPARATOR = " ";

    private final List<KeyStroke> keyStrokes;

    private KeyShortcut(final List<KeyStroke> keyStrokes) {
        this.keyStrokes = Collections.unmodifiableList(new ArrayList<>(keyStrokes));
    }

    /// @param keyStroke The key stroke.
    /// @return A shortcut of a single key stroke.
    public static KeyShortcut of(final KeyStroke keyStroke) {
        return new KeyShortcut(Collections.singletonList(Objects.requireNonNull(keyStroke)));
    }

    /// @param keyCode1 The key code of the first key, pressed without modifiers.
    /// @param keyCode2 The key code of the second key, pressed without modifiers.
    /// @return A shortcut of two keys pressed one after the other.
    public static KeyShortcut sequence(final int keyCode1, final int keyCode2) {
        return new KeyShortcut(Arrays.asList(KeyStroke.of(keyCode1), KeyStroke.of(keyCode2)));
    }

    /// Makes a shortcut from the key strokes, if they make a valid shortcut (see [#isValid(List)]).
    ///
    /// @param keyStrokes The key strokes.
    /// @return The shortcut, or null if the key strokes don't make a valid shortcut.
    public static KeyShortcut of(final List<KeyStroke> keyStrokes) {
        return isValid(keyStrokes)
                ? new KeyShortcut(keyStrokes)
                : null;
    }

    /// Whether the key strokes make a valid shortcut: a single key stroke that isn't just a
    /// modifier key, e.g. `Ctrl` on its own (except Shift, as `Shift Shift` is a sequence), or two
    /// key strokes without modifiers.
    ///
    /// @param keyStrokes The key strokes.
    /// @return Whether they are a valid shortcut.
    public static boolean isValid(final List<KeyStroke> keyStrokes) {
        if (NullSafe.isEmptyCollection(keyStrokes) || keyStrokes.size() > 2 || keyStrokes.contains(null)) {
            return false;
        } else if (keyStrokes.size() == 1) {
            return !keyStrokes.get(0).isModifierKey();
        } else {
            return keyStrokes.stream()
                    .noneMatch(KeyStroke::hasAnyModifier);
        }
    }

    /// Adds a key that the user has pressed to the shortcut they are recording. A key without
    /// modifiers that follows another key without modifiers makes a sequence, e.g. `G` then `U`
    /// makes `G U`. Any other key starts the shortcut again, so the user can correct a mistake by
    /// pressing the keys again. Modifier keys pressed on their own are ignored, as they will be
    /// part of the next key stroke.
    ///
    /// @param recorded  The shortcut recorded so far, or null if there is none yet.
    /// @param keyStroke The key that the user has pressed.
    /// @return The recorded shortcut.
    public static KeyShortcut record(final KeyShortcut recorded, final KeyStroke keyStroke) {
        if (keyStroke == null || keyStroke.isModifierKey()) {
            return recorded;
        } else if (recorded != null
                   && !recorded.isSequence()
                   && !recorded.getFirst().hasAnyModifier()
                   && !keyStroke.hasAnyModifier()) {
            return new KeyShortcut(Arrays.asList(recorded.getFirst(), keyStroke));
        } else {
            return of(keyStroke);
        }
    }

    /// Reads the text form of a shortcut, e.g. `Ctrl+Shift+S` or `G U`.
    ///
    /// @param text The text form of the shortcut.
    /// @return The shortcut, or null if the text isn't a valid shortcut.
    public static KeyShortcut parse(final String text) {
        if (NullSafe.isBlankString(text)) {
            return null;
        }
        final String[] parts = text.trim().split(" +");
        final List<KeyStroke> keyStrokes = new ArrayList<>();
        for (final String part : parts) {
            final KeyStroke keyStroke = KeyStroke.parse(part);
            if (keyStroke == null) {
                return null;
            }
            keyStrokes.add(keyStroke);
        }
        return of(keyStrokes);
    }

    /// @return The key strokes, in the order they are pressed.
    public List<KeyStroke> getKeyStrokes() {
        return keyStrokes;
    }

    /// @return The first (or only) key stroke.
    public KeyStroke getFirst() {
        return keyStrokes.get(0);
    }

    /// @return Whether this is a sequence of two keys pressed one after the other.
    public boolean isSequence() {
        return keyStrokes.size() > 1;
    }

    /// Whether this is a character key shortcut, as defined by WCAG 2.1.4, i.e. it is made only
    /// of keys that type characters, without Ctrl, Alt or Meta. Users can turn these off.
    ///
    /// @return Whether this is a character key shortcut.
    public boolean isCharacterKeyShortcut() {
        return keyStrokes.stream().noneMatch(KeyStroke::hasCommandModifier)
               && keyStrokes.stream().anyMatch(KeyStroke::isCharacterKey);
    }

    /// Whether the two shortcuts can't both be used, because they are the same or because one is
    /// a single key that is also the first key of the other (a sequence), so pressing that key
    /// would always start the sequence.
    ///
    /// @param other The other shortcut.
    /// @return Whether the two shortcuts clash.
    public boolean clashesWith(final KeyShortcut other) {
        if (other == null) {
            return false;
        } else if (equals(other)) {
            return true;
        } else if (isSequence() != other.isSequence()) {
            final KeyShortcut single = isSequence()
                    ? other
                    : this;
            final KeyShortcut sequence = isSequence()
                    ? this
                    : other;
            // Key sequences match on key code only
            return !single.getFirst().hasCommandModifier()
                   && single.getFirst().getKeyCode() == sequence.getFirst().getKeyCode();
        } else {
            return false;
        }
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final KeyShortcut that = (KeyShortcut) o;
        return Objects.equals(keyStrokes, that.keyStrokes);
    }

    @Override
    public int hashCode() {
        return Objects.hash(keyStrokes);
    }

    /// @return The text form of the shortcut, e.g. `Ctrl+Shift+S` or `G U`.
    @Override
    public String toString() {
        return keyStrokes.stream()
                .map(KeyStroke::toString)
                .collect(Collectors.joining(SEQUENCE_SEPARATOR));
    }
}
