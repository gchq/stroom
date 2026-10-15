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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestKeyCombo {

    @ParameterizedTest
    @ValueSource(strings = {
            "alt+F",
            "control+K",
            "1",
            "alt+ArrowUp",
            "control+shift+,",
            "alt+shift+E",
            "shift+F6",
            "F6",
            "meta+K",
            "alt+=",
            "alt+-",
    })
    void testParseAndToStringRoundTrip(final String value) {
        assertThat(KeyCombo.parse(value).toString()).isEqualTo(value);
    }

    @Test
    void testParse() {
        assertThat(KeyCombo.parse("control+shift+ArrowUp"))
                .isEqualTo(new KeyCombo(false, true, true, false, "ArrowUp"));
        // Modifier order doesn't matter
        assertThat(KeyCombo.parse("shift+control+ArrowUp"))
                .isEqualTo(KeyCombo.parse("control+shift+ArrowUp"));
        // A key named like a modifier
        assertThat(KeyCombo.parse("alt").getKey()).isEqualTo("alt");
    }

    @ParameterizedTest
    @NullAndEmptySource
    void testParse_invalid(final String value) {
        assertThatThrownBy(() -> KeyCombo.parse(value)).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @CsvSource(value = {
            "KeyF,F",
            "Digit1,1",
            "Numpad3,3",
            "Comma,','",
            "Equal,=",
            "NumpadAdd,=",
            "Minus,-",
            "ArrowUp,ArrowUp",
            "F6,F6",
            "Escape,Escape",
    })
    void testFromKeyCode(final String code, final String expected) {
        assertThat(KeyCombo.fromKeyCode(code)).isEqualTo(expected);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"AltLeft", "ShiftRight", "ControlLeft", "MetaRight"})
    void testFromKeyCode_modifiersAndInvalid(final String code) {
        assertThat(KeyCombo.fromKeyCode(code)).isNull();
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "alt+F|alt F",
            "control+K|⌃ K",
            "control+shift+,|⌃ ⇧ ,",
            "alt+ArrowLeft|alt ←",
            "control+shift+ArrowDown|⌃ ⇧ ↓",
            "meta+alt+Escape|⌘ alt esc",
            "1|1",
    })
    void testToDisplayString(final String value, final String expected) {
        assertThat(KeyCombo.parse(value).toDisplayString()).isEqualTo(expected);
    }

    @Test
    void testParse_modifierWithoutKey() {
        // A trailing separator isn't stripped, so the whole value is the key
        assertThat(KeyCombo.parse("alt+").getKey()).isEqualTo("alt+");
        assertThat(KeyCombo.parse("alt+").isPlainKey()).isTrue();
    }

    @Test
    void testIsShift() {
        assertThat(KeyCombo.parse("shift+F6").isShift()).isTrue();
        assertThat(KeyCombo.parse("control+shift+,").isShift()).isTrue();
        assertThat(KeyCombo.parse("alt+F").isShift()).isFalse();
    }

    @Test
    void testIsPlainKey() {
        assertThat(KeyCombo.parse("1").isPlainKey()).isTrue();
        assertThat(KeyCombo.parse("shift+F6").isPlainKey()).isTrue();
        assertThat(KeyCombo.parse("alt+F").isPlainKey()).isFalse();
        assertThat(KeyCombo.parse("control+K").isPlainKey()).isFalse();
        assertThat(KeyCombo.parse("meta+K").isPlainKey()).isFalse();
    }

    @Test
    void testEquals() {
        assertThat(new KeyCombo(true, false, false, false, "F"))
                .isEqualTo(KeyCombo.parse("alt+F"))
                .hasSameHashCodeAs(KeyCombo.parse("alt+F"))
                .isNotEqualTo(KeyCombo.parse("alt+G"))
                .isNotEqualTo(KeyCombo.parse("F"));
    }
}
