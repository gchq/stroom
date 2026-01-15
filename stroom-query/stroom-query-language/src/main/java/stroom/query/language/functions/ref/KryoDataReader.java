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

package stroom.query.language.functions.ref;

import com.esotericsoftware.kryo.io.Input;

public class KryoDataReader implements DataReader, AutoCloseable {

    private final Input input;

    public KryoDataReader(final Input input) {
        this.input = input;
    }

    @Override
    public int readByteUnsigned() {
        return input.readByteUnsigned();
    }

    @Override
    public byte readByte() {
        return input.readByte();
    }

    @Override
    public boolean readBoolean() {
        return input.readBoolean();
    }

    @Override
    public short readShort() {
        return input.readShort();
    }

    @Override
    public int readInt() {
        return input.readInt();
    }

    @Override
    public long readLong() {
        return input.readLong();
    }

    @Override
    public float readFloat() {
        return input.readFloat();
    }

    @Override
    public double readDouble() {
        return input.readDouble();
    }

    @Override
    public String readString() {
        return input.readString();
    }

    @Override
    public byte[] readBytes() {
        final int length = input.readInt();
        final byte[] bytes = new byte[length];
        input.readBytes(bytes);
        return bytes;
    }

    @Override
    public void close() {
        input.close();
    }
}
