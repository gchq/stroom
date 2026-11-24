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
import org.junit.jupiter.params.provider.CsvSource;

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

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "floor(1.28, 1)|1.2",
            "ceiling(1.22, 1)|1.3",
            "round(1.25, 1)|1.3",
            "round(1.25, '1')|1.3",
            "round(1.239, '2.0')|1.24",
            "round(1.239, ' 2 ')|1.24",
            "round(1.5)|2"})
    void numbers(final String expression, final double expected) throws ParseException {
        assertThat(eval(expression, Map.of(), ValNull.INSTANCE)).isEqualTo(ValDouble.create(expected));
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "floor(100m, 1M)|Can't round a duration by a period of months or years",
            "floor(100m, 1h, 2024-01-01T00:00:00Z)|An origin can only be used to round a date",
            "floor(1d, 1h)|Can't round a period",
            "floor(${field}, param('missing'))|no value was supplied for it",
            "floor(${field}, parseDuration('15mm'))|Text cannot be parsed to a Duration",
            "floorTime(${field}, parseDuration('soon'))|Text cannot be parsed to a Duration",
            "floor('1e300', 1d)|Overflow",
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
            "floor(${field}, 0h)|it must be at least one millisecond",
            "floor(${field}, 0M)|it must be more than zero",
            "floor(${field}, 'P1M2D')|it must only have one of years, months and days",
            "floor(${field}, 'soon')|is not a number of decimal places, a duration or a period: soon",
            "floor(${field}, 2, day())|can only have an origin and time zone if",
            "floor(${field}, 1d, ${field})|Invalid origin",
            "floor(${field}, 1d, 5m)|Invalid origin: 5m - it must be a date",
            "floor(${field}, 1d, 'soon')|Invalid origin",
            "floor(${field}, 1d, '2024-02-30')|Invalid origin",
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
