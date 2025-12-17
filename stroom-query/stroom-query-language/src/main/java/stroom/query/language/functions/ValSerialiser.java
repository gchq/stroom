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

package stroom.query.language.functions;

import stroom.query.language.functions.ref.DataReader;
import stroom.query.language.functions.ref.DataWriter;
import stroom.util.logging.LogUtil;

import java.util.Arrays;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Function;

public final class ValSerialiser {

    private static final int maxId = Arrays.stream(Type.values())
            .mapToInt(Type::getId)
            .max()
            .orElse(0);
    private static final Serialiser[] SERIALISERS = new Serialiser[maxId + 1];

    static {
        SERIALISERS[Type.NULL.getId()] = new Serialiser(
                input -> ValNull.INSTANCE,
                (output, value) -> {
                });
        SERIALISERS[Type.BOOLEAN.getId()] = new Serialiser(
                input -> ValBoolean.create(input.readBoolean()),
                (output, value) -> output.writeBoolean(value.toBoolean()));
        SERIALISERS[Type.FLOAT.getId()] = new Serialiser(
                input -> ValFloat.create(input.readFloat()),
                (output, value) -> output.writeFloat(value.toFloat()));
        SERIALISERS[Type.DOUBLE.getId()] = new Serialiser(
                input -> ValDouble.create(input.readDouble()),
                (output, value) -> output.writeDouble(value.toDouble()));
        SERIALISERS[Type.INTEGER.getId()] = new Serialiser(
                input -> ValInteger.create(input.readInt()),
                (output, value) -> output.writeInt(value.toInteger()));
        SERIALISERS[Type.LONG.getId()] = new Serialiser(
                input -> ValLong.create(input.readLong()),
                (output, value) -> output.writeLong(value.toLong()));
        SERIALISERS[Type.DATE.getId()] = new Serialiser(
                input -> ValDate.create(input.readLong()),
                (output, value) -> output.writeLong(value.toLong()));
        SERIALISERS[Type.STRING.getId()] = new Serialiser(
                input -> ValString.create(input.readString()),
                (output, value) -> output.writeString(value.toString()));
        SERIALISERS[Type.ERR.getId()] = new Serialiser(
                input -> ValErr.create(input.readString()),
                (output, value) -> output.writeString(((ValErr) value).getMessage()));
        SERIALISERS[Type.DURATION.getId()] = new Serialiser(
                input -> ValDuration.create(input.readLong()),
                (output, value) -> output.writeLong(value.toLong()));
        SERIALISERS[Type.BYTE.getId()] = new Serialiser(
                input -> ValByte.create(input.readByte()),
                (output, value) -> output.writeByte(((ValByte) value).getValue()));
        SERIALISERS[Type.SHORT.getId()] = new Serialiser(
                input -> ValShort.create(input.readShort()),
                (output, value) -> output.writeShort(((ValShort) value).getValue()));
        SERIALISERS[Type.XML.getId()] = new Serialiser(
                input -> ValXml.create(input.readBytes()),
                (output, value) -> output.writeBytes(((ValXml) value).getBytes()));
    }

    public static Val read(final DataReader reader) {
        final int id = reader.readByte();
        final Serialiser serialiser = SERIALISERS[id];
        return serialiser.reader.apply(reader);
    }

    public static void write(final DataWriter writer, final Val val) {
        final byte id = val.type().getId();
        writer.writeByte(id);
        final Serialiser serialiser = SERIALISERS[id];
        Objects.requireNonNull(serialiser, () -> LogUtil.message("No serialiser found for val type: {}, id: {}",
                val.type(), id));

        serialiser.writer.accept(writer, val);
    }

    public static Val[] readArray(final DataReader reader) {
        Val[] values = Val.emptyArray();

        final int valueCount = reader.readByteUnsigned();
        if (valueCount > 0) {
            values = new Val[valueCount];
            for (int i = 0; i < valueCount; i++) {
                values[i] = read(reader);
            }
        }

        return values;
    }

    public static void writeArray(final DataWriter writer, final Val[] values) {
        if (values.length > 255) {
            throw new RuntimeException("You can only write a maximum of " + 255 + " values");
        }
        writer.writeByteUnsigned(values.length);
        for (final Val val : values) {
            write(writer, val);
        }
    }

    /**
     * For testing to ensure we have all types covered
     */
    static Serialiser getSerialiser(final int id) {
        return SERIALISERS[id];
    }


    // --------------------------------------------------------------------------------


    /**
     * Package private for testing
     */
    static class Serialiser {

        final Function<DataReader, Val> reader;
        final BiConsumer<DataWriter, Val> writer;

        public Serialiser(final Function<DataReader, Val> reader,
                          final BiConsumer<DataWriter, Val> writer) {
            this.reader = reader;
            this.writer = writer;
        }
    }
}
