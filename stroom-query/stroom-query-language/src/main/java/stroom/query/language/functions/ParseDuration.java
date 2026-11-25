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
                                description = "The duration as a string."))
        })
class ParseDuration extends AbstractFunction {

    static final String NAME = "parseDuration";

    private Generator gen;
    private Function function;
    private boolean hasAggregate;

    public ParseDuration(final String name) {
        super(name, 1, 1);
    }

    @Override
    public void setParams(final Param[] params) throws ParseException {
        super.setParams(params);

        final Param param = params[0];
        if (param instanceof Function) {
            function = (Function) param;
            hasAggregate = function.hasAggregate();
        } else {
            gen = new StaticValueFunction(ValDurationUtil.toDuration((Val) param)).createGenerator();
        }
    }

    @Override
    public Generator createGenerator() {
        if (gen != null) {
            return gen;
        }

        final Generator childGenerator = function.createGenerator();
        return new Gen(childGenerator);
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

        Gen(final Generator childGenerator) {
            super(childGenerator);
        }

        @Override
        public void set(final Val[] values, final StoredValues storedValues) {
            childGenerator.set(values, storedValues);
        }

        @Override
        public Val eval(final StoredValues storedValues, final Supplier<ChildData> childDataSupplier) {
            final Val val = childGenerator.eval(storedValues, childDataSupplier);
            return ValDurationUtil.toDuration(val);
        }
    }
}
