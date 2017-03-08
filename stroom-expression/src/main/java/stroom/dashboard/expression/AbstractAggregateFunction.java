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

package stroom.dashboard.expression;

public abstract class AbstractAggregateFunction extends AbstractManyChildFunction implements AggregateFunction {
    private final Calculator calculator;

    public AbstractAggregateFunction(final String name, final Calculator calculator) {
        super(name, 1, Integer.MAX_VALUE);
        this.calculator = calculator;
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

    private static class AggregateGen extends AbstractSingleChildGenerator {
        private static final long serialVersionUID = -5622353515345145314L;

        private final Calculator calculator;

        private Double current;

        public AggregateGen(final Generator childGenerator, final Calculator calculator) {
            super(childGenerator);
            this.calculator = calculator;
        }

        @Override
        public void set(final String[] values) {
            childGenerator.set(values);
            current = calculator.calc(current, childGenerator.eval());
        }

        @Override
        public Object eval() {
            return current;
        }

        @Override
        public void merge(final Generator generator) {
            final AggregateGen aggregateGen = (AggregateGen) generator;
            current = calculator.calc(current, aggregateGen.current);
            super.merge(generator);
        }
    }

    private static class Gen extends AbstractManyChildGenerator {
        private static final long serialVersionUID = -5622353515345145314L;

        private final Calculator calculator;

        public Gen(final Generator[] childGenerators, final Calculator calculator) {
            super(childGenerators);
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

            return value;
        }
    }
}
