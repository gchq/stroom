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
import stroom.query.language.functions.ref.ValReference;
import stroom.query.language.functions.ref.ValueReferenceIndex;

import java.util.function.Supplier;

abstract class AbstractAggregateFunction extends AbstractManyChildFunction implements AggregateFunction {

    private final Calculator calculator;
    private ValReference valReference;

    AbstractAggregateFunction(final String name, final Calculator calculator) {
        super(name, 1, Integer.MAX_VALUE);
        this.calculator = calculator;
    }

    @Override
    public void addValueReferences(final ValueReferenceIndex valueReferenceIndex) {
        valReference = valueReferenceIndex.addValue(name);
        super.addValueReferences(valueReferenceIndex);
    }

    @Override
    public Generator createGenerator() {
        // If we only have a single param then we are operating in aggregate
        // mode.
        if (isAggregate()) {
            final Generator childGenerator = functions[0].createGenerator();
            return new AggregateGen(childGenerator, calculator, valReference);
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
        private final ValReference valReference;

        AggregateGen(final Generator childGenerator,
                     final Calculator calculator,
                     final ValReference valReference) {
            super(childGenerator);
            this.calculator = calculator;
            this.valReference = valReference;
        }

        @Override
        public void set(final Val[] values, final StoredValues storedValues) {
            childGenerator.set(values, storedValues);
            final Val current = valReference.get(storedValues);
            final Val val = calculator.calc(current, childGenerator.eval(storedValues, null));
            valReference.set(storedValues, val);
        }

        @Override
        public Val eval(final StoredValues storedValues, final Supplier<ChildData> childDataSupplier) {
            return valReference.get(storedValues);
        }

        @Override
        public void merge(final StoredValues existingValues, final StoredValues newValues) {
            Val current = valReference.get(existingValues);
            current = calculator.calc(current, valReference.get(newValues));
            valReference.set(existingValues, current);
            super.merge(existingValues, newValues);
        }
    }

    private static final class Gen extends AbstractManyChildGenerator {

        private final Calculator calculator;

        Gen(final Generator[] childGenerators, final Calculator calculator) {
            super(childGenerators);
            this.calculator = calculator;
        }

        @Override
        public Val eval(final StoredValues storedValues, final Supplier<ChildData> childDataSupplier) {
            Val value = ValNull.INSTANCE;
            for (final Generator gen : childGenerators) {
                final Val val = gen.eval(storedValues, childDataSupplier);
//                if (!val.type().isValue()) {
//                    return val;
//                }
                value = calculator.calc(value, val);
            }
            return value;
        }
    }
}
