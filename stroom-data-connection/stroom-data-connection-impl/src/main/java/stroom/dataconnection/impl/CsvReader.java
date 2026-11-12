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

import stroom.dataconnection.shared.CsvSettings;
import stroom.util.shared.NullSafe;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import java.io.IOException;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/// Reads CSV into a [DataTable].
final class CsvReader {

    private static final char BYTE_ORDER_MARK = 0xFEFF;

    private CsvReader() {
    }

    /// Parses the CSV of a document.
    ///
    /// @param settings The document's CSV settings, with its data.
    /// @param maxRows  The most rows to read after the header.
    /// @return The rows.
    /// @throws IllegalArgumentException If the CSV cannot be read, or the delimiter is not one character.
    static DataTable parse(final CsvSettings settings, final int maxRows) {
        final String data = withoutByteOrderMark(NullSafe.string(settings.getData()));
        final boolean hasHeader = !Boolean.FALSE.equals(settings.getHeader());
        final CSVFormat format = format(settings);

        final List<String> header = new ArrayList<>();
        final List<List<String>> rows = new ArrayList<>();
        try (final CSVParser parser = CSVParser.parse(new StringReader(data), format)) {
            for (final CSVRecord record : parser) {
                if (hasHeader && record.getRecordNumber() == 1) {
                    record.forEach(name -> header.add(name.trim()));
                } else if (rows.size() < maxRows) {
                    rows.add(record.toList());
                } else {
                    break;
                }
            }
        } catch (final IOException | UncheckedIOException | IllegalStateException e) {
            // The data is already in memory, so these are Commons CSV reporting bad quoting, naming
            // the line.
            throw new IllegalArgumentException("The CSV could not be read: " + e.getMessage(), e);
        }
        return new DataTable(header, rows);
    }

    /// The format the CSV of some settings is read with.
    ///
    /// @param settings CSV settings.
    /// @return The format.
    /// @throws IllegalArgumentException If the delimiter is not one character, or is one that cannot
    ///                                  be used, such as a quote or a line break.
    static CSVFormat format(final CsvSettings settings) {
        final String delimiter = Objects.requireNonNullElse(settings.getDelimiter(), CsvSettings.DEFAULT_DELIMITER);
        if (delimiter.length() != 1) {
            throw new IllegalArgumentException("The CSV delimiter must be one character, not '" + delimiter + "'.");
        }
        try {
            return CSVFormat.RFC4180.builder()
                    .setDelimiter(delimiter.charAt(0))
                    .setIgnoreEmptyLines(true)
                    .get();
        } catch (final IllegalArgumentException e) {
            throw new IllegalArgumentException("The CSV delimiter cannot be '" + delimiter + "': "
                                               + e.getMessage(), e);
        }
    }

    /// @param data Text, or `null`.
    /// @return The number of bytes it takes in UTF-8, as it is stored, without making a copy.
    static long utf8Length(final String data) {
        if (data == null) {
            return 0;
        }
        long bytes = 0;
        for (int index = 0; index < data.length(); index++) {
            final char c = data.charAt(index);
            if (c < 0x80) {
                bytes += 1;
            } else if (c < 0x800) {
                bytes += 2;
            } else if (Character.isHighSurrogate(c) && index + 1 < data.length()
                       && Character.isLowSurrogate(data.charAt(index + 1))) {
                bytes += 4;
                index++;
            } else {
                bytes += 3;
            }
        }
        return bytes;
    }

    // Spreadsheets saving 'CSV UTF-8' start the file with a byte order mark, which would otherwise
    // become part of the first column's name or value.
    private static String withoutByteOrderMark(final String data) {
        return !data.isEmpty() && data.charAt(0) == BYTE_ORDER_MARK
                ? data.substring(1)
                : data;
    }
}
