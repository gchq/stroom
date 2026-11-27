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

import stroom.query.api.DurationPattern;
import stroom.query.language.functions.ref.StoredValues;

import java.text.ParseException;
import java.util.function.Supplier;

@SuppressWarnings("unused") //Used by FunctionFactory
@FunctionDef(
        name = ParseDuration.NAME,
        commonCategory = FunctionCategory.DATE,
        commonSubCategories = "Duration",
        commonReturnType = ValDuration.class,
        commonReturnDescription = "The parsed duration.",
        signatures = {
                @FunctionSignature(
                        description = "Parses the supplied value as a duration. It can be numbers with the units w, " +
                                "d, h, m, s, ms, us and ns, e.g. '1d 2h' or '1.5h', ISO 8601, e.g. 'PT1H30M', or a " +
                                "number of milliseconds. Units must be in lower case, as 1M is a month. A day is 24 " +
                                "hours, and any part of a millisecond is dropped. Use parsePeriod for months and " +
                                "years.",
                        args = @FunctionArg(
                                name = "value",
                                argType = ValString.class,
                                description = "The duration as a string.")),
                @FunctionSignature(
                        description = "Parses the supplied value as a duration in the supplied pattern, such as " +
                                "'26:03:04' in 'hh:mm:ss'.",
                        args = {
                                @FunctionArg(
                                        name = "value",
                                        argType = ValString.class,
                                        description = "The duration as a string, which can start with a sign."),
                                @FunctionArg(
                                        name = "pattern",
                                        argType = ValString.class,
                                        description = "The pattern, e.g. 'd.hh:mm:ss', of d (days), h or hh (hours), " +
                                                      "m or mm (minutes), s or ss (seconds), f, ff or fff " +
                                                      "(fractions of a second) and other text, whose letters must " +
                                                      "be in single quotes. Only the largest unit in the pattern " +
                                                      "can be more than a clock shows, so with 'hh:mm' 49:30 is " +
                                                      "49 hours and 30 minutes, but 1:90 is an error. A fraction " +
                                                      "after seconds can have any number of digits, unless it is " +
                                                      "written together with them, as in 'ssfff'. A number can " +
                                                      "only be read with a pattern of one unit, such as 'm'.")})
        })
class ParseDuration extends AbstractFunction {

    static final String NAME = "parseDuration";

    // Null if text can be any duration.
    private DurationPattern pattern;
    private Generator gen;
    private Function function;
    private boolean hasAggregate;

    public ParseDuration(final String name) {
        super(name, 1, 2);
    }

    @Override
    public void setParams(final Param[] params) throws ParseException {
        super.setParams(params);

        if (params.length > 1) {
            try {
                pattern = DurationPattern.parse(ParamParseUtil.parseStringParam(params, 1, name));
                pattern.checkCanRead();
            } catch (final IllegalArgumentException e) {
                throw new ParseException(e.getMessage(), 0);
            }
        }

        final Param param = params[0];
        if (param instanceof Function) {
            function = (Function) param;
            hasAggregate = function.hasAggregate();
        } else {
            gen = new StaticValueFunction(parse((Val) param, pattern)).createGenerator();
        }
    }

    private static Val parse(final Val val, final DurationPattern pattern) {
        return pattern == null
                ? ValDurationUtil.toDuration(val)
                : ValDurationUtil.toDuration(val, pattern);
    }

    @Override
    public Generator createGenerator() {
        if (gen != null) {
            return gen;
        }

        final Generator childGenerator = function.createGenerator();
        return new Gen(childGenerator, pattern);
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


    private static final class Gen extends AbstractSingleChildGenerator {

        private final DurationPattern pattern;

        Gen(final Generator childGenerator, final DurationPattern pattern) {
            super(childGenerator);
            this.pattern = pattern;
        }

        @Override
        public void set(final Val[] values, final StoredValues storedValues) {
            childGenerator.set(values, storedValues);
        }

        @Override
        public Val eval(final StoredValues storedValues, final Supplier<ChildData> childDataSupplier) {
            final Val val = childGenerator.eval(storedValues, childDataSupplier);
            return parse(val, pattern);
        }
    }
}
