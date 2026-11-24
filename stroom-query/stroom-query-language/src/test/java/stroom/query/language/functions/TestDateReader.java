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

package stroom.query.language.functions;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestDateReader {

    private static final ZoneId LONDON = ZoneId.of("Europe/London");
    private static final Supplier<ZoneId> NOT_NEEDED = () -> {
        throw new AssertionError("The time zone is not needed");
    };

    @ParameterizedTest
    @CsvSource({
            "2024-07-01T09:00:00Z,2024-07-01T09:00:00Z",
            "2024-07-01T09:00:00.123+02:00,2024-07-01T07:00:00.123Z",
            "2024-07-01T09:00:00+0200,2024-07-01T07:00:00Z",
            "1719824400000,2024-07-01T09:00:00Z",
            "1719824400000.9,2024-07-01T09:00:00Z"})
    void textThatDoesNotNeedTheTimeZone(final String text, final Instant expected) {
        assertThat(DateReader.readMillis(ValString.create(text), NOT_NEEDED)).isEqualTo(expected.toEpochMilli());
    }

    @ParameterizedTest
    @CsvSource({
            "2024-07-01T09:00:00,2024-07-01T08:00:00Z",
            "' 2024-07-01T09:00 ',2024-07-01T08:00:00Z",
            "2024-07-01,2024-06-30T23:00:00Z"})
    void textThatNeedsTheTimeZone(final String text, final Instant expected) {
        assertThat(DateReader.readMillis(ValString.create(text), () -> LONDON)).isEqualTo(expected.toEpochMilli());
        assertThat(DateReader.read(ValString.create(text), LONDON)).isEqualTo(expected.atZone(LONDON));
    }

    @Test
    void numbersAndDates() {
        assertThat(DateReader.readMillis(ValDate.create(5), NOT_NEEDED)).isEqualTo(5);
        assertThat(DateReader.readMillis(ValLong.create(5), NOT_NEEDED)).isEqualTo(5);
        assertThat(DateReader.readMillis(ValDouble.create(5.9), NOT_NEEDED)).isEqualTo(5);
    }

    @Test
    void valuesThatAreNotDates() {
        for (final Val val : List.of(
                ValDouble.create(Double.NaN),
                ValDouble.create(Double.POSITIVE_INFINITY),
                ValDouble.create(Double.NEGATIVE_INFINITY),
                ValFloat.create(Float.NaN),
                ValDouble.create(1e300),
                ValDouble.create(-1e300),
                ValDouble.create(0x1p63),
                ValDuration.create(5),
                ValPeriod.create(0, 0, 1),
                ValBoolean.TRUE)) {
            assertThatThrownBy(() -> DateReader.readMillis(val, NOT_NEEDED))
                    .isInstanceOf(DateTimeException.class)
                    .hasMessage("A " + val.type().getName() + " is not a date");
        }
    }

    @Test
    void readIfDate() {
        assertThat(DateReader.readIfDate(ValString.create("2024-07-01T09:00:00"), LONDON))
                .isEqualTo(Instant.parse("2024-07-01T08:00:00Z").atZone(LONDON));
        for (final Val val : List.of(ValNull.INSTANCE, ValErr.create("bad"), ValString.create(" "),
                ValString.create("soon"), ValString.create("-12345678901234567890"))) {
            assertThat(DateReader.readIfDate(val, LONDON)).as(val.toString()).isNull();
        }
        for (final Val val : List.of(ValString.create("2h"), ValDuration.create(5), ValBoolean.TRUE,
                ValDouble.create(Double.NaN))) {
            assertThatThrownBy(() -> DateReader.readIfDate(val, LONDON)).isInstanceOf(DateTimeException.class);
        }
    }

    @Test
    void numberInTextThatIsTooLarge() {
        assertThatThrownBy(() -> DateReader.readMillis(ValString.create("12345678901234567890"), NOT_NEEDED))
                .isInstanceOf(DateTimeException.class)
                .hasMessage("'12345678901234567890' is too large to be a date");
    }

    @ParameterizedTest
    @ValueSource(strings = {"soon", "", "2024-13-01", "2024-07-01T25:00:00Z", "1h"})
    void textThatIsNotADate(final String text) {
        assertThatThrownBy(() -> DateReader.readMillis(ValString.create(text), NOT_NEEDED))
                .isInstanceOf(DateTimeException.class)
                .hasMessage("'" + text + "' is not a date");
    }
}
