/*
 * Copyright 2024 Crown Copyright
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
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

public class TestParseDate extends AbstractFunctionTest<ParseDate> {

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            // With no offset the date is in the zone of the query, an hour ahead of UTC in July.
            "2024-07-01T09:00:00.000||2024-07-01T08:00:00Z",
            "2024-07-01T09:00:00||2024-07-01T08:00:00Z",
            "2024-01-01T09:00:00||2024-01-01T09:00:00Z",
            "2024 07 01|, 'yyyy MM dd'|2024-06-30T23:00:00Z",
            // An offset or zone in the text or the call wins.
            "2024-07-01T09:00:00Z||2024-07-01T09:00:00Z",
            "2024-07-01T09:00:00.000+0200||2024-07-01T07:00:00Z",
            "2024 07 01 09 +02|, 'yyyy MM dd HH X'|2024-07-01T07:00:00Z",
            "2024 07 01|, 'yyyy MM dd', 'UTC'|2024-07-01T00:00:00Z",
            // Text that says it is in UTC is.
            "2024-07-01T08:00:00Z|, \"yyyy-MM-dd'T'HH:mm:ss'Z'\"|2024-07-01T08:00:00Z"})
    void parseDateUsesTheZoneOfTheQuery(final String text, final String otherParams, final Instant expected)
            throws ParseException {
        final String params = otherParams == null
                ? ""
                : otherParams;
        final Val date = ValDate.create(expected.toEpochMilli());

        assertThat(evalInLondon("parseDate('" + text + "'" + params + ")", ValNull.INSTANCE)).isEqualTo(date);
        assertThat(evalInLondon("parseDate(${field}" + params + ")", ValString.create(text))).isEqualTo(date);
    }

    @Test
    void parseDateOfAConstantGivesADate() throws ParseException {
        final long millis = Instant.parse("2024-07-01T08:00:00Z").toEpochMilli();

        assertThat(evalInLondon("typeOf(parseDate('2024-07-01T08:00:00Z'))", ValNull.INSTANCE))
                .isEqualTo(ValString.create("date"));
        assertThat(evalInLondon("parseDate(" + millis + ")", ValNull.INSTANCE)).isEqualTo(ValDate.create(millis));
    }

    @Test
    void badTextInAFieldGivesAnError() throws ParseException {
        assertThat(evalInLondon("parseDate(${field})", ValString.create("yesterday")).type()).isEqualTo(Type.ERR);
    }

    @Test
    void noSettingsIsUtc() throws ParseException {
        assertThat(evalWithNoSettings("parseDate(${field})", ValString.create("2024-07-01T08:00:00")))
                .isEqualTo(ValDate.create(Instant.parse("2024-07-01T08:00:00Z").toEpochMilli()));
    }

    @Override
    Class<ParseDate> getFunctionType() {
        return ParseDate.class;
    }

    @Override
    Stream<TestCase> getTestCases() {
        return Stream.of(
                TestCase.of(
                        "null",
                        ValNull.INSTANCE,
                        Val.create(null)),
                TestCase.of(
                        "",
                        ValNull.INSTANCE,
                        Val.create("")),
                TestCase.of(
                        "0 milli",
                        ValDate.create(Instant.parse("2010-01-01T23:59:59Z").toEpochMilli()),
                        Val.create("2010-01-01T23:59:59Z")),
                TestCase.of(
                        "1 milli",
                        ValDate.create(Instant.parse("2010-01-01T23:59:59.1Z").toEpochMilli()),
                        Val.create("2010-01-01T23:59:59.1Z")),
                TestCase.of(
                        "2 milli",
                        ValDate.create(Instant.parse("2010-01-01T23:59:59.12Z").toEpochMilli()),
                        Val.create("2010-01-01T23:59:59.12Z")),
                TestCase.of(
                        "3 milli",
                        ValDate.create(Instant.parse("2010-01-01T23:59:59.123Z").toEpochMilli()),
                        Val.create("2010-01-01T23:59:59.123Z")),
                TestCase.of(
                        "4 milli",
                        ValDate.create(Instant.parse("2010-01-01T23:59:59.123Z").toEpochMilli()),
                        Val.create("2010-01-01T23:59:59.1234Z")),
                TestCase.of(
                        "+0000 offset",
                        ValDate.create(Instant.parse("2010-01-01T23:59:59.123Z").toEpochMilli()),
                        Val.create("2010-01-01T23:59:59.123+0000")),
                TestCase.of(
                        "+0200 offset",
                        ValDate.create(Instant.parse("2010-01-01T21:59:59.123Z").toEpochMilli()),
                        Val.create("2010-01-01T23:59:59.123+0200")),
                TestCase.of(
                        "+00:00 offset",
                        ValDate.create(Instant.parse("2010-01-01T23:59:59.123Z").toEpochMilli()),
                        Val.create("2010-01-01T23:59:59.123+00:00")),
                TestCase.of(
                        "+02:00 offset",
                        ValDate.create(Instant.parse("2010-01-01T21:59:59.123Z").toEpochMilli()),
                        Val.create("2010-01-01T23:59:59.123+02:00")),


                TestCase.of(
                        "3 milli + format",
                        ValDate.create(Instant.parse("2010-01-01T23:59:59.123Z").toEpochMilli()),
                        Val.create("20100101 235959123Z"),
                        ValString.create("yyyyMMdd HHmmssSSSXX")),

                TestCase.of(
                        "3 milli + format + zone",
                        ValDate.create(Instant.parse("2010-01-01T23:59:59.123Z")
                                .minus(2, ChronoUnit.HOURS)
                                .toEpochMilli()),
                        Val.create("20100101 235959123"),
                        ValString.create("yyyyMMdd HHmmssSSS"),
                        ValString.create("GMT+2"))
        );
    }

    private static Val evalInLondon(final String input, final Val field) throws ParseException {
        return DateFunctionTestUtil.eval(DateFunctionTestUtil.LONDON, input, field);
    }

    private static Val evalWithNoSettings(final String input, final Val field) throws ParseException {
        return DateFunctionTestUtil.eval(DateFunctionTestUtil.NO_SETTINGS, input, field);
    }
}
