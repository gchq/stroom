package stroom.dashboard.expression.v1;

import stroom.dashboard.expression.v1.ref.MyByteBufferInput;
import stroom.dashboard.expression.v1.ref.MyByteBufferOutput;

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

    public static Val read(final MyByteBufferInput input) {
        final int id = input.readByte();
        final Serialiser serialiser = SERIALISERS[id];
        return serialiser.reader.apply(input);
    }

    public static void write(final MyByteBufferOutput output, final Val val) {
        final byte id = val.type().getId();
        output.writeByte(id);
        final Serialiser serialiser = SERIALISERS[id];
        serialiser.writer.accept(output, val);
    }

    public static Val[] readArray(final MyByteBufferInput input) {
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

    public static void writeArray(final MyByteBufferOutput output, final Val[] values) {
        if (values.length > 255) {
            throw new RuntimeException("You can only write a maximum of " + 255 + " values");
        }
        output.writeByte(values.length);
        for (final Val val : values) {
            write(output, val);
        }
    }

    private static class Serialiser {

        final Function<MyByteBufferInput, Val> reader;
        final BiConsumer<MyByteBufferOutput, Val> writer;

        public Serialiser(final Function<MyByteBufferInput, Val> reader,
                          final BiConsumer<MyByteBufferOutput, Val> writer) {
            this.reader = reader;
            this.writer = writer;
        }
    }
}
