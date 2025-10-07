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

package stroom.dashboard.impl.download;

import stroom.query.api.v2.DateTimeFormatSettings;
import stroom.query.api.v2.DateTimeSettings;
import stroom.query.api.v2.Field;
import stroom.query.api.v2.Format;
import stroom.query.api.v2.Format.Type;
import stroom.query.api.v2.FormatSettings;
import stroom.query.api.v2.NumberFormatSettings;
import stroom.query.common.v2.format.DateTimeFormatter;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormat;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.streaming.SXSSFSheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;

import java.io.IOException;
import java.io.OutputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class ExcelTarget implements SearchResultWriter.Target {

    // Excel cannot store more than 32767 characters in a cell so we must truncate some values.
    private static final int EXCEL_MAX_CELL_CHARACTERS = 32767;
    private static final int TRUNCATED_LENGTH = EXCEL_MAX_CELL_CHARACTERS - 3;
    private static final String TRUNCATION_MARKER = "...";

    private final OutputStream outputStream;
    private final DateTimeSettings dateTimeSettings;

    private SXSSFWorkbook workbook;
    private SXSSFSheet sheet;
    private Row row;

    private int colNum = 0;
    private int rowNum = 0;

    private CellStyle headingStyle;
    private final Map<Field, Optional<CellStyle>> fieldStyles = new HashMap<>();

    public ExcelTarget(final OutputStream outputStream, final DateTimeSettings dateTimeSettings) {
        this.outputStream = outputStream;
        this.dateTimeSettings = dateTimeSettings;
    }

    @Override
    public void start() {
        // Create a workbook with 100 rows in memory. Exceeding rows will be
        // flushed to disk.
        workbook = new SXSSFWorkbook(100);

        // Create a style for headings.
        final Font headingFont = workbook.createFont();
        headingFont.setBold(true);
        headingFont.setColor(IndexedColors.WHITE.getIndex());
        headingStyle = workbook.createCellStyle();
        headingStyle.setFont(headingFont);
        headingStyle.setFillBackgroundColor(IndexedColors.BLACK.getIndex());
        headingStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
    }

    @Override
    public void end() throws IOException {
        // Write the workbook to the output stream.
        workbook.write(outputStream);
        outputStream.close();

        // Close the workbook.
        workbook.close();

        // Dispose of temporary files backing workbook on disk.
        workbook.dispose();
    }

    @Override
    public void startTable(final String tableName) {
        sheet = workbook.createSheet(tableName);
        rowNum = 0;
        colNum = 0;
    }

    @Override
    public void endTable() {
        // Auto-size tracked columns
        for (var columnIndex : sheet.getTrackedColumnsForAutoSizing()) {
            sheet.autoSizeColumn(columnIndex);
        }
    }

    @Override
    public void startLine() {
        row = sheet.createRow(rowNum++);
        colNum = 0;
    }

    @Override
    public void endLine() {
        // Do nothing.
    }

    @Override
    public void writeHeading(final int fieldIndex, final Field field, final String heading) {
        final Cell cell = row.createCell(colNum++);
        cell.setCellValue(heading);
        cell.setCellStyle(headingStyle);

        // Auto-size datetime and numeric columns
        if (field.getFormat() != null) {
            final Format.Type fieldType = field.getFormat().getType();
            if (fieldType == Type.DATE_TIME || fieldType == Type.NUMBER) {
                sheet.trackColumnForAutoSizing(fieldIndex);
            }
        }
    }

    @Override
    public void writeValue(final Field field, final String value) {
        if (value == null) {
            colNum++;
        } else {
            final Cell cell = row.createCell(colNum++);
            setCellValue(workbook, cell, field, value);
        }
    }

    private void setCellValue(final SXSSFWorkbook workbook, final Cell cell, final Field field, final String value) {
        if (value != null) {
            if (field == null) {
                general(cell, value);

            } else {
                Type type = Type.GENERAL;
                if (field.getFormat() != null) {
                    type = field.getFormat().getType();
                }

                switch (type) {
                    case TEXT:
                        text(cell, value, field);
                        break;
                    case NUMBER:
                        number(cell, value, field);
                        break;
                    case DATE_TIME:
                        dateTime(cell, value, field);
                        break;
                    default:
                        general(cell, value);
                        break;
                }
            }
        }
    }

    private void general(final Cell cell, final String value) {
        getText(value).ifPresent(cell::setCellValue);
    }

    private void text(final Cell cell, final String value, final Field field) {
        getText(value).ifPresent(cell::setCellValue);
        setCellFormat(cell, field);
    }

    private void dateTime(final Cell cell, final String value, final Field field) {
        final Double dbl = TypeConverter.getDouble(value);
        if (dbl != null) {
            final long ms = dbl.longValue();
            final Date date = new Date(ms);
            cell.setCellValue(date);
            setCellFormat(cell, field);

        } else {
            try {
                final DateTimeFormatter dateTimeFormatter = DateTimeFormatter.create(
                        (DateTimeFormatSettings) field.getFormat().getSettings(), dateTimeSettings);
                final LocalDateTime dateTime = dateTimeFormatter.parse(value);
                cell.setCellValue(dateTime);
                setCellFormat(cell, field);
            } catch (DateTimeParseException e) {
                general(cell, value);
            }
        }
    }

    private void number(final Cell cell, final String value, final Field field) {
        final Double dbl = TypeConverter.getDouble(value);
        if (dbl != null) {
            cell.setCellValue(dbl);
        } else {
            general(cell, value);
        }

        setCellFormat(cell, field);
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

    private void setCellFormat(final Cell cell, final Field field) {
        final Optional<CellStyle> fieldStyle = getFieldStyle(field);
        fieldStyle.ifPresent(cell::setCellStyle);
    }

    private Optional<CellStyle> getFieldStyle(final Field key) {
        if (key == null) {
            return Optional.empty();
        }

        return fieldStyles.computeIfAbsent(key, field -> {
            DataFormat dataFormat = null;
            CellStyle cellStyle = null;

            Type type = Type.GENERAL;
            FormatSettings settings = null;
            if (field.getFormat() != null) {
                type = field.getFormat().getType();
                settings = field.getFormat().getSettings();
            }

            switch (type) {
                case TEXT:
                    dataFormat = workbook.createDataFormat();
                    cellStyle = workbook.createCellStyle();
                    cellStyle.setDataFormat(dataFormat.getFormat("@"));
                    break;
                case NUMBER:
                    dataFormat = workbook.createDataFormat();
                    cellStyle = workbook.createCellStyle();

                    if (settings instanceof NumberFormatSettings) {
                        final NumberFormatSettings numberFormatSettings = (NumberFormatSettings) settings;
                        final StringBuilder sb = new StringBuilder();

                        if (Boolean.TRUE.equals(numberFormatSettings.getUseSeparator())) {
                            sb.append("#,##0");
                        } else {
                            sb.append("#");
                        }
                        if (numberFormatSettings.getDecimalPlaces() != null
                                && numberFormatSettings.getDecimalPlaces() > 0) {
                            sb.append(".");
                            for (int i = 0; i < numberFormatSettings.getDecimalPlaces(); i++) {
                                sb.append("0");
                            }
                        }

                        final String pattern = sb.toString();
                        cellStyle.setDataFormat(dataFormat.getFormat(pattern));
                    } else {
                        // Basic number format, with no decimals
                        cellStyle.setDataFormat(dataFormat.getFormat("#"));
                    }
                    break;
                case DATE_TIME:
                    String pattern = "dd/mm/yyyy hh:mm:ss";

                    if (settings instanceof DateTimeFormatSettings) {
                        final DateTimeFormatSettings dateTimeFormatSettings = (DateTimeFormatSettings) settings;
                        if (dateTimeFormatSettings.getPattern() != null
                                && dateTimeFormatSettings.getPattern().trim().length() > 0) {
                            pattern = dateTimeFormatSettings.getPattern();
                            pattern = pattern.replaceAll("'", "");
                            pattern = pattern.replaceAll("\\.SSS.*$", "");
                        }
                    }

                    dataFormat = workbook.createDataFormat();
                    cellStyle = workbook.createCellStyle();
                    cellStyle.setDataFormat(dataFormat.getFormat(pattern));
                    break;
                default:
                    break;
            }

            return Optional.ofNullable(cellStyle);
        });
    }
}
