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
import stroom.query.language.functions.ValInteger;
import stroom.query.language.functions.ValLong;
import stroom.query.language.functions.ValNull;
import stroom.query.language.functions.ValString;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class TestSqlTypeMapper {

    private final ResultSet resultSet = Mockito.mock(ResultSet.class);

    static Stream<Arguments> sqlTypes() {
        return Stream.of(
                Arguments.of(Types.VARCHAR, FieldType.TEXT),
                Arguments.of(Types.CLOB, FieldType.TEXT),
                Arguments.of(Types.NVARCHAR, FieldType.TEXT),
                Arguments.of(Types.TINYINT, FieldType.INTEGER),
                Arguments.of(Types.SMALLINT, FieldType.INTEGER),
                Arguments.of(Types.INTEGER, FieldType.INTEGER),
                Arguments.of(Types.BIGINT, FieldType.LONG),
                Arguments.of(Types.REAL, FieldType.FLOAT),
                Arguments.of(Types.FLOAT, FieldType.FLOAT),
                Arguments.of(Types.DOUBLE, FieldType.DOUBLE),
                Arguments.of(Types.DECIMAL, FieldType.DOUBLE),
                Arguments.of(Types.NUMERIC, FieldType.DOUBLE),
                Arguments.of(Types.BOOLEAN, FieldType.BOOLEAN),
                Arguments.of(Types.BIT, FieldType.BOOLEAN),
                Arguments.of(Types.DATE, FieldType.DATE),
                Arguments.of(Types.TIMESTAMP, FieldType.DATE),
                Arguments.of(Types.TIMESTAMP_WITH_TIMEZONE, FieldType.DATE),
                Arguments.of(Types.BLOB, FieldType.TEXT),
                Arguments.of(Types.TIME, FieldType.TEXT),
                Arguments.of(Types.VARBINARY, FieldType.TEXT),
                Arguments.of(Types.ARRAY, FieldType.TEXT),
                Arguments.of(Types.OTHER, FieldType.TEXT));
    }

    @ParameterizedTest
    @MethodSource("sqlTypes")
    void mapsSqlTypes(final int sqlType, final FieldType expected) {
        assertThat(SqlTypeMapper.toFieldType(sqlType)).isEqualTo(expected);
    }

    @Test
    void columnsUseTheirLabels() throws SQLException {
        final ResultSetMetaData metaData = Mockito.mock(ResultSetMetaData.class);
        Mockito.when(metaData.getColumnCount()).thenReturn(2);
        Mockito.when(metaData.getColumnLabel(1)).thenReturn("staff_name");
        Mockito.when(metaData.getColumnType(1)).thenReturn(Types.VARCHAR);
        Mockito.when(metaData.getColumnLabel(2)).thenReturn("joined");
        Mockito.when(metaData.getColumnType(2)).thenReturn(Types.TIMESTAMP);

        final List<SqlColumn> columns = SqlTypeMapper.toColumns(metaData);

        assertThat(columns).containsExactly(
                new SqlColumn("staff_name", FieldType.TEXT, Types.VARCHAR),
                new SqlColumn("joined", FieldType.DATE, Types.TIMESTAMP));
    }

    @Test
    void readsNumbersAndBooleans() throws SQLException {
        Mockito.when(resultSet.getInt(1)).thenReturn(7);
        Mockito.when(resultSet.getObject(2)).thenReturn(8_000_000_000L);
        Mockito.when(resultSet.getDouble(3)).thenReturn(1.5);
        Mockito.when(resultSet.getBoolean(4)).thenReturn(true);

        assertThat(read(1, Types.INTEGER)).isEqualTo(ValInteger.create(7));
        assertThat(read(2, Types.BIGINT)).isEqualTo(ValLong.create(8_000_000_000L));
        assertThat(read(3, Types.DECIMAL)).isEqualTo(ValDouble.create(1.5));
        assertThat(read(4, Types.BOOLEAN)).isEqualTo(ValBoolean.create(true));
    }

    static Stream<Arguments> decimals() {
        return Stream.of(
                Arguments.of(Types.DECIMAL, 18, 0, FieldType.LONG),
                Arguments.of(Types.NUMERIC, 10, 0, FieldType.LONG),
                Arguments.of(Types.DECIMAL, 19, 0, FieldType.DOUBLE),
                Arguments.of(Types.DECIMAL, 10, 2, FieldType.DOUBLE),
                // PostgreSQL's NUMERIC with no precision reports 0, and may hold fractions.
                Arguments.of(Types.NUMERIC, 0, 0, FieldType.DOUBLE),
                Arguments.of(Types.VARCHAR, 10, 0, FieldType.TEXT));
    }

    @ParameterizedTest
    @MethodSource("decimals")
    void wholeDecimalsThatFitALongAreLongs(final int sqlType,
                                           final int precision,
                                           final int scale,
                                           final FieldType expected) {
        assertThat(SqlTypeMapper.toFieldType(sqlType, precision, scale)).isEqualTo(expected);
    }

    @Test
    void wholeDecimalIsReadExactly() throws SQLException {
        // As a double this would lose its last digit.
        Mockito.when(resultSet.getObject(1)).thenReturn(new BigDecimal("123456789012345678"));

        final Val val = SqlTypeMapper.readValue(resultSet, 1, new SqlColumn("c", FieldType.LONG, Types.DECIMAL));

        assertThat(val).isEqualTo(ValLong.create(123_456_789_012_345_678L));
    }

    @Test
    void unsignedBigintTooBigForALongIsReadAsADouble() throws SQLException {
        // MySQL gives BIGINT UNSIGNED values as BigInteger; getLong would throw for this one.
        final BigInteger tooBig = BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.TEN);
        Mockito.when(resultSet.getObject(1)).thenReturn(tooBig);

        assertThat(read(1, Types.BIGINT)).isEqualTo(ValDouble.create(tooBig.doubleValue()));
    }

    @Test
    void unsignedBigintThatFitsIsReadAsALong() throws SQLException {
        Mockito.when(resultSet.getObject(1)).thenReturn(BigInteger.valueOf(42));

        assertThat(read(1, Types.BIGINT)).isEqualTo(ValLong.create(42));
    }

    @Test
    void sqlNullIsReadAsNullNotZero() throws SQLException {
        // The primitive getters give 0 for NULL; only wasNull() tells them apart.
        Mockito.when(resultSet.getInt(1)).thenReturn(0);
        Mockito.when(resultSet.wasNull()).thenReturn(true);

        assertThat(read(1, Types.INTEGER)).isSameAs(ValNull.INSTANCE);
    }

    @Test
    void zeroIsNotNull() throws SQLException {
        Mockito.when(resultSet.getInt(1)).thenReturn(0);
        Mockito.when(resultSet.wasNull()).thenReturn(false);

        assertThat(read(1, Types.INTEGER)).isEqualTo(ValInteger.create(0));
    }

    @Test
    void timestampWithoutZoneIsTakenAsUtc() throws SQLException {
        Mockito.when(resultSet.getObject(1, LocalDateTime.class))
                .thenReturn(LocalDateTime.of(2026, 11, 10, 9, 30));

        assertThat(read(1, Types.TIMESTAMP))
                .isEqualTo(ValDate.create(Instant.parse("2026-11-10T09:30:00Z")));
    }

    @Test
    void dateIsMidnightUtc() throws SQLException {
        Mockito.when(resultSet.getObject(1, LocalDate.class)).thenReturn(LocalDate.of(2026, 11, 10));

        assertThat(read(1, Types.DATE)).isEqualTo(ValDate.create(Instant.parse("2026-11-10T00:00:00Z")));
    }

    @Test
    void timestampWithZoneKeepsItsInstant() throws SQLException {
        Mockito.when(resultSet.getObject(1, OffsetDateTime.class))
                .thenReturn(OffsetDateTime.of(2026, 11, 10, 10, 30, 0, 0, ZoneOffset.ofHours(1)));

        assertThat(read(1, Types.TIMESTAMP_WITH_TIMEZONE))
                .isEqualTo(ValDate.create(Instant.parse("2026-11-10T09:30:00Z")));
    }

    @Test
    void nullDateIsNull() throws SQLException {
        Mockito.when(resultSet.getObject(1, LocalDateTime.class)).thenReturn(null);
        Mockito.when(resultSet.wasNull()).thenReturn(true);

        assertThat(read(1, Types.TIMESTAMP)).isSameAs(ValNull.INSTANCE);
    }

    @Test
    void otherTypesAreReadAsText() throws SQLException {
        Mockito.when(resultSet.getString(1)).thenReturn("{\"a\": 1}");

        assertThat(read(1, Types.OTHER)).isEqualTo(ValString.create("{\"a\": 1}"));
    }

    private Val read(final int columnIndex, final int sqlType) throws SQLException {
        return SqlTypeMapper.readValue(
                resultSet,
                columnIndex,
                new SqlColumn("c", SqlTypeMapper.toFieldType(sqlType), sqlType));
    }
}
