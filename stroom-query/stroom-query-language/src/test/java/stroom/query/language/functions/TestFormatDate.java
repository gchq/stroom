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
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

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
