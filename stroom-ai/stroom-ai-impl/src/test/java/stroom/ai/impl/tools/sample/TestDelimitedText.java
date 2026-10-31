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

package stroom.ai.impl.tools.sample;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestDelimitedText {

    @Test
    void rowsAreReadWithQuotesAndEscapes() {
        assertThat(DelimitedText.read("a,\"b, c\",\"say \"\"hi\"\"\"\n\nx\"y,\"multi\nline\"", DelimitedText.EXCEL))
                .containsExactly(List.of("a", "b, c", "say \"hi\""), List.of(), List.of("x\"y", "multi\nline"));
        // A quote after a quoted value's close is kept, as Python's reader keeps it when not strict.
        assertThat(DelimitedText.read("\"ab\"c,d", DelimitedText.EXCEL)).containsExactly(List.of("abc", "d"));
        // An unclosed quote runs to the end.
        assertThat(DelimitedText.read("a,\"open\nmore", DelimitedText.EXCEL))
                .containsExactly(List.of("a", "open\nmore"));
        assertThat(DelimitedText.read("a\tb", DelimitedText.Dialect.delimitedBy('\t')))
                .containsExactly(List.of("a", "b"));
    }

    @Test
    void aVeryLongFieldIsRead() {
        // Seen in testing: a 250,000-character URL query was refused ("field larger than field limit (131072)").
        final String query = "q=" + "a".repeat(250_000);
        final String quoted = "say \"\"" + "b".repeat(200_000) + "\"\"";

        assertThat(DelimitedText.read("1," + query + ",\"" + quoted + "\"\n2,short,x", DelimitedText.EXCEL))
                .containsExactly(List.of("1", query, "say \"" + "b".repeat(200_000) + "\""),
                        List.of("2", "short", "x"));
    }

    @Test
    void theSnifferFindsTheDelimiterAndTheHeader() {
        assertThat(DelimitedText.sniff("time,user,action\n2026-10-01T10:00:00Z,alice,login\n", ",\t|;").delimiter())
                .isEqualTo(',');
        assertThat(DelimitedText.sniff("a|b|c\n1|2|3\n4|5|6", ",\t|;").delimiter()).isEqualTo('|');
        assertThat(DelimitedText.sniff("\"a\";\"b\"\n\"c\";\"d\"", ",\t|;").delimiter()).isEqualTo(';');
        assertThatThrownBy(() -> DelimitedText.sniff("one thing\nanother thing", ",\t|;"))
                .isInstanceOf(DelimitedText.CsvException.class)
                .hasMessage("Could not determine delimiter");
        assertThat(DelimitedText.hasHeader("name,age\nalice,31\nbob,42\n")).isTrue();
        assertThat(DelimitedText.hasHeader("alice,31\nbob,42\ncarol,7\n")).isFalse();
    }

    @Test
    void numbersIncludeExponentsAndComplexForms() {
        assertThat(List.of("1", " -2.5e3 ", "1_000", "inf", "nan", "3j", "(1+2j)", ".5", "1.", "j", "-j"))
                .allMatch(DelimitedText::isComplex);
        assertThat(List.of("", "abc", "1_", "_1", "1e", "1+", "(1", "10.0.0.1", "2026-10-01")).noneMatch(
                DelimitedText::isComplex);
    }
}
