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

import stroom.dashboard.expression.v1.ref.CountIterationReference;
import stroom.dashboard.expression.v1.ref.StoredValues;
import stroom.dashboard.expression.v1.ref.ValueReferenceIndex;

import java.text.ParseException;
import java.util.function.Supplier;

@SuppressWarnings("unused") //Used by FunctionFactory
@FunctionDef(
        name = CountPrevious.NAME,
        commonCategory = FunctionCategory.AGGREGATE,
        commonDescription = "Counts the number of records that are passed through it for a previous time period. " +
                "Doesn't take any notice of the values of any fields.",
        signatures = @FunctionSignature(
                returnType = ValLong.class,
                returnDescription = "Number of records",
                args = {}))
public class CountPrevious extends AbstractFunction implements AggregateFunction {

    static final String NAME = "countPrevious";
    private int iteration;
    private CountIterationReference valueReference;

    public CountPrevious(final String name) {
        super(name, 1, 1);
    }

    @Override
    public void setParams(final Param[] params) throws ParseException {
        super.setParams(params);
        iteration = Integer.parseInt(params[0].toString());
    }

    @Override
    public void addValueReferences(final ValueReferenceIndex valueReferenceIndex) {
        valueReference = valueReferenceIndex.addCountIteration(iteration);
        super.addValueReferences(valueReferenceIndex);
    }

    @Override
    public Generator createGenerator() {
        return new Gen(iteration, valueReference);
    }

    @Override
    public boolean isAggregate() {
        return true;
    }

    @Override
    public boolean hasAggregate() {
        return isAggregate();
    }

    public static final class Gen extends AbstractNoChildGenerator {

        private final int iteration;
        private final CountIterationReference valueReference;

        public Gen(final int iteration, final CountIterationReference valueReference) {
            this.iteration = iteration;
            this.valueReference = valueReference;
        }

        @Override
        public void set(final Val[] values, final StoredValues storedValues) {
            valueReference.increment(storedValues);
        }

        @Override
        public Val eval(final StoredValues storedValues, final Supplier<ChildData> childDataSupplier) {
            return ValLong.create(valueReference.get(storedValues));
        }

        @Override
        public void merge(final StoredValues existingValues, final StoredValues newValues) {
            final long existingValue = valueReference.get(existingValues);
            final long newValue = valueReference.get(newValues);
            valueReference.set(existingValues, existingValue + newValue);
            super.merge(existingValues, newValues);
        }

        public int getIteration() {
            return iteration;
        }
    }
}
