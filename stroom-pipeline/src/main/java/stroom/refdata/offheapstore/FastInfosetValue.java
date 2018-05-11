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
import java.util.Arrays;

public class FastInfosetValue extends RefDataValue {

    static final byte TYPE_ID = 1;

    private final byte[] fastInfosetBytes;

    public FastInfosetValue(final byte[] fastInfosetBytes) {
        this.fastInfosetBytes = fastInfosetBytes;
    }

    static FastInfosetValue of(byte[] fastInfosetBytes) {
        return new FastInfosetValue(fastInfosetBytes);
    }

    static FastInfosetValue fromByteBuffer(final ByteBuffer byteBuffer) {

        byte[] bytes = new byte[byteBuffer.remaining()];
        byteBuffer.get(bytes);
        return new FastInfosetValue(bytes);
    }

    @Override
    public byte getTypeId() {
        return TYPE_ID;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final FastInfosetValue that = (FastInfosetValue) o;
        return Arrays.equals(fastInfosetBytes, that.fastInfosetBytes);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(fastInfosetBytes);
    }

    public byte[] getValueBytes() {
        return fastInfosetBytes;
    }

    @Override
    void putValue(final ByteBuffer byteBuffer) {
        byteBuffer.put(fastInfosetBytes);
    }
}
