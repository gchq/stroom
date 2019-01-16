/*
 * Copyright 2016 Crown Copyright
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

package stroom.util.shared;

import stroom.docref.SharedObject;

import java.util.Objects;

public class OffsetRange<T extends Number> implements SharedObject {
    private static final long serialVersionUID = 5045453517852867315L;

    private T offset;
    private T length;

    public OffsetRange() {
        // Default constructor necessary for GWT serialisation.
    }

    public OffsetRange(final T offset, final T length) {
        this.offset = offset;
        this.length = length;
    }

    public T getOffset() {
        return offset;
    }

    public T getLength() {
        return length;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final OffsetRange<?> that = (OffsetRange<?>) o;
        return Objects.equals(offset, that.offset) &&
                Objects.equals(length, that.length);
    }

    @Override
    public int hashCode() {
        return Objects.hash(offset, length);
    }

    @Override
    public String toString() {
        return "OffsetRange{" +
                "offset=" + offset +
                ", length=" + length +
                '}';
    }
}
