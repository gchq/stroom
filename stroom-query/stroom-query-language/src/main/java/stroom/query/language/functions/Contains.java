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
        name = Contains.NAME,
        commonCategory = FunctionCategory.STRING,
        commonReturnType = ValBoolean.class,
        commonReturnDescription = "True if string contains substring",
        signatures = @FunctionSignature(
                description = "Tests if inputString contains subString.",
                args = {
                        @FunctionArg(
                                name = "inputString",
                                description = "The string to find subString in.",
                                argType = ValString.class),
                        @FunctionArg(
                                name = "subString",
                                description = "The string to find in inputString.",
                                argType = ValString.class)
                }))
class Contains extends AbstractManyChildFunction {

    static final String NAME = "contains";
    private Generator gen;
    private boolean simple;

    public Contains(final String name) {
        super(name, 2, 2);
    }

    @Override
    public void setParams(final Param[] params) throws ParseException {
        super.setParams(params);

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
            final String string = params[0].toString();
            final String substring = params[1].toString();
            final boolean result = string.contains(substring);
            gen = new StaticValueFunction(ValBoolean.create(result)).createGenerator();
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
            final Val valSubString = childGenerators[1].eval(storedValues, childDataSupplier);
            if (!valSubString.type().isValue()) {
                return ValErr.wrap(valSubString);
            }

            try {
                final String string = val.toString();
                final String substring = valSubString.toString();
                return ValBoolean.create(string.contains(substring));

            } catch (final RuntimeException e) {
                return ValErr.create(e.getMessage());
            }
        }
    }
}
