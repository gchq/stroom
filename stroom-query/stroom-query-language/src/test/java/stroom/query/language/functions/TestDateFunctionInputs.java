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
import stroom.query.language.functions.ref.StoredValues;
import stroom.query.language.functions.ref.ValueReferenceIndex;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.text.ParseException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/// Gives every argument of the functions of dates, durations and periods every kind of value, in every way that it can
/// be given, and checks that they all follow the same rules.
class TestDateFunctionInputs {

    private static final String DATE = "2024-07-03T09:00:00Z";
    private static final String LATER_DATE = "2024-08-05T10:00:00Z";
    private static final String NO_VALUE = "null";
    private static final String ERROR = "error";

    private static final ExpressionContext LONDON = createContext("Europe/London");
    private static final ExpressionContext UNKNOWN_ZONE = createContext("Mars/Base");

    private static final List<Input> DATES = List.of(
            literal(DATE, ValDate.create(Instant.parse(DATE).toEpochMilli())),
            text(DATE),
            text("2024-07-03T09:00:00"),
            text("2024-07-03"),
            text("2024-07-03T09:00:00.123+02:00"),
            text(" 2024-07-03T09:00:00Z "));
    private static final List<Input> NUMBERS = List.of(
            literal("1719997200000", ValLong.create(1719997200000L)),
            literal("2", ValLong.create(2)),
            literal("1.5", ValDouble.create(1.5)),
            text("1719997200000"),
            text("2"),
            text(" 2 "),
            text("1.5"),
            text("1E3"),
            text("-5"));
    private static final List<Input> DURATIONS = List.of(
            literal("2h", ValDuration.create(7_200_000)),
            text("2h"),
            text("PT2H"),
            text("1h30m"),
            text(" 2h "),
            text("2 h"),
            text("1.5h"),
            text("-2h"),
            text("-1.5h"));
    private static final List<Input> PERIODS = List.of(
            literal("1d", ValPeriod.create(0, 0, 1)),
            literal("1M", ValPeriod.create(0, 1, 0)),
            text("1d"),
            text("1 d"),
            text("P1D"),
            text("1M"),
            text("1 M"),
            text("P1M"),
            text("1y"));
    private static final List<Input> NULLS = List.of(
            literal("null()", ValNull.INSTANCE),
            text(""),
            text(" "));
    private static final List<Input> ERRORS = List.of(
            literal("true()", ValBoolean.TRUE),
            text("true"),
            text("soon"),
            text("1M 2h"),
            text("NaN"),
            text("Infinity"),
            field("NaN", ValDouble.create(Double.NaN)),
            field("infinity", ValDouble.create(Double.POSITIVE_INFINITY)),
            field("NaN float", ValFloat.create(Float.NaN)),
            field("huge number", ValDouble.create(1e300)));
    private static final List<Input> TOO_LARGE = List.of(
            text("99999999999999999999"),
            text("-99999999999999999999"),
            text("+99999999999999999999"),
            text(" 99999999999999999999 "),
            text("1e30"),
            text("+999999999-01-01T00:00:00Z"),
            text("+999999999-01-01T00:00:00"),
            text("+999999999-01-01"),
            text("-999999999-01-01"));
    private static final List<Input> NAMES = List.of(
            text("Europe/London"),
            text("UTC"),
            text(" UTC "),
            text("+02"),
            text("BST"),
            text("Mars/Base"),
            text("year"),
            text("Year"),
            text("day"),
            text("s"),
            text("HH"));
    private static final Input ERROR_FIELD = field("error", ValErr.create("bad"));
    private static final List<Input> ALL = Stream.of(DATES, NUMBERS, DURATIONS, PERIODS, NULLS, ERRORS, NAMES)
            .flatMap(List::stream)
            .toList();

    private static final List<Argument> DATE_ARGUMENTS = Stream.concat(
                    Stream.of("getYear", "getQuarter", "getMonth", "getDayOfMonth", "getDayOfWeek", "getDayOfYear",
                                    "getWeekOfYear", "getWeekBasedYear", "getHour", "getMinute", "getSecond",
                                    "getMillisecond", "timeOfDay", "toDate", "formatDate")
                            .map(name -> name + "({})"),
                    Stream.of("formatDate({}, 'yyyy HH')",
                            "formatDate({}, 'yyyy HH', 'Europe/London')",
                            "formatDate({}, \"yyyy HH'Z'\")",
                            "datePart({}, 'year')",
                            "dateDiff('day', {}, " + LATER_DATE + ")",
                            "dateDiff('hour', " + LATER_DATE + ", {})",
                            "periodBetween({}, " + LATER_DATE + ")",
                            "periodBetween(" + LATER_DATE + ", {})",
                            "toEpoch({}, 's')"))
            .map(Argument::new)
            .toList();
    // These give a null for text that is not a date, and an error in every row for a mistake in a constant, as they
    // always have.
    private static final List<Argument> OLDER_DATE_ARGUMENTS = Stream.concat(
                    Stream.of("isWeekend", "floorDay", "floorHour", "floorMinute", "floorSecond", "floorWeek",
                                    "floorMonth", "floorYear", "ceilingDay", "ceilingHour", "ceilingMinute",
                                    "ceilingSecond", "ceilingWeek", "ceilingMonth", "ceilingYear", "roundDay",
                                    "roundHour", "roundMinute", "roundSecond", "roundWeek", "roundMonth", "roundYear")
                            .map(name -> name + "({})"),
                    Stream.of("floorTime({}, 1h)",
                            "ceilingTime({}, 1M)",
                            "roundTime({}, 1d)"))
            .map(Argument::new)
            .toList();
    // This only reads numbers if there is no pattern, and text must be in the pattern.
    private static final List<Argument> PARSE_DATE_ARGUMENTS =
            Stream.of("parseDate({})", "parseDate({}, 'yyyy-MM-dd')")
                    .map(Argument::new)
                    .toList();
    // These round a date or a duration.
    private static final List<Argument> DATE_OR_DURATION_ARGUMENTS =
            Stream.of("floor({}, 1d)", "ceiling({}, 1M)", "round({}, 1h)")
                    .map(Argument::new)
                    .toList();
    private static final List<Argument> OTHER_ARGUMENTS = Stream.of(
                    "ago({})",
                    "toDuration({})",
                    "toPeriod({})",
                    "parsePeriod({})",
                    "formatPeriod({})",
                    "makeDate({}, 7, 1)",
                    "makeDate(2024, {}, 1)",
                    "makeDate(2024, 7, 1, {}, 0)",
                    "makePeriod({}, 0, 0)",
                    "makePeriod(0, 0, {})",
                    "fromEpoch({}, 's')",
                    // Units.
                    "datePart(" + DATE + ", {})",
                    "dateDiff({}, " + DATE + ", " + LATER_DATE + ")",
                    "fromEpoch(1, {})",
                    "toEpoch(" + DATE + ", {})",
                    // Time zones.
                    "getYear(" + DATE + ", {})",
                    "timeOfDay(" + DATE + ", {})",
                    "datePart(" + DATE + ", 'hour', {})",
                    "dateDiff('day', " + DATE + ", " + LATER_DATE + ", {})",
                    "periodBetween(" + DATE + ", " + LATER_DATE + ", {})",
                    "makeDate(2024, 7, 1, 0, 0, 0, 0, {})",
                    "floor(" + DATE + ", 1d, null(), {})",
                    "formatDate(" + DATE + ", 'HH', {})",
                    "parseDate('2024-07-03 09', 'yyyy-MM-dd HH', {})")
            .map(Argument::new)
            .toList();
    // These round a number.
    private static final List<Argument> NUMBER_ARGUMENTS = Stream.of(
                    "floor({})", "ceiling({})", "round({})", "floor({}, 2)", "ceiling({}, 1)", "round({}, 1)")
            .map(Argument::new)
            .toList();
    // These can't be a field.
    private static final List<Argument> CONSTANT_ARGUMENTS = Stream.of(
                    "floor(" + DATE + ", {})",
                    "floor(100m, {})",
                    "floorTime(" + DATE + ", {})",
                    "ceilingTime(" + DATE + ", {})",
                    "roundTime(" + DATE + ", {})",
                    "floor(" + DATE + ", 1d, {})",
                    "floor(" + DATE + ", 1M, {}, 'America/New_York')",
                    "ceilingTime(" + DATE + ", 1h, {})")
            .map(Argument::new)
            .toList();
    // These give an error in every row for a mistake in a constant, as they always have.
    private static final List<Argument> DURATION_ARGUMENTS = Stream.of(
                    "parseDuration({})",
                    "formatDuration({})",
                    "formatDuration({}, 'd.hh:mm:ss')",
                    "formatISODuration({})")
            .map(Argument::new)
            .toList();
    // These must be text, or don't treat fields and constants the same.
    private static final List<Argument> VARIED_ARGUMENTS = Stream.of(
                    "formatDate(" + DATE + ", {})",
                    "parseDate('2024', {})",
                    // A number that is written is text that isn't in ISO format.
                    "parseISODuration({})",
                    // The last can be an hour or a time zone.
                    "makeDate(2024, 7, 1, {})")
            .map(Argument::new)
            .toList();
    // There is no origin if it is written as null(), but a named one could be a query parameter with no value.
    private static final Input WRITTEN_NULL = NULLS.getFirst();
    // A blank time zone that is written is UTC, as it always has been, but one from a field is an error.
    private static final Set<String> BLANK_ZONE_IS_UTC = Set.of(
            "formatDate(" + DATE + ", 'HH', {})",
            "parseDate('2024-07-03 09', 'yyyy-MM-dd HH', {})");

    static Stream<Argument> allArguments() {
        return Stream.of(DATE_ARGUMENTS, OLDER_DATE_ARGUMENTS, PARSE_DATE_ARGUMENTS, DATE_OR_DURATION_ARGUMENTS,
                        OTHER_ARGUMENTS, DURATION_ARGUMENTS, CONSTANT_ARGUMENTS, VARIED_ARGUMENTS, NUMBER_ARGUMENTS)
                .flatMap(List::stream);
    }

    static Stream<Argument> numberArguments() {
        return NUMBER_ARGUMENTS.stream();
    }

    static Stream<Argument> dateArguments() {
        return DATE_ARGUMENTS.stream();
    }

    static Stream<Argument> olderDateArguments() {
        return OLDER_DATE_ARGUMENTS.stream();
    }

    static Stream<Argument> durationArguments() {
        return DURATION_ARGUMENTS.stream();
    }

    static Stream<Argument> dateOrDurationArguments() {
        return DATE_OR_DURATION_ARGUMENTS.stream();
    }

    static Stream<Argument> fieldArguments() {
        return Stream.of(DATE_ARGUMENTS, OLDER_DATE_ARGUMENTS, PARSE_DATE_ARGUMENTS, DATE_OR_DURATION_ARGUMENTS,
                        OTHER_ARGUMENTS, DURATION_ARGUMENTS)
                .flatMap(List::stream);
    }

    static Stream<Argument> constantArguments() {
        return CONSTANT_ARGUMENTS.stream();
    }

    static Stream<Argument> argumentsCheckedWhenParsed() {
        return Stream.of(DATE_ARGUMENTS, PARSE_DATE_ARGUMENTS, OTHER_ARGUMENTS).flatMap(List::stream);
    }

    // Anything else would not be shown to the query's author as it should be, or would fail the whole search.
    @ParameterizedTest
    @MethodSource("allArguments")
    void mistakesAreReportedProperly(final Argument argument) {
        final List<String> problems = new ArrayList<>();
        for (final Input input : ALL) {
            for (final ExpressionContext context : List.of(LONDON, UNKNOWN_ZONE)) {
                for (final Result result : argument.evalEveryWay(context, input)) {
                    if (result.thrown != null && !result.isReportedWhenParsed()) {
                        problems.add(input + " gives " + result.thrown);
                    }
                }
            }
        }
        assertThat(problems).isEmpty();
    }

    @ParameterizedTest
    @MethodSource("fieldArguments")
    void resultIsTheSameHoweverTheValueIsGiven(final Argument argument) {
        final List<String> problems = new ArrayList<>();
        for (final Input input : ALL) {
            final boolean blankZoneIsUtc = NULLS.contains(input) && BLANK_ZONE_IS_UTC.contains(argument.template);
            if (!blankZoneIsUtc) {
                final List<String> results = argument.evalEveryWay(LONDON, input).stream()
                        .map(Result::getSummary)
                        .distinct()
                        .toList();
                if (results.size() > 1) {
                    problems.add(input + " gives " + results);
                }
            }
        }
        assertThat(problems).isEmpty();
    }

    @ParameterizedTest
    @MethodSource("constantArguments")
    void resultIsTheSameHoweverTheConstantIsGiven(final Argument argument) {
        final List<String> problems = new ArrayList<>();
        for (final Input input : ALL) {
            final List<String> results = argument.evalConstantEveryWay(LONDON, input).stream()
                    .map(Result::getSummary)
                    .distinct()
                    .toList();
            if (results.size() > 1 && input != WRITTEN_NULL) {
                problems.add(input + " gives " + results);
            }
        }
        assertThat(problems).isEmpty();
    }

    @ParameterizedTest
    @MethodSource("fieldArguments")
    void errorIsKept(final Argument argument) {
        assertThat(argument.evalField(LONDON, ERROR_FIELD).val).isEqualTo(ERROR_FIELD.val);
    }

    @ParameterizedTest
    @MethodSource("argumentsCheckedWhenParsed")
    void mistakeInAConstantIsReportedWhenParsed(final Argument argument) {
        final List<String> problems = new ArrayList<>();
        for (final Input input : ALL) {
            // A function is not known to be constant when the expression is parsed.
            if (input.source != null && !input.source.endsWith("()")) {
                final Result result = argument.evalSource(LONDON, input);
                if (result.val != null && result.val.type().isError()) {
                    problems.add(input + " gives " + result.val);
                }
            }
        }
        assertThat(problems).isEmpty();
    }

    @ParameterizedTest
    @MethodSource("numberArguments")
    void textIsRoundedToANumber(final Argument argument) {
        final List<String> problems = new ArrayList<>();
        final List<Input> inputs = new ArrayList<>(NUMBERS);
        inputs.add(text("1e300"));
        inputs.add(text("12345678901234567890.7"));
        for (final Input input : inputs) {
            final Val number = ValDouble.create(input.val.toDouble());
            final Val expected = argument.evalField(LONDON, field("number", number)).val;
            final Val val = argument.evalField(LONDON, input).val;
            if (!(val instanceof ValDouble) || !val.equals(expected)) {
                problems.add(input + " gives " + val + " not " + expected);
            }
        }
        assertThat(problems).isEmpty();
    }

    @ParameterizedTest
    @MethodSource("dateArguments")
    void datesAreReadTheSame(final Argument argument) {
        assertDates(argument, Stream.of(DURATIONS, PERIODS, ERRORS).flatMap(List::stream).toList());
    }

    @ParameterizedTest
    @MethodSource("olderDateArguments")
    void datesAreReadTheSameByOlderFunctions(final Argument argument) {
        final List<Input> notDates = Stream.of(DURATIONS, PERIODS, ERRORS).flatMap(List::stream).toList();
        assertDates(argument, notDates.stream()
                .filter(input -> !isText(input))
                .toList());

        // They have always given a null for this, so saved dashboards may have it.
        for (final Input input : notDates.stream().filter(TestDateFunctionInputs::isText).toList()) {
            assertThat(argument.evalField(LONDON, input).getSummary()).as(input.name).isEqualTo(NO_VALUE);
        }
    }

    @ParameterizedTest
    @MethodSource("dateArguments")
    void datesThatAreTooLargeAreErrors(final Argument argument) {
        final List<String> problems = new ArrayList<>();
        for (final Input input : TOO_LARGE) {
            for (final Result result : argument.evalEveryWay(LONDON, input)) {
                final String message = result.val == null
                        ? result.thrown.getMessage()
                        : result.val.toString();
                if (!result.getSummary().equals(ERROR) || !message.contains("is too large to be a date")) {
                    problems.add(input + " gives " + message);
                }
            }
        }
        assertThat(problems).isEmpty();
    }

    @ParameterizedTest
    @MethodSource("olderDateArguments")
    void textOfDatesThatAreTooLargeIsNullInOlderFunctions(final Argument argument) {
        for (final Input input : TOO_LARGE) {
            assertThat(argument.evalEveryWay(LONDON, input))
                    .as(input.name)
                    .extracting(Result::getSummary)
                    .containsOnly(NO_VALUE);
        }
    }

    @ParameterizedTest
    @MethodSource("durationArguments")
    void durationsAreReadTheSame(final Argument argument) {
        final List<String> problems = new ArrayList<>();
        for (final Input input : ALL) {
            // The same as it would be for the duration itself.
            final Val duration = new Argument("toDuration({})").evalField(LONDON, input).val;
            final String expected = argument.evalField(LONDON, field("duration", duration)).getSummary();
            final String actual = argument.evalField(LONDON, input).getSummary();
            if (!actual.equals(expected)) {
                problems.add(input + " gives " + actual + " not " + expected);
            }
        }
        assertThat(problems).isEmpty();
    }

    private static boolean isText(final Input input) {
        return input.val instanceof ValString;
    }

    @ParameterizedTest
    @MethodSource("dateOrDurationArguments")
    void datesAreReadTheSameByFunctionsThatAlsoRoundDurations(final Argument argument) {
        assertDates(argument, ERRORS.stream()
                .filter(input -> !input.name.equals("'1M 2h'"))
                .toList());
    }

    private static void assertDates(final Argument argument, final List<Input> notDates) {
        final List<String> problems = new ArrayList<>();
        for (final Input input : Stream.concat(DATES.stream(), NUMBERS.stream()).toList()) {
            // The same as it would be for the date itself.
            final Val date = new Argument("toDate({})").evalField(LONDON, input).val;
            final String expected = argument.evalField(LONDON, field("date", date)).getSummary();
            final String actual = argument.evalField(LONDON, input).getSummary();
            if (!actual.equals(expected) || actual.equals(ERROR)) {
                problems.add(input + " gives " + actual + " not " + expected);
            }
        }
        for (final Input input : NULLS) {
            final String actual = argument.evalField(LONDON, input).getSummary();
            if (!actual.equals(NO_VALUE)) {
                problems.add(input + " gives " + actual);
            }
        }
        for (final Input input : notDates) {
            final String actual = argument.evalField(LONDON, input).getSummary();
            if (!actual.equals(ERROR)) {
                problems.add(input + " gives " + actual);
            }
        }
        assertThat(problems).isEmpty();
    }

    private static ExpressionContext createContext(final String zone) {
        return ExpressionContext.builder()
                .dateTimeSettings(DateTimeSettings.builder()
                        .referenceTime(Instant.parse("2024-07-10T12:00:00Z").toEpochMilli())
                        .timeZone(UserTimeZone.fromId(zone))
                        .build())
                .build();
    }

    private static Input literal(final String source, final Val val) {
        return new Input(source, source, null, val);
    }

    private static Input text(final String text) {
        return new Input("'" + text + "'", "'" + text + "'", text, ValString.create(text));
    }

    private static Input field(final String name, final Val val) {
        return new Input(name, null, null, val);
    }

    private static Result eval(final ExpressionContext context,
                               final String input,
                               final String named,
                               final String param,
                               final Val field) {
        final FieldIndex fieldIndex = new FieldIndex();
        fieldIndex.create("field");
        final Expression expression;
        try {
            final Map<String, Expression> names = named == null
                    ? Map.of()
                    : Map.of("named", new ExpressionParser(new ParamFactory(Map.of()))
                            .parse(context, fieldIndex, named));
            expression = new ExpressionParser(new ParamFactory(names)).parse(context, fieldIndex, input);
        } catch (final ParseException | RuntimeException e) {
            return new Result(null, e);
        }
        try {
            expression.setStaticMappedValues(param == null
                    ? Map.of()
                    : Map.of("param", param));
            final ValueReferenceIndex valueReferenceIndex = new ValueReferenceIndex();
            expression.addValueReferences(valueReferenceIndex);
            final StoredValues storedValues = valueReferenceIndex.createStoredValues();
            final Generator generator = expression.createGenerator();
            generator.set(new Val[]{field}, storedValues);
            return new Result(generator.eval(storedValues, null), null);
        } catch (final RuntimeException e) {
            // Wrapped so that it can't be taken for one from the parser.
            return new Result(null, new IllegalStateException(e));
        }
    }


    // --------------------------------------------------------------------------------


    // A value to give a function. Only text can be a query parameter, and some values can only come from a field.
    private record Input(String name, String source, String text, Val val) {

        @Override
        public String toString() {
            return name;
        }
    }


    // --------------------------------------------------------------------------------


    // An argument of a function, which is where the braces are.
    record Argument(String template) {

        private String with(final String source) {
            return template.replace("{}", source);
        }

        private Result evalField(final ExpressionContext context, final Input input) {
            return eval(context, with("${field}"), null, null, input.val);
        }

        private Result evalSource(final ExpressionContext context, final Input input) {
            return eval(context, with(input.source), null, null, ValNull.INSTANCE);
        }

        private List<Result> evalConstantEveryWay(final ExpressionContext context, final Input input) {
            final List<Result> results = new ArrayList<>();
            if (input.source != null) {
                results.add(evalSource(context, input));
                results.add(eval(context, with("named"), input.source, null, ValNull.INSTANCE));
            }
            if (input.text != null) {
                results.add(eval(context, with("param('param')"), null, input.text, ValNull.INSTANCE));
            }
            return results;
        }

        private List<Result> evalEveryWay(final ExpressionContext context, final Input input) {
            final List<Result> results = evalConstantEveryWay(context, input);
            results.add(evalField(context, input));
            return results;
        }

        @Override
        public String toString() {
            return template;
        }
    }


    // --------------------------------------------------------------------------------


    private record Result(Val val, Exception thrown) {

        private boolean isReportedWhenParsed() {
            return thrown instanceof TokenException;
        }

        // The same for any error, as the wording and when it is reported can differ.
        private String getSummary() {
            if (thrown != null || val.type().isError()) {
                return ERROR;
            }
            return val.type().isNull()
                    ? NO_VALUE
                    : val.type().getName() + " " + val;
        }
    }
}
