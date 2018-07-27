/*
 * Copyright 2018 Crown Copyright
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
 *
 */

package stroom.refdata.offheapstore;

import java.util.Objects;

public abstract class RefDataValue {

    // A count of the number of KeyValueStoreDb or RangeValueStoreDb keys that are associated
    // with this value. This count should not form part of the equals or hashcode
    protected final int referenceCount;

    RefDataValue(final int referenceCount) {
        this.referenceCount = referenceCount;
    }

    /**
     * @return The hashcode of just the underlying value that this object wraps
     * rather than hashcode of the whole object.
     */
    public abstract int getValueHashCode();

    /**
     * @return A code to represent the class of the implementation,
     * unique within all sub-classes of {@link RefDataValue}
     */
    public abstract int getTypeId();

    public int getReferenceCount() {
        return referenceCount;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final RefDataValue that = (RefDataValue) o;
        return referenceCount == that.referenceCount;
    }

    @Override
    public int hashCode() {
        return Objects.hash(referenceCount);
    }

    @Override
    public String toString() {
        return "RefDataValue{" +
                "referenceCount=" + referenceCount +
                '}';
    }
}
