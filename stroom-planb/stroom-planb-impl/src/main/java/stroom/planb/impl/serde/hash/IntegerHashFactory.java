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

public class IntegerHashFactory implements HashFactory {

    @Override
    public Hash create(final byte[] bytes) {
        return new IntegerHash(Long.hashCode(LongHashFunction.xx3().hashBytes(bytes)));
    }

    @Override
    public Hash create(final ByteBuffer byteBuffer) {
        return create(ByteBufferUtils.toBytes(byteBuffer));
    }

    @Override
    public int hashLength() {
        return Integer.BYTES;
    }

    private static class IntegerHash implements Hash {

        private final int hash;

        public IntegerHash(final int hash) {
            this.hash = hash;
        }

        @Override
        public void write(final ByteBuffer byteBuffer) {
            byteBuffer.putInt(hash);
        }

        @Override
        public int len() {
            return Integer.BYTES;
        }

        @Override
        public String toString() {
            return Integer.toString(hash);
        }
    }
}
