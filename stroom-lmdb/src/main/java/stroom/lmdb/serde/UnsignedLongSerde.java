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

public class UnsignedLongSerde implements Serde<UnsignedLong> {

    private final int len;
    private final UnsignedBytes unsignedBytes;

    public UnsignedLongSerde(final int len) {
        this.len = len;
        this.unsignedBytes = UnsignedBytesInstances.ofLength(len);
    }

    public UnsignedLongSerde(final int len, final UnsignedBytes unsignedBytes) {
        if (len != unsignedBytes.length()) {
            throw new RuntimeException("Length mismatch, " + len + " vs " + unsignedBytes.length());
        }
        this.len = len;
        this.unsignedBytes = unsignedBytes;
    }

    @Override
    public UnsignedLong deserialize(final ByteBuffer byteBuffer) {
        final UnsignedLong unsignedLong = new UnsignedLong(unsignedBytes.get(byteBuffer), len);
        byteBuffer.flip();
        return unsignedLong;
    }

    @Override
    public void serialize(final ByteBuffer byteBuffer, final UnsignedLong unsignedLong) {
        unsignedBytes.put(byteBuffer, unsignedLong.getValue());
        byteBuffer.flip();
    }

    public int getLength() {
        return len;
    }

    @Override
    public int getBufferCapacity() {
        return len;
    }

    @Override
    public String toString() {
        return "UnsignedLongSerde{" +
                "len=" + len +
                '}';
    }
}
