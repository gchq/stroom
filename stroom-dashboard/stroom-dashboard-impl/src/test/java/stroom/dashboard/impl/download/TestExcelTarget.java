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

package stroom.dashboard.impl.download;

import stroom.query.api.Column;
import stroom.query.api.Format;

import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class TestExcelTarget {

    // Not a number or date that Excel would show in another way.
    @Test
    void durationsAreTextAsTheTableShowsThem() throws IOException {
        final Column column = Column.builder().format(Format.DURATION).build();
        final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        final ExcelTarget target = new ExcelTarget(outputStream, null);
        target.start();
        target.startTable("Table");
        target.startLine();
        target.writeHeading(0, column, "Elapsed");
        target.endLine();
        target.startLine();
        target.writeValue(column, "49:30");
        target.endLine();
        target.endTable();
        target.end();

        try (final XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(outputStream.toByteArray()))) {
            final Row row = workbook.getSheetAt(0).getRow(1);
            assertThat(row.getCell(0).getCellType()).isEqualTo(CellType.STRING);
            assertThat(row.getCell(0).getStringCellValue()).isEqualTo("49:30");
        }
    }
}
