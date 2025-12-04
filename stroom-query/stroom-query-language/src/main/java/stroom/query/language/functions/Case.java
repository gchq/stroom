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

import stroom.query.language.functions.ref.StoredValues;

import java.text.ParseException;
import java.util.function.Supplier;

@SuppressWarnings("unused") //Used by FunctionFactory
@FunctionDef(
        name = Case.NAME,
        commonCategory = FunctionCategory.LOGIC,
        commonReturnType = Val.class,
        commonReturnDescription = "One of the result arguments, if matched, or the value of the otherwise " +
                "argument if no match is found.",
        signatures = @FunctionSignature(
                description = "Similar to a switch statement. The arguments are split into 3 parts: " +
                        "the input value to compare, pairs of test values with their respective output values " +
                        "and a default result if no matches are found. It must always have an even number " +
                        "of arguments and can have any number of test/result pairs.",
                args = {
                        @FunctionArg(
                                name = "input",
                                description = "The input value to compare against.",
                                argType = Val.class),
                        @FunctionArg(
                                name = "test1",
                                description = "A test value to compare against the input value.",
                                argType = Val.class),
                        @FunctionArg(
                                name = "result1",
                                description = "The result of the function if test1 matches.",
                                argType = Val.class),
                        @FunctionArg(
                                name = "testN",
                                description = "A test value to compare against the input value.",
                                argType = Val.class),
                        @FunctionArg(
                                name = "resultN",
                                description = "The result of the function if testN matches.",
                                argType = Val.class),
                        @FunctionArg(
                                name = "otherwise",
                                description = "The result of the function if none of the test arguments match.",
                                argType = Val.class)}))
class Case extends AbstractManyChildFunction {

    static final String NAME = "case";
    private Generator gen;
    private boolean simple;

    public Case(final String name) {
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
        for (final Param param : params) {
            if (!(param instanceof Val)) {
                simple = false;
                break;
            }
        }

        if (simple) {
            // Static computation.
            final Object input = params[0];

            Object newValue = params[params.length - 1];
            for (int i = 1; i < params.length - 1; i += 2) {
                final Object testValue = params[i];

                if (input.equals(testValue)) {
                    newValue = params[i + 1];
                    break;
                }
            }

            gen = new StaticValueGen(Val.create(newValue));
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


    // --------------------------------------------------------------------------------


    private static final class Gen extends AbstractManyChildGenerator {

        Gen(final Generator[] childGenerators) {
            super(childGenerators);
        }

        @Override
        public Val eval(final StoredValues storedValues, final Supplier<ChildData> childDataSupplier) {
            final Val val = childGenerators[0].eval(storedValues, childDataSupplier);
            if (!val.type().isValue()) {
                return val;
            }

            try {
                Val newVal = childGenerators[childGenerators.length - 1].eval(storedValues, childDataSupplier);
                if (!newVal.type().isNull() && !newVal.type().isValue()) {
                    return ValErr.wrap(newVal);
                }
                Object newValue = newVal;

                for (int i = 1; i < childGenerators.length - 1; i += 2) {
                    final Val testValue = childGenerators[i].eval(storedValues, childDataSupplier);
                    if (!testValue.type().isValue()) {
                        return ValErr.wrap(testValue);
                    }

                    if (val.equals(testValue)) {
                        newVal = childGenerators[i + 1].eval(storedValues, childDataSupplier);
                        if (!newVal.type().isNull() && !newVal.type().isValue()) {
                            return ValErr.wrap(newVal);
                        }
                        newValue = newVal;
                        break;
                    }
                }

                return Val.create(newValue);

            } catch (final RuntimeException e) {
                return ValErr.create(e.getMessage());
            }
        }
    }
}
