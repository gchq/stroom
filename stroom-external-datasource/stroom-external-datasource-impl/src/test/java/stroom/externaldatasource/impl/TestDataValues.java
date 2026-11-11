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
import stroom.query.language.functions.ValBoolean;
import stroom.query.language.functions.ValDate;
import stroom.query.language.functions.ValDouble;
import stroom.query.language.functions.ValLong;
import stroom.query.language.functions.ValNull;
import stroom.query.language.functions.ValString;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestDataValues {

    @Test
    void valuesAreReadAsTheirColumnType() {
        assertThat(DataValues.read(" 42 ", DataColumnType.LONG)).isEqualTo(ValLong.create(42));
        assertThat(DataValues.read("-1.5e3", DataColumnType.DOUBLE)).isEqualTo(ValDouble.create(-1500));
        assertThat(DataValues.read("TRUE", DataColumnType.BOOLEAN)).isEqualTo(ValBoolean.create(true));
        assertThat(DataValues.read(" Red ", DataColumnType.TEXT)).isEqualTo(ValString.create(" Red "));
        assertThat(DataValues.read("Red", null)).isEqualTo(ValString.create("Red"));
        assertThat(DataValues.read("2026-11-11T09:30:00Z", DataColumnType.DATE))
                .isEqualTo(ValDate.create(Instant.parse("2026-11-11T09:30:00Z")));
    }

    @Test
    void emptyValuesAreNull() {
        assertThat(DataValues.read(null, DataColumnType.LONG)).isEqualTo(ValNull.INSTANCE);
        assertThat(DataValues.read("", DataColumnType.TEXT)).isEqualTo(ValNull.INSTANCE);
        assertThat(DataValues.read("  ", DataColumnType.DATE)).isEqualTo(ValNull.INSTANCE);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "2026-11-11|2026-11-11T00:00:00Z",
            "2026-11-11T09:30|2026-11-11T09:30:00Z",
            "2026-11-11 09:30:15|2026-11-11T09:30:15Z",
            "2026-11-11T09:30:15.250Z|2026-11-11T09:30:15.250Z",
            "2026-11-11T10:30:00+01:00|2026-11-11T09:30:00Z",
            "2026-11-11T10:30:00+0100|2026-11-11T09:30:00Z",
            "2026-11-11T10:30:00+01|2026-11-11T09:30:00Z",
            "2026-11-11T04:30:00-05:00|2026-11-11T09:30:00Z"})
    void isoDatesAreRead(final String text, final String expected) {
        assertThat(DataValues.parseDate(text)).isEqualTo(Instant.parse(expected));
    }

    @ParameterizedTest
    @ValueSource(strings = {"11/11/2026", "2026-13-01", "yesterday", "2026-11-11T25:00"})
    void otherDatesAreRefused(final String text) {
        assertThatThrownBy(() -> DataValues.read(text, DataColumnType.DATE))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"NaN", "Infinity", "0x1p3", "1.5d", "1,000", "1e999"})
    void onlyPlainNumbersAreDoubles(final String text) {
        assertThatThrownBy(() -> DataValues.read(text, DataColumnType.DOUBLE))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"1.0", "1e3", "99999999999999999999", "twelve"})
    void onlyWholeNumbersThatFitAreLongs(final String text) {
        assertThatThrownBy(() -> DataValues.read(text, DataColumnType.LONG))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void booleansAreOnlyTrueOrFalse() {
        assertThatThrownBy(() -> DataValues.read("yes", DataColumnType.BOOLEAN))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void guessedTypeIsTheNarrowestEveryValueFits() {
        assertThat(DataValues.guessType(List.of("1", " 2", ""))).isEqualTo(DataColumnType.LONG);
        assertThat(DataValues.guessType(List.of("1", "2.5"))).isEqualTo(DataColumnType.DOUBLE);
        assertThat(DataValues.guessType(List.of("2026-11-11", "2026-11-12T09:00:00Z")))
                .isEqualTo(DataColumnType.DATE);
        assertThat(DataValues.guessType(List.of("true", "False"))).isEqualTo(DataColumnType.BOOLEAN);
        assertThat(DataValues.guessType(List.of("1", "two"))).isEqualTo(DataColumnType.TEXT);
    }

    @Test
    void columnWithNoValuesIsText() {
        assertThat(DataValues.guessType(Arrays.asList("", null, " "))).isEqualTo(DataColumnType.TEXT);
        assertThat(DataValues.guessType(List.of())).isEqualTo(DataColumnType.TEXT);
    }

    @Test
    void codesWithLeadingZerosAreGuessedAsText() {
        // As a number, 0123 would lose its zero.
        assertThat(DataValues.guessType(List.of("0123", "0456"))).isEqualTo(DataColumnType.TEXT);
        assertThat(DataValues.guessType(List.of("0", "10", "0.5"))).isEqualTo(DataColumnType.DOUBLE);
    }

    @Test
    void wholeNumbersTooBigForALongAreGuessedAsText() {
        // As a double, a 20-digit identifier would be rounded.
        assertThat(DataValues.guessType(List.of("12345678901234567890", "1"))).isEqualTo(DataColumnType.TEXT);
    }
}
