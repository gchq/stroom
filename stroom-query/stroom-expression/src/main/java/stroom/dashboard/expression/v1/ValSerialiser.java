package stroom.dashboard.expression.v1;

import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;

import java.util.function.BiConsumer;
import java.util.function.Function;

public final class ValSerialiser {
    private static final Serialiser[] SERIALISERS = new Serialiser[20];

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
                (output, value) -> output.writeFloat(value.toDouble().floatValue()));
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
    }

    public static Val read(final Input input) {
        final int id = input.readByte();
        final Serialiser serialiser = SERIALISERS[id];
        return serialiser.reader.apply(input);
    }

    public static void write(final Output output, final Val val) {
        final byte id = val.type().getId();
        output.writeByte(id);
        final Serialiser serialiser = SERIALISERS[id];
        serialiser.writer.accept(output, val);
    }

    public static Val[] readArray(final Input input) {
        Val[] values = Val.empty();

        final int valueCount = input.readByteUnsigned();
        if (valueCount > 0) {
            values = new Val[valueCount];
            for (int i = 0; i < valueCount; i++) {
                values[i] = read(input);
            }
        }

        return values;
    }

    public static void writeArray(final Output output, final Val[] values) {
        if (values.length > 255) {
            throw new RuntimeException("You can only write a maximum of " + 255 + " values");
        }
        output.writeByte(values.length);
        for (final Val val : values) {
            write(output, val);
        }
    }

    private static class Serialiser {

        final Function<Input, Val> reader;
        final BiConsumer<Output, Val> writer;

        public Serialiser(final Function<Input, Val> reader,
                          final BiConsumer<Output, Val> writer) {
            this.reader = reader;
            this.writer = writer;
        }
    }
}
