/*
 * Copyright 2018 Crown Copyright
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

import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;

import java.text.ParseException;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Supplier;

@SuppressWarnings("unused") //Used by FunctionFactory
@FunctionDef(
        name = CountUnique.NAME,
        commonCategory = FunctionCategory.AGGREGATE,
        commonReturnType = ValLong.class,
        commonReturnDescription = "The number of unique values",
        signatures = {
                @FunctionSignature(
                        category = FunctionCategory.AGGREGATE,
                        description = "Determines the number of unique values across all grouped records.",
                        args = @FunctionArg(
                                name = "values",
                                description = "Grouped field or the result of another function",
                                argType = Val.class)),
                @FunctionSignature(
                        category = FunctionCategory.MATHEMATICS,
                        description = "Determines the number of unique values in the provided arguments",
                        args = @FunctionArg(
                                name = "arg",
                                description = "Field, the result of another function or a constant.",
                                argType = Val.class,
                                isVarargs = true,
                                minVarargsCount = 2))})
class CountUnique extends AbstractFunction {

    static final String NAME = "countUnique";

    private Generator gen;
    private Function function;

    public CountUnique(final String name) {
        super(name, 1, 1);
    }

    @Override
    public void setParams(final Param[] params) throws ParseException {
        super.setParams(params);

        final Param param = params[0];
        if (param instanceof Function) {
            function = (Function) param;

            if (function.hasAggregate()) {
                throw new ParseException(name + " cannot be applied to aggregating function", 0);
            }

        } else {
            /*
             * Optimise replacement of static input in case user does something
             * stupid.
             */
            gen = new StaticValueFunction(ValInteger.create(1)).createGenerator();
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
    public boolean isAggregate() {
        return true;
    }

    @Override
    public boolean hasAggregate() {
        return isAggregate();
    }

    private static final class Gen extends AbstractSingleChildGenerator {

        private final Set<Val> uniqueValues = new HashSet<>();

        Gen(final Generator childGenerator) {
            super(childGenerator);
        }

        @Override
        public void set(final Val[] values) {
            childGenerator.set(values);
            final Val value = childGenerator.eval(null);
            if (value.type().isValue()) {
                uniqueValues.add(value);
            }
        }

        @Override
        public Val eval(final Supplier<ChildData> childDataSupplier) {
            return ValInteger.create(uniqueValues.size());
        }

        @Override
        public void merge(final Generator generator) {
            final Gen gen = (Gen) generator;
            uniqueValues.addAll(gen.uniqueValues);
            super.merge(generator);
        }

        @Override
        public void read(final Input input) {
            uniqueValues.clear();
            final int length = input.readInt(true);
            for (int i = 0; i < length; i++) {
                uniqueValues.add(ValSerialiser.read(input));
            }
        }

        @Override
        public void write(final Output output) {
            output.writeInt(uniqueValues.size(), true);
            for (final Val val : uniqueValues) {
                ValSerialiser.write(output, val);
            }
        }
    }
}
