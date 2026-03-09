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

package stroom.planb.impl.serde.hash;

import stroom.bytebuffer.ByteBufferUtils;

import net.openhft.hashing.LongHashFunction;

import java.nio.ByteBuffer;

public class LongHashFactory implements HashFactory {

    @Override
    public Hash create(final byte[] bytes) {
        return new LongHash(LongHashFunction.xx3().hashBytes(bytes));
    }

    @Override
    public Hash create(final ByteBuffer byteBuffer) {
        return create(ByteBufferUtils.toBytes(byteBuffer));
    }

    @Override
    public int hashLength() {
        return Long.BYTES;
    }

    private static class LongHash implements Hash {
        private final long hash;

        public LongHash(final long hash) {
            this.hash = hash;
        }

        @Override
        public void write(final ByteBuffer byteBuffer) {
            byteBuffer.putLong(hash);
        }

        @Override
        public int len() {
            return Long.BYTES;
        }

        @Override
        public String toString() {
            return Long.toString(hash);
        }
    }
}
