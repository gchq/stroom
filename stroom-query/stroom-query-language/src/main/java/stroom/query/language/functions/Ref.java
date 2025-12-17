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

import stroom.query.language.functions.ref.FieldValReference;
import stroom.query.language.functions.ref.StoredValues;
import stroom.query.language.functions.ref.ValueReferenceIndex;

import java.util.Objects;
import java.util.function.Supplier;

@ArchitecturalFunction
class Ref extends AbstractFunction {

    private final String text;
    private final int fieldIndex;
    private FieldValReference fieldValReference;

    public Ref(final String text, final int fieldIndex) {
        super(text, 0, 0);
        this.text = text;
        this.fieldIndex = fieldIndex;
    }

    @Override
    public void addValueReferences(final ValueReferenceIndex valueReferenceIndex) {
        // If the field index is less than 0 then we will always return null so don't store.
        if (fieldIndex >= 0) {
            fieldValReference = valueReferenceIndex.addFieldValue(text, fieldIndex);
        }
        super.addValueReferences(valueReferenceIndex);
    }

    @Override
    public Generator createGenerator() {
        // If the field index is less than 0 then we will always return null so
        // get the null generator.
        if (fieldValReference == null) {
            return Null.GEN;
        } else {
            return new Gen(fieldIndex, fieldValReference);
        }
    }

    @Override
    public void appendString(final StringBuilder sb) {
        sb.append("${");
        sb.append(text);
        sb.append("}");
    }

    @Override
    public boolean hasAggregate() {
        return false;
    }

    private static final class Gen extends AbstractNoChildGenerator {


        private final int fieldIndex;
        private final FieldValReference fieldValReference;

        Gen(final int fieldIndex,
            final FieldValReference fieldValReference) {
            if (fieldIndex < 0) {
                throw new IndexOutOfBoundsException("Field index must be >= 0");
            }
            Objects.requireNonNull(fieldValReference, "Null field val reference");

            this.fieldIndex = fieldIndex;
            this.fieldValReference = fieldValReference;
        }

        @Override
        public void set(final Val[] values, final StoredValues storedValues) {
            final Val val = values[fieldIndex];
            if (val != null) {
                fieldValReference.set(storedValues, val);
            }
        }

        @Override
        public Val eval(final StoredValues storedValues, final Supplier<ChildData> childDataSupplier) {
            return fieldValReference.get(storedValues);
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            final Gen gen = (Gen) o;
            return fieldIndex == gen.fieldIndex;
        }

        @Override
        public int hashCode() {
            return Objects.hash(fieldIndex);
        }
    }
}
