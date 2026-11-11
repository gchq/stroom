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

import stroom.externaldatasource.shared.DataColumnType;
import stroom.query.api.datasource.FieldType;
import stroom.query.language.functions.Val;
import stroom.query.language.functions.ValBoolean;
import stroom.query.language.functions.ValDate;
import stroom.query.language.functions.ValDouble;
import stroom.query.language.functions.ValLong;
import stroom.query.language.functions.ValNull;
import stroom.query.language.functions.ValString;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/// Reads the text values of stored data, such as CSV, as the types of their columns, and guesses
/// those types from the values.
final class DataValues {

    // A date and time written with a space rather than a 'T' between them, as spreadsheets often do.
    private static final Pattern SPACED_DATE_TIME = Pattern.compile("^\\d{4}-\\d{2}-\\d{2} \\d");
    private static final Pattern WHOLE_NUMBER = Pattern.compile("^[-+]?\\d+$");
    // A whole number with a leading zero, such as a code '0123', but not '0' or '0.5'.
    private static final Pattern LEADING_ZERO = Pattern.compile("^[-+]?0\\d");
    private static final Pattern DECIMAL_NUMBER = Pattern.compile("^[-+]?(\\d+\\.?\\d*|\\.\\d+)([eE][-+]?\\d+)?$");

    // The order types are tried in when guessing: the narrowest first, so whole numbers are not
    // taken as doubles, and text is what is left.
    private static final List<DataColumnType> GUESS_ORDER = List.of(
            DataColumnType.LONG,
            DataColumnType.DOUBLE,
            DataColumnType.DATE,
            DataColumnType.BOOLEAN);

    private DataValues() {
    }

    /// Reads a value as its column's type.
    ///
    /// @param text The value as written, or `null` if the row has no value for the column.
    /// @param type The column's type, or `null` for [DataColumnType#TEXT].
    /// @return The value. An empty value is [ValNull].
    /// @throws IllegalArgumentException If the value is not of the column's type.
    static Val read(final String text, final DataColumnType type) {
        if (text == null || text.isEmpty()) {
            return ValNull.INSTANCE;
        }
        final DataColumnType columnType = Objects.requireNonNullElse(type, DataColumnType.TEXT);
        if (columnType == DataColumnType.TEXT) {
            return ValString.create(text);
        }
        final String trimmed = text.trim();
        if (trimmed.isEmpty()) {
            return ValNull.INSTANCE;
        }
        return switch (columnType) {
            case LONG -> ValLong.create(parseLong(trimmed));
            case DOUBLE -> ValDouble.create(parseDouble(trimmed));
            case DATE -> ValDate.create(parseDate(trimmed));
            case BOOLEAN -> ValBoolean.create(parseBoolean(trimmed));
            case TEXT -> ValString.create(text);
        };
    }

    /// Guesses a column's type from its values: the narrowest of Long, Double, Date and Boolean that
    /// every non-empty value is, or else Text.
    ///
    /// A column that would lose something as a number is Text: whole numbers too big for a Long, such
    /// as long identifiers, which a Double would round, and codes with leading zeros, such as `0123`.
    ///
    /// @param values Values of the column. Empty and `null` values are ignored.
    /// @return The type. A column with no values is Text.
    static DataColumnType guessType(final Collection<String> values) {
        final List<String> nonEmpty = values.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .toList();
        if (nonEmpty.isEmpty()) {
            return DataColumnType.TEXT;
        }
        if (nonEmpty.stream().anyMatch(value -> LEADING_ZERO.matcher(value).find())) {
            return DataColumnType.TEXT;
        }
        if (nonEmpty.stream().allMatch(value -> WHOLE_NUMBER.matcher(value).matches())
            && !nonEmpty.stream().allMatch(isType(DataColumnType.LONG))) {
            return DataColumnType.TEXT;
        }
        for (final DataColumnType type : GUESS_ORDER) {
            if (nonEmpty.stream().allMatch(isType(type))) {
                return type;
            }
        }
        return DataColumnType.TEXT;
    }

    /// @param type A column type, or `null` for Text.
    /// @return The query field type its values are read as.
    static FieldType toFieldType(final DataColumnType type) {
        return switch (Objects.requireNonNullElse(type, DataColumnType.TEXT)) {
            case LONG -> FieldType.LONG;
            case DOUBLE -> FieldType.DOUBLE;
            case DATE -> FieldType.DATE;
            case BOOLEAN -> FieldType.BOOLEAN;
            case TEXT -> FieldType.TEXT;
        };
    }

    private static Predicate<String> isType(final DataColumnType type) {
        return value -> {
            try {
                read(value, type);
                return true;
            } catch (final IllegalArgumentException e) {
                return false;
            }
        };
    }

    private static long parseLong(final String text) {
        if (!WHOLE_NUMBER.matcher(text).matches()) {
            throw new IllegalArgumentException("'" + text + "' is not a whole number");
        }
        try {
            return Long.parseLong(text);
        } catch (final NumberFormatException e) {
            throw new IllegalArgumentException("'" + text + "' is too big for a whole number", e);
        }
    }

    // Only plain decimal numbers, as Java's own parser also reads NaN, Infinity, hexadecimal and a
    // trailing 'd' or 'f', which a CSV number should not be.
    private static double parseDouble(final String text) {
        if (!DECIMAL_NUMBER.matcher(text).matches()) {
            throw new IllegalArgumentException("'" + text + "' is not a number");
        }
        final double value = Double.parseDouble(text);
        if (Double.isInfinite(value)) {
            throw new IllegalArgumentException("'" + text + "' is too big a number");
        }
        return value;
    }

    private static boolean parseBoolean(final String text) {
        if ("true".equalsIgnoreCase(text)) {
            return true;
        }
        if ("false".equalsIgnoreCase(text)) {
            return false;
        }
        throw new IllegalArgumentException("'" + text + "' is not true or false");
    }

    /// Reads an ISO 8601 date, or date and time. A time with no zone is taken as UTC. A space may
    /// stand for the 'T' between date and time.
    ///
    /// @param text The date.
    /// @return The instant it stands for.
    /// @throws IllegalArgumentException If the text is not such a date.
    static Instant parseDate(final String text) {
        final String iso = SPACED_DATE_TIME.matcher(text).find()
                ? text.substring(0, 10) + "T" + text.substring(11)
                : text;
        try {
            if (iso.length() == 10) {
                return LocalDate.parse(iso).atStartOfDay(ZoneOffset.UTC).toInstant();
            }
            final char last = iso.charAt(iso.length() - 1);
            if (last == 'Z' || iso.matches(".*[+-]\\d{2}(:?\\d{2})?$") && iso.indexOf('T') > 0) {
                return OffsetDateTime.parse(normaliseOffset(iso)).toInstant();
            }
            return LocalDateTime.parse(iso).toInstant(ZoneOffset.UTC);
        } catch (final DateTimeParseException e) {
            throw new IllegalArgumentException("'" + text + "' is not an ISO 8601 date", e);
        }
    }

    // OffsetDateTime wants +hh:mm, so +hh and +hhmm are rewritten.
    private static String normaliseOffset(final String iso) {
        if (iso.endsWith("Z")) {
            return iso;
        }
        if (iso.matches(".*[+-]\\d{2}$")) {
            return iso + ":00";
        }
        if (iso.matches(".*[+-]\\d{4}$")) {
            return iso.substring(0, iso.length() - 2) + ":" + iso.substring(iso.length() - 2);
        }
        return iso;
    }
}
