/*
 * Copyright 2016-2026 Crown Copyright
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

import stroom.query.api.DateTimeSettings;

import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers writing a block of prose - a report's AI summary - alongside the data, for the two output types
 * that have somewhere to put it.
 */
class TestTargetSummaryOutput {

    private static final String SUMMARY = """
            Two users account for most of the activity.

            Nothing looks out of the ordinary.""";

    @Test
    void excel_putsTheSummaryOnASheetOfItsOwn() throws IOException {
        final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        final ExcelTarget target = new ExcelTarget(outputStream, DateTimeSettings.builder().build());
        target.start();
        target.startTable("Report");
        target.endTable();
        target.writeText("AI Summary", SUMMARY);
        target.end();

        try (final Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(outputStream.toByteArray()))) {
            final Sheet sheet = workbook.getSheet("AI Summary");
            assertThat(sheet).isNotNull();

            // A line to a row, so the text reads as text rather than as one unwrapped cell.
            final List<String> lines = new ArrayList<>();
            for (int i = 0; i <= sheet.getLastRowNum(); i++) {
                lines.add(sheet.getRow(i).getCell(0).getStringCellValue());
            }
            assertThat(lines).containsExactly(
                    "Two users account for most of the activity.",
                    "",
                    "Nothing looks out of the ordinary.");
        }
    }

    @Test
    void excel_writesNoSheetWhenThereIsNoSummary() throws IOException {
        final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        final ExcelTarget target = new ExcelTarget(outputStream, DateTimeSettings.builder().build());
        target.start();
        target.startTable("Report");
        target.endTable();
        target.writeText("AI Summary", null);
        target.writeText("AI Summary", "   ");
        target.end();

        try (final Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(outputStream.toByteArray()))) {
            assertThat(workbook.getSheet("AI Summary")).isNull();
        }
    }

    /**
     * A cell cannot hold more than 32767 characters, so a line longer than that has to be cut rather than
     * failing the whole report.
     */
    @Test
    void excel_truncatesALineTooLongForACell() throws IOException {
        final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        final ExcelTarget target = new ExcelTarget(outputStream, DateTimeSettings.builder().build());
        target.start();
        target.startTable("Report");
        target.endTable();
        target.writeText("AI Summary", "x".repeat(40_000));
        target.end();

        try (final Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(outputStream.toByteArray()))) {
            final String value = workbook.getSheet("AI Summary").getRow(0).getCell(0).getStringCellValue();
            assertThat(value).hasSize(32767);
            assertThat(value).endsWith("...");
        }
    }

    @Test
    void markdown_putsTheSummaryInASectionAfterTheTable() throws IOException {
        final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        final MarkdownTarget target = new MarkdownTarget(outputStream);
        target.start();
        target.writeSection("AI Summary", SUMMARY);
        target.end();

        assertThat(outputStream.toString(StandardCharsets.UTF_8))
                .isEqualTo("\n## AI Summary\n\n" + SUMMARY + "\n");
    }

    @Test
    void markdown_writesNothingWhenThereIsNoSummary() throws IOException {
        final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        final MarkdownTarget target = new MarkdownTarget(outputStream);
        target.start();
        target.writeSection("AI Summary", null);
        target.writeSection("AI Summary", "  ");
        target.end();

        assertThat(outputStream.toString(StandardCharsets.UTF_8)).isEmpty();
    }
}
