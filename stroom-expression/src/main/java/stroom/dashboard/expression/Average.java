/*
 * Copyright 2016 Crown Copyright
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

package stroom.dashboard.expression;

public class Average extends AbstractManyChildFunction implements AggregateFunction {
    private static class AggregateGen extends AbstractSingleChildGenerator {
        private static final long serialVersionUID = -6770724151493320673L;

        private final Calculator calculator;

        private Double current;
        private int count;

        public AggregateGen(final Generator childGenerator, final Calculator calculator) {
            super(childGenerator);
            this.calculator = calculator;
        }

        @Override
        public void set(final String[] values) {
            childGenerator.set(values);
            current = calculator.calc(current, childGenerator.eval());
            count++;
        }

        @Override
        public Object eval() {
            if (current == null || count == 0) {
                return null;
            }
            return current / count;
        }

        @Override
        public void merge(final Generator generator) {
            final AggregateGen aggregateGen = (AggregateGen) generator;
            current = calculator.calc(current, aggregateGen.current);
            count += aggregateGen.count;

            super.merge(generator);
        }
    }

    private static class Gen extends AbstractManyChildGenerator {
        private static final long serialVersionUID = -6770724151493320673L;

        private final Calculator calculator;

        public Gen(final Generator[] generators, final Calculator calculator) {
            super(generators);
            this.calculator = calculator;
        }

        @Override
        public void set(final String[] values) {
            for (final Generator gen : childGenerators) {
                gen.set(values);
            }
        }

        @Override
        public Object eval() {
            Double value = null;
            for (final Generator gen : childGenerators) {
                value = calculator.calc(value, gen.eval());
            }

            final Double cur = TypeConverter.getDouble(value);
            if (cur == null) {
                return null;
            }

            return cur / childGenerators.length;
        }
    }

    public static final String NAME = "average";
    public static final String ALIAS = "mean";

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
}
