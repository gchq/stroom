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

import stroom.query.language.functions.ref.StoredValues;
import stroom.query.language.functions.ref.ValueReferenceIndex;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvFileSource;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.text.ParseException;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/// `floor`, `ceiling` and `round` with the different things that they can round by, and the functions that are
/// shorthand for them, as written in expressions.
class TestAbstractRoundingFunction {

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            // In London, which is an hour ahead of UTC in May.
            "floor(2024-05-15T10:20:00Z, 1d)|2024-05-14T23:00:00Z",
            "floor(2024-05-15T10:20:00Z, 1w)|2024-05-12T23:00:00Z",
            "floor(2024-05-15T10:20:00Z, 3M)|2024-03-31T23:00:00Z",
            "floor(2024-05-15T10:20:00Z, 1y)|2024-01-01T00:00:00Z",
            "ceiling(2024-05-15T10:20:00Z, 15m)|2024-05-15T10:30:00Z",
            "round(2024-05-15T10:20:00Z, 15m)|2024-05-15T10:15:00Z",
            "floor(2024-05-15T10:20:00Z, 'PT15M')|2024-05-15T10:15:00Z",
            "floor(2024-05-15T10:20:00Z, 'P3M')|2024-03-31T23:00:00Z",
            "floor(2024-05-15T10:20:00Z, '15m')|2024-05-15T10:15:00Z",
            "floor(2024-05-15T10:20:00Z, '1h30m')|2024-05-15T09:30:00Z",
            "floor(2024-05-15T10:20:00Z, '1w')|2024-05-12T23:00:00Z",
            // Numbers and text are used as dates.
            "floor(1715768400000, 1d)|2024-05-14T23:00:00Z",
            "floor('2024-05-15T10:20:00Z', 1d)|2024-05-14T23:00:00Z",
            "floor('1715768400000', 1d)|2024-05-14T23:00:00Z",
            "floor('1715768400000.0', 1d)|2024-05-14T23:00:00Z",
            "floor('1.7157684E12', 1d)|2024-05-14T23:00:00Z",
            // Origins.
            "floor(2024-03-15T10:00:00Z, 1y, 2024-04-01T00:00:00Z)|2023-04-01T00:00:00Z",
            "floor(2024-03-15T10:00:00Z, 1y, '2024-04-01T00:00:00')|2023-03-31T23:00:00Z",
            // Text with no offset is in the zone of the query.
            "floorTime('2024-05-15T10:20:00', 1h)|2024-05-15T09:00:00Z",
            "floorMonth('2024-05-15')|2024-04-30T23:00:00Z",
            // Spaces and fractions.
            "floor(2024-05-15T10:20:00Z, '1 M')|2024-04-30T23:00:00Z",
            "floor(2024-05-15T10:20:00Z, ' 1 h ')|2024-05-15T10:00:00Z",
            // A multiple of 0.7 days since 1970, as 0.7 days is not 16h47m59s999ms.
            "floor(1970-01-08T00:00:00, '0.7d')|1970-01-07T23:00:00Z",
            "floor(2024-05-15T10:20:00Z, '0.5h')|2024-05-15T10:00:00Z",
            "floorTime(2024-05-15T10:20:00Z, '1 h')|2024-05-15T10:00:00Z",
            "floorTime(2024-05-15T10:20:00Z, '0.25 h')|2024-05-15T10:15:00Z",
            // Whatever parseDuration reads.
            "floor(2024-05-15T10:20:00Z, '1.5h 30m')|2024-05-15T09:00:00Z",
            "floor(2024-05-15T10:20:00Z, '.5h')|2024-05-15T10:00:00Z",
            "floor(2024-05-15T10:20:00Z, '+1h')|2024-05-15T10:00:00Z",
            "floor(2024-05-15T10:20:00.0015Z, '1500us')|2024-05-15T10:20:00.001Z",
            "floor(2024-05-15T10:20:00Z, \"'1h'\")|2024-05-15T10:00:00Z",
            "floorTime(2024-05-15T10:20:00Z, '1.5h 30m')|2024-05-15T09:00:00Z",
            "floorTime(2024-05-15T10:20:00Z, '+1h')|2024-05-15T10:00:00Z",
            "floor(1970-01-08T00:00:00, '0.5w')|1970-01-07T23:00:00Z",
            // Whole days are a period however they are written.
            "floor(2024-05-15T10:20:00Z, '+1d')|2024-05-14T23:00:00Z",
            "floor(2024-05-15T10:20:00Z, '+P1D')|2024-05-14T23:00:00Z",
            "floor(2024-05-15T10:20:00Z, \"'1M'\")|2024-04-30T23:00:00Z",
            // A date with no time is midnight in the zone of the call.
            "floor(2024-03-15T10:00:00Z, 1y, '2024-04-01')|2023-03-31T23:00:00Z",
            "floor(2024-03-15T10:00:00Z, 1y, ' 2024-04-01 ', 'America/New_York')|2023-04-01T04:00:00Z",
            "floor(2024-05-15T10:20:00Z, 1w, parseDate('2024 01 07', 'yyyy MM dd'))|2024-05-11T23:00:00Z",
            "floor(2024-05-15T10:20:00Z, 1d, null())|2024-05-14T23:00:00Z",
            "floor(2024-05-15T10:20:00Z, 1y, 1711929600000)|2024-04-01T00:00:00Z",
            "floor(2024-05-15T10:20:00Z, 1y, '1711929600000')|2024-04-01T00:00:00Z",
            // A literal with no offset is in the zone of the query, whatever the zone of the call.
            "floor(2024-05-15T10:20:00Z, 1y, 2024-04-01T00:00:00, 'America/New_York')|2024-03-31T23:00:00Z",
            // Time zones.
            "floor(2024-05-15T10:20:00Z, 1d, null(), 'America/New_York')|2024-05-15T04:00:00Z",
            "floor(2024-05-15T10:20:00Z, 1d, null(), '+0530')|2024-05-14T18:30:00Z",
            "floor(2024-05-15T10:20:00Z, 1d, null(), 'GMT-5')|2024-05-15T05:00:00Z",
            // Text with no offset is in the zone of the call.
            "floor(2024-05-15T10:20:00Z, 1y, '2024-04-01T00:00:00', 'America/New_York')|2024-04-01T04:00:00Z",
            "floor('2024-05-15T10:20:00', 1h, null(), 'America/New_York')|2024-05-15T14:00:00Z",
            "floor(2024-05-15T10:20:00Z, 7d)|2024-05-12T23:00:00Z",
            "floor(2024-05-15T10:20:00Z, 2w)|2024-05-12T23:00:00Z",
            // The shorthand functions.
            "floorTime(2024-05-15T10:20:00Z, 15m)|2024-05-15T10:15:00Z",
            "ceilingTime(2024-05-15T10:20:00Z, 1d)|2024-05-15T23:00:00Z",
            "roundTime(2024-05-15T10:20:00Z, 3M)|2024-03-31T23:00:00Z",
            "floorTime(2024-05-15T10:20:00Z, 'P7D')|2024-05-08T23:00:00Z",
            "floorTime(2024-05-15T10:20:00Z, ' P7D ')|2024-05-08T23:00:00Z",
            "floorTime(2024-05-15T10:20:00Z, 'P7D', 2024-01-01T00:00:00Z)|2024-05-12T23:00:00Z",
            "floorTime(2024-05-15T10:20:00Z, 1w)|2024-05-12T23:00:00Z",
            "floorTime(2024-05-15T10:20:00Z, '15m')|2024-05-15T10:15:00Z",
            "floorTime(2024-05-15T10:20:00Z, '3M')|2024-03-31T23:00:00Z",
            "floorTime(2024-05-15T10:20:00Z, 'P3M')|2024-03-31T23:00:00Z"})
    void dates(final String expression, final Instant expected) throws ParseException {
        assertThat(eval(expression, Map.of(), ValNull.INSTANCE)).isEqualTo(ValDate.create(expected.toEpochMilli()));
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "floor(100m, 1h)|3600000",
            "ceiling(100m, 1h)|7200000",
            "round(100m, 1h)|7200000",
            "round(1h + 30m, 1h)|7200000",
            "floor(30h, 1d)|86400000",
            "floor('100m', 1h)|3600000",
            "floor(2024-05-15T10:20:00Z - 2024-05-15T09:01:00Z, 5m)|4500000"})
    void durations(final String expression, final long expected) throws ParseException {
        assertThat(eval(expression, Map.of(), ValNull.INSTANCE)).isEqualTo(ValDuration.create(expected));
    }

    // So that a saved dashboard doesn't show errors, or group by them.
    @ParameterizedTest
    @ValueSource(strings = {"N/A", "06/07/2024", "2024-07-06 12:00:00", "-12345678901234567890", "true", " ", "2h",
            "+2h", "PT1H", "1M", "1H"})
    void textThatIsNotADateIsNull(final String text) throws ParseException {
        for (final String expression : new String[]{"floorDay(${field})", "ceilingMonth(${field})",
                "roundHour(${field})", "floorTime(${field}, 1h)", "isWeekend(${field})"}) {
            assertThat(eval(expression, Map.of(), ValString.create(text))).as(expression).isEqualTo(ValNull.INSTANCE);
        }
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "floor(1.28, 1)|1.2",
            "ceiling(1.22, 1)|1.3",
            "round(1.25, 1)|1.3",
            "round(1.25, '1')|1.3",
            "round(1.239, '2.0')|1.24",
            "round(1.239, ' 2 ')|1.24",
            "round(1.5)|2",
            // These are not exact as doubles.
            "ceiling(1.1, 2)|1.1",
            "floor(0.29, 2)|0.29",
            "floor(4.35, 2)|4.35",
            "round(1.005, 2)|1.01",
            "round(1.5, 20)|1.5",
            "floor(1719820800000, 300)|1719820800000",
            "ceiling(1.5, 308)|1.5",
            "round(478934961435163.94, 1)|478934961435163.9",
            // Halfway is rounded up.
            "round(2.5, 1)|2.5",
            "round(0.25, 1)|0.3",
            "round(0 - 0.25, 1)|-0.2",
            "round(0 - 0.26, 1)|-0.3"})
    void numbers(final String expression, final double expected) throws ParseException {
        assertThat(eval(expression, Map.of(), ValNull.INSTANCE)).isEqualTo(ValDouble.create(expected));
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "floor(${field}, 1s)|0.7d|60480000",
            "floor(${field}, 1h)|-1.5h|-7200000",
            "ceiling(${field}, 1h)|-PT1H30M|-3600000",
            "round(${field}, 1s)|1h 30m|5400000"})
    void textOfADurationIsReadAsParseDurationDoes(final String expression, final String text, final long expected)
            throws ParseException {
        assertThat(eval(expression, Map.of(), ValString.create(text))).isEqualTo(ValDuration.create(expected));
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "soon|'soon' is not a date or a duration",
            "1H|'1H' is not a date or a duration: Unknown unit 'H' in a duration. Use ns, us, ms, s, m, h, d or w",
            // Not that parsePeriod can read it, as periods can't be rounded.
            "1M|'1M' is not a date or a duration: '1M' is a period of months or years, so is not a duration",
            "P1M|'P1M' is not a date or a duration: 'P1M' is a period of months or years, so is not a duration",
            "PT99999999999999999H|'PT99999999999999999H' is not a date or a duration: The duration is too long",
            "99999999999999h|'99999999999999h' is not a date or a duration: The duration is too long"})
    void textThatIsNotADateOrDuration(final String text, final String message) throws ParseException {
        assertThat(eval("floor(${field}, 1h)", Map.of(), ValString.create(text))).isEqualTo(ValErr.create(message));
    }

    // Recorded before dates and durations could be rounded, as nothing about numbers was meant to change, except that
    // decimal places are now exact.
    @ParameterizedTest
    @CsvFileSource(resources = "TestAbstractRoundingFunction_numbers.csv")
    void numbersAreRoundedAsTheyWere(final String function, final double number, final double expected)
            throws ParseException {
        assertThat(eval(function.replace("{}", "${field}"), Map.of(), ValDouble.create(number)))
                .isEqualTo(ValDouble.create(expected));
    }

    // They are whole milliseconds.
    @ParameterizedTest
    @ValueSource(strings = {
            "floor({})", "ceiling({})", "round({})", "floor({}, 1)", "ceiling({}, 6)", "round({}, 8)", "floor({}, 20)",
            "round({}, 308)"})
    void datesAndDurationsHaveNoDecimalPlaces(final String function) throws ParseException {
        final String expression = function.replace("{}", "${field}");
        for (final long millis : new long[]{0, 1788713250100L, 31041600, 1719820800001L, -1, Long.MAX_VALUE}) {
            assertThat(eval(expression, Map.of(), ValDate.create(millis))).isEqualTo(ValDate.create(millis));
            assertThat(eval(expression, Map.of(), ValDuration.create(millis))).isEqualTo(ValDuration.create(millis));
        }
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "floor(100m, 1M)|Can't round a duration by a period of months or years",
            "floor(power(10, 300), 1d)|A double is not a date",
            "floor(power(10, 400), 1d)|A double is not a date",
            "floor(isNull(1), 1d)|A boolean is not a date",
            "floor(100m, 1h, 2024-01-01T00:00:00Z)|An origin can only be used to round a date",
            "floor(1d, 1h)|Can't round a period",
            "floor(${field}, param('missing'))|no value was supplied for it",
            "floor(${field}, parseDuration('15mm'))|Unknown unit 'mm' in a duration",
            "floorTime(${field}, parseDuration('soon'))|Text cannot be parsed to a Duration",
            "floor('1e300', 1d)|'1e300' is too large to be a date",
            "floorTime(100m, 1h)|Use floor, ceiling or round to round a duration",
            "roundYear(1d)|A period is not a date",
            "ceilingMonth(isNull(1))|A boolean is not a date",
            "floorTime(${field}, 0h)|it must be at least one millisecond",
            "floorTime(${field}, 1M, 5m)|Invalid origin",
            "floorTime(${field}, 1M, 'soon')|Invalid origin"})
    void errors(final String expression, final String message) throws ParseException {
        final Val val = eval(expression, Map.of(), ValDate.create(0));
        assertThat(val.type()).as(val.toString()).isEqualTo(Type.ERR);
        assertThat(val.toString()).contains(message);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "floor(${field}, ${field})|expected to be a numeric or time period",
            "floor(${field}, ${field} + 1)|expected to be a numeric or time period",
            "floor(${field}, concat('1', ${field}))|expected to be a numeric or time period",
            "floor(${field}, count())|expected to be a numeric or time period",
            "floor(${field}, 1h, day() + ${field})|not something computed per row",
            "floor(${field}, 0)|must be positive",
            "floor(${field}, 309)|is too large",
            "floor(${field}, 1719820800000)|is too large",
            "floor(${field}, '1E3')|is too large",
            "floor(${field}, 2024-01-01T00:00:00Z)|can't be a date",
            "floor(${field}, 0h)|it must be at least one millisecond",
            "floor(${field}, 0M)|it must be more than zero",
            "floor(${field}, 'P1M2D')|it must only have one of years, months and days",
            "floor(${field}, 'soon')|is not a number of decimal places, a duration or a period: soon",
            "floor(${field}, 2, day())|can only have an origin and time zone if",
            "floor(${field}, 1d, ${field})|not something computed per row",
            "floor(${field}, 1d, 5m)|Invalid origin: 5m - it must be a date",
            "floor(${field}, 1d, 'soon')|Invalid origin",
            "floor(${field}, 1d, '2024-02-30')|Invalid origin: Text '2024-02-30' could not be parsed: Invalid date",
            "floor(${field}, 1d, 99999999999999999999)|Invalid origin",
            "floor(${field}, 1d, null(), 'Mars/Base')|Unknown time zone 'Mars/Base'",
            "floor(${field}, 1d, null(), 'BST')|Unknown time zone 'BST'. Use a name such as Europe/London",
            "floor(${field}, 1d, null(), '')|There is no time zone"})
    void mistakesThatAreReportedWhenTheExpressionIsParsed(final String expression, final String message) {
        assertThatThrownBy(() -> eval(expression, Map.of(), ValDate.create(0)))
                .hasMessageContaining(message);
    }

    @Test
    void queryParameters() throws ParseException {
        final Val time = ValDate.create(Instant.parse("2024-05-15T10:20:00Z").toEpochMilli());

        for (final String by : new String[]{"PT15M", "15m"}) {
            assertThat(eval("floor(${field}, param('by'))", Map.of("by", by), time))
                    .isEqualTo(ValDate.create(Instant.parse("2024-05-15T10:15:00Z").toEpochMilli()));
        }
        assertThat(eval("floor(${field}, param('by'))", Map.of("by", "3M"), time))
                .isEqualTo(ValDate.create(Instant.parse("2024-03-31T23:00:00Z").toEpochMilli()));
        assertThat(eval("floor(${field}, param('by'))", Map.of("by", "2"), ValDouble.create(1.239)))
                .isEqualTo(ValDouble.create(1.23));
        assertThat(eval("floor(${field}, param('by'))", Map.of("by", "soon"), time).toString())
                .contains("is not a number of decimal places, a duration or a period: soon");
        assertThat(eval("floor(${field}, 1w, param('origin'))", Map.of("origin", "2024-01-07T00:00:00"), time))
                .isEqualTo(ValDate.create(Instant.parse("2024-05-11T23:00:00Z").toEpochMilli()));
        assertThat(eval("floor(${field}, 1d, '+12024-01-01')", Map.of(), time))
                .isEqualTo(eval("floor(${field}, 1d, '+12024-01-01T00:00:00')", Map.of(), time));
        assertThat(eval("floor(${field}, 1w, param('origin'))", Map.of("origin", "1704585600000"), time))
                .isEqualTo(ValDate.create(Instant.parse("2024-05-11T23:00:00Z").toEpochMilli()));
        assertThat(eval("floor(${field}, param('by') + 1)", Map.of("by", "1"), ValDouble.create(1.239)).type())
                .isEqualTo(Type.ERR);
        assertThat(eval("floor(${field}, 1d, null(), param('zone'))", Map.of("zone", "America/New_York"), time))
                .isEqualTo(ValDate.create(Instant.parse("2024-05-15T04:00:00Z").toEpochMilli()));
    }

    @Test
    void zoneOfEachRow() throws ParseException {
        final String expression = "floor(2024-05-15T10:20:00Z, 1d, null(), ${field})";

        assertThat(eval(expression, Map.of(), ValString.create("America/New_York")))
                .isEqualTo(ValDate.create(Instant.parse("2024-05-15T04:00:00Z").toEpochMilli()));
        assertThat(eval(expression, Map.of(), ValString.create("Mars/Base")).toString())
                .contains("Unknown time zone 'Mars/Base'");
        assertThat(eval(expression, Map.of(), ValString.create("")).toString()).contains("There is no time zone");
        assertThat(eval(expression, Map.of(), ValNull.INSTANCE).toString()).contains("There is no time zone");
        assertThat(eval(expression, Map.of(), ValErr.create("bad"))).isEqualTo(ValErr.create("bad"));
    }

    @Test
    void namedExpressions() throws ParseException {
        final FieldIndex fieldIndex = new FieldIndex();
        fieldIndex.create("field");
        final ExpressionParser parser = new ExpressionParser(new ParamFactory(Map.of(
                "perRow", parse("${field} + 1"),
                "constant", parse("param('by')"))));

        assertThatThrownBy(() -> parser.parse(DateFunctionTestUtil.LONDON, fieldIndex, "floor(${field}, perRow)"))
                .hasMessageContaining("expected to be a numeric or time period");
        assertThat(parser.parse(DateFunctionTestUtil.LONDON, fieldIndex, "floor(${field}, constant)")).isNotNull();
    }

    @Test
    void nullsAndErrorsAreKept() throws ParseException {
        assertThat(eval("floor(${field}, 1d)", Map.of(), ValNull.INSTANCE)).isEqualTo(ValNull.INSTANCE);
        assertThat(eval("floor(${field}, 1d)", Map.of(), ValErr.create("bad"))).isEqualTo(ValErr.create("bad"));
    }

    @Test
    void textOfTheExpressionIsParsedTheSame() throws ParseException {
        final String expression = "floor(${field}, 3M, 2024-04-01T00:00:00.000Z, 'Europe/Paris')";

        assertThat(parse(expression).toString()).isEqualTo(expression);
    }

    private static Val eval(final String input,
                            final Map<String, String> params,
                            final Val field) throws ParseException {
        final Expression expression = parse(input);
        expression.setStaticMappedValues(params);
        final ValueReferenceIndex valueReferenceIndex = new ValueReferenceIndex();
        expression.addValueReferences(valueReferenceIndex);
        final StoredValues storedValues = valueReferenceIndex.createStoredValues();
        final Generator generator = expression.createGenerator();
        generator.set(new Val[]{field}, storedValues);
        return generator.eval(storedValues, null);
    }

    private static Expression parse(final String input) throws ParseException {
        final FieldIndex fieldIndex = new FieldIndex();
        fieldIndex.create("field");
        return new ExpressionParser(new ParamFactory(Map.of()))
                .parse(DateFunctionTestUtil.LONDON, fieldIndex, input);
    }
}
