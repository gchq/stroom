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
        name = FormatDate.NAME,
        commonCategory = FunctionCategory.DATE,
        commonReturnType = ValString.class,
        signatures = {
                @FunctionSignature(
                        args = @FunctionArg(
                                name = "date",
                                argType = ValLong.class,
                                description = "The date as milliseconds since the epoch (1 Jan 1970)"),
                        returnDescription = "A date string in the default date format pattern.",
                        description = "Convert the date to a string using the default date format pattern (" +
                                DateUtil.DEFAULT_PATTERN + ") and the time zone of the query."),
                @FunctionSignature(
                        args = {
                                @FunctionArg(
                                        name = "date",
                                        argType = ValLong.class,
                                        description = "The date as milliseconds since the epoch (1 Jan 1970)"),
                                @FunctionArg(
                                        name = "pattern",
                                        argType = ValString.class,
                                        description = "The format pattern, e.g. 'yyyy MM dd'")},
                        returnDescription = "A date string in the supplied date format pattern.",
                        description = "Convert the date to a string using the supplied date format pattern and " +
                                "the time zone of the query, or UTC if the pattern ends with a quoted 'Z', 'UTC' or " +
                                "'GMT'."),
                @FunctionSignature(
                        args = {
                                @FunctionArg(
                                        name = "date",
                                        argType = ValLong.class,
                                        description = "The date as milliseconds since the epoch (1 Jan 1970)"),
                                @FunctionArg(
                                        name = "pattern",
                                        argType = ValString.class,
                                        description = "The format pattern, e.g. 'yyyy MM dd'"),

                                @FunctionArg(
                                        name = "timezone",
                                        argType = ValString.class,
                                        description = "The timezone, e.g. '+0400', which can be a field.")},
                        returnDescription = "A date string in the supplied date format pattern.",
                        description = "Convert the date to a string using the supplied date format " +
                                "pattern and timezone.")})
class FormatDate extends AbstractFunction {

    static final String NAME = "formatDate";

    private final ExpressionContext expressionContext;
    private DateTimeFormatter formatter = DateUtil.DEFAULT_FORMATTER;
    private ZoneId zoneId;
    private Function zoneFunction;

    private Generator gen;
    private Function function;
    private boolean hasAggregate;

    public FormatDate(final ExpressionContext expressionContext, final String name) {
        super(name, 1, 3);
        this.expressionContext = expressionContext;
    }

    @Override
    public void setParams(final Param[] params) throws ParseException {
        super.setParams(params);

        if (params.length >= 2) {
            final String pattern = ParamParseUtil.parseStringParam(params, 1, name);
            formatter = FormatterCache.getFormatter(pattern, Mode.FORMAT);
        }
        if (params.length >= 3 && params[2] instanceof final Function func) {
            zoneFunction = func;
        } else if (params.length >= 3) {
            zoneId = FormatterCache.getZoneId(ParamParseUtil.parseStringParam(params, 2, name));
        } else if (params.length == 2 && FormatterCache.isMarkedUtc(params[1].toString())) {
            // The text says that it is in UTC, so the time must be.
            zoneId = ZoneOffset.UTC;
        } else {
            try {
                zoneId = UserTimeZoneUtil.getZoneId(expressionContext);
            } catch (final DateTimeException e) {
                throw new ParseException(e.getMessage(), 0);
            }
        }

        final Param param = params[0];
        if (param instanceof Function) {
            function = (Function) param;
            hasAggregate = function.hasAggregate();

        } else {
            final Long millis = ((Val) param).toLong();
            if (millis == null) {
                throw new ParseException("Unable to convert first argument of '" + name + "' function to long", 0);
            }

            if (zoneFunction != null) {
                // The time zone may be different in each row.
                function = new StaticValueFunction((Val) param);
            } else {
                final String string = DateUtil.format(millis, formatter, zoneId);
                gen = new StaticValueFunction(ValString.create(string)).createGenerator();
            }
        }
    }

    @Override
    public Generator createGenerator() {
        if (gen != null) {
            return gen;
        }

        final Generator childGenerator = function.createGenerator();
        return zoneFunction == null
                ? new Gen(new Generator[]{childGenerator}, formatter, zoneId)
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


    // --------------------------------------------------------------------------------


    private static class Gen extends AbstractManyChildGenerator {


        private final DateTimeFormatter formatter;
        private final ZoneId zoneId;

        Gen(final Generator[] childGenerators, final DateTimeFormatter formatter, final ZoneId zoneId) {
            super(childGenerators);
            this.formatter = formatter;
            this.zoneId = zoneId;
        }

        @Override
        public Val eval(final StoredValues storedValues, final Supplier<ChildData> childDataSupplier) {
            final Val val = childGenerators[0].eval(storedValues, childDataSupplier);
            if (!val.type().isValue()) {
                return val;
            }

            ZoneId rowZoneId = zoneId;
            if (childGenerators.length > 1) {
                final Val zone = childGenerators[1].eval(storedValues, childDataSupplier);
                if (zone.type().isError()) {
                    return zone;
                }
                try {
                    rowZoneId = UserTimeZoneUtil.getZoneId(zone);
                } catch (final DateTimeException e) {
                    return ValErr.create(e.getMessage());
                }
            }
            final Long millis = val.toLong();
            if (millis == null) {
                return ValErr.create("Unable to convert argument to long");
            }

            try {
                return ValString.create(DateUtil.format(
                        millis, formatter, rowZoneId));
            } catch (final RuntimeException e) {
                return ValErr.create(e.getMessage());
            }
        }
    }
}
