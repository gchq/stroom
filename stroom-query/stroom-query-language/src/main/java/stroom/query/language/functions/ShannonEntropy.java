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

import java.text.ParseException;
import java.util.function.Supplier;

@SuppressWarnings("unused") //Used by FunctionFactory
@FunctionDef(
        name = ShannonEntropy.NAME,
        commonCategory = FunctionCategory.STRING,
        commonReturnType = ValDouble.class,
        commonReturnDescription = "The Shannon entropy of the string in bits per character, or null if the " +
                                  "input is null.",
        signatures = @FunctionSignature(
                description = "Return the Shannon entropy of the provided string, i.e. how unpredictable its " +
                              "characters are. Useful for spotting machine-generated values such as random " +
                              "domain names, encoded command lines or tokens. " +
                              "The result ranges from 0, for one repeated character, up to log2 of the " +
                              "string's length, so short strings cannot score highly, e.g. a 12-character " +
                              "string scores at most 3.585. Random-looking strings typically score about 3.5 " +
                              "or above, e.g. `shannonEntropy(${DnsQuery}) > 3.5`. " +
                              "Characters are counted as Unicode code points and are case-sensitive; use " +
                              "`shannonEntropy(lowerCase(${field}))` to ignore case. " +
                              "Numbers and dates are measured on their string form.",
                args = {
                        @FunctionArg(
                                name = "input",
                                description = "The string to measure.",
                                argType = ValString.class)}))
class ShannonEntropy extends AbstractFunction {

    static final String NAME = "shannonEntropy";
    private Generator gen;
    private Function function;
    private boolean hasAggregate;

    /// Creates the function. Called reflectively by [FunctionFactory].
    ///
    /// @param name The name the function was called by in the expression.
    public ShannonEntropy(final String name) {
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
            // Static input so compute it once. Null and error values are passed through as they are.
            final Val val = (Val) param;
            final Val result = val.type().isValue()
                    ? ValDouble.create(ShannonEntropyCalculator.calculate(val.toString()))
                    : val;
            gen = new StaticValueFunction(result).createGenerator();
            hasAggregate = false;
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
            if (!val.type().isValue()) {
                return val;
            }

            return ValDouble.create(ShannonEntropyCalculator.calculate(val.toString()));
        }
    }
}
