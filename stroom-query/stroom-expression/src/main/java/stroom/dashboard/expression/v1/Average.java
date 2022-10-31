/*
 * Copyright 2017 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
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

import java.util.function.Supplier;

@SuppressWarnings("unused") //Used by FunctionFactory
@FunctionDef(
        name = Average.NAME,
        aliases = Average.ALIAS,
        commonReturnType = ValDouble.class,
        commonReturnDescription = "The average (mean) of all the values",
        signatures = {
                @FunctionSignature(
                        category = FunctionCategory.AGGREGATE,
                        description = "Determines the average (mean) value across all grouped records.",
                        args = @FunctionArg(
                                name = "values",
                                description = "Grouped field or the result of another function",
                                argType = ValNumber.class)),
                @FunctionSignature(
                        category = FunctionCategory.MATHEMATICS,
                        subCategories = "Statistical",
                        description = "Determines the average (mean) value of all arguments.",
                        args = @FunctionArg(
                                name = "value",
                                description = "Field, the result of another function or a constant.",
                                argType = ValNumber.class,
                                isVarargs = true,
                                minVarargsCount = 2))})
class Average extends AbstractManyChildFunction implements AggregateFunction {

    static final String NAME = "average";
    static final String ALIAS = "mean";
    private final Add.Calc calculator = new Add.Calc();

    public Average(final String name) {
        super(name, 1, Integer.MAX_VALUE);
    }

    @Override
    public Generator createGenerator() {
        // If we only have a single param then we are operating in aggregate
        // mode.
        if (isAggregate()) {
            final Generator childGenerator = functions[0].createGenerator();
            return new AggregateGen(childGenerator, calculator);
        }

        return super.createGenerator();
    }

    @Override
    protected Generator createGenerator(final Generator[] childGenerators) {
        return new Gen(childGenerators, calculator);
    }

    @Override
    public boolean isAggregate() {
        return functions.length == 1;
    }

    private static final class AggregateGen extends AbstractSingleChildGenerator {

        private final Calculator calculator;

        private Val current = ValNull.INSTANCE;
        private int count;

        AggregateGen(final Generator childGenerator, final Calculator calculator) {
            super(childGenerator);
            this.calculator = calculator;
        }

        @Override
        public void set(final Val[] values) {
            childGenerator.set(values);
            current = calculator.calc(current, childGenerator.eval(null));
            count++;
        }

        @Override
        public Val eval(final Supplier<ChildData> childDataSupplier) {
            if (!current.type().isValue() || count == 0) {
                if (current.type().isError()) {
                    return current;
                } else {
                    return ValNull.INSTANCE;
                }
            }
            return ValDouble.create(current.toDouble() / count);
        }

        @Override
        public void merge(final Generator generator) {
            final AggregateGen aggregateGen = (AggregateGen) generator;
            current = calculator.calc(current, aggregateGen.current);
            count += aggregateGen.count;

            super.merge(generator);
        }

        @Override
        public void read(final Input input) {
            super.read(input);
            current = ValSerialiser.read(input);
            count = input.readInt(true);
        }

        @Override
        public void write(final Output output) {
            super.write(output);
            ValSerialiser.write(output, current);
            output.writeInt(count, true);
        }
    }

    private static final class Gen extends AbstractManyChildGenerator {

        private final Calculator calculator;

        Gen(final Generator[] generators, final Calculator calculator) {
            super(generators);
            this.calculator = calculator;
        }

        @Override
        public void set(final Val[] values) {
            for (final Generator gen : childGenerators) {
                gen.set(values);
            }
        }

        @Override
        public Val eval(final Supplier<ChildData> childDataSupplier) {
            Val value = ValNull.INSTANCE;
            for (final Generator gen : childGenerators) {
                final Val val = gen.eval(childDataSupplier);
                if (!val.type().isValue()) {
                    return val;
                }
                value = calculator.calc(value, val);
            }
            if (!value.type().isValue()) {
                return value;
            }
            final Double val = value.toDouble();
            if (val == null) {
                return value;
            }
            return ValDouble.create(val / childGenerators.length);
        }
    }
}
