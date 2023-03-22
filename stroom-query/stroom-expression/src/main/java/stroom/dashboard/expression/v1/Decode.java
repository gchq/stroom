/*
 * Copyright 2017 Crown Copyright
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

package stroom.dashboard.expression.v1;

import java.text.ParseException;
import java.util.function.Supplier;
import java.util.regex.Pattern;

@SuppressWarnings("unused") //Used by FunctionFactory
@FunctionDef(
        name = Decode.NAME,
        commonCategory = FunctionCategory.STRING,
        commonReturnType = ValString.class,
        commonReturnDescription = "One of the result arguments, if matched, or the value of the otherwise " +
                "argument if no match is found.",
        signatures = @FunctionSignature(
                description = "Similar to a switch/case statement. The arguments are split into 3 parts: " +
                        "the input value to test, pairs of regex patterns with their respective output values " +
                        "and a default result if no matches are found. It must always have an even number " +
                        "of arguments and can have any number of pattern/result pairs.",
                args = {
                        @FunctionArg(
                                name = "input",
                                description = "The input string to test the regex patterns against.",
                                argType = ValString.class),
                        @FunctionArg(
                                name = "pattern1",
                                description = "A regex pattern to test against the input string.",
                                argType = ValString.class),
                        @FunctionArg(
                                name = "result1",
                                description = "The result of the function if test1 matches.",
                                argType = ValString.class),
                        @FunctionArg(
                                name = "patternN",
                                description = "A regex pattern to test against the input string.",
                                argType = ValString.class),
                        @FunctionArg(
                                name = "resultN",
                                description = "The result of the function if testN matches.",
                                argType = ValString.class),
                        @FunctionArg(
                                name = "otherwise",
                                description = "The result of the function if none of the test arguments match.",
                                argType = ValString.class)}))
class Decode extends AbstractManyChildFunction {

    static final String NAME = "decode";
    private Generator gen;
    private boolean simple;

    public Decode(final String name) {
        super(name, 4, Integer.MAX_VALUE);
    }

    @Override
    public void setParams(final Param[] params) throws ParseException {
        super.setParams(params);

        if (params.length % 2 == 1) {
            throw new ParseException("Expected to get an even number of arguments of '" + name + "' function", 0);
        }

        // See if this is a static computation.
        simple = true;
        for (Param param : params) {
            if (!(param instanceof Val)) {
                simple = false;
                break;
            }
        }

        if (simple) {
            // Static computation.
            final String value = params[0].toString();

            String newValue = params[params.length - 1].toString();
            for (int i = 1; i < params.length - 1; i += 2) {
                final String regex = params[i].toString();
                if (regex.length() == 0) {
                    throw new ParseException(
                            "An empty regex has been defined for argument of '" + name + "' function", 0);
                }

                final Pattern pattern = PatternCache.get(regex);
                if (pattern.matcher(value).matches()) {
                    newValue = params[i + 1].toString();
                    break;
                }
            }

            gen = new StaticValueFunction(ValString.create(newValue)).createGenerator();

        } else {
            for (int i = 1; i < params.length - 1; i += 2) {
                if (params[i] instanceof Val) {
                    // Test regex is valid.
                    final String regex = params[i].toString();
                    if (regex.length() == 0) {
                        throw new ParseException(
                                "An empty regex has been defined for argument of '" + name + "' function", 0);
                    }
                    PatternCache.get(regex);
                }
            }
        }
    }

    @Override
    public Generator createGenerator() {
        if (gen != null) {
            return gen;
        }
        return super.createGenerator();
    }

    @Override
    protected Generator createGenerator(final Generator[] childGenerators) {
        return new Gen(childGenerators);
    }

    @Override
    public boolean hasAggregate() {
        if (simple) {
            return false;
        }
        return super.hasAggregate();
    }

    private static final class Gen extends AbstractManyChildGenerator {

        Gen(final Generator[] childGenerators) {
            super(childGenerators);
        }

        @Override
        public void set(final Values values) {
            for (final Generator generator : childGenerators) {
                generator.set(values);
            }
        }

        @Override
        public Val eval(final Supplier<ChildData> childDataSupplier) {
            final Val val = childGenerators[0].eval(childDataSupplier);
            if (!val.type().isValue()) {
                return val;
            }

            try {
                final String value = val.toString();
                Val newVal = childGenerators[childGenerators.length - 1].eval(childDataSupplier);
                if (!newVal.type().isValue()) {
                    return ValErr.wrap(newVal);
                }
                String newValue = newVal.toString();

                for (int i = 1; i < childGenerators.length - 1; i += 2) {
                    final Val valRegex = childGenerators[i].eval(childDataSupplier);
                    if (!valRegex.type().isValue()) {
                        return ValErr.wrap(valRegex);
                    }

                    final String regex = valRegex.toString();
                    if (regex.length() == 0) {
                        return ValErr.create("Empty regex");
                    }

                    final Pattern pattern = PatternCache.get(regex);
                    if (pattern.matcher(value).matches()) {
                        newVal = childGenerators[i + 1].eval(childDataSupplier);
                        if (!newVal.type().isValue()) {
                            return ValErr.wrap(newVal);
                        }
                        newValue = newVal.toString();
                        break;
                    }
                }

                if (newValue == null) {
                    return ValNull.INSTANCE;
                }

                return ValString.create(newValue);

            } catch (final RuntimeException e) {
                return ValErr.create(e.getMessage());
            }
        }
    }
}
