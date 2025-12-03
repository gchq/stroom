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

package stroom.pipeline.refdata.store.offheapstore;

import java.util.Objects;


/**
 * < typeId >< referenceCount >
 * < 1 byte >< 3 bytes >
 * <p>
 * referenceCount stored as a 3 byte unsigned integer so a max
 * of ~16 million.
 */
public class ValueStoreMeta {

    private final byte typeId;
    private final int referenceCount;

    public ValueStoreMeta(final byte typeId) {
        this.typeId = typeId;
        this.referenceCount = 1;
    }

    public ValueStoreMeta(final byte typeId, final int referenceCount) {
        this.typeId = typeId;
        this.referenceCount = referenceCount;
    }

    public byte getTypeId() {
        return typeId;
    }

    public int getReferenceCount() {
        return referenceCount;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ValueStoreMeta that = (ValueStoreMeta) o;
        return typeId == that.typeId &&
                referenceCount == that.referenceCount;
    }

    @Override
    public int hashCode() {
        return Objects.hash(typeId, referenceCount);
    }

    @Override
    public String toString() {
        return "ValueStoreMeta{" +
                "typeId=" + typeId +
                ", referenceCount=" + referenceCount +
                '}';
    }
}
