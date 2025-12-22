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

package stroom.lmdb.serde;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Objects;

public interface Serde<T> extends Serializer<T>, Deserializer<T> {

    ByteOrder JAVA_BYTE_ORDER = ByteOrder.BIG_ENDIAN;

    default Serializer<T> serializer() {
        return this;
    }

    default Deserializer<T> deserializer() {
        return this;
    }

    /**
     * Wraps the supplied serde with one that sets the {@link ByteOrder} to native
     * order then delegates to serde.
     * All byteBuffers will be returned to {@link ByteOrder#BIG_ENDIAN} after use.
     * This is useful for MDB_INTEGERKEY mode.
     * See <a href="https://github.com/lmdbjava/lmdbjava/wiki/Keys#numeric-keys">Numeric Keys</a>
     * and <a href="https://github.com/lmdbjava/lmdbjava/issues/51">this github issue</a>.
     */
    static <T> Serde<T> usingNativeOrder(final Serde<T> serde) {

        Objects.requireNonNull(serde);
        if (ByteOrder.nativeOrder().equals(JAVA_BYTE_ORDER)) {
            // java byte order matched native so can just use serde as is
            return serde;
        } else {
            return new Serde<T>() {

                @Override
                public T deserialize(final ByteBuffer byteBuffer) {
                    try {
                        byteBuffer.order(ByteOrder.nativeOrder());
                        return serde.deserialize(byteBuffer);

                    } finally {
                        // Ensure we return the byte buffer to default endianness
                        byteBuffer.order(ByteOrder.BIG_ENDIAN);
                    }
                }

                @Override
                public void serialize(final ByteBuffer byteBuffer, final T object) {
                    try {
                        byteBuffer.order(ByteOrder.nativeOrder());
                        serde.serialize(byteBuffer, object);

                    } finally {
                        // Ensure we return the byte buffer to default endianness
                        byteBuffer.order(ByteOrder.BIG_ENDIAN);
                    }
                }

                @Override
                public int getBufferCapacity() {
                    return serde.getBufferCapacity();
                }
            };
        }
    }
}
