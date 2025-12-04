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
        name = If.NAME,
        commonCategory = FunctionCategory.LOGIC,
        commonReturnType = Val.class,
        commonReturnDescription = "If condition is true, the value of the 2nd parameter else the value of the 3rd " +
                "parameter. The type will match that of the selected parameter.",
        signatures = @FunctionSignature(
                description = "Tests if the 1st parameter is true and if so returns parameter 2 else it returns " +
                        "parameter 3.",
                args = {
                        @FunctionArg(
                                name = "condition",
                                description = "A field, function or constant that evaluates to a boolean.",
                                argType = ValBoolean.class),
                        @FunctionArg(
                                name = "valueIfTrue",
                                description = "Field, the result of another function or a constant.",
                                argType = Val.class),
                        @FunctionArg(
                                name = "valueIfFalse",
                                description = "Field, the result of another function or a constant.",
                                argType = Val.class)}))
class If extends AbstractManyChildFunction {

    static final String NAME = "if";
    private Generator gen;
    private boolean simple;

    public If(final String name) {
        super(name, 3, 3);
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

        if (params[0] instanceof Val) {
            final Boolean condition = ((Val) params[0]).toBoolean();
            if (condition == null) {
                throw new ParseException("Expecting a condition for first argument of '" + name + "' function", 0);
            }
        }

        if (simple) {
            // Static computation.
            final Boolean condition = ((Val) params[0]).toBoolean();
            if (condition) {
                gen = new StaticValueFunction((Val) params[1]).createGenerator();
            } else {
                gen = new StaticValueFunction((Val) params[2]).createGenerator();
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
        public Val eval(final StoredValues storedValues, final Supplier<ChildData> childDataSupplier) {
            final Val val = childGenerators[0].eval(storedValues, childDataSupplier);
            if (!val.type().isValue()) {
                return val;
            }

            try {
                final Boolean condition = val.toBoolean();
                if (condition == null) {
                    return ValErr.create("Expecting a condition");
                }
                if (condition) {
                    return childGenerators[1].eval(storedValues, childDataSupplier);
                } else {
                    return childGenerators[2].eval(storedValues, childDataSupplier);
                }
            } catch (final RuntimeException e) {
                return ValErr.create(e.getMessage());
            }
        }
    }
}
