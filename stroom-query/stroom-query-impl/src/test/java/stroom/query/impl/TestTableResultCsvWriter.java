/*
 * Copyright 2016-2025 Crown Copyright
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

package stroom.query.impl;

import stroom.query.api.Column;
import stroom.query.api.Row;
import stroom.query.api.TableResult;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class TestTableResultCsvWriter {


    @Test
    void testWithNoRowsOrColumns() {
        final TableResult tableResult = TableResult.builder().build();

        final TableResultCsvWriter csvWriter = new TableResultCsvWriter(tableResult);

        final String csv = csvWriter.toCsv();

        assertThat(csv).isEqualTo("");
    }

    @Test
    void testWithNoRows() {
        final Column col1 = Column.builder().name("col1").visible(true).special(false).build();
        final Column col2 = Column.builder().name("col2").visible(false).special(true).build();
        final Column col3 = Column.builder().name("col3").visible(true).special(false).build();
        final Column col4 = Column.builder().name("col4").visible(true).special(true).build();
        final Column col5 = Column.builder().name("col5").visible(false).special(false).build();

        final TableResult tableResult = TableResult.builder().columns(List.of(col1, col2, col3, col4, col5)).build();

        final TableResultCsvWriter csvWriter = new TableResultCsvWriter(tableResult);
        final String csv = csvWriter.toCsv();

        final String expectedCsv = """
            "col1","col3\"""";

        assertThat(csv).isEqualTo(expectedCsv);
    }

    @Test
    void testWithRows() {
        final Column col1 = Column.builder().name("col1").visible(true).special(false).build();
        final Column col2 = Column.builder().name("col2").visible(false).special(true).build();
        final Column col3 = Column.builder().name("col3").visible(true).special(false).build();

        final Row row1 = Row.builder().values(List.of("1", "2", "3")).build();
        final Row row2 = Row.builder().values(List.of("4", "5", "6")).build();
        final Row row3 = Row.builder().values(List.of("7", "8", "9")).build();

        final TableResult tableResult = TableResult.builder()
                .columns(List.of(col1, col2, col3))
                .addRow(row1).addRow(row2).addRow(row3)
                .build();

        final TableResultCsvWriter csvWriter = new TableResultCsvWriter(tableResult);
        final String csv = csvWriter.toCsv();

        final String expectedCsv = """
            "col1","col3"
            "1","3"
            "4","6"
            "7","9\"""";

        assertThat(csv).isEqualTo(expectedCsv);
    }

    @Test
    void testWithNullValues() {
        final Column col1 = Column.builder().name("col1").visible(true).special(false).build();
        final Column col2 = Column.builder().name("col2").visible(true).special(false).build();
        final Column col3 = Column.builder().name("col3").visible(true).special(false).build();

        final Row row1 = Row.builder().values(Stream.of(null, "2", "3").toList()).build();
        final Row row2 = Row.builder().values(Stream.of(null, "5", "6").toList()).build();
        final Row row3 = Row.builder().values(Stream.of(null, "8", "9").toList()).build();

        final TableResult tableResult = TableResult.builder()
                .columns(List.of(col1, col2, col3))
                .addRow(row1).addRow(row2).addRow(row3)
                .build();

        final TableResultCsvWriter csvWriter = new TableResultCsvWriter(tableResult);
        final String csv = csvWriter.toCsv();

        final String expectedCsv = """
            "col1","col2","col3"
            "","2","3"
            "","5","6"
            "","8","9\"""";

        assertThat(csv).isEqualTo(expectedCsv);
    }
}
