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

import java.text.ParseException;

import static org.assertj.core.api.Assertions.assertThat;

class TestTimeOfDay {

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "timeOfDay(2024-07-01T08:30:00Z)|9h30m",
            "timeOfDay(2024-07-01T08:30:00Z, 'UTC')|8h30m",
            "timeOfDay(2024-07-01T00:00:00)|0ms",
            "timeOfDay(2024-07-01T23:59:59.999)|23h59m59s999ms",
            // By the clock, even though fewer hours have passed since midnight on this day.
            "timeOfDay(2024-03-31T09:00:00)|9h"})
    void times(final String expression, final String expected) throws ParseException {
        final Val val = eval(expression);
        assertThat(val.type()).isEqualTo(Type.DURATION);
        assertThat(val).hasToString(expected);
    }

    @Test
    void outsideWorkingHoursWhereverItHappened() throws ParseException {
        final String expression = "timeOfDay(2024-07-01T08:30:00Z, ${field}) < 9h";
        assertThat(eval(expression, ValString.create("Europe/London"))).isEqualTo(ValBoolean.FALSE);
        assertThat(eval(expression, ValString.create("America/New_York"))).isEqualTo(ValBoolean.TRUE);
        assertThat(eval(expression, ValString.create("Mars/Base")).toString()).contains("Unknown time zone");
    }

    // In a query whose time zone is Europe/London.
    private static Val eval(final String expression, final Val field) throws ParseException {
        return DateFunctionTestUtil.eval(DateFunctionTestUtil.LONDON, expression, field);
    }

    private static Val eval(final String expression) throws ParseException {
        return eval(expression, ValNull.INSTANCE);
    }
}
