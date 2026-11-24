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

import stroom.query.api.token.TokenException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.text.ParseException;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestFormatDate {

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "|2024-07-01T09:00:00.000+0100",
            ", 'yyyy-MM-dd HH:mm'|2024-07-01 09:00",
            ", 'yyyy-MM-dd HH:mm', 'UTC'|2024-07-01 08:00",
            // Text that says it is in UTC is.
            ", \"yyyy-MM-dd'T'HH:mm:ss'Z'\"|2024-07-01T08:00:00Z",
            ", \"HH:mm 'UTC'\"|08:00 UTC",
            ", \"HH:mm 'Z'\", 'Europe/Paris'|10:00 Z",
            ", \"'Z' HH:mm\"|Z 09:00"})
    void formatDateUsesTheZoneOfTheQuery(final String otherParams, final String expected) throws ParseException {
        final String params = otherParams == null
                ? ""
                : otherParams;
        final long millis = Instant.parse("2024-07-01T08:00:00Z").toEpochMilli();

        assertThat(evalInLondon("formatDate(" + millis + params + ")", ValNull.INSTANCE))
                .isEqualTo(ValString.create(expected));
        assertThat(evalInLondon("formatDate(${field}" + params + ")", ValDate.create(millis)))
                .isEqualTo(ValString.create(expected));
    }

    @Test
    void zoneOfEachRow() throws ParseException {
        final String expression = "formatDate(2024-07-01T08:00:00Z, 'HH:mm', ${field})";

        assertThat(evalInLondon(expression, ValString.create("America/New_York"))).isEqualTo(ValString.create("04:00"));
        assertThat(evalInLondon(expression, ValString.create("+0530"))).isEqualTo(ValString.create("13:30"));
        assertThat(evalInLondon(expression, ValString.create("Mars/Base")).toString())
                .contains("Unknown time zone 'Mars/Base'");
        assertThat(evalInLondon(expression, ValString.create("")).toString()).contains("There is no time zone");
    }

    @Test
    void errorOfTheZoneIsKept() throws ParseException {
        assertThat(evalInLondon("formatDate(2024-07-01T08:00:00Z, 'HH:mm', ${field})", ValErr.create("lookup failed")))
                .isEqualTo(ValErr.create("lookup failed"));
    }

    @Test
    void zoneNames() throws ParseException {
        assertThat(evalInLondon("formatDate(2024-07-01T08:00:00Z, 'HH:mm', '')", ValNull.INSTANCE))
                .isEqualTo(ValString.create("08:00"));
        // A short name is refused, whether it is written or comes from a field.
        assertThatThrownBy(() -> evalInLondon("formatDate(2024-07-01T08:00:00Z, 'HH:mm', 'BST')", ValNull.INSTANCE))
                .hasMessageContaining("Unknown time zone 'BST'");
        assertThat(evalInLondon("formatDate(2024-07-01T08:00:00Z, 'HH:mm', ${field})", ValString.create("BST"))
                .toString())
                .contains("Unknown time zone 'BST'");
    }

    @Test
    void mistakesThatAreReportedWhenTheExpressionIsParsed() {
        for (final String zone : new String[]{"'UTC'", "${field}"}) {
            assertThatThrownBy(() -> evalInLondon("formatDate('abc', 'HH', " + zone + ")", ValString.create("UTC")))
                    .isInstanceOf(TokenException.class)
                    .hasMessageContaining("'abc' is not a date");
        }
        assertThatThrownBy(() -> DateFunctionTestUtil.eval(
                DateFunctionTestUtil.UNKNOWN_ZONE, "formatDate(${field})", ValDate.create(0)))
                .isInstanceOf(TokenException.class)
                .hasMessageContaining("Unknown time zone 'Mars/Base'");
    }

    @Test
    void textWithNoOffset() throws ParseException {
        for (final String date : new String[]{"'2024-07-01T09:00:00'", "${field}"}) {
            final ValString field = ValString.create("2024-07-01T09:00:00");
            // It is in the time zone that it is formatted in.
            assertThat(evalInLondon("formatDate(" + date + ")", field))
                    .isEqualTo(ValString.create("2024-07-01T09:00:00.000+0100"));
            // Text that says it is in UTC is, but it is still read in the time zone of the query.
            assertThat(evalInLondon("formatDate(" + date + ", \"HH:mm'Z'\")", field))
                    .isEqualTo(ValString.create("08:00Z"));
            assertThat(evalInLondon("formatDate(" + date + ", 'HH XX', 'America/New_York')", field))
                    .isEqualTo(ValString.create("09 -0400"));
        }
        assertThat(evalInLondon("formatDate(${field})", ValString.create(" "))).isEqualTo(ValNull.INSTANCE);
        // A blank is null whatever the time zone is.
        assertThat(evalInLondon("formatDate(${field}, 'HH', ${field})", ValString.create(" ")))
                .isEqualTo(ValNull.INSTANCE);
        assertThat(evalInLondon("formatDate('', 'HH', ${field})", ValString.create("Mars/Base")))
                .isEqualTo(ValNull.INSTANCE);
    }

    @Test
    void formattedDateIsReadBack() throws ParseException {
        final Val date = ValDate.create(Instant.parse("2024-07-01T08:00:00Z").toEpochMilli());

        assertThat(evalInLondon("parseDate(formatDate(${field}))", date)).isEqualTo(date);
    }

    @Test
    void noSettingsIsUtc() throws ParseException {
        final Val date = ValDate.create(Instant.parse("2024-07-01T08:00:00Z").toEpochMilli());

        assertThat(evalWithNoSettings("formatDate(${field})", date))
                .isEqualTo(ValString.create("2024-07-01T08:00:00.000Z"));
    }

    private static Val evalInLondon(final String input, final Val field) throws ParseException {
        return DateFunctionTestUtil.eval(DateFunctionTestUtil.LONDON, input, field);
    }

    private static Val evalWithNoSettings(final String input, final Val field) throws ParseException {
        return DateFunctionTestUtil.eval(DateFunctionTestUtil.NO_SETTINGS, input, field);
    }
}
