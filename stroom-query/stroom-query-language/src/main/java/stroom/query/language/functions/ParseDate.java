/*
 * Copyright 2020 Crown Copyright
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

import stroom.query.language.functions.FormatterCache.Mode;
import stroom.query.language.functions.ref.StoredValues;

import java.text.ParseException;
import java.time.DateTimeException;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.function.Supplier;

@SuppressWarnings("unused") //Used by FunctionFactory
@FunctionDef(
        name = ParseDate.NAME,
        commonCategory = FunctionCategory.DATE,
        commonReturnType = ValDate.class,
        commonReturnDescription = "The date and time.",
        signatures = {
                @FunctionSignature(
                        args = @FunctionArg(
                                name = "dateString",
                                argType = ValString.class,
                                description = "The date string, e.g. '2014 02 22'"),
                        description = "Parse dateString as an ISO 8601 date and time, e.g. " +
                                "'2014-02-22T12:12:12.888Z'. If dateString has no offset then it is in the time " +
                                "zone of the query. Put dates in quotes, as 2014-02-22 without them is a " +
                                "subtraction. A number is milliseconds since the epoch."),
                @FunctionSignature(
                        args = {
                                @FunctionArg(
                                        name = "dateString",
                                        argType = ValString.class,
                                        description = "The date string, e.g. '2014 02 22'"),
                                @FunctionArg(
                                        name = "pattern",
                                        argType = ValString.class,
                                        description = "The format pattern, e.g. 'yyyy MM dd'. Parts in square " +
                                                "brackets are optional, e.g. 'yyyy-MM-dd[ HH:mm]'.")},
                        description = "Parse dateString using the supplied date format pattern. If dateString " +
                                      "has no offset then it is in the time zone of the query, or UTC if the " +
                                      "pattern ends with a quoted 'Z', 'UTC' or 'GMT'."),
                @FunctionSignature(
                        args = {
                                @FunctionArg(
                                        name = "dateString",
                                        argType = ValString.class,
                                        description = "The date string, e.g. '2014 02 22'"),
                                @FunctionArg(
                                        name = "pattern",
                                        argType = ValString.class,
                                        description = "The format pattern, e.g. 'yyyy MM dd'. Parts in square " +
                                                "brackets are optional, e.g. 'yyyy-MM-dd[ HH:mm]'."),

                                @FunctionArg(
                                        name = "timezone",
                                        argType = ValString.class,
                                        description = "The time zone, e.g. 'Europe/London' or '+0400', which can be " +
                                                "a field. Short names such as 'BST' and 'EST' are not accepted, as " +
                                                "they mean different things to different people."),
                        },
                        description = "Parse dateString using the supplied date format pattern. If dateString " +
                                      "has no offset then it is in the supplied timezone.")})
class ParseDate extends AbstractFunction {

    static final String NAME = "parseDate";

    private final ExpressionContext expressionContext;
    private DateTimeFormatter formatter = DateUtil.DEFAULT_ISO_PARSER;
    private boolean hasPattern;
    private Supplier<ZoneId> zone;
    private Function zoneFunction;

    private Generator gen;
    private Function function;
    private boolean hasAggregate;

    public ParseDate(final ExpressionContext expressionContext, final String name) {
        super(name, 1, 3);
        this.expressionContext = expressionContext;
    }

    @Override
    public void setParams(final Param[] params) throws ParseException {
        super.setParams(params);

        try {
            if (params.length >= 2) {
                hasPattern = true;
                final String pattern = ParamParseUtil.parseStringParam(params, 1, name);
                formatter = FormatterCache.getFormatter(pattern, Mode.PARSE);
            }
            if (params.length >= 3 && params[2] instanceof final Function func) {
                zoneFunction = func;
            } else if (params.length >= 3) {
                final ZoneId zoneId = FormatterCache.getZoneId(ParamParseUtil.parseStringParam(params, 2, name));
                zone = () -> zoneId;
            } else if (params.length == 2 && FormatterCache.isMarkedUtc(params[1].toString())) {
                // The text says that it is in UTC, so the time must be.
                zone = () -> ZoneOffset.UTC;
            } else {
                zone = new QueryZone(expressionContext);
            }

            final Param param = params[0];
            if (param instanceof Function) {
                function = (Function) param;
                hasAggregate = function.hasAggregate();
            } else {
                // Report a mistake now, rather than as an error in every row.
                final Val val = parse((Val) param, formatter, hasPattern, zoneFunction == null
                        ? zone
                        : () -> ZoneOffset.UTC);
                if (zoneFunction == null) {
                    gen = new StaticValueFunction(val).createGenerator();
                } else {
                    // The time zone may be different in each row.
                    function = new StaticValueFunction((Val) param);
                }
            }
        } catch (final DateTimeException | IllegalArgumentException | ArithmeticException e) {
            throw new ParseException(e.getMessage(), 0);
        }
    }

    // Reads a value as a date. Without a pattern it can be a date or a number of milliseconds. A pattern is of text.
    private static Val parse(final Val val,
                             final DateTimeFormatter formatter,
                             final boolean hasPattern,
                             final Supplier<ZoneId> zone) {
        if (!val.type().isValue()) {
            return val;
        }
        final String text = val.toString();
        if (val instanceof ValDuration || val instanceof ValPeriod || text == null) {
            throw new DateTimeException("A " + val.type().getName() + " is not a date");
        } else if (text.isBlank()) {
            return ValNull.INSTANCE;
        } else if (!hasPattern && val instanceof ValNumber) {
            return ValDate.create(DateReader.readMillis(val, zone));
        }
        // A pattern may have spaces at its start or end.
        return ValDate.create(DateUtil.parse(hasPattern
                ? text
                : text.trim(), formatter, zone));
    }

    @Override
    public Generator createGenerator() {
        if (gen != null) {
            return gen;
        }

        final Generator childGenerator = function.createGenerator();
        return zoneFunction == null
                ? new Gen(new Generator[]{childGenerator}, formatter, hasPattern, zone)
                : new Gen(new Generator[]{childGenerator, zoneFunction.createGenerator()},
                        formatter,
                        hasPattern,
                        null);
    }

    @Override
    public boolean hasAggregate() {
        return hasAggregate || (zoneFunction != null && zoneFunction.hasAggregate());
    }

    @Override
    public boolean requiresChildData() {
        return (function != null && function.requiresChildData())
               || (zoneFunction != null && zoneFunction.requiresChildData());
    }

    private static final class Gen extends AbstractManyChildGenerator {

        private final DateTimeFormatter formatter;
        private final boolean hasPattern;
        private final Supplier<ZoneId> zone;

        Gen(final Generator[] childGenerators,
            final DateTimeFormatter formatter,
            final boolean hasPattern,
            final Supplier<ZoneId> zone) {
            super(childGenerators);
            this.formatter = formatter;
            this.hasPattern = hasPattern;
            this.zone = zone;
        }

        @Override
        public Val eval(final StoredValues storedValues, final Supplier<ChildData> childDataSupplier) {
            final Val val = childGenerators[0].eval(storedValues, childDataSupplier);
            if (!val.type().isValue()) {
                return val;
            } else if (DateReader.isBlank(val)) {
                return ValNull.INSTANCE;
            }

            Supplier<ZoneId> rowZone = zone;
            if (childGenerators.length > 1) {
                final Val zoneVal = childGenerators[1].eval(storedValues, childDataSupplier);
                if (zoneVal.type().isError()) {
                    return zoneVal;
                }
                try {
                    final ZoneId rowZoneId = UserTimeZoneUtil.getZoneId(zoneVal);
                    rowZone = () -> rowZoneId;
                } catch (final DateTimeException e) {
                    return ValErr.create(e.getMessage());
                }
            }

            try {
                return parse(val, formatter, hasPattern, rowZone);
            } catch (final RuntimeException e) {
                return ValErr.create(e.getMessage());
            }
        }
    }
}
