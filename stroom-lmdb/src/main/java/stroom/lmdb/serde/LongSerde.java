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

package stroom.lmdb.serde;

import java.nio.ByteBuffer;

public class LongSerde implements Serde<Long> {

    @Override
    public Long deserialize(final ByteBuffer byteBuffer) {
        final Long val = byteBuffer.getLong();
        byteBuffer.flip();
        return val;
    }

    @Override
    public void serialize(final ByteBuffer byteBuffer, final Long val) {
        byteBuffer.putLong(val);
        byteBuffer.flip();
    }

    public void increment(final ByteBuffer byteBuffer) {
        final int val = byteBuffer.getInt();
        byteBuffer.flip();
        byteBuffer.putLong(val + 1);
        byteBuffer.flip();
    }

    public void decrement(final ByteBuffer byteBuffer) {
        final int val = byteBuffer.getInt();
        byteBuffer.flip();
        byteBuffer.putLong(val - 1);
        byteBuffer.flip();
    }

    @Override
    public int getBufferCapacity() {
        return Long.BYTES;
    }
}
