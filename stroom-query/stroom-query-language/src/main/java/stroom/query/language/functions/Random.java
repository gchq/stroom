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

import stroom.query.language.functions.ref.RandomValReference;
import stroom.query.language.functions.ref.StoredValues;
import stroom.query.language.functions.ref.ValueReferenceIndex;

import java.util.function.Supplier;

@SuppressWarnings("unused") //Used by FunctionFactory
@FunctionDef(
        name = Random.NAME,
        commonCategory = FunctionCategory.MATHEMATICS,
        commonReturnType = ValDouble.class,
        commonReturnDescription = "A random number between 0.0 (inc.) and 1.0 (excl.)",
        signatures = @FunctionSignature(
                description = "Returns a double value with a positive sign, greater than or equal to 0.0 and " +
                        "less than 1.0. Returned values are chosen pseudorandomly with (approximately) uniform " +
                        "distribution from that range.",
                args = {
                }))
class Random extends AbstractFunction {

    static final String NAME = "random";

    private RandomValReference randomValReference;

    public Random(final String name) {
        super(name, 0, 0);
    }

    @Override
    public void addValueReferences(final ValueReferenceIndex valueReferenceIndex) {
        randomValReference = valueReferenceIndex.addRandomValue(name);
        super.addValueReferences(valueReferenceIndex);
    }

    @Override
    public Generator createGenerator() {
        return new Gen(randomValReference);
    }

    @Override
    public boolean hasAggregate() {
        return false;
    }

    private static final class Gen extends AbstractNoChildGenerator {

        private final RandomValReference randomValReference;

        public Gen(final RandomValReference randomValReference) {
            this.randomValReference = randomValReference;
        }

        @Override
        public void set(final Val[] values, final StoredValues storedValues) {
            randomValReference.set(storedValues, ValDouble.create(Math.random()));
        }

        @Override
        public Val eval(final StoredValues storedValues, final Supplier<ChildData> childDataSupplier) {
            return randomValReference.get(storedValues);
        }
    }
}
