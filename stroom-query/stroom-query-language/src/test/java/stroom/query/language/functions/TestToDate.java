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
import org.junit.jupiter.params.provider.ValueSource;

import java.text.ParseException;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestToDate {

    @ParameterizedTest
    @ValueSource(strings = {"2024-02-30T10:00:00", "2023-02-29T10:00:00Z", "2024-04-31T00:00:00+01:00",
            "2024-04-31T24:00:00Z", "2024-02-30"})
    void dayThatTheMonthDoesNotHave(final String text) throws ParseException {
        for (final String function : new String[]{"toDate", "getDayOfMonth", "formatDate"}) {
            assertThat(eval(function + "(${field})", ValString.create(text)))
                    .isEqualTo(ValErr.create("'" + text + "' is not a date"));
        }
        assertThat(eval("floorDay(${field})", ValString.create(text))).isEqualTo(ValNull.INSTANCE);
        assertThatThrownBy(() -> eval("floor(2024-07-01T00:00:00Z, 1d, '" + text + "')", ValNull.INSTANCE))
                .isInstanceOf(TokenException.class)
                .hasMessageContaining("Invalid date");
    }

    @Test
    void textUsedAsANumberWithADayThatTheMonthDoesNotHave() throws ParseException {
        final Val text = ValString.create("2024-02-30T10:00:00Z");
        assertThat(eval("toLong(${field})", text).type()).isEqualTo(Type.ERR);
        assertThat(eval("toDouble(${field})", text).type()).isEqualTo(Type.ERR);
        assertThat(eval("${field} = 2024-02-29T10:00:00Z", text)).isEqualTo(ValBoolean.FALSE);
        assertThat(eval("toLong(${field})", ValString.create("2024-02-29T10:00:00Z")))
                .isEqualTo(ValLong.create(1_709_200_800_000L));
        assertThat(eval("${field} = 2024-02-29T10:00:00Z", ValString.create("2024-02-29T10:00:00Z")))
                .isEqualTo(ValBoolean.TRUE);
    }

    @Test
    void literalOfADayThatTheMonthDoesNotHave() {
        for (final String literal : new String[]{"2024-02-30T10:00:00", "2024-02-30T10:00:00Z"}) {
            assertThatThrownBy(() -> eval(literal, ValNull.INSTANCE))
                    .isInstanceOf(TokenException.class)
                    .hasMessageContaining("Invalid date 'FEBRUARY 30'");
        }
    }

    @ParameterizedTest
    @CsvSource({
            "2024-02-29T10:00:00Z,2024-02-29T10:00:00Z",
            "2023-12-31T24:00:00Z,2024-01-01T00:00:00Z",
            "2024-02-29T24:00:00Z,2024-03-01T00:00:00Z",
            "2024-03-01T00:00:00Z,2024-03-01T00:00:00Z",
            "2024-04-30T00:30:00+01:00,2024-04-29T23:30:00Z"})
    void dayThatTheMonthHas(final String text, final Instant expected) throws ParseException {
        assertThat(eval("toDate(${field})", ValString.create(text)))
                .isEqualTo(ValDate.create(expected.toEpochMilli()));
    }

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
            "toDate(power(10, 300))|A double is not a date",
            "toDate(isNull(1))|A boolean is not a date"})
    void errors(final String expression, final String message) throws ParseException {
        final Val val = eval(expression, ValString.create("soon"));
        assertThat(val.type()).as(val.toString()).isEqualTo(Type.ERR);
        assertThat(val.toString()).contains(message);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "toDate(1h)|A duration is not a date",
            "toDate(1d)|A period is not a date"})
    void mistakesThatAreReportedWhenTheExpressionIsParsed(final String expression, final String message) {
        assertThatThrownBy(() -> eval(expression))
                .isInstanceOf(TokenException.class)
                .hasMessageContaining(message);
    }

    // In a query whose time zone is Europe/London.
    private static Val eval(final String expression, final Val field) throws ParseException {
        return DateFunctionTestUtil.eval(DateFunctionTestUtil.LONDON, expression, field);
    }

    private static Val eval(final String expression) throws ParseException {
        return eval(expression, ValNull.INSTANCE);
    }
}
