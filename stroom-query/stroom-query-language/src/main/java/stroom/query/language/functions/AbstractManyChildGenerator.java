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

import java.util.function.Supplier;

abstract class AbstractManyChildGenerator extends AbstractGenerator {

    final Generator[] childGenerators;

    AbstractManyChildGenerator(final Generator[] childGenerators) {
        this.childGenerators = childGenerators;
    }

    @Override
    public final void set(final Val[] values, final StoredValues storedValues) {
        for (final Generator generator : childGenerators) {
            generator.set(values, storedValues);
        }
    }

    @Override
    public abstract Val eval(final StoredValues storedValues, final Supplier<ChildData> childDataSupplier);

    @Override
    public final void merge(final StoredValues existingValues, final StoredValues newValues) {
        for (final Generator childGenerator : childGenerators) {
            childGenerator.merge(existingValues, newValues);
        }
    }
}
