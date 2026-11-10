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

package stroom.sqldatasource.impl;

import stroom.query.api.datasource.FieldType;
import stroom.query.language.functions.Val;
import stroom.query.language.functions.ValBoolean;
import stroom.query.language.functions.ValDate;
import stroom.query.language.functions.ValDouble;
import stroom.query.language.functions.ValFloat;
import stroom.query.language.functions.ValInteger;
import stroom.query.language.functions.ValLong;
import stroom.query.language.functions.ValNull;
import stroom.query.language.functions.ValString;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

/// Maps JDBC column types to Stroom field types, and reads column values as Stroom values.
///
/// Dates and timestamps become Stroom dates. A date or timestamp without a time zone is taken to
/// be in UTC. A type Stroom has no match for is read as text.
///
/// Whole numbers are read as longs. One too big for a long, such as a large MySQL
/// `BIGINT UNSIGNED`, is read as a double rather than failing the query. A `DECIMAL` or `NUMERIC`
/// with no fraction and at most 18 digits is read as a long, so it stays exact; any other is read
/// as a double.
public final class SqlTypeMapper {

    // The most decimal digits that always fit in a long.
    private static final int MAX_LONG_DIGITS = 18;

    private SqlTypeMapper() {
    }

    /// Gives the Stroom field type for a JDBC column type.
    ///
    /// @param sqlType A `java.sql.Types` code.
    /// @return The Stroom field type its values are read as.
    public static FieldType toFieldType(final int sqlType) {
        return switch (sqlType) {
            case Types.TINYINT, Types.SMALLINT, Types.INTEGER -> FieldType.INTEGER;
            case Types.BIGINT -> FieldType.LONG;
            case Types.REAL, Types.FLOAT -> FieldType.FLOAT;
            case Types.DOUBLE, Types.DECIMAL, Types.NUMERIC -> FieldType.DOUBLE;
            case Types.BOOLEAN, Types.BIT -> FieldType.BOOLEAN;
            case Types.DATE, Types.TIMESTAMP, Types.TIMESTAMP_WITH_TIMEZONE -> FieldType.DATE;
            default -> FieldType.TEXT;
        };
    }

    /// Gives the Stroom field type for a JDBC column, taking account of the size of decimals.
    ///
    /// @param sqlType   A `java.sql.Types` code.
    /// @param precision The column's precision, or 0 if unknown.
    /// @param scale     The column's scale.
    /// @return The Stroom field type its values are read as.
    public static FieldType toFieldType(final int sqlType, final int precision, final int scale) {
        if ((sqlType == Types.DECIMAL || sqlType == Types.NUMERIC)
            && scale == 0
            && precision > 0
            && precision <= MAX_LONG_DIGITS) {
            return FieldType.LONG;
        }
        return toFieldType(sqlType);
    }

    /// Reads the columns of a result set.
    ///
    /// @param metaData The result set's metadata.
    /// @return The columns, in order.
    /// @throws SQLException If the driver cannot describe a column.
    public static List<SqlColumn> toColumns(final ResultSetMetaData metaData) throws SQLException {
        final int count = metaData.getColumnCount();
        final List<SqlColumn> columns = new ArrayList<>(count);
        for (int i = 1; i <= count; i++) {
            final int sqlType = metaData.getColumnType(i);
            final FieldType fieldType = toFieldType(sqlType, metaData.getPrecision(i), metaData.getScale(i));
            columns.add(new SqlColumn(metaData.getColumnLabel(i), fieldType, sqlType));
        }
        return columns;
    }

    /// Reads one value from the current row of a result set.
    ///
    /// @param resultSet   The result set, on a row.
    /// @param columnIndex The column, counted from 1.
    /// @param column      The column's description.
    /// @return The value, or [ValNull#INSTANCE] for SQL `NULL`.
    /// @throws SQLException If the driver cannot read the value.
    public static Val readValue(final ResultSet resultSet,
                                final int columnIndex,
                                final SqlColumn column) throws SQLException {
        final Val val = switch (column.fieldType()) {
            case INTEGER -> ValInteger.create(resultSet.getInt(columnIndex));
            case LONG -> readWholeNumber(resultSet.getObject(columnIndex));
            case FLOAT -> ValFloat.create(resultSet.getFloat(columnIndex));
            case DOUBLE -> ValDouble.create(resultSet.getDouble(columnIndex));
            case BOOLEAN -> ValBoolean.create(resultSet.getBoolean(columnIndex));
            case DATE -> readDate(resultSet, columnIndex, column.sqlType());
            default -> readText(resultSet, columnIndex);
        };
        // The primitive getters return 0 or false for NULL, so ask afterwards.
        return val == null || resultSet.wasNull()
                ? ValNull.INSTANCE
                : val;
    }

    /// Reads a whole number of any size, falling back to a double when it is too big for a long.
    ///
    /// @param value A number, or `null`.
    /// @return The value, or `null` for `null`.
    static Val readWholeNumber(final Object value) {
        return switch (value) {
            case null -> null;
            case final BigInteger bigInteger -> bigInteger.bitLength() < Long.SIZE
                    ? ValLong.create(bigInteger.longValue())
                    : ValDouble.create(bigInteger.doubleValue());
            case final BigDecimal bigDecimal -> readWholeNumber(bigDecimal.toBigInteger());
            case final Number number -> ValLong.create(number.longValue());
            default -> ValLong.create(Long.parseLong(value.toString()));
        };
    }

    private static Val readDate(final ResultSet resultSet,
                                final int columnIndex,
                                final int sqlType) throws SQLException {
        return switch (sqlType) {
            case Types.DATE -> {
                final LocalDate date = resultSet.getObject(columnIndex, LocalDate.class);
                yield date == null
                        ? null
                        : ValDate.create(date.atStartOfDay(ZoneOffset.UTC).toInstant());
            }
            case Types.TIMESTAMP_WITH_TIMEZONE -> {
                final OffsetDateTime dateTime = resultSet.getObject(columnIndex, OffsetDateTime.class);
                yield dateTime == null
                        ? null
                        : ValDate.create(dateTime.toInstant());
            }
            default -> {
                final LocalDateTime dateTime = resultSet.getObject(columnIndex, LocalDateTime.class);
                yield dateTime == null
                        ? null
                        : ValDate.create(dateTime.toInstant(ZoneOffset.UTC));
            }
        };
    }

    private static Val readText(final ResultSet resultSet, final int columnIndex) throws SQLException {
        final String value = resultSet.getString(columnIndex);
        return value == null
                ? null
                : ValString.create(value);
    }
}
