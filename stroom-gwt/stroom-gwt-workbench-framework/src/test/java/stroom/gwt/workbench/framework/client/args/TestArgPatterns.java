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

package stroom.gwt.workbench.framework.client.args;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class TestArgPatterns {

    @ParameterizedTest
    @ValueSource(strings = {"", "abc", "ABC xyz", "a_b-c", "123"})
    void testIsPlain_true(final String value) {
        assertThat(ArgPatterns.isPlain(value)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"a.b", "a;b", "<script>", "café", "a\tb", "a+b", "#fff"})
    void testIsPlain_false(final String value) {
        assertThat(ArgPatterns.isPlain(value)).isFalse();
    }

    @Test
    void testIsSafeKey() {
        assertThat(ArgPatterns.isSafeKey("text")).isTrue();
        assertThat(ArgPatterns.isSafeKey("")).isFalse();
        assertThat(ArgPatterns.isSafeKey(null)).isFalse();
        assertThat(ArgPatterns.isSafeKey("a[0]")).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "12.5", "-0.001"})
    void testIsNumber_true(final String value) {
        assertThat(ArgPatterns.isNumber(value)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "-", "1.", ".5", "+1", "1e5", "1.2.3", "--1"})
    void testIsNumber_false(final String value) {
        assertThat(ArgPatterns.isNumber(value)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"#fff", "#FFFF", "#ff4785", "#ff4785AA"})
    void testIsHexColour_true(final String value) {
        assertThat(ArgPatterns.isHexColour(value)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"#", "#ff", "#fffff", "#fffffff", "#ggg", "fff"})
    void testIsHexColour_false(final String value) {
        assertThat(ArgPatterns.isHexColour(value)).isFalse();
    }

    @Test
    void testMatchColour() {
        assertThat(ArgPatterns.matchColour("rgba(255, 0, 0, 0.5)"))
                .containsExactly("rgba", "255", "0", "0", "0.5");
        assertThat(ArgPatterns.matchColour("rgb(255,0,0)"))
                .containsExactly("rgb", "255", "0", "0", null);
        assertThat(ArgPatterns.matchColour("HSLA(120, 50%, 25%, 1)"))
                .containsExactly("HSLA", "120", "50", "25", "1");
        assertThat(ArgPatterns.matchColour("hsl(120,50%,25%)"))
                .containsExactly("hsl", "120", "50", "25", null);
        // As the regular expression does, backtracking splits the digits into the last number
        // and the alpha
        assertThat(ArgPatterns.matchColour("rgb(1,2,30.5)"))
                .containsExactly("rgb", "1", "2", "3", "0.5");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "rgb(1,2)",
            "rgb(1000,0,0)",
            "rgb(1,2,3",
            "rgb(1,2,3) ",
            "rgb( 1,2,3)",
            "rgb(1,  2,3)",
            "rgb(1,2,3,0.123)",
            "cmyk(1,2,3)",
            "rgb(a,b,c)",
    })
    void testMatchColour_noMatch(final String value) {
        assertThat(ArgPatterns.matchColour(value)).isNull();
    }

    @Test
    void testIsIsoDate() {
        assertThat(ArgPatterns.isIsoDate("2026-10-05T12:30:00.000Z")).isTrue();
        assertThat(ArgPatterns.isIsoDate("2026-10-05T12:30:00Z")).isTrue();
        assertThat(ArgPatterns.isIsoDate("2026-10-05")).isFalse();
        assertThat(ArgPatterns.isIsoDate("2026-10-05T12:30:00.000+01:00")).isFalse();
        assertThat(ArgPatterns.isIsoDate("abcd-10-05T12:30:00Z")).isFalse();
    }

    @Test
    void testIsSafeString() {
        assertThat(ArgPatterns.isSafeString("Click me")).isTrue();
        assertThat(ArgPatterns.isSafeString("-1.5")).isTrue();
        assertThat(ArgPatterns.isSafeString("#ff4785")).isTrue();
        assertThat(ArgPatterns.isSafeString("rgba(1, 2, 3, 0.5)")).isTrue();
        assertThat(ArgPatterns.isSafeString("2026-10-05T12:30:00Z")).isTrue();
        assertThat(ArgPatterns.isSafeString("<script>alert(1)</script>")).isFalse();
        assertThat(ArgPatterns.isSafeString("javascript:alert(1)")).isFalse();
    }
}
