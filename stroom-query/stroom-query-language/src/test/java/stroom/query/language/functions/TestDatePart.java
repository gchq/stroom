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

import java.text.ParseException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestDatePart {

    // An hour ahead of UTC in July, so this is 00:30 on Monday 1 July in London.
    private static final String TIME = "2024-06-30T23:30:45.678Z";

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "getYear|year|2024|2024",
            "getQuarter|quarter|3|2",
            "getMonth|month|7|6",
            "getDayOfMonth|dayOfMonth|1|30",
            "getDayOfWeek|dayOfWeek|1|7",
            "getDayOfYear|dayOfYear|183|182",
            "getWeekOfYear|weekOfYear|27|26",
            "getWeekBasedYear|weekBasedYear|2024|2024",
            "getHour|hour|0|23",
            "getMinute|minute|30|30",
            "getSecond|second|45|45",
            "getMillisecond|millisecond|678|678"})
    void parts(final String function, final String part, final int london, final int utc) throws ParseException {
        assertThat(eval(function + "(" + TIME + ")")).isEqualTo(ValInteger.create(london));
        assertThat(eval(function + "(" + TIME + ", 'UTC')")).isEqualTo(ValInteger.create(utc));
        assertThat(eval(function + "(" + TIME + ", ${field})", ValString.create("UTC")))
                .isEqualTo(ValInteger.create(utc));
        assertThat(eval("datePart(" + TIME + ", '" + part + "')")).isEqualTo(ValInteger.create(london));
        assertThat(eval("datePart(" + TIME + ", '" + part + "', 'UTC')")).isEqualTo(ValInteger.create(utc));
        assertThat(eval("datePart(" + TIME + ", ${field}, 'UTC')", ValString.create(part)))
                .isEqualTo(ValInteger.create(utc));
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            // The week belongs to the year that most of it is in.
            "2024-12-30T12:00:00Z|1|2025",
            "2021-01-03T12:00:00Z|53|2020"})
    void weeks(final String time, final int week, final int year) throws ParseException {
        assertThat(eval("getWeekOfYear(" + time + ")")).isEqualTo(ValInteger.create(week));
        assertThat(eval("getWeekBasedYear(" + time + ")")).isEqualTo(ValInteger.create(year));
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "2024-06-30T23:30:00Z|7",
            "2024-07-01T00:30:00|7",
            "2024-07-01|7",
            "1719790200000|7",
            "2024-06-30T23:30:00+02:00|6"})
    void textAndNumbers(final String text, final int expected) throws ParseException {
        assertThat(eval("getMonth(${field})", ValString.create(text))).isEqualTo(ValInteger.create(expected));
    }

    @Test
    void numbers() throws ParseException {
        assertThat(eval("getMonth(${field})", ValLong.create(1719790200000L))).isEqualTo(ValInteger.create(7));
        assertThat(eval("getMonth(1719790200000)")).isEqualTo(ValInteger.create(7));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "getYear(${field})", "datePart(${field}, 'year')", "datePart(2024-01-01T00:00:00Z, ${field})"})
    void nullsAndErrorsAreKept(final String expression) throws ParseException {
        assertThat(eval(expression, ValNull.INSTANCE)).isEqualTo(ValNull.INSTANCE);
        assertThat(eval(expression, ValString.create(" "))).isEqualTo(ValNull.INSTANCE);
        assertThat(eval(expression, ValErr.create("bad"))).isEqualTo(ValErr.create("bad"));
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "getYear(${field})|'soon' is not a date",
            "getYear(power(10, 400))|A double is not a date",
            "datePart(2024-01-01T00:00:00Z, ${field})|Unknown part of a date 'soon'. Use one of year, quarter",
            "getYear(2024-01-01T00:00:00Z, ${field})|Unknown time zone 'soon'"})
    void errors(final String expression, final String message) throws ParseException {
        final Val val = eval(expression, ValString.create("soon"));
        assertThat(val.type()).as(val.toString()).isEqualTo(Type.ERR);
        assertThat(val.toString()).contains(message);
    }

    @ParameterizedTest
    @CsvSource({"' '", "''"})
    void blankZoneIsAnError(final String zone) throws ParseException {
        assertThat(eval("getYear(2024-01-01T00:00:00Z, ${field})", ValString.create(zone)).toString())
                .contains("There is no time zone");
        assertThat(eval("getYear(2024-01-01T00:00:00Z, ${field})", ValNull.INSTANCE).toString())
                .contains("There is no time zone");
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "datePart(${field}, 'Year')|Unknown part of a date 'Year'",
            "getYear(${field}, 'Mars/Base')|Unknown time zone 'Mars/Base'",
            "getYear(${field}, '')|There is no time zone",
            "datePart(${field}, 'year', 'Mars/Base')|Unknown time zone 'Mars/Base'",
            "getYear(1h)|A duration is not a date",
            "getYear(1M)|A period is not a date"})
    void mistakesThatAreReportedWhenTheExpressionIsParsed(final String expression, final String message) {
        assertThatThrownBy(() -> eval(expression)).hasMessageContaining(message);
    }

    // In a query whose time zone is Europe/London.
    private static Val eval(final String expression, final Val field) throws ParseException {
        return DateFunctionTestUtil.eval(DateFunctionTestUtil.LONDON, expression, field);
    }

    private static Val eval(final String expression) throws ParseException {
        return eval(expression, ValNull.INSTANCE);
    }
}
