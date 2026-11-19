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

import com.google.gwt.event.dom.client.KeyCodes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Arrays;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

class TestKeyShortcut {

    @ParameterizedTest
    @ValueSource(strings = {"Ctrl+Shift+S", "G U", "Shift Shift", "F1", "/", "W"})
    void parsesItsOwnTextForm(final String text) {
        final KeyShortcut shortcut = KeyShortcut.parse(text);

        assertThat(shortcut).isNotNull();
        assertThat(shortcut.toString()).isEqualTo(text);
    }

    @Test
    void parsesASequence() {
        final KeyShortcut shortcut = KeyShortcut.parse("g  u");

        assertThat(shortcut).isEqualTo(KeyShortcut.sequence(KeyCodes.KEY_G, KeyCodes.KEY_U));
        assertThat(shortcut.isSequence()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "",
            "Ctrl",
            "Shift",
            "Ctrl+G U",
            "G Shift+U",
            "G U I",
            "G Nope"})
    void doesNotParseInvalidShortcuts(final String text) {
        assertThat(KeyShortcut.parse(text)).isNull();
    }

    @Test
    void isNotValidWithNoKeyStrokes() {
        assertThat(KeyShortcut.isValid(Collections.emptyList())).isFalse();
        assertThat(KeyShortcut.isValid(null)).isFalse();
        assertThat(KeyShortcut.isValid(Arrays.asList(KeyStroke.of(KeyCodes.KEY_G), null))).isFalse();
    }

    @ParameterizedTest
    @CsvSource({
            "W, true",
            "G U, true",
            "Shift+/, true",
            "Ctrl+S, false",
            "F1, false",
            "Shift Shift, false",
            "Down, false"})
    void knowsCharacterKeyShortcuts(final String text, final boolean expected) {
        assertThat(KeyShortcut.parse(text).isCharacterKeyShortcut()).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({
            "Ctrl+S, Ctrl+S, true",
            "Ctrl+S, Ctrl+Shift+S, false",
            "G U, G U, true",
            "G U, G I, false",
            // A single G would always start the sequence
            "G, G U, true",
            "G U, G, true",
            "Shift+G, G U, true",
            "Ctrl+G, G U, false",
            "U, G U, false"})
    void knowsWhenShortcutsClash(final String text1, final String text2, final boolean expected) {
        final KeyShortcut shortcut1 = KeyShortcut.parse(text1);
        final KeyShortcut shortcut2 = KeyShortcut.parse(text2);

        assertThat(shortcut1.clashesWith(shortcut2)).isEqualTo(expected);
        assertThat(shortcut2.clashesWith(shortcut1)).isEqualTo(expected);
    }

    @Test
    void doesNotClashWithNull() {
        assertThat(KeyShortcut.parse("G").clashesWith(null)).isFalse();
    }

    @Test
    void recordsAKeyCombination() {
        KeyShortcut recorded = KeyShortcut.record(null, KeyStroke.of(KeyCodes.KEY_CTRL));
        assertThat(recorded).isNull();

        recorded = KeyShortcut.record(recorded, KeyStroke.parse("Ctrl+S"));
        assertThat(recorded).hasToString("Ctrl+S");
    }

    @Test
    void recordsTwoPlainKeysAsASequence() {
        KeyShortcut recorded = KeyShortcut.record(null, KeyStroke.parse("G"));
        recorded = KeyShortcut.record(recorded, KeyStroke.parse("U"));

        assertThat(recorded).hasToString("G U");
    }

    @Test
    void startsAgainAfterASequence() {
        KeyShortcut recorded = KeyShortcut.parse("G U");
        recorded = KeyShortcut.record(recorded, KeyStroke.parse("I"));

        assertThat(recorded).hasToString("I");
    }

    @Test
    void startsAgainWhenAKeyHasModifiers() {
        KeyShortcut recorded = KeyShortcut.parse("G");
        recorded = KeyShortcut.record(recorded, KeyStroke.parse("Ctrl+U"));
        assertThat(recorded).hasToString("Ctrl+U");

        recorded = KeyShortcut.record(recorded, KeyStroke.parse("I"));
        assertThat(recorded).hasToString("I");
    }

    @Test
    void ignoresModifierKeysOnTheirOwn() {
        final KeyShortcut recorded = KeyShortcut.parse("G");

        assertThat(KeyShortcut.record(recorded, KeyStroke.of(KeyCodes.KEY_SHIFT))).isEqualTo(recorded);
        assertThat(KeyShortcut.record(recorded, null)).isEqualTo(recorded);
    }
}
