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

import static org.assertj.core.api.Assertions.assertThat;

class TestKeyStroke {

    @ParameterizedTest
    @ValueSource(strings = {
            "S",
            "Ctrl+S",
            "Ctrl+Alt+Shift+Meta+S",
            "F1",
            "F12",
            "Up",
            "PageDown",
            "Esc",
            "Space",
            "Shift",
            "/",
            "Ctrl+/",
            ";",
            "'",
            "\\",
            "[",
            "7",
            "Key106"})
    void parsesItsOwnTextForm(final String text) {
        final KeyStroke keyStroke = KeyStroke.parse(text);

        assertThat(keyStroke).isNotNull();
        assertThat(keyStroke.toString()).isEqualTo(text);
    }

    @Test
    void parsesModifiersInAnyOrderAndCase() {
        final KeyStroke keyStroke = KeyStroke.parse(" shift+CTRL+s ");

        assertThat(keyStroke).isEqualTo(KeyStroke.builder().ctrl().shift().keyCode(KeyCodes.KEY_S).build());
        assertThat(keyStroke.toString()).isEqualTo("Ctrl+Shift+S");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "Ctrl+", "Hyper+S", "Ctrl+Nope", "F13", "SS", "Key"})
    void doesNotParseNonsense(final String text) {
        assertThat(KeyStroke.parse(text)).isNull();
    }

    @Test
    void doesNotParseNull() {
        assertThat(KeyStroke.parse(null)).isNull();
    }

    @Test
    void givesFirefoxKeyCodesTheSameKeyAsOtherBrowsers() {
        // Firefox gives 59 for the semicolon key and 173 for the dash key
        assertThat(KeyStroke.of(59)).isEqualTo(KeyStroke.parse(";"));
        assertThat(KeyStroke.of(173)).isEqualTo(KeyStroke.parse("-"));
    }

    @ParameterizedTest
    @CsvSource({
            "W, true",
            "Shift+W, true",
            "7, true",
            "/, true",
            "Space, true",
            "Ctrl+W, false",
            "Alt+W, false",
            "Meta+W, false",
            "F1, false",
            "Up, false",
            "Esc, false",
            "Shift, false"})
    void knowsCharacterKeys(final String text, final boolean expected) {
        assertThat(KeyStroke.parse(text).isCharacterKey()).isEqualTo(expected);
    }

    @Test
    void knowsModifierKeys() {
        assertThat(KeyStroke.of(KeyCodes.KEY_SHIFT).isModifierKey()).isTrue();
        assertThat(KeyStroke.of(KeyCodes.KEY_CTRL).isModifierKey()).isTrue();
        assertThat(KeyStroke.of(KeyCodes.KEY_ALT).isModifierKey()).isTrue();
        assertThat(KeyStroke.of(KeyCodes.KEY_S).isModifierKey()).isFalse();
    }

    @Test
    void doesNotCountShiftAsACommandModifier() {
        assertThat(KeyStroke.parse("Shift+S").hasCommandModifier()).isFalse();
        assertThat(KeyStroke.parse("Shift+S").hasAnyModifier()).isTrue();
        assertThat(KeyStroke.parse("Ctrl+S").hasCommandModifier()).isTrue();
    }
}
