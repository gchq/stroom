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

class TestToDate {

    @Test
    void theTimeZoneIsOnlyNeededForTextWithNoOffset() throws ParseException {
        assertThat(DateFunctionTestUtil.eval(
                DateFunctionTestUtil.UNKNOWN_ZONE, "toDate(${field})", ValString.create("2024-01-01T00:00:00Z")))
                .isEqualTo(ValDate.create(1704067200000L));
        assertThat(DateFunctionTestUtil.eval(
                DateFunctionTestUtil.UNKNOWN_ZONE, "toDate(${field})", ValString.create("2024-01-01T00:00:00"))
                .toString())
                .contains("Unknown time zone 'Mars/Base'");
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "2024-07-01T09:00:00Z|2024-07-01T09:00:00Z",
            "2024-07-01T09:00:00+02:00|2024-07-01T07:00:00Z",
            // With no offset the date is in the zone of the query, an hour ahead of UTC in July.
            "2024-07-01T09:00:00|2024-07-01T08:00:00Z",
            "2024-07-01|2024-06-30T23:00:00Z",
            "1719824400000|2024-07-01T09:00:00Z"})
    void texts(final String text, final Instant expected) throws ParseException {
        final ValDate date = ValDate.create(expected.toEpochMilli());
        assertThat(eval("toDate(${field})", ValString.create(text))).isEqualTo(date);
        assertThat(eval("toDate('" + text + "')")).isEqualTo(date);
    }

    @Test
    void otherTypes() throws ParseException {
        final ValDate date = ValDate.create(1719824400000L);
        assertThat(eval("toDate(${field})", ValLong.create(1719824400000L))).isEqualTo(date);
        assertThat(eval("toDate(${field})", date)).isEqualTo(date);
        assertThat(eval("toDate(${field})", ValNull.INSTANCE)).isEqualTo(ValNull.INSTANCE);
        assertThat(eval("toDate(${field})", ValString.create(""))).isEqualTo(ValNull.INSTANCE);
        assertThat(eval("toDate(${field})", ValErr.create("bad"))).isEqualTo(ValErr.create("bad"));
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "toDate(${field})|'soon' is not a date",
            "toDate(1h)|A duration is not a date",
            "toDate(1d)|A period is not a date",
            "toDate(isNull(1))|A boolean is not a date"})
    void errors(final String expression, final String message) throws ParseException {
        final Val val = eval(expression, ValString.create("soon"));
        assertThat(val.type()).as(val.toString()).isEqualTo(Type.ERR);
        assertThat(val.toString()).contains(message);
    }

    // In a query whose time zone is Europe/London.
    private static Val eval(final String expression, final Val field) throws ParseException {
        return DateFunctionTestUtil.eval(DateFunctionTestUtil.LONDON, expression, field);
    }

    private static Val eval(final String expression) throws ParseException {
        return eval(expression, ValNull.INSTANCE);
    }
}
