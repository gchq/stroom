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

abstract class Selector extends AbstractSingleChildGenerator {

    Selector(final Generator childGenerator) {
        super(childGenerator);
    }

    @Override
    public void set(final Val[] values, final StoredValues storedValues) {
        childGenerator.set(values, storedValues);
    }

    @Override
    public Val eval(final StoredValues storedValues, final Supplier<ChildData> childDataSupplier) {
        Val val = null;
        if (childDataSupplier != null) {
            final ChildData childData = childDataSupplier.get();
            if (childData != null) {
                val = select(childGenerator, childData);
            }
        }

        if (val == null) {
            val = childGenerator.eval(storedValues, childDataSupplier);
        }

        return val;
    }

    Val select(final Generator childGenerator, final ChildData childData) {
        return null;
    }
}
