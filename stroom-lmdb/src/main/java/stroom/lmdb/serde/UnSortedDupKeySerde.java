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

import stroom.lmdb.UnSortedDupKey;

import java.nio.ByteBuffer;

public class UnSortedDupKeySerde<K> implements Serde<UnSortedDupKey<K>> {

    private final UnsignedBytes unsignedBytes;
    private final Serde<K> keySerde;
    private final int idByteLength;

    public UnSortedDupKeySerde(final Serde<K> keySerde, final int idByteLength) {
        this.keySerde = keySerde;
        this.idByteLength = idByteLength;
        this.unsignedBytes = UnsignedBytesInstances.ofLength(idByteLength);
    }

    public UnSortedDupKeySerde(final Serde<K> keySerde) {
        this(keySerde, UnSortedDupKey.DEFAULT_ID_BYTE_LENGTH);
    }

    @Override
    public UnSortedDupKey<K> deserialize(final ByteBuffer byteBuffer) {
        final ByteBuffer keyBuffer = byteBuffer.slice(
                0,
                byteBuffer.remaining() - idByteLength);
        final K key = keySerde.deserialize(keyBuffer);
        final long id = unsignedBytes.get(byteBuffer, byteBuffer.remaining() - idByteLength);
        return new UnSortedDupKey<>(key, UnsignedLong.of(id, unsignedBytes));
    }

    @Override
    public void serialize(final ByteBuffer byteBuffer, final UnSortedDupKey<K> unSortedDupKey) {
        final ByteBuffer keyBuffer = byteBuffer.slice();
        keySerde.serialize(keyBuffer, unSortedDupKey.getKey());
        byteBuffer.position(keyBuffer.remaining());
        unsignedBytes.put(byteBuffer, unSortedDupKey.getId());
        byteBuffer.flip();
    }

    @Override
    public int getBufferCapacity() {
        return idByteLength + keySerde.getBufferCapacity();
    }
}
