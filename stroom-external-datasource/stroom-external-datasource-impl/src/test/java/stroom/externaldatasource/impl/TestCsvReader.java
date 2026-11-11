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

package stroom.externaldatasource.impl;

import stroom.externaldatasource.shared.CsvSettings;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestCsvReader {

    private static CsvSettings csv(final String data) {
        return CsvSettings.builder()
                .data(data)
                .build();
    }

    @Test
    void headerAndRowsAreRead() {
        final DataTable table = CsvReader.parse(csv("name, size\nRed,1\n\"Blue, dark\",\"2\"\n"), 100);

        assertThat(table.header()).containsExactly("name", "size");
        assertThat(table.rows()).containsExactly(List.of("Red", "1"), List.of("Blue, dark", "2"));
        assertThat(table.columnCount()).isEqualTo(2);
    }

    @Test
    void csvWithoutHeaderHasRowsOnly() {
        final DataTable table = CsvReader.parse(CsvSettings.builder()
                .header(false)
                .data("Red,1\nBlue,2")
                .build(), 100);

        assertThat(table.header()).isEmpty();
        assertThat(table.rows()).hasSize(2);
    }

    @Test
    void otherDelimitersAreUsed() {
        final DataTable table = CsvReader.parse(CsvSettings.builder()
                .delimiter("\t")
                .data("name\tsize\nRed, dark\t1\n")
                .build(), 100);

        assertThat(table.rows()).containsExactly(List.of("Red, dark", "1"));
    }

    @Test
    void quotedValuesMaySpanLines() {
        final DataTable table = CsvReader.parse(csv("name,note\nRed,\"first\nsecond\"\n"), 100);

        assertThat(table.rows()).containsExactly(List.of("Red", "first\nsecond"));
    }

    @Test
    void emptyLinesAreSkippedAndRaggedRowsKept() {
        final DataTable table = CsvReader.parse(csv("a,b,c\n\n1,2\n1,2,3,4\n"), 100);

        assertThat(table.rows()).containsExactly(List.of("1", "2"), List.of("1", "2", "3", "4"));
        assertThat(table.columnCount()).isEqualTo(4);
    }

    @Test
    void rowsStopAtTheLimit() {
        final DataTable table = CsvReader.parse(csv("n\n1\n2\n3\n"), 2);

        assertThat(table.rows()).containsExactly(List.of("1"), List.of("2"));
    }

    @Test
    void noDataGivesAnEmptyTable() {
        final DataTable table = CsvReader.parse(csv(null), 100);

        assertThat(table.header()).isEmpty();
        assertThat(table.rows()).isEmpty();
    }

    @Test
    void badQuotingIsReported() {
        assertThatThrownBy(() -> CsvReader.parse(csv("name\n\"Red\"x\n"), 100))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageStartingWith("The CSV could not be read");
    }

    @Test
    void delimiterMustBeOneCharacter() {
        assertThatThrownBy(() -> CsvReader.parse(CsvSettings.builder()
                .delimiter(";;")
                .data("a")
                .build(), 100))
                .hasMessageContaining("must be one character");
    }

    @Test
    void byteOrderMarkIsNotPartOfTheFirstName() {
        // Spreadsheets saving 'CSV UTF-8' start with one.
        final DataTable table = CsvReader.parse(csv("\uFEFFid,name\n1,Red\n"), 100);

        assertThat(table.header()).containsExactly("id", "name");
    }

    @Test
    void windowsLineEndingsAreRead() {
        final DataTable table = CsvReader.parse(csv("name,size\r\nRed,1\r\nBlue,2\r\n"), 100);

        assertThat(table.rows()).containsExactly(List.of("Red", "1"), List.of("Blue", "2"));
    }

    @Test
    void quoteCannotBeTheDelimiter() {
        assertThatThrownBy(() -> CsvReader.format(CsvSettings.builder()
                .delimiter("\"")
                .build()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("delimiter cannot be");
    }

    @Test
    void utf8LengthCountsBytesAsStored() {
        assertThat(CsvReader.utf8Length(null)).isZero();
        assertThat(CsvReader.utf8Length("abc")).isEqualTo(3);
        assertThat(CsvReader.utf8Length("£€\uD83D\uDE00")).isEqualTo(2 + 3 + 4);
    }
}
