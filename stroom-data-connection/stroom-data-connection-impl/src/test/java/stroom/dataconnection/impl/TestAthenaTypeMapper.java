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

import java.sql.Types;
import java.time.Instant;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class TestAthenaTypeMapper {

    static Stream<Arguments> athenaTypes() {
        return Stream.of(
                Arguments.of("boolean", Types.BOOLEAN),
                Arguments.of("tinyint", Types.TINYINT),
                Arguments.of("smallint", Types.SMALLINT),
                Arguments.of("integer", Types.INTEGER),
                Arguments.of("int", Types.INTEGER),
                Arguments.of("bigint", Types.BIGINT),
                Arguments.of("real", Types.REAL),
                Arguments.of("float", Types.REAL),
                Arguments.of("double", Types.DOUBLE),
                Arguments.of("decimal(10,2)", Types.DECIMAL),
                Arguments.of("varchar", Types.VARCHAR),
                Arguments.of("varchar(20)", Types.VARCHAR),
                Arguments.of("char(3)", Types.VARCHAR),
                Arguments.of("string", Types.VARCHAR),
                Arguments.of("date", Types.DATE),
                Arguments.of("timestamp", Types.TIMESTAMP),
                Arguments.of("timestamp(3)", Types.TIMESTAMP),
                Arguments.of("timestamp(3) with time zone", Types.TIMESTAMP_WITH_TIMEZONE),
                Arguments.of("TIMESTAMP", Types.TIMESTAMP),
                Arguments.of("array(varchar)", Types.OTHER),
                Arguments.of("map(varchar,bigint)", Types.OTHER),
                Arguments.of("json", Types.OTHER),
                Arguments.of(null, Types.OTHER));
    }

    @ParameterizedTest
    @MethodSource("athenaTypes")
    void mapsAthenaTypeNames(final String athenaType, final int expected) {
        assertThat(AthenaTypeMapper.toSqlType(athenaType)).isEqualTo(expected);
    }

    private static Val read(final String text, final String athenaType) {
        final int sqlType = AthenaTypeMapper.toSqlType(athenaType);
        return AthenaTypeMapper.readValue(text, new SqlColumn("c", SqlTypeMapper.toFieldType(sqlType), sqlType));
    }

    @Test
    void readsNumbersAndBooleans() {
        assertThat(read("42", "integer")).isEqualTo(ValInteger.create(42));
        assertThat(read("8000000000", "bigint")).isEqualTo(ValLong.create(8_000_000_000L));
        assertThat(read("1.5", "double")).isEqualTo(ValDouble.create(1.5));
        assertThat(read("true", "boolean")).isEqualTo(ValBoolean.create(true));
    }

    @Test
    void wholeDecimalIsReadExactly() {
        final SqlColumn column = new SqlColumn("c", FieldType.LONG, Types.DECIMAL);

        assertThat(AthenaTypeMapper.readValue("123456789012345678", column))
                .isEqualTo(ValLong.create(123_456_789_012_345_678L));
    }

    @Test
    void nullIsNull() {
        assertThat(read(null, "bigint")).isSameAs(ValNull.INSTANCE);
    }

    @Test
    void timestampIsTakenAsUtc() {
        assertThat(read("2026-11-10 09:30:00.123", "timestamp"))
                .isEqualTo(ValDate.create(Instant.parse("2026-11-10T09:30:00.123Z")));
        assertThat(read("2026-11-10 09:30:00", "timestamp"))
                .isEqualTo(ValDate.create(Instant.parse("2026-11-10T09:30:00Z")));
    }

    @Test
    void timestampWithZoneKeepsItsInstant() {
        assertThat(read("2026-11-10 10:30:00.000 Europe/Paris", "timestamp(3) with time zone"))
                .isEqualTo(ValDate.create(Instant.parse("2026-11-10T09:30:00Z")));
        assertThat(read("2026-11-10 09:30:00.000 UTC", "timestamp(3) with time zone"))
                .isEqualTo(ValDate.create(Instant.parse("2026-11-10T09:30:00Z")));
    }

    @Test
    void dateIsMidnightUtc() {
        assertThat(read("2026-11-10", "date")).isEqualTo(ValDate.create(Instant.parse("2026-11-10T00:00:00Z")));
    }

    @Test
    void otherTypesAreText() {
        assertThat(read("[a, b]", "array(varchar)")).isEqualTo(ValString.create("[a, b]"));
    }

    @Test
    void valueThatCannotBeReadIsKeptAsText() {
        // A failure in one value should not fail the whole query.
        assertThat(read("not a number", "bigint")).isEqualTo(ValString.create("not a number"));
        assertThat(read("someday", "date")).isEqualTo(ValString.create("someday"));
    }
}
