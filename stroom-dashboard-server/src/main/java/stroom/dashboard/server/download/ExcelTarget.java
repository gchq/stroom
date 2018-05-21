/*
 * Copyright 2017 Crown Copyright
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

package stroom.dashboard.server.download;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormat;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import stroom.dashboard.server.TypeConverter;
import stroom.dashboard.shared.DateTimeFormatSettings;
import stroom.dashboard.shared.Field;
import stroom.dashboard.shared.Format.Type;
import stroom.dashboard.shared.FormatSettings;
import stroom.dashboard.shared.NumberFormatSettings;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Date;
import java.util.Optional;

public class ExcelTarget implements SearchResultWriter.Target {
    // Excel cannot store more than 32767 characters in a cell so we must truncate some values.
    private static final int EXCEL_MAX_CELL_CHARACTERS = 32767;
    private static final int TRUNCATED_LENGTH = EXCEL_MAX_CELL_CHARACTERS - 3;
    private static final String TRUNCATION_MARKER = "...";

    private final OutputStream outputStream;

    private SXSSFWorkbook wb;
    private Sheet sh;
    private Row row;

    private int colNum = 0;
    private int rowNum = 0;

    public ExcelTarget(final OutputStream outputStream) {
        this.outputStream = outputStream;
    }

    @Override
    public void start() {
        // Create a workbook with 100 rows in memory. Exceeding rows will be
        // flushed to disk.
        wb = new SXSSFWorkbook(100);
        sh = wb.createSheet();
    }

    @Override
    public void end() throws IOException {
        // Write the workbook to the output stream.
        wb.write(outputStream);
        outputStream.close();

        // Close the workbook.
        wb.close();

        // Dispose of temporary files backing workbook on disk.
        wb.dispose();
    }

    @Override
    public void startLine() {
        row = sh.createRow(rowNum++);
        colNum = 0;
    }

    @Override
    public void endLine() {
        // Do nothing.
    }

    @Override
    public void writeHeading(final Field field, final String heading) {
        // Create a style for headings.
        final Font headingFont = wb.createFont();
        headingFont.setBold(true);
        final CellStyle headingStyle = wb.createCellStyle();
        headingStyle.setFont(headingFont);

        final Cell cell = row.createCell(colNum++);
        cell.setCellType(CellType.STRING);
        cell.setCellValue(heading);
        cell.setCellStyle(headingStyle);
    }

    @Override
    public void writeValue(final Field field, final String value) {
        if (value == null) {
            colNum++;
        } else {
            final Cell cell = row.createCell(colNum++);
            setCellValue(wb, cell, field, value);
        }
    }

    private void setCellValue(final SXSSFWorkbook wb, final Cell cell, final Field field, final String value) {
        if (value != null) {
            if (field == null) {
                general(wb, cell, value);

            } else {
                Type type = Type.GENERAL;
                FormatSettings settings = null;
                if (field.getFormat() != null) {
                    type = field.getFormat().getType();
                    settings = field.getFormat().getSettings();
                }

                switch (type) {
                    case TEXT:
                        getText(value).ifPresent(str -> {
                            cell.setCellValue(str);
                            cell.setCellType(CellType.STRING);
                        });
                        break;
                    case NUMBER:
                        number(wb, cell, value, settings);
                        break;
                    case DATE_TIME:
                        dateTime(wb, cell, value, settings);
                        break;
                    default:
                        general(wb, cell, value);
                        break;
                }
            }
        }
    }

    private void general(final SXSSFWorkbook wb, final Cell cell, final String value) {
        getText(value).ifPresent(cell::setCellValue);
    }

    private void dateTime(final SXSSFWorkbook wb, final Cell cell, final String value, final FormatSettings settings) {
        final Double dbl = TypeConverter.getDouble(value);
        if (dbl != null) {
            final long ms = dbl.longValue();

            final Date date = new Date(ms);
            cell.setCellValue(date);
            cell.setCellType(CellType.NUMERIC);

            String pattern = "dd/mm/yyyy hh:mm:ss";

            if (settings instanceof DateTimeFormatSettings) {
                final DateTimeFormatSettings dateTimeFormatSettings = (DateTimeFormatSettings) settings;
                if (dateTimeFormatSettings.getPattern() != null
                        && dateTimeFormatSettings.getPattern().trim().length() > 0) {
                    pattern = dateTimeFormatSettings.getPattern();
                    pattern = pattern.replaceAll("'", "");
                    pattern = pattern.replaceAll("\\.SSS", "");
                }
            }

            final DataFormat df = wb.createDataFormat();
            final CellStyle cs = wb.createCellStyle();
            cs.setDataFormat(df.getFormat(pattern));
            cell.setCellStyle(cs);

        } else {
            getText(value).ifPresent(cell::setCellValue);
        }
    }

    private void number(final SXSSFWorkbook wb, final Cell cell, final String value, final FormatSettings settings) {
        final Double dbl = TypeConverter.getDouble(value);
        if (dbl != null) {
            cell.setCellValue(dbl);
            cell.setCellType(CellType.NUMERIC);

            if (settings instanceof NumberFormatSettings) {
                final NumberFormatSettings numberFormatSettings = (NumberFormatSettings) settings;
                final StringBuilder sb = new StringBuilder();

                if (Boolean.TRUE.equals(numberFormatSettings.getUseSeparator())) {
                    sb.append("#,##0");
                } else {
                    sb.append("#");
                }
                if (numberFormatSettings.getDecimalPlaces() != null && numberFormatSettings.getDecimalPlaces() > 0) {
                    sb.append(".");
                    for (int i = 0; i < numberFormatSettings.getDecimalPlaces(); i++) {
                        sb.append("0");
                    }
                }

                final String pattern = sb.toString();

                final DataFormat df = wb.createDataFormat();
                final CellStyle cs = wb.createCellStyle();
                cs.setDataFormat(df.getFormat(pattern));
                cell.setCellStyle(cs);
            }
        } else {
            getText(value).ifPresent(cell::setCellValue);
        }
    }

    private Optional<String> getText(final String value) {
        return Optional.ofNullable(value)
                .map(text -> {
                    if (text.length() > EXCEL_MAX_CELL_CHARACTERS) {
                        return text.substring(0, TRUNCATED_LENGTH) + TRUNCATION_MARKER;
                    } else {
                        return text;
                    }
                });
    }
}
