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

import stroom.query.language.functions.ref.CountReference;
import stroom.query.language.functions.ref.StoredValues;
import stroom.query.language.functions.ref.ValueReferenceIndex;

import java.util.function.Supplier;

@SuppressWarnings("unused") //Used by FunctionFactory
@FunctionDef(
        name = "count",
        commonCategory = FunctionCategory.AGGREGATE,
        commonDescription = "Counts the number of records that are passed through it. Doesn't take any " +
                "notice of the values of any fields.",
        signatures = @FunctionSignature(
                returnType = ValLong.class,
                returnDescription = "Number of records",
                args = {}))
class Count extends AbstractFunction implements AggregateFunction {

    static final String NAME = "count";
    private CountReference countReference;

    public Count(final String name) {
        super(name, 0, 0);
    }

    @Override
    public void addValueReferences(final ValueReferenceIndex valueReferenceIndex) {
        countReference = valueReferenceIndex.addCount(name);
        super.addValueReferences(valueReferenceIndex);
    }

    @Override
    public Generator createGenerator() {
        return new Gen(countReference);
    }

    @Override
    public boolean isAggregate() {
        return true;
    }

    @Override
    public boolean hasAggregate() {
        return isAggregate();
    }


    // --------------------------------------------------------------------------------


    private static final class Gen extends AbstractNoChildGenerator {

        private final CountReference countReference;

        public Gen(final CountReference countReference) {
            this.countReference = countReference;
        }

        @Override
        public void set(final Val[] values, final StoredValues storedValues) {
            countReference.increment(storedValues);
        }

        @Override
        public Val eval(final StoredValues storedValues, final Supplier<ChildData> childDataSupplier) {
            return ValLong.create(countReference.get(storedValues));
        }

        @Override
        public void merge(final StoredValues existingValues, final StoredValues newValues) {
            final long newValue = countReference.get(newValues);
            countReference.add(existingValues, newValue);
            super.merge(existingValues, newValues);
        }
    }
}
