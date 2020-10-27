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

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;

class Variance extends AbstractManyChildFunction implements AggregateFunction {
    static final String NAME = "variance";

    public Variance(final String name) {
        super(name, 1, Integer.MAX_VALUE);
    }

    @Override
    public void setParams(final Param[] params) throws ParseException {
        super.setParams(params);

        for (final Function function : functions) {
            if (function.hasAggregate()) {
                throw new ParseException("Inner param of '" + name + "' cannot be an aggregating function", 0);
            }
        }
    }

    @Override
    public Generator createGenerator() {
        // If we only have a single param then we are operating in aggregate
        // mode.
        if (isAggregate()) {
            final Generator childGenerator = functions[0].createGenerator();
            return new AggregateGen(childGenerator);
        }

        return super.createGenerator();
    }

    @Override
    protected Generator createGenerator(final Generator[] childGenerators) {
        return new Gen(childGenerators);
    }

    @Override
    public boolean isAggregate() {
        return functions.length == 1;
    }

    private static final class AggregateGen extends AbstractSingleChildGenerator {
        private static final long serialVersionUID = -6770724151493320673L;

        private final List<Double> list = new ArrayList<>();

        AggregateGen(final Generator childGenerator) {
            super(childGenerator);
        }

        @Override
        public void set(final Val[] values) {
            childGenerator.set(values);
            final Double d = childGenerator.eval().toDouble();
            if (d != null) {
                list.add(d);
            }
        }

        @Override
        public Val eval() {
            if (list.size() == 0) {
                return ValNull.INSTANCE;
            }

            // Isolate list.
            Double[] arr;
            synchronized (list) {
                arr = list.toArray(new Double[0]);
            }

            // calculate variance
            return ValDouble.create(Statistics.variance(arr));
        }

        @Override
        public void merge(final Generator generator) {
            final AggregateGen aggregateGen = (AggregateGen) generator;
            list.addAll(aggregateGen.list);
            super.merge(generator);
        }

        @Override
        public void read(final Kryo kryo, final Input input) {
            super.read(kryo, input);
            final int size = input.readInt(true);
            list.clear();
            for (int i = 0; i < size; i++) {
                list.add(input.readDouble());
            }
        }

        @Override
        public void write(final Kryo kryo, final Output output) {
            super.write(kryo, output);
            output.writeInt(list.size(), true);
            for (final double d : list) {
                output.writeDouble(d);
            }
        }
    }

    private static final class Gen extends AbstractManyChildGenerator {
        private static final long serialVersionUID = -6770724151493320673L;

        Gen(final Generator[] generators) {
            super(generators);
        }

        @Override
        public void set(final Val[] values) {
            for (final Generator gen : childGenerators) {
                gen.set(values);
            }
        }

        @Override
        public Val eval() {
            final List<Double> list = new ArrayList<>(childGenerators.length);
            for (final Generator gen : childGenerators) {
                final Val val = gen.eval();
                if (!val.type().isValue()) {
                    return val;
                }
                final Double value = val.toDouble();
                if (value != null) {
                    list.add(value);
                }
            }

            if (list.size() == 0) {
                return ValNull.INSTANCE;
            }

            // Isolate list.
            final Double[] arr = list.toArray(new Double[0]);

            // calculate variance
            return ValDouble.create(Statistics.variance(arr));
        }
    }
}
