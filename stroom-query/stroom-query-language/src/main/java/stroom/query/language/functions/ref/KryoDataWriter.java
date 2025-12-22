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

import com.esotericsoftware.kryo.io.Output;

public class KryoDataWriter implements DataWriter {

    private final Output output;

    public KryoDataWriter(final Output output) {
        this.output = output;
    }

    @Override
    public void writeByteUnsigned(final int value) {
        output.writeByte(value);
    }

    @Override
    public void writeByte(final byte value) {
        output.writeByte(value);
    }

    @Override
    public void writeBoolean(final boolean value) {
        output.writeBoolean(value);
    }

    @Override
    public void writeShort(final short value) {
        output.writeShort(value);
    }

    @Override
    public void writeInt(final int value) {
        output.writeInt(value);
    }

    @Override
    public void writeLong(final long value) {
        output.writeLong(value);
    }

    @Override
    public void writeFloat(final float value) {
        output.writeFloat(value);
    }

    @Override
    public void writeDouble(final double value) {
        output.writeDouble(value);
    }

    @Override
    public void writeString(final String value) {
        output.writeString(value);
    }

    @Override
    public void writeBytes(final byte[] bytes) {
        output.writeInt(bytes.length);
        output.write(bytes);
    }

    @Override
    public void close() {
        output.close();
    }
}
