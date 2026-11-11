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

import stroom.externaldatasource.shared.DataColumn;
import stroom.externaldatasource.shared.DataColumnType;
import stroom.externaldatasource.shared.ExcelSettings;

import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestExcelReader {

    private static final ExcelSettings FIRST_SHEET = ExcelSettings.builder().build();

    /// Builds a workbook of one or more sheets, each a list of rows of cell values. A value may be a
    /// String, Number, Boolean, LocalDateTime or `null` for no cell.
    static byte[] workbook(final Workbook workbook, final Object[][]... sheets) {
        try (workbook) {
            final CellStyle dateStyle = workbook.createCellStyle();
            dateStyle.setDataFormat(workbook.getCreationHelper().createDataFormat().getFormat("yyyy-mm-dd hh:mm"));
            final CellStyle thousandsStyle = workbook.createCellStyle();
            thousandsStyle.setDataFormat(workbook.getCreationHelper().createDataFormat().getFormat("#,##0.00"));
            for (int sheetIndex = 0; sheetIndex < sheets.length; sheetIndex++) {
                final Sheet sheet = workbook.createSheet("Sheet" + (sheetIndex + 1));
                final Object[][] rows = sheets[sheetIndex];
                for (int rowIndex = 0; rowIndex < rows.length; rowIndex++) {
                    final Row row = sheet.createRow(rowIndex);
                    for (int column = 0; column < rows[rowIndex].length; column++) {
                        final Object value = rows[rowIndex][column];
                        if (value instanceof final String text) {
                            row.createCell(column).setCellValue(text);
                        } else if (value instanceof final Number number) {
                            row.createCell(column).setCellValue(number.doubleValue());
                            row.getCell(column).setCellStyle(thousandsStyle);
                        } else if (value instanceof final Boolean bool) {
                            row.createCell(column).setCellValue(bool);
                        } else if (value instanceof final LocalDateTime dateTime) {
                            row.createCell(column).setCellValue(dateTime);
                            row.getCell(column).setCellStyle(dateStyle);
                        }
                    }
                }
            }
            final ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
        } catch (final IOException e) {
            throw new RuntimeException(e);
        }
    }

    static byte[] xlsx(final Object[][]... sheets) {
        return workbook(new XSSFWorkbook(), sheets);
    }

    @Test
    void headerAndRowsAreReadAsPlainText() {
        final byte[] data = xlsx(new Object[][]{
                {"team", "members", "founded", "on_call"},
                {"Red", 4, LocalDateTime.of(2020, 1, 1, 0, 0), true},
                {"Blue", 1234.5, LocalDateTime.of(2021, 6, 30, 9, 30), false}});

        final DataTable table = ExcelReader.read(data, FIRST_SHEET, 100);

        assertThat(table.header()).containsExactly("team", "members", "founded", "on_call");
        // Not '1,234.50' as displayed, and dates in ISO 8601.
        assertThat(table.rows()).containsExactly(
                List.of("Red", "4", "2020-01-01", "TRUE"),
                List.of("Blue", "1234.5", "2021-06-30T09:30", "FALSE"));
    }

    @Test
    void valuesReadAsTheirColumnTypes() {
        final byte[] data = xlsx(new Object[][]{
                {"members", "founded", "on_call"},
                {4, LocalDateTime.of(2021, 6, 30, 9, 30), true}});
        final List<String> row = ExcelReader.read(data, FIRST_SHEET, 100).rows().getFirst();

        assertThat(DataValues.read(row.get(0), DataColumnType.LONG).toLong()).isEqualTo(4L);
        assertThat(DataValues.read(row.get(1), DataColumnType.DATE).toString()).startsWith("2021-06-30T09:30");
        assertThat(DataValues.read(row.get(2), DataColumnType.BOOLEAN).toBoolean()).isTrue();
    }

    @Test
    void emptyRowsAreSkippedAndEmptyCellsAreNull() {
        final byte[] data = xlsx(new Object[][]{
                {"a", "b", "c"},
                {},
                {"1", null, "3"},
                {null, null, "z"}});

        final DataTable table = ExcelReader.read(data, FIRST_SHEET, 100);

        assertThat(table.rows()).containsExactly(Arrays.asList("1", null, "3"), Arrays.asList(null, null, "z"));
    }

    @Test
    void namedSheetIsRead() {
        final byte[] data = xlsx(new Object[][]{{"first"}, {"1"}}, new Object[][]{{"second"}, {"2"}});

        final DataTable table = ExcelReader.read(data, ExcelSettings.builder().sheet("Sheet2").build(), 100);

        assertThat(table.header()).containsExactly("second");
    }

    @Test
    void missingSheetIsReportedWithTheSheetsThereAre() {
        final byte[] data = xlsx(new Object[][]{{"first"}}, new Object[][]{{"second"}});

        assertThatThrownBy(() -> ExcelReader.read(data, ExcelSettings.builder().sheet("Totals").build(), 100))
                .hasMessage("The workbook has no sheet named 'Totals'. Its sheets are: Sheet1, Sheet2.");
    }

    @Test
    void workbookWithoutHeaderHasRowsOnly() {
        final byte[] data = xlsx(new Object[][]{{"Red", 1}, {"Blue", 2}});

        final DataTable table = ExcelReader.read(data, ExcelSettings.builder().header(false).build(), 100);

        assertThat(table.header()).isEmpty();
        assertThat(table.rows()).hasSize(2);
    }

    @Test
    void rowsStopAtTheLimit() {
        final byte[] data = xlsx(new Object[][]{{"n"}, {1}, {2}, {3}});

        assertThat(ExcelReader.read(data, FIRST_SHEET, 2).rows()).containsExactly(List.of("1"), List.of("2"));
    }

    @Test
    void mergedCellHoldsItsValueInItsTopLeftCell() throws IOException {
        try (final XSSFWorkbook workbook = new XSSFWorkbook()) {
            final Sheet sheet = workbook.createSheet("Sheet1");
            sheet.createRow(0).createCell(0).setCellValue("a");
            sheet.getRow(0).createCell(1).setCellValue("b");
            sheet.createRow(1).createCell(0).setCellValue("merged");
            sheet.addMergedRegion(new CellRangeAddress(1, 1, 0, 1));
            final ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);

            assertThat(ExcelReader.read(out.toByteArray(), FIRST_SHEET, 100).rows())
                    .containsExactly(List.of("merged"));
        }
    }

    @Test
    void formulaGivesItsLastValue() throws IOException {
        try (final XSSFWorkbook workbook = new XSSFWorkbook()) {
            final Sheet sheet = workbook.createSheet("Sheet1");
            sheet.createRow(0).createCell(0).setCellValue("total");
            sheet.createRow(1).createCell(0).setCellFormula("2*21");
            workbook.getCreationHelper().createFormulaEvaluator().evaluateAll();
            final ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);

            assertThat(ExcelReader.read(out.toByteArray(), FIRST_SHEET, 100).rows())
                    .containsExactly(List.of("42"));
        }
    }

    @Test
    void oldXlsWorkbookIsRefused() {
        final byte[] data = workbook(new HSSFWorkbook(), new Object[][]{{"a"}});

        assertThatThrownBy(() -> ExcelReader.read(data, FIRST_SHEET, 100))
                .hasMessageContaining("older .xls workbook, which cannot be read");
    }

    @Test
    void fileThatIsNotAWorkbookIsRefused() {
        assertThatThrownBy(() -> ExcelReader.read("team,size\nRed,1\n".getBytes(), FIRST_SHEET, 100))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void noWorkbookGivesAnEmptyTable() {
        assertThat(ExcelReader.read(null, FIRST_SHEET, 100).rows()).isEmpty();
    }

    @Test
    void columnsAreDetectedFromTheSheet() {
        final byte[] data = xlsx(new Object[][]{
                {"team", "members", "founded", "on_call"},
                {"Red", 4, LocalDateTime.of(2020, 1, 1, 0, 0), true}});

        assertThat(ColumnDetector.detect(ExcelReader.read(data, FIRST_SHEET, 100), null)).containsExactly(
                new DataColumn("team", DataColumnType.TEXT),
                new DataColumn("members", DataColumnType.LONG),
                new DataColumn("founded", DataColumnType.DATE),
                new DataColumn("on_call", DataColumnType.BOOLEAN));
    }

    @Test
    void datesOfA1904WorkbookAreRead() throws IOException {
        // Older Mac versions of Excel count dates from 1904.
        try (final XSSFWorkbook workbook = new XSSFWorkbook()) {
            workbook.getCTWorkbook().addNewWorkbookPr().setDate1904(true);
            final CellStyle dateStyle = workbook.createCellStyle();
            dateStyle.setDataFormat(workbook.getCreationHelper().createDataFormat().getFormat("yyyy-mm-dd"));
            final Sheet sheet = workbook.createSheet("Sheet1");
            sheet.createRow(0).createCell(0).setCellValue("date");
            sheet.createRow(1).createCell(0).setCellValue(LocalDateTime.of(2026, 11, 11, 0, 0));
            sheet.getRow(1).getCell(0).setCellStyle(dateStyle);
            final ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);

            assertThat(ExcelReader.read(out.toByteArray(), FIRST_SHEET, 100).rows())
                    .containsExactly(List.of("2026-11-11"));
        }
    }

    @Test
    void inlineStringsAreRead() throws IOException {
        // The streaming writer, as some other tools do, writes strings inline rather than shared.
        try (final SXSSFWorkbook workbook = new SXSSFWorkbook()) {
            final Sheet sheet = workbook.createSheet("Sheet1");
            sheet.createRow(0).createCell(0).setCellValue("team");
            sheet.createRow(1).createCell(0).setCellValue("Red");
            final ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            workbook.dispose();

            final DataTable table = ExcelReader.read(out.toByteArray(), FIRST_SHEET, 100);

            assertThat(table.header()).containsExactly("team");
            assertThat(table.rows()).containsExactly(List.of("Red"));
        }
    }

    @Test
    void timeWithNoDateIsReadAsATime() throws IOException {
        try (final XSSFWorkbook workbook = new XSSFWorkbook()) {
            final CellStyle timeStyle = workbook.createCellStyle();
            timeStyle.setDataFormat(workbook.getCreationHelper().createDataFormat().getFormat("hh:mm"));
            final Sheet sheet = workbook.createSheet("Sheet1");
            sheet.createRow(0).createCell(0).setCellValue("opens");
            sheet.createRow(1).createCell(0).setCellValue(9.5 / 24);
            sheet.getRow(1).getCell(0).setCellStyle(timeStyle);
            final ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);

            assertThat(ExcelReader.read(out.toByteArray(), FIRST_SHEET, 100).rows())
                    .containsExactly(List.of("09:30"));
        }
    }

    @Test
    void sheetIsFoundWithoutRegardToCaseOrSpaces() {
        final byte[] data = xlsx(new Object[][]{{"first"}}, new Object[][]{{"second"}});

        final DataTable table = ExcelReader.read(data, ExcelSettings.builder().sheet(" sheet2 ").build(), 100);

        assertThat(table.header()).containsExactly("second");
    }

    @Test
    void workbookThatUnpacksTooLargeIsRefused() {
        // A small zip can unpack to far more than its size, and POI unpacks it into memory.
        final byte[] data = xlsx(new Object[][]{{"team"}, {"Red"}});

        assertThatThrownBy(() -> ExcelReader.read(data, FIRST_SHEET, 100, 100))
                .hasMessageContaining("unpacks to more than");
    }
}
