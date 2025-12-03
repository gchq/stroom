/*
 * Copyright 2016-2025 Crown Copyright
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
                                DateUtil.DEFAULT_PATTERN + ") and default timezone (UTC)."),
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
                                "the default timezone (UTC)."),
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
                                        description = "The timezone, e.g. '+0400'")},
                        returnDescription = "A date string in the supplied date format pattern.",
                        description = "Convert the date to a string using the supplied date format" +
                                "pattern and timezone.")})
class FormatDate extends AbstractFunction {

    static final String NAME = "formatDate";
    private String pattern = DateUtil.DEFAULT_PATTERN;
    private String timeZone;

    private Generator gen;
    private Function function;
    private boolean hasAggregate;

    public FormatDate(final String name) {
        super(name, 1, 3);
    }

    @Override
    public void setParams(final Param[] params) throws ParseException {
        super.setParams(params);

        DateTimeFormatter formatter = DateUtil.DEFAULT_FORMATTER;
        ZoneId zoneId = ZoneOffset.UTC;

        if (params.length >= 2) {
            pattern = ParamParseUtil.parseStringParam(params, 1, name);
            formatter = FormatterCache.getFormatter(pattern, Mode.FORMAT);
        }
        if (params.length >= 3) {
            timeZone = ParamParseUtil.parseStringParam(params, 2, name);
            zoneId = FormatterCache.getZoneId(timeZone);
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

            final String string = DateUtil.format(millis, formatter, zoneId);
            gen = new StaticValueFunction(ValString.create(string)).createGenerator();
        }
    }

    @Override
    public Generator createGenerator() {
        if (gen != null) {
            return gen;
        }

        final Generator childGenerator = function.createGenerator();
        return new Gen(childGenerator, pattern, timeZone);
    }

    @Override
    public boolean hasAggregate() {
        return hasAggregate;
    }

    @Override
    public boolean requiresChildData() {
        if (function != null) {
            return function.requiresChildData();
        }
        return super.requiresChildData();
    }


    // --------------------------------------------------------------------------------


    private static class Gen extends AbstractSingleChildGenerator {


        private final String pattern;
        private final String timeZone;

        Gen(final Generator childGenerator, final String pattern, final String timeZone) {
            super(childGenerator);
            this.pattern = pattern;
            this.timeZone = timeZone;
        }

        @Override
        public void set(final Val[] values, final StoredValues storedValues) {
            childGenerator.set(values, storedValues);
        }

        @Override
        public Val eval(final StoredValues storedValues, final Supplier<ChildData> childDataSupplier) {
            final Val val = childGenerator.eval(storedValues, childDataSupplier);
            if (!val.type().isValue()) {
                return val;
            }
            final Long millis = val.toLong();
            if (millis == null) {
                return ValErr.create("Unable to convert argument to long");
            }

            try {
                return ValString.create(FormatterCache.format(millis, pattern, timeZone));
            } catch (final RuntimeException e) {
                return ValErr.create(e.getMessage());
            }
        }
    }
}
