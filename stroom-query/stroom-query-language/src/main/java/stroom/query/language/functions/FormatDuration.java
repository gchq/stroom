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
        name = FormatDuration.NAME,
        commonCategory = FunctionCategory.DATE,
        commonSubCategories = "Duration",
        commonReturnType = ValString.class,
        commonReturnDescription = "A duration formatted as a string.",
        signatures = {
                @FunctionSignature(
                        description = "Formats the supplied duration as a string in its largest unit, e.g. 1.5h.",
                        args = @FunctionArg(
                                name = "value",
                                argType = Val.class,
                                description = "The duration, a number of milliseconds or text that parseDuration " +
                                              "can read.")),
                @FunctionSignature(
                        description = "Formats the supplied duration as a string using the supplied pattern.",
                        args = {
                                @FunctionArg(
                                        name = "value",
                                        argType = Val.class,
                                        description = "The duration, a number of milliseconds or text that " +
                                                      "parseDuration can read."),
                                @FunctionArg(
                                        name = "pattern",
                                        argType = ValString.class,
                                        description = "The pattern, e.g. 'd.hh:mm:ss', of d (days), h or hh (hours), " +
                                                      "m or mm (minutes), s or ss (seconds), f, ff or fff " +
                                                      "(fractions of a second) and other text, whose letters must " +
                                                      "be in single quotes. Each unit has what the larger ones " +
                                                      "in the pattern leave, so 2 days and 90 minutes is 49:30 " +
                                                      "with 'hh:mm'.")})})
class FormatDuration extends AbstractFunction {

    static final String NAME = "formatDuration";

    // Null to format in the largest unit.
    private DurationPattern pattern;
    private Generator gen;
    private Function function;
    private boolean hasAggregate;

    public FormatDuration(final String name) {
        super(name, 1, 2);
    }

    @Override
    public void setParams(final Param[] params) throws ParseException {
        super.setParams(params);

        if (params.length > 1) {
            try {
                pattern = DurationPattern.parse(ParamParseUtil.parseStringParam(params, 1, name));
            } catch (final IllegalArgumentException e) {
                throw new ParseException(e.getMessage(), 0);
            }
        }

        final Param param = params[0];
        if (param instanceof Function) {
            function = (Function) param;
            hasAggregate = function.hasAggregate();
        } else {
            gen = new StaticValueFunction(format((Val) param, pattern)).createGenerator();
        }
    }

    private static Val format(final Val val, final DurationPattern pattern) {
        return pattern == null
                ? ValDurationUtil.formatDuration(val)
                : ValDurationUtil.formatDuration(val, pattern);
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
            return format(val, pattern);
        }
    }
}
