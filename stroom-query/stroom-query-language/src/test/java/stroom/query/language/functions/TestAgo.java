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

import stroom.query.api.DateTimeSettings;
import stroom.query.api.UserTimeZone;
import stroom.query.api.token.TokenException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.text.ParseException;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestAgo {

    private static final ExpressionContext CONTEXT = ExpressionContext.builder()
            .dateTimeSettings(DateTimeSettings.builder()
                    .referenceTime(Instant.parse("2024-03-31T11:00:00Z").toEpochMilli())
                    .timeZone(UserTimeZone.fromId("Europe/London"))
                    .build())
            .build();

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            // Now is 12:00 in London, an hour after which the clocks went forward.
            "ago(2h)|2024-03-31T09:00:00Z",
            "ago(24h)|2024-03-30T11:00:00Z",
            "ago(1d)|2024-03-30T12:00:00Z",
            "ago(1M)|2024-02-29T12:00:00Z",
            "ago('1d')|2024-03-30T12:00:00Z",
            "ago('PT2H')|2024-03-31T09:00:00Z",
            "ago(-1h)|2024-03-31T12:00:00Z",
            "ago('-1h')|2024-03-31T12:00:00Z",
            "ago('1h 30m')|2024-03-31T09:30:00Z",
            "ago('- 1h 30m')|2024-03-31T12:30:00Z",
            "ago('1.5h')|2024-03-31T09:30:00Z",
            "ago('3M')|2023-12-31T12:00:00Z",
            "ago('1 d')|2024-03-30T12:00:00Z",
            "ago('1 M')|2024-02-29T12:00:00Z",
            "ago('1d 2d')|2024-03-28T12:00:00Z",
            "ago('1d 2h')|2024-03-30T09:00:00Z",
            // A period backwards, so it keeps its local time.
            "ago('-1d')|2024-04-01T11:00:00Z",
            "ago('-P1D')|2024-04-01T11:00:00Z"})
    void times(final String expression, final Instant expected) throws ParseException {
        final ValDate date = ValDate.create(expected.toEpochMilli());
        assertThat(DateFunctionTestUtil.eval(CONTEXT, expression, ValNull.INSTANCE)).isEqualTo(date);
    }

    @Test
    void sameAsSubtractingFromNow() throws ParseException {
        for (final String amount : new String[]{"1d", "2h", "3M", "1y"}) {
            assertThat(DateFunctionTestUtil.eval(CONTEXT, "ago(" + amount + ")", ValNull.INSTANCE))
                    .isEqualTo(DateFunctionTestUtil.eval(CONTEXT, "now() - " + amount, ValNull.INSTANCE));
        }
    }

    @Test
    void fields() throws ParseException {
        assertThat(DateFunctionTestUtil.eval(CONTEXT, "ago(${field})", ValDuration.create(7_200_000)))
                .isEqualTo(ValDate.create(Instant.parse("2024-03-31T09:00:00Z").toEpochMilli()));
        assertThat(DateFunctionTestUtil.eval(CONTEXT, "ago(${field})", ValNull.INSTANCE))
                .isEqualTo(ValNull.INSTANCE);
        assertThat(DateFunctionTestUtil.eval(CONTEXT, "ago(${field})", ValLong.create(5)).toString())
                .contains("A long is not a duration or period");
    }

    @Test
    void longRunOfDashes() throws ParseException {
        final Val val = DateFunctionTestUtil.eval(CONTEXT, "ago(${field})", ValString.create("-".repeat(50_000)));
        assertThat(val.type()).isEqualTo(Type.ERR);
    }

    @Test
    void noReferenceTime() throws ParseException {
        final ExpressionContext noReferenceTime = ExpressionContext.builder()
                .dateTimeSettings(DateTimeSettings.builder().build().withoutReferenceTime())
                .build();
        assertThat(DateFunctionTestUtil.eval(noReferenceTime, "ago(1d)", ValNull.INSTANCE).toString())
                .contains("referenceTime not set");
        assertThat(DateFunctionTestUtil.eval(
                DateFunctionTestUtil.NO_SETTINGS, "ago(1d)", ValNull.INSTANCE).toString())
                .contains("referenceTime not set");
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "ago(5)|A double is not a duration or period",
            "ago('soon')|is not a number and a unit",
            "ago('1M 2h')|months and years vary",
            "ago('1000')|is not a number and a unit",
            "ago('-1000')|is not a number and a unit",
            "ago('--1d')|is not a number and a unit",
            "ago('- -1d')|is not a number and a unit",
            "ago('1 5m')|is not a number and a unit",
            "ago('1m s')|is not a number and a unit",
            "ago('1.5M')|is not a number and a unit",
            "ago('PT9999999999999999S')|overflow",
            "ago('2000000000y2000000000y')|overflow"})
    void mistakesThatAreReportedWhenTheExpressionIsParsed(final String expression, final String message) {
        // Not just any exception, so that the editor can show where the mistake is.
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
