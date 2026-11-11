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

import stroom.query.api.datasource.FieldType;
import stroom.query.language.functions.Val;
import stroom.query.language.functions.ValBoolean;
import stroom.query.language.functions.ValDate;
import stroom.query.language.functions.ValDouble;
import stroom.query.language.functions.ValFloat;
import stroom.query.language.functions.ValInteger;
import stroom.query.language.functions.ValNull;
import stroom.query.language.functions.ValString;

import java.math.BigDecimal;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.util.Locale;

/// Maps Amazon Athena column types to Stroom field types, and reads Athena's text values as Stroom
/// values.
///
/// Athena returns every value as text, with each column's type name, such as `varchar`, `bigint`
/// or `timestamp`. The type name is turned into a `java.sql.Types` code so the columns map to
/// Stroom field types in the same way as JDBC columns. A timestamp without a time zone is taken to
/// be in UTC. A value that cannot be read as its type is kept as text rather than failing the
/// query.
public final class AthenaTypeMapper {

    // Such as '2026-11-10 09:30:00.123'.
    private static final DateTimeFormatter TIMESTAMP_FORMAT = new DateTimeFormatterBuilder()
            .append(DateTimeFormatter.ISO_LOCAL_DATE)
            .appendLiteral(' ')
            .append(DateTimeFormatter.ISO_LOCAL_TIME)
            .toFormatter(Locale.ROOT);

    // Such as '2026-11-10 09:30:00.123 UTC' or '2026-11-10 09:30:00.123 Europe/London'.
    private static final DateTimeFormatter TIMESTAMP_WITH_ZONE_FORMAT = new DateTimeFormatterBuilder()
            .append(TIMESTAMP_FORMAT)
            .appendLiteral(' ')
            .appendZoneOrOffsetId()
            .toFormatter(Locale.ROOT);

    private AthenaTypeMapper() {
        // Static methods only.
    }

    /// Gives the `java.sql.Types` code for an Athena type name.
    ///
    /// @param athenaType The type name, such as `varchar(20)`, `decimal(10,2)` or `timestamp(3)`.
    /// @return The JDBC type code.
    public static int toSqlType(final String athenaType) {
        if (athenaType == null) {
            return Types.OTHER;
        }
        final String type = athenaType.trim().toLowerCase(Locale.ROOT);
        if (type.startsWith("timestamp") && type.contains("with time zone")) {
            return Types.TIMESTAMP_WITH_TIMEZONE;
        }
        final String baseType = baseType(type);
        return switch (baseType) {
            case "boolean" -> Types.BOOLEAN;
            case "tinyint" -> Types.TINYINT;
            case "smallint" -> Types.SMALLINT;
            case "integer", "int" -> Types.INTEGER;
            case "bigint" -> Types.BIGINT;
            case "real", "float" -> Types.REAL;
            case "double" -> Types.DOUBLE;
            case "decimal" -> Types.DECIMAL;
            case "varchar", "char", "string" -> Types.VARCHAR;
            case "date" -> Types.DATE;
            case "timestamp" -> Types.TIMESTAMP;
            default -> Types.OTHER;
        };
    }

    /// Reads one value.
    ///
    /// @param text   The value as Athena gives it, or `null` for SQL `NULL`.
    /// @param column The column's description.
    /// @return The value.
    public static Val readValue(final String text, final SqlColumn column) {
        if (text == null) {
            return ValNull.INSTANCE;
        }
        try {
            return switch (column.fieldType()) {
                case INTEGER -> ValInteger.create(Integer.parseInt(text.trim()));
                case LONG -> SqlTypeMapper.readWholeNumber(new BigDecimal(text.trim()));
                case FLOAT -> ValFloat.create(Float.parseFloat(text.trim()));
                case DOUBLE -> ValDouble.create(Double.parseDouble(text.trim()));
                case BOOLEAN -> ValBoolean.create(Boolean.parseBoolean(text.trim()));
                case DATE -> readDate(text.trim(), column.sqlType());
                default -> ValString.create(text);
            };
        } catch (final NumberFormatException | DateTimeParseException e) {
            return ValString.create(text);
        }
    }

    private static Val readDate(final String text, final int sqlType) {
        return switch (sqlType) {
            case Types.DATE -> ValDate.create(LocalDate.parse(text).atStartOfDay(ZoneOffset.UTC).toInstant());
            case Types.TIMESTAMP_WITH_TIMEZONE -> ValDate.create(
                    ZonedDateTime.parse(text, TIMESTAMP_WITH_ZONE_FORMAT).toInstant());
            default -> ValDate.create(LocalDateTime.parse(text, TIMESTAMP_FORMAT).toInstant(ZoneOffset.UTC));
        };
    }

    // The type name without its parameters: 'varchar(20)' becomes 'varchar'.
    private static String baseType(final String type) {
        final int bracket = type.indexOf('(');
        return bracket < 0
                ? type
                : type.substring(0, bracket).trim();
    }
}
