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
import stroom.externaldatasource.shared.DataColumn;
import stroom.externaldatasource.shared.DataColumnType;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TestColumnDetector {

    @Test
    void namesComeFromTheHeaderAndTypesFromTheValues() {
        final List<DataColumn> columns = detect(CsvSettings.builder()
                .data("team,size,score,founded,active\nRed,4,1.5,2020-01-01,true\nBlue,5,2,2021-06-30,false\n")
                .build());

        assertThat(columns).containsExactly(
                new DataColumn("team", DataColumnType.TEXT),
                new DataColumn("size", DataColumnType.LONG),
                new DataColumn("score", DataColumnType.DOUBLE),
                new DataColumn("founded", DataColumnType.DATE),
                new DataColumn("active", DataColumnType.BOOLEAN));
    }

    @Test
    void typeChosenByTheUserIsKept() {
        // The user made 'size' text, so detecting again does not make it a number.
        final List<DataColumn> columns = detect(CsvSettings.builder()
                .columns(List.of(new DataColumn("size", DataColumnType.TEXT)))
                .data("team,size\nRed,4\n")
                .build());

        assertThat(columns).containsExactly(
                new DataColumn("team", DataColumnType.TEXT),
                new DataColumn("size", DataColumnType.TEXT));
    }

    @Test
    void columnsWithoutHeaderAreNumbered() {
        final List<DataColumn> columns = detect(CsvSettings.builder()
                .header(false)
                .data("Red,4\nBlue,5,extra\n")
                .build());

        assertThat(columns).extracting(DataColumn::getName).containsExactly("column1", "column2", "column3");
    }

    @Test
    void blankAndRepeatedHeaderNamesAreMadeUnique() {
        final List<DataColumn> columns = detect(CsvSettings.builder()
                .data("name,,name\nRed,1,Blue\n")
                .build());

        assertThat(columns).extracting(DataColumn::getName).containsExactly("name", "column2", "name_2");
    }

    @Test
    void headerOnlyGivesTextColumns() {
        final List<DataColumn> columns = detect(CsvSettings.builder()
                .data("team,size\n")
                .build());

        assertThat(columns).extracting(DataColumn::getType)
                .containsExactly(DataColumnType.TEXT, DataColumnType.TEXT);
    }

    private static List<DataColumn> detect(final CsvSettings settings) {
        return ColumnDetector.detect(CsvReader.parse(settings, ColumnDetector.SAMPLE_ROWS), settings.getColumns());
    }
}
