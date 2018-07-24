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

import java.nio.ByteBuffer;
import java.util.Objects;

public class ByteBufferPair {
    private final ByteBuffer keyBuffer;
    private final ByteBuffer valueBuffer;

    ByteBufferPair(final ByteBuffer keyBuffer, final ByteBuffer valueBuffer) {
        this.keyBuffer = keyBuffer;
        this.valueBuffer = valueBuffer;
    }

    public static ByteBufferPair of(final ByteBuffer keyBuffer, final ByteBuffer valueBuffer) {
        return new ByteBufferPair(keyBuffer, valueBuffer);
    }

    ByteBuffer getKeyBuffer() {
        return keyBuffer;
    }

    ByteBuffer getValueBuffer() {
        return valueBuffer;
    }

    public void clear() {
        keyBuffer.clear();
        valueBuffer.clear();
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final ByteBufferPair that = (ByteBufferPair) o;
        return Objects.equals(keyBuffer, that.keyBuffer) &&
                Objects.equals(valueBuffer, that.valueBuffer);
    }

    @Override
    public int hashCode() {

        return Objects.hash(keyBuffer, valueBuffer);
    }

    @Override
    public String toString() {
        return "key: [" + ByteBufferUtils.byteBufferInfo(keyBuffer) +
                "] value: [" + ByteBufferUtils.byteBufferInfo(valueBuffer) +
                "]";
    }
}
