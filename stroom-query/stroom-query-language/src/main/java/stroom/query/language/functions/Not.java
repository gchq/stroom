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
        name = Not.NAME,
        commonCategory = FunctionCategory.LOGIC,
        commonReturnType = ValBoolean.class,
        commonReturnDescription = "The inverse of the supplied value.",
        signatures = @FunctionSignature(
                description = "Inverts boolean values making true, false etc. The value provided will be implicitly " +
                        "cast to a boolean, e.g 'not(\"true\")' returns false",
                args = {
                        @FunctionArg(
                                name = "value",
                                description = "Field, function or a constant that evaluates to a boolean.",
                                argType = Val.class)
                }))
class Not extends AbstractFunction {

    static final String NAME = "not";
    private Generator gen;
    private Function function;
    private boolean hasAggregate;

    public Not(final String name) {
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
            final Boolean condition = ((Val) params[0]).toBoolean();
            if (condition == null) {
                throw new ParseException("Expecting a condition for first argument of '" + name + "' function", 0);
            }
            // Static computation.
            gen = new StaticValueFunction(ValBoolean.create(!condition)).createGenerator();
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

            try {
                final Boolean condition = val.toBoolean();
                if (condition == null) {
                    return ValErr.create("Expecting a condition");
                }
                return ValBoolean.create(!condition);
            } catch (final RuntimeException e) {
                return ValErr.create(e.getMessage());
            }
        }
    }
}
