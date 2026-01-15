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
        name = TypeOf.NAME,
        commonCategory = FunctionCategory.TYPE_CHECKING,
        commonReturnType = Val.class,
        commonReturnDescription = "The name of the data type of the value, e.g. 'null', 'error', 'string', etc.",
        signatures = @FunctionSignature(
                description = "Determine the data type of the passed value.",
                args = {
                        @FunctionArg(
                                name = "value",
                                description = "The value to find the type of.",
                                argType = Val.class)
                }))
class TypeOf extends AbstractFunction {

    static final String NAME = "typeOf";
    private Generator gen;
    private Function function;
    private boolean hasAggregate;

    public TypeOf(final String name) {
        super(name, 1, 1);
    }

    @Override
    public void setParams(final Param[] params) throws ParseException {
        super.setParams(params);

        final Param param = params[0];
        if (param instanceof Function) {
            function = (Function) param;
            hasAggregate = function.hasAggregate();
        } else if (param instanceof Val) {
            // Optimise replacement of static input in case user does something stupid.
            gen = new Gen(new StaticValueFunction((Val) param).createGenerator());
            hasAggregate = false;
        } else {
            throw new RuntimeException("Unexpected type [" + param.getClass().getSimpleName() + "]");
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
            final String type = val.type().toString();
            if (type != null) {
                return ValString.create(type);
            }
            return ValNull.INSTANCE;
        }
    }
}
