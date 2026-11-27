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
                                description = "The date, a number of milliseconds since the epoch (1 Jan 1970), or " +
                                        "text of either."),
                        returnDescription = "A date string in the default date format pattern.",
                        description = "Convert the date to a string using the default date format pattern (" +
                                DateUtil.DEFAULT_PATTERN + ") and the time zone of the query."),
                @FunctionSignature(
                        args = {
                                @FunctionArg(
                                        name = "date",
                                        argType = ValLong.class,
                                        description = "The date, a number of milliseconds since the epoch (1 Jan " +
                                                "1970), or text of either."),
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
                                        description = "The date, a number of milliseconds since the epoch (1 Jan " +
                                                "1970), or text of either."),
                                @FunctionArg(
                                        name = "pattern",
                                        argType = ValString.class,
                                        description = "The format pattern, e.g. 'yyyy MM dd'"),

                                @FunctionArg(
                                        name = "timezone",
                                        argType = ValString.class,
                                        description = "The time zone, e.g. 'Europe/London' or '+0400', which can be " +
                                                "a field. Short names such as 'BST' and 'EST' are not accepted, as " +
                                                "they mean different things to different people.")},
                        returnDescription = "A date string in the supplied date format pattern.",
                        description = "Convert the date to a string using the supplied date format " +
                                "pattern and timezone.")})
class FormatDate extends AbstractFunction {

    static final String NAME = "formatDate";

    private final ExpressionContext expressionContext;
    private DateTimeFormatter formatter = DateUtil.DEFAULT_FORMATTER;
    private ZoneId zoneId;
    // The time zone of text that has no offset.
    private Supplier<ZoneId> textZone = () -> zoneId;
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

        try {
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
                textZone = new QueryZone(expressionContext);
            } else {
                zoneId = UserTimeZoneUtil.getZoneId(expressionContext);
            }

            final Param param = params[0];
            if (param instanceof Function) {
                function = (Function) param;
                hasAggregate = function.hasAggregate();
            } else {
                // Report a mistake now, rather than as an error in every row.
                if (zoneFunction == null) {
                    gen = new StaticValueFunction(format((Val) param, formatter, zoneId, textZone)).createGenerator();
                } else {
                    format((Val) param, formatter, ZoneOffset.UTC, () -> ZoneOffset.UTC);
                    // The time zone may be different in each row.
                    function = new StaticValueFunction((Val) param);
                }
            }
        } catch (final DateTimeException | IllegalArgumentException | ArithmeticException e) {
            throw new ParseException(e.getMessage(), 0);
        }
    }

    private static Val format(final Val val,
                              final DateTimeFormatter formatter,
                              final ZoneId zoneId,
                              final Supplier<ZoneId> textZone) {
        return DateReader.isBlank(val)
                ? ValNull.INSTANCE
                : ValString.create(DateUtil.format(
                        DateReader.readMillis(val, textZone), formatter, zoneId));
    }

    @Override
    public Generator createGenerator() {
        if (gen != null) {
            return gen;
        }

        final Generator childGenerator = function.createGenerator();
        return zoneFunction == null
                ? new Gen(new Generator[]{childGenerator}, formatter, zoneId, textZone)
                : new Gen(new Generator[]{childGenerator, zoneFunction.createGenerator()}, formatter, null, null);
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
        private final Supplier<ZoneId> textZone;

        Gen(final Generator[] childGenerators,
            final DateTimeFormatter formatter,
            final ZoneId zoneId,
            final Supplier<ZoneId> textZone) {
            super(childGenerators);
            this.formatter = formatter;
            this.zoneId = zoneId;
            this.textZone = textZone;
        }

        @Override
        public Val eval(final StoredValues storedValues, final Supplier<ChildData> childDataSupplier) {
            final Val val = childGenerators[0].eval(storedValues, childDataSupplier);
            if (!val.type().isValue()) {
                return val;
            } else if (DateReader.isBlank(val)) {
                return ValNull.INSTANCE;
            }

            try {
                if (childGenerators.length == 1) {
                    return format(val, formatter, zoneId, textZone);
                }
                final Val zone = childGenerators[1].eval(storedValues, childDataSupplier);
                if (zone.type().isError()) {
                    return zone;
                }
                final ZoneId rowZoneId = UserTimeZoneUtil.getZoneId(zone);
                return format(val, formatter, rowZoneId, () -> rowZoneId);
            } catch (final RuntimeException e) {
                return ValErr.create(e.getMessage());
            }
        }
    }
}
