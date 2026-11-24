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
import stroom.util.shared.NullSafe;

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
        commonReturnDescription = "The date as number of milliseconds since the epoch.",
        signatures = {
                @FunctionSignature(
                        args = @FunctionArg(
                                name = "dateString",
                                argType = ValString.class,
                                description = "The date string, e.g. '2014 02 22'"),
                        description = "Parse dateString as an ISO 8601 date and time, " +
                                      "e.g. '2014-02-22T12:12:12.888Z'. If dateString has no offset then it " +
                                      "is in the time zone of the query."),
                @FunctionSignature(
                        args = {
                                @FunctionArg(
                                        name = "dateString",
                                        argType = ValString.class,
                                        description = "The date string, e.g. '2014 02 22'"),
                                @FunctionArg(
                                        name = "pattern",
                                        argType = ValString.class,
                                        description = "The format pattern, e.g. 'yyyy MM dd'")},
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
                                        description = "The format pattern, e.g. 'yyyy MM dd'"),

                                @FunctionArg(
                                        name = "timezone",
                                        argType = ValString.class,
                                        description = "The timezone, e.g. '+0400', which can be a field."),
                        },
                        description = "Parse dateString using the supplied date format pattern. If dateString " +
                                      "has no offset then it is in the supplied timezone.")})
class ParseDate extends AbstractFunction {

    static final String NAME = "parseDate";

    private final ExpressionContext expressionContext;
    private DateTimeFormatter formatter = DateUtil.DEFAULT_ISO_PARSER;
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

        if (params.length >= 2) {
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

        } else if (zoneFunction != null && param instanceof ValString && !NullSafe.isBlankString(param.toString())) {
            // The time zone may be different in each row.
            function = new StaticValueFunction((Val) param);
        } else if (param instanceof ValNull) {
            // This is consistent with ParseDuration
            gen = new StaticValueFunction(ValNull.INSTANCE).createGenerator();
        } else if (param instanceof ValString) {
            final String string = param.toString();
            final Val val;
            if (!NullSafe.isBlankString(string)) {
                val = ValDate.create(parseConstant(string));
            } else {
                val = ValNull.INSTANCE;
            }
            gen = new StaticValueFunction(val).createGenerator();

        } else {
            final Long millis = ((Val) param).toLong();
            if (millis == null) {
                throw new ParseException("Unable to convert first argument of '" + name + "' function to long", 0);
            }
            gen = new StaticValueFunction(ValDate.create(millis)).createGenerator();
        }
    }

    private long parseConstant(final String string) throws ParseException {
        try {
            return DateUtil.parse(string, formatter, zone);
        } catch (final DateTimeException e) {
            throw new ParseException(e.getMessage(), 0);
        }
    }

    @Override
    public Generator createGenerator() {
        if (gen != null) {
            return gen;
        }

        final Generator childGenerator = function.createGenerator();
        return zoneFunction == null
                ? new Gen(new Generator[]{childGenerator}, formatter, zone)
                : new Gen(new Generator[]{childGenerator, zoneFunction.createGenerator()}, formatter, null);
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
        private final Supplier<ZoneId> zone;

        Gen(final Generator[] childGenerators, final DateTimeFormatter formatter, final Supplier<ZoneId> zone) {
            super(childGenerators);
            this.formatter = formatter;
            this.zone = zone;
        }

        @Override
        public Val eval(final StoredValues storedValues, final Supplier<ChildData> childDataSupplier) {
            final Val val = childGenerators[0].eval(storedValues, childDataSupplier);
            if (!val.type().isValue()) {
                return val;
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
                return ValDate.create(DateUtil.parse(
                        val.toString(), formatter, rowZone));
            } catch (final RuntimeException e) {
                return ValErr.create(e.getMessage());
            }
        }
    }
}
