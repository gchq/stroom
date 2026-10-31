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

/// Text handled as Python handles it (the values below are what Python 3.12 gives).
class TestToolText {

    @Test
    void reprQuotesAndEscapes() {
        assertThat(ToolText.repr("\0")).isEqualTo("'\\x00'");
        assertThat(ToolText.repr("a\u007fb")).isEqualTo("'a\\x7fb'");
        assertThat(ToolText.repr("\u2028")).isEqualTo("'\\u2028'");
        assertThat(ToolText.repr("\u00a0")).isEqualTo("'\\xa0'");
        assertThat(ToolText.repr("😀")).isEqualTo("'😀'");
        assertThat(ToolText.repr("it's")).isEqualTo("\"it's\"");
        assertThat(ToolText.repr("a\"b'c")).isEqualTo("'a\"b\\'c'");
        assertThat(ToolText.repr(null)).isEqualTo("None");
        assertThat(ToolText.repr(Map.of("ok", true))).isEqualTo("{'ok': True}");
    }

    @Test
    void aListOfStringsIsWrittenInSingleQuotes() {
        assertThat(ToolText.list(List.of("c:\\x", "it's"))).isEqualTo("['c:\\\\x', \"it's\"]");
        assertThat(ToolText.list(List.of())).isEqualTo("[]");
    }

    @Test
    void strWritesNumbersInShortForm() {
        assertThat(ToolText.str(1e16)).isEqualTo("1e+16");
        assertThat(ToolText.str(0.1)).isEqualTo("0.1");
        assertThat(ToolText.str(3L)).isEqualTo("3");
        assertThat(ToolText.str("x")).isEqualTo("x");
    }

    @Test
    void onlyANewLineEndsALineInARegex() {
        // As Python's re: '$' doesn't match before a '\r', and '.' matches it.
        assertThat(ToolText.search(ToolText.re("^a$", Pattern.MULTILINE), "a\r\nb")).isFalse();
        assertThat(ToolText.search(ToolText.re("a.b"), "a\rb")).isTrue();
    }

    @Test
    void numbersAreFormattedWithTheirRounding() {
        assertThat(ToolText.formatG(0.0001234)).isEqualTo("0.0001234");
        assertThat(ToolText.formatG(1234567)).isEqualTo("1.23457e+06");
        assertThat(ToolText.formatFixed0(2.5)).isEqualTo("2");
        assertThat(ToolText.formatFixed0(3.5)).isEqualTo("4");
    }

    @Test
    void stringMethodsStripUnicodeWhitespace() {
        assertThat(ToolText.lstrip(" \u3000x\u2000 ")).isEqualTo("x\u2000 ");
        assertThat(ToolText.strip("//a/b//", "/")).isEqualTo("a/b");
        assertThat(ToolText.lower("ÉCS")).isEqualTo("écs");
        assertThat(ToolText.lastPart("event.user.id", '.')).isEqualTo("id");
        assertThat(ToolText.lastPart("id", '.')).isEqualTo("id");
        assertThat(ToolText.search(ToolText.ignoringCase("^user"), "UserId")).isTrue();
    }
}
