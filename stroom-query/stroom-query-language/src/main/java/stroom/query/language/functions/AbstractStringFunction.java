/*
 * Copyright 2017-2024 Crown Copyright
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
import stroom.query.language.token.Param;

import java.text.ParseException;
import java.util.function.Supplier;

abstract class AbstractStringFunction extends AbstractFunction {

    private Generator gen;
    private Function function;
    private boolean hasAggregate;

    AbstractStringFunction(final String name) {
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
            // Optimise replacement of static input in case user does something stupid.
            gen = new StaticValueGen(ValString.create(getOperation().apply(param.toString())));
        }
    }

    @Override
    public Generator createGenerator() {
        if (gen != null) {
            return gen;
        }

        final Generator childGenerator = function.createGenerator();
        return new Gen(childGenerator, getOperation());
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

    abstract Operation getOperation();


    // --------------------------------------------------------------------------------


    interface Operation {

        String apply(String value);
    }


    // --------------------------------------------------------------------------------


    private static final class Gen extends AbstractSingleChildGenerator {

        private final Operation operation;

        Gen(final Generator childGenerator, final Operation operation) {
            super(childGenerator);
            this.operation = operation;
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

            return ValString.create(operation.apply(val.toString()));
        }
    }
}
