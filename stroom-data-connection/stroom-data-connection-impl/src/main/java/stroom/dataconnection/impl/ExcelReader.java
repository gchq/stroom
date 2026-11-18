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

package stroom.dataconnection.impl;

import stroom.dataconnection.shared.ExcelSettings;
import stroom.util.shared.ModelStringUtil;
import stroom.util.shared.NullSafe;

import org.apache.poi.UnsupportedFileFormatException;
import org.apache.poi.openxml4j.exceptions.OpenXML4JException;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.util.XMLHelper;
import org.apache.poi.xssf.eventusermodel.XSSFReader;
import org.apache.poi.xssf.eventusermodel.XSSFReader.SheetIterator;
import org.apache.poi.xssf.eventusermodel.XSSFSheetXMLHandler;
import org.apache.poi.xssf.eventusermodel.XSSFSheetXMLHandler.SheetContentsHandler;
import org.apache.poi.xssf.model.SharedStrings;
import org.apache.poi.xssf.model.StylesTable;
import org.apache.poi.xssf.usermodel.XSSFComment;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.zip.ZipInputStream;
import javax.xml.parsers.ParserConfigurationException;

/// Reads a sheet of an Excel `.xlsx` workbook into a [DataTable], with POI's streaming reader, so only
/// the rows wanted are held in memory.
///
/// Each cell is read as the text a CSV of it would hold: a formula gives the value Excel last
/// worked out for it; a number is written plainly, without the cell's display format; a date is
/// written in ISO 8601, without a zone, as UTC; a Boolean is `TRUE` or `FALSE`. Merged cells hold their
/// value in their top-left cell only. Rows with no values are skipped; empty cells are `null`.
///
/// A workbook is a zip of XML files. Before POI reads it, the unpacked size is measured, and a workbook
/// that unpacks to more than [#MAX_UNPACKED_BYTES] is refused, as POI unpacks every file into memory.
final class ExcelReader {

    /// The most a workbook may unpack to. Its zip may be at most the stored data limit, 5 MiB by
    /// default, and such a workbook of plain values unpacks to a few tens of MiB.
    static final long MAX_UNPACKED_BYTES = 200L * 1024 * 1024;

    private static final Pattern DATE_1904 = Pattern.compile("date1904\\s*=\\s*[\"'](1|true)[\"']");

    private ExcelReader() {
    }

    /// Reads a sheet of a workbook.
    ///
    /// @param data     The workbook, or `null` for none.
    /// @param settings The sheet to read, and whether its first row is a header.
    /// @param maxRows  The most rows to read after the header.
    /// @return The rows.
    /// @throws IllegalArgumentException If the workbook cannot be read, is not an `.xlsx` workbook, or
    ///                                  has no such sheet.
    static DataTable read(final byte[] data, final ExcelSettings settings, final int maxRows) {
        return read(data, settings, maxRows, MAX_UNPACKED_BYTES);
    }

    /// Reads a sheet of a workbook, refusing one that unpacks to more than the given size.
    ///
    /// @param data             The workbook, or `null` for none.
    /// @param settings         The sheet to read, and whether its first row is a header.
    /// @param maxRows          The most rows to read after the header.
    /// @param maxUnpackedBytes The most the workbook may unpack to.
    /// @return The rows.
    /// @throws IllegalArgumentException If the workbook cannot be read, is not an `.xlsx` workbook,
    ///                                  unpacks to too much, or has no such sheet.
    static DataTable read(final byte[] data,
                          final ExcelSettings settings,
                          final int maxRows,
                          final long maxUnpackedBytes) {
        if (data == null || data.length == 0) {
            return new DataTable(List.of(), List.of());
        }
        requireUnpackedSizeWithin(data, maxUnpackedBytes);
        final boolean hasHeader = !Boolean.FALSE.equals(settings.getHeader());
        OPCPackage workbook = null;
        try {
            workbook = OPCPackage.open(new ByteArrayInputStream(data));
            final XSSFReader reader = new XSSFReader(workbook);
            reader.setUseReadOnlySharedStringsTable(true);
            final SharedStrings strings = reader.getSharedStringsTable();
            final StylesTable styles = reader.getStylesTable();
            final RowCollector rows = new RowCollector(hasHeader, maxRows);
            final DataFormatter formatter = new PlainDataFormatter(uses1904Dates(reader));
            try (final InputStream sheet = openSheet(reader, settings.getSheet())) {
                final XMLReader xmlReader = XMLHelper.newXMLReader();
                xmlReader.setContentHandler(new XSSFSheetXMLHandler(styles, strings, rows, formatter, false));
                xmlReader.parse(new InputSource(sheet));
            } catch (final EnoughRows e) {
                // All the rows wanted have been read.
            }
            return rows.table();
        } catch (final UnsupportedFileFormatException e) {
            throw new IllegalArgumentException("The file is not an Excel .xlsx workbook. It may be an older .xls "
                                               + "workbook, which cannot be read, or protected by a password. "
                                               + "Save it as an unprotected .xlsx workbook in Excel first.", e);
        } catch (final IllegalArgumentException e) {
            throw e;
        } catch (final IOException | OpenXML4JException | SAXException | ParserConfigurationException
                       | RuntimeException e) {
            throw new IllegalArgumentException("The workbook could not be read: " + e.getMessage(), e);
        } finally {
            // Opened from memory to read, so there is nothing to save.
            if (workbook != null) {
                workbook.revert();
            }
        }
    }

    // Measured by reading the zip, as the sizes a zip states may be false.
    private static void requireUnpackedSizeWithin(final byte[] data, final long maxUnpackedBytes) {
        final byte[] buffer = new byte[8192];
        long total = 0;
        try (final ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(data))) {
            while (zip.getNextEntry() != null) {
                int read;
                while ((read = zip.read(buffer)) > 0) {
                    total += read;
                    if (total > maxUnpackedBytes) {
                        throw new IllegalArgumentException("The workbook unpacks to more than "
                                                           + ModelStringUtil.formatIECByteSizeString(maxUnpackedBytes)
                                                           + ", so it cannot be read.");
                    }
                }
            }
        } catch (final IOException e) {
            // Not a zip, such as an old .xls workbook; POI says what it is.
        }
    }

    // Workbooks from older Mac versions of Excel count dates from 1904 rather than 1900.
    private static boolean uses1904Dates(final XSSFReader reader) throws IOException, OpenXML4JException {
        try (final InputStream workbookData = reader.getWorkbookData()) {
            final String workbookXml = new String(workbookData.readAllBytes(), StandardCharsets.UTF_8);
            final int start = workbookXml.indexOf("<workbookPr");
            if (start < 0) {
                return false;
            }
            final int end = workbookXml.indexOf('>', start);
            return DATE_1904.matcher(workbookXml.substring(start, end < 0 ? workbookXml.length() : end)).find();
        }
    }

    // The named sheet, or the first if none is named. Names are matched as Excel does, without regard to
    // case or outer spaces. The other sheets' streams are closed unread.
    private static InputStream openSheet(final XSSFReader reader, final String sheetName)
            throws IOException, OpenXML4JException {
        final SheetIterator sheets = reader.getSheetIterator();
        final List<String> names = new ArrayList<>();
        while (sheets.hasNext()) {
            final InputStream sheet = sheets.next();
            final String name = sheets.getSheetName();
            if (NullSafe.isBlankString(sheetName) || name.trim().equalsIgnoreCase(sheetName.trim())) {
                return sheet;
            }
            names.add(name);
            sheet.close();
        }
        throw new IllegalArgumentException(names.isEmpty()
                ? "The workbook has no sheets."
                : "The workbook has no sheet named '" + sheetName.trim() + "'. Its sheets are: "
                  + String.join(", ", names) + ".");
    }

    // --------------------------------------------------------------------------------

    // Collects the rows of a sheet, the first with values as the header if there is one.
    private static final class RowCollector implements SheetContentsHandler {

        private final boolean hasHeader;
        private final int maxRows;
        private final List<String> header = new ArrayList<>();
        private final List<List<String>> rows = new ArrayList<>();
        private List<String> row;
        private boolean headerRead;

        private RowCollector(final boolean hasHeader, final int maxRows) {
            this.hasHeader = hasHeader;
            this.maxRows = maxRows;
        }

        @Override
        public void startRow(final int rowNum) {
            row = new ArrayList<>();
        }

        @Override
        public void cell(final String cellReference, final String formattedValue, final XSSFComment comment) {
            final int column = cellReference == null
                    ? row.size()
                    : new CellReference(cellReference).getCol();
            while (row.size() < column) {
                row.add(null);
            }
            row.add(formattedValue);
        }

        @Override
        public void endRow(final int rowNum) {
            if (row.stream().allMatch(NullSafe::isBlankString)) {
                return;
            }
            if (hasHeader && !headerRead) {
                header.addAll(row);
                headerRead = true;
            } else {
                rows.add(row);
                if (rows.size() >= maxRows) {
                    throw new EnoughRows();
                }
            }
        }

        private DataTable table() {
            return new DataTable(header, rows);
        }
    }

    // Stops reading a sheet once enough rows have been read.
    private static final class EnoughRows extends RuntimeException {

        private EnoughRows() {
            super(null, null, false, false);
        }
    }

    // Writes numbers plainly and dates in ISO 8601, rather than as the cell displays them, so they read
    // as their column's type.
    // The workbook's date system is passed in, as POI's sheet handler never says which it uses. A time
    // with no date, such as 09:30, is written as a time, which a Date column refuses.
    private static final class PlainDataFormatter extends DataFormatter {

        private final boolean uses1904Dates;

        private PlainDataFormatter(final boolean uses1904Dates) {
            this.uses1904Dates = uses1904Dates;
        }

        @Override
        public String formatRawCellContents(final double value, final int formatIndex, final String formatString) {
            return formatRawCellContents(value, formatIndex, formatString, uses1904Dates);
        }

        @Override
        public String formatRawCellContents(final double value,
                                            final int formatIndex,
                                            final String formatString,
                                            final boolean use1904Windowing) {
            if (DateUtil.isADateFormat(formatIndex, formatString) && DateUtil.isValidExcelDate(value)) {
                if (value < 1) {
                    return LocalTime.ofNanoOfDay(Math.round(value * 86_400_000L) * 1_000_000L).toString();
                }
                final LocalDateTime dateTime = DateUtil.getLocalDateTime(value, use1904Windowing || uses1904Dates);
                return LocalTime.MIDNIGHT.equals(dateTime.toLocalTime())
                        ? dateTime.toLocalDate().toString()
                        : dateTime.toString();
            }
            if (value == Math.rint(value) && Math.abs(value) < 1e15) {
                return Long.toString((long) value);
            }
            return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
        }
    }
}
