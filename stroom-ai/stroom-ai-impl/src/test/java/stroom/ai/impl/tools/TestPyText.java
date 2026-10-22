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

package stroom.ai.impl.tools;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/// Text handled as the Stroom MCP server's Python handled it (the values below are what Python 3.12 gives).
class TestPyText {

    @Test
    void reprEscapesAsPythonDoes() {
        assertThat(PyText.repr("\0")).isEqualTo("'\\x00'");
        assertThat(PyText.repr("a\u007fb")).isEqualTo("'a\\x7fb'");
        assertThat(PyText.repr("\u2028")).isEqualTo("'\\u2028'");
        assertThat(PyText.repr("\u00a0")).isEqualTo("'\\xa0'");
        assertThat(PyText.repr("😀")).isEqualTo("'😀'");
        assertThat(PyText.repr("it's")).isEqualTo("\"it's\"");
        assertThat(PyText.repr("a\"b'c")).isEqualTo("'a\"b\\'c'");
        assertThat(PyText.repr(null)).isEqualTo("None");
        assertThat(PyText.repr(Map.of("ok", true))).isEqualTo("{'ok': True}");
    }

    @Test
    void aListOfStringsIsWrittenAsPythonWritesIt() {
        assertThat(PyText.list(List.of("c:\\x", "it's"))).isEqualTo("['c:\\\\x', \"it's\"]");
        assertThat(PyText.list(List.of())).isEqualTo("[]");
    }

    @Test
    void strWritesNumbersAsPythonDoes() {
        assertThat(PyText.str(1e16)).isEqualTo("1e+16");
        assertThat(PyText.str(0.1)).isEqualTo("0.1");
        assertThat(PyText.str(3L)).isEqualTo("3");
        assertThat(PyText.str("x")).isEqualTo("x");
    }

    @Test
    void onlyANewLineEndsALineInARegex() {
        // As Python's re: '$' doesn't match before a '\r', and '.' matches it.
        assertThat(PyText.search(PyText.re("^a$", Pattern.MULTILINE), "a\r\nb")).isFalse();
        assertThat(PyText.search(PyText.re("a.b"), "a\rb")).isTrue();
    }

    @Test
    void numbersAreFormattedAsPythonFormatsThem() {
        assertThat(PyText.formatG(0.0001234)).isEqualTo("0.0001234");
        assertThat(PyText.formatG(1234567)).isEqualTo("1.23457e+06");
        assertThat(PyText.formatFixed0(2.5)).isEqualTo("2");
        assertThat(PyText.formatFixed0(3.5)).isEqualTo("4");
    }

    @Test
    void stringMethodsAreAsPythonHasThem() {
        assertThat(PyText.lstrip(" \u3000x\u2000 ")).isEqualTo("x\u2000 ");
        assertThat(PyText.strip("//a/b//", "/")).isEqualTo("a/b");
        assertThat(PyText.lower("ÉCS")).isEqualTo("écs");
        assertThat(PyText.lastPart("event.user.id", '.')).isEqualTo("id");
        assertThat(PyText.lastPart("id", '.')).isEqualTo("id");
        assertThat(PyText.search(PyText.ignoringCase("^user"), "UserId")).isTrue();
    }
}
